package dev.syrkbuilder.core.engine;

import dev.syrkbuilder.core.history.ChangeSet;

final class ReplayJob<W, B> implements EditJob {
    private final Platform<W, B> platform;
    private final W world;
    private final ChangeSet<B> changes;
    private final boolean undo;
    private final Runnable onFinish;
    private int next;

    ReplayJob(Platform<W, B> platform, W world, ChangeSet<B> changes, boolean undo, Runnable onFinish) {
        this.platform = platform;
        this.world = world;
        this.changes = changes;
        this.undo = undo;
        this.onFinish = onFinish;
    }

    @Override
    public int step(int budget) {
        int start = next;
        next = changes.replay(undo, next, budget, (x, y, z, state) -> platform.set(world, x, y, z, state));
        return Math.max(1, next < 0 ? changes.size() - start : next - start);
    }

    @Override
    public boolean done() {
        return next < 0 || changes.size() == 0;
    }

    @Override
    public void finish() {
        onFinish.run();
    }
}
