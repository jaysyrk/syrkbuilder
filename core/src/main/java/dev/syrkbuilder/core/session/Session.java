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
    private dev.syrkbuilder.core.edit.NoiseMask noiseMask;
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

    private final Map<Integer, dev.syrkbuilder.core.edit.Box> strokeFrames = new LinkedHashMap<>();

    public dev.syrkbuilder.core.edit.Box strokeFrame(int stroke, dev.syrkbuilder.core.edit.Box first) {
        dev.syrkbuilder.core.edit.Box frame = strokeFrames.computeIfAbsent(stroke, k -> first);
        while (strokeFrames.size() > 16) {
            strokeFrames.remove(strokeFrames.keySet().iterator().next());
        }
        return frame;
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

    public boolean maskAllows(int x, int y, int z, String blockId) {
        return maskAllows(blockId) && (noiseMask == null || noiseMask.allows(x, y, z));
    }

    public dev.syrkbuilder.core.edit.NoiseMask noiseMask() {
        return noiseMask;
    }

    public void noiseMask(dev.syrkbuilder.core.edit.NoiseMask noiseMask) {
        this.noiseMask = noiseMask;
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
