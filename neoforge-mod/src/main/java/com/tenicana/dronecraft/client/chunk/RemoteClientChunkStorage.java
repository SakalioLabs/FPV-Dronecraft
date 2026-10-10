package com.tenicana.dronecraft.client.chunk;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Two bounded native storage arrays share one ClientLevel and one light engine.
 * Each chunk belongs to exactly one array. Moving a view transfers references;
 * only chunks leaving both local regions use the ordinary cache.drop lifecycle.
 */
public final class RemoteClientChunkStorage {
	private final ClientChunkCache cache;
	private final ClientLevel level;
	private volatile ClientChunkCache.Storage secondary;
	private boolean receivedRemoteCenters;
	private boolean remoteCentersActive;
	private ChunkPos authoritativeBodyCenter;
	private ChunkPos authoritativeDroneCenter;

	public RemoteClientChunkStorage(ClientChunkCache cache, ClientLevel level, ClientChunkCache.Storage primary) {
		this.cache = cache;
		this.level = level;
	}

	/** The packet and query methods themselves still run in vanilla, with their mod hooks. */
	public ClientChunkCache.Storage storageFor(ClientChunkCache.Storage primary, int chunkX, int chunkZ) {
		if (hasChunk(primary, chunkX, chunkZ)) {
			return primary;
		}
		ClientChunkCache.Storage other = secondary;
		if (other != null && hasChunk(other, chunkX, chunkZ)) {
			return other;
		}
		if (primary.inRange(chunkX, chunkZ)) {
			return primary;
		}
		return other != null && other.inRange(chunkX, chunkZ) ? other : primary;
	}

	public int secondaryChunkCount() {
		ClientChunkCache.Storage other = secondary;
		return other == null ? 0 : other.chunkCount;
	}

	public boolean hasSecondaryChunk(int chunkX, int chunkZ) {
		ClientChunkCache.Storage other = secondary;
		return other != null && hasChunk(other, chunkX, chunkZ);
	}

	/** Delivered before vanilla's primary center and the chunks for this subscription. */
	public void setRemoteCenters(ClientChunkCache.Storage primary, boolean active,
			int bodyX, int bodyZ, int droneX, int droneZ) {
		receivedRemoteCenters = true;
		remoteCentersActive = active;
		authoritativeBodyCenter = new ChunkPos(bodyX, bodyZ);
		authoritativeDroneCenter = active ? new ChunkPos(droneX, droneZ) : null;
		tick(primary);
	}

	public void tick(ClientChunkCache.Storage primary) {
		ChunkPos otherCenter = secondaryCenter(primary.viewCenterX, primary.viewCenterZ, primary.chunkRadius);
		ClientChunkCache.Storage other = secondary;
		if (sameCenter(other, otherCenter, primary.chunkRadius)) {
			return;
		}
		List<LevelChunk> retained = prepareMove(primary, primary.viewCenterX, primary.viewCenterZ, primary.chunkRadius, otherCenter);
		completeMove(primary, otherCenter, retained);
	}

	public Move prepareCenterChange(ClientChunkCache.Storage primary, int centerX, int centerZ) {
		ChunkPos otherCenter = secondaryCenter(centerX, centerZ, primary.chunkRadius);
		if (centerX == primary.viewCenterX && centerZ == primary.viewCenterZ
				&& sameCenter(secondary, otherCenter, primary.chunkRadius)) {
			return null;
		}
		return new Move(otherCenter, prepareMove(primary, centerX, centerZ, primary.chunkRadius, otherCenter));
	}

	public Move prepareRadiusChange(ClientChunkCache.Storage primary, int nativeStorageRadius) {
		ChunkPos otherCenter = secondaryCenter(primary.viewCenterX, primary.viewCenterZ, nativeStorageRadius);
		if (primary.chunkRadius == nativeStorageRadius && sameCenter(secondary, otherCenter, nativeStorageRadius)) {
			return null;
		}
		return new Move(otherCenter, prepareMove(primary, primary.viewCenterX, primary.viewCenterZ, nativeStorageRadius, otherCenter));
	}

	public void completeMove(ClientChunkCache.Storage primary, Move move) {
		if (move != null) {
			completeMove(primary, move.otherCenter(), move.retained());
		}
	}

	private List<LevelChunk> prepareMove(ClientChunkCache.Storage primary, int centerX, int centerZ, int radius, ChunkPos otherCenter) {
		List<LevelChunk> chunks = snapshot(primary);
		ClientChunkCache.Storage previousSecondary = secondary;
		if (previousSecondary != null) {
			chunks.addAll(snapshot(previousSecondary));
		}
		List<LevelChunk> retained = new ArrayList<>(chunks.size());
		for (LevelChunk chunk : chunks) {
			ChunkPos pos = chunk.getPos();
			if (inRange(centerX, centerZ, radius, pos.x, pos.z)
					|| (otherCenter != null && inRange(otherCenter.x, otherCenter.z, radius, pos.x, pos.z))) {
				retained.add(chunk);
			} else {
				// Calls NeoForge/Sodium's native drop hooks and ClientLevel.unload before changing centers.
				cache.drop(pos);
			}
		}
		clearReferences(primary);
		if (previousSecondary != null) {
			clearReferences(previousSecondary);
		}
		return retained;
	}

