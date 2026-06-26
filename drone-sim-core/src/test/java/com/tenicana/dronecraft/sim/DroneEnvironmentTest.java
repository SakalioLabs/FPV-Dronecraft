package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		double[] shelterObstructions = {0.12, Double.NaN, 2.5};
		double[] localVoxelObstacleResiduals = {0.42, Double.NaN, -2.0, 2.5};
		Vec3[] pressureGradientWinds = {
				new Vec3(0.18, 0.0, 0.0),
				null,
				new Vec3(-50.0, 50.0, 0.0)
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
				diskGradients,
				shelterObstructions,
				localVoxelObstacleResiduals,
				pressureGradientWinds
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
		assertEquals(0.12, environment.rotorA4mcShelterObstruction(0), 1.0e-9);
		assertEquals(0.0, environment.rotorA4mcShelterObstruction(1), 1.0e-9);
		assertEquals(1.0, environment.rotorA4mcShelterObstruction(2), 1.0e-9);
		assertEquals(0.0, environment.rotorA4mcShelterObstruction(3), 1.0e-9);
		assertEquals(1.0, environment.maxRotorA4mcShelterObstruction(), 1.0e-9);
		assertEquals(0.42, environment.rotorLocalVoxelObstacleResidual(0), 1.0e-9);
		assertEquals(1.0, environment.rotorLocalVoxelObstacleResidual(1), 1.0e-9);
		assertEquals(0.0, environment.rotorLocalVoxelObstacleResidual(2), 1.0e-9);
		assertEquals(1.0, environment.rotorLocalVoxelObstacleResidual(3), 1.0e-9);
		assertEquals(1.0, environment.rotorLocalVoxelObstacleResidual(4), 1.0e-9);
		assertEquals(0.0, environment.minRotorLocalVoxelObstacleResidual(), 1.0e-9);
		assertEquals(new Vec3(0.18, 0.0, 0.0), environment.rotorA4mcPressureGradientWindBodyMetersPerSecond(0));
		assertEquals(Vec3.ZERO, environment.rotorA4mcPressureGradientWindBodyMetersPerSecond(1));
		assertEquals(new Vec3(-12.0, 12.0, 0.0), environment.rotorA4mcPressureGradientWindBodyMetersPerSecond(2));
		assertEquals(Vec3.ZERO, environment.rotorA4mcPressureGradientWindBodyMetersPerSecond(3));
		assertEquals(0.18, environment.rotorA4mcPressureGradientWindMagnitudeMetersPerSecond(0), 1.0e-9);
		assertEquals(Math.sqrt(288.0), environment.maxRotorA4mcPressureGradientWindMetersPerSecond(), 1.0e-9);

		Vec3[] copy = environment.rotorWindVelocityWorldMetersPerSecond();
		copy[0] = new Vec3(9.0, 9.0, 9.0);
		assertEquals(new Vec3(2.5, 1.0, -1.0), environment.rotorWindVelocityWorldMetersPerSecond(0));
		Vec3[] gradientCopy = environment.rotorDiskWindGradientBodyMetersPerSecond();
		gradientCopy[0] = new Vec3(9.0, 9.0, 9.0);
		assertEquals(new Vec3(0.5, 0.0, 0.0), environment.rotorDiskWindGradientBodyMetersPerSecond(0));
		double[] shelterCopy = environment.rotorA4mcShelterObstructions();
		shelterCopy[0] = 0.99;
		assertEquals(0.12, environment.rotorA4mcShelterObstruction(0), 1.0e-9);
		double[] residualCopy = environment.rotorLocalVoxelObstacleResiduals();
		residualCopy[0] = 0.99;
		assertEquals(0.42, environment.rotorLocalVoxelObstacleResidual(0), 1.0e-9);
		Vec3[] pressureGradientCopy = environment.rotorA4mcPressureGradientWindBodyMetersPerSecond();
		pressureGradientCopy[0] = new Vec3(9.0, 9.0, 9.0);
		assertEquals(new Vec3(0.18, 0.0, 0.0), environment.rotorA4mcPressureGradientWindBodyMetersPerSecond(0));
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
				2.0,
				12_000.0,
				12.0,
				-1.0,
				48.0,
				true,
				"L2!",
				"SERVER Authoritative",
				2_000_000L,
				-2.0,
				40.0,
				Double.NaN,
				true,
				120.0,
				true,
				1.8,
				2.0,
				1.8,
				new Vec3(64.0, -64.0, 12.0)
		);

		assertEquals("a4mc_core_", environment.windSourceId());
		assertEquals(true, environment.windSourceTrustedForGameplay());
		assertEquals(1.0, environment.windSourceConfidence(), 1.0e-9);
		assertEquals(1.5, environment.windSourceTurbulenceIntensity(), 1.0e-9);
		assertEquals(5000.0, environment.windSourcePressureAnomalyPascals(), 1.0e-9);
		assertEquals(5.0, environment.windShearMagnitudePerBlock(), 1.0e-9);
		assertEquals(0.0, environment.windShelterFactor(), 1.0e-9);
		assertEquals(12.0, environment.windUpdraftMetersPerSecond(), 1.0e-9);
		assertEquals(true, environment.windSourceLocalVoxelFlow());
		assertEquals("l2_", environment.windSourceLevel());
		assertEquals("server_authoritative", environment.windSourceAuthority());
		assertEquals(1_000_000L, environment.windSourceFreshnessAgeTicks());
		assertEquals(0.0, environment.windSourceMeanSpeedMetersPerSecond(), 1.0e-9);
		assertEquals(30.0, environment.windSourceEffectiveSpeedMetersPerSecond(), 1.0e-9);
		assertEquals(0.0, environment.windSourceGustSpeedMetersPerSecond(), 1.0e-9);
		assertEquals(true, environment.windSourceHasTemperature());
		assertEquals(65.0, environment.windSourceTemperatureCelsius(), 1.0e-9);
		assertEquals(true, environment.windSourceHasHumidity());
		assertEquals(1.0, environment.windSourceHumidity(), 1.0e-9);
		assertEquals(1.0, environment.windSourceAblStability(), 1.0e-9);
		assertEquals(1.0, environment.windSourceAblMixingStrength(), 1.0e-9);
		assertEquals(new Vec3(30.0, -30.0, 12.0), environment.windSourceGustVelocityWorldMetersPerSecond());
	}

	@Test
	void windSourceQualityFadesWithConfidenceTrustAndFreshness() {
		assertEquals(1.0, DroneEnvironment.windSourceQualityFactor(true, 1.0, -1L), 1.0e-9);
		assertEquals(0.50, DroneEnvironment.windSourceQualityFactor(true, 0.50, 0L), 1.0e-9);
		assertEquals(0.25, DroneEnvironment.windSourceQualityFactor(true, 0.50, 100L), 1.0e-9);
		assertEquals(0.0, DroneEnvironment.windSourceQualityFactor(true, 1.0, 160L), 1.0e-9);
		assertEquals(0.0, DroneEnvironment.windSourceQualityFactor(false, 1.0, 0L), 1.0e-9);
	}

	@Test
	void adoptedWindSourceHumidityUsesSourceQuality() {
		DroneEnvironment fresh = environmentWithWindSourceHumidity(true, 1.0, 0L, 0.80);
		DroneEnvironment halfStale = environmentWithWindSourceHumidity(true, 1.0, 100L, 0.80);
		DroneEnvironment stale = environmentWithWindSourceHumidity(true, 1.0, 160L, 0.80);
		DroneEnvironment untrusted = environmentWithWindSourceHumidity(false, 1.0, 0L, 0.80);
		DroneEnvironment rainyStale = environmentWithWindSourceHumidity(true, 1.0, 160L, 0.80, 0.35);

		assertEquals(0.80, fresh.windSourceHumidity(), 1.0e-9);
		assertEquals(0.80, fresh.adoptedWindSourceHumidity(), 1.0e-9);
		assertEquals(0.40, halfStale.adoptedWindSourceHumidity(), 1.0e-9);
		assertEquals(0.0, stale.adoptedWindSourceHumidity(), 1.0e-9);
		assertEquals(0.0, untrusted.adoptedWindSourceHumidity(), 1.0e-9);
		assertEquals(0.35, rainyStale.ambientHumidity(), 1.0e-9);
	}

	@Test
	void adoptedWindSourceTemperatureUsesSourceQuality() {
		DroneEnvironment fresh = environmentWithWindSourceTemperature(true, 1.0, 0L, 40.0);
		DroneEnvironment halfStale = environmentWithWindSourceTemperature(true, 1.0, 100L, 40.0);
		DroneEnvironment stale = environmentWithWindSourceTemperature(true, 1.0, 160L, 40.0);
		DroneEnvironment untrusted = environmentWithWindSourceTemperature(false, 1.0, 0L, 40.0);

		assertEquals(40.0, fresh.windSourceTemperatureCelsius(), 1.0e-9);
		assertEquals(40.0, fresh.adoptedWindSourceTemperatureCelsius(20.0), 1.0e-9);
		assertEquals(30.0, halfStale.adoptedWindSourceTemperatureCelsius(20.0), 1.0e-9);
		assertEquals(20.0, stale.adoptedWindSourceTemperatureCelsius(20.0), 1.0e-9);
		assertEquals(20.0, untrusted.adoptedWindSourceTemperatureCelsius(20.0), 1.0e-9);
	}

	@Test
	void adoptedWindSourcePressureUsesSourceQuality() {
		DroneEnvironment fresh = environmentWithWindSourcePressure(true, 1.0, 0L, -1200.0);
		DroneEnvironment halfStale = environmentWithWindSourcePressure(true, 1.0, 100L, -1200.0);
		DroneEnvironment stale = environmentWithWindSourcePressure(true, 1.0, 160L, -1200.0);
		DroneEnvironment untrusted = environmentWithWindSourcePressure(false, 1.0, 0L, -1200.0);
		DroneEnvironment internal = environmentWithWindSourcePressure(
				DroneEnvironment.WIND_SOURCE_INTERNAL,
				true,
				1.0,
				0L,
				-1200.0
		);

		assertEquals(-1200.0, fresh.windSourcePressureAnomalyPascals(), 1.0e-9);
		assertEquals(-1200.0, fresh.adoptedWindSourcePressureAnomalyPascals(), 1.0e-9);
		assertEquals(-600.0, halfStale.adoptedWindSourcePressureAnomalyPascals(), 1.0e-9);
		assertEquals(0.0, stale.adoptedWindSourcePressureAnomalyPascals(), 1.0e-9);
		assertEquals(0.0, untrusted.adoptedWindSourcePressureAnomalyPascals(), 1.0e-9);
		assertEquals(0.0, internal.adoptedWindSourcePressureAnomalyPascals(), 1.0e-9);
	}

	@Test
	void pressureAnomalyAdjustsDensityAndBarometerPressure() {
		double neutral = DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, 15.0);
		double lowPressure = DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, 15.0, -2200.0);
		double highPressure = DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, 15.0, 2200.0);

		assertTrue(lowPressure < neutral);
		assertTrue(highPressure > neutral);
		assertEquals(
				DroneEnvironment.barometricPressureHectopascals(0.0, neutral, 15.0) - 22.0,
				DroneEnvironment.barometricPressureHectopascals(0.0, neutral, 15.0, -2200.0),
				1.0e-9
		);
	}

	@Test
	void moistAirCoolingMultiplierTracksHumidityAndTemperature() {
		double dryHot = DroneEnvironment.moistAirCoolingMultiplier(45.0, 0.0);
		double mildHumid = DroneEnvironment.moistAirCoolingMultiplier(20.0, 1.0);
		double hotHumid = DroneEnvironment.moistAirCoolingMultiplier(45.0, 1.0);

		assertEquals(1.0, dryHot, 1.0e-12);
		assertTrue(mildHumid < dryHot);
		assertTrue(hotHumid < mildHumid);
		assertTrue(hotHumid >= 0.90);
	}

	@Test
	void weightedSurfaceEffectsUsePartialPatchCoverageGate() {
		DroneConfig config = DroneConfig.racingQuad();
		double rotorRadius = config.rotors().get(0).radiusMeters();
		double fullGround = DroneEnvironment.groundEffectThrustMultiplier(config, rotorRadius);
		double fullCeiling = DroneEnvironment.ceilingEffectThrustMultiplier(config, rotorRadius);

		double[] fullSurface = {rotorRadius, rotorRadius, rotorRadius, rotorRadius};
		double[] evenWeights = {1.0, 1.0, 1.0, 1.0};
		assertEquals(fullGround,
				DroneEnvironment.weightedGroundEffectThrustMultiplier(config, fullSurface, evenWeights),
				1.0e-12);
		assertEquals(fullCeiling,
				DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, fullSurface, evenWeights),
				1.0e-12);

		double[] quarterSupported = {
				rotorRadius,
				Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY
		};
		assertEquals(0.25,
				DroneEnvironment.weightedSurfaceEffectSupportCoverage(quarterSupported, evenWeights),
				1.0e-12);
		assertEquals(rotorRadius,
				DroneEnvironment.partialSurfaceCoveragePatchDiameterMeters(config, 0.25),
				1.0e-12);
		assertEquals(0.0,
				DroneEnvironment.partialSurfaceCoverageGate(config, 0.25),
				1.0e-12);
		assertEquals(1.0,
				DroneEnvironment.weightedGroundEffectThrustMultiplier(config, quarterSupported, evenWeights),
				1.0e-12);
		assertEquals(1.0,
				DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, quarterSupported, evenWeights),
				1.0e-12);

		double[] partialSupported = {rotorRadius, Double.POSITIVE_INFINITY};
		double[] partialWeights = {0.5625, 0.4375};
		assertEquals(0.5625,
				DroneEnvironment.weightedSurfaceEffectSupportCoverage(partialSupported, partialWeights),
				1.0e-12);
		assertEquals(rotorRadius * 1.5,
				DroneEnvironment.partialSurfaceCoveragePatchDiameterMeters(config, 0.5625),
				1.0e-12);
		assertEquals(0.5,
				DroneEnvironment.partialSurfaceCoverageGate(config, 0.5625),
				1.0e-12);
		assertEquals(1.0 + (fullGround - 1.0) * 0.5,
				DroneEnvironment.weightedGroundEffectThrustMultiplier(config, partialSupported, partialWeights),
				1.0e-12);
		assertEquals(1.0 + (fullCeiling - 1.0) * 0.5,
				DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, partialSupported, partialWeights),
				1.0e-12);
	}

	private static DroneEnvironment environmentWithWindSourceHumidity(
			boolean trusted,
			double confidence,
			long freshnessAgeTicks,
			double humidity
	) {
		return environmentWithWindSourceHumidity(trusted, confidence, freshnessAgeTicks, humidity, 0.0);
	}

	private static DroneEnvironment environmentWithWindSourceTemperature(
			boolean trusted,
			double confidence,
			long freshnessAgeTicks,
			double temperatureCelsius
	) {
		return windSourceScalarEnvironment(
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				trusted,
				confidence,
				freshnessAgeTicks,
				0.0,
				true,
				temperatureCelsius,
				false,
				0.0,
				0.0
		);
	}

	private static DroneEnvironment environmentWithWindSourcePressure(
			boolean trusted,
			double confidence,
			long freshnessAgeTicks,
			double pressureAnomalyPascals
	) {
		return environmentWithWindSourcePressure(
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				trusted,
				confidence,
				freshnessAgeTicks,
				pressureAnomalyPascals
		);
	}

	private static DroneEnvironment environmentWithWindSourcePressure(
			String sourceId,
			boolean trusted,
			double confidence,
			long freshnessAgeTicks,
			double pressureAnomalyPascals
	) {
		return windSourceScalarEnvironment(
				sourceId,
				trusted,
				confidence,
				freshnessAgeTicks,
				pressureAnomalyPascals,
				true,
				-8.0,
				false,
				0.0,
				0.0
		);
	}

	private static DroneEnvironment environmentWithWindSourceHumidity(
			boolean trusted,
			double confidence,
			long freshnessAgeTicks,
			double humidity,
			double precipitationWetness
	) {
		return windSourceScalarEnvironment(
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				trusted,
				confidence,
				freshnessAgeTicks,
				0.0,
				true,
				-8.0,
				true,
				humidity,
				precipitationWetness
		);
	}

	private static DroneEnvironment windSourceScalarEnvironment(
			String sourceId,
			boolean trusted,
			double confidence,
			long freshnessAgeTicks,
			double pressureAnomalyPascals,
			boolean hasTemperature,
			double temperatureCelsius,
			boolean hasHumidity,
			double humidity,
			double precipitationWetness
	) {
		return new DroneEnvironment(
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
				precipitationWetness,
				-8.0,
				null,
				null,
				null,
				sourceId,
				trusted,
				confidence,
				0.0,
				pressureAnomalyPascals,
				0.0,
				0.0,
				0.0,
				false,
				"l1",
				"server_authoritative",
				freshnessAgeTicks,
				0.0,
				0.0,
				0.0,
				hasTemperature,
				temperatureCelsius,
				hasHumidity,
				humidity,
				0.0,
				0.0
		);
	}
}
