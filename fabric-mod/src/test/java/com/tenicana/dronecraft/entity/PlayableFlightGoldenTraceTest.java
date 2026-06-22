package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.ActuatorOutput;
import com.tenicana.dronecraft.sim.flight.FlightModelDiagnostics;
import com.tenicana.dronecraft.sim.flight.FlightModelInitializationContext;
import com.tenicana.dronecraft.sim.flight.FlightModelRouter;
import com.tenicana.dronecraft.sim.flight.FlightStateSnapshot;
import com.tenicana.dronecraft.sim.flight.FlightStepContext;
import com.tenicana.dronecraft.sim.flight.FlightStepResult;
import com.tenicana.dronecraft.sim.flight.StateCorrection;
import com.tenicana.dronecraft.sim.flight.StateCorrectionReason;

class PlayableFlightGoldenTraceTest {
	private static final String UPDATE_PROPERTY = "fpvdrone.updateGoldenTraces";
	private static final double DT_SECONDS = 0.05;
	private static final double ABS_TOLERANCE = 1.0e-5;
	private static final double REL_TOLERANCE = 1.0e-6;
	private static final float DEBUG_AXIS_RISE_SMOOTH = PlayableDebugAxisFilter.DEFAULT_RISE_SMOOTHING;
	private static final float DEBUG_AXIS_FALL_SMOOTH = PlayableDebugAxisFilter.DEFAULT_FALL_SMOOTHING;
	private static final float DEBUG_THRUST_DEADZONE = 0.005f;
	private static final float DEBUG_MOVEMENT_EPSILON = 0.015f;
	private static final float LOW_ALTITUDE_LOCKED_AUTHORITY = 0.62f;
	private static final FlightMode DEFAULT_ENTITY_FLIGHT_MODE = FlightMode.DEFAULT_FIRST_FLIGHT;
	private static final String HEADER = String.join(",",
			"model",
			"scenario",
			"tick",
			"dt_s",
			"input_throttle",
			"input_pitch",
			"input_roll",
			"input_yaw",
			"input_armed",
			"input_link_active",
			"input_mode",
			"position_x_m",
			"position_y_m",
			"position_z_m",
			"world_velocity_x_mps",
			"world_velocity_y_mps",
			"world_velocity_z_mps",
			"body_velocity_x_mps",
			"body_velocity_y_mps",
			"body_velocity_z_mps",
			"quat_w",
			"quat_x",
			"quat_y",
			"quat_z",
			"body_rate_x_radps",
			"body_rate_y_radps",
			"body_rate_z_radps",
			"motor_power",
			"average_rpm",
			"rotor_thrust_avg_n",
			"flight_mode",
			"armed",
			"finite",
			"correction"
	);

	@Test
	void playableDirectTraceMatchesGoldenBaseline() throws IOException {
		List<String> actual = traceLines();
		Path golden = goldenPath();
		if (updateGoldenTraces()) {
			Files.createDirectories(golden.getParent());
			Files.write(golden, actual, StandardCharsets.UTF_8);
			return;
		}
		if (!Files.exists(golden)) {
			fail("Missing golden trace " + golden + ". Re-run with " + UPDATE_PROPERTY + "=true or FPVDRONE_UPDATE_GOLDEN_TRACES=true to generate it.");
		}
		assertTraceMatches(Files.readAllLines(golden, StandardCharsets.UTF_8), actual);
	}

	@Test
	void playableAdapterRouterMatchesDirectRouteTickByTick() {
		for (Scenario scenario : scenarios()) {
			DroneConfig config = scenario.config().create();
			DirectRouteHarness direct = new DirectRouteHarness(config);
			scenario.setup().apply(direct);
			LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
			FlightModelRouter router = new FlightModelRouter(List.of(adapter), LegacyPlayableFlightModelAdapter.ID);
			router.initialize(new FlightModelInitializationContext(config, direct.snapshot(), playableEnvironment(scenario, 0, direct), 0L));

			for (int tick = 0; tick < scenario.ticks(); tick++) {
				DroneInput input = scenario.input().at(tick, config).normalized();
				String mutation = scenario.beforeStep().apply(tick, direct);
				applyMutationToRouter(router, direct, mutation);
				DroneEnvironment environment = playableEnvironment(scenario, tick, direct);
				FlightStepResult routeResult = router.step(new FlightStepContext(
						input,
						router.snapshot(),
						environment,
						DT_SECONDS,
						tick,
						config
				));
				direct.step(input, mutation);

				assertSnapshotClose(scenario.name(), tick, direct.snapshot(), routeResult.nextState());
				assertActuatorClose(scenario.name(), tick, direct.actuatorOutput(input), routeResult.actuatorOutput());
				assertCorrectionEvents(scenario.name(), tick, direct.stateCorrectionEvents(), routeResult.stateCorrections());
				assertDiagnosticsClose(scenario.name(), tick, direct.diagnostics(environment), routeResult.diagnostics());
				assertEquals(
						direct.lossyFields(environment),
						routeResult.diagnostics().lossyFields(),
						"scenario=" + scenario.name() + " tick=" + tick + " lossy diagnostics changed"
				);
			}
		}
	}

