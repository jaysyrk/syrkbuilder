package dev.syrkbuilder.core.model;

import java.util.ArrayList;
import java.util.List;

public final class Mesh {
    public record Triangle(float[] pos, float[] uv, int color, Texture texture, boolean topLeftUv) {
    }

    public final List<Triangle> triangles = new ArrayList<>();

    public void add(Triangle t) {
        triangles.add(t);
    }

    public float[] bounds() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Triangle t : triangles) {
            for (int i = 0; i < 9; i++) {
                int axis = i % 3;
                b[axis] = Math.min(b[axis], t.pos()[i]);
                b[axis + 3] = Math.max(b[axis + 3], t.pos()[i]);
            }
        }
        return b;
    }
}
