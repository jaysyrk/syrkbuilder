package dev.syrkbuilder.core.command;

import dev.syrkbuilder.core.brush.Surface;
import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.CellStream;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.terrain.Erosion;
import dev.syrkbuilder.core.terrain.Heightfield;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

final class NatureOps {
    private static final Set<String> FLUIDS = Set.of("minecraft:water", "minecraft:lava", "minecraft:kelp", "minecraft:kelp_plant",
        "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:bubble_column");

    private NatureOps() {
    }

    static CellStream smooth(Box box, WorldView world, int passes) {
        int w = box.maxX() - box.minX() + 1;
        int d = box.maxZ() - box.minZ() + 1;
        int bottom = Math.max(box.minY(), world.minY());
        int ceiling = Math.min(box.maxY(), world.maxY() - 1);
        Heightfield h = new Heightfield(box.minX(), box.minZ(), w, d);
        int[] tops = new int[w * d];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int top = Surface.top(world, box.minX() + i, box.minZ() + j, bottom, ceiling);
                tops[j * w + i] = top;
                h.set(i, j, top == Integer.MIN_VALUE ? bottom : top);
            }
        }
        Erosion.smooth(h, passes);
        CellStream out = new CellStream(box);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int old = tops[j * w + i];
                if (old == Integer.MIN_VALUE) {
                    continue;
                }
                int x = box.minX() + i;
                int z = box.minZ() + j;
                int want = Math.max(bottom, Math.min(ceiling, (int) Math.round(h.get(i, j))));
                if (want == old) {
                    continue;
                }
                String surface = world.blockState(x, old, z);
                String under = old - 1 >= bottom && !Surface.soft(world.blockId(x, old - 1, z)) ? world.blockState(x, old - 1, z) : surface;
                if (want > old) {
                    out.add(x, old, z, under);
                    for (int y = old + 1; y < want; y++) {
                        out.add(x, y, z, under);
                    }
                    out.add(x, want, z, surface);
                } else {
                    for (int y = old + 1; y <= Math.min(ceiling, old + 2); y++) {
                        String above = world.blockId(x, y, z);
                        if (Surface.soft(above) && !Surface.air(above)) {
                            out.add(x, y, z, SelectionOps.AIR);
                        }
                    }
                    for (int y = old; y > want; y--) {
                        out.add(x, y, z, SelectionOps.AIR);
                    }
                    out.add(x, want, z, surface);
                }
            }
        }
        return out;
    }

    static CellStream drain(int[] c, int radius, WorldView world) {
        Box box = new Box(c[0] - radius, c[1] - radius, c[2] - radius, c[0] + radius, c[1] + radius, c[2] + radius);
        CellStream out = new CellStream(box);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int[] p = {c[0] + dx, c[1] + dy, c[2] + dz};
                    if (fluid(world, p) && seen.add(key(p))) {
                        queue.add(p);
                    }
                }
            }
        }
        long r2 = (long) radius * radius;
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            out.add(p[0], p[1], p[2], SelectionOps.AIR);
            for (int[] dir : dirs) {
                int[] n = {p[0] + dir[0], p[1] + dir[1], p[2] + dir[2]};
                long dx = n[0] - c[0];
                long dy = n[1] - c[1];
                long dz = n[2] - c[2];
                if (dx * dx + dy * dy + dz * dz > r2 || n[1] < world.minY() || n[1] >= world.maxY()) {
                    continue;
                }
                if (fluid(world, n) && seen.add(key(n))) {
                    queue.add(n);
                }
            }
        }
        return out;
    }

    private static boolean fluid(WorldView world, int[] p) {
        return p[1] >= world.minY() && p[1] < world.maxY() && FLUIDS.contains(Pattern.baseId(world.blockId(p[0], p[1], p[2])));
    }

    private static long key(int[] p) {
        return ((long) (p[0] & 0x3FFFFFF) << 38) | ((long) (p[2] & 0x3FFFFFF) << 12) | (p[1] & 0xFFF);
    }

    interface ColumnEdit {
        void column(int x, int z, int top, String topId, CellStream out);
    }

    static CellStream columns(int[] c, int radius, WorldView world, ColumnEdit edit) {
        Box box = new Box(c[0] - radius, c[1] - radius, c[2] - radius, c[0] + radius, c[1] + radius, c[2] + radius);
        CellStream out = new CellStream(box);
        int bottom = Math.max(world.minY(), c[1] - radius);
        int ceiling = Math.min(world.maxY() - 2, c[1] + radius);
        for (int x = c[0] - radius; x <= c[0] + radius; x++) {
            for (int z = c[2] - radius; z <= c[2] + radius; z++) {
                if ((long) (x - c[0]) * (x - c[0]) + (long) (z - c[2]) * (z - c[2]) > (long) radius * radius) {
                    continue;
                }
                for (int y = ceiling; y >= bottom; y--) {
                    String id = Pattern.baseId(world.blockId(x, y, z));
                    if (Surface.air(id)) {
                        continue;
                    }
                    edit.column(x, z, y, id, out);
                    break;
                }
            }
        }
        return out;
    }

    static CellStream snow(int[] c, int radius, WorldView world) {
        return columns(c, radius, world, (x, z, y, id, out) -> {
            if (id.equals("minecraft:water")) {
                out.add(x, y, z, "minecraft:ice");
            } else if (id.equals("minecraft:snow") || id.equals("minecraft:lava")) {
                return;
            } else if (Surface.soft(id)) {
                out.add(x, y, z, "minecraft:snow");
            } else {
                out.add(x, y + 1, z, "minecraft:snow");
                if (id.equals("minecraft:grass_block")) {
                    out.add(x, y, z, "minecraft:grass_block[snowy=true]");
                }
            }
        });
    }

    static CellStream thaw(int[] c, int radius, WorldView world) {
        return columns(c, radius, world, (x, z, y, id, out) -> {
            if (id.equals("minecraft:snow")) {
                out.add(x, y, z, SelectionOps.AIR);
                if (Pattern.baseId(world.blockId(x, y - 1, z)).equals("minecraft:grass_block")) {
                    out.add(x, y - 1, z, "minecraft:grass_block");
                }
            } else if (id.equals("minecraft:ice")) {
                out.add(x, y, z, "minecraft:water");
            }
        });
    }

    static CellStream green(int[] c, int radius, WorldView world) {
        return columns(c, radius, world, (x, z, y, id, out) -> {
            if (id.equals("minecraft:dirt") || id.equals("minecraft:coarse_dirt")) {
                out.add(x, y, z, "minecraft:grass_block");
            }
        });
    }
}