	private void completeMove(ClientChunkCache.Storage primary, ChunkPos otherCenter, List<LevelChunk> retained) {
		ClientChunkCache.Storage other = null;
		if (otherCenter != null) {
			other = secondary;
			if (other == null || other.chunkRadius != primary.chunkRadius) {
				other = cache.new Storage(primary.chunkRadius);
			}
			other.viewCenterX = otherCenter.x;
			other.viewCenterZ = otherCenter.z;
		}
		secondary = other;
		for (LevelChunk chunk : retained) {
			ChunkPos pos = chunk.getPos();
			ClientChunkCache.Storage owner = primary.inRange(pos.x, pos.z) ? primary : other;
			if (owner != null && owner.inRange(pos.x, pos.z)) {
				putTransferredReference(owner, chunk);
			}
		}
	}

	private ChunkPos secondaryCenter(int primaryX, int primaryZ, int radius) {
		Minecraft client = Minecraft.getInstance();
		if (client.level != level || client.player == null) {
			return null;
		}
		ChunkPos body = client.player.chunkPosition();
		if (receivedRemoteCenters) {
			if (remoteCentersActive && client.player.isAlive()) {
				if (matchesCenter(primaryX, primaryZ, authoritativeBodyCenter)) {
					return authoritativeDroneCenter;
				}
				if (matchesCenter(primaryX, primaryZ, authoritativeDroneCenter)) {
					return authoritativeBodyCenter;
				}
				// The authoritative union arrives before vanilla's new primary center.
				// Keep both existing arrays until that following packet selects the other
				// anchor: choosing a future drone center now could discard cached body
				// chunks that the server correctly considers already delivered.
				ClientChunkCache.Storage other = secondary;
				return other == null ? null : new ChunkPos(other.viewCenterX, other.viewCenterZ);
			}
			// An inactive server subscription must not be revived by a cached local
			// controller flag. Preserve body collision data until its center arrives.
			return inRange(primaryX, primaryZ, radius, body.x, body.z) ? null : body;
		}
		DroneEntity drone = DroneClientState.controlledDrone();
		boolean linked = client.player.isAlive() && DroneClientState.hasController()
				&& DroneClientState.isControlledDroneValid(client);
		if (linked) {
			ChunkPos droneCenter = drone.chunkPosition();
			// The authoritative center packet may arrive a tick after a local B-key switch.
			// Select the other anchor using that actual center, not the optimistic view mode.
			return distanceSquared(primaryX, primaryZ, body) >= distanceSquared(primaryX, primaryZ, droneCenter)
					? body : droneCenter;
		}
		// Keep body collision data during the short return-to-LOS packet transition.
		return inRange(primaryX, primaryZ, radius, body.x, body.z) ? null : body;
	}

	private static boolean matchesCenter(int x, int z, ChunkPos center) {
		return center != null && x == center.x && z == center.z;
	}

	private static boolean sameCenter(ClientChunkCache.Storage storage, ChunkPos center, int radius) {
		return center == null ? storage == null : storage != null && storage.chunkRadius == radius
				&& storage.viewCenterX == center.x && storage.viewCenterZ == center.z;
	}

	private static long distanceSquared(int x, int z, ChunkPos pos) {
		long dx = (long) x - pos.x;
		long dz = (long) z - pos.z;
		return dx * dx + dz * dz;
	}

	private static boolean inRange(int centerX, int centerZ, int radius, int chunkX, int chunkZ) {
		return Math.abs((long) chunkX - centerX) <= radius && Math.abs((long) chunkZ - centerZ) <= radius;
	}

	private static boolean hasChunk(ClientChunkCache.Storage storage, int chunkX, int chunkZ) {
		if (!storage.inRange(chunkX, chunkZ)) {
			return false;
		}
		LevelChunk chunk = storage.getChunk(storage.getIndex(chunkX, chunkZ));
		return chunk != null && chunk.getPos().x == chunkX && chunk.getPos().z == chunkZ;
	}

	private static List<LevelChunk> snapshot(ClientChunkCache.Storage storage) {
		List<LevelChunk> chunks = new ArrayList<>(storage.chunkCount);
		for (int index = 0; index < storage.chunks.length(); index++) {
			LevelChunk chunk = storage.chunks.get(index);
			if (chunk != null) {
				chunks.add(chunk);
			}
		}
		return chunks;
	}

	private static void clearReferences(ClientChunkCache.Storage storage) {
		for (int index = 0; index < storage.chunks.length(); index++) {
			storage.chunks.set(index, null);
		}
		storage.chunkCount = 0;
	}

	private static void putTransferredReference(ClientChunkCache.Storage storage, LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		storage.chunks.set(storage.getIndex(pos.x, pos.z), chunk);
		storage.chunkCount++;

	}

	public record Move(ChunkPos otherCenter, List<LevelChunk> retained) {
	}
}
