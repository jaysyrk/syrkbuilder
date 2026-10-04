package dev.syrkbuilder.core.terrain;

import dev.syrkbuilder.core.noise.PerlinNoise;

final class Shaper {
    private final TerrainParams p;
    private final PerlinNoise noise;
    private final PerlinNoise warp;
    private final double[][] peaks;

    Shaper(TerrainParams p) {
        this.p = p;
        this.noise = new PerlinNoise(p.seed);
        this.warp = new PerlinNoise(p.seed * 31 + 7);
        java.util.Random random = new java.util.Random(p.seed ^ 0x5DEECE66DL);
        int count = p.type == TerrainType.MOUNTAIN ? p.peaks : 1;
        peaks = new double[count][3];
        for (int k = 0; k < count; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double dist = count == 1 ? 0 : (0.15 + random.nextDouble() * 0.35) * p.radius;
            peaks[k][0] = Math.cos(a) * dist;
            peaks[k][1] = Math.sin(a) * dist;
            peaks[k][2] = k == 0 ? 1.0 : 0.55 + random.nextDouble() * 0.4;
        }
    }

    int extent() {
        return p.type == TerrainType.CANYON || p.type == TerrainType.FJORD || p.type == TerrainType.VALLEY ? p.radius + p.width : p.radius + 2;
    }

    double height(double dx, double dz) {
        switch (p.type) {
            case MOUNTAIN: return mountain(dx, dz);
            case HILLS: return hills(dx, dz);
            case MESA: return mesa(dx, dz);
            case VOLCANO: return volcano(dx, dz);
            case CRATER: return crater(dx, dz);
            case CANYON: return canyon(dx, dz, true);
            case DUNES: return dunes(dx, dz);
            case BUTTES: return buttes(dx, dz);
            case VALLEY: return valley(dx, dz);
            case FJORD: return canyon(dx, dz, false);
            case LAKE: return lake(dx, dz);
            case ATOLL: return atoll(dx, dz);
            case ARCHIPELAGO: return archipelago(dx, dz);
            case SWAMP: return swamp(dx, dz);
            default: return islandTop(dx, dz);
        }
    }

    double fluid(double dx, double dz) {
        if (p.type == TerrainType.VOLCANO) {
            double d = Math.sqrt(dx * dx + dz * dz) / p.radius;
            if (d < CRATER_FRAC) {
                return coneAt(CRATER_FRAC) - craterDepth() * 0.55;
            }
        }
        boolean wet = switch (p.type) {
            case FJORD -> canyon(dx, dz, false) < -0.03;
            case LAKE -> lake(dx, dz) < -0.04;
            case ATOLL -> warpedDistance(dx, dz, 0.15) < 0.98 && atoll(dx, dz) < 0.03;
            case ARCHIPELAGO -> warpedDistance(dx, dz, 0.3) < 0.98 && archipelago(dx, dz) < 0.04;
            case SWAMP -> warpedDistance(dx, dz, 0.2) < 0.97 && pools(dx, dz) > 0.5;
            case VALLEY -> channel(dx, dz) > 0.05;
            default -> false;
        };
        return wet ? 0 : Double.NaN;
    }

    private double warpedDistance(double dx, double dz, double amount) {
        double s = p.radius * 0.7;
        double wx = dx + amount * p.radius * warp.noise(dx / s, dz / s);
        double wz = dz + amount * p.radius * warp.noise(dx / s + 57.3, dz / s - 21.9);
        return Math.sqrt(wx * wx + wz * wz) / p.radius;
    }

