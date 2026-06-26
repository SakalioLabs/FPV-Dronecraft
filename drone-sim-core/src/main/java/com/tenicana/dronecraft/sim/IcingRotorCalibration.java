package com.tenicana.dronecraft.sim;

public final class IcingRotorCalibration {
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "Icing-Rotor-MDPI-Packet";
	public static final String CAVEAT = "icing-time-accumulating-not-ordinary-rain";
	public static final String PAPER_TITLE = "An Experimental Apparatus for Icing Tests of Low Altitude Hovering Drones";
	public static final String DOI = "https://doi.org/10.3390/drones6030068";
	public static final String LICENSE = "CC BY 4.0";
	public static final int PACKET_ROW_COUNT = 362;
	public static final int SOURCE_INVENTORY_ROW_COUNT = 17;
	public static final int CONDITION_GRID_ROW_COUNT = 35;
	public static final int TABLE4_CASE_ROW_COUNT = 168;
	public static final int HEIGHT_RATIO_ROW_COUNT = 28;
	public static final int TEMPERATURE_RATIO_ROW_COUNT = 24;
	public static final int MVD_RATIO_ROW_COUNT = 28;
	public static final int DISTRIBUTION_ROW_COUNT = 45;
	public static final int CURRENT_MODEL_COMPARISON_ROW_COUNT = 12;
	public static final int EXTREME_CASE_ROW_COUNT = 4;
	public static final int METHOD_ROW_COUNT = 1;

	public static final double SOURCE_ROTOR_BLADE_COUNT = 4.0;
	public static final double SOURCE_ROTOR_DIAMETER_METERS = 0.66;
	public static final double SOURCE_ROTOR_MAX_TIP_SPEED_METERS_PER_SECOND = 208.0;
	public static final double TABLE4_RPM = 4950.0;
	public static final double TABLE4_PITCH_DEGREES = 11.7;
	public static final double TABLE4_LAMBDA_G_DM2_H = 80.0;
	public static final double TABLE4_EQUIVALENT_RAIN_MM_H = 8.0;
	public static final double TABLE4_TIP_SPEED_METERS_PER_SECOND = 171.059719988;
	public static final double DRY_HEIGHT_CURVE_FIT_ERROR_PERCENT = 1.28;
	public static final double H4_CLOUD_INTENSITY_MULTIPLIER_LOW = 1.3;
	public static final double H4_CLOUD_INTENSITY_MULTIPLIER_HIGH = 1.4;

	public static final double ABS_CT_STAR_RATE_MIN_PERCENT_PER_SECOND = 0.012;
	public static final double ABS_CT_STAR_RATE_MEDIAN_PERCENT_PER_SECOND = 0.1025;
	public static final double ABS_CT_STAR_RATE_MAX_PERCENT_PER_SECOND = 0.226;
	public static final double CQ_STAR_RATE_MEDIAN_PERCENT_PER_SECOND = 0.219;
	public static final double P_PLUS_RATE_MEDIAN_PERCENT_PER_SECOND = 0.266;
	public static final double ICING_TIME_MIN_SECONDS = 106.0;
	public static final double ICING_TIME_MEDIAN_SECONDS = 194.5;
	public static final double ICING_TIME_MAX_SECONDS = 761.0;
	public static final double PROJECTED_CT_LOSS_MIN_PERCENT = 9.132;
	public static final double PROJECTED_CT_LOSS_MEDIAN_PERCENT = 19.388;
	public static final double PROJECTED_CT_LOSS_MAX_PERCENT = 23.956;
	public static final double PROJECTED_CQ_INCREASE_MEDIAN_PERCENT = 39.0835;
	public static final double PROJECTED_POWER_REQUIRED_MIN_PERCENT = 20.865;
	public static final double PROJECTED_POWER_REQUIRED_MEDIAN_PERCENT = 50.178;
	public static final double PROJECTED_POWER_REQUIRED_MAX_PERCENT = 89.49;
	public static final double CURRENT_FULL_WETNESS_RAIN_LOSS_PERCENT = 3.0;
	public static final double CT_LOSS_MEDIAN_OVER_CURRENT_RAIN_LOSS = 6.46266666667;
	public static final double CT_LOSS_MAX_OVER_CURRENT_RAIN_LOSS = 7.98533333333;
	private static final double FREEZING_HUMIDITY_ONSET = 0.72;
	private static final double FREEZING_HUMIDITY_FULL = 0.98;
	private static final double MAX_FREEZING_HUMIDITY_EQUIVALENT_WETNESS = 0.40;

