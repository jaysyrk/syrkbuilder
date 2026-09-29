package dev.syrkbuilder.fabric;

import net.minecraft.core.BlockPos;

public final class Selection {
    private static BlockPos pos1;
    private static BlockPos pos2;

    private Selection() {
    }

    public static BlockPos pos1() {
        return pos1;
    }

    public static BlockPos pos2() {
        return pos2;
    }

    public static void setPos1(BlockPos pos) {
        pos1 = pos.immutable();
    }

    public static void setPos2(BlockPos pos) {
        pos2 = pos.immutable();
    }

    public static void clear() {
        pos1 = null;
        pos2 = null;
    }

    public static boolean complete() {
        return pos1 != null && pos2 != null;
    }

    public static long volume() {
        if (!complete()) {
            return 0;
        }
        return (long) (Math.abs(pos1.getX() - pos2.getX()) + 1) * (Math.abs(pos1.getY() - pos2.getY()) + 1) * (Math.abs(pos1.getZ() - pos2.getZ()) + 1);
    }

    public static String describe(BlockPos p) {
        return p == null ? "unset" : p.getX() + ", " + p.getY() + ", " + p.getZ();
    }
}