	private static boolean updateGoldenTraces() {
		return Boolean.getBoolean(UPDATE_PROPERTY)
				|| "true".equalsIgnoreCase(System.getenv("FPVDRONE_UPDATE_GOLDEN_TRACES"));
	}

	private static List<String> traceLines() {
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (Scenario scenario : scenarios()) {
			DirectRouteHarness harness = new DirectRouteHarness(scenario.config().create());
			scenario.setup().apply(harness);
			for (int tick = 0; tick < scenario.ticks(); tick++) {
				DroneInput input = scenario.input().at(tick, harness.config()).normalized();
				String correction = scenario.beforeStep().apply(tick, harness);
				harness.step(input, correction);
				lines.add(harness.snapshotLine(scenario.name(), tick, input));
			}
		}
		return lines;
	}

	private static List<Scenario> scenarios() {
		return List.of(
				scenario("static_disarmed", 16, constant(0.0, 0.0, 0.0, 0.0, false, FlightMode.ANGLE)),
				scenario("armed_hover", 48, (tick, config) -> new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON)),
				scenario("throttle_step", 64, (tick, config) -> new DroneInput(tick < 16 ? config.hoverThrottle() : 0.62, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON)),
				scenario("pitch_input", 48, constant(0.52, 0.55, 0.0, 0.0, true, FlightMode.HORIZON)),
				scenario("roll_input", 48, constant(0.52, 0.0, -0.55, 0.0, true, FlightMode.HORIZON)),
				scenario("yaw_input", 48, constant(0.52, 0.0, 0.0, 0.65, true, FlightMode.ACRO)),
				scenario("pitch_roll_diagonal", 48, constant(0.56, 0.48, -0.48, 0.0, true, FlightMode.ACRO)),
				scenario("roll_360", 140, constant(0.66, 0.0, 1.0, 0.0, true, FlightMode.ACRO)),
				scenario("pitch_loop_360", 140, constant(0.66, 1.0, 0.0, 0.0, true, FlightMode.ACRO)),
				scenario("high_speed_forward", 90, constant(0.78, 0.78, 0.0, 0.0, true, FlightMode.ACRO)),
				scenario("lateral_initial_velocity", 48, constant(0.50, 0.0, 0.0, 0.0, true, FlightMode.HORIZON), harness -> {
					harness.setAirborne();
					harness.setLocalVelocity(12.0f, 0.0f, 0.0f);
				}),
				scenario("wind_field", 64, constant(0.54, 0.35, 0.0, 0.0, true, FlightMode.HORIZON), ScenarioSetup::setAirborne, (tick, harness) -> tick == 0 ? "ENVIRONMENT_WIND_UNREPRESENTED" : "NONE"),
				scenario("ground_contact", 48, constant(0.28, 0.0, 0.0, 0.0, true, FlightMode.HORIZON), harness -> {
					harness.setPosition(new Vec3(0.0, 0.20, 0.0));
					harness.setNearGroundLocked(true);
				}),
				scenario("collision_constraint", 48, constant(0.42, 0.35, 0.0, 0.0, true, FlightMode.HORIZON), ScenarioSetup::setAirborne, (tick, harness) -> {
					if (tick == 18) {
						harness.setLocalVelocity(0.0f, 0.0f, 0.0f);
						return "COLLISION_CONSTRAINT";
					}
					return "NONE";
				}),
				scenario("reset_respawn", 48, (tick, config) -> tick < 20
						? new DroneInput(0.58, 0.30, 0.0, 0.0, true, true, FlightMode.HORIZON)
						: new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON), ScenarioSetup::setAirborne, (tick, harness) -> {
					if (tick == 20) {
						harness.resetAt(new Vec3(1.5, 2.0, -1.5));
						return "RESET_RESPAWN";
					}
					return "NONE";
				}),
				new Scenario(
						"model_selection_initialization",
						48,
						DroneConfig::racingQuad,
						(tick, config) -> new DroneInput(tick < 24 ? config.hoverThrottle() : 0.57, tick < 24 ? 0.0 : 0.32, 0.0, 0.0, true, true, tick < 24 ? FlightMode.ANGLE : FlightMode.ACRO),
						ScenarioSetup::setAirborne,
						(tick, harness) -> tick == 0 ? "MODEL_INITIALIZED" : "NONE"
				)
		);
	}

	private static Scenario scenario(String name, int ticks, InputPlan input) {
		return scenario(name, ticks, input, ScenarioSetup::setAirborne);
	}

	private static Scenario scenario(String name, int ticks, InputPlan input, ScenarioSetup setup) {
		return scenario(name, ticks, input, setup, TickMutation.none());
	}

	private static Scenario scenario(String name, int ticks, InputPlan input, ScenarioSetup setup, TickMutation beforeStep) {
		return new Scenario(name, ticks, DroneConfig::racingQuad, input, setup, beforeStep);
	}

	private static InputPlan constant(double throttle, double pitch, double roll, double yaw, boolean armed, FlightMode mode) {
		return (tick, config) -> new DroneInput(throttle, pitch, roll, yaw, armed, true, mode);
	}

	private static DroneEnvironment playableEnvironment(Scenario scenario, int tick, DirectRouteHarness harness) {
		if ("wind_field".equals(scenario.name())) {
			return new DroneEnvironment(new Vec3(5.0, 0.0, -2.0), 1.0, Double.POSITIVE_INFINITY);
		}
		if (harness.nearGroundLocked) {
			return new DroneEnvironment(Vec3.ZERO, 1.0, 0.08);
		}
		return DroneEnvironment.calm();
	}

	private static void applyMutationToRouter(FlightModelRouter router, DirectRouteHarness direct, String mutation) {
		if ("RESET_RESPAWN".equals(mutation)) {
			router.reset(direct.snapshot());
			return;
		}
		if ("COLLISION_CONSTRAINT".equals(mutation)) {
			router.applyResolvedState(
					direct.snapshot(),
					new StateCorrection(StateCorrectionReason.COLLISION_CONTACT_SOLVE, "COLLISION_CONSTRAINT", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
			);
		}
	}

	private static void assertSnapshotClose(String scenario, int tick, FlightStateSnapshot expected, FlightStateSnapshot actual) {
		assertVecClose(scenario, tick, "position", expected.positionWorldMeters(), actual.positionWorldMeters());
		assertVecClose(scenario, tick, "world_velocity", expected.velocityWorldMetersPerSecond(), actual.velocityWorldMetersPerSecond());
		assertVecClose(scenario, tick, "body_velocity", expected.velocityBodyMetersPerSecond(), actual.velocityBodyMetersPerSecond());
		assertQuaternionClose(scenario, tick, "quaternion", expected.attitude(), actual.attitude());
		assertVecClose(scenario, tick, "angular_rate", expected.angularVelocityBodyRadiansPerSecond(), actual.angularVelocityBodyRadiansPerSecond());
		assertEquals(expected.flightMode(), actual.flightMode(), () -> firstDifference(scenario, tick, "flight_mode", expected.flightMode(), actual.flightMode()));
		assertEquals(expected.armed(), actual.armed(), () -> firstDifference(scenario, tick, "armed", expected.armed(), actual.armed()));
		assertTrue(actual.isFinite(), () -> firstDifference(scenario, tick, "finite", true, false));
	}

	private static void assertActuatorClose(String scenario, int tick, ActuatorOutput expected, ActuatorOutput actual) {
		assertArrayClose(scenario, tick, "actuator.motor_power", expected.motorPower(), actual.motorPower());
		assertArrayClose(scenario, tick, "actuator.motor_rpm", expected.motorRpm(), actual.motorRpm());
		assertArrayClose(scenario, tick, "actuator.rotor_thrust_n", expected.rotorThrustNewtons(), actual.rotorThrustNewtons());
	}

	private static void assertCorrectionEvents(String scenario, int tick, List<String> expected, List<StateCorrection> actual) {
		List<String> actualEvents = actual.stream()
				.map(correction -> correction.reason().name() + ":" + correction.detail())
				.toList();
		assertEquals(expected, actualEvents, () -> firstDifference(scenario, tick, "state_corrections", expected, actualEvents));
	}

	private static void assertDiagnosticsClose(String scenario, int tick, Map<String, String> expected, FlightModelDiagnostics actual) {
		assertTrue(actual.finite(), () -> firstDifference(scenario, tick, "diagnostics.finite", true, false));
		for (Map.Entry<String, String> entry : expected.entrySet()) {
			String actualValue = actual.values().get(entry.getKey());
			assertTrue(actualValue != null, () -> firstDifference(scenario, tick, "diagnostics." + entry.getKey(), entry.getValue(), "<missing>"));
			assertNumberClose(scenario, tick, "diagnostics." + entry.getKey(), Double.parseDouble(entry.getValue()), Double.parseDouble(actualValue));
		}
	}

	private static void assertArrayClose(String scenario, int tick, String field, double[] expected, double[] actual) {
		assertEquals(expected.length, actual.length, () -> firstDifference(scenario, tick, field + ".length", expected.length, actual.length));
		for (int i = 0; i < expected.length; i++) {
			assertNumberClose(scenario, tick, field + "[" + i + "]", expected[i], actual[i]);
		}
	}

	private static void assertVecClose(String scenario, int tick, String field, Vec3 expected, Vec3 actual) {
		assertNumberClose(scenario, tick, field + ".x", expected.x(), actual.x());
		assertNumberClose(scenario, tick, field + ".y", expected.y(), actual.y());
		assertNumberClose(scenario, tick, field + ".z", expected.z(), actual.z());
	}

	private static void assertQuaternionClose(String scenario, int tick, String field, Quaternion expected, Quaternion actual) {
		assertNumberClose(scenario, tick, field + ".w", expected.w(), actual.w());
		assertNumberClose(scenario, tick, field + ".x", expected.x(), actual.x());
		assertNumberClose(scenario, tick, field + ".y", expected.y(), actual.y());
		assertNumberClose(scenario, tick, field + ".z", expected.z(), actual.z());
	}

	private static void assertNumberClose(String scenario, int tick, String field, double expected, double actual) {
		if (Double.compare(expected, actual) == 0) {
			return;
		}
		double scale = Math.max(Math.abs(expected), Math.abs(actual));
		double allowed = Math.max(ABS_TOLERANCE, REL_TOLERANCE * scale);
		double diff = Math.abs(expected - actual);
		if (diff > allowed) {
			double relative = scale <= 0.0 ? diff : diff / scale;
			fail(firstDifference(scenario, tick, field, expected, actual)
					+ " abs=" + diff
					+ " rel=" + relative
					+ " allowed=" + allowed
					+ " source=LegacyPlayableFlightModelAdapter route must preserve DirectRouteHarness call order");
		}
	}

	private static String firstDifference(String scenario, int tick, String field, Object expected, Object actual) {
		return "first route-equivalence difference scenario=" + scenario
				+ " tick=" + tick
				+ " field=" + field
				+ " expected=" + expected
				+ " actual=" + actual;
	}

	private static final class DirectRouteHarness {
		private final DroneConfig config;
		private Vec3 position = new Vec3(0.0, 20.0, 0.0);
		private Vec3 lastWorldVelocity = Vec3.ZERO;
		private Vec3 lastBodyVelocity = Vec3.ZERO;
		private Vec3 lastBodyRate = Vec3.ZERO;
		private Quaternion lastAttitude = Quaternion.IDENTITY;
		private boolean nearGroundLocked;
		private float yawDegrees;
		private float debugVelocityYawDegrees;
		private float debugCommandThrottle;
		private float debugCommandPitch;
		private float debugCommandRoll;
		private float debugCommandYaw;
		private float debugVelocityX;
		private float debugVelocityY;
		private float debugVelocityZ;
		private float debugVisualPitchRadians;
		private float debugVisualRollRadians;
		private float debugTargetYawRate;
		private FlightMode debugFlightMode = DEFAULT_ENTITY_FLIGHT_MODE;
		private int debugModeSwitchTicksRemaining;
		private float debugMotorPower;
		private float debugAverageMotorRpm;
		private float debugAcroCollectiveThrustToWeight;
		private float debugAcroPitchRateRadiansPerTick;
		private float debugAcroRollRateRadiansPerTick;
		private int debugAcroRollRecoveryTicksRemaining;
		private float debugAcroAeroCrossflowLag;
		private float debugAcroSidewashMemory;
		private boolean lastArmed;
		private List<String> lastStateCorrectionEvents = List.of();
		private String lastCorrection = "NONE";

		DirectRouteHarness(DroneConfig config) {
			this.config = config;
		}

		DroneConfig config() {
			return config;
		}

		void setAirborne() {
			setPosition(new Vec3(0.0, 20.0, 0.0));
			setLocalVelocity(0.0f, 0.0f, 0.0f);
			nearGroundLocked = false;
		}

		void setPosition(Vec3 position) {
			this.position = position;
		}

		void setLocalVelocity(float x, float y, float z) {
			debugVelocityX = x;
			debugVelocityY = y;
			debugVelocityZ = z;
			debugVelocityYawDegrees = yawDegrees;
		}

		void setNearGroundLocked(boolean nearGroundLocked) {
			this.nearGroundLocked = nearGroundLocked;
		}

		void resetAt(Vec3 position) {
			this.position = position;
			yawDegrees = 0.0f;
			clearDebugFlightState();
			lastArmed = false;
		}

		void step(DroneInput input, String correction) {
			List<String> correctionEvents = new ArrayList<>();
			rebaseDebugVelocityToCurrentYaw();
			float throttle = clamp((float) input.throttle(), 0.0f, 1.0f);
			float pitch = clamp((float) input.pitch(), -1.0f, 1.0f);
			float roll = clamp((float) input.roll(), -1.0f, 1.0f);
			float yaw = clamp((float) input.yaw(), -1.0f, 1.0f);

			float playablePitch = PlayableFlightModel.playableAxisCommand(pitch);
			float playableRoll = PlayableFlightModel.playableAxisCommand(roll);
			float playableYaw = PlayableFlightModel.playableAxisCommand(yaw);

			float smoothedThrottle = PlayableDebugAxisFilter.throttle(debugCommandThrottle, throttle);
			float smoothedPitch = PlayableDebugAxisFilter.filter(debugCommandPitch, playablePitch, DEBUG_AXIS_RISE_SMOOTH, DEBUG_AXIS_FALL_SMOOTH, true);
			float smoothedRoll = PlayableDebugAxisFilter.filter(debugCommandRoll, playableRoll, DEBUG_AXIS_RISE_SMOOTH, DEBUG_AXIS_FALL_SMOOTH, true);
			float smoothedYaw = PlayableDebugAxisFilter.filter(debugCommandYaw, playableYaw, DEBUG_AXIS_RISE_SMOOTH, DEBUG_AXIS_FALL_SMOOTH, true);

			debugCommandThrottle = smoothedThrottle;
			debugCommandPitch = smoothedPitch;
			debugCommandRoll = smoothedRoll;
			debugCommandYaw = smoothedYaw;

			boolean shouldFly = input.armed()
					|| smoothedThrottle > DEBUG_THRUST_DEADZONE
					|| Math.abs(smoothedPitch) > DEBUG_MOVEMENT_EPSILON
					|| Math.abs(smoothedRoll) > DEBUG_MOVEMENT_EPSILON;
			float previousPitch = debugVisualPitchRadians;
			float previousRoll = debugVisualRollRadians;
			float previousYaw = yawDegrees;
			int previousRecoveryTicks = debugAcroRollRecoveryTicksRemaining;
			float hoverThrottle = clamp((float) config.hoverThrottle(), 0.12f, 0.55f);
			float lowAltitudeHorizontalAuthorityScale = nearGroundLocked ? LOW_ALTITUDE_LOCKED_AUTHORITY : 1.0f;
			PlayableFlightModel.Step step = PlayableFlightModel.step(
					input.flightMode(),
					smoothedThrottle,
					smoothedPitch,
					smoothedRoll,
					smoothedYaw,
					hoverThrottle,
					nearGroundLocked,
					lowAltitudeHorizontalAuthorityScale,
					new PlayableFlightModel.State(
							debugVelocityX,
							debugVelocityY,
							debugVelocityZ,
							debugVisualPitchRadians,
							debugVisualRollRadians,
							debugTargetYawRate,
							debugFlightMode,
							debugModeSwitchTicksRemaining,
							debugAcroCollectiveThrustToWeight,
							debugAcroPitchRateRadiansPerTick,
							debugAcroRollRateRadiansPerTick,
							debugAcroRollRecoveryTicksRemaining,
							debugAcroAeroCrossflowLag,
							debugAcroSidewashMemory
					)
			);

			float targetVx = step.targetVelocityX();
			float targetVy = step.targetVelocityY();
			float targetVz = step.targetVelocityZ();
			float targetYaw = step.yawDegreesPerTick();
			if (!shouldFly) {
				clearDebugFlightState();
				lastCorrection = correction.equals("NONE") ? "IDLE_CLEAR" : correction;
				lastArmed = input.armed();
				correctionEvents.add(StateCorrectionReason.GROUND_STABILIZATION.name() + ":IDLE_CLEAR");
				lastStateCorrectionEvents = List.copyOf(correctionEvents);
				return;
			}
			if (Math.abs(targetVx) < 0.015f) {
				targetVx = 0.0f;
			}
			if (Math.abs(targetVy) < 0.010f) {
				targetVy = 0.0f;
			}
			if (Math.abs(targetVz) < 0.015f) {
				targetVz = 0.0f;
			}
			if (Math.abs(targetYaw) < 0.015f) {
				targetYaw = 0.0f;
			}

			debugVelocityX = step.velocityX();
			debugVelocityY = step.velocityY();
			debugVelocityZ = step.velocityZ();
			debugVisualPitchRadians = step.pitchRadians();
			debugVisualRollRadians = step.rollRadians();
			debugFlightMode = step.mode();
			debugModeSwitchTicksRemaining = step.modeSwitchTicksRemaining();
			debugMotorPower = step.motorPower();
			debugAverageMotorRpm = step.averageRpm();
			debugTargetYawRate = targetYaw;
			debugAcroCollectiveThrustToWeight = step.acroCollectiveThrustToWeight();
			debugAcroPitchRateRadiansPerTick = step.acroPitchRateRadiansPerTick();
			debugAcroRollRateRadiansPerTick = step.acroRollRateRadiansPerTick();
			debugAcroRollRecoveryTicksRemaining = step.acroRollRecoveryTicksRemaining();
			debugAcroAeroCrossflowLag = step.acroAeroCrossflowLag();
			debugAcroSidewashMemory = step.acroSidewashMemory();
			if (previousRecoveryTicks == 0 && debugAcroRollRecoveryTicksRemaining > 0) {
				correctionEvents.add(StateCorrectionReason.COMPLETED_ROLL_VELOCITY_TRIM.name() + ":ACRO_ROLL_RECOVERY_STARTED");
			}

			float movementYawDegrees = PlayableMovementYaw.midpointForTick(yawDegrees, targetYaw);
			PlayableFlightModel.Velocity worldVelocity = PlayableFlightModel.worldVelocityForYaw(
					debugVelocityX,
					debugVelocityY,
					debugVelocityZ,
					movementYawDegrees
			);
			lastWorldVelocity = new Vec3(worldVelocity.x(), worldVelocity.y(), worldVelocity.z());
			position = position.add(lastWorldVelocity.multiply(DT_SECONDS));
			PlayableFlightModel.Velocity localVelocity = PlayableFlightModel.localVelocityForYaw(
					(float) lastWorldVelocity.x(),
					(float) lastWorldVelocity.y(),
					(float) lastWorldVelocity.z(),
					movementYawDegrees
			);
			debugVelocityX = localVelocity.x();
			debugVelocityY = localVelocity.y();
			debugVelocityZ = localVelocity.z();
			debugVelocityYawDegrees = movementYawDegrees;
			if (Math.abs(targetYaw) > PlayableMovementYaw.APPLY_EPSILON_DEGREES) {
				yawDegrees += targetYaw;
			}
			lastAttitude = attitudeQuaternion(yawDegrees, debugVisualPitchRadians, debugVisualRollRadians);
			lastBodyVelocity = lastAttitude.conjugate().rotate(lastWorldVelocity);
			lastBodyRate = new Vec3(
					(debugVisualPitchRadians - previousPitch) / DT_SECONDS,
					Math.toRadians(yawDegrees - previousYaw) / DT_SECONDS,
					(debugVisualRollRadians - previousRoll) / DT_SECONDS
			);
			lastArmed = input.armed();
			lastStateCorrectionEvents = List.copyOf(correctionEvents);
			lastCorrection = correction;
		}

		FlightStateSnapshot snapshot() {
			PlayableFlightModel.Velocity worldVelocity = PlayableFlightModel.worldVelocityForYaw(
					debugVelocityX,
					debugVelocityY,
					debugVelocityZ,
					debugVelocityYawDegrees
			);
			Vec3 velocityWorld = new Vec3(worldVelocity.x(), worldVelocity.y(), worldVelocity.z());
			Quaternion attitude = attitudeQuaternion(yawDegrees, debugVisualPitchRadians, debugVisualRollRadians);
			return new FlightStateSnapshot(
					position,
					velocityWorld,
					attitude,
					lastBodyRate,
					debugFlightMode,
					lastArmed
			);
		}

		ActuatorOutput actuatorOutput(DroneInput input) {
			List<RotorSpec> rotors = config.rotors();
			double[] motorPower = new double[rotors.size()];
			double[] motorRpm = new double[rotors.size()];
			double[] rotorThrust = new double[rotors.size()];
			for (int i = 0; i < rotors.size(); i++) {
				double mix = 1.0 + rotorMixerPreview(i, input);
				motorPower[i] = clamp((float) ((input.armed() ? debugMotorPower : 0.0) * mix), 0.0f, 1.0f);
				motorRpm[i] = Math.max(0.0, (input.armed() ? debugAverageMotorRpm : 0.0) * mix);
				rotorThrust[i] = motorPower[i] * rotors.get(i).maxThrustNewtons() * 0.45;
			}
			return new ActuatorOutput(motorPower, motorRpm, rotorThrust);
		}

		List<String> stateCorrectionEvents() {
			return lastStateCorrectionEvents;
		}

		Map<String, String> diagnostics(DroneEnvironment environment) {
			return Map.ofEntries(
					Map.entry("yaw_degrees", Float.toString(yawDegrees)),
					Map.entry("velocity_yaw_degrees", Float.toString(debugVelocityYawDegrees)),
					Map.entry("velocity_body_x_mps", Float.toString(debugVelocityX)),
					Map.entry("velocity_body_y_mps", Float.toString(debugVelocityY)),
					Map.entry("velocity_body_z_mps", Float.toString(debugVelocityZ)),
					Map.entry("visual_pitch_radians", Float.toString(debugVisualPitchRadians)),
					Map.entry("visual_roll_radians", Float.toString(debugVisualRollRadians)),
					Map.entry("target_yaw_degrees_per_tick", Float.toString(debugTargetYawRate)),
					Map.entry("command_throttle", Float.toString(debugCommandThrottle)),
					Map.entry("command_pitch", Float.toString(debugCommandPitch)),
					Map.entry("command_roll", Float.toString(debugCommandRoll)),
					Map.entry("command_yaw", Float.toString(debugCommandYaw)),
					Map.entry("motor_power", Float.toString(debugMotorPower)),
					Map.entry("average_motor_rpm", Float.toString(debugAverageMotorRpm)),
					Map.entry("low_altitude_horizontal_authority_scale", Float.toString(nearGroundLocked ? LOW_ALTITUDE_LOCKED_AUTHORITY : 1.0f)),
					Map.entry("flight_mode", Integer.toString(debugFlightMode.id())),
					Map.entry("mode_switch_ticks_remaining", Integer.toString(debugModeSwitchTicksRemaining)),
					Map.entry("acro_collective_thrust_to_weight", Float.toString(debugAcroCollectiveThrustToWeight)),
					Map.entry("acro_pitch_rate_radians_per_tick", Float.toString(debugAcroPitchRateRadiansPerTick)),
					Map.entry("acro_roll_rate_radians_per_tick", Float.toString(debugAcroRollRateRadiansPerTick)),
					Map.entry("acro_roll_recovery_ticks_remaining", Integer.toString(debugAcroRollRecoveryTicksRemaining)),
					Map.entry("acro_aero_crossflow_lag", Float.toString(debugAcroAeroCrossflowLag)),
					Map.entry("acro_sidewash_memory", Float.toString(debugAcroSidewashMemory)),
					Map.entry("ground_clearance_m", Double.toString(environment.groundClearanceMeters())),
					Map.entry("ground_lock_threshold_m", "0.3")
			);
		}

		List<String> lossyFields(DroneEnvironment environment) {
			List<String> lossy = new ArrayList<>();
			if (environment.windVelocityWorldMetersPerSecond().length() > 1.0e-9) {
				lossy.add("environment.windVelocityWorldMetersPerSecond");
			}
			if (Math.abs(environment.airDensityRatio() - 1.0) > 1.0e-9) {
				lossy.add("environment.airDensityRatio");
			}
			if (environment.turbulenceIntensity() > 1.0e-9) {
				lossy.add("environment.turbulenceIntensity");
			}
			if (environment.obstacleProximity() > 1.0e-9) {
				lossy.add("environment.obstacleProximity");
			}
			if (Double.isFinite(environment.ceilingClearanceMeters())) {
				lossy.add("environment.ceilingClearanceMeters");
			}
			if (environment.rotorThrustMultipliers() != null) {
				lossy.add("environment.rotorThrustMultipliers");
			}
			if (environment.rotorFlowObstructions() != null) {
				lossy.add("environment.rotorFlowObstructions");
			}
			return lossy;
		}

		String snapshotLine(String scenario, int tick, DroneInput input) {
			double rotorThrust = averageRotorThrust(input);
			boolean finite = finite(position)
					&& finite(lastWorldVelocity)
					&& finite(lastBodyVelocity)
					&& finite(lastAttitude)
					&& finite(lastBodyRate)
					&& finite(debugMotorPower)
					&& finite(debugAverageMotorRpm)
					&& finite(rotorThrust);
			return String.join(",",
					"playable-direct",
					scenario,
					Integer.toString(tick),
					number(DT_SECONDS),
					number(input.throttle()),
					number(input.pitch()),
					number(input.roll()),
					number(input.yaw()),
					Boolean.toString(input.armed()),
					Boolean.toString(input.linkActive()),
					Integer.toString(input.flightMode().id()),
					number(position.x()),
					number(position.y()),
					number(position.z()),
					number(lastWorldVelocity.x()),
					number(lastWorldVelocity.y()),
					number(lastWorldVelocity.z()),
					number(lastBodyVelocity.x()),
					number(lastBodyVelocity.y()),
					number(lastBodyVelocity.z()),
					number(lastAttitude.w()),
					number(lastAttitude.x()),
					number(lastAttitude.y()),
					number(lastAttitude.z()),
					number(lastBodyRate.x()),
					number(lastBodyRate.y()),
					number(lastBodyRate.z()),
					number(input.armed() ? debugMotorPower : 0.0),
					number(input.armed() ? debugAverageMotorRpm : 0.0),
					number(input.armed() ? rotorThrust : 0.0),
					Integer.toString(debugFlightMode.id()),
					Boolean.toString(input.armed()),
					Boolean.toString(finite),
					lastCorrection
			);
		}

		private double averageRotorThrust(DroneInput input) {
			List<RotorSpec> rotors = config.rotors();
			double sum = 0.0;
			for (int i = 0; i < rotors.size(); i++) {
				double mix = 1.0 + rotorMixerPreview(i, input);
				double motorPower = clamp((float) (debugMotorPower * mix), 0.0f, 1.0f);
				sum += motorPower * rotors.get(i).maxThrustNewtons() * 0.45;
			}
			return rotors.isEmpty() ? 0.0 : sum / rotors.size();
		}

		private double rotorMixerPreview(int rotorIndex, DroneInput input) {
			double rollSign = switch (rotorIndex & 3) {
				case 0, 3 -> 1.0;
				default -> -1.0;
			};
			double pitchSign = switch (rotorIndex & 3) {
				case 0, 1 -> 1.0;
				default -> -1.0;
			};
			double yawSign = config.rotors().get(rotorIndex).spinDirection();
			return 0.04 * input.roll() * rollSign
					+ 0.04 * input.pitch() * pitchSign
					+ 0.025 * input.yaw() * yawSign;
		}

		private void rebaseDebugVelocityToCurrentYaw() {
			if (Math.abs(yawDegrees - debugVelocityYawDegrees) <= 1.0e-4f) {
				return;
			}
			PlayableFlightModel.Velocity localVelocity = PlayableFlightModel.reframeVelocityForYaw(
					debugVelocityX,
					debugVelocityY,
					debugVelocityZ,
					debugVelocityYawDegrees,
					yawDegrees
			);
			debugVelocityX = localVelocity.x();
			debugVelocityY = localVelocity.y();
			debugVelocityZ = localVelocity.z();
			debugVelocityYawDegrees = yawDegrees;
		}

		private void clearDebugFlightState() {
			debugVelocityX = 0.0f;
			debugVelocityY = 0.0f;
			debugVelocityZ = 0.0f;
			debugVelocityYawDegrees = yawDegrees;
			debugVisualPitchRadians = 0.0f;
			debugVisualRollRadians = 0.0f;
			debugMotorPower = 0.0f;
			debugAverageMotorRpm = 0.0f;
			debugTargetYawRate = 0.0f;
			debugAcroCollectiveThrustToWeight = 0.0f;
			debugAcroPitchRateRadiansPerTick = 0.0f;
			debugAcroRollRateRadiansPerTick = 0.0f;
			debugAcroRollRecoveryTicksRemaining = 0;
			debugAcroAeroCrossflowLag = 0.0f;
			debugAcroSidewashMemory = 0.0f;
			debugModeSwitchTicksRemaining = 0;
			debugCommandThrottle = 0.0f;
			debugCommandPitch = 0.0f;
			debugCommandRoll = 0.0f;
			debugCommandYaw = 0.0f;
			debugFlightMode = DEFAULT_ENTITY_FLIGHT_MODE;
			lastWorldVelocity = Vec3.ZERO;
			lastBodyVelocity = Vec3.ZERO;
			lastBodyRate = Vec3.ZERO;
			lastAttitude = attitudeQuaternion(yawDegrees, 0.0f, 0.0f);
		}
	}

	private static Quaternion attitudeQuaternion(double yawDegrees, double pitchRadians, double rollRadians) {
		Quaternion yaw = axisAngle(0.0, 1.0, 0.0, Math.toRadians(yawDegrees));
		Quaternion pitch = axisAngle(1.0, 0.0, 0.0, pitchRadians);
		Quaternion roll = axisAngle(0.0, 0.0, 1.0, rollRadians);
		return yaw.multiply(pitch).multiply(roll).normalized();
	}

	private static Quaternion axisAngle(double x, double y, double z, double radians) {
		double half = radians * 0.5;
		double sin = Math.sin(half);
		return new Quaternion(Math.cos(half), x * sin, y * sin, z * sin).normalized();
	}

	private static float clamp(float value, float min, float max) {
		if (!Float.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	private static boolean finite(Vec3 value) {
		return value != null && value.isFinite();
	}

	private static boolean finite(Quaternion value) {
		return value != null
				&& finite(value.w())
				&& finite(value.x())
				&& finite(value.y())
				&& finite(value.z());
	}

	private static boolean finite(double value) {
		return Double.isFinite(value);
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.12g", value);
	}

	private static Path goldenPath() {
		return projectDir().resolve("src/test/resources/golden/flight/playable-direct-v1.csv");
	}

	private static Path projectDir() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/entity/PlayableFlightModel.java");
			if (Files.exists(direct)) {
				return current;
			}
			Path child = current.resolve("fabric-mod/src/main/java/com/tenicana/dronecraft/entity/PlayableFlightModel.java");
			if (Files.exists(child)) {
				return current.resolve("fabric-mod");
			}
			current = current.getParent();
		}
		throw new IllegalStateException("Cannot locate fabric-mod project directory");
	}

	private static void assertTraceMatches(List<String> expected, List<String> actual) {
		assertEquals(expected.get(0), actual.get(0), "golden header changed");
		assertEquals(expected.size(), actual.size(), "golden trace line count changed");
		String[] columns = expected.get(0).split(",", -1);
		for (int line = 1; line < expected.size(); line++) {
			String[] expectedParts = expected.get(line).split(",", -1);
			String[] actualParts = actual.get(line).split(",", -1);
			assertEquals(columns.length, expectedParts.length, "golden column count changed at line " + line);
			assertEquals(columns.length, actualParts.length, "actual column count changed at line " + line);
			for (int column = 0; column < columns.length; column++) {
				if (isNumeric(columns[column])) {
					assertNumberClose(columns[column], line, expectedParts[column], actualParts[column]);
				} else {
					assertEquals(expectedParts[column], actualParts[column], "column " + columns[column] + " changed at line " + line);
				}
			}
		}
	}

	private static boolean isNumeric(String column) {
		return switch (column) {
			case "model", "scenario", "input_armed", "input_link_active", "input_mode", "flight_mode", "armed", "finite", "correction" -> false;
			default -> true;
		};
	}

	private static void assertNumberClose(String column, int line, String expectedText, String actualText) {
		double expected = Double.parseDouble(expectedText);
		double actual = Double.parseDouble(actualText);
		if (Double.compare(expected, actual) == 0) {
			return;
		}
		double scale = Math.max(Math.abs(expected), Math.abs(actual));
		double allowed = Math.max(ABS_TOLERANCE, REL_TOLERANCE * scale);
		double diff = Math.abs(expected - actual);
		if (diff > allowed) {
			fail("column " + column + " changed at line " + line + ": expected=" + expectedText + " actual=" + actualText + " diff=" + diff + " allowed=" + allowed);
		}
	}

	private record Scenario(
			String name,
			int ticks,
			ConfigPlan config,
			InputPlan input,
			ScenarioSetup setup,
			TickMutation beforeStep
	) {
	}

	@FunctionalInterface
	private interface ConfigPlan {
		DroneConfig create();
	}

	@FunctionalInterface
	private interface InputPlan {
		DroneInput at(int tick, DroneConfig config);
	}

	@FunctionalInterface
	private interface ScenarioSetup {
		void apply(DirectRouteHarness harness);

		static void setAirborne(DirectRouteHarness harness) {
			harness.setAirborne();
		}
	}

	@FunctionalInterface
	private interface TickMutation {
		String apply(int tick, DirectRouteHarness harness);

		static TickMutation none() {
			return (tick, harness) -> "NONE";
		}
	}
}
