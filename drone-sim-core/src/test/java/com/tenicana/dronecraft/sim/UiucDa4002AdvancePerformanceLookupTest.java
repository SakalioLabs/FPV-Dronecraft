package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiucDa4002AdvancePerformanceLookupTest {
	private static final double RHO = 1.225;
	private static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;

	@Test
	void loadsAllNineReviewedUiucAdvanceCurves() {
		assertEquals(3, UiucDa4002AdvancePerformanceLookup.fiveInchCurves().size());
		assertEquals(6, UiucDa4002AdvancePerformanceLookup.nineInchCurves().size());
		assertEquals(112, UiucDa4002AdvancePerformanceLookup.curves().stream()
				.mapToInt(curve -> curve.rows().size())
				.sum());
	}

	@Test
	void exactAndMidpointQueriesPreserveCoefficientClosure() {
		UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve =
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(0);
		UiucDa4002AdvancePerformanceLookup.LookupResult exact =
				UiucDa4002AdvancePerformanceLookup.evaluate(
						curve,
						0.315488,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(UiucDa4002AdvancePerformanceLookup.InterpolationStatus.EXACT,
				exact.interpolationStatus());
		assertEquals(0.103248, exact.thrustCoefficientCt(), 0.0);
		assertEquals(0.073956, exact.powerCoefficientCp(), 0.0);
		assertEquals(0.440443, exact.propulsiveEfficiencyEta(), 0.0);
		assertTrue(Math.abs(exact.etaClosureResidual()) < 5.0e-6);

		double midpointJ = (0.315488 + 0.392566) * 0.5;
		UiucDa4002AdvancePerformanceLookup.LookupResult midpoint =
				UiucDa4002AdvancePerformanceLookup.evaluate(
						curve,
						midpointJ,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		assertEquals(UiucDa4002AdvancePerformanceLookup.InterpolationStatus
				.LINEAR_ADVANCE_RATIO, midpoint.interpolationStatus());
		assertEquals(0.5, midpoint.interpolationFraction(), 1.0e-15);
		assertEquals((0.103248 + 0.095053) * 0.5,
				midpoint.thrustCoefficientCt(), 1.0e-15);
		assertEquals((0.073956 + 0.071617) * 0.5,
				midpoint.powerCoefficientCp(), 1.0e-15);
		assertEquals(midpoint.thrustCoefficientCt() * midpointJ
				/ midpoint.powerCoefficientCp(), midpoint.propulsiveEfficiencyEta(), 1.0e-15);
		assertEquals(0.0, midpoint.etaClosureResidual(), 1.0e-15);
	}

	@Test
	void outOfEnvelopeQueriesAreBlockedOrExplicitlyClamped() {
		UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve =
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(0);
		UiucDa4002AdvancePerformanceLookup.LookupResult blocked =
				UiucDa4002AdvancePerformanceLookup.evaluate(
						curve,
						0.0,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		UiucDa4002AdvancePerformanceLookup.LookupResult clamped =
				UiucDa4002AdvancePerformanceLookup.evaluate(
						curve,
						0.0,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.CLAMP_TO_ENVELOPE
				);

		assertTrue(blocked.blocked());
		assertTrue(blocked.outOfEnvelope());
		assertFalse(blocked.clamped());
		assertEquals(UiucDa4002AdvancePerformanceLookup.InterpolationStatus.BLOCKED,
				blocked.interpolationStatus());
		assertEquals(0.0, blocked.thrustCoefficientCt(), 0.0);
		assertFalse(clamped.blocked());
		assertTrue(clamped.outOfEnvelope());
		assertTrue(clamped.clamped());
		assertEquals(curve.minimumAdvanceRatio(), clamped.effectiveAdvanceRatioJ(), 0.0);
		assertEquals(curve.rows().get(0).thrustCoefficientCt(),
				clamped.thrustCoefficientCt(), 0.0);
	}

	@Test
	void dimensionalSampleUsesCtCpDefinitionsAndResultantSectionSpeed() {
		UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve =
				UiucDa4002AdvancePerformanceLookup.nineInchCurves().get(0);
		UiucDa4002AdvancePerformanceLookup.DimensionalSample sample =
				UiucDa4002AdvancePerformanceLookup.sample(
						curve,
						0.543643,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		double revolutionsPerSecond = curve.rpm() / 60.0;
		double diameter = curve.referenceDiameterMeters();
		double radius = diameter * 0.5;
		double omega = 2.0 * Math.PI * revolutionsPerSecond;
		double axialVelocity = 0.543643 * revolutionsPerSecond * diameter;
		double relativeSpeed75 = Math.hypot(0.75 * omega * radius, axialVelocity);
		double rotationalSpeed75 = 0.75 * omega * radius;
		double chord75 = curve.geometry().chordToRadiusAt(0.75) * radius;

		assertFalse(sample.blocked());
		assertEquals(sample.lookup().thrustCoefficientCt()
				* RHO * Math.pow(revolutionsPerSecond, 2.0) * Math.pow(diameter, 4.0),
				sample.thrustNewtons(), 1.0e-15);
		assertEquals(sample.lookup().powerCoefficientCp()
				* RHO * Math.pow(revolutionsPerSecond, 3.0) * Math.pow(diameter, 5.0),
				sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts(),
				sample.shaftTorqueNewtonMeters() * omega, 1.0e-15);
		assertEquals(sample.thrustNewtons() * axialVelocity,
				sample.usefulPropulsivePowerWatts(), 1.0e-15);
		assertEquals(sample.lookup().propulsiveEfficiencyEta(),
				sample.coefficientDerivedEfficiencyEta(), 1.0e-5);
		assertEquals(RHO * rotationalSpeed75 * chord75 / DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				sample.reynoldsNumberAtSeventyFivePercentRadius(), 1.0e-10);
		assertEquals(RHO * relativeSpeed75 * chord75 / DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				sample.resultantSectionReynoldsNumberAtSeventyFivePercentRadius(), 1.0e-10);
	}
}
