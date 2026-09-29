package dev.syrkbuilder.core.engine;

public record EngineConfig(long maxVolume, int blocksPerTick, long historyBlocks, boolean saveHistory, long maxUploadBytes, long scriptTimeoutMillis) {
    public static EngineConfig defaults() {
        return new EngineConfig(4_000_000L, 40_000, 5_000_000L, true, 32L * 1024 * 1024, 3000);
    }
}
