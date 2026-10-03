package dev.syrkbuilder.core.noise;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class NoiseField {
    private static final Map<Integer, double[]> RANGES = new ConcurrentHashMap<>();

    private final PerlinNoise perlin;
    private final SimplexNoise simplex;

    public NoiseField(long seed) {
        this.perlin = new PerlinNoise(seed);
        this.simplex = new SimplexNoise(seed);
    }

    // Normalised per kind and octave count so that 0 is the middle of the relief and the highest and lowest 1% of the noise land
    // on 1 and -1. Without it billowy sits well below zero and layering more octaves flattens everything, so strength= means nothing.
    public double sample(NoiseKind kind, double x, double z, int octaves) {
        double[] range = RANGES.computeIfAbsent(kind.ordinal() * 16 + octaves, k -> measure(kind, octaves));
        return (raw(kind, x, z, octaves) - range[0]) * range[1];
    }

    private static double[] measure(NoiseKind kind, int octaves) {
        NoiseField probe = new NoiseField(0x5EEDL);
        Random random = new Random(7);
        double[] v = new double[4096];
        for (int i = 0; i < v.length; i++) {
            v[i] = probe.raw(kind, random.nextDouble() * 512, random.nextDouble() * 512, octaves);
        }
        java.util.Arrays.sort(v);
        double low = v[v.length / 100];
        double high = v[v.length - 1 - v.length / 100];
        return new double[]{(low + high) / 2, high > low ? 2 / (high - low) : 1};
    }

    private double raw(NoiseKind kind, double x, double z, int octaves) {
        return switch (kind) {
            case FRACTAL -> perlin.fbm(x, z, octaves, 2.0, 0.5);
            case RIDGED -> 2 * perlin.ridged(x, z, octaves, 2.0, 0.5) - 1;
            case SIMPLEX -> layered(false, x, z, octaves);
            case BILLOWY -> layered(true, x, z, octaves);
        };
    }

    private double layered(boolean billowy, double x, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            double n = billowy ? 2 * Math.abs(perlin.noise(x, z)) - 1 : simplex.noise(x, z);
            sum += amp * n;
            norm += amp;
            amp *= 0.5;
            x *= 2;
            z *= 2;
        }
        return sum / norm;
    }
}
