package dev.syrkbuilder.core.model;

public final class VoxelModel {
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final int[] argb;

    public VoxelModel(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.argb = new int[sizeX * sizeY * sizeZ];
    }

    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public int get(int x, int y, int z) {
        return argb[index(x, y, z)];
    }

    public void set(int x, int y, int z, int color) {
        argb[index(x, y, z)] = color;
    }

    public long filled() {
        long n = 0;
        for (int c : argb) {
            if ((c >>> 24) != 0) {
                n++;
            }
        }
        return n;
    }
}
