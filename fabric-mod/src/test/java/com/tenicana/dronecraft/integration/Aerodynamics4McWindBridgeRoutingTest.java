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

	@Test
	void bridgeTracksCurrentGameplaySampleContractWithoutL2SolverDependency() throws IOException {
		String source = Files.readString(bridgeSource(), StandardCharsets.UTF_8);

		assertTrue(source.contains("sampleClass.getMethod(\"hasFlow\")"),
				"bridge should keep A4MC's flow availability gate");
		assertTrue(source.contains("sampleClass.getMethod(\"isTrustedForGameplay\")"),
				"bridge should keep A4MC's server-authority gate");
		assertTrue(source.contains("sampleClass.getMethod(\"meanVelocityVector\")"),
				"bridge should bind the gameplay mean wind vector");
		assertTrue(source.contains("sampleClass.getMethod(\"effectiveVelocityVector\")"),
				"bridge should bind the bounded gameplay effective wind vector");
		assertTrue(source.contains("methodOrNull(sampleClass, \"gustVelocityVector\")"),
				"bridge should use A4MC's explicit gameplay gust vector when present");
		assertTrue(source.contains("sampleClass.getMethod(\"turbulenceIntensity\")"),
				"bridge should preserve A4MC turbulence for the core turbulence floor");
		assertTrue(source.contains("sampleClass.getMethod(\"windShearMagnitudePerBlock\")"),
				"bridge should preserve A4MC shear diagnostics");
		assertTrue(source.contains("sampleClass.getMethod(\"shelterFactor\")"),
				"bridge should preserve A4MC local shelter diagnostics");
		assertTrue(source.contains("sampleClass.getMethod(\"updraftMetersPerSecond\")"),
				"bridge should preserve A4MC updraft diagnostics");
		assertTrue(source.contains("sampleClass.getMethod(\"temperatureKelvin\")"),
				"bridge should preserve A4MC source temperature telemetry");
		assertTrue(source.contains("sampleClass.getMethod(\"humidity\")"),
				"bridge should preserve A4MC source humidity telemetry");
		assertTrue(source.contains("sampleClass.getMethod(\"confidence\")"),
				"bridge should preserve A4MC source confidence");
		assertTrue(source.contains("methodOrNull(sampleClass, \"pressure\")"),
				"bridge should preserve A4MC pressure anomaly/proxy when available");
		assertTrue(source.contains("methodOrNull(sampleClass, \"hasLocalL2Modifier\")"),
				"bridge should track trusted local L2 gameplay modifiers without changing policy");
		assertTrue(source.contains("methodOrNull(sampleClass, \"sourceLevel\")"),
				"bridge should keep A4MC source level telemetry");
		assertTrue(source.contains("methodOrNull(sampleClass, \"authority\")"),
				"bridge should keep A4MC source authority telemetry");
		assertTrue(source.contains("methodOrNull(sampleClass, \"l1Epoch\")"),
				"bridge should keep A4MC L1 freshness telemetry");
		assertTrue(source.contains("methodOrNull(sampleClass, \"worldDeltaEpoch\")"),
				"bridge should keep A4MC world-delta freshness telemetry");
		assertTrue(source.contains("methodOrNull(sampleClass, \"l2Epoch\")"),
				"bridge should keep A4MC L2 freshness telemetry");
		assertTrue(source.contains("methodOrNull(sampleClass, \"ablStability\")"),
				"bridge should preserve A4MC boundary-layer stability diagnostics");
		assertTrue(source.contains("methodOrNull(sampleClass, \"ablMixingStrength\")"),
				"bridge should preserve A4MC boundary-layer mixing diagnostics");
		assertFalse(source.contains("AeroL2Request"),
				"gameplay wind bridge should not couple server flight dynamics directly to A4MC's L2 solver API");
		assertFalse(source.contains("AeroL2Result"),
				"gameplay wind bridge should not require A4MC L2 result classes");
		assertFalse(source.contains("AeroL2ForceMoment"),
				"gameplay wind bridge should leave L2 force/moment coupling for a separate bounded path");
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
