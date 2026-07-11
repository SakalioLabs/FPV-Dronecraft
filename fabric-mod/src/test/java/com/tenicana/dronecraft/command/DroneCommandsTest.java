package com.tenicana.dronecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.sim.DroneConfig;

import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;

class DroneCommandsTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void commandTreeEnforcesGamemasterMutationBoundaries() throws Exception {
		CommandDispatcher<CommandSourceStack> dispatcher = registeredDispatcher();
		CommandSourceStack unprivileged = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
		CommandSourceStack moderator = Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR);
		CommandSourceStack gamemaster = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);

		String[] readOnlyPaths = {
				"fpvdrone status",
				"fpvdrone debug status",
				"fpvdrone environment status",
				"fpvdrone tune status"
		};
		for (String path : readOnlyPaths) {
			assertAvailableToAll(dispatcher, path, unprivileged, moderator, gamemaster);
		}

		assertAvailableToAll(dispatcher, "fpvdrone preset list", unprivileged, moderator, gamemaster);
		assertAvailableToAll(dispatcher, "fpvdrone preset racing_quad", unprivileged, moderator, gamemaster);

		String[] protectedBoundaries = {
				"fpvdrone debug trace",
				"fpvdrone debug ticklog",
				"fpvdrone debug physics",
				"fpvdrone debug mode",
				"fpvdrone debug playablepreset",
				"fpvdrone fault",
				"fpvdrone environment clear",
				"fpvdrone environment wind",
				"fpvdrone environment turbulence",
				"fpvdrone environment density",
				"fpvdrone tune reset",
				"fpvdrone tune set"
		};
		for (String path : protectedBoundaries) {
			CommandNode<CommandSourceStack> node = commandNode(dispatcher, path);
			assertFalse(node.canUse(unprivileged), path + " must reject sources without permissions");
			assertFalse(node.canUse(moderator), path + " must reject moderator-level sources");
			assertTrue(node.canUse(gamemaster), path + " must accept gamemaster-level sources");
		}
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
			Path child = current.resolve("fabric-mod/src/main/java/com/tenicana/dronecraft/command/DroneCommands.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		throw new IllegalStateException("Cannot locate DroneCommands.java");
	}

	private static CommandDispatcher<CommandSourceStack> registeredDispatcher() throws Exception {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		Method register = DroneCommands.class.getDeclaredMethod("register", CommandDispatcher.class);
		register.setAccessible(true);
		register.invoke(null, dispatcher);
		return dispatcher;
	}

	private static void assertAvailableToAll(
			CommandDispatcher<CommandSourceStack> dispatcher,
			String path,
			CommandSourceStack unprivileged,
			CommandSourceStack moderator,
			CommandSourceStack gamemaster
	) {
		CommandNode<CommandSourceStack> current = dispatcher.getRoot();
		StringBuilder resolvedPath = new StringBuilder();
		for (String segment : path.split(" ")) {
			current = current.getChild(segment);
			assertNotNull(current, "Missing command node: " + path);
			if (!resolvedPath.isEmpty()) {
				resolvedPath.append(' ');
			}
			resolvedPath.append(segment);
			String nodePath = resolvedPath.toString();
			assertTrue(current.canUse(unprivileged), nodePath + " must remain available without permissions");
			assertTrue(current.canUse(moderator), nodePath + " must remain available to moderators");
			assertTrue(current.canUse(gamemaster), nodePath + " must remain available to gamemasters");
		}
	}

	private static CommandNode<CommandSourceStack> commandNode(
			CommandDispatcher<CommandSourceStack> dispatcher,
			String path
	) {
		CommandNode<CommandSourceStack> current = dispatcher.getRoot();
		for (String segment : path.split(" ")) {
			current = current.getChild(segment);
			assertNotNull(current, "Missing command node: " + path);
		}
		return current;
	}
}
