package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.tenicana.dronecraft.entity.DroneEntity;

@Mixin(Minecraft.class)
public abstract class MinecraftAttackSanitizerMixin {
	@Shadow
	public ClientLevel level;

	@Shadow
	public Entity crosshairPickEntity;

	@Shadow
	public HitResult hitResult;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void fpvdrone$cancelInvalidDroneEntityAttack(CallbackInfoReturnable<Boolean> cir) {
		boolean invalidAttackTarget = false;
		if (hitResult instanceof EntityHitResult entityHit && fpvdrone$isInvalidDroneTarget(entityHit.getEntity())) {
			hitResult = null;
			invalidAttackTarget = true;
		}
		if (fpvdrone$isInvalidDroneTarget(crosshairPickEntity)) {
			crosshairPickEntity = null;
		}
		if (invalidAttackTarget) {
			cir.setReturnValue(false);
		}
	}

	private boolean fpvdrone$isInvalidDroneTarget(Entity entity) {
		return entity instanceof DroneEntity drone
				&& (level == null || drone.level() != level || drone.isRemoved() || !drone.isAlive());
	}
}
