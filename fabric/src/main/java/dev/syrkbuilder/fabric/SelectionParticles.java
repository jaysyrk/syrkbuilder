package dev.syrkbuilder.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

final class SelectionParticles {
    private static final int MAX_PER_EDGE = 60;
    private int ticks;

    void tick(Minecraft client) {
        if (++ticks % 5 != 0 || client.level == null || client.player == null) {
            return;
        }
        ClientData.Pending p = ClientData.pending();
        if (p != null) {
            box(client.level, p.minX(), p.minY(), p.minZ(), p.maxX() + 1, p.maxY() + 1, p.maxZ() + 1, ParticleTypes.END_ROD);
        }
        java.util.List<int[]> path = ClientData.path();
        if (!path.isEmpty()) {
            for (int[] q : path) {
                client.level.addParticle(ParticleTypes.END_ROD, q[0] + 0.5, q[1] + 1.2, q[2] + 0.5, 0, 0.02, 0);
            }
            java.util.List<double[]> curve = dev.syrkbuilder.core.path.Paths.curve(path, 1.0);
            for (int i = 0; i < curve.size(); i += Math.max(1, curve.size() / 200)) {
                double[] c = curve.get(i);
                client.level.addParticle(ParticleTypes.FLAME, c[0], c[1] + 1.1, c[2], 0, 0, 0);
            }
        }
        BlockPos a = Selection.pos1();
        BlockPos b = Selection.pos2();
        if (a == null && b == null) {
            return;
        }
        if (a == null || b == null) {
            BlockPos only = a != null ? a : b;
            box(client.level, only.getX(), only.getY(), only.getZ(), only.getX() + 1, only.getY() + 1, only.getZ() + 1, ParticleTypes.HAPPY_VILLAGER);
            return;
        }
        box(client.level, Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1, ParticleTypes.HAPPY_VILLAGER);
    }

    private static void box(ClientLevel level, double x1, double y1, double z1, double x2, double y2, double z2, ParticleOptions particle) {
        double[] xs = {x1, x2};
        double[] ys = {y1, y2};
        double[] zs = {z1, z2};
        for (double y : ys) {
            for (double z : zs) {
                edge(level, x1, y, z, x2, y, z, particle);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                edge(level, x, y1, z, x, y2, z, particle);
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                edge(level, x, y, z1, x, y, z2, particle);
            }
        }
    }

    private static void edge(ClientLevel level, double x1, double y1, double z1, double x2, double y2, double z2, ParticleOptions particle) {
        double len = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        int steps = (int) Math.min(MAX_PER_EDGE, Math.max(1, len));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            level.addParticle(particle, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 0, 0, 0);
        }
    }
}
