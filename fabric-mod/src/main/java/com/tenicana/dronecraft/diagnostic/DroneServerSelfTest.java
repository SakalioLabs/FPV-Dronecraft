package com.tenicana.dronecraft.diagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.blackbox.DroneBlackboxSample;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;

public final class DroneServerSelfTest {
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final UUID SELF_TEST_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000f001");
	private static final String PROPERTY_ENABLED = "fpvdrone.selftest";
	private static final String ENV_ENABLED = "FPVDRONE_SELFTEST";
	private static final String PROPERTY_SECONDS = "fpvdrone.selftest.seconds";
	private static final String ENV_SECONDS = "FPVDRONE_SELFTEST_SECONDS";
	private static final int DEFAULT_SECONDS = 12;
	private static final int POST_SCRIPT_TICKS = 40;

	private static DroneServerSelfTest active;

	private final int requestedSeconds;
	private DroneEntity drone;
	private boolean started;
	private boolean finished;
	private int durationTicks;
	private int finishTick;
	private double initialX;
	private double initialY;
	private double initialZ;
	private double maxAltitudeGain;
	private double maxHorizontalDistance;
	private double maxSpeed;
	private double maxAirspeed;
	private double maxMotorPower;
	private double maxBatteryCurrent;
	private double maxBatterySag;
	private double maxBatteryEffectiveResistance;
	private double maxImuSupplyNoise;
	private double maxPropwash;
	private double maxVortexRingState;
	private double maxRotorAdvanceRatio;
	private double maxRotorStall;
	private double maxRotorVibration;
	private double maxRotorConing;
	private double maxRotorWakeInterference;
	private double maxRotorWakeSwirlVelocity;
	private double maxRotorWindmilling;
	private double maxRotorWakeSwirlTorque;
	private double maxRotorActiveBrakingTorque;
	private double maxRotorAccelerationReactionTorque;
	private double maxRotorGyroscopicTorque;
	private double maxRotorFlappingTorque;
	private double maxMixerSaturation;
	private double finalX;
	private double finalY;
	private double finalZ;
	private double finalSpeed;

	private DroneServerSelfTest(int requestedSeconds) {
		this.requestedSeconds = requestedSeconds;
	}

	public static void initialize() {
		if (!isEnabled()) {
			return;
		}

		active = new DroneServerSelfTest(requestedSeconds());
		ServerTickEvents.END_SERVER_TICK.register(server -> active.tick(server));
		FpvDronecraftMod.LOGGER.info("FPV Dronecraft server self-test armed for {} seconds", active.requestedSeconds);
	}

	private void tick(MinecraftServer server) {
		if (finished) {
			return;
		}

		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return;
		}

		if (!started) {
			start(level);
			return;
		}

		if (drone == null || drone.isRemoved()) {
			finish(server, false, "drone_removed");
			return;
		}