	private IcingRotorCalibration() {
	}

	public record RowTypeCounts(
			int totalRowCount,
			int sourceInventoryRowCount,
			int conditionGridRowCount,
			int table4CaseRowCount,
			int heightRatioRowCount,
			int temperatureRatioRowCount,
			int mvdRatioRowCount,
			int distributionRowCount,
			int currentModelComparisonRowCount,
			int extremeCaseRowCount,
			int methodRowCount
	) {
	}

	public record SourceInventoryAudit(
			String paperTitle,
			String doi,
			String license,
			double rotorBladeCount,
			double rotorDiameterMeters,
			String rotorAirfoil,
			double rotorMaxTipSpeedMetersPerSecond,
			double table4Rpm,
			double table4PitchDegrees,
			double table4LambdaGdm2h,
			double table4EquivalentRainMillimetersPerHour,
			double table4TipSpeedMetersPerSecond,
			double dryHeightCurveFitErrorPercent,
			double h4CloudIntensityMultiplierLow,
			double h4CloudIntensityMultiplierHigh
	) {
	}

	public record DistributionAudit(
			double absCtStarRateMinPercentPerSecond,
			double absCtStarRateMedianPercentPerSecond,
			double absCtStarRateMaxPercentPerSecond,
			double cqStarRateMedianPercentPerSecond,
			double pPlusRateMedianPercentPerSecond,
			double icingTimeMinSeconds,
			double icingTimeMedianSeconds,
			double icingTimeMaxSeconds,
			double projectedCtLossMinPercent,
			double projectedCtLossMedianPercent,
			double projectedCtLossMaxPercent,
			double projectedCqIncreaseMedianPercent,
			double projectedPowerRequiredMinPercent,
			double projectedPowerRequiredMedianPercent,
			double projectedPowerRequiredMaxPercent
	) {
	}

	public record CurrentModelComparisonAudit(
			double currentFullWetnessRainLossPercent,
			double icingProjectedCtLossMinOverCurrentRainLoss,
			double icingProjectedCtLossMedianOverCurrentRainLoss,
			double icingProjectedCtLossMaxOverCurrentRainLoss,
			String recommendation
	) {
	}

	public record RuntimeModelAudit(
			double severityOneIcingTimeSeconds,
			double severityOneCtLossPercent,
			double severityOneThrustScale,
			double severityOnePowerRequiredIncreasePercent,
			double severityOnePowerScale,
			double severityMax,
			double maxModeledCtLossPercent,
			double maxModeledPowerScale,
			double fullWetMinusEightCSpinOneAccretionRatePerSecond,
			double halfWetMinusEightCSpinOneAccretionRatePerSecond,
			double warmFiveCSpinOneRecoveryRatePerSecond
	) {
	}

	public record ExtremeCaseAudit(
			String strongestProjectedCtLossCase,
			double strongestProjectedCtLossPercent,
			String weakestProjectedCtLossCase,
			double weakestProjectedCtLossPercent,
			String strongestProjectedPowerRequiredCase,
			double strongestProjectedPowerRequiredPercent,
			String longestIcingTimeCase,
			double longestIcingTimeSeconds
	) {
	}

	public record IcingRotorAudit(
			String sourceId,
			String caveat,
			RowTypeCounts rowTypeCounts,
			SourceInventoryAudit sourceInventory,
			DistributionAudit distribution,
			CurrentModelComparisonAudit currentModelComparison,
			RuntimeModelAudit runtimeModel,
			ExtremeCaseAudit extremeCase
	) {
	}

	public static IcingRotorAudit audit() {
		return new IcingRotorAudit(
				SOURCE_ID,
				CAVEAT,
				rowTypeCounts(),
				sourceInventory(),
				distribution(),
				currentModelComparison(),
				runtimeModel(),
				extremeCase()
		);
	}

