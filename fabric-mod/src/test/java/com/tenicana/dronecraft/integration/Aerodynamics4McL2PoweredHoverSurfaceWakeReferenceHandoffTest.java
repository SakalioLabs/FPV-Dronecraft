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

class Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoffTest {
	@Test
	void auditBuildsStableReferenceHandoffScenarios() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Reference-Handoff-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("never enables runtime coupling"));
		assertEquals(88, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(18, audit.scenarioMetricCount());
		assertEquals(9, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary current =
				find(audit.scenarios(), "current_lab_validation_blocked").summary();
		assertFalse(current.labValidationAccepted());
		assertEquals(32, current.expectedErrorBudgetGroupCount());
		assertEquals(32, current.observedErrorBudgetGroupCount());
		assertEquals(0, current.readyErrorBudgetGroupCount());
		assertEquals(32, current.blockedErrorBudgetGroupCount());
		assertFalse(current.allErrorBudgetGroupsReady());
		assertFalse(current.referenceMaterialExportAllowed());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", current.status());
		assertEquals("surface-wake-lab-validation-not-accepted", current.message());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary ready =
				find(audit.scenarios(), "lab_accepted_error_budget_ready").summary();
		assertTrue(ready.labValidationAccepted());
		assertEquals(32, ready.readyErrorBudgetGroupCount());
		assertEquals(32, ready.validationCandidateGroupCount());
		assertEquals(0, ready.blockedErrorBudgetGroupCount());
		assertTrue(ready.allErrorBudgetGroupsReady());
		assertTrue(ready.referenceMaterialExportAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("aggregate-surface-wake-reference-table", ready.referencePayloadKind());
		assertEquals("READY", ready.status());
		assertEquals("surface-wake-reference-material-ready", ready.message());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary acceptedBlocked =
				find(audit.scenarios(), "lab_accepted_error_budget_blocked").summary();
		assertTrue(acceptedBlocked.labValidationAccepted());
		assertFalse(acceptedBlocked.referenceMaterialExportAllowed());
		assertEquals("surface-wake-error-budget-not-ready", acceptedBlocked.message());

		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary budgetOnly =
				find(audit.scenarios(), "budget_ready_without_lab_acceptance").summary();
		assertFalse(budgetOnly.labValidationAccepted());
		assertTrue(budgetOnly.allErrorBudgetGroupsReady());
		assertFalse(budgetOnly.referenceMaterialExportAllowed());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(1, audit.extrema().referenceMaterialExportAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(32, audit.extrema().maxObservedErrorBudgetGroupCount());
		assertEquals(32, audit.extrema().maxReadyErrorBudgetGroupCount());
		assertEquals(32, audit.extrema().maxBlockedErrorBudgetGroupCount());
	}

	@Test
	void handoffRequiresAcceptedLabValidationAndReadyBudget() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary currentAcceptance =
				findAcceptance("current_transient_probe_unavailable");
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary accepted =
				findAcceptance("transient_probe_all_targets_pass");
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> currentGroups =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit().groups();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> readyGroups =
				currentGroups.stream()
						.map(Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoffTest::readyGroup)
						.toList();

		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary open =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.handoff(
						"open", accepted, readyGroups, "synthetic-live");
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary noAccept =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.handoff(
						"noAccept", currentAcceptance, readyGroups, "synthetic-live");
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary noBudget =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.handoff(
						"noBudget", accepted, currentGroups, "synthetic-live");

		assertTrue(open.referenceMaterialExportAllowed());
		assertFalse(open.runtimeCouplingAllowed());
		assertFalse(open.gameplayAutoApplyAllowed());
		assertFalse(noAccept.referenceMaterialExportAllowed());
		assertFalse(noBudget.referenceMaterialExportAllowed());
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary accepted =
				findAcceptance("transient_probe_all_targets_pass");
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> groups =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit().groups();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.handoff(
						"", accepted, groups, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.handoff(
						"scenario", null, groups, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.handoff(
						"scenario", accepted, null, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.handoff(
						"scenario", accepted, groups, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_reference_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_handoff_summary,all_scenarios,reference_material_export_allowed_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_handoff_summary,all_scenarios,runtime_coupling_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_handoff_scenario,current_lab_validation_blocked,reference_material_export_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_handoff_scenario,lab_accepted_error_budget_ready,reference_material_export_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffScenario find(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary findAcceptance(
			String name
	) {
		return Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.audit().scenarios().stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup readyGroup(
			Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup group
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup(
				group.presetName(),
				group.spinState(),
				group.surfaceType(),
				group.clearanceOverRadius(),
				group.clearanceMeters(),
				group.expectedRotorSeedCount(),
				group.expectedRotorSeedCount(),
				0,
				0,
				group.expectedRotorSeedCount(),
				group.expectedRotorSeedCount(),
				0,
				0,
				true,
				true,
				true,
				group.meanTargetPressurePascals(),
				0.0,
				0.0,
				group.meanTargetWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				group.meanArrivalBandSeconds(),
				0.0,
				0.0,
				true,
				"READY",
				"surface-wake-error-budget-ready"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_hover_surface_wake_reference_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
