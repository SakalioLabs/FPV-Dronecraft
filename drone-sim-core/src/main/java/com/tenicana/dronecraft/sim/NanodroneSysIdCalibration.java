package com.tenicana.dronecraft.sim;

public final class NanodroneSysIdCalibration {
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "Nanodrone-System-Identification-Packet";
	public static final String PLATFORM = "Crazyflie 2.1 Brushless nano-quadrotor";
	public static final String PAPER_TITLE = "Nonlinear System Identification for a Nano-drone Benchmark";
	public static final String CAVEAT =
			"Crazyflie nano-quad sysid validates omega^2 data plumbing and train/test checks; do not transplant nano coefficients directly to FPV scale.";
	public static final int PACKET_ROW_COUNT = 1092;
	public static final int SOURCE_INVENTORY_ROW_COUNT = 20;
	public static final int COLUMN_SCHEMA_ROW_COUNT = 21;
	public static final int FILE_SUMMARY_ROW_COUNT = 405;
	public static final int DISTRIBUTION_SUMMARY_ROW_COUNT = 343;
	public static final int MODEL_CONSTANT_ROW_COUNT = 13;
	public static final int FIXED_MODEL_EVAL_ROW_COUNT = 60;
	public static final int TRAIN_TO_TEST_EVAL_ROW_COUNT = 72;
	public static final int COEFFICIENT_FIT_ROW_COUNT = 132;
	public static final int CURRENT_MODEL_COMPARISON_ROW_COUNT = 10;
	public static final int GROUP_SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 2;

	public static final int CLAIMED_SAMPLE_COUNT = 75_000;
	public static final int ACTUAL_LOADED_SAMPLE_COUNT = 75_096;
	public static final double ACTUAL_LOADED_DURATION_SECONDS = 750.81;
	public static final double SAMPLE_RATE_HERTZ = 100.0;
	public static final double SAMPLE_DT_SECONDS = 0.01;
	public static final int BENCHMARK_OPEN_LOOP_HORIZON_STEPS = 50;
	public static final double BENCHMARK_OPEN_LOOP_HORIZON_SECONDS = 0.5;
	public static final int ACTUAL_CSV_FILE_COUNT = 15;
	public static final int TRAIN_CSV_FILE_COUNT = 12;
	public static final int TEST_CSV_FILE_COUNT = 3;
	public static final int TRAIN_SAMPLE_COUNT = 55_599;
	public static final int TEST_SAMPLE_COUNT = 19_497;
	public static final String TRAJECTORY_NAMES = "chirp|melon|random|square";
	public static final int CHIRP_SAMPLE_COUNT = 24_000;
	public static final double CHIRP_DURATION_SECONDS = 239.96;
	public static final int RANDOM_SAMPLE_COUNT = 23_999;
	public static final double RANDOM_DURATION_SECONDS = 239.95;
	public static final int MELON_SAMPLE_COUNT = 19_497;
	public static final double MELON_DURATION_SECONDS = 194.94;
	public static final int SQUARE_SAMPLE_COUNT = 7_600;
	public static final double SQUARE_DURATION_SECONDS = 75.96;

	public static final double SOURCE_MASS_KG = 0.045;
	public static final double SOURCE_GRAVITY_METERS_PER_SECOND_SQUARED = 9.81;
	public static final double SOURCE_ARM_LENGTH_METERS = 0.0353;
	public static final double SOURCE_KT_NEWTONS_PER_RADIAN_PER_SECOND_SQUARED = 3.72e-8;
	public static final double SOURCE_KC_NEWTON_METERS_PER_RADIAN_PER_SECOND_SQUARED = 7.74e-12;
	public static final double SOURCE_THRUST_TO_WEIGHT = 2.0;
	public static final double SOURCE_TMAX_NEWTONS = 0.8829;
	public static final double SOURCE_JXX_KG_METERS_SQUARED = 2.3951e-5;
	public static final double SOURCE_JYY_KG_METERS_SQUARED = 2.3951e-5;
	public static final double SOURCE_JZZ_KG_METERS_SQUARED = 3.2347e-6;
	public static final double SOURCE_MAX_TORQUE_ROLL_NEWTON_METERS = 0.01;
	public static final double SOURCE_MAX_TORQUE_PITCH_NEWTON_METERS = 0.01;
	public static final double SOURCE_MAX_TORQUE_YAW_NEWTON_METERS = 0.003;

