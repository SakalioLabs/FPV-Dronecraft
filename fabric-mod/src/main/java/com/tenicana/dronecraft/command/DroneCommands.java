package com.tenicana.dronecraft.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.tenicana.dronecraft.blackbox.DroneBlackboxSummary;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneEnvironmentOverride;
import com.tenicana.dronecraft.sim.PidGains;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class DroneCommands {
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private DroneCommands() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("fpvdrone")
				.then(Commands.literal("spawn")
						.executes(context -> spawn(context.getSource(), "racing_quad", DroneConfig::racingQuad))
						.then(spawnPreset("racing_quad", DroneConfig::racingQuad))
						.then(spawnPreset("cinewhoop", DroneConfig::cinewhoop))
						.then(spawnPreset("heavy_lift", DroneConfig::heavyLift))
						.then(spawnPreset("hex_lift", DroneConfig::hexLift))
						.then(spawnPreset("octo_lift", DroneConfig::octoLift))
						.then(spawnPreset("coaxial_x8", DroneConfig::coaxialX8)))
				.then(Commands.literal("status").executes(context -> flightStatus(context.getSource())))
				.then(Commands.literal("repair").executes(context -> repair(context.getSource())))
				.then(Commands.literal("fault")
						.then(Commands.literal("rotor")
								.then(Commands.argument("index", IntegerArgumentType.integer(0, 7))
										.then(Commands.argument("damage", DoubleArgumentType.doubleArg(0.0, 1.0))
												.executes(context -> rotorFault(
														context.getSource(),
														IntegerArgumentType.getInteger(context, "index"),
														DoubleArgumentType.getDouble(context, "damage"),
														false
												)))))
						.then(Commands.literal("propstrike")
								.then(Commands.argument("index", IntegerArgumentType.integer(0, 7))
										.then(Commands.argument("severity", DoubleArgumentType.doubleArg(0.0, 1.0))
												.executes(context -> rotorFault(
														context.getSource(),
														IntegerArgumentType.getInteger(context, "index"),
														DoubleArgumentType.getDouble(context, "severity"),
														true
												))))))
				.then(Commands.literal("blackbox")
						.then(Commands.literal("status").executes(context -> blackboxStatus(context.getSource())))
						.then(Commands.literal("summary").executes(context -> summary(context.getSource())))
						.then(Commands.literal("clear").executes(context -> clear(context.getSource())))
						.then(Commands.literal("save").executes(context -> save(context.getSource()))))
				.then(Commands.literal("diagnostic")
						.then(Commands.literal("start")
								.executes(context -> diagnosticStart(context.getSource(), DroneControlManager.defaultDiagnosticDurationSeconds()))
								.then(Commands.argument("seconds", IntegerArgumentType.integer(6, 60))
										.executes(context -> diagnosticStart(
												context.getSource(),
												IntegerArgumentType.getInteger(context, "seconds")
										))))
						.then(Commands.literal("record")
								.executes(context -> diagnosticStart(context.getSource(), DroneControlManager.defaultDiagnosticDurationSeconds(), true))
								.then(Commands.argument("seconds", IntegerArgumentType.integer(6, 60))
										.executes(context -> diagnosticStart(
												context.getSource(),
												IntegerArgumentType.getInteger(context, "seconds"),
												true
										))))
						.then(Commands.literal("status").executes(context -> diagnosticStatus(context.getSource())))
						.then(Commands.literal("stop").executes(context -> diagnosticStop(context.getSource()))))
				.then(Commands.literal("preset")
						.then(Commands.literal("list").executes(context -> presetList(context.getSource())))
						.then(preset("racing_quad", DroneConfig::racingQuad))
						.then(preset("cinewhoop", DroneConfig::cinewhoop))
						.then(preset("heavy_lift", DroneConfig::heavyLift))
						.then(preset("hex_lift", DroneConfig::hexLift))
						.then(preset("octo_lift", DroneConfig::octoLift))
						.then(preset("coaxial_x8", DroneConfig::coaxialX8)))
				.then(Commands.literal("environment")
						.then(Commands.literal("status").executes(context -> environmentStatus(context.getSource())))
						.then(Commands.literal("clear").executes(context -> environmentClear(context.getSource())))
						.then(Commands.literal("wind")
								.then(Commands.literal("clear").executes(context -> environmentWindClear(context.getSource())))
								.then(Commands.argument("x", DoubleArgumentType.doubleArg(-40.0, 40.0))
										.then(Commands.argument("y", DoubleArgumentType.doubleArg(-20.0, 20.0))
												.then(Commands.argument("z", DoubleArgumentType.doubleArg(-40.0, 40.0))
														.executes(context -> environmentWind(
																context.getSource(),
																DoubleArgumentType.getDouble(context, "x"),
																DoubleArgumentType.getDouble(context, "y"),
																DoubleArgumentType.getDouble(context, "z")
														))))))
						.then(Commands.literal("turbulence")
								.then(Commands.literal("clear").executes(context -> environmentTurbulenceClear(context.getSource())))
								.then(Commands.argument("intensity", DoubleArgumentType.doubleArg(0.0, 1.5))
										.executes(context -> environmentTurbulence(
												context.getSource(),
												DoubleArgumentType.getDouble(context, "intensity")
										))))
						.then(Commands.literal("density")
								.then(Commands.literal("clear").executes(context -> environmentDensityClear(context.getSource())))
								.then(Commands.argument("ratio", DoubleArgumentType.doubleArg(0.35, 1.35))
										.executes(context -> environmentDensity(
												context.getSource(),
												DoubleArgumentType.getDouble(context, "ratio")
										)))))
				.then(Commands.literal("tune")
						.then(Commands.literal("status").executes(context -> tuneStatus(context.getSource())))
						.then(Commands.literal("reset").executes(context -> tuneReset(context.getSource())))
						.then(Commands.literal("set")
								.then(tuneParameter("pitch_p", (config, value) -> config.withPitchGains(withP(config.pitchGains(), value))))
								.then(tuneParameter("pitch_i", (config, value) -> config.withPitchGains(withI(config.pitchGains(), value))))
								.then(tuneParameter("pitch_d", (config, value) -> config.withPitchGains(withD(config.pitchGains(), value))))
								.then(tuneParameter("pitch_limit", (config, value) -> config.withPitchGains(withLimit(config.pitchGains(), value))))
								.then(tuneParameter("yaw_p", (config, value) -> config.withYawGains(withP(config.yawGains(), value))))
								.then(tuneParameter("yaw_i", (config, value) -> config.withYawGains(withI(config.yawGains(), value))))
								.then(tuneParameter("yaw_d", (config, value) -> config.withYawGains(withD(config.yawGains(), value))))
								.then(tuneParameter("yaw_limit", (config, value) -> config.withYawGains(withLimit(config.yawGains(), value))))
								.then(tuneParameter("roll_p", (config, value) -> config.withRollGains(withP(config.rollGains(), value))))
								.then(tuneParameter("roll_i", (config, value) -> config.withRollGains(withI(config.rollGains(), value))))
								.then(tuneParameter("roll_d", (config, value) -> config.withRollGains(withD(config.rollGains(), value))))
								.then(tuneParameter("roll_limit", (config, value) -> config.withRollGains(withLimit(config.rollGains(), value))))
								.then(tuneParameter("feedforward", 0.0, 0.01, (config, value) -> withAllGains(config, gains -> withFeedForward(gains, value))))
								.then(tuneParameter("dterm_lpf", 0.0, 1000.0, (config, value) -> withAllGains(config, gains -> withDTermLowPass(gains, value))))
								.then(tuneParameter("anti_gravity", 0.0, 8.0, (config, value) -> withAllGains(config, gains -> withAntiGravity(gains, value))))
								.then(tuneParameter("tpa_breakpoint", 0.0, 1.0, (config, value) -> withAllGains(config, gains -> withTpaBreakpoint(gains, value))))
								.then(tuneParameter("tpa_strength", 0.0, 0.95, (config, value) -> withAllGains(config, gains -> withTpaStrength(gains, value))))
								.then(tuneParameter("iterm_relax", 0.0, 1.0, DroneConfig::withPidIntegralRelaxStrength))
								.then(tuneParameter("pitch_rate", (config, value) -> config.withRates(Math.toRadians(value), config.maxYawRateRadiansPerSecond(), config.maxRollRateRadiansPerSecond())))
								.then(tuneParameter("yaw_rate", (config, value) -> config.withRates(config.maxPitchRateRadiansPerSecond(), Math.toRadians(value), config.maxRollRateRadiansPerSecond())))
								.then(tuneParameter("roll_rate", (config, value) -> config.withRates(config.maxPitchRateRadiansPerSecond(), config.maxYawRateRadiansPerSecond(), Math.toRadians(value))))
								.then(tuneParameter("pitch_expo", 0.0, 1.0, (config, value) -> config.withRateExpo(new Vec3(value, config.rateExpo().y(), config.rateExpo().z()))))
								.then(tuneParameter("yaw_expo", 0.0, 1.0, (config, value) -> config.withRateExpo(new Vec3(config.rateExpo().x(), value, config.rateExpo().z()))))
								.then(tuneParameter("roll_expo", 0.0, 1.0, (config, value) -> config.withRateExpo(new Vec3(config.rateExpo().x(), config.rateExpo().y(), value))))
								.then(tuneParameter("pitch_super_rate", 0.0, 0.95, (config, value) -> config.withRateSuper(new Vec3(value, config.rateSuper().y(), config.rateSuper().z()))))
								.then(tuneParameter("yaw_super_rate", 0.0, 0.95, (config, value) -> config.withRateSuper(new Vec3(config.rateSuper().x(), value, config.rateSuper().z()))))
								.then(tuneParameter("roll_super_rate", 0.0, 0.95, (config, value) -> config.withRateSuper(new Vec3(config.rateSuper().x(), config.rateSuper().y(), value))))
								.then(tuneParameter("level_angle", 5.0, 85.0, (config, value) -> config.withSelfLevel(Math.toRadians(value), config.selfLevelRateGain(), config.horizonTransitionStartStick(), config.horizonTransitionEndStick())))
								.then(tuneParameter("level_gain", 0.5, 15.0, (config, value) -> config.withSelfLevel(config.selfLevelMaxAngleRadians(), value, config.horizonTransitionStartStick(), config.horizonTransitionEndStick())))
								.then(tuneParameter("horizon_start", 0.0, 0.95, (config, value) -> config.withSelfLevel(config.selfLevelMaxAngleRadians(), config.selfLevelRateGain(), value, config.horizonTransitionEndStick())))
								.then(tuneParameter("horizon_end", 0.05, 1.0, (config, value) -> config.withSelfLevel(config.selfLevelMaxAngleRadians(), config.selfLevelRateGain(), config.horizonTransitionStartStick(), value)))
								.then(tuneParameter("mass_kg", 0.05, 20.0, DroneConfig::withMassKg))
								.then(tuneParameter("inertia_x", 0.0001, 2.0, (config, value) -> config.withInertiaKgMetersSquared(new Vec3(value, config.inertiaKgMetersSquared().y(), config.inertiaKgMetersSquared().z()))))
								.then(tuneParameter("inertia_y", 0.0001, 2.0, (config, value) -> config.withInertiaKgMetersSquared(new Vec3(config.inertiaKgMetersSquared().x(), value, config.inertiaKgMetersSquared().z()))))
								.then(tuneParameter("inertia_z", 0.0001, 2.0, (config, value) -> config.withInertiaKgMetersSquared(new Vec3(config.inertiaKgMetersSquared().x(), config.inertiaKgMetersSquared().y(), value))))
								.then(tuneParameter("cg_x", -1.0, 1.0, (config, value) -> config.withCenterOfMassOffsetBodyMeters(new Vec3(value, config.centerOfMassOffsetBodyMeters().y(), config.centerOfMassOffsetBodyMeters().z()))))
								.then(tuneParameter("cg_y", -1.0, 1.0, (config, value) -> config.withCenterOfMassOffsetBodyMeters(new Vec3(config.centerOfMassOffsetBodyMeters().x(), value, config.centerOfMassOffsetBodyMeters().z()))))
								.then(tuneParameter("cg_z", -1.0, 1.0, (config, value) -> config.withCenterOfMassOffsetBodyMeters(new Vec3(config.centerOfMassOffsetBodyMeters().x(), config.centerOfMassOffsetBodyMeters().y(), value))))
								.then(tuneParameter("imu_x", -1.0, 1.0, (config, value) -> config.withImuOffsetBodyMeters(new Vec3(value, config.imuOffsetBodyMeters().y(), config.imuOffsetBodyMeters().z()))))
								.then(tuneParameter("imu_y", -1.0, 1.0, (config, value) -> config.withImuOffsetBodyMeters(new Vec3(config.imuOffsetBodyMeters().x(), value, config.imuOffsetBodyMeters().z()))))
								.then(tuneParameter("imu_z", -1.0, 1.0, (config, value) -> config.withImuOffsetBodyMeters(new Vec3(config.imuOffsetBodyMeters().x(), config.imuOffsetBodyMeters().y(), value))))
								.then(tuneParameter("cp_x", -1.0, 1.0, (config, value) -> config.withCenterOfPressureOffsetBodyMeters(new Vec3(value, config.centerOfPressureOffsetBodyMeters().y(), config.centerOfPressureOffsetBodyMeters().z()))))
								.then(tuneParameter("cp_y", -1.0, 1.0, (config, value) -> config.withCenterOfPressureOffsetBodyMeters(new Vec3(config.centerOfPressureOffsetBodyMeters().x(), value, config.centerOfPressureOffsetBodyMeters().z()))))
								.then(tuneParameter("cp_z", -1.0, 1.0, (config, value) -> config.withCenterOfPressureOffsetBodyMeters(new Vec3(config.centerOfPressureOffsetBodyMeters().x(), config.centerOfPressureOffsetBodyMeters().y(), value))))
								.then(tuneParameter("angular_drag", 0.0, 1.0, DroneConfig::withAngularDragCoefficient))
								.then(tuneParameter("motor_tau", 0.005, 0.5, DroneConfig::withMotorTimeConstantSeconds))
								.then(tuneParameter("esc_curve", 0.45, 2.5, (config, value) -> config.withEscMotorResponse(value, config.escOutputSlewRatePerSecond(), config.escOutputFallSlewRatePerSecond(), config.escDeadband(), config.voltageCompensationStrength(), config.motorActiveBrakingStrength())))
								.then(tuneParameter("esc_slew", 0.1, 1000.0, (config, value) -> config.withEscMotorResponse(config.escOutputCurveExponent(), value, config.escOutputFallSlewRatePerSecond(), config.escDeadband(), config.voltageCompensationStrength(), config.motorActiveBrakingStrength())))
								.then(tuneParameter("esc_down_slew", 0.1, 1000.0, (config, value) -> config.withEscMotorResponse(config.escOutputCurveExponent(), config.escOutputSlewRatePerSecond(), value, config.escDeadband(), config.voltageCompensationStrength(), config.motorActiveBrakingStrength())))
								.then(tuneParameter("esc_deadband", 0.0, 0.25, (config, value) -> config.withEscMotorResponse(config.escOutputCurveExponent(), config.escOutputSlewRatePerSecond(), config.escOutputFallSlewRatePerSecond(), value, config.voltageCompensationStrength(), config.motorActiveBrakingStrength())))
								.then(tuneParameter("motor_brake", 0.0, 1.0, (config, value) -> config.withEscMotorResponse(config.escOutputCurveExponent(), config.escOutputSlewRatePerSecond(), config.escOutputFallSlewRatePerSecond(), config.escDeadband(), config.voltageCompensationStrength(), value)))
								.then(tuneParameter("voltage_compensation", 0.0, 1.0, (config, value) -> config.withEscMotorResponse(config.escOutputCurveExponent(), config.escOutputSlewRatePerSecond(), config.escOutputFallSlewRatePerSecond(), config.escDeadband(), value, config.motorActiveBrakingStrength())))
								.then(tuneParameter("esc_frame_rate", 0.0, 8000.0, (config, value) -> config.withEscCommandSignal(value, config.escCommandResolutionSteps())))
								.then(tuneParameter("esc_resolution", 0.0, 65535.0, (config, value) -> config.withEscCommandSignal(config.escCommandFrameRateHertz(), value)))
								.then(tuneParameter("motor_heat_rate", 0.0, 250.0, (config, value) -> config.withMotorThermal(value, config.motorCoolingRatePerSecond(), config.motorThermalLimitCelsius(), config.motorThermalCutoffCelsius())))
								.then(tuneParameter("motor_cooling_rate", 0.0, 5.0, (config, value) -> config.withMotorThermal(config.motorThermalRiseCelsiusPerSecond(), value, config.motorThermalLimitCelsius(), config.motorThermalCutoffCelsius())))
								.then(tuneParameter("motor_temp_limit", 30.0, 220.0, (config, value) -> config.withMotorThermal(config.motorThermalRiseCelsiusPerSecond(), config.motorCoolingRatePerSecond(), value, config.motorThermalCutoffCelsius())))
								.then(tuneParameter("motor_temp_cutoff", 31.0, 260.0, (config, value) -> config.withMotorThermal(config.motorThermalRiseCelsiusPerSecond(), config.motorCoolingRatePerSecond(), config.motorThermalLimitCelsius(), value)))
								.then(tuneParameter("battery_nominal_voltage", 1.0, 60.0, (config, value) -> config.withBattery(value, config.emptyBatteryVoltage(), config.batteryInternalResistanceOhms(), config.batteryCapacityAmpHours(), config.maxBatteryCurrentAmps())))
								.then(tuneParameter("battery_empty_voltage", 0.5, 60.0, (config, value) -> config.withBattery(config.nominalBatteryVoltage(), value, config.batteryInternalResistanceOhms(), config.batteryCapacityAmpHours(), config.maxBatteryCurrentAmps())))
								.then(tuneParameter("battery_resistance", 0.0, 1.0, (config, value) -> config.withBattery(config.nominalBatteryVoltage(), config.emptyBatteryVoltage(), value, config.batteryCapacityAmpHours(), config.maxBatteryCurrentAmps())))
								.then(tuneParameter("battery_capacity_ah", 0.05, 20.0, (config, value) -> config.withBattery(config.nominalBatteryVoltage(), config.emptyBatteryVoltage(), config.batteryInternalResistanceOhms(), value, config.maxBatteryCurrentAmps())))
								.then(tuneParameter("battery_max_current", 1.0, 500.0, (config, value) -> config.withBattery(config.nominalBatteryVoltage(), config.emptyBatteryVoltage(), config.batteryInternalResistanceOhms(), config.batteryCapacityAmpHours(), value)))
								.then(tuneParameter("linear_drag", DroneConfig::withLinearDragCoefficient))
								.then(tuneParameter("body_drag_x", (config, value) -> config.withBodyDragCoefficients(new Vec3(value, config.bodyDragCoefficients().y(), config.bodyDragCoefficients().z()))))
								.then(tuneParameter("body_drag_y", (config, value) -> config.withBodyDragCoefficients(new Vec3(config.bodyDragCoefficients().x(), value, config.bodyDragCoefficients().z()))))
								.then(tuneParameter("body_drag_z", (config, value) -> config.withBodyDragCoefficients(new Vec3(config.bodyDragCoefficients().x(), config.bodyDragCoefficients().y(), value))))
								.then(tuneParameter("rotor_max_thrust", 0.5, 200.0, DroneConfig::withRotorMaxThrustNewtons))
								.then(tuneParameter("rotor_thrust_coefficient", 0.00000001, 0.001, DroneConfig::withRotorThrustCoefficient))
								.then(tuneParameter("rotor_radius", 0.01, 0.3, DroneConfig::withRotorRadiusMeters))
								.then(tuneParameter("rotor_blade_pitch", 0.005, 0.8, DroneConfig::withRotorBladePitchMeters))
								.then(tuneParameter("rotor_blade_count", 1.0, 8.0, (config, value) -> config.withRotorBladeCount((int) Math.round(value))))
								.then(tuneParameter("rotor_transverse_lift", 0.0, 0.25, DroneConfig::withRotorTransverseFlowLiftCoefficient))
								.then(tuneParameter("rotor_axial_loss", 0.0, 0.45, DroneConfig::withRotorAxialFlowThrustLossCoefficient))
								.then(tuneParameter("rotor_disk_drag", 0.0, 0.03, DroneConfig::withRotorDiskDragCoefficient))
								.then(tuneParameter("rotor_flapping", 0.0, 0.2, DroneConfig::withRotorFlappingCoefficient))
								.then(tuneParameter("rotor_stall_loss", 0.0, 0.65, DroneConfig::withRotorStallThrustLossCoefficient))
								.then(tuneParameter("rotor_imbalance", 0.0, 0.35, DroneConfig::withRotorImbalanceIntensity))
								.then(tuneParameter("rotor_yaw_torque", 0.0, 0.08, DroneConfig::withRotorYawTorquePerThrustMeter))
								.then(tuneParameter("rotor_outward_cant", -35.0, 35.0, DroneConfig::withRotorOutwardCantDegrees))
								.then(tuneParameter("rotor_inertia", 0.0, 0.0005, DroneConfig::withRotorInertiaKgMetersSquared))
								.then(tuneParameter("rotor_inflow_tau", 0.0, 0.4, (config, value) -> config.withRotorInducedInflow(value, config.rotors().get(0).inducedInflowLagCoefficient())))
								.then(tuneParameter("rotor_inflow_lag", 0.0, 0.6, (config, value) -> config.withRotorInducedInflow(config.rotors().get(0).inducedInflowTimeConstantSeconds(), value)))
								.then(tuneParameter("ground_effect_height", 0.0, 5.0, (config, value) -> config.withGroundEffect(value, config.groundEffectMaxThrustBoost())))
								.then(tuneParameter("ground_effect_boost", 0.0, 0.6, (config, value) -> config.withGroundEffect(config.groundEffectHeightMeters(), value)))
								.then(tuneParameter("propwash_start", 0.0, 20.0, (config, value) -> config.withPropwash(value, config.propwashFullDescentMetersPerSecond(), config.propwashMaxTorqueNewtonMeters())))
								.then(tuneParameter("propwash_full", 0.1, 30.0, (config, value) -> config.withPropwash(config.propwashStartDescentMetersPerSecond(), value, config.propwashMaxTorqueNewtonMeters())))
								.then(tuneParameter("propwash_torque", 0.0, 0.2, (config, value) -> config.withPropwash(config.propwashStartDescentMetersPerSecond(), config.propwashFullDescentMetersPerSecond(), value)))
								.then(tuneParameter("motor_idle", 0.0, 0.18, (config, value) -> config.withMotorIdleAndAirmode(value, config.airmodeStrength())))
								.then(tuneParameter("airmode_strength", 0.0, 1.0, (config, value) -> config.withMotorIdleAndAirmode(config.motorIdleThrustFraction(), value)))
								.then(tuneParameter("gyro_lpf", 0.0, 1000.0, (config, value) -> config.withFlightControllerSensors(value, config.gyroNoiseStdDevRadiansPerSecond(), config.accelerometerLowPassCutoffHz(), config.accelerometerNoiseStdDevMetersPerSecondSquared(), config.controlLatencySeconds())))
								.then(tuneParameter("gyro_noise", 0.0, 5.0, (config, value) -> config.withFlightControllerSensors(config.gyroLowPassCutoffHz(), value, config.accelerometerLowPassCutoffHz(), config.accelerometerNoiseStdDevMetersPerSecondSquared(), config.controlLatencySeconds())))
								.then(tuneParameter("accel_lpf", 0.0, 1000.0, (config, value) -> config.withFlightControllerSensors(config.gyroLowPassCutoffHz(), config.gyroNoiseStdDevRadiansPerSecond(), value, config.accelerometerNoiseStdDevMetersPerSecondSquared(), config.controlLatencySeconds())))
								.then(tuneParameter("accel_noise", 0.0, 20.0, (config, value) -> config.withFlightControllerSensors(config.gyroLowPassCutoffHz(), config.gyroNoiseStdDevRadiansPerSecond(), config.accelerometerLowPassCutoffHz(), value, config.controlLatencySeconds())))
								.then(tuneParameter("control_latency", 0.0, 0.08, (config, value) -> config.withFlightControllerSensors(config.gyroLowPassCutoffHz(), config.gyroNoiseStdDevRadiansPerSecond(), config.accelerometerLowPassCutoffHz(), config.accelerometerNoiseStdDevMetersPerSecondSquared(), value)))
								.then(tuneParameter("rc_smoothing", 0.0, 0.25, (config, value) -> config.withControlLink(value, config.rcCommandLatencySeconds(), config.rcFailsafeTimeoutSeconds())))
								.then(tuneParameter("rc_latency", 0.0, 0.20, (config, value) -> config.withControlLink(config.rcCommandSmoothingTimeConstantSeconds(), value, config.rcFailsafeTimeoutSeconds())))
								.then(tuneParameter("rc_failsafe", 0.0, 2.0, (config, value) -> config.withControlLink(config.rcCommandSmoothingTimeConstantSeconds(), config.rcCommandLatencySeconds(), value)))
								.then(tuneParameter("rc_frame_rate", 0.0, 1000.0, (config, value) -> config.withControlReceiver(value, config.rcChannelResolutionSteps())))
								.then(tuneParameter("rc_resolution", 0.0, 65535.0, (config, value) -> config.withControlReceiver(config.rcFrameRateHertz(), value)))
								.then(tuneParameter("attitude_accel_gain", 0.0, 10.0, (config, value) -> config.withAttitudeEstimator(value, config.attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared())))
								.then(tuneParameter("attitude_accel_trust", 0.1, 12.0, (config, value) -> config.withAttitudeEstimator(config.attitudeEstimatorAccelerometerCorrectionGain(), value))))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tuneParameter(String name, TuningSetter setter) {
		return tuneParameter(name, 0.0, 5000.0, setter);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tuneParameter(String name, double min, double max, TuningSetter setter) {
		return Commands.literal(name)
				.then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max))
						.executes(context -> setTune(
								context.getSource(),
								name,
								DoubleArgumentType.getDouble(context, "value"),
								setter
						)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> preset(String name, Supplier<DroneConfig> configFactory) {
		return Commands.literal(name)
				.executes(context -> applyPreset(context.getSource(), name, configFactory));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> spawnPreset(String name, Supplier<DroneConfig> configFactory) {
		return Commands.literal(name)
				.executes(context -> spawn(context.getSource(), name, configFactory));
	}

	private static int spawn(CommandSourceStack source, String name, Supplier<DroneConfig> configFactory) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, player.level());
		drone.setOwner(player.getUUID());
		drone.setPos(player.getX(), player.getY() + 1.25, player.getZ());
		drone.setYRot(player.getYRot());
		drone.applyConfig(configFactory.get(), name);
		player.level().addFreshEntity(drone);
		source.sendSuccess(
				() -> Component.literal(String.format(
						java.util.Locale.ROOT,
						"Spawned and bound %s drone at %.2f %.2f %.2f",
						name,
						drone.getX(),
						drone.getY(),
						drone.getZ()
				)),
				false
		);
		return 1;
	}

	private static int repair(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.repairDamage();
		source.sendSuccess(() -> Component.literal("Drone repaired: frame, rotors, and prop-strike telemetry reset."), false);
		return 1;
	}

	private static int rotorFault(CommandSourceStack source, int rotorIndex, double severity, boolean propStrike) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		if (!drone.injectRotorFault(rotorIndex, severity, propStrike)) {
			source.sendFailure(Component.literal("Rotor fault did not apply; use a valid rotor index for this airframe and damage/severity > 0."));
			return 0;
		}

		source.sendSuccess(
				() -> Component.literal(String.format(
						java.util.Locale.ROOT,
						"%s rotor %d severity %.2f -> health %.0f%%",
						propStrike ? "Injected prop strike on" : "Injected damage on",
						rotorIndex,
						severity,
						drone.getRotorHealth(rotorIndex) * 100.0f
				)),
				false
		);
		return 1;
	}

	private static int flightStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby. Use /fpvdrone spawn or right-click a drone with a controller."));
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		DroneControlManager.DiagnosticStatus diagnostic = DroneControlManager.diagnosticStatus(player.getUUID(), drone.tickCount);
		DroneStatusFormatter.Telemetry telemetry = new DroneStatusFormatter.Telemetry(
				drone.isArmed(),
				drone.getFlightMode(),
				drone.isRawControlLinkActive(),
				drone.isProcessedControlLinkActive(),
				drone.isControlFailsafeActive(),
				drone.getControlLinkLossSeconds(),
				drone.getControlFrameAgeSeconds(),
				drone.getControlFrameIntervalSeconds(),
				drone.getControlFrameError(),
				drone.getControlThrottle(),
				drone.getControlPitch(),
				drone.getControlRoll(),
				drone.getControlYaw(),
				drone.getSpeedMetersPerSecond(),
				drone.getContactImpactSpeedMetersPerSecond(),
				drone.getContactSlipSpeedMetersPerSecond(),
				drone.getContactBounceSpeedMetersPerSecond(),
				drone.getContactAngularImpulseDegreesPerSecond(),
				drone.getAirspeedMetersPerSecond(),
				drone.getAngleOfAttackDegrees(),
				drone.getSideslipDegrees(),
				drone.getAirframeLiftForceNewtons(),
				drone.getAirframeSeparatedFlowIntensity(),
				drone.getRotorFlappingTiltDegrees(),
				drone.getGroundEffectDragForceNewtons(),
				drone.getRotorWashDragForceNewtons(),
				drone.getRotorWallEffectForceNewtons(),
				drone.getBarometerAltitudeMeters(),
				drone.getBarometerVerticalSpeedMetersPerSecond(),
				drone.getBarometerPressureHectopascals(),
				drone.getBarometerErrorMeters(),
				drone.getBatteryVoltage(),
				drone.getBatterySagVoltage(),
				drone.getBatteryEffectiveResistanceOhms(),
				drone.getBatteryRegenerativeCurrentAmps(),
				drone.getBatteryVoltageSpike(),
				drone.getBatteryBusRippleVoltage(),
				drone.getBatteryStateOfCharge(),
				drone.getBatteryCurrentAmps(),
				drone.getBatteryPowerLimit(),
				drone.getBatteryCurrentLimit(),
				drone.getGyroClipIntensity(),
				drone.getAccelerometerClipIntensity(),
				drone.getImuSupplyNoiseIntensity(),
				drone.getPidDTermLowPassCutoffHertz(),
				drone.getFrameHealth(),
				drone.getRotorHealth(),
				drone.getMotorTemperatureCelsius(),
				drone.getMotorThermalLimit(),
				drone.getMotorVoltageHeadroom(),
				drone.getEscTemperatureCelsius(),
				drone.getEscThermalLimit(),
				drone.getEscCoolingFactor(),
				drone.getEscDesyncIntensity(),
				drone.getEscCommandFrameAgeSeconds(),
				drone.getEscCommandFrameIntervalSeconds(),
				drone.getEscCommandError(),
				drone.getRotorAerodynamicLoadFactor(),
				drone.getRotorInPlaneDragForceNewtons(),
				drone.getRotorSurfaceScrapeIntensity(),
				drone.getPropwashIntensity(),
				drone.getVortexRingStateIntensity(),
				drone.getRotorTranslationalLiftIntensity(),
				drone.getRotorInducedVelocityMetersPerSecond(),
				drone.getRotorInducedLagThrustScale(),
				drone.getRotorAdvanceRatio(),
				drone.getRotorTipMach(),
				drone.getRotorLowReynoldsLoss(),
				drone.getRotorBladeAngleOfAttackDegrees(),
				drone.getRotorBladeElementStallIntensity(),
				drone.getRotorBladePassRippleIntensity(),
				drone.getRotorBladeDissymmetryTorqueNewtonMeters(),
				drone.getRotorInflowSkewIntensity(),
				drone.getRotorWakeInterferenceIntensity(),
				drone.getRotorWakeThrustScale(),
				drone.getRotorCoaxialLoadBias(),
				drone.getRotorWetThrustScale(),
				drone.getRotorWakeSwirlVelocityMetersPerSecond(),
				drone.getRotorWindmillingIntensity(),
				drone.getRotorWakeSwirlTorqueNewtonMeters(),
				drone.getRotorActiveBrakingTorqueNewtonMeters(),
				drone.getRotorAccelerationReactionTorqueNewtonMeters(),
				drone.getRotorGyroscopicTorqueNewtonMeters(),
				drone.getRotorFlappingTorqueNewtonMeters(),
				drone.getDroneWakeIntensity(),
				drone.getCeilingEffectMultiplier(),
				drone.getEnvironmentThrustAsymmetry(),
				drone.getRotorFlowObstruction(),
				drone.getWaterImmersionIntensity(),
				drone.getPrecipitationWetnessIntensity(),
				drone.getAmbientTemperatureCelsius(),
				drone.getRotorStallIntensity(),
				drone.getRotorVibration(),
				drone.getRotorConingIntensity(),
				drone.getMixerSaturation(),
				drone.getWindSpeedMetersPerSecond(),
				drone.getEffectiveWindSpeedMetersPerSecond(),
				drone.getWindGustSpeedMetersPerSecond(),
				drone.getWindShearAccelerationMetersPerSecondSquared(),
				drone.getTurbulenceIntensity(),
				drone.getObstacleProximity(),
				drone.getGroundEffectMultiplier(),
				drone.blackbox().size(),
				drone.blackbox().capacity(),
				drone.getPropStrikeCount(),
				drone.getLastPropStrikeRotorIndex(),
				drone.getLastPropStrikeSeverity(),
				diagnostic.active(),
				diagnostic.phase(),
				diagnostic.elapsedSeconds(),
				diagnostic.durationSeconds()
		);
		source.sendSuccess(() -> Component.literal(DroneStatusFormatter.format(telemetry)), false);
		return 1;
	}

	private static int blackboxStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		source.sendSuccess(
				() -> Component.literal("Blackbox samples: " + drone.blackbox().size() + "/" + drone.blackbox().capacity()),
				false
		);
		return drone.blackbox().size();
	}

	private static int summary(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		DroneBlackboxSummary summary = DroneBlackboxSummary.from(drone.blackbox());
		if (!summary.hasSamples()) {
			source.sendFailure(Component.literal("No blackbox samples to summarize."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(summary.formatForChat()), false);
		return summary.sampleCount();
	}

	private static int clear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.blackbox().clear();
		source.sendSuccess(() -> Component.literal("Blackbox cleared."), false);
		return 1;
	}

	private static int save(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		if (drone.blackbox().size() == 0) {
			source.sendFailure(Component.literal("No blackbox samples to save."));
			return 0;
		}

		Path directory = source.getServer().getFile("fpvdrone-blackbox");
		String fileName = "drone-" + source.getPlayerOrException().getUUID() + "-" + LocalDateTime.now().format(FILE_TIME) + ".csv";
		Path output = directory.resolve(fileName);

		try {
			Files.createDirectories(directory);
			Files.writeString(output, drone.blackbox().toCsv(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			source.sendFailure(Component.literal("Failed to save blackbox: " + exception.getMessage()));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Blackbox saved: " + output.toAbsolutePath()), false);
		return drone.blackbox().size();
	}

	private static int diagnosticStart(CommandSourceStack source, int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return diagnosticStart(source, seconds, false);
	}

	private static int diagnosticStart(CommandSourceStack source, int seconds, boolean autoSaveBlackbox) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		int durationTicks = DroneControlManager.startDiagnostic(player.getUUID(), drone.tickCount, seconds * 20, autoSaveBlackbox);
		drone.blackbox().clear();
		source.sendSuccess(
				() -> Component.literal(String.format(
						java.util.Locale.ROOT,
						"Diagnostic flight started: %.1fs scripted profile. Blackbox cleared.%s",
						durationTicks / 20.0,
						autoSaveBlackbox ? " It will auto-save when complete." : ""
				)),
				false
		);
		return 1;
	}

	private static int diagnosticStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		DroneControlManager.DiagnosticStatus status = DroneControlManager.diagnosticStatus(player.getUUID(), drone.tickCount);
		if (!status.active()) {
			source.sendSuccess(
					() -> Component.literal("No diagnostic flight active. Blackbox samples: " + drone.blackbox().size() + "/" + drone.blackbox().capacity()),
					false
			);
			return 0;
		}

		source.sendSuccess(
				() -> Component.literal(String.format(
						java.util.Locale.ROOT,
						"Diagnostic active: %s %.1fs/%.1fs, blackbox samples %d/%d",
						status.phase(),
						status.elapsedSeconds(),
						status.durationSeconds(),
						drone.blackbox().size(),
						drone.blackbox().capacity()
				)),
				false
		);
		return 1;
	}

	private static int diagnosticStop(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		if (!DroneControlManager.stopDiagnostic(player.getUUID())) {
			source.sendFailure(Component.literal("No diagnostic flight active."));
			return 0;
		}

		source.sendSuccess(
				() -> Component.literal("Diagnostic flight stopped. Blackbox samples: " + drone.blackbox().size() + "/" + drone.blackbox().capacity()),
				false
		);
		return 1;
	}

	private static int presetList(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("Available drone presets: racing_quad, cinewhoop, heavy_lift, hex_lift, octo_lift, coaxial_x8"), false);
		return 5;
	}

	private static int environmentStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(formatEnvironmentStatus(drone)), false);
		return drone.environmentOverride().active() ? 1 : 0;
	}

	private static int environmentClear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyEnvironmentOverride(DroneEnvironmentOverride.natural());
		source.sendSuccess(() -> Component.literal("Environment override cleared; drone uses natural Minecraft weather, altitude, ground effect, and obstacle airflow."), false);
		return 1;
	}

	private static int environmentWind(CommandSourceStack source, double x, double y, double z) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyEnvironmentOverride(drone.environmentOverride().withWind(new Vec3(x, y, z)));
		source.sendSuccess(() -> Component.literal(formatEnvironmentStatus(drone)), false);
		return 1;
	}

	private static int environmentWindClear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyEnvironmentOverride(drone.environmentOverride().withoutWind());
		source.sendSuccess(() -> Component.literal(formatEnvironmentStatus(drone)), false);
		return 1;
	}

	private static int environmentTurbulence(CommandSourceStack source, double intensity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyEnvironmentOverride(drone.environmentOverride().withTurbulence(intensity));
		source.sendSuccess(() -> Component.literal(formatEnvironmentStatus(drone)), false);
		return 1;
	}

	private static int environmentTurbulenceClear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyEnvironmentOverride(drone.environmentOverride().withoutTurbulence());
		source.sendSuccess(() -> Component.literal(formatEnvironmentStatus(drone)), false);
		return 1;
	}

	private static int environmentDensity(CommandSourceStack source, double ratio) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyEnvironmentOverride(drone.environmentOverride().withAirDensity(ratio));
		source.sendSuccess(() -> Component.literal(formatEnvironmentStatus(drone)), false);
		return 1;
	}

	private static int environmentDensityClear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyEnvironmentOverride(drone.environmentOverride().withoutAirDensity());
		source.sendSuccess(() -> Component.literal(formatEnvironmentStatus(drone)), false);
		return 1;
	}

	private static int applyPreset(CommandSourceStack source, String name, Supplier<DroneConfig> configFactory) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyConfig(configFactory.get(), name);
		source.sendSuccess(() -> Component.literal("Applied drone preset: " + name), false);
		return 1;
	}

	private static int tuneStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		DroneConfig config = drone.config();
		source.sendSuccess(() -> Component.literal(formatTuneStatus(config)), false);
		return 1;
	}

	private static int tuneReset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyConfig(DroneConfig.racingQuad(), "racing_quad");
		source.sendSuccess(() -> Component.literal("Drone tuning reset to racing quad defaults."), false);
		return 1;
	}

	private static int setTune(CommandSourceStack source, String name, double value, TuningSetter setter) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		DroneEntity drone = findOwnedDrone(source);
		if (drone == null) {
			source.sendFailure(Component.literal("No linked drone found nearby."));
			return 0;
		}

		drone.applyConfig(setter.apply(drone.config(), value));
		source.sendSuccess(() -> Component.literal("Set " + name + " = " + value), false);
		return 1;
	}

	private static DroneEntity findOwnedDrone(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		AABB search = player.getBoundingBox().inflate(512.0);
		List<DroneEntity> drones = player.level().getEntities(
				EntityTypeTest.forClass(DroneEntity.class),
				search,
				drone -> drone.isAlive() && drone.isOwnedBy(player.getUUID())
		);

		if (drones.isEmpty()) {
			return null;
		}

		return drones.stream()
				.min(Comparator.comparingDouble(drone -> drone.distanceToSqr(player)))
				.orElse(null);
	}

	private static String formatEnvironmentStatus(DroneEntity drone) {
		DroneEnvironment environment = drone.currentEnvironment();
		Vec3 wind = environment.windVelocityWorldMetersPerSecond();
		return String.format(
				java.util.Locale.ROOT,
				"%s | current wind %.2f %.2f %.2fm/s density %.2f temp %.1fC turbulence %.2f obstacle %.2f water %.2f/%.2f rain %.2f ground %s ceiling %s ceil-mult %.2f",
				drone.environmentOverride().formatForChat(),
				wind.x(),
				wind.y(),
				wind.z(),
				environment.airDensityRatio(),
				environment.ambientTemperatureCelsius(),
				environment.turbulenceIntensity(),
				environment.obstacleProximity(),
				environment.waterImmersionIntensity(),
				environment.maxRotorWaterImmersion(),
				environment.precipitationWetnessIntensity(),
				formatDistanceMeters(environment.groundClearanceMeters()),
				formatDistanceMeters(environment.ceilingClearanceMeters()),
				environment.ceilingEffectThrustMultiplier(drone.config())
		);
	}

	private static String formatDistanceMeters(double distanceMeters) {
		return Double.isFinite(distanceMeters)
				? String.format(java.util.Locale.ROOT, "%.2fm", distanceMeters)
				: "open";
	}

	private static String formatTuneStatus(DroneConfig config) {
		RotorSpec rotor = config.rotors().get(0);
		return String.format(
				java.util.Locale.ROOT,
				"Rates deg/s P/Y/R: %.0f / %.0f / %.0f\nExpo P/Y/R: %.2f / %.2f / %.2f super %.2f / %.2f / %.2f\nSelf-level: angle %.0fdeg gain %.2f horizon %.2f..%.2f\nPitch PID: %.5f %.5f %.5f limit %.2f\nYaw PID: %.5f %.5f %.5f limit %.2f\nRoll PID: %.5f %.5f %.5f limit %.2f\nPID assist: FF P/Y/R %.6f / %.6f / %.6f DLPF P/Y/R %.0f / %.0f / %.0fHz AG %.2f TPA %.2f@%.2f IRelax %.2f\nAirframe: rotors %d mass %.3fkg inertia %.5f %.5f %.5f cg %.3f %.3f %.3fm imu %.3f %.3f %.3fm cp %.3f %.3f %.3fm angular drag %.4f\nMotor/battery: tau %.3fs V %.1f/%.1f R %.3f cap %.2fAh max %.1fA\nESC: curve %.3f up %.1f/s down %.1f/s deadband %.3f brake %.2f voltage comp %.2f frame %.0fHz res %d\nThermal: heat %.1fC/s cool %.3f limit %.0fC cutoff %.0fC\nFC sensors: gyro LPF %.1fHz noise %.4frad/s accel LPF %.1fHz noise %.3fm/s^2 latency %.3fs\nAttitude estimator: accel gain %.2f trust %.2fm/s^2\nRC link: smoothing %.3fs latency %.3fs failsafe %.2fs frame %.0fHz res %d\nDrag: linear %.4f body %.4f %.4f %.4f\nRotor: thrust %.2fN Ct %.8f radius %.4fm pitch %.4fm blades %d lift %.3f axial %.3f disk %.4f flap %.3f stall %.2f imbalance %.3f cant %.2fdeg yaw %.5fm inertia %.8fkg*m^2 inflow %.3fs lag %.2f\nGround effect: height %.2fm boost %.2f\nPropwash: start %.2fm/s full %.2fm/s torque %.3fNm\nMixer: idle %.3f airmode %.2f",
				Math.toDegrees(config.maxPitchRateRadiansPerSecond()),
				Math.toDegrees(config.maxYawRateRadiansPerSecond()),
				Math.toDegrees(config.maxRollRateRadiansPerSecond()),
				config.rateExpo().x(),
				config.rateExpo().y(),
				config.rateExpo().z(),
				config.rateSuper().x(),
				config.rateSuper().y(),
				config.rateSuper().z(),
				Math.toDegrees(config.selfLevelMaxAngleRadians()),
				config.selfLevelRateGain(),
				config.horizonTransitionStartStick(),
				config.horizonTransitionEndStick(),
				config.pitchGains().p(),
				config.pitchGains().i(),
				config.pitchGains().d(),
				config.pitchGains().integratorLimit(),
				config.yawGains().p(),
				config.yawGains().i(),
				config.yawGains().d(),
				config.yawGains().integratorLimit(),
				config.rollGains().p(),
				config.rollGains().i(),
				config.rollGains().d(),
				config.rollGains().integratorLimit(),
				config.pitchGains().feedForward(),
				config.yawGains().feedForward(),
				config.rollGains().feedForward(),
				config.pitchGains().dTermLowPassCutoffHz(),
				config.yawGains().dTermLowPassCutoffHz(),
				config.rollGains().dTermLowPassCutoffHz(),
				config.pitchGains().antiGravityGain(),
				config.pitchGains().tpaStrength(),
				config.pitchGains().tpaBreakpoint(),
				config.pidIntegralRelaxStrength(),
				config.rotors().size(),
				config.massKg(),
				config.inertiaKgMetersSquared().x(),
				config.inertiaKgMetersSquared().y(),
				config.inertiaKgMetersSquared().z(),
				config.centerOfMassOffsetBodyMeters().x(),
				config.centerOfMassOffsetBodyMeters().y(),
				config.centerOfMassOffsetBodyMeters().z(),
				config.imuOffsetBodyMeters().x(),
				config.imuOffsetBodyMeters().y(),
				config.imuOffsetBodyMeters().z(),
				config.centerOfPressureOffsetBodyMeters().x(),
				config.centerOfPressureOffsetBodyMeters().y(),
				config.centerOfPressureOffsetBodyMeters().z(),
				config.angularDragCoefficient(),
				config.motorTimeConstantSeconds(),
				config.nominalBatteryVoltage(),
				config.emptyBatteryVoltage(),
				config.batteryInternalResistanceOhms(),
				config.batteryCapacityAmpHours(),
				config.maxBatteryCurrentAmps(),
				config.escOutputCurveExponent(),
				config.escOutputSlewRatePerSecond(),
				config.escOutputFallSlewRatePerSecond(),
				config.escDeadband(),
				config.motorActiveBrakingStrength(),
				config.voltageCompensationStrength(),
				config.escCommandFrameRateHertz(),
				config.escCommandResolutionSteps(),
				config.motorThermalRiseCelsiusPerSecond(),
				config.motorCoolingRatePerSecond(),
				config.motorThermalLimitCelsius(),
				config.motorThermalCutoffCelsius(),
				config.gyroLowPassCutoffHz(),
				config.gyroNoiseStdDevRadiansPerSecond(),
				config.accelerometerLowPassCutoffHz(),
				config.accelerometerNoiseStdDevMetersPerSecondSquared(),
				config.controlLatencySeconds(),
				config.attitudeEstimatorAccelerometerCorrectionGain(),
				config.attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared(),
				config.rcCommandSmoothingTimeConstantSeconds(),
				config.rcCommandLatencySeconds(),
				config.rcFailsafeTimeoutSeconds(),
				config.rcFrameRateHertz(),
				config.rcChannelResolutionSteps(),
				config.linearDragCoefficient(),
				config.bodyDragCoefficients().x(),
				config.bodyDragCoefficients().y(),
				config.bodyDragCoefficients().z(),
				rotor.maxThrustNewtons(),
				rotor.thrustCoefficient(),
				rotor.radiusMeters(),
				rotor.bladePitchMeters(),
				rotor.bladeCount(),
				rotor.transverseFlowLiftCoefficient(),
				rotor.axialFlowThrustLossCoefficient(),
				rotor.diskDragCoefficient(),
				rotor.flappingCoefficient(),
				rotor.stallThrustLossCoefficient(),
				config.averageRotorImbalanceIntensity(),
				config.averageRotorOutwardCantDegrees(),
				rotor.yawTorquePerThrustMeter(),
				rotor.rotorInertiaKgMetersSquared(),
				rotor.inducedInflowTimeConstantSeconds(),
				rotor.inducedInflowLagCoefficient(),
				config.groundEffectHeightMeters(),
				config.groundEffectMaxThrustBoost(),
				config.propwashStartDescentMetersPerSecond(),
				config.propwashFullDescentMetersPerSecond(),
				config.propwashMaxTorqueNewtonMeters(),
				config.motorIdleThrustFraction(),
				config.airmodeStrength()
		);
	}

	private static PidGains withP(PidGains gains, double value) {
		return new PidGains(value, gains.i(), gains.d(), gains.integratorLimit(), gains.feedForward(), gains.dTermLowPassCutoffHz(), gains.antiGravityGain(), gains.tpaBreakpoint(), gains.tpaStrength());
	}

	private static PidGains withI(PidGains gains, double value) {
		return new PidGains(gains.p(), value, gains.d(), gains.integratorLimit(), gains.feedForward(), gains.dTermLowPassCutoffHz(), gains.antiGravityGain(), gains.tpaBreakpoint(), gains.tpaStrength());
	}

	private static PidGains withD(PidGains gains, double value) {
		return new PidGains(gains.p(), gains.i(), value, gains.integratorLimit(), gains.feedForward(), gains.dTermLowPassCutoffHz(), gains.antiGravityGain(), gains.tpaBreakpoint(), gains.tpaStrength());
	}

	private static PidGains withLimit(PidGains gains, double value) {
		return new PidGains(gains.p(), gains.i(), gains.d(), value, gains.feedForward(), gains.dTermLowPassCutoffHz(), gains.antiGravityGain(), gains.tpaBreakpoint(), gains.tpaStrength());
	}

	private static PidGains withFeedForward(PidGains gains, double value) {
		return new PidGains(gains.p(), gains.i(), gains.d(), gains.integratorLimit(), value, gains.dTermLowPassCutoffHz(), gains.antiGravityGain(), gains.tpaBreakpoint(), gains.tpaStrength());
	}

	private static PidGains withDTermLowPass(PidGains gains, double value) {
		return new PidGains(gains.p(), gains.i(), gains.d(), gains.integratorLimit(), gains.feedForward(), value, gains.antiGravityGain(), gains.tpaBreakpoint(), gains.tpaStrength());
	}

	private static PidGains withAntiGravity(PidGains gains, double value) {
		return new PidGains(gains.p(), gains.i(), gains.d(), gains.integratorLimit(), gains.feedForward(), gains.dTermLowPassCutoffHz(), value, gains.tpaBreakpoint(), gains.tpaStrength());
	}

	private static PidGains withTpaBreakpoint(PidGains gains, double value) {
		return new PidGains(gains.p(), gains.i(), gains.d(), gains.integratorLimit(), gains.feedForward(), gains.dTermLowPassCutoffHz(), gains.antiGravityGain(), value, gains.tpaStrength());
	}

	private static PidGains withTpaStrength(PidGains gains, double value) {
		return new PidGains(gains.p(), gains.i(), gains.d(), gains.integratorLimit(), gains.feedForward(), gains.dTermLowPassCutoffHz(), gains.antiGravityGain(), gains.tpaBreakpoint(), value);
	}

	private static DroneConfig withAllGains(DroneConfig config, GainTransform transform) {
		return config
				.withPitchGains(transform.apply(config.pitchGains()))
				.withYawGains(transform.apply(config.yawGains()))
				.withRollGains(transform.apply(config.rollGains()));
	}

	@FunctionalInterface
	private interface TuningSetter {
		DroneConfig apply(DroneConfig config, double value);
	}

	@FunctionalInterface
	private interface GainTransform {
		PidGains apply(PidGains gains);
	}
}
