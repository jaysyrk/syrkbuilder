package dev.syrkbuilder.fabric;

import dev.syrkbuilder.core.brush.BrushType;
import dev.syrkbuilder.core.model.BlockPalette;
import dev.syrkbuilder.core.terrain.TerrainStyle;
import dev.syrkbuilder.core.terrain.TerrainType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class EditorScreen extends Screen {
    private static final int BAR = 0xF0101114;
    private static final int PANEL = 0xE816181D;
    private static final int EDGE = 0xFF262932;
    private static final int ITEM = 0xFF20232B;
    private static final int ITEM_HOVER = 0xFF2B2F3A;
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int ACCENT_DARK = 0xFF2F4F99;
    private static final int TEXT = 0xFFE6E8EE;
    private static final int DIM = 0xFF8B90A0;
    private static final int FAINT = 0xFF5A5F6E;

    private static final int TOP = 20;
    private static final int BOTTOM = 16;
    private static final int LEFT_W = 86;
    private static final int RIGHT_W = 204;
    private static final int ROW = 16;

    private static final String[] TOOLS = {"Shapes", "Terrain", "Brushes", "Fill", "Trees", "Paths", "Selection", "Clipboard", "Import", "Scripts", "History", "Settings"};
    private static final String[] ICONS = {"◆", "▲", "✎", "▼", "♣", "∿", "▣", "❐", "↓", "✦", "⟲", "⚙"};
    private static final int HISTORY = 10;
    static final int SETTINGS = 11;
    private static final int BRUSHES = 2;
    private static final String[] SHAPES = {"sphere", "ellipsoid", "dome", "cyl", "cone", "pyramid", "circle", "disc", "torus", "helix"};
    private static final String[] SEL_ACTIONS = {"set", "walls", "outline", "replace", "line"};
    private static final String[] DIRECTIONS = {"look", "up", "down", "north", "south", "east", "west", "all"};
    private static String selAmount = "1";
    private static int selDirection;
    private static final String[] ROTATIONS = {"0°", "90°", "180°", "270°"};

    private static int tool;
    private static final int[] scroll = new int[TOOLS.length];
    private static int shape;
    private static String shapeBlocks = "stone";
    private static String radius = "6";
    private static String shapeHeight = "12";
    private static String thickness = "2";
    private static boolean hollow;
    private static boolean airOnly;
    private static boolean upright;
    private static int terrainType;
    private static int terrainStyle = -1;
    private static String tRadius = "48";
    private static String tHeight = "40";
    private static String tErosion = "50";
    private static String tRoughness = "0.5";
    private static String tPeaks = "3";
    private static String tSeed = "";
    private static int brushType;
    private static String brushRadius = "5";
    private static String brushBlocks = "stone";
    private static String brushStrength = "";
    private static String brushDensity = "";
    private static String brushScale = "";
    private static String brushDepth = "1";
    private static boolean brushReplaceTop;
    private static int selAction;
    private static String selBlocks = "stone";
    private static String selFrom = "air";
    private static int pasteRotation;
    private static boolean pasteSkipAir = true;
    private static boolean pasteFlip;
    private static String templateName = "";
    private static int modelIndex;
    private static String modelSize = "48";
    private static String heightmapHeight = "";
    private static int palette;
    private static boolean solid;
    private static int modelRotation;
    private static int scriptIndex;
    private static String scriptArgs = "";
    private static String checkpointName = "";
    private static String gotoTarget = "";
    private static int fillMode;
    private static String fillBlocks = "water";
    private static String fillRadius = "12";
    private static int treeType;
    private static String treeHeight = "";
    private static String forestRadius = "12";
    private static String forestDensity = "";
    private static int pathKind;
    private static String pathWidth = "";
    private static String pathBlocks = "";
    private static String pathHeight = "5";
    private static boolean previewOn = Settings.previewByDefault;

    private interface Painter {
        void paint(GuiGraphics g, int x, int y, int w, int h, boolean hover);
    }

    private record Element(int x, int y, int w, int h, boolean inPanel, Painter painter, Runnable click, String hint) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Element> elements = new ArrayList<>();
    private int panelBottom;
    private int contentHeight;
    private BlockPos target;

    private boolean looking;
    private double lookX;
    private double lookY;
    private double savedX;
    private double savedY;
    private net.minecraft.client.KeyMapping rebinding;
    private boolean clearFocus;
    private ColorPicker picker;
    private Consumer<String> mainBlocks;
    private int seenData = -1;
    private boolean synced;
    private Runnable primary;
    private String primaryLabel;
    private boolean painting;
    private java.util.function.Supplier<String> dragCommand;
    private java.util.function.DoubleSupplier dragSpacing;
    private boolean dragStroke;
    private BlockPos lastDrag;
    private int stroke;
    private long nextDab;

    static EditorScreen at(int startTool) {
        tool = startTool;
        return new EditorScreen();
    }

    EditorScreen() {
        super(Component.literal("SyrkBuilder"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        elements.clear();
        Minecraft mc = Minecraft.getInstance();
        target = mc.player == null ? null : SyrkBuilderClient.lookedAt(mc.player);

        int x = width - 6;
        x = topButton(x, "✕", "Close (Esc)", this::onClose);
        x = topButton(x, "⚙", "Settings", () -> selectTool(SETTINGS));
        x = topButton(x, "History", "Show the timeline in chat", () -> run("history 20"));
        x = topButton(x, "↷ Redo", "Redo (Ctrl+Y)", () -> run("redo"));
        x = topButton(x, "↶ Undo", "Undo (Ctrl+Z)", () -> run("undo"));
        x = topButton(x - 6, SyrkBuilderClient.noclipOn() ? "§bNoclip on" : "§8Noclip off", "Fly through blocks (N)", () -> {
            SyrkBuilderClient.toggleNoclip(Feedback.chat());
            rebuildWidgets();
        });
        topButton(x - 3, previewOn ? "§bPreview on" : "§8Preview off", "Preview: the last thing you place can still be moved, turned or cancelled", () -> {
            previewOn = !previewOn;
            run("preview " + (previewOn ? "on" : "off"));
            rebuildWidgets();
        });
        if (!synced) {
            synced = true;
            SyrkBuilderClient.sync();
            if (!Settings.previewByDefault) {
                run("preview off -q");
            }
        }
        seenData = ClientData.version();
        clearFocus = true;
        pendingBar();

        for (int i = 0; i < TOOLS.length; i++) {
            int index = i;
            int ty = TOP + 5 + i * 17;
            elements.add(new Element(4, ty, LEFT_W - 8, 16, false, (g, ex, ey, w, h, hover) -> {
                boolean active = tool == index;
                if (active || hover) {
                    box(g, ex, ey, w, h, active ? ITEM_HOVER : ITEM);
                }
                if (active) {
                    g.fill(ex, ey + 3, ex + 2, ey + h - 3, ACCENT);
                }
                text(g, ICONS[index], ex + 8, ey + 5, active ? ACCENT : DIM);
                text(g, TOOLS[index], ex + 20, ey + 5, active ? TEXT : DIM);
            }, () -> selectTool(index), TOOLS[i] + (i < 9 ? "  (" + (i + 1) + ")" : index == HISTORY ? "  (0)" : "")));
        }

        panelBottom = height - BOTTOM;
        int px = width - RIGHT_W + 10;
        int start = TOP + 8 - scroll[tool];
        primary = null;
        primaryLabel = null;
        dragCommand = null;
        mainBlocks = null;
        Panel p = new Panel(px, start, RIGHT_W - 20);
        p.title(TOOLS[tool]);
        switch (tool) {
            case 0 -> shapes(p);
            case 1 -> terrain(p);
            case 2 -> brushes(p);
            case 3 -> fill(p);
            case 4 -> trees(p);
            case 5 -> paths(p);
            case 6 -> selection(p);
            case 7 -> clipboard(p);
            case 8 -> importPanel(p);
            case 9 -> scripts(p);
            case SETTINGS -> settings(p);
            default -> history(p);
        }
        contentHeight = p.y + scroll[tool] - TOP;
        int max = Math.max(0, contentHeight - (panelBottom - TOP) + 8);
        if (scroll[tool] > max) {
            scroll[tool] = max;
            rebuildWidgets();
        }
    }

    private int topButton(int right, String label, String hint, Runnable action) {
        int w = font.width(label) + 12;
        int bx = right - w;
        elements.add(new Element(bx, 3, w, 14, false, (g, ex, ey, ew, eh, hover) -> {
            if (hover) {
                box(g, ex, ey, ew, eh, ITEM_HOVER);
            }
            text(g, label, ex + 6, ey + 3, hover ? TEXT : DIM);
        }, action, hint));
        return bx - 3;
    }

    private void selectTool(int index) {
        tool = index;
        rebuildWidgets();
    }

    private final class Panel {
        final int x;
        final int w;
        int y;

        Panel(int x, int y, int w) {
            this.x = x;
            this.y = y;
            this.w = w;
        }

        boolean visible(int top, int h) {
            return top >= TOP + 1 && top + h <= panelBottom;
        }

        void add(int ex, int ey, int ew, int eh, Painter painter, Runnable click, String hint) {
            elements.add(new Element(ex, ey, ew, eh, true, painter, click, hint));
        }

        void title(String t) {
            int ty = y;
            add(x, ty, w, 12, (g, ex, ey, ew, eh, hover) -> text(g, "§l" + t, ex, ey + 2, TEXT), null, null);
            y += 18;
        }

        void section(String t) {
            y += 4;
            add(x, y, w, 10, (g, ex, ey, ew, eh, hover) -> {
                String s = t.toUpperCase(Locale.ROOT);
                text(g, s, ex, ey + 1, FAINT);
                g.fill(ex + font.width(s) + 5, ey + 5, ex + ew, ey + 6, EDGE);
            }, null, null);
            y += 14;
        }

        void note(String s) {
            for (String line : wrap(s, w)) {
                add(x, y, w, 10, (g, ex, ey, ew, eh, hover) -> text(g, line, ex, ey, DIM), null, null);
                y += 10;
            }
            y += 4;
        }

        void chips(String[] options, String[] hints, int selected, int cols, IntConsumer pick) {
            int gap = 3;
            int cw = (w - gap * (cols - 1)) / cols;
            for (int i = 0; i < options.length; i++) {
                int index = i;
                int cx = x + (i % cols) * (cw + gap);
                int cy = y + (i / cols) * (ROW + gap);
                String label = options[i];
                add(cx, cy, cw, ROW, (g, ex, ey, ew, eh, hover) -> {
                    boolean on = index == selected;
                    box(g, ex, ey, ew, eh, on ? ACCENT_DARK : hover ? ITEM_HOVER : ITEM);
                    if (on) {
                        outline(g, ex, ey, ew, eh, ACCENT);
                    }
                    String shown = fit(label, ew - 6);
                    text(g, shown, ex + (ew - font.width(shown)) / 2, ey + 4, on ? TEXT : hover ? TEXT : DIM);
                }, () -> {
                    pick.accept(index);
                    rebuildWidgets();
                }, hints == null ? null : hints[i]);
            }
            y += ((options.length + cols - 1) / cols) * (ROW + gap) + 3;
        }

        void segments(String label, String[] options, int selected, IntConsumer pick) {
            int lw = label == null ? 0 : 58;
            if (label != null) {
                add(x, y, lw, ROW, (g, ex, ey, ew, eh, hover) -> text(g, label, ex, ey + 4, DIM), null, null);
            }
            int sw = (w - lw) / options.length;
            for (int i = 0; i < options.length; i++) {
                int index = i;
                String o = options[i];
                int sx = x + lw + i * sw;
                int width = i == options.length - 1 ? w - lw - i * sw : sw;
                add(sx, y, width, ROW, (g, ex, ey, ew, eh, hover) -> {
                    boolean on = index == selected;
                    g.fill(ex, ey, ex + ew, ey + eh, on ? ACCENT_DARK : hover ? ITEM_HOVER : ITEM);
                    if (index > 0) {
                        g.fill(ex, ey + 3, ex + 1, ey + eh - 3, EDGE);
                    }
                    text(g, o, ex + (ew - font.width(o)) / 2, ey + 4, on ? TEXT : DIM);
                }, () -> {
                    pick.accept(index);
                    rebuildWidgets();
                }, null);
            }
            y += ROW + 4;
        }

        void field(String label, String value, String placeholder, Consumer<String> onChange) {
            field(label, value, placeholder, onChange, false);
        }

        void blocksField(String label, String value, String placeholder, Consumer<String> onChange) {
            if (mainBlocks == null) {
                mainBlocks = onChange;
            }
            field(label, value, placeholder, onChange, true);
        }

        void field(String label, String value, String placeholder, Consumer<String> onChange, boolean picker) {
            int lw = 58;
            int fy = y;
            int sw = picker ? 18 : 0;
            if (picker) {
                Consumer<String> set = v -> {
                    onChange.accept(v);
                    rebuildWidgets();
                };
                add(x + w - 16, fy, 16, ROW, (g, ex, ey, ew, eh, hover) -> {
                    box(g, ex, ey, ew, eh, hover ? ITEM_HOVER : ITEM);
                    Integer c = firstColor(value);
                    if (c != null) {
                        g.fill(ex + 3, ey + 3, ex + ew - 3, ey + eh - 3, 0xFF000000 | c);
                    } else {
                        g.fill(ex + 3, ey + 3, ex + 8, ey + 8, 0xFFE0533D);
                        g.fill(ex + 8, ey + 3, ex + 13, ey + 8, 0xFFF0C23B);
                        g.fill(ex + 3, ey + 8, ex + 8, ey + 13, 0xFF4CAF6A);
                        g.fill(ex + 8, ey + 8, ex + 13, ey + 13, 0xFF4A7BDF);
                    }
                }, () -> openPicker(set), "Pick blocks by colour, or build a gradient");
            }
            add(x, fy, lw, ROW, (g, ex, ey, ew, eh, hover) -> text(g, label, ex, ey + 4, DIM), null, null);
            EditBox[] ref = new EditBox[1];
            add(x + lw, fy, w - lw - sw, ROW, (g, ex, ey, ew, eh, hover) -> {
                boolean focused = ref[0] != null && ref[0].isFocused();
                box(g, ex, ey, ew, eh, focused ? 0xFF0C0D10 : hover ? ITEM_HOVER : ITEM);
                if (focused) {
                    outline(g, ex, ey, ew, eh, ACCENT);
                }
                if (ref[0] != null && ref[0].getValue().isEmpty() && !focused && placeholder != null) {
                    text(g, placeholder, ex + 5, ey + 4, FAINT);
                }
            }, null, null);
            if (visible(fy, ROW)) {
                EditBox box = new EditBox(font, x + lw + 5, fy + 4, w - lw - 8 - sw, 10, Component.literal(label));
                box.setBordered(false);
                box.setMaxLength(256);
                box.setValue(value);
                box.setTextColor(TEXT);
                box.setResponder(onChange);
                addRenderableWidget(box);
                ref[0] = box;
            }
            y += ROW + 4;
        }

        void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String hint) {
            add(x, y, w, ROW, (g, ex, ey, ew, eh, hover) -> {
                boolean on = get.getAsBoolean();
                text(g, label, ex, ey + 4, hover ? TEXT : DIM);
                int sx = ex + ew - 22;
                box(g, sx, ey + 3, 22, 10, on ? ACCENT : ITEM_HOVER);
                int knob = on ? sx + 13 : sx + 1;
                box(g, knob, ey + 4, 8, 8, on ? TEXT : DIM);
            }, () -> {
                set.accept(!get.getAsBoolean());
                rebuildWidgets();
            }, hint);
            y += ROW + 2;
        }

        void button(String label, boolean primary, Runnable action) {
            add(x, y, w, primary ? 20 : ROW, (g, ex, ey, ew, eh, hover) -> {
                box(g, ex, ey, ew, eh, primary ? (hover ? 0xFF6E9BFF : ACCENT) : hover ? ITEM_HOVER : ITEM);
                text(g, label, ex + (ew - font.width(label)) / 2, ey + (eh - 8) / 2, primary ? 0xFFFFFFFF : TEXT);
            }, action, null);
            y += (primary ? 20 : ROW) + 4;
        }

        void buttons(String a, Runnable ra, String b, Runnable rb) {
            int half = (w - 3) / 2;
            add(x, y, half, ROW, (g, ex, ey, ew, eh, hover) -> {
                box(g, ex, ey, ew, eh, hover ? ITEM_HOVER : ITEM);
                text(g, a, ex + (ew - font.width(a)) / 2, ey + 4, TEXT);
            }, ra, null);
            add(x + half + 3, y, w - half - 3, ROW, (g, ex, ey, ew, eh, hover) -> {
                box(g, ex, ey, ew, eh, hover ? ITEM_HOVER : ITEM);
                text(g, b, ex + (ew - font.width(b)) / 2, ey + 4, TEXT);
            }, rb, null);
            y += ROW + 4;
        }

        void list(List<String> items, int selected, IntConsumer pick) {
            for (int i = 0; i < items.size(); i++) {
                int index = i;
                String item = items.get(i);
                add(x, y, w, 14, (g, ex, ey, ew, eh, hover) -> {
                    boolean on = index == selected;
                    if (on || hover) {
                        box(g, ex, ey, ew, eh, on ? ACCENT_DARK : ITEM_HOVER);
                    }
                    text(g, fit(item, ew - 10), ex + 5, ey + 3, on ? TEXT : DIM);
                }, () -> {
                    pick.accept(index);
                    rebuildWidgets();
                }, null);
                y += 15;
            }
            y += 4;
        }

        void gap(int h) {
            y += h;
        }
    }

    private void shapes(Panel p) {
        p.chips(SHAPES, null, shape, 3, v -> shape = v);
        String s = SHAPES[shape];
        p.section("Settings");
        p.blocksField("Blocks", shapeBlocks, "stone, 70%stone,30%andesite", v -> shapeBlocks = v);
        p.field("Radius", radius, "0-256", v -> radius = v);
        if (s.equals("cyl") || s.equals("cone") || s.equals("helix") || s.equals("ellipsoid")) {
            p.field("Height", shapeHeight, "1-384", v -> shapeHeight = v);
        }
        if (s.equals("circle") || s.equals("torus")) {
            p.field(s.equals("torus") ? "Tube" : "Thickness", thickness, "blocks", v -> thickness = v);
        }
        p.toggle("Hollow", () -> hollow, v -> hollow = v, "Only the shell");
        p.toggle("Only replace air", () -> airOnly, v -> airOnly = v, "Leave existing blocks alone");
        if (s.equals("circle") || s.equals("disc")) {
            p.toggle("Upright", () -> upright, v -> upright = v, "Stand it up, facing you");
        }
        p.gap(4);
        placeDrag(p, "Place at target", "place " + s, () -> {
            String cmd = switch (s) {
                case "cyl", "cone", "helix" -> s + " " + blocks(shapeBlocks) + " " + num(radius) + " " + num(shapeHeight);
                case "ellipsoid" -> s + " " + blocks(shapeBlocks) + " " + num(radius) + " " + num(shapeHeight) + " " + num(radius);
                case "circle", "torus" -> s + " " + blocks(shapeBlocks) + " " + num(radius) + " " + num(thickness);
                default -> s + " " + blocks(shapeBlocks) + " " + num(radius);
            };
            return cmd + (hollow ? " -h" : "") + (airOnly ? " -a" : "") + (upright && (s.equals("circle") || s.equals("disc")) ? " -v" : "");
        }, () -> Math.max(1, number(radius, 6) * 0.5), true);
        p.note("§8Tip: blocks can be a gradient, e.g. grad:stone,andesite,diorite");
    }

    private void terrain(Panel p) {
        TerrainType[] all = TerrainType.values();
        String[] types = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            types[i] = all[i].id();
        }
        p.chips(types, null, terrainType, 3, v -> terrainType = v);
        p.section("Style");
        String[] styles = new String[TerrainStyle.NAMES.size() + 1];
        styles[0] = "auto";
        for (int i = 0; i < TerrainStyle.NAMES.size(); i++) {
            styles[i + 1] = TerrainStyle.NAMES.get(i);
        }
        p.chips(styles, null, terrainStyle + 1, 4, v -> terrainStyle = v - 1);
        p.section("Shape");
        p.field("Radius", tRadius, "blocks", v -> tRadius = v);
        p.field("Height", tHeight, "blocks", v -> tHeight = v);
        p.field("Erosion", tErosion, "0-100", v -> tErosion = v);
        p.field("Rough", tRoughness, "0-1", v -> tRoughness = v);
        p.field("Peaks", tPeaks, "count", v -> tPeaks = v);
        p.field("Seed", tSeed, "random", v -> tSeed = v);
        p.gap(4);
        placeDrag(p, "Generate at target", "generate " + types[terrainType], () -> {
            StringBuilder cmd = new StringBuilder("terrain ").append(types[terrainType])
                .append(" radius=").append(num(tRadius)).append(" height=").append(num(tHeight))
                .append(" erosion=").append(num(tErosion)).append(" roughness=").append(num(tRoughness))
                .append(" peaks=").append(num(tPeaks));
            if (terrainStyle >= 0) {
                cmd.append(" style=").append(TerrainStyle.NAMES.get(terrainStyle));
            }
            if (!tSeed.isBlank()) {
                cmd.append(" seed=").append(tSeed.trim().replace(' ', '_'));
            }
            return cmd.toString();
        }, () -> Math.max(6, number(tRadius, 48) * 0.7), true);
    }

    private void brushes(Panel p) {
        BrushType[] types = BrushType.values();
        brushType = Math.floorMod(brushType, types.length);
        String[][] groups = {{"Blocks"}, {"Sculpt"}, {"Terrain"}};
        for (int gi = 0; gi < groups.length; gi++) {
            List<String> names = new ArrayList<>();
            List<String> hints = new ArrayList<>();
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < types.length; i++) {
                if (group(types[i]) == gi) {
                    names.add(types[i].id());
                    hints.add(types[i].id() + ": " + types[i].description);
                    indexes.add(i);
                }
            }
            p.section(groups[gi][0]);
            int selected = indexes.indexOf(brushType);
            p.chips(names.toArray(new String[0]), hints.toArray(new String[0]), selected, 3, v -> brushType = indexes.get(v));
        }
        BrushType type = types[brushType];
        p.section(type.id());
        p.note(type.description);
        p.field("Radius", brushRadius, "1-" + type.maxRadius(), v -> brushRadius = v);
        if (type.needsBlocks) {
            boolean optional = type.blocks == BrushType.Blocks.OPTIONAL;
            p.blocksField("Blocks", brushBlocks, optional ? "touching block" : "stone", v -> brushBlocks = v);
        }
        if (type.usesStrength()) {
            p.field("Strength", brushStrength, type.strengthInBlocks() ? (int) type.defaultStrength + " (blocks)" : String.valueOf(type.defaultStrength),
                v -> brushStrength = v);
        }
        if (type.options.contains("density")) {
            p.field("Density", brushDensity, "0-1", v -> brushDensity = v);
        }
        if (type.options.contains("scale")) {
            p.field("Noise size", brushScale, "auto", v -> brushScale = v);
        }
        if (type == BrushType.OVERLAY) {
            p.field("Depth", brushDepth, "1-16", v -> brushDepth = v);
            p.toggle("Replace top", () -> brushReplaceTop, v -> brushReplaceTop = v, "Swap the top layer instead of adding one");
        }
        p.gap(4);
        String args = brushArgs(type);
        p.button("Bind to held item", true, () -> BrushBindings.bind(Feedback.chat(), args));
        p.button("Apply once at target", false, () -> run("brush " + args));
        primary = () -> run("brush " + args);
        dragCommand = () -> "brush " + brushArgs(BrushType.values()[brushType]);
        dragSpacing = () -> Math.max(1, number(brushRadius, 5) * 0.35);
        dragStroke = true;
        primaryLabel = "paint " + type.id();
        p.note("§8Bind it, press Esc, then hold right-click to paint.");
    }

    private static int group(BrushType t) {
        if (t.terrain()) {
            return 2;
        }
        return t.voxel() || t == BrushType.SPIKES ? 1 : 0;
    }

    private static String brushArgs(BrushType type) {
        StringBuilder sb = new StringBuilder(type.id()).append(' ').append(num(brushRadius));
        if (type.needsBlocks && (type.blocks == BrushType.Blocks.REQUIRED || !brushBlocks.isBlank())) {
            sb.append(' ').append(blocks(brushBlocks));
        }
        if (type.usesStrength() && !brushStrength.isBlank()) {
            sb.append(" strength=").append(num(brushStrength));
        }
        if (type.options.contains("density") && !brushDensity.isBlank()) {
            sb.append(" density=").append(num(brushDensity));
        }
        if (type.options.contains("scale") && !brushScale.isBlank()) {
            sb.append(" scale=").append(num(brushScale));
        }
        if (type == BrushType.OVERLAY) {
            sb.append(" depth=").append(num(brushDepth)).append(brushReplaceTop ? " -r" : "");
        }
        return sb.toString();
    }

    private void selection(Panel p) {
        p.note("pos1  §f" + Selection.describe(Selection.pos1()));
        p.gap(-4);
        p.note("pos2  §f" + Selection.describe(Selection.pos2()));
        p.buttons("Set pos1", () -> setPos(true), "Set pos2", () -> setPos(false));
        p.button("Clear selection", false, () -> {
            Selection.clear();
            rebuildWidgets();
        });
        p.note("§8Left-click the world: pos1. Shift+left-click: pos2.");
        primary = () -> setPos(!shiftDown());
        primaryLabel = "set pos1 (shift: pos2)";
        p.section("Action");
        p.chips(SEL_ACTIONS, null, selAction, 3, v -> selAction = v);
        boolean replace = SEL_ACTIONS[selAction].equals("replace");
        if (replace) {
            p.blocksField("From", selFrom, "air", v -> selFrom = v);
        }
        p.blocksField(replace ? "To" : "Blocks", selBlocks, "stone", v -> selBlocks = v);
        p.gap(4);
        p.button("Apply to selection", true, () -> {
            String a = SEL_ACTIONS[selAction];
            run(replace ? "replace " + blocks(selFrom) + " " + blocks(selBlocks) : a + " " + blocks(selBlocks));
        });
        p.section("Transform");
        p.field("Amount", selAmount, "1", v -> selAmount = v);
        p.chips(DIRECTIONS, null, selDirection, 4, v -> selDirection = v);
        p.buttons("Move", () -> run("move " + amount() + dirWord(false)), "Stack", () -> run("stack " + amount() + dirWord(false)));
        p.buttons("Expand", () -> run("expand " + amount() + dirWord(true)), "Contract", () -> run("contract " + amount() + dirWord(true)));
        p.button("Shift selection only", false, () -> run("shift " + amount() + dirWord(false)));
        p.note("§8Look = the way you face. Move takes the selection along; Stack repeats it next to itself.");
        p.section("Edit");
        p.buttons("Hollow", () -> run("hollow"), "Naturalize", () -> run("naturalize"));
        p.button("Overlay with blocks above", false, () -> run("overlay " + blocks(selBlocks)));
        p.buttons("Count blocks", () -> run("count " + blocks(selBlocks)), "Block list", () -> run("distr"));
        p.section("Magic select");
        p.buttons("Same block", () -> run("select"), "Whole build", () -> run("select -a"));
        p.note("§8Selects everything connected to the block you aim at.");
    }

    private static String amount() {
        String n = selAmount.trim();
        return n.matches("\\d+") && !n.equals("0") ? n : "1";
    }

    private static String dirWord(boolean allowAll) {
        String d = DIRECTIONS[selDirection];
        if (d.equals("look") || d.equals("all") && !allowAll) {
            return "";
        }
        return " " + d;
    }

    private void clipboard(Panel p) {
        p.button("Copy selection", false, () -> run("copy"));
        p.section("Paste");
        p.segments("Rotate", ROTATIONS, pasteRotation, v -> pasteRotation = v);
        p.toggle("Skip air", () -> pasteSkipAir, v -> pasteSkipAir = v, "Air in the clipboard doesn't overwrite");
        p.toggle("Mirror", () -> pasteFlip, v -> pasteFlip = v, "Flip left-right");
        place(p, "Paste at target", "paste", () -> run("paste" + pasteOptions()));
        p.section("Templates");
        p.field("Name", templateName, "my_build", v -> templateName = v);
        p.buttons("Save", () -> {
            run("template save " + name(templateName));
            rebuildWidgets();
        }, "Paste", () -> run("template paste " + name(templateName) + pasteOptions()));
        p.button("Load to clipboard", false, () -> run("template load " + name(templateName)));
        p.buttons("Export .schem", () -> run("template export " + name(templateName) + " schem"),
            "Export .litematic", () -> run("template export " + name(templateName) + " litematic"));
        p.note("§8.schem works in WorldEdit, FAWE and Axiom; .litematic in Litematica. Their files load here too.");
        List<String> names = ClientData.templates();
        if (!names.isEmpty()) {
            p.section("Saved");
            p.list(names, names.indexOf(templateName), v -> templateName = names.get(v));
        }
    }

    private String pasteOptions() {
        return (pasteRotation > 0 ? " rotate=" + pasteRotation * 90 : "") + (pasteSkipAir ? " -a" : "") + (pasteFlip ? " -flip" : "");
    }

    private void importPanel(Panel p) {
        List<String> models = SyrkBuilderClient.importables();
        if (models.isEmpty()) {
            p.note("No models yet. Put .obj (with its .mtl and textures), .glb or .vox files in .minecraft/syrkbuilder/models,");
            p.note("or greyscale .png/.jpg heightmaps in .minecraft/syrkbuilder/heightmaps");
            return;
        }
        modelIndex = Math.min(modelIndex, models.size() - 1);
        p.list(models, modelIndex, v -> {
            modelIndex = v;
            rebuildWidgets();
        });
        String chosen = models.get(modelIndex);
        boolean image = SyrkBuilderClient.isImage(chosen);
        p.section(image ? "Heightmap" : "Options");
        p.field("Size", modelSize, image ? "width, e.g. 128" : "2-512", v -> modelSize = v);
        if (image) {
            p.field("Height", heightmapHeight, "auto", v -> heightmapHeight = v);
            p.note("§8Brighter = higher. Grass on top, dirt, stone below, snow on the peaks.");
        } else {
            String[] palettes = BlockPalette.NAMES.toArray(new String[0]);
            p.chips(palettes, null, palette, 3, v -> palette = v);
            p.toggle("Solid", () -> solid, v -> solid = v, "Fill the inside, not just the shell");
        }
        p.segments("Rotate", ROTATIONS, modelRotation, v -> modelRotation = v);
        p.gap(4);
        String[] palettes = BlockPalette.NAMES.toArray(new String[0]);
        place(p, "Import at target", "import " + chosen, () -> SyrkBuilderClient.importModel(Feedback.chat(),
            chosen + (modelSize.isBlank() ? "" : " size=" + num(modelSize))
                + (image ? (heightmapHeight.isBlank() ? "" : " height=" + num(heightmapHeight)) : " palette=" + palettes[palette] + (solid ? " -s" : ""))
                + (modelRotation > 0 ? " rotate=" + modelRotation * 90 : "")));
    }

    private void scripts(Panel p) {
        List<String> scripts = new ArrayList<>();
        for (String f : LocalFiles.list(LocalFiles.scripts(), SyrkBuilderClient.SCRIPT_TYPES)) {
            scripts.add(f.substring(0, f.length() - 3));
        }
        if (scripts.isEmpty()) {
            p.note("No scripts in .minecraft/syrkbuilder/scripts");
            return;
        }
        scriptIndex = Math.min(scriptIndex, scripts.size() - 1);
        p.list(scripts, scriptIndex, v -> scriptIndex = v);
        p.section("Run");
        p.field("Args", scriptArgs, "e.g. 8 30 stone", v -> scriptArgs = v);
        p.gap(4);
        place(p, "Run script", "run " + scripts.get(scriptIndex), () -> SyrkBuilderClient.runScript(Feedback.chat(),
            scripts.get(scriptIndex) + (scriptArgs.isBlank() ? "" : " " + scriptArgs.trim())));
    }

    private void history(Panel p) {
        p.buttons("↶ Undo", () -> run("undo"), "Redo ↷", () -> run("redo"));
        p.field("Checkpoint", checkpointName, "name, e.g. before_roof", v -> checkpointName = v);
        p.button("Save checkpoint here", false, () -> run("checkpoint " + name(checkpointName)));
        p.section("Timeline");
        List<ClientData.Row> rows = ClientData.history();
        if (rows.isEmpty()) {
            p.note("§8Nothing yet - your edits show up here as a graph. Undo, then build something else, and both versions stay.");
            return;
        }
        p.note("§8Click an entry to jump the world there. Shift+click to rewind only your selection to it.");
        timeline(p, rows);
    }

    private void timeline(Panel p, List<ClientData.Row> rows) {
        java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm");
        int lanes = Math.min(6, ClientData.lanes());
        int textX = 8 + lanes * 7;
        for (ClientData.Row r : rows) {
            String target = r.root() ? "start" : "#" + r.id();
            String when = r.time() == 0 ? "" : time.format(new java.util.Date(r.time()));
            String hint = (r.root() ? "Start" : "#" + r.id() + " " + r.label() + " - " + String.format("%,d", r.blocks()) + " blocks, " + when)
                + (r.current() ? "  (you are here)" : "  - click: go here, shift+click: restore selection");
            p.add(p.x, p.y, p.w, 14, (g, ex, ey, ew, eh, hover) -> {
                if (r.current()) {
                    box(g, ex, ey, ew, eh, ACCENT_DARK);
                } else if (hover) {
                    box(g, ex, ey, ew, eh, ITEM_HOVER);
                }
                int line = r.onPath() ? 0xFF6F7890 : 0xFF3A3F4C;
                int mid = ey + eh / 2;
                for (int l : r.passing()) {
                    int lx = ex + 5 + Math.min(l, 5) * 7;
                    g.fill(lx, ey, lx + 1, ey + eh, 0xFF3A3F4C);
                }
                int nx = ex + 5 + Math.min(r.lane(), 5) * 7;
                if (r.above()) {
                    g.fill(nx, ey, nx + 1, mid, line);
                }
                if (r.below()) {
                    g.fill(nx, mid, nx + 1, ey + eh, line);
                }
                for (int m : r.merges()) {
                    int mx = ex + 5 + Math.min(m, 5) * 7;
                    g.fill(mx, ey, mx + 1, mid, 0xFF3A3F4C);
                    g.fill(Math.min(nx, mx), mid, Math.max(nx, mx) + 1, mid + 1, 0xFF3A3F4C);
                }
                int dot = r.current() ? ACCENT : r.onPath() ? TEXT : FAINT;
                box(g, nx - 2, mid - 2, 5, 5, dot);
                String label = r.root() ? "start" : r.label();
                String size = r.root() ? "" : compact(r.blocks());
                int sizeW = font.width(size);
                String cp = r.checkpoint() == null || r.checkpoint().isEmpty() || r.root() ? "" : " §d" + r.checkpoint();
                text(g, fit(label + cp, ew - textX - sizeW - 8), ex + textX, ey + 3, r.current() ? TEXT : r.onPath() ? 0xFFC9CDD8 : FAINT);
                text(g, size, ex + ew - sizeW - 3, ey + 3, FAINT);
            }, () -> run((shiftDown() && !r.root() ? "restore " : "goto ") + target), hint);
            p.y += 14;
        }
        p.gap(4);
    }

    private static String compact(int n) {
        if (n >= 1_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        }
        return n >= 1000 ? String.format("%.1fk", n / 1000.0) : String.valueOf(n);
    }

    private void settings(Panel p) {
        p.section("Keys");
        keyRow(p, "Open editor", SyrkBuilderClient.editorKey());
        keyRow(p, "Noclip", SyrkBuilderClient.noclipKey());
        p.segments("Noclip", new String[]{"toggle", "hold"}, Settings.noclipHold ? 1 : 0, v -> {
            Settings.noclipHold = v == 1;
            Settings.save();
        });
        p.note("§8Click a key, then press the new one (Esc cancels). Also in Controls → SyrkBuilder.");
        p.section("Movement");
        double[] speeds = {0.5, 1, 1.5, 2, 3, 4};
        p.segments("Fly speed", new String[]{"½", "1x", "1.5", "2x", "3x", "4x"}, nearest(speeds, Settings.flySpeed), v -> {
            Settings.flySpeed = speeds[v];
            Settings.save();
        });
        double[] looks = {0.5, 0.75, 1, 1.5, 2};
        p.segments("Look", new String[]{"½", "¾", "1x", "1.5", "2x"}, nearest(looks, Settings.lookSensitivity), v -> {
            Settings.lookSensitivity = looks[v];
            Settings.save();
        });
        p.note("§8Look = how fast the camera turns while holding right-click in the editor.");
        p.section("Editing");
        p.toggle("Preview new placements", () -> Settings.previewByDefault, v -> {
            Settings.previewByDefault = v;
            previewOn = v;
            run("preview " + (v ? "on" : "off") + " -q");
            Settings.save();
        }, "Keep the last placement adjustable (arrows, R, Delete)");
        p.toggle("Golden axe wand", () -> Settings.wand, v -> {
            Settings.wand = v;
            Settings.save();
        }, "Left/right-click with a golden axe sets pos1/pos2");
        p.toggle("Quiet chat", () -> Settings.quietChat, v -> {
            Settings.quietChat = v;
            Settings.save();
        }, "Only show errors in chat; everything else goes to the editor's status bar");
        p.section("Outlines");
        p.toggle("Selection box", () -> Settings.selectionParticles, v -> {
            Settings.selectionParticles = v;
            Settings.save();
        }, "Green particles around pos1/pos2");
        p.toggle("Preview box", () -> Settings.previewParticles, v -> {
            Settings.previewParticles = v;
            Settings.save();
        }, "White particles around the last placement");
        p.toggle("Path curve", () -> Settings.pathParticles, v -> {
            Settings.pathParticles = v;
            Settings.save();
        }, "Flames along the path you're placing");
        p.note("§8Saved in config/syrkbuilder.properties.");
    }

    private static int nearest(double[] values, double v) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i] - v) < Math.abs(values[best] - v)) {
                best = i;
            }
        }
        return best;
    }

    private void keyRow(Panel p, String label, net.minecraft.client.KeyMapping mapping) {
        p.add(p.x, p.y, 58, ROW, (g, ex, ey, ew, eh, hover) -> text(g, label, ex, ey + 4, DIM), null, null);
        p.add(p.x + 70, p.y, p.w - 70, ROW, (g, ex, ey, ew, eh, hover) -> {
            boolean waiting = rebinding == mapping;
            box(g, ex, ey, ew, eh, waiting ? ACCENT_DARK : hover ? ITEM_HOVER : ITEM);
            if (waiting) {
                outline(g, ex, ey, ew, eh, ACCENT);
            }
            String name = waiting ? "press a key..." : KeyBindingHelper.getBoundKeyOf(mapping).getDisplayName().getString();
            text(g, name, ex + (ew - font.width(name)) / 2, ey + 4, waiting ? TEXT : DIM);
        }, () -> {
            rebinding = mapping;
            rebuildWidgets();
        }, "Click, then press the key you want");
        p.y += ROW + 4;
    }

    private void fill(Panel p) {
        dev.syrkbuilder.core.shape.FloodFill.Mode[] modes = dev.syrkbuilder.core.shape.FloodFill.Mode.values();
        String[] names = new String[modes.length];
        String[] hints = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            names[i] = modes[i].id;
            hints[i] = modes[i].id + ": " + modes[i].description;
        }
        fillMode = Math.min(fillMode, modes.length - 1);
        p.chips(names, hints, fillMode, 3, v -> fillMode = v);
        dev.syrkbuilder.core.shape.FloodFill.Mode mode = modes[fillMode];
        p.note(switch (mode) {
            case HOLE -> "Aim at the bottom of a hole, pit or pool - it fills to the brim, like pouring water.";
            case CONNECTED -> "Aim at a block - everything touching it that's the same block gets recoloured.";
            case ROOM -> "Aim at the floor of a closed room or cave - it fills the whole space.";
        });
        p.blocksField("Blocks", fillBlocks, "water", v -> fillBlocks = v);
        p.field("Radius", fillRadius, "12 (max 64)", v -> fillRadius = v);
        p.gap(4);
        placeDrag(p, "Fill at target", "fill " + mode.id, () -> "fill " + blocks(fillBlocks.isBlank() ? "water" : fillBlocks) + " "
            + (fillRadius.isBlank() ? "12" : num(fillRadius)) + " mode=" + mode.id, () -> 1, true);
        p.note("§8Stops at the radius if the space isn't closed off.");
    }

    void openPicker(boolean gradientMode) {
        ColorPicker.gradientMode(gradientMode);
        openPicker(v -> {
            shapeBlocks = v;
            rebuildWidgets();
        });
    }

    private void openPicker(Consumer<String> set) {
        int px = Math.max(LEFT_W + 4, LEFT_W + (width - LEFT_W - RIGHT_W - ColorPicker.W) / 2);
        int py = Math.max(TOP + 4, TOP + (height - TOP - BOTTOM - ColorPicker.H) / 2);
        picker = new ColorPicker(px, py, set, () -> picker = null);
    }

    private static Integer firstColor(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        String first = pattern.replaceFirst("^grad[xyzr]?:", "").split(",")[0];
        first = first.contains("%") ? first.substring(first.indexOf('%') + 1) : first;
        return dev.syrkbuilder.core.model.BlockPalette.colorOf(first);
    }

    private void trees(Panel p) {
        dev.syrkbuilder.core.tree.Trees.Type[] types = dev.syrkbuilder.core.tree.Trees.Type.values();
        String[] names = new String[types.length + 1];
        String[] hints = new String[types.length + 1];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].id;
            hints[i] = types[i].id + ": " + types[i].description;
        }
        names[types.length] = "mix";
        hints[types.length] = "mix: oak, birch and dark oak";
        treeType = Math.min(treeType, names.length - 1);
        p.chips(names, hints, treeType, 3, v -> treeType = v);
        String type = names[treeType];
        p.field("Height", treeHeight, "auto (varies)", v -> treeHeight = v);
        p.gap(4);
        placeDrag(p, "Grow at target", "grow " + type, () -> "tree " + type + (treeHeight.isBlank() ? "" : " " + num(treeHeight)), () -> 5, true);
        p.note("§8Every tree is different. Left-click the ground to plant more.");
        p.section("Forest brush");
        p.field("Radius", forestRadius, "blocks", v -> forestRadius = v);
        p.field("Density", forestDensity, "0.02", v -> forestDensity = v);
        p.button("Bind forest to held item", false, () -> BrushBindings.bind(Feedback.chat(),
            "trees " + num(forestRadius) + " type=" + type + (forestDensity.isBlank() ? "" : " density=" + num(forestDensity))));
        p.note("§8Then press Esc and hold right-click to plant. Trees only grow on soil, spaced apart.");
    }

    private void paths(Panel p) {
        dev.syrkbuilder.core.path.Paths.Kind[] kinds = dev.syrkbuilder.core.path.Paths.Kind.values();
        int points = ClientData.path().size();
        p.note(points == 0 ? "Left-click the ground to place path points (they show as flames)."
            : "§f" + points + "§7 point" + (points == 1 ? "" : "s") + " placed. Left-click to add more.");
        p.buttons("Undo point", () -> run("path undo"), "Clear", () -> run("path clear"));
        p.section("Build as");
        String[] names = new String[kinds.length];
        String[] hints = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            names[i] = kinds[i].id;
            hints[i] = kinds[i].id + ": " + kinds[i].description;
        }
        pathKind = Math.min(pathKind, kinds.length - 1);
        p.chips(names, hints, pathKind, 3, v -> pathKind = v);
        dev.syrkbuilder.core.path.Paths.Kind kind = kinds[pathKind];
        p.field("Width", pathWidth, String.valueOf(kind.defaultWidth), v -> pathWidth = v);
        p.blocksField("Blocks", pathBlocks, kind.defaultBlocks.replace("minecraft:", ""), v -> pathBlocks = v);
        if (kind == dev.syrkbuilder.core.path.Paths.Kind.WALL) {
            p.field("Height", pathHeight, "5", v -> pathHeight = v);
        }
        p.gap(4);
        p.button("Build " + kind.id, true, () -> run("path " + kind.id + " " + (pathWidth.isBlank() ? kind.defaultWidth : num(pathWidth))
            + (pathBlocks.isBlank() ? "" : " " + blocks(pathBlocks)) + (kind == dev.syrkbuilder.core.path.Paths.Kind.WALL ? " height=" + num(pathHeight) : "")));
        primary = () -> run("path add");
        primaryLabel = "add path point (drag to draw)";
        dragCommand = () -> "path add";
        dragSpacing = () -> 5;
        dragStroke = false;
    }

    private void pendingBar() {
        ClientData.Pending pending = ClientData.pending();
        if (pending == null) {
            return;
        }
        int left = LEFT_W + 8;
        int right = width - RIGHT_W - 8;
        int x = left;
        int y = TOP + 6;
        List<String[]> buttons = new ArrayList<>();
        buttons.add(new String[]{"✓ Keep", "confirm", "Keep it here (Enter) - doing anything else keeps it too"});
        buttons.add(new String[]{"✕ Cancel", "cancel", "Remove it completely (Delete / Backspace)"});
        if (pending.movable()) {
            buttons.add(new String[]{"⟳", "turn", "Turn 90° (R) - pastes, templates and imports"});
            buttons.add(new String[]{"←", "nudge left", "Move left (←, shift = 5 blocks)"});
            buttons.add(new String[]{"↑", "nudge forward", "Move away from you (↑)"});
            buttons.add(new String[]{"↓", "nudge back", "Move towards you (↓)"});
            buttons.add(new String[]{"→", "nudge right", "Move right (→)"});
            buttons.add(new String[]{"⤒", "nudge up", "Move up (Page Up)"});
            buttons.add(new String[]{"⤓", "nudge down", "Move down (Page Down)"});
        }
        String title = "§7Placed §f" + pending.label();
        int titleW = font.width(title) + 12;
        elements.add(new Element(x, y, Math.min(titleW, right - x), 16, false, (g, ex, ey, ew, eh, hover) -> {
            box(g, ex, ey, ew, eh, 0xE0101114);
            text(g, fit(title, ew - 10), ex + 6, ey + 4, TEXT);
        }, null, "Still live: move, turn or cancel it. Anything else you do keeps it."));
        x += Math.min(titleW, right - x) + 3;
        for (String[] b : buttons) {
            int w = font.width(b[0]) + 12;
            if (x + w > right) {
                x = left;
                y += 19;
            }
            String command = b[1];
            boolean main = command.equals("confirm");
            elements.add(new Element(x, y, w, 16, false, (g, ex, ey, ew, eh, hover) -> {
                box(g, ex, ey, ew, eh, main ? (hover ? 0xFF6E9BFF : ACCENT) : hover ? ITEM_HOVER : 0xE0181A20);
                text(g, b[0], ex + 6, ey + 4, main ? 0xFFFFFFFF : TEXT);
            }, () -> run(command + (command.startsWith("nudge") && shiftDown() ? " 5" : "")), b[2]));
            x += w + 3;
        }
    }

    private void place(Panel p, String label, String clickLabel, Runnable action) {
        p.button(label, true, action);
        primary = action;
        primaryLabel = clickLabel;
    }

    private static boolean shiftDown() {
        return down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private void placeDrag(Panel p, String label, String clickLabel, java.util.function.Supplier<String> command,
                           java.util.function.DoubleSupplier spacing, boolean merge) {
        place(p, label, clickLabel + " (drag for more)", () -> run(command.get()));
        dragCommand = command;
        dragSpacing = spacing;
        dragStroke = merge;
    }

    private static double number(String raw, double def) {
        try {
            return raw == null || raw.isBlank() ? def : Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void usePrimary() {
        if (primary == null) {
            return;
        }
        setFocused(null);
        Minecraft mc = Minecraft.getInstance();
        lastDrag = mc.player == null ? null : SyrkBuilderClient.lookedAt(mc.player);
        stroke = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        nextDab = System.nanoTime() + 200_000_000L;
        painting = dragCommand != null;
        if (tool == BRUSHES && dragCommand != null) {
            SyrkBuilderClient.forward(BrushBindings.QUIET, dragCommand.get() + " stroke=" + stroke + " -q");
        } else {
            primary.run();
        }
    }

    private void drag() {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L || dragCommand == null || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            painting = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BlockPos aim = mc.player == null ? null : SyrkBuilderClient.lookedAt(mc.player);
        if (aim == null) {
            return;
        }
        long now = System.nanoTime();
        double moved = lastDrag == null ? Double.MAX_VALUE : Math.sqrt(aim.distSqr(lastDrag));
        boolean due = moved >= dragSpacing.getAsDouble() || tool == BRUSHES && now >= nextDab;
        if (!due) {
            return;
        }
        lastDrag = aim;
        nextDab = now + 200_000_000L;
        SyrkBuilderClient.forward(BrushBindings.QUIET, dragCommand.get() + (dragStroke ? " stroke=" + stroke : "") + " -q");
    }

    private static void run(String command) {
        SyrkBuilderClient.forward(Feedback.chat(), command);
    }

    private void setPos(boolean first) {
        SyrkBuilderClient.setPos(Feedback.chat(), first);
        rebuildWidgets();
    }

    private static String blocks(String raw) {
        String b = raw.trim().replace(" ", "");
        return b.isEmpty() ? "stone" : b;
    }

    private static String num(String raw) {
        String n = raw.trim();
        return n.isEmpty() ? "0" : n.replace(' ', '_');
    }

    private static String name(String raw) {
        return raw.trim().replace(' ', '_');
    }

    boolean isTyping() {
        return getFocused() instanceof EditBox;
    }

    private boolean inViewport(double x, double y) {
        return x > LEFT_W && x < width - RIGHT_W && y > TOP && y < height - BOTTOM;
    }

    private boolean visible(Element e) {
        return !e.inPanel() || e.y() >= TOP + 1 && e.y() + e.h() <= panelBottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (picker != null && !looking && event.button() == 0) {
            setFocused(null);
            return picker.mouseClicked(event.x(), event.y());
        }
        if (looking) {
            if (event.button() == 0) {
                usePrimary();
            }
            return true;
        }
        if (event.button() == 0) {
            for (Element e : elements) {
                if (e.click() != null && visible(e) && e.contains(event.x(), event.y())) {
                    setFocused(null);
                    e.click().run();
                    return true;
                }
            }
        }
        if (event.button() == 0 && inViewport(event.x(), event.y())) {
            usePrimary();
            return true;
        }
        if (event.button() == 1 && inViewport(event.x(), event.y())) {
            startLook();
            return true;
        }
        if (event.button() == 2 && inViewport(event.x(), event.y())) {
            String b = ColorPicker.aimedBlock();
            if (b != null && mainBlocks != null) {
                mainBlocks.accept(b.startsWith("minecraft:") ? b.substring(10) : b);
                StatusLine.set("§7Picked §f" + b);
                rebuildWidgets();
            }
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled) {
            setFocused(null);
        }
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (rebinding != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                rebinding.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
                net.minecraft.client.KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
            rebinding = null;
            rebuildWidgets();
            return true;
        }
        if (picker != null && key == GLFW.GLFW_KEY_ESCAPE) {
            picker = null;
            return true;
        }
        if (isTyping()) {
            return super.keyPressed(event);
        }
        boolean ctrl = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0
            || down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL)
            || down(GLFW.GLFW_KEY_LEFT_SUPER) || down(GLFW.GLFW_KEY_RIGHT_SUPER);
        boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || shiftDown();
        if (!ctrl && isKey(SyrkBuilderClient.editorKey(), key)) {
            onClose();
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            run(key == GLFW.GLFW_KEY_Y || shift ? "redo" : "undo");
            return true;
        }
        if (!ctrl && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            selectTool(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_0) {
            selectTool(HISTORY);
            return true;
        }
        ClientData.Pending pending = ClientData.pending();
        if (pending != null && !ctrl) {
            int n = shift ? 5 : 1;
            String command = switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "confirm";
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> "cancel";
                case GLFW.GLFW_KEY_LEFT -> pending.movable() ? "nudge left " + n : null;
                case GLFW.GLFW_KEY_RIGHT -> pending.movable() ? "nudge right " + n : null;
                case GLFW.GLFW_KEY_UP -> pending.movable() ? "nudge forward " + n : null;
                case GLFW.GLFW_KEY_DOWN -> pending.movable() ? "nudge back " + n : null;
                case GLFW.GLFW_KEY_PAGE_UP -> pending.movable() ? "nudge up " + n : null;
                case GLFW.GLFW_KEY_PAGE_DOWN -> pending.movable() ? "nudge down " + n : null;
                case GLFW.GLFW_KEY_R -> pending.movable() ? "turn" : null;
                default -> null;
            };
            if (command != null) {
                run(command);
                return true;
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_UP,
                 GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_BACKSPACE,
                 GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_TAB -> {
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (picker != null) {
            picker.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (picker != null && picker.mouseDragged(event.x(), event.y())) {
            return true;
        }
        return looking || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= width - RIGHT_W && mouseY > TOP && mouseY < height - BOTTOM) {
            int max = Math.max(0, contentHeight - (height - BOTTOM - TOP) + 8);
            int next = Math.max(0, Math.min(max, scroll[tool] - (int) Math.round(scrollY * 18)));
            if (next != scroll[tool]) {
                scroll[tool] = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void startLook() {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            return;
        }
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(window, cx, cy);
        savedX = cx[0];
        savedY = cy[0];
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwGetCursorPos(window, cx, cy);
        lookX = cx[0];
        lookY = cy[0];
        setFocused(null);
        looking = true;
    }

    private void updateLook() {
        Minecraft mc = Minecraft.getInstance();
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L || mc.player == null || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
            stopLook();
            return;
        }
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(window, cx, cy);
        double dx = cx[0] - lookX;
        double dy = cy[0] - lookY;
        lookX = cx[0];
        lookY = cy[0];
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double f = s * s * s * 8.0 * Settings.lookSensitivity;
        if (dx != 0 || dy != 0) {
            mc.player.turn(dx * f, dy * f);
        }
    }

    private void stopLook() {
        if (!looking) {
            return;
        }
        looking = false;
        long window = GLFW.glfwGetCurrentContext();
        if (window != 0L) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            GLFW.glfwSetCursorPos(window, savedX, savedY);
        }
    }

    private static boolean isKey(net.minecraft.client.KeyMapping mapping, int key) {
        InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(mapping);
        return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == key;
    }

    private static boolean down(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    @Override
    public void removed() {
        stopLook();
        EditorMovement.release();
        super.removed();
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private String fit(String s, int w) {
        if (font.width(s) <= w) {
            return s;
        }
        while (s.length() > 1 && font.width(s + "..") > w) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private List<String> wrap(String s, int w) {
        List<String> lines = new ArrayList<>();
        String prefix = s.startsWith("§") && s.length() > 1 ? s.substring(0, 2) : "";
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (font.width(next) > w && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(prefix).append(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, TOP, BAR);
        g.fill(0, TOP, width, TOP + 1, EDGE);
        g.fill(0, TOP + 1, LEFT_W, height - BOTTOM, PANEL);
        g.fill(LEFT_W, TOP + 1, LEFT_W + 1, height - BOTTOM, EDGE);
        g.fill(width - RIGHT_W, TOP + 1, width, height - BOTTOM, PANEL);
        g.fill(width - RIGHT_W - 1, TOP + 1, width - RIGHT_W, height - BOTTOM, EDGE);
        g.fill(0, height - BOTTOM - 1, width, height - BOTTOM, EDGE);
        g.fill(0, height - BOTTOM, width, height, BAR);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (clearFocus) {
            clearFocus = false;
            setFocused(null);
        }
        if (looking) {
            updateLook();
            mouseX = -1;
            mouseY = -1;
        }
        if (painting) {
            drag();
        }
        if (ClientData.version() != seenData && !isTyping() && !looking) {
            rebuildWidgets();
        }
        int realX = mouseX;
        int realY = mouseY;
        if (picker != null && picker.contains(mouseX, mouseY)) {
            mouseX = -1;
            mouseY = -1;
        }
        String hint = null;
        g.enableScissor(width - RIGHT_W, TOP + 1, width, height - BOTTOM - 1);
        for (Element e : elements) {
            if (e.inPanel()) {
                boolean hover = visible(e) && e.click() != null && e.contains(mouseX, mouseY);
                e.painter().paint(g, e.x(), e.y(), e.w(), e.h(), hover);
                if (hover || e.hint() != null && e.contains(mouseX, mouseY)) {
                    hint = e.hint() != null ? e.hint() : hint;
                }
            }
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partialTick);
        for (Element e : elements) {
            if (!e.inPanel()) {
                boolean hover = e.contains(mouseX, mouseY);
                e.painter().paint(g, e.x(), e.y(), e.w(), e.h(), hover);
                if (hover && e.hint() != null) {
                    hint = e.hint();
                }
            }
        }
        int visibleH = height - BOTTOM - TOP - 1;
        if (contentHeight > visibleH - 8) {
            int trackTop = TOP + 3;
            int trackH = visibleH - 6;
            int barH = Math.max(16, trackH * visibleH / (contentHeight + 8));
            int max = Math.max(1, contentHeight - visibleH + 8);
            int barY = trackTop + (trackH - barH) * Math.min(scroll[tool], max) / max;
            g.fill(width - 4, barY, width - 2, barY + barH, EDGE);
        }

        text(g, "§lSyrk", 8, 6, TEXT);
        text(g, "§lBuilder", 8 + font.width("§lSyrk"), 6, ACCENT);

        Minecraft mc = Minecraft.getInstance();
        target = mc.player == null ? null : SyrkBuilderClient.lookedAt(mc.player);
        String left;
        if (looking) {
            left = "§fLooking §8- left-click to " + (primaryLabel == null ? "act" : primaryLabel) + ", release right-click to stop";
        } else if (hint != null) {
            left = "§f" + hint;
        } else if (painting) {
            left = "§fDragging §8- move to keep placing, release left-click to stop (one undo)";
        } else {
            String sel = Selection.complete() ? "§f" + Selection.volume() + "§7 blocks selected" : "§7No selection";
            String aim = target == null ? "§8no target §7- hold right-click to look around"
                : "§7target §f" + target.getX() + " " + target.getY() + " " + target.getZ();
            left = sel + "   §8•   " + aim + (primaryLabel == null ? "" : "   §8•   §7left-click: §f" + primaryLabel);
        }
        String status = hint == null && !looking ? StatusLine.get() : "";
        int statusW = status.isEmpty() ? 0 : Math.min(font.width(status), (width - 24) / 2);
        text(g, fit(left, width - 22 - statusW), 6, height - BOTTOM + 4, DIM);
        if (!status.isEmpty()) {
            text(g, fit(status, statusW), width - 8 - statusW, height - BOTTOM + 4, TEXT);
        }
        if (picker != null) {
            picker.render(g, font, realX, realY);
        }
    }
}
