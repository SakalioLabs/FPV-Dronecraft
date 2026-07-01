package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Promotion-Clearance-Evidence-Ledger-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload promotion clearance evidence ledger evaluates whether the clearance-plan evidence artifacts are accepted before payload review can proceed; it never creates CT/CP values, lookup execution inputs, runtime coupling, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int EVIDENCE_LANE_ROW_COUNT =
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.CLEARANCE_PLAN_ROW_COUNT;
	public static final int SCENARIO_ROW_COUNT = 4;
	public static final int SUMMARY_ROW_COUNT = 19;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ EVIDENCE_LANE_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"execute-clearance-evidence-ledger-before-reviewed-payload-output";

	private PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedger() {
	}

	public record ClearanceEvidenceDecision(
			String laneName,
			boolean evidenceSubmitted,
			boolean evidenceAccepted,
			String evidenceFingerprint,
			String reviewerId,
			String note
	) {
	}

	public record ClearanceEvidenceLaneRow(
			int sequenceIndex,
			String laneName,
			String blockerKind,
			String prerequisiteLaneName,
			String requiredEvidenceArtifact,
			String acceptanceGate,
			boolean evidenceSubmitted,
			boolean evidenceAccepted,
			boolean prerequisiteEvidenceAccepted,
			boolean laneSatisfied,
			boolean clearsPromotionBlocker,
			boolean clearsFullSimulationCoverageBlocker,
			int affectedSlotCount,
			int promotionBlockerIncidence,
			int fullSimulationCoverageIncidence,
			String evidenceFingerprint,
			String reviewerId,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record ClearanceEvidenceSummary(
			int evidenceLaneCount,
			int submittedEvidenceCount,
			int acceptedEvidenceArtifactCount,
			int satisfiedEvidenceLaneCount,
			int missingEvidenceCount,
			int rejectedEvidenceCount,
			int waitingPrerequisiteCount,
			int requiredPromotionEvidenceLaneCount,
			int acceptedPromotionEvidenceLaneCount,
			int requiredFullSimulationEvidenceLaneCount,
			int acceptedFullSimulationEvidenceLaneCount,
			boolean promotionEvidenceComplete,
			boolean fullSimulationEvidenceComplete,
			boolean reviewedPayloadReviewAllowed,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String firstBlockedLane,
			String nextRequiredAction
	) {
	}

	public record ClearanceEvidenceScenario(
			String scenarioName,
			List<ClearanceEvidenceLaneRow> rows,
			ClearanceEvidenceSummary summary
	) {
		public ClearanceEvidenceScenario {
			rows = List.copyOf(rows);
		}
	}

	public record CtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedgerAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int evidenceLaneRowCount,
			int scenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ClearanceEvidenceLaneRow> currentRows,
			List<ClearanceEvidenceScenario> scenarios,
			ClearanceEvidenceSummary currentSummary
	) {
		public CtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedgerAudit {
			currentRows = List.copyOf(currentRows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedgerAudit audit() {
		ClearanceEvidenceScenario current = evaluate("current_no_evidence_submitted", List.of());
		List<ClearanceEvidenceScenario> scenarios = List.of(
				current,
				evaluate("synthetic_source_review_evidence_only", List.of(
						acceptedDecision("source_review", "source-review-fingerprint", "lab-reviewer"))),
				evaluate("synthetic_promotion_evidence_accepted_geometry_pending", List.of(
						acceptedDecision("source_review", "source-review-fingerprint", "lab-reviewer"),
						acceptedDecision("coefficient_payload_review", "payload-review-fingerprint",
								"lab-reviewer"),
						acceptedDecision("draft_review", "draft-review-fingerprint", "lab-reviewer"),
						acceptedDecision("coefficient_guard", "coefficient-guard-fingerprint", "lab-reviewer"),
						acceptedDecision("negative_ct_risk", "negative-ct-fingerprint", "lab-reviewer"),
						acceptedDecision("source_distance_risk", "source-distance-fingerprint", "lab-reviewer"))),
				evaluate("synthetic_all_clearance_evidence_accepted", List.of(
						acceptedDecision("source_review", "source-review-fingerprint", "lab-reviewer"),
						acceptedDecision("coefficient_payload_review", "payload-review-fingerprint",
								"lab-reviewer"),
						acceptedDecision("draft_review", "draft-review-fingerprint", "lab-reviewer"),
						acceptedDecision("coefficient_guard", "coefficient-guard-fingerprint", "lab-reviewer"),
						acceptedDecision("negative_ct_risk", "negative-ct-fingerprint", "lab-reviewer"),
						acceptedDecision("source_distance_risk", "source-distance-fingerprint", "lab-reviewer"),
						acceptedDecision("performance_only_geometry_coverage", "geometry-coverage-fingerprint",
								"lab-reviewer")))
		);
		return new CtCpJLookupCoefficientPayloadPromotionClearanceEvidenceLedgerAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				EVIDENCE_LANE_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				current.rows(),
				scenarios,
				current.summary()
		);
	}

	public static ClearanceEvidenceScenario evaluate(
			String scenarioName,
			List<ClearanceEvidenceDecision> decisions
	) {
		List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow> planRows =
				PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.audit().rows();
		Map<String, ClearanceEvidenceDecision> decisionByLane = decisionByLane(decisions, planRows);
		Set<String> satisfiedLaneNames = new HashSet<>();
		List<ClearanceEvidenceLaneRow> rows = new ArrayList<>();
		for (PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow planRow
				: planRows) {
			ClearanceEvidenceDecision decision = decisionByLane.get(planRow.laneName());
			boolean submitted = decision != null && decision.evidenceSubmitted();
			boolean accepted = submitted && decision.evidenceAccepted();
			boolean prerequisiteAccepted = "none".equals(planRow.prerequisiteLaneName())
					|| satisfiedLaneNames.contains(planRow.prerequisiteLaneName());
			boolean laneSatisfied = accepted && prerequisiteAccepted;
			if (laneSatisfied) {
				satisfiedLaneNames.add(planRow.laneName());
			}
			rows.add(new ClearanceEvidenceLaneRow(
					planRow.sequenceIndex(),
					planRow.laneName(),
					planRow.blockerKind(),
					planRow.prerequisiteLaneName(),
					planRow.requiredEvidenceArtifact(),
					planRow.acceptanceGate(),
					submitted,
					accepted,
					prerequisiteAccepted,
					laneSatisfied,
					planRow.clearsPromotionBlocker(),
					planRow.clearsFullSimulationCoverageBlocker(),
					planRow.affectedSlotCount(),
					planRow.promotionBlockerIncidenceCleared(),
					planRow.fullSimulationCoverageIncidenceCleared(),
					decision == null ? "missing" : decision.evidenceFingerprint(),
					decision == null ? "unassigned" : decision.reviewerId(),
					status(submitted, accepted, prerequisiteAccepted, laneSatisfied),
					nextAction(planRow, submitted, accepted, prerequisiteAccepted, laneSatisfied),
					message(planRow, submitted, accepted, prerequisiteAccepted, laneSatisfied)
			));
		}
		return new ClearanceEvidenceScenario(scenarioName, rows, summary(rows));
	}

	public static ClearanceEvidenceDecision acceptedDecision(
			String laneName,
			String evidenceFingerprint,
			String reviewerId
	) {
		return new ClearanceEvidenceDecision(laneName, true, true, evidenceFingerprint, reviewerId,
				"synthetic accepted evidence");
	}

	private static ClearanceEvidenceSummary summary(List<ClearanceEvidenceLaneRow> rows) {
		int submitted = 0;
		int acceptedArtifact = 0;
		int satisfied = 0;
		int missing = 0;
		int rejected = 0;
		int waiting = 0;
		int requiredPromotion = 0;
		int acceptedPromotion = 0;
		int requiredFullSimulation = 0;
		int acceptedFullSimulation = 0;
		String firstBlocked = "";
		for (ClearanceEvidenceLaneRow row : rows) {
			if (row.evidenceSubmitted()) {
				submitted++;
			} else {
				missing++;
			}
			if (row.evidenceAccepted()) {
				acceptedArtifact++;
			}
			if (row.evidenceSubmitted() && !row.evidenceAccepted()) {
				rejected++;
			}
			if (row.evidenceAccepted() && !row.prerequisiteEvidenceAccepted()) {
				waiting++;
			}
			if (row.laneSatisfied()) {
				satisfied++;
			} else if (firstBlocked.isBlank()) {
				firstBlocked = row.laneName();
			}
			if (row.clearsPromotionBlocker()) {
				requiredPromotion++;
				if (row.laneSatisfied()) {
					acceptedPromotion++;
				}
			}
			if (row.clearsFullSimulationCoverageBlocker()) {
				requiredFullSimulation++;
				if (row.laneSatisfied()) {
					acceptedFullSimulation++;
				}
			}
		}
		boolean promotionComplete = acceptedPromotion == requiredPromotion;
		boolean fullSimulationComplete = acceptedFullSimulation == requiredFullSimulation;
		return new ClearanceEvidenceSummary(
				rows.size(),
				submitted,
				acceptedArtifact,
				satisfied,
				missing,
				rejected,
				waiting,
				requiredPromotion,
				acceptedPromotion,
				requiredFullSimulation,
				acceptedFullSimulation,
				promotionComplete,
				fullSimulationComplete,
				promotionComplete,
				false,
				false,
				false,
				firstBlocked,
				firstBlocked.isBlank()
						? "review-coefficient-payload-values-before-lookup-execution"
						: NEXT_REQUIRED_ACTION
		);
	}

	private static Map<String, ClearanceEvidenceDecision> decisionByLane(
			List<ClearanceEvidenceDecision> decisions,
			List<PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow> planRows
	) {
		Set<String> knownLaneNames = new HashSet<>();
		for (PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow planRow
				: planRows) {
			knownLaneNames.add(planRow.laneName());
		}
		Map<String, ClearanceEvidenceDecision> byLane = new HashMap<>();
		for (ClearanceEvidenceDecision decision : decisions) {
			if (!knownLaneNames.contains(decision.laneName())) {
				throw new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload promotion clearance evidence lane: "
								+ decision.laneName());
			}
			if (byLane.put(decision.laneName(), decision) != null) {
				throw new IllegalArgumentException(
						"duplicate CT/CP/J coefficient payload promotion clearance evidence lane: "
								+ decision.laneName());
			}
		}
		return byLane;
	}

	private static String status(
			boolean submitted,
			boolean accepted,
			boolean prerequisiteAccepted,
			boolean laneSatisfied
	) {
		if (laneSatisfied) {
			return "READY";
		}
		if (accepted && !prerequisiteAccepted) {
			return "WAITING_PREREQUISITE";
		}
		if (submitted && !accepted) {
			return "REJECTED";
		}
		return "MISSING";
	}

	private static String nextAction(
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow planRow,
			boolean submitted,
			boolean accepted,
			boolean prerequisiteAccepted,
			boolean laneSatisfied
	) {
		if (laneSatisfied) {
			return "advance-to-next-clearance-evidence-lane";
		}
		if (accepted && !prerequisiteAccepted) {
			return "complete-" + planRow.prerequisiteLaneName() + "-before-" + planRow.laneName();
		}
		if (submitted && !accepted) {
			return "revise-or-reject-" + planRow.requiredEvidenceArtifact();
		}
		return "submit-" + planRow.requiredEvidenceArtifact();
	}

	private static String message(
			PropellerArchiveCtCpJLookupCoefficientPayloadPromotionClearancePlan.PromotionClearancePlanRow planRow,
			boolean submitted,
			boolean accepted,
			boolean prerequisiteAccepted,
			boolean laneSatisfied
	) {
		if (laneSatisfied) {
			return "clearance evidence lane accepted";
		}
		if (accepted && !prerequisiteAccepted) {
			return "accepted artifact waits on prerequisite lane: " + planRow.prerequisiteLaneName();
		}
		if (submitted && !accepted) {
			return "submitted evidence artifact is not accepted: " + planRow.requiredEvidenceArtifact();
		}
		return "missing required evidence artifact: " + planRow.requiredEvidenceArtifact();
	}
}
