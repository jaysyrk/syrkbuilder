package dev.syrkbuilder.core.noise;

import java.util.List;
import java.util.Locale;

public enum NoisePreset {
    ALPINE(NoiseKind.RIDGED, 26, 5, 38, "alpine", 0, 0.6),
    ROLLING(NoiseKind.FRACTAL, 34, 4, 12, "grassy", 0, 0.2),
    DUNES(NoiseKind.RIDGED, 18, 2, 8, "desert", 0, 0.4),
    MESA(NoiseKind.BILLOWY, 30, 3, 22, "mesa", 4, 0.7),
    ISLANDS(NoiseKind.BILLOWY, 40, 3, 20, "grassy", 0, 0.6),
    CRAGGY(NoiseKind.RIDGED, 14, 4, 18, "rocky", 0, 0.4);

    public static final List<String> NAMES = List.of("alpine", "rolling", "dunes", "mesa", "islands", "craggy");

    public final NoiseKind kind;
    public final double scale;
    public final int octaves;
    public final double amplitude;
    public final String style;
    public final int steps;
    // How far the relief sits above the aimed height, as a fraction of the amplitude, so mountains and islands rise out of the ground.
    public final double lift;

    NoisePreset(NoiseKind kind, double scale, int octaves, double amplitude, String style, int steps, double lift) {
        this.kind = kind;
        this.scale = scale;
        this.octaves = octaves;
        this.amplitude = amplitude;
        this.style = style;
        this.steps = steps;
        this.lift = lift;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NoisePreset byName(String name) {
        for (NoisePreset p : values()) {
            if (p.id().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}
