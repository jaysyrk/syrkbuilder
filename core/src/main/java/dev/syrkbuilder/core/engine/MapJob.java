package dev.syrkbuilder.core.engine;

import dev.syrkbuilder.core.history.ChangeSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

final class MapJob<W, B> implements EditJob {
    private final Platform<W, B> platform;
    private final W world;
    private final Iterator<Map.Entry<Long, B>> iterator;
    private final ChangeSet<B> changes;
    private final Consumer<ChangeSet<B>> onFinish;

    MapJob(Platform<W, B> platform, W world, Map<Long, B> blocks, String label, Consumer<ChangeSet<B>> onFinish) {
        this.platform = platform;
        this.world = world;
        this.iterator = blocks.entrySet().iterator();
        this.changes = new ChangeSet<>(label);
        this.onFinish = onFinish;
    }

    @Override
    public int step(int budget) {
        int n = 0;
        while (n < budget && iterator.hasNext()) {
            Map.Entry<Long, B> e = iterator.next();
            long p = e.getKey();
            int x = ChangeSet.unpackX(p);
            int y = ChangeSet.unpackY(p);
            int z = ChangeSet.unpackZ(p);
            B old = platform.get(world, x, y, z);
            if (!old.equals(e.getValue())) {
                changes.record(x, y, z, old, e.getValue());
                platform.set(world, x, y, z, e.getValue());
            }
            n++;
        }
        return Math.max(1, n);
    }

    @Override
    public boolean done() {
        return !iterator.hasNext();
    }

    @Override
    public void finish() {
        onFinish.accept(changes);
    }
}
