package com.tenicana.dronecraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneEntityModelTest {
	@Test
	void positivePlayablePitchRendersAsNoseDownInLineOfSightView() {
		float playablePitch = (float) Math.toRadians(18.0);
		float modelPitch = DroneEntityModel.bodyPitchRotationRadians(playablePitch);
		float renderedForwardYOffset = DroneEntityModel.renderedBodyForwardYOffset(playablePitch);

		assertTrue(modelPitch < 0.0f);
		assertEquals(-playablePitch, modelPitch, 1.0e-6f);
		assertTrue(renderedForwardYOffset > 0.0f);
	}

	@Test
	void negativePlayablePitchRendersAsNoseUpInLineOfSightView() {
		float playablePitch = (float) Math.toRadians(-18.0);
		float renderedForwardYOffset = DroneEntityModel.renderedBodyForwardYOffset(playablePitch);

		assertTrue(renderedForwardYOffset < 0.0f);
		assertEquals(
				-DroneEntityModel.renderedBodyForwardYOffset(-playablePitch),
				renderedForwardYOffset,
				1.0e-6f
		);
	}
}
