package dev.syrkbuilder.fabric;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

final class Settings {
    static boolean noclipHold;
    static double flySpeed = 1.0;
    static double lookSensitivity = 1.0;
    static boolean selectionParticles = true;
    static boolean previewParticles = true;
    static boolean pathParticles = true;
    static boolean wand = true;
    static boolean previewByDefault = true;
    static boolean quietChat;
    static boolean aimPointer = true;
    static boolean aimHintSeen;

    private static boolean loaded;

    private Settings() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("syrkbuilder.properties");
    }

    static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = file();
        if (!Files.isRegularFile(f)) {
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        noclipHold = bool(p, "noclip-hold", noclipHold);
        flySpeed = number(p, "fly-speed", flySpeed, 0.25, 8);
        lookSensitivity = number(p, "look-sensitivity", lookSensitivity, 0.1, 4);
        selectionParticles = bool(p, "selection-particles", selectionParticles);
        previewParticles = bool(p, "preview-particles", previewParticles);
        pathParticles = bool(p, "path-particles", pathParticles);
        wand = bool(p, "wand", wand);
        previewByDefault = bool(p, "preview", previewByDefault);
        quietChat = bool(p, "quiet-chat", quietChat);
        aimPointer = bool(p, "aim-pointer", aimPointer);
        aimHintSeen = bool(p, "aim-hint-seen", aimHintSeen);
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("noclip-hold", String.valueOf(noclipHold));
        p.setProperty("fly-speed", String.valueOf(flySpeed));
        p.setProperty("look-sensitivity", String.valueOf(lookSensitivity));
        p.setProperty("selection-particles", String.valueOf(selectionParticles));
        p.setProperty("preview-particles", String.valueOf(previewParticles));
        p.setProperty("path-particles", String.valueOf(pathParticles));
        p.setProperty("wand", String.valueOf(wand));
        p.setProperty("preview", String.valueOf(previewByDefault));
        p.setProperty("quiet-chat", String.valueOf(quietChat));
        p.setProperty("aim-pointer", String.valueOf(aimPointer));
        p.setProperty("aim-hint-seen", String.valueOf(aimHintSeen));
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                p.store(w, "SyrkBuilder");
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static double number(Properties p, String key, double def, double min, double max) {
        try {
            return Math.max(min, Math.min(max, Double.parseDouble(p.getProperty(key, String.valueOf(def)).trim())));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
