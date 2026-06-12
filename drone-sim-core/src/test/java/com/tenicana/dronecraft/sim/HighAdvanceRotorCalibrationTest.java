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

	@Test
	void mejzlikWindTunnelAuditAddsMeasuredAxialHighJBoundaryWithoutEdgewiseRetune() {
		HighAdvanceRotorCalibration.MejzlikWindTunnelAudit audit =
				HighAdvanceRotorCalibration.audit(DroneConfig.racingQuad()).mejzlikWindTunnelAudit();

		assertEquals("Mejzlik-Wind-Tunnel-Prop-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("axial propeller wind-tunnel"));
		assertEquals(129, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceInventoryRowCount());
		assertEquals(52, audit.tableValueRowCount());
		assertEquals(60, audit.modelVsTunnelRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(4900.0, audit.windTunnelRpm(), 1.0e-12);
		assertEquals(35.0, audit.windTunnelSpeedMaxMetersPerSecond(), 1.0e-12);
		assertEquals(60.0, audit.cfdMaxSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(1.38, audit.cfdMaxAdvanceRatioJ(), 1.0e-12);
		assertEquals(4, audit.publishedTableJCount());
		assertEquals(0.784304932735, audit.ctZeroCrossingAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.249652013872, audit.ctZeroCrossingProjectMu(), 1.0e-12);
		assertEquals(21.6197905685, audit.racingHoverSpeedAtCtZeroMetersPerSecond(), 1.0e-10);
		assertEquals(1.06814150222, audit.currentLiftDissymmetryEndAdvanceRatioJ(), 1.0e-12);
		assertEquals(1.31946891451, audit.currentRetreatingStallStartAdvanceRatioJ(), 1.0e-12);
		assertEquals(1.44513262065, audit.currentHighAdvanceLossStartAdvanceRatioJ(), 1.0e-12);
		assertEquals(1.36189568322, audit.currentLiftDissymmetryEndOverCtZeroJ(), 1.0e-11);
		assertEquals(1.68234172633, audit.currentRetreatingStallStartOverCtZeroJ(), 1.0e-11);
		assertEquals(1.84256474789, audit.currentHighAdvanceLossStartOverCtZeroJ(), 1.0e-11);
		assertEquals(39.8358639581, audit.racingHoverSpeedAtCurrentHighAdvanceLossStartMetersPerSecond(), 1.0e-10);

		HighAdvanceRotorCalibration.MejzlikWindTunnelPoint low = audit.lowAdvancePoint();
		assertEquals("wind_tunnel_j_0.2", low.pointId());
		assertEquals(0.2, low.advanceRatioJ(), 1.0e-12);
		assertEquals(0.06366197723675814, low.codeEquivalentProjectMu(), 1.0e-15);
		assertEquals(0.0918, low.windTunnelCt(), 1.0e-15);
		assertEquals(0.0417, low.windTunnelCp(), 1.0e-15);
		assertEquals(0.4452, low.windTunnelEfficiency(), 1.0e-15);
		assertEquals(1.0, low.windTunnelCtOverJ02(), 1.0e-12);
		assertEquals(1.0, low.windTunnelCpOverJ02(), 1.0e-12);
		assertTrue(low.windTunnelPositiveThrust());
		assertEquals(0.9432006010518407, low.currentThrustScale(), 1.0e-15);
		assertEquals(0.9746761652757989, low.currentPowerScale(), 1.0e-15);
		assertEquals(1.0333710179879627, low.currentTorquePerThrustScale(), 1.0e-15);

		HighAdvanceRotorCalibration.MejzlikWindTunnelPoint high = audit.highMeasuredPoint();
		assertEquals("wind_tunnel_j_0.6", high.pointId());
		assertEquals(0.6, high.advanceRatioJ(), 1.0e-12);
		assertEquals(0.1909859317102744, high.codeEquivalentProjectMu(), 1.0e-15);
		assertEquals(0.0411, high.windTunnelCt(), 1.0e-15);
		assertEquals(0.0321, high.windTunnelCp(), 1.0e-15);
		assertEquals(0.7745, high.windTunnelEfficiency(), 1.0e-15);
		assertEquals(0.44771241830065356, high.windTunnelCtOverJ02(), 1.0e-15);
		assertEquals(0.7697841726618704, high.windTunnelCpOverJ02(), 1.0e-15);
		assertEquals(0.27182441700960225, high.currentThrustScale(), 1.0e-15);
		assertEquals(0.45259375, high.currentPowerScale(), 1.0e-15);
		assertEquals(0.6071406686491846, high.currentThrustScaleOverPositiveWindTunnelCtRatio(), 1.0e-15);
		assertEquals(0.587948890186916, high.currentPowerScaleOverWindTunnelCpRatio(), 1.0e-15);

		HighAdvanceRotorCalibration.MejzlikWindTunnelPoint windmilling = audit.windmillingBoundaryPoint();
		assertEquals("wind_tunnel_j_0.8", windmilling.pointId());
		assertFalse(windmilling.windTunnelPositiveThrust());
		assertEquals(-0.0035, windmilling.windTunnelCt(), 1.0e-15);
		assertEquals(-0.03812636165577342, windmilling.windTunnelCtOverJ02(), 1.0e-15);
		assertEquals(0.12, windmilling.currentThrustScale(), 1.0e-15);
		assertEquals(0.2989999999999998, windmilling.currentPowerScale(), 1.0e-15);
		assertEquals(0.0, windmilling.currentThrustScaleOverPositiveWindTunnelCtRatio(), 1.0e-12);
	}

	@Test
	void tytoWindTunnelLeadAuditAddsArticleLevelForwardFlowConstraint() {
		HighAdvanceRotorCalibration.TytoWindTunnelLeadAudit audit =
				HighAdvanceRotorCalibration.audit(DroneConfig.racingQuad()).tytoWindTunnelLeadAudit();

		assertEquals("Tyto-Wind-Tunnel-Lead-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("article-level lead"));
		assertTrue(audit.articleMentions0To38Mph());
		assertTrue(audit.articleMentions0To17Mps());
		assertTrue(audit.articleMentionsFourThrottleSteps());
		assertTrue(audit.articleMentionsThrustDeclines75Percent());
		assertTrue(audit.articleMentionsPowerDeclines19Percent());
		assertEquals(9.0, audit.propellerDiameterInches(), 1.0e-12);
		assertEquals(2.1, audit.propellerDistanceFromWindshaperMeters(), 1.0e-12);
		assertEquals("2x2", audit.windshaperFanGrid());
		assertEquals(36, audit.windshaperFanCount());
		assertEquals(6, audit.airspeedConditionCount());
		assertEquals(4, audit.throttleStepCount());
		assertEquals(9000.0, audit.comparisonRpm(), 1.0e-12);
		assertTrue(audit.measuredFieldsIncludeThrustTorqueRpmCurrentVoltageAirspeed());
		assertEquals(17.0, audit.maxWindSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(38.027916964, audit.maxWindSpeedMilesPerHour(), 1.0e-12);
		assertEquals(0.495771361913, audit.tyto9InAdvanceRatioAtMaxWind9000Rpm(), 1.0e-12);
		assertEquals(0.157808925784, audit.tyto9InCodeMuAtMaxWind9000Rpm(), 1.0e-12);
		assertEquals(0.453467850036, audit.racing5InAdvanceRatioAt12p5MpsHoverRpm(), 1.0e-12);
		assertEquals(0.616716276049, audit.racing5InAdvanceRatioAtMaxWindHoverRpm(), 1.0e-12);
		assertEquals(0.196306887637, audit.racing5InCodeMuAtMaxWindHoverRpm(), 1.0e-12);
		assertEquals(0.275639964716, audit.racing5InAdvanceRatioAtMaxWindMaxRpm(), 1.0e-12);
		assertEquals(0.0877389257966, audit.racing5InCodeMuAtMaxWindMaxRpm(), 1.0e-12);
		assertEquals(0.25, audit.articleThrustRetentionAtMaxWind9000Rpm(), 1.0e-12);
		assertEquals(0.81, audit.articlePowerRetentionAtMaxWind9000Rpm(), 1.0e-12);
		assertEquals(0.308641975309, audit.articleThrustPowerRatioRetentionAtMaxWind9000Rpm(), 1.0e-12);
		assertFalse(audit.articleRawNumericTableAvailable());
		assertTrue(audit.needsFigureDigitizationOrRawExport());

		HighAdvanceRotorCalibration.TytoForwardFlowPoint article = audit.tytoArticle9000RpmMaxWindPoint();
		assertEquals("tyto_9in_17mps_9000rpm", article.pointId());
		assertEquals(17.0, article.windSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.495771361913, article.packetEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.157808925784, article.packetCodeEquivalentProjectMu(), 1.0e-12);
		assertEquals(0.15780892578371, article.currentRotorAdvanceRatio(), 1.0e-14);
		assertEquals(0.495771361913, article.currentEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.504823295015123, article.currentThrustScale(), 1.0e-15);
		assertEquals(0.650850210890329, article.currentPowerScale(), 1.0e-15);
		assertEquals(1.28926342606838, article.currentTorquePerThrustScale(), 1.0e-14);
		assertEquals(2.01929318006049, article.currentThrustScaleOverArticleRetention(), 1.0e-14);
		assertEquals(0.803518778876949, article.currentPowerScaleOverArticleRetention(), 1.0e-15);

		HighAdvanceRotorCalibration.TytoForwardFlowPoint hover17 = audit.racingHoverMaxWindPoint();
		assertEquals("racing_5in_17mps_hover_rpm", hover17.pointId());
		assertEquals(0.616716276049, hover17.packetEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.196306887637, hover17.packetCodeEquivalentProjectMu(), 1.0e-12);
		assertEquals(0.23043851928893, hover17.currentThrustScale(), 1.0e-15);
		assertEquals(0.429609030100437, hover17.currentPowerScale(), 1.0e-15);
		assertEquals(1.86431084276228, hover17.currentTorquePerThrustScale(), 1.0e-14);
		assertEquals(0.92175407715572, hover17.currentThrustScaleOverArticleRetention(), 1.0e-14);
		assertEquals(0.530381518642515, hover17.currentPowerScaleOverArticleRetention(), 1.0e-15);

		HighAdvanceRotorCalibration.TytoForwardFlowPoint maxRpm17 = audit.racingMaxRpmMaxWindPoint();
		assertEquals("racing_5in_17mps_max_rpm", maxRpm17.pointId());
		assertEquals(0.275639964716, maxRpm17.packetEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.0877389257966, maxRpm17.packetCodeEquivalentProjectMu(), 1.0e-12);
		assertEquals(0.883767021782819, maxRpm17.currentThrustScale(), 1.0e-15);
		assertEquals(0.955374350275526, maxRpm17.currentPowerScale(), 1.0e-15);
		assertEquals(1.08102511943504, maxRpm17.currentTorquePerThrustScale(), 1.0e-14);
		assertEquals(3.53506808713128, maxRpm17.currentThrustScaleOverArticleRetention(), 1.0e-14);
		assertEquals(1.179474506513, maxRpm17.currentPowerScaleOverArticleRetention(), 1.0e-12);
	}
}
