package dev.syrkbuilder.core.brush;

import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.noise.PerlinNoise;
import dev.syrkbuilder.core.terrain.Erosion;
import dev.syrkbuilder.core.terrain.Heightfield;
import java.util.Random;
import java.util.Set;

public final class Brushes {
    public record Settings(BrushType type, int radius, Pattern pattern, double strength, double density, int depth,
                           int height, double scale, boolean replaceTop, Set<String> from, long seed, String variant) {
    }

    private Brushes() {
    }

    public static EditBuffer apply(Settings s, WorldView world, int cx, int cy, int cz, long limit) {
        EditBuffer out = new EditBuffer(limit);
        int r = s.radius();
        if (s.pattern() != null) {
            s.pattern().bind(new dev.syrkbuilder.core.edit.Box(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r));
        }
        switch (s.type()) {
            case SPHERE, ERASE, PAINT, REPLACE, FILL -> ball(s, world, cx, cy, cz, out);
            case OVERLAY, SCATTER -> surface(s, world, cx, cy, cz, out);
            case SPIKES -> spikes(s, world, cx, cy, cz, out);
            case TREES -> forest(s, world, cx, cy, cz, out);
            case BLOB, CARVE, SCULPT, INFLATE, DEFLATE, ROUGHEN, DECAY, SPLATTER -> VoxelBrushes.apply(s, world, cx, cy, cz, out);
            default -> terrain(s, world, cx, cy, cz, out);
        }
        return out;
    }

    private static boolean inBall(int dx, int dy, int dz, int r) {
        double a = r + 0.5;
        return dx * dx + dy * dy + dz * dz <= a * a;
    }

