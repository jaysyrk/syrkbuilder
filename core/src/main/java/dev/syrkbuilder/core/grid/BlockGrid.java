package dev.syrkbuilder.core.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockGrid {
    public static final int EMPTY = -1;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private final int[] cells;
    private final Map<String, int[]> markers = new LinkedHashMap<>();

    public BlockGrid(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Empty grid");
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = new int[sizeX * sizeY * sizeZ];
        java.util.Arrays.fill(cells, EMPTY);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public long volume() {
        return cells.length;
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public void set(int x, int y, int z, String block) {
        if (block == null) {
            cells[index(x, y, z)] = EMPTY;
            return;
        }
        Integer idx = paletteIndex.get(block);
        if (idx == null) {
            idx = palette.size();
            palette.add(block);
            paletteIndex.put(block, idx);
        }
        cells[index(x, y, z)] = idx;
    }

    public String get(int x, int y, int z) {
        int idx = cells[index(x, y, z)];
        return idx == EMPTY ? null : palette.get(idx);
    }

    public int rawCell(int i) {
        return cells[i];
    }

    public void setRawCell(int i, int paletteIdx) {
        cells[i] = paletteIdx;
    }

    public List<String> palette() {
        return palette;
    }

    public int addPalette(String block) {
        Integer idx = paletteIndex.get(block);
        if (idx == null) {
            idx = palette.size();
            palette.add(block);
            paletteIndex.put(block, idx);
        }
        return idx;
    }

    public Map<String, int[]> markers() {
        return markers;
    }

    public long nonEmpty() {
        long n = 0;
        for (int c : cells) {
            if (c != EMPTY) {
                n++;
            }
        }
        return n;
    }

    public BlockGrid transformed(int quarterTurns, boolean mirrorX) {
        int q = Math.floorMod(quarterTurns, 4);
        boolean swap = q % 2 == 1;
        BlockGrid out = new BlockGrid(swap ? sizeZ : sizeX, sizeY, swap ? sizeX : sizeZ);
        Map<String, String> stateCache = new HashMap<>();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    String block = get(x, y, z);
                    if (block == null) {
                        continue;
                    }
                    int[] p = mapPosition(x, z, q, mirrorX);
                    String rotated = stateCache.computeIfAbsent(block, b -> BlockStates.transform(b, q, mirrorX));
                    out.set(p[0], y, p[1], rotated);
                }
            }
        }
        for (Map.Entry<String, int[]> m : markers.entrySet()) {
            int[] v = m.getValue();
            int[] p = mapPosition(v[0], v[2], q, mirrorX);
            out.markers.put(m.getKey(), new int[]{p[0], v[1], p[1]});
        }
        return out;
    }

    public int[] mapPosition(int x, int z, int quarterTurns, boolean mirrorX) {
        int q = Math.floorMod(quarterTurns, 4);
        int w = sizeX;
        int d = sizeZ;
        if (mirrorX) {
            x = w - 1 - x;
        }
        for (int i = 0; i < q; i++) {
            int nx = d - 1 - z;
            int nz = x;
            x = nx;
            z = nz;
            int t = w;
            w = d;
            d = t;
        }
        return new int[]{x, z};
    }
}
