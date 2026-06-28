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

class Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReportTest {
	@Test
	void auditBuildsStableAcceptanceBudgetBlockerScenarios() {
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Acceptance-Budget-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not enable runtime coupling"));
		assertEquals(138, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(30, audit.scenarioMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerSummary current =
				find(audit.scenarios(), "current_handoff_and_budget_blocked").summary();
		assertFalse(current.acceptanceBudgetGateReady());
		assertEquals(4, current.blockerCount());
		assertTrue(current.acceptanceHandoffBlocker());
		assertTrue(current.validationBudgetBlocker());
		assertTrue(current.hoverAcceptanceHandoffBlocker());
		assertTrue(current.cruiseAcceptanceHandoffBlocker());
		assertTrue(current.hoverValidationBudgetBlocker());
		assertTrue(current.cruiseValidationBudgetBlocker());
		assertEquals(2, current.acceptanceHandoffBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", current.dominantAcceptanceHandoffMessage());
		assertEquals(2, current.validationBudgetBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", current.dominantValidationBudgetMessage());
		assertTrue(current.runtimeCouplingStillClosed());
		assertTrue(current.gameplayAutoApplyStillClosed());
		assertFalse(current.hoverAcceptanceHandoffReady());
		assertFalse(current.cruiseAcceptanceHandoffReady());
		assertEquals(0, current.readyHandoffCount());
		assertEquals(2, current.expectedHandoffCount());
		assertFalse(current.hoverValidationBudgetCandidate());
		assertFalse(current.cruiseValidationBudgetCandidate());
		assertEquals(0, current.validationBudgetCandidateCount());
		assertEquals(2, current.expectedValidationBudgetGroupCount());
		assertEquals(0.0, current.maxForceErrorRatio(), 1.0e-12);
		assertEquals(0.0, current.maxForceErrorNewtons(), 1.0e-12);
		assertEquals(0.0, current.maxMomentErrorNewtonMeters(), 1.0e-12);
		assertEquals(0.0, current.maxCenterOfForceErrorMeters(), 1.0e-12);
		assertEquals("audit-only-powered-source-acceptance-budget-gate", current.sourceRuntimeInfo());
		assertEquals("complete-hover-and-cruise-powered-source-acceptance-handoffs",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("powered-source-acceptance-budget-blocked", current.message());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerSummary handoffOnly =
				find(audit.scenarios(), "handoff_ready_budget_blocked").summary();
		assertEquals(2, handoffOnly.blockerCount());
		assertFalse(handoffOnly.acceptanceHandoffBlocker());
		assertTrue(handoffOnly.validationBudgetBlocker());
		assertEquals(0, handoffOnly.acceptanceHandoffBlockerMessageCount());
		assertEquals("none", handoffOnly.dominantAcceptanceHandoffMessage());
		assertEquals(2, handoffOnly.validationBudgetBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", handoffOnly.dominantValidationBudgetMessage());
		assertEquals("produce-hover-and-cruise-validation-error-budget-candidates",
				handoffOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerSummary budgetOnly =
				find(audit.scenarios(), "budget_ready_handoff_blocked").summary();
		assertEquals(2, budgetOnly.blockerCount());
		assertTrue(budgetOnly.acceptanceHandoffBlocker());
		assertFalse(budgetOnly.validationBudgetBlocker());
		assertEquals(2, budgetOnly.acceptanceHandoffBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", budgetOnly.dominantAcceptanceHandoffMessage());
		assertEquals(0, budgetOnly.validationBudgetBlockerMessageCount());
		assertEquals("none", budgetOnly.dominantValidationBudgetMessage());
		assertEquals("complete-hover-and-cruise-powered-source-acceptance-handoffs",
				budgetOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerSummary ready =
				find(audit.scenarios(), "handoff_ready_budget_ready").summary();
		assertTrue(ready.acceptanceBudgetGateReady());
		assertEquals(0, ready.blockerCount());
		assertFalse(ready.acceptanceHandoffBlocker());
		assertFalse(ready.validationBudgetBlocker());
		assertEquals(0, ready.acceptanceHandoffBlockerMessageCount());
		assertEquals("none", ready.dominantAcceptanceHandoffMessage());
		assertEquals(0, ready.validationBudgetBlockerMessageCount());
		assertEquals("none", ready.dominantValidationBudgetMessage());
		assertTrue(ready.runtimeCouplingStillClosed());
		assertTrue(ready.gameplayAutoApplyStillClosed());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-acceptance-budget-clear", ready.message());
		assertEquals("powered-source-acceptance-evidence-ready-for-final-coupling-review",
				ready.nextRequiredAction());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(4, audit.extrema().maxBlockerCount());
		assertEquals(2, audit.extrema().acceptanceHandoffBlockerScenarioCount());
		assertEquals(2, audit.extrema().validationBudgetBlockerScenarioCount());
		assertEquals(2, audit.extrema().hoverAcceptanceHandoffBlockerScenarioCount());
		assertEquals(2, audit.extrema().cruiseAcceptanceHandoffBlockerScenarioCount());
		assertEquals(2, audit.extrema().hoverValidationBudgetBlockerScenarioCount());
		assertEquals(2, audit.extrema().cruiseValidationBudgetBlockerScenarioCount());
		assertEquals(2, audit.extrema().maxAcceptanceHandoffBlockerMessageCount());
		assertEquals(2, audit.extrema().maxValidationBudgetBlockerMessageCount());
	}

	@Test
	void reportSeparatesHandoffFromValidationBudgetBlockers() {
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit gateAudit =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(getClass().getClassLoader());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerSummary handoffOnly =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.report(
						findGate(gateAudit.scenarios(), "handoff_ready_budget_blocked").summary());
		assertFalse(handoffOnly.acceptanceHandoffBlocker());
		assertTrue(handoffOnly.validationBudgetBlocker());
		assertFalse(handoffOnly.hoverAcceptanceHandoffBlocker());
		assertFalse(handoffOnly.cruiseAcceptanceHandoffBlocker());
		assertTrue(handoffOnly.hoverValidationBudgetBlocker());
		assertTrue(handoffOnly.cruiseValidationBudgetBlocker());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerSummary budgetOnly =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.report(
						findGate(gateAudit.scenarios(), "budget_ready_handoff_blocked").summary());
		assertTrue(budgetOnly.acceptanceHandoffBlocker());
		assertFalse(budgetOnly.validationBudgetBlocker());
		assertTrue(budgetOnly.hoverAcceptanceHandoffBlocker());
		assertTrue(budgetOnly.cruiseAcceptanceHandoffBlocker());
		assertFalse(budgetOnly.hoverValidationBudgetBlocker());
		assertFalse(budgetOnly.cruiseValidationBudgetBlocker());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.audit(
						(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.report(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve(
				"docs/data/a4mc_l2_powered_source_acceptance_budget_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_blocker_report_summary,all_scenarios,max_blocker_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_blocker_report_scenario,current_handoff_and_budget_blocked,blocker_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_blocker_report_scenario,current_handoff_and_budget_blocked,dominant_acceptance_handoff_message,powered-source-api-surface-missing,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_blocker_report_scenario,handoff_ready_budget_blocked,dominant_validation_budget_message,powered-source-api-surface-missing,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_blocker_report_scenario,handoff_ready_budget_ready,acceptance_budget_gate_ready,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerScenario find(
			List<Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport.PoweredSourceAcceptanceBudgetBlockerScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetScenario findGate(
			List<Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetScenario> scenarios,
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
							"docs/data/a4mc_l2_powered_source_acceptance_budget_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
