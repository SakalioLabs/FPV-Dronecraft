package com.tenicana.dronecraft.sim;

public final class ControlResponseCalibration {
	public static final String APDRONE_CONTROL_RESPONSE_SOURCE_ID = "APDrone-Mendeley-Blackbox";
	public static final String APDRONE_CONTROL_RESPONSE_SELECTION = "setpoint_to_gyro_correlation";
	public static final double APDRONE_CONTROL_RESPONSE_ANALYSIS_RATE_HERTZ = 500.0;
	public static final double APDRONE_CONTROL_RESPONSE_RELIABLE_CORRELATION_THRESHOLD = 0.35;
	public static final double APDRONE_CONTROL_RESPONSE_MAX_LAG_SECONDS = 0.08;
	public static final double APDRONE_CONTROL_RESPONSE_ACTIVITY_THRESHOLD_DEGREES_PER_SECOND = 30.0;
	public static final int APDRONE_CONTROL_RESPONSE_MIN_DYNAMIC_SAMPLES = 250;
	public static final int APDRONE_CONTROL_RESPONSE_SOURCE_FILE_COUNT = 21;
	public static final int APDRONE_ROLL_RELIABLE_ROW_COUNT = 16;
	public static final int APDRONE_PITCH_RELIABLE_ROW_COUNT = 15;
	public static final int APDRONE_YAW_RELIABLE_ROW_COUNT = 6;
	public static final double APDRONE_ROLL_LAG_P10_MS = 19.950000000079626;
	public static final double APDRONE_ROLL_LAG_P50_MS = 23.952000000235785;
	public static final double APDRONE_ROLL_LAG_P90_MS = 37.93600000000197;
	public static final double APDRONE_ROLL_ABS_CORRELATION_P50 = 0.928063325272031;
	public static final double APDRONE_ROLL_ABS_CORRELATION_P90 = 0.9952843137610449;
	public static final double APDRONE_ROLL_GAIN_P50 = 1.043613999029235;
	public static final double APDRONE_ROLL_MAE_DEGREES_PER_SECOND_P50 = 6.245374193351072;
	public static final double APDRONE_PITCH_LAG_P10_MS = 10.778400000106103;
	public static final double APDRONE_PITCH_LAG_P50_MS = 13.972000000038065;
	public static final double APDRONE_PITCH_LAG_P90_MS = 16.35640000003491;
	public static final double APDRONE_PITCH_ABS_CORRELATION_P50 = 0.9433201487164501;
	public static final double APDRONE_PITCH_ABS_CORRELATION_P90 = 0.9971638242811274;
	public static final double APDRONE_PITCH_GAIN_P50 = 1.0447216907807626;
	public static final double APDRONE_PITCH_MAE_DEGREES_PER_SECOND_P50 = 5.868046571798189;
	public static final double APDRONE_YAW_LAG_P10_MS = 5.988000000058946;
	public static final double APDRONE_YAW_LAG_P50_MS = 15.968000000086136;
	public static final double APDRONE_YAW_LAG_P90_MS = 45.90800000037376;
	public static final double APDRONE_YAW_ABS_CORRELATION_P50 = 0.9894612568199653;
	public static final double APDRONE_YAW_ABS_CORRELATION_P90 = 0.9974587572330679;
	public static final double APDRONE_YAW_GAIN_P50 = 1.006794210722791;
	public static final double APDRONE_YAW_MAE_DEGREES_PER_SECOND_P50 = 4.247886914794264;

	private ControlResponseCalibration() {
	}

	public record AxisLagAudit(
			String axis,
			int fileAxisRowCount,
			int reliableRowCount,
			double lagP10Milliseconds,
			double lagP50Milliseconds,
			double lagP90Milliseconds,
			double absCorrelationP50,
			double absCorrelationP90,
			double gainP50,
			double maeDegreesPerSecondP50,
			double configuredControlLatencyMilliseconds,
			double configuredControlPlusRcLatencyMilliseconds,
			double configuredRcSmoothingTauMilliseconds,
			double rcFrameIntervalMilliseconds,
			double escFrameIntervalMilliseconds,
			double maxRateDegreesPerSecond,
			double p50LagOverControlLatency,
			double p50LagOverControlPlusRcLatency
	) {
	}

	public record ControlResponseAudit(
			String sourceId,
			String selection,
			double analysisRateHertz,
			double reliableCorrelationThreshold,
			double maxLagSeconds,
			double activityThresholdDegreesPerSecond,
			int minDynamicSamples,
			AxisLagAudit roll,
			AxisLagAudit pitch,
			AxisLagAudit yaw
	) {
	}

