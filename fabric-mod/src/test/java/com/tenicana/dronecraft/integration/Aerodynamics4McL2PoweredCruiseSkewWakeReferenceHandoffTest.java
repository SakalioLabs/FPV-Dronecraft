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

class Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoffTest {
	@Test
	void auditBuildsStableReferenceHandoffScenarios() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Reference-Handoff-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("never enables runtime coupling"));
		assertEquals(93, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(19, audit.scenarioMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary current =
				find(audit.scenarios(), "current_lab_validation_blocked").summary();
		assertFalse(current.labValidationAccepted());
		assertEquals(48, current.expectedErrorBudgetGroupCount());
		assertEquals(48, current.observedErrorBudgetGroupCount());
		assertEquals(0, current.readyErrorBudgetGroupCount());
		assertEquals(48, current.blockedErrorBudgetGroupCount());
		assertFalse(current.allErrorBudgetGroupsReady());
		assertFalse(current.referenceMaterialExportAllowed());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", current.status());
		assertEquals("cruise-skew-wake-lab-validation-not-accepted", current.message());

		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary ready =
				find(audit.scenarios(), "lab_accepted_error_budget_ready").summary();
		assertTrue(ready.labValidationAccepted());
		assertEquals(48, ready.readyErrorBudgetGroupCount());
		assertEquals(48, ready.validationCandidateGroupCount());
		assertEquals(0, ready.blockedErrorBudgetGroupCount());
		assertTrue(ready.allErrorBudgetGroupsReady());
		assertTrue(ready.referenceMaterialExportAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("aggregate-cruise-skew-wake-reference-table", ready.referencePayloadKind());
		assertEquals("READY", ready.status());
		assertEquals("cruise-skew-wake-reference-material-ready", ready.message());

		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary acceptedBlocked =
				find(audit.scenarios(), "lab_accepted_error_budget_blocked").summary();
		assertTrue(acceptedBlocked.labValidationAccepted());
		assertFalse(acceptedBlocked.referenceMaterialExportAllowed());
		assertEquals("cruise-skew-wake-error-budget-not-ready", acceptedBlocked.message());

		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary budgetOnly =
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
		assertEquals(48, audit.extrema().maxObservedErrorBudgetGroupCount());
		assertEquals(48, audit.extrema().maxReadyErrorBudgetGroupCount());
		assertEquals(48, audit.extrema().maxBlockedErrorBudgetGroupCount());
		assertEquals(0.0, audit.extrema().maxMomentumErrorRatio(), 1.0e-12);
	}

	@Test
	void handoffRequiresAcceptedLabValidationAndReadyBudget() {
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary currentAcceptance =
				findAcceptance("current_probe_apis_unavailable");
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary accepted =
				findAcceptance("skew_and_transient_probe_all_targets_pass");
		List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> currentGroups =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit().groups();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> readyGroups =
				currentGroups.stream()
						.map(Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoffTest::readyGroup)
						.toList();

		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary open =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.handoff(
						"open", accepted, readyGroups, "synthetic-live");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary noAccept =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.handoff(
						"noAccept", currentAcceptance, readyGroups, "synthetic-live");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary noBudget =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.handoff(
						"noBudget", accepted, currentGroups, "synthetic-live");

		assertTrue(open.referenceMaterialExportAllowed());
		assertFalse(open.runtimeCouplingAllowed());
		assertFalse(open.gameplayAutoApplyAllowed());
		assertFalse(noAccept.referenceMaterialExportAllowed());
		assertFalse(noBudget.referenceMaterialExportAllowed());
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary accepted =
				findAcceptance("skew_and_transient_probe_all_targets_pass");
		List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> groups =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit().groups();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.handoff(
						"", accepted, groups, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.handoff(
						"scenario", null, groups, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.handoff(
						"scenario", accepted, null, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.handoff(
						"scenario", accepted, groups, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_reference_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_handoff_summary,all_scenarios,reference_material_export_allowed_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_handoff_summary,all_scenarios,runtime_coupling_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_handoff_scenario,current_lab_validation_blocked,reference_material_export_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_handoff_scenario,lab_accepted_error_budget_ready,reference_material_export_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffScenario find(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary findAcceptance(
			String name
	) {
		return Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.audit().scenarios().stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup readyGroup(
			Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup(
				group.presetName(),
				group.spinState(),
				group.axialPlaneIndex(),
				group.sweepColumnIndex(),
				group.axialPlaneFraction(),
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
				group.meanTargetResultantDynamicPressurePascals(),
				0.0,
				0.0,
				group.meanTargetResultantWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				group.meanArrivalBandSeconds(),
				0.0,
				0.0,
				group.meanTargetAxialMomentumFluxNewtons(),
				0.0,
				0.0,
				true,
				"READY",
				"cruise-skew-wake-error-budget-ready"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_reference_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
