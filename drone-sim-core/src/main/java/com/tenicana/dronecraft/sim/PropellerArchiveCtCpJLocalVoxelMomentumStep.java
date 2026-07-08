package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLocalVoxelMomentumStep {
	private static final double EPSILON = 1.0e-12;

	private PropellerArchiveCtCpJLocalVoxelMomentumStep() {
	}

	public record CellMomentumStep(
			int xIndex,
			int yIndex,
			int zIndex,
			Vec3 cellCenterWorldMeters,
			double cellVolumeCubicMeters,
			boolean active,
			double sourceVolumeFraction,
			Vec3 initialVelocityWorldMetersPerSecond,
			Vec3 sourceAccelerationWorldMetersPerSecondSquared,
			Vec3 velocityDeltaWorldMetersPerSecond,
			Vec3 velocityAfterStepWorldMetersPerSecond,
			Vec3 targetWakeVelocityWorldMetersPerSecond,
			Vec3 targetWakeVelocityResidualWorldMetersPerSecond,
			Vec3 momentumRateWorldNewtons,
			Vec3 impulseWorldNewtonSeconds,
			Vec3 wakeAngularMomentumTorqueWorldNewtonMeters,
			Vec3 wakeAngularMomentumImpulseWorldNewtonMeterSeconds
	) {
		public CellMomentumStep {
			xIndex = Math.max(0, xIndex);
			yIndex = Math.max(0, yIndex);
			zIndex = Math.max(0, zIndex);
			cellCenterWorldMeters = finiteVecOrZero(cellCenterWorldMeters);
			cellVolumeCubicMeters = finiteNonnegative(cellVolumeCubicMeters);
			sourceVolumeFraction = MathUtil.clamp(sourceVolumeFraction, 0.0, 1.0);
			initialVelocityWorldMetersPerSecond = finiteVecOrZero(initialVelocityWorldMetersPerSecond);
			sourceAccelerationWorldMetersPerSecondSquared =
					finiteVecOrZero(sourceAccelerationWorldMetersPerSecondSquared);
			velocityDeltaWorldMetersPerSecond = finiteVecOrZero(velocityDeltaWorldMetersPerSecond);
			velocityAfterStepWorldMetersPerSecond = finiteVecOrZero(velocityAfterStepWorldMetersPerSecond);
			targetWakeVelocityWorldMetersPerSecond = finiteVecOrZero(targetWakeVelocityWorldMetersPerSecond);
			targetWakeVelocityResidualWorldMetersPerSecond =
					finiteVecOrZero(targetWakeVelocityResidualWorldMetersPerSecond);
			momentumRateWorldNewtons = finiteVecOrZero(momentumRateWorldNewtons);
			impulseWorldNewtonSeconds = finiteVecOrZero(impulseWorldNewtonSeconds);
			wakeAngularMomentumTorqueWorldNewtonMeters =
					finiteVecOrZero(wakeAngularMomentumTorqueWorldNewtonMeters);
			wakeAngularMomentumImpulseWorldNewtonMeterSeconds =
					finiteVecOrZero(wakeAngularMomentumImpulseWorldNewtonMeterSeconds);
		}
	}

	public record MomentumStepSample(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			List<CellMomentumStep> cells
	) {
		public MomentumStepSample {
			if (sourceGridSample == null) {
				throw new IllegalArgumentException("sourceGridSample must not be null.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			cells = List.copyOf(cells == null ? List.of() : cells);
		}

		public List<CellMomentumStep> activeCells() {
			return cells.stream()
					.filter(CellMomentumStep::active)
					.toList();
		}

		public int activeCellCount() {
			int count = 0;
			for (CellMomentumStep cell : cells) {
				if (cell.active()) {
					count++;
				}
			}
			return count;
		}

		public Vec3 totalMomentumRateWorldNewtons() {
			Vec3 sum = Vec3.ZERO;
			for (CellMomentumStep cell : cells) {
				sum = sum.add(cell.momentumRateWorldNewtons());
			}
			return sum;
		}

		public Vec3 totalImpulseWorldNewtonSeconds() {
			Vec3 sum = Vec3.ZERO;
			for (CellMomentumStep cell : cells) {
				sum = sum.add(cell.impulseWorldNewtonSeconds());
			}
			return sum;
		}

		public Vec3 totalWakeAngularMomentumTorqueWorldNewtonMeters() {
			Vec3 sum = Vec3.ZERO;
			for (CellMomentumStep cell : cells) {
				sum = sum.add(cell.wakeAngularMomentumTorqueWorldNewtonMeters());
			}
			return sum;
		}

		public Vec3 totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds() {
			Vec3 sum = Vec3.ZERO;
			for (CellMomentumStep cell : cells) {
				sum = sum.add(cell.wakeAngularMomentumImpulseWorldNewtonMeterSeconds());
			}
			return sum;
		}

		public double maxVelocityDeltaMetersPerSecond() {
			double max = 0.0;
			for (CellMomentumStep cell : cells) {
				max = Math.max(max, cell.velocityDeltaWorldMetersPerSecond().length());
			}
			return max;
		}
	}

	public static MomentumStepSample step(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds
	) {
		return step(sourceGridSample, airDensityKgPerCubicMeter, timeStepSeconds, Vec3.ZERO);
	}

	public static MomentumStepSample step(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			Vec3 uniformInitialVelocityWorldMetersPerSecond
	) {
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		Vec3 initialVelocity = finiteVecOrZero(uniformInitialVelocityWorldMetersPerSecond);
		ArrayList<Vec3> initialVelocities = new ArrayList<>(sourceGridSample.cells().size());
		for (int i = 0; i < sourceGridSample.cells().size(); i++) {
			initialVelocities.add(initialVelocity);
		}
		return step(sourceGridSample, airDensityKgPerCubicMeter, timeStepSeconds, initialVelocities);
	}

	public static MomentumStepSample step(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			List<Vec3> initialCellVelocitiesWorldMetersPerSecond
	) {
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
			throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
		}
		List<Vec3> initialVelocities = initialCellVelocitiesWorldMetersPerSecond == null
				? List.of()
				: List.copyOf(initialCellVelocitiesWorldMetersPerSecond);
		if (!initialVelocities.isEmpty() && initialVelocities.size() != sourceGridSample.cells().size()) {
			throw new IllegalArgumentException("initialCellVelocities size must match sourceGridSample cells.");
		}
		ArrayList<CellMomentumStep> cells = new ArrayList<>(sourceGridSample.cells().size());
		for (int i = 0; i < sourceGridSample.cells().size(); i++) {
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell =
					sourceGridSample.cells().get(i);
			Vec3 initialVelocity = initialVelocities.isEmpty()
					? Vec3.ZERO
					: finiteVecOrZero(initialVelocities.get(i));
			cells.add(stepCell(sourceCell, airDensityKgPerCubicMeter, timeStepSeconds, initialVelocity));
		}
		return new MomentumStepSample(
				sourceGridSample,
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				cells
		);
	}

	private static CellMomentumStep stepCell(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			Vec3 initialVelocityWorldMetersPerSecond
	) {
		Vec3 acceleration = sourceCell.bodyForceDensityWorldNewtonsPerCubicMeter()
				.multiply(1.0 / airDensityKgPerCubicMeter);
		Vec3 velocityDelta = acceleration.multiply(timeStepSeconds);
		Vec3 velocityAfterStep = initialVelocityWorldMetersPerSecond.add(velocityDelta);
		Vec3 targetWakeVelocity = sourceCell.targetWakeVelocityWorldMetersPerSecond();
		Vec3 momentumRate = sourceCell.integratedBodyForceWorldNewtons();
		Vec3 impulse = momentumRate.multiply(timeStepSeconds);
		Vec3 wakeTorque = sourceCell.integratedWakeAngularMomentumTorqueWorldNewtonMeters();
		Vec3 wakeTorqueImpulse = wakeTorque.multiply(timeStepSeconds);
		return new CellMomentumStep(
				sourceCell.xIndex(),
				sourceCell.yIndex(),
				sourceCell.zIndex(),
				sourceCell.cellCenterWorldMeters(),
				sourceCell.cellVolumeCubicMeters(),
				sourceCell.active(),
				sourceCell.sourceVolumeFraction(),
				initialVelocityWorldMetersPerSecond,
				acceleration,
				velocityDelta,
				velocityAfterStep,
				targetWakeVelocity,
				targetWakeVelocity.subtract(velocityAfterStep),
				momentumRate,
				impulse,
				wakeTorque,
				wakeTorqueImpulse
		);
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	private static double finiteNonnegative(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 0.0;
	}
}
