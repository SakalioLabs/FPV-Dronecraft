package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Clearance-Plan-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload promotion clearance plan orders the blocker lanes that must be cleared before draft coefficients can become reviewed payload rows; it is a review plan only and never enables lookup execution, runtime coupling, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int CLEARANCE_PLAN_ROW_COUNT =
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.BLOCKER_LANE_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ CLEARANCE_PLAN_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"execute-clearance-plan-before-reviewed-payload-output";

	private PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan() {
	}

	public record PromotionClearancePlanRow(
			int sequenceIndex,
			String laneName,
			String blockerKind,
			String prerequisiteLaneName,
			String requiredEvidenceArtifact,
			String acceptanceGate,
			int affectedSlotCount,
			int promotionBlockerIncidenceCleared,
			int fullSimulationCoverageIncidenceCleared,
			int criticalPrioritySlotCount,
			int highPrioritySlotCount,
			int mediumPrioritySlotCount,
			int lowPrioritySlotCount,
			boolean currentSatisfied,
			boolean clearsPromotionBlocker,
			boolean clearsFullSimulationCoverageBlocker,
			boolean reviewedPayloadOutputAllowed,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PromotionClearancePlanSummary(
			int clearancePlanRowCount,
			int promotionClearanceLaneCount,
			int fullSimulationClearanceLaneCount,
			int currentSatisfiedLaneCount,
			int blockedLaneCount,
			int plannedPromotionBlockerIncidenceCleared,
			int plannedFullSimulationCoverageIncidenceCleared,
			int maxAffectedSlotCount,
			int criticalPrioritySlotCount,
			int highPrioritySlotCount,
			int mediumPrioritySlotCount,
			int lowPrioritySlotCount,
			int reviewedPayloadOutputAllowedCount,
			int lookupExecutionInputReadyCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String firstRequiredLane,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupCoefficientPayloadPromotionClearancePlanAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int clearancePlanRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<PromotionClearancePlanRow> rows,
			PromotionClearancePlanSummary summary
	) {
		public CtCpJLookupCoefficientPayloadPromotionClearancePlanAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJLookupCoefficientPayloadPromotionClearancePlanAudit audit() {
		List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane> lanes =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.audit().lanes();
		List<PromotionClearancePlanRow> rows = List.of(
				row(1, lane(lanes, "source_review"), "none",
						"source-license-review-memo-plus-archive-fingerprint",
						"archive identity license and raw-row use approved for offline coefficient review"),
				row(2, lane(lanes, "coefficient_payload_review"), "source_review",
						"reviewed-ct-cp-payload-rowset",
						"all 21 coefficient payload slots reviewed against source identity and slot keys"),
				row(3, lane(lanes, "draft_review"), "coefficient_payload_review",
						"draft-risk-review-signoff",
						"draft review queue priority items closed or explicitly rejected"),
				row(4, lane(lanes, "coefficient_guard"), "draft_review",
						"coefficient-guard-resolution-record",
						"finite nonnegative CT positive CP and eta consistency accepted for promoted rows"),
				row(5, lane(lanes, "negative_ct_risk"), "coefficient_guard",
						"heavy-lift-high-j-negative-ct-review",
						"negative CT row corrected rejected or isolated from payload promotion"),
				row(6, lane(lanes, "source_distance_risk"), "draft_review",
						"local-fit-support-expansion-or-revalidation",
						"distant source support rows refit with accepted stencil or rejected from promotion"),
				row(7, lane(lanes, "performance_only_geometry_coverage"), "source_review",
						"heavy-lift-reviewed-geometry-or-surrogate",
						"heavyLift performance-only rows receive reviewed geometry before full simulation evidence")
		);
		return new CtCpJLookupCoefficientPayloadPromotionClearancePlanAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				CLEARANCE_PLAN_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(rows)
		);
	}

	public static PromotionClearancePlanRow planRow(String laneName) {
		return audit().rows()
				.stream()
				.filter(row -> row.laneName().equals(laneName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload promotion clearance lane: " + laneName));
	}

	private static PromotionClearancePlanRow row(
			int sequenceIndex,
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane lane,
			String prerequisiteLaneName,
			String requiredEvidenceArtifact,
			String acceptanceGate
	) {
		boolean satisfied = lane.affectedSlotCount() == 0;
		return new PromotionClearancePlanRow(
				sequenceIndex,
				lane.laneName(),
				lane.blockerKind(),
				prerequisiteLaneName,
				requiredEvidenceArtifact,
				acceptanceGate,
				lane.affectedSlotCount(),
				lane.promotionBlockingSlotCount(),
				lane.fullSimulationBlockingSlotCount(),
				lane.criticalPrioritySlotCount(),
				lane.highPrioritySlotCount(),
				lane.mediumPrioritySlotCount(),
				lane.lowPrioritySlotCount(),
				satisfied,
				lane.promotionBlocker(),
				lane.fullSimulationCoverageBlocker(),
				false,
				false,
				false,
				false,
				satisfied ? "advance-to-next-clearance-lane" : lane.nextRequiredAction(),
				satisfied ? "READY" : "BLOCKED",
				satisfied ? "clearance-lane-satisfied" : lane.message()
		);
	}

	private static PromotionClearancePlanSummary summary(List<PromotionClearancePlanRow> rows) {
		List<PromotionClearancePlanRow> planRows = List.copyOf(rows);
		int promotion = 0;
		int full = 0;
		int satisfied = 0;
		int blocked = 0;
		int promotionIncidence = 0;
		int fullIncidence = 0;
		int maxAffected = 0;
		int critical = 0;
		int high = 0;
		int medium = 0;
		int low = 0;
		int output = 0;
		int lookup = 0;
		int runtime = 0;
		int gameplay = 0;
		String firstRequired = "";
		for (PromotionClearancePlanRow row : planRows) {
			if (row.clearsPromotionBlocker()) {
				promotion++;
				promotionIncidence += row.promotionBlockerIncidenceCleared();
			}
			if (row.clearsFullSimulationCoverageBlocker()) {
				full++;
				fullIncidence += row.fullSimulationCoverageIncidenceCleared();
			}
			if (row.currentSatisfied()) {
				satisfied++;
			} else {
				blocked++;
				if (firstRequired.isBlank()) {
					firstRequired = row.laneName();
				}
			}
			maxAffected = Math.max(maxAffected, row.affectedSlotCount());
			critical += row.criticalPrioritySlotCount();
			high += row.highPrioritySlotCount();
			medium += row.mediumPrioritySlotCount();
			low += row.lowPrioritySlotCount();
			if (row.reviewedPayloadOutputAllowed()) {
				output++;
			}
			if (row.lookupExecutionInputReady()) {
				lookup++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new PromotionClearancePlanSummary(
				planRows.size(),
				promotion,
				full,
				satisfied,
				blocked,
				promotionIncidence,
				fullIncidence,
				maxAffected,
				critical,
				high,
				medium,
				low,
				output,
				lookup,
				runtime,
				gameplay,
				firstRequired,
				NEXT_REQUIRED_ACTION
		);
	}

	private static PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane lane(
			List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionBlockerReport.PromotionBlockerLane> lanes,
			String laneName
	) {
		return lanes.stream()
				.filter(lane -> lane.laneName().equals(laneName))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("missing blocker lane for clearance plan: " + laneName));
	}
}