    private static void ball(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int r = s.radius();
        int minY = Math.max(world.minY(), cy - r);
        int maxY = Math.min(world.maxY() - 1, cy + r);
        for (int y = minY; y <= maxY; y++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    if (!inBall(x - cx, y - cy, z - cz, r)) {
                        continue;
                    }
                    String id = world.blockId(x, y, z);
                    switch (s.type()) {
                        case SPHERE -> out.set(x, y, z, s.pattern().pick(x, y, z));
                        case ERASE -> {
                            if (!Surface.air(id)) {
                                out.set(x, y, z, "minecraft:air");
                            }
                        }
                        case PAINT -> {
                            if (!Surface.air(id) && exposed(world, x, y, z)) {
                                out.set(x, y, z, s.pattern().pick(x, y, z));
                            }
                        }
                        case REPLACE -> {
                            boolean match = s.from().isEmpty() ? !Surface.air(id) : s.from().contains(id);
                            if (match) {
                                out.set(x, y, z, s.pattern().pick(x, y, z));
                            }
                        }
                        default -> {
                            if (y <= cy && Surface.soft(id)) {
                                out.set(x, y, z, s.pattern().pick(x, y, z));
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean exposed(WorldView w, int x, int y, int z) {
        return Surface.soft(w.blockId(x + 1, y, z)) || Surface.soft(w.blockId(x - 1, y, z)) || Surface.soft(w.blockId(x, y + 1, z))
            || Surface.soft(w.blockId(x, y - 1, z)) || Surface.soft(w.blockId(x, y, z + 1)) || Surface.soft(w.blockId(x, y, z - 1));
    }

    private static void surface(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int r = s.radius();
        Random random = new Random(s.seed());
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > r + 0.5) {
                    continue;
                }
                int top = Surface.top(world, x, z, Math.max(world.minY(), cy - r), Math.min(world.maxY() - 2, cy + r));
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                if (s.type() == BrushType.SCATTER) {
                    if (random.nextDouble() >= s.density()) {
                        continue;
                    }
                    for (int k = 1; k <= s.height() && top + k < world.maxY(); k++) {
                        if (Surface.air(world.blockId(x, top + k, z))) {
                            out.set(x, top + k, z, s.pattern().pick(x, top + k, z));
                        }
                    }
                } else if (s.replaceTop()) {
                    for (int k = 0; k < s.depth(); k++) {
                        out.set(x, top - k, z, s.pattern().pick(x, top - k, z));
                    }
                } else {
                    for (int k = 1; k <= s.depth() && top + k < world.maxY(); k++) {
                        if (Surface.soft(world.blockId(x, top + k, z))) {
                            out.set(x, top + k, z, s.pattern().pick(x, top + k, z));
                        }
                    }
                }
            }
        }
    }

    private static double falloff(double d, int r) {
        if (d >= r + 0.5) {
            return 0;
        }
        double t = 1 - (d / (r + 0.5)) * (d / (r + 0.5));
        return t * t;
    }

    private static void terrain(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int r = s.radius();
        int margin = 3;
        int size = (r + margin) * 2 + 1;
        int minX = cx - r - margin;
        int minZ = cz - r - margin;
        int scanBottom = Math.max(world.minY(), cy - r * 2);
        int scanTop = Math.min(world.maxY() - 2, cy + r * 2);
        Heightfield h = new Heightfield(minX, minZ, size, size);
        boolean[] has = new boolean[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int top = Surface.top(world, minX + i, minZ + j, scanBottom, scanTop);
                if (top != Integer.MIN_VALUE) {
                    h.set(i, j, top);
                    has[j * size + i] = true;
                } else {
                    h.set(i, j, cy);
                }
            }
        }
        Heightfield target = h.copy();
        PerlinNoise noise = new PerlinNoise(s.seed());
        double scale = s.scale() > 0 ? s.scale() : 12;
        switch (s.type()) {
            case SMOOTH -> {
                int passes = Math.max(1, (int) Math.round(1 + s.strength() * 4));
                Erosion.smooth(target, passes);
            }
            case MELT -> Erosion.thermal(target, 6, 1.0, 0.5);
            default -> {
            }
        }
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int x = minX + i;
                int z = minZ + j;
                double f = falloff(Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)), r);
                if (f <= 0 || !has[j * size + i]) {
                    continue;
                }
                double old = h.get(i, j);
                double want = switch (s.type()) {
                    case RAISE -> old + s.strength() * f;
                    case LOWER -> old - s.strength() * f;
                    case FLATTEN -> old + (cy - old) * Math.min(1, s.strength()) * f;
                    case NOISE -> old + noise.fbm(x / scale, z / scale, 3, 2.0, 0.5) * s.strength() * f;
                    case CRATER -> old + crater(Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) / (r + 0.5)) * s.strength();
                    case TERRACE -> f > 0.15 ? cy + Math.floor((old - cy) / Math.max(1, s.strength()) + 0.5) * Math.max(1, s.strength()) : old;
                    case SMOOTH -> old + (target.get(i, j) - old) * f;
                    default -> old + (target.get(i, j) - old) * f;
                };
                moveColumn(world, x, z, (int) old, (int) Math.round(want), out);
            }
        }
    }

    static double crater(double t) {
        double bowl = t < 0.75 ? -(1 - (t / 0.75) * (t / 0.75)) : 0;
        double rim = 0.35 * Math.exp(-Math.pow((t - 0.82) / 0.1, 2));
        return bowl + rim;
    }

    private static void spikes(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int r = s.radius();
        Random random = new Random(s.seed());
        int maxH = (int) Math.max(2, Math.round(s.strength()));
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > r + 0.5 || random.nextDouble() >= s.density()) {
                    continue;
                }
                int top = Surface.top(world, x, z, Math.max(world.minY(), cy - r), Math.min(world.maxY() - 2, cy + r));
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int h = Math.max(2, (int) Math.round(maxH * (0.5 + 0.5 * random.nextDouble()) * (0.5 + 0.5 * falloff(d, r))));
                double base = Math.max(0.6, h / 5.0);
                for (int k = 0; k < h && top + 1 + k < world.maxY(); k++) {
                    double rad = base * (1 - (double) k / h);
                    int ri = (int) Math.ceil(rad);
                    for (int dz = -ri; dz <= ri; dz++) {
                        for (int dx = -ri; dx <= ri; dx++) {
                            if (dx * dx + dz * dz > rad * rad + 0.3) {
                                continue;
                            }
                            int y = top + 1 + k;
                            if (k == 0 && Surface.soft(world.blockId(x + dx, y - 1, z + dz))) {
                                out.set(x + dx, y - 1, z + dz, s.pattern().pick(x + dx, y - 1, z + dz));
                            }
                            if (Surface.soft(world.blockId(x + dx, y, z + dz))) {
                                out.set(x + dx, y, z + dz, s.pattern().pick(x + dx, y, z + dz));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void forest(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int r = s.radius();
        Random random = new Random(s.seed());
        java.util.List<dev.syrkbuilder.core.tree.Trees.Type> types = treeTypes(s.variant());
        java.util.List<int[]> planted = new java.util.ArrayList<>();
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > (r + 0.5) * (r + 0.5) || random.nextDouble() >= s.density()) {
                    continue;
                }
                boolean crowded = false;
                for (int[] p : planted) {
                    if ((p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z) < 16) {
                        crowded = true;
                        break;
                    }
                }
                int top = Surface.top(world, x, z, Math.max(world.minY(), cy - r), Math.min(world.maxY() - 2, cy + r));
                if (crowded || top == Integer.MIN_VALUE) {
                    continue;
                }
                String ground = world.blockId(x, top, z);
                if (!(ground.endsWith("grass_block") || ground.endsWith("dirt") || ground.endsWith("podzol") || ground.endsWith("moss_block")
                    || ground.endsWith(":sand") || ground.endsWith("mud") || ground.endsWith("coarse_dirt") || ground.endsWith("rooted_dirt")
                    || ground.endsWith("snow_block"))) {
                    continue;
                }
                planted.add(new int[]{x, z});
                dev.syrkbuilder.core.tree.Trees.grow(types.get(random.nextInt(types.size())), world, x, top, z, 0, random.nextLong(), out);
            }
        }
    }

    public static java.util.List<dev.syrkbuilder.core.tree.Trees.Type> treeTypes(String raw) {
        java.util.List<dev.syrkbuilder.core.tree.Trees.Type> types = new java.util.ArrayList<>();
        String v = raw == null || raw.isBlank() ? "oak" : raw;
        if (v.equalsIgnoreCase("mix")) {
            return java.util.List.of(dev.syrkbuilder.core.tree.Trees.Type.OAK, dev.syrkbuilder.core.tree.Trees.Type.OAK,
                dev.syrkbuilder.core.tree.Trees.Type.BIRCH, dev.syrkbuilder.core.tree.Trees.Type.DARK_OAK);
        }
        for (String part : v.split(",")) {
            dev.syrkbuilder.core.tree.Trees.Type t = dev.syrkbuilder.core.tree.Trees.Type.byName(part.trim());
            if (t == null) {
                throw new IllegalArgumentException("Unknown tree '" + part + "'. Trees: " + String.join(", ", dev.syrkbuilder.core.tree.Trees.Type.NAMES) + ", mix");
            }
            types.add(t);
        }
        return types;
    }

    private static void moveColumn(WorldView world, int x, int z, int from, int to, EditBuffer out) {
        to = Math.max(world.minY(), Math.min(world.maxY() - 2, to));
        if (to == from) {
            return;
        }
        String top = world.blockState(x, from, z);
        String below = world.blockState(x, from - 1, z);
        String filler = Surface.soft(world.blockId(x, from - 1, z)) ? top : below;
        if (to > from) {
            for (int y = from; y < to; y++) {
                out.set(x, y, z, filler);
            }
            out.set(x, to, z, top);
        } else {
            for (int y = to + 1; y <= from; y++) {
                out.set(x, y, z, "minecraft:air");
            }
            if (Surface.soft(world.blockId(x, from + 1, z)) && !Surface.air(world.blockId(x, from + 1, z))) {
                out.set(x, from + 1, z, "minecraft:air");
            }
            out.set(x, to, z, top);
        }
    }
}
