package dev.syrkbuilder.fabric;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

interface Feedback {
    void info(String message);

    void error(String message);

    static Feedback of(CommandContext<FabricClientCommandSource> ctx) {
        return new Feedback() {
            @Override
            public void info(String message) {
                ctx.getSource().sendFeedback(Component.literal(message));
            }

            @Override
            public void error(String message) {
                ctx.getSource().sendError(Component.literal(message));
            }
        };
    }

    static Feedback chat() {
        return new Feedback() {
            @Override
            public void info(String message) {
                if (Settings.quietChat) {
                    StatusLine.set(message);
                } else {
                    show(message);
                }
            }

            @Override
            public void error(String message) {
                show("§c" + message);
            }

            private void show(String message) {
                StatusLine.set(message);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Chat.say(Component.literal(message));
                }
            }
        };
    }
}
