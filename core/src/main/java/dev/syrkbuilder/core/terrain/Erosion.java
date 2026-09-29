package dev.syrkbuilder.core.terrain;

import java.util.Random;

public final class Erosion {
    private Erosion() {
    }

    public static void thermal(Heightfield hf, int iterations, double talus, double rate) {
        double[] h = hf.raw();
        int w = hf.width;
        int d = hf.depth;
        int[] di = {1, -1, 0, 0};
        int[] dj = {0, 0, 1, -1};
        double[] delta = new double[h.length];
        for (int it = 0; it < iterations; it++) {
            java.util.Arrays.fill(delta, 0);
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    int idx = j * w + i;
                    double hi = h[idx];
                    double maxDiff = 0;
                    double total = 0;
                    for (int k = 0; k < 4; k++) {
                        int ni = i + di[k];
                        int nj = j + dj[k];
                        if (ni < 0 || nj < 0 || ni >= w || nj >= d) {
                            continue;
                        }
                        double diff = hi - h[nj * w + ni];
                        if (diff > talus) {
                            total += diff;
                            maxDiff = Math.max(maxDiff, diff);
                        }
                    }
                    if (total <= 0) {
                        continue;
                    }
                    double moved = rate * (maxDiff - talus);
                    for (int k = 0; k < 4; k++) {
                        int ni = i + di[k];
                        int nj = j + dj[k];
                        if (ni < 0 || nj < 0 || ni >= w || nj >= d) {
                            continue;
                        }
                        double diff = hi - h[nj * w + ni];
                        if (diff > talus) {
                            double share = moved * diff / total;
                            delta[idx] -= share;
                            delta[nj * w + ni] += share;
                        }
                    }
                }
            }
            for (int k = 0; k < h.length; k++) {
                h[k] += delta[k];
            }
        }
    }

    public static void hydraulic(Heightfield hf, int droplets, long seed) {
        final double inertia = 0.05;
        final double capacityFactor = 4.0;
        final double minCapacity = 0.01;
        final double depositRate = 0.3;
        final double erodeRate = 0.3;
        final double evaporate = 0.02;
        final double gravity = 4.0;
        final int maxSteps = 48;
        double[] h = hf.raw();
        int w = hf.width;
        int d = hf.depth;
        if (w < 3 || d < 3) {
            return;
        }
        Random random = new Random(seed);
        for (int n = 0; n < droplets; n++) {
            double x = 1 + random.nextDouble() * (w - 3);
            double y = 1 + random.nextDouble() * (d - 3);
            double dx = 0;
            double dy = 0;
            double speed = 1;
            double water = 1;
            double sediment = 0;
            for (int step = 0; step < maxSteps; step++) {
                int cx = (int) x;
                int cy = (int) y;
                double fx = x - cx;
                double fy = y - cy;
                double[] g = gradient(h, w, cx, cy, fx, fy);
                dx = dx * inertia - g[0] * (1 - inertia);
                dy = dy * inertia - g[1] * (1 - inertia);
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len < 1e-9) {
                    break;
                }
                dx /= len;
                dy /= len;
                double nx = x + dx;
                double ny = y + dy;
                if (nx < 1 || ny < 1 || nx >= w - 2 || ny >= d - 2) {
                    break;
                }
                double oldH = g[2];
                double newH = gradient(h, w, (int) nx, (int) ny, nx - (int) nx, ny - (int) ny)[2];
                double deltaH = newH - oldH;
                double capacity = Math.max(-deltaH * speed * water * capacityFactor, minCapacity);
                if (sediment > capacity || deltaH > 0) {
                    double amount = deltaH > 0 ? Math.min(deltaH, sediment) : (sediment - capacity) * depositRate;
                    sediment -= amount;
                    deposit(h, w, cx, cy, fx, fy, amount);
                } else {
                    double amount = Math.min((capacity - sediment) * erodeRate, -deltaH);
                    sediment += amount;
                    deposit(h, w, cx, cy, fx, fy, -amount);
                }
                speed = Math.sqrt(Math.max(0, speed * speed + deltaH * -gravity));
                water *= 1 - evaporate;
                x = nx;
                y = ny;
            }
        }
    }

    private static double[] gradient(double[] h, int w, int cx, int cy, double fx, double fy) {
        double nw = h[cy * w + cx];
        double ne = h[cy * w + cx + 1];
        double sw = h[(cy + 1) * w + cx];
        double se = h[(cy + 1) * w + cx + 1];
        double gx = (ne - nw) * (1 - fy) + (se - sw) * fy;
        double gy = (sw - nw) * (1 - fx) + (se - ne) * fx;
        double height = nw * (1 - fx) * (1 - fy) + ne * fx * (1 - fy) + sw * (1 - fx) * fy + se * fx * fy;
        return new double[]{gx, gy, height};
    }

    private static void deposit(double[] h, int w, int cx, int cy, double fx, double fy, double amount) {
        h[cy * w + cx] += amount * (1 - fx) * (1 - fy);
        h[cy * w + cx + 1] += amount * fx * (1 - fy);
        h[(cy + 1) * w + cx] += amount * (1 - fx) * fy;
        h[(cy + 1) * w + cx + 1] += amount * fx * fy;
    }

    public static void smooth(Heightfield hf, int passes) {
        double[] h = hf.raw();
        int w = hf.width;
        int d = hf.depth;
        double[] tmp = new double[h.length];
        for (int p = 0; p < passes; p++) {
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    double sum = 0;
                    int count = 0;
                    for (int oj = -1; oj <= 1; oj++) {
                        for (int oi = -1; oi <= 1; oi++) {
                            int ni = i + oi;
                            int nj = j + oj;
                            if (ni >= 0 && nj >= 0 && ni < w && nj < d) {
                                sum += h[nj * w + ni];
                                count++;
                            }
                        }
                    }
                    tmp[j * w + i] = sum / count;
                }
            }
            System.arraycopy(tmp, 0, h, 0, h.length);
        }
    }
}
