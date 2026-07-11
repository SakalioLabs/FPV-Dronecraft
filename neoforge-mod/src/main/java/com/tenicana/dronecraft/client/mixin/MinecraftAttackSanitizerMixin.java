package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.tenicana.dronecraft.client.camera.ClientCameraSafety;

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
		if (fpvdrone$clearInvalidEntityTarget()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void fpvdrone$cancelInvalidDroneEntityUse(CallbackInfo ci) {
		if (fpvdrone$clearInvalidEntityTarget()) {
			ci.cancel();
		}
	}

	private boolean fpvdrone$clearInvalidEntityTarget() {
		boolean invalidTarget = false;
		if (hitResult instanceof EntityHitResult entityHit && fpvdrone$isInvalidEntityTarget(entityHit.getEntity())) {
			hitResult = null;
			invalidTarget = true;
		}
		if (fpvdrone$isInvalidEntityTarget(crosshairPickEntity)) {
			crosshairPickEntity = null;
			invalidTarget = true;
		}
		return invalidTarget;
	}

	private boolean fpvdrone$isInvalidEntityTarget(Entity entity) {
		return ClientCameraSafety.isInvalidEntityTarget(
				entity != null,
				level != null,
				entity != null && entity.level() == level,
				entity != null && entity.isRemoved(),
				entity != null && entity.isAlive()
		);
	}
}
