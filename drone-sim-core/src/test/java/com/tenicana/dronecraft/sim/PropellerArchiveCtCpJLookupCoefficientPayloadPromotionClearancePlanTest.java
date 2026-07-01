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

class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlanTest {
	@Test
	void auditOrdersPromotionBlockerLanesIntoClearancePlan() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan
				.CtCpJLookupCoefficientPayloadPromotionClearancePlanAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Clearance-Plan-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("orders the blocker lanes"));
		assertEquals(33, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(7, audit.clearancePlanRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(7, audit.rows().size());
		assertEquals("source_review", audit.rows().get(0).laneName());
		assertEquals("performance_only_geometry_coverage", audit.rows().get(6).laneName());
	}

	@Test
	void sourceReviewIsFirstPromotionClearanceLane() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow row =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.planRow("source_review");

		assertEquals(1, row.sequenceIndex());
		assertEquals("none", row.prerequisiteLaneName());
		assertEquals("source-license-review-memo-plus-archive-fingerprint", row.requiredEvidenceArtifact());
		assertEquals(21, row.affectedSlotCount());
		assertEquals(21, row.promotionBlockerIncidenceCleared());
		assertEquals(0, row.fullSimulationCoverageIncidenceCleared());
		assertTrue(row.clearsPromotionBlocker());
		assertFalse(row.clearsFullSimulationCoverageBlocker());
		assertFalse(row.currentSatisfied());
		assertEquals("complete-source-review-before-payload-promotion", row.nextRequiredAction());
		assertEquals("source review blocks every draft payload row", row.message());
	}

	@Test
	void riskAndCoverageLanesKeepSeparateEvidenceRequirements() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow distance =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.planRow("source_distance_risk");
		assertEquals(6, distance.sequenceIndex());
		assertEquals("draft_review", distance.prerequisiteLaneName());
		assertEquals("local-fit-support-expansion-or-revalidation", distance.requiredEvidenceArtifact());
		assertEquals(4, distance.affectedSlotCount());
		assertEquals(4, distance.promotionBlockerIncidenceCleared());
		assertEquals(0, distance.fullSimulationCoverageIncidenceCleared());
		assertEquals(1, distance.criticalPrioritySlotCount());
		assertEquals(3, distance.highPrioritySlotCount());
		assertFalse(distance.reviewedPayloadOutputAllowed());

		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow coverage =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.planRow(
						"performance_only_geometry_coverage");
		assertEquals(7, coverage.sequenceIndex());
		assertEquals("source_review", coverage.prerequisiteLaneName());
		assertEquals("heavy-lift-reviewed-geometry-or-surrogate", coverage.requiredEvidenceArtifact());
		assertEquals(0, coverage.promotionBlockerIncidenceCleared());
		assertEquals(7, coverage.fullSimulationCoverageIncidenceCleared());
		assertFalse(coverage.clearsPromotionBlocker());
		assertTrue(coverage.clearsFullSimulationCoverageBlocker());
	}

	@Test
	void summaryKeepsPlanBlockedAndOutputsClosed() {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.audit().summary();

		assertEquals(7, summary.clearancePlanRowCount());
		assertEquals(6, summary.promotionClearanceLaneCount());
		assertEquals(1, summary.fullSimulationClearanceLaneCount());
		assertEquals(0, summary.currentSatisfiedLaneCount());
		assertEquals(7, summary.blockedLaneCount());
		assertEquals(69, summary.plannedPromotionBlockerIncidenceCleared());
		assertEquals(7, summary.plannedFullSimulationCoverageIncidenceCleared());
		assertEquals(21, summary.maxAffectedSlotCount());
		assertEquals(7, summary.criticalPrioritySlotCount());
		assertEquals(13, summary.highPrioritySlotCount());
		assertEquals(50, summary.mediumPrioritySlotCount());
		assertEquals(6, summary.lowPrioritySlotCount());
		assertEquals(0, summary.reviewedPayloadOutputAllowedCount());
		assertEquals(0, summary.lookupExecutionInputReadyCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("source_review", summary.firstRequiredLane());
		assertEquals("execute-clearance-plan-before-reviewed-payload-output", summary.nextRequiredAction());
	}

	@Test
	void rejectsUnknownClearancePlanLane() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.planRow("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan
				.CtCpJLookupCoefficientPayloadPromotionClearancePlanAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_plan_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_plan,1,source_review,SOURCE_REVIEW,BLOCKED,affected_slot_count,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_plan,6,source_distance_risk,SOURCE_DISTANCE,BLOCKED,affected_slot_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_plan,7,performance_only_geometry_coverage,FULL_SIMULATION_COVERAGE,BLOCKED,affected_slot_count,7,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_plan_summary,all,summary,BLOCKED,planned_promotion_blocker_incidence_cleared,69,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_promotion_clearance_plan_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
