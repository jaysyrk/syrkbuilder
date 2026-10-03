package dev.syrkbuilder.core.noise;

import java.util.List;
import java.util.Locale;

public enum NoiseKind {
    SIMPLEX("smooth, even rolling relief"),
    FRACTAL("classic layered noise: hills with finer detail"),
    BILLOWY("rounded lumps with creased valleys: dunes, hummocks, clouds"),
    RIDGED("sharp crests and ridge lines: mountains");

    public static final List<String> NAMES = List.of("simplex", "fractal", "billowy", "ridged");

    public final String description;

    NoiseKind(String description) {
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NoiseKind byName(String name) {
        for (NoiseKind k : values()) {
            if (k.id().equalsIgnoreCase(name)) {
                return k;
            }
        }
        return null;
    }
}
