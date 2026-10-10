package com.tenicana.dronecraft.performance.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tenicana.dronecraft.performance.DronePlacementBindingSmoke;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe inherited AvatarRenderer submission without injecting additional player rendering. */
@Mixin(LivingEntityRenderer.class)
public abstract class PilotBodySubmissionProbeMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"))
	private void fpvPerformance$pilotSubmitted(LivingEntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback) {
		if (DronePlacementBindingSmoke.ENABLED) DronePlacementBindingSmoke.onPilotSubmitted(state);
	}
}
