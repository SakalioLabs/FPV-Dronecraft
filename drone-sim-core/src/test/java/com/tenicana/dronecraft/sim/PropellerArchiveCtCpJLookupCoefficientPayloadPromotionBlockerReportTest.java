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

class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReportTest {
	@Test
	void auditAggregatesPromotionBlockerLanes() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport
				.CtCpJLookupCoefficientPayloadPromotionBlockerReportAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Blocker-Report-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("aggregates why draft coefficients cannot become reviewed payload rows"));
		assertEquals(33, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(7, audit.blockerLaneRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(7, audit.lanes().size());
	}

	@Test
	void globalReviewLanesBlockEverySlot() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane source =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.lane("source_review");
		assertEquals("SOURCE_REVIEW", source.blockerKind());
		assertEquals(21, source.affectedSlotCount());
		assertEquals(21, source.promotionBlockingSlotCount());
		assertEquals(1, source.criticalPrioritySlotCount());
		assertEquals(3, source.highPrioritySlotCount());
		assertEquals(15, source.mediumPrioritySlotCount());
		assertEquals(2, source.lowPrioritySlotCount());
		assertEquals(6, source.maxBlockerCount());
		assertTrue(source.promotionBlocker());
		assertFalse(source.fullSimulationCoverageBlocker());
		assertEquals("complete-source-review-before-payload-promotion", source.nextRequiredAction());

		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane payload =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.lane(
						"coefficient_payload_review");
		assertEquals(21, payload.affectedSlotCount());
		assertEquals("PAYLOAD_REVIEW", payload.blockerKind());
	}

	@Test
	void riskLanesPreserveNegativeCtAndDistanceDiagnostics() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane negative =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.lane("negative_ct_risk");
		assertEquals(1, negative.affectedSlotCount());
		assertEquals(1, negative.criticalPrioritySlotCount());
		assertEquals(6, negative.maxBlockerCount());
		assertEquals("review-negative-ct-draft-before-payload-promotion", negative.nextRequiredAction());

		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane distance =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.lane("source_distance_risk");
		assertEquals(4, distance.affectedSlotCount());
		assertEquals(4, distance.promotionBlockingSlotCount());
		assertEquals(1, distance.criticalPrioritySlotCount());
		assertEquals(3, distance.highPrioritySlotCount());
		assertEquals("resolve-source-distance-risk-before-promotion", distance.nextRequiredAction());
	}

	@Test
	void fullSimulationCoverageLaneIsSeparatedFromPayloadPromotionBlockers() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane coverage =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.lane(
						"performance_only_geometry_coverage");

		assertEquals("FULL_SIMULATION_COVERAGE", coverage.blockerKind());
		assertEquals(7, coverage.affectedSlotCount());
		assertEquals(0, coverage.promotionBlockingSlotCount());
		assertEquals(7, coverage.fullSimulationBlockingSlotCount());
		assertFalse(coverage.promotionBlocker());
		assertTrue(coverage.fullSimulationCoverageBlocker());
		assertEquals(1, coverage.criticalPrioritySlotCount());
		assertEquals(1, coverage.highPrioritySlotCount());
		assertEquals(5, coverage.mediumPrioritySlotCount());
		assertEquals("performance-only heavyLift rows cannot become full simulation evidence yet",
				coverage.message());
	}

	@Test
	void summaryKeepsReviewedPayloadOutputClosed() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.audit().summary();

		assertEquals(7, summary.blockerLaneCount());
		assertEquals(7, summary.activeBlockerLaneCount());
		assertEquals(6, summary.promotionBlockingLaneCount());
		assertEquals(1, summary.fullSimulationBlockingLaneCount());
		assertEquals(69, summary.totalPromotionBlockerIncidenceCount());
		assertEquals(7, summary.totalFullSimulationCoverageIncidenceCount());
		assertEquals(21, summary.maxAffectedSlotCount());
		assertEquals(6, summary.maxBlockerCount());
		assertEquals(21, summary.sourceReviewBlockedSlotCount());
		assertEquals(21, summary.coefficientPayloadReviewBlockedSlotCount());
		assertEquals(21, summary.draftReviewBlockedSlotCount());
		assertEquals(1, summary.coefficientGuardBlockedSlotCount());
		assertEquals(1, summary.negativeCtBlockedSlotCount());
		assertEquals(4, summary.sourceDistanceBlockedSlotCount());
		assertEquals(7, summary.performanceOnlyCoverageSlotCount());
		assertEquals(0, summary.promotionAllowedSlotCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
	}

	@Test
	void rejectsUnknownBlockerLane() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.lane("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport
				.CtCpJLookupCoefficientPayloadPromotionBlockerReportAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_blocker_lane,source_review,SOURCE_REVIEW,BLOCKED,affected_slot_count,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_blocker_lane,source_distance_risk,SOURCE_DISTANCE,BLOCKED,affected_slot_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_blocker_lane,performance_only_geometry_coverage,FULL_SIMULATION_COVERAGE,BLOCKED,affected_slot_count,7,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_blocker_summary,all,summary,BLOCKED,total_promotion_blocker_incidence_count,69,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
