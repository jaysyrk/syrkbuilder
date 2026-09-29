package dev.syrkbuilder.fabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

final class LocalFiles {
    private LocalFiles() {
    }

    static Path root() {
        Path p = FabricLoader.getInstance().getGameDir().resolve("syrkbuilder");
        try {
            Files.createDirectories(p);
        } catch (IOException ignored) {
        }
        return p;
    }

    static Path models() {
        return dir("models");
    }

    static Path heightmaps() {
        return dir("heightmaps");
    }

    static Path scripts() {
        return dir("scripts");
    }

    private static Path dir(String name) {
        Path p = root().resolve(name);
        try {
            Files.createDirectories(p);
        } catch (IOException ignored) {
        }
        return p;
    }

    static List<String> list(Path dir, String... extensions) {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(f -> {
                String n = f.getFileName().toString();
                for (String ext : extensions) {
                    if (n.toLowerCase(Locale.ROOT).endsWith(ext)) {
                        names.add(n);
                    }
                }
            });
        } catch (IOException ignored) {
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    static Path resolve(Path dir, String name) {
        Path p = dir.resolve(name).normalize();
        return p.startsWith(dir) ? p : null;
    }
}
