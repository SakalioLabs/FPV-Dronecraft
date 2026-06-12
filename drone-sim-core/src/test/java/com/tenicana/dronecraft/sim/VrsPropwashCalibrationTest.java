package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VrsPropwashCalibrationTest {
	@Test
	void racingQuadAuditMatchesVrsPropwashPacketAnchors() {
		VrsPropwashCalibration.VrsPropwashAudit audit =
				VrsPropwashCalibration.audit(DroneConfig.racingQuad());

		assertEquals("VRS-Propwash-Calibration-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("separate calibration surfaces"));
		assertEquals(1094, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(726, audit.currentScanMetricRowCount());
		assertEquals(66, audit.currentScanScenarioCount());
		assertEquals(234, audit.currentVsShettyMetricRowCount());
		assertEquals(91, audit.shettyDigitizedMetricRowCount());
		assertEquals(13, audit.shettyDigitizedPointCount());
		assertEquals(28, audit.referenceAnchorMetricRowCount());

		VrsPropwashCalibration.ReferenceRegimeAudit reference = audit.referenceRegime();
		assertEquals(1.20, reference.cambridgePeakBandLowVi(), 1.0e-12);
		assertEquals(1.30, reference.cambridgePeakBandHighVi(), 1.0e-12);
		assertEquals(0.33, reference.cambridgePeakLossFraction(), 1.0e-12);
		assertEquals(0.50, reference.broadRegimeLowVi(), 1.0e-12);
		assertEquals(2.00, reference.broadRegimeHighVi(), 1.0e-12);
		assertEquals(0.6625, reference.shettyMaxDigitizedHalfAmplitudeFraction(), 1.0e-12);
		assertEquals(1.2396163037650905, reference.shettyMaxDigitizedDescentRatioVi(), 1.0e-15);
	}

	@Test
	void vrsScanKeepsMeanLossBuffetLateralAndPropwashTorqueSeparate() {
		VrsPropwashCalibration.VrsPropwashAudit audit =
				VrsPropwashCalibration.audit(DroneConfig.racingQuad());

		VrsPropwashCalibration.VrsScanSample early = audit.earlyEntry();
		assertEquals(0.75, early.descentRatioVi(), 1.0e-12);
		assertEquals(9.321696972452157, early.hoverInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(6.9912727293391175, early.descentSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(0.18601117322446423, early.currentVrsIntensityHoverSpinNoCrossflow(), 1.0e-15);
		assertEquals(0.352, early.currentVrsEntryComponent(), 1.0e-15);
		assertEquals(1.0, early.currentVrsExitComponent(), 1.0e-12);
		assertEquals(6.130928269478341, early.currentVrsBaseThrustLossPercentHoverSpin(), 1.0e-12);
		assertEquals(2.1441847717744467, early.currentVrsBuffetHalfAmplitudePercentMaxSpin(), 1.0e-12);
		assertEquals(14.0, early.currentVrsLateralForceBoundPercentMaxThrust(), 1.0e-12);
		assertEquals(0.9040137225168147, early.currentPropwashDescentFactor(), 1.0e-15);
		assertEquals(0.035, early.propwashMaxTorqueNewtonMeters(), 1.0e-12);
		assertEquals(5.585649805091385, early.buffetFrequencyHertzHoverSpin(), 1.0e-15);

		VrsPropwashCalibration.VrsScanSample peak = audit.peakBandLow();
		assertEquals(1.20, peak.descentRatioVi(), 1.0e-12);
		assertEquals(0.5284408330240461, peak.currentVrsIntensityHoverSpinNoCrossflow(), 1.0e-15);
		assertEquals(17.41740985647256, peak.currentVrsBaseThrustLossPercentHoverSpin(), 1.0e-12);
		assertEquals(27.900000000000002, peak.currentVrsBuffetHalfAmplitudePercentMaxSpin(), 1.0e-12);
		assertEquals(14.0, peak.currentVrsLateralForceBoundPercentMaxThrust(), 1.0e-12);
		assertEquals(1.0, peak.currentPropwashDescentFactor(), 1.0e-12);
		assertEquals(8.341664991962565, peak.buffetFrequencyHertzHoverSpin(), 1.0e-15);

		VrsPropwashCalibration.VrsScanSample exit = audit.highDescentExit();
		assertEquals(1.90, exit.descentRatioVi(), 1.0e-12);
		assertEquals(0.3360768175582993, exit.currentVrsExitComponent(), 1.0e-15);
		assertEquals(5.853587674671858, exit.currentVrsBaseThrustLossPercentHoverSpin(), 1.0e-12);
		assertEquals(1.67954089401458, exit.currentVrsBuffetHalfAmplitudePercentMaxSpin(), 1.0e-12);
		assertTrue(exit.currentVrsBuffetHalfAmplitudePercentMaxSpin()
				< peak.currentVrsBuffetHalfAmplitudePercentMaxSpin() * 0.07);
	}

	@Test
	void shettyComparisonsUseMaxSpinBuffetEnvelopeAsBoundsNotRmsFit() {
		VrsPropwashCalibration.VrsPropwashAudit audit =
				VrsPropwashCalibration.audit(DroneConfig.racingQuad());

		VrsPropwashCalibration.ShettyComparison largest = audit.largestShettyDigitized();
		assertEquals("APC Thin Electric 10x5", largest.referencePropeller());
		assertEquals(-0.30, largest.referenceAdvanceRatioJ(), 1.0e-12);
		assertEquals(1.2396163037650905, largest.descentRatioViProxy(), 1.0e-15);
		assertEquals(0.6625, largest.referenceMeasuredHalfAmplitudeFraction(), 1.0e-12);
		assertEquals(0.279, largest.currentVrsBuffetHalfAmplitudeFractionMaxSpin(), 1.0e-15);
		assertEquals(0.4211320754716982, largest.currentBuffetOverReferenceMeasuredHalfAmplitude(), 1.0e-15);
		assertEquals(0.9987878787878788, largest.currentBaseLossOverCambridgePeakLoss(), 1.0e-15);

		VrsPropwashCalibration.ShettyComparison best = audit.bestCurrentShettyMatch();
		assertEquals("APC Thin Electric 10x10", best.referencePropeller());
		assertEquals(-0.35, best.referenceAdvanceRatioJ(), 1.0e-12);
		assertEquals(1.3107472992273979, best.descentRatioViProxy(), 1.0e-15);
		assertEquals(0.36725663716814155, best.referenceMeasuredHalfAmplitudeFraction(), 1.0e-15);
		assertEquals(0.7596867469879519, best.currentBuffetOverReferenceMeasuredHalfAmplitude(), 1.0e-15);
		assertTrue(best.currentBuffetOverReferenceMeasuredHalfAmplitude()
				> largest.currentBuffetOverReferenceMeasuredHalfAmplitude());
	}

	@Test
	void activeEnvelopeUsesPacketScanRatios() {
		VrsPropwashCalibration.VrsActiveEnvelope active =
				VrsPropwashCalibration.audit(DroneConfig.racingQuad()).activeEnvelope();

		assertEquals(0.75, active.firstActiveDescentRatioVi(), 1.0e-12);
		assertEquals(1.90, active.lastActiveDescentRatioVi(), 1.0e-12);
		assertEquals(0.95, active.propwashFullyActiveFromDescentRatioVi(), 1.0e-12);
		assertEquals(1.20, active.peakLossDescentRatioVi(), 1.0e-12);
		assertEquals(17.41740985647256, active.peakHoverSpinLossPercent(), 1.0e-12);
	}

	@Test
	void vrsPropwashAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> VrsPropwashCalibration.audit(null));
	}
}
