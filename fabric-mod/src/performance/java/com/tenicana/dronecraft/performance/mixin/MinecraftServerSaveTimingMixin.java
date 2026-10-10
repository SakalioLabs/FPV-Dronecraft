package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.MinecraftServer;
import com.tenicana.dronecraft.performance.NormalClientPerformanceProbe;
import com.tenicana.dronecraft.performance.PerformanceTimings;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSaveTimingMixin {
	@Inject(method = "saveEverything", at = @At("HEAD"))
	private void fpvPerformance$saveStart(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
		if (NormalClientPerformanceProbe.ENABLED) PerformanceTimings.saveBegin(silent, flush, force);
	}
	@Inject(method = "saveEverything", at = @At("RETURN"))
	private void fpvPerformance$saveEnd(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
		if (NormalClientPerformanceProbe.ENABLED) PerformanceTimings.saveEnd(cir.getReturnValue());
	}
}
