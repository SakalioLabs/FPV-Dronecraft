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

class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedgerTest {
	@Test
	void auditBuildsCurrentEvidenceLedgerFromClearancePlan() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
				.CtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedgerAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Clearance-Evidence-Ledger-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("evaluates whether the clearance-plan evidence artifacts are accepted"));
		assertEquals(38, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(7, audit.evidenceLaneRowCount());
		assertEquals(4, audit.scenarioRowCount());
		assertEquals(19, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(7, audit.currentRows().size());
		assertEquals(4, audit.scenarios().size());
		assertEquals("current_no_evidence_submitted", audit.scenarios().get(0).scenarioName());
	}

	@Test
	void currentLedgerKeepsAllEvidenceMissingAndOutputsClosed() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
				.ClearanceEvidenceSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.audit()
						.currentSummary();

		assertEquals(7, summary.evidenceLaneCount());
		assertEquals(0, summary.submittedEvidenceCount());
		assertEquals(0, summary.acceptedEvidenceArtifactCount());
		assertEquals(0, summary.satisfiedEvidenceLaneCount());
		assertEquals(7, summary.missingEvidenceCount());
		assertEquals(0, summary.rejectedEvidenceCount());
		assertEquals(0, summary.waitingPrerequisiteCount());
		assertEquals(6, summary.requiredPromotionEvidenceLaneCount());
		assertEquals(0, summary.acceptedPromotionEvidenceLaneCount());
		assertEquals(1, summary.requiredFullSimulationEvidenceLaneCount());
		assertEquals(0, summary.acceptedFullSimulationEvidenceLaneCount());
		assertFalse(summary.promotionEvidenceComplete());
		assertFalse(summary.fullSimulationEvidenceComplete());
		assertFalse(summary.reviewedPayloadReviewAllowed());
		assertFalse(summary.lookupExecutionInputReady());
		assertFalse(summary.runtimeCouplingAllowed());
		assertFalse(summary.gameplayAutoApplyAllowed());
		assertEquals("source_review", summary.firstBlockedLane());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				summary.nextRequiredAction());
	}

	@Test
	void prerequisiteChainBlocksAcceptedEvidenceUntilEarlierLanesReady() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceScenario
				scenario = PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.evaluate(
						"payload_review_without_source",
						List.of(PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
								.acceptedDecision("coefficient_payload_review", "payload-review-fingerprint",
										"lab-reviewer")));

		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceLaneRow row =
				scenario.rows().get(1);
		assertEquals("coefficient_payload_review", row.laneName());
		assertTrue(row.evidenceSubmitted());
		assertTrue(row.evidenceAccepted());
		assertFalse(row.prerequisiteEvidenceAccepted());
		assertFalse(row.laneSatisfied());
		assertEquals("WAITING_PREREQUISITE", row.status());
		assertEquals("complete-source_review-before-coefficient_payload_review", row.nextRequiredAction());
		assertEquals(1, scenario.summary().acceptedEvidenceArtifactCount());
		assertEquals(0, scenario.summary().satisfiedEvidenceLaneCount());
		assertEquals(1, scenario.summary().waitingPrerequisiteCount());
		assertEquals("source_review", scenario.summary().firstBlockedLane());
	}

	@Test
	void promotionEvidenceCanCompleteBeforeFullSimulationCoverage() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceScenario
				scenario = PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.audit()
						.scenarios()
						.stream()
						.filter(item -> item.scenarioName()
								.equals("synthetic_promotion_evidence_accepted_geometry_pending"))
						.findFirst()
						.orElseThrow();

		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceSummary
				summary = scenario.summary();
		assertEquals(6, summary.submittedEvidenceCount());
		assertEquals(6, summary.satisfiedEvidenceLaneCount());
		assertEquals(6, summary.acceptedPromotionEvidenceLaneCount());
		assertEquals(0, summary.acceptedFullSimulationEvidenceLaneCount());
		assertTrue(summary.promotionEvidenceComplete());
		assertFalse(summary.fullSimulationEvidenceComplete());
		assertTrue(summary.reviewedPayloadReviewAllowed());
		assertFalse(summary.lookupExecutionInputReady());
		assertEquals("performance_only_geometry_coverage", summary.firstBlockedLane());
	}

	@Test
	void allEvidenceAcceptedStillKeepsLookupExecutionClosedUntilPayloadRowsExist() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceScenario
				scenario = PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.audit()
						.scenarios()
						.stream()
						.filter(item -> item.scenarioName().equals("synthetic_all_clearance_evidence_accepted"))
						.findFirst()
						.orElseThrow();

		assertEquals(7, scenario.summary().satisfiedEvidenceLaneCount());
		assertTrue(scenario.summary().promotionEvidenceComplete());
		assertTrue(scenario.summary().fullSimulationEvidenceComplete());
		assertTrue(scenario.summary().reviewedPayloadReviewAllowed());
		assertFalse(scenario.summary().lookupExecutionInputReady());
		assertEquals("", scenario.summary().firstBlockedLane());
		assertEquals("review-coefficient-payload-values-before-lookup-execution",
				scenario.summary().nextRequiredAction());
	}

	@Test
	void rejectsUnknownAndDuplicateEvidenceLanes() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.evaluate(
						"unknown",
						List.of(PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
								.acceptedDecision("missing", "fingerprint", "lab-reviewer"))));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.evaluate(
						"duplicate",
						List.of(
								PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
										.acceptedDecision("source_review", "fingerprint-a", "lab-reviewer"),
								PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
										.acceptedDecision("source_review", "fingerprint-b", "lab-reviewer"))));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
				.CtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedgerAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_evidence_ledger_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_evidence_lane,current_no_evidence_submitted,1,source_review,SOURCE_REVIEW,MISSING,evidence_lane_state,missing,state,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_evidence_scenario,synthetic_promotion_evidence_accepted_geometry_pending,all,scenario,PROMOTION_EVIDENCE,READY,promotion_evidence_complete,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_evidence_summary,current_no_evidence_submitted,all,summary,summary,BLOCKED,first_blocked_lane,source_review,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_evidence_ledger_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
