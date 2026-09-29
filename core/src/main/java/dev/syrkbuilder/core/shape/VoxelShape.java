package dev.syrkbuilder.core.shape;

@FunctionalInterface
public interface VoxelShape {
    boolean inside(int x, int y, int z);
}
