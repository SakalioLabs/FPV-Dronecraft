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

class PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueueTest {
	@Test
	void auditCreatesBlockedManualReviewQueueFromCoefficientDrafts() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue
				.CtCpJLookupCoefficientPayloadDraftReviewQueueAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Draft-Review-Queue-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("blocked manual-review queue"));
		assertEquals(50, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(21, audit.reviewQueueRowCount());
		assertEquals(20, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(21, audit.rows().size());
	}

	@Test
	void directStaticRowsStayLowPriorityButStillRequireReview() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow row =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.reviewRow(
						"racingQuad", "static_anchor_low_rpm", "static-anchor");

		assertEquals("DIRECT_STATIC_SAMPLE_DRAFT", row.draftCoefficientSourceKind());
		assertEquals(0.139069, row.draftCtCoefficient(), 1.0e-12);
		assertEquals(0.116804, row.draftCpCoefficient(), 1.0e-12);
		assertTrue(row.directStaticSampleUsed());
		assertTrue(row.postReviewFullSimulationLookupAllowed());
		assertFalse(row.negativeCtRisk());
		assertFalse(row.highNearestSourceDistanceRisk());
		assertFalse(row.highFitSourceDistanceRisk());
		assertFalse(row.performanceOnlyCoverageRisk());
		assertTrue(row.manualReviewRequired());
		assertFalse(row.draftReviewPassed());
		assertFalse(row.lookupExecutionInputReady());
		assertEquals("LOW", row.reviewPriority());
		assertEquals("manual-review-required-before-payload-promotion", row.message());
	}

	@Test
	void highDistanceRowsArePrioritizedBeforePayloadPromotion() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow racingHigh =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.reviewRow(
						"racingQuad", "high_domain_max_rpm", "j1-rmax");
		assertFalse(racingHigh.negativeCtRisk());
		assertFalse(racingHigh.highNearestSourceDistanceRisk());
		assertTrue(racingHigh.highFitSourceDistanceRisk());
		assertEquals("HIGH", racingHigh.reviewPriority());
		assertEquals("fit-source-distance-high-for-draft-payload", racingHigh.message());

		PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow heavyHigh =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.reviewRow(
						"heavyLift", "high_domain_max_rpm", "j0-rmax");
		assertTrue(heavyHigh.highNearestSourceDistanceRisk());
		assertTrue(heavyHigh.highFitSourceDistanceRisk());
		assertTrue(heavyHigh.performanceOnlyCoverageRisk());
		assertEquals("HIGH", heavyHigh.reviewPriority());
		assertEquals("nearest-source-distance-high-for-draft-payload", heavyHigh.message());
	}

	@Test
	void negativeCtDraftIsCriticalAndCannotReachLookupExecution() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow row =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.reviewRow(
						"heavyLift", "high_domain_max_rpm", "j1-rmax");

		assertEquals(-0.008978626, row.draftCtCoefficient(), 1.0e-12);
		assertTrue(row.negativeCtRisk());
		assertTrue(row.highNearestSourceDistanceRisk());
		assertTrue(row.performanceOnlyCoverageRisk());
		assertFalse(row.nonnegativeDraftCt());
		assertTrue(row.positiveDraftCp());
		assertTrue(row.etaFormulaConsistent());
		assertEquals("CRITICAL", row.reviewPriority());
		assertEquals("review-negative-ct-draft-before-payload-promotion", row.nextRequiredAction());
		assertEquals("negative-ct-draft-blocks-reviewed-payload", row.message());
		assertFalse(row.draftReviewPassed());
		assertFalse(row.lookupExecutionInputReady());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
	}

	@Test
	void summaryCountsRiskClassesAndKeepsAuthorityClosed() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.audit().summary();

		assertEquals(21, summary.reviewQueueRowCount());
		assertEquals(3, summary.directStaticReviewRowCount());
		assertEquals(18, summary.localIdwReviewRowCount());
		assertEquals(21, summary.manualReviewRequiredCount());
		assertEquals(21, summary.sourceReviewBlockedCount());
		assertEquals(21, summary.coefficientPayloadReviewBlockedCount());
		assertEquals(1, summary.negativeCtRiskCount());
		assertEquals(2, summary.highNearestSourceDistanceRiskCount());
		assertEquals(3, summary.highFitSourceDistanceRiskCount());
		assertEquals(7, summary.performanceOnlyCoverageRiskCount());
		assertEquals(1, summary.criticalPriorityCount());
		assertEquals(3, summary.highPriorityCount());
		assertEquals(15, summary.mediumPriorityCount());
		assertEquals(2, summary.lowPriorityCount());
		assertEquals(0, summary.draftReviewPassedCount());
		assertEquals(0, summary.lookupExecutionInputReadyCount());
		assertEquals(0.15, summary.highNearestSourceDistanceThreshold(), 1.0e-12);
		assertEquals(0.25, summary.highFitSourceDistanceThreshold(), 1.0e-12);
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
	}

	@Test
	void rejectsUnknownReviewQueueTargets() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.reviewRow(
						"cinewhoop", "static_anchor_low_rpm", "static-anchor"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.reviewRow(
						"racingQuad", "mid_domain_mid_rpm", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue
				.CtCpJLookupCoefficientPayloadDraftReviewQueueAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_review_queue_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_review_queue,racingQuad,static_anchor_low_rpm,static-anchor,BLOCKED,review_priority,LOW,priority,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_review_queue,racingQuad,high_domain_max_rpm,j1-rmax,BLOCKED,review_priority,HIGH,priority,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_review_queue,heavyLift,high_domain_max_rpm,j1-rmax,BLOCKED,review_priority,CRITICAL,priority,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_review_queue_summary,all,critical_priority_count,,BLOCKED,critical_priority_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_review_queue_summary,all,lookup_execution_input_ready_count,,BLOCKED,lookup_execution_input_ready_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_review_queue_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
