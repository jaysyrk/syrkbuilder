package dev.syrkbuilder.core.terrain;

import dev.syrkbuilder.core.edit.BlockSink;
import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.noise.PerlinNoise;

public final class TerrainJob implements EditStream {
    private static final int REPAINT_DEPTH = 3;
    private static final String AIR = "minecraft:air";

    private final TerrainParams params;
    private final TerrainStyle style;
    private final int cx;
    private final int cy;
    private final int cz;
    private final WorldView world;
    private final String fluidBlock;

    private int count;
    private int[] colX;
    private int[] colZ;
    private int[] from;
    private int[] top;
    private int[] ground;
    private int[] fluid;
    private double[] slope;
    private double[] frac;
    private double[] jitter;
    private long estimate;
    private boolean clipped;

    private int cursor;
    private int cursorY;
    private final TerrainStyle.Column column = new TerrainStyle.Column();

    public TerrainJob(TerrainParams params, int cx, int cy, int cz, WorldView world) {
        this.params = params;
        TerrainStyle s = TerrainStyle.byName(params.style);
        this.style = s == null ? TerrainStyle.byName(params.type.defaultStyle) : s;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.world = world;
        this.fluidBlock = params.type.fluidBlock();
    }

    public TerrainJob prepare() {
        Shaper shaper = new Shaper(params);
        int ext = shaper.extent();
        int size = ext * 2 + 1;
        int minX = cx - ext;
        int minZ = cz - ext;
        PerlinNoise jitterNoise = new PerlinNoise(params.seed + 99);
        if (params.type == TerrainType.ISLAND) {
            prepareIsland(shaper, ext, size, minX, minZ, jitterNoise);
        } else {
            prepareGround(shaper, ext, size, minX, minZ, jitterNoise);
        }
        cursor = 0;
        cursorY = count > 0 ? from[0] : 0;
        return this;
    }

