package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class OfflineFlightRecorder {
	public static final double SIMULATION_DT_SECONDS = 0.005;
	public static final double DEFAULT_DURATION_SECONDS = 16.0;
	public static final int SAMPLE_EVERY_STEPS = 4;

	private static final String CSV_HEADER = String.join(",",
			"sample",
			"step",
			"time_s",
			"phase",
			"throttle",
			"pitch_input",
			"roll_input",
			"yaw_input",
			"armed",
			"flight_mode",
			"raw_link_active",
			"control_throttle",
			"control_pitch",
			"control_roll",
			"control_yaw",
			"control_armed",
			"control_flight_mode",
			"control_link_active",
			"control_link_loss_s",
			"control_failsafe",
			"control_frame_age_s",
			"control_frame_interval_s",
			"control_frame_error",
			"pos_x_m",
			"pos_y_m",
			"pos_z_m",
			"vel_x_mps",
			"vel_y_mps",
			"vel_z_mps",
			"speed_mps",
			"att_pitch_deg",
			"att_yaw_deg",
			"att_roll_deg",
			"est_pitch_deg",
			"est_yaw_deg",
			"est_roll_deg",
			"attitude_error_pitch_deg",
			"attitude_error_yaw_deg",
			"attitude_error_roll_deg",
			"attitude_error_deg",
			"attitude_accel_trust",
			"rate_pitch_dps",
			"rate_yaw_dps",
			"rate_roll_dps",
			"gyro_pitch_dps",
			"gyro_yaw_dps",
			"gyro_roll_dps",
			"gyro_bias_pitch_dps",
			"gyro_bias_yaw_dps",
			"gyro_bias_roll_dps",
			"gyro_clip",
			"gyro_notch_hz",
			"gyro_notch_attenuation",
			"gyro_blade_pass_notch_hz",
			"gyro_blade_pass_notch_attenuation",
			"linear_accel_x_mps2",
			"linear_accel_y_mps2",
			"linear_accel_z_mps2",
			"accel_x_mps2",
			"accel_y_mps2",
			"accel_z_mps2",
			"accel_bias_x_mps2",
			"accel_bias_y_mps2",
			"accel_bias_z_mps2",
			"accel_clip",
			"avg_motor_output",
			"motor_0_output",
			"motor_1_output",
			"motor_2_output",
			"motor_3_output",
			"esc_command_frame_age_s",
			"esc_command_frame_interval_s",
			"esc_command_error",
			"avg_motor_rpm",
			"motor_0_rpm",
			"motor_1_rpm",
			"motor_2_rpm",
			"motor_3_rpm",
			"avg_motor_target_rpm",
			"motor_0_target_rpm",
			"motor_1_target_rpm",
			"motor_2_target_rpm",
			"motor_3_target_rpm",
			"avg_motor_tracking_error",
			"motor_0_tracking_error",
			"motor_1_tracking_error",
			"motor_2_tracking_error",
			"motor_3_tracking_error",
			"avg_motor_actuator_authority",
			"motor_0_actuator_authority",
			"motor_1_actuator_authority",
			"motor_2_actuator_authority",
			"motor_3_actuator_authority",
			"avg_motor_accel_rad_s2",
			"motor_0_accel_rad_s2",
			"motor_1_accel_rad_s2",
			"motor_2_accel_rad_s2",
			"motor_3_accel_rad_s2",
			"avg_motor_aero_torque_nm",
			"motor_0_aero_torque_nm",
			"motor_1_aero_torque_nm",
			"motor_2_aero_torque_nm",
			"motor_3_aero_torque_nm",
			"avg_motor_shaft_power_w",
			"motor_0_shaft_power_w",
			"motor_1_shaft_power_w",
			"motor_2_shaft_power_w",
			"motor_3_shaft_power_w",
			"avg_motor_current_a",
			"motor_0_current_a",
			"motor_1_current_a",
			"motor_2_current_a",
			"motor_3_current_a",
			"battery_current_a",
			"battery_voltage_v",
			"battery_ocv_v",
			"battery_ohmic_sag_v",
			"battery_transient_sag_v",
			"battery_regen_current_a",
			"battery_voltage_spike_v",
			"battery_soc",
			"battery_current_limit",
			"battery_power_limit",
			"avg_motor_temp_c",
			"thermal_limit",
			"avg_motor_cooling_factor",
			"motor_0_cooling_factor",
			"motor_1_cooling_factor",
			"motor_2_cooling_factor",
			"motor_3_cooling_factor",
			"rotor_0_thrust_n",
			"rotor_1_thrust_n",
			"rotor_2_thrust_n",
			"rotor_3_thrust_n",
			"avg_induced_velocity_mps",
			"rotor_translational_lift",
			"rotor_aerodynamic_load",
			"rotor_inflow_skew",
			"rotor_wake_interference",
			"rotor_0_wake_interference",
			"rotor_1_wake_interference",
			"rotor_2_wake_interference",
			"rotor_3_wake_interference",
			"rotor_blade_dissymmetry_pitch_torque_nm",
			"rotor_blade_dissymmetry_yaw_torque_nm",
			"rotor_blade_dissymmetry_roll_torque_nm",
			"rotor_skew_pitch_torque_nm",
			"rotor_skew_yaw_torque_nm",
			"rotor_skew_roll_torque_nm",
			"rotor_wake_swirl_pitch_torque_nm",
			"rotor_wake_swirl_yaw_torque_nm",
			"rotor_wake_swirl_roll_torque_nm",
			"rotor_inertia_pitch_torque_nm",
			"rotor_inertia_yaw_torque_nm",
			"rotor_inertia_roll_torque_nm",
			"rotor_acceleration_reaction_pitch_torque_nm",
			"rotor_acceleration_reaction_yaw_torque_nm",
			"rotor_acceleration_reaction_roll_torque_nm",
			"rotor_gyroscopic_pitch_torque_nm",
			"rotor_gyroscopic_yaw_torque_nm",
			"rotor_gyroscopic_roll_torque_nm",
			"rotor_active_braking_pitch_torque_nm",
			"rotor_active_braking_yaw_torque_nm",
			"rotor_active_braking_roll_torque_nm",
			"rotor_flapping_pitch_torque_nm",
			"rotor_flapping_yaw_torque_nm",
			"rotor_flapping_roll_torque_nm",
			"rotor_angular_drag_pitch_torque_nm",
			"rotor_angular_drag_yaw_torque_nm",
			"rotor_angular_drag_roll_torque_nm",
			"avg_flapping_force_n",
			"rotor_stall_intensity",
			"rotor_vibration",
			"mixer_saturation",
			"pid_attenuation",
			"pid_integral_relax",
			"pid_dterm_lpf_hz",
			"anti_gravity_boost",
			"level_target_pitch_deg",
			"level_target_roll_deg",
			"level_error_pitch_deg",
			"level_error_roll_deg",
			"self_level_blend",
			"target_pitch_rate_dps",
			"target_yaw_rate_dps",
			"target_roll_rate_dps",
			"rate_error_pitch_dps",
			"rate_error_yaw_dps",
			"rate_error_roll_dps",
			"pid_pitch_p_nm",
			"pid_yaw_p_nm",
			"pid_roll_p_nm",
			"pid_pitch_i_nm",
			"pid_yaw_i_nm",
			"pid_roll_i_nm",
			"pid_pitch_d_nm",
			"pid_yaw_d_nm",
			"pid_roll_d_nm",
			"pid_pitch_ff_nm",
			"pid_yaw_ff_nm",
			"pid_roll_ff_nm",
			"pid_pitch_output_nm",
			"pid_yaw_output_nm",
			"pid_roll_output_nm",
			"propwash_intensity",
			"propwash_wake_intensity",
			"vortex_ring_state",
			"propwash_pitch_torque_nm",
			"propwash_yaw_torque_nm",
			"propwash_roll_torque_nm",
			"airframe_pitch_torque_nm",
			"airframe_yaw_torque_nm",
			"airframe_roll_torque_nm",
			"airframe_pressure_center_pitch_torque_nm",
			"airframe_pressure_center_yaw_torque_nm",
			"airframe_pressure_center_roll_torque_nm",
			"airframe_angular_drag_pitch_torque_nm",
			"airframe_angular_drag_yaw_torque_nm",
			"airframe_angular_drag_roll_torque_nm",
			"rel_air_body_x_mps",
			"rel_air_body_y_mps",
			"rel_air_body_z_mps",
			"airspeed_mps",
			"angle_of_attack_deg",
			"sideslip_deg",
			"airframe_separation",
			"turbulence_intensity",
			"obstacle_proximity",
			"wind_turbulence_pitch_torque_nm",
			"wind_turbulence_yaw_torque_nm",
			"wind_turbulence_roll_torque_nm",
			"wind_x_mps",
			"wind_y_mps",
			"wind_z_mps",
			"air_density_ratio",
			"effective_air_density_ratio",
			"ambient_temperature_c",
			"ground_clearance_m",
			"ground_effect_multiplier",
			"tune_pitch_super_rate",
			"tune_yaw_super_rate",
			"tune_roll_super_rate",
			"tune_level_angle_deg",
			"tune_level_gain",
			"tune_horizon_start",
			"tune_horizon_end",
			"tune_iterm_relax",
			"tune_attitude_accel_gain",
			"tune_attitude_accel_trust_mps2",
			"tune_rc_smoothing_s",
			"tune_rc_latency_s",
			"tune_rc_failsafe_s",
			"tune_rc_frame_rate_hz",
			"tune_rc_resolution_steps",
			"tune_esc_down_slew_rate",
			"tune_esc_deadband",
			"tune_motor_brake",
			"tune_accel_lpf_hz",
			"tune_accel_noise_mps2",
			"tune_esc_command_frame_rate_hz",
			"tune_esc_command_resolution_steps",
			"tune_rotor_blade_pitch_m",
			"tune_rotor_imbalance",
			"airframe_lift_x_n",
			"airframe_lift_y_n",
			"airframe_lift_z_n",
			"airframe_lift_n",
			"ground_effect_drag_x_n",
			"ground_effect_drag_y_n",
			"ground_effect_drag_z_n",
			"ground_effect_drag_n",
			"rotor_wash_drag_x_n",
			"rotor_wash_drag_y_n",
			"rotor_wash_drag_z_n",
			"rotor_wash_drag_n",
			"barometer_altitude_m",
			"barometer_vertical_speed_mps",
			"barometer_pressure_hpa",
			"barometer_error_m",
			"barometer_propwash_error_m",
			"avg_esc_temp_c",
			"max_esc_temp_c",
			"esc_thermal_limit",
			"avg_esc_cooling_factor",
			"esc_0_temp_c",
			"esc_1_temp_c",
			"esc_2_temp_c",
			"esc_3_temp_c",
			"esc_0_thermal_limit",
			"esc_1_thermal_limit",
			"esc_2_thermal_limit",
			"esc_3_thermal_limit",
			"esc_0_cooling_factor",
			"esc_1_cooling_factor",
			"esc_2_cooling_factor",
			"esc_3_cooling_factor",
			"effective_wind_x_mps",
			"effective_wind_y_mps",
			"effective_wind_z_mps",
			"wind_gust_speed_mps",
			"wind_shear_accel_mps2",
			"rotor_wall_effect_x_n",
			"rotor_wall_effect_y_n",
			"rotor_wall_effect_z_n",
			"rotor_wall_effect_n",
			"contact_impact_mps",
			"contact_slip_mps",
			"contact_bounce_mps",
			"contact_angular_impulse_pitch_dps",
			"contact_angular_impulse_yaw_dps",
			"contact_angular_impulse_roll_dps",
			"contact_angular_impulse_dps",
			"rotor_surface_scrape",
			"rotor_0_surface_scrape",
			"rotor_1_surface_scrape",
			"rotor_2_surface_scrape",
			"rotor_3_surface_scrape",
			"airframe_rotor_count",
			"motor_4_output",
			"motor_5_output",
			"motor_6_output",
			"motor_7_output",
			"motor_4_rpm",
			"motor_5_rpm",
			"motor_6_rpm",
			"motor_7_rpm",
			"motor_4_target_rpm",
			"motor_5_target_rpm",
			"motor_6_target_rpm",
			"motor_7_target_rpm",
			"motor_4_tracking_error",
			"motor_5_tracking_error",
			"motor_6_tracking_error",
			"motor_7_tracking_error",
			"motor_4_actuator_authority",
			"motor_5_actuator_authority",
			"motor_6_actuator_authority",
			"motor_7_actuator_authority",
			"motor_4_current_a",
			"motor_5_current_a",
			"motor_6_current_a",
			"motor_7_current_a",
			"motor_phase_current_a",
			"motor_0_phase_current_a",
			"motor_1_phase_current_a",
			"motor_2_phase_current_a",
			"motor_3_phase_current_a",
			"motor_4_phase_current_a",
			"motor_5_phase_current_a",
			"motor_6_phase_current_a",
			"motor_7_phase_current_a",
			"motor_current_ripple_a",
			"motor_0_current_ripple_a",
			"motor_1_current_ripple_a",
			"motor_2_current_ripple_a",
			"motor_3_current_ripple_a",
			"motor_4_current_ripple_a",
			"motor_5_current_ripple_a",
			"motor_6_current_ripple_a",
			"motor_7_current_ripple_a",
			"motor_commutation_ripple",
			"motor_0_commutation_ripple",
			"motor_1_commutation_ripple",
			"motor_2_commutation_ripple",
			"motor_3_commutation_ripple",
			"motor_4_commutation_ripple",
			"motor_5_commutation_ripple",
			"motor_6_commutation_ripple",
			"motor_7_commutation_ripple",
			"motor_torque_ripple_nm",
			"motor_0_torque_ripple_nm",
			"motor_1_torque_ripple_nm",
			"motor_2_torque_ripple_nm",
			"motor_3_torque_ripple_nm",
			"motor_4_torque_ripple_nm",
			"motor_5_torque_ripple_nm",
			"motor_6_torque_ripple_nm",
			"motor_7_torque_ripple_nm",
			"battery_bus_ripple_v",
			"imu_supply_noise",
			"battery_temp_c",
			"battery_cooling_factor",
			"battery_thermal_limit",
			"rotor_4_thrust_n",
			"rotor_5_thrust_n",
			"rotor_6_thrust_n",
			"rotor_7_thrust_n",
			"rotor_4_health",
			"rotor_5_health",
			"rotor_6_health",
			"rotor_7_health",
			"rotor_4_surface_scrape",
			"rotor_5_surface_scrape",
			"rotor_6_surface_scrape",
			"rotor_7_surface_scrape",
			"rotor_4_wake_interference",
			"rotor_5_wake_interference",
			"rotor_6_wake_interference",
			"rotor_7_wake_interference",
			"rotor_wake_swirl_mps",
			"rotor_0_wake_swirl_mps",
			"rotor_1_wake_swirl_mps",
			"rotor_2_wake_swirl_mps",
			"rotor_3_wake_swirl_mps",
			"rotor_4_wake_swirl_mps",
			"rotor_5_wake_swirl_mps",
			"rotor_6_wake_swirl_mps",
			"rotor_7_wake_swirl_mps",
			"rotor_4_env_thrust_multiplier",
			"rotor_5_env_thrust_multiplier",
			"rotor_6_env_thrust_multiplier",
			"rotor_7_env_thrust_multiplier",
			"rotor_4_flow_obstruction",
			"rotor_5_flow_obstruction",
			"rotor_6_flow_obstruction",
			"rotor_7_flow_obstruction",
			"rotor_advance_ratio",
			"rotor_0_advance_ratio",
			"rotor_1_advance_ratio",
			"rotor_2_advance_ratio",
			"rotor_3_advance_ratio",
			"rotor_4_advance_ratio",
			"rotor_5_advance_ratio",
			"rotor_6_advance_ratio",
			"rotor_7_advance_ratio",
			"rotor_tip_mach",
			"rotor_0_tip_mach",
			"rotor_1_tip_mach",
			"rotor_2_tip_mach",
			"rotor_3_tip_mach",
			"rotor_4_tip_mach",
			"rotor_5_tip_mach",
			"rotor_6_tip_mach",
			"rotor_7_tip_mach",
			"rotor_low_reynolds_loss",
			"rotor_0_low_reynolds_loss",
			"rotor_1_low_reynolds_loss",
			"rotor_2_low_reynolds_loss",
			"rotor_3_low_reynolds_loss",
			"rotor_4_low_reynolds_loss",
			"rotor_5_low_reynolds_loss",
			"rotor_6_low_reynolds_loss",
			"rotor_7_low_reynolds_loss",
			"rotor_blade_aoa_deg",
			"rotor_0_blade_aoa_deg",
			"rotor_1_blade_aoa_deg",
			"rotor_2_blade_aoa_deg",
			"rotor_3_blade_aoa_deg",
			"rotor_4_blade_aoa_deg",
			"rotor_5_blade_aoa_deg",
			"rotor_6_blade_aoa_deg",
			"rotor_7_blade_aoa_deg",
			"rotor_blade_element_stall",
			"rotor_0_blade_element_stall",
			"rotor_1_blade_element_stall",
			"rotor_2_blade_element_stall",
			"rotor_3_blade_element_stall",
			"rotor_4_blade_element_stall",
			"rotor_5_blade_element_stall",
			"rotor_6_blade_element_stall",
			"rotor_7_blade_element_stall",
			"rotor_blade_dissymmetry",
			"rotor_0_blade_dissymmetry",
			"rotor_1_blade_dissymmetry",
			"rotor_2_blade_dissymmetry",
			"rotor_3_blade_dissymmetry",
			"rotor_4_blade_dissymmetry",
			"rotor_5_blade_dissymmetry",
			"rotor_6_blade_dissymmetry",
			"rotor_7_blade_dissymmetry",
			"rotor_blade_pass_ripple",
			"rotor_0_blade_pass_ripple",
			"rotor_1_blade_pass_ripple",
			"rotor_2_blade_pass_ripple",
			"rotor_3_blade_pass_ripple",
			"rotor_4_blade_pass_ripple",
			"rotor_5_blade_pass_ripple",
			"rotor_6_blade_pass_ripple",
			"rotor_7_blade_pass_ripple",
			"rotor_flapping_tilt_deg",
			"rotor_0_flapping_tilt_deg",
			"rotor_1_flapping_tilt_deg",
			"rotor_2_flapping_tilt_deg",
			"rotor_3_flapping_tilt_deg",
			"rotor_4_flapping_tilt_deg",
			"rotor_5_flapping_tilt_deg",
			"rotor_6_flapping_tilt_deg",
			"rotor_7_flapping_tilt_deg",
			"rotor_coning",
			"rotor_0_coning",
			"rotor_1_coning",
			"rotor_2_coning",
			"rotor_3_coning",
			"rotor_4_coning",
			"rotor_5_coning",
			"rotor_6_coning",
			"rotor_7_coning",
			"motor_electrical_efficiency",
			"motor_0_electrical_efficiency",
			"motor_1_electrical_efficiency",
			"motor_2_electrical_efficiency",
			"motor_3_electrical_efficiency",
			"motor_4_electrical_efficiency",
			"motor_5_electrical_efficiency",
			"motor_6_electrical_efficiency",
			"motor_7_electrical_efficiency",
			"motor_voltage_headroom",
			"motor_0_voltage_headroom",
			"motor_1_voltage_headroom",
			"motor_2_voltage_headroom",
			"motor_3_voltage_headroom",
			"motor_4_voltage_headroom",
			"motor_5_voltage_headroom",
			"motor_6_voltage_headroom",
			"motor_7_voltage_headroom",
			"water_immersion",
			"precipitation_wetness",
			"rotor_0_water_immersion",
			"rotor_1_water_immersion",
			"rotor_2_water_immersion",
			"rotor_3_water_immersion",
			"rotor_4_water_immersion",
			"rotor_5_water_immersion",
			"rotor_6_water_immersion",
			"rotor_7_water_immersion",
			"avg_motor_mechanical_loss_torque_nm",
			"motor_0_mechanical_loss_torque_nm",
			"motor_1_mechanical_loss_torque_nm",
			"motor_2_mechanical_loss_torque_nm",
			"motor_3_mechanical_loss_torque_nm",
			"motor_4_mechanical_loss_torque_nm",
			"motor_5_mechanical_loss_torque_nm",
			"motor_6_mechanical_loss_torque_nm",
			"motor_7_mechanical_loss_torque_nm",
			"rotor_0_force_x_n",
			"rotor_0_force_y_n",
			"rotor_0_force_z_n",
			"rotor_1_force_x_n",
			"rotor_1_force_y_n",
			"rotor_1_force_z_n",
			"rotor_2_force_x_n",
			"rotor_2_force_y_n",
			"rotor_2_force_z_n",
			"rotor_3_force_x_n",
			"rotor_3_force_y_n",
			"rotor_3_force_z_n",
			"rotor_4_force_x_n",
			"rotor_4_force_y_n",
			"rotor_4_force_z_n",
			"rotor_5_force_x_n",
			"rotor_5_force_y_n",
			"rotor_5_force_z_n",
			"rotor_6_force_x_n",
			"rotor_6_force_y_n",
			"rotor_6_force_z_n",
			"rotor_7_force_x_n",
			"rotor_7_force_y_n",
			"rotor_7_force_z_n",
			"rotor_0_torque_x_nm",
			"rotor_0_torque_y_nm",
			"rotor_0_torque_z_nm",
			"rotor_1_torque_x_nm",
			"rotor_1_torque_y_nm",
			"rotor_1_torque_z_nm",
			"rotor_2_torque_x_nm",
			"rotor_2_torque_y_nm",
			"rotor_2_torque_z_nm",
			"rotor_3_torque_x_nm",
			"rotor_3_torque_y_nm",
			"rotor_3_torque_z_nm",
			"rotor_4_torque_x_nm",
			"rotor_4_torque_y_nm",
			"rotor_4_torque_z_nm",
			"rotor_5_torque_x_nm",
			"rotor_5_torque_y_nm",
			"rotor_5_torque_z_nm",
			"rotor_6_torque_x_nm",
			"rotor_6_torque_y_nm",
			"rotor_6_torque_z_nm",
			"rotor_7_torque_x_nm",
			"rotor_7_torque_y_nm",
			"rotor_7_torque_z_nm",
			"rotor_arm_flex",
			"rotor_0_arm_flex",
			"rotor_1_arm_flex",
			"rotor_2_arm_flex",
			"rotor_3_arm_flex",
			"rotor_4_arm_flex",
			"rotor_5_arm_flex",
			"rotor_6_arm_flex",
			"rotor_7_arm_flex",
			"tune_cg_x_m",
			"tune_cg_y_m",
			"tune_cg_z_m",
			"tune_imu_x_m",
			"tune_imu_y_m",
			"tune_imu_z_m",
			"tune_cp_x_m",
			"tune_cp_y_m",
			"tune_cp_z_m",
			"tune_rotor_outward_cant_deg",
			"mixer_output_pitch_nm",
			"mixer_output_yaw_nm",
			"mixer_output_roll_nm",
			"mixer_pitch_authority",
			"mixer_yaw_authority",
			"mixer_roll_authority",
			"mixer_min_axis_authority",
			"mixer_low_saturation",
			"mixer_high_saturation",
			"mixer_low_headroom",
			"mixer_high_headroom",
			"pid_integral_relax_pitch",
			"pid_integral_relax_yaw",
			"pid_integral_relax_roll"
	);

	private OfflineFlightRecorder() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
			printUsage();
			return;
		}

		String presetName = args.length > 0 ? args[0] : "racing_quad";
		Path outputPath = args.length > 1
				? Path.of(args[1])
				: Path.of("build", "offline-flight", presetName + ".csv");
		double durationSeconds = args.length > 2 ? Double.parseDouble(args[2]) : DEFAULT_DURATION_SECONDS;
		FlightReport report = record(presetName, outputPath, durationSeconds);

		System.out.printf(Locale.ROOT, "Wrote %d samples to %s%n", report.samples(), outputPath.toAbsolutePath());
		System.out.printf(
				Locale.ROOT,
				"Summary: max_speed=%.2f m/s, max_current=%.1f A, max_regen=%.1f A, min_voltage=%.2f V, max_sag=%.2f V, max_spike=%.4f V, max_ripple=%.4f V, max_imu_power_noise=%.3f, max_batt=%.1f C, batt_limit=%.2f, max_propwash=%.3f, max_vrs=%.3f, max_rotor_adv=%.3f, max_tip_mach=%.3f, max_low_re=%.3f, max_bdiss_torque=%.4f N-m, max_wake_swirl=%.2f m/s, max_wake_swirl_torque=%.4f N-m, max_active_brake_torque=%.4f N-m, max_rotor_accel_torque=%.4f N-m, max_rotor_gyro_torque=%.4f N-m, max_flap_torque=%.4f N-m, min_motor_eff=%.3f, min_motor_headroom=%.3f, max_track=%.3f, min_auth=%.2f, min_mix_axis=%.2f, max_rotor_stall=%.3f, max_airframe_sep=%.3f, max_coning=%.3f, max_arm_flex=%.3f, max_scrape=%.3f, max_gust=%.2f m/s, max_shear=%.2f m/s2, max_wall=%.3f N, max_contact=%.2f/%.2f/%.2f m/s, max_contact_ang=%.0f d/s, max_aero_torque=%.4f N-m, max_baro_error=%.3f m, max_esc=%.1f C, esc_limit=%.2f%n",
				report.maxSpeedMetersPerSecond(),
				report.maxBatteryCurrentAmps(),
				report.maxBatteryRegenerativeCurrentAmps(),
				report.minBatteryVoltage(),
				report.maxBatterySagVoltage(),
				report.maxBatteryVoltageSpike(),
				report.maxBatteryBusRippleVoltage(),
				report.maxImuSupplyNoiseIntensity(),
				report.maxBatteryTemperatureCelsius(),
				report.minBatteryThermalLimit(),
				report.maxPropwashIntensity(),
				report.maxVortexRingStateIntensity(),
				report.maxRotorAdvanceRatio(),
				report.maxRotorTipMach(),
				report.maxRotorLowReynoldsLoss(),
				report.maxRotorBladeDissymmetryTorqueNewtonMeters(),
				report.maxRotorWakeSwirlVelocityMetersPerSecond(),
				report.maxRotorWakeSwirlTorqueNewtonMeters(),
				report.maxRotorActiveBrakingTorqueNewtonMeters(),
				report.maxRotorAccelerationReactionTorqueNewtonMeters(),
				report.maxRotorGyroscopicTorqueNewtonMeters(),
				report.maxRotorFlappingTorqueNewtonMeters(),
				report.minMotorElectricalEfficiency(),
				report.minMotorVoltageHeadroom(),
				report.maxMotorTrackingError(),
				report.minMotorActuatorAuthority(),
				report.minMixerAxisAuthority(),
				report.maxRotorStallIntensity(),
				report.maxAirframeSeparatedFlowIntensity(),
				report.maxRotorConingIntensity(),
				report.maxRotorArmFlexIntensity(),
				report.maxRotorSurfaceScrapeIntensity(),
				report.maxWindGustSpeedMetersPerSecond(),
				report.maxWindShearAccelerationMetersPerSecondSquared(),
				report.maxRotorWallEffectForceNewtons(),
				report.maxContactImpactSpeedMetersPerSecond(),
				report.maxContactSlipSpeedMetersPerSecond(),
				report.maxContactBounceSpeedMetersPerSecond(),
				report.maxContactAngularImpulseDegreesPerSecond(),
				report.maxAirframeTorqueNewtonMeters(),
				report.maxBarometerErrorMeters(),
				report.maxEscTemperatureCelsius(),
				report.minEscThermalLimit()
		);
	}

	public static FlightReport record(String presetName, Path outputPath) throws IOException {
		return record(presetName, outputPath, DEFAULT_DURATION_SECONDS);
	}

	public static FlightReport record(String presetName, Path outputPath, double durationSeconds) throws IOException {
		DroneConfig config = preset(presetName);
		DronePhysics physics = new DronePhysics(config);
		physics.state().setPositionMeters(new Vec3(0.0, 3.0, 0.0));

		Path parent = outputPath.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		FlightReport report = new FlightReport();
		int totalSteps = Math.max(1, (int) Math.round(durationSeconds / SIMULATION_DT_SECONDS));
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8))) {
			writer.println(CSV_HEADER);
			int sample = 0;
			for (int step = 0; step <= totalSteps; step++) {
				double timeSeconds = step * SIMULATION_DT_SECONDS;
				ScriptFrame frame = script(timeSeconds, config);
				DroneEnvironment environment = environmentFor(physics.state(), frame.windVelocityWorldMetersPerSecond());
				physics.step(frame.input(), SIMULATION_DT_SECONDS, environment);

				if (step % SAMPLE_EVERY_STEPS == 0) {
					writeSample(writer, sample, step, timeSeconds, frame, physics, environment);
					report.record(physics.state());
					sample++;
				}
			}
			report.setSamples(sample);
		}
		return report;
	}

	public static DroneConfig preset(String presetName) {
		return switch (presetName) {
			case "racing_quad" -> DroneConfig.racingQuad();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavy_lift" -> DroneConfig.heavyLift();
			case "hex_lift" -> DroneConfig.hexLift();
			case "octo_lift" -> DroneConfig.octoLift();
			case "coaxial_x8" -> DroneConfig.coaxialX8();
			default -> throw new IllegalArgumentException("Unknown preset '" + presetName + "'. Use racing_quad, cinewhoop, heavy_lift, hex_lift, octo_lift, or coaxial_x8.");
		};
	}

	public static String csvHeader() {
		return CSV_HEADER;
	}

	private static void printUsage() {
		System.out.println("Usage: OfflineFlightRecorder [preset] [output.csv] [duration_seconds]");
		System.out.println("Presets: racing_quad, cinewhoop, heavy_lift, hex_lift, octo_lift, coaxial_x8");
	}

	private static ScriptFrame script(double timeSeconds, DroneConfig config) {
		double hover = config.hoverThrottle();
		if (timeSeconds < 0.4) {
			return frame("armed_idle", 0.0, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 1.8) {
			return frame("takeoff", hover + 0.18, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 2.7) {
			return frame("hover_settle", hover + 0.035, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 4.8) {
			return frame("zero_throttle_dive", 0.0, -0.04, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 6.4) {
			return frame("propwash_recovery", 0.90, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 6.95) {
			return frame("cooldown_hover", hover + 0.02, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 7.3) {
			return frame("pitch_entry", hover + 0.08, 0.18, 0.0, 0.0, new Vec3(0.0, 0.0, -2.0));
		}
		if (timeSeconds < 8.95) {
			return frame("forward_slip", hover + 0.11, 0.0, 0.0, 0.0, new Vec3(4.0, 0.0, -2.0));
		}
		if (timeSeconds < 9.3) {
			return frame("pitch_recover", hover + 0.06, -0.18, 0.0, 0.0, new Vec3(4.0, 0.0, 0.0));
		}
		if (timeSeconds < 10.15) {
			return frame("crosswind_settle", hover + 0.04, 0.0, 0.0, 0.0, new Vec3(5.0, 0.0, 0.0));
		}
		if (timeSeconds < 10.5) {
			return frame("roll_step", 0.52, 0.0, 0.85, 0.0, new Vec3(5.0, 0.0, 0.0));
		}
		if (timeSeconds < 11.35) {
			return frame("roll_brake", hover + 0.05, 0.0, -0.45, 0.0, new Vec3(3.0, 0.0, 0.0));
		}
		if (timeSeconds < 12.25) {
			return frame("yaw_step", 0.50, 0.0, 0.0, 0.70, Vec3.ZERO);
		}
		if (timeSeconds < 12.95) {
			return frame("settle_before_punch", hover + 0.03, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 14.35) {
			return frame("throttle_punch", 0.88, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 14.75) {
			return frame("throttle_chop", 0.0, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		return frame("cooldown_hover", hover + 0.02, 0.0, 0.0, 0.0, Vec3.ZERO);
	}

	private static ScriptFrame frame(String phase, double throttle, double pitch, double roll, double yaw, Vec3 windVelocityWorldMetersPerSecond) {
		return new ScriptFrame(
				phase,
				new DroneInput(
						MathUtil.clamp(throttle, 0.0, 1.0),
						MathUtil.clamp(pitch, -1.0, 1.0),
						MathUtil.clamp(roll, -1.0, 1.0),
						MathUtil.clamp(yaw, -1.0, 1.0),
						true
				),
				windVelocityWorldMetersPerSecond
		);
	}

	private static DroneEnvironment environmentFor(DroneState state, Vec3 windVelocityWorldMetersPerSecond) {
		double altitude = state.positionMeters().y();
		double ambientTemperatureCelsius = MathUtil.clamp(25.0 - Math.max(0.0, altitude) * 0.0065, -40.0, 65.0);
		double airDensityRatio = DroneEnvironment.standardAtmosphereAirDensityRatio(altitude, ambientTemperatureCelsius);
		double groundClearance = Math.max(0.0, altitude);
		double turbulenceIntensity = MathUtil.clamp(windVelocityWorldMetersPerSecond.length() / 12.0, 0.0, 0.55);
		return new DroneEnvironment(
				windVelocityWorldMetersPerSecond,
				airDensityRatio,
				groundClearance,
				turbulenceIntensity,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				ambientTemperatureCelsius
		);
	}

	private static void writeSample(
			PrintWriter writer,
			int sample,
			int step,
			double timeSeconds,
			ScriptFrame frame,
			DronePhysics physics,
			DroneEnvironment environment
	) {
		DroneState state = physics.state();
		Vec3 position = state.positionMeters();
		Vec3 velocity = state.velocityMetersPerSecond();
		Vec3 euler = normalizeEulerForLog(state.orientation());
		Vec3 estimatedEuler = normalizeEulerForLog(state.estimatedOrientation());
		Vec3 attitudeError = state.attitudeEstimateErrorRadians();
		Vec3 angularVelocity = state.angularVelocityBodyRadiansPerSecond();
		Vec3 gyro = state.gyroAngularVelocityBodyRadiansPerSecond();
		Vec3 gyroBias = state.gyroBiasBodyRadiansPerSecond();
		Vec3 linearAcceleration = state.linearAccelerationWorldMetersPerSecondSquared();
		Vec3 accelerometer = state.accelerometerBodyMetersPerSecondSquared();
		Vec3 accelerometerBias = state.accelerometerBiasBodyMetersPerSecondSquared();
		Vec3 targetRates = state.targetRatesBodyRadiansPerSecond();
		Vec3 rateError = state.rateErrorBodyRadiansPerSecond();
		Vec3 levelTarget = state.levelTargetAttitudeRadians();
		Vec3 levelError = state.levelAttitudeErrorRadians();
		Vec3 pidP = state.pidProportionalTorqueBodyNewtonMeters();
		Vec3 pidI = state.pidIntegralTorqueBodyNewtonMeters();
		Vec3 pidD = state.pidDerivativeTorqueBodyNewtonMeters();
		Vec3 pidFf = state.pidFeedForwardTorqueBodyNewtonMeters();
		Vec3 pidOutput = state.pidOutputTorqueBodyNewtonMeters();
		Vec3 propwashTorque = state.propwashTorqueBodyNewtonMeters();
		Vec3 airframeTorque = state.airframeAerodynamicTorqueBodyNewtonMeters();
		Vec3 pressureCenterTorque = state.airframePressureCenterTorqueBodyNewtonMeters();
		Vec3 airframeAngularDragTorque = state.airframeAngularDragTorqueBodyNewtonMeters();
		Vec3 airframeLift = state.airframeLiftForceBodyNewtons();
		Vec3 groundEffectDrag = state.groundEffectDragForceBodyNewtons();
		Vec3 rotorWashDrag = state.rotorWashDragForceBodyNewtons();
		Vec3 rotorWallEffect = state.rotorWallEffectForceBodyNewtons();
		Vec3 turbulenceTorque = state.windTurbulenceTorqueBodyNewtonMeters();
		Vec3 rotorSkewTorque = state.rotorInflowSkewTorqueBodyNewtonMeters();
		Vec3 rotorBladeDissymmetryTorque = state.rotorBladeDissymmetryTorqueBodyNewtonMeters();
		Vec3 rotorWakeSwirlTorque = state.rotorWakeSwirlTorqueBodyNewtonMeters();
		Vec3 rotorInertiaTorque = state.rotorInertiaTorqueBodyNewtonMeters();
		Vec3 rotorAccelerationReactionTorque = state.rotorAccelerationReactionTorqueBodyNewtonMeters();
		Vec3 rotorGyroscopicTorque = state.rotorGyroscopicTorqueBodyNewtonMeters();
		Vec3 rotorActiveBrakingTorque = state.rotorActiveBrakingTorqueBodyNewtonMeters();
		Vec3 rotorFlappingTorque = state.rotorFlappingTorqueBodyNewtonMeters();
		Vec3 rotorAngularDragTorque = state.rotorAngularDragTorqueBodyNewtonMeters();
		Vec3 relativeAir = state.relativeAirVelocityBodyMetersPerSecond();
		Vec3 wind = environment.windVelocityWorldMetersPerSecond();
		Vec3 effectiveWind = state.effectiveWindVelocityWorldMetersPerSecond();
		Vec3 contactAngularImpulse = state.contactAngularImpulseBodyRadiansPerSecond();
		DroneInput input = frame.input();
		DroneInput processedInput = state.processedControlInput();

		writer.printf(
				Locale.ROOT,
				"%d,%d,%.3f,%s,%.5f,%.5f,%.5f,%.5f,%d,%s,%d,%.5f,%.5f,%.5f,%.5f,%d,%s,%d,%.3f,%d,"
						+ "%.5f,%.5f,%.6f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.3f,%.3f,%.3f,"
						+ "%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,"
						+ "%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.3f,%.5f,%.3f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.6f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.3f,%.3f,%.3f,%.3f,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,"
						+ "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
						+ "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
						+ "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
						+ "%.4f,%.4f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.3f,%.2f,%.3f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.3f,%.0f,%.4f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.3f,%.3f,%.5f,%.5f,"
						+ "%.3f,%.3f,%.3f,%.3f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.3f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.4f,%.4f,%.4f,%.4f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f%s%n",
				sample,
				step,
				timeSeconds,
				frame.phase(),
				input.throttle(),
				input.pitch(),
				input.roll(),
				input.yaw(),
				input.armed() ? 1 : 0,
				input.flightMode().csvName(),
				input.linkActive() ? 1 : 0,
				processedInput.throttle(),
				processedInput.pitch(),
				processedInput.roll(),
				processedInput.yaw(),
				processedInput.armed() ? 1 : 0,
				processedInput.flightMode().csvName(),
				processedInput.linkActive() ? 1 : 0,
				state.controlLinkLossSeconds(),
				state.controlFailsafeActive() ? 1 : 0,
				state.controlFrameAgeSeconds(),
				state.controlFrameIntervalSeconds(),
				state.controlFrameError(),
				position.x(),
				position.y(),
				position.z(),
				velocity.x(),
				velocity.y(),
				velocity.z(),
				velocity.length(),
				Math.toDegrees(euler.x()),
				Math.toDegrees(euler.y()),
				Math.toDegrees(euler.z()),
				Math.toDegrees(estimatedEuler.x()),
				Math.toDegrees(estimatedEuler.y()),
				Math.toDegrees(estimatedEuler.z()),
				Math.toDegrees(attitudeError.x()),
				Math.toDegrees(attitudeError.y()),
				Math.toDegrees(attitudeError.z()),
				Math.toDegrees(attitudeError.length()),
				state.attitudeEstimatorAccelerometerTrust(),
				Math.toDegrees(angularVelocity.x()),
				Math.toDegrees(angularVelocity.y()),
				Math.toDegrees(angularVelocity.z()),
				Math.toDegrees(gyro.x()),
				Math.toDegrees(gyro.y()),
				Math.toDegrees(gyro.z()),
				Math.toDegrees(gyroBias.x()),
				Math.toDegrees(gyroBias.y()),
				Math.toDegrees(gyroBias.z()),
				state.gyroClipIntensity(),
				state.gyroDynamicNotchFrequencyHertz(),
				state.gyroDynamicNotchAttenuation(),
				state.gyroBladePassNotchFrequencyHertz(),
				state.gyroBladePassNotchAttenuation(),
				linearAcceleration.x(),
				linearAcceleration.y(),
				linearAcceleration.z(),
				accelerometer.x(),
				accelerometer.y(),
				accelerometer.z(),
				accelerometerBias.x(),
				accelerometerBias.y(),
				accelerometerBias.z(),
				state.accelerometerClipIntensity(),
				state.averageEscOutputCommand(),
				state.escOutputCommand(0),
				state.escOutputCommand(1),
				state.escOutputCommand(2),
				state.escOutputCommand(3),
				state.escCommandFrameAgeSeconds(),
				state.escCommandFrameIntervalSeconds(),
				state.escCommandError(),
				state.averageMotorRpm(),
				state.motorRpm(0),
				state.motorRpm(1),
				state.motorRpm(2),
				state.motorRpm(3),
				state.averageMotorTargetRpm(),
				state.motorTargetRpm(0),
				state.motorTargetRpm(1),
				state.motorTargetRpm(2),
				state.motorTargetRpm(3),
				state.averageMotorTrackingError(),
				state.motorTrackingError(0),
				state.motorTrackingError(1),
				state.motorTrackingError(2),
				state.motorTrackingError(3),
				state.averageMotorActuatorAuthority(),
				state.motorActuatorAuthority(0),
				state.motorActuatorAuthority(1),
				state.motorActuatorAuthority(2),
				state.motorActuatorAuthority(3),
				state.averageMotorAngularAccelerationRadiansPerSecondSquared(),
				state.motorAngularAccelerationRadiansPerSecondSquared(0),
				state.motorAngularAccelerationRadiansPerSecondSquared(1),
				state.motorAngularAccelerationRadiansPerSecondSquared(2),
				state.motorAngularAccelerationRadiansPerSecondSquared(3),
				state.averageMotorAerodynamicTorqueNewtonMeters(),
				state.motorAerodynamicTorqueNewtonMeters(0),
				state.motorAerodynamicTorqueNewtonMeters(1),
				state.motorAerodynamicTorqueNewtonMeters(2),
				state.motorAerodynamicTorqueNewtonMeters(3),
				state.averageMotorShaftPowerWatts(),
				state.motorShaftPowerWatts(0),
				state.motorShaftPowerWatts(1),
				state.motorShaftPowerWatts(2),
				state.motorShaftPowerWatts(3),
				state.averageMotorCurrentAmps(),
				state.motorCurrentAmps(0),
				state.motorCurrentAmps(1),
				state.motorCurrentAmps(2),
				state.motorCurrentAmps(3),
				state.batteryCurrentAmps(),
				state.batteryVoltage(),
				state.batteryOpenCircuitVoltage(),
				state.batteryOhmicSagVoltage(),
				state.batteryTransientSagVoltage(),
				state.batteryRegenerativeCurrentAmps(),
				state.batteryVoltageSpike(),
				state.batteryStateOfCharge(),
				state.batteryCurrentLimit(),
				state.batteryPowerLimit(),
				state.averageMotorTemperatureCelsius(),
				state.motorThermalLimit(),
				state.averageMotorCoolingFactor(),
				state.motorCoolingFactor(0),
				state.motorCoolingFactor(1),
				state.motorCoolingFactor(2),
				state.motorCoolingFactor(3),
				state.rotorThrustNewtons(0),
				state.rotorThrustNewtons(1),
				state.rotorThrustNewtons(2),
				state.rotorThrustNewtons(3),
				state.averageRotorInducedVelocityMetersPerSecond(),
				state.averageRotorTranslationalLiftIntensity(),
				state.averageRotorAerodynamicLoadFactor(),
				state.rotorInflowSkewIntensity(),
				state.averageRotorWakeInterferenceIntensity(),
				state.rotorWakeInterferenceIntensity(0),
				state.rotorWakeInterferenceIntensity(1),
				state.rotorWakeInterferenceIntensity(2),
				state.rotorWakeInterferenceIntensity(3),
				rotorBladeDissymmetryTorque.x(),
				rotorBladeDissymmetryTorque.y(),
				rotorBladeDissymmetryTorque.z(),
				rotorSkewTorque.x(),
				rotorSkewTorque.y(),
				rotorSkewTorque.z(),
				rotorWakeSwirlTorque.x(),
				rotorWakeSwirlTorque.y(),
				rotorWakeSwirlTorque.z(),
				rotorInertiaTorque.x(),
				rotorInertiaTorque.y(),
				rotorInertiaTorque.z(),
				rotorAccelerationReactionTorque.x(),
				rotorAccelerationReactionTorque.y(),
				rotorAccelerationReactionTorque.z(),
				rotorGyroscopicTorque.x(),
				rotorGyroscopicTorque.y(),
				rotorGyroscopicTorque.z(),
				rotorActiveBrakingTorque.x(),
				rotorActiveBrakingTorque.y(),
				rotorActiveBrakingTorque.z(),
				rotorFlappingTorque.x(),
				rotorFlappingTorque.y(),
				rotorFlappingTorque.z(),
				rotorAngularDragTorque.x(),
				rotorAngularDragTorque.y(),
				rotorAngularDragTorque.z(),
				state.averageRotorFlappingForceNewtons(),
				state.averageRotorStallIntensity(),
				state.rotorVibration(),
				state.mixerSaturation(),
				state.pidAttenuation(),
				state.pidIntegralRelax(),
				state.pidDTermLowPassCutoffHertz(),
				state.antiGravityBoost(),
				Math.toDegrees(levelTarget.x()),
				Math.toDegrees(levelTarget.z()),
				Math.toDegrees(levelError.x()),
				Math.toDegrees(levelError.z()),
				state.selfLevelBlend(),
				Math.toDegrees(targetRates.x()),
				Math.toDegrees(targetRates.y()),
				Math.toDegrees(targetRates.z()),
				Math.toDegrees(rateError.x()),
				Math.toDegrees(rateError.y()),
				Math.toDegrees(rateError.z()),
				pidP.x(),
				pidP.y(),
				pidP.z(),
				pidI.x(),
				pidI.y(),
				pidI.z(),
				pidD.x(),
				pidD.y(),
				pidD.z(),
				pidFf.x(),
				pidFf.y(),
				pidFf.z(),
				pidOutput.x(),
				pidOutput.y(),
				pidOutput.z(),
				state.propwashIntensity(),
				state.propwashWakeIntensity(),
				state.vortexRingStateIntensity(),
				propwashTorque.x(),
				propwashTorque.y(),
				propwashTorque.z(),
				airframeTorque.x(),
				airframeTorque.y(),
				airframeTorque.z(),
				pressureCenterTorque.x(),
				pressureCenterTorque.y(),
				pressureCenterTorque.z(),
				airframeAngularDragTorque.x(),
				airframeAngularDragTorque.y(),
				airframeAngularDragTorque.z(),
				relativeAir.x(),
				relativeAir.y(),
				relativeAir.z(),
				state.airspeedMetersPerSecond(),
				Math.toDegrees(state.angleOfAttackRadians()),
				Math.toDegrees(state.sideslipRadians()),
				state.airframeSeparatedFlowIntensity(),
				environment.turbulenceIntensity(),
				environment.obstacleProximity(),
				turbulenceTorque.x(),
				turbulenceTorque.y(),
				turbulenceTorque.z(),
				wind.x(),
				wind.y(),
				wind.z(),
				environment.airDensityRatio(),
				environment.effectiveAirDensityRatio(),
				environment.ambientTemperatureCelsius(),
				environment.groundClearanceMeters(),
				environment.groundEffectThrustMultiplier(physics.config()),
				physics.config().rateSuper().x(),
				physics.config().rateSuper().y(),
				physics.config().rateSuper().z(),
				Math.toDegrees(physics.config().selfLevelMaxAngleRadians()),
				physics.config().selfLevelRateGain(),
				physics.config().horizonTransitionStartStick(),
				physics.config().horizonTransitionEndStick(),
				physics.config().pidIntegralRelaxStrength(),
				physics.config().attitudeEstimatorAccelerometerCorrectionGain(),
				physics.config().attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared(),
				physics.config().rcCommandSmoothingTimeConstantSeconds(),
				physics.config().rcCommandLatencySeconds(),
				physics.config().rcFailsafeTimeoutSeconds(),
				physics.config().rcFrameRateHertz(),
				(double) physics.config().rcChannelResolutionSteps(),
				physics.config().escOutputFallSlewRatePerSecond(),
				physics.config().escDeadband(),
				physics.config().motorActiveBrakingStrength(),
				physics.config().accelerometerLowPassCutoffHz(),
				physics.config().accelerometerNoiseStdDevMetersPerSecondSquared(),
				physics.config().escCommandFrameRateHertz(),
				(double) physics.config().escCommandResolutionSteps(),
				physics.config().rotors().get(0).bladePitchMeters(),
				physics.config().averageRotorImbalanceIntensity(),
				airframeLift.x(),
				airframeLift.y(),
				airframeLift.z(),
				airframeLift.length(),
				groundEffectDrag.x(),
				groundEffectDrag.y(),
				groundEffectDrag.z(),
				groundEffectDrag.length(),
				rotorWashDrag.x(),
				rotorWashDrag.y(),
				rotorWashDrag.z(),
				rotorWashDrag.length(),
				state.barometerAltitudeMeters(),
				state.barometerVerticalSpeedMetersPerSecond(),
				state.barometerPressureHectopascals(),
				state.barometerErrorMeters(),
				state.barometerPropwashErrorMeters(),
				state.averageEscTemperatureCelsius(),
				state.maxEscTemperatureCelsius(),
				state.escThermalLimit(),
				state.averageEscCoolingFactor(),
				state.escTemperatureCelsius(0),
				state.escTemperatureCelsius(1),
				state.escTemperatureCelsius(2),
				state.escTemperatureCelsius(3),
				state.escThermalLimit(0),
				state.escThermalLimit(1),
				state.escThermalLimit(2),
				state.escThermalLimit(3),
				state.escCoolingFactor(0),
				state.escCoolingFactor(1),
				state.escCoolingFactor(2),
				state.escCoolingFactor(3),
				effectiveWind.x(),
				effectiveWind.y(),
				effectiveWind.z(),
				state.windGustSpeedMetersPerSecond(),
				state.windShearAccelerationMetersPerSecondSquared(),
				rotorWallEffect.x(),
				rotorWallEffect.y(),
				rotorWallEffect.z(),
				rotorWallEffect.length(),
				state.contactImpactSpeedMetersPerSecond(),
				state.contactSlipSpeedMetersPerSecond(),
				state.contactBounceSpeedMetersPerSecond(),
				Math.toDegrees(contactAngularImpulse.x()),
				Math.toDegrees(contactAngularImpulse.y()),
				Math.toDegrees(contactAngularImpulse.z()),
				Math.toDegrees(contactAngularImpulse.length()),
				state.averageRotorSurfaceScrapeIntensity(),
				state.rotorSurfaceScrapeIntensity(0),
				state.rotorSurfaceScrapeIntensity(1),
				state.rotorSurfaceScrapeIntensity(2),
				state.rotorSurfaceScrapeIntensity(3),
				extraRotorColumns(state, physics.config(), environment)
		);
	}

	private static String extraRotorColumns(DroneState state, DroneConfig config, DroneEnvironment environment) {
		StringBuilder builder = new StringBuilder();
		double[] motorPowers = state.motorPower(config);
		double[] motorRpm = state.motorRpm();
		double[] motorTargetOmega = state.motorTargetOmegaRadiansPerSecond();
		double[] motorTrackingError = state.motorTrackingError();
		double[] motorActuatorAuthority = state.motorActuatorAuthority();
		double[] motorCurrents = state.motorCurrentAmps();
		double[] motorPhaseCurrents = state.motorPhaseCurrentAmps();
		double[] motorCurrentRipples = state.motorCurrentRippleAmps();
		double[] motorCommutationRipples = state.motorCommutationRippleIntensity();
		double[] motorTorqueRipples = state.motorTorqueRippleNewtonMeters();
		double[] rotorThrust = state.rotorThrustNewtons();
		double[] rotorHealth = state.rotorHealth();
		double[] rotorScrape = state.rotorSurfaceScrapeIntensity();
		double[] rotorWakeInterference = state.rotorWakeInterferenceIntensity();
		double[] rotorWakeSwirl = state.rotorWakeSwirlVelocityMetersPerSecond();
		double[] rotorAdvanceRatio = state.rotorAdvanceRatio();
		double[] rotorTipMach = state.rotorTipMach();
		double[] rotorLowReynoldsLoss = state.rotorLowReynoldsLoss();
		double[] rotorBladeAngleOfAttack = state.rotorBladeAngleOfAttackRadians();
		double[] rotorBladeElementStall = state.rotorBladeElementStallIntensity();
		double[] rotorBladeDissymmetry = state.rotorBladeDissymmetryIntensity();
		double[] rotorBladePassRipple = state.rotorBladePassRippleIntensity();
		double[] rotorFlappingTilt = state.rotorFlappingTiltRadians();
		double[] rotorConing = state.rotorConingIntensity();
		double[] motorElectricalEfficiency = state.motorElectricalEfficiency();
		double[] motorVoltageHeadroom = state.motorVoltageHeadroom();
		double[] motorMechanicalLoss = state.motorMechanicalLossTorqueNewtonMeters();
		Vec3[] rotorForceBody = state.rotorForceBodyNewtons();
		Vec3[] rotorTorqueBody = state.rotorTorqueBodyNewtonMeters();
		double[] rotorArmFlex = state.rotorArmFlexIntensity();
		Vec3 mixerOutputTorque = state.mixerOutputTorqueBodyNewtonMeters();
		Vec3 mixerAxisAuthority = state.mixerAxisAuthority();
		Vec3 pidIntegralRelaxAxes = state.pidIntegralRelaxAxes();

		appendExtra(builder, config.rotors().size());
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorPowers, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorRpm, i), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorTargetOmega, i) * 60.0 / (Math.PI * 2.0), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorTrackingError, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrOne(motorActuatorAuthority, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorCurrents, i), "%.3f");
		}
		appendExtra(builder, state.averageMotorPhaseCurrentAmps(), "%.3f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorPhaseCurrents, i), "%.3f");
		}
		appendExtra(builder, state.averageMotorCurrentRippleAmps(), "%.3f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorCurrentRipples, i), "%.3f");
		}
		appendExtra(builder, state.averageMotorCommutationRippleIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorCommutationRipples, i), "%.5f");
		}
		appendExtra(builder, state.averageMotorTorqueRippleNewtonMeters(), "%.6f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorTorqueRipples, i), "%.6f");
		}
		appendExtra(builder, state.batteryBusRippleVoltage(), "%.5f");
		appendExtra(builder, state.imuSupplyNoiseIntensity(), "%.5f");
		appendExtra(builder, state.batteryTemperatureCelsius(), "%.3f");
		appendExtra(builder, state.batteryCoolingFactor(), "%.5f");
		appendExtra(builder, state.batteryThermalLimit(), "%.5f");
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorThrust, i), "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorHealth, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorScrape, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorWakeInterference, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorWakeSwirlVelocityMetersPerSecond(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorWakeSwirl, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, i < config.rotors().size() ? environment.rotorThrustMultiplier(i, config) : 1.0, "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, environment.rotorFlowObstruction(i), "%.5f");
		}
		appendExtra(builder, state.averageRotorAdvanceRatio(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorAdvanceRatio, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorTipMach(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorTipMach, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorLowReynoldsLoss(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorLowReynoldsLoss, i), "%.5f");
		}
		appendExtra(builder, Math.toDegrees(state.averageRotorBladeAngleOfAttackRadians()), "%.4f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, Math.toDegrees(valueOrZero(rotorBladeAngleOfAttack, i)), "%.4f");
		}
		appendExtra(builder, state.averageRotorBladeElementStallIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorBladeElementStall, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorBladeDissymmetryIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorBladeDissymmetry, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorBladePassRippleIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorBladePassRipple, i), "%.5f");
		}
		appendExtra(builder, Math.toDegrees(state.averageRotorFlappingTiltRadians()), "%.4f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, Math.toDegrees(valueOrZero(rotorFlappingTilt, i)), "%.4f");
		}
		appendExtra(builder, state.averageRotorConingIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorConing, i), "%.5f");
		}
		appendExtra(builder, state.averageMotorElectricalEfficiency(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorElectricalEfficiency, i), "%.5f");
		}
		appendExtra(builder, state.averageMotorVoltageHeadroom(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(motorVoltageHeadroom, i), "%.5f");
		}
		appendExtra(builder, environment.waterImmersionIntensity(), "%.5f");
		appendExtra(builder, environment.precipitationWetnessIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, environment.rotorWaterImmersion(i), "%.5f");
		}
		appendExtra(builder, state.averageMotorMechanicalLossTorqueNewtonMeters(), "%.6f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorMechanicalLoss, i), "%.6f");
		}
		for (int i = 0; i < 8; i++) {
			appendRotorForceColumns(builder, rotorForceBody, i);
		}
		for (int i = 0; i < 8; i++) {
			appendRotorTorqueColumns(builder, rotorTorqueBody, i);
		}
		appendExtra(builder, state.averageRotorArmFlexIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorArmFlex, i), "%.5f");
		}
		appendExtra(builder, config.centerOfMassOffsetBodyMeters().x(), "%.5f");
		appendExtra(builder, config.centerOfMassOffsetBodyMeters().y(), "%.5f");
		appendExtra(builder, config.centerOfMassOffsetBodyMeters().z(), "%.5f");
		appendExtra(builder, config.imuOffsetBodyMeters().x(), "%.5f");
		appendExtra(builder, config.imuOffsetBodyMeters().y(), "%.5f");
		appendExtra(builder, config.imuOffsetBodyMeters().z(), "%.5f");
		appendExtra(builder, config.centerOfPressureOffsetBodyMeters().x(), "%.5f");
		appendExtra(builder, config.centerOfPressureOffsetBodyMeters().y(), "%.5f");
		appendExtra(builder, config.centerOfPressureOffsetBodyMeters().z(), "%.5f");
		appendExtra(builder, config.averageRotorOutwardCantDegrees(), "%.3f");
		appendExtra(builder, mixerOutputTorque.x(), "%.6f");
		appendExtra(builder, mixerOutputTorque.y(), "%.6f");
		appendExtra(builder, mixerOutputTorque.z(), "%.6f");
		appendExtra(builder, mixerAxisAuthority.x(), "%.6f");
		appendExtra(builder, mixerAxisAuthority.y(), "%.6f");
		appendExtra(builder, mixerAxisAuthority.z(), "%.6f");
		appendExtra(builder, state.minMixerAxisAuthority(), "%.6f");
		appendExtra(builder, state.mixerLowSaturation(), "%.6f");
		appendExtra(builder, state.mixerHighSaturation(), "%.6f");
		appendExtra(builder, state.mixerLowHeadroom(), "%.6f");
		appendExtra(builder, state.mixerHighHeadroom(), "%.6f");
		appendExtra(builder, pidIntegralRelaxAxes.x(), "%.6f");
		appendExtra(builder, pidIntegralRelaxAxes.y(), "%.6f");
		appendExtra(builder, pidIntegralRelaxAxes.z(), "%.6f");
		return builder.toString();
	}

	private static void appendExtra(StringBuilder builder, int value) {
		builder.append(',').append(value);
	}

	private static void appendExtra(StringBuilder builder, double value, String format) {
		builder.append(',').append(String.format(Locale.ROOT, format, value));
	}

	private static double valueOrZero(double[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : 0.0;
	}

	private static double valueOrOne(double[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : 1.0;
	}

	private static Vec3 vectorOrZero(Vec3[] values, int index) {
		return index >= 0 && index < values.length && values[index] != null ? values[index] : Vec3.ZERO;
	}

	private static void appendRotorForceColumns(StringBuilder builder, Vec3[] rotorForceBody, int index) {
		Vec3 force = vectorOrZero(rotorForceBody, index);
		appendExtra(builder, force.x(), "%.5f");
		appendExtra(builder, force.y(), "%.5f");
		appendExtra(builder, force.z(), "%.5f");
	}

	private static void appendRotorTorqueColumns(StringBuilder builder, Vec3[] rotorTorqueBody, int index) {
		Vec3 torque = vectorOrZero(rotorTorqueBody, index);
		appendExtra(builder, torque.x(), "%.6f");
		appendExtra(builder, torque.y(), "%.6f");
		appendExtra(builder, torque.z(), "%.6f");
	}

	private static Vec3 normalizeEulerForLog(Quaternion orientation) {
		Vec3 euler = orientation.toEulerXYZRadians();
		return new Vec3(
				normalizeRadians(euler.x()),
				normalizeRadians(euler.y()),
				normalizeRadians(euler.z())
		);
	}

	private static double normalizeRadians(double radians) {
		double normalized = radians;
		while (normalized > Math.PI) {
			normalized -= Math.PI * 2.0;
		}
		while (normalized < -Math.PI) {
			normalized += Math.PI * 2.0;
		}
		return normalized;
	}

	private record ScriptFrame(String phase, DroneInput input, Vec3 windVelocityWorldMetersPerSecond) {
	}

	public static final class FlightReport {
		private int samples;
		private double maxSpeedMetersPerSecond;
		private double maxBatteryCurrentAmps;
		private double maxBatteryRegenerativeCurrentAmps;
		private double minBatteryVoltage = Double.POSITIVE_INFINITY;
		private double maxBatterySagVoltage;
		private double maxBatteryVoltageSpike;
		private double maxBatteryBusRippleVoltage;
		private double maxImuSupplyNoiseIntensity;
		private double maxBatteryTemperatureCelsius = 25.0;
		private double minBatteryThermalLimit = 1.0;
		private double maxPropwashIntensity;
		private double maxVortexRingStateIntensity;
		private double maxRotorAdvanceRatio;
		private double maxRotorTipMach;
		private double maxRotorLowReynoldsLoss;
		private double maxRotorBladeDissymmetryTorqueNewtonMeters;
		private double maxRotorWakeSwirlVelocityMetersPerSecond;
		private double maxRotorWakeSwirlTorqueNewtonMeters;
		private double maxRotorActiveBrakingTorqueNewtonMeters;
		private double maxRotorAccelerationReactionTorqueNewtonMeters;
		private double maxRotorGyroscopicTorqueNewtonMeters;
		private double maxRotorFlappingTorqueNewtonMeters;
		private double minMotorElectricalEfficiency = 1.0;
		private double minMotorVoltageHeadroom = 1.0;
		private double maxMotorTrackingError;
		private double minMotorActuatorAuthority = 1.0;
		private double minMixerAxisAuthority = 1.0;
		private double maxRotorStallIntensity;
		private double maxAirframeSeparatedFlowIntensity;
		private double maxRotorConingIntensity;
		private double maxRotorArmFlexIntensity;
		private double maxRotorSurfaceScrapeIntensity;
		private double maxWindGustSpeedMetersPerSecond;
		private double maxWindShearAccelerationMetersPerSecondSquared;
		private double maxRotorWallEffectForceNewtons;
		private double maxContactImpactSpeedMetersPerSecond;
		private double maxContactSlipSpeedMetersPerSecond;
		private double maxContactBounceSpeedMetersPerSecond;
		private double maxContactAngularImpulseDegreesPerSecond;
		private double maxAirframeTorqueNewtonMeters;
		private double maxBarometerErrorMeters;
		private double maxBarometerPropwashErrorMeters;
		private double maxEscTemperatureCelsius;
		private double minEscThermalLimit = 1.0;

		private void record(DroneState state) {
			maxSpeedMetersPerSecond = Math.max(maxSpeedMetersPerSecond, state.speedMetersPerSecond());
			maxBatteryCurrentAmps = Math.max(maxBatteryCurrentAmps, state.batteryCurrentAmps());
			maxBatteryRegenerativeCurrentAmps = Math.max(maxBatteryRegenerativeCurrentAmps, state.batteryRegenerativeCurrentAmps());
			minBatteryVoltage = Math.min(minBatteryVoltage, state.batteryVoltage());
			maxBatterySagVoltage = Math.max(maxBatterySagVoltage, state.batteryOhmicSagVoltage() + state.batteryTransientSagVoltage());
			maxBatteryVoltageSpike = Math.max(maxBatteryVoltageSpike, state.batteryVoltageSpike());
			maxBatteryBusRippleVoltage = Math.max(maxBatteryBusRippleVoltage, state.batteryBusRippleVoltage());
			maxImuSupplyNoiseIntensity = Math.max(maxImuSupplyNoiseIntensity, state.imuSupplyNoiseIntensity());
			maxBatteryTemperatureCelsius = Math.max(maxBatteryTemperatureCelsius, state.batteryTemperatureCelsius());
			minBatteryThermalLimit = Math.min(minBatteryThermalLimit, state.batteryThermalLimit());
			maxPropwashIntensity = Math.max(maxPropwashIntensity, state.propwashIntensity());
			maxVortexRingStateIntensity = Math.max(maxVortexRingStateIntensity, state.vortexRingStateIntensity());
			maxRotorAdvanceRatio = Math.max(maxRotorAdvanceRatio, state.maxRotorAdvanceRatio());
			maxRotorTipMach = Math.max(maxRotorTipMach, state.maxRotorTipMach());
			maxRotorLowReynoldsLoss = Math.max(maxRotorLowReynoldsLoss, state.maxRotorLowReynoldsLoss());
			maxRotorBladeDissymmetryTorqueNewtonMeters = Math.max(
					maxRotorBladeDissymmetryTorqueNewtonMeters,
					state.rotorBladeDissymmetryTorqueBodyNewtonMeters().length()
			);
			maxRotorWakeSwirlVelocityMetersPerSecond = Math.max(
					maxRotorWakeSwirlVelocityMetersPerSecond,
					state.maxRotorWakeSwirlVelocityMetersPerSecond()
			);
			maxRotorWakeSwirlTorqueNewtonMeters = Math.max(
					maxRotorWakeSwirlTorqueNewtonMeters,
					state.rotorWakeSwirlTorqueBodyNewtonMeters().length()
			);
			maxRotorActiveBrakingTorqueNewtonMeters = Math.max(
					maxRotorActiveBrakingTorqueNewtonMeters,
					state.rotorActiveBrakingTorqueBodyNewtonMeters().length()
			);
			maxRotorAccelerationReactionTorqueNewtonMeters = Math.max(
					maxRotorAccelerationReactionTorqueNewtonMeters,
					state.rotorAccelerationReactionTorqueBodyNewtonMeters().length()
			);
			maxRotorGyroscopicTorqueNewtonMeters = Math.max(
					maxRotorGyroscopicTorqueNewtonMeters,
					state.rotorGyroscopicTorqueBodyNewtonMeters().length()
			);
			maxRotorFlappingTorqueNewtonMeters = Math.max(
					maxRotorFlappingTorqueNewtonMeters,
					state.rotorFlappingTorqueBodyNewtonMeters().length()
			);
			minMotorElectricalEfficiency = Math.min(minMotorElectricalEfficiency, state.minMotorElectricalEfficiency());
			minMotorVoltageHeadroom = Math.min(minMotorVoltageHeadroom, state.minMotorVoltageHeadroom());
			maxMotorTrackingError = Math.max(maxMotorTrackingError, state.maxMotorTrackingError());
			minMotorActuatorAuthority = Math.min(minMotorActuatorAuthority, state.minMotorActuatorAuthority());
			minMixerAxisAuthority = Math.min(minMixerAxisAuthority, state.minMixerAxisAuthority());
			maxRotorStallIntensity = Math.max(maxRotorStallIntensity, state.averageRotorStallIntensity());
			maxAirframeSeparatedFlowIntensity = Math.max(maxAirframeSeparatedFlowIntensity, state.airframeSeparatedFlowIntensity());
			maxRotorConingIntensity = Math.max(maxRotorConingIntensity, state.maxRotorConingIntensity());
			maxRotorArmFlexIntensity = Math.max(maxRotorArmFlexIntensity, state.maxRotorArmFlexIntensity());
			maxRotorSurfaceScrapeIntensity = Math.max(maxRotorSurfaceScrapeIntensity, state.maxRotorSurfaceScrapeIntensity());
			maxWindGustSpeedMetersPerSecond = Math.max(maxWindGustSpeedMetersPerSecond, state.windGustSpeedMetersPerSecond());
			maxWindShearAccelerationMetersPerSecondSquared = Math.max(maxWindShearAccelerationMetersPerSecondSquared, state.windShearAccelerationMetersPerSecondSquared());
			maxRotorWallEffectForceNewtons = Math.max(maxRotorWallEffectForceNewtons, state.rotorWallEffectForceBodyNewtons().length());
			maxContactImpactSpeedMetersPerSecond = Math.max(maxContactImpactSpeedMetersPerSecond, state.contactImpactSpeedMetersPerSecond());
			maxContactSlipSpeedMetersPerSecond = Math.max(maxContactSlipSpeedMetersPerSecond, state.contactSlipSpeedMetersPerSecond());
			maxContactBounceSpeedMetersPerSecond = Math.max(maxContactBounceSpeedMetersPerSecond, state.contactBounceSpeedMetersPerSecond());
			maxContactAngularImpulseDegreesPerSecond = Math.max(maxContactAngularImpulseDegreesPerSecond, Math.toDegrees(state.contactAngularImpulseBodyRadiansPerSecond().length()));
			maxAirframeTorqueNewtonMeters = Math.max(maxAirframeTorqueNewtonMeters, state.airframeAerodynamicTorqueBodyNewtonMeters().length());
			maxBarometerErrorMeters = Math.max(maxBarometerErrorMeters, Math.abs(state.barometerErrorMeters()));
			maxBarometerPropwashErrorMeters = Math.max(maxBarometerPropwashErrorMeters, Math.abs(state.barometerPropwashErrorMeters()));
			maxEscTemperatureCelsius = Math.max(maxEscTemperatureCelsius, state.maxEscTemperatureCelsius());
			minEscThermalLimit = Math.min(minEscThermalLimit, state.escThermalLimit());
		}

		private void setSamples(int samples) {
			this.samples = samples;
		}

		public int samples() {
			return samples;
		}

		public double maxSpeedMetersPerSecond() {
			return maxSpeedMetersPerSecond;
		}

		public double maxBatteryCurrentAmps() {
			return maxBatteryCurrentAmps;
		}

		public double maxBatteryRegenerativeCurrentAmps() {
			return maxBatteryRegenerativeCurrentAmps;
		}

		public double minBatteryVoltage() {
			return minBatteryVoltage;
		}

		public double maxBatterySagVoltage() {
			return maxBatterySagVoltage;
		}

		public double maxBatteryVoltageSpike() {
			return maxBatteryVoltageSpike;
		}

		public double maxBatteryBusRippleVoltage() {
			return maxBatteryBusRippleVoltage;
		}

		public double maxImuSupplyNoiseIntensity() {
			return maxImuSupplyNoiseIntensity;
		}

		public double maxBatteryTemperatureCelsius() {
			return maxBatteryTemperatureCelsius;
		}

		public double minBatteryThermalLimit() {
			return minBatteryThermalLimit;
		}

		public double maxPropwashIntensity() {
			return maxPropwashIntensity;
		}

		public double maxVortexRingStateIntensity() {
			return maxVortexRingStateIntensity;
		}

		public double maxRotorAdvanceRatio() {
			return maxRotorAdvanceRatio;
		}

		public double maxRotorTipMach() {
			return maxRotorTipMach;
		}

		public double maxRotorLowReynoldsLoss() {
			return maxRotorLowReynoldsLoss;
		}

		public double maxRotorBladeDissymmetryTorqueNewtonMeters() {
			return maxRotorBladeDissymmetryTorqueNewtonMeters;
		}

		public double maxRotorWakeSwirlVelocityMetersPerSecond() {
			return maxRotorWakeSwirlVelocityMetersPerSecond;
		}

		public double maxRotorWakeSwirlTorqueNewtonMeters() {
			return maxRotorWakeSwirlTorqueNewtonMeters;
		}

		public double maxRotorActiveBrakingTorqueNewtonMeters() {
			return maxRotorActiveBrakingTorqueNewtonMeters;
		}

		public double maxRotorAccelerationReactionTorqueNewtonMeters() {
			return maxRotorAccelerationReactionTorqueNewtonMeters;
		}

		public double maxRotorGyroscopicTorqueNewtonMeters() {
			return maxRotorGyroscopicTorqueNewtonMeters;
		}

		public double maxRotorFlappingTorqueNewtonMeters() {
			return maxRotorFlappingTorqueNewtonMeters;
		}

		public double minMotorElectricalEfficiency() {
			return minMotorElectricalEfficiency;
		}

		public double minMotorVoltageHeadroom() {
			return minMotorVoltageHeadroom;
		}

		public double maxMotorTrackingError() {
			return maxMotorTrackingError;
		}

		public double minMotorActuatorAuthority() {
			return minMotorActuatorAuthority;
		}

		public double minMixerAxisAuthority() {
			return minMixerAxisAuthority;
		}

		public double maxRotorStallIntensity() {
			return maxRotorStallIntensity;
		}

		public double maxAirframeSeparatedFlowIntensity() {
			return maxAirframeSeparatedFlowIntensity;
		}

		public double maxRotorConingIntensity() {
			return maxRotorConingIntensity;
		}

		public double maxRotorArmFlexIntensity() {
			return maxRotorArmFlexIntensity;
		}

		public double maxRotorSurfaceScrapeIntensity() {
			return maxRotorSurfaceScrapeIntensity;
		}

		public double maxWindGustSpeedMetersPerSecond() {
			return maxWindGustSpeedMetersPerSecond;
		}

		public double maxWindShearAccelerationMetersPerSecondSquared() {
			return maxWindShearAccelerationMetersPerSecondSquared;
		}

		public double maxRotorWallEffectForceNewtons() {
			return maxRotorWallEffectForceNewtons;
		}

		public double maxContactImpactSpeedMetersPerSecond() {
			return maxContactImpactSpeedMetersPerSecond;
		}

		public double maxContactSlipSpeedMetersPerSecond() {
			return maxContactSlipSpeedMetersPerSecond;
		}

		public double maxContactBounceSpeedMetersPerSecond() {
			return maxContactBounceSpeedMetersPerSecond;
		}

		public double maxContactAngularImpulseDegreesPerSecond() {
			return maxContactAngularImpulseDegreesPerSecond;
		}

		public double maxAirframeTorqueNewtonMeters() {
			return maxAirframeTorqueNewtonMeters;
		}

		public double maxBarometerErrorMeters() {
			return maxBarometerErrorMeters;
		}

		public double maxBarometerPropwashErrorMeters() {
			return maxBarometerPropwashErrorMeters;
		}

		public double maxEscTemperatureCelsius() {
			return maxEscTemperatureCelsius;
		}

		public double minEscThermalLimit() {
			return minEscThermalLimit;
		}
	}
}
