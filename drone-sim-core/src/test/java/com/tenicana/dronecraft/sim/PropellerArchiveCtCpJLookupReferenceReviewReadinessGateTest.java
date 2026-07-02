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

class PropellerArchiveCtCpJLookupReferenceReviewReadinessGateTest {
	private static final PropellerArchiveCtCpJLookupReferenceReviewReadinessGate
			.CtCpJLookupReferenceReviewReadinessGateAudit AUDIT =
			PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.audit();

	@Test
	void auditBuildsReferenceReviewReadinessScenarios() {
		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Reference-Review-Readiness-Gate-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("execution acceptance handoff"));
		assertEquals(33, AUDIT.packetRowCount());
		assertEquals(8, AUDIT.sourceReferenceRowCount());
		assertEquals(4, AUDIT.reviewScenarioRowCount());
		assertEquals(20, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(4, AUDIT.scenarios().size());
	}

	@Test
	void currentScenarioBlocksBeforeAcceptanceHandoff() {
		PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario current =
				scenario("current_result_seeds_unavailable_reference_blocked");

		assertEquals("current_result_seeds_unavailable", current.acceptanceHandoffScenarioName());
		assertFalse(current.acceptanceHandoffReady());
		assertFalse(current.compactReferenceReviewed());
		assertFalse(current.lookupAcceptanceReady());
		assertFalse(current.lookupExecutionContractReady());
		assertEquals(9, current.expectedReferenceRowCount());
		assertEquals(12, current.expectedReferenceFieldCount());
		assertEquals(12, current.observedReferenceFieldCount());
		assertEquals(0, current.readySeedCount());
		assertEquals(0, current.pendingSeedCount());
		assertEquals(9, current.unavailableSeedCount());
		assertEquals(0, current.acceptedLookupTargetCount());
		assertEquals(9, current.blockedLookupTargetCount());
		assertTrue(current.allReferenceFieldsPresent());
		assertFalse(current.referenceReviewReady());
		assertFalse(current.referenceMaterialExportAllowed());
		assertEquals("BLOCKED", current.status());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				current.nextRequiredAction());
	}

	@Test
	void pendingResultsCannotOpenReferenceReview() {
		PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario pending =
				scenario("authorized_runs_pending_results_reference_blocked");

		assertEquals("authorized_runs_pending_results", pending.acceptanceHandoffScenarioName());
		assertFalse(pending.acceptanceHandoffReady());
		assertTrue(pending.lookupExecutionContractReady());
		assertEquals(0, pending.readySeedCount());
		assertEquals(9, pending.pendingSeedCount());
		assertEquals(0, pending.acceptedLookupTargetCount());
		assertEquals(9, pending.blockedLookupTargetCount());
		assertFalse(pending.referenceReviewReady());
		assertEquals("record-lookup-execution-result-evidence-before-acceptance",
				pending.nextRequiredAction());
		assertTrue(pending.message().contains("waiting for CT/CP/J result evidence"));
	}

	@Test
	void acceptanceHandoffReadyStillBlocksWithoutReferenceReview() {
		PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario reviewMissing =
				scenario("acceptance_handoff_ready_reference_review_missing");

		assertTrue(reviewMissing.acceptanceHandoffReady());
		assertTrue(reviewMissing.lookupAcceptanceReady());
		assertTrue(reviewMissing.lookupExecutionContractReady());
		assertTrue(reviewMissing.lookupInterpolationExecuted());
		assertFalse(reviewMissing.compactReferenceReviewed());
		assertEquals(9, reviewMissing.readySeedCount());
		assertEquals(9, reviewMissing.acceptedLookupTargetCount());
		assertEquals(0, reviewMissing.blockedLookupTargetCount());
		assertFalse(reviewMissing.referenceReviewReady());
		assertFalse(reviewMissing.referenceMaterialExportAllowed());
		assertEquals("BLOCKED", reviewMissing.status());
		assertEquals("review-accepted-ct-cp-j-results-before-reference-export",
				reviewMissing.nextRequiredAction());
	}

	@Test
	void reviewedAcceptanceHandoffOpensReferenceMaterialOnly() {
		PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario ready =
				scenario("acceptance_handoff_ready_reference_reviewed");

		assertTrue(ready.acceptanceHandoffReady());
		assertTrue(ready.compactReferenceReviewed());
		assertTrue(ready.referenceReviewReady());
		assertTrue(ready.referenceMaterialExportAllowed());
		assertEquals(9, ready.performanceReferenceRowAvailableCount());
		assertEquals(6, ready.fullSimulationReferenceRowAvailableCount());
		assertEquals(3, ready.performanceOnlyReferenceRowAvailableCount());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("REVIEW_READY", ready.status());
		assertEquals("materialize-ct-cp-j-lookup-reference-table-after-review",
				ready.nextRequiredAction());
	}

	@Test
	void summaryKeepsCurrentBlockedAndSyntheticReviewReadyCeiling() {
		PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessSummary summary =
				AUDIT.summary();

		assertEquals(4, summary.scenarioCount());
		assertEquals(1, summary.reviewReadyScenarioCount());
		assertEquals(3, summary.blockedScenarioCount());
		assertEquals(9, summary.maxExpectedReferenceRowCount());
		assertEquals(9, summary.maxReadySeedCount());
		assertEquals(9, summary.maxPendingSeedCount());
		assertEquals(9, summary.maxUnavailableSeedCount());
		assertEquals(9, summary.maxAcceptedLookupTargetCount());
		assertEquals(9, summary.maxBlockedLookupTargetCount());
		assertEquals(9, summary.maxPerformanceReferenceRowAvailableCount());
		assertEquals(6, summary.maxFullSimulationReferenceRowAvailableCount());
		assertEquals(3, summary.maxPerformanceOnlyReferenceRowAvailableCount());
		assertEquals(0, summary.currentReadySeedCount());
		assertEquals(0, summary.currentAcceptedLookupTargetCount());
		assertEquals(9, summary.currentBlockedLookupTargetCount());
		assertEquals(1, summary.referenceMaterialExportAllowedCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("acceptance_handoff_ready_reference_reviewed", summary.firstReviewReadyScenario());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				summary.nextRequiredAction());
	}

	@Test
	void rejectsInvalidScenarioInputs() {
		PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario handoff =
				PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.scenario(
						"synthetic_all_result_seeds_ready");
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.scenario("", handoff, true));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.scenario("bad", null, true));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_reference_review_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_review_readiness,current_result_seeds_unavailable_reference_blocked,BLOCKED,reference_review_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_review_readiness,authorized_runs_pending_results_reference_blocked,BLOCKED,pending_seed_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_review_readiness,acceptance_handoff_ready_reference_reviewed,REVIEW_READY,reference_material_export_allowed,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_review_readiness_summary,all,BLOCKED,max_full_simulation_reference_row_available_count,6,count,")));
	}

	private static PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario
			scenario(String scenarioName) {
		return AUDIT.scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_reference_review_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
