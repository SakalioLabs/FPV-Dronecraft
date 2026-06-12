package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.AirframeDragCalibration;
import com.tenicana.dronecraft.sim.AirframeInertiaCalibration;
import com.tenicana.dronecraft.sim.CoaxialAllocationCalibration;
import com.tenicana.dronecraft.sim.ControlResponseCalibration;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.FpvLipoEsrCalibration;
import com.tenicana.dronecraft.sim.HighAdvanceRotorCalibration;
import com.tenicana.dronecraft.sim.IcingRotorCalibration;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.MotorBenchCurrentModel;
import com.tenicana.dronecraft.sim.MotorResponseCalibration;
import com.tenicana.dronecraft.sim.MotorThermalCalibration;
import com.tenicana.dronecraft.sim.NanodroneSysIdCalibration;
import com.tenicana.dronecraft.sim.NeuroBemAirframeResidualCalibration;
import com.tenicana.dronecraft.sim.PidTuningCalibration;
import com.tenicana.dronecraft.sim.PrecipitationWaterCalibration;
import com.tenicana.dronecraft.sim.PropellerDamageCalibration;
import com.tenicana.dronecraft.sim.PropGeometryCalibration;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.RateEnvelopeCalibration;
import com.tenicana.dronecraft.sim.RotorDynamicsCalibration;
import com.tenicana.dronecraft.sim.RotorFlowObstructionModel;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.SensorNoiseCalibration;
import com.tenicana.dronecraft.sim.SurfaceNearfieldCalibration;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.VrsPropwashCalibration;
import com.tenicana.dronecraft.sim.WindGustCalibration;

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
	private static final double WALL_SKIM_CLOSEST_ROTOR_CLEARANCE_METERS = 0.075;
	private static final double RAIN_BURST_PRECIPITATION_WETNESS = 1.0;
	private static final int LIGHT_PROP_FAULT_ROTOR_INDEX = 0;
	private static final double LIGHT_PROP_FAULT_DAMAGE = 0.10;
	private static final double BATTERY_AUTONOMY_DT_SECONDS = 0.010;
	private static final double BATTERY_AUTONOMY_MAX_SECONDS = 900.0;
	private static final double BATTERY_AUTONOMY_CURRENT_MATCH_SECONDS = 8.0;
	private static final double BATTERY_AUTONOMY_CURRENT_MATCH_WARMUP_SECONDS = 2.0;
	private static final int BATTERY_AUTONOMY_CURRENT_MATCH_ITERATIONS = 16;
	private static final double APDRONE_AUTONOMY_LOWER_VOLTAGE = 12.0;
	private static final double APDRONE_AUTONOMY_UPPER_VOLTAGE = 18.0;
	private static final double APDRONE_MAX_POWER_REFERENCE_SECONDS = 205.862266;
	private static final double APDRONE_MAX_POWER_REFERENCE_MEAN_CURRENT_AMPS = 25.900383408869278;
	private static final double APDRONE_MAX_POWER_REFERENCE_P95_CURRENT_AMPS = 42.06;
	private static final double APDRONE_MAX_POWER_REFERENCE_THROTTLE = 0.9867448887828499;
	private static final double APDRONE_MAX_POWER_REFERENCE_START_VOLTAGE = 16.648;
	private static final double APDRONE_MAX_POWER_REFERENCE_MEAN_VOLTAGE = 14.334578187533713;
	private static final double APDRONE_NORMAL_POWER_REFERENCE_SECONDS = 511.05425759999997;
	private static final double APDRONE_NORMAL_POWER_REFERENCE_MEAN_CURRENT_AMPS = 9.30422120169919;
	private static final double APDRONE_NORMAL_POWER_REFERENCE_P95_CURRENT_AMPS = 10.99;
	private static final double APDRONE_NORMAL_POWER_REFERENCE_THROTTLE = 0.5439609800526073;
	private static final double APDRONE_NORMAL_POWER_REFERENCE_START_VOLTAGE = 16.576;
	private static final double APDRONE_NORMAL_POWER_REFERENCE_MEAN_VOLTAGE = 14.701756347346997;
	private static final double APDRONE_SELECTED_FLIGHT_REFERENCE_MAX_SPEED_METERS_PER_SECOND = 5.75;
	private static final double APDRONE_OPEN_FIELD_MEAN_FILE_MAX_SPEED_METERS_PER_SECOND = 11.072;
	private static final double APDRONE_OPEN_FIELD_FLIGHT_2_P95_SPEED_METERS_PER_SECOND = 17.25;
	private static final double APDRONE_OPEN_FIELD_FASTEST_SPEED_METERS_PER_SECOND = 18.72;
	private static final int PROP_DAMAGE_AUDIT_ROTOR_INDEX = 0;
	private static final double PROP_DAMAGE_AUDIT_DAMAGE = PropellerDamageCalibration.REFERENCE_AUDIT_DAMAGE;
	private static final double PROP_DAMAGE_AUDIT_WARMUP_SECONDS = 2.0;
	private static final double PROP_DAMAGE_AUDIT_SAMPLE_SECONDS = 6.0;
	private static final Vec3 WALL_SKIM_DIRECTION_BODY = new Vec3(1.0, 0.0, 0.0);
	private static final Vec3[] ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY = {
			new Vec3(1.0, 0.0, 0.0),
			new Vec3(-1.0, 0.0, 0.0),
			new Vec3(0.0, 0.0, 1.0),
			new Vec3(0.0, 0.0, -1.0),
			new Vec3(1.0, 0.0, 1.0).normalized(),
			new Vec3(1.0, 0.0, -1.0).normalized(),
			new Vec3(-1.0, 0.0, 1.0).normalized(),
			new Vec3(-1.0, 0.0, -1.0).normalized()
	};

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
			"gyro_blade_pass_alias_1024_hz",
			"gyro_blade_pass_alias_4000_hz",
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
			"battery_effective_resistance_ohm",
			"battery_resistance_aging_scale",
			"battery_equivalent_cycles",
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
			"min_induced_lag_thrust_scale",
			"rotor_translational_lift",
			"rotor_aerodynamic_load",
			"rotor_inflow_skew",
			"rotor_wake_interference",
			"rotor_0_wake_interference",
			"rotor_1_wake_interference",
			"rotor_2_wake_interference",
			"rotor_3_wake_interference",
			"rotor_wake_thrust_scale",
			"rotor_0_wake_thrust_scale",
			"rotor_1_wake_thrust_scale",
			"rotor_2_wake_thrust_scale",
			"rotor_3_wake_thrust_scale",
			"rotor_wet_thrust_scale",
			"rotor_0_wet_thrust_scale",
			"rotor_1_wet_thrust_scale",
			"rotor_2_wet_thrust_scale",
			"rotor_3_wet_thrust_scale",
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
			"tune_esc_dshot_bitrate_kbit_s",
			"tune_esc_dshot_raw_frame_us",
			"tune_esc_dshot_wire_utilization",
			"tune_esc_command_interval_raw_frame_ratio",
			"tune_rotor_blade_pitch_m",
			"tune_rotor_pitch_to_diameter",
			"tune_rotor_pitch_angle_70r_deg",
			"tune_rotor_chord_m",
			"tune_rotor_chord_to_radius",
			"tune_rotor_blade_count",
			"tune_rotor_imbalance",
			"airframe_lift_x_n",
			"airframe_lift_y_n",
			"airframe_lift_z_n",
			"airframe_lift_n",
			"airframe_body_drag_x_n",
			"airframe_body_drag_y_n",
			"airframe_body_drag_z_n",
			"airframe_body_drag_n",
			"linear_damping_drag_x_n",
			"linear_damping_drag_y_n",
			"linear_damping_drag_z_n",
			"linear_damping_drag_n",
			"ground_effect_drag_x_n",
			"ground_effect_drag_y_n",
			"ground_effect_drag_z_n",
			"ground_effect_drag_n",
			"ground_effect_leveling_pitch_torque_nm",
			"ground_effect_leveling_yaw_torque_nm",
			"ground_effect_leveling_roll_torque_nm",
			"ground_effect_leveling_torque_nm",
			"rotor_wash_drag_x_n",
			"rotor_wash_drag_y_n",
			"rotor_wash_drag_z_n",
			"rotor_wash_drag_n",
			"barometer_altitude_m",
			"barometer_vertical_speed_mps",
			"barometer_pressure_hpa",
			"barometer_error_m",
			"barometer_sensor_noise_m",
			"barometer_pressure_port_error_m",
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
			"contact_surface_friction",
			"contact_surface_restitution",
			"contact_surface_scrape",
			"contact_angular_impulse_pitch_dps",
			"contact_angular_impulse_yaw_dps",
			"contact_angular_impulse_roll_dps",
			"contact_angular_impulse_dps",
			"rotor_surface_scrape",
			"rotor_0_surface_scrape",
			"rotor_1_surface_scrape",
			"rotor_2_surface_scrape",
			"rotor_3_surface_scrape",
			"tune_motor_pole_pairs",
			"airframe_rotor_count",
			"avg_esc_electrical_output",
			"max_esc_electrical_error",
			"avg_esc_electrical_error",
			"esc_0_electrical_output",
			"esc_1_electrical_output",
			"esc_2_electrical_output",
			"esc_3_electrical_output",
			"esc_4_electrical_output",
			"esc_5_electrical_output",
			"esc_6_electrical_output",
			"esc_7_electrical_output",
			"esc_0_electrical_error",
			"esc_1_electrical_error",
			"esc_2_electrical_error",
			"esc_3_electrical_error",
			"esc_4_electrical_error",
			"esc_5_electrical_error",
			"esc_6_electrical_error",
			"esc_7_electrical_error",
			"avg_motor_erpm100",
			"motor_0_erpm100",
			"motor_1_erpm100",
			"motor_2_erpm100",
			"motor_3_erpm100",
			"avg_motor_einterval_us",
			"motor_0_einterval_us",
			"motor_1_einterval_us",
			"motor_2_einterval_us",
			"motor_3_einterval_us",
			"avg_motor_rpm_telemetry_valid",
			"motor_0_rpm_telemetry_valid",
			"motor_1_rpm_telemetry_valid",
			"motor_2_rpm_telemetry_valid",
			"motor_3_rpm_telemetry_valid",
			"avg_motor_target_erpm100",
			"motor_0_target_erpm100",
			"motor_1_target_erpm100",
			"motor_2_target_erpm100",
			"motor_3_target_erpm100",
			"avg_motor_target_einterval_us",
			"motor_0_target_einterval_us",
			"motor_1_target_einterval_us",
			"motor_2_target_einterval_us",
			"motor_3_target_einterval_us",
			"motor_4_output",
			"motor_5_output",
			"motor_6_output",
			"motor_7_output",
			"motor_4_rpm",
			"motor_5_rpm",
			"motor_6_rpm",
			"motor_7_rpm",
			"motor_4_erpm100",
			"motor_5_erpm100",
			"motor_6_erpm100",
			"motor_7_erpm100",
			"motor_4_einterval_us",
			"motor_5_einterval_us",
			"motor_6_einterval_us",
			"motor_7_einterval_us",
			"motor_4_rpm_telemetry_valid",
			"motor_5_rpm_telemetry_valid",
			"motor_6_rpm_telemetry_valid",
			"motor_7_rpm_telemetry_valid",
			"motor_4_target_rpm",
			"motor_5_target_rpm",
			"motor_6_target_rpm",
			"motor_7_target_rpm",
			"motor_4_target_erpm100",
			"motor_5_target_erpm100",
			"motor_6_target_erpm100",
			"motor_7_target_erpm100",
			"motor_4_target_einterval_us",
			"motor_5_target_einterval_us",
			"motor_6_target_einterval_us",
			"motor_7_target_einterval_us",
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
			"motor_regen_current_a",
			"motor_0_regen_current_a",
			"motor_1_regen_current_a",
			"motor_2_regen_current_a",
			"motor_3_regen_current_a",
			"motor_4_regen_current_a",
			"motor_5_regen_current_a",
			"motor_6_regen_current_a",
			"motor_7_regen_current_a",
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
			"battery_slow_polarization_v",
			"imu_supply_noise",
			"battery_temp_c",
			"battery_cooling_factor",
			"battery_thermal_limit",
			"battery_capacity_aging_scale",
			"battery_soc_resistance_scale",
			"battery_temp_resistance_scale",
			"battery_polarization_resistance_scale",
			"battery_20pct_sag_current_a",
			"battery_20pct_sag_current_margin",
			"rotor_4_thrust_n",
			"rotor_5_thrust_n",
			"rotor_6_thrust_n",
			"rotor_7_thrust_n",
			"rotor_0_health",
			"rotor_1_health",
			"rotor_2_health",
			"rotor_3_health",
			"rotor_4_health",
			"rotor_5_health",
			"rotor_6_health",
			"rotor_7_health",
			"rotor_damage_vibration",
			"rotor_0_damage_vibration",
			"rotor_1_damage_vibration",
			"rotor_2_damage_vibration",
			"rotor_3_damage_vibration",
			"rotor_4_damage_vibration",
			"rotor_5_damage_vibration",
			"rotor_6_damage_vibration",
			"rotor_7_damage_vibration",
			"rotor_4_surface_scrape",
			"rotor_5_surface_scrape",
			"rotor_6_surface_scrape",
			"rotor_7_surface_scrape",
			"rotor_4_wake_interference",
			"rotor_5_wake_interference",
			"rotor_6_wake_interference",
			"rotor_7_wake_interference",
			"rotor_4_wake_thrust_scale",
			"rotor_5_wake_thrust_scale",
			"rotor_6_wake_thrust_scale",
			"rotor_7_wake_thrust_scale",
			"rotor_4_wet_thrust_scale",
			"rotor_5_wet_thrust_scale",
			"rotor_6_wet_thrust_scale",
			"rotor_7_wet_thrust_scale",
			"rotor_wake_swirl_mps",
			"rotor_0_wake_swirl_mps",
			"rotor_1_wake_swirl_mps",
			"rotor_2_wake_swirl_mps",
			"rotor_3_wake_swirl_mps",
			"rotor_4_wake_swirl_mps",
			"rotor_5_wake_swirl_mps",
			"rotor_6_wake_swirl_mps",
			"rotor_7_wake_swirl_mps",
			"rotor_windmilling",
			"rotor_0_windmilling",
			"rotor_1_windmilling",
			"rotor_2_windmilling",
			"rotor_3_windmilling",
			"rotor_4_windmilling",
			"rotor_5_windmilling",
			"rotor_6_windmilling",
			"rotor_7_windmilling",
			"rotor_0_env_thrust_multiplier",
			"rotor_1_env_thrust_multiplier",
			"rotor_2_env_thrust_multiplier",
			"rotor_3_env_thrust_multiplier",
			"rotor_4_env_thrust_multiplier",
			"rotor_5_env_thrust_multiplier",
			"rotor_6_env_thrust_multiplier",
			"rotor_7_env_thrust_multiplier",
			"rotor_0_flow_obstruction",
			"rotor_1_flow_obstruction",
			"rotor_2_flow_obstruction",
			"rotor_3_flow_obstruction",
			"rotor_4_flow_obstruction",
			"rotor_5_flow_obstruction",
			"rotor_6_flow_obstruction",
			"rotor_7_flow_obstruction",
			"rotor_dynamic_inflow_tau_s",
			"rotor_0_dynamic_inflow_tau_s",
			"rotor_1_dynamic_inflow_tau_s",
			"rotor_2_dynamic_inflow_tau_s",
			"rotor_3_dynamic_inflow_tau_s",
			"rotor_4_dynamic_inflow_tau_s",
			"rotor_5_dynamic_inflow_tau_s",
			"rotor_6_dynamic_inflow_tau_s",
			"rotor_7_dynamic_inflow_tau_s",
			"rotor_advance_ratio",
			"rotor_0_advance_ratio",
			"rotor_1_advance_ratio",
			"rotor_2_advance_ratio",
			"rotor_3_advance_ratio",
			"rotor_4_advance_ratio",
			"rotor_5_advance_ratio",
			"rotor_6_advance_ratio",
			"rotor_7_advance_ratio",
			"rotor_prop_advance_ratio_j",
			"rotor_0_prop_advance_ratio_j",
			"rotor_1_prop_advance_ratio_j",
			"rotor_2_prop_advance_ratio_j",
			"rotor_3_prop_advance_ratio_j",
			"rotor_4_prop_advance_ratio_j",
			"rotor_5_prop_advance_ratio_j",
			"rotor_6_prop_advance_ratio_j",
			"rotor_7_prop_advance_ratio_j",
			"rotor_prop_thrust_scale",
			"rotor_0_prop_thrust_scale",
			"rotor_1_prop_thrust_scale",
			"rotor_2_prop_thrust_scale",
			"rotor_3_prop_thrust_scale",
			"rotor_4_prop_thrust_scale",
			"rotor_5_prop_thrust_scale",
			"rotor_6_prop_thrust_scale",
			"rotor_7_prop_thrust_scale",
			"rotor_prop_power_scale",
			"rotor_0_prop_power_scale",
			"rotor_1_prop_power_scale",
			"rotor_2_prop_power_scale",
			"rotor_3_prop_power_scale",
			"rotor_4_prop_power_scale",
			"rotor_5_prop_power_scale",
			"rotor_6_prop_power_scale",
			"rotor_7_prop_power_scale",
			"rotor_axial_gust_thrust_scale",
			"rotor_0_axial_gust_thrust_scale",
			"rotor_1_axial_gust_thrust_scale",
			"rotor_2_axial_gust_thrust_scale",
			"rotor_3_axial_gust_thrust_scale",
			"rotor_4_axial_gust_thrust_scale",
			"rotor_5_axial_gust_thrust_scale",
			"rotor_6_axial_gust_thrust_scale",
			"rotor_7_axial_gust_thrust_scale",
			"rotor_reverse_flow_fraction",
			"rotor_0_reverse_flow_fraction",
			"rotor_1_reverse_flow_fraction",
			"rotor_2_reverse_flow_fraction",
			"rotor_3_reverse_flow_fraction",
			"rotor_4_reverse_flow_fraction",
			"rotor_5_reverse_flow_fraction",
			"rotor_6_reverse_flow_fraction",
			"rotor_7_reverse_flow_fraction",
			"rotor_tip_mach",
			"rotor_0_tip_mach",
			"rotor_1_tip_mach",
			"rotor_2_tip_mach",
			"rotor_3_tip_mach",
			"rotor_4_tip_mach",
			"rotor_5_tip_mach",
			"rotor_6_tip_mach",
			"rotor_7_tip_mach",
			"rotor_compressibility_thrust_scale",
			"rotor_0_compressibility_thrust_scale",
			"rotor_1_compressibility_thrust_scale",
			"rotor_2_compressibility_thrust_scale",
			"rotor_3_compressibility_thrust_scale",
			"rotor_4_compressibility_thrust_scale",
			"rotor_5_compressibility_thrust_scale",
			"rotor_6_compressibility_thrust_scale",
			"rotor_7_compressibility_thrust_scale",
			"rotor_reynolds_number",
			"rotor_0_reynolds_number",
			"rotor_1_reynolds_number",
			"rotor_2_reynolds_number",
			"rotor_3_reynolds_number",
			"rotor_4_reynolds_number",
			"rotor_5_reynolds_number",
			"rotor_6_reynolds_number",
			"rotor_7_reynolds_number",
			"rotor_reynolds_index",
			"rotor_0_reynolds_index",
			"rotor_1_reynolds_index",
			"rotor_2_reynolds_index",
			"rotor_3_reynolds_index",
			"rotor_4_reynolds_index",
			"rotor_5_reynolds_index",
			"rotor_6_reynolds_index",
			"rotor_7_reynolds_index",
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
			"rotor_coning_angle_deg",
			"rotor_0_coning_angle_deg",
			"rotor_1_coning_angle_deg",
			"rotor_2_coning_angle_deg",
			"rotor_3_coning_angle_deg",
			"rotor_4_coning_angle_deg",
			"rotor_5_coning_angle_deg",
			"rotor_6_coning_angle_deg",
			"rotor_7_coning_angle_deg",
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
			"motor_winding_resistance_scale",
			"motor_0_winding_resistance_scale",
			"motor_1_winding_resistance_scale",
			"motor_2_winding_resistance_scale",
			"motor_3_winding_resistance_scale",
			"motor_4_winding_resistance_scale",
			"motor_5_winding_resistance_scale",
			"motor_6_winding_resistance_scale",
			"motor_7_winding_resistance_scale",
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
			"rotor_0_precipitation_wetness",
			"rotor_1_precipitation_wetness",
			"rotor_2_precipitation_wetness",
			"rotor_3_precipitation_wetness",
			"rotor_4_precipitation_wetness",
			"rotor_5_precipitation_wetness",
			"rotor_6_precipitation_wetness",
			"rotor_7_precipitation_wetness",
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
			"rotor_arm_flex_deflection_mm",
			"rotor_0_arm_flex_deflection_mm",
			"rotor_1_arm_flex_deflection_mm",
			"rotor_2_arm_flex_deflection_mm",
			"rotor_3_arm_flex_deflection_mm",
			"rotor_4_arm_flex_deflection_mm",
			"rotor_5_arm_flex_deflection_mm",
			"rotor_6_arm_flex_deflection_mm",
			"rotor_7_arm_flex_deflection_mm",
			"rotor_arm_flex_tilt_deg",
			"rotor_0_arm_flex_tilt_deg",
			"rotor_1_arm_flex_tilt_deg",
			"rotor_2_arm_flex_tilt_deg",
			"rotor_3_arm_flex_tilt_deg",
			"rotor_4_arm_flex_tilt_deg",
			"rotor_5_arm_flex_tilt_deg",
			"rotor_6_arm_flex_tilt_deg",
			"rotor_7_arm_flex_tilt_deg",
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
			"pid_integral_relax_roll",
			"rotor_in_plane_drag_force_n",
			"rotor_0_in_plane_drag_force_n",
			"rotor_1_in_plane_drag_force_n",
			"rotor_2_in_plane_drag_force_n",
			"rotor_3_in_plane_drag_force_n",
			"rotor_4_in_plane_drag_force_n",
			"rotor_5_in_plane_drag_force_n",
			"rotor_6_in_plane_drag_force_n",
			"rotor_7_in_plane_drag_force_n",
			"rotor_coaxial_load_bias",
			"rotor_0_coaxial_load_bias",
			"rotor_1_coaxial_load_bias",
			"rotor_2_coaxial_load_bias",
			"rotor_3_coaxial_load_bias",
			"rotor_4_coaxial_load_bias",
			"rotor_5_coaxial_load_bias",
			"rotor_6_coaxial_load_bias",
			"rotor_7_coaxial_load_bias",
			"rotor_coaxial_load_bias_target",
			"rotor_coaxial_load_bias_clipping",
			"rotor_coaxial_allocation_load",
			"rotor_coaxial_allocation_ratio",
			"rotor_coaxial_allocation_mech_gain_pct",
			"rotor_coaxial_allocation_elec_gain_pct",
			"rotor_coaxial_allocation_uncertainty_pct",
			"airframe_drag_along_flow_n",
			"airframe_drag_equivalent_linear_k",
			"airframe_drag_equivalent_cda_m2",
			"airframe_drag_imav_ratio",
			"gyro_notch_spread_hz",
			"gyro_rpm_harmonic_notch_attenuation",
			"gyro_blade_pass_notch_spread_hz",
			"vortex_ring_thrust_buffet",
			"vortex_ring_max_thrust_buffet",
			"vortex_ring_buffet_force_x_n",
			"vortex_ring_buffet_force_y_n",
			"vortex_ring_buffet_force_z_n",
			"vortex_ring_buffet_force_n",
			"wind_dryden_speed_mps",
			"wind_burble_speed_mps",
			"mqtb_hq5x4x3_current_a",
			"mqtb_hq5x4x3_power_w",
			"mqtb_hq5x4x3_current_ratio",
			"mqtb_hq5x4x3_current_residual_a",
			"apdrone_pdf5045_current_a",
			"apdrone_pdf5045_power_w",
			"apdrone_pdf5045_current_ratio",
			"apdrone_pdf5045_current_residual_a"
	);

	private OfflineFlightRecorder() {
	}

	public record BatteryAutonomyEstimate(
			String scenario,
			double throttleCommand,
			double lowerVoltageCutoff,
			double upperVoltageCutoff,
			double referenceDurationSeconds,
			double referenceMeanCurrentAmps,
			double simulatedDurationSeconds,
			double simulatedTimeInVoltageWindowSeconds,
			double startVoltage,
			double endVoltage,
			double minVoltage,
			double meanCurrentAmps,
			double peakCurrentAmps,
			double consumedAmpHours,
			double consumedWattHours,
			double currentMatchedDirectThrottleCommand,
			double currentMatchedMeanCurrentAmps,
			double currentMatchedPeakCurrentAmps
	) {
		public double durationRatio() {
			return simulatedDurationSeconds / Math.max(1.0e-9, referenceDurationSeconds);
		}

		public double meanCurrentRatio() {
			return meanCurrentAmps / Math.max(1.0e-9, referenceMeanCurrentAmps);
		}

		public double currentMatchedMeanCurrentRatio() {
			return currentMatchedMeanCurrentAmps / Math.max(1.0e-9, referenceMeanCurrentAmps);
		}

		public double referenceThrottleToCurrentMatchedDirectThrottleRatio() {
			return throttleCommand / Math.max(1.0e-9, currentMatchedDirectThrottleCommand);
		}
	}

	public record BatteryVoltageDropAudit(
			double configuredResistanceOhms,
			double normalMeanCurrentAmps,
			double normalP95CurrentAmps,
			double maxMeanCurrentAmps,
			double maxP95CurrentAmps,
			double deltaCurrentAmps,
			double normalStartVoltage,
			double maxStartVoltage,
			double normalMeanVoltage,
			double maxMeanVoltage,
			double observedMeanVoltageDelta,
			double normalConfiguredSagAtMeanCurrent,
			double maxConfiguredSagAtMeanCurrent,
			double configuredSagDelta,
			double normalConfiguredSagAtP95Current,
			double maxConfiguredSagAtP95Current,
			double inferredResistanceProxyOhms,
			double configuredOverInferredProxy,
			double observedMeanVoltageDeltaOverConfiguredSagDelta,
			double normalStartDropResistanceProxyOhms,
			double maxStartDropResistanceProxyOhms,
			double normalConfiguredOverStartDropProxy,
			double maxConfiguredOverStartDropProxy
	) {
		public double configuredResistanceMilliohms() {
			return configuredResistanceOhms * 1000.0;
		}

		public double inferredResistanceProxyMilliohms() {
			return inferredResistanceProxyOhms * 1000.0;
		}

		public double normalStartDropResistanceProxyMilliohms() {
			return normalStartDropResistanceProxyOhms * 1000.0;
		}

		public double maxStartDropResistanceProxyMilliohms() {
			return maxStartDropResistanceProxyOhms * 1000.0;
		}
	}

	public record ReferenceSpeedEnvelopeEstimate(
			String speedPoint,
			double referenceSpeedMetersPerSecond,
			AirframeDragCalibration.Axis axis,
			double dragLimitedLevelSpeedMetersPerSecond,
			double speedOverDragLimitedLevelSpeed,
			double baseDragForceNewtons,
			double horizontalThrustMarginNewtons,
			double residualHorizontalMarginNewtons,
			double dragOverHorizontalMargin,
			double requiredMaxThrustFraction,
			double requiredTiltDegrees,
			boolean reachable
	) {
	}

	public record PropDamageVibrationAudit(
			int damagedRotorIndex,
			double rotorDamageAmount,
			int sampleCount,
			double sampledSeconds,
			double healthyGyroDynamicRmsRadiansPerSecond,
			double damagedGyroDynamicRmsRadiansPerSecond,
			double gyroDynamicRmsRatio,
			double healthyAccelerometerDynamicRmsMetersPerSecondSquared,
			double damagedAccelerometerDynamicRmsMetersPerSecondSquared,
			double accelerometerDynamicRmsRatio,
			double maxHealthyRotorVibration,
			double maxDamagedRotorVibration,
			double maxDamagedRotorDamageVibration,
			double referenceSingleBrokenGyroRmsRatio,
			double referenceSingleBrokenAccelerometerRmsRatio,
			double padreSingleRotorAccelerometerFeatureRmsRatio,
			double padreTwoPositionAccelerometerFeatureRmsRatio
	) {
	}

	private record StaticBatteryLoad(
			double throttleCommand,
			double meanCurrentAmps,
			double peakCurrentAmps
	) {
	}

	private record DamageSensorRms(
			int sampleCount,
			double sampledSeconds,
			double gyroDynamicRmsRadiansPerSecond,
			double accelerometerDynamicRmsMetersPerSecondSquared,
			double maxRotorVibration,
			double maxRotorDamageVibration
	) {
	}

	private static final class VectorRmsAccumulator {
		private int samples;
		private Vec3 sum = Vec3.ZERO;
		private double sumLengthSquared;

		private void add(Vec3 value) {
			if (value == null || !value.isFinite()) {
				return;
			}

			samples++;
			sum = sum.add(value);
			sumLengthSquared += value.lengthSquared();
		}

		private int samples() {
			return samples;
		}

		private double dynamicRms() {
			if (samples <= 0) {
				return 0.0;
			}

			Vec3 mean = sum.multiply(1.0 / samples);
			double meanSquare = sumLengthSquared / samples;
			return Math.sqrt(Math.max(0.0, meanSquare - mean.lengthSquared()));
		}
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
		DroneConfig preset = preset(presetName);
		AirframeDragCalibration.Coastdown lateralCoastdown = AirframeDragCalibration.coastdown(
				preset,
				AirframeDragCalibration.Axis.X,
				20.0,
				5.0
		);
		AirframeDragCalibration.Coastdown forwardCoastdown = AirframeDragCalibration.coastdown(
				preset,
				AirframeDragCalibration.Axis.Z,
				20.0,
				5.0
		);
		AirframeDragCalibration.BodyDragFit lateralImavFit =
				AirframeDragCalibration.fitBodyQuadraticCoefficientToImav2022Reference(
						preset,
						AirframeDragCalibration.Axis.X,
						20.0,
						5.0
				);
		AirframeDragCalibration.BodyDragFit forwardImavFit =
				AirframeDragCalibration.fitBodyQuadraticCoefficientToImav2022Reference(
						preset,
						AirframeDragCalibration.Axis.Z,
						20.0,
						5.0
				);
		AirframeDragCalibration.LevelFlightRequirement aiioManualHigh =
				AirframeDragCalibration.worstHorizontalLevelFlightRequirement(preset, 14.0, 1.0);
		AirframeDragCalibration.LevelFlightRequirement ratmHighSpeed =
				AirframeDragCalibration.worstHorizontalLevelFlightRequirement(preset, 21.0, 1.0);
		AirframeDragCalibration.LevelFlightRequirement uzhFpvVmax =
				AirframeDragCalibration.worstHorizontalLevelFlightRequirement(preset, 26.79, 1.0);
		AirframeDragCalibration.RatmHighSpeedEnvelopeAudit ratmEnvelopeAudit =
				AirframeDragCalibration.ratmHighSpeedEnvelopeAudit(preset);
		NeuroBemAirframeResidualCalibration.NeuroBemAirframeResidualAudit neuroBemResidualAudit =
				NeuroBemAirframeResidualCalibration.audit(preset);
		FpvLipoEsrCalibration.FpvLipoEsrAudit lipoEsrAudit =
				FpvLipoEsrCalibration.audit(preset);
		RotorDynamicsCalibration.RotorDynamicsAudit rotorDynamicsAudit =
				RotorDynamicsCalibration.audit(preset);
		HighAdvanceRotorCalibration.HighAdvanceAudit highAdvanceAudit =
				HighAdvanceRotorCalibration.audit(preset);
		PropGeometryCalibration.PropGeometryAudit propGeometryAudit =
				PropGeometryCalibration.audit(preset);
		PrecipitationWaterCalibration.PrecipitationWaterAudit precipitationWaterAudit =
				PrecipitationWaterCalibration.audit(preset);
		IcingRotorCalibration.IcingRotorAudit icingRotorAudit =
				IcingRotorCalibration.audit();
		WindGustCalibration.WindGustAudit windGustAudit =
				WindGustCalibration.audit();
		VrsPropwashCalibration.VrsPropwashAudit vrsPropwashAudit =
				VrsPropwashCalibration.audit(preset);
		SurfaceNearfieldCalibration.SurfaceNearfieldAudit surfaceNearfieldAudit =
				SurfaceNearfieldCalibration.audit(preset);
		CoaxialAllocationCalibration.CoaxialAllocationAudit coaxialAllocationAudit =
				CoaxialAllocationCalibration.audit(DroneConfig.coaxialX8());
		AirframeInertiaCalibration.ApDroneInertiaAudit apDroneInertiaAudit =
				AirframeInertiaCalibration.apDroneInertiaAudit(preset);
		MotorBenchCurrentModel.StaticPowertrainAudit tytoAudit =
				MotorBenchCurrentModel.tytoX3nmStaticPowertrainAudit(preset);
		MotorBenchCurrentModel.StaticYawTorqueAudit tytoYawTorqueAudit =
				MotorBenchCurrentModel.tytoStaticYawTorqueAudit(preset);
		MotorBenchCurrentModel.ApDroneMotorSpecAudit apDroneMotorAudit =
				MotorBenchCurrentModel.apDroneMotorSpecAudit(preset);
		MotorBenchCurrentModel.FoxeerDonut5145PropAudit foxeerPropAudit =
				MotorBenchCurrentModel.foxeerDonut5145PropAudit(preset);
		MotorBenchCurrentModel.RotorSpeedTelemetryAudit aiioRotorSpeedAudit =
				MotorBenchCurrentModel.aiioRotorSpeedTelemetryAudit(preset);
		MotorBenchCurrentModel.ApDroneUrbanMotorRpmAudit apDroneUrbanMotorRpmAudit =
				MotorBenchCurrentModel.apDroneUrbanMotorRpmAudit(preset);
		MotorThermalCalibration.MotorThermalAudit motorThermalAudit =
				MotorThermalCalibration.audit(preset);
		NanodroneSysIdCalibration.NanodroneSysIdAudit nanodroneSysIdAudit =
				NanodroneSysIdCalibration.audit(preset);
		MotorResponseCalibration.MotorResponseAudit motorResponseAudit =
				MotorResponseCalibration.audit(preset);
		ControlResponseCalibration.ControlResponseAudit controlResponseAudit =
				ControlResponseCalibration.apDroneControlResponseAudit(preset);
		PidTuningCalibration.ApDronePidTuningAudit pidTuningAudit =
				PidTuningCalibration.apDronePidTuningAudit();
		RateEnvelopeCalibration.RateEnvelopeAudit rateEnvelopeAudit =
				RateEnvelopeCalibration.apDroneRateEnvelopeAudit(preset);
		SensorNoiseCalibration.ImuNoiseAudit imuNoiseAudit =
				SensorNoiseCalibration.apDroneImuNoiseAudit(preset);
		SensorNoiseCalibration.BarometerNoiseAudit barometerNoiseAudit =
				SensorNoiseCalibration.apDroneBarometerNoiseAudit(preset);
		PropDamageVibrationAudit propDamageAudit = propDamageVibrationAudit(preset);

		System.out.printf(Locale.ROOT, "Wrote %d samples to %s%n", report.samples(), outputPath.toAbsolutePath());
		System.out.printf(
				Locale.ROOT,
				"Summary: max_speed=%.2f m/s, max_current=%.1f A, max_regen=%.1f A, max_motor_regen=%.3f A, min_voltage=%.2f V, max_sag=%.2f V, max_ir=%.1f mOhm, max_irx=%.2f/%.2f/%.2f, max_spike=%.4f V, max_ripple=%.4f V, max_imu_power_noise=%.3f, max_erpm100=%.1f/%.1f, min_eint=%.1f/%.1f us, max_rpm_valid=%.2f/%.2f, notch=%.1fHz/%.3f, notch_spread=%.1fHz, hnotch=%.3f, bpass_notch=%.1fHz/%.3f, bpass_spread=%.1fHz, max_batt=%.1f C, batt_limit=%.2f, max_propwash=%.3f, max_vrs=%.3f, max_vrs_buffet=%.3f, max_vrs_buffet_force=%.3f N, max_induced=%.2f m/s, max_inflow_lag=%.1f%%, max_dynamic_inflow_tau=%.3f s, max_rotor_adv=%.3f, max_prop_j=%.3f, min_prop_thrust=%.3f, min_prop_power=%.3f, axial_gust=%.2f..%.2f, max_reverse_flow=%.3f, max_tip_mach=%.3f, max_mach_loss=%.1f%%, max_low_re=%.3f, max_bpass=%.3f, max_hforce=%.3f N, max_coax_bias=%.3f, max_coax_target=%.3f, max_coax_clip=%.3f, max_coax_load=%.2f, max_coax_ratio=%.2f, max_coax_gain=%.1f/%.1f%%, max_coax_unc=%.1f%%, max_wet_loss=%.1f%%, max_bdiss_torque=%.4f N-m, max_wake_swirl=%.2f m/s, max_windmill=%.3f, max_wake_swirl_torque=%.4f N-m, max_active_brake_torque=%.4f N-m, max_rotor_accel_torque=%.4f N-m, max_rotor_gyro_torque=%.4f N-m, max_flap_torque=%.4f N-m, min_motor_eff=%.3f, min_motor_headroom=%.3f, max_motor_winding_r=%.3f, max_track=%.3f, min_auth=%.2f, min_mix_axis=%.2f, max_rotor_stall=%.3f, max_damage_vib=%.3f, max_airframe_sep=%.3f, max_body_drag=%.3f N, max_linear_drag=%.3f N, max_ground_level=%.4f N-m, max_coning=%.3f, max_coning_angle=%.2f deg, max_arm_flex=%.3f, max_arm_flex_mm=%.2f, max_arm_flex_tilt=%.2f deg, max_scrape=%.3f, max_gust=%.2f m/s, max_dryden=%.2f m/s, max_burble=%.2f m/s, max_shear=%.2f m/s2, max_wall=%.3f N, max_contact=%.2f/%.2f/%.2f m/s, max_contact_ang=%.0f d/s, max_aero_torque=%.4f N-m, max_baro_error=%.3f m, max_baro_port=%.3f m, max_baro_wash=%.3f m, max_esc=%.1f C, esc_limit=%.2f%n",
				report.maxSpeedMetersPerSecond(),
				report.maxBatteryCurrentAmps(),
				report.maxBatteryRegenerativeCurrentAmps(),
				report.maxMotorRegenerativeCurrentAmps(),
				report.minBatteryVoltage(),
				report.maxBatterySagVoltage(),
				report.maxBatteryEffectiveResistanceOhms() * 1000.0,
				report.maxBatteryStateOfChargeResistanceScale(),
				report.maxBatteryTemperatureResistanceScale(),
				report.maxBatteryPolarizationResistanceScale(),
				report.maxBatteryVoltageSpike(),
				report.maxBatteryBusRippleVoltage(),
				report.maxImuSupplyNoiseIntensity(),
				report.maxAverageMotorTelemetryErpm100(),
				report.maxMotorTelemetryErpm100(),
				report.minAverageMotorTelemetryEIntervalMicros(),
				report.minMotorTelemetryEIntervalMicros(),
				report.maxAverageMotorRpmTelemetryValidity(),
				report.maxMotorRpmTelemetryValidity(),
				report.maxGyroNotchFrequencyHertz(),
				report.maxGyroNotchAttenuation(),
				report.maxGyroNotchSpreadHertz(),
				report.maxGyroRpmHarmonicNotchAttenuation(),
				report.maxGyroBladePassNotchFrequencyHertz(),
				report.maxGyroBladePassNotchAttenuation(),
				report.maxGyroBladePassNotchSpreadHertz(),
				report.maxBatteryTemperatureCelsius(),
				report.minBatteryThermalLimit(),
				report.maxPropwashIntensity(),
				report.maxVortexRingStateIntensity(),
				report.maxVortexRingThrustBuffetAmplitude(),
				report.maxVortexRingBuffetForceNewtons(),
				report.maxRotorInducedVelocityMetersPerSecond(),
				report.maxRotorInducedLagThrustLossPercent(),
				report.maxRotorDynamicInflowTimeConstantSeconds(),
				report.maxRotorAdvanceRatio(),
				report.maxRotorPropellerAdvanceRatioJ(),
				report.minRotorPropellerThrustScale(),
				report.minRotorPropellerPowerScale(),
				report.minRotorAxialGustThrustScale(),
				report.maxRotorAxialGustThrustScale(),
				report.maxRotorReverseFlowInboardFraction(),
				report.maxRotorTipMach(),
				report.maxRotorCompressibilityThrustLossPercent(),
				report.maxRotorLowReynoldsLoss(),
				report.maxRotorBladePassRippleIntensity(),
				report.maxRotorInPlaneDragForceNewtons(),
				report.maxRotorCoaxialLoadBias(),
				report.maxRotorCoaxialLoadBiasTarget(),
				report.maxRotorCoaxialLoadBiasClipping(),
				report.maxRotorCoaxialAllocationLoadFraction(),
				report.maxRotorCoaxialAllocationCommandRatio(),
				report.maxRotorCoaxialAllocationMechanicalGainPercent(),
				report.maxRotorCoaxialAllocationElectricalGainPercent(),
				report.maxRotorCoaxialAllocationUncertaintyPercent(),
				report.maxRotorWetThrustLossPercent(),
				report.maxRotorBladeDissymmetryTorqueNewtonMeters(),
				report.maxRotorWakeSwirlVelocityMetersPerSecond(),
				report.maxRotorWindmillingIntensity(),
				report.maxRotorWakeSwirlTorqueNewtonMeters(),
				report.maxRotorActiveBrakingTorqueNewtonMeters(),
				report.maxRotorAccelerationReactionTorqueNewtonMeters(),
				report.maxRotorGyroscopicTorqueNewtonMeters(),
				report.maxRotorFlappingTorqueNewtonMeters(),
				report.minMotorElectricalEfficiency(),
				report.minMotorVoltageHeadroom(),
				report.maxMotorWindingResistanceScale(),
				report.maxMotorTrackingError(),
				report.minMotorActuatorAuthority(),
				report.minMixerAxisAuthority(),
				report.maxRotorStallIntensity(),
				report.maxRotorDamageVibration(),
				report.maxAirframeSeparatedFlowIntensity(),
				report.maxAirframeBodyDragForceNewtons(),
				report.maxLinearDampingDragForceNewtons(),
				report.maxGroundEffectLevelingTorqueNewtonMeters(),
				report.maxRotorConingIntensity(),
				Math.toDegrees(report.maxRotorConingAngleRadians()),
				report.maxRotorArmFlexIntensity(),
				report.maxRotorArmFlexDeflectionMeters() * 1000.0,
				Math.toDegrees(report.maxRotorArmFlexTiltRadians()),
				report.maxRotorSurfaceScrapeIntensity(),
				report.maxWindGustSpeedMetersPerSecond(),
				report.maxWindDrydenSpeedMetersPerSecond(),
				report.maxWindBurbleSpeedMetersPerSecond(),
				report.maxWindShearAccelerationMetersPerSecondSquared(),
				report.maxRotorWallEffectForceNewtons(),
				report.maxContactImpactSpeedMetersPerSecond(),
				report.maxContactSlipSpeedMetersPerSecond(),
				report.maxContactBounceSpeedMetersPerSecond(),
				report.maxContactAngularImpulseDegreesPerSecond(),
				report.maxAirframeTorqueNewtonMeters(),
				report.maxBarometerErrorMeters(),
				report.maxBarometerPressurePortErrorMeters(),
				report.maxBarometerPropwashErrorMeters(),
				report.maxEscTemperatureCelsius(),
				report.minEscThermalLimit()
		);
		System.out.printf(
				Locale.ROOT,
				"Airframe coastdown 20->5 m/s: X %.2f s/%.1f m (%.0f%%/%.0f%% IMAV), Z %.2f s/%.1f m (%.0f%%/%.0f%% IMAV)%n",
				lateralCoastdown.timeSeconds(),
				lateralCoastdown.distanceMeters(),
				lateralCoastdown.timeRatioToReference() * 100.0,
				lateralCoastdown.distanceRatioToReference() * 100.0,
				forwardCoastdown.timeSeconds(),
				forwardCoastdown.distanceMeters(),
				forwardCoastdown.timeRatioToReference() * 100.0,
				forwardCoastdown.distanceRatioToReference() * 100.0
		);
		System.out.printf(
				Locale.ROOT,
				"Airframe IMAV body-drag fit 20->5 m/s: X %.5f (%s), Z %.5f (%s)%n",
				lateralImavFit.bodyQuadraticCoefficient(),
				lateralImavFit.targetReachable() ? "reachable" : "linear-high",
				forwardImavFit.bodyQuadraticCoefficient(),
				forwardImavFit.targetReachable() ? "reachable" : "linear-high"
		);
		System.out.printf(
				Locale.ROOT,
				"Airframe base-drag level-flight envelope: %s; %s; %s%n",
				formatLevelFlightRequirement("AI-IO14", aiioManualHigh),
				formatLevelFlightRequirement("RATM21", ratmHighSpeed),
				formatLevelFlightRequirement("UZH26.8", uzhFpvVmax)
		);
		System.out.printf(
				Locale.ROOT,
				"RATM high-speed audit: %s flights %d (%d auto/%d pilot) rows %d@%.0fHz >=%.0fm/s %d(%.1f%%) vmax %.2fm/s p99 %.2fm/s member %s req %.2f/%.2f/%.2fmax drag %.1f/%.1f/%.1f%%margin limit %.1fm/s speed/limit %.2f/%.2f kv %.2fx esc %.2fx radius %.2fx vbat_min %.1fV%n",
				ratmEnvelopeAudit.sourceId(),
				ratmEnvelopeAudit.totalFlightCount(),
				ratmEnvelopeAudit.autonomousFlightCount(),
				ratmEnvelopeAudit.pilotedFlightCount(),
				ratmEnvelopeAudit.totalSampleRowCount(),
				ratmEnvelopeAudit.sampleRateHertz(),
				ratmEnvelopeAudit.readmeSpeedFloorMetersPerSecond(),
				ratmEnvelopeAudit.flightCountAtOrAboveReadmeFloor(),
				ratmEnvelopeAudit.flightFractionAtOrAboveReadmeFloor() * 100.0,
				ratmEnvelopeAudit.fastestSpeedMetersPerSecond(),
				ratmEnvelopeAudit.fastestP99SpeedMetersPerSecond(),
				ratmEnvelopeAudit.fastestMemberPath(),
				ratmEnvelopeAudit.readmeFloorRequirement().requiredMaxThrustFraction(),
				ratmEnvelopeAudit.fastestRequirement().requiredMaxThrustFraction(),
				ratmEnvelopeAudit.p99Requirement().requiredMaxThrustFraction(),
				ratmEnvelopeAudit.readmeFloorRequirement().dragToHorizontalMarginRatio() * 100.0,
				ratmEnvelopeAudit.fastestRequirement().dragToHorizontalMarginRatio() * 100.0,
				ratmEnvelopeAudit.p99Requirement().dragToHorizontalMarginRatio() * 100.0,
				ratmEnvelopeAudit.slowestHorizontalDragLimitedLevelSpeedMetersPerSecond(),
				ratmEnvelopeAudit.fastestSpeedOverDragLimitedLevelSpeed(),
				ratmEnvelopeAudit.p99SpeedOverDragLimitedLevelSpeed(),
				ratmEnvelopeAudit.configuredMaxRpmOverRatmKvAtNominalVoltage(),
				ratmEnvelopeAudit.configuredPerMotorCurrentOverRatmEscCurrent(),
				ratmEnvelopeAudit.configuredRotorRadiusOverRatmPropRadius(),
				ratmEnvelopeAudit.minimumBatteryVoltageAcrossGroup()
		);
		System.out.printf(
				Locale.ROOT,
				"NeuroBEM residual audit: %s rows %d files %d raw %d %.1fmin 0.772kg speed p50/p95/max %.2f/%.2f/%.2fm/s residual p50/p95 %.2f/%.2fN %.1f%%W drag_like p95 %.2fN eqC95 %.5f, preset X/Z 10m %.2f/%.2fN p95v %.2f/%.2fN over_resid %.2f/%.2fx over_draglike %.2f/%.2fx bins trim %.1fm/s %.2f/%.2fN fast %.1fm/s %.2f/%.2fN caveat low-speed-residual-not-wind-tunnel-drag%n",
				neuroBemResidualAudit.sourceId(),
				neuroBemResidualAudit.packetMetricRowCount(),
				neuroBemResidualAudit.globalEnvelope().predictionCsvFileCount(),
				neuroBemResidualAudit.globalEnvelope().rawSampleRowCount(),
				neuroBemResidualAudit.globalEnvelope().totalDurationMinutes(),
				neuroBemResidualAudit.globalEnvelope().bodySpeedSampleP50MetersPerSecond(),
				neuroBemResidualAudit.globalEnvelope().bodySpeedSampleP95MetersPerSecond(),
				neuroBemResidualAudit.globalEnvelope().bodySpeedMaxMetersPerSecond(),
				neuroBemResidualAudit.globalEnvelope().residualForceSampleP50Newtons(),
				neuroBemResidualAudit.globalEnvelope().residualForceSampleP95Newtons(),
				neuroBemResidualAudit.globalEnvelope().residualForceSampleP95OverWeight() * 100.0,
				neuroBemResidualAudit.globalEnvelope().dragLikeForceSampleP95Newtons(),
				neuroBemResidualAudit.globalEnvelope().equivalentQuadCoeffSampleP95(),
				neuroBemResidualAudit.lateralAxisComparison().dragAtTenMetersPerSecondNewtons(),
				neuroBemResidualAudit.forwardAxisComparison().dragAtTenMetersPerSecondNewtons(),
				neuroBemResidualAudit.lateralAxisComparison().dragAtNeuroBemP95SpeedNewtons(),
				neuroBemResidualAudit.forwardAxisComparison().dragAtNeuroBemP95SpeedNewtons(),
				neuroBemResidualAudit.lateralAxisComparison().dragAtNeuroBemP95SpeedOverNeuroBemResidualP95(),
				neuroBemResidualAudit.forwardAxisComparison().dragAtNeuroBemP95SpeedOverNeuroBemResidualP95(),
				neuroBemResidualAudit.lateralAxisComparison().dragAtNeuroBemP95SpeedOverNeuroBemDragLikeP95(),
				neuroBemResidualAudit.forwardAxisComparison().dragAtNeuroBemP95SpeedOverNeuroBemDragLikeP95(),
				neuroBemResidualAudit.maneuverSpeedBin().speedSampleP50MetersPerSecond(),
				neuroBemResidualAudit.maneuverSpeedBin().currentXDragAtP50SpeedNewtons(),
				neuroBemResidualAudit.maneuverSpeedBin().currentZDragAtP50SpeedNewtons(),
				neuroBemResidualAudit.fastPacketSpeedBin().speedSampleP50MetersPerSecond(),
				neuroBemResidualAudit.fastPacketSpeedBin().currentXDragAtP50SpeedNewtons(),
				neuroBemResidualAudit.fastPacketSpeedBin().currentZDragAtP50SpeedNewtons()
		);
		System.out.printf(
				Locale.ROOT,
				"FPV LiPo ESR audit: %s rows %d anchors %d measured_cell %.3f/%.3f/%.3fmOhm measured_pack %.1f..%.1fmOhm, preset %dS %.2fAh %.0fA %.1fC %.3fmOhm/cell pack %.1fmOhm sag %.2fV %.1f%%, ir_formula %.1fA cfg %.2fx cold10 %.2fx, mendeley fresh/worn pack %.1f/%.1fmOhm sag %.2f/%.2fV 20pct %.0f/%.0fA, temp0 %.2fx/%.2fx sag %.2fV temp70 %.2fx/%.2fx sag %.2fV jeffco %.2f..%.2fx model %.2f..%.2fx caveat absolute-fpv-ir-vs-shape-priors%n",
				lipoEsrAudit.sourceId(),
				lipoEsrAudit.packetMetricRowCount(),
				lipoEsrAudit.measuredRange().measuredPackCount(),
				lipoEsrAudit.measuredRange().measuredPerCellIrMinMilliohms(),
				lipoEsrAudit.measuredRange().measuredPerCellIrMedianMilliohms(),
				lipoEsrAudit.measuredRange().measuredPerCellIrMaxMilliohms(),
				lipoEsrAudit.measuredRange().measuredPackIrMinMilliohms(),
				lipoEsrAudit.measuredRange().measuredPackIrMaxMilliohms(),
				lipoEsrAudit.configuredPack().cells(),
				lipoEsrAudit.configuredPack().capacityAmpHours(),
				lipoEsrAudit.configuredPack().maxCurrentAmps(),
				lipoEsrAudit.configuredPack().currentLimitC(),
				lipoEsrAudit.configuredPack().perCellIrMilliohms(),
				lipoEsrAudit.configuredPack().packResistanceOhms() * 1000.0,
				lipoEsrAudit.configuredPack().sagAtCurrentLimitVolts(),
				lipoEsrAudit.configuredPack().sagAtCurrentLimitPercentNominal(),
				lipoEsrAudit.formulaGuardrail().irFormulaTrueCurrentAmps(),
				lipoEsrAudit.formulaGuardrail().configuredCurrentOverIrFormulaCurrent(),
				lipoEsrAudit.formulaGuardrail().configuredCurrentOverColdTenCdropCurrent(),
				lipoEsrAudit.freshFullProjection().projectedPackResistanceOhms() * 1000.0,
				lipoEsrAudit.wornTenPercentProjection().projectedPackResistanceOhms() * 1000.0,
				lipoEsrAudit.freshFullProjection().configCurrentSagVolts(),
				lipoEsrAudit.wornTenPercentProjection().configCurrentSagVolts(),
				lipoEsrAudit.freshFullProjection().configCurrentForTwentyPercentNominalSagAmps(),
				lipoEsrAudit.wornTenPercentProjection().configCurrentForTwentyPercentNominalSagAmps(),
				lipoEsrAudit.coldZeroCelsius().resistanceScale(),
				lipoEsrAudit.coldZeroCelsius().currentScale(),
				lipoEsrAudit.coldZeroCelsius().sagAtTemperatureScaledLimitVolts(),
				lipoEsrAudit.hotSeventyCelsius().resistanceScale(),
				lipoEsrAudit.hotSeventyCelsius().currentScale(),
				lipoEsrAudit.hotSeventyCelsius().sagAtTemperatureScaledLimitVolts(),
				lipoEsrAudit.temperatureReference().jeffcoReferenceColdOverWarmIrMin(),
				lipoEsrAudit.temperatureReference().jeffcoReferenceColdOverWarmIrMax(),
				lipoEsrAudit.temperatureReference().currentModelOverJeffcoMin(),
				lipoEsrAudit.temperatureReference().currentModelOverJeffcoMax()
		);
		System.out.printf(
				Locale.ROOT,
				"Rotor dynamics audit: %s rows %d inertia/inflow/flex %d/%d/%d refs %d, rotor %.2fin P/D %.3f blades %d I %.3e ref %s %.2fx/%.2fx/%.2fx mass %.2fg %.2fx, rpm hover/max %.0f/%.0f L %.5f/%.5f gyro %.3f/%.3fNm spinup %.3f/%.3fNm, inflow tau %.1fms %.1fxPX4 vi %.2f/%.2fm/s transit %.1f/%.1f/%.1fms dyn %.1f/%.1f/%.1f/%.1fms, coning target %.3f angle %.2fdeg thrust %.3f vib %.3f freq %.1fHz ref %.2f/%.2fdeg, arm flex %.3f/%.3f/%.3f defl %.2f/%.2f/%.2fmm tilt %.2f/%.2f/%.2fdeg beam %s %.2fmm %.2fxfreq caveat runtime-helpers%n",
				rotorDynamicsAudit.sourceId(),
				rotorDynamicsAudit.packetMetricRowCount(),
				rotorDynamicsAudit.rotorInertiaRowCount(),
				rotorDynamicsAudit.rotorInflowRowCount(),
				rotorDynamicsAudit.armFlexConingRowCount(),
				rotorDynamicsAudit.physicalPropReferenceCount(),
				rotorDynamicsAudit.inertia().configuredDiameterInches(),
				rotorDynamicsAudit.inertia().configuredPitchToDiameterRatio(),
				rotorDynamicsAudit.inertia().configuredBladeCount(),
				rotorDynamicsAudit.inertia().configuredRotorInertiaKgMetersSquared(),
				rotorDynamicsAudit.inertia().nearestPhysicalReference().propellerId(),
				rotorDynamicsAudit.inertia().configuredOverReferenceHubBiasedInertia(),
				rotorDynamicsAudit.inertia().configuredOverReferenceUniformBladeInertia(),
				rotorDynamicsAudit.inertia().configuredOverReferenceTipBiasedInertia(),
				rotorDynamicsAudit.inertia().configuredEquivalentUniformBladeMassGrams(),
				rotorDynamicsAudit.inertia().configuredEquivalentUniformBladeMassOverReferenceMass(),
				rotorDynamicsAudit.inertia().hoverRpm(),
				rotorDynamicsAudit.inertia().maxRpm(),
				rotorDynamicsAudit.inertia().hoverAngularMomentumNewtonMeterSeconds(),
				rotorDynamicsAudit.inertia().maxAngularMomentumNewtonMeterSeconds(),
				rotorDynamicsAudit.inertia().hoverGyroTorquePerRotorNewtonMeters(),
				rotorDynamicsAudit.inertia().maxGyroTorquePerRotorNewtonMeters(),
				rotorDynamicsAudit.inertia().motorTauSpinupReactionTorqueNewtonMeters(),
				rotorDynamicsAudit.inertia().fiftyMillisecondSpinupReactionTorqueNewtonMeters(),
				rotorDynamicsAudit.dynamicInflow().configuredInflowTimeConstantSeconds() * 1000.0,
				rotorDynamicsAudit.dynamicInflow().configuredTauOverReferenceUp(),
				rotorDynamicsAudit.dynamicInflow().hoverInducedVelocityMetersPerSecond(),
				rotorDynamicsAudit.dynamicInflow().maxInducedVelocityMetersPerSecond(),
				rotorDynamicsAudit.dynamicInflow().wakeTransitOneRadiusHoverSeconds() * 1000.0,
				rotorDynamicsAudit.dynamicInflow().wakeTransitTwoRadiusHoverSeconds() * 1000.0,
				rotorDynamicsAudit.dynamicInflow().wakeTransitOneRadiusMaxSeconds() * 1000.0,
				rotorDynamicsAudit.dynamicInflow().runtimeHoverDynamicTauSeconds() * 1000.0,
				rotorDynamicsAudit.dynamicInflow().runtimeHighThrustDynamicTauSeconds() * 1000.0,
				rotorDynamicsAudit.dynamicInflow().runtimeFastCrossflowDynamicTauSeconds() * 1000.0,
				rotorDynamicsAudit.dynamicInflow().runtimeFastDescentDynamicTauSeconds() * 1000.0,
				rotorDynamicsAudit.coning().maxTargetIntensity(),
				rotorDynamicsAudit.coning().maxConingAngleDegrees(),
				rotorDynamicsAudit.coning().maxConingThrustScale(),
				rotorDynamicsAudit.coning().maxConingVibration(),
				rotorDynamicsAudit.coning().maxConingNaturalFrequencyHertz(),
				rotorDynamicsAudit.coning().djiPhantom8500RpmConingDegrees(),
				rotorDynamicsAudit.coning().tmotor15x5_5000RpmConingDegrees(),
				rotorDynamicsAudit.armFlex().hoverTargetIntensity(),
				rotorDynamicsAudit.armFlex().maxSteadyTargetIntensity(),
				rotorDynamicsAudit.armFlex().maxSnapTargetIntensity(),
				rotorDynamicsAudit.armFlex().fullFlexVerticalDeflectionMillimeters(),
				rotorDynamicsAudit.armFlex().maxSteadyVerticalDeflectionMillimeters(),
				rotorDynamicsAudit.armFlex().maxSnapVerticalDeflectionMillimeters(),
				rotorDynamicsAudit.armFlex().fullFlexTiltDegrees(),
				rotorDynamicsAudit.armFlex().maxSteadyTiltDegrees(),
				rotorDynamicsAudit.armFlex().maxSnapTiltDegrees(),
				rotorDynamicsAudit.armFlex().representativeBeamSensitivity().geometryId(),
				rotorDynamicsAudit.armFlex().representativeBeamSensitivity().cantileverTipDeflectionMillimeters(),
				rotorDynamicsAudit.armFlex().representativeBeamSensitivity().beamFrequencyOverRuntimeMaxSpin()
		);
		System.out.printf(
				Locale.ROOT,
				"High-advance prop audit: %s APC %d props/%d rows maxJ %.3f mu %.3f, rotor P/D %.3f blades %d, 5.1x5 thr_zeroJ %.3f pos_mu %.3f, 5x11 pos_mu %.3f; current uiucJ %.3f th/pw %.2f/%.2f vs APC %.2f/%.2f, fpvJ %.3f th/pw %.2f/%.2f vs APC %.2f/%.2f, x11J %.3f th/pw %.2f/%.2f vs APC %.2f/%.2f caveat axial-not-edgewise%n",
				highAdvanceAudit.sourceId(),
				highAdvanceAudit.selectedApcPropellerCount(),
				highAdvanceAudit.selectedApcRowCount(),
				highAdvanceAudit.selectedApcMaxAdvanceRatioJ(),
				highAdvanceAudit.selectedApcMaxEquivalentProjectMu(),
				highAdvanceAudit.representativeRotorPitchToDiameterRatio(),
				highAdvanceAudit.representativeRotorBladeCount(),
				highAdvanceAudit.fpvAdjacentThreeBladeReference().nearestZeroCtAdvanceRatioJ(),
				highAdvanceAudit.fpvAdjacentThreeBladeReference().highestPositiveCtEquivalentProjectMu(),
				highAdvanceAudit.extremeHighPitchReference().highestPositiveCtEquivalentProjectMu(),
				highAdvanceAudit.uiucMeasuredRangeMax().targetEquivalentAdvanceRatioJ(),
				highAdvanceAudit.uiucMeasuredRangeMax().currentThrustScale(),
				highAdvanceAudit.uiucMeasuredRangeMax().currentPowerScale(),
				highAdvanceAudit.uiucMeasuredRangeMax().apcCtOverStaticCt(),
				highAdvanceAudit.uiucMeasuredRangeMax().apcCpOverStaticCp(),
				highAdvanceAudit.fpvAdjacentLiftDissymmetryEnd().targetEquivalentAdvanceRatioJ(),
				highAdvanceAudit.fpvAdjacentLiftDissymmetryEnd().currentThrustScale(),
				highAdvanceAudit.fpvAdjacentLiftDissymmetryEnd().currentPowerScale(),
				highAdvanceAudit.fpvAdjacentLiftDissymmetryEnd().apcCtOverStaticCt(),
				highAdvanceAudit.fpvAdjacentLiftDissymmetryEnd().apcCpOverStaticCp(),
				highAdvanceAudit.extremePitchHighAdvanceLossStart().targetEquivalentAdvanceRatioJ(),
				highAdvanceAudit.extremePitchHighAdvanceLossStart().currentThrustScale(),
				highAdvanceAudit.extremePitchHighAdvanceLossStart().currentPowerScale(),
				highAdvanceAudit.extremePitchHighAdvanceLossStart().apcCtOverStaticCt(),
				highAdvanceAudit.extremePitchHighAdvanceLossStart().apcCpOverStaticCp()
		);
		System.out.printf(
				Locale.ROOT,
				"Mejzlik wind-tunnel prop audit: %s rows %d table %d model_cmp %d rpm %.0f speed %.0fm/s cfdJ %.2f, CT0 J %.3f mu %.3f hover %.1fm/s, current thresholds lift/rbs/loss %.2f/%.2f/%.2fx CT0 loss_hover %.1fm/s, J0.2 th/pw %.2f/%.2f vs tunnel %.2f/%.2f, J0.6 th/pw %.2f/%.2f vs tunnel %.2f/%.2f, J0.8 th/pw %.2f/%.2f tunnelCT %.4f caveat axial-wind-tunnel-not-edgewise-fpv%n",
				highAdvanceAudit.mejzlikWindTunnelAudit().sourceId(),
				highAdvanceAudit.mejzlikWindTunnelAudit().packetMetricRowCount(),
				highAdvanceAudit.mejzlikWindTunnelAudit().tableValueRowCount(),
				highAdvanceAudit.mejzlikWindTunnelAudit().modelVsTunnelRowCount(),
				highAdvanceAudit.mejzlikWindTunnelAudit().windTunnelRpm(),
				highAdvanceAudit.mejzlikWindTunnelAudit().windTunnelSpeedMaxMetersPerSecond(),
				highAdvanceAudit.mejzlikWindTunnelAudit().cfdMaxAdvanceRatioJ(),
				highAdvanceAudit.mejzlikWindTunnelAudit().ctZeroCrossingAdvanceRatioJ(),
				highAdvanceAudit.mejzlikWindTunnelAudit().ctZeroCrossingProjectMu(),
				highAdvanceAudit.mejzlikWindTunnelAudit().racingHoverSpeedAtCtZeroMetersPerSecond(),
				highAdvanceAudit.mejzlikWindTunnelAudit().currentLiftDissymmetryEndOverCtZeroJ(),
				highAdvanceAudit.mejzlikWindTunnelAudit().currentRetreatingStallStartOverCtZeroJ(),
				highAdvanceAudit.mejzlikWindTunnelAudit().currentHighAdvanceLossStartOverCtZeroJ(),
				highAdvanceAudit.mejzlikWindTunnelAudit().racingHoverSpeedAtCurrentHighAdvanceLossStartMetersPerSecond(),
				highAdvanceAudit.mejzlikWindTunnelAudit().lowAdvancePoint().currentThrustScale(),
				highAdvanceAudit.mejzlikWindTunnelAudit().lowAdvancePoint().currentPowerScale(),
				highAdvanceAudit.mejzlikWindTunnelAudit().lowAdvancePoint().windTunnelCtOverJ02(),
				highAdvanceAudit.mejzlikWindTunnelAudit().lowAdvancePoint().windTunnelCpOverJ02(),
				highAdvanceAudit.mejzlikWindTunnelAudit().highMeasuredPoint().currentThrustScale(),
				highAdvanceAudit.mejzlikWindTunnelAudit().highMeasuredPoint().currentPowerScale(),
				highAdvanceAudit.mejzlikWindTunnelAudit().highMeasuredPoint().windTunnelCtOverJ02(),
				highAdvanceAudit.mejzlikWindTunnelAudit().highMeasuredPoint().windTunnelCpOverJ02(),
				highAdvanceAudit.mejzlikWindTunnelAudit().windmillingBoundaryPoint().currentThrustScale(),
				highAdvanceAudit.mejzlikWindTunnelAudit().windmillingBoundaryPoint().currentPowerScale(),
				highAdvanceAudit.mejzlikWindTunnelAudit().windmillingBoundaryPoint().windTunnelCt()
		);
		System.out.printf(
				Locale.ROOT,
				"Prop geometry audit: %s rows %d refs %d/%d current %.2fx%.2fin P/D %.3f blades %d angle70 %.2fdeg chord/R %.3f pitch_speed %.1f/%.1fm/s, %s pitch %.2fx P/D %.2fx angle %.2fx, %s pitch %.2fx P/D %.2fx angle %.2fx, %s pitch %.2fx P/D %.2fx angle %.2fx, UIUC %s chord %.2fx beta %.2fx localP %.2fx, %s chord %.2fx beta %.2fx localP %.2fx, lift %s beta %.2fx localP %.2fx caveat geometry-not-slip%n",
				propGeometryAudit.sourceId(),
				propGeometryAudit.packetRowCount(),
				propGeometryAudit.officialPropReferenceCount(),
				propGeometryAudit.uiucGeometryReferenceCount(),
				propGeometryAudit.current().diameterInches(),
				propGeometryAudit.current().pitchInches(),
				propGeometryAudit.current().pitchToDiameterRatio(),
				propGeometryAudit.current().bladeCount(),
				propGeometryAudit.current().geometricPitchAngle70rDegrees(),
				propGeometryAudit.current().representativeChordToRadius70r(),
				propGeometryAudit.current().hoverPitchSpeedMetersPerSecond(),
				propGeometryAudit.current().maxPitchSpeedMetersPerSecond(),
				propGeometryAudit.hq5x43Comparison().reference().propellerId(),
				propGeometryAudit.hq5x43Comparison().currentPitchOverReference(),
				propGeometryAudit.hq5x43Comparison().currentPitchToDiameterOverReference(),
				propGeometryAudit.hq5x43Comparison().currentGeometricPitchAngleOverReference(),
				propGeometryAudit.hq5x45Comparison().reference().propellerId(),
				propGeometryAudit.hq5x45Comparison().currentPitchOverReference(),
				propGeometryAudit.hq5x45Comparison().currentPitchToDiameterOverReference(),
				propGeometryAudit.hq5x45Comparison().currentGeometricPitchAngleOverReference(),
				propGeometryAudit.gemfan51466Comparison().reference().propellerId(),
				propGeometryAudit.gemfan51466Comparison().currentPitchOverReference(),
				propGeometryAudit.gemfan51466Comparison().currentPitchToDiameterOverReference(),
				propGeometryAudit.gemfan51466Comparison().currentGeometricPitchAngleOverReference(),
				propGeometryAudit.da4052Comparison().reference().geometryId(),
				propGeometryAudit.da4052Comparison().currentChordOverReference70r(),
				propGeometryAudit.da4052Comparison().currentGeometricPitchAngleOverReference70r(),
				propGeometryAudit.da4052Comparison().currentPitchToDiameterOverReferenceLocal70r(),
				propGeometryAudit.nr640Comparison().reference().geometryId(),
				propGeometryAudit.nr640Comparison().currentChordOverReference70r(),
				propGeometryAudit.nr640Comparison().currentGeometricPitchAngleOverReference70r(),
				propGeometryAudit.nr640Comparison().currentPitchToDiameterOverReferenceLocal70r(),
				propGeometryAudit.apcThin10x5Comparison().reference().geometryId(),
				propGeometryAudit.apcThin10x5Comparison().currentGeometricPitchAngleOverReference70r(),
				propGeometryAudit.apcThin10x5Comparison().currentPitchToDiameterOverReferenceLocal70r()
		);
		System.out.printf(
				Locale.ROOT,
				"Precipitation/water audit: %s rows %d refs %d rain/water/moist %d/%d/%d, ICAS-2020-heavy-rain-CT %.0fmmh loss %.2f/%.2f%% java %.2f%% ratios %.2f/%.2f, NWS water_gps %.3f/%.3f/%.3f wet %.3f/%.3f/%.3f java_loss %.2f/%.2f/%.2f%%, stress100 %.3fg/s impact %.4fx, water0.5@5m/s loss %.1f%% drag %.2fx, moist35 %.4f caveat wet-prop-not-immersion%n",
				precipitationWaterAudit.sourceId(),
				precipitationWaterAudit.packetMetricRowCount(),
				precipitationWaterAudit.sourceReferenceCount(),
				precipitationWaterAudit.rainScanScenarioCount(),
				precipitationWaterAudit.waterImmersionScanScenarioCount(),
				precipitationWaterAudit.moistAirDensityScenarioCount(),
				precipitationWaterAudit.icas4319Rpm().equivalentRainRateMillimetersPerHour(),
				precipitationWaterAudit.icas4319Rpm().ctLossPercent(),
				precipitationWaterAudit.icas6528Rpm().ctLossPercent(),
				precipitationWaterAudit.formula().javaFullWetnessThrustLossPercent(),
				precipitationWaterAudit.icas4319Comparison().javaLossOverIcasLoss(),
				precipitationWaterAudit.icas6528Comparison().javaLossOverIcasLoss(),
				precipitationWaterAudit.nwsLightRain005InHour().allRotorsWaterGramsPerSecond(),
				precipitationWaterAudit.nwsModerateRain025InHour().allRotorsWaterGramsPerSecond(),
				precipitationWaterAudit.nwsFullWetness150InHour().allRotorsWaterGramsPerSecond(),
				precipitationWaterAudit.nwsLightRain005InHour().precipitationWetnessProxy(),
				precipitationWaterAudit.nwsModerateRain025InHour().precipitationWetnessProxy(),
				precipitationWaterAudit.nwsFullWetness150InHour().precipitationWetnessProxy(),
				precipitationWaterAudit.nwsLightRain005InHour().javaSourceThrustLossPercent(),
				precipitationWaterAudit.nwsModerateRain025InHour().javaSourceThrustLossPercent(),
				precipitationWaterAudit.nwsFullWetness150InHour().javaSourceThrustLossPercent(),
				precipitationWaterAudit.stress100MillimetersPerHour().allRotorsWaterGramsPerSecond(),
				precipitationWaterAudit.stress100MillimetersPerHour().rainImpactForceAllRotorsOverWeightAt8MetersPerSecond(),
				precipitationWaterAudit.halfImmersionAt5MetersPerSecond().waterImmersionThrustLossPercent(),
				precipitationWaterAudit.halfImmersionAt5MetersPerSecond().waterDragOverWeight(),
				precipitationWaterAudit.hotFullWetMoistAir().moistAirDensityMultiplier()
		);
		System.out.printf(
				Locale.ROOT,
				"Icing rotor audit: %s rows %d source %s %.2fm %.0f blades tip %.0fm/s, table4 %.0frpm %.1fdeg lambda %.0fg/dm2/h rain %.1fmmh, CT rate %.3f/%.3f/%.3f%%/s time %.0f/%.1f/%.0fs loss %.1f/%.1f/%.1f%% power %.1f/%.1f/%.1f%%, rain_loss %.1f%% ratio %.2f/%.2fx runtime sev1 %.1fs thrust %.3f power %.3f max %.1f%%/%.3f, extremes %s %.1f%% %s %.1f%% caveat %s%n",
				icingRotorAudit.sourceId(),
				icingRotorAudit.rowTypeCounts().totalRowCount(),
				icingRotorAudit.sourceInventory().doi(),
				icingRotorAudit.sourceInventory().rotorDiameterMeters(),
				icingRotorAudit.sourceInventory().rotorBladeCount(),
				icingRotorAudit.sourceInventory().rotorMaxTipSpeedMetersPerSecond(),
				icingRotorAudit.sourceInventory().table4Rpm(),
				icingRotorAudit.sourceInventory().table4PitchDegrees(),
				icingRotorAudit.sourceInventory().table4LambdaGdm2h(),
				icingRotorAudit.sourceInventory().table4EquivalentRainMillimetersPerHour(),
				icingRotorAudit.distribution().absCtStarRateMinPercentPerSecond(),
				icingRotorAudit.distribution().absCtStarRateMedianPercentPerSecond(),
				icingRotorAudit.distribution().absCtStarRateMaxPercentPerSecond(),
				icingRotorAudit.distribution().icingTimeMinSeconds(),
				icingRotorAudit.distribution().icingTimeMedianSeconds(),
				icingRotorAudit.distribution().icingTimeMaxSeconds(),
				icingRotorAudit.distribution().projectedCtLossMinPercent(),
				icingRotorAudit.distribution().projectedCtLossMedianPercent(),
				icingRotorAudit.distribution().projectedCtLossMaxPercent(),
				icingRotorAudit.distribution().projectedPowerRequiredMinPercent(),
				icingRotorAudit.distribution().projectedPowerRequiredMedianPercent(),
				icingRotorAudit.distribution().projectedPowerRequiredMaxPercent(),
				icingRotorAudit.currentModelComparison().currentFullWetnessRainLossPercent(),
				icingRotorAudit.currentModelComparison().icingProjectedCtLossMedianOverCurrentRainLoss(),
				icingRotorAudit.currentModelComparison().icingProjectedCtLossMaxOverCurrentRainLoss(),
				icingRotorAudit.runtimeModel().severityOneIcingTimeSeconds(),
				icingRotorAudit.runtimeModel().severityOneThrustScale(),
				icingRotorAudit.runtimeModel().severityOnePowerScale(),
				icingRotorAudit.runtimeModel().maxModeledCtLossPercent(),
				icingRotorAudit.runtimeModel().maxModeledPowerScale(),
				icingRotorAudit.extremeCase().strongestProjectedCtLossCase(),
				icingRotorAudit.extremeCase().strongestProjectedCtLossPercent(),
				icingRotorAudit.extremeCase().strongestProjectedPowerRequiredCase(),
				icingRotorAudit.extremeCase().strongestProjectedPowerRequiredPercent(),
				icingRotorAudit.caveat()
		);
		System.out.printf(
				Locale.ROOT,
				"Wind/gust audit: %s rows %d refs %d scans %d spectral %d ICAS %d, rep %s rms %.2f/%.2f/%.2fm/s dryden %.2f/%.2f ratio %.2f/%.2f peak %.2f/%.2f/%.2f, corner %.2fHz dryden %.3f/%.3fHz shape1 %.1f/%.2fx, light %.2f/%.2f/%.2fm/s sat %.2f/%.2f/%.2fm/s, scan_rms_ratio %.2f..%.2f/%.2f..%.2f, ICAS10ms CT 4319 %.0f..%.0f%% 6528 %.0f..%.0f%% caveat dryden-burble-ct-separate%n",
				windGustAudit.sourceId(),
				windGustAudit.packetMetricRowCount(),
				windGustAudit.sourceReferenceCount(),
				windGustAudit.currentWindScanCount(),
				windGustAudit.spectralShapeMetricRowCount(),
				windGustAudit.icasHoverGustRowCount(),
				windGustAudit.representativeDirtyAir().scenarioId(),
				windGustAudit.representativeDirtyAir().currentGustRmsXMetersPerSecond(),
				windGustAudit.representativeDirtyAir().currentGustRmsYMetersPerSecond(),
				windGustAudit.representativeDirtyAir().currentGustRmsZMetersPerSecond(),
				windGustAudit.representativeDirtyAir().drydenTargetRmsXMetersPerSecond(),
				windGustAudit.representativeDirtyAir().drydenTargetRmsYMetersPerSecond(),
				windGustAudit.representativeDirtyAir().currentXRmsOverDrydenU(),
				windGustAudit.representativeDirtyAir().currentYRmsOverDrydenW(),
				windGustAudit.representativeDirtyAir().currentGustPeakXMetersPerSecond(),
				windGustAudit.representativeDirtyAir().currentGustPeakYMetersPerSecond(),
				windGustAudit.representativeDirtyAir().currentGustPeakZMetersPerSecond(),
				windGustAudit.representativeSpectralShape().currentGustCornerHertz(),
				windGustAudit.representativeSpectralShape().drydenLongitudinalPoleHertz(),
				windGustAudit.representativeSpectralShape().drydenVerticalPoleHertz(),
				windGustAudit.representativeSpectralShape().currentShapeOverDrydenLongitudinalAtOneHertz(),
				windGustAudit.representativeSpectralShape().currentShapeOverDrydenVerticalAtOneHertz(),
				windGustAudit.lightDirtyAir().currentGustRmsXMetersPerSecond(),
				windGustAudit.lightDirtyAir().currentGustRmsYMetersPerSecond(),
				windGustAudit.lightDirtyAir().currentGustRmsZMetersPerSecond(),
				windGustAudit.saturatedDirtyAir().currentGustRmsXMetersPerSecond(),
				windGustAudit.saturatedDirtyAir().currentGustRmsYMetersPerSecond(),
				windGustAudit.saturatedDirtyAir().currentGustRmsZMetersPerSecond(),
				windGustAudit.currentXRmsOverDrydenUMin(),
				windGustAudit.currentXRmsOverDrydenUMax(),
				windGustAudit.currentYRmsOverDrydenWMin(),
				windGustAudit.currentYRmsOverDrydenWMax(),
				windGustAudit.strongest4319Downdraft().ctChangePercent(),
				windGustAudit.strongest4319Updraft().ctChangePercent(),
				windGustAudit.strongest6528Downdraft().ctChangePercent(),
				windGustAudit.strongest6528Updraft().ctChangePercent()
		);
		System.out.printf(
				Locale.ROOT,
				"VRS/propwash audit: %s rows %d refs %d scans %d/%d shetty %d/%d cmp %d, Cambridge peak %.2f..%.2fvi loss %.0f%% broad %.2f..%.2fvi, preset active %.2f..%.2fvi full_prop %.2fvi, peak %.2fvi loss %.1f%% buffet %.1f%% lat_bound %.1f%% torque %.3fNm freq %.1fHz, early %.2fvi loss %.1f%% buffet %.1f%%, exit %.2fvi loss %.1f%%, Shetty max %.2fvi amp %.2f current %.2fx best %.2fx loss %.2fx caveat separate-mean-buffet-lateral-torque%n",
				vrsPropwashAudit.sourceId(),
				vrsPropwashAudit.packetMetricRowCount(),
				vrsPropwashAudit.sourceReferenceCount(),
				vrsPropwashAudit.currentScanMetricRowCount(),
				vrsPropwashAudit.currentScanScenarioCount(),
				vrsPropwashAudit.shettyDigitizedMetricRowCount(),
				vrsPropwashAudit.shettyDigitizedPointCount(),
				vrsPropwashAudit.currentVsShettyMetricRowCount(),
				vrsPropwashAudit.referenceRegime().cambridgePeakBandLowVi(),
				vrsPropwashAudit.referenceRegime().cambridgePeakBandHighVi(),
				vrsPropwashAudit.referenceRegime().cambridgePeakLossFraction() * 100.0,
				vrsPropwashAudit.referenceRegime().broadRegimeLowVi(),
				vrsPropwashAudit.referenceRegime().broadRegimeHighVi(),
				vrsPropwashAudit.activeEnvelope().firstActiveDescentRatioVi(),
				vrsPropwashAudit.activeEnvelope().lastActiveDescentRatioVi(),
				vrsPropwashAudit.activeEnvelope().propwashFullyActiveFromDescentRatioVi(),
				vrsPropwashAudit.activeEnvelope().peakLossDescentRatioVi(),
				vrsPropwashAudit.peakBandLow().currentVrsBaseThrustLossPercentHoverSpin(),
				vrsPropwashAudit.peakBandLow().currentVrsBuffetHalfAmplitudePercentMaxSpin(),
				vrsPropwashAudit.peakBandLow().currentVrsLateralForceBoundPercentMaxThrust(),
				vrsPropwashAudit.peakBandLow().propwashMaxTorqueNewtonMeters(),
				vrsPropwashAudit.peakBandLow().buffetFrequencyHertzHoverSpin(),
				vrsPropwashAudit.earlyEntry().descentRatioVi(),
				vrsPropwashAudit.earlyEntry().currentVrsBaseThrustLossPercentHoverSpin(),
				vrsPropwashAudit.earlyEntry().currentVrsBuffetHalfAmplitudePercentMaxSpin(),
				vrsPropwashAudit.highDescentExit().descentRatioVi(),
				vrsPropwashAudit.highDescentExit().currentVrsBaseThrustLossPercentHoverSpin(),
				vrsPropwashAudit.largestShettyDigitized().descentRatioViProxy(),
				vrsPropwashAudit.largestShettyDigitized().referenceMeasuredHalfAmplitudeFraction(),
				vrsPropwashAudit.largestShettyDigitized().currentBuffetOverReferenceMeasuredHalfAmplitude(),
				vrsPropwashAudit.bestCurrentShettyMatch().currentBuffetOverReferenceMeasuredHalfAmplitude(),
				vrsPropwashAudit.bestCurrentShettyMatch().currentBaseLossOverCambridgePeakLoss()
		);
		System.out.printf(
				Locale.ROOT,
				"Coaxial allocation audit: %s rows %d platform %s %.1fkgf %.1fgf torque %.4f/%.7fNm channels %s, zD %.2f pairs %d sep %.4fm R %.3fm geom_match %s wake %.2f/%.2f%% zD055_extra %.2f/%.2f%%, 60pct ratio %.3f bias %.3f left/right %.3f/%.3f mech/elec %.2f/%.2f%% unc %.2f%% p10/med/p90 %.3f/%.3f/%.3f mech %.2f/%.2f/%.2f%%, 11in zD0.70 1000g %.3f %.2f%% 1500g %.3f %.2f%% claim %.1f%%, strongest multi %.2f%% %.3f command %.2f%% %.3f elec %.2f%% surface %.2f%%/%.4f %.2f%%/%.4f caveat %s%n",
				coaxialAllocationAudit.sourceId(),
				coaxialAllocationAudit.rowTypeCounts().totalRowCount(),
				coaxialAllocationAudit.referencePlatform().platform(),
				coaxialAllocationAudit.referencePlatform().thrustLoadCellCapacityKgf(),
				coaxialAllocationAudit.referencePlatform().thrustPrecisionGf(),
				coaxialAllocationAudit.referencePlatform().torqueCapacityNewtonMeters(),
				coaxialAllocationAudit.referencePlatform().torquePrecisionNewtonMeters(),
				coaxialAllocationAudit.referencePlatform().measuredChannels(),
				coaxialAllocationAudit.currentGeometry().separationOverDiameter(),
				coaxialAllocationAudit.currentGeometry().coaxialPairCount(),
				coaxialAllocationAudit.currentGeometry().upperLowerSeparationMeters(),
				coaxialAllocationAudit.currentGeometry().radiusMeters(),
				coaxialAllocationAudit.currentGeometry().matchesPacketGeometry(),
				coaxialAllocationAudit.wakeLoss().hoverWakeLossZOverD072Percent(),
				coaxialAllocationAudit.wakeLoss().maxWakeLossZOverD072Percent(),
				coaxialAllocationAudit.wakeLoss().hoverWakeLossZOverD055MinusZOverD072Percent(),
				coaxialAllocationAudit.wakeLoss().maxWakeLossZOverD055MinusZOverD072Percent(),
				coaxialAllocationAudit.runtimeAllocation().recommendedPwmRatioRightOverLeft(),
				coaxialAllocationAudit.runtimeAllocation().loadBiasTarget(),
				coaxialAllocationAudit.runtimeAllocation().recommendedLeftPwmScaleVsEqual(),
				coaxialAllocationAudit.runtimeAllocation().recommendedRightPwmScaleVsEqual(),
				coaxialAllocationAudit.runtimeAllocation().mechanicalGainOverEqualPercent(),
				coaxialAllocationAudit.runtimeAllocation().electricalGainOverEqualPercent(),
				coaxialAllocationAudit.runtimeAllocation().allocationUncertaintyPercent(),
				coaxialAllocationAudit.runtimeAllocation().allGroupRatioP10(),
				coaxialAllocationAudit.runtimeAllocation().allGroupRatioMedian(),
				coaxialAllocationAudit.runtimeAllocation().allGroupRatioP90(),
				coaxialAllocationAudit.runtimeAllocation().allGroupMechanicalGainP10Percent(),
				coaxialAllocationAudit.runtimeAllocation().allGroupMechanicalGainMedianPercent(),
				coaxialAllocationAudit.runtimeAllocation().allGroupMechanicalGainP90Percent(),
				coaxialAllocationAudit.benchmarkAllocation().elevenInZOverD070RatioAt1000g(),
				coaxialAllocationAudit.benchmarkAllocation().elevenInZOverD070MechanicalGainAt1000gPercent(),
				coaxialAllocationAudit.benchmarkAllocation().elevenInZOverD070RatioAt1500g(),
				coaxialAllocationAudit.benchmarkAllocation().elevenInZOverD070MechanicalGainAt1500gPercent(),
				coaxialAllocationAudit.benchmarkAllocation().allocationClaimMechanicalGainPercent(),
				coaxialAllocationAudit.strongestAllocation().strongestMultiOptimal60PercentMechanicalGainPercent(),
				coaxialAllocationAudit.strongestAllocation().strongestMultiOptimal60PercentPwmRatioRightOverLeft(),
				coaxialAllocationAudit.strongestAllocation().strongestCommandEnvelope60PercentMechanicalGainPercent(),
				coaxialAllocationAudit.strongestAllocation().strongestCommandEnvelope60PercentPwmRatioRightOverLeft(),
				coaxialAllocationAudit.strongestAllocation().strongestCommandEnvelope60PercentElectricalGainPercent(),
				coaxialAllocationAudit.surfaceFit().thrustMedianCvRmseOverRangePercent(),
				coaxialAllocationAudit.surfaceFit().thrustMedianCvR2(),
				coaxialAllocationAudit.surfaceFit().mechanicalPowerMedianCvRmseOverRangePercent(),
				coaxialAllocationAudit.surfaceFit().mechanicalPowerMedianCvR2(),
				coaxialAllocationAudit.caveat()
		);
		System.out.printf(
				Locale.ROOT,
				"Surface near-field audit: %s rows %d refs %d ground/ceiling %d wall_map %d wall_force %d zju %d, hR1 current/zju %.3f/%.3f extra %.2f cheese %.2f ceiling %.3f, hR0.5 %.3f hR2 %.3f, wall0.25R obs %.3f loss %.1f%% side %.1f%%W, wall1R loss %.1f%% side %.1f%%W, fullObs side %.1f/%.1f%%W speed12 %.1f%%W, ZJU drag %.2f/%.2f pred %.2f caveat ground-ceiling-wall-separate%n",
				surfaceNearfieldAudit.sourceId(),
				surfaceNearfieldAudit.packetMetricRowCount(),
				surfaceNearfieldAudit.sourceReferenceCount(),
				surfaceNearfieldAudit.groundCeilingScanRowCount(),
				surfaceNearfieldAudit.wallRuntimeMappingRowCount(),
				surfaceNearfieldAudit.wallForceScanRowCount(),
				surfaceNearfieldAudit.zjuGroundCheckRowCount(),
				surfaceNearfieldAudit.oneRadiusGround().currentGroundMultiplier(),
				surfaceNearfieldAudit.oneRadiusGround().zjuGroundMultiplier(),
				surfaceNearfieldAudit.oneRadiusGround().currentExtraOverZjuExtra(),
				surfaceNearfieldAudit.oneRadiusGround().currentGroundOverCheeseman(),
				surfaceNearfieldAudit.oneRadiusGround().currentCeilingMultiplier(),
				surfaceNearfieldAudit.halfRadiusGround().currentGroundMultiplier(),
				surfaceNearfieldAudit.twoRadiusGround().currentGroundMultiplier(),
				surfaceNearfieldAudit.quarterRadiusWall().runtimeObstruction(),
				surfaceNearfieldAudit.quarterRadiusWall().twoAffectedVehicleThrustLossFraction() * 100.0,
				surfaceNearfieldAudit.quarterRadiusWall().twoAffectedWallForceOverWeight() * 100.0,
				surfaceNearfieldAudit.oneRadiusWall().twoAffectedVehicleThrustLossFraction() * 100.0,
				surfaceNearfieldAudit.oneRadiusWall().twoAffectedWallForceOverWeight() * 100.0,
				surfaceNearfieldAudit.fullObstructionWall().twoAffectedWallForceOverWeight() * 100.0,
				surfaceNearfieldAudit.fullObstructionHoverSideForce().fourRotorForceOverWeight() * 100.0,
				surfaceNearfieldAudit.fullObstructionFastSideForce().twoRotorForceOverWeight() * 100.0,
				surfaceNearfieldAudit.zjuDragObservation().measuredDragXLowOverHigh(),
				surfaceNearfieldAudit.zjuDragObservation().measuredDragYLowOverHigh(),
				surfaceNearfieldAudit.zjuDragObservation().predictedDragRatioFromSqrtThrust()
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone inertia audit: %s mass %.4f/%.4f ratio %.3f Ixyz %.6f/%.6f/%.6f ref %.6f/%.6f/%.6f rg %.4f/%.4f/%.4f ref %.4f/%.4f/%.4f yaw_ratio %.3f/%.3f map %.2f/%.2f/%.2f radius %.3f/%.3f%n",
				apDroneInertiaAudit.referenceId(),
				apDroneInertiaAudit.currentMassKg(),
				apDroneInertiaAudit.referenceMassKg(),
				apDroneInertiaAudit.currentMassOverReference(),
				apDroneInertiaAudit.currentProjectInertiaXKgMetersSquared(),
				apDroneInertiaAudit.currentProjectYawInertiaYKgMetersSquared(),
				apDroneInertiaAudit.currentProjectInertiaZKgMetersSquared(),
				apDroneInertiaAudit.referenceSourceInertiaXKgMetersSquared(),
				apDroneInertiaAudit.referenceSourceYawInertiaZKgMetersSquared(),
				apDroneInertiaAudit.referenceSourceInertiaYKgMetersSquared(),
				apDroneInertiaAudit.currentRadiusOfGyrationXMeters(),
				apDroneInertiaAudit.currentRadiusOfGyrationYawYMeters(),
				apDroneInertiaAudit.currentRadiusOfGyrationZMeters(),
				apDroneInertiaAudit.referenceRadiusOfGyrationSourceXMeters(),
				apDroneInertiaAudit.referenceRadiusOfGyrationYawZMeters(),
				apDroneInertiaAudit.referenceRadiusOfGyrationSourceYMeters(),
				apDroneInertiaAudit.currentYawToRollPitchMeanInertiaRatio(),
				apDroneInertiaAudit.referenceYawToRollPitchMeanInertiaRatio(),
				apDroneInertiaAudit.currentProjectXOverReferenceSourceX(),
				apDroneInertiaAudit.currentProjectYawYOverReferenceSourceYawZ(),
				apDroneInertiaAudit.currentProjectZOverReferenceSourceY(),
				apDroneInertiaAudit.currentMotorCenterRadiusMeters(),
				apDroneInertiaAudit.referenceMotorCenterRadiusMeters()
		);
		System.out.printf(
				Locale.ROOT,
				"Tyto x3nm static-powertrain audit: max_thrust %.2fN/%.2fN ratio %.2f, k %.3e/%.3e ratio %.2f, rpm %.0f tyto_eq %.0f tyto_ref %.0f, ref_current %.1fA@%.1fV fit_r2 %.4f%n",
				tytoAudit.configuredMaxRotorThrustNewtons(),
				tytoAudit.referenceMaxThrustNewtons(),
				tytoAudit.configuredMaxThrustOverReference(),
				tytoAudit.configuredThrustCoefficient(),
				tytoAudit.referenceThrustCoefficient(),
				tytoAudit.configuredThrustCoefficientOverReference(),
				tytoAudit.configuredMaxRpm(),
				tytoAudit.referenceEquivalentRpmForConfiguredMaxThrust(),
				tytoAudit.referenceRpmAtMaxThrust(),
				tytoAudit.referenceMaxCurrentAmps(),
				tytoAudit.referenceVoltageAtMaxThrust(),
				tytoAudit.referenceFitR2()
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone motor PDF audit: %s max_thrust %.2fN headline %.2fN ratio %.2f table %.2fN@%.1fA/%.1fV ratio %.2f, kv_rpm pdf %.2fx/%.2fx bf %.2fx/%.2fx, winding_r %.4f/%.4f ohm ratio %.2f, per_motor_current %.1f/%.1fA ratio %.2f%n",
				apDroneMotorAudit.referenceId(),
				apDroneMotorAudit.configuredMaxRotorThrustNewtons(),
				apDroneMotorAudit.referenceHeadlineMaxThrustNewtons(),
				apDroneMotorAudit.configuredMaxThrustOverReferenceHeadline(),
				apDroneMotorAudit.referenceBestVisibleMaxThrustNewtons(),
				apDroneMotorAudit.referenceBestVisibleMaxCurrentAmps(),
				apDroneMotorAudit.referenceBestVisibleMaxVoltageVolts(),
				apDroneMotorAudit.configuredMaxThrustOverReferenceBestVisible(),
				apDroneMotorAudit.configuredMaxRpmOverReferenceKvFullCharge(),
				apDroneMotorAudit.configuredMaxRpmOverReferenceKvNominal(),
				apDroneMotorAudit.configuredMaxRpmOverBetaflightKvFullCharge(),
				apDroneMotorAudit.configuredMaxRpmOverBetaflightKvNominal(),
				apDroneMotorAudit.configuredMotorWindingResistanceOhms(),
				apDroneMotorAudit.referenceMotorWindingResistanceOhms(),
				apDroneMotorAudit.configuredMotorWindingResistanceOverReference(),
				apDroneMotorAudit.configuredPerMotorPackCurrentAmps(),
				apDroneMotorAudit.referenceContinuousCurrentAmps(),
				apDroneMotorAudit.configuredPerMotorPackCurrentOverReferenceContinuous()
		);
		System.out.printf(
				Locale.ROOT,
				"Foxeer Donut 5145 prop audit: %s max_thrust %.2fN/%.2fN ratio %.2f, k %.3e/%.3e ratio %.2f, yaw_qt %.5fm/%.5fm ratio %.2f, rpm %.0f/%.0f ratio %.3f, torque %.3fNm current %.1fA@%.1fV power %.0fW vib %.1fg%n",
				foxeerPropAudit.referenceId(),
				foxeerPropAudit.configuredMaxRotorThrustNewtons(),
				foxeerPropAudit.referenceThrustNewtons(),
				foxeerPropAudit.configuredMaxThrustOverReference(),
				foxeerPropAudit.configuredThrustCoefficient(),
				foxeerPropAudit.referenceThrustCoefficient(),
				foxeerPropAudit.configuredThrustCoefficientOverReference(),
				foxeerPropAudit.configuredYawTorquePerThrustMeter(),
				foxeerPropAudit.referenceTorquePerThrustMeter(),
				foxeerPropAudit.configuredYawTorquePerThrustOverReference(),
				foxeerPropAudit.configuredMaxRpm(),
				foxeerPropAudit.referenceRpm(),
				foxeerPropAudit.configuredMaxRpmOverReference(),
				foxeerPropAudit.referenceTorqueNewtonMeters(),
				foxeerPropAudit.referenceCurrentAmps(),
				foxeerPropAudit.referenceVoltageVolts(),
				foxeerPropAudit.referencePowerWatts(),
				foxeerPropAudit.referenceVibrationG()
		);
		System.out.printf(
				Locale.ROOT,
				"Tyto static yaw-torque audit: yaw_qt %.5fm, %s fit %.5fm ratio %.2f high %.5fm, %s-5040 fit %.5fm ratio %.2f high %.5fm, fit_window %.5f..%.5fm pos %.2f, fit_r2 %.4f/%.4f%n",
				tytoYawTorqueAudit.configuredYawTorquePerThrustMeter(),
				tytoYawTorqueAudit.lowTorqueReferenceId(),
				tytoYawTorqueAudit.lowTorqueReferenceFitQOverTMeters(),
				tytoYawTorqueAudit.configuredOverLowTorqueReferenceFit(),
				tytoYawTorqueAudit.lowTorqueReferenceHighThrustMeanQOverTMeters(),
				tytoYawTorqueAudit.highTorqueReferenceId(),
				tytoYawTorqueAudit.highTorqueReferenceFitQOverTMeters(),
				tytoYawTorqueAudit.configuredOverHighTorqueReferenceFit(),
				tytoYawTorqueAudit.highTorqueReferenceHighThrustMeanQOverTMeters(),
				tytoYawTorqueAudit.referenceFitWindowMinMeters(),
				tytoYawTorqueAudit.referenceFitWindowMaxMeters(),
				tytoYawTorqueAudit.configuredPositionWithinReferenceFitWindow(),
				tytoYawTorqueAudit.lowTorqueReferenceFitR2(),
				tytoYawTorqueAudit.highTorqueReferenceFitR2()
		);
		System.out.printf(
				Locale.ROOT,
				"AI-IO rotor-speed audit: max_rpm %.0f/%.0f ratio %.4f, hover %.0f, low_dyn %.0frpm %.3fx hover speed_p95 %.2fm/s bpf %.1fHz, p95_file %.0f, bpass %.1fHz/%.1fHz, bpass_nyq %.1fx, files %d@%.1fHz%n",
				aiioRotorSpeedAudit.referenceMaxRotorRpm(),
				aiioRotorSpeedAudit.configuredMaxRotorRpm(),
				aiioRotorSpeedAudit.referenceMaxRotorRpmOverConfiguredMax(),
				aiioRotorSpeedAudit.configuredHoverRotorRpm(),
				aiioRotorSpeedAudit.lowDynamicRotorRpmMean(),
				aiioRotorSpeedAudit.lowDynamicRotorRpmMeanOverConfiguredHover(),
				aiioRotorSpeedAudit.lowDynamicSpeedP95MetersPerSecond(),
				aiioRotorSpeedAudit.lowDynamicMeanBladePassHertzForConfiguredBladeCount(),
				aiioRotorSpeedAudit.referenceRotorRpmP95OfFilePeaks(),
				aiioRotorSpeedAudit.referenceBladePassHertzForConfiguredBladeCount(),
				aiioRotorSpeedAudit.configuredMaxBladePassHertz(),
				aiioRotorSpeedAudit.referenceBladePassOverTelemetryNyquist(),
				aiioRotorSpeedAudit.referenceSampleFileCount(),
				aiioRotorSpeedAudit.referenceSampleRateHertz()
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone urban motor-RPM audit: %s files %d valid %.1f%% cmd %.3f/%.3f/%.3f thr %.3f/%.3f rpm %.0f/%.0f/%.0f thr_rpm %.0f/%.0f->%.0f/%.0f thr_ratio %.2f/%.2f cfg %.0f/%.0f rpm_ratio hover %.2f/%.2f max %.2f/%.2f erpm100 %.1f/%.1f kv %.0f/%.0f fit %.0frpm_per_norm %.0frpm r2 %.3f power %.3f@%.3f r2 %.3f spread %.0f/%.0frpm bpf %.1f/%.1fHz%n",
				apDroneUrbanMotorRpmAudit.referenceId(),
				apDroneUrbanMotorRpmAudit.sourceFileCount(),
				apDroneUrbanMotorRpmAudit.validErpmFraction() * 100.0,
				apDroneUrbanMotorRpmAudit.motorCommandP50(),
				apDroneUrbanMotorRpmAudit.motorCommandP95(),
				apDroneUrbanMotorRpmAudit.motorCommandP99(),
				apDroneUrbanMotorRpmAudit.flightThrottleP50(),
				apDroneUrbanMotorRpmAudit.flightThrottleP95(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmP50(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmP95(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmMaxSampled(),
				apDroneUrbanMotorRpmAudit.projectRpmAtFlightThrottleP50(),
				apDroneUrbanMotorRpmAudit.projectRpmAtFlightThrottleP95(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmAtFlightThrottleP50(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmAtFlightThrottleP95(),
				apDroneUrbanMotorRpmAudit.sampledMechanicalOverProjectThrottleRpmP50(),
				apDroneUrbanMotorRpmAudit.sampledMechanicalOverProjectThrottleRpmP95(),
				apDroneUrbanMotorRpmAudit.configuredHoverRpm(),
				apDroneUrbanMotorRpmAudit.configuredMaxRpm(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmP50OverConfiguredHover(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmP95OverConfiguredHover(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmP95OverConfiguredMax(),
				apDroneUrbanMotorRpmAudit.mechanicalRpmMaxOverConfiguredMax(),
				apDroneUrbanMotorRpmAudit.configuredHoverLoggedErpm100(),
				apDroneUrbanMotorRpmAudit.configuredMaxLoggedErpm100(),
				apDroneUrbanMotorRpmAudit.effectiveKvRpmPerVoltP50(),
				apDroneUrbanMotorRpmAudit.effectiveKvRpmPerVoltP95(),
				apDroneUrbanMotorRpmAudit.linearFitSlopeRpmPerNorm(),
				apDroneUrbanMotorRpmAudit.linearFitInterceptRpm(),
				apDroneUrbanMotorRpmAudit.linearFitR2(),
				apDroneUrbanMotorRpmAudit.powerFitExponent(),
				apDroneUrbanMotorRpmAudit.powerFitScaleRpmFractionAtNorm1(),
				apDroneUrbanMotorRpmAudit.powerFitR2Log(),
				apDroneUrbanMotorRpmAudit.motorP50RpmSpread(),
				apDroneUrbanMotorRpmAudit.motorP95RpmSpread(),
				apDroneUrbanMotorRpmAudit.measuredP95BladePassHertz(),
				apDroneUrbanMotorRpmAudit.configuredMaxBladePassHertz()
		);
		System.out.printf(
				Locale.ROOT,
				"Motor thermal audit: %s rows %d sources %d U8max %.1fC %.1fW MQTB %s %.0fkv %.1fg %s %.0fA, preset rise/cool %.1fCps/%.3fs^-1 limit/cut %.0f/%.0fC esc %.0f/%.0fC tc %.1f/%.1f/%.1fs cooling %.2f/%.2f/%.2f, rise full/hover/esc %.1f/%.1f/%.1fC abs25 %.1f/%.1f/%.1fC, elec kv %.0frpmV kt %.4fNmA current %.1f/%.1fA limit %.1fA %.2fx phase %.1fA head %.3f stress %.3f, copper95/125 %.3f/%.3f mid %.3f cross U8 %.2fx hover %.2fx mqtb %.2fkv %.2fesc caveat cross-class-U8-no-FPV-thermocouple%n",
				motorThermalAudit.sourceId(),
				motorThermalAudit.rowTypeCounts().totalRowCount(),
				motorThermalAudit.rowTypeCounts().sourceInventoryRowCount(),
				motorThermalAudit.u8Dyno36v().temperatureMaxCelsius(),
				motorThermalAudit.u8Dyno36v().lossMaxWatts(),
				motorThermalAudit.mqtbMetadata().referenceMotor(),
				motorThermalAudit.mqtbMetadata().testedKvRpmPerVolt(),
				motorThermalAudit.mqtbMetadata().motorWeightGrams(),
				motorThermalAudit.mqtbMetadata().esc(),
				motorThermalAudit.mqtbMetadata().escCurrentRatingAmps(),
				motorThermalAudit.preset().thermalRiseCelsiusPerSecond(),
				motorThermalAudit.preset().coolingRatePerSecond(),
				motorThermalAudit.preset().motorLimitCelsius(),
				motorThermalAudit.preset().motorCutoffCelsius(),
				motorThermalAudit.preset().escLimitCelsius(),
				motorThermalAudit.preset().escCutoffCelsius(),
				motorThermalAudit.preset().motorBaseTimeConstantSeconds(),
				motorThermalAudit.preset().motorFullWashTimeConstantSeconds(),
				motorThermalAudit.preset().motorFullTenMetersPerSecondTimeConstantSeconds(),
				motorThermalAudit.preset().hoverMotorCoolingFactorProxy(),
				motorThermalAudit.preset().fullMotorCoolingFactorProxy(),
				motorThermalAudit.preset().fullTenMetersPerSecondMotorCoolingFactorProxy(),
				motorThermalAudit.preset().motorFullSteadyRiseCelsius(),
				motorThermalAudit.preset().motorHoverSteadyRiseProxyCelsius(),
				motorThermalAudit.preset().escFullCurrentSteadyRiseProxyCelsius(),
				motorThermalAudit.racingQuadCrosscheck().currentFullPowerMotorAbsoluteTempAt25c(),
				motorThermalAudit.racingQuadCrosscheck().currentHoverMotorAbsoluteTempAt25c(),
				motorThermalAudit.racingQuadCrosscheck().currentFullEscAbsoluteTempAt25c(),
				motorThermalAudit.electricalStress().inferredKvRpmPerVolt(),
				motorThermalAudit.electricalStress().torqueConstantNewtonMetersPerAmp(),
				motorThermalAudit.electricalStress().hoverPowerCurrentAmps(),
				motorThermalAudit.electricalStress().maxPowerCurrentAmps(),
				motorThermalAudit.electricalStress().perMotorCurrentLimitAmps(),
				motorThermalAudit.electricalStress().maxPowerCurrentOverLimit(),
				motorThermalAudit.electricalStress().maxPhaseCurrentProxyAmps(),
				motorThermalAudit.electricalStress().maxVoltageHeadroom(),
				motorThermalAudit.electricalStress().maxDesyncHeadroomStress(),
				motorThermalAudit.preset().windingResistanceScaleAtLimit(),
				motorThermalAudit.preset().windingResistanceScaleAtCutoff(),
				motorThermalAudit.preset().motorLimitScaleAtMidpoint(),
				motorThermalAudit.racingQuadCrosscheck().currentFullMotorRiseOverU8MaxRise(),
				motorThermalAudit.racingQuadCrosscheck().currentHoverMotorRiseOverU8MaxRise(),
				motorThermalAudit.racingQuadCrosscheck().currentInferredKvOverMqtbTestedKv(),
				motorThermalAudit.racingQuadCrosscheck().currentPerMotorLimitOverMqtbEscRating()
		);
		System.out.printf(
				Locale.ROOT,
				"Nanodrone sysid audit: %s rows %d source %d schema %d files %d samples %d@%.0fHz train/test %d/%d horizon %.2fs, model %.3fkg arm %.3fm Kt %.3e Kc %.3e Tmax %.3fN TW%.1f, thrust source/train/test R2 %.3f/%.3f/%.3f rmse %.4f/%.4f/%.4fN fitKt/source %.4f, torque fit/source roll/pitch/yaw %.3f/%.3f/%.3f testR2 %.3f/%.3f/%.3f, cfg Kt %.3e nanoKt %.3fx hover/max %.0f/%.0frad_s nano p95/max %.2fx/%.2fx TW %.2f/%.2f/%.2f caveat nano-not-fpv-scale%n",
				nanodroneSysIdAudit.sourceId(),
				nanodroneSysIdAudit.rowTypeCounts().totalRowCount(),
				nanodroneSysIdAudit.rowTypeCounts().sourceInventoryRowCount(),
				nanodroneSysIdAudit.rowTypeCounts().columnSchemaRowCount(),
				nanodroneSysIdAudit.sourceData().actualCsvFileCount(),
				nanodroneSysIdAudit.sourceData().actualLoadedSampleCount(),
				nanodroneSysIdAudit.sourceData().sampleRateHertz(),
				nanodroneSysIdAudit.sourceData().trainSampleCount(),
				nanodroneSysIdAudit.sourceData().testSampleCount(),
				nanodroneSysIdAudit.sourceData().benchmarkOpenLoopHorizonSeconds(),
				nanodroneSysIdAudit.referenceModel().massKg(),
				nanodroneSysIdAudit.referenceModel().armLengthMeters(),
				nanodroneSysIdAudit.referenceModel().sourceKtNewtonsPerRadianPerSecondSquared(),
				nanodroneSysIdAudit.referenceModel().sourceKcNewtonMetersPerRadianPerSecondSquared(),
				nanodroneSysIdAudit.referenceModel().sourceTmaxNewtons(),
				nanodroneSysIdAudit.referenceModel().sourceThrustToWeight(),
				nanodroneSysIdAudit.sourceThrustFit().r2(),
				nanodroneSysIdAudit.trainThrustFit().trainR2(),
				nanodroneSysIdAudit.trainThrustFit().testR2(),
				nanodroneSysIdAudit.sourceThrustFit().rmse(),
				nanodroneSysIdAudit.trainThrustFit().trainRmse(),
				nanodroneSysIdAudit.trainThrustFit().testRmse(),
				nanodroneSysIdAudit.trainThrustFit().trainFitCoefficientOverSource(),
				nanodroneSysIdAudit.trainRollTorqueFit().trainFitCoefficientOverSource(),
				nanodroneSysIdAudit.trainPitchTorqueFit().trainFitCoefficientOverSource(),
				nanodroneSysIdAudit.trainYawTorqueFit().trainFitCoefficientOverSource(),
				nanodroneSysIdAudit.trainRollTorqueFit().testR2(),
				nanodroneSysIdAudit.trainPitchTorqueFit().testR2(),
				nanodroneSysIdAudit.trainYawTorqueFit().testR2(),
				nanodroneSysIdAudit.currentScale().configuredAverageRotorThrustCoefficient(),
				nanodroneSysIdAudit.currentScale().sourceKtOverConfiguredRotorThrustCoefficient(),
				nanodroneSysIdAudit.currentScale().configuredHoverRotorRadiansPerSecond(),
				nanodroneSysIdAudit.currentScale().configuredMaxRotorRadiansPerSecond(),
				nanodroneSysIdAudit.currentScale().nanodroneMotorP95OverConfiguredHover(),
				nanodroneSysIdAudit.currentScale().nanodroneMotorMaxOverConfiguredMax(),
				nanodroneSysIdAudit.currentScale().nanodroneSourceKtThrustToWeightP50(),
				nanodroneSysIdAudit.currentScale().nanodroneSourceKtThrustToWeightP95(),
				nanodroneSysIdAudit.currentScale().nanodroneSourceKtThrustToWeightMax()
		);
		System.out.printf(
				Locale.ROOT,
				"Motor response dynamics audit: %s rows %d, RotorS/PX4 ref up/down/inflow %.1f/%.1f/%.1fms racing %.1fx/%.1fx inflow %.1fx, %s BF50 slew pos/neg %.0f/%.0frpm/s log_p50 %.0frpm/s tau %.1f/%.1fms decoded_max %.0frpm, %s lag level %.1f/%.1f/%.1fms delta %.1f/%.1f/%.1fms tau50 %.2fms tau90max %.1fms valid %.1f%%, preset tau %.1fms esc %.0fHz %.2fms brake %.2f brakeTau %.1fms slew %.0f/%.0frpm/s obs_ratio %.2f/%.2f tau_ratio %.2f/%.2f cfg_rpm %.0f/%.0f %.2fx ap_tau %.2f/%.2f frame_delta %.3fx%n",
				motorResponseAudit.sourceId(),
				motorResponseAudit.packetRowCount(),
				motorResponseAudit.rotorSPx4Reference().motorSpinupReferenceTauSeconds() * 1000.0,
				motorResponseAudit.rotorSPx4Reference().motorSpindownReferenceTauSeconds() * 1000.0,
				motorResponseAudit.rotorSPx4Reference().rotorInflowReferenceTauSeconds() * 1000.0,
				motorResponseAudit.rotorSPx4Reference().racingQuadMotorTauOverSpinupReference(),
				motorResponseAudit.rotorSPx4Reference().racingQuadMotorTauOverSpindownReference(),
				motorResponseAudit.rotorSPx4Reference().racingQuadInflowTauOverReference(),
				motorResponseAudit.betaflightRpmSlewReference().referenceId(),
				motorResponseAudit.betaflightRpmSlewReference().observedMaxPositive50msSlewRpmPerSecond(),
				motorResponseAudit.betaflightRpmSlewReference().observedMaxNegative50msSlewRpmPerSecond(),
				motorResponseAudit.betaflightRpmSlewReference().logLevelPositiveSlewP50RpmPerSecond(),
				motorResponseAudit.betaflightRpmSlewReference().observedPositiveTauEquivalentSeconds() * 1000.0,
				motorResponseAudit.betaflightRpmSlewReference().observedNegativeTauEquivalentSeconds() * 1000.0,
				motorResponseAudit.betaflightRpmSlewReference().decodedRpmMaxAcrossMotors(),
				motorResponseAudit.apDroneUrbanRpmLagReference().referenceId(),
				motorResponseAudit.apDroneUrbanRpmLagReference().commandRpmLevelLagP10Milliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().commandRpmLevelLagP50Milliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().commandRpmLevelLagP90Milliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().commandRpmDeltaLagP10Milliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().commandRpmDeltaLagP50Milliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().commandRpmDeltaLagP90Milliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().firstOrderTauP50AcrossFilesP50Milliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().firstOrderTauP90AcrossFilesMaxMilliseconds(),
				motorResponseAudit.apDroneUrbanRpmLagReference().validErpmFractionMin() * 100.0,
				motorResponseAudit.preset().motorTimeConstantSeconds() * 1000.0,
				motorResponseAudit.preset().escFrameRateHertz(),
				motorResponseAudit.preset().escCommandFrameIntervalMilliseconds(),
				motorResponseAudit.preset().activeBrakingStrength(),
				motorResponseAudit.preset().activeBrakingTauProxySeconds() * 1000.0,
				motorResponseAudit.preset().nominalSpinupSlewRpmPerSecond(),
				motorResponseAudit.preset().activeBrakingSlewProxyRpmPerSecond(),
				motorResponseAudit.preset().observedPositiveSlewOverNominalSpinupProxy(),
				motorResponseAudit.preset().observedNegativeSlewOverActiveBrakingProxy(),
				motorResponseAudit.preset().observedPositiveTauOverMotorTau(),
				motorResponseAudit.preset().observedNegativeTauOverActiveBrakingTauProxy(),
				motorResponseAudit.preset().averageHoverRotorRpm(),
				motorResponseAudit.preset().averageMaxRotorRpm(),
				motorResponseAudit.preset().configuredMaxRpmOverBetaflightDecodedMaxRpm(),
				motorResponseAudit.preset().motorTauOverApDroneUrbanFirstOrderTauP50(),
				motorResponseAudit.preset().motorTauOverApDroneUrbanLevelLagP50(),
				motorResponseAudit.preset().escFrameIntervalOverApDroneUrbanDeltaLagP50()
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone control-response audit: %s lag_p50 roll/pitch/yaw %.1f/%.1f/%.1fms cfg_control %.1fms cfg+rc %.1fms smoothing %.1fms frame rc/esc %.2f/%.2fms ratios_ctrl %.2f/%.2f/%.2f ratios_total %.2f/%.2f/%.2f corr_p50 %.3f/%.3f/%.3f gain_p50 %.3f/%.3f/%.3f mae_p50 %.1f/%.1f/%.1fdps reliable %d/%d/%d%n",
				controlResponseAudit.sourceId(),
				controlResponseAudit.roll().lagP50Milliseconds(),
				controlResponseAudit.pitch().lagP50Milliseconds(),
				controlResponseAudit.yaw().lagP50Milliseconds(),
				controlResponseAudit.roll().configuredControlLatencyMilliseconds(),
				controlResponseAudit.roll().configuredControlPlusRcLatencyMilliseconds(),
				controlResponseAudit.roll().configuredRcSmoothingTauMilliseconds(),
				controlResponseAudit.roll().rcFrameIntervalMilliseconds(),
				controlResponseAudit.roll().escFrameIntervalMilliseconds(),
				controlResponseAudit.roll().p50LagOverControlLatency(),
				controlResponseAudit.pitch().p50LagOverControlLatency(),
				controlResponseAudit.yaw().p50LagOverControlLatency(),
				controlResponseAudit.roll().p50LagOverControlPlusRcLatency(),
				controlResponseAudit.pitch().p50LagOverControlPlusRcLatency(),
				controlResponseAudit.yaw().p50LagOverControlPlusRcLatency(),
				controlResponseAudit.roll().absCorrelationP50(),
				controlResponseAudit.pitch().absCorrelationP50(),
				controlResponseAudit.yaw().absCorrelationP50(),
				controlResponseAudit.roll().gainP50(),
				controlResponseAudit.pitch().gainP50(),
				controlResponseAudit.yaw().gainP50(),
				controlResponseAudit.roll().maeDegreesPerSecondP50(),
				controlResponseAudit.pitch().maeDegreesPerSecondP50(),
				controlResponseAudit.yaw().maeDegreesPerSecondP50(),
				controlResponseAudit.roll().reliableRowCount(),
				controlResponseAudit.pitch().reliableRowCount(),
				controlResponseAudit.yaw().reliableRowCount()
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone PID tuning audit: %s PI/P roll/pitch/yaw %.3f/%.3f/%.3f PID/PI %.2f/%.2f/%.2f dumpD/best %.2f/%.2f/%.2f dmin_match %s/%s/%s best %s; %s; %s%n",
				pidTuningAudit.sourceId(),
				pidTuningAudit.roll().piMaeOverPOnlyMae(),
				pidTuningAudit.pitch().piMaeOverPOnlyMae(),
				pidTuningAudit.yaw().piMaeOverPOnlyMae(),
				pidTuningAudit.roll().pidMaeOverPiMae(),
				pidTuningAudit.pitch().pidMaeOverPiMae(),
				pidTuningAudit.yaw().pidMaeOverPiMae(),
				pidTuningAudit.roll().betaflightConfigKdOverBestKd(),
				pidTuningAudit.pitch().betaflightConfigKdOverBestKd(),
				pidTuningAudit.yaw().betaflightConfigKdOverBestKd(),
				pidTuningAudit.roll().betaflightConfigDMinMatchesBestKd(),
				pidTuningAudit.pitch().betaflightConfigDMinMatchesBestKd(),
				pidTuningAudit.yaw().betaflightConfigDMinMatchesBestKd(),
				formatPidTuningAxis(pidTuningAudit.roll()),
				formatPidTuningAxis(pidTuningAudit.pitch()),
				formatPidTuningAxis(pidTuningAudit.yaw())
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone rate-envelope audit: %s %s type %s rc_rate %.1f center %.1f/%.1fdps max %.1f/%.1fdps dump %.1fdps limit %.0fdps cfg/limit %.2f expo %.2f super %.3f stick25/50/75 %.1f/%.1f/%.1fdps ref %.1f/%.1f/%.1fdps%n",
				rateEnvelopeAudit.sourceId(),
				rateEnvelopeAudit.selection(),
				rateEnvelopeAudit.betaflightRatesTypeName(),
				rateEnvelopeAudit.betaflightActualRcRate(),
				rateEnvelopeAudit.roll().configuredCenterSensitivityDegreesPerSecond(),
				rateEnvelopeAudit.referenceCenterSensitivityDegreesPerSecond(),
				rateEnvelopeAudit.roll().configuredMaxRateDegreesPerSecond(),
				rateEnvelopeAudit.selectedReferenceMaxRateDegreesPerSecond(),
				rateEnvelopeAudit.dumpOpenFieldReferenceMaxRateDegreesPerSecond(),
				rateEnvelopeAudit.betaflightRateLimitDegreesPerSecond(),
				rateEnvelopeAudit.roll().configuredMaxOverBetaflightRateLimit(),
				rateEnvelopeAudit.roll().configuredRateExpo(),
				rateEnvelopeAudit.roll().configuredRateSuper(),
				rateEnvelopeAudit.roll().configuredRateAtStick25DegreesPerSecond(),
				rateEnvelopeAudit.roll().configuredRateAtStick50DegreesPerSecond(),
				rateEnvelopeAudit.roll().configuredRateAtStick75DegreesPerSecond(),
				rateEnvelopeAudit.roll().selectedReferenceRateAtStick25DegreesPerSecond(),
				rateEnvelopeAudit.roll().selectedReferenceRateAtStick50DegreesPerSecond(),
				rateEnvelopeAudit.roll().selectedReferenceRateAtStick75DegreesPerSecond()
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone IMU noise audit: %s %s %dseg/%dfiles %.1fs gyro cfg %.5frad/s p50 %.5f p90 %.5f cfg/p90 %.2f strict %.5f cfg/strict %.2f accel cfg %.4fm/s2 p50 %.4f p90 %.4f cfg/p90 %.2f strict %.4f cfg/strict %.2f lpf %.0f/%.0fHz baro_quiet %.4fm rms %.4fm%n",
				imuNoiseAudit.sourceId(),
				imuNoiseAudit.lowMotionSelection(),
				imuNoiseAudit.lowMotionSegmentCount(),
				imuNoiseAudit.lowMotionSourceFileCount(),
				imuNoiseAudit.lowMotionDurationSeconds(),
				imuNoiseAudit.configuredGyroNoiseRadiansPerSecond(),
				imuNoiseAudit.lowMotionGyroVectorRmsP50RadiansPerSecond(),
				imuNoiseAudit.lowMotionGyroVectorRmsP90RadiansPerSecond(),
				imuNoiseAudit.configuredGyroOverLowMotionP90(),
				imuNoiseAudit.strictStaticGyroVectorRmsRadiansPerSecond(),
				imuNoiseAudit.configuredGyroOverStrictStatic(),
				imuNoiseAudit.configuredAccelerometerNoiseMetersPerSecondSquared(),
				imuNoiseAudit.lowMotionAccelerometerVectorRmsP50MetersPerSecondSquared(),
				imuNoiseAudit.lowMotionAccelerometerVectorRmsP90MetersPerSecondSquared(),
				imuNoiseAudit.configuredAccelerometerOverLowMotionP90(),
				imuNoiseAudit.strictStaticAccelerometerVectorRmsMetersPerSecondSquared(),
				imuNoiseAudit.configuredAccelerometerOverStrictStatic(),
				imuNoiseAudit.configuredGyroLowPassHertz(),
				imuNoiseAudit.configuredAccelerometerLowPassHertz(),
				imuNoiseAudit.configuredQuietBarometerNoiseAmplitudeMeters(),
				imuNoiseAudit.configuredQuietBarometerNoiseRmsMeters()
		);
		System.out.printf(
				Locale.ROOT,
				"APDrone barometer noise audit: %s %s %dseg/%dfiles %.1fs detrended_p50/p90 %.4f/%.4fm cfg_rms %.4fm cfg/p50 %.2f cfg/p90 %.2f strict %.4fm cfg/strict %.2f dps310 %.4fm cfg/dps %.2f peak_pp_p50/p90 %.2f/%.2fm slope_p50/p90 %.3f/%.3fm/s tau %.2f/%.2fs%n",
				barometerNoiseAudit.sourceId(),
				barometerNoiseAudit.lowMotionSelection(),
				barometerNoiseAudit.lowMotionSegmentCount(),
				barometerNoiseAudit.lowMotionSourceFileCount(),
				barometerNoiseAudit.lowMotionDurationSeconds(),
				barometerNoiseAudit.lowMotionDetrendedStdP50Meters(),
				barometerNoiseAudit.lowMotionDetrendedStdP90Meters(),
				barometerNoiseAudit.configuredQuietBarometerNoiseRmsMeters(),
				barometerNoiseAudit.configuredRmsOverLowMotionDetrendedP50(),
				barometerNoiseAudit.configuredRmsOverLowMotionDetrendedP90(),
				barometerNoiseAudit.strictStaticDetrendedStdMeters(),
				barometerNoiseAudit.configuredRmsOverStrictStaticDetrended(),
				barometerNoiseAudit.dps310PressureNoiseAltitudeMeters(),
				barometerNoiseAudit.configuredRmsOverDps310PressureNoise(),
				barometerNoiseAudit.lowMotionPeakToPeakP50Meters(),
				barometerNoiseAudit.lowMotionPeakToPeakP90Meters(),
				barometerNoiseAudit.lowMotionAbsSlopeP50MetersPerSecond(),
				barometerNoiseAudit.lowMotionAbsSlopeP90MetersPerSecond(),
				barometerNoiseAudit.configuredAltitudeTimeConstantSeconds(),
				barometerNoiseAudit.configuredVerticalSpeedTimeConstantSeconds()
		);
		System.out.printf(
				Locale.ROOT,
				"Prop damage vibration audit: single_fault rotor %d damage %.2f gyro %.2fx ref %.2fx, accel %.2fx ref %.2fx padre %.2f..%.2fx, dvib %.3f rotor_vib %.3f/%.3f samples %d@%.1fs%n",
				propDamageAudit.damagedRotorIndex(),
				propDamageAudit.rotorDamageAmount(),
				propDamageAudit.gyroDynamicRmsRatio(),
				propDamageAudit.referenceSingleBrokenGyroRmsRatio(),
				propDamageAudit.accelerometerDynamicRmsRatio(),
				propDamageAudit.referenceSingleBrokenAccelerometerRmsRatio(),
				propDamageAudit.padreSingleRotorAccelerometerFeatureRmsRatio(),
				propDamageAudit.padreTwoPositionAccelerometerFeatureRmsRatio(),
				propDamageAudit.maxDamagedRotorDamageVibration(),
				propDamageAudit.maxHealthyRotorVibration(),
				propDamageAudit.maxDamagedRotorVibration(),
				propDamageAudit.sampleCount(),
				propDamageAudit.sampledSeconds()
		);
		if ("apdrone".equals(presetName)) {
			BatteryAutonomyEstimate[] autonomy = apDroneBatteryAutonomyEstimates(preset);
			BatteryVoltageDropAudit voltageDrop = apDroneBatteryVoltageDropAudit(preset);
			ReferenceSpeedEnvelopeEstimate[] speedEnvelope = apDroneOpenFieldSpeedEnvelopeAudit(preset);
			System.out.printf(
					Locale.ROOT,
					"APDrone battery-autonomy audit: %s sim %.1fs/%.1fs ref %.1fs ratio %.2f current %.1fA/%.1fA ref %.1fA equiv_direct %.3f match %.1fA log/direct %.1fx; %s sim %.1fs/%.1fs ref %.1fs ratio %.2f current %.1fA/%.1fA ref %.1fA equiv_direct %.3f match %.1fA log/direct %.1fx%n",
					autonomy[0].scenario(),
					autonomy[0].simulatedDurationSeconds(),
					autonomy[0].simulatedTimeInVoltageWindowSeconds(),
					autonomy[0].referenceDurationSeconds(),
					autonomy[0].durationRatio(),
					autonomy[0].meanCurrentAmps(),
					autonomy[0].peakCurrentAmps(),
					autonomy[0].referenceMeanCurrentAmps(),
					autonomy[0].currentMatchedDirectThrottleCommand(),
					autonomy[0].currentMatchedMeanCurrentAmps(),
					autonomy[0].referenceThrottleToCurrentMatchedDirectThrottleRatio(),
					autonomy[1].scenario(),
					autonomy[1].simulatedDurationSeconds(),
					autonomy[1].simulatedTimeInVoltageWindowSeconds(),
					autonomy[1].referenceDurationSeconds(),
					autonomy[1].durationRatio(),
					autonomy[1].meanCurrentAmps(),
					autonomy[1].peakCurrentAmps(),
					autonomy[1].referenceMeanCurrentAmps(),
					autonomy[1].currentMatchedDirectThrottleCommand(),
					autonomy[1].currentMatchedMeanCurrentAmps(),
					autonomy[1].referenceThrottleToCurrentMatchedDirectThrottleRatio()
			);
			System.out.printf(
					Locale.ROOT,
					"APDrone ESR voltage-drop audit: config %.1fmOhm cross_proxy %.1fmOhm cfg/proxy %.2f sag_delta %.3fV obs_delta %.3fV obs/config %.2f; start_proxy normal %.1fmOhm max %.1fmOhm; p95_sag normal %.3fV max %.3fV%n",
					voltageDrop.configuredResistanceMilliohms(),
					voltageDrop.inferredResistanceProxyMilliohms(),
					voltageDrop.configuredOverInferredProxy(),
					voltageDrop.configuredSagDelta(),
					voltageDrop.observedMeanVoltageDelta(),
					voltageDrop.observedMeanVoltageDeltaOverConfiguredSagDelta(),
					voltageDrop.normalStartDropResistanceProxyMilliohms(),
					voltageDrop.maxStartDropResistanceProxyMilliohms(),
					voltageDrop.normalConfiguredSagAtP95Current(),
					voltageDrop.maxConfiguredSagAtP95Current()
			);
			System.out.printf(
					Locale.ROOT,
					"APDrone open-field speed-envelope audit: %s %.2fm/s %s drag %.2fN margin %.1fN limit %.1fm/s speed/limit %.2f; %s %.2fm/s %s drag %.2fN margin %.1fN limit %.1fm/s speed/limit %.2f; %s %.2fm/s %s drag %.2fN margin %.1fN limit %.1fm/s speed/limit %.2f%n",
					speedEnvelope[0].speedPoint(),
					speedEnvelope[0].referenceSpeedMetersPerSecond(),
					speedEnvelope[0].axis().name(),
					speedEnvelope[0].baseDragForceNewtons(),
					speedEnvelope[0].horizontalThrustMarginNewtons(),
					speedEnvelope[0].dragLimitedLevelSpeedMetersPerSecond(),
					speedEnvelope[0].speedOverDragLimitedLevelSpeed(),
					speedEnvelope[2].speedPoint(),
					speedEnvelope[2].referenceSpeedMetersPerSecond(),
					speedEnvelope[2].axis().name(),
					speedEnvelope[2].baseDragForceNewtons(),
					speedEnvelope[2].horizontalThrustMarginNewtons(),
					speedEnvelope[2].dragLimitedLevelSpeedMetersPerSecond(),
					speedEnvelope[2].speedOverDragLimitedLevelSpeed(),
					speedEnvelope[3].speedPoint(),
					speedEnvelope[3].referenceSpeedMetersPerSecond(),
					speedEnvelope[3].axis().name(),
					speedEnvelope[3].baseDragForceNewtons(),
					speedEnvelope[3].horizontalThrustMarginNewtons(),
					speedEnvelope[3].dragLimitedLevelSpeedMetersPerSecond(),
					speedEnvelope[3].speedOverDragLimitedLevelSpeed()
			);
		}
	}

	private static String formatLevelFlightRequirement(
			String label,
			AirframeDragCalibration.LevelFlightRequirement requirement
	) {
		return String.format(
				Locale.ROOT,
				"%s %.2fm/s %s drag %.2fN %.0f%%margin thrust %.2fmax tilt %.1fdeg %s",
				label,
				requirement.speedMetersPerSecond(),
				requirement.axis().name(),
				requirement.baseDragForceNewtons(),
				requirement.dragToHorizontalMarginRatio() * 100.0,
				requirement.requiredMaxThrustFraction(),
				requirement.requiredTiltDegrees(),
				requirement.reachable() ? "reachable" : "over-thrust"
		);
	}

	private static String formatPidTuningAxis(PidTuningCalibration.AxisPidTuningAudit axis) {
		return String.format(
				Locale.ROOT,
				"%s P%.0f %.1fMAE PI%.0f/%.0f %.2fMAE PID_D%.0f %.2fMAE dumpD%.0f dmin%.0f",
				axis.axis(),
				axis.bestPOnlyKp(),
				axis.bestPOnlyMae(),
				axis.bestPiKp(),
				axis.bestPiKi(),
				axis.bestPiMae(),
				axis.bestPidKd(),
				axis.bestPidMae(),
				axis.betaflightConfigKd(),
				axis.betaflightConfigDMin()
		);
	}

	public static FlightReport record(String presetName, Path outputPath) throws IOException {
		return record(presetName, outputPath, DEFAULT_DURATION_SECONDS);
	}

	public static BatteryAutonomyEstimate[] apDroneBatteryAutonomyEstimates() {
		return apDroneBatteryAutonomyEstimates(DroneConfig.apDrone());
	}

	public static BatteryAutonomyEstimate[] apDroneBatteryAutonomyEstimates(DroneConfig config) {
		return new BatteryAutonomyEstimate[] {
				estimateStaticBatteryAutonomy(
						config,
						"max_power",
						APDRONE_MAX_POWER_REFERENCE_THROTTLE,
						APDRONE_AUTONOMY_LOWER_VOLTAGE,
						APDRONE_AUTONOMY_UPPER_VOLTAGE,
						APDRONE_MAX_POWER_REFERENCE_SECONDS,
						APDRONE_MAX_POWER_REFERENCE_MEAN_CURRENT_AMPS,
						BATTERY_AUTONOMY_MAX_SECONDS
				),
				estimateStaticBatteryAutonomy(
						config,
						"normal_power",
						APDRONE_NORMAL_POWER_REFERENCE_THROTTLE,
						APDRONE_AUTONOMY_LOWER_VOLTAGE,
						APDRONE_AUTONOMY_UPPER_VOLTAGE,
						APDRONE_NORMAL_POWER_REFERENCE_SECONDS,
						APDRONE_NORMAL_POWER_REFERENCE_MEAN_CURRENT_AMPS,
						BATTERY_AUTONOMY_MAX_SECONDS
				)
		};
	}

	public static BatteryVoltageDropAudit apDroneBatteryVoltageDropAudit() {
		return apDroneBatteryVoltageDropAudit(DroneConfig.apDrone());
	}

	public static BatteryVoltageDropAudit apDroneBatteryVoltageDropAudit(DroneConfig config) {
		double configuredResistance = config.batteryInternalResistanceOhms();
		double normalMeanCurrent = APDRONE_NORMAL_POWER_REFERENCE_MEAN_CURRENT_AMPS;
		double maxMeanCurrent = APDRONE_MAX_POWER_REFERENCE_MEAN_CURRENT_AMPS;
		double deltaCurrent = maxMeanCurrent - normalMeanCurrent;
		double normalMeanVoltage = APDRONE_NORMAL_POWER_REFERENCE_MEAN_VOLTAGE;
		double maxMeanVoltage = APDRONE_MAX_POWER_REFERENCE_MEAN_VOLTAGE;
		double observedMeanVoltageDelta = normalMeanVoltage - maxMeanVoltage;
		double normalConfiguredSagAtMeanCurrent = normalMeanCurrent * configuredResistance;
		double maxConfiguredSagAtMeanCurrent = maxMeanCurrent * configuredResistance;
		double configuredSagDelta = maxConfiguredSagAtMeanCurrent - normalConfiguredSagAtMeanCurrent;
		double normalConfiguredSagAtP95Current =
				APDRONE_NORMAL_POWER_REFERENCE_P95_CURRENT_AMPS * configuredResistance;
		double maxConfiguredSagAtP95Current =
				APDRONE_MAX_POWER_REFERENCE_P95_CURRENT_AMPS * configuredResistance;
		double inferredResistanceProxy = observedMeanVoltageDelta / Math.max(1.0e-9, deltaCurrent);
		double normalStartDropResistanceProxy = (config.nominalBatteryVoltage()
				- APDRONE_NORMAL_POWER_REFERENCE_START_VOLTAGE) / Math.max(1.0e-9, normalMeanCurrent);
		double maxStartDropResistanceProxy = (config.nominalBatteryVoltage()
				- APDRONE_MAX_POWER_REFERENCE_START_VOLTAGE) / Math.max(1.0e-9, maxMeanCurrent);

		return new BatteryVoltageDropAudit(
				configuredResistance,
				normalMeanCurrent,
				APDRONE_NORMAL_POWER_REFERENCE_P95_CURRENT_AMPS,
				maxMeanCurrent,
				APDRONE_MAX_POWER_REFERENCE_P95_CURRENT_AMPS,
				deltaCurrent,
				APDRONE_NORMAL_POWER_REFERENCE_START_VOLTAGE,
				APDRONE_MAX_POWER_REFERENCE_START_VOLTAGE,
				normalMeanVoltage,
				maxMeanVoltage,
				observedMeanVoltageDelta,
				normalConfiguredSagAtMeanCurrent,
				maxConfiguredSagAtMeanCurrent,
				configuredSagDelta,
				normalConfiguredSagAtP95Current,
				maxConfiguredSagAtP95Current,
				inferredResistanceProxy,
				configuredResistance / Math.max(1.0e-9, inferredResistanceProxy),
				observedMeanVoltageDelta / Math.max(1.0e-9, configuredSagDelta),
				normalStartDropResistanceProxy,
				maxStartDropResistanceProxy,
				configuredResistance / Math.max(1.0e-9, normalStartDropResistanceProxy),
				configuredResistance / Math.max(1.0e-9, maxStartDropResistanceProxy)
		);
	}

	public static ReferenceSpeedEnvelopeEstimate[] apDroneOpenFieldSpeedEnvelopeAudit() {
		return apDroneOpenFieldSpeedEnvelopeAudit(DroneConfig.apDrone());
	}

	public static ReferenceSpeedEnvelopeEstimate[] apDroneOpenFieldSpeedEnvelopeAudit(DroneConfig config) {
		return new ReferenceSpeedEnvelopeEstimate[] {
				referenceSpeedEnvelopeEstimate(
						config,
						"selected_max",
						APDRONE_SELECTED_FLIGHT_REFERENCE_MAX_SPEED_METERS_PER_SECOND
				),
				referenceSpeedEnvelopeEstimate(
						config,
						"open_field_mean_file_max",
						APDRONE_OPEN_FIELD_MEAN_FILE_MAX_SPEED_METERS_PER_SECOND
				),
				referenceSpeedEnvelopeEstimate(
						config,
						"open_field_flight2_p95",
						APDRONE_OPEN_FIELD_FLIGHT_2_P95_SPEED_METERS_PER_SECOND
				),
				referenceSpeedEnvelopeEstimate(
						config,
						"open_field_fastest",
						APDRONE_OPEN_FIELD_FASTEST_SPEED_METERS_PER_SECOND
				)
		};
	}

	private static ReferenceSpeedEnvelopeEstimate referenceSpeedEnvelopeEstimate(
			DroneConfig config,
			String speedPoint,
			double referenceSpeedMetersPerSecond
	) {
		AirframeDragCalibration.LevelFlightRequirement requirement =
				AirframeDragCalibration.worstHorizontalLevelFlightRequirement(
						config,
						referenceSpeedMetersPerSecond,
						1.0
				);
		double dragLimitedLevelSpeed = AirframeDragCalibration.dragLimitedLevelSpeedMetersPerSecond(
				config,
				requirement.axis(),
				1.0
		);
		double speedOverLimit = Double.isFinite(dragLimitedLevelSpeed) && dragLimitedLevelSpeed > 1.0e-9
				? referenceSpeedMetersPerSecond / dragLimitedLevelSpeed
				: 0.0;
		double residualMargin = requirement.horizontalThrustMarginNewtons()
				- requirement.baseDragForceNewtons();
		return new ReferenceSpeedEnvelopeEstimate(
				speedPoint,
				referenceSpeedMetersPerSecond,
				requirement.axis(),
				dragLimitedLevelSpeed,
				speedOverLimit,
				requirement.baseDragForceNewtons(),
				requirement.horizontalThrustMarginNewtons(),
				residualMargin,
				requirement.dragToHorizontalMarginRatio(),
				requirement.requiredMaxThrustFraction(),
				requirement.requiredTiltDegrees(),
				requirement.reachable()
		);
	}

	public static BatteryAutonomyEstimate estimateStaticBatteryAutonomy(
			DroneConfig config,
			String scenario,
			double throttleCommand,
			double lowerVoltageCutoff,
			double upperVoltageCutoff,
			double referenceDurationSeconds,
			double referenceMeanCurrentAmps,
			double maxSeconds
	) {
		double clampedThrottleCommand = MathUtil.clamp(throttleCommand, 0.0, 1.0);
		StaticBatteryLoad currentMatchedLoad = estimateStaticBatteryLoadForMeanCurrent(config, referenceMeanCurrentAmps);
		DronePhysics physics = new DronePhysics(config);
		DroneInput input = new DroneInput(clampedThrottleCommand, 0.0, 0.0, 0.0, true);
		DroneEnvironment environment = DroneEnvironment.calm();
		int maxSteps = Math.max(1, (int) Math.round(Math.max(BATTERY_AUTONOMY_DT_SECONDS, maxSeconds) / BATTERY_AUTONOMY_DT_SECONDS));
		double elapsedSeconds = 0.0;
		double inVoltageWindowSeconds = 0.0;
		double ampSeconds = 0.0;
		double wattSeconds = 0.0;
		double startVoltage = Double.NaN;
		double endVoltage = config.nominalBatteryVoltage();
		double minVoltage = Double.POSITIVE_INFINITY;
		double peakCurrent = 0.0;

		for (int step = 0; step < maxSteps; step++) {
			holdStaticAutonomyRig(physics.state());
			physics.step(input, BATTERY_AUTONOMY_DT_SECONDS, environment);
			DroneState state = physics.state();
			double voltage = state.batteryVoltage();
			double current = state.batteryCurrentAmps();
			elapsedSeconds += BATTERY_AUTONOMY_DT_SECONDS;
			if (!Double.isFinite(startVoltage)) {
				startVoltage = voltage;
			}
			endVoltage = voltage;
			minVoltage = Math.min(minVoltage, voltage);
			peakCurrent = Math.max(peakCurrent, current);
			ampSeconds += current * BATTERY_AUTONOMY_DT_SECONDS;
			wattSeconds += current * Math.max(0.0, voltage) * BATTERY_AUTONOMY_DT_SECONDS;
			if (voltage >= lowerVoltageCutoff && voltage <= upperVoltageCutoff) {
				inVoltageWindowSeconds += BATTERY_AUTONOMY_DT_SECONDS;
			}
			if (elapsedSeconds >= 1.0 && (voltage <= lowerVoltageCutoff || state.batteryStateOfCharge() <= 0.001)) {
				break;
			}
		}

		double safeElapsed = Math.max(BATTERY_AUTONOMY_DT_SECONDS, elapsedSeconds);
		return new BatteryAutonomyEstimate(
				scenario,
				clampedThrottleCommand,
				lowerVoltageCutoff,
				upperVoltageCutoff,
				referenceDurationSeconds,
				referenceMeanCurrentAmps,
				elapsedSeconds,
				inVoltageWindowSeconds,
				Double.isFinite(startVoltage) ? startVoltage : endVoltage,
				endVoltage,
				Double.isFinite(minVoltage) ? minVoltage : endVoltage,
				ampSeconds / safeElapsed,
				peakCurrent,
				ampSeconds / 3600.0,
				wattSeconds / 3600.0,
				currentMatchedLoad.throttleCommand(),
				currentMatchedLoad.meanCurrentAmps(),
				currentMatchedLoad.peakCurrentAmps()
		);
	}

	private static StaticBatteryLoad estimateStaticBatteryLoadForMeanCurrent(
			DroneConfig config,
			double targetMeanCurrentAmps
	) {
		DroneConfig directThrottleConfig =
				config.withThrottleCommandCurveExponent(DroneConfig.DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT);
		StaticBatteryLoad low = sampleStaticBatteryLoad(directThrottleConfig, 0.0);
		StaticBatteryLoad high = sampleStaticBatteryLoad(directThrottleConfig, 1.0);
		StaticBatteryLoad best = closerToMeanCurrent(low, high, targetMeanCurrentAmps);
		if (!Double.isFinite(targetMeanCurrentAmps) || targetMeanCurrentAmps <= low.meanCurrentAmps()) {
			return low;
		}
		if (targetMeanCurrentAmps >= high.meanCurrentAmps()) {
			return high;
		}

		double lowThrottle = low.throttleCommand();
		double highThrottle = high.throttleCommand();
		for (int i = 0; i < BATTERY_AUTONOMY_CURRENT_MATCH_ITERATIONS; i++) {
			double midThrottle = (lowThrottle + highThrottle) * 0.5;
			StaticBatteryLoad mid = sampleStaticBatteryLoad(directThrottleConfig, midThrottle);
			best = closerToMeanCurrent(best, mid, targetMeanCurrentAmps);
			if (mid.meanCurrentAmps() < targetMeanCurrentAmps) {
				lowThrottle = midThrottle;
			} else {
				highThrottle = midThrottle;
			}
		}
		return best;
	}

	private static StaticBatteryLoad closerToMeanCurrent(
			StaticBatteryLoad first,
			StaticBatteryLoad second,
			double targetMeanCurrentAmps
	) {
		double firstError = Math.abs(first.meanCurrentAmps() - targetMeanCurrentAmps);
		double secondError = Math.abs(second.meanCurrentAmps() - targetMeanCurrentAmps);
		return secondError < firstError ? second : first;
	}

	private static StaticBatteryLoad sampleStaticBatteryLoad(DroneConfig config, double throttleCommand) {
		double clampedThrottleCommand = MathUtil.clamp(throttleCommand, 0.0, 1.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput input = new DroneInput(clampedThrottleCommand, 0.0, 0.0, 0.0, true);
		DroneEnvironment environment = DroneEnvironment.calm();
		int maxSteps = Math.max(
				1,
				(int) Math.round(BATTERY_AUTONOMY_CURRENT_MATCH_SECONDS / BATTERY_AUTONOMY_DT_SECONDS)
		);
		double warmupSeconds = Math.min(
				BATTERY_AUTONOMY_CURRENT_MATCH_WARMUP_SECONDS,
				Math.max(0.0, BATTERY_AUTONOMY_CURRENT_MATCH_SECONDS - BATTERY_AUTONOMY_DT_SECONDS)
		);
		double ampSeconds = 0.0;
		double sampledSeconds = 0.0;
		double peakCurrent = 0.0;
		double lastCurrent = 0.0;

		for (int step = 0; step < maxSteps; step++) {
			holdStaticAutonomyRig(physics.state());
			physics.step(input, BATTERY_AUTONOMY_DT_SECONDS, environment);
			double elapsedSeconds = (step + 1) * BATTERY_AUTONOMY_DT_SECONDS;
			lastCurrent = physics.state().batteryCurrentAmps();
			if (elapsedSeconds > warmupSeconds) {
				ampSeconds += lastCurrent * BATTERY_AUTONOMY_DT_SECONDS;
				sampledSeconds += BATTERY_AUTONOMY_DT_SECONDS;
				peakCurrent = Math.max(peakCurrent, lastCurrent);
			}
		}

		double meanCurrent = sampledSeconds > 0.0 ? ampSeconds / sampledSeconds : lastCurrent;
		return new StaticBatteryLoad(clampedThrottleCommand, meanCurrent, peakCurrent);
	}

	public static PropDamageVibrationAudit propDamageVibrationAudit(DroneConfig config) {
		int rotorIndex = Math.min(PROP_DAMAGE_AUDIT_ROTOR_INDEX, Math.max(0, config.rotors().size() - 1));
		DamageSensorRms healthy = sampleDamageSensorRms(config, -1, 0.0);
		DamageSensorRms damaged = sampleDamageSensorRms(config, rotorIndex, PROP_DAMAGE_AUDIT_DAMAGE);
		return new PropDamageVibrationAudit(
				rotorIndex,
				PROP_DAMAGE_AUDIT_DAMAGE,
				damaged.sampleCount(),
				damaged.sampledSeconds(),
				healthy.gyroDynamicRmsRadiansPerSecond(),
				damaged.gyroDynamicRmsRadiansPerSecond(),
				ratio(damaged.gyroDynamicRmsRadiansPerSecond(), healthy.gyroDynamicRmsRadiansPerSecond()),
				healthy.accelerometerDynamicRmsMetersPerSecondSquared(),
				damaged.accelerometerDynamicRmsMetersPerSecondSquared(),
				ratio(
						damaged.accelerometerDynamicRmsMetersPerSecondSquared(),
						healthy.accelerometerDynamicRmsMetersPerSecondSquared()
				),
				healthy.maxRotorVibration(),
				damaged.maxRotorVibration(),
				damaged.maxRotorDamageVibration(),
				PropellerDamageCalibration.UAV_REALISTIC_SINGLE_BROKEN_GYRO_RMS_RATIO,
				PropellerDamageCalibration.UAV_REALISTIC_SINGLE_BROKEN_ACCEL_RMS_RATIO,
				PropellerDamageCalibration.PADRE_SINGLE_ROTOR_ACCEL_FEATURE_RMS_RATIO,
				PropellerDamageCalibration.PADRE_TWO_POSITION_ACCEL_FEATURE_RMS_RATIO
		);
	}

	private static DamageSensorRms sampleDamageSensorRms(DroneConfig config, int damagedRotorIndex, double damageAmount) {
		DronePhysics physics = new DronePhysics(config);
		DroneState state = physics.state();
		if (damagedRotorIndex >= 0 && damagedRotorIndex < state.motorCount() && damageAmount > 0.0) {
			state.damageRotor(damagedRotorIndex, damageAmount);
		}

		DroneInput input = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment environment = DroneEnvironment.calm();
		VectorRmsAccumulator gyro = new VectorRmsAccumulator();
		VectorRmsAccumulator accelerometer = new VectorRmsAccumulator();
		int warmupSteps = Math.max(0, (int) Math.round(PROP_DAMAGE_AUDIT_WARMUP_SECONDS / SIMULATION_DT_SECONDS));
		int sampleSteps = Math.max(1, (int) Math.round(PROP_DAMAGE_AUDIT_SAMPLE_SECONDS / SIMULATION_DT_SECONDS));
		double maxRotorVibration = 0.0;
		double maxRotorDamageVibration = 0.0;

		for (int step = 0; step < warmupSteps + sampleSteps; step++) {
			holdStaticAutonomyRig(state);
			physics.step(input, SIMULATION_DT_SECONDS, environment);
			if (step >= warmupSteps) {
				gyro.add(state.gyroAngularVelocityBodyRadiansPerSecond());
				accelerometer.add(state.accelerometerBodyMetersPerSecondSquared());
				maxRotorVibration = Math.max(maxRotorVibration, state.rotorVibration());
				maxRotorDamageVibration = Math.max(maxRotorDamageVibration, state.maxRotorDamageVibration());
			}
		}

		return new DamageSensorRms(
				gyro.samples(),
				gyro.samples() * SIMULATION_DT_SECONDS,
				gyro.dynamicRms(),
				accelerometer.dynamicRms(),
				maxRotorVibration,
				maxRotorDamageVibration
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static void holdStaticAutonomyRig(DroneState state) {
		state.setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		state.setVelocityMetersPerSecond(Vec3.ZERO);
		state.setOrientation(Quaternion.IDENTITY);
		state.setEstimatedOrientation(Quaternion.IDENTITY);
		state.setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
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
			boolean[] scriptedRotorDamageApplied = new boolean[Math.max(8, config.rotors().size())];
			for (int step = 0; step <= totalSteps; step++) {
				double timeSeconds = step * SIMULATION_DT_SECONDS;
				ScriptFrame frame = script(timeSeconds, config);
				DroneEnvironment environment = environmentFor(physics.state(), config, frame);
				applyScriptedRotorDamage(physics.state(), frame, scriptedRotorDamageApplied);
				physics.step(frame.input(), SIMULATION_DT_SECONDS, environment);

				if (step % SAMPLE_EVERY_STEPS == 0) {
					writeSample(writer, sample, step, timeSeconds, frame, physics, environment);
					report.record(physics.state(), config);
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
			case "apdrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavy_lift" -> DroneConfig.heavyLift();
			case "hex_lift" -> DroneConfig.hexLift();
			case "octo_lift" -> DroneConfig.octoLift();
			case "coaxial_x8" -> DroneConfig.coaxialX8();
			default -> throw new IllegalArgumentException("Unknown preset '" + presetName + "'. Use racing_quad, apdrone, cinewhoop, heavy_lift, hex_lift, octo_lift, or coaxial_x8.");
		};
	}

	public static String csvHeader() {
		return CSV_HEADER;
	}

	private static void printUsage() {
		System.out.println("Usage: OfflineFlightRecorder [preset] [output.csv] [duration_seconds]");
		System.out.println("Presets: racing_quad, apdrone, cinewhoop, heavy_lift, hex_lift, octo_lift, coaxial_x8");
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
		if (timeSeconds < 9.65) {
			return frame("crosswind_settle", hover + 0.04, 0.0, 0.0, 0.0, new Vec3(5.0, 0.0, 0.0));
		}
		if (timeSeconds < 10.15) {
			return frame("wall_skim", hover + 0.05, 0.0, 0.04, 0.0, new Vec3(5.0, 0.0, 0.0));
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
			return frame(
					"rain_burst",
					hover + 0.03,
					0.0,
					0.0,
					0.0,
					Vec3.ZERO,
					RAIN_BURST_PRECIPITATION_WETNESS
			);
		}
		if (timeSeconds < 14.35) {
			return frame("throttle_punch", 0.88, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 14.75) {
			return frame("throttle_chop", 0.0, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		if (timeSeconds < 15.35) {
			return frame(
					"light_prop_fault",
					hover + 0.02,
					0.0,
					0.0,
					0.0,
					Vec3.ZERO,
					0.0,
					LIGHT_PROP_FAULT_ROTOR_INDEX,
					LIGHT_PROP_FAULT_DAMAGE
			);
		}
		return frame("cooldown_hover", hover + 0.02, 0.0, 0.0, 0.0, Vec3.ZERO);
	}

	private static ScriptFrame frame(String phase, double throttle, double pitch, double roll, double yaw, Vec3 windVelocityWorldMetersPerSecond) {
		return frame(phase, throttle, pitch, roll, yaw, windVelocityWorldMetersPerSecond, 0.0);
	}

	private static ScriptFrame frame(
			String phase,
			double throttle,
			double pitch,
			double roll,
			double yaw,
			Vec3 windVelocityWorldMetersPerSecond,
			double precipitationWetnessIntensity
	) {
		return frame(phase, throttle, pitch, roll, yaw, windVelocityWorldMetersPerSecond, precipitationWetnessIntensity, -1, 0.0);
	}

	private static ScriptFrame frame(
			String phase,
			double throttle,
			double pitch,
			double roll,
			double yaw,
			Vec3 windVelocityWorldMetersPerSecond,
			double precipitationWetnessIntensity,
			int rotorDamageIndex,
			double rotorDamageAmount
	) {
		return new ScriptFrame(
				phase,
				new DroneInput(
						MathUtil.clamp(throttle, 0.0, 1.0),
						MathUtil.clamp(pitch, -1.0, 1.0),
						MathUtil.clamp(roll, -1.0, 1.0),
						MathUtil.clamp(yaw, -1.0, 1.0),
						true
				),
				windVelocityWorldMetersPerSecond,
				MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0),
				rotorDamageIndex,
				MathUtil.clamp(rotorDamageAmount, 0.0, 1.0)
		);
	}

	private static void applyScriptedRotorDamage(DroneState state, ScriptFrame frame, boolean[] scriptedRotorDamageApplied) {
		int rotorIndex = frame.rotorDamageIndex();
		if (rotorIndex < 0
				|| rotorIndex >= state.motorCount()
				|| rotorIndex >= scriptedRotorDamageApplied.length
				|| scriptedRotorDamageApplied[rotorIndex]
				|| frame.rotorDamageAmount() <= 0.0) {
			return;
		}
		state.damageRotor(rotorIndex, frame.rotorDamageAmount());
		scriptedRotorDamageApplied[rotorIndex] = true;
	}

	private static DroneEnvironment environmentFor(DroneState state, DroneConfig config, ScriptFrame frame) {
		Vec3 windVelocityWorldMetersPerSecond = frame.windVelocityWorldMetersPerSecond();
		double altitude = state.positionMeters().y();
		double ambientTemperatureCelsius = MathUtil.clamp(25.0 - Math.max(0.0, altitude) * 0.0065, -40.0, 65.0);
		double airDensityRatio = DroneEnvironment.standardAtmosphereAirDensityRatio(altitude, ambientTemperatureCelsius);
		double groundClearance = Math.max(0.0, altitude);
		RotorFlowObstructionProfile rotorFlow = rotorFlowObstructionFor(config, frame.phase());
		double obstacleProximity = rotorFlow.maxIntensity();
		double turbulenceIntensity = MathUtil.clamp(
				windVelocityWorldMetersPerSecond.length() / 12.0 + 0.18 * obstacleProximity,
				0.0,
				0.55
		);
		return new DroneEnvironment(
				windVelocityWorldMetersPerSecond,
				airDensityRatio,
				groundClearance,
				turbulenceIntensity,
				obstacleProximity,
				0.0,
				Double.POSITIVE_INFINITY,
				rotorFlow.thrustMultipliers(),
				rotorFlow.obstructions(),
				rotorFlow.directionsBody(),
				null,
				0.0,
				frame.precipitationWetnessIntensity(),
				ambientTemperatureCelsius
		);
	}

	private static RotorFlowObstructionProfile rotorFlowObstructionFor(DroneConfig config, String phase) {
		if (!"wall_skim".equals(phase)) {
			return RotorFlowObstructionProfile.CLEAR;
		}

		int rotorCount = config.rotors().size();
		double[] obstructions = new double[rotorCount];
		Vec3[] directions = new Vec3[rotorCount];
		double bodyCenterClearance = maxRotorProjection(config, WALL_SKIM_DIRECTION_BODY)
				+ WALL_SKIM_CLOSEST_ROTOR_CLEARANCE_METERS;
		double maxIntensity = 0.0;

		for (int i = 0; i < rotorCount; i++) {
			RotorSpec rotor = config.rotors().get(i);
			double rotorClearance = bodyCenterClearance - rotor.positionBodyMeters().dot(WALL_SKIM_DIRECTION_BODY);
			RotorFlowObstructionModel.Result result = RotorFlowObstructionModel.fromDirectionalDistances(
					distancesToBodyWalls(rotorClearance, WALL_SKIM_DIRECTION_BODY),
					ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY,
					sideFlowSampleMaxDistance(rotor),
					rotor.radiusMeters()
			);
			obstructions[i] = result.intensity();
			directions[i] = result.directionBody();
			maxIntensity = Math.max(maxIntensity, result.intensity());
		}

		return new RotorFlowObstructionProfile(obstructions, directions, maxIntensity);
	}

	private static double maxRotorProjection(DroneConfig config, Vec3 directionBody) {
		double maxProjection = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			maxProjection = Math.max(maxProjection, rotor.positionBodyMeters().dot(directionBody));
		}
		return maxProjection;
	}

	private static double sideFlowSampleMaxDistance(RotorSpec rotor) {
		return MathUtil.clamp(rotor.radiusMeters() * 6.5, 0.32, 0.70);
	}

	private static double[] distancesToBodyWalls(double clearanceMeters, Vec3... wallDirectionsBody) {
		double[] distances = new double[ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY.length];
		double clearance = Math.max(0.0, clearanceMeters);
		for (int i = 0; i < ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY.length; i++) {
			Vec3 sampleDirection = ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY[i].normalized();
			double distance = Double.POSITIVE_INFINITY;
			for (Vec3 wallDirection : wallDirectionsBody) {
				double projection = sampleDirection.dot(wallDirection.normalized());
				if (projection > 1.0e-9) {
					distance = Math.min(distance, clearance / projection);
				}
			}
			distances[i] = distance;
		}
		return distances;
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
		Vec3 airframeBodyDrag = state.airframeBodyDragForceBodyNewtons();
		Vec3 linearDampingDrag = state.linearDampingDragForceWorldNewtons();
		Vec3 groundEffectDrag = state.groundEffectDragForceBodyNewtons();
		Vec3 groundEffectLevelingTorque = state.groundEffectLevelingTorqueBodyNewtonMeters();
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
						+ "%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.3f,%.5f,%.3f,%.3f,%.3f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.6f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.3f,%.3f,%.3f,%.3f,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.6f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.3f,%.3f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
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
						+ "%.3f,%.0f,%.1f,%.3f,%.5f,%.2f,%.5f,%.5f,%.3f,%.5f,%.5f,%.0f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.6f,%.6f,%.6f,%.6f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.3f,%.3f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.3f,%.3f,%.3f,%.3f,"
						+ "%.5f,%.5f,%.5f,%.5f,"
						+ "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%.3f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,"
						+ "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,"
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
				DronePhysics.sampledFrequencyAliasHertz(state.gyroBladePassNotchFrequencyHertz(), 1024.0),
				DronePhysics.sampledFrequencyAliasHertz(state.gyroBladePassNotchFrequencyHertz(), 4000.0),
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
				state.batteryEffectiveResistanceOhms(),
				state.batteryResistanceAgingScale(),
				state.batteryEquivalentCycles(),
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
				state.minRotorInducedLagThrustScale(),
				state.averageRotorTranslationalLiftIntensity(),
				state.averageRotorAerodynamicLoadFactor(),
				state.rotorInflowSkewIntensity(),
				state.averageRotorWakeInterferenceIntensity(),
				state.rotorWakeInterferenceIntensity(0),
				state.rotorWakeInterferenceIntensity(1),
				state.rotorWakeInterferenceIntensity(2),
				state.rotorWakeInterferenceIntensity(3),
				state.averageRotorWakeThrustScale(),
				state.rotorWakeThrustScale(0),
				state.rotorWakeThrustScale(1),
				state.rotorWakeThrustScale(2),
				state.rotorWakeThrustScale(3),
				state.averageRotorWetThrustScale(),
				state.rotorWetThrustScale(0),
				state.rotorWetThrustScale(1),
				state.rotorWetThrustScale(2),
				state.rotorWetThrustScale(3),
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
				physics.config().escCommandProtocol().bitrateKilobitsPerSecond(),
				physics.config().escCommandProtocol().rawFrameMicroseconds(),
				physics.config().escCommandProtocol().commandWireUtilization(physics.config().escCommandFrameRateHertz()),
				physics.config().escCommandProtocol().commandIntervalRawFrameRatio(physics.config().escCommandFrameRateHertz()),
				physics.config().rotors().get(0).bladePitchMeters(),
				physics.config().rotors().get(0).bladePitchToDiameterRatio(),
				Math.toDegrees(physics.config().rotors().get(0).geometricBladePitchAngleRadians()),
				physics.config().rotors().get(0).representativeBladeChordMeters(),
				physics.config().rotors().get(0).representativeBladeChordToRadiusRatio(),
				(double) physics.config().rotors().get(0).bladeCount(),
				physics.config().averageRotorImbalanceIntensity(),
				airframeLift.x(),
				airframeLift.y(),
				airframeLift.z(),
				airframeLift.length(),
				airframeBodyDrag.x(),
				airframeBodyDrag.y(),
				airframeBodyDrag.z(),
				airframeBodyDrag.length(),
				linearDampingDrag.x(),
				linearDampingDrag.y(),
				linearDampingDrag.z(),
				linearDampingDrag.length(),
				groundEffectDrag.x(),
				groundEffectDrag.y(),
				groundEffectDrag.z(),
				groundEffectDrag.length(),
				groundEffectLevelingTorque.x(),
				groundEffectLevelingTorque.y(),
				groundEffectLevelingTorque.z(),
				groundEffectLevelingTorque.length(),
				rotorWashDrag.x(),
				rotorWashDrag.y(),
				rotorWashDrag.z(),
				rotorWashDrag.length(),
				state.barometerAltitudeMeters(),
				state.barometerVerticalSpeedMetersPerSecond(),
				state.barometerPressureHectopascals(),
				state.barometerErrorMeters(),
				state.barometerSensorNoiseMeters(),
				state.barometerPressurePortErrorMeters(),
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
				state.contactSurfaceFrictionMultiplier(),
				state.contactSurfaceRestitutionMultiplier(),
				state.contactSurfaceScrapeMultiplier(),
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
		double[] escElectricalOutput = state.escElectricalOutputCommand();
		double[] escElectricalError = state.escElectricalOutputError();
		double[] motorPowers = state.motorPower(config);
		double[] motorRpm = state.motorRpm();
		double[] motorTelemetryRpm = state.motorRpmTelemetryRpm();
		double[] motorTelemetryValidity = state.motorRpmTelemetryValidity();
		double[] motorTargetOmega = state.motorTargetOmegaRadiansPerSecond();
		double[] motorTrackingError = state.motorTrackingError();
		double[] motorActuatorAuthority = state.motorActuatorAuthority();
		double[] motorCurrents = state.motorCurrentAmps();
		double[] motorRegenerativeCurrents = state.motorRegenerativeCurrentAmps();
		double[] motorPhaseCurrents = state.motorPhaseCurrentAmps();
		double[] motorCurrentRipples = state.motorCurrentRippleAmps();
		double[] motorCommutationRipples = state.motorCommutationRippleIntensity();
		double[] motorTorqueRipples = state.motorTorqueRippleNewtonMeters();
		double[] rotorThrust = state.rotorThrustNewtons();
		double[] rotorHealth = state.rotorHealth();
		double[] rotorDamageVibration = state.rotorDamageVibration();
		double[] rotorScrape = state.rotorSurfaceScrapeIntensity();
		double[] rotorWakeInterference = state.rotorWakeInterferenceIntensity();
		double[] rotorWakeThrustScale = state.rotorWakeThrustScale();
		double[] rotorWetThrustScale = state.rotorWetThrustScale();
		double[] rotorCoaxialLoadBias = state.rotorCoaxialLoadBias();
		double[] rotorInPlaneDrag = state.rotorInPlaneDragForceNewtons();
		double[] rotorWakeSwirl = state.rotorWakeSwirlVelocityMetersPerSecond();
		double[] rotorWindmilling = state.rotorWindmillingIntensity();
		double[] rotorDynamicInflowTimeConstant = state.rotorDynamicInflowTimeConstantSeconds();
		double[] rotorAdvanceRatio = state.rotorAdvanceRatio();
		double[] rotorPropellerAdvanceRatioJ = state.rotorPropellerAdvanceRatioJ();
		double[] rotorPropellerThrustScale = state.rotorPropellerThrustScale();
		double[] rotorPropellerPowerScale = state.rotorPropellerPowerScale();
		double[] rotorAxialGustThrustScale = state.rotorAxialGustThrustScale();
		double[] rotorReverseFlowInboardFraction = state.rotorReverseFlowInboardFraction();
		double[] rotorTipMach = state.rotorTipMach();
		double[] rotorCompressibilityThrustScale = state.rotorCompressibilityThrustScale();
		double[] rotorReynoldsNumber = state.rotorReynoldsNumber();
		double[] rotorReynoldsIndex = state.rotorReynoldsIndex();
		double[] rotorLowReynoldsLoss = state.rotorLowReynoldsLoss();
		double[] rotorBladeAngleOfAttack = state.rotorBladeAngleOfAttackRadians();
		double[] rotorBladeElementStall = state.rotorBladeElementStallIntensity();
		double[] rotorBladeDissymmetry = state.rotorBladeDissymmetryIntensity();
		double[] rotorBladePassRipple = state.rotorBladePassRippleIntensity();
		double[] rotorFlappingTilt = state.rotorFlappingTiltRadians();
		double[] rotorConing = state.rotorConingIntensity();
		double[] rotorConingAngle = state.rotorConingAngleRadians();
		double[] motorElectricalEfficiency = state.motorElectricalEfficiency();
		double[] motorVoltageHeadroom = state.motorVoltageHeadroom();
		double[] motorWindingResistanceScale = state.motorWindingResistanceScale();
		double[] motorMechanicalLoss = state.motorMechanicalLossTorqueNewtonMeters();
		Vec3[] rotorForceBody = state.rotorForceBodyNewtons();
		Vec3[] rotorTorqueBody = state.rotorTorqueBodyNewtonMeters();
		double[] rotorArmFlex = state.rotorArmFlexIntensity();
		double[] rotorArmFlexDeflection = state.rotorArmFlexDeflectionMeters();
		double[] rotorArmFlexTilt = state.rotorArmFlexTiltRadians();
		Vec3 mixerOutputTorque = state.mixerOutputTorqueBodyNewtonMeters();
		Vec3 mixerAxisAuthority = state.mixerAxisAuthority();
		Vec3 pidIntegralRelaxAxes = state.pidIntegralRelaxAxes();

		double averageMotorPolePairs = averageMotorPolePairs(config);
		appendExtra(builder, averageMotorPolePairs, "%.3f");
		appendExtra(builder, config.rotors().size());
		appendExtra(builder, state.averageEscElectricalOutputCommand(), "%.5f");
		appendExtra(builder, state.maxEscElectricalOutputError(), "%.6f");
		appendExtra(builder, state.averageEscElectricalOutputError(), "%.6f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(escElectricalOutput, i), "%.5f");
		}
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(escElectricalError, i), "%.6f");
		}
		appendExtra(builder, DronePhysics.betaflightErpm100FromMechanicalRpm(state.averageMotorRpmTelemetryRpm(), averageMotorPolePairs), "%.1f");
		for (int i = 0; i < 4; i++) {
			appendExtra(builder, DronePhysics.betaflightErpm100FromMechanicalRpm(valueOrZero(motorTelemetryRpm, i), motorPolePairs(config, i)), "%.1f");
		}
		appendExtra(builder, DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(state.averageMotorRpmTelemetryRpm(), state.averageMotorRpmTelemetryValidity(), averageMotorPolePairs), "%.1f");
		for (int i = 0; i < 4; i++) {
			appendExtra(builder, motorTelemetryEIntervalMicros(config, motorTelemetryRpm, motorTelemetryValidity, i), "%.1f");
		}
		appendExtra(builder, state.averageMotorRpmTelemetryValidity(), "%.3f");
		for (int i = 0; i < 4; i++) {
			appendExtra(builder, valueOrZero(motorTelemetryValidity, i), "%.3f");
		}
		appendExtra(builder, DronePhysics.betaflightErpm100FromMechanicalRpm(state.averageMotorTargetRpm(), averageMotorPolePairs), "%.1f");
		for (int i = 0; i < 4; i++) {
			appendExtra(builder, DronePhysics.betaflightErpm100FromMechanicalRpm(motorTargetRpm(valueOrZero(motorTargetOmega, i)), motorPolePairs(config, i)), "%.1f");
		}
		appendExtra(builder, DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(state.averageMotorTargetRpm(), averageMotorPolePairs), "%.1f");
		for (int i = 0; i < 4; i++) {
			appendExtra(builder, DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(motorTargetRpm(valueOrZero(motorTargetOmega, i)), motorPolePairs(config, i)), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorPowers, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorRpm, i), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, DronePhysics.betaflightErpm100FromMechanicalRpm(valueOrZero(motorTelemetryRpm, i), motorPolePairs(config, i)), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, motorTelemetryEIntervalMicros(config, motorTelemetryRpm, motorTelemetryValidity, i), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorTelemetryValidity, i), "%.3f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, motorTargetRpm(valueOrZero(motorTargetOmega, i)), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, DronePhysics.betaflightErpm100FromMechanicalRpm(motorTargetRpm(valueOrZero(motorTargetOmega, i)), motorPolePairs(config, i)), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(motorTargetRpm(valueOrZero(motorTargetOmega, i)), motorPolePairs(config, i)), "%.1f");
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
		appendExtra(builder, state.averageMotorRegenerativeCurrentAmps(), "%.3f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorRegenerativeCurrents, i), "%.3f");
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
		appendExtra(builder, state.batterySlowPolarizationVoltage(), "%.5f");
		appendExtra(builder, state.imuSupplyNoiseIntensity(), "%.5f");
		appendExtra(builder, state.batteryTemperatureCelsius(), "%.3f");
		appendExtra(builder, state.batteryCoolingFactor(), "%.5f");
		appendExtra(builder, state.batteryThermalLimit(), "%.5f");
		appendExtra(builder, state.batteryCapacityAgingScale(), "%.5f");
		appendExtra(builder, state.batteryStateOfChargeResistanceScale(), "%.5f");
		appendExtra(builder, state.batteryTemperatureResistanceScale(), "%.5f");
		appendExtra(builder, state.batteryPolarizationResistanceScale(), "%.5f");
		appendExtra(builder, state.batteryTwentyPercentSagCurrentAmps(), "%.3f");
		appendExtra(builder, state.batteryTwentyPercentSagCurrentMargin(), "%.5f");
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorThrust, i), "%.4f");
		}
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorHealth, i), "%.5f");
		}
		appendExtra(builder, state.maxRotorDamageVibration(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorDamageVibration, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorScrape, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorWakeInterference, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorWakeThrustScale, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorWetThrustScale, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorWakeSwirlVelocityMetersPerSecond(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorWakeSwirl, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorWindmillingIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorWindmilling, i), "%.5f");
		}
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, i < config.rotors().size() ? environment.rotorThrustMultiplier(i, config) : 1.0, "%.5f");
		}
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, environment.rotorFlowObstruction(i), "%.5f");
		}
		appendExtra(builder, state.averageRotorDynamicInflowTimeConstantSeconds(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorDynamicInflowTimeConstant, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorAdvanceRatio(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorAdvanceRatio, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorPropellerAdvanceRatioJ(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorPropellerAdvanceRatioJ, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorPropellerThrustScale(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorPropellerThrustScale, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorPropellerPowerScale(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorPropellerPowerScale, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorAxialGustThrustScale(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorAxialGustThrustScale, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorReverseFlowInboardFraction(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorReverseFlowInboardFraction, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorTipMach(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorTipMach, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorCompressibilityThrustScale(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(rotorCompressibilityThrustScale, i), "%.5f");
		}
		appendExtra(builder, state.averageRotorReynoldsNumber(), "%.1f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorReynoldsNumber, i), "%.1f");
		}
		appendExtra(builder, state.averageRotorReynoldsIndex(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorReynoldsIndex, i), "%.5f");
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
		appendExtra(builder, Math.toDegrees(state.averageRotorConingAngleRadians()), "%.4f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, Math.toDegrees(valueOrZero(rotorConingAngle, i)), "%.4f");
		}
		appendExtra(builder, state.averageMotorElectricalEfficiency(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(motorElectricalEfficiency, i), "%.5f");
		}
		appendExtra(builder, state.averageMotorVoltageHeadroom(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(motorVoltageHeadroom, i), "%.5f");
		}
		appendExtra(builder, state.averageMotorWindingResistanceScale(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrOne(motorWindingResistanceScale, i), "%.5f");
		}
		appendExtra(builder, environment.waterImmersionIntensity(), "%.5f");
		appendExtra(builder, environment.precipitationWetnessIntensity(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, environment.rotorWaterImmersion(i), "%.5f");
		}
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, environment.rotorPrecipitationWetness(i), "%.5f");
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
		appendExtra(builder, state.averageRotorArmFlexDeflectionMeters() * 1000.0, "%.4f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorArmFlexDeflection, i) * 1000.0, "%.4f");
		}
		appendExtra(builder, Math.toDegrees(state.averageRotorArmFlexTiltRadians()), "%.4f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, Math.toDegrees(valueOrZero(rotorArmFlexTilt, i)), "%.4f");
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
		appendExtra(builder, state.averageRotorInPlaneDragForceNewtons(), "%.4f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorInPlaneDrag, i), "%.4f");
		}
		appendExtra(builder, state.averageAbsRotorCoaxialLoadBias(), "%.5f");
		for (int i = 0; i < 8; i++) {
			appendExtra(builder, valueOrZero(rotorCoaxialLoadBias, i), "%.5f");
		}
		appendExtra(builder, state.averageAbsRotorCoaxialLoadBiasTarget(), "%.5f");
		appendExtra(builder, state.maxRotorCoaxialLoadBiasClipping(), "%.5f");
		appendExtra(builder, state.averageRotorCoaxialAllocationLoadFraction(), "%.5f");
		appendExtra(builder, state.maxRotorCoaxialAllocationCommandRatio(), "%.5f");
		appendExtra(builder, state.maxRotorCoaxialAllocationMechanicalGainPercent(), "%.5f");
		appendExtra(builder, state.maxRotorCoaxialAllocationElectricalGainPercent(), "%.5f");
		appendExtra(builder, state.maxRotorCoaxialAllocationUncertaintyPercent(), "%.5f");
		appendExtra(builder, state.airframeDragAlongFlowNewtons(), "%.5f");
		appendExtra(builder, state.airframeDragEquivalentLinearCoefficient(), "%.5f");
		appendExtra(builder, state.airframeDragEquivalentCdAMetersSquared(), "%.5f");
		appendExtra(builder, state.airframeDragImavReferenceRatio(), "%.5f");
		appendExtra(builder, state.gyroDynamicNotchSpreadHertz(), "%.3f");
		appendExtra(builder, state.gyroRpmHarmonicNotchAttenuation(), "%.5f");
		appendExtra(builder, state.gyroBladePassNotchSpreadHertz(), "%.3f");
		Vec3 vortexRingBuffetForce = state.vortexRingBuffetForceBodyNewtons();
		appendExtra(builder, state.vortexRingThrustBuffetAmplitude(), "%.5f");
		appendExtra(builder, state.maxVortexRingThrustBuffetAmplitude(), "%.5f");
		appendExtra(builder, vortexRingBuffetForce.x(), "%.5f");
		appendExtra(builder, vortexRingBuffetForce.y(), "%.5f");
		appendExtra(builder, vortexRingBuffetForce.z(), "%.5f");
		appendExtra(builder, vortexRingBuffetForce.length(), "%.5f");
		appendExtra(builder, state.drydenTurbulenceSpeedMetersPerSecond(), "%.5f");
		appendExtra(builder, state.windBurbleSpeedMetersPerSecond(), "%.5f");
		appendExtra(builder, MotorBenchCurrentModel.mqtbHq5x4x3TotalCurrentAmps(state), "%.3f");
		appendExtra(builder, MotorBenchCurrentModel.mqtbHq5x4x3TotalElectricalPowerWatts(state), "%.3f");
		appendExtra(builder, MotorBenchCurrentModel.mqtbHq5x4x3CurrentRatio(state), "%.5f");
		appendExtra(builder, MotorBenchCurrentModel.mqtbHq5x4x3CurrentResidualAmps(state), "%.3f");
		appendExtra(builder, MotorBenchCurrentModel.apDronePdf5045TotalCurrentAmps(config, state), "%.3f");
		appendExtra(builder, MotorBenchCurrentModel.apDronePdf5045TotalElectricalPowerWatts(config, state), "%.3f");
		appendExtra(builder, MotorBenchCurrentModel.apDronePdf5045CurrentRatio(config, state), "%.5f");
		appendExtra(builder, MotorBenchCurrentModel.apDronePdf5045CurrentResidualAmps(config, state), "%.3f");
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

	private static double motorTelemetryEIntervalMicros(
			DroneConfig config,
			double[] motorTelemetryRpm,
			double[] motorTelemetryValidity,
			int index
	) {
		if (index < 0 || index >= motorTelemetryRpm.length || index >= motorTelemetryValidity.length) {
			return 0.0;
		}
		return DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
				motorTelemetryRpm[index],
				motorTelemetryValidity[index],
				motorPolePairs(config, index)
		);
	}

	private static double averageMotorPolePairs(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return RotorSpec.DEFAULT_MOTOR_POLE_PAIRS;
		}
		return config.rotors().stream()
				.mapToDouble(RotorSpec::motorPolePairs)
				.average()
				.orElse(RotorSpec.DEFAULT_MOTOR_POLE_PAIRS);
	}

	private static double motorPolePairs(DroneConfig config, int index) {
		if (config == null || index < 0 || index >= config.rotors().size()) {
			return RotorSpec.DEFAULT_MOTOR_POLE_PAIRS;
		}
		return config.rotors().get(index).motorPolePairs();
	}

	private static double motorTargetRpm(double omegaRadiansPerSecond) {
		return omegaRadiansPerSecond * 60.0 / (Math.PI * 2.0);
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

	private record ScriptFrame(
			String phase,
			DroneInput input,
			Vec3 windVelocityWorldMetersPerSecond,
			double precipitationWetnessIntensity,
			int rotorDamageIndex,
			double rotorDamageAmount
	) {
	}

	private record RotorFlowObstructionProfile(double[] obstructions, Vec3[] directionsBody, double maxIntensity) {
		private static final RotorFlowObstructionProfile CLEAR = new RotorFlowObstructionProfile(null, null, 0.0);

		private double[] thrustMultipliers() {
			if (obstructions == null || obstructions.length == 0) {
				return null;
			}

			double[] multipliers = new double[obstructions.length];
			for (int i = 0; i < multipliers.length; i++) {
				multipliers[i] = RotorFlowObstructionModel.thrustMultiplier(obstructions[i]);
			}
			return multipliers;
		}
	}

	public static final class FlightReport {
		private int samples;
		private double maxSpeedMetersPerSecond;
		private double maxBatteryCurrentAmps;
		private double maxBatteryRegenerativeCurrentAmps;
		private double maxMotorRegenerativeCurrentAmps;
		private double minBatteryVoltage = Double.POSITIVE_INFINITY;
		private double maxBatterySagVoltage;
		private double maxBatteryEffectiveResistanceOhms;
		private double maxBatteryStateOfChargeResistanceScale = 1.0;
		private double maxBatteryTemperatureResistanceScale = 1.0;
		private double maxBatteryPolarizationResistanceScale = 1.0;
		private double maxBatteryVoltageSpike;
		private double maxBatteryBusRippleVoltage;
		private double maxImuSupplyNoiseIntensity;
		private double maxAverageMotorTelemetryRpm;
		private double maxMotorTelemetryRpm;
		private double maxAverageMotorTelemetryErpm100;
		private double maxMotorTelemetryErpm100;
		private double minAverageMotorTelemetryEIntervalMicros = DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS;
		private double minMotorTelemetryEIntervalMicros = DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS;
		private double maxAverageMotorRpmTelemetryValidity;
		private double maxMotorRpmTelemetryValidity;
		private double maxGyroNotchFrequencyHertz;
		private double maxGyroNotchAttenuation;
		private double maxGyroNotchSpreadHertz;
		private double maxGyroRpmHarmonicNotchAttenuation;
		private double maxGyroBladePassNotchFrequencyHertz;
		private double maxGyroBladePassNotchAttenuation;
		private double maxGyroBladePassNotchSpreadHertz;
		private double maxBatteryTemperatureCelsius = 25.0;
		private double minBatteryThermalLimit = 1.0;
		private double maxPropwashIntensity;
		private double maxVortexRingStateIntensity;
		private double maxVortexRingThrustBuffetAmplitude;
		private double maxVortexRingBuffetForceNewtons;
		private double maxRotorInducedVelocityMetersPerSecond;
		private double minRotorInducedLagThrustScale = 1.0;
		private double maxRotorDynamicInflowTimeConstantSeconds;
		private double maxRotorAdvanceRatio;
		private double maxRotorPropellerAdvanceRatioJ;
		private double minRotorPropellerThrustScale = 1.0;
		private double minRotorPropellerPowerScale = 1.0;
		private double minRotorAxialGustThrustScale = 1.0;
		private double maxRotorAxialGustThrustScale = 1.0;
		private double maxRotorReverseFlowInboardFraction;
		private double maxRotorTipMach;
		private double minRotorCompressibilityThrustScale = 1.0;
		private double maxRotorLowReynoldsLoss;
		private double maxRotorBladePassRippleIntensity;
		private double maxRotorInPlaneDragForceNewtons;
		private double maxRotorCoaxialLoadBias;
		private double maxRotorCoaxialLoadBiasTarget;
		private double maxRotorCoaxialLoadBiasClipping;
		private double maxRotorCoaxialAllocationLoadFraction;
		private double maxRotorCoaxialAllocationCommandRatio = 1.0;
		private double maxRotorCoaxialAllocationMechanicalGainPercent;
		private double maxRotorCoaxialAllocationElectricalGainPercent;
		private double maxRotorCoaxialAllocationUncertaintyPercent;
		private double minRotorWetThrustScale = 1.0;
		private double maxRotorBladeDissymmetryTorqueNewtonMeters;
		private double maxRotorWakeSwirlVelocityMetersPerSecond;
		private double maxRotorWindmillingIntensity;
		private double maxRotorWakeSwirlTorqueNewtonMeters;
		private double maxRotorActiveBrakingTorqueNewtonMeters;
		private double maxRotorAccelerationReactionTorqueNewtonMeters;
		private double maxRotorGyroscopicTorqueNewtonMeters;
		private double maxRotorFlappingTorqueNewtonMeters;
		private double minMotorElectricalEfficiency = 1.0;
		private double minMotorVoltageHeadroom = 1.0;
		private double maxMotorWindingResistanceScale = 1.0;
		private double maxMotorTrackingError;
		private double minMotorActuatorAuthority = 1.0;
		private double minMixerAxisAuthority = 1.0;
		private double maxRotorStallIntensity;
		private double maxRotorDamageVibration;
		private double maxAirframeSeparatedFlowIntensity;
		private double maxAirframeBodyDragForceNewtons;
		private double maxLinearDampingDragForceNewtons;
		private double maxGroundEffectLevelingTorqueNewtonMeters;
		private double maxRotorConingIntensity;
		private double maxRotorConingAngleRadians;
		private double maxRotorArmFlexIntensity;
		private double maxRotorArmFlexDeflectionMeters;
		private double maxRotorArmFlexTiltRadians;
		private double maxRotorSurfaceScrapeIntensity;
		private double maxWindGustSpeedMetersPerSecond;
		private double maxWindDrydenSpeedMetersPerSecond;
		private double maxWindBurbleSpeedMetersPerSecond;
		private double maxWindShearAccelerationMetersPerSecondSquared;
		private double maxRotorWallEffectForceNewtons;
		private double maxContactImpactSpeedMetersPerSecond;
		private double maxContactSlipSpeedMetersPerSecond;
		private double maxContactBounceSpeedMetersPerSecond;
		private double maxContactAngularImpulseDegreesPerSecond;
		private double maxAirframeTorqueNewtonMeters;
		private double maxBarometerErrorMeters;
		private double maxBarometerPressurePortErrorMeters;
		private double maxBarometerPropwashErrorMeters;
		private double maxEscTemperatureCelsius;
		private double minEscThermalLimit = 1.0;

		private void record(DroneState state, DroneConfig config) {
			maxSpeedMetersPerSecond = Math.max(maxSpeedMetersPerSecond, state.speedMetersPerSecond());
			maxBatteryCurrentAmps = Math.max(maxBatteryCurrentAmps, state.batteryCurrentAmps());
			maxBatteryRegenerativeCurrentAmps = Math.max(maxBatteryRegenerativeCurrentAmps, state.batteryRegenerativeCurrentAmps());
			maxMotorRegenerativeCurrentAmps = Math.max(maxMotorRegenerativeCurrentAmps, state.maxMotorRegenerativeCurrentAmps());
			minBatteryVoltage = Math.min(minBatteryVoltage, state.batteryVoltage());
			maxBatterySagVoltage = Math.max(maxBatterySagVoltage, state.batteryTotalSagVoltage());
			maxBatteryEffectiveResistanceOhms = Math.max(maxBatteryEffectiveResistanceOhms, state.batteryEffectiveResistanceOhms());
			maxBatteryStateOfChargeResistanceScale = Math.max(maxBatteryStateOfChargeResistanceScale, state.batteryStateOfChargeResistanceScale());
			maxBatteryTemperatureResistanceScale = Math.max(maxBatteryTemperatureResistanceScale, state.batteryTemperatureResistanceScale());
			maxBatteryPolarizationResistanceScale = Math.max(maxBatteryPolarizationResistanceScale, state.batteryPolarizationResistanceScale());
			maxBatteryVoltageSpike = Math.max(maxBatteryVoltageSpike, state.batteryVoltageSpike());
			maxBatteryBusRippleVoltage = Math.max(maxBatteryBusRippleVoltage, state.batteryBusRippleVoltage());
			maxImuSupplyNoiseIntensity = Math.max(maxImuSupplyNoiseIntensity, state.imuSupplyNoiseIntensity());
			recordRpmTelemetry(state, config);
			maxGyroNotchFrequencyHertz = Math.max(maxGyroNotchFrequencyHertz, state.gyroDynamicNotchFrequencyHertz());
			maxGyroNotchAttenuation = Math.max(maxGyroNotchAttenuation, state.gyroDynamicNotchAttenuation());
			maxGyroNotchSpreadHertz = Math.max(maxGyroNotchSpreadHertz, state.gyroDynamicNotchSpreadHertz());
			maxGyroRpmHarmonicNotchAttenuation = Math.max(maxGyroRpmHarmonicNotchAttenuation, state.gyroRpmHarmonicNotchAttenuation());
			maxGyroBladePassNotchFrequencyHertz = Math.max(maxGyroBladePassNotchFrequencyHertz, state.gyroBladePassNotchFrequencyHertz());
			maxGyroBladePassNotchAttenuation = Math.max(maxGyroBladePassNotchAttenuation, state.gyroBladePassNotchAttenuation());
			maxGyroBladePassNotchSpreadHertz = Math.max(maxGyroBladePassNotchSpreadHertz, state.gyroBladePassNotchSpreadHertz());
			maxBatteryTemperatureCelsius = Math.max(maxBatteryTemperatureCelsius, state.batteryTemperatureCelsius());
			minBatteryThermalLimit = Math.min(minBatteryThermalLimit, state.batteryThermalLimit());
			maxPropwashIntensity = Math.max(maxPropwashIntensity, state.propwashIntensity());
			maxVortexRingStateIntensity = Math.max(maxVortexRingStateIntensity, state.vortexRingStateIntensity());
			maxVortexRingThrustBuffetAmplitude = Math.max(
					maxVortexRingThrustBuffetAmplitude,
					state.maxVortexRingThrustBuffetAmplitude()
			);
			maxVortexRingBuffetForceNewtons = Math.max(
					maxVortexRingBuffetForceNewtons,
					state.vortexRingBuffetForceBodyNewtons().length()
			);
			maxRotorInducedVelocityMetersPerSecond = Math.max(
					maxRotorInducedVelocityMetersPerSecond,
					state.maxRotorInducedVelocityMetersPerSecond()
			);
			minRotorInducedLagThrustScale = Math.min(minRotorInducedLagThrustScale, state.minRotorInducedLagThrustScale());
			maxRotorDynamicInflowTimeConstantSeconds = Math.max(
					maxRotorDynamicInflowTimeConstantSeconds,
					state.maxRotorDynamicInflowTimeConstantSeconds()
			);
			maxRotorAdvanceRatio = Math.max(maxRotorAdvanceRatio, state.maxRotorAdvanceRatio());
			maxRotorPropellerAdvanceRatioJ = Math.max(maxRotorPropellerAdvanceRatioJ, state.maxRotorPropellerAdvanceRatioJ());
			minRotorPropellerThrustScale = Math.min(minRotorPropellerThrustScale, state.minRotorPropellerThrustScale());
			minRotorPropellerPowerScale = Math.min(minRotorPropellerPowerScale, state.minRotorPropellerPowerScale());
			minRotorAxialGustThrustScale = Math.min(minRotorAxialGustThrustScale, state.minRotorAxialGustThrustScale());
			maxRotorAxialGustThrustScale = Math.max(maxRotorAxialGustThrustScale, state.maxRotorAxialGustThrustScale());
			maxRotorReverseFlowInboardFraction = Math.max(maxRotorReverseFlowInboardFraction, state.maxRotorReverseFlowInboardFraction());
			maxRotorTipMach = Math.max(maxRotorTipMach, state.maxRotorTipMach());
			minRotorCompressibilityThrustScale = Math.min(
					minRotorCompressibilityThrustScale,
					state.minRotorCompressibilityThrustScale()
			);
			maxRotorLowReynoldsLoss = Math.max(maxRotorLowReynoldsLoss, state.maxRotorLowReynoldsLoss());
			maxRotorBladePassRippleIntensity = Math.max(maxRotorBladePassRippleIntensity, state.maxRotorBladePassRippleIntensity());
			maxRotorInPlaneDragForceNewtons = Math.max(maxRotorInPlaneDragForceNewtons, state.maxRotorInPlaneDragForceNewtons());
			maxRotorCoaxialLoadBias = Math.max(maxRotorCoaxialLoadBias, state.maxAbsRotorCoaxialLoadBias());
			maxRotorCoaxialLoadBiasTarget = Math.max(maxRotorCoaxialLoadBiasTarget, state.maxAbsRotorCoaxialLoadBiasTarget());
			maxRotorCoaxialLoadBiasClipping = Math.max(maxRotorCoaxialLoadBiasClipping, state.maxRotorCoaxialLoadBiasClipping());
			maxRotorCoaxialAllocationLoadFraction = Math.max(
					maxRotorCoaxialAllocationLoadFraction,
					state.maxRotorCoaxialAllocationLoadFraction()
			);
			maxRotorCoaxialAllocationCommandRatio = Math.max(
					maxRotorCoaxialAllocationCommandRatio,
					state.maxRotorCoaxialAllocationCommandRatio()
			);
			maxRotorCoaxialAllocationMechanicalGainPercent = Math.max(
					maxRotorCoaxialAllocationMechanicalGainPercent,
					state.maxRotorCoaxialAllocationMechanicalGainPercent()
			);
			maxRotorCoaxialAllocationElectricalGainPercent = Math.max(
					maxRotorCoaxialAllocationElectricalGainPercent,
					state.maxRotorCoaxialAllocationElectricalGainPercent()
			);
			maxRotorCoaxialAllocationUncertaintyPercent = Math.max(
					maxRotorCoaxialAllocationUncertaintyPercent,
					state.maxRotorCoaxialAllocationUncertaintyPercent()
			);
			minRotorWetThrustScale = Math.min(minRotorWetThrustScale, state.minRotorWetThrustScale());
			maxRotorBladeDissymmetryTorqueNewtonMeters = Math.max(
					maxRotorBladeDissymmetryTorqueNewtonMeters,
					state.rotorBladeDissymmetryTorqueBodyNewtonMeters().length()
			);
			maxRotorWakeSwirlVelocityMetersPerSecond = Math.max(
					maxRotorWakeSwirlVelocityMetersPerSecond,
					state.maxRotorWakeSwirlVelocityMetersPerSecond()
			);
			maxRotorWindmillingIntensity = Math.max(
					maxRotorWindmillingIntensity,
					state.maxRotorWindmillingIntensity()
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
			maxMotorWindingResistanceScale = Math.max(
					maxMotorWindingResistanceScale,
					state.maxMotorWindingResistanceScale()
			);
			maxMotorTrackingError = Math.max(maxMotorTrackingError, state.maxMotorTrackingError());
			minMotorActuatorAuthority = Math.min(minMotorActuatorAuthority, state.minMotorActuatorAuthority());
			minMixerAxisAuthority = Math.min(minMixerAxisAuthority, state.minMixerAxisAuthority());
			maxRotorStallIntensity = Math.max(maxRotorStallIntensity, state.averageRotorStallIntensity());
			maxRotorDamageVibration = Math.max(maxRotorDamageVibration, state.maxRotorDamageVibration());
			maxAirframeSeparatedFlowIntensity = Math.max(maxAirframeSeparatedFlowIntensity, state.airframeSeparatedFlowIntensity());
			maxAirframeBodyDragForceNewtons = Math.max(
					maxAirframeBodyDragForceNewtons,
					state.airframeBodyDragForceBodyNewtons().length()
			);
			maxLinearDampingDragForceNewtons = Math.max(
					maxLinearDampingDragForceNewtons,
					state.linearDampingDragForceWorldNewtons().length()
			);
			maxGroundEffectLevelingTorqueNewtonMeters = Math.max(
					maxGroundEffectLevelingTorqueNewtonMeters,
					state.groundEffectLevelingTorqueBodyNewtonMeters().length()
			);
			maxRotorConingIntensity = Math.max(maxRotorConingIntensity, state.maxRotorConingIntensity());
			maxRotorConingAngleRadians = Math.max(maxRotorConingAngleRadians, state.maxRotorConingAngleRadians());
			maxRotorArmFlexIntensity = Math.max(maxRotorArmFlexIntensity, state.maxRotorArmFlexIntensity());
			maxRotorArmFlexDeflectionMeters = Math.max(maxRotorArmFlexDeflectionMeters, state.maxRotorArmFlexDeflectionMeters());
			maxRotorArmFlexTiltRadians = Math.max(maxRotorArmFlexTiltRadians, state.maxRotorArmFlexTiltRadians());
			maxRotorSurfaceScrapeIntensity = Math.max(maxRotorSurfaceScrapeIntensity, state.maxRotorSurfaceScrapeIntensity());
			maxWindGustSpeedMetersPerSecond = Math.max(maxWindGustSpeedMetersPerSecond, state.windGustSpeedMetersPerSecond());
			maxWindDrydenSpeedMetersPerSecond = Math.max(maxWindDrydenSpeedMetersPerSecond, state.drydenTurbulenceSpeedMetersPerSecond());
			maxWindBurbleSpeedMetersPerSecond = Math.max(maxWindBurbleSpeedMetersPerSecond, state.windBurbleSpeedMetersPerSecond());
			maxWindShearAccelerationMetersPerSecondSquared = Math.max(maxWindShearAccelerationMetersPerSecondSquared, state.windShearAccelerationMetersPerSecondSquared());
			maxRotorWallEffectForceNewtons = Math.max(maxRotorWallEffectForceNewtons, state.rotorWallEffectForceBodyNewtons().length());
			maxContactImpactSpeedMetersPerSecond = Math.max(maxContactImpactSpeedMetersPerSecond, state.contactImpactSpeedMetersPerSecond());
			maxContactSlipSpeedMetersPerSecond = Math.max(maxContactSlipSpeedMetersPerSecond, state.contactSlipSpeedMetersPerSecond());
			maxContactBounceSpeedMetersPerSecond = Math.max(maxContactBounceSpeedMetersPerSecond, state.contactBounceSpeedMetersPerSecond());
			maxContactAngularImpulseDegreesPerSecond = Math.max(maxContactAngularImpulseDegreesPerSecond, Math.toDegrees(state.contactAngularImpulseBodyRadiansPerSecond().length()));
			maxAirframeTorqueNewtonMeters = Math.max(maxAirframeTorqueNewtonMeters, state.airframeAerodynamicTorqueBodyNewtonMeters().length());
			maxBarometerErrorMeters = Math.max(maxBarometerErrorMeters, Math.abs(state.barometerErrorMeters()));
			maxBarometerPressurePortErrorMeters = Math.max(maxBarometerPressurePortErrorMeters, Math.abs(state.barometerPressurePortErrorMeters()));
			maxBarometerPropwashErrorMeters = Math.max(maxBarometerPropwashErrorMeters, Math.abs(state.barometerPropwashErrorMeters()));
			maxEscTemperatureCelsius = Math.max(maxEscTemperatureCelsius, state.maxEscTemperatureCelsius());
			minEscThermalLimit = Math.min(minEscThermalLimit, state.escThermalLimit());
		}

		private void recordRpmTelemetry(DroneState state, DroneConfig config) {
			double averageTelemetryRpm = state.averageMotorRpmTelemetryRpm();
			double averagePolePairs = averageMotorPolePairs(config);
			maxAverageMotorTelemetryRpm = Math.max(maxAverageMotorTelemetryRpm, averageTelemetryRpm);
			maxAverageMotorTelemetryErpm100 = Math.max(
					maxAverageMotorTelemetryErpm100,
					DronePhysics.betaflightErpm100FromMechanicalRpm(averageTelemetryRpm, averagePolePairs)
			);
			minAverageMotorTelemetryEIntervalMicros = minValidEIntervalMicros(
					minAverageMotorTelemetryEIntervalMicros,
					DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
							averageTelemetryRpm,
							state.averageMotorRpmTelemetryValidity(),
							averagePolePairs
					)
			);
			maxAverageMotorRpmTelemetryValidity = Math.max(
					maxAverageMotorRpmTelemetryValidity,
					state.averageMotorRpmTelemetryValidity()
			);

			double[] telemetryRpm = state.motorRpmTelemetryRpm();
			double[] telemetryValidity = state.motorRpmTelemetryValidity();
			for (int i = 0; i < telemetryRpm.length; i++) {
				double rpm = telemetryRpm[i];
				double validity = i < telemetryValidity.length ? telemetryValidity[i] : 0.0;
				maxMotorTelemetryRpm = Math.max(maxMotorTelemetryRpm, rpm);
				maxMotorTelemetryErpm100 = Math.max(
						maxMotorTelemetryErpm100,
						DronePhysics.betaflightErpm100FromMechanicalRpm(rpm, motorPolePairs(config, i))
				);
				minMotorTelemetryEIntervalMicros = minValidEIntervalMicros(
						minMotorTelemetryEIntervalMicros,
						DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(rpm, validity, motorPolePairs(config, i))
				);
				maxMotorRpmTelemetryValidity = Math.max(maxMotorRpmTelemetryValidity, validity);
			}
		}

		private static double minValidEIntervalMicros(double currentMin, double sample) {
			if (sample > 0.0 && sample < DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS) {
				return Math.min(currentMin, sample);
			}
			return currentMin;
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

		public double maxMotorRegenerativeCurrentAmps() {
			return maxMotorRegenerativeCurrentAmps;
		}

		public double minBatteryVoltage() {
			return minBatteryVoltage;
		}

		public double maxBatterySagVoltage() {
			return maxBatterySagVoltage;
		}

		public double maxBatteryEffectiveResistanceOhms() {
			return maxBatteryEffectiveResistanceOhms;
		}

		public double maxBatteryStateOfChargeResistanceScale() {
			return maxBatteryStateOfChargeResistanceScale;
		}

		public double maxBatteryTemperatureResistanceScale() {
			return maxBatteryTemperatureResistanceScale;
		}

		public double maxBatteryPolarizationResistanceScale() {
			return maxBatteryPolarizationResistanceScale;
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

		public double maxAverageMotorTelemetryRpm() {
			return maxAverageMotorTelemetryRpm;
		}

		public double maxMotorTelemetryRpm() {
			return maxMotorTelemetryRpm;
		}

		public double maxAverageMotorTelemetryErpm100() {
			return maxAverageMotorTelemetryErpm100;
		}

		public double maxMotorTelemetryErpm100() {
			return maxMotorTelemetryErpm100;
		}

		public double minAverageMotorTelemetryEIntervalMicros() {
			return minAverageMotorTelemetryEIntervalMicros;
		}

		public double minMotorTelemetryEIntervalMicros() {
			return minMotorTelemetryEIntervalMicros;
		}

		public double maxAverageMotorRpmTelemetryValidity() {
			return maxAverageMotorRpmTelemetryValidity;
		}

		public double maxMotorRpmTelemetryValidity() {
			return maxMotorRpmTelemetryValidity;
		}

		public double maxGyroNotchFrequencyHertz() {
			return maxGyroNotchFrequencyHertz;
		}

		public double maxGyroNotchAttenuation() {
			return maxGyroNotchAttenuation;
		}

		public double maxGyroNotchSpreadHertz() {
			return maxGyroNotchSpreadHertz;
		}

		public double maxGyroRpmHarmonicNotchAttenuation() {
			return maxGyroRpmHarmonicNotchAttenuation;
		}

		public double maxGyroBladePassNotchFrequencyHertz() {
			return maxGyroBladePassNotchFrequencyHertz;
		}

		public double maxGyroBladePassNotchAttenuation() {
			return maxGyroBladePassNotchAttenuation;
		}

		public double maxGyroBladePassNotchSpreadHertz() {
			return maxGyroBladePassNotchSpreadHertz;
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

		public double maxVortexRingThrustBuffetAmplitude() {
			return maxVortexRingThrustBuffetAmplitude;
		}

		public double maxVortexRingBuffetForceNewtons() {
			return maxVortexRingBuffetForceNewtons;
		}

		public double maxRotorInducedVelocityMetersPerSecond() {
			return maxRotorInducedVelocityMetersPerSecond;
		}

		public double minRotorInducedLagThrustScale() {
			return minRotorInducedLagThrustScale;
		}

		public double maxRotorInducedLagThrustLossPercent() {
			return (1.0 - minRotorInducedLagThrustScale) * 100.0;
		}

		public double maxRotorDynamicInflowTimeConstantSeconds() {
			return maxRotorDynamicInflowTimeConstantSeconds;
		}

		public double maxRotorAdvanceRatio() {
			return maxRotorAdvanceRatio;
		}

		public double maxRotorPropellerAdvanceRatioJ() {
			return maxRotorPropellerAdvanceRatioJ;
		}

		public double minRotorPropellerThrustScale() {
			return minRotorPropellerThrustScale;
		}

		public double minRotorPropellerPowerScale() {
			return minRotorPropellerPowerScale;
		}

		public double minRotorAxialGustThrustScale() {
			return minRotorAxialGustThrustScale;
		}

		public double maxRotorAxialGustThrustScale() {
			return maxRotorAxialGustThrustScale;
		}

		public double maxRotorReverseFlowInboardFraction() {
			return maxRotorReverseFlowInboardFraction;
		}

		public double maxRotorTipMach() {
			return maxRotorTipMach;
		}

		public double minRotorCompressibilityThrustScale() {
			return minRotorCompressibilityThrustScale;
		}

		public double maxRotorCompressibilityThrustLossPercent() {
			return (1.0 - minRotorCompressibilityThrustScale) * 100.0;
		}

		public double maxRotorLowReynoldsLoss() {
			return maxRotorLowReynoldsLoss;
		}

		public double maxRotorBladePassRippleIntensity() {
			return maxRotorBladePassRippleIntensity;
		}

		public double maxRotorInPlaneDragForceNewtons() {
			return maxRotorInPlaneDragForceNewtons;
		}

		public double maxRotorCoaxialLoadBias() {
			return maxRotorCoaxialLoadBias;
		}

		public double maxRotorCoaxialLoadBiasTarget() {
			return maxRotorCoaxialLoadBiasTarget;
		}

		public double maxRotorCoaxialLoadBiasClipping() {
			return maxRotorCoaxialLoadBiasClipping;
		}

		public double maxRotorCoaxialAllocationLoadFraction() {
			return maxRotorCoaxialAllocationLoadFraction;
		}

		public double maxRotorCoaxialAllocationCommandRatio() {
			return maxRotorCoaxialAllocationCommandRatio;
		}

		public double maxRotorCoaxialAllocationMechanicalGainPercent() {
			return maxRotorCoaxialAllocationMechanicalGainPercent;
		}

		public double maxRotorCoaxialAllocationElectricalGainPercent() {
			return maxRotorCoaxialAllocationElectricalGainPercent;
		}

		public double maxRotorCoaxialAllocationUncertaintyPercent() {
			return maxRotorCoaxialAllocationUncertaintyPercent;
		}

		public double minRotorWetThrustScale() {
			return minRotorWetThrustScale;
		}

		public double maxRotorWetThrustLossPercent() {
			return (1.0 - minRotorWetThrustScale) * 100.0;
		}

		public double maxRotorBladeDissymmetryTorqueNewtonMeters() {
			return maxRotorBladeDissymmetryTorqueNewtonMeters;
		}

		public double maxRotorWakeSwirlVelocityMetersPerSecond() {
			return maxRotorWakeSwirlVelocityMetersPerSecond;
		}

		public double maxRotorWindmillingIntensity() {
			return maxRotorWindmillingIntensity;
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

		public double maxMotorWindingResistanceScale() {
			return maxMotorWindingResistanceScale;
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

		public double maxRotorDamageVibration() {
			return maxRotorDamageVibration;
		}

		public double maxAirframeSeparatedFlowIntensity() {
			return maxAirframeSeparatedFlowIntensity;
		}

		public double maxAirframeBodyDragForceNewtons() {
			return maxAirframeBodyDragForceNewtons;
		}

		public double maxLinearDampingDragForceNewtons() {
			return maxLinearDampingDragForceNewtons;
		}

		public double maxGroundEffectLevelingTorqueNewtonMeters() {
			return maxGroundEffectLevelingTorqueNewtonMeters;
		}

		public double maxRotorConingIntensity() {
			return maxRotorConingIntensity;
		}

		public double maxRotorConingAngleRadians() {
			return maxRotorConingAngleRadians;
		}

		public double maxRotorArmFlexIntensity() {
			return maxRotorArmFlexIntensity;
		}

		public double maxRotorArmFlexDeflectionMeters() {
			return maxRotorArmFlexDeflectionMeters;
		}

		public double maxRotorArmFlexTiltRadians() {
			return maxRotorArmFlexTiltRadians;
		}

		public double maxRotorSurfaceScrapeIntensity() {
			return maxRotorSurfaceScrapeIntensity;
		}

		public double maxWindGustSpeedMetersPerSecond() {
			return maxWindGustSpeedMetersPerSecond;
		}

		public double maxWindDrydenSpeedMetersPerSecond() {
			return maxWindDrydenSpeedMetersPerSecond;
		}

		public double maxWindBurbleSpeedMetersPerSecond() {
			return maxWindBurbleSpeedMetersPerSecond;
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

		public double maxBarometerPressurePortErrorMeters() {
			return maxBarometerPressurePortErrorMeters;
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
