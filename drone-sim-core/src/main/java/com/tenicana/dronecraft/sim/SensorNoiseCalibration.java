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
	public static final double QUIET_BAROMETER_ACCEL_NOISE_TO_ALTITUDE_AMPLITUDE = 0.035;
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
