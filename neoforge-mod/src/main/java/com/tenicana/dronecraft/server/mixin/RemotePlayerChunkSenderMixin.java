package com.tenicana.dronecraft.server.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;

import com.tenicana.dronecraft.network.DroneRemoteSessions;

@Mixin(PlayerChunkSender.class)
public abstract class RemotePlayerChunkSenderMixin {
	@Redirect(method = "sendNextChunks", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
	))
	private ChunkPos fpvdrone$prioritizeTheVisibleRegion(ServerPlayer player) {
		return DroneRemoteSessions.viewCenter(player);
	}
}
