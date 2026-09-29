package dev.syrkbuilder.fabric.mixin;

import dev.syrkbuilder.fabric.NoClip;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void syrkbuilder$noclipMove(CallbackInfo ci) {
        NoClip.apply((Player) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void syrkbuilder$noclipAfterTick(CallbackInfo ci) {
        NoClip.apply((Player) (Object) this);
    }
}
