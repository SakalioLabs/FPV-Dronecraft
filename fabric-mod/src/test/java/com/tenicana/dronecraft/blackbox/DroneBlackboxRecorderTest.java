package com.tenicana.dronecraft.blackbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
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
				0.36,
				7.5
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
					tick == 2 ? new Vec3(0.0, Math.toRadians(540.0), 0.0) : Vec3.ZERO
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

		assertEquals(header.length, row.length);
		assertEquals(DroneBlackboxSample.CSV_HEADER.split(",", -1).length, header.length);
		assertTrue(csv.contains("physics_substeps"));
		assertTrue(csv.contains("physics_dt_s"));
		assertTrue(csv.contains("physics_rate_hz"));
		assertTrue(csv.contains("control_frame_age_s"));
		assertTrue(csv.contains("control_frame_error"));
		assertTrue(csv.contains("esc_command_frame_age_s"));
		assertTrue(csv.contains("esc_command_frame_interval_s"));
		assertTrue(csv.contains("esc_command_error"));
		assertTrue(csv.contains("pid_dterm_lpf_hz"));
		assertTrue(csv.contains("rotor_stall_intensity"));
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
		assertTrue(csv.contains("rotor_advance_ratio"));
		assertTrue(csv.contains("rotor_0_advance_ratio"));
		assertTrue(csv.contains("rotor_3_advance_ratio"));
		assertTrue(csv.contains("rotor_tip_mach"));
		assertTrue(csv.contains("rotor_0_tip_mach"));
		assertTrue(csv.contains("rotor_7_tip_mach"));
		assertTrue(csv.contains("rotor_blade_aoa_deg"));
		assertTrue(csv.contains("rotor_0_blade_aoa_deg"));
		assertTrue(csv.contains("rotor_7_blade_aoa_deg"));
		assertTrue(csv.contains("rotor_blade_element_stall"));
		assertTrue(csv.contains("rotor_7_blade_element_stall"));
		assertTrue(csv.contains("rotor_blade_dissymmetry"));
		assertTrue(csv.contains("rotor_7_blade_dissymmetry"));
		assertTrue(csv.contains("rotor_blade_pass_ripple"));
		assertTrue(csv.contains("rotor_7_blade_pass_ripple"));
		assertTrue(csv.contains("rotor_flapping_tilt_deg"));
		assertTrue(csv.contains("rotor_7_flapping_tilt_deg"));
		assertTrue(csv.contains("rotor_coning"));
		assertTrue(csv.contains("rotor_0_coning"));
		assertTrue(csv.contains("rotor_7_coning"));
		assertTrue(csv.contains("rotor_aerodynamic_load"));
		assertTrue(csv.contains("rotor_0_aerodynamic_load"));
		assertTrue(csv.contains("rotor_3_aerodynamic_load"));
		assertTrue(csv.contains("rotor_inflow_skew"));
		assertTrue(csv.contains("rotor_wake_interference"));
		assertTrue(csv.contains("rotor_3_wake_interference"));
		assertTrue(csv.contains("rotor_wake_swirl_mps"));
		assertTrue(csv.contains("rotor_3_wake_swirl_mps"));
		assertTrue(csv.contains("rotor_7_wake_swirl_mps"));
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
		assertTrue(csv.contains("rotor_inertia_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_inertia_roll_torque_nm"));
		assertTrue(csv.contains("rotor_angular_drag_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_angular_drag_roll_torque_nm"));
		assertTrue(csv.contains("airframe_angular_drag_pitch_torque_nm"));
		assertTrue(csv.contains("airframe_angular_drag_roll_torque_nm"));
		assertTrue(csv.contains("airframe_separation"));
		assertTrue(csv.contains("airframe_lift_n"));
		assertTrue(csv.contains("ground_effect_drag_n"));
		assertTrue(csv.contains("rotor_wash_drag_n"));
		assertTrue(csv.contains("rotor_wall_effect_n"));
		assertTrue(csv.contains("contact_impact_mps"));
		assertTrue(csv.contains("contact_slip_mps"));
		assertTrue(csv.contains("contact_bounce_mps"));
		assertTrue(csv.contains("contact_angular_impulse_dps"));
		assertTrue(csv.contains("barometer_altitude_m"));
		assertTrue(csv.contains("barometer_vertical_speed_mps"));
		assertTrue(csv.contains("barometer_pressure_hpa"));
		assertTrue(csv.contains("barometer_error_m"));
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
		assertTrue(csv.contains("water_immersion"));
		assertTrue(csv.contains("precipitation_wetness"));
		assertTrue(csv.contains("effective_air_density_ratio"));
		assertTrue(csv.contains("ambient_temperature_c"));
		assertTrue(csv.contains("rotor_0_water_immersion"));
		assertTrue(csv.contains("rotor_3_water_immersion"));
		assertTrue(csv.contains("propwash_wake_intensity"));
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
		assertTrue(csv.contains("avg_motor_target_rpm"));
		assertTrue(csv.contains("motor_0_target_rpm"));
		assertTrue(csv.contains("motor_3_target_rpm"));
		assertTrue(csv.contains("avg_motor_tracking_error"));
		assertTrue(csv.contains("motor_0_tracking_error"));
		assertTrue(csv.contains("motor_3_tracking_error"));
		assertTrue(csv.contains("avg_motor_actuator_authority"));
		assertTrue(csv.contains("motor_0_actuator_authority"));
		assertTrue(csv.contains("motor_3_actuator_authority"));
		assertTrue(csv.contains("battery_current_limit"));
		assertTrue(csv.contains("battery_regen_current_a"));
		assertTrue(csv.contains("battery_voltage_spike_v"));
		assertTrue(csv.contains("battery_bus_ripple_v"));
		assertTrue(csv.contains("battery_temp_c"));
		assertTrue(csv.contains("battery_cooling_factor"));
		assertTrue(csv.contains("battery_thermal_limit"));
		assertTrue(csv.contains("effective_wind_x_mps"));
		assertTrue(csv.contains("effective_wind_y_mps"));
		assertTrue(csv.contains("effective_wind_z_mps"));
		assertTrue(csv.contains("wind_gust_speed_mps"));
		assertTrue(csv.contains("wind_shear_accel_mps2"));
		assertTrue(csv.contains("gyro_bias_pitch_dps"));
		assertTrue(csv.contains("gyro_clip"));
		assertTrue(csv.contains("gyro_blade_pass_notch_hz"));
		assertTrue(csv.contains("gyro_blade_pass_notch_attenuation"));
		assertTrue(csv.contains("accel_bias_x_mps2"));
		assertTrue(csv.contains("accel_clip"));
		assertTrue(csv.contains("airframe_pressure_center_pitch_torque_nm"));
		assertTrue(csv.contains("rotor_surface_scrape"));
		assertTrue(csv.contains("rotor_1_surface_scrape"));
		assertTrue(csv.contains("tune_rotor_blade_pitch_m"));
		assertTrue(csv.contains("tune_rotor_stall_loss"));
		assertTrue(csv.contains("tune_rotor_imbalance"));
		assertTrue(csv.contains("tune_rc_frame_rate_hz"));
		assertTrue(csv.contains("tune_rc_resolution_steps"));
		assertTrue(csv.contains("tune_esc_command_frame_rate_hz"));
		assertTrue(csv.contains("tune_esc_command_resolution_steps"));
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
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "control_frame_age_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "control_frame_interval_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "control_frame_error")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_command_frame_age_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_command_frame_interval_s")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_command_error")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rotor_blade_pitch_m")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rotor_stall_loss")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rotor_imbalance")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rc_frame_rate_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_rc_resolution_steps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_esc_command_frame_rate_hz")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "tune_esc_command_resolution_steps")]));
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
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "gyro_blade_pass_notch_attenuation")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "accel_bias_x_mps2")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "accel_clip")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_advance_ratio")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_0_advance_ratio")]));
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
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_tip_mach")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_tip_mach")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_angular_drag_roll_torque_nm")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "airframe_separation")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "airframe_lift_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "ground_effect_drag_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wash_drag_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_wall_effect_n")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_impact_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_slip_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_bounce_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "contact_angular_impulse_dps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_surface_scrape")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_1_surface_scrape")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_altitude_m")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_pressure_hpa")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "barometer_error_m")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "max_esc_temp_c")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "esc_thermal_limit")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_esc_cooling_factor")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_regen_current_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_voltage_spike_v")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_bus_ripple_v")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_temp_c")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_cooling_factor")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "battery_thermal_limit")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "effective_wind_x_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_gust_speed_mps")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "wind_shear_accel_mps2")]));
		assertEquals(0.18, Double.parseDouble(row[indexOf(header, "water_immersion")]), 0.0001);
		assertEquals(0.36, Double.parseDouble(row[indexOf(header, "precipitation_wetness")]), 0.0001);
		assertEquals(7.5, Double.parseDouble(row[indexOf(header, "ambient_temperature_c")]), 0.0001);
		double airDensity = Double.parseDouble(row[indexOf(header, "air_density_ratio")]);
		double effectiveAirDensity = Double.parseDouble(row[indexOf(header, "effective_air_density_ratio")]);
		assertTrue(effectiveAirDensity < airDensity);
		assertTrue(effectiveAirDensity > airDensity * 0.997);
		assertEquals(0.45, Double.parseDouble(row[indexOf(header, "rotor_0_water_immersion")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_3_water_immersion")]), 0.0001);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_electrical_efficiency")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_electrical_efficiency")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_voltage_headroom")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_voltage_headroom")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "avg_motor_target_rpm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_target_rpm")]));
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
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_coning")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_0_coning")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "pid_integral_relax_pitch")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "pid_integral_relax_yaw")]));
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "pid_integral_relax_roll")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_commutation_ripple")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_commutation_ripple")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_phase_current_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_phase_current_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_current_ripple_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_current_ripple_a")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_torque_ripple_nm")]));
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_0_torque_ripple_nm")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "avg_motor_mechanical_loss_torque_nm")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_0_mechanical_loss_torque_nm")]) >= 0.0);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertEquals(4, summary.sampleCount());
		assertEquals(10, summary.maxPhysicsSubsteps());
		assertEquals(200.0, summary.maxPhysicsRateHertz(), 0.001);
		assertTrue(summary.maxSpeedMetersPerSecond() >= 0.0);
		assertUnitInterval(summary.maxAirframeSeparatedFlowIntensity());
		assertTrue(summary.maxAirframeLiftForceNewtons() >= 0.0);
		assertTrue(summary.maxGroundEffectDragForceNewtons() >= 0.0);
		assertTrue(summary.maxRotorWashDragForceNewtons() >= 0.0);
		assertTrue(summary.maxRotorWallEffectForceNewtons() >= 0.0);
		assertEquals(4.5, summary.maxContactImpactSpeedMetersPerSecond(), 0.0001);
		assertEquals(5.0, summary.maxContactSlipSpeedMetersPerSecond(), 0.0001);
		assertEquals(0.6, summary.maxContactBounceSpeedMetersPerSecond(), 0.0001);
		assertEquals(540.0, summary.maxContactAngularImpulseDegreesPerSecond(), 0.0001);
		assertEquals(0.18, summary.maxRotorSurfaceScrapeIntensity(), 0.0001);
		assertTrue(summary.maxRotorConingIntensity() >= 0.0);
		assertTrue(summary.maxRotorAdvanceRatio() >= 0.0);
		assertTrue(summary.maxRotorTipMach() >= 0.0);
		assertTrue(summary.maxRotorWakeInterferenceIntensity() >= 0.0);
		assertTrue(summary.maxRotorArmFlexIntensity() >= 0.0);
		assertTrue(summary.maxRotorAngularDragTorqueNewtonMeters() >= 0.0);
		assertTrue(summary.maxControlFrameAgeSeconds() >= 0.0);
		assertTrue(summary.maxControlFrameError() >= 0.0);
		assertTrue(summary.maxBarometerErrorMeters() >= 0.0);
		assertTrue(summary.maxBarometerPropwashErrorMeters() >= 0.0);
		assertTrue(summary.minBarometerPressureHectopascals() > 0.0);
		assertTrue(summary.maxEscTemperatureCelsius() >= 25.0);
		assertTrue(summary.minMotorElectricalEfficiency() > 0.0);
		assertTrue(summary.minMotorVoltageHeadroom() >= 0.0);
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
		assertTrue(summary.maxBatteryVoltageSpike() >= 0.0);
		assertTrue(summary.maxWindGustSpeedMetersPerSecond() >= 0.0);
		assertTrue(summary.maxWindShearAccelerationMetersPerSecondSquared() >= 0.0);
		assertEquals(0.45, summary.maxWaterImmersionIntensity(), 0.0001);
		assertEquals(0.36, summary.maxPrecipitationWetnessIntensity(), 0.0001);
		assertEquals(7.5, summary.minAmbientTemperatureCelsius(), 0.0001);
		assertEquals(7.5, summary.maxAmbientTemperatureCelsius(), 0.0001);
		assertTrue(summary.minBatteryVoltage() > 0.0);
		assertTrue(summary.formatForChat().contains("Blackbox"));
		assertTrue(summary.formatForChat().contains("loop 10@200Hz"));
		assertTrue(summary.formatForChat().contains("current-limit"));
		assertTrue(summary.formatForChat().contains("ripple"));
		assertTrue(summary.formatForChat().contains("batt-limit"));
		assertTrue(summary.formatForChat().contains("track"));
		assertTrue(summary.formatForChat().contains("auth"));
		assertTrue(summary.formatForChat().contains("mix-auth"));
		assertTrue(summary.formatForChat().contains("mix-edge"));
		assertTrue(summary.formatForChat().contains("mix-head"));
		assertTrue(summary.formatForChat().contains("regen"));
		assertTrue(summary.formatForChat().contains("spike"));
		assertTrue(summary.formatForChat().contains("contact"));
		assertTrue(summary.formatForChat().contains("propwash"));
		assertTrue(summary.formatForChat().contains("ETL"));
		assertTrue(summary.formatForChat().contains("adv"));
		assertTrue(summary.formatForChat().contains("tipmach"));
		assertTrue(summary.formatForChat().contains("load"));
		assertTrue(summary.formatForChat().contains("skew"));
		assertTrue(summary.formatForChat().contains("rwake"));
		assertTrue(summary.formatForChat().contains("rdamp"));
		assertTrue(summary.formatForChat().contains("ang-drag"));
		assertTrue(summary.formatForChat().contains("sep"));
		assertTrue(summary.formatForChat().contains("lift"));
		assertTrue(summary.formatForChat().contains("cushion"));
		assertTrue(summary.formatForChat().contains("wash"));
		assertTrue(summary.formatForChat().contains("wall"));
		assertTrue(summary.formatForChat().contains("coning"));
		assertTrue(summary.formatForChat().contains("flex"));
		assertTrue(summary.formatForChat().contains("scrape"));
		assertTrue(summary.formatForChat().contains("baro"));
		assertTrue(summary.formatForChat().contains("esc"));
		assertTrue(summary.formatForChat().contains("wake"));
		assertTrue(summary.formatForChat().contains("water"));
		assertTrue(summary.formatForChat().contains("rain"));
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
		assertTrue(summary.formatForChat().contains("rotor min"));
		assertTrue(summary.formatForChat().contains("prop-strike"));
		assertTrue(summary.formatForChat().contains("rc-frame"));
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
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_wake_swirl_mps")]) > 0.05);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")]) >= 0.0);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_blade_aoa_deg")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_element_stall")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_dissymmetry")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_pass_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_flapping_tilt_deg")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_interference")]) > 0.15);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_swirl_mps")]) > 0.10);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertTrue(summary.maxRotorWakeInterferenceIntensity() > 0.10);
		assertTrue(summary.maxRotorWakeSwirlVelocityMetersPerSecond() > 0.10);
		assertTrue(summary.formatForChat().contains("rwake"));
		assertTrue(summary.formatForChat().contains("swirl"));
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
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_electrical_efficiency")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_voltage_headroom")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_mechanical_loss_torque_nm")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_commutation_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_phase_current_a")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_current_ripple_a")]) >= 0.0);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "motor_7_torque_ripple_nm")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_thrust_n")]) > 0.0);
		assertEquals(0.80, Double.parseDouble(row[indexOf(header, "rotor_7_health")]), 0.0001);
		assertEquals(0.66, Double.parseDouble(row[indexOf(header, "rotor_7_surface_scrape")]), 0.0001);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_tip_mach")]) >= 0.0);
		assertDoesNotThrow(() -> Double.parseDouble(row[indexOf(header, "rotor_7_blade_aoa_deg")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_element_stall")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_dissymmetry")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_pass_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_flapping_tilt_deg")]) >= 0.0);
		assertUnitInterval(Double.parseDouble(row[indexOf(header, "rotor_7_coning")]));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_interference")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_swirl_mps")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_arm_flex")]) >= 0.0);
		assertEquals(0.0, Double.parseDouble(row[indexOf(header, "rotor_7_water_immersion")]), 0.0001);
		assertEquals(0.23, Double.parseDouble(row[indexOf(header, "prop_strike_7_severity")]), 0.0001);
		assertEquals(1.12, Double.parseDouble(row[indexOf(header, "rotor_7_env_thrust_multiplier")]), 0.0001);
		assertEquals(0.71, Double.parseDouble(row[indexOf(header, "rotor_7_flow_obstruction")]), 0.0001);

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(recorder);
		assertEquals(0.80, summary.minRotorHealth(), 0.0001);
		assertEquals(0.23, summary.maxPropStrikeSeverity(), 0.0001);
		assertTrue(summary.formatForChat().contains("rotor min 80.0%"));
	}

	private static int indexOf(String[] header, String column) {
		for (int i = 0; i < header.length; i++) {
			if (header[i].equals(column)) {
				return i;
			}
		}
		throw new AssertionError("Missing column " + column);
	}

	private static void assertUnitInterval(double value) {
		assertTrue(Double.isFinite(value));
		assertTrue(value >= 0.0);
		assertTrue(value <= 1.0);
	}
}
