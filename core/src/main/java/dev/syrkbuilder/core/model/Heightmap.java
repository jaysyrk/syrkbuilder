package dev.syrkbuilder.core.model;

import dev.syrkbuilder.core.grid.BlockGrid;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class Heightmap {
    private Heightmap() {
    }

    public static BlockGrid load(Path file, int size, int height) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException("Couldn't read " + file.getFileName() + " as an image.");
        }
        return build(Texture.of(image), image.getWidth(), image.getHeight(), size, height);
    }

    static BlockGrid build(Texture texture, int imageW, int imageH, int size, int height) {
        int w;
        int d;
        if (imageW >= imageH) {
            w = size;
            d = Math.max(1, (int) Math.round(size * (double) imageH / imageW));
        } else {
            d = size;
            w = Math.max(1, (int) Math.round(size * (double) imageW / imageH));
        }
        int top = height > 0 ? height : Math.max(8, size / 4);
        int[][] columns = new int[w][d];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                double b = brightness(texture, (x + 0.5) / w, (z + 0.5) / d);
                columns[x][z] = (int) Math.round(b * top);
            }
        }
        int snowLine = (int) Math.round(top * 0.82);
        BlockGrid grid = new BlockGrid(w, top + 1, d);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                int h = columns[x][z];
                int steep = 0;
                for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = Math.max(0, Math.min(w - 1, x + n[0]));
                    int nz = Math.max(0, Math.min(d - 1, z + n[1]));
                    steep = Math.max(steep, h - columns[nx][nz]);
                }
                for (int y = 0; y <= h; y++) {
                    int depth = h - y;
                    String block;
                    if (depth == 0) {
                        block = h >= snowLine && top >= 12 ? "minecraft:snow_block" : steep >= 3 ? "minecraft:stone" : "minecraft:grass_block";
                    } else if (depth <= 3 && steep < 3 && h < snowLine) {
                        block = "minecraft:dirt";
                    } else {
                        block = "minecraft:stone";
                    }
                    grid.set(x, y, z, block);
                }
            }
        }
        return grid;
    }

    private static double brightness(Texture texture, double u, double v) {
        int argb = texture.sample(u, v, true);
        int a = argb >>> 24;
        if (a < 16) {
            return 0;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    }
}
