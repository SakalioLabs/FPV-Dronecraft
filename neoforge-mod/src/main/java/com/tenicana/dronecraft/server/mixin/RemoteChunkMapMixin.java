package com.tenicana.dronecraft.server.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.network.DroneRemoteSessions;
import com.tenicana.dronecraft.remote.DroneChunkTrackingView;
import com.tenicana.dronecraft.remote.RemoteChunkMapAccess;
import com.tenicana.dronecraft.network.DroneChunkCentersPayload;
import net.neoforged.neoforge.network.PacketDistributor;

@Mixin(ChunkMap.class)
public abstract class RemoteChunkMapMixin implements RemoteChunkMapAccess {
	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	abstract int getPlayerViewDistance(ServerPlayer player);

	@Override
	public int fpvdrone$playerViewDistance(ServerPlayer player) {
		return getPlayerViewDistance(player);
	}

	@Shadow
	private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view) {
		throw new AssertionError("Shadow method");
	}

	@Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
	private void fpvdrone$subscribeToPilotAndDrone(ServerPlayer player, CallbackInfo ci) {
		DroneEntity drone = DroneRemoteSessions.trackedDrone(player);
		if (drone == null || player.level() != level) {
			return;
		}
		DroneChunkTrackingView next = new DroneChunkTrackingView(
				player.chunkPosition(), drone.chunkPosition(), getPlayerViewDistance(player),
				DroneRemoteSessions.viewCenter(player)
		);
		if (!next.equals(player.getChunkTrackingView())) {
			applyChunkTrackingView(player, next);
		}
		ci.cancel();
	}

	@Inject(method = "applyChunkTrackingView", at = @At("HEAD"))
	private void fpvdrone$sendRemoteViewCenter(ServerPlayer player, ChunkTrackingView view, CallbackInfo ci) {
		if (player.level() != level) {
			return;
		}
		if (!(view instanceof DroneChunkTrackingView remote)) {
			if (player.getChunkTrackingView() instanceof DroneChunkTrackingView
					&& player.connection.hasChannel(DroneChunkCentersPayload.TYPE.id())) {
				PacketDistributor.sendToPlayer(player, new DroneChunkCentersPayload(false, 0, 0, 0, 0));
			}
			return;
		}
		if (player.connection.hasChannel(DroneChunkCentersPayload.TYPE.id())) {
			PacketDistributor.sendToPlayer(player, new DroneChunkCentersPayload(true,
					remote.bodyCenter().x, remote.bodyCenter().z, remote.droneCenter().x, remote.droneCenter().z));
		}
		ChunkPos previousCenter = DroneChunkTrackingView.primaryCenter(player.getChunkTrackingView());
		if (!remote.primaryCenter().equals(previousCenter)) {
			player.connection.send(new ClientboundSetChunkCacheCenterPacket(
					remote.primaryCenter().x, remote.primaryCenter().z
			));
		}
	}

	@Redirect(method = "applyChunkTrackingView", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkTrackingView;difference(Lnet/minecraft/server/level/ChunkTrackingView;Lnet/minecraft/server/level/ChunkTrackingView;Ljava/util/function/Consumer;Ljava/util/function/Consumer;)V"
	))
	private void fpvdrone$updateOnlyChangedSubscriptions(
			ChunkTrackingView previous,
			ChunkTrackingView next,
			Consumer<ChunkPos> added,
			Consumer<ChunkPos> removed
	) {
		DroneChunkTrackingView.difference(previous, next, added, removed);
	}
}