    private void prepareGround(Shaper shaper, int ext, int size, int minX, int minZ, PerlinNoise jitterNoise) {
        Heightfield raw = new Heightfield(minX, minZ, size, size);
        double[] rawFluid = new double[size * size];
        double maxAbs = 0;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                double h = shaper.height(i - ext, j - ext);
                raw.set(i, j, h);
                rawFluid[j * size + i] = shaper.fluid(i - ext, j - ext);
                maxAbs = Math.max(maxAbs, Math.abs(h));
            }
        }
        double scale = maxAbs > 0 ? params.height / maxAbs : 0;

        Heightfield groundHf = new Heightfield(minX, minZ, size, size);
        int sea = world.groundY(cx, cz);
        Heightfield surface = new Heightfield(minX, minZ, size, size);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int g = world.groundY(minX + i, minZ + j);
                groundHf.set(i, j, g);
                surface.set(i, j, g + raw.get(i, j) * scale);
            }
        }

        if (params.erosion > 0) {
            int area = size * size;
            Erosion.hydraulic(surface, (int) (area * 0.7 * params.erosion / 100.0), params.seed);
            Erosion.thermal(surface, 2 + params.erosion / 20, 1.6, 0.4);
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < size; i++) {
                    if (raw.get(i, j) == 0) {
                        surface.set(i, j, groundHf.get(i, j));
                    }
                }
            }
        }

        allocate(size * size);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int g = (int) groundHf.get(i, j);
                int t = clampY((int) Math.round(surface.get(i, j)));
                double f = rawFluid[j * size + i];
                int fl = Double.isNaN(f) ? Integer.MIN_VALUE : clampY((int) Math.round((params.type.water() ? sea : g) + f * scale));
                if (params.type.water() && fl != Integer.MIN_VALUE && t >= fl) {
                    t = fl - 1;
                }
                boolean changed = t != g || (fl != Integer.MIN_VALUE && fl > t);
                if (!changed) {
                    continue;
                }
                int k = count++;
                colX[k] = minX + i;
                colZ[k] = minZ + j;
                top[k] = t;
                ground[k] = g;
                fluid[k] = fl;
                from[k] = clampY(Math.min(g, t) - REPAINT_DEPTH);
                slope[k] = surface.slope(i, j);
                frac[k] = params.height > 0 ? Math.max(0, (t - g) / (double) params.height) : 0;
                jitter[k] = jitterNoise.noise((minX + i) / 12.0, (minZ + j) / 12.0);
                int last = Math.max(Math.max(t, g), fl);
                estimate += last - from[k] + 1;
            }
        }
    }

    private void prepareIsland(Shaper shaper, int ext, int size, int minX, int minZ, PerlinNoise jitterNoise) {
        Heightfield topHf = new Heightfield(minX, minZ, size, size);
        double maxTop = 0;
        double maxBottom = 0;
        double[] bottomRaw = new double[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                double t = shaper.islandTop(i - ext, j - ext);
                double b = shaper.islandBottom(i - ext, j - ext);
                topHf.set(i, j, t);
                bottomRaw[j * size + i] = b;
                maxTop = Math.max(maxTop, t);
                maxBottom = Math.max(maxBottom, b);
            }
        }
        double topScale = maxTop > 0 ? params.height * 0.5 / maxTop : 0;
        double bottomScale = maxBottom > 0 ? params.height * 1.6 / maxBottom : 0;
        Heightfield surface = new Heightfield(minX, minZ, size, size);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                surface.set(i, j, cy + topHf.get(i, j) * topScale);
            }
        }
        allocate(size * size);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                if (topHf.get(i, j) <= 0) {
                    continue;
                }
                int t = clampY((int) Math.round(surface.get(i, j)));
                int b = clampY((int) Math.round(cy - bottomRaw[j * size + i] * bottomScale));
                int k = count++;
                colX[k] = minX + i;
                colZ[k] = minZ + j;
                top[k] = t;
                ground[k] = Integer.MIN_VALUE;
                fluid[k] = Integer.MIN_VALUE;
                from[k] = Math.min(b, t);
                slope[k] = surface.slope(i, j);
                frac[k] = 0.3;
                jitter[k] = jitterNoise.noise((minX + i) / 12.0, (minZ + j) / 12.0);
                estimate += t - from[k] + 1;
            }
        }
    }

    private void allocate(int n) {
        colX = new int[n];
        colZ = new int[n];
        from = new int[n];
        top = new int[n];
        ground = new int[n];
        fluid = new int[n];
        slope = new double[n];
        frac = new double[n];
        jitter = new double[n];
        count = 0;
        estimate = 0;
    }

    private int clampY(int y) {
        int c = Math.max(world.minY(), Math.min(world.maxY() - 1, y));
        if (c != y) {
            clipped = true;
        }
        return c;
    }

    public boolean clipped() {
        return clipped;
    }

    public int columns() {
        return count;
    }

    @Override
    public long estimate() {
        return estimate;
    }

    @Override
    public boolean drain(BlockSink sink, int max) {
        int emitted = 0;
        while (cursor < count) {
            int k = cursor;
            int last = Math.max(top[k], Math.max(ground[k], fluid[k]));
            column.x = colX[k];
            column.z = colZ[k];
            column.top = top[k];
            column.slope = slope[k];
            column.heightFrac = frac[k];
            column.jitter = jitter[k];
            while (cursorY <= last) {
                if (emitted >= max) {
                    return true;
                }
                int y = cursorY++;
                String block;
                if (y <= top[k]) {
                    block = style.block(column, y, top[k] - y);
                } else if (fluid[k] != Integer.MIN_VALUE && y <= fluid[k]) {
                    block = fluidBlock;
                } else {
                    block = AIR;
                }
                sink.set(colX[k], y, colZ[k], block);
                emitted++;
            }
            cursor++;
            if (cursor < count) {
                cursorY = from[cursor];
            }
        }
        return false;
    }
}
