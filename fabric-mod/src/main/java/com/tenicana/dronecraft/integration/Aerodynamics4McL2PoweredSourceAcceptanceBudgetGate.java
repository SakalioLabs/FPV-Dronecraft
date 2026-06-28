package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Acceptance-Budget-Gate-Packet";
	public static final String CAVEAT =
			"Acceptance budget gate requires hover and cruise acceptance handoffs plus their validation error budgets to be ready before final powered-source coupling review; it never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 24;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate() {
	}

	public record PoweredSourceAcceptanceBudgetSummary(
			boolean hoverAcceptanceHandoffReady,
			boolean cruiseAcceptanceHandoffReady,
			int expectedHandoffCount,
			int handoffCount,
			int readyHandoffCount,
			int blockedHandoffCount,
			int expectedValidationBudgetGroupCount,
			int validationBudgetGroupCount,
			int validationBudgetCandidateCount,
			int blockedValidationBudgetGroupCount,
			boolean hoverValidationBudgetCandidate,
			boolean cruiseValidationBudgetCandidate,
			boolean allAcceptanceHandoffsReady,
			boolean allValidationBudgetsReady,
			boolean acceptanceBudgetGateReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			double maxForceErrorRatio,
			double maxForceErrorNewtons,
			double maxMomentErrorNewtonMeters,
			double maxCenterOfForceErrorMeters,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record PoweredSourceAcceptanceBudgetScenario(
			String scenarioName,
			PoweredSourceAcceptanceBudgetSummary summary
	) {
	}

	public record PoweredSourceAcceptanceBudgetExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxReadyHandoffCount,
			int maxValidationBudgetCandidateCount,
			int maxBlockedValidationBudgetGroupCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double maxForceErrorRatio,
			double maxCenterOfForceErrorMeters
	) {
	}

	public record PoweredSourceAcceptanceBudgetAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceAcceptanceBudgetScenario> scenarios,
			PoweredSourceAcceptanceBudgetExtrema extrema
	) {
		public PoweredSourceAcceptanceBudgetAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceAcceptanceBudgetAudit audit() {
		return audit(
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(),
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit()
		);
	}

	public static PoweredSourceAcceptanceBudgetAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(loader),
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit(loader)
		);
	}

	public static PoweredSourceAcceptanceBudgetAudit audit(
			Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffAudit handoffAudit,
			Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetAudit budgetAudit
	) {
		if (handoffAudit == null || budgetAudit == null) {
			throw new IllegalArgumentException("handoff and budget audits are required.");
		}
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> currentHandoffs =
				handoffAudit.handoffs();
		List<Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup> currentBudgets =
				budgetAudit.groups();
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> readyHandoffs =
				List.of(readyHandoff("hover"), readyHandoff("cruise"));
		List<Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup> readyBudgets =
				currentBudgets.stream()
						.map(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate::readyBudget)
						.toList();
		List<PoweredSourceAcceptanceBudgetScenario> scenarios = List.of(
				new PoweredSourceAcceptanceBudgetScenario(
						"current_handoff_and_budget_blocked",
						gate(currentHandoffs, currentBudgets, "audit-only-powered-source-acceptance-budget-gate")),
				new PoweredSourceAcceptanceBudgetScenario(
						"handoff_ready_budget_ready",
						gate(readyHandoffs, readyBudgets, "synthetic-live-powered-source-acceptance-budget-ready")),
				new PoweredSourceAcceptanceBudgetScenario(
						"handoff_ready_budget_blocked",
						gate(readyHandoffs, currentBudgets, "synthetic-handoff-ready-budget-blocked")),
				new PoweredSourceAcceptanceBudgetScenario(
						"budget_ready_handoff_blocked",
						gate(currentHandoffs, readyBudgets, "synthetic-budget-ready-handoff-blocked"))
		);
		return new PoweredSourceAcceptanceBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static PoweredSourceAcceptanceBudgetSummary gate(
			List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> handoffs,
			List<Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup> budgets,
			String sourceRuntimeInfo
	) {
		if (handoffs == null || budgets == null) {
			throw new IllegalArgumentException("handoffs and budgets must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		Set<String> observedHandoffs = new HashSet<>();
		boolean hoverHandoff = false;
		boolean cruiseHandoff = false;
		int readyHandoffs = 0;
		for (Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary handoff : handoffs) {
			if (handoff == null || handoff.spinState() == null || handoff.spinState().isBlank()) {
				throw new IllegalArgumentException("handoffs must include stable spin-state names.");
			}
			if (!observedHandoffs.add(handoff.spinState())) {
				throw new IllegalArgumentException("duplicate handoff spin state: " + handoff.spinState());
			}
			if ("hover".equals(handoff.spinState())) {
				hoverHandoff = handoff.acceptanceHandoffReady();
				if (hoverHandoff) {
					readyHandoffs++;
				}
			} else if ("cruise".equals(handoff.spinState())) {
				cruiseHandoff = handoff.acceptanceHandoffReady();
				if (cruiseHandoff) {
					readyHandoffs++;
				}
			}
		}
		Set<String> observedBudgets = new HashSet<>();
		boolean hoverBudget = false;
		boolean cruiseBudget = false;
		int candidateBudgets = 0;
		double maxForceRatio = 0.0;
		double maxForceError = 0.0;
		double maxMomentError = 0.0;
		double maxCenterError = 0.0;
		for (Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup budget
				: budgets) {
			if (budget == null || budget.spinState() == null || budget.spinState().isBlank()) {
				throw new IllegalArgumentException("budgets must include stable spin-state names.");
			}
			if (!observedBudgets.add(budget.spinState())) {
				throw new IllegalArgumentException("duplicate budget spin state: " + budget.spinState());
			}
			if ("hover".equals(budget.spinState())) {
				hoverBudget = budget.validationBudgetCandidate();
				if (hoverBudget) {
					candidateBudgets++;
				}
			} else if ("cruise".equals(budget.spinState())) {
				cruiseBudget = budget.validationBudgetCandidate();
				if (cruiseBudget) {
					candidateBudgets++;
				}
			}
			maxForceRatio = Math.max(maxForceRatio, budget.maxForceErrorRatio());
			maxForceError = Math.max(maxForceError, budget.maxForceErrorNewtons());
			maxMomentError = Math.max(maxMomentError, budget.maxMomentErrorNewtonMeters());
			maxCenterError = Math.max(maxCenterError, budget.maxCenterOfForceErrorMeters());
		}
		boolean allHandoffsReady = hoverHandoff
				&& cruiseHandoff
				&& observedHandoffs.contains("hover")
				&& observedHandoffs.contains("cruise")
				&& observedHandoffs.size() == 2;
		boolean allBudgetsReady = hoverBudget
				&& cruiseBudget
				&& observedBudgets.contains("hover")
				&& observedBudgets.contains("cruise")
				&& observedBudgets.size() == 2;
		boolean ready = allHandoffsReady && allBudgetsReady;
		return new PoweredSourceAcceptanceBudgetSummary(
				hoverHandoff,
				cruiseHandoff,
				2,
				handoffs.size(),
				readyHandoffs,
				2 - readyHandoffs,
				2,
				budgets.size(),
				candidateBudgets,
				2 - candidateBudgets,
				hoverBudget,
				cruiseBudget,
				allHandoffsReady,
				allBudgetsReady,
				ready,
				false,
				false,
				maxForceRatio,
				maxForceError,
				maxMomentError,
				maxCenterError,
				ready ? "READY" : "BLOCKED",
				messageFor(allHandoffsReady, allBudgetsReady),
				sourceRuntimeInfo
		);
	}

	private static String messageFor(boolean allHandoffsReady, boolean allBudgetsReady) {
		if (!allHandoffsReady) {
			return "acceptance-handoffs-not-ready";
		}
		if (!allBudgetsReady) {
			return "validation-error-budgets-not-ready";
		}
		return "acceptance-budget-gate-ready";
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary readyHandoff(
			String spinState
	) {
		boolean hover = "hover".equals(spinState);
		return new Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary(
				spinState,
				hover ? Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID : Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID,
				hover ? Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID : Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID,
				4,
				4,
				4,
				4,
				4,
				4,
				0,
				0,
				"none",
				0,
				0,
				0,
				0.0,
				0.0,
				true,
				true,
				true,
				"READY",
				"acceptance-handoff-ready"
		);
	}

	private static Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup readyBudget(
			Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup budget
	) {
		return new Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup(
				budget.spinState(),
				budget.validationPacketId(),
				budget.acceptanceGatePacketId(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				budget.expectedPresetCount(),
				0,
				0,
				"none",
				0,
				budget.expectedPresetCount(),
				0,
				0,
				true,
				true,
				true,
				true,
				0.0,
				0.0,
				0.0,
				0.0,
				budget.meanTargetForceMagnitudeNewtons(),
				budget.meanTargetMomentMagnitudeNewtonMeters(),
				true,
				"READY",
				"powered-source-validation-error-budget-ready"
		);
	}

	private static PoweredSourceAcceptanceBudgetExtrema extrema(
			List<PoweredSourceAcceptanceBudgetScenario> scenarios
	) {
		int ready = 0;
		int runtime = 0;
		int autoApply = 0;
		int maxHandoffs = 0;
		int maxBudgets = 0;
		int maxBlockedBudgets = 0;
		double maxForceRatio = 0.0;
		double maxCenterError = 0.0;
		for (PoweredSourceAcceptanceBudgetScenario scenario : scenarios) {
			PoweredSourceAcceptanceBudgetSummary summary = scenario.summary();
			if (summary.acceptanceBudgetGateReady()) {
				ready++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				autoApply++;
			}
			maxHandoffs = Math.max(maxHandoffs, summary.readyHandoffCount());
			maxBudgets = Math.max(maxBudgets, summary.validationBudgetCandidateCount());
			maxBlockedBudgets = Math.max(maxBlockedBudgets, summary.blockedValidationBudgetGroupCount());
			maxForceRatio = Math.max(maxForceRatio, summary.maxForceErrorRatio());
			maxCenterError = Math.max(maxCenterError, summary.maxCenterOfForceErrorMeters());
		}
		return new PoweredSourceAcceptanceBudgetExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxHandoffs,
				maxBudgets,
				maxBlockedBudgets,
				runtime,
				autoApply,
				maxForceRatio,
				maxCenterError
		);
	}
}
