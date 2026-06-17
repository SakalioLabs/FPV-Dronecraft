package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.ControlStickProfile;
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
	void stickCommandsRampIntoVelocityAndVisibleAttitude() {
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

		assertTrue(step.targetVelocityZ() < -0.08f);
		assertTrue(step.targetVelocityX() < -0.08f);
		assertTrue(step.pitchRadians() < Math.toRadians(3.0));
		assertTrue(step.rollRadians() > -Math.toRadians(3.0));
		assertTrue(step.yawDegreesPerTick() > 0.30f);
		assertTrue(step.averageRpm() > 9000.0f);

		PlayableFlightModel.Step held = holdStick(FlightMode.HORIZON, 12, 0.45f, 0.50f, -0.40f, 0.25f);
		assertTrue(held.targetVelocityZ() < -0.35f);
		assertTrue(held.targetVelocityX() < -0.28f);
		assertTrue(held.pitchRadians() > Math.toRadians(8.0));
		assertTrue(held.rollRadians() < -Math.toRadians(6.5));
	}

	@Test
	void horizonModeLimitsFirstTickAttitudeForGentleInputs() {
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				FlightMode.HORIZON,
				0.45f,
				0.35f,
				0.35f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.ZERO
		);

		assertTrue(Math.abs(step.pitchRadians()) <= Math.toRadians(2.2));
		assertTrue(Math.abs(step.rollRadians()) <= Math.toRadians(2.2));
		assertTrue(Math.abs(step.targetVelocityX()) < 0.08f);
		assertTrue(Math.abs(step.targetVelocityZ()) < 0.08f);
	}

	@Test
	void horizonModeKeepsSmallStickCorrectionsGentle() {
		PlayableFlightModel.Step step = holdStick(FlightMode.HORIZON, 10, 0.42f, 0.25f, -0.25f, 0.25f);

		assertTrue(Math.abs(step.pitchRadians()) < Math.toRadians(5.8));
		assertTrue(Math.abs(step.rollRadians()) < Math.toRadians(6.3));
		assertTrue(Math.abs(step.targetVelocityX()) < 0.30f);
		assertTrue(Math.abs(step.targetVelocityZ()) < 0.30f);
		assertTrue(step.yawDegreesPerTick() < 0.40f);
	}

	@Test
	void angleModeRecentersReleasedSticks() {
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.30f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(0.0f, 0.0f, 0.0f, (float) Math.toRadians(18.0), (float) Math.toRadians(-14.0))
		);

		assertTrue(Math.abs(step.pitchRadians()) < Math.toRadians(18.0));
		assertTrue(Math.abs(step.rollRadians()) < Math.toRadians(14.0));
	}

	@Test
	void angleModeTurnsSmallGamepadCorrectionsIntoFineTrim() {
		float gentleStick = (float) ControlStickProfile.gamepadCommand(0.35, 0.10);
		PlayableFlightModel.Step held = holdStick(FlightMode.ANGLE, 20, 0.42f, gentleStick, -gentleStick, gentleStick);

		assertTrue(Math.abs(held.pitchRadians()) < Math.toRadians(0.30));
		assertTrue(Math.abs(held.rollRadians()) < Math.toRadians(0.30));
		assertTrue(Math.abs(held.targetVelocityX()) < 0.02f);
		assertTrue(Math.abs(held.targetVelocityZ()) < 0.02f);
		assertTrue(held.yawDegreesPerTick() < 0.01f);
	}

	@Test
	void angleModeTreatsMediumGamepadInputAsTrainingControl() {
		float mediumStick = (float) ControlStickProfile.gamepadCommand(0.60, 0.10);
		PlayableFlightModel.Step held = holdStick(FlightMode.ANGLE, 16, 0.42f, mediumStick, -mediumStick, mediumStick);

		assertTrue(Math.abs(held.pitchRadians()) < Math.toRadians(1.6));
		assertTrue(Math.abs(held.rollRadians()) < Math.toRadians(1.6));
		assertTrue(Math.abs(held.targetVelocityX()) < 0.09f);
		assertTrue(Math.abs(held.targetVelocityZ()) < 0.09f);
		assertTrue(held.yawDegreesPerTick() < 0.07f);
	}

	@Test
	void angleModeStillProvidesDeliberateFullStickAuthority() {
		PlayableFlightModel.Step held = holdStick(FlightMode.ANGLE, 18, 0.45f, 1.0f, -1.0f, 1.0f);

		assertTrue(Math.abs(held.pitchRadians()) > Math.toRadians(7.0));
		assertTrue(Math.abs(held.rollRadians()) > Math.toRadians(7.0));
		assertTrue(Math.abs(held.targetVelocityX()) > 0.35f);
		assertTrue(Math.abs(held.targetVelocityZ()) > 0.35f);
		assertTrue(held.yawDegreesPerTick() > 0.40f);
	}

	@Test
	void angleModeSmoothsYawIntoAndOutOfTurns() {
		PlayableFlightModel.Step first = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				PlayableFlightModel.State.ZERO
		);
		PlayableFlightModel.Step held = holdStick(FlightMode.ANGLE, 12, 0.45f, 0.0f, 0.0f, 1.0f);
		PlayableFlightModel.Step released = runFrom(
				FlightMode.ANGLE,
				6,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(held)
		);

		assertTrue(first.yawDegreesPerTick() > 0.15f);
		assertTrue(first.yawDegreesPerTick() < 0.25f);
		assertTrue(held.yawDegreesPerTick() > 0.47f);
		assertTrue(released.yawDegreesPerTick() < 0.02f);
	}

	@Test
	void angleModeBrakesHorizontalDriftWhenStickIsReleased() {
		PlayableFlightModel.Step held = holdStick(FlightMode.ANGLE, 18, 0.45f, 1.0f, -1.0f, 0.0f);
		PlayableFlightModel.Step released = runFrom(
				FlightMode.ANGLE,
				8,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(held)
		);

		assertTrue(Math.abs(held.velocityX()) > 0.35f);
		assertTrue(Math.abs(held.velocityZ()) > 0.35f);
		assertTrue(Math.abs(released.velocityX()) < 0.07f);
		assertTrue(Math.abs(released.velocityZ()) < 0.07f);
	}

	@Test
	void angleModeAirBrakesHoverDriftWithCenteredSticks() {
		PlayableFlightModel.Step released = runFrom(
				FlightMode.ANGLE,
				4,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.70f,
						0.0f,
						-0.62f,
						(float) Math.toRadians(2.0),
						(float) Math.toRadians(-2.0),
						0.0f,
						FlightMode.ANGLE
				)
		);

		assertTrue(Math.abs(released.velocityX()) < 0.06f);
		assertTrue(Math.abs(released.velocityZ()) < 0.06f);
		assertTrue(Math.abs(released.pitchRadians()) < Math.toRadians(1.0));
		assertTrue(Math.abs(released.rollRadians()) < Math.toRadians(1.0));
	}

	@Test
	void hoverAirBrakeDoesNotFightDeliberateAngleInput() {
		PlayableFlightModel.Step step = runFrom(
				FlightMode.ANGLE,
				8,
				0.20f,
				1.0f,
				-1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);

		assertTrue(Math.abs(step.targetVelocityX()) > 0.18f);
		assertTrue(Math.abs(step.targetVelocityZ()) > 0.18f);
		assertTrue(Math.abs(step.velocityX()) > 0.08f);
		assertTrue(Math.abs(step.velocityZ()) > 0.08f);
	}

	@Test
	void angleModeCatchesHoverAfterThrottleIsRecentered() {
		PlayableFlightModel.Step climb = holdStick(FlightMode.ANGLE, 12, 0.60f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step hover = runFrom(
				FlightMode.ANGLE,
				8,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(climb)
		);

		assertTrue(climb.velocityY() > 0.45f);
		assertEquals(0.0f, hover.targetVelocityY(), 1.0e-5f);
		assertTrue(Math.abs(hover.velocityY()) < 0.06f);
	}

	@Test
	void angleModeScrubsHorizontalDriftAfterTouchdown() {
		PlayableFlightModel.Step held = holdStick(FlightMode.ANGLE, 18, 0.45f, 1.0f, -1.0f, 0.0f);
		PlayableFlightModel.Step landed = runFrom(
				FlightMode.ANGLE,
				5,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				true,
				stateFrom(held)
		);

		assertTrue(Math.abs(held.velocityX()) > 0.35f);
		assertTrue(Math.abs(held.velocityZ()) > 0.35f);
		assertEquals(0.0f, landed.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, landed.velocityY(), 1.0e-5f);
		assertTrue(Math.abs(landed.velocityX()) < 0.03f);
		assertTrue(Math.abs(landed.velocityZ()) < 0.03f);
	}

	@Test
	void angleModeGroundFrictionDoesNotMuffleTakeoffAuthority() {
		PlayableFlightModel.Step step = runFrom(
				FlightMode.ANGLE,
				10,
				0.60f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				true,
				PlayableFlightModel.State.ZERO
		);

		assertTrue(step.targetVelocityY() > 0.60f);
		assertTrue(step.velocityY() > 0.35f);
		assertTrue(step.targetVelocityZ() < -0.25f);
		assertTrue(step.velocityZ() < -0.10f);
	}

	@Test
	void angleModeGivesGamepadCenterAStableHoverWindow() {
		float centerThrottle = (float) ControlStickProfile.gamepadThrottle(0.50);
		float slightClimbStick = (float) ControlStickProfile.gamepadThrottle(0.55);
		float climbStick = (float) ControlStickProfile.gamepadThrottle(0.60);
		float descentStick = (float) ControlStickProfile.gamepadThrottle(0.40);

		PlayableFlightModel.Step center = holdStick(FlightMode.ANGLE, 8, centerThrottle, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step slightClimb = holdStick(FlightMode.ANGLE, 8, slightClimbStick, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step climb = holdStick(FlightMode.ANGLE, 8, climbStick, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step descent = holdStick(FlightMode.ANGLE, 8, descentStick, 0.0f, 0.0f, 0.0f);

		assertEquals(0.0f, center.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, slightClimb.targetVelocityY(), 1.0e-5f);
		assertTrue(climb.targetVelocityY() > 0.10f);
		assertTrue(descent.targetVelocityY() < -0.10f);
		assertTrue(descent.targetVelocityY() > -0.35f);
	}

	@Test
	void acroModeHoldsAttitudeAfterStickRelease() {
		PlayableFlightModel.Step held = holdStick(FlightMode.ACRO, 10, 0.45f, 0.90f, -0.80f, 0.0f);
		PlayableFlightModel.Step released = runFrom(
				FlightMode.ACRO,
				6,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(held)
		);

		assertTrue(held.pitchRadians() > Math.toRadians(38.0));
		assertTrue(held.rollRadians() < -Math.toRadians(34.0));
		assertTrue(released.pitchRadians() > held.pitchRadians() * 0.95f);
		assertTrue(released.rollRadians() < held.rollRadians() * 0.95f);
		assertTrue(released.targetVelocityZ() < -1.25f);
	}

	@Test
	void acroProfileHasMoreAuthorityThanAngleProfile() {
		PlayableFlightModel.Step angle = holdStick(FlightMode.ANGLE, 12, 0.45f, 1.0f, 1.0f, 1.0f);
		PlayableFlightModel.Step acro = holdStick(FlightMode.ACRO, 12, 0.45f, 1.0f, 1.0f, 1.0f);

		assertTrue(Math.abs(acro.targetVelocityZ()) > Math.abs(angle.targetVelocityZ()));
		assertTrue(acro.pitchRadians() > angle.pitchRadians());
		assertTrue(acro.yawDegreesPerTick() > angle.yawDegreesPerTick());
	}

	@Test
	void switchingFromAcroToAngleReleasesHeldAttitude() {
		PlayableFlightModel.Step acro = holdStick(FlightMode.ACRO, 14, 0.45f, 1.0f, -1.0f, 1.0f);
		PlayableFlightModel.Step angle = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(acro)
		);

		assertTrue(Math.abs(acro.pitchRadians()) > Math.toRadians(55.0));
		assertTrue(Math.abs(acro.rollRadians()) > Math.toRadians(55.0));
		assertTrue(Math.abs(angle.pitchRadians()) < Math.toRadians(10.0));
		assertTrue(Math.abs(angle.rollRadians()) < Math.toRadians(10.0));
		assertTrue(Math.abs(angle.yawDegreesPerTick()) < 0.13f);
		assertTrue(Math.abs(angle.targetVelocityX()) < 0.50f);
		assertTrue(Math.abs(angle.targetVelocityZ()) < 0.50f);
	}

	private static PlayableFlightModel.Step holdStick(FlightMode mode, int ticks, float throttle, float pitch, float roll, float yaw) {
		return runFrom(mode, ticks, throttle, pitch, roll, yaw, 0.20f, false, PlayableFlightModel.State.ZERO);
	}

	private static PlayableFlightModel.Step runFrom(
			FlightMode mode,
			int ticks,
			float throttle,
			float pitch,
			float roll,
			float yaw,
			float hoverThrottle,
			boolean nearGroundLocked,
			PlayableFlightModel.State state
	) {
		PlayableFlightModel.Step step = null;
		PlayableFlightModel.State current = state;
		for (int i = 0; i < ticks; i++) {
			step = PlayableFlightModel.step(mode, throttle, pitch, roll, yaw, hoverThrottle, nearGroundLocked, current);
			current = stateFrom(step);
		}
		return step;
	}

	private static PlayableFlightModel.State stateFrom(PlayableFlightModel.Step step) {
		return new PlayableFlightModel.State(
				step.velocityX(),
				step.velocityY(),
				step.velocityZ(),
				step.pitchRadians(),
				step.rollRadians(),
				step.yawDegreesPerTick(),
				step.mode()
		);
	}
}
