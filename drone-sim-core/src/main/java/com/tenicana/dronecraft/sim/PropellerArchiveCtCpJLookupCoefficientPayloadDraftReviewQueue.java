package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Draft-Review-Queue-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload draft review queue turns compact draft coefficients into a blocked manual-review queue; it does not bless draft values, feed lookup execution, or enable runtime/gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int REVIEW_QUEUE_ROW_COUNT =
			PropellerArchiveCtCpJLookupCoefficientPayloadDraft.DRAFT_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_QUEUE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"review-draft-coefficient-risk-queue-before-promoting-payloads";

	private static final double HIGH_NEAREST_SOURCE_DISTANCE = 0.15;
	private static final double HIGH_FIT_SOURCE_DISTANCE = 0.25;

	private PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue() {
	}

	public record CoefficientPayloadDraftReviewQueueRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String slotId,
			String slotKind,
			String draftCoefficientSourceKind,
			double draftCtCoefficient,
			double draftCpCoefficient,
			double draftEtaAtSlot,
			double nearestSourceNormalizedDistance,
			double maxFitSourceNormalizedDistance,
			boolean directStaticSampleUsed,
			boolean postReviewFullSimulationLookupAllowed,
			boolean finiteDraftCoefficient,
			boolean nonnegativeDraftCt,
			boolean positiveDraftCp,
			boolean etaFormulaConsistent,
			boolean negativeCtRisk,
			boolean highNearestSourceDistanceRisk,
			boolean highFitSourceDistanceRisk,
			boolean performanceOnlyCoverageRisk,
			boolean manualReviewRequired,
			boolean sourceReviewComplete,
			boolean coefficientPayloadReviewComplete,
			boolean draftReviewPassed,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String reviewPriority,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record CoefficientPayloadDraftReviewQueueSummary(
			int reviewQueueRowCount,
			int directStaticReviewRowCount,
			int localIdwReviewRowCount,
			int manualReviewRequiredCount,
			int sourceReviewBlockedCount,
			int coefficientPayloadReviewBlockedCount,
			int negativeCtRiskCount,
			int highNearestSourceDistanceRiskCount,
			int highFitSourceDistanceRiskCount,
			int performanceOnlyCoverageRiskCount,
			int criticalPriorityCount,
			int highPriorityCount,
			int mediumPriorityCount,
			int lowPriorityCount,
			int draftReviewPassedCount,
			int lookupExecutionInputReadyCount,
			double highNearestSourceDistanceThreshold,
			double highFitSourceDistanceThreshold,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupCoefficientPayloadDraftReviewQueueAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int reviewQueueRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CoefficientPayloadDraftReviewQueueRow> rows,
			CoefficientPayloadDraftReviewQueueSummary summary
	) {
		public CtCpJLookupCoefficientPayloadDraftReviewQueueAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJLookupCoefficientPayloadDraftReviewQueueAudit audit() {
		List<CoefficientPayloadDraftReviewQueueRow> rows =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraft.audit()
						.rows()
						.stream()
						.map(PropellerArchiveCtCpJLookupCoefficientPayloadDraftReviewQueue::row)
						.toList();
		return new CtCpJLookupCoefficientPayloadDraftReviewQueueAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				REVIEW_QUEUE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(rows)
		);
	}

	public static CoefficientPayloadDraftReviewQueueRow reviewRow(
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
						"unknown CT/CP/J coefficient payload draft review queue row: "
								+ presetName + " / " + caseName + " / " + slotId));
	}

	private static CoefficientPayloadDraftReviewQueueRow row(
			PropellerArchiveCtCpJLookupCoefficientPayloadDraft.CoefficientPayloadDraftRow draft
	) {
		boolean negativeCt = !draft.nonnegativeDraftCt();
		boolean highNearest = draft.nearestSourceNormalizedDistance() > HIGH_NEAREST_SOURCE_DISTANCE;
		boolean highFit = draft.maxFitSourceNormalizedDistance() > HIGH_FIT_SOURCE_DISTANCE;
		boolean performanceOnly = !draft.postReviewFullSimulationLookupAllowed();
		boolean sourceReviewComplete = false;
		boolean payloadReviewComplete = false;
		boolean manualReviewRequired = true;
		boolean reviewPassed = sourceReviewComplete
				&& payloadReviewComplete
				&& !negativeCt
				&& draft.positiveDraftCp()
				&& draft.etaFormulaConsistent()
				&& !highNearest
				&& !highFit;
		String priority = priority(negativeCt, draft.positiveDraftCp(), draft.etaFormulaConsistent(),
				highNearest, highFit, performanceOnly, draft.directStaticSampleUsed());
		return new CoefficientPayloadDraftReviewQueueRow(
				draft.presetName(),
				draft.caseName(),
				draft.performanceMatchId(),
				draft.slotId(),
				draft.slotKind(),
				draft.draftCoefficientSourceKind(),
				draft.draftCtCoefficient(),
				draft.draftCpCoefficient(),
				draft.draftEtaAtSlot(),
				draft.nearestSourceNormalizedDistance(),
				draft.maxFitSourceNormalizedDistance(),
				draft.directStaticSampleUsed(),
				draft.postReviewFullSimulationLookupAllowed(),
				draft.finiteDraftCoefficient(),
				draft.nonnegativeDraftCt(),
				draft.positiveDraftCp(),
				draft.etaFormulaConsistent(),
				negativeCt,
				highNearest,
				highFit,
				performanceOnly,
				manualReviewRequired,
				sourceReviewComplete,
				payloadReviewComplete,
				reviewPassed,
				false,
				false,
				false,
				priority,
				nextRequiredActionFor(priority, negativeCt, highNearest, highFit, performanceOnly),
				reviewPassed ? "READY" : "BLOCKED",
				messageFor(priority, negativeCt, highNearest, highFit, performanceOnly)
		);
	}

	private static CoefficientPayloadDraftReviewQueueSummary summary(
			List<CoefficientPayloadDraftReviewQueueRow> rows
	) {
		List<CoefficientPayloadDraftReviewQueueRow> queueRows = List.copyOf(rows);
		int direct = 0;
		int idw = 0;
		int manual = 0;
		int sourceBlocked = 0;
		int payloadBlocked = 0;
		int negative = 0;
		int highNearest = 0;
		int highFit = 0;
		int performanceOnly = 0;
		int critical = 0;
		int high = 0;
		int medium = 0;
		int low = 0;
		int passed = 0;
		int lookup = 0;
		int runtime = 0;
		int gameplay = 0;
		for (CoefficientPayloadDraftReviewQueueRow row : queueRows) {
			if (row.directStaticSampleUsed()) {
				direct++;
			}
			if ("LOCAL_IDW_8_NEIGHBOR_DRAFT".equals(row.draftCoefficientSourceKind())) {
				idw++;
			}
			if (row.manualReviewRequired()) {
				manual++;
			}
			if (!row.sourceReviewComplete()) {
				sourceBlocked++;
			}
			if (!row.coefficientPayloadReviewComplete()) {
				payloadBlocked++;
			}
			if (row.negativeCtRisk()) {
				negative++;
			}
			if (row.highNearestSourceDistanceRisk()) {
				highNearest++;
			}
			if (row.highFitSourceDistanceRisk()) {
				highFit++;
			}
			if (row.performanceOnlyCoverageRisk()) {
				performanceOnly++;
			}
			switch (row.reviewPriority()) {
				case "CRITICAL" -> critical++;
				case "HIGH" -> high++;
				case "MEDIUM" -> medium++;
				case "LOW" -> low++;
				default -> throw new IllegalStateException("unknown review priority: " + row.reviewPriority());
			}
			if (row.draftReviewPassed()) {
				passed++;
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
		return new CoefficientPayloadDraftReviewQueueSummary(
				queueRows.size(),
				direct,
				idw,
				manual,
				sourceBlocked,
				payloadBlocked,
				negative,
				highNearest,
				highFit,
				performanceOnly,
				critical,
				high,
				medium,
				low,
				passed,
				lookup,
				HIGH_NEAREST_SOURCE_DISTANCE,
				HIGH_FIT_SOURCE_DISTANCE,
				runtime,
				gameplay
		);
	}

	private static String priority(
			boolean negativeCt,
			boolean positiveCp,
			boolean etaConsistent,
			boolean highNearest,
			boolean highFit,
			boolean performanceOnly,
			boolean directStatic
	) {
		if (negativeCt || !positiveCp || !etaConsistent) {
			return "CRITICAL";
		}
		if (highNearest || highFit) {
			return "HIGH";
		}
		if (performanceOnly || !directStatic) {
			return "MEDIUM";
		}
		return "LOW";
	}

	private static String nextRequiredActionFor(
			String priority,
			boolean negativeCt,
			boolean highNearest,
			boolean highFit,
			boolean performanceOnly
	) {
		if ("CRITICAL".equals(priority) && negativeCt) {
			return "review-negative-ct-draft-before-payload-promotion";
		}
		if ("HIGH".equals(priority) && (highNearest || highFit)) {
			return "increase-or-review-local-fit-support-before-payload-promotion";
		}
		if (performanceOnly) {
			return "resolve-geometry-coverage-before-full-simulation-payload-promotion";
		}
		return "manual-review-draft-coefficient-before-payload-promotion";
	}

	private static String messageFor(
			String priority,
			boolean negativeCt,
			boolean highNearest,
			boolean highFit,
			boolean performanceOnly
	) {
		if ("CRITICAL".equals(priority) && negativeCt) {
			return "negative-ct-draft-blocks-reviewed-payload";
		}
		if ("HIGH".equals(priority) && highNearest) {
			return "nearest-source-distance-high-for-draft-payload";
		}
		if ("HIGH".equals(priority) && highFit) {
			return "fit-source-distance-high-for-draft-payload";
		}
		if (performanceOnly) {
			return "performance-only-coverage-blocks-full-simulation-payload";
		}
		return "manual-review-required-before-payload-promotion";
	}
}
