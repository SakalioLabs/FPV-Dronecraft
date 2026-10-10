package com.tenicana.dronecraft.clientvalidation.mixin;

import com.tenicana.dronecraft.clientvalidation.NativeClientWorldValidation;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation only: this hook cannot suppress, add, or change a native render call. */
@Mixin(ItemInHandRenderer.class)
abstract class FirstPersonHandsProbeMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void fpvValidation$observeNativeHands(CallbackInfo callback) {
        NativeClientWorldValidation.onFirstPersonHands();
    }
}
