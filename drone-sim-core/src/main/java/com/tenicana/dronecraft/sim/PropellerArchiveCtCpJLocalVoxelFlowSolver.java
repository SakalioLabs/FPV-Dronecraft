package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLocalVoxelFlowSolver {
	private static final double EPSILON = 1.0e-12;
	public static final double DEFAULT_MAX_ADVECTION_COURANT_NUMBER = 1.0;
	public static final int DEFAULT_PRESSURE_PROJECTION_ITERATIONS = 24;

	private PropellerArchiveCtCpJLocalVoxelFlowSolver() {
	}

	public record SolverConfig(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double sourceThicknessMeters,
			double kinematicViscositySquareMetersPerSecond,
			int stepCount,
			double maxAdvectionCourantNumber,
			int pressureProjectionIterations
	) {
		public SolverConfig(
				double airDensityKgPerCubicMeter,
				double timeStepSeconds,
				double sourceThicknessMeters,
				double kinematicViscositySquareMetersPerSecond,
				int stepCount
		) {
			this(
					airDensityKgPerCubicMeter,
					timeStepSeconds,
					sourceThicknessMeters,
					kinematicViscositySquareMetersPerSecond,
					stepCount,
					DEFAULT_MAX_ADVECTION_COURANT_NUMBER,
					DEFAULT_PRESSURE_PROJECTION_ITERATIONS
			);
		}

		public SolverConfig(
				double airDensityKgPerCubicMeter,
				double timeStepSeconds,
				double sourceThicknessMeters,
				double kinematicViscositySquareMetersPerSecond,
				int stepCount,
				double maxAdvectionCourantNumber
		) {
			this(
					airDensityKgPerCubicMeter,
					timeStepSeconds,
					sourceThicknessMeters,
					kinematicViscositySquareMetersPerSecond,
					stepCount,
					maxAdvectionCourantNumber,
					DEFAULT_PRESSURE_PROJECTION_ITERATIONS
			);
		}

		public SolverConfig {
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= EPSILON) {
				throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
			}
			if (!Double.isFinite(kinematicViscositySquareMetersPerSecond)
					|| kinematicViscositySquareMetersPerSecond < 0.0) {
				throw new IllegalArgumentException(
						"kinematicViscositySquareMetersPerSecond must be finite and nonnegative.");
			}
			if (stepCount < 0) {
				throw new IllegalArgumentException("stepCount must be nonnegative.");
			}
			if (!Double.isFinite(maxAdvectionCourantNumber) || maxAdvectionCourantNumber <= EPSILON) {
				throw new IllegalArgumentException("maxAdvectionCourantNumber must be finite and positive.");
			}
			if (pressureProjectionIterations < 0) {
				throw new IllegalArgumentException("pressureProjectionIterations must be nonnegative.");
			}
		}

		public double diffusionNumber(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec
		) {
			if (gridSpec == null) {
				throw new IllegalArgumentException("gridSpec must not be null.");
			}
			return kinematicViscositySquareMetersPerSecond
					* timeStepSeconds
					/ (gridSpec.cellSizeMeters() * gridSpec.cellSizeMeters());
		}
	}

	public record SolverIteration(
			int stepIndex,
			PropellerArchiveCtCpJLocalVoxelFlowState.VoxelFlowAdvance sourceAdvance,
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionRun advectionRun,
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusionStep,
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityProjectionStep projectionStep,
			PropellerArchiveCtCpJLocalVoxelFlowState.SolidBoundaryStep solidBoundaryStep
	) {
		public SolverIteration {
			if (stepIndex < 0) {
				throw new IllegalArgumentException("stepIndex must be nonnegative.");
			}
			if (sourceAdvance == null) {
				throw new IllegalArgumentException("sourceAdvance must not be null.");
			}
			if (advectionRun == null) {
				throw new IllegalArgumentException("advectionRun must not be null.");
			}
			if (diffusionStep == null) {
				throw new IllegalArgumentException("diffusionStep must not be null.");
			}
			if (projectionStep == null) {
				throw new IllegalArgumentException("projectionStep must not be null.");
			}
			if (solidBoundaryStep == null) {
				throw new IllegalArgumentException("solidBoundaryStep must not be null.");
			}
			if (!sourceAdvance.nextState().equals(advectionRun.previousState())) {
				throw new IllegalArgumentException("advection must start from the source-advanced state.");
			}
			if (!advectionRun.nextState().equals(diffusionStep.previousState())) {
				throw new IllegalArgumentException("diffusion must start from the advected state.");
			}
			if (!diffusionStep.nextState().equals(projectionStep.previousState())) {
				throw new IllegalArgumentException("projection must start from the diffused state.");
			}
			if (!projectionStep.nextState().equals(solidBoundaryStep.previousState())) {
				throw new IllegalArgumentException("solid boundary must start from the projected state.");
			}
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateBeforeStep() {
			return sourceAdvance.previousState();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateAfterSource() {
			return sourceAdvance.nextState();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateAfterAdvection() {
			return advectionRun.nextState();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateAfterDiffusion() {
			return diffusionStep.nextState();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateAfterProjection() {
			return projectionStep.nextState();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateAfterSolidBoundary() {
			return solidBoundaryStep.nextState();
		}
	}

	public record SolverRun(
			PropellerArchiveCtCpJLocalVoxelFlowState initialState,
			PropellerArchiveCtCpJLocalVoxelFlowState finalState,
			SolverConfig config,
			PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask solidMask,
			List<SolverIteration> iterations
	) {
		public SolverRun {
			if (initialState == null) {
				throw new IllegalArgumentException("initialState must not be null.");
			}
			if (finalState == null) {
				throw new IllegalArgumentException("finalState must not be null.");
			}
			if (config == null) {
				throw new IllegalArgumentException("config must not be null.");
			}
			if (solidMask == null) {
				throw new IllegalArgumentException("solidMask must not be null.");
			}
			if (!initialState.gridSpec().equals(finalState.gridSpec())) {
				throw new IllegalArgumentException("initial and final states must share a voxel grid.");
			}
			if (!initialState.gridSpec().equals(solidMask.gridSpec())) {
				throw new IllegalArgumentException("solidMask grid must match solver states.");
			}
			iterations = List.copyOf(iterations == null ? List.of() : iterations);
		}

		public int completedStepCount() {
			return iterations.size();
		}

		public Vec3 totalSourceImpulseWorldNewtonSeconds() {
			Vec3 sum = Vec3.ZERO;
			for (SolverIteration iteration : iterations) {
				sum = sum.add(iteration.sourceAdvance().totalSourceImpulseWorldNewtonSeconds());
			}
			return sum;
		}

		public Vec3 totalThroughFlowImpulseWorldNewtonSeconds() {
			Vec3 sum = Vec3.ZERO;
			for (SolverIteration iteration : iterations) {
				sum = sum.add(iteration.sourceAdvance().totalThroughFlowImpulseWorldNewtonSeconds());
			}
			return sum;
		}

		public double totalSourceMassKilograms() {
			double mass = 0.0;
			for (SolverIteration iteration : iterations) {
				mass += iteration.sourceAdvance().totalSourceMassFlowRateKilogramsPerSecond()
						* config.timeStepSeconds();
			}
			return mass;
		}

		public double maxResidenceAlpha() {
			double max = 0.0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max, iteration.sourceAdvance().maxResidenceAlpha());
			}
			return max;
		}

		public double maxDiffusionNumber() {
			double max = 0.0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max, iteration.diffusionStep().diffusionNumber());
			}
			return max;
		}

		public double maxAdvectionCourantNumber() {
			double max = 0.0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max, iteration.advectionRun().maxCourantNumber());
			}
			return max;
		}

		public int maxAdvectionSubstepCount() {
			int max = 0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max, iteration.advectionRun().completedSubstepCount());
			}
			return max;
		}

		public Vec3 totalAdvectionMomentumResidualWorldNewtonSeconds() {
			Vec3 sum = Vec3.ZERO;
			for (SolverIteration iteration : iterations) {
				sum = sum.add(iteration.advectionRun().momentumResidualWorldNewtonSeconds());
			}
			return sum;
		}

		public Vec3 totalProjectionMomentumResidualWorldNewtonSeconds() {
			Vec3 sum = Vec3.ZERO;
			for (SolverIteration iteration : iterations) {
				sum = sum.add(iteration.projectionStep().momentumResidualWorldNewtonSeconds());
			}
			return sum;
		}

		public Vec3 totalSolidBoundaryMomentumResidualWorldNewtonSeconds() {
			Vec3 sum = Vec3.ZERO;
			for (SolverIteration iteration : iterations) {
				sum = sum.add(iteration.solidBoundaryStep().momentumResidualWorldNewtonSeconds());
			}
			return sum;
		}

		public int maxSolidCellCount() {
			int max = 0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max, iteration.solidBoundaryStep().solidCellCount());
			}
			return max;
		}

		public int maxSolidClampedCellCount() {
			int max = 0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max, iteration.solidBoundaryStep().clampedCellCount());
			}
			return max;
		}

		public double maxDivergenceBeforeProjectionPerSecond() {
			double max = 0.0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max,
						iteration.projectionStep().divergenceBefore().maxAbsDivergencePerSecond());
			}
			return max;
		}

		public double maxDivergenceAfterProjectionPerSecond() {
			double max = 0.0;
			for (SolverIteration iteration : iterations) {
				max = Math.max(max,
						iteration.projectionStep().divergenceAfter().maxAbsDivergencePerSecond());
			}
			return max;
		}

		public double initialKineticEnergyJoules() {
			return initialState.totalKineticEnergyJoules(config.airDensityKgPerCubicMeter());
		}

		public double finalKineticEnergyJoules() {
			return finalState.totalKineticEnergyJoules(config.airDensityKgPerCubicMeter());
		}

		public Vec3 finalMomentumWorldNewtonSeconds() {
			return finalState.totalMomentumWorldNewtonSeconds(config.airDensityKgPerCubicMeter());
		}

		public double finalMaxSpeedMetersPerSecond() {
			return finalState.maxSpeedMetersPerSecond();
		}
	}

	public static SolverRun run(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			SolverConfig config
	) {
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		return run(
				PropellerArchiveCtCpJLocalVoxelFlowState.calm(sourceGridSample.gridSpec()),
				sourceGridSample,
				config
		);
	}

	public static SolverRun run(
			PropellerArchiveCtCpJLocalVoxelFlowState initialState,
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			SolverConfig config
	) {
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		return run(
				initialState,
				sourceGridSample,
				config,
				PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.open(sourceGridSample.gridSpec())
		);
	}

	public static SolverRun run(
			PropellerArchiveCtCpJLocalVoxelFlowState initialState,
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			SolverConfig config,
			PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask solidMask
	) {
		if (initialState == null) {
			throw new IllegalArgumentException("initialState must not be null.");
		}
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (solidMask == null) {
			throw new IllegalArgumentException("solidMask must not be null.");
		}
		if (!initialState.gridSpec().equals(sourceGridSample.gridSpec())) {
			throw new IllegalArgumentException("sourceGridSample grid must match initialState grid.");
		}
		if (!initialState.gridSpec().equals(solidMask.gridSpec())) {
			throw new IllegalArgumentException("solidMask grid must match initialState grid.");
		}
		ArrayList<SolverIteration> iterations = new ArrayList<>(config.stepCount());
		PropellerArchiveCtCpJLocalVoxelFlowState state = initialState;
		for (int step = 0; step < config.stepCount(); step++) {
			PropellerArchiveCtCpJLocalVoxelFlowState.VoxelFlowAdvance sourceAdvance =
					state.advanceWithSource(
							sourceGridSample,
							config.airDensityKgPerCubicMeter(),
							config.timeStepSeconds(),
							config.sourceThicknessMeters()
					);
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityAdvectionRun advection =
					sourceAdvance.nextState().advectVelocityWithCourantLimit(
							config.airDensityKgPerCubicMeter(),
							config.timeStepSeconds(),
							config.maxAdvectionCourantNumber()
					);
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
					advection.nextState().diffuseVelocity(
							config.airDensityKgPerCubicMeter(),
							config.kinematicViscositySquareMetersPerSecond(),
							config.timeStepSeconds(),
							solidMask
					);
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityProjectionStep projection =
					diffusion.nextState().projectVelocityDivergence(
							config.airDensityKgPerCubicMeter(),
							config.pressureProjectionIterations(),
							solidMask
					);
			PropellerArchiveCtCpJLocalVoxelFlowState.SolidBoundaryStep solidBoundary =
					projection.nextState().applySolidMask(
							solidMask,
							config.airDensityKgPerCubicMeter()
					);
			iterations.add(new SolverIteration(step, sourceAdvance, advection, diffusion, projection, solidBoundary));
			state = solidBoundary.nextState();
		}
		return new SolverRun(initialState, state, config, solidMask, iterations);
	}
}
