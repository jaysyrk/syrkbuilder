package dev.syrkbuilder.core.brush;

import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.noise.PerlinNoise;
import java.util.List;

final class Rocks {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Rocks() {
    }

    static void apply(Brushes.Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        if (s.type() == BrushType.BOULDER) {
            boulder(s, world, cx, cy, cz, out);
        } else {
            cliff(s, world, cx, cy, cz, out);
        }
    }

    // A rock is a noisy ellipsoid sunk a little into the ground, so it reads as resting there rather than floating.
    private static void boulder(Brushes.Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        PerlinNoise noise = new PerlinNoise(s.seed());
        double scale = s.scale() > 0 ? s.scale() : Math.max(3, Math.min(s.rx(), s.rz()) * 0.8);
        int top = Surface.top(world, cx, cz, Math.max(world.minY(), cy - s.ry() * 2), Math.min(world.maxY() - 2, cy + s.ry() * 2));
        if (top == Integer.MIN_VALUE) {
            top = cy;
        }
        int centre = top + (int) Math.round(s.ry() * 0.3);
        int floor = top - Math.max(1, s.ry() / 3);
        for (int y = Math.max(world.minY(), centre - s.ry() - 1); y <= Math.min(world.maxY() - 1, centre + s.ry() + 1); y++) {
            if (y < floor) {
                continue;
            }
            for (int z = cz - s.rz() - 1; z <= cz + s.rz() + 1; z++) {
                for (int x = cx - s.rx() - 1; x <= cx + s.rx() + 1; x++) {
                    double bump = noise.fbm(x / scale, y / scale, z / scale, 3);
                    if (Brushes.reach(s, x - cx, y - centre, z - cz) + bump * s.strength() * 0.9 <= 1) {
                        out.set(x, y, z, s.pattern().pick(x, y, z));
                    }
                }
            }
        }
    }

    // Faces are recoloured in horizontal bands that wobble with the noise, and the foot of each band sometimes juts out as a ledge.
    private static void cliff(Brushes.Settings s, WorldView world, int cx, int cy, int cz, EditBuffer out) {
        List<String> layers = s.pattern().blocks();
        int band = Math.max(1, s.depth());
        PerlinNoise noise = new PerlinNoise(s.seed());
        for (int y = Math.max(world.minY(), cy - s.ry()); y <= Math.min(world.maxY() - 2, cy + s.ry()); y++) {
            for (int z = cz - s.rz(); z <= cz + s.rz(); z++) {
                for (int x = cx - s.rx(); x <= cx + s.rx(); x++) {
                    if (Brushes.reach(s, x - cx, y - cy, z - cz) > 1 || Surface.soft(world.blockId(x, y, z))) {
                        continue;
                    }
                    boolean face = false;
                    for (int[] side : SIDES) {
                        face |= Surface.soft(world.blockId(x + side[0], y, z + side[1]));
                    }
                    if (!face) {
                        continue;
                    }
                    int wobble = (int) Math.round(noise.noise(x * 0.11, z * 0.11) * band * 2);
                    int layer = Math.floorMod(Math.floorDiv(y + wobble, band), layers.size());
                    out.set(x, y, z, layers.get(layer));
                    if (Math.floorMod(y + wobble, band) != 0) {
                        continue;
                    }
                    double chance = s.strength() * (0.35 + 0.65 * (0.5 + 0.5 * noise.noise(x * 0.37, y * 0.37, z * 0.37)));
                    for (int[] side : SIDES) {
                        int nx = x + side[0];
                        int nz = z + side[1];
                        if (Surface.soft(world.blockId(nx, y, nz)) && Surface.soft(world.blockId(nx, y - 1, nz)) && rand(nx, y, nz, s.seed()) < chance) {
                            out.set(nx, y, nz, layers.get(layer));
                        }
                    }
                }
            }
        }
    }

    private static double rand(int x, int y, int z, long seed) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 0x9E3779B97F4A7C15L ^ seed;
        h = h * h * 42317861L + h * 11L;
        h ^= h >>> 29;
        return ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }
}