		sample();
		if (drone.tickCount >= finishTick) {
			finish(server, evaluatePassed(), failureReason());
		}
	}

	private void start(ServerLevel level) {
		drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.setOwner(SELF_TEST_OWNER);
		drone.setPos(0.0, 96.0, 0.0);
		level.addFreshEntity(drone);
		initialX = drone.getX();
		initialY = drone.getY();
		initialZ = drone.getZ();
		durationTicks = DroneControlManager.startDiagnostic(SELF_TEST_OWNER, drone.tickCount, requestedSeconds * 20);
		finishTick = durationTicks + POST_SCRIPT_TICKS;
		started = true;
		FpvDronecraftMod.LOGGER.info("FPV Dronecraft server self-test spawned drone at {}, {}, {} for {} ticks", initialX, initialY, initialZ, durationTicks);
	}

	private void sample() {
		maxAltitudeGain = Math.max(maxAltitudeGain, drone.getY() - initialY);
		maxHorizontalDistance = Math.max(maxHorizontalDistance, Math.hypot(drone.getX() - initialX, drone.getZ() - initialZ));
		maxSpeed = Math.max(maxSpeed, drone.getSpeedMetersPerSecond());
		maxAirspeed = Math.max(maxAirspeed, drone.getAirspeedMetersPerSecond());
		maxMotorPower = Math.max(maxMotorPower, drone.getMotorPower());
		maxBatteryCurrent = Math.max(maxBatteryCurrent, drone.getBatteryCurrentAmps());
		maxBatterySag = Math.max(maxBatterySag, drone.getBatterySagVoltage());
		maxBatteryEffectiveResistance = Math.max(maxBatteryEffectiveResistance, drone.getBatteryEffectiveResistanceOhms());
		maxImuSupplyNoise = Math.max(maxImuSupplyNoise, drone.getImuSupplyNoiseIntensity());
		maxPropwash = Math.max(maxPropwash, drone.getPropwashIntensity());
		maxVortexRingState = Math.max(maxVortexRingState, drone.getVortexRingStateIntensity());
		maxRotorAdvanceRatio = Math.max(maxRotorAdvanceRatio, drone.getRotorAdvanceRatio());
		maxRotorStall = Math.max(maxRotorStall, drone.getRotorStallIntensity());
		maxRotorVibration = Math.max(maxRotorVibration, drone.getRotorVibration());
		maxRotorConing = Math.max(maxRotorConing, drone.getRotorConingIntensity());
		maxRotorWakeInterference = Math.max(maxRotorWakeInterference, drone.getRotorWakeInterferenceIntensity());
		maxRotorWakeSwirlVelocity = Math.max(maxRotorWakeSwirlVelocity, drone.getRotorWakeSwirlVelocityMetersPerSecond());
		maxRotorWindmilling = Math.max(maxRotorWindmilling, drone.getRotorWindmillingIntensity());
		maxRotorWakeSwirlTorque = Math.max(maxRotorWakeSwirlTorque, drone.getRotorWakeSwirlTorqueNewtonMeters());
		maxRotorActiveBrakingTorque = Math.max(maxRotorActiveBrakingTorque, drone.getRotorActiveBrakingTorqueNewtonMeters());
		maxRotorAccelerationReactionTorque = Math.max(maxRotorAccelerationReactionTorque, drone.getRotorAccelerationReactionTorqueNewtonMeters());
		maxRotorGyroscopicTorque = Math.max(maxRotorGyroscopicTorque, drone.getRotorGyroscopicTorqueNewtonMeters());
		maxRotorFlappingTorque = Math.max(maxRotorFlappingTorque, drone.getRotorFlappingTorqueNewtonMeters());
		maxMixerSaturation = Math.max(maxMixerSaturation, drone.getMixerSaturation());
	}

	private boolean evaluatePassed() {
		if (drone == null) {
			return false;
		}
		String csv = drone.blackbox().toCsv();
		return drone.blackbox().size() >= durationTicks
				&& maxAltitudeGain > 0.45
				&& maxSpeed > 0.35
				&& maxAirspeed > 0.25
				&& maxMotorPower > 0.08
				&& maxBatteryCurrent > 1.5
				&& maxBatterySag > 0.01
				&& maxBatteryEffectiveResistance > 0.001
				&& DroneBlackboxSample.CSV_HEADER.contains("physics_substeps")
				&& DroneBlackboxSample.CSV_HEADER.contains("physics_dt_s")
				&& DroneBlackboxSample.CSV_HEADER.contains("physics_rate_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("airframe_rotor_count")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_rpm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_thrust_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_force_y_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_torque_y_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_health")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_stall_intensity")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_surface_scrape")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_advance_ratio")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_tip_mach")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_tip_mach")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_low_reynolds_loss")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_low_reynolds_loss")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_aoa_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_element_stall")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_dissymmetry")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_pass_ripple")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_pass_ripple")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_tilt_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_flapping_tilt_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coning")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coning")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_interference")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_wake_swirl_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_windmilling")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_windmilling")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_pitch_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_yaw_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_roll_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_active_braking_pitch_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_active_braking_yaw_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_active_braking_roll_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_acceleration_reaction_pitch_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_acceleration_reaction_yaw_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_acceleration_reaction_roll_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_gyroscopic_pitch_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_gyroscopic_yaw_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_gyroscopic_roll_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_pitch_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_yaw_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_roll_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("imu_supply_noise")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry_pitch_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry_yaw_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry_roll_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_blade_pitch_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_blade_count")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_stall_loss")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_imbalance")
				&& DroneBlackboxSample.CSV_HEADER.contains("esc_command_error")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_frame_rate_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_resolution_steps")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_attenuation")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_accel_rad_s2")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_commutation_ripple")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_commutation_ripple")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_regen_current_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_regen_current_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_phase_current_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_phase_current_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_current_ripple_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_current_ripple_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_torque_ripple_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_torque_ripple_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_mechanical_loss_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_mechanical_loss_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_shaft_power_w")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_electrical_efficiency")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_electrical_efficiency")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_voltage_headroom")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_voltage_headroom")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_winding_resistance_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_winding_resistance_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_target_rpm")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_target_rpm")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_tracking_error")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_tracking_error")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_actuator_authority")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_actuator_authority")
				&& DroneBlackboxSample.CSV_HEADER.contains("mixer_saturation")
				&& DroneBlackboxSample.CSV_HEADER.contains("mixer_yaw_authority")
				&& DroneBlackboxSample.CSV_HEADER.contains("mixer_min_axis_authority")
				&& DroneBlackboxSample.CSV_HEADER.contains("mixer_low_saturation")
				&& DroneBlackboxSample.CSV_HEADER.contains("mixer_high_saturation")
				&& DroneBlackboxSample.CSV_HEADER.contains("mixer_low_headroom")
				&& DroneBlackboxSample.CSV_HEADER.contains("mixer_high_headroom")
				&& DroneBlackboxSample.CSV_HEADER.contains("pid_integral_relax_pitch")
				&& DroneBlackboxSample.CSV_HEADER.contains("pid_integral_relax_yaw")
				&& DroneBlackboxSample.CSV_HEADER.contains("pid_integral_relax_roll")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_cg_x_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_cg_z_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_imu_x_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_imu_z_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_cp_x_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("airframe_pressure_center_pitch_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_outward_cant_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_bias_pitch_dps")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_clip")
				&& DroneBlackboxSample.CSV_HEADER.contains("accel_bias_x_mps2")
				&& DroneBlackboxSample.CSV_HEADER.contains("accel_clip")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_current_limit")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_regen_current_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_effective_resistance_ohm")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_voltage_spike_v")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_bus_ripple_v")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_temp_c")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_cooling_factor")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_thermal_limit")
				&& DroneBlackboxSample.CSV_HEADER.contains("airframe_separation")
				&& DroneBlackboxSample.CSV_HEADER.contains("airframe_lift_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("ground_effect_drag_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wash_drag_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wall_effect_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_impact_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_slip_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_bounce_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_angular_impulse_dps")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_altitude_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_error_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_propwash_error_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("effective_wind_x_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("wind_gust_speed_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("wind_shear_accel_mps2")
				&& DroneBlackboxSample.CSV_HEADER.contains("water_immersion")
				&& DroneBlackboxSample.CSV_HEADER.contains("precipitation_wetness")
				&& DroneBlackboxSample.CSV_HEADER.contains("ambient_temperature_c")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_0_water_immersion")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_water_immersion")
				&& DroneBlackboxSample.CSV_HEADER.contains("max_esc_temp_c")
				&& DroneBlackboxSample.CSV_HEADER.contains("esc_thermal_limit")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_esc_cooling_factor")
				&& DroneBlackboxSample.CSV_HEADER.contains("flight_mode")
				&& !csv.contains("NaN")
				&& !csv.contains("Infinity");
	}

	private String failureReason() {
		if (drone == null) {
			return "drone_missing";
		}
		if (drone.blackbox().size() < durationTicks) {
			return "not_enough_blackbox_samples";
		}
		if (maxAltitudeGain <= 0.45) {
			return "insufficient_climb";
		}
		if (maxSpeed <= 0.35 || maxAirspeed <= 0.25) {
			return "insufficient_motion";
		}
		if (maxMotorPower <= 0.08 || maxBatteryCurrent <= 1.5 || maxBatterySag <= 0.01 || maxBatteryEffectiveResistance <= 0.001) {
			return "powertrain_not_exercised";
		}
		String csv = drone.blackbox().toCsv();
		if (!DroneBlackboxSample.CSV_HEADER.contains("airframe_rotor_count")
				|| !DroneBlackboxSample.CSV_HEADER.contains("physics_substeps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("physics_dt_s")
				|| !DroneBlackboxSample.CSV_HEADER.contains("physics_rate_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_rpm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_thrust_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_force_y_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_torque_y_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_health")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_stall_intensity")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_surface_scrape")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_advance_ratio")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_tip_mach")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_tip_mach")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_low_reynolds_loss")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_low_reynolds_loss")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_aoa_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_element_stall")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_dissymmetry")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_pass_ripple")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_pass_ripple")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_tilt_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_flapping_tilt_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coning")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coning")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_interference")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_wake_swirl_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_windmilling")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_windmilling")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_pitch_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_yaw_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_active_braking_pitch_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_active_braking_yaw_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_active_braking_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_acceleration_reaction_pitch_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_acceleration_reaction_yaw_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_acceleration_reaction_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_gyroscopic_pitch_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_gyroscopic_yaw_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_gyroscopic_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_pitch_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_yaw_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("imu_supply_noise")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry_pitch_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry_yaw_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_blade_pitch_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_blade_count")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_stall_loss")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_imbalance")
				|| !DroneBlackboxSample.CSV_HEADER.contains("esc_command_error")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_frame_rate_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_resolution_steps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_attenuation")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_inertia_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("propwash_wake_intensity")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_motor_cooling_factor")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_motor_accel_rad_s2")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_commutation_ripple")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_commutation_ripple")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_regen_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_regen_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_phase_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_phase_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_current_ripple_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_current_ripple_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_torque_ripple_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_torque_ripple_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_motor_mechanical_loss_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_mechanical_loss_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_motor_shaft_power_w")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_electrical_efficiency")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_electrical_efficiency")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_voltage_headroom")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_voltage_headroom")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_winding_resistance_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_winding_resistance_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_motor_target_rpm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_target_rpm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_motor_tracking_error")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_tracking_error")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_motor_actuator_authority")
				|| !DroneBlackboxSample.CSV_HEADER.contains("motor_5_actuator_authority")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mixer_saturation")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mixer_yaw_authority")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mixer_min_axis_authority")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mixer_low_saturation")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mixer_high_saturation")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mixer_low_headroom")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mixer_high_headroom")
				|| !DroneBlackboxSample.CSV_HEADER.contains("pid_integral_relax_pitch")
				|| !DroneBlackboxSample.CSV_HEADER.contains("pid_integral_relax_yaw")
				|| !DroneBlackboxSample.CSV_HEADER.contains("pid_integral_relax_roll")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_cg_x_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_cg_z_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_imu_x_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_imu_z_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_cp_x_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("airframe_pressure_center_pitch_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_outward_cant_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_bias_pitch_dps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_clip")
				|| !DroneBlackboxSample.CSV_HEADER.contains("accel_bias_x_mps2")
				|| !DroneBlackboxSample.CSV_HEADER.contains("accel_clip")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_current_limit")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_regen_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_effective_resistance_ohm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_voltage_spike_v")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_bus_ripple_v")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_temp_c")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_cooling_factor")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_thermal_limit")
				|| !DroneBlackboxSample.CSV_HEADER.contains("airframe_angular_drag_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("airframe_separation")
				|| !DroneBlackboxSample.CSV_HEADER.contains("airframe_lift_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("ground_effect_drag_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wash_drag_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wall_effect_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_impact_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_slip_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_bounce_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_angular_impulse_dps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_altitude_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_error_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_propwash_error_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("effective_wind_x_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_gust_speed_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_shear_accel_mps2")
				|| !DroneBlackboxSample.CSV_HEADER.contains("water_immersion")
				|| !DroneBlackboxSample.CSV_HEADER.contains("precipitation_wetness")
				|| !DroneBlackboxSample.CSV_HEADER.contains("ambient_temperature_c")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_0_water_immersion")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_water_immersion")
				|| !DroneBlackboxSample.CSV_HEADER.contains("max_esc_temp_c")
				|| !DroneBlackboxSample.CSV_HEADER.contains("esc_thermal_limit")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_esc_cooling_factor")
				|| !DroneBlackboxSample.CSV_HEADER.contains("flight_mode")) {
			return "blackbox_header_missing_required_columns";
		}
		if (csv.contains("NaN") || csv.contains("Infinity")) {
			return "blackbox_contains_nonfinite_values";
		}
		return "passed";
	}

	private void finish(MinecraftServer server, boolean passed, String reason) {
		finished = true;
		DroneControlManager.stopDiagnostic(SELF_TEST_OWNER);

		Path directory = server.getFile("fpvdrone-selftest");
		String timestamp = LocalDateTime.now().format(FILE_TIME);
		Path csvPath = directory.resolve("server-selftest-" + timestamp + ".csv");
		Path reportPath = directory.resolve("server-selftest-" + timestamp + ".json");

		try {
			Files.createDirectories(directory);
			if (drone != null) {
				Files.writeString(csvPath, drone.blackbox().toCsv(), StandardCharsets.UTF_8);
			}
			Files.writeString(reportPath, reportJson(passed, reason, csvPath), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			FpvDronecraftMod.LOGGER.error("Failed to write FPV Dronecraft server self-test artifacts", exception);
		}

		if (drone != null) {
			drone.discard();
		}

		if (passed) {
			FpvDronecraftMod.LOGGER.info("FPV Dronecraft server self-test passed; report {}", reportPath.toAbsolutePath());
		} else {
			FpvDronecraftMod.LOGGER.error("FPV Dronecraft server self-test failed: {}; report {}", reason, reportPath.toAbsolutePath());
		}
		server.halt(false);
	}

	private String reportJson(boolean passed, String reason, Path csvPath) {
		int sampleCount = drone == null ? 0 : drone.blackbox().size();
		if (drone != null) {
			finalX = drone.getX();
			finalY = drone.getY();
			finalZ = drone.getZ();
			finalSpeed = drone.getSpeedMetersPerSecond();
		}
		return String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"passed\": %s,\n"
						+ "  \"reason\": \"%s\",\n"
						+ "  \"csv_column_count\": %d,\n"
						+ "  \"physics_substeps_per_tick\": %d,\n"
						+ "  \"physics_dt_s\": %.5f,\n"
						+ "  \"physics_rate_hz\": %.1f,\n"
						+ "  \"duration_ticks\": %d,\n"
						+ "  \"sample_count\": %d,\n"
						+ "  \"initial_x\": %.5f,\n"
						+ "  \"initial_y\": %.5f,\n"
						+ "  \"initial_z\": %.5f,\n"
						+ "  \"final_x\": %.5f,\n"
						+ "  \"final_y\": %.5f,\n"
						+ "  \"final_z\": %.5f,\n"
						+ "  \"final_speed_mps\": %.5f,\n"
						+ "  \"max_altitude_gain_m\": %.5f,\n"
						+ "  \"max_horizontal_distance_m\": %.5f,\n"
						+ "  \"max_speed_mps\": %.5f,\n"
						+ "  \"max_airspeed_mps\": %.5f,\n"
						+ "  \"max_motor_power\": %.5f,\n"
						+ "  \"max_battery_current_a\": %.5f,\n"
						+ "  \"max_battery_sag_v\": %.5f,\n"
						+ "  \"max_battery_effective_resistance_ohm\": %.6f,\n"
						+ "  \"max_imu_supply_noise\": %.5f,\n"
						+ "  \"max_propwash\": %.5f,\n"
						+ "  \"max_vortex_ring_state\": %.5f,\n"
						+ "  \"max_rotor_advance_ratio\": %.5f,\n"
						+ "  \"max_rotor_stall\": %.5f,\n"
						+ "  \"max_rotor_vibration\": %.5f,\n"
						+ "  \"max_rotor_coning\": %.5f,\n"
						+ "  \"max_rotor_wake_interference\": %.5f,\n"
						+ "  \"max_rotor_wake_swirl_mps\": %.5f,\n"
						+ "  \"max_rotor_windmilling\": %.5f,\n"
						+ "  \"max_rotor_wake_swirl_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_active_braking_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_acceleration_reaction_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_gyroscopic_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_flapping_torque_nm\": %.6f,\n"
						+ "  \"max_mixer_saturation\": %.5f,\n"
						+ "  \"csv\": \"%s\"\n"
						+ "}\n",
				passed,
				escapeJson(reason),
				DroneBlackboxSample.CSV_HEADER.split(",", -1).length,
				DroneEntity.physicsStepsPerTick(),
				DroneEntity.physicsDtSeconds(),
				DroneEntity.physicsRateHertz(),
				durationTicks,
				sampleCount,
				initialX,
				initialY,
				initialZ,
				finalX,
				finalY,
				finalZ,
				finalSpeed,
				maxAltitudeGain,
				maxHorizontalDistance,
				maxSpeed,
				maxAirspeed,
				maxMotorPower,
				maxBatteryCurrent,
				maxBatterySag,
				maxBatteryEffectiveResistance,
				maxImuSupplyNoise,
				maxPropwash,
				maxVortexRingState,
				maxRotorAdvanceRatio,
				maxRotorStall,
				maxRotorVibration,
				maxRotorConing,
				maxRotorWakeInterference,
				maxRotorWakeSwirlVelocity,
				maxRotorWindmilling,
				maxRotorWakeSwirlTorque,
				maxRotorActiveBrakingTorque,
				maxRotorAccelerationReactionTorque,
				maxRotorGyroscopicTorque,
				maxRotorFlappingTorque,
				maxMixerSaturation,
				escapeJson(csvPath.toAbsolutePath().toString())
		);
	}

	private static boolean isEnabled() {
		return Boolean.getBoolean(PROPERTY_ENABLED) || "true".equalsIgnoreCase(System.getenv(ENV_ENABLED));
	}

	private static int requestedSeconds() {
		String property = System.getProperty(PROPERTY_SECONDS);
		if (property != null && !property.isBlank()) {
			return parseSeconds(property);
		}
		String environment = System.getenv(ENV_SECONDS);
		if (environment != null && !environment.isBlank()) {
			return parseSeconds(environment);
		}
		return DEFAULT_SECONDS;
	}

	private static int parseSeconds(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ignored) {
			return DEFAULT_SECONDS;
		}
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
