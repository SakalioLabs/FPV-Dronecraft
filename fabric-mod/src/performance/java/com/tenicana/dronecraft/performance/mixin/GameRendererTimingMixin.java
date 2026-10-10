package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import com.tenicana.dronecraft.performance.NormalClientPerformanceProbe;
import com.tenicana.dronecraft.performance.PerformanceTimings;
import com.tenicana.dronecraft.performance.PerformanceTimings.Metric;
import com.tenicana.dronecraft.performance.PerformanceTimings.Phase;

@Mixin(GameRenderer.class)
public abstract class GameRendererTimingMixin {
	@Unique private long fpvPerformance$renderStart;
	@Unique private Phase fpvPerformance$renderPhase;
	@Inject(method = "render", at = @At("HEAD"))
	private void fpvPerformance$renderStart(DeltaTracker delta, boolean render, CallbackInfo ci) {
		if (!NormalClientPerformanceProbe.ENABLED) return;
		fpvPerformance$renderStart = System.nanoTime();
		fpvPerformance$renderPhase = PerformanceTimings.phase();
	}
	@Inject(method = "render", at = @At("RETURN"))
	private void fpvPerformance$renderEnd(DeltaTracker delta, boolean render, CallbackInfo ci) {
		if (NormalClientPerformanceProbe.ENABLED && fpvPerformance$renderStart != 0) {
			PerformanceTimings.add(fpvPerformance$renderPhase, Metric.RENDER_DURATION, System.nanoTime() - fpvPerformance$renderStart);
		}
	}
}
