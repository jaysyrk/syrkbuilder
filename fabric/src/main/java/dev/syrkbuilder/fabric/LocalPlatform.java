package dev.syrkbuilder.fabric;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.engine.Platform;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class LocalPlatform implements Platform<ServerLevel, BlockState> {
    private static final int FLAGS = 2 | 16 | 32;

    private final MinecraftServer server;

    LocalPlatform(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public WorldView view(ServerLevel level) {
        return new WorldView() {
            @Override
            public int groundY(int x, int z) {
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            }

            @Override
            public int minY() {
                return level.getMinY();
            }

            @Override
            public int maxY() {
                return level.getMaxY() + 1;
            }

            @Override
            public String blockId(int x, int y, int z) {
                return BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
            }

            @Override
            public String blockState(int x, int y, int z) {
                return BlockStateParser.serialize(level.getBlockState(new BlockPos(x, y, z)));
            }
        };
    }

    @Override
    public String worldKey(ServerLevel level) {
        String dim = level.dimension().toString();
        dim = dim.substring(dim.lastIndexOf('/') + 1).replace("]", "").trim();
        return server.getWorldData().getLevelName() + "/" + dim;
    }

    @Override
    public BlockState parse(String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, state, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    @Override
    public String serialize(BlockState state) {
        return BlockStateParser.serialize(state);
    }

    @Override
    public String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @Override
    public BlockState get(ServerLevel level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    @Override
    public void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
    }

    @Override
    public void runOnMainThread(Runnable task) {
        server.execute(task);
    }
}
