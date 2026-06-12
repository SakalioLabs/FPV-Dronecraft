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
		assertEquals(0.004243, audit.configuredQuietBarometerNoiseAmplitudeMeters(), 2.0e-6);
		assertEquals(0.0032250231406140634, audit.configuredQuietBarometerNoiseRmsMeters(), 1.0e-15);
	}
}
