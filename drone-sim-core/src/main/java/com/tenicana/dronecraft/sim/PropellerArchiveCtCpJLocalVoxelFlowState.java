package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public record PropellerArchiveCtCpJLocalVoxelFlowState(
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
		List<Vec3> velocitiesWorldMetersPerSecond
) {
	private static final double EPSILON = 1.0e-12;
	private static final int MAX_ADVECTION_SUBSTEPS = 4096;
	private static final int MAX_PRESSURE_PROJECTION_ITERATIONS = 4096;

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

	public record VoxelSolidMask(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
			List<Boolean> solidCells
	) {
		public VoxelSolidMask {
			if (gridSpec == null) {
				throw new IllegalArgumentException("gridSpec must not be null.");
			}
			List<Boolean> cells = solidCells == null ? List.of() : solidCells;
			if (cells.size() != gridSpec.totalCellCount()) {
				throw new IllegalArgumentException("solid cell count must match voxel grid cell count.");
			}
			ArrayList<Boolean> sanitized = new ArrayList<>(cells.size());
			for (Boolean solid : cells) {
				sanitized.add(Boolean.TRUE.equals(solid));
			}
			solidCells = List.copyOf(sanitized);
		}

		public static VoxelSolidMask open(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec
		) {
			if (gridSpec == null) {
				throw new IllegalArgumentException("gridSpec must not be null.");
			}
			ArrayList<Boolean> cells = new ArrayList<>(gridSpec.totalCellCount());
			for (int i = 0; i < gridSpec.totalCellCount(); i++) {
				cells.add(Boolean.FALSE);
			}
			return new VoxelSolidMask(gridSpec, cells);
		}

		public int solidCellCount() {
			int count = 0;
			for (Boolean solid : solidCells) {
				if (solid.booleanValue()) {
					count++;
				}
			}
			return count;
		}

		public boolean isSolid(int xIndex, int yIndex, int zIndex) {
			return isSolidCellIndex(linearIndex(xIndex, yIndex, zIndex));
		}

		public boolean isSolidCellIndex(int cellIndex) {
			if (cellIndex < 0 || cellIndex >= solidCells.size()) {
				throw new IndexOutOfBoundsException("cell index outside voxel grid.");
			}
			return solidCells.get(cellIndex).booleanValue();
		}

		private int linearIndex(int xIndex, int yIndex, int zIndex) {
			if (xIndex < 0 || xIndex >= gridSpec.cellCountX()
					|| yIndex < 0 || yIndex >= gridSpec.cellCountY()
					|| zIndex < 0 || zIndex >= gridSpec.cellCountZ()) {
				throw new IndexOutOfBoundsException("cell index outside voxel grid.");
			}
			return (yIndex * gridSpec.cellCountZ() + zIndex) * gridSpec.cellCountX() + xIndex;
		}
	}

	public record SolidBoundaryStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			VoxelSolidMask solidMask,
			double airDensityKgPerCubicMeter,
			int solidCellCount,
			int clampedCellCount,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public SolidBoundaryStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (solidMask == null) {
				throw new IllegalArgumentException("solidMask must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())
					|| !previousState.gridSpec().equals(solidMask.gridSpec())) {
				throw new IllegalArgumentException("solid boundary states and mask must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (solidCellCount < 0) {
				throw new IllegalArgumentException("solidCellCount must be nonnegative.");
			}
			if (clampedCellCount < 0 || clampedCellCount > solidCellCount) {
				throw new IllegalArgumentException("clampedCellCount must be within solid cell count.");
			}
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
		}
	}

	public record VelocityDiffusionStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			double kinematicViscositySquareMetersPerSecond,
			double timeStepSeconds,
			double diffusionNumber,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public VelocityDiffusionStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("diffusion states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(kinematicViscositySquareMetersPerSecond)
					|| kinematicViscositySquareMetersPerSecond < 0.0) {
				throw new IllegalArgumentException(
						"kinematicViscositySquareMetersPerSecond must be finite and nonnegative.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			diffusionNumber = finiteNonnegative(diffusionNumber);
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
		}
	}

	public record VelocityAdvectionStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double maxCourantNumber,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public VelocityAdvectionStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("advection states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			maxCourantNumber = finiteNonnegative(maxCourantNumber);
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
		}
	}

	public record VelocityAdvectionRun(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double maxAllowedCourantNumber,
			List<VelocityAdvectionStep> substeps
	) {
		public VelocityAdvectionRun {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("advection run states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			if (!Double.isFinite(maxAllowedCourantNumber) || maxAllowedCourantNumber <= EPSILON) {
				throw new IllegalArgumentException("maxAllowedCourantNumber must be finite and positive.");
			}
			substeps = List.copyOf(substeps == null ? List.of() : substeps);
			if (substeps.isEmpty()) {
				throw new IllegalArgumentException("advection run must contain at least one substep.");
			}
			if (!substeps.get(0).previousState().equals(previousState)) {
				throw new IllegalArgumentException("first advection substep must start from previousState.");
			}
			if (!substeps.get(substeps.size() - 1).nextState().equals(nextState)) {
				throw new IllegalArgumentException("last advection substep must end at nextState.");
			}
			for (int i = 1; i < substeps.size(); i++) {
				if (!substeps.get(i - 1).nextState().equals(substeps.get(i).previousState())) {
					throw new IllegalArgumentException("advection substeps must be contiguous.");
				}
			}
		}

		public int completedSubstepCount() {
			return substeps.size();
		}

		public double maxCourantNumber() {
			double max = 0.0;
			for (VelocityAdvectionStep substep : substeps) {
				max = Math.max(max, substep.maxCourantNumber());
			}
			return max;
		}

		public Vec3 totalMomentumBeforeWorldNewtonSeconds() {
			return substeps.get(0).totalMomentumBeforeWorldNewtonSeconds();
		}

		public Vec3 totalMomentumAfterWorldNewtonSeconds() {
			return substeps.get(substeps.size() - 1).totalMomentumAfterWorldNewtonSeconds();
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds().subtract(totalMomentumBeforeWorldNewtonSeconds());
		}

		public double kineticEnergyBeforeJoules() {
			return substeps.get(0).kineticEnergyBeforeJoules();
		}

		public double kineticEnergyAfterJoules() {
			return substeps.get(substeps.size() - 1).kineticEnergyAfterJoules();
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules() - kineticEnergyBeforeJoules();
		}
	}

	public record DivergenceMetrics(
			double maxAbsDivergencePerSecond,
			double rmsDivergencePerSecond,
			double meanDivergencePerSecond
	) {
		public DivergenceMetrics {
			maxAbsDivergencePerSecond = finiteNonnegative(maxAbsDivergencePerSecond);
			rmsDivergencePerSecond = finiteNonnegative(rmsDivergencePerSecond);
			meanDivergencePerSecond = Double.isFinite(meanDivergencePerSecond)
					? meanDivergencePerSecond
					: 0.0;
		}
	}

	public record VelocityProjectionStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			int pressureProjectionIterations,
			DivergenceMetrics divergenceBefore,
			DivergenceMetrics divergenceAfter,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public VelocityProjectionStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("projection states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (pressureProjectionIterations < 0) {
				throw new IllegalArgumentException("pressureProjectionIterations must be nonnegative.");
			}
			if (pressureProjectionIterations > MAX_PRESSURE_PROJECTION_ITERATIONS) {
				throw new IllegalArgumentException("pressureProjectionIterations exceeds maximum supported bound.");
			}
			divergenceBefore = divergenceBefore == null
					? new DivergenceMetrics(0.0, 0.0, 0.0)
					: divergenceBefore;
			divergenceAfter = divergenceAfter == null
					? new DivergenceMetrics(0.0, 0.0, 0.0)
					: divergenceAfter;
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
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

	public SolidBoundaryStep applySolidMask(
			VoxelSolidMask solidMask,
			double airDensityKgPerCubicMeter
	) {
		if (solidMask == null) {
			throw new IllegalArgumentException("solidMask must not be null.");
		}
		if (!gridSpec.equals(solidMask.gridSpec())) {
			throw new IllegalArgumentException("solidMask grid must match this flow state.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		int clampedCellCount = 0;
		for (int i = 0; i < velocitiesWorldMetersPerSecond.size(); i++) {
			Vec3 velocity = velocitiesWorldMetersPerSecond.get(i);
			if (solidMask.isSolidCellIndex(i)) {
				if (velocity.lengthSquared() > EPSILON * EPSILON) {
					clampedCellCount++;
				}
				nextVelocities.add(Vec3.ZERO);
			} else {
				nextVelocities.add(velocity);
			}
		}
		PropellerArchiveCtCpJLocalVoxelFlowState nextState =
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		return new SolidBoundaryStep(
				this,
				nextState,
				solidMask,
				airDensityKgPerCubicMeter,
				solidMask.solidCellCount(),
				clampedCellCount,
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter)
		);
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

	public VelocityAdvectionStep advectVelocity(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
			throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
		}
		double maxCourantNumber = 0.0;
		ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					Vec3 currentVelocity = velocityAt(x, y, z);
					maxCourantNumber = Math.max(
							maxCourantNumber,
							currentVelocity.length() * timeStepSeconds / gridSpec.cellSizeMeters()
					);
					Vec3 backtracedPoint = gridSpec.cellCenterWorldMeters(x, y, z)
							.subtract(currentVelocity.multiply(timeStepSeconds));
					nextVelocities.add(sampleVelocityClamped(backtracedPoint));
				}
			}
		}
		PropellerArchiveCtCpJLocalVoxelFlowState nextState =
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		return new VelocityAdvectionStep(
				this,
				nextState,
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				maxCourantNumber,
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter)
		);
	}

	public VelocityAdvectionRun advectVelocityWithCourantLimit(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double maxAllowedCourantNumber
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
			throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
		}
		if (!Double.isFinite(maxAllowedCourantNumber) || maxAllowedCourantNumber <= EPSILON) {
			throw new IllegalArgumentException("maxAllowedCourantNumber must be finite and positive.");
		}
		int substepCount = advectionSubstepCount(timeStepSeconds, maxAllowedCourantNumber);
		double substepSeconds = timeStepSeconds / substepCount;
		ArrayList<VelocityAdvectionStep> substeps = new ArrayList<>(substepCount);
		PropellerArchiveCtCpJLocalVoxelFlowState state = this;
		for (int i = 0; i < substepCount; i++) {
			VelocityAdvectionStep substep = state.advectVelocity(airDensityKgPerCubicMeter, substepSeconds);
			substeps.add(substep);
			state = substep.nextState();
		}
		return new VelocityAdvectionRun(
				this,
				state,
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				maxAllowedCourantNumber,
				substeps
		);
	}

	public VelocityDiffusionStep diffuseVelocity(
			double airDensityKgPerCubicMeter,
			double kinematicViscositySquareMetersPerSecond,
			double timeStepSeconds
	) {
		return diffuseVelocity(
				airDensityKgPerCubicMeter,
				kinematicViscositySquareMetersPerSecond,
				timeStepSeconds,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public VelocityDiffusionStep diffuseVelocity(
			double airDensityKgPerCubicMeter,
			double kinematicViscositySquareMetersPerSecond,
			double timeStepSeconds,
			VoxelSolidMask solidMask
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(kinematicViscositySquareMetersPerSecond)
				|| kinematicViscositySquareMetersPerSecond < 0.0) {
			throw new IllegalArgumentException(
					"kinematicViscositySquareMetersPerSecond must be finite and nonnegative.");
		}
		if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
			throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
		}
		validateSolidMask(solidMask);
		double diffusionNumber = kinematicViscositySquareMetersPerSecond
				* timeStepSeconds
				/ (gridSpec.cellSizeMeters() * gridSpec.cellSizeMeters());
		if (diffusionNumber > 1.0 / 6.0 + 1.0e-15) {
			throw new IllegalArgumentException("diffusionNumber must be <= 1/6 for explicit 3D stability.");
		}
		ArrayList<Vec3> deltas = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		for (int i = 0; i < velocitiesWorldMetersPerSecond.size(); i++) {
			deltas.add(Vec3.ZERO);
		}
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					int index = linearIndex(x, y, z);
					if (x + 1 < gridSpec.cellCountX()) {
						accumulateDiffusiveExchange(
								deltas,
								index,
								linearIndex(x + 1, y, z),
								diffusionNumber,
								solidMask);
					}
					if (y + 1 < gridSpec.cellCountY()) {
						accumulateDiffusiveExchange(
								deltas,
								index,
								linearIndex(x, y + 1, z),
								diffusionNumber,
								solidMask);
					}
					if (z + 1 < gridSpec.cellCountZ()) {
						accumulateDiffusiveExchange(
								deltas,
								index,
								linearIndex(x, y, z + 1),
								diffusionNumber,
								solidMask);
					}
				}
			}
		}
		ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		for (int i = 0; i < velocitiesWorldMetersPerSecond.size(); i++) {
			nextVelocities.add(velocitiesWorldMetersPerSecond.get(i).add(deltas.get(i)));
		}
		PropellerArchiveCtCpJLocalVoxelFlowState nextState =
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		return new VelocityDiffusionStep(
				this,
				nextState,
				airDensityKgPerCubicMeter,
				kinematicViscositySquareMetersPerSecond,
				timeStepSeconds,
				diffusionNumber,
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter)
		);
	}

	public VelocityProjectionStep projectVelocityDivergence(
			double airDensityKgPerCubicMeter,
			int pressureProjectionIterations
	) {
		return projectVelocityDivergence(
				airDensityKgPerCubicMeter,
				pressureProjectionIterations,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public VelocityProjectionStep projectVelocityDivergence(
			double airDensityKgPerCubicMeter,
			int pressureProjectionIterations,
			VoxelSolidMask solidMask
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (pressureProjectionIterations < 0) {
			throw new IllegalArgumentException("pressureProjectionIterations must be nonnegative.");
		}
		if (pressureProjectionIterations > MAX_PRESSURE_PROJECTION_ITERATIONS) {
			throw new IllegalArgumentException("pressureProjectionIterations exceeds maximum supported bound.");
		}
		validateSolidMask(solidMask);
		DivergenceMetrics divergenceBefore = divergenceMetrics(solidMask);
		PropellerArchiveCtCpJLocalVoxelFlowState nextState = this;
		if (pressureProjectionIterations > 0 && divergenceBefore.maxAbsDivergencePerSecond() > EPSILON) {
			double[] divergence = divergenceValues(solidMask);
			double[] pressurePotential = pressurePotential(divergence, pressureProjectionIterations, solidMask);
			ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
			for (int y = 0; y < gridSpec.cellCountY(); y++) {
				for (int z = 0; z < gridSpec.cellCountZ(); z++) {
					for (int x = 0; x < gridSpec.cellCountX(); x++) {
						int index = linearIndex(x, y, z);
						nextVelocities.add(solidMask.isSolidCellIndex(index)
								? velocityAt(x, y, z)
								: projectedVelocityAt(pressurePotential, solidMask, x, y, z));
					}
				}
			}
			nextState = new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		}
		return new VelocityProjectionStep(
				this,
				nextState,
				airDensityKgPerCubicMeter,
				pressureProjectionIterations,
				divergenceBefore,
				nextState.divergenceMetrics(solidMask),
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter)
		);
	}

	public DivergenceMetrics divergenceMetrics() {
		return divergenceMetrics(VoxelSolidMask.open(gridSpec));
	}

	public DivergenceMetrics divergenceMetrics(VoxelSolidMask solidMask) {
		validateSolidMask(solidMask);
		double[] divergence = divergenceValues(solidMask);
		double maxAbs = 0.0;
		double sumSquares = 0.0;
		double sum = 0.0;
		int openCount = 0;
		for (int i = 0; i < divergence.length; i++) {
			if (solidMask.isSolidCellIndex(i)) {
				continue;
			}
			double value = divergence[i];
			maxAbs = Math.max(maxAbs, Math.abs(value));
			sumSquares += value * value;
			sum += value;
			openCount++;
		}
		return new DivergenceMetrics(
				maxAbs,
				openCount == 0 ? 0.0 : Math.sqrt(sumSquares / openCount),
				openCount == 0 ? 0.0 : sum / openCount
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

	public Vec3 totalMomentumWorldNewtonSeconds(double airDensityKgPerCubicMeter) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		double cellMass = airDensityKgPerCubicMeter * gridSpec.cellVolumeCubicMeters();
		Vec3 momentum = Vec3.ZERO;
		for (Vec3 velocity : velocitiesWorldMetersPerSecond) {
			momentum = momentum.add(velocity.multiply(cellMass));
		}
		return momentum;
	}

	public double maxSpeedMetersPerSecond() {
		double max = 0.0;
		for (Vec3 velocity : velocitiesWorldMetersPerSecond) {
			max = Math.max(max, velocity.length());
		}
		return max;
	}

	private int advectionSubstepCount(double timeStepSeconds, double maxAllowedCourantNumber) {
		double maxCourantNumber = maxSpeedMetersPerSecond() * timeStepSeconds / gridSpec.cellSizeMeters();
		int substepCount = Math.max(1, (int) Math.ceil(maxCourantNumber / maxAllowedCourantNumber));
		if (substepCount > MAX_ADVECTION_SUBSTEPS) {
			throw new IllegalArgumentException("advection substep count exceeds maximum supported bound.");
		}
		return substepCount;
	}

	private int linearIndex(int xIndex, int yIndex, int zIndex) {
		if (xIndex < 0 || xIndex >= gridSpec.cellCountX()
				|| yIndex < 0 || yIndex >= gridSpec.cellCountY()
				|| zIndex < 0 || zIndex >= gridSpec.cellCountZ()) {
			throw new IndexOutOfBoundsException("cell index outside voxel grid.");
		}
		return (yIndex * gridSpec.cellCountZ() + zIndex) * gridSpec.cellCountX() + xIndex;
	}

	private double[] divergenceValues() {
		return divergenceValues(VoxelSolidMask.open(gridSpec));
	}

	private double[] divergenceValues(VoxelSolidMask solidMask) {
		double[] divergence = new double[velocitiesWorldMetersPerSecond.size()];
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					divergence[linearIndex(x, y, z)] = divergenceAt(x, y, z, solidMask);
				}
			}
		}
		return divergence;
	}

	private double divergenceAt(int x, int y, int z) {
		return divergenceAt(x, y, z, VoxelSolidMask.open(gridSpec));
	}

	private double divergenceAt(int x, int y, int z, VoxelSolidMask solidMask) {
		int centerIndex = linearIndex(x, y, z);
		if (solidMask.isSolidCellIndex(centerIndex)) {
			return 0.0;
		}
		double dx = gridSpec.cellSizeMeters();
		Vec3 center = velocityAt(x, y, z);
		double east = x + 1 < gridSpec.cellCountX()
				? openFaceVelocity(solidMask, x + 1, y, z, 0.5 * (center.x() + velocityAt(x + 1, y, z).x()))
				: center.x();
		double west = x > 0
				? openFaceVelocity(solidMask, x - 1, y, z, 0.5 * (velocityAt(x - 1, y, z).x() + center.x()))
				: center.x();
		double up = y + 1 < gridSpec.cellCountY()
				? openFaceVelocity(solidMask, x, y + 1, z, 0.5 * (center.y() + velocityAt(x, y + 1, z).y()))
				: center.y();
		double down = y > 0
				? openFaceVelocity(solidMask, x, y - 1, z, 0.5 * (velocityAt(x, y - 1, z).y() + center.y()))
				: center.y();
		double south = z + 1 < gridSpec.cellCountZ()
				? openFaceVelocity(solidMask, x, y, z + 1, 0.5 * (center.z() + velocityAt(x, y, z + 1).z()))
				: center.z();
		double north = z > 0
				? openFaceVelocity(solidMask, x, y, z - 1, 0.5 * (velocityAt(x, y, z - 1).z() + center.z()))
				: center.z();
		return (east - west + up - down + south - north) / dx;
	}

	private double[] pressurePotential(double[] divergence, int iterationCount) {
		return pressurePotential(divergence, iterationCount, VoxelSolidMask.open(gridSpec));
	}

	private double[] pressurePotential(double[] divergence, int iterationCount, VoxelSolidMask solidMask) {
		double meanDivergence = mean(divergence, solidMask);
		double dxSquared = gridSpec.cellSizeMeters() * gridSpec.cellSizeMeters();
		double[] previous = new double[divergence.length];
		double[] next = new double[divergence.length];
		for (int iteration = 0; iteration < iterationCount; iteration++) {
			for (int y = 0; y < gridSpec.cellCountY(); y++) {
				for (int z = 0; z < gridSpec.cellCountZ(); z++) {
					for (int x = 0; x < gridSpec.cellCountX(); x++) {
						int index = linearIndex(x, y, z);
						if (solidMask.isSolidCellIndex(index)) {
							next[index] = 0.0;
							continue;
						}
						int neighborCount = 0;
						double neighborSum = 0.0;
						if (openNeighbor(solidMask, x - 1, y, z)) {
							neighborSum += previous[linearIndex(x - 1, y, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x + 1, y, z)) {
							neighborSum += previous[linearIndex(x + 1, y, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y - 1, z)) {
							neighborSum += previous[linearIndex(x, y - 1, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y + 1, z)) {
							neighborSum += previous[linearIndex(x, y + 1, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y, z - 1)) {
							neighborSum += previous[linearIndex(x, y, z - 1)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y, z + 1)) {
							neighborSum += previous[linearIndex(x, y, z + 1)];
							neighborCount++;
						}
						next[index] = neighborCount == 0
								? 0.0
								: (neighborSum - (divergence[index] - meanDivergence) * dxSquared) / neighborCount;
					}
				}
			}
			subtractMean(next, solidMask);
			double[] swap = previous;
			previous = next;
			next = swap;
		}
		return previous;
	}

	private Vec3 projectedVelocityAt(double[] pressurePotential, int x, int y, int z) {
		return projectedVelocityAt(pressurePotential, VoxelSolidMask.open(gridSpec), x, y, z);
	}

	private Vec3 projectedVelocityAt(double[] pressurePotential, VoxelSolidMask solidMask, int x, int y, int z) {
		Vec3 center = velocityAt(x, y, z);
		double dx = gridSpec.cellSizeMeters();
		double lowX = center.x();
		double highX = center.x();
		if (solidNeighbor(solidMask, x - 1, y, z)) {
			lowX = 0.0;
		} else if (openNeighbor(solidMask, x - 1, y, z)) {
			lowX = 0.5 * (velocityAt(x - 1, y, z).x() + center.x())
					- (pressurePotential[linearIndex(x, y, z)]
					- pressurePotential[linearIndex(x - 1, y, z)]) / dx;
		}
		if (solidNeighbor(solidMask, x + 1, y, z)) {
			highX = 0.0;
		} else if (openNeighbor(solidMask, x + 1, y, z)) {
			highX = 0.5 * (center.x() + velocityAt(x + 1, y, z).x())
					- (pressurePotential[linearIndex(x + 1, y, z)]
					- pressurePotential[linearIndex(x, y, z)]) / dx;
		}
		double lowY = center.y();
		double highY = center.y();
		if (solidNeighbor(solidMask, x, y - 1, z)) {
			lowY = 0.0;
		} else if (openNeighbor(solidMask, x, y - 1, z)) {
			lowY = 0.5 * (velocityAt(x, y - 1, z).y() + center.y())
					- (pressurePotential[linearIndex(x, y, z)]
					- pressurePotential[linearIndex(x, y - 1, z)]) / dx;
		}
		if (solidNeighbor(solidMask, x, y + 1, z)) {
			highY = 0.0;
		} else if (openNeighbor(solidMask, x, y + 1, z)) {
			highY = 0.5 * (center.y() + velocityAt(x, y + 1, z).y())
					- (pressurePotential[linearIndex(x, y + 1, z)]
					- pressurePotential[linearIndex(x, y, z)]) / dx;
		}
		double lowZ = center.z();
		double highZ = center.z();
		if (solidNeighbor(solidMask, x, y, z - 1)) {
			lowZ = 0.0;
		} else if (openNeighbor(solidMask, x, y, z - 1)) {
			lowZ = 0.5 * (velocityAt(x, y, z - 1).z() + center.z())
					- (pressurePotential[linearIndex(x, y, z)]
					- pressurePotential[linearIndex(x, y, z - 1)]) / dx;
		}
		if (solidNeighbor(solidMask, x, y, z + 1)) {
			highZ = 0.0;
		} else if (openNeighbor(solidMask, x, y, z + 1)) {
			highZ = 0.5 * (center.z() + velocityAt(x, y, z + 1).z())
					- (pressurePotential[linearIndex(x, y, z + 1)]
					- pressurePotential[linearIndex(x, y, z)]) / dx;
		}
		return new Vec3(
				0.5 * (lowX + highX),
				0.5 * (lowY + highY),
				0.5 * (lowZ + highZ)
		);
	}

	private Vec3 sampleVelocityClamped(Vec3 pointWorldMeters) {
		double fx = fractionalCellIndex(pointWorldMeters.x(), gridSpec.originWorldMeters().x(), gridSpec.cellCountX());
		double fy = fractionalCellIndex(pointWorldMeters.y(), gridSpec.originWorldMeters().y(), gridSpec.cellCountY());
		double fz = fractionalCellIndex(pointWorldMeters.z(), gridSpec.originWorldMeters().z(), gridSpec.cellCountZ());
		int x0 = (int) Math.floor(fx);
		int y0 = (int) Math.floor(fy);
		int z0 = (int) Math.floor(fz);
		int x1 = Math.min(x0 + 1, gridSpec.cellCountX() - 1);
		int y1 = Math.min(y0 + 1, gridSpec.cellCountY() - 1);
		int z1 = Math.min(z0 + 1, gridSpec.cellCountZ() - 1);
		double tx = fx - x0;
		double ty = fy - y0;
		double tz = fz - z0;
		Vec3 x00 = lerp(velocityAt(x0, y0, z0), velocityAt(x1, y0, z0), tx);
		Vec3 x10 = lerp(velocityAt(x0, y1, z0), velocityAt(x1, y1, z0), tx);
		Vec3 x01 = lerp(velocityAt(x0, y0, z1), velocityAt(x1, y0, z1), tx);
		Vec3 x11 = lerp(velocityAt(x0, y1, z1), velocityAt(x1, y1, z1), tx);
		Vec3 y0Blend = lerp(x00, x10, ty);
		Vec3 y1Blend = lerp(x01, x11, ty);
		return lerp(y0Blend, y1Blend, tz);
	}

	private double fractionalCellIndex(double worldCoordinate, double originCoordinate, int cellCount) {
		double raw = (worldCoordinate - originCoordinate) / gridSpec.cellSizeMeters() - 0.5;
		return MathUtil.clamp(raw, 0.0, Math.max(0, cellCount - 1));
	}

	private double openFaceVelocity(VoxelSolidMask solidMask, int x, int y, int z, double openFaceVelocity) {
		return solidMask.isSolidCellIndex(linearIndex(x, y, z)) ? 0.0 : openFaceVelocity;
	}

	private boolean openNeighbor(VoxelSolidMask solidMask, int x, int y, int z) {
		return x >= 0 && x < gridSpec.cellCountX()
				&& y >= 0 && y < gridSpec.cellCountY()
				&& z >= 0 && z < gridSpec.cellCountZ()
				&& !solidMask.isSolidCellIndex(linearIndex(x, y, z));
	}

	private boolean solidNeighbor(VoxelSolidMask solidMask, int x, int y, int z) {
		return x >= 0 && x < gridSpec.cellCountX()
				&& y >= 0 && y < gridSpec.cellCountY()
				&& z >= 0 && z < gridSpec.cellCountZ()
				&& solidMask.isSolidCellIndex(linearIndex(x, y, z));
	}

	private void validateSolidMask(VoxelSolidMask solidMask) {
		if (solidMask == null) {
			throw new IllegalArgumentException("solidMask must not be null.");
		}
		if (!gridSpec.equals(solidMask.gridSpec())) {
			throw new IllegalArgumentException("solidMask grid must match this flow state.");
		}
	}

	private static Vec3 lerp(Vec3 first, Vec3 second, double t) {
		return first.multiply(1.0 - t).add(second.multiply(t));
	}

	private void accumulateDiffusiveExchange(
			ArrayList<Vec3> deltas,
			int firstIndex,
			int secondIndex,
			double diffusionNumber
	) {
		accumulateDiffusiveExchange(
				deltas,
				firstIndex,
				secondIndex,
				diffusionNumber,
				VoxelSolidMask.open(gridSpec));
	}

	private void accumulateDiffusiveExchange(
			ArrayList<Vec3> deltas,
			int firstIndex,
			int secondIndex,
			double diffusionNumber,
			VoxelSolidMask solidMask
	) {
		if (diffusionNumber <= EPSILON) {
			return;
		}
		if (solidMask.isSolidCellIndex(firstIndex) || solidMask.isSolidCellIndex(secondIndex)) {
			return;
		}
		Vec3 exchange = velocitiesWorldMetersPerSecond.get(secondIndex)
				.subtract(velocitiesWorldMetersPerSecond.get(firstIndex))
				.multiply(diffusionNumber);
		deltas.set(firstIndex, deltas.get(firstIndex).add(exchange));
		deltas.set(secondIndex, deltas.get(secondIndex).subtract(exchange));
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	private static double finiteNonnegative(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 0.0;
	}

	private static double mean(double[] values) {
		if (values.length == 0) {
			return 0.0;
		}
		double sum = 0.0;
		for (double value : values) {
			sum += value;
		}
		return sum / values.length;
	}

	private static double mean(double[] values, VoxelSolidMask solidMask) {
		if (values.length == 0) {
			return 0.0;
		}
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < values.length; i++) {
			if (solidMask.isSolidCellIndex(i)) {
				continue;
			}
			sum += values[i];
			count++;
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private static void subtractMean(double[] values) {
		double mean = mean(values);
		for (int i = 0; i < values.length; i++) {
			values[i] -= mean;
		}
	}

	private static void subtractMean(double[] values, VoxelSolidMask solidMask) {
		double mean = mean(values, solidMask);
		for (int i = 0; i < values.length; i++) {
			if (solidMask.isSolidCellIndex(i)) {
				values[i] = 0.0;
			} else {
				values[i] -= mean;
			}
		}
	}
}
