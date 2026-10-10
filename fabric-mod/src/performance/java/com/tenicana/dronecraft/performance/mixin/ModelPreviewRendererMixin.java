package com.tenicana.dronecraft.performance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tenicana.dronecraft.client.render.DroneEntityRenderState;
import com.tenicana.dronecraft.client.render.DroneEntityRenderer;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.performance.ModelPreviewCapture;
import com.tenicana.dronecraft.performance.ThreeViewSmoke;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;

/** This mixin belongs only to the disposable smoke helper, never the shipped mod. */
@Mixin(DroneEntityRenderer.class)
public abstract class ModelPreviewRendererMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"), remap = false)
	private void fpvPerformance$modelPose(DroneEntity entity, DroneEntityRenderState state, float partialTick, CallbackInfo ci) {
		if (ModelPreviewCapture.ENABLED) ModelPreviewCapture.applyRenderPose(entity, state);
		if (ThreeViewSmoke.ENABLED) ThreeViewSmoke.onExtracted(entity, state);
	}

	@Inject(method = "submit", at = @At("HEAD"), remap = false)
	private void fpvPerformance$modelSubmitted(DroneEntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (ModelPreviewCapture.ENABLED) ModelPreviewCapture.onSubmitted(state);
		if (ThreeViewSmoke.ENABLED) ThreeViewSmoke.onSubmitted(state);
	}
}