	public static ControlResponseAudit apDroneControlResponseAudit(DroneConfig config) {
		double controlLatencyMilliseconds = config.controlLatencySeconds() * 1000.0;
		double controlPlusRcLatencyMilliseconds =
				(config.controlLatencySeconds() + config.rcCommandLatencySeconds()) * 1000.0;
		double rcSmoothingTauMilliseconds = config.rcCommandSmoothingTimeConstantSeconds() * 1000.0;
		double rcFrameIntervalMilliseconds = frameIntervalMilliseconds(config.rcFrameRateHertz());
		double escFrameIntervalMilliseconds = frameIntervalMilliseconds(config.escCommandFrameRateHertz());
		return new ControlResponseAudit(
				APDRONE_CONTROL_RESPONSE_SOURCE_ID,
				APDRONE_CONTROL_RESPONSE_SELECTION,
				APDRONE_CONTROL_RESPONSE_ANALYSIS_RATE_HERTZ,
				APDRONE_CONTROL_RESPONSE_RELIABLE_CORRELATION_THRESHOLD,
				APDRONE_CONTROL_RESPONSE_MAX_LAG_SECONDS,
				APDRONE_CONTROL_RESPONSE_ACTIVITY_THRESHOLD_DEGREES_PER_SECOND,
				APDRONE_CONTROL_RESPONSE_MIN_DYNAMIC_SAMPLES,
				axis(
						"roll",
						APDRONE_ROLL_RELIABLE_ROW_COUNT,
						APDRONE_ROLL_LAG_P10_MS,
						APDRONE_ROLL_LAG_P50_MS,
						APDRONE_ROLL_LAG_P90_MS,
						APDRONE_ROLL_ABS_CORRELATION_P50,
						APDRONE_ROLL_ABS_CORRELATION_P90,
						APDRONE_ROLL_GAIN_P50,
						APDRONE_ROLL_MAE_DEGREES_PER_SECOND_P50,
						controlLatencyMilliseconds,
						controlPlusRcLatencyMilliseconds,
						rcSmoothingTauMilliseconds,
						rcFrameIntervalMilliseconds,
						escFrameIntervalMilliseconds,
						Math.toDegrees(config.maxRollRateRadiansPerSecond())
				),
				axis(
						"pitch",
						APDRONE_PITCH_RELIABLE_ROW_COUNT,
						APDRONE_PITCH_LAG_P10_MS,
						APDRONE_PITCH_LAG_P50_MS,
						APDRONE_PITCH_LAG_P90_MS,
						APDRONE_PITCH_ABS_CORRELATION_P50,
						APDRONE_PITCH_ABS_CORRELATION_P90,
						APDRONE_PITCH_GAIN_P50,
						APDRONE_PITCH_MAE_DEGREES_PER_SECOND_P50,
						controlLatencyMilliseconds,
						controlPlusRcLatencyMilliseconds,
						rcSmoothingTauMilliseconds,
						rcFrameIntervalMilliseconds,
						escFrameIntervalMilliseconds,
						Math.toDegrees(config.maxPitchRateRadiansPerSecond())
				),
				axis(
						"yaw",
						APDRONE_YAW_RELIABLE_ROW_COUNT,
						APDRONE_YAW_LAG_P10_MS,
						APDRONE_YAW_LAG_P50_MS,
						APDRONE_YAW_LAG_P90_MS,
						APDRONE_YAW_ABS_CORRELATION_P50,
						APDRONE_YAW_ABS_CORRELATION_P90,
						APDRONE_YAW_GAIN_P50,
						APDRONE_YAW_MAE_DEGREES_PER_SECOND_P50,
						controlLatencyMilliseconds,
						controlPlusRcLatencyMilliseconds,
						rcSmoothingTauMilliseconds,
						rcFrameIntervalMilliseconds,
						escFrameIntervalMilliseconds,
						Math.toDegrees(config.maxYawRateRadiansPerSecond())
				)
		);
	}

	private static AxisLagAudit axis(
			String axis,
			int reliableRowCount,
			double lagP10Milliseconds,
			double lagP50Milliseconds,
			double lagP90Milliseconds,
			double absCorrelationP50,
			double absCorrelationP90,
			double gainP50,
			double maeDegreesPerSecondP50,
			double configuredControlLatencyMilliseconds,
			double configuredControlPlusRcLatencyMilliseconds,
			double configuredRcSmoothingTauMilliseconds,
			double rcFrameIntervalMilliseconds,
			double escFrameIntervalMilliseconds,
			double maxRateDegreesPerSecond
	) {
		return new AxisLagAudit(
				axis,
				APDRONE_CONTROL_RESPONSE_SOURCE_FILE_COUNT,
				reliableRowCount,
				lagP10Milliseconds,
				lagP50Milliseconds,
				lagP90Milliseconds,
				absCorrelationP50,
				absCorrelationP90,
				gainP50,
				maeDegreesPerSecondP50,
				configuredControlLatencyMilliseconds,
				configuredControlPlusRcLatencyMilliseconds,
				configuredRcSmoothingTauMilliseconds,
				rcFrameIntervalMilliseconds,
				escFrameIntervalMilliseconds,
				maxRateDegreesPerSecond,
				ratio(lagP50Milliseconds, configuredControlLatencyMilliseconds),
				ratio(lagP50Milliseconds, configuredControlPlusRcLatencyMilliseconds)
		);
	}

	private static double frameIntervalMilliseconds(double frameRateHertz) {
		return frameRateHertz <= 0.0 ? 0.0 : 1000.0 / frameRateHertz;
	}

	private static double ratio(double numerator, double denominator) {
		return denominator == 0.0 ? 0.0 : numerator / denominator;
	}
}
