package dev.syrkbuilder.fabric.mixin;

import dev.syrkbuilder.fabric.NoClip;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void syrkbuilder$noclip(CallbackInfo ci) {
        NoClip.apply((LocalPlayer) (Object) this);
    }
}
