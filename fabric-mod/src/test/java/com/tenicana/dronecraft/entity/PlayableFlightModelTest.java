package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.FlightMode;

class PlayableFlightModelTest {
	@Test
	void hoverBandDoesNotForceClimbOrSink() {
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				FlightMode.HORIZON,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.ZERO
		);

		assertEquals(0.0f, step.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, step.pitchRadians(), 1.0e-5f);
		assertEquals(0.0f, step.rollRadians(), 1.0e-5f);
	}

	@Test
	void lowThrottleDescendsUnlessGroundLocked() {
		PlayableFlightModel.Step airborne = PlayableFlightModel.step(
				FlightMode.HORIZON,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.ZERO
		);
		PlayableFlightModel.Step grounded = PlayableFlightModel.step(
				FlightMode.HORIZON,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				true,
				new PlayableFlightModel.State(0.0f, -0.5f, 0.0f)
		);

		assertTrue(airborne.targetVelocityY() < -0.9f);
		assertEquals(0.0f, grounded.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, grounded.velocityY(), 1.0e-5f);
	}

	@Test
	void stickCommandsProduceVelocityAndVisibleAttitude() {
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				FlightMode.HORIZON,
				0.45f,
				0.50f,
				-0.40f,
				0.25f,
				0.20f,
				false,
				PlayableFlightModel.State.ZERO
		);

		assertTrue(step.targetVelocityZ() < -0.50f);
		assertTrue(step.targetVelocityX() < -0.35f);
		assertTrue(step.pitchRadians() > Math.toRadians(15.0));
		assertTrue(step.rollRadians() < -Math.toRadians(12.0));
		assertTrue(step.yawDegreesPerTick() > 0.4f);
		assertTrue(step.averageRpm() > 9000.0f);
	}

	@Test
	void acroProfileHasMoreAuthorityThanAngleProfile() {
		PlayableFlightModel.Step angle = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.45f,
				1.0f,
				1.0f,
				1.0f,
				0.20f,
				false,
				PlayableFlightModel.State.ZERO
		);
		PlayableFlightModel.Step acro = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				1.0f,
				1.0f,
				0.20f,
				false,
				PlayableFlightModel.State.ZERO
		);

		assertTrue(Math.abs(acro.targetVelocityZ()) > Math.abs(angle.targetVelocityZ()));
		assertTrue(acro.pitchRadians() > angle.pitchRadians());
		assertTrue(acro.yawDegreesPerTick() > angle.yawDegreesPerTick());
	}
}
