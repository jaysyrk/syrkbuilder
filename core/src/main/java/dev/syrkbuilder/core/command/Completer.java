package dev.syrkbuilder.core.command;

import dev.syrkbuilder.core.model.BlockPalette;
import dev.syrkbuilder.core.terrain.TerrainStyle;
import dev.syrkbuilder.core.terrain.TerrainType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class Completer {
    private enum Kind { TREE, PATH_SUB, NUDGE, PREVIEW, FORMAT, SYMMETRY, MASK, BRUSH, BLOCKS, NUMBER, TERRAIN, ROTATION, TEMPLATE_SUB, TEMPLATE, MARKER_SUB, HISTORY, NAME, MODEL, SCRIPT, FREE, BIOME }

    private record Spec(List<Kind> args, List<String> numberHints, List<String> options, List<String> flags) {
    }

    public static final java.util.regex.Pattern GRADIENT_PREFIX = java.util.regex.Pattern.compile("^grad(?:[xyzr]|\\([^)]*\\))?:");
    private static final List<String> PASTE_OPTIONS = List.of("rotate=", "swap=", "up=");
    private static final List<String> TERRAIN_OPTIONS = List.of("radius=", "height=", "style=", "seed=", "erosion=", "roughness=",
        "peaks=", "steps=", "width=", "angle=");

    private static final Map<String, Spec> SPECS = Map.ofEntries(
        Map.entry("set", spec(List.of(Kind.BLOCKS))),
        Map.entry("walls", spec(List.of(Kind.BLOCKS))),
        Map.entry("outline", spec(List.of(Kind.BLOCKS))),
        Map.entry("replace", spec(List.of(Kind.BLOCKS, Kind.BLOCKS))),
        Map.entry("line", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER), List.of("1", "2", "3"), List.of(), List.of("-a"))),
        Map.entry("sphere", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER), List.of("5", "10", "20"), List.of(), List.of("-h", "-a"))),
        Map.entry("ellipsoid", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER, Kind.NUMBER, Kind.NUMBER), List.of("8", "4", "12"), List.of(), List.of("-h", "-a"))),
        Map.entry("dome", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER), List.of("8", "12", "20"), List.of(), List.of("-h", "-a"))),
        Map.entry("cyl", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER, Kind.NUMBER), List.of("5", "10", "20"), List.of(), List.of("-h", "-a"))),
        Map.entry("circle", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER, Kind.NUMBER), List.of("10", "20", "1", "3"), List.of(), List.of("-v", "-a"))),
        Map.entry("disc", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER), List.of("10", "20"), List.of(), List.of("-v", "-a"))),
        Map.entry("cone", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER, Kind.NUMBER), List.of("6", "12", "20"), List.of(), List.of("-h", "-a"))),
        Map.entry("pyramid", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER), List.of("8", "16"), List.of(), List.of("-h", "-a"))),
        Map.entry("torus", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER, Kind.NUMBER), List.of("10", "3"), List.of(), List.of("-h", "-a"))),
        Map.entry("helix", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER, Kind.NUMBER), List.of("6", "20"), List.of("turns=", "thickness="), List.of("-a"))),
        Map.entry("terrain", new Spec(List.of(Kind.TERRAIN), List.of(), TERRAIN_OPTIONS, List.of())),
        Map.entry("brush", new Spec(List.of(Kind.BRUSH, Kind.NUMBER, Kind.BLOCKS), List.of("3", "5", "8"),
            List.of("strength=", "density=", "depth=", "height=", "scale=", "from=", "type=", "rx=", "ry=", "rz="), List.of("-r"))),
        Map.entry("copy", spec(List.of())),
        Map.entry("paste", new Spec(List.of(), List.of(), PASTE_OPTIONS, List.of("-a", "-flip"))),
        Map.entry("rotate", spec(List.of(Kind.ROTATION))),
        Map.entry("flip", spec(List.of())),
        Map.entry("template", new Spec(List.of(Kind.TEMPLATE_SUB, Kind.TEMPLATE, Kind.FORMAT), List.of(), PASTE_OPTIONS, List.of("-a", "-flip"))),
        Map.entry("marker", spec(List.of(Kind.MARKER_SUB, Kind.NAME))),
        Map.entry("undo", new Spec(List.of(Kind.NUMBER), List.of("1", "5", "10"), List.of(), List.of())),
        Map.entry("redo", new Spec(List.of(Kind.NUMBER), List.of("1", "5", "10"), List.of(), List.of())),
        Map.entry("history", new Spec(List.of(Kind.NUMBER), List.of("15", "50"), List.of(), List.of())),
        Map.entry("goto", spec(List.of(Kind.HISTORY))),
        Map.entry("checkpoint", spec(List.of(Kind.NAME))),
        Map.entry("restore", spec(List.of(Kind.HISTORY))),
        Map.entry("help", spec(List.of())),
        Map.entry("mask", spec(List.of(Kind.MASK))),
        Map.entry("fill", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER), List.of("8", "12", "24"), List.of("mode="), List.of())),
        Map.entry("tree", new Spec(List.of(Kind.TREE, Kind.NUMBER), List.of("6", "10", "16"), List.of("seed="), List.of())),
        Map.entry("path", new Spec(List.of(Kind.PATH_SUB, Kind.NUMBER, Kind.BLOCKS), List.of("3", "5", "7"), List.of("height="), List.of())),
        Map.entry("nudge", new Spec(List.of(Kind.NUDGE, Kind.NUMBER, Kind.NUMBER), List.of("1", "2", "5"), List.of(), List.of())),
        Map.entry("turn", new Spec(List.of(Kind.NUMBER), List.of("90", "180", "270", "-90"), List.of(), List.of())),
        Map.entry("cancel", spec(List.of())),
        Map.entry("confirm", spec(List.of())),
        Map.entry("preview", spec(List.of(Kind.PREVIEW))),
        Map.entry("symmetry", spec(List.of(Kind.SYMMETRY))),
        Map.entry("gradient", new Spec(List.of(Kind.BLOCKS, Kind.BLOCKS, Kind.NUMBER), List.of("3", "5", "8"), List.of("palette="), List.of())),
        Map.entry("import", new Spec(List.of(Kind.MODEL), List.of(), List.of("size=", "palette=", "rotate=", "swap=", "up="), List.of("-s", "-a", "-flip"))),
        Map.entry("move", new Spec(List.of(Kind.NUMBER, Kind.NUDGE), List.of("1", "5", "10"), List.of("leave="), List.of("-a"))),
        Map.entry("stack", new Spec(List.of(Kind.NUMBER, Kind.NUDGE), List.of("1", "3", "5"), List.of(), List.of("-a", "-s"))),
        Map.entry("hollow", new Spec(List.of(Kind.NUMBER, Kind.BLOCKS), List.of("1", "2", "3"), List.of(), List.of())),
        Map.entry("overlay", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER), List.of("1", "2", "3"), List.of(), List.of())),
        Map.entry("naturalize", spec(List.of())),
        Map.entry("count", spec(List.of(Kind.BLOCKS))),
        Map.entry("distr", spec(List.of())),
        Map.entry("select", new Spec(List.of(Kind.NUMBER), List.of("50000", "200000"), List.of(), List.of("-a", "-d"))),
        Map.entry("expand", new Spec(List.of(Kind.NUMBER, Kind.NUDGE), List.of("1", "5", "10"), List.of(), List.of())),
        Map.entry("contract", new Spec(List.of(Kind.NUMBER, Kind.NUDGE), List.of("1", "5", "10"), List.of(), List.of())),
        Map.entry("shift", new Spec(List.of(Kind.NUMBER, Kind.NUDGE), List.of("1", "5", "10"), List.of(), List.of())),
        Map.entry("size", spec(List.of())),
        Map.entry("cut", spec(List.of())),
        Map.entry("biome", new Spec(List.of(Kind.BIOME, Kind.NUMBER), List.of("8", "16", "32"), List.of(), List.of("-s"))),
        Map.entry("text", new Spec(List.of(Kind.BLOCKS, Kind.FREE), List.of(), List.of("size=", "depth="), List.of("-f"))),
        Map.entry("arch", new Spec(List.of(Kind.BLOCKS, Kind.NUMBER, Kind.NUMBER), List.of("12", "20", "8"), List.of("thickness=", "depth="), List.of())),
        Map.entry("replacenear", new Spec(List.of(Kind.NUMBER, Kind.BLOCKS, Kind.BLOCKS), List.of("5", "10", "20"), List.of(), List.of())),
        Map.entry("smooth", new Spec(List.of(Kind.NUMBER), List.of("1", "3", "6"), List.of(), List.of())),
        Map.entry("drain", new Spec(List.of(Kind.NUMBER), List.of("5", "10", "20"), List.of(), List.of())),
        Map.entry("snow", new Spec(List.of(Kind.NUMBER), List.of("8", "12", "24"), List.of(), List.of())),
        Map.entry("thaw", new Spec(List.of(Kind.NUMBER), List.of("8", "12", "24"), List.of(), List.of())),
        Map.entry("green", new Spec(List.of(Kind.NUMBER), List.of("8", "12", "24"), List.of(), List.of())),
        Map.entry("script", new Spec(List.of(Kind.SCRIPT, Kind.FREE, Kind.FREE, Kind.FREE), List.of(), List.of(), List.of())));

    public static final List<String> COMMANDS = List.of("help", "set", "replace", "walls", "outline", "line", "sphere", "ellipsoid",
        "dome", "cyl", "circle", "disc", "cone", "pyramid", "torus", "helix", "terrain", "copy", "paste", "rotate", "flip",
        "template", "marker", "undo", "redo", "history", "goto", "checkpoint", "restore", "mask", "symmetry", "gradient",
        "tree", "path", "fill", "nudge", "turn", "cancel", "confirm", "preview", "move", "stack", "hollow", "overlay",
        "naturalize", "count", "distr", "select", "expand", "contract", "shift", "size", "cut", "smooth", "drain", "snow", "thaw", "green", "text", "arch", "replacenear", "biome");

    public static final List<String> BIOMES = List.of("plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert", "swamp", "mangrove_swamp",
        "forest", "flower_forest", "birch_forest", "dark_forest", "pale_garden", "old_growth_birch_forest", "old_growth_pine_taiga",
        "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna", "savanna_plateau", "windswept_hills", "windswept_gravelly_hills",
        "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle", "bamboo_jungle", "badlands", "eroded_badlands",
        "wooded_badlands", "meadow", "cherry_grove", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks", "river",
        "frozen_river", "beach", "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean",
        "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "mushroom_fields", "dripstone_caves", "lush_caves",
        "deep_dark", "nether_wastes", "warped_forest", "crimson_forest", "soul_sand_valley", "basalt_deltas", "the_end", "end_highlands",
        "end_midlands", "small_end_islands", "end_barrens", "the_void");

    private static Spec spec(List<Kind> args) {
        return new Spec(args, List.of(), List.of(), List.of());
    }

    private final Supplier<Collection<String>> blocks;
    private final Supplier<Collection<String>> templates;
    private final Supplier<Collection<String>> scripts;
    private final Supplier<Collection<String>> models;

    public Completer(Supplier<Collection<String>> blocks, Supplier<Collection<String>> templates,
                     Supplier<Collection<String>> scripts, Supplier<Collection<String>> models) {
        this.blocks = blocks;
        this.templates = templates;
        this.scripts = scripts;
        this.models = models;
    }

    public static int wordStart(String typed) {
        return typed.lastIndexOf(' ') + 1;
    }

    public List<String> complete(String typed, String command) {
        String[] parts = typed.split(" ", -1);
        String word = parts[parts.length - 1];
        List<String> before = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                before.add(parts[i]);
            }
        }
        if (command == null) {
            if (before.isEmpty()) {
                return filter(COMMANDS, word);
            }
            command = before.remove(0).toLowerCase(Locale.ROOT);
        }
        command = switch (command) {
            case "cylinder" -> "cyl";
            case "ring" -> "circle";
            case "t" -> "terrain";
            case "tpl" -> "template";
            case "markers" -> "marker";
            case "b" -> "brush";
            case "hist" -> "history";
            case "cp" -> "checkpoint";
            case "jump" -> "goto";
            case "sym", "mirror" -> "symmetry";
            case "grad" -> "gradient";
            default -> command;
        };
        Spec spec = SPECS.get(command);
        if (spec == null) {
            return List.of();
        }
        if (command.equals("brush") && !before.isEmpty() && before.get(0).equalsIgnoreCase("bind")) {
            before.remove(0);
        }
        int position = 0;
        String sub = null;
        for (String b : before) {
            if (!b.startsWith("-") && !(b.contains("=") && !b.contains("["))) {
                if (position == 0) {
                    sub = b.toLowerCase(Locale.ROOT);
                }
                position++;
            }
        }
        if (word.contains("=") && !word.contains("[")) {
            return optionValues(word);
        }
        if (word.startsWith("-")) {
            return filter(spec.flags(), word);
        }
        Set<String> out = new LinkedHashSet<>();
        if (position < spec.args().size()) {
            out.addAll(positional(spec.args().get(position), spec, word, sub, command));
        }
        if (position >= Math.min(1, spec.args().size()) || spec.args().isEmpty()) {
            boolean templateWithoutPaste = command.equals("template") && !"paste".equals(sub);
            if (!templateWithoutPaste) {
                out.addAll(filter(spec.options(), word));
                if (!word.isEmpty()) {
                    out.addAll(filter(spec.flags(), word));
                }
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> positional(Kind kind, Spec spec, String word, String sub, String command) {
        return switch (kind) {
            case BRUSH -> {
                List<String> names = new ArrayList<>();
                for (dev.syrkbuilder.core.brush.BrushType t : dev.syrkbuilder.core.brush.BrushType.values()) {
                    names.add(t.id());
                }
                names.addAll(List.of("list", "bind", "unbind", "binds"));
                yield filter(names, word);
            }
            case BLOCKS -> {
                dev.syrkbuilder.core.brush.BrushType bt = command.equals("brush") && sub != null ? dev.syrkbuilder.core.brush.BrushType.byName(sub) : null;
                yield bt != null && !bt.needsBlocks ? List.of() : blocks(word);
            }
            case NUMBER -> filter(spec.numberHints(), word);
            case SYMMETRY -> filter(List.of("x", "z", "xz", "off"), word);
            case TREE -> {
                List<String> trees = new ArrayList<>(dev.syrkbuilder.core.tree.Trees.Type.NAMES);
                trees.add("mix");
                trees.add("list");
                yield filter(trees, word);
            }
            case PATH_SUB -> filter(List.of("add", "undo", "clear", "road", "wall", "tunnel", "river", "bridge", "line"), word);
            case NUDGE -> filter(List.of("up", "down", "left", "right", "forward", "back", "1", "-1"), word);
            case PREVIEW -> filter(List.of("on", "off"), word);
            case BIOME -> filter(BIOMES, word);
            case MASK -> {
                List<String> out = new ArrayList<>(filter(List.of("off"), word));
                out.addAll(blocks(word));
                yield out;
            }
            case TERRAIN -> {
                List<String> types = new ArrayList<>();
                for (TerrainType t : TerrainType.values()) {
                    types.add(t.id());
                }
                types.add("list");
                yield filter(types, word);
            }
            case ROTATION -> filter(List.of("90", "180", "270"), word);
            case TEMPLATE_SUB -> filter(List.of("save", "export", "paste", "load", "list", "info", "delete"), word);
            case TEMPLATE -> "list".equals(sub) ? List.of() : filter(templates.get(), word);
            case FORMAT -> "export".equals(sub) ? filter(List.of("schem", "litematic"), word) : List.of();
            case MARKER_SUB -> filter(List.of("list", "remove", "clear"), word);
            case HISTORY -> filter(List.of("start", "#1"), word);
            case MODEL -> filter(models.get(), word);
            case SCRIPT -> filter(scripts.get(), word);
            case NAME, FREE -> List.of();
        };
    }

    private List<String> optionValues(String word) {
        int eq = word.indexOf('=');
        String key = word.substring(0, eq + 1).toLowerCase(Locale.ROOT);
        String value = word.substring(eq + 1);
        List<String> values = switch (key) {
            case "style=" -> TerrainStyle.NAMES;
            case "palette=" -> BlockPalette.NAMES;
            case "rotate=" -> List.of("90", "180", "270");
            case "radius=" -> List.of("32", "48", "64", "96");
            case "height=" -> List.of("20", "40", "60", "90");
            case "erosion=" -> List.of("0", "25", "50", "75", "100");
            case "roughness=" -> List.of("0.2", "0.5", "0.8");
            case "peaks=" -> List.of("1", "3", "5");
            case "steps=" -> List.of("2", "4", "6");
            case "size=" -> List.of("32", "48", "64", "96");
            case "strength=" -> List.of("0.3", "0.5", "1", "3", "6");
            case "density=" -> List.of("0.05", "0.15", "0.4");
            case "depth=" -> List.of("1", "2", "3");
            case "scale=" -> List.of("4", "8", "16", "32");
            case "mode=" -> List.of("hole", "connected", "room");
            case "type=" -> {
                List<String> trees = new ArrayList<>(dev.syrkbuilder.core.tree.Trees.Type.NAMES);
                trees.add("mix");
                yield trees;
            }

            case "from=" -> swapValues(value);
            case "swap=" -> swapValues(value);
            default -> List.of();
        };
        List<String> out = new ArrayList<>();
        for (String v : key.equals("swap=") || key.equals("from=") ? values : filter(values, value)) {
            out.add(key + v);
        }
        return out;
    }

    private List<String> swapValues(String value) {
        int cut = Math.max(value.lastIndexOf(','), value.lastIndexOf(':'));
        String prefix = value.substring(0, cut + 1);
        String frag = value.substring(cut + 1);
        List<String> out = new ArrayList<>();
        for (String b : shortBlocks()) {
            if (b.startsWith(frag.toLowerCase(Locale.ROOT)) && out.size() < 60) {
                out.add(prefix + b);
            }
        }
        return out;
    }

    private List<String> blocks(String word) {
        int lead = word.startsWith("!") ? 1 : 0;
        java.util.regex.Matcher g = GRADIENT_PREFIX.matcher(word);
        if (g.find()) {
            lead = g.end();
        }
        int cut = Math.max(lead - 1, Math.max(word.lastIndexOf(','), word.lastIndexOf('%')));
        String prefix = word.substring(0, cut + 1);
        String frag = word.substring(cut + 1).toLowerCase(Locale.ROOT);
        if (frag.contains("[")) {
            return List.of();
        }
        boolean namespaced = frag.contains(":");
        List<String> out = new ArrayList<>();
        for (String id : blocks.get()) {
            String candidate = namespaced ? id : id.startsWith("minecraft:") ? id.substring(10) : id;
            if (candidate.startsWith(frag)) {
                out.add(prefix + candidate);
            }
        }
        out.sort(null);
        return out.size() > 200 ? out.subList(0, 200) : out;
    }

    private List<String> shortBlocks() {
        List<String> out = new ArrayList<>();
        for (String id : blocks.get()) {
            out.add(id.startsWith("minecraft:") ? id.substring(10) : id);
        }
        out.sort(null);
        return out;
    }

    private static List<String> filter(Collection<String> options, String word) {
        String w = word.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(w)) {
                out.add(o);
            }
        }
        return out;
    }
}
