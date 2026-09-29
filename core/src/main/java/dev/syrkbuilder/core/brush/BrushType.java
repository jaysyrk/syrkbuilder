package dev.syrkbuilder.core.brush;

import java.util.List;
import java.util.Locale;

public enum BrushType {
    SPHERE(Blocks.REQUIRED, -1, 0, List.of(), "place a ball of blocks"),
    ERASE(Blocks.NONE, -1, 0, List.of(), "remove blocks in a ball"),
    PAINT(Blocks.REQUIRED, -1, 0, List.of(), "recolour only exposed surfaces"),
    SPLATTER(Blocks.REQUIRED, -1, 0, List.of("density", "scale"), "recolour surfaces in noisy patches (density= coverage)"),
    REPLACE(Blocks.REQUIRED, -1, 0, List.of("from"), "swap blocks matching from= in a ball"),
    OVERLAY(Blocks.REQUIRED, -1, 0, List.of("depth"), "add a layer on the ground (depth=, -r replaces the top instead)"),
    SCATTER(Blocks.REQUIRED, -1, 0, List.of("density", "height"), "sprinkle blocks/plants on the ground (density=, height=)"),
    FILL(Blocks.REQUIRED, -1, 0, List.of(), "fill empty space below where you aim (lakes)"),
    TREES(Blocks.NONE, -1, 0, List.of("density", "type"), "plant a forest (type=oak,birch or mix, density=)"),
    BLOB(Blocks.REQUIRED, 0.5, 1, List.of("scale"), "lumpy, organic ball - rocks, clouds, bushes (strength= lumpiness)"),
    CARVE(Blocks.NONE, 0.5, 1, List.of("scale"), "hollow out a lumpy, cave-like hole (strength= lumpiness)"),
    SCULPT(Blocks.OPTIONAL, 0.5, 1, List.of(), "3D smooth: rounds corners, fills dents, works on cliffs and overhangs"),
    INFLATE(Blocks.OPTIONAL, -1, 0, List.of(), "grow surfaces by a layer (uses the touching block unless you give one)"),
    DEFLATE(Blocks.NONE, -1, 0, List.of(), "shave a layer off surfaces"),
    ROUGHEN(Blocks.OPTIONAL, 0.5, 1, List.of("scale"), "3D noise on surfaces: bumps and pits on cliffs, rocks, models"),
    DECAY(Blocks.NONE, -1, 0, List.of("density"), "crumble exposed blocks away - ruins and weathering (density= chance)"),
    SPIKES(Blocks.REQUIRED, 8, 64, List.of("density"), "tapered spikes out of the ground (strength= height)"),
    RAISE(Blocks.NONE, 3, 64, List.of(), "push terrain up (strength= blocks)"),
    LOWER(Blocks.NONE, 3, 64, List.of(), "push terrain down (strength= blocks)"),
    SMOOTH(Blocks.NONE, 0.5, 1, List.of(), "even out bumpy terrain (strength= 0-1)"),
    FLATTEN(Blocks.NONE, 0.5, 1, List.of(), "pull terrain to the height you aim at (strength= 0-1)"),
    NOISE(Blocks.NONE, 3, 64, List.of("scale"), "roughen terrain (strength= blocks, scale=)"),
    CRATER(Blocks.NONE, 4, 64, List.of(), "dig a bowl with a raised rim (strength= depth)"),
    TERRACE(Blocks.NONE, 4, 32, List.of(), "cut terrain into steps (strength= step height)"),
    MELT(Blocks.NONE, -1, 0, List.of(), "erode sharp edges into slopes"),
    STAMP(Blocks.NONE, -1, 0, List.of(), "paint your clipboard where you aim, randomly turned (-r keeps it straight)");

    public enum Blocks { REQUIRED, OPTIONAL, NONE }

    public final Blocks blocks;
    public final boolean needsBlocks;
    public final double defaultStrength;
    public final double maxStrength;
    public final List<String> options;
    public final String description;

    BrushType(Blocks blocks, double defaultStrength, double maxStrength, List<String> options, String description) {
        this.blocks = blocks;
        this.needsBlocks = blocks != Blocks.NONE;
        this.defaultStrength = defaultStrength;
        this.maxStrength = maxStrength;
        this.options = options;
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean usesStrength() {
        return defaultStrength >= 0;
    }

    public boolean strengthInBlocks() {
        return maxStrength > 1;
    }

    public boolean terrain() {
        return switch (this) {
            case RAISE, LOWER, SMOOTH, FLATTEN, NOISE, MELT, CRATER, TERRACE -> true;
            default -> false;
        };
    }

    public boolean voxel() {
        return switch (this) {
            case BLOB, CARVE, SCULPT, INFLATE, DEFLATE, ROUGHEN, DECAY, SPLATTER -> true;
            default -> false;
        };
    }

    public int maxRadius() {
        return voxel() ? 32 : 64;
    }

    public static BrushType byName(String name) {
        for (BrushType t : values()) {
            if (t.id().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
