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
		assertTrue(firmerClimb.targetVelocityY() < 0.55f);
		assertTrue(justBelowBand.targetVelocityY() < 0.0f);
		assertTrue(justBelowBand.targetVelocityY() > -0.06f);
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
		assertTrue(Math.abs(step.pitchRadians()) <= Math.toRadians(3.2));
		assertTrue(Math.abs(step.rollRadians()) <= Math.toRadians(3.2));
		assertTrue(step.yawDegreesPerTick() > 0.60f);
		assertTrue(step.yawDegreesPerTick() <= 0.75f);
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

		assertTrue(step.targetVelocityZ() > 0.08f);
		assertTrue(step.targetVelocityX() > 0.07f);
		assertTrue(step.pitchRadians() <= Math.toRadians(4.0));
		assertTrue(step.rollRadians() >= -Math.toRadians(4.0));
		assertTrue(step.yawDegreesPerTick() > 0.30f);
		assertTrue(step.averageRpm() > 7000.0f);
		assertTrue(step.averageRpm() < 9000.0f);

		PlayableFlightModel.Step held = holdStick(FlightMode.HORIZON, 12, 0.45f, 0.50f, -0.40f, 0.25f);
		assertTrue(held.targetVelocityZ() > 1.80f);
		assertTrue(held.targetVelocityX() > 1.20f);
		assertTrue(held.pitchRadians() > Math.toRadians(8.0));
		assertTrue(held.rollRadians() < -Math.toRadians(6.5));
	}

	@Test
	void rollSideVelocityMatchesVisibleBankDirection() {
		PlayableFlightModel.Step leftBank = holdStick(FlightMode.HORIZON, 18, 0.55f, 0.0f, -0.75f, 0.0f);
		PlayableFlightModel.Step rightBank = holdStick(FlightMode.HORIZON, 18, 0.55f, 0.0f, 0.75f, 0.0f);

		assertTrue(leftBank.rollRadians() < -Math.toRadians(8.0));
		assertTrue(leftBank.targetVelocityX() > 1.8f);
		assertTrue(rightBank.rollRadians() > Math.toRadians(8.0));
		assertTrue(rightBank.targetVelocityX() < -1.8f);
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

		assertTrue(Math.abs(step.pitchRadians()) <= Math.toRadians(4.0));
		assertTrue(Math.abs(step.rollRadians()) <= Math.toRadians(4.0));
		assertTrue(Math.abs(step.targetVelocityX()) < 0.82f);
		assertTrue(Math.abs(step.targetVelocityZ()) < 0.82f);
	}

	@Test
	void horizonModeKeepsSmallStickCorrectionsGentle() {
		PlayableFlightModel.Step step = holdStick(FlightMode.HORIZON, 10, 0.42f, 0.25f, -0.25f, 0.25f);

		assertTrue(Math.abs(step.pitchRadians()) < Math.toRadians(11.5));
		assertTrue(Math.abs(step.rollRadians()) < Math.toRadians(11.5));
		assertTrue(Math.abs(step.targetVelocityX()) < 2.45f);
		assertTrue(Math.abs(step.targetVelocityZ()) < 2.45f);
		assertTrue(step.yawDegreesPerTick() < 0.95f);
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

		assertTrue(Math.abs(held.pitchRadians()) > Math.toRadians(2.0));
		assertTrue(Math.abs(held.pitchRadians()) < Math.toRadians(3.0));
		assertTrue(Math.abs(held.rollRadians()) > Math.toRadians(2.0));
		assertTrue(Math.abs(held.rollRadians()) < Math.toRadians(3.0));
		assertTrue(Math.abs(held.targetVelocityX()) > 0.15f);
		assertTrue(Math.abs(held.targetVelocityX()) < 0.42f);
		assertTrue(Math.abs(held.targetVelocityZ()) > 0.15f);
		assertTrue(Math.abs(held.targetVelocityZ()) < 0.42f);
		assertTrue(held.yawDegreesPerTick() > 0.18f);
		assertTrue(held.yawDegreesPerTick() < 0.26f);
	}

	@Test
	void angleModeTreatsMediumGamepadInputAsTrainingControl() {
		float mediumStick = (float) ControlStickProfile.gamepadCommand(0.60, 0.10);
		PlayableFlightModel.Step held = holdStick(FlightMode.ANGLE, 16, 0.42f, mediumStick, -mediumStick, mediumStick);

		assertTrue(Math.abs(held.pitchRadians()) > Math.toRadians(6.0));
		assertTrue(Math.abs(held.pitchRadians()) < Math.toRadians(7.8));
		assertTrue(Math.abs(held.rollRadians()) > Math.toRadians(6.0));
		assertTrue(Math.abs(held.rollRadians()) < Math.toRadians(7.8));
		assertTrue(Math.abs(held.targetVelocityX()) > 0.45f);
		assertTrue(Math.abs(held.targetVelocityX()) < 1.10f);
		assertTrue(Math.abs(held.targetVelocityZ()) > 0.45f);
		assertTrue(Math.abs(held.targetVelocityZ()) < 1.10f);
		assertTrue(held.yawDegreesPerTick() > 0.50f);
		assertTrue(held.yawDegreesPerTick() < 0.65f);
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

		assertTrue(first.yawDegreesPerTick() > 1.0f);
		assertTrue(first.yawDegreesPerTick() < 1.25f);
		assertTrue(held.yawDegreesPerTick() > 1.65f);
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
		assertTrue(Math.abs(released.velocityX()) < 0.10f, "releasedVelocityX=" + released.velocityX());
		assertTrue(Math.abs(released.velocityZ()) < 0.10f, "releasedVelocityZ=" + released.velocityZ());
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
		assertTrue(Math.abs(groundHover.velocityX()) < 0.18f);
		assertTrue(Math.abs(groundHover.velocityZ()) < 0.18f);
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
		assertTrue(step.targetVelocityZ() > 0.25f);
		assertTrue(step.velocityZ() > 0.10f);
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

		assertTrue(guarded.targetVelocityY() >= normal.targetVelocityY() - 1.0e-6f);
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
		assertTrue(descent.targetVelocityY() > -0.90f);
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
		assertTrue(descent.targetVelocityY() > -1.10f);
	}

	@Test
	void playableServerLayerPreservesClientShapedFineStickCommands() {
		float shapedHalfStick = (float) ControlStickProfile.gamepadCommand(0.50, 0.08, 0.60, 0.86);

		assertTrue(shapedHalfStick > 0.20f);
		assertTrue(shapedHalfStick < 0.22f);
		assertEquals(0.0f, PlayableFlightModel.playableAxisCommand(0.004f), 1.0e-6f);
		assertEquals(shapedHalfStick, PlayableFlightModel.playableAxisCommand(shapedHalfStick), 1.0e-6f);
		assertEquals(-shapedHalfStick, PlayableFlightModel.playableAxisCommand(-shapedHalfStick), 1.0e-6f);
	}

	@Test
	void yawRelativeVelocityMappingKeepsPitchAndRollAlignedWithDroneHeading() {
		PlayableFlightModel.Velocity forwardYaw0 = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.25f, 1.0f, 0.0f);
		PlayableFlightModel.Velocity forwardYaw90 = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.25f, 1.0f, 90.0f);
		PlayableFlightModel.Velocity rightYaw90 = PlayableFlightModel.worldVelocityForYaw(1.0f, 0.0f, 0.0f, 90.0f);
		PlayableFlightModel.Velocity forwardYaw180 = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.25f, 1.0f, 180.0f);

		assertEquals(0.0f, forwardYaw0.x(), 1.0e-5f);
		assertEquals(0.25f, forwardYaw0.y(), 1.0e-5f);
		assertEquals(1.0f, forwardYaw0.z(), 1.0e-5f);
		assertEquals(-1.0f, forwardYaw90.x(), 1.0e-5f);
		assertEquals(0.25f, forwardYaw90.y(), 1.0e-5f);
		assertEquals(0.0f, forwardYaw90.z(), 1.0e-5f);
		assertEquals(0.0f, rightYaw90.x(), 1.0e-5f);
		assertEquals(1.0f, rightYaw90.z(), 1.0e-5f);
		assertEquals(0.0f, forwardYaw180.x(), 1.0e-5f);
		assertEquals(0.25f, forwardYaw180.y(), 1.0e-5f);
		assertEquals(-1.0f, forwardYaw180.z(), 1.0e-5f);
	}

	@Test
	void yawRelativeVelocityMappingRoundTripsCollisionAdjustedWorldVelocity() {
		PlayableFlightModel.Velocity world = PlayableFlightModel.worldVelocityForYaw(0.35f, -0.20f, 0.80f, 135.0f);
		PlayableFlightModel.Velocity local = PlayableFlightModel.localVelocityForYaw(world.x(), world.y(), world.z(), 135.0f);

		assertEquals(0.35f, local.x(), 1.0e-5f);
		assertEquals(-0.20f, local.y(), 1.0e-5f);
		assertEquals(0.80f, local.z(), 1.0e-5f);
	}

	@Test
	void yawVelocityReframeKeepsWorldMomentumWhenHeadingChanges() {
		PlayableFlightModel.Velocity localAtYaw90 = PlayableFlightModel.reframeVelocityForYaw(
				0.0f,
				0.25f,
				12.0f,
				0.0f,
				90.0f
		);
		PlayableFlightModel.Velocity worldAfterYaw = PlayableFlightModel.worldVelocityForYaw(
				localAtYaw90.x(),
				localAtYaw90.y(),
				localAtYaw90.z(),
				90.0f
		);

		assertTrue(localAtYaw90.x() > 11.99f, "localX=" + localAtYaw90.x());
		assertEquals(0.25f, localAtYaw90.y(), 1.0e-5f);
		assertEquals(0.0f, localAtYaw90.z(), 1.0e-4f);
		assertEquals(0.0f, worldAfterYaw.x(), 1.0e-4f);
		assertEquals(0.25f, worldAfterYaw.y(), 1.0e-5f);
		assertEquals(12.0f, worldAfterYaw.z(), 1.0e-4f);
	}

	@Test
	void angleModeWithGentleTrainingPresetKeepsMidStickCalm() {
		float mediumStick = (float) ControlStickProfile.gamepadCommand(0.70, 0.10, 1.00, 0.42);
		float fullStick = (float) ControlStickProfile.gamepadCommand(1.0, 0.10, 1.00, 0.42);

		PlayableFlightModel.Step medium = holdStick(FlightMode.ANGLE, 18, 0.42f, mediumStick, -mediumStick, mediumStick);
		PlayableFlightModel.Step full = holdStick(FlightMode.ANGLE, 18, 0.45f, fullStick, -fullStick, fullStick);

		assertTrue(Math.abs(medium.pitchRadians()) < Math.toRadians(3.0));
		assertTrue(Math.abs(medium.rollRadians()) < Math.toRadians(3.0));
		assertTrue(Math.abs(medium.targetVelocityX()) < 0.42f, "mediumTargetX=" + medium.targetVelocityX());
		assertTrue(Math.abs(medium.targetVelocityZ()) < 0.42f, "mediumTargetZ=" + medium.targetVelocityZ());
		assertTrue(medium.yawDegreesPerTick() < 0.26f);
		assertTrue(Math.abs(full.pitchRadians()) > Math.toRadians(3.5));
		assertTrue(Math.abs(full.rollRadians()) > Math.toRadians(3.5));
		assertTrue(Math.abs(full.targetVelocityX()) > 0.15f, "fullTargetX=" + full.targetVelocityX());
		assertTrue(Math.abs(full.targetVelocityZ()) > 0.15f, "fullTargetZ=" + full.targetVelocityZ());
		assertTrue(full.yawDegreesPerTick() > 0.15f);
	}

	@Test
	void defaultTrainingClientCorrectionIsVisibleAndRecenters() {
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

		assertTrue(maxTapPitch > Math.toRadians(6.0), "maxTapPitchDeg=" + Math.toDegrees(maxTapPitch));
		assertTrue(maxTapPitch < Math.toRadians(9.5), "maxTapPitchDeg=" + Math.toDegrees(maxTapPitch));
		assertTrue(maxTapRoll > Math.toRadians(6.0), "maxTapRollDeg=" + Math.toDegrees(maxTapRoll));
		assertTrue(maxTapRoll < Math.toRadians(9.5), "maxTapRollDeg=" + Math.toDegrees(maxTapRoll));
		assertTrue(maxTapYaw > 0.65f, "maxTapYawDegPerTick=" + maxTapYaw);
		assertTrue(maxTapYaw < 0.90f, "maxTapYawDegPerTick=" + maxTapYaw);
		assertTrue(maxTapHorizontalTarget > 0.35f, "maxTapHorizontalTarget=" + maxTapHorizontalTarget);
		assertTrue(maxTapHorizontalTarget < 0.95f, "maxTapHorizontalTarget=" + maxTapHorizontalTarget);
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
		assertTrue(released.pitchRadians() >= held.pitchRadians(), "heldPitch=" + held.pitchRadians() + " releasedPitch=" + released.pitchRadians());
		assertTrue(released.pitchRadians() < held.pitchRadians() + Math.toRadians(3.5), "releasedPitchDeg=" + Math.toDegrees(released.pitchRadians()));
		assertTrue(released.rollRadians() <= held.rollRadians(), "heldRoll=" + held.rollRadians() + " releasedRoll=" + released.rollRadians());
		assertTrue(released.rollRadians() > held.rollRadians() - Math.toRadians(3.5), "releasedRollDeg=" + Math.toDegrees(released.rollRadians()));
		assertEquals(0.0f, released.acroPitchRateRadiansPerTick(), 1.0e-3f);
		assertEquals(0.0f, released.acroRollRateRadiansPerTick(), 1.0e-3f);
		assertTrue(released.targetVelocityZ() > 6.0f);
	}

	@Test
	void acroPitchAndRollRatesRampInsteadOfInstantAttitudeStep() {
		PlayableFlightModel.Step firstTick = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step settled = runFrom(
				FlightMode.ACRO,
				8,
				0.45f,
				1.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);

		assertTrue(firstTick.acroPitchRateRadiansPerTick() > Math.toRadians(3.5), "firstPitchRateDeg=" + Math.toDegrees(firstTick.acroPitchRateRadiansPerTick()));
		assertTrue(firstTick.acroPitchRateRadiansPerTick() < Math.toRadians(7.0), "firstPitchRateDeg=" + Math.toDegrees(firstTick.acroPitchRateRadiansPerTick()));
		assertTrue(firstTick.acroRollRateRadiansPerTick() > Math.toRadians(3.8), "firstRollRateDeg=" + Math.toDegrees(firstTick.acroRollRateRadiansPerTick()));
		assertTrue(firstTick.acroRollRateRadiansPerTick() < Math.toRadians(7.4), "firstRollRateDeg=" + Math.toDegrees(firstTick.acroRollRateRadiansPerTick()));
		assertTrue(settled.acroPitchRateRadiansPerTick() > Math.toRadians(8.4), "settledPitchRateDeg=" + Math.toDegrees(settled.acroPitchRateRadiansPerTick()));
		assertTrue(settled.acroRollRateRadiansPerTick() > Math.toRadians(8.9), "settledRollRateDeg=" + Math.toDegrees(settled.acroRollRateRadiansPerTick()));
		assertTrue(firstTick.pitchRadians() < Math.toRadians(7.0), "firstPitchDeg=" + Math.toDegrees(firstTick.pitchRadians()));
		assertTrue(settled.pitchRadians() > Math.toRadians(58.0), "settledPitchDeg=" + Math.toDegrees(settled.pitchRadians()));
	}

	@Test
	void acroModeAllowsContinuousFullPitchAndRollRotation() {
		PlayableFlightModel.Step pitched = holdStick(FlightMode.ACRO, 48, 0.45f, 1.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step rolled = holdStick(FlightMode.ACRO, 44, 0.45f, 0.0f, 1.0f, 0.0f);

		assertTrue(pitched.pitchRadians() > Math.toRadians(360.0), "pitchDeg=" + Math.toDegrees(pitched.pitchRadians()));
		assertTrue(rolled.rollRadians() > Math.toRadians(360.0), "rollDeg=" + Math.toDegrees(rolled.rollRadians()));
	}

	@Test
	void acroModeCapturesCompletedRotationAfterStickRelease() {
		PlayableFlightModel.Step rolled = holdStick(FlightMode.ACRO, 42, 0.45f, 0.0f, 1.0f, 0.0f);
		PlayableFlightModel.Step released = runFrom(
				FlightMode.ACRO,
				8,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(rolled)
		);

		assertTrue(rolled.rollRadians() > Math.toRadians(360.0), "rolledDeg=" + Math.toDegrees(rolled.rollRadians()));
		assertEquals(Math.toRadians(360.0), released.rollRadians(), 1.0e-5);
		assertTrue(Math.abs(released.targetVelocityX()) < 1.0e-3f, "releasedTargetX=" + released.targetVelocityX());
	}

	@Test
	void completedAcroRollDoesNotKeepCreatingSidewaysTarget() {
		PlayableFlightModel.Step completedRoll = PlayableFlightModel.step(
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
						0.0f,
						(float) Math.toRadians(360.0),
						0.0f,
						FlightMode.ACRO
				)
		);
		PlayableFlightModel.Step quarterRoll = PlayableFlightModel.step(
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
						0.0f,
						(float) Math.toRadians(90.0),
						0.0f,
						FlightMode.ACRO
				)
		);

		assertTrue(Math.abs(completedRoll.targetVelocityX()) < 1.0e-3f, "completedRollTargetX=" + completedRoll.targetVelocityX());
		assertEquals(0.0f, completedRoll.velocityX(), 1.0e-3f);
		assertTrue(Math.abs(quarterRoll.targetVelocityX()) > 24.0f, "quarterRollTargetX=" + quarterRoll.targetVelocityX());
	}

	@Test
	void releasedAcroRollOvershootSnapsToCompletedRotation() {
		PlayableFlightModel.Step overshotRoll = PlayableFlightModel.step(
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
						0.0f,
						(float) Math.toRadians(395.0),
						0.0f,
						FlightMode.ACRO
				)
		);
		PlayableFlightModel.Step activeBank = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.35f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(450.0),
						0.0f,
						FlightMode.ACRO
				)
		);

		assertEquals(Math.toRadians(360.0), overshotRoll.rollRadians(), 1.0e-5);
		assertTrue(Math.abs(overshotRoll.targetVelocityX()) < 1.0e-3f, "overshotRollTargetX=" + overshotRoll.targetVelocityX());
		assertEquals(0.0f, overshotRoll.velocityX(), 1.0e-3f);
		assertTrue(Math.abs(activeBank.targetVelocityX()) > 24.0f, "activeBankTargetX=" + activeBank.targetVelocityX());
	}

	@Test
	void releasedAcroRollWideOvershootSnapsToCompletedRotation() {
		PlayableFlightModel.Step overshotRoll = PlayableFlightModel.step(
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
						0.0f,
						(float) Math.toRadians(428.0),
						0.0f,
						FlightMode.ACRO
				)
		);
		PlayableFlightModel.Step activeKnifeEdge = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.35f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(450.0),
						0.0f,
						FlightMode.ACRO
				)
		);

		assertEquals(Math.toRadians(360.0), overshotRoll.rollRadians(), 1.0e-5);
		assertTrue(Math.abs(overshotRoll.targetVelocityX()) < 1.0e-3f, "overshotRollTargetX=" + overshotRoll.targetVelocityX());
		assertEquals(0.0f, overshotRoll.velocityX(), 1.0e-3f);
		assertTrue(Math.abs(activeKnifeEdge.targetVelocityX()) > 24.0f, "activeKnifeEdgeTargetX=" + activeKnifeEdge.targetVelocityX());
	}

	@Test
	void releasedAcroRollNearKnifeEdgeCompletesTurnInsteadOfKeepingSideThrust() {
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
						0.0f,
						(float) Math.toRadians(451.0),
						0.0f,
						FlightMode.ACRO
				)
		);

		assertEquals(Math.toRadians(360.0), released.rollRadians(), 1.0e-5);
		assertTrue(Math.abs(released.targetVelocityX()) < 1.0e-3f, "releasedTargetX=" + released.targetVelocityX());
		assertEquals(0.0f, released.velocityX(), 1.0e-3f);
	}

	@Test
	void filteredReleaseTailAfterFullRollDoesNotKeepSideFlying() {
		PlayableFlightModel.Step released = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.10f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						1.35f,
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(480.0),
						0.0f,
						FlightMode.ACRO,
						0,
						1.70f,
						0.0f,
						(float) Math.toRadians(4.0)
				)
		);

		assertEquals(Math.toRadians(360.0), released.rollRadians(), 1.0e-5);
		assertEquals(0.0f, released.acroRollRateRadiansPerTick(), 1.0e-6f);
		assertTrue(Math.abs(released.targetVelocityX()) < 1.0e-3f, "releasedTargetX=" + released.targetVelocityX());
		assertEquals(0.0f, released.velocityX(), 1.0e-6f);
	}

	@Test
	void completedAcroPitchLoopDoesNotKeepCreatingForwardTarget() {
		PlayableFlightModel.Step completedLoop = PlayableFlightModel.step(
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
						(float) Math.toRadians(360.0),
						0.0f,
						0.0f,
						FlightMode.ACRO
				)
		);
		PlayableFlightModel.Step quarterLoop = PlayableFlightModel.step(
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
						(float) Math.toRadians(90.0),
						0.0f,
						0.0f,
						FlightMode.ACRO
				)
		);

		assertTrue(Math.abs(completedLoop.targetVelocityZ()) < 1.0e-3f, "completedLoopTargetZ=" + completedLoop.targetVelocityZ());
		assertEquals(0.0f, completedLoop.velocityZ(), 1.0e-3f);
		assertTrue(Math.abs(quarterLoop.targetVelocityZ()) > 24.0f, "quarterLoopTargetZ=" + quarterLoop.targetVelocityZ());
	}

	@Test
	void releasedAcroPitchLoopOvershootSnapsToCompletedRotation() {
		PlayableFlightModel.Step overshotLoop = PlayableFlightModel.step(
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
						(float) Math.toRadians(394.0),
						0.0f,
						0.0f,
						FlightMode.ACRO
				)
		);
		PlayableFlightModel.Step activeSteepLoop = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.35f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(450.0),
						0.0f,
						0.0f,
						FlightMode.ACRO
				)
		);

		assertEquals(Math.toRadians(360.0), overshotLoop.pitchRadians(), 1.0e-5);
		assertTrue(Math.abs(overshotLoop.targetVelocityZ()) < 1.0e-3f, "overshotLoopTargetZ=" + overshotLoop.targetVelocityZ());
		assertEquals(0.0f, overshotLoop.velocityZ(), 1.0e-3f);
		assertTrue(Math.abs(activeSteepLoop.targetVelocityZ()) > 24.0f, "activeSteepLoopTargetZ=" + activeSteepLoop.targetVelocityZ());
	}

	@Test
	void releasedAcroPitchWideOvershootSnapsToCompletedRotation() {
		PlayableFlightModel.Step overshotLoop = PlayableFlightModel.step(
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
						(float) Math.toRadians(425.0),
						0.0f,
						0.0f,
						FlightMode.ACRO
				)
		);
		PlayableFlightModel.Step activeSteepLoop = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.35f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(450.0),
						0.0f,
						0.0f,
						FlightMode.ACRO
				)
		);

		assertEquals(Math.toRadians(360.0), overshotLoop.pitchRadians(), 1.0e-5);
		assertTrue(Math.abs(overshotLoop.targetVelocityZ()) < 1.0e-3f, "overshotLoopTargetZ=" + overshotLoop.targetVelocityZ());
		assertEquals(0.0f, overshotLoop.velocityZ(), 1.0e-3f);
		assertTrue(Math.abs(activeSteepLoop.targetVelocityZ()) > 24.0f, "activeSteepLoopTargetZ=" + activeSteepLoop.targetVelocityZ());
	}

	@Test
	void filteredReleaseTailAfterFullPitchLoopDoesNotKeepForwardThrust() {
		PlayableFlightModel.Step released = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.10f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						1.35f,
						(float) Math.toRadians(480.0),
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.70f,
						(float) Math.toRadians(4.0),
						0.0f
				)
		);

		assertEquals(Math.toRadians(360.0), released.pitchRadians(), 1.0e-5);
		assertEquals(0.0f, released.acroPitchRateRadiansPerTick(), 1.0e-6f);
		assertTrue(Math.abs(released.targetVelocityZ()) < 1.0e-3f, "releasedTargetZ=" + released.targetVelocityZ());
		assertEquals(0.0f, released.velocityZ(), 1.0e-6f);
	}

	@Test
	void acroDiagonalCommandUsesSingleHorizontalSpeedEnvelope() {
		PlayableFlightModel.Step diagonal = holdStick(FlightMode.ACRO, 9, 0.68f, 1.0f, 1.0f, 0.0f);
		float horizontalTargetSpeed = horizontalSpeed(diagonal.targetVelocityX(), diagonal.targetVelocityZ());

		assertTrue(Math.abs(diagonal.targetVelocityX()) > 24.0f, "targetX=" + diagonal.targetVelocityX());
		assertTrue(Math.abs(diagonal.targetVelocityZ()) > 4.5f, "targetZ=" + diagonal.targetVelocityZ());
		assertTrue(Math.abs(diagonal.targetVelocityX()) > Math.abs(diagonal.targetVelocityZ()) * 2.35f);
		assertTrue(horizontalTargetSpeed > 25.0f, "horizontalTargetSpeed=" + horizontalTargetSpeed);
		assertTrue(horizontalTargetSpeed < 27.6f, "horizontalTargetSpeed=" + horizontalTargetSpeed);
	}

	@Test
	void acroMidDiagonalUsesBodyUpThrustAxisInsteadOfPlanarAxisSum() {
		PlayableFlightModel.Step diagonal = PlayableFlightModel.step(
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
						(float) Math.toRadians(45.0),
						(float) Math.toRadians(45.0),
						0.0f,
						FlightMode.ACRO
				)
		);
		float horizontalTargetSpeed = horizontalSpeed(diagonal.targetVelocityX(), diagonal.targetVelocityZ());

		assertTrue(diagonal.targetVelocityX() < -17.0f, "targetX=" + diagonal.targetVelocityX());
		assertTrue(diagonal.targetVelocityZ() > 14.0f, "targetZ=" + diagonal.targetVelocityZ());
		assertTrue(Math.abs(diagonal.targetVelocityX()) > Math.abs(diagonal.targetVelocityZ()) * 1.35f);
		assertTrue(horizontalTargetSpeed > 25.0f, "horizontalTargetSpeed=" + horizontalTargetSpeed);
		assertTrue(horizontalTargetSpeed < 26.1f, "horizontalTargetSpeed=" + horizontalTargetSpeed);
	}

	@Test
	void acroMidDiagonalAcceleratesAlongCoupledThrustCone() {
		PlayableFlightModel.Step diagonal = PlayableFlightModel.step(
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
						(float) Math.toRadians(45.0),
						(float) Math.toRadians(45.0),
						0.0f,
						FlightMode.ACRO
				)
		);

		assertTrue(diagonal.velocityX() < -0.40f, "velocityX=" + diagonal.velocityX());
		assertTrue(diagonal.velocityZ() > 0.40f, "velocityZ=" + diagonal.velocityZ());
		assertTrue(Math.abs(diagonal.velocityX()) > Math.abs(diagonal.velocityZ()) * 1.35f);
		assertTrue(diagonal.velocityY() < -0.03f, "velocityY=" + diagonal.velocityY());
		assertTrue(diagonal.velocityY() > -0.14f, "velocityY=" + diagonal.velocityY());
	}

	@Test
	void acroBodyFrameVelocityRoundTripsAfterFullRollOffset() {
		float pitchRadians = (float) Math.toRadians(37.0);
		float rollRadians = (float) Math.toRadians(428.0);
		PlayableFlightModel.Velocity bodyVelocity = PlayableFlightModel.acroBodyVelocityForYawLocal(
				5.5f,
				-1.25f,
				18.0f,
				pitchRadians,
				rollRadians
		);
		PlayableFlightModel.Velocity yawLocalVelocity = PlayableFlightModel.yawLocalVelocityForAcroBody(
				bodyVelocity.x(),
				bodyVelocity.y(),
				bodyVelocity.z(),
				pitchRadians,
				rollRadians
		);

		assertEquals(5.5f, yawLocalVelocity.x(), 1.0e-4f);
		assertEquals(-1.25f, yawLocalVelocity.y(), 1.0e-4f);
		assertEquals(18.0f, yawLocalVelocity.z(), 1.0e-4f);
	}

	@Test
	void acroCruiseCanReachFpvSpeedWithoutInstantVelocitySnap() {
		PlayableFlightModel.Step firstTick = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step pitched = holdStick(FlightMode.ACRO, 7, 0.68f, 1.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step cruise = runFrom(
				FlightMode.ACRO,
				75,
				0.68f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(pitched)
		);

		assertTrue(firstTick.targetVelocityZ() > 2.2f, "firstTargetZ=" + firstTick.targetVelocityZ());
		assertTrue(firstTick.targetVelocityZ() < 5.0f, "firstTargetZ=" + firstTick.targetVelocityZ());
		assertTrue(firstTick.velocityZ() < 0.90f, "firstVelocityZ=" + firstTick.velocityZ());
		assertTrue(cruise.pitchRadians() >= pitched.pitchRadians(), "pitchedDeg=" + Math.toDegrees(pitched.pitchRadians()) + " cruiseDeg=" + Math.toDegrees(cruise.pitchRadians()));
		assertTrue(cruise.pitchRadians() < pitched.pitchRadians() + Math.toRadians(4.5), "cruiseDeg=" + Math.toDegrees(cruise.pitchRadians()));
		assertTrue(cruise.targetVelocityZ() >= 25.0f, "cruiseTargetZ=" + cruise.targetVelocityZ());
		assertTrue(cruise.velocityZ() >= 25.0f, "cruiseVelocityZ=" + cruise.velocityZ());
	}

	@Test
	void acroCollectiveThrustRespondsWithMotorLagInsteadOfInstantStep() {
		PlayableFlightModel.Step firstTick = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step secondTick = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(firstTick)
		);

		assertTrue(firstTick.acroCollectiveThrustToWeight() > 1.20f, "firstThrustToWeight=" + firstTick.acroCollectiveThrustToWeight());
		assertTrue(firstTick.acroCollectiveThrustToWeight() < 1.60f, "firstThrustToWeight=" + firstTick.acroCollectiveThrustToWeight());
		assertTrue(secondTick.acroCollectiveThrustToWeight() > firstTick.acroCollectiveThrustToWeight(), "secondThrustToWeight=" + secondTick.acroCollectiveThrustToWeight());
		assertTrue(secondTick.acroCollectiveThrustToWeight() < 2.20f, "secondThrustToWeight=" + secondTick.acroCollectiveThrustToWeight());
	}

	@Test
	void acroCenteredSticksCoastWithAerodynamicDragInsteadOfVelocityTargetBrake() {
		PlayableFlightModel.Step released = runFrom(
				FlightMode.ACRO,
				20,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(0.0f, 0.0f, 18.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO)
		);

		assertEquals(0.0f, released.targetVelocityZ(), 1.0e-6f);
		assertTrue(released.velocityZ() > 10.0f, "releasedVelocityZ=" + released.velocityZ());
		assertTrue(released.velocityZ() < 16.5f, "releasedVelocityZ=" + released.velocityZ());
	}

	@Test
	void acroHighSpeedCoastdownMatchesFiveInchDragEnvelope() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO
		);
		PlayableFlightModel.Step step = null;
		float distanceMeters = 0.0f;
		float speedAfterOneSecond = Float.NaN;
		float distanceAfterOneSecond = Float.NaN;
		for (int tick = 1; tick <= 40; tick++) {
			step = PlayableFlightModel.step(
					FlightMode.ACRO,
					0.45f,
					0.0f,
					0.0f,
					0.0f,
					0.20f,
					false,
					state
			);
			distanceMeters += step.velocityZ() * 0.05f;
			state = stateFrom(step);
			if (tick == 20) {
				speedAfterOneSecond = step.velocityZ();
				distanceAfterOneSecond = distanceMeters;
			}
		}

		assertTrue(speedAfterOneSecond > 15.8f, "speedAfterOneSecond=" + speedAfterOneSecond);
		assertTrue(speedAfterOneSecond < 16.8f, "speedAfterOneSecond=" + speedAfterOneSecond);
		assertTrue(distanceAfterOneSecond > 19.0f, "distanceAfterOneSecond=" + distanceAfterOneSecond);
		assertTrue(distanceAfterOneSecond < 20.8f, "distanceAfterOneSecond=" + distanceAfterOneSecond);
		assertTrue(step != null && step.velocityZ() > 11.0f, "speedAfterTwoSeconds=" + (step == null ? Float.NaN : step.velocityZ()));
		assertTrue(step != null && step.velocityZ() < 12.2f, "speedAfterTwoSeconds=" + (step == null ? Float.NaN : step.velocityZ()));
		assertTrue(distanceMeters > 32.0f, "distanceAfterTwoSeconds=" + distanceMeters);
		assertTrue(distanceMeters < 34.8f, "distanceAfterTwoSeconds=" + distanceMeters);
	}

	@Test
	void centeredSticksKeepMomentumInsteadOfZeroingVelocity() {
		PlayableFlightModel.Step released = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(0.0f, 0.0f, 18.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO)
		);

		assertEquals(0.0f, released.targetVelocityZ(), 1.0e-6f);
		assertTrue(released.velocityZ() > 17.0f, "releasedVelocityZ=" + released.velocityZ());
		assertTrue(released.velocityZ() < 18.0f, "releasedVelocityZ=" + released.velocityZ());
	}

	@Test
	void zeroThrottleFallsTenMetersAtGravityPace() {
		PlayableFlightModel.State state = PlayableFlightModel.State.zero(FlightMode.ACRO);
		float fallMeters = 0.0f;
		PlayableFlightModel.Step step = null;
		int ticks = 0;
		while (fallMeters < 10.0f && ticks < 80) {
			step = PlayableFlightModel.step(
					FlightMode.ACRO,
					0.0f,
					0.0f,
					0.0f,
					0.0f,
					0.20f,
					false,
					state
			);
			fallMeters += -step.velocityY() / 20.0f;
			state = stateFrom(step);
			ticks++;
		}

		assertTrue(ticks <= 45, "ticks=" + ticks + " fallMeters=" + fallMeters);
		assertTrue(step != null && step.velocityY() < -8.0f, "velocityY=" + (step == null ? Float.NaN : step.velocityY()));
	}

	@Test
	void zeroThrottleDoesNotCreateHorizontalThrustFromHeldAttitude() {
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(95.0),
						(float) Math.toRadians(35.0),
						0.0f,
						FlightMode.ACRO
				)
		);

		assertEquals(0.0f, step.targetVelocityX(), 1.0e-6f);
		assertEquals(0.0f, step.targetVelocityZ(), 1.0e-6f);
		assertEquals(0.0f, step.velocityX(), 1.0e-6f);
		assertEquals(0.0f, step.velocityZ(), 1.0e-6f);
		assertTrue(step.targetVelocityY() < -12.0f, "targetVelocityY=" + step.targetVelocityY());
	}

	@Test
	void verticalThrustProjectionFollowsPitchAndRollAttitude() {
		assertEquals(1.0f, PlayableFlightModel.verticalThrustProjection(0.0f, 0.0f), 1.0e-6f);
		assertTrue(PlayableFlightModel.verticalThrustProjection((float) Math.toRadians(80.0), 0.0f) < 0.20f);
		assertTrue(PlayableFlightModel.verticalThrustProjection((float) Math.toRadians(115.0), 0.0f) < 0.0f);
		assertTrue(PlayableFlightModel.verticalThrustProjection(0.0f, (float) Math.toRadians(80.0)) < 0.20f);
	}

	@Test
	void steepAcroPitchTurnsThrottleIntoForwardFlightInsteadOfWorldVerticalClimb() {
		PlayableFlightModel.Step level = holdStick(FlightMode.ACRO, 16, 0.65f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step steep = holdStick(FlightMode.ACRO, 16, 0.65f, 1.0f, 0.0f, 0.0f);

		assertTrue(level.targetVelocityY() > 2.0f, "levelTargetY=" + level.targetVelocityY());
		assertTrue(steep.pitchRadians() > Math.toRadians(100.0), "steepPitchDeg=" + Math.toDegrees(steep.pitchRadians()));
		assertTrue(steep.targetVelocityY() < 0.0f, "steepTargetY=" + steep.targetVelocityY());
		assertTrue(steep.velocityY() < 0.0f, "steepVelocityY=" + steep.velocityY());
		assertTrue(steep.targetVelocityZ() > 10.0f, "steepTargetZ=" + steep.targetVelocityZ());
	}

	@Test
	void tiltedAcroHoverLosesVerticalLift() {
		PlayableFlightModel.Step level = holdStick(FlightMode.ACRO, 12, 0.20f, 0.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step tilted = holdStick(FlightMode.ACRO, 12, 0.20f, 1.0f, 0.0f, 0.0f);

		assertEquals(0.0f, level.targetVelocityY(), 1.0e-6f);
		assertTrue(tilted.pitchRadians() > Math.toRadians(90.0), "tiltedPitchDeg=" + Math.toDegrees(tilted.pitchRadians()));
		assertTrue(tilted.targetVelocityY() < -1.0f, "tiltedTargetY=" + tilted.targetVelocityY());
		assertTrue(tilted.targetVelocityZ() > 10.0f, "tiltedTargetZ=" + tilted.targetVelocityZ());
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

		assertTrue(Math.abs(midStick.pitchRadians()) > Math.toRadians(80.0), "midPitchDeg=" + Math.toDegrees(midStick.pitchRadians()));
		assertTrue(Math.abs(midStick.pitchRadians()) < Math.toRadians(110.0), "midPitchDeg=" + Math.toDegrees(midStick.pitchRadians()));
		assertTrue(Math.abs(midStick.rollRadians()) > Math.toRadians(86.0), "midRollDeg=" + Math.toDegrees(midStick.rollRadians()));
		assertTrue(Math.abs(midStick.rollRadians()) < Math.toRadians(118.0), "midRollDeg=" + Math.toDegrees(midStick.rollRadians()));
		assertTrue(midStick.yawDegreesPerTick() < 2.85f, "midYawDegPerTick=" + midStick.yawDegreesPerTick());
		assertTrue(Math.abs(fullStick.pitchRadians()) > Math.toRadians(105.0), "fullPitchDeg=" + Math.toDegrees(fullStick.pitchRadians()));
		assertTrue(Math.abs(fullStick.rollRadians()) > Math.toRadians(115.0), "fullRollDeg=" + Math.toDegrees(fullStick.rollRadians()));
		assertTrue(fullStick.yawDegreesPerTick() > 4.5f, "fullYawDegPerTick=" + fullStick.yawDegreesPerTick());
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
		assertTrue(Math.abs(angle.pitchRadians()) < Math.toRadians(18.0));
		assertTrue(Math.abs(angle.rollRadians()) < Math.toRadians(18.0));
		assertTrue(Math.abs(angle.yawDegreesPerTick()) < 0.45f);
		assertTrue(Math.abs(angle.targetVelocityX()) < 2.70f);
		assertTrue(Math.abs(angle.targetVelocityZ()) < 2.70f);
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
		assertTrue(Math.abs(captured.velocityX()) < 7.0f);
		assertTrue(Math.abs(captured.velocityZ()) < 7.0f);
		assertTrue(Math.abs(captured.yawDegreesPerTick()) < Math.abs(firstAcro.yawDegreesPerTick()));
		assertTrue(Math.abs(captured.pitchRadians()) > Math.toRadians(18.0));
		assertTrue(Math.abs(captured.rollRadians()) > Math.toRadians(18.0));
		assertEquals(captured.pitchRadians(), heldAfterCapture.pitchRadians(), 1.0e-6f);
		assertEquals(captured.rollRadians(), heldAfterCapture.rollRadians(), 1.0e-6f);
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
				step.modeSwitchTicksRemaining(),
				step.acroCollectiveThrustToWeight(),
				step.acroPitchRateRadiansPerTick(),
				step.acroRollRateRadiansPerTick()
		);
	}

	private static float horizontalSpeed(float x, float z) {
		return (float) Math.sqrt(x * x + z * z);
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
