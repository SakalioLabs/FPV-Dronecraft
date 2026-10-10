package com.tenicana.dronecraft.client.mixin;

import java.util.function.BooleanSupplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IntegratedServer.class)
public abstract class RuntimeIntegratedServerTimingMixin {
	@WrapMethod(method = "tickServer(Ljava/util/function/BooleanSupplier;)V")
	private void fpvdrone$measureServerTick(BooleanSupplier budget, Operation<Void> original) {
		long captureId = RuntimePerformanceMonitor.captureId();
		if (captureId == 0) {
			original.call(budget);
			return;
		}
		long startNs = System.nanoTime();
		try {
			original.call(budget);
		} finally {
			// tickServer updates the pause flag before choosing its paused or running branch.
			boolean paused = ((IntegratedServer) (Object) this).isPaused();
			RuntimePerformanceMonitor.recordServerTick(captureId, startNs, System.nanoTime(), paused);
		}
	}
}
