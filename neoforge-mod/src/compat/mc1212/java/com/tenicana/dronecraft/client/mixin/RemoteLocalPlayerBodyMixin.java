package com.tenicana.dronecraft.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tenicana.dronecraft.client.DroneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class RemoteLocalPlayerBodyMixin {
	// 1.21.2 introduced the key-state record while retaining public scalar impulses.
	// Neutral reads cover native flight, fluid, sprint and auto-jump checks without
	// replacing the real input object or skipping its sampling and mod input events.
	@ModifyExpressionValue(method = "*", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/player/ClientInput;keyPresses:Lnet/minecraft/world/entity/player/Input;"))
	private Input fpvdrone$neutralBodyKeys(Input keys) {
		return fpvdrone$hasRemoteView() ? Input.EMPTY : keys;
	}

	@ModifyExpressionValue(method = "*", at = {
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/ClientInput;leftImpulse:F"),
			@At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/player/ClientInput;forwardImpulse:F")
	})
	private float fpvdrone$neutralBodyImpulse(float impulse) {
		return fpvdrone$hasRemoteView() ? 0.0f : impulse;
	}

	@ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/ClientInput;getMoveVector()Lnet/minecraft/world/phys/Vec2;"))
	private Vec2 fpvdrone$neutralBodyMoveVector(Vec2 movement) {
		return fpvdrone$hasRemoteView() ? Vec2.ZERO : movement;
	}

	@ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
	private boolean fpvdrone$neutralBodyForwardImpulse(boolean forward) {
		return !fpvdrone$hasRemoteView() && forward;
	}

	@ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;isControlledCamera()Z"))
	private boolean fpvdrone$sendActualBodyPosition(boolean controlledCamera) {
		return controlledCamera || fpvdrone$hasRemoteView();
	}

	@Inject(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V", shift = At.Shift.BEFORE))
	private void fpvdrone$clearPreviousBodyInput(CallbackInfo callback) {
		if (fpvdrone$hasRemoteView()) {
			LocalPlayer body = (LocalPlayer) (Object) this;
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
