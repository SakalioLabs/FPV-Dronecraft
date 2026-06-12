package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoaxialAllocationCalibrationTest {
	@Test
	void coaxialAllocationAuditKeepsPacketInventoryAndReferenceRig() {
		CoaxialAllocationCalibration.CoaxialAllocationAudit audit =
				CoaxialAllocationCalibration.audit(DroneConfig.coaxialX8());

		assertEquals("Coaxial-Allocation-Calibration-Packet", audit.sourceId());
		assertEquals("allocation-not-thrust-loss-fit", audit.caveat());
		assertEquals(835, audit.rowTypeCounts().totalRowCount());
		assertEquals(36, audit.rowTypeCounts().elevenInZOverD07AllocationRowCount());
		assertEquals(40, audit.rowTypeCounts().commandEnvelope60PercentRowCount());
		assertEquals(4, audit.rowTypeCounts().currentGeometryRowCount());
		assertEquals(288, audit.rowTypeCounts().currentWakeScanRowCount());
		assertEquals(1, audit.rowTypeCounts().methodRowCount());
		assertEquals(180, audit.rowTypeCounts().multiOptimal60PercentRowCount());
		assertEquals(40, audit.rowTypeCounts().multiShape60PercentRowCount());
		assertEquals(1, audit.rowTypeCounts().referenceAllocationClaimRowCount());
		assertEquals(5, audit.rowTypeCounts().referencePlatformRowCount());
		assertEquals(12, audit.rowTypeCounts().referenceRegionRowCount());
		assertEquals(5, audit.rowTypeCounts().referenceSpacingScanRowCount());
		assertEquals(100, audit.rowTypeCounts().runtimeModelPointRowCount());
		assertEquals(44, audit.rowTypeCounts().runtimeRawGroup60PercentRowCount());
		assertEquals(31, audit.rowTypeCounts().summaryRowCount());
		assertEquals(48, audit.rowTypeCounts().surfaceFitSummaryRowCount());

		CoaxialAllocationCalibration.ReferencePlatformAudit platform = audit.referencePlatform();
		assertEquals("New Dexterity Coaxial Benchmarking Platform", platform.platform());
		assertEquals(5.0, platform.thrustLoadCellCapacityKgf(), 1.0e-12);
		assertEquals(2.5, platform.thrustPrecisionGf(), 1.0e-12);
		assertEquals(1.4715, platform.torqueCapacityNewtonMeters(), 1.0e-12);
		assertEquals(0.00073575, platform.torquePrecisionNewtonMeters(), 1.0e-15);
		assertEquals("thrust, torque, RPM, voltage, current", platform.measuredChannels());

		CoaxialAllocationCalibration.ReferenceSpacingAudit spacing = audit.referenceSpacing();
		assertEquals(0.1, spacing.zOverDMin(), 1.0e-15);
		assertEquals(1.0, spacing.zOverDMax(), 1.0e-15);
		assertEquals(7, spacing.spacingPointCount());
		assertEquals(100, spacing.commandGridPointsPerSpacing());
		assertEquals(700, spacing.pointsPerRotorSet());
	}

	@Test
	void coaxialX8GeometryAndWakeLossMatchPacketSummary() {
		CoaxialAllocationCalibration.CoaxialAllocationAudit audit =
				CoaxialAllocationCalibration.audit(DroneConfig.coaxialX8());

		CoaxialAllocationCalibration.ReferenceRegionAudit region = audit.referenceRegion();
		assertEquals(0.25, region.localEfficiencyMax1ZOverDMin(), 1.0e-15);
		assertEquals(0.40, region.localEfficiencyMax1ZOverDMax(), 1.0e-15);
		assertEquals(0.55, region.localEfficiencyMinimumZOverDCenter(), 1.0e-15);
		assertEquals(0.70, region.localEfficiencyMax2ZOverDMin(), 1.0e-15);
		assertEquals(0.85, region.localEfficiencyMax2ZOverDMax(), 1.0e-15);
		assertTrue(region.currentSpacingInSecondMaxRegion());

		CoaxialAllocationCalibration.CurrentGeometryAudit geometry = audit.currentGeometry();
		assertEquals(8, geometry.rotorCount());
		assertEquals(4, geometry.coaxialPairCount());
		assertEquals(0.115, geometry.radiusMeters(), 1.0e-15);
		assertEquals(0.1656, geometry.upperLowerSeparationMeters(), 1.0e-15);
		assertEquals(1.44, geometry.separationOverRadius(), 1.0e-15);
		assertEquals(0.72, geometry.separationOverDiameter(), 1.0e-15);
		assertTrue(geometry.matchesPacketGeometry());

		CoaxialAllocationCalibration.WakeLossAudit wake = audit.wakeLoss();
		assertEquals(6.99866242649, wake.hoverWakeLossZOverD072Percent(), 1.0e-12);
		assertEquals(19.0310654545, wake.maxWakeLossZOverD072Percent(), 1.0e-10);
		assertEquals(0.120965473507, wake.hoverWakeLossZOverD055MinusZOverD072Percent(), 1.0e-15);
		assertEquals(0.328934545455, wake.maxWakeLossZOverD055MinusZOverD072Percent(), 1.0e-15);
	}

	@Test
	void runtimeAllocationPriorMatchesCoaxialCommandMapPacket() {
		CoaxialAllocationCalibration.CoaxialAllocationAudit audit =
				CoaxialAllocationCalibration.audit(DroneConfig.coaxialX8());
		CoaxialAllocationCalibration.RuntimeAllocationAudit runtime = audit.runtimeAllocation();

		assertEquals(0.72, runtime.lookupZOverD(), 1.0e-15);
		assertEquals(0.60, runtime.targetLoadFraction(), 1.0e-15);
		assertEquals(1074.55702959, runtime.referenceTargetTotalThrustGrams(), 1.0e-8);
		assertEquals(1.32751117604, runtime.recommendedPwmRatioRightOverLeft(), 1.0e-11);
		assertEquals(0.827324794979, runtime.recommendedLeftPwmScaleVsEqual(), 1.0e-12);
		assertEquals(1.09514567019, runtime.recommendedRightPwmScaleVsEqual(), 1.0e-11);
		assertEquals(4.40500605811, runtime.mechanicalGainOverEqualPercent(), 1.0e-11);
		assertEquals(2.84225441168, runtime.electricalGainOverEqualPercent(), 1.0e-11);
		assertEquals(0.115, runtime.loadBiasTarget(), 1.0e-15);
		assertEquals(8.60841764401816, runtime.allocationUncertaintyPercent(), 1.0e-14);
		assertEquals(9.05511811024, runtime.currentCoaxialDiameterInches(), 1.0e-11);
		assertEquals(11.0, runtime.nearestReferencePropDiameterInches(), 1.0e-15);
		assertEquals(4, runtime.groupSampleCount());

		assertEquals(1.2176229629, runtime.allGroupRatioP10(), 1.0e-12);
		assertEquals(1.38426721762, runtime.allGroupRatioMedian(), 1.0e-11);
		assertEquals(1.44700529172, runtime.allGroupRatioP90(), 1.0e-11);
		assertEquals(2.43665922622, runtime.allGroupMechanicalGainP10Percent(), 1.0e-11);
		assertEquals(3.69175588554, runtime.allGroupMechanicalGainMedianPercent(), 1.0e-11);
		assertEquals(6.14026556739, runtime.allGroupMechanicalGainP90Percent(), 1.0e-11);
		assertEquals(-1.06207834665, runtime.allGroupElectricalGainP10Percent(), 1.0e-11);
		assertEquals(1.88071159644, runtime.allGroupElectricalGainMedianPercent(), 1.0e-11);
		assertEquals(3.29569222562, runtime.allGroupElectricalGainP90Percent(), 1.0e-11);
	}

	@Test
	void commandMapHelpersMirrorRuntimeShape() {
		assertEquals(1.3275111760369225,
				CoaxialAllocationCalibration.commandMapAllocationRatio(0.72, 0.60),
				1.0e-15);
		assertEquals(4.405006058107876,
				CoaxialAllocationCalibration.commandMapMechanicalGainPercent(0.72, 0.60),
				1.0e-15);
		assertEquals(2.842254411676719,
				CoaxialAllocationCalibration.commandMapElectricalGainPercent(0.72, 0.60),
				1.0e-15);
		assertEquals(0.115,
				CoaxialAllocationCalibration.commandMapLoadBias(0.72, 0.60),
				1.0e-15);
		assertEquals(8.60841764401816,
				CoaxialAllocationCalibration.commandMapAllocationUncertaintyPercent(0.72, 0.60),
				1.0e-14);
		assertEquals(1.0,
				CoaxialAllocationCalibration.commandMapAllocationRatio(0.10, 0.60),
				1.0e-15);
		assertEquals(0.0,
				CoaxialAllocationCalibration.commandMapMechanicalGainPercent(0.72, 0.05),
				1.0e-15);
	}

	@Test
	void benchmarkAllocationAndSurfaceFitSummariesStayVisible() {
		CoaxialAllocationCalibration.CoaxialAllocationAudit audit =
				CoaxialAllocationCalibration.audit(DroneConfig.coaxialX8());

		CoaxialAllocationCalibration.BenchmarkAllocationAudit benchmark = audit.benchmarkAllocation();
		assertEquals(1.32105488559, benchmark.elevenInZOverD070RatioAt1000g(), 1.0e-11);
		assertEquals(5.10391814153, benchmark.elevenInZOverD070MechanicalGainAt1000gPercent(), 1.0e-11);
		assertEquals(1.18825381257, benchmark.elevenInZOverD070RatioAt1500g(), 1.0e-11);
		assertEquals(6.55695305946, benchmark.elevenInZOverD070MechanicalGainAt1500gPercent(), 1.0e-11);
		assertEquals(11.0, benchmark.allocationClaimMechanicalGainPercent(), 1.0e-12);

		CoaxialAllocationCalibration.StrongestAllocationAudit strongest = audit.strongestAllocation();
		assertEquals("22.0in 240kv 25V MN501S T zD 0.40 optimal vs equal 0.60",
				strongest.strongestMultiOptimal60PercentName());
		assertEquals(11.5971168978, strongest.strongestMultiOptimal60PercentMechanicalGainPercent(), 1.0e-10);
		assertEquals(1.16820448818, strongest.strongestMultiOptimal60PercentPwmRatioRightOverLeft(), 1.0e-11);
		assertEquals("22.0in 240kv 25V MN501S T command envelope 0.60",
				strongest.strongestCommandEnvelope60PercentName());
		assertEquals(9.42018897016, strongest.strongestCommandEnvelope60PercentMechanicalGainPercent(), 1.0e-11);
		assertEquals(1.4635645296, strongest.strongestCommandEnvelope60PercentPwmRatioRightOverLeft(), 1.0e-11);
		assertEquals(-0.0119293498523, strongest.strongestCommandEnvelope60PercentElectricalGainPercent(), 1.0e-15);

		CoaxialAllocationCalibration.SurfaceFitAudit surfaceFit = audit.surfaceFit();
		assertEquals(1.07171693717, surfaceFit.thrustMedianCvRmseOverRangePercent(), 1.0e-11);
		assertEquals(0.997941367477, surfaceFit.thrustMedianCvR2(), 1.0e-12);
		assertEquals(1.24381747572, surfaceFit.mechanicalPowerMedianCvRmseOverRangePercent(), 1.0e-11);
		assertEquals(0.997209102716, surfaceFit.mechanicalPowerMedianCvR2(), 1.0e-12);
	}

	@Test
	void coaxialAllocationAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> CoaxialAllocationCalibration.audit(null));
	}
}
