package com.tenicana.dronecraft.sim;

public final class MotorThermalCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double RADIANS_PER_SECOND_TO_RPM = 60.0 / (2.0 * Math.PI);
	private static final double MOTOR_NO_LOAD_OMEGA_SCALE = 1.35;
	private static final double MOTOR_STALL_CURRENT_SCALE = 3.20;
	private static final double REFERENCE_ELECTRICAL_EFFICIENCY = 0.75;
	private static final double REFERENCE_AMBIENT_CELSIUS = 25.0;

	public static final String SOURCE_ID = "Motor-Thermal-Calibration-Packet";
	public static final String CAVEAT =
			"U8 open dyno rows are cross-class BLDC thermal anchors; MQTB is FPV-class hardware metadata, not FPV thermocouple telemetry.";
	public static final int PACKET_ROW_COUNT = 240;
	public static final int SOURCE_INVENTORY_ROW_COUNT = 6;
	public static final int U8_DYNO_SUMMARY_ROW_COUNT = 32;
	public static final int MQTB_METADATA_ROW_COUNT = 10;
	public static final int CURRENT_PRESET_ROW_COUNT = 108;
	public static final int ELECTRICAL_STRESS_ROW_COUNT = 48;
	public static final int RACINGQUAD_CROSSCHECK_ROW_COUNT = 17;
	public static final int COPPER_RESISTANCE_SCALE_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final double MIN_THERMAL_THRUST_LIMIT = 0.45;

	public static final String U8_DYNO_SOURCE_ID = "U8-Kv100-Dyno-Data";
	public static final double U8_24V_TEMPERATURE_SAMPLE_COUNT = 208.0;
	public static final double U8_24V_TEMPERATURE_MEAN_CELSIUS = 31.092133653846155;
	public static final double U8_24V_TEMPERATURE_MAX_CELSIUS = 48.623;
	public static final double U8_24V_LOSS_MEAN_WATTS = 116.26204333333332;
	public static final double U8_24V_LOSS_MAX_WATTS = 406.677;
	public static final double U8_24V_MOTOR_EFFICIENCY_MEAN = 0.37641835443037974;
	public static final double U8_24V_MOTOR_EFFICIENCY_MAX = 0.6587;
	public static final double U8_24V_DRIVER_EFFICIENCY_MEAN = 0.8664711111111111;
	public static final double U8_24V_DRIVER_EFFICIENCY_MAX = 0.9744;
	public static final double U8_24V_RPM_MAX = 1504.7;
	public static final double U8_24V_TORQUE_MAX_NEWTON_METERS = 3.1727;
	public static final double U8_24V_CURRENT_MAX_AMPS = 50.0;
	public static final double U8_24V_MAX_TEMP_CURRENT_AMPS = 50.0;
	public static final double U8_24V_MAX_TEMP_RPM = 900.9041;
	public static final double U8_24V_MAX_TEMP_TORQUE_NEWTON_METERS = 1.4651;
	public static final double U8_36V_TEMPERATURE_SAMPLE_COUNT = 232.0;
	public static final double U8_36V_TEMPERATURE_MEAN_CELSIUS = 32.330050862068966;
	public static final double U8_36V_TEMPERATURE_MAX_CELSIUS = 57.6919;
	public static final double U8_36V_LOSS_MEAN_WATTS = 132.23101237623763;
	public static final double U8_36V_LOSS_MAX_WATTS = 484.1668;
	public static final double U8_36V_MOTOR_EFFICIENCY_MEAN = 0.38233368983957217;
	public static final double U8_36V_MOTOR_EFFICIENCY_MAX = 0.6824;
	public static final double U8_36V_DRIVER_EFFICIENCY_MEAN = 0.8759965346534654;
	public static final double U8_36V_DRIVER_EFFICIENCY_MAX = 0.9795;
	public static final double U8_36V_RPM_MAX = 1703.8609;
	public static final double U8_36V_TORQUE_MAX_NEWTON_METERS = 3.3789;
	public static final double U8_36V_CURRENT_MAX_AMPS = 50.0;
	public static final double U8_36V_MAX_TEMP_CURRENT_AMPS = 50.0;
	public static final double U8_36V_MAX_TEMP_RPM = 0.0;
	public static final double U8_36V_MAX_TEMP_TORQUE_NEWTON_METERS = 3.3789;

	public static final String MQTB_REFERENCE_MOTOR = "Emax Eco 2306 2400kv";
	public static final double MQTB_TESTED_KV_RPM_PER_VOLT = 2300.0;
	public static final double MQTB_MOTOR_WEIGHT_GRAMS = 29.7;
	public static final double MQTB_STATOR_DIAMETER_MILLIMETERS = 23.0;
	public static final double MQTB_STATOR_HEIGHT_MILLIMETERS = 6.0;
	public static final double MQTB_STATOR_VOLUME_PROXY_CUBIC_MILLIMETERS = 2492.8537706235006;
	public static final String MQTB_ESC = "XRotor 40A ESC";
	public static final double MQTB_ESC_CURRENT_RATING_AMPS = 40.0;
	public static final double MQTB_AMBIENT_TEMP_MIN_CELSIUS = 21.11111111111111;
	public static final double MQTB_AMBIENT_TEMP_MAX_CELSIUS = 23.333333333333332;
	public static final double MQTB_LOGGER_RATE_MILLISECONDS = 4.0;

	private MotorThermalCalibration() {
	}

	public record RowTypeCounts(
			int totalRowCount,
			int sourceInventoryRowCount,
			int u8DynoSummaryRowCount,
			int mqtbMetadataRowCount,
			int currentPresetRowCount,
			int electricalStressRowCount,
			int racingQuadCrosscheckRowCount,
			int copperResistanceScaleRowCount,
			int methodRowCount
	) {
	}

	public record U8DynoSummary(
			String referenceId,
			double voltageVolts,
			double temperatureSampleCount,
			double temperatureMeanCelsius,
			double temperatureMaxCelsius,
			double lossMeanWatts,
			double lossMaxWatts,
			double motorEfficiencyMean,
			double motorEfficiencyMax,
			double driverEfficiencyMean,
			double driverEfficiencyMax,
			double rpmMax,
			double torqueMaxNewtonMeters,
			double currentMaxAmps,
			double maxTempCurrentAmps,
			double maxTempRpm,
			double maxTempTorqueNewtonMeters
	) {
	}

	public record MqtbFpvMotorMetadata(
			String referenceMotor,
			double testedKvRpmPerVolt,
			double motorWeightGrams,
			double statorDiameterMillimeters,
			double statorHeightMillimeters,
			double statorVolumeProxyCubicMillimeters,
			String esc,
			double escCurrentRatingAmps,
			double ambientTempMinCelsius,
			double ambientTempMaxCelsius,
			double loggerRateMilliseconds
	) {
	}

	public record CopperResistanceReference(
			double scaleAt0C,
			double scaleAt25C,
			double scaleAt60C,
			double scaleAt95C,
			double scaleAt125C,
			double scaleAt150C,
			double scaleAt180C,
			double scaleAt220C,
			double scaleAt260C
	) {
	}

	public record PresetThermalAudit(
			double thermalRiseCelsiusPerSecond,
			double coolingRatePerSecond,
			double motorLimitCelsius,
			double motorCutoffCelsius,
			double escLimitCelsius,
			double escCutoffCelsius,
			double minThermalThrustLimit,
			double hoverPowerProxy,
			double hoverMotorCoolingFactorProxy,
			double fullMotorCoolingFactorProxy,
			double fullTenMetersPerSecondMotorCoolingFactorProxy,
			double hoverEscCoolingFactorProxy,
			double fullEscCoolingFactorProxy,
			double motorBaseTimeConstantSeconds,
			double motorFullWashTimeConstantSeconds,
			double motorFullTenMetersPerSecondTimeConstantSeconds,
			double motorFullSteadyRiseCelsius,
			double motorHoverSteadyRiseProxyCelsius,
			double escFullCurrentSteadyRiseProxyCelsius,
			double inferredWindingResistance25cOhms,
			double windingResistanceScaleAtLimit,
			double windingResistanceScaleAtCutoff,
			double motorLimitScaleAtMidpoint,
			double escLimitScaleAtMidpoint
	) {
	}

	public record ElectricalStressAudit(
			double inferredKvRpmPerVolt,
			double torqueConstantNewtonMetersPerAmp,
			double perMotorCurrentLimitAmps,
			double hoverPowerCurrentAmps,
			double maxPowerCurrentAmps,
			double maxPowerCurrentOverLimit,
			double hoverPhaseCurrentProxyAmps,
			double maxPhaseCurrentProxyAmps,
			double hoverVoltageHeadroom,
			double maxVoltageHeadroom,
			double maxDesyncHeadroomStress
	) {
	}

	public record RacingQuadCrosscheck(
			double referenceAmbientCelsius,
			double u8OpenDynoMaxTemperatureCelsius,
			double u8OpenDynoMaxLossWatts,
			double u8MaxTemperatureRiseVs25c,
			double currentFullPowerMotorAbsoluteTempAt25c,
			double currentHoverMotorAbsoluteTempAt25c,
			double currentFullEscAbsoluteTempAt25c,
			double currentFullMotorRiseOverMotorLimitMargin,
			double currentFullMotorRiseOverMotorCutoffMargin,
			double currentFullEscRiseOverEscLimitMargin,
			double currentFullEscRiseOverEscCutoffMargin,
			double currentFullMotorRiseOverU8MaxRise,
			double currentHoverMotorRiseOverU8MaxRise,
			double currentInferredKvOverMqtbTestedKv,
			double currentPerMotorLimitOverMqtbEscRating,
			double currentMaxPowerCurrentOverLimit,
			double currentMaxPowerCurrentAmps
	) {
	}

	public record MotorThermalAudit(
			String sourceId,
			String caveat,
			RowTypeCounts rowTypeCounts,
			U8DynoSummary u8Dyno24v,
			U8DynoSummary u8Dyno36v,
			MqtbFpvMotorMetadata mqtbMetadata,
			CopperResistanceReference copperResistance,
			PresetThermalAudit preset,
			ElectricalStressAudit electricalStress,
			RacingQuadCrosscheck racingQuadCrosscheck
	) {
	}

	public static MotorThermalAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		PresetThermalAudit preset = presetThermalAudit(config);
		ElectricalStressAudit electrical = electricalStressAudit(config);
		return new MotorThermalAudit(
				SOURCE_ID,
				CAVEAT,
				rowTypeCounts(),
				u8Dyno24v(),
				u8Dyno36v(),
				mqtbMetadata(),
				copperResistanceReference(),
				preset,
				electrical,
				racingQuadCrosscheck(preset, electrical)
		);
	}

	public static double copperWindingResistanceScale(double temperatureCelsius) {
		if (!Double.isFinite(temperatureCelsius)) {
			return 1.0;
		}
		return MathUtil.clamp(1.0 + 0.0039 * (temperatureCelsius - REFERENCE_AMBIENT_CELSIUS), 0.72, 1.90);
	}

	public static double thermalLimitScale(double temperatureCelsius, double limitCelsius, double cutoffCelsius) {
		if (!Double.isFinite(temperatureCelsius)
				|| !Double.isFinite(limitCelsius)
				|| !Double.isFinite(cutoffCelsius)
				|| cutoffCelsius <= limitCelsius) {
			return 1.0;
		}
		if (temperatureCelsius <= limitCelsius) {
			return 1.0;
		}
		if (temperatureCelsius >= cutoffCelsius) {
			return MIN_THERMAL_THRUST_LIMIT;
		}
		double t = (temperatureCelsius - limitCelsius) / (cutoffCelsius - limitCelsius);
		double smooth = t * t * (3.0 - 2.0 * t);
		return 1.0 - (1.0 - MIN_THERMAL_THRUST_LIMIT) * smooth;
	}

	public static double motorCoolingFactorProxy(double power, double escOutput, double densityRatio, double airspeedMetersPerSecond) {
		double freestreamCooling = MathUtil.clamp(airspeedMetersPerSecond / 18.0, 0.0, 1.8);
		double rotorWashCooling = 0.92
				* MathUtil.clamp(power, 0.0, 1.2)
				* (0.45 + 0.55 * MathUtil.clamp(escOutput, 0.0, 1.2));
		return MathUtil.clamp(
				(1.0 + freestreamCooling + rotorWashCooling)
						* MathUtil.clamp(densityRatio, 0.35, 1.35),
				0.20,
				4.0
		);
	}

	public static double escCoolingFactorProxy(
			double motorCoolingFactor,
			double power,
			double escOutput,
			double densityRatio
	) {
		double rotorWashCooling = 0.45
				* MathUtil.clamp(power, 0.0, 1.2)
				* (0.35 + 0.65 * MathUtil.clamp(escOutput, 0.0, 1.2));
		double boardAirflow = 0.58 + 0.42 * MathUtil.clamp(motorCoolingFactor, 0.20, 4.0) + rotorWashCooling;
		return MathUtil.clamp(boardAirflow * MathUtil.clamp(densityRatio, 0.35, 1.35), 0.20, 4.0);
	}

	private static RowTypeCounts rowTypeCounts() {
		return new RowTypeCounts(
				PACKET_ROW_COUNT,
				SOURCE_INVENTORY_ROW_COUNT,
				U8_DYNO_SUMMARY_ROW_COUNT,
				MQTB_METADATA_ROW_COUNT,
				CURRENT_PRESET_ROW_COUNT,
				ELECTRICAL_STRESS_ROW_COUNT,
				RACINGQUAD_CROSSCHECK_ROW_COUNT,
				COPPER_RESISTANCE_SCALE_ROW_COUNT,
				METHOD_ROW_COUNT
		);
	}

	private static U8DynoSummary u8Dyno24v() {
		return new U8DynoSummary(
				U8_DYNO_SOURCE_ID,
				24.0,
				U8_24V_TEMPERATURE_SAMPLE_COUNT,
				U8_24V_TEMPERATURE_MEAN_CELSIUS,
				U8_24V_TEMPERATURE_MAX_CELSIUS,
				U8_24V_LOSS_MEAN_WATTS,
				U8_24V_LOSS_MAX_WATTS,
				U8_24V_MOTOR_EFFICIENCY_MEAN,
				U8_24V_MOTOR_EFFICIENCY_MAX,
				U8_24V_DRIVER_EFFICIENCY_MEAN,
				U8_24V_DRIVER_EFFICIENCY_MAX,
				U8_24V_RPM_MAX,
				U8_24V_TORQUE_MAX_NEWTON_METERS,
				U8_24V_CURRENT_MAX_AMPS,
				U8_24V_MAX_TEMP_CURRENT_AMPS,
				U8_24V_MAX_TEMP_RPM,
				U8_24V_MAX_TEMP_TORQUE_NEWTON_METERS
		);
	}

	private static U8DynoSummary u8Dyno36v() {
		return new U8DynoSummary(
				U8_DYNO_SOURCE_ID,
				36.0,
				U8_36V_TEMPERATURE_SAMPLE_COUNT,
				U8_36V_TEMPERATURE_MEAN_CELSIUS,
				U8_36V_TEMPERATURE_MAX_CELSIUS,
				U8_36V_LOSS_MEAN_WATTS,
				U8_36V_LOSS_MAX_WATTS,
				U8_36V_MOTOR_EFFICIENCY_MEAN,
				U8_36V_MOTOR_EFFICIENCY_MAX,
				U8_36V_DRIVER_EFFICIENCY_MEAN,
				U8_36V_DRIVER_EFFICIENCY_MAX,
				U8_36V_RPM_MAX,
				U8_36V_TORQUE_MAX_NEWTON_METERS,
				U8_36V_CURRENT_MAX_AMPS,
				U8_36V_MAX_TEMP_CURRENT_AMPS,
				U8_36V_MAX_TEMP_RPM,
				U8_36V_MAX_TEMP_TORQUE_NEWTON_METERS
		);
	}

	private static MqtbFpvMotorMetadata mqtbMetadata() {
		return new MqtbFpvMotorMetadata(
				MQTB_REFERENCE_MOTOR,
				MQTB_TESTED_KV_RPM_PER_VOLT,
				MQTB_MOTOR_WEIGHT_GRAMS,
				MQTB_STATOR_DIAMETER_MILLIMETERS,
				MQTB_STATOR_HEIGHT_MILLIMETERS,
				MQTB_STATOR_VOLUME_PROXY_CUBIC_MILLIMETERS,
				MQTB_ESC,
				MQTB_ESC_CURRENT_RATING_AMPS,
				MQTB_AMBIENT_TEMP_MIN_CELSIUS,
				MQTB_AMBIENT_TEMP_MAX_CELSIUS,
				MQTB_LOGGER_RATE_MILLISECONDS
		);
	}

	private static CopperResistanceReference copperResistanceReference() {
		return new CopperResistanceReference(
				copperWindingResistanceScale(0.0),
				copperWindingResistanceScale(25.0),
				copperWindingResistanceScale(60.0),
				copperWindingResistanceScale(95.0),
				copperWindingResistanceScale(125.0),
				copperWindingResistanceScale(150.0),
				copperWindingResistanceScale(180.0),
				copperWindingResistanceScale(220.0),
				copperWindingResistanceScale(260.0)
		);
	}

	private static PresetThermalAudit presetThermalAudit(DroneConfig config) {
		double thermalRise = config.motorThermalRiseCelsiusPerSecond();
		double coolingRate = config.motorCoolingRatePerSecond();
		double motorLimit = config.motorThermalLimitCelsius();
		double motorCutoff = config.motorThermalCutoffCelsius();
		double escLimit = Math.max(30.0, motorLimit - 5.0);
		double escCutoff = Math.max(escLimit + 1.0, motorCutoff - 5.0);
		double hoverPower = averageHoverPowerProxy(config);
		double hoverMotorCooling = motorCoolingFactorProxy(hoverPower, hoverPower, 1.0, 0.0);
		double fullMotorCooling = motorCoolingFactorProxy(1.0, 1.0, 1.0, 0.0);
		double fullAirspeedMotorCooling = motorCoolingFactorProxy(1.0, 1.0, 1.0, 10.0);
		double hoverEscCooling = escCoolingFactorProxy(hoverMotorCooling, hoverPower, hoverPower, 1.0);
		double fullEscCooling = escCoolingFactorProxy(fullMotorCooling, 1.0, 1.0, 1.0);
		double fullRise = ratio(thermalRise, coolingRate * fullMotorCooling);
		double hoverRise = ratio(thermalRise * hoverPower * hoverPower, coolingRate * hoverMotorCooling);
		double escRise = ratio(thermalRise * 0.72 * 0.62, coolingRate * 0.90 * fullEscCooling);
		double inferredWindingResistance = inferredWindingResistance25cOhms(config);
		return new PresetThermalAudit(
				thermalRise,
				coolingRate,
				motorLimit,
				motorCutoff,
				escLimit,
				escCutoff,
				MIN_THERMAL_THRUST_LIMIT,
				hoverPower,
				hoverMotorCooling,
				fullMotorCooling,
				fullAirspeedMotorCooling,
				hoverEscCooling,
				fullEscCooling,
				ratio(1.0, coolingRate),
				ratio(1.0, coolingRate * fullMotorCooling),
				ratio(1.0, coolingRate * fullAirspeedMotorCooling),
				fullRise,
				hoverRise,
				escRise,
				inferredWindingResistance,
				copperWindingResistanceScale(motorLimit),
				copperWindingResistanceScale(motorCutoff),
				thermalLimitScale((motorLimit + motorCutoff) * 0.5, motorLimit, motorCutoff),
				thermalLimitScale((escLimit + escCutoff) * 0.5, escLimit, escCutoff)
		);
	}

	private static ElectricalStressAudit electricalStressAudit(DroneConfig config) {
		double averageMaxOmega = averageMaxOmegaRadiansPerSecond(config);
		double kvRadiansPerSecondPerVolt = averageMaxOmega * MOTOR_NO_LOAD_OMEGA_SCALE
				/ Math.max(1.0, config.nominalBatteryVoltage());
		double kvRpmPerVolt = kvRadiansPerSecondPerVolt * RADIANS_PER_SECOND_TO_RPM;
		double torqueConstant = kvRadiansPerSecondPerVolt <= EPSILON ? 0.0 : 1.0 / kvRadiansPerSecondPerVolt;
		double perMotorCurrentLimit = config.maxBatteryCurrentAmps() / config.rotors().size();
		double hoverOmega = averageHoverOmegaRadiansPerSecond(config);
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double maxThrust = averageMaxRotorThrustNewtons(config);
		double yawTorquePerThrust = averageYawTorquePerThrustMeters(config);
		double hoverTorque = yawTorquePerThrust * hoverThrust;
		double maxTorque = yawTorquePerThrust * maxThrust;
		double hoverShaftPower = hoverTorque * hoverOmega;
		double maxShaftPower = maxTorque * averageMaxOmega;
		double hoverCurrent = ratio(
				hoverShaftPower,
				Math.max(1.0, config.nominalBatteryVoltage() * REFERENCE_ELECTRICAL_EFFICIENCY)
		);
		double maxCurrent = ratio(
				maxShaftPower,
				Math.max(1.0, config.nominalBatteryVoltage() * REFERENCE_ELECTRICAL_EFFICIENCY)
		);
		double hoverPhaseCurrent = ratio(hoverTorque, torqueConstant);
		double maxPhaseCurrent = ratio(maxTorque, torqueConstant);
		double hoverBackEmf = ratio(hoverOmega, kvRadiansPerSecondPerVolt);
		double maxBackEmf = ratio(averageMaxOmega, kvRadiansPerSecondPerVolt);
		double hoverHeadroom = motorVoltageHeadroomFromDrive(hoverBackEmf, config.nominalBatteryVoltage());
		double maxHeadroom = motorVoltageHeadroomFromDrive(maxBackEmf, config.nominalBatteryVoltage());
		double maxDesyncStress = 1.0 - smoothStep(0.08, 0.36, maxHeadroom);
		return new ElectricalStressAudit(
				kvRpmPerVolt,
				torqueConstant,
				perMotorCurrentLimit,
				hoverCurrent,
				maxCurrent,
				ratio(maxCurrent, perMotorCurrentLimit),
				hoverPhaseCurrent,
				maxPhaseCurrent,
				hoverHeadroom,
				maxHeadroom,
				maxDesyncStress
		);
	}

	private static RacingQuadCrosscheck racingQuadCrosscheck(PresetThermalAudit preset, ElectricalStressAudit electrical) {
		double u8MaxTemperature = Math.max(U8_24V_TEMPERATURE_MAX_CELSIUS, U8_36V_TEMPERATURE_MAX_CELSIUS);
		double u8MaxLoss = Math.max(U8_24V_LOSS_MAX_WATTS, U8_36V_LOSS_MAX_WATTS);
		double u8MaxRise = u8MaxTemperature - REFERENCE_AMBIENT_CELSIUS;
		return new RacingQuadCrosscheck(
				REFERENCE_AMBIENT_CELSIUS,
				u8MaxTemperature,
				u8MaxLoss,
				u8MaxRise,
				REFERENCE_AMBIENT_CELSIUS + preset.motorFullSteadyRiseCelsius(),
				REFERENCE_AMBIENT_CELSIUS + preset.motorHoverSteadyRiseProxyCelsius(),
				REFERENCE_AMBIENT_CELSIUS + preset.escFullCurrentSteadyRiseProxyCelsius(),
				ratio(preset.motorFullSteadyRiseCelsius(), preset.motorLimitCelsius() - REFERENCE_AMBIENT_CELSIUS),
				ratio(preset.motorFullSteadyRiseCelsius(), preset.motorCutoffCelsius() - REFERENCE_AMBIENT_CELSIUS),
				ratio(preset.escFullCurrentSteadyRiseProxyCelsius(), preset.escLimitCelsius() - REFERENCE_AMBIENT_CELSIUS),
				ratio(preset.escFullCurrentSteadyRiseProxyCelsius(), preset.escCutoffCelsius() - REFERENCE_AMBIENT_CELSIUS),
				ratio(preset.motorFullSteadyRiseCelsius(), u8MaxRise),
				ratio(preset.motorHoverSteadyRiseProxyCelsius(), u8MaxRise),
				ratio(electrical.inferredKvRpmPerVolt(), MQTB_TESTED_KV_RPM_PER_VOLT),
				ratio(electrical.perMotorCurrentLimitAmps(), MQTB_ESC_CURRENT_RATING_AMPS),
				electrical.maxPowerCurrentOverLimit(),
				electrical.maxPowerCurrentAmps()
		);
	}

	private static double inferredWindingResistance25cOhms(DroneConfig config) {
		double perMotorMaxCurrent = config.maxBatteryCurrentAmps() / config.rotors().size();
		double stallCurrent = Math.max(1.0, perMotorMaxCurrent * MOTOR_STALL_CURRENT_SCALE);
		return MathUtil.clamp(config.nominalBatteryVoltage() / stallCurrent, 0.025, 2.5);
	}

	private static double averageHoverPowerProxy(DroneConfig config) {
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += hoverThrust / rotor.maxThrustNewtons();
		}
		return MathUtil.clamp(total / config.rotors().size(), 0.0, 1.2);
	}

	private static double averageMaxRotorThrustNewtons(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxThrustNewtons();
		}
		return total / config.rotors().size();
	}

	private static double averageMaxOmegaRadiansPerSecond(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxOmegaRadiansPerSecond();
		}
		return total / config.rotors().size();
	}

	private static double averageHoverOmegaRadiansPerSecond(DroneConfig config) {
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += radiansPerSecondForThrustAndCoefficient(hoverThrust, rotor.thrustCoefficient());
		}
		return total / config.rotors().size();
	}

	private static double averageYawTorquePerThrustMeters(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.yawTorquePerThrustMeter();
		}
		return total / config.rotors().size();
	}

	private static double radiansPerSecondForThrustAndCoefficient(double thrustNewtons, double thrustCoefficient) {
		if (!Double.isFinite(thrustNewtons)
				|| !Double.isFinite(thrustCoefficient)
				|| thrustNewtons <= 0.0
				|| thrustCoefficient <= 0.0) {
			return 0.0;
		}
		return Math.sqrt(thrustNewtons / thrustCoefficient);
	}

	private static double motorVoltageHeadroomFromDrive(double backEmfVolts, double driveVoltageVolts) {
		if (driveVoltageVolts <= EPSILON) {
			return 0.0;
		}
		return MathUtil.clamp((driveVoltageVolts - backEmfVolts) / driveVoltageVolts, 0.0, 1.0);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
