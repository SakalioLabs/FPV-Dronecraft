package com.tenicana.dronecraft.performance.mixin;

import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.server.IntegratedServer;
import com.tenicana.dronecraft.performance.NormalClientPerformanceProbe;
import com.tenicana.dronecraft.performance.PerformanceTimings;
import com.tenicana.dronecraft.performance.PerformanceTimings.Metric;
import com.tenicana.dronecraft.performance.PerformanceTimings.Phase;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerTimingMixin {
	@Unique private long fpvPerformance$serverTickStart;
	@Unique private long fpvPerformance$lastServerTickStart;
	@Unique private Phase fpvPerformance$serverTickPhase;
	@Unique private Phase fpvPerformance$lastServerTickPhase;
	@Unique private long fpvPerformance$lastSaveRevision;
	@Inject(method = "tickServer", at = @At("HEAD"))
	private void fpvPerformance$tickStart(BooleanSupplier budget, CallbackInfo ci) {
		if (!NormalClientPerformanceProbe.ENABLED) return;
		long now = System.nanoTime();
		Phase phase = PerformanceTimings.phase();
		long saveRevision = PerformanceTimings.saveRevision();
		if (fpvPerformance$lastServerTickStart != 0 && fpvPerformance$lastServerTickPhase == phase) {
			PerformanceTimings.add(phase, Metric.SERVER_TICK_INTERVAL, now - fpvPerformance$lastServerTickStart);
		}
		if (fpvPerformance$lastServerTickStart != 0 && (PerformanceTimings.saveActive() || saveRevision != fpvPerformance$lastSaveRevision)) {
			PerformanceTimings.add(PerformanceTimings.lastSavePhase(), Metric.SERVER_TICK_INTERVAL_OVERLAPPING_SAVE, now - fpvPerformance$lastServerTickStart);
		}
		fpvPerformance$serverTickStart = now;
		fpvPerformance$serverTickPhase = phase;
		fpvPerformance$lastServerTickStart = now;
		fpvPerformance$lastServerTickPhase = phase;
		fpvPerformance$lastSaveRevision = saveRevision;
	}
	@Inject(method = "tickServer", at = @At("RETURN"))
	private void fpvPerformance$tickEnd(BooleanSupplier budget, CallbackInfo ci) {
		if (!NormalClientPerformanceProbe.ENABLED || fpvPerformance$serverTickStart == 0) return;
		boolean paused = ((IntegratedServer) (Object) this).isPaused();
		PerformanceTimings.add(fpvPerformance$serverTickPhase, paused ? Metric.SERVER_PAUSED_TICK_DURATION : Metric.SERVER_TICK_DURATION, System.nanoTime() - fpvPerformance$serverTickStart);
	}
}
