package dev.syrkbuilder.core.shape;

import dev.syrkbuilder.core.brush.Surface;
import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import java.util.ArrayDeque;
import java.util.Locale;

public final class FloodFill {
    public enum Mode {
        HOLE("hole", "fill holes, pools and pits to the brim, like pouring water"),
        CONNECTED("connected", "recolour everything connected that matches the block you aim at"),
        ROOM("room", "fill an enclosed space in every direction (rooms, caves)");

        public final String id;
        public final String description;

        Mode(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public static Mode byName(String name) {
            for (Mode m : values()) {
                if (m.id.equals(name.toLowerCase(Locale.ROOT))) {
                    return m;
                }
            }
            return null;
        }
    }

    private FloodFill() {
    }

    private static boolean fillable(String id) {
        return Surface.soft(id) || id.equals("minecraft:water") || id.equals("minecraft:lava");
    }

    public static boolean fill(Mode mode, WorldView world, int tx, int ty, int tz, int radius, Pattern pattern, EditBuffer out) {
        int sx = tx;
        int sy = mode == Mode.CONNECTED ? ty : ty + 1;
        int sz = tz;
        String match = mode == Mode.CONNECTED ? world.blockId(tx, ty, tz) : null;
        if (mode != Mode.CONNECTED && !fillable(world.blockId(sx, sy, sz))) {
            throw new IllegalArgumentException("Nothing to fill above that block - aim at the floor of the hole or room.");
        }
        Flood result;
        if (mode == Mode.HOLE) {
            result = flood(world, sx, sy, sz, radius, sy, null);
            for (int level = sy + 1; level <= Math.min(sy + radius, world.maxY() - 1) && !result.edge; level++) {
                Flood higher = flood(world, sx, sy, sz, radius, level, null);
                if (higher.edge || higher.cells.size() == result.cells.size()) {
                    break;
                }
                result = higher;
            }
        } else {
            result = flood(world, sx, sy, sz, radius, Integer.MAX_VALUE, match);
        }
        for (long key : result.cells) {
            int x = dev.syrkbuilder.core.history.ChangeSet.unpackX(key);
            int y = dev.syrkbuilder.core.history.ChangeSet.unpackY(key);
            int z = dev.syrkbuilder.core.history.ChangeSet.unpackZ(key);
            out.set(x, y, z, pattern.pick(x, y, z));
        }
        return result.edge;
    }

    private record Flood(java.util.LinkedHashSet<Long> cells, boolean edge) {
    }

    private static Flood flood(WorldView world, int sx, int sy, int sz, int radius, int maxY, String match) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}, {0, 1, 0}};
        java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy, sz});
        seen.add(dev.syrkbuilder.core.history.ChangeSet.pack(sx, sy, sz));
        long r2 = (long) radius * radius;
        boolean edge = false;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int x = p[0] + d[0];
                int y = p[1] + d[1];
                int z = p[2] + d[2];
                if (y < world.minY() || y >= world.maxY() || y > maxY) {
                    continue;
                }
                long key = dev.syrkbuilder.core.history.ChangeSet.pack(x, y, z);
                if (seen.contains(key)) {
                    continue;
                }
                String id = world.blockId(x, y, z);
                if (match != null ? !id.equals(match) : !fillable(id)) {
                    continue;
                }
                long dx = x - sx;
                long dy = y - sy;
                long dz = z - sz;
                if (dx * dx + dy * dy + dz * dz > r2) {
                    edge = true;
                    continue;
                }
                seen.add(key);
                queue.add(new int[]{x, y, z});
            }
        }
        return new Flood(seen, edge);
    }
}
