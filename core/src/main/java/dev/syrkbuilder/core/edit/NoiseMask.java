package dev.syrkbuilder.core.edit;

import dev.syrkbuilder.core.noise.SimplexNoise;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

// A mask that lets an edit through only in organic patches: fractal blobs, voronoi cells, crack lines, or a height ramp.
// Patterns are 2D (the same all the way up a column) unless 3d is given, so terrain and floors get clean patches.
public final class NoiseMask {
    public static final List<String> NAMES = List.of("fractal", "cell", "voronoi", "crack", "ygradient");
    private static final int CALIBRATION = 4096;

    private enum Kind { FRACTAL, CELL, VORONOI, CRACK, YGRADIENT }

    private final Kind kind;
    private final boolean inverted;
    private final boolean volume;
    private final double scale;
    private final double coverage;
    private final double jitter;
    private final double y0;
    private final double y1;
    private final long seed;
    private final SimplexNoise noise;
    private final SimplexNoise warp;
    private double threshold;

    private NoiseMask(Kind kind, boolean inverted, boolean volume, double scale, double coverage, double jitter, double y0, double y1, long seed) {
        this.kind = kind;
        this.inverted = inverted;
        this.volume = volume;
        this.scale = scale;
        this.coverage = coverage;
        this.jitter = jitter;
        this.y0 = y0;
        this.y1 = y1;
        this.seed = seed;
        this.noise = new SimplexNoise(seed);
        this.warp = new SimplexNoise(seed * 31 + 7);
    }

    public static boolean isSpec(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (s.startsWith("!")) {
            s = s.substring(1);
        }
        for (String name : NAMES) {
            if (s.equals(name) || s.startsWith(name + ":")) {
                return true;
            }
        }
        return false;
    }

