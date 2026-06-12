package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FpvLipoEsrCalibrationTest {
	@Test
	void auditMatchesFpvLipoEsrPacketAnchors() {
		FpvLipoEsrCalibration.FpvLipoEsrAudit audit =
				FpvLipoEsrCalibration.audit(DroneConfig.racingQuad());

		assertEquals("FPV-LiPo-ESR-Calibration-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("absolute high-C pack scale"));
		assertEquals(774, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceInventoryRowCount());
		assertEquals(60, audit.measuredIrAnchorRowCount());
		assertEquals(54, audit.currentPresetAbsoluteCheckRowCount());
		assertEquals(80, audit.currentVsMeasuredIrRowCount());
		assertEquals(70, audit.currentFormulaGuardrailRowCount());
		assertEquals(396, audit.socSohRuntimeProjectionRowCount());
		assertEquals(30, audit.temperatureModelCheckRowCount());
		assertEquals(18, audit.temperatureReferenceCheckRowCount());
		assertEquals(12, audit.cRateTemperatureShapeRowCount());
		assertEquals(8, audit.methodGuardrailRowCount());
		assertEquals(39, audit.summaryRowCount());

		FpvLipoEsrCalibration.MeasuredIrRange range = audit.measuredRange();
		assertEquals(5, range.measuredPackCount());
		assertEquals(4.975, range.measuredPerCellIrMinMilliohms(), 1.0e-12);
		assertEquals(6.525, range.measuredPerCellIrMedianMilliohms(), 1.0e-12);
		assertEquals(6.85, range.measuredPerCellIrMaxMilliohms(), 1.0e-12);
		assertEquals(19.9, range.measuredPackIrMinMilliohms(), 1.0e-12);
		assertEquals(27.4, range.measuredPackIrMaxMilliohms(), 1.0e-12);
		assertEquals(1.791, range.measuredSagAt90AmpsMinVolts(), 1.0e-12);
		assertEquals(2.466, range.measuredSagAt90AmpsMaxVolts(), 1.0e-12);
	}

	@Test
	void measuredIrAnchorsPreserveFastMedianAndHighIrPackRows() {
		FpvLipoEsrCalibration.FpvLipoEsrAudit audit =
				FpvLipoEsrCalibration.audit(DroneConfig.racingQuad());

		FpvLipoEsrCalibration.MeasuredIrAnchor fastest = audit.lowestMeasuredAnchor();
		assertEquals("Acehe Formula 4S 95C 1500mAh", fastest.packName());
		assertEquals(4, fastest.cells());
		assertEquals(1.50, fastest.capacityAmpHours(), 1.0e-12);
		assertEquals(95.0, fastest.listedC(), 1.0e-12);
		assertEquals(142.5, fastest.listedCurrentAmps(), 1.0e-12);
		assertEquals(4.975, fastest.perCellIrMeanMilliohms(), 1.0e-12);
		assertEquals(19.9, fastest.packIrMilliohms(), 1.0e-12);
		assertEquals(1.7909999999999997, fastest.sagAt90AmpsVolts(), 1.0e-15);

		FpvLipoEsrCalibration.MeasuredIrAnchor median = audit.medianMeasuredAnchor();
		assertEquals("Tattu 4S 45C 1550mAh", median.packName());
		assertEquals(6.525, median.perCellIrMeanMilliohms(), 1.0e-12);
		assertEquals(26.1, median.packIrMilliohms(), 1.0e-12);

		FpvLipoEsrCalibration.MeasuredIrAnchor high = audit.highestMeasuredAnchor();
		assertEquals("DroneLab Chaos 4S 75C 1300mAh", high.packName());
		assertEquals(6.85, high.perCellIrMeanMilliohms(), 1.0e-12);
		assertEquals(27.4, high.packIrMilliohms(), 1.0e-12);
	}

	@Test
	void configuredPackAuditUsesLiveRacingQuadBatteryFields() {
		FpvLipoEsrCalibration.ConfiguredPackAudit configured =
				FpvLipoEsrCalibration.audit(DroneConfig.racingQuad()).configuredPack();

		assertEquals(4, configured.cells());
		assertEquals(16.8, configured.nominalVoltage(), 1.0e-12);
		assertEquals(1.5, configured.capacityAmpHours(), 1.0e-12);
		assertEquals(90.0, configured.maxCurrentAmps(), 1.0e-12);
		assertEquals(60.0, configured.currentLimitC(), 1.0e-12);
		assertEquals(0.018, configured.packResistanceOhms(), 1.0e-15);
		assertEquals(4.5, configured.perCellIrMilliohms(), 1.0e-12);
		assertEquals(1.6199999999999999, configured.sagAtCurrentLimitVolts(), 1.0e-15);
		assertEquals(9.642857142857142, configured.sagAtCurrentLimitPercentNominal(), 1.0e-12);
		assertEquals(186.66666666666666, configured.currentForTwentyPercentNominalSagAmps(), 1.0e-12);
		assertEquals(0.48214285714285715, configured.configuredCurrentOverTwentyPercentSagCurrent(), 1.0e-15);
		assertEquals(0.9045226130653267, configured.configuredPerCellIrOverLowestMeasuredFpvAnchor(), 1.0e-15);
		assertEquals(0.6896551724137931, configured.configuredPerCellIrOverMeasuredMedian(), 1.0e-15);
		assertEquals(0.6569343065693431, configured.configuredPerCellIrOverHighestMeasuredFpvAnchor(), 1.0e-15);
	}

	@Test
	void irFormulaGuardrailFlagsManufacturerCAsBurstNotSustainedCurrent() {
		FpvLipoEsrCalibration.IrFormulaGuardrail guardrail =
				FpvLipoEsrCalibration.audit(DroneConfig.racingQuad()).formulaGuardrail();

		assertEquals(4.5, guardrail.configuredPerCellIrMilliohms(), 1.0e-12);
		assertEquals(60.0, guardrail.configuredCurrentLimitC(), 1.0e-12);
		assertEquals(30.4290309725, guardrail.irFormulaTrueC(), 1.0e-10);
		assertEquals(45.6435464588, guardrail.irFormulaTrueCurrentAmps(), 1.0e-10);
		assertEquals(1.97180120702, guardrail.configuredCurrentOverIrFormulaCurrent(), 1.0e-11);
		assertEquals(0.821583836258, guardrail.sagAtIrFormulaCurrentVolts(), 1.0e-12);
		assertEquals(1.62, guardrail.sagAtConfiguredCurrentVolts(), 1.0e-12);
		assertEquals(9.64285714286, guardrail.sagAtConfiguredCurrentPercentNominal(), 1.0e-11);
		assertEquals(32.2748612184, guardrail.coldTenCdropIrDoubledTrueCurrentAmps(), 1.0e-10);
		assertEquals(2.78854800927, guardrail.configuredCurrentOverColdTenCdropCurrent(), 1.0e-11);
	}

	@Test
	void socSohProjectionKeepsAbsoluteEsrSeparateFromMendeleyShape() {
		FpvLipoEsrCalibration.FpvLipoEsrAudit audit =
				FpvLipoEsrCalibration.audit(DroneConfig.racingQuad());

		FpvLipoEsrCalibration.SocSohProjection fresh = audit.freshFullProjection();
		assertEquals("fresh_full", fresh.projectionId());
		assertEquals(1.002684224007052, fresh.resistanceScale(), 1.0e-15);
		assertEquals(0.018048316032126935, fresh.projectedPackResistanceOhms(), 1.0e-15);
		assertEquals(4.5120790080317335, fresh.projectedPerCellResistanceMilliohms(), 1.0e-15);
		assertEquals(1.624348442891424, fresh.configCurrentSagVolts(), 1.0e-15);
		assertEquals(186.16695286247352, fresh.configCurrentForTwentyPercentNominalSagAmps(), 1.0e-12);
		assertEquals(0.48343703657482856, fresh.configCurrentOverTwentyPercentSagCurrent(), 1.0e-15);

		FpvLipoEsrCalibration.SocSohProjection worn = audit.wornTenPercentProjection();
		assertEquals("worn_10pct", worn.projectionId());
		assertEquals(1.1902959848598708, worn.resistanceScale(), 1.0e-15);
		assertEquals(0.021425327727477673, worn.projectedPackResistanceOhms(), 1.0e-15);
		assertEquals(5.356331931869418, worn.projectedPerCellResistanceMilliohms(), 1.0e-15);
		assertEquals(1.9282794954729905, worn.configCurrentSagVolts(), 1.0e-15);
		assertEquals(156.82373883554877, worn.configCurrentForTwentyPercentNominalSagAmps(), 1.0e-12);
		assertEquals(0.573892706986009, worn.configCurrentOverTwentyPercentSagCurrent(), 1.0e-15);
	}

	@Test
	void temperatureAuditUsesRuntimeBatteryTemperatureFunctions() {
		FpvLipoEsrCalibration.FpvLipoEsrAudit audit =
				FpvLipoEsrCalibration.audit(DroneConfig.racingQuad());

		FpvLipoEsrCalibration.TemperatureModelPoint cold = audit.coldZeroCelsius();
		assertEquals(0.0, cold.batteryTemperatureCelsius(), 1.0e-12);
		assertEquals(25.0, cold.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(2.0147500000000003, cold.resistanceScale(), 1.0e-15);
		assertEquals(0.7250000000000001, cold.currentScale(), 1.0e-15);
		assertEquals(1.0, cold.thermalPowerLimit(), 1.0e-12);
		assertEquals(0.0362655, cold.effectiveResistanceOhms(), 1.0e-15);
		assertEquals(65.25000000000001, cold.effectiveCurrentLimitAmps(), 1.0e-14);
		assertEquals(2.3663238750000004, cold.sagAtTemperatureScaledLimitVolts(), 1.0e-15);

		FpvLipoEsrCalibration.TemperatureModelPoint room = audit.roomTwentyFiveCelsius();
		assertEquals(1.0, room.resistanceScale(), 1.0e-12);
		assertEquals(1.0, room.currentScale(), 1.0e-12);
		assertEquals(1.6199999999999999, room.sagAtTemperatureScaledLimitVolts(), 1.0e-15);

		FpvLipoEsrCalibration.TemperatureModelPoint hot = audit.hotSeventyCelsius();
		assertEquals(70.0, hot.batteryTemperatureCelsius(), 1.0e-12);
		assertEquals(1.06795, hot.resistanceScale(), 1.0e-15);
		assertEquals(0.832, hot.currentScale(), 1.0e-15);
		assertEquals(0.7835276967930029, hot.thermalPowerLimit(), 1.0e-15);
		assertEquals(0.019223099999999996, hot.effectiveResistanceOhms(), 1.0e-15);
		assertEquals(74.88, hot.effectiveCurrentLimitAmps(), 1.0e-12);
		assertEquals(1.4394257279999996, hot.sagAtTemperatureScaledLimitVolts(), 1.0e-15);

		FpvLipoEsrCalibration.TemperatureReferenceCheck reference = audit.temperatureReference();
		assertEquals(1.67693661972, reference.jeffcoReferenceColdOverWarmIrMin(), 1.0e-12);
		assertEquals(2.03448275862, reference.jeffcoReferenceColdOverWarmIrMax(), 1.0e-12);
		assertEquals(0.747690178085, reference.currentModelOverJeffcoMin(), 1.0e-12);
		assertEquals(0.907108091157, reference.currentModelOverJeffcoMax(), 1.0e-12);
	}

	@Test
	void auditRequiresConfig() {
		assertThrows(IllegalArgumentException.class, () -> FpvLipoEsrCalibration.audit(null));
	}
}
