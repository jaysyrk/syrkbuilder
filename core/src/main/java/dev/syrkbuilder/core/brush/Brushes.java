package dev.syrkbuilder.core.brush;

import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.noise.NoiseField;
import dev.syrkbuilder.core.noise.NoiseKind;
import dev.syrkbuilder.core.noise.NoisePreset;
import dev.syrkbuilder.core.noise.PerlinNoise;
import dev.syrkbuilder.core.terrain.Erosion;
import dev.syrkbuilder.core.terrain.Heightfield;
import dev.syrkbuilder.core.terrain.TerrainStyle;
import java.util.Random;
import java.util.Set;

public final class Brushes {
    public record Settings(BrushType type, int radius, int rx, int ry, int rz, Pattern pattern, double strength, double density, int depth,
                           int height, double scale, boolean replaceTop, Set<String> from, long seed, String variant,
                           dev.syrkbuilder.core.edit.Box frame, NoiseKind noise, int octaves, String preset, String style, boolean detail) {
    }

    private Brushes() {
    }

    // How far a cell is from the centre as a fraction of the brush's size on each axis: 1 is the edge.
    static double reach(Settings s, int dx, int dy, int dz) {
        double a = dx / (s.rx() + 0.5);
        double b = dy / (s.ry() + 0.5);
        double c = dz / (s.rz() + 0.5);
        return Math.sqrt(a * a + b * b + c * c);
    }

    public static EditBuffer apply(Settings s, WorldView world, int cx, int cy, int cz, long limit) {
        EditBuffer out = new EditBuffer(limit);
        if (s.pattern() != null) {
            s.pattern().bind(s.frame() != null ? s.frame()
                : new dev.syrkbuilder.core.edit.Box(cx - s.rx(), cy - s.ry(), cz - s.rz(), cx + s.rx(), cy + s.ry(), cz + s.rz()));
        }
        switch (s.type()) {
            case SPHERE, ERASE, PAINT, REPLACE, FILL -> ball(s, world, cx, cy, cz, out);
            case OVERLAY, SCATTER -> surface(s, world, cx, cy, cz, out);
            case SPIKES -> spikes(s, world, cx, cy, cz, out);
            case BOULDER, CLIFF -> Rocks.apply(s, world, cx, cy, cz, out);
            case TREES -> forest(s, world, cx, cy, cz, out);
            case BLOB, CARVE, SCULPT, INFLATE, DEFLATE, ROUGHEN, DECAY, SPLATTER -> VoxelBrushes.apply(s, world, cx, cy, cz, out);
            default -> terrain(s, world, cx, cy, cz, out);
        }
        return out;
    }

