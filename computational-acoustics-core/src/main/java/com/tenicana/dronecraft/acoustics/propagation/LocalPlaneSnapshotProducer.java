package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.GridBudgetExceededException;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Synchronous construction of one immutable local-plane snapshot from a
 * minimal frozen block view. It has no Minecraft or audio dependencies.
 */
public final class LocalPlaneSnapshotProducer {
	private static final double CELL_CONTAINMENT_EPSILON = 1.0e-9;

	private LocalPlaneSnapshotProducer() {
	}

	public static Result capture(
			FrozenBlockView view,
			CellCaptureBounds coverage,
			int halo
	) {
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(coverage, "coverage");
		if (halo < 0
				|| coverage.sizeX() <= halo * 2
				|| coverage.sizeY() <= halo * 2
				|| coverage.sizeZ() <= halo * 2) {
			throw new IllegalArgumentException(
					"capture halo must leave a positive inner volume"
			);
		}
		long generationBefore = view.generation();
		int sampledCells = 0;
		int unloadedCells = 0;
		int emptyCells = 0;
		List<MaterialBox> boxes = new ArrayList<>();
		for (int x = coverage.minimumX();
				x <= coverage.maximumX(); x++) {
			for (int z = coverage.minimumZ();
					z <= coverage.maximumZ(); z++) {
				for (int y = coverage.minimumY();
						y <= coverage.maximumY(); y++) {
					if (!view.isLoaded(x, y, z)) {
						unloadedCells++;
						continue;
					}
					sampledCells++;
					int before = boxes.size();
					view.appendMaterialBoxes(x, y, z, boxes);
					validateAppendedBoxes(boxes, before, x, y, z);
					if (boxes.size() == before) {
						emptyCells++;
					}
				}
			}
		}
		long generationAfter = view.generation();
		boolean generationStable =
				generationBefore == generationAfter;
		if (unloadedCells > 0 || !generationStable) {
			return new Result(
					generationBefore,
					generationAfter,
					generationStable,
					false,
					false,
					coverage,
					halo,
					sampledCells,
					unloadedCells,
					emptyCells,
					boxes.size(),
					0,
					List.of(),
					List.of()
			);
		}
		int innerMinimumX = coverage.minimumX() + halo;
		int innerMinimumY = coverage.minimumY() + halo;
		int innerMinimumZ = coverage.minimumZ() + halo;
		int innerMaximumX = coverage.maximumX() - halo;
		int innerMaximumY = coverage.maximumY() - halo;
		int innerMaximumZ = coverage.maximumZ() - halo;
		List<AxisAlignedPlanePatch> inner;
		try {
			inner = MaterialBoxUnionSurfaceExtractor.extract(
					boxes,
					patch -> ownerInside(
							patch,
							innerMinimumX,
							innerMinimumY,
							innerMinimumZ,
							innerMaximumX,
							innerMaximumY,
							innerMaximumZ
					)
			);
		} catch (GridBudgetExceededException ignored) {
			return new Result(
					generationBefore,
					generationAfter,
					true,
					false,
					true,
					coverage,
					halo,
					sampledCells,
					0,
					emptyCells,
					boxes.size(),
					0,
					List.of(),
					List.of()
			);
		}
		return new Result(
				generationBefore,
				generationAfter,
				true,
				true,
				false,
				coverage,
				halo,
				sampledCells,
				0,
				emptyCells,
				boxes.size(),
				inner.size(),
				List.copyOf(boxes),
				inner
		);
	}

	private static void validateAppendedBoxes(
			List<MaterialBox> boxes,
			int first,
			int cellX,
			int cellY,
			int cellZ
	) {
		if (boxes.size() < first) {
			throw new IllegalArgumentException(
					"block view removed previously appended material boxes"
			);
		}
		for (int index = first; index < boxes.size(); index++) {
			MaterialBox box = Objects.requireNonNull(
					boxes.get(index),
					"block view appended null material box"
			);
			if (box.minimumX() < cellX - CELL_CONTAINMENT_EPSILON
					|| box.minimumY()
							< cellY - CELL_CONTAINMENT_EPSILON
					|| box.minimumZ()
							< cellZ - CELL_CONTAINMENT_EPSILON
					|| box.maximumX()
							> cellX + 1.0 + CELL_CONTAINMENT_EPSILON
					|| box.maximumY()
							> cellY + 1.0 + CELL_CONTAINMENT_EPSILON
					|| box.maximumZ()
							> cellZ + 1.0 + CELL_CONTAINMENT_EPSILON) {
				throw new IllegalArgumentException(
						"block view material box escapes sampled cell"
				);
			}
		}
	}

	private static boolean ownerInside(
			AxisAlignedPlanePatch patch,
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ
	) {
		double first = (
				patch.minimumFirstMeters() + patch.maximumFirstMeters()
		) * 0.5;
		double second = (
				patch.minimumSecondMeters() + patch.maximumSecondMeters()
		) * 0.5;
		double ownerOffset =
				-patch.normalSign()
						* LocalPlaneReflectionSolver.AIR_SIDE_OFFSET_METERS;
		double x;
		double y;
		double z;
		if (patch.axis() == 0) {
			x = patch.coordinateMeters() + ownerOffset;
			y = first;
			z = second;
		} else if (patch.axis() == 1) {
			x = first;
			y = patch.coordinateMeters() + ownerOffset;
			z = second;
		} else {
			x = first;
			y = second;
			z = patch.coordinateMeters() + ownerOffset;
		}
		int ownerX = (int) Math.floor(x);
		int ownerY = (int) Math.floor(y);
		int ownerZ = (int) Math.floor(z);
		return ownerX >= minimumX && ownerX <= maximumX
				&& ownerY >= minimumY && ownerY <= maximumY
				&& ownerZ >= minimumZ && ownerZ <= maximumZ;
	}

	public interface FrozenBlockView {
		long generation();

		boolean isLoaded(int x, int y, int z);

		void appendMaterialBoxes(
				int x,
				int y,
				int z,
				List<MaterialBox> output
		);
	}

	public record Result(
			long generationBefore,
			long generationAfter,
			boolean generationStable,
			boolean complete,
			boolean unionGridBudgetExceeded,
			CellCaptureBounds coverage,
			int halo,
			int sampledCells,
			int unloadedCells,
			int emptyCells,
			int materialBoxCount,
			int patchCount,
			List<MaterialBox> materialBoxes,
			List<AxisAlignedPlanePatch> patches
	) {
		public Result {
			Objects.requireNonNull(coverage, "coverage");
			materialBoxes = List.copyOf(
					Objects.requireNonNull(
							materialBoxes,
							"materialBoxes"
					)
			);
			patches = List.copyOf(
					Objects.requireNonNull(patches, "patches")
			);
			if (!complete
					&& (!materialBoxes.isEmpty() || !patches.isEmpty())) {
				throw new IllegalArgumentException(
						"incomplete snapshot must not expose geometry"
				);
			}
		}

		public Result discardGeometry() {
			return new Result(
					generationBefore,
					generationAfter,
					generationStable,
					false,
					unionGridBudgetExceeded,
					coverage,
					halo,
					sampledCells,
					unloadedCells,
					emptyCells,
					materialBoxCount,
					0,
					List.of(),
					List.of()
			);
		}
	}
}
