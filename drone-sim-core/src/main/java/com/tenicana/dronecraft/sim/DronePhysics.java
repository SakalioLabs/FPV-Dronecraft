package com.tenicana.dronecraft.sim;

import java.util.Arrays;
import java.util.List;

public final class DronePhysics {
	private static final double MOTOR_AMBIENT_TEMPERATURE_CELSIUS = 25.0;
	private static final double AVIONICS_CURRENT_AMPS = 1.2;
	private static final double MIN_THERMAL_THRUST_LIMIT = 0.45;
	private static final double MOTOR_STALL_CURRENT_SCALE = 3.20;
	private static final double MOTOR_NO_LOAD_OMEGA_SCALE = 1.35;
	private static final double MOTOR_OUTRUNNER_POLE_PAIRS = 7.0;
	private static final double ROTOR_ARM_FLEX_TILT_RADIANS = Math.toRadians(4.0);
	private static final double ROTOR_ARM_FLEX_VERTICAL_DEFLECTION_SCALE = 0.055;
	private static final double ROTOR_WINDMILL_MAX_OMEGA_FRACTION = 0.32;
	private static final double MOTOR_STATIC_BREAKAWAY_TORQUE_NEWTON_METERS = 0.030;
	private static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	private static final double BAROMETER_ALTITUDE_TIME_CONSTANT_SECONDS = 0.090;
	private static final double BAROMETER_VERTICAL_SPEED_TIME_CONSTANT_SECONDS = 0.180;
	private static final double GYRO_FULL_SCALE_RADIANS_PER_SECOND = Math.toRadians(2000.0);
	private static final double ACCELEROMETER_FULL_SCALE_METERS_PER_SECOND_SQUARED = 16.0 * 9.80665;
	private static final int GYRO_DELAY_BUFFER_SIZE = 256;
	private static final int CONTROL_DELAY_BUFFER_SIZE = 256;
	private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 BODY_ROTOR_AXIS = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 BODY_RIGHT = new Vec3(1.0, 0.0, 0.0);
	private static final Vec3 BODY_FORWARD = new Vec3(0.0, 0.0, 1.0);
	private DroneConfig config;
	private final DroneState state;
	private final PidController pitchPid;
	private final PidController yawPid;
	private final PidController rollPid;
	private final double[] targetRotorThrusts;
	private final double[] escDesyncPhases;
	private final double[] motorCommutationPhases;
	private final double[] heldEscOutputCommands;
	private final double[] escCommandFrameClockSeconds;
	private final double[] escCommandFrameAgeSeconds;
	private final double[] escCommandErrors;
	private final boolean[] escCommandFrameInitialized;
	private final double[] rotorArmFlexIntensity;
	private final Vec3[] rotorFlappingTiltBody;
	private final Vec3[] previousRotorForceBodyNewtons;
	private final Vec3[] previousRotorTorqueBodyNewtonMeters;
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
	private double gyroRotorVibrationPhase;
	private double gyroBladePassVibrationPhase;
	private double accelerometerNoiseTimeSeconds;
	private double barometerNoiseTimeSeconds;
	private double barometerFilteredAltitudeMeters;
	private double barometerFilteredVerticalSpeedMetersPerSecond;
	private boolean barometerInitialized;
	private Vec3 gyroBiasBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 accelerometerBiasBodyMetersPerSecondSquared = Vec3.ZERO;
	private double sensorBiasTimeSeconds;
	private double controlLinkLossSeconds;
	private double propwashPhaseA;
	private double propwashPhaseB;
	private double turbulencePhaseA;
	private double turbulencePhaseB;
	private double turbulencePhaseC;
	private double windGustPhaseA;
	private double windGustPhaseB;
	private double windGustPhaseC;
	private Vec3 meanWindVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 windGustVelocityWorldMetersPerSecond = Vec3.ZERO;
	private boolean windModelInitialized;
	private boolean batteryThermalInitialized;
	private Vec3 previousTargetRatesRadiansPerSecond = Vec3.ZERO;
	private Vec3 previousPidGyroRatesBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 feedForwardTorqueBody = Vec3.ZERO;
	private boolean hasPreviousTargetRates;
	private boolean hasPreviousPidGyroRates;
	private double previousThrottle;
	private double antiGravityTransient;

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

