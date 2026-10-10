package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AbstractInterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractInterpolationHandler.class)
public abstract class RuntimeDronePoseDeliveryMixin {
	@Shadow @Final protected Entity entity;
	@Unique private long fpvdrone$lastMovementCaptureId;
	@Unique private long fpvdrone$lastMovementArrivalNs;

	@Inject(method = "interpolateTo(Lnet/minecraft/world/entity/PositionPath;FF)V", at = @At("HEAD"))
	private void fpvdrone$recordMovementTarget(PositionPath position, float yaw, float pitch, CallbackInfo callback) {
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
