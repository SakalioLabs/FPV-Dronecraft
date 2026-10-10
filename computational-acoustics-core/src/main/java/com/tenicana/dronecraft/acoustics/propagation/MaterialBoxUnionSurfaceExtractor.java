package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Snapshot-time reconstruction of the exposed boundary of an axis-aligned
 * material-box union. Internal box/block interfaces are never emitted.
 */
public final class MaterialBoxUnionSurfaceExtractor {
	public static final int MAXIMUM_GRID_CELLS = 2_000_000;

	private MaterialBoxUnionSurfaceExtractor() {
	}

	public static List<AxisAlignedPlanePatch> extract(
			List<MaterialBox> boxes
	) {
		return extract(boxes, ignored -> true);
	}

	public static List<AxisAlignedPlanePatch> extract(
			List<MaterialBox> boxes,
			Predicate<AxisAlignedPlanePatch> includeBeforeCoalescing
	) {
		Objects.requireNonNull(boxes, "boxes");
		Objects.requireNonNull(
				includeBeforeCoalescing,
				"includeBeforeCoalescing"
		);
		if (boxes.isEmpty()) {
			return List.of();
		}
		double[] x = coordinates(boxes, 0);
		double[] y = coordinates(boxes, 1);
		double[] z = coordinates(boxes, 2);
		int cellsX = x.length - 1;
		int cellsY = y.length - 1;
		int cellsZ = z.length - 1;
		long cellCount = (long) cellsX * cellsY * cellsZ;
		if (cellCount > MAXIMUM_GRID_CELLS) {
			throw new GridBudgetExceededException(
					cellCount,
					MAXIMUM_GRID_CELLS
			);
		}
		AcousticMaterial[] occupancy =
				new AcousticMaterial[(int) cellCount];
		for (MaterialBox box : boxes) {
			int minimumX = coordinateIndex(x, box.minimumX());
			int maximumX = coordinateIndex(x, box.maximumX());
			int minimumY = coordinateIndex(y, box.minimumY());
			int maximumY = coordinateIndex(y, box.maximumY());
			int minimumZ = coordinateIndex(z, box.minimumZ());
			int maximumZ = coordinateIndex(z, box.maximumZ());
			for (int ix = minimumX; ix < maximumX; ix++) {
				for (int iy = minimumY; iy < maximumY; iy++) {
					for (int iz = minimumZ; iz < maximumZ; iz++) {
						int index = index(
								ix, iy, iz, cellsY, cellsZ
						);
						AcousticMaterial existing = occupancy[index];
						if (existing != null
								&& !existing.equals(box.material())) {
							throw new IllegalArgumentException(
									"overlapping material boxes disagree"
							);
						}
						occupancy[index] = box.material();
					}
				}
			}
		}
		List<AxisAlignedPlanePatch> patches = new ArrayList<>();
		for (int ix = 0; ix < cellsX; ix++) {
			for (int iy = 0; iy < cellsY; iy++) {
				for (int iz = 0; iz < cellsZ; iz++) {
					AcousticMaterial material = occupancy[
							index(ix, iy, iz, cellsY, cellsZ)
					];
					if (material == null) {
						continue;
					}
					if (ix == 0 || occupancy[
							index(ix - 1, iy, iz, cellsY, cellsZ)
					] == null) {
						patches.add(patch(
								0, -1, x[ix],
								y[iy], y[iy + 1],
								z[iz], z[iz + 1],
								material
						));
					}
					if (ix == cellsX - 1 || occupancy[
							index(ix + 1, iy, iz, cellsY, cellsZ)
					] == null) {
						patches.add(patch(
								0, 1, x[ix + 1],
								y[iy], y[iy + 1],
								z[iz], z[iz + 1],
								material
						));
					}
					if (iy == 0 || occupancy[
							index(ix, iy - 1, iz, cellsY, cellsZ)
					] == null) {
						patches.add(patch(
								1, -1, y[iy],
								x[ix], x[ix + 1],
								z[iz], z[iz + 1],
								material
						));
					}
					if (iy == cellsY - 1 || occupancy[
							index(ix, iy + 1, iz, cellsY, cellsZ)
					] == null) {
						patches.add(patch(
								1, 1, y[iy + 1],
								x[ix], x[ix + 1],
								z[iz], z[iz + 1],
								material
						));
					}
					if (iz == 0 || occupancy[
							index(ix, iy, iz - 1, cellsY, cellsZ)
					] == null) {
						patches.add(patch(
								2, -1, z[iz],
								x[ix], x[ix + 1],
								y[iy], y[iy + 1],
								material
						));
					}
					if (iz == cellsZ - 1 || occupancy[
							index(ix, iy, iz + 1, cellsY, cellsZ)
					] == null) {
						patches.add(patch(
								2, 1, z[iz + 1],
								x[ix], x[ix + 1],
								y[iy], y[iy + 1],
								material
						));
					}
				}
			}
		}
		patches.removeIf(
				patch -> !includeBeforeCoalescing.test(patch)
		);
		return List.copyOf(coalesce(patches));
	}

