package com.tenicana.dronecraft.blackbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.ContactDynamics;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.PidGains;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

class DroneBlackboxRecorderTest {
	@Test
	void blackboxCsvKeepsHeaderAndRowsAlignedWithDiagnosticTelemetry() {
		DroneConfig config = DroneConfig.racingQuad();
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(16);
		DroneEnvironment environment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				new double[] {0.45, 0.0, 0.22, 0.0},
				0.18,
				new double[] {0.62, 0.36, 0.18, 0.0},
				0.36,
				7.5,
				null,
				new Vec3[] {new Vec3(0.42, 0.0, 0.0), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO},
				new double[] {0.16, 0.0, 0.08, 0.0},
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				true,
				0.82,
				-1450.0,
				0.37,
				0.44,
				1.25,
				true,
				"l2",
				"server_authoritative",
				7L,
				2.20,
				3.10,
				1.35,
				true,
				7.5,
				true,
				0.61,
				0.73,
				0.66,
				new Vec3(2.0, -1.0, 0.5)
		);

		for (int tick = 0; tick < 4; tick++) {
			DroneInput input = new DroneInput(0.46, 0.08, -0.06, 0.04, true, true, FlightMode.HORIZON);
			for (int substep = 0; substep < 10; substep++) {
				physics.step(input, 0.005, environment);
			}
			physics.state().setContactTelemetry(
					tick == 2 ? 4.5 : 0.0,
					tick == 2 ? 5.0 : 0.0,
					tick == 2 ? 0.6 : 0.0,
					tick == 2 ? new Vec3(0.0, Math.toRadians(540.0), 0.0) : Vec3.ZERO,
					tick == 2 ? new ContactDynamics.ContactSurface(0.72, 1.08, 1.70) : ContactDynamics.DEFAULT_SURFACE
			);
			if (tick == 2) {
				physics.state().addRotorSurfaceScrapeIntensity(1, 0.72);
			}
			recorder.record(DroneBlackboxSample.from(
					tick,
					tick,
					10,
					0.005,
					physics.state(),
					input,
					physics.state().averageMotorPower(config),
					1.0,
					physics.state().averageRotorHealth(),
					0.0,
					-1,
					0.0,
					0,
					new double[4],
					environment,
					config
			));
		}

		String csv = recorder.toCsv();
		String[] lines = csv.strip().split("\\R");
		String[] header = lines[0].split(",", -1);
		String[] row = lines[1].split(",", -1);
		String[] contactRow = lines[3].split(",", -1);