	public static final double NANODRONE_MOTOR_RAD_PER_SECOND_P95 = 1_994.10804384;
	public static final double NANODRONE_MOTOR_RAD_PER_SECOND_MAX = 2_530.66938885;
	public static final double NANODRONE_SOURCE_KT_THRUST_TO_WEIGHT_P50 = 1.01961284597;
	public static final double NANODRONE_SOURCE_KT_THRUST_TO_WEIGHT_P95 = 1.3036074917;
	public static final double NANODRONE_SOURCE_KT_THRUST_TO_WEIGHT_MAX = 2.03599811389;

	private NanodroneSysIdCalibration() {
	}

	public record RowTypeCounts(
			int totalRowCount,
			int sourceInventoryRowCount,
			int columnSchemaRowCount,
			int fileSummaryRowCount,
			int distributionSummaryRowCount,
			int modelConstantRowCount,
			int fixedModelEvalRowCount,
			int trainToTestEvalRowCount,
			int coefficientFitRowCount,
			int currentModelComparisonRowCount,
			int groupSummaryRowCount,
			int methodRowCount
	) {
	}

	public record SourceDataAudit(
			String platform,
			String paperTitle,
			int claimedSampleCount,
			int actualLoadedSampleCount,
			double actualLoadedDurationSeconds,
			double sampleRateHertz,
			double sampleDtSeconds,
			int benchmarkOpenLoopHorizonSteps,
			double benchmarkOpenLoopHorizonSeconds,
			int actualCsvFileCount,
			int trainCsvFileCount,
			int testCsvFileCount,
			int trainSampleCount,
			int testSampleCount,
			String trajectoryNames,
			int chirpSampleCount,
			double chirpDurationSeconds,
			int randomSampleCount,
			double randomDurationSeconds,
			int melonSampleCount,
			double melonDurationSeconds,
			int squareSampleCount,
			double squareDurationSeconds
	) {
	}

	public record ReferenceModelAudit(
			double massKg,
			double gravityMetersPerSecondSquared,
			double armLengthMeters,
			double sourceKtNewtonsPerRadianPerSecondSquared,
			double sourceKcNewtonMetersPerRadianPerSecondSquared,
			double sourceThrustToWeight,
			double sourceTmaxNewtons,
			double sourceJxxKgMetersSquared,
			double sourceJyyKgMetersSquared,
			double sourceJzzKgMetersSquared,
			double sourceMaxTorqueRollNewtonMeters,
			double sourceMaxTorquePitchNewtonMeters,
			double sourceMaxTorqueYawNewtonMeters
	) {
	}

	public record FixedSourceModelFit(
			String signalId,
			double sourceCoefficient,
			double rmse,
			double r2,
			double meanPredictionMinusMeasurement,
			int samples
	) {
	}

	public record TrainToTestFit(
			String signalId,
			double trainFitCoefficient,
			double trainFitCoefficientOverSource,
			double trainRmse,
			double trainR2,
			double trainBias,
			int trainSamples,
			double testRmse,
			double testR2,
			double testBias,
			int testSamples,
			double allRmse,
			double allR2,
			double allBias,
			int allSamples
	) {
	}

	public record CurrentScaleAudit(
			double configuredAverageRotorThrustCoefficient,
			double sourceKtOverConfiguredRotorThrustCoefficient,
			double configuredHoverRotorRadiansPerSecond,
			double configuredMaxRotorRadiansPerSecond,
			double nanodroneMotorRadiansPerSecondP95,
			double nanodroneMotorRadiansPerSecondMax,
			double nanodroneMotorP95OverConfiguredHover,
			double nanodroneMotorMaxOverConfiguredMax,
			double nanodroneSourceKtThrustToWeightP50,
			double nanodroneSourceKtThrustToWeightP95,
			double nanodroneSourceKtThrustToWeightMax
	) {
	}

