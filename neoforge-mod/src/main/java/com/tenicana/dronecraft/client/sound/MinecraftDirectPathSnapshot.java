package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.path.SparseAirGrid;
import com.tenicana.dronecraft.acoustics.path.VoxelBounds;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.propagation.MultiPathSolver;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

final class MinecraftDirectPathSnapshot {
	private static final int AIR_CORRIDOR_RADIUS = 3;

	private MinecraftDirectPathSnapshot() {
	}

	static MinecraftPropagationSnapshot capture(
			ClientLevel level,
			List<MultiPathSolver.Probe> probes,
			AcousticVector listener,
			int maxCells,
			boolean captureAirCorridor
	) {
		SparseMaterialSnapshot.Builder snapshot = SparseMaterialSnapshot.builder();
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		LongOpenHashSet sampledCells = new LongOpenHashSet();
		for (MultiPathSolver.Probe probe : probes) {
			VoxelDda.WalkResult walk = VoxelDda.walk(
					probe.position(),
					listener,
					(x, y, z, pathLengthMeters) -> {
						if (!sampledCells.add(pack(x, y, z))) {
							return true;
						}
						position.set(x, y, z);
						if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
							snapshot.markIncomplete();
							return true;
						}
						snapshot.put(
								x,
								y,
								z,
								MinecraftAcousticMaterials.sample(
										level,
										position,
										level.getBlockState(position)
								)
						);
						return true;
					},
					maxCells
			);
			if (!walk.reachedEnd()) {
				snapshot.markIncomplete();
			}
		}
		AcousticVector source = probes.getFirst().position();
		VoxelDda.Cell sourceCell = cellAt(source);
		VoxelDda.Cell listenerCell = cellAt(listener);
		Optional<SparseAirGrid> airGrid = captureAirCorridor
				? Optional.of(captureAirGrid(
						level,
						source,
						listener,
						sourceCell,
						listenerCell,
						maxCells
				))
				: Optional.empty();
		return new MinecraftPropagationSnapshot(
				snapshot.build(),
				airGrid,
				sourceCell,
				listenerCell
		);
	}

	/**
	 * Converts an already captured runtime snapshot into the portable offline
	 * CPU/native parity contract. This method is diagnostic-only.
	 */
	static DdaProductionSnapshotBundle.Bundle diagnosticBundle(
			MinecraftPropagationSnapshot snapshot,
			List<MultiPathSolver.Probe> probes,
			AcousticVector listener,
			int maxCells,
			String minecraftVersion,
			String modVersion,
			long snapshotGeneration
	) {
		List<DdaProductionSnapshotBundle.Ray> rays = probes.stream()
				.map(probe -> new DdaProductionSnapshotBundle.Ray(
						probe.position(),
						listener,
						maxCells
				))
				.toList();
		return new DdaProductionSnapshotBundle.Bundle(
				new DdaProductionSnapshotBundle.Metadata(
						MinecraftAcousticMaterials.MAPPING_ALGORITHM_VERSION,
						minecraftVersion,
						modVersion,
						MinecraftAcousticMaterials
								.diagnosticContentFingerprintSha256(),
						snapshotGeneration
				),
				snapshot.materials(),
				rays
		);
	}

	private static SparseAirGrid captureAirGrid(
			ClientLevel level,
			AcousticVector source,
			AcousticVector listener,
			VoxelDda.Cell sourceCell,
			VoxelDda.Cell listenerCell,
			int maxCells
	) {
		VoxelBounds bounds = new VoxelBounds(
				Math.min(sourceCell.x(), listenerCell.x()) - AIR_CORRIDOR_RADIUS,
				Math.min(sourceCell.y(), listenerCell.y()) - AIR_CORRIDOR_RADIUS,
				Math.min(sourceCell.z(), listenerCell.z()) - AIR_CORRIDOR_RADIUS,
				Math.max(sourceCell.x(), listenerCell.x()) + AIR_CORRIDOR_RADIUS,
				Math.max(sourceCell.y(), listenerCell.y()) + AIR_CORRIDOR_RADIUS,
				Math.max(sourceCell.z(), listenerCell.z()) + AIR_CORRIDOR_RADIUS
		);
		SparseAirGrid.Builder grid = SparseAirGrid.builder(bounds);
		LongOpenHashSet sampled = new LongOpenHashSet();
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		VoxelDda.WalkResult walk = VoxelDda.walk(
				source,
				listener,
				(x, y, z, pathLengthMeters) -> {
					for (int dx = -AIR_CORRIDOR_RADIUS; dx <= AIR_CORRIDOR_RADIUS; dx++) {
						for (int dy = -AIR_CORRIDOR_RADIUS; dy <= AIR_CORRIDOR_RADIUS; dy++) {
							for (int dz = -AIR_CORRIDOR_RADIUS; dz <= AIR_CORRIDOR_RADIUS; dz++) {
								if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > AIR_CORRIDOR_RADIUS) {
									continue;
								}
								int sampleX = x + dx;
								int sampleY = y + dy;
								int sampleZ = z + dz;
								long packed = pack(sampleX, sampleY, sampleZ);
								if (!sampled.add(packed)) {
									continue;
								}
								if (!level.hasChunk(
										Math.floorDiv(sampleX, 16),
										Math.floorDiv(sampleZ, 16)
								)) {
									grid.markIncomplete();
									continue;
								}
								position.set(sampleX, sampleY, sampleZ);
								grid.sample(
										sampleX,
										sampleY,
										sampleZ,
										level.getBlockState(position)
												.getCollisionShape(level, position)
												.isEmpty()
								);
							}
						}
					}
					return true;
				},
				maxCells
		);
		if (!walk.reachedEnd()) {
			grid.markIncomplete();
		}
		grid.sample(sourceCell.x(), sourceCell.y(), sourceCell.z(), true);
		grid.sample(listenerCell.x(), listenerCell.y(), listenerCell.z(), true);
		return grid.build();
	}

	private static VoxelDda.Cell cellAt(AcousticVector position) {
		return new VoxelDda.Cell(
				(int) Math.floor(position.x()),
				(int) Math.floor(position.y()),
				(int) Math.floor(position.z())
		);
	}

	private static long pack(int x, int y, int z) {
		return ((long) x & 0x3ffffffL) << 38
				| ((long) z & 0x3ffffffL) << 12
				| (long) y & 0xfffL;
	}
}
