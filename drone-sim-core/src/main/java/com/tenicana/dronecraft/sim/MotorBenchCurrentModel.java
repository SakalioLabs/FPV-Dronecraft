package com.tenicana.dronecraft.sim;

public final class MotorBenchCurrentModel {
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_A = 1.4297926376886003;
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_B = 1.1578910573663044;
	public static final double MQTB_HQ5X4X3_POWER_FIT_A = 23.004245948867702;
	public static final double MQTB_HQ5X4X3_POWER_FIT_B = 1.150640372104493;
	public static final double MQTB_HQ5X4X3_RADIUS_METERS = 0.0635;
	public static final double MQTB_HQ5X4X3_PITCH_TO_DIAMETER_RATIO = 0.80;
	public static final int MQTB_HQ5X4X3_BLADE_COUNT = 3;
	public static final String TYTO_X3NM_SOURCE_ID = "x3nm";
	public static final double TYTO_X3NM_MAX_THRUST_NEWTONS = 12.547278947987;
	public static final double TYTO_X3NM_MAX_CURRENT_AMPS = 22.185563411713;
	public static final double TYTO_X3NM_VOLTAGE_AT_MAX_THRUST = 24.213822555542;
	public static final double TYTO_X3NM_FIT_THRUST_COEFFICIENT = 1.7996539842396274e-6;
	public static final double TYTO_X3NM_FIT_R2 = 0.9988870109198886;
	public static final int TYTO_X3NM_FIT_POINT_COUNT = 7;
	public static final double TYTO_X3NM_FIT_Q_OVER_T_METERS = 0.0113854299885362;
	public static final double TYTO_X3NM_TORQUE_RATIO_FIT_R2 = 0.99991803288615;
	public static final int TYTO_X3NM_TORQUE_RATIO_FIT_POINT_COUNT = 7;
	public static final double TYTO_X3NM_HIGH_THRUST_Q_OVER_T_MEAN_METERS = 0.011369775044516606;
	public static final double TYTO_X3NM_Q_OVER_T_AT_MAX_THRUST_METERS = 0.011385548298033902;
	public static final String TYTO_DNQ_SOURCE_ID = "dnq";
	public static final double TYTO_DNQ_FIT_Q_OVER_T_METERS = 0.014586521016335946;
	public static final double TYTO_DNQ_TORQUE_RATIO_FIT_R2 = 0.9996491957508717;
	public static final int TYTO_DNQ_TORQUE_RATIO_FIT_POINT_COUNT = 14;
	public static final double TYTO_DNQ_HIGH_THRUST_Q_OVER_T_MEAN_METERS = 0.0145697672667851;
	public static final double TYTO_DNQ_Q_OVER_T_AT_MAX_THRUST_METERS = 0.014512651125968636;
	public static final String APDRONE_MOTOR_PDF_SOURCE_ID = "YSIDO-2507-1800KV";
	public static final double APDRONE_MOTOR_PDF_KV_RPM_PER_VOLT = 1800.0;
	public static final double APDRONE_BETAFLIGHT_KV_RPM_PER_VOLT = 1960.0;
	public static final double APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS =
			DroneConfig.APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS;
	public static final double APDRONE_MOTOR_PDF_CONTINUOUS_CURRENT_AMPS = 42.0;
	public static final double APDRONE_MOTOR_PDF_HEADLINE_MAX_THRUST_NEWTONS = 14.5922952;
	public static final double APDRONE_MOTOR_PDF_BEST_VISIBLE_MAX_THRUST_NEWTONS = 14.1608026;
	public static final double APDRONE_MOTOR_PDF_BEST_VISIBLE_MAX_CURRENT_AMPS = 32.16;
	public static final double APDRONE_MOTOR_PDF_BEST_VISIBLE_MAX_VOLTAGE_VOLTS = 24.39;
	public static final double APDRONE_BATTERY_FILENAME_NOMINAL_VOLTAGE_VOLTS = 14.8;
	public static final String AIIO_ROTOR_SPEED_SOURCE_ID = "AI-IO";
	public static final int AIIO_EXTRACTED_TEST_SAMPLE_FILE_COUNT = 22;
	public static final double AIIO_HDF5_SAMPLE_RATE_HERTZ = 100.00009536752259;
	public static final double AIIO_HDF5_TELEMETRY_NYQUIST_HERTZ = 50.000047683761295;
	public static final double AIIO_FASTEST_TEST_SPEED_METERS_PER_SECOND = 13.597415180566555;
	public static final double AIIO_ROTOR_RPM_P95_OF_FILE_PEAKS = 24245.16376198689;
	public static final double AIIO_ROTOR_RPM_MAX = 29146.829122720956;
	public static final double AIIO_THREE_BLADE_BLADE_PASS_HERTZ_AT_MAX = 1457.3414561360478;
	public static final int AIIO_LOW_DYNAMIC_STRICT_SAMPLE_COUNT = 37089;
	public static final int AIIO_LOW_DYNAMIC_STRICT_SAMPLE_FILE_COUNT = 20;
	public static final double AIIO_LOW_DYNAMIC_STRICT_DURATION_SECONDS = 370.8896462917328;
	public static final double AIIO_LOW_DYNAMIC_STRICT_MAX_SPEED_METERS_PER_SECOND = 1.0;
	public static final double AIIO_LOW_DYNAMIC_STRICT_MAX_GROUND_ACCELERATION_METERS_PER_SECOND_SQUARED = 1.5;
	public static final double AIIO_LOW_DYNAMIC_STRICT_MAX_GYRO_NORM_RADIANS_PER_SECOND = 0.5;
	public static final double AIIO_LOW_DYNAMIC_STRICT_SPEED_MEAN_METERS_PER_SECOND = 0.5199172711044272;
	public static final double AIIO_LOW_DYNAMIC_STRICT_SPEED_P95_METERS_PER_SECOND = 0.9754606713859196;
	public static final double AIIO_LOW_DYNAMIC_STRICT_GROUND_ACCELERATION_P95_METERS_PER_SECOND_SQUARED =
			1.2580811125853344;
	public static final double AIIO_LOW_DYNAMIC_STRICT_GYRO_NORM_P95_RADIANS_PER_SECOND = 0.4236720246096596;
	public static final double AIIO_LOW_DYNAMIC_STRICT_THROTTLE_MEAN = 0.2870552036315011;
	public static final double AIIO_LOW_DYNAMIC_STRICT_THROTTLE_P95 = 0.30747738541591185;
	public static final double AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_MEAN = 13642.489652165377;
	public static final double AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_P50 = 13791.514254986312;
	public static final double AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_P95 = 14119.280967371637;
	public static final double AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_MAX = 15192.087569316081;
	public static final double AIIO_LOW_DYNAMIC_STRICT_THREE_BLADE_BLADE_PASS_HERTZ_AT_MEAN = 682.1244826082689;

