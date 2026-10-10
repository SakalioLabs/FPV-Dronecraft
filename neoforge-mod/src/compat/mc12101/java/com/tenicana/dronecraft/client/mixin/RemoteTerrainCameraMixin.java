package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tenicana.dronecraft.client.DroneClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class RemoteTerrainCameraMixin {
	// 1.21.1 centers the terrain ring on the player body. Substitute only its
	// anchor coordinates so the native section-change gate and culling stay intact.
	@WrapOperation(method = "setupRender", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
	private double fpvdrone$terrainCameraX(LocalPlayer player, Operation<Double> original,
			@Local(argsOnly = true) Camera camera) {
		return fpvdrone$useDroneCamera() ? camera.getPosition().x : original.call(player);
	}

	@WrapOperation(method = "setupRender", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
	private double fpvdrone$terrainCameraY(LocalPlayer player, Operation<Double> original,
			@Local(argsOnly = true) Camera camera) {
		return fpvdrone$useDroneCamera() ? camera.getPosition().y : original.call(player);
	}

	@WrapOperation(method = "setupRender", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
	private double fpvdrone$terrainCameraZ(LocalPlayer player, Operation<Double> original,
			@Local(argsOnly = true) Camera camera) {
		return fpvdrone$useDroneCamera() ? camera.getPosition().z : original.call(player);
	}

	@Unique
	private static boolean fpvdrone$useDroneCamera() {
		Minecraft client = Minecraft.getInstance();
		return client != null
				&& client.player != null
				&& client.player.isAlive()
				&& DroneClientState.isDroneCameraActive(client.level)
				&& DroneClientState.isControlledDroneValid(client)
				&& client.getCameraEntity() == DroneClientState.controlledDrone();
	}
}
