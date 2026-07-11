package com.tenicana.dronecraft.entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.FlightModel;
import com.tenicana.dronecraft.sim.flight.FlightModelInitializationContext;
import com.tenicana.dronecraft.sim.flight.FlightStateSnapshot;
import com.tenicana.dronecraft.sim.flight.FlightStepContext;
import com.tenicana.dronecraft.sim.flight.FlightStepResult;
import com.tenicana.dronecraft.sim.flight.SimulationFlightModelAdapter;
import com.tenicana.dronecraft.sim.flight.StateCorrection;

public final class FlightModelComparisonRunner {
	private static final double DT_SECONDS = 0.05;
	private static final double POSITION_THRESHOLD_METERS = 0.50;
	private static final double VELOCITY_THRESHOLD_METERS_PER_SECOND = 0.50;
	private static final double ATTITUDE_THRESHOLD_RADIANS = 0.25;
	private static final double ANGULAR_RATE_THRESHOLD_RADIANS_PER_SECOND = 0.75;
	private static final double MOTOR_POWER_THRESHOLD = 0.20;
	private static final double RPM_THRESHOLD = 2500.0;
	private static final double THRUST_THRESHOLD_NEWTONS = 2.0;
	private static final String HEADER = String.join(",",
			"scenario",
			"tick",
			"dt_s",
			"position_diff_m",
			"velocity_diff_mps",
			"attitude_distance_rad",
			"angular_rate_diff_radps",
			"motor_power_diff",
			"average_rpm_diff",
			"rotor_thrust_diff_n",
			"playable_state_corrections",
			"simulation_state_corrections",
			"first_threshold_tick"
	);

	private FlightModelComparisonRunner() {
	}

	public static void main(String[] args) throws IOException {
		Path output = args.length == 0
				? Path.of("build", "flight-comparison", "comparison.csv")
				: Path.of(args[0]);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.write(output, runCsv(), StandardCharsets.UTF_8);
	}

	public static List<String> runCsv() {
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (Scenario scenario : scenarios()) {
			lines.addAll(runScenario(scenario));
		}
		return lines;
	}

	private static List<String> runScenario(Scenario scenario) {
		DroneConfig config = DroneConfig.racingQuad();
		FlightModel playable = new LegacyPlayableFlightModelAdapter();
		FlightModel simulation = new SimulationFlightModelAdapter();
		FlightStateSnapshot initial = scenario.initialState();
		DroneEnvironment initialEnvironment = scenario.environment().at(0);
		playable.initialize(new FlightModelInitializationContext(config, initial, initialEnvironment, 0L));
		simulation.initialize(new FlightModelInitializationContext(config, initial, initialEnvironment, 0L));

		List<Row> rows = new ArrayList<>();
		int firstThresholdTick = -1;
		for (int tick = 0; tick < scenario.ticks(); tick++) {
			DroneInput input = scenario.input().at(tick, config).normalized();
			DroneEnvironment environment = scenario.environment().at(tick);
			FlightStepResult playableResult = playable.step(new FlightStepContext(input, playable.snapshot(), environment, DT_SECONDS, tick, config));
			FlightStepResult simulationResult = simulation.step(new FlightStepContext(input, simulation.snapshot(), environment, DT_SECONDS, tick, config));
			Row row = Row.from(scenario.name(), tick, playableResult, simulationResult);
			if (firstThresholdTick < 0 && row.exceedsThreshold()) {
				firstThresholdTick = tick;
			}
			rows.add(row);
		}
		final int thresholdTick = firstThresholdTick;
		return rows.stream()
				.map(row -> row.toCsv(thresholdTick))
				.toList();
	}

