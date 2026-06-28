package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceApiSurfaceAuditTest {
	@Test
	void auditTracksCurrentA4mcL2SurfaceWithoutPoweredSourceExtensionPoints() {
		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceAudit audit =
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-API-Surface-Audit-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("never fabricates rotor source output"));
		assertEquals(101, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(3, audit.scenarioSampleCount());
		assertEquals(28, audit.scenarioMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(3, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary current =
				find(audit.scenarios(), "current_a4mc_l2_surface").summary();
		assertTrue(current.l2WindApiAvailable());
		assertTrue(current.l2RequestBuilderAvailable());
		assertTrue(current.l2RequestMaskAvailable());
		assertTrue(current.l2InitialFlowAvailable());
		assertTrue(current.l2FlowAtlasAvailable());
		assertTrue(current.l2ForceMomentAvailable());
		assertEquals(6, current.baseCapabilityCount());
		assertEquals(6, current.requiredBaseCapabilityCount());
		assertFalse(current.bodyForceSourceApiAvailable());
		assertFalse(current.porousSourceApiAvailable());
		assertFalse(current.rotorSourceTermApiAvailable());
		assertFalse(current.sourceTermEnvelopeApiAvailable());
		assertFalse(current.sourceTermRuntimeResultAvailable());
		assertEquals(0, current.poweredSourceApiSurfaceCount());
		assertEquals(5, current.requiredPoweredSourceApiSurfaceCount());
		assertFalse(current.poweredSourceApiReady());
		assertFalse(current.poweredSourceExecutorWiringAllowed());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("none", current.missingBaseCapabilityList());
		assertTrue(current.missingPoweredSourceApiList().contains("body_force_source_api"));
		assertEquals("none", current.discoveredRequestExtensionMethods());
		assertEquals("none", current.discoveredResultExtensionMethods());
		assertEquals("62a52a584e9c65246e50226b29a1f0449e43995e", current.upstreamReferenceCommit());
		assertEquals("BLOCKED", current.status());
		assertEquals("powered-source-api-surface-missing", current.message());

		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary missing =
				find(audit.scenarios(), "missing_a4mc_l2_surface").summary();
		assertEquals(0, missing.baseCapabilityCount());
		assertEquals(0, missing.poweredSourceApiSurfaceCount());
		assertFalse(missing.poweredSourceApiReady());
		assertTrue(missing.missingBaseCapabilityList().contains("run_l2"));
		assertEquals("l2-base-api-surface-missing", missing.message());

		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary ready =
				find(audit.scenarios(), "synthetic_powered_source_api_ready").summary();
		assertEquals(6, ready.baseCapabilityCount());
		assertEquals(5, ready.poweredSourceApiSurfaceCount());
		assertTrue(ready.poweredSourceApiReady());
		assertTrue(ready.poweredSourceExecutorWiringAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("none", ready.missingBaseCapabilityList());
		assertEquals("none", ready.missingPoweredSourceApiList());
		assertTrue(ready.discoveredRequestExtensionMethods().contains("bodyForceSource"));
		assertTrue(ready.discoveredResultExtensionMethods().contains("poweredSourceForceMomentDelta"));
		assertEquals("READY", ready.status());
		assertEquals("powered-source-api-surface-ready", ready.message());

		assertEquals(3, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(2, audit.extrema().blockedScenarioCount());
		assertEquals(6, audit.extrema().maxBaseCapabilityCount());
		assertEquals(5, audit.extrema().maxPoweredSourceApiSurfaceCount());
		assertEquals(6, audit.extrema().maxMissingBaseCapabilityCount());
		assertEquals(5, audit.extrema().maxMissingPoweredSourceApiCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(1, audit.extrema().executorWiringAllowedCount());
	}

	@Test
	void summaryRejectsInvalidInputs() {
		Aerodynamics4McL2Bridge.L2Capabilities capabilities =
				Aerodynamics4McL2Bridge.inspect(getClass().getClassLoader());

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceApiSurfaceAudit.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceApiSurfaceAudit.summary(null, List.of(), List.of(), "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceApiSurfaceAudit.summary(capabilities, null, List.of(), "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceApiSurfaceAudit.summary(capabilities, List.of(), null, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceApiSurfaceAudit.summary(capabilities, List.of(), List.of(), " "));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceAudit audit =
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_api_surface_audit_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_api_surface_audit_summary,all_scenarios,ready_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_api_surface_audit_scenario,current_a4mc_l2_surface,powered_source_api_ready,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_api_surface_audit_scenario,synthetic_powered_source_api_ready,powered_source_api_ready,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceScenario find(
			List<Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_api_surface_audit_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
