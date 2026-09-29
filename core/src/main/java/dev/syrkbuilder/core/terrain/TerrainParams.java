package dev.syrkbuilder.core.terrain;

import dev.syrkbuilder.core.command.Args;

public final class TerrainParams {
    public TerrainType type = TerrainType.MOUNTAIN;
    public int radius = 48;
    public int height = 50;
    public long seed = 1;
    public double roughness = 0.5;
    public int peaks = 3;
    public int erosion = 50;
    public int steps = 4;
    public double angle = 0;
    public int width = 12;
    public String style;

    public static TerrainParams parse(TerrainType type, Args args, double defaultAngle) {
        TerrainParams p = new TerrainParams();
        p.type = type;
        p.radius = args.intValue("radius", type.defaultRadius, 4, 512);
        p.height = args.intValue("height", type.defaultHeight, 1, 320);
        p.seed = args.longValue("seed", System.nanoTime());
        p.roughness = args.doubleValue("roughness", 0.5, 0, 1);
        p.peaks = args.intValue("peaks", 3, 1, 8);
        p.erosion = args.intValue("erosion", type.defaultErosion, 0, 100);
        p.steps = args.intValue("steps", 4, 1, 12);
        p.angle = args.doubleValue("angle", defaultAngle, -3600, 3600);
        p.width = args.intValue("width", type == TerrainType.DUNES ? 18 : 12, 2, 128);
        p.style = args.string("style", type.defaultStyle);
        return p;
    }
}
