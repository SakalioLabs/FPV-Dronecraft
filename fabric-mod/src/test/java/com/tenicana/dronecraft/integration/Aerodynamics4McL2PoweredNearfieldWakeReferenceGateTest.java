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

class Aerodynamics4McL2PoweredNearfieldWakeReferenceGateTest {
	@Test
	void auditBuildsStableNearfieldReferenceScenarios() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit audit =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit();

		assertEquals("A4MC-L2-Powered-Nearfield-Wake-Reference-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not enable runtime coupling"));
		assertEquals(140, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(30, audit.scenarioMetricCount());
		assertEquals(13, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary current =
				find(audit.scenarios(), "current_hover_and_cruise_reference_blocked").summary();
		assertFalse(current.nearfieldReferencePackageExportAllowed());
		assertEquals(2, current.blockerCount());
		assertTrue(current.hoverSurfaceWakeReferenceBlocker());
		assertTrue(current.cruiseSkewWakeReferenceBlocker());
		assertFalse(current.hoverLabValidationAccepted());
		assertFalse(current.cruiseLabValidationAccepted());
		assertFalse(current.hoverErrorBudgetReady());
		assertFalse(current.cruiseErrorBudgetReady());
		assertFalse(current.hoverReferenceMaterialExportAllowed());
		assertFalse(current.cruiseReferenceMaterialExportAllowed());
		assertEquals(32, current.hoverExpectedReferenceRowCount());
		assertEquals(48, current.cruiseExpectedReferenceRowCount());
		assertEquals(80, current.totalExpectedReferenceRowCount());
		assertEquals(0, current.hoverReadyErrorBudgetGroupCount());
		assertEquals(0, current.cruiseReadyErrorBudgetGroupCount());
		assertEquals(32, current.hoverBlockedErrorBudgetGroupCount());
		assertEquals(48, current.cruiseBlockedErrorBudgetGroupCount());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("combined-hover-surface-and-cruise-skew-wake-reference-package",
				current.referencePayloadKind());
		assertEquals("complete-hover-surface-and-cruise-skew-wake-reference-handoffs",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("nearfield-wake-reference-package-blocked", current.message());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary hoverOnly =
				find(audit.scenarios(), "hover_ready_cruise_reference_blocked").summary();
		assertFalse(hoverOnly.hoverSurfaceWakeReferenceBlocker());
		assertTrue(hoverOnly.cruiseSkewWakeReferenceBlocker());
		assertEquals(1, hoverOnly.blockerCount());
		assertEquals("complete-cruise-skew-wake-reference-handoff",
				hoverOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary cruiseOnly =
				find(audit.scenarios(), "cruise_ready_hover_reference_blocked").summary();
		assertTrue(cruiseOnly.hoverSurfaceWakeReferenceBlocker());
		assertFalse(cruiseOnly.cruiseSkewWakeReferenceBlocker());
		assertEquals(1, cruiseOnly.blockerCount());
		assertEquals("complete-hover-surface-wake-reference-handoff",
				cruiseOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary ready =
				find(audit.scenarios(), "hover_and_cruise_reference_ready").summary();
		assertTrue(ready.nearfieldReferencePackageExportAllowed());
		assertEquals(0, ready.blockerCount());
		assertTrue(ready.hoverLabValidationAccepted());
		assertTrue(ready.cruiseLabValidationAccepted());
		assertTrue(ready.hoverErrorBudgetReady());
		assertTrue(ready.cruiseErrorBudgetReady());
		assertTrue(ready.hoverReferenceMaterialExportAllowed());
		assertTrue(ready.cruiseReferenceMaterialExportAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("nearfield-wake-reference-package-ready-for-reviewed-export",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("nearfield-wake-reference-package-ready", ready.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(2, audit.extrema().maxBlockerCount());
		assertEquals(2, audit.extrema().hoverReferenceBlockerScenarioCount());
		assertEquals(2, audit.extrema().cruiseReferenceBlockerScenarioCount());
		assertEquals(1, audit.extrema().referencePackageExportAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(80, audit.extrema().maxTotalExpectedReferenceRowCount());
		assertEquals(32, audit.extrema().maxHoverBlockedErrorBudgetGroupCount());
		assertEquals(48, audit.extrema().maxCruiseBlockedErrorBudgetGroupCount());
		assertEquals(0.0, audit.extrema().maxCruiseMomentumErrorRatio(), 1.0e-12);
	}

	@Test
	void gateRequiresBothReferenceHandoffs() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverReady =
				findHover("lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverBlocked =
				findHover("current_lab_validation_blocked");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseReady =
				findCruise("lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseBlocked =
				findCruise("current_lab_validation_blocked");

		assertTrue(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate
				.gate(hoverReady, cruiseReady)
				.nearfieldReferencePackageExportAllowed());
		assertFalse(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate
				.gate(hoverBlocked, cruiseReady)
				.nearfieldReferencePackageExportAllowed());
		assertFalse(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate
				.gate(hoverReady, cruiseBlocked)
				.nearfieldReferencePackageExportAllowed());
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverReady =
				findHover("lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseReady =
				findCruise("lab_accepted_error_budget_ready");

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.gate(null, cruiseReady));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.gate(hoverReady, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit audit =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_nearfield_wake_reference_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_summary,all_scenarios,reference_package_export_allowed_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_scenario,current_hover_and_cruise_reference_blocked,nearfield_reference_package_export_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_scenario,hover_and_cruise_reference_ready,nearfield_reference_package_export_allowed,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_scenario,hover_and_cruise_reference_ready,total_expected_reference_row_count,80,")));
	}

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceScenario find(
			List<Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary findHover(
			String name
	) {
		return Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.audit().scenarios().stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary findCruise(
			String name
	) {
		return Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.audit().scenarios().stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_nearfield_wake_reference_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
