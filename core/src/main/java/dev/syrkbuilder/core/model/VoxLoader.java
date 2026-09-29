package dev.syrkbuilder.core.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoxLoader {
    private VoxLoader() {
    }

    public static VoxelModel load(Path file) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.remaining() < 8 || buf.get() != 'V' || buf.get() != 'O' || buf.get() != 'X' || buf.get() != ' ') {
            throw new IOException("Not a MagicaVoxel file");
        }
        buf.getInt();
        int sx = -1;
        int sy = -1;
        int sz = -1;
        byte[] voxels = null;
        int[] palette = null;
        while (buf.remaining() >= 12) {
            String id = "" + (char) buf.get() + (char) buf.get() + (char) buf.get() + (char) buf.get();
            int content = buf.getInt();
            buf.getInt();
            int start = buf.position();
            switch (id) {
                case "SIZE" -> {
                    if (sx < 0) {
                        sx = buf.getInt();
                        sy = buf.getInt();
                        sz = buf.getInt();
                    }
                }
                case "XYZI" -> {
                    if (voxels == null) {
                        int n = buf.getInt();
                        voxels = new byte[n * 4];
                        buf.get(voxels);
                    }
                }
                case "RGBA" -> {
                    palette = new int[256];
                    for (int i = 0; i < 256; i++) {
                        int r = buf.get() & 255;
                        int g = buf.get() & 255;
                        int b = buf.get() & 255;
                        int a = buf.get() & 255;
                        palette[i] = a << 24 | r << 16 | g << 8 | b;
                    }
                }
                default -> {
                }
            }
            if (!id.equals("MAIN")) {
                buf.position(start + content);
            }
        }
        if (sx <= 0 || voxels == null) {
            throw new IOException("No voxel data found");
        }
        VoxelModel model = new VoxelModel(sx, sz, sy);
        for (int i = 0; i < voxels.length; i += 4) {
            int x = voxels[i] & 255;
            int y = voxels[i + 1] & 255;
            int z = voxels[i + 2] & 255;
            int c = voxels[i + 3] & 255;
            if (x >= sx || y >= sy || z >= sz || c == 0) {
                continue;
            }
            int color = palette != null ? palette[c - 1] : defaultColor(c);
            model.set(x, z, sy - 1 - y, 0xFF000000 | (color & 0xFFFFFF));
        }
        return model;
    }

    private static int defaultColor(int index) {
        float hue = (index * 0.61803f) % 1f;
        return java.awt.Color.HSBtoRGB(hue, 0.6f, 0.85f);
    }
}
