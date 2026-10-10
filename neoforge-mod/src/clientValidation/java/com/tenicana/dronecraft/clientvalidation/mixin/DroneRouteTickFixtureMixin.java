package com.tenicana.dronecraft.clientvalidation.mixin;

import com.tenicana.dronecraft.clientvalidation.NativeClientWorldValidation;
import com.tenicana.dronecraft.entity.DroneEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the declared route fixture on the native entity clock, before lease refresh. */
@Mixin(DroneEntity.class)
abstract class DroneRouteTickFixtureMixin {
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void fpvValidation$advanceRouteOnNativeEntityTick(CallbackInfo callback) {
        NativeClientWorldValidation.onServerDroneTick((DroneEntity) (Object) this);
    }
}
