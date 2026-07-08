package com.tenicana.dronecraft.sim;

import java.util.List;

public record PropellerArchiveCtCpJActuatorDiskSourceField(
		List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms,
		double sourceThicknessMeters
) {
	private static final double EPSILON = 1.0e-9;

	public PropellerArchiveCtCpJActuatorDiskSourceField {
		sourceTerms = List.copyOf(sourceTerms == null ? List.of() : sourceTerms);
		if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= EPSILON) {
			throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
		}
	}

	public record SourceFieldSample(
			Vec3 samplePointWorldMeters,
			int contributingSourceCount,
			Vec3 bodyForceDensityWorldNewtonsPerCubicMeter,
			Vec3 wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter,
			double pressureJumpPascals,
			double massFluxKilogramsPerSecondSquareMeter,
			double idealMomentumPowerLoadingWattsPerSquareMeter,
			Vec3 farWakeAxialVelocityWorldMetersPerSecond,
			Vec3 wakeSwirlVelocityWorldMetersPerSecond,
			Vec3 targetWakeVelocityWorldMetersPerSecond
	) {
		public SourceFieldSample {
			samplePointWorldMeters = finiteVecOrZero(samplePointWorldMeters);
			contributingSourceCount = Math.max(0, contributingSourceCount);
			bodyForceDensityWorldNewtonsPerCubicMeter =
					finiteVecOrZero(bodyForceDensityWorldNewtonsPerCubicMeter);
			wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter =
					finiteVecOrZero(wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter);
			pressureJumpPascals = finiteNonnegative(pressureJumpPascals);
			massFluxKilogramsPerSecondSquareMeter = finiteNonnegative(massFluxKilogramsPerSecondSquareMeter);
			idealMomentumPowerLoadingWattsPerSquareMeter =
					finiteNonnegative(idealMomentumPowerLoadingWattsPerSquareMeter);
			farWakeAxialVelocityWorldMetersPerSecond =
					finiteVecOrZero(farWakeAxialVelocityWorldMetersPerSecond);
			wakeSwirlVelocityWorldMetersPerSecond =
					finiteVecOrZero(wakeSwirlVelocityWorldMetersPerSecond);
			targetWakeVelocityWorldMetersPerSecond =
					finiteVecOrZero(targetWakeVelocityWorldMetersPerSecond);
		}

		public boolean insideAnySource() {
			return contributingSourceCount > 0;
		}
	}

	public record VoxelGridSpec(
			Vec3 originWorldMeters,
			double cellSizeMeters,
			int cellCountX,
			int cellCountY,
			int cellCountZ
	) {
		public VoxelGridSpec {
			originWorldMeters = finiteVecOrZero(originWorldMeters);
			if (!Double.isFinite(cellSizeMeters) || cellSizeMeters <= EPSILON) {
				throw new IllegalArgumentException("cellSizeMeters must be finite and positive.");
			}
			if (cellCountX <= 0 || cellCountY <= 0 || cellCountZ <= 0) {
				throw new IllegalArgumentException("cell counts must be positive.");
			}
		}

		public int totalCellCount() {
			return cellCountX * cellCountY * cellCountZ;
		}

		public double cellVolumeCubicMeters() {
			return cellSizeMeters * cellSizeMeters * cellSizeMeters;
		}

		public Vec3 cellCenterWorldMeters(int xIndex, int yIndex, int zIndex) {
			checkIndex(xIndex, cellCountX, "xIndex");
			checkIndex(yIndex, cellCountY, "yIndex");
			checkIndex(zIndex, cellCountZ, "zIndex");
			return originWorldMeters.add(new Vec3(
					(xIndex + 0.5) * cellSizeMeters,
					(yIndex + 0.5) * cellSizeMeters,
					(zIndex + 0.5) * cellSizeMeters
			));
		}

		private static void checkIndex(int index, int count, String name) {
			if (index < 0 || index >= count) {
				throw new IndexOutOfBoundsException(name + " outside voxel grid.");
			}
		}
	}

	public record VoxelCellSample(
			int xIndex,
			int yIndex,
			int zIndex,
			Vec3 cellCenterWorldMeters,
			double cellVolumeCubicMeters,
			int totalSubsampleCount,
			int activeSubsampleCount,
			double sourceVolumeFraction,
			Vec3 bodyForceDensityWorldNewtonsPerCubicMeter,
			Vec3 wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter,
			double pressureJumpPascals,
			double massFluxKilogramsPerSecondSquareMeter,
			double idealMomentumPowerLoadingWattsPerSquareMeter,
			Vec3 farWakeAxialVelocityWorldMetersPerSecond,
			Vec3 wakeSwirlVelocityWorldMetersPerSecond,
			Vec3 targetWakeVelocityWorldMetersPerSecond
	) {
		public VoxelCellSample {
			xIndex = Math.max(0, xIndex);
			yIndex = Math.max(0, yIndex);
			zIndex = Math.max(0, zIndex);
			cellCenterWorldMeters = finiteVecOrZero(cellCenterWorldMeters);
			cellVolumeCubicMeters = finiteNonnegative(cellVolumeCubicMeters);
			totalSubsampleCount = Math.max(1, totalSubsampleCount);
			activeSubsampleCount = Math.max(0, Math.min(activeSubsampleCount, totalSubsampleCount));
			sourceVolumeFraction = MathUtil.clamp(sourceVolumeFraction, 0.0, 1.0);
			bodyForceDensityWorldNewtonsPerCubicMeter =
					finiteVecOrZero(bodyForceDensityWorldNewtonsPerCubicMeter);
			wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter =
					finiteVecOrZero(wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter);
			pressureJumpPascals = finiteNonnegative(pressureJumpPascals);
			massFluxKilogramsPerSecondSquareMeter = finiteNonnegative(massFluxKilogramsPerSecondSquareMeter);
			idealMomentumPowerLoadingWattsPerSquareMeter =
					finiteNonnegative(idealMomentumPowerLoadingWattsPerSquareMeter);
			farWakeAxialVelocityWorldMetersPerSecond =
					finiteVecOrZero(farWakeAxialVelocityWorldMetersPerSecond);
			wakeSwirlVelocityWorldMetersPerSecond =
					finiteVecOrZero(wakeSwirlVelocityWorldMetersPerSecond);
			targetWakeVelocityWorldMetersPerSecond =
					finiteVecOrZero(targetWakeVelocityWorldMetersPerSecond);
		}

		public boolean active() {
			return activeSubsampleCount > 0;
		}

		public Vec3 integratedBodyForceWorldNewtons() {
			return bodyForceDensityWorldNewtonsPerCubicMeter.multiply(cellVolumeCubicMeters);
		}

		public Vec3 integratedWakeAngularMomentumTorqueWorldNewtonMeters() {
			return wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter.multiply(cellVolumeCubicMeters);
		}

		public double sampledSourceVolumeCubicMeters() {
			return sourceVolumeFraction * cellVolumeCubicMeters;
		}
	}

	public record VoxelGridSample(
			VoxelGridSpec gridSpec,
			int subcellSamplesPerAxis,
			List<VoxelCellSample> cells
	) {
		public VoxelGridSample {
			if (gridSpec == null) {
				throw new IllegalArgumentException("gridSpec must not be null.");
			}
			subcellSamplesPerAxis = normalizeSubcellSamplesPerAxis(subcellSamplesPerAxis);
			cells = List.copyOf(cells == null ? List.of() : cells);
		}

		public List<VoxelCellSample> activeCells() {
			return cells.stream()
					.filter(VoxelCellSample::active)
					.toList();
		}

		public int activeCellCount() {
			int count = 0;
			for (VoxelCellSample cell : cells) {
				if (cell.active()) {
					count++;
				}
			}
			return count;
		}

		public int activeSubsampleCount() {
			int count = 0;
			for (VoxelCellSample cell : cells) {
				count += cell.activeSubsampleCount();
			}
			return count;
		}

		public double sampledSourceVolumeCubicMeters() {
			double volume = 0.0;
			for (VoxelCellSample cell : cells) {
				volume += cell.sampledSourceVolumeCubicMeters();
			}
			return volume;
		}

		public Vec3 integratedBodyForceWorldNewtons() {
			Vec3 sum = Vec3.ZERO;
			for (VoxelCellSample cell : cells) {
				sum = sum.add(cell.integratedBodyForceWorldNewtons());
			}
			return sum;
		}

		public Vec3 integratedWakeAngularMomentumTorqueWorldNewtonMeters() {
			Vec3 sum = Vec3.ZERO;
			for (VoxelCellSample cell : cells) {
				sum = sum.add(cell.integratedWakeAngularMomentumTorqueWorldNewtonMeters());
			}
			return sum;
		}
	}

	public SourceFieldSample sampleAt(Vec3 samplePointWorldMeters) {
		Vec3 point = finiteVecOrZero(samplePointWorldMeters);
		int contributingSources = 0;
		Vec3 bodyForceDensity = Vec3.ZERO;
		Vec3 wakeTorqueDensity = Vec3.ZERO;
		double pressureJump = 0.0;
		double massFlux = 0.0;
		double powerLoading = 0.0;
		Vec3 farWakeAxialVelocity = Vec3.ZERO;
		Vec3 wakeSwirlVelocity = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (!containsActuatorDiskVolume(sourceTerm, point)) {
				continue;
			}
			contributingSources++;
			bodyForceDensity = bodyForceDensity.add(
					sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(sourceThicknessMeters));
			wakeTorqueDensity = wakeTorqueDensity.add(
					sourceTerm.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(
							sourceThicknessMeters));
			pressureJump += sourceTerm.pressureJumpPascals();
			massFlux += sourceTerm.massFluxKilogramsPerSecondSquareMeter();
			powerLoading += sourceTerm.idealMomentumPowerLoadingWattsPerSquareMeter();
			farWakeAxialVelocity = farWakeAxialVelocity.add(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond());
			wakeSwirlVelocity = wakeSwirlVelocity.add(sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(point));
		}
		return new SourceFieldSample(
				point,
				contributingSources,
				bodyForceDensity,
				wakeTorqueDensity,
				pressureJump,
				massFlux,
				powerLoading,
				farWakeAxialVelocity,
				wakeSwirlVelocity,
				farWakeAxialVelocity.add(wakeSwirlVelocity)
		);
	}

	public VoxelGridSample sampleVoxelGrid(VoxelGridSpec gridSpec) {
		return sampleVoxelGrid(gridSpec, 1);
	}

	public VoxelGridSample sampleVoxelGrid(VoxelGridSpec gridSpec, int subcellSamplesPerAxis) {
		if (gridSpec == null) {
			throw new IllegalArgumentException("gridSpec must not be null.");
		}
		int normalizedSubcells = normalizeSubcellSamplesPerAxis(subcellSamplesPerAxis);
		java.util.ArrayList<VoxelCellSample> cells =
				new java.util.ArrayList<>(gridSpec.totalCellCount());
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					cells.add(sampleVoxelCell(gridSpec, x, y, z, normalizedSubcells));
				}
			}
		}
		return new VoxelGridSample(gridSpec, normalizedSubcells, cells);
	}

	private VoxelCellSample sampleVoxelCell(
			VoxelGridSpec gridSpec,
			int xIndex,
			int yIndex,
			int zIndex,
			int subcellSamplesPerAxis
	) {
		Vec3 cellCenter = gridSpec.cellCenterWorldMeters(xIndex, yIndex, zIndex);
		int totalSubsamples = subcellSamplesPerAxis * subcellSamplesPerAxis * subcellSamplesPerAxis;
		int activeSubsamples = 0;
		Vec3 bodyForceDensity = Vec3.ZERO;
		Vec3 wakeTorqueDensity = Vec3.ZERO;
		double pressureJump = 0.0;
		double massFlux = 0.0;
		double powerLoading = 0.0;
		Vec3 farWakeAxialVelocity = Vec3.ZERO;
		Vec3 wakeSwirlVelocity = Vec3.ZERO;
		Vec3 targetWakeVelocity = Vec3.ZERO;
		for (int sy = 0; sy < subcellSamplesPerAxis; sy++) {
			for (int sz = 0; sz < subcellSamplesPerAxis; sz++) {
				for (int sx = 0; sx < subcellSamplesPerAxis; sx++) {
					Vec3 subcellPoint = subcellPointWorldMeters(
							cellCenter,
							gridSpec.cellSizeMeters(),
							subcellSamplesPerAxis,
							sx,
							sy,
							sz
					);
					SourceFieldSample sample = sampleAt(subcellPoint);
					if (sample.insideAnySource()) {
						activeSubsamples++;
					}
					bodyForceDensity = bodyForceDensity.add(sample.bodyForceDensityWorldNewtonsPerCubicMeter());
					wakeTorqueDensity =
							wakeTorqueDensity.add(sample.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter());
					pressureJump += sample.pressureJumpPascals();
					massFlux += sample.massFluxKilogramsPerSecondSquareMeter();
					powerLoading += sample.idealMomentumPowerLoadingWattsPerSquareMeter();
					farWakeAxialVelocity =
							farWakeAxialVelocity.add(sample.farWakeAxialVelocityWorldMetersPerSecond());
					wakeSwirlVelocity = wakeSwirlVelocity.add(sample.wakeSwirlVelocityWorldMetersPerSecond());
					targetWakeVelocity = targetWakeVelocity.add(sample.targetWakeVelocityWorldMetersPerSecond());
				}
			}
		}
		double inverseSamples = 1.0 / totalSubsamples;
		return new VoxelCellSample(
				xIndex,
				yIndex,
				zIndex,
				cellCenter,
				gridSpec.cellVolumeCubicMeters(),
				totalSubsamples,
				activeSubsamples,
				activeSubsamples * inverseSamples,
				bodyForceDensity.multiply(inverseSamples),
				wakeTorqueDensity.multiply(inverseSamples),
				pressureJump * inverseSamples,
				massFlux * inverseSamples,
				powerLoading * inverseSamples,
				farWakeAxialVelocity.multiply(inverseSamples),
				wakeSwirlVelocity.multiply(inverseSamples),
				targetWakeVelocity.multiply(inverseSamples)
		);
	}

	public boolean containsActuatorDiskVolume(
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm,
			Vec3 samplePointWorldMeters
	) {
		if (sourceTerm == null || !sourceTerm.applied() || sourceTerm.diskAreaSquareMeters() <= EPSILON) {
			return false;
		}
		Vec3 normal = finiteVecOrZero(sourceTerm.diskNormalWorld()).normalized();
		if (normal.lengthSquared() <= EPSILON) {
			return false;
		}
		Vec3 offset = finiteVecOrZero(samplePointWorldMeters).subtract(sourceTerm.diskCenterWorldMeters());
		double axialDistance = offset.dot(normal);
		if (Math.abs(axialDistance) > sourceThicknessMeters * 0.5 + EPSILON) {
			return false;
		}
		Vec3 radial = offset.subtract(normal.multiply(axialDistance));
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		return radial.lengthSquared() <= diskRadius * diskRadius + EPSILON;
	}

	public Vec3 integratedBodyForceWorldNewtons() {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (sourceTerm != null && sourceTerm.applied()) {
				sum = sum.add(sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(sourceThicknessMeters)
						.multiply(sourceTerm.sourceVolumeCubicMeters(sourceThicknessMeters)));
			}
		}
		return sum;
	}

	public Vec3 integratedWakeAngularMomentumTorqueWorldNewtonMeters() {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (sourceTerm != null && sourceTerm.applied()) {
				sum = sum.add(sourceTerm
						.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(
								sourceThicknessMeters)
						.multiply(sourceTerm.sourceVolumeCubicMeters(sourceThicknessMeters)));
			}
		}
		return sum;
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	private static double finiteNonnegative(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 0.0;
	}

	private static int normalizeSubcellSamplesPerAxis(int subcellSamplesPerAxis) {
		return Math.max(1, Math.min(16, subcellSamplesPerAxis));
	}

	private static Vec3 subcellPointWorldMeters(
			Vec3 cellCenterWorldMeters,
			double cellSizeMeters,
			int subcellSamplesPerAxis,
			int sx,
			int sy,
			int sz
	) {
		double scale = cellSizeMeters / subcellSamplesPerAxis;
		return cellCenterWorldMeters.add(new Vec3(
				(sx + 0.5) * scale - cellSizeMeters * 0.5,
				(sy + 0.5) * scale - cellSizeMeters * 0.5,
				(sz + 0.5) * scale - cellSizeMeters * 0.5
		));
	}
}
