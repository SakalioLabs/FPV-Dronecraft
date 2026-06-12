package com.tenicana.dronecraft.sim;

public final class SensorNoiseCalibration {
	public static final String APDRONE_IMU_SOURCE_ID = "APDrone-Mendeley-Blackbox";
	public static final String APDRONE_IMU_LOW_MOTION_SELECTION = "zero_throttle_low_motion";
	public static final String APDRONE_IMU_STRICT_STATIC_SELECTION = "strict_static";
	public static final int APDRONE_IMU_LOW_MOTION_SEGMENT_COUNT = 43;
	public static final int APDRONE_IMU_LOW_MOTION_SOURCE_FILE_COUNT = 21;
	public static final int APDRONE_IMU_LOW_MOTION_SAMPLE_COUNT = 177_026;
	public static final double APDRONE_IMU_LOW_MOTION_DURATION_SECONDS = 89.17872299999983;
	public static final double APDRONE_IMU_STRICT_STATIC_GYRO_VECTOR_RMS_RADIANS_PER_SECOND =
			0.011012163404200583;
	public static final double APDRONE_IMU_STRICT_STATIC_ACCEL_VECTOR_RMS_METERS_PER_SECOND_SQUARED =
			0.05378802454365603;
	public static final double APDRONE_IMU_LOW_MOTION_GYRO_VECTOR_RMS_P50_RADIANS_PER_SECOND =
			0.005343137816697945;
	public static final double APDRONE_IMU_LOW_MOTION_GYRO_VECTOR_RMS_P90_RADIANS_PER_SECOND =
			0.02490097352780192;
	public static final double APDRONE_IMU_LOW_MOTION_ACCEL_VECTOR_RMS_P50_METERS_PER_SECOND_SQUARED =
			0.031544741552925394;
	public static final double APDRONE_IMU_LOW_MOTION_ACCEL_VECTOR_RMS_P90_METERS_PER_SECOND_SQUARED =
			0.1212572174381882;
	public static final double APDRONE_IMU_REFERENCE_GYRO_LPF_HERTZ = 125.0;
	public static final double APDRONE_IMU_REFERENCE_ACCEL_LPF_HERTZ = 80.0;
	public static final String APDRONE_BAROMETER_SOURCE_ID = APDRONE_IMU_SOURCE_ID;
	public static final int APDRONE_BAROMETER_STRICT_STATIC_SEGMENT_COUNT = 1;
	public static final int APDRONE_BAROMETER_STRICT_STATIC_SOURCE_FILE_COUNT = 1;
	public static final int APDRONE_BAROMETER_STRICT_STATIC_SAMPLE_COUNT = 1_217;
	public static final double APDRONE_BAROMETER_STRICT_STATIC_DURATION_SECONDS = 0.6069669999999974;
	public static final double APDRONE_BAROMETER_STRICT_STATIC_DETRENDED_STD_METERS =
			0.0448688059126939;
	public static final double APDRONE_BAROMETER_LOW_MOTION_ALTITUDE_STD_P50_METERS =
			0.07234794487121128;
	public static final double APDRONE_BAROMETER_LOW_MOTION_ALTITUDE_STD_P90_METERS =
			0.15701987989708205;
	public static final double APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P50_METERS =
			0.0473254554729487;
	public static final double APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P90_METERS =
			0.13125824407872994;
	public static final double APDRONE_BAROMETER_LOW_MOTION_PEAK_TO_PEAK_P50_METERS = 0.21;
	public static final double APDRONE_BAROMETER_LOW_MOTION_PEAK_TO_PEAK_P90_METERS = 0.958;
	public static final double APDRONE_BAROMETER_LOW_MOTION_ABS_SLOPE_P50_METERS_PER_SECOND =
			0.03734281684447072;
	public static final double APDRONE_BAROMETER_LOW_MOTION_ABS_SLOPE_P90_METERS_PER_SECOND =
			0.6436827196710759;
	public static final double DPS310_PRESSURE_NOISE_ALTITUDE_METERS = 0.016648427966986585;
	public static final double BAROMETER_ALTITUDE_TIME_CONSTANT_SECONDS = 0.090;
	public static final double BAROMETER_VERTICAL_SPEED_TIME_CONSTANT_SECONDS = 0.180;
	public static final double QUIET_BAROMETER_ACCEL_NOISE_TO_ALTITUDE_AMPLITUDE = 0.513605908960336;
	public static final double QUIET_BAROMETER_SECOND_HARMONIC_SCALE = 0.35;
	public static final double QUIET_BAROMETER_THIRD_HARMONIC_SCALE = 0.18;

	private SensorNoiseCalibration() {
	}

