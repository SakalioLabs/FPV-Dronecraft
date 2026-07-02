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

class PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoffTest {
	@Test
	void auditCombinesWorksheetAndReviewedPayloadScenarios() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff
				.CtCpJLookupCoefficientPayloadReviewHandoffAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Review-Handoff-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("requires both an open worksheet"));
		assertEquals(28, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(4, audit.handoffScenarioRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.scenarios().size());
		assertEquals("current_evidence_blocked_no_payload_review", audit.scenarios().get(0).scenarioName());
	}

	@Test
	void currentScenarioKeepsPayloadHandoffAndLookupInputClosed() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffScenario current =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.scenario(
						"current_evidence_blocked_no_payload_review");

		assertFalse(current.worksheetReviewWorkOpen());
		assertFalse(current.sourceRowsReviewed());
		assertEquals(21, current.expectedSlotCount());
		assertEquals(0, current.reviewWorkOpenSlotCount());
		assertEquals(21, current.evidenceBlockedSlotCount());
		assertEquals(0, current.observedPayloadSlotCount());
		assertEquals(0, current.reviewedPayloadSlotCount());
		assertEquals(0, current.lookupExecutionInputSlotCount());
		assertFalse(current.reviewedPayloadHandoffReady());
		assertFalse(current.lookupExecutionInputReady());
		assertEquals("BLOCKED", current.status());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				current.nextRequiredAction());
	}

	@Test
	void worksheetOpenButPayloadUnreviewedStillBlocksHandoff() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffScenario scenario =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.scenario(
						"worksheet_open_payload_unreviewed");

		assertTrue(scenario.worksheetReviewWorkOpen());
		assertTrue(scenario.sourceRowsReviewed());
		assertEquals(21, scenario.reviewWorkOpenSlotCount());
		assertEquals(21, scenario.observedPayloadSlotCount());
		assertEquals(0, scenario.reviewedPayloadSlotCount());
		assertEquals(0, scenario.coefficientPayloadReadySlotCount());
		assertEquals(21, scenario.failedPayloadSlotCount());
		assertFalse(scenario.allPayloadsPassed());
		assertFalse(scenario.reviewedPayloadHandoffReady());
		assertEquals("review-and-bind-ct-cp-coefficient-payloads-to-grid-slots-before-lookup-execution",
				scenario.nextRequiredAction());
		assertTrue(scenario.message().contains("coefficient-payload-guard-failed"));
	}

	@Test
	void payloadReadyWithoutWorksheetEvidenceCannotLeakIntoLookupInput() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffScenario scenario =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.scenario(
						"payload_ready_but_worksheet_blocked");

		assertFalse(scenario.worksheetReviewWorkOpen());
		assertTrue(scenario.sourceRowsReviewed());
		assertEquals(21, scenario.reviewedPayloadSlotCount());
		assertEquals(21, scenario.coefficientPayloadReadySlotCount());
		assertTrue(scenario.allPayloadsPassed());
		assertFalse(scenario.reviewedPayloadHandoffReady());
		assertFalse(scenario.lookupExecutionInputReady());
		assertEquals(0, scenario.lookupExecutionInputSlotCount());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				scenario.nextRequiredAction());
	}

	@Test
	void worksheetOpenAndPayloadReadyExposesLookupInputButNoRuntimeAuthority() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffScenario scenario =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.scenario(
						"worksheet_open_payload_ready");

		assertTrue(scenario.worksheetReviewWorkOpen());
		assertTrue(scenario.sourceRowsReviewed());
		assertEquals(21, scenario.reviewWorkOpenSlotCount());
		assertEquals(21, scenario.observedPayloadSlotCount());
		assertEquals(21, scenario.reviewedPayloadSlotCount());
		assertEquals(21, scenario.coefficientPayloadReadySlotCount());
		assertEquals(21, scenario.lookupExecutionInputSlotCount());
		assertTrue(scenario.allExpectedPayloadSlotsPresent());
		assertTrue(scenario.allPayloadsPassed());
		assertTrue(scenario.reviewedPayloadHandoffReady());
		assertTrue(scenario.lookupExecutionInputReady());
		assertFalse(scenario.runtimeCouplingAllowed());
		assertFalse(scenario.gameplayAutoApplyAllowed());
		assertEquals("READY", scenario.status());
	}

	@Test
	void summaryReportsOnlySyntheticReadyScenario() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.audit().summary();

		assertEquals(4, summary.scenarioCount());
		assertEquals(1, summary.readyScenarioCount());
		assertEquals(3, summary.blockedScenarioCount());
		assertEquals(21, summary.maxReviewWorkOpenSlotCount());
		assertEquals(21, summary.maxObservedPayloadSlotCount());
		assertEquals(21, summary.maxReviewedPayloadSlotCount());
		assertEquals(21, summary.maxLookupExecutionInputSlotCount());
		assertEquals(0, summary.currentReviewWorkOpenSlotCount());
		assertEquals(21, summary.currentEvidenceBlockedSlotCount());
		assertEquals(0, summary.currentReviewedPayloadSlotCount());
		assertEquals(0, summary.currentLookupExecutionInputSlotCount());
		assertFalse(summary.currentReviewedPayloadHandoffReady());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("BLOCKED", summary.currentStatus());
	}

	@Test
	void rejectsUnknownHandoffScenario() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff
				.CtCpJLookupCoefficientPayloadReviewHandoffAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_review_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_review_handoff,current_evidence_blocked_no_payload_review,BLOCKED,worksheet_review_work_open,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_review_handoff,worksheet_open_payload_ready,READY,lookup_execution_input_slot_count,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_review_handoff_summary,all,BLOCKED,current_lookup_execution_input_slot_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_review_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
