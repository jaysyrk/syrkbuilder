package dev.syrkbuilder.core.engine;

import dev.syrkbuilder.core.command.Commands;
import dev.syrkbuilder.core.command.Result;
import dev.syrkbuilder.core.history.ChangeSet;
import dev.syrkbuilder.core.history.HistoryTree;
import dev.syrkbuilder.core.protocol.Protocol;
import dev.syrkbuilder.core.protocol.Request;
import dev.syrkbuilder.core.protocol.UploadChunk;
import dev.syrkbuilder.core.session.Services;
import dev.syrkbuilder.core.session.Session;
import dev.syrkbuilder.core.session.TemplateStore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class Engine<W, B> {
    public static final List<String> EXAMPLE_SCRIPTS = List.of("tower", "spiral", "forest", "maze");

    private final Platform<W, B> platform;
    private final EngineConfig config;
    private final Logger logger;
    private final Commands commands;
    private final Services services;
    private final HistoryStore store;
    private final FileTemplateStore templates;
    private final Map<String, B> parsed = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, HistoryTree<B>> histories = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Map<String, List<Runnable>> waitingForHistory = new HashMap<>();
    private final Map<UUID, Deque<EditJob>> queues = new HashMap<>();
    private final Map<String, Integer> strokes = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    private record Pending(String key, int nodeId, Request rerun, String label, int[] box) {
    }

    public Engine(Platform<W, B> platform, EngineConfig config, File dataFolder, Logger logger) {
        this.platform = platform;
        this.config = config;
        this.logger = logger;
        this.commands = new Commands(config.maxVolume());
        File scriptsDir = new File(dataFolder, "scripts");
        installExamples(scriptsDir);
        this.templates = new FileTemplateStore(new File(dataFolder, "templates"), platform::dataVersion);
        this.services = new Services() {
            @Override
            public TemplateStore templates() {
                return templates;
            }

            @Override
            public String serverScript(String name) {
                File f = new File(scriptsDir, name + ".js");
                try {
                    return f.isFile() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : null;
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            public long scriptTimeoutMillis() {
                return config.scriptTimeoutMillis();
            }
        };
        this.store = new HistoryStore(new File(dataFolder, "history"), logger);
    }

    private void installExamples(File dir) {
        dir.mkdirs();
        for (String name : EXAMPLE_SCRIPTS) {
            File target = new File(dir, name + ".js");
            if (target.exists()) {
                continue;
            }
            try (InputStream in = Engine.class.getResourceAsStream("/dev/syrkbuilder/core/scripts/" + name + ".js")) {
                if (in != null) {
                    Files.copy(in, target.toPath());
                }
            } catch (IOException e) {
                logger.warning("Couldn't install example script " + name + ": " + e.getMessage());
            }
        }
    }

    public void addTemplateFolder(File folder) {
        templates.addFolder(folder);
    }

    public void shutdown() {
        store.shutdown();
    }

    public void preload(UUID player, W world) {
        history(player, world);
    }

    public void receive(UUID player, W world, byte[] message, Consumer<String> reply) {
        Object decoded;
        try {
            decoded = Protocol.decode(message);
        } catch (IOException e) {
            reply.accept("&c" + e.getMessage() + " - update SyrkBuilder so the mod and plugin match.");
            return;
        }
        if (decoded instanceof UploadChunk chunk) {
            try {
                session(player).uploads().accept(chunk);
            } catch (IllegalArgumentException e) {
                reply.accept("&c" + e.getMessage());
            }
        } else if (decoded instanceof Request request) {
            handle(player, world, request, reply);
        }
    }

    private Session session(UUID player) {
        return sessions.computeIfAbsent(player, k -> new Session(config.maxUploadBytes()));
    }

    private void handle(UUID player, W world, Request request, Consumer<String> reply) {
        HistoryTree<B> tree = history(player, world);
        if (tree == null) {
            Request queued = request;
            waitingForHistory.computeIfAbsent(key(player, world), k -> new java.util.ArrayList<>())
                .add(() -> handle(player, world, queued, reply));
            return;
        }
        String key = key(player, world);
        dev.syrkbuilder.core.command.Args args = dev.syrkbuilder.core.command.Args.parse(request.command());
        String first = args.lower(0);
        switch (first) {
            case "sync" -> {
                sendHistory(tree, reply);
                sendTemplates(reply);
                sendPending(player, reply);
                sendPath(player, reply);
                return;
            }
            case "noclip" -> {
                String message = platform.noclip(player, args.lower(1));
                reply.accept(message == null ? "&cNoclip isn't available here." : message);
                return;
            }
            case "confirm", "ok", "keep" -> {
                confirm(player, reply, true);
                return;
            }
            case "cancel" -> {
                cancel(player, world, tree, reply);
                return;
            }
            case "nudge", "shift" -> {
                nudge(player, world, tree, request, args, reply);
                return;
            }
            case "turn" -> {
                turn(player, world, tree, args, reply);
                return;
            }
            case "preview" -> {
                String mode = args.lower(1);
                boolean on = mode.isEmpty() ? !session(player).preview() : mode.equals("on") || mode.equals("true");
                session(player).preview(on);
                if (args.flag("q")) {
                    return;
                }
                reply.accept(on ? "&aPreview on&7: the last thing you place can be moved (/sb nudge), turned (/sb turn) or cancelled (/sb cancel) until you do something else."
                    : "&7Preview off: edits are final straight away (undo still works).");
                return;
            }
            default -> {
                Pending p = pending.get(player);
                if (p != null) {
                    if (args.has("stroke") && p.key().equals(key) && tree.current().id() == p.nodeId()) {
                        pending.remove(player);
                        strokes.put(key, args.intValue("stroke", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
                        sendPending(player, reply);
                    } else {
                        confirm(player, reply, false);
                    }
                }
            }
        }
        if (!args.has("stroke")) {
            strokes.remove(key);
        }
        if ((first.equals("terrain") || first.equals("t") || first.equals("tree") || first.equals("path")) && !args.has("seed")) {
            request = new Request(request.command() + " seed=" + (System.nanoTime() & 0xFFFFFFFFFFFFL), request.target(), request.pos1(),
                request.pos2(), request.feet(), request.yaw(), request.pitch());
        }
        final Request req = request;
        Result result = commands.run(request, platform.view(world), session(player), services);
        result.warnings().forEach(w -> reply.accept("&e" + w));
        if ((first.equals("template") || first.equals("tpl")) && result.kind() == Result.Kind.MESSAGE) {
            sendTemplates(reply);
        }
        if (first.equals("path")) {
            sendPath(player, reply);
        }
        switch (result.kind()) {
            case MESSAGE -> result.lines().forEach(reply);
            case ERROR -> reply.accept("&c" + result.lines().get(0));
            case HISTORY -> showHistory(tree, result.count(), reply);
            case UNDO, REDO -> replay(player, world, tree, key, result.kind() == Result.Kind.UNDO, result.count(), reply);
            case CHECKPOINT -> {
                tree.checkpoint(result.label());
                sendHistory(tree, reply);
                saveIndex(key, tree, List.of());
                reply.accept("&aCheckpoint &f" + result.label() + " &aset at &f#" + tree.current().id() + "&7. Return with &f/sb goto " + result.label());
            }
            case GOTO -> jump(player, world, tree, key, result.label(), reply);
            case RESTORE -> restore(player, world, tree, key, result, reply);
            case EDIT -> {
                long start = System.currentTimeMillis();
                int stroke = args.intValue("stroke", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
                StreamJob<W, B> job = new StreamJob<>(platform, parsed, world, result.stream(), result.label(), session(player), done -> {
                    if (done.error() != null) {
                        reply.accept("&c" + done.error() + " &7(" + done.changes().size() + " blocks were placed - /sb undo reverts them)");
                    }
                    if (stroke != 0) {
                        if (mergeStroke(tree, key, stroke, done.changes())) {
                            sendHistory(tree, reply);
                        }
                        return;
                    }
                    strokes.remove(key);
                    push(tree, key, done.changes());
                    sendHistory(tree, reply);
                    double secs = (System.currentTimeMillis() - start) / 1000.0;
                    if (session(player).preview() && done.error() == null && done.changes().size() > 0 && tree.current().change() == done.changes()) {
                        Request rerun = rerunnable(req, args);
                        pending.put(player, new Pending(key, tree.current().id(), rerun, result.label(), bounds(done.changes())));
                        sendPending(player, reply);
                        reply.accept(String.format("&aPlaced &f%s &7(%,d blocks, %.1fs) &8- %s", result.label(), done.changes().size(), secs,
                            rerun == null ? "&7/sb cancel to remove, anything else keeps it" : "&7move &f/sb nudge&7, turn &f/sb turn&7, &f/sb cancel&7; anything else keeps it"));
                    } else {
                        reply.accept(String.format("&a%s &7done - &f%,d &7blocks changed in &f%.1fs&7. Undo with &f/sb undo", result.label(), done.changes().size(), secs));
                    }
                    result.lines().forEach(reply);
                });
                enqueue(player, job);
                if (job.estimate() > config.blocksPerTick() * 2L) {
                    reply.accept(String.format("&7Working on &f%s&7 (~%,d blocks)...", result.label(), job.estimate()));
                }
            }
        }
    }

    private String key(UUID player, W world) {
        return player + "/" + platform.worldKey(world).replaceAll("[^A-Za-z0-9_./-]", "_").replace("..", "_");
    }

    private HistoryTree<B> history(UUID player, W world) {
        String key = key(player, world);
        HistoryTree<B> tree = histories.get(key);
        if (tree != null) {
            return tree;
        }
        if (!config.saveHistory()) {
            tree = new HistoryTree<>(config.historyBlocks());
            histories.put(key, tree);
            return tree;
        }
        if (loading.add(key)) {
            store.load(key, loaded -> platform.runOnMainThread(() -> {
                loading.remove(key);
                histories.put(key, rebuild(key, loaded));
                List<Runnable> waiting = waitingForHistory.remove(key);
                if (waiting != null) {
                    waiting.forEach(Runnable::run);
                }
            }));
        }
        return null;
    }

    private HistoryTree<B> rebuild(String key, HistoryStore.Loaded loaded) {
        if (loaded == null) {
            return new HistoryTree<>(config.historyBlocks());
        }
        Map<Integer, ChangeSet<B>> changes = new HashMap<>();
        for (Map.Entry<Integer, ChangeSet<String>> e : loaded.changes().entrySet()) {
            try {
                changes.put(e.getKey(), e.getValue().mapPalette(platform::parse));
            } catch (IllegalArgumentException ex) {
                logger.warning("Skipping unreadable history entry " + key + "/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        return HistoryTree.rebuild(config.historyBlocks(), loaded.index().nodes(), changes, loaded.index().currentId());
    }

    private void push(HistoryTree<B> tree, String key, ChangeSet<B> change) {
        if (change.size() == 0) {
            return;
        }
        List<Integer> removed = tree.push(change);
        if (config.saveHistory() && tree.current().change() == change) {
            store.saveChange(key, tree.current().id(), change.mapPalette(platform::serialize));
        }
        saveIndex(key, tree, removed);
    }

    private boolean mergeStroke(HistoryTree<B> tree, String key, int stroke, ChangeSet<B> dab) {
        if (dab.size() == 0) {
            return false;
        }
        Integer current = strokes.get(key);
        ChangeSet<B> head = tree.current().change();
        if (current != null && current == stroke && head != null) {
            head.appendAll(dab);
            tree.grew(dab.size());
            if (config.saveHistory()) {
                store.saveChange(key, tree.current().id(), head.mapPalette(platform::serialize));
            }
            return false;
        }
        push(tree, key, dab);
        strokes.put(key, stroke);
        return true;
    }

    private void saveIndex(String key, HistoryTree<B> tree, List<Integer> removed) {
        if (config.saveHistory()) {
            store.saveIndex(key, tree.structure(), tree.current().id(), removed);
        }
    }

    private void replay(UUID player, W world, HistoryTree<B> tree, String key, boolean undo, int count, Consumer<String> reply) {
        int queued = 0;
        for (int i = 0; i < count; i++) {
            ChangeSet<B> change = undo ? tree.undo() : tree.redo();
            if (change == null) {
                break;
            }
            queued++;
            String verb = undo ? "Undid" : "Redid";
            enqueue(player, new ReplayJob<>(platform, world, change, undo, () -> reply.accept(String.format("&a%s &f%s &7(%,d blocks)", verb, change.label(), change.size()))));
        }
        if (queued == 0) {
            reply.accept(undo ? "&cNothing to undo." : "&cNothing to redo.");
        } else {
            saveIndex(key, tree, List.of());
            sendHistory(tree, reply);
        }
    }

    private void jump(UUID player, W world, HistoryTree<B> tree, String key, String target, Consumer<String> reply) {
        HistoryTree.Node<B> node = tree.find(target);
        if (node == null) {
            reply.accept("&cNo history entry or checkpoint '" + target + "'. See /sb history");
            return;
        }
        List<HistoryTree.Step<B>> steps = tree.pathTo(node);
        if (steps.isEmpty()) {
            reply.accept("&7You're already there.");
            return;
        }
        long blocks = 0;
        for (HistoryTree.Step<B> step : steps) {
            blocks += step.change().size();
        }
        final long total = blocks;
        for (int i = 0; i < steps.size(); i++) {
            HistoryTree.Step<B> step = steps.get(i);
            boolean last = i == steps.size() - 1;
            enqueue(player, new ReplayJob<>(platform, world, step.change(), step.undo(), () -> {
                if (last) {
                    reply.accept(String.format("&aMoved to &f#%d%s &7(%d steps, %,d blocks)", node.id(),
                        node.checkpoint() == null ? "" : " (" + node.checkpoint() + ")", steps.size(), total));
                }
            }));
        }
        saveIndex(key, tree, List.of());
        sendHistory(tree, reply);
    }

    private void restore(UUID player, W world, HistoryTree<B> tree, String key, Result result, Consumer<String> reply) {
        HistoryTree.Node<B> node = tree.find(result.label());
        if (node == null) {
            reply.accept("&cNo history entry or checkpoint '" + result.label() + "'. See /sb history");
            return;
        }
        Map<Long, B> blocks = tree.regionAt(node, result.box());
        if (blocks.isEmpty()) {
            reply.accept("&7Nothing in your selection changed since #" + node.id() + ".");
            return;
        }
        String label = "restore selection to #" + node.id();
        enqueue(player, new MapJob<>(platform, world, blocks, label, change -> {
            push(tree, key, change);
            sendHistory(tree, reply);
            reply.accept(String.format("&aRestored &f%,d &ablocks in your selection to &f#%d&7. This is undoable too.", change.size(), node.id()));
        }));
    }

    private void showHistory(HistoryTree<B> tree, int count, Consumer<String> reply) {
        List<HistoryTree.Node<B>> nodes = tree.nodes();
        if (nodes.size() <= 1) {
            reply.accept("&7No history yet in this world.");
            return;
        }
        SimpleDateFormat time = new SimpleDateFormat("HH:mm");
        reply.accept("&6History &7(&a> &7= you are here, &8grey&7 = undone or another branch)");
        for (int i = Math.max(0, nodes.size() - count); i < nodes.size(); i++) {
            HistoryTree.Node<B> n = nodes.get(i);
            boolean here = n == tree.current();
            String color = tree.onCurrentPath(n) ? "&f" : "&8";
            String label = n.change() == null ? "start" : n.change().label();
            String size = n.change() == null ? "" : String.format(" &7(%,d)", n.change().size());
            String when = n.change() == null ? "" : "&7" + time.format(new Date(n.change().createdAt())) + " ";
            String branch = n.parent() != null && n.parent().children().size() > 1 ? " &3branch of #" + n.parent().id() : "";
            String cp = n.checkpoint() == null ? "" : " &d[" + n.checkpoint() + "]";
            reply.accept((here ? "&a> " : "  ") + color + "#" + n.id() + " " + when + color + label + size + cp + branch);
        }
        reply.accept("&7Jump anywhere with &f/sb goto #id&7, rewind just your selection with &f/sb restore #id");
    }

    private static Request rerunnable(Request req, dev.syrkbuilder.core.command.Args args) {
        String cmd = args.lower(0);
        String again = switch (cmd) {
            case "upload-paste" -> "paste " + args.rest(2);
            case "upload-script" -> null;
            default -> req.command();
        };
        return again == null ? null : new Request(again.trim(), req.target(), req.pos1(), req.pos2(), req.feet(), req.yaw(), req.pitch());
    }

    private static int[] bounds(ChangeSet<?> change) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < change.size(); i++) {
            long p = change.packedPosition(i);
            int x = ChangeSet.unpackX(p);
            int y = ChangeSet.unpackY(p);
            int z = ChangeSet.unpackZ(p);
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x);
            b[4] = Math.max(b[4], y);
            b[5] = Math.max(b[5], z);
        }
        return b;
    }

    private void confirm(UUID player, Consumer<String> reply, boolean explicit) {
        Pending p = pending.remove(player);
        sendPending(player, reply);
        if (explicit) {
            reply.accept(p == null ? "&7Nothing to confirm." : "&aKept &f" + p.label() + "&a.");
        }
    }

    private boolean retract(UUID player, W world, HistoryTree<B> tree, Consumer<String> reply) {
        Pending p = pending.get(player);
        if (p == null) {
            reply.accept("&cNothing to change - this works on the last thing you placed (shapes, terrain, pastes, imports).");
            return false;
        }
        HistoryTree.Node<B> node = tree.node(p.nodeId());
        if (node == null || node != tree.current()) {
            pending.remove(player);
            sendPending(player, reply);
            reply.accept("&cThat edit is already settled - use /sb undo instead.");
            return false;
        }
        ChangeSet<B> change = tree.undo();
        enqueue(player, new ReplayJob<>(platform, world, change, true, () -> { }));
        tree.drop(node);
        strokes.remove(p.key());
        saveIndex(p.key(), tree, List.of(node.id()));
        pending.remove(player);
        return true;
    }

    private void cancel(UUID player, W world, HistoryTree<B> tree, Consumer<String> reply) {
        Pending p = pending.get(player);
        if (retract(player, world, tree, reply)) {
            sendPending(player, reply);
            sendHistory(tree, reply);
            reply.accept("&7Cancelled &f" + p.label() + "&7.");
        }
    }

    private void nudge(UUID player, W world, HistoryTree<B> tree, Request req, dev.syrkbuilder.core.command.Args a, Consumer<String> reply) {
        int[] d;
        String w = a.lower(1);
        if (w.isEmpty()) {
            reply.accept("&cUsage: /sb nudge <dx> <dy> <dz>  or  /sb nudge <up|down|left|right|forward|back> [blocks]");
            return;
        }
        if (w.matches("-?\\d+")) {
            d = new int[]{a.intArg(1, "x", 0, -1000, 1000), a.intArg(2, "y", 0, -1000, 1000), a.intArg(3, "z", 0, -1000, 1000)};
        } else {
            int n = a.intArg(2, "n", 1, 1, 1000);
            d = direction(w, req.yaw(), n);
            if (d == null) {
                reply.accept("&cDirections: up, down, left, right, forward, back (or north/south/east/west).");
                return;
            }
        }
        repeat(player, world, tree, reply, p -> shifted(p.rerun(), d), "moved", p -> {
            if (p.rerun().command().toLowerCase(java.util.Locale.ROOT).startsWith("path ")) {
                for (int[] point : session(player).path()) {
                    point[0] += d[0];
                    point[1] += d[1];
                    point[2] += d[2];
                }
                sendPath(player, reply);
            }
        });
    }

    private void turn(UUID player, W world, HistoryTree<B> tree, dev.syrkbuilder.core.command.Args a, Consumer<String> reply) {
        int deg = a.intArg(1, "deg", 90, -3600, 3600);
        if (deg % 90 != 0) {
            reply.accept("&cTurn by a multiple of 90 degrees.");
            return;
        }
        repeat(player, world, tree, reply, p -> {
            String cmd = p.rerun().command();
            String head = cmd.split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (!head.equals("paste") && !head.equals("template") && !head.equals("tpl")) {
                return null;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(^| )rotate=(-?\\d+)").matcher(cmd);
            String turned = m.find()
                ? m.replaceFirst(m.group(1) + "rotate=" + Math.floorMod(Integer.parseInt(m.group(2)) + deg, 360))
                : cmd + " rotate=" + Math.floorMod(deg, 360);
            Request r = p.rerun();
            return new Request(turned, r.target(), r.pos1(), r.pos2(), r.feet(), r.yaw(), r.pitch());
        }, "turned", p -> { });
    }

    private void repeat(UUID player, W world, HistoryTree<B> tree, Consumer<String> reply,
                        java.util.function.Function<Pending, Request> change, String verb, Consumer<Pending> afterRetract) {
        Pending p = pending.get(player);
        if (p != null && p.rerun() == null) {
            reply.accept("&cThis one can't be " + verb + " - /sb cancel it and place it again.");
            return;
        }
        Request next = p == null ? null : change.apply(p);
        if (p != null && next == null) {
            reply.accept("&cOnly pastes, templates and imports can be turned - nudge shapes and terrain instead.");
            return;
        }
        if (!retract(player, world, tree, reply)) {
            return;
        }
        afterRetract.accept(p);
        enqueue(player, new EditJob() {
            private boolean ran;

            @Override
            public int step(int budget) {
                ran = true;
                return 1;
            }

            @Override
            public boolean done() {
                return ran;
            }

            @Override
            public void finish() {
                handle(player, world, next, reply);
            }
        });
    }

    private static Request shifted(Request r, int[] d) {
        return new Request(r.command(), add(r.target(), d), add(r.pos1(), d), add(r.pos2(), d), add(r.feet(), d), r.yaw(), r.pitch());
    }

    private static int[] add(int[] p, int[] d) {
        return p == null ? null : new int[]{p[0] + d[0], p[1] + d[1], p[2] + d[2]};
    }

    public static int[] direction(String word, float yaw, int n) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        int[] forward = Math.abs(fx) > Math.abs(fz) ? new int[]{(int) Math.signum(fx), 0, 0} : new int[]{0, 0, (int) Math.signum(fz)};
        int[] right = {-forward[2], 0, forward[0]};
        int[] unit = switch (word) {
            case "up", "u" -> new int[]{0, 1, 0};
            case "down", "d" -> new int[]{0, -1, 0};
            case "forward", "f", "ahead" -> forward;
            case "back", "backward", "b" -> new int[]{-forward[0], 0, -forward[2]};
            case "right", "r" -> right;
            case "left", "l" -> new int[]{-right[0], 0, -right[2]};
            case "north", "n" -> new int[]{0, 0, -1};
            case "south", "s" -> new int[]{0, 0, 1};
            case "east", "e" -> new int[]{1, 0, 0};
            case "west", "w" -> new int[]{-1, 0, 0};
            default -> null;
        };
        return unit == null ? null : new int[]{unit[0] * n, unit[1] * n, unit[2] * n};
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }

    private void sendHistory(HistoryTree<B> tree, Consumer<String> reply) {
        List<HistoryTree.Node<B>> nodes = tree.nodes();
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, nodes.size() - 400); i < nodes.size(); i++) {
            HistoryTree.Node<B> n = nodes.get(i);
            int flags = (n == tree.current() ? 1 : 0) | (tree.onCurrentPath(n) ? 2 : 0) | (n == tree.root() ? 4 : 0);
            sb.append(n.id()).append('\t').append(n.parent() == null ? -1 : n.parent().id()).append('\t').append(flags).append('\t')
                .append(n.change() == null ? 0 : n.change().size()).append('\t').append(n.change() == null ? 0 : n.change().createdAt()).append('\t')
                .append(clean(n.checkpoint())).append('\t').append(n.change() == null ? "start" : clean(n.change().label())).append('\n');
        }
        reply.accept(Protocol.dataLine("history", sb.toString()));
    }

    private void sendPath(UUID player, Consumer<String> reply) {
        StringBuilder sb = new StringBuilder();
        for (int[] p : session(player).path()) {
            sb.append(p[0]).append(' ').append(p[1]).append(' ').append(p[2]).append('\n');
        }
        reply.accept(Protocol.dataLine("path", sb.toString()));
    }

    private void sendTemplates(Consumer<String> reply) {
        reply.accept(Protocol.dataLine("templates", String.join("\n", services.templates().list())));
    }

    private void sendPending(UUID player, Consumer<String> reply) {
        Pending p = pending.get(player);
        if (p == null) {
            reply.accept(Protocol.dataLine("pending", ""));
            return;
        }
        int[] b = p.box();
        reply.accept(Protocol.dataLine("pending", clean(p.label()) + "\t" + b[0] + "\t" + b[1] + "\t" + b[2] + "\t" + b[3] + "\t" + b[4] + "\t" + b[5]
            + "\t" + (p.rerun() == null ? 0 : 1)));
    }

    private void enqueue(UUID player, EditJob job) {
        queues.computeIfAbsent(player, k -> new ArrayDeque<>()).add(job);
    }

    public void tick() {
        if (queues.isEmpty()) {
            return;
        }
        int active = 0;
        for (Deque<EditJob> q : queues.values()) {
            if (!q.isEmpty()) {
                active++;
            }
        }
        if (active == 0) {
            return;
        }
        int share = Math.max(500, config.blocksPerTick() / active);
        for (Deque<EditJob> q : queues.values()) {
            int budget = share;
            while (budget > 0 && !q.isEmpty()) {
                EditJob job = q.peek();
                budget -= job.step(budget);
                if (job.done()) {
                    q.poll();
                    job.finish();
                }
            }
        }
        queues.values().removeIf(Deque::isEmpty);
    }

    public boolean busy() {
        return !queues.isEmpty();
    }
}
