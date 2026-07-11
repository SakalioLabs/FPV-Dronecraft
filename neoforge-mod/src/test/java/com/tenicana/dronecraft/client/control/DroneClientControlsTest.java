package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DroneClientControlsTest {
	@Test
	void gamepadInputRequiresDroneControlAuthority() {
		assertFalse(DroneControlAuthority.shouldUseGamepadInput(true, false, false, false));
		assertFalse(DroneControlAuthority.shouldUseGamepadInput(true, false, true, false));
		assertFalse(DroneControlAuthority.shouldUseGamepadInput(false, true, false, false));

		assertTrue(DroneControlAuthority.shouldUseGamepadInput(true, true, false, false));
		assertTrue(DroneControlAuthority.shouldUseGamepadInput(true, false, true, true));
	}

	@Test
	void controlAuthorityComesFromControllerOrLinkedVirtualController() {
		assertFalse(DroneControlAuthority.hasControlAuthority(false, false, false));
		assertFalse(DroneControlAuthority.hasControlAuthority(false, true, false));

		assertTrue(DroneControlAuthority.hasControlAuthority(true, false, false));
		assertTrue(DroneControlAuthority.hasControlAuthority(false, true, true));
	}

	@Test
	void neoForgeKeyMappingEventRegistersOneCategoryAndAllSeventeenMappings() throws IOException {
		String source = Files.readString(droneClientControlsSource(), StandardCharsets.UTF_8);
		String clientSource = Files.readString(fpvDronecraftClientSource(), StandardCharsets.UTF_8);
		String registration = source.substring(
				source.indexOf("public static void registerKeyMappings(RegisterKeyMappingsEvent event)"),
				source.indexOf("public static void onClientTick")
		);

		assertTrue(
				source.contains("import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;"),
				"key mappings must be exposed through NeoForge's registration event"
		);
		assertEquals(1, countOccurrences(registration, "event.registerCategory("), "the controls category must be registered exactly once");
		assertEquals(17, countOccurrences(registration, "event.register("), "all 17 control mappings must be registered exactly once");

		String[] expectedMappings = {
				"ARM",
				"FLIGHT_MODE",
				"VIRTUAL_CONTROLLER",
				"FPV_VIEW",
				"HUD_TOGGLE",
				"GAMEPAD_TOGGLE",
				"CONFIG_RELOAD",
				"CONTROLLER_SETTINGS",
				"PITCH_FORWARD",
				"PITCH_BACK",
				"ROLL_LEFT",
				"ROLL_RIGHT",
				"THROTTLE_UP",
				"THROTTLE_DOWN",
				"YAW_LEFT",
				"YAW_RIGHT",
				"THROTTLE_CALIBRATE"
		};
		for (String mapping : expectedMappings) {
			assertTrue(registration.contains("event.register(" + mapping + ");"), mapping + " must be registered through RegisterKeyMappingsEvent");
		}

		assertTrue(
				clientSource.contains("RegisterKeyMappingsEvent event")
						&& clientSource.contains("DroneClientControls.registerKeyMappings(event);"),
				"the NeoForge client entry point must delegate key registration to the controls module"
		);
	}

	private static int countOccurrences(String source, String needle) {
		int count = 0;
		int offset = 0;
		while ((offset = source.indexOf(needle, offset)) >= 0) {
			count++;
			offset += needle.length();
		}
		return count;
	}

	private static Path droneClientControlsSource() {
		return locateSource("com/tenicana/dronecraft/client/control/DroneClientControls.java");
	}

	private static Path fpvDronecraftClientSource() {
		return locateSource("com/tenicana/dronecraft/client/FpvDronecraftClient.java");
	}

	private static Path locateSource(String relativePath) {
		Path current = Path.of("").toAbsolutePath();
		for (int i = 0; i < 8 && current != null; i++) {
			Path direct = current.resolve("src/main/java").resolve(relativePath);
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("neoforge-mod/src/main/java").resolve(relativePath);
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate " + relativePath);
		return Path.of(relativePath);
	}
}
