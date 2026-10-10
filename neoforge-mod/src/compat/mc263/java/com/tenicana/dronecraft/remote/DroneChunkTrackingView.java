package com.tenicana.dronecraft.remote;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

/** Keeps the pilot's physical surroundings and the controlled drone in the same subscription. */
public record DroneChunkTrackingView(
		ChunkPos bodyCenter,
		ChunkPos droneCenter,
		int viewDistance,
		ChunkPos primaryCenter
) implements ChunkTrackingView {
	public DroneChunkTrackingView {
		Objects.requireNonNull(bodyCenter, "bodyCenter");
		Objects.requireNonNull(droneCenter, "droneCenter");
		Objects.requireNonNull(primaryCenter, "primaryCenter");
		if (viewDistance < 0) {
			throw new IllegalArgumentException("viewDistance must be nonnegative");
		}
	}

	@Override
	public boolean contains(int chunkX, int chunkZ, boolean includeBorder) {
		return containsAround(bodyCenter, chunkX, chunkZ, includeBorder)
				|| containsAround(droneCenter, chunkX, chunkZ, includeBorder);
	}

	@Override
	public void forEach(Consumer<ChunkPos> consumer) {
		forEachAround(bodyCenter, consumer);
		if (!bodyCenter.equals(droneCenter)) {
			forEachAround(droneCenter, chunk -> {
				if (!containsAround(bodyCenter, chunk.x(), chunk.z(), true)) {
					consumer.accept(chunk);
				}
			});
		}
	}

	/** The vanilla fallback resends every chunk for non-Positioned views; remote views need a true delta. */
	public static void difference(
			ChunkTrackingView previous,
			ChunkTrackingView next,
			Consumer<ChunkPos> added,
			Consumer<ChunkPos> removed
	) {
		if (!(previous instanceof DroneChunkTrackingView) && !(next instanceof DroneChunkTrackingView)) {
			ChunkTrackingView.difference(previous, next, added, removed);
			return;
		}
		if (previous.equals(next)) {
			return;
		}
		previous.forEach(chunk -> {
			if (!next.contains(chunk)) {
				removed.accept(chunk);
			}
		});
		next.forEach(chunk -> {
			if (!previous.contains(chunk)) {
				added.accept(chunk);
			}
		});
	}

	public static ChunkPos primaryCenter(ChunkTrackingView view) {
		if (view instanceof DroneChunkTrackingView remote) {
			return remote.primaryCenter();
		}
		return view instanceof ChunkTrackingView.Positioned positioned ? positioned.center() : null;
	}

	private boolean containsAround(ChunkPos center, int chunkX, int chunkZ, boolean includeBorder) {
		return ChunkTrackingView.isWithinDistance(
				center.x(), center.z(), viewDistance, chunkX, chunkZ, includeBorder
		);
	}

	private void forEachAround(ChunkPos center, Consumer<ChunkPos> consumer) {
		// Iterate each bounded region separately: the gap between a distant pilot and drone is never scanned.
		int extent = viewDistance + 1;
		for (int chunkX = center.x() - extent; chunkX <= center.x() + extent; chunkX++) {
			for (int chunkZ = center.z() - extent; chunkZ <= center.z() + extent; chunkZ++) {
				if (containsAround(center, chunkX, chunkZ, true)) {
					consumer.accept(new ChunkPos(chunkX, chunkZ));
				}
			}
		}
	}
}
