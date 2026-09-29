package dev.syrkbuilder.core.model;

import java.awt.image.BufferedImage;

public final class Texture {
    private final int width;
    private final int height;
    private final int[] argb;

    public Texture(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public static Texture of(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        return new Texture(w, h, image.getRGB(0, 0, w, h, null, 0, w));
    }

    public int sample(double u, double v, boolean topLeftOrigin) {
        double fu = u - Math.floor(u);
        double fv = v - Math.floor(v);
        if (!topLeftOrigin) {
            fv = 1 - fv;
        }
        int x = Math.min(width - 1, (int) (fu * width));
        int y = Math.min(height - 1, (int) (fv * height));
        return argb[y * width + x];
    }
}
