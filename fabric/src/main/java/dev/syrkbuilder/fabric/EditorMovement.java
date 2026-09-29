package dev.syrkbuilder.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

final class EditorMovement {
    private EditorMovement() {
    }

    private static KeyMapping[] keys(Minecraft mc) {
        return new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
            mc.options.keyJump, mc.options.keyShift, mc.options.keySprint};
    }

    static void tick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof EditorScreen editor)) {
            return;
        }
        boolean allowed = !editor.isTyping();
        for (KeyMapping key : keys(mc)) {
            InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
            boolean down = allowed && bound.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
            key.setDown(down);
        }
    }

    static void release() {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping key : keys(mc)) {
            key.setDown(false);
        }
    }
}
