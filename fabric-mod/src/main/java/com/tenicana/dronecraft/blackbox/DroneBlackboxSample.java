package com.tenicana.dronecraft.blackbox;

import java.util.Locale;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class DroneBlackboxSample {
	public static final String CSV_HEADER = String.join(
			",",
			"game_time",
			"tick",
			"physics_substeps",
			"physics_dt_s",
			"physics_rate_hz",
			"x",
			"y",
			"z",
			"speed_mps",
			"pitch_deg",
			"yaw_deg",
			"roll_deg",
			"estimated_pitch_deg",
			"estimated_yaw_deg",
			"estimated_roll_deg",
			"attitude_error_pitch_deg",
			"attitude_error_yaw_deg",
			"attitude_error_roll_deg",
			"attitude_error_deg",
			"attitude_accel_trust",
			"body_pitch_rate_dps",
			"body_yaw_rate_dps",
			"body_roll_rate_dps",
			"gyro_pitch_rate_dps",
			"gyro_yaw_rate_dps",
			"gyro_roll_rate_dps",
			"gyro_bias_pitch_dps",
			"gyro_bias_yaw_dps",
			"gyro_bias_roll_dps",
			"gyro_clip",
			"gyro_notch_hz",
			"gyro_notch_attenuation",
			"gyro_notch_spread_hz",
			"gyro_rpm_harmonic_notch_attenuation",
			"gyro_blade_pass_notch_hz",
			"gyro_blade_pass_alias_1024_hz",
			"gyro_blade_pass_alias_4000_hz",
			"gyro_blade_pass_notch_attenuation",
			"gyro_blade_pass_notch_spread_hz",
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
			"imu_supply_noise",
			"throttle",
			"input_pitch",
			"input_yaw",
			"input_roll",
			"armed",
			"flight_mode",
			"raw_link_active",
			"control_throttle",
			"control_pitch",
			"control_yaw",
			"control_roll",
			"control_armed",
			"control_flight_mode",
			"control_link_active",
			"control_link_loss_s",
			"control_failsafe",
			"control_frame_age_s",
			"control_frame_interval_s",
			"control_frame_error",
			"motor_power",
			"esc_output",
			"esc_command_frame_age_s",
			"esc_command_frame_interval_s",
			"esc_command_error",
			"esc_desync",
			"esc_0_desync",
			"esc_1_desync",
			"esc_2_desync",
			"esc_3_desync",
			"motor_commutation_ripple",
			"motor_0_commutation_ripple",
			"motor_1_commutation_ripple",
			"motor_2_commutation_ripple",
			"motor_3_commutation_ripple",
			"motor_current_a",
			"motor_0_current_a",
			"motor_1_current_a",
			"motor_2_current_a",
			"motor_3_current_a",
			"motor_regen_current_a",
			"motor_0_regen_current_a",
			"motor_1_regen_current_a",
			"motor_2_regen_current_a",
			"motor_3_regen_current_a",
			"motor_phase_current_a",
			"motor_0_phase_current_a",
			"motor_1_phase_current_a",
			"motor_2_phase_current_a",
			"motor_3_phase_current_a",
			"motor_current_ripple_a",
			"motor_0_current_ripple_a",
			"motor_1_current_ripple_a",
			"motor_2_current_ripple_a",
			"motor_3_current_ripple_a",
			"motor_0_power",
			"motor_1_power",
			"motor_2_power",
			"motor_3_power",
			"avg_motor_rpm",
			"motor_0_rpm",
			"motor_1_rpm",
			"motor_2_rpm",
			"motor_3_rpm",
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
			"avg_motor_target_rpm",
			"motor_0_target_rpm",
			"motor_1_target_rpm",
			"motor_2_target_rpm",
			"motor_3_target_rpm",
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
			"motor_torque_ripple_nm",
			"motor_0_torque_ripple_nm",
			"motor_1_torque_ripple_nm",
			"motor_2_torque_ripple_nm",
			"motor_3_torque_ripple_nm",
			"avg_motor_mechanical_loss_torque_nm",
			"motor_0_mechanical_loss_torque_nm",
			"motor_1_mechanical_loss_torque_nm",
			"motor_2_mechanical_loss_torque_nm",
			"motor_3_mechanical_loss_torque_nm",
			"avg_motor_shaft_power_w",
			"motor_0_shaft_power_w",
			"motor_1_shaft_power_w",
			"motor_2_shaft_power_w",
			"motor_3_shaft_power_w",
			"motor_electrical_efficiency",
			"motor_0_electrical_efficiency",
			"motor_1_electrical_efficiency",
			"motor_2_electrical_efficiency",
			"motor_3_electrical_efficiency",
			"motor_voltage_headroom",
			"motor_0_voltage_headroom",
			"motor_1_voltage_headroom",
			"motor_2_voltage_headroom",
			"motor_3_voltage_headroom",
			"motor_winding_resistance_scale",
			"motor_0_winding_resistance_scale",
			"motor_1_winding_resistance_scale",
			"motor_2_winding_resistance_scale",
			"motor_3_winding_resistance_scale",
			"motor_temp_c",
			"motor_thermal_limit",
			"avg_motor_cooling_factor",
			"motor_0_cooling_factor",
			"motor_1_cooling_factor",
			"motor_2_cooling_factor",
			"motor_3_cooling_factor",
			"rotor_0_thrust_n",
			"rotor_1_thrust_n",
			"rotor_2_thrust_n",
			"rotor_3_thrust_n",
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
			"rotor_induced_velocity_mps",
			"rotor_induced_lag_thrust_scale",
			"rotor_dynamic_inflow_tau_s",
			"rotor_0_dynamic_inflow_tau_s",
			"rotor_1_dynamic_inflow_tau_s",
			"rotor_2_dynamic_inflow_tau_s",
			"rotor_3_dynamic_inflow_tau_s",
			"rotor_translational_lift",
			"rotor_0_translational_lift",
			"rotor_1_translational_lift",
			"rotor_2_translational_lift",
			"rotor_3_translational_lift",
			"rotor_advance_ratio",
			"rotor_0_advance_ratio",
			"rotor_1_advance_ratio",
			"rotor_2_advance_ratio",
			"rotor_3_advance_ratio",
			"rotor_prop_advance_ratio_j",
			"rotor_0_prop_advance_ratio_j",
			"rotor_1_prop_advance_ratio_j",
			"rotor_2_prop_advance_ratio_j",
			"rotor_3_prop_advance_ratio_j",
			"rotor_prop_thrust_scale",
			"rotor_0_prop_thrust_scale",
			"rotor_1_prop_thrust_scale",
			"rotor_2_prop_thrust_scale",
			"rotor_3_prop_thrust_scale",
			"rotor_prop_power_scale",
			"rotor_0_prop_power_scale",
			"rotor_1_prop_power_scale",
			"rotor_2_prop_power_scale",
			"rotor_3_prop_power_scale",
			"rotor_reverse_flow_fraction",
			"rotor_0_reverse_flow_fraction",
			"rotor_1_reverse_flow_fraction",
			"rotor_2_reverse_flow_fraction",
			"rotor_3_reverse_flow_fraction",
			"rotor_tip_mach",
			"rotor_0_tip_mach",
			"rotor_1_tip_mach",
			"rotor_2_tip_mach",
			"rotor_3_tip_mach",
			"rotor_compressibility_thrust_scale",
			"rotor_0_compressibility_thrust_scale",
			"rotor_1_compressibility_thrust_scale",
			"rotor_2_compressibility_thrust_scale",
			"rotor_3_compressibility_thrust_scale",
			"rotor_low_reynolds_loss",
			"rotor_0_low_reynolds_loss",
			"rotor_1_low_reynolds_loss",
			"rotor_2_low_reynolds_loss",
			"rotor_3_low_reynolds_loss",
			"rotor_blade_aoa_deg",
			"rotor_0_blade_aoa_deg",
			"rotor_1_blade_aoa_deg",
			"rotor_2_blade_aoa_deg",
			"rotor_3_blade_aoa_deg",
			"rotor_blade_element_stall",
			"rotor_0_blade_element_stall",
			"rotor_1_blade_element_stall",
			"rotor_2_blade_element_stall",
			"rotor_3_blade_element_stall",
			"rotor_blade_dissymmetry",
			"rotor_0_blade_dissymmetry",
			"rotor_1_blade_dissymmetry",
			"rotor_2_blade_dissymmetry",
			"rotor_3_blade_dissymmetry",
			"rotor_blade_pass_ripple",
			"rotor_0_blade_pass_ripple",
			"rotor_1_blade_pass_ripple",
			"rotor_2_blade_pass_ripple",
			"rotor_3_blade_pass_ripple",
			"rotor_aerodynamic_load",
			"rotor_0_aerodynamic_load",
			"rotor_1_aerodynamic_load",
			"rotor_2_aerodynamic_load",
			"rotor_3_aerodynamic_load",
			"rotor_in_plane_drag_force_n",
			"rotor_0_in_plane_drag_force_n",
			"rotor_1_in_plane_drag_force_n",
			"rotor_2_in_plane_drag_force_n",
			"rotor_3_in_plane_drag_force_n",
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
			"rotor_coaxial_load_bias",
			"rotor_0_coaxial_load_bias",
			"rotor_1_coaxial_load_bias",
			"rotor_2_coaxial_load_bias",
			"rotor_3_coaxial_load_bias",
			"rotor_coaxial_load_bias_target",
			"rotor_coaxial_load_bias_clipping",
			"rotor_coaxial_allocation_load",
			"rotor_coaxial_allocation_ratio",
			"rotor_coaxial_allocation_mech_gain_pct",
			"rotor_coaxial_allocation_elec_gain_pct",
			"rotor_wet_thrust_scale",
			"rotor_0_wet_thrust_scale",
			"rotor_1_wet_thrust_scale",
			"rotor_2_wet_thrust_scale",
			"rotor_3_wet_thrust_scale",
			"rotor_wake_swirl_mps",
			"rotor_0_wake_swirl_mps",
			"rotor_1_wake_swirl_mps",
			"rotor_2_wake_swirl_mps",
			"rotor_3_wake_swirl_mps",
			"rotor_windmilling",
			"rotor_0_windmilling",
			"rotor_1_windmilling",
			"rotor_2_windmilling",
			"rotor_3_windmilling",
			"rotor_wake_swirl_pitch_torque_nm",
			"rotor_wake_swirl_yaw_torque_nm",
			"rotor_wake_swirl_roll_torque_nm",
			"rotor_blade_dissymmetry_pitch_torque_nm",
			"rotor_blade_dissymmetry_yaw_torque_nm",
			"rotor_blade_dissymmetry_roll_torque_nm",
			"rotor_skew_pitch_torque_nm",
			"rotor_skew_yaw_torque_nm",
			"rotor_skew_roll_torque_nm",
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
			"rotor_flapping_force_n",
			"rotor_flapping_tilt_deg",
			"rotor_0_flapping_tilt_deg",
			"rotor_1_flapping_tilt_deg",
			"rotor_2_flapping_tilt_deg",
			"rotor_3_flapping_tilt_deg",
			"rotor_coning",
			"rotor_0_coning",
			"rotor_1_coning",
			"rotor_2_coning",
			"rotor_3_coning",
			"rotor_coning_angle_deg",
			"rotor_0_coning_angle_deg",
			"rotor_1_coning_angle_deg",
			"rotor_2_coning_angle_deg",
			"rotor_3_coning_angle_deg",
			"rotor_vibration",
			"rotor_arm_flex",
			"rotor_0_arm_flex",
			"rotor_1_arm_flex",
			"rotor_2_arm_flex",
			"rotor_3_arm_flex",
			"rotor_arm_flex_deflection_mm",
			"rotor_0_arm_flex_deflection_mm",
			"rotor_1_arm_flex_deflection_mm",
			"rotor_2_arm_flex_deflection_mm",
			"rotor_3_arm_flex_deflection_mm",
			"rotor_arm_flex_tilt_deg",
			"rotor_0_arm_flex_tilt_deg",
			"rotor_1_arm_flex_tilt_deg",
			"rotor_2_arm_flex_tilt_deg",
			"rotor_3_arm_flex_tilt_deg",
			"rotor_surface_scrape",
			"rotor_0_surface_scrape",
			"rotor_1_surface_scrape",
			"rotor_2_surface_scrape",
			"rotor_3_surface_scrape",
			"rotor_stall_intensity",
			"battery_voltage",
			"battery_ocv_v",
			"battery_ohmic_sag_v",
			"battery_transient_sag_v",
			"battery_slow_polarization_v",
			"battery_effective_resistance_ohm",
			"battery_20pct_sag_current_a",
			"battery_20pct_sag_current_margin",
			"battery_resistance_aging_scale",
			"battery_capacity_aging_scale",
			"battery_polarization_resistance_scale",
			"battery_equivalent_cycles",
			"battery_regen_current_a",
			"battery_voltage_spike_v",
			"battery_bus_ripple_v",
			"battery_soc",
			"battery_current_a",
			"battery_current_limit",
			"battery_power_limit",
			"battery_temp_c",
			"battery_cooling_factor",
			"battery_thermal_limit",
			"frame_health",
			"rotor_health",
			"rotor_0_health",
			"rotor_1_health",
			"rotor_2_health",
			"rotor_3_health",
			"collision_severity",
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
			"prop_strike",
			"prop_strike_rotor",
			"prop_strike_severity",
			"prop_strike_count",
			"prop_strike_0_severity",
			"prop_strike_1_severity",
			"prop_strike_2_severity",
			"prop_strike_3_severity",
			"wind_x_mps",
			"wind_y_mps",
			"wind_z_mps",
			"effective_wind_x_mps",
			"effective_wind_y_mps",
			"effective_wind_z_mps",
			"wind_gust_speed_mps",
			"wind_shear_accel_mps2",
			"air_density_ratio",
			"effective_air_density_ratio",
			"ambient_temperature_c",
			"ground_clearance_m",
			"ground_effect_multiplier",
			"ceiling_clearance_m",
			"ceiling_effect_multiplier",
			"env_thrust_asymmetry",
			"rotor_0_env_thrust_multiplier",
			"rotor_1_env_thrust_multiplier",
			"rotor_2_env_thrust_multiplier",
			"rotor_3_env_thrust_multiplier",
			"rotor_flow_obstruction",
			"rotor_0_flow_obstruction",
			"rotor_1_flow_obstruction",
			"rotor_2_flow_obstruction",
			"rotor_3_flow_obstruction",
			"propwash_intensity",
			"propwash_wake_intensity",
			"vortex_ring_state",
			"vortex_ring_thrust_buffet",
			"vortex_ring_max_thrust_buffet",
			"vortex_ring_buffet_force_x_n",
			"vortex_ring_buffet_force_y_n",
			"vortex_ring_buffet_force_z_n",
			"vortex_ring_buffet_force_n",
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
			"drone_wake_intensity",
			"water_immersion",
			"precipitation_wetness",
			"rotor_0_water_immersion",
			"rotor_1_water_immersion",
			"rotor_2_water_immersion",
			"rotor_3_water_immersion",
			"wind_turbulence_pitch_torque_nm",
			"wind_turbulence_yaw_torque_nm",
			"wind_turbulence_roll_torque_nm",
			"mixer_saturation",
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
			"pid_attenuation",
			"pid_integral_relax",
			"pid_integral_relax_pitch",
			"pid_integral_relax_yaw",
			"pid_integral_relax_roll",
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
			"tune_mass_kg",
			"tune_inertia_x_kgm2",
			"tune_inertia_y_kgm2",
			"tune_inertia_z_kgm2",
			"tune_cg_x_m",
			"tune_cg_y_m",
			"tune_cg_z_m",
			"tune_imu_x_m",
			"tune_imu_y_m",
			"tune_imu_z_m",
			"tune_cp_x_m",
			"tune_cp_y_m",
			"tune_cp_z_m",
			"tune_angular_drag",
			"tune_motor_tau_s",
			"tune_esc_curve_exp",
			"tune_esc_slew_rate",
			"tune_voltage_compensation",
			"tune_motor_heat_cps",
			"tune_motor_cooling",
			"tune_motor_temp_limit_c",
			"tune_motor_temp_cutoff_c",
			"tune_battery_nominal_v",
			"tune_battery_empty_v",
			"tune_battery_resistance_ohm",
			"tune_battery_capacity_ah",
			"tune_battery_max_current_a",
			"tune_rotor_max_thrust_n",
			"tune_rotor_thrust_coefficient",
			"tune_rotor_radius_m",
			"tune_rotor_blade_pitch_m",
			"tune_rotor_pitch_to_diameter",
			"tune_rotor_pitch_angle_70r_deg",
			"tune_rotor_chord_m",
			"tune_rotor_chord_to_radius",
			"tune_rotor_blade_count",
			"tune_rotor_transverse_lift",
			"tune_rotor_axial_loss",
			"tune_rotor_flapping",
			"tune_rotor_stall_loss",
			"tune_rotor_imbalance",
			"tune_pitch_rate_dps",
			"tune_yaw_rate_dps",
			"tune_roll_rate_dps",
			"tune_pitch_p",
			"tune_yaw_p",
			"tune_roll_p",
			"tune_pitch_i",
			"tune_yaw_i",
			"tune_roll_i",
			"tune_pitch_d",
			"tune_yaw_d",
			"tune_roll_d",
			"tune_feedforward",
			"tune_dterm_lpf_hz",
			"tune_anti_gravity",
			"tune_tpa_breakpoint",
			"tune_tpa_strength",
			"tune_iterm_relax",
			"tune_pitch_expo",
			"tune_yaw_expo",
			"tune_roll_expo",
			"tune_pitch_super_rate",
			"tune_yaw_super_rate",
			"tune_roll_super_rate",
			"tune_level_angle_deg",
			"tune_level_gain",
			"tune_horizon_start",
			"tune_horizon_end",
			"tune_linear_drag",
			"tune_rotor_disk_drag",
			"tune_rotor_yaw_torque_m",
			"tune_rotor_outward_cant_deg",
			"tune_rotor_inertia_kgm2",
			"tune_rotor_inflow_tau_s",
			"tune_rotor_inflow_lag",
			"tune_propwash_start_mps",
			"tune_propwash_full_mps",
			"tune_propwash_torque_nm",
			"tune_motor_idle",
			"tune_airmode_strength",
			"tune_gyro_lpf_hz",
			"tune_gyro_noise_rps",
			"tune_control_latency_s",
			"tune_attitude_accel_gain",
			"tune_attitude_accel_trust_mps2",
			"tune_rc_smoothing_s",
			"tune_rc_latency_s",
			"tune_rc_failsafe_s",
			"tune_rc_frame_rate_hz",
			"tune_rc_resolution_steps",
			"tune_esc_command_frame_rate_hz",
			"tune_esc_command_resolution_steps",
			"tune_esc_down_slew_rate",
			"tune_esc_deadband",
			"tune_motor_brake",
			"tune_accel_lpf_hz",
			"tune_accel_noise_mps2",
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
			"airframe_drag_along_flow_n",
			"airframe_drag_equivalent_linear_k",
			"airframe_drag_equivalent_cda_m2",
			"airframe_drag_imav_ratio",
			"ground_effect_drag_x_n",
			"ground_effect_drag_y_n",
			"ground_effect_drag_z_n",
			"ground_effect_drag_n",
			"rotor_wash_drag_x_n",
			"rotor_wash_drag_y_n",
			"rotor_wash_drag_z_n",
			"rotor_wash_drag_n",
			"rotor_wall_effect_x_n",
			"rotor_wall_effect_y_n",
			"rotor_wall_effect_z_n",
			"rotor_wall_effect_n",
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
			"airframe_rotor_count",
			"motor_4_power",
			"motor_5_power",
			"motor_6_power",
			"motor_7_power",
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
			"motor_4_regen_current_a",
			"motor_5_regen_current_a",
			"motor_6_regen_current_a",
			"motor_7_regen_current_a",
			"motor_4_phase_current_a",
			"motor_5_phase_current_a",
			"motor_6_phase_current_a",
			"motor_7_phase_current_a",
			"motor_4_current_ripple_a",
			"motor_5_current_ripple_a",
			"motor_6_current_ripple_a",
			"motor_7_current_ripple_a",
			"motor_4_commutation_ripple",
			"motor_5_commutation_ripple",
			"motor_6_commutation_ripple",
			"motor_7_commutation_ripple",
			"motor_4_electrical_efficiency",
			"motor_5_electrical_efficiency",
			"motor_6_electrical_efficiency",
			"motor_7_electrical_efficiency",
			"motor_4_voltage_headroom",
			"motor_5_voltage_headroom",
			"motor_6_voltage_headroom",
			"motor_7_voltage_headroom",
			"motor_4_winding_resistance_scale",
			"motor_5_winding_resistance_scale",
			"motor_6_winding_resistance_scale",
			"motor_7_winding_resistance_scale",
			"motor_4_mechanical_loss_torque_nm",
			"motor_5_mechanical_loss_torque_nm",
			"motor_6_mechanical_loss_torque_nm",
			"motor_7_mechanical_loss_torque_nm",
			"motor_4_torque_ripple_nm",
			"motor_5_torque_ripple_nm",
			"motor_6_torque_ripple_nm",
			"motor_7_torque_ripple_nm",
			"rotor_4_thrust_n",
			"rotor_5_thrust_n",
			"rotor_6_thrust_n",
			"rotor_7_thrust_n",
			"rotor_4_dynamic_inflow_tau_s",
			"rotor_5_dynamic_inflow_tau_s",
			"rotor_6_dynamic_inflow_tau_s",
			"rotor_7_dynamic_inflow_tau_s",
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
			"rotor_4_health",
			"rotor_5_health",
			"rotor_6_health",
			"rotor_7_health",
			"rotor_4_surface_scrape",
			"rotor_5_surface_scrape",
			"rotor_6_surface_scrape",
			"rotor_7_surface_scrape",
			"rotor_4_advance_ratio",
			"rotor_5_advance_ratio",
			"rotor_6_advance_ratio",
			"rotor_7_advance_ratio",
			"rotor_4_prop_advance_ratio_j",
			"rotor_5_prop_advance_ratio_j",
			"rotor_6_prop_advance_ratio_j",
			"rotor_7_prop_advance_ratio_j",
			"rotor_4_prop_thrust_scale",
			"rotor_5_prop_thrust_scale",
			"rotor_6_prop_thrust_scale",
			"rotor_7_prop_thrust_scale",
			"rotor_4_prop_power_scale",
			"rotor_5_prop_power_scale",
			"rotor_6_prop_power_scale",
			"rotor_7_prop_power_scale",
			"rotor_4_reverse_flow_fraction",
			"rotor_5_reverse_flow_fraction",
			"rotor_6_reverse_flow_fraction",
			"rotor_7_reverse_flow_fraction",
			"rotor_4_tip_mach",
			"rotor_5_tip_mach",
			"rotor_6_tip_mach",
			"rotor_7_tip_mach",
			"rotor_4_compressibility_thrust_scale",
			"rotor_5_compressibility_thrust_scale",
			"rotor_6_compressibility_thrust_scale",
			"rotor_7_compressibility_thrust_scale",
			"rotor_4_low_reynolds_loss",
			"rotor_5_low_reynolds_loss",
			"rotor_6_low_reynolds_loss",
			"rotor_7_low_reynolds_loss",
			"rotor_4_blade_aoa_deg",
			"rotor_5_blade_aoa_deg",
			"rotor_6_blade_aoa_deg",
			"rotor_7_blade_aoa_deg",
			"rotor_4_blade_element_stall",
			"rotor_5_blade_element_stall",
			"rotor_6_blade_element_stall",
			"rotor_7_blade_element_stall",
			"rotor_4_blade_dissymmetry",
			"rotor_5_blade_dissymmetry",
			"rotor_6_blade_dissymmetry",
			"rotor_7_blade_dissymmetry",
			"rotor_4_blade_pass_ripple",
			"rotor_5_blade_pass_ripple",
			"rotor_6_blade_pass_ripple",
			"rotor_7_blade_pass_ripple",
			"rotor_4_in_plane_drag_force_n",
			"rotor_5_in_plane_drag_force_n",
			"rotor_6_in_plane_drag_force_n",
			"rotor_7_in_plane_drag_force_n",
			"rotor_4_flapping_tilt_deg",
			"rotor_5_flapping_tilt_deg",
			"rotor_6_flapping_tilt_deg",
			"rotor_7_flapping_tilt_deg",
			"rotor_4_coning",
			"rotor_5_coning",
			"rotor_6_coning",
			"rotor_7_coning",
			"rotor_4_coning_angle_deg",
			"rotor_5_coning_angle_deg",
			"rotor_6_coning_angle_deg",
			"rotor_7_coning_angle_deg",
			"rotor_4_wake_interference",
			"rotor_5_wake_interference",
			"rotor_6_wake_interference",
			"rotor_7_wake_interference",
			"rotor_4_wake_thrust_scale",
			"rotor_5_wake_thrust_scale",
			"rotor_6_wake_thrust_scale",
			"rotor_7_wake_thrust_scale",
			"rotor_4_coaxial_load_bias",
			"rotor_5_coaxial_load_bias",
			"rotor_6_coaxial_load_bias",
			"rotor_7_coaxial_load_bias",
			"rotor_4_wet_thrust_scale",
			"rotor_5_wet_thrust_scale",
			"rotor_6_wet_thrust_scale",
			"rotor_7_wet_thrust_scale",
			"rotor_4_wake_swirl_mps",
			"rotor_5_wake_swirl_mps",
			"rotor_6_wake_swirl_mps",
			"rotor_7_wake_swirl_mps",
			"rotor_4_windmilling",
			"rotor_5_windmilling",
			"rotor_6_windmilling",
			"rotor_7_windmilling",
			"rotor_4_arm_flex",
			"rotor_5_arm_flex",
			"rotor_6_arm_flex",
			"rotor_7_arm_flex",
			"rotor_4_arm_flex_deflection_mm",
			"rotor_5_arm_flex_deflection_mm",
			"rotor_6_arm_flex_deflection_mm",
			"rotor_7_arm_flex_deflection_mm",
			"rotor_4_arm_flex_tilt_deg",
			"rotor_5_arm_flex_tilt_deg",
			"rotor_6_arm_flex_tilt_deg",
			"rotor_7_arm_flex_tilt_deg",
			"prop_strike_4_severity",
			"prop_strike_5_severity",
			"prop_strike_6_severity",
			"prop_strike_7_severity",
			"rotor_4_env_thrust_multiplier",
			"rotor_5_env_thrust_multiplier",
			"rotor_6_env_thrust_multiplier",
			"rotor_7_env_thrust_multiplier",
			"rotor_4_flow_obstruction",
			"rotor_5_flow_obstruction",
			"rotor_6_flow_obstruction",
			"rotor_7_flow_obstruction",
			"rotor_4_water_immersion",
			"rotor_5_water_immersion",
			"rotor_6_water_immersion",
			"rotor_7_water_immersion"
	);
	private static final int CSV_COLUMN_COUNT = CSV_HEADER.split(",", -1).length;

	private final String csvLine;

	private DroneBlackboxSample(String csvLine) {
		this.csvLine = csvLine;
	}

	public static DroneBlackboxSample from(
			long gameTime,
			int tickCount,
			DroneState state,
			DroneInput input,
			double motorPower,
			double frameHealth,
			double rotorHealth,
			double collisionSeverity,
			int propStrikeRotorIndex,
			double propStrikeSeverity,
			int propStrikeCount,
			double[] propStrikeSeverityByRotor,
			DroneEnvironment environment,
			DroneConfig config
	) {
		return from(
				gameTime,
				tickCount,
				1,
				0.0,
				state,
				input,
				motorPower,
				frameHealth,
				rotorHealth,
				collisionSeverity,
				propStrikeRotorIndex,
				propStrikeSeverity,
				propStrikeCount,
				propStrikeSeverityByRotor,
				environment,
				config
		);
	}

	public static DroneBlackboxSample from(
			long gameTime,
			int tickCount,
			int physicsSubsteps,
			double physicsDtSeconds,
			DroneState state,
			DroneInput input,
			double motorPower,
			double frameHealth,
			double rotorHealth,
			double collisionSeverity,
			int propStrikeRotorIndex,
			double propStrikeSeverity,
			int propStrikeCount,
			double[] propStrikeSeverityByRotor,
			DroneEnvironment environment,
			DroneConfig config
	) {
		Vec3 position = state.positionMeters();
		Vec3 euler = state.orientation().toEulerXYZRadians();
		Vec3 estimatedEuler = state.estimatedOrientation().toEulerXYZRadians();
		Vec3 attitudeError = state.attitudeEstimateErrorRadians();
		Vec3 rates = state.angularVelocityBodyRadiansPerSecond();
		Vec3 gyroRates = state.gyroAngularVelocityBodyRadiansPerSecond();
		Vec3 gyroBias = state.gyroBiasBodyRadiansPerSecond();
		Vec3 linearAcceleration = state.linearAccelerationWorldMetersPerSecondSquared();
		Vec3 accelerometer = state.accelerometerBodyMetersPerSecondSquared();
		Vec3 accelerometerBias = state.accelerometerBiasBodyMetersPerSecondSquared();
		DroneInput processedInput = state.processedControlInput();
		Vec3 targetRates = state.targetRatesBodyRadiansPerSecond();
		Vec3 rateError = state.rateErrorBodyRadiansPerSecond();
		Vec3 levelTarget = state.levelTargetAttitudeRadians();
		Vec3 levelError = state.levelAttitudeErrorRadians();
		Vec3 pidP = state.pidProportionalTorqueBodyNewtonMeters();
		Vec3 pidI = state.pidIntegralTorqueBodyNewtonMeters();
		Vec3 pidD = state.pidDerivativeTorqueBodyNewtonMeters();
		Vec3 pidFf = state.pidFeedForwardTorqueBodyNewtonMeters();
		Vec3 pidOutput = state.pidOutputTorqueBodyNewtonMeters();
		Vec3 wind = environment.windVelocityWorldMetersPerSecond();
		Vec3 propwashTorque = state.propwashTorqueBodyNewtonMeters();
		Vec3 vortexRingBuffetForce = state.vortexRingBuffetForceBodyNewtons();
		Vec3 airframeTorque = state.airframeAerodynamicTorqueBodyNewtonMeters();
		Vec3 pressureCenterTorque = state.airframePressureCenterTorqueBodyNewtonMeters();
		Vec3 airframeAngularDragTorque = state.airframeAngularDragTorqueBodyNewtonMeters();
		Vec3 airframeLift = state.airframeLiftForceBodyNewtons();
		Vec3 airframeBodyDrag = state.airframeBodyDragForceBodyNewtons();
		Vec3 linearDampingDrag = state.linearDampingDragForceWorldNewtons();
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
		Vec3 effectiveWind = state.effectiveWindVelocityWorldMetersPerSecond();
		Vec3 contactAngularImpulse = state.contactAngularImpulseBodyRadiansPerSecond();
		double[] motorPowers = state.motorPower(config);
		double[] motorTargetOmega = state.motorTargetOmegaRadiansPerSecond();
		double[] motorTrackingError = state.motorTrackingError();
		double[] motorActuatorAuthority = state.motorActuatorAuthority();
		double[] motorCurrents = state.motorCurrentAmps();
		double[] motorRegenerativeCurrents = state.motorRegenerativeCurrentAmps();
		double[] motorPhaseCurrents = state.motorPhaseCurrentAmps();
		double[] motorCurrentRipples = state.motorCurrentRippleAmps();
		double[] motorCommutationRipples = state.motorCommutationRippleIntensity();
		double[] motorTorqueRipples = state.motorTorqueRippleNewtonMeters();
		double[] motorElectricalEfficiency = state.motorElectricalEfficiency();
		double[] motorVoltageHeadroom = state.motorVoltageHeadroom();
		double[] motorWindingResistanceScale = state.motorWindingResistanceScale();
		double[] motorMechanicalLoss = state.motorMechanicalLossTorqueNewtonMeters();
		double[] motorTelemetryRpm = state.motorRpmTelemetryRpm();
		double[] motorTelemetryValidity = state.motorRpmTelemetryValidity();
		double[] rotorThrust = state.rotorThrustNewtons();
		Vec3[] rotorForceBody = state.rotorForceBodyNewtons();
		Vec3[] rotorTorqueBody = state.rotorTorqueBodyNewtonMeters();
		RotorSpec rotor = config.rotors().get(0);
		double groundClearance = Double.isFinite(environment.groundClearanceMeters()) ? environment.groundClearanceMeters() : -1.0;
		double ceilingClearance = Double.isFinite(environment.ceilingClearanceMeters()) ? environment.ceilingClearanceMeters() : -1.0;
		int sanitizedPhysicsSubsteps = Math.max(0, physicsSubsteps);
		double sanitizedPhysicsDtSeconds = Double.isFinite(physicsDtSeconds) && physicsDtSeconds > 0.0 ? physicsDtSeconds : 0.0;
		double physicsRateHertz = sanitizedPhysicsDtSeconds > 0.0 ? 1.0 / sanitizedPhysicsDtSeconds : 0.0;

		CsvRow row = new CsvRow();
		row.add(gameTime);
		row.add(tickCount);
		row.add(sanitizedPhysicsSubsteps);
		row.add(sanitizedPhysicsDtSeconds, "%.5f");
		row.add(physicsRateHertz, "%.3f");
		row.add(position.x(), "%.5f");
		row.add(position.y(), "%.5f");
		row.add(position.z(), "%.5f");
		row.add(state.speedMetersPerSecond(), "%.5f");
		row.add(Math.toDegrees(euler.x()), "%.4f");
		row.add(Math.toDegrees(euler.y()), "%.4f");
		row.add(Math.toDegrees(euler.z()), "%.4f");
		row.add(Math.toDegrees(estimatedEuler.x()), "%.4f");
		row.add(Math.toDegrees(estimatedEuler.y()), "%.4f");
		row.add(Math.toDegrees(estimatedEuler.z()), "%.4f");
		row.add(Math.toDegrees(attitudeError.x()), "%.4f");
		row.add(Math.toDegrees(attitudeError.y()), "%.4f");
		row.add(Math.toDegrees(attitudeError.z()), "%.4f");
		row.add(Math.toDegrees(attitudeError.length()), "%.4f");
		row.add(state.attitudeEstimatorAccelerometerTrust(), "%.4f");
		row.add(Math.toDegrees(rates.x()), "%.4f");
		row.add(Math.toDegrees(rates.y()), "%.4f");
		row.add(Math.toDegrees(rates.z()), "%.4f");
		row.add(Math.toDegrees(gyroRates.x()), "%.4f");
		row.add(Math.toDegrees(gyroRates.y()), "%.4f");
		row.add(Math.toDegrees(gyroRates.z()), "%.4f");
		row.add(Math.toDegrees(gyroBias.x()), "%.4f");
		row.add(Math.toDegrees(gyroBias.y()), "%.4f");
		row.add(Math.toDegrees(gyroBias.z()), "%.4f");
		row.add(state.gyroClipIntensity(), "%.5f");
		row.add(state.gyroDynamicNotchFrequencyHertz(), "%.3f");
		row.add(state.gyroDynamicNotchAttenuation(), "%.5f");
		row.add(state.gyroDynamicNotchSpreadHertz(), "%.3f");
		row.add(state.gyroRpmHarmonicNotchAttenuation(), "%.5f");
		row.add(state.gyroBladePassNotchFrequencyHertz(), "%.3f");
		row.add(DronePhysics.sampledFrequencyAliasHertz(state.gyroBladePassNotchFrequencyHertz(), 1024.0), "%.3f");
		row.add(DronePhysics.sampledFrequencyAliasHertz(state.gyroBladePassNotchFrequencyHertz(), 4000.0), "%.3f");
		row.add(state.gyroBladePassNotchAttenuation(), "%.5f");
		row.add(state.gyroBladePassNotchSpreadHertz(), "%.3f");
		row.add(linearAcceleration.x(), "%.4f");
		row.add(linearAcceleration.y(), "%.4f");
		row.add(linearAcceleration.z(), "%.4f");
		row.add(accelerometer.x(), "%.4f");
		row.add(accelerometer.y(), "%.4f");
		row.add(accelerometer.z(), "%.4f");
		row.add(accelerometerBias.x(), "%.4f");
		row.add(accelerometerBias.y(), "%.4f");
		row.add(accelerometerBias.z(), "%.4f");
		row.add(state.accelerometerClipIntensity(), "%.5f");
		row.add(state.imuSupplyNoiseIntensity(), "%.5f");
		row.add(input.throttle(), "%.5f");
		row.add(input.pitch(), "%.5f");
		row.add(input.yaw(), "%.5f");
		row.add(input.roll(), "%.5f");
		row.add(input.armed());
		row.add(input.flightMode().csvName());
		row.add(input.linkActive());
		row.add(processedInput.throttle(), "%.5f");
		row.add(processedInput.pitch(), "%.5f");
		row.add(processedInput.yaw(), "%.5f");
		row.add(processedInput.roll(), "%.5f");
		row.add(processedInput.armed());
		row.add(processedInput.flightMode().csvName());
		row.add(processedInput.linkActive());
		row.add(state.controlLinkLossSeconds(), "%.3f");
		row.add(state.controlFailsafeActive());
		row.add(state.controlFrameAgeSeconds(), "%.5f");
		row.add(state.controlFrameIntervalSeconds(), "%.5f");
		row.add(state.controlFrameError(), "%.6f");
		row.add(motorPower, "%.5f");
		row.add(state.averageEscOutputCommand(), "%.5f");
		row.add(state.escCommandFrameAgeSeconds(), "%.5f");
		row.add(state.escCommandFrameIntervalSeconds(), "%.5f");
		row.add(state.escCommandError(), "%.6f");
		row.add(state.maxEscDesyncIntensity(), "%.5f");
		row.add(escDesyncOrZero(state, 0), "%.5f");
		row.add(escDesyncOrZero(state, 1), "%.5f");
		row.add(escDesyncOrZero(state, 2), "%.5f");
		row.add(escDesyncOrZero(state, 3), "%.5f");
		row.add(state.averageMotorCommutationRippleIntensity(), "%.5f");
		row.add(valueOrZero(motorCommutationRipples, 0), "%.5f");
		row.add(valueOrZero(motorCommutationRipples, 1), "%.5f");
		row.add(valueOrZero(motorCommutationRipples, 2), "%.5f");
		row.add(valueOrZero(motorCommutationRipples, 3), "%.5f");
		row.add(state.averageMotorCurrentAmps(), "%.3f");
		row.add(valueOrZero(motorCurrents, 0), "%.3f");
		row.add(valueOrZero(motorCurrents, 1), "%.3f");
		row.add(valueOrZero(motorCurrents, 2), "%.3f");
		row.add(valueOrZero(motorCurrents, 3), "%.3f");
		row.add(state.averageMotorRegenerativeCurrentAmps(), "%.3f");
		row.add(valueOrZero(motorRegenerativeCurrents, 0), "%.3f");
		row.add(valueOrZero(motorRegenerativeCurrents, 1), "%.3f");
		row.add(valueOrZero(motorRegenerativeCurrents, 2), "%.3f");
		row.add(valueOrZero(motorRegenerativeCurrents, 3), "%.3f");
		row.add(state.averageMotorPhaseCurrentAmps(), "%.3f");
		row.add(valueOrZero(motorPhaseCurrents, 0), "%.3f");
		row.add(valueOrZero(motorPhaseCurrents, 1), "%.3f");
		row.add(valueOrZero(motorPhaseCurrents, 2), "%.3f");
		row.add(valueOrZero(motorPhaseCurrents, 3), "%.3f");
		row.add(state.averageMotorCurrentRippleAmps(), "%.3f");
		row.add(valueOrZero(motorCurrentRipples, 0), "%.3f");
		row.add(valueOrZero(motorCurrentRipples, 1), "%.3f");
		row.add(valueOrZero(motorCurrentRipples, 2), "%.3f");
		row.add(valueOrZero(motorCurrentRipples, 3), "%.3f");
		row.add(valueOrZero(motorPowers, 0), "%.5f");
		row.add(valueOrZero(motorPowers, 1), "%.5f");
		row.add(valueOrZero(motorPowers, 2), "%.5f");
		row.add(valueOrZero(motorPowers, 3), "%.5f");
		row.add(state.averageMotorRpm(), "%.1f");
		row.add(state.motorRpm(0), "%.1f");
		row.add(state.motorRpm(1), "%.1f");
		row.add(state.motorRpm(2), "%.1f");
		row.add(state.motorRpm(3), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(state.averageMotorRpmTelemetryRpm()), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(valueOrZero(motorTelemetryRpm, 0)), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(valueOrZero(motorTelemetryRpm, 1)), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(valueOrZero(motorTelemetryRpm, 2)), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(valueOrZero(motorTelemetryRpm, 3)), "%.1f");
		row.add(DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
				state.averageMotorRpmTelemetryRpm(),
				state.averageMotorRpmTelemetryValidity()
		), "%.1f");
		row.add(motorTelemetryEIntervalMicros(motorTelemetryRpm, motorTelemetryValidity, 0), "%.1f");
		row.add(motorTelemetryEIntervalMicros(motorTelemetryRpm, motorTelemetryValidity, 1), "%.1f");
		row.add(motorTelemetryEIntervalMicros(motorTelemetryRpm, motorTelemetryValidity, 2), "%.1f");
		row.add(motorTelemetryEIntervalMicros(motorTelemetryRpm, motorTelemetryValidity, 3), "%.1f");
		row.add(state.averageMotorRpmTelemetryValidity(), "%.3f");
		row.add(valueOrZero(motorTelemetryValidity, 0), "%.3f");
		row.add(valueOrZero(motorTelemetryValidity, 1), "%.3f");
		row.add(valueOrZero(motorTelemetryValidity, 2), "%.3f");
		row.add(valueOrZero(motorTelemetryValidity, 3), "%.3f");
		row.add(state.averageMotorTargetRpm(), "%.1f");
		row.add(state.motorTargetRpm(0), "%.1f");
		row.add(state.motorTargetRpm(1), "%.1f");
		row.add(state.motorTargetRpm(2), "%.1f");
		row.add(state.motorTargetRpm(3), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(state.averageMotorTargetRpm()), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(state.motorTargetRpm(0)), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(state.motorTargetRpm(1)), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(state.motorTargetRpm(2)), "%.1f");
		row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(state.motorTargetRpm(3)), "%.1f");
		row.add(DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(state.averageMotorTargetRpm()), "%.1f");
		row.add(DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(state.motorTargetRpm(0)), "%.1f");
		row.add(DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(state.motorTargetRpm(1)), "%.1f");
		row.add(DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(state.motorTargetRpm(2)), "%.1f");
		row.add(DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(state.motorTargetRpm(3)), "%.1f");
		row.add(state.averageMotorTrackingError(), "%.5f");
		row.add(state.motorTrackingError(0), "%.5f");
		row.add(state.motorTrackingError(1), "%.5f");
		row.add(state.motorTrackingError(2), "%.5f");
		row.add(state.motorTrackingError(3), "%.5f");
		row.add(state.averageMotorActuatorAuthority(), "%.5f");
		row.add(state.motorActuatorAuthority(0), "%.5f");
		row.add(state.motorActuatorAuthority(1), "%.5f");
		row.add(state.motorActuatorAuthority(2), "%.5f");
		row.add(state.motorActuatorAuthority(3), "%.5f");
		row.add(state.averageMotorAngularAccelerationRadiansPerSecondSquared(), "%.3f");
		row.add(state.motorAngularAccelerationRadiansPerSecondSquared(0), "%.3f");
		row.add(state.motorAngularAccelerationRadiansPerSecondSquared(1), "%.3f");
		row.add(state.motorAngularAccelerationRadiansPerSecondSquared(2), "%.3f");
		row.add(state.motorAngularAccelerationRadiansPerSecondSquared(3), "%.3f");
		row.add(state.averageMotorAerodynamicTorqueNewtonMeters(), "%.6f");
		row.add(state.motorAerodynamicTorqueNewtonMeters(0), "%.6f");
		row.add(state.motorAerodynamicTorqueNewtonMeters(1), "%.6f");
		row.add(state.motorAerodynamicTorqueNewtonMeters(2), "%.6f");
		row.add(state.motorAerodynamicTorqueNewtonMeters(3), "%.6f");
		row.add(state.averageMotorTorqueRippleNewtonMeters(), "%.6f");
		row.add(valueOrZero(motorTorqueRipples, 0), "%.6f");
		row.add(valueOrZero(motorTorqueRipples, 1), "%.6f");
		row.add(valueOrZero(motorTorqueRipples, 2), "%.6f");
		row.add(valueOrZero(motorTorqueRipples, 3), "%.6f");
		row.add(state.averageMotorMechanicalLossTorqueNewtonMeters(), "%.6f");
		row.add(valueOrZero(motorMechanicalLoss, 0), "%.6f");
		row.add(valueOrZero(motorMechanicalLoss, 1), "%.6f");
		row.add(valueOrZero(motorMechanicalLoss, 2), "%.6f");
		row.add(valueOrZero(motorMechanicalLoss, 3), "%.6f");
		row.add(state.averageMotorShaftPowerWatts(), "%.3f");
		row.add(state.motorShaftPowerWatts(0), "%.3f");
		row.add(state.motorShaftPowerWatts(1), "%.3f");
		row.add(state.motorShaftPowerWatts(2), "%.3f");
		row.add(state.motorShaftPowerWatts(3), "%.3f");
		row.add(state.averageMotorElectricalEfficiency(), "%.5f");
		row.add(valueOrZero(motorElectricalEfficiency, 0), "%.5f");
		row.add(valueOrZero(motorElectricalEfficiency, 1), "%.5f");
		row.add(valueOrZero(motorElectricalEfficiency, 2), "%.5f");
		row.add(valueOrZero(motorElectricalEfficiency, 3), "%.5f");
		row.add(state.averageMotorVoltageHeadroom(), "%.5f");
		row.add(valueOrZero(motorVoltageHeadroom, 0), "%.5f");
		row.add(valueOrZero(motorVoltageHeadroom, 1), "%.5f");
		row.add(valueOrZero(motorVoltageHeadroom, 2), "%.5f");
		row.add(valueOrZero(motorVoltageHeadroom, 3), "%.5f");
		row.add(state.averageMotorWindingResistanceScale(), "%.5f");
		row.add(valueOrOne(motorWindingResistanceScale, 0), "%.5f");
		row.add(valueOrOne(motorWindingResistanceScale, 1), "%.5f");
		row.add(valueOrOne(motorWindingResistanceScale, 2), "%.5f");
		row.add(valueOrOne(motorWindingResistanceScale, 3), "%.5f");
		row.add(state.maxMotorTemperatureCelsius(), "%.2f");
		row.add(state.motorThermalLimit(), "%.5f");
		row.add(state.averageMotorCoolingFactor(), "%.5f");
		row.add(state.motorCoolingFactor(0), "%.5f");
		row.add(state.motorCoolingFactor(1), "%.5f");
		row.add(state.motorCoolingFactor(2), "%.5f");
		row.add(state.motorCoolingFactor(3), "%.5f");
		row.add(valueOrZero(rotorThrust, 0), "%.4f");
		row.add(valueOrZero(rotorThrust, 1), "%.4f");
		row.add(valueOrZero(rotorThrust, 2), "%.4f");
		row.add(valueOrZero(rotorThrust, 3), "%.4f");
		addRotorForceColumns(row, rotorForceBody, 0);
		addRotorForceColumns(row, rotorForceBody, 1);
		addRotorForceColumns(row, rotorForceBody, 2);
		addRotorForceColumns(row, rotorForceBody, 3);
		addRotorTorqueColumns(row, rotorTorqueBody, 0);
		addRotorTorqueColumns(row, rotorTorqueBody, 1);
		addRotorTorqueColumns(row, rotorTorqueBody, 2);
		addRotorTorqueColumns(row, rotorTorqueBody, 3);
		row.add(state.averageRotorInducedVelocityMetersPerSecond(), "%.4f");
		row.add(state.minRotorInducedLagThrustScale(), "%.5f");
		row.add(state.averageRotorDynamicInflowTimeConstantSeconds(), "%.5f");
		row.add(rotorDynamicInflowTimeConstantOrZero(state, 0), "%.5f");
		row.add(rotorDynamicInflowTimeConstantOrZero(state, 1), "%.5f");
		row.add(rotorDynamicInflowTimeConstantOrZero(state, 2), "%.5f");
		row.add(rotorDynamicInflowTimeConstantOrZero(state, 3), "%.5f");
		row.add(state.averageRotorTranslationalLiftIntensity(), "%.5f");
		row.add(rotorTranslationalLiftOrZero(state, 0), "%.5f");
		row.add(rotorTranslationalLiftOrZero(state, 1), "%.5f");
		row.add(rotorTranslationalLiftOrZero(state, 2), "%.5f");
		row.add(rotorTranslationalLiftOrZero(state, 3), "%.5f");
		row.add(state.averageRotorAdvanceRatio(), "%.5f");
		row.add(rotorAdvanceRatioOrZero(state, 0), "%.5f");
		row.add(rotorAdvanceRatioOrZero(state, 1), "%.5f");
		row.add(rotorAdvanceRatioOrZero(state, 2), "%.5f");
		row.add(rotorAdvanceRatioOrZero(state, 3), "%.5f");
		row.add(state.averageRotorPropellerAdvanceRatioJ(), "%.5f");
		row.add(rotorPropellerAdvanceRatioJOrZero(state, 0), "%.5f");
		row.add(rotorPropellerAdvanceRatioJOrZero(state, 1), "%.5f");
		row.add(rotorPropellerAdvanceRatioJOrZero(state, 2), "%.5f");
		row.add(rotorPropellerAdvanceRatioJOrZero(state, 3), "%.5f");
		row.add(state.averageRotorPropellerThrustScale(), "%.5f");
		row.add(rotorPropellerThrustScaleOrOne(state, 0), "%.5f");
		row.add(rotorPropellerThrustScaleOrOne(state, 1), "%.5f");
		row.add(rotorPropellerThrustScaleOrOne(state, 2), "%.5f");
		row.add(rotorPropellerThrustScaleOrOne(state, 3), "%.5f");
		row.add(state.averageRotorPropellerPowerScale(), "%.5f");
		row.add(rotorPropellerPowerScaleOrOne(state, 0), "%.5f");
		row.add(rotorPropellerPowerScaleOrOne(state, 1), "%.5f");
		row.add(rotorPropellerPowerScaleOrOne(state, 2), "%.5f");
		row.add(rotorPropellerPowerScaleOrOne(state, 3), "%.5f");
		row.add(state.averageRotorReverseFlowInboardFraction(), "%.5f");
		row.add(rotorReverseFlowFractionOrZero(state, 0), "%.5f");
		row.add(rotorReverseFlowFractionOrZero(state, 1), "%.5f");
		row.add(rotorReverseFlowFractionOrZero(state, 2), "%.5f");
		row.add(rotorReverseFlowFractionOrZero(state, 3), "%.5f");
		row.add(state.averageRotorTipMach(), "%.5f");
		row.add(rotorTipMachOrZero(state, 0), "%.5f");
		row.add(rotorTipMachOrZero(state, 1), "%.5f");
		row.add(rotorTipMachOrZero(state, 2), "%.5f");
		row.add(rotorTipMachOrZero(state, 3), "%.5f");
		row.add(state.averageRotorCompressibilityThrustScale(), "%.5f");
		row.add(rotorCompressibilityThrustScaleOrOne(state, 0), "%.5f");
		row.add(rotorCompressibilityThrustScaleOrOne(state, 1), "%.5f");
		row.add(rotorCompressibilityThrustScaleOrOne(state, 2), "%.5f");
		row.add(rotorCompressibilityThrustScaleOrOne(state, 3), "%.5f");
		row.add(state.averageRotorLowReynoldsLoss(), "%.5f");
		row.add(rotorLowReynoldsLossOrZero(state, 0), "%.5f");
		row.add(rotorLowReynoldsLossOrZero(state, 1), "%.5f");
		row.add(rotorLowReynoldsLossOrZero(state, 2), "%.5f");
		row.add(rotorLowReynoldsLossOrZero(state, 3), "%.5f");
		row.add(Math.toDegrees(state.averageRotorBladeAngleOfAttackRadians()), "%.4f");
		row.add(rotorBladeAngleOfAttackDegreesOrZero(state, 0), "%.4f");
		row.add(rotorBladeAngleOfAttackDegreesOrZero(state, 1), "%.4f");
		row.add(rotorBladeAngleOfAttackDegreesOrZero(state, 2), "%.4f");
		row.add(rotorBladeAngleOfAttackDegreesOrZero(state, 3), "%.4f");
		row.add(state.averageRotorBladeElementStallIntensity(), "%.5f");
		row.add(rotorBladeElementStallOrZero(state, 0), "%.5f");
		row.add(rotorBladeElementStallOrZero(state, 1), "%.5f");
		row.add(rotorBladeElementStallOrZero(state, 2), "%.5f");
		row.add(rotorBladeElementStallOrZero(state, 3), "%.5f");
		row.add(state.averageRotorBladeDissymmetryIntensity(), "%.5f");
		row.add(rotorBladeDissymmetryOrZero(state, 0), "%.5f");
		row.add(rotorBladeDissymmetryOrZero(state, 1), "%.5f");
		row.add(rotorBladeDissymmetryOrZero(state, 2), "%.5f");
		row.add(rotorBladeDissymmetryOrZero(state, 3), "%.5f");
		row.add(state.averageRotorBladePassRippleIntensity(), "%.5f");
		row.add(rotorBladePassRippleOrZero(state, 0), "%.5f");
		row.add(rotorBladePassRippleOrZero(state, 1), "%.5f");
		row.add(rotorBladePassRippleOrZero(state, 2), "%.5f");
		row.add(rotorBladePassRippleOrZero(state, 3), "%.5f");
		row.add(state.averageRotorAerodynamicLoadFactor(), "%.5f");
		row.add(rotorAerodynamicLoadOrZero(state, 0), "%.5f");
		row.add(rotorAerodynamicLoadOrZero(state, 1), "%.5f");
		row.add(rotorAerodynamicLoadOrZero(state, 2), "%.5f");
		row.add(rotorAerodynamicLoadOrZero(state, 3), "%.5f");
		row.add(state.averageRotorInPlaneDragForceNewtons(), "%.4f");
		row.add(rotorInPlaneDragForceOrZero(state, 0), "%.4f");
		row.add(rotorInPlaneDragForceOrZero(state, 1), "%.4f");
		row.add(rotorInPlaneDragForceOrZero(state, 2), "%.4f");
		row.add(rotorInPlaneDragForceOrZero(state, 3), "%.4f");
		row.add(state.rotorInflowSkewIntensity(), "%.5f");
		row.add(state.averageRotorWakeInterferenceIntensity(), "%.5f");
		row.add(rotorWakeInterferenceOrZero(state, 0), "%.5f");
		row.add(rotorWakeInterferenceOrZero(state, 1), "%.5f");
		row.add(rotorWakeInterferenceOrZero(state, 2), "%.5f");
		row.add(rotorWakeInterferenceOrZero(state, 3), "%.5f");
		row.add(state.averageRotorWakeThrustScale(), "%.5f");
		row.add(rotorWakeThrustScaleOrOne(state, 0), "%.5f");
		row.add(rotorWakeThrustScaleOrOne(state, 1), "%.5f");
		row.add(rotorWakeThrustScaleOrOne(state, 2), "%.5f");
		row.add(rotorWakeThrustScaleOrOne(state, 3), "%.5f");
		row.add(state.averageAbsRotorCoaxialLoadBias(), "%.5f");
		row.add(rotorCoaxialLoadBiasOrZero(state, 0), "%.5f");
		row.add(rotorCoaxialLoadBiasOrZero(state, 1), "%.5f");
		row.add(rotorCoaxialLoadBiasOrZero(state, 2), "%.5f");
		row.add(rotorCoaxialLoadBiasOrZero(state, 3), "%.5f");
		row.add(state.averageAbsRotorCoaxialLoadBiasTarget(), "%.5f");
		row.add(state.maxRotorCoaxialLoadBiasClipping(), "%.5f");
		row.add(state.averageRotorCoaxialAllocationLoadFraction(), "%.5f");
		row.add(state.maxRotorCoaxialAllocationCommandRatio(), "%.5f");
		row.add(state.maxRotorCoaxialAllocationMechanicalGainPercent(), "%.5f");
		row.add(state.maxRotorCoaxialAllocationElectricalGainPercent(), "%.5f");
		row.add(state.averageRotorWetThrustScale(), "%.5f");
		row.add(rotorWetThrustScaleOrOne(state, 0), "%.5f");
		row.add(rotorWetThrustScaleOrOne(state, 1), "%.5f");
		row.add(rotorWetThrustScaleOrOne(state, 2), "%.5f");
		row.add(rotorWetThrustScaleOrOne(state, 3), "%.5f");
		row.add(state.averageRotorWakeSwirlVelocityMetersPerSecond(), "%.5f");
		row.add(rotorWakeSwirlOrZero(state, 0), "%.5f");
		row.add(rotorWakeSwirlOrZero(state, 1), "%.5f");
		row.add(rotorWakeSwirlOrZero(state, 2), "%.5f");
		row.add(rotorWakeSwirlOrZero(state, 3), "%.5f");
		row.add(state.averageRotorWindmillingIntensity(), "%.5f");
		row.add(rotorWindmillingOrZero(state, 0), "%.5f");
		row.add(rotorWindmillingOrZero(state, 1), "%.5f");
		row.add(rotorWindmillingOrZero(state, 2), "%.5f");
		row.add(rotorWindmillingOrZero(state, 3), "%.5f");
		row.add(rotorWakeSwirlTorque.x(), "%.6f");
		row.add(rotorWakeSwirlTorque.y(), "%.6f");
		row.add(rotorWakeSwirlTorque.z(), "%.6f");
		row.add(rotorBladeDissymmetryTorque.x(), "%.6f");
		row.add(rotorBladeDissymmetryTorque.y(), "%.6f");
		row.add(rotorBladeDissymmetryTorque.z(), "%.6f");
		row.add(rotorSkewTorque.x(), "%.6f");
		row.add(rotorSkewTorque.y(), "%.6f");
		row.add(rotorSkewTorque.z(), "%.6f");
		row.add(rotorInertiaTorque.x(), "%.6f");
		row.add(rotorInertiaTorque.y(), "%.6f");
		row.add(rotorInertiaTorque.z(), "%.6f");
		row.add(rotorAccelerationReactionTorque.x(), "%.6f");
		row.add(rotorAccelerationReactionTorque.y(), "%.6f");
		row.add(rotorAccelerationReactionTorque.z(), "%.6f");
		row.add(rotorGyroscopicTorque.x(), "%.6f");
		row.add(rotorGyroscopicTorque.y(), "%.6f");
		row.add(rotorGyroscopicTorque.z(), "%.6f");
		row.add(rotorActiveBrakingTorque.x(), "%.6f");
		row.add(rotorActiveBrakingTorque.y(), "%.6f");
		row.add(rotorActiveBrakingTorque.z(), "%.6f");
		row.add(rotorFlappingTorque.x(), "%.6f");
		row.add(rotorFlappingTorque.y(), "%.6f");
		row.add(rotorFlappingTorque.z(), "%.6f");
		row.add(rotorAngularDragTorque.x(), "%.6f");
		row.add(rotorAngularDragTorque.y(), "%.6f");
		row.add(rotorAngularDragTorque.z(), "%.6f");
		row.add(state.averageRotorFlappingForceNewtons(), "%.4f");
		row.add(Math.toDegrees(state.averageRotorFlappingTiltRadians()), "%.4f");
		row.add(rotorFlappingTiltDegreesOrZero(state, 0), "%.4f");
		row.add(rotorFlappingTiltDegreesOrZero(state, 1), "%.4f");
		row.add(rotorFlappingTiltDegreesOrZero(state, 2), "%.4f");
		row.add(rotorFlappingTiltDegreesOrZero(state, 3), "%.4f");
		row.add(state.averageRotorConingIntensity(), "%.5f");
		row.add(rotorConingOrZero(state, 0), "%.5f");
		row.add(rotorConingOrZero(state, 1), "%.5f");
		row.add(rotorConingOrZero(state, 2), "%.5f");
		row.add(rotorConingOrZero(state, 3), "%.5f");
		row.add(Math.toDegrees(state.averageRotorConingAngleRadians()), "%.4f");
		row.add(rotorConingAngleDegreesOrZero(state, 0), "%.4f");
		row.add(rotorConingAngleDegreesOrZero(state, 1), "%.4f");
		row.add(rotorConingAngleDegreesOrZero(state, 2), "%.4f");
		row.add(rotorConingAngleDegreesOrZero(state, 3), "%.4f");
		row.add(state.rotorVibration(), "%.5f");
		row.add(state.averageRotorArmFlexIntensity(), "%.5f");
		row.add(rotorArmFlexOrZero(state, 0), "%.5f");
		row.add(rotorArmFlexOrZero(state, 1), "%.5f");
		row.add(rotorArmFlexOrZero(state, 2), "%.5f");
		row.add(rotorArmFlexOrZero(state, 3), "%.5f");
		row.add(state.averageRotorArmFlexDeflectionMeters() * 1000.0, "%.4f");
		row.add(rotorArmFlexDeflectionMillimetersOrZero(state, 0), "%.4f");
		row.add(rotorArmFlexDeflectionMillimetersOrZero(state, 1), "%.4f");
		row.add(rotorArmFlexDeflectionMillimetersOrZero(state, 2), "%.4f");
		row.add(rotorArmFlexDeflectionMillimetersOrZero(state, 3), "%.4f");
		row.add(Math.toDegrees(state.averageRotorArmFlexTiltRadians()), "%.4f");
		row.add(rotorArmFlexTiltDegreesOrZero(state, 0), "%.4f");
		row.add(rotorArmFlexTiltDegreesOrZero(state, 1), "%.4f");
		row.add(rotorArmFlexTiltDegreesOrZero(state, 2), "%.4f");
		row.add(rotorArmFlexTiltDegreesOrZero(state, 3), "%.4f");
		row.add(state.averageRotorSurfaceScrapeIntensity(), "%.5f");
		row.add(rotorSurfaceScrapeOrZero(state, 0), "%.5f");
		row.add(rotorSurfaceScrapeOrZero(state, 1), "%.5f");
		row.add(rotorSurfaceScrapeOrZero(state, 2), "%.5f");
		row.add(rotorSurfaceScrapeOrZero(state, 3), "%.5f");
		row.add(state.averageRotorStallIntensity(), "%.5f");
		row.add(state.batteryVoltage(), "%.4f");
		row.add(state.batteryOpenCircuitVoltage(), "%.4f");
		row.add(state.batteryOhmicSagVoltage(), "%.4f");
		row.add(state.batteryTransientSagVoltage(), "%.4f");
		row.add(state.batterySlowPolarizationVoltage(), "%.4f");
		row.add(state.batteryEffectiveResistanceOhms(), "%.6f");
		row.add(state.batteryTwentyPercentSagCurrentAmps(), "%.3f");
		row.add(state.batteryTwentyPercentSagCurrentMargin(), "%.5f");
		row.add(state.batteryResistanceAgingScale(), "%.5f");
		row.add(state.batteryCapacityAgingScale(), "%.5f");
		row.add(state.batteryPolarizationResistanceScale(), "%.5f");
		row.add(state.batteryEquivalentCycles(), "%.5f");
		row.add(state.batteryRegenerativeCurrentAmps(), "%.3f");
		row.add(state.batteryVoltageSpike(), "%.4f");
		row.add(state.batteryBusRippleVoltage(), "%.5f");
		row.add(state.batteryStateOfCharge(), "%.5f");
		row.add(state.batteryCurrentAmps(), "%.3f");
		row.add(state.batteryCurrentLimit(), "%.5f");
		row.add(state.batteryPowerLimit(), "%.5f");
		row.add(state.batteryTemperatureCelsius(), "%.3f");
		row.add(state.batteryCoolingFactor(), "%.5f");
		row.add(state.batteryThermalLimit(), "%.5f");
		row.add(frameHealth, "%.4f");
		row.add(rotorHealth, "%.5f");
		row.add(rotorHealthOrOne(state, 0), "%.5f");
		row.add(rotorHealthOrOne(state, 1), "%.5f");
		row.add(rotorHealthOrOne(state, 2), "%.5f");
		row.add(rotorHealthOrOne(state, 3), "%.5f");
		row.add(collisionSeverity, "%.5f");
		row.add(state.contactImpactSpeedMetersPerSecond(), "%.5f");
		row.add(state.contactSlipSpeedMetersPerSecond(), "%.5f");
		row.add(state.contactBounceSpeedMetersPerSecond(), "%.5f");
		row.add(state.contactSurfaceFrictionMultiplier(), "%.4f");
		row.add(state.contactSurfaceRestitutionMultiplier(), "%.4f");
		row.add(state.contactSurfaceScrapeMultiplier(), "%.4f");
		row.add(Math.toDegrees(contactAngularImpulse.x()), "%.4f");
		row.add(Math.toDegrees(contactAngularImpulse.y()), "%.4f");
		row.add(Math.toDegrees(contactAngularImpulse.z()), "%.4f");
		row.add(Math.toDegrees(contactAngularImpulse.length()), "%.4f");
		row.add(propStrikeSeverity > 0.0);
		row.add(propStrikeSeverity > 0.0 ? propStrikeRotorIndex : -1);
		row.add(propStrikeSeverity, "%.5f");
		row.add(propStrikeCount);
		row.add(valueOrZero(propStrikeSeverityByRotor, 0), "%.5f");
		row.add(valueOrZero(propStrikeSeverityByRotor, 1), "%.5f");
		row.add(valueOrZero(propStrikeSeverityByRotor, 2), "%.5f");
		row.add(valueOrZero(propStrikeSeverityByRotor, 3), "%.5f");
		row.add(wind.x(), "%.5f");
		row.add(wind.y(), "%.5f");
		row.add(wind.z(), "%.5f");
		row.add(effectiveWind.x(), "%.5f");
		row.add(effectiveWind.y(), "%.5f");
		row.add(effectiveWind.z(), "%.5f");
		row.add(state.windGustSpeedMetersPerSecond(), "%.5f");
		row.add(state.windShearAccelerationMetersPerSecondSquared(), "%.5f");
		row.add(environment.airDensityRatio(), "%.5f");
		row.add(environment.effectiveAirDensityRatio(), "%.5f");
		row.add(environment.ambientTemperatureCelsius(), "%.3f");
		row.add(groundClearance, "%.5f");
		row.add(environment.groundEffectThrustMultiplier(config), "%.5f");
		row.add(ceilingClearance, "%.5f");
		row.add(environment.ceilingEffectThrustMultiplier(config), "%.5f");
		row.add(environment.rotorThrustAsymmetry(config), "%.5f");
		row.add(environment.rotorThrustMultiplier(0, config), "%.5f");
		row.add(environment.rotorThrustMultiplier(1, config), "%.5f");
		row.add(environment.rotorThrustMultiplier(2, config), "%.5f");
		row.add(environment.rotorThrustMultiplier(3, config), "%.5f");
		row.add(environment.maxRotorFlowObstruction(), "%.5f");
		row.add(environment.rotorFlowObstruction(0), "%.5f");
		row.add(environment.rotorFlowObstruction(1), "%.5f");
		row.add(environment.rotorFlowObstruction(2), "%.5f");
		row.add(environment.rotorFlowObstruction(3), "%.5f");
		row.add(state.propwashIntensity(), "%.4f");
		row.add(state.propwashWakeIntensity(), "%.4f");
		row.add(state.vortexRingStateIntensity(), "%.4f");
		row.add(state.vortexRingThrustBuffetAmplitude(), "%.5f");
		row.add(state.maxVortexRingThrustBuffetAmplitude(), "%.5f");
		row.add(vortexRingBuffetForce.x(), "%.5f");
		row.add(vortexRingBuffetForce.y(), "%.5f");
		row.add(vortexRingBuffetForce.z(), "%.5f");
		row.add(vortexRingBuffetForce.length(), "%.5f");
		row.add(propwashTorque.x(), "%.5f");
		row.add(propwashTorque.y(), "%.5f");
		row.add(propwashTorque.z(), "%.5f");
		row.add(airframeTorque.x(), "%.6f");
		row.add(airframeTorque.y(), "%.6f");
		row.add(airframeTorque.z(), "%.6f");
		row.add(pressureCenterTorque.x(), "%.6f");
		row.add(pressureCenterTorque.y(), "%.6f");
		row.add(pressureCenterTorque.z(), "%.6f");
		row.add(airframeAngularDragTorque.x(), "%.6f");
		row.add(airframeAngularDragTorque.y(), "%.6f");
		row.add(airframeAngularDragTorque.z(), "%.6f");
		row.add(relativeAir.x(), "%.5f");
		row.add(relativeAir.y(), "%.5f");
		row.add(relativeAir.z(), "%.5f");
		row.add(state.airspeedMetersPerSecond(), "%.5f");
		row.add(Math.toDegrees(state.angleOfAttackRadians()), "%.3f");
		row.add(Math.toDegrees(state.sideslipRadians()), "%.3f");
		row.add(state.airframeSeparatedFlowIntensity(), "%.5f");
		row.add(environment.turbulenceIntensity(), "%.5f");
		row.add(environment.obstacleProximity(), "%.5f");
		row.add(environment.droneWakeIntensity(), "%.5f");
		row.add(environment.waterImmersionIntensity(), "%.5f");
		row.add(environment.precipitationWetnessIntensity(), "%.5f");
		row.add(environment.rotorWaterImmersion(0), "%.5f");
		row.add(environment.rotorWaterImmersion(1), "%.5f");
		row.add(environment.rotorWaterImmersion(2), "%.5f");
		row.add(environment.rotorWaterImmersion(3), "%.5f");
		row.add(turbulenceTorque.x(), "%.6f");
		row.add(turbulenceTorque.y(), "%.6f");
		row.add(turbulenceTorque.z(), "%.6f");
		row.add(state.mixerSaturation(), "%.6f");
		Vec3 mixerOutputTorque = state.mixerOutputTorqueBodyNewtonMeters();
		Vec3 mixerAxisAuthority = state.mixerAxisAuthority();
		row.add(mixerOutputTorque.x(), "%.6f");
		row.add(mixerOutputTorque.y(), "%.6f");
		row.add(mixerOutputTorque.z(), "%.6f");
		row.add(mixerAxisAuthority.x(), "%.6f");
		row.add(mixerAxisAuthority.y(), "%.6f");
		row.add(mixerAxisAuthority.z(), "%.6f");
		row.add(state.minMixerAxisAuthority(), "%.6f");
		row.add(state.mixerLowSaturation(), "%.6f");
		row.add(state.mixerHighSaturation(), "%.6f");
		row.add(state.mixerLowHeadroom(), "%.6f");
		row.add(state.mixerHighHeadroom(), "%.6f");
		row.add(state.pidAttenuation(), "%.6f");
		row.add(state.pidIntegralRelax(), "%.6f");
		Vec3 pidIntegralRelaxAxes = state.pidIntegralRelaxAxes();
		row.add(pidIntegralRelaxAxes.x(), "%.6f");
		row.add(pidIntegralRelaxAxes.y(), "%.6f");
		row.add(pidIntegralRelaxAxes.z(), "%.6f");
		row.add(state.pidDTermLowPassCutoffHertz(), "%.5f");
		row.add(state.antiGravityBoost(), "%.6f");
		row.add(Math.toDegrees(levelTarget.x()), "%.3f");
		row.add(Math.toDegrees(levelTarget.z()), "%.3f");
		row.add(Math.toDegrees(levelError.x()), "%.3f");
		row.add(Math.toDegrees(levelError.z()), "%.3f");
		row.add(state.selfLevelBlend(), "%.5f");
		row.add(Math.toDegrees(targetRates.x()), "%.4f");
		row.add(Math.toDegrees(targetRates.y()), "%.4f");
		row.add(Math.toDegrees(targetRates.z()), "%.4f");
		row.add(Math.toDegrees(rateError.x()), "%.4f");
		row.add(Math.toDegrees(rateError.y()), "%.4f");
		row.add(Math.toDegrees(rateError.z()), "%.4f");
		row.add(pidP.x(), "%.6f");
		row.add(pidP.y(), "%.6f");
		row.add(pidP.z(), "%.6f");
		row.add(pidI.x(), "%.6f");
		row.add(pidI.y(), "%.6f");
		row.add(pidI.z(), "%.6f");
		row.add(pidD.x(), "%.6f");
		row.add(pidD.y(), "%.6f");
		row.add(pidD.z(), "%.6f");
		row.add(pidFf.x(), "%.6f");
		row.add(pidFf.y(), "%.6f");
		row.add(pidFf.z(), "%.6f");
		row.add(pidOutput.x(), "%.6f");
		row.add(pidOutput.y(), "%.6f");
		row.add(pidOutput.z(), "%.6f");
		row.add(config.massKg(), "%.5f");
		row.add(config.inertiaKgMetersSquared().x(), "%.5f");
		row.add(config.inertiaKgMetersSquared().y(), "%.5f");
		row.add(config.inertiaKgMetersSquared().z(), "%.4f");
		row.add(config.centerOfMassOffsetBodyMeters().x(), "%.5f");
		row.add(config.centerOfMassOffsetBodyMeters().y(), "%.5f");
		row.add(config.centerOfMassOffsetBodyMeters().z(), "%.5f");
		row.add(config.imuOffsetBodyMeters().x(), "%.5f");
		row.add(config.imuOffsetBodyMeters().y(), "%.5f");
		row.add(config.imuOffsetBodyMeters().z(), "%.5f");
		row.add(config.centerOfPressureOffsetBodyMeters().x(), "%.5f");
		row.add(config.centerOfPressureOffsetBodyMeters().y(), "%.5f");
		row.add(config.centerOfPressureOffsetBodyMeters().z(), "%.5f");
		row.add(config.angularDragCoefficient(), "%.6f");
		row.add(config.motorTimeConstantSeconds(), "%.6f");
		row.add(config.escOutputCurveExponent(), "%.6f");
		row.add(config.escOutputSlewRatePerSecond(), "%.5f");
		row.add(config.voltageCompensationStrength(), "%.3f");
		row.add(config.motorThermalRiseCelsiusPerSecond(), "%.2f");
		row.add(config.motorCoolingRatePerSecond(), "%.3f");
		row.add(config.motorThermalLimitCelsius(), "%.3f");
		row.add(config.motorThermalCutoffCelsius(), "%.4f");
		row.add(config.nominalBatteryVoltage(), "%.1f");
		row.add(config.emptyBatteryVoltage(), "%.1f");
		row.add(config.batteryInternalResistanceOhms(), "%.4f");
		row.add(config.batteryCapacityAmpHours(), "%.3f");
		row.add(config.maxBatteryCurrentAmps(), "%.3f");
		row.add(rotor.maxThrustNewtons(), "%.5f");
		row.add(rotor.thrustCoefficient(), "%.4f");
		row.add(rotor.radiusMeters(), "%.3f");
		row.add(rotor.bladePitchMeters(), "%.4f");
		row.add(rotor.bladePitchToDiameterRatio(), "%.5f");
		row.add(Math.toDegrees(rotor.geometricBladePitchAngleRadians()), "%.3f");
		row.add(rotor.representativeBladeChordMeters(), "%.5f");
		row.add(rotor.representativeBladeChordToRadiusRatio(), "%.5f");
		row.add((double) rotor.bladeCount(), "%.0f");
		row.add(rotor.transverseFlowLiftCoefficient(), "%.4f");
		row.add(rotor.axialFlowThrustLossCoefficient(), "%.8f");
		row.add(rotor.flappingCoefficient(), "%.5f");
		row.add(rotor.stallThrustLossCoefficient(), "%.3f");
		row.add(config.averageRotorImbalanceIntensity(), "%.5f");
		row.add(Math.toDegrees(config.maxPitchRateRadiansPerSecond()), "%.4f");
		row.add(Math.toDegrees(config.maxYawRateRadiansPerSecond()), "%.3f");
		row.add(Math.toDegrees(config.maxRollRateRadiansPerSecond()), "%.4f");
		row.add(config.pitchGains().p(), "%.2f");
		row.add(config.yawGains().p(), "%.2f");
		row.add(config.rollGains().p(), "%.2f");
		row.add(config.pitchGains().i(), "%.6f");
		row.add(config.yawGains().i(), "%.6f");
		row.add(config.rollGains().i(), "%.6f");
		row.add(config.pitchGains().d(), "%.6f");
		row.add(config.yawGains().d(), "%.6f");
		row.add(config.rollGains().d(), "%.6f");
		row.add(config.pitchGains().feedForward(), "%.6f");
		row.add(config.pitchGains().dTermLowPassCutoffHz(), "%.6f");
		row.add(config.pitchGains().antiGravityGain(), "%.6f");
		row.add(config.pitchGains().tpaBreakpoint(), "%.6f");
		row.add(config.pitchGains().tpaStrength(), "%.2f");
		row.add(config.pidIntegralRelaxStrength(), "%.3f");
		row.add(config.rateExpo().x(), "%.3f");
		row.add(config.rateExpo().y(), "%.3f");
		row.add(config.rateExpo().z(), "%.3f");
		row.add(config.rateSuper().x(), "%.3f");
		row.add(config.rateSuper().y(), "%.3f");
		row.add(config.rateSuper().z(), "%.3f");
		row.add(Math.toDegrees(config.selfLevelMaxAngleRadians()), "%.3f");
		row.add(config.selfLevelRateGain(), "%.3f");
		row.add(config.horizonTransitionStartStick(), "%.3f");
		row.add(config.horizonTransitionEndStick(), "%.3f");
		row.add(config.linearDragCoefficient(), "%.4f");
		row.add(rotor.diskDragCoefficient(), "%.4f");
		row.add(rotor.yawTorquePerThrustMeter(), "%.4f");
		row.add(config.averageRotorOutwardCantDegrees(), "%.3f");
		row.add(rotor.rotorInertiaKgMetersSquared(), "%.5f");
		row.add(rotor.inducedInflowTimeConstantSeconds(), "%.5f");
		row.add(rotor.inducedInflowLagCoefficient(), "%.5f");
		row.add(config.propwashStartDescentMetersPerSecond(), "%.3f");
		row.add(config.propwashFullDescentMetersPerSecond(), "%.3f");
		row.add(config.propwashMaxTorqueNewtonMeters(), "%.8f");
		row.add(config.motorIdleThrustFraction(), "%.3f");
		row.add(config.airmodeStrength(), "%.3f");
		row.add(config.gyroLowPassCutoffHz(), "%.5f");
		row.add(config.gyroNoiseStdDevRadiansPerSecond(), "%.4f");
		row.add(config.controlLatencySeconds(), "%.2f");
		row.add(config.attitudeEstimatorAccelerometerCorrectionGain(), "%.3f");
		row.add(config.attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared(), "%.3f");
		row.add(config.rcCommandSmoothingTimeConstantSeconds(), "%.3f");
		row.add(config.rcCommandLatencySeconds(), "%.3f");
		row.add(config.rcFailsafeTimeoutSeconds(), "%.3f");
		row.add(config.rcFrameRateHertz(), "%.3f");
		row.add(config.rcChannelResolutionSteps());
		row.add(config.escCommandFrameRateHertz(), "%.3f");
		row.add(config.escCommandResolutionSteps());
		row.add(config.escOutputFallSlewRatePerSecond(), "%.5f");
		row.add(config.escDeadband(), "%.4f");
		row.add(config.motorActiveBrakingStrength(), "%.3f");
		row.add(config.accelerometerLowPassCutoffHz(), "%.5f");
		row.add(config.accelerometerNoiseStdDevMetersPerSecondSquared(), "%.3f");
		row.add(airframeLift.x(), "%.5f");
		row.add(airframeLift.y(), "%.5f");
		row.add(airframeLift.z(), "%.5f");
		row.add(airframeLift.length(), "%.5f");
		row.add(airframeBodyDrag.x(), "%.5f");
		row.add(airframeBodyDrag.y(), "%.5f");
		row.add(airframeBodyDrag.z(), "%.5f");
		row.add(airframeBodyDrag.length(), "%.5f");
		row.add(linearDampingDrag.x(), "%.5f");
		row.add(linearDampingDrag.y(), "%.5f");
		row.add(linearDampingDrag.z(), "%.5f");
		row.add(linearDampingDrag.length(), "%.5f");
		row.add(state.airframeDragAlongFlowNewtons(), "%.5f");
		row.add(state.airframeDragEquivalentLinearCoefficient(), "%.5f");
		row.add(state.airframeDragEquivalentCdAMetersSquared(), "%.5f");
		row.add(state.airframeDragImavReferenceRatio(), "%.5f");
		row.add(groundEffectDrag.x(), "%.5f");
		row.add(groundEffectDrag.y(), "%.5f");
		row.add(groundEffectDrag.z(), "%.5f");
		row.add(groundEffectDrag.length(), "%.5f");
		row.add(rotorWashDrag.x(), "%.5f");
		row.add(rotorWashDrag.y(), "%.5f");
		row.add(rotorWashDrag.z(), "%.5f");
		row.add(rotorWashDrag.length(), "%.5f");
		row.add(rotorWallEffect.x(), "%.5f");
		row.add(rotorWallEffect.y(), "%.5f");
		row.add(rotorWallEffect.z(), "%.5f");
		row.add(rotorWallEffect.length(), "%.5f");
		row.add(state.barometerAltitudeMeters(), "%.5f");
		row.add(state.barometerVerticalSpeedMetersPerSecond(), "%.5f");
		row.add(state.barometerPressureHectopascals(), "%.5f");
		row.add(state.barometerErrorMeters(), "%.5f");
		row.add(state.barometerPropwashErrorMeters(), "%.5f");
		row.add(state.averageEscTemperatureCelsius(), "%.3f");
		row.add(state.maxEscTemperatureCelsius(), "%.3f");
		row.add(state.escThermalLimit(), "%.5f");
		row.add(state.averageEscCoolingFactor(), "%.5f");
		row.add(escTemperatureOrAmbient(state, 0), "%.3f");
		row.add(escTemperatureOrAmbient(state, 1), "%.3f");
		row.add(escTemperatureOrAmbient(state, 2), "%.3f");
		row.add(escTemperatureOrAmbient(state, 3), "%.3f");
		row.add(escThermalLimitOrOne(state, 0), "%.5f");
		row.add(escThermalLimitOrOne(state, 1), "%.5f");
		row.add(escThermalLimitOrOne(state, 2), "%.5f");
		row.add(escThermalLimitOrOne(state, 3), "%.5f");
		row.add(escCoolingFactorOrOne(state, 0), "%.5f");
		row.add(escCoolingFactorOrOne(state, 1), "%.5f");
		row.add(escCoolingFactorOrOne(state, 2), "%.5f");
		row.add(escCoolingFactorOrOne(state, 3), "%.5f");
		addExtendedRotorColumns(row, state, environment, config, motorPowers, motorTargetOmega, motorTrackingError, motorActuatorAuthority, motorCurrents, motorRegenerativeCurrents, motorPhaseCurrents, motorCurrentRipples, motorCommutationRipples, motorTorqueRipples, motorElectricalEfficiency, motorVoltageHeadroom, motorWindingResistanceScale, motorMechanicalLoss, rotorThrust, rotorForceBody, rotorTorqueBody, propStrikeSeverityByRotor);

		return new DroneBlackboxSample(row.build());
	}

	private static double valueOrZero(double[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : 0.0;
	}

	private static double valueOrOne(double[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : 1.0;
	}

	private static double motorTargetRpm(double omegaRadiansPerSecond) {
		return omegaRadiansPerSecond * 60.0 / (Math.PI * 2.0);
	}

	private static Vec3 vectorOrZero(Vec3[] values, int index) {
		return index >= 0 && index < values.length && values[index] != null ? values[index] : Vec3.ZERO;
	}

	private static void addRotorForceColumns(CsvRow row, Vec3[] rotorForceBody, int index) {
		Vec3 force = vectorOrZero(rotorForceBody, index);
		row.add(force.x(), "%.5f");
		row.add(force.y(), "%.5f");
		row.add(force.z(), "%.5f");
	}

	private static void addRotorTorqueColumns(CsvRow row, Vec3[] rotorTorqueBody, int index) {
		Vec3 torque = vectorOrZero(rotorTorqueBody, index);
		row.add(torque.x(), "%.6f");
		row.add(torque.y(), "%.6f");
		row.add(torque.z(), "%.6f");
	}

	private static void addExtendedRotorColumns(
			CsvRow row,
			DroneState state,
			DroneEnvironment environment,
			DroneConfig config,
			double[] motorPowers,
			double[] motorTargetOmega,
			double[] motorTrackingError,
			double[] motorActuatorAuthority,
			double[] motorCurrents,
			double[] motorRegenerativeCurrents,
			double[] motorPhaseCurrents,
			double[] motorCurrentRipples,
			double[] motorCommutationRipples,
			double[] motorTorqueRipples,
			double[] motorElectricalEfficiency,
			double[] motorVoltageHeadroom,
			double[] motorWindingResistanceScale,
			double[] motorMechanicalLoss,
			double[] rotorThrust,
			Vec3[] rotorForceBody,
			Vec3[] rotorTorqueBody,
			double[] propStrikeSeverityByRotor
	) {
		double[] motorRpm = state.motorRpm();
		double[] motorTelemetryRpm = state.motorRpmTelemetryRpm();
		double[] motorTelemetryValidity = state.motorRpmTelemetryValidity();
		double[] rotorHealth = state.rotorHealth();
		double[] rotorScrape = state.rotorSurfaceScrapeIntensity();
		double[] rotorDynamicInflowTimeConstant = state.rotorDynamicInflowTimeConstantSeconds();
		double[] rotorAdvanceRatio = state.rotorAdvanceRatio();
		double[] rotorPropellerAdvanceRatioJ = state.rotorPropellerAdvanceRatioJ();
		double[] rotorPropellerThrustScale = state.rotorPropellerThrustScale();
		double[] rotorPropellerPowerScale = state.rotorPropellerPowerScale();
		double[] rotorReverseFlowInboardFraction = state.rotorReverseFlowInboardFraction();
		double[] rotorTipMach = state.rotorTipMach();
		double[] rotorCompressibilityThrustScale = state.rotorCompressibilityThrustScale();
		double[] rotorLowReynoldsLoss = state.rotorLowReynoldsLoss();
		double[] rotorBladeAngleOfAttack = state.rotorBladeAngleOfAttackRadians();
		double[] rotorBladeElementStall = state.rotorBladeElementStallIntensity();
		double[] rotorBladeDissymmetry = state.rotorBladeDissymmetryIntensity();
		double[] rotorBladePassRipple = state.rotorBladePassRippleIntensity();
		double[] rotorInPlaneDrag = state.rotorInPlaneDragForceNewtons();
		double[] rotorFlappingTilt = state.rotorFlappingTiltRadians();
		double[] rotorConing = state.rotorConingIntensity();
		double[] rotorConingAngle = state.rotorConingAngleRadians();
		double[] rotorWakeInterference = state.rotorWakeInterferenceIntensity();
		double[] rotorWakeThrustScale = state.rotorWakeThrustScale();
		double[] rotorWetThrustScale = state.rotorWetThrustScale();
		double[] rotorCoaxialLoadBias = state.rotorCoaxialLoadBias();
		double[] rotorWakeSwirl = state.rotorWakeSwirlVelocityMetersPerSecond();
		double[] rotorWindmilling = state.rotorWindmillingIntensity();
		double[] rotorArmFlex = state.rotorArmFlexIntensity();
		double[] rotorArmFlexDeflection = state.rotorArmFlexDeflectionMeters();
		double[] rotorArmFlexTilt = state.rotorArmFlexTiltRadians();

		row.add(config.rotors().size());
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorPowers, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorRpm, i), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(valueOrZero(motorTelemetryRpm, i)), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(motorTelemetryEIntervalMicros(motorTelemetryRpm, motorTelemetryValidity, i), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorTelemetryValidity, i), "%.3f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(motorTargetRpm(valueOrZero(motorTargetOmega, i)), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(DronePhysics.betaflightErpm100FromMechanicalRpm(motorTargetRpm(valueOrZero(motorTargetOmega, i))), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(motorTargetRpm(valueOrZero(motorTargetOmega, i))), "%.1f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorTrackingError, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(motorActuatorAuthority, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorCurrents, i), "%.3f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorRegenerativeCurrents, i), "%.3f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorPhaseCurrents, i), "%.3f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorCurrentRipples, i), "%.3f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorCommutationRipples, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorElectricalEfficiency, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(motorVoltageHeadroom, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(motorWindingResistanceScale, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorMechanicalLoss, i), "%.6f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(motorTorqueRipples, i), "%.6f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorThrust, i), "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorDynamicInflowTimeConstant, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			addRotorForceColumns(row, rotorForceBody, i);
		}
		for (int i = 4; i < 8; i++) {
			addRotorTorqueColumns(row, rotorTorqueBody, i);
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(rotorHealth, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorScrape, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorAdvanceRatio, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorPropellerAdvanceRatioJ, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(rotorPropellerThrustScale, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(rotorPropellerPowerScale, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorReverseFlowInboardFraction, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorTipMach, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(rotorCompressibilityThrustScale, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorLowReynoldsLoss, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(Math.toDegrees(valueOrZero(rotorBladeAngleOfAttack, i)), "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorBladeElementStall, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorBladeDissymmetry, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorBladePassRipple, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorInPlaneDrag, i), "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(Math.toDegrees(valueOrZero(rotorFlappingTilt, i)), "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorConing, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(Math.toDegrees(valueOrZero(rotorConingAngle, i)), "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorWakeInterference, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(rotorWakeThrustScale, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorCoaxialLoadBias, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrOne(rotorWetThrustScale, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorWakeSwirl, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorWindmilling, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorArmFlex, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(rotorArmFlexDeflection, i) * 1000.0, "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(Math.toDegrees(valueOrZero(rotorArmFlexTilt, i)), "%.4f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(valueOrZero(propStrikeSeverityByRotor, i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(i < config.rotors().size() ? environment.rotorThrustMultiplier(i, config) : 1.0, "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(environment.rotorFlowObstruction(i), "%.5f");
		}
		for (int i = 4; i < 8; i++) {
			row.add(environment.rotorWaterImmersion(i), "%.5f");
		}
	}

	private static double rotorHealthOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorHealth(index) : 1.0;
	}

	private static double escDesyncOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.escDesyncIntensity(index) : 0.0;
	}

	private static double escTemperatureOrAmbient(DroneState state, int index) {
		return index < state.motorCount() ? state.escTemperatureCelsius(index) : 25.0;
	}

	private static double escThermalLimitOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.escThermalLimit(index) : 1.0;
	}

	private static double escCoolingFactorOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.escCoolingFactor(index) : 1.0;
	}

	private static double motorTelemetryEIntervalMicros(double[] motorTelemetryRpm, double[] motorTelemetryValidity, int index) {
		if (index < 0 || index >= motorTelemetryRpm.length || index >= motorTelemetryValidity.length) {
			return 0.0;
		}
		return DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
				motorTelemetryRpm[index],
				motorTelemetryValidity[index]
		);
	}

	private static double rotorTranslationalLiftOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorTranslationalLiftIntensity(index) : 0.0;
	}

	private static double rotorDynamicInflowTimeConstantOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorDynamicInflowTimeConstantSeconds(index) : 0.0;
	}

	private static double rotorAerodynamicLoadOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorAerodynamicLoadFactor(index) : 0.0;
	}

	private static double rotorInPlaneDragForceOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorInPlaneDragForceNewtons(index) : 0.0;
	}

	private static double rotorAdvanceRatioOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorAdvanceRatio(index) : 0.0;
	}

	private static double rotorPropellerAdvanceRatioJOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorPropellerAdvanceRatioJ(index) : 0.0;
	}

	private static double rotorPropellerThrustScaleOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorPropellerThrustScale(index) : 1.0;
	}

	private static double rotorPropellerPowerScaleOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorPropellerPowerScale(index) : 1.0;
	}

	private static double rotorReverseFlowFractionOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorReverseFlowInboardFraction(index) : 0.0;
	}

	private static double rotorTipMachOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorTipMach(index) : 0.0;
	}

	private static double rotorCompressibilityThrustScaleOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorCompressibilityThrustScale(index) : 1.0;
	}

	private static double rotorLowReynoldsLossOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorLowReynoldsLoss(index) : 0.0;
	}

	private static double rotorBladeAngleOfAttackDegreesOrZero(DroneState state, int index) {
		return index < state.motorCount() ? Math.toDegrees(state.rotorBladeAngleOfAttackRadians(index)) : 0.0;
	}

	private static double rotorBladeElementStallOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorBladeElementStallIntensity(index) : 0.0;
	}

	private static double rotorBladeDissymmetryOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorBladeDissymmetryIntensity(index) : 0.0;
	}

	private static double rotorBladePassRippleOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorBladePassRippleIntensity(index) : 0.0;
	}

	private static double rotorFlappingTiltDegreesOrZero(DroneState state, int index) {
		return index < state.motorCount() ? Math.toDegrees(state.rotorFlappingTiltRadians(index)) : 0.0;
	}

	private static double rotorConingOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorConingIntensity(index) : 0.0;
	}

	private static double rotorConingAngleDegreesOrZero(DroneState state, int index) {
		return index < state.motorCount() ? Math.toDegrees(state.rotorConingAngleRadians(index)) : 0.0;
	}

	private static double rotorWakeInterferenceOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorWakeInterferenceIntensity(index) : 0.0;
	}

	private static double rotorWakeThrustScaleOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorWakeThrustScale(index) : 1.0;
	}

	private static double rotorCoaxialLoadBiasOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorCoaxialLoadBias(index) : 0.0;
	}

	private static double rotorWetThrustScaleOrOne(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorWetThrustScale(index) : 1.0;
	}

	private static double rotorWakeSwirlOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorWakeSwirlVelocityMetersPerSecond(index) : 0.0;
	}

	private static double rotorWindmillingOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorWindmillingIntensity(index) : 0.0;
	}

	private static double rotorArmFlexOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorArmFlexIntensity(index) : 0.0;
	}

	private static double rotorArmFlexDeflectionMillimetersOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorArmFlexDeflectionMeters(index) * 1000.0 : 0.0;
	}

	private static double rotorArmFlexTiltDegreesOrZero(DroneState state, int index) {
		return index < state.motorCount() ? Math.toDegrees(state.rotorArmFlexTiltRadians(index)) : 0.0;
	}

	private static double rotorSurfaceScrapeOrZero(DroneState state, int index) {
		return index < state.motorCount() ? state.rotorSurfaceScrapeIntensity(index) : 0.0;
	}

	public String toCsvLine() {
		return csvLine;
	}

	private static final class CsvRow {
		private final StringBuilder builder = new StringBuilder();
		private int columns;

		private void add(long value) {
			addRaw(Long.toString(value));
		}

		private void add(int value) {
			addRaw(Integer.toString(value));
		}

		private void add(boolean value) {
			addRaw(Boolean.toString(value));
		}

		private void add(String value) {
			addRaw(value == null ? "" : value);
		}

		private void add(double value, String format) {
			addRaw(String.format(Locale.ROOT, format, value));
		}

		private void addRaw(String value) {
			if (columns > 0) {
				builder.append(',');
			}
			builder.append(value);
			columns++;
		}

		private String build() {
			if (columns != CSV_COLUMN_COUNT) {
				throw new IllegalStateException("Blackbox CSV row has " + columns + " columns, expected " + CSV_COLUMN_COUNT);
			}
			return builder.toString();
		}
	}
}
