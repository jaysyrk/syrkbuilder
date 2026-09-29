package dev.syrkbuilder.core.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

@SuppressWarnings("unchecked")
public final class GltfLoader {
    private final Map<String, Object> root;
    private final List<ByteBuffer> buffers = new ArrayList<>();
    private final Path dir;
    private final Map<Integer, Texture> textureCache = new HashMap<>();

    private GltfLoader(Map<String, Object> root, ByteBuffer glbBin, Path dir) throws IOException {
        this.root = root;
        this.dir = dir;
        for (Object o : list(root, "buffers")) {
            Map<String, Object> b = (Map<String, Object>) o;
            String uri = (String) b.get("uri");
            if (uri == null) {
                buffers.add(glbBin);
            } else {
                buffers.add(ByteBuffer.wrap(readUri(uri)).order(ByteOrder.LITTLE_ENDIAN));
            }
        }
    }

    public static Mesh load(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Map<String, Object> json;
        ByteBuffer bin = null;
        if (bytes.length >= 12 && bytes[0] == 'g' && bytes[1] == 'l' && bytes[2] == 'T' && bytes[3] == 'F') {
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            buf.position(12);
            String text = null;
            while (buf.remaining() >= 8) {
                int len = buf.getInt();
                int type = buf.getInt();
                byte[] chunk = new byte[len];
                buf.get(chunk);
                if (type == 0x4E4F534A) {
                    text = new String(chunk, StandardCharsets.UTF_8);
                } else if (type == 0x004E4942) {
                    bin = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
                }
            }
            if (text == null) {
                throw new IOException("GLB file has no JSON chunk");
            }
            json = (Map<String, Object>) Json.parse(text);
        } else {
            json = (Map<String, Object>) Json.parse(new String(bytes, StandardCharsets.UTF_8));
        }
        GltfLoader loader = new GltfLoader(json, bin, file.getParent());
        Mesh mesh = new Mesh();
        loader.addScene(mesh);
        return mesh;
    }