	public record ImuNoiseAudit(
			String sourceId,
			String lowMotionSelection,
			int lowMotionSegmentCount,
			int lowMotionSourceFileCount,
			int lowMotionSampleCount,
			double lowMotionDurationSeconds,
			double configuredGyroNoiseRadiansPerSecond,
			double configuredAccelerometerNoiseMetersPerSecondSquared,
			double configuredGyroLowPassHertz,
			double configuredAccelerometerLowPassHertz,
			double strictStaticGyroVectorRmsRadiansPerSecond,
			double strictStaticAccelerometerVectorRmsMetersPerSecondSquared,
			double lowMotionGyroVectorRmsP50RadiansPerSecond,
			double lowMotionGyroVectorRmsP90RadiansPerSecond,
			double lowMotionAccelerometerVectorRmsP50MetersPerSecondSquared,
			double lowMotionAccelerometerVectorRmsP90MetersPerSecondSquared,
			double configuredGyroOverStrictStatic,
			double configuredGyroOverLowMotionP50,
			double configuredGyroOverLowMotionP90,
			double configuredAccelerometerOverStrictStatic,
			double configuredAccelerometerOverLowMotionP50,
			double configuredAccelerometerOverLowMotionP90,
			double configuredQuietBarometerNoiseAmplitudeMeters,
			double configuredQuietBarometerNoiseRmsMeters
	) {
	}

	public record BarometerNoiseAudit(
			String sourceId,
			String lowMotionSelection,
			String strictStaticSelection,
			int lowMotionSegmentCount,
			int lowMotionSourceFileCount,
			int lowMotionSampleCount,
			double lowMotionDurationSeconds,
			int strictStaticSegmentCount,
			int strictStaticSourceFileCount,
			int strictStaticSampleCount,
			double strictStaticDurationSeconds,
			double configuredQuietBarometerNoiseAmplitudeMeters,
			double configuredQuietBarometerNoiseRmsMeters,
			double configuredAltitudeTimeConstantSeconds,
			double configuredVerticalSpeedTimeConstantSeconds,
			double dps310PressureNoiseAltitudeMeters,
			double lowMotionBarometerAltitudeStdP50Meters,
			double lowMotionBarometerAltitudeStdP90Meters,
			double lowMotionDetrendedStdP50Meters,
			double lowMotionDetrendedStdP90Meters,
			double lowMotionPeakToPeakP50Meters,
			double lowMotionPeakToPeakP90Meters,
			double lowMotionAbsSlopeP50MetersPerSecond,
			double lowMotionAbsSlopeP90MetersPerSecond,
			double strictStaticDetrendedStdMeters,
			double configuredRmsOverLowMotionDetrendedP50,
			double configuredRmsOverLowMotionDetrendedP90,
			double configuredRmsOverStrictStaticDetrended,
			double configuredRmsOverDps310PressureNoise,
			double lowMotionDetrendedP50OverDps310PressureNoise,
			double lowMotionDetrendedP90OverDps310PressureNoise
	) {
	}

	public static ImuNoiseAudit apDroneImuNoiseAudit(DroneConfig config) {
		return new ImuNoiseAudit(
				APDRONE_IMU_SOURCE_ID,
				APDRONE_IMU_LOW_MOTION_SELECTION,
				APDRONE_IMU_LOW_MOTION_SEGMENT_COUNT,
				APDRONE_IMU_LOW_MOTION_SOURCE_FILE_COUNT,
				APDRONE_IMU_LOW_MOTION_SAMPLE_COUNT,
				APDRONE_IMU_LOW_MOTION_DURATION_SECONDS,
				config.gyroNoiseStdDevRadiansPerSecond(),
				config.accelerometerNoiseStdDevMetersPerSecondSquared(),
				config.gyroLowPassCutoffHz(),
				config.accelerometerLowPassCutoffHz(),
				APDRONE_IMU_STRICT_STATIC_GYRO_VECTOR_RMS_RADIANS_PER_SECOND,
				APDRONE_IMU_STRICT_STATIC_ACCEL_VECTOR_RMS_METERS_PER_SECOND_SQUARED,
				APDRONE_IMU_LOW_MOTION_GYRO_VECTOR_RMS_P50_RADIANS_PER_SECOND,
				APDRONE_IMU_LOW_MOTION_GYRO_VECTOR_RMS_P90_RADIANS_PER_SECOND,
				APDRONE_IMU_LOW_MOTION_ACCEL_VECTOR_RMS_P50_METERS_PER_SECOND_SQUARED,
				APDRONE_IMU_LOW_MOTION_ACCEL_VECTOR_RMS_P90_METERS_PER_SECOND_SQUARED,
				ratio(config.gyroNoiseStdDevRadiansPerSecond(), APDRONE_IMU_STRICT_STATIC_GYRO_VECTOR_RMS_RADIANS_PER_SECOND),
				ratio(config.gyroNoiseStdDevRadiansPerSecond(), APDRONE_IMU_LOW_MOTION_GYRO_VECTOR_RMS_P50_RADIANS_PER_SECOND),
				ratio(config.gyroNoiseStdDevRadiansPerSecond(), APDRONE_IMU_LOW_MOTION_GYRO_VECTOR_RMS_P90_RADIANS_PER_SECOND),
				ratio(config.accelerometerNoiseStdDevMetersPerSecondSquared(), APDRONE_IMU_STRICT_STATIC_ACCEL_VECTOR_RMS_METERS_PER_SECOND_SQUARED),
				ratio(config.accelerometerNoiseStdDevMetersPerSecondSquared(), APDRONE_IMU_LOW_MOTION_ACCEL_VECTOR_RMS_P50_METERS_PER_SECOND_SQUARED),
				ratio(config.accelerometerNoiseStdDevMetersPerSecondSquared(), APDRONE_IMU_LOW_MOTION_ACCEL_VECTOR_RMS_P90_METERS_PER_SECOND_SQUARED),
				quietBarometerNoiseAmplitudeMeters(config),
				quietBarometerNoiseRmsMeters(config)
		);
	}

