package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrecipitationWaterCalibrationTest {
	@Test
	void racingQuadAuditMatchesPrecipitationWaterPacketCrosschecks() {
		PrecipitationWaterCalibration.PrecipitationWaterAudit audit =
				PrecipitationWaterCalibration.audit(DroneConfig.racingQuad());

		assertEquals("Precipitation-Water-Calibration-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("wet-prop"));
		assertEquals(1608, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(42, audit.rainScanScenarioCount());
		assertEquals(108, audit.waterImmersionScanScenarioCount());
		assertEquals(20, audit.moistAirDensityScenarioCount());
		assertEquals(12, audit.currentVsIcasScenarioCount());
		assertEquals(2, audit.icasHeavyRainReferenceCount());

		PrecipitationWaterCalibration.RainFormulaAudit formula = audit.formula();
		assertEquals("clamp(1 - 0.03 * wetness^0.85, 0.96, 1)", formula.javaFormula());
		assertEquals(0.030, formula.javaLossCoefficient(), 1.0e-12);
		assertEquals(0.85, formula.javaLossExponent(), 1.0e-12);
		assertEquals(0.96, formula.javaMinimumThrustScale(), 1.0e-12);
		assertEquals(3.0, formula.javaFullWetnessThrustLossPercent(), 1.0e-12);
		assertEquals(5.5, formula.generatedReferenceFullWetnessThrustLossPercent(), 1.0e-12);
		assertEquals(0.5454545454545454, formula.javaLossOverGeneratedReferenceLoss(), 1.0e-15);

		PrecipitationWaterCalibration.IcasHeavyRainCtReference icas4319 = audit.icas4319Rpm();
		assertEquals("ICAS_2020_heavy_rain_4319rpm", icas4319.referenceId());
		assertEquals(4319.0, icas4319.rpm(), 1.0e-12);
		assertEquals(19.0, icas4319.liquidWaterContentGramsPerCubicMeter(), 1.0e-12);
		assertEquals(0.15314, icas4319.waterMassFluxKilogramsPerSquareMeterSecond(), 1.0e-12);
		assertEquals(552.962888665998, icas4319.equivalentRainRateMillimetersPerHour(), 1.0e-12);
		assertEquals(2.6408664962881856, icas4319.ctLossPercent(), 1.0e-15);
		assertEquals(1.6730861819932663, audit.icas6528Rpm().ctLossPercent(), 1.0e-15);

		PrecipitationWaterCalibration.IcasComparison icasComparison = audit.icas4319Comparison();
		assertEquals(3.0, icasComparison.javaFullWetnessThrustLossPercent(), 1.0e-12);
		assertEquals(1.135990783410137, icasComparison.javaLossOverIcasLoss(), 1.0e-12);
		assertEquals(2.0826497695852515, icasComparison.generatedReferenceLossOverIcasLoss(), 1.0e-12);
		assertEquals(7.759718334899081, icasComparison.allRotorsWaterGramsPerSecondAtIcasLwc(), 1.0e-12);
		assertEquals(0.005754698613991772, icasComparison.rainImpactForceAllRotorsOverWeightAt8MetersPerSecond(), 1.0e-15);
		assertEquals(1.7930935251798488, audit.icas6528Comparison().javaLossOverIcasLoss(), 1.0e-12);

		PrecipitationWaterCalibration.RainScenarioAudit light = audit.nwsLightRain005InHour();
		assertEquals("nws_0.05_in_h", light.scenario());
		assertEquals(1.27, light.rainRateMillimetersPerHour(), 1.0e-12);
		assertEquals(0.05, light.rainRateInchesPerHour(), 1.0e-12);
		assertEquals(0.0003517194444444445, light.waterMassFluxKilogramsPerSquareMeterSecond(), 1.0e-18);
		assertEquals(0.012667686977437443, light.rotorDiskAreaSquareMeters(), 1.0e-15);
		assertEquals(0.017821887304401686, light.allRotorsWaterGramsPerSecond(), 1.0e-15);
		assertEquals(1.0 / 30.0, light.precipitationWetnessProxy(), 1.0e-15);
		assertEquals(0.30535822536604806, light.generatedReferenceThrustLossPercent(), 1.0e-12);
		assertEquals(0.166559032018, light.javaSourceThrustLossPercent(), 1.0e-12);

		PrecipitationWaterCalibration.RainScenarioAudit moderate = audit.nwsModerateRain025InHour();
		assertEquals(6.35, moderate.rainRateMillimetersPerHour(), 1.0e-12);
		assertEquals(0.16666666666666666, moderate.precipitationWetnessProxy(), 1.0e-15);
		assertEquals(0.08910943652200842, moderate.allRotorsWaterGramsPerSecond(), 1.0e-15);
		assertEquals(1.1993173781496558, moderate.generatedReferenceThrustLossPercent(), 1.0e-12);
		assertEquals(0.654173115354, moderate.javaSourceThrustLossPercent(), 1.0e-12);
		assertEquals(0.016560346304456495, moderate.rotorPrecipitationLoadFactor(), 1.0e-15);
		assertEquals(0.006810844855014762, moderate.rotorPrecipitationVibrationAtHover(), 1.0e-15);

		PrecipitationWaterCalibration.RainScenarioAudit fullWet = audit.nwsFullWetness150InHour();
		assertEquals("nws_1.50_in_h_full_wetness", fullWet.scenario());
		assertEquals(38.1, fullWet.rainRateMillimetersPerHour(), 1.0e-12);
		assertEquals(1.0, fullWet.precipitationWetnessProxy(), 1.0e-12);
		assertEquals(0.5346566191320506, fullWet.allRotorsWaterGramsPerSecond(), 1.0e-15);
		assertEquals(5.5, fullWet.generatedReferenceThrustLossPercent(), 1.0e-12);
		assertEquals(3.0, fullWet.javaSourceThrustLossPercent(), 1.0e-12);
		assertEquals(0.13, fullWet.rotorPrecipitationLoadFactor(), 1.0e-12);
		assertEquals(0.044695088387046805, fullWet.rotorPrecipitationVibrationAtHover(), 1.0e-15);

		PrecipitationWaterCalibration.RainScenarioAudit stress100 = audit.stress100MillimetersPerHour();
		assertEquals("stress_100_mm_h", stress100.scenario());
		assertEquals(100.0, stress100.rainRateMillimetersPerHour(), 1.0e-12);
		assertEquals(3.937007874015748, stress100.rainRateInchesPerHour(), 1.0e-15);
		assertEquals(1.4032982129450147, stress100.allRotorsWaterGramsPerSecond(), 1.0e-15);
		assertEquals(0.0010407025013694435, stress100.rainImpactForceAllRotorsOverWeightAt8MetersPerSecond(), 1.0e-15);

		PrecipitationWaterCalibration.WaterImmersionAudit water = audit.halfImmersionAt5MetersPerSecond();
		assertEquals(0.5, water.waterImmersion(), 1.0e-12);
		assertEquals(5.0, water.speedMetersPerSecond(), 1.0e-12);
		assertEquals(49.78199026019692, water.waterImmersionThrustLossPercent(), 1.0e-12);
		assertEquals(0.47754077045206683, water.rotorWaterLoadFactor(), 1.0e-15);
		assertEquals(0.13671983974244217, water.rotorWaterIngestionVibrationAtHover(), 1.0e-15);
		assertEquals(31.679796059071748, water.waterDragForceNewtons(), 1.0e-12);
		assertEquals(2.9367637877517945, water.waterDragOverWeight(), 1.0e-12);
		assertEquals(2.812074767078499, water.waterDragCoefficientProxy(), 1.0e-15);

		PrecipitationWaterCalibration.MoistAirDensityAudit moist = audit.hotFullWetMoistAir();
		assertEquals(35.0, moist.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(1.0, moist.precipitationWetness(), 1.0e-12);
		assertEquals(0.9789925675447962, moist.moistAirDensityMultiplier(), 1.0e-15);
	}

	@Test
	void liftPresetAuditKeepsRainMassFluxSeparateFromWaterImmersion() {
		PrecipitationWaterCalibration.PrecipitationWaterAudit racing =
				PrecipitationWaterCalibration.audit(DroneConfig.racingQuad());
		PrecipitationWaterCalibration.PrecipitationWaterAudit heavy =
				PrecipitationWaterCalibration.audit(DroneConfig.heavyLift());

		assertTrue(heavy.nwsFullWetness150InHour().allRotorsWaterGramsPerSecond()
				> racing.nwsFullWetness150InHour().allRotorsWaterGramsPerSecond() * 3.5);
		assertEquals(racing.formula().javaFullWetnessThrustLossPercent(),
				heavy.formula().javaFullWetnessThrustLossPercent(),
				1.0e-12);
		assertTrue(heavy.halfImmersionAt5MetersPerSecond().waterDragOverWeight()
				< racing.halfImmersionAt5MetersPerSecond().waterDragOverWeight());
		assertEquals(0.9789925675447962,
				heavy.hotFullWetMoistAir().moistAirDensityMultiplier(),
				1.0e-15);
	}

	@Test
	void precipitationWaterAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> PrecipitationWaterCalibration.audit(null));
	}
}
