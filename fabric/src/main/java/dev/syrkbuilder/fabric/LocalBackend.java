package dev.syrkbuilder.fabric;

import dev.syrkbuilder.core.engine.Engine;
import dev.syrkbuilder.core.engine.EngineConfig;
import dev.syrkbuilder.core.protocol.Protocol;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import java.util.UUID;
import java.util.logging.Logger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class LocalBackend {
    private static final Logger LOGGER = Logger.getLogger("SyrkBuilder");
    private static Engine<ServerLevel, BlockState> engine;
    private static IntegratedServer engineServer;

    private LocalBackend() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (engine != null && server == engineServer) {
                engine.tick();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (engine != null && server == engineServer) {
                engine.shutdown();
                engine = null;
                engineServer = null;
            }
        });
    }

    static boolean active() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    static void send(byte[] message) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        UUID player = mc.player.getUUID();
        var dimension = mc.player.level().dimension();
        server.execute(() -> {
            if (engine == null || engineServer != server) {
                engine = new Engine<>(new LocalPlatform(server), EngineConfig.defaults(), LocalFiles.root().toFile(), LOGGER);
                Path game = FabricLoader.getInstance().getGameDir();
                engine.addTemplateFolder(game.resolve("schematics").toFile());
                engine.addTemplateFolder(game.resolve("config").resolve("worldedit").resolve("schematics").toFile());
                engineServer = server;
            }
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                return;
            }
            engine.receive(player, level, message, LocalBackend::reply);
        });
    }

    private static void reply(String line) {
        Minecraft mc = Minecraft.getInstance();
        Protocol.Data data = Protocol.dataLine(line);
        if (data != null) {
            mc.execute(() -> ClientData.accept(data));
            return;
        }
        String text = line.replace('&', '§');
        StatusLine.set(text);
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(text), false);
            }
        });
    }
}
