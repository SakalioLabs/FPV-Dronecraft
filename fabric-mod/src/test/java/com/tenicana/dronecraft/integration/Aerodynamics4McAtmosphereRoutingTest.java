package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class Aerodynamics4McAtmosphereRoutingTest {
	@Test
	void entitySamplesOneCompactThermalPointForBothEnvironmentPaths() throws IOException {
		String source = Files.readString(droneEntitySource(), StandardCharsets.UTF_8);
		String advanced = between(source, "private DroneEnvironment sampleEnvironment()", "private DroneEnvironment sampleActiveEnvironment");
		String stageOne = between(source, "private DroneEnvironment samplePlayableStageOneEnvironment()", "private Aerodynamics4McAtmosphereBridge.AtmosphereSample sampleAerodynamicsAtmosphere");
		String bridge = between(source, "private Aerodynamics4McAtmosphereBridge.AtmosphereSample sampleAerodynamicsAtmosphere", "private static double atmosphereSourceQuality");
		String rotorSampling = between(source, "private PrecipitationWetness samplePrecipitationWetness", "private DroneWakeAirflow sampleDroneWakeAirflow");

		assertEquals(1, occurrences(advanced, "sampleAerodynamicsAtmosphere()"));
		assertEquals(1, occurrences(stageOne, "sampleAerodynamicsAtmosphere()"));
		assertEquals(1, occurrences(bridge, "Aerodynamics4McAtmosphereBridge.sampleGameplay"));
		assertTrue(bridge.contains("environmentOverride.windEnabled()"), "an explicit wind override must suppress A4MC thermal adoption");
		assertFalse(bridge.contains("entityPhysicsPosition()"), "the absent-mod path must not allocate a simulation Vec3");
		assertFalse(rotorSampling.contains("Aerodynamics4McAtmosphereBridge"), "thermal sampling must not expand into per-rotor probes");
		assertTrue(stageOne.contains("effectiveAmbientTemperature"), "the default playable path must receive adopted source temperature");
		assertTrue(stageOne.contains("adoptedSourceHumidity"), "the default playable path must receive quality-gated source humidity");
	}

	@Test
	void aerodynamics4McRemainsAnOptionalSuggestedMod() throws IOException {
		String manifest = Files.readString(fabricManifest(), StandardCharsets.UTF_8);

		assertTrue(manifest.contains("\"suggests\""));
		assertTrue(manifest.contains("\"aerodynamics4mc\": \"*\""));
		String depends = between(manifest, "\"depends\"", "\"suggests\"");
		assertFalse(depends.contains("aerodynamics4mc"), "A4MC must not become a hard dependency");
	}

	private static int occurrences(String text, String needle) {
		int count = 0;
		for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) {
			count++;
		}
		return count;
	}

	private static String between(String text, String start, String end) {
		int startIndex = text.indexOf(start);
		int endIndex = text.indexOf(end, startIndex + start.length());
		if (startIndex < 0 || endIndex < 0) {
			fail("Cannot locate source range " + start + " .. " + end);
		}
		return text.substring(startIndex, endIndex);
	}

	private static Path droneEntitySource() {
		return locate("fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java");
	}

	private static Path fabricManifest() {
		return locate("fabric-mod/src/main/resources/fabric.mod.json");
	}

	private static Path locate(String relativePath) {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path candidate = current.resolve(relativePath);
			if (Files.exists(candidate)) {
				return candidate;
			}
			current = current.getParent();
		}
		fail("Cannot locate " + relativePath);
		return Path.of(".");
	}
}
