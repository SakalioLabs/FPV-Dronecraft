package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HighAdvanceRotorCalibrationTest {
	@Test
	void highAdvanceAuditSeparatesAxialApcPropellerShapeFromEdgewiseRotorMu() {
		HighAdvanceRotorCalibration.HighAdvanceAudit audit =
				HighAdvanceRotorCalibration.audit(DroneConfig.racingQuad());

		assertEquals("APC-High-J-Axial-Propeller-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("axial propeller predictions"));
		assertEquals(7, audit.selectedApcPropellerCount());
		assertEquals(7_590, audit.selectedApcRowCount());
		assertEquals(2.4664, audit.selectedApcMaxAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.785079503284, audit.selectedApcMaxEquivalentProjectMu(), 1.0e-12);
		assertEquals(0.85, audit.representativeRotorPitchToDiameterRatio(), 1.0e-12);
		assertEquals(3, audit.representativeRotorBladeCount());

		assertEquals("APC_5x4E_3blade", audit.conventionalThreeBladeReference().propellerId());
		assertEquals(0.9545, audit.conventionalThreeBladeReference().nearestZeroCtAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.305100025907, audit.conventionalThreeBladeReference().highestPositiveCtEquivalentProjectMu(), 1.0e-12);
		assertEquals("APC_5.1x5.0E_3blade", audit.fpvAdjacentThreeBladeReference().propellerId());
		assertEquals(1.1241, audit.fpvAdjacentThreeBladeReference().nearestZeroCtAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.359531016445, audit.fpvAdjacentThreeBladeReference().highestPositiveCtEquivalentProjectMu(), 1.0e-12);
		assertEquals("APC_5x11E", audit.extremeHighPitchReference().propellerId());
		assertEquals(0.781387108604, audit.extremeHighPitchReference().highestPositiveCtEquivalentProjectMu(), 1.0e-12);

		HighAdvanceRotorCalibration.AdvancePointAudit uiucMax = audit.uiucMeasuredRangeMax();
		assertEquals(0.571, uiucMax.targetEquivalentAdvanceRatioJ(), 1.0e-12);
		assertTrue(uiucMax.targetWithinApcFileRange());
		assertEquals(0.711514129059, uiucMax.apcCtOverStaticCt(), 1.0e-12);
		assertEquals(1.03515151515, uiucMax.apcCpOverStaticCp(), 1.0e-12);
		assertTrue(uiucMax.currentThrustScale() > 0.30);
		assertTrue(uiucMax.currentThrustScale() < 0.40);
		assertTrue(uiucMax.currentPowerScale() > 0.45);
		assertTrue(uiucMax.currentPowerScale() < 0.60);
		assertTrue(uiucMax.currentThrustScaleOverApcCtRatio() < 0.60);

		HighAdvanceRotorCalibration.AdvancePointAudit fpvLiftEnd = audit.fpvAdjacentLiftDissymmetryEnd();
		assertEquals(1.06814150222, fpvLiftEnd.targetEquivalentAdvanceRatioJ(), 1.0e-12);
		assertTrue(fpvLiftEnd.targetWithinApcFileRange());
		assertEquals(0.0793319415449, fpvLiftEnd.apcCtOverStaticCt(), 1.0e-12);
		assertEquals(0.342158859470, fpvLiftEnd.apcCpOverStaticCp(), 1.0e-12);
		assertTrue(fpvLiftEnd.currentThrustScale() >= 0.12);
		assertTrue(fpvLiftEnd.currentThrustScale() < 0.14);
		assertTrue(fpvLiftEnd.currentPowerScale() > 0.18);
		assertTrue(fpvLiftEnd.currentPowerScale() < 0.20);

		HighAdvanceRotorCalibration.AdvancePointAudit highPitch = audit.extremePitchHighAdvanceLossStart();
		assertEquals(1.44513262065, highPitch.targetEquivalentAdvanceRatioJ(), 1.0e-12);
		assertTrue(highPitch.targetWithinApcFileRange());
		assertEquals(1.00575815739, highPitch.apcCtOverStaticCt(), 1.0e-12);
		assertEquals(1.45454545455, highPitch.apcCpOverStaticCp(), 1.0e-12);
		assertTrue(highPitch.currentThrustScale() < 0.14);
		assertTrue(highPitch.currentPowerScale() < 0.20);
		assertTrue(highPitch.currentThrustScaleOverApcCtRatio() < 0.14);

		HighAdvanceRotorCalibration.AdvancePointAudit stallEnd = audit.extremePitchRetreatingStallEnd();
		assertFalse(stallEnd.targetWithinApcFileRange());
		assertEquals(0.0, stallEnd.apcCtOverStaticCt(), 1.0e-12);
		assertEquals(0.0, stallEnd.currentThrustScaleOverApcCtRatio(), 1.0e-12);
	}
}
