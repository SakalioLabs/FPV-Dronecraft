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
		assertEquals(1.3852885300014108, halfRadius.currentGroundMultiplier(), 1.0e-15);
		assertEquals(halfRadius.zjuGroundMultiplier(), halfRadius.currentGroundMultiplier(), 1.0e-15);
		assertEquals(1.0, halfRadius.currentExtraOverZjuExtra(), 1.0e-15);
		assertEquals(1.3333333333333333, halfRadius.cheesemanGroundMultiplier(), 1.0e-15);
		assertTrue(halfRadius.currentCeilingMultiplier() < halfRadius.currentGroundMultiplier());

		SurfaceNearfieldCalibration.GroundReferenceSample oneRadius = audit.oneRadiusGround();
		assertEquals(1.332498952304364, oneRadius.currentGroundMultiplier(), 1.0e-15);
		assertEquals(1.332498952304364, oneRadius.zjuGroundMultiplier(), 1.0e-15);
		assertEquals(1.2492177677853413, oneRadius.currentGroundOverCheeseman(), 1.0e-15);
		assertEquals(1.11310774, oneRadius.currentCeilingMultiplier(), 1.0e-12);
		assertEquals(0.835353557370564, oneRadius.currentCeilingOverGround(), 1.0e-15);

		assertEquals(1.2147853317334425, audit.twoRadiusGround().currentGroundMultiplier(), 1.0e-15);
		assertEquals(1.0888972333930909, audit.fourRadiusGround().currentGroundMultiplier(), 1.0e-15);
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
		assertEquals(0.07846708395155111, tangent.twoAffectedWallForceOverWeight(), 1.0e-15);

		SurfaceNearfieldCalibration.WallRuntimeMapping quarter = audit.quarterRadiusWall();
		assertEquals(0.34251882123714633, quarter.diskSegmentBlockedFraction(), 1.0e-15);
		assertEquals(0.7425770812496462, quarter.runtimeObstruction(), 1.0e-15);
		assertEquals(0.02047361949921278, quarter.twoAffectedVehicleThrustLossFraction(), 1.0e-15);
		assertEquals(0.06261666298788188, quarter.twoAffectedWallForceOverWeight(), 1.0e-15);

		SurfaceNearfieldCalibration.WallRuntimeMapping oneRadius = audit.oneRadiusWall();
		assertEquals(0.0, oneRadius.diskSegmentBlockedFraction(), 1.0e-12);
		assertEquals(0.4871041716485088, oneRadius.runtimeObstruction(), 1.0e-15);
		assertEquals(0.005778771885631362, oneRadius.twoAffectedVehicleThrustLossFraction(), 1.0e-15);
		assertEquals(0.03237486233426463, oneRadius.twoAffectedWallForceOverWeight(), 1.0e-15);

		SurfaceNearfieldCalibration.WallRuntimeMapping full = audit.fullObstructionWall();
		assertEquals(1.0, full.runtimeObstruction(), 1.0e-12);
		assertEquals(0.050000000000000044, full.twoAffectedVehicleThrustLossFraction(), 1.0e-15);
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
		assertEquals(1.332498952304364, threeQuarter.fullGroundMultiplier(), 1.0e-15);
		assertEquals(1.166249476152182, threeQuarter.gatedGroundMultiplier(), 1.0e-15);
		assertEquals(1.11310774, threeQuarter.fullCeilingMultiplier(), 1.0e-12);
		assertEquals(1.05655387, threeQuarter.gatedCeilingMultiplier(), 1.0e-12);

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
