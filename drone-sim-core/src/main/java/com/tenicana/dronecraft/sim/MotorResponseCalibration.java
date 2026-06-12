package com.tenicana.dronecraft.sim;

public final class MotorResponseCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double RADIANS_PER_SECOND_TO_RPM = 60.0 / (2.0 * Math.PI);

	public static final String SOURCE_ID = "Motor-Response-Dynamics-Packet";
	public static final int PACKET_ROW_COUNT = 210;
	public static final String ROTORS_PX4_ACTUATOR_LAG_SOURCE_ID = "RotorS-PX4-Actuator-Lag";
	public static final double ROTORS_PX4_MOTOR_SPINUP_REFERENCE_TAU_SECONDS = 0.0125;
	public static final double ROTORS_PX4_MOTOR_SPINDOWN_REFERENCE_TAU_SECONDS = 0.025;
	public static final double ROTORS_PX4_ROTOR_INFLOW_REFERENCE_TAU_SECONDS = 0.0125;
	public static final String BETAFLIGHT_RPM_SLEW_SOURCE_ID = "Betaflight-PR12562-RPM-Slew";
	public static final int BETAFLIGHT_DECODED_MOTOR_ROW_COUNT = 16;
	public static final int BETAFLIGHT_DECODED_LOG_COUNT_WITH_VALID_RPM = 4;
	public static final double BETAFLIGHT_CURRENT_RACING_MOTOR_TAU_SECONDS = 0.045;
	public static final double BETAFLIGHT_CURRENT_RACING_BRAKING_TAU_PROXY_SECONDS = 0.0140625;
	public static final double BETAFLIGHT_CURRENT_RACING_NOMINAL_SPINUP_SLEW_RPM_PER_SECOND = 647_502.949989;
	public static final double BETAFLIGHT_CURRENT_RACING_BRAKING_SLEW_PROXY_RPM_PER_SECOND = 2_072_009.43996;
	public static final double BETAFLIGHT_OBSERVED_MAX_POSITIVE_50MS_SLEW_RPM_PER_SECOND = 503_271.096249;
	public static final double BETAFLIGHT_OBSERVED_MAX_NEGATIVE_50MS_SLEW_RPM_PER_SECOND = 525_477.707006;
	public static final double BETAFLIGHT_OBSERVED_POSITIVE_SLEW_OVER_CURRENT_SPINUP_PROXY = 0.777249117177;
	public static final double BETAFLIGHT_OBSERVED_NEGATIVE_SLEW_OVER_CURRENT_BRAKING_PROXY = 0.253607776524;
	public static final double BETAFLIGHT_OBSERVED_POSITIVE_TAU_EQUIVALENT_SECONDS = 0.0578964954807;
	public static final double BETAFLIGHT_OBSERVED_NEGATIVE_TAU_EQUIVALENT_SECONDS = 0.0554497980809;
	public static final double BETAFLIGHT_DECODED_RPM_MAX_ACROSS_MOTORS = 42_916.6666667;
	public static final double BETAFLIGHT_CURRENT_MAX_RPM_OVER_DECODED_RPM_MAX = 0.678935132027;
	public static final double BETAFLIGHT_LOG_LEVEL_POSITIVE_SLEW_P50_RPM_PER_SECOND = 264_945.835222;
	public static final double BETAFLIGHT_LOG_LEVEL_POSITIVE_SLEW_MAX_RPM_PER_SECOND = 503_271.096249;
	public static final double BETAFLIGHT_LOG_LEVEL_NEGATIVE_SLEW_MAX_RPM_PER_SECOND = 525_477.707006;
	public static final String APDRONE_URBAN_RPM_LAG_SOURCE_ID = "APDrone-Urban-eRPM-Lag";
	public static final int APDRONE_URBAN_SOURCE_FILE_COUNT = 5;
	public static final double APDRONE_URBAN_CURRENT_MOTOR_TAU_SECONDS = 0.015;
	public static final double APDRONE_URBAN_CURRENT_ESC_FRAME_RATE_HERTZ = 480.0;
	public static final double APDRONE_URBAN_CURRENT_ACTIVE_BRAKING_STRENGTH = 0.62;
	public static final double APDRONE_URBAN_CURRENT_HOVER_RPM = 10_046.5319353;
	public static final double APDRONE_URBAN_CURRENT_MAX_RPM = 29_739.565768;
	public static final double APDRONE_URBAN_CURRENT_NOMINAL_SPINUP_SLEW_RPM_PER_SECOND = 1_982_637.71787;
	public static final double APDRONE_URBAN_VALID_ERPM_FRACTION_MIN = 0.898207694325;
	public static final double APDRONE_URBAN_MECHANICAL_RPM_P95_MEDIAN = 13_957.1428571;
	public static final double APDRONE_URBAN_MECHANICAL_RPM_MAX_ACROSS_FILES = 19_000.0;
	public static final double APDRONE_URBAN_RPM_OVER_CURRENT_HOVER_P95_MEDIAN = 1.38924983735;
	public static final double APDRONE_URBAN_LINEAR_FIT_R2_MEDIAN = 0.886081159956;
	public static final double APDRONE_URBAN_POWER_FIT_R2_MEDIAN = 0.891071027341;
	public static final double APDRONE_URBAN_COMMAND_RPM_LEVEL_LAG_P10_MILLISECONDS = 43.1136000001;
	public static final double APDRONE_URBAN_COMMAND_RPM_LEVEL_LAG_P50_MILLISECONDS = 47.9040000001;
	public static final double APDRONE_URBAN_COMMAND_RPM_LEVEL_LAG_P90_MILLISECONDS = 73.3912;
	public static final double APDRONE_URBAN_COMMAND_RPM_DELTA_LAG_P10_MILLISECONDS = 39.9200000001;
	public static final double APDRONE_URBAN_COMMAND_RPM_DELTA_LAG_P50_MILLISECONDS = 39.9200000001;
	public static final double APDRONE_URBAN_COMMAND_RPM_DELTA_LAG_P90_MILLISECONDS = 63.8176;
	public static final double APDRONE_URBAN_FIRST_ORDER_TAU_P50_ACROSS_FILES_P10_MILLISECONDS = 3.10953736319;
	public static final double APDRONE_URBAN_FIRST_ORDER_TAU_P50_ACROSS_FILES_P50_MILLISECONDS = 3.94632051698;
	public static final double APDRONE_URBAN_FIRST_ORDER_TAU_P50_ACROSS_FILES_P90_MILLISECONDS = 4.07857848698;
	public static final double APDRONE_URBAN_FIRST_ORDER_TAU_P90_ACROSS_FILES_MAX_MILLISECONDS = 34.0431254323;
	public static final double APDRONE_URBAN_CURRENT_TAU_OVER_FIRST_ORDER_TAU_P50 = 3.80100905019;
	public static final double APDRONE_URBAN_CURRENT_TAU_OVER_LEVEL_LAG_P50 = 0.313126252504;
	public static final double MIN_ACTIVE_BRAKING_TAU_SECONDS = 0.005;
	public static final String OPEN_BENCH_PROPBENCH_SOURCE_ID = "ESC-Test-PropBench-Packet";
	public static final String OPEN_BENCH_PROPBENCH_CAVEAT =
			"Adjacent 7-inch static bench evidence; use slew bounds and protocol schema, not direct 5-inch thrust or yaw-torque coefficients.";
	public static final int OPEN_BENCH_PROPBENCH_PACKET_ROW_COUNT = 367;
	public static final int OPEN_BENCH_SOURCE_INVENTORY_COUNT = 6;
	public static final int OPEN_BENCH_RCBENCH_TEST_COUNT = 6;
	public static final int OPEN_BENCH_RCBENCH_SAMPLE_COUNT_TOTAL = 67_972;
	public static final int OPEN_BENCH_RCBENCH_RPM_BIN_COUNT = 102;
	public static final int OPEN_BENCH_AUTOQUAD_ESC_SUMMARY_ROW_COUNT = 32;
	public static final int OPEN_BENCH_PROPBENCH_PROTOCOL_METRIC_COUNT = 9;
	public static final int OPEN_BENCH_PROPBENCH_CSV_SCHEMA_FIELD_COUNT = 6;
	public static final double OPEN_BENCH_PROP_DIAMETER_METERS = 0.1778;
	public static final int OPEN_BENCH_PROP_BLADE_COUNT = 3;
	public static final double OPEN_BENCH_NOMINAL_SUPPLY_VOLTAGE_VOLTS = 16.0;
	public static final double OPEN_BENCH_K_FIT_P10 = 4.30424359474e-06;
	public static final double OPEN_BENCH_K_FIT_P50 = 4.44527215402e-06;
	public static final double OPEN_BENCH_K_FIT_P90 = 4.54877146718e-06;
	public static final double OPEN_BENCH_K_FIT_P50_OVER_CURRENT_RACING_QUAD_K = 3.06570493381;
	public static final int OPEN_BENCH_HIGH_RPM_TORQUE_FIT_SAMPLE_COUNT = 0;
	public static final double OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_P50_RPM_PER_SECOND = 138_752.523874;
	public static final double OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_MAX_RPM_PER_SECOND = 253_618.683953;
	public static final double OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_P50_RPM_PER_SECOND = 135_827.751648;
	public static final double OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_MAX_RPM_PER_SECOND = 272_067.092347;
	public static final double OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_OVER_CURRENT_RACING_QUAD_SPINUP_PROXY = 0.391687302672;
	public static final double OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_OVER_CURRENT_RACING_QUAD_BRAKING_PROXY = 0.131305913525;
	public static final double OPEN_BENCH_PROPBENCH_MAX_THRUST_RAMP_UP_MILLISECONDS = 5000.0;
	public static final double OPEN_BENCH_PROPBENCH_MAX_THRUST_HOLD_MILLISECONDS = 1000.0;
	public static final double OPEN_BENCH_PROPBENCH_MAX_THRUST_RAMP_DOWN_MILLISECONDS = 800.0;
	public static final double OPEN_BENCH_PROPBENCH_MAX_THRUST_REST_MILLISECONDS = 900.0;
	public static final int OPEN_BENCH_PROPBENCH_TEST_REPEAT_COUNT = 3;
	public static final double OPEN_BENCH_PROPBENCH_AVG_ACCEL_RAMP_UP_MILLISECONDS = 1000.0;
	public static final double OPEN_BENCH_PROPBENCH_AVG_ACCEL_OBSERVATION_WINDOW_MILLISECONDS = 7000.0;
	public static final double OPEN_BENCH_PROPBENCH_FC_TELEMETRY_POLL_INTERVAL_MILLISECONDS = 200.0;
	public static final double OPEN_BENCH_PROPBENCH_RPM_SCALE_CONSTANT = 41.0;

	private MotorResponseCalibration() {
	}

	public record RotorSPx4ActuatorLagReference(
			String referenceId,
			double motorSpinupReferenceTauSeconds,
			double motorSpindownReferenceTauSeconds,
			double rotorInflowReferenceTauSeconds,
			double racingQuadMotorTauSeconds,
			double racingQuadRotorInflowTauSeconds,
			double racingQuadMotorTauOverSpinupReference,
			double racingQuadMotorTauOverSpindownReference,
			double racingQuadInflowTauOverReference,
			double racingQuadWakeTransitOneRadiusSeconds,
			double racingQuadWakeTransitTwoRadiusSeconds,
			double racingQuadInflowTauOverOneRadiusWakeTransit,
			double racingQuadInflowTauOverTwoRadiusWakeTransit
	) {
	}

	public record BetaflightRpmSlewReference(
			String referenceId,
			int decodedMotorRowCount,
			int decodedLogCountWithValidRpm,
			double currentRacingMotorTauSeconds,
			double currentRacingBrakingTauProxySeconds,
			double currentRacingNominalSpinupSlewRpmPerSecond,
			double currentRacingBrakingSlewProxyRpmPerSecond,
			double observedMaxPositive50msSlewRpmPerSecond,
			double observedMaxNegative50msSlewRpmPerSecond,
			double observedPositiveSlewOverCurrentSpinupProxy,
			double observedNegativeSlewOverCurrentBrakingProxy,
			double observedPositiveTauEquivalentSeconds,
			double observedNegativeTauEquivalentSeconds,
			double decodedRpmMaxAcrossMotors,
			double currentMaxRpmOverDecodedRpmMax,
			double logLevelPositiveSlewP50RpmPerSecond,
			double logLevelPositiveSlewMaxRpmPerSecond,
			double logLevelNegativeSlewMaxRpmPerSecond
	) {
	}

	public record ApDroneUrbanRpmLagReference(
			String referenceId,
			int sourceFileCount,
			double currentApDroneMotorTauSeconds,
			double currentApDroneEscFrameRateHertz,
			double currentApDroneActiveBrakingStrength,
			double currentApDroneHoverRpm,
			double currentApDroneMaxRpm,
			double currentApDroneNominalSpinupSlewRpmPerSecond,
			double validErpmFractionMin,
			double mechanicalRpmP95Median,
			double mechanicalRpmMaxAcrossFiles,
			double rpmOverCurrentHoverP95Median,
			double linearFitR2Median,
			double powerFitR2Median,
			double commandRpmLevelLagP10Milliseconds,
			double commandRpmLevelLagP50Milliseconds,
			double commandRpmLevelLagP90Milliseconds,
			double commandRpmDeltaLagP10Milliseconds,
			double commandRpmDeltaLagP50Milliseconds,
			double commandRpmDeltaLagP90Milliseconds,
			double firstOrderTauP50AcrossFilesP10Milliseconds,
			double firstOrderTauP50AcrossFilesP50Milliseconds,
			double firstOrderTauP50AcrossFilesP90Milliseconds,
			double firstOrderTauP90AcrossFilesMaxMilliseconds,
			double currentMotorTauOverFirstOrderTauP50,
			double currentMotorTauOverLevelLagP50
	) {
	}

	public record OpenBenchPropResponseReference(
			String referenceId,
			String caveat,
			int packetRowCount,
			int sourceInventoryCount,
			int rcbenchTestCount,
			int rcbenchSampleCountTotal,
			int rcbenchRpmBinCount,
			int autoquadEscSummaryRowCount,
			int propbenchProtocolMetricCount,
			int propbenchCsvSchemaFieldCount,
			double propDiameterMeters,
			int bladeCount,
			double nominalSupplyVoltageVolts,
			double propDiameterOverAveragePresetPropDiameter,
			double thrustCoefficientKFitP10,
			double thrustCoefficientKFitP50,
			double thrustCoefficientKFitP90,
			double thrustCoefficientKFitP50OverAveragePresetK,
			double thrustCoefficientKFitP50OverCurrentRacingQuadK,
			int highRpmTorqueFitSampleCount,
			boolean rcbenchmarkCommandTimestampsAvailable,
			boolean highRpmTorqueRowsAvailable,
			double maxPositiveSlew50msP50RpmPerSecond,
			double maxPositiveSlew50msMaxRpmPerSecond,
			double maxNegativeSlew50msP50RpmPerSecond,
			double maxNegativeSlew50msMaxRpmPerSecond,
			double maxPositiveSlew50msMaxOverPresetSpinupProxy,
			double maxNegativeSlew50msMaxOverPresetBrakingProxy,
			double positiveSlewTauEquivalentSeconds,
			double negativeSlewTauEquivalentSeconds,
			double positiveSlewTauOverPresetMotorTau,
			double negativeSlewTauOverPresetActiveBrakingTau,
			double propbenchMaxThrustRampUpMilliseconds,
			double propbenchMaxThrustHoldMilliseconds,
			double propbenchMaxThrustRampDownMilliseconds,
			double propbenchMaxThrustRestMilliseconds,
			int propbenchTestRepeatCount,
			double propbenchAvgAccelRampUpMilliseconds,
			double propbenchAvgAccelObservationWindowMilliseconds,
			double propbenchFcTelemetryPollIntervalMilliseconds,
			double propbenchRpmScaleConstant
	) {
	}

	public record PresetMotorResponseAudit(
			double motorTimeConstantSeconds,
			double escFrameRateHertz,
			double escCommandFrameIntervalMilliseconds,
			double activeBrakingStrength,
			double averageHoverRotorRpm,
			double averageMaxRotorRpm,
			double nominalSpinupSlewRpmPerSecond,
			double activeBrakingTauProxySeconds,
			double activeBrakingSlewProxyRpmPerSecond,
			double motorTauOverRotorSSpinupReference,
			double motorTauOverRotorSSpindownReference,
			double observedPositiveSlewOverNominalSpinupProxy,
			double observedNegativeSlewOverActiveBrakingProxy,
			double observedPositiveTauOverMotorTau,
			double observedNegativeTauOverActiveBrakingTauProxy,
			double configuredMaxRpmOverBetaflightDecodedMaxRpm,
			double motorTauOverApDroneUrbanFirstOrderTauP50,
			double motorTauOverApDroneUrbanLevelLagP50,
			double escFrameIntervalOverApDroneUrbanDeltaLagP50
	) {
	}

	public record MotorResponseAudit(
			String sourceId,
			int packetRowCount,
			RotorSPx4ActuatorLagReference rotorSPx4Reference,
			BetaflightRpmSlewReference betaflightRpmSlewReference,
			ApDroneUrbanRpmLagReference apDroneUrbanRpmLagReference,
			OpenBenchPropResponseReference openBenchPropResponseReference,
			PresetMotorResponseAudit preset
	) {
	}

	public static MotorResponseAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		BetaflightRpmSlewReference betaflightReference = betaflightReference();
		ApDroneUrbanRpmLagReference apDroneReference = apDroneReference();
		return new MotorResponseAudit(
				SOURCE_ID,
				PACKET_ROW_COUNT,
				rotorSPx4Reference(),
				betaflightReference,
				apDroneReference,
				openBenchReference(config),
				presetAudit(config, betaflightReference, apDroneReference)
		);
	}

	private static RotorSPx4ActuatorLagReference rotorSPx4Reference() {
		return new RotorSPx4ActuatorLagReference(
				ROTORS_PX4_ACTUATOR_LAG_SOURCE_ID,
				ROTORS_PX4_MOTOR_SPINUP_REFERENCE_TAU_SECONDS,
				ROTORS_PX4_MOTOR_SPINDOWN_REFERENCE_TAU_SECONDS,
				ROTORS_PX4_ROTOR_INFLOW_REFERENCE_TAU_SECONDS,
				0.045,
				0.035,
				3.5999999999999996,
				1.7999999999999998,
				2.8000000000000003,
				0.006812064389955786,
				0.013624128779911572,
				5.137943213162607,
				2.5689716065813033
		);
	}

	private static BetaflightRpmSlewReference betaflightReference() {
		return new BetaflightRpmSlewReference(
				BETAFLIGHT_RPM_SLEW_SOURCE_ID,
				BETAFLIGHT_DECODED_MOTOR_ROW_COUNT,
				BETAFLIGHT_DECODED_LOG_COUNT_WITH_VALID_RPM,
				BETAFLIGHT_CURRENT_RACING_MOTOR_TAU_SECONDS,
				BETAFLIGHT_CURRENT_RACING_BRAKING_TAU_PROXY_SECONDS,
				BETAFLIGHT_CURRENT_RACING_NOMINAL_SPINUP_SLEW_RPM_PER_SECOND,
				BETAFLIGHT_CURRENT_RACING_BRAKING_SLEW_PROXY_RPM_PER_SECOND,
				BETAFLIGHT_OBSERVED_MAX_POSITIVE_50MS_SLEW_RPM_PER_SECOND,
				BETAFLIGHT_OBSERVED_MAX_NEGATIVE_50MS_SLEW_RPM_PER_SECOND,
				BETAFLIGHT_OBSERVED_POSITIVE_SLEW_OVER_CURRENT_SPINUP_PROXY,
				BETAFLIGHT_OBSERVED_NEGATIVE_SLEW_OVER_CURRENT_BRAKING_PROXY,
				BETAFLIGHT_OBSERVED_POSITIVE_TAU_EQUIVALENT_SECONDS,
				BETAFLIGHT_OBSERVED_NEGATIVE_TAU_EQUIVALENT_SECONDS,
				BETAFLIGHT_DECODED_RPM_MAX_ACROSS_MOTORS,
				BETAFLIGHT_CURRENT_MAX_RPM_OVER_DECODED_RPM_MAX,
				BETAFLIGHT_LOG_LEVEL_POSITIVE_SLEW_P50_RPM_PER_SECOND,
				BETAFLIGHT_LOG_LEVEL_POSITIVE_SLEW_MAX_RPM_PER_SECOND,
				BETAFLIGHT_LOG_LEVEL_NEGATIVE_SLEW_MAX_RPM_PER_SECOND
		);
	}

	private static ApDroneUrbanRpmLagReference apDroneReference() {
		return new ApDroneUrbanRpmLagReference(
				APDRONE_URBAN_RPM_LAG_SOURCE_ID,
				APDRONE_URBAN_SOURCE_FILE_COUNT,
				APDRONE_URBAN_CURRENT_MOTOR_TAU_SECONDS,
				APDRONE_URBAN_CURRENT_ESC_FRAME_RATE_HERTZ,
				APDRONE_URBAN_CURRENT_ACTIVE_BRAKING_STRENGTH,
				APDRONE_URBAN_CURRENT_HOVER_RPM,
				APDRONE_URBAN_CURRENT_MAX_RPM,
				APDRONE_URBAN_CURRENT_NOMINAL_SPINUP_SLEW_RPM_PER_SECOND,
				APDRONE_URBAN_VALID_ERPM_FRACTION_MIN,
				APDRONE_URBAN_MECHANICAL_RPM_P95_MEDIAN,
				APDRONE_URBAN_MECHANICAL_RPM_MAX_ACROSS_FILES,
				APDRONE_URBAN_RPM_OVER_CURRENT_HOVER_P95_MEDIAN,
				APDRONE_URBAN_LINEAR_FIT_R2_MEDIAN,
				APDRONE_URBAN_POWER_FIT_R2_MEDIAN,
				APDRONE_URBAN_COMMAND_RPM_LEVEL_LAG_P10_MILLISECONDS,
				APDRONE_URBAN_COMMAND_RPM_LEVEL_LAG_P50_MILLISECONDS,
				APDRONE_URBAN_COMMAND_RPM_LEVEL_LAG_P90_MILLISECONDS,
				APDRONE_URBAN_COMMAND_RPM_DELTA_LAG_P10_MILLISECONDS,
				APDRONE_URBAN_COMMAND_RPM_DELTA_LAG_P50_MILLISECONDS,
				APDRONE_URBAN_COMMAND_RPM_DELTA_LAG_P90_MILLISECONDS,
				APDRONE_URBAN_FIRST_ORDER_TAU_P50_ACROSS_FILES_P10_MILLISECONDS,
				APDRONE_URBAN_FIRST_ORDER_TAU_P50_ACROSS_FILES_P50_MILLISECONDS,
				APDRONE_URBAN_FIRST_ORDER_TAU_P50_ACROSS_FILES_P90_MILLISECONDS,
				APDRONE_URBAN_FIRST_ORDER_TAU_P90_ACROSS_FILES_MAX_MILLISECONDS,
				APDRONE_URBAN_CURRENT_TAU_OVER_FIRST_ORDER_TAU_P50,
				APDRONE_URBAN_CURRENT_TAU_OVER_LEVEL_LAG_P50
		);
	}

	private static OpenBenchPropResponseReference openBenchReference(DroneConfig config) {
		double averageMaxRpm = averageMaxRotorRpm(config);
		double nominalSpinupSlew = ratio(averageMaxRpm, config.motorTimeConstantSeconds());
		double activeBrakingTau = activeBrakingTauProxySeconds(config.motorTimeConstantSeconds(),
				config.motorActiveBrakingStrength());
		double activeBrakingSlew = ratio(averageMaxRpm, activeBrakingTau);
		return new OpenBenchPropResponseReference(
				OPEN_BENCH_PROPBENCH_SOURCE_ID,
				OPEN_BENCH_PROPBENCH_CAVEAT,
				OPEN_BENCH_PROPBENCH_PACKET_ROW_COUNT,
				OPEN_BENCH_SOURCE_INVENTORY_COUNT,
				OPEN_BENCH_RCBENCH_TEST_COUNT,
				OPEN_BENCH_RCBENCH_SAMPLE_COUNT_TOTAL,
				OPEN_BENCH_RCBENCH_RPM_BIN_COUNT,
				OPEN_BENCH_AUTOQUAD_ESC_SUMMARY_ROW_COUNT,
				OPEN_BENCH_PROPBENCH_PROTOCOL_METRIC_COUNT,
				OPEN_BENCH_PROPBENCH_CSV_SCHEMA_FIELD_COUNT,
				OPEN_BENCH_PROP_DIAMETER_METERS,
				OPEN_BENCH_PROP_BLADE_COUNT,
				OPEN_BENCH_NOMINAL_SUPPLY_VOLTAGE_VOLTS,
				ratio(OPEN_BENCH_PROP_DIAMETER_METERS, averagePropDiameterMeters(config)),
				OPEN_BENCH_K_FIT_P10,
				OPEN_BENCH_K_FIT_P50,
				OPEN_BENCH_K_FIT_P90,
				ratio(OPEN_BENCH_K_FIT_P50, averageThrustCoefficient(config)),
				OPEN_BENCH_K_FIT_P50_OVER_CURRENT_RACING_QUAD_K,
				OPEN_BENCH_HIGH_RPM_TORQUE_FIT_SAMPLE_COUNT,
				false,
				false,
				OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_P50_RPM_PER_SECOND,
				OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_MAX_RPM_PER_SECOND,
				OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_P50_RPM_PER_SECOND,
				OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_MAX_RPM_PER_SECOND,
				ratio(OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_MAX_RPM_PER_SECOND, nominalSpinupSlew),
				ratio(OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_MAX_RPM_PER_SECOND, activeBrakingSlew),
				ratio(averageMaxRpm, OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_MAX_RPM_PER_SECOND),
				ratio(averageMaxRpm, OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_MAX_RPM_PER_SECOND),
				ratio(ratio(averageMaxRpm, OPEN_BENCH_MAX_POSITIVE_SLEW_50MS_MAX_RPM_PER_SECOND),
						config.motorTimeConstantSeconds()),
				ratio(ratio(averageMaxRpm, OPEN_BENCH_MAX_NEGATIVE_SLEW_50MS_MAX_RPM_PER_SECOND),
						activeBrakingTau),
				OPEN_BENCH_PROPBENCH_MAX_THRUST_RAMP_UP_MILLISECONDS,
				OPEN_BENCH_PROPBENCH_MAX_THRUST_HOLD_MILLISECONDS,
				OPEN_BENCH_PROPBENCH_MAX_THRUST_RAMP_DOWN_MILLISECONDS,
				OPEN_BENCH_PROPBENCH_MAX_THRUST_REST_MILLISECONDS,
				OPEN_BENCH_PROPBENCH_TEST_REPEAT_COUNT,
				OPEN_BENCH_PROPBENCH_AVG_ACCEL_RAMP_UP_MILLISECONDS,
				OPEN_BENCH_PROPBENCH_AVG_ACCEL_OBSERVATION_WINDOW_MILLISECONDS,
				OPEN_BENCH_PROPBENCH_FC_TELEMETRY_POLL_INTERVAL_MILLISECONDS,
				OPEN_BENCH_PROPBENCH_RPM_SCALE_CONSTANT
		);
	}

	private static PresetMotorResponseAudit presetAudit(
			DroneConfig config,
			BetaflightRpmSlewReference betaflightReference,
			ApDroneUrbanRpmLagReference apDroneReference
	) {
		double motorTauSeconds = config.motorTimeConstantSeconds();
		double escFrameRateHertz = config.escCommandFrameRateHertz();
		double escFrameIntervalMilliseconds = escFrameRateHertz <= EPSILON ? 0.0 : 1000.0 / escFrameRateHertz;
		double activeBrakingStrength = MathUtil.clamp(config.motorActiveBrakingStrength(), 0.0, 1.0);
		double averageHoverRpm = averageHoverRotorRpm(config);
		double averageMaxRpm = averageMaxRotorRpm(config);
		double nominalSpinupSlewRpmPerSecond = ratio(averageMaxRpm, motorTauSeconds);
		double activeBrakingTauProxySeconds = activeBrakingTauProxySeconds(motorTauSeconds, activeBrakingStrength);
		double activeBrakingSlewProxyRpmPerSecond = ratio(averageMaxRpm, activeBrakingTauProxySeconds);
		return new PresetMotorResponseAudit(
				motorTauSeconds,
				escFrameRateHertz,
				escFrameIntervalMilliseconds,
				activeBrakingStrength,
				averageHoverRpm,
				averageMaxRpm,
				nominalSpinupSlewRpmPerSecond,
				activeBrakingTauProxySeconds,
				activeBrakingSlewProxyRpmPerSecond,
				ratio(motorTauSeconds, ROTORS_PX4_MOTOR_SPINUP_REFERENCE_TAU_SECONDS),
				ratio(motorTauSeconds, ROTORS_PX4_MOTOR_SPINDOWN_REFERENCE_TAU_SECONDS),
				ratio(betaflightReference.observedMaxPositive50msSlewRpmPerSecond(), nominalSpinupSlewRpmPerSecond),
				ratio(betaflightReference.observedMaxNegative50msSlewRpmPerSecond(), activeBrakingSlewProxyRpmPerSecond),
				ratio(betaflightReference.observedPositiveTauEquivalentSeconds(), motorTauSeconds),
				ratio(betaflightReference.observedNegativeTauEquivalentSeconds(), activeBrakingTauProxySeconds),
				ratio(averageMaxRpm, betaflightReference.decodedRpmMaxAcrossMotors()),
				ratio(motorTauSeconds, apDroneReference.firstOrderTauP50AcrossFilesP50Milliseconds() * 0.001),
				ratio(motorTauSeconds, apDroneReference.commandRpmLevelLagP50Milliseconds() * 0.001),
				ratio(escFrameIntervalMilliseconds, apDroneReference.commandRpmDeltaLagP50Milliseconds())
		);
	}

	private static double activeBrakingTauProxySeconds(double motorTauSeconds, double activeBrakingStrength) {
		return Math.max(
				MIN_ACTIVE_BRAKING_TAU_SECONDS,
				motorTauSeconds / (1.0 + 4.0 * MathUtil.clamp(activeBrakingStrength, 0.0, 1.0))
		);
	}

	private static double averagePropDiameterMeters(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.radiusMeters() * 2.0;
		}
		return total / config.rotors().size();
	}

	private static double averageThrustCoefficient(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.thrustCoefficient();
		}
		return total / config.rotors().size();
	}

	private static double averageMaxRotorRpm(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxOmegaRadiansPerSecond() * RADIANS_PER_SECOND_TO_RPM;
		}
		return total / config.rotors().size();
	}

	private static double averageHoverRotorRpm(DroneConfig config) {
		double nominalHoverThrust = config.massKg() * config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rpmForThrustAndCoefficient(nominalHoverThrust, rotor.thrustCoefficient());
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
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
