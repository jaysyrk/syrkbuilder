package dev.syrkbuilder.core.edit;

public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Box clampY(int worldMin, int worldMaxExclusive) {
        return new Box(minX, Math.max(minY, worldMin), minZ, maxX, Math.min(maxY, worldMaxExclusive - 1), maxZ);
    }
}
