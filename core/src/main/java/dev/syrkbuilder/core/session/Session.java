package dev.syrkbuilder.core.session;

import dev.syrkbuilder.core.grid.BlockGrid;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Session {
    private BlockGrid clipboard;
    private final Map<String, int[]> markers = new LinkedHashMap<>();
    private final Uploads uploads;
    private java.util.Set<String> mask;
    private boolean maskInverted;
    private String symmetry;
    private int[] symmetryOrigin;

    public Session(long maxUploadBytes) {
        this.uploads = new Uploads(maxUploadBytes);
    }

    public BlockGrid clipboard() {
        return clipboard;
    }

    public void clipboard(BlockGrid grid) {
        this.clipboard = grid;
    }

    public Map<String, int[]> markers() {
        return markers;
    }

    public Uploads uploads() {
        return uploads;
    }

    public void mask(java.util.Set<String> ids, boolean inverted) {
        this.mask = ids;
        this.maskInverted = inverted;
    }

    private final java.util.List<int[]> path = new java.util.ArrayList<>();

    public java.util.List<int[]> path() {
        return path;
    }

    private boolean preview = true;

    public boolean preview() {
        return preview;
    }

    public void preview(boolean on) {
        preview = on;
    }

    public java.util.Set<String> mask() {
        return mask;
    }

    public boolean maskInverted() {
        return maskInverted;
    }

    public boolean maskAllows(String blockId) {
        return mask == null || mask.contains(blockId) != maskInverted;
    }

    public void symmetry(String axes, int[] origin) {
        this.symmetry = axes;
        this.symmetryOrigin = origin;
    }

    public String symmetry() {
        return symmetry;
    }

    public int[] symmetryOrigin() {
        return symmetryOrigin;
    }
}
