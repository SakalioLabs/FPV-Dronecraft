package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import com.tenicana.dronecraft.performance.NormalClientPerformanceProbe;
import com.tenicana.dronecraft.performance.PerformanceTimings;
import com.tenicana.dronecraft.performance.PerformanceTimings.Metric;
import com.tenicana.dronecraft.performance.PerformanceTimings.Phase;

@Mixin(Minecraft.class)
public abstract class MinecraftTimingMixin {
	@Unique private long fpvPerformance$frameStart;
	@Unique private long fpvPerformance$lastFrameStart;
	@Unique private Phase fpvPerformance$framePhase;
	@Unique private Phase fpvPerformance$lastFramePhase;
	@Unique private long fpvPerformance$lastFrameSaveRevision;
	@Unique private long fpvPerformance$frameSaveRevision;
	@Unique private boolean fpvPerformance$lastFrameSaving;
	@Unique private long fpvPerformance$tickStart;
	@Unique private Phase fpvPerformance$tickPhase;

	@Inject(method = "runTick", at = @At("HEAD"))
	private void fpvPerformance$frameStart(boolean render, CallbackInfo ci) {
		if (!NormalClientPerformanceProbe.ENABLED) return;
		NormalClientPerformanceProbe.onFrameBoundary((Minecraft) (Object) this, render);
		if (!render) return;
		long now = System.nanoTime();
		Phase phase = PerformanceTimings.phase();
		long saveRevision = PerformanceTimings.saveRevision();
		boolean saving = PerformanceTimings.saveActive();
		if (fpvPerformance$lastFrameStart != 0) {
			PerformanceTimings.add(phase, fpvPerformance$lastFramePhase == phase ? Metric.FRAME_INTERVAL : Metric.FRAME_INTERVAL_CROSSING_PHASE_BOUNDARY, now - fpvPerformance$lastFrameStart);
			if (saving || fpvPerformance$lastFrameSaving || saveRevision != fpvPerformance$lastFrameSaveRevision) {
				PerformanceTimings.add(PerformanceTimings.lastSavePhase(), Metric.FRAME_INTERVAL_OVERLAPPING_SAVE, now - fpvPerformance$lastFrameStart);
			}
		}
		fpvPerformance$frameStart = now;
		fpvPerformance$framePhase = phase;
		fpvPerformance$frameSaveRevision = saveRevision;
		fpvPerformance$lastFrameStart = now;
		fpvPerformance$lastFramePhase = phase;
		fpvPerformance$lastFrameSaveRevision = saveRevision;
		fpvPerformance$lastFrameSaving = saving;
	}

	@Inject(method = "runTick", at = @At("RETURN"))
	private void fpvPerformance$frameEnd(boolean render, CallbackInfo ci) {
		if (NormalClientPerformanceProbe.ENABLED && render && fpvPerformance$frameStart != 0) {
			PerformanceTimings.add(fpvPerformance$framePhase, Metric.FRAME_DURATION, System.nanoTime() - fpvPerformance$frameStart);
			if (PerformanceTimings.saveActive() || fpvPerformance$frameSaveRevision != PerformanceTimings.saveRevision()) {
				PerformanceTimings.add(PerformanceTimings.lastSavePhase(), Metric.FRAME_DURATION_OVERLAPPING_SAVE, System.nanoTime() - fpvPerformance$frameStart);
			}
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fpvPerformance$tickStart(CallbackInfo ci) {
		if (!NormalClientPerformanceProbe.ENABLED) return;
		fpvPerformance$tickStart = System.nanoTime();
		fpvPerformance$tickPhase = PerformanceTimings.phase();
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void fpvPerformance$tickEnd(CallbackInfo ci) {
		if (NormalClientPerformanceProbe.ENABLED && fpvPerformance$tickStart != 0) {
			PerformanceTimings.add(fpvPerformance$tickPhase, Metric.CLIENT_TICK_DURATION, System.nanoTime() - fpvPerformance$tickStart);
		}
	}
}
