package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public record PropellerArchiveCtCpJLocalVoxelFlowState(
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
		List<Vec3> velocitiesWorldMetersPerSecond
) {
	private static final double EPSILON = 1.0e-12;

	public PropellerArchiveCtCpJLocalVoxelFlowState {
		if (gridSpec == null) {
			throw new IllegalArgumentException("gridSpec must not be null.");
		}
		List<Vec3> velocities = velocitiesWorldMetersPerSecond == null
				? List.of()
				: velocitiesWorldMetersPerSecond;
		if (velocities.size() != gridSpec.totalCellCount()) {
			throw new IllegalArgumentException("velocity count must match voxel grid cell count.");
		}
		ArrayList<Vec3> sanitized = new ArrayList<>(velocities.size());
		for (Vec3 velocity : velocities) {
			sanitized.add(finiteVecOrZero(velocity));
		}
		velocitiesWorldMetersPerSecond = List.copyOf(sanitized);
	}

	public record VoxelFlowAdvance(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			PropellerArchiveCtCpJLocalVoxelMomentumStep.MassFluxResidenceStepSample residenceStep
	) {
		public VoxelFlowAdvance {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (residenceStep == null) {
				throw new IllegalArgumentException("residenceStep must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("flow advance states must share a voxel grid.");
			}
		}

		public Vec3 totalSourceMomentumRateWorldNewtons() {
			return residenceStep.totalSourceMomentumRateWorldNewtons();
		}

		public Vec3 totalThroughFlowMomentumRateWorldNewtons() {
			return residenceStep.totalThroughFlowMomentumRateWorldNewtons();
		}

		public Vec3 totalCombinedMomentumRateWorldNewtons() {
			return residenceStep.totalCombinedMomentumRateWorldNewtons();
		}

		public Vec3 totalSourceImpulseWorldNewtonSeconds() {
			return residenceStep.totalSourceImpulseWorldNewtonSeconds();
		}

		public Vec3 totalThroughFlowImpulseWorldNewtonSeconds() {
			return residenceStep.totalThroughFlowImpulseWorldNewtonSeconds();
		}

		public double totalSourceMassFlowRateKilogramsPerSecond() {
			return residenceStep.totalSourceMassFlowRateKilogramsPerSecond();
		}

		public double maxResidenceAlpha() {
			return residenceStep.maxResidenceAlpha();
		}

		public double meanActiveWakeResidualAfterResidenceMetersPerSecond() {
			double sum = 0.0;
			int count = 0;
			for (PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep cell
					: residenceStep.activeCells()) {
				sum += cell.targetWakeVelocityResidualAfterResidenceWorldMetersPerSecond().length();
				count++;
			}
			return count == 0 ? 0.0 : sum / count;
		}
	}

	public static PropellerArchiveCtCpJLocalVoxelFlowState calm(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec
	) {
		return uniform(gridSpec, Vec3.ZERO);
	}

	public static PropellerArchiveCtCpJLocalVoxelFlowState uniform(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
			Vec3 velocityWorldMetersPerSecond
	) {
		if (gridSpec == null) {
			throw new IllegalArgumentException("gridSpec must not be null.");
		}
		Vec3 velocity = finiteVecOrZero(velocityWorldMetersPerSecond);
		ArrayList<Vec3> velocities = new ArrayList<>(gridSpec.totalCellCount());
		for (int i = 0; i < gridSpec.totalCellCount(); i++) {
			velocities.add(velocity);
		}
		return new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, velocities);
	}

	public Vec3 velocityAt(int xIndex, int yIndex, int zIndex) {
		return velocitiesWorldMetersPerSecond.get(linearIndex(xIndex, yIndex, zIndex));
	}

	public VoxelFlowAdvance advanceWithSource(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double sourceThicknessMeters
	) {
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		if (!gridSpec.equals(sourceGridSample.gridSpec())) {
			throw new IllegalArgumentException("sourceGridSample grid must match this flow state.");
		}
		PropellerArchiveCtCpJLocalVoxelMomentumStep.MassFluxResidenceStepSample residenceStep =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.stepWithMassFluxResidence(
						sourceGridSample,
						airDensityKgPerCubicMeter,
						timeStepSeconds,
						sourceThicknessMeters,
						velocitiesWorldMetersPerSecond
				);
		ArrayList<Vec3> nextVelocities = new ArrayList<>(residenceStep.cells().size());
		for (PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep cell
				: residenceStep.cells()) {
			nextVelocities.add(cell.velocityAfterResidenceWorldMetersPerSecond());
		}
		return new VoxelFlowAdvance(
				this,
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities),
				residenceStep
		);
	}

	public double totalKineticEnergyJoules(double airDensityKgPerCubicMeter) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		double cellMass = airDensityKgPerCubicMeter * gridSpec.cellVolumeCubicMeters();
		double energy = 0.0;
		for (Vec3 velocity : velocitiesWorldMetersPerSecond) {
			energy += 0.5 * cellMass * velocity.lengthSquared();
		}
		return energy;
	}

	public double maxSpeedMetersPerSecond() {
		double max = 0.0;
		for (Vec3 velocity : velocitiesWorldMetersPerSecond) {
			max = Math.max(max, velocity.length());
		}
		return max;
	}

	private int linearIndex(int xIndex, int yIndex, int zIndex) {
		if (xIndex < 0 || xIndex >= gridSpec.cellCountX()
				|| yIndex < 0 || yIndex >= gridSpec.cellCountY()
				|| zIndex < 0 || zIndex >= gridSpec.cellCountZ()) {
			throw new IndexOutOfBoundsException("cell index outside voxel grid.");
		}
		return (yIndex * gridSpec.cellCountZ() + zIndex) * gridSpec.cellCountX() + xIndex;
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}
}
