package com.tenicana.dronecraft.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

import com.tenicana.dronecraft.client.camera.ClientCameraSafety;
import com.tenicana.dronecraft.camera.DroneThirdPersonCamera;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.entity.DroneEntity;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand(FZLorg/joml/Matrix4f;)V", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
			shift = At.Shift.AFTER
	), cancellable = true)
	private void fpvdrone$hideDroneCameraHands(float partialTick, boolean sleeping, Matrix4f viewMatrix, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (DroneClientState.isDroneCameraActive(client.level)) {
			// Keep the feature/buffer flush, and return before vanilla pushes the hand matrices.
			ci.cancel();
		}
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void fpvdrone$overrideFpvFov(Camera camera, float partialTick, boolean useFovSetting,
			CallbackInfoReturnable<Float> cir) {
		DroneEntity drone = DroneClientState.controlledDrone();
		Minecraft client = Minecraft.getInstance();
		if (DroneClientState.isThirdPersonActive(client.level)) {
			cir.setReturnValue(DroneThirdPersonCamera.FOV_DEGREES);
			return;
		}
		if (!ClientCameraSafety.isUsableFpvDroneReference(
				DroneClientState.isFpvActive(client.level),
				drone != null,
				drone != null && drone.level() == client.level,
				drone != null && drone.isRemoved(),
				drone != null && drone.isAlive()
		)) {
			return;
		}

		DroneClientConfig config = DroneClientControls.config();
		float speed = Math.max(drone.getAirspeedMetersPerSecond(), drone.getSpeedMetersPerSecond());
		float speedStretch = Mth.clamp((speed - 2.0f) / 22.0f, 0.0f, 1.0f);
		float throttleStretch = Mth.clamp((drone.getMotorPower() - 0.25f) / 0.65f, 0.0f, 1.0f);
		float dynamicFov = config.cameraDynamicFovDegrees() * (0.70f * speedStretch + 0.30f * throttleStretch);
		cir.setReturnValue(Mth.clamp(config.cameraFovDegrees() + dynamicFov, 70.0f, 140.0f));
	}
}
