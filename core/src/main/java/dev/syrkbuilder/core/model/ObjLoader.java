package dev.syrkbuilder.core.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public final class ObjLoader {
    private record Material(int color, Texture texture) {
    }

    private ObjLoader() {
    }

    public static Mesh load(Path file) throws IOException {
        return load(file, new ArrayList<>());
    }

    public static Mesh load(Path file, List<String> warnings) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<Integer> vertexColors = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        Map<String, Material> materials = new HashMap<>();
        Material current = new Material(0xFFC8C8C8, null);
        Mesh mesh = new Mesh();
        boolean sawMtllib = false;
        java.util.Set<String> missingMaterials = new java.util.HashSet<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "v" -> {
                    positions.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    if (t.length >= 7) {
                        vertexColors.add(0xFF000000 | channel(t[4]) << 16 | channel(t[5]) << 8 | channel(t[6]));
                    } else {
                        vertexColors.add(null);
                    }
                }
                case "vt" -> uvs.add(new float[]{f(t[1]), t.length > 2 ? f(t[2]) : 0});
                case "mtllib" -> {
                    sawMtllib = true;
                    String name = line.substring(line.indexOf(' ') + 1).trim();
                    Path mtl = findSibling(file, name);
                    if (mtl == null) {
                        String base = file.getFileName().toString();
                        mtl = findSibling(file, base.substring(0, base.lastIndexOf('.')) + ".mtl");
                    }
                    if (mtl != null) {
                        materials.putAll(loadMtl(mtl, warnings));
                    } else {
                        warnings.add("Couldn't find the colours file '" + name + "' next to " + file.getFileName() + " - the model will be grey.");
                    }
                }
                case "usemtl" -> {
                    String name = line.substring(line.indexOf(' ') + 1).trim();
                    current = materials.get(name);
                    if (current == null) {
                        if (!materials.isEmpty() && missingMaterials.add(name)) {
                            warnings.add("Material '" + name + "' isn't in the .mtl file - that part will be grey.");
                        }
                        current = new Material(0xFFC8C8C8, null);
                    }
                }
                case "f" -> {
                    int n = t.length - 1;
                    int[] vi = new int[n];
                    int[] ti = new int[n];
                    for (int k = 0; k < n; k++) {
                        String[] parts = t[k + 1].split("/");
                        vi[k] = index(parts[0], positions.size());
                        ti[k] = parts.length > 1 && !parts[1].isEmpty() ? index(parts[1], uvs.size()) : -1;
                    }
                    for (int k = 1; k + 1 < n; k++) {
                        int[] corners = {0, k, k + 1};
                        float[] pos = new float[9];
                        float[] uv = new float[6];
                        boolean hasUv = true;
                        int color = current.color();
                        int vcR = 0;
                        int vcG = 0;
                        int vcB = 0;
                        int vcCount = 0;
                        for (int c = 0; c < 3; c++) {
                            float[] p = positions.get(vi[corners[c]]);
                            System.arraycopy(p, 0, pos, c * 3, 3);
                            if (ti[corners[c]] >= 0) {
                                float[] tc = uvs.get(ti[corners[c]]);
                                uv[c * 2] = tc[0];
                                uv[c * 2 + 1] = tc[1];
                            } else {
                                hasUv = false;
                            }
                            Integer vc = vertexColors.get(vi[corners[c]]);
                            if (vc != null) {
                                vcR += (vc >> 16) & 255;
                                vcG += (vc >> 8) & 255;
                                vcB += vc & 255;
                                vcCount++;
                            }
                        }
                        if (vcCount == 3 && current.texture() == null) {
                            color = 0xFF000000 | (vcR / 3) << 16 | (vcG / 3) << 8 | (vcB / 3);
                        }
                        mesh.add(new Mesh.Triangle(pos, hasUv ? uv : null, color, hasUv ? current.texture() : null, false));
                    }
                }
                default -> {
                }
            }
        }
        if (!sawMtllib && vertexColors.stream().allMatch(c -> c == null)) {
            warnings.add(file.getFileName() + " has no colours (no mtllib line) - the model will be grey.");
        }
        return mesh;
    }

    static Path findSibling(Path model, String name) {
        Path dir = model.toAbsolutePath().getParent();
        String clean = name.replace('\\', '/');
        if (clean.startsWith("./")) {
            clean = clean.substring(2);
        }
        Path exact = dir.resolve(clean).normalize();
        if (exact.startsWith(dir) && Files.isRegularFile(exact)) {
            return exact;
        }
        String wanted = clean.substring(clean.lastIndexOf('/') + 1);
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().equalsIgnoreCase(wanted) && Files.isRegularFile(f)).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, Material> loadMtl(Path file, List<String> warnings) throws IOException {
        Map<String, Material> out = new HashMap<>();
        String name = null;
        int color = 0xFFC8C8C8;
        Texture texture = null;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.replace("\uFEFF", "").trim();
            String[] t = line.split("\\s+");
            if (t[0].equals("newmtl")) {
                if (name != null) {
                    out.put(name, new Material(color, texture));
                }
                name = line.substring(line.indexOf(' ') + 1).trim();
                color = 0xFFC8C8C8;
                texture = null;
            } else if (t[0].equals("Kd") && t.length >= 4) {
                color = 0xFF000000 | channel(t[1]) << 16 | channel(t[2]) << 8 | channel(t[3]);
            } else if (t[0].equals("map_Kd") && t.length >= 2) {
                Path img = findSibling(file, t[t.length - 1]);
                if (img == null) {
                    warnings.add("Texture '" + t[t.length - 1] + "' not found next to " + file.getFileName() + " - using the plain colour.");
                } else {
                    var image = ImageIO.read(img.toFile());
                    if (image != null) {
                        texture = Texture.of(image);
                        color = 0xFFFFFFFF;
                    }
                }
            }
        }
        if (name != null) {
            out.put(name, new Material(color, texture));
        }
        return out;
    }

    private static int index(String raw, int count) {
        int i = Integer.parseInt(raw);
        int idx = i < 0 ? count + i : i - 1;
        if (idx < 0 || idx >= count) {
            throw new IllegalArgumentException("OBJ face refers to missing vertex " + raw);
        }
        return idx;
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    private static int channel(String s) {
        return Math.max(0, Math.min(255, Math.round(Float.parseFloat(s) * 255)));
    }
}
