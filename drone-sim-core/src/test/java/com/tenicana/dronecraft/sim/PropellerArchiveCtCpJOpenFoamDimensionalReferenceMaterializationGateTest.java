package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGateTest {
	private static final PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
			.CtCpJOpenFoamDimensionalReferenceMaterializationGateAudit AUDIT =
					PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate.audit();

	@Test
	void auditBuildsMaterializationReadinessScenarios() {
		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Reference-Materialization-Gate-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("both reviewed CT/CP/J lookup reference rows"));
		assertTrue(AUDIT.caveat().contains("reviewed OpenFOAM dimensional reference handoff rows"));
		assertEquals(34, AUDIT.packetRowCount());
		assertEquals(8, AUDIT.sourceReferenceRowCount());
		assertEquals(5, AUDIT.materializationScenarioRowCount());
		assertEquals(20, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(5, AUDIT.scenarios().size());
	}

	@Test
	void currentScenarioBlocksOpenFoamMaterializationBeforeLookupAndOpenFoamReadiness() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario current =
						scenario("current_lookup_and_openfoam_blocked");

		assertEquals("current_result_seeds_unavailable_reference_blocked",
				current.lookupReferenceReviewScenarioName());
		assertEquals("current_dimensional_support_blocked",
				current.openFoamDimensionalHandoffScenarioName());
		assertFalse(current.lookupReferenceReviewReady());
		assertFalse(current.lookupReferenceMaterialExportAllowed());
		assertFalse(current.openFoamDimensionalHandoffReady());
		assertFalse(current.openFoamDimensionalSupportReady());
		assertFalse(current.openFoamSolverQualityContractReady());
		assertFalse(current.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(0, current.lookupPerformanceReferenceRowAvailableCount());
		assertEquals(0, current.lookupFullSimulationReferenceRowAvailableCount());
		assertEquals(6, current.expectedOpenFoamReferenceRowCount());
		assertEquals(0, current.openFoamReferenceRowAvailableCount());
		assertEquals(6, current.blockedOpenFoamReferenceRowCount());
		assertEquals(4, current.openFoamSolverQualityBlockerCount());
		assertFalse(current.referenceMaterializationReady());
		assertEquals("BLOCKED", current.status());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				current.nextRequiredAction());
		assertTrue(current.message().contains("lookup reference readiness"));
	}

	@Test
	void pendingLookupResultsKeepOpenFoamMaterializationBlocked() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario pending =
						scenario("lookup_reference_pending_results");

		assertEquals("authorized_runs_pending_results_reference_blocked",
				pending.lookupReferenceReviewScenarioName());
		assertFalse(pending.lookupReferenceMaterialExportAllowed());
		assertEquals(6, pending.expectedOpenFoamReferenceRowCount());
		assertEquals(0, pending.openFoamReferenceRowAvailableCount());
		assertEquals(6, pending.blockedOpenFoamReferenceRowCount());
		assertEquals("record-lookup-execution-result-evidence-before-acceptance",
				pending.nextRequiredAction());
		assertTrue(pending.message().contains("waiting for CT/CP/J result evidence"));
	}

	@Test
	void openFoamReadyStillBlocksWhenLookupReferenceReviewIsMissing() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario lookupMissing =
						scenario("openfoam_ready_lookup_reference_review_missing");

		assertFalse(lookupMissing.lookupReferenceReviewReady());
		assertFalse(lookupMissing.lookupReferenceMaterialExportAllowed());
		assertTrue(lookupMissing.openFoamDimensionalHandoffReady());
		assertTrue(lookupMissing.openFoamDimensionalSupportReady());
		assertTrue(lookupMissing.openFoamSolverQualityContractReady());
		assertTrue(lookupMissing.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(0, lookupMissing.openFoamReferenceRowAvailableCount());
		assertEquals(6, lookupMissing.blockedOpenFoamReferenceRowCount());
		assertEquals("review-accepted-ct-cp-j-results-before-reference-export",
				lookupMissing.nextRequiredAction());
	}

	@Test
	void lookupReadyStillBlocksWhenOpenFoamReferenceReviewIsMissing() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario openFoamReviewMissing =
						scenario("lookup_reference_ready_openfoam_review_missing");

		assertTrue(openFoamReviewMissing.lookupReferenceReviewReady());
		assertTrue(openFoamReviewMissing.lookupReferenceMaterialExportAllowed());
		assertFalse(openFoamReviewMissing.openFoamDimensionalHandoffReady());
		assertTrue(openFoamReviewMissing.openFoamDimensionalSupportReady());
		assertTrue(openFoamReviewMissing.openFoamSolverQualityContractReady());
		assertTrue(openFoamReviewMissing.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(9, openFoamReviewMissing.lookupPerformanceReferenceRowAvailableCount());
		assertEquals(6, openFoamReviewMissing.lookupFullSimulationReferenceRowAvailableCount());
		assertEquals(0, openFoamReviewMissing.openFoamReferenceRowAvailableCount());
		assertEquals(6, openFoamReviewMissing.blockedOpenFoamReferenceRowCount());
		assertEquals("review-openfoam-dimensional-reference-before-materialization",
				openFoamReviewMissing.nextRequiredAction());
		assertTrue(openFoamReviewMissing.message().contains("dimensional reference handoff"));
	}

	@Test
	void lookupAndOpenFoamReadyOpensMaterializationOnly() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario ready =
						scenario("lookup_reference_and_openfoam_ready");

		assertTrue(ready.lookupReferenceReviewReady());
		assertTrue(ready.lookupReferenceMaterialExportAllowed());
		assertTrue(ready.openFoamDimensionalHandoffReady());
		assertTrue(ready.openFoamDimensionalSupportReady());
		assertTrue(ready.openFoamSolverQualityContractReady());
		assertTrue(ready.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(9, ready.lookupPerformanceReferenceRowAvailableCount());
		assertEquals(6, ready.lookupFullSimulationReferenceRowAvailableCount());
		assertEquals(6, ready.expectedOpenFoamReferenceRowCount());
		assertEquals(6, ready.openFoamReferenceRowAvailableCount());
		assertEquals(0, ready.blockedOpenFoamReferenceRowCount());
		assertEquals(0, ready.openFoamSolverQualityBlockerCount());
		assertTrue(ready.referenceMaterializationReady());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("MATERIALIZATION_READY", ready.status());
		assertEquals("materialize-openfoam-dimensional-reference-table-after-review",
				ready.nextRequiredAction());
	}

	@Test
	void summaryKeepsCurrentBlockedAndReadyCeilingVisible() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationSummary summary = AUDIT.summary();

		assertEquals(5, summary.scenarioCount());
		assertEquals(1, summary.materializationReadyScenarioCount());
		assertEquals(4, summary.blockedScenarioCount());
		assertEquals(9, summary.maxLookupPerformanceReferenceRowAvailableCount());
		assertEquals(6, summary.maxLookupFullSimulationReferenceRowAvailableCount());
		assertEquals(6, summary.maxExpectedOpenFoamReferenceRowCount());
		assertEquals(6, summary.maxOpenFoamReferenceRowAvailableCount());
		assertEquals(6, summary.maxBlockedOpenFoamReferenceRowCount());
		assertEquals(0, summary.currentLookupPerformanceReferenceRowAvailableCount());
		assertEquals(0, summary.currentOpenFoamReferenceRowAvailableCount());
		assertEquals(6, summary.currentBlockedOpenFoamReferenceRowCount());
		assertEquals(2, summary.openFoamDimensionalHandoffReadyScenarioCount());
		assertEquals(2, summary.lookupReferenceReviewReadyScenarioCount());
		assertEquals(4, summary.maxOpenFoamSolverQualityBlockerCount());
		assertEquals(2, summary.openFoamSolverQualityBlockedScenarioCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("lookup_reference_and_openfoam_ready", summary.firstMaterializationReadyScenario());
		assertEquals("BLOCKED", summary.currentStatus());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				summary.nextRequiredAction());
	}

	@Test
	void rejectsInvalidInputs() {
		PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario lookup =
				validLookup();
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				validHandoff();

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
						.scenario("", lookup, handoff));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
						.scenario("bad", null, handoff));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
						.scenario("bad", lookup, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_reference_materialization_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_materialization,current_lookup_and_openfoam_blocked,BLOCKED,reference_materialization_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_materialization,openfoam_ready_lookup_reference_review_missing,BLOCKED,lookup_reference_material_export_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_materialization,lookup_reference_and_openfoam_ready,MATERIALIZATION_READY,openfoam_reference_row_available_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_materialization_summary,all,BLOCKED,max_openfoam_reference_row_available_count,6,count,")));
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
			.OpenFoamDimensionalReferenceMaterializationScenario scenario(String name) {
		return AUDIT.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario
			validLookup() {
		return new PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario(
				"acceptance_handoff_ready_reference_reviewed",
				"synthetic_all_result_seeds_ready",
				true,
				true,
				true,
				true,
				true,
				9,
				PropellerArchiveCtCpJLookupReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				PropellerArchiveCtCpJLookupReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				9,
				0,
				0,
				9,
				0,
				true,
				true,
				true,
				9,
				6,
				3,
				false,
				false,
				"REVIEW_READY",
				"materialize-ct-cp-j-lookup-reference-table-after-review",
				"CT/CP/J lookup reference review is ready for table materialization"
		);
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary
			validHandoff() {
		return new PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary(
				true,
				true,
				true,
				true,
				true,
				true,
				0,
				"openfoam-solver-quality-blockers-clear",
				6,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				6,
				0,
				true,
				5,
				1,
				9,
				0.00027500814692071884,
				0.000071,
				6,
				0,
				0.0,
				0.0,
				true,
				true,
				6,
				false,
				false,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_PAYLOAD_KIND,
				"READY",
				"openfoam-dimensional-reference-material-ready",
				"READY",
				"synthetic-openfoam-dimensional-reference-handoff-ready"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_reference_materialization_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