    private byte[] readUri(String uri) throws IOException {
        if (uri.startsWith("data:")) {
            return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
        }
        Path p = dir.resolve(java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8)).normalize();
        if (!p.startsWith(dir)) {
            throw new IOException("glTF refers to a file outside its folder");
        }
        return Files.readAllBytes(p);
    }

    private void addScene(Mesh mesh) throws IOException {
        List<Object> scenes = list(root, "scenes");
        List<Object> nodes;
        if (!scenes.isEmpty()) {
            int sceneIndex = root.get("scene") == null ? 0 : num(root.get("scene"));
            nodes = list((Map<String, Object>) scenes.get(sceneIndex), "nodes");
        } else {
            nodes = new ArrayList<>();
            List<Object> all = list(root, "nodes");
            boolean[] child = new boolean[all.size()];
            for (Object o : all) {
                for (Object c : list((Map<String, Object>) o, "children")) {
                    child[num(c)] = true;
                }
            }
            for (int k = 0; k < all.size(); k++) {
                if (!child[k]) {
                    nodes.add((double) k);
                }
            }
        }
        for (Object n : nodes) {
            addNode(mesh, num(n), identity(), 0);
        }
    }

    private void addNode(Mesh mesh, int index, double[] parent, int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("glTF node hierarchy is too deep");
        }
        Map<String, Object> node = (Map<String, Object>) list(root, "nodes").get(index);
        double[] m = multiply(parent, localMatrix(node));
        if (node.get("mesh") != null) {
            addMesh(mesh, num(node.get("mesh")), m);
        }
        for (Object c : list(node, "children")) {
            addNode(mesh, num(c), m, depth + 1);
        }
    }

    private void addMesh(Mesh mesh, int index, double[] m) throws IOException {
        Map<String, Object> gmesh = (Map<String, Object>) list(root, "meshes").get(index);
        for (Object o : list(gmesh, "primitives")) {
            Map<String, Object> prim = (Map<String, Object>) o;
            int mode = prim.get("mode") == null ? 4 : num(prim.get("mode"));
            if (mode != 4) {
                continue;
            }
            Map<String, Object> attrs = (Map<String, Object>) prim.get("attributes");
            float[] pos = readFloats(num(attrs.get("POSITION")));
            float[] uv = attrs.get("TEXCOORD_0") == null ? null : readFloats(num(attrs.get("TEXCOORD_0")));
            int[] idx = prim.get("indices") == null ? null : readInts(num(prim.get("indices")));
            int vertexCount = pos.length / 3;
            int triCount = idx == null ? vertexCount / 3 : idx.length / 3;

            int color = 0xFFFFFFFF;
            Texture texture = null;
            if (prim.get("material") != null) {
                Map<String, Object> mat = (Map<String, Object>) list(root, "materials").get(num(prim.get("material")));
                Map<String, Object> pbr = (Map<String, Object>) mat.get("pbrMetallicRoughness");
                if (pbr != null) {
                    List<Object> f = (List<Object>) pbr.get("baseColorFactor");
                    if (f != null && f.size() >= 3) {
                        color = argb(dbl(f.get(0)), dbl(f.get(1)), dbl(f.get(2)), f.size() > 3 ? dbl(f.get(3)) : 1);
                    }
                    Map<String, Object> tex = (Map<String, Object>) pbr.get("baseColorTexture");
                    if (tex != null && uv != null) {
                        texture = texture(num(tex.get("index")));
                    }
                }
            }
            for (int t = 0; t < triCount; t++) {
                float[] p = new float[9];
                float[] tuv = uv == null ? null : new float[6];
                for (int c = 0; c < 3; c++) {
                    int v = idx == null ? t * 3 + c : idx[t * 3 + c];
                    double x = pos[v * 3];
                    double y = pos[v * 3 + 1];
                    double z = pos[v * 3 + 2];
                    p[c * 3] = (float) (m[0] * x + m[4] * y + m[8] * z + m[12]);
                    p[c * 3 + 1] = (float) (m[1] * x + m[5] * y + m[9] * z + m[13]);
                    p[c * 3 + 2] = (float) (m[2] * x + m[6] * y + m[10] * z + m[14]);
                    if (tuv != null) {
                        tuv[c * 2] = uv[v * 2];
                        tuv[c * 2 + 1] = uv[v * 2 + 1];
                    }
                }
                mesh.add(new Mesh.Triangle(p, tuv, color, texture, true));
            }
        }
    }

    private Texture texture(int index) throws IOException {
        if (textureCache.containsKey(index)) {
            return textureCache.get(index);
        }
        Map<String, Object> tex = (Map<String, Object>) list(root, "textures").get(index);
        Texture result = null;
        if (tex.get("source") != null) {
            Map<String, Object> img = (Map<String, Object>) list(root, "images").get(num(tex.get("source")));
            byte[] data;
            if (img.get("bufferView") != null) {
                data = bufferView(num(img.get("bufferView")));
            } else {
                data = readUri((String) img.get("uri"));
            }
            var image = ImageIO.read(new ByteArrayInputStream(data));
            if (image != null) {
                result = Texture.of(image);
            }
        }
        textureCache.put(index, result);
        return result;
    }

    private byte[] bufferView(int index) {
        Map<String, Object> view = (Map<String, Object>) list(root, "bufferViews").get(index);
        ByteBuffer buf = buffers.get(num(view.get("buffer")));
        int offset = view.get("byteOffset") == null ? 0 : num(view.get("byteOffset"));
        int len = num(view.get("byteLength"));
        byte[] out = new byte[len];
        buf.duplicate().position(offset).get(out, 0, len);
        return out;
    }

    private static int componentSize(int type) {
        return switch (type) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            default -> 4;
        };
    }

    private static int components(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> throw new IllegalArgumentException("Unsupported accessor type " + type);
        };
    }

    private double read(ByteBuffer buf, int at, int componentType, boolean normalized) {
        return switch (componentType) {
            case 5126 -> buf.getFloat(at);
            case 5125 -> buf.getInt(at) & 0xFFFFFFFFL;
            case 5123 -> normalized ? (buf.getShort(at) & 0xFFFF) / 65535.0 : buf.getShort(at) & 0xFFFF;
            case 5121 -> normalized ? (buf.get(at) & 0xFF) / 255.0 : buf.get(at) & 0xFF;
            case 5122 -> normalized ? Math.max(-1, buf.getShort(at) / 32767.0) : buf.getShort(at);
            case 5120 -> normalized ? Math.max(-1, buf.get(at) / 127.0) : buf.get(at);
            default -> throw new IllegalArgumentException("Unsupported component type " + componentType);
        };
    }

    private double[] readAccessor(int index) {
        Map<String, Object> acc = (Map<String, Object>) list(root, "accessors").get(index);
        int count = num(acc.get("count"));
        int comps = components((String) acc.get("type"));
        int ctype = num(acc.get("componentType"));
        boolean normalized = Boolean.TRUE.equals(acc.get("normalized"));
        double[] out = new double[count * comps];
        if (acc.get("bufferView") == null) {
            return out;
        }
        Map<String, Object> view = (Map<String, Object>) list(root, "bufferViews").get(num(acc.get("bufferView")));
        ByteBuffer buf = buffers.get(num(view.get("buffer")));
        int base = (view.get("byteOffset") == null ? 0 : num(view.get("byteOffset"))) + (acc.get("byteOffset") == null ? 0 : num(acc.get("byteOffset")));
        int elem = comps * componentSize(ctype);
        int stride = view.get("byteStride") == null ? elem : num(view.get("byteStride"));
        for (int k = 0; k < count; k++) {
            for (int c = 0; c < comps; c++) {
                out[k * comps + c] = read(buf, base + k * stride + c * componentSize(ctype), ctype, normalized);
            }
        }
        return out;
    }

    private float[] readFloats(int accessor) {
        double[] d = readAccessor(accessor);
        float[] f = new float[d.length];
        for (int k = 0; k < d.length; k++) {
            f[k] = (float) d[k];
        }
        return f;
    }

    private int[] readInts(int accessor) {
        double[] d = readAccessor(accessor);
        int[] out = new int[d.length];
        for (int k = 0; k < d.length; k++) {
            out[k] = (int) d[k];
        }
        return out;
    }

    private static double[] identity() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] r = new double[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                double s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[k * 4 + row] * b[col * 4 + k];
                }
                r[col * 4 + row] = s;
            }
        }
        return r;
    }

    private static double[] localMatrix(Map<String, Object> node) {
        List<Object> matrix = (List<Object>) node.get("matrix");
        if (matrix != null && matrix.size() == 16) {
            double[] m = new double[16];
            for (int k = 0; k < 16; k++) {
                m[k] = dbl(matrix.get(k));
            }
            return m;
        }
        double[] t = vec(node.get("translation"), new double[]{0, 0, 0});
        double[] q = vec(node.get("rotation"), new double[]{0, 0, 0, 1});
        double[] s = vec(node.get("scale"), new double[]{1, 1, 1});
        double x = q[0];
        double y = q[1];
        double z = q[2];
        double w = q[3];
        double[] r = {
            1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
            2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
            2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0,
            0, 0, 0, 1
        };
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                r[col * 4 + row] *= s[col];
            }
        }
        r[12] = t[0];
        r[13] = t[1];
        r[14] = t[2];
        return r;
    }

    private static double[] vec(Object o, double[] def) {
        if (!(o instanceof List<?> l) || l.size() != def.length) {
            return def;
        }
        double[] v = new double[def.length];
        for (int k = 0; k < v.length; k++) {
            v[k] = dbl(l.get(k));
        }
        return v;
    }

    private static List<Object> list(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static int num(Object o) {
        return ((Number) o).intValue();
    }

    private static double dbl(Object o) {
        return ((Number) o).doubleValue();
    }

    private static int argb(double r, double g, double b, double a) {
        return (int) Math.round(Math.max(0, Math.min(1, a)) * 255) << 24
            | (int) Math.round(Math.max(0, Math.min(1, r)) * 255) << 16
            | (int) Math.round(Math.max(0, Math.min(1, g)) * 255) << 8
            | (int) Math.round(Math.max(0, Math.min(1, b)) * 255);
    }
}
