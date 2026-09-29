package dev.syrkbuilder.core.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Pattern {
    private static final String DIRECTIONS = "up, down, east, west, north, south, out, in, look, or x/y/z like 1/0/1";

    private final List<String> blocks = new ArrayList<>();
    private final List<Double> cumulative = new ArrayList<>();
    private double total;
    private boolean gradient;
    private double dirX;
    private double dirY = 1;
    private double dirZ;
    private boolean axisAligned = true;
    private int radial;
    private boolean ranged;
    private double rangeFrom;
    private double rangeTo;
    private Box bounds;

    public static Pattern parse(String raw) {
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("grad(")) {
            int close = trimmed.indexOf(')');
            if (close < 0 || close + 1 >= trimmed.length() || trimmed.charAt(close + 1) != ':') {
                throw new IllegalArgumentException("Gradient options go in brackets before a colon, e.g. grad(down):stone,snow_block");
            }
            Pattern g = parsePlain(trimmed.substring(close + 2));
            g.gradient = true;
            g.options(lower.substring(5, close));
            return g;
        }
        for (String prefix : List.of("grad:", "grady:", "gradx:", "gradz:", "gradr:")) {
            if (lower.startsWith(prefix)) {
                Pattern g = parsePlain(trimmed.substring(prefix.length()));
                g.gradient = true;
                g.direction(prefix.length() == 5 ? "y" : String.valueOf(prefix.charAt(4)));
                return g;
            }
        }
        return parsePlain(raw);
    }

    private void options(String opts) {
        for (String part : opts.split(",")) {
            String o = part.trim();
            if (o.isEmpty()) {
                continue;
            }
            int dots = o.indexOf("..");
            if (dots > 0) {
                try {
                    rangeFrom = Double.parseDouble(o.substring(0, dots));
                    rangeTo = Double.parseDouble(o.substring(dots + 2));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("A gradient range looks like 60..90, not '" + o + "'");
                }
                ranged = true;
            } else {
                direction(o);
            }
        }
    }

    private void direction(String d) {
        radial = 0;
        axisAligned = true;
        switch (d) {
            case "up", "y" -> aim(0, 1, 0);
            case "down", "-y" -> aim(0, -1, 0);
            case "east", "x" -> aim(1, 0, 0);
            case "west", "-x" -> aim(-1, 0, 0);
            case "south", "z" -> aim(0, 0, 1);
            case "north", "-z" -> aim(0, 0, -1);
            case "out", "r" -> radial = 1;
            case "in", "-r" -> radial = -1;
            case "look" -> throw new IllegalArgumentException("grad(look) needs a player - use it in /sb commands");
            default -> vector(d);
        }
    }

    private void vector(String d) {
        String[] c = d.split("/");
        if (c.length != 3) {
            throw new IllegalArgumentException("Unknown gradient direction '" + d + "'. Use " + DIRECTIONS);
        }
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(c[0]);
            y = Double.parseDouble(c[1]);
            z = Double.parseDouble(c[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown gradient direction '" + d + "'. Use " + DIRECTIONS);
        }
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len == 0) {
            throw new IllegalArgumentException("A gradient direction can't be 0/0/0");
        }
        aim(x / len, y / len, z / len);
        axisAligned = false;
    }

    private void aim(double x, double y, double z) {
        dirX = x;
        dirY = y;
        dirZ = z;
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

    // With a range, the first and last block sit at fixed world positions, so a brush stroke blends as one
    // gradient instead of restarting in every dab. Without one, the gradient stretches over the shape it fills.
    private String pickGradient(int x, int y, int z, double dither) {
        double at;
        double lo;
        double hi;
        if (radial != 0) {
            if (bounds == null) {
                return blocks.get(0);
            }
            double cx = (bounds.minX() + bounds.maxX()) / 2.0;
            double cy = (bounds.minY() + bounds.maxY()) / 2.0;
            double cz = (bounds.minZ() + bounds.maxZ()) / 2.0;
            at = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
            double half = Math.max(1, Math.max(bounds.maxX() - cx, Math.max(bounds.maxY() - cy, bounds.maxZ() - cz)));
            lo = ranged ? rangeFrom : radial > 0 ? 0 : half;
            hi = ranged ? rangeTo : radial > 0 ? half : 0;
        } else if (ranged) {
            // A range names world coordinates, so on a plain axis the range's order sets the direction.
            at = axisAligned ? x * Math.abs(dirX) + y * Math.abs(dirY) + z * Math.abs(dirZ) : x * dirX + y * dirY + z * dirZ;
            lo = rangeFrom;
            hi = rangeTo;
        } else {
            if (bounds == null) {
                return blocks.get(0);
            }
            at = x * dirX + y * dirY + z * dirZ;
            lo = low(dirX, bounds.minX(), bounds.maxX()) + low(dirY, bounds.minY(), bounds.maxY()) + low(dirZ, bounds.minZ(), bounds.maxZ());
            hi = high(dirX, bounds.minX(), bounds.maxX()) + high(dirY, bounds.minY(), bounds.maxY()) + high(dirZ, bounds.minZ(), bounds.maxZ());
        }
        double t = hi == lo ? 0 : Math.max(0, Math.min(1, (at - lo) / (hi - lo)));
        double scaled = t * (blocks.size() - 1) + dither;
        int idx = Math.max(0, Math.min(blocks.size() - 1, (int) Math.floor(scaled)));
        return blocks.get(idx);
    }

    private static double low(double d, int min, int max) {
        return Math.min(d * min, d * max);
    }

    private static double high(double d, int min, int max) {
        return Math.max(d * min, d * max);
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
        return gradient;
    }

    public Pattern bind(Box box) {
        this.bounds = box;
        return this;
    }

    // A straight gradient with no range stretches over whatever box it's bound to.
    public boolean fitsBounds() {
        return gradient && radial == 0 && !ranged;
    }

    // A face that points along the gradient (a floor's top under an upward gradient) sits at a single point on it.
    public boolean along(int nx, int ny, int nz) {
        return gradient && radial == 0 && Math.abs(nx * dirX + ny * dirY + nz * dirZ) >= 0.7;
    }

    // The nearest gradient step with no dithering, for surfaces where a dithered blend would only speckle.
    public String pickClean(int x, int y, int z) {
        if (gradient && blocks.size() > 1) {
            return pickGradient(x, y, z, 0.5);
        }
        return pick(x, y, z);
    }

    public String pick(int x, int y, int z) {
        if (gradient && blocks.size() > 1) {
            return pickGradient(x, y, z, dither(x, y, z));
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