	private static List<AxisAlignedPlanePatch> coalesce(
			List<AxisAlignedPlanePatch> patches
	) {
		Map<PlaneKey, List<AxisAlignedPlanePatch>> planes =
				new LinkedHashMap<>();
		for (AxisAlignedPlanePatch patch : patches) {
			planes.computeIfAbsent(
					new PlaneKey(
							patch.axis(),
							patch.normalSign(),
							patch.coordinateMeters(),
							patch.material()
					),
					ignored -> new ArrayList<>()
			).add(patch);
		}
		List<AxisAlignedPlanePatch> result = new ArrayList<>();
		for (List<AxisAlignedPlanePatch> plane : planes.values()) {
			List<AxisAlignedPlanePatch> merged = plane;
			while (true) {
				int before = merged.size();
				merged = mergeAdjacent(merged, true);
				merged = mergeAdjacent(merged, false);
				if (merged.size() == before) {
					break;
				}
			}
			result.addAll(merged);
		}
		return result;
	}

	private static List<AxisAlignedPlanePatch> mergeAdjacent(
			List<AxisAlignedPlanePatch> input,
			boolean alongFirst
	) {
		if (input.size() < 2) {
			return input;
		}
		List<AxisAlignedPlanePatch> sorted = new ArrayList<>(input);
		Comparator<AxisAlignedPlanePatch> comparator = alongFirst
				? Comparator.comparingDouble(
						AxisAlignedPlanePatch::minimumSecondMeters
				).thenComparingDouble(
						AxisAlignedPlanePatch::maximumSecondMeters
				).thenComparingDouble(
						AxisAlignedPlanePatch::minimumFirstMeters
				).thenComparingDouble(
						AxisAlignedPlanePatch::maximumFirstMeters
				)
				: Comparator.comparingDouble(
						AxisAlignedPlanePatch::minimumFirstMeters
				).thenComparingDouble(
						AxisAlignedPlanePatch::maximumFirstMeters
				).thenComparingDouble(
						AxisAlignedPlanePatch::minimumSecondMeters
				).thenComparingDouble(
						AxisAlignedPlanePatch::maximumSecondMeters
				);
		sorted.sort(comparator);
		List<AxisAlignedPlanePatch> output =
				new ArrayList<>(sorted.size());
		AxisAlignedPlanePatch current = sorted.getFirst();
		for (int index = 1; index < sorted.size(); index++) {
			AxisAlignedPlanePatch next = sorted.get(index);
			if (adjacent(current, next, alongFirst)) {
				current = new AxisAlignedPlanePatch(
						current.axis(),
						current.normalSign(),
						current.coordinateMeters(),
						current.minimumFirstMeters(),
						alongFirst
								? next.maximumFirstMeters()
								: current.maximumFirstMeters(),
						current.minimumSecondMeters(),
						alongFirst
								? current.maximumSecondMeters()
								: next.maximumSecondMeters(),
						current.material()
				);
			} else {
				output.add(current);
				current = next;
			}
		}
		output.add(current);
		return output;
	}

