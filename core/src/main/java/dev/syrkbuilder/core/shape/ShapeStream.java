package dev.syrkbuilder.core.shape;

import dev.syrkbuilder.core.edit.BlockSink;
import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;

public final class ShapeStream implements EditStream {
    private final Box box;
    private final VoxelShape shape;
    private final Pattern pattern;
    private final boolean hollow;
    private final WorldView airOnly;
    private int x;
    private int y;
    private int z;

    public ShapeStream(Box box, VoxelShape shape, Pattern pattern, boolean hollow, WorldView airOnly) {
        this.box = box;
        this.shape = shape;
        this.pattern = pattern.bind(box);
        this.hollow = hollow;
        this.airOnly = airOnly;
        this.x = box.minX();
        this.y = box.minY();
        this.z = box.minZ();
    }

    public Box box() {
        return box;
    }

    @Override
    public long estimate() {
        return box.volume();
    }

    @Override
    public boolean drain(BlockSink sink, int max) {
        int emitted = 0;
        int visited = 0;
        int visitCap = Math.max(max * 8, 4096);
        while (y <= box.maxY()) {
            if (emitted >= max || visited >= visitCap) {
                return true;
            }
            visited++;
            if (shape.inside(x, y, z) && (!hollow || exposed(x, y, z))
                && (airOnly == null || airOnly.blockId(x, y, z).endsWith("air"))) {
                sink.set(x, y, z, pattern.pick(x, y, z));
                emitted++;
            }
            if (++x > box.maxX()) {
                x = box.minX();
                if (++z > box.maxZ()) {
                    z = box.minZ();
                    y++;
                }
            }
        }
        return false;
    }

    private boolean exposed(int x, int y, int z) {
        return !shape.inside(x + 1, y, z) || !shape.inside(x - 1, y, z)
            || !shape.inside(x, y + 1, z) || !shape.inside(x, y - 1, z)
            || !shape.inside(x, y, z + 1) || !shape.inside(x, y, z - 1);
    }
}