    private static double smoothstep(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static double bell(double d) {
        if (d >= 1) {
            return 0;
        }
        double t = 1 - d * d;
        return t * t;
    }

    private double mountain(double dx, double dz) {
        double base = warpedDistance(dx, dz, 0.25);
        if (base >= 1) {
            return 0;
        }
        double sigma = p.radius * (p.peaks == 1 ? 0.42 : 0.3);
        final double k = 10;
        double sum = 0;
        for (double[] peak : peaks) {
            double ddx = dx - peak[0];
            double ddz = dz - peak[1];
            sum += Math.exp(k * peak[2] * Math.exp(-(ddx * ddx + ddz * ddz) / (2 * sigma * sigma)));
        }
        double mass = Math.max(0, (Math.log(sum) - Math.log(peaks.length)) / k);
        double scale = p.radius * 0.38;
        double qx = dx / scale + 0.6 * warp.noise(dx / scale * 0.5 + 11.1, dz / scale * 0.5);
        double qz = dz / scale + 0.6 * warp.noise(dx / scale * 0.5, dz / scale * 0.5 - 7.7);
        double gain = 0.45 + p.roughness * 0.2;
        double ridge = noise.ridged(qx, qz, 6, 2.0, gain);
        double detail = noise.fbm(dx / (scale * 0.2), dz / (scale * 0.2), 3, 2.0, 0.5);
        double h = Math.pow(mass, 1.3) * (0.25 + 0.75 * ridge) + detail * 0.03 * p.roughness * mass;
        return Math.max(0, h * bell(base));
    }

    private double hills(double dx, double dz) {
        double base = warpedDistance(dx, dz, 0.3);
        if (base >= 1) {
            return 0;
        }
        double scale = p.radius * 0.6;
        double n = 0.5 + 0.5 * noise.fbm(dx / scale, dz / scale, 4, 2.0, 0.45 + p.roughness * 0.15);
        return bell(base) * (0.35 + 0.65 * n);
    }

    private double mesa(double dx, double dz) {
        double base = warpedDistance(dx, dz, 0.35);
        double edge = 1 - smoothstep(0.55, 0.95, base);
        if (edge <= 0) {
            return 0;
        }
        double scale = p.radius * 0.5;
        double n = 0.5 + 0.5 * noise.fbm(dx / scale, dz / scale, 4, 2.0, 0.5);
        double v = edge * (0.55 + 0.45 * n);
        return terrace(v, p.steps);
    }

    private static double terrace(double v, int steps) {
        double scaled = v * steps;
        double level = Math.floor(scaled);
        double frac = scaled - level;
        double riser = Math.pow(smoothstep(0.75, 1.0, frac), 2);
        return (level + riser) / steps;
    }

    private static final double CRATER_FRAC = 0.2;

    private double coneAt(double d) {
        return Math.pow(Math.max(0, 1 - d), 1.35);
    }

    private double craterDepth() {
        return coneAt(CRATER_FRAC) * 0.45;
    }

    private double volcano(double dx, double dz) {
        double d = warpedDistance(dx, dz, 0.12);
        if (d >= 1) {
            return 0;
        }
        double angle = Math.atan2(dz, dx);
        double ridges = noise.ridged(Math.cos(angle) * 3 + d * 2, Math.sin(angle) * 3 + d * 2, 4, 2.0, 0.5);
        double h;
        if (d < CRATER_FRAC) {
            double t = d / CRATER_FRAC;
            h = coneAt(CRATER_FRAC) - craterDepth() * (1 - t * t);
        } else {
            h = coneAt(d) * (0.85 + 0.25 * ridges * p.roughness);
        }
        return Math.max(0, h * (1 - smoothstep(0.85, 1.0, d)));
    }

    private double crater(double dx, double dz) {
        double d = warpedDistance(dx, dz, 0.08);
        if (d >= 1) {
            return 0;
        }
        double bowl = d < 0.7 ? -(1 - Math.pow(d / 0.7, 2)) : 0;
        double rim = 0.3 * Math.exp(-Math.pow((d - 0.75) / 0.1, 2));
        double n = 0.08 * p.roughness * noise.fbm(dx / 8.0, dz / 8.0, 3, 2.0, 0.5);
        return (bowl + rim + n) * (1 - smoothstep(0.9, 1.0, d));
    }

    private double canyon(double dx, double dz, boolean steps) {
        double rad = Math.toRadians(p.angle);
        double ax = -Math.sin(rad);
        double az = Math.cos(rad);
        double along = dx * ax + dz * az;
        double across = dx * az - dz * ax;
        double t = Math.abs(along) / p.radius;
        if (t >= 1) {
            return 0;
        }
        double meander = p.width * 0.9 * noise.fbm(along / (p.radius * 0.5), 3.7, 3, 2.0, 0.5);
        double dist = Math.abs(across - meander);
        double half = p.width / 2.0;
        if (dist >= half * 1.6) {
            return 0;
        }
        double wall = 1 - smoothstep(half * 0.6, half * 1.6, dist);
        double stepped = steps ? terrace(wall, Math.max(2, p.steps)) : wall;
        double ends = 1 - smoothstep(0.75, 1.0, t);
        return -stepped * ends;
    }

    private double dunes(double dx, double dz) {
        double base = warpedDistance(dx, dz, 0.3);
        if (base >= 1) {
            return 0;
        }
        double rad = Math.toRadians(p.angle);
        double u = dx * -Math.sin(rad) + dz * Math.cos(rad);
        double wobble = noise.fbm(dx / (p.width * 2.0), dz / (p.width * 2.0), 3, 2.0, 0.5) * p.width * 0.8;
        double phase = (u + wobble) / p.width;
        double wave = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2);
        return bell(base) * (0.18 + 0.82 * Math.pow(wave, 1.6));
    }

    private double buttes(double dx, double dz) {
        double base = warpedDistance(dx, dz, 0.2);
        if (base >= 1) {
            return 0;
        }
        double scale = p.radius * 0.28;
        double n = 0.5 + 0.5 * noise.fbm(dx / scale, dz / scale, 3, 2.0, 0.5);
        double edge = 0.74 - 0.03 * p.peaks;
        double pillar = smoothstep(edge, edge + 0.018, n);
        double top = 0.72 + 0.28 * (0.5 + 0.5 * noise.noise(dx / (scale * 2.4) + 31.7, dz / (scale * 2.4)));
        double h = 0.1 * n + pillar * top;
        return h * (1 - smoothstep(0.82, 1.0, base));
    }

    private double valleyAcross(double dx, double dz) {
        double rad = Math.toRadians(p.angle);
        double ax = -Math.sin(rad);
        double az = Math.cos(rad);
        double along = dx * ax + dz * az;
        double across = dx * az - dz * ax;
        double meander = p.width * 1.1 * noise.fbm(along / (p.radius * 0.45), 8.3, 3, 2.0, 0.5);
        return Math.abs(across - meander);
    }