	public static double icingSeverityRatePerSecond(
			double ambientTemperatureCelsius,
			double precipitationWetness,
			double spinRatio
	) {
		double freezingFactor = freezingTemperatureFactor(ambientTemperatureCelsius);
		if (freezingFactor <= EPSILON) {
			return 0.0;
		}
		double wetness = MathUtil.clamp(precipitationWetness, 0.0, 1.0);
		double spin = MathUtil.clamp(spinRatio, 0.0, 1.25);
		double wetnessFactor = Math.pow(wetness, 1.05);
		double spinFactor = smoothStep(0.08, 0.32, spin);
		return freezingFactor * wetnessFactor * spinFactor / ICING_TIME_MEDIAN_SECONDS;
	}

	public static double icingRecoveryRatePerSecond(
			double ambientTemperatureCelsius,
			double precipitationWetness,
			double spinRatio
	) {
		double wetness = MathUtil.clamp(precipitationWetness, 0.0, 1.0);
		double spin = MathUtil.clamp(spinRatio, 0.0, 1.25);
		double warmMelt = smoothStep(0.5, 5.0, ambientTemperatureCelsius) / 65.0;
		double dryShedding = (1.0 - wetness) * smoothStep(0.25, 0.85, spin) / 420.0;
		double sublimation = (1.0 - wetness) * (1.0 - freezingTemperatureFactor(ambientTemperatureCelsius)) / 900.0;
		return MathUtil.clamp(warmMelt + dryShedding + sublimation, 0.0, 0.05);
	}

	public static double freezingHumidityEquivalentWetness(double ambientTemperatureCelsius, double humidity) {
		double freezingFactor = freezingTemperatureFactor(ambientTemperatureCelsius);
		if (freezingFactor <= EPSILON) {
			return 0.0;
		}
		double humidAir = MathUtil.clamp(humidity, 0.0, 1.0);
		double saturationFactor = smoothStep(FREEZING_HUMIDITY_ONSET, FREEZING_HUMIDITY_FULL, humidAir);
		return MathUtil.clamp(
				MAX_FREEZING_HUMIDITY_EQUIVALENT_WETNESS * freezingFactor * saturationFactor,
				0.0,
				MAX_FREEZING_HUMIDITY_EQUIVALENT_WETNESS
		);
	}

