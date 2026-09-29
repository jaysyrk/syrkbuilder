package dev.syrkbuilder.paper;

import dev.syrkbuilder.core.edit.WorldView;
import dev.syrkbuilder.core.engine.Platform;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

final class PaperPlatform implements Platform<World, BlockData> {
    private final Plugin plugin;

    PaperPlatform(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public WorldView view(World world) {
        return new PaperWorldView(world);
    }

    @Override
    public String worldKey(World world) {
        return world.getName();
    }

    @Override
    public BlockData parse(String state) {
        return Bukkit.createBlockData(state);
    }

    @Override
    public String serialize(BlockData state) {
        return state.getAsString();
    }

    @Override
    public String blockId(BlockData state) {
        return state.getMaterial().getKey().toString();
    }

    @Override
    public BlockData get(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z).getBlockData();
    }

    @Override
    public void set(World world, int x, int y, int z, BlockData state) {
        world.getBlockAt(x, y, z).setBlockData(state, false);
    }

    private final java.util.Map<java.util.UUID, org.bukkit.GameMode> beforeNoclip = new java.util.HashMap<>();

    @Override
    public String noclip(java.util.UUID id, String mode) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return null;
        }
        if (!player.hasPermission("syrkbuilder.noclip")) {
            return "&cYou don't have permission to use noclip (syrkbuilder.noclip).";
        }
        boolean on = mode.isEmpty() ? !beforeNoclip.containsKey(id) : mode.equals("on");
        if (on) {
            beforeNoclip.putIfAbsent(id, player.getGameMode());
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            return "&aNoclip on &7- spectator mode on this server, so you can fly through blocks. &8/sb noclip (or N) to go back.";
        }
        org.bukkit.GameMode before = beforeNoclip.remove(id);
        if (before != null) {
            player.setGameMode(before);
        }
        return "&7Noclip off.";
    }

    @Override
    @SuppressWarnings("deprecation")
    public int dataVersion() {
        return Bukkit.getUnsafe().getDataVersion();
    }

    @Override
    public void runOnMainThread(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