	public DronePhysics(DroneConfig config) {
		this.config = config;
		this.state = new DroneState(config.rotors().size());
		this.state.setBatteryVoltage(config.nominalBatteryVoltage());
		this.state.setBatteryOpenCircuitVoltage(config.nominalBatteryVoltage());
		this.pitchPid = new PidController(config.pitchGains());
		this.yawPid = new PidController(config.yawGains());
		this.rollPid = new PidController(config.rollGains());
		this.targetRotorThrusts = new double[config.rotors().size()];
		this.escDesyncPhases = new double[config.rotors().size()];
		this.motorCommutationPhases = new double[config.rotors().size()];
		this.heldEscOutputCommands = new double[config.rotors().size()];
		this.escCommandFrameClockSeconds = new double[config.rotors().size()];
		this.escCommandFrameAgeSeconds = new double[config.rotors().size()];
		this.escCommandErrors = new double[config.rotors().size()];
		this.escCommandFrameInitialized = new boolean[config.rotors().size()];
		this.rotorArmFlexIntensity = new double[config.rotors().size()];
		this.rotorFlappingTiltBody = new Vec3[config.rotors().size()];
		this.previousRotorForceBodyNewtons = new Vec3[config.rotors().size()];
		this.previousRotorTorqueBodyNewtonMeters = new Vec3[config.rotors().size()];
		Arrays.fill(this.rotorFlappingTiltBody, Vec3.ZERO);
		Arrays.fill(this.previousRotorForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(this.previousRotorTorqueBodyNewtonMeters, Vec3.ZERO);
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
			double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0;
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

	public void resetControlLoops() {
		pitchPid.reset();
		yawPid.reset();
		rollPid.reset();
		Arrays.fill(targetRotorThrusts, 0.0);
		Arrays.fill(escDesyncPhases, 0.0);
		Arrays.fill(motorCommutationPhases, 0.0);
		Arrays.fill(heldEscOutputCommands, 0.0);
		Arrays.fill(escCommandFrameClockSeconds, 0.0);
		Arrays.fill(escCommandFrameAgeSeconds, 0.0);
		Arrays.fill(escCommandErrors, 0.0);
		Arrays.fill(escCommandFrameInitialized, false);
		Arrays.fill(rotorArmFlexIntensity, 0.0);
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
		state.setRotorWashDragForceBodyNewtons(Vec3.ZERO);
		state.setAirframeAerodynamicTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setAirframeAngularDragTorqueBodyNewtonMeters(Vec3.ZERO);
		state.setRotorInertiaTorqueBodyNewtonMeters(Vec3.ZERO);
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
		double airDensity = environment.airDensityRatio();
		double waterImmersion = environment.waterImmersionIntensity();
		double precipitationWetness = environment.precipitationWetnessIntensity();
		Vec3 effectiveWindVelocityWorld = updateAirMassWind(environment, dtSeconds);
		Vec3 relativeAirVelocityBody = state.orientation()
				.conjugate()
				.rotate(state.velocityMetersPerSecond().subtract(effectiveWindVelocityWorld));
		updateAerodynamicTelemetry(relativeAirVelocityBody);
		Vec3 angularVelocityBody = state.angularVelocityBodyRadiansPerSecond();
		RotorWakeInterference rotorWakeInterference = calculateRotorWakeInterference(input.armed(), relativeAirVelocityBody);
		double vortexRingStateSum = 0.0;
		double rotorVibrationSum = 0.0;
		double rotorInflowSkewSum = 0.0;
		Vec3 rotorInflowSkewTorqueSum = Vec3.ZERO;
		Vec3 rotorInertiaTorqueSum = Vec3.ZERO;
		Vec3 rotorAngularDragTorqueSum = Vec3.ZERO;
		Vec3 rotorWallEffectForceSum = Vec3.ZERO;

		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double rotorWaterImmersion = environment.rotorWaterImmersion(i);
			double rotorWaterLoad = rotorWaterLoadFactor(rotorWaterImmersion);
			double rotorPrecipitationLoad = rotorPrecipitationLoadFactor(precipitationWetness);
			double escOutput;
			if (input.armed()) {
				escOutput = updateEscOutputCommand(i, rotor, voltageScale, dtSeconds);
			} else {
				resetEscSignalOutput(i);
				escOutput = 0.0;
			}
			state.setEscOutputCommand(i, escOutput);
			double surfaceScrape = state.rotorSurfaceScrapeIntensity(i);
			double powerLimitScale = Math.sqrt(state.batteryPowerLimit() * state.motorThermalLimit() * state.escThermalLimit() * state.rotorHealth(i));
			double targetOmega = input.armed()
					? rotor.maxOmegaRadiansPerSecond()
							* escOutput
							* voltageScale
							* powerLimitScale
							* motorLoadTargetScale(state.rotorAerodynamicLoadFactor(i) + 0.70 * surfaceScrape + rotorWaterLoad + rotorPrecipitationLoad, escOutput)
							* rotorSurfaceScrapeTargetScale(surfaceScrape)
					: 0.0;
			state.setMotorTargetOmegaRadiansPerSecond(i, targetOmega);
			double previousOmega = state.motorOmegaRadiansPerSecond(i);
			double motorAlpha = MathUtil.expSmoothing(dtSeconds, motorResponseTimeConstantSeconds(
					rotor,
					previousOmega,
					targetOmega,
					escOutput,
					voltageScale,
					powerLimitScale,
					state.rotorAerodynamicLoadFactor(i) + 0.85 * surfaceScrape + rotorWaterLoad + rotorPrecipitationLoad
			));
			double commandedOmega = previousOmega + (targetOmega - previousOmega) * motorAlpha;
			commandedOmega = electricallyLimitedMotorOmega(
					i,
					rotor,
					previousOmega,
					commandedOmega,
					escOutput,
					powerLimitScale,
					state.rotorAerodynamicLoadFactor(i) + 0.85 * surfaceScrape + rotorWaterLoad + rotorPrecipitationLoad,
					surfaceScrape,
					dtSeconds
			);
			double mechanicalLossTorque = motorMechanicalLossTorque(
					rotor,
					commandedOmega,
					airDensity,
					rotorWaterImmersion,
					precipitationWetness,
					surfaceScrape
			);
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
			Vec3 rotorRelativeAirVelocityBody = relativeAirVelocityBody
					.add(angularVelocityBody.cross(rotorArmBody))
					.add(rotorWakeInterference.downwashVelocityBodyMetersPerSecond(i))
					.add(wakeSwirlVelocityBody);
			commandedOmega = applyRotorWindmilling(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					commandedOmega,
					escOutput,
					dtSeconds
			);
			double advanceRatio = rotorAdvanceRatio(aerodynamicRotor, rotorRelativeAirVelocityBody, commandedOmega);
			state.setRotorAdvanceRatio(i, advanceRatio);
			double kinematicRotorStall = rotorBladeStallIntensity(aerodynamicRotor, rotorRelativeAirVelocityBody, commandedOmega);
			double rotorStall = kinematicRotorStall;
			double desyncIntensity = updateEscDesyncIntensity(
					i,
					rotor,
					environment,
					rotorStall,
					previousOmega,
					targetOmega,
					escOutput,
					voltageScale,
					surfaceScrape,
					dtSeconds
			);
			double desyncPulse = escDesyncPulse(i, commandedOmega, desyncIntensity, dtSeconds);
			double driveVoltage = motorDriveVoltage(escOutput, powerLimitScale);
			double commandedVoltageHeadroom = motorVoltageHeadroomFromDriveVoltage(rotor, commandedOmega, driveVoltage);
			MotorCommutationRipple commutationRipple = updateMotorCommutationRipple(
					i,
					rotor,
					commandedOmega,
					escOutput,
					commandedVoltageHeadroom,
					state.rotorAerodynamicLoadFactor(i) + 0.85 * surfaceScrape + rotorWaterLoad + rotorPrecipitationLoad,
					desyncIntensity,
					surfaceScrape,
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
			double rotorTipMach = rotorTipMach(aerodynamicRotor, rotorRelativeAirVelocityBody, omega, environment.ambientTemperatureCelsius());
			state.setRotorTipMach(i, rotorTipMach);
			double compressibilityThrustScale = rotorCompressibilityThrustScale(rotorTipMach);
			double compressibilityLoad = rotorCompressibilityLoadFactor(rotorTipMach);
			double thrustScale = airDensity
					* environment.rotorThrustMultiplier(i, config)
					* rotorWakeInterferenceThrustScale(wakeInterference)
					* waterImmersionThrustScale(rotorWaterImmersion)
					* precipitationThrustScale(precipitationWetness)
					* rotorHealthThrustScale(state.rotorHealth(i));
			double baseThrust = rotor.thrustCoefficient() * omega * omega * thrustScale;
			double inflowLagScale = updateRotorInducedInflow(i, rotor, rotorRelativeAirVelocityBody, omega, baseThrust, airDensity, dtSeconds);
			double rotorAirflowScale = rotorAirflowThrustMultiplier(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					omega,
					state.rotorTranslationalLiftIntensity(i)
			);
			BladeElementAerodynamics bladeElement = rotorBladeElementAerodynamics(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					omega,
					state.rotorInducedVelocityMetersPerSecond(i)
			);
			BladeDissymmetryAerodynamics bladeDissymmetry = rotorBladeDissymmetryAerodynamics(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					omega,
					baseThrust
			);
			rotorStall = kinematicRotorStall;
			state.setRotorBladeAngleOfAttackRadians(i, bladeElement.angleOfAttackRadians());
			state.setRotorBladeElementStallIntensity(i, bladeElement.stallIntensity());
			state.setRotorBladeDissymmetryIntensity(i, bladeDissymmetry.intensity());
			state.setRotorStallIntensity(i, rotorStall);
			rotorVibrationSum += rotorDamageVibration(rotor, omega, state.rotorHealth(i))
					+ rotorStallVibration(rotor, omega, rotorStall)
					+ bladeElement.vibration()
					+ bladeDissymmetry.vibration()
					+ rotorFlowObstructionVibration(rotor, omega, environment.rotorFlowObstruction(i))
					+ rotorSurfaceScrapeVibration(rotor, omega, surfaceScrape)
					+ rotorWakeInterferenceVibration(rotor, omega, wakeInterference)
					+ rotorWakeSwirlVibration(rotor, omega, wakeSwirlSpeed)
					+ rotorWaterIngestionVibration(rotor, omega, rotorWaterImmersion)
					+ rotorPrecipitationVibration(rotor, omega, precipitationWetness)
					+ rotorCompressibilityVibration(rotor, omega, rotorTipMach)
					+ rotorImbalanceVibration(rotor, omega)
					+ rotorWindmillingVibration(aerodynamicRotor, rotorRelativeAirVelocityBody, omega, escOutput)
					+ motorCommutationRippleVibration(rotor, omega, commutationRipple.intensity(), commutationRipple.torqueRippleNewtonMeters());
			double vortexRingState = rotorVortexRingStateIntensity(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					omega,
					state.rotorInducedVelocityMetersPerSecond(i)
			);
			vortexRingStateSum += vortexRingState;
			double aerodynamicLoadFactor = MathUtil.clamp(rotorAerodynamicLoadFactor(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					omega,
					state.rotorInducedVelocityMetersPerSecond(i),
					state.rotorTranslationalLiftIntensity(i),
					rotorStall,
					vortexRingState,
					environment.rotorFlowObstruction(i),
					surfaceScrape
			)
					+ rotorAngularDragLoadFactor(aerodynamicRotor, angularVelocityBody, omega)
					+ 0.28 * wakeInterference
					+ rotorWakeSwirlLoadFactor(rotor, omega, wakeSwirlSpeed)
					+ rotorWindmillingLoadFactor(aerodynamicRotor, rotorRelativeAirVelocityBody, omega, escOutput)
					+ compressibilityLoad
					+ bladeElement.loadFactor()
					+ bladeDissymmetry.loadFactor()
					+ rotorWaterLoad
					+ rotorPrecipitationLoad, 0.0, 2.0);
			state.setRotorAerodynamicLoadFactor(i, aerodynamicLoadFactor);
			double vortexRingThrustScale = 1.0 - rotor.axialFlowThrustLossCoefficient() * 1.35 * vortexRingState;
			double stallThrustScale = 1.0 - rotor.stallThrustLossCoefficient() * rotorStall;
			double thrust = baseThrust
					* rotorAirflowScale
					* inflowLagScale
					* bladeElement.thrustScale()
					* bladeDissymmetry.thrustScale()
					* compressibilityThrustScale
					* MathUtil.clamp(vortexRingThrustScale, 0.45, 1.0)
					* MathUtil.clamp(stallThrustScale, 0.35, 1.0);
			state.setRotorThrustNewtons(i, thrust);
			Vec3 forceBody = aerodynamicRotor.thrustAxisBody().multiply(thrust);
			Vec3 flappingForceBody = updateRotorFlappingForce(i, aerodynamicRotor, rotorRelativeAirVelocityBody, omega, thrust, dtSeconds);
			state.setRotorFlappingForceNewtons(i, Math.hypot(flappingForceBody.x(), flappingForceBody.z()));
			Vec3 thrustAxisForceBody = forceBody.add(flappingForceBody);
			Vec3 rotorDiskAxisBody = rotorDiskAxisBody(thrustAxisForceBody);
			Vec3 diskDragBody = rotorDiskDragForce(aerodynamicRotor, rotorRelativeAirVelocityBody, omega, airDensity);
			Vec3 windmillingDragBody = rotorWindmillingDragForce(aerodynamicRotor, rotorRelativeAirVelocityBody, omega, escOutput, airDensity);
			Vec3 wallEffectForceBody = rotorWallEffectForce(
					aerodynamicRotor,
					rotorRelativeAirVelocityBody,
					omega,
					thrust,
					environment.rotorFlowObstruction(i),
					environment.rotorFlowObstructionDirectionBody(i)
			);
			rotorWallEffectForceSum = rotorWallEffectForceSum.add(wallEffectForceBody);
			forceBody = thrustAxisForceBody.add(diskDragBody).add(windmillingDragBody).add(wallEffectForceBody);
			state.setRotorForceBodyNewtons(i, forceBody);
			Vec3 torqueFromArm = rotorArmBody.cross(forceBody);
			double reactionTorqueScale = rotorReactionTorqueScale(aerodynamicLoadFactor, rotorStall, vortexRingState);
			double motorAerodynamicTorque = rotor.yawTorquePerThrustMeter() * thrust * reactionTorqueScale;
			state.setMotorAerodynamicTorqueNewtonMeters(i, motorAerodynamicTorque);
			state.setMotorShaftPowerWatts(
					i,
					motorAerodynamicTorque * Math.max(0.0, omega)
							+ mechanicalLossTorque * Math.max(0.0, omega)
							+ motorPositiveInertiaPowerWatts(rotor, motorAngularAcceleration, omega)
			);
			double reactionTorqueNewtonMeters = motorAerodynamicTorque + commutationRipple.torqueRippleNewtonMeters();
			Vec3 reactionTorque = rotorDiskAxisBody.multiply(rotor.spinDirection() * reactionTorqueNewtonMeters);
			Vec3 inertiaTorque = rotorInertiaTorque(rotor, previousOmega, omega, angularVelocityBody, rotorDiskAxisBody, dtSeconds);
			rotorInertiaTorqueSum = rotorInertiaTorqueSum.add(inertiaTorque);
			Vec3 angularDragTorque = rotorAngularDragTorque(
					aerodynamicRotor,
					angularVelocityBody,
					rotorDiskAxisBody,
					omega,
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
					omega,
					state.rotorTranslationalLiftIntensity(i),
					rotorStall
			);
			Vec3 inflowSkewTorque = rotorInflowSkewTorque(aerodynamicRotor, rotorRelativeAirVelocityBody, thrust, inflowSkewIntensity);
			rotorInflowSkewSum += inflowSkewIntensity;
			rotorInflowSkewTorqueSum = rotorInflowSkewTorqueSum.add(inflowSkewTorque);

			Vec3 rotorTorqueBody = torqueFromArm
					.add(reactionTorque)
					.add(inertiaTorque)
					.add(angularDragTorque)
					.add(inflowSkewTorque);
			state.setRotorTorqueBodyNewtonMeters(i, rotorTorqueBody);
			double rotorArmFlex = updateRotorArmFlexIntensity(i, rotor, forceBody, rotorTorqueBody, omega, dtSeconds);
			state.setRotorArmFlexIntensity(i, rotorArmFlex);
			rotorVibrationSum += rotorArmFlexVibration(rotor, omega, rotorArmFlex);
			totalForceBody = totalForceBody.add(forceBody);
			totalTorqueBody = totalTorqueBody.add(rotorTorqueBody);
			state.setRotorSurfaceScrapeIntensity(i, surfaceScrapeDecay(surfaceScrape, dtSeconds));
		}
		state.setVortexRingStateIntensity(vortexRingStateSum / config.rotors().size());
		state.setRotorVibration(rotorVibrationSum / config.rotors().size());
		state.setRotorInflowSkewIntensity(rotorInflowSkewSum / config.rotors().size());
		state.setRotorInflowSkewTorqueBodyNewtonMeters(rotorInflowSkewTorqueSum);
		state.setRotorInertiaTorqueBodyNewtonMeters(rotorInertiaTorqueSum);
		state.setRotorAngularDragTorqueBodyNewtonMeters(rotorAngularDragTorqueSum);
		state.setRotorWallEffectForceBodyNewtons(rotorWallEffectForceSum);
		updateEscSignalTelemetry();

		Vec3 airframeTorqueBody = calculateAirframeAerodynamicTorque(relativeAirVelocityBody, airDensity);
		state.setAirframeAerodynamicTorqueBodyNewtonMeters(airframeTorqueBody);
		Vec3 turbulenceTorqueBody = calculateWindTurbulenceTorque(environment, relativeAirVelocityBody, dtSeconds);
		state.setWindTurbulenceTorqueBodyNewtonMeters(turbulenceTorqueBody);
		totalTorqueBody = totalTorqueBody
				.add(airframeTorqueBody)
				.add(calculatePropwashTorque(input, relativeAirVelocityBody, angularVelocityBody, dtSeconds))
				.add(turbulenceTorqueBody);
		integrateLinear(totalForceBody, environment, effectiveWindVelocityWorld, dtSeconds);
		updateBarometerMeasurement(environment, dtSeconds);
		updateAccelerometerMeasurement(dtSeconds);
		integrateAngular(totalTorqueBody, relativeAirVelocityBody, airDensity, dtSeconds);
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

		escCommandFrameClockSeconds[index] += Math.max(0.0, dtSeconds);
		boolean firstFrame = !escCommandFrameInitialized[index];
		if (firstFrame || escCommandFrameClockSeconds[index] >= intervalSeconds) {
			heldEscOutputCommands[index] = quantizedOutput;
			escCommandFrameInitialized[index] = true;
			escCommandFrameClockSeconds[index] = firstFrame ? 0.0 : escCommandFrameClockSeconds[index] - intervalSeconds;
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

	private void resetEscSignalOutput(int index) {
		heldEscOutputCommands[index] = 0.0;
		escCommandFrameClockSeconds[index] = 0.0;
		escCommandFrameAgeSeconds[index] = 0.0;
		escCommandErrors[index] = 0.0;
		escCommandFrameInitialized[index] = false;
	}

	private double quantizeEscCommand(double command) {
		int resolutionSteps = config.escCommandResolutionSteps();
		if (resolutionSteps < 2) {
			return MathUtil.clamp(command, 0.0, 1.0);
		}
		double scale = Math.max(1.0, resolutionSteps - 1.0);
		return Math.round(MathUtil.clamp(command, 0.0, 1.0) * scale) / scale;
	}

	private double escCommandFrameIntervalSeconds() {
		return config.escCommandFrameRateHertz() <= 1.0e-9 ? 0.0 : 1.0 / config.escCommandFrameRateHertz();
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
			double authorityFactor = 1.0 / MathUtil.clamp(voltageAuthority * powerAuthority * escAuthority, 0.30, 1.08);
			return MathUtil.clamp(
					baseTimeConstant * inertiaFactor * loadDragFactor * authorityFactor * backEmfFactor,
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
				+ 0.14 * thermalStress
				+ 0.16 * outputStress
				- 0.42;
		double targetIntensity = MathUtil.clamp(risk * 1.45, 0.0, 1.0) * smoothStep(0.12, 0.38, escOutput);
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
		double windingResistanceOhms = inferredMotorWindingResistanceOhms();
		double torqueConstant = motorTorqueConstantNewtonMetersPerAmp(rotor);
		double loadTorque = motorLoadTorqueEstimate(index, rotor, previousOmega, aerodynamicLoadFactor, surfaceScrapeIntensity);
		double limitedAcceleration;
		if (requestedAcceleration > 0.0) {
			double phaseCurrent = Math.max(0.0, (driveVoltage - backEmfVoltage) / windingResistanceOhms);
			double availableTorque = phaseCurrent * torqueConstant;
			double breakawayTorque = motorStaticBreakawayTorque(rotor, previousOmega, escOutput, surfaceScrapeIntensity);
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
		return MathUtil.clamp(
				MOTOR_STATIC_BREAKAWAY_TORQUE_NEWTON_METERS
						* radiusScale
						* inertiaScale
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
			double waterImmersion,
			double precipitationWetness,
			double surfaceScrapeIntensity
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.25);
		if (spinRatio <= 1.0e-6) {
			return 0.0;
		}

		double inertiaScale = MathUtil.clamp(rotor.rotorInertiaKgMetersSquared() / 1.6e-5, 0.25, 8.0);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.35, 3.0);
		double diskDragScale = MathUtil.clamp(rotor.diskDragCoefficient() / 0.0028, 0.25, 5.0);
		double bearingTorque = (0.00011 * Math.sqrt(radiusScale) + 0.00014 * Math.sqrt(inertiaScale))
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
		double imbalanceTorque = 0.0045 * rotor.imbalanceIntensity() * spinRatio * spinRatio;
		return MathUtil.clamp(bearingTorque + windageTorque + wetPropTorque + rainTorque + scrapeTorque + imbalanceTorque, 0.0, 0.050);
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
		double activeSpin = smoothStep(0.025, 0.18, spinRatio) * smoothStep(0.04, 0.16, output);
		double intensity = activeSpin * MathUtil.clamp(
				0.006
						+ 0.030 * dutyRipple
						+ 0.030 * loadStress
						+ 0.036 * headroomStress
						+ 0.090 * MathUtil.clamp(desyncIntensity, 0.0, 1.0)
						+ 0.050 * MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0)
						+ 0.120 * rotor.imbalanceIntensity() * spinRatio
						+ 0.026 * lowSpeedLoad,
				0.0,
				0.28
		);
		if (intensity <= 1.0e-7) {
			return new MotorCommutationRipple(0.0, 0.0, 0.0);
		}

		motorCommutationPhases[index] = normalizeRadians(
				motorCommutationPhases[index] + Math.abs(omegaRadiansPerSecond) * MOTOR_OUTRUNNER_POLE_PAIRS * dtSeconds
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

	private static Vec3 rotorInertiaTorque(
			RotorSpec rotor,
			double previousOmega,
			double omega,
			Vec3 bodyAngularVelocity,
			Vec3 rotorDiskAxisBody,
			double dtSeconds
	) {
		if (rotor.rotorInertiaKgMetersSquared() <= 0.0 || dtSeconds <= 0.0) {
			return Vec3.ZERO;
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
		return accelerationReactionTorque.add(gyroscopicReactionTorque);
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
		return 1.0 - smoothStep(0.035, 0.22, MathUtil.clamp(escOutput, 0.0, 1.0));
	}

	private static Vec3 rotorWallEffectForce(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double obstruction,
			Vec3 obstructionDirectionBody
	) {
		obstruction = MathUtil.clamp(obstruction, 0.0, 1.0);
		if (obstruction <= 1.0e-6 || thrustNewtons <= 1.0e-6 || obstructionDirectionBody == null) {
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
				* speedWashout;
		return lateralDirection.multiply(-forceMagnitude).clamp(-4.0, 4.0);
	}

	private Vec3 calculatePropwashTorque(
			DroneInput input,
			Vec3 relativeAirVelocityBody,
			Vec3 angularVelocityBody,
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
		double targetWake = input.armed()
				? MathUtil.clamp(descentFactor * wakeRetention * (0.25 + 0.75 * motorPower), 0.0, 1.0)
				: 0.0;
		double wakeIntensity = updatePropwashWakeIntensity(targetWake, wakeRetention, dtSeconds);
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

	private double updatePropwashWakeIntensity(double targetWakeIntensity, double wakeRetention, double dtSeconds) {
		double previousWakeIntensity = state.propwashWakeIntensity();
		double flushFactor = 1.0 - MathUtil.clamp(wakeRetention, 0.0, 1.0);
		double timeConstant = targetWakeIntensity > previousWakeIntensity
				? 0.055
				: MathUtil.clamp(0.130 - 0.090 * flushFactor, 0.040, 0.130);
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double wakeIntensity = previousWakeIntensity + (targetWakeIntensity - previousWakeIntensity) * alpha;
		state.setPropwashWakeIntensity(wakeIntensity);
		return wakeIntensity;
	}

	private Vec3 calculateWindTurbulenceTorque(DroneEnvironment environment, Vec3 relativeAirVelocityBody, double dtSeconds) {
		double intensity = environment.turbulenceIntensity();
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
		double highAdvanceLoss = 0.20 * smoothStep(0.46, 0.92, advanceRatio);

		double descentSpeed = Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody) - 1.2);
		double descentRatio = descentSpeed / Math.max(1.5, tipSpeed * 0.08);
		double axialLoss = rotor.axialFlowThrustLossCoefficient() * MathUtil.clamp(descentRatio, 0.0, 1.0);
		double climbSpeed = Math.max(0.0, rotorAxialVelocity(rotor, relativeAirVelocityBody));
		double pitchAdvance = climbSpeed / rotorPitchSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double pitchUnloadAuthority = MathUtil.clamp(0.08 + 0.18 / rotorBladePitchRatio(rotor), 0.10, 0.32);
		double bladePitchUnload = pitchUnloadAuthority * smoothStep(0.42, 1.05, pitchAdvance);
		return MathUtil.clamp(transverseLift * (1.0 - axialLoss) * (1.0 - highAdvanceLoss) * (1.0 - bladePitchUnload), 0.55, 1.25);
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

	private static double rotorStallVibration(RotorSpec rotor, double omegaRadiansPerSecond, double rotorStallIntensity) {
		if (rotorStallIntensity <= 1.0e-6 || rotor.stallThrustLossCoefficient() <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double lossScale = MathUtil.clamp(rotor.stallThrustLossCoefficient() / 0.34, 0.0, 1.0);
		return MathUtil.clamp(0.35 * lossScale * rotorStallIntensity * spinRatio, 0.0, 1.0);
	}

	private static double rotorVortexRingStateIntensity(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.12 || rotor.axialFlowThrustLossCoefficient() <= 0.0) {
			return 0.0;
		}

		double descentSpeed = Math.max(0.0, -rotorAxialVelocity(rotor, relativeAirVelocityBody));
		double inducedVelocity = Math.max(1.0, inducedVelocityMetersPerSecond);
		double descentRatio = descentSpeed / inducedVelocity;
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);

		double entry = smoothStep(0.45, 0.95, descentRatio);
		double exit = 1.0 - smoothStep(1.55, 2.25, descentRatio);
		double washout = 1.0 - smoothStep(2.5, 7.0, transverseSpeed);
		double load = smoothStep(0.12, 0.75, spinRatio);
		return MathUtil.clamp(entry * exit * washout * load, 0.0, 1.0);
	}

	private static double rotorTipSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
	}

	private static double rotorPitchSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.max(0.5, Math.abs(omegaRadiansPerSecond) * rotor.bladePitchMeters() / (2.0 * Math.PI));
	}

	private static double rotorBladePitchRatio(RotorSpec rotor) {
		return MathUtil.clamp(
				rotor.bladePitchMeters() / Math.max(1.0e-6, RotorSpec.defaultBladePitchMeters(rotor.radiusMeters())),
				0.25,
				2.50
		);
	}

	private static BladeElementAerodynamics rotorBladeElementAerodynamics(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.08) {
			return BladeElementAerodynamics.IDLE;
		}

		double stationRadius = rotor.radiusMeters() * 0.70;
		double tangentialSpeed = Math.max(0.5, Math.abs(omegaRadiansPerSecond) * stationRadius);
		double geometricPitchAngle = Math.atan(rotor.bladePitchMeters() / Math.max(1.0e-6, 2.0 * Math.PI * stationRadius));
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

	private static BladeDissymmetryAerodynamics rotorBladeDissymmetryAerodynamics(
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
		double liftDissymmetry = smoothStep(0.08, 0.34, advanceRatio) * loadedRotor;
		double retreatingBladeStall = smoothStep(0.42, 0.82, advanceRatio) * loadedRotor;
		double intensity = MathUtil.clamp(liftDissymmetry + 0.35 * retreatingBladeStall, 0.0, 1.0);
		double thrustScale = MathUtil.clamp(1.0 - 0.025 * liftDissymmetry - 0.075 * retreatingBladeStall, 0.86, 1.0);
		double loadFactor = MathUtil.clamp(0.035 * liftDissymmetry + 0.13 * retreatingBladeStall, 0.0, 0.22);
		double vibration = MathUtil.clamp(0.025 * liftDissymmetry + 0.075 * retreatingBladeStall, 0.0, 0.12);
		return new BladeDissymmetryAerodynamics(intensity, thrustScale, loadFactor, vibration);
	}

	private record BladeDissymmetryAerodynamics(
			double intensity,
			double thrustScale,
			double loadFactor,
			double vibration
	) {
		private static final BladeDissymmetryAerodynamics IDLE = new BladeDissymmetryAerodynamics(0.0, 1.0, 0.0, 0.0);
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

	private static double rotorCompressibilityVibration(RotorSpec rotor, double omegaRadiansPerSecond, double rotorTipMach) {
		double intensity = rotorCompressibilityIntensity(rotorTipMach);
		if (intensity <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.22 * intensity * spinRatio, 0.0, 0.34);
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

	private static double rotorAdvanceRatio(RotorSpec rotor, Vec3 relativeAirVelocityBody, double omegaRadiansPerSecond) {
		double transverseSpeed = rotorTransverseSpeed(rotor, relativeAirVelocityBody);
		return MathUtil.clamp(transverseSpeed / rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond), 0.0, 2.0);
	}

	private static double rotorDamageVibration(RotorSpec rotor, double omegaRadiansPerSecond, double rotorHealth) {
		double damage = 1.0 - MathUtil.clamp(rotorHealth, 0.0, 1.0);
		if (damage <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(damage * spinRatio * spinRatio, 0.0, 1.0);
	}

	private static double rotorImbalanceVibration(RotorSpec rotor, double omegaRadiansPerSecond) {
		double imbalance = MathUtil.clamp(rotor.imbalanceIntensity(), 0.0, 0.35);
		if (imbalance <= 1.0e-7) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(imbalance * (0.18 + 0.82 * spinRatio * spinRatio), 0.0, 0.40);
	}

	private static double rotorHealthThrustScale(double rotorHealth) {
		double health = MathUtil.clamp(rotorHealth, 0.0, 1.0);
		return Math.pow(health, 1.10);
	}

	private static double rotorSurfaceScrapeTargetScale(double surfaceScrapeIntensity) {
		double scrape = MathUtil.clamp(surfaceScrapeIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.26 * scrape, 0.60, 1.0);
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

	private static double waterImmersionThrustScale(double waterImmersionIntensity) {
		double water = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.82 * Math.pow(water, 0.72), 0.12, 1.0);
	}

	private static double precipitationThrustScale(double precipitationWetnessIntensity) {
		double wetness = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.055 * Math.pow(wetness, 0.85), 0.92, 1.0);
	}

	private static double rotorWaterLoadFactor(double waterImmersionIntensity) {
		double water = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		return 0.82 * Math.pow(water, 0.78);
	}

	private static double rotorPrecipitationLoadFactor(double precipitationWetnessIntensity) {
		double wetness = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		return 0.13 * Math.pow(wetness, 1.15);
	}

	private static double rotorWaterIngestionVibration(RotorSpec rotor, double omegaRadiansPerSecond, double waterImmersionIntensity) {
		double water = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		if (water <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.48 * Math.pow(water, 0.65) * spinRatio, 0.0, 1.0);
	}

	private static double rotorPrecipitationVibration(RotorSpec rotor, double omegaRadiansPerSecond, double precipitationWetnessIntensity) {
		double wetness = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		if (wetness <= 1.0e-6) {
			return 0.0;
		}

		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		return MathUtil.clamp(0.10 * Math.pow(wetness, 1.05) * spinRatio, 0.0, 1.0);
	}

	private RotorWakeInterference calculateRotorWakeInterference(boolean armed, Vec3 relativeAirVelocityBody) {
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
		if (crossflowFlush <= 1.0e-6) {
			return new RotorWakeInterference(intensity, downwash, swirl);
		}

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
				double downstreamFactor = rotorWakeAxisOverlap(source, receiver, downstreamDistance, lateralDistance);
				if (downstreamFactor <= 1.0e-6) {
					continue;
				}

				double wakeRadius = source.radiusMeters() + Math.max(0.0, downstreamDistance) * 0.42;
				double lateralFactor = 1.0 - smoothStep(wakeRadius * 0.35, wakeRadius + receiver.radiusMeters() * 0.85, lateralDistance);
				if (lateralFactor <= 1.0e-6) {
					continue;
				}

				double sourceSpinRatio = MathUtil.clamp(
						Math.abs(state.motorOmegaRadiansPerSecond(sourceIndex)) / source.maxOmegaRadiansPerSecond(),
						0.0,
						1.0
				);
				if (sourceSpinRatio <= 0.05) {
					continue;
				}

				double radiusMatch = MathUtil.clamp(source.radiusMeters() / Math.max(1.0e-6, receiver.radiusMeters()), 0.45, 1.45);
				double contribution = MathUtil.clamp(
						sourceSpinRatio * sourceSpinRatio * downstreamFactor * lateralFactor * crossflowFlush * (0.70 + 0.18 * radiusMatch),
						0.0,
						1.0
				);
				double sourceInducedVelocity = Math.max(
						state.rotorInducedVelocityMetersPerSecond(sourceIndex),
						sourceSpinRatio * source.maxOmegaRadiansPerSecond() * source.radiusMeters() * 0.10
				);
				double coaxialCoreFactor = 1.0 - smoothStep(source.radiusMeters() * 0.08, source.radiusMeters() * 0.72, lateralDistance);
				double swirlCapture = MathUtil.clamp(0.34 + 0.26 * coaxialCoreFactor + 0.14 * sourceSpinRatio, 0.0, 0.78);
				double swirlVelocity = contribution * sourceInducedVelocity * swirlCapture;
				Vec3 sourceArmBody = sourcePosition.subtract(config.centerOfMassOffsetBodyMeters());
				Vec3 swirlDirection = rotorWakeSwirlDirection(
						sourceAxisBody,
						lateralOffset,
						sourceArmBody,
						source.spinDirection()
				);
				receiverIntensity += contribution;
				receiverDownwash = receiverDownwash.add(sourceAxisBody.multiply(-contribution * sourceInducedVelocity));
				receiverSwirl = receiverSwirl.add(swirlDirection.multiply(swirlVelocity));
			}
			intensity[receiverIndex] = MathUtil.clamp(receiverIntensity, 0.0, 1.0);
			downwash[receiverIndex] = receiverDownwash.clamp(-12.0, 12.0);
			swirl[receiverIndex] = receiverSwirl.clamp(-8.0, 8.0);
		}
		return new RotorWakeInterference(intensity, downwash, swirl);
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

	private static double rotorWakeInterferenceThrustScale(double interference) {
		return MathUtil.clamp(1.0 - 0.22 * MathUtil.clamp(interference, 0.0, 1.0), 0.72, 1.0);
	}

	private static Vec3 rotorArmBodyWithFlex(RotorSpec rotor, Vec3 nominalRotorArmBody, double armFlexIntensity) {
		double flex = MathUtil.clamp(armFlexIntensity, 0.0, 1.0);
		Vec3 radialDirection = horizontalRotorArmDirection(nominalRotorArmBody);
		if (flex <= 1.0e-6 || radialDirection.lengthSquared() <= 1.0e-9) {
			return nominalRotorArmBody;
		}

		double armLength = Math.max(0.08, Math.hypot(nominalRotorArmBody.x(), nominalRotorArmBody.z()));
		double radiusScale = MathUtil.clamp(Math.sqrt(rotor.radiusMeters() / 0.0635), 0.55, 2.20);
		double verticalDeflection = ROTOR_ARM_FLEX_VERTICAL_DEFLECTION_SCALE * flex * armLength * radiusScale;
		return nominalRotorArmBody.add(BODY_ROTOR_AXIS.multiply(verticalDeflection));
	}

	private static RotorSpec rotorWithArmFlexedThrustAxis(RotorSpec rotor, Vec3 nominalRotorArmBody, double armFlexIntensity) {
		double flex = MathUtil.clamp(armFlexIntensity, 0.0, 1.0);
		Vec3 radialDirection = horizontalRotorArmDirection(nominalRotorArmBody);
		if (flex <= 1.0e-6 || radialDirection.lengthSquared() <= 1.0e-9) {
			return rotor;
		}

		double armLength = Math.max(0.08, Math.hypot(nominalRotorArmBody.x(), nominalRotorArmBody.z()));
		double armScale = MathUtil.clamp(Math.sqrt(armLength / 0.24), 0.55, 2.10);
		double radiusScale = MathUtil.clamp(Math.sqrt(rotor.radiusMeters() / 0.0635), 0.55, 2.20);
		double tiltRadians = ROTOR_ARM_FLEX_TILT_RADIANS * flex * armScale * radiusScale;
		Vec3 axis = rotorAxisBody(rotor).subtract(radialDirection.multiply(tiltRadians)).normalized();
		if (axis.lengthSquared() <= 1.0e-9 || axis.y() <= 0.0) {
			return rotor;
		}
		return rotor.withThrustAxisBody(axis);
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
		double steadyLoad = smoothStep(0.22, 0.95, forceMagnitude / maxThrust);
		double snapLoad = smoothStep(4.0, 45.0, forceSlew);
		double torsionalSnap = smoothStep(1.5, 28.0, torqueSlew);
		double target = MathUtil.clamp(
				(0.16 * steadyLoad + 0.26 * snapLoad + 0.18 * torsionalSnap) * smoothStep(0.05, 0.35, spinRatio),
				0.0,
				1.0
		);
		double timeConstant = target > rotorArmFlexIntensity[index] ? 0.018 : 0.085;
		double alpha = MathUtil.expSmoothing(dtSeconds, timeConstant);
		double flex = rotorArmFlexIntensity[index] + (target - rotorArmFlexIntensity[index]) * alpha;
		rotorArmFlexIntensity[index] = MathUtil.clamp(flex, 0.0, 1.0);
		previousRotorForceBodyNewtons[index] = forceBody;
		previousRotorTorqueBodyNewtonMeters[index] = torqueBody;
		return rotorArmFlexIntensity[index];
	}

	private static double rotorArmFlexVibration(RotorSpec rotor, double omegaRadiansPerSecond, double armFlexIntensity) {
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
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double dtSeconds
	) {
		Vec3 targetTiltBody = rotorFlappingTargetTiltBody(rotor, relativeAirVelocityBody, omegaRadiansPerSecond, thrustNewtons);
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
		double tilt = rotor.flappingCoefficient()
				* MathUtil.clamp(advanceRatio / 0.22, 0.0, 1.0)
				* (0.35 + 0.65 * thrustFraction);
		Vec3 transverseUnit = transverseVelocityBody.multiply(1.0 / transverseSpeed);
		return transverseUnit.multiply(-tilt);
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
				- climbUnload;
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

	private double updateRotorInducedInflow(
			int index,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBody,
			double omegaRadiansPerSecond,
			double baseThrustNewtons,
			double airDensityRatio,
			double dtSeconds
	) {
		double hoverTargetInducedVelocity = targetRotorInducedVelocityMetersPerSecond(rotor, baseThrustNewtons, airDensityRatio);
		double translationalLift = rotorTranslationalLiftIntensity(
				rotor,
				relativeAirVelocityBody,
				omegaRadiansPerSecond,
				hoverTargetInducedVelocity
		);
		state.setRotorTranslationalLiftIntensity(index, translationalLift);

		double liftCoefficientScale = MathUtil.clamp(rotor.transverseFlowLiftCoefficient() / 0.08, 0.0, 1.35);
		double cleanInflowReduction = 0.28 * translationalLift * liftCoefficientScale;
		double targetInducedVelocity = hoverTargetInducedVelocity * MathUtil.clamp(1.0 - cleanInflowReduction, 0.58, 1.0);
		double previousInducedVelocity = state.rotorInducedVelocityMetersPerSecond(index);
		double alpha = rotor.inducedInflowTimeConstantSeconds() <= 0.0
				? 1.0
				: MathUtil.expSmoothing(dtSeconds, rotor.inducedInflowTimeConstantSeconds());
		double inducedVelocity = previousInducedVelocity + (targetInducedVelocity - previousInducedVelocity) * alpha;
		state.setRotorInducedVelocityMetersPerSecond(index, inducedVelocity);

		if (rotor.inducedInflowLagCoefficient() <= 0.0 || targetInducedVelocity <= 1.0e-6) {
			return 1.0;
		}

		double safeVelocity = Math.max(1.0e-6, Math.max(targetInducedVelocity, previousInducedVelocity));
		double inflowDeficit = Math.max(0.0, (targetInducedVelocity - inducedVelocity) / targetInducedVelocity);
		double wakeCarryover = Math.max(0.0, (inducedVelocity - targetInducedVelocity) / safeVelocity);
		double thrustLoss = rotor.inducedInflowLagCoefficient() * (inflowDeficit + 0.35 * wakeCarryover);
		return MathUtil.clamp(1.0 - thrustLoss, 0.65, 1.0);
	}

	private static double rotorTranslationalLiftIntensity(
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

	private static double targetRotorInducedVelocityMetersPerSecond(
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

	private Vec3 calculateAirframeAerodynamicTorque(Vec3 relativeAirVelocityBody, double airDensityRatio) {
		double speed = relativeAirVelocityBody.length();
		Vec3 pressureCenterTorque = calculateAirframePressureCenterTorque(relativeAirVelocityBody, airDensityRatio);
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
		return attitudeTorque.add(pressureCenterTorque).clamp(-0.55, 0.55);
	}

	private Vec3 calculateAirframePressureCenterTorque(Vec3 relativeAirVelocityBody, double airDensityRatio) {
		Vec3 momentArmBody = config.centerOfPressureOffsetBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
		if (momentArmBody.lengthSquared() <= 1.0e-12 || relativeAirVelocityBody.lengthSquared() <= 1.0e-6 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}

		Vec3 airframeForceBody = calculateAirframeBodyDragForce(relativeAirVelocityBody, airDensityRatio)
				.add(calculateAirframeLiftForce(relativeAirVelocityBody, airDensityRatio));
		return momentArmBody.cross(airframeForceBody).clamp(-0.45, 0.45);
	}

	private Vec3 calculateAirframeAngularDragTorque(Vec3 angularVelocityBody, Vec3 relativeAirVelocityBody, double airDensityRatio) {
		double speed = relativeAirVelocityBody.length();
		Vec3 drag = config.bodyDragCoefficients();
		double dynamicScale = Math.max(0.0, airDensityRatio) * speed * speed;
		double pitchDamping = config.angularDragCoefficient()
				+ MathUtil.clamp(dynamicScale * (0.00022 * drag.z() + 0.00006 * drag.y()), 0.0, 0.36);
		double yawDamping = config.angularDragCoefficient()
				+ MathUtil.clamp(dynamicScale * (0.00018 * drag.x() + 0.00008 * drag.z()), 0.0, 0.36);
		double rollDamping = config.angularDragCoefficient()
				+ MathUtil.clamp(dynamicScale * (0.00020 * drag.x() + 0.00006 * drag.y()), 0.0, 0.36);
		return new Vec3(
				-angularVelocityBody.x() * pitchDamping,
				-angularVelocityBody.y() * yawDamping,
				-angularVelocityBody.z() * rollDamping
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
		Vec3 targetMeanWind = environment.windVelocityWorldMetersPerSecond();
		if (!windModelInitialized || dtSeconds <= 0.0) {
			windModelInitialized = true;
			meanWindVelocityWorldMetersPerSecond = targetMeanWind;
			windGustVelocityWorldMetersPerSecond = Vec3.ZERO;
			state.setEffectiveWindVelocityWorldMetersPerSecond(targetMeanWind);
			state.setWindGustVelocityWorldMetersPerSecond(Vec3.ZERO);
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
		Vec3 targetGust = windGustTarget(environment, targetMeanWind, dirtyAir);
		double gustTimeConstant = MathUtil.clamp(0.070 + 0.085 / (0.35 + dirtyAir), 0.055, 0.260);
		double gustAlpha = MathUtil.expSmoothing(dtSeconds, gustTimeConstant);
		windGustVelocityWorldMetersPerSecond = windGustVelocityWorldMetersPerSecond.add(
				targetGust.subtract(windGustVelocityWorldMetersPerSecond).multiply(gustAlpha)
		);

		Vec3 previousEffectiveWind = state.effectiveWindVelocityWorldMetersPerSecond();
		Vec3 effectiveWind = meanWindVelocityWorldMetersPerSecond.add(windGustVelocityWorldMetersPerSecond);
		double shearAcceleration = effectiveWind.subtract(previousEffectiveWind).length() / Math.max(1.0e-6, dtSeconds);
		state.setEffectiveWindVelocityWorldMetersPerSecond(effectiveWind);
		state.setWindGustVelocityWorldMetersPerSecond(windGustVelocityWorldMetersPerSecond);
		state.setWindShearAccelerationMetersPerSecondSquared(shearAcceleration);
		return effectiveWind;
	}

	private double dirtyAirIntensity(DroneEnvironment environment) {
		return MathUtil.clamp(
				environment.turbulenceIntensity()
						+ 0.26 * environment.obstacleProximity()
						+ 0.18 * environment.droneWakeIntensity()
						+ 0.12 * environment.ceilingEffectIntensity(config),
				0.0,
				1.8
		);
	}

	private Vec3 windGustTarget(DroneEnvironment environment, Vec3 targetMeanWind, double dirtyAir) {
		if (dirtyAir <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double windSpeed = targetMeanWind.length();
		double gustScale = MathUtil.clamp(dirtyAir * (0.32 + 0.070 * windSpeed), 0.0, 4.5);
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
		return new Vec3(horizontalGustX, verticalGust, horizontalGustZ)
				.multiply(gustScale)
				.add(upstreamBurble);
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
		double dynamicCutoff = MathUtil.lerp(lowCutoff, configuredCutoff, authority) * vibrationFoldback;
		return MathUtil.clamp(dynamicCutoff, Math.max(1.0, configuredCutoff * 0.30), configuredCutoff);
	}

	private static double feedForwardTorque(PidGains gains, double targetRateAccelerationRadiansPerSecondSquared) {
		double torque = targetRateAccelerationRadiansPerSecondSquared * gains.feedForward();
		double limit = Math.max(0.04, gains.p() * 8.0);
		return MathUtil.clamp(torque, -limit, limit);
	}

	private static double shapeRateInput(double input, double expo, double superRate) {
		double clamped = MathUtil.clamp(input, -1.0, 1.0);
		double shaped = clamped * (1.0 - expo) + clamped * clamped * clamped * expo;
		double superRateClamped = MathUtil.clamp(superRate, 0.0, 0.95);
		if (superRateClamped <= 1.0e-9) {
			return shaped;
		}

		double denominator = Math.max(1.0e-6, 1.0 - Math.abs(shaped) * superRateClamped);
		return shaped * (1.0 - superRateClamped) / denominator;
	}

	private void mixRotorThrusts(DroneInput input, Vec3 requestedTorqueBody) {
		double[] baseThrusts = new double[targetRotorThrusts.length];
		double[] mixedThrusts = new double[targetRotorThrusts.length];
		double[] torqueMix = input.armed()
				? allocateTorqueMixDeltas(config.rotors(), config.centerOfMassOffsetBodyMeters(), requestedTorqueBody)
				: new double[targetRotorThrusts.length];

		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double baseThrust = input.armed() ? input.throttle() * rotor.maxThrustNewtons() : 0.0;
			baseThrusts[i] = baseThrust;
			mixedThrusts[i] = baseThrust + torqueMix[i];
		}

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

	private void integrateLinear(Vec3 totalForceBody, DroneEnvironment environment, Vec3 effectiveWindVelocityWorld, double dtSeconds) {
		Vec3 gravity = new Vec3(0.0, -config.massKg() * config.gravityMetersPerSecondSquared(), 0.0);
		Vec3 velocity = state.velocityMetersPerSecond();
		Vec3 relativeAirVelocity = velocity.subtract(effectiveWindVelocityWorld);
		Vec3 velocityBody = state.orientation().conjugate().rotate(relativeAirVelocity);
		Vec3 airframeLiftBody = calculateAirframeLiftForce(velocityBody, environment.airDensityRatio());
		state.setAirframeLiftForceBodyNewtons(airframeLiftBody);
		Vec3 groundEffectDragBody = calculateGroundEffectDragForce(totalForceBody, velocityBody, environment);
		state.setGroundEffectDragForceBodyNewtons(groundEffectDragBody);
		Vec3 rotorWashDragBody = calculateRotorWashDragForce(totalForceBody, velocityBody, environment.airDensityRatio());
		state.setRotorWashDragForceBodyNewtons(rotorWashDragBody);
		Vec3 thrustWorld = state.orientation().rotate(totalForceBody.add(airframeLiftBody).add(groundEffectDragBody).add(rotorWashDragBody));
		Vec3 bodyDrag = calculateAirframeBodyDragForce(velocityBody, environment.airDensityRatio());
		Vec3 isotropicDrag = relativeAirVelocity.multiply(-config.linearDragCoefficient() * relativeAirVelocity.length() * environment.airDensityRatio());
		Vec3 waterDrag = calculateWaterImmersionDragForce(velocity, environment);
		Vec3 drag = state.orientation().rotate(bodyDrag).add(isotropicDrag).add(waterDrag);
		Vec3 acceleration = thrustWorld.add(gravity).add(drag).multiply(1.0 / config.massKg());

		velocity = velocity.add(acceleration.multiply(dtSeconds));
		Vec3 position = state.positionMeters().add(velocity.multiply(dtSeconds));

		state.setLinearAccelerationWorldMetersPerSecondSquared(acceleration);
		state.setVelocityMetersPerSecond(velocity);
		state.setPositionMeters(position);
	}

	private Vec3 calculateAirframeBodyDragForce(Vec3 relativeAirVelocityBody, double airDensityRatio) {
		return new Vec3(
				-config.bodyDragCoefficients().x() * MathUtil.squareSigned(relativeAirVelocityBody.x()),
				-config.bodyDragCoefficients().y() * MathUtil.squareSigned(relativeAirVelocityBody.y()),
				-config.bodyDragCoefficients().z() * MathUtil.squareSigned(relativeAirVelocityBody.z())
		).multiply(Math.max(0.0, airDensityRatio));
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

	private Vec3 calculateRotorWashDragForce(Vec3 totalForceBody, Vec3 relativeAirVelocityBody, double airDensityRatio) {
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

		Vec3 drag = config.bodyDragCoefficients();
		double washDynamicScale = Math.max(0.0, airDensityRatio) * inducedVelocity * washIntensity;
		double horizontalExposure = 0.055 * (drag.x() + drag.z());
		double verticalExposure = 0.040 * drag.y();
		return new Vec3(
				-relativeAirVelocityBody.x() * washDynamicScale * horizontalExposure,
				-relativeAirVelocityBody.y() * washDynamicScale * verticalExposure,
				-relativeAirVelocityBody.z() * washDynamicScale * horizontalExposure
		).clamp(-12.0, 12.0);
	}

	private Vec3 calculateGroundEffectDragForce(Vec3 totalForceBody, Vec3 relativeAirVelocityBody, DroneEnvironment environment) {
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

		double lateralDragCoefficient = 0.18 * Math.sqrt(config.bodyDragCoefficients().x() * config.bodyDragCoefficients().z());
		double densityScale = Math.max(0.0, environment.airDensityRatio());
		double cushionScale = proximity * proximity * rotorWash * densityScale;
		double dragScale = lateralDragCoefficient * lateralSpeed * cushionScale;
		return new Vec3(
				-relativeAirVelocityBody.x() * dragScale,
				0.0,
				-relativeAirVelocityBody.z() * dragScale
		).clamp(-14.0, 14.0);
	}

	private Vec3 calculateAirframeLiftForce(Vec3 relativeAirVelocityBody, double airDensityRatio) {
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared < 1.0e-6 || airDensityRatio <= 0.0) {
			return Vec3.ZERO;
		}

		double pitchPlaneSpeed = Math.hypot(relativeAirVelocityBody.y(), relativeAirVelocityBody.z());
		Vec3 pitchLift = Vec3.ZERO;
		if (pitchPlaneSpeed > 1.0e-6) {
			double aoa = Math.atan2(relativeAirVelocityBody.y(), relativeAirVelocityBody.z());
			double liftCoefficient = 0.085 * Math.sqrt(config.bodyDragCoefficients().y() * config.bodyDragCoefficients().z());
			double stallScale = 1.0 - 0.55 * smoothStep(Math.toRadians(34.0), Math.toRadians(72.0), Math.abs(aoa));
			double liftMagnitude = liftCoefficient * speedSquared * Math.sin(2.0 * aoa) * stallScale * airDensityRatio;
			Vec3 liftDirection = new Vec3(0.0, relativeAirVelocityBody.z(), -relativeAirVelocityBody.y()).normalized();
			pitchLift = liftDirection.multiply(liftMagnitude);
		}

		double yawPlaneSpeed = Math.hypot(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
		Vec3 sideLift = Vec3.ZERO;
		if (yawPlaneSpeed > 1.0e-6) {
			double sideslip = Math.atan2(relativeAirVelocityBody.x(), relativeAirVelocityBody.z());
			double sideforceCoefficient = 0.065 * Math.sqrt(config.bodyDragCoefficients().x() * config.bodyDragCoefficients().z());
			double stallScale = 1.0 - 0.50 * smoothStep(Math.toRadians(35.0), Math.toRadians(75.0), Math.abs(sideslip));
			double sideforceMagnitude = sideforceCoefficient * speedSquared * Math.sin(2.0 * sideslip) * stallScale * airDensityRatio;
			Vec3 sideforceDirection = new Vec3(-relativeAirVelocityBody.z(), 0.0, relativeAirVelocityBody.x()).normalized();
			sideLift = sideforceDirection.multiply(sideforceMagnitude);
		}

		return pitchLift.add(sideLift).clamp(-18.0, 18.0);
	}

	private void integrateAngular(Vec3 torqueBody, Vec3 relativeAirVelocityBody, double airDensityRatio, double dtSeconds) {
		Vec3 omega = state.angularVelocityBodyRadiansPerSecond();
		Vec3 angularDrag = calculateAirframeAngularDragTorque(omega, relativeAirVelocityBody, airDensityRatio);
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
		integrateBatteryThermal(dischargeCurrentAmps, regenerativeCurrentAmps, environment, dtSeconds);
		updateBatteryVoltage(netBatteryCurrentAmps, regenerativeCurrentAmps, environment, dtSeconds);
	}

	private MotorCurrentEstimate estimateMotorCurrent(int index) {
		double escOutput = state.escOutputCommand(index);
		RotorSpec rotor = config.rotors().get(index);
		double rpmFraction = state.motorPower(config, index);
		double perMotorMaxCurrentAmps = config.maxBatteryCurrentAmps() / state.motorCount();
		double brakingLoad = config.motorActiveBrakingStrength() * Math.max(0.0, rpmFraction - escOutput);
		double brakingCurrent = perMotorMaxCurrentAmps
				* 0.16
				* brakingLoad
				* (0.30 + 0.70 * rpmFraction);
		double regenerativeCurrent = brakingCurrent * regenerativeBrakingFraction(rpmFraction, escOutput, brakingLoad);
		double thrustFraction = MathUtil.clamp(state.rotorThrustNewtons(index) / rotor.maxThrustNewtons(), 0.0, 1.25);
		double aerodynamicLoadFactor = state.rotorAerodynamicLoadFactor(index) <= 1.0e-6
				? 1.0
				: state.rotorAerodynamicLoadFactor(index);
		double electricalEfficiency = motorElectricalEfficiency(index, rpmFraction, aerodynamicLoadFactor);
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
		double aerodynamicPower = rpmFraction * rpmFraction * rpmFraction * aerodynamicLoadFactor;
		double loadedPropPower = Math.sqrt(thrustFraction) * rpmFraction * aerodynamicLoadFactor;
		double normalizedLoad = 0.12 * escCopperLoss + 0.34 * aerodynamicPower + 0.54 * loadedPropPower;
		double shaftPowerCurrent = motorShaftPowerCurrentAmps(index, perMotorMaxCurrentAmps, electricalEfficiency);
		double driveVoltage = motorDriveVoltage(
				escOutput,
				Math.sqrt(state.batteryPowerLimit() * state.motorThermalLimit() * state.escThermalLimit(index) * state.rotorHealth(index))
		);
		double backEmfVoltage = motorBackEmfVoltage(rotor, state.motorOmegaRadiansPerSecond(index));
		double windingVoltageDrop = Math.max(0.0, driveVoltage - backEmfVoltage);
		double windingCurrent = windingVoltageDrop / inferredMotorWindingResistanceOhms();
		double phaseCurrent = Math.max(0.0, windingCurrent);
		double busVoltage = Math.max(1.0e-6, state.batteryVoltage());
		double busEquivalentWindingCurrent = windingCurrent * MathUtil.clamp(windingVoltageDrop / busVoltage, 0.0, 1.0);
		double noLoadCurrent = perMotorMaxCurrentAmps
				* (0.018 + 0.045 * rpmFraction)
				* MathUtil.clamp(0.35 + 0.65 * escOutput, 0.0, 1.0);
		double electricalModelCurrent = noLoadCurrent
				+ busEquivalentWindingCurrent * MathUtil.clamp(0.35 + 0.65 * aerodynamicLoadFactor, 0.25, 1.60);
		double desyncCurrent = perMotorMaxCurrentAmps
				* 0.22
				* state.escDesyncIntensity(index)
				* escOutput
				* (0.45 + 0.55 * rpmFraction);
		double currentRipple = phaseCurrent
				* state.motorCommutationRippleIntensity(index)
				* (0.22 + 0.78 * escOutput)
				+ phaseCurrent
						* rotor.imbalanceIntensity()
						* (0.05 + 0.45 * rpmFraction)
				+ perMotorMaxCurrentAmps
						* 0.06
						* state.escDesyncIntensity(index)
						* smoothStep(0.12, 0.82, escOutput);
		double curveCurrent = perMotorMaxCurrentAmps * MathUtil.clamp(normalizedLoad, 0.0, 1.20);
		double propulsionCurrent = Math.max(Math.max(curveCurrent, shaftPowerCurrent), electricalModelCurrent)
				+ desyncCurrent
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

	private double regenerativeBrakingFraction(double rpmFraction, double escOutput, double brakingLoad) {
		if (brakingLoad <= 1.0e-6 || config.motorActiveBrakingStrength() <= 1.0e-6) {
			return 0.0;
		}

		double overrun = Math.max(0.0, rpmFraction - escOutput);
		double overrunFactor = smoothStep(0.025, 0.42, overrun);
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

	private double motorElectricalEfficiency(int index, double rpmFraction, double aerodynamicLoadFactor) {
		double escAuthority = MathUtil.clamp(0.35 + 0.65 * state.escOutputCommand(index), 0.0, 1.0);
		return MathUtil.clamp(
				0.58
						+ 0.22 * escAuthority
						- 0.07 * Math.pow(1.0 - MathUtil.clamp(rpmFraction, 0.0, 1.15), 2.0)
						- 0.05 * MathUtil.clamp(aerodynamicLoadFactor - 1.0, 0.0, 0.75)
						- 0.05 * (1.0 - state.escThermalLimit(index))
						- 0.06 * state.escDesyncIntensity(index)
						- 0.026 * state.motorCommutationRippleIntensity(index),
				0.52,
				0.86
		);
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

	private Vec3 gyroNoiseBodyRadiansPerSecond(double dtSeconds) {
		double noise = config.gyroNoiseStdDevRadiansPerSecond();
		double vibration = state.rotorVibration();
		double busRipple = state.batteryBusRippleVoltage();
		if (noise <= 0.0 && vibration <= 0.0 && busRipple <= 0.0) {
			state.setGyroDynamicNotchFrequencyHertz(0.0);
			state.setGyroDynamicNotchAttenuation(0.0);
			state.setGyroBladePassNotchFrequencyHertz(0.0);
			state.setGyroBladePassNotchAttenuation(0.0);
			return Vec3.ZERO;
		}

		double motorVibration = 0.25 + 0.75 * state.averageMotorPower(config);
		double propwashVibration = 1.0 + 0.7 * state.propwashIntensity();
		double noiseScale = noise * motorVibration * propwashVibration / 1.35;
		double averageOmega = averageMotorOmegaRadiansPerSecond();
		double notchFrequencyHertz = averageOmega / (Math.PI * 2.0);
		double notchAttenuation = gyroDynamicNotchAttenuation(notchFrequencyHertz, vibration);
		double bladePassNotchFrequencyHertz = notchFrequencyHertz * 2.0;
		double bladePassNotchAttenuation = gyroDynamicNotchAttenuation(bladePassNotchFrequencyHertz, vibration);
		state.setGyroDynamicNotchFrequencyHertz(notchFrequencyHertz);
		state.setGyroDynamicNotchAttenuation(notchAttenuation);
		state.setGyroBladePassNotchFrequencyHertz(bladePassNotchFrequencyHertz);
		state.setGyroBladePassNotchAttenuation(bladePassNotchAttenuation);
		gyroRotorVibrationPhase += averageOmega * dtSeconds;
		gyroBladePassVibrationPhase += averageOmega * 2.0 * dtSeconds;
		double t = gyroNoiseTimeSeconds;
		Vec3 broadbandNoise = new Vec3(
				noiseScale * (Math.sin(t * 437.0 + 0.2) + 0.35 * Math.sin(t * 941.0 + 1.7)),
				noiseScale * (Math.sin(t * 389.0 + 2.1) + 0.30 * Math.sin(t * 811.0 + 0.4)),
				noiseScale * (Math.sin(t * 463.0 + 1.1) + 0.32 * Math.sin(t * 877.0 + 2.8))
		);
		double railNoiseScale = Math.toRadians(0.42 * busRipple) * (0.35 + 0.65 * motorVibration);
		Vec3 powerRailNoise = new Vec3(
				railNoiseScale * (Math.sin(t * 617.0 + 0.9) + 0.28 * Math.sin(t * 1321.0 + 2.4)),
				railNoiseScale * (Math.sin(t * 557.0 + 1.8) + 0.24 * Math.sin(t * 1187.0 + 0.6)),
				railNoiseScale * (Math.sin(t * 683.0 + 2.9) + 0.26 * Math.sin(t * 1261.0 + 1.4))
		);
		double rotorVibrationScale = 0.45 * vibration * (1.0 - notchAttenuation);
		double bladePassVibrationScale = 0.45 * vibration * (1.0 - bladePassNotchAttenuation);
		Vec3 motorSynchronousNoise = new Vec3(
				rotorVibrationScale * Math.sin(gyroRotorVibrationPhase + 0.3)
						+ 0.42 * bladePassVibrationScale * Math.sin(gyroBladePassVibrationPhase + 1.2),
				rotorVibrationScale * Math.sin(gyroRotorVibrationPhase + 2.0)
						+ 0.35 * bladePassVibrationScale * Math.sin(gyroBladePassVibrationPhase + 0.4),
				rotorVibrationScale * Math.sin(gyroRotorVibrationPhase + 4.1)
						+ 0.38 * bladePassVibrationScale * Math.sin(gyroBladePassVibrationPhase + 2.6)
		);
		return broadbandNoise.add(powerRailNoise).add(motorSynchronousNoise);
	}

	private double averageMotorOmegaRadiansPerSecond() {
		double sum = 0.0;
		for (int i = 0; i < state.motorCount(); i++) {
			sum += Math.abs(state.motorOmegaRadiansPerSecond(i));
		}
		return sum / state.motorCount();
	}

	private static double gyroDynamicNotchAttenuation(double frequencyHertz, double rotorVibration) {
		double frequencyActive = smoothStep(25.0, 75.0, frequencyHertz);
		double vibrationActive = smoothStep(0.005, 0.09, rotorVibration);
		return 0.72 * frequencyActive * vibrationActive;
	}

	private void resetGyroModel() {
		gyroFilteredBodyRadiansPerSecond = state.angularVelocityBodyRadiansPerSecond();
		Arrays.fill(gyroDelayBuffer, gyroFilteredBodyRadiansPerSecond);
		state.setGyroAngularVelocityBodyRadiansPerSecond(gyroFilteredBodyRadiansPerSecond);
		state.setGyroClipIntensity(0.0);
		state.setGyroDynamicNotchFrequencyHertz(0.0);
		state.setGyroDynamicNotchAttenuation(0.0);
		state.setGyroBladePassNotchFrequencyHertz(0.0);
		state.setGyroBladePassNotchAttenuation(0.0);
		gyroDelayWriteIndex = 0;
		gyroNoiseTimeSeconds = 0.0;
		gyroRotorVibrationPhase = 0.0;
		gyroBladePassVibrationPhase = 0.0;
	}

	private void updateAccelerometerMeasurement(double dtSeconds) {
		accelerometerNoiseTimeSeconds += dtSeconds;
		Vec3 rawSpecificForce = specificForceBodyMetersPerSecondSquared()
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
		if (noise <= 0.0 && vibration <= 0.0 && busRipple <= 0.0) {
			return Vec3.ZERO;
		}

		double motorVibration = 0.20 + 0.80 * state.averageMotorPower(config);
		double propwashVibration = 1.0 + 0.9 * state.propwashIntensity();
		double scale = noise * motorVibration * propwashVibration / 1.30
				+ 4.0 * vibration
				+ 0.18 * busRipple * (0.30 + 0.70 * motorVibration);
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
		double flowError = barometerFlowErrorMeters(environment);
		double rawAltitude = trueAltitude + flowError + barometerNoiseMeters(environment);

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
				environment.ambientTemperatureCelsius()
		));
		state.setBarometerErrorMeters(barometerFilteredAltitudeMeters - trueAltitude);
		state.setBarometerPropwashErrorMeters(flowError);
	}

	private double barometerFlowErrorMeters(DroneEnvironment environment) {
		double motorPower = state.averageMotorPower(config);
		double inducedVelocity = state.averageRotorInducedVelocityMetersPerSecond();
		double cleanRotorWash = smoothStep(0.08, 0.35, motorPower) * smoothStep(0.5, 5.5, inducedVelocity);
		double unsteadyWash = 0.85 * state.propwashIntensity()
				+ 0.55 * state.vortexRingStateIntensity()
				+ 0.25 * state.rotorVibration()
				+ 0.32 * environment.droneWakeIntensity();
		double groundCompression = config.groundEffectMaxThrustBoost() <= 1.0e-6
				? 0.0
				: MathUtil.clamp((environment.groundEffectThrustMultiplier(config) - 1.0) / config.groundEffectMaxThrustBoost(), 0.0, 1.0);
		double ceilingSuction = environment.ceilingEffectIntensity(config);
		double pressurePortError = 0.90 * cleanRotorWash
				+ 1.15 * unsteadyWash
				+ 0.35 * ceilingSuction
				- 0.60 * groundCompression;
		return MathUtil.clamp(pressurePortError, -2.5, 4.5);
	}

	private double barometerNoiseMeters(DroneEnvironment environment) {
		double noiseAmplitude = 0.035 * config.accelerometerNoiseStdDevMetersPerSecondSquared()
				+ 0.040 * environment.turbulenceIntensity()
				+ 0.090 * state.rotorVibration()
				+ 0.035 * state.propwashIntensity();
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
		barometerInitialized = false;
		state.setBarometerAltitudeMeters(barometerFilteredAltitudeMeters);
		state.setBarometerVerticalSpeedMetersPerSecond(barometerFilteredVerticalSpeedMetersPerSecond);
		state.setBarometerPressureHectopascals(DroneEnvironment.barometricPressureHectopascals(barometerFilteredAltitudeMeters, 1.0, 25.0));
		state.setBarometerErrorMeters(0.0);
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
		trust = MathUtil.clamp(trust, 0.0, 1.0);
		state.setAttitudeEstimatorAccelerometerTrust(trust);
		if (trust <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 measuredUpBody = measuredSpecificForce.normalized();
		Vec3 estimatedUpBody = estimatedOrientation.conjugate().rotate(WORLD_UP).normalized();
		return measuredUpBody.cross(estimatedUpBody).multiply(gain * trust);
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
		state.setEscCommandTelemetry(0.0, escCommandFrameIntervalSeconds(), 0.0);
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
					* Math.max(0.0, state.motorPower(config, i) - state.escOutputCommand(i));
			double power = MathUtil.clamp(
					0.40 * state.escOutputCommand(i) + 0.48 * currentLoad + 0.12 * brakingLoad,
					0.0,
					1.2
			);
			double temperature = state.motorTemperatureCelsius(i);
			double heatRate = config.motorThermalRiseCelsiusPerSecond() * power * power;
			double coolingFactor = motorCoolingFactor(rotor, relativeAirVelocityBody, angularVelocityBody, environment, i);
			state.setMotorCoolingFactor(i, coolingFactor);
			double coolingRate = config.motorCoolingRatePerSecond()
					* coolingFactor
					* (temperature - environment.ambientTemperatureCelsius());
			state.setMotorTemperatureCelsius(i, temperature + (heatRate - coolingRate) * dtSeconds);
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
			double output = state.escOutputCommand(i);
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
					* (temperature - environment.ambientTemperatureCelsius());
			state.setEscTemperatureCelsius(i, temperature + (heatRate - coolingRate) * dtSeconds);
			state.setEscThermalLimit(i, escThermalLimit(state.escTemperatureCelsius(i)));
		}
		updateEscThermalLimit();
	}

	private double escCoolingFactor(DroneEnvironment environment, int rotorIndex) {
		double rotorWashCooling = 0.45 * state.motorPower(config, rotorIndex) * (0.35 + 0.65 * state.escOutputCommand(rotorIndex));
		double boardAirflow = 0.58 + 0.42 * state.motorCoolingFactor(rotorIndex) + rotorWashCooling;
		double obstructionLoss = 1.0 - 0.36 * environment.rotorFlowObstruction(rotorIndex);
		double densityFactor = MathUtil.clamp(environment.airDensityRatio(), 0.35, 1.35);
		return MathUtil.clamp(boardAirflow * densityFactor * obstructionLoss, 0.20, 4.0);
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
		double rotorWashCooling = 0.92 * state.motorPower(config, rotorIndex) * (0.45 + 0.55 * state.escOutputCommand(rotorIndex));
		double obstructionLoss = 1.0 - 0.48 * environment.rotorFlowObstruction(rotorIndex);
		double densityFactor = MathUtil.clamp(environment.airDensityRatio(), 0.35, 1.35);
		return MathUtil.clamp((1.0 + freestreamCooling + rotorWashCooling) * densityFactor * obstructionLoss, 0.20, 4.0);
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
			state.setBatteryTemperatureCelsius(environment.ambientTemperatureCelsius());
			state.setBatteryCoolingFactor(1.0);
			state.setBatteryThermalLimit(batteryThermalLimit(environment.ambientTemperatureCelsius()));
			batteryThermalInitialized = true;
		}

		double packTemperature = state.batteryTemperatureCelsius();
		double capacityScale = MathUtil.clamp(Math.sqrt(Math.max(0.35, config.batteryCapacityAmpHours())), 0.60, 2.4);
		double maxCurrent = Math.max(1.0, config.maxBatteryCurrentAmps());
		double currentLoad = MathUtil.clamp(dischargeCurrentAmps / maxCurrent, 0.0, 2.0);
		double regenLoad = MathUtil.clamp(regenerativeCurrentAmps / maxCurrent, 0.0, 1.5);
		double rippleLoad = MathUtil.clamp(state.averageMotorCurrentRippleAmps() / Math.max(1.0, maxCurrent / Math.max(1, state.motorCount())), 0.0, 1.8);
		double batteryResistanceOhms = temperatureAdjustedBatteryResistanceOhms(packTemperature, environment.ambientTemperatureCelsius());
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
				* Math.max(0.0, packTemperature - environment.ambientTemperatureCelsius());
		state.setBatteryTemperatureCelsius(packTemperature + (heatRate - coolingRate) * dtSeconds);
		state.setBatteryThermalLimit(batteryThermalLimit(state.batteryTemperatureCelsius()));
	}

	private double batteryCoolingFactor(DroneEnvironment environment) {
		double airspeedCooling = MathUtil.clamp(state.airspeedMetersPerSecond() / 20.0, 0.0, 1.8);
		double rotorWashCooling = 0.35 * state.averageMotorPower(config);
		double densityFactor = MathUtil.clamp(environment.airDensityRatio(), 0.35, 1.35);
		double wetCooling = 1.40 * MathUtil.clamp(environment.waterImmersionIntensity(), 0.0, 1.0)
				+ 0.22 * MathUtil.clamp(environment.precipitationWetnessIntensity(), 0.0, 1.0);
		return MathUtil.clamp((0.55 + 0.45 * airspeedCooling + rotorWashCooling) * densityFactor + wetCooling, 0.20, 4.0);
	}

	private static double batteryThermalLimit(double batteryTemperatureCelsius) {
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
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0;
		double stateOfCharge = MathUtil.clamp(1.0 - state.batteryAmpSecondsConsumed() / capacityAmpSeconds, 0.0, 1.0);
		state.setBatteryStateOfCharge(stateOfCharge);
		double openCircuitVoltage = config.emptyBatteryVoltage()
				+ (config.nominalBatteryVoltage() - config.emptyBatteryVoltage()) * stateOfCharge;
		double dischargeCurrentAmps = Math.max(0.0, netCurrentAmps);
		double batteryResistanceOhms = temperatureAdjustedBatteryResistanceOhms(state.batteryTemperatureCelsius(), environment.ambientTemperatureCelsius());
		double totalResistanceSag = dischargeCurrentAmps * batteryResistanceOhms;
		double ohmicSag = totalResistanceSag * 0.62;
		double targetTransientSag = totalResistanceSag * 0.38;
		double previousTransientSag = state.batteryTransientSagVoltage();
		double timeConstant = targetTransientSag > previousTransientSag
				? batterySagRiseTimeConstantSeconds()
				: batterySagRecoveryTimeConstantSeconds();
		double alpha = dtSeconds <= 0.0 ? 1.0 : MathUtil.expSmoothing(dtSeconds, timeConstant);
		double transientSag = previousTransientSag + (targetTransientSag - previousTransientSag) * alpha;
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
		state.setBatteryVoltageSpike(voltageSpike);
		state.setBatteryBusRippleVoltage(busRipple);
		double minimumVoltage = config.emptyBatteryVoltage() * 0.85;
		double maximumBusVoltage = config.nominalBatteryVoltage() * 1.12;
		state.setBatteryVoltage(MathUtil.clamp(openCircuitVoltage - ohmicSag - transientSag + voltageSpike - 0.08 * busRipple, minimumVoltage, maximumBusVoltage));
		double stateOfChargeLimit = batteryStateOfChargePowerLimit(stateOfCharge);
		double currentLimit = updateBatteryCurrentLimit(dischargeCurrentAmps, state.batteryTemperatureCelsius(), dtSeconds);
		state.setBatteryPowerLimit(Math.min(Math.min(stateOfChargeLimit, currentLimit), state.batteryThermalLimit()));
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
		double escSwitchingWindow = 0.25 + 0.75 * MathUtil.clamp(state.averageEscOutputCommand() * (1.0 - state.averageEscOutputCommand()) * 4.0, 0.0, 1.0);
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

	private double temperatureAdjustedBatteryResistanceOhms(double batteryTemperatureCelsius, double ambientTemperatureCelsius) {
		double electricalTemperature = 0.78 * batteryTemperatureCelsius + 0.22 * ambientTemperatureCelsius;
		double coldRise = Math.max(0.0, 25.0 - electricalTemperature);
		double heatRise = Math.max(0.0, electricalTemperature - 45.0);
		double scale = 1.0 + 0.024 * coldRise + 0.0045 * heatRise;
		return config.batteryInternalResistanceOhms() * MathUtil.clamp(scale, 0.72, 2.85);
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

	private static double temperatureAdjustedBatteryCurrentScale(double batteryTemperatureCelsius) {
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