	private static final double RADIANS_PER_SECOND_TO_RPM = 60.0 / (2.0 * Math.PI);

	private MotorBenchCurrentModel() {
	}

	public record StaticPowertrainAudit(
			String referenceId,
			double configuredMaxRotorThrustNewtons,
			double configuredThrustCoefficient,
			double configuredMaxRpm,
			double referenceMaxThrustNewtons,
			double referenceMaxCurrentAmps,
			double referenceVoltageAtMaxThrust,
			double referenceThrustCoefficient,
			double referenceFitR2,
			int referenceFitPointCount,
			double referenceRpmAtMaxThrust,
			double referenceEquivalentRpmForConfiguredMaxThrust,
			double configuredMaxThrustOverReference,
			double configuredThrustCoefficientOverReference
	) {
	}

	public record RotorSpeedTelemetryAudit(
			String referenceId,
			int referenceSampleFileCount,
			double referenceSampleRateHertz,
			double referenceTelemetryNyquistHertz,
			double referenceFastestSpeedMetersPerSecond,
			double referenceRotorRpmP95OfFilePeaks,
			double referenceMaxRotorRpm,
			double configuredHoverRotorRpm,
			double configuredMaxRotorRpm,
			double referenceMaxRotorRpmOverConfiguredMax,
			double configuredMaxRotorRpmOverReferenceMax,
			int lowDynamicSampleCount,
			int lowDynamicSampleFileCount,
			double lowDynamicDurationSeconds,
			double lowDynamicMaxSpeedMetersPerSecond,
			double lowDynamicMaxGroundAccelerationMetersPerSecondSquared,
			double lowDynamicMaxGyroNormRadiansPerSecond,
			double lowDynamicSpeedMeanMetersPerSecond,
			double lowDynamicSpeedP95MetersPerSecond,
			double lowDynamicGroundAccelerationP95MetersPerSecondSquared,
			double lowDynamicGyroNormP95RadiansPerSecond,
			double lowDynamicThrottleMean,
			double lowDynamicThrottleP95,
			double lowDynamicRotorRpmMean,
			double lowDynamicRotorRpmP50,
			double lowDynamicRotorRpmP95,
			double lowDynamicRotorRpmMax,
			double lowDynamicRotorRpmMeanOverConfiguredHover,
			double lowDynamicRotorRpmP50OverConfiguredHover,
			double lowDynamicRotorRpmP95OverConfiguredMax,
			double lowDynamicMeanBladePassHertzForConfiguredBladeCount,
			double lowDynamicThreeBladeBladePassHertzAtMean,
			double configuredBladeCount,
			double referenceBladePassHertzForConfiguredBladeCount,
			double configuredMaxBladePassHertz,
			double referenceThreeBladeBladePassHertz,
			double referenceBladePassOverTelemetryNyquist
	) {
	}

