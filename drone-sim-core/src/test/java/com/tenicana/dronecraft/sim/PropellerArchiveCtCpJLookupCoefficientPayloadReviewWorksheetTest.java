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

class PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheetTest {
	@Test
	void auditBuildsCurrentWorksheetFromEvidenceLedgerAndReviewedGridInput() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
				.CtCpJLookupCoefficientPayloadReviewWorksheetAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Review-Worksheet-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("opens manual coefficient-entry work"));
		assertEquals(53, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(21, audit.worksheetRowCount());
		assertEquals(4, audit.scenarioRowCount());
		assertEquals(19, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(21, audit.currentRows().size());
		assertEquals(4, audit.scenarios().size());
		assertEquals("current_no_evidence_submitted", audit.scenarios().get(0).scenarioName());
	}

	@Test
	void currentWorksheetKeepsEverySlotEvidenceBlocked() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
				.CoefficientPayloadReviewWorksheetSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.audit().currentSummary();

		assertEquals(21, summary.expectedSlotCount());
		assertEquals(0, summary.reviewWorkOpenSlotCount());
		assertEquals(21, summary.evidenceBlockedSlotCount());
		assertEquals(3, summary.staticAnchorSlotCount());
		assertEquals(12, summary.bilinearCornerSlotCount());
		assertEquals(6, summary.highRpmEdgeSlotCount());
		assertEquals(14, summary.fullSimulationSlotCount());
		assertEquals(7, summary.performanceOnlySlotCount());
		assertEquals(21, summary.sourceArchiveIdentityRequiredCount());
		assertEquals(0, summary.ctCpEntryRequiredCount());
		assertEquals(0, summary.ctCpValuePresentCount());
		assertEquals(0, summary.coefficientPayloadReviewedCount());
		assertEquals(0, summary.lookupExecutionInputReadyCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertFalse(summary.promotionEvidenceComplete());
		assertFalse(summary.fullSimulationEvidenceComplete());
		assertEquals("racingQuad/static_anchor_low_rpm/static-anchor", summary.firstBlockedSlotKey());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				summary.nextRequiredAction());
	}

	@Test
	void promotionEvidenceAcceptedOpensWorksheetButNotLookupExecution() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
				.CoefficientPayloadReviewWorksheetScenario scenario =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.scenario(
						"synthetic_promotion_evidence_accepted_geometry_pending");
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
				.CoefficientPayloadReviewWorksheetSummary summary = scenario.summary();

		assertEquals(21, summary.reviewWorkOpenSlotCount());
		assertEquals(0, summary.evidenceBlockedSlotCount());
		assertEquals(21, summary.ctCpEntryRequiredCount());
		assertEquals(0, summary.ctCpValuePresentCount());
		assertEquals(0, summary.coefficientPayloadReviewedCount());
		assertEquals(0, summary.lookupExecutionInputReadyCount());
		assertTrue(summary.promotionEvidenceComplete());
		assertFalse(summary.fullSimulationEvidenceComplete());
		assertEquals("", summary.firstBlockedSlotKey());
		assertEquals("enter-reviewed-ct-cp-values-before-reviewed-payload-binding",
				summary.nextRequiredAction());

		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.CoefficientPayloadReviewWorksheetRow row =
				scenario.rows().stream()
						.filter(item -> item.presetName().equals("apDrone")
								&& item.caseName().equals("static_anchor_low_rpm")
								&& item.slotId().equals("static-anchor"))
						.findFirst()
						.orElseThrow();
		assertTrue(row.reviewWorkAllowed());
		assertTrue(row.ctCpEntryRequired());
		assertFalse(row.ctCpValuePresent());
		assertFalse(row.lookupExecutionInputReady());
		assertEquals("AWAIT_CT_CP_REVIEW", row.status());
		assertEquals("DIRECT_STATIC_ANCHOR_SLOT", row.slotKind());
		assertEquals(0.0, row.slotAdvanceRatioJ(), 1.0e-12);
		assertEquals(1477.8, row.slotRpm(), 1.0e-12);
	}

	@Test
	void performanceOnlyRowsRemainMarkedAfterAllEvidenceIsAccepted() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
				.CoefficientPayloadReviewWorksheetScenario scenario =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.scenario(
						"synthetic_all_clearance_evidence_accepted");

		assertTrue(scenario.summary().promotionEvidenceComplete());
		assertTrue(scenario.summary().fullSimulationEvidenceComplete());
		assertEquals(21, scenario.summary().reviewWorkOpenSlotCount());
		assertEquals(7, scenario.summary().performanceOnlySlotCount());
		assertEquals(0, scenario.summary().lookupExecutionInputReadyCount());

		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.CoefficientPayloadReviewWorksheetRow row =
				scenario.rows().stream()
						.filter(item -> item.presetName().equals("heavyLift")
								&& item.caseName().equals("high_domain_max_rpm")
								&& item.slotId().equals("j1-rmax"))
						.findFirst()
						.orElseThrow();
		assertTrue(row.reviewWorkAllowed());
		assertTrue(row.performanceOnlyCoverage());
		assertFalse(row.fullSimulationLookupAllowed());
		assertFalse(row.runtimeCouplingAllowed());
		assertEquals("review performance CT CP payload while full-simulation geometry coverage remains isolated",
				row.message());
	}

	@Test
	void rejectsUnknownWorksheetScenarioAndNullEvidenceScenario() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.worksheet(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
				.CtCpJLookupCoefficientPayloadReviewWorksheetAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_review_worksheet_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_review_worksheet,current_no_evidence_submitted,racingQuad,static_anchor_low_rpm,static-anchor,EVIDENCE_BLOCKED,review_work_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_review_worksheet_scenario,synthetic_promotion_evidence_accepted_geometry_pending,all,summary,,REVIEW_OPEN,review_work_open_slot_count,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_review_worksheet_summary,current_no_evidence_submitted,all,summary,,BLOCKED,lookup_execution_input_ready_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_review_worksheet_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
