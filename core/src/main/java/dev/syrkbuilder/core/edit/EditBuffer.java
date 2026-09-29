package dev.syrkbuilder.core.edit;

import dev.syrkbuilder.core.history.ChangeSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EditBuffer implements EditStream {
    private final Map<Long, String> blocks = new LinkedHashMap<>();
    private final long limit;
    private Iterator<Map.Entry<Long, String>> iterator;

    public EditBuffer(long limit) {
        this.limit = limit;
    }

    public void set(int x, int y, int z, String block) {
        blocks.put(ChangeSet.pack(x, y, z), block);
        if (blocks.size() > limit) {
            throw new IllegalStateException("Edit is larger than the limit of " + limit + " blocks");
        }
    }

    public String get(int x, int y, int z) {
        return blocks.get(ChangeSet.pack(x, y, z));
    }

    public int size() {
        return blocks.size();
    }

    @Override
    public long estimate() {
        return blocks.size();
    }

    @Override
    public boolean drain(BlockSink sink, int max) {
        if (iterator == null) {
            iterator = blocks.entrySet().iterator();
        }
        int n = 0;
        while (iterator.hasNext()) {
            if (n++ >= max) {
                return true;
            }
            Map.Entry<Long, String> e = iterator.next();
            long p = e.getKey();
            sink.set(ChangeSet.unpackX(p), ChangeSet.unpackY(p), ChangeSet.unpackZ(p), e.getValue());
        }
        return false;
    }
}
