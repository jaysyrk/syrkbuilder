package dev.syrkbuilder.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class Chat {
    private Chat() {
    }

    static void say(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }
}
