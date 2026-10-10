package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenicana.dronecraft.performance.ControllerModelSmoke;
import com.tenicana.dronecraft.performance.DronePlacementBindingSmoke;

import net.minecraft.client.renderer.ItemInHandRenderer;

/** Observes actual vanilla hands/held-item rendering without changing it. */
@Mixin(ItemInHandRenderer.class)
public abstract class ControllerHandsProbeMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
	private void fpvPerformance$controllerVanillaHands(CallbackInfo ci) {
		if (ControllerModelSmoke.ENABLED) ControllerModelSmoke.onVanillaHands();
		if (DronePlacementBindingSmoke.ENABLED) DronePlacementBindingSmoke.onVanillaHands();
	}
}
