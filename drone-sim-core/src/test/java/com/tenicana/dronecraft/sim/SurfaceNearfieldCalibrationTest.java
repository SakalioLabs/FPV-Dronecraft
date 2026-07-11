package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SurfaceNearfieldCalibrationTest {
	@Test
	void racingQuadAuditMatchesSurfaceNearfieldPacketAnchors() {
		SurfaceNearfieldCalibration.SurfaceNearfieldAudit audit =
				SurfaceNearfieldCalibration.audit(DroneConfig.racingQuad());

		assertEquals("Surface-Nearfield-Calibration-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("separate near-field paths"));
		assertEquals(708, audit.packetMetricRowCount());
		assertEquals(4, audit.sourceReferenceCount());
		assertEquals(120, audit.groundCeilingScanRowCount());
		assertEquals(160, audit.wallRuntimeMappingRowCount());
		assertEquals(240, audit.wallForceScanRowCount());
		assertEquals(150, audit.zjuGroundCheckRowCount());
		assertEquals(7, audit.zjuDragObservationRowCount());

		SurfaceNearfieldCalibration.GroundReferenceSample halfRadius = audit.halfRadiusGround();
		assertEquals(0.5, halfRadius.clearanceOverRadius(), 1.0e-12);
		assertEquals(0.03175, halfRadius.clearanceMeters(), 1.0e-12);
		assertEquals(1.222127994323132, halfRadius.currentGroundMultiplier(), 1.0e-15);
		assertEquals(1.3852885300014108, halfRadius.zjuGroundMultiplier(), 1.0e-15);
		assertEquals(0.5765237660262522, halfRadius.currentExtraOverZjuExtra(), 1.0e-15);
		assertEquals(1.3333333333333333, halfRadius.cheesemanGroundMultiplier(), 1.0e-15);
		assertTrue(halfRadius.currentCeilingMultiplier() < halfRadius.currentGroundMultiplier());

		SurfaceNearfieldCalibration.GroundReferenceSample oneRadius = audit.oneRadiusGround();
		assertEquals(1.0856401115902108, oneRadius.currentGroundMultiplier(), 1.0e-15);
		assertEquals(1.332498952304364, oneRadius.zjuGroundMultiplier(), 1.0e-15);
		assertEquals(1.0177876046158227, oneRadius.currentGroundOverCheeseman(), 1.0e-15);
		assertEquals(1.096081775587298, oneRadius.currentCeilingMultiplier(), 1.0e-12);
		assertEquals(1.0096179791863003, oneRadius.currentCeilingOverGround(), 1.0e-15);

		assertEquals(1.0127299026689103, audit.twoRadiusGround().currentGroundMultiplier(), 1.0e-15);
		assertEquals(1.0002812683077769, audit.fourRadiusGround().currentGroundMultiplier(), 1.0e-15);
	}

	@Test
	void wallAuditUsesRuntimeObstructionAndSideForcePathsSeparately() {
		SurfaceNearfieldCalibration.SurfaceNearfieldAudit audit =
				SurfaceNearfieldCalibration.audit(DroneConfig.racingQuad());

		SurfaceNearfieldCalibration.WallRuntimeMapping tangent = audit.tangentWall();
		assertEquals(0.5, tangent.diskSegmentBlockedFraction(), 1.0e-12);
		assertEquals(0.8511764705882353, tangent.runtimeObstruction(), 1.0e-15);
		assertEquals(RotorFlowObstructionModel.thrustMultiplier(tangent.runtimeObstruction()),
				tangent.affectedRotorThrustMultiplier(), 1.0e-15);
		assertEquals(0.030833926552004898, tangent.twoAffectedVehicleThrustLossFraction(), 1.0e-15);
		assertEquals(1.0, tangent.wallForceGeometryFactor(), 1.0e-15);
		assertEquals(0.07846708395155111, tangent.twoAffectedWallForceOverWeight(), 1.0e-15);

		SurfaceNearfieldCalibration.WallRuntimeMapping quarter = audit.quarterRadiusWall();
		assertEquals(0.34251882123714633, quarter.diskSegmentBlockedFraction(), 1.0e-15);
		assertEquals(0.7425770812496462, quarter.runtimeObstruction(), 1.0e-15);
		assertEquals(0.02047361949921278, quarter.twoAffectedVehicleThrustLossFraction(), 1.0e-15);
		assertEquals(0.8935973471085157, quarter.wallForceGeometryFactor(), 1.0e-15);
		assertEquals(0.05595408393075923, quarter.twoAffectedWallForceOverWeight(), 1.0e-15);

		SurfaceNearfieldCalibration.WallRuntimeMapping oneRadius = audit.oneRadiusWall();
		assertEquals(0.0, oneRadius.diskSegmentBlockedFraction(), 1.0e-12);
		assertEquals(0.4871041716485088, oneRadius.runtimeObstruction(), 1.0e-15);
		assertEquals(0.005778771885631362, oneRadius.twoAffectedVehicleThrustLossFraction(), 1.0e-15);
		assertEquals(0.6376281516217733, oneRadius.wallForceGeometryFactor(), 1.0e-15);
		assertEquals(0.020643123629206523, oneRadius.twoAffectedWallForceOverWeight(), 1.0e-15);

		SurfaceNearfieldCalibration.WallRuntimeMapping full = audit.fullObstructionWall();
		assertEquals(1.0, full.runtimeObstruction(), 1.0e-12);
		assertEquals(0.050000000000000044, full.twoAffectedVehicleThrustLossFraction(), 1.0e-15);
		assertEquals(1.0, full.wallForceGeometryFactor(), 1.0e-15);
		assertEquals(0.1032553403672804, full.twoAffectedWallForceOverWeight(), 1.0e-15);
	}

	@Test
	void wallForceAuditSeparatesHoverCushionFromTransverseSpeedWashout() {
		SurfaceNearfieldCalibration.SurfaceNearfieldAudit audit =
				SurfaceNearfieldCalibration.audit(DroneConfig.racingQuad());

		SurfaceNearfieldCalibration.WallForceSample hover = audit.fullObstructionHoverSideForce();
		assertEquals(1.0, hover.obstruction(), 1.0e-12);
		assertEquals(0.0, hover.speedMetersPerSecond(), 1.0e-12);
		assertEquals(1.0, hover.speedWashout(), 1.0e-12);
		assertEquals(0.21446817941013513, hover.wallCushion(), 1.0e-15);
		assertEquals(0.5569239409870347, hover.forcePerRotorNewtons(), 1.0e-15);
		assertEquals(0.1032553403672804, hover.twoRotorForceOverWeight(), 1.0e-15);
		assertEquals(0.2065106807345608, hover.fourRotorForceOverWeight(), 1.0e-15);

		SurfaceNearfieldCalibration.WallForceSample fast = audit.fullObstructionFastSideForce();
		assertEquals(12.0, fast.speedMetersPerSecond(), 1.0e-12);
		assertEquals(0.21999999999999997, fast.speedWashout(), 1.0e-15);
		assertEquals(0.04718299947022972, fast.wallCushion(), 1.0e-15);
		assertEquals(0.07786045822877247, fast.forcePerRotorNewtons(), 1.0e-15);
		assertEquals(0.01443555847377637, fast.twoRotorForceOverWeight(), 1.0e-15);
		assertTrue(fast.forcePerRotorNewtons() < hover.forcePerRotorNewtons() * 0.15);
	}

	@Test
	void zjuDragObservationKeepsDragDropSeparateFromSqrtThrustPrediction() {
		SurfaceNearfieldCalibration.ZjuDragObservation drag =
				SurfaceNearfieldCalibration.audit(DroneConfig.racingQuad()).zjuDragObservation();

		assertEquals(0.10, drag.lowHeightMeters(), 1.0e-12);
		assertEquals(2.0, drag.highHeightMeters(), 1.0e-12);
		assertEquals(0.9486, drag.predictedDragRatioFromSqrtThrust(), 1.0e-12);
		assertEquals(0.5963, drag.measuredDragXLowOverHigh(), 1.0e-12);
		assertEquals(0.6179, drag.measuredDragYLowOverHigh(), 1.0e-12);
		assertEquals(0.6286105840185537, drag.measuredXOverPredicted(), 1.0e-15);
		assertEquals(0.6513809825005271, drag.measuredYOverPredicted(), 1.0e-15);
		assertTrue(drag.measuredXOverPredicted() < 0.70);
	}

	@Test
	void jirsSurfaceEffectAuditAddsRawGroundCeilingWallAnchors() {
		DroneConfig config = DroneConfig.racingQuad();
		SurfaceNearfieldCalibration.SurfaceNearfieldAudit audit =
				SurfaceNearfieldCalibration.audit(config);
		SurfaceNearfieldCalibration.JirsSurfaceEffectAudit jirs =
				audit.jirsSurfaceEffectAudit();

		assertEquals("JIRS-2024-Surface-Effect-Packet", jirs.sourceId());
		assertEquals("10.1007/s10846-024-02155-7", jirs.doi());
		assertEquals("10.5281/zenodo.11384638", jirs.supplementDoi());
		assertTrue(jirs.caveat().contains("wall force/moment"));
		assertEquals(460164, jirs.supplementZipSizeBytes());
		assertEquals(18, jirs.supplementZipFileCount());
		assertEquals(9, jirs.supplementCsvFileCount());
		assertEquals(4, jirs.supplementMatFileCount());
		assertEquals(1, jirs.supplementPdfFileCount());
		assertEquals(225, jirs.numericMeasurementRowCount());
		assertEquals(40, jirs.uncertaintySummaryRowCount());

		SurfaceNearfieldCalibration.JirsSurfaceCurveFitAudit curveFit = jirs.curveFit();
		assertEquals("JIRS-2024-Surface-Curve-Fit-Packet", curveFit.sourceId());
		assertTrue(curveFit.model().contains("exp(-k * h_over_R)"));
		assertEquals(196, curveFit.packetRowCount());
		assertEquals(225, curveFit.inputMeasurementRowCount());
		assertEquals("ground", curveFit.groundFit().effect());
		assertEquals(0.576141774524, curveFit.groundFit().a(), 1.0e-12);
		assertEquals(1.9062, curveFit.groundFit().k(), 1.0e-12);
		assertEquals(0.980116586669, curveFit.groundFit().r2(), 1.0e-12);
		assertEquals(0.0159323160145, curveFit.groundFit().rmse(), 1.0e-12);
		assertEquals(0.0110665839276, curveFit.groundFit().mae(), 1.0e-12);
		assertEquals(10, curveFit.groundFit().sampleCount());
		assertEquals("ceiling", curveFit.ceilingFit().effect());
		assertEquals(0.384690708893, curveFit.ceilingFit().a(), 1.0e-12);
		assertEquals(1.38724, curveFit.ceilingFit().k(), 1.0e-12);
		assertEquals(0.951208172755, curveFit.ceilingFit().r2(), 1.0e-12);
		assertEquals(0.0194307382179, curveFit.ceilingFit().rmse(), 1.0e-12);
		assertEquals(0.0131115496255, curveFit.ceilingFit().mae(), 1.0e-12);
		assertEquals(10, curveFit.ceilingFit().sampleCount());
		assertEquals(1.222127994323132,
				SurfaceNearfieldCalibration.jirsGroundCurveFitMultiplier(0.5), 1.0e-15);
		assertEquals(1.096081775587298,
				SurfaceNearfieldCalibration.jirsCeilingCurveFitMultiplier(1.0), 1.0e-15);
		SurfaceNearfieldCalibration.JirsCurveFitRuntimeComparison[] runtimeComparisons =
				curveFit.runtimeComparisons();
		assertEquals(10, runtimeComparisons.length);
		assertEquals("ground", runtimeComparisons[0].effect());
		assertEquals(0.5, runtimeComparisons[0].clearanceOverRadius(), 1.0e-12);
		assertEquals(1.222127994323132, runtimeComparisons[0].runtimeMultiplier(), 1.0e-15);
		assertEquals(runtimeComparisons[0].fitMultiplier(),
				runtimeComparisons[0].runtimeMultiplier(), 1.0e-15);
		assertEquals(1.0, runtimeComparisons[0].runtimeOverFitMultiplier(), 1.0e-15);
		assertEquals("ceiling", runtimeComparisons[6].effect());
		assertEquals(1.0, runtimeComparisons[6].clearanceOverRadius(), 1.0e-12);
		assertEquals(1.096081775587298, runtimeComparisons[6].runtimeMultiplier(), 1.0e-15);
		assertEquals(1.0, runtimeComparisons[6].runtimeExtraOverFitExtra(), 1.0e-15);

		double rotorRadiusMeters = config.rotors().get(0).radiusMeters();
		SurfaceNearfieldCalibration.JirsThrustAnchor ground = jirs.ground();
		assertEquals("ground", ground.effect());
		assertEquals(40, ground.sampleCount());
		assertEquals(0.862999580105, ground.nearFzRatioMin(), 1.0e-12);
		assertEquals(1.02584338925, ground.nearFzRatioP50(), 1.0e-12);
		assertEquals(1.31490929705, ground.nearFzRatioMax(), 1.0e-12);
		assertEquals(0.328083989501, ground.closestDistanceOverRadiusMin(), 1.0e-12);
		assertEquals(0.393700787402, ground.closestDistanceOverRadiusMax(), 1.0e-12);
		assertEquals(1.29121270457, ground.closestFzRatioP50(), 1.0e-12);
		assertEquals(1.31490929705, ground.closestFzRatioMax(), 1.0e-12);
		assertEquals(100.0, ground.farBaselineDistanceCentimeters(), 1.0e-12);
		assertEquals(
				DroneEnvironment.groundEffectThrustMultiplier(
						config,
						rotorRadiusMeters * ground.closestDistanceOverRadiusMin()
				),
				ground.currentMultiplierAtClosestMin(),
				1.0e-15
		);
		assertEquals(
				DroneEnvironment.groundEffectThrustMultiplier(
						config,
						rotorRadiusMeters * ground.closestDistanceOverRadiusMax()
				),
				ground.currentMultiplierAtClosestMax(),
				1.0e-15
		);
		assertTrue(ground.currentClosestMinOverMeasuredP50() > 1.0);
		assertEquals(0.9673835011209799, ground.currentClosestMaxOverMeasuredMax(), 1.0e-15);

		SurfaceNearfieldCalibration.JirsThrustAnchor ceiling = jirs.ceiling();
		assertEquals("ceiling", ceiling.effect());
		assertEquals(40, ceiling.sampleCount());
		assertEquals(0.993352831694, ceiling.nearFzRatioMin(), 1.0e-12);
		assertEquals(1.04620491703, ceiling.nearFzRatioP50(), 1.0e-12);
		assertEquals(1.28879545278, ceiling.nearFzRatioMax(), 1.0e-12);
		assertEquals(1.22717806178, ceiling.closestFzRatioP50(), 1.0e-12);
		assertEquals(1.28879545278, ceiling.closestFzRatioMax(), 1.0e-12);
		assertEquals(
				DroneEnvironment.ceilingEffectThrustMultiplier(
						config,
						rotorRadiusMeters * ceiling.closestDistanceOverRadiusMin()
				),
				ceiling.currentMultiplierAtClosestMin(),
				1.0e-15
		);
		assertEquals(1.0137356995714861, ceiling.currentClosestMinOverMeasuredP50(), 1.0e-15);
		assertEquals(0.9487942451541636, ceiling.currentClosestMaxOverMeasuredMax(), 1.0e-15);

		SurfaceNearfieldCalibration.JirsWallAnchor wall = jirs.wall();
		assertEquals(145, wall.sampleCount());
		assertEquals(0.964566929134, wall.distanceOverRadiusMin(), 1.0e-12);
		assertEquals(3.0, wall.distanceOverRadiusMax(), 1.0e-12);
		assertEquals(0.09864, wall.absForceP50Newtons(), 1.0e-12);
		assertEquals(0.42777, wall.absForceMaxNewtons(), 1.0e-12);
		assertEquals(-0.42777, wall.signedForceMinNewtons(), 1.0e-12);
		assertEquals(0.24607, wall.signedForceMaxNewtons(), 1.0e-12);
		assertEquals(0.027677, wall.absMomentP50NewtonMeters(), 1.0e-12);
		assertEquals(0.11947, wall.absMomentMaxNewtonMeters(), 1.0e-12);
		assertEquals("WallEffect_13_DU2SRI.csv", wall.strongestForceSource());
		assertEquals(1544, wall.strongestForcePwm());
		assertEquals(1.75003028468, wall.strongestForceDistanceOverRadius(), 1.0e-12);
		assertEquals("WallEffect_12_txc.csv", wall.strongestMomentSource());
		assertEquals(1719, wall.strongestMomentPwm());
		assertEquals(1.0, wall.strongestMomentDistanceOverRadius(), 1.0e-12);
		assertEquals(0.0389109547857, wall.terraXcubeWallForceUncertaintyP50Newtons(), 1.0e-12);
		assertEquals(1.1100511642, wall.du2sriWallForceUncertaintyP50Newtons(), 1.0e-12);
		assertEquals(0.00687397096237, wall.terraXcubeWallMomentUncertaintyP50NewtonMeters(), 1.0e-12);
		assertEquals(0.0560048097306, wall.du2sriWallMomentUncertaintyP50NewtonMeters(), 1.0e-12);
		assertEquals(1.1100511642 / 0.0389109547857,
				wall.du2sriOverTerraXcubeWallForceUncertaintyP50(), 1.0e-12);
		assertEquals(audit.oneRadiusWall().twoAffectedWallForceOverWeight(),
				wall.runtimeOneRadiusTwoRotorForceOverWeight(), 1.0e-15);
		assertEquals(audit.fullObstructionHoverSideForce().forcePerRotorNewtons(),
				wall.runtimeFullObstructionHoverForcePerRotorNewtons(), 1.0e-15);
		assertEquals(audit.fullObstructionWall().twoAffectedWallForceOverWeight(),
				wall.runtimeFullObstructionTwoRotorForceOverWeight(), 1.0e-15);
		assertTrue(wall.du2sriOverTerraXcubeWallForceUncertaintyP50() > 28.0);
		assertTrue(wall.runtimeOneRadiusWallForcePerRotorNewtons() > wall.absForceP50Newtons());
		assertTrue(wall.runtimeOneRadiusWallForcePerRotorNewtons() < wall.absForceMaxNewtons());
		assertTrue(wall.runtimeFullObstructionHoverForcePerRotorNewtons() > wall.absForceMaxNewtons());
	}

	@Test
	void partialSurfaceLeadAuditMatchesCaiOlAbstractThresholds() {
		SurfaceNearfieldCalibration.PartialSurfaceLeadAudit audit =
				SurfaceNearfieldCalibration.audit(DroneConfig.racingQuad()).partialSurfaceLeadAudit();

		assertEquals("Partial-Surface-Effect-Lead-Packet", audit.sourceId());
		assertEquals("10.2514/1.C036974", audit.doi());
		assertTrue(audit.caveat().contains("Abstract-level"));
		assertEquals(0, audit.publicBundleCount());
		assertEquals(1411, audit.abstractCharacterCount());
		assertTrue(audit.mentionsPartialGround());
		assertTrue(audit.mentionsPartialCeiling());
		assertTrue(audit.mentionsCircularAndAnnularPlates());
		assertTrue(audit.mentionsForceBalance());
		assertTrue(audit.mentionsPlateEqualPropDiameter());
		assertTrue(audit.mentionsLessThanHalfPropDiameter());
		assertTrue(audit.mentionsSuperimposedCeiling());
		assertTrue(audit.mentionsCurveFitWithinSixPercent());
		assertEquals(0.5, audit.negligiblePlateDiameterOverPropDiameter(), 1.0e-12);
		assertEquals(1.0, audit.fullLikePlateDiameterOverPropDiameter(), 1.0e-12);
		assertEquals(0.25, audit.negligiblePlateAreaOverDiskArea(), 1.0e-12);
		assertEquals(0.06, audit.curveFitRelativeAccuracy(), 1.0e-12);
		assertEquals(0.127, audit.propellerDiameterMeters(), 1.0e-12);
		assertEquals(0.0635, audit.negligiblePatchDiameterMeters(), 1.0e-12);
		assertEquals(0.127, audit.fullLikePatchDiameterMeters(), 1.0e-12);
		assertEquals(7.874015748031496, audit.minecraftBlockWidthOverPropDiameter(), 1.0e-12);

		SurfaceNearfieldCalibration.PartialSurfaceGateSample half = audit.halfDiameterPatch();
		assertEquals(0.5, half.plateDiameterOverPropDiameter(), 1.0e-12);
		assertEquals(0.25, half.circularPatchAreaOverPropDiskArea(), 1.0e-12);
		assertEquals(0.0, half.gate(), 1.0e-12);
		assertEquals(1.0, half.gatedGroundMultiplier(), 1.0e-12);
		assertEquals(1.0, half.gatedCeilingMultiplier(), 1.0e-12);

		SurfaceNearfieldCalibration.PartialSurfaceGateSample threeQuarter =
				audit.threeQuarterDiameterPatch();
		assertEquals(0.75, threeQuarter.plateDiameterOverPropDiameter(), 1.0e-12);
		assertEquals(0.5625, threeQuarter.circularPatchAreaOverPropDiskArea(), 1.0e-12);
		assertEquals(0.5, threeQuarter.gate(), 1.0e-12);
		assertEquals(1.0856401115902108, threeQuarter.fullGroundMultiplier(), 1.0e-15);
		assertEquals(1.0428200557951053, threeQuarter.gatedGroundMultiplier(), 1.0e-15);
		assertEquals(1.096081775587298, threeQuarter.fullCeilingMultiplier(), 1.0e-12);
		assertEquals(1.0480408877936491, threeQuarter.gatedCeilingMultiplier(), 1.0e-12);

		assertEquals(1.0, audit.fullDiameterPatch().gate(), 1.0e-12);
		assertEquals(audit.fullDiameterPatch().fullGroundMultiplier(),
				audit.fullDiameterPatch().gatedGroundMultiplier(), 1.0e-12);
		assertEquals(1.0, audit.minecraftBlockPatch().gate(), 1.0e-12);
		assertTrue(audit.minecraftBlockPatch().plateDiameterOverPropDiameter() > 7.8);
	}

	@Test
	void surfaceNearfieldAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> SurfaceNearfieldCalibration.audit(null));
	}
}
