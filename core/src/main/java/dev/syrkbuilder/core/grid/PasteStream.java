package dev.syrkbuilder.core.grid;

import dev.syrkbuilder.core.edit.BlockSink;
import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.edit.Pattern;
import java.util.HashMap;
import java.util.Map;

public final class PasteStream implements EditStream {
    private final BlockGrid grid;
    private final int ox;
    private final int oy;
    private final int oz;
    private final boolean skipAir;
    private final Map<String, String> swaps;
    private final Map<String, String> swapCache = new HashMap<>();
    private int cursor;

    public PasteStream(BlockGrid grid, int ox, int oy, int oz, boolean skipAir, Map<String, String> swaps) {
        this.grid = grid;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.skipAir = skipAir;
        this.swaps = swaps;
    }

    public dev.syrkbuilder.core.edit.Box box() {
        return new dev.syrkbuilder.core.edit.Box(ox, oy, oz, ox + grid.sizeX() - 1, oy + grid.sizeY() - 1, oz + grid.sizeZ() - 1);
    }

    @Override
    public long estimate() {
        return grid.volume();
    }

    @Override
    public boolean drain(BlockSink sink, int max) {
        int emitted = 0;
        int sx = grid.sizeX();
        int sz = grid.sizeZ();
        long volume = grid.volume();
        while (cursor < volume) {
            if (emitted >= max) {
                return true;
            }
            int i = cursor++;
            int idx = grid.rawCell(i);
            if (idx == BlockGrid.EMPTY) {
                continue;
            }
            String block = grid.palette().get(idx);
            if (skipAir && Pattern.baseId(block).endsWith(":air")) {
                continue;
            }
            if (!swaps.isEmpty()) {
                block = swapCache.computeIfAbsent(block, this::swap);
            }
            int x = i % sx;
            int z = (i / sx) % sz;
            int y = i / (sx * sz);
            sink.set(ox + x, oy + y, oz + z, block);
            emitted++;
        }
        return false;
    }

    private String swap(String block) {
        String base = Pattern.baseId(block);
        String to = swaps.get(base);
        return to == null ? block : to + block.substring(base.length());
    }
}
