package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.client.config.DroneClientConfig;
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
	void hoverBandEdgesRampWithoutVerticalSpeedJump() {
		PlayableFlightModel.Step highInsideBand = holdStick(FlightMode.ANGLE, 1, 0.254f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step justAboveBand = holdStick(FlightMode.ANGLE, 1, 0.256f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step firmerClimb = holdStick(FlightMode.ANGLE, 1, 0.320f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step lowInsideBand = holdStick(FlightMode.ANGLE, 1, 0.146f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step justBelowBand = holdStick(FlightMode.ANGLE, 1, 0.144f, 0.0f, 0.0f, 0.0f);

		assertEquals(0.0f, highInsideBand.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, lowInsideBand.targetVelocityY(), 1.0e-5f);
		assertTrue(justAboveBand.targetVelocityY() > 0.0f);
		assertTrue(justAboveBand.targetVelocityY() < 0.02f);
		assertTrue(firmerClimb.targetVelocityY() > justAboveBand.targetVelocityY() * 8.0f);
		assertTrue(firmerClimb.targetVelocityY() < 0.25f);
		assertTrue(justBelowBand.targetVelocityY() < 0.0f);
		assertTrue(justBelowBand.targetVelocityY() > -0.02f);
	}

	@Test
	void playableRpmTelemetryUsesHoverReferencedCurve() {
		PlayableFlightModel.Step idle = holdStick(FlightMode.ANGLE, 1, 0.0f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step hover = holdStick(FlightMode.ANGLE, 1, 0.20f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step slightClimb = holdStick(FlightMode.ANGLE, 1, 0.22f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step punch = holdStick(FlightMode.ANGLE, 1, 0.80f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step heavyHover = holdStick(FlightMode.ANGLE, 1, 0.40f, 0.0f, 0.0f, 0.0f, 0.40f);

		assertEquals(2200.0f, idle.averageRpm(), 1.0e-3f);
		assertTrue(hover.averageRpm() > 6200.0f);
		assertTrue(hover.averageRpm() < 7000.0f);
		assertTrue(slightClimb.averageRpm() >= hover.averageRpm());
		assertTrue(slightClimb.averageRpm() < 7000.0f);
		assertTrue(punch.averageRpm() > 11000.0f);
		assertTrue(punch.averageRpm() < 12400.0f);
		assertTrue(heavyHover.averageRpm() > 6200.0f);
		assertTrue(heavyHover.averageRpm() < 7000.0f);
	}

	@Test
	void nullModeFallsBackToFirstFlightAngle() {
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				null,
				0.45f,
				0.60f,
				0.60f,
				0.60f,
				0.20f,
				false,
				null
		);

		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, step.mode());
		assertTrue(Math.abs(step.pitchRadians()) <= Math.toRadians(0.55));
		assertTrue(Math.abs(step.rollRadians()) <= Math.toRadians(0.55));
		assertTrue(step.yawDegreesPerTick() <= 0.25f);
	}

	@Test
	void zeroPlayableStateUsesFirstFlightAngle() {
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, PlayableFlightModel.State.ZERO.mode());
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, PlayableFlightModel.State.zero(null).mode());
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
		assertTrue(step.targetVelocityX() < -0.07f);
		assertTrue(step.pitchRadians() < Math.toRadians(3.0));
		assertTrue(step.rollRadians() > -Math.toRadians(3.0));
		assertTrue(step.yawDegreesPerTick() > 0.30f);
		assertTrue(step.averageRpm() > 7000.0f);
		assertTrue(step.averageRpm() < 9000.0f);

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
		assertTrue(held.yawDegreesPerTick() > 0.34f);
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

		assertTrue(first.yawDegreesPerTick() > 0.12f);
		assertTrue(first.yawDegreesPerTick() < 0.22f);
		assertTrue(held.yawDegreesPerTick() > 0.35f);
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

		assertTrue(Math.abs(held.velocityX()) > 0.25f);
		assertTrue(Math.abs(held.velocityZ()) > 0.25f);
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
	void angleModeSnapsTinyReleasedDriftToRest() {
		PlayableFlightModel.Step released = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.012f,
						0.011f,
						-0.010f,
						(float) Math.toRadians(0.15),
						(float) Math.toRadians(-0.14),
						0.012f,
						FlightMode.ANGLE
				)
		);

		assertEquals(0.0f, released.velocityX(), 1.0e-6f);
		assertEquals(0.0f, released.velocityY(), 1.0e-6f);
		assertEquals(0.0f, released.velocityZ(), 1.0e-6f);
		assertEquals(0.0f, released.pitchRadians(), 1.0e-6f);
		assertEquals(0.0f, released.rollRadians(), 1.0e-6f);
		assertEquals(0.0f, released.yawDegreesPerTick(), 1.0e-6f);
	}

	@Test
	void acroModeDoesNotSnapTinyHeldAttitudeToLevel() {
		PlayableFlightModel.Step released = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(0.15),
						(float) Math.toRadians(-0.14),
						0.0f,
						FlightMode.ACRO
				)
		);

		assertTrue(released.pitchRadians() > Math.toRadians(0.10));
		assertTrue(released.rollRadians() < -Math.toRadians(0.10));
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

		assertTrue(Math.abs(step.targetVelocityX()) > 0.15f);
		assertTrue(Math.abs(step.targetVelocityZ()) > 0.15f);
		assertTrue(Math.abs(step.velocityX()) > 0.05f, "velocityX=" + step.velocityX());
		assertTrue(Math.abs(step.velocityZ()) > 0.05f, "velocityZ=" + step.velocityZ());
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

		assertTrue(Math.abs(held.velocityX()) > 0.25f);
		assertTrue(Math.abs(held.velocityZ()) > 0.25f);
		assertEquals(0.0f, landed.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, landed.velocityY(), 1.0e-5f);
		assertTrue(Math.abs(landed.velocityX()) < 0.03f);
		assertTrue(Math.abs(landed.velocityZ()) < 0.03f);
	}

	@Test
	void angleModeAirBrakesReleasedSticksDuringPoweredClimb() {
		PlayableFlightModel.Step held = runFrom(
				FlightMode.ANGLE,
				12,
				0.60f,
				1.0f,
				-1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);
		PlayableFlightModel.Step released = runFrom(
				FlightMode.ANGLE,
				4,
				0.60f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(held)
		);

		assertTrue(Math.abs(held.velocityX()) > 0.14f, "heldVelocityX=" + held.velocityX());
		assertTrue(Math.abs(held.velocityZ()) > 0.14f, "heldVelocityZ=" + held.velocityZ());
		assertTrue(Math.abs(released.velocityX()) < 0.04f, "releasedVelocityX=" + released.velocityX());
		assertTrue(Math.abs(released.velocityZ()) < 0.04f, "releasedVelocityZ=" + released.velocityZ());
	}

	@Test
	void angleModeSoftensHorizontalAuthorityWhileGroundLockedNearHover() {
		PlayableFlightModel.Step groundHover = runFrom(
				FlightMode.ANGLE,
				12,
				0.20f,
				1.0f,
				-1.0f,
				0.0f,
				0.20f,
				true,
				PlayableFlightModel.State.ZERO
		);
		PlayableFlightModel.Step poweredTakeoff = runFrom(
				FlightMode.ANGLE,
				12,
				0.60f,
				1.0f,
				-1.0f,
				0.0f,
				0.20f,
				true,
				PlayableFlightModel.State.ZERO
		);

		assertTrue(Math.abs(groundHover.targetVelocityX()) < 0.20f);
		assertTrue(Math.abs(groundHover.targetVelocityZ()) < 0.20f);
		assertTrue(Math.abs(groundHover.velocityX()) < 0.10f);
		assertTrue(Math.abs(groundHover.velocityZ()) < 0.10f);
		assertTrue(Math.abs(poweredTakeoff.targetVelocityX()) > Math.abs(groundHover.targetVelocityX()) * 1.8f);
		assertTrue(Math.abs(poweredTakeoff.targetVelocityZ()) > Math.abs(groundHover.targetVelocityZ()) * 1.8f);
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
	void angleModeBrakesVerticalSpeedWhenThrottleReturnsToHover() {
		PlayableFlightModel.Step climb = runFrom(
				FlightMode.ANGLE,
				8,
				0.60f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);
		PlayableFlightModel.Step recovered = runFrom(
				FlightMode.ANGLE,
				4,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(climb)
		);

		assertTrue(climb.velocityY() > 0.30f, "climbVelocityY=" + climb.velocityY());
		assertEquals(0.0f, recovered.targetVelocityY(), 1.0e-6f);
		assertTrue(Math.abs(recovered.velocityY()) < 0.08f, "recoveredVelocityY=" + recovered.velocityY());
	}

	@Test
	void lowAltitudeGuardSoftensHorizontalAuthorityWithoutBlockingVerticalFlight() {
		PlayableFlightModel.Step normal = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.45f,
				1.0f,
				-1.0f,
				0.0f,
				0.20f,
				false,
				1.0f,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);
		PlayableFlightModel.Step guarded = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.45f,
				1.0f,
				-1.0f,
				0.0f,
				0.20f,
				false,
				0.62f,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);
		PlayableFlightModel.Step descending = PlayableFlightModel.step(
				FlightMode.ANGLE,
				0.10f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				0.62f,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);

		assertTrue(Math.abs(guarded.targetVelocityX()) < Math.abs(normal.targetVelocityX()) * 0.70f);
		assertTrue(Math.abs(guarded.targetVelocityZ()) < Math.abs(normal.targetVelocityZ()) * 0.70f);
		assertEquals(normal.targetVelocityY(), guarded.targetVelocityY(), 1.0e-6f);
		assertTrue(descending.targetVelocityY() < -0.10f);
	}

	@Test
	void lowAltitudeGuardAlsoSoftensVisibleAttitudeAndYawDuringTakeoff() {
		PlayableFlightModel.Step normal = runFrom(
				FlightMode.ANGLE,
				12,
				0.60f,
				1.0f,
				-1.0f,
				1.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);
		PlayableFlightModel.Step guarded = runFrom(
				FlightMode.ANGLE,
				12,
				0.60f,
				1.0f,
				-1.0f,
				1.0f,
				0.20f,
				true,
				PlayableFlightModel.State.zero(FlightMode.ANGLE)
		);

		assertEquals(normal.targetVelocityY(), guarded.targetVelocityY(), 1.0e-6f);
		assertTrue(guarded.targetVelocityY() > 0.60f);
		assertTrue(Math.abs(guarded.pitchRadians()) < Math.abs(normal.pitchRadians()) * 0.82f);
		assertTrue(Math.abs(guarded.rollRadians()) < Math.abs(normal.rollRadians()) * 0.82f);
		assertTrue(guarded.yawDegreesPerTick() < normal.yawDegreesPerTick() * 0.75f);
	}

	@Test
	void angleModeGivesGamepadCenterAStableHoverWindow() {
		float lowJitterThrottle = (float) ControlStickProfile.gamepadThrottle(0.48);
		float centerThrottle = (float) ControlStickProfile.gamepadThrottle(0.50);
		float highJitterThrottle = (float) ControlStickProfile.gamepadThrottle(0.52);
		float slightClimbStick = (float) ControlStickProfile.gamepadThrottle(0.55);
		float climbStick = (float) ControlStickProfile.gamepadThrottle(0.60);
		float descentStick = (float) ControlStickProfile.gamepadThrottle(0.40);

		PlayableFlightModel.Step lowJitter = holdStick(FlightMode.ANGLE, 8, lowJitterThrottle, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step center = holdStick(FlightMode.ANGLE, 8, centerThrottle, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step highJitter = holdStick(FlightMode.ANGLE, 8, highJitterThrottle, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step slightClimb = holdStick(FlightMode.ANGLE, 8, slightClimbStick, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step climb = holdStick(FlightMode.ANGLE, 8, climbStick, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step descent = holdStick(FlightMode.ANGLE, 8, descentStick, 0.0f, 0.0f, 0.0f);

		assertEquals(0.0f, lowJitter.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, center.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, highJitter.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, slightClimb.targetVelocityY(), 1.0e-5f);
		assertTrue(climb.targetVelocityY() > 0.10f);
		assertTrue(descent.targetVelocityY() < -0.10f);
		assertTrue(descent.targetVelocityY() > -0.35f);
	}

	@Test
	void angleModeHonorsAirframeSpecificGamepadHoverDetent() {
		float heavyHover = 0.40f;
		float lowJitterThrottle = (float) ControlStickProfile.gamepadThrottle(0.48, heavyHover);
		float centerThrottle = (float) ControlStickProfile.gamepadThrottle(0.50, heavyHover);
		float highJitterThrottle = (float) ControlStickProfile.gamepadThrottle(0.52, heavyHover);
		float slightClimbStick = (float) ControlStickProfile.gamepadThrottle(0.55, heavyHover);
		float climbStick = (float) ControlStickProfile.gamepadThrottle(0.60, heavyHover);
		float descentStick = (float) ControlStickProfile.gamepadThrottle(0.40, heavyHover);

		PlayableFlightModel.Step lowJitter = holdStick(FlightMode.ANGLE, 8, lowJitterThrottle, 0.0f, 0.0f, 0.0f, heavyHover);
		PlayableFlightModel.Step center = holdStick(FlightMode.ANGLE, 8, centerThrottle, 0.0f, 0.0f, 0.0f, heavyHover);
		PlayableFlightModel.Step highJitter = holdStick(FlightMode.ANGLE, 8, highJitterThrottle, 0.0f, 0.0f, 0.0f, heavyHover);
		PlayableFlightModel.Step slightClimb = holdStick(FlightMode.ANGLE, 8, slightClimbStick, 0.0f, 0.0f, 0.0f, heavyHover);
		PlayableFlightModel.Step climb = holdStick(FlightMode.ANGLE, 8, climbStick, 0.0f, 0.0f, 0.0f, heavyHover);
		PlayableFlightModel.Step descent = holdStick(FlightMode.ANGLE, 8, descentStick, 0.0f, 0.0f, 0.0f, heavyHover);

		assertEquals(heavyHover, lowJitterThrottle, 1.0e-6f);
		assertEquals(heavyHover, centerThrottle, 1.0e-6f);
		assertEquals(heavyHover, highJitterThrottle, 1.0e-6f);
		assertEquals(0.0f, lowJitter.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, center.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, highJitter.targetVelocityY(), 1.0e-5f);
		assertEquals(0.0f, slightClimb.targetVelocityY(), 1.0e-5f);
		assertTrue(climb.targetVelocityY() > 0.05f);
		assertTrue(descent.targetVelocityY() < -0.15f);
		assertTrue(descent.targetVelocityY() > -0.35f);
	}

	@Test
	void playableServerLayerPreservesClientShapedFineStickCommands() {
		float shapedHalfStick = (float) ControlStickProfile.gamepadCommand(0.50, 0.10, 1.00, 0.42);

		assertTrue(shapedHalfStick > 0.014f);
		assertTrue(shapedHalfStick < 0.017f);
		assertEquals(0.0f, PlayableFlightModel.playableAxisCommand(0.004f), 1.0e-6f);
		assertEquals(shapedHalfStick, PlayableFlightModel.playableAxisCommand(shapedHalfStick), 1.0e-6f);
		assertEquals(-shapedHalfStick, PlayableFlightModel.playableAxisCommand(-shapedHalfStick), 1.0e-6f);
	}

	@Test
	void yawRelativeVelocityMappingKeepsPitchAndRollAlignedWithDroneHeading() {
		PlayableFlightModel.Velocity forwardYaw0 = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.25f, -1.0f, 0.0f);
		PlayableFlightModel.Velocity forwardYaw90 = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.25f, -1.0f, 90.0f);
		PlayableFlightModel.Velocity rightYaw90 = PlayableFlightModel.worldVelocityForYaw(1.0f, 0.0f, 0.0f, 90.0f);
		PlayableFlightModel.Velocity forwardYaw180 = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.25f, -1.0f, 180.0f);

		assertEquals(0.0f, forwardYaw0.x(), 1.0e-5f);
		assertEquals(0.25f, forwardYaw0.y(), 1.0e-5f);
		assertEquals(-1.0f, forwardYaw0.z(), 1.0e-5f);
		assertEquals(1.0f, forwardYaw90.x(), 1.0e-5f);
		assertEquals(0.25f, forwardYaw90.y(), 1.0e-5f);
		assertEquals(0.0f, forwardYaw90.z(), 1.0e-5f);
		assertEquals(0.0f, rightYaw90.x(), 1.0e-5f);
		assertEquals(1.0f, rightYaw90.z(), 1.0e-5f);
		assertEquals(0.0f, forwardYaw180.x(), 1.0e-5f);
		assertEquals(0.25f, forwardYaw180.y(), 1.0e-5f);
		assertEquals(1.0f, forwardYaw180.z(), 1.0e-5f);
	}

	@Test
	void yawRelativeVelocityMappingRoundTripsCollisionAdjustedWorldVelocity() {
		PlayableFlightModel.Velocity world = PlayableFlightModel.worldVelocityForYaw(0.35f, -0.20f, -0.80f, 135.0f);
		PlayableFlightModel.Velocity local = PlayableFlightModel.localVelocityForYaw(world.x(), world.y(), world.z(), 135.0f);

		assertEquals(0.35f, local.x(), 1.0e-5f);
		assertEquals(-0.20f, local.y(), 1.0e-5f);
		assertEquals(-0.80f, local.z(), 1.0e-5f);
	}

	@Test
	void angleModeWithGentleTrainingPresetKeepsMidStickCalm() {
		float mediumStick = (float) ControlStickProfile.gamepadCommand(0.70, 0.10, 1.00, 0.42);
		float fullStick = (float) ControlStickProfile.gamepadCommand(1.0, 0.10, 1.00, 0.42);

		PlayableFlightModel.Step medium = holdStick(FlightMode.ANGLE, 18, 0.42f, mediumStick, -mediumStick, mediumStick);
		PlayableFlightModel.Step full = holdStick(FlightMode.ANGLE, 18, 0.45f, fullStick, -fullStick, fullStick);

		assertTrue(Math.abs(medium.pitchRadians()) < Math.toRadians(1.8));
		assertTrue(Math.abs(medium.rollRadians()) < Math.toRadians(1.8));
		assertTrue(Math.abs(medium.targetVelocityX()) < 0.035f, "mediumTargetX=" + medium.targetVelocityX());
		assertTrue(Math.abs(medium.targetVelocityZ()) < 0.035f, "mediumTargetZ=" + medium.targetVelocityZ());
		assertTrue(medium.yawDegreesPerTick() < 0.07f);
		assertTrue(Math.abs(full.pitchRadians()) > Math.toRadians(3.5));
		assertTrue(Math.abs(full.rollRadians()) > Math.toRadians(3.5));
		assertTrue(Math.abs(full.targetVelocityX()) > 0.15f, "fullTargetX=" + full.targetVelocityX());
		assertTrue(Math.abs(full.targetVelocityZ()) > 0.15f, "fullTargetZ=" + full.targetVelocityZ());
		assertTrue(full.yawDegreesPerTick() > 0.15f);
	}

	@Test
	void defaultTrainingClientTapStaysCalmAndRecenters() {
		DroneClientConfig config = DroneClientConfig.defaults();
		ClientTrainingAxes axes = new ClientTrainingAxes();
		PlayableFlightModel.State state = PlayableFlightModel.State.zero(FlightMode.ANGLE);
		float hoverThrottle = (float) ControlStickProfile.gamepadThrottle(0.50);
		float maxTapPitch = 0.0f;
		float maxTapRoll = 0.0f;
		float maxTapYaw = 0.0f;
		float maxTapHorizontalTarget = 0.0f;

		for (int i = 0; i < 12; i++) {
			ClientTrainingAxes.Sample sample = axes.sample(config, 0.70f, -0.70f, 0.70f);
			PlayableFlightModel.Step step = PlayableFlightModel.step(
					FlightMode.ANGLE,
					hoverThrottle,
					sample.pitch(),
					sample.roll(),
					sample.yaw(),
					hoverThrottle,
					false,
					state
			);
			state = stateFrom(step);
			maxTapPitch = Math.max(maxTapPitch, Math.abs(step.pitchRadians()));
			maxTapRoll = Math.max(maxTapRoll, Math.abs(step.rollRadians()));
			maxTapYaw = Math.max(maxTapYaw, Math.abs(step.yawDegreesPerTick()));
			maxTapHorizontalTarget = Math.max(maxTapHorizontalTarget, Math.max(Math.abs(step.targetVelocityX()), Math.abs(step.targetVelocityZ())));
		}

		PlayableFlightModel.Step released = null;
		for (int i = 0; i < 8; i++) {
			ClientTrainingAxes.Sample sample = axes.sample(config, 0.0f, 0.0f, 0.0f);
			released = PlayableFlightModel.step(
					FlightMode.ANGLE,
					hoverThrottle,
					sample.pitch(),
					sample.roll(),
					sample.yaw(),
					hoverThrottle,
					false,
					state
			);
			state = stateFrom(released);
		}

		assertTrue(maxTapPitch < Math.toRadians(0.75), "maxTapPitchDeg=" + Math.toDegrees(maxTapPitch));
		assertTrue(maxTapRoll < Math.toRadians(0.75), "maxTapRollDeg=" + Math.toDegrees(maxTapRoll));
		assertTrue(maxTapYaw < 0.035f, "maxTapYawDegPerTick=" + maxTapYaw);
		assertTrue(maxTapHorizontalTarget < 0.045f, "maxTapHorizontalTarget=" + maxTapHorizontalTarget);
		assertTrue(Math.abs(released.pitchRadians()) < Math.toRadians(0.15));
		assertTrue(Math.abs(released.rollRadians()) < Math.toRadians(0.15));
		assertEquals(0.0f, released.yawDegreesPerTick(), 1.0e-6f);
		assertEquals(0.0f, released.targetVelocityX(), 1.0e-6f);
		assertEquals(0.0f, released.targetVelocityZ(), 1.0e-6f);
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
	void acroGamepadPresetKeepsMidStickProgressiveButKeepsFullAuthority() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.applyGamepadFeelPreset(DroneClientConfig.ControlFeelPreset.ACRO);
		float hoverThrottle = (float) ControlStickProfile.gamepadThrottle(0.50);
		PlayableFlightModel.Step midStick = runClientPresetStick(config, 20, 0.70f, -0.70f, 0.70f, hoverThrottle);
		PlayableFlightModel.Step fullStick = runClientPresetStick(config, 20, 1.0f, -1.0f, 1.0f, hoverThrottle);

		assertTrue(Math.abs(midStick.pitchRadians()) < Math.toRadians(20.0), "midPitchDeg=" + Math.toDegrees(midStick.pitchRadians()));
		assertTrue(Math.abs(midStick.rollRadians()) < Math.toRadians(22.0), "midRollDeg=" + Math.toDegrees(midStick.rollRadians()));
		assertTrue(midStick.yawDegreesPerTick() < 0.50f, "midYawDegPerTick=" + midStick.yawDegreesPerTick());
		assertTrue(Math.abs(fullStick.pitchRadians()) > Math.toRadians(55.0), "fullPitchDeg=" + Math.toDegrees(fullStick.pitchRadians()));
		assertTrue(Math.abs(fullStick.rollRadians()) > Math.toRadians(55.0), "fullRollDeg=" + Math.toDegrees(fullStick.rollRadians()));
		assertTrue(fullStick.yawDegreesPerTick() > 2.0f, "fullYawDegPerTick=" + fullStick.yawDegreesPerTick());
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

	@Test
	void switchingFromHorizonToAcroSoftCapturesCenteredSticks() {
		PlayableFlightModel.Step horizon = holdStick(FlightMode.HORIZON, 14, 0.45f, 1.0f, -1.0f, 1.0f);
		PlayableFlightModel.Step firstAcro = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(horizon)
		);
		PlayableFlightModel.Step captured = runFrom(
				FlightMode.ACRO,
				6,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(horizon)
		);
		PlayableFlightModel.Step heldAfterCapture = runFrom(
				FlightMode.ACRO,
				6,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(captured)
		);

		assertTrue(Math.abs(horizon.pitchRadians()) > Math.toRadians(30.0));
		assertTrue(Math.abs(horizon.rollRadians()) > Math.toRadians(30.0));
		assertTrue(firstAcro.modeSwitchTicksRemaining() > 0);
		assertTrue(Math.abs(captured.velocityX()) < Math.abs(firstAcro.velocityX()));
		assertTrue(Math.abs(captured.velocityZ()) < Math.abs(firstAcro.velocityZ()));
		assertTrue(Math.abs(captured.yawDegreesPerTick()) < Math.abs(firstAcro.yawDegreesPerTick()));
		assertTrue(Math.abs(captured.pitchRadians()) > Math.toRadians(18.0));
		assertTrue(Math.abs(captured.rollRadians()) > Math.toRadians(18.0));
		assertTrue(Math.abs(heldAfterCapture.pitchRadians()) > Math.abs(captured.pitchRadians()) * 0.94f);
		assertTrue(Math.abs(heldAfterCapture.rollRadians()) > Math.abs(captured.rollRadians()) * 0.94f);
	}

	private static PlayableFlightModel.Step holdStick(FlightMode mode, int ticks, float throttle, float pitch, float roll, float yaw) {
		return runFrom(mode, ticks, throttle, pitch, roll, yaw, 0.20f, false, PlayableFlightModel.State.ZERO);
	}

	private static PlayableFlightModel.Step holdStick(FlightMode mode, int ticks, float throttle, float pitch, float roll, float yaw, float hoverThrottle) {
		return runFrom(mode, ticks, throttle, pitch, roll, yaw, hoverThrottle, false, PlayableFlightModel.State.ZERO);
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

	private static PlayableFlightModel.Step runClientPresetStick(
			DroneClientConfig config,
			int ticks,
			float rawPitch,
			float rawRoll,
			float rawYaw,
			float hoverThrottle
	) {
		ClientTrainingAxes axes = new ClientTrainingAxes();
		PlayableFlightModel.State state = PlayableFlightModel.State.zero(FlightMode.ACRO);
		PlayableFlightModel.Step step = null;
		for (int i = 0; i < ticks; i++) {
			ClientTrainingAxes.Sample sample = axes.sample(config, rawPitch, rawRoll, rawYaw);
			step = PlayableFlightModel.step(
					FlightMode.ACRO,
					hoverThrottle,
					sample.pitch(),
					sample.roll(),
					sample.yaw(),
					hoverThrottle,
					false,
					state
			);
			state = stateFrom(step);
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
				step.mode(),
				step.modeSwitchTicksRemaining()
		);
	}

	private static final class ClientTrainingAxes {
		private float pitch;
		private float roll;
		private float yaw;

		private Sample sample(DroneClientConfig config, float rawPitch, float rawRoll, float rawYaw) {
			pitch = approachAxis(
					pitch,
					trainingCommand(config, rawPitch, config.gamepadRollPitchRateScale()),
					config.gamepadAxisRisePerTick(),
					config.gamepadAxisFallPerTick()
			);
			roll = approachAxis(
					roll,
					trainingCommand(config, rawRoll, config.gamepadRollPitchRateScale()),
					config.gamepadAxisRisePerTick(),
					config.gamepadAxisFallPerTick()
			);
			yaw = approachAxis(
					yaw,
					trainingCommand(config, rawYaw, config.gamepadYawRateScale()),
					config.gamepadAxisRisePerTick(),
					config.gamepadAxisFallPerTick()
			);
			return new Sample(pitch, roll, yaw);
		}

		private static float trainingCommand(DroneClientConfig config, float raw, float rateScale) {
			return (float) ControlStickProfile.gamepadCommand(
					raw,
					config.gamepadDeadband(),
					config.gamepadExpo(),
					rateScale
			);
		}

		private static float approachAxis(float current, float target, float risePerTick, float fallPerTick) {
			float step = shouldUseRiseStep(current, target) ? risePerTick : fallPerTick;
			if (current < target) {
				return Math.min(target, current + step);
			}
			if (current > target) {
				return Math.max(target, current - step);
			}
			return current;
		}

		private static boolean shouldUseRiseStep(float current, float target) {
			if (Math.abs(target) <= Math.abs(current)) {
				return false;
			}
			return current == 0.0f || target == 0.0f || Math.signum(current) == Math.signum(target);
		}

		private record Sample(float pitch, float roll, float yaw) {
		}
	}
}
