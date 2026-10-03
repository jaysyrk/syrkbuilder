package dev.syrkbuilder.paper;

import dev.syrkbuilder.core.engine.Engine;
import dev.syrkbuilder.core.engine.EngineConfig;
import dev.syrkbuilder.core.protocol.Protocol;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class SyrkBuilderPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    private Engine<World, BlockData> engine;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        EngineConfig config = new EngineConfig(
            getConfig().getLong("max-volume", 4_000_000L),
            Math.max(1000, getConfig().getInt("blocks-per-tick", 40000)),
            getConfig().getLong("history-blocks", 5_000_000L),
            getConfig().getBoolean("save-history", true),
            getConfig().getLong("max-upload-mb", 32) * 1024 * 1024,
            getConfig().getLong("script-timeout-ms", 3000));
        engine = new Engine<>(new PaperPlatform(this), config, getDataFolder(), getLogger());
        java.io.File plugins = getDataFolder().getParentFile();
        engine.addTemplateFolder(new java.io.File(plugins, "WorldEdit/schematics"));
        engine.addTemplateFolder(new java.io.File(plugins, "FastAsyncWorldEdit/schematics"));
        getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> engine.tick(), 1L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            engine.preload(p.getUniqueId(), p.getWorld());
        }
        getLogger().info("SyrkBuilder is switched off on servers for now: every request is refused until server support is released.");
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        engine.preload(event.getPlayer().getUniqueId(), event.getPlayer().getWorld());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Protocol.CHANNEL.equals(channel)) {
            return;
        }
        if (!player.hasPermission("syrkbuilder.use")) {
            send(player, "&cYou don't have permission to use SyrkBuilder.");
            return;
        }
        engine.receive(player.getUniqueId(), player.getWorld(), message, line -> send(player, line));
    }

    private void send(Player player, String message) {
        Protocol.Data data = Protocol.dataLine(message);
        if (data != null) {
            player.sendPluginMessage(this, Protocol.CHANNEL, Protocol.encodeData(data));
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
