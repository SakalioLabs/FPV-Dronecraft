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
	private static final double EPSILON = 1.0e-12;

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
			"solid_box_count",
			"solid_box_minimum_volume_fraction",
			"solid_occluded_source_cell_count",
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
			"solid_occluded_source_momentum_rate_world_x_n",
			"solid_occluded_source_momentum_rate_world_y_n",
			"solid_occluded_source_momentum_rate_world_z_n",
			"target_wake_angular_momentum_torque_world_x_nm",
			"target_wake_angular_momentum_torque_world_y_nm",
			"target_wake_angular_momentum_torque_world_z_nm",
			"source_wake_angular_momentum_torque_world_x_nm",
			"source_wake_angular_momentum_torque_world_y_nm",
			"source_wake_angular_momentum_torque_world_z_nm",
			"solid_occluded_source_wake_angular_momentum_torque_world_x_nm",
			"solid_occluded_source_wake_angular_momentum_torque_world_y_nm",
			"solid_occluded_source_wake_angular_momentum_torque_world_z_nm",
			"source_wake_angular_momentum_impulse_world_x_nm_s",
			"source_wake_angular_momentum_impulse_world_y_nm_s",
			"source_wake_angular_momentum_impulse_world_z_nm_s",
			"cumulative_source_wake_angular_momentum_impulse_world_x_nm_s",
			"cumulative_source_wake_angular_momentum_impulse_world_y_nm_s",
			"cumulative_source_wake_angular_momentum_impulse_world_z_nm_s",
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
			"target_ideal_momentum_power_w",
			"target_wake_swirl_kinetic_power_w",
			"target_total_wake_kinetic_power_w",
			"source_ideal_momentum_power_w",
			"source_ideal_momentum_energy_j",
			"source_wake_swirl_kinetic_power_w",
			"source_wake_swirl_kinetic_energy_j",
			"source_total_wake_kinetic_power_w",
			"source_total_wake_kinetic_energy_j",
			"solid_occluded_source_ideal_momentum_power_w",
			"solid_occluded_source_wake_swirl_kinetic_power_w",
			"solid_occluded_source_total_wake_kinetic_power_w",
			"source_mechanical_work_power_w",
			"source_mechanical_work_energy_j",
			"through_flow_mechanical_work_power_w",
			"through_flow_mechanical_work_energy_j",
			"combined_mechanical_work_power_w",
			"combined_mechanical_work_energy_j",
			"coupled_source_force_mechanical_work_power_w",
			"coupled_source_force_mechanical_work_energy_j",
			"coupled_wake_residence_mechanical_work_power_w",
			"coupled_wake_residence_mechanical_work_energy_j",
			"combined_mechanical_work_power_minus_source_total_wake_kinetic_power_w",
			"combined_mechanical_work_power_over_source_total_wake_kinetic_power",
			"flow_kinetic_energy_source_delta_j",
			"flow_kinetic_energy_source_delta_minus_ideal_momentum_energy_j",
			"flow_kinetic_energy_source_delta_minus_total_wake_kinetic_energy_j",
			"flow_kinetic_energy_source_delta_minus_source_mechanical_work_j",
			"flow_kinetic_energy_source_delta_minus_combined_mechanical_work_j",
			"cumulative_source_ideal_momentum_energy_j",
			"cumulative_source_wake_swirl_kinetic_energy_j",
			"cumulative_source_total_wake_kinetic_energy_j",
			"cumulative_source_mechanical_work_energy_j",
			"cumulative_through_flow_mechanical_work_energy_j",
			"cumulative_combined_mechanical_work_energy_j",
			"cumulative_coupled_source_force_mechanical_work_energy_j",
			"cumulative_coupled_wake_residence_mechanical_work_energy_j",
			"cumulative_combined_mechanical_work_energy_minus_source_total_wake_kinetic_energy_j",
			"cumulative_combined_mechanical_work_energy_over_source_total_wake_kinetic_energy",
			"cumulative_flow_kinetic_energy_source_delta_j",
			"cumulative_flow_kinetic_energy_source_delta_minus_ideal_momentum_energy_j",
			"cumulative_flow_kinetic_energy_source_delta_minus_total_wake_kinetic_energy_j",
			"cumulative_flow_kinetic_energy_source_delta_minus_source_mechanical_work_j",
			"cumulative_flow_kinetic_energy_source_delta_minus_combined_mechanical_work_j",
			"cumulative_open_boundary_net_outward_mass_kg",
			"cumulative_open_boundary_outward_mass_kg",
			"cumulative_open_boundary_inward_mass_kg",
			"cumulative_open_boundary_net_outward_impulse_world_x_ns",
			"cumulative_open_boundary_net_outward_impulse_world_y_ns",
			"cumulative_open_boundary_net_outward_impulse_world_z_ns",
			"cumulative_open_boundary_net_outward_angular_impulse_world_x_nm_s",
			"cumulative_open_boundary_net_outward_angular_impulse_world_y_nm_s",
			"cumulative_open_boundary_net_outward_angular_impulse_world_z_nm_s",
			"cumulative_open_boundary_net_outward_kinetic_energy_j",
			"cumulative_open_boundary_outward_kinetic_energy_j",
			"cumulative_open_boundary_inward_kinetic_energy_j",
			"cumulative_open_boundary_outward_mass_over_source_mass",
			"cumulative_open_boundary_net_outward_mass_over_source_mass",
			"kinetic_energy_after_solid_boundary_over_cumulative_source_wake_energy",
			"cumulative_open_boundary_net_outward_kinetic_energy_over_source_wake_energy",
			"retained_plus_boundary_net_outward_kinetic_energy_over_source_wake_energy",
			"max_residence_alpha",
			"mean_active_wake_residual_after_residence_mps",
			"mass_flow_weighted_wake_residual_after_residence_mps",
			"max_divergence_before_projection_s",
			"rms_divergence_before_projection_s",
			"mean_divergence_before_projection_s",
			"net_divergence_volume_flow_before_projection_m3_s",
			"gross_abs_divergence_volume_flow_before_projection_m3_s",
			"net_divergence_mass_flow_before_projection_kg_s",
			"gross_abs_divergence_mass_flow_before_projection_kg_s",
			"max_divergence_after_projection_s",
			"rms_divergence_after_projection_s",
			"mean_divergence_after_projection_s",
			"net_divergence_volume_flow_after_projection_m3_s",
			"gross_abs_divergence_volume_flow_after_projection_m3_s",
			"net_divergence_mass_flow_after_projection_kg_s",
			"gross_abs_divergence_mass_flow_after_projection_kg_s",
			"open_boundary_net_outward_volume_flow_after_source_m3_s",
			"open_boundary_outward_volume_flow_after_source_m3_s",
			"open_boundary_inward_volume_flow_after_source_m3_s",
			"open_boundary_net_outward_mass_flow_after_source_kg_s",
			"open_boundary_outward_mass_flow_after_source_kg_s",
			"open_boundary_inward_mass_flow_after_source_kg_s",
			"open_boundary_net_outward_momentum_flux_after_source_world_x_n",
			"open_boundary_net_outward_momentum_flux_after_source_world_y_n",
			"open_boundary_net_outward_momentum_flux_after_source_world_z_n",
			"open_boundary_net_outward_angular_momentum_flux_after_source_world_x_nm",
			"open_boundary_net_outward_angular_momentum_flux_after_source_world_y_nm",
			"open_boundary_net_outward_angular_momentum_flux_after_source_world_z_nm",
			"open_boundary_net_outward_kinetic_power_after_source_w",
			"open_boundary_outward_kinetic_power_after_source_w",
			"open_boundary_inward_kinetic_power_after_source_w",
			"open_boundary_net_outward_volume_flow_after_projection_m3_s",
			"open_boundary_outward_volume_flow_after_projection_m3_s",
			"open_boundary_inward_volume_flow_after_projection_m3_s",
			"open_boundary_net_outward_mass_flow_after_projection_kg_s",
			"open_boundary_outward_mass_flow_after_projection_kg_s",
			"open_boundary_inward_mass_flow_after_projection_kg_s",
			"open_boundary_net_outward_momentum_flux_after_projection_world_x_n",
			"open_boundary_net_outward_momentum_flux_after_projection_world_y_n",
			"open_boundary_net_outward_momentum_flux_after_projection_world_z_n",
			"open_boundary_net_outward_angular_momentum_flux_after_projection_world_x_nm",
			"open_boundary_net_outward_angular_momentum_flux_after_projection_world_y_nm",
			"open_boundary_net_outward_angular_momentum_flux_after_projection_world_z_nm",
			"open_boundary_net_outward_kinetic_power_after_projection_w",
			"open_boundary_outward_kinetic_power_after_projection_w",
			"open_boundary_inward_kinetic_power_after_projection_w",
			"open_boundary_net_outward_volume_flow_after_solid_boundary_m3_s",
			"open_boundary_outward_volume_flow_after_solid_boundary_m3_s",
			"open_boundary_inward_volume_flow_after_solid_boundary_m3_s",
			"open_boundary_net_outward_mass_flow_after_solid_boundary_kg_s",
			"open_boundary_outward_mass_flow_after_solid_boundary_kg_s",
			"open_boundary_inward_mass_flow_after_solid_boundary_kg_s",
			"open_boundary_net_outward_momentum_flux_after_solid_boundary_world_x_n",
			"open_boundary_net_outward_momentum_flux_after_solid_boundary_world_y_n",
			"open_boundary_net_outward_momentum_flux_after_solid_boundary_world_z_n",
			"open_boundary_net_outward_angular_momentum_flux_after_solid_boundary_world_x_nm",
			"open_boundary_net_outward_angular_momentum_flux_after_solid_boundary_world_y_nm",
			"open_boundary_net_outward_angular_momentum_flux_after_solid_boundary_world_z_nm",
			"open_boundary_net_outward_kinetic_power_after_solid_boundary_w",
			"open_boundary_outward_kinetic_power_after_solid_boundary_w",
			"open_boundary_inward_kinetic_power_after_solid_boundary_w",
			"max_vorticity_after_source_s",
			"rms_vorticity_after_source_s",
			"mean_vorticity_after_source_world_x_s",
			"mean_vorticity_after_source_world_y_s",
			"mean_vorticity_after_source_world_z_s",
			"enstrophy_after_source_m3_per_s2",
			"helicity_after_source_m4_per_s2",
			"mean_helicity_density_after_source_m_per_s2",
			"max_vorticity_after_projection_s",
			"rms_vorticity_after_projection_s",
			"mean_vorticity_after_projection_world_x_s",
			"mean_vorticity_after_projection_world_y_s",
			"mean_vorticity_after_projection_world_z_s",
			"enstrophy_after_projection_m3_per_s2",
			"helicity_after_projection_m4_per_s2",
			"mean_helicity_density_after_projection_m_per_s2",
			"max_vorticity_after_solid_boundary_s",
			"rms_vorticity_after_solid_boundary_s",
			"mean_vorticity_after_solid_boundary_world_x_s",
			"mean_vorticity_after_solid_boundary_world_y_s",
			"mean_vorticity_after_solid_boundary_world_z_s",
			"enstrophy_after_solid_boundary_m3_per_s2",
			"helicity_after_solid_boundary_m4_per_s2",
			"mean_helicity_density_after_solid_boundary_m_per_s2",
			"kinetic_energy_centroid_after_source_world_x_m",
			"kinetic_energy_centroid_after_source_world_y_m",
			"kinetic_energy_centroid_after_source_world_z_m",
			"kinetic_energy_rms_radius_after_source_m",
			"kinetic_energy_centroid_after_projection_world_x_m",
			"kinetic_energy_centroid_after_projection_world_y_m",
			"kinetic_energy_centroid_after_projection_world_z_m",
			"kinetic_energy_rms_radius_after_projection_m",
			"kinetic_energy_centroid_after_solid_boundary_world_x_m",
			"kinetic_energy_centroid_after_solid_boundary_world_y_m",
			"kinetic_energy_centroid_after_solid_boundary_world_z_m",
			"kinetic_energy_rms_radius_after_solid_boundary_m",
			"kinetic_energy_before_source_j",
			"kinetic_energy_after_source_j",
			"kinetic_energy_after_advection_j",
			"kinetic_energy_advection_delta_j",
			"kinetic_energy_after_diffusion_j",
			"kinetic_energy_diffusion_delta_j",
			"viscous_dissipated_energy_j",
			"mean_viscous_dissipation_power_w",
			"cumulative_viscous_dissipated_energy_j",
			"kinetic_energy_after_projection_j",
			"kinetic_energy_projection_delta_j",
			"kinetic_energy_after_solid_boundary_j",
			"kinetic_energy_solid_boundary_delta_j",
			"solid_boundary_dissipated_energy_j",
			"cumulative_solid_boundary_dissipated_energy_j",
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
			"flow_impulse_on_solid_boundary_world_x_ns",
			"flow_impulse_on_solid_boundary_world_y_ns",
			"flow_impulse_on_solid_boundary_world_z_ns",
			"final_momentum_world_x_ns",
			"final_momentum_world_y_ns",
			"final_momentum_world_z_ns",
			"flow_angular_momentum_reference_world_x_m",
			"flow_angular_momentum_reference_world_y_m",
			"flow_angular_momentum_reference_world_z_m",
			"flow_angular_momentum_before_source_world_x_nm_s",
			"flow_angular_momentum_before_source_world_y_nm_s",
			"flow_angular_momentum_before_source_world_z_nm_s",
			"flow_angular_momentum_after_source_world_x_nm_s",
			"flow_angular_momentum_after_source_world_y_nm_s",
			"flow_angular_momentum_after_source_world_z_nm_s",
			"flow_angular_momentum_source_delta_world_x_nm_s",
			"flow_angular_momentum_source_delta_world_y_nm_s",
			"flow_angular_momentum_source_delta_world_z_nm_s",
			"flow_angular_momentum_source_delta_minus_wake_impulse_world_x_nm_s",
			"flow_angular_momentum_source_delta_minus_wake_impulse_world_y_nm_s",
			"flow_angular_momentum_source_delta_minus_wake_impulse_world_z_nm_s",
			"flow_angular_momentum_before_solid_boundary_world_x_nm_s",
			"flow_angular_momentum_before_solid_boundary_world_y_nm_s",
			"flow_angular_momentum_before_solid_boundary_world_z_nm_s",
			"flow_angular_momentum_after_solid_boundary_world_x_nm_s",
			"flow_angular_momentum_after_solid_boundary_world_y_nm_s",
			"flow_angular_momentum_after_solid_boundary_world_z_nm_s",
			"flow_angular_momentum_solid_boundary_delta_world_x_nm_s",
			"flow_angular_momentum_solid_boundary_delta_world_y_nm_s",
			"flow_angular_momentum_solid_boundary_delta_world_z_nm_s",
			"flow_angular_impulse_on_solid_boundary_world_x_nm_s",
			"flow_angular_impulse_on_solid_boundary_world_y_nm_s",
			"flow_angular_impulse_on_solid_boundary_world_z_nm_s"
	);

	private CtCpJLocalVoxelFlowSolverExporter() {
	}

	public record SolidBoxExportConfig(
			List<PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox> solidBoxes,
			double minimumSolidVolumeFraction
	) {
		public SolidBoxExportConfig {
			List<PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox> boxes =
					solidBoxes == null ? List.of() : solidBoxes;
			ArrayList<PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox> sanitizedBoxes =
					new ArrayList<>(boxes.size());
			for (PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox box : boxes) {
				if (box == null) {
					throw new IllegalArgumentException("solidBoxes must not contain null boxes.");
				}
				sanitizedBoxes.add(box);
			}
			solidBoxes = List.copyOf(sanitizedBoxes);
			if (!Double.isFinite(minimumSolidVolumeFraction)
					|| minimumSolidVolumeFraction < 0.0
					|| minimumSolidVolumeFraction > 1.0) {
				throw new IllegalArgumentException("minimumSolidVolumeFraction must be within [0, 1].");
			}
		}

		public static SolidBoxExportConfig open() {
			return new SolidBoxExportConfig(List.of(), 0.0);
		}

		public int solidBoxCount() {
			return solidBoxes.size();
		}

		public PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask solidMaskFor(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec
		) {
			if (solidBoxes.isEmpty()) {
				return PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.open(gridSpec);
			}
			return PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.fromWorldSolidBoxes(
					gridSpec,
					solidBoxes,
					minimumSolidVolumeFraction);
		}
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
		SolidBoxExportConfig solidBoxConfig = solidBoxExportConfig(args, 15);
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
				downstreamWakeLength,
				solidBoxConfig
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
				DEFAULT_DOWNSTREAM_WAKE_LENGTH_METERS,
				SolidBoxExportConfig.open()
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
		write(
				presetName,
				output,
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
				downstreamWakeLengthMeters,
				SolidBoxExportConfig.open()
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
			double downstreamWakeLengthMeters,
			SolidBoxExportConfig solidBoxConfig
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
				downstreamWakeLengthMeters,
				solidBoxConfig
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
				DEFAULT_DOWNSTREAM_WAKE_LENGTH_METERS,
				SolidBoxExportConfig.open()
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
		return csvLines(
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
				downstreamWakeLengthMeters,
				SolidBoxExportConfig.open()
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
			double downstreamWakeLengthMeters,
			SolidBoxExportConfig solidBoxConfig
	) {
		SolidBoxExportConfig effectiveSolidBoxConfig =
				solidBoxConfig == null ? SolidBoxExportConfig.open() : solidBoxConfig;
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
					config, paddingCells, downstreamWakeLengthMeters, effectiveSolidBoxConfig));
		}
		return List.copyOf(lines);
	}

	private static List<String> csvLinesForGroup(
			SourceGroupKey key,
			List<Map<String, String>> rows,
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config,
			int paddingCells,
			double downstreamWakeLengthMeters,
			SolidBoxExportConfig solidBoxConfig
	) {
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample =
				sourceGridSample(rows);
		PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask solidMask =
				solidBoxConfig.solidMaskFor(sourceGridSample.gridSpec());
		PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run =
				PropellerArchiveCtCpJLocalVoxelFlowSolver.run(sourceGridSample, config, solidMask);
		Map<String, String> first = rows.get(0);
		GroupMetadata metadata = metadata(
				key,
				first,
				sourceGridSample,
				config,
				paddingCells,
				downstreamWakeLengthMeters,
				solidBoxConfig);
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
		Vec3 solidOccludedSourceMomentumRate = initial
				? zero
				: metadata.sourceGridSample().integratedBodyForceWorldNewtons().subtract(sourceMomentumRate);
		Vec3 targetWakeAngularMomentumTorque =
				metadata.sourceGridSample().integratedWakeAngularMomentumTorqueWorldNewtonMeters();
		Vec3 sourceWakeAngularMomentumTorque =
				initial ? zero : iteration.sourceAdvance().totalWakeAngularMomentumTorqueWorldNewtonMeters();
		Vec3 solidOccludedSourceWakeAngularMomentumTorque =
				initial ? zero : targetWakeAngularMomentumTorque.subtract(sourceWakeAngularMomentumTorque);
		Vec3 sourceWakeAngularMomentumImpulse = initial
				? zero
				: iteration.sourceAdvance().totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds();
		Vec3 cumulativeSourceWakeAngularMomentumImpulse =
				cumulativeSourceWakeAngularMomentumImpulse(run, completedSteps);
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
		double targetIdealMomentumPower =
				metadata.sourceGridSample().integratedIdealMomentumPowerWatts(run.config().sourceThicknessMeters());
		double targetWakeSwirlKineticPower = metadata.sourceGridSample()
				.integratedWakeSwirlKineticPowerWatts(run.config().sourceThicknessMeters());
		double targetTotalWakeKineticPower = metadata.sourceGridSample()
				.integratedTotalWakeKineticPowerWatts(run.config().sourceThicknessMeters());
		double sourceIdealMomentumPower = initial ? 0.0 : iteration.sourceAdvance().totalIdealMomentumPowerWatts();
		double sourceIdealMomentumEnergy = initial ? 0.0 : iteration.sourceAdvance().idealMomentumEnergyJoules();
		double sourceWakeSwirlKineticPower = initial ? 0.0
				: iteration.sourceAdvance().totalWakeSwirlKineticPowerWatts();
		double sourceWakeSwirlKineticEnergy = initial ? 0.0
				: iteration.sourceAdvance().wakeSwirlKineticEnergyJoules();
		double sourceTotalWakeKineticPower = initial ? 0.0
				: iteration.sourceAdvance().totalWakeKineticPowerWatts();
		double sourceTotalWakeKineticEnergy = initial ? 0.0
				: iteration.sourceAdvance().totalWakeKineticEnergyJoules();
		double solidOccludedIdealMomentumPower =
				occludedPower(initial, targetIdealMomentumPower, sourceIdealMomentumPower);
		double solidOccludedWakeSwirlKineticPower =
				occludedPower(initial, targetWakeSwirlKineticPower, sourceWakeSwirlKineticPower);
		double solidOccludedTotalWakeKineticPower =
				occludedPower(initial, targetTotalWakeKineticPower, sourceTotalWakeKineticPower);
		double sourceMechanicalWorkPower = initial ? 0.0 : iteration.sourceAdvance().sourceMechanicalWorkPowerWatts();
		double sourceMechanicalWorkEnergy = initial ? 0.0 : iteration.sourceAdvance().sourceMechanicalWorkEnergyJoules();
		double throughFlowMechanicalWorkPower = initial ? 0.0
				: iteration.sourceAdvance().throughFlowMechanicalWorkPowerWatts();
		double throughFlowMechanicalWorkEnergy = initial ? 0.0
				: iteration.sourceAdvance().throughFlowMechanicalWorkEnergyJoules();
		double combinedMechanicalWorkPower = initial ? 0.0
				: iteration.sourceAdvance().combinedMechanicalWorkPowerWatts();
		double combinedMechanicalWorkEnergy = initial ? 0.0
				: iteration.sourceAdvance().combinedMechanicalWorkEnergyJoules();
		double coupledSourceForceMechanicalWorkPower = initial ? 0.0
				: iteration.sourceAdvance().coupledSourceForceMechanicalWorkPowerWatts();
		double coupledSourceForceMechanicalWorkEnergy = initial ? 0.0
				: iteration.sourceAdvance().coupledSourceForceMechanicalWorkEnergyJoules();
		double coupledWakeResidenceMechanicalWorkPower = initial ? 0.0
				: iteration.sourceAdvance().coupledWakeResidenceMechanicalWorkPowerWatts();
		double coupledWakeResidenceMechanicalWorkEnergy = initial ? 0.0
				: iteration.sourceAdvance().coupledWakeResidenceMechanicalWorkEnergyJoules();
		double combinedMechanicalWorkPowerMinusWakeKineticPower = initial ? 0.0
				: iteration.sourceAdvance().combinedMechanicalWorkPowerMinusWakeKineticPowerWatts();
		double combinedMechanicalWorkPowerOverWakeKineticPower = initial ? 0.0
				: iteration.sourceAdvance().combinedMechanicalWorkPowerOverWakeKineticPower();
		double flowKineticEnergySourceDelta = initial ? 0.0
				: iteration.sourceAdvance().flowKineticEnergyDeltaJoules(run.solidMask());
		double flowKineticEnergySourceDeltaMinusIdeal = initial ? 0.0
				: iteration.sourceAdvance().flowKineticEnergyDeltaMinusIdealMomentumEnergyJoules(run.solidMask());
		double flowKineticEnergySourceDeltaMinusTotalWake = initial ? 0.0
				: flowKineticEnergySourceDelta - sourceTotalWakeKineticEnergy;
		double flowKineticEnergySourceDeltaMinusMechanicalWork = initial ? 0.0
				: iteration.sourceAdvance().flowKineticEnergyDeltaMinusSourceMechanicalWorkJoules(run.solidMask());
		double flowKineticEnergySourceDeltaMinusCombinedMechanicalWork = initial ? 0.0
				: iteration.sourceAdvance().flowKineticEnergyDeltaMinusCombinedMechanicalWorkJoules(run.solidMask());
		double cumulativeSourceIdealMomentumEnergy =
				cumulativeSourceIdealMomentumEnergy(run, completedSteps);
		double cumulativeSourceWakeSwirlKineticEnergy =
				cumulativeSourceWakeSwirlKineticEnergy(run, completedSteps);
		double cumulativeSourceTotalWakeKineticEnergy =
				cumulativeSourceTotalWakeKineticEnergy(run, completedSteps);
		double cumulativeSourceMechanicalWorkEnergy =
				cumulativeSourceMechanicalWorkEnergy(run, completedSteps);
		double cumulativeThroughFlowMechanicalWorkEnergy =
				cumulativeThroughFlowMechanicalWorkEnergy(run, completedSteps);
		double cumulativeCombinedMechanicalWorkEnergy =
				cumulativeCombinedMechanicalWorkEnergy(run, completedSteps);
		double cumulativeCoupledSourceForceMechanicalWorkEnergy =
				cumulativeCoupledSourceForceMechanicalWorkEnergy(run, completedSteps);
		double cumulativeCoupledWakeResidenceMechanicalWorkEnergy =
				cumulativeCoupledWakeResidenceMechanicalWorkEnergy(run, completedSteps);
		double cumulativeCombinedMechanicalWorkEnergyMinusWakeKineticEnergy =
				cumulativeCombinedMechanicalWorkEnergy - cumulativeSourceTotalWakeKineticEnergy;
		double cumulativeCombinedMechanicalWorkEnergyOverWakeKineticEnergy =
				ratio(cumulativeCombinedMechanicalWorkEnergy, cumulativeSourceTotalWakeKineticEnergy);
		double cumulativeFlowKineticEnergySourceDelta =
				cumulativeFlowKineticEnergySourceDelta(run, completedSteps);
		double cumulativeFlowKineticEnergySourceDeltaMinusIdeal =
				cumulativeFlowKineticEnergySourceDeltaMinusIdeal(run, completedSteps);
		double cumulativeFlowKineticEnergySourceDeltaMinusTotalWake =
				cumulativeFlowKineticEnergySourceDeltaMinusTotalWake(run, completedSteps);
		double cumulativeFlowKineticEnergySourceDeltaMinusMechanicalWork =
				cumulativeFlowKineticEnergySourceDeltaMinusMechanicalWork(run, completedSteps);
		double cumulativeFlowKineticEnergySourceDeltaMinusCombinedMechanicalWork =
				cumulativeFlowKineticEnergySourceDeltaMinusCombinedMechanicalWork(run, completedSteps);
		double cumulativeOpenBoundaryNetOutwardMass =
				cumulativeOpenBoundaryNetOutwardMass(run, completedSteps);
		double cumulativeOpenBoundaryOutwardMass =
				cumulativeOpenBoundaryOutwardMass(run, completedSteps);
		double cumulativeOpenBoundaryInwardMass =
				cumulativeOpenBoundaryInwardMass(run, completedSteps);
		Vec3 cumulativeOpenBoundaryNetOutwardImpulse =
				cumulativeOpenBoundaryNetOutwardImpulse(run, completedSteps);
		Vec3 cumulativeOpenBoundaryNetOutwardAngularImpulse =
				cumulativeOpenBoundaryNetOutwardAngularImpulse(run, completedSteps);
		double cumulativeOpenBoundaryNetOutwardKineticEnergy =
				cumulativeOpenBoundaryNetOutwardKineticEnergy(run, completedSteps);
		double cumulativeOpenBoundaryOutwardKineticEnergy =
				cumulativeOpenBoundaryOutwardKineticEnergy(run, completedSteps);
		double cumulativeOpenBoundaryInwardKineticEnergy =
				cumulativeOpenBoundaryInwardKineticEnergy(run, completedSteps);
		double cumulativeViscousDissipatedEnergy =
				cumulativeViscousDissipatedEnergy(run, completedSteps);
		double maxResidenceAlpha = initial ? 0.0 : iteration.sourceAdvance().maxResidenceAlpha();
		double meanWakeResidual = initial ? 0.0
				: iteration.sourceAdvance().meanActiveWakeResidualAfterResidenceMetersPerSecond();
		double massFlowWeightedWakeResidual = initial ? 0.0
				: iteration.sourceAdvance().massFlowWeightedWakeResidualAfterResidenceMetersPerSecond();
		double advectionCourantNumber = initial ? 0.0 : iteration.advectionRun().maxCourantNumber();
		int advectionSubstepCount = initial ? 0 : iteration.advectionRun().completedSubstepCount();
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics divergenceBeforeProjection =
				initial ? new PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics(0.0, 0.0, 0.0)
						: iteration.projectionStep().divergenceBefore();
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics divergenceAfterProjection =
				initial ? new PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceMetrics(0.0, 0.0, 0.0)
						: iteration.projectionStep().divergenceAfter();
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceIntegralMetrics divergenceIntegralsBeforeProjection =
				initial ? new PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceIntegralMetrics(0.0, 0.0, 0.0)
						: iteration.projectionStep().previousState().divergenceIntegralMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceIntegralMetrics divergenceIntegralsAfterProjection =
				initial ? new PropellerArchiveCtCpJLocalVoxelFlowState.DivergenceIntegralMetrics(0.0, 0.0, 0.0)
						: iteration.projectionStep().nextState().divergenceIntegralMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics boundaryFluxAfterSource =
				initial ? run.initialState().openBoundaryFluxMetrics(
						run.config().airDensityKgPerCubicMeter(), run.solidMask())
						: iteration.stateAfterSource().openBoundaryFluxMetrics(
								run.config().airDensityKgPerCubicMeter(), run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics boundaryFluxAfterProjection =
				initial ? boundaryFluxAfterSource
						: iteration.stateAfterProjection().openBoundaryFluxMetrics(
								run.config().airDensityKgPerCubicMeter(), run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics boundaryFluxAfterSolidBoundary =
				initial ? boundaryFluxAfterProjection
						: iteration.stateAfterSolidBoundary().openBoundaryFluxMetrics(
								run.config().airDensityKgPerCubicMeter(), run.solidMask());
		Vec3 boundaryMomentumAfterSource = boundaryFluxAfterSource.netOutwardMomentumFluxWorldNewtons();
		Vec3 boundaryMomentumAfterProjection = boundaryFluxAfterProjection.netOutwardMomentumFluxWorldNewtons();
		Vec3 boundaryMomentumAfterSolidBoundary =
				boundaryFluxAfterSolidBoundary.netOutwardMomentumFluxWorldNewtons();
		Vec3 boundaryAngularMomentumAfterSource =
				boundaryFluxAfterSource.netOutwardAngularMomentumFluxWorldNewtonMeters();
		Vec3 boundaryAngularMomentumAfterProjection =
				boundaryFluxAfterProjection.netOutwardAngularMomentumFluxWorldNewtonMeters();
		Vec3 boundaryAngularMomentumAfterSolidBoundary =
				boundaryFluxAfterSolidBoundary.netOutwardAngularMomentumFluxWorldNewtonMeters();
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityMetrics vorticityAfterSource =
				initial ? run.initialState().vorticityMetrics(run.solidMask())
						: iteration.stateAfterSource().vorticityMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityMetrics vorticityAfterProjection =
				initial ? vorticityAfterSource
						: iteration.stateAfterProjection().vorticityMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityMetrics vorticityAfterSolidBoundary =
				initial ? vorticityAfterProjection
						: iteration.stateAfterSolidBoundary().vorticityMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityIntegralMetrics vorticityIntegralsAfterSource =
				initial ? run.initialState().vorticityIntegralMetrics(run.solidMask())
						: iteration.stateAfterSource().vorticityIntegralMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityIntegralMetrics vorticityIntegralsAfterProjection =
				initial ? vorticityIntegralsAfterSource
						: iteration.stateAfterProjection().vorticityIntegralMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.VorticityIntegralMetrics vorticityIntegralsAfterSolidBoundary =
				initial ? vorticityIntegralsAfterProjection
						: iteration.stateAfterSolidBoundary().vorticityIntegralMetrics(run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.KineticEnergyDistributionMetrics energyDistributionAfterSource =
				initial ? run.initialState().kineticEnergyDistributionMetrics(
						run.config().airDensityKgPerCubicMeter(), run.solidMask())
						: iteration.stateAfterSource().kineticEnergyDistributionMetrics(
								run.config().airDensityKgPerCubicMeter(), run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.KineticEnergyDistributionMetrics energyDistributionAfterProjection =
				initial ? energyDistributionAfterSource
						: iteration.stateAfterProjection().kineticEnergyDistributionMetrics(
								run.config().airDensityKgPerCubicMeter(), run.solidMask());
		PropellerArchiveCtCpJLocalVoxelFlowState.KineticEnergyDistributionMetrics
				energyDistributionAfterSolidBoundary =
						initial ? energyDistributionAfterProjection
								: iteration.stateAfterSolidBoundary().kineticEnergyDistributionMetrics(
										run.config().airDensityKgPerCubicMeter(), run.solidMask());
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
		double viscousDissipatedEnergy = initial ? 0.0
				: iteration.diffusionStep().viscousDissipatedEnergyJoules();
		double meanViscousDissipationPower = initial ? 0.0
				: iteration.diffusionStep().meanViscousDissipationPowerWatts();
		double energyAfterProjection = initial ? energyAfterDiffusion
				: iteration.projectionStep().kineticEnergyAfterJoules();
		double energyProjectionDelta = initial ? 0.0 : iteration.projectionStep().kineticEnergyDeltaJoules();
		double energyAfterSolidBoundary = initial ? energyAfterProjection
				: iteration.solidBoundaryStep().kineticEnergyAfterJoules();
		double energySolidBoundaryDelta = initial ? 0.0
				: iteration.solidBoundaryStep().kineticEnergyDeltaJoules();
		double solidBoundaryDissipatedEnergy = initial ? 0.0
				: iteration.solidBoundaryStep().dissipatedKineticEnergyJoules();
		double cumulativeSolidBoundaryDissipatedEnergy =
				cumulativeSolidBoundaryDissipatedEnergy(run, completedSteps);
		double cumulativeOpenBoundaryOutwardMassOverSourceMass =
				ratio(cumulativeOpenBoundaryOutwardMass, cumulativeSourceMass);
		double cumulativeOpenBoundaryNetOutwardMassOverSourceMass =
				ratio(cumulativeOpenBoundaryNetOutwardMass, cumulativeSourceMass);
		double kineticEnergyAfterSolidBoundaryOverSourceWakeEnergy =
				ratio(energyAfterSolidBoundary - run.initialKineticEnergyJoules(),
						cumulativeSourceTotalWakeKineticEnergy);
		double cumulativeOpenBoundaryNetOutwardKineticEnergyOverSourceWakeEnergy =
				ratio(cumulativeOpenBoundaryNetOutwardKineticEnergy, cumulativeSourceTotalWakeKineticEnergy);
		double retainedPlusBoundaryNetOutwardKineticEnergyOverSourceWakeEnergy =
				ratio(energyAfterSolidBoundary
								- run.initialKineticEnergyJoules()
								+ cumulativeOpenBoundaryNetOutwardKineticEnergy,
						cumulativeSourceTotalWakeKineticEnergy);
		double maxSpeedAfterSource = initial ? run.initialState().maxSpeedMetersPerSecond(run.solidMask())
				: iteration.stateAfterSource().maxSpeedMetersPerSecond(run.solidMask());
		double maxSpeedAfterAdvection = initial ? maxSpeedAfterSource
				: iteration.stateAfterAdvection().maxSpeedMetersPerSecond(run.solidMask());
		double maxSpeedAfterDiffusion = initial ? run.initialState().maxSpeedMetersPerSecond(run.solidMask())
				: iteration.stateAfterDiffusion().maxSpeedMetersPerSecond(run.solidMask());
		double maxSpeedAfterProjection = initial ? maxSpeedAfterDiffusion
				: iteration.stateAfterProjection().maxSpeedMetersPerSecond(run.solidMask());
		double maxSpeedAfterSolidBoundary = initial ? maxSpeedAfterProjection
				: iteration.stateAfterSolidBoundary().maxSpeedMetersPerSecond(run.solidMask());
		Vec3 advectionMomentumBefore = initial ? run.initialState()
				.totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter(), run.solidMask())
				: iteration.advectionRun().totalMomentumBeforeWorldNewtonSeconds();
		Vec3 advectionMomentumAfter = initial ? advectionMomentumBefore
				: iteration.advectionRun().totalMomentumAfterWorldNewtonSeconds();
		Vec3 advectionMomentumResidual = initial ? zero : iteration.advectionRun().momentumResidualWorldNewtonSeconds();
		Vec3 momentumBeforeDiffusion = initial ? run.initialState()
				.totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter(), run.solidMask())
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
		Vec3 flowImpulseOnSolidBoundary = initial ? zero
				: iteration.solidBoundaryStep().flowImpulseOnSolidBoundaryWorldNewtonSeconds();
		Vec3 finalMomentum = initial ? run.initialState()
				.totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter(), run.solidMask())
				: iteration.stateAfterSolidBoundary()
						.totalMomentumWorldNewtonSeconds(run.config().airDensityKgPerCubicMeter(), run.solidMask());
		Vec3 angularMomentumReference = metadata.sourceGridSample().gridSpec().gridCenterWorldMeters();
		Vec3 angularMomentumBeforeSource = initial ? run.initialAngularMomentumWorldNewtonMeterSeconds(
				angularMomentumReference)
				: iteration.stateBeforeStep().totalAngularMomentumWorldNewtonMeterSeconds(
						run.config().airDensityKgPerCubicMeter(),
						angularMomentumReference,
						run.solidMask());
		Vec3 angularMomentumAfterSource = initial ? run.initialAngularMomentumWorldNewtonMeterSeconds(
				angularMomentumReference)
				: iteration.stateAfterSource().totalAngularMomentumWorldNewtonMeterSeconds(
						run.config().airDensityKgPerCubicMeter(),
						angularMomentumReference,
						run.solidMask());
		Vec3 angularMomentumSourceDelta =
				initial ? zero : angularMomentumAfterSource.subtract(angularMomentumBeforeSource);
		Vec3 angularMomentumSourceDeltaMinusWakeImpulse =
				initial ? zero : angularMomentumSourceDelta.subtract(sourceWakeAngularMomentumImpulse);
		Vec3 angularMomentumBeforeSolidBoundary = initial ? angularMomentumAfterSource
				: iteration.solidBoundaryStep().angularMomentumBeforeWorldNewtonMeterSeconds(
						angularMomentumReference);
		Vec3 angularMomentumAfterSolidBoundary = initial ? angularMomentumBeforeSolidBoundary
				: iteration.solidBoundaryStep().angularMomentumAfterWorldNewtonMeterSeconds(
						angularMomentumReference);
		Vec3 angularMomentumSolidBoundaryDelta = initial ? zero
				: iteration.solidBoundaryStep().angularMomentumResidualWorldNewtonMeterSeconds(
						angularMomentumReference);
		Vec3 flowAngularImpulseOnSolidBoundary = initial ? zero
				: iteration.solidBoundaryStep().flowAngularImpulseOnSolidBoundaryWorldNewtonMeterSeconds(
						angularMomentumReference);
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
				Integer.toString(metadata.solidBoxConfig().solidBoxCount()),
				number(metadata.solidBoxConfig().minimumSolidVolumeFraction()),
				Integer.toString(solidOccludedSourceCellCount(metadata.sourceGridSample(), run.solidMask())),
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
				number(solidOccludedSourceMomentumRate.x()),
				number(solidOccludedSourceMomentumRate.y()),
				number(solidOccludedSourceMomentumRate.z()),
				number(targetWakeAngularMomentumTorque.x()),
				number(targetWakeAngularMomentumTorque.y()),
				number(targetWakeAngularMomentumTorque.z()),
				number(sourceWakeAngularMomentumTorque.x()),
				number(sourceWakeAngularMomentumTorque.y()),
				number(sourceWakeAngularMomentumTorque.z()),
				number(solidOccludedSourceWakeAngularMomentumTorque.x()),
				number(solidOccludedSourceWakeAngularMomentumTorque.y()),
				number(solidOccludedSourceWakeAngularMomentumTorque.z()),
				number(sourceWakeAngularMomentumImpulse.x()),
				number(sourceWakeAngularMomentumImpulse.y()),
				number(sourceWakeAngularMomentumImpulse.z()),
				number(cumulativeSourceWakeAngularMomentumImpulse.x()),
				number(cumulativeSourceWakeAngularMomentumImpulse.y()),
				number(cumulativeSourceWakeAngularMomentumImpulse.z()),
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
				number(targetIdealMomentumPower),
				number(targetWakeSwirlKineticPower),
				number(targetTotalWakeKineticPower),
				number(sourceIdealMomentumPower),
				number(sourceIdealMomentumEnergy),
				number(sourceWakeSwirlKineticPower),
				number(sourceWakeSwirlKineticEnergy),
				number(sourceTotalWakeKineticPower),
				number(sourceTotalWakeKineticEnergy),
				number(solidOccludedIdealMomentumPower),
				number(solidOccludedWakeSwirlKineticPower),
				number(solidOccludedTotalWakeKineticPower),
				number(sourceMechanicalWorkPower),
				number(sourceMechanicalWorkEnergy),
				number(throughFlowMechanicalWorkPower),
				number(throughFlowMechanicalWorkEnergy),
				number(combinedMechanicalWorkPower),
				number(combinedMechanicalWorkEnergy),
				number(coupledSourceForceMechanicalWorkPower),
				number(coupledSourceForceMechanicalWorkEnergy),
				number(coupledWakeResidenceMechanicalWorkPower),
				number(coupledWakeResidenceMechanicalWorkEnergy),
				number(combinedMechanicalWorkPowerMinusWakeKineticPower),
				number(combinedMechanicalWorkPowerOverWakeKineticPower),
				number(flowKineticEnergySourceDelta),
				number(flowKineticEnergySourceDeltaMinusIdeal),
				number(flowKineticEnergySourceDeltaMinusTotalWake),
				number(flowKineticEnergySourceDeltaMinusMechanicalWork),
				number(flowKineticEnergySourceDeltaMinusCombinedMechanicalWork),
				number(cumulativeSourceIdealMomentumEnergy),
				number(cumulativeSourceWakeSwirlKineticEnergy),
				number(cumulativeSourceTotalWakeKineticEnergy),
				number(cumulativeSourceMechanicalWorkEnergy),
				number(cumulativeThroughFlowMechanicalWorkEnergy),
				number(cumulativeCombinedMechanicalWorkEnergy),
				number(cumulativeCoupledSourceForceMechanicalWorkEnergy),
				number(cumulativeCoupledWakeResidenceMechanicalWorkEnergy),
				number(cumulativeCombinedMechanicalWorkEnergyMinusWakeKineticEnergy),
				number(cumulativeCombinedMechanicalWorkEnergyOverWakeKineticEnergy),
				number(cumulativeFlowKineticEnergySourceDelta),
				number(cumulativeFlowKineticEnergySourceDeltaMinusIdeal),
				number(cumulativeFlowKineticEnergySourceDeltaMinusTotalWake),
				number(cumulativeFlowKineticEnergySourceDeltaMinusMechanicalWork),
				number(cumulativeFlowKineticEnergySourceDeltaMinusCombinedMechanicalWork),
				number(cumulativeOpenBoundaryNetOutwardMass),
				number(cumulativeOpenBoundaryOutwardMass),
				number(cumulativeOpenBoundaryInwardMass),
				number(cumulativeOpenBoundaryNetOutwardImpulse.x()),
				number(cumulativeOpenBoundaryNetOutwardImpulse.y()),
				number(cumulativeOpenBoundaryNetOutwardImpulse.z()),
				number(cumulativeOpenBoundaryNetOutwardAngularImpulse.x()),
				number(cumulativeOpenBoundaryNetOutwardAngularImpulse.y()),
				number(cumulativeOpenBoundaryNetOutwardAngularImpulse.z()),
				number(cumulativeOpenBoundaryNetOutwardKineticEnergy),
				number(cumulativeOpenBoundaryOutwardKineticEnergy),
				number(cumulativeOpenBoundaryInwardKineticEnergy),
				number(cumulativeOpenBoundaryOutwardMassOverSourceMass),
				number(cumulativeOpenBoundaryNetOutwardMassOverSourceMass),
				number(kineticEnergyAfterSolidBoundaryOverSourceWakeEnergy),
				number(cumulativeOpenBoundaryNetOutwardKineticEnergyOverSourceWakeEnergy),
				number(retainedPlusBoundaryNetOutwardKineticEnergyOverSourceWakeEnergy),
				number(maxResidenceAlpha),
				number(meanWakeResidual),
				number(massFlowWeightedWakeResidual),
				number(divergenceBeforeProjection.maxAbsDivergencePerSecond()),
				number(divergenceBeforeProjection.rmsDivergencePerSecond()),
				number(divergenceBeforeProjection.meanDivergencePerSecond()),
				number(divergenceIntegralsBeforeProjection.netVolumeFlowRateCubicMetersPerSecond()),
				number(divergenceIntegralsBeforeProjection.grossAbsVolumeFlowRateCubicMetersPerSecond()),
				number(divergenceIntegralsBeforeProjection.netMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(divergenceIntegralsBeforeProjection.grossAbsMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(divergenceAfterProjection.maxAbsDivergencePerSecond()),
				number(divergenceAfterProjection.rmsDivergencePerSecond()),
				number(divergenceAfterProjection.meanDivergencePerSecond()),
				number(divergenceIntegralsAfterProjection.netVolumeFlowRateCubicMetersPerSecond()),
				number(divergenceIntegralsAfterProjection.grossAbsVolumeFlowRateCubicMetersPerSecond()),
				number(divergenceIntegralsAfterProjection.netMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(divergenceIntegralsAfterProjection.grossAbsMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryFluxAfterSource.netOutwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterSource.outwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterSource.inwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterSource.netOutwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryFluxAfterSource.outwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryFluxAfterSource.inwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryMomentumAfterSource.x()),
				number(boundaryMomentumAfterSource.y()),
				number(boundaryMomentumAfterSource.z()),
				number(boundaryAngularMomentumAfterSource.x()),
				number(boundaryAngularMomentumAfterSource.y()),
				number(boundaryAngularMomentumAfterSource.z()),
				number(boundaryFluxAfterSource.netOutwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterSource.outwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterSource.inwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterProjection.netOutwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterProjection.outwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterProjection.inwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterProjection.netOutwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryFluxAfterProjection.outwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryFluxAfterProjection.inwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryMomentumAfterProjection.x()),
				number(boundaryMomentumAfterProjection.y()),
				number(boundaryMomentumAfterProjection.z()),
				number(boundaryAngularMomentumAfterProjection.x()),
				number(boundaryAngularMomentumAfterProjection.y()),
				number(boundaryAngularMomentumAfterProjection.z()),
				number(boundaryFluxAfterProjection.netOutwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterProjection.outwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterProjection.inwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterSolidBoundary.netOutwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterSolidBoundary.outwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterSolidBoundary.inwardVolumeFlowRateCubicMetersPerSecond()),
				number(boundaryFluxAfterSolidBoundary.netOutwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryFluxAfterSolidBoundary.outwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryFluxAfterSolidBoundary.inwardMassFlowRateKilogramsPerSecond(
						run.config().airDensityKgPerCubicMeter())),
				number(boundaryMomentumAfterSolidBoundary.x()),
				number(boundaryMomentumAfterSolidBoundary.y()),
				number(boundaryMomentumAfterSolidBoundary.z()),
				number(boundaryAngularMomentumAfterSolidBoundary.x()),
				number(boundaryAngularMomentumAfterSolidBoundary.y()),
				number(boundaryAngularMomentumAfterSolidBoundary.z()),
				number(boundaryFluxAfterSolidBoundary.netOutwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterSolidBoundary.outwardKineticEnergyPowerWatts()),
				number(boundaryFluxAfterSolidBoundary.inwardKineticEnergyPowerWatts()),
				number(vorticityAfterSource.maxMagnitudePerSecond()),
				number(vorticityAfterSource.rmsMagnitudePerSecond()),
				number(vorticityAfterSource.meanVorticityWorldPerSecond().x()),
				number(vorticityAfterSource.meanVorticityWorldPerSecond().y()),
				number(vorticityAfterSource.meanVorticityWorldPerSecond().z()),
				number(vorticityIntegralsAfterSource.enstrophyCubicMetersPerSecondSquared()),
				number(vorticityIntegralsAfterSource.helicityFourthMetersPerSecondSquared()),
				number(vorticityIntegralsAfterSource.meanHelicityDensityMetersPerSecondSquared()),
				number(vorticityAfterProjection.maxMagnitudePerSecond()),
				number(vorticityAfterProjection.rmsMagnitudePerSecond()),
				number(vorticityAfterProjection.meanVorticityWorldPerSecond().x()),
				number(vorticityAfterProjection.meanVorticityWorldPerSecond().y()),
				number(vorticityAfterProjection.meanVorticityWorldPerSecond().z()),
				number(vorticityIntegralsAfterProjection.enstrophyCubicMetersPerSecondSquared()),
				number(vorticityIntegralsAfterProjection.helicityFourthMetersPerSecondSquared()),
				number(vorticityIntegralsAfterProjection.meanHelicityDensityMetersPerSecondSquared()),
				number(vorticityAfterSolidBoundary.maxMagnitudePerSecond()),
				number(vorticityAfterSolidBoundary.rmsMagnitudePerSecond()),
				number(vorticityAfterSolidBoundary.meanVorticityWorldPerSecond().x()),
				number(vorticityAfterSolidBoundary.meanVorticityWorldPerSecond().y()),
				number(vorticityAfterSolidBoundary.meanVorticityWorldPerSecond().z()),
				number(vorticityIntegralsAfterSolidBoundary.enstrophyCubicMetersPerSecondSquared()),
				number(vorticityIntegralsAfterSolidBoundary.helicityFourthMetersPerSecondSquared()),
				number(vorticityIntegralsAfterSolidBoundary.meanHelicityDensityMetersPerSecondSquared()),
				number(energyDistributionAfterSource.centroidWorldMeters().x()),
				number(energyDistributionAfterSource.centroidWorldMeters().y()),
				number(energyDistributionAfterSource.centroidWorldMeters().z()),
				number(energyDistributionAfterSource.rmsRadiusMeters()),
				number(energyDistributionAfterProjection.centroidWorldMeters().x()),
				number(energyDistributionAfterProjection.centroidWorldMeters().y()),
				number(energyDistributionAfterProjection.centroidWorldMeters().z()),
				number(energyDistributionAfterProjection.rmsRadiusMeters()),
				number(energyDistributionAfterSolidBoundary.centroidWorldMeters().x()),
				number(energyDistributionAfterSolidBoundary.centroidWorldMeters().y()),
				number(energyDistributionAfterSolidBoundary.centroidWorldMeters().z()),
				number(energyDistributionAfterSolidBoundary.rmsRadiusMeters()),
				number(energyBeforeSource),
				number(energyAfterSource),
				number(energyAfterAdvection),
				number(energyAdvectionDelta),
				number(energyAfterDiffusion),
				number(energyDiffusionDelta),
				number(viscousDissipatedEnergy),
				number(meanViscousDissipationPower),
				number(cumulativeViscousDissipatedEnergy),
				number(energyAfterProjection),
				number(energyProjectionDelta),
				number(energyAfterSolidBoundary),
				number(energySolidBoundaryDelta),
				number(solidBoundaryDissipatedEnergy),
				number(cumulativeSolidBoundaryDissipatedEnergy),
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
				number(flowImpulseOnSolidBoundary.x()),
				number(flowImpulseOnSolidBoundary.y()),
				number(flowImpulseOnSolidBoundary.z()),
				number(finalMomentum.x()),
				number(finalMomentum.y()),
				number(finalMomentum.z()),
				number(angularMomentumReference.x()),
				number(angularMomentumReference.y()),
				number(angularMomentumReference.z()),
				number(angularMomentumBeforeSource.x()),
				number(angularMomentumBeforeSource.y()),
				number(angularMomentumBeforeSource.z()),
				number(angularMomentumAfterSource.x()),
				number(angularMomentumAfterSource.y()),
				number(angularMomentumAfterSource.z()),
				number(angularMomentumSourceDelta.x()),
				number(angularMomentumSourceDelta.y()),
				number(angularMomentumSourceDelta.z()),
				number(angularMomentumSourceDeltaMinusWakeImpulse.x()),
				number(angularMomentumSourceDeltaMinusWakeImpulse.y()),
				number(angularMomentumSourceDeltaMinusWakeImpulse.z()),
				number(angularMomentumBeforeSolidBoundary.x()),
				number(angularMomentumBeforeSolidBoundary.y()),
				number(angularMomentumBeforeSolidBoundary.z()),
				number(angularMomentumAfterSolidBoundary.x()),
				number(angularMomentumAfterSolidBoundary.y()),
				number(angularMomentumAfterSolidBoundary.z()),
				number(angularMomentumSolidBoundaryDelta.x()),
				number(angularMomentumSolidBoundaryDelta.y()),
				number(angularMomentumSolidBoundaryDelta.z()),
				number(flowAngularImpulseOnSolidBoundary.x()),
				number(flowAngularImpulseOnSolidBoundary.y()),
				number(flowAngularImpulseOnSolidBoundary.z())
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

	private static double occludedPower(boolean initial, double targetPowerWatts, double appliedPowerWatts) {
		if (initial) {
			return 0.0;
		}
		double residual = targetPowerWatts - appliedPowerWatts;
		return Double.isFinite(residual) && residual > 0.0 ? residual : 0.0;
	}

	private static Vec3 cumulativeSourceWakeAngularMomentumImpulse(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(run.iterations().get(i).sourceAdvance()
					.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds());
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

	private static double cumulativeSolidBoundaryDissipatedEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).solidBoundaryStep().dissipatedKineticEnergyJoules();
		}
		return energy;
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

	private static double cumulativeSourceIdealMomentumEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance().idealMomentumEnergyJoules();
		}
		return energy;
	}

	private static double cumulativeSourceWakeSwirlKineticEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance().wakeSwirlKineticEnergyJoules();
		}
		return energy;
	}

	private static double cumulativeSourceTotalWakeKineticEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance().totalWakeKineticEnergyJoules();
		}
		return energy;
	}

	private static double cumulativeSourceMechanicalWorkEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance().sourceMechanicalWorkEnergyJoules();
		}
		return energy;
	}

	private static double cumulativeThroughFlowMechanicalWorkEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance().throughFlowMechanicalWorkEnergyJoules();
		}
		return energy;
	}

	private static double cumulativeCombinedMechanicalWorkEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		return cumulativeSourceMechanicalWorkEnergy(run, completedSteps)
				+ cumulativeThroughFlowMechanicalWorkEnergy(run, completedSteps);
	}

	private static double cumulativeCoupledSourceForceMechanicalWorkEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance()
					.coupledSourceForceMechanicalWorkEnergyJoules();
		}
		return energy;
	}

	private static double cumulativeCoupledWakeResidenceMechanicalWorkEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance()
					.coupledWakeResidenceMechanicalWorkEnergyJoules();
		}
		return energy;
	}

	private static double cumulativeFlowKineticEnergySourceDelta(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).sourceAdvance().flowKineticEnergyDeltaJoules(run.solidMask());
		}
		return energy;
	}

	private static double cumulativeFlowKineticEnergySourceDeltaMinusIdeal(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		return cumulativeFlowKineticEnergySourceDelta(run, completedSteps)
				- cumulativeSourceIdealMomentumEnergy(run, completedSteps);
	}

	private static double cumulativeFlowKineticEnergySourceDeltaMinusTotalWake(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		return cumulativeFlowKineticEnergySourceDelta(run, completedSteps)
				- cumulativeSourceTotalWakeKineticEnergy(run, completedSteps);
	}

	private static double cumulativeFlowKineticEnergySourceDeltaMinusMechanicalWork(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		return cumulativeFlowKineticEnergySourceDelta(run, completedSteps)
				- cumulativeSourceMechanicalWorkEnergy(run, completedSteps);
	}

	private static double cumulativeFlowKineticEnergySourceDeltaMinusCombinedMechanicalWork(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		return cumulativeFlowKineticEnergySourceDelta(run, completedSteps)
				- cumulativeCombinedMechanicalWorkEnergy(run, completedSteps);
	}

	private static double cumulativeOpenBoundaryNetOutwardMass(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double mass = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			mass += openBoundaryFluxAfterSolidBoundary(run, i)
					.netOutwardMassFlowRateKilogramsPerSecond(run.config().airDensityKgPerCubicMeter())
					* run.config().timeStepSeconds();
		}
		return mass;
	}

	private static double cumulativeOpenBoundaryOutwardMass(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double mass = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			mass += openBoundaryFluxAfterSolidBoundary(run, i)
					.outwardMassFlowRateKilogramsPerSecond(run.config().airDensityKgPerCubicMeter())
					* run.config().timeStepSeconds();
		}
		return mass;
	}

	private static double cumulativeOpenBoundaryInwardMass(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double mass = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			mass += openBoundaryFluxAfterSolidBoundary(run, i)
					.inwardMassFlowRateKilogramsPerSecond(run.config().airDensityKgPerCubicMeter())
					* run.config().timeStepSeconds();
		}
		return mass;
	}

	private static Vec3 cumulativeOpenBoundaryNetOutwardImpulse(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(openBoundaryFluxAfterSolidBoundary(run, i)
					.netOutwardMomentumFluxWorldNewtons()
					.multiply(run.config().timeStepSeconds()));
		}
		return sum;
	}

	private static Vec3 cumulativeOpenBoundaryNetOutwardAngularImpulse(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			sum = sum.add(openBoundaryFluxAfterSolidBoundary(run, i)
					.netOutwardAngularMomentumFluxWorldNewtonMeters()
					.multiply(run.config().timeStepSeconds()));
		}
		return sum;
	}

	private static double cumulativeOpenBoundaryNetOutwardKineticEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += openBoundaryFluxAfterSolidBoundary(run, i)
					.netOutwardKineticEnergyPowerWatts()
					* run.config().timeStepSeconds();
		}
		return energy;
	}

	private static double cumulativeOpenBoundaryOutwardKineticEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += openBoundaryFluxAfterSolidBoundary(run, i)
					.outwardKineticEnergyPowerWatts()
					* run.config().timeStepSeconds();
		}
		return energy;
	}

	private static double cumulativeOpenBoundaryInwardKineticEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += openBoundaryFluxAfterSolidBoundary(run, i)
					.inwardKineticEnergyPowerWatts()
					* run.config().timeStepSeconds();
		}
		return energy;
	}

	private static PropellerArchiveCtCpJLocalVoxelFlowState.OpenBoundaryFluxMetrics openBoundaryFluxAfterSolidBoundary(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int iterationIndex
	) {
		return run.iterations().get(iterationIndex)
				.stateAfterSolidBoundary()
				.openBoundaryFluxMetrics(
						run.config().airDensityKgPerCubicMeter(),
						run.solidMask()
				);
	}

	private static double cumulativeViscousDissipatedEnergy(
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverRun run,
			int completedSteps
	) {
		double energy = 0.0;
		for (int i = 0; i < completedSteps && i < run.iterations().size(); i++) {
			energy += run.iterations().get(i).diffusionStep().viscousDissipatedEnergyJoules();
		}
		return energy;
	}

	private static int solidOccludedSourceCellCount(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask solidMask
	) {
		int count = 0;
		for (int cellIndex = 0; cellIndex < sourceGridSample.cells().size(); cellIndex++) {
			if (sourceGridSample.cells().get(cellIndex).active()
					&& solidMask.solidVolumeFractionCellIndex(cellIndex) > EPSILON) {
				count++;
			}
		}
		return count;
	}

	private static GroupMetadata metadata(
			SourceGroupKey key,
			Map<String, String> first,
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config,
			int paddingCells,
			double downstreamWakeLengthMeters,
			SolidBoxExportConfig solidBoxConfig
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
				config,
				solidBoxConfig
		);
	}

	private static SolidBoxExportConfig solidBoxExportConfig(String[] args, int firstIndex) {
		if (args.length > firstIndex && solidBoxListArgument(args[firstIndex])) {
			for (int i = firstIndex + 2; i < args.length; i++) {
				if (!args[i].isBlank()) {
					throw new IllegalArgumentException(
							"solidBoxes list argument accepts only an optional minimum solid volume threshold.");
				}
			}
			double minimumSolidVolumeFraction =
					args.length > firstIndex + 1 && !args[firstIndex + 1].isBlank()
							? Double.parseDouble(args[firstIndex + 1])
							: 0.0;
			return new SolidBoxExportConfig(
					parseSolidBoxList(args[firstIndex]),
					minimumSolidVolumeFraction);
		}
		int lastValueIndexExclusive = args.length;
		while (lastValueIndexExclusive > firstIndex && args[lastValueIndexExclusive - 1].isBlank()) {
			lastValueIndexExclusive--;
		}
		if (lastValueIndexExclusive <= firstIndex) {
			return SolidBoxExportConfig.open();
		}
		ArrayList<String> providedValues = new ArrayList<>(lastValueIndexExclusive - firstIndex);
		for (int i = firstIndex; i < lastValueIndexExclusive; i++) {
			if (args[i].isBlank()) {
				throw new IllegalArgumentException(
						"solid box export requires contiguous bounds; blank values are allowed only after all boxes.");
			}
			providedValues.add(args[i].trim());
		}
		if (providedValues.size() < 6) {
			throw new IllegalArgumentException(
					"solid box export requires six bounds per box.");
		}
		boolean hasTrailingThreshold = providedValues.size() % 6 == 1;
		int valueCount = hasTrailingThreshold ? providedValues.size() - 1 : providedValues.size();
		if (valueCount == 0 || valueCount % 6 != 0) {
			throw new IllegalArgumentException(
					"solid box export requires repeated minX,minY,minZ,maxX,maxY,maxZ bounds, plus optional threshold.");
		}
		ArrayList<PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox> boxes =
				new ArrayList<>(valueCount / 6);
		for (int i = 0; i < valueCount; i += 6) {
			boxes.add(worldSolidBox(
					Double.parseDouble(providedValues.get(i)),
					Double.parseDouble(providedValues.get(i + 1)),
					Double.parseDouble(providedValues.get(i + 2)),
					Double.parseDouble(providedValues.get(i + 3)),
					Double.parseDouble(providedValues.get(i + 4)),
					Double.parseDouble(providedValues.get(i + 5))));
		}
		double minimumSolidVolumeFraction = hasTrailingThreshold
				? Double.parseDouble(providedValues.get(providedValues.size() - 1))
				: 0.0;
		return new SolidBoxExportConfig(boxes, minimumSolidVolumeFraction);
	}

	private static boolean solidBoxListArgument(String value) {
		return value != null && (value.contains(",") || value.contains(";"));
	}

	private static List<PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox> parseSolidBoxList(
			String solidBoxes
	) {
		if (solidBoxes == null || solidBoxes.isBlank()) {
			return List.of();
		}
		ArrayList<PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox> boxes =
				new ArrayList<>();
		for (String boxText : solidBoxes.split(";")) {
			if (boxText.isBlank()) {
				continue;
			}
			String[] values = boxText.trim().split(",");
			if (values.length != 6) {
				throw new IllegalArgumentException(
						"solidBoxes entries must be minX,minY,minZ,maxX,maxY,maxZ.");
			}
			boxes.add(worldSolidBox(
					Double.parseDouble(values[0].trim()),
					Double.parseDouble(values[1].trim()),
					Double.parseDouble(values[2].trim()),
					Double.parseDouble(values[3].trim()),
					Double.parseDouble(values[4].trim()),
					Double.parseDouble(values[5].trim())));
		}
		return List.copyOf(boxes);
	}

	private static PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox worldSolidBox(
			double minX,
			double minY,
			double minZ,
			double maxX,
			double maxY,
			double maxZ
	) {
		return new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
				new Vec3(minX, minY, minZ),
				new Vec3(maxX, maxY, maxZ));
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
				vector(row,
						"actuator_disk_axial_velocity_world_x_mps",
						"actuator_disk_axial_velocity_world_y_mps",
						"actuator_disk_axial_velocity_world_z_mps"),
				number(row, "ideal_momentum_power_loading_w_m2"),
				number(row, "wake_swirl_kinetic_power_loading_w_m2"),
				number(row, "total_wake_kinetic_power_loading_w_m2"),
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

	private static double ratio(double numerator, double denominator) {
		return Double.isFinite(numerator)
				&& Double.isFinite(denominator)
				&& Math.abs(denominator) > EPSILON
				? numerator / denominator
				: 0.0;
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
			PropellerArchiveCtCpJLocalVoxelFlowSolver.SolverConfig config,
			SolidBoxExportConfig solidBoxConfig
	) {
	}
}
