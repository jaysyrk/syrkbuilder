package dev.syrkbuilder.core.grid;

import dev.syrkbuilder.core.history.HistoryCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GridCodec {
    private static final int MAGIC = 0x53424731;
    public static final long MAX_VOLUME = 64L * 1024 * 1024;

    private GridCodec() {
    }

    public static byte[] encode(BlockGrid grid) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeInt(MAGIC);
                out.writeInt(grid.sizeX());
                out.writeInt(grid.sizeY());
                out.writeInt(grid.sizeZ());
                out.writeInt(grid.palette().size());
                for (String block : grid.palette()) {
                    out.writeUTF(block);
                }
                long volume = grid.volume();
                int i = 0;
                while (i < volume) {
                    int v = grid.rawCell(i);
                    int run = 1;
                    while (i + run < volume && grid.rawCell(i + run) == v) {
                        run++;
                    }
                    HistoryCodec.writeVarInt(out, run);
                    HistoryCodec.writeVarInt(out, v + 1);
                    i += run;
                }
                out.writeInt(grid.markers().size());
                for (Map.Entry<String, int[]> m : grid.markers().entrySet()) {
                    out.writeUTF(m.getKey());
                    out.writeInt(m.getValue()[0]);
                    out.writeInt(m.getValue()[1]);
                    out.writeInt(m.getValue()[2]);
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static BlockGrid decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not a SyrkBuilder grid");
            }
            int sx = in.readInt();
            int sy = in.readInt();
            int sz = in.readInt();
            if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > MAX_VOLUME) {
                throw new IOException("Grid size " + sx + "x" + sy + "x" + sz + " is too big");
            }
            BlockGrid grid = new BlockGrid(sx, sy, sz);
            int paletteSize = in.readInt();
            for (int i = 0; i < paletteSize; i++) {
                grid.addPalette(in.readUTF());
            }
            long volume = grid.volume();
            int i = 0;
            while (i < volume) {
                int run = HistoryCodec.readVarInt(in);
                int v = HistoryCodec.readVarInt(in) - 1;
                if (run <= 0 || i + run > volume || v >= paletteSize) {
                    throw new IOException("Corrupt grid data");
                }
                for (int k = 0; k < run; k++) {
                    grid.setRawCell(i + k, v);
                }
                i += run;
            }
            int markers = in.readInt();
            for (int k = 0; k < markers; k++) {
                grid.markers().put(in.readUTF(), new int[]{in.readInt(), in.readInt(), in.readInt()});
            }
            return grid;
        }
    }
}
