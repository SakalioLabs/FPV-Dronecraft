package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.FlightModelInitializationContext;
import com.tenicana.dronecraft.sim.flight.FlightStateSnapshot;
import com.tenicana.dronecraft.sim.flight.FlightStepContext;
import com.tenicana.dronecraft.sim.flight.FlightStepResult;

class PlayableAcroDescentRecoveryTest {
	private static final double TICK_SECONDS = 0.05;
	private static final DroneConfig CONFIG = DroneConfig.racingQuad();
	private static final PlayableFlightPreset AGILE = PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE;
	private static final PlayableFlightPreset LEGACY = PlayableFlightPreset.LEGACY_HEAVY_RACING_QUAD;

	@Test
	void actualAdapterArrestsTerminalDescentsWithColdMotorHistory() throws IOException {
		List<Recovery> recoveries = new ArrayList<>();
		recoveries.add(recover("cold_upright", initializedAdapter(0.0, -18.0, 0.0), 1L));
		recoveries.add(recover("cold_pitch20", initializedAdapter(20.0, -18.0, 0.0), 1L));
		recoveries.add(recover("cold_forward20", initializedAdapter(0.0, -18.0, 20.0), 1L));
		recoveries.add(recover("cold_pitch45", initializedAdapter(45.0, -18.0, 0.0), 1L));

		// Establish the motor state through actual hover and throttle-cut steps,
		// rather than supplying the NaN motor history used by direct-model tests.
		LegacyPlayableFlightModelAdapter falling = initializedAdapter(0.0, 0.0, 0.0);
		long tick = 1L;
		for (int hoverTick = 0; hoverTick < 30; hoverTick++) {
			step(falling, AGILE, FlightMode.ACRO, CONFIG.hoverThrottle(), tick++);
		}
		assertTrue(diagnostic(falling.diagnostics().values(), "acro_collective_thrust_to_weight") > 0.9);
		boolean reachedTerminalDescent = false;
		for (int descentTick = 0; descentTick < 160; descentTick++) {
			FlightStepResult result = step(falling, AGILE, FlightMode.ACRO, 0.0, tick++);
			if (result.nextState().velocityWorldMetersPerSecond().y() <= -17.95) {
				reachedTerminalDescent = true;
				assertEquals(0.0, diagnostic(result, "command_throttle"), 1.0e-6);
				assertEquals(0.0, diagnostic(result, "acro_collective_thrust_to_weight"), 1.0e-6);
				break;
			}
		}
		assertTrue(reachedTerminalDescent, "zero throttle did not develop the playable terminal descent");
		recoveries.add(recover("hover_then_throttle_cut", falling, tick));

		writeReport(recoveries);
		assertRecovery(recoveries.get(0), 5.5, 0.70);
		assertRecovery(recoveries.get(1), 6.0, 0.75);
		assertRecovery(recoveries.get(2), 8.0, 0.95);
		assertRecovery(recoveries.get(3), 11.0, 1.35);
		assertRecovery(recoveries.get(4), 5.5, 0.70);
	}

	@Test
	void agileAcroBypassesCommandLagButKeepsMotorRiseAndReleaseResponse() {
		LegacyPlayableFlightModelAdapter adapter = initializedAdapter(0.0, -18.0, 0.0);
		FlightStepResult first = step(adapter, AGILE, FlightMode.ACRO, 1.0, 1L);
		FlightStepResult second = step(adapter, AGILE, FlightMode.ACRO, 1.0, 2L);
		FlightStepResult released = step(adapter, AGILE, FlightMode.ACRO, 0.0, 3L);

		assertEquals(1.0, diagnostic(first, "command_throttle"), 1.0e-6);
		assertEquals(1.0, diagnostic(second, "command_throttle"), 1.0e-6);
		double firstCollective = diagnostic(first, "acro_collective_thrust_to_weight");
		double secondCollective = diagnostic(second, "acro_collective_thrust_to_weight");
		double releasedCollective = diagnostic(released, "acro_collective_thrust_to_weight");
		assertTrue(firstCollective > 0.0 && secondCollective > firstCollective,
				"direct command must retain motor spool-up: first=" + firstCollective + " second=" + secondCollective);
		assertEquals(0.0, diagnostic(released, "command_throttle"), 1.0e-6);
		assertTrue(releasedCollective > 0.0 && releasedCollective < secondCollective,
				"zero command must retain motor rundown: released=" + releasedCollective + " previous=" + secondCollective);
	}

