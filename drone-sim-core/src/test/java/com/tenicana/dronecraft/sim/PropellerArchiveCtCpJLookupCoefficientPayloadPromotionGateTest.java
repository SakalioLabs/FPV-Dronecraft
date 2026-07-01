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

class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGateTest {
	@Test
	void auditDefinesBlockedPromotionGateToReviewedPayloads() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate
				.CtCpJLookupCoefficientPayloadPromotionGateAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("only audit path"));
		assertEquals(48, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(21, audit.promotionGateRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(21, audit.rows().size());
	}

	@Test
	void lowRiskStaticRowsStillCannotPromoteWithoutReviews() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow row =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.promotionRow(
						"racingQuad", "static_anchor_low_rpm", "static-anchor");

		assertEquals("LOW", row.reviewPriority());
		assertEquals("DIRECT_STATIC_SAMPLE_DRAFT", row.draftCoefficientSourceKind());
		assertTrue(row.coefficientPayloadGuardsPassed());
		assertTrue(row.negativeCtRiskResolved());
		assertTrue(row.distanceRiskResolved());
		assertFalse(row.sourceReviewComplete());
		assertFalse(row.coefficientPayloadReviewComplete());
		assertFalse(row.draftReviewPassed());
		assertEquals(3, row.blockerCount());
		assertFalse(row.coefficientPayloadPromotionAllowed());
		assertFalse(row.lookupExecutionInputReady());
		assertEquals("complete-source-review-before-payload-promotion", row.nextRequiredAction());
		assertEquals("source-review-blocks-payload-promotion", row.message());
	}

	@Test
	void highDistanceAndNegativeCtRowsCarryExtraPromotionBlockers() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow racingHigh =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.promotionRow(
						"racingQuad", "high_domain_max_rpm", "j1-rmax");
		assertEquals("HIGH", racingHigh.reviewPriority());
		assertTrue(racingHigh.coefficientPayloadGuardsPassed());
		assertTrue(racingHigh.negativeCtRiskResolved());
		assertFalse(racingHigh.distanceRiskResolved());
		assertEquals(4, racingHigh.blockerCount());
		assertFalse(racingHigh.coefficientPayloadPromotionAllowed());

		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow heavyCritical =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.promotionRow(
						"heavyLift", "high_domain_max_rpm", "j1-rmax");
		assertEquals("CRITICAL", heavyCritical.reviewPriority());
		assertFalse(heavyCritical.coefficientPayloadGuardsPassed());
		assertFalse(heavyCritical.negativeCtRiskResolved());
		assertFalse(heavyCritical.distanceRiskResolved());
		assertTrue(heavyCritical.performanceOnlyCoverage());
		assertEquals(6, heavyCritical.blockerCount());
		assertFalse(heavyCritical.coefficientPayloadPromotionAllowed());
		assertFalse(heavyCritical.fullSimulationPromotionAllowed());
		assertFalse(heavyCritical.performanceOnlyPromotionAllowed());
	}

	@Test
	void summaryKeepsAllPromotionOutputsClosed() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.audit().summary();

		assertEquals(21, summary.promotionGateRowCount());
		assertEquals(21, summary.sourceReviewBlockedCount());
		assertEquals(21, summary.coefficientPayloadReviewBlockedCount());
		assertEquals(21, summary.draftReviewBlockedCount());
		assertEquals(1, summary.coefficientGuardBlockedCount());
		assertEquals(1, summary.negativeCtBlockedCount());
		assertEquals(4, summary.distanceRiskBlockedCount());
		assertEquals(7, summary.performanceOnlyCoverageCount());
		assertEquals(0, summary.coefficientPayloadPromotionAllowedCount());
		assertEquals(0, summary.fullSimulationPromotionAllowedCount());
		assertEquals(0, summary.performanceOnlyPromotionAllowedCount());
		assertEquals(0, summary.lookupExecutionInputReadyCount());
		assertEquals(6, summary.maxBlockerCount());
		assertEquals(1, summary.criticalBlockedCount());
		assertEquals(3, summary.highBlockedCount());
		assertEquals(15, summary.mediumBlockedCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
	}

	@Test
	void currentPromotionGateProducesNoReviewedPayloadRows() {
		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> promoted =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.reviewedPayloadRows();
		assertTrue(promoted.isEmpty());

		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary summary =
				PropellerArchiveCtCpJLookupReviewedCoefficientPayload.review(
						true, promoted, "current-promotion-gate-output");
		assertEquals(21, summary.missingPayloadSlotCount());
		assertFalse(summary.lookupExecutionPayloadReady());
		assertEquals("coefficient-payload-slot-set-incomplete", summary.message());
	}

	@Test
	void rejectsUnknownPromotionTargets() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.promotionRow(
						"cinewhoop", "static_anchor_low_rpm", "static-anchor"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.promotionRow(
						"racingQuad", "mid_domain_mid_rpm", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate
				.CtCpJLookupCoefficientPayloadPromotionGateAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_gate,racingQuad,static_anchor_low_rpm,static-anchor,BLOCKED,coefficient_payload_promotion_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_gate,racingQuad,high_domain_max_rpm,j1-rmax,BLOCKED,coefficient_payload_promotion_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_gate,heavyLift,high_domain_max_rpm,j1-rmax,BLOCKED,coefficient_payload_promotion_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_gate_summary,all,coefficient_payload_promotion_allowed_count,,BLOCKED,coefficient_payload_promotion_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_gate_summary,all,max_blocker_count,,BLOCKED,max_blocker_count,6,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
