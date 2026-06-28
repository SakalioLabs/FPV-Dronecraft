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

class Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReportTest {
	@Test
	void auditBuildsStableSurfaceWakeReadinessBlockerScenarios() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Readiness-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not invoke A4MC"));
		assertEquals(149, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(25, audit.scenarioMetricCount());
		assertEquals(17, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary current =
				find(audit.scenarios(), "current_api_unavailable_tick_rate_only").summary();
		assertFalse(current.liveValidationRunAllowed());
		assertEquals(4, current.blockerCount());
		assertTrue(current.poweredSourceApiBlocker());
		assertTrue(current.transientProbeApiBlocker());
		assertFalse(current.targetCoverageBlocker());
		assertTrue(current.targetProbeApiBlocker());
		assertTrue(current.temporalResolutionBlocker());
		assertFalse(current.invalidTargetBlocker());
		assertFalse(current.unexpectedTargetBlocker());
		assertFalse(current.runtimeCouplingLeakBlocker());
		assertEquals(128, current.expectedTargetCount());
		assertEquals(128, current.observedTargetCount());
		assertEquals(64, current.groundTargetCount());
		assertEquals(64, current.ceilingTargetCount());
		assertEquals(0, current.transientProbeApiAvailableTargetCount());
		assertEquals(0, current.runtimeCouplingAllowedTargetCount());
		assertEquals(0, current.missingTargetCount());
		assertEquals(0.05, current.appliedMaxSamplePeriodSeconds(), 1.0e-12);
		assertEquals(0.00015885419296393082, current.requiredMaxSamplePeriodSeconds(), 1.0e-16);
		assertEquals("wait-for-powered-source-api-before-surface-wake-validation",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("hover-surface-wake-readiness-blocked", current.message());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary ready =
				find(audit.scenarios(), "powered_and_probe_api_available_substepped").summary();
		assertTrue(ready.liveValidationRunAllowed());
		assertEquals(0, ready.blockerCount());
		assertEquals(128, ready.transientProbeApiAvailableTargetCount());
		assertEquals("hover-surface-wake-readiness-ready-for-live-validation",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("hover-surface-wake-readiness-clear", ready.message());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary missing =
				find(audit.scenarios(), "api_available_one_target_missing").summary();
		assertTrue(missing.targetCoverageBlocker());
		assertFalse(missing.targetProbeApiBlocker());
		assertEquals(1, missing.blockerCount());
		assertEquals(1, missing.missingTargetCount());
		assertEquals("restore-complete-hover-surface-wake-target-set",
				missing.nextRequiredAction());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary underresolved =
				find(audit.scenarios(), "api_available_tick_rate_underresolved").summary();
		assertTrue(underresolved.temporalResolutionBlocker());
		assertEquals(1, underresolved.blockerCount());
		assertEquals("substep-hover-surface-wake-validation-to-fast-arrival-window",
				underresolved.nextRequiredAction());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary targetApiUnavailable =
				find(audit.scenarios(), "powered_api_available_probe_target_unavailable").summary();
		assertTrue(targetApiUnavailable.targetProbeApiBlocker());
		assertEquals(1, targetApiUnavailable.blockerCount());
		assertEquals("make-every-surface-wake-target-probe-api-backed",
				targetApiUnavailable.nextRequiredAction());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(4, audit.extrema().maxBlockerCount());
		assertEquals(1, audit.extrema().poweredSourceApiBlockerScenarioCount());
		assertEquals(1, audit.extrema().transientProbeApiBlockerScenarioCount());
		assertEquals(1, audit.extrema().targetCoverageBlockerScenarioCount());
		assertEquals(2, audit.extrema().targetProbeApiBlockerScenarioCount());
		assertEquals(2, audit.extrema().temporalResolutionBlockerScenarioCount());
		assertEquals(0, audit.extrema().invalidTargetBlockerScenarioCount());
		assertEquals(0, audit.extrema().unexpectedTargetBlockerScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingLeakBlockerScenarioCount());
		assertEquals(1, audit.extrema().maxMissingTargetCount());
	}

	@Test
	void reportSeparatesReadinessBlockerClasses() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessAudit readinessAudit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.audit();

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary missing =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.report(
						findReadiness(readinessAudit.scenarios(), "api_available_one_target_missing").summary());
		assertTrue(missing.targetCoverageBlocker());
		assertFalse(missing.targetProbeApiBlocker());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary targetApiUnavailable =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.report(
						findReadiness(readinessAudit.scenarios(),
								"powered_api_available_probe_target_unavailable").summary());
		assertFalse(targetApiUnavailable.targetCoverageBlocker());
		assertTrue(targetApiUnavailable.targetProbeApiBlocker());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerSummary underresolved =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.report(
						findReadiness(readinessAudit.scenarios(), "api_available_tick_rate_underresolved").summary());
		assertFalse(underresolved.poweredSourceApiBlocker());
		assertFalse(underresolved.transientProbeApiBlocker());
		assertTrue(underresolved.temporalResolutionBlocker());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.report(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_readiness_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_readiness_blocker_report_summary,all_scenarios,max_blocker_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_readiness_blocker_report_scenario,current_api_unavailable_tick_rate_only,next_required_action,wait-for-powered-source-api-before-surface-wake-validation,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_readiness_blocker_report_scenario,api_available_tick_rate_underresolved,temporal_resolution_blocker,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_readiness_blocker_report_scenario,powered_and_probe_api_available_substepped,live_validation_run_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerScenario find(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport.PoweredHoverSurfaceWakeReadinessBlockerScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessScenario findReadiness(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessScenario> scenarios,
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
							"docs/data/a4mc_l2_powered_hover_surface_wake_readiness_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
