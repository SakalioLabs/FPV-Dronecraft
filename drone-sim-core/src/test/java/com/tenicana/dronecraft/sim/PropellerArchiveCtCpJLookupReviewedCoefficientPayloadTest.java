package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJLookupReviewedCoefficientPayloadTest {
	@Test
	void auditDefinesCoefficientPayloadBoundaryWithoutImportingRows() {
		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.CtCpJLookupReviewedCoefficientPayloadAudit audit =
				PropellerArchiveCtCpJLookupReviewedCoefficientPayload.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Reviewed-Coefficient-Payload-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("reviewed CT/CP values"));
		assertEquals(75, audit.packetRowCount());
		assertEquals(10, audit.sourceReferenceRowCount());
		assertEquals(23, audit.payloadFieldRowCount());
		assertEquals(21, audit.payloadSlotRowCount());
		assertEquals(4, audit.scenarioRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(23, audit.fields().size());
		assertEquals(21, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow current =
				audit.rows().get(0);
		assertEquals("racingQuad", current.presetName());
		assertEquals("static_anchor_low_rpm", current.caseName());
		assertFalse(current.sourceGridSlotReady());
		assertFalse(current.coefficientPayloadReviewed());
		assertTrue(current.finiteCoefficientPayload());
		assertFalse(current.positiveCpPayload());
		assertFalse(current.coefficientPayloadReady());
		assertFalse(current.lookupExecutionInputReady());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("reviewed-grid-slot-not-ready", current.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(21, audit.extrema().maxObservedPayloadSlotCount());
		assertEquals(21, audit.extrema().maxReviewedPayloadSlotCount());
		assertEquals(21, audit.extrema().maxLookupExecutionInputSlotCount());
		assertEquals(21, audit.extrema().maxMissingPayloadSlotCount());
		assertEquals(21, audit.extrema().maxFailedPayloadSlotCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void reviewScenariosSeparateMissingPayloadsFromReadyPayloads() {
		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.CtCpJLookupReviewedCoefficientPayloadAudit audit =
				PropellerArchiveCtCpJLookupReviewedCoefficientPayload.audit();

		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary current =
				scenario(audit, "current_no_reviewed_payloads").summary();
		assertFalse(current.sourceRowsReviewed());
		assertEquals(21, current.expectedSlotCount());
		assertEquals(0, current.observedPayloadSlotCount());
		assertEquals(21, current.missingPayloadSlotCount());
		assertEquals("source-license-review-required", current.message());
		assertFalse(current.lookupExecutionPayloadReady());

		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary missing =
				scenario(audit, "reviewed_grid_slots_payload_missing").summary();
		assertTrue(missing.sourceRowsReviewed());
		assertEquals(21, missing.observedPayloadSlotCount());
		assertEquals(0, missing.reviewedPayloadSlotCount());
		assertEquals(0, missing.positiveCpPayloadSlotCount());
		assertEquals(0, missing.lookupExecutionInputSlotCount());
		assertEquals(21, missing.failedPayloadSlotCount());
		assertEquals("coefficient-payload-guard-failed", missing.message());

		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary ready =
				scenario(audit, "synthetic_all_payloads_reviewed").summary();
		assertTrue(ready.sourceRowsReviewed());
		assertEquals(21, ready.reviewedPayloadSlotCount());
		assertEquals(21, ready.finiteCoefficientPayloadSlotCount());
		assertEquals(21, ready.nonnegativeCtPayloadSlotCount());
		assertEquals(21, ready.positiveCpPayloadSlotCount());
		assertEquals(21, ready.etaConsistentPayloadSlotCount());
		assertEquals(21, ready.lookupExecutionInputSlotCount());
		assertTrue(ready.allExpectedSlotsPresent());
		assertTrue(ready.allPayloadsPassed());
		assertTrue(ready.lookupExecutionPayloadReady());
		assertEquals("reviewed-coefficient-payload-ready", ready.message());

		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary failed =
				scenario(audit, "synthetic_one_payload_failed_cp").summary();
		assertEquals(21, failed.reviewedPayloadSlotCount());
		assertEquals(20, failed.positiveCpPayloadSlotCount());
		assertEquals(20, failed.lookupExecutionInputSlotCount());
		assertEquals(1, failed.failedPayloadSlotCount());
		assertFalse(failed.lookupExecutionPayloadReady());
	}

	@Test
	void reviewedPayloadRowsConvertToLookupExecutionRows() {
		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> rows =
				passingRows();

		List<PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow> lookupRows =
				PropellerArchiveCtCpJLookupReviewedCoefficientPayload.lookupRows(
						rows, "apDrone", "mid_domain_mid_rpm");
		assertEquals(4, lookupRows.size());
		assertEquals("j0-r0", lookupRows.get(0).rowId());
		assertEquals(0.32512, lookupRows.get(0).advanceRatioJ(), 1.0e-12);
		assertEquals(4065.36, lookupRows.get(0).rpm(), 1.0e-9);
		assertTrue(lookupRows.stream().allMatch(row -> row.cpCoefficient() > 0.0));

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult result =
				PropellerArchiveCtCpJLookupExecutionContract.execute(
						lookupRows, "apDrone", "mid_domain_mid_rpm");
		assertTrue(result.acceptedByLookupGate());
		assertEquals(4, result.observedNeighborRows());
		assertEquals("ACCEPTED", result.status());
	}

	@Test
	void payloadReviewRejectsMissingUnreadyDuplicateAndUnknownRows() {
		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> rows =
				passingRows();
		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> missing =
				new ArrayList<>(rows);
		missing.remove(0);
		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary missingSummary =
				PropellerArchiveCtCpJLookupReviewedCoefficientPayload.review(
						true, missing, "missing-one-payload");
		assertEquals(1, missingSummary.missingPayloadSlotCount());
		assertEquals("coefficient-payload-slot-set-incomplete", missingSummary.message());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedCoefficientPayload.lookupRows(
						PropellerArchiveCtCpJLookupReviewedCoefficientPayload.audit().rows(),
						"apDrone", "mid_domain_mid_rpm"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedCoefficientPayload.lookupRows(
						rows, "cinewhoop", "mid_domain_mid_rpm"));

		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> duplicate =
				new ArrayList<>(rows);
		duplicate.set(1, duplicate.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedCoefficientPayload.review(
						true, duplicate, "duplicate-payload"));

		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> duplicateTarget =
				new ArrayList<>(rows);
		duplicateTarget.set(9, duplicateTarget.get(8));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedCoefficientPayload.lookupRows(
						duplicateTarget, "apDrone", "mid_domain_mid_rpm"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedCoefficientPayload.review(
						true, null, "missing-payload-list"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedCoefficientPayload.review(
						true, rows, ""));
	}

	@Test
	void fieldsAndCsvPacketStayAligned() throws IOException {
		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadField cp =
				PropellerArchiveCtCpJLookupReviewedCoefficientPayload.field("cp_coefficient");
		assertEquals("coefficient", cp.unit());
		assertTrue(cp.downstreamUse().contains("power coefficient"));
		assertFalse(cp.runtimeCouplingAllowed());
		assertFalse(cp.gameplayAutoApplyAllowed());
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedCoefficientPayload.field("missing"));

		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.CtCpJLookupReviewedCoefficientPayloadAudit audit =
				PropellerArchiveCtCpJLookupReviewedCoefficientPayload.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_reviewed_coefficient_payload_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_coefficient_payload_source,reviewed_grid_input,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_coefficient_payload_slot,apDrone,mid_domain_mid_rpm,j0-r0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_coefficient_payload_scenario,synthetic_all_payloads_reviewed,scenario,,READY,lookup_execution_payload_ready,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_coefficient_payload_summary,all,summary,,BLOCKED,max_lookup_execution_input_slot_count,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_coefficient_payload_summary,all,summary,,BLOCKED,runtime_coupling_allowed_count,0,count,")));
	}

	private static List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> passingRows() {
		List<PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot> slots =
				PropellerArchiveCtCpJLookupReviewedGridInput.audit().slots();
		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> rows =
				new ArrayList<>();
		for (int i = 0; i < slots.size(); i++) {
			rows.add(PropellerArchiveCtCpJLookupReviewedCoefficientPayload.reviewedRow(
					slots.get(i), 0.120 - 0.001 * i, 0.040 + 0.001 * i));
		}
		return rows;
	}

	private static PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadScenario scenario(
			PropellerArchiveCtCpJLookupReviewedCoefficientPayload.CtCpJLookupReviewedCoefficientPayloadAudit audit,
			String name
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_reviewed_coefficient_payload_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
