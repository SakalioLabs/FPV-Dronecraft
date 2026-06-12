package com.tenicana.dronecraft.sim;

public final class RateEnvelopeCalibration {
	public static final String APDRONE_RATE_ENVELOPE_SOURCE_ID = "APDrone-Mendeley-Blackbox";
	public static final String APDRONE_RATE_ENVELOPE_SELECTION = "Betaflight-Actual-urban-670";
	public static final int APDRONE_BETAFLIGHT_RATES_TYPE = 3;
	public static final String APDRONE_BETAFLIGHT_RATES_TYPE_NAME = "ACTUAL";
	public static final double APDRONE_BETAFLIGHT_ACTUAL_RC_RATE = 7.0;
	public static final double APDRONE_BETAFLIGHT_ACTUAL_CENTER_SENSITIVITY_DEGREES_PER_SECOND = 70.0;
	public static final double APDRONE_BETAFLIGHT_RATE_LIMIT_DEGREES_PER_SECOND = 1998.0;
	public static final double APDRONE_DUMP_OPEN_FIELD_ACTUAL_RATE_DEGREES_PER_SECOND = 300.0;
	public static final double APDRONE_URBAN_BATTERY_ACTUAL_RATE_DEGREES_PER_SECOND = 670.0;
	public static final double APDRONE_SELECTED_ACTUAL_RATE_DEGREES_PER_SECOND =
			APDRONE_URBAN_BATTERY_ACTUAL_RATE_DEGREES_PER_SECOND;
	public static final double APDRONE_SELECTED_ACTUAL_EXPO_FRACTION = 0.5;
	public static final double APDRONE_SELECTED_PROJECT_EQUIVALENT_SUPER_RATE =
			0.791044776119403;
	public static final double APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_25_DEGREES_PER_SECOND =
			36.3232421875;
	public static final double APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_50_DEGREES_PER_SECOND =
			114.6875;
	public static final double APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_75_DEGREES_PER_SECOND =
			274.6435546875;

	private RateEnvelopeCalibration() {
	}

	public record AxisRateAudit(
			String axis,
			double configuredMaxRateDegreesPerSecond,
			double configuredRateExpo,
			double configuredRateSuper,
			double configuredCenterSensitivityDegreesPerSecond,
			double configuredMaxOverSelectedReferenceMax,
			double configuredMaxOverDumpReferenceMax,
			double configuredMaxOverBetaflightRateLimit,
			double configuredCenterOverReferenceCenter,
			double configuredRateAtStick25DegreesPerSecond,
			double configuredRateAtStick50DegreesPerSecond,
			double configuredRateAtStick75DegreesPerSecond,
			double selectedReferenceRateAtStick25DegreesPerSecond,
			double selectedReferenceRateAtStick50DegreesPerSecond,
			double selectedReferenceRateAtStick75DegreesPerSecond
	) {
	}

	public record RateEnvelopeAudit(
			String sourceId,
			String selection,
			int betaflightRatesType,
			String betaflightRatesTypeName,
			double betaflightActualRcRate,
			double referenceCenterSensitivityDegreesPerSecond,
			double selectedReferenceMaxRateDegreesPerSecond,
			double dumpOpenFieldReferenceMaxRateDegreesPerSecond,
			double betaflightRateLimitDegreesPerSecond,
			double selectedReferenceExpoFraction,
			double selectedProjectEquivalentSuperRate,
			AxisRateAudit roll,
			AxisRateAudit pitch,
			AxisRateAudit yaw
	) {
	}

	public static RateEnvelopeAudit apDroneRateEnvelopeAudit(DroneConfig config) {
		return new RateEnvelopeAudit(
				APDRONE_RATE_ENVELOPE_SOURCE_ID,
				APDRONE_RATE_ENVELOPE_SELECTION,
				APDRONE_BETAFLIGHT_RATES_TYPE,
				APDRONE_BETAFLIGHT_RATES_TYPE_NAME,
				APDRONE_BETAFLIGHT_ACTUAL_RC_RATE,
				APDRONE_BETAFLIGHT_ACTUAL_CENTER_SENSITIVITY_DEGREES_PER_SECOND,
				APDRONE_SELECTED_ACTUAL_RATE_DEGREES_PER_SECOND,
				APDRONE_DUMP_OPEN_FIELD_ACTUAL_RATE_DEGREES_PER_SECOND,
				APDRONE_BETAFLIGHT_RATE_LIMIT_DEGREES_PER_SECOND,
				APDRONE_SELECTED_ACTUAL_EXPO_FRACTION,
				APDRONE_SELECTED_PROJECT_EQUIVALENT_SUPER_RATE,
				axis(
						"roll",
						Math.toDegrees(config.maxRollRateRadiansPerSecond()),
						config.rateExpo().z(),
						config.rateSuper().z()
				),
				axis(
						"pitch",
						Math.toDegrees(config.maxPitchRateRadiansPerSecond()),
						config.rateExpo().x(),
						config.rateSuper().x()
				),
				axis(
						"yaw",
						Math.toDegrees(config.maxYawRateRadiansPerSecond()),
						config.rateExpo().y(),
						config.rateSuper().y()
				)
		);
	}

