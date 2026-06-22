package com.tenicana.dronecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;

class DroneCommandsTest {
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
}