	public static double freezingTemperatureFactor(double ambientTemperatureCelsius) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			return 0.0;
		}
		double onset = 1.0 - smoothStep(-1.0, 1.0, ambientTemperatureCelsius);
		double deepColdFade = 1.0 - 0.28 * smoothStep(18.0, 28.0, -ambientTemperatureCelsius);
		return MathUtil.clamp(onset * deepColdFade, 0.0, 1.0);
	}

	public static double icingThrustScale(double icingSeverity) {
		double severity = MathUtil.clamp(icingSeverity, 0.0, 1.25);
		return MathUtil.clamp(1.0 - PROJECTED_CT_LOSS_MEDIAN_PERCENT * 0.01 * severity, 0.70, 1.0);
	}

	public static double icingPowerScale(double icingSeverity) {
		double severity = MathUtil.clamp(icingSeverity, 0.0, 1.25);
		return MathUtil.clamp(1.0 + PROJECTED_POWER_REQUIRED_MEDIAN_PERCENT * 0.01 * severity, 1.0, 1.90);
	}

	public static double icingAerodynamicLoadFactor(double icingSeverity) {
		double severity = MathUtil.clamp(icingSeverity, 0.0, 1.25);
		return 0.24 * Math.pow(severity, 0.82);
	}

	public static double icingMechanicalLossTorqueScale(double icingSeverity) {
		double severity = MathUtil.clamp(icingSeverity, 0.0, 1.25);
		return MathUtil.clamp(1.0 + 0.55 * severity, 1.0, 1.75);
	}

	private static RowTypeCounts rowTypeCounts() {
		return new RowTypeCounts(
				PACKET_ROW_COUNT,
				SOURCE_INVENTORY_ROW_COUNT,
				CONDITION_GRID_ROW_COUNT,
				TABLE4_CASE_ROW_COUNT,
				HEIGHT_RATIO_ROW_COUNT,
				TEMPERATURE_RATIO_ROW_COUNT,
				MVD_RATIO_ROW_COUNT,
				DISTRIBUTION_ROW_COUNT,
				CURRENT_MODEL_COMPARISON_ROW_COUNT,
				EXTREME_CASE_ROW_COUNT,
				METHOD_ROW_COUNT
		);
	}

	private static SourceInventoryAudit sourceInventory() {
		return new SourceInventoryAudit(
				PAPER_TITLE,
				DOI,
				LICENSE,
				SOURCE_ROTOR_BLADE_COUNT,
				SOURCE_ROTOR_DIAMETER_METERS,
				"NACA 4412",
				SOURCE_ROTOR_MAX_TIP_SPEED_METERS_PER_SECOND,
				TABLE4_RPM,
				TABLE4_PITCH_DEGREES,
				TABLE4_LAMBDA_G_DM2_H,
				TABLE4_EQUIVALENT_RAIN_MM_H,
				TABLE4_TIP_SPEED_METERS_PER_SECOND,
				DRY_HEIGHT_CURVE_FIT_ERROR_PERCENT,
				H4_CLOUD_INTENSITY_MULTIPLIER_LOW,
				H4_CLOUD_INTENSITY_MULTIPLIER_HIGH
		);
	}

	private static DistributionAudit distribution() {
		return new DistributionAudit(
				ABS_CT_STAR_RATE_MIN_PERCENT_PER_SECOND,
				ABS_CT_STAR_RATE_MEDIAN_PERCENT_PER_SECOND,
				ABS_CT_STAR_RATE_MAX_PERCENT_PER_SECOND,
				CQ_STAR_RATE_MEDIAN_PERCENT_PER_SECOND,
				P_PLUS_RATE_MEDIAN_PERCENT_PER_SECOND,
				ICING_TIME_MIN_SECONDS,
				ICING_TIME_MEDIAN_SECONDS,
				ICING_TIME_MAX_SECONDS,
				PROJECTED_CT_LOSS_MIN_PERCENT,
				PROJECTED_CT_LOSS_MEDIAN_PERCENT,
				PROJECTED_CT_LOSS_MAX_PERCENT,
				PROJECTED_CQ_INCREASE_MEDIAN_PERCENT,
				PROJECTED_POWER_REQUIRED_MIN_PERCENT,
				PROJECTED_POWER_REQUIRED_MEDIAN_PERCENT,
				PROJECTED_POWER_REQUIRED_MAX_PERCENT
		);
	}

	private static CurrentModelComparisonAudit currentModelComparison() {
		return new CurrentModelComparisonAudit(
				CURRENT_FULL_WETNESS_RAIN_LOSS_PERCENT,
				PROJECTED_CT_LOSS_MIN_PERCENT / CURRENT_FULL_WETNESS_RAIN_LOSS_PERCENT,
				CT_LOSS_MEDIAN_OVER_CURRENT_RAIN_LOSS,
				CT_LOSS_MAX_OVER_CURRENT_RAIN_LOSS,
				"keep separate; icing is time-accumulating frozen contamination"
		);
	}

	private static RuntimeModelAudit runtimeModel() {
		double severityMax = 1.25;
		return new RuntimeModelAudit(
				ICING_TIME_MEDIAN_SECONDS,
				PROJECTED_CT_LOSS_MEDIAN_PERCENT,
				icingThrustScale(1.0),
				PROJECTED_POWER_REQUIRED_MEDIAN_PERCENT,
				icingPowerScale(1.0),
				severityMax,
				(1.0 - icingThrustScale(severityMax)) * 100.0,
				icingPowerScale(severityMax),
				icingSeverityRatePerSecond(-8.0, 1.0, 1.0),
				icingSeverityRatePerSecond(-8.0, 0.5, 1.0),
				icingRecoveryRatePerSecond(5.0, 0.0, 1.0)
		);
	}

	private static ExtremeCaseAudit extremeCase() {
		return new ExtremeCaseAudit(
				"MVD120_T-15_h4m",
				23.956,
				"MVD800_T-5_h4m",
				9.132,
				"MVD120_T-5_h4m",
				89.49,
				"MVD800_T-5_h4m",
				761.0
		);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
