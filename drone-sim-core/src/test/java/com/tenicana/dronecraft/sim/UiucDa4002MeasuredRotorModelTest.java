package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiucDa4002MeasuredRotorModelTest {
	private static final double RHO = 1.225;
	private static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;

	@Test
	void reproducesAllStaticRowsAndSingleRunNominalTracksExactly() {
		assertStaticRows(
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75,
				UiucDa4002StaticPerformanceLookup.fiveByThreePointSevenFive()
		);
		assertStaticRows(
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
				UiucDa4002StaticPerformanceLookup.nineBySixPointSevenFive()
		);
		assertAdvanceRowsAtNominalRpm(
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75,
				UiucDa4002AdvancePerformanceLookup.fiveInchCurves(),
				new double[] { 4_000.0, 5_000.0, 6_000.0 }
		);
		assertAdvanceRowsAtNominalRpm(
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().subList(0, 2),
				new double[] { 2_000.0, 3_000.0 }
		);
	}

	@Test
	void overlappingTunnelSpeedRunsFormContinuousNominalTracks() {
		assertStitchedTrack(
				4_000.0,
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(2),
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(3)
		);
		assertStitchedTrack(
				5_000.0,
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(4),
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(5)
		);
	}

	@Test
	void bridgesStaticToFirstAdvanceThenInterpolatesOnlyBetweenRpmTracks() {
		UiucDa4002MeasuredRotorModel.LookupResult bridge =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75,
						0.10,
						4_000.0
				);
		assertEquals(UiucDa4002MeasuredRotorModel.InterpolationStatus
				.LINEAR_STATIC_TO_FIRST_ADVANCE, bridge.interpolationStatus());
		assertEquals(UiucDa4002MeasuredRotorModel.TrackInterpolationStatus
				.LINEAR_STATIC_TO_FIRST_ADVANCE, bridge.lowerTrackInterpolationStatus());
		assertEquals(0.0, bridge.lowerTrackLowerAdvanceRatioJ(), 0.0);
		assertEquals(0.289856, bridge.lowerTrackUpperAdvanceRatioJ(), 0.0);

		UiucDa4002AdvancePerformanceLookup.LookupResult lower =
				UiucDa4002AdvancePerformanceLookup.evaluate(
						UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(1),
						0.40,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		UiucDa4002AdvancePerformanceLookup.LookupResult upper =
				UiucDa4002AdvancePerformanceLookup.evaluate(
						UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(2),
						0.40,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		UiucDa4002MeasuredRotorModel.LookupResult surface =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						0.40,
						3_500.0
				);
		double rpmFraction = 0.5;
		assertEquals(UiucDa4002MeasuredRotorModel.InterpolationStatus
				.LINEAR_RPM_BETWEEN_TRACKS, surface.interpolationStatus());
		assertEquals("da4002-9x6.75-rpm3030", surface.lowerSourceCurveId());
		assertEquals("da4002-9x6.75-rpm4054", surface.upperSourceCurveId());
		assertEquals(3_000.0, surface.lowerRpm(), 0.0);
		assertEquals(4_000.0, surface.upperRpm(), 0.0);
		assertEquals(rpmFraction, surface.rpmInterpolationFraction(), 0.0);
		assertEquals(lerp(lower.thrustCoefficientCt(), upper.thrustCoefficientCt(), rpmFraction),
				surface.thrustCoefficientCt(), 1.0e-15);
		assertEquals(lerp(lower.powerCoefficientCp(), upper.powerCoefficientCp(), rpmFraction),
				surface.powerCoefficientCp(), 1.0e-15);
	}

	@Test
	void dimensionalSampleClosesCoefficientAndAxialMomentumDefinitions() {
		UiucDa4002MeasuredRotorModel.DimensionalSample sample =
				UiucDa4002MeasuredRotorModel.sample(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						0.40,
						3_500.0,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS
				);
		double n = 3_500.0 / 60.0;
		double diameter = UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS;
		double omega = 2.0 * Math.PI * n;

		assertEquals(UiucDa4002MeasuredRotorModel.PropulsiveRegime.AXIAL_POSITIVE_THRUST,
				sample.propulsiveRegime());
		assertTrue(sample.positiveThrustRuntimeEligible());
		assertEquals(sample.lookup().thrustCoefficientCt()
				* RHO * Math.pow(n, 2.0) * Math.pow(diameter, 4.0),
				sample.thrustNewtons(), 1.0e-14);
		assertEquals(sample.lookup().powerCoefficientCp()
				* RHO * Math.pow(n, 3.0) * Math.pow(diameter, 5.0),
				sample.shaftPowerWatts(), 1.0e-14);
		assertEquals(sample.shaftPowerWatts(),
				sample.shaftTorqueNewtonMeters() * omega, 1.0e-14);
		assertEquals(sample.thrustNewtons() / sample.diskAreaSquareMeters(),
				sample.signedDiskLoadingNewtonsPerSquareMeter(), 1.0e-14);
		assertEquals(sample.thrustNewtons() * sample.axialFreestreamVelocityMetersPerSecond(),
				sample.usefulPropulsivePowerWatts(), 1.0e-14);
		assertEquals(sample.usefulPropulsivePowerWatts() + sample.idealInducedPowerWatts(),
				sample.idealMomentumPowerWatts(), 1.0e-14);
		assertEquals(
				2.0 * RHO * sample.diskAreaSquareMeters()
						* sample.idealInducedVelocityMetersPerSecond()
						* (sample.axialFreestreamVelocityMetersPerSecond()
						+ sample.idealInducedVelocityMetersPerSecond()),
				sample.thrustNewtons(),
				1.0e-13
		);
	}

	@Test
	void measuredNegativeTailIsReferenceOnlyAndSurfaceGapsBlock() {
		UiucDa4002MeasuredRotorModel.DimensionalSample negative =
				UiucDa4002MeasuredRotorModel.sample(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						0.914534,
						5_000.0,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS
				);
		assertFalse(negative.blocked());
		assertEquals(UiucDa4002MeasuredRotorModel.PropulsiveRegime
				.MEASURED_NON_POSITIVE_THRUST, negative.propulsiveRegime());
		assertFalse(negative.positiveThrustRuntimeEligible());
		assertTrue(negative.thrustNewtons() < 0.0);
		assertTrue(negative.shaftPowerWatts() > 0.0);
		assertEquals(0.0, negative.idealInducedVelocityMetersPerSecond(), 0.0);
		assertEquals(0.0, negative.idealMomentumPowerWatts(), 0.0);

		UiucDa4002MeasuredRotorModel.LookupResult highAdvance =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						0.92,
						4_000.0
				);
		UiucDa4002MeasuredRotorModel.LookupResult lowRpm =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						0.40,
						1_000.0
				);
		UiucDa4002MeasuredRotorModel.LookupResult missingAdjacentFiveInchTrack =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75,
						0.855,
						5_500.0
				);
		UiucDa4002MeasuredRotorModel.LookupResult missingAdjacentNineInchTrack =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						0.88,
						4_500.0
				);
		assertTrue(highAdvance.blocked());
		assertTrue(lowRpm.blocked());
		assertTrue(missingAdjacentFiveInchTrack.blocked());
		assertTrue(missingAdjacentNineInchTrack.blocked());
		assertFalse(highAdvance.clamped());
		assertFalse(lowRpm.clamped());
		assertEquals(UiucDa4002MeasuredRotorModel.InterpolationStatus.BLOCKED,
				highAdvance.interpolationStatus());
	}

	private static void assertStaticRows(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			UiucDa4002StaticPerformanceLookup.StaticCurve curve
	) {
		for (UiucDa4002StaticPerformanceLookup.StaticRow row : curve.rows()) {
			UiucDa4002MeasuredRotorModel.LookupResult result =
					UiucDa4002MeasuredRotorModel.evaluate(propeller, 0.0, row.rpm());
			assertEquals(UiucDa4002MeasuredRotorModel.InterpolationStatus.STATIC_EXACT,
					result.interpolationStatus());
			assertEquals(row.thrustCoefficientCt(), result.thrustCoefficientCt(), 0.0);
			assertEquals(row.powerCoefficientCp(), result.powerCoefficientCp(), 0.0);
		}
	}

	private static void assertAdvanceRowsAtNominalRpm(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			java.util.List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> curves,
			double[] nominalRpms
	) {
		assertEquals(curves.size(), nominalRpms.length);
		for (int curveIndex = 0; curveIndex < curves.size(); curveIndex++) {
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve = curves.get(curveIndex);
			for (UiucDa4002AdvancePerformanceLookup.AdvanceRow row : curve.rows()) {
				UiucDa4002MeasuredRotorModel.LookupResult result =
						UiucDa4002MeasuredRotorModel.evaluate(
								propeller,
								row.advanceRatioJ(),
								nominalRpms[curveIndex]
						);
				assertEquals(UiucDa4002MeasuredRotorModel.InterpolationStatus.ADVANCE_EXACT,
						result.interpolationStatus());
				assertEquals(curve.id(), result.lowerSourceCurveId());
				assertEquals(row.thrustCoefficientCt(), result.thrustCoefficientCt(), 0.0);
				assertEquals(row.powerCoefficientCp(), result.powerCoefficientCp(), 0.0);
			}
		}
	}

	private static void assertStitchedTrack(
			double nominalRpm,
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve lowSpeedRun,
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve highSpeedRun
	) {
		double overlapLower = highSpeedRun.minimumAdvanceRatio();
		double overlapUpper = lowSpeedRun.maximumAdvanceRatio();
		double midpoint = 0.5 * (overlapLower + overlapUpper);
		UiucDa4002MeasuredRotorModel.LookupResult atMidpoint =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						midpoint,
						nominalRpm
				);
		assertEquals(UiucDa4002MeasuredRotorModel.InterpolationStatus
				.LINEAR_OVERLAPPING_RUNS, atMidpoint.interpolationStatus());
		assertTrue(atMidpoint.lowerSourceCurveId().contains(lowSpeedRun.id()));
		assertTrue(atMidpoint.lowerSourceCurveId().contains(highSpeedRun.id()));
		assertContinuousAtBoundary(nominalRpm, overlapLower);
		assertContinuousAtBoundary(nominalRpm, overlapUpper);
	}

	private static void assertContinuousAtBoundary(double nominalRpm, double advanceRatio) {
		double epsilon = 1.0e-9;
		UiucDa4002MeasuredRotorModel.LookupResult lower =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						advanceRatio - epsilon,
						nominalRpm
				);
		UiucDa4002MeasuredRotorModel.LookupResult upper =
				UiucDa4002MeasuredRotorModel.evaluate(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						advanceRatio + epsilon,
						nominalRpm
				);
		assertEquals(lower.thrustCoefficientCt(), upper.thrustCoefficientCt(), 1.0e-8);
		assertEquals(lower.powerCoefficientCp(), upper.powerCoefficientCp(), 1.0e-8);
	}

	private static double lerp(double lower, double upper, double fraction) {
		return lower + (upper - lower) * fraction;
	}
}
