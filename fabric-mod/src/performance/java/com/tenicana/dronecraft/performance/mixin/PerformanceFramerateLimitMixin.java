package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.tenicana.dronecraft.performance.NormalClientPerformanceProbe;

/** Remove minimized/AFK throttles only in this explicit disposable run. */
@Mixin(FramerateLimitTracker.class)
public abstract class PerformanceFramerateLimitMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void fpvPerformance$syntheticLimit(CallbackInfoReturnable<Integer> cir) {
		if (NormalClientPerformanceProbe.ENABLED) cir.setReturnValue(NormalClientPerformanceProbe.FRAME_LIMIT);
	}
}
