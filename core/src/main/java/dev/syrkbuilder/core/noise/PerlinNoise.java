package dev.syrkbuilder.core.noise;

import java.util.Random;

public final class PerlinNoise {
    private final int[] perm = new int[512];

    public PerlinNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    public double noise(double x, double y) {
        int xi = floor(x);
        int yi = floor(y);
        double xf = x - xi;
        double yf = y - yi;
        int X = xi & 255;
        int Y = yi & 255;
        double u = fade(xf);
        double v = fade(yf);
        int aa = perm[perm[X] + Y];
        int ab = perm[perm[X] + Y + 1];
        int ba = perm[perm[X + 1] + Y];
        int bb = perm[perm[X + 1] + Y + 1];
        double x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf));
        double x2 = lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1));
        return lerp(v, x1, x2) * 1.41421356;
    }

    public double fbm(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y);
            norm += amp;
            amp *= gain;
            x *= lacunarity;
            y *= lacunarity;
        }
        return sum / norm;
    }

    public double ridged(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0;
        double amp = 0.5;
        double norm = 0;
        double weight = 1;
        for (int i = 0; i < octaves; i++) {
            double n = 1 - Math.abs(noise(x, y));
            n *= n;
            n *= weight;
            weight = Math.min(1, Math.max(0, n * 2));
            sum += n * amp;
            norm += amp;
            amp *= gain;
            x *= lacunarity;
            y *= lacunarity;
        }
        return sum / norm;
    }

    public double noise(double x, double y, double z) {
        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);
        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;
        int X = xi & 255;
        int Y = yi & 255;
        int Z = zi & 255;
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);
        int a = perm[X] + Y;
        int aa = perm[a] + Z;
        int ab = perm[a + 1] + Z;
        int b = perm[X + 1] + Y;
        int ba = perm[b] + Z;
        int bb = perm[b + 1] + Z;
        double x1 = lerp(u, grad3(perm[aa], xf, yf, zf), grad3(perm[ba], xf - 1, yf, zf));
        double x2 = lerp(u, grad3(perm[ab], xf, yf - 1, zf), grad3(perm[bb], xf - 1, yf - 1, zf));
        double y1 = lerp(v, x1, x2);
        x1 = lerp(u, grad3(perm[aa + 1], xf, yf, zf - 1), grad3(perm[ba + 1], xf - 1, yf, zf - 1));
        x2 = lerp(u, grad3(perm[ab + 1], xf, yf - 1, zf - 1), grad3(perm[bb + 1], xf - 1, yf - 1, zf - 1));
        return lerp(w, y1, lerp(v, x1, x2));
    }

    public double fbm(double x, double y, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y, z);
            norm += amp;
            amp *= 0.5;
            x *= 2;
            y *= 2;
            z *= 2;
        }
        return sum / norm;
    }

    private static double grad3(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        switch (hash & 7) {
            case 0: return x + y;
            case 1: return -x + y;
            case 2: return x - y;
            case 3: return -x - y;
            case 4: return x;
            case 5: return -x;
            case 6: return y;
            default: return -y;
        }
    }
}
