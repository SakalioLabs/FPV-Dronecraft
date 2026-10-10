package com.tenicana.dronecraft.server.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.network.DroneRemoteSessions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class RemoteTrackedEntityMixin {
	@Shadow
	@Final
	private Entity entity;

	@Redirect(method = "updatePlayer", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"
	))
	private Vec3 fpvdrone$trackFromTheNearestPilotOrDrone(ServerPlayer player) {
		return DroneRemoteSessions.trackingPosition(player, entity);
	}

	@WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
	private boolean fpvdrone$keepTheControlledTargetDuringChunkDelivery(
			ChunkMap chunks, ServerPlayer player, int x, int z, Operation<Boolean> original) {
		// Losing this pairing would stop the very control heartbeat that loads the new region.
		return DroneRemoteSessions.trackedDrone(player) == entity || original.call(chunks, player, x, z);
	}
}
