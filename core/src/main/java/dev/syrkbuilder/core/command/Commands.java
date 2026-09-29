package dev.syrkbuilder.core.command;

import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.grid.BlockGrid;
import dev.syrkbuilder.core.grid.GridCodec;
import dev.syrkbuilder.core.grid.PasteStream;
import dev.syrkbuilder.core.protocol.Request;
import dev.syrkbuilder.core.script.ScriptRunner;
import dev.syrkbuilder.core.session.Services;
import dev.syrkbuilder.core.session.Session;
import dev.syrkbuilder.core.session.TemplateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import dev.syrkbuilder.core.shape.SelectionStreams;
import dev.syrkbuilder.core.shape.ShapeStream;
import dev.syrkbuilder.core.shape.Shapes;
import dev.syrkbuilder.core.terrain.TerrainJob;
import dev.syrkbuilder.core.terrain.TerrainParams;
import dev.syrkbuilder.core.terrain.TerrainStyle;
import dev.syrkbuilder.core.terrain.TerrainType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Commands {
    private final long maxVolume;

    public Commands(long maxVolume) {
        this.maxVolume = maxVolume;
    }

    public static final List<String> HELP = List.of(
        "&6&lSyrkBuilder &7- &f/sb <command>",
        "&eSelection&7: set, replace <from> <to>, walls, outline, line [radius]",
        "&eShapes&7 (at the block you look at): sphere, ellipsoid, dome, cyl, circle, disc, cone, pyramid, torus, helix",
        "&eTerrain&7: terrain <mountain|hills|mesa|volcano|crater|canyon|dunes|island> [options] &8(/sb terrain list)",
        "&eBrushes&7: brush <type> [radius] [blocks] - or &f/sb brush bind ...&7 and hold right-click &8(/sb brush list)",
        "&eTrees&7: tree <oak|birch|spruce|pine|jungle|dark_oak|acacia|cherry|willow|palm|dead|swamp|mix> [height], brush trees <radius> type=",
        "&eNoclip&7: noclip (or press N) - fly through blocks &8(on servers: spectator mode, needs syrkbuilder.noclip)",
        "&eFill&7: fill <blocks> [radius] [mode=hole|connected|room] &8(bucket fill from the block you aim at)",
        "&ePaths&7: path add (repeat), then path <road|wall|tunnel|river|bridge|line> [width] [blocks]; path undo|clear",
        "&ePreview&7: the last placement stays live - nudge <dx dy dz|up|left|forward..> [n], turn [deg], cancel, confirm; preview on|off",
        "&eSelection edits&7: move [n] [dir], stack [n] [dir], hollow [thickness], overlay <blocks> [depth], naturalize",
        "&eBuild&7: text <blocks> <words...> [size=] [depth=] [-f flat], arch <blocks> <width> <height> [thickness=] [depth=], replacenear <radius> <from> <to>",
        "&eMore&7: cut, smooth [passes] (selection), drain|snow|thaw|green [radius] (around where you look)",
        "&eSelection tools&7: select [-a] [-d] (magic select what you look at), expand|contract|shift <n> [dir|vert], size, count <blocks>, distr",
        "&eClipboard&7: copy, paste [rotate=90] [flip] [-a] [swap=stone:andesite], rotate <deg>, flip",
        "&eTemplates&7: template save|export|paste|load|list|info|delete <name> (reads .schem/.litematic too), marker <name>|list|remove|clear",
        "&eImport&7: import <file> [size=64] [palette=all] [-s solid] &8(files in .minecraft/syrkbuilder/models)",
        "&eScripts&7: script <file> [args] &8(.minecraft/syrkbuilder/scripts, or the server's scripts folder)",
        "&eGradients&7: blocks like &fgrad:stone,andesite,diorite&7 (gradx/gradz/gradr); &fgradient <from> <to> [steps]&7 finds in-between blocks",
        "&eMask & mirror&7: mask <blocks|!blocks|off>, symmetry <x|z|xz|off> &8(apply to every edit)",
        "&eHistory&7: undo [n], redo [n], history, goto <#id|checkpoint>, checkpoint <name>, restore <#id|checkpoint>",
        "&7Blocks can be mixes: &f60%stone,40%andesite&7. Flags: &f-h&7 hollow, &f-a&7 skip/only air.");

    public Result run(Request req, WorldView world, Session session, Services services) {
        Args a = Args.parse(req.command());
        String cmd = a.lower(0);
        try {
            Result r = dispatch(cmd, a, req, world, session, services);
            List<String> warnings = new ArrayList<>(a.notes());
            if (r.kind() == Result.Kind.EDIT && r.stream() instanceof ShapeStream shape) {
                warnings.addAll(buildLimit(shape.box(), world));
            }
            if (r.kind() == Result.Kind.EDIT && r.stream() instanceof TerrainJob job && job.clipped()) {
                warnings.add("The terrain reaches the world's build limit (y " + world.minY() + " to " + (world.maxY() - 1)
                    + ") - it gets cut flat there. Use a lower height, or start lower.");
            }
            if (r.kind() == Result.Kind.EDIT && r.stream() instanceof PasteStream paste) {
                warnings.addAll(buildLimit(paste.box(), world));
            }
            return r.withWarnings(warnings);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private static List<String> buildLimit(Box box, WorldView world) {
        if (box.maxY() >= world.maxY() || box.minY() < world.minY()) {
            return List.of("Part of this is outside the world's build limit (y " + world.minY() + " to " + (world.maxY() - 1)
                + ") and will be cut off. It spans y " + box.minY() + " to " + box.maxY() + ".");
        }
        return List.of();
    }

    private Result dispatch(String cmd, Args a, Request req, WorldView world, Session session, Services services) {
        {
            return switch (cmd) {
                case "", "help", "?" -> Result.message(HELP);
                case "undo" -> Result.undo(a.intArg(1, "n", 1, 1, 100));
                case "redo" -> Result.redo(a.intArg(1, "n", 1, 1, 100));
                case "history", "hist" -> Result.history(a.intArg(1, "n", 15, 1, 100));
                case "goto", "jump" -> a.word(1) == null ? Result.error("Usage: /sb goto <#id|checkpoint>") : Result.jump(a.word(1));
                case "checkpoint", "cp" -> a.word(1) == null ? Result.error("Usage: /sb checkpoint <name>") : Result.checkpoint(name(a.word(1)));
                case "restore" -> restore(req, a);
                case "set" -> selection(req, "set", box -> SelectionStreams.set(box, pattern(a, 1)));
                case "walls" -> selection(req, "walls", box -> SelectionStreams.walls(box, pattern(a, 1)));
                case "outline" -> selection(req, "outline", box -> SelectionStreams.outline(box, pattern(a, 1)));
                case "replace" -> selection(req, "replace", box -> SelectionStreams.replace(box, ids(a, 1), pattern(a, 2), world));
                case "line" -> line(req, a, world);
                case "sphere", "ellipsoid", "dome", "cyl", "cylinder", "circle", "ring", "disc", "cone", "pyramid", "torus", "helix" -> shape(cmd, req, a, world);
                case "terrain", "t" -> terrain(req, a, world);
                case "tree" -> tree(req, a, world);
                case "fill" -> fill(req, a, world);
                case "path" -> path(req, a, world, session);
                case "brush", "b" -> a.lower(1).equals("stamp") ? stamp(req, a, session) : brush(req, a, world);
                case "gradient", "grad" -> gradient(a);
                case "mask" -> mask(a, session);
                case "symmetry", "sym", "mirror" -> symmetry(req, a, session);
                case "copy" -> copy(req, world, session);
                case "paste" -> paste(req, a, session.clipboard(), "paste");
                case "rotate" -> rotateClipboard(a, session);
                case "flip" -> flipClipboard(session);
                case "template", "tpl" -> template(req, a, world, session, services.templates());
                case "marker", "markers" -> marker(req, a, session);
                case "upload-paste" -> uploadPaste(req, a, session);
                case "upload-script" -> uploadScript(req, a, world, session, services);
                case "script" -> serverScript(req, a, world, services);
                case "move" -> move(req, a, world);
                case "stack" -> stack(req, a, world);
                case "hollow" -> hollow(req, a, world);
                case "overlay" -> overlay(req, a, world);
                case "naturalize", "naturalise" -> selection(req, "naturalize", box -> SelectionOps.naturalize(box, world));
                case "count" -> count(req, a, world);
                case "distr", "distribution", "analyze", "analyse" -> distr(req, world);
                case "select", "magic", "sel+" -> magic(req, a, world);
                case "expand" -> resize(req, a, 1, "expand", world);
                case "contract" -> resize(req, a, -1, "contract", world);
                case "shift" -> shiftSelection(req, a);
                case "size" -> size(req);
                case "text" -> text(req, a);
                case "arch" -> arch(req, a);
                case "replacenear", "rnear" -> replaceNear(req, a, world);
                case "cut" -> cut(req, world, session);
                case "smooth" -> smoothSelection(req, a, world);
                case "drain" -> around(req, a, "drain", 10, (c, r) -> NatureOps.drain(c, r, world));
                case "snow" -> around(req, a, "snow", 12, (c, r) -> NatureOps.snow(c, r, world));
                case "thaw" -> around(req, a, "thaw", 12, (c, r) -> NatureOps.thaw(c, r, world));
                case "green" -> around(req, a, "green", 12, (c, r) -> NatureOps.green(c, r, world));
                default -> Result.error("Unknown command '" + cmd + "'. Try /sb help");
            };
        }
    }

    private static String name(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_\\-]{1,32}")) {
            throw new IllegalArgumentException("Names can use letters, numbers, - and _ (max 32).");
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private Box selectionBox(Request req) {
        if (req.pos1() == null || req.pos2() == null) {
            throw new IllegalArgumentException("Select an area first: left-click and right-click with a golden axe, or /sb pos1 and /sb pos2.");
        }
        return Box.of(req.pos1()[0], req.pos1()[1], req.pos1()[2], req.pos2()[0], req.pos2()[1], req.pos2()[2]);
    }

    private Result restore(Request req, Args a) {
        if (a.word(1) == null) {
            return Result.error("Usage: /sb restore <#id|checkpoint> - rewinds just your selection to that point.");
        }
        return Result.restore(a.word(1), selectionBox(req));
    }

    private Result copy(Request req, WorldView world, Session session) {
        Box box = selectionBox(req);
        if (box.volume() > maxVolume) {
            return Result.error("Selection is " + box.volume() + " blocks - the limit is " + maxVolume + ".");
        }
        box = box.clampY(world.minY(), world.maxY());
        BlockGrid grid = new BlockGrid(box.maxX() - box.minX() + 1, box.maxY() - box.minY() + 1, box.maxZ() - box.minZ() + 1);
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    grid.set(x - box.minX(), y - box.minY(), z - box.minZ(), world.blockState(x, y, z));
                }
            }
        }
        int markers = 0;
        for (Map.Entry<String, int[]> m : session.markers().entrySet()) {
            int[] p = m.getValue();
            if (box.contains(p[0], p[1], p[2])) {
                grid.markers().put(m.getKey(), new int[]{p[0] - box.minX(), p[1] - box.minY(), p[2] - box.minZ()});
                markers++;
            }
        }
        session.clipboard(grid);
        return Result.message(String.format("&aCopied &f%,d &ablocks &7(%dx%dx%d%s). Paste with &f/sb paste",
            grid.volume(), grid.sizeX(), grid.sizeY(), grid.sizeZ(), markers > 0 ? ", " + markers + " markers" : ""));
    }

    private Result paste(Request req, Args a, BlockGrid source, String label) {
        if (source == null) {
            return Result.error("Your clipboard is empty - /sb copy a selection or /sb template load <name>.");
        }
        int turns = quarterTurns(a.doubleValue("rotate", 0, -3600, 3600));
        boolean flip = a.flag("flip") || a.lower(1).equals("flip") || "x".equalsIgnoreCase(a.string("flip", ""));
        BlockGrid grid = turns != 0 || flip ? source.transformed(turns, flip) : source;
        int[] t = anchor(req);
        int ox = t[0] - grid.sizeX() / 2;
        int oy = t[1] + 1 + a.intValue("up", 0, -512, 512);
        int oz = t[2] - grid.sizeZ() / 2;
        List<String> done = new ArrayList<>();
        for (Map.Entry<String, int[]> m : grid.markers().entrySet()) {
            int[] p = m.getValue();
            done.add(String.format("&7Marker &f%s &7at &f%d %d %d", m.getKey(), ox + p[0], oy + p[1], oz + p[2]));
        }
        return Result.edit(label, new PasteStream(grid, ox, oy, oz, a.flag("a"), swaps(a.string("swap", ""))), done);
    }

    private static int quarterTurns(double degrees) {
        double q = degrees / 90.0;
        if (Math.abs(q - Math.round(q)) > 1e-6) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees.");
        }
        return Math.floorMod(Math.round(q), 4);
    }

    private static Map<String, String> swaps(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw.isBlank()) {
            return map;
        }
        for (String pair : raw.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                map.put(Pattern.normalize(parts[0]), Pattern.normalize(parts[1]));
            } else if (parts.length == 4) {
                map.put(Pattern.normalize(parts[0] + ":" + parts[1]), Pattern.normalize(parts[2] + ":" + parts[3]));
            } else {
                throw new IllegalArgumentException("Bad swap '" + pair + "' - use from:to, e.g. swap=stone:andesite");
            }
        }
        return map;
    }

    private Result rotateClipboard(Args a, Session session) {
        if (session.clipboard() == null) {
            return Result.error("Your clipboard is empty.");
        }
        int turns = quarterTurns(a.doubleArg(1, "deg", 90, -3600, 3600));
        session.clipboard(session.clipboard().transformed(turns, false));
        return Result.message("&aClipboard rotated " + (turns * 90) + " degrees clockwise.");
    }

    private Result flipClipboard(Session session) {
        if (session.clipboard() == null) {
            return Result.error("Your clipboard is empty.");
        }
        session.clipboard(session.clipboard().transformed(0, true));
        return Result.message("&aClipboard mirrored east-west.");
    }

    private Result template(Request req, Args a, WorldView world, Session session, TemplateStore store) {
        String sub = a.lower(1);
        try {
            switch (sub) {
                case "save" -> {
                    String name = name(a.word(2));
                    if (req.pos1() != null && req.pos2() != null && !a.flag("c")) {
                        Result copied = copy(req, world, session);
                        if (copied.kind() == Result.Kind.ERROR) {
                            return copied;
                        }
                    }
                    if (session.clipboard() == null) {
                        return Result.error("Select an area (or copy one) first.");
                    }
                    store.save(name, session.clipboard());
                    BlockGrid g = session.clipboard();
                    return Result.message(String.format("&aSaved template &f%s &7(%dx%dx%d, %d markers)", name, g.sizeX(), g.sizeY(), g.sizeZ(), g.markers().size()));
                }
                case "load" -> {
                    BlockGrid g = loadTemplate(store, a.word(2));
                    session.clipboard(g);
                    return Result.message("&aLoaded template &f" + a.word(2).toLowerCase(Locale.ROOT) + " &ainto your clipboard.");
                }
                case "paste" -> {
                    BlockGrid g = loadTemplate(store, a.word(2));
                    return paste(req, shift(a, 2), g, "template " + a.word(2).toLowerCase(Locale.ROOT));
                }
                case "list", "" -> {
                    List<String> names = store.list();
                    return Result.message(names.isEmpty() ? "&7No templates yet - /sb template save <name>" : "&6Templates&7: &f" + String.join(", ", names));
                }
                case "export" -> {
                    String name = name(a.word(2));
                    dev.syrkbuilder.core.grid.Schematics.Format format = dev.syrkbuilder.core.grid.Schematics.Format.byName(a.word(3) == null ? "schem" : a.word(3));
                    if (format == null) {
                        return Result.error("Export as schem (WorldEdit, FAWE, Axiom) or litematic (Litematica).");
                    }
                    if (req.pos1() != null && req.pos2() != null && !a.flag("c")) {
                        Result copied = copy(req, world, session);
                        if (copied.kind() == Result.Kind.ERROR) {
                            return copied;
                        }
                    }
                    if (session.clipboard() == null) {
                        return Result.error("Select an area (or copy one) first.");
                    }
                    String file = store.export(name, session.clipboard(), format);
                    return Result.message("&aExported &f" + file + " &7to the templates folder. Block entities (chest contents, signs) aren't included.");
                }
                case "info" -> {
                    BlockGrid g = loadTemplate(store, a.word(2));
                    List<String> lines = new ArrayList<>();
                    lines.add(String.format("&6%s&7: %dx%dx%d, %,d blocks, %d block types", a.word(2), g.sizeX(), g.sizeY(), g.sizeZ(), g.nonEmpty(), g.palette().size()));
                    for (Map.Entry<String, int[]> m : g.markers().entrySet()) {
                        int[] p = m.getValue();
                        lines.add("&7- marker &f" + m.getKey() + " &7at +" + p[0] + " +" + p[1] + " +" + p[2]);
                    }
                    return Result.message(lines);
                }
                case "delete", "remove" -> {
                    String name = name(a.word(2));
                    return store.delete(name) ? Result.message("&7Deleted template &f" + name) : Result.error("No template named " + name);
                }
                default -> {
                    return Result.error("Usage: /sb template <save|export|paste|load|list|info|delete> [name]");
                }
            }
        } catch (IOException e) {
            return Result.error("Template storage error: " + e.getMessage());
        }
    }

    private static Args shift(Args a, int n) {
        StringBuilder sb = new StringBuilder("paste");
        return Args.parse(sb.append(' ').append(a.rest(n + 1)).toString());
    }

    private static BlockGrid loadTemplate(TemplateStore store, String raw) throws IOException {
        String name = name(raw);
        BlockGrid g = store.load(name);
        if (g == null) {
            throw new IllegalArgumentException("No template named " + name + " - /sb template list");
        }
        return g;
    }

    private Result marker(Request req, Args a, Session session) {
        String sub = a.lower(1);
        switch (sub) {
            case "", "list" -> {
                if (session.markers().isEmpty()) {
                    return Result.message("&7No markers. Look at a block and /sb marker <name> (e.g. spawn1).");
                }
                List<String> lines = new ArrayList<>();
                for (Map.Entry<String, int[]> m : session.markers().entrySet()) {
                    int[] p = m.getValue();
                    lines.add("&7- &f" + m.getKey() + " &7at " + p[0] + " " + p[1] + " " + p[2]);
                }
                return Result.message(lines);
            }
            case "clear" -> {
                session.markers().clear();
                return Result.message("&7Markers cleared.");
            }
            case "remove" -> {
                String name = name(a.word(2));
                return session.markers().remove(name) != null ? Result.message("&7Removed marker &f" + name) : Result.error("No marker named " + name);
            }
            default -> {
                String name = name(a.word(1));
                int[] t = anchor(req);
                int[] p = req.target() != null ? new int[]{t[0], t[1] + 1, t[2]} : t;
                session.markers().put(name, p);
                return Result.message("&aMarker &f" + name + " &aset at &f" + p[0] + " " + p[1] + " " + p[2] + "&7. It's saved with templates that contain it.");
            }
        }
    }

    private static byte[] takeUpload(Session session, Args a) {
        int id = a.intArg(1, "id", -1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        byte[] data = session.uploads().take(id);
        if (data == null) {
            throw new IllegalArgumentException("Upload didn't arrive - try again.");
        }
        return data;
    }

    private Result uploadPaste(Request req, Args a, Session session) {
        BlockGrid grid;
        try {
            grid = GridCodec.decode(takeUpload(session, a));
        } catch (IOException e) {
            return Result.error("Couldn't read the imported model: " + e.getMessage());
        }
        if (grid.volume() > maxVolume) {
            return Result.error("That model is " + grid.volume() + " blocks - the limit is " + maxVolume + ".");
        }
        String name = a.string("name", "model");
        session.clipboard(grid);
        return paste(req, shift(a, 1), grid, "import " + name);
    }

    private Result uploadScript(Request req, Args a, WorldView world, Session session, Services services) {
        String source = new String(takeUpload(session, a), StandardCharsets.UTF_8);
        String name = a.word(2) == null ? "script" : a.word(2);
        return runScript(req, name, source, restArgs(a, 3), world, services);
    }

    private Result serverScript(Request req, Args a, WorldView world, Services services) {
        String name = a.word(1);
        if (name == null) {
            return Result.error("Usage: /sb script <name> [args...]");
        }
        String safe = name.replaceAll("\\.js$", "");
        if (!safe.matches("[A-Za-z0-9_\\-]{1,64}")) {
            return Result.error("Script names can use letters, numbers, - and _.");
        }
        String source = services.serverScript(safe);
        if (source == null) {
            return Result.error("No script '" + safe + "' on your computer (.minecraft/syrkbuilder/scripts) or the server.");
        }
        return runScript(req, safe, source, restArgs(a, 2), world, services);
    }

    private static String[] restArgs(Args a, int from) {
        List<String> list = new ArrayList<>();
        for (int i = from; i < a.size(); i++) {
            list.add(a.word(i));
        }
        return list.toArray(new String[0]);
    }

    private Result runScript(Request req, String name, String source, String[] args, WorldView world, Services services) {
        int[] origin = req.target() != null || req.feet() != null || req.pos1() == null ? anchor(req) : req.pos1();
        try {
            ScriptRunner.Output out = ScriptRunner.run(source, name + ".js", world, origin, req.pos1(), req.pos2(), args, maxVolume, services.scriptTimeoutMillis());
            List<String> lines = new ArrayList<>();
            for (String m : out.messages()) {
                lines.add("&7[" + name + "] &f" + m);
            }
            if (out.edits().size() == 0) {
                lines.add("&7Script " + name + " finished without placing blocks.");
                return Result.message(lines);
            }
            return Result.edit("script " + name, out.edits(), lines);
        } catch (ScriptRunner.ScriptFailure e) {
            return Result.error("Script error: " + e.getMessage());
        }
    }

    private interface BoxEdit {
        EditStream make(Box box);
    }

    private Box checkedSelection(Request req) {
        Box box = selectionBox(req);
        if (box.volume() > maxVolume) {
            throw new IllegalArgumentException("Selection is " + box.volume() + " blocks - the limit is " + maxVolume + ".");
        }
        return box;
    }

    private static int[] offset(Request req, Args a, int from, int def) {
        String w = a.lower(from);
        if (w.matches("-?\\d+") && a.lower(from + 1).matches("-?\\d+") && a.lower(from + 2).matches("-?\\d+")) {
            return new int[]{a.intArg(from, "x", 0, -4096, 4096), a.intArg(from + 1, "y", 0, -4096, 4096), a.intArg(from + 2, "z", 0, -4096, 4096)};
        }
        int n;
        String dir;
        if (w.matches("\\d+")) {
            n = a.intArg(from, "n", def, 1, 4096);
            dir = a.lower(from + 1);
        } else {
            n = a.intValue("n", def, 1, 4096);
            dir = w;
        }
        int[] unit = Directions.unit(dir, req.yaw(), req.pitch());
        if (unit == null) {
            throw new IllegalArgumentException("Directions: up, down, left, right, forward, back, north, south, east, west (or leave it out to use where you look).");
        }
        return new int[]{unit[0] * n, unit[1] * n, unit[2] * n};
    }

    private Result move(Request req, Args a, WorldView world) {
        Box box = checkedSelection(req).clampY(world.minY(), world.maxY());
        int[] d = offset(req, a, 1, 1);
        BlockGrid grid = SelectionOps.read(box, world);
        Pattern leave = a.has("leave") ? Pattern.parse(a.string("leave", "air")) : null;
        Box dest = SelectionOps.shift(box, d[0], d[1], d[2]);
        return Result.edit("move", SelectionOps.move(box, grid, d[0], d[1], d[2], a.flag("a"), leave),
            List.of(String.format("&7Moved by &f%d %d %d&7 - the selection moved with it.", d[0], d[1], d[2]))).selecting(dest);
    }

    private Result stack(Request req, Args a, WorldView world) {
        Box box = checkedSelection(req).clampY(world.minY(), world.maxY());
        String w = a.lower(1);
        int count = w.matches("\\d+") ? a.intArg(1, "n", 1, 1, 256) : a.intValue("n", 1, 1, 256);
        String dir = w.matches("\\d+") ? a.lower(2) : w;
        int[] unit = Directions.unit(dir, req.yaw(), req.pitch());
        if (unit == null) {
            throw new IllegalArgumentException("Directions: up, down, left, right, forward, back, north, south, east, west.");
        }
        if (box.volume() * count > maxVolume * 4) {
            return Result.error("That's " + box.volume() * count + " blocks - use fewer copies or a smaller selection.");
        }
        BlockGrid grid = SelectionOps.read(box, world);
        Result r = Result.edit("stack", SelectionOps.stack(box, grid, unit, count, a.flag("a")),
            List.of(String.format("&7Stacked &f%d &7cop%s.", count, count == 1 ? "y" : "ies")));
        return a.flag("s") ? r.selecting(SelectionOps.stackBounds(box, unit, count)) : r;
    }

    private Result hollow(Request req, Args a, WorldView world) {
        Box box = checkedSelection(req).clampY(world.minY(), world.maxY());
        int thickness = a.intArg(1, "thickness", 1, 1, 64);
        Pattern fill = a.word(2) != null ? Pattern.parse(a.word(2)) : null;
        return Result.edit("hollow", SelectionOps.hollow(box, world, thickness, fill));
    }

    private Result overlay(Request req, Args a, WorldView world) {
        Box box = checkedSelection(req);
        return Result.edit("overlay", SelectionOps.overlay(box, world, pattern(a, 1), a.intArg(2, "depth", 1, 1, 64)));
    }

    private Result count(Request req, Args a, WorldView world) {
        Box box = checkedSelection(req);
        Set<String> want = ids(a, 1);
        Map<String, Long> all = SelectionOps.distribution(box, world);
        long n = 0;
        for (String id : want) {
            n += all.getOrDefault(id, 0L);
        }
        return Result.message(String.format("&f%,d &7of the &f%,d &7blocks in your selection are &f%s&7.", n, box.volume(), String.join(", ", want)));
    }

    private Result distr(Request req, WorldView world) {
        Box box = checkedSelection(req);
        Map<String, Long> all = SelectionOps.distribution(box, world);
        long total = 0;
        for (long v : all.values()) {
            total += v;
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(all.entrySet());
        sorted.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        List<String> lines = new ArrayList<>();
        lines.add(String.format("&6Blocks in your selection &7(%,d total, %d kinds)", total, sorted.size()));
        for (int i = 0; i < Math.min(12, sorted.size()); i++) {
            Map.Entry<String, Long> e = sorted.get(i);
            lines.add(String.format("&f%6.1f%% &7%,d &f%s", 100.0 * e.getValue() / Math.max(1, total), e.getValue(), e.getKey().replace("minecraft:", "")));
        }
        if (sorted.size() > 12) {
            lines.add("&8...and " + (sorted.size() - 12) + " more");
        }
        return Result.message(lines);
    }

    private Result magic(Request req, Args a, WorldView world) {
        if (req.target() == null) {
            return Result.error("Look at a block to select everything connected to it.");
        }
        int limit = (int) Math.min(Integer.MAX_VALUE, Math.min(maxVolume, a.intArg(1, "limit", 200000, 1, Integer.MAX_VALUE)));
        SelectionOps.Region region = SelectionOps.connected(req.target(), world, a.flag("a"), a.flag("d"), limit);
        if (region == null) {
            return Result.error("That's air - look at a block.");
        }
        Box b = region.box();
        String what = a.flag("a") ? "connected blocks" : "connected " + Pattern.baseId(world.blockId(req.target()[0], req.target()[1], req.target()[2])).replace("minecraft:", "");
        return Result.message(String.format("&aSelected &f%,d &a%s &7(%dx%dx%d)%s", region.count(), what,
            b.maxX() - b.minX() + 1, b.maxY() - b.minY() + 1, b.maxZ() - b.minZ() + 1,
            region.capped() ? " &e- stopped at the limit, raise it with limit=" : "")).selecting(b);
    }

    private Result resize(Request req, Args a, int sign, String verb, WorldView world) {
        Box box = selectionBox(req);
        if (a.lower(1).equals("vert") || a.lower(1).equals("vertical")) {
            if (sign < 0) {
                return Result.error("Use /sb contract <n> up|down to shrink vertically.");
            }
            Box full = new Box(box.minX(), world.minY(), box.minZ(), box.maxX(), world.maxY() - 1, box.maxZ());
            return Result.message("&aSelection now spans the full height.").selecting(full);
        }
        int n = a.intArg(1, "n", 1, 1, 4096);
        String dir = a.lower(2);
        Box out;
        if (dir.equals("all") || dir.equals("*")) {
            out = new Box(box.minX() - n * sign, box.minY() - n * sign, box.minZ() - n * sign, box.maxX() + n * sign, box.maxY() + n * sign, box.maxZ() + n * sign);
            if (out.minX() > out.maxX() || out.minY() > out.maxY() || out.minZ() > out.maxZ()) {
                return Result.error("That would shrink the selection to nothing.");
            }
        } else {
            int[] unit = Directions.unit(dir, req.yaw(), req.pitch());
            if (unit == null) {
                return Result.error("Directions: up, down, left, right, forward, back, north, south, east, west, all.");
            }
            out = SelectionOps.resize(box, unit, n * sign);
        }
        return Result.message(String.format("&a%s &7- selection is now &f%dx%dx%d &7(%,d blocks)", verb.equals("expand") ? "Expanded" : "Contracted",
            out.maxX() - out.minX() + 1, out.maxY() - out.minY() + 1, out.maxZ() - out.minZ() + 1, out.volume())).selecting(out);
    }

    private Result shiftSelection(Request req, Args a) {
        Box box = selectionBox(req);
        int[] d = offset(req, a, 1, 1);
        return Result.message(String.format("&aSelection shifted by &f%d %d %d&7 (blocks stay where they are - use /sb move to take them along).", d[0], d[1], d[2]))
            .selecting(SelectionOps.shift(box, d[0], d[1], d[2]));
    }

    private interface AroundEdit {
        dev.syrkbuilder.core.edit.CellStream make(int[] center, int radius);
    }

    private Result around(Request req, Args a, String label, int def, AroundEdit edit) {
        int radius = a.intArg(1, "radius", def, 1, 64);
        dev.syrkbuilder.core.edit.CellStream stream = edit.make(anchor(req), radius);
        if (stream.size() == 0) {
            return Result.message("&7Nothing to " + label + " within " + radius + " blocks of where you look.");
        }
        return Result.edit(label, stream);
    }

    private Result cut(Request req, WorldView world, Session session) {
        Box box = checkedSelection(req);
        Result copied = copy(req, world, session);
        if (copied.kind() == Result.Kind.ERROR) {
            return copied;
        }
        return Result.edit("cut", SelectionStreams.set(box.clampY(world.minY(), world.maxY()), Pattern.of(SelectionOps.AIR)),
            List.of(copied.lines().get(0).replace("Copied", "Cut")));
    }

    private Result smoothSelection(Request req, Args a, WorldView world) {
        Box box = checkedSelection(req);
        return Result.edit("smooth", NatureOps.smooth(box, world, a.intArg(1, "passes", 3, 1, 20)));
    }

    private Result text(Request req, Args a) {
        if (a.size() < 3) {
            return Result.error("Usage: /sb text <blocks> <words...> [size=1] [depth=1] [-f]  e.g. /sb text gold_block Hello");
        }
        Pattern pattern = pattern(a, 1);
        List<String> words = new ArrayList<>();
        for (int i = 2; i < a.size(); i++) {
            words.add(a.word(i));
        }
        String text = String.join(" ", words);
        if (text.length() > 64) {
            return Result.error("Keep it to 64 characters.");
        }
        int size = a.intValue("size", 1, 1, 16);
        int depth = a.intValue("depth", 1, 1, 16);
        return Result.edit("text", BuildOps.text(anchor(req), req.yaw(), text, pattern, size, depth, a.flag("f")));
    }

    private Result arch(Request req, Args a) {
        Pattern pattern = pattern(a, 1);
        int width = a.intArg(2, "width", 12, 3, 256);
        int height = a.intArg(3, "height", Math.max(3, width / 2), 2, 256);
        return Result.edit("arch", BuildOps.arch(anchor(req), req.yaw(), pattern, width, height,
            a.intValue("thickness", 2, 1, 64), a.intValue("depth", 3, 1, 64)));
    }

    private Result replaceNear(Request req, Args a, WorldView world) {
        if (a.size() < 4) {
            return Result.error("Usage: /sb replacenear <radius> <from> <to>");
        }
        int radius = a.intArg(1, "radius", 8, 1, 64);
        dev.syrkbuilder.core.edit.CellStream stream = BuildOps.replaceNear(anchor(req), radius, ids(a, 2), pattern(a, 3), world);
        if (stream.size() == 0) {
            return Result.message("&7No matching blocks within " + radius + " blocks.");
        }
        return Result.edit("replacenear", stream);
    }

    private Result size(Request req) {
        Box b = selectionBox(req);
        return Result.message(String.format("&7Selection &f%dx%dx%d &7(%,d blocks) from &f%d %d %d &7to &f%d %d %d",
            b.maxX() - b.minX() + 1, b.maxY() - b.minY() + 1, b.maxZ() - b.minZ() + 1, b.volume(),
            b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
    }

    private Result selection(Request req, String label, BoxEdit edit) {
        Box box = selectionBox(req);
        if (box.volume() > maxVolume) {
            return Result.error("Selection is " + box.volume() + " blocks - the limit is " + maxVolume + ".");
        }
        return Result.edit(label, edit.make(box));
    }

    private static Pattern pattern(Args a, int i) {
        String raw = a.word(i);
        if (raw == null) {
            throw new IllegalArgumentException("Missing block pattern, e.g. stone or 70%stone,30%andesite");
        }
        return Pattern.parse(raw);
    }

    private static Set<String> ids(Args a, int i) {
        Set<String> set = new HashSet<>();
        for (String block : pattern(a, i).blocks()) {
            set.add(Pattern.baseId(block));
        }
        return set;
    }

    private static int[] anchor(Request req) {
        if (req.target() != null) {
            return req.target();
        }
        if (req.feet() != null) {
            return req.feet();
        }
        throw new IllegalArgumentException("Look at a block first.");
    }

    private Result line(Request req, Args a, WorldView world) {
        if (req.pos1() == null || req.pos2() == null) {
            return Result.error("Set pos1 and pos2 as the line's ends.");
        }
        int[] p = req.pos1();
        int[] q = req.pos2();
        double radius = a.doubleArg(2, "radius", 0, 0, 32);
        Shapes.Placed placed = Shapes.line(p[0], p[1], p[2], q[0], q[1], q[2], radius);
        return placedEdit("line", placed, pattern(a, 1), false, a.flag("a") ? world : null);
    }

    private Result shape(String cmd, Request req, Args a, WorldView world) {
        int[] t = anchor(req);
        int x = t[0];
        int y = t[1];
        int z = t[2];
        Pattern pattern = pattern(a, 1);
        boolean hollow = a.flag("h");
        Shapes.Placed placed;
        switch (cmd) {
            case "sphere" -> placed = Shapes.sphere(x, y, z, a.doubleArg(2, "r", 5, 0, 256));
            case "ellipsoid" -> placed = Shapes.ellipsoid(x, y, z, a.doubleArg(2, "rx", 8, 0, 256), a.doubleArg(3, "ry", 4, 0, 256), a.doubleArg(4, "rz", 8, 0, 256));
            case "dome" -> placed = Shapes.dome(x, y + 1, z, a.doubleArg(2, "r", 8, 0, 256));
            case "cyl", "cylinder" -> placed = Shapes.cylinder(x, y + 1, z, a.doubleArg(2, "r", 5, 0, 256), a.intArg(3, "height", 1, 1, 384));
            case "circle", "ring" -> placed = Shapes.circle(x, y + 1, z, a.doubleArg(2, "r", 8, 0, 256), a.doubleArg(3, "thickness", 1, 1, 256), axis(req, a));
            case "disc" -> placed = Shapes.circle(x, y + 1, z, a.doubleArg(2, "r", 8, 0, 256), 1e9, axis(req, a));
            case "cone" -> placed = Shapes.cone(x, y + 1, z, a.doubleArg(2, "r", 6, 0, 256), a.intArg(3, "height", 12, 1, 384));
            case "pyramid" -> placed = Shapes.pyramid(x, y + 1, z, a.intArg(2, "size", 8, 0, 256));
            case "torus" -> placed = Shapes.torus(x, y + 1, z, a.doubleArg(2, "major", 10, 1, 256), a.doubleArg(3, "minor", 3, 0, 64));
            default -> placed = Shapes.helix(x, y + 1, z, a.doubleArg(2, "r", 6, 1, 128), a.intArg(3, "height", 20, 1, 384),
                a.doubleValue("turns", 3, 0.1, 64), a.doubleValue("thickness", 1, 0, 16));
        }
        return placedEdit(cmd, placed, pattern, hollow, a.flag("a") ? world : null);
    }

    private static Shapes.Axis axis(Request req, Args a) {
        if (!a.flag("v")) {
            return Shapes.Axis.Y;
        }
        float yaw = ((req.yaw() % 360) + 360) % 360;
        boolean facingX = (yaw > 45 && yaw < 135) || (yaw > 225 && yaw < 315);
        return facingX ? Shapes.Axis.X : Shapes.Axis.Z;
    }

    private Result placedEdit(String label, Shapes.Placed placed, Pattern pattern, boolean hollow, WorldView airOnly) {
        if (placed.box().volume() > maxVolume) {
            return Result.error("That shape covers " + placed.box().volume() + " blocks - the limit is " + maxVolume + ".");
        }
        return Result.edit(label, new ShapeStream(placed.box(), placed.shape(), pattern, hollow, airOnly));
    }

    private Result mask(Args a, Session session) {
        String raw = a.word(1);
        if (raw == null) {
            if (session.mask() == null) {
                return Result.message("&7No mask - edits can change any block. &f/sb mask grass_block,dirt&7 or &f/sb mask !leaves");
            }
            return Result.message("&7Mask: " + (session.maskInverted() ? "everything except " : "only ") + "&f" + String.join(", ", session.mask()));
        }
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("none")) {
            session.mask(null, false);
            return Result.message("&7Mask off - edits can change any block.");
        }
        boolean inverted = raw.startsWith("!");
        Set<String> ids = new HashSet<>();
        for (String b : Pattern.parse(inverted ? raw.substring(1) : raw).blocks()) {
            ids.add(Pattern.baseId(b));
        }
        session.mask(ids, inverted);
        return Result.message("&aMask set&7: edits will " + (inverted ? "never change " : "only change ") + "&f" + String.join(", ", ids)
            + "&7. Turn off with &f/sb mask off");
    }

    private Result symmetry(Request req, Args a, Session session) {
        String axes = a.lower(1);
        if (axes.isEmpty() || axes.equals("off") || axes.equals("none")) {
            session.symmetry(null, null);
            return Result.message("&7Symmetry off.");
        }
        if (!axes.equals("x") && !axes.equals("z") && !axes.equals("xz") && !axes.equals("zx")) {
            return Result.error("Usage: /sb symmetry <x|z|xz|off> - mirrors through the block you look at");
        }
        int[] o = anchor(req);
        session.symmetry(axes.equals("zx") ? "xz" : axes, o.clone());
        return Result.message("&aSymmetry " + axes.toUpperCase(Locale.ROOT) + " &7through &f" + o[0] + " " + o[1] + " " + o[2]
            + "&7 - every edit is mirrored. &f/sb symmetry off&7 to stop.");
    }

    private Result gradient(Args a) {
        if (a.word(2) == null) {
            return Result.error("Usage: /sb gradient <from> <to> [steps] [palette=all] - blocks or #rrggbb colours");
        }
        int steps = a.intArg(3, "steps", 5, 2, 16);
        List<String> blocks = dev.syrkbuilder.core.model.BlockPalette.gradient(a.word(1), a.word(2), steps, a.string("palette", "all"));
        List<String> shortNames = new ArrayList<>();
        for (String b : blocks) {
            shortNames.add(b.startsWith("minecraft:") ? b.substring(10) : b);
        }
        String pattern = "grad:" + String.join(",", shortNames);
        return Result.message(List.of("&6Gradient&7: &f" + String.join(" &8> &f", shortNames),
            "&7Use it as blocks anywhere: &f" + pattern,
            "&8(gradx:/gradz: run sideways, gradr: from the centre out)"));
    }

    private Result fill(Request req, Args a, WorldView world) {
        if (a.word(1) == null) {
            return Result.error("Usage: /sb fill <blocks> [radius] [mode=hole|connected|room] - aim at the floor of a hole (or at a block to recolour)");
        }
        Pattern pattern = pattern(a, 1);
        int radius = a.intArg(2, "radius", 12, 1, 64);
        dev.syrkbuilder.core.shape.FloodFill.Mode mode = dev.syrkbuilder.core.shape.FloodFill.Mode.byName(a.string("mode", a.word(3) == null ? "hole" : a.word(3)));
        if (mode == null) {
            return Result.error("Fill modes: hole (default), connected, room.");
        }
        if (req.target() == null) {
            return Result.error("Aim at a block - the fill starts there.");
        }
        int[] t = req.target();
        dev.syrkbuilder.core.edit.EditBuffer out = new dev.syrkbuilder.core.edit.EditBuffer(maxVolume);
        boolean edge = dev.syrkbuilder.core.shape.FloodFill.fill(mode, world, t[0], t[1], t[2], radius, pattern, out);
        Result r = Result.edit("fill " + mode.id, out);
        return edge && mode != dev.syrkbuilder.core.shape.FloodFill.Mode.CONNECTED
            ? r.withWarnings(List.of("The fill reached its " + radius + "-block radius - the space isn't closed off there, so it stopped at the edge."))
            : r;
    }

    private Result tree(Request req, Args a, WorldView world) {
        String typeName = a.word(1) == null ? "oak" : a.lower(1);
        if (typeName.equals("list")) {
            List<String> lines = new ArrayList<>();
            lines.add("&6Trees &7(&f/sb tree <type> [height]&7):");
            for (dev.syrkbuilder.core.tree.Trees.Type t : dev.syrkbuilder.core.tree.Trees.Type.values()) {
                lines.add("&7- &f" + t.id + " &8" + t.description);
            }
            return Result.message(lines);
        }
        java.util.List<dev.syrkbuilder.core.tree.Trees.Type> types = dev.syrkbuilder.core.brush.Brushes.treeTypes(typeName);
        long seed = a.longValue("seed", System.nanoTime());
        dev.syrkbuilder.core.tree.Trees.Type type = types.get(new java.util.Random(seed).nextInt(types.size()));
        int height = a.intArg(2, "height", 0, 0, 64);
        int[] t = anchor(req);
        dev.syrkbuilder.core.edit.EditBuffer out = new dev.syrkbuilder.core.edit.EditBuffer(maxVolume);
        dev.syrkbuilder.core.tree.Trees.grow(type, world, t[0], t[1], t[2], height, seed, out);
        return Result.edit("tree " + type.id, out);
    }

    private Result path(Request req, Args a, WorldView world, Session session) {
        String sub = a.lower(1);
        List<int[]> points = session.path();
        switch (sub) {
            case "add", "point", "" -> {
                if (sub.isEmpty() && points.isEmpty()) {
                    return Result.message(List.of("&6Path&7: place points with &f/sb path add&7 (or left-click in the editor), then build:",
                        "&f/sb path <road|wall|tunnel|river|bridge|line> [width] [blocks]&7, &f/sb path undo&7 / &fclear"));
                }
                if (sub.isEmpty()) {
                    return Result.message("&7Path has &f" + points.size() + "&7 points. Build with &f/sb path road&7 (or wall, tunnel, river, bridge, line).");
                }
                int[] p = anchor(req);
                points.add(p.clone());
                return Result.message("&aPath point " + points.size() + " &7at " + p[0] + " " + p[1] + " " + p[2]
                    + (points.size() >= 2 ? " &8- build with /sb path road|wall|tunnel|river|bridge|line" : ""));
            }
            case "undo", "back" -> {
                if (!points.isEmpty()) {
                    points.remove(points.size() - 1);
                }
                return Result.message("&7Path has &f" + points.size() + "&7 points.");
            }
            case "clear" -> {
                points.clear();
                return Result.message("&7Path points cleared.");
            }
            default -> {
                dev.syrkbuilder.core.path.Paths.Kind kind = dev.syrkbuilder.core.path.Paths.Kind.byName(sub);
                if (kind == null) {
                    return Result.error("Usage: /sb path add|undo|clear, or /sb path <road|wall|tunnel|river|bridge|line> [width] [blocks]");
                }
                int width = a.intArg(2, "width", kind.defaultWidth, 1, 64);
                Pattern pattern = a.word(3) != null ? pattern(a, 3) : Pattern.parse(kind.defaultBlocks);
                int height = a.intValue("height", 5, 1, 64);
                dev.syrkbuilder.core.edit.EditBuffer out = new dev.syrkbuilder.core.edit.EditBuffer(maxVolume);
                dev.syrkbuilder.core.path.Paths.build(kind, points, width, height, pattern, world, out);
                return Result.edit("path " + kind.id, out);
            }
        }
    }

    private Result brush(Request req, Args a, WorldView world) {
        String typeName = a.lower(1);
        if (typeName.isEmpty() || typeName.equals("list")) {
            List<String> lines = new ArrayList<>();
            lines.add("&6Brushes &7(&f/sb brush <type> [radius] [blocks]&7, or &f/sb brush bind ...&7 then hold right-click):");
            for (dev.syrkbuilder.core.brush.BrushType t : dev.syrkbuilder.core.brush.BrushType.values()) {
                lines.add("&7- &f" + t.id() + " &8" + t.description);
            }
            return Result.message(lines);
        }
        dev.syrkbuilder.core.brush.BrushType type = dev.syrkbuilder.core.brush.BrushType.byName(typeName);
        if (type == null) {
            return Result.error("Unknown brush '" + typeName + "'. Try /sb brush list");
        }
        int radius = a.intArg(2, "radius", 4, 1, type.maxRadius());
        Pattern pattern = switch (type.blocks) {
            case REQUIRED -> pattern(a, 3);
            case OPTIONAL -> a.word(3) != null ? pattern(a, 3) : null;
            case NONE -> Pattern.of("stone");
        };
        double strength = type.usesStrength() ? a.doubleValue("strength", type.defaultStrength, 0, type.maxStrength) : 0.5;
        double density = a.doubleValue("density", switch (type) {
            case SPLATTER -> 0.5;
            case DECAY -> 0.3;
            case SPIKES -> 0.03;
            case TREES -> 0.02;
            default -> 0.15;
        }, 0, 1);
        Set<String> from = new HashSet<>();
        if (a.has("from")) {
            for (String b : Pattern.parse(a.string("from", "")).blocks()) {
                from.add(Pattern.baseId(b));
            }
        }
        dev.syrkbuilder.core.brush.Brushes.Settings settings = new dev.syrkbuilder.core.brush.Brushes.Settings(type, radius, pattern, strength,
            density, a.intValue("depth", 1, 1, 16), a.intValue("height", 1, 1, 16),
            a.doubleValue("scale", 0, 0, 256), a.flag("r"), from, System.nanoTime(), a.string("type", "oak"));
        if (type == dev.syrkbuilder.core.brush.BrushType.TREES) {
            dev.syrkbuilder.core.brush.Brushes.treeTypes(settings.variant());
        }
        int[] t = anchor(req);
        dev.syrkbuilder.core.edit.EditBuffer edits = dev.syrkbuilder.core.brush.Brushes.apply(settings, world, t[0], t[1], t[2], maxVolume);
        int stroke = a.intValue("stroke", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return new Result(Result.Kind.EDIT, "brush " + type.id(), edits, stroke, List.of(), null, List.of(), null);
    }

    private Result stamp(Request req, Args a, Session session) {
        BlockGrid clip = session.clipboard();
        if (clip == null) {
            return Result.error("The stamp brush paints your clipboard - /sb copy something first (or /sb template load <name>).");
        }
        int turns = a.flag("r") ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextInt(4);
        BlockGrid grid = turns == 0 ? clip : clip.transformed(turns, false);
        int[] t = anchor(req);
        int stroke = a.intValue("stroke", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        PasteStream paste = new PasteStream(grid, t[0] - grid.sizeX() / 2, t[1] + 1, t[2] - grid.sizeZ() / 2, true, Map.of());
        return new Result(Result.Kind.EDIT, "brush stamp", paste, stroke, List.of(), null, List.of(), null);
    }

    private Result terrain(Request req, Args a, WorldView world) {
        String typeName = a.lower(1);
        if (typeName.isEmpty() || typeName.equals("list")) {
            List<String> lines = new ArrayList<>();
            lines.add("&6Terrain types&7:");
            for (TerrainType t : TerrainType.values()) {
                lines.add("&7- &f" + t.id() + " &8(radius " + t.defaultRadius + ", height " + t.defaultHeight + ", style " + t.defaultStyle + ")");
            }
            lines.add("&6Styles&7: &f" + String.join(", ", TerrainStyle.NAMES));
            return Result.message(lines);
        }
        TerrainType type = TerrainType.byName(typeName);
        if (type == null) {
            return Result.error("Unknown terrain '" + typeName + "'. Try /sb terrain list");
        }
        TerrainParams params = TerrainParams.parse(type, a, req.yaw());
        if (TerrainStyle.byName(params.style) == null) {
            return Result.error("Unknown style '" + params.style + "'. Styles: " + String.join(", ", TerrainStyle.NAMES));
        }
        int[] t = anchor(req);
        TerrainJob job = new TerrainJob(params, t[0], t[1], t[2], world).prepare();
        if (job.estimate() > maxVolume) {
            return Result.error("That terrain would change about " + job.estimate() + " blocks - the limit is " + maxVolume + ". Try a smaller radius or height.");
        }
        return Result.edit("terrain " + type.id() + " (seed " + params.seed + ")", job);
    }
}
