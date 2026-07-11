package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.UiucDa4002FiveInchRuntimeLookup;

class PlayableFlightAgileCandidateTest {
	private static final float HOVER_THROTTLE = 0.20f;
	private static final float TICK_SECONDS = 0.05f;

	@Test
	void legacyHeavyRacingQuadRemainsDefaultPreset() {
		assertEquals(PlayableFlightPreset.LEGACY_HEAVY_RACING_QUAD, PlayableFlightPreset.defaultPreset());
	}

	@Test
	void agileCandidateMeetsAcroRollTargets() {
		RollResult hover = fullRollSeconds(new PlayableFlightModel.State(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO), 0.68f);
		RollResult forward = fullRollSeconds(new PlayableFlightModel.State(0.0f, 0.0f, 20.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO), 0.72f);

		assertTrue(hover.seconds() >= 0.45f, () -> "hover roll too abrupt: " + hover);
		assertTrue(hover.seconds() <= 0.60f, () -> "hover roll too slow: " + hover);
		assertTrue(forward.seconds() <= 0.70f, () -> "forward roll too slow: " + forward);
		assertTrue(hover.maxRollRateDps() > 560.0f, () -> "hover roll rate too low: " + hover);
		assertTrue(forward.maxRollRateDps() > 520.0f, () -> "forward roll rate too low: " + forward);
	}

	@Test
	void agileCandidateReversesRollRateWithinTarget() {
		PlayableFlightModel.State state = PlayableFlightModel.State.zero(FlightMode.ACRO);
		int reversalTick = 12;
		int reversedAfterTicks = -1;
		for (int tick = 0; tick < 60; tick++) {
			float roll = tick < reversalTick ? 1.0f : -1.0f;
			PlayableFlightModel.Step step = step(state, 0.70f, 0.0f, roll, 0.0f);
			if (tick >= reversalTick && step.acroRollRateRadiansPerTick() < 0.0f) {
				reversedAfterTicks = tick - reversalTick;
				break;
			}
			state = stateFrom(step);
		}

		assertTrue(reversedAfterTicks >= 0, "roll rate never changed direction");
		assertTrue(reversedAfterTicks * TICK_SECONDS <= 0.150f, "roll reversal took " + reversedAfterTicks * TICK_SECONDS + "s");
	}

	@Test
	void agileCandidatePulloutArrestsSinkWithinFiveMeters() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(
				0.0f,
				-8.0f,
				20.0f,
				(float) Math.toRadians(10.0),
				0.0f,
				0.0f,
				FlightMode.ACRO
		);
		float altitude = 0.0f;
		float lowest = 0.0f;
		float finalVelocityY = -8.0f;
		float finalPitchRadians = 0.0f;
		boolean arrested = false;
		for (int tick = 0; tick < 80; tick++) {
			PlayableFlightModel.Step step = step(state, 1.0f, 0.0f, 0.0f, 0.0f);
			altitude += step.velocityY() * TICK_SECONDS;
			lowest = Math.min(lowest, altitude);
			finalVelocityY = step.velocityY();
			finalPitchRadians = step.pitchRadians();
			if (step.velocityY() >= 0.0f) {
				arrested = true;
				break;
			}
			state = stateFrom(step);
		}

