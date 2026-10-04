package dev.syrkbuilder.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.syrkbuilder.core.command.Args;
import dev.syrkbuilder.core.command.Completer;
import dev.syrkbuilder.core.edit.ViewRay;
import dev.syrkbuilder.core.grid.BlockGrid;
import dev.syrkbuilder.core.grid.GridCodec;
import dev.syrkbuilder.core.model.ModelImporter;
import dev.syrkbuilder.core.protocol.Protocol;
import dev.syrkbuilder.core.protocol.Request;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SyrkBuilderClient implements ClientModInitializer {
    private static final double REACH = 256.0;

    static final String[] MODEL_TYPES = {".obj", ".glb", ".gltf", ".vox"};
    static final String[] IMAGE_TYPES = {".png", ".jpg", ".jpeg"};
    static final String[] SCRIPT_TYPES = {".js"};


    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("syrkbuilder", "main"));
    private static final boolean SERVER_SUPPORT = false;

    private static KeyMapping editorKey;
    private static KeyMapping noclipKey;
    private final SelectionParticles particles = new SelectionParticles();

    @Override
    public void onInitializeClient() {
        Settings.load();
        LocalFiles.models();
        LocalFiles.heightmaps();
        LocalFiles.scripts();
        LocalBackend.init();
        if (SERVER_SUPPORT) {
            PayloadTypeRegistry.playC2S().register(SyrkPayload.TYPE, SyrkPayload.CODEC);
            PayloadTypeRegistry.playS2C().register(SyrkPayload.TYPE, SyrkPayload.CODEC);
            ClientPlayNetworking.registerGlobalReceiver(SyrkPayload.TYPE, (payload, context) -> {
                Protocol.Data data = Protocol.decodeData(payload.data());
                if (data != null) {
                    ClientData.accept(data);
                }
            });
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientData.clear();
            NoClip.clear();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("sb")
                .executes(ctx -> forward(Feedback.of(ctx), "help"))
                .then(ClientCommandManager.literal("pos1").executes(ctx -> setPos(Feedback.of(ctx), true)))
                .then(ClientCommandManager.literal("pos2").executes(ctx -> setPos(Feedback.of(ctx), false)))
                .then(ClientCommandManager.literal("sel").executes(ctx -> showSelection(Feedback.of(ctx))))
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    Selection.clear();
                    Feedback.of(ctx).info("§7Selection cleared.");
                    return 1;
                }))
                .then(ClientCommandManager.literal("settings").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(EditorScreen.at(EditorScreen.SETTINGS)));
                    return 1;
                }))
                .then(ClientCommandManager.literal("noclip").executes(ctx -> {
                    toggleNoclip(Feedback.of(ctx));
                    return 1;
                }))
                .then(ClientCommandManager.literal("wand").executes(ctx -> {
                    Settings.wand = !Settings.wand;
                    Settings.save();
                    Feedback.of(ctx).info(Settings.wand
                        ? "§aGolden axe wand on §7- left-click = pos1, right-click = pos2."
                        : "§7Golden axe wand off.");
                    return 1;
                }))
                .then(ClientCommandManager.literal("import")
                    .executes(ctx -> {
                        listFiles(Feedback.of(ctx), "Models", LocalFiles.models(), MODEL_TYPES);
                        return listFiles(Feedback.of(ctx), "Heightmaps", LocalFiles.heightmaps(), IMAGE_TYPES);
                    })
                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                        .suggests(suggestions("import"))
                        .executes(ctx -> importModel(Feedback.of(ctx), StringArgumentType.getString(ctx, "args")))))
                .then(ClientCommandManager.literal("script")
                    .executes(ctx -> listFiles(Feedback.of(ctx), "Scripts", LocalFiles.scripts(), SCRIPT_TYPES))
                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                        .suggests(suggestions("script"))
                        .executes(ctx -> runScript(Feedback.of(ctx), StringArgumentType.getString(ctx, "args")))))
                .then(ClientCommandManager.literal("brush")
                    .executes(ctx -> forward(Feedback.of(ctx), "brush list"))
                    .then(ClientCommandManager.literal("bind")
                        .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                            .suggests(suggestions("brush"))
                            .executes(ctx -> {
                                BrushBindings.bind(Feedback.of(ctx), StringArgumentType.getString(ctx, "args"));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("unbind").executes(ctx -> {
                        BrushBindings.unbind(Feedback.of(ctx));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("binds").executes(ctx -> {
                        BrushBindings.list(Feedback.of(ctx));
                        return 1;
                    }))
                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                        .suggests(suggestions("brush"))
                        .executes(ctx -> forward(Feedback.of(ctx), "brush " + StringArgumentType.getString(ctx, "args")))))
                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                    .suggests(suggestions(null))
                    .executes(ctx -> forward(Feedback.of(ctx), StringArgumentType.getString(ctx, "command"))))));

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() || !isWand(player)) {
                return InteractionResult.PASS;
            }
            if (!pos.equals(Selection.pos1())) {
                Selection.setPos1(pos);
                Chat.say(Component.literal("§dpos1 §7set to §f" + Selection.describe(pos) + volumeSuffix()));
            }
            return InteractionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, level, hand) ->
            level.isClientSide() && BrushBindings.boundTo(player) != null ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && BrushBindings.boundTo(player) != null) {
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide() || !isWand(player)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (!pos.equals(Selection.pos2())) {
                Selection.setPos2(pos);
                Chat.say(Component.literal("§dpos2 §7set to §f" + Selection.describe(pos) + volumeSuffix()));
            }
            return InteractionResult.FAIL;
        });

        editorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.syrkbuilder.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, KEY_CATEGORY));
        noclipKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.syrkbuilder.noclip", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (blocked() != null) {
                NoClip.clear();
                heldNoclip = false;
            }
            particles.tick(client);
            EditorMovement.tick(client);
            BrushBindings.tick(client);
            if (Settings.noclipHold) {
                while (noclipKey.consumeClick()) {
                }
                holdNoclip(client);
            } else {
                while (noclipKey.consumeClick()) {
                    if (client.screen == null) {
                        toggleNoclip(Feedback.chat());
                    }
                }
            }
            applyFlySpeed(client);
            while (editorKey.consumeClick()) {
                if (client.screen == null && client.player != null) {
                    String why = blocked();
                    if (why != null) {
                        Feedback.chat().error(why);
                    } else {
                        client.setScreen(new EditorScreen());
                    }
                }
            }
        });
    }

    private static boolean isWand(Player player) {
        return Settings.wand && blocked() == null && player.getMainHandItem().is(Items.GOLDEN_AXE);
    }

    static String volumeSuffix() {
        return Selection.complete() ? " §8(" + Selection.volume() + " blocks)" : "";
    }

    private static double cursorX;
    private static double cursorY;
    private static int cursorW;
    private static int cursorH;

    static void cursor(double x, double y, int screenW, int screenH) {
        if (!Settings.aimPointer) {
            clearCursor();
            return;
        }
        cursorX = x;
        cursorY = y;
        cursorW = screenW;
        cursorH = screenH;
    }

    static void clearCursor() {
        cursorW = 0;
        cursorH = 0;
    }

    static boolean cursorAim() {
        return cursorW > 0 && cursorH > 0;
    }

    static BlockPos lookedAt(LocalPlayer player) {
        if (cursorAim()) {
            return underCursor(player);
        }
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().immutable();
        }
        return null;
    }

    private static BlockPos underCursor(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        var f = camera.forwardVector();
        var l = camera.leftVector();
        var u = camera.upVector();
        double[] d = ViewRay.direction(new double[]{f.x(), f.y(), f.z()}, new double[]{l.x(), l.y(), l.z()}, new double[]{u.x(), u.y(), u.z()},
            mc.options.fov().get(), (double) cursorW / cursorH, (cursorX / cursorW) * 2 - 1, 1 - (cursorY / cursorH) * 2);
        Vec3 from = camera.position();
        Vec3 to = from.add(d[0] * REACH, d[1] * REACH, d[2] * REACH);
        BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().immutable() : null;
    }

    static int setPos(Feedback fb, boolean first) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        BlockPos pos = lookedAt(player);
        if (pos == null) {
            pos = player.blockPosition();
        }
        if (first) {
            Selection.setPos1(pos);
        } else {
            Selection.setPos2(pos);
        }
        fb.info("§d" + (first ? "pos1" : "pos2") + " §7set to §f" + Selection.describe(pos) + volumeSuffix());
        return 1;
    }

    static int showSelection(Feedback fb) {
        fb.info("§7pos1: §f" + Selection.describe(Selection.pos1())
            + " §7pos2: §f" + Selection.describe(Selection.pos2()) + volumeSuffix());
        return 1;
    }

    static int forward(Feedback fb, String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        String why = blocked();
        if (why != null) {
            fb.error(why);
            return 0;
        }
        BlockPos target = lookedAt(player);
        BlockPos feet = player.blockPosition();
        Request request = new Request(command, arr(target), arr(Selection.pos1()), arr(Selection.pos2()), arr(feet),
            player.getYRot(), player.getXRot());
        transport(Protocol.encode(request));
        return 1;
    }

    private static List<String> blockIds;

    private static final Completer COMPLETER = new Completer(
        SyrkBuilderClient::blockIds,
        ClientData::templates,
        () -> {
            List<String> names = new java.util.ArrayList<>();
            for (String f : LocalFiles.list(LocalFiles.scripts(), SCRIPT_TYPES)) {
                names.add(f.substring(0, f.length() - 3));
            }
            return names;
        },
        SyrkBuilderClient::importables);

    static boolean isImage(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String ext : IMAGE_TYPES) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    static List<String> importables() {
        List<String> all = new java.util.ArrayList<>(LocalFiles.list(LocalFiles.models(), MODEL_TYPES));
        all.addAll(LocalFiles.list(LocalFiles.heightmaps(), IMAGE_TYPES));
        return all;
    }

    private static List<String> blockIds() {
        if (blockIds == null) {
            List<String> ids = new java.util.ArrayList<>();
            for (var id : BuiltInRegistries.BLOCK.keySet()) {
                ids.add(id.toString());
            }
            ids.sort(null);
            blockIds = ids;
        }
        return blockIds;
    }

    private static SuggestionProvider<FabricClientCommandSource> suggestions(String command) {
        return (ctx, builder) -> {
            String typed = builder.getRemaining();
            var offset = builder.createOffset(builder.getStart() + Completer.wordStart(typed));
            for (String s : COMPLETER.complete(typed, command)) {
                offset.suggest(s);
            }
            return offset.buildFuture();
        };
    }

    static int listFiles(Feedback fb, String title, Path dir, String... types) {
        List<String> names = LocalFiles.list(dir, types);
        fb.info(names.isEmpty()
            ? "§7No files yet - put them in §f" + dir
            : "§6" + title + "§7: §f" + String.join(", ", names));
        return 1;
    }

    static int importModel(Feedback fb, String line) {
        Args args = Args.parse(line);
        String name = args.word(0);
        Path folder = name != null && isImage(name) ? LocalFiles.heightmaps() : LocalFiles.models();
        Path file = name == null ? null : LocalFiles.resolve(folder, name);
        if (file == null || !Files.isRegularFile(file)) {
            fb.error("No " + (folder.equals(LocalFiles.heightmaps()) ? "heightmap" : "model") + " '" + name + "' in " + folder);
            return 0;
        }
        String why = blocked();
        if (why != null) {
            fb.error(why);
            return 0;
        }
        ModelImporter.Options options;
        try {
            options = new ModelImporter.Options(args.intValue("size", isImage(name) ? 128 : 48, 2, 512), args.flag("s") || args.flag("solid"),
                args.string("palette", "all"), args.intValue("height", 0, 0, 384));
        } catch (IllegalArgumentException e) {
            fb.error(e.getMessage());
            return 0;
        }
        String pasteOptions = args.rest(1);
        fb.info("§7Converting §f" + name + "§7...");
        List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        CompletableFuture.supplyAsync(() -> {
            try {
                BlockGrid grid = ModelImporter.importFile(file, options, warnings);
                return GridCodec.encode(grid);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }).whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                Chat.say(Component.literal("§cCouldn't import " + name + ": " + cause.getMessage()));
                return;
            }
            for (String warning : warnings) {
                Chat.say(Component.literal("§e" + warning));
            }
            String safeName = name.replaceAll("[^A-Za-z0-9_.-]", "_");
            upload(player, bytes, id -> "upload-paste " + id + " name=" + safeName + " " + pasteOptions);
            Chat.say(Component.literal(String.format("§7Uploading §f%s §7(%,d KB)...", name, Math.max(1, bytes.length / 1024))));
        }));
        return 1;
    }

    static int runScript(Feedback fb, String line) {
        Args args = Args.parse(line);
        String name = args.word(0);
        String base = name.toLowerCase(Locale.ROOT).endsWith(".js") ? name.substring(0, name.length() - 3) : name;
        Path file = LocalFiles.resolve(LocalFiles.scripts(), base + ".js");
        if (file == null || !Files.isRegularFile(file)) {
            return forward(fb, "script " + line);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        String why = blocked();
        if (why != null) {
            fb.error(why);
            return 0;
        }
        try {
            byte[] source = Files.readString(file, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            String rest = args.rest(1);
            String safeName = base.replaceAll("[^A-Za-z0-9_-]", "_");
            upload(player, source, id -> "upload-script " + id + " " + safeName + (rest.isEmpty() ? "" : " " + rest));
            return 1;
        } catch (Exception e) {
            fb.error("Couldn't read " + file + ": " + e.getMessage());
            return 0;
        }
    }

    private static void upload(LocalPlayer player, byte[] data, java.util.function.IntFunction<String> command) {
        int id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        for (byte[] packet : Protocol.encodeUpload(id, data)) {
            transport(packet);
        }
        BlockPos target = lookedAt(player);
        Request request = new Request(command.apply(id), arr(target), arr(Selection.pos1()), arr(Selection.pos2()), arr(player.blockPosition()),
            player.getYRot(), player.getXRot());
        transport(Protocol.encode(request));
    }

    static void toggleNoclip(Feedback fb) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String why = blocked();
        if (why != null) {
            fb.error(why);
            return;
        }
        if (LocalBackend.active()) {
            if (!player.getAbilities().mayfly) {
                fb.error("Noclip needs creative mode (you have to be able to fly).");
                return;
            }
            boolean on = NoClip.toggle(player.getUUID());
            if (on) {
                player.getAbilities().flying = true;
            }
            fb.info(on ? "§aNoclip on §7- fly straight through blocks. §8(N or /sb noclip to turn off)" : "§7Noclip off.");
        } else if (available()) {
            forward(fb, "noclip");
        } else {
            fb.error("This server doesn't have the SyrkBuilder plugin, so noclip isn't available.");
        }
    }

    private static boolean heldNoclip;

    private static void holdNoclip(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !LocalBackend.active()) {
            return;
        }
        boolean held = noclipKey.isDown() && player.getAbilities().mayfly;
        if (held != heldNoclip) {
            heldNoclip = held;
            NoClip.set(player.getUUID(), held);
            if (held) {
                player.getAbilities().flying = true;
            }
        }
    }

    private static void applyFlySpeed(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !player.getAbilities().mayfly || blocked() != null) {
            return;
        }
        if (LocalBackend.active() || Settings.flySpeed != 1.0) {
            player.getAbilities().setFlyingSpeed((float) (0.05 * Settings.flySpeed));
        }
    }

    static KeyMapping editorKey() {
        return editorKey;
    }

    static KeyMapping noclipKey() {
        return noclipKey;
    }

    static boolean noclipOn() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (NoClip.active(player.getUUID()) || !LocalBackend.active() && player.isSpectator());
    }

    static void sync() {
        if (Minecraft.getInstance().player != null && available()) {
            forward(SILENT, "sync");
        }
    }

    private static final Feedback SILENT = new Feedback() {
        @Override
        public void info(String message) {
        }

        @Override
        public void error(String message) {
        }
    };

    static String blocked() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "Join a world first.";
        }
        if (!LocalBackend.active()) {
            if (!SERVER_SUPPORT) {
                return "SyrkBuilder doesn't work on servers yet - only in your own singleplayer worlds.";
            }
            if (!ClientPlayNetworking.canSend(SyrkPayload.TYPE)) {
                return "This server doesn't have the SyrkBuilder plugin - edits need it installed.";
            }
        }
        return player.isCreative() ? null : "SyrkBuilder only works in creative mode.";
    }

    static boolean available() {
        return blocked() == null;
    }

    private static void transport(byte[] message) {
        if (LocalBackend.active()) {
            LocalBackend.send(message);
        } else if (SERVER_SUPPORT) {
            ClientPlayNetworking.send(new SyrkPayload(message));
        }
    }

    private static int[] arr(BlockPos pos) {
        return pos == null ? null : new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }
}
