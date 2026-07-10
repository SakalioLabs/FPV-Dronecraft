package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiucDa4002StaticPerformanceLookupTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;

	@Test
	void exactUiucAnchorProducesDimensionallyClosedStaticLoadsAndReynolds75() {
		UiucDa4002StaticPerformanceLookup.StaticCurve curve =
				UiucDa4002StaticPerformanceLookup.nineBySixPointSevenFive();
		UiucDa4002StaticPerformanceLookup.DimensionalSample sample =
				UiucDa4002StaticPerformanceLookup.sample(
						curve,
						5_943.333,
						RHO,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS,
						UiucDa4002StaticPerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(2, UiucDa4002StaticPerformanceLookup.curves().size());
		assertEquals(13, UiucDa4002StaticPerformanceLookup.fiveByThreePointSevenFive()
				.rows().size());
		assertEquals(19, curve.rows().size());
		assertEquals(UiucDa4002StaticPerformanceLookup.InterpolationStatus.EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(0.140450, sample.lookup().thrustCoefficientCt(), 0.0);
		assertEquals(0.080081, sample.lookup().powerCoefficientCp(), 0.0);
		assertFalse(sample.blocked());
		assertFalse(sample.clamped());

		double n = 5_943.333 / 60.0;
		double omega = n * 2.0 * Math.PI;
		double diameter = UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS;
		double radius = diameter * 0.5;
		double chord75 = curve.geometry().chordToRadiusAt(0.75) * radius;
		double speed75 = 0.75 * omega * radius;
		assertEquals(0.140450 * RHO * n * n * Math.pow(diameter, 4.0),
				sample.thrustNewtons(), 1.0e-12);
		assertEquals(0.080081 * RHO * Math.pow(n, 3.0) * Math.pow(diameter, 5.0),
				sample.shaftPowerWatts(), 1.0e-12);
		assertEquals(sample.shaftPowerWatts(),
				sample.shaftTorqueNewtonMeters() * omega, 1.0e-12);
		assertEquals(sample.lookup().powerCoefficientCp(),
				2.0 * Math.PI * sample.torqueCoefficientCq(), 1.0e-15);
		assertEquals(RHO * speed75 * chord75 / DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				sample.reynoldsNumberAtSeventyFivePercentRadius(), 1.0e-10);
		assertEquals(
				Math.pow(sample.lookup().thrustCoefficientCt(), 1.5)
						* Math.sqrt(2.0 / Math.PI)
						/ sample.lookup().powerCoefficientCp(),
				sample.hoverFigureOfMerit(),
				1.0e-15
		);
	}

	@Test
	void interpolationIsPiecewiseLinearAndEnvelopeBehaviorIsExplicit() {
		UiucDa4002StaticPerformanceLookup.StaticCurve curve =
				UiucDa4002StaticPerformanceLookup.nineBySixPointSevenFive();
		double midpointRpm = 0.5 * (3_940.000 + 4_213.333);
		UiucDa4002StaticPerformanceLookup.LookupResult midpoint =
				UiucDa4002StaticPerformanceLookup.evaluate(
						curve,
						midpointRpm,
						UiucDa4002StaticPerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		UiucDa4002StaticPerformanceLookup.LookupResult blocked =
				UiucDa4002StaticPerformanceLookup.evaluate(
						curve,
						1_000.0,
						UiucDa4002StaticPerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		UiucDa4002StaticPerformanceLookup.LookupResult clamped =
				UiucDa4002StaticPerformanceLookup.evaluate(
						curve,
						1_000.0,
						UiucDa4002StaticPerformanceLookup.EnvelopePolicy.CLAMP_TO_ENVELOPE
				);

		assertEquals(UiucDa4002StaticPerformanceLookup.InterpolationStatus.LINEAR_RPM,
				midpoint.interpolationStatus());
		assertEquals(0.5, midpoint.interpolationFraction(), 1.0e-15);
		assertEquals(0.5 * (0.134936 + 0.136319), midpoint.thrustCoefficientCt(), 1.0e-15);
		assertEquals(0.5 * (0.082488 + 0.079168), midpoint.powerCoefficientCp(), 1.0e-15);
		assertTrue(blocked.blocked());
		assertTrue(blocked.outOfEnvelope());
		assertFalse(blocked.clamped());
		assertEquals(0.0, blocked.thrustCoefficientCt(), 0.0);
		assertTrue(clamped.accepted());
		assertTrue(clamped.outOfEnvelope());
		assertTrue(clamped.clamped());
		assertEquals(1_546.667, clamped.effectiveRpm(), 0.0);
		assertEquals(0.125931, clamped.thrustCoefficientCt(), 0.0);
		assertEquals(0.088432, clamped.powerCoefficientCp(), 0.0);
	}
}
