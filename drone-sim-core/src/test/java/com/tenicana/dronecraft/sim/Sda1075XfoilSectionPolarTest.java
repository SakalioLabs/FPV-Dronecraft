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
	void returnsPathIndependentLowReynoldsAnchorFromCheckedInXfoilPolars() {
		Sda1075XfoilSectionPolar.PolarSample sample =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						10_000.0,
						6.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(Sda1075XfoilSectionPolar.InterpolationStatus.EXACT,
				sample.interpolationStatus());
		assertEquals(0.3124, sample.liftCoefficientCl(), 0.0);
		assertEquals(0.07423, sample.dragCoefficientCd(), 0.0);
		assertEquals(-0.0195, sample.pitchingMomentCoefficientCm(), 0.0);
		assertTrue(sample.lowReynoldsExtensionUsed());
		assertTrue(sample.sourceSweepAgreementVerified());
		assertFalse(sample.clamped());
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
	void lowReynoldsInterpolationUsesStableSourceSweepAnchors() {
		Sda1075XfoilSectionPolar.PolarSample sample =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						17_500.0,
						6.5,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(Sda1075XfoilSectionPolar.InterpolationStatus.BILINEAR,
				sample.interpolationStatus());
		assertEquals(15_000.0, sample.lowerReynoldsNumber(), 0.0);
		assertEquals(20_000.0, sample.upperReynoldsNumber(), 0.0);
		assertEquals(0.5, sample.reynoldsInterpolationFraction(), 0.0);
		assertEquals(0.5, sample.angleInterpolationFraction(), 0.0);
		assertEquals(0.333725, sample.liftCoefficientCl(), 1.0e-15);
		assertEquals(0.074345, sample.dragCoefficientCd(), 1.0e-15);
		assertEquals(-0.026525, sample.pitchingMomentCoefficientCm(), 1.0e-15);
	}

	@Test
	void outOfEnvelopeQueryMustExplicitlyBlockOrClamp() {
		Sda1075XfoilSectionPolar.PolarSample blocked =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						5_000.0,
						20.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		Sda1075XfoilSectionPolar.PolarSample clamped =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						5_000.0,
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
		assertEquals(10_000.0, clamped.effectiveReynoldsNumber(), 0.0);
		assertEquals(12.0, clamped.effectiveAngleOfAttackDegrees(), 0.0);
		assertEquals(0.5485, clamped.liftCoefficientCl(), 0.0);
		assertEquals(0.14329, clamped.dragCoefficientCd(), 0.0);
		assertTrue(clamped.lowReynoldsExtensionUsed());
	}

	@Test
	void lowReynoldsNegativeAngleMustExplicitlyBlockOrClamp() {
		Sda1075XfoilSectionPolar.PolarSample blocked =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						20_000.0,
						-1.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		Sda1075XfoilSectionPolar.PolarSample clamped =
				Sda1075XfoilSectionPolar.evaluateDegrees(
						20_000.0,
						-1.0,
						Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
				);

		assertTrue(blocked.blocked());
		assertFalse(blocked.reynoldsOutOfEnvelope());
		assertTrue(blocked.angleOfAttackOutOfEnvelope());
		assertEquals(0.0, clamped.effectiveAngleOfAttackDegrees(), 0.0);
		assertEquals(-0.0189, clamped.liftCoefficientCl(), 0.0);
		assertEquals(0.03231, clamped.dragCoefficientCd(), 0.0);
	}
}
