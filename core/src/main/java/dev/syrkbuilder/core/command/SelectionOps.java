package dev.syrkbuilder.core.command;

import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.CellStream;
import dev.syrkbuilder.core.edit.ChainStream;
import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.grid.BlockGrid;
import dev.syrkbuilder.core.grid.PasteStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SelectionOps {
    static final String AIR = "minecraft:air";
    private static final Set<String> NATURAL = Set.of("minecraft:stone", "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
        "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt", "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
        "minecraft:tuff", "minecraft:gravel", "minecraft:dirt_path");

    private SelectionOps() {
    }

    static boolean isAir(String id) {
        String base = Pattern.baseId(id);
        return base.equals(AIR) || base.equals("minecraft:cave_air") || base.equals("minecraft:void_air");
    }

    static BlockGrid read(Box box, WorldView world) {
        BlockGrid grid = new BlockGrid(box.maxX() - box.minX() + 1, box.maxY() - box.minY() + 1, box.maxZ() - box.minZ() + 1);
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    grid.set(x - box.minX(), y - box.minY(), z - box.minZ(), world.blockState(x, y, z));
                }
            }
        }
        return grid;
    }

    static Box shift(Box box, int dx, int dy, int dz) {
        return new Box(box.minX() + dx, box.minY() + dy, box.minZ() + dz, box.maxX() + dx, box.maxY() + dy, box.maxZ() + dz);
    }

    static EditStream move(Box box, BlockGrid grid, int dx, int dy, int dz, boolean skipAir, Pattern leave) {
        Box dest = shift(box, dx, dy, dz);
        CellStream clear = new CellStream(box);
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (dest.contains(x, y, z)) {
                        continue;
                    }
                    String here = grid.get(x - box.minX(), y - box.minY(), z - box.minZ());
                    if (skipAir && here != null && isAir(here)) {
                        continue;
                    }
                    clear.add(x, y, z, leave == null ? AIR : leave.pick(x, y, z));
                }
            }
        }
        return new ChainStream(List.of(clear, new PasteStream(grid, dest.minX(), dest.minY(), dest.minZ(), skipAir, Map.of())));
    }

    static EditStream stack(Box box, BlockGrid grid, int[] unit, int count, boolean skipAir) {
        int sx = (box.maxX() - box.minX() + 1) * unit[0];
        int sy = (box.maxY() - box.minY() + 1) * unit[1];
        int sz = (box.maxZ() - box.minZ() + 1) * unit[2];
        List<EditStream> parts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            parts.add(new PasteStream(grid, box.minX() + sx * i, box.minY() + sy * i, box.minZ() + sz * i, skipAir, Map.of()));
        }
        return new ChainStream(parts);
    }

    static Box stackBounds(Box box, int[] unit, int count) {
        Box last = shift(box, (box.maxX() - box.minX() + 1) * unit[0] * count, (box.maxY() - box.minY() + 1) * unit[1] * count,
            (box.maxZ() - box.minZ() + 1) * unit[2] * count);
        return Box.of(Math.min(box.minX(), last.minX()), Math.min(box.minY(), last.minY()), Math.min(box.minZ(), last.minZ()),
            Math.max(box.maxX(), last.maxX()), Math.max(box.maxY(), last.maxY()), Math.max(box.maxZ(), last.maxZ()));
    }

    static CellStream hollow(Box box, WorldView world, int thickness, Pattern fill) {
        int w = box.maxX() - box.minX() + 1;
        int h = box.maxY() - box.minY() + 1;
        int d = box.maxZ() - box.minZ() + 1;
        int[] dist = new int[w * h * d];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    int i = x + w * (z + d * y);
                    boolean solid = !isAir(world.blockId(box.minX() + x, box.minY() + y, box.minZ() + z));
                    if (!solid) {
                        dist[i] = 0;
                    } else if (x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == d - 1) {
                        dist[i] = 1;
                        queue.add(i);
                    } else {
                        dist[i] = Integer.MAX_VALUE;
                    }
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    int i = x + w * (z + d * y);
                    if (dist[i] == Integer.MAX_VALUE && touchesAir(dist, x, y, z, w, h, d)) {
                        dist[i] = 1;
                        queue.add(i);
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int z = (i / w) % d;
            int y = i / (w * d);
            for (int[] dir : dirs) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= w || ny >= h || nz >= d) {
                    continue;
                }
                int n = nx + w * (nz + d * ny);
                if (dist[n] > dist[i] + 1) {
                    dist[n] = dist[i] + 1;
                    queue.add(n);
                }
            }
        }
        CellStream out = new CellStream(box);
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    if (dist[x + w * (z + d * y)] > thickness) {
                        int wx = box.minX() + x;
                        int wy = box.minY() + y;
                        int wz = box.minZ() + z;
                        out.add(wx, wy, wz, fill == null ? AIR : fill.pick(wx, wy, wz));
                    }
                }
            }
        }
        return out;
    }

    private static boolean touchesAir(int[] dist, int x, int y, int z, int w, int h, int d) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];
            if (nx >= 0 && ny >= 0 && nz >= 0 && nx < w && ny < h && nz < d && dist[nx + w * (nz + d * ny)] == 0) {
                return true;
            }
        }
        return false;
    }

    static CellStream overlay(Box box, WorldView world, Pattern pattern, int depth) {
        pattern.bind(new Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + depth, box.maxZ()));
        CellStream out = new CellStream(box);
        int top = Math.min(box.maxY() + 1, world.maxY() - 1);
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = Math.min(box.maxY(), world.maxY() - 1); y >= Math.max(box.minY(), world.minY()); y--) {
                    if (isAir(world.blockId(x, y, z))) {
                        continue;
                    }
                    for (int k = 1; k <= depth && y + k <= top; k++) {
                        out.add(x, y + k, z, pattern.pick(x, y + k, z));
                    }
                    break;
                }
            }
        }
        return out;
    }

    static CellStream naturalize(Box box, WorldView world) {
        CellStream out = new CellStream(box);
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                int depth = 0;
                for (int y = Math.min(box.maxY(), world.maxY() - 1); y >= Math.max(box.minY(), world.minY()); y--) {
                    String id = Pattern.baseId(world.blockId(x, y, z));
                    if (isAir(id)) {
                        depth = 0;
                        continue;
                    }
                    if (!NATURAL.contains(id)) {
                        depth++;
                        continue;
                    }
                    String want = depth == 0 ? "minecraft:grass_block" : depth <= 3 ? "minecraft:dirt" : "minecraft:stone";
                    if (!want.equals(id)) {
                        out.add(x, y, z, want);
                    }
                    depth++;
                }
            }
        }
        return out;
    }

    static Map<String, Long> distribution(Box box, WorldView world) {
        Map<String, Long> counts = new HashMap<>();
        for (int y = Math.max(box.minY(), world.minY()); y <= Math.min(box.maxY(), world.maxY() - 1); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    counts.merge(Pattern.baseId(world.blockId(x, y, z)), 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    record Region(Box box, int count, boolean capped) {
    }

    static Region connected(int[] start, WorldView world, boolean anySolid, boolean diagonal, int limit) {
        String startId = Pattern.baseId(world.blockId(start[0], start[1], start[2]));
        if (isAir(startId)) {
            return null;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(pack(start[0], start[1], start[2]));
        int minX = start[0], minY = start[1], minZ = start[2], maxX = start[0], maxY = start[1], maxZ = start[2];
        boolean capped = false;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int steps = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (steps == 0 || !diagonal && steps > 1) {
                            continue;
                        }
                        int nx = p[0] + dx;
                        int ny = p[1] + dy;
                        int nz = p[2] + dz;
                        if (ny < world.minY() || ny >= world.maxY()) {
                            continue;
                        }
                        long key = pack(nx, ny, nz);
                        if (seen.contains(key)) {
                            continue;
                        }
                        String id = Pattern.baseId(world.blockId(nx, ny, nz));
                        if (anySolid ? isAir(id) : !id.equals(startId)) {
                            continue;
                        }
                        if (seen.size() >= limit) {
                            capped = true;
                            continue;
                        }
                        seen.add(key);
                        queue.add(new int[]{nx, ny, nz});
                    }
                }
            }
        }
        return new Region(new Box(minX, minY, minZ, maxX, maxY, maxZ), seen.size(), capped);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static Box resize(Box box, int[] unit, int n) {
        int minX = box.minX(), minY = box.minY(), minZ = box.minZ(), maxX = box.maxX(), maxY = box.maxY(), maxZ = box.maxZ();
        if (unit[0] > 0) {
            maxX += n;
        } else if (unit[0] < 0) {
            minX -= n;
        }
        if (unit[1] > 0) {
            maxY += n;
        } else if (unit[1] < 0) {
            minY -= n;
        }
        if (unit[2] > 0) {
            maxZ += n;
        } else if (unit[2] < 0) {
            minZ -= n;
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("That would shrink the selection to nothing.");
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
