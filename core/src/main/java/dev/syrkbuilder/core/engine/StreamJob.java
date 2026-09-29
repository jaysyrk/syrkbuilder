package dev.syrkbuilder.core.engine;

import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.grid.BlockStates;
import dev.syrkbuilder.core.history.ChangeSet;
import java.util.Map;
import java.util.function.Consumer;

final class StreamJob<W, B> implements EditJob {
    private final Platform<W, B> platform;
    private final Map<String, B> parsed;
    private final W world;
    private final EditStream stream;
    private final ChangeSet<B> changes;
    private final Consumer<StreamJob<W, B>> onFinish;
    private final dev.syrkbuilder.core.session.Session session;
    private final int[] counter = new int[1];
    private boolean done;
    private String error;

    StreamJob(Platform<W, B> platform, Map<String, B> parsed, W world, EditStream stream, String label,
              dev.syrkbuilder.core.session.Session session, Consumer<StreamJob<W, B>> onFinish) {
        this.session = session;
        this.platform = platform;
        this.parsed = parsed;
        this.world = world;
        this.stream = stream;
        this.changes = new ChangeSet<>(label);
        this.onFinish = onFinish;
    }

    ChangeSet<B> changes() {
        return changes;
    }

    String error() {
        return error;
    }

    long estimate() {
        return stream.estimate();
    }

    @Override
    public int step(int budget) {
        counter[0] = 0;
        try {
            String axes = session == null ? null : session.symmetry();
            int[] o = session == null ? null : session.symmetryOrigin();
            boolean mirrorX = axes != null && axes.contains("x");
            boolean mirrorZ = axes != null && axes.contains("z");
            boolean more = stream.drain((x, y, z, id) -> {
                counter[0]++;
                place(x, y, z, id);
                if (mirrorX) {
                    place(2 * o[0] - x, y, z, BlockStates.transform(id, 0, true));
                }
                if (mirrorZ) {
                    place(x, y, 2 * o[2] - z, BlockStates.transform(id, 2, true));
                }
                if (mirrorX && mirrorZ) {
                    place(2 * o[0] - x, y, 2 * o[2] - z, BlockStates.transform(id, 2, false));
                }
            }, budget);
            if (!more) {
                done = true;
            }
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
            done = true;
        }
        return Math.max(1, counter[0]);
    }

    private void place(int x, int y, int z, String id) {
        B old = platform.get(world, x, y, z);
        if (session != null && !session.maskAllows(platform.blockId(old))) {
            return;
        }
        B data = parse(id);
        if (!old.equals(data)) {
            changes.record(x, y, z, old, data);
            platform.set(world, x, y, z, data);
        }
    }

    private B parse(String id) {
        B data = parsed.get(id);
        if (data == null) {
            try {
                data = platform.parse(id);
            } catch (IllegalArgumentException e) {
                int bracket = id.indexOf('[');
                if (bracket < 0) {
                    throw new IllegalArgumentException("Unknown block '" + id + "'");
                }
                try {
                    data = platform.parse(id.substring(0, bracket));
                } catch (IllegalArgumentException e2) {
                    throw new IllegalArgumentException("Unknown block '" + id + "'");
                }
            }
            parsed.put(id, data);
        }
        return data;
    }

    @Override
    public boolean done() {
        return done;
    }

    @Override
    public void finish() {
        onFinish.accept(this);
    }
}
