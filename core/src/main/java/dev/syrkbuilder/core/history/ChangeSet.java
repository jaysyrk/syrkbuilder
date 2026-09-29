package dev.syrkbuilder.core.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChangeSet<B> {
    private final String label;
    private final long createdAt;
    private final List<B> palette = new ArrayList<>();
    private final Map<B, Integer> paletteIndex = new HashMap<>();
    private long[] positions = new long[256];
    private int[] before = new int[256];
    private int[] after = new int[256];
    private int size;

    public ChangeSet(String label) {
        this.label = label;
        this.createdAt = System.currentTimeMillis();
    }

    public String label() {
        return label;
    }

    public long createdAt() {
        return createdAt;
    }

    public int size() {
        return size;
    }

    public List<B> palette() {
        return palette;
    }

    public long packedPosition(int i) {
        return positions[i];
    }

    public int beforeIndex(int i) {
        return before[i];
    }

    public int afterIndex(int i) {
        return after[i];
    }

    static <B> ChangeSet<B> restore(String label, long createdAt, List<B> palette, long[] positions, int[] before, int[] after) {
        ChangeSet<B> c = new ChangeSet<>(label, createdAt);
        for (B state : palette) {
            c.paletteIndex.putIfAbsent(state, c.palette.size());
            c.palette.add(state);
        }
        c.positions = positions;
        c.before = before;
        c.after = after;
        c.size = positions.length;
        return c;
    }

    private ChangeSet(String label, long createdAt) {
        this.label = label;
        this.createdAt = createdAt;
    }

    public <C> ChangeSet<C> mapPalette(java.util.function.Function<B, C> convert) {
        List<C> mapped = new ArrayList<>(palette.size());
        for (B state : palette) {
            mapped.add(convert.apply(state));
        }
        return ChangeSet.restore(label, createdAt, mapped, Arrays.copyOf(positions, size), Arrays.copyOf(before, size), Arrays.copyOf(after, size));
    }

    public void appendAll(ChangeSet<B> other) {
        for (int i = 0; i < other.size; i++) {
            long p = other.positions[i];
            record(unpackX(p), unpackY(p), unpackZ(p), other.palette.get(other.before[i]), other.palette.get(other.after[i]));
        }
    }

    public interface BoxVisitor<B> {
        void visit(int x, int y, int z, B before, B after);
    }

    public void forEach(BoxVisitor<B> visitor) {
        for (int i = 0; i < size; i++) {
            long p = positions[i];
            visitor.visit(unpackX(p), unpackY(p), unpackZ(p), palette.get(before[i]), palette.get(after[i]));
        }
    }

    public void record(int x, int y, int z, B oldState, B newState) {
        if (size == positions.length) {
            int n = size * 2;
            positions = Arrays.copyOf(positions, n);
            before = Arrays.copyOf(before, n);
            after = Arrays.copyOf(after, n);
        }
        positions[size] = pack(x, y, z);
        before[size] = intern(oldState);
        after[size] = intern(newState);
        size++;
    }

    private int intern(B state) {
        Integer idx = paletteIndex.get(state);
        if (idx == null) {
            idx = palette.size();
            palette.add(state);
            paletteIndex.put(state, idx);
        }
        return idx;
    }

    @FunctionalInterface
    public interface Applier<B> {
        void apply(int x, int y, int z, B state);
    }

    public int replay(boolean undo, int from, int max, Applier<B> applier) {
        int done = 0;
        int i = from;
        while (i < size && done < max) {
            int k = undo ? size - 1 - i : i;
            long p = positions[k];
            applier.apply(unpackX(p), unpackY(p), unpackZ(p), palette.get(undo ? before[k] : after[k]));
            i++;
            done++;
        }
        return i < size ? i : -1;
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int unpackX(long p) {
        return (int) (p >> 38);
    }

    public static int unpackZ(long p) {
        return (int) (p << 26 >> 38);
    }

    public static int unpackY(long p) {
        return (int) (p << 52 >> 52);
    }
}