		assertEquals(header.length, row.length);
		assertEquals(header.length, contactRow.length);
		assertEquals(DroneBlackboxSample.CSV_HEADER.split(",", -1).length, header.length);
		assertTrue(csv.contains("physics_substeps"));
		assertTrue(csv.contains("physics_dt_s"));
		assertTrue(csv.contains("physics_rate_hz"));
		assertTrue(csv.contains("flight_model"));
		assertTrue(csv.contains("playable_low_altitude_authority"));
		assertTrue(csv.contains("playable_visual_pitch_deg"));
		assertTrue(csv.contains("playable_visual_yaw_deg"));
		assertTrue(csv.contains("playable_visual_roll_deg"));
		assertTrue(csv.contains("playable_visual_yaw_rate_dps"));
		assertTrue(csv.contains("control_frame_age_s"));
		assertTrue(csv.contains("control_frame_error"));
		assertTrue(csv.contains("esc_command_frame_age_s"));
		assertTrue(csv.contains("esc_command_frame_interval_s"));
		assertTrue(csv.contains("esc_command_error"));
		assertTrue(csv.contains("esc_electrical_output"));
		assertTrue(csv.contains("esc_electrical_error"));
		assertTrue(csv.contains("esc_7_electrical_output"));
		assertTrue(csv.contains("battery_resistance_aging_scale"));
		assertTrue(csv.contains("battery_capacity_aging_scale"));
		assertTrue(csv.contains("battery_soc_resistance_scale"));
		assertTrue(csv.contains("battery_temp_resistance_scale"));
		assertTrue(csv.contains("battery_polarization_resistance_scale"));
		assertTrue(csv.contains("battery_slow_polarization_v"));
		assertTrue(csv.contains("battery_20pct_sag_current_a"));
		assertTrue(csv.contains("battery_20pct_sag_current_margin"));
		assertTrue(csv.contains("mqtb_hq5x4x3_current_a"));
		assertTrue(csv.contains("mqtb_hq5x4x3_power_w"));
		assertTrue(csv.contains("mqtb_hq5x4x3_current_ratio"));
		assertTrue(csv.contains("mqtb_hq5x4x3_current_residual_a"));
		assertTrue(csv.contains("battery_equivalent_cycles"));
		assertTrue(csv.contains("pid_dterm_lpf_hz"));
		assertTrue(csv.contains("rotor_stall_intensity"));
		assertTrue(csv.contains("rotor_induced_lag_thrust_scale"));
		assertTrue(csv.contains("rotor_dynamic_inflow_tau_s"));
		assertTrue(csv.contains("rotor_0_dynamic_inflow_tau_s"));
		assertTrue(csv.contains("rotor_7_dynamic_inflow_tau_s"));
		assertTrue(csv.contains("rotor_translational_lift"));
		assertTrue(csv.contains("rotor_0_translational_lift"));
		assertTrue(csv.contains("rotor_3_translational_lift"));
		assertTrue(csv.contains("rotor_0_force_x_n"));
		assertTrue(csv.contains("rotor_3_force_z_n"));
		assertTrue(csv.contains("rotor_7_force_z_n"));
		assertTrue(csv.contains("rotor_0_torque_x_nm"));
		assertTrue(csv.contains("rotor_3_torque_z_nm"));
		assertTrue(csv.contains("rotor_7_torque_z_nm"));
		assertTrue(csv.contains("rotor_arm_flex"));
		assertTrue(csv.contains("rotor_0_arm_flex"));
		assertTrue(csv.contains("rotor_7_arm_flex"));
		assertTrue(csv.contains("rotor_arm_flex_deflection_mm"));
		assertTrue(csv.contains("rotor_0_arm_flex_deflection_mm"));
		assertTrue(csv.contains("rotor_7_arm_flex_deflection_mm"));
		assertTrue(csv.contains("rotor_arm_flex_tilt_deg"));
		assertTrue(csv.contains("rotor_0_arm_flex_tilt_deg"));
		assertTrue(csv.contains("rotor_7_arm_flex_tilt_deg"));
		assertTrue(csv.contains("rotor_advance_ratio"));
		assertTrue(csv.contains("rotor_0_advance_ratio"));
		assertTrue(csv.contains("rotor_3_advance_ratio"));
		assertTrue(csv.contains("rotor_prop_advance_ratio_j"));
		assertTrue(csv.contains("rotor_0_prop_advance_ratio_j"));
		assertTrue(csv.contains("rotor_3_prop_advance_ratio_j"));
		assertTrue(csv.contains("rotor_prop_thrust_scale"));
		assertTrue(csv.contains("rotor_0_prop_thrust_scale"));
		assertTrue(csv.contains("rotor_7_prop_thrust_scale"));
		assertTrue(csv.contains("rotor_prop_power_scale"));
		assertTrue(csv.contains("rotor_0_prop_power_scale"));
		assertTrue(csv.contains("rotor_7_prop_power_scale"));
		assertTrue(csv.contains("rotor_axial_gust_thrust_scale"));
		assertTrue(csv.contains("rotor_0_axial_gust_thrust_scale"));
		assertTrue(csv.contains("rotor_7_axial_gust_thrust_scale"));
		assertTrue(csv.contains("rotor_reverse_flow_fraction"));
		assertTrue(csv.contains("rotor_0_reverse_flow_fraction"));
		assertTrue(csv.contains("rotor_7_reverse_flow_fraction"));
		assertTrue(csv.contains("rotor_tip_mach"));
		assertTrue(csv.contains("rotor_0_tip_mach"));
		assertTrue(csv.contains("rotor_7_tip_mach"));
		assertTrue(csv.contains("rotor_compressibility_thrust_scale"));
		assertTrue(csv.contains("rotor_0_compressibility_thrust_scale"));
		assertTrue(csv.contains("rotor_7_compressibility_thrust_scale"));
		assertTrue(csv.contains("rotor_reynolds_number"));
		assertTrue(csv.contains("rotor_0_reynolds_number"));
		assertTrue(csv.contains("rotor_7_reynolds_number"));
		assertTrue(csv.contains("rotor_reynolds_index"));
		assertTrue(csv.contains("rotor_0_reynolds_index"));
		assertTrue(csv.contains("rotor_7_reynolds_index"));
		assertTrue(csv.contains("rotor_low_reynolds_loss"));
		assertTrue(csv.contains("rotor_7_low_reynolds_loss"));
		assertTrue(csv.contains("rotor_blade_aoa_deg"));
		assertTrue(csv.contains("rotor_0_blade_aoa_deg"));
		assertTrue(csv.contains("rotor_7_blade_aoa_deg"));
		assertTrue(csv.contains("rotor_blade_element_stall"));
		assertTrue(csv.contains("rotor_7_blade_element_stall"));
		assertTrue(csv.contains("rotor_blade_dissymmetry"));
		assertTrue(csv.contains("rotor_7_blade_dissymmetry"));
		assertTrue(csv.contains("rotor_blade_pass_ripple"));
		assertTrue(csv.contains("rotor_7_blade_pass_ripple"));
		assertTrue(csv.contains("rotor_damage_vibration"));
		assertTrue(csv.contains("rotor_7_damage_vibration"));
		assertTrue(csv.contains("rotor_flapping_tilt_deg"));
		assertTrue(csv.contains("rotor_7_flapping_tilt_deg"));
		assertTrue(csv.contains("rotor_coning"));
		assertTrue(csv.contains("rotor_0_coning"));
		assertTrue(csv.contains("rotor_7_coning"));
		assertTrue(csv.contains("rotor_coning_angle_deg"));
		assertTrue(csv.contains("rotor_0_coning_angle_deg"));
		assertTrue(csv.contains("rotor_7_coning_angle_deg"));
		assertTrue(csv.contains("rotor_aerodynamic_load"));
		assertTrue(csv.contains("rotor_0_aerodynamic_load"));
		assertTrue(csv.contains("rotor_3_aerodynamic_load"));
		assertTrue(csv.contains("rotor_in_plane_drag_force_n"));
		assertTrue(csv.contains("rotor_0_in_plane_drag_force_n"));
		assertTrue(csv.contains("rotor_7_in_plane_drag_force_n"));
		assertTrue(csv.contains("rotor_inflow_skew"));
		assertTrue(csv.contains("rotor_wake_interference"));
		assertTrue(csv.contains("rotor_3_wake_interference"));
		assertTrue(csv.contains("rotor_wake_thrust_scale"));
		assertTrue(csv.contains("rotor_3_wake_thrust_scale"));
		assertTrue(csv.contains("rotor_7_wake_thrust_scale"));
		assertTrue(csv.contains("rotor_coaxial_load_bias"));
		assertTrue(csv.contains("rotor_coaxial_load_bias_target"));
		assertTrue(csv.contains("rotor_coaxial_load_bias_clipping"));
		assertTrue(csv.contains("rotor_coaxial_allocation_ratio"));
		assertTrue(csv.contains("rotor_coaxial_allocation_mech_gain_pct"));
		assertTrue(csv.contains("rotor_coaxial_allocation_uncertainty_pct"));
		assertTrue(csv.contains("rotor_3_coaxial_load_bias"));
		assertTrue(csv.contains("rotor_7_coaxial_load_bias"));
		assertTrue(csv.contains("rotor_wet_thrust_scale"));
		assertTrue(csv.contains("rotor_3_wet_thrust_scale"));
		assertTrue(csv.contains("rotor_7_wet_thrust_scale"));
		assertTrue(csv.contains("rotor_icing_severity"));
		assertTrue(csv.contains("rotor_3_icing_severity"));
		assertTrue(csv.contains("rotor_7_icing_severity"));
		assertTrue(csv.contains("rotor_icing_thrust_scale"));
		assertTrue(csv.contains("rotor_7_icing_thrust_scale"));
		assertTrue(csv.contains("rotor_icing_power_scale"));
		assertTrue(csv.contains("rotor_7_icing_power_scale"));
		assertTrue(csv.contains("rotor_wake_swirl_mps"));
		assertTrue(csv.contains("rotor_3_wake_swirl_mps"));
		assertTrue(csv.contains("rotor_7_wake_swirl_mps"));
		assertTrue(csv.contains("rotor_windmilling"));
		assertTrue(csv.contains("rotor_3_windmilling"));
		assertTrue(csv.contains("rotor_7_windmilling"));
		assertTrue(csv.contains("rotor_wake_swirl_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_wake_swirl_yaw_torque_nm"));
		assertTrue(csv.contains("rotor_wake_swirl_roll_torque_nm"));
		assertTrue(csv.contains("rotor_active_braking_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_active_braking_yaw_torque_nm"));
		assertTrue(csv.contains("rotor_active_braking_roll_torque_nm"));
		assertTrue(csv.contains("rotor_flapping_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_flapping_yaw_torque_nm"));
		assertTrue(csv.contains("rotor_flapping_roll_torque_nm"));
		assertTrue(csv.contains("mixer_output_pitch_nm"));
		assertTrue(csv.contains("mixer_output_yaw_nm"));
		assertTrue(csv.contains("mixer_output_roll_nm"));
		assertTrue(csv.contains("mixer_pitch_authority"));
		assertTrue(csv.contains("mixer_yaw_authority"));
		assertTrue(csv.contains("mixer_roll_authority"));
		assertTrue(csv.contains("mixer_min_axis_authority"));
		assertTrue(csv.contains("mixer_low_saturation"));
		assertTrue(csv.contains("mixer_high_saturation"));
		assertTrue(csv.contains("mixer_low_headroom"));
		assertTrue(csv.contains("mixer_high_headroom"));
		assertTrue(csv.contains("pid_integral_relax_pitch"));
		assertTrue(csv.contains("pid_integral_relax_yaw"));
		assertTrue(csv.contains("pid_integral_relax_roll"));
		assertTrue(csv.contains("rotor_skew_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_skew_roll_torque_nm"));
		assertTrue(csv.contains("rotor_blade_dissymmetry_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_blade_dissymmetry_yaw_torque_nm"));
		assertTrue(csv.contains("rotor_blade_dissymmetry_roll_torque_nm"));
		assertTrue(csv.contains("rotor_inertia_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_inertia_roll_torque_nm"));
		assertTrue(csv.contains("rotor_acceleration_reaction_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_acceleration_reaction_roll_torque_nm"));
		assertTrue(csv.contains("rotor_gyroscopic_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_gyroscopic_roll_torque_nm"));
		assertTrue(csv.contains("rotor_angular_drag_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_angular_drag_roll_torque_nm"));
		assertTrue(csv.contains("airframe_angular_drag_pitch_torque_nm"));
		assertTrue(csv.contains("airframe_angular_drag_roll_torque_nm"));
		assertTrue(csv.contains("airframe_separation"));
		assertTrue(csv.contains("airframe_lift_n"));
		assertTrue(csv.contains("airframe_body_drag_n"));
		assertTrue(csv.contains("linear_damping_drag_n"));
		assertTrue(csv.contains("ground_effect_drag_n"));
		assertTrue(csv.contains("ground_effect_leveling_torque_nm"));
		assertTrue(csv.contains("rotor_wash_drag_n"));
		assertTrue(csv.contains("rotor_wall_effect_n"));
		assertTrue(csv.contains("contact_impact_mps"));
		assertTrue(csv.contains("contact_slip_mps"));
		assertTrue(csv.contains("contact_bounce_mps"));
		assertTrue(csv.contains("contact_surface_friction"));
		assertTrue(csv.contains("contact_surface_restitution"));
		assertTrue(csv.contains("contact_surface_scrape"));
		assertTrue(csv.contains("contact_angular_impulse_dps"));
		assertTrue(csv.contains("barometer_altitude_m"));
		assertTrue(csv.contains("barometer_vertical_speed_mps"));
		assertTrue(csv.contains("barometer_pressure_hpa"));
		assertTrue(csv.contains("barometer_error_m"));
		assertTrue(csv.contains("barometer_sensor_noise_m"));
		assertTrue(csv.contains("barometer_pressure_port_error_m"));
		assertTrue(csv.contains("barometer_propwash_error_m"));
		assertTrue(csv.contains("max_esc_temp_c"));
		assertTrue(csv.contains("esc_thermal_limit"));
		assertTrue(csv.contains("avg_esc_cooling_factor"));
		assertTrue(csv.contains("esc_0_temp_c"));
		assertTrue(csv.contains("esc_3_cooling_factor"));
		assertTrue(csv.contains("esc_desync"));
		assertTrue(csv.contains("esc_0_desync"));
		assertTrue(csv.contains("esc_3_desync"));
		assertTrue(csv.contains("rotor_0_health"));
		assertTrue(csv.contains("rotor_3_health"));
		assertTrue(csv.contains("prop_strike_severity"));
		assertTrue(csv.contains("drone_wake_intensity"));
		assertTrue(csv.contains("ceiling_clearance_m"));
		assertTrue(csv.contains("ceiling_effect_multiplier"));
		assertTrue(csv.contains("env_thrust_asymmetry"));
		assertTrue(csv.contains("rotor_0_env_thrust_multiplier"));
		assertTrue(csv.contains("rotor_3_env_thrust_multiplier"));
		assertTrue(csv.contains("rotor_flow_obstruction"));
		assertTrue(csv.contains("rotor_0_flow_obstruction"));
		assertTrue(csv.contains("rotor_3_flow_obstruction"));
		assertTrue(csv.contains("rotor_disk_wind_gradient_mps"));
		assertTrue(csv.contains("rotor_0_disk_wind_gradient_mps"));
		assertTrue(csv.contains("rotor_3_disk_wind_gradient_mps"));
		assertTrue(csv.contains("rotor_a4mc_shelter_obstruction"));
		assertTrue(csv.contains("rotor_0_a4mc_shelter_obstruction"));
		assertTrue(csv.contains("rotor_7_a4mc_shelter_obstruction"));
		assertTrue(csv.contains("water_immersion"));
		assertTrue(csv.contains("precipitation_wetness"));
		assertTrue(csv.contains("effective_air_density_ratio"));
		assertTrue(csv.contains("ambient_temperature_c"));
		assertTrue(csv.contains("rotor_0_water_immersion"));
		assertTrue(csv.contains("rotor_3_water_immersion"));
		assertTrue(csv.contains("rotor_0_precipitation_wetness"));
		assertTrue(csv.contains("rotor_3_precipitation_wetness"));
		assertTrue(csv.contains("propwash_wake_intensity"));
		assertTrue(csv.contains("vortex_ring_thrust_buffet"));
		assertTrue(csv.contains("vortex_ring_max_thrust_buffet"));
		assertTrue(csv.contains("vortex_ring_buffet_force_n"));
		assertTrue(csv.contains("avg_motor_cooling_factor"));
		assertTrue(csv.contains("motor_0_cooling_factor"));
		assertTrue(csv.contains("motor_3_cooling_factor"));
		assertTrue(csv.contains("avg_motor_accel_rad_s2"));
		assertTrue(csv.contains("motor_0_accel_rad_s2"));
		assertTrue(csv.contains("motor_3_accel_rad_s2"));
		assertTrue(csv.contains("avg_motor_aero_torque_nm"));
		assertTrue(csv.contains("motor_0_aero_torque_nm"));
		assertTrue(csv.contains("motor_3_aero_torque_nm"));
		assertTrue(csv.contains("motor_commutation_ripple"));
		assertTrue(csv.contains("motor_0_commutation_ripple"));
		assertTrue(csv.contains("motor_3_commutation_ripple"));
		assertTrue(csv.contains("motor_regen_current_a"));
		assertTrue(csv.contains("motor_0_regen_current_a"));
		assertTrue(csv.contains("motor_3_regen_current_a"));
		assertTrue(csv.contains("motor_phase_current_a"));
		assertTrue(csv.contains("motor_0_phase_current_a"));
		assertTrue(csv.contains("motor_3_phase_current_a"));
		assertTrue(csv.contains("motor_current_ripple_a"));
		assertTrue(csv.contains("motor_0_current_ripple_a"));
		assertTrue(csv.contains("motor_3_current_ripple_a"));
		assertTrue(csv.contains("motor_torque_ripple_nm"));
		assertTrue(csv.contains("motor_0_torque_ripple_nm"));
		assertTrue(csv.contains("motor_3_torque_ripple_nm"));
		assertTrue(csv.contains("avg_motor_mechanical_loss_torque_nm"));
		assertTrue(csv.contains("motor_0_mechanical_loss_torque_nm"));
		assertTrue(csv.contains("motor_3_mechanical_loss_torque_nm"));
		assertTrue(csv.contains("avg_motor_shaft_power_w"));
		assertTrue(csv.contains("motor_0_shaft_power_w"));
		assertTrue(csv.contains("motor_3_shaft_power_w"));
		assertTrue(csv.contains("motor_electrical_efficiency"));
		assertTrue(csv.contains("motor_0_electrical_efficiency"));
		assertTrue(csv.contains("motor_3_electrical_efficiency"));
		assertTrue(csv.contains("motor_voltage_headroom"));
		assertTrue(csv.contains("motor_0_voltage_headroom"));
		assertTrue(csv.contains("motor_3_voltage_headroom"));
		assertTrue(csv.contains("motor_winding_resistance_scale"));
		assertTrue(csv.contains("motor_0_winding_resistance_scale"));
		assertTrue(csv.contains("motor_3_winding_resistance_scale"));
		assertTrue(csv.contains("avg_motor_erpm100"));
		assertTrue(csv.contains("motor_0_erpm100"));
		assertTrue(csv.contains("motor_3_erpm100"));
		assertTrue(csv.contains("avg_motor_einterval_us"));
		assertTrue(csv.contains("motor_0_einterval_us"));
		assertTrue(csv.contains("motor_3_einterval_us"));
		assertTrue(csv.contains("avg_motor_rpm_telemetry_valid"));
		assertTrue(csv.contains("motor_0_rpm_telemetry_valid"));
		assertTrue(csv.contains("motor_3_rpm_telemetry_valid"));
		assertTrue(csv.contains("avg_motor_target_rpm"));
		assertTrue(csv.contains("avg_motor_target_erpm100"));
		assertTrue(csv.contains("avg_motor_target_einterval_us"));
		assertTrue(csv.contains("motor_0_target_rpm"));
		assertTrue(csv.contains("motor_0_target_erpm100"));
		assertTrue(csv.contains("motor_0_target_einterval_us"));
		assertTrue(csv.contains("motor_3_target_rpm"));
		assertTrue(csv.contains("motor_3_target_erpm100"));
		assertTrue(csv.contains("motor_3_target_einterval_us"));
		assertTrue(csv.contains("avg_motor_tracking_error"));
		assertTrue(csv.contains("motor_0_tracking_error"));
		assertTrue(csv.contains("motor_3_tracking_error"));
		assertTrue(csv.contains("avg_motor_actuator_authority"));
		assertTrue(csv.contains("motor_0_actuator_authority"));
		assertTrue(csv.contains("motor_3_actuator_authority"));
		assertTrue(csv.contains("battery_current_limit"));
		assertTrue(csv.contains("battery_regen_current_a"));
		assertTrue(csv.contains("battery_effective_resistance_ohm"));
		assertTrue(csv.contains("battery_20pct_sag_current_a"));
		assertTrue(csv.contains("battery_20pct_sag_current_margin"));
		assertTrue(csv.contains("mqtb_hq5x4x3_current_a"));
		assertTrue(csv.contains("mqtb_hq5x4x3_power_w"));
		assertTrue(csv.contains("mqtb_hq5x4x3_current_ratio"));
		assertTrue(csv.contains("mqtb_hq5x4x3_current_residual_a"));
		assertTrue(csv.contains("battery_voltage_spike_v"));
		assertTrue(csv.contains("battery_bus_ripple_v"));
		assertTrue(csv.contains("imu_supply_noise"));
		assertTrue(csv.contains("battery_temp_c"));
		assertTrue(csv.contains("battery_cooling_factor"));
		assertTrue(csv.contains("battery_thermal_limit"));
		assertTrue(csv.contains("effective_wind_x_mps"));
		assertTrue(csv.contains("effective_wind_y_mps"));
		assertTrue(csv.contains("effective_wind_z_mps"));
		assertTrue(csv.contains("wind_gust_speed_mps"));
		assertTrue(csv.contains("wind_dryden_speed_mps"));
		assertTrue(csv.contains("wind_burble_speed_mps"));
		assertTrue(csv.contains("wind_a4mc_source_gust_speed_mps"));
		assertTrue(csv.contains("wind_a4mc_source_gust_x_mps"));
		assertTrue(csv.contains("wind_a4mc_source_gust_y_mps"));
		assertTrue(csv.contains("wind_a4mc_source_gust_z_mps"));
		assertTrue(csv.contains("wind_a4mc_terrain_shear_speed_mps"));
		assertTrue(csv.contains("wind_shear_accel_mps2"));
		assertTrue(csv.contains("wind_source"));
		assertTrue(csv.contains("wind_source_confidence"));
		assertTrue(csv.contains("wind_source_quality"));
		assertTrue(csv.contains("wind_source_pressure_anomaly_pa"));
		assertTrue(csv.contains("wind_source_shelter_factor"));
		assertTrue(csv.contains("wind_source_level"));
		assertTrue(csv.contains("wind_source_authority"));
		assertTrue(csv.contains("wind_source_freshness_age_ticks"));
		assertTrue(csv.contains("wind_source_mean_speed_mps"));
		assertTrue(csv.contains("wind_source_effective_speed_mps"));
		assertTrue(csv.contains("wind_source_gust_speed_mps"));
		assertTrue(csv.contains("wind_source_gust_x_mps"));
		assertTrue(csv.contains("wind_source_gust_y_mps"));
		assertTrue(csv.contains("wind_source_gust_z_mps"));
		assertTrue(csv.contains("wind_source_humidity"));
		assertTrue(csv.contains("wind_source_abl_stability"));
		assertTrue(csv.contains("wind_source_abl_mixing_strength"));
		assertTrue(csv.contains("gyro_bias_pitch_dps"));
		assertTrue(csv.contains("gyro_clip"));
		assertTrue(csv.contains("gyro_blade_pass_notch_hz"));
		assertTrue(csv.contains("gyro_blade_pass_alias_1024_hz"));
		assertTrue(csv.contains("gyro_blade_pass_alias_4000_hz"));
		assertTrue(csv.contains("gyro_blade_pass_notch_attenuation"));
		assertTrue(csv.contains("gyro_notch_spread_hz"));
		assertTrue(csv.contains("gyro_rpm_harmonic_notch_attenuation"));
		assertTrue(csv.contains("gyro_blade_pass_notch_spread_hz"));
		assertTrue(csv.contains("accel_bias_x_mps2"));
		assertTrue(csv.contains("accel_clip"));
		assertTrue(csv.contains("airframe_pressure_center_pitch_torque_nm"));
		assertTrue(csv.contains("airframe_drag_along_flow_n"));
		assertTrue(csv.contains("airframe_drag_equivalent_linear_k"));
		assertTrue(csv.contains("airframe_drag_equivalent_cda_m2"));
		assertTrue(csv.contains("airframe_drag_imav_ratio"));
		assertTrue(csv.contains("rotor_surface_scrape"));
		assertTrue(csv.contains("rotor_1_surface_scrape"));
		assertTrue(csv.contains("tune_rotor_blade_pitch_m"));
		assertTrue(csv.contains("tune_rotor_pitch_to_diameter"));
		assertTrue(csv.contains("tune_rotor_pitch_angle_70r_deg"));
		assertTrue(csv.contains("tune_rotor_blade_count"));
		assertTrue(csv.contains("tune_motor_pole_pairs"));
		assertTrue(csv.contains("tune_rotor_stall_loss"));
		assertTrue(csv.contains("tune_rotor_imbalance"));
		assertTrue(csv.contains("tune_rc_frame_rate_hz"));
		assertTrue(csv.contains("tune_rc_resolution_steps"));
		assertTrue(csv.contains("tune_esc_command_frame_rate_hz"));
		assertTrue(csv.contains("tune_esc_command_resolution_steps"));
		assertTrue(csv.contains("tune_esc_dshot_bitrate_kbit_s"));
		assertTrue(csv.contains("tune_esc_dshot_raw_frame_us"));
		assertTrue(csv.contains("tune_esc_dshot_wire_utilization"));
		assertTrue(csv.contains("tune_esc_command_interval_raw_frame_ratio"));
		assertTrue(csv.contains("tune_cg_x_m"));
		assertTrue(csv.contains("tune_cg_y_m"));
		assertTrue(csv.contains("tune_cg_z_m"));
		assertTrue(csv.contains("tune_imu_x_m"));
		assertTrue(csv.contains("tune_imu_y_m"));
		assertTrue(csv.contains("tune_imu_z_m"));
		assertTrue(csv.contains("tune_cp_x_m"));
		assertTrue(csv.contains("tune_cp_y_m"));
		assertTrue(csv.contains("tune_cp_z_m"));
		assertTrue(csv.contains("tune_rotor_outward_cant_deg"));
		assertTrue(csv.contains("flight_mode"));
		assertTrue(csv.contains("horizon"));
		assertFalse(csv.contains("NaN"));
		assertFalse(csv.contains("Infinity"));
		assertEquals("10", row[indexOf(header, "physics_substeps")]);
		assertEquals(0.005, Double.parseDouble(row[indexOf(header, "physics_dt_s")]), 0.000001);
		assertEquals(200.0, Double.parseDouble(row[indexOf(header, "physics_rate_hz")]), 0.001);
		assertEquals("simulation", row[indexOf(header, "flight_model")]);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "control_frame_age_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "control_frame_interval_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "control_frame_error")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_command_frame_age_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_command_frame_interval_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_command_error")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_electrical_output")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_electrical_error")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_7_electrical_output")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rotor_blade_pitch_m")]));
		assertEquals(0.85, Double.parseDouble(row[indexOf(header, "tune_rotor_pitch_to_diameter")]), 0.00001);
		assertEquals(21.13, Double.parseDouble(row[indexOf(header, "tune_rotor_pitch_angle_70r_deg")]), 0.02);
		assertEquals(3.0, Double.parseDouble(row[indexOf(header, "tune_rotor_blade_count")]), 0.0001);
		assertEquals(RotorSpec.DEFAULT_MOTOR_POLE_PAIRS, Double.parseDouble(row[indexOf(header, "tune_motor_pole_pairs")]), 0.0001);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rotor_stall_loss")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rotor_imbalance")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rc_frame_rate_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rc_resolution_steps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_esc_command_frame_rate_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_esc_command_resolution_steps")]));
		assertEquals(600.0, Double.parseDouble(row[indexOf(header, "tune_esc_dshot_bitrate_kbit_s")]), 1.0e-9);
		assertEquals(26.667, Double.parseDouble(row[indexOf(header, "tune_esc_dshot_raw_frame_us")]), 0.001);
		assertEquals(0.01067, Double.parseDouble(row[indexOf(header, "tune_esc_dshot_wire_utilization")]), 0.00001);
		assertEquals(93.75, Double.parseDouble(row[indexOf(header, "tune_esc_command_interval_raw_frame_ratio")]), 0.01);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "tune_cg_x_m")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "tune_cg_z_m")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "tune_imu_x_m")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "tune_imu_z_m")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "tune_cp_x_m")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "tune_cp_z_m")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "tune_rotor_outward_cant_deg")]), 0.0001);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_pressure_center_pitch_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_bias_pitch_dps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_clip")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_blade_pass_notch_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_blade_pass_alias_1024_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_blade_pass_alias_4000_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_blade_pass_notch_attenuation")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_notch_spread_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_rpm_harmonic_notch_attenuation")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_blade_pass_notch_spread_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "accel_bias_x_mps2")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "accel_clip")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_drag_along_flow_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_drag_equivalent_linear_k")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_drag_equivalent_cda_m2")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_drag_imav_ratio")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_advance_ratio")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_0_advance_ratio")]));
		double loggedRotorAdvanceRatio = Double.parseDouble(row[indexOf(header, "rotor_advance_ratio")]);
		double loggedRotorPropAdvanceRatioJ = Double.parseDouble(row[indexOf(header, "rotor_prop_advance_ratio_j")]);
		assertEquals(Math.PI * loggedRotorAdvanceRatio, loggedRotorPropAdvanceRatioJ, 1.0e-4);
		double loggedRotorPropellerThrustScale = Double.parseDouble(row[indexOf(header, "rotor_prop_thrust_scale")]);
		assertTrue(loggedRotorPropellerThrustScale > 0.0);
		assertTrue(loggedRotorPropellerThrustScale <= 1.08);
		double loggedRotorPropellerPowerScale = Double.parseDouble(row[indexOf(header, "rotor_prop_power_scale")]);
		assertTrue(loggedRotorPropellerPowerScale > 0.0);
		assertTrue(loggedRotorPropellerPowerScale <= 1.08);
		double loggedRotorReverseFlow = Double.parseDouble(row[indexOf(header, "rotor_reverse_flow_fraction")]);
		assertUnitInterval(loggedRotorReverseFlow);
		assertTrue(loggedRotorReverseFlow <= Math.min(1.0, loggedRotorAdvanceRatio) + 0.02);
		double loggedRotor0AdvanceRatio = Double.parseDouble(row[indexOf(header, "rotor_0_advance_ratio")]);
		double loggedRotor0PropAdvanceRatioJ = Double.parseDouble(row[indexOf(header, "rotor_0_prop_advance_ratio_j")]);
		assertEquals(Math.PI * loggedRotor0AdvanceRatio, loggedRotor0PropAdvanceRatioJ, 1.0e-4);
		double loggedRotor0PropellerThrustScale = Double.parseDouble(row[indexOf(header, "rotor_0_prop_thrust_scale")]);
		assertTrue(loggedRotor0PropellerThrustScale > 0.0);
		assertTrue(loggedRotor0PropellerThrustScale <= 1.08);
		double loggedRotor0PropellerPowerScale = Double.parseDouble(row[indexOf(header, "rotor_0_prop_power_scale")]);
		assertTrue(loggedRotor0PropellerPowerScale > 0.0);
		assertTrue(loggedRotor0PropellerPowerScale <= 1.08);
		double loggedRotor0ReverseFlow = Double.parseDouble(row[indexOf(header, "rotor_0_reverse_flow_fraction")]);
		assertUnitInterval(loggedRotor0ReverseFlow);
		assertTrue(loggedRotor0ReverseFlow <= Math.min(1.0, loggedRotor0AdvanceRatio) + 0.02);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_blade_aoa_deg")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_0_blade_aoa_deg")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_blade_element_stall")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_blade_dissymmetry")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_blade_pass_ripple")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_flapping_tilt_deg")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_0_force_y_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_force_z_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_0_torque_y_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_torque_z_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_arm_flex")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_arm_flex")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_arm_flex_deflection_mm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_arm_flex_deflection_mm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_arm_flex_tilt_deg")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_arm_flex_tilt_deg")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_tip_mach")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_tip_mach")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_compressibility_thrust_scale")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_compressibility_thrust_scale")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_reynolds_number")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reynolds_number")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_reynolds_index")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reynolds_index")]) >= 0.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_low_reynolds_loss")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_low_reynolds_loss")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_wake_thrust_scale")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_wake_thrust_scale")]));
		double loggedWetThrustScale = Double.parseDouble(row[indexOf(header, "rotor_wet_thrust_scale")]);
		assertUnitInterval(loggedWetThrustScale);
		assertTrue(loggedWetThrustScale < 1.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_wet_thrust_scale")]));
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_icing_severity")]), 1.0e-5);
		assertEquals(1.0, Double.parseDouble(row[indexOf(header, "rotor_icing_thrust_scale")]), 1.0e-5);
		assertEquals(1.0, Double.parseDouble(row[indexOf(header, "rotor_icing_power_scale")]), 1.0e-5);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_7_icing_severity")]), 1.0e-5);
		assertEquals(1.0, Double.parseDouble(row[indexOf(header, "rotor_7_icing_thrust_scale")]), 1.0e-5);
		assertEquals(1.0, Double.parseDouble(row[indexOf(header, "rotor_7_icing_power_scale")]), 1.0e-5);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_pitch_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_yaw_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_roll_torque_nm")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_windmilling")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_0_windmilling")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_active_braking_pitch_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_active_braking_yaw_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_active_braking_roll_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_acceleration_reaction_pitch_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_acceleration_reaction_yaw_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_acceleration_reaction_roll_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_gyroscopic_pitch_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_gyroscopic_yaw_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_gyroscopic_roll_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_flapping_pitch_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_flapping_yaw_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_flapping_roll_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_angular_drag_roll_torque_nm")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "airframe_separation")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_lift_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_body_drag_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "linear_damping_drag_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "ground_effect_drag_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "ground_effect_leveling_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wash_drag_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wall_effect_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_impact_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_slip_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_bounce_mps")]));
		assertEquals(0.72, Double.parseDouble(contactRow[indexOf(header, "contact_surface_friction")]), 1.0e-4);
		assertEquals(1.08, Double.parseDouble(contactRow[indexOf(header, "contact_surface_restitution")]), 1.0e-4);
		assertEquals(1.70, Double.parseDouble(contactRow[indexOf(header, "contact_surface_scrape")]), 1.0e-4);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_angular_impulse_dps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_surface_scrape")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_1_surface_scrape")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_altitude_m")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_pressure_hpa")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_error_m")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_sensor_noise_m")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_pressure_port_error_m")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "max_esc_temp_c")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_thermal_limit")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_esc_cooling_factor")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_regen_current_a")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "battery_effective_resistance_ohm")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "battery_20pct_sag_current_a")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "battery_20pct_sag_current_margin")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "mqtb_hq5x4x3_current_a")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "mqtb_hq5x4x3_power_w")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "mqtb_hq5x4x3_current_ratio")]) > 0.0);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "mqtb_hq5x4x3_current_residual_a")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "battery_soc_resistance_scale")]) >= 1.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "battery_temp_resistance_scale")]) >= 1.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "battery_polarization_resistance_scale")]) >= 1.0);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_slow_polarization_v")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_voltage_spike_v")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_bus_ripple_v")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "imu_supply_noise")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_temp_c")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_cooling_factor")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_thermal_limit")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "effective_wind_x_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_gust_speed_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_dryden_speed_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_burble_speed_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_a4mc_source_gust_speed_mps")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "wind_a4mc_source_gust_x_mps")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "wind_a4mc_source_gust_y_mps")]) < 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "wind_a4mc_source_gust_z_mps")]) > 0.0);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_a4mc_terrain_shear_speed_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_shear_accel_mps2")]));
		assertEquals("aerodynamics4mc", row[indexOf(header, "wind_source")]);
		assertEquals("true", row[indexOf(header, "wind_source_trusted")]);
		assertEquals(0.82, Double.parseDouble(row[indexOf(header, "wind_source_confidence")]), 1.0e-5);
		assertEquals(0.82, Double.parseDouble(row[indexOf(header, "wind_source_quality")]), 1.0e-5);
		assertEquals(-1450.0, Double.parseDouble(row[indexOf(header, "wind_source_pressure_anomaly_pa")]), 1.0e-5);
		assertEquals(0.37, Double.parseDouble(row[indexOf(header, "wind_source_shear_mag_per_block")]), 1.0e-5);
		assertEquals(0.44, Double.parseDouble(row[indexOf(header, "wind_source_shelter_factor")]), 1.0e-5);
		assertEquals(1.25, Double.parseDouble(row[indexOf(header, "wind_source_updraft_mps")]), 1.0e-5);
		assertEquals("true", row[indexOf(header, "wind_source_local_voxel_flow")]);
		assertEquals("l2", row[indexOf(header, "wind_source_level")]);
		assertEquals("server_authoritative", row[indexOf(header, "wind_source_authority")]);
		assertEquals(7.0, Double.parseDouble(row[indexOf(header, "wind_source_freshness_age_ticks")]), 1.0e-5);
		assertEquals(2.20, Double.parseDouble(row[indexOf(header, "wind_source_mean_speed_mps")]), 1.0e-5);
		assertEquals(3.10, Double.parseDouble(row[indexOf(header, "wind_source_effective_speed_mps")]), 1.0e-5);
		assertEquals(1.35, Double.parseDouble(row[indexOf(header, "wind_source_gust_speed_mps")]), 1.0e-5);
		assertEquals(2.0, Double.parseDouble(row[indexOf(header, "wind_source_gust_x_mps")]), 1.0e-5);
		assertEquals(-1.0, Double.parseDouble(row[indexOf(header, "wind_source_gust_y_mps")]), 1.0e-5);
		assertEquals(0.5, Double.parseDouble(row[indexOf(header, "wind_source_gust_z_mps")]), 1.0e-5);
		assertEquals("true", row[indexOf(header, "wind_source_has_temperature")]);
		assertEquals(7.5, Double.parseDouble(row[indexOf(header, "wind_source_temperature_c")]), 1.0e-5);
		assertEquals("true", row[indexOf(header, "wind_source_has_humidity")]);
		assertEquals(0.61, Double.parseDouble(row[indexOf(header, "wind_source_humidity")]), 1.0e-5);
		assertEquals(0.73, Double.parseDouble(row[indexOf(header, "wind_source_abl_stability")]), 1.0e-5);
		assertEquals(0.66, Double.parseDouble(row[indexOf(header, "wind_source_abl_mixing_strength")]), 1.0e-5);
		assertEquals(0.42, Double.parseDouble(row[indexOf(header, "rotor_disk_wind_gradient_mps")]), 1.0e-5);
		assertEquals(0.42, Double.parseDouble(row[indexOf(header, "rotor_0_disk_wind_gradient_mps")]), 1.0e-5);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_3_disk_wind_gradient_mps")]), 1.0e-9);
		assertEquals(0.16, Double.parseDouble(row[indexOf(header, "rotor_a4mc_shelter_obstruction")]), 1.0e-5);
		assertEquals(0.16, Double.parseDouble(row[indexOf(header, "rotor_0_a4mc_shelter_obstruction")]), 1.0e-5);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_3_a4mc_shelter_obstruction")]), 1.0e-9);
		assertEquals(0.18, Double.parseDouble(row[indexOf(header, "water_immersion")]), 0.0001);
		assertEquals(0.36, Double.parseDouble(row[indexOf(header, "precipitation_wetness")]), 0.0001);
		assertEquals(7.5, Double.parseDouble(row[indexOf(header, "ambient_temperature_c")]), 0.0001);
		double airDensity = Double.parseDouble(row[indexOf(header, "air_density_ratio")]);
		double effectiveAirDensity = Double.parseDouble(row[indexOf(header, "effective_air_density_ratio")]);
		assertTrue(effectiveAirDensity < airDensity);
		assertTrue(effectiveAirDensity > airDensity * 0.997);
		assertEquals(0.45, Double.parseDouble(row[indexOf(header, "rotor_0_water_immersion")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_3_water_immersion")]), 0.0001);
		assertEquals(0.62, Double.parseDouble(row[indexOf(header, "rotor_0_precipitation_wetness")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_3_precipitation_wetness")]), 0.0001);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_electrical_efficiency")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_electrical_efficiency")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_voltage_headroom")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_voltage_headroom")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_winding_resistance_scale")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_winding_resistance_scale")]));
		double avgTelemetryValidity = Double.parseDouble(row[indexOf(header, "avg_motor_rpm_telemetry_valid")]);
		double avgMotorErpm100 = Double.parseDouble(row[indexOf(header, "avg_motor_erpm100")]);
		assertUnitInterval(avgTelemetryValidity);
		assertTrue(Double.isFinite(avgMotorErpm100));
		if (avgTelemetryValidity >= 0.5 && avgMotorErpm100 > 0.0) {
			double expectedAvgMotorEInterval = 600000.0 / avgMotorErpm100;
			assertEquals(
					expectedAvgMotorEInterval,
					Double.parseDouble(row[indexOf(header, "avg_motor_einterval_us")]),
					Math.max(1.0, expectedAvgMotorEInterval * 0.002)
			);
		} else {
			assertEquals(
					DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS,
					Double.parseDouble(row[indexOf(header, "avg_motor_einterval_us")]),
					1.0e-9
			);
		}
		double motor0TelemetryValidity = Double.parseDouble(row[indexOf(header, "motor_0_rpm_telemetry_valid")]);
		double motor0Erpm100 = Double.parseDouble(row[indexOf(header, "motor_0_erpm100")]);
		assertUnitInterval(motor0TelemetryValidity);
		assertTrue(Double.isFinite(motor0Erpm100));
		if (motor0TelemetryValidity >= 0.5 && motor0Erpm100 > 0.0) {
			double expectedMotor0EInterval = 600000.0 / motor0Erpm100;
			assertEquals(
					expectedMotor0EInterval,
					Double.parseDouble(row[indexOf(header, "motor_0_einterval_us")]),
					Math.max(1.0, expectedMotor0EInterval * 0.002)
			);
		} else {
			assertEquals(
					DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS,
					Double.parseDouble(row[indexOf(header, "motor_0_einterval_us")]),
					1.0e-9
			);
		}
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_motor_target_rpm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_motor_target_erpm100")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_motor_target_einterval_us")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_target_rpm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_target_erpm100")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_target_einterval_us")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_motor_tracking_error")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_tracking_error")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_motor_actuator_authority")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_actuator_authority")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "mixer_output_pitch_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "mixer_output_yaw_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "mixer_output_roll_nm")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_pitch_authority")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_yaw_authority")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_roll_authority")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_min_axis_authority")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_low_saturation")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_high_saturation")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_low_headroom")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "mixer_high_headroom")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_induced_lag_thrust_scale")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_dynamic_inflow_tau_s")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_dynamic_inflow_tau_s")]) <= 0.36);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_coning")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_0_coning")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coning_angle_deg")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_0_coning_angle_deg")]) >= 0.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "pid_integral_relax_pitch")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "pid_integral_relax_yaw")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "pid_integral_relax_roll")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_commutation_ripple")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_commutation_ripple")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_regen_current_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_regen_current_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_phase_current_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_phase_current_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_current_ripple_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_current_ripple_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_torque_ripple_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_torque_ripple_nm")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "avg_motor_mechanical_loss_torque_nm")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_0_mechanical_loss_torque_nm")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_axial_gust_thrust_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_axial_gust_thrust_scale")]) <= 2.5);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_0_axial_gust_thrust_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_0_axial_gust_thrust_scale")]) <= 2.5);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertEquals(4, summary.sampleCount());
		assertEquals(0, summary.playableFlightModelSamples());
		assertEquals(4, summary.simulationFlightModelSamples());
		DroneBlackboxSummary.WindSourceStats windSourceStats = summary.windSourceStats();
		assertEquals(4, windSourceStats.aerodynamics4McSamples());
		assertEquals(4, windSourceStats.trustedSourceSamples());
		assertEquals(0, windSourceStats.untrustedAerodynamics4McSamples());
		assertEquals(4, windSourceStats.localVoxelFlowSamples());
		assertEquals(0, windSourceStats.l0SourceSamples());
		assertEquals(0, windSourceStats.l1SourceSamples());
		assertEquals(4, windSourceStats.l2SourceSamples());
		assertEquals(7.0, windSourceStats.maxFreshnessAgeTicks(), 1.0e-5);
		assertEquals(0, windSourceStats.staleSourceSamples());
		assertEquals(2.20, windSourceStats.maxMeanSpeedMetersPerSecond(), 1.0e-5);
		assertEquals(3.10, windSourceStats.maxEffectiveSpeedMetersPerSecond(), 1.0e-5);
		assertEquals(1.35, windSourceStats.maxGustSpeedMetersPerSecond(), 1.0e-5);
		assertEquals(0.82, windSourceStats.maxConfidence(), 1.0e-5);
		assertEquals(0.82, windSourceStats.maxQualityFactor(), 1.0e-5);
		assertEquals(1450.0, windSourceStats.maxAbsPressureAnomalyPascals(), 1.0e-5);
		assertEquals(0.44, windSourceStats.maxShelterFactor(), 1.0e-5);
		assertEquals(0.37, windSourceStats.maxShearMagnitudePerBlock(), 1.0e-5);
		assertEquals(1.25, windSourceStats.maxAbsUpdraftMetersPerSecond(), 1.0e-5);
		assertEquals(0.73, windSourceStats.maxAbsAblStability(), 1.0e-5);
		assertEquals(0.66, windSourceStats.maxAblMixingStrength(), 1.0e-5);
		assertEquals(0.42, windSourceStats.maxRotorDiskWindGradientMetersPerSecond(), 1.0e-5);
		assertEquals(0.16, windSourceStats.maxRotorA4mcShelterObstruction(), 1.0e-5);
		assertEquals(10, summary.maxPhysicsSubsteps());
		assertEquals(200.0, summary.maxPhysicsRateHertz(), 0.001);
		assertEquals(1.0, summary.minPlayableLowAltitudeAuthority(), 1.0e-6);
		assertTrue(summary.maxSpeedMetersPerSecond() >= 0.0);
		assertUnitInterval(summary.maxAirframeSeparatedFlowIntensity());
		assertTrue(summary.maxAirframeLiftForceNewtons() >= 0.0);
		assertTrue(summary.maxAirframeBodyDragForceNewtons() >= 0.0);
		assertTrue(summary.maxLinearDampingDragForceNewtons() >= 0.0);
		assertTrue(summary.maxGroundEffectDragForceNewtons() >= 0.0);
		assertEquals(
				maxOfColumns(lines, header, "ground_effect_leveling_torque_nm"),
				summary.maxGroundEffectLevelingTorqueNewtonMeters(),
				1.0e-5
		);
		assertTrue(summary.maxRotorWashDragForceNewtons() >= 0.0);
		assertTrue(summary.maxRotorWallEffectForceNewtons() >= 0.0);
		assertEquals(4.5, summary.maxContactImpactSpeedMetersPerSecond(), 0.0001);
		assertEquals(5.0, summary.maxContactSlipSpeedMetersPerSecond(), 0.0001);
		assertEquals(0.6, summary.maxContactBounceSpeedMetersPerSecond(), 0.0001);
		assertEquals(540.0, summary.maxContactAngularImpulseDegreesPerSecond(), 0.0001);
		assertEquals(0.72, summary.minContactSurfaceFrictionMultiplier(), 0.0001);
		assertEquals(1.00, summary.maxContactSurfaceFrictionMultiplier(), 0.0001);
		assertEquals(1.00, summary.minContactSurfaceRestitutionMultiplier(), 0.0001);
		assertEquals(1.08, summary.maxContactSurfaceRestitutionMultiplier(), 0.0001);
		assertEquals(1.00, summary.minContactSurfaceScrapeMultiplier(), 0.0001);
		assertEquals(1.70, summary.maxContactSurfaceScrapeMultiplier(), 0.0001);
		assertTrue(summary.formatForChat().contains("surface 0.72..1.00/1.00..1.08/1.00..1.70"));
		assertEquals(0.18, summary.maxRotorSurfaceScrapeIntensity(), 0.0001);
		assertTrue(summary.maxRotorConingIntensity() >= 0.0);
		assertTrue(summary.maxRotorConingAngleDegrees() >= 0.0);
		assertTrue(summary.maxRotorFlappingTiltDegrees() >= 0.0);
		assertTrue(summary.maxRotorInducedVelocityMetersPerSecond() > 0.0);
		assertUnitInterval(summary.minRotorInducedLagThrustScale());
		assertTrue(summary.maxRotorAdvanceRatio() >= 0.0);
		assertEquals(
				maxOfColumns(
						lines,
						header,
						"rotor_prop_advance_ratio_j",
						"rotor_0_prop_advance_ratio_j",
						"rotor_1_prop_advance_ratio_j",
						"rotor_2_prop_advance_ratio_j",
						"rotor_3_prop_advance_ratio_j",
						"rotor_4_prop_advance_ratio_j",
						"rotor_5_prop_advance_ratio_j",
						"rotor_6_prop_advance_ratio_j",
						"rotor_7_prop_advance_ratio_j"
				),
				summary.maxRotorPropellerAdvanceRatioJ(),
				1.0e-5
		);
		assertEquals(
				minOfColumns(
						lines,
						header,
						"rotor_prop_thrust_scale",
						"rotor_0_prop_thrust_scale",
						"rotor_1_prop_thrust_scale",
						"rotor_2_prop_thrust_scale",
						"rotor_3_prop_thrust_scale",
						"rotor_4_prop_thrust_scale",
						"rotor_5_prop_thrust_scale",
						"rotor_6_prop_thrust_scale",
						"rotor_7_prop_thrust_scale"
				),
				summary.minRotorPropellerThrustScale(),
				1.0e-5
		);
		assertEquals(
				minOfColumns(
						lines,
						header,
						"rotor_prop_power_scale",
						"rotor_0_prop_power_scale",
						"rotor_1_prop_power_scale",
						"rotor_2_prop_power_scale",
						"rotor_3_prop_power_scale",
						"rotor_4_prop_power_scale",
						"rotor_5_prop_power_scale",
						"rotor_6_prop_power_scale",
						"rotor_7_prop_power_scale"
				),
				summary.minRotorPropellerPowerScale(),
				1.0e-5
		);
		assertEquals(
				minOfColumns(
						lines,
						header,
						"rotor_axial_gust_thrust_scale",
						"rotor_0_axial_gust_thrust_scale",
						"rotor_1_axial_gust_thrust_scale",
						"rotor_2_axial_gust_thrust_scale",
						"rotor_3_axial_gust_thrust_scale",
						"rotor_4_axial_gust_thrust_scale",
						"rotor_5_axial_gust_thrust_scale",
						"rotor_6_axial_gust_thrust_scale",
						"rotor_7_axial_gust_thrust_scale"
				),
				summary.minRotorAxialGustThrustScale(),
				1.0e-5
		);
		assertEquals(
				maxOfColumns(
						lines,
						header,
						"rotor_axial_gust_thrust_scale",
						"rotor_0_axial_gust_thrust_scale",
						"rotor_1_axial_gust_thrust_scale",
						"rotor_2_axial_gust_thrust_scale",
						"rotor_3_axial_gust_thrust_scale",
						"rotor_4_axial_gust_thrust_scale",
						"rotor_5_axial_gust_thrust_scale",
						"rotor_6_axial_gust_thrust_scale",
						"rotor_7_axial_gust_thrust_scale"
				),
				summary.maxRotorAxialGustThrustScale(),
				1.0e-5
		);
		assertEquals(
				maxOfColumns(
						lines,
						header,
						"rotor_reverse_flow_fraction",
						"rotor_0_reverse_flow_fraction",
						"rotor_1_reverse_flow_fraction",
						"rotor_2_reverse_flow_fraction",
						"rotor_3_reverse_flow_fraction",
						"rotor_4_reverse_flow_fraction",
						"rotor_5_reverse_flow_fraction",
						"rotor_6_reverse_flow_fraction",
						"rotor_7_reverse_flow_fraction"
				),
				summary.maxRotorReverseFlowInboardFraction(),
				1.0e-5
		);
		assertTrue(summary.formatForChat().contains(" ppwr "));
		assertTrue(summary.formatForChat().contains(" agust "));
		assertTrue(summary.maxRotorTipMach() >= 0.0);
		assertUnitInterval(summary.minRotorCompressibilityThrustScale());
		assertUnitInterval(summary.maxRotorLowReynoldsLoss());
		assertTrue(summary.maxRotorWakeInterferenceIntensity() >= 0.0);
		assertUnitInterval(summary.maxRotorWindmillingIntensity());
		assertTrue(summary.maxRotorArmFlexIntensity() >= 0.0);
		assertTrue(summary.maxRotorArmFlexDeflectionMillimeters() >= 0.0);
		assertTrue(summary.maxRotorArmFlexTiltDegrees() >= 0.0);
		assertTrue(summary.maxRotorBladeDissymmetryTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorAccelerationReactionTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorGyroscopicTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorFlappingTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorAngularDragTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxControlFrameAgeSeconds() >= 0.0);
		assertTrue(summary.maxControlFrameError() >= 0.0);
		assertTrue(summary.maxBarometerErrorMeters() >= 0.0);
		assertTrue(summary.maxBarometerPropwashErrorMeters() >= 0.0);
		assertTrue(summary.minBarometerPressureHectopascals() > 0.0);
		assertTrue(summary.maxEscTemperatureCelsius() >= 25.0);
		assertTrue(summary.minMotorElectricalEfficiency() > 0.0);
		assertTrue(summary.minMotorVoltageHeadroom() >= 0.0);
		assertTrue(summary.maxMotorWindingResistanceScale() >= 1.0);
		assertTrue(summary.minMixerAxisAuthority() >= 0.0);
		assertTrue(summary.minMixerAxisAuthority() <= 1.0);
		assertUnitInterval(summary.maxMixerLowSaturation());
		assertUnitInterval(summary.maxMixerHighSaturation());
		assertUnitInterval(summary.minMixerLowHeadroom());
		assertUnitInterval(summary.minMixerHighHeadroom());
		assertTrue(summary.maxMotorMechanicalLossTorqueNewtonMeters() > 0.0);
		assertTrue(summary.minEscThermalLimit() > 0.0);
		assertTrue(summary.maxBatteryCurrentAmps() > 0.0);
		assertTrue(summary.maxBatteryRegenerativeCurrentAmps() >= 0.0);
		assertTrue(summary.maxMotorRegenerativeCurrentAmps() >= 0.0);
		assertTrue(summary.maxBatteryEffectiveResistanceOhms() > 0.0);
		assertEquals(
				maxOfColumns(lines, header, "battery_soc_resistance_scale"),
				summary.maxBatteryStateOfChargeResistanceScale(),
				1.0e-5
		);
		assertEquals(
				maxOfColumns(lines, header, "battery_temp_resistance_scale"),
				summary.maxBatteryTemperatureResistanceScale(),
				1.0e-5
		);
		assertEquals(
				maxOfColumns(lines, header, "battery_polarization_resistance_scale"),
				summary.maxBatteryPolarizationResistanceScale(),
				1.0e-5
		);
		assertTrue(summary.maxBatteryStateOfChargeResistanceScale() >= 1.0);
		assertTrue(summary.maxBatteryTemperatureResistanceScale() >= 1.0);
		assertTrue(summary.maxBatteryPolarizationResistanceScale() >= 1.0);
		assertTrue(summary.maxBatteryVoltageSpike() >= 0.0);
		assertTrue(summary.maxWindGustSpeedMetersPerSecond() >= 0.0);
		assertTrue(summary.maxWindDrydenSpeedMetersPerSecond() >= 0.0);
		assertTrue(summary.maxWindBurbleSpeedMetersPerSecond() >= 0.0);
		assertTrue(summary.maxWindA4mcSourceGustSpeedMetersPerSecond() >= 0.0);
		assertTrue(summary.maxWindA4mcTerrainShearSpeedMetersPerSecond() >= 0.0);
		assertEquals(
				maxOfColumns(lines, header, "wind_dryden_speed_mps"),
				summary.maxWindDrydenSpeedMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxOfColumns(lines, header, "wind_burble_speed_mps"),
				summary.maxWindBurbleSpeedMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxOfColumns(lines, header, "wind_a4mc_source_gust_speed_mps"),
				summary.maxWindA4mcSourceGustSpeedMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxOfColumns(lines, header, "wind_a4mc_terrain_shear_speed_mps"),
				summary.maxWindA4mcTerrainShearSpeedMetersPerSecond(),
				1.0e-5
		);
		assertTrue(summary.maxWindShearAccelerationMetersPerSecondSquared() >= 0.0);
		assertTrue(summary.maxRotorBladePassRippleIntensity() > 0.0);
		assertEquals(0.45, summary.maxWaterImmersionIntensity(), 0.0001);
		assertEquals(0.62, summary.maxPrecipitationWetnessIntensity(), 0.0001);
		assertTrue(summary.minRotorWetThrustScale() < 1.0);
		assertTrue(summary.minRotorWetThrustScale() >= 0.08);
		assertEquals(7.5, summary.minAmbientTemperatureCelsius(), 0.0001);
		assertEquals(7.5, summary.maxAmbientTemperatureCelsius(), 0.0001);
		assertTrue(summary.minBatteryVoltage() > 0.0);
		assertTrue(summary.formatForChat().contains("Blackbox"));
		assertTrue(summary.formatForChat().contains("loop 10@200Hz"));
		assertTrue(summary.formatForChat().contains("current-limit"));
		assertTrue(summary.formatForChat().contains("ir"));
		assertTrue(summary.formatForChat().contains("irx"));
		assertTrue(summary.formatForChat().contains("ripple"));
		assertTrue(summary.formatForChat().contains("imuP"));
		assertTrue(summary.formatForChat().contains("batt-limit"));
		assertTrue(summary.formatForChat().contains("track"));
		assertTrue(summary.formatForChat().contains("auth"));
		assertTrue(summary.formatForChat().contains("mix-auth"));
		assertTrue(summary.formatForChat().contains("dryden"));
		assertTrue(summary.formatForChat().contains("burble"));
		assertTrue(summary.formatForChat().contains("a4mcsrc"));
		assertTrue(summary.formatForChat().contains("a4mc 4/4"));
		assertTrue(summary.formatForChat().contains("trusted 4"));
		assertTrue(summary.formatForChat().contains("untrusted 0"));
		assertTrue(summary.formatForChat().contains("l2 4"));
		assertTrue(summary.formatForChat().contains("src 0/0/4"));
		assertTrue(summary.formatForChat().contains("age 7t"));
		assertTrue(summary.formatForChat().contains("stale 0"));
		assertTrue(summary.formatForChat().contains("srcwind 2.20/3.10/1.35"));
		assertTrue(summary.formatForChat().contains("conf 0.82"));
		assertTrue(summary.formatForChat().contains("q 0.82"));
		assertTrue(summary.formatForChat().contains("p 1450Pa"));
		assertTrue(summary.formatForChat().contains("shelter 0.44"));
		assertTrue(summary.formatForChat().contains("srcshear 0.37/m"));
		assertTrue(summary.formatForChat().contains("updraft 1.25m/s"));
		assertTrue(summary.formatForChat().contains("abl 0.73"));
		assertTrue(summary.formatForChat().contains("mix 0.66"));
		assertTrue(summary.formatForChat().contains("diskgrad 0.42m/s"));
		assertTrue(summary.formatForChat().contains("mix-edge"));
		assertTrue(summary.formatForChat().contains("mix-head"));
		assertTrue(summary.formatForChat().contains("regen"));
		assertTrue(summary.formatForChat().contains("motor-regen"));
		assertTrue(summary.formatForChat().contains("spike"));
		assertTrue(summary.formatForChat().contains("contact"));
		assertTrue(summary.formatForChat().contains("propwash"));
		assertTrue(summary.formatForChat().contains("vrsbuf"));
		assertTrue(summary.formatForChat().contains("vrsF"));
		assertTrue(summary.formatForChat().contains("ind"));
		assertTrue(summary.formatForChat().contains("iloss"));
		assertTrue(summary.formatForChat().contains("ETL"));
		assertTrue(summary.formatForChat().contains("adv"));
		assertTrue(summary.formatForChat().contains("tipmach"));
		assertTrue(summary.formatForChat().contains("machloss"));
		assertTrue(summary.formatForChat().contains("lowre"));
		assertTrue(summary.formatForChat().contains("bpass"));
		assertTrue(summary.formatForChat().contains("load"));
		assertTrue(summary.formatForChat().contains("skew"));
		assertTrue(summary.formatForChat().contains("bdiss"));
		assertTrue(summary.formatForChat().contains("rwake"));
		assertTrue(summary.formatForChat().contains("wmill"));
		assertTrue(summary.formatForChat().contains("rdamp"));
		assertTrue(summary.formatForChat().contains("ang-drag"));
		assertTrue(summary.formatForChat().contains("sep"));
		assertTrue(summary.formatForChat().contains("lift"));
		assertTrue(summary.formatForChat().contains("bodyD"));
		assertTrue(summary.formatForChat().contains("linD"));
		assertTrue(summary.formatForChat().contains("cushion"));
		assertTrue(summary.formatForChat().contains("glev"));
		assertTrue(summary.formatForChat().contains("wash"));
		assertTrue(summary.formatForChat().contains("wall"));
		assertTrue(summary.formatForChat().contains("coning"));
		assertTrue(summary.formatForChat().contains("flap"));
		assertTrue(summary.formatForChat().contains("flex"));
		assertTrue(summary.formatForChat().contains("scrape"));
		assertTrue(summary.formatForChat().contains("baro"));
		assertTrue(summary.formatForChat().contains("esc"));
		assertTrue(summary.formatForChat().contains("wake"));
		assertTrue(summary.formatForChat().contains("water"));
		assertTrue(summary.formatForChat().contains("rain"));
		assertTrue(summary.formatForChat().contains("wetloss"));
		assertTrue(summary.formatForChat().contains("ice"));
		assertTrue(summary.formatForChat().contains("iceloss"));
		assertTrue(summary.formatForChat().contains("icepwr"));
		assertTrue(summary.formatForChat().contains("temp 7.5..7.5C"));
		assertTrue(summary.formatForChat().contains("gust"));
		assertTrue(summary.formatForChat().contains("shear"));
		assertTrue(summary.formatForChat().contains("ceil"));
		assertTrue(summary.formatForChat().contains("asym"));
		assertTrue(summary.formatForChat().contains("block"));
		assertTrue(summary.formatForChat().contains("desync"));
		assertTrue(summary.formatForChat().contains("mech-loss"));
		assertTrue(summary.formatForChat().contains("eff"));
		assertTrue(summary.formatForChat().contains("headroom"));
		assertTrue(summary.formatForChat().contains("mR"));
		assertTrue(summary.formatForChat().contains("rotor min"));
		assertTrue(summary.formatForChat().contains("prop-strike"));
		assertTrue(summary.formatForChat().contains("rc-frame"));
	}

	@Test
	void blackboxSummarySeparatesUntrustedAerodynamics4McSamples() {
		DroneConfig config = DroneConfig.racingQuad();
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneInput input = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true, true, FlightMode.ACRO);
		DroneEnvironment environment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				null,
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				false,
				0.71,
				920.0,
				0.22,
				0.31,
				-0.40,
				true,
				"l1",
				"client_local",
				12L,
				1.20,
				1.40,
				0.60,
				false,
				0.0,
				false,
				0.0,
				0.0,
				0.0,
				new Vec3(0.45, 0.0, 0.20)
		);

		physics.step(input, 0.005, environment);
		recorder.record(DroneBlackboxSample.from(
				42,
				42,
				1,
				0.005,
				physics.state(),
				input,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				environment,
				config
		));

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		DroneBlackboxSummary.WindSourceStats windSourceStats = summary.windSourceStats();
		assertEquals(1, windSourceStats.aerodynamics4McSamples());
		assertEquals(0, windSourceStats.trustedSourceSamples());
		assertEquals(1, windSourceStats.untrustedAerodynamics4McSamples());
		assertEquals(1, windSourceStats.localVoxelFlowSamples());
		assertEquals(0, windSourceStats.l0SourceSamples());
		assertEquals(1, windSourceStats.l1SourceSamples());
		assertEquals(0, windSourceStats.l2SourceSamples());
		assertEquals(12.0, windSourceStats.maxFreshnessAgeTicks(), 1.0e-5);
		assertEquals(0.71, windSourceStats.maxConfidence(), 1.0e-5);
		assertEquals(0.0, windSourceStats.maxQualityFactor(), 1.0e-5);
		assertEquals(920.0, windSourceStats.maxAbsPressureAnomalyPascals(), 1.0e-5);
		assertTrue(summary.formatForChat().contains("a4mc 1/1"));
		assertTrue(summary.formatForChat().contains("trusted 0 untrusted 1"));
		assertTrue(summary.formatForChat().contains("src 0/1/0"));
		assertTrue(summary.formatForChat().contains("conf 0.71"));
		assertTrue(summary.formatForChat().contains("q 0.00"));
	}

	@Test
	void blackboxCsvRecordsExplicitFlightModelMode() {
		DroneConfig config = DroneConfig.racingQuad();
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneInput input = new DroneInput(0.52, 0.01, -0.02, 0.03, true, true, FlightMode.ANGLE);
		DroneEnvironment environment = DroneEnvironment.calm();

		for (int i = 0; i < 10; i++) {
			physics.step(input, 0.005, environment);
		}
		recorder.record(DroneBlackboxSample.from(
				0,
				0,
				10,
				0.005,
				"playable",
				0.62,
				3.4,
				5.0,
				-2.6,
				72.0,
				physics.state(),
				input,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				environment,
				config
		));
		recorder.record(DroneBlackboxSample.from(
				1,
				1,
				10,
				0.005,
				"playable",
				0.74,
				-2.1,
				17.0,
				4.2,
				48.0,
				physics.state(),
				input,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				environment,
				config
		));

		String[] lines = recorder.toCsv().strip().split("\\R");
		String[] header = lines[0].split(",", -1);
		String[] row = lines[1].split(",", -1);
		assertEquals("playable", row[indexOf(header, "flight_model")]);
		assertEquals(0.62, Double.parseDouble(row[indexOf(header, "playable_low_altitude_authority")]), 1.0e-5);
		assertEquals(3.4, Double.parseDouble(row[indexOf(header, "playable_visual_pitch_deg")]), 1.0e-5);
		assertEquals(5.0, Double.parseDouble(row[indexOf(header, "playable_visual_yaw_deg")]), 1.0e-5);
		assertEquals(-2.6, Double.parseDouble(row[indexOf(header, "playable_visual_roll_deg")]), 1.0e-5);
		assertEquals(72.0, Double.parseDouble(row[indexOf(header, "playable_visual_yaw_rate_dps")]), 1.0e-5);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertEquals(2, summary.playableFlightModelSamples());
		assertEquals(0, summary.simulationFlightModelSamples());
		assertEquals(0.62, summary.minPlayableLowAltitudeAuthority(), 1.0e-5);
		assertEquals(3.4, summary.playableVisualStats().maxPitchDegrees(), 1.0e-5);
		assertEquals(4.2, summary.playableVisualStats().maxRollDegrees(), 1.0e-5);
		assertEquals(72.0, summary.playableVisualStats().maxYawRateDegreesPerSecond(), 1.0e-5);
		assertEquals(12.0, summary.playableVisualStats().finalYawDriftDegrees(), 1.0e-5);
		assertTrue(summary.formatForChat().contains("flight playable 2 sim 0 lowAlt 38% vis 3.4/4.2deg yaw 72.0dps drift 12.0deg"));
	}

	@Test
	void blackboxSummaryReportsPlayableNeutralStickStability() {
		DroneConfig config = DroneConfig.racingQuad();
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneEnvironment environment = DroneEnvironment.calm();
		DroneInput neutral = new DroneInput(0.42, 0.0, 0.0, 0.0, true, true, FlightMode.ANGLE);
		DroneInput commanded = new DroneInput(0.42, 0.10, 0.0, 0.25, true, true, FlightMode.ANGLE);

		for (int i = 0; i < 10; i++) {
			physics.step(neutral, 0.005, environment);
		}
		recorder.record(DroneBlackboxSample.from(
				0,
				0,
				10,
				0.005,
				"playable",
				1.0,
				0.34,
				0.0,
				0.18,
				0.05,
				physics.state(),
				neutral,
				0.16,
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				environment,
				config
		));
		recorder.record(DroneBlackboxSample.from(
				1,
				1,
				10,
				0.005,
				"playable",
				1.0,
				3.0,
				0.0,
				2.0,
				4.0,
				physics.state(),
				commanded,
				0.16,
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				environment,
				config
		));

		DroneBlackboxSummary.PlayableNeutralStats neutralStats = DroneBlackboxSummary.from(recorder).playableNeutralStats();
		assertEquals(1, neutralStats.sampleCount());
		assertEquals(0.34, neutralStats.maxPitchDegrees(), 1.0e-5);
		assertEquals(0.18, neutralStats.maxRollDegrees(), 1.0e-5);
		assertEquals(0.05, neutralStats.maxYawRateDegreesPerSecond(), 1.0e-5);
	}

	@Test
	void blackboxCsvRecordsRotorIcingTelemetryFromFreezingWetFlight() {
		DroneConfig config = DroneConfig.racingQuad();
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneInput input = new DroneInput(0.86, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);
		DroneEnvironment freezingRain = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				1.0,
				-8.0
		);

		for (int i = 0; i < 2500; i++) {
			physics.step(input, 0.010, freezingRain);
		}
		recorder.record(DroneBlackboxSample.from(
				0,
				0,
				1,
				0.010,
				physics.state(),
				input,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				freezingRain,
				config
		));

		String[] lines = recorder.toCsv().strip().split("\\R");
		String[] header = lines[0].split(",", -1);
		String[] row = lines[1].split(",", -1);
		double severity = Double.parseDouble(row[indexOf(header, "rotor_icing_severity")]);
		double rotor0Severity = Double.parseDouble(row[indexOf(header, "rotor_0_icing_severity")]);
		double thrustScale = Double.parseDouble(row[indexOf(header, "rotor_icing_thrust_scale")]);
		double rotor0ThrustScale = Double.parseDouble(row[indexOf(header, "rotor_0_icing_thrust_scale")]);
		double powerScale = Double.parseDouble(row[indexOf(header, "rotor_icing_power_scale")]);
		double rotor0PowerScale = Double.parseDouble(row[indexOf(header, "rotor_0_icing_power_scale")]);

		assertTrue(severity > 0.10, () -> "severity=" + severity);
		assertTrue(rotor0Severity > 0.10, () -> "rotor0Severity=" + rotor0Severity);
		assertTrue(thrustScale < 0.985, () -> "thrustScale=" + thrustScale);
		assertTrue(rotor0ThrustScale < 0.985, () -> "rotor0ThrustScale=" + rotor0ThrustScale);
		assertTrue(powerScale > 1.05, () -> "powerScale=" + powerScale);
		assertTrue(rotor0PowerScale > 1.05, () -> "rotor0PowerScale=" + rotor0PowerScale);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertTrue(summary.maxRotorIcingSeverity() > 0.10);
		assertTrue(summary.minRotorIcingThrustScale() < 0.985);
		assertTrue(summary.maxRotorIcingPowerScale() > 1.05);
		assertTrue(summary.formatForChat().contains("ice "));
		assertTrue(summary.formatForChat().contains("iceloss "));
		assertTrue(summary.formatForChat().contains("icepwr "));
	}

	@Test
	void blackboxSummaryReportsRotorFlappingTilt() {
		DroneConfig config = DroneConfig.racingQuad()
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorFlappingCoefficient(0.16);
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true, true, FlightMode.ACRO);

		for (int i = 0; i < 500; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(new Vec3(16.0, 0.0, 0.0));
			physics.step(hover, 0.005, DroneEnvironment.calm());
		}
		recorder.record(DroneBlackboxSample.from(
				60,
				60,
				10,
				0.005,
				physics.state(),
				hover,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				DroneEnvironment.calm(),
				config
		));

		String[] lines = recorder.toCsv().strip().split("\\R");
		String[] header = lines[0].split(",", -1);
		String[] row = lines[1].split(",", -1);
		double averageFlappingTilt = Double.parseDouble(row[indexOf(header, "rotor_flapping_tilt_deg")]);
		double rotorFlappingTilt = Double.parseDouble(row[indexOf(header, "rotor_0_flapping_tilt_deg")]);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertTrue(averageFlappingTilt > 3.0);
		assertTrue(summary.maxRotorFlappingTiltDegrees() >= averageFlappingTilt);
		assertTrue(summary.maxRotorFlappingTiltDegrees() >= rotorFlappingTilt);
		assertTrue(summary.formatForChat().contains("flap"));
		assertTrue(summary.formatForChat().contains("deg"));
	}

	@Test
	void blackboxSummaryReportsRotorInPlaneHForce() {
		DroneConfig config = DroneConfig.racingQuad()
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0042)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO);

		for (int i = 0; i < 320; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(new Vec3(24.0, 0.0, 0.0));
			physics.step(hover, 0.005, DroneEnvironment.calm());
		}
		recorder.record(DroneBlackboxSample.from(
				70,
				70,
				10,
				0.005,
				physics.state(),
				hover,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				DroneEnvironment.calm(),
				config
		));

		String[] lines = recorder.toCsv().strip().split("\\R");
		String[] header = lines[0].split(",", -1);
		String[] row = lines[1].split(",", -1);
		double averageHForce = Double.parseDouble(row[indexOf(header, "rotor_in_plane_drag_force_n")]);
		double averageMotorAeroTorque = Double.parseDouble(row[indexOf(header, "avg_motor_aero_torque_nm")]);
		double averageMotorShaftPower = Double.parseDouble(row[indexOf(header, "avg_motor_shaft_power_w")]);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertTrue(averageHForce > 0.10, () -> "hforce=" + averageHForce);
		assertTrue(averageMotorAeroTorque > 0.006, () -> "motorAeroTorque=" + averageMotorAeroTorque);
		assertTrue(averageMotorShaftPower > 8.0, () -> "motorShaftPower=" + averageMotorShaftPower);
		assertTrue(summary.maxRotorInPlaneDragForceNewtons() >= averageHForce);
		assertTrue(summary.formatForChat().contains("hforce"));
	}

	@Test
	void blackboxSummaryReportsBladeDissymmetryHubMoment() {
		DroneConfig config = DroneConfig.racingQuad()
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneInput input = new DroneInput(0.68, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO);
		Vec3 crosswind = new Vec3(34.0, 0.0, 0.0);

		for (int i = 0; i < 220; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(crosswind);
			physics.step(input, 0.005);
		}
		recorder.record(DroneBlackboxSample.from(
				60,
				60,
				10,
				0.005,
				physics.state(),
				input,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[4],
				DroneEnvironment.calm(),
				config
		));

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertTrue(summary.maxRotorBladeDissymmetryTorqueNewtonMeters() > 0.003,
				() -> "summary=" + summary.formatForChat());
		assertTrue(summary.formatForChat().contains("bdiss"));
	}

	@Test
	void blackboxSummaryReportsCoaxialRotorWakeInterference() {
		DroneConfig config = DroneConfig.coaxialX8();
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneEnvironment environment = DroneEnvironment.calm();
		DroneInput input = new DroneInput(config.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO);

		for (int i = 0; i < 700; i++) {
			physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.step(input, 0.005, environment);
		}
		recorder.record(DroneBlackboxSample.from(
				40,
				40,
				10,
				0.005,
				physics.state(),
				input,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[8],
				environment,
				config
		));

		String[] lines = recorder.toCsv().strip().split("\\R");
		String[] header = lines[0].split(",", -1);
		String[] row = lines[1].split(",", -1);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_wake_interference")]) > 0.10);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_wake_thrust_scale")]) < 1.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_mps")]) > 0.05);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_load_bias")]) > 0.015);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_0_coaxial_load_bias")]) > 0.015);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_1_coaxial_load_bias")]) < -0.015);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_load_bias_target")])
				+ 1.0e-5 >= Double.parseDouble(row[indexOf(header, "rotor_coaxial_load_bias")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_load_bias_clipping")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_allocation_load")]) > 0.10);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_allocation_ratio")]) > 1.02);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_allocation_mech_gain_pct")]) > 0.10);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_allocation_elec_gain_pct")]) > 0.10);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_coaxial_allocation_uncertainty_pct")]) > 0.10);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_windmilling")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_pitch_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_yaw_torque_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_roll_torque_nm")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")]) >= 0.0);
		assertEquals(
				Math.PI * Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")]),
				Double.parseDouble(row[indexOf(header, "rotor_7_prop_advance_ratio_j")]),
				1.0e-4
		);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_thrust_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_thrust_scale")]) <= 1.08);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_power_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_power_scale")]) <= 1.08);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_axial_gust_thrust_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_axial_gust_thrust_scale")]) <= 2.5);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_dynamic_inflow_tau_s")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_dynamic_inflow_tau_s")]) <= 0.36);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_reverse_flow_fraction")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reverse_flow_fraction")])
				<= Math.min(1.0, Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")])) + 0.02);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_blade_aoa_deg")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_element_stall")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_dissymmetry")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_pass_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_flapping_tilt_deg")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_interference")]) > 0.15);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_thrust_scale")]) < 0.99);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_swirl_mps")]) > 0.10);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_windmilling")]));

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertTrue(summary.maxRotorWakeInterferenceIntensity() > 0.10);
		assertTrue(summary.maxRotorCoaxialLoadBias() > 0.015);
		assertTrue(summary.maxRotorCoaxialLoadBiasTarget() + 1.0e-6 >= summary.maxRotorCoaxialLoadBias());
		assertTrue(summary.maxRotorCoaxialLoadBiasClipping() >= 0.0);
		assertTrue(summary.maxRotorCoaxialAllocationLoadFraction() > 0.10);
		assertTrue(summary.maxRotorCoaxialAllocationCommandRatio() > 1.02);
		assertTrue(summary.maxRotorCoaxialAllocationMechanicalGainPercent() > 0.10);
		assertTrue(summary.maxRotorCoaxialAllocationElectricalGainPercent() > 0.10);
		assertTrue(summary.maxRotorCoaxialAllocationUncertaintyPercent() > 0.10);
		assertTrue(summary.maxRotorWakeSwirlVelocityMetersPerSecond() > 0.10);
		assertUnitInterval(summary.maxRotorWindmillingIntensity());
		assertTrue(summary.maxRotorWakeSwirlTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorActiveBrakingTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorAccelerationReactionTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorGyroscopicTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxRotorFlappingTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.formatForChat().contains("rwake"));
		assertTrue(summary.formatForChat().contains("cratio"));
		assertTrue(summary.formatForChat().contains("swirl"));
		assertTrue(summary.formatForChat().contains("wmill"));
		assertTrue(summary.formatForChat().contains("swirlT"));
		assertTrue(summary.formatForChat().contains("brakeT"));
		assertTrue(summary.formatForChat().contains("accelT"));
		assertTrue(summary.formatForChat().contains("gyroT"));
		assertTrue(summary.formatForChat().contains("flapT"));
	}

	@Test
	void blackboxSummaryReportsWakeSwirlHubTorque() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig base = withCommonGains(DroneConfig.octoLift(), passiveGains)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withRotorImbalanceIntensity(0.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		RotorSpec template = base.rotors().get(0);
		double arm = 0.34;
		double upperY = template.radiusMeters() * 0.70;
		double lowerY = -upperY;
		DroneConfig stacked = base.withRotors(List.of(
				rotorLike(template, new Vec3(arm, upperY, arm), 1),
				rotorLike(template, new Vec3(arm, lowerY, arm), -1),
				rotorLike(template, new Vec3(-arm, 0.0, arm), -1),
				rotorLike(template, new Vec3(-arm, 0.0, -arm), 1),
				rotorLike(template, new Vec3(arm, 0.0, -arm), -1),
				rotorLike(template, new Vec3(0.0, 0.0, arm * 1.45), 1),
				rotorLike(template, new Vec3(-arm * 1.45, 0.0, 0.0), 1),
				rotorLike(template, new Vec3(0.0, 0.0, -arm * 1.45), -1)
		));
		DronePhysics physics = new DronePhysics(stacked);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneEnvironment environment = DroneEnvironment.calm();
		DroneInput input = new DroneInput(stacked.hoverThrottle() + 0.10, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO);

		for (int i = 0; i < 700; i++) {
			physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.step(input, 0.005, environment);
		}
		recorder.record(DroneBlackboxSample.from(
				40,
				40,
				10,
				0.005,
				physics.state(),
				input,
				physics.state().averageMotorPower(stacked),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				-1,
				0.0,
				0,
				new double[8],
				environment,
				stacked
		));

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertTrue(summary.maxRotorWakeSwirlVelocityMetersPerSecond() > 0.10);
		assertTrue(summary.maxRotorWakeSwirlTorqueNewtonMeters() > 0.0025,
				() -> "summary=" + summary.formatForChat());
		assertTrue(summary.formatForChat().contains("swirlT"));
	}

	@Test
	void blackboxCsvRecordsExtendedOctoRotorTelemetry() {
		DroneConfig config = DroneConfig.octoLift();
		DronePhysics physics = new DronePhysics(config);
		DroneBlackboxRecorder recorder = new DroneBlackboxRecorder(4);
		DroneEnvironment environment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0, 0.82, 1.18, 0.91, 1.12},
				new double[] {0.0, 0.0, 0.0, 0.0, 0.35, 0.62, 0.44, 0.71}
		);
		DroneInput input = new DroneInput(config.hoverThrottle() + 0.04, 0.0, 0.0, 0.25, true, true, FlightMode.ACRO);

		for (int i = 0; i < 240; i++) {
			physics.step(input, 0.005, environment);
		}
		physics.state().damageRotor(7, 0.20);
		physics.step(input, 0.005, environment);
		physics.state().addRotorSurfaceScrapeIntensity(7, 0.66);

		double[] propStrikeSeverity = new double[8];
		propStrikeSeverity[7] = 0.23;
		recorder.record(DroneBlackboxSample.from(
				20,
				20,
				10,
				0.005,
				physics.state(),
				input,
				physics.state().averageMotorPower(config),
				1.0,
				physics.state().averageRotorHealth(),
				0.0,
				7,
				0.23,
				1,
				propStrikeSeverity,
				environment,
				config
		));

		String[] lines = recorder.toCsv().strip().split("\\R");
		String[] header = lines[0].split(",", -1);
		String[] row = lines[1].split(",", -1);

		assertEquals(header.length, row.length);
		assertEquals("8", row[indexOf(header, "airframe_rotor_count")]);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_rpm")]) > 0.0);
		double motor7Erpm100 = Double.parseDouble(row[indexOf(header, "motor_7_erpm100")]);
		double motor7Einterval = Double.parseDouble(row[indexOf(header, "motor_7_einterval_us")]);
		double motor7TelemetryValidity = Double.parseDouble(row[indexOf(header, "motor_7_rpm_telemetry_valid")]);
		assertTrue(motor7Erpm100 > 0.0);
		assertTrue(motor7Einterval > 0.0);
		assertUnitInterval(motor7TelemetryValidity);
		assertTrue(motor7TelemetryValidity < 0.95);
		if (motor7TelemetryValidity >= 0.5) {
			double expectedMotor7Einterval = 600000.0 / motor7Erpm100;
			assertEquals(expectedMotor7Einterval, motor7Einterval, Math.max(1.0, expectedMotor7Einterval * 0.002));
		} else {
			assertEquals(DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS, motor7Einterval, 1.0e-9);
		}
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_target_erpm100")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_target_einterval_us")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_electrical_efficiency")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_voltage_headroom")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_winding_resistance_scale")]) >= 1.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_mechanical_loss_torque_nm")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_commutation_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_regen_current_a")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_phase_current_a")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_current_ripple_a")]) >= 0.0);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_7_torque_ripple_nm")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_thrust_n")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_in_plane_drag_force_n")]) >= 0.0);
		assertEquals(0.80, Double.parseDouble(row[indexOf(header, "rotor_7_health")]), 0.0001);
		assertEquals(0.66, Double.parseDouble(row[indexOf(header, "rotor_7_surface_scrape")]), 0.0001);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")]) >= 0.0);
		assertEquals(
				Math.PI * Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")]),
				Double.parseDouble(row[indexOf(header, "rotor_7_prop_advance_ratio_j")]),
				1.0e-4
		);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_power_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_power_scale")]) <= 1.08);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_axial_gust_thrust_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_axial_gust_thrust_scale")]) <= 2.5);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_dynamic_inflow_tau_s")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_dynamic_inflow_tau_s")]) <= 0.36);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_reverse_flow_fraction")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reverse_flow_fraction")])
				<= Math.min(1.0, Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")])) + 0.02);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_tip_mach")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reynolds_number")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reynolds_index")]) >= 0.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_low_reynolds_loss")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_blade_aoa_deg")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_element_stall")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_dissymmetry")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_pass_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_flapping_tilt_deg")]) >= 0.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_coning")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_interference")]) >= 0.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_wake_thrust_scale")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_wet_thrust_scale")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_swirl_mps")]) >= 0.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_windmilling")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_damage_vibration")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_damage_vibration")]) > 0.0);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_0_damage_vibration")]), 1.0e-9);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_arm_flex")]) >= 0.0);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_7_water_immersion")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_7_precipitation_wetness")]), 0.0001);
		assertEquals(0.23, Double.parseDouble(row[indexOf(header, "prop_strike_7_severity")]), 0.0001);
		assertEquals(1.12, Double.parseDouble(row[indexOf(header, "rotor_7_env_thrust_multiplier")]), 0.0001);
		assertEquals(0.71, Double.parseDouble(row[indexOf(header, "rotor_7_flow_obstruction")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_7_disk_wind_gradient_mps")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_7_a4mc_shelter_obstruction")]), 0.0001);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertEquals(0.80, summary.minRotorHealth(), 0.0001);
		assertTrue(summary.maxRotorDamageVibration() > 0.0);
		assertEquals(0.23, summary.maxPropStrikeSeverity(), 0.0001);
		assertTrue(summary.formatForChat().contains("rotor min 80.0%"));
		assertTrue(summary.formatForChat().contains("dvib"));
	}

	private static int indexOf(String[] header, String column) {
		for (int i = 0; i < header.length; i++) {
			if (header[i].equals(column)) {
				return i;
			}
		}
		throw new AssertionError("Missing column " + column);
	}

	private static double maxOfColumns(String[] lines, String[] header, String... columns) {
		double max = Double.NEGATIVE_INFINITY;
		for (String column : columns) {
			int index = indexOf(header, column);
			for (int i = 1; i < lines.length; i++) {
				String[] row = lines[i].split(",", -1);
				max = Math.max(max, Double.parseDouble(row[index]));
			}
		}
		return max;
	}

	private static double minOfColumns(String[] lines, String[] header, String... columns) {
		double min = Double.POSITIVE_INFINITY;
		for (String column : columns) {
			int index = indexOf(header, column);
			for (int i = 1; i < lines.length; i++) {
				String[] row = lines[i].split(",", -1);
				min = Math.min(min, Double.parseDouble(row[index]));
			}
		}
		return min;
	}

	private static void assertUnitInterval(double value) {
		assertTrue(Double.isFinite(value));
		assertTrue(value >= 0.0);
		assertTrue(value <= 1.0);
	}

	private static DroneConfig withCommonGains(DroneConfig config, PidGains gains) {
		return config
				.withPitchGains(gains)
				.withYawGains(gains)
				.withRollGains(gains);
	}

	private static RotorSpec rotorLike(RotorSpec template, Vec3 positionBodyMeters, int spinDirection) {
		return new RotorSpec(
				positionBodyMeters,
				template.thrustAxisBody(),
				spinDirection,
				template.maxThrustNewtons(),
				template.thrustCoefficient(),
				template.yawTorquePerThrustMeter(),
				template.radiusMeters(),
				template.bladePitchMeters(),
				template.transverseFlowLiftCoefficient(),
				template.axialFlowThrustLossCoefficient(),
				template.diskDragCoefficient(),
				template.rotorInertiaKgMetersSquared(),
				template.inducedInflowTimeConstantSeconds(),
				template.inducedInflowLagCoefficient(),
				template.flappingCoefficient(),
				template.stallThrustLossCoefficient(),
				template.imbalanceIntensity(),
				template.motorWindingResistanceOhms(),
				template.motorPolePairs(),
				template.bladeCount()
		);
	}
}
