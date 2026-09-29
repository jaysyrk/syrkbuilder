package dev.syrkbuilder.core.model;

import dev.syrkbuilder.core.grid.BlockGrid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlockPalette {
    private record Entry(String block, double l, double a, double b) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Integer> rgb = new ArrayList<>();
    private final Map<Integer, String> cache = new HashMap<>();

    public static final List<String> NAMES = List.of("all", "concrete", "wool", "terracotta", "natural", "wood");

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] CONCRETE = {0xCFD5D6, 0xE06100, 0xA9309F, 0x2389C6, 0xF0AF15, 0x5EA818, 0xD5658E, 0x36393D,
        0x7D7D73, 0x157788, 0x641F9C, 0x2C2E8F, 0x603B1F, 0x495B24, 0x8E2020, 0x080A0F};
    private static final int[] WOOL = {0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C527, 0x70B919, 0xED8DAC, 0x3E4447,
        0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA02722, 0x141519};
    private static final int[] TERRACOTTA = {0xD1B2A1, 0xA15325, 0x95586C, 0x716C89, 0xBA8523, 0x677534, 0xA14E4E, 0x392A23,
        0x876A61, 0x565B5B, 0x764656, 0x4A3B5B, 0x4D3323, 0x4C532A, 0x8F3D2E, 0x251610};

    private static final Object[][] NATURAL = {
        {"stone", 0x7D7D7D}, {"cobblestone", 0x7F7F7F}, {"andesite", 0x888888}, {"diorite", 0xBCBCBC}, {"granite", 0x956755},
        {"deepslate", 0x505052}, {"blackstone", 0x2A2429}, {"smooth_stone", 0x9E9E9E}, {"stone_bricks", 0x7A797A},
        {"bricks", 0x966153}, {"sandstone", 0xD8CB9B}, {"sand", 0xDBCFA3}, {"red_sand", 0xBE6621}, {"red_sandstone", 0xBA631D},
        {"gravel", 0x837F7E}, {"dirt", 0x866043}, {"coarse_dirt", 0x77553B}, {"mud", 0x3C393C}, {"packed_mud", 0x8E6A4F},
        {"mud_bricks", 0x89674F}, {"clay", 0xA0A6B3}, {"snow_block", 0xF9FEFE}, {"packed_ice", 0x8DB4FA}, {"calcite", 0xDFE0DC},
        {"tuff", 0x6C6D66}, {"smooth_basalt", 0x48484E}, {"obsidian", 0x0F0A18}, {"netherrack", 0x612626},
        {"nether_bricks", 0x2C151A}, {"quartz_block", 0xEBE5DE}, {"prismarine", 0x639C97}, {"dark_prismarine", 0x335B4B},
        {"purpur_block", 0xA97DA9}, {"end_stone", 0xDBDE9E}, {"moss_block", 0x596D2D}, {"mossy_cobblestone", 0x6E765E},
        {"bone_block", 0xE5E1CF}, {"honeycomb_block", 0xE5941D}, {"melon", 0x6F911E}, {"dried_kelp_block", 0x323A26},
        {"amethyst_block", 0x8561BF}, {"crying_obsidian", 0x200A3C}, {"sea_lantern", 0xACC7BE}, {"shroomlight", 0xF09246},
        {"glowstone", 0xAB8354}, {"gold_block", 0xF6D03D}, {"iron_block", 0xDCDCDC}, {"diamond_block", 0x62EDE4},
        {"emerald_block", 0x2ACB57}, {"lapis_block", 0x1E438C}, {"redstone_block", 0xAF1805}, {"copper_block", 0xC06B4F},
        {"oxidized_copper", 0x52A284}, {"netherite_block", 0x423D3F}, {"raw_iron_block", 0xA6876B}, {"raw_gold_block", 0xDDA92E},
        {"raw_copper_block", 0x9A694F}
    };
    private static final Object[][] WOOD = {
        {"oak_planks", 0xA2824E}, {"spruce_planks", 0x725430}, {"birch_planks", 0xC0AF79}, {"jungle_planks", 0xA07350},
        {"acacia_planks", 0xA85A32}, {"dark_oak_planks", 0x422B14}, {"mangrove_planks", 0x753630}, {"cherry_planks", 0xE2B2AC},
        {"bamboo_planks", 0xC1AD50}, {"crimson_planks", 0x653046}, {"warped_planks", 0x2B6863}
    };

    private static Map<String, Integer> allColors;

    public static Integer colorOf(String block) {
        String b = block.trim();
        if (b.startsWith("#") && b.length() == 7) {
            try {
                return Integer.parseInt(b.substring(1), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (allColors == null) {
            BlockPalette all = named("all");
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; i < all.entries.size(); i++) {
                m.put(all.entries.get(i).block(), all.rgb.get(i));
            }
            allColors = m;
        }
        String id = dev.syrkbuilder.core.edit.Pattern.baseId(dev.syrkbuilder.core.edit.Pattern.normalize(b));
        return allColors.get(id);
    }

    public static List<String> gradient(String from, String to, int steps, String palette) {
        Integer a = colorOf(from);
        Integer b = colorOf(to);
        if (a == null) {
            throw new IllegalArgumentException("No colour known for '" + from + "' - use a known block or #rrggbb.");
        }
        if (b == null) {
            throw new IllegalArgumentException("No colour known for '" + to + "' - use a known block or #rrggbb.");
        }
        BlockPalette p = named(palette);
        double[] la = lab(a);
        double[] lb = lab(b);
        List<String> out = new ArrayList<>();
        int n = Math.max(2, steps);
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            String block;
            if (i == 0 && !from.startsWith("#")) {
                block = dev.syrkbuilder.core.edit.Pattern.baseId(dev.syrkbuilder.core.edit.Pattern.normalize(from));
            } else if (i == n - 1 && !to.startsWith("#")) {
                block = dev.syrkbuilder.core.edit.Pattern.baseId(dev.syrkbuilder.core.edit.Pattern.normalize(to));
            } else {
                block = p.nearestLab(la[0] + (lb[0] - la[0]) * t, la[1] + (lb[1] - la[1]) * t, la[2] + (lb[2] - la[2]) * t);
            }
            if (out.isEmpty() || !out.get(out.size() - 1).equals(block)) {
                out.add(block);
            }
        }
        return out;
    }

    private String nearestLab(double l, double a, double b) {
        Entry best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entry e : entries) {
            double d = (e.l - l) * (e.l - l) + (e.a - a) * (e.a - a) + (e.b - b) * (e.b - b);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best.block;
    }

    public List<String> nearest(int rgb, int count) {
        double[] lab = lab(rgb);
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(java.util.Comparator.comparingDouble(e ->
            (e.l - lab[0]) * (e.l - lab[0]) + (e.a - lab[1]) * (e.a - lab[1]) + (e.b - lab[2]) * (e.b - lab[2])));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, sorted.size()); i++) {
            out.add(sorted.get(i).block());
        }
        return out;
    }

    public static BlockPalette named(String name) {
        BlockPalette p = new BlockPalette();
        String n = name == null ? "all" : name.toLowerCase(Locale.ROOT);
        boolean all = n.equals("all");
        if (all || n.equals("concrete")) {
            p.addColors("_concrete", CONCRETE);
        }
        if (all || n.equals("wool")) {
            p.addColors("_wool", WOOL);
        }
        if (all || n.equals("terracotta")) {
            p.addColors("_terracotta", TERRACOTTA);
            p.add("minecraft:terracotta", 0x985E43);
        }
        if (all || n.equals("natural")) {
            p.addAll(NATURAL);
        }
        if (all || n.equals("wood")) {
            p.addAll(WOOD);
        }
        if (p.entries.isEmpty()) {
            throw new IllegalArgumentException("Unknown palette '" + name + "'. Palettes: " + String.join(", ", NAMES));
        }
        return p;
    }

    private void addColors(String suffix, int[] colors) {
        for (int i = 0; i < COLORS.length; i++) {
            add("minecraft:" + COLORS[i] + suffix, colors[i]);
        }
    }

    private void addAll(Object[][] table) {
        for (Object[] row : table) {
            add("minecraft:" + row[0], (Integer) row[1]);
        }
    }

    public void add(String block, int color) {
        double[] lab = lab(color);
        entries.add(new Entry(block, lab[0], lab[1], lab[2]));
        rgb.add(color);
    }

    public String match(int rgb) {
        int key = ((rgb >> 19) & 31) << 10 | ((rgb >> 11) & 31) << 5 | ((rgb >> 3) & 31);
        String hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        double[] lab = lab(rgb);
        Entry best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entry e : entries) {
            double dl = e.l - lab[0];
            double da = e.a - lab[1];
            double db = e.b - lab[2];
            double d = dl * dl + da * da + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        cache.put(key, best.block);
        return best.block;
    }

    public BlockGrid toGrid(VoxelModel model) {
        BlockGrid grid = new BlockGrid(model.sizeX, model.sizeY, model.sizeZ);
        for (int y = 0; y < model.sizeY; y++) {
            for (int z = 0; z < model.sizeZ; z++) {
                for (int x = 0; x < model.sizeX; x++) {
                    int c = model.get(x, y, z);
                    if ((c >>> 24) != 0) {
                        grid.set(x, y, z, match(c & 0xFFFFFF));
                    }
                }
            }
        }
        return grid;
    }

    static double[] lab(int rgb) {
        double r = linear(((rgb >> 16) & 255) / 255.0);
        double g = linear(((rgb >> 8) & 255) / 255.0);
        double b = linear((rgb & 255) / 255.0);
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
        x = f(x);
        y = f(y);
        z = f(z);
        return new double[]{116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }
}
