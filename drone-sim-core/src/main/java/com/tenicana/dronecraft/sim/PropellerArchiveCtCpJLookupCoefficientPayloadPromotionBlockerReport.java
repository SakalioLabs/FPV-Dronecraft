package com.tenicana.dronecraft.sim;

import java.util.List;
import java.util.function.Predicate;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Blocker-Report-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload promotion blocker report aggregates why draft coefficients cannot become reviewed payload rows; it reports remediation lanes only and never enables lookup execution, runtime coupling, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int BLOCKER_LANE_ROW_COUNT = 7;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ BLOCKER_LANE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"clear-promotion-blocker-lanes-before-reviewed-payload-output";

	private PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport() {
	}

	public record PromotionBlockerLane(
			String laneName,
			String blockerKind,
			int affectedSlotCount,
			int promotionBlockingSlotCount,
			int fullSimulationBlockingSlotCount,
			int criticalPrioritySlotCount,
			int highPrioritySlotCount,
			int mediumPrioritySlotCount,
			int lowPrioritySlotCount,
			int maxBlockerCount,
			boolean promotionBlocker,
			boolean fullSimulationCoverageBlocker,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PromotionBlockerSummary(
			int blockerLaneCount,
			int activeBlockerLaneCount,
			int promotionBlockingLaneCount,
			int fullSimulationBlockingLaneCount,
			int totalPromotionBlockerIncidenceCount,
			int totalFullSimulationCoverageIncidenceCount,
			int maxAffectedSlotCount,
			int maxBlockerCount,
			int sourceReviewBlockedSlotCount,
			int coefficientPayloadReviewBlockedSlotCount,
			int draftReviewBlockedSlotCount,
			int coefficientGuardBlockedSlotCount,
			int negativeCtBlockedSlotCount,
			int sourceDistanceBlockedSlotCount,
			int performanceOnlyCoverageSlotCount,
			int promotionAllowedSlotCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupCoefficientPayloadPromotionBlockerReportAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int blockerLaneRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<PromotionBlockerLane> lanes,
			PromotionBlockerSummary summary
	) {
		public CtCpJLookupCoefficientPayloadPromotionBlockerReportAudit {
			lanes = List.copyOf(lanes);
		}
	}

	public static CtCpJLookupCoefficientPayloadPromotionBlockerReportAudit audit() {
		List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow> rows =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.audit().rows();
		List<PromotionBlockerLane> lanes = List.of(
				lane("source_review", "SOURCE_REVIEW", rows,
						row -> !row.sourceReviewComplete(), true, false,
						"complete-source-review-before-payload-promotion",
						"source review blocks every draft payload row"),
				lane("coefficient_payload_review", "PAYLOAD_REVIEW", rows,
						row -> !row.coefficientPayloadReviewComplete(), true, false,
						"complete-coefficient-payload-review-before-promotion",
						"coefficient payload review is still closed for every slot"),
				lane("draft_review", "DRAFT_REVIEW", rows,
						row -> !row.draftReviewPassed(), true, false,
						"review-draft-coefficient-risk-queue-before-promoting-payloads",
						"draft review has not passed for any candidate"),
				lane("coefficient_guard", "COEFFICIENT_GUARD", rows,
						row -> !row.coefficientPayloadGuardsPassed(), true, false,
						"resolve-coefficient-guard-failure-before-promotion",
						"coefficient guard rejects nonphysical CT CP eta draft rows"),
				lane("negative_ct_risk", "NEGATIVE_CT", rows,
						row -> !row.negativeCtRiskResolved(), true, false,
						"review-negative-ct-draft-before-payload-promotion",
						"negative CT draft must be resolved before payload promotion"),
				lane("source_distance_risk", "SOURCE_DISTANCE", rows,
						row -> !row.distanceRiskResolved(), true, false,
						"resolve-source-distance-risk-before-promotion",
						"local fit source support is too distant for automatic promotion"),
				lane("performance_only_geometry_coverage", "FULL_SIMULATION_COVERAGE", rows,
						PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate
								.CoefficientPayloadPromotionGateRow::performanceOnlyCoverage,
						false, true,
						"resolve-geometry-coverage-before-full-simulation-payload-promotion",
						"performance-only heavyLift rows cannot become full simulation evidence yet")
		);
		return new CtCpJLookupCoefficientPayloadPromotionBlockerReportAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				BLOCKER_LANE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				lanes,
				summary(rows, lanes)
		);
	}

	public static PromotionBlockerLane lane(String laneName) {
		return audit().lanes()
				.stream()
				.filter(lane -> lane.laneName().equals(laneName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload promotion blocker lane: " + laneName));
	}

	private static PromotionBlockerLane lane(
			String laneName,
			String blockerKind,
			List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow> rows,
			Predicate<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow>
					predicate,
			boolean promotionBlocker,
			boolean fullSimulationCoverageBlocker,
			String nextRequiredAction,
			String message
	) {
		int affected = 0;
		int critical = 0;
		int high = 0;
		int medium = 0;
		int low = 0;
		int maxBlockers = 0;
		for (PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow row
				: rows) {
			if (predicate.test(row)) {
				affected++;
				maxBlockers = Math.max(maxBlockers, row.blockerCount());
				switch (row.reviewPriority()) {
					case "CRITICAL" -> critical++;
					case "HIGH" -> high++;
					case "MEDIUM" -> medium++;
					case "LOW" -> low++;
					default -> throw new IllegalStateException("unknown review priority: " + row.reviewPriority());
				}
			}
		}
		return new PromotionBlockerLane(
				laneName,
				blockerKind,
				affected,
				promotionBlocker ? affected : 0,
				fullSimulationCoverageBlocker ? affected : 0,
				critical,
				high,
				medium,
				low,
				maxBlockers,
				promotionBlocker,
				fullSimulationCoverageBlocker,
				false,
				false,
				nextRequiredAction,
				affected == 0 ? "READY" : "BLOCKED",
				message
		);
	}

	private static PromotionBlockerSummary summary(
			List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow> rows,
			List<PromotionBlockerLane> lanes
	) {
		int active = 0;
		int promotionLanes = 0;
		int fullLanes = 0;
		int promotionIncidence = 0;
		int fullIncidence = 0;
		int maxAffected = 0;
		int maxBlockers = 0;
		int runtime = 0;
		int gameplay = 0;
		for (PromotionBlockerLane lane : lanes) {
			if (lane.affectedSlotCount() > 0) {
				active++;
			}
			if (lane.promotionBlocker()) {
				promotionLanes++;
				promotionIncidence += lane.promotionBlockingSlotCount();
			}
			if (lane.fullSimulationCoverageBlocker()) {
				fullLanes++;
				fullIncidence += lane.fullSimulationBlockingSlotCount();
			}
			maxAffected = Math.max(maxAffected, lane.affectedSlotCount());
			maxBlockers = Math.max(maxBlockers, lane.maxBlockerCount());
			if (lane.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (lane.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new PromotionBlockerSummary(
				lanes.size(),
				active,
				promotionLanes,
				fullLanes,
				promotionIncidence,
				fullIncidence,
				maxAffected,
				maxBlockers,
				count(rows, row -> !row.sourceReviewComplete()),
				count(rows, row -> !row.coefficientPayloadReviewComplete()),
				count(rows, row -> !row.draftReviewPassed()),
				count(rows, row -> !row.coefficientPayloadGuardsPassed()),
				count(rows, row -> !row.negativeCtRiskResolved()),
				count(rows, row -> !row.distanceRiskResolved()),
				count(rows, PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate
						.CoefficientPayloadPromotionGateRow::performanceOnlyCoverage),
				count(rows, PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate
						.CoefficientPayloadPromotionGateRow::coefficientPayloadPromotionAllowed),
				runtime,
				gameplay
		);
	}

	private static int count(
			List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow> rows,
			Predicate<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow>
					predicate
	) {
		int count = 0;
		for (PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate.CoefficientPayloadPromotionGateRow row
				: rows) {
			if (predicate.test(row)) {
				count++;
			}
		}
		return count;
	}
}
