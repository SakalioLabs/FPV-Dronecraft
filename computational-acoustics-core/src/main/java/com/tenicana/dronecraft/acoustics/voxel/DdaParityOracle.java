package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Allocation-heavy diagnostic trace for validating future accelerated DDA
 * backends. Product propagation should continue to use {@link VoxelDda}
 * directly.
 */
public final class DdaParityOracle {
	public static final int AIR_MATERIAL_ID = 0;

	private DdaParityOracle() {
	}

	public static RayTrace trace(
			AcousticVector start,
			AcousticVector end,
			MaterialIdQuery materials,
			int maxCells
	) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(materials, "materials");
		List<Segment> segments = new ArrayList<>();
		VoxelDda.Cell[] firstMaterialCell = {null};
		VoxelDda.WalkResult walk = VoxelDda.walk(
				start,
				end,
				(x, y, z, pathLengthMeters) -> {
					int materialId = materials.materialIdAt(x, y, z);
					if (materialId < AIR_MATERIAL_ID) {
						throw new IllegalArgumentException(
								"material id must be non-negative"
						);
					}
					VoxelDda.Cell cell = new VoxelDda.Cell(x, y, z);
					segments.add(new Segment(
							cell,
							pathLengthMeters,
							materialId
					));
					if (
							firstMaterialCell[0] == null
									&& materialId != AIR_MATERIAL_ID
					) {
						firstMaterialCell[0] = cell;
					}
					return true;
				},
				maxCells
		);
		return new RayTrace(
				start,
				end,
				maxCells,
				segments,
				Optional.ofNullable(firstMaterialCell[0]),
				walk.visitedCellCount(),
				walk.reachedEnd(),
				walk.stoppedEarly(),
				!walk.reachedEnd() && !walk.stoppedEarly()
		);
	}

	@FunctionalInterface
	public interface MaterialIdQuery {
		int materialIdAt(int x, int y, int z);
	}

	public record Segment(
			VoxelDda.Cell cell,
			double lengthMeters,
			int materialId
	) {
		public Segment {
			Objects.requireNonNull(cell, "cell");
			if (!Double.isFinite(lengthMeters) || lengthMeters < 0.0) {
				throw new IllegalArgumentException(
						"lengthMeters must be finite and non-negative"
				);
			}
			if (materialId < AIR_MATERIAL_ID) {
				throw new IllegalArgumentException(
						"materialId must be non-negative"
				);
			}
		}
	}

	public record RayTrace(
			AcousticVector start,
			AcousticVector end,
			int maximumCells,
			List<Segment> segments,
			Optional<VoxelDda.Cell> firstMaterialCell,
			int visitedCellCount,
			boolean reachedEnd,
			boolean stoppedEarly,
			boolean truncated
	) {
		public RayTrace {
			Objects.requireNonNull(start, "start");
			Objects.requireNonNull(end, "end");
			segments = List.copyOf(segments);
			Objects.requireNonNull(firstMaterialCell, "firstMaterialCell");
			if (maximumCells < 1) {
				throw new IllegalArgumentException(
						"maximumCells must be positive"
				);
			}
			if (visitedCellCount != segments.size()) {
				throw new IllegalArgumentException(
						"visitedCellCount must equal segment count"
				);
			}
			if (truncated == (reachedEnd || stoppedEarly)) {
				throw new IllegalArgumentException(
						"truncated must be the inverse of terminal flags"
				);
			}
		}
	}
}
