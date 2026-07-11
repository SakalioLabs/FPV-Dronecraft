package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class DroneEnvironmentAtmosphereTest {
	private static final Class<?>[] LEGACY_ATMOSPHERE_CONSTRUCTOR_PREFIX = {
			Vec3.class,
			double.class,
			double.class,
			double.class,
			double.class,
			double.class,
			double.class,
			double[].class,
			double[].class,
			Vec3[].class,
			double[].class,
			double.class,
			double[].class,
			double.class,
			double.class,
			double[].class,
			double.class,
			double.class,
			double.class
	};

	@Test
	void legacyConstructorDerivesCompactAtmospherePrimitives() {
		DroneEnvironment environment = legacyEnvironment(0.85, 35.0, 1.0);

		assertRawEquals(35.0, environment.effectiveAmbientTemperatureCelsius());
		assertRawEquals(1.0, environment.ambientHumidity());
		assertRawEquals(0.0, environment.adoptedSourceHumidity());
		assertRawEquals(0.0, environment.adoptedSourcePressureAnomalyPascals());
		assertRawEquals(1.0, environment.motorEscVentilationFactor());
		assertRawEquals(1.0, environment.batteryVentilationFactor());
		assertEquals(Vec3.ZERO, environment.adoptedSourceGustVelocityWorldMetersPerSecond());
		assertSame(Vec3.ZERO, DroneEnvironment.calm().adoptedSourceGustVelocityWorldMetersPerSecond());
		assertRawEquals(0.0, environment.adoptedAblStability());
		assertRawEquals(0.0, environment.adoptedAblMixingStrength());
		assertRawEquals(0.0, DroneEnvironment.calm().adoptedAblStability());
		assertRawEquals(0.0, DroneEnvironment.calm().adoptedAblMixingStrength());
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
	void adoptedAblPrimitivesAreFiniteClampedAndNormalizeNeutralZero() {
		DroneEnvironment nonFinite = explicitAblEnvironment(Double.NaN, Double.POSITIVE_INFINITY);
		assertRawEquals(0.0, nonFinite.adoptedAblStability());
		assertRawEquals(0.0, nonFinite.adoptedAblMixingStrength());

		DroneEnvironment lower = explicitAblEnvironment(-2.0, -2.0);
		assertRawEquals(-1.0, lower.adoptedAblStability());
		assertRawEquals(0.0, lower.adoptedAblMixingStrength());

		DroneEnvironment upper = explicitAblEnvironment(2.0, 2.0);
		assertRawEquals(1.0, upper.adoptedAblStability());
		assertRawEquals(1.0, upper.adoptedAblMixingStrength());

		DroneEnvironment signedZero = explicitAblEnvironment(-0.0, -0.0);
		assertRawEquals(0.0, signedZero.adoptedAblStability());
		assertRawEquals(0.0, signedZero.adoptedAblMixingStrength());
	}

	@Test
	void adoptedSpatialGradientsAreFiniteClampedAndPreserveInRangeInstances() {
		Vec3 windDerivativeX = new Vec3(120.0, -240.0, 360.0);
		Vec3 windDerivativeZ = new Vec3(-1200.0, 1200.0, 0.5);
		Vec3 pressureGradient = new Vec3(200000.0, -200000.0, 750.0);
		DroneEnvironment inRange = explicitGradientEnvironment(
				windDerivativeX,
				windDerivativeZ,
				pressureGradient
		);

		assertSame(windDerivativeX, inRange.adoptedWindDerivativeAlongBodyXPerMeter());
		assertSame(windDerivativeZ, inRange.adoptedWindDerivativeAlongBodyZPerMeter());
		assertSame(pressureGradient, inRange.adoptedPressureGradientBodyPascalsPerMeter());

		DroneEnvironment clamped = explicitGradientEnvironment(
				new Vec3(1201.0, -1800.0, 120.0),
				new Vec3(-1201.0, 1750.0, -1900.0),
				new Vec3(220000.0, -250000.0, 90000.0)
		);
		assertEquals(
				new Vec3(1200.0, -1200.0, 120.0),
				clamped.adoptedWindDerivativeAlongBodyXPerMeter()
		);
		assertEquals(
				new Vec3(-1200.0, 1200.0, -1200.0),
				clamped.adoptedWindDerivativeAlongBodyZPerMeter()
		);
		assertEquals(
				new Vec3(200000.0, -200000.0, 90000.0),
				clamped.adoptedPressureGradientBodyPascalsPerMeter()
		);
	}

	@Test
	void adoptedSpatialGradientsRejectNonFiniteVectorsAndNormalizeNeutralZero() {
		DroneEnvironment nonFinite = explicitGradientEnvironment(
				null,
				new Vec3(1.0, Double.NaN, 3.0),
				new Vec3(Double.NEGATIVE_INFINITY, 2.0, 3.0)
		);
		assertSame(Vec3.ZERO, nonFinite.adoptedWindDerivativeAlongBodyXPerMeter());
		assertSame(Vec3.ZERO, nonFinite.adoptedWindDerivativeAlongBodyZPerMeter());
		assertSame(Vec3.ZERO, nonFinite.adoptedPressureGradientBodyPascalsPerMeter());

		DroneEnvironment signedZero = explicitGradientEnvironment(
				new Vec3(-0.0, 0.0, -0.0),
				new Vec3(0.0, -0.0, 0.0),
				new Vec3(-0.0, -0.0, 0.0)
		);
		assertSame(Vec3.ZERO, signedZero.adoptedWindDerivativeAlongBodyXPerMeter());
		assertSame(Vec3.ZERO, signedZero.adoptedWindDerivativeAlongBodyZPerMeter());
		assertSame(Vec3.ZERO, signedZero.adoptedPressureGradientBodyPascalsPerMeter());
	}

	@Test
	void adoptedLocalPressureCenterOffsetIsFiniteClampedAndNeutralized() {
		Vec3 inRange = new Vec3(0.012, -0.006, 0.018);
		assertSame(inRange, explicitLocalPressureCenterEnvironment(inRange)
				.adoptedLocalPressureCenterOffsetBodyMeters());
		assertEquals(
				new Vec3(0.024, -0.024, 0.024),
				explicitLocalPressureCenterEnvironment(new Vec3(0.08, -0.04, 0.12))
						.adoptedLocalPressureCenterOffsetBodyMeters()
		);
		assertSame(Vec3.ZERO, explicitLocalPressureCenterEnvironment(null)
				.adoptedLocalPressureCenterOffsetBodyMeters());
		assertSame(Vec3.ZERO, explicitLocalPressureCenterEnvironment(new Vec3(1.0, Double.NaN, 2.0))
				.adoptedLocalPressureCenterOffsetBodyMeters());
		assertSame(Vec3.ZERO, explicitLocalPressureCenterEnvironment(new Vec3(-0.0, 0.0, -0.0))
				.adoptedLocalPressureCenterOffsetBodyMeters());
	}

	@Test
	void adoptedLocalStaticPressureExposureIsFiniteClampedAndLegacyNeutral() {
		assertRawEquals(0.0, explicitLocalPressureCenterEnvironment(Vec3.ZERO)
				.adoptedLocalStaticPressureExposure());
		assertRawEquals(0.0, explicitLocalStaticPressureEnvironment(Double.NaN)
				.adoptedLocalStaticPressureExposure());
		assertRawEquals(0.0, explicitLocalStaticPressureEnvironment(-0.5)
				.adoptedLocalStaticPressureExposure());
		assertRawEquals(0.65, explicitLocalStaticPressureEnvironment(0.65)
				.adoptedLocalStaticPressureExposure());
		assertRawEquals(1.0, explicitLocalStaticPressureEnvironment(2.0)
				.adoptedLocalStaticPressureExposure());
	}

	@Test
	void adoptedTerrainShearPrimitivesAreFiniteClampedAndLegacyNeutral() {
		DroneEnvironment invalid = explicitTerrainShearEnvironment(Double.NaN, Double.POSITIVE_INFINITY);
		assertRawEquals(0.0, invalid.adoptedSourceWindShearMagnitudePerBlock());
		assertRawEquals(0.0, invalid.adoptedSourceShelterFactor());
		DroneEnvironment clamped = explicitTerrainShearEnvironment(8.0, 2.0);
		assertRawEquals(5.0, clamped.adoptedSourceWindShearMagnitudePerBlock());
		assertRawEquals(1.0, clamped.adoptedSourceShelterFactor());
		DroneEnvironment inRange = explicitTerrainShearEnvironment(1.25, 0.45);
		assertRawEquals(1.25, inRange.adoptedSourceWindShearMagnitudePerBlock());
		assertRawEquals(0.45, inRange.adoptedSourceShelterFactor());
	}

	@Test
	void adoptedUpdraftPrimitivesAreFiniteClampedAndLegacyNeutral() {
		DroneEnvironment invalid = explicitUpdraftEnvironment(Double.NaN, Double.POSITIVE_INFINITY);
		assertRawEquals(0.0, invalid.adoptedSourceUpdraftMetersPerSecond());
		assertRawEquals(0.0, invalid.adoptedSourceUpdraftLocalVoxelGain());
		DroneEnvironment clamped = explicitUpdraftEnvironment(20.0, 2.0);
		assertRawEquals(12.0, clamped.adoptedSourceUpdraftMetersPerSecond());
		assertRawEquals(1.0, clamped.adoptedSourceUpdraftLocalVoxelGain());
		DroneEnvironment down = explicitUpdraftEnvironment(-4.5, 0.72);
		assertRawEquals(-4.5, down.adoptedSourceUpdraftMetersPerSecond());
		assertRawEquals(0.72, down.adoptedSourceUpdraftLocalVoxelGain());
	}

	@Test
	void legacyAtmosphereConstructorDescriptorsDefaultSpatialGradientsToNeutral() throws NoSuchMethodException {
		assertLegacyAtmosphereConstructorDescriptor();
		assertLegacyAtmosphereConstructorDescriptor(double.class, double.class, double.class);
		assertLegacyAtmosphereConstructorDescriptor(double.class, double.class, double.class, Vec3.class);
		assertLegacyAtmosphereConstructorDescriptor(
				double.class,
				double.class,
				double.class,
				Vec3.class,
				double.class,
				double.class
		);
		assertLegacyAtmosphereConstructorDescriptor(
				double.class,
				double.class,
				double.class,
				Vec3.class,
				double.class,
				double.class,
				Vec3.class,
				Vec3.class,
				Vec3.class
		);
		assertLegacyAtmosphereConstructorDescriptor(
				double.class, double.class, double.class, Vec3.class, double.class, double.class,
				Vec3.class, Vec3.class, Vec3.class, Vec3.class
		);
		assertLegacyAtmosphereConstructorDescriptor(
				double.class, double.class, double.class, Vec3.class, double.class, double.class,
				Vec3.class, Vec3.class, Vec3.class, Vec3.class, double.class
		);
		assertLegacyAtmosphereConstructorDescriptor(
				double.class, double.class, double.class, Vec3.class, double.class, double.class,
				Vec3.class, Vec3.class, Vec3.class, Vec3.class, double.class, double.class,
				double.class
		);

		DroneEnvironment[] legacyEnvironments = {
				explicitEnvironment(20.0, 30.0, 0.20, 0.10, 0.75),
				explicitFlowEnvironment(1200.0, 0.83, 0.88),
				explicitSourceGustEnvironment(new Vec3(1.0, 2.0, 3.0)),
				explicitAblEnvironment(-0.4, 0.7)
		};
		for (DroneEnvironment environment : legacyEnvironments) {
			assertSame(Vec3.ZERO, environment.adoptedWindDerivativeAlongBodyXPerMeter());
			assertSame(Vec3.ZERO, environment.adoptedWindDerivativeAlongBodyZPerMeter());
			assertSame(Vec3.ZERO, environment.adoptedPressureGradientBodyPascalsPerMeter());
			assertSame(Vec3.ZERO, environment.adoptedLocalPressureCenterOffsetBodyMeters());
			assertRawEquals(0.0, environment.adoptedLocalStaticPressureExposure());
			assertRawEquals(0.0, environment.adoptedSourceWindShearMagnitudePerBlock());
			assertRawEquals(0.0, environment.adoptedSourceShelterFactor());
			assertRawEquals(0.0, environment.adoptedSourceUpdraftMetersPerSecond());
			assertRawEquals(0.0, environment.adoptedSourceUpdraftLocalVoxelGain());
		}
		assertSame(Vec3.ZERO, DroneEnvironment.calm().adoptedWindDerivativeAlongBodyXPerMeter());
		assertSame(Vec3.ZERO, DroneEnvironment.calm().adoptedWindDerivativeAlongBodyZPerMeter());
		assertSame(Vec3.ZERO, DroneEnvironment.calm().adoptedPressureGradientBodyPascalsPerMeter());
		assertSame(Vec3.ZERO, DroneEnvironment.calm().adoptedLocalPressureCenterOffsetBodyMeters());
		assertRawEquals(0.0, DroneEnvironment.calm().adoptedLocalStaticPressureExposure());
		assertRawEquals(0.0, DroneEnvironment.calm().adoptedSourceWindShearMagnitudePerBlock());
		assertRawEquals(0.0, DroneEnvironment.calm().adoptedSourceShelterFactor());
		assertRawEquals(0.0, DroneEnvironment.calm().adoptedSourceUpdraftMetersPerSecond());
		assertRawEquals(0.0, DroneEnvironment.calm().adoptedSourceUpdraftLocalVoxelGain());
	}

	@Test
	void adoptedSourceGustIsFiniteClampedAndLegacyNeutral() {
		Vec3 inRange = new Vec3(1.0, -2.0, 3.0);
		assertSame(
				inRange,
				explicitSourceGustEnvironment(inRange).adoptedSourceGustVelocityWorldMetersPerSecond()
		);
		assertEquals(
				Vec3.ZERO,
				explicitSourceGustEnvironment(new Vec3(Double.NaN, 2.0, 3.0))
						.adoptedSourceGustVelocityWorldMetersPerSecond()
		);
		assertEquals(
				new Vec3(30.0, -30.0, 3.0),
				explicitSourceGustEnvironment(new Vec3(45.0, -50.0, 3.0))
						.adoptedSourceGustVelocityWorldMetersPerSecond()
		);
		assertSame(
				Vec3.ZERO,
				explicitFlowEnvironment(0.0, 1.0, 1.0).adoptedSourceGustVelocityWorldMetersPerSecond()
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
	void adoptedPressureAndBodyVentilationAreSanitizedAndAppliedOnce() {
		DroneEnvironment positivePressure = explicitFlowEnvironment(4500.0, 0.83, 0.881);
		DroneEnvironment negativePressure = explicitFlowEnvironment(-4500.0, 0.83, 0.881);
		double positiveMultiplier = (101325.0 + 4500.0) / 101325.0;
		double negativeMultiplier = (101325.0 - 4500.0) / 101325.0;

		assertRawEquals(positiveMultiplier, positivePressure.effectiveAirDensityRatio());
		assertRawEquals(negativeMultiplier, negativePressure.effectiveAirDensityRatio());
		assertRawEquals(0.83, positivePressure.motorEscVentilationFactor());
		assertRawEquals(0.881, positivePressure.batteryVentilationFactor());
		assertRawEquals(
				positiveMultiplier,
				DroneEnvironment.pressureAirDensityMultiplier(4500.0)
		);

		DroneEnvironment sanitized = explicitFlowEnvironment(
				Double.POSITIVE_INFINITY,
				Double.NaN,
				-5.0
		);
		assertRawEquals(0.0, sanitized.adoptedSourcePressureAnomalyPascals());
		assertRawEquals(1.0, sanitized.motorEscVentilationFactor());
		assertRawEquals(0.78, sanitized.batteryVentilationFactor());

		DroneEnvironment clamped = explicitFlowEnvironment(9000.0, 0.0, 2.0);
		assertRawEquals(5000.0, clamped.adoptedSourcePressureAnomalyPascals());
		assertRawEquals(0.72, clamped.motorEscVentilationFactor());
		assertRawEquals(1.0, clamped.batteryVentilationFactor());
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

	private static DroneEnvironment explicitFlowEnvironment(
			double pressureAnomalyPascals,
			double motorEscVentilationFactor,
			double batteryVentilationFactor
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
				0.0,
				25.0,
				null,
				25.0,
				0.0,
				0.0,
				pressureAnomalyPascals,
				motorEscVentilationFactor,
				batteryVentilationFactor
		);
	}

	private static DroneEnvironment explicitSourceGustEnvironment(Vec3 sourceGust) {
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
				0.0,
				25.0,
				null,
				25.0,
				0.0,
				0.0,
				0.0,
				1.0,
				1.0,
				sourceGust
		);
	}

	private static DroneEnvironment explicitAblEnvironment(double stability, double mixingStrength) {
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
				0.0,
				25.0,
				null,
				25.0,
				0.0,
				0.0,
				0.0,
				1.0,
				1.0,
				Vec3.ZERO,
				stability,
				mixingStrength
		);
	}

	private static DroneEnvironment explicitGradientEnvironment(
			Vec3 windDerivativeAlongBodyXPerMeter,
			Vec3 windDerivativeAlongBodyZPerMeter,
			Vec3 pressureGradientBodyPascalsPerMeter
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
				0.0,
				25.0,
				null,
				25.0,
				0.0,
				0.0,
				0.0,
				1.0,
				1.0,
				Vec3.ZERO,
				0.0,
				0.0,
				windDerivativeAlongBodyXPerMeter,
				windDerivativeAlongBodyZPerMeter,
				pressureGradientBodyPascalsPerMeter
		);
	}

	private static DroneEnvironment explicitLocalPressureCenterEnvironment(Vec3 localPressureCenterOffset) {
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
				0.0,
				25.0,
				null,
				25.0,
				0.0,
				0.0,
				0.0,
				1.0,
				1.0,
				Vec3.ZERO,
				0.0,
				0.0,
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO,
				localPressureCenterOffset
		);
	}

	private static DroneEnvironment explicitLocalStaticPressureEnvironment(double exposure) {
		DroneEnvironment base = explicitLocalPressureCenterEnvironment(Vec3.ZERO);
		return new DroneEnvironment(
				base.windVelocityWorldMetersPerSecond(), base.airDensityRatio(), base.groundClearanceMeters(),
				base.turbulenceIntensity(), base.obstacleProximity(), base.droneWakeIntensity(),
				base.ceilingClearanceMeters(), null, null, null, null, 0.0, null, 0.0, 25.0, null,
				25.0, 0.0, 0.0, 0.0, 1.0, 1.0, Vec3.ZERO, 0.0, 0.0,
				Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, exposure
		);
	}

	private static DroneEnvironment explicitTerrainShearEnvironment(double shear, double shelter) {
		return new DroneEnvironment(
				Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0,
				Double.POSITIVE_INFINITY, null, null, null, null, 0.0, null, 0.0, 25.0, null,
				25.0, 0.0, 0.0, 0.0, 1.0, 1.0, Vec3.ZERO, 0.0, 0.0,
				Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.0, shear, shelter
		);
	}

	private static DroneEnvironment explicitUpdraftEnvironment(double updraft, double localVoxelGain) {
		return new DroneEnvironment(
				Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0,
				Double.POSITIVE_INFINITY, null, null, null, null, 0.0, null, 0.0, 25.0, null,
				25.0, 0.0, 0.0, 0.0, 1.0, 1.0, Vec3.ZERO, 0.0, 0.0,
				Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.0, 0.0, 0.0,
				updraft, localVoxelGain
		);
	}

	private static void assertLegacyAtmosphereConstructorDescriptor(Class<?>... trailingTypes)
			throws NoSuchMethodException {
		Class<?>[] descriptor = new Class<?>[
				LEGACY_ATMOSPHERE_CONSTRUCTOR_PREFIX.length + trailingTypes.length
		];
		System.arraycopy(
				LEGACY_ATMOSPHERE_CONSTRUCTOR_PREFIX,
				0,
				descriptor,
				0,
				LEGACY_ATMOSPHERE_CONSTRUCTOR_PREFIX.length
		);
		System.arraycopy(
				trailingTypes,
				0,
				descriptor,
				LEGACY_ATMOSPHERE_CONSTRUCTOR_PREFIX.length,
				trailingTypes.length
		);
		assertEquals(
				descriptor.length,
				DroneEnvironment.class.getConstructor(descriptor).getParameterCount()
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
