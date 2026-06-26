package com.tenicana.dronecraft.diagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.blackbox.DroneBlackboxSample;
import com.tenicana.dronecraft.blackbox.DroneBlackboxSummary;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.debug.DroneDebugSettings.FlightModelMode;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneServerSelfTest {
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final UUID SELF_TEST_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000f001");
	private static final String PROPERTY_ENABLED = "fpvdrone.selftest";
	private static final String ENV_ENABLED = "FPVDRONE_SELFTEST";
	private static final String PROPERTY_SECONDS = "fpvdrone.selftest.seconds";
	private static final String ENV_SECONDS = "FPVDRONE_SELFTEST_SECONDS";
	private static final String PROPERTY_FLIGHT_MODEL = "fpvdrone.selftest.flight_model";
	private static final String ENV_FLIGHT_MODEL = "FPVDRONE_SELFTEST_FLIGHT_MODEL";
	private static final String PROPERTY_CONTROL_MODE = "fpvdrone.selftest.control_mode";
	private static final String ENV_CONTROL_MODE = "FPVDRONE_SELFTEST_CONTROL_MODE";
	private static final int DEFAULT_SECONDS = 12;
	private static final int POST_SCRIPT_TICKS = 10;
	private static final int PHYSICS_STEPS_PER_TICK = 10;
	private static final double PHYSICS_DT_SECONDS = 0.005;
	private static final double PHYSICS_RATE_HERTZ = 1.0 / PHYSICS_DT_SECONDS;
	private static final String SELF_TEST_PRESET = "racing_quad";
	private static final double SELF_TEST_SPAWN_CLEARANCE_METERS = 0.14;
	private static final int SELF_TEST_PLATFORM_Y = 95;
	private static final int SELF_TEST_PLATFORM_RADIUS_BLOCKS = 16;
	private static final int PLAYABLE_NEUTRAL_MIN_SAMPLES = 20;
	private static final double PLAYABLE_NEUTRAL_MAX_VISUAL_ATTITUDE_DEGREES = 1.5;
	private static final double PLAYABLE_NEUTRAL_MAX_VISUAL_YAW_RATE_DEGREES_PER_SECOND = 0.35;
	private static final double PLAYABLE_ACRO_MIN_VISUAL_ATTITUDE_DEGREES = 8.0;
	private static final double PLAYABLE_ACRO_MIN_VISUAL_YAW_RATE_DEGREES_PER_SECOND = 8.0;
	private static final double PLAYABLE_MAX_AVERAGE_MOTOR_RPM_TELEMETRY = 11000.0;
	private static final double PLAYABLE_ASSISTED_MAX_FINAL_HORIZONTAL_DISTANCE_METERS = 1.10;
	private static final double PLAYABLE_MAX_FINAL_SPEED_METERS_PER_SECOND = 0.08;
	private static final double PLAYABLE_ACRO_MAX_FINAL_SPEED_METERS_PER_SECOND = 34.0;

	private static DroneServerSelfTest active;

	private final int requestedSeconds;
	private final FlightModelMode flightModelMode;
	private final FlightMode controlFlightMode;
	private DroneEntity drone;
	private boolean started;
	private boolean finished;
	private int durationTicks;
	private int finishTick;
	private int elapsedTicks;
	private int forcedChunkX;
	private int forcedChunkZ;
	private int lastObservedDroneTickCount = -1;
	private boolean chunkForced;
	private double initialX;
	private double initialY;
	private double initialZ;
	private double maxAltitudeGain;
	private double maxHorizontalDistance;
	private double minPlayableLowAltitudeAuthority = 1.0;
	private double maxPlayableVisualPitchDegrees;
	private double maxPlayableVisualRollDegrees;
	private double maxPlayableVisualYawRateDegreesPerSecond;
	private double finalPlayableVisualYawDriftDegrees;
	private int playableNeutralSampleCount;
	private double maxPlayableNeutralVisualPitchDegrees;
	private double maxPlayableNeutralVisualRollDegrees;
	private double maxPlayableNeutralVisualYawRateDegreesPerSecond;
	private double maxSpeed;
	private double maxAirspeed;
	private double maxMotorPower;
	private double maxAverageMotorTelemetryRpm;
	private double maxMotor5TelemetryRpm;
	private double maxAverageMotorTelemetryErpm100;
	private double maxMotor5TelemetryErpm100;
	private double minAverageMotorTelemetryEIntervalMicros = DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS;
	private double minMotor5TelemetryEIntervalMicros = DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS;
	private double maxAverageMotorRpmTelemetryValidity;
	private double maxMotor5RpmTelemetryValidity;
	private double maxBatteryCurrent;
	private double maxBatterySag;
	private double maxBatteryEffectiveResistance;
	private double maxBatteryStateOfChargeResistanceScale = 1.0;
	private double maxBatteryTemperatureResistanceScale = 1.0;
	private double maxBatteryPolarizationResistanceScale = 1.0;
	private double maxImuSupplyNoise;
	private double maxGyroNotchFrequency;
	private double maxGyroNotchAttenuation;
	private double maxGyroNotchSpread;
	private double maxGyroRpmHarmonicNotchAttenuation;
	private double maxGyroBladePassNotchFrequency;
	private double maxGyroBladePassNotchAttenuation;
	private double maxGyroBladePassNotchSpread;
	private double maxMotorWindingResistanceScale = 1.0;
	private double maxPropwash;
	private double maxVortexRingState;
	private double maxVortexRingThrustBuffet;
	private double maxVortexRingBuffetForce;
	private double maxRotorInducedVelocity;
	private double minRotorInducedLagThrustScale = 1.0;
	private double maxRotorDynamicInflowTimeConstant;
	private double maxRotorTranslationalLift;
	private double maxRotorAdvanceRatio;
	private double maxRotorPropellerAdvanceRatioJ;
	private double minRotorPropellerThrustScale = 1.0;
	private double minRotorPropellerPowerScale = 1.0;
	private double maxRotorReverseFlow;
	private double minRotorCompressibilityThrustScale = 1.0;
	private double maxRotorLowReynoldsLoss;
	private double maxRotorBladePassRipple;
	private double maxRotorStall;
	private double maxRotorVibration;
	private double maxRotorDamageVibration;
	private double maxRotorConing;
	private double maxRotorWakeInterference;
	private double maxRotorInPlaneDragForce;
	private double maxRotorCoaxialLoadBias;
	private double maxRotorCoaxialLoadBiasTarget;
	private double maxRotorCoaxialLoadBiasClipping;
	private double maxRotorCoaxialAllocationLoadFraction;
	private double maxRotorCoaxialAllocationCommandRatio = 1.0;
	private double maxRotorCoaxialAllocationMechanicalGainPercent;
	private double maxRotorCoaxialAllocationElectricalGainPercent;
	private double maxRotorCoaxialAllocationUncertaintyPercent;
	private double minRotorWetThrustScale = 1.0;
	private double maxRotorIcingSeverity;
	private double minRotorIcingThrustScale = 1.0;
	private double maxRotorIcingPowerScale = 1.0;
	private double maxRotorWakeSwirlVelocity;
	private double maxRotorWindmilling;
	private double maxRotorWakeSwirlTorque;
	private double maxRotorActiveBrakingTorque;
	private double maxRotorAccelerationReactionTorque;
	private double maxRotorGyroscopicTorque;
	private double maxRotorFlappingTorque;
	private double maxGroundEffectLevelingTorque;
	private double maxAirframeBodyDragForce;
	private double maxLinearDampingDragForce;
	private double maxAirframeDragAlongFlow;
	private double maxAirframeDragEquivalentLinearCoefficient;
	private double maxAirframeDragEquivalentCdA;
	private double maxAirframeDragImavReferenceRatio;
	private double maxMixerSaturation;
	private double finalX;
	private double finalY;
	private double finalZ;
	private double finalSpeed;
	private double finalAltitudeGain;
	private double finalHorizontalDistance;
	private boolean previousBypassPhysicsEnabled;

	private DroneServerSelfTest(int requestedSeconds) {
		this(requestedSeconds, FlightModelMode.SIMULATION);
	}

	private DroneServerSelfTest(int requestedSeconds, FlightModelMode flightModelMode) {
		this(requestedSeconds, flightModelMode, FlightMode.DEFAULT_FIRST_FLIGHT);
	}

	private DroneServerSelfTest(int requestedSeconds, FlightModelMode flightModelMode, FlightMode controlFlightMode) {
		this.requestedSeconds = requestedSeconds;
		this.flightModelMode = flightModelMode == null ? FlightModelMode.SIMULATION : flightModelMode;
		this.controlFlightMode = controlFlightMode == null ? FlightMode.DEFAULT_FIRST_FLIGHT : controlFlightMode;
	}

	public static void initialize() {
		if (!isEnabled()) {
			return;
		}

		active = new DroneServerSelfTest(requestedSeconds(), requestedFlightModelMode(), requestedControlFlightMode());
		ServerTickEvents.END_SERVER_TICK.register(server -> active.tick(server));
		FpvDronecraftMod.LOGGER.info(
				"FPV Dronecraft server self-test armed for {} seconds in {} mode with {} flight mode",
				active.requestedSeconds,
				active.flightModelMode.id(),
				active.controlFlightMode.csvName()
		);
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

		elapsedTicks++;
		ensureDroneTicked();
		sample();
		if (elapsedTicks >= finishTick || drone.tickCount >= finishTick) {
			refreshFinalStateAndBlackboxSummary();
			boolean passed = evaluatePassed();
			finish(server, passed, failureReason());
		}
	}

	private void start(ServerLevel level) {
		previousBypassPhysicsEnabled = DroneDebugSettings.bypassPhysicsEnabled();
		DroneDebugSettings.setFlightModelMode(flightModelMode);
		drone = new DroneEntity(DroneEntityTypes.DRONE, level);
		drone.applyConfig(DroneConfig.racingQuad(), SELF_TEST_PRESET);
		drone.setOwner(SELF_TEST_OWNER);
		buildSelfTestPlatform(level);
		drone.setPos(0.0, selfTestSpawnY(), 0.0);
		forcedChunkX = ((int) Math.floor(drone.getX())) >> 4;
		forcedChunkZ = ((int) Math.floor(drone.getZ())) >> 4;
		level.setChunkForced(forcedChunkX, forcedChunkZ, true);
		chunkForced = true;
		level.addFreshEntity(drone);
		initialX = drone.getX();
		initialY = drone.getY();
		initialZ = drone.getZ();
		durationTicks = DroneControlManager.startDiagnostic(SELF_TEST_OWNER, drone.tickCount, requestedSeconds * 20, false, controlFlightMode);
		finishTick = durationTicks + POST_SCRIPT_TICKS;
		started = true;
		FpvDronecraftMod.LOGGER.info(
				"FPV Dronecraft server self-test spawned {} drone at {}, {}, {} for {} ticks",
				flightModelMode.id(),
				initialX,
				initialY,
				initialZ,
				durationTicks
		);
	}

	private static void buildSelfTestPlatform(ServerLevel level) {
		for (int x = -SELF_TEST_PLATFORM_RADIUS_BLOCKS; x <= SELF_TEST_PLATFORM_RADIUS_BLOCKS; x++) {
			for (int z = -SELF_TEST_PLATFORM_RADIUS_BLOCKS; z <= SELF_TEST_PLATFORM_RADIUS_BLOCKS; z++) {
				level.setBlock(BlockPos.containing(x, SELF_TEST_PLATFORM_Y, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
				level.setBlock(BlockPos.containing(x, SELF_TEST_PLATFORM_Y + 1, z), Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	private static double selfTestSpawnY() {
		return SELF_TEST_PLATFORM_Y + 1.0 + SELF_TEST_SPAWN_CLEARANCE_METERS;
	}

	private void ensureDroneTicked() {
		if (drone == null || drone.isRemoved()) {
			return;
		}
		if (lastObservedDroneTickCount >= 0 && drone.tickCount <= lastObservedDroneTickCount) {
			drone.tickCount = lastObservedDroneTickCount + 1;
			drone.tick();
		}
		lastObservedDroneTickCount = drone.tickCount;
	}

	private void sample() {
		maxAltitudeGain = Math.max(maxAltitudeGain, drone.getY() - initialY);
		maxHorizontalDistance = Math.max(maxHorizontalDistance, Math.hypot(drone.getX() - initialX, drone.getZ() - initialZ));
		maxSpeed = Math.max(maxSpeed, drone.getSpeedMetersPerSecond());
		maxAirspeed = Math.max(maxAirspeed, drone.getAirspeedMetersPerSecond());
		maxMotorPower = Math.max(maxMotorPower, drone.getMotorPower());
		maxAverageMotorTelemetryRpm = Math.max(maxAverageMotorTelemetryRpm, drone.getAverageMotorRpmTelemetryRpm());
		maxMotor5TelemetryRpm = Math.max(maxMotor5TelemetryRpm, drone.getMotorRpmTelemetryRpm(5));
		maxAverageMotorTelemetryErpm100 = Math.max(maxAverageMotorTelemetryErpm100, drone.getAverageMotorTelemetryErpm100());
		maxMotor5TelemetryErpm100 = Math.max(maxMotor5TelemetryErpm100, drone.getMotorTelemetryErpm100(5));
		minAverageMotorTelemetryEIntervalMicros = minValidEIntervalMicros(
				minAverageMotorTelemetryEIntervalMicros,
				drone.getAverageMotorTelemetryEIntervalMicros()
		);
		minMotor5TelemetryEIntervalMicros = minValidEIntervalMicros(
				minMotor5TelemetryEIntervalMicros,
				drone.getMotorTelemetryEIntervalMicros(5)
		);
		maxAverageMotorRpmTelemetryValidity = Math.max(maxAverageMotorRpmTelemetryValidity, drone.getAverageMotorRpmTelemetryValidity());
		maxMotor5RpmTelemetryValidity = Math.max(maxMotor5RpmTelemetryValidity, drone.getMotorRpmTelemetryValidity(5));
		maxBatteryCurrent = Math.max(maxBatteryCurrent, drone.getBatteryCurrentAmps());
		maxBatterySag = Math.max(maxBatterySag, drone.getBatterySagVoltage());
		maxBatteryEffectiveResistance = Math.max(maxBatteryEffectiveResistance, drone.getBatteryEffectiveResistanceOhms());
		maxBatteryStateOfChargeResistanceScale = Math.max(maxBatteryStateOfChargeResistanceScale, drone.getBatteryStateOfChargeResistanceScale());
		maxBatteryTemperatureResistanceScale = Math.max(maxBatteryTemperatureResistanceScale, drone.getBatteryTemperatureResistanceScale());
		maxBatteryPolarizationResistanceScale = Math.max(maxBatteryPolarizationResistanceScale, drone.getBatteryPolarizationResistanceScale());
		maxImuSupplyNoise = Math.max(maxImuSupplyNoise, drone.getImuSupplyNoiseIntensity());
		maxGyroNotchFrequency = Math.max(maxGyroNotchFrequency, drone.getGyroNotchFrequencyHertz());
		maxGyroNotchAttenuation = Math.max(maxGyroNotchAttenuation, drone.getGyroNotchAttenuation());
		maxGyroNotchSpread = Math.max(maxGyroNotchSpread, drone.getGyroNotchSpreadHertz());
		maxGyroRpmHarmonicNotchAttenuation = Math.max(maxGyroRpmHarmonicNotchAttenuation, drone.getGyroRpmHarmonicNotchAttenuation());
		maxGyroBladePassNotchFrequency = Math.max(maxGyroBladePassNotchFrequency, drone.getGyroBladePassNotchFrequencyHertz());
		maxGyroBladePassNotchAttenuation = Math.max(maxGyroBladePassNotchAttenuation, drone.getGyroBladePassNotchAttenuation());
		maxGyroBladePassNotchSpread = Math.max(maxGyroBladePassNotchSpread, drone.getGyroBladePassNotchSpreadHertz());
		maxMotorWindingResistanceScale = Math.max(maxMotorWindingResistanceScale, drone.getMotorWindingResistanceScale());
		maxPropwash = Math.max(maxPropwash, drone.getPropwashIntensity());
		maxVortexRingState = Math.max(maxVortexRingState, drone.getVortexRingStateIntensity());
		maxVortexRingThrustBuffet = Math.max(maxVortexRingThrustBuffet, drone.getVortexRingThrustBuffetAmplitude());
		maxVortexRingBuffetForce = Math.max(maxVortexRingBuffetForce, drone.getVortexRingBuffetForceNewtons());
		maxRotorInducedVelocity = Math.max(maxRotorInducedVelocity, drone.getRotorInducedVelocityMetersPerSecond());
		minRotorInducedLagThrustScale = Math.min(minRotorInducedLagThrustScale, drone.getRotorInducedLagThrustScale());
		maxRotorDynamicInflowTimeConstant = Math.max(maxRotorDynamicInflowTimeConstant, drone.getRotorDynamicInflowTimeConstantSeconds());
		maxRotorTranslationalLift = Math.max(maxRotorTranslationalLift, drone.getRotorTranslationalLiftIntensity());
		maxRotorAdvanceRatio = Math.max(maxRotorAdvanceRatio, drone.getRotorAdvanceRatio());
		maxRotorPropellerAdvanceRatioJ = Math.max(maxRotorPropellerAdvanceRatioJ, drone.getRotorPropellerAdvanceRatioJ());
		minRotorPropellerThrustScale = Math.min(minRotorPropellerThrustScale, drone.getRotorPropellerThrustScale());
		minRotorPropellerPowerScale = Math.min(minRotorPropellerPowerScale, drone.getRotorPropellerPowerScale());
		maxRotorReverseFlow = Math.max(maxRotorReverseFlow, drone.getRotorReverseFlowInboardFraction());
		minRotorCompressibilityThrustScale = Math.min(minRotorCompressibilityThrustScale, drone.getRotorCompressibilityThrustScale());
		maxRotorLowReynoldsLoss = Math.max(maxRotorLowReynoldsLoss, drone.getRotorLowReynoldsLoss());
		maxRotorBladePassRipple = Math.max(maxRotorBladePassRipple, drone.getRotorBladePassRippleIntensity());
		maxRotorStall = Math.max(maxRotorStall, drone.getRotorStallIntensity());
		maxRotorVibration = Math.max(maxRotorVibration, drone.getRotorVibration());
		maxRotorDamageVibration = Math.max(maxRotorDamageVibration, drone.getRotorDamageVibration());
		maxRotorConing = Math.max(maxRotorConing, drone.getRotorConingIntensity());
		maxRotorWakeInterference = Math.max(maxRotorWakeInterference, drone.getRotorWakeInterferenceIntensity());
		maxRotorInPlaneDragForce = Math.max(maxRotorInPlaneDragForce, drone.getRotorInPlaneDragForceNewtons());
		maxRotorCoaxialLoadBias = Math.max(maxRotorCoaxialLoadBias, drone.getRotorCoaxialLoadBias());
		maxRotorCoaxialLoadBiasTarget = Math.max(maxRotorCoaxialLoadBiasTarget, drone.getRotorCoaxialLoadBiasTarget());
		maxRotorCoaxialLoadBiasClipping = Math.max(maxRotorCoaxialLoadBiasClipping, drone.getRotorCoaxialLoadBiasClipping());
		maxRotorCoaxialAllocationLoadFraction = Math.max(maxRotorCoaxialAllocationLoadFraction, drone.getRotorCoaxialAllocationLoadFraction());
		maxRotorCoaxialAllocationCommandRatio = Math.max(maxRotorCoaxialAllocationCommandRatio, drone.getRotorCoaxialAllocationCommandRatio());
		maxRotorCoaxialAllocationMechanicalGainPercent = Math.max(maxRotorCoaxialAllocationMechanicalGainPercent, drone.getRotorCoaxialAllocationMechanicalGainPercent());
		maxRotorCoaxialAllocationElectricalGainPercent = Math.max(maxRotorCoaxialAllocationElectricalGainPercent, drone.getRotorCoaxialAllocationElectricalGainPercent());
		maxRotorCoaxialAllocationUncertaintyPercent = Math.max(maxRotorCoaxialAllocationUncertaintyPercent, drone.getRotorCoaxialAllocationUncertaintyPercent());
		minRotorWetThrustScale = Math.min(minRotorWetThrustScale, drone.getRotorWetThrustScale());
		maxRotorIcingSeverity = Math.max(maxRotorIcingSeverity, drone.getRotorIcingSeverity());
		minRotorIcingThrustScale = Math.min(minRotorIcingThrustScale, drone.getRotorIcingThrustScale());
		maxRotorIcingPowerScale = Math.max(maxRotorIcingPowerScale, drone.getRotorIcingPowerScale());
		maxRotorWakeSwirlVelocity = Math.max(maxRotorWakeSwirlVelocity, drone.getRotorWakeSwirlVelocityMetersPerSecond());
		maxRotorWindmilling = Math.max(maxRotorWindmilling, drone.getRotorWindmillingIntensity());
		maxRotorWakeSwirlTorque = Math.max(maxRotorWakeSwirlTorque, drone.getRotorWakeSwirlTorqueNewtonMeters());
		maxRotorActiveBrakingTorque = Math.max(maxRotorActiveBrakingTorque, drone.getRotorActiveBrakingTorqueNewtonMeters());
		maxRotorAccelerationReactionTorque = Math.max(maxRotorAccelerationReactionTorque, drone.getRotorAccelerationReactionTorqueNewtonMeters());
		maxRotorGyroscopicTorque = Math.max(maxRotorGyroscopicTorque, drone.getRotorGyroscopicTorqueNewtonMeters());
		maxRotorFlappingTorque = Math.max(maxRotorFlappingTorque, drone.getRotorFlappingTorqueNewtonMeters());
		maxGroundEffectLevelingTorque = Math.max(maxGroundEffectLevelingTorque, drone.getGroundEffectLevelingTorqueNewtonMeters());
		maxAirframeBodyDragForce = Math.max(maxAirframeBodyDragForce, drone.getAirframeBodyDragForceNewtons());
		maxLinearDampingDragForce = Math.max(maxLinearDampingDragForce, drone.getLinearDampingDragForceNewtons());
		maxAirframeDragAlongFlow = Math.max(maxAirframeDragAlongFlow, drone.getAirframeDragAlongFlowNewtons());
		maxAirframeDragEquivalentLinearCoefficient = Math.max(maxAirframeDragEquivalentLinearCoefficient, drone.getAirframeDragEquivalentLinearCoefficient());
		maxAirframeDragEquivalentCdA = Math.max(maxAirframeDragEquivalentCdA, drone.getAirframeDragEquivalentCdAMetersSquared());
		maxAirframeDragImavReferenceRatio = Math.max(maxAirframeDragImavReferenceRatio, drone.getAirframeDragImavReferenceRatio());
		maxMixerSaturation = Math.max(maxMixerSaturation, drone.getMixerSaturation());
	}

	private static double minValidEIntervalMicros(double currentMin, double sample) {
		if (sample > 0.0 && sample < DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS) {
			return Math.min(currentMin, sample);
		}
		return currentMin;
	}

	private boolean rpmTelemetryExercised() {
		boolean averageTelemetryExercised = maxAverageMotorRpmTelemetryValidity >= 0.5
				&& maxAverageMotorTelemetryRpm > 100.0
				&& maxAverageMotorTelemetryErpm100 > 5.0
				&& minAverageMotorTelemetryEIntervalMicros > 0.0
				&& minAverageMotorTelemetryEIntervalMicros < DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS;
		if (!averageTelemetryExercised || drone == null || drone.config().rotors().size() <= 5) {
			return averageTelemetryExercised;
		}
		return maxMotor5RpmTelemetryValidity >= 0.5
				&& maxMotor5TelemetryRpm > 100.0
				&& maxMotor5TelemetryErpm100 > 5.0
				&& minMotor5TelemetryEIntervalMicros > 0.0
				&& minMotor5TelemetryEIntervalMicros < DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS;
	}

	private boolean gyroRpmNotchExercised() {
		return maxGyroNotchFrequency > 1.0
				&& maxGyroBladePassNotchFrequency > maxGyroNotchFrequency
				&& (maxGyroNotchAttenuation > 0.0001
						|| maxGyroBladePassNotchAttenuation > 0.0001
						|| maxGyroRpmHarmonicNotchAttenuation > 0.0001)
				&& maxGyroNotchSpread >= 0.0
				&& maxGyroBladePassNotchSpread >= 0.0;
	}

	private boolean coaxialAllocationExercised() {
		if (drone == null || drone.config().rotors().size() <= 5) {
			return true;
		}
		return maxRotorCoaxialLoadBias > 0.005
				&& maxRotorCoaxialLoadBiasTarget + 1.0e-6 >= maxRotorCoaxialLoadBias
				&& maxRotorCoaxialAllocationLoadFraction > 0.01
				&& maxRotorCoaxialAllocationCommandRatio > 1.0
				&& maxRotorCoaxialAllocationMechanicalGainPercent > 0.0
				&& maxRotorCoaxialAllocationElectricalGainPercent > 0.0
				&& maxRotorCoaxialAllocationUncertaintyPercent >= 0.0;
	}

	private boolean evaluatePassed() {
		if (drone == null) {
			return false;
		}
		String csv = drone.blackbox().toCsv();
		return drone.blackbox().size() >= durationTicks
				&& maxAltitudeGain > minimumAltitudeGain()
				&& maxSpeed > minimumSpeed()
				&& motionAirspeed() > minimumAirspeed()
				&& maxMotorPower > 0.08
				&& modelSpecificTelemetryPassed()
				&& blackboxContainsFlightModel(csv)
				&& DroneBlackboxSample.CSV_HEADER.contains("physics_substeps")
				&& DroneBlackboxSample.CSV_HEADER.contains("physics_dt_s")
				&& DroneBlackboxSample.CSV_HEADER.contains("physics_rate_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("flight_model")
				&& DroneBlackboxSample.CSV_HEADER.contains("playable_low_altitude_authority")
				&& DroneBlackboxSample.CSV_HEADER.contains("playable_visual_pitch_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("playable_visual_yaw_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("playable_visual_roll_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("playable_visual_yaw_rate_dps")
				&& DroneBlackboxSample.CSV_HEADER.contains("airframe_rotor_count")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_rpm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_thrust_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_force_y_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_torque_y_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_health")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_stall_intensity")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_damage_vibration")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_damage_vibration")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex_deflection_mm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex_deflection_mm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex_tilt_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex_tilt_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_surface_scrape")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_dynamic_inflow_tau_s")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_dynamic_inflow_tau_s")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_advance_ratio")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_prop_advance_ratio_j")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_prop_advance_ratio_j")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_prop_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_prop_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_prop_power_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_prop_power_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_axial_gust_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_axial_gust_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_reverse_flow_fraction")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_reverse_flow_fraction")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_tip_mach")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_tip_mach")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_compressibility_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_compressibility_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_reynolds_number")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_reynolds_number")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_reynolds_index")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_reynolds_index")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_low_reynolds_loss")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_low_reynolds_loss")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_aoa_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_element_stall")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_dissymmetry")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_pass_ripple")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_pass_ripple")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_in_plane_drag_force_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_in_plane_drag_force_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_tilt_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_flapping_tilt_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coning")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coning")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coning_angle_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coning_angle_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_interference")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_wake_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_load_bias")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_load_bias_target")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_load_bias_clipping")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_load")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_ratio")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_mech_gain_pct")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_elec_gain_pct")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_uncertainty_pct")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coaxial_load_bias")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wet_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_wet_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_icing_severity")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_icing_severity")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_icing_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_icing_thrust_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_icing_power_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_icing_power_scale")
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
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_pitch_to_diameter")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_pitch_angle_70r_deg")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_chord_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_chord_to_radius")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_blade_count")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_motor_pole_pairs")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_stall_loss")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_imbalance")
				&& DroneBlackboxSample.CSV_HEADER.contains("esc_electrical_output")
				&& DroneBlackboxSample.CSV_HEADER.contains("esc_electrical_error")
				&& DroneBlackboxSample.CSV_HEADER.contains("esc_7_electrical_output")
				&& DroneBlackboxSample.CSV_HEADER.contains("esc_command_error")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_frame_rate_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_resolution_steps")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_alias_1024_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_alias_4000_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_attenuation")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_notch_spread_hz")
				&& DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_spread_hz")
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
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_soc_resistance_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_temp_resistance_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_polarization_resistance_scale")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_erpm100")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_erpm100")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_einterval_us")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_einterval_us")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_rpm_telemetry_valid")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_rpm_telemetry_valid")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_target_rpm")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_target_rpm")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_target_erpm100")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_target_erpm100")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_motor_target_einterval_us")
				&& DroneBlackboxSample.CSV_HEADER.contains("motor_5_target_einterval_us")
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
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_20pct_sag_current_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_20pct_sag_current_margin")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_regen_current_a")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_effective_resistance_ohm")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_slow_polarization_v")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_voltage_spike_v")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_bus_ripple_v")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_temp_c")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_cooling_factor")
				&& DroneBlackboxSample.CSV_HEADER.contains("battery_thermal_limit")
				&& DroneBlackboxSample.CSV_HEADER.contains("airframe_separation")
				&& DroneBlackboxSample.CSV_HEADER.contains("airframe_lift_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("ground_effect_drag_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("ground_effect_leveling_torque_nm")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wash_drag_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_wall_effect_n")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_impact_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_slip_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_bounce_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_surface_friction")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_surface_restitution")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_surface_scrape")
				&& DroneBlackboxSample.CSV_HEADER.contains("contact_angular_impulse_dps")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_altitude_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_error_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_sensor_noise_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_pressure_port_error_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("barometer_propwash_error_m")
				&& DroneBlackboxSample.CSV_HEADER.contains("effective_wind_x_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("wind_gust_speed_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("wind_dryden_speed_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("wind_burble_speed_mps")
				&& DroneBlackboxSample.CSV_HEADER.contains("wind_shear_accel_mps2")
				&& DroneBlackboxSample.CSV_HEADER.contains("water_immersion")
				&& DroneBlackboxSample.CSV_HEADER.contains("precipitation_wetness")
				&& DroneBlackboxSample.CSV_HEADER.contains("ambient_temperature_c")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_0_water_immersion")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_water_immersion")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_0_precipitation_wetness")
				&& DroneBlackboxSample.CSV_HEADER.contains("rotor_5_precipitation_wetness")
				&& DroneBlackboxSample.CSV_HEADER.contains("max_esc_temp_c")
				&& DroneBlackboxSample.CSV_HEADER.contains("esc_thermal_limit")
				&& DroneBlackboxSample.CSV_HEADER.contains("avg_esc_cooling_factor")
				&& DroneBlackboxSample.CSV_HEADER.contains("flight_mode")
				&& !csv.contains("NaN")
				&& !csv.contains("Infinity");
	}

	private boolean modelSpecificTelemetryPassed() {
		if (flightModelMode == FlightModelMode.PLAYABLE) {
			return playableTelemetryExercised();
		}
		return maxBatteryCurrent > 1.5
				&& maxBatterySag > 0.01
				&& maxBatteryEffectiveResistance > 0.001
				&& rpmTelemetryExercised()
				&& gyroRpmNotchExercised()
				&& coaxialAllocationExercised();
	}

	private boolean playableTelemetryExercised() {
		return maxHorizontalDistance > 0.05
				&& maxAverageMotorTelemetryRpm > 1000.0
				&& maxAverageMotorTelemetryRpm <= PLAYABLE_MAX_AVERAGE_MOTOR_RPM_TELEMETRY
				&& playableFinalSpeedStable()
				&& playableAssistedFinalDriftStable()
				&& playableModeTelemetryStable();
	}

	private boolean playableFinalSpeedStable() {
		double maxFinalSpeed = controlFlightMode == FlightMode.ACRO
				? PLAYABLE_ACRO_MAX_FINAL_SPEED_METERS_PER_SECOND
				: PLAYABLE_MAX_FINAL_SPEED_METERS_PER_SECOND;
		return finalSpeed <= maxFinalSpeed;
	}

	private boolean playableAssistedFinalDriftStable() {
		return controlFlightMode == FlightMode.ACRO
				|| finalHorizontalDistance <= PLAYABLE_ASSISTED_MAX_FINAL_HORIZONTAL_DISTANCE_METERS;
	}

	private boolean playableModeTelemetryStable() {
		if (controlFlightMode == FlightMode.ACRO) {
			return playableAcroTelemetryStable();
		}
		return playableNeutralTelemetryStable();
	}

	private boolean playableNeutralTelemetryStable() {
		return playableNeutralSampleCount >= PLAYABLE_NEUTRAL_MIN_SAMPLES
				&& maxPlayableNeutralVisualPitchDegrees <= PLAYABLE_NEUTRAL_MAX_VISUAL_ATTITUDE_DEGREES
				&& maxPlayableNeutralVisualRollDegrees <= PLAYABLE_NEUTRAL_MAX_VISUAL_ATTITUDE_DEGREES
				&& maxPlayableNeutralVisualYawRateDegreesPerSecond <= PLAYABLE_NEUTRAL_MAX_VISUAL_YAW_RATE_DEGREES_PER_SECOND;
	}

	private boolean playableAcroTelemetryStable() {
		return playableNeutralSampleCount >= PLAYABLE_NEUTRAL_MIN_SAMPLES
				&& Math.max(maxPlayableVisualPitchDegrees, maxPlayableVisualRollDegrees) >= PLAYABLE_ACRO_MIN_VISUAL_ATTITUDE_DEGREES
				&& maxPlayableVisualYawRateDegreesPerSecond >= PLAYABLE_ACRO_MIN_VISUAL_YAW_RATE_DEGREES_PER_SECOND
				&& maxPlayableNeutralVisualYawRateDegreesPerSecond <= PLAYABLE_NEUTRAL_MAX_VISUAL_YAW_RATE_DEGREES_PER_SECOND;
	}

	private double minimumAltitudeGain() {
		return flightModelMode == FlightModelMode.PLAYABLE ? 0.30 : 0.45;
	}

	private double minimumSpeed() {
		return flightModelMode == FlightModelMode.PLAYABLE ? 0.20 : 0.35;
	}

	private double minimumAirspeed() {
		return flightModelMode == FlightModelMode.PLAYABLE ? 0.10 : 0.25;
	}

	private double motionAirspeed() {
		if (flightModelMode == FlightModelMode.PLAYABLE) {
			return Math.max(maxAirspeed, maxSpeed);
		}
		return maxAirspeed;
	}

	private boolean blackboxContainsFlightModel(String csv) {
		return DroneBlackboxSample.CSV_HEADER.contains("flight_model")
				&& csv != null
				&& csv.contains("," + flightModelMode.id() + ",");
	}

	private void refreshFinalStateAndBlackboxSummary() {
		if (drone == null) {
			return;
		}
		finalX = drone.getX();
		finalY = drone.getY();
		finalZ = drone.getZ();
		finalSpeed = drone.getSpeedMetersPerSecond();
		finalAltitudeGain = finalY - initialY;
		finalHorizontalDistance = Math.hypot(finalX - initialX, finalZ - initialZ);
		if (drone.blackbox().size() <= 0) {
			return;
		}
		DroneBlackboxSummary summary = DroneBlackboxSummary.from(drone.blackbox());
		minPlayableLowAltitudeAuthority = summary.minPlayableLowAltitudeAuthority();
		DroneBlackboxSummary.PlayableVisualStats playableVisualStats = summary.playableVisualStats();
		maxPlayableVisualPitchDegrees = playableVisualStats.maxPitchDegrees();
		maxPlayableVisualRollDegrees = playableVisualStats.maxRollDegrees();
		maxPlayableVisualYawRateDegreesPerSecond = playableVisualStats.maxYawRateDegreesPerSecond();
		finalPlayableVisualYawDriftDegrees = playableVisualStats.finalYawDriftDegrees();
		DroneBlackboxSummary.PlayableNeutralStats playableNeutralStats = summary.playableNeutralStats();
		playableNeutralSampleCount = playableNeutralStats.sampleCount();
		maxPlayableNeutralVisualPitchDegrees = playableNeutralStats.maxPitchDegrees();
		maxPlayableNeutralVisualRollDegrees = playableNeutralStats.maxRollDegrees();
		maxPlayableNeutralVisualYawRateDegreesPerSecond = playableNeutralStats.maxYawRateDegreesPerSecond();
	}

	private String failureReason() {
		if (drone == null) {
			return "drone_missing";
		}
		if (drone.blackbox().size() < durationTicks) {
			return "not_enough_blackbox_samples";
		}
		if (maxAltitudeGain <= minimumAltitudeGain()) {
			return "insufficient_climb";
		}
		if (maxSpeed <= minimumSpeed() || motionAirspeed() <= minimumAirspeed()) {
			return "insufficient_motion";
		}
		String csv = drone.blackbox().toCsv();
		if (maxMotorPower <= 0.08) {
			return "motor_power_not_exercised";
		}
		if (!blackboxContainsFlightModel(csv)) {
			return "flight_model_not_recorded";
		}
		if (flightModelMode == FlightModelMode.PLAYABLE) {
			if (maxHorizontalDistance <= 0.05 || maxAverageMotorTelemetryRpm <= 1000.0) {
				return "playable_layer_not_exercised";
			}
			if (maxAverageMotorTelemetryRpm > PLAYABLE_MAX_AVERAGE_MOTOR_RPM_TELEMETRY) {
				return "playable_rpm_telemetry_too_high";
			}
			if (!playableFinalSpeedStable()) {
				return "playable_final_speed_too_high";
			}
			if (!playableAssistedFinalDriftStable()) {
				return "playable_assisted_final_drift_too_high";
			}
			if (!playableModeTelemetryStable()) {
				return controlFlightMode == FlightMode.ACRO ? "playable_acro_not_stable" : "playable_neutral_not_stable";
			}
		} else {
			if (maxBatteryCurrent <= 1.5 || maxBatterySag <= 0.01 || maxBatteryEffectiveResistance <= 0.001) {
				return "powertrain_not_exercised";
			}
			if (!rpmTelemetryExercised()) {
				return "rpm_telemetry_not_exercised";
			}
			if (!gyroRpmNotchExercised()) {
				return "gyro_rpm_notch_not_exercised";
			}
			if (!coaxialAllocationExercised()) {
				return "coaxial_allocation_not_exercised";
			}
		}
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
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_damage_vibration")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_damage_vibration")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex_deflection_mm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex_deflection_mm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_arm_flex_tilt_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_arm_flex_tilt_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_surface_scrape")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_dynamic_inflow_tau_s")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_dynamic_inflow_tau_s")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_advance_ratio")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_prop_advance_ratio_j")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_prop_advance_ratio_j")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_prop_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_prop_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_prop_power_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_prop_power_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_axial_gust_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_axial_gust_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_tip_mach")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_tip_mach")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_compressibility_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_compressibility_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_reynolds_number")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_reynolds_number")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_reynolds_index")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_reynolds_index")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_low_reynolds_loss")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_low_reynolds_loss")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_aoa_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_element_stall")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_dissymmetry")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_dissymmetry")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_blade_pass_ripple")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_blade_pass_ripple")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_in_plane_drag_force_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_in_plane_drag_force_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_flapping_tilt_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_flapping_tilt_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coning")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coning")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coning_angle_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coning_angle_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_interference")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_wake_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_load_bias")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_load_bias_target")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_load_bias_clipping")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_load")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_ratio")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_mech_gain_pct")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_elec_gain_pct")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_coaxial_allocation_uncertainty_pct")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_coaxial_load_bias")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wet_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_wet_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_icing_severity")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_icing_severity")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_icing_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_icing_thrust_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_icing_power_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_icing_power_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wake_swirl_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_wake_swirl_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_windmilling")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_windmilling")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_reverse_flow_fraction")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_reverse_flow_fraction")
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
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_motor_pole_pairs")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_chord_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_chord_to_radius")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_stall_loss")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_imbalance")
				|| !DroneBlackboxSample.CSV_HEADER.contains("esc_electrical_output")
				|| !DroneBlackboxSample.CSV_HEADER.contains("esc_electrical_error")
				|| !DroneBlackboxSample.CSV_HEADER.contains("esc_7_electrical_output")
				|| !DroneBlackboxSample.CSV_HEADER.contains("esc_command_error")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_frame_rate_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_esc_command_resolution_steps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_alias_1024_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_alias_4000_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_attenuation")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_notch_spread_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_spread_hz")
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
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_pitch_to_diameter")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_pitch_angle_70r_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_chord_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("tune_rotor_chord_to_radius")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_bias_pitch_dps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_clip")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_notch_spread_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("gyro_blade_pass_notch_spread_hz")
				|| !DroneBlackboxSample.CSV_HEADER.contains("accel_bias_x_mps2")
				|| !DroneBlackboxSample.CSV_HEADER.contains("accel_clip")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_current_limit")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mqtb_hq5x4x3_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mqtb_hq5x4x3_power_w")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mqtb_hq5x4x3_current_ratio")
				|| !DroneBlackboxSample.CSV_HEADER.contains("mqtb_hq5x4x3_current_residual_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_20pct_sag_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_20pct_sag_current_margin")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_regen_current_a")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_effective_resistance_ohm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_soc_resistance_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_temp_resistance_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_polarization_resistance_scale")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_slow_polarization_v")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_voltage_spike_v")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_bus_ripple_v")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_temp_c")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_cooling_factor")
				|| !DroneBlackboxSample.CSV_HEADER.contains("battery_thermal_limit")
				|| !DroneBlackboxSample.CSV_HEADER.contains("airframe_angular_drag_roll_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("airframe_separation")
				|| !DroneBlackboxSample.CSV_HEADER.contains("airframe_lift_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("ground_effect_drag_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("ground_effect_leveling_torque_nm")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wash_drag_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_wall_effect_n")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_impact_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_slip_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_bounce_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_surface_friction")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_surface_restitution")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_surface_scrape")
				|| !DroneBlackboxSample.CSV_HEADER.contains("contact_angular_impulse_dps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_altitude_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_error_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_sensor_noise_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_pressure_port_error_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("barometer_propwash_error_m")
				|| !DroneBlackboxSample.CSV_HEADER.contains("effective_wind_x_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_gust_speed_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_dryden_speed_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_burble_speed_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_a4mc_source_gust_x_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_a4mc_source_gust_y_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_a4mc_source_gust_z_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_shear_accel_mps2")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_source")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_source_confidence")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_source_shelter_factor")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_source_gust_x_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_source_gust_y_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_source_gust_z_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("wind_source_humidity")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_disk_wind_gradient_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_disk_wind_gradient_mps")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_a4mc_shelter_obstruction")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_a4mc_shelter_obstruction")
				|| !DroneBlackboxSample.CSV_HEADER.contains("water_immersion")
				|| !DroneBlackboxSample.CSV_HEADER.contains("precipitation_wetness")
				|| !DroneBlackboxSample.CSV_HEADER.contains("ambient_temperature_c")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_0_water_immersion")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_water_immersion")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_0_precipitation_wetness")
				|| !DroneBlackboxSample.CSV_HEADER.contains("rotor_5_precipitation_wetness")
				|| !DroneBlackboxSample.CSV_HEADER.contains("max_esc_temp_c")
				|| !DroneBlackboxSample.CSV_HEADER.contains("esc_thermal_limit")
				|| !DroneBlackboxSample.CSV_HEADER.contains("avg_esc_cooling_factor")
				|| !DroneBlackboxSample.CSV_HEADER.contains("flight_model")
				|| !DroneBlackboxSample.CSV_HEADER.contains("playable_low_altitude_authority")
				|| !DroneBlackboxSample.CSV_HEADER.contains("playable_visual_pitch_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("playable_visual_yaw_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("playable_visual_roll_deg")
				|| !DroneBlackboxSample.CSV_HEADER.contains("playable_visual_yaw_rate_dps")
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
		DroneDebugSettings.setBypassPhysicsEnabled(previousBypassPhysicsEnabled);

		Path directory = server.getFile("fpvdrone-selftest");
		String timestamp = LocalDateTime.now().format(FILE_TIME);
		String filePrefix = "server-selftest-" + flightModelMode.id() + "-" + timestamp;
		Path csvPath = directory.resolve(filePrefix + ".csv");
		Path reportPath = directory.resolve(filePrefix + ".json");

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
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (chunkForced && level != null) {
			level.setChunkForced(forcedChunkX, forcedChunkZ, false);
			chunkForced = false;
		}

		if (passed) {
			FpvDronecraftMod.LOGGER.info(
					"FPV Dronecraft {} server self-test passed; report {}",
					flightModelMode.id(),
					reportPath.toAbsolutePath()
			);
		} else {
			FpvDronecraftMod.LOGGER.error(
					"FPV Dronecraft {} server self-test failed: {}; report {}",
					flightModelMode.id(),
					reason,
					reportPath.toAbsolutePath()
			);
		}
		server.halt(false);
	}

	private String reportJson(boolean passed, String reason, Path csvPath) {
		int sampleCount = drone == null ? 0 : drone.blackbox().size();
		refreshFinalStateAndBlackboxSummary();
		double maxPlayableLowAltitudeSuppressionPercent = Math.max(0.0, (1.0 - minPlayableLowAltitudeAuthority) * 100.0);
		String flightMode = drone == null ? "unknown" : reportFlightModeFromCsv(drone.blackbox().toCsv());
		return String.format(
				Locale.ROOT,
				"{\n"
						+ "  \"passed\": %s,\n"
						+ "  \"reason\": \"%s\",\n"
						+ "  \"flight_model\": \"%s\",\n"
						+ "  \"flight_mode\": \"%s\",\n"
						+ "  \"self_test_control_mode\": \"%s\",\n"
						+ "  \"min_playable_low_altitude_authority\": %.5f,\n"
						+ "  \"max_playable_low_altitude_suppression_percent\": %.3f,\n"
						+ "  \"max_playable_visual_pitch_deg\": %.4f,\n"
						+ "  \"max_playable_visual_roll_deg\": %.4f,\n"
						+ "  \"max_playable_visual_yaw_rate_dps\": %.4f,\n"
						+ "  \"final_playable_visual_yaw_drift_deg\": %.4f,\n"
						+ "  \"playable_neutral_sample_count\": %d,\n"
						+ "  \"max_playable_neutral_visual_pitch_deg\": %.4f,\n"
						+ "  \"max_playable_neutral_visual_roll_deg\": %.4f,\n"
						+ "  \"max_playable_neutral_visual_yaw_rate_dps\": %.4f,\n"
						+ "  \"csv_column_count\": %d,\n"
						+ "  \"airframe_rotor_count\": %d,\n"
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
						+ "  \"final_altitude_gain_m\": %.5f,\n"
						+ "  \"final_horizontal_distance_m\": %.5f,\n"
						+ "  \"max_altitude_gain_m\": %.5f,\n"
						+ "  \"max_horizontal_distance_m\": %.5f,\n"
						+ "  \"max_speed_mps\": %.5f,\n"
						+ "  \"max_airspeed_mps\": %.5f,\n"
						+ "  \"max_motor_power\": %.5f,\n"
						+ "  \"max_avg_motor_rpm_telemetry_rpm\": %.2f,\n"
						+ "  \"max_motor_5_rpm_telemetry_rpm\": %.2f,\n"
						+ "  \"max_avg_motor_erpm100\": %.2f,\n"
						+ "  \"max_motor_5_erpm100\": %.2f,\n"
						+ "  \"min_avg_motor_einterval_us\": %.2f,\n"
						+ "  \"min_motor_5_einterval_us\": %.2f,\n"
						+ "  \"max_avg_motor_rpm_telemetry_valid\": %.5f,\n"
						+ "  \"max_motor_5_rpm_telemetry_valid\": %.5f,\n"
						+ "  \"max_battery_current_a\": %.5f,\n"
						+ "  \"max_battery_sag_v\": %.5f,\n"
						+ "  \"max_battery_effective_resistance_ohm\": %.6f,\n"
						+ "  \"max_battery_soc_resistance_scale\": %.5f,\n"
						+ "  \"max_battery_temp_resistance_scale\": %.5f,\n"
						+ "  \"max_battery_polarization_resistance_scale\": %.5f,\n"
						+ "  \"max_imu_supply_noise\": %.5f,\n"
						+ "  \"max_gyro_notch_hz\": %.5f,\n"
						+ "  \"max_gyro_notch_attenuation\": %.5f,\n"
						+ "  \"max_gyro_notch_spread_hz\": %.5f,\n"
						+ "  \"max_gyro_rpm_harmonic_notch_attenuation\": %.5f,\n"
						+ "  \"max_gyro_blade_pass_notch_hz\": %.5f,\n"
						+ "  \"max_gyro_blade_pass_notch_attenuation\": %.5f,\n"
						+ "  \"max_gyro_blade_pass_notch_spread_hz\": %.5f,\n"
						+ "  \"max_motor_winding_resistance_scale\": %.5f,\n"
						+ "  \"max_propwash\": %.5f,\n"
						+ "  \"max_vortex_ring_state\": %.5f,\n"
						+ "  \"max_vortex_ring_thrust_buffet\": %.5f,\n"
						+ "  \"max_vortex_ring_buffet_force_n\": %.5f,\n"
						+ "  \"max_rotor_induced_velocity_mps\": %.5f,\n"
						+ "  \"max_rotor_inflow_lag_loss_percent\": %.3f,\n"
						+ "  \"max_rotor_dynamic_inflow_tau_s\": %.5f,\n"
						+ "  \"max_rotor_translational_lift\": %.5f,\n"
						+ "  \"max_rotor_advance_ratio\": %.5f,\n"
						+ "  \"max_rotor_propeller_advance_ratio_j\": %.5f,\n"
						+ "  \"max_rotor_propeller_thrust_loss_percent\": %.3f,\n"
						+ "  \"max_rotor_propeller_power_loss_percent\": %.3f,\n"
						+ "  \"max_rotor_reverse_flow\": %.5f,\n"
						+ "  \"max_rotor_compressibility_loss_percent\": %.3f,\n"
						+ "  \"max_rotor_low_reynolds_loss\": %.5f,\n"
						+ "  \"max_rotor_blade_pass_ripple\": %.5f,\n"
						+ "  \"max_rotor_stall\": %.5f,\n"
						+ "  \"max_rotor_vibration\": %.5f,\n"
						+ "  \"max_rotor_damage_vibration\": %.5f,\n"
						+ "  \"max_rotor_coning\": %.5f,\n"
						+ "  \"max_rotor_wake_interference\": %.5f,\n"
						+ "  \"max_rotor_in_plane_drag_force_n\": %.5f,\n"
						+ "  \"max_rotor_coaxial_load_bias\": %.5f,\n"
						+ "  \"max_rotor_coaxial_load_bias_target\": %.5f,\n"
						+ "  \"max_rotor_coaxial_load_bias_clipping\": %.5f,\n"
						+ "  \"max_rotor_coaxial_allocation_load\": %.5f,\n"
						+ "  \"max_rotor_coaxial_allocation_ratio\": %.5f,\n"
						+ "  \"max_rotor_coaxial_allocation_mech_gain_pct\": %.5f,\n"
						+ "  \"max_rotor_coaxial_allocation_elec_gain_pct\": %.5f,\n"
						+ "  \"max_rotor_coaxial_allocation_uncertainty_pct\": %.5f,\n"
						+ "  \"max_rotor_wet_thrust_loss_percent\": %.3f,\n"
						+ "  \"max_rotor_icing_severity\": %.5f,\n"
						+ "  \"max_rotor_icing_thrust_loss_percent\": %.3f,\n"
						+ "  \"max_rotor_icing_power_scale\": %.5f,\n"
						+ "  \"max_rotor_wake_swirl_mps\": %.5f,\n"
						+ "  \"max_rotor_windmilling\": %.5f,\n"
						+ "  \"max_rotor_wake_swirl_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_active_braking_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_acceleration_reaction_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_gyroscopic_torque_nm\": %.6f,\n"
						+ "  \"max_rotor_flapping_torque_nm\": %.6f,\n"
						+ "  \"max_ground_effect_leveling_torque_nm\": %.6f,\n"
						+ "  \"max_airframe_body_drag_n\": %.5f,\n"
						+ "  \"max_linear_damping_drag_n\": %.5f,\n"
						+ "  \"max_airframe_drag_along_flow_n\": %.5f,\n"
						+ "  \"max_airframe_drag_equivalent_linear_k\": %.5f,\n"
						+ "  \"max_airframe_drag_equivalent_cda_m2\": %.5f,\n"
						+ "  \"max_airframe_drag_imav_ratio\": %.5f,\n"
						+ "  \"max_mixer_saturation\": %.5f,\n"
						+ "  \"csv\": \"%s\"\n"
						+ "}\n",
				passed,
				escapeJson(reason),
				flightModelMode.id(),
				escapeJson(flightMode),
				controlFlightMode.csvName(),
				minPlayableLowAltitudeAuthority,
				maxPlayableLowAltitudeSuppressionPercent,
				maxPlayableVisualPitchDegrees,
				maxPlayableVisualRollDegrees,
				maxPlayableVisualYawRateDegreesPerSecond,
				finalPlayableVisualYawDriftDegrees,
				playableNeutralSampleCount,
				maxPlayableNeutralVisualPitchDegrees,
				maxPlayableNeutralVisualRollDegrees,
				maxPlayableNeutralVisualYawRateDegreesPerSecond,
				DroneBlackboxSample.CSV_HEADER.split(",", -1).length,
				drone == null ? 0 : drone.config().rotors().size(),
				PHYSICS_STEPS_PER_TICK,
				PHYSICS_DT_SECONDS,
				PHYSICS_RATE_HERTZ,
				durationTicks,
				sampleCount,
				initialX,
				initialY,
				initialZ,
				finalX,
				finalY,
				finalZ,
				finalSpeed,
				finalAltitudeGain,
				finalHorizontalDistance,
				maxAltitudeGain,
				maxHorizontalDistance,
				maxSpeed,
				maxAirspeed,
				maxMotorPower,
				maxAverageMotorTelemetryRpm,
				maxMotor5TelemetryRpm,
				maxAverageMotorTelemetryErpm100,
				maxMotor5TelemetryErpm100,
				minAverageMotorTelemetryEIntervalMicros,
				minMotor5TelemetryEIntervalMicros,
				maxAverageMotorRpmTelemetryValidity,
				maxMotor5RpmTelemetryValidity,
				maxBatteryCurrent,
				maxBatterySag,
				maxBatteryEffectiveResistance,
				maxBatteryStateOfChargeResistanceScale,
				maxBatteryTemperatureResistanceScale,
				maxBatteryPolarizationResistanceScale,
				maxImuSupplyNoise,
				maxGyroNotchFrequency,
				maxGyroNotchAttenuation,
				maxGyroNotchSpread,
				maxGyroRpmHarmonicNotchAttenuation,
				maxGyroBladePassNotchFrequency,
				maxGyroBladePassNotchAttenuation,
				maxGyroBladePassNotchSpread,
				maxMotorWindingResistanceScale,
				maxPropwash,
				maxVortexRingState,
				maxVortexRingThrustBuffet,
				maxVortexRingBuffetForce,
				maxRotorInducedVelocity,
				(1.0 - minRotorInducedLagThrustScale) * 100.0,
				maxRotorDynamicInflowTimeConstant,
				maxRotorTranslationalLift,
				maxRotorAdvanceRatio,
				maxRotorPropellerAdvanceRatioJ,
				(1.0 - minRotorPropellerThrustScale) * 100.0,
				(1.0 - minRotorPropellerPowerScale) * 100.0,
				maxRotorReverseFlow,
				(1.0 - minRotorCompressibilityThrustScale) * 100.0,
				maxRotorLowReynoldsLoss,
				maxRotorBladePassRipple,
				maxRotorStall,
				maxRotorVibration,
				maxRotorDamageVibration,
				maxRotorConing,
				maxRotorWakeInterference,
				maxRotorInPlaneDragForce,
				maxRotorCoaxialLoadBias,
				maxRotorCoaxialLoadBiasTarget,
				maxRotorCoaxialLoadBiasClipping,
				maxRotorCoaxialAllocationLoadFraction,
				maxRotorCoaxialAllocationCommandRatio,
				maxRotorCoaxialAllocationMechanicalGainPercent,
				maxRotorCoaxialAllocationElectricalGainPercent,
				maxRotorCoaxialAllocationUncertaintyPercent,
				(1.0 - minRotorWetThrustScale) * 100.0,
				maxRotorIcingSeverity,
				(1.0 - minRotorIcingThrustScale) * 100.0,
				maxRotorIcingPowerScale,
				maxRotorWakeSwirlVelocity,
				maxRotorWindmilling,
				maxRotorWakeSwirlTorque,
				maxRotorActiveBrakingTorque,
				maxRotorAccelerationReactionTorque,
				maxRotorGyroscopicTorque,
				maxRotorFlappingTorque,
				maxGroundEffectLevelingTorque,
				maxAirframeBodyDragForce,
				maxLinearDampingDragForce,
				maxAirframeDragAlongFlow,
				maxAirframeDragEquivalentLinearCoefficient,
				maxAirframeDragEquivalentCdA,
				maxAirframeDragImavReferenceRatio,
				maxMixerSaturation,
				escapeJson(csvPath.toAbsolutePath().toString())
		);
	}

	private static String reportFlightModeFromCsv(String csv) {
		if (csv == null || csv.isBlank()) {
			return "unknown";
		}
		String[] lines = csv.split("\\R");
		if (lines.length < 2) {
			return "unknown";
		}
		String[] header = lines[0].split(",", -1);
		int flightModeColumn = csvColumnIndex(header, "flight_mode");
		int controlFlightModeColumn = csvColumnIndex(header, "control_flight_mode");
		int armedColumn = csvColumnIndex(header, "armed");
		int controlArmedColumn = csvColumnIndex(header, "control_armed");
		int motorPowerColumn = csvColumnIndex(header, "motor_power");
		if (flightModeColumn < 0 && controlFlightModeColumn < 0) {
			return "unknown";
		}
		String fallbackFlightMode = "unknown";
		for (int i = 1; i < lines.length; i++) {
			if (lines[i].isBlank()) {
				continue;
			}
			String[] row = lines[i].split(",", -1);
			String rowFlightMode = reportFlightModeFromCsvRow(row, flightModeColumn, controlFlightModeColumn);
			if ("unknown".equals(rowFlightMode)) {
				continue;
			}
			if ("unknown".equals(fallbackFlightMode)) {
				fallbackFlightMode = rowFlightMode;
			}
			if (isActiveCsvFlightSample(row, armedColumn, controlArmedColumn, motorPowerColumn)) {
				return rowFlightMode;
			}
		}
		return fallbackFlightMode;
	}

	private static String reportFlightModeFromCsvRow(String[] row, int flightModeColumn, int controlFlightModeColumn) {
		String flightMode = normalizedFlightMode(csvValue(row, flightModeColumn));
		String controlFlightMode = normalizedFlightMode(csvValue(row, controlFlightModeColumn));
		if (isReportFlightMode(flightMode) && flightMode.equals(controlFlightMode)) {
			return flightMode;
		}
		if (isReportFlightMode(controlFlightMode)) {
			return controlFlightMode;
		}
		if (isReportFlightMode(flightMode)) {
			return flightMode;
		}
		return "unknown";
	}

	private static int csvColumnIndex(String[] header, String column) {
		for (int i = 0; i < header.length; i++) {
			if (column.equals(header[i])) {
				return i;
			}
		}
		return -1;
	}

	private static String csvValue(String[] row, int column) {
		return column >= 0 && column < row.length ? row[column] : "";
	}

	private static String normalizedFlightMode(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean isReportFlightMode(String value) {
		return "acro".equals(value) || "angle".equals(value) || "horizon".equals(value);
	}

	private static boolean isActiveCsvFlightSample(String[] row, int armedColumn, int controlArmedColumn, int motorPowerColumn) {
		return Boolean.parseBoolean(csvValue(row, armedColumn))
				|| Boolean.parseBoolean(csvValue(row, controlArmedColumn))
				|| parseCsvDouble(csvValue(row, motorPowerColumn)) > 0.08;
	}

	private static double parseCsvDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException ignored) {
			return 0.0;
		}
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

	private static FlightModelMode requestedFlightModelMode() {
		String property = System.getProperty(PROPERTY_FLIGHT_MODEL);
		if (property != null && !property.isBlank()) {
			return parseFlightModelMode(property);
		}
		String environment = System.getenv(ENV_FLIGHT_MODEL);
		if (environment != null && !environment.isBlank()) {
			return parseFlightModelMode(environment);
		}
		return FlightModelMode.SIMULATION;
	}

	private static FlightMode requestedControlFlightMode() {
		String property = System.getProperty(PROPERTY_CONTROL_MODE);
		if (property != null && !property.isBlank()) {
			return parseControlFlightMode(property);
		}
		String environment = System.getenv(ENV_CONTROL_MODE);
		if (environment != null && !environment.isBlank()) {
			return parseControlFlightMode(environment);
		}
		return FlightMode.DEFAULT_FIRST_FLIGHT;
	}

	private static FlightModelMode parseFlightModelMode(String value) {
		if (value == null) {
			return FlightModelMode.SIMULATION;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "playable", "direct", "bypass" -> FlightModelMode.PLAYABLE;
			case "sim", "simulation", "physics", "6dof" -> FlightModelMode.SIMULATION;
			default -> FlightModelMode.SIMULATION;
		};
	}

	private static FlightMode parseControlFlightMode(String value) {
		if (value == null) {
			return FlightMode.DEFAULT_FIRST_FLIGHT;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "acro" -> FlightMode.ACRO;
			case "horizon" -> FlightMode.HORIZON;
			case "angle", "training", "stable" -> FlightMode.ANGLE;
			default -> FlightMode.DEFAULT_FIRST_FLIGHT;
		};
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
