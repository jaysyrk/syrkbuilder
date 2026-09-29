package dev.syrkbuilder.core.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Pattern {
    private final List<String> blocks = new ArrayList<>();
    private final List<Double> cumulative = new ArrayList<>();
    private double total;
    private char gradient;
    private Box bounds;

    public static Pattern parse(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        for (String prefix : List.of("grad:", "grady:", "gradx:", "gradz:", "gradr:")) {
            if (lower.startsWith(prefix)) {
                Pattern g = parsePlain(raw.trim().substring(prefix.length()));
                g.gradient = prefix.length() == 5 ? 'y' : prefix.charAt(4);
                return g;
            }
        }
        return parsePlain(raw);
    }

    private static Pattern parsePlain(String raw) {
        Pattern p = new Pattern();
        for (String part : splitTopLevel(raw)) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            double weight = 1;
            int pct = part.indexOf('%');
            if (pct > 0 && part.indexOf('[') < 0 || pct > 0 && pct < part.indexOf('[')) {
                try {
                    weight = Double.parseDouble(part.substring(0, pct));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad weight in '" + part + "'");
                }
                part = part.substring(pct + 1);
            }
            if (weight <= 0) {
                continue;
            }
            p.total += weight;
            p.blocks.add(normalize(part));
            p.cumulative.add(p.total);
        }
        if (p.blocks.isEmpty()) {
            throw new IllegalArgumentException("Empty block pattern");
        }
        return p;
    }

    public static Pattern of(String block) {
        return parse(block);
    }

    public static String normalize(String id) {
        String s = id.trim();
        int bracket = s.indexOf('[');
        String name = (bracket < 0 ? s : s.substring(0, bracket)).toLowerCase(Locale.ROOT);
        String state = bracket < 0 ? "" : s.substring(bracket);
        if (name.indexOf(':') < 0) {
            name = "minecraft:" + name;
        }
        return name + state;
    }

    public static String baseId(String block) {
        int bracket = block.indexOf('[');
        return bracket < 0 ? block : block.substring(0, bracket);
    }

    private String pickGradient(int x, int y, int z) {
        if (bounds == null) {
            return blocks.get(0);
        }
        double t = switch (gradient) {
            case 'x' -> frac(x, bounds.minX(), bounds.maxX());
            case 'z' -> frac(z, bounds.minZ(), bounds.maxZ());
            case 'r' -> {
                double cx = (bounds.minX() + bounds.maxX()) / 2.0;
                double cy = (bounds.minY() + bounds.maxY()) / 2.0;
                double cz = (bounds.minZ() + bounds.maxZ()) / 2.0;
                double half = Math.max(1, Math.max(bounds.maxX() - cx, Math.max(bounds.maxY() - cy, bounds.maxZ() - cz)));
                double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
                yield Math.min(1, d / half);
            }
            default -> frac(y, bounds.minY(), bounds.maxY());
        };
        double scaled = t * (blocks.size() - 1) + dither(x, y, z);
        int idx = Math.max(0, Math.min(blocks.size() - 1, (int) Math.floor(scaled)));
        return blocks.get(idx);
    }

    private static double frac(int v, int min, int max) {
        return max <= min ? 0 : (v - min) / (double) (max - min);
    }

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private static double dither(int x, int y, int z) {
        int b = BAYER[Math.floorMod(x, 4) + 4 * Math.floorMod(z, 4)];
        int shifted = (b + 5 * Math.floorMod(y, 4)) % 16;
        return (shifted + 0.5) / 16.0;
    }

    private static List<String> splitTopLevel(String raw) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    public List<String> blocks() {
        return blocks;
    }

    public boolean isGradient() {
        return gradient != 0;
    }

    public Pattern bind(Box box) {
        this.bounds = box;
        return this;
    }

    public String pick(int x, int y, int z) {
        if (gradient != 0 && blocks.size() > 1) {
            return pickGradient(x, y, z);
        }
        if (blocks.size() == 1) {
            return blocks.get(0);
        }
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        double r = ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53) * total;
        for (int i = 0; i < cumulative.size(); i++) {
            if (r < cumulative.get(i)) {
                return blocks.get(i);
            }
        }
        return blocks.get(blocks.size() - 1);
    }
}
