package com.tenicana.dronecraft.client.mixin;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.chunk.RemoteClientChunkCacheAccess;
import com.tenicana.dronecraft.client.chunk.RemoteClientChunkStorage;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
abstract class RemoteClientChunkCacheMixin implements RemoteClientChunkCacheAccess {
	@Shadow private volatile ClientChunkCache.Storage storage;
	@Unique private RemoteClientChunkStorage fpvdrone$remoteStorage;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fpvdrone$initializeRemoteStorage(ClientLevel level, int viewDistance, CallbackInfo callback) {
		fpvdrone$remoteStorage = new RemoteClientChunkStorage((ClientChunkCache) (Object) this, level, storage);
	}

	@Redirect(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
			at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
					target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;storage:Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"))
	private ClientChunkCache.Storage fpvdrone$queryStorage(ClientChunkCache owner, int x, int z, ChunkStatus status, boolean create) {
		return fpvdrone$storageFor(x, z);
	}

	@Redirect(method = "replaceWithPacketData", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;storage:Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"))
	private ClientChunkCache.Storage fpvdrone$packetStorage(ClientChunkCache owner, int x, int z, FriendlyByteBuf data,
			CompoundTag heightmaps, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities) {
		return fpvdrone$storageFor(x, z);
	}

	@Redirect(method = "replaceBiomes", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;storage:Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"))
	private ClientChunkCache.Storage fpvdrone$biomeStorage(ClientChunkCache owner, int x, int z, FriendlyByteBuf data) {
		return fpvdrone$storageFor(x, z);
	}

	@Redirect(method = "drop", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;storage:Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"))
	private ClientChunkCache.Storage fpvdrone$dropStorage(ClientChunkCache owner, ChunkPos pos) {
		return fpvdrone$storageFor(pos.x, pos.z);
	}

	@WrapMethod(method = "updateViewCenter")
	private void fpvdrone$moveCacheCenter(int x, int z, Operation<Void> original) {
		RemoteClientChunkStorage.Move move = fpvdrone$remoteStorage.prepareCenterChange(storage, x, z);
		original.call(x, z);
		fpvdrone$remoteStorage.completeMove(storage, move);
	}

	@WrapMethod(method = "updateViewRadius")
	private void fpvdrone$changeCacheRadius(int viewDistance, Operation<Void> original) {
		// Vanilla calculateStorageRange is max(2, viewDistance) + 3 in this target version.
		RemoteClientChunkStorage.Move move = fpvdrone$remoteStorage.prepareRadiusChange(storage, Math.max(2, viewDistance) + 3);
		original.call(viewDistance);
		fpvdrone$remoteStorage.completeMove(storage, move);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fpvdrone$updateOtherCacheCenter(BooleanSupplier hasTime, boolean tickChunks, CallbackInfo callback) {
		fpvdrone$remoteStorage.tick(storage);
	}

	@ModifyReturnValue(method = "getLoadedChunksCount", at = @At("RETURN"))
	private int fpvdrone$combinedChunkCount(int primaryCount) {
		return primaryCount + fpvdrone$secondaryChunkCount();
	}

	@Override
	public void fpvdrone$setRemoteCenters(boolean active, int bodyX, int bodyZ, int droneX, int droneZ) {
		if (fpvdrone$remoteStorage != null) {
			fpvdrone$remoteStorage.setRemoteCenters(storage, active, bodyX, bodyZ, droneX, droneZ);
		}
	}

	@Override
	public int fpvdrone$secondaryChunkCount() {
		return fpvdrone$remoteStorage == null ? 0 : fpvdrone$remoteStorage.secondaryChunkCount();
	}

	@Override
	public boolean fpvdrone$hasSecondaryChunk(int x, int z) {
		return fpvdrone$remoteStorage != null && fpvdrone$remoteStorage.hasSecondaryChunk(x, z);
	}

	@Unique
	private ClientChunkCache.Storage fpvdrone$storageFor(int x, int z) {
		return fpvdrone$remoteStorage == null ? storage : fpvdrone$remoteStorage.storageFor(storage, x, z);
	}
}
