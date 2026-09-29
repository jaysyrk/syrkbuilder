package dev.syrkbuilder.fabric;

import dev.syrkbuilder.core.model.BlockPalette;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

final class ColorPicker {
    private static final int BG = 0xF416181D;
    private static final int EDGE = 0xFF262932;
    private static final int ITEM = 0xFF20232B;
    private static final int ITEM_HOVER = 0xFF2B2F3A;
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int ACCENT_DARK = 0xFF2F4F99;
    private static final int TEXT = 0xFFE6E8EE;
    private static final int DIM = 0xFF8B90A0;

    private static final String[] PALETTES = {"all", "concrete", "wool", "terracotta", "natural", "wood"};
    private static final String[] PALETTE_LABELS = {"all", "conc.", "wool", "terra.", "stone", "wood"};
    private static final int[] STEPS = {3, 4, 5, 6, 8, 10};
    private static final String[] DIRECTIONS = {"up", "down", "+x", "-x", "+z", "-z", "look", "out", "in"};
    private static final String[] DIRECTION_WORDS = {"up", "down", "east", "west", "south", "north", null, "out", "in"};
    private static final String[] DIRECTION_HINTS = {"Bottom to top", "Top to bottom", "Along +x (east)", "Along -x (west)",
        "Along +z (south)", "Along -z (north)", "The way you're looking now - click again to re-aim", "Centre outwards", "Edge inwards"};
    private static final int LOOK = 6;

    static final int W = 240;
    // The tallest the picker gets (the Gradient tab), for placing it on screen.
    static final int H = 236;
    private static final int GRADIENT_ROWS = 36;

    private static float hue = 25;
    private static float sat = 0.75f;
    private static float val = 0.8f;
    private static int palette;
    private static boolean gradient;
    private static final int[] ends = {0x3A3A3A, 0xE9ECEC};
    private static int editing;
    private static int steps = 2;
    private static int direction;
    private static String lookToken = "0/1/0";
    private static Double rangeFrom;
    private static Double rangeTo;

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private final Consumer<String> target;
    private final Runnable close;
    private final int x;
    private final int y;
    private String hover;
    private int dragging = -1;

    ColorPicker(int x, int y, Consumer<String> target, Runnable close) {
        this.x = x;
        this.y = y;
        this.target = target;
        this.close = close;
        if (gradient) {
            setHsv(ends[editing]);
        }
    }

    static void gradientMode(boolean on) {
        gradient = on;
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + height();
    }

    private int height() {
        return useY(blocksTop(), gradient ? gradientBlocks().size() : nearest().size()) - y + 30;
    }

    // Gradient steps sit in one row so the tab stays short enough for small screens.
    private static int perRow(int count) {
        return gradient ? Math.max(6, count) : 6;
    }

    private int rowsTop() {
        return y + SQ_Y + SQ + 6;
    }

    private int paletteY() {
        return rowsTop() + (gradient ? GRADIENT_ROWS : 0);
    }

    private int blocksTop() {
        return paletteY() + 20;
    }

