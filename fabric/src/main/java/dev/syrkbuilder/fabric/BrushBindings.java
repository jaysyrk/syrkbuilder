package dev.syrkbuilder.fabric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class BrushBindings {
    private static final int DAB_TICKS = 4;

    private static final Map<String, String> BINDINGS = new LinkedHashMap<>();
    private static boolean loaded;
    private static int stroke;
    private static int cooldown;

    private BrushBindings() {
    }

    private static Path file() {
        return LocalFiles.root().resolve("brushes.txt");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (Files.isRegularFile(file())) {
                for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                    int eq = line.indexOf('=');
                    if (eq > 0 && !line.startsWith("#")) {
                        BINDINGS.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder("# item=brush settings (edit with /sb brush bind / unbind)\n");
        BINDINGS.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        try {
            Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    static String boundTo(Player player) {
        load();
        ItemStack stack = player.getMainHandItem();
        return stack.isEmpty() ? null : BINDINGS.get(itemId(stack));
    }

    static void bind(Feedback fb, String args) {
        load();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            fb.error("Hold the item you want to use as the brush (e.g. a blaze rod).");
            return;
        }
        String type = args.trim().split("\\s+")[0];
        if (dev.syrkbuilder.core.brush.BrushType.byName(type) == null) {
            fb.error("Unknown brush '" + type + "'. Try /sb brush list");
            return;
        }
        BINDINGS.put(itemId(stack), args.trim());
        save();
        fb.info("§aBound §f" + args.trim() + " §ato §f" + itemId(stack) + "§a. Hold right-click to paint. §7/sb brush unbind to remove.");
    }

    static void unbind(Feedback fb) {
        load();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String id = player.getMainHandItem().isEmpty() ? null : itemId(player.getMainHandItem());
        if (id != null && BINDINGS.remove(id) != null) {
            save();
            fb.info("§7Brush removed from §f" + id);
        } else {
            fb.error("The item you're holding has no brush.");
        }
    }

    static void list(Feedback fb) {
        load();
        if (BINDINGS.isEmpty()) {
            fb.info("§7No brushes bound. Hold an item and §f/sb brush bind <type> [radius] [blocks]");
            return;
        }
        fb.info("§6Bound brushes:");
        BINDINGS.forEach((k, v) -> fb.info("§7- §f" + k + " §8→ §f" + v));
    }

    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        String brush = player == null || mc.screen != null ? null : boundTo(player);
        if (brush == null || !mc.options.keyUse.isDown()) {
            stroke = 0;
            cooldown = 0;
            return;
        }
        if (stroke == 0) {
            stroke = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        }
        if (cooldown-- > 0) {
            return;
        }
        cooldown = DAB_TICKS - 1;
        BlockPos target = SyrkBuilderClient.lookedAt(player);
        if (target != null) {
            SyrkBuilderClient.forward(QUIET, "brush " + brush + " stroke=" + stroke);
        }
    }

    static final Feedback QUIET = new Feedback() {
        @Override
        public void info(String message) {
        }

        @Override
        public void error(String message) {
            Feedback.chat().error(message);
        }
    };
}
