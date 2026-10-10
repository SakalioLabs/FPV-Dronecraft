package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.performance.NormalClientPerformanceProbe;
import com.tenicana.dronecraft.performance.PerformanceTimings;

@Mixin(value = DroneEntity.class)
public abstract class DroneTickTimingMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void fpvPerformance$entityTick(CallbackInfo ci) {
		DroneEntity entity = (DroneEntity) (Object) this;
		if (NormalClientPerformanceProbe.isProbeDrone(entity.getUUID())) PerformanceTimings.droneTick(entity.level().isClientSide());
	}
}
