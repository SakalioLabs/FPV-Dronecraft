package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tenicana.dronecraft.client.DroneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class RemoteLocalPlayerBodyMixin {
	// Neutralize reads before vanilla can toggle creative flight, start Elytra flight,
	// sink in fluid or trigger auto-jump. Sampling and input events still run normally.
	@ModifyExpressionValue(method = "*", at = {
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;jumping:Z"),
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;shiftKeyDown:Z"),
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;up:Z"),
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;down:Z"),
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;left:Z"),
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;right:Z")
	})
	private boolean fpvdrone$neutralBodyKey(boolean pressed) {
		return !fpvdrone$hasRemoteView() && pressed;
	}

	@ModifyExpressionValue(method = "*", at = {
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;leftImpulse:F"),
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/Input;forwardImpulse:F")
	})
	private float fpvdrone$neutralBodyImpulse(float impulse) {
		return fpvdrone$hasRemoteView() ? 0.0f : impulse;
	}

	@ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/Input;getMoveVector()Lnet/minecraft/world/phys/Vec2;"))
	private Vec2 fpvdrone$neutralBodyMoveVector(Vec2 movement) {
		return fpvdrone$hasRemoteView() ? Vec2.ZERO : movement;
	}

	@ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/Input;hasForwardImpulse()Z"))
	private boolean fpvdrone$neutralBodyForwardImpulse(boolean forward) {
		return !fpvdrone$hasRemoteView() && forward;
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;isControlledCamera()Z"))
	private boolean fpvdrone$sendActualBodyPosition(boolean controlledCamera) {
		// Only position reporting is enabled: camera-dependent keyboard movement stays vanilla.
		return controlledCamera || fpvdrone$hasRemoteView();
	}

	@Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V", shift = At.Shift.BEFORE))
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
