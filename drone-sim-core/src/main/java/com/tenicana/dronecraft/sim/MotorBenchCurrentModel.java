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
	public static final double APDRONE_PDF_5045_6S_CURRENT_FIT_A = 1.0126900652391877;
	public static final double APDRONE_PDF_5045_6S_CURRENT_FIT_B = 1.303139834022931;
	public static final double APDRONE_PDF_5045_6S_POWER_FIT_A = 27.51898610712409;
	public static final double APDRONE_PDF_5045_6S_POWER_FIT_B = 1.2629199818308061;
	public static final double APDRONE_FOXEER_5145_RADIUS_METERS = 5.1 * 0.0254 * 0.5;
	public static final double APDRONE_FOXEER_5145_PITCH_TO_DIAMETER_RATIO = 4.5 / 5.1;
	public static final int APDRONE_FOXEER_5145_BLADE_COUNT = 3;
	public static final String FOXEER_DONUT_5145_PUBLIC_TEST_SOURCE_ID = "Foxeer-Donut-5145";
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_NEWTONS = 13.556742379949998;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_RPM = 29802.0;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_COEFFICIENT = 1.3918976015517363e-6;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_TORQUE_NEWTON_METERS = 0.184;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_TORQUE_PER_THRUST_METERS = 0.013572582176683558;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_CURRENT_AMPS = 34.83;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_VOLTAGE_VOLTS = 23.72;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_POWER_WATTS = 826.0;
	public static final double FOXEER_DONUT_5145_PUBLIC_TEST_VIBRATION_G = 1.7;
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
	public static final String APDRONE_URBAN_MOTOR_RPM_SOURCE_ID = "APDrone-Mendeley-Urban-eRPM";
	public static final int APDRONE_URBAN_MOTOR_RPM_SOURCE_FILE_COUNT = 5;
	public static final double APDRONE_URBAN_MOTOR_OUTPUT_LOW = 158.0;
	public static final double APDRONE_URBAN_MOTOR_OUTPUT_HIGH = 2047.0;
	public static final int APDRONE_URBAN_MOTOR_POLES = 14;
	public static final boolean APDRONE_URBAN_DSHOT_BIDIRECTIONAL = true;
	public static final double APDRONE_URBAN_THRUST_LINEAR_PERCENT = 20.0;
	public static final double APDRONE_URBAN_MOTOR_RPM_DURATION_SECONDS = 218.60566;
	public static final int APDRONE_URBAN_MOTOR_RPM_SAMPLE_COUNT = 8_839_444;
	public static final int APDRONE_URBAN_MOTOR_RPM_VALID_ERPM_SAMPLE_COUNT = 8_532_916;
	public static final double APDRONE_URBAN_MOTOR_RPM_VALID_ERPM_FRACTION = 0.9653227058172437;
	public static final int APDRONE_URBAN_MOTOR_RPM_SAMPLED_PERCENTILE_COUNT = 426_649;
	public static final double APDRONE_URBAN_MOTOR_COMMAND_P50 = 0.4695606140815246;
	public static final double APDRONE_URBAN_MOTOR_COMMAND_P95 = 0.5569084171519323;
	public static final double APDRONE_URBAN_MOTOR_COMMAND_P99 = 0.5881418740074114;
	public static final double APDRONE_URBAN_MECHANICAL_RPM_P50 = 12428.57142857143;
	public static final double APDRONE_URBAN_MECHANICAL_RPM_P95 = 14100.0;
	public static final double APDRONE_URBAN_MECHANICAL_RPM_P99 = 14671.42857142857;
	public static final double APDRONE_URBAN_MECHANICAL_RPM_MAX_SAMPLED = 19000.0;
	public static final double APDRONE_URBAN_EFFECTIVE_KV_RPM_PER_VOLT_P50 = 1747.6119054465175;
	public static final double APDRONE_URBAN_EFFECTIVE_KV_RPM_PER_VOLT_P95 = 1945.9294542855673;
	public static final double APDRONE_URBAN_RPM_OVER_LINEAR_MOTOR_COMMAND_P50 = 0.885346198843192;
	public static final double APDRONE_URBAN_RPM_OVER_LINEAR_MOTOR_COMMAND_P95 = 0.991249628534453;
	public static final double APDRONE_URBAN_RPM_OVER_SQRT_MOTOR_COMMAND_P50 = 0.610184367768238;
	public static final double APDRONE_URBAN_RPM_OVER_SQRT_MOTOR_COMMAND_P95 = 0.6472152484376887;
	public static final double APDRONE_URBAN_INFERRED_THRUST_NEWTONS_P50 = 2.357799914647421;
	public static final double APDRONE_URBAN_INFERRED_THRUST_NEWTONS_P95 = 3.034609043535689;
	public static final double APDRONE_URBAN_INFERRED_QUAD_TWR_P50 = 1.5304180776091347;
	public static final double APDRONE_URBAN_INFERRED_QUAD_TWR_P95 = 1.9697263155587432;
	public static final int APDRONE_URBAN_LINEAR_FIT_COUNT = 421_313;
	public static final double APDRONE_URBAN_LINEAR_FIT_SLOPE_RPM_PER_NORM = 18811.050781477166;
	public static final double APDRONE_URBAN_LINEAR_FIT_INTERCEPT_RPM = 3545.8876633553405;
	public static final double APDRONE_URBAN_LINEAR_FIT_R2 = 0.8646054039037868;
	public static final double APDRONE_URBAN_LINEAR_FIT_RMSE_RPM = 557.0568044537405;
	public static final double APDRONE_URBAN_LINEAR_FIT_NORM_AT_HOVER_RPM = 0.34557581857109704;
	public static final double APDRONE_URBAN_POWER_FIT_SCALE_RPM_FRACTION_AT_NORM_1 = 0.6852594622080235;
	public static final double APDRONE_URBAN_POWER_FIT_EXPONENT = 0.656685495229842;
	public static final double APDRONE_URBAN_POWER_FIT_R2_LOG = 0.8809725007373087;
	public static final double APDRONE_URBAN_POWER_FIT_RMSE_RPM_FRACTION = 0.01800243818551285;
	public static final double APDRONE_URBAN_MOTOR0_RPM_P50 = 11642.857142857143;
	public static final double APDRONE_URBAN_MOTOR1_RPM_P50 = 13400.0;
	public static final double APDRONE_URBAN_MOTOR2_RPM_P50 = 11800.0;
	public static final double APDRONE_URBAN_MOTOR3_RPM_P50 = 13228.57142857143;
	public static final double APDRONE_URBAN_MOTOR0_RPM_P95 = 12757.142857142859;
	public static final double APDRONE_URBAN_MOTOR1_RPM_P95 = 14385.714285714286;
	public static final double APDRONE_URBAN_MOTOR2_RPM_P95 = 12757.142857142857;
	public static final double APDRONE_URBAN_MOTOR3_RPM_P95 = 14185.714285714286;

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

	public record ApDroneUrbanMotorRpmAudit(
			String referenceId,
			int sourceFileCount,
			double motorOutputLow,
			double motorOutputHigh,
			int motorPoles,
			boolean dshotBidirectional,
			double thrustLinearPercent,
			double durationSeconds,
			int sampleCount,
			int validErpmSampleCount,
			double validErpmFraction,
			int sampledPercentileCount,
			double motorCommandP50,
			double motorCommandP95,
			double motorCommandP99,
			double mechanicalRpmP50,
			double mechanicalRpmP95,
			double mechanicalRpmP99,
			double mechanicalRpmMaxSampled,
			double configuredHoverRpm,
			double configuredMaxRpm,
			double configuredHoverLoggedErpm100,
			double configuredMaxLoggedErpm100,
			double mechanicalRpmP50OverConfiguredHover,
			double mechanicalRpmP95OverConfiguredHover,
			double mechanicalRpmP95OverConfiguredMax,
			double mechanicalRpmMaxOverConfiguredMax,
			double effectiveKvRpmPerVoltP50,
			double effectiveKvRpmPerVoltP95,
			double rpmOverLinearMotorCommandP50,
			double rpmOverLinearMotorCommandP95,
			double rpmOverSqrtMotorCommandP50,
			double rpmOverSqrtMotorCommandP95,
			double inferredThrustNewtonsP50,
			double inferredThrustNewtonsP95,
			double inferredQuadTwrP50,
			double inferredQuadTwrP95,
			int linearFitCount,
			double linearFitSlopeRpmPerNorm,
			double linearFitInterceptRpm,
			double linearFitR2,
			double linearFitRmseRpm,
			double linearFitNormAtHoverRpm,
			double powerFitScaleRpmFractionAtNorm1,
			double powerFitExponent,
			double powerFitR2Log,
			double powerFitRmseRpmFraction,
			double motorP50RpmSpread,
			double motorP95RpmSpread,
			double measuredP95BladePassHertz,
			double configuredMaxBladePassHertz
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

	public record FoxeerDonut5145PropAudit(
			String referenceId,
			double configuredMaxRotorThrustNewtons,
			double configuredThrustCoefficient,
			double configuredYawTorquePerThrustMeter,
			double configuredMaxRpm,
			double referenceThrustNewtons,
			double referenceRpm,
			double referenceThrustCoefficient,
			double referenceTorqueNewtonMeters,
			double referenceTorquePerThrustMeter,
			double referenceCurrentAmps,
			double referenceVoltageVolts,
			double referencePowerWatts,
			double referenceVibrationG,
			double configuredMaxThrustOverReference,
			double configuredThrustCoefficientOverReference,
			double configuredYawTorquePerThrustOverReference,
			double configuredMaxRpmOverReference
	) {
	}

	public static double mqtbHq5x4x3CurrentAmpsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, MQTB_HQ5X4X3_CURRENT_FIT_A, MQTB_HQ5X4X3_CURRENT_FIT_B);
	}

	public static double mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, MQTB_HQ5X4X3_POWER_FIT_A, MQTB_HQ5X4X3_POWER_FIT_B);
	}

	public static double apDronePdf5045CurrentAmpsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, APDRONE_PDF_5045_6S_CURRENT_FIT_A, APDRONE_PDF_5045_6S_CURRENT_FIT_B);
	}

	public static double apDronePdf5045ElectricalPowerWattsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, APDRONE_PDF_5045_6S_POWER_FIT_A, APDRONE_PDF_5045_6S_POWER_FIT_B);
	}

	public static double mechanicalRpmForBetaflightErpm100(double loggedErpm100, double motorPoles) {
		if (!Double.isFinite(loggedErpm100)
				|| loggedErpm100 <= 0.0
				|| !Double.isFinite(motorPoles)
				|| motorPoles <= 0.0) {
			return 0.0;
		}
		return loggedErpm100 * 100.0 * 2.0 / motorPoles;
	}

	public static double betaflightErpm100ForMechanicalRpm(double mechanicalRpm, double motorPoles) {
		if (!Double.isFinite(mechanicalRpm)
				|| mechanicalRpm <= 0.0
				|| !Double.isFinite(motorPoles)
				|| motorPoles <= 0.0) {
			return 0.0;
		}
		return mechanicalRpm * motorPoles / 200.0;
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

	public static double apDronePdf5045TotalCurrentAmps(DroneConfig config, DroneState state) {
		if (config == null || state == null) {
			return 0.0;
		}
		double total = 0.0;
		int count = Math.min(config.rotors().size(), state.rotorThrustNewtons().length);
		for (int i = 0; i < count; i++) {
			total += apDronePdf5045RotorSimilarity(config.rotors().get(i))
					* apDronePdf5045CurrentAmpsForThrustNewtons(state.rotorThrustNewtons(i));
		}
		return total;
	}

	public static double apDronePdf5045TotalElectricalPowerWatts(DroneConfig config, DroneState state) {
		if (config == null || state == null) {
			return 0.0;
		}
		double total = 0.0;
		int count = Math.min(config.rotors().size(), state.rotorThrustNewtons().length);
		for (int i = 0; i < count; i++) {
			total += apDronePdf5045RotorSimilarity(config.rotors().get(i))
					* apDronePdf5045ElectricalPowerWattsForThrustNewtons(state.rotorThrustNewtons(i));
		}
		return total;
	}

	public static double apDronePdf5045CurrentRatio(DroneConfig config, DroneState state) {
		double referenceCurrent = apDronePdf5045TotalCurrentAmps(config, state);
		if (referenceCurrent <= 1.0e-9) {
			return 0.0;
		}
		return totalMotorCurrentAmps(state) / referenceCurrent;
	}

	public static double apDronePdf5045CurrentResidualAmps(DroneConfig config, DroneState state) {
		return totalMotorCurrentAmps(state) - apDronePdf5045TotalCurrentAmps(config, state);
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

	public static double apDronePdf5045RotorSimilarity(RotorSpec rotor) {
		if (rotor == null) {
			return 0.0;
		}

		double radiusWeight = plateauWindow(rotor.radiusMeters(), APDRONE_FOXEER_5145_RADIUS_METERS, 0.003, 0.012);
		double pitchWeight = plateauWindow(
				rotor.bladePitchToDiameterRatio(),
				APDRONE_FOXEER_5145_PITCH_TO_DIAMETER_RATIO,
				0.035,
				0.16
		);
		double bladeWeight = 1.0 - MathUtil.clamp(
				Math.abs(rotor.bladeCount() - APDRONE_FOXEER_5145_BLADE_COUNT),
				0.0,
				1.0
		);
		double windingResistanceWeight = plateauWindow(
				rotor.motorWindingResistanceOhms(),
				APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS,
				0.008,
				0.035
		);
		return MathUtil.clamp(radiusWeight * pitchWeight * bladeWeight * windingResistanceWeight, 0.0, 1.0);
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

	public static ApDroneUrbanMotorRpmAudit apDroneUrbanMotorRpmAudit(DroneConfig config) {
		double configuredHoverRotorRpm = averageHoverRotorRpm(config);
		double configuredMaxRotorRpm = averageMaxRotorRpm(config);
		double configuredBladeCount = averageBladeCount(config);
		double motorP50Spread = max(
				APDRONE_URBAN_MOTOR0_RPM_P50,
				APDRONE_URBAN_MOTOR1_RPM_P50,
				APDRONE_URBAN_MOTOR2_RPM_P50,
				APDRONE_URBAN_MOTOR3_RPM_P50
		) - min(
				APDRONE_URBAN_MOTOR0_RPM_P50,
				APDRONE_URBAN_MOTOR1_RPM_P50,
				APDRONE_URBAN_MOTOR2_RPM_P50,
				APDRONE_URBAN_MOTOR3_RPM_P50
		);
		double motorP95Spread = max(
				APDRONE_URBAN_MOTOR0_RPM_P95,
				APDRONE_URBAN_MOTOR1_RPM_P95,
				APDRONE_URBAN_MOTOR2_RPM_P95,
				APDRONE_URBAN_MOTOR3_RPM_P95
		) - min(
				APDRONE_URBAN_MOTOR0_RPM_P95,
				APDRONE_URBAN_MOTOR1_RPM_P95,
				APDRONE_URBAN_MOTOR2_RPM_P95,
				APDRONE_URBAN_MOTOR3_RPM_P95
		);
		return new ApDroneUrbanMotorRpmAudit(
				APDRONE_URBAN_MOTOR_RPM_SOURCE_ID,
				APDRONE_URBAN_MOTOR_RPM_SOURCE_FILE_COUNT,
				APDRONE_URBAN_MOTOR_OUTPUT_LOW,
				APDRONE_URBAN_MOTOR_OUTPUT_HIGH,
				APDRONE_URBAN_MOTOR_POLES,
				APDRONE_URBAN_DSHOT_BIDIRECTIONAL,
				APDRONE_URBAN_THRUST_LINEAR_PERCENT,
				APDRONE_URBAN_MOTOR_RPM_DURATION_SECONDS,
				APDRONE_URBAN_MOTOR_RPM_SAMPLE_COUNT,
				APDRONE_URBAN_MOTOR_RPM_VALID_ERPM_SAMPLE_COUNT,
				APDRONE_URBAN_MOTOR_RPM_VALID_ERPM_FRACTION,
				APDRONE_URBAN_MOTOR_RPM_SAMPLED_PERCENTILE_COUNT,
				APDRONE_URBAN_MOTOR_COMMAND_P50,
				APDRONE_URBAN_MOTOR_COMMAND_P95,
				APDRONE_URBAN_MOTOR_COMMAND_P99,
				APDRONE_URBAN_MECHANICAL_RPM_P50,
				APDRONE_URBAN_MECHANICAL_RPM_P95,
				APDRONE_URBAN_MECHANICAL_RPM_P99,
				APDRONE_URBAN_MECHANICAL_RPM_MAX_SAMPLED,
				configuredHoverRotorRpm,
				configuredMaxRotorRpm,
				betaflightErpm100ForMechanicalRpm(configuredHoverRotorRpm, APDRONE_URBAN_MOTOR_POLES),
				betaflightErpm100ForMechanicalRpm(configuredMaxRotorRpm, APDRONE_URBAN_MOTOR_POLES),
				ratio(APDRONE_URBAN_MECHANICAL_RPM_P50, configuredHoverRotorRpm),
				ratio(APDRONE_URBAN_MECHANICAL_RPM_P95, configuredHoverRotorRpm),
				ratio(APDRONE_URBAN_MECHANICAL_RPM_P95, configuredMaxRotorRpm),
				ratio(APDRONE_URBAN_MECHANICAL_RPM_MAX_SAMPLED, configuredMaxRotorRpm),
				APDRONE_URBAN_EFFECTIVE_KV_RPM_PER_VOLT_P50,
				APDRONE_URBAN_EFFECTIVE_KV_RPM_PER_VOLT_P95,
				APDRONE_URBAN_RPM_OVER_LINEAR_MOTOR_COMMAND_P50,
				APDRONE_URBAN_RPM_OVER_LINEAR_MOTOR_COMMAND_P95,
				APDRONE_URBAN_RPM_OVER_SQRT_MOTOR_COMMAND_P50,
				APDRONE_URBAN_RPM_OVER_SQRT_MOTOR_COMMAND_P95,
				APDRONE_URBAN_INFERRED_THRUST_NEWTONS_P50,
				APDRONE_URBAN_INFERRED_THRUST_NEWTONS_P95,
				APDRONE_URBAN_INFERRED_QUAD_TWR_P50,
				APDRONE_URBAN_INFERRED_QUAD_TWR_P95,
				APDRONE_URBAN_LINEAR_FIT_COUNT,
				APDRONE_URBAN_LINEAR_FIT_SLOPE_RPM_PER_NORM,
				APDRONE_URBAN_LINEAR_FIT_INTERCEPT_RPM,
				APDRONE_URBAN_LINEAR_FIT_R2,
				APDRONE_URBAN_LINEAR_FIT_RMSE_RPM,
				APDRONE_URBAN_LINEAR_FIT_NORM_AT_HOVER_RPM,
				APDRONE_URBAN_POWER_FIT_SCALE_RPM_FRACTION_AT_NORM_1,
				APDRONE_URBAN_POWER_FIT_EXPONENT,
				APDRONE_URBAN_POWER_FIT_R2_LOG,
				APDRONE_URBAN_POWER_FIT_RMSE_RPM_FRACTION,
				motorP50Spread,
				motorP95Spread,
				APDRONE_URBAN_MECHANICAL_RPM_P95 * configuredBladeCount / 60.0,
				configuredMaxRotorRpm * configuredBladeCount / 60.0
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

	public static FoxeerDonut5145PropAudit foxeerDonut5145PropAudit(DroneConfig config) {
		double configuredMaxThrustNewtons = averageMaxRotorThrustNewtons(config);
		double configuredThrustCoefficient = averageThrustCoefficient(config);
		double configuredYawTorque = averageYawTorquePerThrustMeter(config);
		double configuredMaxRpm = averageMaxRotorRpm(config);
		return new FoxeerDonut5145PropAudit(
				FOXEER_DONUT_5145_PUBLIC_TEST_SOURCE_ID,
				configuredMaxThrustNewtons,
				configuredThrustCoefficient,
				configuredYawTorque,
				configuredMaxRpm,
				FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_NEWTONS,
				FOXEER_DONUT_5145_PUBLIC_TEST_RPM,
				FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_COEFFICIENT,
				FOXEER_DONUT_5145_PUBLIC_TEST_TORQUE_NEWTON_METERS,
				FOXEER_DONUT_5145_PUBLIC_TEST_TORQUE_PER_THRUST_METERS,
				FOXEER_DONUT_5145_PUBLIC_TEST_CURRENT_AMPS,
				FOXEER_DONUT_5145_PUBLIC_TEST_VOLTAGE_VOLTS,
				FOXEER_DONUT_5145_PUBLIC_TEST_POWER_WATTS,
				FOXEER_DONUT_5145_PUBLIC_TEST_VIBRATION_G,
				ratio(configuredMaxThrustNewtons, FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_NEWTONS),
				ratio(configuredThrustCoefficient, FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_COEFFICIENT),
				ratio(configuredYawTorque, FOXEER_DONUT_5145_PUBLIC_TEST_TORQUE_PER_THRUST_METERS),
				ratio(configuredMaxRpm, FOXEER_DONUT_5145_PUBLIC_TEST_RPM)
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

	private static double min(double first, double... rest) {
		double result = first;
		for (double value : rest) {
			result = Math.min(result, value);
		}
		return result;
	}

	private static double max(double first, double... rest) {
		double result = first;
		for (double value : rest) {
			result = Math.max(result, value);
		}
		return result;
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
