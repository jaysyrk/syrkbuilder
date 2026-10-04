package dev.syrkbuilder.fabric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TerrainPresets {
    static final int FIELDS = 8;

    private static final Map<String, String[]> PRESETS = new LinkedHashMap<>();
    private static boolean loaded;

    private TerrainPresets() {
    }

    private static Path file() {
        return LocalFiles.root().resolve("terrain-presets.txt");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (Files.isRegularFile(file())) {
                for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                    int eq = line.indexOf('=');
                    if (eq <= 0 || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.substring(eq + 1).split("\\|", -1);
                    if (parts.length == FIELDS) {
                        PRESETS.put(line.substring(0, eq).trim(), parts);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder("# name=type|style|radius|height|erosion|roughness|peaks|seed (edit in the Terrain tab)\n");
        PRESETS.forEach((name, v) -> sb.append(name).append('=').append(String.join("|", v)).append('\n'));
        try {
            Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    static String clean(String name) {
        return name.replaceAll("[^A-Za-z0-9 _.-]", "").trim();
    }

    static List<String> names() {
        load();
        return new ArrayList<>(PRESETS.keySet());
    }

    static String[] get(String name) {
        load();
        return PRESETS.get(name);
    }

    static void put(String name, String[] values) {
        load();
        PRESETS.put(name, values);
        save();
    }

    static void remove(String name) {
        load();
        if (PRESETS.remove(name) != null) {
            save();
        }
    }
}