		assertTrue(arrested, "candidate never arrested the -8 m/s sink; finalVy=" + finalVelocityY + " lowest=" + lowest + " pitchDeg=" + Math.toDegrees(finalPitchRadians));
		assertTrue(-lowest <= 5.0f, "pullout lost " + -lowest + "m");
	}

	@Test
	void agileCandidateMaintainsCruiseSpeedHeadroom() {
		PlayableFlightModel.State state = new PlayableFlightModel.State(0.0f, 0.0f, 20.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO);
		float maxHorizontalSpeed = 0.0f;
		for (int tick = 0; tick < 120; tick++) {
			PlayableFlightModel.Step step = step(state, 0.70f, 0.35f, 0.0f, 0.0f);
			maxHorizontalSpeed = Math.max(maxHorizontalSpeed, horizontalSpeed(step.velocityX(), step.velocityZ()));
			state = stateFrom(step);
		}

		assertTrue(maxHorizontalSpeed >= 25.0f, "candidate cruise headroom too low: " + maxHorizontalSpeed);
		assertTrue(maxHorizontalSpeed <= 52.0f, "candidate exceeded hard speed headroom: " + maxHorizontalSpeed);
	}

	@Test
	void agileCandidateConsumesOnlyBoundedPositiveDa4002AxialReference() {
		float rpm = 5_000.0f;
		float advanceRatioJ = 0.4f;
		float axialVelocity = advanceRatioJ * (rpm / 60.0f)
				* UiucDa4002FiveInchRuntimeLookup.PROPELLER_DIAMETER_METERS;
		float expected = UiucDa4002FiveInchRuntimeLookup.normalizedThrustScale(
				advanceRatioJ,
				rpm
		);

		assertEquals(expected, PlayableFlightModel.acroDa4002AxialThrustScale(
				PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
				axialVelocity,
				rpm
		), 1.0e-6f);
		assertEquals(1.0f, PlayableFlightModel.acroDa4002AxialThrustScale(
				PlayableFlightPreset.LEGACY_HEAVY_RACING_QUAD,
				axialVelocity,
				rpm
		), 0.0f);
		assertEquals(1.0f, PlayableFlightModel.acroDa4002AxialThrustScale(
				PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
				-axialVelocity,
				rpm
		), 0.0f);
		assertEquals(1.0f, PlayableFlightModel.acroDa4002AxialThrustScale(
				PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
				axialVelocity,
				3_999.0f
		), 0.0f);
		assertEquals(1.0f, PlayableFlightModel.acroDa4002AxialThrustScale(
				PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
				axialVelocity,
				6_001.0f
		), 0.0f);
	}

	@Test
	void da4002InfluenceFadesInsideMeasuredEnvelopeEdges() {
		float advanceRatioJ = 0.4f;
		float lowerBoundaryRpm = 4_000.0f;
		float lowerBlendRpm = 4_200.0f;
		float fullWeightRpm = 4_400.0f;

		float boundary = axialReferenceScaleAt(advanceRatioJ, lowerBoundaryRpm);
		float blended = axialReferenceScaleAt(advanceRatioJ, lowerBlendRpm);
		float full = axialReferenceScaleAt(advanceRatioJ, fullWeightRpm);
		assertEquals(1.0f, boundary, 0.0f);
		assertTrue(blended < boundary && blended > full);
		assertEquals(
				UiucDa4002FiveInchRuntimeLookup.normalizedThrustScale(
						advanceRatioJ,
						fullWeightRpm
				),
				full,
				1.0e-6f
		);
	}

	private static float axialReferenceScaleAt(float advanceRatioJ, float rpm) {
		float axialVelocity = advanceRatioJ * (rpm / 60.0f)
				* UiucDa4002FiveInchRuntimeLookup.PROPELLER_DIAMETER_METERS;
		return PlayableFlightModel.acroDa4002AxialThrustScale(
				PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
				axialVelocity,
				rpm
		);
	}

	private static RollResult fullRollSeconds(PlayableFlightModel.State initialState, float throttle) {
		PlayableFlightModel.State state = initialState;
		float maxRollRateDps = 0.0f;
		for (int tick = 0; tick < 40; tick++) {
			PlayableFlightModel.Step step = step(state, throttle, 0.0f, 1.0f, 0.0f);
			maxRollRateDps = Math.max(maxRollRateDps, Math.abs(degreesPerSecond(step.acroRollRateRadiansPerTick())));
			if (Math.abs(step.rollRadians()) >= Math.PI * 2.0) {
				return new RollResult((tick + 1) * TICK_SECONDS, maxRollRateDps);
			}
			state = stateFrom(step);
		}
		return new RollResult(Float.POSITIVE_INFINITY, maxRollRateDps);
	}

	private static PlayableFlightModel.Step step(
			PlayableFlightModel.State state,
			float throttle,
			float pitch,
			float roll,
			float yaw
	) {
		return PlayableFlightModel.step(
				PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE,
				FlightMode.ACRO,
				throttle,
				pitch,
				roll,
				yaw,
				HOVER_THROTTLE,
				false,
				1.0f,
				state
		);
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
				step.acroSidewashMemory()
		);
	}

	private static float degreesPerSecond(float radiansPerTick) {
		return (float) Math.toDegrees(radiansPerTick / TICK_SECONDS);
	}

	private static float horizontalSpeed(float x, float z) {
		return (float) Math.sqrt(x * x + z * z);
	}

	private record RollResult(float seconds, float maxRollRateDps) {
	}
}
