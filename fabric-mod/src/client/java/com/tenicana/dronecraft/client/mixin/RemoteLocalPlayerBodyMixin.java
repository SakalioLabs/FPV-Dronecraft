package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tenicana.dronecraft.client.DroneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class RemoteLocalPlayerBodyMixin {
	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;isControlledCamera()Z"))
	private boolean fpvdrone$sendActualBodyPosition(boolean controlledCamera) {
		// Only position reporting is enabled: camera-dependent keyboard movement stays vanilla.
		return controlledCamera || fpvdrone$hasRemoteView();
	}

	@Inject(method = "applyInput", at = @At("TAIL"))
	private void fpvdrone$clearPreviousBodyInput(CallbackInfo callback) {
		if (fpvdrone$hasRemoteView()) {
			LocalPlayer body = (LocalPlayer) (Object) this;
			// Vanilla's remote-camera branch merely decays the previous walking input by 0.98.
			body.xxa = 0.0f;
			body.zza = 0.0f;
			body.setJumping(false);
		}
	}

	@Unique
	private boolean fpvdrone$hasRemoteView() {
		Minecraft client = Minecraft.getInstance();
		return client.player == (Object) this
				&& DroneClientState.isDroneCameraActive(client.level)
				&& client.getCameraEntity() == DroneClientState.controlledDrone();
	}
}
