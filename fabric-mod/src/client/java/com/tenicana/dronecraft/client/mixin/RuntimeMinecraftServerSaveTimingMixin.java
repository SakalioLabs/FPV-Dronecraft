package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MinecraftServer.class)
public abstract class RuntimeMinecraftServerSaveTimingMixin {
	@WrapMethod(method = "saveEverything(ZZZ)Z")
	private boolean fpvdrone$measureServerSave(
			boolean silent,
			boolean flush,
			boolean force,
			Operation<Boolean> original
	) {
		long captureId = RuntimePerformanceMonitor.captureId();
		if (captureId == 0 || !((Object) this instanceof IntegratedServer)) {
			return original.call(silent, flush, force);
		}
		long startNs = System.nanoTime();
		RuntimePerformanceMonitor.saveStarted(captureId, startNs, silent, flush, force);
		boolean completed = false;
		boolean result = false;
		try {
			result = original.call(silent, flush, force);
			completed = true;
			return result;
		} finally {
			long endNs = System.nanoTime();
			if (completed) {
				RuntimePerformanceMonitor.saveFinished(captureId, startNs, endNs, result);
			} else {
				RuntimePerformanceMonitor.saveFailed(captureId, startNs, endNs);
			}
		}
	}
}
