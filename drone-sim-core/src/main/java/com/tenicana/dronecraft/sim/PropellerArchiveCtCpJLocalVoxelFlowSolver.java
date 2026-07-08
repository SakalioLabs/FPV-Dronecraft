package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLocalVoxelFlowSolver {
	private static final double EPSILON = 1.0e-12;

	private PropellerArchiveCtCpJLocalVoxelFlowSolver() {
	}

	public record SolverConfig(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double sourceThicknessMeters,
			double kinematicViscositySquareMetersPerSecond,
			int stepCount
	) {
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
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusionStep
	) {
		public SolverIteration {
			if (stepIndex < 0) {
				throw new IllegalArgumentException("stepIndex must be nonnegative.");
			}
			if (sourceAdvance == null) {
				throw new IllegalArgumentException("sourceAdvance must not be null.");
			}
			if (diffusionStep == null) {
				throw new IllegalArgumentException("diffusionStep must not be null.");
			}
			if (!sourceAdvance.nextState().equals(diffusionStep.previousState())) {
				throw new IllegalArgumentException("diffusion must start from the source-advanced state.");
			}
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateBeforeStep() {
			return sourceAdvance.previousState();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateAfterSource() {
			return sourceAdvance.nextState();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState stateAfterDiffusion() {
			return diffusionStep.nextState();
		}
	}

	public record SolverRun(
			PropellerArchiveCtCpJLocalVoxelFlowState initialState,
			PropellerArchiveCtCpJLocalVoxelFlowState finalState,
			SolverConfig config,
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
			if (!initialState.gridSpec().equals(finalState.gridSpec())) {
				throw new IllegalArgumentException("initial and final states must share a voxel grid.");
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
		if (initialState == null) {
			throw new IllegalArgumentException("initialState must not be null.");
		}
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (!initialState.gridSpec().equals(sourceGridSample.gridSpec())) {
			throw new IllegalArgumentException("sourceGridSample grid must match initialState grid.");
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
			PropellerArchiveCtCpJLocalVoxelFlowState.VelocityDiffusionStep diffusion =
					sourceAdvance.nextState().diffuseVelocity(
							config.airDensityKgPerCubicMeter(),
							config.kinematicViscositySquareMetersPerSecond(),
							config.timeStepSeconds()
					);
			iterations.add(new SolverIteration(step, sourceAdvance, diffusion));
			state = diffusion.nextState();
		}
		return new SolverRun(initialState, state, config, iterations);
	}
}
