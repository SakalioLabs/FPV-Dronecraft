package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenicana.dronecraft.client.DroneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class RemotePilotBodyRenderMixin {
	@Definition(id = "localPlayer", type = LocalPlayer.class)
	@Expression("? instanceof localPlayer")
	@WrapOperation(method = "extractVisibleEntities", at = @At("MIXINEXTRAS:EXPRESSION"))
	private boolean fpvdrone$keepRemotePilotBodyVisible(Object entity, Operation<Boolean> original) {
		boolean localPlayer = original.call(entity);
		if (!localPlayer) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		// Vanilla skips every LocalPlayer whose entity is not the camera entity. During
		// a valid drone view, the real pilot instead needs the ordinary world body render.
		// This changes only that skip: distance, frustum, terrain and visibility stay native.
		boolean remotePilot = entity == client.player
				&& client.player.isAlive()
				&& DroneClientState.isDroneCameraActive(client.level)
				&& DroneClientState.isControlledDroneValid(client)
				&& client.getCameraEntity() == DroneClientState.controlledDrone();
		return !remotePilot;
	}
}
