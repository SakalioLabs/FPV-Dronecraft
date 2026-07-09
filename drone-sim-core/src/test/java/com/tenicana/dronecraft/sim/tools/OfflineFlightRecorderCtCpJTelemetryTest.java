package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.RotorSpec;

class OfflineFlightRecorderCtCpJTelemetryTest {
	@TempDir
	Path tempDir;

	@Test
	void apDroneTraceExportsCtCpJReferenceTelemetryColumns() throws IOException {
		Path output = tempDir.resolve("apdrone.csv");
		OfflineFlightRecorder.FlightReport report = OfflineFlightRecorder.record("apdrone", output, 1.0);

		List<String> lines = Files.readAllLines(output);
		assertTrue(lines.size() > 2);
		String[] header = lines.get(0).split(",", -1);
		int airDensityRatioIndex = column(header, "effective_air_density_ratio");
		int ambientHumidityIndex = column(header, "ambient_humidity");
		int motorRpmIndex = column(header, "motor_0_rpm");
		int rotorThrustIndex = column(header, "rotor_0_thrust_n");
		int rotorForceXIndex = column(header, "rotor_0_force_x_n");
		int rotorForceYIndex = column(header, "rotor_0_force_y_n");
		int rotorForceZIndex = column(header, "rotor_0_force_z_n");
		int rotorTorqueXIndex = column(header, "rotor_0_torque_x_nm");
		int rotorTorqueYIndex = column(header, "rotor_0_torque_y_nm");
		int rotorTorqueZIndex = column(header, "rotor_0_torque_z_nm");
		int motorAerodynamicTorqueIndex = column(header, "motor_0_aero_torque_nm");
		int rotorPropellerJIndex = column(header, "rotor_0_prop_advance_ratio_j");
		int runtimeValidIndex = column(header, "rotor_ctcpj_runtime_valid");
		int rotorRuntimeValidIndex = column(header, "rotor_0_ctcpj_runtime_valid");
		int rotorRuntimeCtIndex = column(header, "rotor_0_ctcpj_runtime_ct");
		int rotorRuntimeCpIndex = column(header, "rotor_0_ctcpj_runtime_cp");
		int rotorRuntimeCqIndex = column(header, "rotor_0_ctcpj_runtime_torque_coefficient_cq");
		int rotorRuntimeEtaIndex = column(header, "rotor_0_ctcpj_runtime_eta");
		int rotorRuntimeDiskLoadingIndex = column(header, "rotor_0_ctcpj_runtime_disk_loading_n_m2");
		int rotorRuntimeInducedVelocityIndex = column(header,
				"rotor_0_ctcpj_runtime_ideal_induced_velocity_mps");
		int rotorRuntimeIdealMomentumPowerIndex = column(header,
				"rotor_0_ctcpj_runtime_ideal_momentum_power_w");
		int rotorRuntimeUsefulAxialPowerIndex = column(header,
				"rotor_0_ctcpj_runtime_useful_axial_thrust_power_w");
		int rotorRuntimeIdealInducedPowerIndex = column(header,
				"rotor_0_ctcpj_runtime_ideal_induced_power_w");
		int rotorRuntimeAxialEtaIndex = column(header,
				"rotor_0_ctcpj_runtime_axial_propulsive_efficiency");
		int rotorRuntimeMomentumRatioIndex = column(header,
				"rotor_0_ctcpj_runtime_ideal_momentum_power_over_shaft_power");
		int availableIndex = column(header, "rotor_ctcpj_ref_available");
		int blockedIndex = column(header, "rotor_ctcpj_ref_blocked");
		int clampedIndex = column(header, "rotor_ctcpj_ref_clamped");
		int appliedIndex = column(header, "rotor_ctcpj_ref_runtime_applied");
		int referenceRpmIndex = column(header, "rotor_ctcpj_ref_rpm");
		int rotorAvailableIndex = column(header, "rotor_0_ctcpj_ref_available");
		int rotorClampedIndex = column(header, "rotor_0_ctcpj_ref_clamped");
		int rotorAppliedIndex = column(header, "rotor_0_ctcpj_ref_runtime_applied");
		int rotorStatusIndex = column(header, "rotor_0_ctcpj_ref_status");
		int rotorLookupStatusIndex = column(header, "rotor_0_ctcpj_ref_lookup_status");
		int rotorJIndex = column(header, "rotor_0_ctcpj_ref_j");
		int rotorRpmIndex = column(header, "rotor_0_ctcpj_ref_rpm");
		int rotorReferenceRelativeAirXIndex = column(header, "rotor_0_ctcpj_ref_relative_air_x_mps");
		int rotorReferenceRelativeAirYIndex = column(header, "rotor_0_ctcpj_ref_relative_air_y_mps");
		int rotorReferenceRelativeAirZIndex = column(header, "rotor_0_ctcpj_ref_relative_air_z_mps");
		int rotorReferenceTransverseAirXIndex = column(header, "rotor_0_ctcpj_ref_transverse_air_x_mps");
		int rotorReferenceTransverseAirYIndex = column(header, "rotor_0_ctcpj_ref_transverse_air_y_mps");
		int rotorReferenceTransverseAirZIndex = column(header, "rotor_0_ctcpj_ref_transverse_air_z_mps");
		int rotorReferenceTransverseAirSpeedIndex = column(header, "rotor_0_ctcpj_ref_transverse_air_speed_mps");
		int rotorReferenceInflowAngleIndex = column(header, "rotor_0_ctcpj_ref_inflow_angle_deg");
		int rotorReferenceSpeedOfSoundIndex = column(header, "rotor_0_ctcpj_ref_speed_of_sound_mps");
		int rotorReferenceDynamicViscosityIndex = column(header, "rotor_0_ctcpj_ref_dynamic_viscosity_pa_s");
		int rotorReferenceTipMachIndex = column(header, "rotor_0_ctcpj_ref_tip_mach");
		int rotorReferenceReynoldsNumberIndex = column(header, "rotor_0_ctcpj_ref_reynolds_number");
		int rotorReferenceReynoldsIndexIndex = column(header, "rotor_0_ctcpj_ref_reynolds_index");
		int rotorReferenceTipMachMarginIndex = column(header, "rotor_0_ctcpj_ref_tip_mach_runtime_margin");
		int rotorReferenceReynoldsMarginIndex = column(header, "rotor_0_ctcpj_ref_reynolds_index_runtime_margin");
		int rotorReferenceEnvelopeMarginIndex =
				column(header, "rotor_0_ctcpj_ref_operating_envelope_margin_fraction");
		int rotorCtIndex = column(header, "rotor_0_ctcpj_ref_ct");
		int rotorCpIndex = column(header, "rotor_0_ctcpj_ref_cp");
		int rotorCqIndex = column(header, "rotor_0_ctcpj_ref_torque_coefficient_cq");
		int rotorEtaIndex = column(header, "rotor_0_ctcpj_ref_eta");
		int rotorReferenceThrustIndex = column(header, "rotor_0_ctcpj_ref_thrust_n");
		int rotorPowerIndex = column(header, "rotor_0_ctcpj_ref_shaft_power_w");
		int rotorReferenceDiskLoadingIndex = column(header, "rotor_0_ctcpj_ref_disk_loading_n_m2");
		int rotorReferenceIdealInducedVelocityIndex =
				column(header, "rotor_0_ctcpj_ref_ideal_induced_velocity_mps");
		int rotorReferenceIdealMomentumPowerIndex =
				column(header, "rotor_0_ctcpj_ref_ideal_momentum_power_w");
		int rotorReferenceUsefulAxialThrustPowerIndex =
				column(header, "rotor_0_ctcpj_ref_useful_axial_thrust_power_w");
		int rotorReferenceIdealInducedPowerIndex =
				column(header, "rotor_0_ctcpj_ref_ideal_induced_power_w");
		int rotorReferenceAxialPropulsiveEfficiencyIndex =
				column(header, "rotor_0_ctcpj_ref_axial_propulsive_efficiency");
		int rotorReferenceIdealMomentumRatioIndex =
				column(header, "rotor_0_ctcpj_ref_ideal_momentum_power_over_shaft_power");
		int rotorIntrinsicPowerResidualIndex =
				column(header, "rotor_0_ctcpj_ref_intrinsic_shaft_power_residual_w");
		int rotorIntrinsicPowerResidualFractionIndex =
				column(header, "rotor_0_ctcpj_ref_intrinsic_shaft_power_residual_fraction");
		int rotorDiskMassFlowIndex = column(header, "rotor_0_ctcpj_ref_disk_mass_flow_kg_s");
		int rotorFarWakeAxialVelocityIndex = column(header, "rotor_0_ctcpj_ref_far_wake_axial_velocity_mps");
		int rotorFarWakeContractedAreaIndex = column(header, "rotor_0_ctcpj_ref_far_wake_contracted_area_m2");
		int rotorFarWakeEquivalentRadiusIndex = column(header, "rotor_0_ctcpj_ref_far_wake_equivalent_radius_m");
		int rotorAngularMomentumSwirlRadiusIndex =
				column(header, "rotor_0_ctcpj_ref_angular_momentum_swirl_radius_m");
		int rotorWakeTangentialVelocityIndex =
				column(header, "rotor_0_ctcpj_ref_wake_tangential_velocity_mps");
		int rotorWakeSwirlPowerIndex = column(header, "rotor_0_ctcpj_ref_wake_swirl_kinetic_power_w");
		int rotorTotalWakePowerIndex = column(header, "rotor_0_ctcpj_ref_total_wake_kinetic_power_w");
		int rotorTotalWakePowerRatioIndex =
				column(header, "rotor_0_ctcpj_ref_total_wake_kinetic_power_over_shaft_power");
		int rotorWakeSwirlPowerRatioIndex =
				column(header, "rotor_0_ctcpj_ref_wake_swirl_kinetic_power_over_shaft_power");
		int rotorTotalWakePowerResidualIndex =
				column(header, "rotor_0_ctcpj_ref_total_wake_kinetic_power_residual_w");
		int rotorTotalWakePowerResidualFractionIndex =
				column(header, "rotor_0_ctcpj_ref_total_wake_kinetic_power_residual_fraction");
		int rotorWakeAngularMomentumTorqueIndex =
				column(header, "rotor_0_ctcpj_ref_wake_angular_momentum_torque_nm");
		int rotorWakeAngularMomentumTorqueResidualIndex =
				column(header, "rotor_0_ctcpj_ref_wake_angular_momentum_torque_residual_nm");
		int rotorWakeAngularMomentumTorqueResidualFractionIndex =
				column(header, "rotor_0_ctcpj_ref_wake_angular_momentum_torque_residual_fraction");
		int rotorReferenceTorqueIndex = column(header, "rotor_0_ctcpj_ref_shaft_torque_nm");
		int rotorReferenceThrustForceXIndex = column(header, "rotor_0_ctcpj_ref_thrust_force_x_n");
		int rotorReferenceThrustForceYIndex = column(header, "rotor_0_ctcpj_ref_thrust_force_y_n");
		int rotorReferenceThrustForceZIndex = column(header, "rotor_0_ctcpj_ref_thrust_force_z_n");
		int rotorReferenceReactionTorqueXIndex = column(header, "rotor_0_ctcpj_ref_reaction_torque_x_nm");
		int rotorReferenceReactionTorqueYIndex = column(header, "rotor_0_ctcpj_ref_reaction_torque_y_nm");
		int rotorReferenceReactionTorqueZIndex = column(header, "rotor_0_ctcpj_ref_reaction_torque_z_nm");
		int rotorReferenceThrustMomentXIndex = column(header, "rotor_0_ctcpj_ref_thrust_moment_x_nm");
		int rotorReferenceThrustMomentYIndex = column(header, "rotor_0_ctcpj_ref_thrust_moment_y_nm");
		int rotorReferenceThrustMomentZIndex = column(header, "rotor_0_ctcpj_ref_thrust_moment_z_nm");
		int rotorReferenceTotalTorqueXIndex = column(header, "rotor_0_ctcpj_ref_total_torque_x_nm");
		int rotorReferenceTotalTorqueYIndex = column(header, "rotor_0_ctcpj_ref_total_torque_y_nm");
		int rotorReferenceTotalTorqueZIndex = column(header, "rotor_0_ctcpj_ref_total_torque_z_nm");
		int rotorReferenceForceResidualXIndex = column(header, "rotor_0_ctcpj_ref_force_residual_x_n");
		int rotorReferenceForceResidualYIndex = column(header, "rotor_0_ctcpj_ref_force_residual_y_n");
		int rotorReferenceForceResidualZIndex = column(header, "rotor_0_ctcpj_ref_force_residual_z_n");
		int rotorReferenceTorqueResidualXIndex = column(header, "rotor_0_ctcpj_ref_torque_residual_x_nm");
		int rotorReferenceTorqueResidualYIndex = column(header, "rotor_0_ctcpj_ref_torque_residual_y_nm");
		int rotorReferenceTorqueResidualZIndex = column(header, "rotor_0_ctcpj_ref_torque_residual_z_nm");
		int rotorThrustResidualIndex = column(header, "rotor_0_ctcpj_ref_thrust_residual_n");
		int rotorPowerResidualIndex = column(header, "rotor_0_ctcpj_ref_shaft_power_residual_w");
		int rotorTorqueResidualIndex = column(header, "rotor_0_ctcpj_ref_shaft_torque_residual_nm");
		int rotorThrustRatioIndex = column(header, "rotor_0_ctcpj_ref_thrust_ratio");
		int rotorTorqueRatioIndex = column(header, "rotor_0_ctcpj_ref_shaft_torque_ratio");
		int stateShadowAvailableIndex = column(header, "rotor_ctcpj_state_ref_available");
		int stateShadowActualRotorsIndex = column(header, "rotor_ctcpj_state_ref_actual_rotors");
		int stateShadowAcceptedRotorsIndex = column(header, "rotor_ctcpj_state_ref_accepted_rotors");
		int stateShadowRuntimeAcceptedRotorsIndex =
				column(header, "rotor_ctcpj_state_ref_runtime_replacement_accepted_rotors");
		int stateShadowBlockedRotorsIndex = column(header, "rotor_ctcpj_state_ref_blocked_rotors");
		int stateShadowClampedRotorsIndex = column(header, "rotor_ctcpj_state_ref_clamped_rotors");
		int stateShadowForceResidualXIndex = column(header, "rotor_ctcpj_state_ref_force_residual_x_n");
		int stateShadowForceResidualYIndex = column(header, "rotor_ctcpj_state_ref_force_residual_y_n");
		int stateShadowForceResidualZIndex = column(header, "rotor_ctcpj_state_ref_force_residual_z_n");
		int stateShadowForceResidualIndex = column(header, "rotor_ctcpj_state_ref_force_residual_n");
		int stateShadowTorqueResidualXIndex = column(header, "rotor_ctcpj_state_ref_torque_residual_x_nm");
		int stateShadowTorqueResidualYIndex = column(header, "rotor_ctcpj_state_ref_torque_residual_y_nm");
		int stateShadowTorqueResidualZIndex = column(header, "rotor_ctcpj_state_ref_torque_residual_z_nm");
		int stateShadowTorqueResidualIndex = column(header, "rotor_ctcpj_state_ref_torque_residual_nm");
		int stateShadowThrustResidualIndex = column(header, "rotor_ctcpj_state_ref_thrust_residual_n");
		int stateShadowPowerResidualIndex = column(header, "rotor_ctcpj_state_ref_shaft_power_residual_w");
		int stateShadowShaftTorqueResidualIndex =
				column(header, "rotor_ctcpj_state_ref_shaft_torque_residual_nm");
		int stateShadowRuntimeForceResidualIndex =
				column(header, "rotor_ctcpj_state_ref_runtime_replacement_force_residual_n");
		int stateShadowRuntimeTorqueResidualIndex =
				column(header, "rotor_ctcpj_state_ref_runtime_replacement_torque_residual_nm");
		int stateShadowRuntimeThrustResidualIndex =
				column(header, "rotor_ctcpj_state_ref_runtime_replacement_thrust_residual_n");
		int stateShadowRuntimePowerResidualIndex =
				column(header, "rotor_ctcpj_state_ref_runtime_replacement_shaft_power_residual_w");
		int stateShadowRuntimeShaftTorqueResidualIndex =
				column(header, "rotor_ctcpj_state_ref_runtime_replacement_shaft_torque_residual_nm");
		int staticAvailableIndex = column(header, "rotor_ctcpj_static_ref_available");
		int rotorStaticCtIndex = column(header, "rotor_0_ctcpj_static_ref_ct");
		int rotorStaticPowerIndex = column(header, "rotor_0_ctcpj_static_ref_shaft_power_w");
		int rotorStaticDiskLoadingIndex = column(header, "rotor_0_ctcpj_static_ref_disk_loading_n_m2");
		int rotorStaticInducedVelocityIndex = column(header,
				"rotor_0_ctcpj_static_ref_ideal_induced_velocity_mps");
		int rotorStaticMomentumRatioIndex = column(header,
				"rotor_0_ctcpj_static_ref_ideal_momentum_power_over_shaft_power");
		int rotorStaticThrustResidualIndex = column(header, "rotor_0_ctcpj_static_ref_thrust_residual_n");
		int rotorStaticPowerResidualIndex = column(header, "rotor_0_ctcpj_static_ref_shaft_power_residual_w");
		int rotorStaticTorqueResidualIndex = column(header, "rotor_0_ctcpj_static_ref_shaft_torque_residual_nm");
		int rotorStaticInducedVelocityResidualIndex = column(header,
				"rotor_0_ctcpj_static_ref_induced_velocity_residual_mps");
		int rotorStaticThrustRatioIndex = column(header, "rotor_0_ctcpj_static_ref_thrust_ratio");
		int rotorStaticInducedVelocityRatioIndex = column(header,
				"rotor_0_ctcpj_static_ref_induced_velocity_ratio");
		int[] rotorReferenceAvailableIndices = new int[8];
		int[] rotorReferenceBlockedIndices = new int[8];
		int[] rotorReferenceRelativeAirXIndices = new int[8];
		int[] rotorReferenceRelativeAirYIndices = new int[8];
		int[] rotorReferenceRelativeAirZIndices = new int[8];
		int[] rotorReferenceTransverseAirSpeedIndices = new int[8];
		int[] rotorReferenceInflowAngleIndices = new int[8];
		int[] rotorReferenceSpeedOfSoundIndices = new int[8];
		int[] rotorReferenceDynamicViscosityIndices = new int[8];
		int[] rotorReferenceTipMachIndices = new int[8];
		int[] rotorReferenceReynoldsNumberIndices = new int[8];
		int[] rotorReferenceReynoldsIndexIndices = new int[8];
		int[] rotorReferenceTipMachMarginIndices = new int[8];
		int[] rotorReferenceReynoldsMarginIndices = new int[8];
		int[] rotorReferenceEnvelopeMarginIndices = new int[8];
		for (int rotor = 0; rotor < 8; rotor++) {
			rotorReferenceAvailableIndices[rotor] = column(header, "rotor_" + rotor + "_ctcpj_ref_available");
			rotorReferenceBlockedIndices[rotor] = column(header, "rotor_" + rotor + "_ctcpj_ref_blocked");
			rotorReferenceRelativeAirXIndices[rotor] = column(header, "rotor_" + rotor + "_ctcpj_ref_relative_air_x_mps");
			rotorReferenceRelativeAirYIndices[rotor] = column(header, "rotor_" + rotor + "_ctcpj_ref_relative_air_y_mps");
			rotorReferenceRelativeAirZIndices[rotor] = column(header, "rotor_" + rotor + "_ctcpj_ref_relative_air_z_mps");
			rotorReferenceTransverseAirSpeedIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_transverse_air_speed_mps");
			rotorReferenceInflowAngleIndices[rotor] = column(header, "rotor_" + rotor + "_ctcpj_ref_inflow_angle_deg");
			rotorReferenceSpeedOfSoundIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_speed_of_sound_mps");
			rotorReferenceDynamicViscosityIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_dynamic_viscosity_pa_s");
			rotorReferenceTipMachIndices[rotor] = column(header, "rotor_" + rotor + "_ctcpj_ref_tip_mach");
			rotorReferenceReynoldsNumberIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_reynolds_number");
			rotorReferenceReynoldsIndexIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_reynolds_index");
			rotorReferenceTipMachMarginIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_tip_mach_runtime_margin");
			rotorReferenceReynoldsMarginIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_reynolds_index_runtime_margin");
			rotorReferenceEnvelopeMarginIndices[rotor] =
					column(header, "rotor_" + rotor + "_ctcpj_ref_operating_envelope_margin_fraction");
		}

		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_ct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_cp"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_torque_coefficient_cq"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_ideal_momentum_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_useful_axial_thrust_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_ideal_induced_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_runtime_axial_propulsive_efficiency"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_runtime_ideal_momentum_power_over_shaft_power"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_ct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_rpm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_runtime_applied"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_lookup_status"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_relative_air_x_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_transverse_air_speed_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_inflow_angle_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_speed_of_sound_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_dynamic_viscosity_pa_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_tip_mach"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_reynolds_number"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_ref_reynolds_index"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_tip_mach_runtime_margin"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_reynolds_index_runtime_margin"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_ctcpj_ref_operating_envelope_margin_fraction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_shaft_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_ref_torque_coefficient_cq"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_thrust_force_x_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_disk_loading_n_m2"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_ref_ideal_momentum_power_over_shaft_power"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_ref_useful_axial_thrust_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_ref_axial_propulsive_efficiency"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_ref_intrinsic_shaft_power_residual_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_ref_intrinsic_shaft_power_residual_fraction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_disk_mass_flow_kg_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_ref_wake_tangential_velocity_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_ref_total_wake_kinetic_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_ref_total_wake_kinetic_power_residual_fraction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_ref_wake_angular_momentum_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_ref_wake_angular_momentum_torque_residual_fraction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_total_torque_z_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_force_residual_x_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_torque_residual_z_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_thrust_residual_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_shaft_torque_residual_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_ref_thrust_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_ref_shaft_torque_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_state_ref_available"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_state_ref_force_residual_x_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_state_ref_torque_residual_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_ctcpj_state_ref_runtime_replacement_shaft_power_residual_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_static_ref_ct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_static_ref_ideal_induced_velocity_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_static_ref_shaft_torque_residual_nm"));

		boolean sawReferenceState = false;
		boolean sawPositiveReferenceRpm = false;
		boolean sawStaticReferenceState = false;
		boolean sawRuntimeCoefficientState = false;
		int referenceFlowSampleCount = 0;
		double referenceRelativeAirSpeedSum = 0.0;
		double referenceRelativeAirSpeedMax = 0.0;
		double referenceTransverseAirSpeedSum = 0.0;
		double referenceTransverseAirSpeedMax = 0.0;
		double referenceInflowAngleSum = 0.0;
		double referenceInflowAngleMax = 0.0;
		double referenceTipMachSum = 0.0;
		double referenceTipMachMax = 0.0;
		double referenceReynoldsNumberSum = 0.0;
		double referenceReynoldsNumberMax = 0.0;
		double referenceReynoldsIndexSum = 0.0;
		double referenceReynoldsIndexMax = 0.0;
		int stateShadowSampleCount = 0;
		int stateShadowAcceptedRotorCount = 0;
		int stateShadowRuntimeAcceptedRotorCount = 0;
		int stateShadowBlockedRotorCount = 0;
		int stateShadowClampedRotorCount = 0;
		double stateShadowForceResidualSum = 0.0;
		double stateShadowForceResidualMax = 0.0;
		double stateShadowTorqueResidualSum = 0.0;
		double stateShadowTorqueResidualMax = 0.0;
		double stateShadowThrustResidualSum = 0.0;
		double stateShadowThrustResidualMax = 0.0;
		double stateShadowPowerResidualSum = 0.0;
		double stateShadowPowerResidualMax = 0.0;
		double stateShadowShaftTorqueResidualSum = 0.0;
		double stateShadowShaftTorqueResidualMax = 0.0;
		double stateShadowRuntimeForceResidualSum = 0.0;
		double stateShadowRuntimeForceResidualMax = 0.0;
		double stateShadowRuntimeTorqueResidualSum = 0.0;
		double stateShadowRuntimeTorqueResidualMax = 0.0;
		double stateShadowRuntimeThrustResidualSum = 0.0;
		double stateShadowRuntimeThrustResidualMax = 0.0;
		double stateShadowRuntimePowerResidualSum = 0.0;
		double stateShadowRuntimePowerResidualMax = 0.0;
		double stateShadowRuntimeShaftTorqueResidualSum = 0.0;
		double stateShadowRuntimeShaftTorqueResidualMax = 0.0;
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			assertEquals(header.length, row.length, "CSV row " + i + " column count changed");
			for (int rotor = 0; rotor < 8; rotor++) {
				boolean referencePresent =
						Double.parseDouble(row[rotorReferenceAvailableIndices[rotor]]) > 0.0
								|| Double.parseDouble(row[rotorReferenceBlockedIndices[rotor]]) > 0.0;
				if (!referencePresent) {
					continue;
				}
				referenceFlowSampleCount++;
				double relativeAirSpeed = magnitude(
						Double.parseDouble(row[rotorReferenceRelativeAirXIndices[rotor]]),
						Double.parseDouble(row[rotorReferenceRelativeAirYIndices[rotor]]),
						Double.parseDouble(row[rotorReferenceRelativeAirZIndices[rotor]])
				);
				double transverseAirSpeed = Double.parseDouble(row[rotorReferenceTransverseAirSpeedIndices[rotor]]);
				double inflowAngle = Double.parseDouble(row[rotorReferenceInflowAngleIndices[rotor]]);
				double speedOfSound = Double.parseDouble(row[rotorReferenceSpeedOfSoundIndices[rotor]]);
				double dynamicViscosity = Double.parseDouble(row[rotorReferenceDynamicViscosityIndices[rotor]]);
				double tipMach = Double.parseDouble(row[rotorReferenceTipMachIndices[rotor]]);
				double reynoldsNumber = Double.parseDouble(row[rotorReferenceReynoldsNumberIndices[rotor]]);
				double reynoldsIndex = Double.parseDouble(row[rotorReferenceReynoldsIndexIndices[rotor]]);
				double tipMachMargin = Double.parseDouble(row[rotorReferenceTipMachMarginIndices[rotor]]);
				double reynoldsMargin = Double.parseDouble(row[rotorReferenceReynoldsMarginIndices[rotor]]);
				double envelopeMargin = Double.parseDouble(row[rotorReferenceEnvelopeMarginIndices[rotor]]);
				assertTrue(Double.isFinite(speedOfSound));
				assertTrue(Double.isFinite(dynamicViscosity));
				assertTrue(Double.isFinite(tipMach));
				assertTrue(Double.isFinite(reynoldsNumber));
				assertTrue(Double.isFinite(reynoldsIndex));
				assertTrue(Double.isFinite(tipMachMargin));
				assertTrue(Double.isFinite(reynoldsMargin));
				assertTrue(Double.isFinite(envelopeMargin));
				assertTrue(speedOfSound > 0.0);
				assertTrue(dynamicViscosity > 0.0);
				assertTrue(tipMach >= 0.0);
				assertTrue(reynoldsNumber >= 0.0);
				assertTrue(reynoldsIndex >= 0.0);
				referenceRelativeAirSpeedSum += relativeAirSpeed;
				referenceRelativeAirSpeedMax = Math.max(referenceRelativeAirSpeedMax, relativeAirSpeed);
				referenceTransverseAirSpeedSum += transverseAirSpeed;
				referenceTransverseAirSpeedMax = Math.max(referenceTransverseAirSpeedMax, transverseAirSpeed);
				referenceInflowAngleSum += inflowAngle;
				referenceInflowAngleMax = Math.max(referenceInflowAngleMax, inflowAngle);
				referenceTipMachSum += tipMach;
				referenceTipMachMax = Math.max(referenceTipMachMax, tipMach);
				referenceReynoldsNumberSum += reynoldsNumber;
				referenceReynoldsNumberMax = Math.max(referenceReynoldsNumberMax, reynoldsNumber);
				referenceReynoldsIndexSum += reynoldsIndex;
				referenceReynoldsIndexMax = Math.max(referenceReynoldsIndexMax, reynoldsIndex);
			}
			double runtimeValid = Double.parseDouble(row[runtimeValidIndex]);
			double rotorRuntimeValid = Double.parseDouble(row[rotorRuntimeValidIndex]);
			double available = Double.parseDouble(row[availableIndex]);
			double blocked = Double.parseDouble(row[blockedIndex]);
			double clamped = Double.parseDouble(row[clampedIndex]);
			double applied = Double.parseDouble(row[appliedIndex]);
			double rotorAvailable = Double.parseDouble(row[rotorAvailableIndex]);
			double rotorClamped = Double.parseDouble(row[rotorClampedIndex]);
			double rotorApplied = Double.parseDouble(row[rotorAppliedIndex]);
			double staticAvailable = Double.parseDouble(row[staticAvailableIndex]);
			double rpm = Double.parseDouble(row[motorRpmIndex]);
			double ambientHumidity = Double.parseDouble(row[ambientHumidityIndex]);
			assertTrue(Double.isFinite(ambientHumidity));
			assertTrue(ambientHumidity >= 0.0);
			assertTrue(ambientHumidity <= 1.0);
			double omega = rpm * 2.0 * Math.PI / 60.0;
			double aerodynamicPower = Double.parseDouble(row[motorAerodynamicTorqueIndex]) * omega;
			if (runtimeValid > 0.0 || rotorRuntimeValid > 0.0) {
				sawRuntimeCoefficientState = true;
				double thrust = Double.parseDouble(row[rotorThrustIndex]);
				double propellerJ = Double.parseDouble(row[rotorPropellerJIndex]);
				double runtimeCt = Double.parseDouble(row[rotorRuntimeCtIndex]);
				double runtimeCp = Double.parseDouble(row[rotorRuntimeCpIndex]);
				double runtimeCq = Double.parseDouble(row[rotorRuntimeCqIndex]);
				double runtimeEta = Double.parseDouble(row[rotorRuntimeEtaIndex]);
				double runtimeDiskLoading = Double.parseDouble(row[rotorRuntimeDiskLoadingIndex]);
				double runtimeInducedVelocity = Double.parseDouble(row[rotorRuntimeInducedVelocityIndex]);
				double runtimeIdealMomentumPower = Double.parseDouble(row[rotorRuntimeIdealMomentumPowerIndex]);
				double runtimeUsefulAxialPower = Double.parseDouble(row[rotorRuntimeUsefulAxialPowerIndex]);
				double runtimeIdealInducedPower = Double.parseDouble(row[rotorRuntimeIdealInducedPowerIndex]);
				double runtimeAxialEta = Double.parseDouble(row[rotorRuntimeAxialEtaIndex]);
				double runtimeMomentumRatio = Double.parseDouble(row[rotorRuntimeMomentumRatioIndex]);
				assertTrue(Double.isFinite(runtimeCt));
				assertTrue(Double.isFinite(runtimeCp));
				assertTrue(Double.isFinite(runtimeCq));
				assertTrue(Double.isFinite(runtimeEta));
				assertTrue(Double.isFinite(runtimeDiskLoading));
				assertTrue(Double.isFinite(runtimeInducedVelocity));
				assertTrue(Double.isFinite(runtimeIdealMomentumPower));
				assertTrue(Double.isFinite(runtimeUsefulAxialPower));
				assertTrue(Double.isFinite(runtimeIdealInducedPower));
				assertTrue(Double.isFinite(runtimeAxialEta));
				assertTrue(Double.isFinite(runtimeMomentumRatio));
				if (rotorRuntimeValid > 0.0 && rpm > 0.0) {
					double n = rpm / 60.0;
					double airDensity = PropellerArchiveCtCpJDimensionalRotorResponse
							.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
							* Math.max(0.20, Double.parseDouble(row[airDensityRatioIndex]));
					assertEquals(
							thrust / (airDensity * n * n * Math.pow(diameter, 4.0)),
							runtimeCt,
							5.0e-6
					);
					assertEquals(
							aerodynamicPower / (airDensity * n * n * n * Math.pow(diameter, 5.0)),
							runtimeCp,
							1.0e-4
					);
					assertEquals(runtimeCp / (2.0 * Math.PI), runtimeCq, 1.0e-7);
					assertEquals(
							Double.parseDouble(row[motorAerodynamicTorqueIndex]),
							runtimeCq * airDensity * n * n * Math.pow(diameter, 5.0),
							5.0e-6
					);
					if (runtimeCp > 1.0e-9) {
						assertEquals(propellerJ * runtimeCt / runtimeCp, runtimeEta, 5.0e-5);
					}
					double axialAdvanceSpeed = Math.max(0.0, propellerJ * n * diameter);
					double diskArea = Math.PI * diameter * diameter * 0.25;
					double expectedInducedVelocity = thrust > 1.0e-9
							? 0.5 * (Math.sqrt(axialAdvanceSpeed * axialAdvanceSpeed
									+ 2.0 * thrust / (airDensity * diskArea)) - axialAdvanceSpeed)
							: 0.0;
					assertEquals(expectedInducedVelocity, runtimeInducedVelocity, 1.5e-4);
					assertEquals(thrust * axialAdvanceSpeed, runtimeUsefulAxialPower, 2.0e-3);
					assertEquals(thrust * expectedInducedVelocity, runtimeIdealInducedPower, 2.0e-3);
					assertEquals(runtimeUsefulAxialPower + runtimeIdealInducedPower,
							runtimeIdealMomentumPower, 2.0e-3);
					if (aerodynamicPower > 1.0e-9) {
						assertEquals(runtimeUsefulAxialPower / aerodynamicPower, runtimeAxialEta, 1.0e-4);
						assertEquals(runtimeEta, runtimeAxialEta, 1.0e-4);
						assertEquals(
								runtimeIdealMomentumPower / aerodynamicPower,
								runtimeMomentumRatio,
								1.0e-4
						);
					}
				}
			}
			if (available > 0.0 || blocked > 0.0 || clamped > 0.0) {
				sawReferenceState = true;
				assertTrue(applied <= available);
				assertTrue(rotorApplied <= rotorAvailable);
				if (rotorClamped > 0.0) {
					assertEquals(0.0, rotorApplied, 1.0e-12);
				} else if (rotorAvailable > 0.0) {
					assertEquals(1.0, rotorApplied, 1.0e-12);
				}
				double referenceRpm = Double.parseDouble(row[referenceRpmIndex]);
				double rotorRpm = Double.parseDouble(row[rotorRpmIndex]);
				double lookupStatus = Double.parseDouble(row[rotorLookupStatusIndex]);
				assertTrue(Double.isFinite(referenceRpm));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStatusIndex])));
				assertTrue(Double.isFinite(lookupStatus));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorJIndex])));
				assertTrue(Double.isFinite(rotorRpm));
				double relativeAirX = Double.parseDouble(row[rotorReferenceRelativeAirXIndex]);
				double relativeAirY = Double.parseDouble(row[rotorReferenceRelativeAirYIndex]);
				double relativeAirZ = Double.parseDouble(row[rotorReferenceRelativeAirZIndex]);
				double transverseAirX = Double.parseDouble(row[rotorReferenceTransverseAirXIndex]);
				double transverseAirY = Double.parseDouble(row[rotorReferenceTransverseAirYIndex]);
				double transverseAirZ = Double.parseDouble(row[rotorReferenceTransverseAirZIndex]);
				double transverseAirSpeed = Double.parseDouble(row[rotorReferenceTransverseAirSpeedIndex]);
				double inflowAngleDegrees = Double.parseDouble(row[rotorReferenceInflowAngleIndex]);
				double speedOfSound = Double.parseDouble(row[rotorReferenceSpeedOfSoundIndex]);
				double dynamicViscosity = Double.parseDouble(row[rotorReferenceDynamicViscosityIndex]);
				double tipMach = Double.parseDouble(row[rotorReferenceTipMachIndex]);
				double reynoldsNumber = Double.parseDouble(row[rotorReferenceReynoldsNumberIndex]);
				double reynoldsIndex = Double.parseDouble(row[rotorReferenceReynoldsIndexIndex]);
				double tipMachMargin = Double.parseDouble(row[rotorReferenceTipMachMarginIndex]);
				double reynoldsMargin = Double.parseDouble(row[rotorReferenceReynoldsMarginIndex]);
				double envelopeMargin = Double.parseDouble(row[rotorReferenceEnvelopeMarginIndex]);
				assertTrue(Double.isFinite(relativeAirX));
				assertTrue(Double.isFinite(relativeAirY));
				assertTrue(Double.isFinite(relativeAirZ));
				assertTrue(Double.isFinite(transverseAirX));
				assertTrue(Double.isFinite(transverseAirY));
				assertTrue(Double.isFinite(transverseAirZ));
				assertTrue(Double.isFinite(transverseAirSpeed));
				assertTrue(Double.isFinite(inflowAngleDegrees));
				assertTrue(Double.isFinite(speedOfSound));
				assertTrue(Double.isFinite(dynamicViscosity));
				assertTrue(Double.isFinite(tipMach));
				assertTrue(Double.isFinite(reynoldsNumber));
				assertTrue(Double.isFinite(reynoldsIndex));
				assertTrue(Double.isFinite(tipMachMargin));
				assertTrue(Double.isFinite(reynoldsMargin));
				assertTrue(Double.isFinite(envelopeMargin));
				assertTrue(speedOfSound >= 0.0);
				assertTrue(dynamicViscosity >= 0.0);
				assertTrue(tipMach >= 0.0);
				assertTrue(reynoldsNumber >= 0.0);
				assertTrue(reynoldsIndex >= 0.0);
				sawPositiveReferenceRpm |= referenceRpm > 0.0 || rotorRpm > 0.0;
				if (blocked > 0.0) {
					assertEquals(
							PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.REFERENCE_WINDOW_UNAVAILABLE.ordinal(),
							(int) lookupStatus
					);
				}
				if (clamped > 0.0) {
					assertEquals(
							PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.CLAMPED.ordinal(),
							(int) lookupStatus
					);
				}
				double referenceCt = Double.parseDouble(row[rotorCtIndex]);
				double referenceCp = Double.parseDouble(row[rotorCpIndex]);
				double referenceCq = Double.parseDouble(row[rotorCqIndex]);
				assertTrue(Double.isFinite(referenceCt));
				assertTrue(Double.isFinite(referenceCp));
				assertTrue(Double.isFinite(referenceCq));
				double referenceThrust = Double.parseDouble(row[rotorReferenceThrustIndex]);
				double referencePower = Double.parseDouble(row[rotorPowerIndex]);
				double referenceDiskLoading = Double.parseDouble(row[rotorReferenceDiskLoadingIndex]);
				double referenceIdealInducedVelocity =
						Double.parseDouble(row[rotorReferenceIdealInducedVelocityIndex]);
				double referenceIdealMomentumPower =
						Double.parseDouble(row[rotorReferenceIdealMomentumPowerIndex]);
				double referenceUsefulAxialPower =
						Double.parseDouble(row[rotorReferenceUsefulAxialThrustPowerIndex]);
				double referenceIdealInducedPower =
						Double.parseDouble(row[rotorReferenceIdealInducedPowerIndex]);
				double referenceAxialPropulsiveEfficiency =
						Double.parseDouble(row[rotorReferenceAxialPropulsiveEfficiencyIndex]);
				double referenceIdealMomentumRatio =
						Double.parseDouble(row[rotorReferenceIdealMomentumRatioIndex]);
				double intrinsicPowerResidual = Double.parseDouble(row[rotorIntrinsicPowerResidualIndex]);
				double intrinsicPowerResidualFraction =
						Double.parseDouble(row[rotorIntrinsicPowerResidualFractionIndex]);
				double diskMassFlow = Double.parseDouble(row[rotorDiskMassFlowIndex]);
				double farWakeAxialVelocity = Double.parseDouble(row[rotorFarWakeAxialVelocityIndex]);
				double farWakeContractedArea = Double.parseDouble(row[rotorFarWakeContractedAreaIndex]);
				double farWakeEquivalentRadius = Double.parseDouble(row[rotorFarWakeEquivalentRadiusIndex]);
				double angularMomentumSwirlRadius = Double.parseDouble(row[rotorAngularMomentumSwirlRadiusIndex]);
				double wakeTangentialVelocity = Double.parseDouble(row[rotorWakeTangentialVelocityIndex]);
				double wakeSwirlPower = Double.parseDouble(row[rotorWakeSwirlPowerIndex]);
				double totalWakePower = Double.parseDouble(row[rotorTotalWakePowerIndex]);
				double totalWakePowerRatio = Double.parseDouble(row[rotorTotalWakePowerRatioIndex]);
				double wakeSwirlPowerRatio = Double.parseDouble(row[rotorWakeSwirlPowerRatioIndex]);
				double totalWakePowerResidual = Double.parseDouble(row[rotorTotalWakePowerResidualIndex]);
				double totalWakePowerResidualFraction =
						Double.parseDouble(row[rotorTotalWakePowerResidualFractionIndex]);
				double wakeAngularMomentumTorque =
						Double.parseDouble(row[rotorWakeAngularMomentumTorqueIndex]);
				double wakeAngularMomentumTorqueResidual =
						Double.parseDouble(row[rotorWakeAngularMomentumTorqueResidualIndex]);
				double wakeAngularMomentumTorqueResidualFraction =
						Double.parseDouble(row[rotorWakeAngularMomentumTorqueResidualFractionIndex]);
				double referenceTorque = Double.parseDouble(row[rotorReferenceTorqueIndex]);
				assertTrue(Double.isFinite(referencePower));
				assertTrue(Double.isFinite(referenceDiskLoading));
				assertTrue(Double.isFinite(referenceIdealInducedVelocity));
				assertTrue(Double.isFinite(referenceIdealMomentumPower));
				assertTrue(Double.isFinite(referenceUsefulAxialPower));
				assertTrue(Double.isFinite(referenceIdealInducedPower));
				assertTrue(Double.isFinite(referenceAxialPropulsiveEfficiency));
				assertTrue(Double.isFinite(referenceIdealMomentumRatio));
				assertTrue(Double.isFinite(intrinsicPowerResidual));
				assertTrue(Double.isFinite(intrinsicPowerResidualFraction));
				assertTrue(Double.isFinite(diskMassFlow));
				assertTrue(Double.isFinite(farWakeAxialVelocity));
				assertTrue(Double.isFinite(farWakeContractedArea));
				assertTrue(Double.isFinite(farWakeEquivalentRadius));
				assertTrue(Double.isFinite(angularMomentumSwirlRadius));
				assertTrue(Double.isFinite(wakeTangentialVelocity));
				assertTrue(Double.isFinite(wakeSwirlPower));
				assertTrue(Double.isFinite(totalWakePower));
				assertTrue(Double.isFinite(totalWakePowerRatio));
				assertTrue(Double.isFinite(wakeSwirlPowerRatio));
				assertTrue(Double.isFinite(totalWakePowerResidual));
				assertTrue(Double.isFinite(totalWakePowerResidualFraction));
				assertTrue(Double.isFinite(wakeAngularMomentumTorque));
				assertTrue(Double.isFinite(wakeAngularMomentumTorqueResidual));
				assertTrue(Double.isFinite(wakeAngularMomentumTorqueResidualFraction));
				double thrustForceX = Double.parseDouble(row[rotorReferenceThrustForceXIndex]);
				double thrustForceY = Double.parseDouble(row[rotorReferenceThrustForceYIndex]);
				double thrustForceZ = Double.parseDouble(row[rotorReferenceThrustForceZIndex]);
				double reactionTorqueX = Double.parseDouble(row[rotorReferenceReactionTorqueXIndex]);
				double reactionTorqueY = Double.parseDouble(row[rotorReferenceReactionTorqueYIndex]);
				double reactionTorqueZ = Double.parseDouble(row[rotorReferenceReactionTorqueZIndex]);
				double thrustMomentX = Double.parseDouble(row[rotorReferenceThrustMomentXIndex]);
				double thrustMomentY = Double.parseDouble(row[rotorReferenceThrustMomentYIndex]);
				double thrustMomentZ = Double.parseDouble(row[rotorReferenceThrustMomentZIndex]);
				double totalTorqueX = Double.parseDouble(row[rotorReferenceTotalTorqueXIndex]);
				double totalTorqueY = Double.parseDouble(row[rotorReferenceTotalTorqueYIndex]);
				double totalTorqueZ = Double.parseDouble(row[rotorReferenceTotalTorqueZIndex]);
				double forceResidualX = Double.parseDouble(row[rotorReferenceForceResidualXIndex]);
				double forceResidualY = Double.parseDouble(row[rotorReferenceForceResidualYIndex]);
				double forceResidualZ = Double.parseDouble(row[rotorReferenceForceResidualZIndex]);
				double torqueResidualX = Double.parseDouble(row[rotorReferenceTorqueResidualXIndex]);
				double torqueResidualY = Double.parseDouble(row[rotorReferenceTorqueResidualYIndex]);
				double torqueResidualZ = Double.parseDouble(row[rotorReferenceTorqueResidualZIndex]);
				assertTrue(Double.isFinite(referenceThrust));
				assertTrue(Double.isFinite(referenceTorque));
				assertTrue(Double.isFinite(thrustForceX));
				assertTrue(Double.isFinite(thrustForceY));
				assertTrue(Double.isFinite(thrustForceZ));
				assertTrue(Double.isFinite(reactionTorqueX));
				assertTrue(Double.isFinite(reactionTorqueY));
				assertTrue(Double.isFinite(reactionTorqueZ));
				assertTrue(Double.isFinite(thrustMomentX));
				assertTrue(Double.isFinite(thrustMomentY));
				assertTrue(Double.isFinite(thrustMomentZ));
				assertTrue(Double.isFinite(totalTorqueX));
				assertTrue(Double.isFinite(totalTorqueY));
				assertTrue(Double.isFinite(totalTorqueZ));
				assertTrue(Double.isFinite(forceResidualX));
				assertTrue(Double.isFinite(forceResidualY));
				assertTrue(Double.isFinite(forceResidualZ));
				assertTrue(Double.isFinite(torqueResidualX));
				assertTrue(Double.isFinite(torqueResidualY));
				assertTrue(Double.isFinite(torqueResidualZ));
				if (rotorAvailable > 0.0) {
					assertEquals(
							magnitude(transverseAirX, transverseAirY, transverseAirZ),
							transverseAirSpeed,
							5.0e-5
					);
					assertTrue(inflowAngleDegrees >= 0.0);
					assertTrue(inflowAngleDegrees <= 180.0 + 1.0e-9);
					assertTrue(speedOfSound > 250.0);
					assertTrue(dynamicViscosity > 1.0e-6);
					assertTrue(tipMach > 0.0);
					assertTrue(reynoldsNumber > 0.0);
					assertTrue(reynoldsIndex > 0.0);
					if (rotorApplied > 0.0) {
						assertTrue(tipMachMargin >= -1.0e-9);
						assertTrue(reynoldsMargin >= -1.0e-9);
						assertTrue(envelopeMargin >= -1.0e-9);
					}
					double thrustForceMagnitude = magnitude(thrustForceX, thrustForceY, thrustForceZ);
					double reactionTorqueMagnitude = magnitude(reactionTorqueX, reactionTorqueY, reactionTorqueZ);
					assertEquals(referenceThrust, thrustForceMagnitude, 3.0e-5);
					assertEquals(referenceTorque, reactionTorqueMagnitude, 2.0e-6);
					assertTrue(referenceDiskLoading > 0.0);
					assertTrue(referenceIdealInducedVelocity > 0.0);
					assertTrue(referenceCq > 0.0);
					assertEquals(referenceCp / (2.0 * Math.PI), referenceCq, 1.0e-7);
					double referenceN = Double.parseDouble(row[rotorRpmIndex]) / 60.0;
					double referenceAirDensity = PropellerArchiveCtCpJDimensionalRotorResponse
							.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
							* Math.max(0.20, Double.parseDouble(row[airDensityRatioIndex]));
					assertEquals(referenceCq
									* referenceAirDensity
									* referenceN
									* referenceN
									* Math.pow(diameter, 5.0),
							referenceTorque, 3.0e-6);
					assertTrue(diskMassFlow > 0.0);
					assertTrue(farWakeAxialVelocity > 0.0);
					assertTrue(farWakeContractedArea > 0.0);
					assertTrue(farWakeEquivalentRadius > 0.0);
					assertTrue(angularMomentumSwirlRadius > 0.0);
					assertTrue(wakeTangentialVelocity > 0.0);
					double referenceAxialSpeed = Math.max(0.0, Double.parseDouble(row[rotorJIndex])
							* Double.parseDouble(row[rotorRpmIndex])
							/ 60.0
							* diameter);
					assertEquals(referenceThrust,
							diskMassFlow * (farWakeAxialVelocity - referenceAxialSpeed), 1.0e-4);
					assertEquals(referenceThrust * referenceAxialSpeed,
							referenceUsefulAxialPower, 2.0e-3);
					assertEquals(referenceThrust * referenceIdealInducedVelocity,
							referenceIdealInducedPower, 2.0e-3);
					assertEquals(referenceUsefulAxialPower + referenceIdealInducedPower,
							referenceIdealMomentumPower, 2.0e-3);
					assertEquals(Math.sqrt(farWakeContractedArea / Math.PI),
							farWakeEquivalentRadius, 5.0e-5);
					assertEquals(farWakeEquivalentRadius
									* RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION,
							angularMomentumSwirlRadius, 5.0e-5);
					assertEquals(diskMassFlow
									* angularMomentumSwirlRadius
									* wakeTangentialVelocity
									* angularMomentumSwirlRadius
									* wakeTangentialVelocity
									/ (farWakeEquivalentRadius * farWakeEquivalentRadius),
							wakeSwirlPower, 1.0e-3);
					assertEquals(diskMassFlow * angularMomentumSwirlRadius * wakeTangentialVelocity,
							wakeAngularMomentumTorque, 3.0e-6);
					assertEquals(referenceTorque, wakeAngularMomentumTorque, 3.0e-6);
					assertEquals(wakeAngularMomentumTorque - referenceTorque,
							wakeAngularMomentumTorqueResidual, 3.0e-6);
					assertEquals(0.0, wakeAngularMomentumTorqueResidual, 3.0e-6);
					assertEquals(0.0, wakeAngularMomentumTorqueResidualFraction, 5.0e-5);
					assertTrue(wakeSwirlPower > 0.0);
					assertTrue(totalWakePower >= referenceIdealMomentumPower);
					assertEquals(referenceIdealMomentumPower + wakeSwirlPower, totalWakePower, 5.0e-5);
					if (referencePower > 1.0e-9) {
						assertEquals(
								referenceIdealMomentumPower / referencePower,
								referenceIdealMomentumRatio,
								5.0e-5
						);
						assertEquals(referenceUsefulAxialPower / referencePower,
								referenceAxialPropulsiveEfficiency, 5.0e-5);
						assertEquals(Double.parseDouble(row[rotorEtaIndex]),
								referenceAxialPropulsiveEfficiency, 5.0e-5);
						assertEquals(
								intrinsicPowerResidual / referencePower,
								intrinsicPowerResidualFraction,
								5.0e-5
						);
						assertEquals(totalWakePower / referencePower, totalWakePowerRatio, 5.0e-5);
						assertEquals(wakeSwirlPower / referencePower, wakeSwirlPowerRatio, 5.0e-5);
						assertEquals(referencePower - totalWakePower, totalWakePowerResidual, 5.0e-5);
						assertEquals(totalWakePowerResidual / referencePower,
								totalWakePowerResidualFraction, 5.0e-5);
					}
					assertEquals(reactionTorqueX + thrustMomentX, totalTorqueX, 2.0e-6);
					assertEquals(reactionTorqueY + thrustMomentY, totalTorqueY, 2.0e-6);
					assertEquals(reactionTorqueZ + thrustMomentZ, totalTorqueZ, 2.0e-6);
					assertEquals(
							Double.parseDouble(row[rotorForceXIndex]) - thrustForceX,
							forceResidualX,
							5.0e-5
					);
					assertEquals(
							Double.parseDouble(row[rotorForceYIndex]) - thrustForceY,
							forceResidualY,
							5.0e-5
					);
					assertEquals(
							Double.parseDouble(row[rotorForceZIndex]) - thrustForceZ,
							forceResidualZ,
							5.0e-5
					);
					assertEquals(
							Double.parseDouble(row[rotorTorqueXIndex]) - totalTorqueX,
							torqueResidualX,
							3.0e-6
					);
					assertEquals(
							Double.parseDouble(row[rotorTorqueYIndex]) - totalTorqueY,
							torqueResidualY,
							3.0e-6
					);
					assertEquals(
							Double.parseDouble(row[rotorTorqueZIndex]) - totalTorqueZ,
							torqueResidualZ,
							3.0e-6
					);
				}
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorThrustResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorPowerResidualIndex])));
				assertEquals(
						aerodynamicPower - Double.parseDouble(row[rotorPowerIndex]),
						Double.parseDouble(row[rotorPowerResidualIndex]),
						2.0e-3
				);
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorTorqueResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorThrustRatioIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorTorqueRatioIndex])));
			}
			double stateShadowAvailable = Double.parseDouble(row[stateShadowAvailableIndex]);
			if (stateShadowAvailable > 0.0) {
				stateShadowSampleCount++;
				int actualRotors = (int) Double.parseDouble(row[stateShadowActualRotorsIndex]);
				int acceptedRotors = (int) Double.parseDouble(row[stateShadowAcceptedRotorsIndex]);
				int runtimeAcceptedRotors =
						(int) Double.parseDouble(row[stateShadowRuntimeAcceptedRotorsIndex]);
				int blockedRotors = (int) Double.parseDouble(row[stateShadowBlockedRotorsIndex]);
				int clampedRotors = (int) Double.parseDouble(row[stateShadowClampedRotorsIndex]);
				double forceX = Double.parseDouble(row[stateShadowForceResidualXIndex]);
				double forceY = Double.parseDouble(row[stateShadowForceResidualYIndex]);
				double forceZ = Double.parseDouble(row[stateShadowForceResidualZIndex]);
				double forceResidual = Double.parseDouble(row[stateShadowForceResidualIndex]);
				double torqueX = Double.parseDouble(row[stateShadowTorqueResidualXIndex]);
				double torqueY = Double.parseDouble(row[stateShadowTorqueResidualYIndex]);
				double torqueZ = Double.parseDouble(row[stateShadowTorqueResidualZIndex]);
				double torqueResidual = Double.parseDouble(row[stateShadowTorqueResidualIndex]);
				double thrustResidual = Math.abs(Double.parseDouble(row[stateShadowThrustResidualIndex]));
				double powerResidual = Math.abs(Double.parseDouble(row[stateShadowPowerResidualIndex]));
				double shaftTorqueResidual =
						Math.abs(Double.parseDouble(row[stateShadowShaftTorqueResidualIndex]));
				double runtimeForceResidual =
						Double.parseDouble(row[stateShadowRuntimeForceResidualIndex]);
				double runtimeTorqueResidual =
						Double.parseDouble(row[stateShadowRuntimeTorqueResidualIndex]);
				double runtimeThrustResidual =
						Math.abs(Double.parseDouble(row[stateShadowRuntimeThrustResidualIndex]));
				double runtimePowerResidual =
						Math.abs(Double.parseDouble(row[stateShadowRuntimePowerResidualIndex]));
				double runtimeShaftTorqueResidual =
						Math.abs(Double.parseDouble(row[stateShadowRuntimeShaftTorqueResidualIndex]));
				assertEquals(DroneConfig.apDrone().rotors().size(), actualRotors);
				assertTrue(acceptedRotors >= 0);
				assertTrue(runtimeAcceptedRotors >= 0);
				assertTrue(blockedRotors >= 0);
				assertTrue(clampedRotors >= 0);
				assertTrue(runtimeAcceptedRotors <= acceptedRotors);
				assertTrue(clampedRotors <= acceptedRotors);
				assertTrue(Double.isFinite(forceX));
				assertTrue(Double.isFinite(forceY));
				assertTrue(Double.isFinite(forceZ));
				assertTrue(Double.isFinite(forceResidual));
				assertTrue(Double.isFinite(torqueX));
				assertTrue(Double.isFinite(torqueY));
				assertTrue(Double.isFinite(torqueZ));
				assertTrue(Double.isFinite(torqueResidual));
				assertTrue(Double.isFinite(thrustResidual));
				assertTrue(Double.isFinite(powerResidual));
				assertTrue(Double.isFinite(shaftTorqueResidual));
				assertTrue(Double.isFinite(runtimeForceResidual));
				assertTrue(Double.isFinite(runtimeTorqueResidual));
				assertTrue(Double.isFinite(runtimeThrustResidual));
				assertTrue(Double.isFinite(runtimePowerResidual));
				assertTrue(Double.isFinite(runtimeShaftTorqueResidual));
				assertEquals(magnitude(forceX, forceY, forceZ), forceResidual, 1.0e-4);
				assertEquals(magnitude(torqueX, torqueY, torqueZ), torqueResidual, 1.0e-5);
				stateShadowAcceptedRotorCount += acceptedRotors;
				stateShadowRuntimeAcceptedRotorCount += runtimeAcceptedRotors;
				stateShadowBlockedRotorCount += blockedRotors;
				stateShadowClampedRotorCount += clampedRotors;
				stateShadowForceResidualSum += forceResidual;
				stateShadowForceResidualMax = Math.max(stateShadowForceResidualMax, forceResidual);
				stateShadowTorqueResidualSum += torqueResidual;
				stateShadowTorqueResidualMax = Math.max(stateShadowTorqueResidualMax, torqueResidual);
				stateShadowThrustResidualSum += thrustResidual;
				stateShadowThrustResidualMax = Math.max(stateShadowThrustResidualMax, thrustResidual);
				stateShadowPowerResidualSum += powerResidual;
				stateShadowPowerResidualMax = Math.max(stateShadowPowerResidualMax, powerResidual);
				stateShadowShaftTorqueResidualSum += shaftTorqueResidual;
				stateShadowShaftTorqueResidualMax = Math.max(
						stateShadowShaftTorqueResidualMax,
						shaftTorqueResidual
				);
				stateShadowRuntimeForceResidualSum += runtimeForceResidual;
				stateShadowRuntimeForceResidualMax = Math.max(
						stateShadowRuntimeForceResidualMax,
						runtimeForceResidual
				);
				stateShadowRuntimeTorqueResidualSum += runtimeTorqueResidual;
				stateShadowRuntimeTorqueResidualMax = Math.max(
						stateShadowRuntimeTorqueResidualMax,
						runtimeTorqueResidual
				);
				stateShadowRuntimeThrustResidualSum += runtimeThrustResidual;
				stateShadowRuntimeThrustResidualMax = Math.max(
						stateShadowRuntimeThrustResidualMax,
						runtimeThrustResidual
				);
				stateShadowRuntimePowerResidualSum += runtimePowerResidual;
				stateShadowRuntimePowerResidualMax = Math.max(
						stateShadowRuntimePowerResidualMax,
						runtimePowerResidual
				);
				stateShadowRuntimeShaftTorqueResidualSum += runtimeShaftTorqueResidual;
				stateShadowRuntimeShaftTorqueResidualMax = Math.max(
						stateShadowRuntimeShaftTorqueResidualMax,
						runtimeShaftTorqueResidual
				);
			}
			if (staticAvailable > 0.0) {
				sawStaticReferenceState = true;
				double staticCt = Double.parseDouble(row[rotorStaticCtIndex]);
				double staticPower = Double.parseDouble(row[rotorStaticPowerIndex]);
				assertTrue(staticCt > 0.15 && staticCt < 0.17);
				assertTrue(staticPower > 0.0);
				assertTrue(Double.parseDouble(row[rotorStaticDiskLoadingIndex]) > 0.0);
				assertTrue(Double.parseDouble(row[rotorStaticInducedVelocityIndex]) > 0.0);
				assertTrue(Double.parseDouble(row[rotorStaticMomentumRatioIndex]) > 0.0);
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticThrustResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticPowerResidualIndex])));
				assertEquals(
						aerodynamicPower - staticPower,
						Double.parseDouble(row[rotorStaticPowerResidualIndex]),
						2.0e-3
				);
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticTorqueResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticInducedVelocityResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticThrustRatioIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticInducedVelocityRatioIndex])));
			}
		}
		assertTrue(sawRuntimeCoefficientState, "apDrone trace should expose runtime CT/CP/J coefficient telemetry");
		assertTrue(report.ctCpJRuntimeCoefficientRotorSampleCount() > 0);
		assertFiniteOrderedRange(
				report.minCtCpJRuntimeThrustCoefficientCt(),
				report.meanCtCpJRuntimeThrustCoefficientCt(),
				report.maxCtCpJRuntimeThrustCoefficientCt(),
				"runtime CT"
		);
		assertFiniteOrderedRange(
				report.minCtCpJRuntimePowerCoefficientCp(),
				report.meanCtCpJRuntimePowerCoefficientCp(),
				report.maxCtCpJRuntimePowerCoefficientCp(),
				"runtime CP"
		);
		assertFiniteOrderedRange(
				report.minCtCpJRuntimePropulsiveEfficiencyEta(),
				report.meanCtCpJRuntimePropulsiveEfficiencyEta(),
				report.maxCtCpJRuntimePropulsiveEfficiencyEta(),
				"runtime eta"
		);
		assertTrue(report.minCtCpJRuntimeThrustCoefficientCt() > 0.0);
		assertTrue(report.minCtCpJRuntimePowerCoefficientCp() > 0.0);
		assertTrue(report.minCtCpJRuntimePropulsiveEfficiencyEta() >= 0.0);
		assertTrue(Double.isFinite(report.meanCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter()));
		assertTrue(Double.isFinite(report.maxCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter()));
		assertTrue(report.meanCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter() > 0.0);
		assertTrue(report.maxCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter()
				>= report.meanCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter());
		assertTrue(Double.isFinite(report.meanCtCpJRuntimeIdealMomentumPowerOverShaftPower()));
		assertTrue(Double.isFinite(report.maxCtCpJRuntimeIdealMomentumPowerOverShaftPower()));
		assertTrue(report.meanCtCpJRuntimeIdealMomentumPowerOverShaftPower() > 0.0);
		assertTrue(report.maxCtCpJRuntimeIdealMomentumPowerOverShaftPower()
				>= report.meanCtCpJRuntimeIdealMomentumPowerOverShaftPower());
		assertTrue(sawReferenceState, "apDrone trace should expose available or blocked CT/CP/J reference telemetry");
		assertTrue(sawPositiveReferenceRpm, "CT/CP/J reference telemetry should preserve lookup RPM for envelope diagnosis");
		assertTrue(sawStaticReferenceState, "apDrone trace should expose static CT/CP shadow telemetry");
		assertTrue(report.ctCpJReferenceRotorSampleCount() > 0);
		assertEquals(referenceFlowSampleCount, report.ctCpJReferenceRotorSampleCount());
		assertEquals(
				referenceRelativeAirSpeedSum / referenceFlowSampleCount,
				report.meanCtCpJReferenceRelativeAirSpeedMetersPerSecond(),
				5.0e-5
		);
		assertEquals(
				referenceRelativeAirSpeedMax,
				report.maxCtCpJReferenceRelativeAirSpeedMetersPerSecond(),
				5.0e-5
		);
		assertEquals(
				referenceTransverseAirSpeedSum / referenceFlowSampleCount,
				report.meanCtCpJReferenceTransverseAirSpeedMetersPerSecond(),
				5.0e-5
		);
		assertEquals(
				referenceTransverseAirSpeedMax,
				report.maxCtCpJReferenceTransverseAirSpeedMetersPerSecond(),
				5.0e-5
		);
		assertEquals(
				referenceInflowAngleSum / referenceFlowSampleCount,
				report.meanCtCpJReferenceInflowAngleDegrees(),
				5.0e-4
		);
		assertEquals(
				referenceInflowAngleMax,
				report.maxCtCpJReferenceInflowAngleDegrees(),
				5.0e-4
		);
		assertEquals(
				referenceTipMachSum / referenceFlowSampleCount,
				report.meanCtCpJReferenceTipMach(),
				5.0e-5
		);
		assertEquals(
				referenceTipMachMax,
				report.maxCtCpJReferenceTipMach(),
				5.0e-5
		);
		assertEquals(
				referenceReynoldsNumberSum / referenceFlowSampleCount,
				report.meanCtCpJReferenceReynoldsNumber(),
				0.2
		);
		assertEquals(
				referenceReynoldsNumberMax,
				report.maxCtCpJReferenceReynoldsNumber(),
				0.2
		);
		assertEquals(
				referenceReynoldsIndexSum / referenceFlowSampleCount,
				report.meanCtCpJReferenceReynoldsIndex(),
				5.0e-5
		);
		assertEquals(
				referenceReynoldsIndexMax,
				report.maxCtCpJReferenceReynoldsIndex(),
				5.0e-5
		);
		assertEquals(
				report.ctCpJReferenceRotorSampleCount(),
				report.ctCpJReferenceAvailableRotorSampleCount() + report.ctCpJReferenceBlockedRotorSampleCount()
		);
		assertTrue(report.ctCpJReferenceCoverageFraction() >= 0.0);
		assertTrue(report.ctCpJReferenceCoverageFraction() <= 1.0);
		assertTrue(report.ctCpJReferenceAvailableRotorSampleCount() > 0);
		assertEquals(0, report.ctCpJReferenceBlockedRotorSampleCount());
		assertEquals(
				report.ctCpJReferenceAvailableRotorSampleCount(),
				report.ctCpJReferenceRuntimeAppliedRotorSampleCount()
						+ report.ctCpJReferenceClampedRotorSampleCount()
		);
		assertTrue(report.ctCpJReferenceRuntimeAppliedRotorSampleCount() > 0);
		assertTrue(report.ctCpJReferenceRuntimeAppliedCoverageFraction() > 0.0);
		assertTrue(report.ctCpJReferenceRuntimeAppliedCoverageFraction()
				<= report.ctCpJReferenceCoverageFraction());
		assertTrue(report.ctCpJReferenceClampedRotorSampleCount()
				<= report.ctCpJReferenceAvailableRotorSampleCount());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.meanCtCpJReferenceForceVectorResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceForceVectorResidualNewtons()));
		assertTrue(report.maxCtCpJReferenceForceVectorResidualNewtons()
				>= report.meanCtCpJReferenceForceVectorResidualNewtons());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceTorqueVectorResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceTorqueVectorResidualNewtonMeters()));
		assertTrue(report.maxCtCpJReferenceTorqueVectorResidualNewtonMeters()
				>= report.meanCtCpJReferenceTorqueVectorResidualNewtonMeters());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsWakeAngularMomentumTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsWakeAngularMomentumTorqueResidualNewtonMeters()));
		assertTrue(report.maxCtCpJReferenceAbsWakeAngularMomentumTorqueResidualNewtonMeters()
				>= report.meanCtCpJReferenceAbsWakeAngularMomentumTorqueResidualNewtonMeters());
		assertEquals(0.0,
				report.maxCtCpJReferenceAbsWakeAngularMomentumTorqueResidualNewtonMeters(), 1.0e-12);
		assertTrue(Double.isFinite(report.meanCtCpJReferenceRuntimeAppliedAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceRuntimeAppliedAbsThrustResidualNewtons()));
		assertTrue(report.maxCtCpJReferenceRuntimeAppliedAbsThrustResidualNewtons()
				>= report.meanCtCpJReferenceRuntimeAppliedAbsThrustResidualNewtons());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceRuntimeAppliedAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceRuntimeAppliedAbsPowerResidualWatts()));
		assertTrue(report.maxCtCpJReferenceRuntimeAppliedAbsPowerResidualWatts()
				>= report.meanCtCpJReferenceRuntimeAppliedAbsPowerResidualWatts());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceRuntimeAppliedAbsTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceRuntimeAppliedAbsTorqueResidualNewtonMeters()));
		assertTrue(report.maxCtCpJReferenceRuntimeAppliedAbsTorqueResidualNewtonMeters()
				>= report.meanCtCpJReferenceRuntimeAppliedAbsTorqueResidualNewtonMeters());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceRuntimeAppliedForceVectorResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceRuntimeAppliedForceVectorResidualNewtons()));
		assertTrue(report.maxCtCpJReferenceRuntimeAppliedForceVectorResidualNewtons()
				>= report.meanCtCpJReferenceRuntimeAppliedForceVectorResidualNewtons());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceRuntimeAppliedTorqueVectorResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceRuntimeAppliedTorqueVectorResidualNewtonMeters()));
		assertTrue(report.maxCtCpJReferenceRuntimeAppliedTorqueVectorResidualNewtonMeters()
				>= report.meanCtCpJReferenceRuntimeAppliedTorqueVectorResidualNewtonMeters());
		assertTrue(Double.isFinite(
				report.meanCtCpJReferenceRuntimeAppliedAbsWakeAngularMomentumTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(
				report.maxCtCpJReferenceRuntimeAppliedAbsWakeAngularMomentumTorqueResidualNewtonMeters()));
		assertTrue(report.maxCtCpJReferenceRuntimeAppliedAbsWakeAngularMomentumTorqueResidualNewtonMeters()
				>= report.meanCtCpJReferenceRuntimeAppliedAbsWakeAngularMomentumTorqueResidualNewtonMeters());
		assertEquals(0.0,
				report.maxCtCpJReferenceRuntimeAppliedAbsWakeAngularMomentumTorqueResidualNewtonMeters(), 1.0e-12);
		assertTrue(stateShadowSampleCount > 0, "apDrone trace should expose CT/CP/J state-shadow telemetry");
		assertEquals(stateShadowSampleCount, report.ctCpJStateShadowFrameSampleCount());
		assertEquals(stateShadowAcceptedRotorCount, report.ctCpJStateShadowAcceptedRotorSampleCount());
		assertEquals(
				stateShadowRuntimeAcceptedRotorCount,
				report.ctCpJStateShadowRuntimeReplacementAcceptedRotorSampleCount()
		);
		assertEquals(stateShadowBlockedRotorCount, report.ctCpJStateShadowBlockedRotorSampleCount());
		assertEquals(stateShadowClampedRotorCount, report.ctCpJStateShadowClampedRotorSampleCount());
		assertEquals(
				stateShadowForceResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowAbsForceResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowForceResidualMax,
				report.maxCtCpJStateShadowAbsForceResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowTorqueResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowAbsTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertEquals(
				stateShadowTorqueResidualMax,
				report.maxCtCpJStateShadowAbsTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertEquals(
				stateShadowThrustResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowAbsThrustResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowThrustResidualMax,
				report.maxCtCpJStateShadowAbsThrustResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowPowerResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowAbsPowerResidualWatts(),
				5.0e-5
		);
		assertEquals(
				stateShadowPowerResidualMax,
				report.maxCtCpJStateShadowAbsPowerResidualWatts(),
				5.0e-5
		);
		assertEquals(
				stateShadowShaftTorqueResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowAbsShaftTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertEquals(
				stateShadowShaftTorqueResidualMax,
				report.maxCtCpJStateShadowAbsShaftTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertEquals(
				stateShadowRuntimeForceResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowRuntimeReplacementAbsForceResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowRuntimeForceResidualMax,
				report.maxCtCpJStateShadowRuntimeReplacementAbsForceResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowRuntimeTorqueResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowRuntimeReplacementAbsTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertEquals(
				stateShadowRuntimeTorqueResidualMax,
				report.maxCtCpJStateShadowRuntimeReplacementAbsTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertEquals(
				stateShadowRuntimeThrustResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowRuntimeReplacementAbsThrustResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowRuntimeThrustResidualMax,
				report.maxCtCpJStateShadowRuntimeReplacementAbsThrustResidualNewtons(),
				5.0e-5
		);
		assertEquals(
				stateShadowRuntimePowerResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowRuntimeReplacementAbsPowerResidualWatts(),
				5.0e-5
		);
		assertEquals(
				stateShadowRuntimePowerResidualMax,
				report.maxCtCpJStateShadowRuntimeReplacementAbsPowerResidualWatts(),
				5.0e-5
		);
		assertEquals(
				stateShadowRuntimeShaftTorqueResidualSum / stateShadowSampleCount,
				report.meanCtCpJStateShadowRuntimeReplacementAbsShaftTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertEquals(
				stateShadowRuntimeShaftTorqueResidualMax,
				report.maxCtCpJStateShadowRuntimeReplacementAbsShaftTorqueResidualNewtonMeters(),
				5.0e-6
		);
		assertTrue(report.ctCpJStaticReferenceRotorSampleCount() > 0);
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(report.maxCtCpJStaticReferenceAbsThrustResidualNewtons() > 0.0);
		assertTrue(report.maxCtCpJStaticReferenceAbsPowerResidualWatts() > 0.0);
		assertTrue(report.maxCtCpJStaticReferenceAbsTorqueResidualNewtonMeters() > 0.0);
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsInducedVelocityResidualMetersPerSecond()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsInducedVelocityResidualMetersPerSecond()));
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceIdealMomentumPowerOverShaftPower()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceIdealMomentumPowerOverShaftPower()));
		assertTrue(report.maxCtCpJStaticReferenceAbsInducedVelocityResidualMetersPerSecond() > 0.0);
		assertTrue(report.meanCtCpJStaticReferenceIdealMomentumPowerOverShaftPower() > 0.0);
	}

	@Test
	void nonAcceptedPresetDoesNotBorrowApDroneStateShadowReference() throws IOException {
		Path output = tempDir.resolve("racing_quad.csv");
		OfflineFlightRecorder.FlightReport report =
				OfflineFlightRecorder.record("racing_quad", output, 0.05);

		List<String> lines = Files.readAllLines(output);
		assertTrue(lines.size() > 1);
		String[] header = lines.get(0).split(",", -1);
		int stateShadowAvailableIndex = column(header, "rotor_ctcpj_state_ref_available");
		int stateShadowAcceptedRotorsIndex = column(header, "rotor_ctcpj_state_ref_accepted_rotors");
		int stateShadowForceResidualIndex = column(header, "rotor_ctcpj_state_ref_force_residual_n");
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			assertEquals(header.length, row.length, "CSV row " + i + " column count changed");
			assertEquals(0.0, Double.parseDouble(row[stateShadowAvailableIndex]), 1.0e-12);
			assertEquals(0.0, Double.parseDouble(row[stateShadowAcceptedRotorsIndex]), 1.0e-12);
			assertEquals(0.0, Double.parseDouble(row[stateShadowForceResidualIndex]), 1.0e-12);
		}
		assertEquals(0, report.ctCpJStateShadowFrameSampleCount());
		assertEquals(0, report.ctCpJStateShadowAcceptedRotorSampleCount());
		assertEquals(0.0, report.meanCtCpJStateShadowAbsForceResidualNewtons(), 1.0e-12);
		assertEquals(0.0, report.maxCtCpJStateShadowAbsForceResidualNewtons(), 1.0e-12);
	}

	private static int column(String[] header, String name) {
		for (int i = 0; i < header.length; i++) {
			if (name.equals(header[i])) {
				return i;
			}
		}
		throw new AssertionError("missing CSV column: " + name);
	}

	private static double magnitude(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}

	private static void assertFiniteOrderedRange(double min, double mean, double max, String label) {
		assertTrue(Double.isFinite(min), label + " min should be finite");
		assertTrue(Double.isFinite(mean), label + " mean should be finite");
		assertTrue(Double.isFinite(max), label + " max should be finite");
		assertTrue(min <= mean, label + " min should not exceed mean");
		assertTrue(mean <= max, label + " mean should not exceed max");
	}
}
