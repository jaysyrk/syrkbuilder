package dev.syrkbuilder.core.terrain;

import java.util.Locale;

public enum TerrainType {
    MOUNTAIN(64, 60, 60, "alpine"),
    HILLS(48, 18, 30, "grassy"),
    MESA(48, 30, 15, "mesa"),
    VOLCANO(56, 55, 35, "volcanic"),
    CRATER(32, 14, 20, "rocky"),
    CANYON(64, 22, 25, "mesa"),
    DUNES(48, 8, 0, "desert"),
    ISLAND(24, 14, 25, "grassy");

    public final int defaultRadius;
    public final int defaultHeight;
    public final int defaultErosion;
    public final String defaultStyle;

    TerrainType(int defaultRadius, int defaultHeight, int defaultErosion, String defaultStyle) {
        this.defaultRadius = defaultRadius;
        this.defaultHeight = defaultHeight;
        this.defaultErosion = defaultErosion;
        this.defaultStyle = defaultStyle;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TerrainType byName(String name) {
        for (TerrainType t : values()) {
            if (t.id().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
