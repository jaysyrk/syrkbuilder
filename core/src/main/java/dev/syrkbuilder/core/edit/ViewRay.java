package dev.syrkbuilder.core.edit;

public final class ViewRay {
    private ViewRay() {
    }

    public static double[] direction(double[] forward, double[] left, double[] up, double fovDegrees, double aspect, double nx, double ny) {
        double tan = Math.tan(Math.toRadians(fovDegrees) / 2);
        double right = nx * tan * aspect;
        double rise = ny * tan;
        double[] d = new double[3];
        double len = 0;
        for (int i = 0; i < 3; i++) {
            d[i] = forward[i] - left[i] * right + up[i] * rise;
            len += d[i] * d[i];
        }
        len = Math.sqrt(len);
        for (int i = 0; i < 3; i++) {
            d[i] /= len;
        }
        return d;
    }
}
