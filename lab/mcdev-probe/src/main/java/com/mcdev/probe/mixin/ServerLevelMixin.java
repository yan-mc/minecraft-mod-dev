package com.mcdev.probe.mixin;

import com.mcdev.probe.TickControl;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void mcdev$freezeLevelTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (TickControl.shouldCancelTick(self)) {
            ci.cancel();
        }
    }
}
