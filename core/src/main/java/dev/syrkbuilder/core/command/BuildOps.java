package dev.syrkbuilder.core.command;

import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.CellStream;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.shape.PixelFont;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class BuildOps {
    private BuildOps() {
    }

    private static CellStream bound(List<int[]> cells, Pattern pattern) {
        if (cells.isEmpty()) {
            return new CellStream(new Box(0, 0, 0, 0, 0, 0));
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
            minZ = Math.min(minZ, c[2]);
            maxX = Math.max(maxX, c[0]);
            maxY = Math.max(maxY, c[1]);
            maxZ = Math.max(maxZ, c[2]);
        }
        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        pattern.bind(box);
        CellStream out = new CellStream(box);
        for (int[] c : cells) {
            out.add(c[0], c[1], c[2], pattern.pick(c[0], c[1], c[2]));
        }
        return out;
    }

    static CellStream text(int[] target, float yaw, String text, Pattern pattern, int size, int depth, boolean flat) {
        int[] right = Directions.unit("right", yaw, 0);
        int[] forward = Directions.unit("forward", yaw, 0);
        int width = PixelFont.render(text, (c, r) -> {
        });
        int startShift = -(width * size) / 2;
        List<int[]> cells = new ArrayList<>();
        PixelFont.render(text, (col, row) -> {
            for (int sx = 0; sx < size; sx++) {
                for (int sy = 0; sy < size; sy++) {
                    for (int dz = 0; dz < depth; dz++) {
                        int along = startShift + col * size + sx;
                        int up = row * size + sy;
                        int x = target[0] + right[0] * along;
                        int z = target[2] + right[2] * along;
                        int y;
                        if (flat) {
                            x += forward[0] * up;
                            z += forward[2] * up;
                            y = target[1] + 1 + dz;
                        } else {
                            x += forward[0] * dz;
                            z += forward[2] * dz;
                            y = target[1] + 1 + up;
                        }
                        cells.add(new int[]{x, y, z});
                    }
                }
            }
        });
        return bound(cells, pattern);
    }

    static CellStream arch(int[] target, float yaw, Pattern pattern, int width, int height, int thickness, int depth) {
        int[] right = Directions.unit("right", yaw, 0);
        int[] forward = Directions.unit("forward", yaw, 0);
        double a = width / 2.0;
        double b = height;
        double ia = Math.max(0.01, a - thickness);
        double ib = Math.max(0.01, b - thickness);
        List<int[]> cells = new ArrayList<>();
        int half = (int) Math.ceil(a);
        for (int u = -half; u <= half; u++) {
            for (int v = 0; v <= height; v++) {
                double du = u;
                double dv = v;
                boolean inOuter = (du * du) / (a * a) + (dv * dv) / (b * b) <= 1.0;
                boolean inInner = (du * du) / (ia * ia) + (dv * dv) / (ib * ib) < 1.0;
                if (!inOuter || inInner) {
                    continue;
                }
                for (int w = 0; w < depth; w++) {
                    int along = w - depth / 2;
                    cells.add(new int[]{target[0] + right[0] * u + forward[0] * along, target[1] + 1 + v, target[2] + right[2] * u + forward[2] * along});
                }
            }
        }
        return bound(cells, pattern);
    }

    static CellStream replaceNear(int[] c, int radius, Set<String> from, Pattern to, WorldView world) {
        List<int[]> cells = new ArrayList<>();
        long r2 = (long) radius * radius;
        for (int x = c[0] - radius; x <= c[0] + radius; x++) {
            for (int y = Math.max(world.minY(), c[1] - radius); y <= Math.min(world.maxY() - 1, c[1] + radius); y++) {
                for (int z = c[2] - radius; z <= c[2] + radius; z++) {
                    long dx = x - c[0];
                    long dy = y - c[1];
                    long dz = z - c[2];
                    if (dx * dx + dy * dy + dz * dz <= r2 && from.contains(Pattern.baseId(world.blockId(x, y, z)))) {
                        cells.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return bound(cells, to);
    }
}
