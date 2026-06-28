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

class Aerodynamics4McL2PoweredSourceAcceptanceBudgetGateTest {
	@Test
	void auditBuildsStableAcceptanceBudgetScenarios() {
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Acceptance-Budget-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("never enables runtime coupling"));
		assertEquals(113, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(24, audit.scenarioMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary current =
				find(audit.scenarios(), "current_handoff_and_budget_blocked").summary();
		assertFalse(current.hoverAcceptanceHandoffReady());
		assertFalse(current.cruiseAcceptanceHandoffReady());
		assertEquals(2, current.expectedHandoffCount());
		assertEquals(2, current.handoffCount());
		assertEquals(0, current.readyHandoffCount());
		assertEquals(2, current.blockedHandoffCount());
		assertEquals(2, current.expectedValidationBudgetGroupCount());
		assertEquals(2, current.validationBudgetGroupCount());
		assertEquals(0, current.validationBudgetCandidateCount());
		assertEquals(2, current.blockedValidationBudgetGroupCount());
		assertFalse(current.hoverValidationBudgetCandidate());
		assertFalse(current.cruiseValidationBudgetCandidate());
		assertFalse(current.allAcceptanceHandoffsReady());
		assertFalse(current.allValidationBudgetsReady());
		assertFalse(current.acceptanceBudgetGateReady());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals(0.0, current.maxForceErrorRatio(), 1.0e-12);
		assertEquals(0.0, current.maxForceErrorNewtons(), 1.0e-12);
		assertEquals(0.0, current.maxMomentErrorNewtonMeters(), 1.0e-12);
		assertEquals(0.0, current.maxCenterOfForceErrorMeters(), 1.0e-12);
		assertEquals("BLOCKED", current.status());
		assertEquals("acceptance-handoffs-not-ready", current.message());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary ready =
				find(audit.scenarios(), "handoff_ready_budget_ready").summary();
		assertTrue(ready.allAcceptanceHandoffsReady());
		assertTrue(ready.allValidationBudgetsReady());
		assertEquals(2, ready.readyHandoffCount());
		assertEquals(0, ready.blockedHandoffCount());
		assertEquals(2, ready.validationBudgetCandidateCount());
		assertEquals(0, ready.blockedValidationBudgetGroupCount());
		assertTrue(ready.acceptanceBudgetGateReady());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("READY", ready.status());
		assertEquals("acceptance-budget-gate-ready", ready.message());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary handoffOnly =
				find(audit.scenarios(), "handoff_ready_budget_blocked").summary();
		assertTrue(handoffOnly.allAcceptanceHandoffsReady());
		assertFalse(handoffOnly.allValidationBudgetsReady());
		assertFalse(handoffOnly.acceptanceBudgetGateReady());
		assertEquals("validation-error-budgets-not-ready", handoffOnly.message());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary budgetOnly =
				find(audit.scenarios(), "budget_ready_handoff_blocked").summary();
		assertFalse(budgetOnly.allAcceptanceHandoffsReady());
		assertTrue(budgetOnly.allValidationBudgetsReady());
		assertFalse(budgetOnly.acceptanceBudgetGateReady());
		assertEquals("acceptance-handoffs-not-ready", budgetOnly.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(2, audit.extrema().maxReadyHandoffCount());
		assertEquals(2, audit.extrema().maxValidationBudgetCandidateCount());
		assertEquals(2, audit.extrema().maxBlockedValidationBudgetGroupCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void gateRequiresReadyHandoffsAndReadyBudgets() {
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> currentHandoffs =
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(getClass().getClassLoader()).handoffs();
		List<Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup> currentBudgets =
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit(getClass().getClassLoader()).groups();
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> readyHandoffs =
				List.of(readyHandoff("hover"), readyHandoff("cruise"));
		List<Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup> readyBudgets =
				currentBudgets.stream()
						.map(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGateTest::readyBudget)
						.toList();

		assertFalse(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate
				.gate(currentHandoffs, currentBudgets, "current").acceptanceBudgetGateReady());
		assertFalse(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate
				.gate(readyHandoffs, currentBudgets, "handoff-only").acceptanceBudgetGateReady());
		assertFalse(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate
				.gate(currentHandoffs, readyBudgets, "budget-only").acceptanceBudgetGateReady());

		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary ready =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.gate(
						readyHandoffs,
						readyBudgets,
						"both-ready"
				);
		assertTrue(ready.acceptanceBudgetGateReady());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
	}

	@Test
	void gateRejectsInvalidInputsAndDuplicateSpinStates() {
		List<Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup> currentBudgets =
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit(getClass().getClassLoader()).groups();
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> readyHandoffs =
				List.of(readyHandoff("hover"), readyHandoff("cruise"));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(null,
						Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(
						Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(),
						null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.gate(null, currentBudgets, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.gate(readyHandoffs, null, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.gate(
						readyHandoffs,
						currentBudgets,
						" "));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.gate(
						List.of(readyHandoff("hover"), readyHandoff("hover")),
						currentBudgets,
						"duplicate-handoff"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.gate(
						readyHandoffs,
						List.of(readyBudget(currentBudgets.get(0)), readyBudget(currentBudgets.get(0))),
						"duplicate-budget"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve(
				"docs/data/a4mc_l2_powered_source_acceptance_budget_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_gate_summary,all_scenarios,ready_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_gate_summary,all_scenarios,runtime_coupling_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_gate_scenario,current_handoff_and_budget_blocked,acceptance_budget_gate_ready,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_budget_gate_scenario,handoff_ready_budget_ready,acceptance_budget_gate_ready,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetScenario find(
			List<Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary readyHandoff(
			String spinState
	) {
		boolean hover = "hover".equals(spinState);
		return new Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary(
				spinState,
				hover ? Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID : Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID,
				hover ? Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID : Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID,
				4,
				4,
				4,
				4,
				4,
				4,
				0,
				0,
				0,
				0,
				0.0,
				0.0,
				true,
				true,
				true,
				"READY",
				"acceptance-handoff-ready"
		);
	}

	private static Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup readyBudget(
			Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup budget
	) {
		return new Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup(
				budget.spinState(),
				budget.validationPacketId(),
				budget.acceptanceGatePacketId(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				0,
				0,
				budget.expectedPresetCount(),
				0,
				0,
				true,
				true,
				true,
				true,
				0.0,
				0.0,
				0.0,
				0.0,
				budget.meanTargetForceMagnitudeNewtons(),
				budget.meanTargetMomentMagnitudeNewtonMeters(),
				true,
				"READY",
				"powered-source-validation-error-budget-ready"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_acceptance_budget_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
