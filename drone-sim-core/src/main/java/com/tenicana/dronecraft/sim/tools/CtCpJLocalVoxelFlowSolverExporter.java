package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJActuatorDiskSourceField;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLocalVoxelFlowSolver;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLocalVoxelFlowState;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CtCpJLocalVoxelFlowSolverExporter {
	public static final double DEFAULT_TIME_STEP_SECONDS = 0.005;
	public static final double DEFAULT_KINEMATIC_VISCOSITY_SQUARE_METERS_PER_SECOND = 1.5e-5;
	public static final double DEFAULT_DOWNSTREAM_WAKE_LENGTH_METERS = 0.60;
	public static final int DEFAULT_STEP_COUNT = 8;

	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"solver_row_kind",
			"step_index",
			"completed_steps",
			"grid_status",
			"lookup_statuses",
			"source_count",
			"applied_source_count",
			"air_density_kg_m3",
			"source_thickness_m",
			"cell_size_m",
			"padding_cells",
			"downstream_wake_length_m",
			"subcell_samples_per_axis",
			"time_step_s",
			"configured_step_count",
			"kinematic_viscosity_m2_s",
			"diffusion_number",
			"max_advection_courant_number",
			"advection_courant_number",
			"advection_substep_count",
			"pressure_projection_iterations",
			"grid_cell_count",
			"active_cell_count",
			"solid_cell_count",
			"solid_clamped_cell_count",
			"target_body_force_world_x_n",
			"target_body_force_world_y_n",
			"target_body_force_world_z_n",
			"source_momentum_rate_world_x_n",
			"source_momentum_rate_world_y_n",
			"source_momentum_rate_world_z_n",
			"source_impulse_world_x_ns",
			"source_impulse_world_y_ns",
			"source_impulse_world_z_ns",
			"cumulative_source_impulse_world_x_ns",
			"cumulative_source_impulse_world_y_ns",
			"cumulative_source_impulse_world_z_ns",
			"through_flow_momentum_rate_world_x_n",
			"through_flow_momentum_rate_world_y_n",
			"through_flow_momentum_rate_world_z_n",
			"through_flow_impulse_world_x_ns",
			"through_flow_impulse_world_y_ns",
			"through_flow_impulse_world_z_ns",
			"cumulative_through_flow_impulse_world_x_ns",
			"cumulative_through_flow_impulse_world_y_ns",
			"cumulative_through_flow_impulse_world_z_ns",
			"cumulative_advection_momentum_residual_world_x_ns",
			"cumulative_advection_momentum_residual_world_y_ns",
			"cumulative_advection_momentum_residual_world_z_ns",
			"cumulative_projection_momentum_residual_world_x_ns",
			"cumulative_projection_momentum_residual_world_y_ns",
			"cumulative_projection_momentum_residual_world_z_ns",
			"cumulative_solid_boundary_momentum_residual_world_x_ns",
			"cumulative_solid_boundary_momentum_residual_world_y_ns",
			"cumulative_solid_boundary_momentum_residual_world_z_ns",
			"source_mass_flow_kg_s",
			"cumulative_source_mass_kg",
			"max_residence_alpha",
			"mean_active_wake_residual_after_residence_mps",
			"max_divergence_before_projection_s",
			"rms_divergence_before_projection_s",
			"mean_divergence_before_projection_s",
			"max_divergence_after_projection_s",
			"rms_divergence_after_projection_s",
			"mean_divergence_after_projection_s",
			"kinetic_energy_before_source_j",
			"kinetic_energy_after_source_j",
			"kinetic_energy_after_advection_j",
			"kinetic_energy_advection_delta_j",
			"kinetic_energy_after_diffusion_j",
			"kinetic_energy_diffusion_delta_j",
			"kinetic_energy_after_projection_j",
			"kinetic_energy_projection_delta_j",
			"kinetic_energy_after_solid_boundary_j",
			"kinetic_energy_solid_boundary_delta_j",
			"max_speed_after_source_mps",
			"max_speed_after_advection_mps",
			"max_speed_after_diffusion_mps",
			"max_speed_after_projection_mps",
			"max_speed_after_solid_boundary_mps",
			"advection_momentum_before_world_x_ns",
			"advection_momentum_before_world_y_ns",
			"advection_momentum_before_world_z_ns",
			"advection_momentum_after_world_x_ns",
			"advection_momentum_after_world_y_ns",
			"advection_momentum_after_world_z_ns",
			"advection_momentum_residual_world_x_ns",
			"advection_momentum_residual_world_y_ns",
			"advection_momentum_residual_world_z_ns",
			"momentum_before_diffusion_world_x_ns",
			"momentum_before_diffusion_world_y_ns",
			"momentum_before_diffusion_world_z_ns",
			"momentum_after_diffusion_world_x_ns",
			"momentum_after_diffusion_world_y_ns",
			"momentum_after_diffusion_world_z_ns",
			"diffusion_momentum_residual_world_x_ns",
			"diffusion_momentum_residual_world_y_ns",
			"diffusion_momentum_residual_world_z_ns",
			"projection_momentum_before_world_x_ns",
			"projection_momentum_before_world_y_ns",
			"projection_momentum_before_world_z_ns",
			"projection_momentum_after_world_x_ns",
			"projection_momentum_after_world_y_ns",
			"projection_momentum_after_world_z_ns",
			"projection_momentum_residual_world_x_ns",
			"projection_momentum_residual_world_y_ns",
			"projection_momentum_residual_world_z_ns",
			"solid_boundary_momentum_before_world_x_ns",
			"solid_boundary_momentum_before_world_y_ns",
			"solid_boundary_momentum_before_world_z_ns",
			"solid_boundary_momentum_after_world_x_ns",
			"solid_boundary_momentum_after_world_y_ns",
			"solid_boundary_momentum_after_world_z_ns",
			"solid_boundary_momentum_residual_world_x_ns",
			"solid_boundary_momentum_residual_world_y_ns",
			"solid_boundary_momentum_residual_world_z_ns",
			"final_momentum_world_x_ns",
			"final_momentum_world_y_ns",
			"final_momentum_world_z_ns"
	);

	private CtCpJLocalVoxelFlowSolverExporter() {
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-local-voxel-flow", presetName + "-solver-summary.csv");
		double airDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double sourceThickness = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
		double cellSizeMeters = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_CELL_SIZE_METERS;
		int paddingCells = args.length >= 6 && !args[5].isBlank()
				? Integer.parseInt(args[5])
				: CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_PADDING_CELLS;
		int subcellSamplesPerAxis = args.length >= 7 && !args[6].isBlank()
				? Integer.parseInt(args[6])
				: CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_SUBCELL_SAMPLES_PER_AXIS;
		double timeStepSeconds = args.length >= 8 && !args[7].isBlank()
				? Double.parseDouble(args[7])
				: DEFAULT_TIME_STEP_SECONDS;
		double kinematicViscosity = args.length >= 9 && !args[8].isBlank()
				? Double.parseDouble(args[8])
				: DEFAULT_KINEMATIC_VISCOSITY_SQUARE_METERS_PER_SECOND;
		int stepCount = args.length >= 10 && !args[9].isBlank()
				? Integer.parseInt(args[9])
				: DEFAULT_STEP_COUNT;
		double ambientTemperatureCelsius = args.length >= 11 && !args[10].isBlank()
				? Double.parseDouble(args[10])
				: 25.0;
		double ambientHumidity = args.length >= 12 && !args[11].isBlank()
				? Double.parseDouble(args[11])
				: 0.0;
		double maxAdvectionCourantNumber = args.length >= 13 && !args[12].isBlank()
				? Double.parseDouble(args[12])
				: PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_MAX_ADVECTION_COURANT_NUMBER;
		int pressureProjectionIterations = args.length >= 14 && !args[13].isBlank()
				? Integer.parseInt(args[13])
				: PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_PRESSURE_PROJECTION_ITERATIONS;
		double downstreamWakeLength = args.length >= 15 && !args[14].isBlank()
				? Double.parseDouble(args[14])
				: DEFAULT_DOWNSTREAM_WAKE_LENGTH_METERS;
		write(
				presetName,
				output,
				airDensity,
				sourceThickness,
				cellSizeMeters,
				paddingCells,
				subcellSamplesPerAxis,
				timeStepSeconds,
				kinematicViscosity,
				stepCount,
				ambientTemperatureCelsius,
				ambientHumidity,
				maxAdvectionCourantNumber,
				pressureProjectionIterations,
				downstreamWakeLength
		);
	}

	public static void write(String presetName, Path output, double airDensityKgPerCubicMeter) throws IOException {
		write(
				presetName,
				output,
				airDensityKgPerCubicMeter,
				CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS,
				CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_CELL_SIZE_METERS,
				CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_PADDING_CELLS,
				CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_SUBCELL_SAMPLES_PER_AXIS,
				DEFAULT_TIME_STEP_SECONDS,
				DEFAULT_KINEMATIC_VISCOSITY_SQUARE_METERS_PER_SECOND,
				DEFAULT_STEP_COUNT,
				25.0,
				0.0,
				PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_MAX_ADVECTION_COURANT_NUMBER,
				PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_PRESSURE_PROJECTION_ITERATIONS,
				DEFAULT_DOWNSTREAM_WAKE_LENGTH_METERS
		);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double cellSizeMeters,
			int paddingCells,
			int subcellSamplesPerAxis,
			double timeStepSeconds,
			double kinematicViscositySquareMetersPerSecond,
			int stepCount,
			double ambientTemperatureCelsius,
			double ambientHumidity,
			double maxAdvectionCourantNumber,
			int pressureProjectionIterations,
			double downstreamWakeLengthMeters
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				cellSizeMeters,
				paddingCells,
				subcellSamplesPerAxis,
				timeStepSeconds,
				kinematicViscositySquareMetersPerSecond,
				stepCount,
				ambientTemperatureCelsius,
				ambientHumidity,
				maxAdvectionCourantNumber,
				pressureProjectionIterations,
				downstreamWakeLengthMeters
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(String presetName, double airDensityKgPerCubicMeter) {
		return csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS,
				CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_CELL_SIZE_METERS,
				CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_PADDING_CELLS,
				CtCpJActuatorDiskVoxelSourceFieldExporter.DEFAULT_SUBCELL_SAMPLES_PER_AXIS,
				DEFAULT_TIME_STEP_SECONDS,
				DEFAULT_KINEMATIC_VISCOSITY_SQUARE_METERS_PER_SECOND,
				DEFAULT_STEP_COUNT,
				25.0,
				0.0,
				PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_MAX_ADVECTION_COURANT_NUMBER,
				PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_PRESSURE_PROJECTION_ITERATIONS,
				DEFAULT_DOWNSTREAM_WAKE_LENGTH_METERS
		);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double cellSizeMeters,
			int paddingCells,
			int subcellSamplesPerAxis,
			double timeStepSeconds,
			double kinematicViscositySquareMetersPerSecond,
			int stepCount,
			double ambientTemperatureCelsius,
			double ambientHumidity,
			double maxAdvectionCourantNumber,
			int pressureProjectionIterations,
			double downstreamWakeLengthMeters
	) {
		List<Map<String, String>> voxelRows = parseCsv(String.join("\n",
				CtCpJActuatorDiskVoxelSourceFieldExporter.csvLines(
						presetName,
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						cellSizeMeters,
						paddingCells,
						subcellSamplesPerAxis,
						ambientTemperatureCelsius,
						ambientHumidity,
						downstreamWakeLengthMeters
				)));
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config =
				new PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig(
						airDensityKgPerCubicMeter,
						timeStepSeconds,
						sourceThicknessMeters,
						kinematicViscositySquareMetersPerSecond,
						stepCount,
						maxAdvectionCourantNumber,
						pressureProjectionIterations
				);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (Map.Entry<SourceGroupKey, List<Map<String, String>>> entry : sourceGroups(voxelRows).entrySet()) {
			lines.addAll(csvLinesForGroup(entry.getKey(), entry.getValue(),
					config, paddingCells, downstreamWakeLengthMeters));
		}
		return List.copyOf(lines);
	}

	private static List<String> csvLinesForGroup(
			SourceGroupKey key,
			List<Map<String, String>> rows,
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config,
			int paddingCells,
			double downstreamWakeLengthMeters
	) {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample =
				sourceGridSample(rows);
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run =
				PropellerArchiveCtCpJLocalVoxelFlowSolver.run(sourceGridSample, config);
		Map<String, String> first = rows.get(0);
		GroupMetadata metadata = metadata(key, first, sourceGridSample, config, paddingCells, downstreamWakeLengthMeters);
		List<String> lines = new ArrayList<>(run.completedStepCount() + 1);
		lines.add(csvLine(metadata, run, null));
		for (PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverIteration iteration : run.iterations()) {
			lines.add(csvLine(metadata, run, iteration));
		}
		return lines;
	}

	private static String csvLine(
			GroupMetadata metadata,
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverIteration iteration
	) {
		boolean initial = iteration == null;
		int completedSteps = initial ? 0 : iteration.stepIndex() + 1;
		Vec3 zero = Vec3.ZERO;
		Vec3 sourceMomentumRate = initial ? zero : iteration.sourceAdvance().totalSourceMomentumRateWorldNewtons();
		Vec3 sourceImpulse = initial ? zero : iteration.sourceAdvance().totalSourceImpulseWorldNewtonSeconds();
		Vec3 cumulativeSourceImpulse = cumulativeSourceImpulse(run, completedSteps);
		Vec3 throughFlowMomentumRate = initial ? zero : iteration.sourceAdvance().totalThroughFlowMomentumRateWorldNewtons();
		Vec3 throughFlowImpulse = initial ? zero : iteration.sourceAdvance().totalThroughFlowImpulseWorldNewtonSeconds();
		Vec3 cumulativeThroughFlowImpulse = cumulativeThroughFlowImpulse(run, completedSteps);
		Vec3 cumulativeAdvectionMomentumResidual = cumulativeAdvectionMomentumResidual(run, completedSteps);
		Vec3 cumulativeProjectionMomentumResidual = cumulativeProjectionMomentumResidual(run, completedSteps);
		Vec3 cumulativeSolidBoundaryMomentumResidual =
				cumulativeSolidBoundaryMomentumResidual(run, completedSteps);
		double sourceMassFlow = initial ? 0.0 : iteration.sourceAdvance().totalSourceMassFlowRateKilogramsPerSecond();
		double cumulativeSourceMass = cumulativeSourceMass(run, completedSteps);
		double maxResidenceAlpha = initial ? 0.0 : iteration.sourceAdvance().maxResidenceAlpha();
		double meanWakeResidual = initial ? 0.0
				: iteration.sourceAdvance().meanActiveWakeResidualAfterResidenceMetersPerSecond();
		double advectionCourantNumber = initial ? 0.0 : iteration.advectionRun().maxCourantNumber();
		int advectionSubstepCount = initial ? 0 : iteration.advectionRun().completedSubstepCount();
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics divergenceBeforeProjection =
				initial ? new PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics(0.0, 0.0, 0.0)
						: iteration.projectionStep().divergenceBefore();
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics divergenceAfterProjection =
				initial ? new PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics(0.0, 0.0, 0.0)
						: iteration.projectionStep().divergenceAfter();
		int solidCellCount = initial ? run.solidMask().solidCellCount()
				: iteration.solidBoundaryStep().solidCellCount();
		int solidClampedCellCount = initial ? 0 : iteration.solidBoundaryStep().clampedCellCount();
		double energyBeforeSource = initial ? run.initialKineticEnergyJoules()
				: iteration.stateBeforeStep().totalKineticEnergyJoules(run.config().airDensityKgPerCubicMeter());
		double energyAfterSource = initial ? energyBeforeSource
				: iteration.stateAfterSource().totalKineticEnergyJoules(run.config().airDensityKgPerCubicMeter());
		double energyAfterAdvection = initial ? energyAfterSource : iteration.advectionRun().kineticEnergyAfterJoules();
		double energyAdvectionDelta = initial ? 0.0 : iteration.advectionRun().kineticEnergyDeltaJoules();
		double energyAfterDiffusion = initial ? energyAfterAdvection
				: iteration.diffusionStep().kineticEnergyAfterJoules();
		double energyDiffusionDelta = initial ? 0.0 : iteration.diffusionStep().kineticEnergyDeltaJoules();
		double energyAfterProjection = initial ? energyAfterDiffusion
				: iteration.projectionStep().kineticEnergyAfterJoules();
		double energyProjectionDelta = initial ? 0.0 : iteration.projectionStep().kineticEnergyDeltaJoules();
		double energyAfterSolidBoundary = initial ? energyAfterProjection
				: iteration.solidBoundaryStep().kineticEnergyAfterJoules();
		double energySolidBoundaryDelta = initial ? 0.0
				: iteration.solidBoundaryStep().kineticEnergyDeltaJoules();
		double maxSpeedAfterSource = initial ? run.initialState().maxSpeedMetersPerSecond()
				: iteration.stateAfterSource().maxSpeedMetersPerSecond();
		double maxSpeedAfterAdvection = initial ? maxSpeedAfterSource
				: iteration.stateAfterAdvection().maxSpeedMetersPerSecond();
		double maxSpeedAfterDiffusion = initial ? run.initialState().maxSpeedMetersPerSecond()
				: iteration.stateAfterDiffusion().maxSpeedMetersPerSecond();
		double maxSpeedAfterProjection = initial ? maxSpeedAfterDiffusion
				: iteration.stateAfterProjection().maxSpeedMetersPerSecond();
		double maxSpeedAfterSolidBoundary = initial ? maxSpeedAfterProjection
				: iteration.stateAfterSolidBoundary().maxSpeedMetersPerSecond();
		Vec3 advectionMomentumBefore = initial ? run.initialState()
				.totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter())
				: iteration.advectionRun().totalMomentumBeforeWorldNewtonSeconds();
		Vec3 advectionMomentumAfter = initial ? advectionMomentumBefore
				: iteration.advectionRun().totalMomentumAfterWorldNewtonSeconds();
		Vec3 advectionMomentumResidual = initial ? zero : iteration.advectionRun().momentumResidualWorldNewtonSeconds();
		Vec3 momentumBeforeDiffusion = initial ? run.initialState()
				.totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter())
				: iteration.diffusionStep().totalMomentumBeforeWorldNewtonSeconds();
		Vec3 momentumAfterDiffusion = initial ? momentumBeforeDiffusion
				: iteration.diffusionStep().totalMomentumAfterWorldNewtonSeconds();
		Vec3 diffusionMomentumResidual = initial ? zero : iteration.diffusionStep().momentumResidualWorldNewtonSeconds();
		Vec3 projectionMomentumBefore = initial ? momentumAfterDiffusion
				: iteration.projectionStep().totalMomentumBeforeWorldNewtonSeconds();
		Vec3 projectionMomentumAfter = initial ? projectionMomentumBefore
				: iteration.projectionStep().totalMomentumAfterWorldNewtonSeconds();
		Vec3 projectionMomentumResidual = initial ? zero : iteration.projectionStep().momentumResidualWorldNewtonSeconds();
		Vec3 solidBoundaryMomentumBefore = initial ? projectionMomentumAfter
				: iteration.solidBoundaryStep().totalMomentumBeforeWorldNewtonSeconds();
		Vec3 solidBoundaryMomentumAfter = initial ? solidBoundaryMomentumBefore
				: iteration.solidBoundaryStep().totalMomentumAfterWorldNewtonSeconds();
		Vec3 solidBoundaryMomentumResidual = initial ? zero
				: iteration.solidBoundaryStep().momentumResidualWorldNewtonSeconds();
		Vec3 finalMomentum = initial ? run.initialState()
				.totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter())
				: iteration.stateAfterSolidBoundary().totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter());
		return String.join(",",
				escape(metadata.key().preset()),
				escape(metadata.key().caseName()),
				escape(metadata.key().rowKind()),
				initial ? "initial" : "step",
				Integer.toString(initial ? 0 : iteration.stepIndex()),
				Integer.toString(completedSteps),
				escape(metadata.gridStatus()),
				escape(metadata.lookupStatuses()),
				Integer.toString(metadata.sourceCount()),
				Integer.toString(metadata.appliedSourceCount()),
				number(run.config().airDensityKgPerCubicMeter()),
				number(run.config().sourceThicknessMeters()),
				number(metadata.cellSizeMeters()),
				Integer.toString(metadata.paddingCells()),
				number(metadata.downstreamWakeLengthMeters()),
				Integer.toString(metadata.subcellSamplesPerAxis()),
				number(run.config().timeStepSeconds()),
				Integer.toString(run.config().stepCount()),
				number(run.config().kinematicViscositySquareMetersPerSecond()),
				number(run.config().diffusionNumber(metadata.sourceGridSample().gridSpec())),
				number(run.config().maxAdvectionCourantNumber()),
				number(advectionCourantNumber),
				Integer.toString(advectionSubstepCount),
				Integer.toString(run.config().pressureProjectionIterations()),
				Integer.toString(metadata.sourceGridSample().gridSpec().totalCellCount()),
				Integer.toString(metadata.sourceGridSample().activeCellCount()),
				Integer.toString(solidCellCount),
				Integer.toString(solidClampedCellCount),
				number(metadata.targetBodyForceWorldNewtons().x()),
				number(metadata.targetBodyForceWorldNewtons().y()),
				number(metadata.targetBodyForceWorldNewtons().z()),
				number(sourceMomentumRate.x()),
				number(sourceMomentumRate.y()),
				number(sourceMomentumRate.z()),
				number(sourceImpulse.x()),
				number(sourceImpulse.y()),
				number(sourceImpulse.z()),
				number(cumulativeSourceImpulse.x()),
				number(cumulativeSourceImpulse.y()),
				number(cumulativeSourceImpulse.z()),
				number(throughFlowMomentumRate.x()),
				number(throughFlowMomentumRate.y()),
				number(throughFlowMomentumRate.z()),
				number(throughFlowImpulse.x()),
				number(throughFlowImpulse.y()),
				number(throughFlowImpulse.z()),
				number(cumulativeThroughFlowImpulse.x()),
				number(cumulativeThroughFlowImpulse.y()),
				number(cumulativeThroughFlowImpulse.z()),
				number(cumulativeAdvectionMomentumResidual.x()),
				number(cumulativeAdvectionMomentumResidual.y()),
				number(cumulativeAdvectionMomentumResidual.z()),
				number(cumulativeProjectionMomentumResidual.x()),
				number(cumulativeProjectionMomentumResidual.y()),
				number(cumulativeProjectionMomentumResidual.z()),
				number(cumulativeSolidBoundaryMomentumResidual.x()),
				number(cumulativeSolidBoundaryMomentumResidual.y()),
				number(cumulativeSolidBoundaryMomentumResidual.z()),
				number(sourceMassFlow),
				number(cumulativeSourceMass),
				number(maxResidenceAlpha),
				number(meanWakeResidual),
				number(divergenceBeforeProjection.maxAbsDivergencePerSecond()),
				number(divergenceBeforeProjection.rmsDivergencePerSecond()),
				number(divergenceBeforeProjection.meanDivergencePerSecond()),
				number(divergenceAfterProjection.maxAbsDivergencePerSecond()),
				number(divergenceAfterProjection.rmsDivergencePerSecond()),
				number(divergenceAfterProjection.meanDivergencePerSecond()),
				number(energyBeforeSource),
				number(energyAfterSource),
				number(energyAfterAdvection),
				number(energyAdvectionDelta),
				number(energyAfterDiffusion),
				number(energyDiffusionDelta),
				number(energyAfterProjection),
				number(energyProjectionDelta),
				number(energyAfterSolidBoundary),
				number(energySolidBoundaryDelta),
				number(maxSpeedAfterSource),
				number(maxSpeedAfterAdvection),
				number(maxSpeedAfterDiffusion),
				number(maxSpeedAfterProjection),
				number(maxSpeedAfterSolidBoundary),
				number(advectionMomentumBefore.x()),
				number(advectionMomentumBefore.y()),
				number(advectionMomentumBefore.z()),
				number(advectionMomentumAfter.x()),
				number(advectionMomentumAfter.y()),
				number(advectionMomentumAfter.z()),
				number(advectionMomentumResidual.x()),
				number(advectionMomentumResidual.y()),
				number(advectionMomentumResidual.z()),
				number(momentumBeforeDiffusion.x()),
				number(momentumBeforeDiffusion.y()),
				number(momentumBeforeDiffusion.z()),
				number(momentumAfterDiffusion.x()),
				number(momentumAfterDiffusion.y()),
				number(momentumAfterDiffusion.z()),
				number(diffusionMomentumResidual.x()),
				number(diffusionMomentumResidual.y()),
				number(diffusionMomentumResidual.z()),
				number(projectionMomentumBefore.x()),
				number(projectionMomentumBefore.y()),
				number(projectionMomentumBefore.z()),
				number(projectionMomentumAfter.x()),
				number(projectionMomentumAfter.y()),
				number(projectionMomentumAfter.z()),
				number(projectionMomentumResidual.x()),
				number(projectionMomentumResidual.y()),
				number(projectionMomentumResidual.z()),
				number(solidBoundaryMomentumBefore.x()),
				number(solidBoundaryMomentumBefore.y()),
				number(solidBoundaryMomentumBefore.z()),
				number(solidBoundaryMomentumAfter.x()),
				number(solidBoundaryMomentumAfter.y()),
				number(solidBoundaryMomentumAfter.z()),
				number(solidBoundaryMomentumResidual.x()),
				number(solidBoundaryMomentumResidual.y()),
				number(solidBoundaryMomentumResidual.z()),
				number(finalMomentum.x()),
				number(finalMomentum.y()),
				number(finalMomentum.z())
		);
	}

	private static Vec3 cumulativeSourceImpulse(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(run.iterations().get(i).sourceAdvance().totalSourceImpulseWorldNewtonSeconds());
		}
		return sum;
	}

	private static Vec3 cumulativeThroughFlowImpulse(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(run.iterations().get(i).sourceAdvance().totalThroughFlowImpulseWorldNewtonSeconds());
		}
		return sum;
	}

	private static Vec3 cumulativeAdvectionMomentumResidual(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(run.iterations().get(i).advectionRun().momentumResidualWorldNewtonSeconds());
		}
		return sum;
	}

	private static Vec3 cumulativeProjectionMomentumResidual(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(run.iterations().get(i).projectionStep().momentumResidualWorldNewtonSeconds());
		}
		return sum;
	}

	private static Vec3 cumulativeSolidBoundaryMomentumResidual(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(run.iterations().get(i).solidBoundaryStep().momentumResidualWorldNewtonSeconds());
		}
		return sum;
	}

	private static double cumulativeSourceMass(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double mass = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			mass += run.iterations().get(i).sourceAdvance().totalSourceMassFlowRateKilogramsPerSecond()
					* run.config().timeStepSeconds();
		}
		return mass;
	}

	private static GroupMetadata metadata(
			SourceGroupKey key,
			Map<String, String> first,
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config,
			int paddingCells,
			double downstreamWakeLengthMeters
	) {
		return new GroupMetadata(
				key,
				text(first, "grid_status"),
				text(first, "lookup_statuses"),
				(int) number(first, "source_count"),
				(int) number(first, "applied_source_count"),
				number(first, "cell_size_m"),
				paddingCells,
				downstreamWakeLengthMeters,
				(int) number(first, "subcell_samples_per_axis"),
				vector(first,
						"target_body_force_world_x_n",
						"target_body_force_world_y_n",
						"target_body_force_world_z_n"),
				sourceGridSample,
				config
		);
	}

	private static PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample(
			List<Map<String, String>> rows
	) {
		Map<String, String> first = rows.get(0);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec(
						vector(first, "grid_origin_x_m", "grid_origin_y_m", "grid_origin_z_m"),
						number(first, "cell_size_m"),
						(int) number(first, "grid_count_x"),
						(int) number(first, "grid_count_y"),
						(int) number(first, "grid_count_z")
				);
		int subcellSamples = (int) number(first, "subcell_samples_per_axis");
		List<PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample> cells =
				zeroCells(grid, subcellSamples);
		for (Map<String, String> row : rows) {
			int x = (int) number(row, "cell_x");
			int y = (int) number(row, "cell_y");
			int z = (int) number(row, "cell_z");
			int index = linearIndex(grid, x, y, z);
			cells.set(index, sourceCell(row));
		}
		return new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample(grid, subcellSamples, cells);
	}

	private static List<PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample> zeroCells(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid,
			int subcellSamplesPerAxis
	) {
		List<PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample> cells =
				new ArrayList<>(grid.totalCellCount());
		int totalSubsamples = Math.max(1, subcellSamplesPerAxis * subcellSamplesPerAxis * subcellSamplesPerAxis);
		for (int y = 0; y < grid.cellCountY(); y++) {
			for (int z = 0; z < grid.cellCountZ(); z++) {
				for (int x = 0; x < grid.cellCountX(); x++) {
					cells.add(new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample(
							x,
							y,
							z,
							grid.cellCenterWorldMeters(x, y, z),
							grid.cellVolumeCubicMeters(),
							totalSubsamples,
							0,
							0.0,
							Vec3.ZERO,
							Vec3.ZERO,
							0.0,
							0.0,
							0.0,
							Vec3.ZERO,
							Vec3.ZERO,
							Vec3.ZERO
					));
				}
			}
		}
		return cells;
	}

	private static PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell(
			Map<String, String> row
	) {
		return new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample(
				(int) number(row, "cell_x"),
				(int) number(row, "cell_y"),
				(int) number(row, "cell_z"),
				vector(row,
						"cell_center_world_x_m",
						"cell_center_world_y_m",
						"cell_center_world_z_m"),
				number(row, "cell_volume_m3"),
				(int) number(row, "total_subsample_count"),
				(int) number(row, "active_subsample_count"),
				number(row, "source_volume_fraction"),
				vector(row,
						"body_force_density_world_x_n_m3",
						"body_force_density_world_y_n_m3",
						"body_force_density_world_z_n_m3"),
				vector(row,
						"wake_angular_momentum_torque_density_world_x_nm_m3",
						"wake_angular_momentum_torque_density_world_y_nm_m3",
						"wake_angular_momentum_torque_density_world_z_nm_m3"),
				number(row, "pressure_jump_pa"),
				number(row, "mass_flux_kg_s_m2"),
				number(row, "ideal_momentum_power_loading_w_m2"),
				vector(row,
						"far_wake_axial_velocity_world_x_mps",
						"far_wake_axial_velocity_world_y_mps",
						"far_wake_axial_velocity_world_z_mps"),
				vector(row,
						"wake_swirl_velocity_world_x_mps",
						"wake_swirl_velocity_world_y_mps",
						"wake_swirl_velocity_world_z_mps"),
				vector(row,
						"target_wake_velocity_world_x_mps",
						"target_wake_velocity_world_y_mps",
						"target_wake_velocity_world_z_mps")
		);
	}

	private static Map<SourceGroupKey, List<Map<String, String>>> sourceGroups(
			List<Map<String, String>> rows
	) {
		Map<SourceGroupKey, List<Map<String, String>>> groups = new LinkedHashMap<>();
		for (Map<String, String> row : rows) {
			SourceGroupKey key = new SourceGroupKey(
					text(row, "preset"),
					text(row, "case"),
					text(row, "row_kind")
			);
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
		}
		return groups;
	}

	private static int linearIndex(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid,
			int x,
			int y,
			int z
	) {
		return (y * grid.cellCountZ() + z) * grid.cellCountX() + x;
	}

	private static List<Map<String, String>> parseCsv(String inputCsv) {
		List<List<String>> rawRows = new ArrayList<>();
		for (String line : inputCsv.split("\\R")) {
			if (line == null || line.isBlank()) {
				continue;
			}
			rawRows.add(parseCsvLine(line));
		}
		if (rawRows.isEmpty()) {
			return List.of();
		}
		List<String> header = rawRows.get(0).stream()
				.map(CtCpJLocalVoxelFlowSolverExporter::normalizeHeader)
				.toList();
		List<Map<String, String>> records = new ArrayList<>();
		for (int rowIndex = 1; rowIndex < rawRows.size(); rowIndex++) {
			Map<String, String> record = new LinkedHashMap<>();
			List<String> cells = rawRows.get(rowIndex);
			for (int column = 0; column < header.size(); column++) {
				record.put(header.get(column), column < cells.size() ? cells.get(column).trim() : "");
			}
			records.add(record);
		}
		return records;
	}

	private static List<String> parseCsvLine(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					cell.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				cells.add(cell.toString());
				cell.setLength(0);
			} else {
				cell.append(ch);
			}
		}
		cells.add(cell.toString());
		return cells;
	}

	private static String normalizeHeader(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	private static String text(Map<String, String> row, String columnName) {
		return row.getOrDefault(columnName, "");
	}

	private static double number(Map<String, String> row, String columnName) {
		String value = row.get(columnName);
		return value == null || value.isBlank() ? Double.NaN : Double.parseDouble(value);
	}

	private static Vec3 vector(Map<String, String> row, String x, String y, String z) {
		return new Vec3(number(row, x), number(row, y), number(row, z));
	}

	private static String number(double value) {
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.15g", value) : "";
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private record SourceGroupKey(
			String preset,
			String caseName,
			String rowKind
	) {
	}

	private record GroupMetadata(
			SourceGroupKey key,
			String gridStatus,
			String lookupStatuses,
			int sourceCount,
			int appliedSourceCount,
			double cellSizeMeters,
			int paddingCells,
			double downstreamWakeLengthMeters,
			int subcellSamplesPerAxis,
			Vec3 targetBodyForceWorldNewtons,
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config
	) {
	}
}
