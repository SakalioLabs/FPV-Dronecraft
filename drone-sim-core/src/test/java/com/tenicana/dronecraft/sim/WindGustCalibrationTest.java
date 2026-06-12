package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindGustCalibrationTest {
	@Test
	void windGustAuditMatchesRepresentativeDrydenPacketRows() {
		WindGustCalibration.WindGustAudit audit = WindGustCalibration.audit();

		assertEquals("Wind-Gust-Dryden-Calibration-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Dryden"));
		assertEquals(632, audit.packetMetricRowCount());
		assertEquals(4, audit.sourceReferenceCount());
		assertEquals(12, audit.currentWindScanCount());
		assertEquals(240, audit.spectralShapeMetricRowCount());
		assertEquals(12, audit.icasHoverGustRowCount());

		WindGustCalibration.WindScenarioAudit representative = audit.representativeDirtyAir();
		assertEquals("wind_10m_s_dirty_1p5_alt_6m", representative.scenarioId());
		assertEquals(1.5, representative.dirtyAir(), 1.0e-12);
		assertEquals(10.0, representative.windSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(6.0, representative.altitudeMeters(), 1.0e-12);
		assertEquals(1.53, representative.currentGustScale(), 1.0e-12);
		assertEquals(0.32, representative.currentBurbleScale(), 1.0e-12);
		assertEquals(0.8333333333333333, representative.drydenIntensityScale(), 1.0e-15);
		assertEquals(1.6529010694070865, representative.currentGustRmsXMetersPerSecond(), 1.0e-15);
		assertEquals(0.8434262508011264, representative.currentGustRmsYMetersPerSecond(), 1.0e-15);
		assertEquals(1.6515264077861698, representative.currentGustRmsZMetersPerSecond(), 1.0e-15);
		assertEquals(0.3806305451536962, representative.currentBurbleRmsXMetersPerSecond(), 1.0e-15);
		assertEquals(0.13008995386270225, representative.currentBurbleRmsYMetersPerSecond(), 1.0e-15);
		assertEquals(0.3746159930809148, representative.currentBurbleRmsZMetersPerSecond(), 1.0e-15);
		assertEquals(1.6084782663570838, representative.drydenTargetRmsXMetersPerSecond(), 1.0e-15);
		assertEquals(0.8333333333333335, representative.drydenTargetRmsYMetersPerSecond(), 1.0e-15);
		assertEquals(4.000316532714168, representative.currentGustPeakXMetersPerSecond(), 1.0e-15);
		assertEquals(1.911466666666667, representative.currentGustPeakYMetersPerSecond(), 1.0e-15);
		assertEquals(3.9856285327141676, representative.currentGustPeakZMetersPerSecond(), 1.0e-15);
		assertEquals(1.9301739196285006, representative.drydenSigmaUMetersPerSecond(), 1.0e-15);
		assertEquals(1.0000000000000002, representative.drydenSigmaWMetersPerSecond(), 1.0e-15);
		assertEquals(0.8563482557702465, representative.currentXRmsOverDrydenU(), 1.0e-15);
		assertEquals(0.8434262508011262, representative.currentYRmsOverDrydenW(), 1.0e-15);

		WindGustCalibration.SpectralShapeAudit spectral = audit.representativeSpectralShape();
		assertEquals(1.3022145714361837, spectral.phaseAPeriodSeconds(), 1.0e-15);
		assertEquals(1.404063755794321, spectral.phaseBPeriodSeconds(), 1.0e-15);
		assertEquals(1.5418859649520456, spectral.phaseCPeriodSeconds(), 1.0e-15);
		assertEquals(0.11594594594594596, spectral.gustTimeConstantSeconds(), 1.0e-15);
		assertEquals(0.445, spectral.meanWindTimeConstantSeconds(), 1.0e-15);
		assertEquals(43.146004048884, spectral.drydenLongitudinalScaleMeters(), 1.0e-12);
		assertEquals(6.0, spectral.drydenVerticalScaleMeters(), 1.0e-12);
		assertEquals(4.314600404888401, spectral.drydenLongitudinalTimeSeconds(), 1.0e-15);
		assertEquals(0.6, spectral.drydenVerticalTimeSeconds(), 1.0e-15);
		assertEquals(1.37266501034968, spectral.currentGustCornerHertz(), 1.0e-15);
		assertEquals(0.35765155750987715, spectral.currentMeanWindCornerHertz(), 1.0e-15);
		assertEquals(0.03688752796471588, spectral.drydenLongitudinalPoleHertz(), 1.0e-15);
		assertEquals(0.2652582384864922, spectral.drydenVerticalPoleHertz(), 1.0e-15);
		assertEquals(0.15314691539494224, spectral.drydenVerticalZeroHertz(), 1.0e-15);
		assertEquals(37.21217132421231, spectral.currentCornerOverDrydenLongitudinalPole(), 1.0e-12);
		assertEquals(5.174825174825175, spectral.currentCornerOverDrydenVerticalPole(), 1.0e-15);
		assertEquals(12.770714456071984, spectral.currentShapeOverDrydenLongitudinalAtHalfHertz(), 1.0e-12);
		assertEquals(1.2528969181517062, spectral.currentShapeOverDrydenVerticalAtHalfHertz(), 1.0e-15);
		assertEquals(21.92637814518929, spectral.currentShapeOverDrydenLongitudinalAtOneHertz(), 1.0e-12);
		assertEquals(1.8613076820745733, spectral.currentShapeOverDrydenVerticalAtOneHertz(), 1.0e-15);
	}

	@Test
	void windGustAuditTracksLightAndSaturatedDirtyAirExtrema() {
		WindGustCalibration.WindGustAudit audit = WindGustCalibration.audit();

		WindGustCalibration.WindScenarioAudit light = audit.lightDirtyAir();
		assertEquals("wind_5m_s_dirty_0p25_alt_6m", light.scenarioId());
		assertEquals(0.14036773095850102, light.currentGustRmsXMetersPerSecond(), 1.0e-15);
		assertEquals(0.07088978657181536, light.currentGustRmsYMetersPerSecond(), 1.0e-15);
		assertEquals(0.14017366836334894, light.currentGustRmsZMetersPerSecond(), 1.0e-15);
		assertEquals(0.1454456818953574, light.currentXRmsOverDrydenU(), 1.0e-15);
		assertEquals(0.14177957314363068, light.currentYRmsOverDrydenW(), 1.0e-15);

		WindGustCalibration.WindScenarioAudit saturated = audit.saturatedDirtyAir();
		assertEquals("wind_15m_s_dirty_1p8_alt_6m", saturated.scenarioId());
		assertEquals(2.9595441727264302, saturated.currentGustRmsXMetersPerSecond(), 1.0e-15);
		assertEquals(1.514583557428457, saturated.currentGustRmsYMetersPerSecond(), 1.0e-15);
		assertEquals(2.9575498894733947, saturated.currentGustRmsZMetersPerSecond(), 1.0e-15);
		assertEquals(7.053113758885502, saturated.currentGustPeakXMetersPerSecond(), 1.0e-15);
		assertEquals(3.3945600000000002, saturated.currentGustPeakYMetersPerSecond(), 1.0e-15);
		assertEquals(7.029440158885501, saturated.currentGustPeakZMetersPerSecond(), 1.0e-15);

		assertEquals(0.141972630061, audit.currentXRmsOverDrydenUMin(), 1.0e-12);
		assertEquals(1.0472089096465733, audit.currentXRmsOverDrydenUMax(), 1.0e-15);
		assertEquals(0.14023921828, audit.currentYRmsOverDrydenWMin(), 1.0e-12);
		assertEquals(1.0208129266341408, audit.currentYRmsOverDrydenWMax(), 1.0e-15);
		assertEquals(13.589292613821732, audit.currentCornerOverDrydenLongitudinalPoleMin(), 1.0e-12);
		assertEquals(78.78038955847183, audit.currentCornerOverDrydenLongitudinalPoleMax(), 1.0e-12);
		assertEquals(44.6710318104, audit.currentShapeOverDrydenLongitudinalAtOneHertzMax(), 1.0e-10);
		assertEquals(3.63836014283, audit.currentShapeOverDrydenVerticalAtOneHertzMax(), 1.0e-10);
	}

	@Test
	void windGustAuditKeepsIcasRotorGustCtSeparateFromWindFieldRms() {
		WindGustCalibration.WindGustAudit audit = WindGustCalibration.audit();

		assertEquals("ICAS_2020_hover_gust_4319rpm_-90deg", audit.strongest4319Downdraft().referenceId());
		assertEquals(4319.0, audit.strongest4319Downdraft().rpm(), 1.0e-12);
		assertEquals(10.0, audit.strongest4319Downdraft().gustSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(-90.0, audit.strongest4319Downdraft().gustDirectionDegrees(), 1.0e-12);
		assertEquals(0.008221, audit.strongest4319Downdraft().ctNoWind(), 1.0e-15);
		assertEquals(-0.002579, audit.strongest4319Downdraft().ctGust(), 1.0e-15);
		assertEquals(-131.37087945505414, audit.strongest4319Downdraft().ctChangePercent(), 1.0e-12);

		assertEquals("ICAS_2020_hover_gust_4319rpm_90deg", audit.strongest4319Updraft().referenceId());
		assertEquals(120.16786279041484, audit.strongest4319Updraft().ctChangePercent(), 1.0e-12);
		assertEquals("ICAS_2020_hover_gust_6528rpm_-90deg", audit.strongest6528Downdraft().referenceId());
		assertEquals(-68.26390561040212, audit.strongest6528Downdraft().ctChangePercent(), 1.0e-12);
		assertEquals("ICAS_2020_hover_gust_6528rpm_90deg", audit.strongest6528Updraft().referenceId());
		assertEquals(42.18637129785699, audit.strongest6528Updraft().ctChangePercent(), 1.0e-12);
	}
}
