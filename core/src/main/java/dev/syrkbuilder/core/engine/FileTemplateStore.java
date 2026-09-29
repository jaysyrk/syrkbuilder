package dev.syrkbuilder.core.engine;

import dev.syrkbuilder.core.grid.BlockGrid;
import dev.syrkbuilder.core.grid.GridCodec;
import dev.syrkbuilder.core.grid.Schematics;
import dev.syrkbuilder.core.session.TemplateStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

public final class FileTemplateStore implements TemplateStore {
    static final List<String> EXTENSIONS = List.of(".sbt", ".schem", ".litematic");

    private final File dir;
    private final List<File> extraDirs = new CopyOnWriteArrayList<>();
    private final IntSupplier dataVersion;

    public FileTemplateStore(File dir, IntSupplier dataVersion) {
        this.dir = dir;
        this.dataVersion = dataVersion;
    }

    public FileTemplateStore(File dir) {
        this(dir, () -> Schematics.DEFAULT_DATA_VERSION);
    }

    public void addFolder(File folder) {
        if (folder != null && !extraDirs.contains(folder)) {
            extraDirs.add(folder);
        }
    }

    static String nameOf(String fileName) {
        String base = fileName;
        for (String ext : EXTENSIONS) {
            if (base.toLowerCase(Locale.ROOT).endsWith(ext)) {
                base = base.substring(0, base.length() - ext.length());
                break;
            }
        }
        String n = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    private static boolean isTemplate(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private List<File> folders() {
        List<File> all = new ArrayList<>();
        all.add(dir);
        all.addAll(extraDirs);
        return all;
    }

    File find(String name) {
        for (File folder : folders()) {
            File[] files = folder.listFiles((d, n) -> isTemplate(n) && nameOf(n).equals(name));
            if (files == null || files.length == 0) {
                continue;
            }
            File best = files[0];
            for (File f : files) {
                if (f.getName().endsWith(".sbt")) {
                    best = f;
                }
            }
            return best;
        }
        return null;
    }

    @Override
    public void save(String name, BlockGrid grid) throws IOException {
        dir.mkdirs();
        Files.write(new File(dir, name + ".sbt").toPath(), GridCodec.encode(grid));
    }

    @Override
    public String export(String name, BlockGrid grid, Schematics.Format format) throws IOException {
        dir.mkdirs();
        byte[] data = format == Schematics.Format.SPONGE
            ? Schematics.writeSponge(grid, dataVersion.getAsInt())
            : Schematics.writeLitematic(grid, name, dataVersion.getAsInt());
        File f = new File(dir, name + "." + format.extension);
        Files.write(f.toPath(), data);
        return f.getName();
    }

    @Override
    public BlockGrid load(String name) throws IOException {
        File f = find(name);
        if (f == null) {
            return null;
        }
        byte[] data = Files.readAllBytes(f.toPath());
        return f.getName().endsWith(".sbt") ? GridCodec.decode(data) : Schematics.read(data);
    }

    @Override
    public List<String> list() {
        TreeSet<String> names = new TreeSet<>();
        for (File folder : folders()) {
            File[] files = folder.listFiles((d, n) -> isTemplate(n));
            if (files != null) {
                for (File f : files) {
                    names.add(nameOf(f.getName()));
                }
            }
        }
        return new ArrayList<>(names);
    }

    @Override
    public boolean delete(String name) {
        boolean any = false;
        File[] files = dir.listFiles((d, n) -> isTemplate(n) && nameOf(n).equals(name));
        if (files != null) {
            for (File f : files) {
                any |= f.delete();
            }
        }
        return any;
    }
}
