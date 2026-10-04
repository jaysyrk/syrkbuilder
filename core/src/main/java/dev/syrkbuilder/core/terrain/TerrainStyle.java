package dev.syrkbuilder.core.terrain;

import java.util.List;
import java.util.Locale;

public abstract class TerrainStyle {
    public static final class Column {
        public int x;
        public int z;
        public int top;
        public double slope;
        public double heightFrac;
        public double jitter;
    }

    public abstract String block(Column c, int y, int depth);

    public static final List<String> NAMES = List.of("alpine", "grassy", "desert", "mesa", "volcanic", "rocky", "snowy", "coastal", "swamp");

    public static TerrainStyle byName(String name) {
        switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "alpine": return ALPINE;
            case "grassy": return GRASSY;
            case "desert": return DESERT;
            case "mesa": return MESA;
            case "volcanic": return VOLCANIC;
            case "rocky": return ROCKY;
            case "snowy": return SNOWY;
            case "coastal": return COASTAL;
            case "swamp": return SWAMP;
            default: return null;
        }
    }

    protected static double rand(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 0x9E3779B97F4A7C15L;
        h = h * h * 42317861L + h * 11L;
        h ^= h >>> 29;
        return ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    protected static String pick(int x, int y, int z, String... blocks) {
        return blocks[(int) (rand(x, y, z) * blocks.length)];
    }

    private static final String[] ROCK = {"minecraft:stone", "minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:tuff"};

    static final TerrainStyle ALPINE = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            double snowline = 0.62 + c.jitter * 0.08;
            if (c.heightFrac > snowline && c.slope < 2.5 && depth <= 1) {
                return "minecraft:snow_block";
            }
            if (c.slope >= 1.6 || c.heightFrac > snowline || (c.heightFrac > 0.4 + c.jitter * 0.1 && c.slope >= 0.9)) {
                return depth == 0 && c.slope < 2 && rand(c.x, y, c.z) < 0.3 ? "minecraft:gravel" : pick(c.x, y, c.z, ROCK);
            }
            if (depth == 0) {
                return c.heightFrac > 0.45 && rand(c.x, y, c.z) < 0.35 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
            }
            return depth < 4 ? "minecraft:dirt" : pick(c.x, y, c.z, ROCK);
        }
    };

    static final TerrainStyle GRASSY = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            if (c.slope >= 2.2) {
                return depth == 0 && rand(c.x, y, c.z) < 0.4 ? "minecraft:coarse_dirt" : pick(c.x, y, c.z, ROCK);
            }
            if (depth == 0) {
                return "minecraft:grass_block";
            }
            return depth < 4 ? "minecraft:dirt" : "minecraft:stone";
        }
    };

    static final TerrainStyle DESERT = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            if (c.slope >= 2.4 && depth > 0) {
                return pick(c.x, y, c.z, "minecraft:sandstone", "minecraft:sandstone", "minecraft:smooth_sandstone");
            }
            return depth < 4 ? "minecraft:sand" : "minecraft:sandstone";
        }
    };

    private static final String[] BANDS = {
        "minecraft:terracotta", "minecraft:orange_terracotta", "minecraft:terracotta", "minecraft:yellow_terracotta",
        "minecraft:red_terracotta", "minecraft:white_terracotta", "minecraft:brown_terracotta", "minecraft:light_gray_terracotta",
        "minecraft:orange_terracotta", "minecraft:red_terracotta"
    };

    static final TerrainStyle MESA = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            if (depth == 0 && c.slope < 1.2) {
                return rand(c.x, y, c.z) < 0.15 ? "minecraft:coarse_dirt" : "minecraft:red_sand";
            }
            int band = Math.floorMod(y + (int) Math.round(c.jitter * 1.5), BANDS.length * 2) / 2;
            return BANDS[band];
        }
    };

    static final TerrainStyle VOLCANIC = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            if (depth == 0 && c.heightFrac > 0.8 && rand(c.x, y, c.z) < 0.35) {
                return "minecraft:magma_block";
            }
            if (depth <= 1) {
                return pick(c.x, y, c.z, "minecraft:basalt", "minecraft:blackstone", "minecraft:blackstone", "minecraft:tuff", "minecraft:smooth_basalt");
            }
            return pick(c.x, y, c.z, "minecraft:blackstone", "minecraft:basalt", "minecraft:stone");
        }
    };

    static final TerrainStyle ROCKY = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            if (depth == 0 && c.slope < 1 && rand(c.x, y, c.z) < 0.4) {
                return "minecraft:gravel";
            }
            return pick(c.x, y, c.z, ROCK);
        }
    };

    static final TerrainStyle SNOWY = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            if (depth <= 1 && c.slope < 2.5) {
                return "minecraft:snow_block";
            }
            return depth == 0 && rand(c.x, y, c.z) < 0.25 ? "minecraft:packed_ice" : pick(c.x, y, c.z, ROCK);
        }
    };

    static final TerrainStyle COASTAL = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            double low = c.heightFrac < 0.02 ? 1 : 0;
            if (depth == 0) {
                if (low > 0) {
                    double r = rand(c.x, y, c.z);
                    return r < 0.2 ? "minecraft:gravel" : r < 0.3 ? "minecraft:clay" : "minecraft:sand";
                }
                if (c.slope >= 2.2) {
                    return pick(c.x, y, c.z, ROCK);
                }
                return c.heightFrac < 0.14 ? "minecraft:sand" : "minecraft:grass_block";
            }
            if (c.slope >= 2.2 && depth > 1) {
                return pick(c.x, y, c.z, ROCK);
            }
            if (depth < 4) {
                return low > 0 || c.heightFrac < 0.14 ? "minecraft:sand" : "minecraft:dirt";
            }
            return depth < 7 && c.heightFrac < 0.14 ? "minecraft:sandstone" : "minecraft:stone";
        }
    };

    static final TerrainStyle SWAMP = new TerrainStyle() {
        @Override
        public String block(Column c, int y, int depth) {
            if (depth == 0) {
                if (c.heightFrac < 0.02) {
                    return rand(c.x, y, c.z) < 0.3 ? "minecraft:clay" : "minecraft:mud";
                }
                double r = rand(c.x, y, c.z);
                return r < 0.25 ? "minecraft:mud" : r < 0.4 ? "minecraft:moss_block" : r < 0.5 ? "minecraft:podzol" : "minecraft:grass_block";
            }
            return depth < 4 ? (c.heightFrac < 0.1 ? "minecraft:mud" : "minecraft:dirt") : "minecraft:stone";
        }
    };
}
