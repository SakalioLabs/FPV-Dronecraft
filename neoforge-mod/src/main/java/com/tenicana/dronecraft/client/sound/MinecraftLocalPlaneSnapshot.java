package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.propagation.AxisAlignedPlanePatch;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer.FrozenBlockView;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer.Result;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Snapshot-time adapter from optimized Minecraft collision shapes to exposed
 * continuous reflection patches. A one-block halo prevents the capture
 * boundary from being mistaken for air.
 */
record MinecraftLocalPlaneSnapshot(
		long generation,
		boolean complete,
		int sampledBlockStates,
		int unloadedBlockStates,
		int emptyBlockStates,
		int materialBoxes,
		boolean generationStable,
		boolean unionGridBudgetExceeded,
		CellCaptureBounds coverage,
		List<MaterialBox> occlusionBoxes,
		List<AxisAlignedPlanePatch> patches
) {
	MinecraftLocalPlaneSnapshot {
		occlusionBoxes = List.copyOf(occlusionBoxes);
		patches = List.copyOf(patches);
	}

	static MinecraftLocalPlaneSnapshot capture(
			ClientLevel level,
			BlockPos center,
			int horizontalRadius,
			int verticalRadius,
			long generation
	) {
		if (horizontalRadius < 1 || verticalRadius < 1) {
			throw new IllegalArgumentException(
					"local-plane snapshot radii must be positive"
			);
		}
		return capture(
				level,
				new CellCaptureBounds(
						center.getX() - horizontalRadius - 1,
						center.getY() - verticalRadius - 1,
						center.getZ() - horizontalRadius - 1,
						center.getX() + horizontalRadius + 1,
						center.getY() + verticalRadius + 1,
						center.getZ() + horizontalRadius + 1
				),
				1,
				generation
		);
	}

	static MinecraftLocalPlaneSnapshot capture(
			ClientLevel level,
			CellCaptureBounds sampleBounds,
			int halo,
			long generation
	) {
		Result result = LocalPlaneSnapshotProducer.capture(
				frozenView(level, generation),
				sampleBounds,
				halo
		);
		return fromResult(generation, sampleBounds, result);
	}

	static FrozenBlockView frozenView(
			ClientLevel level,
			long generation
	) {
		return new FrozenBlockView() {
			private final BlockPos.MutableBlockPos position =
					new BlockPos.MutableBlockPos();

			@Override
			public long generation() {
				return generation;
			}

			@Override
			public boolean isLoaded(int x, int y, int z) {
				return level.hasChunk(
						Math.floorDiv(x, 16),
						Math.floorDiv(z, 16)
				);
			}

			@Override
			public void appendMaterialBoxes(
					int x,
					int y,
					int z,
					List<MaterialBox> output
			) {
				position.set(x, y, z);
				BlockState state = level.getBlockState(position);
				VoxelShape shape = state.getCollisionShape(
						level, position
				);
				if (shape.isEmpty()) {
					return;
				}
				DirectPathSolver.MaterialSample sample =
						MinecraftAcousticMaterials.sample(
								level, position, state
						);
				if (!sample.material().isAir()) {
					appendShapeBoxes(
							output,
							position,
							shape,
							sample.material()
					);
				}
			}
		};
	}

	private static MinecraftLocalPlaneSnapshot fromResult(
			long generation,
			CellCaptureBounds sampleBounds,
			Result result
	) {
		return new MinecraftLocalPlaneSnapshot(
				generation,
				result.complete(),
				result.sampledCells(),
				result.unloadedCells(),
				result.emptyCells(),
				result.materialBoxCount(),
				result.generationStable(),
				result.unionGridBudgetExceeded(),
				sampleBounds,
				result.materialBoxes(),
				result.patches()
		);
	}

	static void appendShapeBoxes(
			List<MaterialBox> output,
			BlockPos position,
			VoxelShape shape,
			AcousticMaterial material
	) {
		for (AABB box : shape.optimize().toAabbs()) {
			output.add(new MaterialBox(
					position.getX() + box.minX,
					position.getY() + box.minY,
					position.getZ() + box.minZ,
					position.getX() + box.maxX,
					position.getY() + box.maxY,
					position.getZ() + box.maxZ,
					material
			));
		}
	}

}