    private static void ball(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int minY = Math.max(world.minY(), cy - s.ry());
        int maxY = Math.min(world.maxY() - 1, cy + s.ry());
        for (int y = minY; y <= maxY; y++) {
            for (int z = cz - s.rz(); z <= cz + s.rz(); z++) {
                for (int x = cx - s.rx(); x <= cx + s.rx(); x++) {
                    if (reach(s, x - cx, y - cy, z - cz) > 1) {
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
                                out.set(x, y, z, paint(s.pattern(), world, x, y, z));
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

    // Dithering blends a gradient across the height of a wall, but a block whose open faces all point along the
    // gradient (the floor at the foot of that wall) sits at one point on it, where dithering only speckles.
    private static String paint(Pattern p, WorldView w, int x, int y, int z) {
        if (p.isGradient()) {
            for (int[] f : FACES) {
                if (Surface.soft(w.blockId(x + f[0], y + f[1], z + f[2])) && !p.along(f[0], f[1], f[2])) {
                    return p.pick(x, y, z);
                }
            }
            return p.pickClean(x, y, z);
        }
        return p.pick(x, y, z);
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static boolean exposed(WorldView w, int x, int y, int z) {
        return Surface.soft(w.blockId(x + 1, y, z)) || Surface.soft(w.blockId(x - 1, y, z)) || Surface.soft(w.blockId(x, y + 1, z))
            || Surface.soft(w.blockId(x, y - 1, z)) || Surface.soft(w.blockId(x, y, z + 1)) || Surface.soft(w.blockId(x, y, z - 1));
    }

    private static void surface(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        Random random = new Random(s.seed());
        for (int z = cz - s.rz(); z <= cz + s.rz(); z++) {
            for (int x = cx - s.rx(); x <= cx + s.rx(); x++) {
                if (reach(s, x - cx, 0, z - cz) > 1) {
                    continue;
                }
                int top = Surface.top(world, x, z, Math.max(world.minY(), cy - s.ry()), Math.min(world.maxY() - 2, cy + s.ry()));
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

    private static double falloff(double reach) {
        if (reach >= 1) {
            return 0;
        }
        double t = 1 - reach * reach;
        return t * t;
    }

    private static void terrain(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int margin = 3;
        int sizeX = (s.rx() + margin) * 2 + 1;
        int sizeZ = (s.rz() + margin) * 2 + 1;
        int minX = cx - s.rx() - margin;
        int minZ = cz - s.rz() - margin;
        int scanBottom = Math.max(world.minY(), cy - Math.max(s.ry() * 2, 64));
        int scanTop = Math.min(world.maxY() - 2, cy + Math.max(s.ry() * 2, 128));
        Heightfield h = new Heightfield(minX, minZ, sizeX, sizeZ);
        boolean[] has = new boolean[sizeX * sizeZ];
        for (int j = 0; j < sizeZ; j++) {
            for (int i = 0; i < sizeX; i++) {
                int top = Surface.top(world, minX + i, minZ + j, scanBottom, scanTop);
                if (top != Integer.MIN_VALUE) {
                    h.set(i, j, top);
                    has[j * sizeX + i] = true;
                } else {
                    h.set(i, j, cy);
                }
            }
        }
        Heightfield target = h.copy();
        PerlinNoise noise = new PerlinNoise(s.seed());
        NoiseField field = new NoiseField(s.seed());
        NoisePreset preset = s.preset() == null ? null : NoisePreset.byName(s.preset());
        NoiseKind kind = s.noise() != null ? s.noise() : preset != null ? preset.kind : NoiseKind.SIMPLEX;
        int octaves = s.octaves() > 0 ? s.octaves() : preset != null ? preset.octaves : 4;
        int steps = preset != null ? preset.steps : 0;
        double lift = preset != null ? preset.lift : 0;
        double scale = s.scale() > 0 ? s.scale() : s.type() == BrushType.TERRAGEN ? preset != null ? preset.scale : 20 : 12;
        switch (s.type()) {
            case SMOOTH -> {
                int passes = Math.max(1, (int) Math.round(1 + s.strength() * 4));
                Erosion.smooth(target, passes);
            }
            case MELT -> Erosion.thermal(target, 6, 1.0, 0.5);
            case ERODE -> {
                Erosion.hydraulic(target, (int) (sizeX * (long) sizeZ * (0.1 + s.strength() * 1.4)), s.seed());
                Erosion.thermal(target, 3, 1.2, 0.4);
            }
            default -> {
            }
        }
        Heightfield result = h.copy();
        boolean[] moved = new boolean[sizeX * sizeZ];
        for (int j = 0; j < sizeZ; j++) {
            for (int i = 0; i < sizeX; i++) {
                int x = minX + i;
                int z = minZ + j;
                double flat = reach(s, x - cx, 0, z - cz);
                double f = falloff(flat);
                if (f <= 0 || !has[j * sizeX + i]) {
                    continue;
                }
                double old = h.get(i, j);
                double want = switch (s.type()) {
                    case RAISE -> old + s.strength() * f;
                    case LOWER -> old - s.strength() * f;
                    case FLATTEN -> old + (cy - old) * Math.min(1, s.strength()) * f;
                    case NOISE -> old + noise.fbm(x / scale, z / scale, 3, 2.0, 0.5) * s.strength() * f;
                    case TERRAGEN -> old + (cy + generated(s, field, kind, octaves, steps, lift, scale, x, z) - old) * f;
                    case CRATER -> old + crater(flat) * s.strength();
                    case TERRACE -> f > 0.15 ? cy + Math.floor((old - cy) / Math.max(1, s.strength()) + 0.5) * Math.max(1, s.strength()) : old;
                    case SMOOTH -> old + (target.get(i, j) - old) * f;
                    default -> old + (target.get(i, j) - old) * f;
                };
                int to = moveColumn(world, x, z, (int) old, (int) Math.round(want), out);
                result.set(i, j, to);
                moved[j * sizeX + i] = true;
            }
        }
        if (s.type() == BrushType.TERRAGEN) {
            String styleName = s.style() != null ? s.style() : preset != null ? preset.style : null;
            if (styleName != null) {
                restyle(TerrainStyle.byName(styleName), field, result, moved, out);
            }
        }
    }

    private static double generated(Settings s, NoiseField field, NoiseKind kind, int octaves, int steps, double lift, double scale, int x, int z) {
        double n = field.sample(kind, x / scale, z / scale, octaves);
        if (s.detail()) {
            n += 0.3 * field.sample(NoiseKind.FRACTAL, x / (scale * 0.25), z / (scale * 0.25), 2);
        }
        double rel = (n + lift) * s.strength();
        if (steps > 0) {
            double step = Math.max(1, s.strength() / steps);
            rel = Math.floor(rel / step + 0.5) * step;
        }
        return rel;
    }

    // Terrain grown by a brush gets the same surface layers as /sb terrain: rock where it is steep, snow up high, grass in the flats.
    private static void restyle(TerrainStyle style, NoiseField field, Heightfield result, boolean[] moved, EditBuffer out) {
        if (style == null) {
            return;
        }
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < result.depth; j++) {
            for (int i = 0; i < result.width; i++) {
                if (moved[j * result.width + i]) {
                    low = Math.min(low, result.get(i, j));
                    high = Math.max(high, result.get(i, j));
                }
            }
        }
        TerrainStyle.Column column = new TerrainStyle.Column();
        for (int j = 0; j < result.depth; j++) {
            for (int i = 0; i < result.width; i++) {
                if (!moved[j * result.width + i]) {
                    continue;
                }
                column.x = result.minX + i;
                column.z = result.minZ + j;
                column.top = (int) result.get(i, j);
                column.slope = result.slope(i, j);
                column.heightFrac = high > low ? (result.get(i, j) - low) / (high - low) : 0.5;
                column.jitter = field.sample(NoiseKind.FRACTAL, column.x / 12.0, column.z / 12.0, 2);
                for (int depth = 0; depth < 4; depth++) {
                    out.set(column.x, column.top - depth, column.z, style.block(column, column.top - depth, depth));
                }
            }
        }
    }

    static double crater(double t) {
        double bowl = t < 0.75 ? -(1 - (t / 0.75) * (t / 0.75)) : 0;
        double rim = 0.35 * Math.exp(-Math.pow((t - 0.82) / 0.1, 2));
        return bowl + rim;
    }

    private static void spikes(Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        Random random = new Random(s.seed());
        int maxH = (int) Math.max(2, Math.round(s.strength()));
        for (int z = cz - s.rz(); z <= cz + s.rz(); z++) {
            for (int x = cx - s.rx(); x <= cx + s.rx(); x++) {
                double flat = reach(s, x - cx, 0, z - cz);
                if (flat > 1 || random.nextDouble() >= s.density()) {
                    continue;
                }
                int top = Surface.top(world, x, z, Math.max(world.minY(), cy - s.ry()), Math.min(world.maxY() - 2, cy + s.ry()));
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int h = Math.max(2, (int) Math.round(maxH * (0.5 + 0.5 * random.nextDouble()) * (0.5 + 0.5 * falloff(flat))));
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
        Random random = new Random(s.seed());
        java.util.List<dev.syrkbuilder.core.tree.Trees.Type> types = treeTypes(s.variant());
        java.util.List<int[]> planted = new java.util.ArrayList<>();
        for (int z = cz - s.rz(); z <= cz + s.rz(); z++) {
            for (int x = cx - s.rx(); x <= cx + s.rx(); x++) {
                if (reach(s, x - cx, 0, z - cz) > 1 || random.nextDouble() >= s.density()) {
                    continue;
                }
                boolean crowded = false;
                for (int[] p : planted) {
                    if ((p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z) < 16) {
                        crowded = true;
                        break;
                    }
                }
                int top = Surface.top(world, x, z, Math.max(world.minY(), cy - s.ry()), Math.min(world.maxY() - 2, cy + s.ry()));
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

    private static int moveColumn(WorldView world, int x, int z, int from, int to, EditBuffer out) {
        to = Math.max(world.minY(), Math.min(world.maxY() - 2, to));
        if (to == from) {
            return to;
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
        return to;
    }
}
