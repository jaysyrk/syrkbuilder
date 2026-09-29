package dev.syrkbuilder.core.model;

import java.util.ArrayDeque;

public final class Voxelizer {
    private Voxelizer() {
    }

    public static VoxelModel voxelize(Mesh mesh, int size, boolean solid) {
        if (mesh.triangles.isEmpty()) {
            throw new IllegalArgumentException("The model has no triangles");
        }
        float[] b = mesh.bounds();
        double extent = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        if (extent <= 0) {
            throw new IllegalArgumentException("The model is flat or empty");
        }
        double scale = (size - 1) / extent;
        int sx = Math.max(1, (int) Math.floor((b[3] - b[0]) * scale) + 1);
        int sy = Math.max(1, (int) Math.floor((b[4] - b[1]) * scale) + 1);
        int sz = Math.max(1, (int) Math.floor((b[5] - b[2]) * scale) + 1);
        long cells = (long) sx * sy * sz;
        if (cells > 64L * 1024 * 1024) {
            throw new IllegalArgumentException("Model would be " + sx + "x" + sy + "x" + sz + " - use a smaller size=");
        }
        long[] r = new long[(int) cells];
        long[] g = new long[(int) cells];
        long[] bl = new long[(int) cells];
        int[] n = new int[(int) cells];

        double[] p = new double[9];
        for (Mesh.Triangle t : mesh.triangles) {
            for (int i = 0; i < 3; i++) {
                p[i * 3] = (t.pos()[i * 3] - b[0]) * scale;
                p[i * 3 + 1] = (t.pos()[i * 3 + 1] - b[1]) * scale;
                p[i * 3 + 2] = (t.pos()[i * 3 + 2] - b[2]) * scale;
            }
            double e1 = dist(p, 0, 1);
            double e2 = dist(p, 1, 2);
            double e3 = dist(p, 0, 2);
            int steps = (int) Math.ceil(Math.max(e1, Math.max(e2, e3)) * 2) + 1;
            for (int i = 0; i <= steps; i++) {
                for (int j = 0; j <= steps - i; j++) {
                    double u = i / (double) steps;
                    double v = j / (double) steps;
                    double w = 1 - u - v;
                    double x = p[0] * w + p[3] * u + p[6] * v;
                    double y = p[1] * w + p[4] * u + p[7] * v;
                    double z = p[2] * w + p[5] * u + p[8] * v;
                    int vx = clamp((int) Math.floor(x + 0.5), sx);
                    int vy = clamp((int) Math.floor(y + 0.5), sy);
                    int vz = clamp((int) Math.floor(z + 0.5), sz);
                    int color = colorAt(t, w, u, v);
                    if ((color >>> 24) < 128) {
                        continue;
                    }
                    int idx = (vy * sz + vz) * sx + vx;
                    r[idx] += (color >> 16) & 255;
                    g[idx] += (color >> 8) & 255;
                    bl[idx] += color & 255;
                    n[idx]++;
                }
            }
        }
        VoxelModel model = new VoxelModel(sx, sy, sz);
        for (int i = 0; i < cells; i++) {
            if (n[i] > 0) {
                model.argb[i] = 0xFF000000 | (int) (r[i] / n[i]) << 16 | (int) (g[i] / n[i]) << 8 | (int) (bl[i] / n[i]);
            }
        }
        if (solid) {
            fillInside(model);
        }
        return model;
    }

    private static int colorAt(Mesh.Triangle t, double w, double u, double v) {
        int base = t.color();
        if (t.texture() == null || t.uv() == null) {
            return base;
        }
        float[] uv = t.uv();
        double tu = uv[0] * w + uv[2] * u + uv[4] * v;
        double tv = uv[1] * w + uv[3] * u + uv[5] * v;
        int tex = t.texture().sample(tu, tv, t.topLeftUv());
        return multiply(tex, base);
    }

    static int multiply(int a, int b) {
        int al = ((a >>> 24) * (b >>> 24)) / 255;
        int r = (((a >> 16) & 255) * ((b >> 16) & 255)) / 255;
        int g = (((a >> 8) & 255) * ((b >> 8) & 255)) / 255;
        int bl = ((a & 255) * (b & 255)) / 255;
        return al << 24 | r << 16 | g << 8 | bl;
    }

    private static double dist(double[] p, int a, int b) {
        double dx = p[a * 3] - p[b * 3];
        double dy = p[a * 3 + 1] - p[b * 3 + 1];
        double dz = p[a * 3 + 2] - p[b * 3 + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    public static void fillInside(VoxelModel m) {
        int sx = m.sizeX;
        int sy = m.sizeY;
        int sz = m.sizeZ;
        boolean[] outside = new boolean[m.argb.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1) {
                        int i = m.index(x, y, z);
                        if ((m.argb[i] >>> 24) == 0 && !outside[i]) {
                            outside[i] = true;
                            queue.add(i);
                        }
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % sx;
            int z = (i / sx) % sz;
            int y = i / (sx * sz);
            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                    continue;
                }
                int ni = m.index(nx, ny, nz);
                if (!outside[ni] && (m.argb[ni] >>> 24) == 0) {
                    outside[ni] = true;
                    queue.add(ni);
                }
            }
        }
        for (int i = 0; i < m.argb.length; i++) {
            if ((m.argb[i] >>> 24) != 0) {
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % sx;
            int z = (i / sx) % sz;
            int y = i / (sx * sz);
            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                    continue;
                }
                int ni = m.index(nx, ny, nz);
                if (!outside[ni] && (m.argb[ni] >>> 24) == 0) {
                    m.argb[ni] = m.argb[i];
                    queue.add(ni);
                }
            }
        }
    }
}
