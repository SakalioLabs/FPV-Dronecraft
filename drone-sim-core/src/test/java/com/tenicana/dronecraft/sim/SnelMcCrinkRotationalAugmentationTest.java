package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnelMcCrinkRotationalAugmentationTest {
	private static final double ALPHA_RADIANS = Math.toRadians(15.0);

	@Test
	void paperEquationAnchorAugmentsNormalForceAndPreservesChordwiseForce() {
		SnelMcCrinkRotationalAugmentation.Sample sample =
				SnelMcCrinkRotationalAugmentation.evaluate(
						query(0.5),
						SnelMcCrinkRotationalAugmentation.Policy.SNEL_MCCRINK_NORMAL_FORCE
				);

		assertEquals(SnelMcCrinkRotationalAugmentation.Status.APPLIED, sample.status());
		assertEquals(1.6449340668482262, sample.potentialFlowLiftCoefficientCl(), 1.0e-15);
		assertEquals(0.7934461846394564, sample.normalForceCoefficientCn2d(), 1.0e-15);
		assertEquals(-0.1297811699788911, sample.chordwiseForceCoefficientCa2d(), 1.0e-15);
		assertEquals(0.8, sample.rotationToRelativeSpeedRatio(), 0.0);
		assertEquals(0.0384, sample.correctionFactor(), 1.0e-16);
		assertEquals(0.0324454681669719, sample.normalForceCoefficientDelta(), 1.0e-15);
		assertEquals(0.8258916528064283, sample.normalForceCoefficientCn3d(), 1.0e-15);
		assertEquals(0.8313399156485182, sample.correctedLiftCoefficientCl(), 1.0e-15);
		assertEquals(0.08839750508887995, sample.correctedDragCoefficientCd(), 1.0e-15);
		assertEquals(
				sample.chordwiseForceCoefficientCa2d(),
				sample.correctedDragCoefficientCd() * Math.cos(ALPHA_RADIANS)
						- sample.correctedLiftCoefficientCl() * Math.sin(ALPHA_RADIANS),
				1.0e-15
		);
		assertTrue(sample.requiresPropellerSpecificValidation());
	}

	@Test
	void sourceSpanLimitIsInclusiveAtPointEightFiveAndPolicyCanBeDisabled() {
		SnelMcCrinkRotationalAugmentation.Sample atLimit =
				SnelMcCrinkRotationalAugmentation.evaluate(
						query(SnelMcCrinkRotationalAugmentation.MAX_APPLIED_RADIAL_FRACTION),
						SnelMcCrinkRotationalAugmentation.Policy.SNEL_MCCRINK_NORMAL_FORCE
				);
		SnelMcCrinkRotationalAugmentation.Sample outboard =
				SnelMcCrinkRotationalAugmentation.evaluate(
						query(0.850001),
						SnelMcCrinkRotationalAugmentation.Policy.SNEL_MCCRINK_NORMAL_FORCE
				);
		SnelMcCrinkRotationalAugmentation.Sample disabled =
				SnelMcCrinkRotationalAugmentation.evaluate(
						query(0.5),
						SnelMcCrinkRotationalAugmentation.Policy.NONE
				);

		assertTrue(atLimit.applied());
		assertTrue(outboard.sourceSpanLimited());
		assertFalse(outboard.applied());
		assertEquals(0.8, outboard.correctedLiftCoefficientCl(), 1.0e-15);
		assertEquals(0.08, outboard.correctedDragCoefficientCd(), 1.0e-16);
		assertEquals(SnelMcCrinkRotationalAugmentation.Status.POLICY_DISABLED,
				disabled.status());
		assertEquals(0.0, disabled.normalForceCoefficientDelta(), 0.0);
		assertFalse(disabled.requiresPropellerSpecificValidation());
	}

	private static SnelMcCrinkRotationalAugmentation.Query query(double radialFraction) {
		return new SnelMcCrinkRotationalAugmentation.Query(
				0.8,
				0.08,
				ALPHA_RADIANS,
				radialFraction,
				0.2,
				40.0,
				50.0
		);
	}
}
