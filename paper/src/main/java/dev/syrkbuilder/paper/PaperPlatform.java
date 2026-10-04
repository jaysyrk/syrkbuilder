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
    public String denied(java.util.UUID id) {
        if (!plugin.getConfig().getBoolean("enabled", true)) {
            return "SyrkBuilder is switched off on this server.";
        }
        org.bukkit.entity.Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return "You have to be online to use SyrkBuilder.";
        }
        boolean creative = player.getGameMode() == org.bukkit.GameMode.CREATIVE || beforeNoclip.containsKey(id);
        if (plugin.getConfig().getBoolean("require-creative", true) && !creative) {
            return "SyrkBuilder only works in creative mode.";
        }
        return null;
    }

    @Override
    public String scriptDenied(java.util.UUID id) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(id);
        if (player == null || !player.hasPermission("syrkbuilder.script")) {
            return "&cYou don't have permission to run scripts on this server (syrkbuilder.script).";
        }
        return null;
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

    @Override
    public String fillBiome(World world, int[] box, String biome) {
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(biome);
        if (key == null || org.bukkit.Registry.BIOME.get(key) == null) {
            return "&cUnknown biome '" + biome + "'.";
        }
        String command = String.format("execute in %s run fillbiome %d %d %d %d %d %d %s", world.getKey(),
            box[0], box[1], box[2], box[3], box[4], box[5], biome);
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:" + command);
        return ok ? "&aBiome set to &f" + biome.replace("minecraft:", "") + "&7. &8Biomes aren't part of undo." : "&cCouldn't set the biome.";
    }
}
