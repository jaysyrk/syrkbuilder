package dev.syrkbuilder.core.engine;

import dev.syrkbuilder.core.edit.WorldView;

public interface Platform<W, B> {
    WorldView view(W world);

    String worldKey(W world);

    B parse(String state);

    String serialize(B state);

    String blockId(B state);

    B get(W world, int x, int y, int z);

    void set(W world, int x, int y, int z, B state);

    default int dataVersion() {
        return dev.syrkbuilder.core.grid.Schematics.DEFAULT_DATA_VERSION;
    }

    default String noclip(java.util.UUID player, String mode) {
        return null;
    }

    void runOnMainThread(Runnable task);
}
