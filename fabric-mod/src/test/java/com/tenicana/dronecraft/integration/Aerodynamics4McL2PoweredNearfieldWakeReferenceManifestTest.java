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

class Aerodynamics4McL2PoweredNearfieldWakeReferenceManifestTest {
	@Test
	void auditBuildsBlockedCurrentAndReadyTargetManifest() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestAudit audit =
				auditFixture();

		assertEquals("A4MC-L2-Powered-Nearfield-Wake-Reference-Manifest-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("combined lab gate"));
		assertTrue(audit.caveat().contains("OpenFOAM rotor-response"));
		assertTrue(audit.caveat().contains("coefficient lookup shape-guard"));
		assertEquals(351, audit.packetMetricRowCount());
		assertEquals(11, audit.sourceReferenceCount());
		assertEquals(2, audit.scenarioSampleCount());
		assertEquals(4, audit.artifactSampleCount());
		assertEquals(8, audit.manifestEntryCount());
		assertEquals(39, audit.entryMetricCount());
		assertEquals(27, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(8, audit.entries().size());

		List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry>
				current = entries(audit.entries(), "current_nearfield_reference_manifest_blocked");
		assertEquals(4, current.size());
		for (Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry entry
				: current) {
			assertFalse(entry.labValidationAccepted());
			assertFalse(entry.errorBudgetReady());
			assertFalse(entry.handoffExportAllowed());
			assertFalse(entry.nearfieldPackageExportAllowed());
			assertFalse(entry.artifactExportAllowed());
			assertEquals(0, entry.availableReferenceRowCount());
			assertEquals(entry.expectedReferenceRowCount(), entry.blockedReferenceRowCount());
			assertFalse(entry.runtimeCouplingAllowed());
			assertFalse(entry.gameplayAutoApplyAllowed());
			assertTrue(entry.playableReviewRequiredBeforeUse());
			assertEquals("complete-hover-surface-cruise-skew-and-openfoam-dimensional-reference-handoffs",
					entry.nextRequiredAction());
			assertEquals("BLOCKED", entry.status());
			assertTrue(entry.message().endsWith("-blocked-by-nearfield-reference-gate"));
		}

		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry
				currentCombined = entry(current, "combined_nearfield_wake_reference_package");
		assertEquals("combined-reference-package", currentCombined.artifactKind());
		assertEquals(86, currentCombined.expectedReferenceRowCount());
		assertEquals(86, currentCombined.blockedReferenceRowCount());
		assertEquals(6, entry(current, "openfoam_dimensional_rotor_reference_table")
				.expectedReferenceRowCount());
		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry
				currentOpenFoam = entry(current, "openfoam_dimensional_rotor_reference_table");
		assertEquals(4, currentOpenFoam.openFoamSolverQualityBlockerCount());
		assertEquals(6, currentOpenFoam.openFoamSolverQualityBlockerRowCount());
		assertEquals("current_lookup_and_openfoam_blocked",
				currentOpenFoam.openFoamReferenceMaterializationScenarioName());
		assertFalse(currentOpenFoam.openFoamLookupReferenceMaterialExportAllowed());
		assertFalse(currentOpenFoam.openFoamReferenceMaterializationReady());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				currentOpenFoam.openFoamReferenceMaterializationNextRequiredAction());
		assertEquals("review-openfoam-mesh-yplus-and-time-step-against-run-setup",
				currentOpenFoam.openFoamSolverQualityNextRequiredAction());
		assertFalse(currentOpenFoam.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(0, currentOpenFoam.openFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(6, currentOpenFoam.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, currentOpenFoam.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, currentOpenFoam.openFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertEquals(0.00027500814692071884,
				currentOpenFoam.openFoamCoefficientArchiveCurveEtaFormulaResidual());
		assertEquals(0.000071, currentOpenFoam.openFoamCoefficientArchiveCurveCtIncrease());
		assertEquals(2, currentOpenFoam.openFoamArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(9, currentOpenFoam.openFoamNegativeThrustTailReferenceCount());
		assertEquals(0.00027500814692071884,
				currentOpenFoam.openFoamArchiveCurveEtaFormulaResidual());
		assertEquals(0.000071, currentOpenFoam.openFoamArchiveCurveCtIncrease());
		assertEquals(0, currentOpenFoam.openFoamArchiveCurveShapeGuardCompleteRowCount());
		assertEquals(4, currentCombined.openFoamSolverQualityBlockerCount());
		assertEquals(6, currentCombined.openFoamSolverQualityBlockerRowCount());
		assertEquals("current_lookup_and_openfoam_blocked",
				currentCombined.openFoamReferenceMaterializationScenarioName());
		assertFalse(currentCombined.openFoamLookupReferenceMaterialExportAllowed());
		assertFalse(currentCombined.openFoamReferenceMaterializationReady());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				currentCombined.openFoamReferenceMaterializationNextRequiredAction());
		assertFalse(currentCombined.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(0, currentCombined.openFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(6, currentCombined.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, currentCombined.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, currentCombined.openFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertEquals(0.00027500814692071884,
				currentCombined.openFoamCoefficientArchiveCurveEtaFormulaResidual());
		assertEquals(0.000071, currentCombined.openFoamCoefficientArchiveCurveCtIncrease());
		assertEquals(2, currentCombined.openFoamArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(9, currentCombined.openFoamNegativeThrustTailReferenceCount());
		assertEquals(0.00027500814692071884,
				currentCombined.openFoamArchiveCurveEtaFormulaResidual());
		assertEquals(0.000071, currentCombined.openFoamArchiveCurveCtIncrease());
		assertEquals(0, currentCombined.openFoamArchiveCurveShapeGuardCompleteRowCount());

		List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry>
				ready = entries(audit.entries(), "synthetic_nearfield_reference_manifest_ready");
		assertEquals(4, ready.size());
		for (Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry entry
				: ready) {
			assertTrue(entry.labValidationAccepted());
			assertTrue(entry.errorBudgetReady());
			assertTrue(entry.handoffExportAllowed());
			assertTrue(entry.nearfieldPackageExportAllowed());
			assertTrue(entry.artifactExportAllowed());
			assertEquals(entry.expectedReferenceRowCount(), entry.availableReferenceRowCount());
			assertEquals(0, entry.blockedReferenceRowCount());
			assertFalse(entry.runtimeCouplingAllowed());
			assertFalse(entry.gameplayAutoApplyAllowed());
			assertTrue(entry.playableReviewRequiredBeforeUse());
			assertEquals(0, entry.openFoamSolverQualityBlockerCount());
			assertEquals(0, entry.openFoamSolverQualityBlockerRowCount());
			assertEquals("openfoam-solver-quality-blockers-clear",
					entry.openFoamSolverQualityNextRequiredAction());
			assertEquals("nearfield-wake-and-openfoam-reference-package-ready-for-reviewed-export",
					entry.nextRequiredAction());
			assertEquals("READY", entry.status());
			assertTrue(entry.message().endsWith("-ready-for-reviewed-export"));
		}

		assertEquals(32, entry(ready, "hover_surface_wake_reference_table").availableReferenceRowCount());
		assertEquals(48, entry(ready, "cruise_skew_wake_reference_table").availableReferenceRowCount());
		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry
				readyOpenFoam = entry(ready, "openfoam_dimensional_rotor_reference_table");
		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry
				readyCombined = entry(ready, "combined_nearfield_wake_reference_package");
		assertEquals(6, readyOpenFoam.availableReferenceRowCount());
		assertEquals("lookup_reference_and_openfoam_ready",
				readyOpenFoam.openFoamReferenceMaterializationScenarioName());
		assertTrue(readyOpenFoam.openFoamLookupReferenceMaterialExportAllowed());
		assertTrue(readyOpenFoam.openFoamReferenceMaterializationReady());
		assertEquals("materialize-openfoam-dimensional-reference-table-after-review",
				readyOpenFoam.openFoamReferenceMaterializationNextRequiredAction());
		assertTrue(readyOpenFoam.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(6, readyOpenFoam.openFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(5, readyOpenFoam.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, readyOpenFoam.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, readyOpenFoam.openFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertEquals(0.00027500814692071884,
				readyOpenFoam.openFoamCoefficientArchiveCurveEtaFormulaResidual());
		assertEquals(0.000071, readyOpenFoam.openFoamCoefficientArchiveCurveCtIncrease());
		assertEquals(6, readyOpenFoam.openFoamArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(0, readyOpenFoam.openFoamNegativeThrustTailReferenceCount());
		assertEquals(0.0, readyOpenFoam.openFoamArchiveCurveEtaFormulaResidual());
		assertEquals(0.0, readyOpenFoam.openFoamArchiveCurveCtIncrease());
		assertEquals(6, readyOpenFoam.openFoamArchiveCurveShapeGuardCompleteRowCount());
		assertEquals(86, readyCombined.availableReferenceRowCount());
		assertEquals("lookup_reference_and_openfoam_ready",
				readyCombined.openFoamReferenceMaterializationScenarioName());
		assertTrue(readyCombined.openFoamLookupReferenceMaterialExportAllowed());
		assertTrue(readyCombined.openFoamReferenceMaterializationReady());
		assertEquals(6, readyCombined.openFoamArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(0, readyCombined.openFoamNegativeThrustTailReferenceCount());
		assertEquals(0.0, readyCombined.openFoamArchiveCurveEtaFormulaResidual());
		assertEquals(0.0, readyCombined.openFoamArchiveCurveCtIncrease());
		assertEquals(6, readyCombined.openFoamArchiveCurveShapeGuardCompleteRowCount());

		assertEquals(8, audit.extrema().manifestEntryCount());
		assertEquals(2, audit.extrema().scenarioSampleCount());
		assertEquals(4, audit.extrema().artifactSampleCount());
		assertEquals(0, audit.extrema().currentArtifactExportAllowedCount());
		assertEquals(4, audit.extrema().readyArtifactExportAllowedCount());
		assertEquals(0, audit.extrema().currentTableAvailableReferenceRowCount());
		assertEquals(86, audit.extrema().readyTableAvailableReferenceRowCount());
		assertEquals(0, audit.extrema().currentOpenFoamAvailableReferenceRowCount());
		assertEquals(6, audit.extrema().readyOpenFoamAvailableReferenceRowCount());
		assertEquals(0, audit.extrema().currentCombinedPackageAvailableReferenceRowCount());
		assertEquals(86, audit.extrema().readyCombinedPackageAvailableReferenceRowCount());
		assertEquals(4, audit.extrema().maxOpenFoamSolverQualityBlockerCount());
		assertEquals(6, audit.extrema().maxOpenFoamSolverQualityBlockerRowCount());
		assertEquals(6, audit.extrema().maxOpenFoamArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(9, audit.extrema().maxOpenFoamNegativeThrustTailReferenceCount());
		assertEquals(0.00027500814692071884,
				audit.extrema().maxOpenFoamArchiveCurveEtaFormulaResidual());
		assertEquals(0.000071, audit.extrema().maxOpenFoamArchiveCurveCtIncrease());
		assertEquals(6, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(6, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, audit.extrema().maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertEquals(0.00027500814692071884,
				audit.extrema().maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual());
		assertEquals(0.000071, audit.extrema().maxOpenFoamCoefficientArchiveCurveCtIncrease());
		assertEquals(6, audit.extrema().maxOpenFoamArchiveCurveShapeGuardCompleteRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(8, audit.extrema().playableReviewRequiredBeforeUseCount());
	}

	@Test
	void manifestEntriesFollowCombinedGateRatherThanSingleTableReadiness() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit gateAudit =
				gateAuditFixture();
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary hoverReadyCruiseBlocked =
				gate(gateAudit, "hover_ready_cruise_and_openfoam_reference_blocked");

		List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.manifestEntries(
						"hover_ready_cruise_blocked_manifest", hoverReadyCruiseBlocked);

		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry hover =
				entry(entries, "hover_surface_wake_reference_table");
		assertTrue(hover.labValidationAccepted());
		assertTrue(hover.errorBudgetReady());
		assertTrue(hover.handoffExportAllowed());
		assertFalse(hover.nearfieldPackageExportAllowed());
		assertFalse(hover.artifactExportAllowed());
		assertEquals(0, hover.openFoamSolverQualityBlockerCount());
		assertEquals("openfoam-solver-quality-blockers-clear",
				hover.openFoamSolverQualityNextRequiredAction());
		assertEquals("not-applicable", hover.openFoamReferenceMaterializationScenarioName());
		assertFalse(hover.openFoamLookupReferenceMaterialExportAllowed());
		assertFalse(hover.openFoamReferenceMaterializationReady());
		assertEquals("openfoam-materialization-not-applicable",
				hover.openFoamReferenceMaterializationNextRequiredAction());
		assertFalse(hover.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(0, hover.openFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(0, hover.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(0, hover.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(0, hover.openFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertEquals(0.0, hover.openFoamCoefficientArchiveCurveEtaFormulaResidual());
		assertEquals(0.0, hover.openFoamCoefficientArchiveCurveCtIncrease());
		assertEquals(0, hover.openFoamArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(0, hover.openFoamNegativeThrustTailReferenceCount());
		assertEquals(0.0, hover.openFoamArchiveCurveEtaFormulaResidual());
		assertEquals(0.0, hover.openFoamArchiveCurveCtIncrease());
		assertEquals(0, hover.openFoamArchiveCurveShapeGuardCompleteRowCount());
		assertEquals(0, hover.availableReferenceRowCount());
		assertEquals(32, hover.blockedReferenceRowCount());
		assertEquals("complete-cruise-skew-wake-and-openfoam-dimensional-reference-handoffs",
				hover.nextRequiredAction());
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary ready =
				gate(gateAuditFixture(),
						"hover_cruise_and_openfoam_reference_ready");

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.manifestEntries("", ready));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.manifestEntries("ready", null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestAudit audit =
				auditFixture();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_nearfield_wake_reference_manifest_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest_summary,all_entries,ready_combined_package_available_reference_row_count,86,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:combined_nearfield_wake_reference_package,artifact_export_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:openfoam_dimensional_rotor_reference_table,openfoam_solver_quality_blocker_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:openfoam_dimensional_rotor_reference_table,openfoam_reference_materialization_scenario_name,current_lookup_and_openfoam_blocked,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:openfoam_dimensional_rotor_reference_table,openfoam_lookup_reference_material_export_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:openfoam_dimensional_rotor_reference_table,openfoam_coefficient_lookup_shape_guard_ready,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:openfoam_dimensional_rotor_reference_table,openfoam_coefficient_negative_thrust_tail_execution_input_row_count,9,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:openfoam_dimensional_rotor_reference_table,openfoam_archive_curve_shape_guard_inherited_reference_count,3,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:openfoam_dimensional_rotor_reference_table,openfoam_negative_thrust_tail_reference_count,9,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest_summary,all_entries,max_openfoam_solver_quality_blocker_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest_summary,all_entries,max_openfoam_coefficient_lookup_shape_guard_ready_row_count,6,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest_summary,all_entries,max_openfoam_coefficient_lookup_shape_guard_inherited_scenario_count,6,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest_summary,all_entries,max_openfoam_archive_curve_shape_guard_complete_row_count,6,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:openfoam_dimensional_rotor_reference_table,available_reference_row_count,6,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:openfoam_dimensional_rotor_reference_table,openfoam_reference_materialization_ready,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:openfoam_dimensional_rotor_reference_table,openfoam_coefficient_lookup_shape_guard_ready,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:openfoam_dimensional_rotor_reference_table,openfoam_archive_curve_shape_guard_complete_row_count,6,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:combined_nearfield_wake_reference_package,available_reference_row_count,86,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:combined_nearfield_wake_reference_package,gameplay_auto_apply_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary gate(
			Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries(
			List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries,
			String scenarioName
	) {
		return entries.stream()
				.filter(entry -> scenarioName.equals(entry.scenarioName()))
				.toList();
	}

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry entry(
			List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries,
			String artifactId
	) {
		return entries.stream()
				.filter(entry -> artifactId.equals(entry.artifactId()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_nearfield_wake_reference_manifest_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestAudit
			auditFixture;

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit
			gateAuditFixture;

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestAudit
			auditFixture() {
		if (auditFixture == null) {
			auditFixture = Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.audit();
		}
		return auditFixture;
	}

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit
			gateAuditFixture() {
		if (gateAuditFixture == null) {
			gateAuditFixture = Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit();
		}
		return gateAuditFixture;
	}
}
