package dev.syrkbuilder.core.tree;

import dev.syrkbuilder.core.brush.Surface;
import dev.syrkbuilder.core.edit.EditBuffer;
import dev.syrkbuilder.core.edit.WorldView;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class Trees {
    public enum Type {
        OAK("oak", 7, "rounded, branching"),
        BIRCH("birch", 9, "tall and slim"),
        SPRUCE("spruce", 12, "conical layers"),
        PINE("pine", 14, "bare trunk, small top"),
        JUNGLE("jungle", 20, "huge 2x2 trunk, vines"),
        DARK_OAK("dark_oak", 9, "thick, wide flat crown"),
        ACACIA("acacia", 7, "bent trunk, flat canopies"),
        CHERRY("cherry", 8, "curving branches, pink crown"),
        WILLOW("willow", 9, "hanging curtains of leaves"),
        PALM("palm", 10, "curved trunk, fronds"),
        DEAD("dead", 7, "bare twisted branches"),
        SWAMP("swamp", 7, "mangrove with arching roots");

        public final String id;
        public final int defaultHeight;
        public final String description;

        Type(String id, int defaultHeight, String description) {
            this.id = id;
            this.defaultHeight = defaultHeight;
            this.description = description;
        }

        public static Type byName(String name) {
            String n = name.toLowerCase(Locale.ROOT).replace("darkoak", "dark_oak");
            for (Type t : values()) {
                if (t.id.equals(n)) {
                    return t;
                }
            }
            return null;
        }

        public static final List<String> NAMES;

        static {
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (Type t : values()) {
                names.add(t.id);
            }
            NAMES = List.copyOf(names);
        }
    }

    private final WorldView world;
    private final EditBuffer out;
    private final Random random;
    private final String log;
    private final String leaves;

    private Trees(WorldView world, EditBuffer out, Random random, String wood, String leafBlock) {
        this.world = world;
        this.out = out;
        this.random = random;
        this.log = "minecraft:" + wood;
        this.leaves = "minecraft:" + leafBlock + "[persistent=true]";
    }

    public static void grow(Type type, WorldView world, int x, int y, int z, int height, long seed, EditBuffer out) {
        Random random = new Random(seed);
        int h = height > 0 ? height : Math.max(3, (int) Math.round(type.defaultHeight * (0.8 + random.nextDouble() * 0.45)));
        Trees t = switch (type) {
            case BIRCH -> new Trees(world, out, random, "birch_log", "birch_leaves");
            case SPRUCE, PINE -> new Trees(world, out, random, "spruce_log", "spruce_leaves");
            case JUNGLE -> new Trees(world, out, random, "jungle_log", "jungle_leaves");
            case DARK_OAK -> new Trees(world, out, random, "dark_oak_log", "dark_oak_leaves");
            case ACACIA -> new Trees(world, out, random, "acacia_log", "acacia_leaves");
            case CHERRY -> new Trees(world, out, random, "cherry_log", "cherry_leaves");
            case PALM -> new Trees(world, out, random, "jungle_log", "jungle_leaves");
            case SWAMP -> new Trees(world, out, random, "mangrove_log", "mangrove_leaves");
            case DEAD -> new Trees(world, out, random, "spruce_log", "oak_leaves");
            default -> new Trees(world, out, random, "oak_log", "oak_leaves");
        };
        double bx = x + 0.5;
        double by = y + 1;
        double bz = z + 0.5;
        switch (type) {
            case OAK -> t.broadleaf(bx, by, bz, h, 3, 2.6, 0.55);
            case CHERRY -> t.broadleaf(bx, by, bz, h, 4, 3.0, 0.75);
            case DEAD -> t.dead(bx, by, bz, h);
            case BIRCH -> t.birch(bx, by, bz, h);
            case SPRUCE -> t.conifer(bx, by, bz, h, h * 0.8, 0.36);
            case PINE -> t.conifer(bx, by, bz, h, h * 0.42, 0.34);
            case JUNGLE -> t.jungle(bx, by, bz, h);
            case DARK_OAK -> t.darkOak(bx, by, bz, h);
            case ACACIA -> t.acacia(bx, by, bz, h);
            case WILLOW -> t.willow(bx, by, bz, h);
            case PALM -> t.palm(bx, by, bz, h);
            case SWAMP -> t.swamp(bx, by, bz, h);
        }
    }

    private boolean free(int x, int y, int z) {
        if (y < world.minY() || y >= world.maxY()) {
            return false;
        }
        String planned = out.get(x, y, z);
        if (planned != null) {
            return !planned.startsWith(log);
        }
        String id = world.blockId(x, y, z);
        return Surface.soft(id) || id.endsWith("_leaves");
    }

    private void wood(int x, int y, int z, char axis) {
        if (free(x, y, z) || out.get(x, y, z) != null) {
            out.set(x, y, z, log + "[axis=" + axis + "]");
        }
    }

    private void leaf(int x, int y, int z) {
        if (out.get(x, y, z) == null && free(x, y, z)) {
            out.set(x, y, z, leaves);
        }
    }

    private static int f(double v) {
        return (int) Math.floor(v);
    }

    private void limb(double ax, double ay, double az, double bx, double by, double bz, double radius) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        char axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) * 0.9 ? 'y' : Math.abs(dx) > Math.abs(dz) ? 'x' : 'z';
        int steps = Math.max(1, (int) Math.ceil(len * 2));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double cx = ax + dx * t;
            double cy = ay + dy * t;
            double cz = az + dz * t;
            if (radius <= 0.6) {
                wood(f(cx), f(cy), f(cz), axis);
                continue;
            }
            int r = (int) Math.ceil(radius);
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if ((ox + 0.5) * (ox + 0.5) + (oz + 0.5) * (oz + 0.5) <= radius * radius + 0.3) {
                        wood(f(cx) + ox, f(cy), f(cz) + oz, axis);
                    }
                }
            }
        }
    }

    private void blob(double cx, double cy, double cz, double rx, double ry) {
        int ix = (int) Math.ceil(rx) + 1;
        int iy = (int) Math.ceil(ry) + 1;
        for (int y = -iy; y <= iy; y++) {
            for (int z = -ix; z <= ix; z++) {
                for (int x = -ix; x <= ix; x++) {
                    double px = f(cx) + x + 0.5 - cx;
                    double py = f(cy) + y + 0.5 - cy;
                    double pz = f(cz) + z + 0.5 - cz;
                    double d = (px * px + pz * pz) / (rx * rx) + (py * py) / (ry * ry);
                    if (d <= 1 - random.nextDouble() * 0.28) {
                        leaf(f(cx) + x, f(cy) + y, f(cz) + z);
                    }
                }
            }
        }
    }

    private void disc(double cx, double cy, double cz, double r) {
        int ir = (int) Math.ceil(r);
        for (int z = -ir; z <= ir; z++) {
            for (int x = -ir; x <= ir; x++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r - 0.35 || d <= r + 0.35 && random.nextBoolean()) {
                    leaf(f(cx) + x, f(cy), f(cz) + z);
                }
            }
        }
    }

    private void broadleaf(double x, double y, double z, int h, int branches, double leafR, double spread) {
        double top = y + h;
        limb(x, y, z, x, top, z, 0);
        blob(x, top, z, leafR, leafR * 0.8);
        double start = y + h * 0.45;
        for (int i = 0; i < branches; i++) {
            double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2 / branches;
            double by = start + random.nextDouble() * (h * 0.45);
            double len = h * (0.35 + random.nextDouble() * 0.25) * (spread / 0.55);
            double ex = x + Math.cos(angle) * len;
            double ez = z + Math.sin(angle) * len;
            double ey = by + len * 0.6;
            limb(x, by, z, ex, ey, ez, 0);
            blob(ex, ey, ez, leafR * (0.8 + random.nextDouble() * 0.3), leafR * 0.7);
        }
    }

    private void birch(double x, double y, double z, int h) {
        limb(x, y, z, x, y + h - 1, z, 0);
        double crownBottom = y + h * 0.4;
        double crownTop = y + h + 1.5;
        blob(x, (crownBottom + crownTop) / 2, z, 2.6, (crownTop - crownBottom) / 2);
        leaf(f(x), f(y + h), f(z));
        leaf(f(x), f(y + h + 1), f(z));
    }

    private void conifer(double x, double y, double z, int h, double crown, double taper) {
        limb(x, y, z, x, y + h - 1, z, 0);
        int base = (int) Math.round(h - crown);
        double maxR = Math.max(2.6, crown * taper);
        int layers = h + 1 - base;
        for (int k = 0; k <= layers; k++) {
            double t = (double) k / Math.max(1, layers);
            double r = maxR * (1 - t) + 0.9;
            if (k % 2 == 1) {
                r = Math.max(1.4, r * 0.7);
            }
            disc(x, y + base + k, z, r);
        }
        leaf(f(x), f(y + h + 1), f(z));
        leaf(f(x), f(y + h + 2), f(z));
    }

    private void jungle(double x, double y, double z, int h) {
        limb(x, y, z, x, y + h, z, 0);
        limb(x + 1, y, z, x + 1, y + h, z, 0);
        limb(x, y, z + 1, x, y + h, z + 1, 0);
        limb(x + 1, y, z + 1, x + 1, y + h, z + 1, 0);
        blob(x + 1, y + h, z + 1, 5, 3);
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double by = y + h * (0.55 + random.nextDouble() * 0.3);
            double ex = x + 1 + Math.cos(angle) * 5;
            double ez = z + 1 + Math.sin(angle) * 5;
            limb(x + 1, by, z + 1, ex, by + 3, ez, 0);
            blob(ex, by + 3, ez, 3, 2);
        }
        for (int k = 0; k < h * 2; k++) {
            int side = random.nextInt(4);
            int vy = f(y) + random.nextInt(Math.max(1, h - 2));
            int[][] spots = {{-1, 0, 'e'}, {2, 0, 'w'}, {0, -1, 's'}, {0, 2, 'n'}};
            int[] s = spots[side];
            String face = switch (s[2]) {
                case 'e' -> "east";
                case 'w' -> "west";
                case 's' -> "south";
                default -> "north";
            };
            int vx = f(x) + s[0];
            int vz = f(z) + s[1];
            if (free(vx, vy, vz) && out.get(vx, vy, vz) == null) {
                out.set(vx, vy, vz, "minecraft:vine[" + face + "=true]");
            }
        }
    }

    private void darkOak(double x, double y, double z, int h) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                limb(x + dx, y, z + dz, x + dx, y + h, z + dz, 0);
            }
        }
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 + random.nextDouble() * 0.8;
            double ex = x + 0.5 + Math.cos(angle) * 3.5;
            double ez = z + 0.5 + Math.sin(angle) * 3.5;
            limb(x + 0.5, y + h - 2, z + 0.5, ex, y + h, ez, 0);
            blob(ex, y + h + 0.5, ez, 3.2, 1.8);
        }
        blob(x + 0.5, y + h + 1, z + 0.5, 4.5, 2);
    }

    private void acacia(double x, double y, double z, int h) {
        double angle = random.nextDouble() * Math.PI * 2;
        double lean = 2 + random.nextInt(2);
        double midY = y + h * 0.5;
        limb(x, y, z, x, midY, z, 0);
        double tx = x + Math.cos(angle) * lean;
        double tz = z + Math.sin(angle) * lean;
        limb(x, midY, z, tx, y + h, tz, 0);
        disc(tx, y + h + 1, tz, 3.4);
        disc(tx, y + h + 2, tz, 2.2);
        if (random.nextBoolean()) {
            double a2 = angle + Math.PI * (0.6 + random.nextDouble() * 0.8);
            double sx = x + Math.cos(a2) * 2.5;
            double sz = z + Math.sin(a2) * 2.5;
            limb(x, midY, z, sx, y + h - 1, sz, 0);
            disc(sx, y + h, sz, 2.4);
        }
    }

    private void willow(double x, double y, double z, int h) {
        limb(x, y, z, x, y + h, z, 0);
        blob(x, y + h, z, 3.5, 2.2);
        for (int i = 0; i < 5; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double ex = x + Math.cos(angle) * 3;
            double ez = z + Math.sin(angle) * 3;
            limb(x, y + h - 2, z, ex, y + h, ez, 0);
            blob(ex, y + h, ez, 2.2, 1.5);
        }
        int r = 5;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 2.5 || d > r || random.nextDouble() < 0.45) {
                    continue;
                }
                int len = 2 + random.nextInt(Math.max(1, h / 2));
                int sx = f(x) + dx;
                int sz = f(z) + dz;
                int startY = f(y + h) - (int) (d * 0.4);
                for (int k = 0; k < len; k++) {
                    leaf(sx, startY - k, sz);
                }
            }
        }
    }

    private void palm(double x, double y, double z, int h) {
        double angle = random.nextDouble() * Math.PI * 2;
        double bend = 1.5 + random.nextDouble() * 2;
        double px = x;
        double py = y;
        double pz = z;
        int segments = 6;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double nx = x + Math.cos(angle) * bend * t * t;
            double nz = z + Math.sin(angle) * bend * t * t;
            double ny = y + h * t;
            limb(px, py, pz, nx, ny, nz, 0);
            px = nx;
            py = ny;
            pz = nz;
        }
        int fronds = 6 + random.nextInt(3);
        for (int i = 0; i < fronds; i++) {
            double a = i * Math.PI * 2 / fronds + random.nextDouble() * 0.3;
            for (double d = 0.5; d <= 4.5; d += 0.5) {
                double fy = py + 1 - (d * d) / 7;
                leaf(f(px + Math.cos(a) * d), f(fy), f(pz + Math.sin(a) * d));
            }
        }
        leaf(f(px), f(py + 1), f(pz));
    }

    private void dead(double x, double y, double z, int h) {
        limb(x, y, z, x, y + h, z, 0);
        int branches = 3 + random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double by = y + h * (0.4 + random.nextDouble() * 0.5);
            double len = 2 + random.nextDouble() * h * 0.35;
            double ex = x + Math.cos(angle) * len;
            double ez = z + Math.sin(angle) * len;
            double ey = by + len * (random.nextDouble() - 0.2);
            limb(x, by, z, ex, ey, ez, 0);
            double a2 = angle + (random.nextDouble() - 0.5) * 1.5;
            limb(ex, ey, ez, ex + Math.cos(a2) * 1.5, ey + 1.5, ez + Math.sin(a2) * 1.5, 0);
        }
    }

    private void swamp(double x, double y, double z, int h) {
        double trunkBase = y + 2;
        limb(x, trunkBase, z, x, y + h, z, 0);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + random.nextDouble() * 0.6;
            double rx = x + Math.cos(a) * 2.2;
            double rz = z + Math.sin(a) * 2.2;
            limb(x, trunkBase, z, rx, y + 0.5, rz, 0);
            limb(rx, y + 0.5, rz, rx, y - 1, rz, 0);
        }
        blob(x, y + h, z, 3.5, 2.2);
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double ex = x + Math.cos(a) * 3;
            double ez = z + Math.sin(a) * 3;
            limb(x, y + h - 2, z, ex, y + h - 0.5, ez, 0);
            blob(ex, y + h - 0.5, ez, 2.3, 1.6);
        }
    }
}
