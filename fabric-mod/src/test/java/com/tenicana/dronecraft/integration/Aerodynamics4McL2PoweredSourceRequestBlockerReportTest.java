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

class Aerodynamics4McL2PoweredSourceRequestBlockerReportTest {
	@Test
	void auditBuildsStableRequestBlockerScenarios() {
		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceRequestBlockerReport.audit();

		assertEquals("A4MC-L2-Powered-Source-Request-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not build A4MC requests"));
		assertEquals(128, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(22, audit.scenarioMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerSummary current =
				find(audit.scenarios(), "current_api_unavailable_requests_blocked").summary();
		assertFalse(current.requestExecutionAllowed());
		assertEquals(5, current.blockerCount());
		assertTrue(current.poweredSourceApiBlocker());
		assertTrue(current.hoverAcceptanceGateBlocker());
		assertTrue(current.cruiseAcceptanceGateBlocker());
		assertFalse(current.requestPresenceBlocker());
		assertTrue(current.requestBuildBlocker());
		assertTrue(current.requestApiBlocker());
		assertFalse(current.invalidRequestBlocker());
		assertFalse(current.unexpectedRequestBlocker());
		assertEquals(8, current.expectedRequestCount());
		assertEquals(8, current.observedRequestCount());
		assertEquals(0, current.missingRequestCount());
		assertEquals(0, current.unexpectedRequestCount());
		assertEquals(0, current.invalidRequestCount());
		assertEquals(0, current.buildAllowedRequestCount());
		assertEquals(0, current.apiAvailableRequestCount());
		assertEquals(4, current.hoverRequestCount());
		assertEquals(4, current.cruiseRequestCount());
		assertEquals("wait-for-powered-source-api", current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("powered-source-requests-blocked", current.message());

		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerSummary ready =
				find(audit.scenarios(), "api_available_acceptance_open_requests_buildable").summary();
		assertTrue(ready.requestExecutionAllowed());
		assertEquals(0, ready.blockerCount());
		assertEquals("powered-source-requests-ready-for-live-executor", ready.nextRequiredAction());
		assertEquals("READY", ready.status());

		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerSummary missing =
				find(audit.scenarios(), "api_available_acceptance_open_one_request_missing").summary();
		assertFalse(missing.requestExecutionAllowed());
		assertEquals(1, missing.blockerCount());
		assertTrue(missing.requestPresenceBlocker());
		assertEquals("restore-complete-hover-and-cruise-request-envelope-set", missing.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerSummary buildBlocked =
				find(audit.scenarios(), "api_available_acceptance_open_build_blocked").summary();
		assertEquals(2, buildBlocked.blockerCount());
		assertTrue(buildBlocked.requestBuildBlocker());
		assertTrue(buildBlocked.requestApiBlocker());
		assertEquals("mark-every-request-api-backed-after-source-api-probe", buildBlocked.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerSummary acceptanceClosed =
				find(audit.scenarios(), "api_available_requests_buildable_acceptance_closed").summary();
		assertEquals(1, acceptanceClosed.blockerCount());
		assertTrue(acceptanceClosed.hoverAcceptanceGateBlocker());
		assertFalse(acceptanceClosed.cruiseAcceptanceGateBlocker());
		assertEquals("open-hover-and-cruise-powered-acceptance-gates", acceptanceClosed.nextRequiredAction());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(5, audit.extrema().maxBlockerCount());
		assertEquals(1, audit.extrema().poweredSourceApiBlockerScenarioCount());
		assertEquals(2, audit.extrema().hoverAcceptanceGateBlockerScenarioCount());
		assertEquals(1, audit.extrema().cruiseAcceptanceGateBlockerScenarioCount());
		assertEquals(1, audit.extrema().requestPresenceBlockerScenarioCount());
		assertEquals(2, audit.extrema().requestBuildBlockerScenarioCount());
		assertEquals(2, audit.extrema().requestApiBlockerScenarioCount());
		assertEquals(0, audit.extrema().invalidRequestBlockerScenarioCount());
		assertEquals(0, audit.extrema().unexpectedRequestBlockerScenarioCount());
	}

	@Test
	void reportSeparatesInvalidAndUnexpectedRequestEnvelopeBlockers() {
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary invalid =
				new Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary(
						true,
						true,
						true,
						8,
						8,
						4,
						4,
						8,
						8,
						1,
						0,
						1,
						true,
						true,
						true,
						true,
						false,
						"BLOCKED"
				);

		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerSummary report =
				Aerodynamics4McL2PoweredSourceRequestBlockerReport.report(invalid);

		assertFalse(report.requestExecutionAllowed());
		assertEquals(2, report.blockerCount());
		assertTrue(report.invalidRequestBlocker());
		assertTrue(report.unexpectedRequestBlocker());
		assertFalse(report.poweredSourceApiBlocker());
		assertFalse(report.requestBuildBlocker());
		assertEquals("repair-request-envelope-identity-and-physical-shape", report.nextRequiredAction());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRequestBlockerReport.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRequestBlockerReport.report(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceRequestBlockerReport.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_request_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_blocker_report_summary,all_scenarios,max_blocker_count,5,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_blocker_report_scenario,current_api_unavailable_requests_blocked,blocker_count,5,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_blocker_report_scenario,api_available_acceptance_open_requests_buildable,request_execution_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerScenario find(
			List<Aerodynamics4McL2PoweredSourceRequestBlockerReport.PoweredSourceRequestBlockerScenario> scenarios,
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
							"docs/data/a4mc_l2_powered_source_request_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
