package dev.syrkbuilder.core.shape;

import dev.syrkbuilder.core.edit.BlockSink;
import dev.syrkbuilder.core.edit.Box;
import dev.syrkbuilder.core.edit.EditStream;
import dev.syrkbuilder.core.edit.Pattern;
import dev.syrkbuilder.core.edit.WorldView;
import java.util.Set;

public final class SelectionStreams {
    private SelectionStreams() {
    }

    public static EditStream set(Box box, Pattern pattern) {
        return new ShapeStream(box, (x, y, z) -> true, pattern, false, null);
    }

    public static EditStream replace(Box box, Set<String> from, Pattern to, WorldView world) {
        return new ShapeStream(box, (x, y, z) -> box.contains(x, y, z) && from.contains(world.blockId(x, y, z)), to, false, null);
    }

    public static EditStream walls(Box box, Pattern pattern) {
        return new ShapeStream(box, (x, y, z) -> x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ(), pattern, false, null);
    }

    public static EditStream outline(Box box, Pattern pattern) {
        return new ShapeStream(box, (x, y, z) -> x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ()
            || y == box.minY() || y == box.maxY(), pattern, false, null);
    }
}
