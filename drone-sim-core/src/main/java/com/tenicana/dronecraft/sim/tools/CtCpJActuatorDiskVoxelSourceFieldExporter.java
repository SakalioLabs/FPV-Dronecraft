package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJActuatorDiskSourceField;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJRotorForceModel;
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
import java.util.StringJoiner;

public final class CtCpJActuatorDiskVoxelSourceFieldExporter {
	public static final double DEFAULT_CELL_SIZE_METERS = 0.02;
	public static final int DEFAULT_PADDING_CELLS = 1;
	public static final int DEFAULT_SUBCELL_SAMPLES_PER_AXIS = 3;

	private static final String ACTIVE_GRID_STATUS = "ACTIVE_SOURCE_FIELD";
	private static final String EMPTY_GRID_STATUS = "EMPTY_SOURCE_FIELD";
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"source_group_name",
			"grid_status",
			"lookup_statuses",
			"clamped",
			"blocked",
			"source_count",
			"applied_source_count",
			"runtime_force_replacement_accepted_count",
			"air_density_kg_m3",
			"source_thickness_m",
			"cell_size_m",
			"padding_cells",
			"subcell_samples_per_axis",
			"query_j",
			"effective_j",
			"query_rpm",
			"effective_rpm",
			"ct",
			"cp",
			"eta",
			"grid_origin_x_m",
			"grid_origin_y_m",
			"grid_origin_z_m",
			"grid_count_x",
			"grid_count_y",
			"grid_count_z",
			"grid_cell_count",
			"active_cell_count",
			"active_subsample_count",
			"sampled_source_volume_m3",
			"target_body_force_world_x_n",
			"target_body_force_world_y_n",
			"target_body_force_world_z_n",
			"voxel_body_force_world_x_n",
			"voxel_body_force_world_y_n",
			"voxel_body_force_world_z_n",
			"body_force_residual_world_x_n",
			"body_force_residual_world_y_n",
			"body_force_residual_world_z_n",
			"target_wake_angular_momentum_torque_world_x_nm",
			"target_wake_angular_momentum_torque_world_y_nm",
			"target_wake_angular_momentum_torque_world_z_nm",
			"voxel_wake_angular_momentum_torque_world_x_nm",
			"voxel_wake_angular_momentum_torque_world_y_nm",
			"voxel_wake_angular_momentum_torque_world_z_nm",
			"wake_angular_momentum_torque_residual_world_x_nm",
			"wake_angular_momentum_torque_residual_world_y_nm",
			"wake_angular_momentum_torque_residual_world_z_nm",
			"target_ideal_momentum_power_w",
			"voxel_ideal_momentum_power_w",
			"ideal_momentum_power_residual_w",
			"target_wake_swirl_kinetic_power_w",
			"voxel_wake_swirl_kinetic_power_w",
			"wake_swirl_kinetic_power_residual_w",
			"target_total_wake_kinetic_power_w",
			"voxel_total_wake_kinetic_power_w",
			"total_wake_kinetic_power_residual_w",
			"cell_x",
			"cell_y",
			"cell_z",
			"cell_active",
			"cell_center_world_x_m",
			"cell_center_world_y_m",
			"cell_center_world_z_m",
			"cell_volume_m3",
			"total_subsample_count",
			"active_subsample_count",
			"source_volume_fraction",
			"body_force_density_world_x_n_m3",
			"body_force_density_world_y_n_m3",
			"body_force_density_world_z_n_m3",
			"acceleration_source_world_x_m_s2",
			"acceleration_source_world_y_m_s2",
			"acceleration_source_world_z_m_s2",
			"integrated_body_force_world_x_n",
			"integrated_body_force_world_y_n",
			"integrated_body_force_world_z_n",
			"wake_angular_momentum_torque_density_world_x_nm_m3",
			"wake_angular_momentum_torque_density_world_y_nm_m3",
			"wake_angular_momentum_torque_density_world_z_nm_m3",
			"integrated_wake_angular_momentum_torque_world_x_nm",
			"integrated_wake_angular_momentum_torque_world_y_nm",
			"integrated_wake_angular_momentum_torque_world_z_nm",
			"pressure_jump_pa",
			"mass_flux_kg_s_m2",
			"actuator_disk_axial_velocity_world_x_mps",
			"actuator_disk_axial_velocity_world_y_mps",
			"actuator_disk_axial_velocity_world_z_mps",
			"ideal_momentum_power_loading_w_m2",
			"wake_swirl_kinetic_power_loading_w_m2",
			"total_wake_kinetic_power_loading_w_m2",
			"integrated_ideal_momentum_power_w",
			"integrated_wake_swirl_kinetic_power_w",
			"integrated_total_wake_kinetic_power_w",
			"far_wake_axial_velocity_world_x_mps",
			"far_wake_axial_velocity_world_y_mps",
			"far_wake_axial_velocity_world_z_mps",
			"wake_swirl_velocity_world_x_mps",
			"wake_swirl_velocity_world_y_mps",
			"wake_swirl_velocity_world_z_mps",
			"target_wake_velocity_world_x_mps",
			"target_wake_velocity_world_y_mps",
			"target_wake_velocity_world_z_mps"
	);

	private CtCpJActuatorDiskVoxelSourceFieldExporter() {
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-actuator-disk-voxel-source-fields",
						presetName + "-voxel-source-field.csv");
		double airDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double sourceThickness = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
		double cellSizeMeters = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: DEFAULT_CELL_SIZE_METERS;
		int paddingCells = args.length >= 6 && !args[5].isBlank()
				? Integer.parseInt(args[5])
				: DEFAULT_PADDING_CELLS;
		int subcellSamplesPerAxis = args.length >= 7 && !args[6].isBlank()
				? Integer.parseInt(args[6])
				: DEFAULT_SUBCELL_SAMPLES_PER_AXIS;
		double ambientTemperatureCelsius = args.length >= 8 && !args[7].isBlank()
				? Double.parseDouble(args[7])
				: 25.0;
		double ambientHumidity = args.length >= 9 && !args[8].isBlank()
				? Double.parseDouble(args[8])
				: 0.0;
		write(
				presetName,
				output,
				airDensity,
				sourceThickness,
				cellSizeMeters,
				paddingCells,
				subcellSamplesPerAxis,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter
	) throws IOException {
		write(
				presetName,
				output,
				airDensityKgPerCubicMeter,
				CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS,
				DEFAULT_CELL_SIZE_METERS,
				DEFAULT_PADDING_CELLS,
				DEFAULT_SUBCELL_SAMPLES_PER_AXIS,
				25.0,
				0.0
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
			double ambientTemperatureCelsius,
			double ambientHumidity
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
				ambientTemperatureCelsius,
				ambientHumidity
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter
	) {
		return csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS,
				DEFAULT_CELL_SIZE_METERS,
				DEFAULT_PADDING_CELLS,
				DEFAULT_SUBCELL_SAMPLES_PER_AXIS,
				25.0,
				0.0
		);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double cellSizeMeters,
			int paddingCells,
			int subcellSamplesPerAxis,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				cellSizeMeters,
				paddingCells,
				subcellSamplesPerAxis,
				ambientTemperatureCelsius,
				ambientHumidity,
				0.0
		);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double cellSizeMeters,
			int paddingCells,
			int subcellSamplesPerAxis,
			double ambientTemperatureCelsius,
			double ambientHumidity,
			double downstreamWakeLengthMeters
	) {
		validate(
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				cellSizeMeters,
				paddingCells,
				subcellSamplesPerAxis,
				downstreamWakeLengthMeters
		);
		List<Map<String, String>> rawRows = parseCsv(String.join("\n",
				CtCpJActuatorDiskSourceTermExporter.csvLines(
						presetName,
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						ambientTemperatureCelsius,
						ambientHumidity
				)));
		Map<SourceGroupKey, List<SourceRow>> groups = sourceGroups(rawRows);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (Map.Entry<SourceGroupKey, List<SourceRow>> entry : groups.entrySet()) {
			lines.addAll(csvLinesForGroup(
					entry.getKey(),
					entry.getValue(),
					airDensityKgPerCubicMeter,
					sourceThicknessMeters,
					cellSizeMeters,
					paddingCells,
					subcellSamplesPerAxis,
					downstreamWakeLengthMeters
			));
		}
		return List.copyOf(lines);
	}

	private static void validate(
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double cellSizeMeters,
			int paddingCells,
			int subcellSamplesPerAxis,
			double downstreamWakeLengthMeters
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= 0.0) {
			throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
		}
		if (!Double.isFinite(cellSizeMeters) || cellSizeMeters <= 0.0) {
			throw new IllegalArgumentException("cellSizeMeters must be finite and positive.");
		}
		if (paddingCells < 0) {
			throw new IllegalArgumentException("paddingCells must be nonnegative.");
		}
		if (subcellSamplesPerAxis <= 0) {
			throw new IllegalArgumentException("subcellSamplesPerAxis must be positive.");
		}
		if (!Double.isFinite(downstreamWakeLengthMeters) || downstreamWakeLengthMeters < 0.0) {
			throw new IllegalArgumentException("downstreamWakeLengthMeters must be finite and nonnegative.");
		}
	}

	private static Map<SourceGroupKey, List<SourceRow>> sourceGroups(List<Map<String, String>> rawRows) {
		Map<SourceGroupKey, List<SourceRow>> groups = new LinkedHashMap<>();
		for (Map<String, String> rawRow : rawRows) {
			SourceGroupKey key = new SourceGroupKey(
					text(rawRow, "preset"),
					text(rawRow, "case"),
					text(rawRow, "row_kind")
			);
			groups.computeIfAbsent(key, ignored -> new ArrayList<>())
					.add(new SourceRow(rawRow, sourceTerm(rawRow)));
		}
		return groups;
	}

	private static List<String> csvLinesForGroup(
			SourceGroupKey key,
			List<SourceRow> sourceRows,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double cellSizeMeters,
			int paddingCells,
			int subcellSamplesPerAxis,
			double downstreamWakeLengthMeters
	) {
		List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms =
				sourceRows.stream()
						.map(SourceRow::sourceTerm)
						.toList();
		PropellerArchiveCtCpJActuatorDiskSourceField field =
				new PropellerArchiveCtCpJActuatorDiskSourceField(sourceTerms, sourceThicknessMeters);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid =
				field.enclosingWakeVoxelGrid(cellSizeMeters, paddingCells, downstreamWakeLengthMeters);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sample =
				field.sampleConservativeVoxelGrid(grid, subcellSamplesPerAxis);
		Vec3 targetForce = field.integratedBodyForceWorldNewtons();
		Vec3 voxelForce = sample.integratedBodyForceWorldNewtons();
		Vec3 targetTorque = field.integratedWakeAngularMomentumTorqueWorldNewtonMeters();
		Vec3 voxelTorque = sample.integratedWakeAngularMomentumTorqueWorldNewtonMeters();
		double targetIdealMomentumPower = field.integratedIdealMomentumPowerWatts();
		double voxelIdealMomentumPower = sample.integratedIdealMomentumPowerWatts(sourceThicknessMeters);
		double targetWakeSwirlKineticPower = field.integratedWakeSwirlKineticPowerWatts();
		double voxelWakeSwirlKineticPower = sample.integratedWakeSwirlKineticPowerWatts(sourceThicknessMeters);
		double targetTotalWakeKineticPower = field.integratedTotalWakeKineticPowerWatts();
		double voxelTotalWakeKineticPower = sample.integratedTotalWakeKineticPowerWatts(sourceThicknessMeters);
		GroupSummary summary = new GroupSummary(
				key,
				sourceRows,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				cellSizeMeters,
				paddingCells,
				sample,
				targetForce,
				voxelForce,
				targetTorque,
				voxelTorque,
				targetIdealMomentumPower,
				voxelIdealMomentumPower,
				targetWakeSwirlKineticPower,
				voxelWakeSwirlKineticPower,
				targetTotalWakeKineticPower,
				voxelTotalWakeKineticPower
		);
		List<PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample> activeCells = sample.activeCells();
		if (activeCells.isEmpty()) {
			return List.of(csvLine(summary, EMPTY_GRID_STATUS, sample.cells().get(0)));
		}
		List<String> lines = new ArrayList<>(activeCells.size());
		for (PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample cell : activeCells) {
			lines.add(csvLine(summary, ACTIVE_GRID_STATUS, cell));
		}
		return lines;
	}

	private static String csvLine(
			GroupSummary summary,
			String gridStatus,
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample cell
	) {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sample = summary.sample();
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec grid = sample.gridSpec();
		Vec3 forceResidual = summary.voxelForceWorldNewtons().subtract(summary.targetForceWorldNewtons());
		Vec3 torqueResidual = summary.voxelWakeTorqueWorldNewtonMeters()
				.subtract(summary.targetWakeTorqueWorldNewtonMeters());
		double idealMomentumPowerResidual =
				summary.voxelIdealMomentumPowerWatts() - summary.targetIdealMomentumPowerWatts();
		double wakeSwirlKineticPowerResidual =
				summary.voxelWakeSwirlKineticPowerWatts() - summary.targetWakeSwirlKineticPowerWatts();
		double totalWakeKineticPowerResidual =
				summary.voxelTotalWakeKineticPowerWatts() - summary.targetTotalWakeKineticPowerWatts();
		Vec3 bodyForceDensity = cell.bodyForceDensityWorldNewtonsPerCubicMeter();
		Vec3 acceleration = bodyForceDensity.multiply(1.0 / summary.airDensityKgPerCubicMeter());
		Vec3 integratedForce = cell.integratedBodyForceWorldNewtons();
		Vec3 wakeTorqueDensity = cell.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter();
		Vec3 integratedWakeTorque = cell.integratedWakeAngularMomentumTorqueWorldNewtonMeters();
		double integratedIdealMomentumPower =
				cell.integratedIdealMomentumPowerWatts(summary.sourceThicknessMeters());
		double integratedWakeSwirlKineticPower =
				cell.integratedWakeSwirlKineticPowerWatts(summary.sourceThicknessMeters());
		double integratedTotalWakeKineticPower =
				cell.integratedTotalWakeKineticPowerWatts(summary.sourceThicknessMeters());
		return String.join(",",
				escape(summary.key().preset()),
				escape(summary.key().caseName()),
				escape(summary.key().rowKind()),
				escape(sourceGroupName(summary.key())),
				escape(gridStatus),
				escape(lookupStatuses(summary.sourceRows())),
				Boolean.toString(anyBoolean(summary.sourceRows(), "clamped")),
				Boolean.toString(anyBoolean(summary.sourceRows(), "blocked")),
				Integer.toString(summary.sourceRows().size()),
				Integer.toString(appliedSourceCount(summary.sourceRows())),
				Integer.toString(runtimeAcceptedCount(summary.sourceRows())),
				number(summary.airDensityKgPerCubicMeter()),
				number(summary.sourceThicknessMeters()),
				number(summary.cellSizeMeters()),
				Integer.toString(summary.paddingCells()),
				Integer.toString(sample.subcellSamplesPerAxis()),
				number(average(summary.sourceRows(), "query_j")),
				number(average(summary.sourceRows(), "effective_j")),
				number(average(summary.sourceRows(), "query_rpm")),
				number(average(summary.sourceRows(), "effective_rpm")),
				number(average(summary.sourceRows(), "ct")),
				number(average(summary.sourceRows(), "cp")),
				number(average(summary.sourceRows(), "eta")),
				number(grid.originWorldMeters().x()),
				number(grid.originWorldMeters().y()),
				number(grid.originWorldMeters().z()),
				Integer.toString(grid.cellCountX()),
				Integer.toString(grid.cellCountY()),
				Integer.toString(grid.cellCountZ()),
				Integer.toString(grid.totalCellCount()),
				Integer.toString(sample.activeCellCount()),
				Integer.toString(sample.activeSubsampleCount()),
				number(sample.sampledSourceVolumeCubicMeters()),
				number(summary.targetForceWorldNewtons().x()),
				number(summary.targetForceWorldNewtons().y()),
				number(summary.targetForceWorldNewtons().z()),
				number(summary.voxelForceWorldNewtons().x()),
				number(summary.voxelForceWorldNewtons().y()),
				number(summary.voxelForceWorldNewtons().z()),
				number(forceResidual.x()),
				number(forceResidual.y()),
				number(forceResidual.z()),
				number(summary.targetWakeTorqueWorldNewtonMeters().x()),
				number(summary.targetWakeTorqueWorldNewtonMeters().y()),
				number(summary.targetWakeTorqueWorldNewtonMeters().z()),
				number(summary.voxelWakeTorqueWorldNewtonMeters().x()),
				number(summary.voxelWakeTorqueWorldNewtonMeters().y()),
				number(summary.voxelWakeTorqueWorldNewtonMeters().z()),
				number(torqueResidual.x()),
				number(torqueResidual.y()),
				number(torqueResidual.z()),
				number(summary.targetIdealMomentumPowerWatts()),
				number(summary.voxelIdealMomentumPowerWatts()),
				number(idealMomentumPowerResidual),
				number(summary.targetWakeSwirlKineticPowerWatts()),
				number(summary.voxelWakeSwirlKineticPowerWatts()),
				number(wakeSwirlKineticPowerResidual),
				number(summary.targetTotalWakeKineticPowerWatts()),
				number(summary.voxelTotalWakeKineticPowerWatts()),
				number(totalWakeKineticPowerResidual),
				Integer.toString(cell.xIndex()),
				Integer.toString(cell.yIndex()),
				Integer.toString(cell.zIndex()),
				Boolean.toString(cell.active()),
				number(cell.cellCenterWorldMeters().x()),
				number(cell.cellCenterWorldMeters().y()),
				number(cell.cellCenterWorldMeters().z()),
				number(cell.cellVolumeCubicMeters()),
				Integer.toString(cell.totalSubsampleCount()),
				Integer.toString(cell.activeSubsampleCount()),
				number(cell.sourceVolumeFraction()),
				number(bodyForceDensity.x()),
				number(bodyForceDensity.y()),
				number(bodyForceDensity.z()),
				number(acceleration.x()),
				number(acceleration.y()),
				number(acceleration.z()),
				number(integratedForce.x()),
				number(integratedForce.y()),
				number(integratedForce.z()),
				number(wakeTorqueDensity.x()),
				number(wakeTorqueDensity.y()),
				number(wakeTorqueDensity.z()),
				number(integratedWakeTorque.x()),
				number(integratedWakeTorque.y()),
				number(integratedWakeTorque.z()),
				number(cell.pressureJumpPascals()),
				number(cell.massFluxKilogramsPerSecondSquareMeter()),
				number(cell.actuatorDiskAxialVelocityWorldMetersPerSecond().x()),
				number(cell.actuatorDiskAxialVelocityWorldMetersPerSecond().y()),
				number(cell.actuatorDiskAxialVelocityWorldMetersPerSecond().z()),
				number(cell.idealMomentumPowerLoadingWattsPerSquareMeter()),
				number(cell.wakeSwirlKineticPowerLoadingWattsPerSquareMeter()),
				number(cell.totalWakeKineticPowerLoadingWattsPerSquareMeter()),
				number(integratedIdealMomentumPower),
				number(integratedWakeSwirlKineticPower),
				number(integratedTotalWakeKineticPower),
				number(cell.farWakeAxialVelocityWorldMetersPerSecond().x()),
				number(cell.farWakeAxialVelocityWorldMetersPerSecond().y()),
				number(cell.farWakeAxialVelocityWorldMetersPerSecond().z()),
				number(cell.wakeSwirlVelocityWorldMetersPerSecond().x()),
				number(cell.wakeSwirlVelocityWorldMetersPerSecond().y()),
				number(cell.wakeSwirlVelocityWorldMetersPerSecond().z()),
				number(cell.targetWakeVelocityWorldMetersPerSecond().x()),
				number(cell.targetWakeVelocityWorldMetersPerSecond().y()),
				number(cell.targetWakeVelocityWorldMetersPerSecond().z())
		);
	}

	private static PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm(
			Map<String, String> row
	) {
		return new PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample(
				(int) number(row, "rotor_index"),
				vector(row, "disk_center_world_x_m", "disk_center_world_y_m", "disk_center_world_z_m"),
				vector(row, "disk_normal_world_x", "disk_normal_world_y", "disk_normal_world_z"),
				number(row, "disk_area_m2"),
				number(row, "pressure_jump_pa"),
				number(row, "mass_flux_kg_s_m2"),
				number(row, "ideal_momentum_power_loading_w_m2"),
				vector(row,
						"actuator_disk_axial_velocity_world_x_mps",
						"actuator_disk_axial_velocity_world_y_mps",
						"actuator_disk_axial_velocity_world_z_mps"),
				vector(row,
						"thrust_surface_force_world_x_n_m2",
						"thrust_surface_force_world_y_n_m2",
						"thrust_surface_force_world_z_n_m2"),
				vector(row,
						"far_wake_axial_velocity_world_x_mps",
						"far_wake_axial_velocity_world_y_mps",
						"far_wake_axial_velocity_world_z_mps"),
				vector(row,
						"reaction_torque_world_x_nm",
						"reaction_torque_world_y_nm",
						"reaction_torque_world_z_nm"),
				vector(row,
						"wake_angular_momentum_torque_world_x_nm",
						"wake_angular_momentum_torque_world_y_nm",
						"wake_angular_momentum_torque_world_z_nm"),
				vector(row,
						"wake_angular_momentum_torque_residual_world_x_nm",
						"wake_angular_momentum_torque_residual_world_y_nm",
						"wake_angular_momentum_torque_residual_world_z_nm"),
				number(row, "far_wake_equivalent_radius_m"),
				number(row, "angular_momentum_swirl_radius_m"),
				number(row, "wake_tangential_velocity_mps"),
				number(row, "wake_swirl_kinetic_power_w"),
				number(row, "total_wake_kinetic_power_w"),
				number(row, "wake_swirl_kinetic_power_over_shaft_power"),
				number(row, "total_wake_kinetic_power_over_shaft_power"),
				number(row, "total_wake_kinetic_power_residual_w"),
				number(row, "total_wake_kinetic_power_residual_fraction"),
				Boolean.parseBoolean(text(row, "runtime_force_replacement_accepted")),
				Boolean.parseBoolean(text(row, "applied")),
				text(row, "lookup_status")
		);
	}

	private static String sourceGroupName(SourceGroupKey key) {
		return sanitize("ctcpj_" + key.preset() + "_" + key.caseName() + "_" + key.rowKind()
				+ "_conservative_voxels");
	}

	private static String sanitize(String value) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char ch = Character.toLowerCase(value.charAt(i));
			if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
				builder.append(ch);
			} else if (builder.length() == 0 || builder.charAt(builder.length() - 1) != '_') {
				builder.append('_');
			}
		}
		while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') {
			builder.setLength(builder.length() - 1);
		}
		return builder.toString();
	}

	private static String lookupStatuses(List<SourceRow> sourceRows) {
		StringJoiner joiner = new StringJoiner("|");
		String previous = null;
		for (SourceRow sourceRow : sourceRows) {
			String status = text(sourceRow.rawRow(), "lookup_status");
			if (status.equals(previous)) {
				continue;
			}
			joiner.add(status);
			previous = status;
		}
		return joiner.toString();
	}

	private static boolean anyBoolean(List<SourceRow> sourceRows, String columnName) {
		for (SourceRow sourceRow : sourceRows) {
			if (Boolean.parseBoolean(text(sourceRow.rawRow(), columnName))) {
				return true;
			}
		}
		return false;
	}

	private static int appliedSourceCount(List<SourceRow> sourceRows) {
		int count = 0;
		for (SourceRow sourceRow : sourceRows) {
			if (sourceRow.sourceTerm().applied()) {
				count++;
			}
		}
		return count;
	}

	private static int runtimeAcceptedCount(List<SourceRow> sourceRows) {
		int count = 0;
		for (SourceRow sourceRow : sourceRows) {
			if (sourceRow.sourceTerm().runtimeForceReplacementAccepted()) {
				count++;
			}
		}
		return count;
	}

	private static double average(List<SourceRow> sourceRows, String columnName) {
		double sum = 0.0;
		int count = 0;
		for (SourceRow sourceRow : sourceRows) {
			double value = number(sourceRow.rawRow(), columnName);
			if (Double.isFinite(value)) {
				sum += value;
				count++;
			}
		}
		return count == 0 ? Double.NaN : sum / count;
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
				.map(CtCpJActuatorDiskVoxelSourceFieldExporter::normalizeHeader)
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

	private record SourceRow(
			Map<String, String> rawRow,
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm
	) {
	}

	private record GroupSummary(
			SourceGroupKey key,
			List<SourceRow> sourceRows,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double cellSizeMeters,
			int paddingCells,
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sample,
			Vec3 targetForceWorldNewtons,
			Vec3 voxelForceWorldNewtons,
			Vec3 targetWakeTorqueWorldNewtonMeters,
			Vec3 voxelWakeTorqueWorldNewtonMeters,
			double targetIdealMomentumPowerWatts,
			double voxelIdealMomentumPowerWatts,
			double targetWakeSwirlKineticPowerWatts,
			double voxelWakeSwirlKineticPowerWatts,
			double targetTotalWakeKineticPowerWatts,
			double voxelTotalWakeKineticPowerWatts
	) {
	}
}