    public static NoiseMask parse(String raw, long seed) {
        String s = raw.toLowerCase(Locale.ROOT);
        boolean inverted = s.startsWith("!");
        if (inverted) {
            s = s.substring(1);
        }
        String[] parts = s.split(":");
        Kind kind;
        try {
            kind = Kind.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown mask '" + parts[0] + "'. Noise masks: " + String.join(", ", NAMES));
        }
        boolean volume = false;
        double[] n = new double[3];
        int count = 0;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equals("3d")) {
                volume = true;
                continue;
            }
            if (count == n.length) {
                throw new IllegalArgumentException("Too many numbers for mask '" + parts[0] + "'");
            }
            try {
                n[count++] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + parts[i] + "' isn't a number in the mask '" + raw + "'");
            }
        }
        double scale = count > 0 ? n[0] : kind == Kind.FRACTAL ? 8 : 6;
        double coverage = count > 1 ? n[1] : switch (kind) {
            case VORONOI -> 0.15;
            case CRACK -> 0.12;
            default -> 0.5;
        };
        double jitter = count > 2 ? n[2] : 0.5;
        if (kind == Kind.YGRADIENT) {
            if (count < 2) {
                throw new IllegalArgumentException("ygradient needs two heights: ygradient:60:90 lets edits through more and more often from y=60 up to y=90");
            }
            return new NoiseMask(kind, inverted, true, 1, 1, 0, n[0], n[1], seed);
        }
        if (scale < 1 || scale > 256) {
            throw new IllegalArgumentException("Mask scale must be 1-256 blocks");
        }
        if (coverage <= 0 || coverage >= 1) {
            throw new IllegalArgumentException("Mask coverage must be between 0 and 1, like 0.4");
        }
        NoiseMask m = new NoiseMask(kind, inverted, volume, scale, coverage, Math.max(0, Math.min(2, jitter)), 0, 0, seed);
        m.calibrate();
        return m;
    }

    // Pick the cut-off from sampled values, so coverage=0.3 really lets about 30% through whatever the noise's range is.
    private void calibrate() {
        if (kind == Kind.CELL) {
            return;
        }
        Random random = new Random(seed);
        double[] sample = new double[CALIBRATION];
        for (int i = 0; i < CALIBRATION; i++) {
            sample[i] = field(random.nextInt(2048) - 1024, random.nextInt(256), random.nextInt(2048) - 1024);
        }
        Arrays.sort(sample);
        threshold = sample[Math.min(CALIBRATION - 1, (int) ((1 - coverage) * CALIBRATION))];
    }

    public boolean allows(int x, int y, int z) {
        return inside(x, y, z) != inverted;
    }

    private boolean inside(int x, int y, int z) {
        return switch (kind) {
            case YGRADIENT -> {
                double t = y1 == y0 ? (y >= y0 ? 1 : 0) : Math.max(0, Math.min(1, (y - y0) / (y1 - y0)));
                yield hash(x, y, z, 0) < t;
            }
            case CELL -> {
                double[] f = voronoi(x + 0.5, y + 0.5, z + 0.5, scale);
                yield f[2] < coverage;
            }
            default -> field(x, y, z) >= threshold;
        };
    }

    private double field(int x, int y, int z) {
        return switch (kind) {
            case FRACTAL -> {
                double v = noise.noise(x / scale, volume ? y / scale : 0.37, z / scale);
                yield v + 0.5 * noise.noise(x / (scale * 0.4), volume ? y / (scale * 0.4) : 0.81, z / (scale * 0.4));
            }
            case VORONOI -> {
                double[] f = voronoi(x + 0.5, y + 0.5, z + 0.5, scale);
                yield -(f[1] - f[0]);
            }
            default -> {
                double w = jitter * scale * 0.35;
                double px = x + 0.5 + w * warp.noise(x / (scale * 0.7), volume ? y / (scale * 0.7) : 0.2, z / (scale * 0.7));
                double pz = z + 0.5 + w * warp.noise(x / (scale * 0.7) + 91.7, volume ? y / (scale * 0.7) : 0.2, z / (scale * 0.7) - 33.1);
                double[] f = voronoi(px, y + 0.5, pz, scale);
                yield -(f[1] - f[0]);
            }
        };
    }

    // Distance to the nearest and second-nearest feature point, plus a stable 0-1 id for the nearest cell.
    private double[] voronoi(double px, double py, double pz, double size) {
        double fx = px / size;
        double fy = volume ? py / size : 0;
        double fz = pz / size;
        int cx = (int) Math.floor(fx);
        int cy = (int) Math.floor(fy);
        int cz = (int) Math.floor(fz);
        double best = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        double id = 0;
        int ry = volume ? 1 : 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -ry; dy <= ry; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int gx = cx + dx;
                    int gy = cy + dy;
                    int gz = cz + dz;
                    double ox = gx + 0.1 + 0.8 * hash(gx, gy, gz, 1);
                    double oy = volume ? gy + 0.1 + 0.8 * hash(gx, gy, gz, 2) : 0;
                    double oz = gz + 0.1 + 0.8 * hash(gx, gy, gz, 3);
                    double d = Math.sqrt((ox - fx) * (ox - fx) + (oy - fy) * (oy - fy) + (oz - fz) * (oz - fz)) * size;
                    if (d < best) {
                        second = best;
                        best = d;
                        id = hash(gx, gy, gz, 4);
                    } else if (d < second) {
                        second = d;
                    }
                }
            }
        }
        return new double[]{best, second, id};
    }

    private double hash(int x, int y, int z, int k) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + y * 0x94D049BB133111EBL + z * 0xD6E8FEB86659FD93L + k * 0xFF51AFD7ED558CCDL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    public String describe() {
        String not = inverted ? "everything except " : "";
        return switch (kind) {
            case YGRADIENT -> not + "a height ramp from y=" + (int) y0 + " to y=" + (int) y1;
            case FRACTAL -> not + "fractal patches (" + (int) scale + " blocks, " + Math.round(coverage * 100) + "%" + (volume ? ", 3d" : "") + ")";
            case CELL -> not + "voronoi cells (" + (int) scale + " blocks, " + Math.round(coverage * 100) + "% of cells" + (volume ? ", 3d" : "") + ")";
            case VORONOI -> not + "voronoi borders (" + (int) scale + " blocks, " + Math.round(coverage * 100) + "%" + (volume ? ", 3d" : "") + ")";
            case CRACK -> not + "cracks (" + (int) scale + " blocks, " + Math.round(coverage * 100) + "%" + (volume ? ", 3d" : "") + ")";
        };
    }
}