	public record NanodroneSysIdAudit(
			String sourceId,
			String caveat,
			RowTypeCounts rowTypeCounts,
			SourceDataAudit sourceData,
			ReferenceModelAudit referenceModel,
			FixedSourceModelFit sourceThrustFit,
			TrainToTestFit trainThrustFit,
			TrainToTestFit trainRollTorqueFit,
			TrainToTestFit trainPitchTorqueFit,
			TrainToTestFit trainYawTorqueFit,
			CurrentScaleAudit currentScale
	) {
	}

	public static NanodroneSysIdAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		return new NanodroneSysIdAudit(
				SOURCE_ID,
				CAVEAT,
				rowTypeCounts(),
				sourceData(),
				referenceModel(),
				sourceThrustFit(),
				thrustTrainToTestFit(),
				rollTorqueTrainToTestFit(),
				pitchTorqueTrainToTestFit(),
				yawTorqueTrainToTestFit(),
				currentScaleAudit(config)
		);
	}

	private static RowTypeCounts rowTypeCounts() {
		return new RowTypeCounts(
				PACKET_ROW_COUNT,
				SOURCE_INVENTORY_ROW_COUNT,
				COLUMN_SCHEMA_ROW_COUNT,
				FILE_SUMMARY_ROW_COUNT,
				DISTRIBUTION_SUMMARY_ROW_COUNT,
				MODEL_CONSTANT_ROW_COUNT,
				FIXED_MODEL_EVAL_ROW_COUNT,
				TRAIN_TO_TEST_EVAL_ROW_COUNT,
				COEFFICIENT_FIT_ROW_COUNT,
				CURRENT_MODEL_COMPARISON_ROW_COUNT,
				GROUP_SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT
		);
	}

	private static SourceDataAudit sourceData() {
		return new SourceDataAudit(
				PLATFORM,
				PAPER_TITLE,
				CLAIMED_SAMPLE_COUNT,
				ACTUAL_LOADED_SAMPLE_COUNT,
				ACTUAL_LOADED_DURATION_SECONDS,
				SAMPLE_RATE_HERTZ,
				SAMPLE_DT_SECONDS,
				BENCHMARK_OPEN_LOOP_HORIZON_STEPS,
				BENCHMARK_OPEN_LOOP_HORIZON_SECONDS,
				ACTUAL_CSV_FILE_COUNT,
				TRAIN_CSV_FILE_COUNT,
				TEST_CSV_FILE_COUNT,
				TRAIN_SAMPLE_COUNT,
				TEST_SAMPLE_COUNT,
				TRAJECTORY_NAMES,
				CHIRP_SAMPLE_COUNT,
				CHIRP_DURATION_SECONDS,
				RANDOM_SAMPLE_COUNT,
				RANDOM_DURATION_SECONDS,
				MELON_SAMPLE_COUNT,
				MELON_DURATION_SECONDS,
				SQUARE_SAMPLE_COUNT,
				SQUARE_DURATION_SECONDS
		);
	}

	private static ReferenceModelAudit referenceModel() {
		return new ReferenceModelAudit(
				SOURCE_MASS_KG,
				SOURCE_GRAVITY_METERS_PER_SECOND_SQUARED,
				SOURCE_ARM_LENGTH_METERS,
				SOURCE_KT_NEWTONS_PER_RADIAN_PER_SECOND_SQUARED,
				SOURCE_KC_NEWTON_METERS_PER_RADIAN_PER_SECOND_SQUARED,
				SOURCE_THRUST_TO_WEIGHT,
				SOURCE_TMAX_NEWTONS,
				SOURCE_JXX_KG_METERS_SQUARED,
				SOURCE_JYY_KG_METERS_SQUARED,
				SOURCE_JZZ_KG_METERS_SQUARED,
				SOURCE_MAX_TORQUE_ROLL_NEWTON_METERS,
				SOURCE_MAX_TORQUE_PITCH_NEWTON_METERS,
				SOURCE_MAX_TORQUE_YAW_NEWTON_METERS
		);
	}

	private static FixedSourceModelFit sourceThrustFit() {
		return new FixedSourceModelFit(
				"thrust_body_z_specific_force_all_source_constant",
				3.72e-8,
				0.0146942006553,
				0.958394627403,
				0.000494231513556,
				75_096
		);
	}

	private static TrainToTestFit thrustTrainToTestFit() {
		return new TrainToTestFit(
				"thrust_body_z_specific_force_train_fit",
				3.71776253321e-8,
				0.999398530433,
				0.0137138575143,
				0.960951623312,
				3.74591513064e-5,
				55_599,
				0.0171630004128,
				0.952819569406,
				0.00073724384236,
				19_497,
				0.0146874060585,
				0.958433095147,
				0.000219142624747,
				75_096
		);
	}

	private static TrainToTestFit rollTorqueTrainToTestFit() {
		return new TrainToTestFit(
				"roll_torque_train_fit",
				3.66997690136e-9,
				0.0986552930472,
				0.000139902128847,
				0.147066706391,
				8.51370664816e-6,
				55_599,
				0.00011267260728,
				-0.145056930777,
				1.30954662439e-6,
				19_497,
				0.000133367983818,
				0.104744272341,
				6.64330731952e-6,
				75_096
		);
	}

	private static TrainToTestFit pitchTorqueTrainToTestFit() {
		return new TrainToTestFit(
				"pitch_torque_train_fit",
				1.85015998527e-9,
				0.049735483475,
				0.000136772608802,
				0.0905159613103,
				3.50945828443e-6,
				55_599,
				0.000108766179245,
				-0.0937251267048,
				-5.80184530262e-6,
				19_497,
				0.000130082181798,
				0.061832132159,
				1.09198616825e-6,
				75_096
		);
	}

	private static TrainToTestFit yawTorqueTrainToTestFit() {
		return new TrainToTestFit(
				"yaw_torque_train_fit",
				-4.81878079796e-12,
				-0.622581498444,
				5.56800303012e-6,
				0.16279913094,
				2.72742810038e-7,
				55_599,
				6.33137622582e-6,
				0.140449460194,
				-7.60418938385e-7,
				19_497,
				5.77590072157e-6,
				0.15595254505,
				4.50542576995e-9,
				75_096
		);
	}

	private static CurrentScaleAudit currentScaleAudit(DroneConfig config) {
		double hoverThrustPerRotor = config.massKg() * config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		double totalK = 0.0;
		double totalHoverRadiansPerSecond = 0.0;
		double totalMaxRadiansPerSecond = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			totalK += rotor.thrustCoefficient();
			totalHoverRadiansPerSecond += radiansPerSecondForThrustAndCoefficient(
					hoverThrustPerRotor,
					rotor.thrustCoefficient()
			);
			totalMaxRadiansPerSecond += rotor.maxOmegaRadiansPerSecond();
		}

		double averageK = totalK / config.rotors().size();
		double averageHoverRadiansPerSecond = totalHoverRadiansPerSecond / config.rotors().size();
		double averageMaxRadiansPerSecond = totalMaxRadiansPerSecond / config.rotors().size();
		return new CurrentScaleAudit(
				averageK,
				ratio(SOURCE_KT_NEWTONS_PER_RADIAN_PER_SECOND_SQUARED, averageK),
				averageHoverRadiansPerSecond,
				averageMaxRadiansPerSecond,
				NANODRONE_MOTOR_RAD_PER_SECOND_P95,
				NANODRONE_MOTOR_RAD_PER_SECOND_MAX,
				ratio(NANODRONE_MOTOR_RAD_PER_SECOND_P95, averageHoverRadiansPerSecond),
				ratio(NANODRONE_MOTOR_RAD_PER_SECOND_MAX, averageMaxRadiansPerSecond),
				NANODRONE_SOURCE_KT_THRUST_TO_WEIGHT_P50,
				NANODRONE_SOURCE_KT_THRUST_TO_WEIGHT_P95,
				NANODRONE_SOURCE_KT_THRUST_TO_WEIGHT_MAX
		);
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

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