	@Test
	void legacyAcroAndAssistedModesPreserveTheirCommandFiltering() {
		for (FlightMode mode : FlightMode.values()) {
			for (PlayableFlightPreset preset : PlayableFlightPreset.values()) {
				if (mode == FlightMode.ACRO && preset == AGILE) {
					continue;
				}
				LegacyPlayableFlightModelAdapter adapter = initializedAdapter(0.0, 0.0, 0.0, mode);
				FlightStepResult first = step(adapter, preset, mode, 1.0, 1L);
				FlightStepResult second = step(adapter, preset, mode, 1.0, 2L);
				String scenario = preset.id() + "/" + mode;
				assertEquals(0.24, diagnostic(first, "command_throttle"), 1.0e-6, scenario);
				assertTrue(diagnostic(second, "command_throttle") > diagnostic(first, "command_throttle")
						&& diagnostic(second, "command_throttle") < 1.0, scenario);
			}
		}
	}

	@Test
	void disarmingAtFullStickStopsMotorsAndIgnoresHeldCommands() {
		for (PlayableFlightPreset preset : PlayableFlightPreset.values()) {
			for (FlightMode mode : FlightMode.values()) {
				LegacyPlayableFlightModelAdapter adapter = initializedAdapter(0.0, 0.0, 0.0, mode);
				for (long tick = 1; tick <= 10; tick++) {
					step(adapter, preset, mode, 1.0, tick);
				}
				assertTrue(adapter.diagnostics().values().containsKey("motor_power"));
				assertTrue(diagnostic(adapter.diagnostics().values(), "motor_power") > 0.0);
				Vec3 lockedPosition = adapter.snapshot().positionWorldMeters();
				for (long tick = 11; tick <= 30; tick++) {
					FlightStepResult locked = adapter.step(new FlightStepContext(
							new DroneInput(1.0, 1.0, -1.0, 1.0, false, true, mode),
							adapter.snapshot(), DroneEnvironment.calm(), TICK_SECONDS, tick, CONFIG,
							Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, preset.id())));
					assertTrue(!locked.nextState().armed());
					assertEquals(mode, locked.nextState().flightMode());
					assertEquals(lockedPosition, locked.nextState().positionWorldMeters());
					assertEquals(Vec3.ZERO, locked.nextState().velocityWorldMetersPerSecond());
					assertEquals(0.0, locked.actuatorOutput().averageMotorPower());
					assertEquals(0.0, locked.actuatorOutput().averageMotorRpm());
					assertEquals(0.0, locked.actuatorOutput().averageRotorThrustNewtons());
					assertEquals(0.0, diagnostic(locked, "command_throttle"));
					assertEquals(0.0, diagnostic(locked, "acro_collective_thrust_to_weight"));
				}
				assertEquals(0.0, diagnostic(step(adapter, preset, mode, 0.0, 31L), "command_throttle"));
			}
		}
	}

	@Test
	void existingAdapterUsesThePresetConfigurationFromEachStep() {
		LegacyPlayableFlightModelAdapter adapter = initializedAdapter(0.0, 0.0, 0.0);
		FlightStepResult legacy = step(adapter, LEGACY, FlightMode.ACRO, 1.0, 1L);
		FlightStepResult agile = step(adapter, AGILE, FlightMode.ACRO, 1.0, 2L);
		FlightStepResult legacyAgain = step(adapter, LEGACY, FlightMode.ACRO, 0.0, 3L);

		assertEquals(LEGACY.id(), legacy.diagnostics().values().get("playable_preset"));
		assertEquals(0.24, diagnostic(legacy, "command_throttle"), 1.0e-6);
		assertEquals(AGILE.id(), agile.diagnostics().values().get("playable_preset"));
		assertEquals(1.0, diagnostic(agile, "command_throttle"), 1.0e-6);
		assertEquals(LEGACY.id(), legacyAgain.diagnostics().values().get("playable_preset"));
		assertEquals(0.58, diagnostic(legacyAgain, "command_throttle"), 1.0e-6);
		assertEquals(FlightMode.ACRO, legacyAgain.nextState().flightMode());
	}

	private static Recovery recover(String scenario, LegacyPlayableFlightModelAdapter adapter, long initialTick) {
		double initialY = adapter.snapshot().positionWorldMeters().y();
		double initialVy = adapter.snapshot().velocityWorldMetersPerSecond().y();
		assertTrue(initialVy <= -17.95 && initialVy >= -18.0, scenario + " initialVy=" + initialVy);
		assertEquals(0.0, diagnostic(adapter.diagnostics().values(), "acro_collective_thrust_to_weight"), 1.0e-6);
		double minimumY = initialY;
		FlightStepResult first = null;
		FlightStepResult last = null;
		int arrestTicks = -1;
		for (int recoveryTick = 0; recoveryTick < 120; recoveryTick++) {
			last = step(adapter, AGILE, FlightMode.ACRO, 1.0, initialTick + recoveryTick);
			assertTrue(last.nextState().isFinite(), scenario + " non-finite state at tick " + recoveryTick);
			assertTrue(last.nextState().armed(), scenario + " lost arming during recovery");
			if (first == null) {
				first = last;
			}
			minimumY = Math.min(minimumY, last.nextState().positionWorldMeters().y());
			if (last.nextState().velocityWorldMetersPerSecond().y() >= 0.0) {
				arrestTicks = recoveryTick + 1;
				break;
			}
		}
		return new Recovery(scenario, initialVy, arrestTicks * TICK_SECONDS, initialY - minimumY,
				diagnostic(first, "command_throttle"), diagnostic(first, "acro_collective_thrust_to_weight"),
				diagnostic(last, "acro_collective_thrust_to_weight"), last.nextState().velocityWorldMetersPerSecond().y());
	}

	private static void assertRecovery(Recovery result, double maximumLoss, double maximumSeconds) {
		assertTrue(result.arrestSeconds() > 0.0 && result.finalVy() >= 0.0, result::toString);
		assertTrue(result.heightLoss() <= maximumLoss, result::toString);
		assertTrue(result.arrestSeconds() <= maximumSeconds, result::toString);
		assertEquals(1.0, result.firstCommandThrottle(), 1.0e-6, result::toString);
		assertTrue(result.firstCollective() > 0.0 && result.firstCollective() < result.lastCollective() - 0.5,
				() -> "recovery bypassed cold-motor response: " + result);
	}

	private static LegacyPlayableFlightModelAdapter initializedAdapter(double pitchDegrees, double vy, double forward) {
		return initializedAdapter(pitchDegrees, vy, forward, FlightMode.ACRO);
	}

	private static LegacyPlayableFlightModelAdapter initializedAdapter(double pitchDegrees, double vy, double forward, FlightMode mode) {
		double halfPitch = Math.toRadians(pitchDegrees) * 0.5;
		Quaternion attitude = new Quaternion(Math.cos(halfPitch), Math.sin(halfPitch), 0.0, 0.0);
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(CONFIG,
				new FlightStateSnapshot(new Vec3(0.0, 300.0, 0.0), new Vec3(0.0, vy, forward), attitude, Vec3.ZERO, mode, true),
				DroneEnvironment.calm(), 0L));
		return adapter;
	}

	private static FlightStepResult step(LegacyPlayableFlightModelAdapter adapter, PlayableFlightPreset preset, FlightMode mode, double throttle, long tick) {
		return adapter.step(new FlightStepContext(new DroneInput(throttle, 0.0, 0.0, 0.0, true, true, mode),
				adapter.snapshot(), DroneEnvironment.calm(), TICK_SECONDS, tick, CONFIG,
				Map.of(LegacyPlayableFlightModelAdapter.OPTION_PLAYABLE_PRESET, preset.id())));
	}

	private static double diagnostic(FlightStepResult result, String key) {
		return diagnostic(result.diagnostics().values(), key);
	}

	private static double diagnostic(Map<String, String> values, String key) {
		assertTrue(values.containsKey(key), "missing diagnostic: " + key);
		return Double.parseDouble(values.get(key));
	}

	private static void writeReport(List<Recovery> results) throws IOException {
		Path report = Path.of(System.getProperty("fpvdrone.descentRecoveryReport",
				"build/flight-recovery-20261008/actual-adapter-recovery.csv"));
		Files.createDirectories(report.toAbsolutePath().getParent());
		StringBuilder csv = new StringBuilder("scenario,initial_vy_mps,arrest_seconds,height_loss_m,first_command_throttle,first_collective_tw,last_collective_tw,final_vy_mps\n");
		for (Recovery result : results) {
			csv.append(String.format(Locale.ROOT, "%s,%.6f,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
					result.scenario(), result.initialVy(), result.arrestSeconds(), result.heightLoss(), result.firstCommandThrottle(),
					result.firstCollective(), result.lastCollective(), result.finalVy()));
		}
		Files.writeString(report, csv, StandardCharsets.UTF_8);
	}

	private record Recovery(String scenario, double initialVy, double arrestSeconds, double heightLoss,
			double firstCommandThrottle, double firstCollective, double lastCollective, double finalVy) {
	}
}