	public static double projectSuperForActualProfile(
			double maxRateDegreesPerSecond,
			double centerSensitivityDegreesPerSecond,
			double expoFraction
	) {
		if (maxRateDegreesPerSecond <= 0.0 || (1.0 - expoFraction) <= 1.0e-9) {
			return 0.0;
		}
		return 1.0 - (centerSensitivityDegreesPerSecond / maxRateDegreesPerSecond) / (1.0 - expoFraction);
	}

	public static double projectRateDegreesPerSecond(
			double stick,
			double maxRateDegreesPerSecond,
			double expo,
			double superRate
	) {
		double command = clamp(stick, -1.0, 1.0);
		double expoClamped = clamp(expo, 0.0, 1.0);
		double superClamped = clamp(superRate, 0.0, 0.95);
		double centerFraction = (1.0 - expoClamped) * (1.0 - superClamped);
		double commandAbs = Math.abs(command);
		double commandSquared = command * command;
		double commandFifth = commandSquared * commandSquared * command;
		double expoCurve = commandAbs * (commandFifth * expoClamped + command * (1.0 - expoClamped));
		double stickMovementFraction = Math.max(0.0, 1.0 - centerFraction);
		double shaped = command * centerFraction + stickMovementFraction * expoCurve;
		return maxRateDegreesPerSecond * clamp(shaped, -1.0, 1.0);
	}

	public static double betaflightActualRateDegreesPerSecond(
			double stick,
			double maxRateDegreesPerSecond,
			double centerSensitivityDegreesPerSecond,
			double expoFraction
	) {
		double command = clamp(stick, -1.0, 1.0);
		double commandAbs = Math.abs(command);
		double expoClamped = clamp(expoFraction, 0.0, 1.0);
		double commandSquared = command * command;
		double commandFifth = commandSquared * commandSquared * command;
		double expof = commandAbs * (commandFifth * expoClamped + command * (1.0 - expoClamped));
		double stickMovement = Math.max(0.0, maxRateDegreesPerSecond - centerSensitivityDegreesPerSecond);
		return command * centerSensitivityDegreesPerSecond + stickMovement * expof;
	}

	private static AxisRateAudit axis(
			String axis,
			double configuredMaxRateDegreesPerSecond,
			double configuredRateExpo,
			double configuredRateSuper
	) {
		double centerSensitivity = configuredMaxRateDegreesPerSecond
				* (1.0 - configuredRateExpo)
				* (1.0 - configuredRateSuper);
		return new AxisRateAudit(
				axis,
				configuredMaxRateDegreesPerSecond,
				configuredRateExpo,
				configuredRateSuper,
				centerSensitivity,
				ratio(configuredMaxRateDegreesPerSecond, APDRONE_SELECTED_ACTUAL_RATE_DEGREES_PER_SECOND),
				ratio(configuredMaxRateDegreesPerSecond, APDRONE_DUMP_OPEN_FIELD_ACTUAL_RATE_DEGREES_PER_SECOND),
				ratio(configuredMaxRateDegreesPerSecond, APDRONE_BETAFLIGHT_RATE_LIMIT_DEGREES_PER_SECOND),
				ratio(centerSensitivity, APDRONE_BETAFLIGHT_ACTUAL_CENTER_SENSITIVITY_DEGREES_PER_SECOND),
				projectRateDegreesPerSecond(0.25, configuredMaxRateDegreesPerSecond, configuredRateExpo, configuredRateSuper),
				projectRateDegreesPerSecond(0.50, configuredMaxRateDegreesPerSecond, configuredRateExpo, configuredRateSuper),
				projectRateDegreesPerSecond(0.75, configuredMaxRateDegreesPerSecond, configuredRateExpo, configuredRateSuper),
				APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_25_DEGREES_PER_SECOND,
				APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_50_DEGREES_PER_SECOND,
				APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_75_DEGREES_PER_SECOND
		);
	}

	private static double ratio(double numerator, double denominator) {
		return denominator == 0.0 ? 0.0 : numerator / denominator;
	}

	private static double clamp(double value, double min, double max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
