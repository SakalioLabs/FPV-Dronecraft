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

class Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReportTest {
	@Test
	void auditBuildsStableCruiseSkewWakeReadinessBlockerScenarios() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Readiness-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not invoke A4MC"));
		assertEquals(187, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(6, audit.scenarioSampleCount());
		assertEquals(27, audit.scenarioMetricCount());
		assertEquals(18, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(6, audit.scenarios().size());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary current =
				find(audit.scenarios(), "current_api_unavailable_tick_rate_only").summary();
		assertFalse(current.liveValidationRunAllowed());
		assertEquals(5, current.blockerCount());
		assertTrue(current.poweredSourceApiBlocker());
		assertTrue(current.skewWakeProbeApiBlocker());
		assertTrue(current.transientProbeApiBlocker());
		assertFalse(current.targetCoverageBlocker());
		assertTrue(current.targetProbeApiBlocker());
		assertTrue(current.temporalResolutionBlocker());
		assertFalse(current.invalidTargetBlocker());
		assertFalse(current.unexpectedTargetBlocker());
		assertFalse(current.runtimeCouplingLeakBlocker());
		assertEquals(192, current.expectedTargetCount());
		assertEquals(192, current.observedTargetCount());
		assertEquals(16, current.sourceTermTargetCount());
		assertEquals(64, current.centerlineSweepTargetCount());
		assertEquals(128, current.lateralSweepTargetCount());
		assertEquals(0, current.transientProbeApiAvailableTargetCount());
		assertEquals(0, current.runtimeCouplingAllowedTargetCount());
		assertEquals(0, current.missingTargetCount());
		assertEquals(0.05, current.appliedMaxSamplePeriodSeconds(), 1.0e-12);
		assertEquals(0.00019151790149419937, current.requiredMaxSamplePeriodSeconds(), 1.0e-16);
		assertEquals(5221.4440122731176, current.maxRequiredSampleRateHertz(), 1.0e-10);
		assertEquals("wait-for-powered-source-api-before-cruise-skew-wake-validation",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("cruise-skew-wake-readiness-blocked", current.message());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary ready =
				find(audit.scenarios(), "powered_and_skew_probe_api_available_substepped").summary();
		assertTrue(ready.liveValidationRunAllowed());
		assertEquals(0, ready.blockerCount());
		assertEquals(192, ready.transientProbeApiAvailableTargetCount());
		assertEquals("cruise-skew-wake-readiness-ready-for-live-validation",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("cruise-skew-wake-readiness-clear", ready.message());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary missing =
				find(audit.scenarios(), "api_available_one_target_missing").summary();
		assertTrue(missing.targetCoverageBlocker());
		assertFalse(missing.targetProbeApiBlocker());
		assertEquals(1, missing.blockerCount());
		assertEquals(1, missing.missingTargetCount());
		assertEquals("restore-complete-cruise-skew-wake-target-set",
				missing.nextRequiredAction());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary underresolved =
				find(audit.scenarios(), "api_available_tick_rate_underresolved").summary();
		assertTrue(underresolved.temporalResolutionBlocker());
		assertEquals(1, underresolved.blockerCount());
		assertEquals("substep-cruise-skew-wake-validation-to-fast-arrival-window",
				underresolved.nextRequiredAction());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary skewApiUnavailable =
				find(audit.scenarios(), "powered_api_available_skew_probe_unavailable").summary();
		assertTrue(skewApiUnavailable.skewWakeProbeApiBlocker());
		assertFalse(skewApiUnavailable.transientProbeApiBlocker());
		assertFalse(skewApiUnavailable.targetProbeApiBlocker());
		assertEquals(1, skewApiUnavailable.blockerCount());
		assertEquals("wait-for-local-cruise-skew-wake-probe-api",
				skewApiUnavailable.nextRequiredAction());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary targetApiUnavailable =
				find(audit.scenarios(), "powered_api_available_probe_target_unavailable").summary();
		assertTrue(targetApiUnavailable.targetProbeApiBlocker());
		assertEquals(1, targetApiUnavailable.blockerCount());
		assertEquals("make-every-cruise-skew-wake-target-probe-api-backed",
				targetApiUnavailable.nextRequiredAction());

		assertEquals(6, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(5, audit.extrema().blockedScenarioCount());
		assertEquals(5, audit.extrema().maxBlockerCount());
		assertEquals(1, audit.extrema().poweredSourceApiBlockerScenarioCount());
		assertEquals(2, audit.extrema().skewWakeProbeApiBlockerScenarioCount());
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
	void reportSeparatesCruiseSkewWakeReadinessBlockerClasses() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessAudit readinessAudit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.audit();

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary missing =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.report(
						findReadiness(readinessAudit.scenarios(), "api_available_one_target_missing").summary());
		assertTrue(missing.targetCoverageBlocker());
		assertFalse(missing.targetProbeApiBlocker());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary skewApiUnavailable =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.report(
						findReadiness(readinessAudit.scenarios(),
								"powered_api_available_skew_probe_unavailable").summary());
		assertTrue(skewApiUnavailable.skewWakeProbeApiBlocker());
		assertFalse(skewApiUnavailable.targetProbeApiBlocker());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerSummary targetApiUnavailable =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.report(
						findReadiness(readinessAudit.scenarios(),
								"powered_api_available_probe_target_unavailable").summary());
		assertFalse(targetApiUnavailable.skewWakeProbeApiBlocker());
		assertTrue(targetApiUnavailable.targetProbeApiBlocker());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.report(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_readiness_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_blocker_report_summary,all_scenarios,max_blocker_count,5,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_blocker_report_scenario,current_api_unavailable_tick_rate_only,next_required_action,wait-for-powered-source-api-before-cruise-skew-wake-validation,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_blocker_report_scenario,powered_api_available_skew_probe_unavailable,skew_wake_probe_api_blocker,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_blocker_report_scenario,powered_and_skew_probe_api_available_substepped,live_validation_run_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerScenario find(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport.PoweredCruiseSkewWakeReadinessBlockerScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessScenario findReadiness(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessScenario> scenarios,
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
							"docs/data/a4mc_l2_powered_cruise_skew_wake_readiness_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
