package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DroneEnvironmentAtmosphereTest {
	@Test
	void legacyConstructorDerivesCompactAtmospherePrimitives() {
		DroneEnvironment environment = legacyEnvironment(0.85, 35.0, 1.0);

		assertRawEquals(35.0, environment.effectiveAmbientTemperatureCelsius());
		assertRawEquals(1.0, environment.ambientHumidity());
		assertRawEquals(0.0, environment.adoptedSourceHumidity());
		assertRawEquals(0.85 * 0.9789925675447962, environment.effectiveAirDensityRatio());

		DroneEnvironment dry = legacyEnvironment(1.0, 35.0, 0.0);
		double legacyDrySound = Math.sqrt(1.4 * 287.05 * (35.0 + 273.15));
		assertRawEquals(1.0, dry.effectiveAirDensityRatio());
		assertRawEquals(legacyDrySound, DroneEnvironment.speedOfSoundMetersPerSecond(35.0));
		assertRawEquals(
				DroneEnvironment.speedOfSoundMetersPerSecond(35.0),
				DroneEnvironment.speedOfSoundMetersPerSecond(35.0, 0.0)
		);
	}

	@Test
	void explicitAtmospherePrimitivesAreSanitizedAndPreserveHumiditySources() {
		DroneEnvironment environment = explicitEnvironment(
				Double.NaN,
				Double.POSITIVE_INFINITY,
				0.30,
				Double.NaN,
				1.50
		);

		assertRawEquals(25.0, environment.ambientTemperatureCelsius());
		assertRawEquals(25.0, environment.effectiveAmbientTemperatureCelsius());
		assertRawEquals(1.0, environment.ambientHumidity());
		assertRawEquals(1.0, environment.adoptedSourceHumidity());

		DroneEnvironment clamped = explicitEnvironment(-50.0, 100.0, -1.0, -2.0, -3.0);
		assertRawEquals(-40.0, clamped.ambientTemperatureCelsius());
		assertRawEquals(65.0, clamped.effectiveAmbientTemperatureCelsius());
		assertRawEquals(0.0, clamped.ambientHumidity());
		assertRawEquals(0.0, clamped.adoptedSourceHumidity());

		DroneEnvironment sourceDominates = explicitEnvironment(20.0, 30.0, 0.20, 0.10, 0.75);
		assertRawEquals(0.75, sourceDominates.ambientHumidity());
		assertRawEquals(0.75, sourceDominates.adoptedSourceHumidity());
	}

	@Test
	void sourceFreshnessMatchesSmoothQualityGateAtEveryBoundary() {
		long[] ages = {-1L, 40L, 41L, 100L, 159L, 160L};
		for (long age : ages) {
			assertRawEquals(freshnessOracle(age), DroneEnvironment.windSourceFreshnessFactor(age));
		}

		assertRawEquals(0.25, DroneEnvironment.windSourceQualityFactor(true, 0.50, 100L));
		assertRawEquals(1.0, DroneEnvironment.windSourceQualityFactor(true, 4.0, -1L));
		assertRawEquals(0.0, DroneEnvironment.windSourceQualityFactor(false, 1.0, -1L));
		assertRawEquals(0.0, DroneEnvironment.windSourceQualityFactor(true, Double.NaN, -1L));
	}

	@Test
	void sourceAdoptionIsQualityGatedAndKeepsRainIndependent() {
		assertRawEquals(
				30.0,
				DroneEnvironment.adoptedSourceTemperatureCelsius(20.0, true, 40.0, 0.50)
		);
		assertRawEquals(
				20.0,
				DroneEnvironment.adoptedSourceTemperatureCelsius(20.0, false, 40.0, 1.0)
		);
		assertRawEquals(
				20.0,
				DroneEnvironment.adoptedSourceTemperatureCelsius(20.0, true, Double.NaN, 1.0)
		);
		assertRawEquals(
				20.0,
				DroneEnvironment.adoptedSourceTemperatureCelsius(20.0, true, 40.0, 1.0e-9)
		);
		assertRawEquals(0.40, DroneEnvironment.adoptedSourceHumidity(true, 0.80, 0.50));
		assertRawEquals(0.0, DroneEnvironment.adoptedSourceHumidity(false, 0.80, 1.0));
		assertRawEquals(0.70, DroneEnvironment.ambientHumidity(0.70, 0.40));
		assertRawEquals(0.80, DroneEnvironment.ambientHumidity(0.20, 0.80));
	}

	@Test
	void effectiveDensityUsesAdoptedTemperatureAndFinalAmbientHumidityOnce() {
		DroneEnvironment environment = explicitEnvironment(25.0, 35.0, 0.0, 1.0, 1.0);
		double temperatureMultiplier = (25.0 + 273.15) / (35.0 + 273.15);
		double expected = temperatureMultiplier * 0.9789925675447962;

		assertRawEquals(expected, environment.effectiveAirDensityRatio());
	}

	@Test
	void moistAirPureFunctionsMatchSimLabAnchorsAndReuseOneVaporFraction() {
		assertMoistAirAnchor(
				35.0,
				0.9789925675447962,
				0.9855504432847805,
				0.9449911518390431,
				355.0691049058361
		);
		assertMoistAirAnchor(
				45.0,
				0.9641127350793487,
				0.9753156378852663,
				0.9272770655706533,
				363.1376574507501
		);
		assertMoistAirAnchor(
				55.0,
				0.9408540051461148,
				0.9593175696772218,
				0.90,
				372.6689298817491
		);

		assertRawEquals(1.0, DroneEnvironment.moistAirDensityMultiplier(55.0, 0.0));
		assertRawEquals(1.0, DroneEnvironment.moistAirDynamicViscosityMultiplier(55.0, 0.0));
		assertRawEquals(1.0, DroneEnvironment.moistAirCoolingMultiplier(55.0, 0.0));

		double partialTemperatureCelsius = 35.0;
		double partialHumidity = 0.37;
		double saturationVaporPressureHectopascals = 6.112 * Math.exp(
				17.67 * partialTemperatureCelsius / (partialTemperatureCelsius + 243.5)
		);
		double saturationVaporPressureFraction = saturationVaporPressureHectopascals / 1013.25;
		double expectedPartialCooling = MathUtil.clamp(
				1.0 - partialHumidity * (0.030 + 0.45 * saturationVaporPressureFraction),
				0.90,
				1.0
		);
		assertRawEquals(
				expectedPartialCooling,
				DroneEnvironment.moistAirCoolingMultiplier(
						partialTemperatureCelsius,
						partialHumidity
				)
		);
	}

	private static void assertMoistAirAnchor(
			double temperatureCelsius,
			double expectedDensity,
			double expectedViscosity,
		double expectedCooling,
		double expectedSpeedOfSound
	) {
		double vaporMoleFraction = DroneEnvironment.moistAirVaporMoleFraction(temperatureCelsius, 1.0);
		assertAnchorEquals(
				expectedDensity,
				DroneEnvironment.moistAirDensityMultiplierFromVaporMoleFraction(vaporMoleFraction)
		);
		assertAnchorEquals(
				expectedViscosity,
				DroneEnvironment.moistAirDynamicViscosityMultiplierFromVaporMoleFraction(vaporMoleFraction)
		);
		assertAnchorEquals(
				expectedCooling,
				DroneEnvironment.moistAirCoolingMultiplier(temperatureCelsius, 1.0)
		);
		assertAnchorEquals(
				expectedSpeedOfSound,
				DroneEnvironment.speedOfSoundMetersPerSecondFromVaporMoleFraction(
						temperatureCelsius,
						vaporMoleFraction
				)
		);
		assertRawEquals(
				DroneEnvironment.moistAirDensityMultiplierFromVaporMoleFraction(vaporMoleFraction),
				DroneEnvironment.moistAirDensityMultiplier(temperatureCelsius, 1.0)
		);
		assertRawEquals(
				DroneEnvironment.moistAirDynamicViscosityMultiplierFromVaporMoleFraction(vaporMoleFraction),
				DroneEnvironment.moistAirDynamicViscosityMultiplier(temperatureCelsius, 1.0)
		);
		assertRawEquals(
				DroneEnvironment.speedOfSoundMetersPerSecondFromVaporMoleFraction(
						temperatureCelsius,
						vaporMoleFraction
				),
				DroneEnvironment.speedOfSoundMetersPerSecond(temperatureCelsius, 1.0)
		);
	}

	private static DroneEnvironment legacyEnvironment(
			double airDensityRatio,
			double ambientTemperatureCelsius,
			double precipitationWetnessIntensity
	) {
		return new DroneEnvironment(
				Vec3.ZERO,
				airDensityRatio,
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
				precipitationWetnessIntensity,
				ambientTemperatureCelsius,
				null
		);
	}

	private static DroneEnvironment explicitEnvironment(
			double ambientTemperatureCelsius,
			double effectiveAmbientTemperatureCelsius,
			double precipitationWetnessIntensity,
			double ambientHumidity,
			double adoptedSourceHumidity
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
				precipitationWetnessIntensity,
				ambientTemperatureCelsius,
				null,
				effectiveAmbientTemperatureCelsius,
				ambientHumidity,
				adoptedSourceHumidity
		);
	}

	private static double freshnessOracle(long ageTicks) {
		if (ageTicks < 0L || ageTicks <= 40L) {
			return 1.0;
		}
		if (ageTicks >= 160L) {
			return 0.0;
		}
		double t = (ageTicks - 40L) / 120.0;
		return 1.0 - t * t * (3.0 - 2.0 * t);
	}

	private static void assertRawEquals(double expected, double actual) {
		assertEquals(
				Double.doubleToRawLongBits(expected),
				Double.doubleToRawLongBits(actual),
				() -> "expected " + expected + " (0x"
						+ Long.toHexString(Double.doubleToRawLongBits(expected)) + ") but got "
						+ actual + " (0x" + Long.toHexString(Double.doubleToRawLongBits(actual)) + ")"
		);
	}

	private static void assertAnchorEquals(double expected, double actual) {
		assertEquals(expected, actual, 1.0e-12);
	}
}
