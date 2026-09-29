package dev.syrkbuilder.paper;

import dev.syrkbuilder.core.edit.WorldView;
import org.bukkit.HeightMap;
import org.bukkit.World;

final class PaperWorldView implements WorldView {
    private final World world;

    PaperWorldView(World world) {
        this.world = world;
    }

    @Override
    public int groundY(int x, int z) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    @Override
    public int minY() {
        return world.getMinHeight();
    }

    @Override
    public int maxY() {
        return world.getMaxHeight();
    }

    @Override
    public String blockId(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().getKey().toString();
    }

    @Override
    public String blockState(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getBlockData().getAsString();
    }
}
