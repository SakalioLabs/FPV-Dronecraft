package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DroneEnvironmentTest {
	@Test
	void rotorWindSamplesAreSanitizedCopiedAndFallbackToMeanWind() {
		Vec3 meanWind = new Vec3(2.0, 0.0, -1.0);
		Vec3[] rotorWinds = {
				new Vec3(2.5, 1.0, -1.0),
				null,
				new Vec3(120.0, -120.0, 0.0)
		};
		Vec3[] diskGradients = {
				new Vec3(0.5, 0.0, 0.0),
				null,
				new Vec3(50.0, -50.0, 0.0)
		};

		DroneEnvironment environment = new DroneEnvironment(
				meanWind,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				rotorWinds,
				diskGradients
		);

		assertEquals(new Vec3(2.5, 1.0, -1.0), environment.rotorWindVelocityWorldMetersPerSecond(0));
		assertEquals(Vec3.ZERO, environment.rotorWindVelocityWorldMetersPerSecond(1));
		assertEquals(new Vec3(30.0, -30.0, 0.0), environment.rotorWindVelocityWorldMetersPerSecond(2));
		assertEquals(meanWind, environment.rotorWindVelocityWorldMetersPerSecond(3));
		assertEquals(new Vec3(0.5, 0.0, 0.0), environment.rotorDiskWindGradientBodyMetersPerSecond(0));
		assertEquals(Vec3.ZERO, environment.rotorDiskWindGradientBodyMetersPerSecond(1));
		assertEquals(new Vec3(12.0, -12.0, 0.0), environment.rotorDiskWindGradientBodyMetersPerSecond(2));
		assertEquals(Vec3.ZERO, environment.rotorDiskWindGradientBodyMetersPerSecond(3));
		assertEquals(Math.sqrt(288.0), environment.maxRotorDiskWindGradientMetersPerSecond(), 1.0e-9);

		Vec3[] copy = environment.rotorWindVelocityWorldMetersPerSecond();
		copy[0] = new Vec3(9.0, 9.0, 9.0);
		assertEquals(new Vec3(2.5, 1.0, -1.0), environment.rotorWindVelocityWorldMetersPerSecond(0));
		Vec3[] gradientCopy = environment.rotorDiskWindGradientBodyMetersPerSecond();
		gradientCopy[0] = new Vec3(9.0, 9.0, 9.0);
		assertEquals(new Vec3(0.5, 0.0, 0.0), environment.rotorDiskWindGradientBodyMetersPerSecond(0));
	}

	@Test
	void windSourceTelemetryIsSanitizedAndClamped() {
		DroneEnvironment environment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				null,
				"A4MC Core!",
				true,
				4.0,
				12.0,
				-1.0,
				48.0,
				true,
				true,
				120.0,
				true,
				1.8,
				2.0,
				1.8
		);

		assertEquals("a4mc_core_", environment.windSourceId());
		assertEquals(true, environment.windSourceTrustedForGameplay());
		assertEquals(1.0, environment.windSourceConfidence(), 1.0e-9);
		assertEquals(5.0, environment.windShearMagnitudePerBlock(), 1.0e-9);
		assertEquals(0.0, environment.windShelterFactor(), 1.0e-9);
		assertEquals(12.0, environment.windUpdraftMetersPerSecond(), 1.0e-9);
		assertEquals(true, environment.windSourceLocalVoxelFlow());
		assertEquals(true, environment.windSourceHasTemperature());
		assertEquals(65.0, environment.windSourceTemperatureCelsius(), 1.0e-9);
		assertEquals(true, environment.windSourceHasHumidity());
		assertEquals(1.0, environment.windSourceHumidity(), 1.0e-9);
		assertEquals(1.0, environment.windSourceAblStability(), 1.0e-9);
		assertEquals(1.0, environment.windSourceAblMixingStrength(), 1.0e-9);
	}
}
