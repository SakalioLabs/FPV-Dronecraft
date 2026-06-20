package com.tenicana.dronecraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneEntityModelTest {
	@Test
	void positivePlayablePitchRendersAsNoseDownInLineOfSightView() {
		float playablePitch = (float) Math.toRadians(18.0);
		float modelPitch = DroneEntityModel.bodyPitchRotationRadians(playablePitch);
		float finalForwardYOffset = DroneEntityModel.renderedBodyForwardYOffsetAfterRendererTransform(playablePitch);

		assertTrue(modelPitch < 0.0f);
		assertEquals(-playablePitch, modelPitch, 1.0e-6f);
		assertTrue(finalForwardYOffset > 0.0f);
	}

	@Test
	void negativePlayablePitchRendersAsNoseUpInLineOfSightView() {
		float playablePitch = (float) Math.toRadians(-18.0);
		float finalForwardYOffset = DroneEntityModel.renderedBodyForwardYOffsetAfterRendererTransform(playablePitch);

		assertTrue(finalForwardYOffset < 0.0f);
		assertEquals(
				-DroneEntityModel.renderedBodyForwardYOffsetAfterRendererTransform(-playablePitch),
				finalForwardYOffset,
				1.0e-6f
		);
	}
}
