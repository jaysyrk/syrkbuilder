package dev.syrkbuilder.core.brush;

public final class StrokeGate {
    private boolean has;
    private int x;
    private int y;
    private int z;

    public boolean allow(int nx, int ny, int nz) {
        if (has && x == nx && y == ny && z == nz) {
            return false;
        }
        has = true;
        x = nx;
        y = ny;
        z = nz;
        return true;
    }

    public void miss() {
        has = false;
    }

    public void reset() {
        has = false;
    }
}
