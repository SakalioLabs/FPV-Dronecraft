package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Gate-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload promotion gate is the only audit path from draft review queue rows into reviewed coefficient payload rows; current promotion is fully blocked and runtime/gameplay coupling remain closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int PROMOTION_GATE_ROW_COUNT =
			PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.REVIEW_QUEUE_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ PROMOTION_GATE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"complete-source-payload-and-risk-review-before-coefficient-promotion";

	private PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate() {
	}

	public record CoefficientPayloadPromotionGateRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String slotId,
			String reviewPriority,
			String draftCoefficientSourceKind,
			double draftCtCoefficient,
			double draftCpCoefficient,
			double draftEtaAtSlot,
			boolean sourceReviewComplete,
			boolean coefficientPayloadReviewComplete,
			boolean draftReviewPassed,
			boolean coefficientPayloadGuardsPassed,
			boolean negativeCtRiskResolved,
			boolean distanceRiskResolved,
			boolean performanceOnlyCoverage,
			boolean coefficientPayloadPromotionAllowed,
			boolean fullSimulationPromotionAllowed,
			boolean performanceOnlyPromotionAllowed,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			int blockerCount,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record CoefficientPayloadPromotionGateSummary(
			int promotionGateRowCount,
			int sourceReviewBlockedCount,
			int coefficientPayloadReviewBlockedCount,
			int draftReviewBlockedCount,
			int coefficientGuardBlockedCount,
			int negativeCtBlockedCount,
			int distanceRiskBlockedCount,
			int performanceOnlyCoverageCount,
			int coefficientPayloadPromotionAllowedCount,
			int fullSimulationPromotionAllowedCount,
			int performanceOnlyPromotionAllowedCount,
			int lookupExecutionInputReadyCount,
			int maxBlockerCount,
			int criticalBlockedCount,
			int highBlockedCount,
			int mediumBlockedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupCoefficientPayloadPromotionGateAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int promotionGateRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CoefficientPayloadPromotionGateRow> rows,
			CoefficientPayloadPromotionGateSummary summary
	) {
		public CtCpJLookupCoefficientPayloadPromotionGateAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJLookupCoefficientPayloadPromotionGateAudit audit() {
		List<CoefficientPayloadPromotionGateRow> rows =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.audit()
						.rows()
						.stream()
						.map(PropellerArchiveCtCpJLookupCoefficientPayloadPromotionGate::row)
						.toList();
		return new CtCpJLookupCoefficientPayloadPromotionGateAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PROMOTION_GATE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(rows)
		);
	}

	public static CoefficientPayloadPromotionGateRow promotionRow(
			String presetName,
			String caseName,
			String slotId
	) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.caseName().equals(caseName)
						&& row.slotId().equals(slotId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload promotion gate row: "
								+ presetName + " / " + caseName + " / " + slotId));
	}

	public static List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow>
			reviewedPayloadRows() {
		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadRow> promoted =
				new ArrayList<>();
		for (CoefficientPayloadPromotionGateRow row : audit().rows()) {
			if (row.coefficientPayloadPromotionAllowed()) {
				promoted.add(PropellerArchiveCtCpJLookupReviewedCoefficientPayload.reviewedRow(
						PropellerArchiveCtCpJLookupReviewedGridInput.slot(
								row.presetName(), row.caseName(), row.slotId()),
						row.draftCtCoefficient(),
						row.draftCpCoefficient()));
			}
		}
		return List.copyOf(promoted);
	}

	private static CoefficientPayloadPromotionGateRow row(
			PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow queue
	) {
		boolean guardsPassed = queue.finiteDraftCoefficient()
				&& queue.nonnegativeDraftCt()
				&& queue.positiveDraftCp()
				&& queue.etaFormulaConsistent();
		boolean negativeResolved = !queue.negativeCtRisk();
		boolean distanceResolved = !queue.highNearestSourceDistanceRisk() && !queue.highFitSourceDistanceRisk();
		boolean performanceOnly = queue.performanceOnlyCoverageRisk();
		int blockers = blockerCount(queue, guardsPassed, negativeResolved, distanceResolved);
		boolean promotionAllowed = blockers == 0;
		return new CoefficientPayloadPromotionGateRow(
				queue.presetName(),
				queue.caseName(),
				queue.performanceMatchId(),
				queue.slotId(),
				queue.reviewPriority(),
				queue.draftCoefficientSourceKind(),
				queue.draftCtCoefficient(),
				queue.draftCpCoefficient(),
				queue.draftEtaAtSlot(),
				queue.sourceReviewComplete(),
				queue.coefficientPayloadReviewComplete(),
				queue.draftReviewPassed(),
				guardsPassed,
				negativeResolved,
				distanceResolved,
				performanceOnly,
				promotionAllowed,
				promotionAllowed && queue.postReviewFullSimulationLookupAllowed(),
				promotionAllowed && performanceOnly,
				promotionAllowed,
				false,
				false,
				blockers,
				nextRequiredActionFor(queue, guardsPassed, negativeResolved, distanceResolved),
				promotionAllowed ? "READY" : "BLOCKED",
				messageFor(queue, guardsPassed, negativeResolved, distanceResolved)
		);
	}

	private static CoefficientPayloadPromotionGateSummary summary(
			List<CoefficientPayloadPromotionGateRow> rows
	) {
		List<CoefficientPayloadPromotionGateRow> gateRows = List.copyOf(rows);
		int sourceBlocked = 0;
		int payloadBlocked = 0;
		int draftBlocked = 0;
		int guardBlocked = 0;
		int negativeBlocked = 0;
		int distanceBlocked = 0;
		int performanceOnly = 0;
		int promotion = 0;
		int full = 0;
		int performance = 0;
		int lookup = 0;
		int maxBlockers = 0;
		int critical = 0;
		int high = 0;
		int medium = 0;
		int runtime = 0;
		int gameplay = 0;
		for (CoefficientPayloadPromotionGateRow row : gateRows) {
			if (!row.sourceReviewComplete()) {
				sourceBlocked++;
			}
			if (!row.coefficientPayloadReviewComplete()) {
				payloadBlocked++;
			}
			if (!row.draftReviewPassed()) {
				draftBlocked++;
			}
			if (!row.coefficientPayloadGuardsPassed()) {
				guardBlocked++;
			}
			if (!row.negativeCtRiskResolved()) {
				negativeBlocked++;
			}
			if (!row.distanceRiskResolved()) {
				distanceBlocked++;
			}
			if (row.performanceOnlyCoverage()) {
				performanceOnly++;
			}
			if (row.coefficientPayloadPromotionAllowed()) {
				promotion++;
			}
			if (row.fullSimulationPromotionAllowed()) {
				full++;
			}
			if (row.performanceOnlyPromotionAllowed()) {
				performance++;
			}
			if (row.lookupExecutionInputReady()) {
				lookup++;
			}
			maxBlockers = Math.max(maxBlockers, row.blockerCount());
			switch (row.reviewPriority()) {
				case "CRITICAL" -> critical++;
				case "HIGH" -> high++;
				case "MEDIUM" -> medium++;
				case "LOW" -> {
				}
				default -> throw new IllegalStateException("unknown review priority: " + row.reviewPriority());
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new CoefficientPayloadPromotionGateSummary(
				gateRows.size(),
				sourceBlocked,
				payloadBlocked,
				draftBlocked,
				guardBlocked,
				negativeBlocked,
				distanceBlocked,
				performanceOnly,
				promotion,
				full,
				performance,
				lookup,
				maxBlockers,
				critical,
				high,
				medium,
				runtime,
				gameplay
		);
	}

	private static int blockerCount(
			PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow queue,
			boolean guardsPassed,
			boolean negativeResolved,
			boolean distanceResolved
	) {
		int blockers = 0;
		if (!queue.sourceReviewComplete()) {
			blockers++;
		}
		if (!queue.coefficientPayloadReviewComplete()) {
			blockers++;
		}
		if (!queue.draftReviewPassed()) {
			blockers++;
		}
		if (!guardsPassed) {
			blockers++;
		}
		if (!negativeResolved) {
			blockers++;
		}
		if (!distanceResolved) {
			blockers++;
		}
		return blockers;
	}

	private static String nextRequiredActionFor(
			PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow queue,
			boolean guardsPassed,
			boolean negativeResolved,
			boolean distanceResolved
	) {
		if (!queue.sourceReviewComplete()) {
			return "complete-source-review-before-payload-promotion";
		}
		if (!queue.coefficientPayloadReviewComplete()) {
			return "complete-coefficient-payload-review-before-promotion";
		}
		if (!queue.draftReviewPassed()) {
			return queue.nextRequiredAction();
		}
		if (!guardsPassed || !negativeResolved) {
			return "resolve-coefficient-guard-failure-before-promotion";
		}
		if (!distanceResolved) {
			return "resolve-source-distance-risk-before-promotion";
		}
		return "promote-reviewed-coefficient-payload-row";
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue.CoefficientPayloadDraftReviewQueueRow queue,
			boolean guardsPassed,
			boolean negativeResolved,
			boolean distanceResolved
	) {
		if (!queue.sourceReviewComplete()) {
			return "source-review-blocks-payload-promotion";
		}
		if (!queue.coefficientPayloadReviewComplete()) {
			return "coefficient-review-blocks-payload-promotion";
		}
		if (!queue.draftReviewPassed()) {
			return "draft-review-blocks-payload-promotion";
		}
		if (!guardsPassed || !negativeResolved) {
			return "coefficient-guard-blocks-payload-promotion";
		}
		if (!distanceResolved) {
			return "source-distance-risk-blocks-payload-promotion";
		}
		return "reviewed-coefficient-payload-promotion-ready";
	}
}
