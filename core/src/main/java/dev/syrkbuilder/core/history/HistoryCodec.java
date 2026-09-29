package dev.syrkbuilder.core.history;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class HistoryCodec {
    private static final int CHANGE_MAGIC = 0x53424331;
    private static final int INDEX_MAGIC = 0x53424931;

    private HistoryCodec() {
    }

    public interface StateCodec<B> {
        String encode(B state);

        B decode(String raw);
    }

    public static <B> void writeChange(DataOutputStream out, ChangeSet<B> c, StateCodec<B> codec) throws IOException {
        out.writeInt(CHANGE_MAGIC);
        out.writeUTF(c.label());
        out.writeLong(c.createdAt());
        out.writeInt(c.palette().size());
        for (B state : c.palette()) {
            out.writeUTF(codec.encode(state));
        }
        out.writeInt(c.size());
        for (int i = 0; i < c.size(); i++) {
            out.writeLong(c.packedPosition(i));
            writeVarInt(out, c.beforeIndex(i));
            writeVarInt(out, c.afterIndex(i));
        }
    }

    public static <B> ChangeSet<B> readChange(DataInputStream in, StateCodec<B> codec) throws IOException {
        if (in.readInt() != CHANGE_MAGIC) {
            throw new IOException("Not a SyrkBuilder change file");
        }
        String label = in.readUTF();
        long createdAt = in.readLong();
        int paletteSize = in.readInt();
        List<B> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(codec.decode(in.readUTF()));
        }
        int size = in.readInt();
        long[] positions = new long[size];
        int[] before = new int[size];
        int[] after = new int[size];
        for (int i = 0; i < size; i++) {
            positions[i] = in.readLong();
            before[i] = readVarInt(in);
            after[i] = readVarInt(in);
        }
        return ChangeSet.restore(label, createdAt, palette, positions, before, after);
    }

    public static void writeIndex(DataOutputStream out, List<HistoryTree.NodeInfo> nodes, int currentId) throws IOException {
        out.writeInt(INDEX_MAGIC);
        out.writeInt(currentId);
        out.writeInt(nodes.size());
        for (HistoryTree.NodeInfo n : nodes) {
            out.writeInt(n.id());
            out.writeInt(n.parent());
            out.writeInt(n.preferred());
            out.writeBoolean(n.checkpoint() != null);
            if (n.checkpoint() != null) {
                out.writeUTF(n.checkpoint());
            }
        }
    }

    public record Index(int currentId, List<HistoryTree.NodeInfo> nodes) {
    }

    public static Index readIndex(DataInputStream in) throws IOException {
        if (in.readInt() != INDEX_MAGIC) {
            throw new IOException("Not a SyrkBuilder history index");
        }
        int current = in.readInt();
        int count = in.readInt();
        List<HistoryTree.NodeInfo> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = in.readInt();
            int parent = in.readInt();
            int preferred = in.readInt();
            String checkpoint = in.readBoolean() ? in.readUTF() : null;
            nodes.add(new HistoryTree.NodeInfo(id, parent, checkpoint, preferred));
        }
        return new Index(current, nodes);
    }

    public static void writeVarInt(DataOutputStream out, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        }
    }
}
