package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the single controlled target's interpolation independent of chunk arrival. */
@Mixin(ClientLevel.class)
abstract class RemoteControlledDroneTickMixin {
	@Unique private DroneEntity fpvdrone$droneBeforeEntityTick;
	@Unique private int fpvdrone$droneTickCountBeforeEntityTick;

	@Inject(method = "tickEntities", at = @At("HEAD"))
	private void fpvdrone$rememberControlledDroneTick(CallbackInfo callback) {
		fpvdrone$droneBeforeEntityTick = fpvdrone$activeControlledDrone();
		if (fpvdrone$droneBeforeEntityTick != null) {
			fpvdrone$droneTickCountBeforeEntityTick = fpvdrone$droneBeforeEntityTick.tickCount;
		}
	}

	@Inject(method = "tickEntities", at = @At("TAIL"))
	private void fpvdrone$tickControlledDroneWithoutItsChunk(CallbackInfo callback) {
		DroneEntity drone = fpvdrone$activeControlledDrone();
		ClientLevel level = (ClientLevel) (Object) this;
		if (drone != null && drone == fpvdrone$droneBeforeEntityTick
				&& drone.tickCount == fpvdrone$droneTickCountBeforeEntityTick
				&& !level.isTickingEntity(drone) && !drone.isPassenger()
				&& !level.tickRateManager().isEntityFrozen(drone)) {
			// Native tickNonPassenger increments tickCount and runs the ordinary
			// position callbacks. The count guard also prevents a second tick if
			// native interpolation moved the drone out of its ticking chunk.
			level.guardEntityTick(level::tickNonPassenger, drone);
		}
		fpvdrone$droneBeforeEntityTick = null;
	}

	@Unique
	private DroneEntity fpvdrone$activeControlledDrone() {
		Minecraft client = Minecraft.getInstance();
		return client.level == (Object) this && client.player != null && client.player.isAlive()
				&& DroneClientState.hasController() && DroneClientState.isControlledDroneValid(client)
				? DroneClientState.controlledDrone() : null;
	}
}
