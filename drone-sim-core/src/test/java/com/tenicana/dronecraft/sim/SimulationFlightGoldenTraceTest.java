package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class SimulationFlightGoldenTraceTest {
	private static final String UPDATE_PROPERTY = "fpvdrone.updateGoldenTraces";
	private static final double DT_SECONDS = 0.005;
	private static final double ABS_TOLERANCE = 1.0e-7;
	private static final double REL_TOLERANCE = 1.0e-7;
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
	void simulationTraceMatchesGoldenBaseline() throws IOException {
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

	private static boolean updateGoldenTraces() {
		return Boolean.getBoolean(UPDATE_PROPERTY)
				|| "true".equalsIgnoreCase(System.getenv("FPVDRONE_UPDATE_GOLDEN_TRACES"));
	}

	private static List<String> traceLines() {
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (Scenario scenario : scenarios()) {
			DroneConfig config = scenario.config().create();
			DronePhysics physics = new DronePhysics(config);
			scenario.setup().apply(physics);
			for (int tick = 0; tick < scenario.ticks(); tick++) {
				DroneInput input = scenario.input().at(tick, config).normalized();
				DroneEnvironment environment = scenario.environment().at(tick);
				String correction = scenario.beforeStep().apply(tick, physics);
				physics.step(input, DT_SECONDS, environment);
				lines.add(snapshotLine(scenario.name(), tick, input, physics, correction));
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
				scenario("roll_360", 120, constant(0.66, 0.0, 1.0, 0.0, true, FlightMode.ACRO)),
				scenario("pitch_loop_360", 120, constant(0.66, 1.0, 0.0, 0.0, true, FlightMode.ACRO)),
				scenario("high_speed_forward", 80, constant(0.78, 0.75, 0.0, 0.0, true, FlightMode.ACRO)),
				scenario("lateral_initial_velocity", 48, constant(0.50, 0.0, 0.0, 0.0, true, FlightMode.HORIZON), physics -> {
					setAirborne(physics);
					physics.state().setVelocityMetersPerSecond(new Vec3(12.0, 0.0, 0.0));
				}),
				scenario("wind_field", 64, constant(0.54, 0.35, 0.0, 0.0, true, FlightMode.HORIZON), ScenarioSetup::setAirborne, tick -> new DroneEnvironment(new Vec3(5.0, 0.0, -2.0), 1.0, Double.POSITIVE_INFINITY)),
				scenario("ground_contact", 48, constant(0.28, 0.0, 0.0, 0.0, true, FlightMode.HORIZON), physics -> {
					physics.state().setPositionMeters(new Vec3(0.0, 0.20, 0.0));
					physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
				}, tick -> new DroneEnvironment(Vec3.ZERO, 1.0, 0.08)),
				scenario("collision_constraint", 48, constant(0.42, 0.35, 0.0, 0.0, true, FlightMode.HORIZON), ScenarioSetup::setAirborne, EnvironmentPlan.calm(), (tick, physics) -> {
					if (tick == 18) {
						physics.state().setVelocityMetersPerSecond(new Vec3(0.0, 0.0, 0.0));
						physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
						return "COLLISION_CONSTRAINT";
					}
					return "NONE";
				}),
				scenario("reset_respawn", 48, (tick, config) -> tick < 20
						? new DroneInput(0.58, 0.30, 0.0, 0.0, true, true, FlightMode.HORIZON)
						: new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON), ScenarioSetup::setAirborne, EnvironmentPlan.calm(), (tick, physics) -> {
					if (tick == 20) {
						physics.sleepAtRest(new Vec3(1.5, 2.0, -1.5), new DroneInput(0.0, 0.0, 0.0, 0.0, false, true, FlightMode.ANGLE));
						return "RESET_RESPAWN";
					}
					return "NONE";
				}),
				new Scenario(
						"model_selection_initialization",
						48,
						() -> deterministic(DroneConfig.apDrone()),
						(tick, config) -> new DroneInput(tick < 24 ? config.hoverThrottle() : 0.57, tick < 24 ? 0.0 : 0.32, 0.0, 0.0, true, true, tick < 24 ? FlightMode.ANGLE : FlightMode.HORIZON),
						ScenarioSetup::setAirborne,
						EnvironmentPlan.calm(),
						(tick, physics) -> tick == 0 ? "MODEL_INITIALIZED" : "NONE"
				)
		);
	}

	private static Scenario scenario(String name, int ticks, InputPlan input) {
		return scenario(name, ticks, input, ScenarioSetup::setAirborne);
	}

	private static Scenario scenario(String name, int ticks, InputPlan input, ScenarioSetup setup) {
		return scenario(name, ticks, input, setup, EnvironmentPlan.calm());
	}

	private static Scenario scenario(String name, int ticks, InputPlan input, ScenarioSetup setup, EnvironmentPlan environment) {
		return scenario(name, ticks, input, setup, environment, TickMutation.none());
	}

	private static Scenario scenario(String name, int ticks, InputPlan input, ScenarioSetup setup, EnvironmentPlan environment, TickMutation beforeStep) {
		return new Scenario(name, ticks, () -> deterministic(DroneConfig.racingQuad()), input, setup, environment, beforeStep);
	}

	private static InputPlan constant(double throttle, double pitch, double roll, double yaw, boolean armed, FlightMode mode) {
		return (tick, config) -> new DroneInput(throttle, pitch, roll, yaw, armed, true, mode);
	}

	private static DroneConfig deterministic(DroneConfig config) {
		return config
				.withControlLink(0.0, 0.0, config.rcFailsafeTimeoutSeconds())
				.withControlReceiver(0.0, 0.0)
				.withEscCommandSignal(0.0, 0.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRateSuper(Vec3.ZERO);
	}

	private static void setAirborne(DronePhysics physics) {
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setEstimatedOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
	}

	private static String snapshotLine(String scenario, int tick, DroneInput input, DronePhysics physics, String correction) {
		DroneState state = physics.state();
		Vec3 position = state.positionMeters();
		Vec3 worldVelocity = state.velocityMetersPerSecond();
		Quaternion attitude = state.orientation().normalized();
		Vec3 bodyVelocity = attitude.conjugate().rotate(worldVelocity);
		Vec3 bodyRate = state.angularVelocityBodyRadiansPerSecond();
		double motorPower = state.averageMotorPower(physics.config());
		double averageRpm = state.averageMotorRpm();
		double rotorThrust = averageRotorThrust(state);
		boolean finite = finite(position)
				&& finite(worldVelocity)
				&& finite(bodyVelocity)
				&& finite(attitude)
				&& finite(bodyRate)
				&& finite(motorPower)
				&& finite(averageRpm)
				&& finite(rotorThrust);
		DroneInput processed = state.processedControlInput().normalized();
		return String.join(",",
				"simulation",
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
				number(worldVelocity.x()),
				number(worldVelocity.y()),
				number(worldVelocity.z()),
				number(bodyVelocity.x()),
				number(bodyVelocity.y()),
				number(bodyVelocity.z()),
				number(attitude.w()),
				number(attitude.x()),
				number(attitude.y()),
				number(attitude.z()),
				number(bodyRate.x()),
				number(bodyRate.y()),
				number(bodyRate.z()),
				number(motorPower),
				number(averageRpm),
				number(rotorThrust),
				Integer.toString(processed.flightMode().id()),
				Boolean.toString(processed.armed()),
				Boolean.toString(finite),
				correction
		);
	}

	private static double averageRotorThrust(DroneState state) {
		double[] thrust = state.rotorThrustNewtons();
		double sum = 0.0;
		for (double value : thrust) {
			sum += value;
		}
		return thrust.length == 0 ? 0.0 : sum / thrust.length;
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
		return projectDir().resolve("src/test/resources/golden/flight/simulation-v1.csv");
	}

	private static Path projectDir() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java");
			if (Files.exists(direct)) {
				return current;
			}
			Path child = current.resolve("drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java");
			if (Files.exists(child)) {
				return current.resolve("drone-sim-core");
			}
			current = current.getParent();
		}
		throw new IllegalStateException("Cannot locate drone-sim-core project directory");
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
			EnvironmentPlan environment,
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
		void apply(DronePhysics physics);

		static void setAirborne(DronePhysics physics) {
			SimulationFlightGoldenTraceTest.setAirborne(physics);
		}
	}

	@FunctionalInterface
	private interface EnvironmentPlan {
		DroneEnvironment at(int tick);

		static EnvironmentPlan calm() {
			return tick -> DroneEnvironment.calm();
		}
	}

	@FunctionalInterface
	private interface TickMutation {
		String apply(int tick, DronePhysics physics);

		static TickMutation none() {
			return (tick, physics) -> "NONE";
		}
	}
}
