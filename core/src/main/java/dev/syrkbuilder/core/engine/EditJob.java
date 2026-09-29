package dev.syrkbuilder.core.engine;

interface EditJob {
    int step(int budget);

    boolean done();

    void finish();
}
