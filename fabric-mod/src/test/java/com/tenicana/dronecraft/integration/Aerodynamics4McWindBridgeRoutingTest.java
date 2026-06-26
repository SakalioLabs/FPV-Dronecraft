package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class Aerodynamics4McWindBridgeRoutingTest {
	@Test
	void bridgeSamplesOnlyServerTrustedGameplayWind() throws IOException {
		String source = Files.readString(bridgeSource(), StandardCharsets.UTF_8);

		assertTrue(source.contains("enumConstant(policyClass, \"GAMEPLAY_SERVER_ONLY\")"),
				"server flight dynamics should request A4MC's server-trusted gameplay policy");
		assertTrue(source.contains("sampleGameplay.invoke(null, level, minecraftPosition, gameplayServerOnlyPolicy)"),
				"bridge should pass the server-only policy instead of relying on A4MC's default overload");
		assertTrue(source.contains("ServerLevel level"),
				"bridge should sample from server worlds for authoritative flight physics");
		assertFalse(source.contains("\"CLIENT_LOCAL_PREFERRED\""),
				"client-local L2 must stay out of server-authoritative drone physics");
		assertFalse(source.contains("\"VISUAL_LOCAL_FIRST\""),
				"debug/visual L2 policy must stay out of server-authoritative drone physics");
		assertFalse(source.contains("\"DIAGNOSTIC_ALL_SOURCES\""),
				"diagnostic all-source policy must stay out of server-authoritative drone physics");
		assertFalse(source.contains("\"SERVER_AGGREGATED_PREFERRED\""),
				"client compatibility sampling should not replace the gameplay server-only policy");
		assertFalse(source.contains("sampleGameplay.invoke(null, level, minecraftPosition, null)"),
				"bridge should not invoke A4MC gameplay sampling with a null/default policy");
	}

	private static Path bridgeSource() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/integration/Aerodynamics4McWindBridge.java");
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("fabric-mod/src/main/java/com/tenicana/dronecraft/integration/Aerodynamics4McWindBridge.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate Aerodynamics4McWindBridge.java");
		return Path.of(".");
	}
}
