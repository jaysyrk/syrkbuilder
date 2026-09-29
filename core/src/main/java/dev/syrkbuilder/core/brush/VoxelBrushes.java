package dev.syrkbuilder.core.brush;

import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.noise.PerlinNoise;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

final class VoxelBrushes {
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private VoxelBrushes() {
    }

    private static final class Grid {
        final int ox;
        final int oy;
        final int oz;
        final int n;
        final String[] state;
        final boolean[] solid;
        final int minY;
        final int maxY;

        Grid(WorldView world, int cx, int cy, int cz, int half) {
            n = half * 2 + 1;
            ox = cx - half;
            oy = cy - half;
            oz = cz - half;
            minY = world.minY();
            maxY = world.maxY() - 1;
            state = new String[n * n * n];
            solid = new boolean[n * n * n];
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    for (int x = 0; x < n; x++) {
                        int wy = oy + y;
                        int i = index(x, y, z);
                        if (wy < minY || wy > maxY) {
                            state[i] = "minecraft:air";
                            continue;
                        }
                        String id = world.blockId(ox + x, wy, oz + z);
                        solid[i] = solid(id);
                        state[i] = solid[i] ? world.blockState(ox + x, wy, oz + z) : "minecraft:air";
                    }
                }
            }
        }

        int index(int x, int y, int z) {
            return (y * n + z) * n + x;
        }

        boolean in(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < n && y < n && z < n;
        }

        boolean solidAt(int x, int y, int z) {
            return in(x, y, z) && solid[index(x, y, z)];
        }

        boolean writable(int y) {
            int wy = oy + y;
            return wy >= minY && wy <= maxY;
        }

        boolean exposed(boolean[] s, int x, int y, int z) {
            for (int[] f : FACES) {
                int a = x + f[0];
                int b = y + f[1];
                int c = z + f[2];
                if (in(a, b, c) && !s[index(a, b, c)]) {
                    return true;
                }
            }
            return false;
        }

        boolean touching(boolean[] s, int x, int y, int z) {
            for (int[] f : FACES) {
                int a = x + f[0];
                int b = y + f[1];
                int c = z + f[2];
                if (in(a, b, c) && s[index(a, b, c)]) {
                    return true;
                }
            }
            return false;
        }

        String material(boolean[] s, String[] st, int x, int y, int z, boolean facesOnly) {
            Map<String, Integer> count = new HashMap<>();
            String best = null;
            int bestCount = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0 || facesOnly && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                            continue;
                        }
                        int a = x + dx;
                        int b = y + dy;
                        int c = z + dz;
                        if (!in(a, b, c) || !s[index(a, b, c)]) {
                            continue;
                        }
                        int weight = dy == 0 ? 2 : 1;
                        int k = count.merge(st[index(a, b, c)], weight, Integer::sum);
                        if (k > bestCount) {
                            bestCount = k;
                            best = st[index(a, b, c)];
                        }
                    }
                }
            }
            return best;
        }
    }

    static boolean solid(String id) {
        return !Surface.soft(id) && !id.equals("minecraft:water") && !id.equals("minecraft:lava") && !id.equals("minecraft:bubble_column");
    }

    private static double inBall(int dx, int dy, int dz, int rx, int ry, int rz) {
        double a = dx / (rx + 0.5);
        double b = dy / (ry + 0.5);
        double c = dz / (rz + 0.5);
        double d = Math.sqrt(a * a + b * b + c * c);
        return d <= 1 ? 1 - d : -1;
    }

    static void apply(Brushes.Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        int r = s.radius();
        boolean lumpy = s.type() == BrushType.BLOB || s.type() == BrushType.CARVE;
        double grow = lumpy ? 1 + 0.6 * s.strength() : 1;
        int gx = (int) Math.ceil(s.rx() * grow);
        int gy = (int) Math.ceil(s.ry() * grow);
        int gz = (int) Math.ceil(s.rz() * grow);
        int half = Math.max(gx, Math.max(gy, gz)) + 2;
        Grid g = new Grid(world, cx, cy, cz, half);
        boolean[] before = g.solid.clone();
        String[] beforeState = g.state.clone();
        Pattern pattern = s.pattern();
        PerlinNoise noise = new PerlinNoise(s.seed());
        Random random = new Random(s.seed());
        double scale = s.scale() > 0 ? s.scale() : switch (s.type()) {
            case ROUGHEN -> 4;
            case SPLATTER -> 5;
            default -> Math.max(3, r * 0.7);
        };
        switch (s.type()) {
            case SCULPT -> {
                int passes = 1 + (int) Math.round(s.strength() * 3);
                for (int p = 0; p < passes; p++) {
                    sculptPass(g, s, half, pattern);
                }
            }
            case INFLATE, DEFLATE, DECAY -> {
                boolean[] snap = g.solid.clone();
                forBall(g, s.rx(), s.ry(), s.rz(), half, (x, y, z, f) -> {
                    int i = g.index(x, y, z);
                    if (s.type() == BrushType.INFLATE) {
                        if (!snap[i] && g.touching(snap, x, y, z)) {
                            String m = pattern != null ? pattern.pick(g.ox + x, g.oy + y, g.oz + z) : g.material(snap, beforeState, x, y, z, true);
                            fill(g, i, m);
                        }
                    } else if (snap[i] && g.exposed(snap, x, y, z)) {
                        boolean go = s.type() == BrushType.DEFLATE || random.nextDouble() < s.density() * (0.4 + 0.6 * f);
                        if (go) {
                            clear(g, i);
                        }
                    }
                });
            }
            case ROUGHEN -> {
                boolean[] snap = g.solid.clone();
                forBall(g, s.rx(), s.ry(), s.rz(), half, (x, y, z, f) -> {
                    int i = g.index(x, y, z);
                    boolean surfaceSolid = snap[i] && g.exposed(snap, x, y, z);
                    boolean surfaceAir = !snap[i] && g.touching(snap, x, y, z);
                    if (!surfaceSolid && !surfaceAir) {
                        return;
                    }
                    double n = noise.fbm((g.ox + x) / scale, (g.oy + y) / scale, (g.oz + z) / scale, 3);
                    double threshold = (1 - s.strength()) * 0.5 + (1 - f) * 0.3;
                    if (surfaceAir && n > threshold) {
                        String m = pattern != null ? pattern.pick(g.ox + x, g.oy + y, g.oz + z) : g.material(snap, beforeState, x, y, z, false);
                        fill(g, i, m);
                    } else if (surfaceSolid && n < -threshold) {
                        clear(g, i);
                    }
                });
            }
            case SPLATTER -> forBall(g, s.rx(), s.ry(), s.rz(), half, (x, y, z, f) -> {
                int i = g.index(x, y, z);
                if (before[i] && g.exposed(before, x, y, z)) {
                    double n = noise.fbm((g.ox + x) / scale, (g.oy + y) / scale, (g.oz + z) / scale, 3);
                    if (n > 0.5 - s.density() - (f - 0.5) * 0.2) {
                        g.state[i] = pattern.pick(g.ox + x, g.oy + y, g.oz + z);
                    }
                }
            });
            case BLOB, CARVE -> forBall(g, gx, gy, gz, half, (x, y, z, f) -> {
                double d = Brushes.reach(s, x - half, y - half, z - half);
                double n = noise.fbm((g.ox + x) / scale, (g.oy + y) / scale, (g.oz + z) / scale, 3);
                double edge = 1 + 0.6 * s.strength() * n;
                if (d > edge) {
                    return;
                }
                int i = g.index(x, y, z);
                if (s.type() == BrushType.BLOB) {
                    fill(g, i, pattern.pick(g.ox + x, g.oy + y, g.oz + z));
                } else if (g.solid[i]) {
                    clear(g, i);
                }
            });
            default -> throw new IllegalArgumentException(s.type().id());
        }
        write(g, before, beforeState, world, out);
    }

    private interface CellVisitor {
        void visit(int x, int y, int z, double f);
    }

    private static void forBall(Grid g, int rx, int ry, int rz, int half, CellVisitor v) {
        for (int y = half - ry; y <= half + ry; y++) {
            if (!g.writable(y)) {
                continue;
            }
            for (int z = half - rz; z <= half + rz; z++) {
                for (int x = half - rx; x <= half + rx; x++) {
                    double f = inBall(x - half, y - half, z - half, rx, ry, rz);
                    if (f >= 0) {
                        v.visit(x, y, z, f);
                    }
                }
            }
        }
    }

    private static void fill(Grid g, int i, String state) {
        if (state != null) {
            g.solid[i] = true;
            g.state[i] = state;
        }
    }

    private static void clear(Grid g, int i) {
        g.solid[i] = false;
        g.state[i] = "minecraft:air";
    }

    private static void sculptPass(Grid g, Brushes.Settings s, int half, Pattern pattern) {
        boolean[] snap = g.solid.clone();
        String[] states = g.state.clone();
        forBall(g, s.rx(), s.ry(), s.rz(), half, (x, y, z, f) -> {
            int count = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int a = x + dx;
                        int b = y + dy;
                        int c = z + dz;
                        if (g.in(a, b, c) ? snap[g.index(a, b, c)] : snap[g.index(x, y, z)]) {
                            count++;
                        }
                    }
                }
            }
            int i = g.index(x, y, z);
            if (snap[i] && count < 13) {
                clear(g, i);
            } else if (!snap[i] && count > 14) {
                fill(g, i, pattern != null ? pattern.pick(g.ox + x, g.oy + y, g.oz + z) : g.material(snap, states, x, y, z, false));
            }
        });
    }

    private static void write(Grid g, boolean[] before, String[] beforeState, WorldView world, EditBuffer out) {
        for (int y = 0; y < g.n; y++) {
            if (!g.writable(y)) {
                continue;
            }
            for (int z = 0; z < g.n; z++) {
                for (int x = 0; x < g.n; x++) {
                    int i = g.index(x, y, z);
                    if (g.solid[i] == before[i] && g.state[i].equals(beforeState[i])) {
                        continue;
                    }
                    int wx = g.ox + x;
                    int wy = g.oy + y;
                    int wz = g.oz + z;
                    if (g.solid[i] || !Surface.air(world.blockId(wx, wy, wz))) {
                        out.set(wx, wy, wz, g.state[i]);
                    }
                    if (before[i] && !g.solid[i] && wy + 1 < world.maxY() && !(y + 1 < g.n && g.solid[g.index(x, y + 1, z)])) {
                        String above = world.blockId(wx, wy + 1, wz);
                        if (Surface.soft(above) && !Surface.air(above)) {
                            out.set(wx, wy + 1, wz, "minecraft:air");
                        }
                    }
                }
            }
        }
    }
}
