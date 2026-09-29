package dev.syrkbuilder.core.terrain;

public final class Heightfield {
    public final int minX;
    public final int minZ;
    public final int width;
    public final int depth;
    private final double[] values;

    public Heightfield(int minX, int minZ, int width, int depth) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.depth = depth;
        this.values = new double[width * depth];
    }

    public double get(int i, int j) {
        return values[j * width + i];
    }

    public void set(int i, int j, double v) {
        values[j * width + i] = v;
    }

    public void add(int i, int j, double v) {
        values[j * width + i] += v;
    }

    public double getClamped(int i, int j) {
        i = Math.max(0, Math.min(width - 1, i));
        j = Math.max(0, Math.min(depth - 1, j));
        return values[j * width + i];
    }

    public boolean inside(int i, int j) {
        return i >= 0 && j >= 0 && i < width && j < depth;
    }

    public double slope(int i, int j) {
        double h = get(i, j);
        double s = 0;
        s = Math.max(s, Math.abs(h - getClamped(i + 1, j)));
        s = Math.max(s, Math.abs(h - getClamped(i - 1, j)));
        s = Math.max(s, Math.abs(h - getClamped(i, j + 1)));
        s = Math.max(s, Math.abs(h - getClamped(i, j - 1)));
        return s;
    }

    public double max() {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            m = Math.max(m, v);
        }
        return m;
    }

    public double min() {
        double m = Double.POSITIVE_INFINITY;
        for (double v : values) {
            m = Math.min(m, v);
        }
        return m;
    }

    public Heightfield copy() {
        Heightfield c = new Heightfield(minX, minZ, width, depth);
        System.arraycopy(values, 0, c.values, 0, values.length);
        return c;
    }

    double[] raw() {
        return values;
    }
}
