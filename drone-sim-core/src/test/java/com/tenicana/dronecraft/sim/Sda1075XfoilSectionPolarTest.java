package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Sda1075XfoilSectionPolarTest {
	@Test
	void returnsExactXfoilAnchorsWithoutChangingTheirUnits() {
		Sda1075XfoilSectionPolar.PolarSample lowRe =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						40_000.0,
						5.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		Sda1075XfoilSectionPolar.PolarSample highRe =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						100_000.0,
						12.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(Sda1075XfoilSectionPolar.InterpolationStatus.EXACT,
				lowRe.interpolationStatus());
		assertEquals(0.6896, lowRe.liftCoefficientCl(), 0.0);
		assertEquals(0.04565, lowRe.dragCoefficientCd(), 0.0);
		assertEquals(-0.0569, lowRe.pitchingMomentCoefficientCm(), 0.0);
		assertEquals(1.2452, highRe.liftCoefficientCl(), 0.0);
		assertEquals(0.04361, highRe.dragCoefficientCd(), 0.0);
		assertEquals(-0.0028, highRe.pitchingMomentCoefficientCm(), 0.0);
		assertFalse(lowRe.blocked());
		assertFalse(highRe.clamped());
	}

	@Test
	void bilinearInterpolationUsesNeighboringReynoldsAndAngleRows() {
		Sda1075XfoilSectionPolar.PolarSample sample =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						50_000.0,
						5.5,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(Sda1075XfoilSectionPolar.InterpolationStatus.BILINEAR,
				sample.interpolationStatus());
		assertEquals(0.5, sample.reynoldsInterpolationFraction(), 0.0);
		assertEquals(0.5, sample.angleInterpolationFraction(), 0.0);
		assertEquals(0.76385, sample.liftCoefficientCl(), 1.0e-15);
		assertEquals(0.03880, sample.dragCoefficientCd(), 1.0e-15);
		assertEquals(-0.047775, sample.pitchingMomentCoefficientCm(), 1.0e-15);
	}

	@Test
	void outOfEnvelopeQueryMustExplicitlyBlockOrClamp() {
		Sda1075XfoilSectionPolar.PolarSample blocked =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						20_000.0,
						20.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		Sda1075XfoilSectionPolar.PolarSample clamped =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						20_000.0,
						20.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
				);

		assertTrue(blocked.blocked());
		assertEquals(Sda1075XfoilSectionPolar.InterpolationStatus.BLOCKED,
				blocked.interpolationStatus());
		assertTrue(blocked.reynoldsOutOfEnvelope());
		assertTrue(blocked.angleOfAttackOutOfEnvelope());
		assertFalse(blocked.clamped());
		assertTrue(clamped.accepted());
		assertTrue(clamped.reynoldsClamped());
		assertTrue(clamped.angleOfAttackClamped());
		assertEquals(40_000.0, clamped.effectiveReynoldsNumber(), 0.0);
		assertEquals(12.0, clamped.effectiveAngleOfAttackDegrees(), 0.0);
		assertEquals(0.7412, clamped.liftCoefficientCl(), 0.0);
		assertEquals(0.15082, clamped.dragCoefficientCd(), 0.0);
	}
}
