package com.tenicana.dronecraft.sim;

public final class PrecipitationWaterCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double INCHES_TO_MILLIMETERS = 25.4;
	private static final double WATER_DENSITY_KG_PER_CUBIC_METER = 997.0;
	private static final double FULL_WETNESS_RAIN_RATE_INCHES_PER_HOUR = 1.5;
	private static final double GENERATED_REFERENCE_RAIN_LOSS_COEFFICIENT = 0.055;
	private static final double CURRENT_RAIN_LOSS_COEFFICIENT = 0.030;
	private static final double CURRENT_RAIN_LOSS_EXPONENT = 0.85;
	private static final double CURRENT_RAIN_MIN_THRUST_SCALE = 0.96;
	private static final double RAIN_IMPACT_SPEED_METERS_PER_SECOND = 8.0;
	private static final double STRESS_RAIN_RATE_MILLIMETERS_PER_HOUR = 100.0;

	public static final String SOURCE_ID = "Precipitation-Water-Calibration-Packet";
	public static final String CAVEAT = "Rain wetness is a wet-prop/load proxy; water immersion is a separate severe drag path.";
	public static final int PACKET_METRIC_ROW_COUNT = 1608;
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int RAIN_SCAN_SCENARIO_COUNT = 42;
	public static final int WATER_IMMERSION_SCAN_SCENARIO_COUNT = 108;
	public static final int MOIST_AIR_DENSITY_SCENARIO_COUNT = 20;
	public static final int CURRENT_VS_ICAS_SCENARIO_COUNT = 12;
	public static final int ICAS_HEAVY_RAIN_REFERENCE_COUNT = 2;

	private static final IcasHeavyRainCtReference ICAS_HEAVY_RAIN_4319_RPM = new IcasHeavyRainCtReference(
			"ICAS_2020_heavy_rain_4319rpm",
			"https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf",
			4319.0,
			19.0,
			0.0028,
			8.06,
			0.15314,
			552.962888665998,
			0.008217,
			0.008,
			2.6408664962881856
	);
	private static final IcasHeavyRainCtReference ICAS_HEAVY_RAIN_6528_RPM = new IcasHeavyRainCtReference(
			"ICAS_2020_heavy_rain_6528rpm",
			"https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf",
			6528.0,
			19.0,
			0.0028,
			8.06,
			0.15314,
			552.962888665998,
			0.008308,
			0.008169,
			1.6730861819932663
	);

	private PrecipitationWaterCalibration() {
	}

	public record IcasHeavyRainCtReference(
			String referenceId,
			String sourceUrl,
			double rpm,
			double liquidWaterContentGramsPerCubicMeter,
			double dropletMeanDiameterMeters,
			double dropletTerminalVelocityMetersPerSecond,
			double waterMassFluxKilogramsPerSquareMeterSecond,
			double equivalentRainRateMillimetersPerHour,
			double ctNoRain,
			double ctWithRain,
			double ctLossPercent
	) {
	}

	public record RainFormulaAudit(
			String javaFormula,
			double javaLossCoefficient,
			double javaLossExponent,
			double javaMinimumThrustScale,
			double javaFullWetnessThrustLossPercent,
			double generatedReferenceFullWetnessThrustLossPercent,
			double javaLossOverGeneratedReferenceLoss
	) {
	}

	public record RainScenarioAudit(
			String scenario,
			double rainRateMillimetersPerHour,
			double rainRateInchesPerHour,
			double waterMassFluxKilogramsPerSquareMeterSecond,
			double rotorDiskAreaSquareMeters,
			double allRotorsWaterGramsPerSecond,
			double precipitationWetnessProxy,
			double generatedReferenceThrustLossPercent,
			double javaSourceThrustLossPercent,
			double rotorPrecipitationLoadFactor,
			double rotorPrecipitationVibrationAtHover,
			double rainImpactForceAllRotorsOverWeightAt8MetersPerSecond
	) {
	}

	public record IcasComparison(
			IcasHeavyRainCtReference reference,
			double javaFullWetnessThrustLossPercent,
			double generatedReferenceFullWetnessThrustLossPercent,
			double javaLossOverIcasLoss,
			double generatedReferenceLossOverIcasLoss,
			double allRotorsWaterGramsPerSecondAtIcasLwc,
			double rainImpactForceAllRotorsOverWeightAt8MetersPerSecond
	) {
	}

	public record WaterImmersionAudit(
			double waterImmersion,
			double speedMetersPerSecond,
			double waterImmersionThrustLossPercent,
			double rotorWaterLoadFactor,
			double rotorWaterIngestionVibrationAtHover,
			double waterDragForceNewtons,
			double waterDragOverWeight,
			double waterDragCoefficientProxy
	) {
	}

	public record MoistAirDensityAudit(
			double ambientTemperatureCelsius,
			double precipitationWetness,
			double moistAirDensityMultiplier
	) {
	}

	public record PrecipitationWaterAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int rainScanScenarioCount,
			int waterImmersionScanScenarioCount,
			int moistAirDensityScenarioCount,
			int currentVsIcasScenarioCount,
			int icasHeavyRainReferenceCount,
			RainFormulaAudit formula,
			IcasHeavyRainCtReference icas4319Rpm,
			IcasHeavyRainCtReference icas6528Rpm,
			IcasComparison icas4319Comparison,
			IcasComparison icas6528Comparison,
			RainScenarioAudit nwsLightRain005InHour,
			RainScenarioAudit nwsModerateRain025InHour,
			RainScenarioAudit nwsFullWetness150InHour,
			RainScenarioAudit stress100MillimetersPerHour,
			WaterImmersionAudit halfImmersionAt5MetersPerSecond,
			MoistAirDensityAudit hotFullWetMoistAir
	) {
	}

	public static PrecipitationWaterAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		RainFormulaAudit formula = formulaAudit();
		double generatedFullWetnessLoss = formula.generatedReferenceFullWetnessThrustLossPercent();
		double javaFullWetnessLoss = formula.javaFullWetnessThrustLossPercent();
		return new PrecipitationWaterAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				RAIN_SCAN_SCENARIO_COUNT,
				WATER_IMMERSION_SCAN_SCENARIO_COUNT,
				MOIST_AIR_DENSITY_SCENARIO_COUNT,
				CURRENT_VS_ICAS_SCENARIO_COUNT,
				ICAS_HEAVY_RAIN_REFERENCE_COUNT,
				formula,
				ICAS_HEAVY_RAIN_4319_RPM,
				ICAS_HEAVY_RAIN_6528_RPM,
				compareIcas(config, ICAS_HEAVY_RAIN_4319_RPM, javaFullWetnessLoss, generatedFullWetnessLoss),
				compareIcas(config, ICAS_HEAVY_RAIN_6528_RPM, javaFullWetnessLoss, generatedFullWetnessLoss),
				rainScenario(config, "nws_0.05_in_h", 0.05 * INCHES_TO_MILLIMETERS),
				rainScenario(config, "nws_0.25_in_h", 0.25 * INCHES_TO_MILLIMETERS),
				rainScenario(config, "nws_1.50_in_h_full_wetness", 1.50 * INCHES_TO_MILLIMETERS),
				rainScenario(config, "stress_100_mm_h", STRESS_RAIN_RATE_MILLIMETERS_PER_HOUR),
				waterImmersionScenario(config, 0.5, 5.0),
				new MoistAirDensityAudit(35.0, 1.0, DroneEnvironment.moistAirDensityMultiplier(35.0, 1.0))
		);
	}

	private static RainFormulaAudit formulaAudit() {
		double javaFullWetnessLossPercent = (1.0 - DronePhysics.precipitationThrustScale(1.0)) * 100.0;
		double generatedFullWetnessLossPercent = GENERATED_REFERENCE_RAIN_LOSS_COEFFICIENT * 100.0;
		return new RainFormulaAudit(
				"clamp(1 - 0.03 * wetness^0.85, 0.96, 1)",
				CURRENT_RAIN_LOSS_COEFFICIENT,
				CURRENT_RAIN_LOSS_EXPONENT,
				CURRENT_RAIN_MIN_THRUST_SCALE,
				javaFullWetnessLossPercent,
				generatedFullWetnessLossPercent,
				ratio(javaFullWetnessLossPercent, generatedFullWetnessLossPercent)
		);
	}

	private static RainScenarioAudit rainScenario(DroneConfig config, String scenario, double rainRateMillimetersPerHour) {
		double rainRateInchesPerHour = rainRateMillimetersPerHour / INCHES_TO_MILLIMETERS;
		double waterMassFlux = waterMassFluxKilogramsPerSquareMeterSecond(rainRateMillimetersPerHour);
		double diskArea = rotorDiskAreaSquareMeters(config);
		double allRotorsWater = allRotorsWaterGramsPerSecond(config, waterMassFlux);
		double wetness = precipitationWetnessProxy(rainRateInchesPerHour);
		RotorSpec representativeRotor = config.rotors().get(0);
		double hoverOmega = averageHoverOmegaRadiansPerSecond(config);
		return new RainScenarioAudit(
				scenario,
				rainRateMillimetersPerHour,
				rainRateInchesPerHour,
				waterMassFlux,
				diskArea,
				allRotorsWater,
				wetness,
				generatedReferenceRainThrustLossPercent(wetness),
				(1.0 - DronePhysics.precipitationThrustScale(wetness)) * 100.0,
				DronePhysics.rotorPrecipitationLoadFactor(wetness),
				DronePhysics.rotorPrecipitationVibration(representativeRotor, hoverOmega, wetness),
				rainImpactForceOverWeight(config, waterMassFlux, RAIN_IMPACT_SPEED_METERS_PER_SECOND)
		);
	}

	private static IcasComparison compareIcas(
			DroneConfig config,
			IcasHeavyRainCtReference reference,
			double javaFullWetnessLoss,
			double generatedFullWetnessLoss
	) {
		double allRotorsWater = allRotorsWaterGramsPerSecond(
				config,
				reference.waterMassFluxKilogramsPerSquareMeterSecond()
		);
		double impactOverWeight = rainImpactForceOverWeight(
				config,
				reference.waterMassFluxKilogramsPerSquareMeterSecond(),
				RAIN_IMPACT_SPEED_METERS_PER_SECOND
		);
		return new IcasComparison(
				reference,
				javaFullWetnessLoss,
				generatedFullWetnessLoss,
				ratio(javaFullWetnessLoss, reference.ctLossPercent()),
				ratio(generatedFullWetnessLoss, reference.ctLossPercent()),
				allRotorsWater,
				impactOverWeight
		);
	}

	private static WaterImmersionAudit waterImmersionScenario(
			DroneConfig config,
			double waterImmersion,
			double speedMetersPerSecond
	) {
		RotorSpec representativeRotor = config.rotors().get(0);
		double hoverOmega = averageHoverOmegaRadiansPerSecond(config);
		double dragCoefficient = waterDragCoefficientProxy(config);
		double water = MathUtil.clamp(waterImmersion, 0.0, 1.0);
		double speed = Math.max(0.0, speedMetersPerSecond);
		double dragForce = MathUtil.clamp(
				dragCoefficient * Math.pow(water, 1.15) * speed * speed,
				0.0,
				120.0
		);
		return new WaterImmersionAudit(
				water,
				speed,
				(1.0 - DronePhysics.waterImmersionThrustScale(water)) * 100.0,
				DronePhysics.rotorWaterLoadFactor(water),
				DronePhysics.rotorWaterIngestionVibration(representativeRotor, hoverOmega, water),
				dragForce,
				ratio(dragForce, config.massKg() * config.gravityMetersPerSecondSquared()),
				dragCoefficient
		);
	}

	private static double waterMassFluxKilogramsPerSquareMeterSecond(double rainRateMillimetersPerHour) {
		double rainRateMetersPerSecond = Math.max(0.0, rainRateMillimetersPerHour) / 1000.0 / 3600.0;
		return rainRateMetersPerSecond * WATER_DENSITY_KG_PER_CUBIC_METER;
	}

	private static double rotorDiskAreaSquareMeters(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		}
		return total / config.rotors().size();
	}

	private static double allRotorsWaterGramsPerSecond(DroneConfig config, double waterMassFlux) {
		double totalArea = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			totalArea += Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		}
		return waterMassFlux * totalArea * 1000.0;
	}

	private static double precipitationWetnessProxy(double rainRateInchesPerHour) {
		return MathUtil.clamp(rainRateInchesPerHour / FULL_WETNESS_RAIN_RATE_INCHES_PER_HOUR, 0.0, 1.0);
	}

	private static double generatedReferenceRainThrustLossPercent(double wetness) {
		return (1.0 - MathUtil.clamp(
				1.0 - GENERATED_REFERENCE_RAIN_LOSS_COEFFICIENT
						* Math.pow(MathUtil.clamp(wetness, 0.0, 1.0), CURRENT_RAIN_LOSS_EXPONENT),
				0.90,
				1.0
		)) * 100.0;
	}

	private static double rainImpactForceOverWeight(
			DroneConfig config,
			double waterMassFlux,
			double impactSpeedMetersPerSecond
	) {
		double totalArea = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			totalArea += Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		}
		double impactForce = waterMassFlux * totalArea * Math.max(0.0, impactSpeedMetersPerSecond);
		return ratio(impactForce, config.massKg() * config.gravityMetersPerSecondSquared());
	}

	private static double averageHoverOmegaRadiansPerSecond(DroneConfig config) {
		double nominalHoverThrust = config.massKg() * config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += Math.sqrt(nominalHoverThrust / Math.max(EPSILON, rotor.thrustCoefficient()));
		}
		return total / config.rotors().size();
	}

	private static double waterDragCoefficientProxy(DroneConfig config) {
		double frontalAreaScale = Math.sqrt(Math.max(
				1.0e-6,
				config.bodyDragCoefficients().x() * config.bodyDragCoefficients().z()
		));
		return MathUtil.clamp(2.8 + 3.6 * frontalAreaScale, 2.8, 8.5);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
