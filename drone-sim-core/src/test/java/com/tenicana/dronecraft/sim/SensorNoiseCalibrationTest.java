package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SensorNoiseCalibrationTest {
	@Test
	void apDroneImuNoiseAuditMatchesMendeleyBlackboxLowMotionReference() {
		SensorNoiseCalibration.ImuNoiseAudit audit =
				SensorNoiseCalibration.apDroneImuNoiseAudit(DroneConfig.apDrone());

		assertEquals("APDrone-Mendeley-Blackbox", audit.sourceId());
		assertEquals("zero_throttle_low_motion", audit.lowMotionSelection());
		assertEquals(43, audit.lowMotionSegmentCount());
		assertEquals(21, audit.lowMotionSourceFileCount());
		assertEquals(177_026, audit.lowMotionSampleCount());
		assertEquals(89.17872299999983, audit.lowMotionDurationSeconds(), 1.0e-12);
		assertEquals(0.02490097352780192, audit.configuredGyroNoiseRadiansPerSecond(), 1.0e-15);
		assertEquals(0.1212572174381882, audit.configuredAccelerometerNoiseMetersPerSecondSquared(), 1.0e-15);
		assertEquals(125.0, audit.configuredGyroLowPassHertz(), 1.0e-12);
		assertEquals(80.0, audit.configuredAccelerometerLowPassHertz(), 1.0e-12);
		assertEquals(0.011012163404200583, audit.strictStaticGyroVectorRmsRadiansPerSecond(), 1.0e-15);
		assertEquals(0.05378802454365603, audit.strictStaticAccelerometerVectorRmsMetersPerSecondSquared(), 1.0e-15);
		assertEquals(0.005343137816697945, audit.lowMotionGyroVectorRmsP50RadiansPerSecond(), 1.0e-15);
		assertEquals(0.02490097352780192, audit.lowMotionGyroVectorRmsP90RadiansPerSecond(), 1.0e-15);
		assertEquals(0.031544741552925394, audit.lowMotionAccelerometerVectorRmsP50MetersPerSecondSquared(), 1.0e-15);
		assertEquals(0.1212572174381882, audit.lowMotionAccelerometerVectorRmsP90MetersPerSecondSquared(), 1.0e-15);
		assertEquals(2.2612244854906041, audit.configuredGyroOverStrictStatic(), 1.0e-15);
		assertEquals(4.6603651977651408, audit.configuredGyroOverLowMotionP50(), 1.0e-15);
		assertEquals(1.0, audit.configuredGyroOverLowMotionP90(), 1.0e-15);
		assertEquals(2.2543534265656491, audit.configuredAccelerometerOverStrictStatic(), 1.0e-15);
		assertEquals(3.8439756190344521, audit.configuredAccelerometerOverLowMotionP50(), 1.0e-15);
		assertEquals(1.0, audit.configuredAccelerometerOverLowMotionP90(), 1.0e-15);
		assertEquals(0.0622784233803418, audit.configuredQuietBarometerNoiseAmplitudeMeters(), 1.0e-15);
		assertEquals(0.0473254554729487, audit.configuredQuietBarometerNoiseRmsMeters(), 1.0e-15);
	}

	@Test
	void apDroneBarometerNoiseAuditMatchesMendeleyBlackboxLowMotionReference() {
		SensorNoiseCalibration.BarometerNoiseAudit audit =
				SensorNoiseCalibration.apDroneBarometerNoiseAudit(DroneConfig.apDrone());

		assertEquals("APDrone-Mendeley-Blackbox", audit.sourceId());
		assertEquals("zero_throttle_low_motion", audit.lowMotionSelection());
		assertEquals("strict_static", audit.strictStaticSelection());
		assertEquals(43, audit.lowMotionSegmentCount());
		assertEquals(21, audit.lowMotionSourceFileCount());
		assertEquals(177_026, audit.lowMotionSampleCount());
		assertEquals(89.17872299999983, audit.lowMotionDurationSeconds(), 1.0e-12);
		assertEquals(1, audit.strictStaticSegmentCount());
		assertEquals(1, audit.strictStaticSourceFileCount());
		assertEquals(1_217, audit.strictStaticSampleCount());
		assertEquals(0.6069669999999974, audit.strictStaticDurationSeconds(), 1.0e-15);
		assertEquals(0.0622784233803418, audit.configuredQuietBarometerNoiseAmplitudeMeters(), 1.0e-15);
		assertEquals(0.0473254554729487, audit.configuredQuietBarometerNoiseRmsMeters(), 1.0e-15);
		assertEquals(0.090, audit.configuredAltitudeTimeConstantSeconds(), 1.0e-12);
		assertEquals(0.180, audit.configuredVerticalSpeedTimeConstantSeconds(), 1.0e-12);
		assertEquals(0.016648427966986585, audit.dps310PressureNoiseAltitudeMeters(), 1.0e-15);
		assertEquals(0.07234794487121128, audit.lowMotionBarometerAltitudeStdP50Meters(), 1.0e-15);
		assertEquals(0.15701987989708205, audit.lowMotionBarometerAltitudeStdP90Meters(), 1.0e-15);
		assertEquals(0.0473254554729487, audit.lowMotionDetrendedStdP50Meters(), 1.0e-15);
		assertEquals(0.13125824407872994, audit.lowMotionDetrendedStdP90Meters(), 1.0e-15);
		assertEquals(0.21, audit.lowMotionPeakToPeakP50Meters(), 1.0e-12);
		assertEquals(0.958, audit.lowMotionPeakToPeakP90Meters(), 1.0e-12);
		assertEquals(0.03734281684447072, audit.lowMotionAbsSlopeP50MetersPerSecond(), 1.0e-15);
		assertEquals(0.6436827196710759, audit.lowMotionAbsSlopeP90MetersPerSecond(), 1.0e-15);
		assertEquals(0.0448688059126939, audit.strictStaticDetrendedStdMeters(), 1.0e-15);
		assertEquals(1.0, audit.configuredRmsOverLowMotionDetrendedP50(), 1.0e-12);
		assertEquals(0.36055225182322594, audit.configuredRmsOverLowMotionDetrendedP90(), 1.0e-12);
		assertEquals(1.054751837279435, audit.configuredRmsOverStrictStaticDetrended(), 1.0e-12);
		assertEquals(2.8426380897219783, audit.configuredRmsOverDps310PressureNoise(), 1.0e-12);
		assertEquals(2.8426380897219783, audit.lowMotionDetrendedP50OverDps310PressureNoise(), 1.0e-12);
		assertEquals(7.884122413179895, audit.lowMotionDetrendedP90OverDps310PressureNoise(), 1.0e-12);
	}
}
