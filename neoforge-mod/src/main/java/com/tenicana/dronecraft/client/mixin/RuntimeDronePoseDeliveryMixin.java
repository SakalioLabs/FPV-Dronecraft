package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DroneEntity.class)
public abstract class RuntimeDronePoseDeliveryMixin {
	@Unique private long fpvdrone$lastMovementCaptureId;
	@Unique private long fpvdrone$lastMovementArrivalNs;

	@Inject(method = "lerpTo(DDDFFI)V", at = @At("HEAD"))
	private void fpvdrone$recordMovementTarget(double x, double y, double z, float yaw, float pitch, int steps, CallbackInfo callback) {
		Entity entity = (Entity) (Object) this;
		long captureId = RuntimePerformanceMonitor.captureId();
		if (captureId == 0 || !(entity instanceof DroneEntity) || !entity.level().isClientSide()) {
			return;
		}
		// Target delivery is separate from interpolate(), which applies the target each client tick.
		long nowNs = System.nanoTime();
		long intervalNs = fpvdrone$lastMovementCaptureId == captureId
				? nowNs - fpvdrone$lastMovementArrivalNs : 0;
		fpvdrone$lastMovementCaptureId = captureId;
		fpvdrone$lastMovementArrivalNs = nowNs;
		RuntimePerformanceMonitor.recordPoseArrival(captureId, entity.getId(), true, nowNs, intervalNs);
	}
}
