package dev.syrkbuilder.core.edit;

import java.util.List;

public final class ChainStream implements EditStream {
    private final List<EditStream> parts;
    private int index;

    public ChainStream(List<EditStream> parts) {
        this.parts = List.copyOf(parts);
    }

    @Override
    public boolean drain(BlockSink sink, int max) {
        int[] count = {0};
        BlockSink counting = (x, y, z, block) -> {
            count[0]++;
            sink.set(x, y, z, block);
        };
        while (index < parts.size()) {
            int left = max - count[0];
            if (left <= 0) {
                return true;
            }
            if (parts.get(index).drain(counting, left)) {
                return true;
            }
            index++;
        }
        return false;
    }

    @Override
    public long estimate() {
        long total = 0;
        for (EditStream part : parts) {
            total += part.estimate();
        }
        return total;
    }
}
