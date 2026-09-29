package dev.syrkbuilder.core.edit;

@FunctionalInterface
public interface BlockSink {
    void set(int x, int y, int z, String block);
}
