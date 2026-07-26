package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.Objects;

/**
 * Accumulates three-band transmission through a voxelized direct path.
 */
public final class DirectPathSolver {
	private DirectPathSolver() {
	}

	public static Result solve(
			AcousticVector source,
			AcousticVector listener,
			MaterialQuery materials,
			int maxCells
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(materials, "materials");
		Accumulator accumulator = new Accumulator();
		VoxelDda.WalkResult walk = VoxelDda.walk(
				source,
				listener,
				(x, y, z, pathLengthMeters) -> {
					MaterialSample sample = Objects.requireNonNull(
							materials.sampleAt(x, y, z),
							"sampleAt returned null"
					);
					accumulator.add(sample, pathLengthMeters);
					return true;
				},
				maxCells
		);
		AcousticBands loss = accumulator.transmissionLossDb;
		AcousticBands gain = loss.map(value -> Math.pow(10.0, -value / 10.0));
		return new Result(
				source.subtract(listener).length(),
				gain,
				loss,
				walk.visitedCellCount(),
				accumulator.materialCellCount,
				walk.reachedEnd()
		);
	}

	@FunctionalInterface
	public interface MaterialQuery {
		MaterialSample sampleAt(int x, int y, int z);
	}

	public record MaterialSample(AcousticMaterial material, double fillFraction) {
		public static final MaterialSample AIR = new MaterialSample(AcousticMaterial.AIR, 0.0);

		public MaterialSample {
			Objects.requireNonNull(material, "material");
			if (!Double.isFinite(fillFraction) || fillFraction < 0.0 || fillFraction > 1.0) {
				throw new IllegalArgumentException("fillFraction must be in [0, 1]");
			}
		}

		public static MaterialSample full(AcousticMaterial material) {
			return new MaterialSample(material, 1.0);
		}
	}

	public record Result(
			double distanceMeters,
			AcousticBands transmissionEnergyGain,
			AcousticBands transmissionLossDb,
			int visitedCellCount,
			int materialCellCount,
			boolean complete
	) {
	}

	private static final class Accumulator {
		private AcousticBands transmissionLossDb = AcousticBands.SILENT;
		private int materialCellCount;

		private void add(MaterialSample sample, double pathLengthMeters) {
			AcousticMaterial material = sample.material();
			double effectiveLength = pathLengthMeters * sample.fillFraction();
			transmissionLossDb = transmissionLossDb.add(
					material.transmissionLossDbPerMeter().multiply(effectiveLength)
			);
			if (!material.isAir() && effectiveLength > 0.0) {
				materialCellCount++;
			}
		}
	}
}
