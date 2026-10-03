package dev.syrkbuilder.core.noise;

import java.util.Random;

public final class SimplexNoise {
    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
        {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];

    public SimplexNoise(long seed) {
        short[] p = new short[256];
        for (short i = 0; i < 256; i++) {
            p[i] = i;
        }
        Random random = new Random(seed ^ 0x2545F4914F6CDD1DL);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            short t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = (short) (perm[i] % 12);
        }
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double dot(int[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }

    private static double dot(int[] g, double x, double y, double z) {
        return g[0] * x + g[1] * y + g[2] * z;
    }

    public double noise(double xin, double yin) {
        double s = (xin + yin) * F2;
        int i = floor(xin + s);
        int j = floor(yin + s);
        double t = (i + j) * G2;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = x0 > y0 ? 0 : 1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        double n0 = corner(permMod12[ii + perm[jj]], x0, y0);
        double n1 = corner(permMod12[ii + i1 + perm[jj + j1]], x1, y1);
        double n2 = corner(permMod12[ii + 1 + perm[jj + 1]], x2, y2);
        return 70.0 * (n0 + n1 + n2);
    }

    private double corner(int gi, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0) {
            return 0;
        }
        t *= t;
        return t * t * dot(GRAD3[gi], x, y);
    }

    private double corner(int gi, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t < 0) {
            return 0;
        }
        t *= t;
        return t * t * dot(GRAD3[gi], x, y, z);
    }

    public double noise(double xin, double yin, double zin) {
        double s = (xin + yin + zin) * F3;
        int i = floor(xin + s);
        int j = floor(yin + s);
        int k = floor(zin + s);
        double t = (i + j + k) * G3;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        double z0 = zin - (k - t);
        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else if (y0 < z0) {
            i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
        } else if (x0 < z0) {
            i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
        } else {
            i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
        }
        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        double n0 = corner(permMod12[ii + perm[jj + perm[kk]]], x0, y0, z0);
        double n1 = corner(permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]], x1, y1, z1);
        double n2 = corner(permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]], x2, y2, z2);
        double n3 = corner(permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]], x3, y3, z3);
        return 32.0 * (n0 + n1 + n2 + n3);
    }
}