	private static boolean adjacent(
			AxisAlignedPlanePatch left,
			AxisAlignedPlanePatch right,
			boolean alongFirst
	) {
		if (alongFirst) {
			return Double.compare(
					left.minimumSecondMeters(),
					right.minimumSecondMeters()
			) == 0 && Double.compare(
					left.maximumSecondMeters(),
					right.maximumSecondMeters()
			) == 0 && Double.compare(
					left.maximumFirstMeters(),
					right.minimumFirstMeters()
			) == 0;
		}
		return Double.compare(
				left.minimumFirstMeters(),
				right.minimumFirstMeters()
		) == 0 && Double.compare(
				left.maximumFirstMeters(),
				right.maximumFirstMeters()
		) == 0 && Double.compare(
				left.maximumSecondMeters(),
				right.minimumSecondMeters()
		) == 0;
	}

	private record PlaneKey(
			int axis,
			int sign,
			double coordinate,
			AcousticMaterial material
	) {
	}

	private static AxisAlignedPlanePatch patch(
			int axis,
			int sign,
			double coordinate,
			double minimumFirst,
			double maximumFirst,
			double minimumSecond,
			double maximumSecond,
			AcousticMaterial material
	) {
		return new AxisAlignedPlanePatch(
				axis,
				sign,
				coordinate,
				minimumFirst,
				maximumFirst,
				minimumSecond,
				maximumSecond,
				material
		);
	}

	private static double[] coordinates(
			List<MaterialBox> boxes,
			int axis
	) {
		double[] values = new double[boxes.size() * 2];
		for (int index = 0; index < boxes.size(); index++) {
			MaterialBox box = boxes.get(index);
			values[index * 2] = box.minimum(axis);
			values[index * 2 + 1] = box.maximum(axis);
		}
		Arrays.sort(values);
		int unique = 1;
		for (int index = 1; index < values.length; index++) {
			if (Double.compare(values[index], values[unique - 1]) != 0) {
				values[unique++] = values[index];
			}
		}
		return Arrays.copyOf(values, unique);
	}

	private static int index(
			int x,
			int y,
			int z,
			int cellsY,
			int cellsZ
	) {
		return (x * cellsY + y) * cellsZ + z;
	}

	private static int coordinateIndex(double[] coordinates, double value) {
		int index = Arrays.binarySearch(coordinates, value);
		if (index < 0) {
			throw new IllegalStateException(
					"box coordinate disappeared from canonical grid"
			);
		}
		return index;
	}

	public record MaterialBox(
			double minimumX,
			double minimumY,
			double minimumZ,
			double maximumX,
			double maximumY,
			double maximumZ,
			AcousticMaterial material
	) {
		public MaterialBox {
			requireFinite(minimumX, "minimumX");
			requireFinite(minimumY, "minimumY");
			requireFinite(minimumZ, "minimumZ");
			requireFinite(maximumX, "maximumX");
			requireFinite(maximumY, "maximumY");
			requireFinite(maximumZ, "maximumZ");
			if (minimumX >= maximumX
					|| minimumY >= maximumY
					|| minimumZ >= maximumZ) {
				throw new IllegalArgumentException(
						"material box must have positive volume"
				);
			}
			Objects.requireNonNull(material, "material");
		}

		private double minimum(int axis) {
			return axis == 0 ? minimumX : axis == 1 ? minimumY : minimumZ;
		}

		private double maximum(int axis) {
			return axis == 0 ? maximumX : axis == 1 ? maximumY : maximumZ;
		}

		private static void requireFinite(double value, String label) {
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException(label + " must be finite");
			}
		}
	}

	public static final class GridBudgetExceededException
			extends IllegalArgumentException {
		private final long gridCells;
		private final int maximumGridCells;

		private GridBudgetExceededException(
				long gridCells,
				int maximumGridCells
		) {
			super(
					"box union coordinate grid " + gridCells
							+ " exceeds snapshot budget " + maximumGridCells
			);
			this.gridCells = gridCells;
			this.maximumGridCells = maximumGridCells;
		}

		public long gridCells() {
			return gridCells;
		}

		public int maximumGridCells() {
			return maximumGridCells;
		}
	}
}
