package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Review-Handoff-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload review handoff requires both an open worksheet and passing reviewed payload summary before lookup-execution input can be exposed; it is a lab gate only and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int HANDOFF_SCENARIO_ROW_COUNT = 4;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ HANDOFF_SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"complete-coefficient-payload-review-handoff-before-lookup-execution";

	private PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff() {
	}

	public record CoefficientPayloadReviewHandoffScenario(
			String scenarioName,
			String worksheetScenarioName,
			String payloadScenarioName,
			boolean promotionEvidenceComplete,
			boolean fullSimulationEvidenceComplete,
			boolean worksheetReviewWorkOpen,
			boolean sourceRowsReviewed,
			int expectedSlotCount,
			int reviewWorkOpenSlotCount,
			int evidenceBlockedSlotCount,
			int observedPayloadSlotCount,
			int reviewedPayloadSlotCount,
			int coefficientPayloadReadySlotCount,
			int lookupExecutionInputSlotCount,
			int missingPayloadSlotCount,
			int failedPayloadSlotCount,
			boolean allExpectedPayloadSlotsPresent,
			boolean allPayloadsPassed,
			boolean reviewedPayloadHandoffReady,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record CoefficientPayloadReviewHandoffSummary(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxReviewWorkOpenSlotCount,
			int maxObservedPayloadSlotCount,
			int maxReviewedPayloadSlotCount,
			int maxLookupExecutionInputSlotCount,
			int currentReviewWorkOpenSlotCount,
			int currentEvidenceBlockedSlotCount,
			int currentReviewedPayloadSlotCount,
			int currentLookupExecutionInputSlotCount,
			boolean currentReviewedPayloadHandoffReady,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String currentStatus,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupCoefficientPayloadReviewHandoffAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int handoffScenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CoefficientPayloadReviewHandoffScenario> scenarios,
			CoefficientPayloadReviewHandoffSummary summary
	) {
		public CtCpJLookupCoefficientPayloadReviewHandoffAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupCoefficientPayloadReviewHandoffAudit audit() {
		List<PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
				.CoefficientPayloadReviewWorksheetScenario> worksheetScenarios =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.audit().scenarios();
		List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadScenario>
				payloadScenarios = PropellerArchiveCtCpJLookupReviewedCoefficientPayload.audit().scenarios();
		List<CoefficientPayloadReviewHandoffScenario> scenarios = List.of(
				scenario("current_evidence_blocked_no_payload_review",
						"current_no_evidence_submitted",
						"current_no_reviewed_payloads",
						worksheetScenarios,
						payloadScenarios),
				scenario("worksheet_open_payload_unreviewed",
						"synthetic_promotion_evidence_accepted_geometry_pending",
						"reviewed_grid_slots_payload_missing",
						worksheetScenarios,
						payloadScenarios),
				scenario("payload_ready_but_worksheet_blocked",
						"current_no_evidence_submitted",
						"synthetic_all_payloads_reviewed",
						worksheetScenarios,
						payloadScenarios),
				scenario("worksheet_open_payload_ready",
						"synthetic_promotion_evidence_accepted_geometry_pending",
						"synthetic_all_payloads_reviewed",
						worksheetScenarios,
						payloadScenarios)
		);
		return new CtCpJLookupCoefficientPayloadReviewHandoffAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				HANDOFF_SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				summary(scenarios)
		);
	}

	public static CoefficientPayloadReviewHandoffScenario scenario(String scenarioName) {
		return audit().scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload review handoff scenario: " + scenarioName));
	}

	private static CoefficientPayloadReviewHandoffScenario scenario(
			String scenarioName,
			String worksheetScenarioName,
			String payloadScenarioName,
			List<PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
					.CoefficientPayloadReviewWorksheetScenario> worksheetScenarios,
			List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadScenario>
					payloadScenarios
	) {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.CoefficientPayloadReviewWorksheetSummary
				worksheet = worksheet(worksheetScenarios, worksheetScenarioName).summary();
		PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary payload =
				payload(payloadScenarios, payloadScenarioName).summary();
		boolean worksheetOpen = worksheet.reviewWorkOpenSlotCount() == worksheet.expectedSlotCount()
				&& worksheet.ctCpEntryRequiredCount() == worksheet.expectedSlotCount();
		boolean payloadReady = payload.lookupExecutionPayloadReady();
		boolean handoffReady = worksheetOpen && payloadReady;
		return new CoefficientPayloadReviewHandoffScenario(
				scenarioName,
				worksheetScenarioName,
				payloadScenarioName,
				worksheet.promotionEvidenceComplete(),
				worksheet.fullSimulationEvidenceComplete(),
				worksheetOpen,
				payload.sourceRowsReviewed(),
				worksheet.expectedSlotCount(),
				worksheet.reviewWorkOpenSlotCount(),
				worksheet.evidenceBlockedSlotCount(),
				payload.observedPayloadSlotCount(),
				payload.reviewedPayloadSlotCount(),
				payload.coefficientPayloadReadySlotCount(),
				handoffReady ? payload.lookupExecutionInputSlotCount() : 0,
				payload.missingPayloadSlotCount(),
				payload.failedPayloadSlotCount(),
				payload.allExpectedSlotsPresent(),
				payload.allPayloadsPassed(),
				handoffReady,
				handoffReady,
				false,
				false,
				handoffReady ? "READY" : "BLOCKED",
				nextAction(worksheetOpen, payloadReady, worksheet, payload),
				message(worksheetOpen, payloadReady, worksheet, payload)
		);
	}

	private static CoefficientPayloadReviewHandoffSummary summary(
			List<CoefficientPayloadReviewHandoffScenario> scenarios
	) {
		int ready = 0;
		int maxOpen = 0;
		int maxObserved = 0;
		int maxReviewed = 0;
		int maxLookup = 0;
		int runtime = 0;
		int gameplay = 0;
		for (CoefficientPayloadReviewHandoffScenario scenario : scenarios) {
			if (scenario.reviewedPayloadHandoffReady()) {
				ready++;
			}
			maxOpen = Math.max(maxOpen, scenario.reviewWorkOpenSlotCount());
			maxObserved = Math.max(maxObserved, scenario.observedPayloadSlotCount());
			maxReviewed = Math.max(maxReviewed, scenario.reviewedPayloadSlotCount());
			maxLookup = Math.max(maxLookup, scenario.lookupExecutionInputSlotCount());
			if (scenario.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (scenario.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		CoefficientPayloadReviewHandoffScenario current = scenarios.get(0);
		return new CoefficientPayloadReviewHandoffSummary(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxOpen,
				maxObserved,
				maxReviewed,
				maxLookup,
				current.reviewWorkOpenSlotCount(),
				current.evidenceBlockedSlotCount(),
				current.reviewedPayloadSlotCount(),
				current.lookupExecutionInputSlotCount(),
				current.reviewedPayloadHandoffReady(),
				runtime,
				gameplay,
				current.status(),
				current.nextRequiredAction()
		);
	}

	private static PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
			.CoefficientPayloadReviewWorksheetScenario worksheet(
					List<PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet
							.CoefficientPayloadReviewWorksheetScenario> scenarios,
					String scenarioName
			) {
		return scenarios.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"missing CT/CP/J coefficient payload review worksheet scenario: " + scenarioName));
	}

	private static PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadScenario payload(
			List<PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadScenario> scenarios,
			String scenarioName
	) {
		return scenarios.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"missing CT/CP/J reviewed coefficient payload scenario: " + scenarioName));
	}

	private static String nextAction(
			boolean worksheetOpen,
			boolean payloadReady,
			PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.CoefficientPayloadReviewWorksheetSummary
					worksheet,
			PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary payload
	) {
		if (!worksheetOpen) {
			return worksheet.nextRequiredAction();
		}
		if (!payloadReady) {
			return PropellerArchiveCtCpJLookupReviewedCoefficientPayload.NEXT_REQUIRED_ACTION;
		}
		return "feed-reviewed-coefficient-payload-rows-to-lookup-execution-contract";
	}

	private static String message(
			boolean worksheetOpen,
			boolean payloadReady,
			PropellerArchiveCtCpJLookupCoefficientPayloadReviewWorksheet.CoefficientPayloadReviewWorksheetSummary
					worksheet,
			PropellerArchiveCtCpJLookupReviewedCoefficientPayload.ReviewedCoefficientPayloadSummary payload
	) {
		if (!worksheetOpen) {
			return "coefficient payload review worksheet is not open: " + worksheet.firstBlockedSlotKey();
		}
		if (!payloadReady) {
			return "reviewed coefficient payload is not ready: " + payload.message();
		}
		return "reviewed coefficient payload handoff ready for lookup execution contract";
	}
}
