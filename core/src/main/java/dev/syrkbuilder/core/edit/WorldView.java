package dev.syrkbuilder.core.edit;

public interface WorldView {
    int groundY(int x, int z);

    int minY();

    int maxY();

    String blockId(int x, int y, int z);

    default String blockState(int x, int y, int z) {
        return blockId(x, y, z);
    }
}
