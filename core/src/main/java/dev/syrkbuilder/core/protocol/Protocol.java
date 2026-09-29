package dev.syrkbuilder.core.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Protocol {
    public static final String CHANNEL = "syrkbuilder:main";
    public static final int VERSION = 2;
    public static final int CHUNK_BYTES = 30000;

    private static final int TYPE_COMMAND = 1;
    private static final int TYPE_UPLOAD = 2;
    private static final int TYPE_DATA = 3;

    public static final char DATA_MARK = '\u0001';

    public record Data(String kind, String text) {
    }

    public static Data dataLine(String line) {
        if (line.isEmpty() || line.charAt(0) != DATA_MARK) {
            return null;
        }
        int split = line.indexOf(DATA_MARK, 1);
        return split < 0 ? null : new Data(line.substring(1, split), line.substring(split + 1));
    }

    public static String dataLine(String kind, String text) {
        return DATA_MARK + kind + DATA_MARK + text;
    }

    public static byte[] encodeData(Data d) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(TYPE_DATA);
            out.writeUTF(d.kind());
            byte[] text = d.text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(text.length);
            out.write(text);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Data decodeData(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readUnsignedByte() != VERSION || in.readUnsignedByte() != TYPE_DATA) {
                return null;
            }
            String kind = in.readUTF();
            int len = in.readInt();
            if (len < 0 || len > 4 * 1024 * 1024) {
                return null;
            }
            byte[] text = new byte[len];
            in.readFully(text);
            return new Data(kind, new String(text, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    private Protocol() {
    }

    public static byte[] encode(Request r) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(TYPE_COMMAND);
            out.writeUTF(r.command());
            writePos(out, r.target());
            writePos(out, r.pos1());
            writePos(out, r.pos2());
            writePos(out, r.feet());
            out.writeFloat(r.yaw());
            out.writeFloat(r.pitch());
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<byte[]> encodeUpload(int uploadId, byte[] data) {
        List<byte[]> packets = new ArrayList<>();
        int total = Math.max(1, (data.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        for (int i = 0; i < total; i++) {
            int from = i * CHUNK_BYTES;
            int len = Math.min(CHUNK_BYTES, data.length - from);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(len + 16);
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeByte(VERSION);
                out.writeByte(TYPE_UPLOAD);
                out.writeInt(uploadId);
                out.writeShort(i);
                out.writeShort(total);
                out.writeInt(Math.max(0, len));
                out.write(data, from, Math.max(0, len));
                packets.add(bytes.toByteArray());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return packets;
    }

    public static Object decode(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int version = in.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("Mod/plugin version mismatch (protocol " + version + ", server expects " + VERSION + ")");
        }
        int type = in.readUnsignedByte();
        if (type == TYPE_UPLOAD) {
            int id = in.readInt();
            int index = in.readUnsignedShort();
            int total = in.readUnsignedShort();
            int len = in.readInt();
            if (len < 0 || len > CHUNK_BYTES) {
                throw new IOException("Bad upload chunk length");
            }
            byte[] bytes = new byte[len];
            in.readFully(bytes);
            return new UploadChunk(id, index, total, bytes);
        }
        if (type != TYPE_COMMAND) {
            throw new IOException("Unknown message type " + type);
        }
        String command = in.readUTF();
        int[] target = readPos(in);
        int[] pos1 = readPos(in);
        int[] pos2 = readPos(in);
        int[] feet = readPos(in);
        return new Request(command, target, pos1, pos2, feet, in.readFloat(), in.readFloat());
    }

    private static void writePos(DataOutputStream out, int[] p) throws IOException {
        out.writeBoolean(p != null);
        if (p != null) {
            out.writeInt(p[0]);
            out.writeInt(p[1]);
            out.writeInt(p[2]);
        }
    }

    private static int[] readPos(DataInputStream in) throws IOException {
        return in.readBoolean() ? new int[]{in.readInt(), in.readInt(), in.readInt()} : null;
    }
}
