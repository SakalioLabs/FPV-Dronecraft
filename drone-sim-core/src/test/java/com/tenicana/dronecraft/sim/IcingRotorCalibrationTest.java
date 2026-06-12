package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IcingRotorCalibrationTest {
	@Test
	void auditMatchesIcingRotorMdpiPacketCrosschecks() {
		IcingRotorCalibration.IcingRotorAudit audit = IcingRotorCalibration.audit();

		assertEquals("Icing-Rotor-MDPI-Packet", audit.sourceId());
		assertEquals("icing-time-accumulating-not-ordinary-rain", audit.caveat());
		assertEquals(362, audit.rowTypeCounts().totalRowCount());
		assertEquals(17, audit.rowTypeCounts().sourceInventoryRowCount());
		assertEquals(35, audit.rowTypeCounts().conditionGridRowCount());
		assertEquals(168, audit.rowTypeCounts().table4CaseRowCount());
		assertEquals(45, audit.rowTypeCounts().distributionRowCount());
		assertEquals(12, audit.rowTypeCounts().currentModelComparisonRowCount());
		assertEquals(4, audit.rowTypeCounts().extremeCaseRowCount());

		IcingRotorCalibration.SourceInventoryAudit source = audit.sourceInventory();
		assertEquals("An Experimental Apparatus for Icing Tests of Low Altitude Hovering Drones", source.paperTitle());
		assertEquals("https://doi.org/10.3390/drones6030068", source.doi());
		assertEquals("CC BY 4.0", source.license());
		assertEquals(4.0, source.rotorBladeCount(), 1.0e-12);
		assertEquals(0.66, source.rotorDiameterMeters(), 1.0e-12);
		assertEquals("NACA 4412", source.rotorAirfoil());
		assertEquals(4950.0, source.table4Rpm(), 1.0e-12);
		assertEquals(11.7, source.table4PitchDegrees(), 1.0e-12);
		assertEquals(80.0, source.table4LambdaGdm2h(), 1.0e-12);
		assertEquals(8.0, source.table4EquivalentRainMillimetersPerHour(), 1.0e-12);
		assertEquals(171.059719988, source.table4TipSpeedMetersPerSecond(), 1.0e-12);

		IcingRotorCalibration.DistributionAudit distribution = audit.distribution();
		assertEquals(0.012, distribution.absCtStarRateMinPercentPerSecond(), 1.0e-12);
		assertEquals(0.1025, distribution.absCtStarRateMedianPercentPerSecond(), 1.0e-12);
		assertEquals(0.226, distribution.absCtStarRateMaxPercentPerSecond(), 1.0e-12);
		assertEquals(194.5, distribution.icingTimeMedianSeconds(), 1.0e-12);
		assertEquals(19.388, distribution.projectedCtLossMedianPercent(), 1.0e-12);
		assertEquals(50.178, distribution.projectedPowerRequiredMedianPercent(), 1.0e-12);

		IcingRotorCalibration.CurrentModelComparisonAudit comparison = audit.currentModelComparison();
		assertEquals(3.0, comparison.currentFullWetnessRainLossPercent(), 1.0e-12);
		assertEquals(6.46266666667, comparison.icingProjectedCtLossMedianOverCurrentRainLoss(), 1.0e-12);
		assertEquals(7.98533333333, comparison.icingProjectedCtLossMaxOverCurrentRainLoss(), 1.0e-12);
		assertTrue(comparison.recommendation().contains("time-accumulating"));

		IcingRotorCalibration.ExtremeCaseAudit extreme = audit.extremeCase();
		assertEquals("MVD120_T-15_h4m", extreme.strongestProjectedCtLossCase());
		assertEquals(23.956, extreme.strongestProjectedCtLossPercent(), 1.0e-12);
		assertEquals("MVD120_T-5_h4m", extreme.strongestProjectedPowerRequiredCase());
		assertEquals(89.49, extreme.strongestProjectedPowerRequiredPercent(), 1.0e-12);
	}

	@Test
	void runtimeModelSeparatesFrozenAccretionFromOrdinaryRainWetness() {
		IcingRotorCalibration.RuntimeModelAudit runtime = IcingRotorCalibration.audit().runtimeModel();

		assertEquals(194.5, runtime.severityOneIcingTimeSeconds(), 1.0e-12);
		assertEquals(19.388, runtime.severityOneCtLossPercent(), 1.0e-12);
		assertEquals(0.80612, runtime.severityOneThrustScale(), 1.0e-12);
		assertEquals(1.50178, runtime.severityOnePowerScale(), 1.0e-12);
		assertEquals(24.235, runtime.maxModeledCtLossPercent(), 1.0e-12);
		assertEquals(1.627225, runtime.maxModeledPowerScale(), 1.0e-12);

		assertEquals(0.0, IcingRotorCalibration.freezingTemperatureFactor(5.0), 1.0e-12);
		assertEquals(1.0, IcingRotorCalibration.freezingTemperatureFactor(-8.0), 1.0e-12);
		assertTrue(IcingRotorCalibration.freezingTemperatureFactor(-25.0) > 0.70);
		assertTrue(IcingRotorCalibration.freezingTemperatureFactor(-25.0) < 0.90);
		assertEquals(1.0 / 194.5, runtime.fullWetMinusEightCSpinOneAccretionRatePerSecond(), 1.0e-15);
		assertEquals(0.0, IcingRotorCalibration.icingSeverityRatePerSecond(5.0, 1.0, 1.0), 1.0e-12);
		assertTrue(runtime.halfWetMinusEightCSpinOneAccretionRatePerSecond()
				< runtime.fullWetMinusEightCSpinOneAccretionRatePerSecond());
		assertTrue(IcingRotorCalibration.icingSeverityRatePerSecond(-8.0, 1.0, 0.05) < 1.0e-6);
		assertTrue(runtime.warmFiveCSpinOneRecoveryRatePerSecond()
				> runtime.fullWetMinusEightCSpinOneAccretionRatePerSecond());

		assertEquals(runtime.maxModeledCtLossPercent(),
				(1.0 - IcingRotorCalibration.icingThrustScale(10.0)) * 100.0,
				1.0e-12);
		assertEquals(runtime.maxModeledPowerScale(), IcingRotorCalibration.icingPowerScale(10.0), 1.0e-12);
		assertEquals(1.0, IcingRotorCalibration.icingThrustScale(-1.0), 1.0e-12);
		assertEquals(1.0, IcingRotorCalibration.icingPowerScale(-1.0), 1.0e-12);
		assertTrue(IcingRotorCalibration.icingAerodynamicLoadFactor(1.0) > 0.20);
		assertTrue(IcingRotorCalibration.icingMechanicalLossTorqueScale(1.0) > 1.50);
	}
}
