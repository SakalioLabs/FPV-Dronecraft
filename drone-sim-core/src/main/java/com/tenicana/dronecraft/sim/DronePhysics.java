package com.tenicana.dronecraft.sim;

import java.util.Arrays;
import java.util.List;

public final class DronePhysics {
	public static final double BETAFLIGHT_EINTERVAL_INVALID_MICROS = 65_535.0;
	private static final double MOTOR_AMBIENT_TEMPERATURE_CELSIUS = 25.0;
	private static final double AVIONICS_CURRENT_AMPS = 1.2;
	private static final double MIN_THERMAL_THRUST_LIMIT = 0.45;
	private static final double MOTOR_STALL_CURRENT_SCALE = 3.20;
	private static final double MOTOR_NO_LOAD_OMEGA_SCALE = 1.35;
	private static final double ESC_RPM_TELEMETRY_MIN_VALID_MECHANICAL_RPM = 120.0;
	private static final double ESC_RPM_TELEMETRY_FULL_VALID_MECHANICAL_RPM = 200.0;
	private static final double ROTOR_ARM_FLEX_TILT_RADIANS = Math.toRadians(4.0);
	private static final double ROTOR_ARM_FLEX_VERTICAL_DEFLECTION_SCALE = 0.055;
	private static final double ROTOR_ARM_FLEX_NATURAL_FREQUENCY_HERTZ = 24.0;
	private static final double ROTOR_ARM_FLEX_DAMPING_RATIO = 0.42;
	private static final double ROTOR_ARM_FLEX_MAX_VELOCITY_PER_SECOND = 18.0;
	private static final double ROTOR_DISK_WIND_GRADIENT_MAX_TILT_RADIANS = Math.toRadians(4.5);
	private static final double ROTOR_DISK_WIND_GRADIENT_MAX_THRUST_LOSS = 0.045;
	private static final double A4MC_LOCAL_PRESSURE_CENTER_HOVER_WASH_GATE_SCALE = 0.42;
	private static final double A4MC_LOCAL_PRESSURE_CENTER_HOVER_DOWNLOAD_GAIN = 0.018;
	private static final double ROTOR_WINDMILL_MAX_OMEGA_FRACTION = 0.32;
	private static final double COAXIAL_LOAD_BIAS_MAX = CoaxialAllocationCalibration.LOAD_BIAS_MAX;
	private static final double MOTOR_STATIC_BREAKAWAY_TORQUE_NEWTON_METERS = 0.030;
	private static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	private static final double APDRONE_CTCPJ_REFERENCE_RADIUS_METERS = 5.1 * 0.0254 * 0.5;
	private static final double APDRONE_CTCPJ_REFERENCE_PITCH_TO_DIAMETER_RATIO = 4.5 / 5.1;
	private static final double APDRONE_CTCPJ_REFERENCE_GEOMETRY_TOLERANCE = 1.0e-6;
	private static final double REFERENCE_AIR_TEMPERATURE_KELVIN = 298.15;
	private static final double REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.837e-5;
	private static final double AIR_SUTHERLAND_CONSTANT_KELVIN = 110.4;
	// CALCE low-current LiPo OCV median, normalized over each preset's configured usable empty/full voltage window.
	private static final double[] LIPO_OCV_SOC_POINTS = {0.0, 0.04, 0.05, 0.10, 0.18, 0.20, 0.35, 0.50, 0.65, 0.80, 0.90, 1.0};
	private static final double[] LIPO_OCV_NORMALIZED_POINTS = {0.0, 0.090, 0.137, 0.181, 0.261, 0.279, 0.349, 0.405, 0.540, 0.702, 0.826, 1.0};
	private static final double[] LIPO_SLOW_POLARIZATION_SOC_POINTS = {0.0, 0.50, 0.80, 1.0};
	private static final double[] LIPO_SLOW_POLARIZATION_RECOVERY_TAU_SECONDS = {360.0, 630.0, 79.0, 120.0};
	private static final double LIPO_MENDELEY_REFERENCE_AGING_CYCLES = 450.0;
	private static final double[] LIPO_MENDELEY_R0_SOC_POINTS = {0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.0};
	private static final double[] LIPO_MENDELEY_R0_FRESH_SCALE = {
			1.0326822976566437, 1.015915885475362, 1.018510929006367, 1.0200955572677333,
			1.0183323058810432, 1.0197545280325397, 1.0174728004953455, 1.015348458875896,
			1.0083956381039862, 1.002684224007052
	};
	private static final double[] LIPO_MENDELEY_R0_AGED_SCALE = {
			1.1523512095542858, 1.1143059803007573, 1.1281835986067184, 1.1275984440817655,
			1.1350752504251385, 1.1385042770480385, 1.1238252952951855, 1.1336659929580892,
			1.1295256100692384, 1.1031165689083977
	};
	private static final double[] LIPO_MENDELEY_R0_WORN_SCALE = {
			1.1902959848598709, 1.1688627229470703, 1.165424310370534, 1.1773179790552062,
			1.2106301151226226, 1.1773193885232351, 1.181201236162614, 1.2141890535118343,
			1.1501151032140817, 1.1452400882358447
	};
	private static final double BAROMETER_ALTITUDE_TIME_CONSTANT_SECONDS =
			SensorNoiseCalibration.BAROMETER_ALTITUDE_TIME_CONSTANT_SECONDS;
	private static final double BAROMETER_VERTICAL_SPEED_TIME_CONSTANT_SECONDS =
			SensorNoiseCalibration.BAROMETER_VERTICAL_SPEED_TIME_CONSTANT_SECONDS;
	private static final double GYRO_FULL_SCALE_RADIANS_PER_SECOND = Math.toRadians(2000.0);
	private static final double ACCELEROMETER_FULL_SCALE_METERS_PER_SECOND_SQUARED = 16.0 * 9.80665;
	private static final int GYRO_DELAY_BUFFER_SIZE = 256;
	private static final int CONTROL_DELAY_BUFFER_SIZE = 256;
	private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 BODY_ROTOR_AXIS = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 BODY_RIGHT = new Vec3(1.0, 0.0, 0.0);
	private static final Vec3 BODY_FORWARD = new Vec3(0.0, 0.0, 1.0);
	static final double JOHNSON_VRS_MODEL_JOIN_LOW_VI = 0.20;
	static final double JOHNSON_VRS_ZERO_DAMPING_LOW_VI = 0.45;
	static final double JOHNSON_VRS_ZERO_DAMPING_HIGH_VI = 1.50;
	static final double JOHNSON_VRS_MODEL_JOIN_HIGH_VI = 2.00;
	static final double JOHNSON_BASELINE_FORWARD_CUTOFF_VX_OVER_VH = 0.75;
	static final double JOHNSON_VRS_FORWARD_CUTOFF_VX_OVER_VH = 0.95;
	private DroneConfig config;
	private final DroneState state;
	private final PidController pitchPid;
	private final PidController yawPid;
	private final PidController rollPid;
	private final double[] targetRotorThrusts;
	private final double[] escDesyncPhases;
	private final double[] motorCommutationPhases;
	private final double[] rotorBladePassPhases;
	private final double[] rotorImbalancePhases;
	private final double[] rotorVortexBuffetPhases;
	private final double[] rotorBladeStallBuffetPhases;
	private final double[] rotorBladeElementStallIntensity;
	private final double[] rotorBladeElementThrustScale;
	private final double[] rotorBladeElementLoadFactor;
	private final double[] rotorBladeElementVibration;
	private final double[] rotorBladeDissymmetryIntensity;
	private final double[] rotorBladeDissymmetryThrustScale;
	private final double[] rotorBladeDissymmetryLoadFactor;
	private final double[] rotorBladeDissymmetryVibration;
	private final double[] rotorBladeDissymmetryReverseFlowInboardFraction;
	private final double[] rotorDynamicStallIntensity;
	private final double[] rotorInducedWakeVelocityMetersPerSecond;
	private final double[] rotorInducedWakeCarryoverIntensity;
	private final double[] rotorSurfaceWetness;
	private final double[] rotorVortexRingStateIntensity;
	private final double[] rotorWakeInterferenceIntensity;
	private final Vec3[] rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond;
	private final Vec3[] rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond;
	private final double[] rotorSurfaceEffectThrustMultipliers;
	private final double[] rotorConingIntensity;
	private final double[] rotorConingVelocity;
	private final double[] heldEscOutputCommands;
	private final double[] escCommandFrameClockSeconds;
	private final double[] escCommandFrameAgeSeconds;
	private final double[] escCommandErrors;
	private final boolean[] escCommandFrameInitialized;
	private final double[] escRpmTelemetryOmegaRadiansPerSecond;
	private final double[] escRpmTelemetryFrameClockSeconds;
	private final double[] escRpmTelemetryFrameAgeSeconds;
	private final double[] escRpmTelemetryDropoutPhases;
	private final boolean[] escRpmTelemetryFrameInitialized;
	private final double[] gyroMotorVibrationPhases;
	private final double[] gyroBladePassVibrationPhases;
	private final double[] rotorArmFlexIntensity;
	private final double[] rotorArmFlexVelocity;
	private final Vec3[] rotorFlappingTiltBody;
	private final Vec3[] previousRotorForceBodyNewtons;
	private final Vec3[] previousRotorTorqueBodyNewtonMeters;
	private final Vec3[] rotorWallEffectForceBodyFiltered;
	private final Vec3[] gyroDelayBuffer = new Vec3[GYRO_DELAY_BUFFER_SIZE];
	private final Vec3[] accelerometerDelayBuffer = new Vec3[GYRO_DELAY_BUFFER_SIZE];
	private final DroneInput[] controlDelayBuffer = new DroneInput[CONTROL_DELAY_BUFFER_SIZE];
	private Vec3 gyroFilteredBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 accelerometerFilteredBodyMetersPerSecondSquared = Vec3.ZERO;
	private DroneInput lastLinkedControlInput = DroneInput.idle();
	private DroneInput smoothedControlInput = DroneInput.idle();
	private DroneInput receiverFrameInput = DroneInput.idle();
	private int gyroDelayWriteIndex;
	private int accelerometerDelayWriteIndex;
	private int controlDelayWriteIndex;
	private double receiverFrameClockSeconds;
	private double receiverFrameAgeSeconds;
	private double gyroNoiseTimeSeconds;
	private double accelerometerNoiseTimeSeconds;
	private double barometerNoiseTimeSeconds;
	private double barometerFilteredAltitudeMeters;
	private double barometerFilteredVerticalSpeedMetersPerSecond;
	private double barometerPressurePortErrorFilteredMeters;
	private double barometerPropwashErrorFilteredMeters;
	private boolean barometerInitialized;
	private Vec3 gyroBiasBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 accelerometerBiasBodyMetersPerSecondSquared = Vec3.ZERO;
	private double sensorBiasTimeSeconds;
	private double controlLinkLossSeconds;
	private double propwashPhaseA;
	private double propwashPhaseB;
	private double airframeSeparationBuffetPhaseA;
	private double airframeSeparationBuffetPhaseB;
	private double airframeSeparatedFlowIntensity;
	private Vec3 rotorWashDragForceBodyFiltered = Vec3.ZERO;
	private Vec3 rotorWashAirframeAngularDampingFiltered = Vec3.ZERO;
	private Vec3 dynamicPressureCenterOffsetBodyFiltered = Vec3.ZERO;
	private Vec3 airframeLiftForceBodyFiltered = Vec3.ZERO;
	private Vec3 airframeDragForceBodyFiltered = Vec3.ZERO;
	private Vec3 groundEffectDragForceBodyFiltered = Vec3.ZERO;
	private Vec3 groundEffectLevelingTorqueBodyFiltered = Vec3.ZERO;
	private double turbulencePhaseA;
	private double turbulencePhaseB;
	private double turbulencePhaseC;
	private double windGustPhaseA;
	private double windGustPhaseB;
	private double windGustPhaseC;
	private Vec3 meanWindVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 windBurbleVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 drydenFirstOrderVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 drydenTransverseLagVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 drydenTurbulenceVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 a4mcSourceGustVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 a4mcUpdraftVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 a4mcTerrainShearVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 windGustVelocityWorldMetersPerSecond = Vec3.ZERO;
	private long drydenRandomState = 0x6A09E667F3BCC909L;
	private double drydenSpareGaussian;
	private boolean hasDrydenSpareGaussian;
	private boolean windModelInitialized;
	private boolean batteryThermalInitialized;
	private Vec3 previousTargetRatesRadiansPerSecond = Vec3.ZERO;
	private Vec3 previousPidGyroRatesBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 feedForwardTorqueBody = Vec3.ZERO;
	private boolean hasPreviousTargetRates;
	private boolean hasPreviousPidGyroRates;
	private double previousThrottle;
	private double antiGravityTransient;

	public record RotorDynamicState(
			double[] motorOmegaRadiansPerSecond,
			double[] escOutputCommand,
			double[] escElectricalOutputCommand,
			double[] motorRpmTelemetryRpm,
			double[] motorRpmTelemetryValidity,
			double[] rotorInducedVelocityMetersPerSecond,
			double[] rotorInducedLagThrustScale,
			double[] rotorInducedWakeVelocityMetersPerSecond,
			double[] rotorInducedWakeCarryoverIntensity,
			double[] rotorSurfaceWetness,
			double[] rotorIcingSeverity,
			double propwashWakeIntensity,
			double propwashIntensity,
			double vortexRingStateIntensity,
			double vortexRingThrustBuffetAmplitude,
			double vortexRingMaxThrustBuffetAmplitude
	) {
		public RotorDynamicState {
			motorOmegaRadiansPerSecond = copyOrNull(motorOmegaRadiansPerSecond);
			escOutputCommand = copyOrNull(escOutputCommand);
			escElectricalOutputCommand = copyOrNull(escElectricalOutputCommand);
			motorRpmTelemetryRpm = copyOrNull(motorRpmTelemetryRpm);
			motorRpmTelemetryValidity = copyOrNull(motorRpmTelemetryValidity);
			rotorInducedVelocityMetersPerSecond = copyOrNull(rotorInducedVelocityMetersPerSecond);
			rotorInducedLagThrustScale = copyOrNull(rotorInducedLagThrustScale);
			rotorInducedWakeVelocityMetersPerSecond = copyOrNull(rotorInducedWakeVelocityMetersPerSecond);
			rotorInducedWakeCarryoverIntensity = copyOrNull(rotorInducedWakeCarryoverIntensity);
			rotorSurfaceWetness = copyOrNull(rotorSurfaceWetness);
			rotorIcingSeverity = copyOrNull(rotorIcingSeverity);
		}

		@Override
		public double[] motorOmegaRadiansPerSecond() {
			return copyOrNull(motorOmegaRadiansPerSecond);
		}

		@Override
		public double[] escOutputCommand() {
			return copyOrNull(escOutputCommand);
		}

		@Override
		public double[] escElectricalOutputCommand() {
			return copyOrNull(escElectricalOutputCommand);
		}

		@Override
		public double[] motorRpmTelemetryRpm() {
			return copyOrNull(motorRpmTelemetryRpm);
		}

		@Override
		public double[] motorRpmTelemetryValidity() {
			return copyOrNull(motorRpmTelemetryValidity);
		}

		@Override
		public double[] rotorInducedVelocityMetersPerSecond() {
			return copyOrNull(rotorInducedVelocityMetersPerSecond);
		}

		@Override
		public double[] rotorInducedLagThrustScale() {
			return copyOrNull(rotorInducedLagThrustScale);
		}

		@Override
		public double[] rotorInducedWakeVelocityMetersPerSecond() {
			return copyOrNull(rotorInducedWakeVelocityMetersPerSecond);
		}

		@Override
		public double[] rotorInducedWakeCarryoverIntensity() {
			return copyOrNull(rotorInducedWakeCarryoverIntensity);
		}

		@Override
		public double[] rotorSurfaceWetness() {
			return copyOrNull(rotorSurfaceWetness);
		}

		@Override
		public double[] rotorIcingSeverity() {
			return copyOrNull(rotorIcingSeverity);
		}

		private static double[] copyOrNull(double[] values) {
			return values == null ? null : Arrays.copyOf(values, values.length);
		}
	}

	public record AerodynamicTransientState(
			Vec3 meanWindVelocityWorldMetersPerSecond,
			Vec3 windBurbleVelocityWorldMetersPerSecond,
			Vec3 drydenFirstOrderVelocityWorldMetersPerSecond,
			Vec3 drydenTransverseLagVelocityWorldMetersPerSecond,
			Vec3 drydenTurbulenceVelocityWorldMetersPerSecond,
			Vec3 a4mcSourceGustVelocityWorldMetersPerSecond,
			Vec3 a4mcUpdraftVelocityWorldMetersPerSecond,
			Vec3 a4mcTerrainShearVelocityWorldMetersPerSecond,
			Vec3 windGustVelocityWorldMetersPerSecond,
			long drydenRandomState,
			double drydenSpareGaussian,
			boolean hasDrydenSpareGaussian,
			boolean windModelInitialized,
			double windGustPhaseA,
			double windGustPhaseB,
			double windGustPhaseC,
			double turbulencePhaseA,
			double turbulencePhaseB,
			double turbulencePhaseC,
			double airframeSeparatedFlowIntensity,
			double airframeSeparationBuffetPhaseA,
			double airframeSeparationBuffetPhaseB,
			Vec3 rotorWashDragForceBody,
			Vec3 rotorWashAirframeAngularDamping,
			Vec3 dynamicPressureCenterOffsetBody,
			Vec3 airframeLiftForceBody,
			Vec3 airframeDragForceBody,
			Vec3 groundEffectDragForceBody,
			Vec3 groundEffectLevelingTorqueBody
	) {
		public AerodynamicTransientState {
			meanWindVelocityWorldMetersPerSecond = finiteVecOrZero(meanWindVelocityWorldMetersPerSecond);
			windBurbleVelocityWorldMetersPerSecond = finiteVecOrZero(windBurbleVelocityWorldMetersPerSecond);
			drydenFirstOrderVelocityWorldMetersPerSecond = finiteVecOrZero(drydenFirstOrderVelocityWorldMetersPerSecond);
			drydenTransverseLagVelocityWorldMetersPerSecond = finiteVecOrZero(drydenTransverseLagVelocityWorldMetersPerSecond);
			drydenTurbulenceVelocityWorldMetersPerSecond = finiteVecOrZero(drydenTurbulenceVelocityWorldMetersPerSecond);
			a4mcSourceGustVelocityWorldMetersPerSecond = finiteVecOrZero(a4mcSourceGustVelocityWorldMetersPerSecond);
			a4mcUpdraftVelocityWorldMetersPerSecond = finiteVecOrZero(a4mcUpdraftVelocityWorldMetersPerSecond);
			a4mcTerrainShearVelocityWorldMetersPerSecond = finiteVecOrZero(a4mcTerrainShearVelocityWorldMetersPerSecond);
			windGustVelocityWorldMetersPerSecond = finiteVecOrZero(windGustVelocityWorldMetersPerSecond);
			rotorWashDragForceBody = finiteVecOrZero(rotorWashDragForceBody);
			rotorWashAirframeAngularDamping = finiteVecOrZero(rotorWashAirframeAngularDamping);
			dynamicPressureCenterOffsetBody = finiteVecOrZero(dynamicPressureCenterOffsetBody);
			airframeLiftForceBody = finiteVecOrZero(airframeLiftForceBody);
			airframeDragForceBody = finiteVecOrZero(airframeDragForceBody);
			groundEffectDragForceBody = finiteVecOrZero(groundEffectDragForceBody);
			groundEffectLevelingTorqueBody = finiteVecOrZero(groundEffectLevelingTorqueBody);
		}
	}

	private record MotorCurrentEstimate(
			double dischargeCurrentAmps,
			double regenerativeCurrentAmps,
			double thermalCurrentAmps,
			double phaseCurrentAmps,
			double currentRippleAmps,
			double electricalEfficiency
	) {
	}

	private record MotorCommutationRipple(
			double intensity,
			double torqueRippleNewtonMeters,
			double deltaOmegaRadiansPerSecond
	) {
	}

	private record RotorBladePassRipple(
			double thrustScale,
			double vibration,
			double intensity
	) {
		private static final RotorBladePassRipple IDLE = new RotorBladePassRipple(1.0, 0.0, 0.0);
	}

	private record RotorVortexRingBuffet(
			double thrustScale,
			Vec3 forceBody,
			double vibration,
			double thrustAmplitude
	) {
		private static final RotorVortexRingBuffet IDLE = new RotorVortexRingBuffet(1.0, Vec3.ZERO, 0.0, 0.0);
	}

	private record RotorBladeStallBuffet(
			double thrustScale,
			Vec3 forceBody,
			double vibration
	) {
		private static final RotorBladeStallBuffet IDLE = new RotorBladeStallBuffet(1.0, Vec3.ZERO, 0.0);
	}

	private record RotorWakeInterference(
			double[] intensity,
			Vec3[] downwashVelocityBodyMetersPerSecond,
			Vec3[] swirlVelocityBodyMetersPerSecond
	) {
		private double intensity(int index) {
			return index >= 0 && index < intensity.length ? intensity[index] : 0.0;
		}

		private Vec3 downwashVelocityBodyMetersPerSecond(int index) {
			return index >= 0 && index < downwashVelocityBodyMetersPerSecond.length
					? downwashVelocityBodyMetersPerSecond[index]
					: Vec3.ZERO;
		}

		private Vec3 swirlVelocityBodyMetersPerSecond(int index) {
			return index >= 0 && index < swirlVelocityBodyMetersPerSecond.length
					? swirlVelocityBodyMetersPerSecond[index]
					: Vec3.ZERO;
		}
	}

	private record RotorWakeFlow(double axialDescentSpeedMetersPerSecond, double transverseSpeedMetersPerSecond) {
	}

	private record RotorConvectedWake(double overlap, double missDistanceMeters, Vec3 swirlOffsetBody) {
		private static final RotorConvectedWake IDLE = new RotorConvectedWake(0.0, Double.POSITIVE_INFINITY, Vec3.ZERO);
	}

	public static double betaflightErpm100FromMechanicalRpm(double mechanicalRpm) {
		return betaflightErpm100FromMechanicalRpm(mechanicalRpm, RotorSpec.DEFAULT_MOTOR_POLE_PAIRS);
	}

	public static double betaflightErpm100FromMechanicalRpm(double mechanicalRpm, double motorPolePairs) {
		if (!Double.isFinite(mechanicalRpm) || mechanicalRpm <= 0.0) {
			return 0.0;
		}
		return mechanicalRpm * normalizedMotorPolePairs(motorPolePairs) / 100.0;
	}

	public static double bladePassFrequencyHertz(double mechanicalRpm, int bladeCount) {
		if (!Double.isFinite(mechanicalRpm) || mechanicalRpm <= 0.0 || bladeCount <= 0) {
			return 0.0;
		}
		return mechanicalRpm * bladeCount / 60.0;
	}

	public static double sampledFrequencyAliasHertz(double frequencyHertz, double sampleRateHertz) {
		if (!Double.isFinite(frequencyHertz)
				|| !Double.isFinite(sampleRateHertz)
				|| frequencyHertz <= 0.0
				|| sampleRateHertz <= 0.0) {
			return 0.0;
		}
		double folded = frequencyHertz % sampleRateHertz;
		if (folded > sampleRateHertz * 0.5) {
			folded = sampleRateHertz - folded;
		}
		return Math.max(0.0, folded);
	}

	public static double bladePassAliasHertz(double mechanicalRpm, int bladeCount, double sampleRateHertz) {
		return sampledFrequencyAliasHertz(bladePassFrequencyHertz(mechanicalRpm, bladeCount), sampleRateHertz);
	}

	public static double betaflightEIntervalMicrosFromMechanicalRpm(double mechanicalRpm) {
		return betaflightEIntervalMicrosFromMechanicalRpm(mechanicalRpm, RotorSpec.DEFAULT_MOTOR_POLE_PAIRS);
	}

	public static double betaflightEIntervalMicrosFromMechanicalRpm(double mechanicalRpm, double motorPolePairs) {
		double electricalRpm = mechanicalRpm * normalizedMotorPolePairs(motorPolePairs);
		if (!Double.isFinite(electricalRpm) || electricalRpm <= 0.0) {
			return 0.0;
		}
		return 60_000_000.0 / electricalRpm;
	}

	public static double betaflightEIntervalMicrosFromTelemetryRpm(double mechanicalRpm, double telemetryValidity) {
		return betaflightEIntervalMicrosFromTelemetryRpm(
				mechanicalRpm,
				telemetryValidity,
				RotorSpec.DEFAULT_MOTOR_POLE_PAIRS
		);
	}

	public static double betaflightEIntervalMicrosFromTelemetryRpm(
			double mechanicalRpm,
			double telemetryValidity,
			double motorPolePairs
	) {
		if (telemetryValidity < 0.5) {
			return BETAFLIGHT_EINTERVAL_INVALID_MICROS;
		}
		return betaflightEIntervalMicrosFromMechanicalRpm(mechanicalRpm, motorPolePairs);
	}

	private static double normalizedMotorPolePairs(double motorPolePairs) {
		if (!Double.isFinite(motorPolePairs) || motorPolePairs <= 0.0) {
			return RotorSpec.DEFAULT_MOTOR_POLE_PAIRS;
		}
		return MathUtil.clamp(motorPolePairs, 1.0, 28.0);
	}

	private static double motorPolePairs(RotorSpec rotor) {
		return rotor == null ? RotorSpec.DEFAULT_MOTOR_POLE_PAIRS : normalizedMotorPolePairs(rotor.motorPolePairs());
	}

	public DronePhysics(DroneConfig config) {
		this.config = config;
		this.state = new DroneState(config.rotors().size());
		this.state.setBatteryVoltage(config.nominalBatteryVoltage());
		this.state.setBatteryOpenCircuitVoltage(config.nominalBatteryVoltage());
		this.state.setBatteryEffectiveResistanceOhms(config.batteryInternalResistanceOhms());
		this.state.setBatteryStateOfChargeResistanceScale(1.0);
		this.state.setBatteryTemperatureResistanceScale(1.0);
		updateBatterySagCurrentTelemetry(config.batteryInternalResistanceOhms());
		this.pitchPid = new PidController(config.pitchGains());
		this.yawPid = new PidController(config.yawGains());
		this.rollPid = new PidController(config.rollGains());
		this.targetRotorThrusts = new double[config.rotors().size()];
		this.escDesyncPhases = new double[config.rotors().size()];
		this.motorCommutationPhases = new double[config.rotors().size()];
		this.rotorBladePassPhases = new double[config.rotors().size()];
		this.rotorImbalancePhases = new double[config.rotors().size()];
		this.rotorVortexBuffetPhases = new double[config.rotors().size()];
		this.rotorBladeStallBuffetPhases = new double[config.rotors().size()];
		this.rotorBladeElementStallIntensity = new double[config.rotors().size()];
		this.rotorBladeElementThrustScale = new double[config.rotors().size()];
		this.rotorBladeElementLoadFactor = new double[config.rotors().size()];
		this.rotorBladeElementVibration = new double[config.rotors().size()];
		this.rotorBladeDissymmetryIntensity = new double[config.rotors().size()];
		this.rotorBladeDissymmetryThrustScale = new double[config.rotors().size()];
		this.rotorBladeDissymmetryLoadFactor = new double[config.rotors().size()];
		this.rotorBladeDissymmetryVibration = new double[config.rotors().size()];
		this.rotorBladeDissymmetryReverseFlowInboardFraction = new double[config.rotors().size()];
		this.rotorDynamicStallIntensity = new double[config.rotors().size()];
		this.rotorInducedWakeVelocityMetersPerSecond = new double[config.rotors().size()];
		this.rotorInducedWakeCarryoverIntensity = new double[config.rotors().size()];
		this.rotorSurfaceWetness = new double[config.rotors().size()];
		this.rotorVortexRingStateIntensity = new double[config.rotors().size()];
		this.rotorWakeInterferenceIntensity = new double[config.rotors().size()];
		this.rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond = new Vec3[config.rotors().size()];
		this.rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond = new Vec3[config.rotors().size()];
		this.rotorSurfaceEffectThrustMultipliers = new double[config.rotors().size()];
		this.rotorConingIntensity = new double[config.rotors().size()];
		this.rotorConingVelocity = new double[config.rotors().size()];
		this.heldEscOutputCommands = new double[config.rotors().size()];
		this.escCommandFrameClockSeconds = new double[config.rotors().size()];
		this.escCommandFrameAgeSeconds = new double[config.rotors().size()];
		this.escCommandErrors = new double[config.rotors().size()];
		this.escCommandFrameInitialized = new boolean[config.rotors().size()];
		this.escRpmTelemetryOmegaRadiansPerSecond = new double[config.rotors().size()];
		this.escRpmTelemetryFrameClockSeconds = new double[config.rotors().size()];
		this.escRpmTelemetryFrameAgeSeconds = new double[config.rotors().size()];
		this.escRpmTelemetryDropoutPhases = new double[config.rotors().size()];
		this.escRpmTelemetryFrameInitialized = new boolean[config.rotors().size()];
		this.gyroMotorVibrationPhases = new double[config.rotors().size()];
		this.gyroBladePassVibrationPhases = new double[config.rotors().size()];
		this.rotorArmFlexIntensity = new double[config.rotors().size()];
		this.rotorArmFlexVelocity = new double[config.rotors().size()];
		this.rotorFlappingTiltBody = new Vec3[config.rotors().size()];
		this.previousRotorForceBodyNewtons = new Vec3[config.rotors().size()];
		this.previousRotorTorqueBodyNewtonMeters = new Vec3[config.rotors().size()];
		this.rotorWallEffectForceBodyFiltered = new Vec3[config.rotors().size()];
		Arrays.fill(this.rotorFlappingTiltBody, Vec3.ZERO);
		Arrays.fill(this.previousRotorForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(this.previousRotorTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(this.rotorWallEffectForceBodyFiltered, Vec3.ZERO);
		Arrays.fill(this.rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(this.rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(this.rotorBladeElementThrustScale, 1.0);
		Arrays.fill(this.rotorBladeDissymmetryThrustScale, 1.0);
		Arrays.fill(this.rotorSurfaceEffectThrustMultipliers, 1.0);
		resetSensorBiasModel();
		resetGyroModel();
		resetAccelerometerModel();
		resetBarometerModel();
		resetControlLinkModel();
		resetAttitudeEstimator();
	}

	public DroneConfig config() {
		return config;
	}

	public void applyConfig(DroneConfig config) {
		if (config.rotors().size() != this.config.rotors().size()) {
			throw new IllegalArgumentException("Runtime tuning cannot change rotor count");
		}

		DroneConfig previousConfig = this.config;
		double previousStateOfCharge = state.batteryStateOfCharge();
		this.config = config;
		pitchPid.setGains(config.pitchGains());
		yawPid.setGains(config.yawGains());
		rollPid.setGains(config.rollGains());
		if (batteryModelChanged(previousConfig, config)) {
			double capacityAmpSeconds = effectiveBatteryCapacityAmpSeconds();
			state.setBatteryAmpSecondsConsumed((1.0 - previousStateOfCharge) * capacityAmpSeconds);
			state.setBatteryTransientSagVoltage(0.0);
			state.setBatteryVoltageSpike(0.0);
			state.setBatteryBusRippleVoltage(0.0);
			batteryThermalInitialized = false;
			state.setBatteryRegenerativeCurrentAmps(0.0);
			state.setBatteryCurrentLimit(1.0);
			updateBatteryVoltage(state.batteryCurrentAmps(), 0.0, DroneEnvironment.calm(), 0.0);
		}
		if (flightControllerSensorModelChanged(previousConfig, config)) {
			resetSensorBiasModel();
			resetGyroModel();
			resetAccelerometerModel();
			resetBarometerModel();
			resetAttitudeEstimator();
		}
		if (controlLinkModelChanged(previousConfig, config)) {
			resetControlLinkModel();
		}
		if (escCommandSignalModelChanged(previousConfig, config)) {
			resetEscSignalModel();
		}
		updateMotorThermalLimit();
		updateEscThermalLimit();
	}

	public DroneState state() {
		return state;
	}

	public void sleepAtRest(Vec3 positionMeters, DroneInput input) {
		Vec3 safePosition = positionMeters == null ? state.positionMeters() : positionMeters;
		state.setPositionMeters(safePosition);
		constrainAtRestKinematics();
		levelAtRestAttitude();
		DroneInput normalized = input == null ? DroneInput.idle() : input.normalized();
		DroneInput processed = input == null
				? DroneInput.idle()
				: new DroneInput(0.0, 0.0, 0.0, 0.0, normalized.armed(), normalized.linkActive(), normalized.flightMode());
		state.setRawControlInput(normalized);
		state.setProcessedControlInput(processed);
		controlLinkLossSeconds = normalized.linkActive() ? 0.0 : controlLinkLossSeconds;
		state.setControlLinkLossSeconds(controlLinkLossSeconds);
		state.setControlFailsafeActive(!normalized.linkActive());
		state.setControlFrameTelemetry(0.0, receiverFrameIntervalSeconds(), controlFrameError(normalized, processed));
		state.resetMotors();
		resetControlLoops();
		resetEscSignalModel();
		resetEscRpmTelemetryModel();
	}

	public void constrainAtRest(Vec3 positionMeters) {
		Vec3 safePosition = positionMeters == null ? state.positionMeters() : positionMeters;
		state.setPositionMeters(safePosition);
		constrainAtRestKinematics();
	}

	public void levelAtRest(Vec3 positionMeters) {
		constrainAtRest(positionMeters);
		levelAtRestAttitude();
	}

	private void constrainAtRestKinematics() {
		state.setVelocityMetersPerSecond(Vec3.ZERO);
		state.setLinearAccelerationWorldMetersPerSecondSquared(Vec3.ZERO);
		state.setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		state.setAngularAccelerationBodyRadiansPerSecondSquared(Vec3.ZERO);
		state.setGyroAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		state.setContactTelemetry(0.0, 0.0, 0.0);
	}

	private void levelAtRestAttitude() {
		state.setOrientation(Quaternion.IDENTITY);
		state.setEstimatedOrientation(Quaternion.IDENTITY);
	}

	public void restoreBatteryTransientState(
			double slowPolarizationVoltage,
			double temperatureCelsius,
			double coolingFactor,
			double thermalLimit
	) {
		state.setBatterySlowPolarizationVoltage(slowPolarizationVoltage);
		state.setBatteryTemperatureCelsius(temperatureCelsius);
		state.setBatteryCoolingFactor(coolingFactor);
		state.setBatteryThermalLimit(thermalLimit);
		batteryThermalInitialized = true;
	}

	public void restorePowertrainThermalState(
			double[] motorTemperaturesCelsius,
			double[] escTemperaturesCelsius,
			double[] motorCoolingFactors,
			double[] escCoolingFactors
	) {
		int count = Math.min(state.motorCount(), config.rotors().size());
		for (int i = 0; i < count; i++) {
			if (motorTemperaturesCelsius != null
					&& i < motorTemperaturesCelsius.length
					&& Double.isFinite(motorTemperaturesCelsius[i])) {
				state.setMotorTemperatureCelsius(i, motorTemperaturesCelsius[i]);
			}
			if (escTemperaturesCelsius != null
					&& i < escTemperaturesCelsius.length
					&& Double.isFinite(escTemperaturesCelsius[i])) {
				state.setEscTemperatureCelsius(i, escTemperaturesCelsius[i]);
			}
			if (motorCoolingFactors != null
					&& i < motorCoolingFactors.length
					&& Double.isFinite(motorCoolingFactors[i])) {
				state.setMotorCoolingFactor(i, motorCoolingFactors[i]);
			}
			if (escCoolingFactors != null
					&& i < escCoolingFactors.length
					&& Double.isFinite(escCoolingFactors[i])) {
				state.setEscCoolingFactor(i, escCoolingFactors[i]);
			}
			updateMotorWindingResistanceScale(i);
			state.setEscThermalLimit(i, escThermalLimit(state.escTemperatureCelsius(i)));
		}
		updateMotorThermalLimit();
		updateEscThermalLimit();
	}

	public RotorDynamicState rotorDynamicStateSnapshot() {
		return new RotorDynamicState(
				state.motorOmegaRadiansPerSecond(),
				state.escOutputCommand(),
				state.escElectricalOutputCommand(),
				state.motorRpmTelemetryRpm(),
				state.motorRpmTelemetryValidity(),
				state.rotorInducedVelocityMetersPerSecond(),
				state.rotorInducedLagThrustScale(),
				Arrays.copyOf(rotorInducedWakeVelocityMetersPerSecond, rotorInducedWakeVelocityMetersPerSecond.length),
				Arrays.copyOf(rotorInducedWakeCarryoverIntensity, rotorInducedWakeCarryoverIntensity.length),
				Arrays.copyOf(rotorSurfaceWetness, rotorSurfaceWetness.length),
				state.rotorIcingSeverity(),
				state.propwashWakeIntensity(),
				state.propwashIntensity(),
				state.vortexRingStateIntensity(),
				state.vortexRingThrustBuffetAmplitude(),
				state.maxVortexRingThrustBuffetAmplitude()
		);
	}

	public void restoreRotorDynamicState(RotorDynamicState dynamicState) {
		if (dynamicState == null) {
			return;
		}

		double[] motorOmega = dynamicState.motorOmegaRadiansPerSecond();
		double[] escOutput = dynamicState.escOutputCommand();
		double[] escElectricalOutput = dynamicState.escElectricalOutputCommand();
		double[] telemetryRpm = dynamicState.motorRpmTelemetryRpm();
		double[] telemetryValidity = dynamicState.motorRpmTelemetryValidity();
		double[] inducedVelocity = dynamicState.rotorInducedVelocityMetersPerSecond();
		double[] inducedLagScale = dynamicState.rotorInducedLagThrustScale();
		double[] wakeVelocity = dynamicState.rotorInducedWakeVelocityMetersPerSecond();
		double[] wakeCarryover = dynamicState.rotorInducedWakeCarryoverIntensity();
		double[] surfaceWetness = dynamicState.rotorSurfaceWetness();
		double[] icingSeverity = dynamicState.rotorIcingSeverity();
		int count = Math.min(state.motorCount(), config.rotors().size());
		for (int i = 0; i < count; i++) {
			RotorSpec rotor = config.rotors().get(i);
			if (hasFiniteValue(motorOmega, i)) {
				double omega = MathUtil.clamp(motorOmega[i], 0.0, rotor.maxOmegaRadiansPerSecond() * 1.08);
				state.setMotorOmegaRadiansPerSecond(i, omega);
				state.setMotorTargetOmegaRadiansPerSecond(i, omega);
				state.setMotorAngularAccelerationRadiansPerSecondSquared(i, 0.0);
			}
			if (hasFiniteValue(escOutput, i)) {
				state.setEscOutputCommand(i, escOutput[i]);
				heldEscOutputCommands[i] = state.escOutputCommand(i);
				escCommandFrameInitialized[i] = true;
				escCommandFrameClockSeconds[i] = 0.0;
				escCommandFrameAgeSeconds[i] = 0.0;
				escCommandErrors[i] = 0.0;
			}
			if (hasFiniteValue(escElectricalOutput, i)) {
				state.setEscElectricalOutputCommand(i, escElectricalOutput[i]);
				state.setEscElectricalOutputError(i, Math.abs(state.escOutputCommand(i) - state.escElectricalOutputCommand(i)));
			} else if (hasFiniteValue(escOutput, i)) {
				state.setEscElectricalOutputCommand(i, state.escOutputCommand(i));
				state.setEscElectricalOutputError(i, 0.0);
			}
			if (hasFiniteValue(telemetryRpm, i) || hasFiniteValue(telemetryValidity, i)) {
				double rpm = hasFiniteValue(telemetryRpm, i) ? Math.max(0.0, telemetryRpm[i]) : state.motorRpmTelemetryRpm(i);
				double validity = hasFiniteValue(telemetryValidity, i) ? telemetryValidity[i] : state.motorRpmTelemetryValidity(i);
				double telemetryOmega = rpm * (Math.PI * 2.0) / 60.0;
				state.setMotorRpmTelemetry(i, telemetryOmega, validity);
				escRpmTelemetryOmegaRadiansPerSecond[i] = telemetryOmega;
				escRpmTelemetryFrameInitialized[i] = state.motorRpmTelemetryValidity(i) > 0.0;
				escRpmTelemetryFrameClockSeconds[i] = 0.0;
				escRpmTelemetryFrameAgeSeconds[i] = 0.0;
			}
			if (hasFiniteValue(inducedVelocity, i)) {
				state.setRotorInducedVelocityMetersPerSecond(i, inducedVelocity[i]);
			}
			if (hasFiniteValue(inducedLagScale, i)) {
				state.setRotorInducedLagThrustScale(i, inducedLagScale[i]);
			}
			if (hasFiniteValue(wakeVelocity, i)) {
				double maxWakeVelocity = targetRotorInducedVelocityMetersPerSecond(rotor, rotor.maxThrustNewtons(), 1.0) * 1.65;
				rotorInducedWakeVelocityMetersPerSecond[i] = MathUtil.clamp(wakeVelocity[i], 0.0, Math.max(1.0, maxWakeVelocity));
			}
			if (hasFiniteValue(wakeCarryover, i)) {
				rotorInducedWakeCarryoverIntensity[i] = MathUtil.clamp(wakeCarryover[i], 0.0, 1.0);
			}
			if (hasFiniteValue(surfaceWetness, i)) {
				rotorSurfaceWetness[i] = MathUtil.clamp(surfaceWetness[i], 0.0, 1.0);
				state.setRotorWetThrustScale(i, precipitationThrustScale(rotorSurfaceWetness[i]));
			}
			if (hasFiniteValue(icingSeverity, i)) {
				double severity = MathUtil.clamp(icingSeverity[i], 0.0, 1.25);
				state.setRotorIcingSeverity(i, severity);
				state.setRotorIcingThrustScale(i, IcingRotorCalibration.icingThrustScale(severity));
				state.setRotorIcingPowerScale(i, IcingRotorCalibration.icingPowerScale(severity));
			}
		}
		if (Double.isFinite(dynamicState.propwashWakeIntensity())) {
			state.setPropwashWakeIntensity(dynamicState.propwashWakeIntensity());
		}
		if (Double.isFinite(dynamicState.propwashIntensity())) {
			state.setPropwashIntensity(dynamicState.propwashIntensity());
		}
		if (Double.isFinite(dynamicState.vortexRingStateIntensity())) {
			state.setVortexRingStateIntensity(dynamicState.vortexRingStateIntensity());
			Arrays.fill(rotorVortexRingStateIntensity, state.vortexRingStateIntensity());
		}
		if (Double.isFinite(dynamicState.vortexRingThrustBuffetAmplitude())) {
			state.setVortexRingThrustBuffetAmplitude(dynamicState.vortexRingThrustBuffetAmplitude());
		}
		if (Double.isFinite(dynamicState.vortexRingMaxThrustBuffetAmplitude())) {
			state.setVortexRingMaxThrustBuffetAmplitude(dynamicState.vortexRingMaxThrustBuffetAmplitude());
		}
		updateEscSignalTelemetry();
	}

	public void restoreDirectFlightTelemetry(DroneInput input, double[] motorPower, double[] motorRpm, double[] rotorThrustNewtons) {
		DroneInput normalized = input == null ? DroneInput.idle() : input.normalized();
		state.setRawControlInput(normalized);
		state.setProcessedControlInput(normalized);
		state.setControlLinkLossSeconds(normalized.linkActive() ? 0.0 : state.controlLinkLossSeconds());
		state.setControlFailsafeActive(!normalized.linkActive());
		state.setControlFrameTelemetry(0.0, receiverFrameIntervalSeconds(), 0.0);

		int count = Math.min(state.motorCount(), config.rotors().size());
		for (int i = 0; i < count; i++) {
			RotorSpec rotor = config.rotors().get(i);
			double power = normalized.armed() && hasFiniteValue(motorPower, i)
					? MathUtil.clamp(motorPower[i], 0.0, 1.0)
					: 0.0;
			double rpm = normalized.armed() && hasFiniteValue(motorRpm, i)
					? Math.max(0.0, motorRpm[i])
					: 0.0;
			double omega = MathUtil.clamp(rpm * (Math.PI * 2.0) / 60.0, 0.0, rotor.maxOmegaRadiansPerSecond() * 1.08);
			double thrust = normalized.armed() && hasFiniteValue(rotorThrustNewtons, i)
					? Math.max(0.0, rotorThrustNewtons[i])
					: 0.0;

			state.setMotorOmegaRadiansPerSecond(i, omega);
			state.setMotorTargetOmegaRadiansPerSecond(i, omega);
			state.setMotorAngularAccelerationRadiansPerSecondSquared(i, 0.0);
			state.setMotorTrackingError(i, 0.0);
			state.setMotorActuatorAuthority(i, normalized.armed() ? 1.0 : 0.0);
			state.setEscOutputCommand(i, power);
			state.setEscElectricalOutputCommand(i, power);
			state.setEscElectricalOutputError(i, 0.0);
			state.setMotorRpmTelemetry(i, omega, rpm >= ESC_RPM_TELEMETRY_MIN_VALID_MECHANICAL_RPM ? 1.0 : 0.0);
			state.setRotorThrustNewtons(i, thrust);
			state.setRotorForceBodyNewtons(i, new Vec3(0.0, thrust, 0.0));
			state.setRotorTorqueBodyNewtonMeters(i, Vec3.ZERO);
		}
		updateEscSignalTelemetry();
	}

	public void clearDirectFlightTelemetry(DroneInput input) {
		DroneInput normalized = input == null ? DroneInput.idle() : input.normalized();
		state.setRawControlInput(normalized);
		state.setProcessedControlInput(normalized);
		state.setControlLinkLossSeconds(normalized.linkActive() ? 0.0 : state.controlLinkLossSeconds());
		state.setControlFailsafeActive(!normalized.linkActive());
		state.setControlFrameTelemetry(0.0, receiverFrameIntervalSeconds(), 0.0);
		state.resetMotors();
		resetEscSignalModel();
		resetEscRpmTelemetryModel();
		updateEscSignalTelemetry();
	}

	public AerodynamicTransientState aerodynamicTransientStateSnapshot() {
		return new AerodynamicTransientState(
				meanWindVelocityWorldMetersPerSecond,
				windBurbleVelocityWorldMetersPerSecond,
				drydenFirstOrderVelocityWorldMetersPerSecond,
				drydenTransverseLagVelocityWorldMetersPerSecond,
				drydenTurbulenceVelocityWorldMetersPerSecond,
				a4mcSourceGustVelocityWorldMetersPerSecond,
				a4mcUpdraftVelocityWorldMetersPerSecond,
				a4mcTerrainShearVelocityWorldMetersPerSecond,
				windGustVelocityWorldMetersPerSecond,
				drydenRandomState,
				drydenSpareGaussian,
				hasDrydenSpareGaussian,
				windModelInitialized,
				windGustPhaseA,
				windGustPhaseB,
				windGustPhaseC,
				turbulencePhaseA,
				turbulencePhaseB,
				turbulencePhaseC,
				airframeSeparatedFlowIntensity,
				airframeSeparationBuffetPhaseA,
				airframeSeparationBuffetPhaseB,
				rotorWashDragForceBodyFiltered,
				rotorWashAirframeAngularDampingFiltered,
				dynamicPressureCenterOffsetBodyFiltered,
				airframeLiftForceBodyFiltered,
				airframeDragForceBodyFiltered,
				groundEffectDragForceBodyFiltered,
				groundEffectLevelingTorqueBodyFiltered
		);
	}

	public void restoreAerodynamicTransientState(AerodynamicTransientState transientState) {
		if (transientState == null) {
			return;
		}

		meanWindVelocityWorldMetersPerSecond = transientState.meanWindVelocityWorldMetersPerSecond().clamp(-80.0, 80.0);
		windBurbleVelocityWorldMetersPerSecond = transientState.windBurbleVelocityWorldMetersPerSecond().clamp(-40.0, 40.0);
		drydenFirstOrderVelocityWorldMetersPerSecond = transientState.drydenFirstOrderVelocityWorldMetersPerSecond().clamp(-40.0, 40.0);
		drydenTransverseLagVelocityWorldMetersPerSecond = transientState.drydenTransverseLagVelocityWorldMetersPerSecond().clamp(-40.0, 40.0);
		drydenTurbulenceVelocityWorldMetersPerSecond = transientState.drydenTurbulenceVelocityWorldMetersPerSecond().clamp(-40.0, 40.0);
		a4mcSourceGustVelocityWorldMetersPerSecond = transientState.a4mcSourceGustVelocityWorldMetersPerSecond().clamp(-12.0, 12.0);
		a4mcUpdraftVelocityWorldMetersPerSecond = transientState.a4mcUpdraftVelocityWorldMetersPerSecond().clamp(-12.0, 12.0);
		a4mcTerrainShearVelocityWorldMetersPerSecond = transientState.a4mcTerrainShearVelocityWorldMetersPerSecond().clamp(-12.0, 12.0);
		windGustVelocityWorldMetersPerSecond = transientState.windGustVelocityWorldMetersPerSecond().clamp(-60.0, 60.0);
		drydenRandomState = transientState.drydenRandomState();
		boolean hasFiniteSpareGaussian = transientState.hasDrydenSpareGaussian()
				&& Double.isFinite(transientState.drydenSpareGaussian());
		drydenSpareGaussian = hasFiniteSpareGaussian
				? MathUtil.clamp(transientState.drydenSpareGaussian(), -10.0, 10.0)
				: 0.0;
		hasDrydenSpareGaussian = hasFiniteSpareGaussian;
		windModelInitialized = transientState.windModelInitialized();
		windGustPhaseA = finitePhase(transientState.windGustPhaseA());
		windGustPhaseB = finitePhase(transientState.windGustPhaseB());
		windGustPhaseC = finitePhase(transientState.windGustPhaseC());
		turbulencePhaseA = finitePhase(transientState.turbulencePhaseA());
		turbulencePhaseB = finitePhase(transientState.turbulencePhaseB());
		turbulencePhaseC = finitePhase(transientState.turbulencePhaseC());
		airframeSeparatedFlowIntensity = MathUtil.clamp(
				finiteOr(transientState.airframeSeparatedFlowIntensity(), 0.0),
				0.0,
				1.0
		);
		airframeSeparationBuffetPhaseA = finitePhase(transientState.airframeSeparationBuffetPhaseA());
		airframeSeparationBuffetPhaseB = finitePhase(transientState.airframeSeparationBuffetPhaseB());
		rotorWashDragForceBodyFiltered = transientState.rotorWashDragForceBody().clamp(-12.0, 12.0);
		rotorWashAirframeAngularDampingFiltered = transientState.rotorWashAirframeAngularDamping().clamp(-0.25, 0.25);
		dynamicPressureCenterOffsetBodyFiltered = transientState.dynamicPressureCenterOffsetBody().clamp(-0.040, 0.040);
		airframeLiftForceBodyFiltered = transientState.airframeLiftForceBody().clamp(-18.0, 18.0);
		airframeDragForceBodyFiltered = transientState.airframeDragForceBody().clamp(-48.0, 48.0);
		groundEffectDragForceBodyFiltered = transientState.groundEffectDragForceBody().clamp(-14.0, 14.0);
		groundEffectLevelingTorqueBodyFiltered = transientState.groundEffectLevelingTorqueBody().clamp(-0.70, 0.70);

		state.setEffectiveWindVelocityWorldMetersPerSecond(
				meanWindVelocityWorldMetersPerSecond.add(windGustVelocityWorldMetersPerSecond)
		);
		state.setWindGustVelocityWorldMetersPerSecond(windGustVelocityWorldMetersPerSecond);
		state.setDrydenTurbulenceVelocityWorldMetersPerSecond(drydenTurbulenceVelocityWorldMetersPerSecond);
		state.setWindBurbleVelocityWorldMetersPerSecond(windBurbleVelocityWorldMetersPerSecond);
		state.setA4mcSourceGustVelocityWorldMetersPerSecond(a4mcSourceGustVelocityWorldMetersPerSecond);
		state.setA4mcUpdraftVelocityWorldMetersPerSecond(a4mcUpdraftVelocityWorldMetersPerSecond);
		state.setA4mcTerrainShearVelocityWorldMetersPerSecond(a4mcTerrainShearVelocityWorldMetersPerSecond);
		state.setAirframeSeparatedFlowIntensity(airframeSeparatedFlowIntensity);
		state.setRotorWashDragForceBodyNewtons(rotorWashDragForceBodyFiltered);
		state.setAirframeLiftForceBodyNewtons(airframeLiftForceBodyFiltered);
		state.setAirframeBodyDragForceBodyNewtons(airframeDragForceBodyFiltered);
		state.setGroundEffectDragForceBodyNewtons(groundEffectDragForceBodyFiltered);
		state.setGroundEffectLevelingTorqueBodyNewtonMeters(groundEffectLevelingTorqueBodyFiltered);
	}

	private static boolean hasFiniteValue(double[] values, int index) {
		return values != null
				&& index >= 0
				&& index < values.length
				&& Double.isFinite(values[index]);
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	private static double finiteOr(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}

	private static double finitePhase(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	public void resetControlLoops() {
		pitchPid.reset();
		yawPid.reset();
		rollPid.reset();
		Arrays.fill(targetRotorThrusts, 0.0);
		Arrays.fill(escDesyncPhases, 0.0);
		Arrays.fill(motorCommutationPhases, 0.0);
		Arrays.fill(rotorBladePassPhases, 0.0);
		Arrays.fill(rotorImbalancePhases, 0.0);
		Arrays.fill(rotorVortexBuffetPhases, 0.0);
		Arrays.fill(rotorBladeStallBuffetPhases, 0.0);
		Arrays.fill(rotorBladeElementStallIntensity, 0.0);
		Arrays.fill(rotorBladeElementThrustScale, 1.0);
		Arrays.fill(rotorBladeElementLoadFactor, 0.0);
		Arrays.fill(rotorBladeElementVibration, 0.0);
		Arrays.fill(rotorBladeDissymmetryIntensity, 0.0);
		Arrays.fill(rotorBladeDissymmetryThrustScale, 1.0);
		Arrays.fill(rotorBladeDissymmetryLoadFactor, 0.0);
		Arrays.fill(rotorBladeDissymmetryVibration, 0.0);
		Arrays.fill(rotorBladeDissymmetryReverseFlowInboardFraction, 0.0);
		Arrays.fill(rotorDynamicStallIntensity, 0.0);
		Arrays.fill(rotorInducedWakeVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorInducedWakeCarryoverIntensity, 0.0);
		Arrays.fill(rotorVortexRingStateIntensity, 0.0);
		Arrays.fill(rotorWakeInterferenceIntensity, 0.0);
		Arrays.fill(rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(rotorSurfaceEffectThrustMultipliers, 1.0);
		Arrays.fill(rotorConingIntensity, 0.0);
		Arrays.fill(rotorConingVelocity, 0.0);
		Arrays.fill(heldEscOutputCommands, 0.0);
		Arrays.fill(escCommandFrameClockSeconds, 0.0);
		Arrays.fill(escCommandFrameAgeSeconds, 0.0);
		Arrays.fill(escCommandErrors, 0.0);
		Arrays.fill(escCommandFrameInitialized, false);
		resetEscRpmTelemetryModel();
		Arrays.fill(rotorArmFlexIntensity, 0.0);
		Arrays.fill(rotorArmFlexVelocity, 0.0);
		Arrays.fill(rotorFlappingTiltBody, Vec3.ZERO);
		Arrays.fill(previousRotorForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(previousRotorTorqueBodyNewtonMeters, Vec3.ZERO);
		state.resetMotors();
		state.setEscCommandTelemetry(0.0, escCommandFrameIntervalSeconds(), 0.0);
		resetGyroModel();
		resetAccelerometerModel();
		resetAttitudeEstimator();
		state.setPropwashIntensity(0.0);
		state.setPropwashTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setAirframeLiftForceBodyNewtons(Vec3.ZERO);
		state.setGroundEffectDragForceBodyNewtons(Vec3.ZERO);
		state.setGroundEffectLevelingTorqueBodyNewtonMeters(Vec3.ZERO);
		rotorWashDragForceBodyFiltered = Vec3.ZERO;
		rotorWashAirframeAngularDampingFiltered = Vec3.ZERO;
		groundEffectDragForceBodyFiltered = Vec3.ZERO;
		groundEffectLevelingTorqueBodyFiltered = Vec3.ZERO;
		state.setRotorWashDragForceBodyNewtons(Vec3.ZERO);
		state.setAirframeAerodynamicTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setAirframeAngularDragTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setRotorFlappingTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setRotorInertiaTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setRotorAccelerationReactionTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setRotorGyroscopicTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setRotorAngularDragTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setMixerSaturation(0.0);
		state.setPidAttenuation(1.0);
		state.setAntiGravityBoost(0.0);
		state.setPidDTermLowPassCutoffHertz(0.0);
		state.setTargetRatesBodyRadiansPerSecond(Vec3.ZERO);
		state.setRateErrorBodyRadiansPerSecond(Vec3.ZERO);
		state.setLevelTargetAttitudeRadians(Vec3.ZERO);
		state.setLevelAttitudeErrorRadians(Vec3.ZERO);
		state.setSelfLevelBlend(0.0);
		state.setPidProportionalTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setPidIntegralTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setPidDerivativeTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setPidFeedForwardTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setPidOutputTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setPidIntegralRelax(0.0);
		resetSensorBiasModel();
		previousTargetRatesRadiansPerSecond = Vec3.ZERO;
		previousPidGyroRatesBodyRadiansPerSecond = Vec3.ZERO;
		feedForwardTorqueBody = Vec3.ZERO;
		hasPreviousTargetRates = false;
		hasPreviousPidGyroRates = false;
		previousThrottle = 0.0;
		antiGravityTransient = 0.0;
	}

	public void step(DroneInput rawInput, double dtSeconds) {
		step(rawInput, dtSeconds, DroneEnvironment.calm());
	}

	public void step(DroneInput rawInput, double dtSeconds, DroneEnvironment environment) {
		if (dtSeconds <= 0.0) {
			return;
		}

		if (environment == null) {
			environment = DroneEnvironment.calm();
		}

		DroneInput input = updateControlInput(rawInput, dtSeconds);
		updateSensorBias(dtSeconds);
		updateGyroMeasurement(dtSeconds);
		Vec3 torqueCommandBody = calculateRateControllerTorque(input, dtSeconds);
		mixRotorThrusts(input, torqueCommandBody);

		Vec3 totalForceBody = Vec3.ZERO;
		Vec3 totalTorqueBody = Vec3.ZERO;
		double voltageScale = MathUtil.clamp(state.batteryVoltage() / config.nominalBatteryVoltage(), 0.55, 1.03);
		double airDensity = environment.effectiveAirDensityRatio();
		double ambientHumidity = environment.ambientHumidity();
		double waterImmersion = environment.waterImmersionIntensity();
		double precipitationWetness = environment.precipitationWetnessIntensity();
		Vec3 effectiveWindVelocityWorld = updateAirMassWind(environment, dtSeconds);
		double ambientDirtyAir = dirtyAirIntensity(environment);
		Vec3 relativeAirVelocityBody = state.orientation()
				.conjugate()
				.rotate(state.velocityMetersPerSecond().subtract(effectiveWindVelocityWorld));
		updateAerodynamicTelemetry(relativeAirVelocityBody);
		updateAirframeSeparatedFlowIntensity(relativeAirVelocityBody, dtSeconds);
		Vec3 airframeDragBody = updateAirframeBodyDragForce(relativeAirVelocityBody, airDensity, dtSeconds);
		Vec3 angularVelocityBody = state.angularVelocityBodyRadiansPerSecond();
		RotorWakeInterference rotorWakeInterference = updateRotorWakeInterference(input.armed(), relativeAirVelocityBody, environment, dtSeconds);
		double vortexRingStateSum = 0.0;
		double vortexRingThrustBuffetAmplitudeSum = 0.0;
		double vortexRingMaxThrustBuffetAmplitude = 0.0;
		double rotorVibrationSum = 0.0;
		double rotorInflowSkewSum = 0.0;
		Vec3 rotorInflowSkewTorqueSum = Vec3.ZERO;
		Vec3 rotorBladeDissymmetryTorqueSum = Vec3.ZERO;
		Vec3 rotorWakeSwirlTorqueSum = Vec3.ZERO;
		Vec3 rotorFlappingTorqueSum = Vec3.ZERO;
		Vec3 rotorActiveBrakingTorqueSum = Vec3.ZERO;
		Vec3 rotorInertiaTorqueSum = Vec3.ZERO;
		Vec3 rotorAccelerationReactionTorqueSum = Vec3.ZERO;
		Vec3 rotorGyroscopicTorqueSum = Vec3.ZERO;
		Vec3 rotorAngularDragTorqueSum = Vec3.ZERO;
		Vec3 rotorWallEffectForceSum = Vec3.ZERO;
		Vec3 vortexRingBuffetForceSum = Vec3.ZERO;

		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double rotorWaterImmersion = environment.rotorWaterImmersion(i);
			double rotorPrecipitationWetness = environment.rotorPrecipitationWetness(i);
			double rotorWetnessForPreviousLoad = Math.max(rotorPrecipitationWetness, rotorSurfaceWetness[i]);
			double rotorWaterLoad = rotorWaterLoadFactor(rotorWaterImmersion);
			double rotorPrecipitationLoad = rotorPrecipitationLoadFactor(rotorWetnessForPreviousLoad);
			double previousIcingSeverity = state.rotorIcingSeverity(i);
			double rotorIcingLoad = IcingRotorCalibration.icingAerodynamicLoadFactor(previousIcingSeverity);
			double escCommandOutput;
			double escElectricalOutput;
			if (input.armed()) {
				escCommandOutput = updateEscOutputCommand(i, rotor, voltageScale, dtSeconds);
				escElectricalOutput = updateEscElectricalOutput(i, escCommandOutput, dtSeconds);
			} else {
				resetEscSignalOutput(i);
				resetEscElectricalOutput(i);
				escCommandOutput = 0.0;
				escElectricalOutput = 0.0;
			}
			state.setEscOutputCommand(i, escCommandOutput);
			state.setEscElectricalOutputCommand(i, escElectricalOutput);
			state.setEscElectricalOutputError(i, Math.abs(escCommandOutput - escElectricalOutput));
			updateMotorWindingResistanceScale(i);
			double surfaceScrape = state.rotorSurfaceScrapeIntensity(i);
			double previousPropellerPowerLoadFactor = motorPropellerDynamicLoadFactor(i, state.rotorAerodynamicLoadFactor(i));
			double previousTargetLoadFactor = previousPropellerPowerLoadFactor
					+ 0.70 * surfaceScrape
					+ rotorWaterLoad
					+ rotorPrecipitationLoad
					+ rotorIcingLoad;
			double previousResponseLoadFactor = previousPropellerPowerLoadFactor
					+ 0.85 * surfaceScrape
					+ rotorWaterLoad
					+ rotorPrecipitationLoad
					+ rotorIcingLoad;
			double powerLimitScale = Math.sqrt(state.batteryPowerLimit() * state.motorThermalLimit() * state.escThermalLimit() * state.rotorHealth(i));
			double targetOmega = input.armed()
					? rotor.maxOmegaRadiansPerSecond()
							* rotor.targetMaxOmegaScale()
							* escElectricalOutput
							* voltageScale
							* powerLimitScale
							* motorWindingTorqueTargetScale(i, escElectricalOutput)
							* motorBearingDragTargetScale(i, escElectricalOutput)
							* motorLoadTargetScale(previousTargetLoadFactor, escElectricalOutput)
							* rotorIcingTargetScale(previousIcingSeverity)
							* rotorSurfaceScrapeTargetScale(surfaceScrape)
					: 0.0;
			state.setMotorTargetOmegaRadiansPerSecond(i, targetOmega);
			double previousOmega = state.motorOmegaRadiansPerSecond(i);
			updateEscRpmTelemetry(i, rotor, input.armed() ? previousOmega : 0.0, dtSeconds);
			double motorAlpha = MathUtil.expSmoothing(dtSeconds, motorResponseTimeConstantSeconds(
					i,
					rotor,
					previousOmega,
					targetOmega,
					escElectricalOutput,
					voltageScale,
					powerLimitScale,
					previousResponseLoadFactor
			));
			double commandedOmega = previousOmega + (targetOmega - previousOmega) * motorAlpha;
			commandedOmega = electricallyLimitedMotorOmega(
					i,
					rotor,
					previousOmega,
					commandedOmega,
					escElectricalOutput,
					powerLimitScale,
					previousResponseLoadFactor,
					surfaceScrape,
					dtSeconds
			);
			commandedOmega = blackboxLimitedActiveBrakingOmega(
					rotor,
					previousOmega,
					targetOmega,
					commandedOmega,
					escElectricalOutput,
					voltageScale,
					dtSeconds
			);
			double mechanicalLossTorque = motorMechanicalLossTorque(
					rotor,
					commandedOmega,
					airDensity,
					state.motorTemperatureCelsius(i),
					rotorWaterImmersion,
					rotorWetnessForPreviousLoad,
					surfaceScrape,
					state.rotorHealth(i)
			) * IcingRotorCalibration.icingMechanicalLossTorqueScale(previousIcingSeverity);
			commandedOmega = applyMotorMechanicalLoss(rotor, commandedOmega, mechanicalLossTorque, dtSeconds);
			state.setMotorMechanicalLossTorqueNewtonMeters(i, mechanicalLossTorque);

			double wakeInterference = rotorWakeInterference.intensity(i);
			state.setRotorWakeInterferenceIntensity(i, wakeInterference);
			Vec3 wakeSwirlVelocityBody = rotorWakeInterference.swirlVelocityBodyMetersPerSecond(i);
			double wakeSwirlSpeed = wakeSwirlVelocityBody.length();
			state.setRotorWakeSwirlVelocityMetersPerSecond(i, wakeSwirlSpeed);
			Vec3 nominalRotorArmBody = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
			double previousRotorArmFlex = rotorArmFlexIntensity[i];
			Vec3 rotorArmBody = rotorArmBodyWithFlex(rotor, nominalRotorArmBody, previousRotorArmFlex);
			RotorSpec aerodynamicRotor = rotorWithArmFlexedThrustAxis(rotor, nominalRotorArmBody, previousRotorArmFlex);
			Vec3 rotorLocalWindDeltaBody = rotorLocalWindDeltaBody(environment, i);
			Vec3 rotorDiskWindGradientBody = rotorEffectiveDiskWindGradientBody(environment, i);
			Vec3 rotorRelativeAirVelocityBody = relativeAirVelocityBody
					.subtract(rotorLocalWindDeltaBody)
					.add(angularVelocityBody.cross(rotorArmBody))
					.add(rotorWakeInterference.downwashVelocityBodyMetersPerSecond(i))
					.add(wakeSwirlVelocityBody);
			double windmillingIntensity = rotorWindmillingIntensity(aerodynamicRotor, rotorRelativeAirVelocityBody, escElectricalOutput);
			state.setRotorWindmillingIntensity(i, windmillingIntensity);
			commandedOmega = applyRotorWindmilling(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					commandedOmega,
					escElectricalOutput,
					dtSeconds
			);
			double commandedAerodynamicOmega = rotorAerodynamicOmegaRadiansPerSecond(aerodynamicRotor, commandedOmega, angularVelocityBody);
			double advanceRatio = rotorAdvanceRatio(aerodynamicRotor, rotorRelativeAirVelocityBody, commandedAerodynamicOmega);
			state.setRotorAdvanceRatio(i, advanceRatio);
			state.setRotorPropellerAdvanceRatioJ(i, rotorPropellerAdvanceRatioJ(advanceRatio));
			state.setRotorPropellerThrustScale(i, rotorForwardAdvanceThrustScale(aerodynamicRotor, advanceRatio));
			state.setRotorPropellerPowerScale(i, rotorForwardAdvancePowerScale(aerodynamicRotor, advanceRatio));
			double kinematicRotorStall = rotorBladeStallIntensity(aerodynamicRotor, rotorRelativeAirVelocityBody, commandedAerodynamicOmega);
			double rotorStall = kinematicRotorStall;
			double desyncIntensity = updateEscDesyncIntensity(
					i,
					rotor,
					environment,
					rotorStall,
					previousOmega,
					targetOmega,
					escElectricalOutput,
					voltageScale,
					surfaceScrape,
					dtSeconds
			);
			double desyncPulse = escDesyncPulse(i, commandedOmega, desyncIntensity, dtSeconds);
			double driveVoltage = motorDriveVoltage(escElectricalOutput, powerLimitScale);
			double commandedVoltageHeadroom = motorVoltageHeadroomFromDriveVoltage(rotor, commandedOmega, driveVoltage);
			MotorCommutationRipple commutationRipple = updateMotorCommutationRipple(
					i,
					rotor,
					commandedOmega,
					escElectricalOutput,
					commandedVoltageHeadroom,
					previousResponseLoadFactor,
					desyncIntensity,
					surfaceScrape,
					state.rotorHealth(i),
					dtSeconds
			);
			double omega = commandedOmega * (1.0 - 0.26 * desyncPulse) * (1.0 - 0.18 * surfaceScrape)
					+ commutationRipple.deltaOmegaRadiansPerSecond();
			omega = MathUtil.clamp(omega, 0.0, rotor.maxOmegaRadiansPerSecond() * 1.08);
			state.setMotorCommutationRippleIntensity(i, commutationRipple.intensity());
			state.setMotorTorqueRippleNewtonMeters(i, commutationRipple.torqueRippleNewtonMeters());
			double motorAngularAcceleration = dtSeconds <= 0.0 ? 0.0 : (omega - previousOmega) / dtSeconds;
			state.setMotorAngularAccelerationRadiansPerSecondSquared(i, motorAngularAcceleration);
			state.setMotorOmegaRadiansPerSecond(i, omega);
			double voltageHeadroom = motorVoltageHeadroomFromDriveVoltage(
					rotor,
					omega,
					driveVoltage
			);
			state.setMotorVoltageHeadroom(i, voltageHeadroom);
			double motorTrackingError = motorTrackingError(rotor, targetOmega, omega);
			state.setMotorTrackingError(i, motorTrackingError);
			state.setMotorActuatorAuthority(i, motorActuatorAuthority(
					motorTrackingError,
					voltageHeadroom,
					powerLimitScale,
					desyncIntensity,
					surfaceScrape
			));
			double aerodynamicOmega = rotorAerodynamicOmegaRadiansPerSecond(aerodynamicRotor, omega, angularVelocityBody);
			double aerodynamicAdvanceRatio = rotorAdvanceRatio(aerodynamicRotor, rotorRelativeAirVelocityBody, aerodynamicOmega);
			state.setRotorAdvanceRatio(i, aerodynamicAdvanceRatio);
			state.setRotorPropellerAdvanceRatioJ(i, rotorPropellerAdvanceRatioJ(aerodynamicAdvanceRatio));
			double propellerThrustScale = rotorForwardAdvanceThrustScale(aerodynamicRotor, aerodynamicAdvanceRatio);
			state.setRotorPropellerThrustScale(i, propellerThrustScale);
			double propellerPowerScale = rotorForwardAdvancePowerScale(aerodynamicRotor, aerodynamicAdvanceRatio);
			state.setRotorPropellerPowerScale(i, propellerPowerScale);
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample ctCpJReferenceSample =
					sampleRotorCtCpJReference(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					airDensity,
					config.centerOfMassOffsetBodyMeters()
			);
			state.setRotorCtCpJReferenceSample(i, ctCpJReferenceSample);
			state.setRotorAxialGustThrustScale(i, rotorAxialGustThrustScale(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega
			));
			double rotorTipMach = rotorTipMach(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					environment.effectiveAmbientTemperatureCelsius()
			);
			state.setRotorTipMach(i, rotorTipMach);
			double compressibilityThrustScale = rotorCompressibilityThrustScale(rotorTipMach);
			state.setRotorCompressibilityThrustScale(i, compressibilityThrustScale);
			double compressibilityLoad = rotorCompressibilityLoadFactor(rotorTipMach);
			double compressibilityReactionTorqueScale = rotorCompressibilityReactionTorqueScale(rotorTipMach);
			state.setRotorReynoldsNumber(i, rotorReynoldsNumber(
					aerodynamicRotor,
					aerodynamicOmega,
					airDensity,
					environment.effectiveAmbientTemperatureCelsius(),
					ambientHumidity
			));
			state.setRotorReynoldsIndex(i, rotorLowReynoldsIndex(
					aerodynamicRotor,
					aerodynamicOmega,
					airDensity,
					environment.effectiveAmbientTemperatureCelsius(),
					ambientHumidity
			));
			double lowReynoldsLoss = rotorLowReynoldsLoss(
					aerodynamicRotor,
					aerodynamicOmega,
					airDensity,
					environment.effectiveAmbientTemperatureCelsius(),
					ambientHumidity
			);
			state.setRotorLowReynoldsLoss(i, lowReynoldsLoss);
			double surfaceEffectThrustMultiplier = updateRotorSurfaceEffectThrustMultiplier(
					i,
					aerodynamicRotor,
					environment.rotorThrustMultiplier(i, config),
					aerodynamicOmega,
					dtSeconds
			);
			double wakeThrustScale = rotorWakeInterferenceThrustScale(wakeInterference);
			state.setRotorWakeThrustScale(i, wakeThrustScale);
			double rotorFilmWetness = updateRotorSurfaceWetness(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					rotorWaterImmersion,
					rotorPrecipitationWetness,
					dtSeconds
			);
			double wetThrustScale = waterImmersionThrustScale(rotorWaterImmersion)
					* precipitationThrustScale(rotorFilmWetness);
			state.setRotorWetThrustScale(i, wetThrustScale);
			double icingSeverity = updateRotorIcingSeverity(
					i,
					aerodynamicRotor,
					aerodynamicOmega,
					Math.max(
							Math.max(rotorPrecipitationWetness, rotorFilmWetness),
							frozenHumidityIcingWetness(environment)
					),
					environment.effectiveAmbientTemperatureCelsius(),
					dtSeconds
			);
			double icingThrustScale = IcingRotorCalibration.icingThrustScale(icingSeverity);
			double icingPowerScale = IcingRotorCalibration.icingPowerScale(icingSeverity);
			state.setRotorIcingThrustScale(i, icingThrustScale);
			state.setRotorIcingPowerScale(i, icingPowerScale);
			double thrustScale = airDensity
					* surfaceEffectThrustMultiplier
					* wakeThrustScale
					* wetThrustScale
					* icingThrustScale
					* rotorHealthThrustScale(state.rotorHealth(i));
			double fallbackBaseThrust = rotor.thrustCoefficient() * aerodynamicOmega * aerodynamicOmega * thrustScale;
			double baseThrust = rotorCtCpJRuntimeBaseThrustNewtons(
					ctCpJReferenceSample,
					fallbackBaseThrust,
					thrustScale,
					airDensity
			);
			double inflowLagScale = updateRotorInducedInflow(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					baseThrust,
					ctCpJReferenceSample,
					airDensity,
					dtSeconds
			);
			double rotorAirflowScale = rotorAirflowThrustMultiplier(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					state.rotorTranslationalLiftIntensity(i)
			);
			double diskWindGradientThrustScale = rotorDiskWindGradientThrustScale(
					aerodynamicRotor,
					rotorDiskWindGradientBody,
					aerodynamicOmega
			);
			double diskWindGradientLoadFactor = rotorDiskWindGradientLoadFactor(
					aerodynamicRotor,
					rotorDiskWindGradientBody,
					aerodynamicOmega
			);
			double diskWindGradientVibration = rotorDiskWindGradientVibration(
					aerodynamicRotor,
					rotorDiskWindGradientBody,
					aerodynamicOmega
			);
			double diskWindGradientStallIntensity = rotorDiskWindGradientStallIntensity(
					aerodynamicRotor,
					rotorDiskWindGradientBody,
					aerodynamicOmega
			);
			state.setRotorDiskWindGradientThrustLossFraction(i, 1.0 - diskWindGradientThrustScale);
			state.setRotorDiskWindGradientLoadFactor(i, diskWindGradientLoadFactor);
			state.setRotorDiskWindGradientVibration(i, diskWindGradientVibration);
			state.setRotorDiskWindGradientStallIntensity(i, diskWindGradientStallIntensity);
			BladeElementAerodynamics bladeElement = updateRotorBladeElementAerodynamics(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					state.rotorInducedVelocityMetersPerSecond(i),
					dtSeconds
			);
			BladeDissymmetryAerodynamics bladeDissymmetry = updateRotorBladeDissymmetryAerodynamics(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					baseThrust,
					dtSeconds
			);
			rotorStall = updateRotorDynamicStallIntensity(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					kinematicRotorStall,
					bladeElement.stallIntensity(),
					bladeDissymmetry.intensity(),
					diskWindGradientStallIntensity,
					dtSeconds
			);
			state.setRotorBladeAngleOfAttackRadians(i, bladeElement.angleOfAttackRadians());
			state.setRotorBladeElementStallIntensity(i, bladeElement.stallIntensity());
			state.setRotorBladeDissymmetryIntensity(i, bladeDissymmetry.intensity());
			state.setRotorReverseFlowInboardFraction(i, bladeDissymmetry.reverseFlowInboardFraction());
			state.setRotorStallIntensity(i, rotorStall);
			double damageVibration = rotorDamageVibration(rotor, omega, state.rotorHealth(i));
			state.setRotorDamageVibration(i, damageVibration);
			rotorVibrationSum += damageVibration
					+ rotorStallVibration(rotor, aerodynamicOmega, rotorStall)
					+ bladeElement.vibration()
					+ bladeDissymmetry.vibration()
					+ rotorFlowObstructionVibration(rotor, aerodynamicOmega, environment.rotorFlowObstruction(i))
					+ rotorSurfaceScrapeVibration(rotor, omega, surfaceScrape)
					+ rotorWakeInterferenceVibration(rotor, aerodynamicOmega, wakeInterference)
					+ rotorWakeSwirlVibration(rotor, aerodynamicOmega, wakeSwirlSpeed)
					+ rotorWaterIngestionVibration(rotor, aerodynamicOmega, rotorWaterImmersion)
					+ rotorPrecipitationVibration(rotor, aerodynamicOmega, Math.max(rotorPrecipitationWetness, rotorFilmWetness))
					+ rotorIcingVibration(rotor, aerodynamicOmega, icingSeverity)
					+ rotorCompressibilityVibration(rotor, aerodynamicOmega, rotorTipMach)
					+ rotorImbalanceVibration(rotor, omega, state.rotorHealth(i))
					+ rotorWindmillingVibration(aerodynamicRotor, rotorRelativeAirVelocityBody, aerodynamicOmega, escElectricalOutput)
					+ motorCommutationRippleVibration(rotor, omega, commutationRipple.intensity(), commutationRipple.torqueRippleNewtonMeters());
			double vortexRingState = updateRotorVortexRingStateIntensity(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					state.rotorInducedVelocityMetersPerSecond(i),
					dtSeconds
			);
			double vortexRingDescentRatio = rotorVortexRingDescentRatio(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					state.rotorInducedVelocityMetersPerSecond(i)
			);
			vortexRingStateSum += vortexRingState;
			double aerodynamicLoadFactor = MathUtil.clamp(rotorAerodynamicLoadFactor(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					state.rotorInducedVelocityMetersPerSecond(i),
					state.rotorTranslationalLiftIntensity(i),
					rotorStall,
					vortexRingState,
					environment.rotorFlowObstruction(i),
					surfaceScrape
			)
					+ rotorAngularDragLoadFactor(aerodynamicRotor, angularVelocityBody, aerodynamicOmega)
					+ 0.28 * wakeInterference
					+ rotorWakeSwirlLoadFactor(rotor, aerodynamicOmega, wakeSwirlSpeed)
					+ rotorWindmillingLoadFactor(aerodynamicRotor, rotorRelativeAirVelocityBody, aerodynamicOmega, escElectricalOutput)
					+ rotorAmbientDirtyAirLoadFactor(aerodynamicRotor, aerodynamicOmega, ambientDirtyAir)
					+ rotorInducedWakeLoadFactor(rotorInducedWakeCarryoverIntensity[i])
					+ rotorInPlaneDragLoadFactor(
							aerodynamicRotor,
							rotorRelativeAirVelocityBody,
							aerodynamicOmega,
							state.rotorTranslationalLiftIntensity(i),
							bladeDissymmetry.intensity(),
							rotorStall
					)
					+ compressibilityLoad
					+ rotorLowReynoldsLoadFactor(lowReynoldsLoss, aerodynamicOmega, aerodynamicRotor)
					+ diskWindGradientLoadFactor
					+ bladeElement.loadFactor()
					+ bladeDissymmetry.loadFactor()
					+ rotorWaterLoad
					+ rotorPrecipitationLoadFactor(Math.max(rotorPrecipitationWetness, rotorFilmWetness))
					+ IcingRotorCalibration.icingAerodynamicLoadFactor(icingSeverity), 0.0, 2.0);
			double coningIntensity = updateRotorConingIntensity(i, aerodynamicRotor, baseThrust, aerodynamicOmega, dtSeconds);
			aerodynamicLoadFactor = MathUtil.clamp(aerodynamicLoadFactor + rotorConingLoadFactor(coningIntensity), 0.0, 2.0);
			state.setRotorAerodynamicLoadFactor(i, aerodynamicLoadFactor);
			rotorVibrationSum += rotorLowReynoldsVibration(lowReynoldsLoss, aerodynamicOmega, aerodynamicRotor);
			rotorVibrationSum += rotorConingVibration(aerodynamicRotor, aerodynamicOmega, coningIntensity);
			rotorVibrationSum += rotorInducedWakeVibration(aerodynamicRotor, aerodynamicOmega, rotorInducedWakeCarryoverIntensity[i]);
			state.setRotorConingIntensity(i, coningIntensity);
			state.setRotorConingAngleRadians(i, rotorConingAngleRadians(aerodynamicRotor, coningIntensity));
			double vortexRingThrustScale = 1.0 - rotorVortexRingMeanThrustLoss(rotor, vortexRingState);
			double stallThrustScale = 1.0 - rotor.stallThrustLossCoefficient() * rotorStall;
			double lowReynoldsThrustScale = rotorLowReynoldsThrustScale(lowReynoldsLoss);
			double coningThrustScale = rotorConingThrustScale(coningIntensity);
			double nominalThrust = baseThrust
					* rotorAirflowScale
					* inflowLagScale
					* bladeElement.thrustScale()
					* bladeDissymmetry.thrustScale()
					* diskWindGradientThrustScale
					* lowReynoldsThrustScale
					* coningThrustScale
					* compressibilityThrustScale
					* MathUtil.clamp(vortexRingThrustScale, 0.45, 1.0)
					* MathUtil.clamp(stallThrustScale, 0.35, 1.0);
			RotorBladePassRipple bladePassRipple = updateRotorBladePassRipple(
					i,
					aerodynamicRotor,
					omega,
					nominalThrust,
					aerodynamicLoadFactor,
					rotorStall,
					bladeElement,
					bladeDissymmetry,
					ambientDirtyAir,
					wakeInterference,
					environment.rotorFlowObstruction(i),
					surfaceScrape,
					dtSeconds
			);
			RotorBladeStallBuffet stallBuffet = updateRotorBladeStallBuffet(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					nominalThrust * bladePassRipple.thrustScale(),
					rotorStall,
					bladeElement,
					bladeDissymmetry,
					dtSeconds
			);
			RotorVortexRingBuffet vortexBuffet = updateRotorVortexRingBuffet(
					i,
					aerodynamicRotor,
					aerodynamicOmega,
					nominalThrust * bladePassRipple.thrustScale() * stallBuffet.thrustScale(),
					vortexRingState,
					vortexRingDescentRatio,
					dtSeconds
			);
			vortexRingThrustBuffetAmplitudeSum += vortexBuffet.thrustAmplitude();
			vortexRingMaxThrustBuffetAmplitude = Math.max(vortexRingMaxThrustBuffetAmplitude, vortexBuffet.thrustAmplitude());
			vortexRingBuffetForceSum = vortexRingBuffetForceSum.add(vortexBuffet.forceBody());
			double thrust = nominalThrust * bladePassRipple.thrustScale() * stallBuffet.thrustScale() * vortexBuffet.thrustScale();
			state.setRotorThrustNewtons(i, thrust);
			state.setRotorBladePassRippleIntensity(i, bladePassRipple.intensity());
			rotorVibrationSum += bladePassRipple.vibration()
					+ stallBuffet.vibration()
					+ vortexBuffet.vibration()
					+ diskWindGradientVibration;
			Vec3 forceBody = rotorCtCpJRuntimeThrustAxisForceBody(
					ctCpJReferenceSample,
					aerodynamicRotor,
					thrust
			);
			Vec3 flappingForceBody = updateRotorFlappingForce(i, aerodynamicRotor, rotorRelativeAirVelocityBody, rotorDiskWindGradientBody, aerodynamicOmega, thrust, dtSeconds);
			Vec3 flappingTorque = rotorArmBody.cross(flappingForceBody);
			rotorFlappingTorqueSum = rotorFlappingTorqueSum.add(flappingTorque);
			Vec3 imbalanceForceBody = updateRotorImbalanceForce(i, aerodynamicRotor, state.rotorHealth(i), omega, thrust, dtSeconds);
			state.setRotorFlappingForceNewtons(i, Math.hypot(flappingForceBody.x(), flappingForceBody.z()));
			Vec3 thrustAxisForceBody = forceBody.add(flappingForceBody);
			Vec3 rotorDiskAxisBody = rotorDiskAxisBody(thrustAxisForceBody);
			Vec3 diskDragBody = rotorDiskDragForce(aerodynamicRotor, rotorRelativeAirVelocityBody, aerodynamicOmega, airDensity);
			Vec3 inPlaneDragBody = rotorInPlaneDragForce(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					thrust,
					airDensity,
					state.rotorTranslationalLiftIntensity(i),
					bladeDissymmetry.intensity(),
					rotorStall
			);
			state.setRotorInPlaneDragForceNewtons(i, inPlaneDragBody.length());
			Vec3 windmillingDragBody = rotorWindmillingDragForce(aerodynamicRotor, rotorRelativeAirVelocityBody, aerodynamicOmega, escElectricalOutput, airDensity);
			Vec3 wallEffectForceBody = updateRotorWallEffectForce(
					i,
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					thrust,
					environment.rotorFlowObstruction(i),
					environment.rotorFlowObstructionDirectionBody(i),
					environment.rotorFlowObstructionWallForceFactor(i),
					dtSeconds
			);
			rotorWallEffectForceSum = rotorWallEffectForceSum.add(wallEffectForceBody);
			forceBody = thrustAxisForceBody.add(stallBuffet.forceBody()).add(vortexBuffet.forceBody()).add(imbalanceForceBody).add(diskDragBody).add(inPlaneDragBody).add(windmillingDragBody).add(wallEffectForceBody);
			state.setRotorForceBodyNewtons(i, forceBody);
			Vec3 torqueFromArm = rotorArmBody.cross(forceBody);
			double reactionTorqueScale = rotorReactionTorqueScale(aerodynamicLoadFactor, rotorStall, vortexRingState);
			double propellerTorquePerThrustScale = rotorForwardAdvanceTorquePerThrustScale(aerodynamicRotor, aerodynamicAdvanceRatio);
			double inPlaneDragShaftTorque = rotorInPlaneDragShaftTorque(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					inPlaneDragBody,
					aerodynamicOmega
			);
			double mechanicalPowerScale = coaxialAllocationMechanicalPowerScale(i);
			double inPlaneDragMotorShaftTorque = inPlaneDragShaftTorque * mechanicalPowerScale * icingPowerScale;
			state.setRotorInPlaneDragShaftTorqueNewtonMeters(i, inPlaneDragMotorShaftTorque);
			state.setRotorInPlaneDragShaftPowerWatts(i,
					inPlaneDragMotorShaftTorque * Math.max(0.0, omega));
			double fallbackRawMotorAerodynamicTorque = rotor.yawTorquePerThrustMeter()
					* thrust
					* reactionTorqueScale
					* compressibilityReactionTorqueScale
					* propellerTorquePerThrustScale
					+ inPlaneDragShaftTorque;
			double rawMotorAerodynamicTorque = rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
					ctCpJReferenceSample,
					fallbackRawMotorAerodynamicTorque,
					reactionTorqueScale,
					compressibilityReactionTorqueScale,
					inPlaneDragShaftTorque,
					thrustScale,
					airDensity
			);
			double motorAerodynamicTorque = rawMotorAerodynamicTorque * mechanicalPowerScale * icingPowerScale;
			state.setMotorAerodynamicTorqueNewtonMeters(i, motorAerodynamicTorque);
			state.setMotorShaftPowerWatts(
					i,
					motorAerodynamicTorque * Math.max(0.0, omega)
							+ mechanicalLossTorque * Math.max(0.0, omega)
							+ motorPositiveInertiaPowerWatts(rotor, motorAngularAcceleration, omega)
			);
			state.updateRotorCtCpJReferenceResidual(i);
			Vec3 reactionTorque = rotorCtCpJRuntimeReactionTorqueBody(
					ctCpJReferenceSample,
					aerodynamicRotor,
					rotorDiskAxisBody,
					motorAerodynamicTorque,
					commutationRipple.torqueRippleNewtonMeters()
			);
			RotorInertiaTorques inertiaTorques = rotorInertiaTorques(rotor, previousOmega, omega, angularVelocityBody, rotorDiskAxisBody, dtSeconds);
			Vec3 inertiaTorque = inertiaTorques.totalTorque();
			Vec3 activeBrakingTorque = rotorActiveBrakingTorque(rotor, previousOmega, omega, escElectricalOutput, rotorDiskAxisBody, dtSeconds);
			rotorActiveBrakingTorqueSum = rotorActiveBrakingTorqueSum.add(activeBrakingTorque);
			rotorInertiaTorqueSum = rotorInertiaTorqueSum.add(inertiaTorque);
			rotorAccelerationReactionTorqueSum = rotorAccelerationReactionTorqueSum.add(inertiaTorques.accelerationReactionTorque());
			rotorGyroscopicTorqueSum = rotorGyroscopicTorqueSum.add(inertiaTorques.gyroscopicReactionTorque());
			Vec3 angularDragTorque = rotorAngularDragTorque(
					aerodynamicRotor,
					angularVelocityBody,
					rotorDiskAxisBody,
					aerodynamicOmega,
					thrust,
					airDensity,
					aerodynamicLoadFactor,
					rotorStall,
					wakeInterference
			);
			rotorAngularDragTorqueSum = rotorAngularDragTorqueSum.add(angularDragTorque);
			double inflowSkewIntensity = rotorInflowSkewIntensity(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					aerodynamicOmega,
					state.rotorTranslationalLiftIntensity(i),
					rotorStall
			);
			Vec3 inflowSkewTorque = rotorInflowSkewTorque(aerodynamicRotor, rotorRelativeAirVelocityBody, thrust, inflowSkewIntensity);
			rotorInflowSkewSum += inflowSkewIntensity;
			rotorInflowSkewTorqueSum = rotorInflowSkewTorqueSum.add(inflowSkewTorque);
			Vec3 bladeDissymmetryTorque = rotorBladeDissymmetryTorque(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					thrust,
					bladeDissymmetry
			);
			rotorBladeDissymmetryTorqueSum = rotorBladeDissymmetryTorqueSum.add(bladeDissymmetryTorque);
			Vec3 wakeSwirlTorque = rotorWakeSwirlTorque(
					aerodynamicRotor,
					wakeSwirlVelocityBody,
					thrust,
					aerodynamicOmega,
					wakeInterference
			);
			rotorWakeSwirlTorqueSum = rotorWakeSwirlTorqueSum.add(wakeSwirlTorque);

			Vec3 rotorTorqueBody = torqueFromArm
					.add(reactionTorque)
					.add(inertiaTorque)
					.add(angularDragTorque)
					.add(inflowSkewTorque)
					.add(bladeDissymmetryTorque)
					.add(wakeSwirlTorque);
			state.setRotorTorqueBodyNewtonMeters(i, rotorTorqueBody);
			double rotorArmFlex = updateRotorArmFlexIntensity(i, rotor, forceBody, rotorTorqueBody, omega, dtSeconds);
			state.setRotorArmFlexIntensity(i, rotorArmFlex);
			state.setRotorArmFlexDeflectionMeters(i, rotorArmFlexVerticalDeflectionMeters(rotor, nominalRotorArmBody, rotorArmFlex));
			state.setRotorArmFlexTiltRadians(i, rotorArmFlexTiltRadians(rotor, nominalRotorArmBody, rotorArmFlex));
			rotorVibrationSum += rotorArmFlexVibration(rotor, omega, rotorArmFlex);
			totalForceBody = totalForceBody.add(forceBody);
			totalTorqueBody = totalTorqueBody.add(rotorTorqueBody);
			state.setRotorSurfaceScrapeIntensity(i, surfaceScrapeDecay(surfaceScrape, dtSeconds));
		}
		state.setVortexRingStateIntensity(vortexRingStateSum / config.rotors().size());
		state.setVortexRingThrustBuffetAmplitude(vortexRingThrustBuffetAmplitudeSum / config.rotors().size());
		state.setVortexRingMaxThrustBuffetAmplitude(vortexRingMaxThrustBuffetAmplitude);
		state.setVortexRingBuffetForceBodyNewtons(vortexRingBuffetForceSum);
		state.setRotorVibration(rotorVibrationSum / config.rotors().size());
		state.setRotorInflowSkewIntensity(rotorInflowSkewSum / config.rotors().size());
		state.setRotorInflowSkewTorqueBodyNewtonMeters(rotorInflowSkewTorqueSum);
		state.setRotorBladeDissymmetryTorqueBodyNewtonMeters(rotorBladeDissymmetryTorqueSum);
		state.setRotorWakeSwirlTorqueBodyNewtonMeters(rotorWakeSwirlTorqueSum);
		state.setRotorFlappingTorqueBodyNewtonMeters(rotorFlappingTorqueSum);
		state.setRotorActiveBrakingTorqueBodyNewtonMeters(rotorActiveBrakingTorqueSum);
		state.setRotorInertiaTorqueBodyNewtonMeters(rotorInertiaTorqueSum);
		state.setRotorAccelerationReactionTorqueBodyNewtonMeters(rotorAccelerationReactionTorqueSum);
		state.setRotorGyroscopicTorqueBodyNewtonMeters(rotorGyroscopicTorqueSum);
		state.setRotorAngularDragTorqueBodyNewtonMeters(rotorAngularDragTorqueSum);
		state.setRotorWallEffectForceBodyNewtons(rotorWallEffectForceSum);
		updateEscSignalTelemetry();

		Vec3 airframeLiftBody = updateAirframeLiftForce(totalForceBody, relativeAirVelocityBody, airDensity, dtSeconds);
		state.setAirframeLiftForceBodyNewtons(airframeLiftBody);
		Vec3 rotorWashDragBody = updateRotorWashDragForce(totalForceBody, relativeAirVelocityBody, airDensity, dtSeconds);
		state.setRotorWashDragForceBodyNewtons(rotorWashDragBody);
		Vec3 airframeTorqueBody = calculateAirframeAerodynamicTorque(
				relativeAirVelocityBody,
				rotorWashDragBody,
				airframeLiftBody,
				airframeDragBody,
				environment,
				airDensity,
				dtSeconds
		);
		state.setAirframeAerodynamicTorqueBodyNewtonMeters(airframeTorqueBody);
		Vec3 turbulenceTorqueBody = calculateWindTurbulenceTorque(environment, relativeAirVelocityBody, dtSeconds);
		state.setWindTurbulenceTorqueBodyNewtonMeters(turbulenceTorqueBody);
		Vec3 groundEffectLevelingTorqueBody = updateGroundEffectLevelingTorque(
				totalForceBody,
				relativeAirVelocityBody,
				environment,
				dtSeconds
		);
		state.setGroundEffectLevelingTorqueBodyNewtonMeters(groundEffectLevelingTorqueBody);
		totalTorqueBody = totalTorqueBody
				.add(airframeTorqueBody)
				.add(calculatePropwashTorque(input, relativeAirVelocityBody, angularVelocityBody, environment, dtSeconds))
				.add(turbulenceTorqueBody)
				.add(groundEffectLevelingTorqueBody);
		integrateLinear(totalForceBody, rotorWashDragBody, airframeLiftBody, airframeDragBody, environment, effectiveWindVelocityWorld, dtSeconds);
		updateBarometerMeasurement(environment, dtSeconds);
		updateAccelerometerMeasurement(dtSeconds);
		integrateAngular(totalTorqueBody, totalForceBody, relativeAirVelocityBody, airDensity, dtSeconds);
		updateAttitudeEstimator(dtSeconds);
		integrateBattery(environment, dtSeconds);
		integrateMotorThermal(environment, dtSeconds);
		integrateEscThermal(environment, dtSeconds);
	}

	private DroneInput updateControlInput(DroneInput rawInput, double dtSeconds) {
		DroneInput raw = rawInput == null ? DroneInput.idle() : rawInput.normalized();
		state.setRawControlInput(raw);

		if (raw.linkActive()) {
			controlLinkLossSeconds = 0.0;
			lastLinkedControlInput = updateReceiverFrame(raw, dtSeconds);
		} else {
			controlLinkLossSeconds = Math.min(
					config.rcFailsafeTimeoutSeconds() + dtSeconds,
					controlLinkLossSeconds + dtSeconds
			);
			updateReceiverFrameTelemetry(raw, lastLinkedControlInput, dtSeconds);
		}

		boolean failsafe = !raw.linkActive() && controlLinkLossSeconds >= config.rcFailsafeTimeoutSeconds();
		DroneInput processed;
		if (failsafe) {
			processed = DroneInput.failsafe();
			smoothedControlInput = processed;
			Arrays.fill(controlDelayBuffer, processed);
			controlDelayWriteIndex = 0;
		} else {
			DroneInput command = raw.linkActive() ? lastLinkedControlInput : withLinkState(lastLinkedControlInput, false);
			processed = smoothControlInput(updateControlDelay(command, dtSeconds), dtSeconds);
		}

		state.setControlLinkLossSeconds(raw.linkActive() ? 0.0 : controlLinkLossSeconds);
		state.setControlFailsafeActive(failsafe);
		state.setProcessedControlInput(processed);
		return processed;
	}

	private DroneInput updateReceiverFrame(DroneInput raw, double dtSeconds) {
		DroneInput normalizedRaw = raw == null ? DroneInput.idle() : raw.normalized();
		double intervalSeconds = receiverFrameIntervalSeconds();
		DroneInput quantizedFrame = quantizeReceiverCommand(normalizedRaw);
		if (intervalSeconds <= 1.0e-9) {
			receiverFrameInput = quantizedFrame;
			receiverFrameClockSeconds = 0.0;
			receiverFrameAgeSeconds = 0.0;
			state.setControlFrameTelemetry(0.0, 0.0, controlFrameError(normalizedRaw, receiverFrameInput));
			return receiverFrameInput;
		}

		receiverFrameClockSeconds += Math.max(0.0, dtSeconds);
		boolean firstLinkedFrame = !receiverFrameInput.linkActive();
		if (firstLinkedFrame || receiverFrameClockSeconds >= intervalSeconds) {
			receiverFrameInput = quantizedFrame;
			receiverFrameClockSeconds = firstLinkedFrame ? 0.0 : receiverFrameClockSeconds - intervalSeconds;
			if (receiverFrameClockSeconds >= intervalSeconds) {
				receiverFrameClockSeconds = 0.0;
			}
			receiverFrameAgeSeconds = 0.0;
		} else {
			receiverFrameAgeSeconds = Math.min(intervalSeconds, receiverFrameAgeSeconds + Math.max(0.0, dtSeconds));
		}
		state.setControlFrameTelemetry(receiverFrameAgeSeconds, intervalSeconds, controlFrameError(normalizedRaw, receiverFrameInput));
		return receiverFrameInput;
	}

	private void updateReceiverFrameTelemetry(DroneInput raw, DroneInput heldCommand, double dtSeconds) {
		double intervalSeconds = receiverFrameIntervalSeconds();
		if (intervalSeconds <= 1.0e-9) {
			state.setControlFrameTelemetry(0.0, 0.0, 0.0);
			return;
		}
		receiverFrameAgeSeconds = Math.min(
				config.rcFailsafeTimeoutSeconds() + Math.max(0.0, dtSeconds),
				receiverFrameAgeSeconds + Math.max(0.0, dtSeconds)
		);
		state.setControlFrameTelemetry(receiverFrameAgeSeconds, intervalSeconds, controlFrameError(raw, heldCommand));
	}

	private double receiverFrameIntervalSeconds() {
		return config.rcFrameRateHertz() <= 1.0e-9 ? 0.0 : 1.0 / config.rcFrameRateHertz();
	}

	private DroneInput quantizeReceiverCommand(DroneInput input) {
		DroneInput normalized = input == null ? DroneInput.idle() : input.normalized();
		int resolutionSteps = config.rcChannelResolutionSteps();
		if (resolutionSteps < 2) {
			return normalized;
		}
		return new DroneInput(
				quantizeUnitChannel(normalized.throttle(), resolutionSteps),
				quantizeSignedChannel(normalized.pitch(), resolutionSteps),
				quantizeSignedChannel(normalized.roll(), resolutionSteps),
				quantizeSignedChannel(normalized.yaw(), resolutionSteps),
				normalized.armed(),
				normalized.linkActive(),
				normalized.flightMode()
		).normalized();
	}

	private static double quantizeUnitChannel(double value, int resolutionSteps) {
		double scale = Math.max(1.0, resolutionSteps - 1.0);
		return Math.round(MathUtil.clamp(value, 0.0, 1.0) * scale) / scale;
	}

	private static double quantizeSignedChannel(double value, int resolutionSteps) {
		double scale = Math.max(1.0, Math.floor((resolutionSteps - 1.0) * 0.5));
		return MathUtil.clamp(Math.round(MathUtil.clamp(value, -1.0, 1.0) * scale) / scale, -1.0, 1.0);
	}

	private static double controlFrameError(DroneInput raw, DroneInput frame) {
		DroneInput normalizedRaw = raw == null ? DroneInput.idle() : raw.normalized();
		DroneInput normalizedFrame = frame == null ? DroneInput.idle() : frame.normalized();
		return Math.max(
				Math.max(Math.abs(normalizedRaw.throttle() - normalizedFrame.throttle()), Math.abs(normalizedRaw.pitch() - normalizedFrame.pitch())),
				Math.max(Math.abs(normalizedRaw.roll() - normalizedFrame.roll()), Math.abs(normalizedRaw.yaw() - normalizedFrame.yaw()))
		);
	}

	private DroneInput updateControlDelay(DroneInput command, double dtSeconds) {
		DroneInput normalizedCommand = command == null ? DroneInput.idle() : command.normalized();
		controlDelayBuffer[controlDelayWriteIndex] = normalizedCommand;
		int delaySamples = Math.min(
				CONTROL_DELAY_BUFFER_SIZE - 1,
				(int) Math.round(config.rcCommandLatencySeconds() / Math.max(dtSeconds, 1.0e-6))
		);
		int readIndex = controlDelayWriteIndex - delaySamples;
		if (readIndex < 0) {
			readIndex += CONTROL_DELAY_BUFFER_SIZE;
		}
		DroneInput delayed = controlDelayBuffer[readIndex];
		controlDelayWriteIndex = (controlDelayWriteIndex + 1) % CONTROL_DELAY_BUFFER_SIZE;
		return delayed == null ? DroneInput.idle() : delayed;
	}

	private DroneInput smoothControlInput(DroneInput targetInput, double dtSeconds) {
		DroneInput target = targetInput == null ? DroneInput.idle() : targetInput.normalized();
		if (!target.armed()) {
			smoothedControlInput = new DroneInput(0.0, 0.0, 0.0, 0.0, false, target.linkActive(), target.flightMode());
			return smoothedControlInput;
		}

		double alpha = MathUtil.expSmoothing(dtSeconds, config.rcCommandSmoothingTimeConstantSeconds());
		DroneInput current = smoothedControlInput.normalized();
		smoothedControlInput = new DroneInput(
				current.throttle() + (target.throttle() - current.throttle()) * alpha,
				current.pitch() + (target.pitch() - current.pitch()) * alpha,
				current.roll() + (target.roll() - current.roll()) * alpha,
				current.yaw() + (target.yaw() - current.yaw()) * alpha,
				target.armed(),
				target.linkActive(),
				target.flightMode()
		).normalized();
		return smoothedControlInput;
	}

	private static DroneInput withLinkState(DroneInput input, boolean linkActive) {
		DroneInput normalized = input == null ? DroneInput.idle() : input.normalized();
		return new DroneInput(
				normalized.throttle(),
				normalized.pitch(),
				normalized.roll(),
				normalized.yaw(),
				normalized.armed(),
				linkActive,
				normalized.flightMode()
		);
	}

	private double updateEscOutputCommand(int index, RotorSpec rotor, double voltageScale, double dtSeconds) {
		double desiredThrustFraction = MathUtil.clamp(targetRotorThrusts[index] / rotor.maxThrustNewtons(), 0.0, 1.0);
		double desiredMotorOutput = Math.sqrt(desiredThrustFraction);
		double compensatedOutput = applyVoltageCompensation(desiredMotorOutput, voltageScale);
		double deadbandedOutput = applyEscDeadband(compensatedOutput);
		double curvedOutput = Math.pow(deadbandedOutput, config.escOutputCurveExponent());
		double previousOutput = state.escOutputCommand(index);
		double slewRate = curvedOutput >= previousOutput
				? config.escOutputSlewRatePerSecond()
				: config.escOutputFallSlewRatePerSecond();
		double maxDelta = slewRate * dtSeconds;
		double continuousOutput = MathUtil.clamp(curvedOutput, previousOutput - maxDelta, previousOutput + maxDelta);
		return updateEscSignalOutput(index, continuousOutput, dtSeconds);
	}

	private double updateEscSignalOutput(int index, double continuousOutput, double dtSeconds) {
		double quantizedOutput = quantizeEscCommand(continuousOutput);
		double intervalSeconds = escCommandFrameIntervalSeconds();
		if (intervalSeconds <= 1.0e-9) {
			heldEscOutputCommands[index] = quantizedOutput;
			escCommandFrameClockSeconds[index] = 0.0;
			escCommandFrameAgeSeconds[index] = 0.0;
			escCommandErrors[index] = Math.abs(continuousOutput - heldEscOutputCommands[index]);
			escCommandFrameInitialized[index] = true;
			return heldEscOutputCommands[index];
		}

		boolean firstFrame = !escCommandFrameInitialized[index];
		if (firstFrame) {
			heldEscOutputCommands[index] = quantizedOutput;
			escCommandFrameInitialized[index] = true;
			escCommandFrameClockSeconds[index] = -escCommandFramePhaseOffsetSeconds(index, intervalSeconds);
			escCommandFrameAgeSeconds[index] = 0.0;
			escCommandErrors[index] = Math.abs(continuousOutput - heldEscOutputCommands[index]);
			return heldEscOutputCommands[index];
		}

		escCommandFrameClockSeconds[index] += Math.max(0.0, dtSeconds);
		if (escCommandFrameClockSeconds[index] >= intervalSeconds) {
			heldEscOutputCommands[index] = quantizedOutput;
			escCommandFrameClockSeconds[index] -= intervalSeconds;
			if (escCommandFrameClockSeconds[index] >= intervalSeconds) {
				escCommandFrameClockSeconds[index] = 0.0;
			}
			escCommandFrameAgeSeconds[index] = 0.0;
		} else {
			escCommandFrameAgeSeconds[index] = Math.min(intervalSeconds, escCommandFrameAgeSeconds[index] + Math.max(0.0, dtSeconds));
		}
		escCommandErrors[index] = Math.abs(continuousOutput - heldEscOutputCommands[index]);
		return heldEscOutputCommands[index];
	}

	private double updateEscElectricalOutput(int index, double commandOutput, double dtSeconds) {
		double previousElectricalOutput = state.escElectricalOutputCommand(index);
		if (dtSeconds <= 0.0) {
			return previousElectricalOutput;
		}

		double timeConstantSeconds = escElectricalOutputTimeConstantSeconds(index, commandOutput, previousElectricalOutput);
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstantSeconds);
		return MathUtil.clamp(previousElectricalOutput + (commandOutput - previousElectricalOutput) * alpha, 0.0, 1.0);
	}

	private double escElectricalOutputTimeConstantSeconds(int index, double commandOutput, double previousElectricalOutput) {
		double intervalSeconds = escCommandFrameIntervalSeconds();
		double schedulingSeconds = intervalSeconds > 1.0e-9 ? intervalSeconds : 0.001;
		double rawFrameSeconds = config.escCommandProtocol().rawFrameSeconds();
		double protocolBaseSeconds = config.escCommandProtocol().digital()
				? 0.00055 + 0.10 * schedulingSeconds + 1.50 * rawFrameSeconds
				: intervalSeconds > 1.0e-9 ? 0.00140 + 0.18 * schedulingSeconds : 0.00058;
		double outputDelta = commandOutput - previousElectricalOutput;
		double activeBraking = MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0);
		double brakingStretch = outputDelta < 0.0
				? 1.0 - 0.42 * activeBraking * smoothStep(0.03, 0.45, -outputDelta)
				: 1.0;
		double perMotorMaxCurrentAmps = config.maxBatteryCurrentAmps() / Math.max(1, state.motorCount());
		double currentRippleStress = perMotorMaxCurrentAmps <= 1.0e-6
				? 0.0
				: smoothStep(0.04, 0.35, state.motorCurrentRippleAmps(index) / perMotorMaxCurrentAmps);
		double voltageHeadroomStress = motorVoltageHeadroomStress(index)
				* smoothStep(0.35, 0.92, previousElectricalOutput);
		double stressStretch = 1.0
				+ 0.62 * state.escDesyncIntensity(index)
				+ 0.30 * voltageHeadroomStress
				+ 0.22 * currentRippleStress;
		return MathUtil.clamp(protocolBaseSeconds * MathUtil.clamp(brakingStretch, 0.52, 1.0) * stressStretch, 0.00045, 0.0085);
	}

	private void resetEscSignalOutput(int index) {
		heldEscOutputCommands[index] = 0.0;
		escCommandFrameClockSeconds[index] = 0.0;
		escCommandFrameAgeSeconds[index] = 0.0;
		escCommandErrors[index] = 0.0;
		escCommandFrameInitialized[index] = false;
	}

	private void resetEscElectricalOutput(int index) {
		state.setEscElectricalOutputCommand(index, 0.0);
		state.setEscElectricalOutputError(index, 0.0);
	}

	private double quantizeEscCommand(double command) {
		int resolutionSteps = config.escCommandResolutionSteps();
		if (resolutionSteps < 2) {
			return MathUtil.clamp(command, 0.0, 1.0);
		}
		double scale = Math.max(1.0, resolutionSteps - 1.0);
		return Math.round(MathUtil.clamp(command, 0.0, 1.0) * scale) / scale;
	}

	private static double quantizeBidirectionalDshotRpmTelemetry(RotorSpec rotor, double omegaRadiansPerSecond) {
		if (rotor == null || omegaRadiansPerSecond <= 0.0 || !Double.isFinite(omegaRadiansPerSecond)) {
			return 0.0;
		}

		double mechanicalRpm = omegaRadiansPerSecond * 60.0 / (Math.PI * 2.0);
		double polePairs = motorPolePairs(rotor);
		double electricalRpmDiv100 = mechanicalRpm * polePairs / 100.0;
		double reportedElectricalRpmDiv100 = Math.round(Math.max(0.0, electricalRpmDiv100));
		double reportedMechanicalRpm = reportedElectricalRpmDiv100 * 100.0 / polePairs;
		return reportedMechanicalRpm * (Math.PI * 2.0) / 60.0;
	}

	private static double escRpmTelemetryValidity(double omegaRadiansPerSecond) {
		if (!Double.isFinite(omegaRadiansPerSecond) || omegaRadiansPerSecond <= 0.0) {
			return 0.0;
		}

		double mechanicalRpm = omegaRadiansPerSecond * 60.0 / (Math.PI * 2.0);
		return smoothStep(
				ESC_RPM_TELEMETRY_MIN_VALID_MECHANICAL_RPM,
				ESC_RPM_TELEMETRY_FULL_VALID_MECHANICAL_RPM,
				mechanicalRpm
		);
	}

	private double escCommandFrameIntervalSeconds() {
		return config.escCommandFrameRateHertz() <= 1.0e-9 ? 0.0 : 1.0 / config.escCommandFrameRateHertz();
	}

	private double escCommandFramePhaseOffsetSeconds(int index, double intervalSeconds) {
		int motorCount = heldEscOutputCommands.length;
		if (motorCount <= 1 || intervalSeconds <= 1.0e-9) {
			return 0.0;
		}
		double phase = Math.floorMod(index, motorCount) / (double) motorCount;
		return intervalSeconds * 0.45 * phase;
	}

	private void updateEscSignalTelemetry() {
		double maxAge = 0.0;
		double maxError = 0.0;
		for (int i = 0; i < heldEscOutputCommands.length; i++) {
			maxAge = Math.max(maxAge, escCommandFrameAgeSeconds[i]);
			maxError = Math.max(maxError, escCommandErrors[i]);
		}
		state.setEscCommandTelemetry(maxAge, escCommandFrameIntervalSeconds(), maxError);
	}

	private double applyVoltageCompensation(double desiredMotorOutput, double voltageScale) {
		double safeVoltageScale = MathUtil.clamp(voltageScale, 0.35, 1.1);
		double compensation = 1.0 + config.voltageCompensationStrength() * (1.0 / safeVoltageScale - 1.0);
		return MathUtil.clamp(desiredMotorOutput * compensation, 0.0, 1.0);
	}

	private double applyEscDeadband(double motorOutput) {
		double deadband = config.escDeadband();
		if (motorOutput <= deadband) {
			return 0.0;
		}
		return MathUtil.clamp(motorOutput, 0.0, 1.0);
	}

	private double motorResponseTimeConstantSeconds(
			int index,
			RotorSpec rotor,
			double previousOmega,
			double targetOmega,
			double escOutput,
			double voltageScale,
			double powerLimitScale,
			double previousAerodynamicLoadFactor
	) {
		double baseTimeConstant = config.motorTimeConstantSeconds();
		if (targetOmega >= previousOmega) {
			double normalizedRotorInertia = rotor.rotorInertiaKgMetersSquared() / 1.6e-5;
			double inertiaFactor = MathUtil.clamp(Math.sqrt(Math.max(0.0, normalizedRotorInertia)), 0.75, 2.80);
			double loadFactor = previousAerodynamicLoadFactor <= 1.0e-6
					? 1.0
					: MathUtil.clamp(previousAerodynamicLoadFactor, 0.35, 1.85);
			double loadDragFactor = 0.90 + 0.20 * loadFactor;
			double voltageAuthority = MathUtil.clamp(voltageScale, 0.50, 1.08);
			double powerAuthority = MathUtil.clamp(powerLimitScale, 0.35, 1.0);
			double escAuthority = MathUtil.clamp(0.72 + 0.28 * escOutput, 0.72, 1.0);
			double voltageHeadroom = motorVoltageHeadroom(rotor, previousOmega, voltageScale);
			double backEmfSaturation = 1.0 - smoothStep(0.08, 0.36, voltageHeadroom);
			double backEmfFactor = 1.0 + 1.10 * backEmfSaturation * smoothStep(0.55, 0.92, escOutput);
			double windingHeatFactor = MathUtil.clamp(Math.sqrt(motorWindingResistanceTemperatureScale(index)), 0.85, 1.35);
			double authorityFactor = 1.0 / MathUtil.clamp(voltageAuthority * powerAuthority * escAuthority, 0.30, 1.08);
			return MathUtil.clamp(
					baseTimeConstant * inertiaFactor * loadDragFactor * authorityFactor * backEmfFactor * windingHeatFactor,
					baseTimeConstant * 0.55,
					baseTimeConstant * 5.5
			);
		}

		double brakeFactor = 1.0 + 4.0 * config.motorActiveBrakingStrength() * MathUtil.clamp(voltageScale, 0.35, 1.0);
		double coastInertiaFactor = MathUtil.clamp(Math.sqrt(Math.max(0.0, rotor.rotorInertiaKgMetersSquared() / 1.6e-5)), 0.75, 2.8);
		return MathUtil.clamp(
				baseTimeConstant * coastInertiaFactor / brakeFactor,
				0.005,
				baseTimeConstant * coastInertiaFactor
		);
	}

	private double blackboxLimitedActiveBrakingOmega(
			RotorSpec rotor,
			double previousOmega,
			double targetOmega,
			double commandedOmega,
			double escOutput,
			double voltageScale,
			double dtSeconds
	) {
		if (dtSeconds <= 1.0e-6
				|| targetOmega >= previousOmega
				|| commandedOmega >= previousOmega
				|| config.motorActiveBrakingStrength() <= 1.0e-6) {
			return commandedOmega;
		}
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		if (maxOmega <= 1.0e-6 || config.motorTimeConstantSeconds() <= 1.0e-6) {
			return commandedOmega;
		}

		double rpmFraction = MathUtil.clamp(previousOmega / maxOmega, 0.0, 1.0);
		double overrun = MathUtil.clamp(rpmFraction - escOutput, 0.0, 1.0);
		double brakeAuthority = MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0);
		double voltageAuthority = MathUtil.clamp((MathUtil.clamp(voltageScale, 0.35, 1.05) - 0.35) / 0.70, 0.0, 1.0);
		double overrunAuthority = smoothStep(0.04, 0.35, overrun);
		double brakingSlewMultiplier = MathUtil.clamp(
				0.92 + 0.14 * brakeAuthority + 0.08 * voltageAuthority + 0.08 * overrunAuthority,
				0.80,
				1.16
		);
		brakingSlewMultiplier = Math.min(
				brakingSlewMultiplier,
				MotorResponseCalibration.activeBrakingRuntimeSlewScaleOverSpinupProxy()
		);
		double referenceSlewRadiansPerSecondSquared = maxOmega / Math.max(0.005, config.motorTimeConstantSeconds());
		double limitedOmega = previousOmega - referenceSlewRadiansPerSecondSquared * brakingSlewMultiplier * dtSeconds;
		return Math.max(commandedOmega, limitedOmega);
	}

	private double updateEscDesyncIntensity(
			int index,
			RotorSpec rotor,
			DroneEnvironment environment,
			double rotorStall,
			double previousOmega,
			double targetOmega,
			double escOutput,
			double voltageScale,
			double surfaceScrapeIntensity,
			double dtSeconds
	) {
		double flowObstruction = environment.rotorFlowObstruction(index);
		double waterStress = smoothStep(0.06, 0.55, environment.rotorWaterImmersion(index));
		double precipitationStress = smoothStep(0.10, 0.78, environment.precipitationWetnessIntensity());
		double accelerationDemand = Math.max(0.0, targetOmega - previousOmega) / Math.max(1.0, rotor.maxOmegaRadiansPerSecond());
		double accelerationStress = smoothStep(0.060, 0.22, accelerationDemand);
		double voltageStress = 1.0 - smoothStep(0.62, 0.92, voltageScale);
		double voltageHeadroom = motorVoltageHeadroom(rotor, previousOmega, voltageScale);
		double voltageHeadroomStress = (1.0 - smoothStep(0.08, 0.36, voltageHeadroom))
				* smoothStep(0.55, 0.92, escOutput);
		double railRippleStress = batteryBusRippleStress() * smoothStep(0.45, 0.90, escOutput);
		double spinRatio = MathUtil.clamp(previousOmega / Math.max(1.0, rotor.maxOmegaRadiansPerSecond()), 0.0, 1.0);
		double railSpikeStress = batteryBusSpikeStress()
				* smoothStep(0.10, 0.65, spinRatio)
				* MathUtil.clamp(0.45 + 0.55 * config.motorActiveBrakingStrength(), 0.0, 1.0);
		double thermalStress = Math.max(1.0 - state.motorThermalLimit(), 1.0 - state.escThermalLimit(index));
		double outputStress = smoothStep(0.48, 0.90, escOutput);
		double risk = 0.78 * flowObstruction
				+ 0.72 * waterStress
				+ 0.11 * precipitationStress
				+ 0.58 * rotorStall
				+ 0.90 * MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0)
				+ 0.24 * accelerationStress
				+ 0.20 * voltageStress
				+ 0.20 * voltageHeadroomStress
				+ 0.34 * railRippleStress
				+ 0.50 * railSpikeStress
				+ 0.14 * thermalStress
				+ 0.16 * outputStress
				- 0.42;
		double activeElectricalDrive = Math.max(
				smoothStep(0.12, 0.38, escOutput),
				0.55 * railSpikeStress * MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0)
		);
		double targetIntensity = MathUtil.clamp(risk * 1.45, 0.0, 1.0) * activeElectricalDrive;
		double previousIntensity = state.escDesyncIntensity(index);
		double timeConstant = targetIntensity > previousIntensity ? 0.018 : 0.090;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double intensity = previousIntensity + (targetIntensity - previousIntensity) * alpha;
		state.setEscDesyncIntensity(index, intensity);
		return intensity;
	}

	private double electricallyLimitedMotorOmega(
			int index,
			RotorSpec rotor,
			double previousOmega,
			double responseOmega,
			double escOutput,
			double powerLimitScale,
			double aerodynamicLoadFactor,
			double surfaceScrapeIntensity,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0 || rotor.rotorInertiaKgMetersSquared() <= 1.0e-9 || escOutput <= 1.0e-6) {
			return responseOmega;
		}

		double requestedAcceleration = (responseOmega - previousOmega) / dtSeconds;
		if (Math.abs(requestedAcceleration) <= 1.0e-6) {
			return responseOmega;
		}

		double driveVoltage = motorDriveVoltage(escOutput, powerLimitScale);
		double backEmfVoltage = motorBackEmfVoltage(rotor, previousOmega);
		double windingResistanceOhms = temperatureAdjustedMotorWindingResistanceOhms(index);
		double torqueConstant = motorTorqueConstantNewtonMetersPerAmp(rotor);
		double loadTorque = motorLoadTorqueEstimate(index, rotor, previousOmega, aerodynamicLoadFactor, surfaceScrapeIntensity);
		double limitedAcceleration;
		if (requestedAcceleration > 0.0) {
			double phaseCurrent = Math.max(0.0, (driveVoltage - backEmfVoltage) / windingResistanceOhms);
			double availableTorque = phaseCurrent * torqueConstant;
			double breakawayTorque = motorStaticBreakawayTorque(rotor, previousOmega, escOutput, state.motorTemperatureCelsius(index), surfaceScrapeIntensity);
			double requiredTorque = loadTorque + breakawayTorque;
			double availableAcceleration = Math.max(
					0.0,
					(availableTorque - requiredTorque) / rotor.rotorInertiaKgMetersSquared()
			);
			limitedAcceleration = Math.min(requestedAcceleration, availableAcceleration);
		} else {
			double overrunVoltage = Math.max(0.0, backEmfVoltage - driveVoltage);
			double brakingCurrent = overrunVoltage / windingResistanceOhms * MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0);
			double brakingTorque = brakingCurrent * torqueConstant;
			double availableDeceleration = Math.max(
					0.0,
					(loadTorque + brakingTorque) / rotor.rotorInertiaKgMetersSquared()
			);
			limitedAcceleration = Math.max(requestedAcceleration, -availableDeceleration);
		}

		double nextOmega = previousOmega + limitedAcceleration * dtSeconds;
		return MathUtil.clamp(nextOmega, 0.0, rotor.maxOmegaRadiansPerSecond() * 1.08);
	}

	private static double motorStaticBreakawayTorque(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double escOutput,
			double motorTemperatureCelsius,
			double surfaceScrapeIntensity
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double staticFriction = 1.0 - smoothStep(0.018, 0.080, spinRatio);
		if (staticFriction <= 1.0e-6) {
			return 0.0;
		}

		double radiusScale = MathUtil.clamp(Math.sqrt(rotor.radiusMeters() / 0.0635), 0.55, 2.20);
		double inertiaScale = MathUtil.clamp(Math.sqrt(rotor.rotorInertiaKgMetersSquared() / 1.6e-5), 0.55, 3.0);
		double coggingPeak = 1.0 + 0.20 * Math.sin(MathUtil.clamp(escOutput, 0.0, 1.0) * 72.0 + rotor.spinDirection() * 0.7);
		double scrapeScale = 1.0 + 3.2 * MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0);
		double bearingViscosityScale = motorBearingViscosityScale(motorTemperatureCelsius);
		return MathUtil.clamp(
				MOTOR_STATIC_BREAKAWAY_TORQUE_NEWTON_METERS
						* radiusScale
						* inertiaScale
						* bearingViscosityScale
						* coggingPeak
						* scrapeScale
						* staticFriction,
				0.0,
				0.16
		);
	}

	private double motorDriveVoltage(double escOutput, double powerLimitScale) {
		double busVoltage = Math.max(0.0, state.batteryVoltage());
		double commandedDuty = MathUtil.clamp(escOutput, 0.0, 1.0);
		double authority = MathUtil.clamp(powerLimitScale, 0.0, 1.0);
		return busVoltage * commandedDuty * authority;
	}

	private static double motorMechanicalLossTorque(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			double motorTemperatureCelsius,
			double waterImmersion,
			double precipitationWetness,
			double surfaceScrapeIntensity,
			double rotorHealth
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.25);
		if (spinRatio <= 1.0e-6) {
			return 0.0;
		}

		double inertiaScale = MathUtil.clamp(rotor.rotorInertiaKgMetersSquared() / 1.6e-5, 0.25, 8.0);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.35, 3.0);
		double diskDragScale = MathUtil.clamp(rotor.diskDragCoefficient() / 0.0028, 0.25, 5.0);
		double bearingTorque = (0.00011 * Math.sqrt(radiusScale) + 0.00014 * Math.sqrt(inertiaScale))
				* motorBearingViscosityScale(motorTemperatureCelsius)
				* smoothStep(0.015, 0.10, spinRatio);
		double windageTorque = 0.0016
				* diskDragScale
				* Math.max(0.20, airDensityRatio)
				* spinRatio
				* spinRatio;
		double wetPropTorque = 0.010 * MathUtil.clamp(waterImmersion, 0.0, 1.0)
				* MathUtil.clamp(waterImmersion, 0.0, 1.0)
				* spinRatio;
		double rainTorque = 0.0006 * MathUtil.clamp(precipitationWetness, 0.0, 1.0) * spinRatio;
		double scrapeTorque = 0.008 * MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0) * (0.35 + 0.65 * spinRatio);
		double imbalanceTorque = 0.0045 * rotorEffectiveImbalanceIntensity(rotor, rotorHealth) * spinRatio * spinRatio;
		double damageProfileTorque = rotorDamageProfileDragTorque(rotor, rotorHealth, spinRatio, radiusScale, diskDragScale);
		return MathUtil.clamp(
				bearingTorque
						+ windageTorque
						+ wetPropTorque
						+ rainTorque
						+ scrapeTorque
						+ imbalanceTorque
						+ damageProfileTorque,
				0.0,
				0.050
		);
	}

	private static double rotorDamageProfileDragTorque(
			RotorSpec rotor,
			double rotorHealth,
			double spinRatio,
			double radiusScale,
			double diskDragScale
	) {
		return PropellerDamageCalibration.profileDragTorque(rotorHealth, spinRatio, radiusScale, diskDragScale);
	}

	private static double motorBearingViscosityScale(double motorTemperatureCelsius) {
		if (!Double.isFinite(motorTemperatureCelsius)) {
			motorTemperatureCelsius = MOTOR_AMBIENT_TEMPERATURE_CELSIUS;
		}
		double coldRise = Math.max(0.0, MOTOR_AMBIENT_TEMPERATURE_CELSIUS - motorTemperatureCelsius);
		double heatRise = Math.max(0.0, motorTemperatureCelsius - 55.0);
		double scale = 1.0 + 0.018 * coldRise - 0.0016 * heatRise;
		return MathUtil.clamp(scale, 0.88, 1.85);
	}

	private double motorBearingDragTargetScale(int index, double escOutput) {
		double extraViscosity = Math.max(0.0, motorBearingViscosityScale(state.motorTemperatureCelsius(index)) - 1.0);
		double lowDrive = 1.0 - smoothStep(0.08, 0.35, escOutput);
		double loss = extraViscosity * (0.07 + 0.17 * lowDrive);
		return MathUtil.clamp(1.0 - loss, 0.76, 1.02);
	}

	private static double applyMotorMechanicalLoss(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double lossTorqueNewtonMeters,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0 || omegaRadiansPerSecond <= 0.0 || lossTorqueNewtonMeters <= 0.0 || rotor.rotorInertiaKgMetersSquared() <= 1.0e-9) {
			return Math.max(0.0, omegaRadiansPerSecond);
		}
		double deceleration = lossTorqueNewtonMeters / rotor.rotorInertiaKgMetersSquared();
		return Math.max(0.0, omegaRadiansPerSecond - deceleration * dtSeconds);
	}

	private double applyRotorWindmilling(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double escOutput,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0 || rotor.rotorInertiaKgMetersSquared() <= 1.0e-9) {
			return Math.max(0.0, omegaRadiansPerSecond);
		}

		double windmillTargetOmega = rotorWindmillingTargetOmega(
				rotor,
				relativeAirVelocityBody,
				escOutput,
				config.motorActiveBrakingStrength()
		);
		if (windmillTargetOmega <= omegaRadiansPerSecond + 1.0e-6) {
			return Math.max(0.0, omegaRadiansPerSecond);
		}

		double reverseAxialSpeed = rotorWindmillingReverseAxialSpeed(rotor, relativeAirVelocityBody);
		double inertiaScale = MathUtil.clamp(Math.sqrt(rotor.rotorInertiaKgMetersSquared() / 1.6e-5), 0.65, 3.0);
		double timeConstant = MathUtil.clamp(0.20 * inertiaScale / (0.65 + reverseAxialSpeed), 0.018, 0.16);
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double omega = omegaRadiansPerSecond + (windmillTargetOmega - omegaRadiansPerSecond) * alpha;
		return MathUtil.clamp(omega, 0.0, rotor.maxOmegaRadiansPerSecond() * 1.08);
	}

	private double motorBackEmfVoltage(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.abs(omegaRadiansPerSecond) / motorKvRadiansPerSecondPerVolt(rotor);
	}

	private double motorKvRadiansPerSecondPerVolt(RotorSpec rotor) {
		return rotor.maxOmegaRadiansPerSecond() * MOTOR_NO_LOAD_OMEGA_SCALE / Math.max(1.0, config.nominalBatteryVoltage());
	}

	private double motorTorqueConstantNewtonMetersPerAmp(RotorSpec rotor) {
		return 1.0 / motorKvRadiansPerSecondPerVolt(rotor);
	}

	private double inferredMotorWindingResistanceOhms() {
		double perMotorMaxCurrent = config.maxBatteryCurrentAmps() / Math.max(1, state.motorCount());
		double stallCurrent = Math.max(1.0, perMotorMaxCurrent * MOTOR_STALL_CURRENT_SCALE);
		return MathUtil.clamp(config.nominalBatteryVoltage() / stallCurrent, 0.025, 2.5);
	}

	private double baseMotorWindingResistanceOhms(int index) {
		if (index >= 0 && index < config.rotors().size()) {
			double configuredResistance = config.rotors().get(index).motorWindingResistanceOhms();
			if (configuredResistance > 0.0) {
				return configuredResistance;
			}
		}
		return inferredMotorWindingResistanceOhms();
	}

	private double temperatureAdjustedMotorWindingResistanceOhms(int index) {
		return baseMotorWindingResistanceOhms(index) * updateMotorWindingResistanceScale(index);
	}

	private double updateMotorWindingResistanceScale(int index) {
		double scale = motorWindingResistanceTemperatureScale(index);
		state.setMotorWindingResistanceScale(index, scale);
		return scale;
	}

	private double motorWindingResistanceTemperatureScale(int index) {
		double windingTemperatureCelsius = state.motorTemperatureCelsius(index);
		double temperatureRise = windingTemperatureCelsius - MOTOR_AMBIENT_TEMPERATURE_CELSIUS;
		double scale = 1.0 + 0.0039 * temperatureRise;
		return MathUtil.clamp(scale, 0.72, 1.90);
	}

	private double motorWindingTorqueTargetScale(int index, double escOutput) {
		double hotWindingLoss = smoothStep(1.05, 1.62, motorWindingResistanceTemperatureScale(index));
		double loadedDrive = smoothStep(0.30, 0.82, escOutput);
		return MathUtil.clamp(1.0 - 0.14 * hotWindingLoss * loadedDrive, 0.82, 1.04);
	}

	private double motorLoadTorqueEstimate(
			int index,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double aerodynamicLoadFactor,
			double surfaceScrapeIntensity
	) {
		double previousTorque = state.motorAerodynamicTorqueNewtonMeters(index);
		double staticPropTorque = rotor.yawTorquePerThrustMeter()
				* rotor.thrustCoefficient()
				* omegaRadiansPerSecond
				* omegaRadiansPerSecond;
		double loadFactor = MathUtil.clamp(aerodynamicLoadFactor <= 1.0e-6 ? 1.0 : aerodynamicLoadFactor, 0.35, 2.25);
		double propTorque = Math.max(previousTorque, staticPropTorque) * (0.65 + 0.35 * loadFactor);
		double scrapeTorque = rotor.yawTorquePerThrustMeter()
				* rotor.maxThrustNewtons()
				* 0.55
				* MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0);
		return Math.max(0.0, propTorque + scrapeTorque);
	}

	private double escDesyncPulse(int index, double omegaRadiansPerSecond, double desyncIntensity, double dtSeconds) {
		if (desyncIntensity <= 1.0e-6) {
			return 0.0;
		}

		escDesyncPhases[index] += (Math.abs(omegaRadiansPerSecond) * 0.11 + 42.0 + 80.0 * desyncIntensity) * dtSeconds;
		double carrier = 0.5 + 0.5 * Math.sin(escDesyncPhases[index] + index * 1.7);
		double pulse = 0.35 + 0.65 * smoothStep(0.35, 0.95, carrier);
		return MathUtil.clamp(desyncIntensity * pulse, 0.0, 1.0);
	}

	private MotorCommutationRipple updateMotorCommutationRipple(
			int index,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double escOutput,
			double voltageHeadroom,
			double aerodynamicLoadFactor,
			double desyncIntensity,
			double surfaceScrapeIntensity,
			double rotorHealth,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0 || escOutput <= 1.0e-6 || omegaRadiansPerSecond <= 1.0e-6) {
			return new MotorCommutationRipple(0.0, 0.0, 0.0);
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.20);
		double output = MathUtil.clamp(escOutput, 0.0, 1.0);
		double load = MathUtil.clamp(aerodynamicLoadFactor <= 1.0e-6 ? 1.0 : aerodynamicLoadFactor, 0.35, 2.25);
		double dutyRipple = Math.sin(Math.PI * output);
		double lowSpeedLoad = (1.0 - smoothStep(0.10, 0.32, spinRatio)) * smoothStep(0.18, 0.60, output);
		double headroomStress = 1.0 - smoothStep(0.08, 0.35, voltageHeadroom);
		double loadStress = smoothStep(0.88, 1.85, load);
		double railRippleStress = batteryBusRippleStress();
		double railSpikeStress = batteryBusSpikeStress() * smoothStep(0.08, 0.55, spinRatio);
		double activeDrive = Math.max(
				smoothStep(0.04, 0.16, output),
				0.45 * railSpikeStress * MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0)
		);
		double activeSpin = smoothStep(0.025, 0.18, spinRatio) * activeDrive;
		double intensity = activeSpin * MathUtil.clamp(
				0.006
						+ 0.030 * dutyRipple
						+ 0.030 * loadStress
						+ 0.036 * headroomStress
						+ 0.024 * railRippleStress
						+ 0.020 * railSpikeStress
						+ 0.090 * MathUtil.clamp(desyncIntensity, 0.0, 1.0)
						+ 0.050 * MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0)
						+ 0.120 * rotorEffectiveImbalanceIntensity(rotor, rotorHealth) * spinRatio
						+ 0.026 * lowSpeedLoad,
				0.0,
				0.28
		);
		if (intensity <= 1.0e-7) {
			return new MotorCommutationRipple(0.0, 0.0, 0.0);
		}

		motorCommutationPhases[index] = normalizeRadians(
				motorCommutationPhases[index] + Math.abs(omegaRadiansPerSecond) * motorPolePairs(rotor) * dtSeconds
		);
		double phase = motorCommutationPhases[index] * 6.0 + index * 1.23;
		double commutationWave = Math.sin(phase)
				+ 0.33 * Math.sin(phase * 2.0 + 0.7)
				+ 0.18 * Math.sin(phase * 3.0 + 1.9);
		double staticPropTorque = rotor.yawTorquePerThrustMeter()
				* rotor.thrustCoefficient()
				* omegaRadiansPerSecond
				* omegaRadiansPerSecond;
		double referenceTorque = Math.max(
				4.0e-4,
				Math.max(state.motorAerodynamicTorqueNewtonMeters(index), staticPropTorque)
						+ state.motorMechanicalLossTorqueNewtonMeters(index)
		);
		double torqueRipple = referenceTorque * intensity * commutationWave;
		double maxDeltaOmega = rotor.maxOmegaRadiansPerSecond() * (0.0012 + 0.0038 * intensity);
		double deltaOmega = rotor.rotorInertiaKgMetersSquared() <= 1.0e-9
				? 0.0
				: MathUtil.clamp(
						torqueRipple / rotor.rotorInertiaKgMetersSquared() * dtSeconds,
						-maxDeltaOmega,
						maxDeltaOmega
				);
		return new MotorCommutationRipple(intensity, torqueRipple, deltaOmega);
	}

	private double batteryBusRippleStress() {
		double rippleRatio = state.batteryBusRippleVoltage() / Math.max(1.0, config.nominalBatteryVoltage());
		return smoothStep(0.0035, 0.018, rippleRatio);
	}

	private double batteryBusSpikeStress() {
		double spikeRatio = state.batteryVoltageSpike() / Math.max(1.0, config.nominalBatteryVoltage());
		return smoothStep(0.010, 0.060, spikeRatio);
	}

	private static double motorVoltageHeadroom(RotorSpec rotor, double omegaRadiansPerSecond, double voltageScale) {
		double availableNoLoadOmega = rotor.maxOmegaRadiansPerSecond() * MathUtil.clamp(voltageScale, 0.35, 1.08);
		if (availableNoLoadOmega <= 1.0e-6) {
			return 0.0;
		}
		return MathUtil.clamp((availableNoLoadOmega - Math.abs(omegaRadiansPerSecond)) / availableNoLoadOmega, 0.0, 1.0);
	}

	private double motorVoltageHeadroomFromDriveVoltage(RotorSpec rotor, double omegaRadiansPerSecond, double driveVoltage) {
		if (driveVoltage <= 1.0e-6) {
			return 0.0;
		}
		double backEmfVoltage = motorBackEmfVoltage(rotor, omegaRadiansPerSecond);
		return MathUtil.clamp((driveVoltage - backEmfVoltage) / driveVoltage, 0.0, 1.0);
	}

	private static RotorInertiaTorques rotorInertiaTorques(
			RotorSpec rotor,
			double previousOmega,
			double omega,
			Vec3 bodyAngularVelocity,
			Vec3 rotorDiskAxisBody,
			double dtSeconds
	) {
		if (rotor.rotorInertiaKgMetersSquared() <= 0.0 || dtSeconds <= 0.0) {
			return RotorInertiaTorques.ZERO;
		}

		Vec3 diskAxisBody = rotorDiskAxisBody == null || rotorDiskAxisBody.lengthSquared() <= 1.0e-9
				? BODY_ROTOR_AXIS
				: rotorDiskAxisBody.normalized();
		double rotorAngularAcceleration = (omega - previousOmega) / dtSeconds;
		Vec3 accelerationReactionTorque = diskAxisBody.multiply(
				-rotor.spinDirection() * rotor.rotorInertiaKgMetersSquared() * rotorAngularAcceleration
		);
		Vec3 angularMomentumBody = diskAxisBody.multiply(rotor.spinDirection() * rotor.rotorInertiaKgMetersSquared() * omega);
		Vec3 gyroscopicReactionTorque = bodyAngularVelocity.cross(angularMomentumBody).multiply(-1.0);
		return new RotorInertiaTorques(accelerationReactionTorque, gyroscopicReactionTorque);
	}

	private record RotorInertiaTorques(Vec3 accelerationReactionTorque, Vec3 gyroscopicReactionTorque) {
		private static final RotorInertiaTorques ZERO = new RotorInertiaTorques(Vec3.ZERO, Vec3.ZERO);

		private Vec3 totalTorque() {
			return accelerationReactionTorque.add(gyroscopicReactionTorque);
		}
	}

	private Vec3 rotorLocalWindDeltaBody(
			DroneEnvironment environment,
			int rotorIndex
	) {
		Vec3 rotorWindWorld = environment.rotorWindVelocityWorldMetersPerSecond(rotorIndex);
		if (rotorWindWorld == null || !rotorWindWorld.isFinite()) {
			return Vec3.ZERO;
		}
		Vec3 baselineWindWorld = environment.windVelocityWorldMetersPerSecond();
		Vec3 rotorDeltaWorld = rotorWindWorld.subtract(baselineWindWorld);
		if (rotorDeltaWorld.lengthSquared() <= 1.0e-12) {
			return Vec3.ZERO;
		}
		return state.orientation().conjugate().rotate(rotorDeltaWorld);
	}

	private static Vec3 rotorEffectiveDiskWindGradientBody(DroneEnvironment environment, int rotorIndex) {
		if (environment == null) {
			return Vec3.ZERO;
		}

		Vec3 diskGradient = environment.rotorDiskWindGradientBodyMetersPerSecond(rotorIndex);
		Vec3 pressureGradient = a4mcRotorPressureGradientDiskWindBody(environment, rotorIndex);
		double pressureSpeed = pressureGradient.length();
		if (pressureSpeed <= 1.0e-6) {
			return diskGradient;
		}

		Vec3 pressureGradientUnit = pressureGradient.multiply(1.0 / pressureSpeed);
		double existingGradientCoverage = Math.max(0.0, diskGradient.dot(pressureGradientUnit));
		double residualFraction = MathUtil.clamp((pressureSpeed - existingGradientCoverage) / pressureSpeed, 0.0, 1.0);
		if (residualFraction <= 1.0e-6) {
			return diskGradient;
		}
		return diskGradient.add(pressureGradient.multiply(residualFraction)).clamp(-12.0, 12.0);
	}

	private static Vec3 a4mcRotorPressureGradientDiskWindBody(DroneEnvironment environment, int rotorIndex) {
		if (environment == null || !environment.windSourceLocalVoxelFlow()) {
			return Vec3.ZERO;
		}
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return Vec3.ZERO;
		}
		Vec3 pressureGradientWind = environment.rotorA4mcPressureGradientWindBodyMetersPerSecond(rotorIndex);
		if (pressureGradientWind == null || !pressureGradientWind.isFinite()) {
			return Vec3.ZERO;
		}
		return pressureGradientWind;
	}

	private Vec3 rotorActiveBrakingTorque(
			RotorSpec rotor,
			double previousOmega,
			double omega,
			double escOutput,
			Vec3 rotorDiskAxisBody,
			double dtSeconds
	) {
		if (rotor.rotorInertiaKgMetersSquared() <= 0.0
				|| dtSeconds <= 0.0
				|| config.motorActiveBrakingStrength() <= 1.0e-6
				|| previousOmega <= omega) {
			return Vec3.ZERO;
		}

		double previousRpmFraction = MathUtil.clamp(previousOmega / Math.max(1.0, rotor.maxOmegaRadiansPerSecond()), 0.0, 1.15);
		double overrun = Math.max(0.0, previousRpmFraction - MathUtil.clamp(escOutput, 0.0, 1.0));
		double brakingBlend = MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0)
				* smoothStep(0.025, 0.42, overrun)
				* smoothStep(0.06, 0.50, previousRpmFraction);
		if (brakingBlend <= 1.0e-7) {
			return Vec3.ZERO;
		}

		Vec3 diskAxisBody = rotorDiskAxisBody == null || rotorDiskAxisBody.lengthSquared() <= 1.0e-9
				? BODY_ROTOR_AXIS
				: rotorDiskAxisBody.normalized();
		double deceleration = (previousOmega - omega) / dtSeconds;
		double torqueMagnitude = rotor.rotorInertiaKgMetersSquared() * deceleration * brakingBlend;
		return diskAxisBody.multiply(rotor.spinDirection() * torqueMagnitude).clamp(-0.35, 0.35);
	}

	private static Vec3 rotorAngularDragTorque(
			RotorSpec rotor,
			Vec3 bodyAngularVelocity,
			Vec3 rotorDiskAxisBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double aerodynamicLoadFactor,
			double rotorStall,
			double wakeInterference
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (rotor.diskDragCoefficient() <= 0.0 || spinRatio <= 0.08 || bodyAngularVelocity.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		Vec3 diskAxisBody = rotorDiskAxisBody == null || rotorDiskAxisBody.lengthSquared() <= 1.0e-9
				? BODY_ROTOR_AXIS
				: rotorDiskAxisBody.normalized();
		double axialRate = bodyAngularVelocity.dot(diskAxisBody);
		Vec3 axialRateBody = diskAxisBody.multiply(axialRate);
		Vec3 transverseRateBody = bodyAngularVelocity.subtract(axialRateBody);
		double diskLoad = Math.max(
				thrustNewtons,
				rotor.maxThrustNewtons() * spinRatio * spinRatio * 0.18
		);
		double loadFactor = MathUtil.clamp(aerodynamicLoadFactor <= 1.0e-6 ? 1.0 : aerodynamicLoadFactor, 0.35, 2.0);
		double dirtyAirFactor = 1.0 + 0.22 * rotorStall + 0.18 * wakeInterference;
		double transverseMomentPerRadPerSecond = diskLoad
				* rotor.radiusMeters()
				* rotor.diskDragCoefficient()
				* Math.max(0.2, airDensityRatio)
				* spinRatio
				* (0.85 + 0.15 * loadFactor)
				* dirtyAirFactor
				* 2.4;
		double axialMomentPerRadPerSecond = transverseMomentPerRadPerSecond * 0.22;
		Vec3 torque = transverseRateBody.multiply(-transverseMomentPerRadPerSecond)
				.add(axialRateBody.multiply(-axialMomentPerRadPerSecond));
		return torque.clamp(-0.18, 0.18);
	}

	private static double rotorAngularDragLoadFactor(RotorSpec rotor, Vec3 bodyAngularVelocity, double omegaRadiansPerSecond) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (rotor.diskDragCoefficient() <= 0.0 || spinRatio <= 0.08) {
			return 0.0;
		}

		Vec3 axis = rotorAxisBody(rotor);
		double axialRate = Math.abs(bodyAngularVelocity.dot(axis));
		double transverseRate = bodyAngularVelocity.subtract(axis.multiply(bodyAngularVelocity.dot(axis))).length();
		double diskDragScale = MathUtil.clamp(rotor.diskDragCoefficient() / 0.0028, 0.0, 3.5);
		double rateLoad = smoothStep(Math.toRadians(180.0), Math.toRadians(900.0), transverseRate + 0.22 * axialRate);
		return MathUtil.clamp(0.16 * diskDragScale * spinRatio * rateLoad, 0.0, 0.45);
	}

	private static Vec3 rotorDiskAxisBody(Vec3 thrustAxisForceBody) {
		if (thrustAxisForceBody == null || thrustAxisForceBody.lengthSquared() <= 1.0e-9) {
			return BODY_ROTOR_AXIS;
		}
		Vec3 diskAxisBody = thrustAxisForceBody.normalized();
		return diskAxisBody.y() <= 0.0 ? BODY_ROTOR_AXIS : diskAxisBody;
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9 || axis.y() <= 0.0) {
			return BODY_ROTOR_AXIS;
		}
		return axis.normalized();
	}

	private static double rotorAxialVelocity(RotorSpec rotor, Vec3 relativeAirVelocityBody) {
		return relativeAirVelocityBody.dot(rotorAxisBody(rotor));
	}

	static PropellerArchiveCtCpJRotorForceModel.RotorForceSample sampleRotorCtCpJReference(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double airDensityRatio
	) {
		return sampleRotorCtCpJReference(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				airDensityRatio,
				Vec3.ZERO
		);
	}

	static PropellerArchiveCtCpJRotorForceModel.RotorForceSample sampleRotorCtCpJReference(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			Vec3 momentReferenceBodyMeters
	) {
		if (!isApDroneCtCpJReferenceRotor(rotor)
				|| relativeAirVelocityBody == null
				|| !relativeAirVelocityBody.isFinite()
				|| !Double.isFinite(omegaRadiansPerSecond)
				|| omegaRadiansPerSecond <= 0.0) {
			return null;
		}
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER
				* Math.max(0.20, Double.isFinite(airDensityRatio) ? airDensityRatio : 1.0);
		return PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromRelativeAirVelocity(
				PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME,
				"",
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				density,
				momentReferenceBodyMeters,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
	}

	static double rotorCtCpJRuntimeBaseThrustNewtons(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double fallbackBaseThrustNewtons,
			double thrustScale,
			double airDensityRatio
	) {
		if (!ctCpJRuntimeSampleAccepted(sample)) {
			return finiteNonnegative(fallbackBaseThrustNewtons);
		}
		double densityScale = Math.max(0.20, Double.isFinite(airDensityRatio) ? airDensityRatio : 1.0);
		double nonDensityScale = Double.isFinite(thrustScale) ? thrustScale / densityScale : 1.0;
		return finiteNonnegative(sample.thrustNewtons() * MathUtil.clamp(nonDensityScale, 0.0, 4.0));
	}

	static double rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double fallbackRawTorqueNewtonMeters,
			double reactionTorqueScale,
			double compressibilityReactionTorqueScale,
			double inPlaneDragShaftTorqueNewtonMeters,
			double thrustScale,
			double airDensityRatio
	) {
		if (!ctCpJRuntimeSampleAccepted(sample)) {
			return finiteNonnegative(fallbackRawTorqueNewtonMeters);
		}
		double densityScale = Math.max(0.20, Double.isFinite(airDensityRatio) ? airDensityRatio : 1.0);
		double nonDensityScale = Double.isFinite(thrustScale) ? thrustScale / densityScale : 1.0;
		double loadScale = MathUtil.clamp(
				finiteOrDefault(reactionTorqueScale, 1.0)
						* finiteOrDefault(compressibilityReactionTorqueScale, 1.0)
						* MathUtil.clamp(nonDensityScale, 0.0, 4.0),
				0.0,
				4.0
		);
		return finiteNonnegative(sample.shaftTorqueNewtonMeters() * loadScale)
				+ finiteNonnegative(inPlaneDragShaftTorqueNewtonMeters);
	}

	static Vec3 rotorCtCpJRuntimeReactionTorqueBody(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			RotorSpec rotor,
			Vec3 rotorDiskAxisBody,
			double motorAerodynamicTorqueNewtonMeters,
			double commutationRippleTorqueNewtonMeters
	) {
		Vec3 axis = runtimeRotorDiskAxisBody(rotorDiskAxisBody);
		double aerodynamicTorque = finiteNonnegative(motorAerodynamicTorqueNewtonMeters);
		double commutationTorque = finiteOrDefault(commutationRippleTorqueNewtonMeters, 0.0);
		Vec3 fallbackTorque = axis.multiply(rotorSpinDirection(rotor) * (aerodynamicTorque + commutationTorque));
		Vec3 commutation = axis.multiply(rotorSpinDirection(rotor) * commutationTorque);
		if (!ctCpJRuntimeSampleAccepted(sample)
				|| sample.shaftTorqueNewtonMeters() <= 1.0e-12
				|| sample.reactionTorqueBodyNewtonMeters().lengthSquared() <= 1.0e-24) {
			return fallbackTorque;
		}
		Vec3 modelAxis = runtimeRotorThrustAxisBody(rotor);
		Vec3 collinearTorque = modelAxis.multiply(rotorSpinDirection(rotor) * sample.shaftTorqueNewtonMeters());
		double tolerance = 1.0e-18 * Math.max(1.0,
				sample.shaftTorqueNewtonMeters() * sample.shaftTorqueNewtonMeters());
		if (sample.reactionTorqueBodyNewtonMeters().subtract(collinearTorque).lengthSquared() <= tolerance) {
			return fallbackTorque;
		}
		return sample.reactionTorqueBodyNewtonMeters()
				.multiply(aerodynamicTorque / sample.shaftTorqueNewtonMeters())
				.add(commutation);
	}

	static Vec3 rotorCtCpJRuntimeThrustAxisForceBody(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			RotorSpec rotor,
			double thrustNewtons
	) {
		double thrust = finiteNonnegative(thrustNewtons);
		Vec3 rotorAxis = runtimeRotorThrustAxisBody(rotor);
		if (!ctCpJRuntimeSampleAccepted(sample)
				|| sample.thrustNewtons() <= 1.0e-9
				|| sample.thrustForceBodyNewtons().lengthSquared() <= 1.0e-18) {
			return rotorAxis.multiply(thrust);
		}
		Vec3 collinearForce = rotorAxis.multiply(sample.thrustNewtons());
		double tolerance = 1.0e-18 * Math.max(1.0, sample.thrustNewtons() * sample.thrustNewtons());
		if (sample.thrustForceBodyNewtons().subtract(collinearForce).lengthSquared() <= tolerance) {
			return rotorAxis.multiply(thrust);
		}
		return sample.thrustForceBodyNewtons().multiply(thrust / sample.thrustNewtons());
	}

	private static Vec3 runtimeRotorThrustAxisBody(RotorSpec rotor) {
		if (rotor == null || rotor.thrustAxisBody() == null || !rotor.thrustAxisBody().isFinite()
				|| rotor.thrustAxisBody().lengthSquared() <= 1.0e-9) {
			return BODY_ROTOR_AXIS;
		}
		return rotor.thrustAxisBody();
	}

	private static Vec3 runtimeRotorDiskAxisBody(Vec3 rotorDiskAxisBody) {
		if (rotorDiskAxisBody == null || !rotorDiskAxisBody.isFinite()
				|| rotorDiskAxisBody.lengthSquared() <= 1.0e-9) {
			return BODY_ROTOR_AXIS;
		}
		return rotorDiskAxisBody;
	}

	private static int rotorSpinDirection(RotorSpec rotor) {
		return rotor == null ? 1 : rotor.spinDirection();
	}

	private static boolean ctCpJRuntimeSampleAccepted(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample
	) {
		return sample != null && sample.runtimeForceReplacementAccepted();
	}

	private static double finiteOrDefault(double value, double defaultValue) {
		return Double.isFinite(value) ? value : defaultValue;
	}

	private static double finiteNonnegative(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static boolean isApDroneCtCpJReferenceRotor(RotorSpec rotor) {
		return rotor != null
				&& Math.abs(rotor.radiusMeters() - APDRONE_CTCPJ_REFERENCE_RADIUS_METERS)
						<= APDRONE_CTCPJ_REFERENCE_GEOMETRY_TOLERANCE
				&& Math.abs(rotor.bladePitchToDiameterRatio() - APDRONE_CTCPJ_REFERENCE_PITCH_TO_DIAMETER_RATIO)
						<= APDRONE_CTCPJ_REFERENCE_GEOMETRY_TOLERANCE
				&& rotor.bladeCount() == 3;
	}

	private static Vec3 rotorTransverseVelocityBody(RotorSpec rotor, Vec3 relativeAirVelocityBody) {
		Vec3 axis = rotorAxisBody(rotor);
		return relativeAirVelocityBody.subtract(axis.multiply(relativeAirVelocityBody.dot(axis)));
	}

	private static double rotorTransverseSpeed(RotorSpec rotor, Vec3 relativeAirVelocityBody) {
		return rotorTransverseVelocityBody(rotor, relativeAirVelocityBody).length();
	}

	private static Vec3 rotorDiskDragForce(RotorSpec rotor, Vec3 relativeAirVelocityBody, double omegaRadiansPerSecond, double airDensityRatio) {
		Vec3 transverseVelocityBody = rotorTransverseVelocityBody(rotor, relativeAirVelocityBody);
		double transverseSpeed = transverseVelocityBody.length();
		if (transverseSpeed <= 1.0e-6 || rotor.diskDragCoefficient() <= 0.0) {
			return Vec3.ZERO;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double spinFactor = 0.15 + 0.85 * spinRatio;
		double dragScale = rotor.diskDragCoefficient() * airDensityRatio * spinFactor * transverseSpeed;
		return transverseVelocityBody.multiply(-dragScale);
	}

	private static Vec3 rotorInPlaneDragForce(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double translationalLiftIntensity,
			double bladeDissymmetryIntensity,
			double rotorStallIntensity
	) {
		Vec3 transverseVelocityBody = rotorTransverseVelocityBody(rotor, relativeAirVelocityBody);
		double transverseSpeed = transverseVelocityBody.length();
		double diskDragScale = MathUtil.clamp(rotor.diskDragCoefficient() / 0.0028, 0.0, 3.5);
		if (transverseSpeed <= 1.0e-6 || thrustNewtons <= 1.0e-6 || diskDragScale <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		if (spinRatio <= 0.08) {
			return Vec3.ZERO;
		}

		double advanceRatio = transverseSpeed / rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double activeDisk = smoothStep(0.10, 0.32, spinRatio);
		double crossflow = smoothStep(0.025, 0.35, advanceRatio);
		double loadedCrossflow = smoothStep(0.08, 0.55, advanceRatio);
		double hCoefficient = diskDragScale
				* activeDisk
				* crossflow
				* (0.030
						+ 0.105 * loadedCrossflow
						+ 0.035 * MathUtil.clamp(translationalLiftIntensity, 0.0, 1.0)
						+ 0.045 * MathUtil.clamp(bladeDissymmetryIntensity, 0.0, 1.0)
						+ 0.055 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0));
		double thrustCoupledForce = Math.max(0.0, thrustNewtons) * hCoefficient;

		double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.max(0.20, airDensityRatio);
		double dynamicPressure = 0.5 * density * transverseSpeed * transverseSpeed;
		double profileCoefficient = diskDragScale
				* activeDisk
				* (0.020 + 0.045 * loadedCrossflow)
				* smoothStep(0.04, 0.32, advanceRatio);
		double profileForce = dynamicPressure * diskArea * profileCoefficient;
		double forceMagnitude = MathUtil.clamp(
				thrustCoupledForce + profileForce,
				0.0,
				rotor.maxThrustNewtons() * 0.42
		);
		return transverseVelocityBody.multiply(-forceMagnitude / transverseSpeed);
	}

	private static double rotorInPlaneDragShaftTorque(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			Vec3 inPlaneDragBody,
			double omegaRadiansPerSecond
	) {
		double forceMagnitude = inPlaneDragBody.length();
		double shaftSpeed = Math.abs(omegaRadiansPerSecond);
		if (forceMagnitude <= 1.0e-9 || shaftSpeed <= 1.0e-6) {
			return 0.0;
		}
		double transverseSpeed = rotorTransverseVelocityBody(rotor, relativeAirVelocityBody).length();
		if (transverseSpeed <= 1.0e-6) {
			return 0.0;
		}

		double profilePowerWatts = forceMagnitude
				* transverseSpeed
				* MathUtil.clamp(0.48 + 0.34 * smoothStep(0.05, 0.55, rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond)), 0.48, 0.82);
		double torqueLimit = Math.max(
				rotor.maxThrustNewtons() * Math.abs(rotor.yawTorquePerThrustMeter()) * 0.90,
				rotor.maxThrustNewtons() * rotor.radiusMeters() * 0.075
		);
		return MathUtil.clamp(profilePowerWatts / shaftSpeed, 0.0, torqueLimit);
	}

	private static double rotorInPlaneDragLoadFactor(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double translationalLiftIntensity,
			double bladeDissymmetryIntensity,
			double rotorStallIntensity
	) {
		double diskDragScale = MathUtil.clamp(rotor.diskDragCoefficient() / 0.0028, 0.0, 3.5);
		if (diskDragScale <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		if (spinRatio <= 0.08) {
			return 0.0;
		}
		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double crossflow = smoothStep(0.04, 0.46, advanceRatio);
		double separatedLoading = 0.35 * MathUtil.clamp(translationalLiftIntensity, 0.0, 1.0)
				+ 0.45 * MathUtil.clamp(bladeDissymmetryIntensity, 0.0, 1.0)
				+ 0.55 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0);
		return MathUtil.clamp(
				0.10 * diskDragScale * crossflow * (0.35 + 0.65 * spinRatio) * (1.0 + separatedLoading),
				0.0,
				0.42
		);
	}

	private static Vec3 rotorWindmillingDragForce(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double escOutput,
			double airDensityRatio
	) {
		double reverseAxialSpeed = rotorWindmillingReverseAxialSpeed(rotor, relativeAirVelocityBody);
		double lowDrive = rotorWindmillingLowDriveFactor(escOutput);
		if (reverseAxialSpeed <= 1.0e-6 || lowDrive <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.max(0.20, airDensityRatio);
		double dynamicPressure = 0.5 * density * reverseAxialSpeed * reverseAxialSpeed;
		double pitchScale = MathUtil.clamp(rotorBladePitchRatio(rotor), 0.45, 1.8);
		double dragCoefficient = (0.13 + 0.22 * pitchScale + 0.38 * smoothStep(0.04, 0.30, spinRatio)) * lowDrive;
		double forceNewtons = MathUtil.clamp(
				dynamicPressure * diskArea * dragCoefficient,
				0.0,
				rotor.maxThrustNewtons() * 0.42
		);
		return rotorAxisBody(rotor).multiply(forceNewtons);
	}

	private static double rotorWindmillingTargetOmega(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double escOutput,
			double motorBrakeStrength
	) {
		double reverseAxialSpeed = rotorWindmillingReverseAxialSpeed(rotor, relativeAirVelocityBody);
		double lowDrive = rotorWindmillingLowDriveFactor(escOutput);
		if (reverseAxialSpeed <= 1.0e-6 || lowDrive <= 1.0e-6) {
			return 0.0;
		}

		double freewheel = 1.0 - 0.78 * MathUtil.clamp(motorBrakeStrength, 0.0, 1.0) * lowDrive;
		double airDrive = smoothStep(0.85, 9.5, reverseAxialSpeed);
		double pitchOmega = reverseAxialSpeed * (2.0 * Math.PI) / Math.max(0.012, rotor.bladePitchMeters());
		double pitchScale = MathUtil.clamp(1.0 / Math.sqrt(rotorBladePitchRatio(rotor)), 0.55, 1.55);
		double targetOmega = pitchOmega * 0.42 * pitchScale * lowDrive * freewheel * airDrive;
		return MathUtil.clamp(targetOmega, 0.0, rotor.maxOmegaRadiansPerSecond() * ROTOR_WINDMILL_MAX_OMEGA_FRACTION);
	}

	private static double rotorWindmillingReverseAxialSpeed(RotorSpec rotor, Vec3 relativeAirVelocityBody) {
		return Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody) - 0.55);
	}

	private static double rotorWindmillingLowDriveFactor(double escOutput) {
		return 1.0 - smoothStep(0.080, 0.42, MathUtil.clamp(escOutput, 0.0, 1.0));
	}

	private static double rotorWindmillingIntensity(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double escOutput
	) {
		double reverseAxialSpeed = rotorWindmillingReverseAxialSpeed(rotor, relativeAirVelocityBody);
		double lowDrive = rotorWindmillingLowDriveFactor(escOutput);
		if (reverseAxialSpeed <= 1.0e-6 || lowDrive <= 1.0e-6) {
			return 0.0;
		}

		double reverseCapture = smoothStep(1.0, 12.0, reverseAxialSpeed);
		return MathUtil.clamp(reverseCapture * lowDrive, 0.0, 1.0);
	}

	private Vec3 updateRotorWallEffectForce(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double obstruction,
			Vec3 obstructionDirectionBody,
			double wallForceFactor,
			double dtSeconds
	) {
		Vec3 target = calculateSteadyRotorWallEffectForce(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				obstruction,
				obstructionDirectionBody,
				wallForceFactor
		);
		if (dtSeconds <= 0.0) {
			rotorWallEffectForceBodyFiltered[index] = target;
			return rotorWallEffectForceBodyFiltered[index];
		}

		double targetMagnitude = target.length();
		double previousMagnitude = rotorWallEffectForceBodyFiltered[index].length();
		double obstructionScale = MathUtil.clamp(obstruction, 0.0, 1.0);
		double buildTimeConstant = MathUtil.clamp(0.040 - 0.012 * obstructionScale, 0.024, 0.046);
		double releaseTimeConstant = MathUtil.clamp(0.095 + 0.045 * obstructionScale, 0.080, 0.145);
		double timeConstant = targetMagnitude > previousMagnitude ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		rotorWallEffectForceBodyFiltered[index] = rotorWallEffectForceBodyFiltered[index]
				.add(target.subtract(rotorWallEffectForceBodyFiltered[index]).multiply(alpha))
				.clamp(-4.0, 4.0);
		if (targetMagnitude <= 1.0e-6 && rotorWallEffectForceBodyFiltered[index].lengthSquared() < 1.0e-8) {
			rotorWallEffectForceBodyFiltered[index] = Vec3.ZERO;
		}
		return rotorWallEffectForceBodyFiltered[index];
	}

	static Vec3 calculateSteadyRotorWallEffectForce(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double obstruction,
			Vec3 obstructionDirectionBody
	) {
		return calculateSteadyRotorWallEffectForce(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				obstruction,
				obstructionDirectionBody,
				1.0
		);
	}

	static Vec3 calculateSteadyRotorWallEffectForce(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double obstruction,
			Vec3 obstructionDirectionBody,
			double wallForceFactor
	) {
		obstruction = MathUtil.clamp(obstruction, 0.0, 1.0);
		wallForceFactor = MathUtil.clamp(wallForceFactor, 0.0, 1.0);
		if (obstruction <= 1.0e-6 || wallForceFactor <= 1.0e-6 || thrustNewtons <= 1.0e-6 || obstructionDirectionBody == null) {
			return Vec3.ZERO;
		}

		Vec3 lateralDirection = new Vec3(obstructionDirectionBody.x(), 0.0, obstructionDirectionBody.z()).normalized();
		if (lateralDirection.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double thrustFraction = MathUtil.clamp(thrustNewtons / rotor.maxThrustNewtons(), 0.0, 1.15);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		double speedWashout = 1.0 - MathUtil.clamp(transverseSpeed / 12.0, 0.0, 0.78);
		double blockage = Math.pow(obstruction, 1.18);
		double wallCushion = blockage * spinRatio * (0.35 + 0.65 * thrustFraction) * speedWashout;
		double diskPressureForce = Math.max(thrustNewtons, rotor.maxThrustNewtons() * spinRatio * spinRatio * 0.70);
		double forceMagnitude = diskPressureForce
				* MathUtil.clamp(0.110 + 0.450 * wallCushion, 0.0, 0.45)
				* blockage
				* speedWashout
				* wallForceFactor;
		return lateralDirection.multiply(-forceMagnitude).clamp(-4.0, 4.0);
	}

	private double updateRotorSurfaceEffectThrustMultiplier(
			int index,
			RotorSpec rotor,
			double targetMultiplier,
			double omegaRadiansPerSecond,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0) {
			return rotorSurfaceEffectThrustMultipliers[index];
		}

		double previousMultiplier = MathUtil.clamp(rotorSurfaceEffectThrustMultipliers[index], 0.35, 2.0);
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double activeDisk = smoothStep(0.08, 0.28, spinRatio);
		double targetDelta = (MathUtil.clamp(targetMultiplier, 0.35, 2.0) - 1.0) * activeDisk;
		double targetSurfaceMultiplier = MathUtil.clamp(1.0 + targetDelta, 0.35, 2.0);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double diskResponse = 0.76 + 0.58 * spinRatio;
		double previousEffect = Math.abs(previousMultiplier - 1.0);
		double targetEffect = Math.abs(targetSurfaceMultiplier - 1.0);
		double buildTimeConstant = MathUtil.clamp(0.034 * Math.sqrt(radiusScale) / diskResponse, 0.010, 0.085);
		double releaseTimeConstant = MathUtil.clamp(0.096 * Math.sqrt(radiusScale) / Math.max(0.70, diskResponse), 0.035, 0.210);
		double timeConstant = targetEffect > previousEffect ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double multiplier = previousMultiplier + (targetSurfaceMultiplier - previousMultiplier) * alpha;
		rotorSurfaceEffectThrustMultipliers[index] = MathUtil.clamp(multiplier, 0.35, 2.0);
		return rotorSurfaceEffectThrustMultipliers[index];
	}

	private Vec3 calculatePropwashTorque(
			DroneInput input,
			Vec3 relativeAirVelocityBody,
			Vec3 angularVelocityBody,
			DroneEnvironment environment,
			double dtSeconds
	) {
		if (config.propwashMaxTorqueNewtonMeters() <= 0.0) {
			state.setPropwashWakeIntensity(0.0);
			state.setPropwashIntensity(0.0);
			state.setPropwashTorqueBodyNewtonMeters(Vec3.ZERO);
			return Vec3.ZERO;
		}

		RotorWakeFlow wakeFlow = averageRotorWakeFlow(relativeAirVelocityBody, angularVelocityBody);
		double descentSpeed = wakeFlow.axialDescentSpeedMetersPerSecond();
		double descentFactor = MathUtil.clamp(
				(descentSpeed - config.propwashStartDescentMetersPerSecond())
						/ (config.propwashFullDescentMetersPerSecond() - config.propwashStartDescentMetersPerSecond()),
				0.0,
				1.0
		);
		double transverseSpeed = wakeFlow.transverseSpeedMetersPerSecond();
		double wakeRetention = 1.0 - MathUtil.clamp(transverseSpeed / 7.0, 0.0, 1.0);
		double motorPower = state.averageMotorPower(config);
		double wakeCoherence = a4mcAblWakeCoherenceMultiplier(environment);
		double targetWake = input.armed()
				? MathUtil.clamp(descentFactor * wakeRetention * (0.25 + 0.75 * motorPower) * wakeCoherence, 0.0, 1.0)
				: 0.0;
		double wakeIntensity = updatePropwashWakeIntensity(targetWake, wakeRetention, environment, dtSeconds);
		double throttleFactor = input.armed() ? Math.pow(input.throttle(), 1.35) : 0.0;
		double intensity = MathUtil.clamp(wakeIntensity * throttleFactor * (0.35 + 0.65 * motorPower), 0.0, 1.0);

		if (intensity <= 1.0e-5) {
			state.setPropwashIntensity(0.0);
			state.setPropwashTorqueBodyNewtonMeters(Vec3.ZERO);
			return Vec3.ZERO;
		}

		propwashPhaseA += dtSeconds * (18.0 + 28.0 * motorPower);
		propwashPhaseB += dtSeconds * (24.0 + 19.0 * motorPower);
		double torqueScale = config.propwashMaxTorqueNewtonMeters() * intensity;
		Vec3 torque = new Vec3(
				torqueScale * (Math.sin(propwashPhaseA) + 0.35 * Math.sin(propwashPhaseB * 2.31)),
				torqueScale * 0.18 * Math.sin(propwashPhaseA * 0.61 + propwashPhaseB),
				torqueScale * (Math.cos(propwashPhaseB) - 0.25 * Math.sin(propwashPhaseA * 1.73))
		);
		state.setPropwashIntensity(intensity);
		state.setPropwashTorqueBodyNewtonMeters(torque);
		return torque;
	}

	private RotorWakeFlow averageRotorWakeFlow(Vec3 relativeAirVelocityBody, Vec3 angularVelocityBody) {
		double weightedDescent = 0.0;
		double weightedTransverse = 0.0;
		double totalWeight = 0.0;
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			Vec3 arm = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
			Vec3 localAirVelocity = relativeAirVelocityBody.add(angularVelocityBody.cross(arm));
			Vec3 axis = rotorAxisBody(rotor);
			double axialVelocity = localAirVelocity.dot(axis);
			double transverseSpeed = localAirVelocity.subtract(axis.multiply(axialVelocity)).length();
			double spinRatio = MathUtil.clamp(
					Math.abs(state.motorOmegaRadiansPerSecond(i)) / rotor.maxOmegaRadiansPerSecond(),
					0.0,
					1.0
			);
			double weight = 0.35 + 0.65 * spinRatio;
			weightedDescent += Math.max(0.0, -axialVelocity) * weight;
			weightedTransverse += transverseSpeed * weight;
			totalWeight += weight;
		}
		if (totalWeight <= 1.0e-9) {
			return new RotorWakeFlow(0.0, 0.0);
		}
		return new RotorWakeFlow(
				weightedDescent / totalWeight,
				weightedTransverse / totalWeight
		);
	}

	private double updatePropwashWakeIntensity(
			double targetWakeIntensity,
			double wakeRetention,
			DroneEnvironment environment,
			double dtSeconds
	) {
		double previousWakeIntensity = state.propwashWakeIntensity();
		double flushFactor = 1.0 - MathUtil.clamp(wakeRetention, 0.0, 1.0);
		double buildTimeConstant = MathUtil.clamp(
				0.055 * a4mcAblWakeBuildTimeScaleMultiplier(environment),
				0.038,
				0.075
		);
		double releaseTimeConstant = MathUtil.clamp(
				(0.130 - 0.090 * flushFactor) * a4mcAblWakeReleaseTimeScaleMultiplier(environment),
				0.028,
				0.180
		);
		double timeConstant = targetWakeIntensity > previousWakeIntensity
				? buildTimeConstant
				: releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double wakeIntensity = previousWakeIntensity + (targetWakeIntensity - previousWakeIntensity) * alpha;
		state.setPropwashWakeIntensity(wakeIntensity);
		return wakeIntensity;
	}

	private Vec3 calculateWindTurbulenceTorque(DroneEnvironment environment, Vec3 relativeAirVelocityBody, double dtSeconds) {
		double intensity = dirtyAirIntensity(environment);
		double airspeed = relativeAirVelocityBody.length();
		if (intensity <= 1.0e-6 || airspeed <= 0.5) {
			return Vec3.ZERO;
		}

		turbulencePhaseA += dtSeconds * (2.6 + airspeed * 0.42 + intensity * 1.8);
		turbulencePhaseB += dtSeconds * (3.9 + airspeed * 0.31 + intensity * 1.2);
		turbulencePhaseC += dtSeconds * (2.1 + airspeed * 0.24 + intensity * 1.5);
		double speedFactor = MathUtil.clamp(airspeed / 18.0, 0.15, 1.45);
		double scale = 0.020 * intensity * speedFactor * speedFactor;
		double lateralRatio = relativeAirVelocityBody.x() / airspeed;
		double verticalRatio = relativeAirVelocityBody.y() / airspeed;

		double pitchTorque = scale * (
				Math.sin(turbulencePhaseA)
						+ 0.35 * Math.sin(turbulencePhaseB * 1.73 + 0.4)
						+ 0.25 * verticalRatio
		);
		double yawTorque = scale * 0.75 * (
				Math.sin(turbulencePhaseB + 1.2)
						+ 0.30 * Math.sin(turbulencePhaseC * 1.41)
						+ 0.35 * lateralRatio
		);
		double rollTorque = scale * 0.85 * (
				Math.sin(turbulencePhaseC + 2.4)
						+ 0.45 * lateralRatio * verticalRatio
		);
		return new Vec3(
				MathUtil.clamp(pitchTorque, -0.08, 0.08),
				MathUtil.clamp(yawTorque, -0.08, 0.08),
				MathUtil.clamp(rollTorque, -0.08, 0.08)
		);
	}

	private static double rotorAirflowThrustMultiplier(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double translationalLiftIntensity
	) {
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		double advanceRatio = transverseSpeed / tipSpeed;
		double transverseLift = 1.0 + rotor.transverseFlowLiftCoefficient() * MathUtil.clamp(
				0.35 * MathUtil.clamp(advanceRatio / 0.18, 0.0, 1.0)
						+ 0.65 * translationalLiftIntensity,
				0.0,
				1.0
		);
		double forwardAdvanceThrustScale = rotorForwardAdvanceThrustScale(rotor, advanceRatio);
		double postPeakAdvanceLoss = rotorForwardAdvancePostPeakThrustLoss(rotor, advanceRatio);

		double descentSpeed = Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody) - 1.2);
		double descentRatio = descentSpeed / Math.max(1.5, tipSpeed * 0.08);
		double axialLoss = rotor.axialFlowThrustLossCoefficient() * MathUtil.clamp(descentRatio, 0.0, 1.0);
		double climbSpeed = Math.max(0.0, rotorAxialVelocity(rotor, relativeAirVelocityBody));
		double pitchAdvance = climbSpeed / rotorPitchSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double pitchUnloadAuthority = MathUtil.clamp(0.08 + 0.18 / rotorBladePitchRatio(rotor), 0.10, 0.32);
		double bladePitchUnload = pitchUnloadAuthority * smoothStep(0.42, 1.05, pitchAdvance);
		double axialGustThrustScale = rotorAxialGustThrustScale(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		return MathUtil.clamp(
				transverseLift
						* forwardAdvanceThrustScale
						* (1.0 - axialLoss)
						* (1.0 - postPeakAdvanceLoss)
						* (1.0 - bladePitchUnload)
						* axialGustThrustScale,
				0.12,
				1.75
		);
	}

	private static double rotorAxialGustThrustScale(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond
	) {
		return rotorAxialGustThrustScale(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond),
				rotorTransverseSpeed(rotor, relativeAirVelocityBody)
		);
	}

	private static double rotorAxialGustThrustScale(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double tipSpeedMetersPerSecond,
			double transverseSpeedMetersPerSecond
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.06) {
			return 1.0;
		}

		double axialVelocity = rotorAxialVelocity(rotor, relativeAirVelocityBody);
		double axialSpeed = Math.abs(axialVelocity);
		double totalSpeed = Math.hypot(axialSpeed, transverseSpeedMetersPerSecond);
		if (axialSpeed <= 0.35 || totalSpeed <= 0.35) {
			return 1.0;
		}

		double axialAlignment = axialSpeed / totalSpeed;
		double transverseAdvanceRatio = transverseSpeedMetersPerSecond / Math.max(1.0, tipSpeedMetersPerSecond);
		double axialDominance = smoothStep(0.55, 0.94, axialAlignment)
				* (1.0 - smoothStep(0.045, 0.22, transverseAdvanceRatio));
		double axialRatio = axialSpeed / Math.max(2.5, tipSpeedMetersPerSecond * 0.22);
		double severity = smoothStep(0.16, 0.58, axialRatio) * axialDominance;
		if (severity <= 1.0e-6) {
			return 1.0;
		}

		double lowRpmAuthority = 1.0 - smoothStep(0.14, 0.32, spinRatio);
		if (axialVelocity > 0.0) {
			double adverseLoss = (0.22 + 0.58 * lowRpmAuthority) * severity;
			return MathUtil.clamp(1.0 - adverseLoss, 0.18, 1.0);
		}

		double assistingGain = (0.36 + 0.96 * lowRpmAuthority) * severity;
		return MathUtil.clamp(1.0 + assistingGain, 1.0, 2.35);
	}

	static double rotorForwardAdvanceThrustScale(RotorSpec rotor, double advanceRatio) {
		return MathUtil.clamp(1.0 - rotorForwardAdvanceThrustLoss(rotor, advanceRatio), 0.12, 1.05);
	}

	static double rotorForwardAdvancePowerScale(RotorSpec rotor, double advanceRatio) {
		double propAdvanceRatio = rotorUiucEquivalentPropellerAdvanceRatio(rotor, advanceRatio);
		double lowAdvanceLoss = 0.032 * smoothStep(0.08, 0.25, propAdvanceRatio);
		double midAdvanceLoss = 0.280 * smoothStep(0.25, 0.45, propAdvanceRatio);
		double highAdvanceLoss = 0.279 * smoothStep(0.45, 0.65, propAdvanceRatio);
		double postPeakLoss = 0.220 * smoothStep(0.65, 0.95, propAdvanceRatio);
		return MathUtil.clamp(1.0 - lowAdvanceLoss - midAdvanceLoss - highAdvanceLoss - postPeakLoss, 0.16, 1.08);
	}

	static double rotorForwardAdvanceTorquePerThrustScale(RotorSpec rotor, double advanceRatio) {
		double thrustScale = rotorForwardAdvanceThrustScale(rotor, advanceRatio);
		double powerScale = rotorForwardAdvancePowerScale(rotor, advanceRatio);
		return MathUtil.clamp(powerScale / Math.max(0.12, thrustScale), 0.65, 3.20);
	}

	private static double rotorForwardAdvanceThrustLoss(RotorSpec rotor, double advanceRatio) {
		double propAdvanceRatio = rotorUiucEquivalentPropellerAdvanceRatio(rotor, advanceRatio);
		double lowAdvanceLoss = 0.10 * smoothStep(0.14, 0.25, propAdvanceRatio);
		double midAdvanceLoss = 0.36 * smoothStep(0.25, 0.45, propAdvanceRatio);
		double highAdvanceLoss = 0.46 * smoothStep(0.45, 0.72, propAdvanceRatio);
		return MathUtil.clamp(lowAdvanceLoss + midAdvanceLoss + highAdvanceLoss, 0.0, 0.88);
	}

	private static double rotorForwardAdvancePostPeakThrustLoss(RotorSpec rotor, double advanceRatio) {
		double propAdvanceRatio = rotorUiucEquivalentPropellerAdvanceRatio(rotor, advanceRatio);
		return 0.20 * smoothStep(0.95, 1.35, propAdvanceRatio);
	}

	static double rotorUiucEquivalentPropellerAdvanceRatio(RotorSpec rotor, double advanceRatio) {
		double pitchRelief = MathUtil.clamp(Math.sqrt(rotorBladePitchRatio(rotor)), 0.75, 1.45);
		// UIUC prop data uses J = V / (nD), while rotorAdvanceRatio is mu = V / (omega R).
		return Math.PI * MathUtil.clamp(advanceRatio, 0.0, 2.0) / pitchRelief;
	}

	static double rotorAdvanceRatioForUiucEquivalentPropellerAdvanceRatio(RotorSpec rotor, double propellerAdvanceRatioJ) {
		double pitchRelief = MathUtil.clamp(Math.sqrt(rotorBladePitchRatio(rotor)), 0.75, 1.45);
		return MathUtil.clamp(Math.max(0.0, propellerAdvanceRatioJ) * pitchRelief / Math.PI, 0.0, 2.0);
	}

	private static double rotorBladeStallIntensity(RotorSpec rotor, Vec3 relativeAirVelocityBody, double omegaRadiansPerSecond) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.12) {
			return 0.0;
		}

		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double lateralStall = smoothStep(0.32, 0.72, advanceRatio);

		double reverseAxialSpeed = Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody));
		double axialRatio = reverseAxialSpeed / Math.max(1.5, tipSpeed * 0.18);
		double reverseAxialStall = 0.65 * smoothStep(0.55, 1.25, axialRatio);

		double combined = 1.0 - (1.0 - lateralStall) * (1.0 - reverseAxialStall);
		return MathUtil.clamp(combined * smoothStep(0.18, 0.55, spinRatio), 0.0, 1.0);
	}

	private double updateRotorDynamicStallIntensity(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double kinematicStallIntensity,
			double bladeElementStallIntensity,
			double bladeDissymmetryIntensity,
			double diskWindGradientStallIntensity,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0) {
			return rotorDynamicStallIntensity[index];
		}

		double previousStall = rotorDynamicStallIntensity[index];
		double targetStall = rotorDynamicStallTargetIntensity(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				kinematicStallIntensity,
				bladeElementStallIntensity,
				bladeDissymmetryIntensity,
				diskWindGradientStallIntensity
		);
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double highAdvance = smoothStep(0.36, 0.78, advanceRatio);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double spinResponse = 0.72 + 0.55 * spinRatio;
		double attackTimeConstant = MathUtil.clamp(
				0.020 * Math.sqrt(radiusScale) / spinResponse * (1.0 - 0.28 * highAdvance),
				0.007,
				0.055
		);
		double recoveryTimeConstant = MathUtil.clamp(
				(0.080 + 0.055 * highAdvance) * Math.sqrt(radiusScale) / Math.max(0.72, spinResponse),
				0.040,
				0.220
		);
		double effectiveTarget = targetStall;
		if (targetStall < previousStall && highAdvance > 1.0e-6) {
			double attachedFlowMemory = previousStall
					* (0.16 + 0.46 * highAdvance)
					* smoothStep(0.14, 0.42, spinRatio);
			effectiveTarget = Math.max(targetStall, attachedFlowMemory);
		}

		double timeConstant = effectiveTarget > previousStall ? attackTimeConstant : recoveryTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double dynamicStall = previousStall + (effectiveTarget - previousStall) * alpha;
		rotorDynamicStallIntensity[index] = MathUtil.clamp(dynamicStall, 0.0, 1.0);
		return rotorDynamicStallIntensity[index];
	}

	private static double rotorDynamicStallTargetIntensity(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double kinematicStallIntensity,
			double bladeElementStallIntensity,
			double bladeDissymmetryIntensity,
			double diskWindGradientStallIntensity
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		if (spinRatio <= 0.08) {
			return 0.0;
		}

		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double highAdvance = smoothStep(0.36, 0.78, advanceRatio);
		double activeRotor = smoothStep(0.14, 0.46, spinRatio);
		double retreatingBladeStall = MathUtil.clamp(bladeDissymmetryIntensity, 0.0, 1.0) * highAdvance;
		double elementStall = MathUtil.clamp(bladeElementStallIntensity, 0.0, 1.0);
		double elementDrivenStall = MathUtil.clamp(
				Math.max(0.70 * elementStall, 0.26 * retreatingBladeStall),
				0.0,
				1.0
		);
		double highAdvanceBias = 0.020
				* smoothStep(0.54, 0.88, advanceRatio)
				* smoothStep(0.22, 0.58, spinRatio);
		double localGradientStall = MathUtil.clamp(diskWindGradientStallIntensity, 0.0, 0.16);
		double targetStall = Math.max(
				Math.max(MathUtil.clamp(kinematicStallIntensity, 0.0, 1.0), elementDrivenStall),
				localGradientStall
		)
				+ highAdvanceBias;
		return MathUtil.clamp(targetStall * activeRotor, 0.0, 1.0);
	}

	private static double rotorStallVibration(RotorSpec rotor, double omegaRadiansPerSecond, double rotorStallIntensity) {
		if (rotorStallIntensity <= 1.0e-6 || rotor.stallThrustLossCoefficient() <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double lossScale = MathUtil.clamp(rotor.stallThrustLossCoefficient() / 0.34, 0.0, 1.0);
		return MathUtil.clamp(0.35 * lossScale * rotorStallIntensity * spinRatio, 0.0, 1.0);
	}

	private double updateRotorVortexRingStateIntensity(
			int rotorIndex,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond,
			double dtSeconds
	) {
		double target = calculateSteadyRotorVortexRingStateIntensity(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond
		);
		if (dtSeconds <= 0.0) {
			rotorVortexRingStateIntensity[rotorIndex] = target;
			return target;
		}

		double previous = rotorVortexRingStateIntensity[rotorIndex];
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		double transverseFlush = rotorVortexRingForwardEscape(transverseSpeed, inducedVelocityMetersPerSecond);
		double diskResponse = 0.72 + 0.55 * spinRatio;
		double attackTimeConstant = MathUtil.clamp(0.070 * Math.sqrt(radiusScale) / diskResponse, 0.025, 0.140);
		double recoveryTimeConstant = MathUtil.clamp(
				(0.190 - 0.095 * transverseFlush) * Math.sqrt(radiusScale) / Math.max(0.72, diskResponse),
				0.055,
				0.260
		);
		double timeConstant = target > previous ? attackTimeConstant : recoveryTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double intensity = previous + (target - previous) * alpha;
		if (target <= 1.0e-6 && intensity < 1.0e-5) {
			intensity = 0.0;
		}
		rotorVortexRingStateIntensity[rotorIndex] = MathUtil.clamp(intensity, 0.0, 1.0);
		return rotorVortexRingStateIntensity[rotorIndex];
	}

	static double calculateSteadyRotorVortexRingStateIntensity(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.12 || rotor.axialFlowThrustLossCoefficient() <= 0.0) {
			return 0.0;
		}

		double descentRatio = rotorVortexRingDescentRatio(rotor, relativeAirVelocityBody, inducedVelocityMetersPerSecond);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);

		double descentEnvelope = rotorVortexRingDescentEnvelope(descentRatio);
		double washout = 1.0 - rotorVortexRingForwardEscape(transverseSpeed, inducedVelocityMetersPerSecond);
		double load = smoothStep(0.12, 0.75, spinRatio);
		return MathUtil.clamp(descentEnvelope * washout * load, 0.0, 1.0);
	}

	static double rotorVortexRingForwardEscape(double transverseSpeedMetersPerSecond, double inducedVelocityMetersPerSecond) {
		if (!Double.isFinite(transverseSpeedMetersPerSecond)
				|| transverseSpeedMetersPerSecond <= 0.0
				|| !Double.isFinite(inducedVelocityMetersPerSecond)) {
			return 0.0;
		}
		double hoverInducedVelocity = Math.max(1.0, inducedVelocityMetersPerSecond);
		double baselineForwardCutoff = JOHNSON_BASELINE_FORWARD_CUTOFF_VX_OVER_VH * hoverInducedVelocity;
		double vrsForwardCutoff = JOHNSON_VRS_FORWARD_CUTOFF_VX_OVER_VH * hoverInducedVelocity;
		return smoothStep(baselineForwardCutoff, vrsForwardCutoff, transverseSpeedMetersPerSecond);
	}

	static double rotorVortexRingDescentEnvelope(double descentRatio) {
		if (!Double.isFinite(descentRatio)) {
			return 0.0;
		}

		double entry = smoothStep(0.45, 1.20, descentRatio);
		double highDescentExit = 1.0 - smoothStep(1.35, 2.25, descentRatio);
		return MathUtil.clamp(entry * highDescentExit, 0.0, 1.0);
	}

	static double rotorVortexRingDescentRatio(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double inducedVelocityMetersPerSecond
	) {
		double descentSpeed = Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody));
		double inducedVelocity = Math.max(1.0, inducedVelocityMetersPerSecond);
		return descentSpeed / inducedVelocity;
	}

	static double rotorVortexRingBuffetEnvelope(double descentRatio) {
		if (!Double.isFinite(descentRatio)) {
			return 0.0;
		}

		double earlyDigitizedShoulder = 0.42
				* smoothStep(0.45, 0.80, descentRatio)
				* (1.0 - smoothStep(0.96, 1.18, descentRatio));
		double peakDigitizedBand = smoothStep(0.78, 1.20, descentRatio)
				* (1.0 - smoothStep(1.36, 1.95, descentRatio));
		double deepDescentShoulder = 0.30
				* smoothStep(1.46, 1.76, descentRatio)
				* (1.0 - smoothStep(2.15, 2.65, descentRatio));
		return MathUtil.clamp(
				Math.max(earlyDigitizedShoulder, Math.max(peakDigitizedBand, deepDescentShoulder)),
				0.0,
				1.0
		);
	}

	static double rotorVortexRingBuffetFrequencyHertz(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double vortexRingStateIntensity,
			double descentRatio
	) {
		double absOmega = Math.abs(omegaRadiansPerSecond);
		double vrs = MathUtil.clamp(vortexRingStateIntensity, 0.0, 1.0);
		if (rotor == null || !Double.isFinite(absOmega) || absOmega <= 1.0e-6 || vrs <= 1.0e-6) {
			return 0.0;
		}

		double revolutionsPerSecond = absOmega / (2.0 * Math.PI);
		double peakBuffetBand = rotorVortexRingBuffetEnvelope(descentRatio);
		double weakState = 1.0 - smoothStep(0.35, 0.90, vrs);
		double highDescentRelease = smoothStep(1.55, 2.25, descentRatio);
		double characteristicRevolutions = 20.0
				+ 18.0 * (1.0 - peakBuffetBand)
				+ 8.0 * weakState
				+ 12.0 * highDescentRelease;
		characteristicRevolutions = MathUtil.clamp(characteristicRevolutions, 20.0, 50.0);
		return revolutionsPerSecond / characteristicRevolutions;
	}

	static double rotorVortexRingMeanThrustLoss(RotorSpec rotor, double vortexRingStateIntensity) {
		double vrs = MathUtil.clamp(vortexRingStateIntensity, 0.0, 1.0);
		if (vrs <= 1.0e-6 || rotor.axialFlowThrustLossCoefficient() <= 0.0) {
			return 0.0;
		}

		double peakLoss = MathUtil.clamp(2.06 * rotor.axialFlowThrustLossCoefficient(), 0.0, 0.33);
		return peakLoss * vrs;
	}

	private static double rotorAerodynamicOmegaRadiansPerSecond(
			RotorSpec rotor,
			double motorOmegaRadiansPerSecond,
			Vec3 bodyAngularVelocity
	) {
		double motorOmega = Math.max(0.0, motorOmegaRadiansPerSecond);
		Vec3 bodyRates = bodyAngularVelocity == null || !bodyAngularVelocity.isFinite()
				? Vec3.ZERO
				: bodyAngularVelocity;
		double axialBodyRate = bodyRates.dot(rotorAxisBody(rotor));
		double spinDirection = rotor.spinDirection() >= 0 ? 1.0 : -1.0;
		double signedBladeRate = spinDirection * motorOmega + axialBodyRate;
		return MathUtil.clamp(
				Math.abs(signedBladeRate),
				0.0,
				rotor.maxOmegaRadiansPerSecond() * 1.18
		);
	}

	private static double rotorTipSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
	}

	private static double rotorPitchSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.max(0.5, Math.abs(omegaRadiansPerSecond) * rotor.bladePitchMeters() / (2.0 * Math.PI));
	}

	private static double rotorBladePitchRatio(RotorSpec rotor) {
		return MathUtil.clamp(
				rotor.bladePitchToDiameterRatio() / RotorSpec.DEFAULT_BLADE_PITCH_TO_DIAMETER_RATIO,
				0.25,
				2.50
		);
	}

	private BladeElementAerodynamics updateRotorBladeElementAerodynamics(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond,
			double dtSeconds
	) {
		BladeElementAerodynamics target = calculateSteadyRotorBladeElementAerodynamics(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond
		);
		if (dtSeconds <= 0.0) {
			setRotorBladeElementState(index, target);
			return target;
		}

		double previousStall = rotorBladeElementStallIntensity[index];
		double previousThrustScale = rotorBladeElementThrustScale[index];
		double previousLoadFactor = rotorBladeElementLoadFactor[index];
		double previousVibration = rotorBladeElementVibration[index];
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double bladePeriodSeconds = Math.abs(omegaRadiansPerSecond) <= 1.0e-6
				? 0.040
				: 2.0 * Math.PI / Math.abs(omegaRadiansPerSecond);
		double previousSeparation = Math.max(previousStall, Math.max(0.0, 1.0 - previousThrustScale));
		double targetSeparation = Math.max(target.stallIntensity(), Math.max(0.0, 1.0 - target.thrustScale()));
		double buildTimeConstant = MathUtil.clamp(
				Math.max(0.020, 3.1 * bladePeriodSeconds) * Math.sqrt(radiusScale) / (0.78 + 0.52 * spinRatio),
				0.015,
				0.095
		);
		double recoveryTimeConstant = MathUtil.clamp(
				Math.max(0.060, 8.0 * bladePeriodSeconds) * Math.sqrt(radiusScale) / (0.72 + 0.30 * spinRatio),
				0.050,
				0.210
		);
		double timeConstant = targetSeparation > previousSeparation ? buildTimeConstant : recoveryTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);

		double stall = previousStall + (target.stallIntensity() - previousStall) * alpha;
		double thrustScale = previousThrustScale + (target.thrustScale() - previousThrustScale) * alpha;
		double loadFactor = previousLoadFactor + (target.loadFactor() - previousLoadFactor) * alpha;
		double vibration = previousVibration + (target.vibration() - previousVibration) * alpha;
		if (targetSeparation <= 1.0e-6 && Math.max(stall, Math.max(0.0, 1.0 - thrustScale)) < 1.0e-5) {
			stall = 0.0;
			thrustScale = 1.0;
			loadFactor = 0.0;
			vibration = 0.0;
		}

		BladeElementAerodynamics filtered = new BladeElementAerodynamics(
				target.angleOfAttackRadians(),
				MathUtil.clamp(stall, 0.0, 1.0),
				MathUtil.clamp(thrustScale, 0.74, 1.03),
				MathUtil.clamp(loadFactor, -0.09, 0.24),
				MathUtil.clamp(vibration, 0.0, 0.12)
		);
		setRotorBladeElementState(index, filtered);
		return filtered;
	}

	private void setRotorBladeElementState(int index, BladeElementAerodynamics aerodynamics) {
		rotorBladeElementStallIntensity[index] = aerodynamics.stallIntensity();
		rotorBladeElementThrustScale[index] = aerodynamics.thrustScale();
		rotorBladeElementLoadFactor[index] = aerodynamics.loadFactor();
		rotorBladeElementVibration[index] = aerodynamics.vibration();
	}

	private static BladeElementAerodynamics calculateSteadyRotorBladeElementAerodynamics(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.08) {
			return BladeElementAerodynamics.IDLE;
		}

		double stationRadius = rotor.radiusMeters() * RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION;
		double tangentialSpeed = Math.max(0.5, Math.abs(omegaRadiansPerSecond) * stationRadius);
		double geometricPitchAngle = rotor.geometricBladePitchAngleRadians();
		double inducedInflow = Math.max(0.0, inducedVelocityMetersPerSecond) * (0.28 + 0.32 * spinRatio);
		double axialInflow = rotorAxialVelocity(rotor, relativeAirVelocityBody) + inducedInflow;
		double inflowAngle = Math.atan2(axialInflow, tangentialSpeed);
		double angleOfAttack = MathUtil.clamp(
				geometricPitchAngle - inflowAngle,
				Math.toRadians(-45.0),
				Math.toRadians(45.0)
		);

		double loadedRotor = smoothStep(0.14, 0.52, spinRatio);
		double positiveStall = smoothStep(Math.toRadians(18.0), Math.toRadians(32.0), angleOfAttack) * loadedRotor;
		double underloadedBlade = smoothStep(Math.toRadians(3.0), Math.toRadians(15.0), -angleOfAttack) * loadedRotor;
		double optimalAngle = Math.toRadians(8.0);
		double offDesignLoss = smoothStep(Math.toRadians(10.0), Math.toRadians(24.0), Math.abs(angleOfAttack - optimalAngle));
		double efficientAoA = smoothStep(Math.toRadians(2.0), Math.toRadians(7.0), angleOfAttack)
				* (1.0 - smoothStep(Math.toRadians(10.0), Math.toRadians(15.0), angleOfAttack));
		double thrustScale = MathUtil.clamp(
				1.0 + 0.015 * efficientAoA - 0.10 * underloadedBlade - 0.12 * positiveStall - 0.03 * offDesignLoss,
				0.74,
				1.03
		);
		double loadFactor = MathUtil.clamp(
				loadedRotor * (0.05 * MathUtil.clamp((angleOfAttack - optimalAngle) / Math.toRadians(12.0), -0.70, 1.40)
						+ 0.12 * positiveStall
						- 0.035 * underloadedBlade),
				-0.09,
				0.24
		);
		double vibration = MathUtil.clamp(0.08 * positiveStall * spinRatio, 0.0, 0.12);
		return new BladeElementAerodynamics(angleOfAttack, positiveStall, thrustScale, loadFactor, vibration);
	}

	private record BladeElementAerodynamics(
			double angleOfAttackRadians,
			double stallIntensity,
			double thrustScale,
			double loadFactor,
			double vibration
	) {
		private static final BladeElementAerodynamics IDLE = new BladeElementAerodynamics(0.0, 0.0, 1.0, 0.0, 0.0);
	}

	private BladeDissymmetryAerodynamics updateRotorBladeDissymmetryAerodynamics(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double baseThrustNewtons,
			double dtSeconds
	) {
		BladeDissymmetryAerodynamics target = calculateSteadyRotorBladeDissymmetryAerodynamics(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				baseThrustNewtons
		);
		if (dtSeconds <= 0.0) {
			setRotorBladeDissymmetryState(index, target);
			return target;
		}

		double previousIntensity = rotorBladeDissymmetryIntensity[index];
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double advancingFlow = smoothStep(0.06, 0.42, advanceRatio);
		double bladePeriodSeconds = Math.abs(omegaRadiansPerSecond) <= 1.0e-6
				? 0.040
				: 2.0 * Math.PI / Math.abs(omegaRadiansPerSecond);
		double buildTimeConstant = MathUtil.clamp(
				Math.max(0.011, 2.2 * bladePeriodSeconds) * Math.sqrt(radiusScale) / (0.82 + 0.28 * spinRatio + 0.20 * advancingFlow),
				0.007,
				0.052
		);
		double releaseTimeConstant = MathUtil.clamp(
				Math.max(0.018, 3.3 * bladePeriodSeconds) * Math.sqrt(radiusScale) / (0.78 + 0.22 * spinRatio),
				0.013,
				0.090
		);
		double timeConstant = target.intensity() > previousIntensity ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double intensity = previousIntensity + (target.intensity() - previousIntensity) * alpha;
		double thrustScale = rotorBladeDissymmetryThrustScale[index]
				+ (target.thrustScale() - rotorBladeDissymmetryThrustScale[index]) * alpha;
		double loadFactor = rotorBladeDissymmetryLoadFactor[index]
				+ (target.loadFactor() - rotorBladeDissymmetryLoadFactor[index]) * alpha;
		double vibration = rotorBladeDissymmetryVibration[index]
				+ (target.vibration() - rotorBladeDissymmetryVibration[index]) * alpha;
		double reverseFlowInboardFraction = rotorBladeDissymmetryReverseFlowInboardFraction[index]
				+ (target.reverseFlowInboardFraction() - rotorBladeDissymmetryReverseFlowInboardFraction[index]) * alpha;

		if (target.intensity() <= 1.0e-6 && intensity < 1.0e-5) {
			intensity = 0.0;
			thrustScale = 1.0;
			loadFactor = 0.0;
			vibration = 0.0;
		}
		BladeDissymmetryAerodynamics filtered = new BladeDissymmetryAerodynamics(
				MathUtil.clamp(intensity, 0.0, 1.0),
				MathUtil.clamp(thrustScale, 0.86, 1.0),
				MathUtil.clamp(loadFactor, 0.0, 0.22),
				MathUtil.clamp(vibration, 0.0, 0.12),
				MathUtil.clamp(reverseFlowInboardFraction, 0.0, 1.0)
		);
		setRotorBladeDissymmetryState(index, filtered);
		return filtered;
	}

	private void setRotorBladeDissymmetryState(int index, BladeDissymmetryAerodynamics aerodynamics) {
		rotorBladeDissymmetryIntensity[index] = aerodynamics.intensity();
		rotorBladeDissymmetryThrustScale[index] = aerodynamics.thrustScale();
		rotorBladeDissymmetryLoadFactor[index] = aerodynamics.loadFactor();
		rotorBladeDissymmetryVibration[index] = aerodynamics.vibration();
		rotorBladeDissymmetryReverseFlowInboardFraction[index] = aerodynamics.reverseFlowInboardFraction();
	}

	private static BladeDissymmetryAerodynamics calculateSteadyRotorBladeDissymmetryAerodynamics(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double baseThrustNewtons
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		if (spinRatio <= 0.10 || transverseSpeed <= 0.25) {
			return BladeDissymmetryAerodynamics.IDLE;
		}

		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double thrustFraction = MathUtil.clamp(baseThrustNewtons / Math.max(1.0e-6, rotor.maxThrustNewtons()), 0.0, 1.25);
		double loadedRotor = smoothStep(0.16, 0.55, spinRatio) * (0.35 + 0.65 * smoothStep(0.05, 0.45, thrustFraction));
		double reverseFlowInboardFraction = MathUtil.clamp(advanceRatio, 0.0, 1.0);
		double reverseFlowSeverity = smoothStep(0.36, 0.82, reverseFlowInboardFraction) * loadedRotor;
		double liftDissymmetry = smoothStep(0.08, 0.34, advanceRatio) * loadedRotor;
		double retreatingBladeStall = smoothStep(0.42, 0.82, advanceRatio) * loadedRotor;
		double intensity = MathUtil.clamp(liftDissymmetry + 0.35 * retreatingBladeStall, 0.0, 1.0);
		double thrustScale = MathUtil.clamp(
				1.0 - 0.025 * liftDissymmetry - 0.075 * retreatingBladeStall - 0.020 * reverseFlowSeverity,
				0.86,
				1.0
		);
		double loadFactor = MathUtil.clamp(
				0.035 * liftDissymmetry + 0.13 * retreatingBladeStall + 0.050 * reverseFlowSeverity,
				0.0,
				0.22
		);
		double vibration = MathUtil.clamp(
				0.025 * liftDissymmetry + 0.075 * retreatingBladeStall + 0.030 * reverseFlowSeverity,
				0.0,
				0.12
		);
		return new BladeDissymmetryAerodynamics(
				intensity,
				thrustScale,
				loadFactor,
				vibration,
				reverseFlowInboardFraction
		);
	}

	private record BladeDissymmetryAerodynamics(
			double intensity,
			double thrustScale,
			double loadFactor,
			double vibration,
			double reverseFlowInboardFraction
	) {
		private static final BladeDissymmetryAerodynamics IDLE = new BladeDissymmetryAerodynamics(0.0, 1.0, 0.0, 0.0, 0.0);
	}

	private static double rotorTipMach(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double ambientTemperatureCelsius
	) {
		double rotationalTipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		double axialSpeed = Math.abs(rotorAxialVelocity(rotor, relativeAirVelocityBody));
		double helicalTipSpeed = Math.sqrt(
				rotationalTipSpeed * rotationalTipSpeed
						+ 0.25 * transverseSpeed * transverseSpeed
						+ 0.16 * axialSpeed * axialSpeed
		);
		return MathUtil.clamp(
				helicalTipSpeed / Math.max(1.0, DroneEnvironment.speedOfSoundMetersPerSecond(ambientTemperatureCelsius)),
				0.0,
				2.0
		);
	}

	private static double rotorCompressibilityIntensity(double rotorTipMach) {
		return smoothStep(0.46, 0.82, rotorTipMach);
	}

	private static double rotorCompressibilityThrustScale(double rotorTipMach) {
		return MathUtil.clamp(1.0 - 0.20 * rotorCompressibilityIntensity(rotorTipMach), 0.74, 1.0);
	}

	private static double rotorCompressibilityLoadFactor(double rotorTipMach) {
		return 0.42 * rotorCompressibilityIntensity(rotorTipMach);
	}

	private static double rotorCompressibilityReactionTorqueScale(double rotorTipMach) {
		double intensity = rotorCompressibilityIntensity(rotorTipMach);
		return MathUtil.clamp(1.0 + 0.32 * intensity + 0.10 * intensity * intensity, 1.0, 1.42);
	}

	private static double rotorCompressibilityVibration(RotorSpec rotor, double omegaRadiansPerSecond, double rotorTipMach) {
		double intensity = rotorCompressibilityIntensity(rotorTipMach);
		if (intensity <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.22 * intensity * spinRatio, 0.0, 0.34);
	}

	private static double rotorLowReynoldsLoss(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			double ambientTemperatureCelsius
	) {
		return rotorLowReynoldsLoss(rotor, omegaRadiansPerSecond, airDensityRatio, ambientTemperatureCelsius, 0.0);
	}

	private static double rotorLowReynoldsLoss(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		if (spinRatio <= 0.08) {
			return 0.0;
		}

		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.32, 3.0);
		double smallPropFactor = 1.0 - smoothStep(0.62, 0.96, radiusScale);
		if (smallPropFactor <= 1.0e-6) {
			return 0.0;
		}
		double reynoldsIndex = rotorLowReynoldsIndex(
				rotor,
				omegaRadiansPerSecond,
				airDensityRatio,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		double lowReynolds = 1.0 - smoothStep(0.52, 1.05, reynoldsIndex);
		return MathUtil.clamp(lowReynolds * smallPropFactor * smoothStep(0.10, 0.34, spinRatio), 0.0, 1.0);
	}

	private static double rotorReynoldsNumber(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			double ambientTemperatureCelsius
	) {
		return rotorReynoldsNumber(rotor, omegaRadiansPerSecond, airDensityRatio, ambientTemperatureCelsius, 0.0);
	}

	private static double rotorReynoldsNumber(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double stationSpeed = 0.75 * rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		if (stationSpeed <= 1.0e-9) {
			return 0.0;
		}
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.max(0.0, airDensityRatio);
		double dynamicViscosity = REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS
				* airDynamicViscosityRatio(ambientTemperatureCelsius, ambientHumidity);
		return MathUtil.clamp(
				density * stationSpeed * rotor.representativeBladeChordMeters() / Math.max(1.0e-9, dynamicViscosity),
				0.0,
				2.0e6
		);
	}

	private static double rotorLowReynoldsIndex(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			double ambientTemperatureCelsius
	) {
		return rotorLowReynoldsIndex(rotor, omegaRadiansPerSecond, airDensityRatio, ambientTemperatureCelsius, 0.0);
	}

	private static double rotorLowReynoldsIndex(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityRatio,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double densityViscosityRatio = MathUtil.clamp(
				Math.max(0.0, airDensityRatio) / airDynamicViscosityRatio(ambientTemperatureCelsius, ambientHumidity),
				0.20,
				1.90
		);
		double chordScale = MathUtil.clamp(
				rotor.representativeBladeChordMeters()
						/ (0.0635 * RotorSpec.DEFAULT_REPRESENTATIVE_CHORD_TO_RADIUS_RATIO),
				0.24,
				3.60
		);
		return densityViscosityRatio
				* chordScale
				* MathUtil.clamp(tipSpeed / 34.0, 0.0, 2.8);
	}

	private static double airDynamicViscosityRatio(double ambientTemperatureCelsius) {
		return airDynamicViscosityRatio(ambientTemperatureCelsius, 0.0);
	}

	private static double airDynamicViscosityRatio(double ambientTemperatureCelsius, double ambientHumidity) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = MOTOR_AMBIENT_TEMPERATURE_CELSIUS;
		}
		double temperatureKelvin = MathUtil.clamp(ambientTemperatureCelsius + 273.15, 233.15, 338.15);
		double ratio = Math.pow(temperatureKelvin / REFERENCE_AIR_TEMPERATURE_KELVIN, 1.5)
				* (REFERENCE_AIR_TEMPERATURE_KELVIN + AIR_SUTHERLAND_CONSTANT_KELVIN)
				/ (temperatureKelvin + AIR_SUTHERLAND_CONSTANT_KELVIN);
		return MathUtil.clamp(
				ratio * DroneEnvironment.moistAirDynamicViscosityMultiplier(ambientTemperatureCelsius, ambientHumidity),
				0.64,
				1.20
		);
	}

	private static double rotorLowReynoldsThrustScale(double lowReynoldsLoss) {
		double loss = MathUtil.clamp(lowReynoldsLoss, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.070 * loss, 0.92, 1.0);
	}

	private static double rotorLowReynoldsLoadFactor(double lowReynoldsLoss, double omegaRadiansPerSecond, RotorSpec rotor) {
		double loss = MathUtil.clamp(lowReynoldsLoss, 0.0, 1.0);
		if (loss <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return 0.060 * loss * (0.35 + 0.65 * spinRatio);
	}

	private static double rotorLowReynoldsVibration(double lowReynoldsLoss, double omegaRadiansPerSecond, RotorSpec rotor) {
		double loss = MathUtil.clamp(lowReynoldsLoss, 0.0, 1.0);
		if (loss <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.018 * loss * (0.25 + 0.75 * spinRatio), 0.0, 0.024);
	}

	private double updateRotorConingIntensity(
			int index,
			RotorSpec rotor,
			double thrustNewtons,
			double omegaRadiansPerSecond,
			double dtSeconds
	) {
		double targetConing = rotorConingTargetIntensity(rotor, thrustNewtons, omegaRadiansPerSecond);
		if (dtSeconds <= 0.0) {
			return rotorConingIntensity[index];
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double naturalFrequencyHertz = rotorConingNaturalFrequencyHertz(rotor, spinRatio);
		double angularFrequency = 2.0 * Math.PI * naturalFrequencyHertz;
		double dampingRatio = rotorConingDampingRatio(spinRatio);
		double coning = rotorConingIntensity[index];
		double velocity = rotorConingVelocity[index];
		int substeps = Math.max(1, (int) Math.ceil(dtSeconds / 0.00125));
		double subDt = dtSeconds / substeps;

		for (int step = 0; step < substeps; step++) {
			double acceleration = angularFrequency * angularFrequency * (targetConing - coning)
					- 2.0 * dampingRatio * angularFrequency * velocity;
			velocity = MathUtil.clamp(velocity + acceleration * subDt, -24.0, 24.0);
			coning += velocity * subDt;
			if (coning < 0.0) {
				coning = 0.0;
				velocity = Math.max(0.0, velocity) * 0.22;
			} else if (coning > 1.0) {
				coning = 1.0;
				velocity = Math.min(0.0, velocity) * 0.22;
			}
		}

		rotorConingIntensity[index] = MathUtil.clamp(coning, 0.0, 1.0);
		rotorConingVelocity[index] = velocity;
		return rotorConingIntensity[index];
	}

	static double rotorConingNaturalFrequencyHertz(RotorSpec rotor, double spinRatio) {
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double centrifugalStiffening = 1.0 + 0.42 * MathUtil.clamp(spinRatio, 0.0, 1.10);
		return MathUtil.clamp(
				30.0 * centrifugalStiffening / Math.sqrt(radiusScale),
				16.0,
				62.0
		);
	}

	static double rotorConingDampingRatio(double spinRatio) {
		return MathUtil.clamp(0.46 + 0.18 * MathUtil.clamp(spinRatio, 0.0, 1.10), 0.44, 0.72);
	}

	static double rotorConingTargetIntensity(RotorSpec rotor, double thrustNewtons, double omegaRadiansPerSecond) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double thrustFraction = MathUtil.clamp(thrustNewtons / Math.max(1.0e-6, rotor.maxThrustNewtons()), 0.0, 1.35);
		if (spinRatio <= 0.10 || thrustFraction <= 0.05) {
			return 0.0;
		}

		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.45, 2.60);
		double pitchFlexScale = MathUtil.clamp(1.0 / Math.sqrt(rotorBladePitchRatio(rotor)), 0.70, 1.35);
		double centrifugalStiffening = 0.45 + 0.55 * spinRatio;
		double load = smoothStep(0.34, 1.05, thrustFraction);
		double diskSize = smoothStep(0.78, 2.10, radiusScale);
		return MathUtil.clamp(load * (0.62 + 0.38 * diskSize) * pitchFlexScale / centrifugalStiffening, 0.0, 1.0);
	}

	static double rotorConingAngleRadians(RotorSpec rotor, double coningIntensity) {
		double coning = MathUtil.clamp(coningIntensity, 0.0, 1.0);
		if (coning <= 1.0e-6) {
			return 0.0;
		}
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.55, 2.60);
		double pitchFlexScale = MathUtil.clamp(1.0 / Math.sqrt(rotorBladePitchRatio(rotor)), 0.70, 1.35);
		double maximumConingDegrees = MathUtil.clamp(2.05 * Math.pow(radiusScale, -0.08) * pitchFlexScale, 1.20, 3.20);
		return Math.toRadians(maximumConingDegrees * coning);
	}

	static double rotorConingThrustScale(double coningIntensity) {
		double coning = MathUtil.clamp(coningIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.038 * coning, 0.94, 1.0);
	}

	static double rotorConingLoadFactor(double coningIntensity) {
		return 0.055 * MathUtil.clamp(coningIntensity, 0.0, 1.0);
	}

	static double rotorConingVibration(RotorSpec rotor, double omegaRadiansPerSecond, double coningIntensity) {
		double coning = MathUtil.clamp(coningIntensity, 0.0, 1.0);
		if (coning <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.020 * coning * (0.35 + 0.65 * spinRatio), 0.0, 0.026);
	}

	private static double rotorWindmillingLoadFactor(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double escOutput
	) {
		double reverseAxialSpeed = rotorWindmillingReverseAxialSpeed(rotor, relativeAirVelocityBody);
		double lowDrive = rotorWindmillingLowDriveFactor(escOutput);
		if (reverseAxialSpeed <= 1.0e-6 || lowDrive <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double airDrive = smoothStep(1.0, 12.0, reverseAxialSpeed);
		return MathUtil.clamp(0.22 * lowDrive * airDrive * (0.35 + 0.65 * spinRatio), 0.0, 0.32);
	}

	private static double rotorWindmillingVibration(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double escOutput
	) {
		double reverseAxialSpeed = rotorWindmillingReverseAxialSpeed(rotor, relativeAirVelocityBody);
		double lowDrive = rotorWindmillingLowDriveFactor(escOutput);
		if (reverseAxialSpeed <= 1.0e-6 || lowDrive <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double axialDrive = smoothStep(1.5, 14.0, reverseAxialSpeed);
		return MathUtil.clamp(0.13 * lowDrive * axialDrive * (0.30 + 0.70 * spinRatio), 0.0, 0.18);
	}

	private static double motorCommutationRippleVibration(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double commutationRippleIntensity,
			double torqueRippleNewtonMeters
	) {
		if (commutationRippleIntensity <= 1.0e-7) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double referenceTorque = Math.max(
				4.0e-4,
				rotor.yawTorquePerThrustMeter()
						* rotor.thrustCoefficient()
						* omegaRadiansPerSecond
						* omegaRadiansPerSecond
		);
		double torqueRatio = MathUtil.clamp(Math.abs(torqueRippleNewtonMeters) / referenceTorque, 0.0, 0.35);
		return MathUtil.clamp(
				0.014 * commutationRippleIntensity * (0.35 + 0.65 * spinRatio)
						+ 0.040 * torqueRatio * commutationRippleIntensity,
				0.0,
				0.055
		);
	}

	private RotorBladePassRipple updateRotorBladePassRipple(
			int index,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double aerodynamicLoadFactor,
			double rotorStallIntensity,
			BladeElementAerodynamics bladeElement,
			BladeDissymmetryAerodynamics bladeDissymmetry,
			double ambientDirtyAir,
			double wakeInterference,
			double flowObstruction,
			double surfaceScrapeIntensity,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0 || thrustNewtons <= 1.0e-6 || omegaRadiansPerSecond <= 1.0e-6) {
			return RotorBladePassRipple.IDLE;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.15);
		double activeSpin = smoothStep(0.08, 0.32, spinRatio);
		if (activeSpin <= 1.0e-7) {
			return RotorBladePassRipple.IDLE;
		}

		rotorBladePassPhases[index] = normalizeRadians(
				rotorBladePassPhases[index] + Math.abs(omegaRadiansPerSecond) * rotor.bladeCount() * dtSeconds
		);
		double phase = rotorBladePassPhases[index] + index * 0.61;
		double bladePassWave = Math.sin(phase)
				+ 0.28 * Math.sin(phase * 2.0 + 1.3)
				+ 0.16 * Math.sin(phase * 3.0 + 0.4);
		double load = MathUtil.clamp(aerodynamicLoadFactor <= 1.0e-6 ? 1.0 : aerodynamicLoadFactor, 0.35, 2.0);
		double loadRipple = 0.004 + 0.006 * smoothStep(0.70, 1.65, load);
		double bladeStallRipple = 0.016 * MathUtil.clamp(bladeElement.stallIntensity(), 0.0, 1.0)
				+ 0.012 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0);
		double dissymmetryRipple = 0.012 * MathUtil.clamp(bladeDissymmetry.intensity(), 0.0, 1.0);
		double dirtyAirRipple = 0.008 * MathUtil.clamp(ambientDirtyAir, 0.0, 1.8)
				+ 0.010 * MathUtil.clamp(wakeInterference, 0.0, 1.0)
				+ 0.012 * MathUtil.clamp(flowObstruction, 0.0, 1.0)
				+ 0.010 * MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0);
		double amplitude = activeSpin * MathUtil.clamp(
				loadRipple + bladeStallRipple + dissymmetryRipple + dirtyAirRipple,
				0.0,
				0.040
		);
		if (amplitude <= 1.0e-7) {
			return RotorBladePassRipple.IDLE;
		}

		double thrustScale = MathUtil.clamp(1.0 + amplitude * bladePassWave, 0.92, 1.08);
		double vibration = MathUtil.clamp(amplitude * (0.25 + 0.75 * Math.abs(bladePassWave)), 0.0, 0.075);
		return new RotorBladePassRipple(thrustScale, vibration, amplitude);
	}

	private RotorBladeStallBuffet updateRotorBladeStallBuffet(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double rotorStallIntensity,
			BladeElementAerodynamics bladeElement,
			BladeDissymmetryAerodynamics bladeDissymmetry,
			double dtSeconds
	) {
		double absOmega = Math.abs(omegaRadiansPerSecond);
		if (dtSeconds <= 0.0 || thrustNewtons <= 1.0e-6 || absOmega <= 1.0e-6 || rotor.stallThrustLossCoefficient() <= 1.0e-6) {
			return RotorBladeStallBuffet.IDLE;
		}

		double spinRatio = MathUtil.clamp(absOmega / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double highAdvance = smoothStep(0.40, 0.82, advanceRatio);
		double stallSource = MathUtil.clamp(
				0.62 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0)
						+ 0.46 * MathUtil.clamp(bladeElement.stallIntensity(), 0.0, 1.0)
						+ 0.34 * MathUtil.clamp(bladeDissymmetry.intensity(), 0.0, 1.0) * highAdvance,
				0.0,
				1.0
		);
		double activeRotor = smoothStep(0.18, 0.56, spinRatio);
		double stallAuthority = MathUtil.clamp(rotor.stallThrustLossCoefficient() / 0.34, 0.0, 1.35);
		double intensity = MathUtil.clamp(stallSource * activeRotor * highAdvance * stallAuthority, 0.0, 1.0);
		if (intensity <= 1.0e-6) {
			return RotorBladeStallBuffet.IDLE;
		}

		double buffetFrequencyHertz = 8.0 + 12.0 * highAdvance + 7.0 * intensity + 2.5 * spinRatio;
		rotorBladeStallBuffetPhases[index] = normalizeRadians(
				rotorBladeStallBuffetPhases[index] + 2.0 * Math.PI * buffetFrequencyHertz * dtSeconds
		);
		double phase = rotorBladeStallBuffetPhases[index] + index * 0.71;
		double wave = Math.sin(phase)
				+ 0.42 * Math.sin(phase * 0.53 + 1.4)
				+ 0.30 * Math.sin(phase * 1.91 + 0.2);
		double lossWave = 0.5 + 0.5 * Math.tanh(wave * 0.90);
		double thrustLossAmplitude = intensity * MathUtil.clamp(0.018 + 0.070 * highAdvance + 0.035 * intensity, 0.0, 0.13);
		double thrustScale = MathUtil.clamp(1.0 - thrustLossAmplitude * lossWave, 0.72, 1.02);

		Vec3 transverseVelocityBody = rotorTransverseVelocityBody(rotor, relativeAirVelocityBody);
		Vec3 axis = rotorAxisBody(rotor);
		Vec3 tangentA = transverseVelocityBody.lengthSquared() <= 1.0e-9
				? BODY_RIGHT.subtract(axis.multiply(BODY_RIGHT.dot(axis))).normalized()
				: transverseVelocityBody.normalized();
		if (tangentA.lengthSquared() <= 1.0e-9) {
			tangentA = BODY_FORWARD.subtract(axis.multiply(BODY_FORWARD.dot(axis))).normalized();
		}
		Vec3 tangentB = axis.cross(tangentA).normalized();
		Vec3 lateralForce = Vec3.ZERO;
		if (tangentA.lengthSquared() > 1.0e-9 && tangentB.lengthSquared() > 1.0e-9) {
			double rolloffBuffetThrust = rotor.maxThrustNewtons()
					* spinRatio
					* spinRatio
					* rotorForwardAdvanceThrustLoss(rotor, advanceRatio)
					* 0.24;
			double buffetReferenceThrust = Math.max(thrustNewtons, rolloffBuffetThrust);
			double lateralMagnitude = MathUtil.clamp(
					buffetReferenceThrust * intensity * (0.014 + 0.055 * highAdvance + 0.030 * intensity),
					0.0,
					rotor.maxThrustNewtons() * 0.12
			);
			double inPlanePulse = Math.sin(phase * 0.83 + 0.9);
			double retreatingPulse = Math.cos(phase * 1.17 + 0.35);
			lateralForce = tangentA.multiply(-inPlanePulse * lateralMagnitude)
					.add(tangentB.multiply(retreatingPulse * lateralMagnitude * (0.55 + 0.35 * highAdvance)));
		}

		double vibration = MathUtil.clamp(
				intensity * (0.018 + 0.070 * lossWave + 0.025 * Math.min(1.0, lateralForce.length() / Math.max(1.0e-6, thrustNewtons))),
				0.0,
				0.14
		);
		return new RotorBladeStallBuffet(thrustScale, lateralForce, vibration);
	}

	private RotorVortexRingBuffet updateRotorVortexRingBuffet(
			int index,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double vortexRingStateIntensity,
			double descentRatio,
			double dtSeconds
	) {
		double vrs = MathUtil.clamp(vortexRingStateIntensity, 0.0, 1.0);
		double absOmega = Math.abs(omegaRadiansPerSecond);
		if (dtSeconds <= 0.0 || thrustNewtons <= 1.0e-6 || vrs <= 1.0e-6 || absOmega <= 1.0e-6) {
			return RotorVortexRingBuffet.IDLE;
		}

		double spinRatio = MathUtil.clamp(absOmega / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double activeRotor = smoothStep(0.16, 0.52, spinRatio);
		double buffetEnvelope = rotorVortexRingBuffetEnvelope(descentRatio);
		double intensity = MathUtil.clamp(vrs * activeRotor * (0.28 + 0.72 * buffetEnvelope), 0.0, 1.0);
		if (intensity <= 1.0e-6) {
			return RotorVortexRingBuffet.IDLE;
		}

		double buffetFrequencyHertz = rotorVortexRingBuffetFrequencyHertz(
				rotor,
				absOmega,
				vrs,
				descentRatio
		);
		if (buffetFrequencyHertz <= 1.0e-6) {
			return RotorVortexRingBuffet.IDLE;
		}
		rotorVortexBuffetPhases[index] = normalizeRadians(
				rotorVortexBuffetPhases[index] + 2.0 * Math.PI * buffetFrequencyHertz * dtSeconds
		);
		double phase = rotorVortexBuffetPhases[index] + index * 0.83;
		double wave = Math.sin(phase)
				+ 0.38 * Math.sin(phase * 1.73 + 0.8)
				+ 0.22 * Math.sin(phase * 2.41 + index * 0.37);
		double thrustAmplitude = MathUtil.clamp(0.042 + 0.138 * intensity, 0.0, 0.20) * intensity;
		double thrustScale = MathUtil.clamp(1.0 + thrustAmplitude * wave, 0.64, 1.28);
		double thrustEnvelope = MathUtil.clamp(thrustAmplitude * 1.55, 0.0, 0.28);

		Vec3 axis = rotorAxisBody(rotor);
		Vec3 tangentA = BODY_RIGHT.subtract(axis.multiply(BODY_RIGHT.dot(axis))).normalized();
		if (tangentA.lengthSquared() <= 1.0e-9) {
			tangentA = BODY_FORWARD.subtract(axis.multiply(BODY_FORWARD.dot(axis))).normalized();
		}
		Vec3 tangentB = axis.cross(tangentA).normalized();
		Vec3 lateralForce = Vec3.ZERO;
		if (tangentA.lengthSquared() > 1.0e-9 && tangentB.lengthSquared() > 1.0e-9) {
			double lateralWaveA = Math.sin(phase * 0.77 + 1.2);
			double lateralWaveB = Math.cos(phase * 1.11 + 0.4);
			double lateralMagnitude = MathUtil.clamp(
					thrustNewtons * intensity * (0.026 + 0.072 * intensity),
					0.0,
					rotor.maxThrustNewtons() * 0.14
			);
			lateralForce = tangentA.multiply(lateralWaveA * lateralMagnitude)
					.add(tangentB.multiply(lateralWaveB * lateralMagnitude * 0.82));
		}

		double vibration = MathUtil.clamp(
				intensity * (0.026 + 0.108 * Math.abs(wave) + 0.034 * Math.min(1.0, lateralForce.length() / Math.max(1.0e-6, thrustNewtons))),
				0.0,
				0.20
		);
		return new RotorVortexRingBuffet(thrustScale, lateralForce, vibration, thrustEnvelope);
	}

	private Vec3 updateRotorImbalanceForce(
			int index,
			RotorSpec rotor,
			double rotorHealth,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double dtSeconds
	) {
		double imbalance = rotorEffectiveImbalanceIntensity(rotor, rotorHealth);
		double absOmega = Math.abs(omegaRadiansPerSecond);
		if (dtSeconds <= 0.0 || imbalance <= 1.0e-7 || absOmega <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double maxOmega = Math.max(1.0e-6, rotor.maxOmegaRadiansPerSecond());
		double spinRatio = MathUtil.clamp(absOmega / maxOmega, 0.0, 1.15);
		double activeSpin = smoothStep(0.06, 0.22, spinRatio);
		if (activeSpin <= 1.0e-7) {
			return Vec3.ZERO;
		}

		rotorImbalancePhases[index] = normalizeRadians(
				rotorImbalancePhases[index] + Math.signum(rotor.spinDirection()) * absOmega * dtSeconds
		);
		Vec3 axis = rotorAxisBody(rotor);
		Vec3 tangentA = BODY_RIGHT.subtract(axis.multiply(BODY_RIGHT.dot(axis))).normalized();
		if (tangentA.lengthSquared() <= 1.0e-9) {
			tangentA = BODY_FORWARD.subtract(axis.multiply(BODY_FORWARD.dot(axis))).normalized();
		}
		Vec3 tangentB = axis.cross(tangentA).normalized();
		if (tangentA.lengthSquared() <= 1.0e-9 || tangentB.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double thrustReference = Math.max(
				Math.max(0.0, thrustNewtons),
				rotor.maxThrustNewtons() * spinRatio * spinRatio * 0.18
		);
		double forceMagnitude = thrustReference
				* imbalance
				* activeSpin
				* spinRatio
				* spinRatio
				* (0.16 + 0.24 * spinRatio);
		forceMagnitude = MathUtil.clamp(forceMagnitude, 0.0, rotor.maxThrustNewtons() * 0.08);
		double phase = rotorImbalancePhases[index] + index * 1.37;
		return tangentA.multiply(Math.cos(phase) * forceMagnitude)
				.add(tangentB.multiply(Math.sin(phase) * forceMagnitude));
	}

	private static double rotorEffectiveImbalanceIntensity(RotorSpec rotor, double rotorHealth) {
		return PropellerDamageCalibration.effectiveImbalanceIntensity(rotor, rotorHealth);
	}

	private static double rotorAdvanceRatio(RotorSpec rotor, Vec3 relativeAirVelocityBody, double omegaRadiansPerSecond) {
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		return MathUtil.clamp(transverseSpeed / rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond), 0.0, 2.0);
	}

	private static double rotorPropellerAdvanceRatioJ(double rotorAdvanceRatio) {
		return MathUtil.clamp(Math.PI * rotorAdvanceRatio, 0.0, Math.PI * 2.0);
	}

	private static double rotorDamageVibration(RotorSpec rotor, double omegaRadiansPerSecond, double rotorHealth) {
		return PropellerDamageCalibration.damageVibrationIntensity(rotor, omegaRadiansPerSecond, rotorHealth);
	}

	private static double rotorImbalanceVibration(RotorSpec rotor, double omegaRadiansPerSecond, double rotorHealth) {
		return PropellerDamageCalibration.imbalanceVibrationIntensity(rotor, omegaRadiansPerSecond, rotorHealth);
	}

	private static double rotorHealthThrustScale(double rotorHealth) {
		return PropellerDamageCalibration.thrustScale(rotorHealth);
	}

	private static double rotorSurfaceScrapeTargetScale(double surfaceScrapeIntensity) {
		double scrape = MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.26 * scrape, 0.60, 1.0);
	}

	private static double rotorIcingTargetScale(double icingSeverity) {
		double powerScale = IcingRotorCalibration.icingPowerScale(icingSeverity);
		return MathUtil.clamp(1.0 / Math.sqrt(powerScale), 0.72, 1.0);
	}

	private static double surfaceScrapeDecay(double surfaceScrapeIntensity, double dtSeconds) {
		if (surfaceScrapeIntensity <= 1.0e-6 || dtSeconds <= 0.0) {
			return 0.0;
		}
		double alpha = MathUtil.expSmoothing(dtSeconds, 0.055);
		return Math.max(0.0, surfaceScrapeIntensity * (1.0 - alpha) - dtSeconds * 0.20);
	}

	private static double rotorSurfaceScrapeVibration(RotorSpec rotor, double omegaRadiansPerSecond, double surfaceScrapeIntensity) {
		double scrape = MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0);
		if (scrape <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(scrape * (0.04 + 0.42 * spinRatio), 0.0, 1.0);
	}

	private static double rotorFlowObstructionVibration(RotorSpec rotor, double omegaRadiansPerSecond, double obstruction) {
		obstruction = MathUtil.clamp(obstruction, 0.0, 1.0);
		if (obstruction <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double unsteadyFlow = Math.pow(obstruction, 1.35);
		return MathUtil.clamp(0.30 * unsteadyFlow * spinRatio, 0.0, 1.0);
	}

	private double updateRotorIcingSeverity(
			int index,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double precipitationWetnessIntensity,
			double ambientTemperatureCelsius,
			double dtSeconds
	) {
		double previous = MathUtil.clamp(state.rotorIcingSeverity(index), 0.0, 1.25);
		if (dtSeconds <= 0.0) {
			return previous;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.25);
		double accretionRate = IcingRotorCalibration.icingSeverityRatePerSecond(
				ambientTemperatureCelsius,
				precipitationWetnessIntensity,
				spinRatio
		);
		double recoveryRate = IcingRotorCalibration.icingRecoveryRatePerSecond(
				ambientTemperatureCelsius,
				precipitationWetnessIntensity,
				spinRatio
		);
		double severity = previous + accretionRate * dtSeconds - recoveryRate * dtSeconds;
		if (accretionRate <= 1.0e-9 && severity <= 5.0e-5) {
			severity = 0.0;
		}
		severity = MathUtil.clamp(severity, 0.0, 1.25);
		state.setRotorIcingSeverity(index, severity);
		return severity;
	}

	private static double frozenHumidityIcingWetness(DroneEnvironment environment) {
		if (environment == null || !environment.windSourceHasHumidity()) {
			return 0.0;
		}
		double adoptedHumidity = environment.adoptedWindSourceHumidity();
		if (adoptedHumidity <= 1.0e-9) {
			return 0.0;
		}
		return IcingRotorCalibration.freezingHumidityEquivalentWetness(
				environment.effectiveAmbientTemperatureCelsius(),
				adoptedHumidity
		);
	}

	private double updateRotorSurfaceWetness(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double waterImmersionIntensity,
			double precipitationWetnessIntensity,
			double dtSeconds
	) {
		double previous = MathUtil.clamp(rotorSurfaceWetness[index], 0.0, 1.0);
		double water = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		double rain = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		double immersionFilm = water <= 1.0e-6 ? 0.0 : Math.pow(water, 0.50);
		double rainFilm = rain <= 1.0e-6 ? 0.0 : 0.92 * Math.pow(rain, 0.82);
		double target = MathUtil.clamp(Math.max(immersionFilm, rainFilm), 0.0, 1.0);
		if (dtSeconds <= 0.0) {
			return previous;
		}

		double maxOmega = Math.max(1.0, rotor.maxOmegaRadiansPerSecond());
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / maxOmega, 0.0, 1.0);
		double timeConstant;
		if (target > previous) {
			double wettingDrive = Math.max(
					Math.pow(water, 0.42),
					0.45 * Math.pow(rain, 0.70)
			);
			timeConstant = MathUtil.clamp(0.035 + 0.230 * (1.0 - wettingDrive), 0.035, 0.265);
		} else {
			double axialSpeed = Math.abs(rotorAxialVelocity(rotor, relativeAirVelocityBody));
			double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
			double diskFlush = smoothStep(1.2, 12.0, Math.hypot(axialSpeed, transverseSpeed));
			double sheddingRate = 0.22 + 3.00 * Math.pow(spinRatio, 1.15) + 0.65 * diskFlush;
			double radiusScale = MathUtil.clamp(Math.sqrt(rotor.radiusMeters() / 0.0635), 0.65, 1.70);
			timeConstant = MathUtil.clamp(1.10 * radiusScale / sheddingRate, 0.20, 4.20);
		}

		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double wetness = previous + (target - previous) * alpha;
		if (target <= 1.0e-6 && wetness <= 5.0e-5) {
			wetness = 0.0;
		}
		rotorSurfaceWetness[index] = MathUtil.clamp(wetness, 0.0, 1.0);
		return rotorSurfaceWetness[index];
	}

	static double waterImmersionThrustScale(double waterImmersionIntensity) {
		double water = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.82 * Math.pow(water, 0.72), 0.12, 1.0);
	}

	static double precipitationThrustScale(double precipitationWetnessIntensity) {
		double wetness = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		// ICAS 2020 heavy-rain quadrotor CFD reports roughly 1.7-2.6% CT loss;
		// keep pure rain milder than water immersion while retaining wet-prop texture.
		return MathUtil.clamp(1.0 - 0.030 * Math.pow(wetness, 0.85), 0.96, 1.0);
	}

	static double rotorWaterLoadFactor(double waterImmersionIntensity) {
		double water = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		return 0.82 * Math.pow(water, 0.78);
	}

	static double rotorPrecipitationLoadFactor(double precipitationWetnessIntensity) {
		double wetness = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		return 0.13 * Math.pow(wetness, 1.15);
	}

	static double rotorWaterIngestionVibration(RotorSpec rotor, double omegaRadiansPerSecond, double waterImmersionIntensity) {
		double water = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		if (water <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.48 * Math.pow(water, 0.65) * spinRatio, 0.0, 1.0);
	}

	static double rotorPrecipitationVibration(RotorSpec rotor, double omegaRadiansPerSecond, double precipitationWetnessIntensity) {
		double wetness = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		if (wetness <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.10 * Math.pow(wetness, 1.05) * spinRatio, 0.0, 1.0);
	}

	static double rotorIcingVibration(RotorSpec rotor, double omegaRadiansPerSecond, double icingSeverity) {
		double severity = MathUtil.clamp(icingSeverity, 0.0, 1.25);
		if (severity <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.22 * Math.pow(severity, 0.82) * spinRatio, 0.0, 1.0);
	}

	private RotorWakeInterference updateRotorWakeInterference(
			boolean armed,
			Vec3 relativeAirVelocityBody,
			DroneEnvironment environment,
			double dtSeconds
	) {
		RotorWakeInterference target = calculateSteadyRotorWakeInterference(armed, relativeAirVelocityBody);
		int rotorCount = config.rotors().size();
		if (dtSeconds <= 0.0) {
			for (int i = 0; i < rotorCount; i++) {
				rotorWakeInterferenceIntensity[i] = target.intensity(i);
				rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond[i] = target.downwashVelocityBodyMetersPerSecond(i);
				rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond[i] = target.swirlVelocityBodyMetersPerSecond(i);
			}
			return target;
		}

		double[] intensity = new double[rotorCount];
		Vec3[] downwash = new Vec3[rotorCount];
		Vec3[] swirl = new Vec3[rotorCount];
		double crossflowSpeed = Math.hypot(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
		double crossflowFlush = smoothStep(2.5, 9.0, crossflowSpeed);
		double wakeCoherence = a4mcAblWakeCoherenceMultiplier(environment);
		double buildTimeScale = a4mcAblWakeBuildTimeScaleMultiplier(environment);
		double releaseTimeScale = a4mcAblWakeReleaseTimeScaleMultiplier(environment);
		for (int i = 0; i < rotorCount; i++) {
			RotorSpec rotor = config.rotors().get(i);
			double targetIntensity = MathUtil.clamp(target.intensity(i) * wakeCoherence, 0.0, 1.0);
			Vec3 targetDownwash = target.downwashVelocityBodyMetersPerSecond(i).multiply(wakeCoherence);
			Vec3 targetSwirl = target.swirlVelocityBodyMetersPerSecond(i).multiply(wakeCoherence);
			Vec3 previousDownwash = rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond[i];
			Vec3 previousSwirl = rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond[i];
			double previousIntensity = rotorWakeInterferenceIntensity[i];
			double previousFlowSpeed = Math.max(previousDownwash.length(), previousSwirl.length());
			double targetFlowSpeed = Math.max(targetDownwash.length(), targetSwirl.length());
			boolean building = targetIntensity > previousIntensity || targetFlowSpeed > previousFlowSpeed + 1.0e-6;
			double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.80);
			double buildTimeConstant = MathUtil.clamp(
					0.090 * Math.sqrt(radiusScale) * buildTimeScale / (0.72 + 0.62 * Math.max(targetIntensity, previousIntensity)),
					0.024,
					0.195
			);
			double releaseTimeConstant = MathUtil.clamp(
					(0.260 - 0.120 * crossflowFlush) * Math.sqrt(radiusScale) * releaseTimeScale,
					0.070,
					0.520
			);
			double timeConstant = building ? buildTimeConstant : releaseTimeConstant;
			double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);

			double filteredIntensity = previousIntensity + (targetIntensity - previousIntensity) * alpha;
			Vec3 filteredDownwash = previousDownwash.add(targetDownwash.subtract(previousDownwash).multiply(alpha));
			Vec3 filteredSwirl = previousSwirl.add(targetSwirl.subtract(previousSwirl).multiply(alpha));
			if (targetIntensity <= 1.0e-6 && filteredIntensity < 1.0e-5) {
				filteredIntensity = 0.0;
			}
			if (targetDownwash.lengthSquared() <= 1.0e-9 && filteredDownwash.lengthSquared() < 1.0e-8) {
				filteredDownwash = Vec3.ZERO;
			}
			if (targetSwirl.lengthSquared() <= 1.0e-9 && filteredSwirl.lengthSquared() < 1.0e-8) {
				filteredSwirl = Vec3.ZERO;
			}

			rotorWakeInterferenceIntensity[i] = MathUtil.clamp(filteredIntensity, 0.0, 1.0);
			rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond[i] = filteredDownwash.clamp(-12.0, 12.0);
			rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond[i] = clampRotorWakeSwirlVelocity(rotor, filteredSwirl);
			intensity[i] = rotorWakeInterferenceIntensity[i];
			downwash[i] = rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond[i];
			swirl[i] = rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond[i];
		}
		return new RotorWakeInterference(intensity, downwash, swirl);
	}

	private RotorWakeInterference calculateSteadyRotorWakeInterference(boolean armed, Vec3 relativeAirVelocityBody) {
		int rotorCount = config.rotors().size();
		double[] intensity = new double[rotorCount];
		Vec3[] downwash = new Vec3[rotorCount];
		Vec3[] swirl = new Vec3[rotorCount];
		Arrays.fill(downwash, Vec3.ZERO);
		Arrays.fill(swirl, Vec3.ZERO);
		if (!armed || rotorCount < 2) {
			return new RotorWakeInterference(intensity, downwash, swirl);
		}

		double crossflowSpeed = Math.hypot(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
		double crossflowFlush = 1.0 - smoothStep(2.0, 8.0, crossflowSpeed);

		for (int receiverIndex = 0; receiverIndex < rotorCount; receiverIndex++) {
			RotorSpec receiver = config.rotors().get(receiverIndex);
			Vec3 receiverPosition = receiver.positionBodyMeters();
			double receiverIntensity = 0.0;
			Vec3 receiverDownwash = Vec3.ZERO;
			Vec3 receiverSwirl = Vec3.ZERO;
			for (int sourceIndex = 0; sourceIndex < rotorCount; sourceIndex++) {
				if (sourceIndex == receiverIndex) {
					continue;
				}

				RotorSpec source = config.rotors().get(sourceIndex);
				Vec3 sourcePosition = source.positionBodyMeters();
				Vec3 sourceAxisBody = rotorAxisBody(source);
				Vec3 sourceToReceiver = receiverPosition.subtract(sourcePosition);
				double downstreamDistance = -sourceToReceiver.dot(sourceAxisBody);
				Vec3 lateralOffset = sourceToReceiver.add(sourceAxisBody.multiply(downstreamDistance));
				double lateralDistance = lateralOffset.length();
				double sourceSpinRatio = MathUtil.clamp(
						Math.abs(state.motorOmegaRadiansPerSecond(sourceIndex)) / source.maxOmegaRadiansPerSecond(),
						0.0,
						1.0
				);
				if (sourceSpinRatio <= 0.05) {
					continue;
				}

				double sourceInducedVelocity = Math.max(
						state.rotorInducedVelocityMetersPerSecond(sourceIndex),
						sourceSpinRatio * source.maxOmegaRadiansPerSecond() * source.radiusMeters() * 0.10
				);
				double axialWakeOverlap = rotorWakeAxisOverlap(source, receiver, downstreamDistance, lateralDistance);
				double axialLateralFactor = 0.0;
				if (axialWakeOverlap > 1.0e-6 && crossflowFlush > 1.0e-6) {
					double wakeRadius = source.radiusMeters() + Math.max(0.0, downstreamDistance) * 0.42;
					axialLateralFactor = 1.0 - smoothStep(wakeRadius * 0.35, wakeRadius + receiver.radiusMeters() * 0.85, lateralDistance);
				}
				double axialContributionFactor = Math.max(0.0, axialWakeOverlap * axialLateralFactor * crossflowFlush);
				RotorConvectedWake convectedWake = rotorConvectedWakeOverlap(
						source,
						receiver,
						relativeAirVelocityBody,
						sourceToReceiver,
						sourceAxisBody,
						sourceInducedVelocity
				);
				double wakeGeometryFactor = MathUtil.clamp(axialContributionFactor + convectedWake.overlap(), 0.0, 1.0);
				if (wakeGeometryFactor <= 1.0e-6) {
					continue;
				}

				double radiusMatch = MathUtil.clamp(source.radiusMeters() / Math.max(1.0e-6, receiver.radiusMeters()), 0.45, 1.45);
				double contribution = MathUtil.clamp(
						sourceSpinRatio * sourceSpinRatio * wakeGeometryFactor * (0.70 + 0.18 * radiusMatch),
						0.0,
						1.0
				);
				double effectiveMissDistance = convectedWake.overlap() > axialContributionFactor
						? convectedWake.missDistanceMeters()
						: lateralDistance;
				double coaxialCoreFactor = 1.0 - smoothStep(source.radiusMeters() * 0.08, source.radiusMeters() * 0.72, effectiveMissDistance);
				double swirlCapture = MathUtil.clamp(0.34 + 0.26 * coaxialCoreFactor + 0.14 * sourceSpinRatio, 0.0, 0.78);
				double swirlVelocity = contribution * sourceInducedVelocity * swirlCapture;
				Vec3 sourceArmBody = sourcePosition.subtract(config.centerOfMassOffsetBodyMeters());
				Vec3 swirlDirection = rotorWakeSwirlDirection(
						sourceAxisBody,
						convectedWake.overlap() > axialContributionFactor ? convectedWake.swirlOffsetBody() : lateralOffset,
						sourceArmBody,
						source.spinDirection()
				);
				receiverIntensity += contribution;
				receiverDownwash = receiverDownwash.add(sourceAxisBody.multiply(-contribution * sourceInducedVelocity));
				receiverSwirl = receiverSwirl.add(swirlDirection.multiply(swirlVelocity));
			}
			intensity[receiverIndex] = MathUtil.clamp(receiverIntensity, 0.0, 1.0);
			downwash[receiverIndex] = receiverDownwash.clamp(-12.0, 12.0);
			swirl[receiverIndex] = clampRotorWakeSwirlVelocity(receiver, receiverSwirl);
		}
		return new RotorWakeInterference(intensity, downwash, swirl);
	}

	private static Vec3 clampRotorWakeSwirlVelocity(RotorSpec receiver, Vec3 swirlVelocityBodyMetersPerSecond) {
		if (swirlVelocityBodyMetersPerSecond == null || swirlVelocityBodyMetersPerSecond.lengthSquared() <= 1.0e-12) {
			return Vec3.ZERO;
		}

		double limit = rotorWakeSwirlVelocityLimitMetersPerSecond(receiver);
		double length = swirlVelocityBodyMetersPerSecond.length();
		if (!Double.isFinite(length) || length <= limit) {
			return swirlVelocityBodyMetersPerSecond.isFinite() ? swirlVelocityBodyMetersPerSecond : Vec3.ZERO;
		}
		return swirlVelocityBodyMetersPerSecond.multiply(limit / Math.max(1.0e-9, length));
	}

	private static double rotorWakeSwirlVelocityLimitMetersPerSecond(RotorSpec receiver) {
		double radius = Math.max(1.0e-6, receiver.radiusMeters());
		double diskArea = Math.PI * radius * radius;
		double maxInducedVelocity = Math.sqrt(receiver.maxThrustNewtons() / Math.max(1.0e-6, 2.0 * SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * diskArea));
		double tipSpeed = receiver.maxOmegaRadiansPerSecond() * radius;
		return MathUtil.clamp(0.62 * maxInducedVelocity + 0.018 * tipSpeed, 4.0, 18.0);
	}

	private static Vec3 rotorWakeSwirlDirection(Vec3 sourceAxisBody, Vec3 lateralOffset, Vec3 sourceArmBody, int spinDirection) {
		Vec3 radial = projectOntoRotorDisk(lateralOffset, sourceAxisBody);
		if (radial.lengthSquared() <= 1.0e-9) {
			radial = projectOntoRotorDisk(sourceArmBody, sourceAxisBody);
		}
		if (radial.lengthSquared() <= 1.0e-9) {
			radial = projectOntoRotorDisk(sourceAxisBody.cross(BODY_FORWARD), sourceAxisBody);
		}
		if (radial.lengthSquared() <= 1.0e-9) {
			radial = projectOntoRotorDisk(sourceAxisBody.cross(BODY_RIGHT), sourceAxisBody);
		}

		Vec3 tangent = sourceAxisBody.cross(radial.normalized()).normalized();
		if (tangent.lengthSquared() <= 1.0e-9) {
			tangent = BODY_FORWARD;
		}
		return tangent.multiply(spinDirection >= 0 ? 1.0 : -1.0);
	}

	private static Vec3 projectOntoRotorDisk(Vec3 vector, Vec3 rotorAxisBody) {
		return vector.subtract(rotorAxisBody.multiply(vector.dot(rotorAxisBody)));
	}

	private static double rotorWakeAxisOverlap(RotorSpec source, RotorSpec receiver, double downstreamDistance, double lateralDistance) {
		double samePlaneTolerance = Math.min(source.radiusMeters(), receiver.radiusMeters()) * 0.20;
		if (downstreamDistance > samePlaneTolerance) {
			double maxUsefulDrop = Math.max(0.12, source.radiusMeters() * 5.5);
			return 1.0 - smoothStep(maxUsefulDrop * 0.20, maxUsefulDrop, downstreamDistance);
		}
		if (Math.abs(downstreamDistance) <= samePlaneTolerance) {
			double overlapDistance = source.radiusMeters() + receiver.radiusMeters();
			double diskOverlap = 1.0 - smoothStep(overlapDistance * 0.40, overlapDistance, lateralDistance);
			return 0.20 * diskOverlap;
		}
		return 0.0;
	}

	private static RotorConvectedWake rotorConvectedWakeOverlap(
			RotorSpec source,
			RotorSpec receiver,
			Vec3 relativeAirVelocityBody,
			Vec3 sourceToReceiverBody,
			Vec3 sourceAxisBody,
			double sourceInducedVelocityMetersPerSecond
	) {
		Vec3 transverseVelocityBody = projectOntoRotorDisk(relativeAirVelocityBody, sourceAxisBody);
		double transverseSpeed = transverseVelocityBody.length();
		if (transverseSpeed <= 1.0 || sourceInducedVelocityMetersPerSecond <= 1.0e-6) {
			return RotorConvectedWake.IDLE;
		}

		Vec3 wakeConvectionDirectionBody = transverseVelocityBody.multiply(-1.0 / transverseSpeed);
		Vec3 receiverLateralOffsetBody = projectOntoRotorDisk(sourceToReceiverBody, sourceAxisBody);
		double alongConvectionMeters = receiverLateralOffsetBody.dot(wakeConvectionDirectionBody);
		double sourceRadius = source.radiusMeters();
		double receiverRadius = receiver.radiusMeters();
		double wakeShedding = smoothStep(0.0004, 0.0028, source.diskDragCoefficient());
		if (wakeShedding <= 1.0e-6) {
			return RotorConvectedWake.IDLE;
		}
		if (alongConvectionMeters <= sourceRadius * 0.25) {
			return RotorConvectedWake.IDLE;
		}

		Vec3 crossTrackBody = receiverLateralOffsetBody.subtract(
				wakeConvectionDirectionBody.multiply(alongConvectionMeters)
		);
		double crossTrackDistance = crossTrackBody.length();
		double wakeAgeSeconds = alongConvectionMeters / Math.max(1.0, transverseSpeed);
		double wakeDropMeters = sourceInducedVelocityMetersPerSecond * wakeAgeSeconds;
		double receiverBelowSourceMeters = -sourceToReceiverBody.dot(sourceAxisBody);
		double axialMissMeters = receiverBelowSourceMeters - wakeDropMeters;
		double wakeRadius = sourceRadius + wakeDropMeters * 0.38 + alongConvectionMeters * 0.045;
		double maxUsefulAlong = Math.max(sourceRadius * 8.0, receiverRadius * 5.5);
		double alongCapture = smoothStep(sourceRadius * 0.35, Math.max(sourceRadius * 2.4, receiverRadius * 2.0), alongConvectionMeters)
				* (1.0 - smoothStep(maxUsefulAlong * 0.72, maxUsefulAlong, alongConvectionMeters));
		double crossTrackCapture = 1.0 - smoothStep(
				wakeRadius * 0.45,
				wakeRadius + receiverRadius * 0.95,
				crossTrackDistance
		);
		double axialCapture = 1.0 - smoothStep(
				receiverRadius * 0.45,
				receiverRadius * 1.75 + wakeRadius * 0.45,
				Math.abs(axialMissMeters)
		);
		double crossflowCapture = smoothStep(4.0, 14.0, transverseSpeed);
		double wakeRetention = MathUtil.clamp(
				sourceInducedVelocityMetersPerSecond / (sourceInducedVelocityMetersPerSecond + 0.30 * transverseSpeed),
				0.18,
				0.82
		);
		double overlap = MathUtil.clamp(
				2.35 * alongCapture * crossTrackCapture * axialCapture * crossflowCapture * wakeRetention * wakeShedding,
				0.0,
				1.0
		);
		if (overlap <= 1.0e-6) {
			return RotorConvectedWake.IDLE;
		}

		double missDistance = Math.sqrt(crossTrackDistance * crossTrackDistance + axialMissMeters * axialMissMeters);
		Vec3 swirlOffset = crossTrackBody.lengthSquared() > 1.0e-9
				? crossTrackBody
				: receiverLateralOffsetBody;
		return new RotorConvectedWake(overlap, missDistance, swirlOffset);
	}

	private static double rotorWakeInterferenceThrustScale(double interference) {
		return MathUtil.clamp(1.0 - 0.22 * MathUtil.clamp(interference, 0.0, 1.0), 0.72, 1.0);
	}

	private static Vec3 rotorArmBodyWithFlex(RotorSpec rotor, Vec3 nominalRotorArmBody, double armFlexIntensity) {
		double flex = MathUtil.clamp(armFlexIntensity, 0.0, 1.0);
		Vec3 radialDirection = horizontalRotorArmDirection(nominalRotorArmBody);
		if (flex <= 1.0e-6 || radialDirection.lengthSquared() <= 1.0e-9) {
			return nominalRotorArmBody;
		}

		return nominalRotorArmBody.add(BODY_ROTOR_AXIS.multiply(
				rotorArmFlexVerticalDeflectionMeters(rotor, nominalRotorArmBody, flex)
		));
	}

	private static RotorSpec rotorWithArmFlexedThrustAxis(RotorSpec rotor, Vec3 nominalRotorArmBody, double armFlexIntensity) {
		double flex = MathUtil.clamp(armFlexIntensity, 0.0, 1.0);
		Vec3 radialDirection = horizontalRotorArmDirection(nominalRotorArmBody);
		if (flex <= 1.0e-6 || radialDirection.lengthSquared() <= 1.0e-9) {
			return rotor;
		}

		double tiltRadians = rotorArmFlexTiltRadians(rotor, nominalRotorArmBody, flex);
		Vec3 axis = rotorAxisBody(rotor).subtract(radialDirection.multiply(tiltRadians)).normalized();
		if (axis.lengthSquared() <= 1.0e-9 || axis.y() <= 0.0) {
			return rotor;
		}
		return rotor.withThrustAxisBody(axis);
	}

	static double rotorArmFlexVerticalDeflectionMeters(RotorSpec rotor, Vec3 nominalRotorArmBody, double armFlexIntensity) {
		double flex = MathUtil.clamp(armFlexIntensity, 0.0, 1.0);
		if (flex <= 1.0e-6 || horizontalRotorArmDirection(nominalRotorArmBody).lengthSquared() <= 1.0e-9) {
			return 0.0;
		}
		double armLength = Math.max(0.08, Math.hypot(nominalRotorArmBody.x(), nominalRotorArmBody.z()));
		double radiusScale = MathUtil.clamp(Math.sqrt(rotor.radiusMeters() / 0.0635), 0.55, 2.20);
		return ROTOR_ARM_FLEX_VERTICAL_DEFLECTION_SCALE * flex * armLength * radiusScale;
	}

	static double rotorArmFlexTiltRadians(RotorSpec rotor, Vec3 nominalRotorArmBody, double armFlexIntensity) {
		double flex = MathUtil.clamp(armFlexIntensity, 0.0, 1.0);
		if (flex <= 1.0e-6 || horizontalRotorArmDirection(nominalRotorArmBody).lengthSquared() <= 1.0e-9) {
			return 0.0;
		}
		double armLength = Math.max(0.08, Math.hypot(nominalRotorArmBody.x(), nominalRotorArmBody.z()));
		double armScale = MathUtil.clamp(Math.sqrt(armLength / 0.24), 0.55, 2.10);
		double radiusScale = MathUtil.clamp(Math.sqrt(rotor.radiusMeters() / 0.0635), 0.55, 2.20);
		return ROTOR_ARM_FLEX_TILT_RADIANS * flex * armScale * radiusScale;
	}

	private static Vec3 horizontalRotorArmDirection(Vec3 rotorArmBody) {
		Vec3 horizontal = new Vec3(rotorArmBody.x(), 0.0, rotorArmBody.z());
		double length = horizontal.length();
		if (length <= 1.0e-9) {
			return Vec3.ZERO;
		}
		return horizontal.multiply(1.0 / length);
	}

	private double updateRotorArmFlexIntensity(
			int index,
			RotorSpec rotor,
			Vec3 forceBody,
			Vec3 torqueBody,
			double omegaRadiansPerSecond,
			double dtSeconds
	) {
		Vec3 previousForce = previousRotorForceBodyNewtons[index];
		Vec3 previousTorque = previousRotorTorqueBodyNewtonMeters[index];
		double maxThrust = Math.max(1.0e-6, rotor.maxThrustNewtons());
		double forceMagnitude = forceBody.length();
		double forceSlew = dtSeconds <= 1.0e-6
				? 0.0
				: forceBody.subtract(previousForce).length() / (maxThrust * dtSeconds);
		double torqueReference = Math.max(1.0e-4, maxThrust * Math.max(0.02, rotor.radiusMeters()));
		double torqueSlew = dtSeconds <= 1.0e-6
				? 0.0
				: torqueBody.subtract(previousTorque).length() / (torqueReference * dtSeconds);
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double target = rotorArmFlexTargetIntensity(rotor, forceMagnitude, forceSlew, torqueSlew, omegaRadiansPerSecond);
		double flex = integrateRotorArmFlexResonance(index, rotor, target, spinRatio, dtSeconds);
		previousRotorForceBodyNewtons[index] = forceBody;
		previousRotorTorqueBodyNewtonMeters[index] = torqueBody;
		return flex;
	}

	static double rotorArmFlexTargetIntensity(
			RotorSpec rotor,
			double forceMagnitudeNewtons,
			double normalizedForceSlewPerSecond,
			double normalizedTorqueSlewPerSecond,
			double omegaRadiansPerSecond
	) {
		double maxThrust = Math.max(1.0e-6, rotor.maxThrustNewtons());
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double steadyLoad = smoothStep(0.22, 0.95, forceMagnitudeNewtons / maxThrust);
		double snapLoad = smoothStep(4.0, 45.0, normalizedForceSlewPerSecond);
		double torsionalSnap = smoothStep(1.5, 28.0, normalizedTorqueSlewPerSecond);
		return MathUtil.clamp(
				(0.16 * steadyLoad + 0.26 * snapLoad + 0.18 * torsionalSnap) * smoothStep(0.05, 0.35, spinRatio),
				0.0,
				1.0
		);
	}

	private double integrateRotorArmFlexResonance(
			int index,
			RotorSpec rotor,
			double targetFlex,
			double spinRatio,
			double dtSeconds
	) {
		if (dtSeconds <= 0.0) {
			return rotorArmFlexIntensity[index];
		}

		Vec3 rotorArmBody = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
		double naturalFrequencyHertz = rotorArmFlexNaturalFrequencyHertz(rotor, rotorArmBody, spinRatio);
		double angularFrequency = Math.PI * 2.0 * naturalFrequencyHertz;
		double dampingRatio = rotorArmFlexDampingRatio(spinRatio);
		double flex = rotorArmFlexIntensity[index];
		double velocity = rotorArmFlexVelocity[index];
		int substeps = Math.max(1, (int) Math.ceil(dtSeconds / 0.0015));
		double subDt = dtSeconds / substeps;
		double target = MathUtil.clamp(targetFlex, 0.0, 1.0);

		for (int step = 0; step < substeps; step++) {
			double acceleration = angularFrequency * angularFrequency * (target - flex)
					- 2.0 * dampingRatio * angularFrequency * velocity;
			velocity = MathUtil.clamp(
					velocity + acceleration * subDt,
					-ROTOR_ARM_FLEX_MAX_VELOCITY_PER_SECOND,
					ROTOR_ARM_FLEX_MAX_VELOCITY_PER_SECOND
			);
			flex += velocity * subDt;
			if (flex < 0.0) {
				flex = 0.0;
				velocity = Math.max(0.0, velocity) * 0.18;
			} else if (flex > 1.0) {
				flex = 1.0;
				velocity = Math.min(0.0, velocity) * 0.18;
			}
		}

		rotorArmFlexIntensity[index] = MathUtil.clamp(flex, 0.0, 1.0);
		rotorArmFlexVelocity[index] = velocity;
		return rotorArmFlexIntensity[index];
	}

	static double rotorArmFlexNaturalFrequencyHertz(RotorSpec rotor, Vec3 rotorArmBody, double spinRatio) {
		Vec3 arm = rotorArmBody == null ? Vec3.ZERO : rotorArmBody;
		double armLength = Math.max(0.08, Math.hypot(arm.x(), arm.z()));
		double lengthScale = MathUtil.clamp(Math.sqrt(0.24 / armLength), 0.70, 1.45);
		double propScale = MathUtil.clamp(Math.sqrt(0.0635 / Math.max(0.025, rotor.radiusMeters())), 0.70, 1.35);
		double centrifugalStiffening = 1.0 + 0.28 * MathUtil.clamp(spinRatio, 0.0, 1.0);
		return ROTOR_ARM_FLEX_NATURAL_FREQUENCY_HERTZ
				* lengthScale
				* propScale
				* centrifugalStiffening;
	}

	static double rotorArmFlexDampingRatio(double spinRatio) {
		return ROTOR_ARM_FLEX_DAMPING_RATIO + 0.10 * MathUtil.clamp(spinRatio, 0.0, 1.0);
	}

	static double rotorArmFlexVibration(RotorSpec rotor, double omegaRadiansPerSecond, double armFlexIntensity) {
		armFlexIntensity = MathUtil.clamp(armFlexIntensity, 0.0, 1.0);
		if (armFlexIntensity <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.24 * armFlexIntensity * (0.25 + 0.75 * spinRatio), 0.0, 0.35);
	}

	private static double rotorWakeInterferenceVibration(RotorSpec rotor, double omegaRadiansPerSecond, double interference) {
		interference = MathUtil.clamp(interference, 0.0, 1.0);
		if (interference <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.24 * Math.pow(interference, 1.25) * spinRatio, 0.0, 1.0);
	}

	private static double rotorWakeSwirlVibration(RotorSpec rotor, double omegaRadiansPerSecond, double swirlVelocityMetersPerSecond) {
		if (swirlVelocityMetersPerSecond <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double tipSpeed = Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
		double swirlRatio = MathUtil.clamp(swirlVelocityMetersPerSecond / tipSpeed, 0.0, 0.45);
		return MathUtil.clamp(0.34 * Math.pow(swirlRatio, 0.82) * spinRatio, 0.0, 0.35);
	}

	private static double rotorWakeSwirlLoadFactor(RotorSpec rotor, double omegaRadiansPerSecond, double swirlVelocityMetersPerSecond) {
		if (swirlVelocityMetersPerSecond <= 1.0e-6) {
			return 0.0;
		}
		double tipSpeed = Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
		double swirlRatio = MathUtil.clamp(swirlVelocityMetersPerSecond / tipSpeed, 0.0, 0.30);
		return MathUtil.clamp(0.20 * swirlRatio, 0.0, 0.06);
	}

	private static double rotorInducedWakeLoadFactor(double carryoverIntensity) {
		carryoverIntensity = MathUtil.clamp(carryoverIntensity, 0.0, 1.0);
		return MathUtil.clamp(0.12 * Math.pow(carryoverIntensity, 0.85), 0.0, 0.14);
	}

	private static double rotorInducedWakeVibration(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double carryoverIntensity
	) {
		carryoverIntensity = MathUtil.clamp(carryoverIntensity, 0.0, 1.0);
		if (carryoverIntensity <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.09 * carryoverIntensity * spinRatio, 0.0, 0.10);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private Vec3 updateRotorFlappingForce(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			Vec3 diskWindGradientBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double dtSeconds
	) {
		Vec3 targetTiltBody = rotorFlappingTargetTiltBody(rotor, relativeAirVelocityBody, omegaRadiansPerSecond, thrustNewtons)
				.add(rotorDiskWindGradientTargetTiltBody(rotor, diskWindGradientBody, omegaRadiansPerSecond, thrustNewtons));
		Vec3 previousTiltBody = rotorFlappingTiltBody[index];
		double previousMagnitude = previousTiltBody.length();
		double targetMagnitude = targetTiltBody.length();
		double responseTimeConstant = targetMagnitude > previousMagnitude ? 0.026 : 0.050;
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		responseTimeConstant *= MathUtil.clamp(1.20 - 0.35 * spinRatio, 0.78, 1.20);
		double alpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, responseTimeConstant);
		Vec3 tiltBody = previousTiltBody.add(targetTiltBody.subtract(previousTiltBody).multiply(alpha));
		double magnitude = tiltBody.length();
		double maxTilt = Math.toRadians(18.0);
		if (magnitude > maxTilt) {
			tiltBody = tiltBody.multiply(maxTilt / magnitude);
			magnitude = maxTilt;
		}

		rotorFlappingTiltBody[index] = tiltBody;
		state.setRotorFlappingTiltRadians(index, magnitude);
		if (thrustNewtons <= 1.0e-6 || magnitude <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double verticalLoss = thrustNewtons * (1.0 - Math.sqrt(Math.max(0.0, 1.0 - magnitude * magnitude)));
		return tiltBody.multiply(thrustNewtons)
				.add(rotorAxisBody(rotor).multiply(-verticalLoss));
	}

	private static Vec3 rotorFlappingTargetTiltBody(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons
	) {
		Vec3 transverseVelocityBody = rotorTransverseVelocityBody(rotor, relativeAirVelocityBody);
		double transverseSpeed = transverseVelocityBody.length();
		if (transverseSpeed <= 1.0e-6 || thrustNewtons <= 1.0e-6 || rotor.flappingCoefficient() <= 0.0) {
			return Vec3.ZERO;
		}

		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double thrustFraction = MathUtil.clamp(thrustNewtons / rotor.maxThrustNewtons(), 0.0, 1.0);
		double advanceResponse = MathUtil.clamp(advanceRatio / 0.095, 0.0, 1.0);
		double diskLoadingResponse = MathUtil.clamp(0.72 + 0.28 * Math.sqrt(thrustFraction), 0.0, 1.0);
		double tilt = rotor.flappingCoefficient()
				* advanceResponse
				* diskLoadingResponse;
		Vec3 transverseUnit = transverseVelocityBody.multiply(1.0 / transverseSpeed);
		return transverseUnit.multiply(-tilt);
	}

	private static Vec3 rotorDiskWindGradientTargetTiltBody(
			RotorSpec rotor,
			Vec3 diskWindGradientBody,
			double omegaRadiansPerSecond,
			double thrustNewtons
	) {
		Vec3 gradientInDisk = rotorDiskWindGradientInPlaneBody(rotor, diskWindGradientBody);
		double gradientSpeed = gradientInDisk.length();
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (gradientSpeed <= 1.0e-6 || thrustNewtons <= 1.0e-6 || spinRatio <= 0.06) {
			return Vec3.ZERO;
		}

		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(gradientSpeed / Math.max(1.0, tipSpeed * 0.12), 0.0, 1.0);
		double thrustFraction = MathUtil.clamp(thrustNewtons / rotor.maxThrustNewtons(), 0.0, 1.0);
		double tilt = ROTOR_DISK_WIND_GRADIENT_MAX_TILT_RADIANS
				* smoothStep(0.03, 0.42, gradientRatio)
				* smoothStep(0.10, 0.55, spinRatio)
				* MathUtil.clamp(0.55 + 0.45 * Math.sqrt(thrustFraction), 0.0, 1.0);
		return gradientInDisk.multiply(1.0 / gradientSpeed).multiply(tilt);
	}

	private static double rotorDiskWindGradientThrustScale(
			RotorSpec rotor,
			Vec3 diskWindGradientBody,
			double omegaRadiansPerSecond
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.06) {
			return 1.0;
		}
		double gradientSpeed = rotorDiskWindGradientInPlaneBody(rotor, diskWindGradientBody).length();
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(gradientSpeed / Math.max(1.0, tipSpeed * 0.14), 0.0, 1.0);
		double loss = ROTOR_DISK_WIND_GRADIENT_MAX_THRUST_LOSS
				* smoothStep(0.04, 0.58, gradientRatio)
				* smoothStep(0.10, 0.55, spinRatio);
		return MathUtil.clamp(1.0 - loss, 1.0 - ROTOR_DISK_WIND_GRADIENT_MAX_THRUST_LOSS, 1.0);
	}

	private static double rotorDiskWindGradientLoadFactor(
			RotorSpec rotor,
			Vec3 diskWindGradientBody,
			double omegaRadiansPerSecond
	) {
		double gradientSpeed = rotorDiskWindGradientInPlaneBody(rotor, diskWindGradientBody).length();
		if (gradientSpeed <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(gradientSpeed / Math.max(1.0, tipSpeed * 0.16), 0.0, 1.0);
		return MathUtil.clamp(0.18 * Math.pow(gradientRatio, 0.85) * smoothStep(0.10, 0.55, spinRatio), 0.0, 0.18);
	}

	private static double rotorDiskWindGradientVibration(
			RotorSpec rotor,
			Vec3 diskWindGradientBody,
			double omegaRadiansPerSecond
	) {
		double gradientSpeed = rotorDiskWindGradientInPlaneBody(rotor, diskWindGradientBody).length();
		if (gradientSpeed <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(gradientSpeed / Math.max(1.0, tipSpeed * 0.12), 0.0, 1.0);
		return MathUtil.clamp(0.18 * Math.pow(gradientRatio, 0.80) * smoothStep(0.08, 0.50, spinRatio), 0.0, 0.18);
	}

	private static double rotorDiskWindGradientStallIntensity(
			RotorSpec rotor,
			Vec3 diskWindGradientBody,
			double omegaRadiansPerSecond
	) {
		double gradientSpeed = rotorDiskWindGradientInPlaneBody(rotor, diskWindGradientBody).length();
		if (gradientSpeed <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.08) {
			return 0.0;
		}
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(gradientSpeed / Math.max(1.0, tipSpeed * 0.10), 0.0, 1.0);
		return MathUtil.clamp(0.14 * smoothStep(0.10, 0.48, gradientRatio) * smoothStep(0.12, 0.50, spinRatio), 0.0, 0.14);
	}

	private static Vec3 rotorDiskWindGradientInPlaneBody(RotorSpec rotor, Vec3 diskWindGradientBody) {
		if (diskWindGradientBody == null || !diskWindGradientBody.isFinite()) {
			return Vec3.ZERO;
		}
		return projectOntoRotorDisk(diskWindGradientBody, rotorAxisBody(rotor)).clamp(-12.0, 12.0);
	}

	private static double rotorInflowSkewIntensity(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double translationalLiftIntensity,
			double rotorStallIntensity
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		if (spinRatio <= 0.12 || transverseSpeed <= 0.25 || translationalLiftIntensity <= 1.0e-6) {
			return 0.0;
		}

		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double advanceSkew = smoothStep(0.035, 0.24, advanceRatio);
		double loadedRotor = smoothStep(0.18, 0.60, spinRatio);
		double stallSoftening = 1.0 - 0.35 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0);
		return MathUtil.clamp(translationalLiftIntensity * advanceSkew * loadedRotor * stallSoftening, 0.0, 1.0);
	}

	private static double rotorAerodynamicLoadFactor(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond,
			double translationalLiftIntensity,
			double rotorStallIntensity,
			double vortexRingStateIntensity,
			double flowObstruction,
			double surfaceScrapeIntensity
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.05) {
			return 0.0;
		}

		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double inducedVelocity = Math.max(1.0, inducedVelocityMetersPerSecond);
		double axialVelocity = rotorAxialVelocity(rotor, relativeAirVelocityBody);
		double descentRatio = Math.max(0.0, -axialVelocity) / inducedVelocity;
		double climbRatio = Math.max(0.0, axialVelocity) / inducedVelocity;
		double cleanTransverseUnload = 0.30 * translationalLiftIntensity;
		double climbUnload = 0.12 * smoothStep(0.45, 1.35, climbRatio);
		double forwardAdvanceUnload = 0.22 * rotorForwardAdvanceThrustLoss(rotor, advanceRatio);
		double descentLoad = 0.28 * smoothStep(0.45, 1.35, descentRatio);
		double highAdvanceLoad = 0.16 * smoothStep(0.38, 0.82, advanceRatio);
		double stallLoad = 0.32 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0);
		double vortexLoad = 0.38 * MathUtil.clamp(vortexRingStateIntensity, 0.0, 1.0);
		double obstructionLoad = 0.32 * Math.pow(MathUtil.clamp(flowObstruction, 0.0, 1.0), 1.35);
		double scrapeLoad = 0.74 * Math.pow(MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0), 1.15);
		double load = 1.0
				+ descentLoad
				+ highAdvanceLoad
				+ stallLoad
				+ vortexLoad
				+ obstructionLoad
				+ scrapeLoad
				- cleanTransverseUnload
				- climbUnload
				- forwardAdvanceUnload;
		return MathUtil.clamp(load, 0.35, 2.0);
	}

	private static double rotorReactionTorqueScale(
			double rotorAerodynamicLoadFactor,
			double rotorStallIntensity,
			double vortexRingStateIntensity
	) {
		double loadFactor = rotorAerodynamicLoadFactor <= 1.0e-6
				? 1.0
				: MathUtil.clamp(rotorAerodynamicLoadFactor, 0.35, 1.75);
		double overload = Math.max(0.0, loadFactor - 1.0);
		double unload = Math.max(0.0, 1.0 - loadFactor);
		double stallDrag = 0.12 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0);
		double vortexDrag = 0.10 * MathUtil.clamp(vortexRingStateIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 + 0.28 * overload - 0.16 * unload + stallDrag + vortexDrag, 0.70, 1.45);
	}

	private static double rotorAmbientDirtyAirLoadFactor(RotorSpec rotor, double omegaRadiansPerSecond, double ambientDirtyAir) {
		if (ambientDirtyAir <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double activeRotor = smoothStep(0.08, 0.32, spinRatio);
		return MathUtil.clamp(0.075 * activeRotor * MathUtil.clamp(ambientDirtyAir, 0.0, 1.8), 0.0, 0.13);
	}

	private static Vec3 rotorInflowSkewTorque(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double thrustNewtons,
			double inflowSkewIntensity
	) {
		Vec3 transverseVelocityBody = rotorTransverseVelocityBody(rotor, relativeAirVelocityBody);
		double transverseSpeed = transverseVelocityBody.length();
		if (inflowSkewIntensity <= 1.0e-6 || transverseSpeed <= 1.0e-6 || thrustNewtons <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 transverseUnit = transverseVelocityBody.multiply(1.0 / transverseSpeed);
		double hubMomentCoefficient = 0.85 * rotor.flappingCoefficient()
				+ 0.35 * rotor.transverseFlowLiftCoefficient();
		if (hubMomentCoefficient <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double moment = thrustNewtons * rotor.radiusMeters() * hubMomentCoefficient * inflowSkewIntensity;
		Vec3 skewMoment = transverseUnit.cross(rotorAxisBody(rotor)).multiply(moment);
		double advancingBladeMoment = moment * 0.28 * rotor.spinDirection();
		Vec3 spinCoupledMoment = transverseUnit.multiply(advancingBladeMoment);
		return skewMoment.add(spinCoupledMoment);
	}

	private static Vec3 rotorBladeDissymmetryTorque(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double thrustNewtons,
			BladeDissymmetryAerodynamics bladeDissymmetry
	) {
		if (bladeDissymmetry == null
				|| bladeDissymmetry.intensity() <= 1.0e-6
				|| thrustNewtons <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 transverseVelocityBody = rotorTransverseVelocityBody(rotor, relativeAirVelocityBody);
		double transverseSpeed = transverseVelocityBody.length();
		if (transverseSpeed <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 transverseUnit = transverseVelocityBody.multiply(1.0 / transverseSpeed);
		Vec3 axisBody = rotorAxisBody(rotor);
		double imbalanceMoment = thrustNewtons
				* rotor.radiusMeters()
				* MathUtil.clamp(
						0.030 * bladeDissymmetry.intensity()
								+ 0.070 * bladeDissymmetry.loadFactor(),
						0.0,
						0.052
				);
		double spinCoupledMoment = imbalanceMoment * 0.34 * rotor.spinDirection();
		Vec3 diskMoment = transverseUnit.cross(axisBody).multiply(imbalanceMoment);
		Vec3 advancingBladeMoment = transverseUnit.multiply(spinCoupledMoment);
		return diskMoment.add(advancingBladeMoment).clamp(-0.055, 0.055);
	}

	private static Vec3 rotorWakeSwirlTorque(
			RotorSpec rotor,
			Vec3 wakeSwirlVelocityBody,
			double thrustNewtons,
			double omegaRadiansPerSecond,
			double wakeInterference
	) {
		if (wakeSwirlVelocityBody == null || thrustNewtons <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 axisBody = rotorAxisBody(rotor);
		Vec3 diskSwirlVelocity = projectOntoRotorDisk(wakeSwirlVelocityBody, axisBody);
		double swirlSpeed = diskSwirlVelocity.length();
		if (swirlSpeed <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double activeRotor = smoothStep(0.10, 0.36, spinRatio);
		if (activeRotor <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double tipSpeed = Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
		double swirlRatio = MathUtil.clamp(swirlSpeed / tipSpeed, 0.0, 0.38);
		double swirlCapture = smoothStep(0.010, 0.140, swirlRatio);
		double wakeCoupling = MathUtil.clamp(0.45 + 0.55 * wakeInterference, 0.45, 1.0);
		double momentCoefficient = MathUtil.clamp(
				(0.055 + 0.120 * MathUtil.clamp(wakeInterference, 0.0, 1.0)) * swirlCapture * activeRotor * wakeCoupling,
				0.0,
				0.085
		);
		if (momentCoefficient <= 1.0e-8) {
			return Vec3.ZERO;
		}

		double moment = thrustNewtons * rotor.radiusMeters() * momentCoefficient;
		Vec3 swirlUnit = diskSwirlVelocity.multiply(1.0 / swirlSpeed);
		Vec3 diskMoment = swirlUnit.cross(axisBody).multiply(moment);
		Vec3 spinCoupledMoment = swirlUnit.multiply(moment * 0.22 * rotor.spinDirection());
		return diskMoment.add(spinCoupledMoment).clamp(-0.080, 0.080);
	}

	private double updateRotorInducedInflow(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double baseThrustNewtons,
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample ctCpJReferenceSample,
			double airDensityRatio,
			double dtSeconds
	) {
		double hoverTargetInducedVelocity = targetRotorInducedVelocityMetersPerSecond(rotor, baseThrustNewtons, airDensityRatio);
		double momentumTargetInducedVelocity = rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
				ctCpJReferenceSample,
				hoverTargetInducedVelocity,
				baseThrustNewtons
		);
		double translationalLift = updateRotorTranslationalLiftIntensity(
				index,
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				hoverTargetInducedVelocity,
				dtSeconds
		);

		double liftCoefficientScale = MathUtil.clamp(rotor.transverseFlowLiftCoefficient() / 0.08, 0.0, 1.35);
		double cleanInflowReduction = 0.28 * translationalLift * liftCoefficientScale;
		double targetInducedVelocity = momentumTargetInducedVelocity * MathUtil.clamp(1.0 - cleanInflowReduction, 0.58, 1.0);
		double previousInducedVelocity = state.rotorInducedVelocityMetersPerSecond(index);
		double nominalHoverInducedVelocity = nominalHoverRotorInducedVelocityMetersPerSecond(rotor);
		double dynamicInflowTimeConstant = rotorDynamicInflowTimeConstantSeconds(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				targetInducedVelocity,
				previousInducedVelocity,
				nominalHoverInducedVelocity
		);
		state.setRotorDynamicInflowTimeConstantSeconds(index, dynamicInflowTimeConstant);
		double alpha = dynamicInflowTimeConstant <= 0.0
				? 1.0
				: MathUtil.expSmoothing(dtSeconds, dynamicInflowTimeConstant);
		double inducedVelocity = previousInducedVelocity + (targetInducedVelocity - previousInducedVelocity) * alpha;
		state.setRotorInducedVelocityMetersPerSecond(index, inducedVelocity);
		double wakeVelocity = updateRotorInducedWakeVelocity(
				index,
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				inducedVelocity,
				targetInducedVelocity,
				dtSeconds
		);

		if (rotor.inducedInflowLagCoefficient() <= 0.0 || targetInducedVelocity <= 1.0e-6) {
			rotorInducedWakeCarryoverIntensity[index] = 0.0;
			state.setRotorInducedLagThrustScale(index, 1.0);
			return 1.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double activeRotor = smoothStep(0.06, 0.32, spinRatio);
		double safeVelocity = Math.max(1.0e-6, Math.max(Math.max(targetInducedVelocity, previousInducedVelocity), wakeVelocity));
		double inflowDeficit = Math.max(0.0, (targetInducedVelocity - inducedVelocity) / targetInducedVelocity);
		double wakeCarryover = Math.max(0.0, (wakeVelocity - targetInducedVelocity) / safeVelocity);
		rotorInducedWakeCarryoverIntensity[index] = MathUtil.clamp(wakeCarryover * activeRotor, 0.0, 1.0);
		double thrustLoss = rotor.inducedInflowLagCoefficient()
				* (inflowDeficit + 0.35 * wakeCarryover + 0.18 * rotorInducedWakeCarryoverIntensity[index]);
		double thrustScale = MathUtil.clamp(1.0 - thrustLoss, 0.65, 1.0);
		state.setRotorInducedLagThrustScale(index, thrustScale);
		return thrustScale;
	}

	private double nominalHoverRotorInducedVelocityMetersPerSecond(RotorSpec rotor) {
		double nominalRotorThrust = config.massKg()
				* Math.max(1.0e-6, config.gravityMetersPerSecondSquared())
				/ Math.max(1.0, config.rotors().size());
		return targetRotorInducedVelocityMetersPerSecond(rotor, nominalRotorThrust, 1.0);
	}

	static double rotorDynamicInflowTimeConstantSeconds(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double targetInducedVelocityMetersPerSecond,
			double previousInducedVelocityMetersPerSecond,
			double nominalHoverInducedVelocityMetersPerSecond
	) {
		double baseTimeConstant = rotor.inducedInflowTimeConstantSeconds();
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		if (baseTimeConstant <= 0.0) {
			return 0.0;
		}
		if (targetInducedVelocityMetersPerSecond <= 1.0e-6
				&& previousInducedVelocityMetersPerSecond <= 1.0e-6
				&& spinRatio <= 0.02) {
			return 0.0;
		}

		double safeTargetInflow = Math.max(0.75, targetInducedVelocityMetersPerSecond);
		double safeNominalHoverInflow = Math.max(0.75, nominalHoverInducedVelocityMetersPerSecond);
		double inducedVelocityScale = MathUtil.clamp(safeNominalHoverInflow / safeTargetInflow, 0.48, 2.45);
		double targetRisingScale = targetInducedVelocityMetersPerSecond >= previousInducedVelocityMetersPerSecond
				? 0.88
				: 1.16;
		double advanceRatio = rotorAdvanceRatio(rotor, relativeAirVelocityBody, omegaRadiansPerSecond);
		double transverseFlush = smoothStep(0.06, 0.32, advanceRatio);
		double climbRatio = Math.max(0.0, rotorAxialVelocity(rotor, relativeAirVelocityBody)) / safeTargetInflow;
		double descentRatio = Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody)) / safeTargetInflow;
		double climbFlush = smoothStep(0.08, 0.55, climbRatio);
		double descendingWake = smoothStep(0.25, 1.15, descentRatio);
		double spinWakeScale = MathUtil.clamp(
				1.16 - 0.26 * smoothStep(0.20, 0.80, spinRatio),
				0.88,
				1.16
		);
		double wakeFlushScale = MathUtil.clamp(
				1.0 - 0.34 * transverseFlush - 0.16 * climbFlush + 0.28 * descendingWake,
				0.52,
				1.32
		);
		double wakeTransitTime = 2.0 * rotor.radiusMeters() / safeTargetInflow;
		double minimumTimeConstant = MathUtil.clamp(1.15 * wakeTransitTime, 0.006, baseTimeConstant * 0.75);
		double maximumTimeConstant = MathUtil.clamp(baseTimeConstant * 2.75, baseTimeConstant, 0.36);
		return MathUtil.clamp(
				baseTimeConstant * inducedVelocityScale * targetRisingScale * spinWakeScale * wakeFlushScale,
				minimumTimeConstant,
				maximumTimeConstant
		);
	}

	static double rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double fallbackTargetInducedVelocityMetersPerSecond,
			double baseThrustNewtons
	) {
		double fallback = Math.max(0.0, finiteOrDefault(fallbackTargetInducedVelocityMetersPerSecond, 0.0));
		if (!ctCpJRuntimeSampleAccepted(sample) || baseThrustNewtons <= 0.0) {
			return fallback;
		}
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				sample.dimensionalSample();
		double inducedVelocity = axialMomentumInducedVelocityMetersPerSecond(
				baseThrustNewtons,
				dimensional.airDensityKgPerCubicMeter(),
				dimensional.diskAreaSquareMeters(),
				dimensional.axialAdvanceSpeedMetersPerSecond()
		);
		return inducedVelocity > 0.0 ? inducedVelocity : fallback;
	}

	private double updateRotorInducedWakeVelocity(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond,
			double targetInducedVelocityMetersPerSecond,
			double dtSeconds
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double activeRotor = smoothStep(0.05, 0.25, spinRatio);
		double targetWakeVelocity = Math.max(
				inducedVelocityMetersPerSecond,
				targetInducedVelocityMetersPerSecond * (0.70 + 0.30 * activeRotor)
		) * activeRotor;
		if (dtSeconds <= 0.0) {
			rotorInducedWakeVelocityMetersPerSecond[index] = targetWakeVelocity;
			return rotorInducedWakeVelocityMetersPerSecond[index];
		}

		double previousWakeVelocity = rotorInducedWakeVelocityMetersPerSecond[index];
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double transverseFlush = smoothStep(1.0, 9.0, rotorTransverseSpeed(rotor, relativeAirVelocityBody));
		double climbFlush = smoothStep(1.0, 8.0, Math.max(0.0, rotorAxialVelocity(rotor, relativeAirVelocityBody)));
		double buildTimeConstant = MathUtil.clamp(
				0.030 * Math.sqrt(radiusScale) / (0.72 + 0.48 * spinRatio),
				0.014,
				0.075
		);
		double releaseTimeConstant = MathUtil.clamp(
				0.185 * Math.sqrt(radiusScale) / (0.68 + 0.82 * transverseFlush + 0.46 * climbFlush),
				0.060,
				0.340
		);
		double timeConstant = targetWakeVelocity > previousWakeVelocity ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double wakeVelocity = previousWakeVelocity + (targetWakeVelocity - previousWakeVelocity) * alpha;
		double maxWakeVelocity = targetRotorInducedVelocityMetersPerSecond(rotor, rotor.maxThrustNewtons(), 1.0) * 1.65;
		wakeVelocity = MathUtil.clamp(wakeVelocity, 0.0, Math.max(1.0, maxWakeVelocity));
		if (targetWakeVelocity <= 1.0e-6 && wakeVelocity < 1.0e-4) {
			wakeVelocity = 0.0;
		}
		rotorInducedWakeVelocityMetersPerSecond[index] = wakeVelocity;
		return wakeVelocity;
	}

	private double updateRotorTranslationalLiftIntensity(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double hoverTargetInducedVelocityMetersPerSecond,
			double dtSeconds
	) {
		double target = calculateSteadyRotorTranslationalLiftIntensity(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				hoverTargetInducedVelocityMetersPerSecond
		);
		if (dtSeconds <= 0.0) {
			state.setRotorTranslationalLiftIntensity(index, target);
			return target;
		}

		double previous = state.rotorTranslationalLiftIntensity(index);
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		double transverseFlush = smoothStep(1.0, 8.0, transverseSpeed);
		double buildTimeConstant = MathUtil.clamp(
				0.075 * Math.sqrt(radiusScale) / (0.78 + 0.36 * spinRatio + 0.24 * transverseFlush),
				0.024,
				0.155
		);
		double releaseTimeConstant = MathUtil.clamp(
				(0.180 - 0.080 * transverseFlush) * Math.sqrt(radiusScale) / (0.72 + 0.28 * spinRatio),
				0.055,
				0.280
		);
		double timeConstant = target > previous ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double translationalLift = previous + (target - previous) * alpha;
		if (target <= 1.0e-6 && translationalLift < 1.0e-5) {
			translationalLift = 0.0;
		}
		state.setRotorTranslationalLiftIntensity(index, translationalLift);
		return state.rotorTranslationalLiftIntensity(index);
	}

	private static double calculateSteadyRotorTranslationalLiftIntensity(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double hoverTargetInducedVelocityMetersPerSecond
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		if (spinRatio <= 0.12 || transverseSpeed <= 0.25 || hoverTargetInducedVelocityMetersPerSecond <= 1.0e-6) {
			return 0.0;
		}

		double tipSpeed = Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
		double advanceRatio = transverseSpeed / tipSpeed;
		double inducedRatio = transverseSpeed / Math.max(1.0, hoverTargetInducedVelocityMetersPerSecond);
		double descentSpeed = Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody));
		double descentRatio = descentSpeed / Math.max(1.0, hoverTargetInducedVelocityMetersPerSecond);
		double cleanDiskFlow = smoothStep(0.45, 1.45, inducedRatio);
		double highAdvanceFlow = smoothStep(0.025, 0.16, advanceRatio);
		double loadedRotor = smoothStep(0.16, 0.55, spinRatio);
		double descentWashPenalty = 1.0 - 0.55 * smoothStep(0.65, 1.50, descentRatio);
		return MathUtil.clamp(cleanDiskFlow * highAdvanceFlow * loadedRotor * descentWashPenalty, 0.0, 1.0);
	}

	static double targetRotorInducedVelocityMetersPerSecond(
			RotorSpec rotor,
			double baseThrustNewtons,
			double airDensityRatio
	) {
		if (baseThrustNewtons <= 0.0) {
			return 0.0;
		}

		double diskAreaMetersSquared = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double airDensity = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.max(0.2, airDensityRatio);
		return Math.sqrt(baseThrustNewtons / Math.max(1.0e-6, 2.0 * airDensity * diskAreaMetersSquared));
	}

	static double axialMomentumInducedVelocityMetersPerSecond(
			double thrustNewtons,
			double airDensityKgPerCubicMeter,
			double diskAreaMetersSquared,
			double axialAdvanceSpeedMetersPerSecond
	) {
		if (thrustNewtons <= 0.0
				|| airDensityKgPerCubicMeter <= 0.0
				|| diskAreaMetersSquared <= 0.0) {
			return 0.0;
		}
		double axialAdvanceSpeed = Double.isFinite(axialAdvanceSpeedMetersPerSecond)
				? Math.max(0.0, axialAdvanceSpeedMetersPerSecond)
				: 0.0;
		double diskTerm = 2.0 * thrustNewtons / (airDensityKgPerCubicMeter * diskAreaMetersSquared);
		return 0.5 * (Math.sqrt(axialAdvanceSpeed * axialAdvanceSpeed + diskTerm) - axialAdvanceSpeed);
	}

	private Vec3 calculateAirframeAerodynamicTorque(
			Vec3 relativeAirVelocityBody,
			Vec3 rotorWashDragForceBody,
			Vec3 airframeLiftForceBody,
			Vec3 airframeDragForceBody,
			DroneEnvironment environment,
			double airDensityRatio,
			double dtSeconds
	) {
		double speed = relativeAirVelocityBody.length();
		Vec3 pressureCenterTorque = calculateAirframePressureCenterTorque(
				relativeAirVelocityBody,
				rotorWashDragForceBody,
				airframeLiftForceBody,
				airframeDragForceBody,
				environment,
				airDensityRatio,
				dtSeconds
		);
		state.setAirframePressureCenterTorqueBodyNewtonMeters(pressureCenterTorque);
		if (speed < 1.0) {
			return pressureCenterTorque;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBody.y(), forwardReference);
		double sideSlip = Math.atan2(relativeAirVelocityBody.x(), forwardReference);
		double dynamicScale = airDensityRatio * speed * speed;
		double lateralRatio = relativeAirVelocityBody.x() / speed;
		double verticalRatio = relativeAirVelocityBody.y() / speed;

		double pitchTorque = -drag.z() * dynamicScale * angleOfAttack * 0.0025;
		double yawTorque = -drag.x() * dynamicScale * sideSlip * 0.0020;
		double rollTorque = -drag.x() * dynamicScale * lateralRatio * verticalRatio * 0.0015;
		Vec3 attitudeTorque = new Vec3(
				MathUtil.clamp(pitchTorque, -0.25, 0.25),
				MathUtil.clamp(yawTorque, -0.25, 0.25),
				MathUtil.clamp(rollTorque, -0.18, 0.18)
		);
		Vec3 separationBuffet = updateAirframeSeparatedFlowBuffetTorque(
				relativeAirVelocityBody,
				airDensityRatio,
				dtSeconds
		);
		return attitudeTorque.add(pressureCenterTorque).add(separationBuffet).clamp(-0.55, 0.55);
	}

	private Vec3 updateAirframeSeparatedFlowBuffetTorque(
			Vec3 relativeAirVelocityBody,
			double airDensityRatio,
			double dtSeconds
	) {
		double speed = relativeAirVelocityBody.length();
		if (speed < 3.0 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}

		double separation = effectiveAirframeSeparationIntensity(relativeAirVelocityBody);
		if (separation <= 1.0e-6) {
			return Vec3.ZERO;
		}

		airframeSeparationBuffetPhaseA = normalizeRadians(
				airframeSeparationBuffetPhaseA + dtSeconds * (5.5 + 0.36 * speed)
		);
		airframeSeparationBuffetPhaseB = normalizeRadians(
				airframeSeparationBuffetPhaseB + dtSeconds * (8.1 + 0.23 * speed)
		);
		Vec3 drag = config.bodyDragCoefficients();
		double dynamicScale = airDensityRatio * speed * speed;
		double exposedAreaScale = Math.sqrt(Math.max(0.0, (drag.x() + drag.y()) * Math.max(0.0, drag.z())));
		double buffetAuthority = MathUtil.clamp(
				dynamicScale * exposedAreaScale * Math.pow(separation, 1.35) * 0.0026,
				0.0,
				0.18
		);
		double pitchWave = Math.sin(airframeSeparationBuffetPhaseA)
				+ 0.34 * Math.sin(airframeSeparationBuffetPhaseB + 0.8);
		double yawWave = Math.sin(airframeSeparationBuffetPhaseB + 1.7)
				+ 0.28 * Math.sin(airframeSeparationBuffetPhaseA * 0.73 + 2.4);
		double rollWave = Math.sin(airframeSeparationBuffetPhaseA - airframeSeparationBuffetPhaseB + 0.5);
		double angleOfAttack = state.angleOfAttackRadians();
		double sideslip = state.sideslipRadians();
		double pitchBias = MathUtil.clamp(Math.abs(angleOfAttack) / Math.toRadians(75.0), 0.20, 1.0);
		double yawBias = MathUtil.clamp(Math.abs(sideslip) / Math.toRadians(75.0), 0.20, 1.0);
		double rollCoupling = MathUtil.clamp((Math.abs(angleOfAttack) + Math.abs(sideslip)) / Math.toRadians(120.0), 0.18, 1.0);
		return new Vec3(
				buffetAuthority * pitchWave * pitchBias,
				buffetAuthority * yawWave * yawBias,
				buffetAuthority * rollWave * 0.42 * rollCoupling
		).clamp(-0.20, 0.20);
	}

	private Vec3 calculateAirframePressureCenterTorque(
			Vec3 relativeAirVelocityBody,
			Vec3 rotorWashDragForceBody,
			Vec3 airframeLiftForceBody,
			Vec3 airframeDragForceBody,
			DroneEnvironment environment,
			double airDensityRatio,
			double dtSeconds
	) {
		Vec3 dynamicPressureCenterOffsetBody = updateDynamicPressureCenterOffsetBody(
				relativeAirVelocityBody,
				environment,
				dtSeconds
		);
		Vec3 momentArmBody = config.centerOfPressureOffsetBodyMeters()
				.add(dynamicPressureCenterOffsetBody)
				.subtract(config.centerOfMassOffsetBodyMeters());
		Vec3 airframeForceBody = airframeDragForceBody
				.add(airframeLiftForceBody)
				.add(rotorWashDragForceBody)
				.add(a4mcLocalVoxelRotorWashPressureCenterForceBody(dynamicPressureCenterOffsetBody, environment, airDensityRatio));
		if (momentArmBody.lengthSquared() <= 1.0e-12 || airframeForceBody.lengthSquared() <= 1.0e-12 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}
		return momentArmBody.cross(airframeForceBody).clamp(-0.45, 0.45);
	}

	private Vec3 updateDynamicPressureCenterOffsetBody(
			Vec3 relativeAirVelocityBody,
			DroneEnvironment environment,
			double dtSeconds
	) {
		Vec3 target = calculateSteadyDynamicPressureCenterOffsetBody(relativeAirVelocityBody, environment);
		if (dtSeconds <= 0.0) {
			dynamicPressureCenterOffsetBodyFiltered = target;
			return dynamicPressureCenterOffsetBodyFiltered;
		}

		double targetMagnitude = target.length();
		double previousMagnitude = dynamicPressureCenterOffsetBodyFiltered.length();
		double timeConstant = targetMagnitude > previousMagnitude ? 0.040 : 0.130;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		dynamicPressureCenterOffsetBodyFiltered = dynamicPressureCenterOffsetBodyFiltered
				.add(target.subtract(dynamicPressureCenterOffsetBodyFiltered).multiply(alpha))
				.clamp(-0.040, 0.040);
		if (targetMagnitude <= 1.0e-6 && dynamicPressureCenterOffsetBodyFiltered.lengthSquared() < 1.0e-8) {
			dynamicPressureCenterOffsetBodyFiltered = Vec3.ZERO;
		}
		return dynamicPressureCenterOffsetBodyFiltered;
	}

	private Vec3 calculateSteadyDynamicPressureCenterOffsetBody(
			Vec3 relativeAirVelocityBody,
			DroneEnvironment environment
	) {
		double speed = relativeAirVelocityBody.length();
		Vec3 a4mcLocalOffset = a4mcLocalVoxelPressureCenterOffsetBody(environment, speed);
		if (speed < 2.0) {
			return a4mcLocalOffset;
		}
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBody.y(), forwardReference);
		double sideslip = Math.atan2(relativeAirVelocityBody.x(), forwardReference);
		double angleIntensity = MathUtil.clamp(
				Math.abs(angleOfAttack) / Math.toRadians(55.0)
						+ 0.80 * Math.abs(sideslip) / Math.toRadians(60.0),
				0.0,
				1.0
		);
		if (angleIntensity <= 1.0e-6) {
			return a4mcLocalOffset;
		}

		double speedScale = smoothStep(3.0, 18.0, speed);
		double separationBias = 0.35 + 0.65 * effectiveAirframeSeparationIntensity(relativeAirVelocityBody);
		double migrationScale = speedScale * separationBias * angleIntensity;
		if (migrationScale <= 1.0e-6) {
			return a4mcLocalOffset;
		}

		double lateralShift = -0.018 * Math.sin(sideslip) * migrationScale;
		double verticalShift = 0.016 * Math.sin(angleOfAttack) * migrationScale;
		double forwardFlowFraction = MathUtil.clamp(Math.abs(relativeAirVelocityBody.z()) / speed, 0.0, 1.0);
		double aftShift = -0.026
				* (Math.abs(Math.sin(angleOfAttack)) + 0.70 * Math.abs(Math.sin(sideslip)))
				* migrationScale
				* forwardFlowFraction;
		return new Vec3(lateralShift, verticalShift, aftShift)
				.add(a4mcLocalOffset)
				.clamp(-0.040, 0.040);
	}

	private Vec3 a4mcLocalVoxelPressureCenterOffsetBody(DroneEnvironment environment, double airspeedMetersPerSecond) {
		if (environment == null || !environment.windSourceLocalVoxelFlow()) {
			return Vec3.ZERO;
		}
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return Vec3.ZERO;
		}
		int count = Math.min(state.motorCount(), config.rotors().size());
		if (count <= 0) {
			return Vec3.ZERO;
		}

		double[] signals = new double[count];
		double signalSum = 0.0;
		for (int i = 0; i < count; i++) {
			double localVoxelCoverage = MathUtil.clamp(1.0 - environment.rotorLocalVoxelObstacleResidual(i), 0.0, 1.0);
			double shelterObstruction = MathUtil.clamp(environment.rotorA4mcShelterObstruction(i), 0.0, 1.0);
			double pressureGradientSignal = a4mcLocalVoxelPressureCenterGradientSignal(
					i,
					environment.rotorA4mcPressureGradientWindBodyMetersPerSecond(i)
			);
			double signal = localVoxelCoverage + 0.65 * shelterObstruction + 0.85 * pressureGradientSignal;
			signals[i] = signal;
			signalSum += signal;
		}
		double meanSignal = signalSum / count;
		if (meanSignal <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 weightedDeficitCentroid = Vec3.ZERO;
		for (int i = 0; i < count; i++) {
			double anomaly = signals[i] - meanSignal;
			if (Math.abs(anomaly) <= 1.0e-6) {
				continue;
			}
			Vec3 position = config.rotors().get(i).positionBodyMeters();
			weightedDeficitCentroid = weightedDeficitCentroid.add(position.multiply(anomaly));
		}
		weightedDeficitCentroid = weightedDeficitCentroid.multiply(1.0 / count);
		if (weightedDeficitCentroid.lengthSquared() <= 1.0e-12) {
			return Vec3.ZERO;
		}

		double speedGate = Math.max(
				smoothStep(2.0, 12.0, airspeedMetersPerSecond),
				a4mcLocalVoxelRotorWashPressureCenterGate()
		);
		double strength = MathUtil.clamp(meanSignal * speedGate, 0.0, 1.0);
		if (strength <= 1.0e-6) {
			return Vec3.ZERO;
		}
		return new Vec3(
				-weightedDeficitCentroid.x() * 0.68 * strength,
				0.0,
				-weightedDeficitCentroid.z() * 0.44 * strength
		).clamp(-0.024, 0.024);
	}

	private double a4mcLocalVoxelPressureCenterGradientSignal(int rotorIndex, Vec3 pressureGradientWindBody) {
		if (pressureGradientWindBody == null || !pressureGradientWindBody.isFinite()
				|| rotorIndex < 0 || rotorIndex >= config.rotors().size()) {
			return 0.0;
		}
		Vec3 rotorPosition = config.rotors().get(rotorIndex).positionBodyMeters();
		Vec3 rotorRadialBody = new Vec3(rotorPosition.x(), 0.0, rotorPosition.z());
		if (rotorRadialBody.lengthSquared() <= 1.0e-12) {
			return 0.0;
		}
		double radialPressureGradientSpeed = Math.abs(pressureGradientWindBody.dot(rotorRadialBody.normalized()));
		return smoothStep(0.15, 1.80, radialPressureGradientSpeed);
	}

	private Vec3 a4mcLocalVoxelRotorWashPressureCenterForceBody(
			Vec3 dynamicPressureCenterOffsetBody,
			DroneEnvironment environment,
			double airDensityRatio
	) {
		if (environment == null
				|| !environment.windSourceLocalVoxelFlow()
				|| a4mcWindSourceQualityFactor(environment) <= 1.0e-9
				|| dynamicPressureCenterOffsetBody.lengthSquared() <= 1.0e-12
				|| airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}
		double washGate = a4mcLocalVoxelRotorWashPressureCenterGate();
		if (washGate <= 1.0e-6) {
			return Vec3.ZERO;
		}
		double inducedVelocity = state.averageRotorInducedVelocityMetersPerSecond();
		Vec3 drag = config.bodyDragCoefficients();
		double bodyAreaScale = Math.sqrt(Math.max(0.0, drag.x() * drag.z()));
		if (bodyAreaScale <= 1.0e-9 || inducedVelocity <= 1.0e-6) {
			return Vec3.ZERO;
		}
		double download = airDensityRatio
				* inducedVelocity
				* inducedVelocity
				* bodyAreaScale
				* A4MC_LOCAL_PRESSURE_CENTER_HOVER_DOWNLOAD_GAIN
				* washGate;
		return new Vec3(0.0, -MathUtil.clamp(download, 0.0, 0.45), 0.0);
	}

	private double a4mcLocalVoxelRotorWashPressureCenterGate() {
		double inducedVelocity = state.averageRotorInducedVelocityMetersPerSecond();
		double thrustToWeight = totalRotorThrustNewtons()
				/ Math.max(1.0e-6, config.massKg() * config.gravityMetersPerSecondSquared());
		return A4MC_LOCAL_PRESSURE_CENTER_HOVER_WASH_GATE_SCALE
				* smoothStep(0.08, 0.70, thrustToWeight)
				* smoothStep(0.35, 5.5, inducedVelocity);
	}

	private double totalRotorThrustNewtons() {
		double total = 0.0;
		int count = state.motorCount();
		for (int i = 0; i < count; i++) {
			total += state.rotorThrustNewtons(i);
		}
		return total;
	}

	private Vec3 calculateAirframeAngularDragTorque(
			Vec3 angularVelocityBody,
			Vec3 totalRotorForceBody,
			Vec3 relativeAirVelocityBody,
			double airDensityRatio,
			double dtSeconds
	) {
		double speed = relativeAirVelocityBody.length();
		Vec3 drag = config.bodyDragCoefficients();
		double dynamicScale = Math.max(0.0, airDensityRatio) * speed * speed;
		Vec3 rotationalDamping = calculateRotationalAirframeAngularDamping(angularVelocityBody, airDensityRatio);
		Vec3 separatedFlowDamping = calculateSeparatedFlowAirframeAngularDamping(relativeAirVelocityBody, airDensityRatio);
		Vec3 rotorWashDamping = updateRotorWashAirframeAngularDamping(totalRotorForceBody, airDensityRatio, dtSeconds);
		double sideslipYawDamping = calculateSideslipWeathercockYawDamping(relativeAirVelocityBody, airDensityRatio);
		double pitchDamping = config.angularDragCoefficient()
				+ MathUtil.clamp(dynamicScale * (0.00022 * drag.z() + 0.00006 * drag.y()), 0.0, 0.36)
				+ rotationalDamping.x()
				+ separatedFlowDamping.x()
				+ rotorWashDamping.x();
		double yawDamping = config.angularDragCoefficient()
				+ MathUtil.clamp(dynamicScale * (0.00018 * drag.x() + 0.00008 * drag.z()), 0.0, 0.36)
				+ sideslipYawDamping
				+ rotationalDamping.y()
				+ separatedFlowDamping.y()
				+ rotorWashDamping.y();
		double rollDamping = config.angularDragCoefficient()
				+ MathUtil.clamp(dynamicScale * (0.00020 * drag.x() + 0.00006 * drag.y()), 0.0, 0.36)
				+ rotationalDamping.z()
				+ separatedFlowDamping.z()
				+ rotorWashDamping.z();
		Vec3 rawDampingTorque = new Vec3(
				-angularVelocityBody.x() * pitchDamping,
				-angularVelocityBody.y() * yawDamping,
				-angularVelocityBody.z() * rollDamping
		);
		return applyNeuroBemAngularDampingGuard(rawDampingTorque, angularVelocityBody, speed, airDensityRatio);
	}

	private Vec3 applyNeuroBemAngularDampingGuard(
			Vec3 rawDampingTorque,
			Vec3 angularVelocityBody,
			double airspeedMetersPerSecond,
			double airDensityRatio
	) {
		if (airDensityRatio <= 0.0
				|| rawDampingTorque.lengthSquared() <= 1.0e-12
				|| angularVelocityBody.lengthSquared() <= 1.0e-12) {
			return rawDampingTorque;
		}

		double speedCoverage = smoothStep(
				NeuroBemAirframeResidualCalibration.BODY_SPEED_SAMPLE_P50_METERS_PER_SECOND,
				NeuroBemAirframeResidualCalibration.BODY_SPEED_SAMPLE_P95_METERS_PER_SECOND,
				airspeedMetersPerSecond
		);
		double rateCoverage = smoothStep(
				NeuroBemAirframeResidualCalibration.ANGULAR_SPEED_SAMPLE_P50_RADIANS_PER_SECOND,
				NeuroBemAirframeResidualCalibration.ANGULAR_SPEED_SAMPLE_P95_RADIANS_PER_SECOND,
				angularVelocityBody.length()
		);
		double guardCoverage = speedCoverage * rateCoverage;
		if (guardCoverage <= 1.0e-6) {
			return rawDampingTorque;
		}

		Vec3 torqueLimit = NeuroBemAirframeResidualCalibration.runtimeResidualTorqueP95AxisLimitNewtonMeters(config);
		Vec3 guardedTorque = new Vec3(
				MathUtil.clamp(rawDampingTorque.x(), -torqueLimit.x(), torqueLimit.x()),
				MathUtil.clamp(rawDampingTorque.y(), -torqueLimit.y(), torqueLimit.y()),
				MathUtil.clamp(rawDampingTorque.z(), -torqueLimit.z(), torqueLimit.z())
		);
		return rawDampingTorque.add(guardedTorque.subtract(rawDampingTorque).multiply(guardCoverage));
	}

	private double calculateSideslipWeathercockYawDamping(Vec3 relativeAirVelocityBody, double airDensityRatio) {
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared <= 1.0e-6 || airDensityRatio <= 0.0) {
			return 0.0;
		}

		double lateralSpeed = Math.abs(relativeAirVelocityBody.x());
		double forwardSpeed = Math.abs(relativeAirVelocityBody.z());
		if (lateralSpeed <= 1.0e-6 || forwardSpeed <= 1.0e-6) {
			return 0.0;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double lateralArea = Math.max(0.0, drag.x());
		double frontalArea = Math.max(0.0, drag.z());
		if (lateralArea <= 1.0e-9 || frontalArea <= 1.0e-9) {
			return 0.0;
		}

		double sideslip = Math.atan2(relativeAirVelocityBody.x(), Math.max(2.0, forwardSpeed));
		double sideslipExposure = smoothStep(Math.toRadians(7.0), Math.toRadians(48.0), Math.abs(sideslip));
		double forwardExposure = smoothStep(2.5, 16.0, forwardSpeed);
		double lateralExposure = smoothStep(1.5, 12.0, lateralSpeed);
		double dynamicScale = Math.max(0.0, airDensityRatio) * speedSquared;
		double weathercockArea = Math.sqrt(lateralArea * frontalArea);
		return MathUtil.clamp(
				dynamicScale
						* weathercockArea
						* sideslipExposure
						* (0.45 + 0.35 * forwardExposure + 0.20 * lateralExposure)
						* 0.00016,
				0.0,
				0.22
		);
	}

	private Vec3 calculateSeparatedFlowAirframeAngularDamping(Vec3 relativeAirVelocityBody, double airDensityRatio) {
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared <= 1.0e-6 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}

		double separation = effectiveAirframeSeparationIntensity(relativeAirVelocityBody);
		if (separation <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBody.y(), forwardReference);
		double sideslip = Math.atan2(relativeAirVelocityBody.x(), forwardReference);
		double pitchExposure = 0.35 + 0.65 * smoothStep(Math.toRadians(24.0), Math.toRadians(72.0), Math.abs(angleOfAttack));
		double yawExposure = 0.35 + 0.65 * smoothStep(Math.toRadians(26.0), Math.toRadians(74.0), Math.abs(sideslip));
		double rollExposure = 0.45 + 0.55 * smoothStep(
				Math.toRadians(32.0),
				Math.toRadians(82.0),
				Math.abs(angleOfAttack) + 0.80 * Math.abs(sideslip)
		);
		double separatedDynamicScale = Math.max(0.0, airDensityRatio) * speedSquared * separation;
		return new Vec3(
				MathUtil.clamp(separatedDynamicScale * pitchExposure * (0.00012 * drag.z() + 0.00005 * drag.y()), 0.0, 0.18),
				MathUtil.clamp(separatedDynamicScale * yawExposure * (0.00012 * drag.x() + 0.00005 * drag.z()), 0.0, 0.18),
				MathUtil.clamp(separatedDynamicScale * rollExposure * (0.00010 * drag.x() + 0.00005 * drag.y()), 0.0, 0.16)
		);
	}

	private Vec3 calculateRotationalAirframeAngularDamping(Vec3 angularVelocityBody, double airDensityRatio) {
		if (airDensityRatio <= 0.0 || angularVelocityBody.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		Vec3 drag = config.bodyDragCoefficients();
		if (Math.max(drag.x(), Math.max(drag.y(), drag.z())) <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double frameRadius = equivalentStaticPortRadiusMeters();
		double pitchLocalSpeed = Math.abs(angularVelocityBody.x()) * frameRadius;
		double yawLocalSpeed = Math.abs(angularVelocityBody.y()) * frameRadius * 0.68;
		double rollLocalSpeed = Math.abs(angularVelocityBody.z()) * frameRadius;
		return new Vec3(
				MathUtil.clamp(airDensityRatio * pitchLocalSpeed * (0.020 * drag.z() + 0.006 * drag.y()), 0.0, 0.08),
				MathUtil.clamp(airDensityRatio * yawLocalSpeed * (0.014 * Math.sqrt(Math.max(0.0, drag.x() * drag.z())) + 0.004 * drag.y()), 0.0, 0.06),
				MathUtil.clamp(airDensityRatio * rollLocalSpeed * (0.020 * drag.x() + 0.006 * drag.y()), 0.0, 0.08)
		);
	}

	private Vec3 updateRotorWashAirframeAngularDamping(
			Vec3 totalRotorForceBody,
			double airDensityRatio,
			double dtSeconds
	) {
		Vec3 target = calculateSteadyRotorWashAirframeAngularDamping(totalRotorForceBody, airDensityRatio);
		if (dtSeconds <= 0.0) {
			rotorWashAirframeAngularDampingFiltered = target;
			return rotorWashAirframeAngularDampingFiltered;
		}

		double targetMagnitude = target.length();
		double previousMagnitude = rotorWashAirframeAngularDampingFiltered.length();
		double timeConstant = targetMagnitude > previousMagnitude ? 0.026 : 0.090;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		rotorWashAirframeAngularDampingFiltered = rotorWashAirframeAngularDampingFiltered
				.add(target.subtract(rotorWashAirframeAngularDampingFiltered).multiply(alpha))
				.clamp(0.0, 0.16);
		if (targetMagnitude <= 1.0e-6 && rotorWashAirframeAngularDampingFiltered.lengthSquared() < 1.0e-8) {
			rotorWashAirframeAngularDampingFiltered = Vec3.ZERO;
		}
		return rotorWashAirframeAngularDampingFiltered;
	}

	private Vec3 calculateSteadyRotorWashAirframeAngularDamping(Vec3 totalRotorForceBody, double airDensityRatio) {
		if (totalRotorForceBody.y() <= 1.0e-6 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}

		double inducedVelocity = state.averageRotorInducedVelocityMetersPerSecond();
		double thrustToWeight = totalRotorForceBody.y() / Math.max(1.0e-6, config.massKg() * config.gravityMetersPerSecondSquared());
		double washIntensity = smoothStep(0.08, 0.70, thrustToWeight) * smoothStep(0.35, 5.5, inducedVelocity);
		if (washIntensity <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double washDynamicScale = Math.max(0.0, airDensityRatio) * inducedVelocity * inducedVelocity * washIntensity;
		double pitchDamping = washDynamicScale * (0.00030 * drag.z() + 0.00010 * drag.y());
		double yawDamping = washDynamicScale * 0.00014 * Math.sqrt(Math.max(0.0, drag.x() * drag.z()));
		double rollDamping = washDynamicScale * (0.00030 * drag.x() + 0.00010 * drag.y());
		return new Vec3(
				MathUtil.clamp(pitchDamping, 0.0, 0.16),
				MathUtil.clamp(yawDamping, 0.0, 0.10),
				MathUtil.clamp(rollDamping, 0.0, 0.16)
		);
	}

	private void updateAerodynamicTelemetry(Vec3 relativeAirVelocityBody) {
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		state.setRelativeAirVelocityBodyMetersPerSecond(relativeAirVelocityBody);
		state.setAirspeedMetersPerSecond(relativeAirVelocityBody.length());
		state.setAngleOfAttackRadians(Math.atan2(relativeAirVelocityBody.y(), forwardReference));
		state.setSideslipRadians(Math.atan2(relativeAirVelocityBody.x(), forwardReference));
	}

	private Vec3 updateAirMassWind(DroneEnvironment environment, double dtSeconds) {
		Vec3 targetMeanWind = boundaryLayerMeanWind(environment);
		if (!windModelInitialized || dtSeconds <= 0.0) {
			windModelInitialized = true;
			meanWindVelocityWorldMetersPerSecond = targetMeanWind;
			windBurbleVelocityWorldMetersPerSecond = Vec3.ZERO;
			drydenFirstOrderVelocityWorldMetersPerSecond = Vec3.ZERO;
			drydenTransverseLagVelocityWorldMetersPerSecond = Vec3.ZERO;
			drydenTurbulenceVelocityWorldMetersPerSecond = Vec3.ZERO;
			a4mcSourceGustVelocityWorldMetersPerSecond = Vec3.ZERO;
			a4mcUpdraftVelocityWorldMetersPerSecond = Vec3.ZERO;
			a4mcTerrainShearVelocityWorldMetersPerSecond = Vec3.ZERO;
			windGustVelocityWorldMetersPerSecond = Vec3.ZERO;
			state.setEffectiveWindVelocityWorldMetersPerSecond(targetMeanWind);
			state.setWindGustVelocityWorldMetersPerSecond(Vec3.ZERO);
			state.setDrydenTurbulenceVelocityWorldMetersPerSecond(Vec3.ZERO);
			state.setWindBurbleVelocityWorldMetersPerSecond(Vec3.ZERO);
			state.setA4mcSourceGustVelocityWorldMetersPerSecond(Vec3.ZERO);
			state.setA4mcUpdraftVelocityWorldMetersPerSecond(Vec3.ZERO);
			state.setA4mcTerrainShearVelocityWorldMetersPerSecond(Vec3.ZERO);
			state.setWindShearAccelerationMetersPerSecondSquared(0.0);
			return targetMeanWind;
		}

		double dirtyAir = dirtyAirIntensity(environment);
		double targetWindSpeed = targetMeanWind.length();
		double meanTimeConstant = MathUtil.clamp(
				0.055
						+ 0.018 * targetWindSpeed
						+ 0.140 * dirtyAir
						+ 0.060 * environment.obstacleProximity()
						+ 0.045 * environment.droneWakeIntensity(),
				0.045,
				0.620
		);
		double meanAlpha = MathUtil.expSmoothing(dtSeconds, meanTimeConstant);
		meanWindVelocityWorldMetersPerSecond = meanWindVelocityWorldMetersPerSecond.add(
				targetMeanWind.subtract(meanWindVelocityWorldMetersPerSecond).multiply(meanAlpha)
		);

		windGustPhaseA += dtSeconds * (1.35 + 0.16 * targetWindSpeed + 1.25 * dirtyAir);
		windGustPhaseB += dtSeconds * (1.95 + 0.11 * targetWindSpeed + 0.95 * dirtyAir);
		windGustPhaseC += dtSeconds * (0.85 + 0.09 * targetWindSpeed + 1.55 * dirtyAir);
		Vec3 targetBurble = windBurbleTarget(environment, targetMeanWind, dirtyAir);
		double burbleTimeConstant = MathUtil.clamp(0.070 + 0.085 / (0.35 + dirtyAir), 0.055, 0.260);
		double burbleAlpha = MathUtil.expSmoothing(dtSeconds, burbleTimeConstant);
		windBurbleVelocityWorldMetersPerSecond = windBurbleVelocityWorldMetersPerSecond.add(
				targetBurble.subtract(windBurbleVelocityWorldMetersPerSecond).multiply(burbleAlpha)
		);
		Vec3 drydenTurbulence = updateDrydenTurbulence(environment, targetMeanWind, dtSeconds);
		a4mcSourceGustVelocityWorldMetersPerSecond = a4mcSourceGustWind(environment, targetMeanWind, dirtyAir);
		Vec3 a4mcUpdraft = updateA4mcUpdraftWind(environment, dtSeconds);
		Vec3 a4mcTerrainShear = updateA4mcTerrainShearWind(environment, targetMeanWind, dirtyAir, dtSeconds);
		windGustVelocityWorldMetersPerSecond = windBurbleVelocityWorldMetersPerSecond
				.add(drydenTurbulence)
				.add(a4mcSourceGustVelocityWorldMetersPerSecond)
				.add(a4mcUpdraft)
				.add(a4mcTerrainShear);

		Vec3 previousEffectiveWind = state.effectiveWindVelocityWorldMetersPerSecond();
		Vec3 effectiveWind = meanWindVelocityWorldMetersPerSecond.add(windGustVelocityWorldMetersPerSecond);
		double shearAcceleration = effectiveWind.subtract(previousEffectiveWind).length() / Math.max(1.0e-6, dtSeconds);
		state.setEffectiveWindVelocityWorldMetersPerSecond(effectiveWind);
		state.setWindGustVelocityWorldMetersPerSecond(windGustVelocityWorldMetersPerSecond);
		state.setDrydenTurbulenceVelocityWorldMetersPerSecond(drydenTurbulence);
		state.setWindBurbleVelocityWorldMetersPerSecond(windBurbleVelocityWorldMetersPerSecond);
		state.setA4mcSourceGustVelocityWorldMetersPerSecond(a4mcSourceGustVelocityWorldMetersPerSecond);
		state.setA4mcUpdraftVelocityWorldMetersPerSecond(a4mcUpdraft);
		state.setA4mcTerrainShearVelocityWorldMetersPerSecond(a4mcTerrainShear);
		state.setWindShearAccelerationMetersPerSecondSquared(shearAcceleration);
		return effectiveWind;
	}

	private double dirtyAirIntensity(DroneEnvironment environment) {
		double atmosphericTurbulence = atmosphericTurbulenceIntensity(environment);
		return MathUtil.clamp(
				atmosphericTurbulence
						+ 0.26 * environment.obstacleProximity()
						+ 0.18 * environment.droneWakeIntensity()
						+ 0.12 * environment.ceilingEffectIntensity(config)
						+ surfaceBoundaryLayerDirtyAir(environment, environment.groundClearanceMeters(), environment.windVelocityWorldMetersPerSecond())
						+ 0.85 * surfaceBoundaryLayerDirtyAir(environment, environment.ceilingClearanceMeters(), environment.windVelocityWorldMetersPerSecond()),
				0.0,
				1.8
		);
	}

	private Vec3 boundaryLayerMeanWind(DroneEnvironment environment) {
		Vec3 wind = environment.windVelocityWorldMetersPerSecond();
		double height = surfaceBoundaryLayerHeightMeters();
		if (height <= 1.0e-6) {
			return wind;
		}

		double groundScale = boundaryLayerHorizontalWindScale(environment, environment.groundClearanceMeters(), height);
		double ceilingScale = boundaryLayerHorizontalWindScale(environment, environment.ceilingClearanceMeters(), height);
		double horizontalScale = Math.min(groundScale, ceilingScale);
		return new Vec3(wind.x() * horizontalScale, wind.y(), wind.z() * horizontalScale);
	}

	private double surfaceBoundaryLayerDirtyAir(DroneEnvironment environment, double clearance, Vec3 wind) {
		double height = surfaceBoundaryLayerHeightMeters();
		if (height <= 1.0e-6 || !Double.isFinite(clearance) || clearance >= height) {
			return 0.0;
		}

		double horizontalWindSpeed = Math.sqrt(wind.x() * wind.x() + wind.z() * wind.z());
		if (horizontalWindSpeed <= 0.5) {
			return 0.0;
		}

		double nearSurface = 1.0 - smoothStep(0.04, height, clearance);
		double windFactor = smoothStep(1.4, 11.0, horizontalWindSpeed);
		double shearFactor = 1.0 - boundaryLayerHorizontalWindScale(environment, clearance, height);
		return MathUtil.clamp(
				0.46 * nearSurface * windFactor * shearFactor * boundaryLayerAblDirtyAirMultiplier(environment),
				0.0,
				0.44
		);
	}

	private double surfaceBoundaryLayerHeightMeters() {
		if (config.groundEffectHeightMeters() <= 1.0e-6) {
			return 0.0;
		}
		return Math.max(0.50, config.groundEffectHeightMeters() * 1.80);
	}

	private static double boundaryLayerHorizontalWindScale(double clearanceMeters, double heightMeters) {
		if (!Double.isFinite(clearanceMeters) || clearanceMeters >= heightMeters || heightMeters <= 1.0e-6) {
			return 1.0;
		}
		double normalizedClearance = MathUtil.clamp(clearanceMeters / Math.max(1.0e-6, heightMeters), 0.0, 1.0);
		double surfaceSlip = 0.18 + 0.12 * smoothStep(0.0, 0.40, normalizedClearance);
		double recovery = smoothStep(0.03, 1.0, normalizedClearance);
		return MathUtil.clamp(surfaceSlip + (1.0 - surfaceSlip) * recovery, 0.18, 1.0);
	}

	private static double boundaryLayerHorizontalWindScale(
			DroneEnvironment environment,
			double clearanceMeters,
			double heightMeters
	) {
		double neutralScale = boundaryLayerHorizontalWindScale(clearanceMeters, heightMeters);
		if (neutralScale >= 1.0 - 1.0e-9 || environment == null) {
			return neutralScale;
		}
		A4mcAblShape shape = a4mcAblShape(environment);
		double deficitMultiplier = MathUtil.clamp(
				1.0
						- 0.22 * shape.ablMixing()
						- 0.35 * shape.mixedUnstable()
						+ 0.32 * shape.stableSuppression(),
				0.55,
				1.35
		);
		double neutralDeficit = 1.0 - neutralScale;
		return MathUtil.clamp(1.0 - neutralDeficit * deficitMultiplier, 0.10, 1.0);
	}

	private static double boundaryLayerAblDirtyAirMultiplier(DroneEnvironment environment) {
		if (environment == null) {
			return 1.0;
		}
		A4mcAblShape shape = a4mcAblShape(environment);
		return MathUtil.clamp(
				1.0
						- 0.10 * shape.ablMixing()
						- 0.22 * shape.mixedUnstable()
						+ 0.28 * shape.stableSuppression(),
				0.60,
				1.35
		);
	}

	private Vec3 windBurbleTarget(DroneEnvironment environment, Vec3 targetMeanWind, double dirtyAir) {
		double burbleDirtyAir = localizedWindBurbleIntensity(environment, dirtyAir);
		if (burbleDirtyAir <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double windSpeed = targetMeanWind.length();
		double gustScale = MathUtil.clamp(burbleDirtyAir * (0.32 + 0.070 * windSpeed), 0.0, 4.5);
		double horizontalGustX = Math.sin(windGustPhaseA)
				+ 0.42 * Math.sin(windGustPhaseB * 1.71 + 0.4)
				+ 0.18 * Math.sin(windGustPhaseC * 2.17 + 2.2);
		double horizontalGustZ = Math.sin(windGustPhaseB + 1.3)
				+ 0.35 * Math.sin(windGustPhaseC * 1.47)
				+ 0.22 * Math.sin(windGustPhaseA * 2.03 + 1.1);
		double verticalGust = 0.34 * Math.sin(windGustPhaseC + 2.6)
				+ 0.16 * Math.sin(windGustPhaseA * 1.33 + 0.8);
		double upstreamBias = MathUtil.clamp(environment.obstacleProximity() * windSpeed / 12.0, 0.0, 0.35);
		Vec3 upstreamBurble = windSpeed <= 1.0e-6
				? Vec3.ZERO
				: targetMeanWind.normalized().multiply(-upstreamBias);
		double burbleScale = MathUtil.clamp(
				0.32
						+ 0.18 * environment.obstacleProximity()
						+ 0.14 * environment.droneWakeIntensity(),
				0.28,
				0.70
		);
		return new Vec3(horizontalGustX, verticalGust, horizontalGustZ)
				.multiply(gustScale)
				.add(upstreamBurble)
				.multiply(burbleScale);
	}

	private Vec3 a4mcSourceGustWind(DroneEnvironment environment, Vec3 targetMeanWind, double dirtyAir) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return Vec3.ZERO;
		}
		Vec3 sourceGustVelocity = environment.windSourceGustVelocityWorldMetersPerSecond().multiply(sourceQuality);
		double sourceGustVectorMagnitude = sourceGustVelocity.length();
		double sourceGustVectorSpeed = MathUtil.clamp(sourceGustVectorMagnitude, 0.0, 12.0);
		double sourceGustSpeed = MathUtil.clamp(
				Math.max(environment.windSourceGustSpeedMetersPerSecond() * sourceQuality, sourceGustVectorSpeed),
				0.0,
				12.0
		);
		if (sourceGustSpeed <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double horizontalWindSpeed = Math.hypot(targetMeanWind.x(), targetMeanWind.z());
		double windGate = smoothStep(0.3, 5.0, Math.max(horizontalWindSpeed, sourceGustSpeed));
		double dirtyGain = MathUtil.clamp(0.72 + 0.10 * dirtyAir, 0.72, 0.94);
		if (sourceGustVectorSpeed > 1.0e-6) {
			double vectorScale = MathUtil.clamp(sourceGustSpeed / Math.max(sourceGustVectorMagnitude, 1.0e-6), 0.0, 1.0);
			return a4mcSourceGustAblShapedVelocity(environment, sourceGustVelocity)
					.multiply(0.22 * windGate * dirtyGain * vectorScale)
					.clamp(-2.0, 2.0);
		}

		double gustSignal = MathUtil.clamp(sourceGustSpeed * windGate * a4mcSourceGustAblScalarMultiplier(environment), 0.0, 8.0);
		Vec3 windAxis = horizontalWindAxis(targetMeanWind);
		Vec3 crossAxis = new Vec3(-windAxis.z(), 0.0, windAxis.x());
		double along = Math.sin(windGustPhaseA * 1.11 + 0.35) * gustSignal * 0.18;
		double cross = Math.sin(windGustPhaseB * 0.97 + 1.85) * gustSignal * 0.28;
		double vertical = Math.sin(windGustPhaseC * 1.29 + 2.40)
				* gustSignal
				* 0.10
				* a4mcSourceGustAblVerticalMultiplier(environment);
		return windAxis.multiply(along)
				.add(crossAxis.multiply(cross))
				.add(WORLD_UP.multiply(vertical))
				.multiply(dirtyGain)
				.clamp(-2.0, 2.0);
	}

	private static Vec3 a4mcSourceGustAblShapedVelocity(DroneEnvironment environment, Vec3 sourceGustVelocity) {
		Vec3 gust = sourceGustVelocity == null || !sourceGustVelocity.isFinite() ? Vec3.ZERO : sourceGustVelocity;
		if (gust.lengthSquared() <= 1.0e-12) {
			return Vec3.ZERO;
		}
		double horizontalGain = a4mcSourceGustAblHorizontalMultiplier(environment);
		double verticalGain = a4mcSourceGustAblVerticalMultiplier(environment);
		return new Vec3(
				gust.x() * horizontalGain,
				gust.y() * verticalGain,
				gust.z() * horizontalGain
		).clamp(-12.0, 12.0);
	}

	private static double a4mcSourceGustAblScalarMultiplier(DroneEnvironment environment) {
		A4mcAblShape shape = a4mcAblShape(environment);
		return MathUtil.clamp(
				1.0 + 0.10 * shape.ablMixing() + 0.18 * shape.mixedUnstable() - 0.16 * shape.stableSuppression(),
				0.72,
				1.25
		);
	}

	private static double a4mcSourceGustAblHorizontalMultiplier(DroneEnvironment environment) {
		A4mcAblShape shape = a4mcAblShape(environment);
		return MathUtil.clamp(
				1.0 + 0.06 * shape.ablMixing() + 0.10 * shape.mixedUnstable() - 0.12 * shape.stableSuppression(),
				0.76,
				1.18
		);
	}

	private static double a4mcSourceGustAblVerticalMultiplier(DroneEnvironment environment) {
		A4mcAblShape shape = a4mcAblShape(environment);
		return MathUtil.clamp(
				1.0 + 0.22 * shape.ablMixing() + 0.44 * shape.mixedUnstable() - 0.52 * shape.stableSuppression(),
				0.45,
				1.55
		);
	}

	private static double a4mcAblWakeCoherenceMultiplier(DroneEnvironment environment) {
		A4mcAblShape shape = a4mcAblShape(environment);
		return MathUtil.clamp(
				1.0 + 0.16 * shape.stableSuppression() - 0.14 * shape.mixedUnstable() - 0.04 * shape.ablMixing(),
				0.78,
				1.18
		);
	}

	private static double a4mcAblWakeBuildTimeScaleMultiplier(DroneEnvironment environment) {
		A4mcAblShape shape = a4mcAblShape(environment);
		return MathUtil.clamp(
				1.0 - 0.08 * shape.stableSuppression() + 0.12 * shape.mixedUnstable() + 0.04 * shape.ablMixing(),
				0.86,
				1.18
		);
	}

	private static double a4mcAblWakeReleaseTimeScaleMultiplier(DroneEnvironment environment) {
		A4mcAblShape shape = a4mcAblShape(environment);
		return MathUtil.clamp(
				1.0 + 0.34 * shape.stableSuppression() - 0.28 * shape.mixedUnstable() - 0.08 * shape.ablMixing(),
				0.64,
				1.36
		);
	}

	private static A4mcAblShape a4mcAblShape(DroneEnvironment environment) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double ablStability = MathUtil.clamp(environment.windSourceAblStability() * sourceQuality, -1.0, 1.0);
		double ablMixing = MathUtil.clamp(environment.windSourceAblMixingStrength() * sourceQuality, 0.0, 1.0);
		double unstable = Math.max(0.0, ablStability);
		double stable = Math.max(0.0, -ablStability);
		double mixedUnstable = unstable * ablMixing;
		double stableSuppression = stable * (0.75 + 0.25 * (1.0 - ablMixing));
		return new A4mcAblShape(ablMixing, mixedUnstable, stableSuppression);
	}

	private Vec3 updateA4mcUpdraftWind(DroneEnvironment environment, double dtSeconds) {
		Vec3 target = a4mcUpdraftWindTarget(environment);
		double updraftMagnitude = Math.abs(target.y());
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double sourceResponse = smoothStep(0.10, 1.0, sourceQuality);
		double tau = MathUtil.clamp(
				(0.22 - 0.065 * smoothStep(0.25, 4.0, updraftMagnitude) - 0.040 * sourceResponse)
						* a4mcUpdraftAblTimeScaleMultiplier(environment),
				0.055,
				0.330
		);
		double alpha = MathUtil.expSmoothing(dtSeconds, tau);
		a4mcUpdraftVelocityWorldMetersPerSecond = a4mcUpdraftVelocityWorldMetersPerSecond.add(
				target.subtract(a4mcUpdraftVelocityWorldMetersPerSecond).multiply(alpha)
		);
		if (updraftMagnitude <= 1.0e-6 && Math.abs(a4mcUpdraftVelocityWorldMetersPerSecond.y()) < 1.0e-4) {
			a4mcUpdraftVelocityWorldMetersPerSecond = Vec3.ZERO;
		}
		return a4mcUpdraftVelocityWorldMetersPerSecond;
	}

	private Vec3 a4mcUpdraftWindTarget(DroneEnvironment environment) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return Vec3.ZERO;
		}
		double sourceUpdraft = a4mcSeparatedUpdraftMetersPerSecond(environment, sourceQuality);
		if (Math.abs(sourceUpdraft) <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double localVoxelGain = environment.windSourceLocalVoxelFlow() ? 1.0 : 0.72;
		double responseGain = MathUtil.clamp(
				localVoxelGain
						* a4mcUpdraftAblTargetMultiplier(environment)
						* (0.50 + 0.22 * smoothStep(0.35, 4.0, Math.abs(sourceUpdraft))),
				0.30,
				0.90
		);
		return WORLD_UP.multiply(MathUtil.clamp(sourceUpdraft * responseGain, -4.5, 4.5));
	}

	private static double a4mcSeparatedUpdraftMetersPerSecond(DroneEnvironment environment, double sourceQuality) {
		double sourceUpdraft = MathUtil.clamp(environment.windUpdraftMetersPerSecond() * sourceQuality, -12.0, 12.0);
		if (Math.abs(sourceUpdraft) <= 1.0e-6) {
			return 0.0;
		}
		Vec3 sourceGustVelocity = environment.windSourceGustVelocityWorldMetersPerSecond();
		if (sourceGustVelocity != null && sourceGustVelocity.isFinite()) {
			double explicitVerticalGust = MathUtil.clamp(sourceGustVelocity.y() * sourceQuality, -12.0, 12.0);
			sourceUpdraft = removeOverlappingVerticalFlow(sourceUpdraft, explicitVerticalGust);
		}
		sourceUpdraft = removeOverlappingVerticalFlow(
				sourceUpdraft,
				MathUtil.clamp(environment.windVelocityWorldMetersPerSecond().y(), -12.0, 12.0)
		);
		return MathUtil.clamp(sourceUpdraft, -12.0, 12.0);
	}

	private static double removeOverlappingVerticalFlow(double verticalSignal, double alreadyRepresentedVerticalFlow) {
		if (Math.abs(verticalSignal) <= 1.0e-6 || Math.abs(alreadyRepresentedVerticalFlow) <= 1.0e-6) {
			return verticalSignal;
		}
		if (Math.signum(verticalSignal) != Math.signum(alreadyRepresentedVerticalFlow)) {
			return verticalSignal;
		}
		if (Math.abs(verticalSignal) <= Math.abs(alreadyRepresentedVerticalFlow)) {
			return 0.0;
		}
		return verticalSignal - alreadyRepresentedVerticalFlow;
	}

	private static double a4mcUpdraftAblTargetMultiplier(DroneEnvironment environment) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double ablStability = MathUtil.clamp(environment.windSourceAblStability() * sourceQuality, -1.0, 1.0);
		double ablMixing = MathUtil.clamp(environment.windSourceAblMixingStrength() * sourceQuality, 0.0, 1.0);
		if (ablMixing <= 1.0e-6 && Math.abs(ablStability) <= 1.0e-6) {
			return 0.84;
		}

		double unstable = Math.max(0.0, ablStability);
		double stable = Math.max(0.0, -ablStability);
		double mixedUnstable = unstable * ablMixing;
		double stableSuppression = stable * (0.75 + 0.25 * (1.0 - ablMixing));
		return MathUtil.clamp(
				(0.84 + 0.18 * ablMixing) * (1.0 + 0.30 * mixedUnstable - 0.34 * stableSuppression),
				0.55,
				1.22
		);
	}

	private static double a4mcUpdraftAblTimeScaleMultiplier(DroneEnvironment environment) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double ablStability = MathUtil.clamp(environment.windSourceAblStability() * sourceQuality, -1.0, 1.0);
		double ablMixing = MathUtil.clamp(environment.windSourceAblMixingStrength() * sourceQuality, 0.0, 1.0);
		if (ablMixing <= 1.0e-6 && Math.abs(ablStability) <= 1.0e-6) {
			return 1.0;
		}

		double unstable = Math.max(0.0, ablStability);
		double stable = Math.max(0.0, -ablStability);
		double mixedUnstable = unstable * ablMixing;
		double stablePersistence = stable * (0.75 + 0.25 * (1.0 - ablMixing));
		return MathUtil.clamp(
				1.0 - 0.16 * ablMixing - 0.24 * mixedUnstable + 0.48 * stablePersistence,
				0.60,
				1.55
		);
	}

	private static double localizedWindBurbleIntensity(DroneEnvironment environment, double dirtyAir) {
		double ambientTurbulence = MathUtil.clamp(atmosphericTurbulenceIntensity(environment), 0.0, 1.5);
		double localDirtyAir = Math.max(0.0, dirtyAir - ambientTurbulence);
		return MathUtil.clamp(localDirtyAir + 0.18 * ambientTurbulence, 0.0, 1.8);
	}

	private Vec3 updateA4mcTerrainShearWind(DroneEnvironment environment, Vec3 targetMeanWind, double dirtyAir, double dtSeconds) {
		Vec3 target = a4mcTerrainShearWindTarget(environment, targetMeanWind, dirtyAir);
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double shearMagnitude = MathUtil.clamp(environment.windShearMagnitudePerBlock() * sourceQuality, 0.0, 5.0);
		double shelter = MathUtil.clamp(environment.windShelterFactor() * sourceQuality, 0.0, 1.0);
		double tau = MathUtil.clamp(
				(0.18 - 0.035 * Math.min(2.0, shearMagnitude) - 0.025 * shelter)
						* a4mcTerrainShearAblTimeScaleMultiplier(environment),
				0.045,
				0.360
		);
		double alpha = MathUtil.expSmoothing(dtSeconds, tau);
		a4mcTerrainShearVelocityWorldMetersPerSecond = a4mcTerrainShearVelocityWorldMetersPerSecond.add(
				target.subtract(a4mcTerrainShearVelocityWorldMetersPerSecond).multiply(alpha)
		);
		return a4mcTerrainShearVelocityWorldMetersPerSecond;
	}

	private static double a4mcTerrainShearAblTimeScaleMultiplier(DroneEnvironment environment) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double ablStability = MathUtil.clamp(environment.windSourceAblStability() * sourceQuality, -1.0, 1.0);
		double ablMixing = MathUtil.clamp(environment.windSourceAblMixingStrength() * sourceQuality, 0.0, 1.0);
		if (ablMixing <= 1.0e-6 && Math.abs(ablStability) <= 1.0e-6) {
			return 1.0;
		}

		double unstable = Math.max(0.0, ablStability);
		double stable = Math.max(0.0, -ablStability);
		double mixedUnstable = unstable * ablMixing;
		double stablePersistence = stable * (0.75 + 0.25 * (1.0 - ablMixing));
		return MathUtil.clamp(
				1.0 - 0.20 * ablMixing - 0.25 * mixedUnstable + 0.55 * stablePersistence,
				0.55,
				1.65
		);
	}

	private Vec3 a4mcTerrainShearWindTarget(DroneEnvironment environment, Vec3 targetMeanWind, double dirtyAir) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return Vec3.ZERO;
		}
		double shearMagnitude = MathUtil.clamp(environment.windShearMagnitudePerBlock() * sourceQuality, 0.0, 5.0);
		double shelter = MathUtil.clamp(environment.windShelterFactor() * sourceQuality, 0.0, 1.0);
		double updraft = a4mcSeparatedUpdraftMetersPerSecond(environment, sourceQuality);
		double horizontalWindSpeed = Math.hypot(targetMeanWind.x(), targetMeanWind.z());
		double shelterWindGate = smoothStep(0.8, 7.0, horizontalWindSpeed);
		if (shearMagnitude <= 1.0e-6 && Math.abs(updraft) <= 1.0e-6 && (shelter <= 1.0e-6 || shelterWindGate <= 1.0e-6)) {
			return Vec3.ZERO;
		}

		Vec3 windAxis = horizontalWindAxis(targetMeanWind);
		Vec3 crossAxis = new Vec3(-windAxis.z(), 0.0, windAxis.x());
		double shelterSignal = shelter
				* shelterWindGate
				* (0.20 + 0.12 * smoothStep(0.25, 2.0, shearMagnitude));
		double terrainSignal = MathUtil.clamp(
				0.42 * shearMagnitude * (0.50 + 0.50 * smoothStep(0.6, 7.0, horizontalWindSpeed))
						+ 0.10 * Math.abs(updraft)
						+ shelterSignal,
				0.0,
				2.40
		);
		double dirtyGain = MathUtil.clamp(0.72 + 0.18 * dirtyAir + 0.10 * shelter * shelterWindGate, 0.72, 1.10);
		double along = Math.sin(windGustPhaseA * 0.73 + 0.40) * terrainSignal * 0.35;
		double cross = Math.sin(windGustPhaseB * 0.91 + 1.10) * terrainSignal * 0.55;
		double vertical = Math.sin(windGustPhaseC * 0.67 + 2.00)
				* terrainSignal
				* (0.22 + 0.16 * smoothStep(0.25, 5.0, Math.abs(updraft)));
		return windAxis.multiply(along)
				.add(crossAxis.multiply(cross))
				.add(WORLD_UP.multiply(vertical))
				.multiply(dirtyGain)
				.clamp(-3.0, 3.0);
	}

	private static double a4mcWindSourceQualityFactor(DroneEnvironment environment) {
		if (!DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC.equals(environment.windSourceId())) {
			return 0.0;
		}
		return environment.windSourceQualityFactor();
	}

	private Vec3 updateDrydenTurbulence(DroneEnvironment environment, Vec3 targetMeanWind, double dtSeconds) {
		double windSpeed = targetMeanWind.length();
		double atmosphericTurbulence = atmosphericDrydenIntensity(environment);
		if (atmosphericTurbulence <= 1.0e-6 || windSpeed <= 0.5 || dtSeconds <= 0.0) {
			double alpha = MathUtil.expSmoothing(dtSeconds, 0.35);
			drydenFirstOrderVelocityWorldMetersPerSecond =
					drydenFirstOrderVelocityWorldMetersPerSecond.multiply(1.0 - alpha);
			drydenTransverseLagVelocityWorldMetersPerSecond =
					drydenTransverseLagVelocityWorldMetersPerSecond.multiply(1.0 - alpha);
			drydenTurbulenceVelocityWorldMetersPerSecond = drydenTurbulenceVelocityWorldMetersPerSecond.multiply(1.0 - alpha);
			return drydenTurbulenceVelocityWorldMetersPerSecond;
		}

		DrydenTurbulenceModel.Parameters dryden = DrydenTurbulenceModel.lowAltitude(drydenReferenceAltitudeMeters(environment), windSpeed);
		DrydenAblTimeScale drydenAblTimeScale = drydenAblTimeScale(environment);
		double intensityScale = MathUtil.clamp(atmosphericTurbulence / 1.8, 0.0, 1.0);
		double longitudinalTau = MathUtil.clamp(
				dryden.longitudinalTimeConstantSeconds() * drydenAblTimeScale.horizontalMultiplier(),
				0.10,
				12.0
		);
		double lateralTau = MathUtil.clamp(
				dryden.lateralTimeConstantSeconds() * drydenAblTimeScale.horizontalMultiplier(),
				0.10,
				12.0
		);
		double verticalTau = MathUtil.clamp(
				dryden.verticalTimeConstantSeconds() * drydenAblTimeScale.verticalMultiplier(),
				0.05,
				6.0
		);

		Vec3 longitudinalAxis = horizontalWindAxis(targetMeanWind);
		Vec3 lateralAxis = new Vec3(-longitudinalAxis.z(), 0.0, longitudinalAxis.x());
		Vec3 verticalAxis = new Vec3(0.0, 1.0, 0.0);
		double currentLongitudinal = drydenFirstOrderVelocityWorldMetersPerSecond.dot(longitudinalAxis);
		double currentLateral = drydenFirstOrderVelocityWorldMetersPerSecond.dot(lateralAxis);
		double currentVertical = drydenFirstOrderVelocityWorldMetersPerSecond.dot(verticalAxis);
		double currentLateralLag = drydenTransverseLagVelocityWorldMetersPerSecond.dot(lateralAxis);
		double currentVerticalLag = drydenTransverseLagVelocityWorldMetersPerSecond.dot(verticalAxis);
		double longitudinalFirstOrder = updateDrydenAxis(
				currentLongitudinal,
				dryden.longitudinalSigmaMetersPerSecond() * intensityScale,
				longitudinalTau,
				dtSeconds
		);
		double lateralFirstOrder = updateDrydenAxis(
				currentLateral,
				dryden.lateralSigmaMetersPerSecond() * intensityScale,
				lateralTau,
				dtSeconds
		);
		double verticalFirstOrder = updateDrydenAxis(
				currentVertical,
				dryden.verticalSigmaMetersPerSecond() * intensityScale,
				verticalTau,
				dtSeconds
		);
		double lateralLag = updateDrydenLag(currentLateralLag, lateralFirstOrder, lateralTau, dtSeconds);
		double verticalLag = updateDrydenLag(currentVerticalLag, verticalFirstOrder, verticalTau, dtSeconds);
		double lateral = DrydenTurbulenceModel.shapeTransverseAxis(lateralFirstOrder, lateralLag);
		double vertical = DrydenTurbulenceModel.shapeTransverseAxis(verticalFirstOrder, verticalLag);

		drydenFirstOrderVelocityWorldMetersPerSecond = longitudinalAxis.multiply(longitudinalFirstOrder)
				.add(lateralAxis.multiply(lateralFirstOrder))
				.add(verticalAxis.multiply(verticalFirstOrder));
		drydenTransverseLagVelocityWorldMetersPerSecond = lateralAxis.multiply(lateralLag)
				.add(verticalAxis.multiply(verticalLag));
		drydenTurbulenceVelocityWorldMetersPerSecond = longitudinalAxis.multiply(longitudinalFirstOrder)
				.add(lateralAxis.multiply(lateral))
				.add(verticalAxis.multiply(vertical));
		return drydenTurbulenceVelocityWorldMetersPerSecond;
	}

	private static double atmosphericDrydenIntensity(DroneEnvironment environment) {
		double baseTurbulence = MathUtil.clamp(atmosphericTurbulenceIntensity(environment), 0.0, 1.8);
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double ablStability = MathUtil.clamp(environment.windSourceAblStability() * sourceQuality, -1.0, 1.0);
		double ablMixing = MathUtil.clamp(environment.windSourceAblMixingStrength() * sourceQuality, 0.0, 1.0);
		if (ablMixing <= 1.0e-6 && Math.abs(ablStability) <= 1.0e-6) {
			return baseTurbulence;
		}

		double unstable = Math.max(0.0, ablStability);
		double stable = Math.max(0.0, -ablStability);
		double mixingMultiplier = 1.0
				+ 0.18 * ablMixing
				+ 0.42 * unstable * ablMixing
				- 0.34 * stable * (0.85 + 0.15 * (1.0 - ablMixing));
		double horizontalWindSpeed = Math.hypot(
				environment.windVelocityWorldMetersPerSecond().x(),
				environment.windVelocityWorldMetersPerSecond().z()
		);
		double convectiveFloor = 0.22
				* unstable
				* ablMixing
				* smoothStep(1.0, 8.0, horizontalWindSpeed);
		return MathUtil.clamp(baseTurbulence * MathUtil.clamp(mixingMultiplier, 0.55, 1.70) + convectiveFloor, 0.0, 1.8);
	}

	private static double atmosphericTurbulenceIntensity(DroneEnvironment environment) {
		return Math.max(environment.turbulenceIntensity(), environment.adoptedWindSourceTurbulenceIntensity());
	}

	private static DrydenAblTimeScale drydenAblTimeScale(DroneEnvironment environment) {
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		double ablStability = MathUtil.clamp(environment.windSourceAblStability() * sourceQuality, -1.0, 1.0);
		double ablMixing = MathUtil.clamp(environment.windSourceAblMixingStrength() * sourceQuality, 0.0, 1.0);
		if (ablMixing <= 1.0e-6 && Math.abs(ablStability) <= 1.0e-6) {
			return DrydenAblTimeScale.NEUTRAL;
		}

		double unstable = Math.max(0.0, ablStability);
		double stable = Math.max(0.0, -ablStability);
		double mixedUnstable = unstable * ablMixing;
		double stablePersistence = stable * (0.75 + 0.25 * (1.0 - ablMixing));
		double horizontalMultiplier = MathUtil.clamp(
				1.0 - 0.16 * ablMixing - 0.24 * mixedUnstable + 0.45 * stablePersistence,
				0.55,
				1.55
		);
		double verticalMultiplier = MathUtil.clamp(
				1.0 - 0.25 * ablMixing - 0.30 * mixedUnstable + 0.70 * stablePersistence,
				0.45,
				1.80
		);
		return new DrydenAblTimeScale(horizontalMultiplier, verticalMultiplier);
	}

	private double updateDrydenAxis(double currentValue, double sigmaMetersPerSecond, double timeConstantSeconds, double dtSeconds) {
		double phi = Math.exp(-dtSeconds / Math.max(1.0e-6, timeConstantSeconds));
		double innovationScale = sigmaMetersPerSecond * Math.sqrt(Math.max(0.0, 1.0 - phi * phi));
		return currentValue * phi + innovationScale * nextDrydenGaussian();
	}

	private static double updateDrydenLag(double currentValue, double targetValue, double timeConstantSeconds, double dtSeconds) {
		double alpha = MathUtil.expSmoothing(dtSeconds, Math.max(1.0e-6, timeConstantSeconds));
		return currentValue + (targetValue - currentValue) * alpha;
	}

	private double nextDrydenGaussian() {
		if (hasDrydenSpareGaussian) {
			hasDrydenSpareGaussian = false;
			return drydenSpareGaussian;
		}

		double u1 = Math.max(1.0e-12, nextDrydenUnitDouble());
		double u2 = nextDrydenUnitDouble();
		double radius = Math.sqrt(-2.0 * Math.log(u1));
		double angle = Math.PI * 2.0 * u2;
		drydenSpareGaussian = radius * Math.sin(angle);
		hasDrydenSpareGaussian = true;
		return radius * Math.cos(angle);
	}

	private double nextDrydenUnitDouble() {
		drydenRandomState += 0x9E3779B97F4A7C15L;
		long value = drydenRandomState;
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		value = value ^ (value >>> 31);
		return (value >>> 11) * 0x1.0p-53;
	}

	private record DrydenAblTimeScale(double horizontalMultiplier, double verticalMultiplier) {
		private static final DrydenAblTimeScale NEUTRAL = new DrydenAblTimeScale(1.0, 1.0);
	}

	private record A4mcAblShape(double ablMixing, double mixedUnstable, double stableSuppression) {
	}

	private static double drydenReferenceAltitudeMeters(DroneEnvironment environment) {
		if (Double.isFinite(environment.groundClearanceMeters())) {
			return environment.groundClearanceMeters();
		}
		return 6.0;
	}

	private static Vec3 horizontalWindAxis(Vec3 windVelocityWorldMetersPerSecond) {
		Vec3 horizontal = new Vec3(windVelocityWorldMetersPerSecond.x(), 0.0, windVelocityWorldMetersPerSecond.z());
		return horizontal.lengthSquared() > 1.0e-9 ? horizontal.normalized() : new Vec3(1.0, 0.0, 0.0);
	}

	private Vec3 calculateRateControllerTorque(DroneInput input, double dtSeconds) {
		state.setFlightMode(input.flightMode());
		if (!input.armed()) {
			resetControlLoops();
			return Vec3.ZERO;
		}

		Vec3 targetRates = targetRatesRadiansPerSecond(input);
		Vec3 targetRateAcceleration = targetRateAcceleration(targetRates, dtSeconds);
		Vec3 feedForwardTorque = updateFeedForwardTorque(targetRateAcceleration, dtSeconds);
		double antiGravityPulse = updateAntiGravityTransient(input.throttle(), dtSeconds);
		Vec3 integralRelax = pidIntegralRelaxAxes(targetRateAcceleration);
		Vec3 integratorAttenuation = new Vec3(
				1.0 - integralRelax.x(),
				1.0 - integralRelax.y(),
				1.0 - integralRelax.z()
		);
		double pitchPidAttenuation = throttlePidAttenuation(input.throttle(), config.pitchGains());
		double yawPidAttenuation = throttlePidAttenuation(input.throttle(), config.yawGains());
		double rollPidAttenuation = throttlePidAttenuation(input.throttle(), config.rollGains());
		double sensorPidAuthority = sensorClippingPidAuthority();
		pitchPidAttenuation *= sensorPidAuthority;
		yawPidAttenuation *= sensorPidAuthority;
		rollPidAttenuation *= sensorPidAuthority;
		integratorAttenuation = integratorAttenuation.multiply(sensorPidAuthority);
		state.setPidAttenuation((pitchPidAttenuation + yawPidAttenuation + rollPidAttenuation) / 3.0);
		state.setAntiGravityBoost(antiGravityPulse * maxAntiGravityGain());
		state.setPidIntegralRelaxAxes(integralRelax);

		Vec3 gyroRates = state.gyroAngularVelocityBodyRadiansPerSecond();
		Vec3 gyroRateAcceleration = gyroRateAcceleration(gyroRates, dtSeconds);
		Vec3 derivativeInput = gyroRateAcceleration.multiply(-1.0);
		double pitchDTermCutoffHz = dynamicDTermLowPassCutoffHertz(config.pitchGains(), input.throttle());
		double yawDTermCutoffHz = dynamicDTermLowPassCutoffHertz(config.yawGains(), input.throttle());
		double rollDTermCutoffHz = dynamicDTermLowPassCutoffHertz(config.rollGains(), input.throttle());
		Vec3 rateError = targetRates.subtract(gyroRates);
		state.setTargetRatesBodyRadiansPerSecond(targetRates);
		state.setRateErrorBodyRadiansPerSecond(rateError);
		state.setPidDTermLowPassCutoffHertz((pitchDTermCutoffHz + yawDTermCutoffHz + rollDTermCutoffHz) / 3.0);

		double pitchOutput = pitchPid.stepWithDerivativeInput(
				rateError.x(),
				derivativeInput.x(),
				dtSeconds,
				pitchPidAttenuation,
				antiGravityPulse * config.pitchGains().antiGravityGain(),
				pitchPidAttenuation,
				feedForwardTorque.x(),
				integratorAttenuation.x(),
				pitchDTermCutoffHz
		);
		double yawOutput = yawPid.stepWithDerivativeInput(
				rateError.y(),
				derivativeInput.y(),
				dtSeconds,
				yawPidAttenuation,
				antiGravityPulse * config.yawGains().antiGravityGain(),
				yawPidAttenuation,
				feedForwardTorque.y(),
				integratorAttenuation.y(),
				yawDTermCutoffHz
		);
		double rollOutput = rollPid.stepWithDerivativeInput(
				rateError.z(),
				derivativeInput.z(),
				dtSeconds,
				rollPidAttenuation,
				antiGravityPulse * config.rollGains().antiGravityGain(),
				rollPidAttenuation,
				feedForwardTorque.z(),
				integratorAttenuation.z(),
				rollDTermCutoffHz
		);
		Vec3 output = new Vec3(pitchOutput, yawOutput, rollOutput);
		state.setPidProportionalTorqueBodyNewtonMeters(new Vec3(
				pitchPid.lastProportionalTerm(),
				yawPid.lastProportionalTerm(),
				rollPid.lastProportionalTerm()
		));
		state.setPidIntegralTorqueBodyNewtonMeters(new Vec3(
				pitchPid.lastIntegralTerm(),
				yawPid.lastIntegralTerm(),
				rollPid.lastIntegralTerm()
		));
		state.setPidDerivativeTorqueBodyNewtonMeters(new Vec3(
				pitchPid.lastDerivativeTerm(),
				yawPid.lastDerivativeTerm(),
				rollPid.lastDerivativeTerm()
		));
		state.setPidFeedForwardTorqueBodyNewtonMeters(new Vec3(
				pitchPid.lastFeedForwardTerm(),
				yawPid.lastFeedForwardTerm(),
				rollPid.lastFeedForwardTerm()
		));
		state.setPidOutputTorqueBodyNewtonMeters(output);
		return output;
	}

	private Vec3 gyroRateAcceleration(Vec3 gyroRates, double dtSeconds) {
		Vec3 acceleration = hasPreviousPidGyroRates
				? gyroRates.subtract(previousPidGyroRatesBodyRadiansPerSecond).multiply(1.0 / Math.max(dtSeconds, 1.0e-6))
				: Vec3.ZERO;
		previousPidGyroRatesBodyRadiansPerSecond = gyroRates;
		hasPreviousPidGyroRates = true;
		return acceleration;
	}

	private Vec3 targetRatesRadiansPerSecond(DroneInput input) {
		Vec3 acroRates = acroTargetRatesRadiansPerSecond(input);
		FlightMode mode = input.flightMode();
		if (mode == FlightMode.ANGLE) {
			return levelTargetRatesRadiansPerSecond(input, 1.0, acroRates.y());
		}
		if (mode == FlightMode.HORIZON) {
			double selfLevelBlend = horizonSelfLevelBlendConfigured(input);
			Vec3 levelRates = levelTargetRatesRadiansPerSecond(input, selfLevelBlend, acroRates.y());
			return new Vec3(
					MathUtil.lerp(acroRates.x(), levelRates.x(), selfLevelBlend),
					acroRates.y(),
					MathUtil.lerp(acroRates.z(), levelRates.z(), selfLevelBlend)
			);
		}

		state.setLevelTargetAttitudeRadians(Vec3.ZERO);
		state.setLevelAttitudeErrorRadians(Vec3.ZERO);
		state.setSelfLevelBlend(0.0);
		return acroRates;
	}

	private Vec3 acroTargetRatesRadiansPerSecond(DroneInput input) {
		return new Vec3(
				shapeRateInput(input.pitch(), config.rateExpo().x(), config.rateSuper().x()) * config.maxPitchRateRadiansPerSecond(),
				shapeRateInput(input.yaw(), config.rateExpo().y(), config.rateSuper().y()) * config.maxYawRateRadiansPerSecond(),
				shapeRateInput(input.roll(), config.rateExpo().z(), config.rateSuper().z()) * config.maxRollRateRadiansPerSecond()
		);
	}

	private Vec3 levelTargetRatesRadiansPerSecond(DroneInput input, double selfLevelBlend, double yawRateRadiansPerSecond) {
		Vec3 estimatedEuler = state.estimatedOrientation().toEulerXYZRadians();
		double targetPitch = input.pitch() * config.selfLevelMaxAngleRadians();
		double targetRoll = input.roll() * config.selfLevelMaxAngleRadians();
		double pitchError = normalizeRadians(targetPitch - estimatedEuler.x());
		double rollError = normalizeRadians(targetRoll - estimatedEuler.z());
		state.setLevelTargetAttitudeRadians(new Vec3(targetPitch, 0.0, targetRoll));
		state.setLevelAttitudeErrorRadians(new Vec3(pitchError, 0.0, rollError));
		state.setSelfLevelBlend(selfLevelBlend);
		return new Vec3(
				MathUtil.clamp(pitchError * config.selfLevelRateGain(), -config.maxPitchRateRadiansPerSecond(), config.maxPitchRateRadiansPerSecond()),
				yawRateRadiansPerSecond,
				MathUtil.clamp(rollError * config.selfLevelRateGain(), -config.maxRollRateRadiansPerSecond(), config.maxRollRateRadiansPerSecond())
		);
	}

	private static double horizonSelfLevelBlend(DroneInput input) {
		return horizonSelfLevelBlend(input, DroneConfig.DEFAULT_HORIZON_TRANSITION_START_STICK, DroneConfig.DEFAULT_HORIZON_TRANSITION_END_STICK);
	}

	private double horizonSelfLevelBlendConfigured(DroneInput input) {
		return horizonSelfLevelBlend(input, config.horizonTransitionStartStick(), config.horizonTransitionEndStick());
	}

	private static double horizonSelfLevelBlend(DroneInput input, double transitionStartStick, double transitionEndStick) {
		double stick = Math.max(Math.abs(input.pitch()), Math.abs(input.roll()));
		return 1.0 - MathUtil.clamp(
				(stick - transitionStartStick) / (transitionEndStick - transitionStartStick),
				0.0,
				1.0
		);
	}

	private static double normalizeRadians(double radians) {
		double wrapped = radians % (Math.PI * 2.0);
		if (wrapped > Math.PI) {
			wrapped -= Math.PI * 2.0;
		}
		if (wrapped < -Math.PI) {
			wrapped += Math.PI * 2.0;
		}
		return wrapped;
	}

	private Vec3 targetRateAcceleration(Vec3 targetRates, double dtSeconds) {
		Vec3 acceleration = hasPreviousTargetRates
				? targetRates.subtract(previousTargetRatesRadiansPerSecond).multiply(1.0 / Math.max(dtSeconds, 1.0e-6))
				: Vec3.ZERO;
		previousTargetRatesRadiansPerSecond = targetRates;
		hasPreviousTargetRates = true;
		return acceleration;
	}

	private Vec3 pidIntegralRelaxAxes(Vec3 targetRateAcceleration) {
		if (config.pidIntegralRelaxStrength() <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 authority = state.mixerAxisAuthority();
		return new Vec3(
				pidAxisIntegralRelax(authority.x(), targetRateAcceleration.x()),
				pidAxisIntegralRelax(authority.y(), targetRateAcceleration.y()),
				pidAxisIntegralRelax(authority.z(), targetRateAcceleration.z())
		);
	}

	private double pidAxisIntegralRelax(double mixerAxisAuthority, double targetRateAcceleration) {
		double authorityRelax = 1.0 - MathUtil.clamp(mixerAxisAuthority, 0.0, 1.0);
		double setpointRelax = MathUtil.clamp(Math.abs(targetRateAcceleration) / Math.toRadians(4200.0), 0.0, 1.0) * 0.70;
		return MathUtil.clamp(config.pidIntegralRelaxStrength() * Math.max(authorityRelax, setpointRelax), 0.0, 1.0);
	}

	private double updateAntiGravityTransient(double throttle, double dtSeconds) {
		double throttleRate = Math.abs(throttle - previousThrottle) / Math.max(dtSeconds, 1.0e-6);
		previousThrottle = throttle;
		double targetTransient = MathUtil.clamp((throttleRate - 0.65) / 3.0, 0.0, 1.0);
		double timeConstant = targetTransient > antiGravityTransient ? 0.012 : 0.220;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		antiGravityTransient += (targetTransient - antiGravityTransient) * alpha;
		return antiGravityTransient;
	}

	private Vec3 updateFeedForwardTorque(Vec3 targetRateAcceleration, double dtSeconds) {
		Vec3 target = new Vec3(
				feedForwardTorque(config.pitchGains(), targetRateAcceleration.x()),
				feedForwardTorque(config.yawGains(), targetRateAcceleration.y()),
				feedForwardTorque(config.rollGains(), targetRateAcceleration.z())
		);
		feedForwardTorqueBody = new Vec3(
				feedForwardPulse(feedForwardTorqueBody.x(), target.x(), dtSeconds),
				feedForwardPulse(feedForwardTorqueBody.y(), target.y(), dtSeconds),
				feedForwardPulse(feedForwardTorqueBody.z(), target.z(), dtSeconds)
		);
		return feedForwardTorqueBody;
	}

	private static double feedForwardPulse(double current, double target, double dtSeconds) {
		if (Math.abs(target) > 1.0e-9
				&& (Math.signum(current) != Math.signum(target) || Math.abs(target) > Math.abs(current))) {
			return target;
		}

		double alpha = MathUtil.expSmoothing(dtSeconds, 0.045);
		return current + (target - current) * alpha;
	}

	private double maxAntiGravityGain() {
		return Math.max(
				config.pitchGains().antiGravityGain(),
				Math.max(config.yawGains().antiGravityGain(), config.rollGains().antiGravityGain())
		);
	}

	private static double throttlePidAttenuation(double throttle, PidGains gains) {
		double denominator = Math.max(1.0e-6, 1.0 - gains.tpaBreakpoint());
		double tpa = MathUtil.clamp((throttle - gains.tpaBreakpoint()) / denominator, 0.0, 1.0);
		return 1.0 - gains.tpaStrength() * tpa;
	}

	private double sensorClippingPidAuthority() {
		double gyroClip = MathUtil.clamp(state.gyroClipIntensity(), 0.0, 1.0);
		double accelerometerClip = MathUtil.clamp(state.accelerometerClipIntensity(), 0.0, 1.0);
		double gyroLoss = 0.55 * smoothStep(0.02, 0.45, gyroClip);
		double accelerometerLoss = 0.18 * smoothStep(0.05, 0.75, accelerometerClip);
		return MathUtil.clamp((1.0 - gyroLoss) * (1.0 - accelerometerLoss), 0.35, 1.0);
	}

	private double dynamicDTermLowPassCutoffHertz(PidGains gains, double throttle) {
		double configuredCutoff = gains.dTermLowPassCutoffHz();
		if (configuredCutoff <= 1.0e-6) {
			return 0.0;
		}

		double motorPower = state.averageMotorPower(config);
		double throttleOpen = smoothStep(0.10, 0.82, throttle);
		double rpmOpen = smoothStep(0.10, 0.78, motorPower);
		double authority = Math.max(throttleOpen, rpmOpen);
		double lowCutoff = Math.min(configuredCutoff, Math.max(5.0, configuredCutoff * 0.42));
		double vibrationFoldback = 1.0 - 0.32 * smoothStep(0.035, 0.180, state.rotorVibration());
		double bladePassRoughness = Math.max(
				state.averageRotorBladePassRippleIntensity(),
				state.maxRotorBladePassRippleIntensity() * 0.72
		);
		double bladePassFoldback = 1.0 - 0.16 * smoothStep(0.006, 0.038, bladePassRoughness);
		double separatedFlowRoughness = Math.max(state.averageRotorStallIntensity(), state.vortexRingStateIntensity());
		double separatedFlowFoldback = 1.0 - 0.14 * smoothStep(0.12, 0.70, separatedFlowRoughness);
		double dynamicCutoff = MathUtil.lerp(lowCutoff, configuredCutoff, authority)
				* vibrationFoldback
				* bladePassFoldback
				* separatedFlowFoldback;
		return MathUtil.clamp(dynamicCutoff, Math.max(1.0, configuredCutoff * 0.30), configuredCutoff);
	}

	private static double feedForwardTorque(PidGains gains, double targetRateAccelerationRadiansPerSecondSquared) {
		double torque = targetRateAccelerationRadiansPerSecondSquared * gains.feedForward();
		double limit = Math.max(0.04, gains.p() * 8.0);
		return MathUtil.clamp(torque, -limit, limit);
	}

	private static double shapeRateInput(double input, double expo, double superRate) {
		double clamped = MathUtil.clamp(input, -1.0, 1.0);
		double expoClamped = MathUtil.clamp(expo, 0.0, 1.0);
		double superRateClamped = MathUtil.clamp(superRate, 0.0, 0.95);
		double centerSensitivityFraction = (1.0 - expoClamped) * (1.0 - superRateClamped);
		double commandAbs = Math.abs(clamped);
		double commandSquared = clamped * clamped;
		double commandFifth = commandSquared * commandSquared * clamped;
		double expoCurve = commandAbs * (commandFifth * expoClamped + clamped * (1.0 - expoClamped));
		double stickMovementFraction = Math.max(0.0, 1.0 - centerSensitivityFraction);
		return MathUtil.clamp(
				clamped * centerSensitivityFraction + stickMovementFraction * expoCurve,
				-1.0,
				1.0
		);
	}

	private void mixRotorThrusts(DroneInput input, Vec3 requestedTorqueBody) {
		double[] baseThrusts = new double[targetRotorThrusts.length];
		double[] mixedThrusts = new double[targetRotorThrusts.length];
		double directThrottleFraction = input.armed()
				? config.throttleCommandToDirectThrustFraction(input.throttle())
				: 0.0;
		double[] torqueMix = input.armed()
				? allocateTorqueMixDeltas(config.rotors(), config.centerOfMassOffsetBodyMeters(), requestedTorqueBody)
				: new double[targetRotorThrusts.length];

		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double baseThrust = directThrottleFraction * rotor.maxThrustNewtons();
			baseThrusts[i] = baseThrust;
			mixedThrusts[i] = baseThrust + torqueMix[i];
		}
		applyCoaxialLoadBias(mixedThrusts, input.armed(), directThrottleFraction);

		double lowDesaturationPressure = 0.0;
		double highDesaturationPressure = 0.0;
		if (input.armed() && config.airmodeStrength() > 0.0) {
			double upwardShift = 0.0;
			for (int i = 0; i < config.rotors().size(); i++) {
				double idleThrust = config.rotors().get(i).maxThrustNewtons() * config.motorIdleThrustFraction();
				upwardShift = Math.max(upwardShift, idleThrust - mixedThrusts[i]);
			}
			if (upwardShift > 0.0) {
				double shift = upwardShift * config.airmodeStrength();
				lowDesaturationPressure = Math.max(lowDesaturationPressure, shift / averageMaxRotorThrust());
				for (int i = 0; i < mixedThrusts.length; i++) {
					mixedThrusts[i] += shift;
				}
			}

			double downwardShift = 0.0;
			for (int i = 0; i < config.rotors().size(); i++) {
				downwardShift = Math.max(downwardShift, mixedThrusts[i] - config.rotors().get(i).maxThrustNewtons());
			}
			if (downwardShift > 0.0) {
				double shift = downwardShift * config.airmodeStrength();
				highDesaturationPressure = Math.max(highDesaturationPressure, shift / averageMaxRotorThrust());
				for (int i = 0; i < mixedThrusts.length; i++) {
					mixedThrusts[i] -= shift;
				}
			}
		}

		double saturation = 0.0;
		double lowSaturation = 0.0;
		double highSaturation = 0.0;
		double lowHeadroom = input.armed() ? 1.0 : 0.0;
		double highHeadroom = input.armed() ? 1.0 : 0.0;
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double minThrust = input.armed() ? rotor.maxThrustNewtons() * config.motorIdleThrustFraction() : 0.0;
			double maxThrust = input.armed() ? rotor.maxThrustNewtons() : 0.0;
			double clamped = MathUtil.clamp(mixedThrusts[i], minThrust, maxThrust);
			double lowClip = Math.max(0.0, minThrust - mixedThrusts[i]);
			double highClip = Math.max(0.0, mixedThrusts[i] - maxThrust);
			double normalizedLowClip = lowClip / rotor.maxThrustNewtons();
			double normalizedHighClip = highClip / rotor.maxThrustNewtons();
			lowSaturation += normalizedLowClip;
			highSaturation += normalizedHighClip;
			saturation += normalizedLowClip + normalizedHighClip;
			double thrustRange = Math.max(1.0e-9, maxThrust - minThrust);
			lowHeadroom = Math.min(lowHeadroom, (clamped - minThrust) / thrustRange);
			highHeadroom = Math.min(highHeadroom, (maxThrust - clamped) / thrustRange);
			targetRotorThrusts[i] = clamped;
		}
		int rotorCount = Math.max(1, targetRotorThrusts.length);
		state.setMixerLowSaturation(Math.max(lowDesaturationPressure, lowSaturation / rotorCount));
		state.setMixerHighSaturation(Math.max(highDesaturationPressure, highSaturation / rotorCount));
		state.setMixerLowHeadroom(lowHeadroom);
		state.setMixerHighHeadroom(highHeadroom);
		state.setMixerSaturation(Math.max(Math.max(lowDesaturationPressure, highDesaturationPressure), saturation / rotorCount));
		Vec3 achievedTorqueBody = mixerOutputTorqueFromThrustDeltas(
				config.rotors(),
				config.centerOfMassOffsetBodyMeters(),
				baseThrusts,
				targetRotorThrusts
		);
		state.setMixerOutputTorqueBodyNewtonMeters(achievedTorqueBody);
		state.setMixerAxisAuthority(mixerAxisAuthority(requestedTorqueBody, achievedTorqueBody));
	}

	private void applyCoaxialLoadBias(double[] mixedThrusts, boolean armed, double throttle) {
		for (int i = 0; i < state.motorCount(); i++) {
			state.setRotorCoaxialLoadBias(i, 0.0);
			state.setRotorCoaxialLoadBiasTarget(i, 0.0);
			state.setRotorCoaxialLoadBiasClipping(i, 0.0);
			state.setRotorCoaxialAllocationLoadFraction(i, 0.0);
			state.setRotorCoaxialAllocationCommandRatio(i, 1.0);
			state.setRotorCoaxialAllocationMechanicalGainPercent(i, 0.0);
			state.setRotorCoaxialAllocationElectricalGainPercent(i, 0.0);
			state.setRotorCoaxialAllocationUncertaintyPercent(i, 0.0);
		}
		if (!armed || mixedThrusts == null || config.rotors().size() < 2) {
			return;
		}

		boolean[] paired = new boolean[config.rotors().size()];
		int[] upperPairIndices = new int[config.rotors().size() / 2];
		int[] lowerPairIndices = new int[config.rotors().size() / 2];
		int pairCount = 0;
		for (int upperIndex = 0; upperIndex < config.rotors().size(); upperIndex++) {
			if (paired[upperIndex]) {
				continue;
			}
			int lowerIndex = coaxialLowerRotorIndex(upperIndex, paired);
			if (lowerIndex < 0) {
				continue;
			}
			upperPairIndices[pairCount] = upperIndex;
			lowerPairIndices[pairCount] = lowerIndex;
			pairCount++;
			paired[upperIndex] = true;
			paired[lowerIndex] = true;
		}
		if (pairCount < 2) {
			return;
		}

		for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
			int upperIndex = upperPairIndices[pairIndex];
			int lowerIndex = lowerPairIndices[pairIndex];
			RotorSpec upper = config.rotors().get(upperIndex);
			RotorSpec lower = config.rotors().get(lowerIndex);
			double pairTotalThrust = mixedThrusts[upperIndex] + mixedThrusts[lowerIndex];
			double pairMeanThrust = pairTotalThrust * 0.5;
			if (pairMeanThrust <= 1.0e-6) {
				continue;
			}

			CoaxialLoadBiasTarget target = coaxialLoadBiasTarget(
					upper,
					lower,
					upperIndex,
					lowerIndex,
					throttle,
					pairMeanThrust
			);
			double targetBias = target.bias();
			state.setRotorCoaxialLoadBiasTarget(upperIndex, targetBias);
			state.setRotorCoaxialLoadBiasTarget(lowerIndex, -targetBias);
			state.setRotorCoaxialAllocationLoadFraction(upperIndex, target.loadFraction());
			state.setRotorCoaxialAllocationLoadFraction(lowerIndex, target.loadFraction());
			state.setRotorCoaxialAllocationCommandRatio(upperIndex, target.commandMapRatio());
			state.setRotorCoaxialAllocationCommandRatio(lowerIndex, target.commandMapRatio());
			state.setRotorCoaxialAllocationMechanicalGainPercent(upperIndex, target.mechanicalGainPercent());
			state.setRotorCoaxialAllocationMechanicalGainPercent(lowerIndex, target.mechanicalGainPercent());
			state.setRotorCoaxialAllocationElectricalGainPercent(upperIndex, target.electricalGainPercent());
			state.setRotorCoaxialAllocationElectricalGainPercent(lowerIndex, target.electricalGainPercent());
			state.setRotorCoaxialAllocationUncertaintyPercent(upperIndex, target.uncertaintyPercent());
			state.setRotorCoaxialAllocationUncertaintyPercent(lowerIndex, target.uncertaintyPercent());
			if (targetBias <= 1.0e-6) {
				continue;
			}

			double upperMax = upper.maxThrustNewtons();
			double lowerMax = lower.maxThrustNewtons();
			double lowerMin = lowerMax * config.motorIdleThrustFraction();
			double requestedShift = pairMeanThrust * targetBias;
			double availableShift = Math.min(upperMax - mixedThrusts[upperIndex], mixedThrusts[lowerIndex] - lowerMin);
			double shift = MathUtil.clamp(requestedShift, 0.0, Math.max(0.0, availableShift));
			double actualBias = MathUtil.clamp(shift / pairMeanThrust, 0.0, COAXIAL_LOAD_BIAS_MAX);
			double clipping = Math.max(0.0, targetBias - actualBias);
			state.setRotorCoaxialLoadBiasClipping(upperIndex, clipping);
			state.setRotorCoaxialLoadBiasClipping(lowerIndex, clipping);
			if (shift > 1.0e-6) {
				mixedThrusts[upperIndex] += shift;
				mixedThrusts[lowerIndex] -= shift;
				state.setRotorCoaxialLoadBias(upperIndex, actualBias);
				state.setRotorCoaxialLoadBias(lowerIndex, -actualBias);
			}
		}
	}

	private int coaxialLowerRotorIndex(int upperIndex, boolean[] paired) {
		RotorSpec upper = config.rotors().get(upperIndex);
		int lowerIndex = -1;
		double bestVerticalSeparation = Double.POSITIVE_INFINITY;
		for (int candidateIndex = 0; candidateIndex < config.rotors().size(); candidateIndex++) {
			if (candidateIndex == upperIndex || paired[candidateIndex]) {
				continue;
			}

			RotorSpec lower = config.rotors().get(candidateIndex);
			double verticalSeparation = upper.positionBodyMeters().y() - lower.positionBodyMeters().y();
			if (verticalSeparation <= 0.0 || !isCoaxialRotorPair(upper, lower, verticalSeparation)) {
				continue;
			}
			if (verticalSeparation < bestVerticalSeparation) {
				bestVerticalSeparation = verticalSeparation;
				lowerIndex = candidateIndex;
			}
		}
		return lowerIndex;
	}

	private static boolean isCoaxialRotorPair(RotorSpec upper, RotorSpec lower, double verticalSeparation) {
		double averageRadius = 0.5 * (upper.radiusMeters() + lower.radiusMeters());
		double lateralDistance = Math.hypot(
				upper.positionBodyMeters().x() - lower.positionBodyMeters().x(),
				upper.positionBodyMeters().z() - lower.positionBodyMeters().z()
		);
		double spacingRatio = verticalSeparation / Math.max(1.0e-6, averageRadius * 2.0);
		double radiusMismatch = Math.abs(upper.radiusMeters() - lower.radiusMeters()) / Math.max(1.0e-6, averageRadius);
		return lateralDistance <= averageRadius * 0.16
				&& spacingRatio >= 0.18
				&& spacingRatio <= 1.35
				&& radiusMismatch <= 0.20
				&& upper.spinDirection() == -lower.spinDirection()
				&& rotorAxisBody(upper).dot(rotorAxisBody(lower)) > 0.96;
	}

	private record CoaxialLoadBiasTarget(
			double bias,
			double loadFraction,
			double commandMapRatio,
			double mechanicalGainPercent,
			double electricalGainPercent,
			double uncertaintyPercent
	) {
	}

	private double coaxialSpacingRatio(RotorSpec upper, RotorSpec lower) {
		double averageRadius = 0.5 * (upper.radiusMeters() + lower.radiusMeters());
		return (upper.positionBodyMeters().y() - lower.positionBodyMeters().y())
				/ Math.max(1.0e-6, averageRadius * 2.0);
	}

	private static double coaxialLoadFraction(RotorSpec upper, RotorSpec lower, double pairMeanThrust) {
		return MathUtil.clamp(
				pairMeanThrust / Math.max(1.0e-6, 0.5 * (upper.maxThrustNewtons() + lower.maxThrustNewtons())),
				0.0,
				1.0
		);
	}

	private CoaxialLoadBiasTarget coaxialLoadBiasTarget(
			RotorSpec upper,
			RotorSpec lower,
			int upperIndex,
			int lowerIndex,
			double throttle,
			double pairMeanThrust
	) {
		double spacingRatio = coaxialSpacingRatio(upper, lower);
		double spacingWindow = coaxialBenchmarkSpacingEfficiencyWindow(spacingRatio);
		double loadFraction = coaxialLoadFraction(upper, lower, pairMeanThrust);
		double loadWindow = smoothStep(
				Math.max(0.02, config.motorIdleThrustFraction()),
				0.52,
				Math.max(loadFraction, throttle)
		);
		double upperHeadroom = 1.0 - smoothStep(0.82, 0.98, loadFraction);
		double lowerWake = MathUtil.clamp(state.rotorWakeInterferenceIntensity(lowerIndex), 0.0, 1.0);
		double wakeWindow = 0.35 + 0.65 * smoothStep(0.08, 0.55, lowerWake);
		double lowerLoss = Math.max(0.0, 1.0 - state.rotorWakeThrustScale(lowerIndex));
		double lossWindow = 0.75 + 0.25 * smoothStep(0.02, 0.12, lowerLoss);
		double spacingBiasTarget = COAXIAL_LOAD_BIAS_MAX
				* spacingWindow
				* loadWindow
				* upperHeadroom
				* wakeWindow
				* lossWindow;
		double commandMapRatio = coaxialCommandMapAllocationRatio(spacingRatio, loadFraction);
		double mechanicalGainPercent = coaxialCommandMapMechanicalGainPercent(spacingRatio, loadFraction);
		double electricalGainPercent = coaxialCommandMapElectricalGainPercent(spacingRatio, loadFraction);
		double uncertaintyPercent = coaxialCommandMapAllocationUncertaintyPercent(spacingRatio, loadFraction);
		double commandMapBiasTarget = coaxialCommandMapLoadBias(spacingRatio, loadFraction)
				* upperHeadroom
				* wakeWindow
				* lossWindow;
		double bias = MathUtil.clamp(
				Math.max(spacingBiasTarget, commandMapBiasTarget),
				0.0,
				COAXIAL_LOAD_BIAS_MAX
		);
		return new CoaxialLoadBiasTarget(
				bias,
				loadFraction,
				commandMapRatio,
				mechanicalGainPercent,
				electricalGainPercent,
				uncertaintyPercent
		);
	}

	private static double coaxialCommandMapLoadBias(double spacingRatio, double loadFraction) {
		return CoaxialAllocationCalibration.commandMapLoadBias(spacingRatio, loadFraction);
	}

	private static double coaxialCommandMapAllocationRatio(double spacingRatio, double loadFraction) {
		return CoaxialAllocationCalibration.commandMapAllocationRatio(spacingRatio, loadFraction);
	}

	private static double coaxialCommandMapMechanicalGainPercent(double spacingRatio, double loadFraction) {
		return CoaxialAllocationCalibration.commandMapMechanicalGainPercent(spacingRatio, loadFraction);
	}

	private static double coaxialCommandMapElectricalGainPercent(double spacingRatio, double loadFraction) {
		return CoaxialAllocationCalibration.commandMapElectricalGainPercent(spacingRatio, loadFraction);
	}

	private static double coaxialCommandMapAllocationUncertaintyPercent(double spacingRatio, double loadFraction) {
		return CoaxialAllocationCalibration.commandMapAllocationUncertaintyPercent(spacingRatio, loadFraction);
	}

	private double coaxialAllocationMechanicalPowerScale(int index) {
		return coaxialAllocationMechanicalPowerScale(
				state.rotorCoaxialAllocationMechanicalGainPercent(index),
				state.rotorCoaxialLoadBiasTarget(index),
				state.rotorCoaxialLoadBias(index),
				state.rotorCoaxialAllocationLoadFraction(index)
		);
	}

	private static double coaxialAllocationMechanicalPowerScale(
			double mechanicalGainPercent,
			double targetBias,
			double actualBias,
			double loadFraction
	) {
		double mechanicalGain = Math.max(0.0, mechanicalGainPercent) * 0.01;
		if (mechanicalGain <= 1.0e-6) {
			return 1.0;
		}

		double targetMagnitude = Math.abs(targetBias);
		double actualMagnitude = Math.abs(actualBias);
		if (targetMagnitude <= 1.0e-6 || actualMagnitude <= 1.0e-6) {
			return 1.0;
		}

		double realization = MathUtil.clamp(actualMagnitude / targetMagnitude, 0.0, 1.0);
		double loadGate = smoothStep(0.08, 0.22, loadFraction);
		return MathUtil.clamp(1.0 - mechanicalGain * realization * loadGate, 0.88, 1.0);
	}

	private double coaxialAllocationElectricalPowerScale(int index) {
		return coaxialAllocationElectricalPowerScale(
				state.rotorCoaxialAllocationElectricalGainPercent(index),
				state.rotorCoaxialLoadBiasTarget(index),
				state.rotorCoaxialLoadBias(index),
				state.rotorCoaxialAllocationLoadFraction(index)
		);
	}

	private static double coaxialAllocationElectricalPowerScale(
			double electricalGainPercent,
			double targetBias,
			double actualBias,
			double loadFraction
	) {
		double electricalGain = Math.max(0.0, electricalGainPercent) * 0.01;
		if (electricalGain <= 1.0e-6) {
			return 1.0;
		}

		double targetMagnitude = Math.abs(targetBias);
		double actualMagnitude = Math.abs(actualBias);
		if (targetMagnitude <= 1.0e-6 || actualMagnitude <= 1.0e-6) {
			return 1.0;
		}

		double realization = MathUtil.clamp(actualMagnitude / targetMagnitude, 0.0, 1.0);
		double loadGate = smoothStep(0.08, 0.22, loadFraction);
		return MathUtil.clamp(1.0 - electricalGain * realization * loadGate, 0.90, 1.0);
	}

	private double coaxialAllocationElectricalEfficiencyBonus(int index, double baseEfficiency) {
		return coaxialAllocationElectricalEfficiencyBonus(
				baseEfficiency,
				state.rotorCoaxialAllocationElectricalGainPercent(index),
				state.rotorCoaxialLoadBiasTarget(index),
				state.rotorCoaxialLoadBias(index),
				state.rotorCoaxialAllocationLoadFraction(index)
		);
	}

	private static double coaxialAllocationElectricalEfficiencyBonus(
			double baseEfficiency,
			double electricalGainPercent,
			double targetBias,
			double actualBias,
			double loadFraction
	) {
		double powerScale = coaxialAllocationElectricalPowerScale(
				electricalGainPercent,
				targetBias,
				actualBias,
				loadFraction
		);
		if (powerScale >= 1.0 - 1.0e-9) {
			return 0.0;
		}

		double boundedBaseEfficiency = MathUtil.clamp(baseEfficiency, 0.52, 0.86);
		double targetEfficiency = boundedBaseEfficiency / Math.max(0.90, powerScale);
		return MathUtil.clamp(targetEfficiency - boundedBaseEfficiency, 0.0, 0.05);
	}

	private static double coaxialBenchmarkSpacingEfficiencyWindow(double spacingRatio) {
		double activeRange = smoothStep(0.12, 0.24, spacingRatio)
				* (1.0 - smoothStep(1.05, 1.32, spacingRatio));
		if (activeRange <= 1.0e-6) {
			return 0.0;
		}

		// New Dexterity 11-inch data has local mechanical-efficiency peaks near z/D 0.25-0.40 and 0.70-0.85,
		// with a softer valley around z/D 0.55; keep this as an allocation-bias window, not a thrust-loss fit.
		double lowerPeak = coaxialSpacingGaussian(spacingRatio, 0.34, 0.16);
		double upperPeak = coaxialSpacingGaussian(spacingRatio, 0.76, 0.13);
		double midValley = coaxialSpacingGaussian(spacingRatio, 0.55, 0.115);
		double envelope = 0.44
				+ 0.30 * lowerPeak
				+ 0.50 * upperPeak
				- 0.24 * midValley;
		return MathUtil.clamp(activeRange * envelope, 0.0, 1.0);
	}

	private static double coaxialSpacingGaussian(double value, double center, double width) {
		double normalized = (value - center) / Math.max(1.0e-6, width);
		return Math.exp(-normalized * normalized);
	}

	private static Vec3 mixerOutputTorqueFromThrustDeltas(
			List<RotorSpec> rotors,
			Vec3 centerOfMassOffsetBodyMeters,
			double[] baseThrusts,
			double[] finalThrusts
	) {
		if (rotors == null || baseThrusts == null || finalThrusts == null) {
			return Vec3.ZERO;
		}

		Vec3 centerOfMass = centerOfMassOffsetBodyMeters == null ? Vec3.ZERO : centerOfMassOffsetBodyMeters;
		Vec3 torque = Vec3.ZERO;
		int count = Math.min(rotors.size(), Math.min(baseThrusts.length, finalThrusts.length));
		for (int i = 0; i < count; i++) {
			RotorSpec rotor = rotors.get(i);
			Vec3 arm = rotor.positionBodyMeters().subtract(centerOfMass);
			double deltaThrust = finalThrusts[i] - baseThrusts[i];
			if (!Double.isFinite(deltaThrust)) {
				continue;
			}
			torque = torque.add(rotorTorqueCoefficientPerThrust(rotor, arm).multiply(deltaThrust));
		}
		return torque.isFinite() ? torque : Vec3.ZERO;
	}

	private static Vec3 mixerAxisAuthority(Vec3 requestedTorqueBody, Vec3 achievedTorqueBody) {
		Vec3 requested = requestedTorqueBody == null || !requestedTorqueBody.isFinite() ? Vec3.ZERO : requestedTorqueBody;
		Vec3 achieved = achievedTorqueBody == null || !achievedTorqueBody.isFinite() ? Vec3.ZERO : achievedTorqueBody;
		return new Vec3(
				axisAuthority(requested.x(), achieved.x()),
				axisAuthority(requested.y(), achieved.y()),
				axisAuthority(requested.z(), achieved.z())
		);
	}

	private static double axisAuthority(double requested, double achieved) {
		if (!Double.isFinite(requested) || !Double.isFinite(achieved)) {
			return 0.0;
		}
		if (Math.abs(requested) <= 1.0e-6) {
			return 1.0;
		}
		return MathUtil.clamp(achieved / requested, 0.0, 1.0);
	}

	private double averageMaxRotorThrust() {
		return config.totalMaxThrustNewtons() / config.rotors().size();
	}

	private static double motorLoadTargetScale(double rotorAerodynamicLoadFactor, double escOutput) {
		if (escOutput <= 1.0e-6) {
			return 1.0;
		}

		double loadFactor = rotorAerodynamicLoadFactor <= 1.0e-6
				? 1.0
				: MathUtil.clamp(rotorAerodynamicLoadFactor, 0.35, 1.75);
		double authority = 0.35 + 0.65 * MathUtil.clamp(escOutput, 0.0, 1.0);
		double overload = Math.max(0.0, loadFactor - 1.0);
		double unload = Math.max(0.0, 1.0 - loadFactor);
		return MathUtil.clamp(1.0 - 0.18 * overload * authority + 0.08 * unload * authority, 0.78, 1.06);
	}

	private static double motorTrackingError(RotorSpec rotor, double targetOmega, double actualOmega) {
		double maxOmega = Math.max(1.0, rotor.maxOmegaRadiansPerSecond());
		return MathUtil.clamp(Math.abs(targetOmega - actualOmega) / maxOmega, 0.0, 1.5);
	}

	private static double motorActuatorAuthority(
			double trackingError,
			double voltageHeadroom,
			double powerLimitScale,
			double desyncIntensity,
			double surfaceScrapeIntensity
	) {
		double responseAuthority = 1.0 - smoothStep(0.025, 0.280, trackingError);
		double voltageAuthority = MathUtil.clamp(0.55 + 0.45 * voltageHeadroom, 0.0, 1.0);
		double limitAuthority = MathUtil.clamp(powerLimitScale, 0.0, 1.0);
		double desyncAuthority = 1.0 - 0.55 * MathUtil.clamp(desyncIntensity, 0.0, 1.0);
		double scrapeAuthority = 1.0 - 0.30 * MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0);
		return MathUtil.clamp(
				responseAuthority * voltageAuthority * limitAuthority * desyncAuthority * scrapeAuthority,
				0.0,
				1.0
		);
	}

	static double[] allocateTorqueMixDeltas(
			List<RotorSpec> rotors,
			Vec3 centerOfMassOffsetBodyMeters,
			Vec3 requestedTorqueBody
	) {
		int rotorCount = rotors == null ? 0 : rotors.size();
		double[] deltas = new double[rotorCount];
		if (rotorCount == 0 || requestedTorqueBody == null || requestedTorqueBody.lengthSquared() <= 1.0e-12) {
			return deltas;
		}

		Vec3 centerOfMass = centerOfMassOffsetBodyMeters == null ? Vec3.ZERO : centerOfMassOffsetBodyMeters;
		double[][] rows = new double[4][rotorCount];
		for (int i = 0; i < rotorCount; i++) {
			RotorSpec rotor = rotors.get(i);
			Vec3 arm = rotor.positionBodyMeters().subtract(centerOfMass);
			Vec3 torqueCoefficient = rotorTorqueCoefficientPerThrust(rotor, arm);
			rows[0][i] = torqueCoefficient.x();
			rows[1][i] = torqueCoefficient.y();
			rows[2][i] = torqueCoefficient.z();
			rows[3][i] = 1.0;
		}

		double[][] gram = new double[4][4];
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 4; column++) {
				double value = 0.0;
				for (int i = 0; i < rotorCount; i++) {
					value += rows[row][i] * rows[column][i];
				}
				gram[row][column] = value;
			}
		}

		double trace = gram[0][0] + gram[1][1] + gram[2][2] + gram[3][3];
		if (!Double.isFinite(trace) || trace <= 1.0e-12) {
			return deltas;
		}

		double damping = Math.max(1.0e-9, trace * 1.0e-8);
		for (int i = 0; i < 4; i++) {
			gram[i][i] += damping;
		}

		double[] lambda = solveLinearSystem(
				gram,
				new double[] { requestedTorqueBody.x(), requestedTorqueBody.y(), requestedTorqueBody.z(), 0.0 }
		);
		if (lambda == null) {
			return deltas;
		}

		for (int i = 0; i < rotorCount; i++) {
			double delta = 0.0;
			for (int row = 0; row < 4; row++) {
				delta += rows[row][i] * lambda[row];
			}
			deltas[i] = Double.isFinite(delta) ? delta : 0.0;
		}
		return deltas;
	}

	private static double[] solveLinearSystem(double[][] matrix, double[] rhs) {
		int size = rhs.length;
		double[][] augmented = new double[size][size + 1];
		for (int row = 0; row < size; row++) {
			for (int column = 0; column < size; column++) {
				double value = matrix[row][column];
				if (!Double.isFinite(value)) {
					return null;
				}
				augmented[row][column] = value;
			}
			if (!Double.isFinite(rhs[row])) {
				return null;
			}
			augmented[row][size] = rhs[row];
		}

		for (int pivot = 0; pivot < size; pivot++) {
			int pivotRow = pivot;
			double pivotAbs = Math.abs(augmented[pivot][pivot]);
			for (int row = pivot + 1; row < size; row++) {
				double candidateAbs = Math.abs(augmented[row][pivot]);
				if (candidateAbs > pivotAbs) {
					pivotAbs = candidateAbs;
					pivotRow = row;
				}
			}
			if (pivotAbs <= 1.0e-12) {
				return null;
			}
			if (pivotRow != pivot) {
				double[] temp = augmented[pivot];
				augmented[pivot] = augmented[pivotRow];
				augmented[pivotRow] = temp;
			}

			double pivotValue = augmented[pivot][pivot];
			for (int column = pivot; column <= size; column++) {
				augmented[pivot][column] /= pivotValue;
			}
			for (int row = 0; row < size; row++) {
				if (row == pivot) {
					continue;
				}
				double factor = augmented[row][pivot];
				if (Math.abs(factor) <= 1.0e-15) {
					continue;
				}
				for (int column = pivot; column <= size; column++) {
					augmented[row][column] -= factor * augmented[pivot][column];
				}
			}
		}

		double[] solution = new double[size];
		for (int row = 0; row < size; row++) {
			solution[row] = augmented[row][size];
		}
		return solution;
	}

	static Vec3 rotorTorqueCoefficientPerThrust(RotorSpec rotor, Vec3 rotorArmBody) {
		Vec3 axis = rotor.thrustAxisBody();
		return rotorArmBody.cross(axis)
				.add(axis.multiply(rotor.spinDirection() * rotor.yawTorquePerThrustMeter()));
	}

	private void integrateLinear(
			Vec3 totalForceBody,
			Vec3 rotorWashDragBody,
			Vec3 airframeLiftBody,
			Vec3 bodyDrag,
			DroneEnvironment environment,
			Vec3 effectiveWindVelocityWorld,
			double dtSeconds
	) {
		Vec3 gravity = new Vec3(0.0, -config.massKg() * config.gravityMetersPerSecondSquared(), 0.0);
		Vec3 velocity = state.velocityMetersPerSecond();
		Vec3 relativeAirVelocity = velocity.subtract(effectiveWindVelocityWorld);
		Vec3 velocityBody = state.orientation().conjugate().rotate(relativeAirVelocity);
		double effectiveAirDensity = environment.effectiveAirDensityRatio();
		Vec3 groundEffectDragBody = updateGroundEffectDragForce(totalForceBody, velocityBody, environment, dtSeconds);
		state.setGroundEffectDragForceBodyNewtons(groundEffectDragBody);
		Vec3 thrustWorld = state.orientation().rotate(totalForceBody.add(airframeLiftBody).add(groundEffectDragBody).add(rotorWashDragBody));
		Vec3 isotropicDrag = relativeAirVelocity.multiply(-config.linearDragCoefficient() * effectiveAirDensity);
		state.setLinearDampingDragForceWorldNewtons(isotropicDrag);
		Vec3 linearDampingDragBody = velocityBody.multiply(-config.linearDragCoefficient() * effectiveAirDensity);
		updateAirframeDragReferenceTelemetry(velocityBody, bodyDrag, linearDampingDragBody, effectiveAirDensity);
		Vec3 waterDrag = calculateWaterImmersionDragForce(velocity, environment);
		Vec3 drag = state.orientation().rotate(bodyDrag).add(isotropicDrag).add(waterDrag);
		Vec3 acceleration = thrustWorld.add(gravity).add(drag).multiply(1.0 / config.massKg());

		velocity = velocity.add(acceleration.multiply(dtSeconds));
		Vec3 position = state.positionMeters().add(velocity.multiply(dtSeconds));

		state.setLinearAccelerationWorldMetersPerSecondSquared(acceleration);
		state.setVelocityMetersPerSecond(velocity);
		state.setPositionMeters(position);
	}

	private void updateAirframeDragReferenceTelemetry(
			Vec3 relativeAirVelocityBody,
			Vec3 bodyDragBody,
			Vec3 linearDampingDragBody,
			double airDensityRatio
	) {
		double speed = relativeAirVelocityBody == null ? 0.0 : relativeAirVelocityBody.length();
		if (speed <= 1.0e-6) {
			state.setAirframeDragAlongFlowNewtons(0.0);
			state.setAirframeDragEquivalentLinearCoefficient(0.0);
			state.setAirframeDragEquivalentCdAMetersSquared(0.0);
			state.setAirframeDragImavReferenceRatio(0.0);
			return;
		}

		Vec3 dragBody = (bodyDragBody == null ? Vec3.ZERO : bodyDragBody)
				.add(linearDampingDragBody == null ? Vec3.ZERO : linearDampingDragBody);
		double alongFlowNewtons = Math.max(
				0.0,
				dragBody.dot(relativeAirVelocityBody.normalized().multiply(-1.0))
		);
		double equivalentLinearCoefficient = AirframeDragCalibration.equivalentLinearCoefficient(
				alongFlowNewtons,
				speed
		);
		double equivalentCdA = AirframeDragCalibration.equivalentCdAMetersSquared(
				alongFlowNewtons,
				speed,
				airDensityRatio
		);
		double referenceForce = AirframeDragCalibration.imav2022ReferenceDragForceNewtons(
				config,
				speed,
				airDensityRatio
		);
		double imavReferenceRatio = referenceForce > 1.0e-9 ? alongFlowNewtons / referenceForce : 0.0;
		state.setAirframeDragAlongFlowNewtons(alongFlowNewtons);
		state.setAirframeDragEquivalentLinearCoefficient(equivalentLinearCoefficient);
		state.setAirframeDragEquivalentCdAMetersSquared(equivalentCdA);
		state.setAirframeDragImavReferenceRatio(imavReferenceRatio);
	}

	private Vec3 updateAirframeBodyDragForce(Vec3 relativeAirVelocityBody, double airDensityRatio, double dtSeconds) {
		Vec3 target = calculateSteadyAirframeBodyDragForce(relativeAirVelocityBody, airDensityRatio);
		if (dtSeconds <= 0.0) {
			airframeDragForceBodyFiltered = target;
			state.setAirframeBodyDragForceBodyNewtons(airframeDragForceBodyFiltered);
			return airframeDragForceBodyFiltered;
		}

		double targetMagnitude = target.length();
		double previousMagnitude = airframeDragForceBodyFiltered.length();
		double airspeed = relativeAirVelocityBody == null ? 0.0 : relativeAirVelocityBody.length();
		double dynamicPressure = smoothStep(4.0, 22.0, airspeed);
		double buildTimeConstant = MathUtil.clamp(0.045 - 0.014 * dynamicPressure, 0.024, 0.052);
		double releaseTimeConstant = MathUtil.clamp(0.110 + 0.050 * (1.0 - dynamicPressure), 0.080, 0.160);
		double timeConstant = targetMagnitude > previousMagnitude ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		airframeDragForceBodyFiltered = airframeDragForceBodyFiltered
				.add(target.subtract(airframeDragForceBodyFiltered).multiply(alpha))
				.clamp(-48.0, 48.0);
		if (targetMagnitude <= 1.0e-6 && airframeDragForceBodyFiltered.lengthSquared() < 1.0e-8) {
			airframeDragForceBodyFiltered = Vec3.ZERO;
		}
		state.setAirframeBodyDragForceBodyNewtons(airframeDragForceBodyFiltered);
		return airframeDragForceBodyFiltered;
	}

	private Vec3 calculateSteadyAirframeBodyDragForce(Vec3 relativeAirVelocityBody, double airDensityRatio) {
		Vec3 baseDrag = new Vec3(
				-config.bodyDragCoefficients().x() * MathUtil.squareSigned(relativeAirVelocityBody.x()),
				-config.bodyDragCoefficients().y() * MathUtil.squareSigned(relativeAirVelocityBody.y()),
				-config.bodyDragCoefficients().z() * MathUtil.squareSigned(relativeAirVelocityBody.z())
		);
		return baseDrag
				.add(calculateAirframeSeparatedFlowDragForce(relativeAirVelocityBody))
				.multiply(Math.max(0.0, airDensityRatio));
	}

	private Vec3 calculateAirframeSeparatedFlowDragForce(Vec3 relativeAirVelocityBody) {
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double maxBroadsideDrag = Math.max(drag.x(), drag.y());
		if (maxBroadsideDrag <= 1.0e-9 || drag.z() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double separation = effectiveAirframeSeparationIntensity(relativeAirVelocityBody);
		if (separation <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double broadsideCoefficient = 0.20 * maxBroadsideDrag
				+ 0.14 * Math.sqrt(Math.max(0.0, (drag.x() + drag.y()) * drag.z()));
		return relativeAirVelocityBody.normalized()
				.multiply(-speedSquared * broadsideCoefficient * separation)
				.clamp(-38.0, 38.0);
	}

	private void updateAirframeSeparatedFlowIntensity(Vec3 relativeAirVelocityBody, double dtSeconds) {
		if (dtSeconds <= 0.0) {
			return;
		}

		double targetSeparation = airframeSeparationIntensity(relativeAirVelocityBody, config.bodyDragCoefficients());
		double previousSeparation = airframeSeparatedFlowIntensity;
		double airspeed = relativeAirVelocityBody == null ? 0.0 : relativeAirVelocityBody.length();
		double dynamicPressure = smoothStep(4.0, 22.0, airspeed);
		double buildTimeConstant = MathUtil.clamp(0.070 - 0.028 * dynamicPressure, 0.026, 0.080);
		double recoveryTimeConstant = MathUtil.clamp(0.155 + 0.070 * (1.0 - dynamicPressure), 0.090, 0.240);
		double timeConstant = targetSeparation > previousSeparation ? buildTimeConstant : recoveryTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		airframeSeparatedFlowIntensity = MathUtil.clamp(
				previousSeparation + (targetSeparation - previousSeparation) * alpha,
				0.0,
				1.0
		);
		state.setAirframeSeparatedFlowIntensity(airframeSeparatedFlowIntensity);
	}

	private double effectiveAirframeSeparationIntensity(Vec3 relativeAirVelocityBody) {
		double targetSeparation = airframeSeparationIntensity(relativeAirVelocityBody, config.bodyDragCoefficients());
		double immediateSeparation = 0.32 * targetSeparation;
		return MathUtil.clamp(Math.max(airframeSeparatedFlowIntensity, immediateSeparation), 0.0, 1.0);
	}

	private static double airframeSeparationIntensity(Vec3 relativeAirVelocityBody, Vec3 dragCoefficients) {
		if (relativeAirVelocityBody == null
				|| dragCoefficients == null
				|| relativeAirVelocityBody.lengthSquared() <= 1.0e-6
				|| dragCoefficients.z() <= 1.0e-9
				|| Math.max(dragCoefficients.x(), dragCoefficients.y()) <= 1.0e-9) {
			return 0.0;
		}

		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBody.y(), forwardReference);
		double sideSlip = Math.atan2(relativeAirVelocityBody.x(), forwardReference);
		double pitchSeparation = smoothStep(Math.toRadians(30.0), Math.toRadians(66.0), Math.abs(angleOfAttack));
		double yawSeparation = smoothStep(Math.toRadians(32.0), Math.toRadians(68.0), Math.abs(sideSlip));
		return MathUtil.clamp(1.0 - (1.0 - pitchSeparation) * (1.0 - yawSeparation), 0.0, 1.0);
	}

	private Vec3 calculateWaterImmersionDragForce(Vec3 velocityWorld, DroneEnvironment environment) {
		double water = environment.waterImmersionIntensity();
		double speed = velocityWorld.length();
		if (water <= 1.0e-6 || speed <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double frontalAreaScale = Math.sqrt(
				Math.max(1.0e-6, config.bodyDragCoefficients().x() * config.bodyDragCoefficients().z())
		);
		double dragCoefficient = MathUtil.clamp(2.8 + 3.6 * frontalAreaScale, 2.8, 8.5);
		double dragScale = dragCoefficient * Math.pow(water, 1.15) * speed;
		return velocityWorld.multiply(-dragScale).clamp(-120.0, 120.0);
	}

	private Vec3 updateRotorWashDragForce(
			Vec3 totalForceBody,
			Vec3 relativeAirVelocityBody,
			double airDensityRatio,
			double dtSeconds
	) {
		Vec3 target = calculateSteadyRotorWashDragForce(totalForceBody, relativeAirVelocityBody, airDensityRatio);
		if (dtSeconds <= 0.0) {
			rotorWashDragForceBodyFiltered = target;
			return rotorWashDragForceBodyFiltered;
		}

		double targetMagnitude = target.length();
		double previousMagnitude = rotorWashDragForceBodyFiltered.length();
		double timeConstant = targetMagnitude > previousMagnitude ? 0.018 : 0.070;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		rotorWashDragForceBodyFiltered = rotorWashDragForceBodyFiltered
				.add(target.subtract(rotorWashDragForceBodyFiltered).multiply(alpha))
				.clamp(-12.0, 12.0);
		if (targetMagnitude <= 1.0e-6 && rotorWashDragForceBodyFiltered.lengthSquared() < 1.0e-8) {
			rotorWashDragForceBodyFiltered = Vec3.ZERO;
		}
		return rotorWashDragForceBodyFiltered;
	}

	private Vec3 calculateSteadyRotorWashDragForce(Vec3 totalForceBody, Vec3 relativeAirVelocityBody, double airDensityRatio) {
		double airspeed = relativeAirVelocityBody.length();
		if (airspeed <= 1.0e-6 || totalForceBody.y() <= 1.0e-6 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}

		double thrustToWeight = totalForceBody.y() / Math.max(1.0e-6, config.massKg() * config.gravityMetersPerSecondSquared());
		double inducedVelocity = state.averageRotorInducedVelocityMetersPerSecond();
		double washIntensity = smoothStep(0.08, 0.70, thrustToWeight) * smoothStep(0.35, 5.5, inducedVelocity);
		if (washIntensity <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double washDynamicScale = Math.max(0.0, airDensityRatio) * inducedVelocity * washIntensity;
		Vec3 projectedExposure = calculateRotorWashProjectedExposure(relativeAirVelocityBody);
		return new Vec3(
				-relativeAirVelocityBody.x() * washDynamicScale * projectedExposure.x(),
				-relativeAirVelocityBody.y() * washDynamicScale * projectedExposure.y(),
				-relativeAirVelocityBody.z() * washDynamicScale * projectedExposure.z()
		).clamp(-12.0, 12.0);
	}

	private Vec3 calculateRotorWashProjectedExposure(Vec3 relativeAirVelocityBody) {
		Vec3 drag = config.bodyDragCoefficients();
		double airspeed = relativeAirVelocityBody.length();
		if (airspeed <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double baseHorizontalExposure = 0.75 * (drag.x() + drag.z());
		double baseVerticalExposure = 0.55 * drag.y();
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBody.y(), forwardReference);
		double sideslip = Math.atan2(relativeAirVelocityBody.x(), forwardReference);
		double verticalRatio = Math.abs(relativeAirVelocityBody.y()) / airspeed;
		double attitudeProjection = smoothStep(
				Math.toRadians(18.0),
				Math.toRadians(62.0),
				Math.hypot(angleOfAttack, 0.85 * sideslip)
		);
		double verticalProjection = smoothStep(0.16, 0.70, verticalRatio);
		double separatedProjection = effectiveAirframeSeparationIntensity(relativeAirVelocityBody);
		double projectedAreaBoost = 1.0
				+ 0.18 * verticalProjection
				+ 0.34 * attitudeProjection
				+ 0.30 * separatedProjection;
		double verticalShadowBoost = 1.0
				+ 0.12 * attitudeProjection
				+ 0.18 * separatedProjection;
		return new Vec3(
				baseHorizontalExposure * projectedAreaBoost,
				baseVerticalExposure * verticalShadowBoost,
				baseHorizontalExposure * projectedAreaBoost
		);
	}

	private Vec3 updateGroundEffectDragForce(
			Vec3 totalForceBody,
			Vec3 relativeAirVelocityBody,
			DroneEnvironment environment,
			double dtSeconds
	) {
		Vec3 target = calculateSteadyGroundEffectDragForce(totalForceBody, relativeAirVelocityBody, environment);
		if (dtSeconds <= 0.0) {
			groundEffectDragForceBodyFiltered = target;
			return groundEffectDragForceBodyFiltered;
		}

		double targetMagnitude = target.length();
		double previousMagnitude = groundEffectDragForceBodyFiltered.length();
		double proximity = config.groundEffectHeightMeters() <= 1.0e-6
				? 0.0
				: 1.0 - MathUtil.clamp(
						environment.groundClearanceMeters() / Math.max(1.0e-6, config.groundEffectHeightMeters()),
						0.0,
						1.0
				);
		double buildTimeConstant = MathUtil.clamp(0.045 - 0.014 * proximity, 0.026, 0.050);
		double releaseTimeConstant = MathUtil.clamp(0.115 + 0.035 * proximity, 0.085, 0.155);
		double timeConstant = targetMagnitude > previousMagnitude ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		groundEffectDragForceBodyFiltered = groundEffectDragForceBodyFiltered
				.add(target.subtract(groundEffectDragForceBodyFiltered).multiply(alpha))
				.clamp(-14.0, 14.0);
		if (targetMagnitude <= 1.0e-6 && groundEffectDragForceBodyFiltered.lengthSquared() < 1.0e-8) {
			groundEffectDragForceBodyFiltered = Vec3.ZERO;
		}
		return groundEffectDragForceBodyFiltered;
	}

	private Vec3 calculateSteadyGroundEffectDragForce(Vec3 totalForceBody, Vec3 relativeAirVelocityBody, DroneEnvironment environment) {
		if (config.groundEffectHeightMeters() <= 1.0e-6
				|| environment.groundClearanceMeters() >= config.groundEffectHeightMeters()) {
			return Vec3.ZERO;
		}

		double lateralSpeed = Math.hypot(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
		if (lateralSpeed <= 1.0e-6 || totalForceBody.y() <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double proximity = 1.0 - MathUtil.clamp(
				environment.groundClearanceMeters() / Math.max(1.0e-6, config.groundEffectHeightMeters()),
				0.0,
				1.0
		);
		double thrustToWeight = totalForceBody.y() / Math.max(1.0e-6, config.massKg() * config.gravityMetersPerSecondSquared());
		double rotorWash = smoothStep(0.08, 0.70, thrustToWeight);
		if (rotorWash <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double lateralDragCoefficient = MathUtil.clamp(
				2.4 * Math.sqrt(config.bodyDragCoefficients().x() * config.bodyDragCoefficients().z()),
				0.0,
				0.032
		);
		double densityScale = Math.max(0.0, environment.effectiveAirDensityRatio());
		double cushionScale = proximity * proximity * rotorWash * densityScale;
		double dragScale = lateralDragCoefficient * lateralSpeed * cushionScale;
		return new Vec3(
				-relativeAirVelocityBody.x() * dragScale,
				0.0,
				-relativeAirVelocityBody.z() * dragScale
		).clamp(-14.0, 14.0);
	}

	private Vec3 updateGroundEffectLevelingTorque(
			Vec3 totalForceBody,
			Vec3 relativeAirVelocityBody,
			DroneEnvironment environment,
			double dtSeconds
	) {
		Vec3 target = calculateSteadyGroundEffectLevelingTorque(totalForceBody, relativeAirVelocityBody, environment);
		if (dtSeconds <= 0.0) {
			groundEffectLevelingTorqueBodyFiltered = target;
			return groundEffectLevelingTorqueBodyFiltered;
		}

		double targetMagnitude = target.length();
		double previousMagnitude = groundEffectLevelingTorqueBodyFiltered.length();
		double shape = environment.groundEffectLevelingTorqueIntensity(config);
		double buildTimeConstant = MathUtil.clamp(0.050 - 0.018 * shape, 0.030, 0.055);
		double releaseTimeConstant = MathUtil.clamp(0.130 + 0.035 * shape, 0.095, 0.170);
		double timeConstant = targetMagnitude > previousMagnitude ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		groundEffectLevelingTorqueBodyFiltered = groundEffectLevelingTorqueBodyFiltered
				.add(target.subtract(groundEffectLevelingTorqueBodyFiltered).multiply(alpha))
				.clamp(-0.70, 0.70);
		if (targetMagnitude <= 1.0e-7 && groundEffectLevelingTorqueBodyFiltered.lengthSquared() < 1.0e-10) {
			groundEffectLevelingTorqueBodyFiltered = Vec3.ZERO;
		}
		return groundEffectLevelingTorqueBodyFiltered;
	}

	private Vec3 calculateSteadyGroundEffectLevelingTorque(
			Vec3 totalForceBody,
			Vec3 relativeAirVelocityBody,
			DroneEnvironment environment
	) {
		if (config.groundEffectHeightMeters() <= 1.0e-6
				|| environment.groundClearanceMeters() >= config.groundEffectHeightMeters()
				|| totalForceBody.y() <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double shape = environment.groundEffectLevelingTorqueIntensity(config);
		if (shape <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 worldUpBody = state.orientation().conjugate().rotate(WORLD_UP);
		double upright = smoothStep(0.08, 0.55, worldUpBody.y());
		if (upright <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 tiltAxisBody = BODY_ROTOR_AXIS.cross(worldUpBody);
		tiltAxisBody = new Vec3(tiltAxisBody.x(), 0.0, tiltAxisBody.z());
		double tiltMagnitude = tiltAxisBody.length();
		Vec3 angularVelocityBody = state.angularVelocityBodyRadiansPerSecond();
		if (tiltMagnitude <= 1.0e-6
				&& Math.hypot(angularVelocityBody.x(), angularVelocityBody.z()) <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double thrustToWeight = totalForceBody.y()
				/ Math.max(1.0e-6, config.massKg() * config.gravityMetersPerSecondSquared());
		double rotorWash = smoothStep(0.12, 0.72, thrustToWeight);
		if (rotorWash <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double lateralSpeed = Math.hypot(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
		double crossflowFade = 1.0 - 0.45 * smoothStep(5.0, 18.0, lateralSpeed);
		double densityScale = MathUtil.clamp(environment.effectiveAirDensityRatio(), 0.0, 1.35);
		double torqueScale = MathUtil.clamp(
				config.massKg()
						* config.gravityMetersPerSecondSquared()
						* averageRotorRadiusMeters()
						* config.groundEffectMaxThrustBoost()
						* 0.62,
				0.0,
				0.70
		);
		double authority = torqueScale * shape * rotorWash * upright * crossflowFade * densityScale;
		if (authority <= 1.0e-7) {
			return Vec3.ZERO;
		}

		Vec3 attitudeTorque = tiltAxisBody.multiply(authority);
		double dampingCoefficient = authority * 0.18;
		Vec3 dampingTorque = new Vec3(
				-angularVelocityBody.x() * dampingCoefficient,
				0.0,
				-angularVelocityBody.z() * dampingCoefficient
		).clamp(-authority * 0.55, authority * 0.55);
		return attitudeTorque.add(dampingTorque).clamp(-0.70, 0.70);
	}

	private double averageRotorRadiusMeters() {
		double sum = 0.0;
		int count = 0;
		for (RotorSpec rotor : config.rotors()) {
			if (rotor.radiusMeters() > 0.0) {
				sum += rotor.radiusMeters();
				count++;
			}
		}
		return count == 0 ? 0.0635 : sum / count;
	}

	private Vec3 updateAirframeLiftForce(
			Vec3 totalForceBody,
			Vec3 relativeAirVelocityBody,
			double airDensityRatio,
			double dtSeconds
	) {
		Vec3 target = calculateSteadyAirframeLiftForce(totalForceBody, relativeAirVelocityBody, airDensityRatio);
		if (dtSeconds <= 0.0) {
			airframeLiftForceBodyFiltered = target;
			return airframeLiftForceBodyFiltered;
		}

		double targetMagnitude = target.length();
		double previousMagnitude = airframeLiftForceBodyFiltered.length();
		double airspeed = relativeAirVelocityBody == null ? 0.0 : relativeAirVelocityBody.length();
		double dynamicPressure = smoothStep(4.0, 22.0, airspeed);
		double buildTimeConstant = MathUtil.clamp(0.055 - 0.018 * dynamicPressure, 0.026, 0.060);
		double releaseTimeConstant = MathUtil.clamp(0.125 + 0.045 * (1.0 - dynamicPressure), 0.090, 0.170);
		double timeConstant = targetMagnitude > previousMagnitude ? buildTimeConstant : releaseTimeConstant;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		airframeLiftForceBodyFiltered = airframeLiftForceBodyFiltered
				.add(target.subtract(airframeLiftForceBodyFiltered).multiply(alpha))
				.clamp(-18.0, 18.0);
		if (targetMagnitude <= 1.0e-6 && airframeLiftForceBodyFiltered.lengthSquared() < 1.0e-8) {
			airframeLiftForceBodyFiltered = Vec3.ZERO;
		}
		return airframeLiftForceBodyFiltered;
	}

	private Vec3 calculateSteadyAirframeLiftForce(
			Vec3 totalForceBody,
			Vec3 relativeAirVelocityBody,
			double airDensityRatio
	) {
		Vec3 poweredLift = calculatePoweredRotorWashAirframeLiftForce(totalForceBody, airDensityRatio);
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared < 1.0e-6 || airDensityRatio <= 0.0) {
			return poweredLift;
		}

		double separatedFlow = effectiveAirframeSeparationIntensity(relativeAirVelocityBody);
		double pitchPlaneSpeed = Math.hypot(relativeAirVelocityBody.y(), relativeAirVelocityBody.z());
		Vec3 pitchLift = Vec3.ZERO;
		if (pitchPlaneSpeed > 1.0e-6) {
			double aoa = Math.atan2(relativeAirVelocityBody.y(), relativeAirVelocityBody.z());
			double liftCoefficient = 0.085 * Math.sqrt(config.bodyDragCoefficients().y() * config.bodyDragCoefficients().z());
			double pitchStall = smoothStep(Math.toRadians(34.0), Math.toRadians(72.0), Math.abs(aoa));
			double dynamicPitchStall = Math.max(0.32 * pitchStall, separatedFlow * pitchStall);
			double stallScale = 1.0 - 0.55 * dynamicPitchStall;
			double liftMagnitude = liftCoefficient * speedSquared * Math.sin(2.0 * aoa) * stallScale * airDensityRatio;
			Vec3 liftDirection = new Vec3(0.0, relativeAirVelocityBody.z(), -relativeAirVelocityBody.y()).normalized();
			pitchLift = liftDirection.multiply(liftMagnitude);
		}

		double yawPlaneSpeed = Math.hypot(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
		Vec3 sideLift = Vec3.ZERO;
		if (yawPlaneSpeed > 1.0e-6) {
			double sideslip = Math.atan2(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
			double sideforceCoefficient = 0.065 * Math.sqrt(config.bodyDragCoefficients().x() * config.bodyDragCoefficients().z());
			double yawStall = smoothStep(Math.toRadians(35.0), Math.toRadians(75.0), Math.abs(sideslip));
			double dynamicYawStall = Math.max(0.32 * yawStall, separatedFlow * yawStall);
			double stallScale = 1.0 - 0.50 * dynamicYawStall;
			double sideforceMagnitude = sideforceCoefficient * speedSquared * Math.sin(2.0 * sideslip) * stallScale * airDensityRatio;
			Vec3 sideforceDirection = new Vec3(-relativeAirVelocityBody.z(), 0.0, relativeAirVelocityBody.x()).normalized();
			sideLift = sideforceDirection.multiply(sideforceMagnitude);
		}

		return poweredLift.add(pitchLift).add(sideLift).clamp(-18.0, 18.0);
	}

	private Vec3 calculatePoweredRotorWashAirframeLiftForce(Vec3 totalForceBody, double airDensityRatio) {
		if (totalForceBody == null || totalForceBody.y() <= 1.0e-6 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double projectedDeckCoefficient = Math.sqrt(Math.max(0.0, drag.x() * drag.z()));
		if (projectedDeckCoefficient <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double thrustToWeight = totalForceBody.y()
				/ Math.max(1.0e-6, config.massKg() * config.gravityMetersPerSecondSquared());
		double motorPower = state.averageMotorPower(config);
		double inducedVelocity = state.averageRotorInducedVelocityMetersPerSecond();
		double washIntensity = smoothStep(0.08, 0.70, thrustToWeight) * smoothStep(0.35, 5.5, inducedVelocity);
		if (washIntensity <= 1.0e-6 || motorPower <= 0.035) {
			return Vec3.ZERO;
		}

		double frameExposure = MathUtil.clamp(projectedDeckCoefficient / 0.0035, 0.0, 1.65);
		double rpmLift = 0.018 + 0.038 * smoothStep(0.16, 0.82, motorPower);
		double poweredLiftFraction = MathUtil.clamp(
				rpmLift * washIntensity * frameExposure * MathUtil.clamp(airDensityRatio, 0.0, 1.35),
				0.0,
				0.075
		);
		double weight = config.massKg() * config.gravityMetersPerSecondSquared();
		double liftMagnitude = Math.min(totalForceBody.y() * poweredLiftFraction, weight * 0.085);
		return BODY_ROTOR_AXIS.multiply(liftMagnitude).clamp(-18.0, 18.0);
	}

	private void integrateAngular(
			Vec3 torqueBody,
			Vec3 totalRotorForceBody,
			Vec3 relativeAirVelocityBody,
			double airDensityRatio,
			double dtSeconds
	) {
		Vec3 omega = state.angularVelocityBodyRadiansPerSecond();
		Vec3 angularDrag = calculateAirframeAngularDragTorque(
				omega,
				totalRotorForceBody,
				relativeAirVelocityBody,
				airDensityRatio,
				dtSeconds
		);
		state.setAirframeAngularDragTorqueBodyNewtonMeters(angularDrag);
		Vec3 inertia = config.inertiaKgMetersSquared();
		Vec3 gyroscopic = omega.cross(inertia.multiply(omega));
		Vec3 angularAcceleration = torqueBody.add(angularDrag).subtract(gyroscopic).divide(inertia);
		state.setAngularAccelerationBodyRadiansPerSecondSquared(angularAcceleration);

		omega = omega.add(angularAcceleration.multiply(dtSeconds));
		state.setAngularVelocityBodyRadiansPerSecond(omega);
		state.setOrientation(state.orientation().integrateBodyAngularVelocity(omega, dtSeconds));
	}

	private void integrateBattery(DroneEnvironment environment, double dtSeconds) {
		double dischargeCurrentAmps = AVIONICS_CURRENT_AMPS;
		double regenerativeCurrentAmps = 0.0;
		for (int i = 0; i < state.motorCount(); i++) {
			MotorCurrentEstimate estimate = estimateMotorCurrent(i);
			state.setMotorCurrentAmps(i, estimate.thermalCurrentAmps());
			state.setMotorRegenerativeCurrentAmps(i, estimate.regenerativeCurrentAmps());
			state.setMotorPhaseCurrentAmps(i, estimate.phaseCurrentAmps());
			state.setMotorCurrentRippleAmps(i, estimate.currentRippleAmps());
			state.setMotorElectricalEfficiency(i, estimate.electricalEfficiency());
			dischargeCurrentAmps += estimate.dischargeCurrentAmps();
			regenerativeCurrentAmps += estimate.regenerativeCurrentAmps();
		}
		double netBatteryCurrentAmps = dischargeCurrentAmps - regenerativeCurrentAmps;
		state.setBatteryRegenerativeCurrentAmps(regenerativeCurrentAmps);
		state.setBatteryCurrentAmps(netBatteryCurrentAmps);
		state.addBatteryAmpSecondsConsumed(netBatteryCurrentAmps * dtSeconds);
		state.addBatteryEquivalentCycles(batteryEquivalentCycleDelta(dischargeCurrentAmps, regenerativeCurrentAmps, dtSeconds));
		integrateBatteryThermal(dischargeCurrentAmps, regenerativeCurrentAmps, environment, dtSeconds);
		updateBatteryVoltage(netBatteryCurrentAmps, regenerativeCurrentAmps, environment, dtSeconds);
	}

	private double batteryEquivalentCycleDelta(double dischargeCurrentAmps, double regenerativeCurrentAmps, double dtSeconds) {
		if (dtSeconds <= 0.0) {
			return 0.0;
		}

		double capacityAmpSeconds = Math.max(1.0e-9, config.batteryCapacityAmpHours() * 3600.0);
		double agingCurrentAmps = Math.max(0.0, dischargeCurrentAmps)
				+ 0.50 * Math.max(0.0, regenerativeCurrentAmps);
		return agingCurrentAmps * dtSeconds / capacityAmpSeconds;
	}

	private MotorCurrentEstimate estimateMotorCurrent(int index) {
		double escOutput = state.escElectricalOutputCommand(index);
		RotorSpec rotor = config.rotors().get(index);
		double rpmFraction = state.motorPower(config, index);
		double perMotorMaxCurrentAmps = config.maxBatteryCurrentAmps() / state.motorCount();
		double overrunBrakingLoad = config.motorActiveBrakingStrength() * Math.max(0.0, rpmFraction - escOutput);
		double windmillingGeneratorLoad = config.motorActiveBrakingStrength()
				* state.rotorWindmillingIntensity(index)
				* (1.0 - smoothStep(0.22, 0.56, escOutput))
				* smoothStep(0.025, 0.30, rpmFraction);
		double brakingLoad = MathUtil.clamp(overrunBrakingLoad + 0.42 * windmillingGeneratorLoad, 0.0, 1.25);
		double brakingCurrent = perMotorMaxCurrentAmps
				* 0.16
				* brakingLoad
				* (0.30 + 0.70 * rpmFraction);
		double regenerativeCurrent = brakingCurrent * regenerativeBrakingFraction(rpmFraction, escOutput, brakingLoad);
		double thrustFraction = MathUtil.clamp(state.rotorThrustNewtons(index) / rotor.maxThrustNewtons(), 0.0, 1.25);
		double aerodynamicLoadFactor = state.rotorAerodynamicLoadFactor(index) <= 1.0e-6
				? 1.0
				: state.rotorAerodynamicLoadFactor(index);
		double propellerPowerLoadFactor = motorPropellerPowerLoadFactor(index, aerodynamicLoadFactor);
		double electricalEfficiency = motorElectricalEfficiency(index, rpmFraction, propellerPowerLoadFactor);
		double coaxialElectricalPowerScale = coaxialAllocationElectricalPowerScale(index);
		if (escOutput <= 1.0e-6) {
			double brakingRippleCurrent = brakingCurrent * 0.08 * smoothStep(0.02, 0.40, brakingLoad);
			return new MotorCurrentEstimate(
					0.0,
					regenerativeCurrent,
					brakingCurrent + 0.20 * brakingRippleCurrent,
					brakingCurrent,
					brakingRippleCurrent,
					electricalEfficiency
			);
		}

		double escCopperLoss = escOutput * escOutput;
		double aerodynamicPower = rpmFraction * rpmFraction * rpmFraction * propellerPowerLoadFactor;
		double loadedPropPower = Math.sqrt(thrustFraction) * rpmFraction * propellerPowerLoadFactor;
		double normalizedLoad = 0.12 * escCopperLoss + 0.34 * aerodynamicPower + 0.54 * loadedPropPower;
		double shaftPowerCurrent = motorShaftPowerCurrentAmps(index, perMotorMaxCurrentAmps, electricalEfficiency);
		double driveVoltage = motorDriveVoltage(
				escOutput,
				Math.sqrt(state.batteryPowerLimit() * state.motorThermalLimit() * state.escThermalLimit(index) * state.rotorHealth(index))
		);
		double backEmfVoltage = motorBackEmfVoltage(rotor, state.motorOmegaRadiansPerSecond(index));
		double windingVoltageDrop = Math.max(0.0, driveVoltage - backEmfVoltage);
		double windingCurrent = windingVoltageDrop / temperatureAdjustedMotorWindingResistanceOhms(index);
		double phaseCurrent = Math.max(0.0, windingCurrent);
		double busVoltage = Math.max(1.0e-6, state.batteryVoltage());
		double busEquivalentWindingCurrent = windingCurrent * MathUtil.clamp(windingVoltageDrop / busVoltage, 0.0, 1.0);
		double noLoadCurrent = perMotorMaxCurrentAmps
				* (0.018 + 0.045 * rpmFraction)
				* MathUtil.clamp(0.35 + 0.65 * escOutput, 0.0, 1.0);
		double electricalModelCurrent = noLoadCurrent
				+ busEquivalentWindingCurrent
						* MathUtil.clamp(0.35 + 0.65 * propellerPowerLoadFactor, 0.25, 1.60)
						* coaxialElectricalPowerScale;
		double desyncCurrent = perMotorMaxCurrentAmps
				* 0.22
				* state.escDesyncIntensity(index)
				* escOutput
				* (0.45 + 0.55 * rpmFraction);
		double voltageLimitLossCurrent = perMotorMaxCurrentAmps
				* (0.012 + 0.030 * MathUtil.clamp(rpmFraction, 0.0, 1.10))
				* motorVoltageHeadroomStress(index)
				* MathUtil.clamp(escOutput, 0.0, 1.0)
				* MathUtil.clamp(0.75 + 0.25 * propellerPowerLoadFactor, 0.75, 1.25);
		double currentRipple = phaseCurrent
				* state.motorCommutationRippleIntensity(index)
				* (0.22 + 0.78 * escOutput)
				+ phaseCurrent
						* rotorEffectiveImbalanceIntensity(rotor, state.rotorHealth(index))
						* (0.05 + 0.45 * rpmFraction)
				+ perMotorMaxCurrentAmps
						* 0.06
						* state.escDesyncIntensity(index)
						* smoothStep(0.12, 0.82, escOutput);
		double curveCurrent = perMotorMaxCurrentAmps * MathUtil.clamp(normalizedLoad, 0.0, 1.20)
				* coaxialElectricalPowerScale;
		double steadyPropulsionCurrent = Math.max(Math.max(curveCurrent, shaftPowerCurrent), electricalModelCurrent);
		double anchoredSteadyPropulsionCurrent = motorBenchAnchoredSteadyMotorCurrentAmps(
				index,
				rotor,
				steadyPropulsionCurrent,
				thrustFraction,
				aerodynamicLoadFactor,
				propellerPowerLoadFactor,
				brakingLoad
		);
		double propulsionCurrent = anchoredSteadyPropulsionCurrent
				+ desyncCurrent
				+ voltageLimitLossCurrent
				+ 0.10 * currentRipple;
		double thermalCurrent = propulsionCurrent + brakingCurrent + 0.35 * currentRipple;
		return new MotorCurrentEstimate(
				propulsionCurrent,
				regenerativeCurrent,
				thermalCurrent,
				phaseCurrent,
				currentRipple,
				electricalEfficiency
		);
	}

	private double motorPropellerPowerLoadFactor(int index, double aerodynamicLoadFactor) {
		return motorPropellerLoadFactor(index, aerodynamicLoadFactor, 0.45, 0.20);
	}

	private double motorPropellerDynamicLoadFactor(int index, double aerodynamicLoadFactor) {
		return motorPropellerLoadFactor(index, aerodynamicLoadFactor, 0.28, 0.25);
	}

	private double motorPropellerLoadFactor(
			int index,
			double aerodynamicLoadFactor,
			double propellerPowerWeight,
			double minimumUnload
	) {
		double loadFactor = aerodynamicLoadFactor <= 1.0e-6
				? 1.0
				: MathUtil.clamp(aerodynamicLoadFactor, 0.35, 2.0);
		double propellerPowerScale = state.rotorPropellerPowerScale(index) <= 1.0e-6
				? 1.0
				: MathUtil.clamp(state.rotorPropellerPowerScale(index), 0.16, 1.08);
		double weight = MathUtil.clamp(propellerPowerWeight, 0.0, 1.0);
		double blendedLoad = (1.0 - weight) * loadFactor + weight * propellerPowerScale;
		if (propellerPowerScale < 1.0) {
			return MathUtil.clamp(Math.min(loadFactor, blendedLoad), minimumUnload, 2.0);
		}
		return MathUtil.clamp(Math.max(loadFactor, blendedLoad), 0.35, 2.0);
	}

	private double regenerativeBrakingFraction(double rpmFraction, double escOutput, double brakingLoad) {
		if (brakingLoad <= 1.0e-6 || config.motorActiveBrakingStrength() <= 1.0e-6) {
			return 0.0;
		}

		double overrun = Math.max(0.0, rpmFraction - escOutput);
		double generatorDrive = Math.max(overrun, brakingLoad * 0.34);
		double overrunFactor = smoothStep(0.025, 0.42, generatorDrive);
		double rpmFactor = smoothStep(0.08, 0.75, rpmFraction);
		double brakeAuthority = MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0);
		return MathUtil.clamp((0.30 + 0.38 * brakeAuthority + 0.12 * rpmFactor) * overrunFactor, 0.0, 0.82);
	}

	private double motorShaftPowerCurrentAmps(
			int index,
			double perMotorMaxCurrentAmps,
			double efficiency
	) {
		double mechanicalPowerWatts = state.motorShaftPowerWatts(index);
		if (mechanicalPowerWatts <= 1.0e-6) {
			return 0.0;
		}

		double electricalCurrent = mechanicalPowerWatts / Math.max(1.0, state.batteryVoltage() * efficiency);
		return MathUtil.clamp(electricalCurrent, 0.0, perMotorMaxCurrentAmps * 1.30);
	}

	private double motorBenchAnchoredSteadyMotorCurrentAmps(
			int index,
			RotorSpec rotor,
			double steadyCurrentAmps,
			double thrustFraction,
			double aerodynamicLoadFactor,
			double propellerPowerLoadFactor,
			double brakingLoad
	) {
		double apDroneProfileWeight = MotorBenchCurrentModel.apDronePdf5045RotorSimilarity(rotor);
		if (apDroneProfileWeight > 1.0e-6) {
			double apDroneBenchCurrentAmps = MotorBenchCurrentModel.apDronePdf5045CurrentAmpsForThrustNewtons(
					state.rotorThrustNewtons(index)
			);
			return motorBenchAnchoredSteadyMotorCurrentAmps(
					index,
					steadyCurrentAmps,
					apDroneBenchCurrentAmps,
					apDroneProfileWeight,
					thrustFraction,
					aerodynamicLoadFactor,
					propellerPowerLoadFactor,
					brakingLoad,
					0.72
			);
		}

		double profileWeight = MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(rotor);
		double benchCurrentAmps = MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(
				state.rotorThrustNewtons(index)
		);
		return motorBenchAnchoredSteadyMotorCurrentAmps(
				index,
				steadyCurrentAmps,
				benchCurrentAmps,
				profileWeight,
				thrustFraction,
				aerodynamicLoadFactor,
				propellerPowerLoadFactor,
				brakingLoad,
				0.62
		);
	}

	private double motorBenchAnchoredSteadyMotorCurrentAmps(
			int index,
			double steadyCurrentAmps,
			double benchCurrentAmps,
			double profileWeight,
			double thrustFraction,
			double aerodynamicLoadFactor,
			double propellerPowerLoadFactor,
			double brakingLoad,
			double maxAnchorBlend
	) {
		if (profileWeight <= 1.0e-6
				|| steadyCurrentAmps <= 1.0e-6
				|| benchCurrentAmps <= 1.0e-6
				|| steadyCurrentAmps <= benchCurrentAmps) {
			return steadyCurrentAmps;
		}

		double activeRotor = smoothStep(0.06, 0.32, thrustFraction);
		double midLoadConfidence = 1.0 - smoothStep(0.58, 0.82, thrustFraction);
		double outputConfidence = 1.0 - smoothStep(0.64, 0.72, state.escElectricalOutputCommand(index));
		double cleanLoadConfidence = 1.0 - smoothStep(0.035, 0.16, Math.abs(aerodynamicLoadFactor - 1.0));
		double powerCurveConfidence = 1.0 - smoothStep(0.12, 0.42, Math.abs(propellerPowerLoadFactor - 1.0));
		double rotorHealthConfidence = smoothStep(0.96, 0.995, minimumRotorHealth());
		double transientPenalty = MathUtil.clamp(
				0.80 * state.motorTrackingError(index)
						+ 0.75 * state.escDesyncIntensity(index)
						+ 0.45 * state.rotorWindmillingIntensity(index)
						+ 0.55 * brakingLoad
						+ 0.35 * motorVoltageHeadroomStress(index),
				0.0,
				1.0
		);
		double anchorBlend = maxAnchorBlend
				* profileWeight
				* activeRotor
				* midLoadConfidence
				* outputConfidence
				* cleanLoadConfidence
				* powerCurveConfidence
				* rotorHealthConfidence
				* (1.0 - transientPenalty);
		return MathUtil.lerp(
				steadyCurrentAmps,
				benchCurrentAmps,
				MathUtil.clamp(anchorBlend, 0.0, MathUtil.clamp(maxAnchorBlend, 0.0, 1.0))
		);
	}

	private double minimumRotorHealth() {
		double minimum = 1.0;
		for (double health : state.rotorHealth()) {
			minimum = Math.min(minimum, health);
		}
		return minimum;
	}

	private double motorElectricalEfficiency(int index, double rpmFraction, double aerodynamicLoadFactor) {
		double escOutput = state.escElectricalOutputCommand(index);
		double escAuthority = MathUtil.clamp(0.35 + 0.65 * escOutput, 0.0, 1.0);
		double hotWindingLoss = smoothStep(1.05, 1.62, motorWindingResistanceTemperatureScale(index));
		double voltageHeadroomStress = motorVoltageHeadroomStress(index);
		double loadedVoltageStress = voltageHeadroomStress * MathUtil.clamp(0.40 + 0.60 * escOutput, 0.0, 1.0);
		double baseEfficiency = MathUtil.clamp(
				0.58
						+ 0.22 * escAuthority
						- 0.07 * Math.pow(1.0 - MathUtil.clamp(rpmFraction, 0.0, 1.15), 2.0)
						- 0.05 * MathUtil.clamp(aerodynamicLoadFactor - 1.0, 0.0, 0.75)
						- 0.060 * loadedVoltageStress
						- 0.055 * hotWindingLoss
						- 0.05 * (1.0 - state.escThermalLimit(index))
						- 0.06 * state.escDesyncIntensity(index)
						- 0.026 * state.motorCommutationRippleIntensity(index),
				0.52,
				0.86
		);
		return MathUtil.clamp(
				baseEfficiency + coaxialAllocationElectricalEfficiencyBonus(index, baseEfficiency),
				0.52,
				0.90
		);
	}

	private double motorVoltageHeadroomStress(int index) {
		return 1.0 - smoothStep(0.10, 0.42, state.motorVoltageHeadroom(index));
	}

	private static double motorPositiveInertiaPowerWatts(RotorSpec rotor, double motorAngularAcceleration, double omega) {
		if (rotor.rotorInertiaKgMetersSquared() <= 0.0 || motorAngularAcceleration <= 0.0 || omega <= 0.0) {
			return 0.0;
		}
		return rotor.rotorInertiaKgMetersSquared() * motorAngularAcceleration * omega;
	}

	private void updateSensorBias(double dtSeconds) {
		sensorBiasTimeSeconds += dtSeconds;
		double averageTemperature = state.averageMotorTemperatureCelsius();
		double heatCelsius = Math.max(0.0, averageTemperature - MOTOR_AMBIENT_TEMPERATURE_CELSIUS);
		double heatFactor = MathUtil.clamp(heatCelsius / 75.0, 0.0, 1.35);
		double motorFactor = state.averageMotorPower(config);
		double vibrationFactor = MathUtil.clamp(state.rotorVibration() / 0.18, 0.0, 1.6);
		double activity = MathUtil.clamp(0.55 * motorFactor + 0.45 * heatFactor + 0.35 * vibrationFactor, 0.0, 1.8);
		double t = sensorBiasTimeSeconds;

		double gyroSensorScale = config.gyroNoiseStdDevRadiansPerSecond() <= 0.0
				? 0.0
				: MathUtil.clamp(config.gyroNoiseStdDevRadiansPerSecond() / 0.025, 0.25, 2.5);
		Vec3 gyroTarget = Vec3.ZERO;
		if (gyroSensorScale > 0.0 && activity > 1.0e-6) {
			double thermalDps = heatCelsius * 0.010;
			double vibrationDps = 0.30 * smoothStep(0.02, 0.18, state.rotorVibration());
			double wanderDps = (0.018 + 0.050 * activity)
					* (0.45 + 0.55 * MathUtil.clamp(config.gyroNoiseStdDevRadiansPerSecond() / 0.08, 0.0, 1.0));
			gyroTarget = new Vec3(
					Math.toRadians((0.85 * thermalDps + vibrationDps) * gyroSensorScale
							+ wanderDps * Math.sin(t * 0.37 + 0.3)),
					Math.toRadians((-0.32 * thermalDps + 0.45 * vibrationDps) * gyroSensorScale
							+ wanderDps * Math.sin(t * 0.29 + 2.0)),
					Math.toRadians((0.58 * thermalDps - 0.25 * vibrationDps) * gyroSensorScale
							+ wanderDps * Math.sin(t * 0.41 + 4.1))
			);
		}

		double accelSensorScale = config.accelerometerNoiseStdDevMetersPerSecondSquared() <= 0.0
				? 0.0
				: MathUtil.clamp(config.accelerometerNoiseStdDevMetersPerSecondSquared() / 0.22, 0.25, 2.5);
		Vec3 accelTarget = Vec3.ZERO;
		if (accelSensorScale > 0.0 && activity > 1.0e-6) {
			double thermalBias = heatCelsius * 0.0028;
			double vibrationBias = 0.18 * smoothStep(0.02, 0.18, state.rotorVibration());
			double wanderBias = (0.010 + 0.030 * activity)
					* (0.45 + 0.55 * MathUtil.clamp(config.accelerometerNoiseStdDevMetersPerSecondSquared() / 0.8, 0.0, 1.0));
			accelTarget = new Vec3(
					(0.75 * thermalBias + vibrationBias) * accelSensorScale
							+ wanderBias * Math.sin(t * 0.23 + 0.8),
					(-0.18 * thermalBias + 0.35 * vibrationBias) * accelSensorScale
							+ wanderBias * Math.sin(t * 0.19 + 1.7),
					(0.52 * thermalBias - 0.20 * vibrationBias) * accelSensorScale
							+ wanderBias * Math.sin(t * 0.31 + 3.4)
			);
		}

		double gyroAlpha = MathUtil.expSmoothing(dtSeconds, 3.5);
		double accelAlpha = MathUtil.expSmoothing(dtSeconds, 5.0);
		gyroBiasBodyRadiansPerSecond = gyroBiasBodyRadiansPerSecond.add(
				gyroTarget.subtract(gyroBiasBodyRadiansPerSecond).multiply(gyroAlpha)
		);
		accelerometerBiasBodyMetersPerSecondSquared = accelerometerBiasBodyMetersPerSecondSquared.add(
				accelTarget.subtract(accelerometerBiasBodyMetersPerSecondSquared).multiply(accelAlpha)
		);
		state.setGyroBiasBodyRadiansPerSecond(gyroBiasBodyRadiansPerSecond);
		state.setAccelerometerBiasBodyMetersPerSecondSquared(accelerometerBiasBodyMetersPerSecondSquared);
	}

	private void updateGyroMeasurement(double dtSeconds) {
		gyroNoiseTimeSeconds += dtSeconds;
		Vec3 rawRate = state.angularVelocityBodyRadiansPerSecond()
				.add(state.gyroBiasBodyRadiansPerSecond())
				.add(gyroSpecificForceErrorBodyRadiansPerSecond())
				.add(gyroNoiseBodyRadiansPerSecond(dtSeconds));
		state.setGyroClipIntensity(sensorClipIntensity(rawRate, GYRO_FULL_SCALE_RADIANS_PER_SECOND));
		Vec3 noisyRate = clipSensorVector(rawRate, GYRO_FULL_SCALE_RADIANS_PER_SECOND);
		double cutoffHz = config.gyroLowPassCutoffHz();
		double alpha = cutoffHz <= 0.0
				? 1.0
				: MathUtil.expSmoothing(dtSeconds, 1.0 / (Math.PI * 2.0 * cutoffHz));
		gyroFilteredBodyRadiansPerSecond = gyroFilteredBodyRadiansPerSecond.add(
				noisyRate.subtract(gyroFilteredBodyRadiansPerSecond).multiply(alpha)
		);

		gyroDelayBuffer[gyroDelayWriteIndex] = gyroFilteredBodyRadiansPerSecond;
		int delaySamples = Math.min(
				GYRO_DELAY_BUFFER_SIZE - 1,
				(int) Math.round(config.controlLatencySeconds() / Math.max(dtSeconds, 1.0e-6))
		);
		int readIndex = gyroDelayWriteIndex - delaySamples;
		if (readIndex < 0) {
			readIndex += GYRO_DELAY_BUFFER_SIZE;
		}
		state.setGyroAngularVelocityBodyRadiansPerSecond(gyroDelayBuffer[readIndex]);
		gyroDelayWriteIndex = (gyroDelayWriteIndex + 1) % GYRO_DELAY_BUFFER_SIZE;
	}

	private Vec3 gyroSpecificForceErrorBodyRadiansPerSecond() {
		double noise = config.gyroNoiseStdDevRadiansPerSecond();
		if (noise <= 0.0) {
			return Vec3.ZERO;
		}

		Vec3 specificForce = specificForceBodyMetersPerSecondSquared();
		double gravity = Math.max(1.0e-6, config.gravityMetersPerSecondSquared());
		Vec3 dynamicG = specificForce.subtract(new Vec3(0.0, gravity, 0.0)).multiply(1.0 / gravity);
		double dynamicMagnitude = dynamicG.length();
		if (dynamicMagnitude <= 0.35) {
			return Vec3.ZERO;
		}

		double sensorScale = MathUtil.clamp(noise / 0.025, 0.20, 2.5);
		double exposure = smoothStep(0.35, 3.0, dynamicMagnitude);
		double scale = Math.toRadians(0.18) * sensorScale * exposure;
		return new Vec3(
				scale * (0.42 * dynamicG.y() - 0.18 * dynamicG.z()),
				scale * (-0.34 * dynamicG.x() + 0.24 * dynamicG.y()),
				scale * (0.36 * dynamicG.x() + 0.22 * dynamicG.y() - 0.14 * dynamicG.z())
		).clamp(-Math.toRadians(4.0), Math.toRadians(4.0));
	}

	private Vec3 gyroNoiseBodyRadiansPerSecond(double dtSeconds) {
		double noise = config.gyroNoiseStdDevRadiansPerSecond();
		double vibration = state.rotorVibration();
		double busRipple = state.batteryBusRippleVoltage();
		double supplyNoise = state.imuSupplyNoiseIntensity();
		if (noise <= 0.0 && vibration <= 0.0 && busRipple <= 0.0 && supplyNoise <= 0.0) {
			state.setGyroDynamicNotchFrequencyHertz(0.0);
			state.setGyroDynamicNotchAttenuation(0.0);
			state.setGyroDynamicNotchSpreadHertz(0.0);
			state.setGyroRpmHarmonicNotchAttenuation(0.0);
			state.setGyroBladePassNotchFrequencyHertz(0.0);
			state.setGyroBladePassNotchAttenuation(0.0);
			state.setGyroBladePassNotchSpreadHertz(0.0);
			return Vec3.ZERO;
		}

		double motorVibration = 0.25 + 0.75 * state.averageMotorPower(config);
		double propwashVibration = 1.0 + 0.7 * state.propwashIntensity();
		double noiseScale = noise * motorVibration * propwashVibration / 1.35;
		double averageOmega = averageEscRpmTelemetryOmegaRadiansPerSecond();
		double notchFrequencyHertz = averageOmega / (Math.PI * 2.0);
		double notchAttenuation = gyroDynamicNotchAttenuation(notchFrequencyHertz, vibration);
		double harmonicNotchAttenuation = gyroRpmHarmonicNotchAttenuation(vibration);
		double averageBladePassOmega = averageEscRpmTelemetryBladePassOmegaRadiansPerSecond();
		double bladePassNotchFrequencyHertz = averageBladePassOmega / (Math.PI * 2.0);
		double bladePassNotchAttenuation = gyroDynamicNotchAttenuation(bladePassNotchFrequencyHertz, vibration);
		state.setGyroDynamicNotchFrequencyHertz(notchFrequencyHertz);
		state.setGyroDynamicNotchAttenuation(notchAttenuation);
		state.setGyroDynamicNotchSpreadHertz(escRpmTelemetryFrequencySpreadHertz(false));
		state.setGyroRpmHarmonicNotchAttenuation(harmonicNotchAttenuation);
		state.setGyroBladePassNotchFrequencyHertz(bladePassNotchFrequencyHertz);
		state.setGyroBladePassNotchAttenuation(bladePassNotchAttenuation);
		state.setGyroBladePassNotchSpreadHertz(escRpmTelemetryFrequencySpreadHertz(true));
		double t = gyroNoiseTimeSeconds;
		Vec3 broadbandNoise = new Vec3(
				noiseScale * (Math.sin(t * 437.0 + 0.2) + 0.35 * Math.sin(t * 941.0 + 1.7)),
				noiseScale * (Math.sin(t * 389.0 + 2.1) + 0.30 * Math.sin(t * 811.0 + 0.4)),
				noiseScale * (Math.sin(t * 463.0 + 1.1) + 0.32 * Math.sin(t * 877.0 + 2.8))
		);
		double railNoiseScale = Math.toRadians(0.42 * busRipple + 0.11 * supplyNoise) * (0.35 + 0.65 * motorVibration);
		Vec3 powerRailNoise = new Vec3(
				railNoiseScale * (Math.sin(t * 617.0 + 0.9) + 0.28 * Math.sin(t * 1321.0 + 2.4)),
				railNoiseScale * (Math.sin(t * 557.0 + 1.8) + 0.24 * Math.sin(t * 1187.0 + 0.6)),
				railNoiseScale * (Math.sin(t * 683.0 + 2.9) + 0.26 * Math.sin(t * 1261.0 + 1.4))
		);
		double rotorVibrationScale = 0.45 * vibration * (1.0 - notchAttenuation);
		double harmonicVibrationScale = 0.12 * vibration * (1.0 - harmonicNotchAttenuation);
		double bladePassVibrationScale = 0.45 * vibration * (1.0 - bladePassNotchAttenuation);
		Vec3 motorSynchronousNoise = perMotorSynchronousGyroNoise(
				dtSeconds,
				rotorVibrationScale,
				harmonicVibrationScale,
				bladePassVibrationScale
		);
		return broadbandNoise.add(powerRailNoise).add(motorSynchronousNoise);
	}

	private Vec3 perMotorSynchronousGyroNoise(
			double dtSeconds,
			double rotorVibrationScale,
			double harmonicVibrationScale,
			double bladePassVibrationScale
	) {
		int count = Math.min(state.motorCount(), config.rotors().size());
		if (count <= 0 || (rotorVibrationScale <= 0.0 && harmonicVibrationScale <= 0.0 && bladePassVibrationScale <= 0.0)) {
			return Vec3.ZERO;
		}

		double mixScale = 1.0 / Math.sqrt(count);
		Vec3 sum = Vec3.ZERO;
		for (int i = 0; i < count; i++) {
			double motorOmega = Math.abs(escRpmTelemetryOmegaRadiansPerSecond[i]);
			double bladePassOmega = motorOmega * config.rotors().get(i).bladeCount();
			gyroMotorVibrationPhases[i] += motorOmega * dtSeconds;
			gyroBladePassVibrationPhases[i] += bladePassOmega * dtSeconds;
			double motorPhase = gyroMotorVibrationPhases[i] + i * 0.73;
			double bladePhase = gyroBladePassVibrationPhases[i] + i * 1.11;
			double secondHarmonicPhase = 2.0 * motorPhase + i * 0.31;
			double thirdHarmonicPhase = 3.0 * motorPhase + i * 0.47;
			double armSignX = Math.signum(config.rotors().get(i).positionBodyMeters().x());
			double armSignZ = Math.signum(config.rotors().get(i).positionBodyMeters().z());
			Vec3 line = new Vec3(
					rotorVibrationScale * Math.sin(motorPhase + 0.3 + 0.18 * armSignZ)
							+ 0.34 * harmonicVibrationScale * Math.sin(secondHarmonicPhase + 0.6 - 0.11 * armSignX)
							+ 0.24 * harmonicVibrationScale * Math.sin(thirdHarmonicPhase + 1.4 + 0.09 * armSignZ)
							+ 0.42 * bladePassVibrationScale * Math.sin(bladePhase + 1.2 + 0.14 * armSignX),
					rotorVibrationScale * Math.sin(motorPhase + 2.0 + 0.16 * armSignX)
							+ 0.29 * harmonicVibrationScale * Math.sin(secondHarmonicPhase + 2.2 + 0.08 * armSignZ)
							+ 0.21 * harmonicVibrationScale * Math.sin(thirdHarmonicPhase + 0.5 - 0.13 * armSignX)
							+ 0.35 * bladePassVibrationScale * Math.sin(bladePhase + 0.4 + 0.12 * armSignZ),
					rotorVibrationScale * Math.sin(motorPhase + 4.1 - 0.15 * armSignX)
							+ 0.31 * harmonicVibrationScale * Math.sin(secondHarmonicPhase + 3.1 + 0.10 * armSignX)
							+ 0.23 * harmonicVibrationScale * Math.sin(thirdHarmonicPhase + 2.8 - 0.07 * armSignZ)
							+ 0.38 * bladePassVibrationScale * Math.sin(bladePhase + 2.6 - 0.10 * armSignZ)
			);
			sum = sum.add(line.multiply(mixScale));
		}
		return sum;
	}

	private double averageEscRpmTelemetryOmegaRadiansPerSecond() {
		double sum = 0.0;
		for (int i = 0; i < state.motorCount(); i++) {
			sum += Math.abs(escRpmTelemetryOmegaRadiansPerSecond[i]);
		}
		return sum / state.motorCount();
	}

	private double averageEscRpmTelemetryBladePassOmegaRadiansPerSecond() {
		double sum = 0.0;
		int count = Math.min(state.motorCount(), config.rotors().size());
		for (int i = 0; i < count; i++) {
			sum += Math.abs(escRpmTelemetryOmegaRadiansPerSecond[i]) * config.rotors().get(i).bladeCount();
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private double escRpmTelemetryFrequencySpreadHertz(boolean bladePass) {
		int count = Math.min(state.motorCount(), config.rotors().size());
		if (count <= 1) {
			return 0.0;
		}
		double min = Double.POSITIVE_INFINITY;
		double max = 0.0;
		for (int i = 0; i < count; i++) {
			double omega = Math.abs(escRpmTelemetryOmegaRadiansPerSecond[i]);
			if (bladePass) {
				omega *= config.rotors().get(i).bladeCount();
			}
			double hertz = omega / (Math.PI * 2.0);
			min = Math.min(min, hertz);
			max = Math.max(max, hertz);
		}
		return Double.isFinite(min) ? Math.max(0.0, max - min) : 0.0;
	}

	private static double gyroDynamicNotchAttenuation(double frequencyHertz, double rotorVibration) {
		double frequencyActive = smoothStep(25.0, 75.0, frequencyHertz);
		double vibrationActive = smoothStep(0.005, 0.09, rotorVibration);
		return 0.72 * frequencyActive * vibrationActive;
	}

	private double gyroRpmHarmonicNotchAttenuation(double rotorVibration) {
		int count = Math.min(state.motorCount(), config.rotors().size());
		if (count <= 0 || rotorVibration <= 0.0) {
			return 0.0;
		}
		double combined = 0.0;
		for (int i = 0; i < count; i++) {
			double validity = state.motorRpmTelemetryValidity(i);
			if (validity <= 0.0) {
				continue;
			}
			double fundamentalHertz = Math.abs(escRpmTelemetryOmegaRadiansPerSecond[i]) / (Math.PI * 2.0);
			if (fundamentalHertz <= 0.0) {
				continue;
			}
			for (int harmonic = 2; harmonic <= 3; harmonic++) {
				double harmonicWeight = harmonic == 2 ? 0.20 : 0.14;
				double attenuation = gyroDynamicNotchAttenuation(fundamentalHertz * harmonic, rotorVibration)
						* validity
						* harmonicWeight;
				combined = combineIndependentNotchAttenuation(combined, attenuation);
			}
		}
		return MathUtil.clamp(combined, 0.0, 0.58);
	}

	private static double combineIndependentNotchAttenuation(double first, double second) {
		double a = MathUtil.clamp(first, 0.0, 1.0);
		double b = MathUtil.clamp(second, 0.0, 1.0);
		return 1.0 - (1.0 - a) * (1.0 - b);
	}

	private void resetGyroModel() {
		gyroFilteredBodyRadiansPerSecond = state.angularVelocityBodyRadiansPerSecond();
		Arrays.fill(gyroDelayBuffer, gyroFilteredBodyRadiansPerSecond);
		state.setGyroAngularVelocityBodyRadiansPerSecond(gyroFilteredBodyRadiansPerSecond);
		state.setGyroClipIntensity(0.0);
		state.setGyroDynamicNotchFrequencyHertz(0.0);
		state.setGyroDynamicNotchAttenuation(0.0);
		state.setGyroDynamicNotchSpreadHertz(0.0);
		state.setGyroRpmHarmonicNotchAttenuation(0.0);
		state.setGyroBladePassNotchFrequencyHertz(0.0);
		state.setGyroBladePassNotchAttenuation(0.0);
		state.setGyroBladePassNotchSpreadHertz(0.0);
		state.setImuSupplyNoiseIntensity(0.0);
		gyroDelayWriteIndex = 0;
		gyroNoiseTimeSeconds = 0.0;
		Arrays.fill(gyroMotorVibrationPhases, 0.0);
		Arrays.fill(gyroBladePassVibrationPhases, 0.0);
	}

	private void updateAccelerometerMeasurement(double dtSeconds) {
		accelerometerNoiseTimeSeconds += dtSeconds;
		Vec3 trueSpecificForce = specificForceBodyMetersPerSecondSquared();
		Vec3 rawSpecificForce = trueSpecificForce
				.add(accelerometerScaleErrorBodyMetersPerSecondSquared(trueSpecificForce))
				.add(state.accelerometerBiasBodyMetersPerSecondSquared())
				.add(accelerometerNoiseBodyMetersPerSecondSquared());
		state.setAccelerometerClipIntensity(sensorClipIntensity(rawSpecificForce, ACCELEROMETER_FULL_SCALE_METERS_PER_SECOND_SQUARED));
		Vec3 noisySpecificForce = clipSensorVector(rawSpecificForce, ACCELEROMETER_FULL_SCALE_METERS_PER_SECOND_SQUARED);
		double cutoffHz = config.accelerometerLowPassCutoffHz();
		double alpha = cutoffHz <= 0.0
				? 1.0
				: MathUtil.expSmoothing(dtSeconds, 1.0 / (Math.PI * 2.0 * cutoffHz));
		accelerometerFilteredBodyMetersPerSecondSquared = accelerometerFilteredBodyMetersPerSecondSquared.add(
				noisySpecificForce.subtract(accelerometerFilteredBodyMetersPerSecondSquared).multiply(alpha)
		);

		accelerometerDelayBuffer[accelerometerDelayWriteIndex] = accelerometerFilteredBodyMetersPerSecondSquared;
		int delaySamples = Math.min(
				GYRO_DELAY_BUFFER_SIZE - 1,
				(int) Math.round(config.controlLatencySeconds() / Math.max(dtSeconds, 1.0e-6))
		);
		int readIndex = accelerometerDelayWriteIndex - delaySamples;
		if (readIndex < 0) {
			readIndex += GYRO_DELAY_BUFFER_SIZE;
		}
		state.setAccelerometerBodyMetersPerSecondSquared(accelerometerDelayBuffer[readIndex]);
		accelerometerDelayWriteIndex = (accelerometerDelayWriteIndex + 1) % GYRO_DELAY_BUFFER_SIZE;
	}

	private Vec3 accelerometerScaleErrorBodyMetersPerSecondSquared(Vec3 trueSpecificForce) {
		double noise = config.accelerometerNoiseStdDevMetersPerSecondSquared();
		if (noise <= 0.0 || trueSpecificForce == null || trueSpecificForce.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double gravity = Math.max(1.0e-6, config.gravityMetersPerSecondSquared());
		Vec3 gForce = trueSpecificForce.multiply(1.0 / gravity);
		double gMagnitude = gForce.length();
		if (gMagnitude <= 1.35) {
			return Vec3.ZERO;
		}

		double sensorScale = MathUtil.clamp(noise / 0.22, 0.20, 2.5);
		double exposure = smoothStep(1.35, 6.0, gMagnitude);
		double compression = MathUtil.clamp(-0.010 * sensorScale * exposure * (gMagnitude - 1.0), -0.12, 0.0);
		Vec3 scaleError = trueSpecificForce.multiply(compression);
		Vec3 crossAxisError = new Vec3(
				0.32 * gForce.y() - 0.18 * gForce.z(),
				-0.22 * gForce.x() + 0.14 * gForce.z(),
				0.26 * gForce.x() + 0.18 * gForce.y()
		).multiply(gravity * 0.0035 * sensorScale * exposure);
		return scaleError.add(crossAxisError).clamp(-gravity * 2.0, gravity * 2.0);
	}

	private Vec3 specificForceBodyMetersPerSecondSquared() {
		Vec3 gravityWorld = new Vec3(0.0, -config.gravityMetersPerSecondSquared(), 0.0);
		Vec3 specificForceWorld = state.linearAccelerationWorldMetersPerSecondSquared().subtract(gravityWorld);
		Vec3 specificForceBody = state.orientation().conjugate().rotate(specificForceWorld);
		Vec3 imuOffsetBody = config.imuOffsetBodyMeters();
		if (imuOffsetBody.lengthSquared() <= 1.0e-12) {
			return specificForceBody;
		}

		Vec3 omega = state.angularVelocityBodyRadiansPerSecond();
		Vec3 angularAcceleration = state.angularAccelerationBodyRadiansPerSecondSquared();
		Vec3 leverArmAcceleration = angularAcceleration.cross(imuOffsetBody)
				.add(omega.cross(omega.cross(imuOffsetBody)));
		return specificForceBody.add(leverArmAcceleration);
	}

	private Vec3 accelerometerNoiseBodyMetersPerSecondSquared() {
		double noise = config.accelerometerNoiseStdDevMetersPerSecondSquared();
		double vibration = state.rotorVibration();
		double busRipple = state.batteryBusRippleVoltage();
		double supplyNoise = state.imuSupplyNoiseIntensity();
		if (noise <= 0.0 && vibration <= 0.0 && busRipple <= 0.0 && supplyNoise <= 0.0) {
			return Vec3.ZERO;
		}

		double motorVibration = 0.20 + 0.80 * state.averageMotorPower(config);
		double propwashVibration = 1.0 + 0.9 * state.propwashIntensity();
		double scale = noise * motorVibration * propwashVibration / 1.30
				+ 4.0 * vibration
				+ (0.18 * busRipple + 0.24 * supplyNoise) * (0.30 + 0.70 * motorVibration);
		double t = accelerometerNoiseTimeSeconds;
		return new Vec3(
				scale * (Math.sin(t * 173.0 + 0.7) + 0.42 * Math.sin(t * 353.0 + 2.2)),
				scale * (Math.sin(t * 211.0 + 1.3) + 0.35 * Math.sin(t * 421.0 + 0.5)),
				scale * (Math.sin(t * 197.0 + 2.6) + 0.38 * Math.sin(t * 389.0 + 1.1))
		);
	}

	private void resetAccelerometerModel() {
		accelerometerFilteredBodyMetersPerSecondSquared = specificForceBodyMetersPerSecondSquared();
		Arrays.fill(accelerometerDelayBuffer, accelerometerFilteredBodyMetersPerSecondSquared);
		state.setAccelerometerBodyMetersPerSecondSquared(accelerometerFilteredBodyMetersPerSecondSquared);
		state.setAccelerometerClipIntensity(0.0);
		accelerometerDelayWriteIndex = 0;
		accelerometerNoiseTimeSeconds = 0.0;
	}

	private void updateBarometerMeasurement(DroneEnvironment environment, double dtSeconds) {
		barometerNoiseTimeSeconds += dtSeconds;
		double trueAltitude = state.positionMeters().y();
		double pressurePortError = updateBarometerPressurePortErrorMeters(environment, dtSeconds);
		double propwashError = updateBarometerPropwashErrorMeters(environment, dtSeconds);
		double flowError = MathUtil.clamp(pressurePortError + propwashError, -2.5, 4.5);
		double sensorNoise = barometerNoiseMeters(environment);
		double rawAltitude = trueAltitude + flowError + sensorNoise;

		if (!barometerInitialized) {
			barometerFilteredAltitudeMeters = rawAltitude;
			barometerFilteredVerticalSpeedMetersPerSecond = state.velocityMetersPerSecond().y();
			barometerInitialized = true;
		} else {
			double previousAltitude = barometerFilteredAltitudeMeters;
			double altitudeAlpha = MathUtil.expSmoothing(dtSeconds, BAROMETER_ALTITUDE_TIME_CONSTANT_SECONDS);
			barometerFilteredAltitudeMeters += (rawAltitude - barometerFilteredAltitudeMeters) * altitudeAlpha;
			double rawVerticalSpeed = (barometerFilteredAltitudeMeters - previousAltitude) / Math.max(dtSeconds, 1.0e-6);
			double verticalSpeedAlpha = MathUtil.expSmoothing(dtSeconds, BAROMETER_VERTICAL_SPEED_TIME_CONSTANT_SECONDS);
			barometerFilteredVerticalSpeedMetersPerSecond += (rawVerticalSpeed - barometerFilteredVerticalSpeedMetersPerSecond) * verticalSpeedAlpha;
		}

		state.setBarometerAltitudeMeters(barometerFilteredAltitudeMeters);
		state.setBarometerVerticalSpeedMetersPerSecond(barometerFilteredVerticalSpeedMetersPerSecond);
		state.setBarometerPressureHectopascals(DroneEnvironment.barometricPressureHectopascals(
				barometerFilteredAltitudeMeters,
				environment.airDensityRatio(),
				environment.effectiveAmbientTemperatureCelsius(),
				environment.adoptedWindSourcePressureAnomalyPascals()
		));
		state.setBarometerErrorMeters(barometerFilteredAltitudeMeters - trueAltitude);
		state.setBarometerSensorNoiseMeters(sensorNoise);
		state.setBarometerPressurePortErrorMeters(pressurePortError);
		state.setBarometerPropwashErrorMeters(propwashError);
	}

	private double updateBarometerPressurePortErrorMeters(DroneEnvironment environment, double dtSeconds) {
		double target = calculateSteadyBarometerPressurePortErrorMeters(environment);
		barometerPressurePortErrorFilteredMeters = updateFilteredBarometerAerodynamicErrorMeters(
				barometerPressurePortErrorFilteredMeters,
				target,
				dtSeconds,
				-1.8,
				2.2
		);
		return barometerPressurePortErrorFilteredMeters;
	}

	private double updateBarometerPropwashErrorMeters(DroneEnvironment environment, double dtSeconds) {
		double target = calculateSteadyBarometerPropwashErrorMeters(environment);
		barometerPropwashErrorFilteredMeters = updateFilteredBarometerAerodynamicErrorMeters(
				barometerPropwashErrorFilteredMeters,
				target,
				dtSeconds,
				-2.5,
				4.5
		);
		return barometerPropwashErrorFilteredMeters;
	}

	private double updateFilteredBarometerAerodynamicErrorMeters(
			double previous,
			double target,
			double dtSeconds,
			double minMeters,
			double maxMeters
	) {
		if (dtSeconds <= 0.0) {
			return MathUtil.clamp(target, minMeters, maxMeters);
		}

		double previousMagnitude = Math.abs(previous);
		double targetMagnitude = Math.abs(target);
		double timeConstant = targetMagnitude > previousMagnitude ? 0.038 : 0.125;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double filtered = previous + (target - previous) * alpha;
		filtered = MathUtil.clamp(filtered, minMeters, maxMeters);
		if (targetMagnitude <= 1.0e-6 && Math.abs(filtered) < 1.0e-5) {
			filtered = 0.0;
		}
		return filtered;
	}

	private double calculateSteadyBarometerPressurePortErrorMeters(DroneEnvironment environment) {
		return MathUtil.clamp(
				barometerDynamicPressureErrorMeters(environment) + a4mcLocalStaticPortPressureErrorMeters(environment),
				-1.8,
				2.2
		);
	}

	private double calculateSteadyBarometerPropwashErrorMeters(DroneEnvironment environment) {
		double motorPower = state.averageMotorPower(config);
		double inducedVelocity = state.averageRotorInducedVelocityMetersPerSecond();
		double cleanRotorWash = smoothStep(0.08, 0.35, motorPower) * smoothStep(0.5, 5.5, inducedVelocity);
		double unsteadyWash = 0.85 * state.propwashIntensity()
				+ 0.55 * state.vortexRingStateIntensity()
				+ 0.25 * state.rotorVibration()
				+ 0.32 * environment.droneWakeIntensity();
		double groundCompression = environment.groundEffectIntensity(config);
		double ceilingSuction = environment.ceilingEffectIntensity(config);
		double propwashError = 0.90 * cleanRotorWash
				+ 1.15 * unsteadyWash
				+ 0.35 * ceilingSuction
				- 0.60 * groundCompression;
		return MathUtil.clamp(propwashError, -2.5, 4.5);
	}

	private double barometerDynamicPressureErrorMeters(DroneEnvironment environment) {
		Vec3 relativeAirVelocityBody = state.relativeAirVelocityBodyMetersPerSecond();
		double airDensityRatio = environment.effectiveAirDensityRatio();
		if (airDensityRatio <= 0.0) {
			return 0.0;
		}

		double densityScale = MathUtil.clamp(airDensityRatio, 0.35, 1.35);
		double linearPressureError = barometerLinearDynamicPressureErrorMeters(relativeAirVelocityBody);
		double rotationalPressureError = barometerRotationalDynamicPressureErrorMeters();
		return MathUtil.clamp(densityScale * (linearPressureError + rotationalPressureError), -1.8, 2.2);
	}

	private double a4mcLocalStaticPortPressureErrorMeters(DroneEnvironment environment) {
		if (!environment.windSourceLocalVoxelFlow()) {
			return 0.0;
		}
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		double pressureAnomalyPascals = environment.adoptedWindSourcePressureAnomalyPascals();
		if (Math.abs(pressureAnomalyPascals) <= 1.0e-6) {
			return 0.0;
		}

		int sampleCount = Math.max(1, state.motorCount());
		double localVoxelCoverageSum = 0.0;
		double shelterObstructionSum = 0.0;
		for (int i = 0; i < sampleCount; i++) {
			localVoxelCoverageSum += MathUtil.clamp(1.0 - environment.rotorLocalVoxelObstacleResidual(i), 0.0, 1.0);
			shelterObstructionSum += environment.rotorA4mcShelterObstruction(i);
		}

		double localVoxelCoverage = localVoxelCoverageSum / sampleCount;
		double shelterObstruction = shelterObstructionSum / sampleCount;
		double exposure = MathUtil.clamp(
				0.35
						+ 0.35 * environment.windShelterFactor()
						+ 0.18 * localVoxelCoverage
						+ 0.12 * shelterObstruction,
				0.25,
				1.0
		);
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER
				* MathUtil.clamp(environment.effectiveAirDensityRatio(), 0.35, 1.35);
		double pressureHeightMeters = -pressureAnomalyPascals / Math.max(1.0e-6, density * config.gravityMetersPerSecondSquared());
		return MathUtil.clamp(pressureHeightMeters * exposure, -0.65, 0.65);
	}

	private double barometerLinearDynamicPressureErrorMeters(Vec3 relativeAirVelocityBody) {
		if (relativeAirVelocityBody == null || relativeAirVelocityBody.lengthSquared() <= 1.0e-6) {
			return 0.0;
		}

		double airspeed = state.airspeedMetersPerSecond();
		if (airspeed < 3.0) {
			return 0.0;
		}
		double angleOfAttack = Math.abs(state.angleOfAttackRadians());
		double sideslip = Math.abs(state.sideslipRadians());
		double maxFlowAngle = Math.max(angleOfAttack, sideslip);
		double dynamicScale = smoothStep(4.0, 22.0, airspeed);
		double alignedFlow = 1.0 - smoothStep(Math.toRadians(18.0), Math.toRadians(58.0), maxFlowAngle);
		double separatedFlow = effectiveAirframeSeparationIntensity(relativeAirVelocityBody);
		double broadsideFlow = smoothStep(Math.toRadians(20.0), Math.toRadians(70.0), maxFlowAngle);
		double ramAltitudeError = -0.0026 * airspeed * airspeed * dynamicScale * alignedFlow;
		double suctionAltitudeError = 0.0018 * airspeed * airspeed * dynamicScale
				* MathUtil.clamp(0.35 * broadsideFlow + 0.65 * separatedFlow, 0.0, 1.0);
		return ramAltitudeError + suctionAltitudeError;
	}

	private double barometerRotationalDynamicPressureErrorMeters() {
		Vec3 omega = state.angularVelocityBodyRadiansPerSecond();
		if (omega == null || omega.lengthSquared() <= 1.0e-6) {
			return 0.0;
		}

		double tumbleRate = Math.hypot(omega.x(), omega.z());
		double yawRate = Math.abs(omega.y());
		double coupledRate = Math.hypot(tumbleRate, 0.55 * yawRate);
		if (coupledRate < Math.toRadians(360.0)) {
			return 0.0;
		}

		double staticPortRadius = equivalentStaticPortRadiusMeters();
		double tumbleLocalSpeed = tumbleRate * staticPortRadius;
		double yawLocalSpeed = yawRate * staticPortRadius * 0.55;
		double localSpeedSquared = tumbleLocalSpeed * tumbleLocalSpeed + yawLocalSpeed * yawLocalSpeed;
		double rateExposure = smoothStep(Math.toRadians(480.0), Math.toRadians(1600.0), coupledRate);
		double tumbleBias = MathUtil.clamp(0.55 + 0.45 * (tumbleRate / Math.max(1.0e-6, coupledRate)), 0.55, 1.0);
		return MathUtil.clamp(0.036 * localSpeedSquared * rateExposure * tumbleBias, 0.0, 1.25);
	}

	private double equivalentStaticPortRadiusMeters() {
		double rotorArmSum = 0.0;
		int rotorCount = 0;
		Vec3 centerOfMass = config.centerOfMassOffsetBodyMeters();
		for (RotorSpec rotor : config.rotors()) {
			rotorArmSum += rotor.positionBodyMeters().subtract(centerOfMass).length();
			rotorCount++;
		}

		double averageRotorArm = rotorCount == 0 ? 0.12 : rotorArmSum / rotorCount;
		double boardOffset = config.imuOffsetBodyMeters().length();
		double frameExposureRadius = averageRotorArm * 0.75;
		return MathUtil.clamp(Math.max(boardOffset, frameExposureRadius), 0.06, 0.28);
	}

	private double barometerNoiseMeters(DroneEnvironment environment) {
		double motorRailCoupling = 0.35 + 0.65 * state.averageMotorPower(config);
		double noiseAmplitude = SensorNoiseCalibration.QUIET_BAROMETER_ACCEL_NOISE_TO_ALTITUDE_AMPLITUDE
				* config.accelerometerNoiseStdDevMetersPerSecondSquared()
				+ 0.040 * atmosphericTurbulenceIntensity(environment)
				+ 0.090 * state.rotorVibration()
				+ 0.035 * state.propwashIntensity()
				+ (0.055 * state.batteryBusRippleVoltage() + 0.030 * state.imuSupplyNoiseIntensity()) * motorRailCoupling;
		if (noiseAmplitude <= 1.0e-9) {
			return 0.0;
		}

		double t = barometerNoiseTimeSeconds;
		return noiseAmplitude * (
				Math.sin(t * 7.7 + 0.4)
						+ 0.35 * Math.sin(t * 17.9 + 1.6)
						+ 0.18 * Math.sin(t * 41.0 + 2.7)
		);
	}

	private void resetBarometerModel() {
		barometerNoiseTimeSeconds = 0.0;
		barometerFilteredAltitudeMeters = state.positionMeters().y();
		barometerFilteredVerticalSpeedMetersPerSecond = state.velocityMetersPerSecond().y();
		barometerPressurePortErrorFilteredMeters = 0.0;
		barometerPropwashErrorFilteredMeters = 0.0;
		barometerInitialized = false;
		state.setBarometerAltitudeMeters(barometerFilteredAltitudeMeters);
		state.setBarometerVerticalSpeedMetersPerSecond(barometerFilteredVerticalSpeedMetersPerSecond);
		state.setBarometerPressureHectopascals(DroneEnvironment.barometricPressureHectopascals(barometerFilteredAltitudeMeters, 1.0, 25.0));
		state.setBarometerErrorMeters(0.0);
		state.setBarometerSensorNoiseMeters(0.0);
		state.setBarometerPressurePortErrorMeters(0.0);
		state.setBarometerPropwashErrorMeters(0.0);
	}

	private static double sensorClipIntensity(Vec3 measurement, double fullScale) {
		if (fullScale <= 1.0e-9) {
			return 0.0;
		}
		double peak = Math.max(
				Math.max(Math.abs(measurement.x()), Math.abs(measurement.y())),
				Math.abs(measurement.z())
		);
		return MathUtil.clamp((peak - fullScale) / fullScale, 0.0, 1.0);
	}

	private static Vec3 clipSensorVector(Vec3 measurement, double fullScale) {
		return new Vec3(
				MathUtil.clamp(measurement.x(), -fullScale, fullScale),
				MathUtil.clamp(measurement.y(), -fullScale, fullScale),
				MathUtil.clamp(measurement.z(), -fullScale, fullScale)
		);
	}

	private void resetSensorBiasModel() {
		gyroBiasBodyRadiansPerSecond = Vec3.ZERO;
		accelerometerBiasBodyMetersPerSecondSquared = Vec3.ZERO;
		sensorBiasTimeSeconds = 0.0;
		state.setGyroBiasBodyRadiansPerSecond(Vec3.ZERO);
		state.setAccelerometerBiasBodyMetersPerSecondSquared(Vec3.ZERO);
	}

	private void updateAttitudeEstimator(double dtSeconds) {
		Quaternion previousEstimate = state.estimatedOrientation();
		Vec3 correctionBody = attitudeAccelerometerCorrectionBody(previousEstimate);
		Vec3 estimatorRateBody = state.gyroAngularVelocityBodyRadiansPerSecond().add(correctionBody);
		Quaternion updatedEstimate = previousEstimate.integrateBodyAngularVelocity(estimatorRateBody, dtSeconds);
		state.setEstimatedOrientation(updatedEstimate);
		updateAttitudeEstimateError(updatedEstimate);
	}

	private Vec3 attitudeAccelerometerCorrectionBody(Quaternion estimatedOrientation) {
		double gain = config.attitudeEstimatorAccelerometerCorrectionGain();
		Vec3 measuredSpecificForce = state.accelerometerBodyMetersPerSecondSquared();
		double magnitude = measuredSpecificForce.length();
		if (gain <= 0.0 || magnitude <= 1.0e-6) {
			state.setAttitudeEstimatorAccelerometerTrust(0.0);
			return Vec3.ZERO;
		}

		double trust = 1.0 - Math.abs(magnitude - config.gravityMetersPerSecondSquared())
				/ config.attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared();
		trust = MathUtil.clamp(trust, 0.0, 1.0) * attitudeAccelerometerQualityScale();
		state.setAttitudeEstimatorAccelerometerTrust(trust);
		if (trust <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 measuredUpBody = measuredSpecificForce.normalized();
		Vec3 estimatedUpBody = estimatedOrientation.conjugate().rotate(WORLD_UP).normalized();
		return measuredUpBody.cross(estimatedUpBody).multiply(gain * trust);
	}

	private double attitudeAccelerometerQualityScale() {
		double vibrationLoss = 0.72 * smoothStep(0.055, 0.240, state.rotorVibration());
		double clipLoss = 0.85 * MathUtil.clamp(state.accelerometerClipIntensity(), 0.0, 1.0);
		return MathUtil.clamp((1.0 - vibrationLoss) * (1.0 - clipLoss), 0.0, 1.0);
	}

	private void updateAttitudeEstimateError(Quaternion estimatedOrientation) {
		Vec3 trueEuler = state.orientation().toEulerXYZRadians();
		Vec3 estimatedEuler = estimatedOrientation.toEulerXYZRadians();
		state.setAttitudeEstimateErrorRadians(new Vec3(
				normalizeAngleRadians(trueEuler.x() - estimatedEuler.x()),
				normalizeAngleRadians(trueEuler.y() - estimatedEuler.y()),
				normalizeAngleRadians(trueEuler.z() - estimatedEuler.z())
		));
	}

	private void resetAttitudeEstimator() {
		state.setEstimatedOrientation(state.orientation());
		state.setAttitudeEstimateErrorRadians(Vec3.ZERO);
		state.setAttitudeEstimatorAccelerometerTrust(0.0);
	}

	private static double normalizeAngleRadians(double radians) {
		double normalized = radians;
		while (normalized > Math.PI) {
			normalized -= Math.PI * 2.0;
		}
		while (normalized < -Math.PI) {
			normalized += Math.PI * 2.0;
		}
		return normalized;
	}

	private void resetControlLinkModel() {
		lastLinkedControlInput = DroneInput.idle();
		smoothedControlInput = DroneInput.idle();
		receiverFrameInput = DroneInput.idle();
		Arrays.fill(controlDelayBuffer, DroneInput.idle());
		controlDelayWriteIndex = 0;
		controlLinkLossSeconds = 0.0;
		receiverFrameClockSeconds = 0.0;
		receiverFrameAgeSeconds = 0.0;
		state.setRawControlInput(DroneInput.idle());
		state.setProcessedControlInput(DroneInput.idle());
		state.setControlLinkLossSeconds(0.0);
		state.setControlFailsafeActive(false);
		state.setControlFrameTelemetry(0.0, receiverFrameIntervalSeconds(), 0.0);
	}

	private void resetEscSignalModel() {
		Arrays.fill(heldEscOutputCommands, 0.0);
		Arrays.fill(escCommandFrameClockSeconds, 0.0);
		Arrays.fill(escCommandFrameAgeSeconds, 0.0);
		Arrays.fill(escCommandErrors, 0.0);
		Arrays.fill(escCommandFrameInitialized, false);
		for (int i = 0; i < state.motorCount(); i++) {
			resetEscElectricalOutput(i);
		}
		resetEscRpmTelemetryModel();
		state.setEscCommandTelemetry(0.0, escCommandFrameIntervalSeconds(), 0.0);
	}

	private void resetEscRpmTelemetryModel() {
		Arrays.fill(escRpmTelemetryOmegaRadiansPerSecond, 0.0);
		Arrays.fill(escRpmTelemetryFrameClockSeconds, 0.0);
		Arrays.fill(escRpmTelemetryFrameAgeSeconds, 0.0);
		Arrays.fill(escRpmTelemetryDropoutPhases, 0.0);
		Arrays.fill(escRpmTelemetryFrameInitialized, false);
		for (int i = 0; i < state.motorCount(); i++) {
			state.setMotorRpmTelemetry(i, 0.0, 0.0);
		}
	}

	private void updateEscRpmTelemetry(int index, RotorSpec rotor, double omegaRadiansPerSecond, double dtSeconds) {
		double intervalSeconds = escCommandFrameIntervalSeconds();
		double telemetryValidity = escRpmTelemetryValidity(omegaRadiansPerSecond);
		double dropoutIntensity = escRpmTelemetryDropoutIntensity(index, telemetryValidity);
		double measuredOmega = telemetryValidity >= 0.5
				? quantizeBidirectionalDshotRpmTelemetry(rotor, omegaRadiansPerSecond)
				: 0.0;
		if (intervalSeconds <= 1.0e-9) {
			escRpmTelemetryOmegaRadiansPerSecond[index] = measuredOmega;
			escRpmTelemetryFrameClockSeconds[index] = 0.0;
			escRpmTelemetryFrameAgeSeconds[index] = 0.0;
			escRpmTelemetryFrameInitialized[index] = true;
			state.setMotorRpmTelemetry(index, measuredOmega, telemetryValidity * (1.0 - 0.55 * dropoutIntensity));
			return;
		}

		double phaseOffset = escCommandFramePhaseOffsetSeconds(index, intervalSeconds);
		if (!escRpmTelemetryFrameInitialized[index]) {
			escRpmTelemetryOmegaRadiansPerSecond[index] = measuredOmega;
			escRpmTelemetryFrameClockSeconds[index] = -phaseOffset;
			escRpmTelemetryFrameAgeSeconds[index] = 0.0;
			escRpmTelemetryFrameInitialized[index] = true;
			state.setMotorRpmTelemetry(index, measuredOmega, telemetryValidity);
			return;
		}

		escRpmTelemetryFrameClockSeconds[index] += Math.max(0.0, dtSeconds);
		if (escRpmTelemetryFrameClockSeconds[index] >= intervalSeconds) {
			boolean frameDropped = escRpmTelemetryFrameDropped(index, dropoutIntensity, intervalSeconds);
			escRpmTelemetryFrameClockSeconds[index] -= intervalSeconds;
			if (escRpmTelemetryFrameClockSeconds[index] >= intervalSeconds) {
				escRpmTelemetryFrameClockSeconds[index] = 0.0;
			}
			if (frameDropped) {
				double staleAge = Math.min(
						intervalSeconds * 4.0,
						escRpmTelemetryFrameAgeSeconds[index] + intervalSeconds
				);
				escRpmTelemetryFrameAgeSeconds[index] = staleAge;
				double staleFade = 1.0 - smoothStep(intervalSeconds * 0.85, intervalSeconds * 3.2, staleAge);
				double staleValidity = Math.min(state.motorRpmTelemetryValidity(index), telemetryValidity)
						* staleFade
						* (1.0 - 0.72 * dropoutIntensity);
				state.setMotorRpmTelemetry(index, escRpmTelemetryOmegaRadiansPerSecond[index], staleValidity);
			} else {
				escRpmTelemetryOmegaRadiansPerSecond[index] = measuredOmega;
				escRpmTelemetryFrameAgeSeconds[index] = 0.0;
				state.setMotorRpmTelemetry(index, measuredOmega, telemetryValidity * (1.0 - 0.28 * dropoutIntensity));
			}
		} else {
			escRpmTelemetryFrameAgeSeconds[index] = Math.min(
					intervalSeconds * 4.0,
					escRpmTelemetryFrameAgeSeconds[index] + Math.max(0.0, dtSeconds)
			);
		}
	}

	private double escRpmTelemetryDropoutIntensity(int index, double telemetryValidity) {
		if (index < 0 || index >= state.motorCount() || telemetryValidity <= 0.0) {
			return 0.0;
		}

		double perMotorMaxCurrentAmps = config.maxBatteryCurrentAmps() / Math.max(1, state.motorCount());
		double currentRippleStress = perMotorMaxCurrentAmps <= 1.0e-6
				? 0.0
				: smoothStep(0.045, 0.36, state.motorCurrentRippleAmps(index) / perMotorMaxCurrentAmps);
		double lowSpeedEdge = 1.0 - smoothStep(0.55, 0.98, telemetryValidity);
		double voltageHeadroomStress = motorVoltageHeadroomStress(index)
				* smoothStep(0.35, 0.92, state.escElectricalOutputCommand(index));
		double brakingOverrun = Math.max(0.0, state.motorPower(config, index) - state.escElectricalOutputCommand(index));
		double brakingStress = MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0)
				* smoothStep(0.06, 0.42, brakingOverrun)
				* batteryBusSpikeStress();
		double electricalStress = 0.82 * state.escDesyncIntensity(index)
				+ 0.28 * state.motorCommutationRippleIntensity(index)
				+ 0.26 * currentRippleStress
				+ 0.22 * batteryBusRippleStress()
				+ 0.20 * batteryBusSpikeStress()
				+ 0.16 * voltageHeadroomStress
				+ 0.15 * brakingStress
				+ 0.10 * lowSpeedEdge;
		return MathUtil.clamp((electricalStress - 0.18) / 0.72, 0.0, 0.95);
	}

	private boolean escRpmTelemetryFrameDropped(int index, double dropoutIntensity, double intervalSeconds) {
		if (dropoutIntensity <= 1.0e-6) {
			return false;
		}

		escRpmTelemetryDropoutPhases[index] += intervalSeconds
				* (37.0 + 11.0 * index + 53.0 * dropoutIntensity);
		double phase = escRpmTelemetryDropoutPhases[index] + index * 0.61;
		double carrier = 0.50
				+ 0.34 * Math.sin(phase)
				+ 0.16 * Math.sin(phase * 2.17 + 1.4 + index * 0.29);
		double burstBoost = 0.24 * smoothStep(0.42, 0.85, dropoutIntensity);
		return carrier < dropoutIntensity + burstBoost;
	}

	private void integrateMotorThermal(DroneEnvironment environment, double dtSeconds) {
		Vec3 relativeAirVelocityBody = state.relativeAirVelocityBodyMetersPerSecond();
		Vec3 angularVelocityBody = state.angularVelocityBodyRadiansPerSecond();
		for (int i = 0; i < state.motorCount(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double perMotorMaxCurrentAmps = config.maxBatteryCurrentAmps() / state.motorCount();
			double currentLoad = perMotorMaxCurrentAmps <= 1.0e-6
					? 0.0
					: MathUtil.clamp(state.motorCurrentAmps(i) / perMotorMaxCurrentAmps, 0.0, 1.2);
			double brakingLoad = config.motorActiveBrakingStrength()
					* Math.max(0.0, state.motorPower(config, i) - state.escElectricalOutputCommand(i));
			double power = MathUtil.clamp(
					0.40 * state.escElectricalOutputCommand(i) + 0.48 * currentLoad + 0.12 * brakingLoad,
					0.0,
					1.2
			);
			double temperature = state.motorTemperatureCelsius(i);
			double heatRate = config.motorThermalRiseCelsiusPerSecond() * power * power;
			double coolingFactor = motorCoolingFactor(rotor, relativeAirVelocityBody, angularVelocityBody, environment, i);
			state.setMotorCoolingFactor(i, coolingFactor);
			double coolingRate = config.motorCoolingRatePerSecond()
					* coolingFactor
					* (temperature - environment.effectiveAmbientTemperatureCelsius());
			state.setMotorTemperatureCelsius(i, temperature + (heatRate - coolingRate) * dtSeconds);
			updateMotorWindingResistanceScale(i);
		}
		updateMotorThermalLimit();
	}

	private void integrateEscThermal(DroneEnvironment environment, double dtSeconds) {
		for (int i = 0; i < state.motorCount(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double perEscMaxCurrentAmps = config.maxBatteryCurrentAmps() / state.motorCount();
			double currentLoad = perEscMaxCurrentAmps <= 1.0e-6
					? 0.0
					: MathUtil.clamp(state.motorCurrentAmps(i) / perEscMaxCurrentAmps, 0.0, 1.35);
			double output = state.escElectricalOutputCommand(i);
			double rpmFraction = state.motorPower(config, i);
			double accelerationStress = MathUtil.clamp(
					Math.abs(state.motorAngularAccelerationRadiansPerSecondSquared(i))
							/ Math.max(1.0, rotor.maxOmegaRadiansPerSecond() / 0.055),
					0.0,
					1.4
			);
			double brakingLoad = config.motorActiveBrakingStrength() * Math.max(0.0, rpmFraction - output);
			double switchingStress = output * (1.0 - output) * (0.35 + 0.65 * rpmFraction)
					+ 0.22 * accelerationStress;
			double heatStress = MathUtil.clamp(
					0.62 * currentLoad * currentLoad
							+ 0.26 * switchingStress
							+ 0.22 * brakingLoad
							+ 0.28 * state.escDesyncIntensity(i),
					0.0,
					2.8
			);

			double temperature = state.escTemperatureCelsius(i);
			double heatRate = config.motorThermalRiseCelsiusPerSecond() * 0.72 * heatStress;
			double coolingFactor = escCoolingFactor(environment, i);
			state.setEscCoolingFactor(i, coolingFactor);
			double coolingRate = config.motorCoolingRatePerSecond()
					* 0.90
					* coolingFactor
					* (temperature - environment.effectiveAmbientTemperatureCelsius());
			state.setEscTemperatureCelsius(i, temperature + (heatRate - coolingRate) * dtSeconds);
			state.setEscThermalLimit(i, escThermalLimit(state.escTemperatureCelsius(i)));
		}
		updateEscThermalLimit();
	}

	private double escCoolingFactor(DroneEnvironment environment, int rotorIndex) {
		double rotorWashCooling = 0.45 * state.motorPower(config, rotorIndex) * (0.35 + 0.65 * state.escElectricalOutputCommand(rotorIndex));
		double boardAirflow = 0.58 + 0.42 * state.motorCoolingFactor(rotorIndex) + rotorWashCooling;
		double obstructionLoss = 1.0 - 0.36 * environment.rotorFlowObstruction(rotorIndex);
		double recirculationEfficiency = 1.0 - 0.78 * recirculatedAirCoolingLoss(environment);
		double localShelterEfficiency = a4mcLocalVoxelVentilationEfficiency(environment, rotorIndex);
		state.setRotorA4mcVentilationEfficiency(rotorIndex, localShelterEfficiency);
		double densityFactor = MathUtil.clamp(environment.effectiveAirDensityRatio(), 0.35, 1.35);
		double moistAirCooling = environment.moistAirCoolingMultiplier();
		return MathUtil.clamp(
				boardAirflow * densityFactor * moistAirCooling * obstructionLoss * recirculationEfficiency * localShelterEfficiency,
				0.20,
				4.0
		);
	}

	private double motorCoolingFactor(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			Vec3 angularVelocityBody,
			DroneEnvironment environment,
			int rotorIndex
	) {
		Vec3 rotorRelativeAirVelocityBody = relativeAirVelocityBody.add(angularVelocityBody.cross(rotor.positionBodyMeters()));
		double transverseSpeed = Math.hypot(rotorRelativeAirVelocityBody.x(), rotorRelativeAirVelocityBody.z());
		double axialSpeed = Math.abs(rotorRelativeAirVelocityBody.y());
		double freestreamCooling = MathUtil.clamp(transverseSpeed / 18.0, 0.0, 1.8)
				+ 0.35 * MathUtil.clamp(axialSpeed / 12.0, 0.0, 1.0);
		double rotorWashCooling = 0.92 * state.motorPower(config, rotorIndex) * (0.45 + 0.55 * state.escElectricalOutputCommand(rotorIndex));
		double obstructionLoss = 1.0 - 0.48 * environment.rotorFlowObstruction(rotorIndex);
		double recirculationEfficiency = 1.0 - recirculatedAirCoolingLoss(environment);
		double localShelterEfficiency = a4mcLocalVoxelVentilationEfficiency(environment, rotorIndex);
		state.setRotorA4mcVentilationEfficiency(rotorIndex, localShelterEfficiency);
		double densityFactor = MathUtil.clamp(environment.effectiveAirDensityRatio(), 0.35, 1.35);
		double moistAirCooling = environment.moistAirCoolingMultiplier();
		return MathUtil.clamp(
				(1.0 + freestreamCooling + rotorWashCooling) * densityFactor * moistAirCooling * obstructionLoss * recirculationEfficiency * localShelterEfficiency,
				0.20,
				4.0
		);
	}

	private static double a4mcLocalVoxelVentilationEfficiency(DroneEnvironment environment, int rotorIndex) {
		if (!environment.windSourceLocalVoxelFlow()) {
			return 1.0;
		}
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return 1.0;
		}
		double bodyShelter = MathUtil.clamp(environment.windShelterFactor() * sourceQuality, 0.0, 1.0);
		double localVoxelCoverage = MathUtil.clamp(1.0 - environment.rotorLocalVoxelObstacleResidual(rotorIndex), 0.0, 1.0);
		double rotorShelterObstruction = MathUtil.clamp(environment.rotorA4mcShelterObstruction(rotorIndex), 0.0, 1.0);
		double ventilationLoss = 0.20 * bodyShelter
				+ 0.12 * localVoxelCoverage
				+ 0.18 * rotorShelterObstruction;
		return MathUtil.clamp(1.0 - ventilationLoss, 0.72, 1.0);
	}

	private static double a4mcPackVentilationEfficiency(DroneEnvironment environment, int rotorCount) {
		if (!environment.windSourceLocalVoxelFlow()) {
			return 1.0;
		}
		double sourceQuality = a4mcWindSourceQualityFactor(environment);
		if (sourceQuality <= 1.0e-9) {
			return 1.0;
		}

		int sampleCount = Math.max(1, rotorCount);
		double localVoxelCoverageSum = 0.0;
		double shelterObstructionSum = 0.0;
		for (int i = 0; i < sampleCount; i++) {
			localVoxelCoverageSum += MathUtil.clamp(1.0 - environment.rotorLocalVoxelObstacleResidual(i), 0.0, 1.0);
			shelterObstructionSum += environment.rotorA4mcShelterObstruction(i);
		}

		double bodyShelter = MathUtil.clamp(environment.windShelterFactor() * sourceQuality, 0.0, 1.0);
		double averageLocalVoxelCoverage = localVoxelCoverageSum / sampleCount;
		double averageShelterObstruction = shelterObstructionSum / sampleCount;
		double ventilationLoss = 0.14 * bodyShelter
				+ 0.08 * averageLocalVoxelCoverage
				+ 0.10 * averageShelterObstruction;
		return MathUtil.clamp(1.0 - ventilationLoss, 0.78, 1.0);
	}

	private double recirculatedAirCoolingLoss(DroneEnvironment environment) {
		double groundRecirculation = config.groundEffectMaxThrustBoost() <= 1.0e-6
				? 0.0
				: MathUtil.clamp(
						(environment.groundEffectThrustMultiplier(config) - 1.0) / config.groundEffectMaxThrustBoost(),
						0.0,
						1.0
				);
		double wakeRecirculation = MathUtil.clamp(environment.droneWakeIntensity() / 1.5, 0.0, 1.0);
		double ownWake = MathUtil.clamp(state.propwashWakeIntensity(), 0.0, 1.0);
		double shearLayer = MathUtil.clamp(
				surfaceBoundaryLayerDirtyAir(environment, environment.groundClearanceMeters(), environment.windVelocityWorldMetersPerSecond())
						+ 0.85 * surfaceBoundaryLayerDirtyAir(environment, environment.ceilingClearanceMeters(), environment.windVelocityWorldMetersPerSecond()),
				0.0,
				1.0
		);
		double loss = 0.24 * environment.obstacleProximity()
				+ 0.18 * wakeRecirculation
				+ 0.17 * environment.ceilingEffectIntensity(config)
				+ 0.16 * groundRecirculation
				+ 0.13 * ownWake
				+ 0.10 * shearLayer;
		return MathUtil.clamp(loss, 0.0, 0.44);
	}

	private void updateMotorThermalLimit() {
		state.setMotorThermalLimit(motorThermalLimit(state.maxMotorTemperatureCelsius()));
	}

	private void updateEscThermalLimit() {
		state.setEscThermalLimit(state.minEscThermalLimit());
	}

	private double motorThermalLimit(double maxMotorTemperatureCelsius) {
		if (maxMotorTemperatureCelsius <= config.motorThermalLimitCelsius()) {
			return 1.0;
		}
		if (maxMotorTemperatureCelsius >= config.motorThermalCutoffCelsius()) {
			return MIN_THERMAL_THRUST_LIMIT;
		}

		double t = (maxMotorTemperatureCelsius - config.motorThermalLimitCelsius())
				/ (config.motorThermalCutoffCelsius() - config.motorThermalLimitCelsius());
		double smooth = t * t * (3.0 - 2.0 * t);
		return 1.0 - (1.0 - MIN_THERMAL_THRUST_LIMIT) * smooth;
	}

	private double escThermalLimit(double escTemperatureCelsius) {
		double limitCelsius = Math.max(30.0, config.motorThermalLimitCelsius() - 5.0);
		double cutoffCelsius = Math.max(limitCelsius + 1.0, config.motorThermalCutoffCelsius() - 5.0);
		if (escTemperatureCelsius <= limitCelsius) {
			return 1.0;
		}
		if (escTemperatureCelsius >= cutoffCelsius) {
			return MIN_THERMAL_THRUST_LIMIT;
		}

		double t = (escTemperatureCelsius - limitCelsius) / (cutoffCelsius - limitCelsius);
		double smooth = t * t * (3.0 - 2.0 * t);
		return 1.0 - (1.0 - MIN_THERMAL_THRUST_LIMIT) * smooth;
	}

	private void integrateBatteryThermal(
			double dischargeCurrentAmps,
			double regenerativeCurrentAmps,
			DroneEnvironment environment,
			double dtSeconds
	) {
		if (!batteryThermalInitialized) {
			state.setBatteryTemperatureCelsius(environment.effectiveAmbientTemperatureCelsius());
			state.setBatteryCoolingFactor(1.0);
			state.setBatteryThermalLimit(batteryThermalLimit(environment.effectiveAmbientTemperatureCelsius()));
			batteryThermalInitialized = true;
		}

		double packTemperature = state.batteryTemperatureCelsius();
		double capacityScale = MathUtil.clamp(Math.sqrt(Math.max(0.35, config.batteryCapacityAmpHours())), 0.60, 2.4);
		double maxCurrent = Math.max(1.0, config.maxBatteryCurrentAmps());
		double currentLoad = MathUtil.clamp(dischargeCurrentAmps / maxCurrent, 0.0, 2.0);
		double regenLoad = MathUtil.clamp(regenerativeCurrentAmps / maxCurrent, 0.0, 1.5);
		double rippleLoad = MathUtil.clamp(state.averageMotorCurrentRippleAmps() / Math.max(1.0, maxCurrent / Math.max(1, state.motorCount())), 0.0, 1.8);
		double batteryResistanceOhms = batteryElectricalResistanceOhms(packTemperature, environment.effectiveAmbientTemperatureCelsius(), currentBatteryStateOfCharge())
				* state.batteryPolarizationResistanceScale();
		double resistanceScale = config.batteryInternalResistanceOhms() <= 1.0e-9
				? 0.0
				: MathUtil.clamp(batteryResistanceOhms / config.batteryInternalResistanceOhms(), 0.40, 3.5);
		double heatRate = config.motorThermalRiseCelsiusPerSecond()
				* (0.115 * currentLoad * currentLoad * resistanceScale
						+ 0.020 * regenLoad * regenLoad
						+ 0.018 * rippleLoad * rippleLoad)
				/ capacityScale;
		double coolingFactor = batteryCoolingFactor(environment);
		state.setBatteryCoolingFactor(coolingFactor);
		double coolingRate = config.motorCoolingRatePerSecond()
				* 0.28
				* coolingFactor
				* (packTemperature - environment.effectiveAmbientTemperatureCelsius());
		state.setBatteryTemperatureCelsius(packTemperature + (heatRate - coolingRate) * dtSeconds);
		state.setBatteryThermalLimit(batteryThermalLimit(state.batteryTemperatureCelsius()));
	}

	private double batteryCoolingFactor(DroneEnvironment environment) {
		double airspeedCooling = MathUtil.clamp(state.airspeedMetersPerSecond() / 20.0, 0.0, 1.8);
		double rotorWashCooling = 0.35 * state.averageMotorPower(config);
		double densityFactor = MathUtil.clamp(environment.effectiveAirDensityRatio(), 0.35, 1.35);
		double recirculationEfficiency = 1.0 - 0.58 * recirculatedAirCoolingLoss(environment);
		double localShelterEfficiency = a4mcPackVentilationEfficiency(environment, state.motorCount());
		state.setA4mcPackVentilationEfficiency(localShelterEfficiency);
		double moistAirCooling = environment.moistAirCoolingMultiplier();
		double airCooling = (0.55 + 0.45 * airspeedCooling + rotorWashCooling)
				* densityFactor
				* moistAirCooling
				* recirculationEfficiency
				* localShelterEfficiency;
		double wetCooling = 1.40 * MathUtil.clamp(environment.waterImmersionIntensity(), 0.0, 1.0)
				+ 0.22 * MathUtil.clamp(environment.precipitationWetnessIntensity(), 0.0, 1.0);
		return MathUtil.clamp(airCooling + wetCooling, 0.20, 4.0);
	}

	static double batteryThermalLimit(double batteryTemperatureCelsius) {
		if (batteryTemperatureCelsius <= 58.0) {
			return 1.0;
		}
		if (batteryTemperatureCelsius >= 86.0) {
			return MIN_THERMAL_THRUST_LIMIT;
		}

		double t = (batteryTemperatureCelsius - 58.0) / 28.0;
		double smooth = t * t * (3.0 - 2.0 * t);
		return 1.0 - (1.0 - MIN_THERMAL_THRUST_LIMIT) * smooth;
	}

	private void updateBatteryVoltage(double netCurrentAmps, double regenerativeCurrentAmps, DroneEnvironment environment, double dtSeconds) {
		state.setBatteryCapacityAgingScale(batteryCapacityAgingScale(state.batteryEquivalentCycles()));
		double stateOfCharge = currentBatteryStateOfCharge();
		state.setBatteryStateOfCharge(stateOfCharge);
		double openCircuitVoltage = batteryOpenCircuitVoltageFromStateOfCharge(stateOfCharge);
		double dischargeCurrentAmps = Math.max(0.0, netCurrentAmps);
		state.setBatteryResistanceAgingScale(batteryAgingResistanceScale(state.batteryEquivalentCycles()));
		double polarizationScale = updateBatteryPolarizationResistanceScale(dischargeCurrentAmps, stateOfCharge, dtSeconds);
		double batteryResistanceOhms = batteryElectricalResistanceOhms(state.batteryTemperatureCelsius(), environment.effectiveAmbientTemperatureCelsius(), stateOfCharge)
				* polarizationScale;
		state.setBatteryEffectiveResistanceOhms(batteryResistanceOhms);
		updateBatterySagCurrentTelemetry(batteryResistanceOhms);
		double totalResistanceSag = dischargeCurrentAmps * batteryResistanceOhms;
		double ohmicSag = totalResistanceSag * 0.62;
		double targetTransientSag = totalResistanceSag * 0.38;
		double previousTransientSag = state.batteryTransientSagVoltage();
		double timeConstant = targetTransientSag > previousTransientSag
				? batterySagRiseTimeConstantSeconds()
				: batterySagRecoveryTimeConstantSeconds();
		double alpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, timeConstant);
		double transientSag = previousTransientSag + (targetTransientSag - previousTransientSag) * alpha;
		double slowPolarization = updateBatterySlowPolarizationVoltage(dischargeCurrentAmps, batteryResistanceOhms, stateOfCharge, dtSeconds);
		double targetVoltageSpike = batteryVoltageSpikeTarget(regenerativeCurrentAmps, stateOfCharge, batteryResistanceOhms);
		double previousVoltageSpike = state.batteryVoltageSpike();
		double spikeTimeConstant = targetVoltageSpike > previousVoltageSpike
				? batteryVoltageSpikeRiseTimeConstantSeconds()
				: batteryVoltageSpikeRecoveryTimeConstantSeconds();
		double spikeAlpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, spikeTimeConstant);
		double voltageSpike = previousVoltageSpike + (targetVoltageSpike - previousVoltageSpike) * spikeAlpha;
		double targetBusRipple = batteryBusRippleTarget(dischargeCurrentAmps, batteryResistanceOhms);
		double previousBusRipple = state.batteryBusRippleVoltage();
		double rippleTimeConstant = targetBusRipple > previousBusRipple
				? batteryBusRippleRiseTimeConstantSeconds()
				: batteryBusRippleRecoveryTimeConstantSeconds();
		double rippleAlpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, rippleTimeConstant);
		double busRipple = previousBusRipple + (targetBusRipple - previousBusRipple) * rippleAlpha;
		state.setBatteryOpenCircuitVoltage(openCircuitVoltage);
		state.setBatteryOhmicSagVoltage(ohmicSag);
		state.setBatteryTransientSagVoltage(transientSag);
		state.setBatterySlowPolarizationVoltage(slowPolarization);
		state.setBatteryVoltageSpike(voltageSpike);
		state.setBatteryBusRippleVoltage(busRipple);
		state.setImuSupplyNoiseIntensity(imuSupplyNoiseIntensity(ohmicSag, transientSag, voltageSpike, busRipple));
		double minimumVoltage = config.emptyBatteryVoltage() * 0.85;
		double maximumBusVoltage = config.nominalBatteryVoltage() * 1.12;
		state.setBatteryVoltage(MathUtil.clamp(openCircuitVoltage - ohmicSag - transientSag - slowPolarization + voltageSpike - 0.08 * busRipple, minimumVoltage, maximumBusVoltage));
		double stateOfChargeLimit = batteryStateOfChargePowerLimit(stateOfCharge);
		double currentLimit = updateBatteryCurrentLimit(dischargeCurrentAmps, state.batteryTemperatureCelsius(), dtSeconds);
		state.setBatteryPowerLimit(Math.min(Math.min(stateOfChargeLimit, currentLimit), state.batteryThermalLimit()));
	}

	private void updateBatterySagCurrentTelemetry(double batteryResistanceOhms) {
		double sagCurrentAmps = batteryTwentyPercentSagCurrentAmps(batteryResistanceOhms);
		state.setBatteryTwentyPercentSagCurrentAmps(sagCurrentAmps);
		state.setBatteryTwentyPercentSagCurrentMargin(
				MathUtil.clamp(sagCurrentAmps / Math.max(1.0, config.maxBatteryCurrentAmps()), 0.0, 99.0)
		);
	}

	private double batteryTwentyPercentSagCurrentAmps(double batteryResistanceOhms) {
		if (batteryResistanceOhms <= 1.0e-9) {
			return 9999.0;
		}
		return MathUtil.clamp(
				0.20 * Math.max(0.0, config.nominalBatteryVoltage()) / batteryResistanceOhms,
				0.0,
				9999.0
		);
	}

	private double currentBatteryStateOfCharge() {
		double capacityAmpSeconds = effectiveBatteryCapacityAmpSeconds();
		return MathUtil.clamp(1.0 - state.batteryAmpSecondsConsumed() / capacityAmpSeconds, 0.0, 1.0);
	}

	private double effectiveBatteryCapacityAmpSeconds() {
		return Math.max(
				1.0e-9,
				config.batteryCapacityAmpHours() * 3600.0 * batteryCapacityAgingScale(state.batteryEquivalentCycles())
		);
	}

	private double imuSupplyNoiseIntensity(double ohmicSag, double transientSag, double voltageSpike, double busRipple) {
		double nominalVoltage = Math.max(1.0, config.nominalBatteryVoltage());
		double sagStress = smoothStep(0.018, 0.145, (ohmicSag + transientSag) / nominalVoltage);
		double rippleStress = smoothStep(0.0025, 0.052, busRipple / nominalVoltage);
		double spikeStress = smoothStep(0.0035, 0.070, voltageSpike / nominalVoltage);
		double currentRippleStress = 0.0;
		for (int i = 0; i < state.motorCount(); i++) {
			currentRippleStress += state.motorCurrentRippleAmps(i) * state.motorCurrentRippleAmps(i);
		}
		currentRippleStress = smoothStep(
				0.08,
				0.70,
				Math.sqrt(currentRippleStress) / Math.max(1.0, config.maxBatteryCurrentAmps())
		);
		double highLoadWindow = smoothStep(0.12, 0.82, state.averageEscElectricalOutputCommand());
		return MathUtil.clamp(
				0.52 * sagStress
						+ 0.60 * rippleStress
						+ 0.45 * spikeStress
						+ 0.24 * currentRippleStress * (0.35 + 0.65 * highLoadWindow),
				0.0,
				1.6
		);
	}

	private double batteryOpenCircuitVoltageFromStateOfCharge(double stateOfCharge) {
		double voltageRange = config.nominalBatteryVoltage() - config.emptyBatteryVoltage();
		if (Math.abs(voltageRange) <= 1.0e-9) {
			return config.nominalBatteryVoltage();
		}
		return config.emptyBatteryVoltage() + voltageRange * normalizedLipoOpenCircuitVoltage(stateOfCharge);
	}

	private double updateBatteryPolarizationResistanceScale(double dischargeCurrentAmps, double stateOfCharge, double dtSeconds) {
		double target = batteryPolarizationResistanceTarget(dischargeCurrentAmps, stateOfCharge);
		double previous = state.batteryPolarizationResistanceScale();
		double capacityScale = MathUtil.clamp(config.batteryCapacityAmpHours(), 0.35, 8.0);
		double timeConstant = target > previous
				? MathUtil.clamp(0.090 + 0.018 * capacityScale, 0.080, 0.220)
				: MathUtil.clamp(0.680 + 0.260 * capacityScale, 0.550, 2.200);
		double alpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, timeConstant);
		double scale = previous + (target - previous) * alpha;
		if (scale < 1.0005) {
			scale = 1.0;
		}
		state.setBatteryPolarizationResistanceScale(scale);
		return state.batteryPolarizationResistanceScale();
	}

	private double batteryPolarizationResistanceTarget(double dischargeCurrentAmps, double stateOfCharge) {
		double maxCurrent = Math.max(1.0, config.maxBatteryCurrentAmps());
		double capacityAmpHours = Math.max(0.05, config.batteryCapacityAmpHours());
		double loadFraction = MathUtil.clamp(dischargeCurrentAmps / maxCurrent, 0.0, 1.6);
		double cRate = dischargeCurrentAmps / capacityAmpHours;
		double ratedCRate = maxCurrent / capacityAmpHours;
		double pulseLoad = smoothStep(0.55, 1.05, loadFraction);
		double cRateLoad = smoothStep(0.55 * ratedCRate, 1.05 * ratedCRate, cRate);
		double smallPackPolarization = 0.25 + 0.75 * (1.0 - smoothStep(2.0, 4.5, capacityAmpHours));
		double lowSocStress = 1.0 - smoothStep(0.16, 0.55, stateOfCharge);
		double rippleStress = smoothStep(
				0.08,
				0.58,
				state.averageMotorCurrentRippleAmps() / Math.max(1.0, maxCurrent)
		);
		double targetRise = smallPackPolarization
				* (0.155 * pulseLoad
						+ 0.065 * cRateLoad
						+ 0.030 * lowSocStress * pulseLoad
						+ 0.025 * rippleStress * pulseLoad);
		return MathUtil.clamp(1.0 + targetRise, 1.0, 1.26);
	}

	private double updateBatterySlowPolarizationVoltage(
			double dischargeCurrentAmps,
			double batteryResistanceOhms,
			double stateOfCharge,
			double dtSeconds
	) {
		double target = batterySlowPolarizationTargetVoltage(dischargeCurrentAmps, batteryResistanceOhms, stateOfCharge);
		double previous = state.batterySlowPolarizationVoltage();
		double loadFraction = dischargeCurrentAmps / Math.max(1.0, config.maxBatteryCurrentAmps());
		double timeConstant = target > previous
				? batterySlowPolarizationRiseTimeConstantSeconds(loadFraction)
				: batterySlowPolarizationRecoveryTimeConstantSeconds(stateOfCharge);
		double alpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, timeConstant);
		double voltage = previous + (target - previous) * alpha;
		if (target <= 1.0e-9 && voltage < 0.001) {
			voltage = 0.0;
		}
		return voltage;
	}

	private double batterySlowPolarizationTargetVoltage(double dischargeCurrentAmps, double batteryResistanceOhms, double stateOfCharge) {
		if (dischargeCurrentAmps <= 1.0e-6 || batteryResistanceOhms <= 1.0e-9) {
			return 0.0;
		}
		double totalResistanceSag = dischargeCurrentAmps * batteryResistanceOhms;
		double loadFraction = MathUtil.clamp(dischargeCurrentAmps / Math.max(1.0, config.maxBatteryCurrentAmps()), 0.0, 1.6);
		double loadStress = smoothStep(0.18, 0.92, loadFraction);
		double lowSocStress = 1.0 + 0.45 * (1.0 - smoothStep(0.18, 0.75, stateOfCharge));
		double target = totalResistanceSag * (0.025 + 0.095 * loadStress) * lowSocStress;
		return MathUtil.clamp(target, 0.0, Math.max(0.0, config.nominalBatteryVoltage()) * 0.055);
	}

	private double batterySlowPolarizationRiseTimeConstantSeconds(double loadFraction) {
		double capacityScale = MathUtil.clamp(config.batteryCapacityAmpHours(), 0.35, 8.0);
		double highLoad = smoothStep(0.35, 1.0, loadFraction);
		return MathUtil.clamp(22.0 + 5.0 * capacityScale - 14.0 * highLoad, 12.0, 75.0);
	}

	private static double batterySlowPolarizationRecoveryTimeConstantSeconds(double stateOfCharge) {
		double soc = MathUtil.clamp(stateOfCharge, 0.0, 1.0);
		return interpolateLookup(
				soc,
				LIPO_SLOW_POLARIZATION_SOC_POINTS,
				LIPO_SLOW_POLARIZATION_RECOVERY_TAU_SECONDS
		);
	}

	private static double normalizedLipoOpenCircuitVoltage(double stateOfCharge) {
		double soc = MathUtil.clamp(stateOfCharge, 0.0, 1.0);
		return interpolateLookup(soc, LIPO_OCV_SOC_POINTS, LIPO_OCV_NORMALIZED_POINTS);
	}

	private double batteryBusRippleTarget(double dischargeCurrentAmps, double batteryResistanceOhms) {
		if (batteryResistanceOhms <= 1.0e-9 || state.motorCount() <= 0) {
			return 0.0;
		}

		double rippleCurrentSquared = 0.0;
		for (int i = 0; i < state.motorCount(); i++) {
			double currentRipple = state.motorCurrentRippleAmps(i);
			rippleCurrentSquared += currentRipple * currentRipple;
		}
		double rippleCurrentRms = Math.sqrt(rippleCurrentSquared);
		double loading = MathUtil.clamp(dischargeCurrentAmps / Math.max(1.0, config.maxBatteryCurrentAmps()), 0.0, 1.5);
		double capSmoothing = 1.0 / Math.sqrt(Math.max(0.35, config.batteryCapacityAmpHours()));
		double averageEscElectricalOutput = state.averageEscElectricalOutputCommand();
		double escSwitchingWindow = 0.25 + 0.75 * MathUtil.clamp(averageEscElectricalOutput * (1.0 - averageEscElectricalOutput) * 4.0, 0.0, 1.0);
		double desyncBurst = 1.0 + 0.65 * state.maxEscDesyncIntensity();
		double target = rippleCurrentRms
				* batteryResistanceOhms
				* capSmoothing
				* escSwitchingWindow
				* desyncBurst
				* (0.30 + 0.50 * smoothStep(0.05, 0.95, loading));
		return MathUtil.clamp(target, 0.0, config.nominalBatteryVoltage() * 0.06);
	}

	private double batteryVoltageSpikeTarget(double regenerativeCurrentAmps, double stateOfCharge, double batteryResistanceOhms) {
		if (regenerativeCurrentAmps <= 1.0e-6 || batteryResistanceOhms <= 1.0e-9) {
			return 0.0;
		}

		double fullPackAcceptanceLoss = 0.35 + 1.15 * MathUtil.clamp(stateOfCharge, 0.0, 1.0);
		double brakingAggression = MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0);
		double target = regenerativeCurrentAmps
				* batteryResistanceOhms
				* fullPackAcceptanceLoss
				* (0.75 + 0.45 * brakingAggression);
		return MathUtil.clamp(target, 0.0, config.nominalBatteryVoltage() * 0.12);
	}

	static double batteryTemperatureResistanceScale(double batteryTemperatureCelsius, double ambientTemperatureCelsius) {
		double electricalTemperature = 0.78 * batteryTemperatureCelsius + 0.22 * ambientTemperatureCelsius;
		double coldRise = Math.max(0.0, 25.0 - electricalTemperature);
		double heatRise = Math.max(0.0, electricalTemperature - 45.0);
		double deepColdRise = Math.max(0.0, coldRise - 6.0);
		double scale = 1.0 + 0.024 * coldRise + 0.0030 * deepColdRise * deepColdRise + 0.0045 * heatRise;
		return MathUtil.clamp(scale, 0.72, 2.85);
	}

	private double batteryElectricalResistanceOhms(double batteryTemperatureCelsius, double ambientTemperatureCelsius, double stateOfCharge) {
		double temperatureScale = batteryTemperatureResistanceScale(batteryTemperatureCelsius, ambientTemperatureCelsius);
		double stateOfChargeScale = batteryStateOfChargeResistanceScale(stateOfCharge, state.batteryEquivalentCycles());
		state.setBatteryTemperatureResistanceScale(temperatureScale);
		state.setBatteryStateOfChargeResistanceScale(stateOfChargeScale);
		return config.batteryInternalResistanceOhms()
				* temperatureScale
				* stateOfChargeScale
				* batteryAgingResistanceScale(state.batteryEquivalentCycles());
	}

	private static double batteryStateOfChargeResistanceScale(double stateOfCharge, double equivalentCycles) {
		double soc = MathUtil.clamp(stateOfCharge, 0.0, 1.0);
		double measuredShape = mendeleyR0SocShapeScale(soc, equivalentCycles);
		double reserveKneeRise = 1.0 - smoothStep(0.02, 0.08, soc);
		double deepReserveRise = 1.0 - smoothStep(0.0, 0.025, soc);
		return MathUtil.clamp(measuredShape + 0.38 * reserveKneeRise + 0.35 * deepReserveRise, 1.0, 1.80);
	}

	private static double mendeleyR0SocShapeScale(double stateOfCharge, double equivalentCycles) {
		double soc = MathUtil.clamp(stateOfCharge, LIPO_MENDELEY_R0_SOC_POINTS[0], 1.0);
		double agingFraction = MathUtil.clamp(equivalentCycles / LIPO_MENDELEY_REFERENCE_AGING_CYCLES, 0.0, 1.0);
		double fresh = interpolateLookup(soc, LIPO_MENDELEY_R0_SOC_POINTS, LIPO_MENDELEY_R0_FRESH_SCALE);
		double aged = interpolateLookup(soc, LIPO_MENDELEY_R0_SOC_POINTS, LIPO_MENDELEY_R0_AGED_SCALE);
		double worn = interpolateLookup(soc, LIPO_MENDELEY_R0_SOC_POINTS, LIPO_MENDELEY_R0_WORN_SCALE);
		double highSocFresh = LIPO_MENDELEY_R0_FRESH_SCALE[LIPO_MENDELEY_R0_FRESH_SCALE.length - 1];
		double highSocAged = LIPO_MENDELEY_R0_AGED_SCALE[LIPO_MENDELEY_R0_AGED_SCALE.length - 1];
		double highSocWorn = LIPO_MENDELEY_R0_WORN_SCALE[LIPO_MENDELEY_R0_WORN_SCALE.length - 1];

		if (agingFraction <= 0.55) {
			double t = smoothStep(0.0, 0.55, agingFraction);
			double scale = MathUtil.lerp(fresh, aged, t);
			double highSocScale = MathUtil.lerp(highSocFresh, highSocAged, t);
			return scale / Math.max(1.0e-6, highSocScale);
		}

		double t = smoothStep(0.55, 1.0, agingFraction);
		double scale = MathUtil.lerp(aged, worn, t);
		double highSocScale = MathUtil.lerp(highSocAged, highSocWorn, t);
		return scale / Math.max(1.0e-6, highSocScale);
	}

	private static double interpolateLookup(double value, double[] x, double[] y) {
		if (value <= x[0]) {
			return y[0];
		}
		for (int i = 1; i < x.length; i++) {
			if (value <= x[i]) {
				double t = (value - x[i - 1]) / Math.max(1.0e-9, x[i] - x[i - 1]);
				return MathUtil.lerp(y[i - 1], y[i], t);
			}
		}
		return y[y.length - 1];
	}

	private static double batteryAgingResistanceScale(double equivalentCycles) {
		double cycles = MathUtil.clamp(equivalentCycles, 0.0, LIPO_MENDELEY_REFERENCE_AGING_CYCLES);
		double earlyGrowth = smoothStep(0.0, 0.36 * LIPO_MENDELEY_REFERENCE_AGING_CYCLES, cycles);
		double lifeGrowth = smoothStep(0.18 * LIPO_MENDELEY_REFERENCE_AGING_CYCLES, LIPO_MENDELEY_REFERENCE_AGING_CYCLES, cycles);
		return MathUtil.clamp(1.0 + 0.055 * earlyGrowth + 0.145 * lifeGrowth, 1.0, 1.22);
	}

	private static double batteryCapacityAgingScale(double equivalentCycles) {
		double cycles = MathUtil.clamp(equivalentCycles, 0.0, 5000.0);
		double highCurrentFade = smoothStep(0.05 * LIPO_MENDELEY_REFERENCE_AGING_CYCLES, 400.0, cycles);
		double lateReferenceFade = smoothStep(0.55 * LIPO_MENDELEY_REFERENCE_AGING_CYCLES, LIPO_MENDELEY_REFERENCE_AGING_CYCLES, cycles);
		double extendedFade = smoothStep(
				LIPO_MENDELEY_REFERENCE_AGING_CYCLES,
				3.0 * LIPO_MENDELEY_REFERENCE_AGING_CYCLES,
				cycles
		);
		return MathUtil.clamp(1.0 - 0.170 * highCurrentFade - 0.030 * lateReferenceFade - 0.150 * extendedFade, 0.65, 1.0);
	}

	private double batterySagRiseTimeConstantSeconds() {
		return MathUtil.clamp(0.030 + 0.018 * config.batteryCapacityAmpHours(), 0.030, 0.180);
	}

	private double batterySagRecoveryTimeConstantSeconds() {
		return MathUtil.clamp(0.420 + 0.220 * config.batteryCapacityAmpHours(), 0.350, 2.500);
	}

	private double batteryVoltageSpikeRiseTimeConstantSeconds() {
		return MathUtil.clamp(0.010 + 0.003 * config.batteryCapacityAmpHours(), 0.010, 0.035);
	}

	private double batteryVoltageSpikeRecoveryTimeConstantSeconds() {
		return MathUtil.clamp(0.090 + 0.035 * config.batteryCapacityAmpHours(), 0.080, 0.280);
	}

	private double batteryBusRippleRiseTimeConstantSeconds() {
		return MathUtil.clamp(0.004 + 0.0015 * config.batteryCapacityAmpHours(), 0.004, 0.018);
	}

	private double batteryBusRippleRecoveryTimeConstantSeconds() {
		return MathUtil.clamp(0.026 + 0.012 * config.batteryCapacityAmpHours(), 0.020, 0.090);
	}

	private static boolean batteryModelChanged(DroneConfig previousConfig, DroneConfig config) {
		return previousConfig.nominalBatteryVoltage() != config.nominalBatteryVoltage()
				|| previousConfig.emptyBatteryVoltage() != config.emptyBatteryVoltage()
				|| previousConfig.batteryInternalResistanceOhms() != config.batteryInternalResistanceOhms()
				|| previousConfig.batteryCapacityAmpHours() != config.batteryCapacityAmpHours();
	}

	private static boolean flightControllerSensorModelChanged(DroneConfig previousConfig, DroneConfig config) {
		return previousConfig.gyroLowPassCutoffHz() != config.gyroLowPassCutoffHz()
				|| previousConfig.gyroNoiseStdDevRadiansPerSecond() != config.gyroNoiseStdDevRadiansPerSecond()
				|| previousConfig.accelerometerLowPassCutoffHz() != config.accelerometerLowPassCutoffHz()
				|| previousConfig.accelerometerNoiseStdDevMetersPerSecondSquared() != config.accelerometerNoiseStdDevMetersPerSecondSquared()
				|| !previousConfig.imuOffsetBodyMeters().equals(config.imuOffsetBodyMeters())
				|| previousConfig.controlLatencySeconds() != config.controlLatencySeconds();
	}

	private static boolean controlLinkModelChanged(DroneConfig previousConfig, DroneConfig config) {
		return previousConfig.rcCommandSmoothingTimeConstantSeconds() != config.rcCommandSmoothingTimeConstantSeconds()
				|| previousConfig.rcCommandLatencySeconds() != config.rcCommandLatencySeconds()
				|| previousConfig.rcFailsafeTimeoutSeconds() != config.rcFailsafeTimeoutSeconds()
				|| previousConfig.rcFrameRateHertz() != config.rcFrameRateHertz()
				|| previousConfig.rcChannelResolutionSteps() != config.rcChannelResolutionSteps();
	}

	private static boolean escCommandSignalModelChanged(DroneConfig previousConfig, DroneConfig config) {
		return previousConfig.escCommandFrameRateHertz() != config.escCommandFrameRateHertz()
				|| previousConfig.escCommandResolutionSteps() != config.escCommandResolutionSteps();
	}

	private double updateBatteryCurrentLimit(double currentAmps, double batteryTemperatureCelsius, double dtSeconds) {
		double target = batteryCurrentPowerLimit(currentAmps, batteryTemperatureCelsius);
		double previous = state.batteryCurrentLimit();
		double timeConstant = target < previous ? batteryCurrentLimitAttackTimeConstantSeconds() : batteryCurrentLimitRecoveryTimeConstantSeconds();
		double alpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, timeConstant);
		double limit = previous + (target - previous) * alpha;
		state.setBatteryCurrentLimit(limit);
		return state.batteryCurrentLimit();
	}

	private double batteryCurrentPowerLimit(double currentAmps, double batteryTemperatureCelsius) {
		double maxCurrent = Math.max(1.0, config.maxBatteryCurrentAmps() * temperatureAdjustedBatteryCurrentScale(batteryTemperatureCelsius));
		double ratio = currentAmps / maxCurrent;
		if (ratio <= 0.98) {
			return 1.0;
		}
		if (ratio >= 1.65) {
			return 0.38;
		}
		double t = (ratio - 0.98) / 0.67;
		double smooth = t * t * (3.0 - 2.0 * t);
		return 1.0 - 0.62 * smooth;
	}

	static double temperatureAdjustedBatteryCurrentScale(double batteryTemperatureCelsius) {
		double coldLoss = Math.max(0.0, 25.0 - batteryTemperatureCelsius) * 0.011;
		double heatLoss = Math.max(0.0, batteryTemperatureCelsius - 42.0) * 0.006;
		return MathUtil.clamp(1.0 - coldLoss - heatLoss, 0.52, 1.0);
	}

	private double batteryCurrentLimitAttackTimeConstantSeconds() {
		return MathUtil.clamp(0.028 + 0.010 * config.batteryCapacityAmpHours(), 0.020, 0.090);
	}

	private double batteryCurrentLimitRecoveryTimeConstantSeconds() {
		return MathUtil.clamp(0.320 + 0.160 * config.batteryCapacityAmpHours(), 0.250, 1.400);
	}

	private static double batteryStateOfChargePowerLimit(double stateOfCharge) {
		if (stateOfCharge >= 0.18) {
			return 1.0;
		}
		if (stateOfCharge <= 0.04) {
			return 0.35;
		}
		double t = (stateOfCharge - 0.04) / 0.14;
		return 0.35 + 0.65 * t * t * (3.0 - 2.0 * t);
	}
}
