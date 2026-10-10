package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.sim.ControlStickProfile;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

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
		assertTrue(Math.abs(released.pitchRadians() - held.pitchRadians()) < Math.toRadians(3.5),
				"heldPitch=" + held.pitchRadians() + " releasedPitch=" + released.pitchRadians());
		assertTrue(Math.abs(released.rollRadians() - held.rollRadians()) < Math.toRadians(6.5),
				"heldRoll=" + held.rollRadians() + " releasedRoll=" + released.rollRadians());
		assertEquals(0.0f, released.acroPitchRateRadiansPerTick(), 1.0e-3f);
		assertEquals(0.0f, released.acroRollRateRadiansPerTick(), 1.0e-3f);
		assertTrue(horizontalSpeed(released.targetVelocityX(), released.targetVelocityZ()) > 6.0f,
				"targetX=" + released.targetVelocityX() + " targetZ=" + released.targetVelocityZ());
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
		assertTrue(settled.pitchRadians() > Math.toRadians(38.0), "settledPitchDeg=" + Math.toDegrees(settled.pitchRadians()));
		assertTrue(settled.pitchRadians() < Math.toRadians(52.0), "settledPitchDeg=" + Math.toDegrees(settled.pitchRadians()));
	}

	@Test
	void acroPhysicalAccelerationUsesMidpointAttitudeDuringFastRates() {
		float currentPitch = (float) Math.toRadians(18.0);
		float currentRoll = (float) Math.toRadians(-12.0);
		float pitchRate = (float) Math.toRadians(6.0);
		float rollRate = (float) Math.toRadians(-4.0);

		assertEquals(
				(float) Math.toRadians(15.0),
				PlayableFlightModel.acroMidpointIntegrationAttitudeRadians(currentPitch, pitchRate),
				1.0e-6f
		);
		assertEquals(
				(float) Math.toRadians(-10.0),
				PlayableFlightModel.acroMidpointIntegrationAttitudeRadians(currentRoll, rollRate),
				1.0e-6f
		);
		assertEquals(
				currentPitch,
				PlayableFlightModel.acroMidpointIntegrationAttitudeRadians(currentPitch, 0.0f),
				1.0e-6f
		);
	}

	@Test
	void acroCrossflowLagUsesCurrentPitchAttitudeDuringFastAoaEntry() {
		PlayableFlightModel.Step first = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.65f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						25.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f,
						0,
						0.0f,
						0.0f
				)
		);
		PlayableFlightModel.Step second = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.65f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(first)
		);

		assertTrue(first.pitchRadians() < Math.toRadians(6.0), "firstPitchDeg=" + Math.toDegrees(first.pitchRadians()));
		assertEquals(0.0f, first.acroAeroCrossflowLag(), 1.0e-6f);
		assertTrue(second.pitchRadians() > Math.toRadians(10.0), "secondPitchDeg=" + Math.toDegrees(second.pitchRadians()));
		assertTrue(second.acroAeroCrossflowLag() > 0.004f,
				"secondLag=" + second.acroAeroCrossflowLag() + " secondPitchDeg=" + Math.toDegrees(second.pitchRadians()));
		assertTrue(second.acroAeroCrossflowLag() < 0.020f,
				"secondLag=" + second.acroAeroCrossflowLag() + " secondPitchDeg=" + Math.toDegrees(second.pitchRadians()));
	}

	@Test
	void acroModeAllowsContinuousFullPitchAndRollRotation() {
		PlayableFlightModel.Step pitched = holdStick(FlightMode.ACRO, 48, 0.45f, 1.0f, 0.0f, 0.0f);
		PlayableFlightModel.Step rolled = holdStick(FlightMode.ACRO, 44, 0.45f, 0.0f, 1.0f, 0.0f);

		assertTrue(pitched.pitchRadians() > Math.toRadians(360.0), "pitchDeg=" + Math.toDegrees(pitched.pitchRadians()));
		assertTrue(rolled.rollRadians() > Math.toRadians(360.0), "rollDeg=" + Math.toDegrees(rolled.rollRadians()));
	}

	@Test
	void completedAcroTurnsKeepTheirResidualAttitudeOnRelease() {
		// The old 250/300-degree capture threshold must not turn any of these
		// released attitudes into an automatic level command.
		for (PlayableFlightPreset preset : PlayableFlightPreset.values()) {
			for (float sign : new float[] { -1.0f, 1.0f }) {
				for (float degrees : new float[] { 264.0f, 300.0f, 330.0f, 390.0f, 428.0f, 451.0f, 540.0f, 720.0f }) {
					for (boolean pitchAxis : new boolean[] { true, false }) {
						float attitude = (float) Math.toRadians(sign * degrees);
						PlayableFlightModel.State previous = acroTurnState(
								pitchAxis ? attitude : 0.0f, pitchAxis ? 0.0f : attitude,
								0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0
						);
						PlayableFlightModel.Step released = acroTurnStep(preset, previous, 0.0f, 0.0f);
						String scenario = preset + " angle=" + sign * degrees + " pitchAxis=" + pitchAxis;
						assertEquals(previous.pitchRadians(), released.pitchRadians(), 1.0e-5f, scenario);
						assertEquals(previous.rollRadians(), released.rollRadians(), 1.0e-5f, scenario);
						assertEquals(0, released.acroRollRecoveryTicksRemaining(), scenario);
					}
				}
			}
		}
	}

	@Test
	void acroTurnReleaseBrakesRatesSmoothlyWithoutJumpingToLevel() {
		for (PlayableFlightPreset preset : PlayableFlightPreset.values()) {
			for (float sign : new float[] { -1.0f, 1.0f }) {
				for (float degrees : new float[] { 300.0f, 330.0f, 390.0f }) {
					for (boolean pitchAxis : new boolean[] { true, false }) {
						float attitude = (float) Math.toRadians(sign * degrees);
						float initialRate = (float) Math.toRadians(sign * 5.0f);
						PlayableFlightModel.State state = acroTurnState(
								pitchAxis ? attitude : 0.0f, pitchAxis ? 0.0f : attitude,
								0.0f, 0.0f, 0.0f,
								pitchAxis ? initialRate : 0.0f, pitchAxis ? 0.0f : initialRate, 0
						);
						for (int tick = 0; tick < 8; tick++) {
							PlayableFlightModel.Step released = acroTurnStep(preset, state, 0.0f, 0.0f);
							float previousAttitude = pitchAxis ? state.pitchRadians() : state.rollRadians();
							float nextAttitude = pitchAxis ? released.pitchRadians() : released.rollRadians();
							float rate = pitchAxis ? released.acroPitchRateRadiansPerTick() : released.acroRollRateRadiansPerTick();
							String scenario = preset + " angle=" + sign * degrees + " pitchAxis=" + pitchAxis + " tick=" + tick;
							assertTrue(Math.abs(nextAttitude - previousAttitude) < Math.toRadians(2.0), scenario);
							assertTrue(Math.abs(nextAttitude - attitude) < Math.toRadians(3.0), scenario);
							assertTrue(Math.abs(rate) < Math.abs(initialRate), scenario);
							assertEquals(0, released.acroRollRecoveryTicksRemaining(), scenario);
							state = stateFrom(released);
						}
						float rate = pitchAxis ? state.acroPitchRateRadiansPerTick() : state.acroRollRateRadiansPerTick();
						assertEquals(0.0f, rate, (float) Math.toRadians(0.04));
					}
				}
			}
		}
	}

	@Test
	void smallAcroCorrectionsRemainUsableAfterSignedFullTurns() {
		for (PlayableFlightPreset preset : PlayableFlightPreset.values()) {
			for (float degrees : new float[] { -720.0f, -360.0f, 360.0f, 720.0f }) {
				for (float command : new float[] { -0.10f, 0.10f }) {
					for (boolean pitchAxis : new boolean[] { true, false }) {
						float attitude = (float) Math.toRadians(degrees);
						PlayableFlightModel.State previous = acroTurnState(
								pitchAxis ? attitude : 0.0f, pitchAxis ? 0.0f : attitude,
								0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 28
						);
						PlayableFlightModel.Step corrected = acroTurnStep(
								preset, previous, pitchAxis ? command : 0.0f, pitchAxis ? 0.0f : command
						);
						float delta = pitchAxis
								? corrected.pitchRadians() - previous.pitchRadians()
								: corrected.rollRadians() - previous.rollRadians();
						float rate = pitchAxis ? corrected.acroPitchRateRadiansPerTick() : corrected.acroRollRateRadiansPerTick();
						String scenario = preset + " angle=" + degrees + " command=" + command + " pitchAxis=" + pitchAxis;
						assertTrue(command * delta > 0.0f, scenario);
						assertTrue(command * rate > 0.0f, scenario);
						assertTrue(Math.abs(delta) < Math.toRadians(3.0), scenario);
						assertEquals(0, corrected.acroRollRecoveryTicksRemaining(), scenario);
					}
				}
			}
		}
	}

	@Test
	void signedFullTurnOffsetsPreserveMixedBodyAxisControlAndVelocity() {
		for (PlayableFlightPreset preset : PlayableFlightPreset.values()) {
			for (float pitchDegrees : new float[] { -115.0f, -37.0f, 0.0f, 37.0f, 115.0f }) {
				for (float rollDegrees : new float[] { -68.0f, 0.0f, 68.0f }) {
					for (float[] commands : new float[][] { { 0.10f, 0.0f }, { -0.10f, 0.0f }, { 0.0f, 0.10f }, { 0.0f, -0.10f }, { 0.30f, -0.25f } }) {
						PlayableFlightModel.State base = acroTurnState(
								(float) Math.toRadians(pitchDegrees), (float) Math.toRadians(rollDegrees),
								12.0f, -2.0f, 6.0f, 0.0f, 0.0f, 0
						);
						PlayableFlightModel.Step expected = acroTurnStep(preset, base, commands[0], commands[1]);
						for (float pitchTurns : new float[] { -720.0f, -360.0f, 0.0f, 360.0f, 720.0f }) {
							for (float rollTurns : new float[] { -720.0f, -360.0f, 0.0f, 360.0f, 720.0f }) {
								PlayableFlightModel.State turned = acroTurnState(
										(float) Math.toRadians(pitchDegrees + pitchTurns), (float) Math.toRadians(rollDegrees + rollTurns),
										12.0f, -2.0f, 6.0f, 0.0f, 0.0f, 0
								);
								PlayableFlightModel.Step actual = acroTurnStep(preset, turned, commands[0], commands[1]);
								String scenario = preset + " pitch=" + pitchDegrees + "/" + pitchTurns + " roll=" + rollDegrees + "/" + rollTurns;
								assertEquals(expected.pitchRadians() - base.pitchRadians(), actual.pitchRadians() - turned.pitchRadians(), 3.0e-5f, scenario);
								assertEquals(expected.rollRadians() - base.rollRadians(), actual.rollRadians() - turned.rollRadians(), 3.0e-5f, scenario);
								assertEquals(expected.acroPitchRateRadiansPerTick(), actual.acroPitchRateRadiansPerTick(), 2.0e-5f, scenario);
								assertEquals(expected.acroRollRateRadiansPerTick(), actual.acroRollRateRadiansPerTick(), 2.0e-5f, scenario);
								assertEquals(expected.velocityX(), actual.velocityX(), 2.0e-4f, scenario);
								assertEquals(expected.velocityY(), actual.velocityY(), 2.0e-4f, scenario);
								assertEquals(expected.velocityZ(), actual.velocityZ(), 2.0e-4f, scenario);
								assertEquals(expected.yawDegreesPerTick(), actual.yawDegreesPerTick(), 2.0e-4f, scenario);
							}
						}
					}
				}
			}
		}
	}

	@Test
	void completingAcroTurnsDoesNotTrimMomentumOrResetAeroMemory() {
		for (PlayableFlightPreset preset : PlayableFlightPreset.values()) {
			for (float[] angles : new float[][] { { 0.0f, 35.0f }, { 34.0f, 0.0f }, { 34.0f, 35.0f } }) {
				PlayableFlightModel.State base = acroTurnState(
						(float) Math.toRadians(angles[0]), (float) Math.toRadians(angles[1]),
						14.0f, -2.0f, 6.0f, 0.0f, 0.0f, 0
				);
				PlayableFlightModel.State turned = acroTurnState(
						(float) Math.toRadians(angles[0] + 360.0f), (float) Math.toRadians(angles[1] + 360.0f),
						14.0f, -2.0f, 6.0f, 0.0f, 0.0f, 28
				);
				PlayableFlightModel.Step expected = acroTurnStep(preset, base, 0.0f, 0.0f);
				PlayableFlightModel.Step actual = acroTurnStep(preset, turned, 0.0f, 0.0f);
				assertEquals(expected.velocityX(), actual.velocityX(), 2.0e-4f);
				assertEquals(expected.velocityY(), actual.velocityY(), 2.0e-4f);
				assertEquals(expected.velocityZ(), actual.velocityZ(), 2.0e-4f);
				assertEquals(expected.acroAeroCrossflowLag(), actual.acroAeroCrossflowLag(), 1.0e-5f);
				assertEquals(expected.acroSidewashMemory(), actual.acroSidewashMemory(), 1.0e-5f);
				assertTrue(actual.velocityX() > 12.0f, "turn must not erase lateral momentum");
				assertTrue(actual.velocityZ() > 4.0f, "turn must not erase forward momentum");
				assertEquals(0, actual.acroRollRecoveryTicksRemaining());
			}
		}
	}

	@Test
	void exactAcroFullTurnsRemainLevelWithoutChangingStoredRevolution() {
		for (float degrees : new float[] { -720.0f, -360.0f, 360.0f, 720.0f }) {
			float radians = (float) Math.toRadians(degrees);
			PlayableFlightModel.Step loop = acroTurnStep(
					PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
					acroTurnState(radians, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0), 0.0f, 0.0f
			);
			PlayableFlightModel.Step roll = acroTurnStep(
					PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
					acroTurnState(0.0f, radians, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0), 0.0f, 0.0f
			);
			assertEquals(radians, loop.pitchRadians(), 1.0e-5f);
			assertEquals(radians, roll.rollRadians(), 1.0e-5f);
			assertEquals(0.0f, loop.targetVelocityZ(), 1.0e-3f);
			assertEquals(0.0f, roll.targetVelocityX(), 1.0e-3f);
			assertEquals(1.0f, PlayableFlightModel.verticalThrustProjection(loop.pitchRadians(), loop.rollRadians()), 1.0e-5f);
			assertEquals(1.0f, PlayableFlightModel.verticalThrustProjection(roll.pitchRadians(), roll.rollRadians()), 1.0e-5f);
		}
	}

	@Test
	void legacyTurnRecoveryCountersNeverSuppressNewAcroCommands() {
		PlayableFlightModel.State previous = acroTurnState(
				0.0f, (float) Math.toRadians(370.0), 16.0f, 0.0f, 16.0f, 0.0f, 0.0f, 28
		);
		assertEquals(0, previous.acroRollRecoveryTicksRemaining());
		PlayableFlightModel.Step corrected = acroTurnStep(PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE, previous, 0.0f, 0.10f);
		assertTrue(corrected.rollRadians() > previous.rollRadians());
		assertTrue(corrected.acroRollRateRadiansPerTick() > 0.0f);
		assertEquals(0, corrected.acroRollRecoveryTicksRemaining());
	}

	private static PlayableFlightModel.State acroTurnState(
			float pitchRadians, float rollRadians,
			float velocityX, float velocityY, float velocityZ,
			float pitchRate, float rollRate, int retiredRecoveryTicks
	) {
		return new PlayableFlightModel.State(
				velocityX, velocityY, velocityZ, pitchRadians, rollRadians,
				0.0f, FlightMode.ACRO, 0, 1.0f, pitchRate, rollRate,
				retiredRecoveryTicks, 1.0f, 1.0f
		);
	}

	private static PlayableFlightModel.Step acroTurnStep(
			PlayableFlightPreset preset, PlayableFlightModel.State previous, float pitch, float roll
	) {
		return PlayableFlightModel.step(preset, FlightMode.ACRO, 0.20f, pitch, roll, 0.0f, 0.20f, false, 1.0f, previous);
	}

	@Test
	void acroDiagonalCommandUsesSingleHorizontalSpeedEnvelope() {
		PlayableFlightModel.Step diagonal = holdStick(FlightMode.ACRO, 9, 0.68f, 1.0f, 1.0f, 0.0f);
		float horizontalTargetSpeed = horizontalSpeed(diagonal.targetVelocityX(), diagonal.targetVelocityZ());
		Vec3 thrustAxis = PlayableAttitude.quaternion(diagonal.acroHeadingCouplingDegreesPerTick(),
				diagonal.pitchRadians(), diagonal.rollRadians()).rotate(new Vec3(0.0, 1.0, 0.0));
		double horizontalThrust = Math.hypot(thrustAxis.x(), thrustAxis.z());

		assertTrue(horizontalTargetSpeed > 25.0f, "horizontalTargetSpeed=" + horizontalTargetSpeed);
		assertTrue(horizontalTargetSpeed < 27.6f, "horizontalTargetSpeed=" + horizontalTargetSpeed);
		// Diagonal sticks rotate about two body axes; their horizontal projection
		// need not contain the old fixed X/Z ratio. The shared envelope and the
		// physical thrust direction must hold for the resulting complete attitude.
		assertTrue(horizontalThrust > 0.0);
		assertEquals(thrustAxis.x() / horizontalThrust, diagonal.targetVelocityX() / horizontalTargetSpeed, 1.0e-5);
		assertEquals(thrustAxis.z() / horizontalThrust, diagonal.targetVelocityZ() / horizontalTargetSpeed, 1.0e-5);
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
	void acroHighAdvanceFlowSoftensPropThrustAtSpeed() {
		float lowSpeedScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				5.0f,
				(float) Math.toRadians(45.0),
				0.0f,
				0.68f,
				0.20f
		);
		float highSideFlowScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				25.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);
		float diagonalSideFlowScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);
		float noseDownForwardScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				25.0f,
				(float) Math.toRadians(60.0),
				0.0f,
				0.68f,
				0.20f
		);

		assertTrue(lowSpeedScale > 0.99f, "lowSpeedScale=" + lowSpeedScale);
		assertTrue(highSideFlowScale > 0.32f, "highSideFlowScale=" + highSideFlowScale);
		assertTrue(highSideFlowScale < 0.38f, "highSideFlowScale=" + highSideFlowScale);
		assertTrue(diagonalSideFlowScale > highSideFlowScale, "diagonalSideFlowScale=" + diagonalSideFlowScale + " highSideFlowScale=" + highSideFlowScale);
		assertTrue(diagonalSideFlowScale < 0.39f, "diagonalSideFlowScale=" + diagonalSideFlowScale);
		assertTrue(noseDownForwardScale > diagonalSideFlowScale + 0.12f, "noseDownForwardScale=" + noseDownForwardScale + " diagonalSideFlowScale=" + diagonalSideFlowScale);
		assertTrue(noseDownForwardScale < 0.64f, "noseDownForwardScale=" + noseDownForwardScale);
	}

	@Test
	void acroCruiseAdvanceRatioHasVisibleFiveInchPropRolloff() {
		float racingCruiseScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				12.5f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);
		float lowSpeedScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				5.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);

		assertTrue(lowSpeedScale > 0.99f, "lowSpeedScale=" + lowSpeedScale);
		assertTrue(racingCruiseScale > 0.62f, "racingCruiseScale=" + racingCruiseScale);
		assertTrue(racingCruiseScale < 0.69f, "racingCruiseScale=" + racingCruiseScale);
	}

	@Test
	void acroHighAdvanceDiagonalSideflowCarriesNearBroadsidePropLoss() {
		float straightCruiseScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				20.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);
		float diagonalSideflowScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);
		float broadsideScale = PlayableFlightModel.acroAdvanceRatioThrustScale(
				22.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);

		assertTrue(straightCruiseScale > 0.49f, "straightCruiseScale=" + straightCruiseScale);
		assertTrue(diagonalSideflowScale < straightCruiseScale * 0.74f,
				"diagonalSideflowScale=" + diagonalSideflowScale + " straightCruiseScale=" + straightCruiseScale);
		assertTrue(diagonalSideflowScale > broadsideScale, "diagonalSideflowScale=" + diagonalSideflowScale + " broadsideScale=" + broadsideScale);
		assertTrue(diagonalSideflowScale < broadsideScale + 0.020f,
				"diagonalSideflowScale=" + diagonalSideflowScale + " broadsideScale=" + broadsideScale);
	}

	@Test
	void acroAdvanceSideflowExposureSeparatesForwardAndLateralDiskFlow() {
		float straight = PlayableFlightModel.acroAdvanceSideflowExposure(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f)
		);
		float diagonal = PlayableFlightModel.acroAdvanceSideflowExposure(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f)
		);
		float side = PlayableFlightModel.acroAdvanceSideflowExposure(
				new PlayableFlightModel.Velocity(25.0f, 0.0f, 0.0f)
		);

		assertEquals(0.0f, straight, 1.0e-6f);
		assertTrue(diagonal > 0.85f, "diagonal=" + diagonal);
		assertTrue(side > 0.99f, "side=" + side);
	}

	@Test
	void acroTranslationalLiftBoostsCleanMidSpeedFlowWithoutFlatteningSideSlip() {
		float stationary = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 0.0f),
				0.68f,
				0.20f
		);
		float slow = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 2.0f),
				0.68f,
				0.20f
		);
		float midForward = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 12.5f),
				0.68f,
				0.20f
		);
		float diagonal = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(9.0f, 0.0f, 9.0f),
				0.68f,
				0.20f
		);
		float fastForward = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				0.68f,
				0.20f
		);
		float fastSide = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(25.0f, 0.0f, 0.0f),
				0.68f,
				0.20f
		);
		float idleForward = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 12.5f),
				0.0f,
				0.20f
		);

		assertEquals(1.0f, stationary, 1.0e-6f);
		assertEquals(1.0f, slow, 1.0e-6f);
		assertEquals(1.0f, idleForward, 1.0e-6f);
		assertTrue(midForward > 1.045f, "midForward=" + midForward);
		assertTrue(midForward < 1.056f, "midForward=" + midForward);
		assertTrue(diagonal > 1.006f, "diagonal=" + diagonal);
		assertTrue(diagonal < midForward - 0.030f, "diagonal=" + diagonal + " midForward=" + midForward);
		assertTrue(fastForward > 1.005f, "fastForward=" + fastForward);
		assertTrue(fastForward < 1.020f, "fastForward=" + fastForward);
		assertTrue(fastSide > 1.0005f, "fastSide=" + fastSide);
		assertTrue(fastSide < fastForward, "fastSide=" + fastSide + " fastForward=" + fastForward);
	}

	@Test
	void acroTranslationalLiftTreatsSideflowAsDirtyEtL() {
		float cleanForward = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 12.5f),
				0.68f,
				0.20f
		);
		float diagonal = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(9.0f, 0.0f, 9.0f),
				0.68f,
				0.20f
		);
		float side = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(12.5f, 0.0f, 0.0f),
				0.68f,
				0.20f
		);
		float cleanGain = cleanForward - 1.0f;
		float diagonalGain = diagonal - 1.0f;
		float sideGain = side - 1.0f;

		assertTrue(cleanGain > 0.045f, "cleanGain=" + cleanGain);
		assertTrue(diagonalGain < cleanGain * 0.23f,
				"diagonalGain=" + diagonalGain + " cleanGain=" + cleanGain);
		assertTrue(sideGain < diagonalGain, "sideGain=" + sideGain + " diagonalGain=" + diagonalGain);
		assertTrue(sideGain < cleanGain * 0.20f, "sideGain=" + sideGain + " cleanGain=" + cleanGain);
	}

	@Test
	void acroSidewashMemoryDelaysDirtyTranslationalLiftWithoutTouchingCleanFlow() {
		float cleanFresh = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 12.5f),
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		float cleanSettled = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 12.5f),
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		float diagonalFresh = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(9.0f, 0.0f, 9.0f),
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		float diagonalSettled = PlayableFlightModel.acroTranslationalLiftThrustScale(
				new PlayableFlightModel.Velocity(9.0f, 0.0f, 9.0f),
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity diagonalFreshDrag = PlayableFlightModel.acroTranslationalLiftDragBodyAcceleration(
				new PlayableFlightModel.Velocity(9.0f, 0.0f, 9.0f),
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity diagonalSettledDrag = PlayableFlightModel.acroTranslationalLiftDragBodyAcceleration(
				new PlayableFlightModel.Velocity(9.0f, 0.0f, 9.0f),
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		float cleanGain = cleanFresh - 1.0f;
		float freshGain = diagonalFresh - 1.0f;
		float settledGain = diagonalSettled - 1.0f;
		float freshDragMagnitude = horizontalSpeed(diagonalFreshDrag.x(), diagonalFreshDrag.z());
		float settledDragMagnitude = horizontalSpeed(diagonalSettledDrag.x(), diagonalSettledDrag.z());

		assertEquals(cleanSettled, cleanFresh, 1.0e-6f);
		assertTrue(freshGain > settledGain * 3.1f,
				"freshGain=" + freshGain + " settledGain=" + settledGain);
		assertTrue(freshGain < cleanGain * 0.82f,
				"freshGain=" + freshGain + " cleanGain=" + cleanGain);
		assertTrue(settledGain < cleanGain * 0.24f,
				"settledGain=" + settledGain + " cleanGain=" + cleanGain);
		assertTrue(freshDragMagnitude > settledDragMagnitude * 3.1f,
				"freshDrag=" + freshDragMagnitude + " settledDrag=" + settledDragMagnitude);
		assertEquals(diagonalFreshDrag.x(), diagonalFreshDrag.z(), 1.0e-6f);
		assertEquals(diagonalSettledDrag.x(), diagonalSettledDrag.z(), 1.0e-6f);
	}

	@Test
	void acroTranslationalLiftDragCostsEnergyWhenLiftBoostAppears() {
		PlayableFlightModel.Velocity stationary = PlayableFlightModel.acroTranslationalLiftDragBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 0.0f),
				12.5f,
				0.68f,
				0.20f
		);
		PlayableFlightModel.Velocity idleForward = PlayableFlightModel.acroTranslationalLiftDragBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 12.5f),
				12.5f,
				0.0f,
				0.20f
		);
		PlayableFlightModel.Velocity midForward = PlayableFlightModel.acroTranslationalLiftDragBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 12.5f),
				12.5f,
				0.68f,
				0.20f
		);
		PlayableFlightModel.Velocity diagonal = PlayableFlightModel.acroTranslationalLiftDragBodyAcceleration(
				new PlayableFlightModel.Velocity(9.0f, 0.0f, 9.0f),
				12.5f,
				0.68f,
				0.20f
		);
		float midForwardWork = midForward.z() * 12.5f;
		float diagonalWork = diagonal.x() * 9.0f + diagonal.z() * 9.0f;

		assertEquals(0.0f, stationary.x(), 1.0e-6f);
		assertEquals(0.0f, stationary.z(), 1.0e-6f);
		assertEquals(0.0f, idleForward.x(), 1.0e-6f);
		assertEquals(0.0f, idleForward.z(), 1.0e-6f);
		assertEquals(0.0f, midForward.x(), 1.0e-6f);
		assertTrue(midForward.z() < -0.20f, "midForwardZ=" + midForward.z());
		assertTrue(midForward.z() > -0.24f, "midForwardZ=" + midForward.z());
		assertTrue(diagonal.x() < -0.025f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.z() < -0.025f, "diagonalZ=" + diagonal.z());
		assertTrue(horizontalSpeed(diagonal.x(), diagonal.z()) < Math.abs(midForward.z()) * 0.50f,
				"diagonal=" + horizontalSpeed(diagonal.x(), diagonal.z()) + " midForwardZ=" + midForward.z());
		assertTrue(midForwardWork < -2.5f, "midForwardWork=" + midForwardWork);
		assertTrue(diagonalWork < -0.45f, "diagonalWork=" + diagonalWork);
	}

	@Test
	void acroAdvanceRatioUsesFiveInchPropJScale() {
		float racingCruiseJ = PlayableFlightModel.acroRotorAdvanceRatio(
				0.0f,
				0.0f,
				12.5f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);
		float fastSideJ = PlayableFlightModel.acroRotorAdvanceRatio(
				25.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);

		assertTrue(racingCruiseJ > 0.44f, "racingCruiseJ=" + racingCruiseJ);
		assertTrue(racingCruiseJ < 0.46f, "racingCruiseJ=" + racingCruiseJ);
		assertTrue(fastSideJ > 0.89f, "fastSideJ=" + fastSideJ);
		assertTrue(fastSideJ < 0.92f, "fastSideJ=" + fastSideJ);
	}

	@Test
	void acroRotorDiskAdvanceRatioUsesMuScaleForFlapping() {
		float fastSideMu = PlayableFlightModel.acroRotorDiskAdvanceRatioMu(
				new PlayableFlightModel.Velocity(25.0f, 0.0f, 0.0f),
				0.68f,
				0.20f
		);

		assertTrue(fastSideMu > 0.28f, "fastSideMu=" + fastSideMu);
		assertTrue(fastSideMu < 0.30f, "fastSideMu=" + fastSideMu);
	}

	@Test
	void acroRotorFlappingBodyAccelerationOpposesPoweredDiagonalDiskFlow() {
		PlayableFlightModel.Velocity diagonal = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				12.5f,
				0.68f,
				0.20f
		);

		assertTrue(diagonal.x() < -0.52f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.x() > -0.70f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.z() < -0.52f, "diagonalZ=" + diagonal.z());
		assertTrue(diagonal.z() > -0.70f, "diagonalZ=" + diagonal.z());
		assertTrue(diagonal.y() < 0.0f, "diagonalY=" + diagonal.y());
		assertTrue(diagonal.y() > -0.06f, "diagonalY=" + diagonal.y());
	}

	@Test
	void acroRotorFlappingWeightsDiagonalFlowMoreThanStraightCruise() {
		PlayableFlightModel.Velocity straight = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				12.5f,
				0.68f,
				0.20f
		);
		PlayableFlightModel.Velocity diagonal = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				12.5f,
				0.68f,
				0.20f
		);

		assertEquals(0.0f, straight.x(), 1.0e-6f);
		assertTrue(straight.z() < -0.16f, "straightZ=" + straight.z());
		assertTrue(straight.z() > -0.30f, "straightZ=" + straight.z());
		assertTrue(horizontalSpeed(diagonal.x(), diagonal.z()) > Math.abs(straight.z()) * 3.0f,
				"diagonal=" + horizontalSpeed(diagonal.x(), diagonal.z()) + " straightZ=" + straight.z());
	}

	@Test
	void acroRotorFlappingKeepsConservativeStarmacScaleAtLowForwardFlow() {
		float hoverThrustAcceleration = 9.80665f;
		PlayableFlightModel.Velocity straight = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 3.4f),
				hoverThrustAcceleration,
				0.20f,
				0.20f
		);

		float tiltDegrees = (float) Math.toDegrees(horizontalSpeed(straight.x(), straight.z()) / hoverThrustAcceleration);

		assertEquals(0.0f, straight.x(), 1.0e-6f);
		assertTrue(tiltDegrees > 0.45f, "tiltDegrees=" + tiltDegrees);
		assertTrue(tiltDegrees < 0.60f, "tiltDegrees=" + tiltDegrees);
	}

	@Test
	void acroRotorInPlaneDragRequiresPoweredDiskFlow() {
		PlayableFlightModel.Velocity idle = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f,
				0.0f,
				0.20f
		);

		assertEquals(0.0f, idle.x(), 1.0e-6f);
		assertEquals(0.0f, idle.y(), 1.0e-6f);
		assertEquals(0.0f, idle.z(), 1.0e-6f);
	}

	@Test
	void acroRotorInPlaneDragOpposesPoweredDiagonalDiskFlow() {
		PlayableFlightModel.Velocity diagonal = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				12.5f,
				0.68f,
				0.20f
		);

		assertTrue(diagonal.x() < -0.65f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.x() > -0.85f, "diagonalX=" + diagonal.x());
		assertEquals(0.0f, diagonal.y(), 1.0e-6f);
		assertTrue(diagonal.z() < -0.65f, "diagonalZ=" + diagonal.z());
		assertTrue(diagonal.z() > -0.85f, "diagonalZ=" + diagonal.z());
	}

	@Test
	void acroRotorInPlaneDragWeightsDiagonalSlipMoreThanStraightCruise() {
		PlayableFlightModel.Velocity straight = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				12.5f,
				0.68f,
				0.20f
		);
		PlayableFlightModel.Velocity diagonal = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				12.5f,
				0.68f,
				0.20f
		);

		assertEquals(0.0f, straight.x(), 1.0e-6f);
		assertTrue(straight.z() < -0.14f, "straightZ=" + straight.z());
		assertTrue(straight.z() > -0.25f, "straightZ=" + straight.z());
		assertTrue(horizontalSpeed(diagonal.x(), diagonal.z()) > Math.abs(straight.z()) * 2.2f,
				"diagonal=" + horizontalSpeed(diagonal.x(), diagonal.z()) + " straightZ=" + straight.z());
	}

	@Test
	void acroDynamicInflowThrustSagRequiresSpeedAndBodyRate() {
		float stationary = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 0.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f
		);
		float fastNoRate = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f,
				0.0f,
				0.68f,
				0.20f
		);
		float fastStraightRate = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(8.8),
				0.0f,
				0.68f,
				0.20f
		);
		float diagonalRate = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f
		);
		float highThrottleDiagonalRate = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				1.0f,
				0.20f
		);

		assertEquals(1.0f, stationary, 1.0e-6f);
		assertEquals(1.0f, fastNoRate, 1.0e-6f);
		assertTrue(fastStraightRate > 0.987f, "fastStraightRate=" + fastStraightRate);
		assertTrue(fastStraightRate < 0.993f, "fastStraightRate=" + fastStraightRate);
		assertTrue(diagonalRate > 0.936f, "diagonalRate=" + diagonalRate);
		assertTrue(diagonalRate < 0.946f, "diagonalRate=" + diagonalRate);
		assertTrue(highThrottleDiagonalRate < diagonalRate, "highThrottleDiagonalRate=" + highThrottleDiagonalRate + " diagonalRate=" + diagonalRate);
		assertTrue(highThrottleDiagonalRate > 0.924f, "highThrottleDiagonalRate=" + highThrottleDiagonalRate);
		assertTrue(highThrottleDiagonalRate < 0.934f, "highThrottleDiagonalRate=" + highThrottleDiagonalRate);
	}

	@Test
	void acroSidewashMemoryDelaysYawDynamicInflowLossWithoutHidingPitchAoa() {
		float freshYaw = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		float settledYaw = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		float freshPitch = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		float settledPitch = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);

		assertTrue(freshYaw > settledYaw + 0.010f, "freshYaw=" + freshYaw + " settledYaw=" + settledYaw);
		assertTrue(freshYaw < 0.985f, "freshYaw=" + freshYaw);
		assertTrue(settledYaw < 0.965f, "settledYaw=" + settledYaw);
		assertEquals(settledPitch, freshPitch, 1.0e-6f);
	}

	@Test
	void acroAirframeSeparationOnlyBuildsAtHighAngleFlow() {
		float straightCruise = PlayableFlightModel.acroAirframeSeparationIntensity(0.0f, 0.0f, 25.0f);
		float diagonalSideslip = PlayableFlightModel.acroAirframeSeparationIntensity(16.0f, 0.0f, 16.0f);
		float broadside = PlayableFlightModel.acroAirframeSeparationIntensity(25.0f, 0.0f, 0.0f);

		assertEquals(0.0f, straightCruise, 1.0e-6f);
		assertTrue(diagonalSideslip > 0.22f, "diagonalSideslip=" + diagonalSideslip);
		assertTrue(diagonalSideslip < 0.45f, "diagonalSideslip=" + diagonalSideslip);
		assertTrue(broadside > 0.98f, "broadside=" + broadside);
	}

	@Test
	void acroSidewashMemoryDelaysYawSeparationWithoutHidingPitchAoa() {
		float freshYawSeparation = PlayableFlightModel.acroAirframeSeparationIntensity(
				16.0f,
				0.0f,
				16.0f,
				1.0f,
				0.0f
		);
		float settledYawSeparation = PlayableFlightModel.acroAirframeSeparationIntensity(
				16.0f,
				0.0f,
				16.0f,
				1.0f,
				1.0f
		);
		float freshPitchSeparation = PlayableFlightModel.acroAirframeSeparationIntensity(
				0.0f,
				16.0f,
				16.0f,
				1.0f,
				0.0f
		);
		float settledPitchSeparation = PlayableFlightModel.acroAirframeSeparationIntensity(
				0.0f,
				16.0f,
				16.0f,
				1.0f,
				1.0f
		);

		assertTrue(freshYawSeparation > 0.08f, "freshYawSeparation=" + freshYawSeparation);
		assertTrue(freshYawSeparation < settledYawSeparation * 0.45f,
				"freshYawSeparation=" + freshYawSeparation + " settledYawSeparation=" + settledYawSeparation);
		assertTrue(settledYawSeparation > 0.22f, "settledYawSeparation=" + settledYawSeparation);
		assertEquals(settledPitchSeparation, freshPitchSeparation, 1.0e-6f);
		assertTrue(freshPitchSeparation > freshYawSeparation * 3.0f,
				"freshPitchSeparation=" + freshPitchSeparation + " freshYawSeparation=" + freshYawSeparation);
	}

	@Test
	void acroAeroCrossflowLagTargetNeedsFastCrossflow() {
		float straightCruise = PlayableFlightModel.acroAeroCrossflowLagTarget(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f)
		);
		float slowBroadside = PlayableFlightModel.acroAeroCrossflowLagTarget(
				new PlayableFlightModel.Velocity(3.0f, 0.0f, 0.0f)
		);
		float diagonalSideslip = PlayableFlightModel.acroAeroCrossflowLagTarget(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f)
		);
		float broadside = PlayableFlightModel.acroAeroCrossflowLagTarget(
				new PlayableFlightModel.Velocity(25.0f, 0.0f, 0.0f)
		);

		assertEquals(0.0f, straightCruise, 1.0e-6f);
		assertEquals(0.0f, slowBroadside, 1.0e-6f);
		assertTrue(diagonalSideslip > 0.82f, "diagonalSideslip=" + diagonalSideslip);
		assertTrue(diagonalSideslip < 0.95f, "diagonalSideslip=" + diagonalSideslip);
		assertTrue(broadside > 0.99f, "broadside=" + broadside);
	}

	@Test
	void acroAeroCrossflowLagBuildsOverTicks() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step first = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				state
		);
		PlayableFlightModel.Step later = runFrom(
				FlightMode.ACRO,
				5,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(first)
		);

		assertTrue(first.acroAeroCrossflowLag() > 0.25f, "firstLag=" + first.acroAeroCrossflowLag());
		assertTrue(first.acroAeroCrossflowLag() < 0.36f, "firstLag=" + first.acroAeroCrossflowLag());
		assertTrue(later.acroAeroCrossflowLag() > first.acroAeroCrossflowLag() + 0.18f,
				"firstLag=" + first.acroAeroCrossflowLag() + " laterLag=" + later.acroAeroCrossflowLag());
		assertTrue(later.acroAeroCrossflowLag() < 0.95f, "laterLag=" + later.acroAeroCrossflowLag());
	}

	@Test
	void acroSidewashMemoryBuildsSlowerThanBaseCrossflowAndReleasesWithTail() {
		PlayableFlightModel.State diagonalState = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step first = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				diagonalState
		);
		PlayableFlightModel.Step later = runFrom(
				FlightMode.ACRO,
				5,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				stateFrom(first)
		);
		PlayableFlightModel.Step release = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						22.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f,
						0,
						1.0f,
						1.0f
				)
		);

		assertTrue(first.acroSidewashMemory() > 0.18f, "firstMemory=" + first.acroSidewashMemory());
		assertTrue(first.acroSidewashMemory() < first.acroAeroCrossflowLag() * 0.78f,
				"firstMemory=" + first.acroSidewashMemory() + " firstLag=" + first.acroAeroCrossflowLag());
		assertTrue(later.acroSidewashMemory() > first.acroSidewashMemory() + 0.42f,
				"firstMemory=" + first.acroSidewashMemory() + " laterMemory=" + later.acroSidewashMemory());
		assertTrue(later.acroSidewashMemory() < 0.85f, "laterMemory=" + later.acroSidewashMemory());
		assertTrue(release.acroSidewashMemory() > release.acroAeroCrossflowLag(),
				"releaseMemory=" + release.acroSidewashMemory() + " releaseLag=" + release.acroAeroCrossflowLag());
	}

	@Test
	void acroSidewashMemoryReleasesWithinOneSecondAfterFlowRealigns() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				22.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f,
				1.0f
		);

		PlayableFlightModel.Step afterOneSecond = runFrom(
				FlightMode.ACRO,
				20,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				state
		);

		assertTrue(afterOneSecond.acroSidewashMemory() < 0.07f,
				"memory=" + afterOneSecond.acroSidewashMemory());
		assertTrue(afterOneSecond.acroSidewashMemory() > afterOneSecond.acroAeroCrossflowLag(),
				"memory=" + afterOneSecond.acroSidewashMemory() + " lag=" + afterOneSecond.acroAeroCrossflowLag());
		assertTrue(afterOneSecond.velocityZ() > 15.0f,
				"velocityZ=" + afterOneSecond.velocityZ());
	}

	@Test
	void acroSidewashForceResponseUsesMemoryInsteadOfInstantFullSideforce() {
		float firstTickResponse = PlayableFlightModel.acroSidewashForceResponse(0.32f, 0.10f);
		float settledResponse = PlayableFlightModel.acroSidewashForceResponse(1.0f, 1.0f);
		float wakeTailResponse = PlayableFlightModel.acroSidewashForceResponse(0.15f, 0.72f);

		assertTrue(firstTickResponse > 0.12f, "firstTickResponse=" + firstTickResponse);
		assertTrue(firstTickResponse < 0.14f, "firstTickResponse=" + firstTickResponse);
		assertEquals(1.0f, settledResponse, 1.0e-6f);
		assertTrue(wakeTailResponse > 0.70f, "wakeTailResponse=" + wakeTailResponse);
		assertTrue(wakeTailResponse < 0.75f, "wakeTailResponse=" + wakeTailResponse);
	}

	@Test
	void acroLaggedCrossflowSoftensFirstTickRotorDiskLoads() {
		PlayableFlightModel.Velocity straight = new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f);
		PlayableFlightModel.Velocity diagonal = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity straightInitialFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				0.0f
		);
		PlayableFlightModel.Velocity straightSettledFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				1.0f
		);
		PlayableFlightModel.Velocity diagonalInitialFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				0.32f
		);
		PlayableFlightModel.Velocity diagonalSettledFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				1.0f
		);
		float straightMagnitude = horizontalSpeed(straightInitialFlap.x(), straightInitialFlap.z());
		float diagonalInitialMagnitude = horizontalSpeed(diagonalInitialFlap.x(), diagonalInitialFlap.z());
		float diagonalSettledMagnitude = horizontalSpeed(diagonalSettledFlap.x(), diagonalSettledFlap.z());

		assertEquals(straightSettledFlap.x(), straightInitialFlap.x(), 1.0e-6f);
		assertEquals(straightSettledFlap.z(), straightInitialFlap.z(), 1.0e-6f);
		assertTrue(diagonalInitialMagnitude < diagonalSettledMagnitude * 0.70f,
				"initial=" + diagonalInitialMagnitude + " settled=" + diagonalSettledMagnitude);
		assertTrue(diagonalInitialMagnitude > straightMagnitude * 1.35f,
				"initial=" + diagonalInitialMagnitude + " straight=" + straightMagnitude);
	}

	@Test
	void acroLaggedCrossflowSoftensFirstTickRotorInPlaneDrag() {
		PlayableFlightModel.Velocity straight = new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f);
		PlayableFlightModel.Velocity diagonal = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity straightInitial = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				0.0f
		);
		PlayableFlightModel.Velocity straightSettled = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				1.0f
		);
		PlayableFlightModel.Velocity diagonalInitial = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				0.32f
		);
		PlayableFlightModel.Velocity diagonalSettled = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				1.0f
		);
		float straightMagnitude = horizontalSpeed(straightInitial.x(), straightInitial.z());
		float diagonalInitialMagnitude = horizontalSpeed(diagonalInitial.x(), diagonalInitial.z());
		float diagonalSettledMagnitude = horizontalSpeed(diagonalSettled.x(), diagonalSettled.z());

		assertEquals(straightSettled.x(), straightInitial.x(), 1.0e-6f);
		assertEquals(straightSettled.z(), straightInitial.z(), 1.0e-6f);
		assertTrue(diagonalInitialMagnitude < diagonalSettledMagnitude * 0.58f,
				"initial=" + diagonalInitialMagnitude + " settled=" + diagonalSettledMagnitude);
		assertTrue(diagonalInitialMagnitude > straightMagnitude * 2.0f,
				"initial=" + diagonalInitialMagnitude + " straight=" + straightMagnitude);
	}

	@Test
	void acroSidewashMemoryDelaysRotorDiskSideLoadsAfterFastSlipEntry() {
		PlayableFlightModel.Velocity straight = new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f);
		PlayableFlightModel.Velocity diagonal = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity straightFreshFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity straightSettledFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity freshFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity settledFlap = PlayableFlightModel.acroRotorFlappingBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity straightFreshInPlane = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity straightSettledInPlane = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				straight,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity freshInPlane = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity settledInPlane = PlayableFlightModel.acroRotorInPlaneDragBodyAcceleration(
				diagonal,
				12.5f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		float freshFlapMagnitude = horizontalSpeed(freshFlap.x(), freshFlap.z());
		float settledFlapMagnitude = horizontalSpeed(settledFlap.x(), settledFlap.z());
		float freshInPlaneMagnitude = horizontalSpeed(freshInPlane.x(), freshInPlane.z());
		float settledInPlaneMagnitude = horizontalSpeed(settledInPlane.x(), settledInPlane.z());

		assertEquals(straightSettledFlap.x(), straightFreshFlap.x(), 1.0e-6f);
		assertEquals(straightSettledFlap.z(), straightFreshFlap.z(), 1.0e-6f);
		assertEquals(straightSettledInPlane.x(), straightFreshInPlane.x(), 1.0e-6f);
		assertEquals(straightSettledInPlane.z(), straightFreshInPlane.z(), 1.0e-6f);
		assertTrue(freshFlapMagnitude < settledFlapMagnitude * 0.62f,
				"freshFlap=" + freshFlapMagnitude + " settledFlap=" + settledFlapMagnitude);
		assertTrue(freshFlapMagnitude > settledFlapMagnitude * 0.45f,
				"freshFlap=" + freshFlapMagnitude + " settledFlap=" + settledFlapMagnitude);
		assertTrue(freshInPlaneMagnitude < settledInPlaneMagnitude * 0.50f,
				"freshInPlane=" + freshInPlaneMagnitude + " settledInPlane=" + settledInPlaneMagnitude);
		assertTrue(freshInPlaneMagnitude > settledInPlaneMagnitude * 0.25f,
				"freshInPlane=" + freshInPlaneMagnitude + " settledInPlane=" + settledInPlaneMagnitude);
	}

	@Test
	void acroLaggedCrossflowSoftensFirstTickThrustLossesWithoutTouchingStraightCruise() {
		float straightInitialAdvance = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				0.0f
		);
		float straightSettledAdvance = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				1.0f
		);
		float diagonalInitialAdvance = PlayableFlightModel.acroAdvanceRatioThrustScale(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				0.32f
		);
		float diagonalSettledAdvance = PlayableFlightModel.acroAdvanceRatioThrustScale(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				1.0f
		);
		float straightInitialInflow = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(8.8),
				0.0f,
				0.68f,
				0.20f,
				0.0f
		);
		float straightSettledInflow = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(8.8),
				0.0f,
				0.68f,
				0.20f,
				1.0f
		);
		float diagonalInitialInflow = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f,
				0.32f
		);
		float diagonalSettledInflow = PlayableFlightModel.acroDynamicInflowThrustScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				0.68f,
				0.20f,
				1.0f
		);

		assertEquals(straightSettledAdvance, straightInitialAdvance, 1.0e-6f);
		assertTrue(diagonalInitialAdvance > diagonalSettledAdvance + 0.015f,
				"initialAdvance=" + diagonalInitialAdvance + " settledAdvance=" + diagonalSettledAdvance);
		assertEquals(straightSettledInflow, straightInitialInflow, 1.0e-6f);
		assertTrue(diagonalInitialInflow > diagonalSettledInflow + 0.010f,
				"initialInflow=" + diagonalInitialInflow + " settledInflow=" + diagonalSettledInflow);
	}

	@Test
	void acroSidewashMemoryDelaysYawAdvanceRatioLossWithoutHidingPitchAoa() {
		float straightFresh = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		float straightSettled = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		float diagonalFresh = PlayableFlightModel.acroAdvanceRatioThrustScale(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		float diagonalSettled = PlayableFlightModel.acroAdvanceRatioThrustScale(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);
		float pitchFresh = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				25.0f,
				(float) Math.toRadians(60.0),
				0.0f,
				0.68f,
				0.20f,
				1.0f,
				0.0f
		);
		float pitchSettled = PlayableFlightModel.acroAdvanceRatioThrustScale(
				0.0f,
				0.0f,
				25.0f,
				(float) Math.toRadians(60.0),
				0.0f,
				0.68f,
				0.20f,
				1.0f,
				1.0f
		);

		assertEquals(straightSettled, straightFresh, 1.0e-6f);
		assertEquals(pitchSettled, pitchFresh, 1.0e-6f);
		assertTrue(diagonalFresh > diagonalSettled + 0.015f,
				"fresh=" + diagonalFresh + " settled=" + diagonalSettled);
		assertTrue(diagonalFresh < pitchFresh,
				"fresh=" + diagonalFresh + " pitch=" + pitchFresh);
		assertTrue(diagonalSettled < 0.46f, "settled=" + diagonalSettled);
	}

	@Test
	void acroLaggedCrossflowSoftensFirstTickPassiveRollMoment() {
		PlayableFlightModel.State risingLag = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				0.0f
		);
		PlayableFlightModel.State settledLag = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.Step risingStep = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				risingLag
		);
		PlayableFlightModel.Step settledStep = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				settledLag
		);
		float risingRollRate = Math.abs(risingStep.acroRollRateRadiansPerTick());
		float settledRollRate = Math.abs(settledStep.acroRollRateRadiansPerTick());

		assertTrue(risingStep.acroAeroCrossflowLag() > 0.25f, "risingLag=" + risingStep.acroAeroCrossflowLag());
		assertTrue(risingStep.acroAeroCrossflowLag() < 0.36f, "risingLag=" + risingStep.acroAeroCrossflowLag());
		assertTrue(risingRollRate > Math.toRadians(0.05), "risingRollDeg=" + Math.toDegrees(risingRollRate));
		assertTrue(risingRollRate < settledRollRate * 0.55f,
				"risingRollDeg=" + Math.toDegrees(risingRollRate) + " settledRollDeg=" + Math.toDegrees(settledRollRate));
		assertTrue(settledRollRate > Math.toRadians(0.40), "settledRollDeg=" + Math.toDegrees(settledRollRate));
	}

	@Test
	void acroSidewashMemoryDelaysPassiveTransverseRollMomentAfterFastSlipEntry() {
		PlayableFlightModel.State freshSidewash = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f,
				0.0f
		);
		PlayableFlightModel.State settledSidewash = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Step freshStep = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				freshSidewash
		);
		PlayableFlightModel.Step settledStep = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				settledSidewash
		);
		float freshRollRate = Math.abs(freshStep.acroRollRateRadiansPerTick());
		float settledRollRate = Math.abs(settledStep.acroRollRateRadiansPerTick());

		assertTrue(freshStep.acroAeroCrossflowLag() > 0.94f, "freshLag=" + freshStep.acroAeroCrossflowLag());
		assertTrue(freshStep.acroSidewashMemory() < 0.25f, "freshMemory=" + freshStep.acroSidewashMemory());
		assertTrue(settledStep.acroSidewashMemory() > 0.96f, "settledMemory=" + settledStep.acroSidewashMemory());
		assertTrue(freshRollRate > Math.toRadians(0.08), "freshRollDeg=" + Math.toDegrees(freshRollRate));
		assertTrue(freshRollRate < settledRollRate * 0.45f,
				"freshRollDeg=" + Math.toDegrees(freshRollRate) + " settledRollDeg=" + Math.toDegrees(settledRollRate));
		assertTrue(settledRollRate > Math.toRadians(0.40), "settledRollDeg=" + Math.toDegrees(settledRollRate));
	}

	@Test
	void acroLaggedCrossflowSoftensFirstTickResidualTorqueLoad() {
		float straightCruise = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(6.0),
				0.0f,
				0.0f
		);
		float diagonalInitial = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(6.0),
				(float) Math.toRadians(6.0),
				0.32f
		);
		float diagonalSettled = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(6.0),
				(float) Math.toRadians(6.0),
				1.0f
		);

		assertEquals(0.0f, straightCruise, 1.0e-6f);
		assertTrue(diagonalInitial > 0.004f, "diagonalInitial=" + diagonalInitial);
		assertTrue(diagonalInitial < diagonalSettled * 0.45f,
				"initial=" + diagonalInitial + " settled=" + diagonalSettled);
		assertTrue(diagonalSettled > 0.017f, "diagonalSettled=" + diagonalSettled);
	}

	@Test
	void acroLaggedCrossflowSoftensFirstTickYawTurnLoad() {
		PlayableFlightModel.Velocity straightInitial = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				5.0f,
				0.0f
		);
		PlayableFlightModel.Velocity straightSettled = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				5.0f,
				1.0f
		);
		PlayableFlightModel.Velocity diagonalInitial = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				5.0f,
				0.32f
		);
		PlayableFlightModel.Velocity diagonalSettled = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				5.0f,
				1.0f
		);
		float straightMagnitude = horizontalSpeed(straightInitial.x(), straightInitial.z());
		float diagonalInitialMagnitude = horizontalSpeed(diagonalInitial.x(), diagonalInitial.z());
		float diagonalSettledMagnitude = horizontalSpeed(diagonalSettled.x(), diagonalSettled.z());

		assertEquals(straightSettled.x(), straightInitial.x(), 1.0e-6f);
		assertEquals(straightSettled.z(), straightInitial.z(), 1.0e-6f);
		assertTrue(diagonalInitialMagnitude < diagonalSettledMagnitude * 0.75f,
				"initial=" + diagonalInitialMagnitude + " settled=" + diagonalSettledMagnitude);
		assertTrue(diagonalInitialMagnitude > straightMagnitude * 1.05f,
				"initial=" + diagonalInitialMagnitude + " straight=" + straightMagnitude);
	}

	@Test
	void acroSidewashMemoryDelaysYawTurnLoadAfterFastSlipEntry() {
		PlayableFlightModel.Velocity straightFresh = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				5.0f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity straightSettled = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				5.0f,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity diagonalFresh = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				5.0f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity diagonalSettled = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				5.0f,
				1.0f,
				1.0f
		);
		float straightFreshMagnitude = horizontalSpeed(straightFresh.x(), straightFresh.z());
		float diagonalFreshMagnitude = horizontalSpeed(diagonalFresh.x(), diagonalFresh.z());
		float diagonalSettledMagnitude = horizontalSpeed(diagonalSettled.x(), diagonalSettled.z());

		assertEquals(straightSettled.x(), straightFresh.x(), 1.0e-6f);
		assertEquals(straightSettled.z(), straightFresh.z(), 1.0e-6f);
		assertTrue(diagonalFreshMagnitude < diagonalSettledMagnitude * 0.70f,
				"fresh=" + diagonalFreshMagnitude + " settled=" + diagonalSettledMagnitude);
		assertTrue(diagonalFreshMagnitude > diagonalSettledMagnitude * 0.60f,
				"fresh=" + diagonalFreshMagnitude + " settled=" + diagonalSettledMagnitude);
		assertTrue(diagonalFreshMagnitude > straightFreshMagnitude * 1.10f,
				"fresh=" + diagonalFreshMagnitude + " straight=" + straightFreshMagnitude);
	}

	@Test
	void acroLaggedCrossflowSoftensFirstTickBodyRateLoad() {
		PlayableFlightModel.Velocity straightInitial = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(9.0),
				0.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Velocity straightSettled = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(9.0),
				0.0f,
				0.0f,
				1.0f
		);
		PlayableFlightModel.Velocity diagonalInitial = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f,
				0.32f
		);
		PlayableFlightModel.Velocity diagonalSettled = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f,
				1.0f
		);
		float straightMagnitude = horizontalSpeed(straightInitial.x(), straightInitial.z());
		float diagonalInitialMagnitude = horizontalSpeed(diagonalInitial.x(), diagonalInitial.z());
		float diagonalSettledMagnitude = horizontalSpeed(diagonalSettled.x(), diagonalSettled.z());

		assertEquals(straightSettled.x(), straightInitial.x(), 1.0e-6f);
		assertEquals(straightSettled.z(), straightInitial.z(), 1.0e-6f);
		assertTrue(diagonalInitialMagnitude < diagonalSettledMagnitude * 0.65f,
				"initial=" + diagonalInitialMagnitude + " settled=" + diagonalSettledMagnitude);
		assertTrue(diagonalInitialMagnitude > straightMagnitude * 1.55f,
				"initial=" + diagonalInitialMagnitude + " straight=" + straightMagnitude);
	}

	@Test
	void acroSidewashMemoryDelaysYawBodyRateLoadWithoutHidingPitchAoa() {
		PlayableFlightModel.Velocity freshYaw = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity settledYaw = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity freshPitch = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity settledPitch = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f,
				1.0f,
				1.0f
		);
		float freshYawMagnitude = horizontalSpeed(freshYaw.x(), freshYaw.z());
		float settledYawMagnitude = horizontalSpeed(settledYaw.x(), settledYaw.z());

		assertTrue(freshYawMagnitude < settledYawMagnitude * 0.62f,
				"freshYaw=" + freshYawMagnitude + " settledYaw=" + settledYawMagnitude);
		assertTrue(freshYawMagnitude > settledYawMagnitude * 0.48f,
				"freshYaw=" + freshYawMagnitude + " settledYaw=" + settledYawMagnitude);
		assertTrue(settledYawMagnitude > 0.35f, "settledYaw=" + settledYawMagnitude);
		assertEquals(settledPitch.x(), freshPitch.x(), 1.0e-6f);
		assertEquals(settledPitch.y(), freshPitch.y(), 1.0e-6f);
		assertEquals(settledPitch.z(), freshPitch.z(), 1.0e-6f);
	}

	@Test
	void acroPitchPlaneLiftIsZeroInStraightCruise() {
		PlayableFlightModel.Velocity lift = PlayableFlightModel.acroPitchPlaneLiftAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				0.0f
		);

		assertEquals(0.0f, lift.x(), 1.0e-6f);
		assertEquals(0.0f, lift.y(), 1.0e-6f);
		assertEquals(0.0f, lift.z(), 1.0e-6f);
	}

	@Test
	void acroPitchPlaneLiftBendsPositiveAoaFlowUpAndBack() {
		PlayableFlightModel.Velocity lift = PlayableFlightModel.acroPitchPlaneLiftAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 8.0f, 18.0f),
				0.0f
		);
		float workAlongVelocity = lift.y() * 8.0f + lift.z() * 18.0f;

		assertEquals(0.0f, lift.x(), 1.0e-6f);
		assertTrue(lift.y() > 0.20f, "liftY=" + lift.y());
		assertTrue(lift.y() < 0.35f, "liftY=" + lift.y());
		assertTrue(lift.z() < -0.08f, "liftZ=" + lift.z());
		assertTrue(lift.z() > -0.18f, "liftZ=" + lift.z());
		assertEquals(0.0f, workAlongVelocity, 1.0e-4f);
	}

	@Test
	void acroPitchPlaneLiftBendsNegativeAoaFlowDownAndBack() {
		PlayableFlightModel.Velocity lift = PlayableFlightModel.acroPitchPlaneLiftAcceleration(
				new PlayableFlightModel.Velocity(0.0f, -8.0f, 18.0f),
				0.0f
		);
		float workAlongVelocity = lift.y() * -8.0f + lift.z() * 18.0f;

		assertEquals(0.0f, lift.x(), 1.0e-6f);
		assertTrue(lift.y() < -0.20f, "liftY=" + lift.y());
		assertTrue(lift.y() > -0.35f, "liftY=" + lift.y());
		assertTrue(lift.z() < -0.08f, "liftZ=" + lift.z());
		assertTrue(lift.z() > -0.18f, "liftZ=" + lift.z());
		assertEquals(0.0f, workAlongVelocity, 1.0e-4f);
	}

	@Test
	void acroSideslipSideforceBendsDiagonalVelocityTowardBodyForward() {
		PlayableFlightModel.Velocity sideforce = PlayableFlightModel.acroSideslipSideforceAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.30f
		);
		float workAlongVelocity = sideforce.x() * 16.0f + sideforce.z() * 16.0f;

		assertTrue(sideforce.x() < -1.65f, "sideforceX=" + sideforce.x());
		assertTrue(sideforce.x() > -1.95f, "sideforceX=" + sideforce.x());
		assertTrue(sideforce.z() > 1.65f, "sideforceZ=" + sideforce.z());
		assertTrue(sideforce.z() < 1.95f, "sideforceZ=" + sideforce.z());
		assertEquals(0.0f, workAlongVelocity, 1.0e-4f);
	}

	@Test
	void acroSideslipSideforceCurvesMoreThanItBrakesDiagonalSlip() {
		PlayableFlightModel.Velocity diagonalVelocity = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity sideforce = PlayableFlightModel.acroSideslipSideforceAcceleration(
				diagonalVelocity,
				0.30f
		);
		PlayableFlightModel.Velocity inducedDrag = PlayableFlightModel.acroSideslipInducedDragAcceleration(
				diagonalVelocity,
				sideforce
		);
		float sideforceMagnitude = horizontalSpeed(sideforce.x(), sideforce.z());
		float inducedDragMagnitude = horizontalSpeed(inducedDrag.x(), inducedDrag.z());
		float sideforceWork = sideforce.x() * diagonalVelocity.x()
				+ sideforce.z() * diagonalVelocity.z();

		assertEquals(0.0f, sideforceWork, 1.0e-4f);
		assertTrue(sideforceMagnitude > 2.35f, "sideforceMagnitude=" + sideforceMagnitude);
		assertTrue(sideforceMagnitude < 2.65f, "sideforceMagnitude=" + sideforceMagnitude);
		assertTrue(inducedDragMagnitude < sideforceMagnitude * 0.28f,
				"sideforceMagnitude=" + sideforceMagnitude + " inducedDragMagnitude=" + inducedDragMagnitude);
	}

	@Test
	void acroSideslipInducedDragMakesSideforceCostEnergy() {
		PlayableFlightModel.Velocity straightVelocity = new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f);
		PlayableFlightModel.Velocity straightSideforce = PlayableFlightModel.acroSideslipSideforceAcceleration(
				straightVelocity,
				0.0f
		);
		PlayableFlightModel.Velocity straightInducedDrag = PlayableFlightModel.acroSideslipInducedDragAcceleration(
				straightVelocity,
				straightSideforce
		);
		PlayableFlightModel.Velocity diagonalVelocity = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity diagonalSideforce = PlayableFlightModel.acroSideslipSideforceAcceleration(
				diagonalVelocity,
				0.30f
		);
		PlayableFlightModel.Velocity diagonalInducedDrag = PlayableFlightModel.acroSideslipInducedDragAcceleration(
				diagonalVelocity,
				diagonalSideforce
		);
		float sideforceWork = diagonalSideforce.x() * diagonalVelocity.x()
				+ diagonalSideforce.z() * diagonalVelocity.z();
		float inducedDragWork = diagonalInducedDrag.x() * diagonalVelocity.x()
				+ diagonalInducedDrag.z() * diagonalVelocity.z();
		float inducedDragMagnitude = horizontalSpeed(diagonalInducedDrag.x(), diagonalInducedDrag.z());

		assertEquals(0.0f, straightInducedDrag.x(), 1.0e-6f);
		assertEquals(0.0f, straightInducedDrag.z(), 1.0e-6f);
		assertEquals(0.0f, sideforceWork, 1.0e-4f);
		assertTrue(diagonalInducedDrag.x() < -0.36f, "inducedDragX=" + diagonalInducedDrag.x());
		assertTrue(diagonalInducedDrag.x() > -0.45f, "inducedDragX=" + diagonalInducedDrag.x());
		assertTrue(diagonalInducedDrag.z() < -0.36f, "inducedDragZ=" + diagonalInducedDrag.z());
		assertTrue(diagonalInducedDrag.z() > -0.45f, "inducedDragZ=" + diagonalInducedDrag.z());
		assertTrue(inducedDragMagnitude > 0.52f, "inducedDragMagnitude=" + inducedDragMagnitude);
		assertTrue(inducedDragMagnitude < 0.65f, "inducedDragMagnitude=" + inducedDragMagnitude);
		assertTrue(inducedDragWork < -12.0f, "inducedDragWork=" + inducedDragWork);
		assertTrue(inducedDragWork > -14.0f, "inducedDragWork=" + inducedDragWork);
	}

	@Test
	void acroSidewashMemoryBuildsSideslipInducedDragSlowerThanSideforce() {
		PlayableFlightModel.Velocity straightVelocity = new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f);
		PlayableFlightModel.Velocity straightSideforce = PlayableFlightModel.acroSideslipSideforceAcceleration(
				straightVelocity,
				0.0f
		);
		PlayableFlightModel.Velocity diagonalVelocity = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity diagonalSideforce = PlayableFlightModel.acroSideslipSideforceAcceleration(
				diagonalVelocity,
				0.30f
		);
		float freshSidewashResponse = PlayableFlightModel.acroSidewashForceResponse(1.0f, 0.0f);
		PlayableFlightModel.Velocity straightFreshDrag = PlayableFlightModel.acroSideslipInducedDragAcceleration(
				straightVelocity,
				straightSideforce,
				freshSidewashResponse
		);
		PlayableFlightModel.Velocity freshDrag = PlayableFlightModel.acroSideslipInducedDragAcceleration(
				diagonalVelocity,
				diagonalSideforce,
				freshSidewashResponse
		);
		PlayableFlightModel.Velocity settledDrag = PlayableFlightModel.acroSideslipInducedDragAcceleration(
				diagonalVelocity,
				diagonalSideforce,
				1.0f
		);
		float freshDragMagnitude = horizontalSpeed(freshDrag.x(), freshDrag.z());
		float settledDragMagnitude = horizontalSpeed(settledDrag.x(), settledDrag.z());

		assertEquals(0.0f, straightFreshDrag.x(), 1.0e-6f);
		assertEquals(0.0f, straightFreshDrag.z(), 1.0e-6f);
		assertTrue(freshSidewashResponse > 0.39f, "freshSidewashResponse=" + freshSidewashResponse);
		assertTrue(freshSidewashResponse < 0.41f, "freshSidewashResponse=" + freshSidewashResponse);
		assertTrue(freshDragMagnitude > settledDragMagnitude * 0.15f,
				"freshDragMagnitude=" + freshDragMagnitude + " settledDragMagnitude=" + settledDragMagnitude);
		assertTrue(freshDragMagnitude < settledDragMagnitude * 0.18f,
				"freshDragMagnitude=" + freshDragMagnitude + " settledDragMagnitude=" + settledDragMagnitude);
	}

	@Test
	void acroYawTurnLoadOnlyAppearsAtHighSpeedYawRate() {
		PlayableFlightModel.Velocity noYaw = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f
		);
		PlayableFlightModel.Velocity lowSpeed = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(5.0f, 0.0f, 0.0f),
				5.0f
		);
		PlayableFlightModel.Velocity slowYaw = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f
		);

		assertEquals(0.0f, noYaw.x(), 1.0e-6f);
		assertEquals(0.0f, noYaw.z(), 1.0e-6f);
		assertEquals(0.0f, lowSpeed.x(), 1.0e-6f);
		assertEquals(0.0f, lowSpeed.z(), 1.0e-6f);
		assertEquals(0.0f, slowYaw.x(), 1.0e-6f);
		assertEquals(0.0f, slowYaw.z(), 1.0e-6f);
	}

	@Test
	void acroYawTurnLoadAddsEnergyCostToFastDiagonalTurns() {
		PlayableFlightModel.Velocity straight = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				5.0f
		);
		PlayableFlightModel.Velocity diagonal = PlayableFlightModel.acroYawTurnLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				5.0f
		);
		float straightMagnitude = horizontalSpeed(straight.x(), straight.z());
		float diagonalMagnitude = horizontalSpeed(diagonal.x(), diagonal.z());
		float workAlongVelocity = diagonal.x() * 16.0f + diagonal.z() * 16.0f;

		assertEquals(0.0f, straight.x(), 1.0e-6f);
		assertTrue(straight.z() < -0.55f, "straightZ=" + straight.z());
		assertTrue(straight.z() > -0.85f, "straightZ=" + straight.z());
		assertTrue(diagonal.x() < -0.65f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.x() > -1.10f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.z() < -0.65f, "diagonalZ=" + diagonal.z());
		assertTrue(diagonal.z() > -1.10f, "diagonalZ=" + diagonal.z());
		assertTrue(diagonalMagnitude > straightMagnitude * 1.30f,
				"diagonalMagnitude=" + diagonalMagnitude + " straightMagnitude=" + straightMagnitude);
		assertTrue(workAlongVelocity < -20.0f, "workAlongVelocity=" + workAlongVelocity);
	}

	@Test
	void acroYawCommandLoadsFastForwardVelocityInSameTick() {
		PlayableFlightModel.State fastForward = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.Step noYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				fastForward
		);
		PlayableFlightModel.Step yaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				fastForward
		);

		assertTrue(yaw.yawDegreesPerTick() > 4.0f, "yawDegPerTick=" + yaw.yawDegreesPerTick());
		assertTrue(yaw.velocityZ() < noYaw.velocityZ() - 0.025f,
				"yawVelocityZ=" + yaw.velocityZ() + " noYawVelocityZ=" + noYaw.velocityZ());
	}

	@Test
	void acroBodyRateLoadOnlyAppearsAtHighSpeedWithBodyRate() {
		PlayableFlightModel.Velocity noRate = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f),
				0.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Velocity lowSpeed = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(-5.0f, 0.0f, 5.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f
		);

		assertEquals(0.0f, noRate.x(), 1.0e-6f);
		assertEquals(0.0f, noRate.z(), 1.0e-6f);
		assertEquals(0.0f, lowSpeed.x(), 1.0e-6f);
		assertEquals(0.0f, lowSpeed.z(), 1.0e-6f);
	}

	@Test
	void acroBodyRateLoadMakesFastDiagonalRatesFeelHeavierThanStraightCruise() {
		PlayableFlightModel.Velocity straightPitch = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(9.0),
				0.0f,
				0.0f
		);
		PlayableFlightModel.Velocity diagonalRollPitch = PlayableFlightModel.acroBodyRateLoadBodyAcceleration(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(9.0),
				(float) Math.toRadians(9.0),
				0.0f
		);
		float straightMagnitude = horizontalSpeed(straightPitch.x(), straightPitch.z());
		float diagonalMagnitude = horizontalSpeed(diagonalRollPitch.x(), diagonalRollPitch.z());
		float diagonalWork = diagonalRollPitch.x() * -16.0f + diagonalRollPitch.z() * 16.0f;

		assertEquals(0.0f, straightPitch.x(), 1.0e-6f);
		assertTrue(straightPitch.z() < -0.12f, "straightZ=" + straightPitch.z());
		assertTrue(straightPitch.z() > -0.26f, "straightZ=" + straightPitch.z());
		assertTrue(diagonalRollPitch.x() > 0.70f, "diagonalX=" + diagonalRollPitch.x());
		assertTrue(diagonalRollPitch.x() < 1.05f, "diagonalX=" + diagonalRollPitch.x());
		assertTrue(diagonalRollPitch.z() < -0.70f, "diagonalZ=" + diagonalRollPitch.z());
		assertTrue(diagonalRollPitch.z() > -1.05f, "diagonalZ=" + diagonalRollPitch.z());
		assertTrue(diagonalMagnitude > straightMagnitude * 2.0f,
				"diagonalMagnitude=" + diagonalMagnitude + " straightMagnitude=" + straightMagnitude);
		assertTrue(diagonalWork < -22.0f, "diagonalWork=" + diagonalWork);
		assertTrue(diagonalWork > -34.0f, "diagonalWork=" + diagonalWork);
	}

	@Test
	void acroThrustVectorTurnLoadOnlyAppearsWhenThrustTurnsFastVelocity() {
		PlayableFlightModel.Velocity lowSpeedTurn = PlayableFlightModel.acroThrustVectorTurnLoadAcceleration(
				0.0f,
				6.0f,
				8.0f,
				0.0f
		);
		PlayableFlightModel.Velocity alignedFastThrust = PlayableFlightModel.acroThrustVectorTurnLoadAcceleration(
				0.0f,
				25.0f,
				0.0f,
				8.0f
		);
		PlayableFlightModel.Velocity fastTurn = PlayableFlightModel.acroThrustVectorTurnLoadAcceleration(
				0.0f,
				25.0f,
				8.0f,
				0.0f
		);

		assertEquals(0.0f, lowSpeedTurn.z(), 1.0e-6f);
		assertEquals(0.0f, alignedFastThrust.z(), 1.0e-6f);
		assertEquals(0.0f, fastTurn.x(), 1.0e-6f);
		assertTrue(fastTurn.z() < -1.35f, "fastTurnZ=" + fastTurn.z());
		assertTrue(fastTurn.z() > -1.52f, "fastTurnZ=" + fastTurn.z());
	}

	@Test
	void acroThrustVectorTurnLoadMakesDiagonalSlipPayForChangingTrack() {
		PlayableFlightModel.Velocity alignedDiagonal = PlayableFlightModel.acroThrustVectorTurnLoadAcceleration(
				16.0f,
				16.0f,
				6.0f,
				6.0f
		);
		PlayableFlightModel.Velocity sideThrustDiagonal = PlayableFlightModel.acroThrustVectorTurnLoadAcceleration(
				16.0f,
				16.0f,
				8.0f,
				0.0f
		);
		float loadMagnitude = horizontalSpeed(sideThrustDiagonal.x(), sideThrustDiagonal.z());
		float workAlongVelocity = sideThrustDiagonal.x() * 16.0f + sideThrustDiagonal.z() * 16.0f;

		assertEquals(0.0f, alignedDiagonal.x(), 1.0e-6f);
		assertEquals(0.0f, alignedDiagonal.z(), 1.0e-6f);
		assertTrue(sideThrustDiagonal.x() < -0.37f, "sideThrustDiagonalX=" + sideThrustDiagonal.x());
		assertTrue(sideThrustDiagonal.x() > -0.58f, "sideThrustDiagonalX=" + sideThrustDiagonal.x());
		assertEquals(sideThrustDiagonal.x(), sideThrustDiagonal.z(), 1.0e-6f);
		assertTrue(loadMagnitude > 0.58f, "loadMagnitude=" + loadMagnitude);
		assertTrue(loadMagnitude < 0.80f, "loadMagnitude=" + loadMagnitude);
		assertTrue(workAlongVelocity < -13.0f, "workAlongVelocity=" + workAlongVelocity);
		assertTrue(workAlongVelocity > -18.5f, "workAlongVelocity=" + workAlongVelocity);
	}

	@Test
	void acroRotorSidewashTurnCurvesWithoutAddingPlanarEnergy() {
		PlayableFlightModel.Velocity alignedDiagonal = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				16.0f,
				16.0f,
				7.0f,
				7.0f,
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f
		);
		PlayableFlightModel.Velocity bankedStraight = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				0.0f,
				25.0f,
				-14.0f,
				0.0f,
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				1.0f
		);
		PlayableFlightModel.Velocity freshDiagonal = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				16.0f,
				16.0f,
				-8.0f,
				8.0f,
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f
		);
		PlayableFlightModel.Velocity settledDiagonal = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				16.0f,
				16.0f,
				-8.0f,
				8.0f,
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f
		);

		float settledWork = settledDiagonal.x() * 16.0f + settledDiagonal.z() * 16.0f;
		float bankedMagnitude = horizontalSpeed(bankedStraight.x(), bankedStraight.z());
		float freshMagnitude = horizontalSpeed(freshDiagonal.x(), freshDiagonal.z());
		float settledMagnitude = horizontalSpeed(settledDiagonal.x(), settledDiagonal.z());

		assertEquals(0.0f, alignedDiagonal.x(), 1.0e-6f);
		assertEquals(0.0f, alignedDiagonal.z(), 1.0e-6f);
		assertTrue(bankedStraight.x() < -0.24f, "bankedStraightX=" + bankedStraight.x());
		assertTrue(bankedStraight.x() > -0.36f, "bankedStraightX=" + bankedStraight.x());
		assertEquals(0.0f, bankedStraight.z(), 1.0e-6f);
		assertTrue(freshMagnitude > 0.19f, "freshMagnitude=" + freshMagnitude);
		assertTrue(freshMagnitude < 0.28f, "freshMagnitude=" + freshMagnitude);
		assertTrue(settledMagnitude > 0.76f, "settledMagnitude=" + settledMagnitude);
		assertTrue(settledMagnitude < 0.90f, "settledMagnitude=" + settledMagnitude);
		assertTrue(settledMagnitude > freshMagnitude * 2.8f,
				"settledMagnitude=" + settledMagnitude + " freshMagnitude=" + freshMagnitude);
		assertTrue(settledDiagonal.x() < -0.48f, "settledX=" + settledDiagonal.x());
		assertTrue(settledDiagonal.z() > 0.48f, "settledZ=" + settledDiagonal.z());
		assertEquals(0.0f, settledWork, 1.0e-4f);
	}

	@Test
	void acroRotorSidewashTurnBuildsThroughMediumSpeedGateExitWithoutAddingEnergy() {
		PlayableFlightModel.Velocity mediumDiagonal = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				8.5f,
				8.5f,
				-7.0f,
				7.0f,
				new PlayableFlightModel.Velocity(8.5f, 0.0f, 8.5f),
				1.0f
		);
		float magnitude = horizontalSpeed(mediumDiagonal.x(), mediumDiagonal.z());
		float workAlongVelocity = mediumDiagonal.x() * 8.5f + mediumDiagonal.z() * 8.5f;

		assertTrue(magnitude > 0.075f, "magnitude=" + magnitude);
		assertTrue(magnitude < 0.135f, "magnitude=" + magnitude);
		assertTrue(mediumDiagonal.x() < -0.050f, "mediumX=" + mediumDiagonal.x());
		assertTrue(mediumDiagonal.z() > 0.050f, "mediumZ=" + mediumDiagonal.z());
		assertEquals(0.0f, workAlongVelocity, 1.0e-4f);
	}

	@Test
	void acroSidewashMemoryDelaysYawRotorSidewashTurnWithoutHidingPitchAoa() {
		PlayableFlightModel.Velocity yawFresh = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				16.0f,
				16.0f,
				-8.0f,
				8.0f,
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity yawSettled = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				16.0f,
				16.0f,
				-8.0f,
				8.0f,
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity pitchFresh = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				0.0f,
				25.0f,
				-8.0f,
				0.0f,
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity pitchSettled = PlayableFlightModel.acroRotorSidewashTurnAcceleration(
				0.0f,
				25.0f,
				-8.0f,
				0.0f,
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				1.0f,
				1.0f
		);
		float yawFreshMagnitude = horizontalSpeed(yawFresh.x(), yawFresh.z());
		float yawSettledMagnitude = horizontalSpeed(yawSettled.x(), yawSettled.z());
		float pitchFreshMagnitude = horizontalSpeed(pitchFresh.x(), pitchFresh.z());

		assertTrue(yawFreshMagnitude < yawSettledMagnitude * 0.58f,
				"yawFresh=" + yawFreshMagnitude + " yawSettled=" + yawSettledMagnitude);
		assertTrue(yawFreshMagnitude > yawSettledMagnitude * 0.42f,
				"yawFresh=" + yawFreshMagnitude + " yawSettled=" + yawSettledMagnitude);
		assertEquals(pitchSettled.x(), pitchFresh.x(), 1.0e-6f);
		assertEquals(pitchSettled.z(), pitchFresh.z(), 1.0e-6f);
		assertTrue(pitchFreshMagnitude > yawFreshMagnitude * 1.35f,
				"pitchFresh=" + pitchFreshMagnitude + " yawFresh=" + yawFreshMagnitude);
		assertTrue(pitchFresh.x() < -0.55f, "pitchFreshX=" + pitchFresh.x());
		assertTrue(pitchFresh.x() > -0.68f, "pitchFreshX=" + pitchFresh.x());
	}

	@Test
	void acroThrustVectorTurnLoadAddsEnergyCostWithoutChangingTurnDirection() {
		PlayableFlightModel.State fastForward = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				(float) Math.toRadians(45.0),
				0.0f,
				FlightMode.ACRO,
				0,
				2.30f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step bankedTurn = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.62f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				fastForward
		);

		assertTrue(bankedTurn.velocityX() < -0.18f, "velocityX=" + bankedTurn.velocityX());
		assertTrue(bankedTurn.velocityZ() < 24.9f, "velocityZ=" + bankedTurn.velocityZ());
		assertTrue(bankedTurn.velocityZ() > 24.2f, "velocityZ=" + bankedTurn.velocityZ());
	}

	@Test
	void acroDiagonalHighSpeedFlowGetsExtraSeparatedDragAndSideforce() {
		PlayableFlightModel.Velocity forward = PlayableFlightModel.acroBodyAerodynamicAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f)
		);
		PlayableFlightModel.Velocity diagonal = PlayableFlightModel.acroBodyAerodynamicAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f)
		);

		assertEquals(0.0f, forward.x(), 1.0e-6f);
		assertTrue(forward.z() < -5.5f, "forwardZ=" + forward.z());
		assertTrue(forward.z() > -6.3f, "forwardZ=" + forward.z());
		assertTrue(diagonal.x() < -9.2f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.x() > -10.8f, "diagonalX=" + diagonal.x());
		assertTrue(diagonal.z() < -2.25f, "diagonalZ=" + diagonal.z());
		assertTrue(diagonal.z() > -3.20f, "diagonalZ=" + diagonal.z());
		assertTrue(Math.abs(diagonal.x()) > Math.abs(diagonal.z()) + 6.8f, "diagonalX=" + diagonal.x() + " diagonalZ=" + diagonal.z());
	}

	@Test
	void acroLaggedCrossflowReducesFirstTickBroadsideDragWithoutRemovingBaseDrag() {
		PlayableFlightModel.Velocity bodyVelocity = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity baseOnly = PlayableFlightModel.acroBodyAerodynamicAcceleration(bodyVelocity, 0.0f);
		PlayableFlightModel.Velocity firstTick = PlayableFlightModel.acroBodyAerodynamicAcceleration(bodyVelocity, 0.32f);
		PlayableFlightModel.Velocity settled = PlayableFlightModel.acroBodyAerodynamicAcceleration(bodyVelocity, 1.0f);

		assertTrue(baseOnly.x() < -2.65f, "baseOnlyX=" + baseOnly.x());
		assertTrue(baseOnly.x() > -2.95f, "baseOnlyX=" + baseOnly.x());
		assertTrue(firstTick.x() < baseOnly.x() - 1.0f, "firstTickX=" + firstTick.x() + " baseOnlyX=" + baseOnly.x());
		assertTrue(firstTick.x() > settled.x() + 2.0f, "firstTickX=" + firstTick.x() + " settledX=" + settled.x());
		assertTrue(settled.x() < -7.7f, "settledX=" + settled.x());
	}

	@Test
	void acroSidewashMemoryDelaysSeparatedBodyLoadsAfterFastSlipEntry() {
		PlayableFlightModel.Velocity bodyVelocity = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity freshSlip = PlayableFlightModel.acroBodyAerodynamicAcceleration(
				bodyVelocity,
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity settledSlip = PlayableFlightModel.acroBodyAerodynamicAcceleration(
				bodyVelocity,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity pitchFresh = PlayableFlightModel.acroBodyAerodynamicAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity pitchSettled = PlayableFlightModel.acroBodyAerodynamicAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				1.0f,
				1.0f
		);

		assertTrue(Math.abs(freshSlip.x()) < Math.abs(settledSlip.x()) * 0.90f,
				"freshX=" + freshSlip.x() + " settledX=" + settledSlip.x());
		assertTrue(Math.abs(freshSlip.x()) > Math.abs(settledSlip.x()) * 0.72f,
				"freshX=" + freshSlip.x() + " settledX=" + settledSlip.x());
		assertTrue(settledSlip.x() < -9.4f, "settledX=" + settledSlip.x());
		assertEquals(pitchSettled.y(), pitchFresh.y(), 1.0e-6f);
		assertEquals(pitchSettled.z(), pitchFresh.z(), 1.0e-6f);
	}

	@Test
	void acroBaseBodyDragKeepsStraightCruiseInstantButDelaysBroadsideArea() {
		PlayableFlightModel.Velocity straightInitial = PlayableFlightModel.acroBaseBodyDragAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				0.0f
		);
		PlayableFlightModel.Velocity straightSettled = PlayableFlightModel.acroBaseBodyDragAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				1.0f
		);
		PlayableFlightModel.Velocity sideInitial = PlayableFlightModel.acroBaseBodyDragAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f
		);
		PlayableFlightModel.Velocity sideSettled = PlayableFlightModel.acroBaseBodyDragAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f
		);

		assertEquals(straightSettled.z(), straightInitial.z(), 1.0e-6f);
		assertEquals(0.0f, straightInitial.x(), 1.0e-6f);
		assertTrue(sideInitial.x() > sideSettled.x() + 2.8f,
				"sideInitialX=" + sideInitial.x() + " sideSettledX=" + sideSettled.x());
		assertTrue(sideInitial.z() < -2.65f, "sideInitialZ=" + sideInitial.z());
		assertTrue(sideInitial.z() > -2.95f, "sideInitialZ=" + sideInitial.z());
	}

	@Test
	void acroFreshDiagonalSlipCarriesMoreSideInertiaThanSettledCrossflow() {
		PlayableFlightModel.State freshSlip = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				0.0f
		);
		PlayableFlightModel.State settledSlip = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.Step fresh = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				freshSlip
		);
		PlayableFlightModel.Step settled = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				settledSlip
		);

		assertTrue(fresh.acroAeroCrossflowLag() < settled.acroAeroCrossflowLag(),
				"freshLag=" + fresh.acroAeroCrossflowLag() + " settledLag=" + settled.acroAeroCrossflowLag());
		assertTrue(fresh.velocityX() > settled.velocityX() + 0.10f,
				"freshX=" + fresh.velocityX() + " settledX=" + settled.velocityX());
		assertTrue(fresh.velocityX() < 16.0f, "freshX=" + fresh.velocityX());
		assertTrue(settled.velocityX() < 15.7f, "settledX=" + settled.velocityX());
	}

	@Test
	void acroCoupledDynamicPressureDragLoadsCrossflowWithoutChangingStraightCruise() {
		PlayableFlightModel.Velocity straight = PlayableFlightModel.acroCoupledDynamicPressureDragAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f)
		);
		PlayableFlightModel.Velocity lowSpeedDiagonal = PlayableFlightModel.acroCoupledDynamicPressureDragAcceleration(
				new PlayableFlightModel.Velocity(3.0f, 0.0f, 3.0f)
		);
		PlayableFlightModel.Velocity fastDiagonal = PlayableFlightModel.acroCoupledDynamicPressureDragAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f)
		);

		assertEquals(0.0f, straight.x(), 1.0e-6f);
		assertEquals(0.0f, straight.z(), 1.0e-6f);
		assertEquals(0.0f, lowSpeedDiagonal.x(), 1.0e-6f);
		assertEquals(0.0f, lowSpeedDiagonal.z(), 1.0e-6f);
		assertTrue(fastDiagonal.x() < -0.78f, "fastDiagonalX=" + fastDiagonal.x());
		assertTrue(fastDiagonal.x() > -1.05f, "fastDiagonalX=" + fastDiagonal.x());
		assertTrue(fastDiagonal.z() < -0.30f, "fastDiagonalZ=" + fastDiagonal.z());
		assertTrue(fastDiagonal.z() > -0.50f, "fastDiagonalZ=" + fastDiagonal.z());
	}

	@Test
	void acroSidewashMemoryDelaysYawCoupledDynamicPressureWithoutHidingPitchAoa() {
		PlayableFlightModel.Velocity freshYaw = PlayableFlightModel.acroCoupledDynamicPressureDragAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity settledYaw = PlayableFlightModel.acroCoupledDynamicPressureDragAcceleration(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f,
				1.0f
		);
		PlayableFlightModel.Velocity freshPitch = PlayableFlightModel.acroCoupledDynamicPressureDragAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				1.0f,
				0.0f
		);
		PlayableFlightModel.Velocity settledPitch = PlayableFlightModel.acroCoupledDynamicPressureDragAcceleration(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				1.0f,
				1.0f
		);

		assertTrue(Math.abs(freshYaw.x()) < Math.abs(settledYaw.x()) * 0.45f,
				"freshYawX=" + freshYaw.x() + " settledYawX=" + settledYaw.x());
		assertTrue(Math.abs(freshYaw.x()) > Math.abs(settledYaw.x()) * 0.25f,
				"freshYawX=" + freshYaw.x() + " settledYawX=" + settledYaw.x());
		assertTrue(settledYaw.x() < -0.78f, "settledYawX=" + settledYaw.x());
		assertEquals(settledPitch.y(), freshPitch.y(), 1.0e-6f);
		assertEquals(settledPitch.z(), freshPitch.z(), 1.0e-6f);
	}

	@Test
	void acroHighSpeedCoastPreservesInertiaDistanceWhileSideslipWashesOut() {
		PlayableFlightModel.Step forwardCoast = runFrom(
				FlightMode.ACRO,
				20,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						25.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f
				)
		);
		PlayableFlightModel.Step diagonalCoast = runFrom(
				FlightMode.ACRO,
				20,
				0.20f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						16.0f,
						0.0f,
						16.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f
				)
		);
		float diagonalSpeed = horizontalSpeed(diagonalCoast.velocityX(), diagonalCoast.velocityZ());

		assertTrue(forwardCoast.velocityZ() > 18.5f, "forwardCoastZ=" + forwardCoast.velocityZ());
		assertTrue(forwardCoast.velocityZ() < 23.5f, "forwardCoastZ=" + forwardCoast.velocityZ());
		assertTrue(diagonalSpeed > 15.5f, "diagonalSpeed=" + diagonalSpeed);
		assertTrue(diagonalSpeed < 18.5f, "diagonalSpeed=" + diagonalSpeed);
		assertTrue(Math.abs(diagonalCoast.velocityX()) < Math.abs(diagonalCoast.velocityZ()) * 0.84f,
				"diagonalX=" + diagonalCoast.velocityX() + " diagonalZ=" + diagonalCoast.velocityZ());
	}

	@Test
	void acroDiagonalCoastKeepsInertiaButCurvesTowardBodyForward() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step step = null;
		float trackDistanceMeters = 0.0f;
		float speedAfterOneSecond = Float.NaN;
		float lateralAfterOneSecond = Float.NaN;
		float forwardAfterOneSecond = Float.NaN;
		for (int tick = 1; tick <= 40; tick++) {
			step = PlayableFlightModel.step(
					FlightMode.ACRO,
					0.20f,
					0.0f,
					0.0f,
					0.0f,
					0.20f,
					false,
					state
			);
			trackDistanceMeters += horizontalSpeed(step.velocityX(), step.velocityZ()) * 0.05f;
			state = stateFrom(step);
			if (tick == 20) {
				speedAfterOneSecond = horizontalSpeed(step.velocityX(), step.velocityZ());
				lateralAfterOneSecond = step.velocityX();
				forwardAfterOneSecond = step.velocityZ();
			}
		}

		float speedAfterTwoSeconds = step == null ? Float.NaN : horizontalSpeed(step.velocityX(), step.velocityZ());
		float lateralAfterTwoSeconds = step == null ? Float.NaN : step.velocityX();
		float forwardAfterTwoSeconds = step == null ? Float.NaN : step.velocityZ();

		assertTrue(speedAfterOneSecond > 15.0f, "speedAfterOneSecond=" + speedAfterOneSecond);
		assertTrue(Math.abs(lateralAfterOneSecond) < Math.abs(forwardAfterOneSecond) * 0.82f,
				"lateralAfterOneSecond=" + lateralAfterOneSecond + " forwardAfterOneSecond=" + forwardAfterOneSecond);
		assertTrue(speedAfterTwoSeconds > 10.5f, "speedAfterTwoSeconds=" + speedAfterTwoSeconds);
		assertTrue(trackDistanceMeters > 29.0f, "trackDistanceMeters=" + trackDistanceMeters);
		assertTrue(Math.abs(lateralAfterTwoSeconds) < Math.abs(forwardAfterTwoSeconds) * 0.68f,
				"lateralAfterTwoSeconds=" + lateralAfterTwoSeconds + " forwardAfterTwoSeconds=" + forwardAfterTwoSeconds);
		assertTrue(forwardAfterTwoSeconds > 9.0f, "forwardAfterTwoSeconds=" + forwardAfterTwoSeconds);
	}

	@Test
	void acroDiagonalReleaseDoesNotBecomeLongPoweredStrafe() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step step = null;
		float trackDistanceMeters = 0.0f;
		float secondsToFiveMetersPerSecond = Float.NaN;
		float distanceToFiveMetersPerSecond = Float.NaN;
		float rollAtFiveMetersPerSecond = Float.NaN;
		for (int tick = 1; tick <= 180; tick++) {
			step = PlayableFlightModel.step(
					FlightMode.ACRO,
					0.20f,
					0.0f,
					0.0f,
					0.0f,
					0.20f,
					false,
					state
			);
			float speed = horizontalSpeed(step.velocityX(), step.velocityZ());
			trackDistanceMeters += speed * 0.05f;
			if (Float.isNaN(secondsToFiveMetersPerSecond) && speed <= 5.0f) {
				secondsToFiveMetersPerSecond = tick * 0.05f;
				distanceToFiveMetersPerSecond = trackDistanceMeters;
				rollAtFiveMetersPerSecond = step.rollRadians();
				break;
			}
			state = stateFrom(step);
		}

		assertTrue(!Float.isNaN(secondsToFiveMetersPerSecond), "finalSpeed=" + (step == null ? Float.NaN : horizontalSpeed(step.velocityX(), step.velocityZ())));
		assertTrue(secondsToFiveMetersPerSecond > 6.40f, "secondsToFive=" + secondsToFiveMetersPerSecond);
		assertTrue(secondsToFiveMetersPerSecond < 8.20f, "secondsToFive=" + secondsToFiveMetersPerSecond);
		assertTrue(distanceToFiveMetersPerSecond > 70.0f, "distanceToFive=" + distanceToFiveMetersPerSecond);
		assertTrue(distanceToFiveMetersPerSecond < 84.0f, "distanceToFive=" + distanceToFiveMetersPerSecond);
		assertTrue(Math.abs(rollAtFiveMetersPerSecond) < Math.toRadians(7.5f),
				"rollAtFiveDeg=" + Math.toDegrees(rollAtFiveMetersPerSecond));
	}

	@Test
	void acroBroadsideCoastKeepsInertiaWithoutBecomingFreeSideSlide() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(
				20.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step step = null;
		float trackDistanceMeters = 0.0f;
		float speedAfterOneSecond = Float.NaN;
		float speedAfterTwoSeconds = Float.NaN;
		float secondsToFiveMetersPerSecond = Float.NaN;
		float distanceToFiveMetersPerSecond = Float.NaN;
		for (int tick = 1; tick <= 80; tick++) {
			step = PlayableFlightModel.step(
					FlightMode.ACRO,
					0.20f,
					0.0f,
					0.0f,
					0.0f,
					0.20f,
					false,
					state
			);
			float speed = horizontalSpeed(step.velocityX(), step.velocityZ());
			trackDistanceMeters += speed * 0.05f;
			if (tick == 20) {
				speedAfterOneSecond = speed;
			}
			if (tick == 40) {
				speedAfterTwoSeconds = speed;
			}
			if (Float.isNaN(secondsToFiveMetersPerSecond) && speed <= 5.0f) {
				secondsToFiveMetersPerSecond = tick * 0.05f;
				distanceToFiveMetersPerSecond = trackDistanceMeters;
				break;
			}
			state = stateFrom(step);
		}

		assertTrue(speedAfterOneSecond > 11.2f, "speedAfterOneSecond=" + speedAfterOneSecond);
		assertTrue(speedAfterOneSecond < 12.2f, "speedAfterOneSecond=" + speedAfterOneSecond);
		assertTrue(speedAfterTwoSeconds > 7.0f, "speedAfterTwoSeconds=" + speedAfterTwoSeconds);
		assertTrue(speedAfterTwoSeconds < 7.8f, "speedAfterTwoSeconds=" + speedAfterTwoSeconds);
		assertTrue(secondsToFiveMetersPerSecond > 2.70f, "secondsToFive=" + secondsToFiveMetersPerSecond);
		assertTrue(secondsToFiveMetersPerSecond < 3.25f, "secondsToFive=" + secondsToFiveMetersPerSecond);
		assertTrue(distanceToFiveMetersPerSecond > 29.0f, "distanceToFive=" + distanceToFiveMetersPerSecond);
		assertTrue(distanceToFiveMetersPerSecond < 32.0f, "distanceToFive=" + distanceToFiveMetersPerSecond);
		assertTrue(step != null && Math.abs(step.velocityZ()) < 1.0e-4f, "velocityZ=" + (step == null ? Float.NaN : step.velocityZ()));
	}

	@Test
	void acroWeathercockYawClearlyTurnsNoseIntoSideslip() {
		float straight = PlayableFlightModel.acroSideslipWeathercockYawDegreesPerTick(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f)
		);
		float pureSide = PlayableFlightModel.acroSideslipWeathercockYawDegreesPerTick(
				new PlayableFlightModel.Velocity(18.0f, 0.0f, 0.0f)
		);
		float rightSlip = PlayableFlightModel.acroSideslipWeathercockYawDegreesPerTick(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f)
		);
		float leftSlip = PlayableFlightModel.acroSideslipWeathercockYawDegreesPerTick(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f)
		);

		assertEquals(0.0f, straight, 1.0e-6f);
		assertTrue(pureSide < -0.20f, "pureSide=" + pureSide);
		assertTrue(pureSide > -0.30f, "pureSide=" + pureSide);
		assertTrue(rightSlip < -0.46f, "rightSlip=" + rightSlip);
		assertTrue(rightSlip > -0.58f, "rightSlip=" + rightSlip);
		assertTrue(Math.abs(pureSide) < Math.abs(rightSlip) * 0.66f, "pureSide=" + pureSide + " rightSlip=" + rightSlip);
		assertEquals(-rightSlip, leftSlip, 1.0e-6f);
	}

	@Test
	void acroSideslipYawDampingScalesWithForwardAndBroadsideSlip() {
		float straight = PlayableFlightModel.acroSideslipYawDampingSmoothing(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f)
		);
		float pureSide = PlayableFlightModel.acroSideslipYawDampingSmoothing(
				new PlayableFlightModel.Velocity(18.0f, 0.0f, 0.0f)
		);
		float diagonal = PlayableFlightModel.acroSideslipYawDampingSmoothing(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f)
		);

		assertEquals(0.0f, straight, 1.0e-6f);
		assertTrue(pureSide > 0.055f, "pureSide=" + pureSide);
		assertTrue(pureSide < 0.085f, "pureSide=" + pureSide);
		assertTrue(diagonal > 0.10f, "diagonal=" + diagonal);
		assertTrue(diagonal <= 0.13f, "diagonal=" + diagonal);
	}

	@Test
	void acroForwardSideslipAddsClearPassiveYawWithoutStealingActiveYaw() {
		PlayableFlightModel.State slipping = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Step passive = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				slipping
		);
		PlayableFlightModel.Step activeYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				slipping
		);

		assertTrue(passive.yawDegreesPerTick() < -0.46f, "passiveYaw=" + passive.yawDegreesPerTick());
		assertTrue(passive.yawDegreesPerTick() > -0.58f, "passiveYaw=" + passive.yawDegreesPerTick());
		assertTrue(activeYaw.yawDegreesPerTick() > 4.30f, "activeYaw=" + activeYaw.yawDegreesPerTick());
	}

	@Test
	void acroSidewashMemoryDelaysPassiveWeathercockYawAfterFastSlipEntry() {
		PlayableFlightModel.State freshSlip = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f,
				0.0f
		);
		PlayableFlightModel.State settledSlip = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Step fresh = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				freshSlip
		);
		PlayableFlightModel.Step settled = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				settledSlip
		);

		assertTrue(fresh.acroAeroCrossflowLag() > 0.94f, "freshLag=" + fresh.acroAeroCrossflowLag());
		assertTrue(fresh.acroSidewashMemory() < 0.25f, "freshMemory=" + fresh.acroSidewashMemory());
		assertTrue(settled.acroSidewashMemory() > 0.96f, "settledMemory=" + settled.acroSidewashMemory());
		assertTrue(fresh.yawDegreesPerTick() < -0.12f, "freshYaw=" + fresh.yawDegreesPerTick());
		assertTrue(fresh.yawDegreesPerTick() > settled.yawDegreesPerTick() * 0.46f,
				"freshYaw=" + fresh.yawDegreesPerTick() + " settledYaw=" + settled.yawDegreesPerTick());
		assertTrue(settled.yawDegreesPerTick() < -0.46f, "settledYaw=" + settled.yawDegreesPerTick());
	}

	@Test
	void acroBroadsideSlipAddsPassiveYawWithoutStealingActiveYaw() {
		PlayableFlightModel.State slipping = new PlayableFlightModel.State(
				18.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f,
				1.0f
		);
		PlayableFlightModel.Step passive = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				slipping
		);
		PlayableFlightModel.Step activeYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				slipping
		);

		assertTrue(passive.yawDegreesPerTick() < -0.20f, "passiveYaw=" + passive.yawDegreesPerTick());
		assertTrue(passive.yawDegreesPerTick() > -0.30f, "passiveYaw=" + passive.yawDegreesPerTick());
		assertTrue(activeYaw.yawDegreesPerTick() > 4.5f, "activeYaw=" + activeYaw.yawDegreesPerTick());
	}

	@Test
	void smallAcroYawCommandKeepsCommandedDirectionDuringOpposingSideslip() {
		PlayableFlightModel.Step rightCommand = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.08f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						16.0f,
						0.0f,
						16.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f,
						0,
						1.0f,
						1.0f
				)
		);
		PlayableFlightModel.Step leftCommand = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				-0.08f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						-16.0f,
						0.0f,
						16.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f,
						0,
						1.0f,
						1.0f
				)
		);

		assertTrue(rightCommand.yawDegreesPerTick() > 0.0f, "rightCommandYaw=" + rightCommand.yawDegreesPerTick());
		assertTrue(leftCommand.yawDegreesPerTick() < 0.0f, "leftCommandYaw=" + leftCommand.yawDegreesPerTick());
	}

	@Test
	void activeAcroPitchDoesNotInheritPassiveRollFromYawSideslip() {
		PlayableFlightModel.Step pitchOnly = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.55f,
				0.55f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						16.0f,
						0.0f,
						16.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f,
						0,
						1.0f,
						1.0f
				)
		);

		assertTrue(Math.abs(pitchOnly.acroPitchRateRadiansPerTick()) > Math.toRadians(2.0),
				"pitchRateDeg=" + Math.toDegrees(pitchOnly.acroPitchRateRadiansPerTick()));
		assertTrue(Math.abs(pitchOnly.acroRollRateRadiansPerTick()) < Math.toRadians(0.12),
				"rollRateDeg=" + Math.toDegrees(pitchOnly.acroRollRateRadiansPerTick()));
		assertTrue(Math.abs(pitchOnly.rollRadians()) < Math.toRadians(0.12),
				"rollDeg=" + Math.toDegrees(pitchOnly.rollRadians()));
	}

	@Test
	void acroSideslipYawCommandLoadMakesHighSpeedYawFeelHeavier() {
		float straight = PlayableFlightModel.acroSideslipYawCommandLoad(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f)
		);
		float lowSpeedSide = PlayableFlightModel.acroSideslipYawCommandLoad(
				new PlayableFlightModel.Velocity(6.0f, 0.0f, 6.0f)
		);
		float diagonal = PlayableFlightModel.acroSideslipYawCommandLoad(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f)
		);
		float broadside = PlayableFlightModel.acroSideslipYawCommandLoad(
				new PlayableFlightModel.Velocity(18.0f, 0.0f, 0.0f)
		);
		PlayableFlightModel.Step calmYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step slippingYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						16.0f,
						0.0f,
						16.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f,
						0,
						1.0f,
						1.0f
				)
		);

		assertEquals(0.0f, straight, 1.0e-6f);
		assertEquals(0.0f, lowSpeedSide, 1.0e-6f);
		assertTrue(diagonal > 0.06f, "diagonal=" + diagonal);
		assertTrue(diagonal < 0.09f, "diagonal=" + diagonal);
		assertTrue(broadside > 0.035f, "broadside=" + broadside);
		assertTrue(broadside < 0.06f, "broadside=" + broadside);
		assertTrue(slippingYaw.yawDegreesPerTick() < calmYaw.yawDegreesPerTick() * 0.96f,
				"slippingYaw=" + slippingYaw.yawDegreesPerTick() + " calmYaw=" + calmYaw.yawDegreesPerTick());
		assertTrue(slippingYaw.yawDegreesPerTick() > calmYaw.yawDegreesPerTick() * 0.86f,
				"slippingYaw=" + slippingYaw.yawDegreesPerTick() + " calmYaw=" + calmYaw.yawDegreesPerTick());
	}

	@Test
	void acroYawRateInertiaLoadsSettledCrossflowWithoutKillingYawAuthority() {
		float hoverScale = PlayableFlightModel.acroYawRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 0.0f),
				0.45f,
				0.20f,
				0.0f
		);
		float idleScale = PlayableFlightModel.acroYawRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 0.0f),
				0.0f,
				0.20f,
				0.0f
		);
		float straightScale = PlayableFlightModel.acroYawRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				0.45f,
				0.20f,
				1.0f
		);
		float freshDiagonalScale = PlayableFlightModel.acroYawRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.45f,
				0.20f,
				0.32f
		);
		float settledDiagonalScale = PlayableFlightModel.acroYawRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.45f,
				0.20f,
				1.0f
		);
		PlayableFlightModel.Step calmYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step settledSlipYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						16.0f,
						0.0f,
						16.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f,
						0,
						1.0f
				)
		);

		assertEquals(1.0f, hoverScale, 1.0e-6f);
		assertTrue(idleScale > 0.89f, "idleScale=" + idleScale);
		assertTrue(idleScale < 0.91f, "idleScale=" + idleScale);
		assertTrue(straightScale > 0.96f, "straightScale=" + straightScale);
		assertTrue(straightScale < hoverScale, "straightScale=" + straightScale);
		assertTrue(freshDiagonalScale < straightScale, "freshDiagonalScale=" + freshDiagonalScale + " straightScale=" + straightScale);
		assertTrue(freshDiagonalScale > 0.94f, "freshDiagonalScale=" + freshDiagonalScale);
		assertTrue(settledDiagonalScale < freshDiagonalScale, "settledDiagonalScale=" + settledDiagonalScale + " freshDiagonalScale=" + freshDiagonalScale);
		assertTrue(settledDiagonalScale > 0.90f, "settledDiagonalScale=" + settledDiagonalScale);
		assertTrue(settledSlipYaw.yawDegreesPerTick() < calmYaw.yawDegreesPerTick() * 0.89f,
				"settledSlipYaw=" + settledSlipYaw.yawDegreesPerTick() + " calmYaw=" + calmYaw.yawDegreesPerTick());
		assertTrue(settledSlipYaw.yawDegreesPerTick() > calmYaw.yawDegreesPerTick() * 0.82f,
				"settledSlipYaw=" + settledSlipYaw.yawDegreesPerTick() + " calmYaw=" + calmYaw.yawDegreesPerTick());
	}

	@Test
	void exactBodyRotationKeepsPitchAndRollDirectionsAtVerticalAndInvertedAttitudes() {
		for (float pitchDegrees : new float[] { -181, -179, -91, -90, -89, -73, 0, 73, 89, 90, 91, 179, 181, 359, 361 }) {
			for (float rollDegrees : new float[] { -100, -80, 0, 80, 100 }) {
				for (float[] rates : new float[][] { { 10, 0 }, { -10, 0 }, { 0, 10 }, { 0, -10 }, { 10, -8 }, { 0.06f, 0.04f } }) {
					float pitch = (float) Math.toRadians(pitchDegrees), roll = (float) Math.toRadians(rollDegrees);
					float pitchRate = (float) Math.toRadians(rates[0]), rollRate = (float) Math.toRadians(rates[1]);
					PlayableFlightModel.AcroBodyRateAttitudeDelta delta = PlayableFlightModel.acroBodyRateAttitudeDelta(pitch, roll, pitchRate, rollRate);
					Quaternion before = PlayableAttitude.quaternion(0.0, pitch, roll);
					Quaternion after = PlayableAttitude.quaternion(delta.yawDegreesPerTick(), pitch + delta.pitchRateRadiansPerTick(), roll + delta.rollRateRadiansPerTick());
					Quaternion bodyStep = before.conjugate().multiply(after).normalized();
					double canonical = bodyStep.w() < 0.0 ? -1.0 : 1.0;
					String scenario = "pitch=" + pitchDegrees + " roll=" + rollDegrees + " rates=" + rates[0] + "/" + rates[1];
					assertTrue(pitchRate == 0.0f || bodyStep.x() * canonical * pitchRate > 0.0, scenario);
					assertTrue(rollRate == 0.0f || bodyStep.z() * canonical * rollRate > 0.0, scenario);
					assertEquals(0.0, bodyStep.y(), 2.0e-6, scenario);
					assertBodyRotationBasis(before, after, pitchRate, rollRate, scenario);
				}
			}
		}
	}

	@Test
	void repeatedMixedBodyRotationsReconstructTheExactAttitudeAcrossVerticalBoundaries() {
		float pitch = (float) Math.toRadians(73.0), roll = (float) Math.toRadians(80.0);
		double yaw = 37.0;
		Quaternion expected = PlayableAttitude.quaternion(yaw, pitch, roll);
		for (int tick = 0; tick < 180; tick++) {
			float pitchRate = (float) Math.toRadians(tick < 120 ? 10.0 : -8.0);
			float rollRate = (float) Math.toRadians(tick % 36 < 18 ? 3.0 : -3.0);
			PlayableFlightModel.AcroBodyRateAttitudeDelta delta = PlayableFlightModel.acroBodyRateAttitudeDelta(pitch, roll, pitchRate, rollRate);
			Quaternion before = PlayableAttitude.quaternion(yaw, pitch, roll);
			pitch += delta.pitchRateRadiansPerTick();
			roll += delta.rollRateRadiansPerTick();
			yaw += delta.yawDegreesPerTick();
			Quaternion after = PlayableAttitude.quaternion(yaw, pitch, roll);
			assertBodyRotationBasis(before, after, pitchRate, rollRate, "tick=" + tick);
			expected = expected.multiply(bodyIncrement(pitchRate, rollRate)).normalized();
			assertOrientationBasis(expected, after, 6.0e-5, "accumulated tick=" + tick);
		}
	}

	@Test
	void geometricHeadingDoesNotBecomeYawMomentumOnTheNextTick() {
		PlayableFlightModel.State previous = new PlayableFlightModel.State(
				0, 0, 0, (float) Math.toRadians(73.0), (float) Math.toRadians(80.0),
				0, FlightMode.ACRO, 0, 1, 0, 0, 0, 0, 0
		);
		PlayableFlightModel.Step turning = acroTurnStep(PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE, previous, 0.20f, 0.0f);
		assertTrue(Math.abs(turning.acroHeadingCouplingDegreesPerTick()) > 1.0f);
		PlayableFlightModel.Step released = acroTurnStep(PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE, stateFrom(turning), 0.0f, 0.0f);
		assertEquals(0.0f, released.yawDegreesPerTick() - released.acroHeadingCouplingDegreesPerTick(), 1.0e-5f);
	}

	private static Quaternion bodyIncrement(float pitchRate, float rollRate) {
		double angle = Math.hypot(pitchRate, rollRate);
		double scale = Math.sin(angle / 2.0) / angle;
		return new Quaternion(Math.cos(angle / 2.0), pitchRate * scale, 0.0, rollRate * scale);
	}

	private static void assertBodyRotationBasis(Quaternion before, Quaternion after, float pitchRate, float rollRate, String scenario) {
		assertOrientationBasis(before.multiply(bodyIncrement(pitchRate, rollRate)), after, 3.0e-6, scenario);
	}

	private static void assertOrientationBasis(Quaternion expected, Quaternion actual, double tolerance, String scenario) {
		for (Vec3 axis : new Vec3[] { new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1) }) {
			Vec3 expectedAxis = expected.rotate(axis), actualAxis = actual.rotate(axis);
			assertEquals(expectedAxis.x(), actualAxis.x(), tolerance, scenario);
			assertEquals(expectedAxis.y(), actualAxis.y(), tolerance, scenario);
			assertEquals(expectedAxis.z(), actualAxis.z(), tolerance, scenario);
		}
	}

	@Test
	void acroAerodynamicRateDampingOnlyBuildsAtFpvSpeeds() {
		float lowSpeed = PlayableFlightModel.acroAerodynamicRateDamping(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 6.0f),
				true
		);
		float fastForwardPitch = PlayableFlightModel.acroAerodynamicRateDamping(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				true
		);
		float fastForwardRoll = PlayableFlightModel.acroAerodynamicRateDamping(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				false
		);

		assertEquals(0.0f, lowSpeed, 1.0e-6f);
		assertTrue(fastForwardPitch > 0.055f, "fastForwardPitch=" + fastForwardPitch);
		assertTrue(fastForwardPitch < 0.095f, "fastForwardPitch=" + fastForwardPitch);
		assertTrue(fastForwardRoll > 0.040f, "fastForwardRoll=" + fastForwardRoll);
		assertTrue(fastForwardRoll < 0.075f, "fastForwardRoll=" + fastForwardRoll);
	}

	@Test
	void acroAerodynamicRateDampingWeightsDiagonalSlipMoreThanStraightCruise() {
		float straightRoll = PlayableFlightModel.acroAerodynamicRateDamping(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				false
		);
		float diagonalRoll = PlayableFlightModel.acroAerodynamicRateDamping(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				false
		);
		float pitchAoa = PlayableFlightModel.acroAerodynamicRateDamping(
				new PlayableFlightModel.Velocity(0.0f, 10.0f, 18.0f),
				true
		);
		float rawRate = (float) Math.toRadians(4.0);
		float dampedRate = PlayableFlightModel.acroAerodynamicRateDamped(
				rawRate,
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				false
		);

		assertTrue(diagonalRoll > straightRoll * 1.25f, "diagonalRoll=" + diagonalRoll + " straightRoll=" + straightRoll);
		assertTrue(diagonalRoll < 0.095f, "diagonalRoll=" + diagonalRoll);
		assertTrue(pitchAoa > 0.055f, "pitchAoa=" + pitchAoa);
		assertTrue(pitchAoa < 0.080f, "pitchAoa=" + pitchAoa);
		assertTrue(dampedRate < rawRate, "dampedRate=" + dampedRate + " rawRate=" + rawRate);
		assertTrue(dampedRate > rawRate * 0.90f, "dampedRate=" + dampedRate + " rawRate=" + rawRate);
	}

	@Test
	void acroRateInertiaLoadsHighSpeedDiagonalFlowWithoutTouchingLowSpeed() {
		float lowSpeedRoll = PlayableFlightModel.acroRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 6.0f),
				false
		);
		float straightCruiseRoll = PlayableFlightModel.acroRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				false
		);
		float diagonalRoll = PlayableFlightModel.acroRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				false
		);
		float pitchAoa = PlayableFlightModel.acroRateInertiaSmoothingScale(
				new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f),
				true
		);

		assertEquals(1.0f, lowSpeedRoll, 1.0e-6f);
		assertTrue(straightCruiseRoll > 0.92f, "straightCruiseRoll=" + straightCruiseRoll);
		assertTrue(straightCruiseRoll < 0.97f, "straightCruiseRoll=" + straightCruiseRoll);
		assertTrue(diagonalRoll > 0.80f, "diagonalRoll=" + diagonalRoll);
		assertTrue(diagonalRoll < 0.87f, "diagonalRoll=" + diagonalRoll);
		assertTrue(diagonalRoll < straightCruiseRoll - 0.06f, "diagonalRoll=" + diagonalRoll + " straightCruiseRoll=" + straightCruiseRoll);
		assertTrue(pitchAoa > 0.84f, "pitchAoa=" + pitchAoa);
		assertTrue(pitchAoa < 0.91f, "pitchAoa=" + pitchAoa);
	}

	@Test
	void acroMotorRateAuthorityKeepsAirmodeButLoadsSettledCrossflow() {
		float idleAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 0.0f),
				0.0f,
				0.20f,
				0.0f
		);
		float hoverAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 0.0f),
				0.20f,
				0.20f,
				0.0f
		);
		float straightCruiseAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				0.68f,
				0.20f,
				1.0f
		);
		float diagonalInitialAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.68f,
				0.20f,
				0.32f
		);
		float diagonalSettledAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.68f,
				0.20f,
				1.0f
		);
		float steepAoaAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(
				new PlayableFlightModel.Velocity(0.0f, 12.0f, 16.0f),
				0.68f,
				0.20f,
				1.0f
		);

		assertTrue(idleAuthority > 0.70f, "idleAuthority=" + idleAuthority);
		assertTrue(idleAuthority < 0.75f, "idleAuthority=" + idleAuthority);
		assertEquals(1.0f, hoverAuthority, 1.0e-6f);
		assertEquals(1.0f, straightCruiseAuthority, 1.0e-6f);
		assertTrue(diagonalInitialAuthority > 0.970f, "diagonalInitialAuthority=" + diagonalInitialAuthority);
		assertTrue(diagonalSettledAuthority > 0.920f, "diagonalSettledAuthority=" + diagonalSettledAuthority);
		assertTrue(diagonalSettledAuthority < 0.955f, "diagonalSettledAuthority=" + diagonalSettledAuthority);
		assertTrue(diagonalInitialAuthority > diagonalSettledAuthority + 0.025f,
				"initial=" + diagonalInitialAuthority + " settled=" + diagonalSettledAuthority);
		assertTrue(steepAoaAuthority > 0.955f, "steepAoaAuthority=" + steepAoaAuthority);
		assertTrue(steepAoaAuthority < 0.985f, "steepAoaAuthority=" + steepAoaAuthority);
	}

	@Test
	void acroSidewashMemoryDelaysYawAngularControlLoadsWithoutHidingPitchAoa() {
		PlayableFlightModel.Velocity yawSlip = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		PlayableFlightModel.Velocity pitchAoa = new PlayableFlightModel.Velocity(0.0f, 16.0f, 16.0f);
		float freshYawDamping = PlayableFlightModel.acroAerodynamicRateDamping(yawSlip, false, 1.0f, 0.0f);
		float settledYawDamping = PlayableFlightModel.acroAerodynamicRateDamping(yawSlip, false, 1.0f, 1.0f);
		float freshYawInertia = PlayableFlightModel.acroRateInertiaSmoothingScale(yawSlip, false, 1.0f, 0.0f);
		float settledYawInertia = PlayableFlightModel.acroRateInertiaSmoothingScale(yawSlip, false, 1.0f, 1.0f);
		float freshAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(yawSlip, 0.68f, 0.20f, 1.0f, 0.0f);
		float settledAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(yawSlip, 0.68f, 0.20f, 1.0f, 1.0f);
		float freshTorqueLoad = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				yawSlip,
				(float) Math.toRadians(6.0),
				(float) Math.toRadians(6.0),
				1.0f,
				0.0f
		);
		float settledTorqueLoad = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				yawSlip,
				(float) Math.toRadians(6.0),
				(float) Math.toRadians(6.0),
				1.0f,
				1.0f
		);
		float freshPitchDamping = PlayableFlightModel.acroAerodynamicRateDamping(pitchAoa, true, 1.0f, 0.0f);
		float settledPitchDamping = PlayableFlightModel.acroAerodynamicRateDamping(pitchAoa, true, 1.0f, 1.0f);
		float freshPitchInertia = PlayableFlightModel.acroRateInertiaSmoothingScale(pitchAoa, true, 1.0f, 0.0f);
		float settledPitchInertia = PlayableFlightModel.acroRateInertiaSmoothingScale(pitchAoa, true, 1.0f, 1.0f);
		float freshPitchAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(pitchAoa, 0.68f, 0.20f, 1.0f, 0.0f);
		float settledPitchAuthority = PlayableFlightModel.acroMotorRateAuthorityScale(pitchAoa, 0.68f, 0.20f, 1.0f, 1.0f);
		float freshPitchTorqueLoad = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				pitchAoa,
				(float) Math.toRadians(7.0),
				0.0f,
				1.0f,
				0.0f
		);
		float settledPitchTorqueLoad = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				pitchAoa,
				(float) Math.toRadians(7.0),
				0.0f,
				1.0f,
				1.0f
		);

		assertTrue(freshYawDamping < settledYawDamping * 0.80f,
				"freshYawDamping=" + freshYawDamping + " settledYawDamping=" + settledYawDamping);
		assertTrue(freshYawDamping > settledYawDamping * 0.68f,
				"freshYawDamping=" + freshYawDamping + " settledYawDamping=" + settledYawDamping);
		assertTrue(freshYawInertia > settledYawInertia + 0.045f,
				"freshYawInertia=" + freshYawInertia + " settledYawInertia=" + settledYawInertia);
		assertTrue(freshAuthority > settledAuthority + 0.025f,
				"freshAuthority=" + freshAuthority + " settledAuthority=" + settledAuthority);
		assertTrue(freshTorqueLoad < settledTorqueLoad * 0.46f,
				"freshTorqueLoad=" + freshTorqueLoad + " settledTorqueLoad=" + settledTorqueLoad);
		assertTrue(freshTorqueLoad > settledTorqueLoad * 0.34f,
				"freshTorqueLoad=" + freshTorqueLoad + " settledTorqueLoad=" + settledTorqueLoad);
		assertEquals(settledPitchDamping, freshPitchDamping, 1.0e-6f);
		assertEquals(settledPitchInertia, freshPitchInertia, 1.0e-6f);
		assertEquals(settledPitchAuthority, freshPitchAuthority, 1.0e-6f);
		assertEquals(settledPitchTorqueLoad, freshPitchTorqueLoad, 1.0e-6f);
	}

	@Test
	void acroHighSpeedCrossflowMakesActiveRollRateFeelLoadedButControllable() {
		PlayableFlightModel.State straightCruise = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				25.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.State diagonalSlip = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.Step straightRoll = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				straightCruise
		);
		PlayableFlightModel.Step diagonalRoll = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				diagonalSlip
		);
		float straightRate = Math.abs(straightRoll.acroRollRateRadiansPerTick());
		float diagonalRate = Math.abs(diagonalRoll.acroRollRateRadiansPerTick());

		assertTrue(straightRate > Math.toRadians(4.7), "straightRateDeg=" + Math.toDegrees(straightRate));
		assertTrue(diagonalRate < straightRate * 0.90f,
				"diagonalRateDeg=" + Math.toDegrees(diagonalRate) + " straightRateDeg=" + Math.toDegrees(straightRate));
		assertTrue(diagonalRate > straightRate * 0.70f,
				"diagonalRateDeg=" + Math.toDegrees(diagonalRate) + " straightRateDeg=" + Math.toDegrees(straightRate));
	}

	@Test
	void acroResidualTorqueRateLoadRequiresHighSpeedCrossflowAndBodyRate() {
		float lowSpeed = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(4.0f, 0.0f, 4.0f),
				(float) Math.toRadians(8.0),
				(float) Math.toRadians(8.0)
		);
		float straightFast = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				(float) Math.toRadians(8.0),
				0.0f
		);
		float passiveSlipMoment = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f,
				(float) Math.toRadians(0.55)
		);
		float diagonalBodyRate = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				(float) Math.toRadians(6.0),
				(float) Math.toRadians(6.0)
		);
		float steepAoaBodyRate = PlayableFlightModel.acroResidualTorqueRateLoadFraction(
				new PlayableFlightModel.Velocity(0.0f, 12.0f, 16.0f),
				(float) Math.toRadians(7.0),
				0.0f
		);

		assertEquals(0.0f, lowSpeed, 1.0e-6f);
		assertEquals(0.0f, straightFast, 1.0e-6f);
		assertEquals(0.0f, passiveSlipMoment, 1.0e-6f);
		assertTrue(diagonalBodyRate > 0.017f, "diagonalBodyRate=" + diagonalBodyRate);
		assertTrue(diagonalBodyRate < 0.030f, "diagonalBodyRate=" + diagonalBodyRate);
		assertTrue(steepAoaBodyRate > 0.006f, "steepAoaBodyRate=" + steepAoaBodyRate);
		assertTrue(steepAoaBodyRate < 0.018f, "steepAoaBodyRate=" + steepAoaBodyRate);
	}

	@Test
	void acroRotorGyroRateLoadRequiresHighRpmAndDiagonalBodyRates() {
		float hoverRpm = PlayableFlightModel.acroRotorGyroRateLoadFraction(
				(float) Math.toRadians(6.0),
				(float) Math.toRadians(6.0),
				0.20f,
				0.20f
		);
		float singleAxis = PlayableFlightModel.acroRotorGyroRateLoadFraction(
				(float) Math.toRadians(8.8),
				0.0f,
				1.0f,
				0.20f
		);
		float sportDiagonal = PlayableFlightModel.acroRotorGyroRateLoadFraction(
				(float) Math.toRadians(5.8),
				(float) Math.toRadians(5.8),
				0.68f,
				0.20f
		);
		float fullDiagonal = PlayableFlightModel.acroRotorGyroRateLoadFraction(
				(float) Math.toRadians(8.8),
				(float) Math.toRadians(9.4),
				1.0f,
				0.20f
		);

		assertEquals(0.0f, hoverRpm, 1.0e-6f);
		assertTrue(singleAxis > 0.008f, "singleAxis=" + singleAxis);
		assertTrue(singleAxis < 0.018f, "singleAxis=" + singleAxis);
		assertTrue(sportDiagonal > 0.032f, "sportDiagonal=" + sportDiagonal);
		assertTrue(sportDiagonal < 0.050f, "sportDiagonal=" + sportDiagonal);
		assertTrue(fullDiagonal > 0.078f, "fullDiagonal=" + fullDiagonal);
		assertTrue(fullDiagonal < 0.090f, "fullDiagonal=" + fullDiagonal);
	}

	@Test
	void acroHighRpmDiagonalStickCarriesRotorGyroWeight() {
		PlayableFlightModel.Step pitchOnly = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step rollOnly = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step diagonal = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.68f,
				1.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);

		assertTrue(diagonal.acroPitchRateRadiansPerTick() < pitchOnly.acroPitchRateRadiansPerTick() * 0.98f,
				"diagonalPitchRateDeg=" + Math.toDegrees(diagonal.acroPitchRateRadiansPerTick())
						+ " pitchOnlyDeg=" + Math.toDegrees(pitchOnly.acroPitchRateRadiansPerTick()));
		assertTrue(diagonal.acroPitchRateRadiansPerTick() > pitchOnly.acroPitchRateRadiansPerTick() * 0.94f,
				"diagonalPitchRateDeg=" + Math.toDegrees(diagonal.acroPitchRateRadiansPerTick())
						+ " pitchOnlyDeg=" + Math.toDegrees(pitchOnly.acroPitchRateRadiansPerTick()));
		assertTrue(diagonal.acroRollRateRadiansPerTick() < rollOnly.acroRollRateRadiansPerTick() * 0.98f,
				"diagonalRollRateDeg=" + Math.toDegrees(diagonal.acroRollRateRadiansPerTick())
						+ " rollOnlyDeg=" + Math.toDegrees(rollOnly.acroRollRateRadiansPerTick()));
		assertTrue(diagonal.acroRollRateRadiansPerTick() > rollOnly.acroRollRateRadiansPerTick() * 0.94f,
				"diagonalRollRateDeg=" + Math.toDegrees(diagonal.acroRollRateRadiansPerTick())
						+ " rollOnlyDeg=" + Math.toDegrees(rollOnly.acroRollRateRadiansPerTick()));
	}

	@Test
	void acroTransverseFlowRollMomentBanksIntoHighSpeedSideslip() {
		float straight = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				0.0f
		);
		float lowSpeedSide = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(6.0f, 0.0f, 0.0f),
				0.0f
		);
		float rightSlip = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f
		);
		float leftSlip = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(-16.0f, 0.0f, 16.0f),
				0.0f
		);
		float activeRoll = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				1.0f
		);

		assertEquals(0.0f, straight, 1.0e-6f);
		assertEquals(0.0f, lowSpeedSide, 1.0e-6f);
		assertTrue(rightSlip > Math.toRadians(0.61), "rightSlipDeg=" + Math.toDegrees(rightSlip));
		assertTrue(rightSlip < Math.toRadians(0.70), "rightSlipDeg=" + Math.toDegrees(rightSlip));
		assertEquals(-rightSlip, leftSlip, 1.0e-6f);
		assertTrue(activeRoll > rightSlip * 0.06f, "activeRollDeg=" + Math.toDegrees(activeRoll));
		assertTrue(activeRoll < rightSlip * 0.12f, "activeRollDeg=" + Math.toDegrees(activeRoll) + " rightSlipDeg=" + Math.toDegrees(rightSlip));
	}

	@Test
	void acroPassiveTransverseRollMomentIsRateHoldLimitedAfterInitialKick() {
		float passiveMoment = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f),
				0.0f
		);
		float initialKick = PlayableFlightModel.acroPassiveRateHoldLimitedTransverseRollMoment(
				passiveMoment,
				0.0f,
				0.0f
		);
		float heldSameDirection = PlayableFlightModel.acroPassiveRateHoldLimitedTransverseRollMoment(
				passiveMoment,
				(float) Math.toRadians(0.42f),
				0.0f
		);
		float oppositeCorrection = PlayableFlightModel.acroPassiveRateHoldLimitedTransverseRollMoment(
				passiveMoment,
				(float) Math.toRadians(-0.42f),
				0.0f
		);
		float activeRoll = PlayableFlightModel.acroPassiveRateHoldLimitedTransverseRollMoment(
				passiveMoment,
				(float) Math.toRadians(0.42f),
				0.35f
		);

		assertEquals(passiveMoment, initialKick, 1.0e-6f);
		assertTrue(heldSameDirection < passiveMoment * 0.16f,
				"heldDeg=" + Math.toDegrees(heldSameDirection) + " passiveDeg=" + Math.toDegrees(passiveMoment));
		assertTrue(heldSameDirection > passiveMoment * 0.10f,
				"heldDeg=" + Math.toDegrees(heldSameDirection) + " passiveDeg=" + Math.toDegrees(passiveMoment));
		assertEquals(passiveMoment, oppositeCorrection, 1.0e-6f);
		assertEquals(passiveMoment, activeRoll, 1.0e-6f);
	}

	@Test
	void acroTransverseFlowRollMomentDependsOnPoweredDiskAdvanceRatio() {
		PlayableFlightModel.Velocity fastSlip = new PlayableFlightModel.Velocity(16.0f, 0.0f, 16.0f);
		float idleSlip = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				fastSlip,
				0.0f,
				0.0f,
				0.20f
		);
		float poweredSlip = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				fastSlip,
				0.0f,
				0.45f,
				0.20f
		);
		float lowMuSlip = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(2.0f, 0.0f, 2.0f),
				0.0f,
				1.0f,
				0.20f
		);
		float overRangeSlip = PlayableFlightModel.acroTransverseFlowRollMomentRate(
				new PlayableFlightModel.Velocity(30.0f, 0.0f, 30.0f),
				0.0f,
				0.45f,
				0.20f
		);

		assertTrue(idleSlip > Math.toRadians(0.08), "idleSlipDeg=" + Math.toDegrees(idleSlip));
		assertTrue(idleSlip < Math.toRadians(0.15), "idleSlipDeg=" + Math.toDegrees(idleSlip));
		assertEquals(0.0f, lowMuSlip, 1.0e-6f);
		assertTrue(poweredSlip > Math.toRadians(0.61), "poweredSlipDeg=" + Math.toDegrees(poweredSlip));
		assertTrue(poweredSlip < Math.toRadians(0.70), "poweredSlipDeg=" + Math.toDegrees(poweredSlip));
		assertTrue(poweredSlip > idleSlip * 4.0f,
				"poweredSlipDeg=" + Math.toDegrees(poweredSlip) + " idleSlipDeg=" + Math.toDegrees(idleSlip));
		assertTrue(overRangeSlip > poweredSlip * 0.70f,
				"overRangeSlipDeg=" + Math.toDegrees(overRangeSlip) + " poweredSlipDeg=" + Math.toDegrees(poweredSlip));
		assertTrue(overRangeSlip < poweredSlip * 0.95f,
				"overRangeSlipDeg=" + Math.toDegrees(overRangeSlip) + " poweredSlipDeg=" + Math.toDegrees(poweredSlip));
	}

	@Test
	void acroTransverseFlowPoweredMuShapeUsesKolaeiRangeWithoutHardExtrapolation() {
		assertEquals(0.0f, PlayableFlightModel.acroTransverseFlowPoweredMuShape(0.03f), 1.0e-6f);
		assertTrue(PlayableFlightModel.acroTransverseFlowPoweredMuShape(0.14f) > 0.45f);
		assertTrue(PlayableFlightModel.acroTransverseFlowPoweredMuShape(0.24f) > 0.96f);
		assertTrue(PlayableFlightModel.acroTransverseFlowPoweredMuShape(0.45f) > 0.68f);
		assertTrue(PlayableFlightModel.acroTransverseFlowPoweredMuShape(0.45f) < 0.85f);
		assertTrue(PlayableFlightModel.acroTransverseFlowPoweredMuShape(0.72f) > 0.42f);
		assertTrue(PlayableFlightModel.acroTransverseFlowPoweredMuShape(0.72f) < 0.48f);
	}

	@Test
	void acroAngleOfAttackPitchMomentFollowsHighSpeedAoaWithoutTouchingCruise() {
		float straight = PlayableFlightModel.acroAngleOfAttackPitchMomentRate(
				new PlayableFlightModel.Velocity(0.0f, 0.0f, 25.0f),
				0.0f
		);
		float lowSpeedClimb = PlayableFlightModel.acroAngleOfAttackPitchMomentRate(
				new PlayableFlightModel.Velocity(0.0f, 4.0f, 4.0f),
				0.0f
		);
		float positiveAoa = PlayableFlightModel.acroAngleOfAttackPitchMomentRate(
				new PlayableFlightModel.Velocity(0.0f, 10.0f, 18.0f),
				0.0f
		);
		float negativeAoa = PlayableFlightModel.acroAngleOfAttackPitchMomentRate(
				new PlayableFlightModel.Velocity(0.0f, -10.0f, 18.0f),
				0.0f
		);
		float activePitch = PlayableFlightModel.acroAngleOfAttackPitchMomentRate(
				new PlayableFlightModel.Velocity(0.0f, 10.0f, 18.0f),
				1.0f
		);

		assertEquals(0.0f, straight, 1.0e-6f);
		assertEquals(0.0f, lowSpeedClimb, 1.0e-6f);
		assertTrue(positiveAoa < -Math.toRadians(0.06), "positiveAoaDeg=" + Math.toDegrees(positiveAoa));
		assertTrue(positiveAoa > -Math.toRadians(0.13), "positiveAoaDeg=" + Math.toDegrees(positiveAoa));
		assertEquals(-positiveAoa, negativeAoa, 1.0e-6f);
		assertTrue(Math.abs(activePitch) > Math.abs(positiveAoa) * 0.06f, "activePitchDeg=" + Math.toDegrees(activePitch));
		assertTrue(Math.abs(activePitch) < Math.abs(positiveAoa) * 0.12f,
				"activePitchDeg=" + Math.toDegrees(activePitch) + " positiveAoaDeg=" + Math.toDegrees(positiveAoa));
		assertEquals(0.0f, PlayableFlightModel.acroAngleOfAttackPitchMomentActivity(0.0f, 0.0f), 1.0e-6f);
		assertEquals(1.0f, PlayableFlightModel.acroAngleOfAttackPitchMomentActivity((float) Math.toRadians(1.2), 0.0f), 1.0e-6f);
		assertEquals(1.0f, PlayableFlightModel.acroAngleOfAttackPitchMomentActivity(0.0f, 0.5f), 1.0e-6f);
		PlayableFlightModel.Velocity climbingAoaVelocity = new PlayableFlightModel.Velocity(0.0f, 10.0f, 18.0f);
		float passiveScale = PlayableFlightModel.acroAngleOfAttackPitchMomentScale(climbingAoaVelocity, 0.0f, 0.0f, 0.0f);
		assertTrue(passiveScale > 0.30f);
		assertTrue(passiveScale < 0.42f);
		assertEquals(0.0f, PlayableFlightModel.acroAngleOfAttackPitchMomentScale(
				climbingAoaVelocity,
				(float) Math.toRadians(25.0),
				0.0f,
				0.0f
		), 1.0e-6f);
		assertEquals(1.0f, PlayableFlightModel.acroAngleOfAttackPitchMomentScale(
				climbingAoaVelocity,
				(float) Math.toRadians(25.0),
				(float) Math.toRadians(1.2),
				0.0f
		), 1.0e-6f);
		assertEquals(1.0f, PlayableFlightModel.acroAngleOfAttackPitchMomentScale(
				climbingAoaVelocity,
				(float) Math.toRadians(25.0),
				0.0f,
				0.5f
		), 1.0e-6f);
	}

	@Test
	void acroHighSpeedAoaLoadsResidualPitchRateWithoutAutolevelingCruise() {
		float residualPitchRate = (float) Math.toRadians(1.0f);
		PlayableFlightModel.State straightCruise = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				18.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				residualPitchRate,
				0.0f
		);
		PlayableFlightModel.State climbingAoa = new PlayableFlightModel.State(
				0.0f,
				10.0f,
				18.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				residualPitchRate,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.State passiveClimbingAoa = new PlayableFlightModel.State(
				0.0f,
				10.0f,
				18.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.Step straight = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				straightCruise
		);
		PlayableFlightModel.Step loaded = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				climbingAoa
		);
		PlayableFlightModel.Step passive = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				passiveClimbingAoa
		);

		assertTrue(loaded.acroPitchRateRadiansPerTick() > 0.0f,
				"loadedPitchRateDeg=" + Math.toDegrees(loaded.acroPitchRateRadiansPerTick()));
		assertTrue(straight.acroPitchRateRadiansPerTick() > loaded.acroPitchRateRadiansPerTick() + Math.toRadians(0.04),
				"straightPitchRateDeg=" + Math.toDegrees(straight.acroPitchRateRadiansPerTick())
						+ " loadedPitchRateDeg=" + Math.toDegrees(loaded.acroPitchRateRadiansPerTick()));
		assertTrue(passive.acroPitchRateRadiansPerTick() < -Math.toRadians(0.025),
				"passivePitchRateDeg=" + Math.toDegrees(passive.acroPitchRateRadiansPerTick()));
		assertTrue(passive.acroPitchRateRadiansPerTick() > -Math.toRadians(0.050),
				"passivePitchRateDeg=" + Math.toDegrees(passive.acroPitchRateRadiansPerTick()));
		assertEquals(loaded.acroPitchRateRadiansPerTick(), loaded.pitchRadians(), 1.0e-6f);
		assertEquals(passive.acroPitchRateRadiansPerTick(), passive.pitchRadians(), 1.0e-6f);
		assertEquals(0.0f, loaded.acroRollRateRadiansPerTick(), 1.0e-6f);
		assertEquals(0.0f, passive.acroRollRateRadiansPerTick(), 1.0e-6f);
	}

	@Test
	void acroHighSpeedSideslipAddsPassiveRollRateWithoutRollStick() {
		PlayableFlightModel.State slipping = new PlayableFlightModel.State(
				16.0f,
				0.0f,
				16.0f,
				0.0f,
				0.0f,
				0.0f,
				FlightMode.ACRO,
				0,
				1.0f,
				0.0f,
				0.0f,
				0,
				1.0f
		);
		PlayableFlightModel.Step passive = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				slipping
		);

		assertTrue(passive.acroRollRateRadiansPerTick() > Math.toRadians(0.61),
				"passiveRollRateDeg=" + Math.toDegrees(passive.acroRollRateRadiansPerTick()));
		assertTrue(passive.acroRollRateRadiansPerTick() < Math.toRadians(0.70),
				"passiveRollRateDeg=" + Math.toDegrees(passive.acroRollRateRadiansPerTick()));
		assertEquals(passive.acroRollRateRadiansPerTick(), passive.rollRadians(), 1.0e-6f);
		assertEquals(0.0f, passive.acroPitchRateRadiansPerTick(), 1.0e-6f);
	}

	@Test
	void acroHighSpeedDiagonalFlowBuildsRollRateWithMoreWeight() {
		PlayableFlightModel.Step lowSpeed = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step diagonalFlow = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						16.0f,
						0.0f,
						16.0f,
						0.0f,
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f
				)
		);

		assertTrue(lowSpeed.acroRollRateRadiansPerTick() > Math.toRadians(5.3), "lowSpeedRollRateDeg=" + Math.toDegrees(lowSpeed.acroRollRateRadiansPerTick()));
		assertTrue(diagonalFlow.acroRollRateRadiansPerTick() > lowSpeed.acroRollRateRadiansPerTick() * 0.70f,
				"diagonalRollRateDeg=" + Math.toDegrees(diagonalFlow.acroRollRateRadiansPerTick()) + " lowSpeedRollRateDeg=" + Math.toDegrees(lowSpeed.acroRollRateRadiansPerTick()));
		assertTrue(diagonalFlow.acroRollRateRadiansPerTick() < lowSpeed.acroRollRateRadiansPerTick() * 0.89f,
				"diagonalRollRateDeg=" + Math.toDegrees(diagonalFlow.acroRollRateRadiansPerTick()) + " lowSpeedRollRateDeg=" + Math.toDegrees(lowSpeed.acroRollRateRadiansPerTick()));
	}

	@Test
	void bankedAcroPitchInputCreatesHeadingChangeWithoutYawStick() {
		PlayableFlightModel.State banked = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				(float) Math.toRadians(60.0),
				0.0f,
				FlightMode.ACRO,
				0,
				0.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step levelPitch = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step bankedPitch = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				banked
		);

		assertEquals(0.0f, levelPitch.yawDegreesPerTick(), 1.0e-6f);
		assertTrue(bankedPitch.yawDegreesPerTick() < 0.0f, "Minecraft heading follows the banked body pitch axis");
		assertEquals(bankedPitch.acroHeadingCouplingDegreesPerTick(), bankedPitch.yawDegreesPerTick(), 1.0e-5f);
		assertBodyRotationBasis(PlayableAttitude.quaternion(0, banked.pitchRadians(), banked.rollRadians()),
				PlayableAttitude.quaternion(bankedPitch.yawDegreesPerTick(), bankedPitch.pitchRadians(), bankedPitch.rollRadians()),
				bankedPitch.acroPitchRateRadiansPerTick(), bankedPitch.acroRollRateRadiansPerTick(), "banked pitch");
	}

	@Test
	void activeYawKeepsItsDynamicAuthorityWhilePreservingExactBankedBodyRotation() {
		PlayableFlightModel.State banked = new PlayableFlightModel.State(
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				(float) Math.toRadians(60.0),
				0.0f,
				FlightMode.ACRO,
				0,
				0.0f,
				0.0f,
				0.0f
		);
		PlayableFlightModel.Step passive = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				banked
		);
		PlayableFlightModel.Step activeYaw = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				0.0f,
				1.0f,
				0.20f,
				false,
				banked
		);

		assertEquals(passive.acroHeadingCouplingDegreesPerTick(), passive.yawDegreesPerTick(), 1.0e-5f);
		float dynamicYaw = activeYaw.yawDegreesPerTick() - activeYaw.acroHeadingCouplingDegreesPerTick();
		assertTrue(dynamicYaw > 4.80f && dynamicYaw < 5.45f, "dynamicYaw=" + dynamicYaw);
		assertEquals(passive.acroHeadingCouplingDegreesPerTick(), activeYaw.acroHeadingCouplingDegreesPerTick(), 1.0e-5f);
	}

	@Test
	void acroBankedPitchProjectsAwayFromPlanarEulerSlide() {
		PlayableFlightModel.Step levelPitch = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step bankedPitch = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(75.0),
						0.0f,
						FlightMode.ACRO,
						0,
						0.0f,
						0.0f,
						0.0f
				)
		);

		PlayableFlightModel.AcroBodyRateAttitudeDelta bankedDelta = PlayableFlightModel.acroBodyRateAttitudeDelta(
				0.0f,
				(float) Math.toRadians(75.0),
				bankedPitch.acroPitchRateRadiansPerTick(),
				0.0f
		);
		float bankedExactProjection = Math.abs(bankedDelta.pitchRateRadiansPerTick() / bankedPitch.acroPitchRateRadiansPerTick());

		assertTrue(bankedExactProjection > 0.24f, "bankedExactProjection=" + bankedExactProjection);
		assertTrue(bankedExactProjection < 0.28f, "bankedExactProjection=" + bankedExactProjection);
		assertTrue(bankedPitch.acroPitchRateRadiansPerTick() > levelPitch.acroPitchRateRadiansPerTick() * 0.95f,
				"bankedBodyPitchRateDeg=" + Math.toDegrees(bankedPitch.acroPitchRateRadiansPerTick())
						+ " levelBodyPitchRateDeg=" + Math.toDegrees(levelPitch.acroPitchRateRadiansPerTick()));
		assertTrue(bankedPitch.pitchRadians() < levelPitch.pitchRadians() * 0.30f,
				"bankedPitchRateDeg=" + Math.toDegrees(bankedPitch.acroPitchRateRadiansPerTick())
						+ " bankedEulerPitchDeg=" + Math.toDegrees(bankedPitch.pitchRadians())
						+ " levelEulerPitchDeg=" + Math.toDegrees(levelPitch.pitchRadians()));
		assertTrue(bankedPitch.pitchRadians() > levelPitch.pitchRadians() * 0.22f,
				"bankedPitchRateDeg=" + Math.toDegrees(bankedPitch.acroPitchRateRadiansPerTick())
						+ " bankedEulerPitchDeg=" + Math.toDegrees(bankedPitch.pitchRadians())
						+ " levelEulerPitchDeg=" + Math.toDegrees(levelPitch.pitchRadians()));
		assertEquals(bankedDelta.pitchRateRadiansPerTick(), bankedPitch.pitchRadians(), 1.0e-5f);
		assertTrue(bankedPitch.yawDegreesPerTick() < 0.0f, "bankedYaw=" + bankedPitch.yawDegreesPerTick());
		assertEquals(bankedDelta.yawDegreesPerTick(), bankedPitch.yawDegreesPerTick(), 1.0e-5f);
		assertTrue(bankedPitch.pitchRadians() < Math.toRadians(80.0), "bankedPitchDeg=" + Math.toDegrees(bankedPitch.pitchRadians()));
	}

	@Test
	void acroVerticalRollProjectsAwayFromPlanarEulerSlide() {
		PlayableFlightModel.Step levelRoll = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				PlayableFlightModel.State.zero(FlightMode.ACRO)
		);
		PlayableFlightModel.Step verticalRoll = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(78.0),
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						0.0f,
						0.0f,
						0.0f
				)
		);

		PlayableFlightModel.AcroBodyRateAttitudeDelta verticalDelta = PlayableFlightModel.acroBodyRateAttitudeDelta(
				(float) Math.toRadians(78.0),
				0.0f,
				0.0f,
				verticalRoll.acroRollRateRadiansPerTick()
		);
		assertTrue(verticalRoll.acroRollRateRadiansPerTick() > levelRoll.acroRollRateRadiansPerTick() * 0.95f);
		assertEquals(verticalDelta.rollRateRadiansPerTick(), verticalRoll.rollRadians(), 1.0e-5f);
		assertEquals(verticalDelta.yawDegreesPerTick(), verticalRoll.yawDegreesPerTick(), 1.0e-5f);
		assertBodyRotationBasis(PlayableAttitude.quaternion(0.0, Math.toRadians(78.0), 0.0),
				PlayableAttitude.quaternion(verticalRoll.yawDegreesPerTick(), verticalRoll.pitchRadians(), verticalRoll.rollRadians()),
				verticalRoll.acroPitchRateRadiansPerTick(), verticalRoll.acroRollRateRadiansPerTick(), "vertical body roll");
	}

	@Test
	void acroBodyRateAttitudeDeltaKeepsCompletedRollContinuity() {
		PlayableFlightModel.AcroBodyRateAttitudeDelta delta = PlayableFlightModel.acroBodyRateAttitudeDelta(
				0.0f,
				(float) Math.toRadians(358.0),
				0.0f,
				(float) Math.toRadians(5.0)
		);

		assertEquals(Math.toRadians(5.0), delta.rollRateRadiansPerTick(), 1.0e-5);
		assertEquals(0.0f, delta.pitchRateRadiansPerTick(), 1.0e-6f);
	}

	@Test
	void bankedAcroPitchUsesEulerAttitudeDeltaForPhysicsMidpoint() {
		PlayableFlightModel.Step bankedPitch = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.65f,
				1.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(75.0),
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f
				)
		);

		assertTrue(bankedPitch.acroPitchRateRadiansPerTick() > Math.toRadians(5.0),
				"bodyPitchRateDeg=" + Math.toDegrees(bankedPitch.acroPitchRateRadiansPerTick()));
		assertTrue(bankedPitch.pitchRadians() > Math.toRadians(1.20),
				"eulerPitchDeg=" + Math.toDegrees(bankedPitch.pitchRadians()));
		assertTrue(bankedPitch.pitchRadians() < Math.toRadians(1.65),
				"eulerPitchDeg=" + Math.toDegrees(bankedPitch.pitchRadians()));
		assertTrue(bankedPitch.velocityZ() > 0.0f,
				"velocityZ=" + bankedPitch.velocityZ() + " eulerPitchDeg=" + Math.toDegrees(bankedPitch.pitchRadians())
						+ " bodyPitchRateDeg=" + Math.toDegrees(bankedPitch.acroPitchRateRadiansPerTick()));
	}

	@Test
	void verticalAcroRollUsesEulerAttitudeDeltaForPhysicsMidpoint() {
		PlayableFlightModel.Step verticalRoll = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.65f,
				0.0f,
				1.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(
						0.0f,
						0.0f,
						0.0f,
						(float) Math.toRadians(78.0),
						0.0f,
						0.0f,
						FlightMode.ACRO,
						0,
						1.0f,
						0.0f,
						0.0f
				)
		);

		assertTrue(verticalRoll.acroRollRateRadiansPerTick() > Math.toRadians(5.0),
				"bodyRollRateDeg=" + Math.toDegrees(verticalRoll.acroRollRateRadiansPerTick()));
		assertBodyRotationBasis(PlayableAttitude.quaternion(0.0, Math.toRadians(78.0), 0.0),
				PlayableAttitude.quaternion(verticalRoll.yawDegreesPerTick(), verticalRoll.pitchRadians(), verticalRoll.rollRadians()),
				verticalRoll.acroPitchRateRadiansPerTick(), verticalRoll.acroRollRateRadiansPerTick(), "vertical roll midpoint");
		assertTrue(verticalRoll.velocityX() < 0.0f,
				"velocityX=" + verticalRoll.velocityX() + " eulerRollDeg=" + Math.toDegrees(verticalRoll.rollRadians())
						+ " bodyRollRateDeg=" + Math.toDegrees(verticalRoll.acroRollRateRadiansPerTick()));
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

		assertTrue(speedAfterOneSecond > 19.2f, "speedAfterOneSecond=" + speedAfterOneSecond);
		assertTrue(speedAfterOneSecond < 20.3f, "speedAfterOneSecond=" + speedAfterOneSecond);
		assertTrue(distanceAfterOneSecond > 21.5f, "distanceAfterOneSecond=" + distanceAfterOneSecond);
		assertTrue(distanceAfterOneSecond < 22.8f, "distanceAfterOneSecond=" + distanceAfterOneSecond);
		assertTrue(step != null && step.velocityZ() > 14.2f, "speedAfterTwoSeconds=" + (step == null ? Float.NaN : step.velocityZ()));
		assertTrue(step != null && step.velocityZ() < 16.4f, "speedAfterTwoSeconds=" + (step == null ? Float.NaN : step.velocityZ()));
		assertTrue(distanceMeters > 38.2f, "distanceAfterTwoSeconds=" + distanceMeters);
		assertTrue(distanceMeters < 40.8f, "distanceAfterTwoSeconds=" + distanceMeters);
	}

	@Test
	void acroOverspeedUsesSoftDragInsteadOfHardHorizontalClamp() {
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				FlightMode.ACRO,
				0.45f,
				0.0f,
				0.0f,
				0.0f,
				0.20f,
				false,
				new PlayableFlightModel.State(0.0f, 0.0f, 35.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO)
		);
		float horizontalSpeed = horizontalSpeed(step.velocityX(), step.velocityZ());

		assertTrue(horizontalSpeed > 32.4f, "horizontalSpeed=" + horizontalSpeed);
		assertTrue(horizontalSpeed < 35.0f, "horizontalSpeed=" + horizontalSpeed);
		assertTrue(step.velocityZ() > 32.4f, "velocityZ=" + step.velocityZ());
		assertTrue(step.velocityZ() < 35.0f, "velocityZ=" + step.velocityZ());
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

		assertTrue(horizontalSpeed(acro.targetVelocityX(), acro.targetVelocityZ())
				> horizontalSpeed(angle.targetVelocityX(), angle.targetVelocityZ()));
		assertTrue(Math.abs(acro.rollRadians()) > Math.abs(angle.rollRadians()) * 4.0f,
				"acroRollDeg=" + Math.toDegrees(acro.rollRadians()) + " angleRollDeg=" + Math.toDegrees(angle.rollRadians()));
		assertTrue(acro.acroPitchRateRadiansPerTick() > Math.toRadians(8.0), "acroPitchRateDeg=" + Math.toDegrees(acro.acroPitchRateRadiansPerTick()));
		assertTrue(acro.acroRollRateRadiansPerTick() > Math.toRadians(8.0), "acroRollRateDeg=" + Math.toDegrees(acro.acroRollRateRadiansPerTick()));
		float dynamicAcroYaw = acro.yawDegreesPerTick() - acro.acroHeadingCouplingDegreesPerTick();
		assertTrue(dynamicAcroYaw > angle.yawDegreesPerTick(), "dynamicAcroYaw=" + dynamicAcroYaw);
	}

	@Test
	void acroGamepadPresetKeepsMidStickProgressiveButKeepsFullAuthority() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.applyGamepadFeelPreset(DroneClientConfig.ControlFeelPreset.ACRO);
		float hoverThrottle = (float) ControlStickProfile.gamepadThrottle(0.50);
		PlayableFlightModel.Step midStick = runClientPresetStick(config, 20, 0.70f, -0.70f, 0.70f, hoverThrottle);
		PlayableFlightModel.Step fullStick = runClientPresetStick(config, 20, 1.0f, -1.0f, 1.0f, hoverThrottle);
		float midDynamicYaw = midStick.yawDegreesPerTick() - midStick.acroHeadingCouplingDegreesPerTick();
		float fullDynamicYaw = fullStick.yawDegreesPerTick() - fullStick.acroHeadingCouplingDegreesPerTick();

		assertTrue(Math.abs(midStick.pitchRadians()) > Math.toRadians(30.0), "midPitchDeg=" + Math.toDegrees(midStick.pitchRadians()));
		assertTrue(Math.abs(midStick.pitchRadians()) < Math.toRadians(80.0), "midPitchDeg=" + Math.toDegrees(midStick.pitchRadians()));
		assertTrue(Math.abs(midStick.rollRadians()) > Math.toRadians(84.0), "midRollDeg=" + Math.toDegrees(midStick.rollRadians()));
		assertTrue(Math.abs(midStick.rollRadians()) < Math.toRadians(135.0), "midRollDeg=" + Math.toDegrees(midStick.rollRadians()));
		assertTrue(midDynamicYaw > 0.0f && midDynamicYaw < 2.85f, "midDynamicYaw=" + midDynamicYaw);
		assertTrue(Math.abs(fullStick.pitchRadians()) > Math.toRadians(30.0), "fullPitchDeg=" + Math.toDegrees(fullStick.pitchRadians()));
		assertTrue(Math.abs(fullStick.acroPitchRateRadiansPerTick()) > Math.toRadians(8.0), "fullPitchRateDeg=" + Math.toDegrees(fullStick.acroPitchRateRadiansPerTick()));
		assertTrue(Math.abs(fullStick.acroRollRateRadiansPerTick()) > Math.toRadians(8.0), "fullRollRateDeg=" + Math.toDegrees(fullStick.acroRollRateRadiansPerTick()));
		assertTrue(Math.abs(fullStick.rollRadians()) > Math.toRadians(115.0), "fullRollDeg=" + Math.toDegrees(fullStick.rollRadians()));
		assertTrue(fullDynamicYaw > 4.5f, "fullDynamicYaw=" + fullDynamicYaw);
		assertTrue(fullDynamicYaw > midDynamicYaw * 1.25f);
		assertTrue(Math.abs(fullStick.acroPitchRateRadiansPerTick()) > Math.abs(midStick.acroPitchRateRadiansPerTick()) * 1.25f);
		assertTrue(Math.abs(fullStick.acroRollRateRadiansPerTick()) > Math.abs(midStick.acroRollRateRadiansPerTick()) * 1.25f);
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

		assertTrue(Math.max(Math.abs(acro.pitchRadians()), Math.abs(acro.rollRadians())) > Math.toRadians(120.0),
				"acroPitchDeg=" + Math.toDegrees(acro.pitchRadians()) + " acroRollDeg=" + Math.toDegrees(acro.rollRadians()));
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

	private static YawReframedRun runYawReframedFrom(
			YawReframedRun run,
			int ticks,
			float throttle,
			float pitch,
			float roll,
			float yawCommand
	) {
		YawReframedRun current = run;
		for (int i = 0; i < ticks; i++) {
			PlayableFlightModel.State state = current.state();
			if (Math.abs(current.yawDegrees() - current.velocityYawDegrees()) > 1.0e-4f) {
				PlayableFlightModel.Velocity reframed = PlayableFlightModel.reframeVelocityForYaw(
						state.velocityX(),
						state.velocityY(),
						state.velocityZ(),
						current.velocityYawDegrees(),
						current.yawDegrees()
				);
				state = new PlayableFlightModel.State(
						reframed.x(),
						reframed.y(),
						reframed.z(),
						state.pitchRadians(),
						state.rollRadians(),
						state.yawDegreesPerTick(),
						state.mode(),
						state.modeSwitchTicksRemaining(),
						state.acroCollectiveThrustToWeight(),
						state.acroPitchRateRadiansPerTick(),
						state.acroRollRateRadiansPerTick(),
						state.acroRollRecoveryTicksRemaining(),
						state.acroAeroCrossflowLag(),
						state.acroSidewashMemory(),
						state.acroHeadingCouplingDegreesPerTick()
				);
			}
			PlayableFlightModel.Step step = PlayableFlightModel.step(
					FlightMode.ACRO,
					throttle,
					pitch,
					roll,
					yawCommand,
					0.20f,
					false,
					state
			);
			current = new YawReframedRun(
					stateFrom(step),
					step,
					current.yawDegrees() + step.yawDegreesPerTick(),
					current.yawDegrees()
			);
		}
		return current;
	}

	private static YawReframedRun runDebugFilteredYawReframedFrom(
			YawReframedRun run,
			DebugFilteredAxes axes,
			int ticks,
			float throttle,
			float rawPitch,
			float rawRoll,
			float rawYaw
	) {
		YawReframedRun current = run;
		for (int i = 0; i < ticks; i++) {
			axes.update(rawPitch, rawRoll, rawYaw);
			current = runYawReframedFrom(current, 1, throttle, axes.pitch(), axes.roll(), axes.yaw());
		}
		return current;
	}

	private static YawReframedRun runClientPresetYawReframedFrom(
			YawReframedRun run,
			ClientTrainingAxes axes,
			DroneClientConfig config,
			int ticks,
			float throttle,
			float rawPitch,
			float rawRoll,
			float rawYaw
	) {
		YawReframedRun current = run;
		for (int i = 0; i < ticks; i++) {
			ClientTrainingAxes.Sample sample = axes.sample(config, rawPitch, rawRoll, rawYaw);
			current = runYawReframedFrom(current, 1, throttle, sample.pitch(), sample.roll(), sample.yaw());
		}
		return current;
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
				step.acroRollRateRadiansPerTick(),
				step.acroRollRecoveryTicksRemaining(),
				step.acroAeroCrossflowLag(),
				step.acroSidewashMemory(),
				step.acroHeadingCouplingDegreesPerTick()
		);
	}

	private static float horizontalSpeed(float x, float z) {
		return (float) Math.sqrt(x * x + z * z);
	}

	private record YawReframedRun(
			PlayableFlightModel.State state,
			PlayableFlightModel.Step step,
			float yawDegrees,
			float velocityYawDegrees
	) {
		private static YawReframedRun zero(FlightMode mode) {
			PlayableFlightModel.State state = PlayableFlightModel.State.zero(mode);
			return new YawReframedRun(state, null, 0.0f, 0.0f);
		}
	}

	private static final class DebugFilteredAxes {
		private float pitch;
		private float roll;
		private float yaw;

		private void update(float rawPitch, float rawRoll, float rawYaw) {
			pitch = filterAxis(pitch, rawPitch);
			roll = filterAxis(roll, rawRoll);
			yaw = filterAxis(yaw, rawYaw);
		}

		private float pitch() {
			return pitch;
		}

		private float roll() {
			return roll;
		}

		private float yaw() {
			return yaw;
		}

		private static float filterAxis(float current, float target) {
			return PlayableDebugAxisFilter.filter(
					current,
					target,
					PlayableDebugAxisFilter.DEFAULT_RISE_SMOOTHING,
					PlayableDebugAxisFilter.DEFAULT_FALL_SMOOTHING,
					true
			);
		}
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
