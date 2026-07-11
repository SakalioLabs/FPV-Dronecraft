package com.tenicana.dronecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.sim.DroneConfig;

class DroneCommandsTest {
	@Test
	void commandsRegisterThroughNeoForgeEventBus() throws Exception {
		Path sourcePath = droneCommandsSource();
		String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
		String modSource = Files.readString(sourcePath.getParent().getParent().resolve("FpvDronecraftMod.java"), StandardCharsets.UTF_8);

		assertTrue(source.contains("import net.neoforged.neoforge.common.NeoForge;"));
		assertTrue(source.contains("import net.neoforged.neoforge.event.RegisterCommandsEvent;"));
		assertTrue(source.contains("NeoForge.EVENT_BUS.addListener(DroneCommands::onRegisterCommands);"));
		assertTrue(source.contains("private static void onRegisterCommands(RegisterCommandsEvent event)"));
		assertTrue(source.contains("register(event.getDispatcher());"));
		assertTrue(source.contains("DroneEntityTypes.drone()"), "NeoForge deferred entity types must be resolved through their accessor");
		assertTrue(modSource.contains("DroneCommands.initialize();"), "the mod entry point must install command event listeners");
	}

	@Test
	void tuneStatusShowsRotorBladeCount() throws Exception {
		Method method = DroneCommands.class.getDeclaredMethod("formatTuneStatus", DroneConfig.class);
		method.setAccessible(true);

		String status = (String) method.invoke(null, DroneConfig.racingQuad().withRotorBladeCount(4));

		assertTrue(status.contains("blades 4"));
	}

	@Test
	void fpvdiagCommandProvidesStartStatusStopTraceRecording() throws Exception {
		String source = Files.readString(droneCommandsSource(), StandardCharsets.UTF_8);

		assertTrue(source.contains("Commands.literal(\"fpvdiag\")"));
		assertTrue(source.contains("fpvdiagStart"));
		assertTrue(source.contains("fpvdiagStatus"));
		assertTrue(source.contains("fpvdiagStop"));
		assertTrue(source.contains("FPVDIAG_SESSIONS"));
		assertTrue(source.contains("DroneFlightTraceFiles.DIRECTORY_NAME"));
		assertTrue(source.contains("RecordingSource.FPV_DIAGNOSTIC"));
	}

	@Test
	void blackboxCaptureIsExplicitlyStartedAndStopped() throws Exception {
		String source = Files.readString(droneCommandsSource(), StandardCharsets.UTF_8);

		assertTrue(source.contains("Commands.literal(\"blackbox\")"));
		assertTrue(source.contains("Commands.literal(\"start\").executes(context -> blackboxStart"));
		assertTrue(source.contains("Commands.literal(\"stop\").executes(context -> blackboxStop"));
		assertTrue(source.contains("startRecording(RecordingSource.MANUAL"));
		assertTrue(source.contains("stopRecording(RecordingSource.MANUAL"));
		assertTrue(source.contains("RecordingSource.SCRIPTED_DIAGNOSTIC"));
	}

	@Test
	void currentCommitShaPrefersConfiguredGitShaProperty() throws Exception {
		String previous = System.getProperty("fpvdrone.git.sha");
		System.setProperty("fpvdrone.git.sha", "603CF117ABC");
		try {
			Method method = DroneCommands.class.getDeclaredMethod("currentCommitSha");
			method.setAccessible(true);

			assertEquals("603CF117ABC", method.invoke(null));
		} finally {
			if (previous == null) {
				System.clearProperty("fpvdrone.git.sha");
			} else {
				System.setProperty("fpvdrone.git.sha", previous);
			}
		}
	}

	@Test
	void flightModelModeMessageExplainsPersistedEntityPolicy() throws Exception {
		Method method = DroneCommands.class.getDeclaredMethod("formatFlightModelMode");
		method.setAccessible(true);

		DroneDebugSettings.setFlightModelMode(DroneDebugSettings.FlightModelMode.PLAYABLE);
		String playable = (String) method.invoke(null);
		assertTrue(playable.contains("playable/direct"), playable);
		assertTrue(playable.contains("affects only newly spawned drones"), playable);
		assertTrue(playable.contains("persisted flight_model_id"), playable);

		DroneDebugSettings.setFlightModelMode(DroneDebugSettings.FlightModelMode.SIMULATION);
		String simulation = (String) method.invoke(null);
		assertTrue(simulation.contains("simulation/6DOF"), simulation);
		assertTrue(simulation.contains("Existing and saved drones keep their persisted flight_model_id"), simulation);
		assertTrue(simulation.contains("legacy saves without that field use the current global default"), simulation);
		DroneDebugSettings.setFlightModelMode(DroneDebugSettings.FlightModelMode.PLAYABLE);
	}

	private static Path droneCommandsSource() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/command/DroneCommands.java");
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("neoforge-mod/src/main/java/com/tenicana/dronecraft/command/DroneCommands.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		throw new IllegalStateException("Cannot locate DroneCommands.java");
	}
}
