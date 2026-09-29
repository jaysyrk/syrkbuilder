package dev.syrkbuilder.core.brush;

import dev.syrkbuilder.core.edit.WorldView;
import java.util.Set;

public final class Surface {
    private static final Set<String> SOFT = Set.of(
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:short_grass", "minecraft:grass", "minecraft:tall_grass",
        "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush", "minecraft:snow", "minecraft:vine", "minecraft:dandelion",
        "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
        "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower",
        "minecraft:lily_of_the_valley", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony",
        "minecraft:sweet_berry_bush", "minecraft:pink_petals", "minecraft:short_dry_grass", "minecraft:tall_dry_grass",
        "minecraft:leaf_litter", "minecraft:bush", "minecraft:firefly_bush", "minecraft:wildflowers");

    private Surface() {
    }

    public static boolean soft(String id) {
        return SOFT.contains(id);
    }

    public static boolean air(String id) {
        return id.endsWith(":air") || id.endsWith("_air");
    }

    public static int top(WorldView world, int x, int z, int bottom, int top) {
        for (int y = top; y >= bottom; y--) {
            if (!soft(world.blockId(x, y, z))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