	public record StaticYawTorqueAudit(
			double configuredYawTorquePerThrustMeter,
			String lowTorqueReferenceId,
			double lowTorqueReferenceFitQOverTMeters,
			double lowTorqueReferenceFitR2,
			int lowTorqueReferenceFitPointCount,
			double lowTorqueReferenceHighThrustMeanQOverTMeters,
			double lowTorqueReferenceQOverTAtMaxThrustMeters,
			double configuredOverLowTorqueReferenceFit,
			double lowTorqueReferenceFitOverConfigured,
			double lowTorqueReferenceAtMaxThrustOverConfigured,
			String highTorqueReferenceId,
			double highTorqueReferenceFitQOverTMeters,
			double highTorqueReferenceFitR2,
			int highTorqueReferenceFitPointCount,
			double highTorqueReferenceHighThrustMeanQOverTMeters,
			double highTorqueReferenceQOverTAtMaxThrustMeters,
			double configuredOverHighTorqueReferenceFit,
			double highTorqueReferenceFitOverConfigured,
			double highTorqueReferenceAtMaxThrustOverConfigured,
			double referenceFitWindowMinMeters,
			double referenceFitWindowMaxMeters,
			double configuredPositionWithinReferenceFitWindow
	) {
	}

	public record ApDroneMotorSpecAudit(
			String referenceId,
			double configuredMaxRotorThrustNewtons,
			double configuredMaxRpm,
			double configuredMotorWindingResistanceOhms,
			double configuredPerMotorPackCurrentAmps,
			double referenceKvRpmPerVolt,
			double betaflightKvRpmPerVolt,
			double referenceMotorWindingResistanceOhms,
			double referenceContinuousCurrentAmps,
			double referenceHeadlineMaxThrustNewtons,
			double referenceBestVisibleMaxThrustNewtons,
			double referenceBestVisibleMaxCurrentAmps,
			double referenceBestVisibleMaxVoltageVolts,
			double configuredMaxThrustOverReferenceHeadline,
			double configuredMaxThrustOverReferenceBestVisible,
			double configuredMotorWindingResistanceOverReference,
			double configuredPerMotorPackCurrentOverReferenceContinuous,
			double configuredMaxRpmOverReferenceKvFullCharge,
			double configuredMaxRpmOverReferenceKvNominal,
			double configuredMaxRpmOverBetaflightKvFullCharge,
			double configuredMaxRpmOverBetaflightKvNominal
	) {
	}