	public static BarometerNoiseAudit apDroneBarometerNoiseAudit(DroneConfig config) {
		double configuredRms = quietBarometerNoiseRmsMeters(config);
		return new BarometerNoiseAudit(
				APDRONE_BAROMETER_SOURCE_ID,
				APDRONE_IMU_LOW_MOTION_SELECTION,
				APDRONE_IMU_STRICT_STATIC_SELECTION,
				APDRONE_IMU_LOW_MOTION_SEGMENT_COUNT,
				APDRONE_IMU_LOW_MOTION_SOURCE_FILE_COUNT,
				APDRONE_IMU_LOW_MOTION_SAMPLE_COUNT,
				APDRONE_IMU_LOW_MOTION_DURATION_SECONDS,
				APDRONE_BAROMETER_STRICT_STATIC_SEGMENT_COUNT,
				APDRONE_BAROMETER_STRICT_STATIC_SOURCE_FILE_COUNT,
				APDRONE_BAROMETER_STRICT_STATIC_SAMPLE_COUNT,
				APDRONE_BAROMETER_STRICT_STATIC_DURATION_SECONDS,
				quietBarometerNoiseAmplitudeMeters(config),
				configuredRms,
				BAROMETER_ALTITUDE_TIME_CONSTANT_SECONDS,
				BAROMETER_VERTICAL_SPEED_TIME_CONSTANT_SECONDS,
				DPS310_PRESSURE_NOISE_ALTITUDE_METERS,
				APDRONE_BAROMETER_LOW_MOTION_ALTITUDE_STD_P50_METERS,
				APDRONE_BAROMETER_LOW_MOTION_ALTITUDE_STD_P90_METERS,
				APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P50_METERS,
				APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P90_METERS,
				APDRONE_BAROMETER_LOW_MOTION_PEAK_TO_PEAK_P50_METERS,
				APDRONE_BAROMETER_LOW_MOTION_PEAK_TO_PEAK_P90_METERS,
				APDRONE_BAROMETER_LOW_MOTION_ABS_SLOPE_P50_METERS_PER_SECOND,
				APDRONE_BAROMETER_LOW_MOTION_ABS_SLOPE_P90_METERS_PER_SECOND,
				APDRONE_BAROMETER_STRICT_STATIC_DETRENDED_STD_METERS,
				ratio(configuredRms, APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P50_METERS),
				ratio(configuredRms, APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P90_METERS),
				ratio(configuredRms, APDRONE_BAROMETER_STRICT_STATIC_DETRENDED_STD_METERS),
				ratio(configuredRms, DPS310_PRESSURE_NOISE_ALTITUDE_METERS),
				ratio(APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P50_METERS, DPS310_PRESSURE_NOISE_ALTITUDE_METERS),
				ratio(APDRONE_BAROMETER_LOW_MOTION_DETRENDED_STD_P90_METERS, DPS310_PRESSURE_NOISE_ALTITUDE_METERS)
		);
	}

	public static double quietBarometerNoiseAmplitudeMeters(DroneConfig config) {
		return QUIET_BAROMETER_ACCEL_NOISE_TO_ALTITUDE_AMPLITUDE
				* Math.max(0.0, config.accelerometerNoiseStdDevMetersPerSecondSquared());
	}

	public static double quietBarometerNoiseRmsMeters(DroneConfig config) {
		double amplitude = quietBarometerNoiseAmplitudeMeters(config);
		double meanSquare = (
				1.0
						+ QUIET_BAROMETER_SECOND_HARMONIC_SCALE * QUIET_BAROMETER_SECOND_HARMONIC_SCALE
						+ QUIET_BAROMETER_THIRD_HARMONIC_SCALE * QUIET_BAROMETER_THIRD_HARMONIC_SCALE
		) / 2.0;
		return amplitude * Math.sqrt(meanSquare);
	}

	private static double ratio(double numerator, double denominator) {
		return denominator == 0.0 ? 0.0 : numerator / denominator;
	}
}
