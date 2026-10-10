package com.tenicana.dronecraft.client.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DroneEntity.class)
public abstract class RuntimeDroneTimingMixin {
	@Unique private long fpvdrone$lastTickCaptureId;
	@Unique private long fpvdrone$lastTickStartNs;
	@Unique private long fpvdrone$lastMetadataCaptureId;
	@Unique private long fpvdrone$lastMetadataArrivalNs;

	@WrapMethod(method = "tick()V")
	private void fpvdrone$measureDroneTick(Operation<Void> original) {
		long captureId = RuntimePerformanceMonitor.captureId();
		if (captureId == 0) {
			original.call();
			return;
		}
		DroneEntity drone = (DroneEntity) (Object) this;
		boolean clientSide = drone.level().isClientSide();
		int entityId = drone.getId();
		long startNs = System.nanoTime();
		long intervalNs = fpvdrone$lastTickCaptureId == captureId
				? startNs - fpvdrone$lastTickStartNs : 0;
		fpvdrone$lastTickCaptureId = captureId;
		fpvdrone$lastTickStartNs = startNs;
		try {
			original.call();
		} finally {
			RuntimePerformanceMonitor.recordDroneTick(
					captureId, clientSide, entityId, startNs, System.nanoTime(), intervalNs
			);
		}
	}

	@Inject(method = "onSyncedDataUpdated(Ljava/util/List;)V", at = @At("RETURN"))
	private void fpvdrone$recordMetadataArrival(
			List<SynchedEntityData.DataValue<?>> values,
			CallbackInfo callback
	) {
		long captureId = RuntimePerformanceMonitor.captureId();
		if (captureId == 0) {
			return;
		}
		DroneEntity drone = (DroneEntity) (Object) this;
		if (!drone.level().isClientSide()) {
			return;
		}
		// The batch callback follows assignValues and one coherent attitude-target delivery.
		long nowNs = System.nanoTime();
		long intervalNs = fpvdrone$lastMetadataCaptureId == captureId
				? nowNs - fpvdrone$lastMetadataArrivalNs : 0;
		fpvdrone$lastMetadataCaptureId = captureId;
		fpvdrone$lastMetadataArrivalNs = nowNs;
		RuntimePerformanceMonitor.recordPoseArrival(captureId, drone.getId(), false, nowNs, intervalNs);
	}
}
