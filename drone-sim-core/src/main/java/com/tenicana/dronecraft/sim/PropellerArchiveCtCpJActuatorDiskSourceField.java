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

		public Vec3 gridCenterWorldMeters() {
			return originWorldMeters.add(new Vec3(
					cellCountX * cellSizeMeters * 0.5,
					cellCountY * cellSizeMeters * 0.5,
					cellCountZ * cellSizeMeters * 0.5
			));
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

	public VoxelGridSpec enclosingVoxelGrid(double cellSizeMeters) {
		return enclosingVoxelGrid(cellSizeMeters, 0);
	}

	public VoxelGridSpec enclosingVoxelGrid(double cellSizeMeters, int paddingCells) {
		return enclosingVoxelGrid(cellSizeMeters, paddingCells, 0.0);
	}

	public VoxelGridSpec enclosingWakeVoxelGrid(
			double cellSizeMeters,
			int paddingCells,
			double downstreamWakeLengthMeters
	) {
		return enclosingVoxelGrid(cellSizeMeters, paddingCells, downstreamWakeLengthMeters);
	}

	private VoxelGridSpec enclosingVoxelGrid(
			double cellSizeMeters,
			int paddingCells,
			double downstreamWakeLengthMeters
	) {
		if (!Double.isFinite(cellSizeMeters) || cellSizeMeters <= EPSILON) {
			throw new IllegalArgumentException("cellSizeMeters must be finite and positive.");
		}
		int padding = Math.max(0, paddingCells);
		double wakeLength = Double.isFinite(downstreamWakeLengthMeters)
				? Math.max(0.0, downstreamWakeLengthMeters)
				: 0.0;
		boolean found = false;
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (sourceTerm == null || !sourceTerm.applied() || sourceTerm.diskAreaSquareMeters() <= EPSILON) {
				continue;
			}
			Vec3 normal = finiteVecOrZero(sourceTerm.diskNormalWorld()).normalized();
			if (normal.lengthSquared() <= EPSILON) {
				continue;
			}
			Vec3 center = sourceTerm.diskCenterWorldMeters();
			double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
			double halfThickness = sourceThicknessMeters * 0.5;
			double extentX = diskAxisAlignedExtent(diskRadius, halfThickness, normal.x());
			double extentY = diskAxisAlignedExtent(diskRadius, halfThickness, normal.y());
			double extentZ = diskAxisAlignedExtent(diskRadius, halfThickness, normal.z());
			Vec3 wakeDirection = downstreamWakeDirection(sourceTerm, normal);
			Vec3 downstreamCenter = center.add(wakeDirection.multiply(wakeLength));
			minX = Math.min(minX, Math.min(center.x(), downstreamCenter.x()) - extentX);
			minY = Math.min(minY, Math.min(center.y(), downstreamCenter.y()) - extentY);
			minZ = Math.min(minZ, Math.min(center.z(), downstreamCenter.z()) - extentZ);
			maxX = Math.max(maxX, Math.max(center.x(), downstreamCenter.x()) + extentX);
			maxY = Math.max(maxY, Math.max(center.y(), downstreamCenter.y()) + extentY);
			maxZ = Math.max(maxZ, Math.max(center.z(), downstreamCenter.z()) + extentZ);
			found = true;
		}
		if (!found) {
			return new VoxelGridSpec(Vec3.ZERO, cellSizeMeters, 1, 1, 1);
		}
		int minCellX = (int) Math.floor(minX / cellSizeMeters) - padding;
		int minCellY = (int) Math.floor(minY / cellSizeMeters) - padding;
		int minCellZ = (int) Math.floor(minZ / cellSizeMeters) - padding;
		int maxCellX = (int) Math.ceil(maxX / cellSizeMeters) + padding;
		int maxCellY = (int) Math.ceil(maxY / cellSizeMeters) + padding;
		int maxCellZ = (int) Math.ceil(maxZ / cellSizeMeters) + padding;
		return new VoxelGridSpec(
				new Vec3(
						minCellX * cellSizeMeters,
						minCellY * cellSizeMeters,
						minCellZ * cellSizeMeters
				),
				cellSizeMeters,
				Math.max(1, maxCellX - minCellX),
				Math.max(1, maxCellY - minCellY),
				Math.max(1, maxCellZ - minCellZ)
		);
	}

	private static Vec3 downstreamWakeDirection(
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm,
			Vec3 fallbackNormal
	) {
		Vec3 farWake = finiteVecOrZero(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond());
		if (farWake.lengthSquared() > EPSILON) {
			return farWake.normalized();
		}
		return finiteVecOrZero(fallbackNormal).normalized();
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

	public VoxelGridSample sampleConservativeVoxelGrid(VoxelGridSpec gridSpec) {
		return sampleConservativeVoxelGrid(gridSpec, 1);
	}

	public VoxelGridSample sampleConservativeVoxelGrid(VoxelGridSpec gridSpec, int subcellSamplesPerAxis) {
		if (gridSpec == null) {
			throw new IllegalArgumentException("gridSpec must not be null.");
		}
		int normalizedSubcells = normalizeSubcellSamplesPerAxis(subcellSamplesPerAxis);
		List<SourceCoverage> coverages = sourceCoverages(gridSpec, normalizedSubcells);
		java.util.ArrayList<VoxelCellSample> cells =
				new java.util.ArrayList<>(gridSpec.totalCellCount());
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					cells.add(sampleConservativeVoxelCell(gridSpec, x, y, z, normalizedSubcells, coverages));
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

	private VoxelCellSample sampleConservativeVoxelCell(
			VoxelGridSpec gridSpec,
			int xIndex,
			int yIndex,
			int zIndex,
			int subcellSamplesPerAxis,
			List<SourceCoverage> coverages
	) {
		Vec3 cellCenter = gridSpec.cellCenterWorldMeters(xIndex, yIndex, zIndex);
		int totalSubsamples = subcellSamplesPerAxis * subcellSamplesPerAxis * subcellSamplesPerAxis;
		int activeSubsamples = countActiveSubsamples(gridSpec, cellCenter, subcellSamplesPerAxis, coverages);
		Vec3 bodyForceDensity = Vec3.ZERO;
		Vec3 wakeTorqueDensity = Vec3.ZERO;
		double pressureJump = 0.0;
		double massFlux = 0.0;
		double powerLoading = 0.0;
		Vec3 farWakeAxialVelocity = Vec3.ZERO;
		Vec3 wakeSwirlVelocity = Vec3.ZERO;
		Vec3 targetWakeVelocity = Vec3.ZERO;
		for (SourceCoverage coverage : coverages) {
			if (coverage.densityScale() <= EPSILON) {
				continue;
			}
			SourceCellCoverage cellCoverage =
					sourceCellCoverage(gridSpec, cellCenter, subcellSamplesPerAxis, coverage.sourceTerm());
			if (cellCoverage.activeSubsamples() == 0) {
				continue;
			}
			double sourceWeight = cellCoverage.sourceVolumeFraction() * coverage.densityScale();
			bodyForceDensity = bodyForceDensity.add(coverage.sourceTerm()
					.equivalentBodyForceWorldNewtonsPerCubicMeter(sourceThicknessMeters)
					.multiply(sourceWeight));
			wakeTorqueDensity = wakeTorqueDensity.add(coverage.sourceTerm()
					.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(sourceThicknessMeters)
					.multiply(sourceWeight));
			pressureJump += coverage.sourceTerm().pressureJumpPascals() * sourceWeight;
			massFlux += coverage.sourceTerm().massFluxKilogramsPerSecondSquareMeter() * sourceWeight;
			powerLoading += coverage.sourceTerm().idealMomentumPowerLoadingWattsPerSquareMeter() * sourceWeight;
			farWakeAxialVelocity = farWakeAxialVelocity.add(
					coverage.sourceTerm().farWakeAxialVelocityWorldMetersPerSecond().multiply(sourceWeight));
			wakeSwirlVelocity = wakeSwirlVelocity.add(cellCoverage.averageWakeSwirlVelocityWorldMetersPerSecond()
					.multiply(sourceWeight));
			targetWakeVelocity = targetWakeVelocity.add(
					coverage.sourceTerm().farWakeAxialVelocityWorldMetersPerSecond()
							.add(cellCoverage.averageWakeSwirlVelocityWorldMetersPerSecond())
							.multiply(sourceWeight));
		}
		return new VoxelCellSample(
				xIndex,
				yIndex,
				zIndex,
				cellCenter,
				gridSpec.cellVolumeCubicMeters(),
				totalSubsamples,
				activeSubsamples,
				activeSubsamples / (double) totalSubsamples,
				bodyForceDensity,
				wakeTorqueDensity,
				pressureJump,
				massFlux,
				powerLoading,
				farWakeAxialVelocity,
				wakeSwirlVelocity,
				targetWakeVelocity
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

	private static double diskAxisAlignedExtent(double diskRadius, double halfThickness, double normalComponent) {
		double clampedNormalComponent = MathUtil.clamp(normalComponent, -1.0, 1.0);
		double radialProjection = Math.sqrt(Math.max(0.0, 1.0 - clampedNormalComponent * clampedNormalComponent));
		return diskRadius * radialProjection + halfThickness * Math.abs(clampedNormalComponent);
	}

	private List<SourceCoverage> sourceCoverages(VoxelGridSpec gridSpec, int subcellSamplesPerAxis) {
		java.util.ArrayList<SourceCoverage> coverages = new java.util.ArrayList<>();
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (sourceTerm == null || !sourceTerm.applied()) {
				continue;
			}
			double sampledVolume = sampledVolumeCubicMeters(gridSpec, subcellSamplesPerAxis, sourceTerm);
			double targetVolume = sourceTerm.sourceVolumeCubicMeters(sourceThicknessMeters);
			double densityScale = sampledVolume > EPSILON && targetVolume > EPSILON
					? targetVolume / sampledVolume
					: 0.0;
			coverages.add(new SourceCoverage(sourceTerm, sampledVolume, targetVolume, densityScale));
		}
		return List.copyOf(coverages);
	}

	private double sampledVolumeCubicMeters(
			VoxelGridSpec gridSpec,
			int subcellSamplesPerAxis,
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm
	) {
		double sampledSubcellVolume = gridSpec.cellVolumeCubicMeters()
				/ (subcellSamplesPerAxis * subcellSamplesPerAxis * subcellSamplesPerAxis);
		double volume = 0.0;
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					Vec3 cellCenter = gridSpec.cellCenterWorldMeters(x, y, z);
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
								if (containsActuatorDiskVolume(sourceTerm, subcellPoint)) {
									volume += sampledSubcellVolume;
								}
							}
						}
					}
				}
			}
		}
		return volume;
	}

	private int countActiveSubsamples(
			VoxelGridSpec gridSpec,
			Vec3 cellCenterWorldMeters,
			int subcellSamplesPerAxis,
			List<SourceCoverage> coverages
	) {
		int activeSubsamples = 0;
		for (int sy = 0; sy < subcellSamplesPerAxis; sy++) {
			for (int sz = 0; sz < subcellSamplesPerAxis; sz++) {
				for (int sx = 0; sx < subcellSamplesPerAxis; sx++) {
					Vec3 subcellPoint = subcellPointWorldMeters(
							cellCenterWorldMeters,
							gridSpec.cellSizeMeters(),
							subcellSamplesPerAxis,
							sx,
							sy,
							sz
					);
					if (anyCoverageContains(coverages, subcellPoint)) {
						activeSubsamples++;
					}
				}
			}
		}
		return activeSubsamples;
	}

	private boolean anyCoverageContains(List<SourceCoverage> coverages, Vec3 samplePointWorldMeters) {
		for (SourceCoverage coverage : coverages) {
			if (containsActuatorDiskVolume(coverage.sourceTerm(), samplePointWorldMeters)) {
				return true;
			}
		}
		return false;
	}

	private SourceCellCoverage sourceCellCoverage(
			VoxelGridSpec gridSpec,
			Vec3 cellCenterWorldMeters,
			int subcellSamplesPerAxis,
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm
	) {
		int totalSubsamples = subcellSamplesPerAxis * subcellSamplesPerAxis * subcellSamplesPerAxis;
		int activeSubsamples = 0;
		Vec3 wakeSwirlVelocity = Vec3.ZERO;
		for (int sy = 0; sy < subcellSamplesPerAxis; sy++) {
			for (int sz = 0; sz < subcellSamplesPerAxis; sz++) {
				for (int sx = 0; sx < subcellSamplesPerAxis; sx++) {
					Vec3 subcellPoint = subcellPointWorldMeters(
							cellCenterWorldMeters,
							gridSpec.cellSizeMeters(),
							subcellSamplesPerAxis,
							sx,
							sy,
							sz
					);
					if (containsActuatorDiskVolume(sourceTerm, subcellPoint)) {
						activeSubsamples++;
						wakeSwirlVelocity =
								wakeSwirlVelocity.add(sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(subcellPoint));
					}
				}
			}
		}
		return new SourceCellCoverage(
				activeSubsamples,
				activeSubsamples / (double) totalSubsamples,
				activeSubsamples == 0
						? Vec3.ZERO
						: wakeSwirlVelocity.multiply(1.0 / activeSubsamples)
		);
	}

	private record SourceCoverage(
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm,
			double sampledVolumeCubicMeters,
			double targetVolumeCubicMeters,
			double densityScale
	) {
	}

	private record SourceCellCoverage(
			int activeSubsamples,
			double sourceVolumeFraction,
			Vec3 averageWakeSwirlVelocityWorldMetersPerSecond
	) {
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
