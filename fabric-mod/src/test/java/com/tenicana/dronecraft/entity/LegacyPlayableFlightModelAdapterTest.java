package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.FlightModelInitializationContext;
import com.tenicana.dronecraft.sim.flight.FlightStateSnapshot;
import com.tenicana.dronecraft.sim.flight.FlightStepContext;
import com.tenicana.dronecraft.sim.flight.FlightStepResult;
import com.tenicana.dronecraft.sim.flight.StateCorrection;
import com.tenicana.dronecraft.sim.flight.StateCorrectionReason;

class LegacyPlayableFlightModelAdapterTest {
	@Test
	void exposesPlayableCapabilitiesAndFiniteStep() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(
				config,
				new FlightStateSnapshot(new Vec3(0.0, 20.0, 0.0), Vec3.ZERO, null, Vec3.ZERO, FlightMode.HORIZON, false),
				DroneEnvironment.calm(),
				0L
		));

		FlightStepResult result = adapter.step(new FlightStepContext(
				new DroneInput(0.52, 0.35, -0.20, 0.10, true, true, FlightMode.HORIZON),
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config
		));

		assertEquals("legacy_playable_direct", adapter.id());
		assertTrue(adapter.capabilities().motorTelemetry());
		assertTrue(adapter.capabilities().assistedStateCorrection());
		assertTrue(adapter.capabilities().lossyStateMapping());
		assertTrue(result.nextState().isFinite());
		assertTrue(result.actuatorOutput().averageMotorRpm() > 0.0);
		assertTrue(Double.parseDouble(result.diagnostics().values().get("estimated_mechanical_rpm")) > 0.0);
		assertTrue(Double.parseDouble(result.diagnostics().values().get("rotor_reference_rpm")) > result.actuatorOutput().averageMotorRpm());
		assertEquals(FlightMode.HORIZON, result.nextState().flightMode());
	}

	@Test
	void reportsUnrepresentedWindWithoutApplyingIt() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.HORIZON), DroneEnvironment.calm(), 0L));

		FlightStepResult calm = adapter.step(new FlightStepContext(
				new DroneInput(0.54, 0.35, 0.0, 0.0, true, true, FlightMode.HORIZON),
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config
		));
		Vec3 calmVelocity = calm.nextState().velocityWorldMetersPerSecond();

		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.HORIZON), DroneEnvironment.calm(), 0L));
		FlightStepResult windy = adapter.step(new FlightStepContext(
				new DroneInput(0.54, 0.35, 0.0, 0.0, true, true, FlightMode.HORIZON),
				adapter.snapshot(),
				new DroneEnvironment(new Vec3(5.0, 0.0, -2.0), 1.0, Double.POSITIVE_INFINITY),
				0.05,
				1L,
				config
		));

		assertVecClose(calmVelocity, windy.nextState().velocityWorldMetersPerSecond(), 1.0e-12);
		assertTrue(windy.diagnostics().lossyFields().contains("environment.windVelocityWorldMetersPerSecond"));
	}

	@Test
	void idleDisarmedStepReportsExplicitCorrection() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.ANGLE), DroneEnvironment.calm(), 0L));

		FlightStepResult result = adapter.step(new FlightStepContext(
				new DroneInput(0.0, 0.0, 0.0, 0.0, false, true, FlightMode.ANGLE),
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config
		));

		assertFalse(result.nextState().armed());
		assertEquals(StateCorrectionReason.GROUND_STABILIZATION, result.stateCorrections().get(0).reason());
		assertEquals(0.0, result.actuatorOutput().averageMotorPower(), 1.0e-12);
	}

	@Test
	void playablePresetOptionRoutesIntoFlightStep() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter legacy = initializedAdapter(config, FlightMode.ACRO);
		LegacyPlayableFlightModelAdapter agile = initializedAdapter(config, FlightMode.ACRO);

		FlightStepResult legacyResult = legacy.step(new FlightStepContext(
				new DroneInput(0.68, 0.0, 1.0, 0.0, true, true, FlightMode.ACRO),
				legacy.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config,
				Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.LEGACY_HEAVY_RACING_QUAD.id())
		));
		FlightStepResult agileResult = agile.step(new FlightStepContext(
				new DroneInput(0.68, 0.0, 1.0, 0.0, true, true, FlightMode.ACRO),
				agile.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config,
				Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id())
		));

		assertEquals(PlayableFlightPreset.LEGACY_HEAVY_RACING_QUAD.id(), legacyResult.diagnostics().values().get("playable_preset"));
		assertEquals(PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id(), agileResult.diagnostics().values().get("playable_preset"));
		assertTrue(
				diagnosticFloat(agileResult, "acro_roll_rate_radians_per_tick")
						> diagnosticFloat(legacyResult, "acro_roll_rate_radians_per_tick") * 2.0f
		);
	}

	@Test
	void resolvedIntegrationStateDoesNotFoldContinuousAcroRoll() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(
				config,
				new FlightStateSnapshot(new Vec3(0.0, 20.0, 0.0), Vec3.ZERO, null, Vec3.ZERO, FlightMode.ACRO, true),
				DroneEnvironment.calm(),
				0L
		));

		FlightStepResult result = null;
		for (int tick = 0; tick < 140; tick++) {
			result = adapter.step(new FlightStepContext(
					new DroneInput(0.66, 0.0, 1.0, 0.0, true, true, FlightMode.ACRO),
					adapter.snapshot(),
					DroneEnvironment.calm(),
					0.05,
					tick,
					config
			));
			if (Math.abs(diagnosticFloat(result, "visual_roll_radians")) > 2.5f) {
				break;
			}
		}

		float rollBefore = diagnosticFloat(result, "visual_roll_radians");
		adapter.applyResolvedState(
				result.nextState(),
				new StateCorrection(StateCorrectionReason.NORMAL_INTEGRATION, "TEST_WORLD_RESOLUTION", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
		);
		float rollAfter = Float.parseFloat(adapter.diagnostics().values().get("visual_roll_radians"));

		assertTrue(Math.abs(rollBefore) > 2.5f, "rollBefore=" + rollBefore);
		assertEquals(rollBefore, rollAfter, 1.0e-6f);
	}

	@Test
	void resolvedIntegrationStateDoesNotFoldContinuousAcroYawPastNinetyDegrees() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(
				config,
				new FlightStateSnapshot(new Vec3(0.0, 20.0, 0.0), Vec3.ZERO, null, Vec3.ZERO, FlightMode.ACRO, true),
				DroneEnvironment.calm(),
				0L
		));

		FlightStepResult result = null;
		for (int tick = 0; tick < 80; tick++) {
			result = adapter.step(new FlightStepContext(
					new DroneInput(0.52, 0.0, 0.0, 1.0, true, true, FlightMode.ACRO),
					adapter.snapshot(),
					DroneEnvironment.calm(),
					0.05,
					tick,
					config
			));
			if (diagnosticFloat(result, "yaw_degrees") > 120.0f) {
				break;
			}
		}

		float yawBefore = diagnosticFloat(result, "yaw_degrees");
		adapter.applyResolvedState(
				result.nextState(),
				new StateCorrection(StateCorrectionReason.RESET_TELEPORT, "TEST_HEADING_SNAPSHOT", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
		);
		float yawAfter = Float.parseFloat(adapter.diagnostics().values().get("yaw_degrees"));

		assertTrue(yawBefore > 120.0f, "yawBefore=" + yawBefore);
		assertEquals(0.0, angularDifferenceDegrees(yawBefore, yawAfter), 1.0e-4,
				() -> "yawBefore=" + yawBefore + " yawAfter=" + yawAfter);
	}

	@Test
	void signedAcroRevolutionsRetainContinuousAnglesThroughIntegrationAndTeleport() {
		DroneConfig config = DroneConfig.racingQuad();
		for (boolean pitchAxis : new boolean[] { true, false }) {
			for (double sign : new double[] { -1.0, 1.0 }) {
				LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
				adapter.initialize(new FlightModelInitializationContext(
						config,
						new FlightStateSnapshot(new Vec3(0.0, 500.0, 0.0), Vec3.ZERO,
								PlayableAttitude.quaternion(137.0, 0.0, 0.0), Vec3.ZERO, FlightMode.ACRO, true),
						DroneEnvironment.calm(), 0L
				));
				boolean crossedFirstTurn = false, crossedSecondTurn = false;
				for (int tick = 0; tick < 100; tick++) {
					FlightStepResult result = adapter.step(new FlightStepContext(
							new DroneInput(0.20, pitchAxis ? sign : 0.0, pitchAxis ? 0.0 : sign,
									0.0, true, true, FlightMode.ACRO),
							adapter.snapshot(), DroneEnvironment.calm(), 0.05, tick, config,
							Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id())
					));
					float pitch = diagnosticFloat(result, "visual_pitch_radians");
					float roll = diagnosticFloat(result, "visual_roll_radians");
					float yaw = diagnosticFloat(result, "yaw_degrees");
					assertAttitudeBasisEquals(PlayableAttitude.quaternion(yaw, pitch, roll), result.nextState().attitude());
					adapter.applyResolvedState(result.nextState(), new StateCorrection(
							StateCorrectionReason.NORMAL_INTEGRATION, "TEST_WORLD_RESOLUTION", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO
					));
					assertEquals(pitch, adapterDiagnosticFloat(adapter, "visual_pitch_radians"), 1.0e-6f);
					assertEquals(roll, adapterDiagnosticFloat(adapter, "visual_roll_radians"), 1.0e-6f);
					double turnAngle = sign * (pitchAxis ? pitch : roll);
					if ((!crossedFirstTurn && turnAngle > Math.PI * 2.0)
							|| (!crossedSecondTurn && turnAngle > Math.PI * 4.0)) {
						FlightStateSnapshot before = adapter.snapshot();
						adapter.applyResolvedState(before, new StateCorrection(
								StateCorrectionReason.RESET_TELEPORT, "TEST_RESTORED_ATTITUDE", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO
						));
						assertEquals(pitch, adapterDiagnosticFloat(adapter, "visual_pitch_radians"), 2.0e-5f);
						assertEquals(roll, adapterDiagnosticFloat(adapter, "visual_roll_radians"), 2.0e-5f);
						assertEquals(yaw, adapterDiagnosticFloat(adapter, "yaw_degrees"), 2.0e-4f);
						assertAttitudeBasisEquals(before.attitude(), adapter.snapshot().attitude());
						if (turnAngle > Math.PI * 4.0) {
							crossedSecondTurn = true;
							break;
						}
						crossedFirstTurn = true;
					}
				}
				assertTrue(crossedFirstTurn && crossedSecondTurn, "pitchAxis=" + pitchAxis + " sign=" + sign);
			}
		}
	}

	@Test
	void playableYawSnapshotAndPhysicalForwardUseTheSameMinecraftConvention() {
		DroneConfig config = DroneConfig.racingQuad();
		for (double yaw : new double[] { -137.0, -90.0, 0.0, 90.0, 137.0 }) {
			LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
			Quaternion initial = PlayableAttitude.quaternion(yaw, Math.toRadians(25.0), 0.0);
			adapter.initialize(new FlightModelInitializationContext(config,
					new FlightStateSnapshot(new Vec3(0.0, 500.0, 0.0), Vec3.ZERO, initial, Vec3.ZERO, FlightMode.ACRO, true),
					DroneEnvironment.calm(), 0L));
			assertAttitudeBasisEquals(initial, adapter.snapshot().attitude());
			FlightStepResult result = adapter.step(new FlightStepContext(
					new DroneInput(0.50, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO),
					adapter.snapshot(), DroneEnvironment.calm(), 0.05, 1L, config,
					Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id())));
			Vec3 forward = result.nextState().attitude().rotate(new Vec3(0.0, 0.0, 1.0));
			Vec3 velocity = result.nextState().velocityWorldMetersPerSecond();
			assertTrue(forward.x() * velocity.x() + forward.z() * velocity.z() > 0.0, "yaw=" + yaw);
		}
	}

	private static float adapterDiagnosticFloat(LegacyPlayableFlightModelAdapter adapter, String key) {
		return Float.parseFloat(adapter.diagnostics().values().get(key));
	}

	@Test
	void tinyBankedCorrectionsPreserveTheirCompleteBodyRotation() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config,
				new FlightStateSnapshot(new Vec3(0, 500, 0), Vec3.ZERO,
						PlayableAttitude.quaternion(37, Math.toRadians(25), Math.toRadians(1)), Vec3.ZERO, FlightMode.ACRO, true),
				DroneEnvironment.calm(), 0));
		Quaternion before = adapter.snapshot().attitude();
		FlightStepResult result = adapter.step(new FlightStepContext(
				new DroneInput(0.20, 0.020, 0, 0, true, true, FlightMode.ACRO),
				adapter.snapshot(), DroneEnvironment.calm(), 0.05, 1, config,
				Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id())));
		double coupling = diagnosticFloat(result, "acro_heading_coupling_degrees_per_tick");
		assertTrue(Math.abs(coupling) > 0.0 && Math.abs(coupling) < 0.015);
		double pitchRate = diagnosticFloat(result, "acro_pitch_rate_radians_per_tick");
		double rollRate = diagnosticFloat(result, "acro_roll_rate_radians_per_tick");
		double angle = Math.hypot(pitchRate, rollRate), scale = Math.sin(angle / 2.0) / angle;
		Quaternion increment = new Quaternion(Math.cos(angle / 2.0), pitchRate * scale, 0, rollRate * scale);
		assertAttitudeBasisEquals(before.multiply(increment), result.nextState().attitude());
	}

	@Test
	void restoringMixedTurnDoesNotConvertGeometricHeadingIntoYawMomentum() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config,
				new FlightStateSnapshot(new Vec3(0, 500, 0), Vec3.ZERO,
						PlayableAttitude.quaternion(37, Math.toRadians(73), Math.toRadians(80)), Vec3.ZERO, FlightMode.ACRO, true),
				DroneEnvironment.calm(), 0));
		FlightStepResult turning = adapter.step(new FlightStepContext(
				new DroneInput(0.20, 0.20, 0, 0, true, true, FlightMode.ACRO),
				adapter.snapshot(), DroneEnvironment.calm(), 0.05, 1, config,
				Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id())));
		assertTrue(Math.abs(diagnosticFloat(turning, "acro_heading_coupling_degrees_per_tick")) > 1.0);
		adapter.applyResolvedState(turning.nextState(), new StateCorrection(
				StateCorrectionReason.RESET_TELEPORT, "TEST_MIXED_TURN_RESTORE", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO));
		FlightStepResult neutral = adapter.step(new FlightStepContext(
				new DroneInput(0.20, 0, 0, 0, true, true, FlightMode.ACRO),
				adapter.snapshot(), DroneEnvironment.calm(), 0.05, 2, config,
				Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id())));
		assertEquals(0.0f,
				diagnosticFloat(neutral, "target_yaw_degrees_per_tick") - diagnosticFloat(neutral, "acro_heading_coupling_degrees_per_tick"),
				1.0e-5f);
	}

	@Test
	void verticalGeometricHeadingChangeDoesNotRotateWorldMomentum() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		Vec3 initialVelocity = new Vec3(6, 0, 0);
		adapter.initialize(new FlightModelInitializationContext(config,
				new FlightStateSnapshot(new Vec3(0, 500, 0), initialVelocity,
						PlayableAttitude.quaternion(37, Math.toRadians(89), Math.toRadians(80)), Vec3.ZERO, FlightMode.ACRO, true),
				DroneEnvironment.calm(), 0));
		FlightStepResult result = adapter.step(new FlightStepContext(
				new DroneInput(0.20, 0.50, 0, 0, true, true, FlightMode.ACRO),
				adapter.snapshot(), DroneEnvironment.calm(), 0.05, 1, config,
				Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE.id())));
		assertTrue(Math.abs(diagnosticFloat(result, "acro_heading_coupling_degrees_per_tick")) > 20.0);
		Vec3 change = result.nextState().velocityWorldMetersPerSecond().subtract(initialVelocity);
		assertTrue(change.length() < 1.20, "coordinate branch change must not rotate existing velocity: " + change);
	}

	private static void assertAttitudeBasisEquals(Quaternion expected, Quaternion actual) {
		for (Vec3 axis : new Vec3[] { new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0) }) {
			assertVecClose(expected.rotate(axis), actual.rotate(axis), 3.0e-6);
		}
	}

	private static void assertVecClose(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.y(), actual.y(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.z(), actual.z(), tolerance, () -> "expected=" + expected + " actual=" + actual);
	}

	private static float diagnosticFloat(FlightStepResult result, String key) {
		String value = result.diagnostics().values().get(key);
		return value == null ? 0.0f : Float.parseFloat(value);
	}

	private static LegacyPlayableFlightModelAdapter initializedAdapter(DroneConfig config, FlightMode mode) {
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(
				config,
				new FlightStateSnapshot(new Vec3(0.0, 20.0, 0.0), Vec3.ZERO, null, Vec3.ZERO, mode, true),
				DroneEnvironment.calm(),
				0L
		));
		return adapter;
	}

	private static double angularDifferenceDegrees(double expected, double actual) {
		double difference = actual - expected;
		while (difference > 180.0) {
			difference -= 360.0;
		}
		while (difference < -180.0) {
			difference += 360.0;
		}
		return difference;
	}
}