	public static double mqtbHq5x4x3CurrentAmpsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, MQTB_HQ5X4X3_CURRENT_FIT_A, MQTB_HQ5X4X3_CURRENT_FIT_B);
	}

	public static double mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, MQTB_HQ5X4X3_POWER_FIT_A, MQTB_HQ5X4X3_POWER_FIT_B);
	}

	public static double mqtbHq5x4x3TotalCurrentAmps(DroneState state) {
		double total = 0.0;
		for (double thrust : state.rotorThrustNewtons()) {
			total += mqtbHq5x4x3CurrentAmpsForThrustNewtons(thrust);
		}
		return total;
	}

	public static double mqtbHq5x4x3TotalElectricalPowerWatts(DroneState state) {
		double total = 0.0;
		for (double thrust : state.rotorThrustNewtons()) {
			total += mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(thrust);
		}
		return total;
	}

	public static double totalMotorCurrentAmps(DroneState state) {
		double total = 0.0;
		for (double current : state.motorCurrentAmps()) {
			total += current;
		}
		return total;
	}

	public static double mqtbHq5x4x3CurrentRatio(DroneState state) {
		double referenceCurrent = mqtbHq5x4x3TotalCurrentAmps(state);
		if (referenceCurrent <= 1.0e-9) {
			return 0.0;
		}
		return totalMotorCurrentAmps(state) / referenceCurrent;
	}

	public static double mqtbHq5x4x3CurrentResidualAmps(DroneState state) {
		return totalMotorCurrentAmps(state) - mqtbHq5x4x3TotalCurrentAmps(state);
	}

	public static double mqtbHq5x4x3RotorSimilarity(RotorSpec rotor) {
		if (rotor == null) {
			return 0.0;
		}

		double radiusWeight = plateauWindow(rotor.radiusMeters(), MQTB_HQ5X4X3_RADIUS_METERS, 0.010, 0.025);
		double pitchWeight = plateauWindow(
				rotor.bladePitchToDiameterRatio(),
				MQTB_HQ5X4X3_PITCH_TO_DIAMETER_RATIO,
				0.075,
				0.22
		);
		double bladeWeight = 1.0 - MathUtil.clamp(
				Math.abs(rotor.bladeCount() - MQTB_HQ5X4X3_BLADE_COUNT),
				0.0,
				1.0
		);
		return MathUtil.clamp(radiusWeight * pitchWeight * bladeWeight, 0.0, 1.0);
	}

	public static StaticPowertrainAudit tytoX3nmStaticPowertrainAudit(DroneConfig config) {
		double configuredMaxThrustNewtons = averageMaxRotorThrustNewtons(config);
		double configuredThrustCoefficient = averageThrustCoefficient(config);
		double configuredMaxRpm = averageMaxRotorRpm(config);
		double referenceRpmAtMaxThrust = rpmForThrustAndCoefficient(
				TYTO_X3NM_MAX_THRUST_NEWTONS,
				TYTO_X3NM_FIT_THRUST_COEFFICIENT
		);
		double referenceEquivalentRpmForConfiguredMaxThrust = rpmForThrustAndCoefficient(
				configuredMaxThrustNewtons,
				TYTO_X3NM_FIT_THRUST_COEFFICIENT
		);
		return new StaticPowertrainAudit(
				TYTO_X3NM_SOURCE_ID,
				configuredMaxThrustNewtons,
				configuredThrustCoefficient,
				configuredMaxRpm,
				TYTO_X3NM_MAX_THRUST_NEWTONS,
				TYTO_X3NM_MAX_CURRENT_AMPS,
				TYTO_X3NM_VOLTAGE_AT_MAX_THRUST,
				TYTO_X3NM_FIT_THRUST_COEFFICIENT,
				TYTO_X3NM_FIT_R2,
				TYTO_X3NM_FIT_POINT_COUNT,
				referenceRpmAtMaxThrust,
				referenceEquivalentRpmForConfiguredMaxThrust,
				ratio(configuredMaxThrustNewtons, TYTO_X3NM_MAX_THRUST_NEWTONS),
				ratio(configuredThrustCoefficient, TYTO_X3NM_FIT_THRUST_COEFFICIENT)
		);
	}

	public static RotorSpeedTelemetryAudit aiioRotorSpeedTelemetryAudit(DroneConfig config) {
		double configuredMaxRotorRpm = averageMaxRotorRpm(config);
		double configuredHoverRotorRpm = averageHoverRotorRpm(config);
		double configuredBladeCount = averageBladeCount(config);
		double referenceBladePass = AIIO_ROTOR_RPM_MAX * configuredBladeCount / 60.0;
		double configuredMaxBladePass = configuredMaxRotorRpm * configuredBladeCount / 60.0;
		double lowDynamicMeanBladePass = AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_MEAN * configuredBladeCount / 60.0;
		return new RotorSpeedTelemetryAudit(
				AIIO_ROTOR_SPEED_SOURCE_ID,
				AIIO_EXTRACTED_TEST_SAMPLE_FILE_COUNT,
				AIIO_HDF5_SAMPLE_RATE_HERTZ,
				AIIO_HDF5_TELEMETRY_NYQUIST_HERTZ,
				AIIO_FASTEST_TEST_SPEED_METERS_PER_SECOND,
				AIIO_ROTOR_RPM_P95_OF_FILE_PEAKS,
				AIIO_ROTOR_RPM_MAX,
				configuredHoverRotorRpm,
				configuredMaxRotorRpm,
				ratio(AIIO_ROTOR_RPM_MAX, configuredMaxRotorRpm),
				ratio(configuredMaxRotorRpm, AIIO_ROTOR_RPM_MAX),
				AIIO_LOW_DYNAMIC_STRICT_SAMPLE_COUNT,
				AIIO_LOW_DYNAMIC_STRICT_SAMPLE_FILE_COUNT,
				AIIO_LOW_DYNAMIC_STRICT_DURATION_SECONDS,
				AIIO_LOW_DYNAMIC_STRICT_MAX_SPEED_METERS_PER_SECOND,
				AIIO_LOW_DYNAMIC_STRICT_MAX_GROUND_ACCELERATION_METERS_PER_SECOND_SQUARED,
				AIIO_LOW_DYNAMIC_STRICT_MAX_GYRO_NORM_RADIANS_PER_SECOND,
				AIIO_LOW_DYNAMIC_STRICT_SPEED_MEAN_METERS_PER_SECOND,
				AIIO_LOW_DYNAMIC_STRICT_SPEED_P95_METERS_PER_SECOND,
				AIIO_LOW_DYNAMIC_STRICT_GROUND_ACCELERATION_P95_METERS_PER_SECOND_SQUARED,
				AIIO_LOW_DYNAMIC_STRICT_GYRO_NORM_P95_RADIANS_PER_SECOND,
				AIIO_LOW_DYNAMIC_STRICT_THROTTLE_MEAN,
				AIIO_LOW_DYNAMIC_STRICT_THROTTLE_P95,
				AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_MEAN,
				AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_P50,
				AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_P95,
				AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_MAX,
				ratio(AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_MEAN, configuredHoverRotorRpm),
				ratio(AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_P50, configuredHoverRotorRpm),
				ratio(AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_P95, configuredMaxRotorRpm),
				lowDynamicMeanBladePass,
				AIIO_LOW_DYNAMIC_STRICT_THREE_BLADE_BLADE_PASS_HERTZ_AT_MEAN,
				configuredBladeCount,
				referenceBladePass,
				configuredMaxBladePass,
				AIIO_THREE_BLADE_BLADE_PASS_HERTZ_AT_MAX,
				ratio(AIIO_THREE_BLADE_BLADE_PASS_HERTZ_AT_MAX, AIIO_HDF5_TELEMETRY_NYQUIST_HERTZ)
		);
	}

	public static StaticYawTorqueAudit tytoStaticYawTorqueAudit(DroneConfig config) {
		double configuredYawTorque = averageYawTorquePerThrustMeter(config);
		double minFit = Math.min(TYTO_X3NM_FIT_Q_OVER_T_METERS, TYTO_DNQ_FIT_Q_OVER_T_METERS);
		double maxFit = Math.max(TYTO_X3NM_FIT_Q_OVER_T_METERS, TYTO_DNQ_FIT_Q_OVER_T_METERS);
		double windowPosition = ratio(configuredYawTorque - minFit, maxFit - minFit);
		return new StaticYawTorqueAudit(
				configuredYawTorque,
				TYTO_X3NM_SOURCE_ID,
				TYTO_X3NM_FIT_Q_OVER_T_METERS,
				TYTO_X3NM_TORQUE_RATIO_FIT_R2,
				TYTO_X3NM_TORQUE_RATIO_FIT_POINT_COUNT,
				TYTO_X3NM_HIGH_THRUST_Q_OVER_T_MEAN_METERS,
				TYTO_X3NM_Q_OVER_T_AT_MAX_THRUST_METERS,
				ratio(configuredYawTorque, TYTO_X3NM_FIT_Q_OVER_T_METERS),
				ratio(TYTO_X3NM_FIT_Q_OVER_T_METERS, configuredYawTorque),
				ratio(TYTO_X3NM_Q_OVER_T_AT_MAX_THRUST_METERS, configuredYawTorque),
				TYTO_DNQ_SOURCE_ID,
				TYTO_DNQ_FIT_Q_OVER_T_METERS,
				TYTO_DNQ_TORQUE_RATIO_FIT_R2,
				TYTO_DNQ_TORQUE_RATIO_FIT_POINT_COUNT,
				TYTO_DNQ_HIGH_THRUST_Q_OVER_T_MEAN_METERS,
				TYTO_DNQ_Q_OVER_T_AT_MAX_THRUST_METERS,
				ratio(configuredYawTorque, TYTO_DNQ_FIT_Q_OVER_T_METERS),
				ratio(TYTO_DNQ_FIT_Q_OVER_T_METERS, configuredYawTorque),
				ratio(TYTO_DNQ_Q_OVER_T_AT_MAX_THRUST_METERS, configuredYawTorque),
				minFit,
				maxFit,
				MathUtil.clamp(windowPosition, 0.0, 1.0)
		);
	}

	public static ApDroneMotorSpecAudit apDroneMotorSpecAudit(DroneConfig config) {
		double configuredMaxThrustNewtons = averageMaxRotorThrustNewtons(config);
		double configuredMaxRpm = averageMaxRotorRpm(config);
		double configuredMotorWindingResistance = averageMotorWindingResistanceOhms(config);
		int rotorCount = config == null ? 0 : config.rotors().size();
		double configuredPerMotorPackCurrent = rotorCount == 0
				? 0.0
				: config.maxBatteryCurrentAmps() / rotorCount;
		double configuredFullChargeVoltage = config == null ? 0.0 : config.nominalBatteryVoltage();
		double referenceKvFullChargeRpm = APDRONE_MOTOR_PDF_KV_RPM_PER_VOLT * configuredFullChargeVoltage;
		double referenceKvNominalRpm = APDRONE_MOTOR_PDF_KV_RPM_PER_VOLT
				* APDRONE_BATTERY_FILENAME_NOMINAL_VOLTAGE_VOLTS;
		double betaflightKvFullChargeRpm = APDRONE_BETAFLIGHT_KV_RPM_PER_VOLT * configuredFullChargeVoltage;
		double betaflightKvNominalRpm = APDRONE_BETAFLIGHT_KV_RPM_PER_VOLT
				* APDRONE_BATTERY_FILENAME_NOMINAL_VOLTAGE_VOLTS;
		return new ApDroneMotorSpecAudit(
				APDRONE_MOTOR_PDF_SOURCE_ID,
				configuredMaxThrustNewtons,
				configuredMaxRpm,
				configuredMotorWindingResistance,
				configuredPerMotorPackCurrent,
				APDRONE_MOTOR_PDF_KV_RPM_PER_VOLT,
				APDRONE_BETAFLIGHT_KV_RPM_PER_VOLT,
				APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS,
				APDRONE_MOTOR_PDF_CONTINUOUS_CURRENT_AMPS,
				APDRONE_MOTOR_PDF_HEADLINE_MAX_THRUST_NEWTONS,
				APDRONE_MOTOR_PDF_BEST_VISIBLE_MAX_THRUST_NEWTONS,
				APDRONE_MOTOR_PDF_BEST_VISIBLE_MAX_CURRENT_AMPS,
				APDRONE_MOTOR_PDF_BEST_VISIBLE_MAX_VOLTAGE_VOLTS,
				ratio(configuredMaxThrustNewtons, APDRONE_MOTOR_PDF_HEADLINE_MAX_THRUST_NEWTONS),
				ratio(configuredMaxThrustNewtons, APDRONE_MOTOR_PDF_BEST_VISIBLE_MAX_THRUST_NEWTONS),
				ratio(configuredMotorWindingResistance, APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS),
				ratio(configuredPerMotorPackCurrent, APDRONE_MOTOR_PDF_CONTINUOUS_CURRENT_AMPS),
				ratio(configuredMaxRpm, referenceKvFullChargeRpm),
				ratio(configuredMaxRpm, referenceKvNominalRpm),
				ratio(configuredMaxRpm, betaflightKvFullChargeRpm),
				ratio(configuredMaxRpm, betaflightKvNominalRpm)
		);
	}

	private static double powerLaw(double thrustNewtons, double coefficient, double exponent) {
		if (!Double.isFinite(thrustNewtons) || thrustNewtons <= 0.0) {
			return 0.0;
		}
		return coefficient * Math.pow(thrustNewtons, exponent);
	}

	private static double averageMaxRotorThrustNewtons(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxThrustNewtons();
		}
		return total / config.rotors().size();
	}

	private static double averageThrustCoefficient(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.thrustCoefficient();
		}
		return total / config.rotors().size();
	}

	private static double averageMaxRotorRpm(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxOmegaRadiansPerSecond() * RADIANS_PER_SECOND_TO_RPM;
		}
		return total / config.rotors().size();
	}

	private static double averageHoverRotorRpm(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double nominalHoverThrust = config.massKg() * config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rpmForThrustAndCoefficient(nominalHoverThrust, rotor.thrustCoefficient());
		}
		return total / config.rotors().size();
	}

	private static double averageBladeCount(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.bladeCount();
		}
		return total / config.rotors().size();
	}

	private static double averageYawTorquePerThrustMeter(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.yawTorquePerThrustMeter();
		}
		return total / config.rotors().size();
	}

	private static double averageMotorWindingResistanceOhms(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.motorWindingResistanceOhms();
		}
		return total / config.rotors().size();
	}

	private static double rpmForThrustAndCoefficient(double thrustNewtons, double thrustCoefficient) {
		if (!Double.isFinite(thrustNewtons)
				|| !Double.isFinite(thrustCoefficient)
				|| thrustNewtons <= 0.0
				|| thrustCoefficient <= 0.0) {
			return 0.0;
		}
		return Math.sqrt(thrustNewtons / thrustCoefficient) * RADIANS_PER_SECOND_TO_RPM;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static double plateauWindow(double value, double center, double innerHalfWidth, double outerHalfWidth) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}

		double distance = Math.abs(value - center);
		if (distance <= innerHalfWidth) {
			return 1.0;
		}
		if (distance >= outerHalfWidth) {
			return 0.0;
		}
		return 1.0 - (distance - innerHalfWidth) / (outerHalfWidth - innerHalfWidth);
	}
}
