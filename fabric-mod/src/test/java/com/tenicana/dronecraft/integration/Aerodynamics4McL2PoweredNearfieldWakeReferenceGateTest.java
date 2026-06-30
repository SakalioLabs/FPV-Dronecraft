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
		assertTrue(audit.caveat().contains("OpenFOAM CT/CP/J dimensional rotor-reference"));
		assertEquals(225, audit.packetMetricRowCount());
		assertEquals(8, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(40, audit.scenarioMetricCount());
		assertEquals(16, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary current =
				find(audit.scenarios(), "current_hover_cruise_and_openfoam_reference_blocked").summary();
		assertFalse(current.nearfieldReferencePackageExportAllowed());
		assertEquals(3, current.blockerCount());
		assertTrue(current.hoverSurfaceWakeReferenceBlocker());
		assertTrue(current.cruiseSkewWakeReferenceBlocker());
		assertTrue(current.openFoamDimensionalReferenceBlocker());
		assertFalse(current.hoverLabValidationAccepted());
		assertFalse(current.cruiseLabValidationAccepted());
		assertFalse(current.hoverErrorBudgetReady());
		assertFalse(current.cruiseErrorBudgetReady());
		assertFalse(current.hoverReferenceMaterialExportAllowed());
		assertFalse(current.cruiseReferenceMaterialExportAllowed());
		assertFalse(current.openFoamLookupExecutionContractReady());
		assertFalse(current.openFoamDimensionalSupportReady());
		assertFalse(current.openFoamSolverQualityContractReady());
		assertFalse(current.openFoamDimensionalReferenceReviewed());
		assertFalse(current.openFoamReferenceMaterialExportAllowed());
		assertEquals(32, current.hoverExpectedReferenceRowCount());
		assertEquals(48, current.cruiseExpectedReferenceRowCount());
		assertEquals(6, current.openFoamExpectedReferenceRowCount());
		assertEquals(0, current.openFoamAvailableReferenceRowCount());
		assertEquals(6, current.openFoamBlockedReferenceRowCount());
		assertEquals(86, current.totalExpectedReferenceRowCount());
		assertEquals(0, current.hoverReadyErrorBudgetGroupCount());
		assertEquals(0, current.cruiseReadyErrorBudgetGroupCount());
		assertEquals(32, current.hoverBlockedErrorBudgetGroupCount());
		assertEquals(48, current.cruiseBlockedErrorBudgetGroupCount());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("combined-hover-surface-cruise-skew-and-openfoam-rotor-reference-package",
				current.referencePayloadKind());
		assertEquals("compact-openfoam-dimensional-rotor-response-reference",
				current.openFoamReferencePayloadKind());
		assertEquals("complete-hover-surface-cruise-skew-and-openfoam-dimensional-reference-handoffs",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("nearfield-wake-and-openfoam-reference-package-blocked", current.message());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary hoverOnly =
				find(audit.scenarios(), "hover_ready_cruise_and_openfoam_reference_blocked").summary();
		assertFalse(hoverOnly.hoverSurfaceWakeReferenceBlocker());
		assertTrue(hoverOnly.cruiseSkewWakeReferenceBlocker());
		assertTrue(hoverOnly.openFoamDimensionalReferenceBlocker());
		assertEquals(2, hoverOnly.blockerCount());
		assertEquals("complete-cruise-skew-wake-and-openfoam-dimensional-reference-handoffs",
				hoverOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary cruiseOnly =
				find(audit.scenarios(), "cruise_ready_hover_and_openfoam_reference_blocked").summary();
		assertTrue(cruiseOnly.hoverSurfaceWakeReferenceBlocker());
		assertFalse(cruiseOnly.cruiseSkewWakeReferenceBlocker());
		assertTrue(cruiseOnly.openFoamDimensionalReferenceBlocker());
		assertEquals(2, cruiseOnly.blockerCount());
		assertEquals("complete-hover-surface-wake-and-openfoam-dimensional-reference-handoffs",
				cruiseOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary wakeReady =
				find(audit.scenarios(), "hover_and_cruise_ready_openfoam_reference_blocked").summary();
		assertFalse(wakeReady.hoverSurfaceWakeReferenceBlocker());
		assertFalse(wakeReady.cruiseSkewWakeReferenceBlocker());
		assertTrue(wakeReady.openFoamDimensionalReferenceBlocker());
		assertEquals(1, wakeReady.blockerCount());
		assertEquals("complete-openfoam-dimensional-rotor-reference-handoff",
				wakeReady.nextRequiredAction());

		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary ready =
				find(audit.scenarios(), "hover_cruise_and_openfoam_reference_ready").summary();
		assertTrue(ready.nearfieldReferencePackageExportAllowed());
		assertEquals(0, ready.blockerCount());
		assertTrue(ready.hoverLabValidationAccepted());
		assertTrue(ready.cruiseLabValidationAccepted());
		assertTrue(ready.hoverErrorBudgetReady());
		assertTrue(ready.cruiseErrorBudgetReady());
		assertTrue(ready.hoverReferenceMaterialExportAllowed());
		assertTrue(ready.cruiseReferenceMaterialExportAllowed());
		assertTrue(ready.openFoamLookupExecutionContractReady());
		assertTrue(ready.openFoamDimensionalSupportReady());
		assertTrue(ready.openFoamSolverQualityContractReady());
		assertTrue(ready.openFoamDimensionalReferenceReviewed());
		assertTrue(ready.openFoamReferenceMaterialExportAllowed());
		assertEquals(6, ready.openFoamAvailableReferenceRowCount());
		assertEquals(0, ready.openFoamBlockedReferenceRowCount());
		assertEquals(86, ready.totalExpectedReferenceRowCount());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("nearfield-wake-and-openfoam-reference-package-ready-for-reviewed-export",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("nearfield-wake-and-openfoam-reference-package-ready", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(3, audit.extrema().maxBlockerCount());
		assertEquals(2, audit.extrema().hoverReferenceBlockerScenarioCount());
		assertEquals(2, audit.extrema().cruiseReferenceBlockerScenarioCount());
		assertEquals(4, audit.extrema().openFoamReferenceBlockerScenarioCount());
		assertEquals(1, audit.extrema().referencePackageExportAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(86, audit.extrema().maxTotalExpectedReferenceRowCount());
		assertEquals(32, audit.extrema().maxHoverBlockedErrorBudgetGroupCount());
		assertEquals(48, audit.extrema().maxCruiseBlockedErrorBudgetGroupCount());
		assertEquals(6, audit.extrema().maxOpenFoamBlockedReferenceRowCount());
		assertEquals(6, audit.extrema().maxOpenFoamAvailableReferenceRowCount());
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
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.OpenFoamDimensionalReferenceReadiness openFoamReady =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.reviewedOpenFoamReferenceReadiness();

		assertTrue(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate
				.gate(hoverReady, cruiseReady, openFoamReady)
				.nearfieldReferencePackageExportAllowed());
		assertFalse(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate
				.gate(hoverReady, cruiseReady)
				.nearfieldReferencePackageExportAllowed());
		assertFalse(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate
				.gate(hoverBlocked, cruiseReady, openFoamReady)
				.nearfieldReferencePackageExportAllowed());
		assertFalse(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate
				.gate(hoverReady, cruiseBlocked, openFoamReady)
				.nearfieldReferencePackageExportAllowed());
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverReady =
				findHover("lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseReady =
				findCruise("lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.OpenFoamDimensionalReferenceReadiness openFoamReady =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.reviewedOpenFoamReferenceReadiness();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.gate(null, cruiseReady));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.gate(hoverReady, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.gate(hoverReady, cruiseReady, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.gate(null, cruiseReady, openFoamReady));
		assertThrows(IllegalArgumentException.class,
				() -> new Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.OpenFoamDimensionalReferenceReadiness(
						true, true, true, true, true, 6, 3, 4,
						"compact-openfoam-dimensional-rotor-response-reference"));
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
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_scenario,current_hover_cruise_and_openfoam_reference_blocked,nearfield_reference_package_export_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_scenario,hover_cruise_and_openfoam_reference_ready,nearfield_reference_package_export_allowed,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_scenario,hover_and_cruise_ready_openfoam_reference_blocked,openfoam_dimensional_reference_blocker,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_gate_scenario,hover_cruise_and_openfoam_reference_ready,total_expected_reference_row_count,86,")));
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
