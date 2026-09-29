package dev.syrkbuilder.core.edit;

import java.util.ArrayList;
import java.util.List;

public final class CellStream implements EditStream {
    private final List<int[]> positions = new ArrayList<>();
    private final List<String> blocks = new ArrayList<>();
    private final Box box;
    private int cursor;

    public CellStream(Box box) {
        this.box = box;
    }

    public void add(int x, int y, int z, String block) {
        positions.add(new int[]{x, y, z});
        blocks.add(block);
    }

    public int size() {
        return positions.size();
    }

    public Box box() {
        return box;
    }

    @Override
    public boolean drain(BlockSink sink, int max) {
        int emitted = 0;
        while (cursor < positions.size()) {
            if (emitted >= max) {
                return true;
            }
            int[] p = positions.get(cursor);
            sink.set(p[0], p[1], p[2], blocks.get(cursor));
            cursor++;
            emitted++;
        }
        return false;
    }

    @Override
    public long estimate() {
        return positions.size();
    }
}