    static int hsv(float h, float s, float v) {
        float c = v * s;
        float hp = (h % 360) / 60f;
        float xx = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0;
        float g = 0;
        float b = 0;
        if (hp < 1) {
            r = c;
            g = xx;
        } else if (hp < 2) {
            r = xx;
            g = c;
        } else if (hp < 3) {
            g = c;
            b = xx;
        } else if (hp < 4) {
            g = xx;
            b = c;
        } else if (hp < 5) {
            r = xx;
            b = c;
        } else {
            r = c;
            b = xx;
        }
        float m = v - c;
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private static void setHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) {
            h = hue;
        } else if (max == r) {
            h = 60 * (((g - b) / d) % 6);
        } else if (max == g) {
            h = 60 * ((b - r) / d + 2);
        } else {
            h = 60 * ((r - g) / d + 4);
        }
        hue = (h + 360) % 360;
        sat = max == 0 ? 0 : d / max;
        val = max;
    }

    private static int current() {
        return hsv(hue, sat, val);
    }

    static ItemStack icon(String block) {
        return ICONS.computeIfAbsent(block, id -> {
            try {
                String base = id.contains("[") ? id.substring(0, id.indexOf('[')) : id;
                Item item = BuiltInRegistries.BLOCK.getValue(Identifier.parse(base)).asItem();
                return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            } catch (RuntimeException e) {
                return ItemStack.EMPTY;
            }
        });
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    static String aimedBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        BlockPos pos = SyrkBuilderClient.lookedAt(mc.player);
        if (pos == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
    }

    private static Integer aimedColor() {
        Minecraft mc = Minecraft.getInstance();
        String id = aimedBlock();
        if (id == null) {
            return null;
        }
        Integer known = BlockPalette.colorOf(id);
        if (known != null) {
            return known;
        }
        BlockPos pos = SyrkBuilderClient.lookedAt(mc.player);
        BlockState state = mc.level.getBlockState(pos);
        return state.getMapColor(mc.level, pos).col;
    }

    private List<String> nearest() {
        return BlockPalette.named(PALETTES[palette]).nearest(current(), 12);
    }

    private List<String> gradientBlocks() {
        try {
            return BlockPalette.gradient(String.format("#%06X", ends[0]), String.format("#%06X", ends[1]), STEPS[steps], PALETTES[palette]);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private String gradientPattern() {
        List<String> names = new ArrayList<>();
        for (String b : gradientBlocks()) {
            names.add(shortId(b));
        }
        String opts = direction == LOOK ? lookToken : DIRECTION_WORDS[direction];
        boolean ranged = hasRange();
        if (ranged) {
            opts += "," + number(rangeFrom) + ".." + number(rangeTo);
        }
        return (direction == 0 && !ranged ? "grad:" : "grad(" + opts + "):") + String.join(",", names);
    }

    private static String palettePattern(List<String> blocks) {
        List<String> names = new ArrayList<>();
        for (String b : blocks) {
            names.add(shortId(b));
        }
        return String.join(",", names);
    }

    private static boolean radial() {
        return direction > LOOK;
    }

    private static boolean hasRange() {
        return rangeFrom != null && rangeTo != null && !radial();
    }

    // Where the aimed block sits along the gradient's direction, measured the same way the gradient measures it.
    private static Double aimedAlong() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        BlockPos pos = SyrkBuilderClient.lookedAt(mc.player);
        if (pos == null) {
            return null;
        }
        return switch (direction) {
            case 0, 1 -> (double) pos.getY();
            case 2, 3 -> (double) pos.getX();
            case 4, 5 -> (double) pos.getZ();
            default -> {
                String[] c = lookToken.split("/");
                double dx = Double.parseDouble(c[0]);
                double dy = Double.parseDouble(c[1]);
                double dz = Double.parseDouble(c[2]);
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                yield (pos.getX() * dx + pos.getY() * dy + pos.getZ() * dz) / len;
            }
        };
    }

    private static void aimLook() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 v = mc.player.getViewVector(1f);
        lookToken = number(v.x) + "/" + number(v.y) + "/" + number(v.z);
    }

    private static String number(double v) {
        return java.math.BigDecimal.valueOf(Math.round(v * 10000) / 10000.0).stripTrailingZeros().toPlainString();
    }

    private static final int SQ_X = 8;
    private static final int SQ_Y = 24;
    private static final int SQ = 96;
    private static final int HUE_X = 108;
    private static final int RIGHT_X = 128;

    void render(GuiGraphics g, Font font, int mx, int my) {
        hover = null;
        int h = height();
        g.fill(x - 1, y - 1, x + W + 1, y + h + 1, EDGE);
        g.fill(x, y, x + W, y + h, BG);
        tab(g, font, x + 8, y + 5, "Colour", !gradient, mx, my);
        tab(g, font, x + 60, y + 5, "Gradient", gradient, mx, my);
        boolean closeHover = mx >= x + W - 18 && mx < x + W - 4 && my >= y + 4 && my < y + 18;
        text(g, font, "✕", x + W - 14, y + 7, closeHover ? TEXT : DIM);

        int cell = 4;
        for (int j = 0; j < SQ / cell; j++) {
            for (int i = 0; i < SQ / cell; i++) {
                float s = (i + 0.5f) / (SQ / cell);
                float v = 1 - (j + 0.5f) / (SQ / cell);
                g.fill(x + SQ_X + i * cell, y + SQ_Y + j * cell, x + SQ_X + (i + 1) * cell, y + SQ_Y + (j + 1) * cell, 0xFF000000 | hsv(hue, s, v));
            }
        }
        int kx = x + SQ_X + Math.round(sat * (SQ - 1));
        int ky = y + SQ_Y + Math.round((1 - val) * (SQ - 1));
        outline(g, kx - 2, ky - 2, 5, 5, 0xFFFFFFFF);
        for (int j = 0; j < SQ / 2; j++) {
            g.fill(x + HUE_X, y + SQ_Y + j * 2, x + HUE_X + 12, y + SQ_Y + j * 2 + 2, 0xFF000000 | hsv(j * 360f / (SQ / 2), 1, 1));
        }
        int hy = y + SQ_Y + Math.round(hue / 360f * (SQ - 1));
        g.fill(x + HUE_X - 2, hy, x + HUE_X + 14, hy + 1, 0xFFFFFFFF);

        int rx = x + RIGHT_X;
        int rw = W - RIGHT_X - 8;
        if (!gradient) {
            g.fill(rx, y + SQ_Y, rx + rw, y + SQ_Y + 26, 0xFF000000 | current());
            text(g, font, String.format("#%06X", current()), rx, y + SQ_Y + 30, DIM);
            button(g, font, rx, y + SQ_Y + 44, rw, "Eyedropper", "Take the colour of the block you aim at", mx, my, false);
            button(g, font, rx, y + SQ_Y + 62, rw, "Use aimed block", "Put the exact block you aim at into the field", mx, my, false);
        } else {
            for (int e = 0; e < 2; e++) {
                int sy = y + SQ_Y + e * 22;
                boolean on = editing == e;
                g.fill(rx, sy, rx + 20, sy + 18, 0xFF000000 | ends[e]);
                if (on) {
                    outline(g, rx - 1, sy - 1, 22, 20, ACCENT);
                }
                text(g, font, (e == 0 ? "From " : "To ") + String.format("#%06X", ends[e]), rx + 24, sy + 5, on ? TEXT : DIM);
                if (mx >= rx && mx < rx + rw && my >= sy && my < sy + 18) {
                    hover = "Edit the " + (e == 0 ? "start" : "end") + " colour with the picker";
                }
            }
            button(g, font, rx, y + SQ_Y + 46, rw, "Eyedropper", "Set the selected end to the colour of the block you aim at", mx, my, false);
            segments(g, font, rx, y + SQ_Y + 64, rw, labels(STEPS), steps, mx, my, repeat("Steps", STEPS.length));
            int ry = rowsTop();
            segments(g, font, x + 8, ry, W - 16, DIRECTIONS, direction, mx, my, DIRECTION_HINTS);
            if (radial()) {
                text(g, font, "Range works with straight directions", x + 8, ry + 21, DIM);
            } else {
                int bw = (W - 16 - 6) / 3;
                button(g, font, x + 8, ry + 18, bw, rangeFrom == null ? "Start at aim" : "from " + number(rangeFrom),
                    "Aim where the first block should go, then click", mx, my, false);
                button(g, font, x + 8 + bw + 3, ry + 18, bw, rangeTo == null ? "End at aim" : "to " + number(rangeTo),
                    "Aim where the last block should go, then click", mx, my, false);
                button(g, font, x + 8 + (bw + 3) * 2, ry + 18, W - 16 - (bw + 3) * 2, hasRange() ? "Fit" : "Fit ✓",
                    "No range: the gradient stretches over each shape or brush dab", mx, my, false);
            }
        }

        segments(g, font, x + 8, paletteY(), W - 16, PALETTE_LABELS, palette, mx, my, repeat("Which blocks to choose from", PALETTE_LABELS.length));

        int by = blocksTop();
        List<String> blocks = gradient ? gradientBlocks() : nearest();
        int per = perRow(blocks.size());
        int cw = (W - 16) / per;
        for (int i = 0; i < blocks.size(); i++) {
            int cx = x + 8 + (i % per) * cw;
            int cy = by + (i / per) * 22;
            String b = blocks.get(i);
            boolean over = mx >= cx && mx < cx + cw - 2 && my >= cy && my < cy + 20;
            g.fill(cx, cy, cx + cw - 2, cy + 20, over ? ITEM_HOVER : ITEM);
            Integer c = BlockPalette.colorOf(b);
            if (c != null) {
                g.fill(cx, cy + 18, cx + cw - 2, cy + 20, 0xFF000000 | c);
            }
            ItemStack stack = icon(b);
            if (!stack.isEmpty()) {
                g.renderItem(stack, cx + (cw - 2 - 16) / 2, cy + 1);
            }
            if (over) {
                hover = shortId(b) + (gradient ? "" : " - click to use");
            }
        }
        if (gradient) {
            button(g, font, x + 8, useY(by, blocks.size()), W - 16, "Use gradient", gradientPattern(), mx, my, true);
        } else if (!blocks.isEmpty()) {
            button(g, font, x + 8, useY(by, blocks.size()), W - 16, "Use palette", "Mix all " + blocks.size() + " blocks shown, evenly", mx, my, true);
        }
        String foot = hover != null ? hover : gradient ? "Pick two colours; the blocks between are blended." : "Pick a colour; click a block to use it.";
        text(g, font, fit(font, foot, W - 16), x + 8, y + h - 12, DIM);
    }

    private static String[] repeat(String s, int n) {
        String[] out = new String[n];
        java.util.Arrays.fill(out, s);
        return out;
    }

    private static String[] labels(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = String.valueOf(values[i]);
        }
        return out;
    }

    boolean mouseClicked(double mx, double my) {
        if (!contains(mx, my)) {
            close.run();
            return true;
        }
        if (my >= y + 4 && my < y + 18) {
            if (mx >= x + W - 18) {
                close.run();
            } else if (mx >= x + 8 && mx < x + 56) {
                gradient = false;
            } else if (mx >= x + 60 && mx < x + 116) {
                gradient = true;
                setHsv(ends[editing]);
            }
            return true;
        }
        List<String> blocks = gradient ? gradientBlocks() : nearest();
        int use = useY(blocksTop(), blocks.size());
        if (my >= use && my < use + 14 && mx >= x + 8 && mx < x + W - 8 && (gradient || !blocks.isEmpty())) {
            target.accept(gradient ? gradientPattern() : palettePattern(blocks));
            close.run();
            return true;
        }
        if (inSquare(mx, my)) {
            dragging = 0;
            drag(mx, my);
            return true;
        }
        if (inHue(mx, my)) {
            dragging = 1;
            drag(mx, my);
            return true;
        }
        int rx = x + RIGHT_X;
        int rw = W - RIGHT_X - 8;
        if (mx >= rx && mx < rx + rw && my < y + SQ_Y + SQ) {
            if (!gradient) {
                if (in(my, y + SQ_Y + 44)) {
                    eyedropper();
                } else if (in(my, y + SQ_Y + 62)) {
                    String b = aimedBlock();
                    if (b != null) {
                        target.accept(shortId(b));
                        close.run();
                    }
                }
            } else {
                for (int e = 0; e < 2; e++) {
                    int sy = y + SQ_Y + e * 22;
                    if (my >= sy && my < sy + 18) {
                        editing = e;
                        setHsv(ends[e]);
                    }
                }
                if (in(my, y + SQ_Y + 46)) {
                    eyedropper();
                } else if (in(my, y + SQ_Y + 64)) {
                    steps = segment(mx, rx, rw, STEPS.length);
                }
            }
            return true;
        }
        boolean wide = mx >= x + 8 && mx < x + W - 8;
        if (gradient && wide && in(my, rowsTop())) {
            int picked = segment(mx, x + 8, W - 16, DIRECTIONS.length);
            if (picked != direction || picked == LOOK) {
                rangeFrom = null;
                rangeTo = null;
            }
            direction = picked;
            if (picked == LOOK) {
                aimLook();
            }
            return true;
        }
        if (gradient && wide && in(my, rowsTop() + 18) && !radial()) {
            int bw = (W - 16 - 6) / 3;
            int which = mx < x + 8 + bw + 2 ? 0 : mx < x + 8 + (bw + 3) * 2 - 1 ? 1 : 2;
            if (which == 2) {
                rangeFrom = null;
                rangeTo = null;
            } else {
                Double at = aimedAlong();
                if (at != null) {
                    if (which == 0) {
                        rangeFrom = at;
                    } else {
                        rangeTo = at;
                    }
                }
            }
            return true;
        }
        if (in(my, paletteY()) && wide) {
            palette = segment(mx, x + 8, W - 16, PALETTES.length);
            return true;
        }
        int by = blocksTop();
        int per = perRow(blocks.size());
        int cw = (W - 16) / per;
        for (int i = 0; i < blocks.size(); i++) {
            int cx = x + 8 + (i % per) * cw;
            int cy = by + (i / per) * 22;
            if (mx >= cx && mx < cx + cw - 2 && my >= cy && my < cy + 20) {
                if (!gradient) {
                    target.accept(shortId(blocks.get(i)));
                    close.run();
                } else {
                    Integer c = BlockPalette.colorOf(blocks.get(i));
                    if (c != null) {
                        ends[editing] = c;
                        setHsv(c);
                    }
                }
                return true;
            }
        }
        return true;
    }

    private static int useY(int blocksTop, int count) {
        int per = perRow(count);
        int rows = Math.max(1, (count + per - 1) / per);
        return blocksTop + rows * 22 + 2;
    }

    boolean mouseDragged(double mx, double my) {
        if (dragging < 0) {
            return false;
        }
        drag(mx, my);
        return true;
    }

    void mouseReleased() {
        dragging = -1;
    }

    private void drag(double mx, double my) {
        if (dragging == 0) {
            sat = clamp((float) (mx - x - SQ_X) / (SQ - 1));
            val = 1 - clamp((float) (my - y - SQ_Y) / (SQ - 1));
        } else {
            hue = clamp((float) (my - y - SQ_Y) / (SQ - 1)) * 359.9f;
        }
        if (gradient) {
            ends[editing] = current();
        }
    }

    private void eyedropper() {
        Integer c = aimedColor();
        if (c == null) {
            return;
        }
        setHsv(c);
        if (gradient) {
            ends[editing] = c;
        }
    }

    private boolean inSquare(double mx, double my) {
        return mx >= x + SQ_X && mx < x + SQ_X + SQ && my >= y + SQ_Y && my < y + SQ_Y + SQ;
    }

    private boolean inHue(double mx, double my) {
        return mx >= x + HUE_X - 2 && mx < x + HUE_X + 14 && my >= y + SQ_Y && my < y + SQ_Y + SQ;
    }

    private static boolean in(double my, int top) {
        return my >= top && my < top + 14;
    }

    private static int segment(double mx, int sx, int sw, int n) {
        return Math.max(0, Math.min(n - 1, (int) ((mx - sx) / (sw / (double) n))));
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private void tab(GuiGraphics g, Font font, int tx, int ty, String label, boolean on, int mx, int my) {
        int w = font.width(label) + 10;
        boolean over = mx >= tx && mx < tx + w && my >= ty && my < ty + 14;
        g.fill(tx, ty, tx + w, ty + 14, on ? ACCENT_DARK : over ? ITEM_HOVER : ITEM);
        text(g, font, label, tx + 5, ty + 3, on ? TEXT : DIM);
    }

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, String label, String hint, int mx, int my, boolean primary) {
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 14;
        g.fill(bx, by, bx + bw, by + 14, primary ? (over ? 0xFF6E9BFF : ACCENT) : over ? ITEM_HOVER : ITEM);
        text(g, font, label, bx + (bw - font.width(label)) / 2, by + 3, primary ? 0xFFFFFFFF : TEXT);
        if (over) {
            hover = hint;
        }
    }

    private void segments(GuiGraphics g, Font font, int sx, int sy, int sw, String[] options, int selected, int mx, int my, String[] hints) {
        int n = options.length;
        for (int i = 0; i < n; i++) {
            int a = sx + (int) Math.round(i * sw / (double) n);
            int b = sx + (int) Math.round((i + 1) * sw / (double) n);
            boolean over = mx >= a && mx < b && my >= sy && my < sy + 14;
            g.fill(a, sy, b, sy + 14, i == selected ? ACCENT_DARK : over ? ITEM_HOVER : ITEM);
            if (i > 0) {
                g.fill(a, sy + 3, a + 1, sy + 11, EDGE);
            }
            text(g, font, options[i], a + (b - a - font.width(options[i])) / 2, sy + 3, i == selected ? TEXT : DIM);
            if (over) {
                hover = hints[i];
            }
        }
    }

    private static void outline(GuiGraphics g, int ox, int oy, int w, int h, int color) {
        g.fill(ox, oy, ox + w, oy + 1, color);
        g.fill(ox, oy + h - 1, ox + w, oy + h, color);
        g.fill(ox, oy, ox + 1, oy + h, color);
        g.fill(ox + w - 1, oy, ox + w, oy + h, color);
    }

    private static void text(GuiGraphics g, Font font, String s, int tx, int ty, int color) {
        g.drawString(font, s, tx, ty, color, false);
    }

    private static String fit(Font font, String s, int w) {
        if (font.width(s) <= w) {
            return s;
        }
        while (s.length() > 1 && font.width(s + "..") > w) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }
}
