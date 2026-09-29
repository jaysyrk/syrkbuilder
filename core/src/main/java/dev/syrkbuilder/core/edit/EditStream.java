package dev.syrkbuilder.core.edit;

public interface EditStream {
    boolean drain(BlockSink sink, int max);

    long estimate();
}
