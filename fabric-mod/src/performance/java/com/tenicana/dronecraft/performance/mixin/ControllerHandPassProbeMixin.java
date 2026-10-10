package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenicana.dronecraft.performance.ControllerModelSmoke;
import com.tenicana.dronecraft.performance.DronePlacementBindingSmoke;

import net.minecraft.client.renderer.GameRenderer;

/** Counts the native hand pass before production camera suppression; never cancels. */
@Mixin(GameRenderer.class)
public abstract class ControllerHandPassProbeMixin {
	@Inject(method = "renderItemInHand(FZLorg/joml/Matrix4f;)V", at = @At("HEAD"))
	private void fpvPerformance$controllerHandPass(CallbackInfo ci) {
		if (ControllerModelSmoke.ENABLED) ControllerModelSmoke.onHandPass();
		if (DronePlacementBindingSmoke.ENABLED) DronePlacementBindingSmoke.onHandPass();
	}
}
