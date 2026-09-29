package dev.syrkbuilder.core.path;

import dev.syrkbuilder.core.brush.Surface;
import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Paths {
    public enum Kind {
        ROAD("road", "minecraft:dirt_path", 5, "flat road: cuts hills, fills dips"),
        WALL("wall", "minecraft:stone_bricks", 1, "wall standing on the curve (height=)"),
        TUNNEL("tunnel", "minecraft:air", 5, "round tunnel through the ground"),
        RIVER("river", "minecraft:sand", 7, "water channel with sloped banks"),
        BRIDGE("bridge", "minecraft:spruce_planks", 5, "deck with railings and pillars"),
        LINE("line", "minecraft:stone", 1, "a tube of blocks along the curve");

        public final String id;
        public final String defaultBlocks;
        public final int defaultWidth;
        public final String description;

        Kind(String id, String defaultBlocks, int defaultWidth, String description) {
            this.id = id;
            this.defaultBlocks = defaultBlocks;
            this.defaultWidth = defaultWidth;
            this.description = description;
        }

        public static Kind byName(String name) {
            for (Kind k : values()) {
                if (k.id.equals(name.toLowerCase(Locale.ROOT))) {
                    return k;
                }
            }
            return null;
        }
    }

    private Paths() {
    }

    public static List<double[]> curve(List<int[]> points, double step) {
        List<double[]> out = new ArrayList<>();
        if (points.isEmpty()) {
            return out;
        }
        List<double[]> p = new ArrayList<>();
        for (int[] q : points) {
            p.add(new double[]{q[0] + 0.5, q[1], q[2] + 0.5});
        }
        if (p.size() == 1) {
            out.add(p.get(0));
            return out;
        }
        for (int i = 0; i < p.size() - 1; i++) {
            double[] p0 = p.get(Math.max(0, i - 1));
            double[] p1 = p.get(i);
            double[] p2 = p.get(i + 1);
            double[] p3 = p.get(Math.min(p.size() - 1, i + 2));
            double len = Math.sqrt(sq(p2[0] - p1[0]) + sq(p2[1] - p1[1]) + sq(p2[2] - p1[2]));
            int n = Math.max(1, (int) Math.ceil(len / step));
            for (int k = 0; k < n; k++) {
                double t = (double) k / n;
                out.add(catmull(p0, p1, p2, p3, t));
            }
        }
        out.add(p.get(p.size() - 1));
        return out;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double[] catmull(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) {
            r[i] = 0.5 * (2 * p1[i] + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2
                + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3);
        }
        return r;
    }

    public static void build(Kind kind, List<int[]> points, int width, int height, Pattern pattern, WorldView world, EditBuffer out) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A path needs at least 2 points - left-click (or /sb path add) where it should go.");
        }
        List<double[]> c = curve(points, 0.35);
        double half = width / 2.0;
        int minY = world.minY();
        int maxY = world.maxY() - 1;
        double travelled = 0;
        double nextPillar = 0;
        for (int i = 0; i < c.size(); i++) {
            double[] p = c.get(i);
            double[] q = c.get(Math.min(c.size() - 1, i + 1));
            double[] o = c.get(Math.max(0, i - 1));
            double tx = q[0] - o[0];
            double tz = q[2] - o[2];
            double tl = Math.sqrt(tx * tx + tz * tz);
            if (tl < 1e-6) {
                tx = 1;
                tz = 0;
                tl = 1;
            }
            double nx = -tz / tl;
            double nz = tx / tl;
            if (i > 0) {
                double[] prev = c.get(i - 1);
                travelled += Math.sqrt(sq(p[0] - prev[0]) + sq(p[2] - prev[2]));
            }
            int cy = (int) Math.round(p[1]);
            for (double s = -half; s <= half + 1e-9; s += 0.4) {
                int x = (int) Math.floor(p[0] + nx * s);
                int z = (int) Math.floor(p[2] + nz * s);
                double edge = Math.abs(s) / Math.max(0.5, half);
                switch (kind) {
                    case ROAD -> {
                        set(out, world, x, cy, z, pattern.pick(x, cy, z), minY, maxY);
                        for (int k = 1; k <= 4; k++) {
                            clear(out, world, x, cy + k, z, minY, maxY);
                        }
                        for (int k = 1; k <= 12; k++) {
                            int y = cy - k;
                            if (y < minY || !Surface.soft(world.blockId(x, y, z))) {
                                break;
                            }
                            set(out, world, x, y, z, "minecraft:dirt", minY, maxY);
                        }
                    }
                    case WALL -> {
                        for (int k = 1; k <= height; k++) {
                            set(out, world, x, cy + k, z, pattern.pick(x, cy + k, z), minY, maxY);
                        }
                    }
                    case TUNNEL -> {
                        double r = half;
                        double h = Math.sqrt(Math.max(0, r * r - s * s));
                        int top = cy + (int) Math.round(r + h);
                        int bottom = cy + 1;
                        for (int y = bottom; y <= top; y++) {
                            clear(out, world, x, y, z, minY, maxY);
                        }
                        String lining = pattern.pick(x, cy, z);
                        if (!lining.endsWith(":air")) {
                            set(out, world, x, cy, z, lining, minY, maxY);
                            if (out.get(x, top + 1, z) == null) {
                                set(out, world, x, top + 1, z, pattern.pick(x, top + 1, z), minY, maxY);
                            }
                        }
                    }
                    case RIVER -> {
                        int depth = Math.max(1, (int) Math.round(Math.max(1.5, half * 0.8) * (1 - edge * edge)));
                        for (int k = 0; k < depth; k++) {
                            set(out, world, x, cy - k, z, "minecraft:water", minY, maxY);
                        }
                        set(out, world, x, cy - depth, z, pattern.pick(x, cy - depth, z), minY, maxY);
                        for (int k = 1; k <= 3; k++) {
                            clear(out, world, x, cy + k, z, minY, maxY);
                        }
                    }
                    case BRIDGE -> {
                        set(out, world, x, cy, z, pattern.pick(x, cy, z), minY, maxY);
                        for (int k = 1; k <= 3; k++) {
                            clear(out, world, x, cy + k, z, minY, maxY);
                        }
                    }
                    case LINE -> {
                        int r = Math.max(0, (width - 1) / 2);
                        for (int dy = -r; dy <= r; dy++) {
                            if (s * s + dy * dy <= (r + 0.5) * (r + 0.5)) {
                                set(out, world, x, cy + dy, z, pattern.pick(x, cy + dy, z), minY, maxY);
                            }
                        }
                    }
                }
            }
            if (kind == Kind.BRIDGE) {
                for (int side = -1; side <= 1; side += 2) {
                    int rx = (int) Math.floor(p[0] + nx * half * side);
                    int rz = (int) Math.floor(p[2] + nz * half * side);
                    set(out, world, rx, cy + 1, rz, "minecraft:spruce_fence", minY, maxY);
                }
                if (travelled >= nextPillar) {
                    nextPillar = travelled + 7;
                    for (int side = -1; side <= 1; side += 2) {
                        int px = (int) Math.floor(p[0] + nx * (half - 0.5) * side);
                        int pz = (int) Math.floor(p[2] + nz * (half - 0.5) * side);
                        for (int y = cy - 1; y >= Math.max(minY, cy - 64); y--) {
                            String id = world.blockId(px, y, pz);
                            if (!Surface.soft(id) && !id.equals("minecraft:water")) {
                                break;
                            }
                            set(out, world, px, y, pz, "minecraft:stone_bricks", minY, maxY);
                        }
                    }
                }
            }
        }
    }

    private static void set(EditBuffer out, WorldView world, int x, int y, int z, String block, int minY, int maxY) {
        if (y >= minY && y <= maxY) {
            out.set(x, y, z, block);
        }
    }

    private static void clear(EditBuffer out, WorldView world, int x, int y, int z, int minY, int maxY) {
        if (y >= minY && y <= maxY && out.get(x, y, z) == null && !Surface.air(world.blockId(x, y, z))) {
            out.set(x, y, z, "minecraft:air");
        }
    }
}
