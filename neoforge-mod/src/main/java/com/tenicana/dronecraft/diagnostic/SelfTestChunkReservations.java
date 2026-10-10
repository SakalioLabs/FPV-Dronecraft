package com.tenicana.dronecraft.diagnostic;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Keeps the headless self-test's moving flight area loaded without owning existing reservations. */
final class SelfTestChunkReservations {
	private static final int HALO_RADIUS_CHUNKS = 1;
	private final Set<Chunk> owned = new LinkedHashSet<>();

	void reserveAround(double x, double z, ChunkForcer forcer) {
		int centerX = ((int) Math.floor(x)) >> 4;
		int centerZ = ((int) Math.floor(z)) >> 4;
		Set<Chunk> required = new LinkedHashSet<>();
		for (int chunkX = centerX - HALO_RADIUS_CHUNKS; chunkX <= centerX + HALO_RADIUS_CHUNKS; chunkX++) {
			for (int chunkZ = centerZ - HALO_RADIUS_CHUNKS; chunkZ <= centerZ + HALO_RADIUS_CHUNKS; chunkZ++) {
				Chunk chunk = new Chunk(chunkX, chunkZ);
				required.add(chunk);
				// Native setChunkForced returns true only when it changes the forced state.
				if (!owned.contains(chunk) && forcer.setForced(chunkX, chunkZ, true)) {
					owned.add(chunk);
				}
			}
		}
		// Acquire the new frontier before releasing the trailing edge.
		Iterator<Chunk> iterator = owned.iterator();
		while (iterator.hasNext()) {
			Chunk chunk = iterator.next();
			if (!required.contains(chunk)) {
				forcer.setForced(chunk.x(), chunk.z(), false);
				iterator.remove();
			}
		}
	}

	void releaseAll(ChunkForcer forcer) {
		Iterator<Chunk> iterator = owned.iterator();
		while (iterator.hasNext()) {
			Chunk chunk = iterator.next();
			forcer.setForced(chunk.x(), chunk.z(), false);
			iterator.remove();
		}
	}

	@FunctionalInterface
	interface ChunkForcer {
		boolean setForced(int x, int z, boolean forced);
	}

	private record Chunk(int x, int z) { }
}
