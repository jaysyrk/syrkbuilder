package dev.syrkbuilder.core.shape;

import dev.syrkbuilder.core.edit.Box;

public final class Shapes {
    private Shapes() {
    }

    public record Placed(Box box, VoxelShape shape) {
    }

    public enum Axis { X, Y, Z }

    public static Placed ellipsoid(int cx, int cy, int cz, double rx, double ry, double rz) {
        double ax = rx + 0.5;
        double ay = ry + 0.5;
        double az = rz + 0.5;
        Box box = Box.of(cx - (int) rx, cy - (int) ry, cz - (int) rz, cx + (int) rx, cy + (int) ry, cz + (int) rz);
        return new Placed(box, (x, y, z) -> {
            double dx = (x - cx) / ax;
            double dy = (y - cy) / ay;
            double dz = (z - cz) / az;
            return dx * dx + dy * dy + dz * dz <= 1;
        });
    }

    public static Placed sphere(int cx, int cy, int cz, double r) {
        return ellipsoid(cx, cy, cz, r, r, r);
    }

    public static Placed dome(int cx, int cy, int cz, double r) {
        Placed s = sphere(cx, cy, cz, r);
        Box box = Box.of(cx - (int) r, cy, cz - (int) r, cx + (int) r, cy + (int) r, cz + (int) r);
        return new Placed(box, (x, y, z) -> y >= cy && s.shape().inside(x, y, z));
    }

    public static Placed cylinder(int cx, int cy, int cz, double r, int height) {
        double a = r + 0.5;
        Box box = Box.of(cx - (int) r, cy, cz - (int) r, cx + (int) r, cy + height - 1, cz + (int) r);
        return new Placed(box, (x, y, z) -> {
            if (y < cy || y >= cy + height) {
                return false;
            }
            double dx = x - cx;
            double dz = z - cz;
            return dx * dx + dz * dz <= a * a;
        });
    }

    public static Placed circle(int cx, int cy, int cz, double r, double thickness, Axis axis) {
        double outer = r + 0.5;
        double inner = Math.max(0, r - thickness + 0.5);
        int ri = (int) r;
        Box box = switch (axis) {
            case Y -> Box.of(cx - ri, cy, cz - ri, cx + ri, cy, cz + ri);
            case X -> Box.of(cx, cy - ri, cz - ri, cx, cy + ri, cz + ri);
            case Z -> Box.of(cx - ri, cy - ri, cz, cx + ri, cy + ri, cz);
        };
        return new Placed(box, (x, y, z) -> {
            double u;
            double v;
            switch (axis) {
                case Y -> {
                    if (y != cy) {
                        return false;
                    }
                    u = x - cx;
                    v = z - cz;
                }
                case X -> {
                    if (x != cx) {
                        return false;
                    }
                    u = y - cy;
                    v = z - cz;
                }
                default -> {
                    if (z != cz) {
                        return false;
                    }
                    u = x - cx;
                    v = y - cy;
                }
            }
            double d2 = u * u + v * v;
            return d2 <= outer * outer && (thickness >= r || d2 > inner * inner);
        });
    }

    public static Placed cone(int cx, int cy, int cz, double r, int height) {
        Box box = Box.of(cx - (int) r, cy, cz - (int) r, cx + (int) r, cy + height - 1, cz + (int) r);
        return new Placed(box, (x, y, z) -> {
            if (y < cy || y >= cy + height) {
                return false;
            }
            double level = (y - cy) / (double) height;
            double a = r * (1 - level) + 0.5;
            double dx = x - cx;
            double dz = z - cz;
            return dx * dx + dz * dz <= a * a;
        });
    }

    public static Placed pyramid(int cx, int cy, int cz, int half) {
        Box box = Box.of(cx - half, cy, cz - half, cx + half, cy + half, cz + half);
        return new Placed(box, (x, y, z) -> {
            int level = y - cy;
            if (level < 0 || level > half) {
                return false;
            }
            int size = half - level;
            return Math.abs(x - cx) <= size && Math.abs(z - cz) <= size;
        });
    }

    public static Placed torus(int cx, int cy, int cz, double major, double minor) {
        int reach = (int) Math.ceil(major + minor);
        int mi = (int) Math.ceil(minor);
        double m = minor + 0.5;
        Box box = Box.of(cx - reach, cy - mi, cz - reach, cx + reach, cy + mi, cz + reach);
        return new Placed(box, (x, y, z) -> {
            double dx = x - cx;
            double dz = z - cz;
            double q = Math.sqrt(dx * dx + dz * dz) - major;
            double dy = y - cy;
            return q * q + dy * dy <= m * m;
        });
    }

    public static Placed line(int x1, int y1, int z1, int x2, int y2, int z2, double radius) {
        int r = (int) Math.ceil(radius);
        Box box = Box.of(Math.min(x1, x2) - r, Math.min(y1, y2) - r, Math.min(z1, z2) - r,
            Math.max(x1, x2) + r, Math.max(y1, y2) + r, Math.max(z1, z2) + r);
        double vx = x2 - x1;
        double vy = y2 - y1;
        double vz = z2 - z1;
        double len2 = vx * vx + vy * vy + vz * vz;
        double a = radius + 0.5;
        return new Placed(box, (x, y, z) -> {
            double wx = x - x1;
            double wy = y - y1;
            double wz = z - z1;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (wx * vx + wy * vy + wz * vz) / len2));
            double dx = wx - t * vx;
            double dy = wy - t * vy;
            double dz = wz - t * vz;
            return dx * dx + dy * dy + dz * dz <= a * a;
        });
    }

    public static Placed helix(int cx, int cy, int cz, double r, int height, double turns, double thickness) {
        int reach = (int) Math.ceil(r + thickness + 1);
        Box box = Box.of(cx - reach, cy, cz - reach, cx + reach, cy + height - 1, cz + reach);
        double a = Math.max(0.5, thickness) + 0.1;
        java.util.Set<Long> cells = new java.util.HashSet<>();
        double length = Math.sqrt(Math.pow(2 * Math.PI * r * Math.abs(turns), 2) + (double) height * height);
        int samples = Math.max(8, (int) Math.ceil(length * 4));
        int ia = (int) Math.ceil(a);
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double angle = t * turns * Math.PI * 2;
            double px = cx + 0.5 + Math.cos(angle) * r;
            double py = cy + 0.5 + t * (height - 1);
            double pz = cz + 0.5 + Math.sin(angle) * r;
            for (int dy = -ia; dy <= ia; dy++) {
                for (int dz = -ia; dz <= ia; dz++) {
                    for (int dx = -ia; dx <= ia; dx++) {
                        int x = (int) Math.floor(px) + dx;
                        int y = (int) Math.floor(py) + dy;
                        int z = (int) Math.floor(pz) + dz;
                        double ex = x + 0.5 - px;
                        double ey = y + 0.5 - py;
                        double ez = z + 0.5 - pz;
                        if (ex * ex + ey * ey + ez * ez <= a * a && y >= cy && y < cy + height) {
                            cells.add(dev.syrkbuilder.core.history.ChangeSet.pack(x, y, z));
                        }
                    }
                }
            }
        }
        return new Placed(box, (x, y, z) -> cells.contains(dev.syrkbuilder.core.history.ChangeSet.pack(x, y, z)));
    }
}
