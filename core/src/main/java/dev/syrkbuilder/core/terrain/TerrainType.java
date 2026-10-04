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
    ISLAND(24, 14, 25, "grassy"),
    BUTTES(56, 28, 4, "mesa"),
    VALLEY(64, 26, 35, "grassy"),
    FJORD(64, 30, 25, "rocky"),
    LAKE(40, 8, 10, "coastal"),
    ATOLL(48, 8, 0, "coastal"),
    ARCHIPELAGO(80, 12, 15, "coastal"),
    SWAMP(48, 4, 0, "swamp");

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

    public boolean water() {
        return switch (this) {
            case VALLEY, FJORD, LAKE, ATOLL, ARCHIPELAGO, SWAMP -> true;
            default -> false;
        };
    }

    public String fluidBlock() {
        return this == VOLCANO ? "minecraft:lava" : "minecraft:water";
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