    private double channel(double dx, double dz) {
        double rad = Math.toRadians(p.angle);
        double along = Math.abs(dx * -Math.sin(rad) + dz * Math.cos(rad)) / p.radius;
        if (along >= 1) {
            return 0;
        }
        double river = Math.max(2.0, p.width * 0.28);
        return (1 - smoothstep(0, river, valleyAcross(dx, dz))) * (1 - smoothstep(0.85, 1.0, along));
    }

    private double valley(double dx, double dz) {
        double rad = Math.toRadians(p.angle);
        double along = Math.abs(dx * -Math.sin(rad) + dz * Math.cos(rad)) / p.radius;
        double across = Math.abs(dx * Math.cos(rad) - dz * -Math.sin(rad));
        if (along >= 1 || across >= p.radius + p.width) {
            return 0;
        }
        double d = valleyAcross(dx, dz);
        double scale = p.radius * 0.3;
        double ridged = noise.ridged(dx / scale, dz / scale, 4, 2.0, 0.45 + p.roughness * 0.2);
        double wall = smoothstep(p.width * 0.35, p.radius * 0.75, d);
        double h = wall * (0.55 + 0.45 * ridged) - 0.1 * channel(dx, dz);
        double ends = 1 - smoothstep(0.8, 1.0, along);
        double sides = 1 - smoothstep(0.75, 1.0, across / (p.radius + p.width));
        return h * ends * sides;
    }

    private double lake(double dx, double dz) {
        double d = warpedDistance(dx, dz, 0.25);
        if (d >= 1) {
            return 0;
        }
        double bowl = d < 0.78 ? -Math.pow(1 - (d / 0.78) * (d / 0.78), 1.4) : 0;
        double rim = 0.07 * Math.exp(-Math.pow((d - 0.86) / 0.08, 2));
        double n = 0.04 * p.roughness * noise.fbm(dx / 7.0, dz / 7.0, 3, 2.0, 0.5);
        return (bowl + rim + n) * (1 - smoothstep(0.92, 1.0, d));
    }

    private double atoll(double dx, double dz) {
        double d = warpedDistance(dx, dz, 0.15);
        if (d >= 1) {
            return 0;
        }
        double broken = 0.5 + 0.5 * noise.fbm(dx / (p.radius * 0.25), dz / (p.radius * 0.25), 3, 2.0, 0.5);
        double ring = Math.exp(-Math.pow((d - 0.62) / 0.1, 2)) * (0.15 + 0.85 * smoothstep(0.28, 0.6, broken));
        double lagoon = -0.35 * (1 - smoothstep(0.35, 0.52, d));
        double sea = -0.35 * smoothstep(0.72, 0.9, d);
        return (0.75 * ring + lagoon + sea) * (1 - smoothstep(0.9, 1.0, d));
    }

    private double archipelago(double dx, double dz) {
        double base = warpedDistance(dx, dz, 0.3);
        if (base >= 1) {
            return 0;
        }
        double scale = p.radius * 0.3;
        double n = 0.5 + 0.5 * noise.fbm(dx / scale, dz / scale, 4, 2.0, 0.5);
        double v = n - (0.66 - 0.03 * p.peaks);
        double h = v > 0 ? 0.12 + 0.6 * Math.pow(Math.min(1, v * 3), 0.9) : Math.max(-0.45, v * 3);
        return h * (1 - smoothstep(0.88, 1.0, base));
    }

    private double pools(double dx, double dz) {
        double scale = p.radius * 0.22;
        double n = 0.5 + 0.5 * noise.fbm(dx / scale + 14.2, dz / scale - 3.9, 3, 2.0, 0.5);
        return smoothstep(0.62 - 0.02 * p.peaks, 0.7 - 0.02 * p.peaks, n);
    }

    private double swamp(double dx, double dz) {
        double base = warpedDistance(dx, dz, 0.2);
        if (base >= 1) {
            return 0;
        }
        double hummocks = 0.5 + 0.5 * noise.fbm(dx / 9.0, dz / 9.0, 3, 2.0, 0.5);
        double h = 0.12 * hummocks - 0.3 * pools(dx, dz);
        return h * (1 - smoothstep(0.85, 1.0, base));
    }

    double islandTop(double dx, double dz) {
        double d = warpedDistance(dx, dz, 0.3);
        if (d >= 1) {
            return 0;
        }
        double n = 0.5 + 0.5 * noise.fbm(dx / (p.radius * 0.5), dz / (p.radius * 0.5), 4, 2.0, 0.5);
        return bell(d) * (0.4 + 0.6 * n);
    }

    double islandBottom(double dx, double dz) {
        double d = warpedDistance(dx, dz, 0.3);
        if (d >= 1) {
            return 0;
        }
        double spikes = noise.ridged(dx / (p.radius * 0.35), dz / (p.radius * 0.35), 4, 2.0, 0.5);
        return Math.pow(1 - d, 1.4) * (1.2 + 1.8 * spikes);
    }
}