	private static List<Scenario> scenarios() {
		return List.of(
				scenario("hover", 80, (tick, config) -> new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON)),
				scenario("throttle_step", 90, (tick, config) -> new DroneInput(tick < 20 ? config.hoverThrottle() : 0.62, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON)),
				scenario("pitch", 70, constant(0.52, 0.55, 0.0, 0.0, FlightMode.HORIZON)),
				scenario("roll", 70, constant(0.52, 0.0, -0.55, 0.0, FlightMode.HORIZON)),
				scenario("yaw", 70, constant(0.52, 0.0, 0.0, 0.65, FlightMode.ACRO)),
				scenario("diagonal", 70, constant(0.56, 0.48, -0.48, 0.0, FlightMode.ACRO)),
				scenario("full_roll", 150, constant(0.66, 0.0, 1.0, 0.0, FlightMode.ACRO)),
				new Scenario("forward_cruise", 90, constant(0.64, 0.45, 0.0, 0.0, FlightMode.ACRO), calm(), new FlightStateSnapshot(new Vec3(0.0, 20.0, 0.0), new Vec3(0.0, 0.0, 12.0), Quaternion.IDENTITY, Vec3.ZERO, FlightMode.ACRO, true)),
				new Scenario("crosswind", 90, constant(0.54, 0.35, 0.0, 0.0, FlightMode.HORIZON), tick -> new DroneEnvironment(new Vec3(5.0, 0.0, -2.0), 1.0, Double.POSITIVE_INFINITY), defaultInitial(FlightMode.HORIZON)),
				new Scenario("collision_free_recovery", 90, (tick, config) -> tick < 20
						? new DroneInput(0.58, 0.45, -0.35, 0.0, true, true, FlightMode.HORIZON)
						: new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON), calm(), defaultInitial(FlightMode.HORIZON))
		);
	}

	private static Scenario scenario(String name, int ticks, InputPlan input) {
		return new Scenario(name, ticks, input, calm(), defaultInitial(FlightMode.HORIZON));
	}

	private static InputPlan constant(double throttle, double pitch, double roll, double yaw, FlightMode mode) {
		return (tick, config) -> new DroneInput(throttle, pitch, roll, yaw, true, true, mode);
	}

	private static EnvironmentPlan calm() {
		return tick -> DroneEnvironment.calm();
	}

	private static FlightStateSnapshot defaultInitial(FlightMode mode) {
		return new FlightStateSnapshot(new Vec3(0.0, 20.0, 0.0), Vec3.ZERO, Quaternion.IDENTITY, Vec3.ZERO, mode, true);
	}

	private static double vectorDistance(Vec3 a, Vec3 b) {
		return a.subtract(b).length();
	}

	private static double quaternionDistance(Quaternion a, Quaternion b) {
		Quaternion na = a.normalized();
		Quaternion nb = b.normalized();
		double dot = Math.abs(na.w() * nb.w() + na.x() * nb.x() + na.y() * nb.y() + na.z() * nb.z());
		return 2.0 * Math.acos(Math.min(1.0, Math.max(0.0, dot)));
	}

	private static String corrections(List<StateCorrection> corrections) {
		if (corrections == null || corrections.isEmpty()) {
			return "NONE";
		}
		return corrections.stream()
				.map(correction -> correction.reason().name())
				.collect(Collectors.joining("|"));
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.12g", value);
	}

	private record Scenario(String name, int ticks, InputPlan input, EnvironmentPlan environment, FlightStateSnapshot initialState) {
	}

	@FunctionalInterface
	private interface InputPlan {
		DroneInput at(int tick, DroneConfig config);
	}

	@FunctionalInterface
	private interface EnvironmentPlan {
		DroneEnvironment at(int tick);
	}

	private record Row(
			String scenario,
			int tick,
			double positionDiff,
			double velocityDiff,
			double attitudeDistance,
			double angularRateDiff,
			double motorPowerDiff,
			double averageRpmDiff,
			double rotorThrustDiff,
			String playableCorrections,
			String simulationCorrections
	) {
		private static Row from(String scenario, int tick, FlightStepResult playable, FlightStepResult simulation) {
			FlightStateSnapshot playableState = playable.nextState();
			FlightStateSnapshot simulationState = simulation.nextState();
			return new Row(
					scenario,
					tick,
					vectorDistance(playableState.positionWorldMeters(), simulationState.positionWorldMeters()),
					vectorDistance(playableState.velocityWorldMetersPerSecond(), simulationState.velocityWorldMetersPerSecond()),
					quaternionDistance(playableState.attitude(), simulationState.attitude()),
					vectorDistance(playableState.angularVelocityBodyRadiansPerSecond(), simulationState.angularVelocityBodyRadiansPerSecond()),
					Math.abs(playable.actuatorOutput().averageMotorPower() - simulation.actuatorOutput().averageMotorPower()),
					Math.abs(playable.actuatorOutput().averageMotorRpm() - simulation.actuatorOutput().averageMotorRpm()),
					Math.abs(playable.actuatorOutput().averageRotorThrustNewtons() - simulation.actuatorOutput().averageRotorThrustNewtons()),
					corrections(playable.stateCorrections()),
					corrections(simulation.stateCorrections())
			);
		}

		private boolean exceedsThreshold() {
			return positionDiff > POSITION_THRESHOLD_METERS
					|| velocityDiff > VELOCITY_THRESHOLD_METERS_PER_SECOND
					|| attitudeDistance > ATTITUDE_THRESHOLD_RADIANS
					|| angularRateDiff > ANGULAR_RATE_THRESHOLD_RADIANS_PER_SECOND
					|| motorPowerDiff > MOTOR_POWER_THRESHOLD
					|| averageRpmDiff > RPM_THRESHOLD
					|| rotorThrustDiff > THRUST_THRESHOLD_NEWTONS;
		}

		private String toCsv(int firstThresholdTick) {
			return String.join(",",
					scenario,
					Integer.toString(tick),
					number(DT_SECONDS),
					number(positionDiff),
					number(velocityDiff),
					number(attitudeDistance),
					number(angularRateDiff),
					number(motorPowerDiff),
					number(averageRpmDiff),
					number(rotorThrustDiff),
					playableCorrections,
					simulationCorrections,
					Integer.toString(firstThresholdTick)
			);
		}
	}
}
