package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Review-Worksheet-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload review worksheet opens manual coefficient-entry work only after promotion evidence clears; it lists required J/RPM slots but never fabricates CT/CP values, lookup execution inputs, runtime coupling, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int WORKSHEET_ROW_COUNT =
			PropellerArchiveCtCpJLookupReviewedGridInput.GRID_INPUT_SLOT_ROW_COUNT;
	public static final int SCENARIO_ROW_COUNT =
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.SCENARIO_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 19;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ WORKSHEET_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"enter-reviewed-ct-cp-values-before-reviewed-payload-binding";

	private PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet() {
	}

	public record CoefficientPayloadReviewWorksheetRow(
			String scenarioName,
			String presetName,
			String caseName,
			String performanceMatchId,
			String slotId,
			String slotKind,
			String slotCoordinateRole,
			double queryAdvanceRatioJ,
			double queryRpm,
			double slotAdvanceRatioJ,
			double slotRpm,
			int minimumPerformanceNeighborRows,
			boolean promotionEvidenceComplete,
			boolean fullSimulationEvidenceComplete,
			boolean reviewWorkAllowed,
			boolean sourceArchiveIdentityRequired,
			boolean ctCpEntryRequired,
			boolean ctCpValuePresent,
			boolean coefficientPayloadReviewed,
			boolean lookupExecutionInputReady,
			boolean fullSimulationLookupAllowed,
			boolean performanceOnlyCoverage,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record CoefficientPayloadReviewWorksheetSummary(
			int expectedSlotCount,
			int reviewWorkOpenSlotCount,
			int evidenceBlockedSlotCount,
			int staticAnchorSlotCount,
			int bilinearCornerSlotCount,
			int highRpmEdgeSlotCount,
			int fullSimulationSlotCount,
			int performanceOnlySlotCount,
			int sourceArchiveIdentityRequiredCount,
			int ctCpEntryRequiredCount,
			int ctCpValuePresentCount,
			int coefficientPayloadReviewedCount,
			int lookupExecutionInputReadyCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			boolean promotionEvidenceComplete,
			boolean fullSimulationEvidenceComplete,
			String firstBlockedSlotKey,
			String nextRequiredAction
	) {
	}

	public record CoefficientPayloadReviewWorksheetScenario(
			String scenarioName,
			List<CoefficientPayloadReviewWorksheetRow> rows,
			CoefficientPayloadReviewWorksheetSummary summary
	) {
		public CoefficientPayloadReviewWorksheetScenario {
			rows = List.copyOf(rows);
		}
	}

	public record CtCpJLookupCoefficientPayloadReviewWorksheetAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int worksheetRowCount,
			int scenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CoefficientPayloadReviewWorksheetRow> currentRows,
			List<CoefficientPayloadReviewWorksheetScenario> scenarios,
			CoefficientPayloadReviewWorksheetSummary currentSummary
	) {
		public CtCpJLookupCoefficientPayloadReviewWorksheetAudit {
			currentRows = List.copyOf(currentRows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupCoefficientPayloadReviewWorksheetAudit audit() {
		List<CoefficientPayloadReviewWorksheetScenario> scenarios =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.audit()
						.scenarios()
						.stream()
						.map(PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet::worksheet)
						.toList();
		CoefficientPayloadReviewWorksheetScenario current = scenarios.get(0);
		return new CtCpJLookupCoefficientPayloadReviewWorksheetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				WORKSHEET_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				current.rows(),
				scenarios,
				current.summary()
		);
	}

	public static CoefficientPayloadReviewWorksheetScenario worksheet(
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceScenario
					evidenceScenario
	) {
		if (evidenceScenario == null) {
			throw new IllegalArgumentException("clearance evidence scenario must not be null.");
		}
		List<CoefficientPayloadReviewWorksheetRow> rows =
				PropellerArchiveCtCpJLookupReviewedGridInput.audit()
						.slots()
						.stream()
						.map(slot -> row(evidenceScenario, slot))
						.toList();
		return new CoefficientPayloadReviewWorksheetScenario(
				evidenceScenario.scenarioName(),
				rows,
				summary(rows)
		);
	}

	public static CoefficientPayloadReviewWorksheetScenario scenario(String scenarioName) {
		return audit().scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload review worksheet scenario: " + scenarioName));
	}

	private static CoefficientPayloadReviewWorksheetRow row(
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceScenario
					evidenceScenario,
			PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot
	) {
		PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger.ClearanceEvidenceSummary
				evidenceSummary = evidenceScenario.summary();
		boolean reviewAllowed = evidenceSummary.reviewedPayloadReviewAllowed();
		boolean performanceOnly = !slot.postReviewFullSimulationLookupAllowed();
		return new CoefficientPayloadReviewWorksheetRow(
				evidenceScenario.scenarioName(),
				slot.presetName(),
				slot.caseName(),
				slot.performanceMatchId(),
				slot.slotId(),
				slot.slotKind(),
				slot.slotCoordinateRole(),
				slot.queryAdvanceRatioJ(),
				slot.queryRpm(),
				slot.slotAdvanceRatioJ(),
				slot.slotRpm(),
				slot.minimumPerformanceNeighborRows(),
				evidenceSummary.promotionEvidenceComplete(),
				evidenceSummary.fullSimulationEvidenceComplete(),
				reviewAllowed,
				true,
				reviewAllowed,
				false,
				false,
				false,
				slot.postReviewFullSimulationLookupAllowed(),
				performanceOnly,
				false,
				false,
				reviewAllowed ? "AWAIT_CT_CP_REVIEW" : "EVIDENCE_BLOCKED",
				reviewAllowed ? NEXT_REQUIRED_ACTION : evidenceSummary.nextRequiredAction(),
				message(reviewAllowed, performanceOnly, evidenceSummary.firstBlockedLane())
		);
	}

	private static CoefficientPayloadReviewWorksheetSummary summary(
			List<CoefficientPayloadReviewWorksheetRow> rows
	) {
		int open = 0;
		int blocked = 0;
		int staticAnchors = 0;
		int bilinear = 0;
		int highEdge = 0;
		int full = 0;
		int performanceOnly = 0;
		int sourceRequired = 0;
		int entryRequired = 0;
		int valuePresent = 0;
		int reviewed = 0;
		int lookup = 0;
		int runtime = 0;
		int gameplay = 0;
		String firstBlocked = "";
		for (CoefficientPayloadReviewWorksheetRow row : rows) {
			if (row.reviewWorkAllowed()) {
				open++;
			} else {
				blocked++;
				if (firstBlocked.isBlank()) {
					firstBlocked = key(row);
				}
			}
			if ("DIRECT_STATIC_ANCHOR_SLOT".equals(row.slotKind())) {
				staticAnchors++;
			}
			if ("BILINEAR_CORNER_SLOT".equals(row.slotKind())) {
				bilinear++;
			}
			if ("HIGH_RPM_EDGE_SLOT".equals(row.slotKind())) {
				highEdge++;
			}
			if (row.fullSimulationLookupAllowed()) {
				full++;
			}
			if (row.performanceOnlyCoverage()) {
				performanceOnly++;
			}
			if (row.sourceArchiveIdentityRequired()) {
				sourceRequired++;
			}
			if (row.ctCpEntryRequired()) {
				entryRequired++;
			}
			if (row.ctCpValuePresent()) {
				valuePresent++;
			}
			if (row.coefficientPayloadReviewed()) {
				reviewed++;
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
		boolean promotionComplete = !rows.isEmpty() && rows.get(0).promotionEvidenceComplete();
		boolean fullSimulationComplete = !rows.isEmpty() && rows.get(0).fullSimulationEvidenceComplete();
		return new CoefficientPayloadReviewWorksheetSummary(
				rows.size(),
				open,
				blocked,
				staticAnchors,
				bilinear,
				highEdge,
				full,
				performanceOnly,
				sourceRequired,
				entryRequired,
				valuePresent,
				reviewed,
				lookup,
				runtime,
				gameplay,
				promotionComplete,
				fullSimulationComplete,
				firstBlocked,
				open == 0
						? PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger
								.NEXT_REQUIRED_ACTION
						: NEXT_REQUIRED_ACTION
		);
	}

	private static String message(boolean reviewAllowed, boolean performanceOnly, String firstEvidenceBlocker) {
		if (!reviewAllowed) {
			return "clearance evidence incomplete before coefficient payload review: " + firstEvidenceBlocker;
		}
		if (performanceOnly) {
			return "review performance CT CP payload while full-simulation geometry coverage remains isolated";
		}
		return "enter reviewed CT CP payload before lookup execution";
	}

	private static String key(CoefficientPayloadReviewWorksheetRow row) {
		return row.presetName() + "/" + row.caseName() + "/" + row.slotId();
	}
}
