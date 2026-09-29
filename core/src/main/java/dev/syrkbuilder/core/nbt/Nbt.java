package dev.syrkbuilder.core.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Nbt {
    public static final byte END = 0;
    public static final byte BYTE = 1;
    public static final byte SHORT = 2;
    public static final byte INT = 3;
    public static final byte LONG = 4;
    public static final byte FLOAT = 5;
    public static final byte DOUBLE = 6;
    public static final byte BYTE_ARRAY = 7;
    public static final byte STRING = 8;
    public static final byte LIST = 9;
    public static final byte COMPOUND = 10;
    public static final byte INT_ARRAY = 11;
    public static final byte LONG_ARRAY = 12;

    private static final int MAX_DEPTH = 512;
    private static final int MAX_ARRAY = 64 * 1024 * 1024;

    private Nbt() {
    }

    public record ListTag(byte type, List<Object> items) {
        public static ListTag of(byte type) {
            return new ListTag(type, new ArrayList<>());
        }
    }

    public record Root(String name, Map<String, Object> tag) {
    }

    public static Root read(byte[] data) throws IOException {
        InputStream in = new ByteArrayInputStream(data);
        if (data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            in = new GZIPInputStream(in);
        }
        DataInputStream din = new DataInputStream(new java.io.BufferedInputStream(in));
        byte type = din.readByte();
        if (type != COMPOUND) {
            throw new IOException("Not an NBT file (root tag type " + type + ")");
        }
        String name = din.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> tag = (Map<String, Object>) readPayload(din, COMPOUND, 0);
        return new Root(name, tag);
    }

    public static byte[] write(String rootName, Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(COMPOUND);
            out.writeUTF(rootName);
            writePayload(out, root);
        }
        return bytes.toByteArray();
    }

    private static Object readPayload(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nested too deeply");
        }
        switch (type) {
            case BYTE:
                return in.readByte();
            case SHORT:
                return in.readShort();
            case INT:
                return in.readInt();
            case LONG:
                return in.readLong();
            case FLOAT:
                return in.readFloat();
            case DOUBLE:
                return in.readDouble();
            case BYTE_ARRAY: {
                byte[] a = new byte[length(in)];
                in.readFully(a);
                return a;
            }
            case STRING:
                return in.readUTF();
            case LIST: {
                byte elem = in.readByte();
                int n = length(in);
                List<Object> items = new ArrayList<>(Math.min(n, 4096));
                for (int i = 0; i < n; i++) {
                    items.add(readPayload(in, elem, depth + 1));
                }
                return new ListTag(elem, items);
            }
            case COMPOUND: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    byte t = in.readByte();
                    if (t == END) {
                        return map;
                    }
                    String key = in.readUTF();
                    map.put(key, readPayload(in, t, depth + 1));
                }
            }
            case INT_ARRAY: {
                int[] a = new int[length(in)];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case LONG_ARRAY: {
                long[] a = new long[length(in)];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > MAX_ARRAY) {
            throw new IOException("Bad NBT length " + n);
        }
        return n;
    }

    static byte typeOf(Object v) {
        if (v instanceof Byte || v instanceof Boolean) {
            return BYTE;
        } else if (v instanceof Short) {
            return SHORT;
        } else if (v instanceof Integer) {
            return INT;
        } else if (v instanceof Long) {
            return LONG;
        } else if (v instanceof Float) {
            return FLOAT;
        } else if (v instanceof Double) {
            return DOUBLE;
        } else if (v instanceof byte[]) {
            return BYTE_ARRAY;
        } else if (v instanceof String) {
            return STRING;
        } else if (v instanceof ListTag) {
            return LIST;
        } else if (v instanceof Map) {
            return COMPOUND;
        } else if (v instanceof int[]) {
            return INT_ARRAY;
        } else if (v instanceof long[]) {
            return LONG_ARRAY;
        }
        throw new IllegalArgumentException("Can't write " + v.getClass() + " as NBT");
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        switch (typeOf(v)) {
            case BYTE -> out.writeByte(v instanceof Boolean b ? (b ? 1 : 0) : (Byte) v);
            case SHORT -> out.writeShort((Short) v);
            case INT -> out.writeInt((Integer) v);
            case LONG -> out.writeLong((Long) v);
            case FLOAT -> out.writeFloat((Float) v);
            case DOUBLE -> out.writeDouble((Double) v);
            case BYTE_ARRAY -> {
                byte[] a = (byte[]) v;
                out.writeInt(a.length);
                out.write(a);
            }
            case STRING -> out.writeUTF((String) v);
            case LIST -> {
                ListTag list = (ListTag) v;
                out.writeByte(list.items().isEmpty() && list.type() == END ? END : list.type());
                out.writeInt(list.items().size());
                for (Object item : list.items()) {
                    writePayload(out, item);
                }
            }
            case COMPOUND -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF(e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(END);
            }
            case INT_ARRAY -> {
                int[] a = (int[]) v;
                out.writeInt(a.length);
                for (int i : a) {
                    out.writeInt(i);
                }
            }
            default -> {
                long[] a = (long[]) v;
                out.writeInt(a.length);
                for (long l : a) {
                    out.writeLong(l);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> tag, String key) {
        Object v = tag.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    public static int intValue(Map<String, Object> tag, String key, int def) {
        Object v = tag.get(key);
        if (v instanceof Short s) {
            return s & 0xFFFF;
        }
        return v instanceof Number n ? n.intValue() : def;
    }

    public static String string(Map<String, Object> tag, String key) {
        Object v = tag.get(key);
        return v instanceof String s ? s : null;
    }

    public static ListTag list(Map<String, Object> tag, String key) {
        Object v = tag.get(key);
        return v instanceof ListTag l ? l : null;
    }
}
