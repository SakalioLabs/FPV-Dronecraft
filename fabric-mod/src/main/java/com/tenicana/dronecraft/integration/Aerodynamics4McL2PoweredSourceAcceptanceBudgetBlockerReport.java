package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Acceptance-Budget-Blocker-Report-Packet";
	public static final String CAVEAT =
			"Acceptance budget blocker report decomposes powered-source handoff and validation-budget gaps into audit reasons only; it does not enable runtime coupling or mutate gameplay configuration.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.SCENARIO_SAMPLE_COUNT;
	public static final int SCENARIO_METRIC_COUNT = 30;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceAcceptanceBudgetBlockerReport() {
	}

	public record PoweredSourceAcceptanceBudgetBlockerSummary(
			boolean acceptanceBudgetGateReady,
			int blockerCount,
			boolean acceptanceHandoffBlocker,
			boolean validationBudgetBlocker,
			boolean hoverAcceptanceHandoffBlocker,
			boolean cruiseAcceptanceHandoffBlocker,
			boolean hoverValidationBudgetBlocker,
			boolean cruiseValidationBudgetBlocker,
			int acceptanceHandoffBlockerMessageCount,
			String dominantAcceptanceHandoffMessage,
			int validationBudgetBlockerMessageCount,
			String dominantValidationBudgetMessage,
			boolean runtimeCouplingStillClosed,
			boolean gameplayAutoApplyStillClosed,
			boolean hoverAcceptanceHandoffReady,
			boolean cruiseAcceptanceHandoffReady,
			int readyHandoffCount,
			int expectedHandoffCount,
			boolean hoverValidationBudgetCandidate,
			boolean cruiseValidationBudgetCandidate,
			int validationBudgetCandidateCount,
			int expectedValidationBudgetGroupCount,
			double maxForceErrorRatio,
			double maxForceErrorNewtons,
			double maxMomentErrorNewtonMeters,
			double maxCenterOfForceErrorMeters,
			String sourceRuntimeInfo,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceAcceptanceBudgetBlockerScenario(
			String scenarioName,
			PoweredSourceAcceptanceBudgetBlockerSummary summary
	) {
	}

	public record PoweredSourceAcceptanceBudgetBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int acceptanceHandoffBlockerScenarioCount,
			int validationBudgetBlockerScenarioCount,
			int hoverAcceptanceHandoffBlockerScenarioCount,
			int cruiseAcceptanceHandoffBlockerScenarioCount,
			int hoverValidationBudgetBlockerScenarioCount,
			int cruiseValidationBudgetBlockerScenarioCount,
			int maxAcceptanceHandoffBlockerMessageCount,
			int maxValidationBudgetBlockerMessageCount
	) {
	}

	public record PoweredSourceAcceptanceBudgetBlockerAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceAcceptanceBudgetBlockerScenario> scenarios,
			PoweredSourceAcceptanceBudgetBlockerExtrema extrema
	) {
		public PoweredSourceAcceptanceBudgetBlockerAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceAcceptanceBudgetBlockerAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit());
	}

	public static PoweredSourceAcceptanceBudgetBlockerAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(loader));
	}

	public static PoweredSourceAcceptanceBudgetBlockerAudit audit(
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit budgetGateAudit
	) {
		if (budgetGateAudit == null) {
			throw new IllegalArgumentException("budgetGateAudit must not be null.");
		}
		List<PoweredSourceAcceptanceBudgetBlockerScenario> scenarios = budgetGateAudit.scenarios().stream()
				.map(scenario -> new PoweredSourceAcceptanceBudgetBlockerScenario(
						scenario.scenarioName(),
						report(scenario.summary())))
				.toList();
		return new PoweredSourceAcceptanceBudgetBlockerAudit(
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

	public static PoweredSourceAcceptanceBudgetBlockerSummary report(
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary readiness
	) {
		if (readiness == null) {
			throw new IllegalArgumentException("readiness summary must not be null.");
		}
		boolean hoverHandoffBlocker = !readiness.hoverAcceptanceHandoffReady();
		boolean cruiseHandoffBlocker = !readiness.cruiseAcceptanceHandoffReady();
		boolean hoverBudgetBlocker = !readiness.hoverValidationBudgetCandidate();
		boolean cruiseBudgetBlocker = !readiness.cruiseValidationBudgetCandidate();
		boolean handoffBlocker = hoverHandoffBlocker || cruiseHandoffBlocker || !readiness.allAcceptanceHandoffsReady();
		boolean budgetBlocker = hoverBudgetBlocker || cruiseBudgetBlocker || !readiness.allValidationBudgetsReady();
		int blockerCount = countTrue(
				hoverHandoffBlocker,
				cruiseHandoffBlocker,
				hoverBudgetBlocker,
				cruiseBudgetBlocker);
		boolean ready = readiness.acceptanceBudgetGateReady() && blockerCount == 0;
		return new PoweredSourceAcceptanceBudgetBlockerSummary(
				ready,
				blockerCount,
				handoffBlocker,
				budgetBlocker,
				hoverHandoffBlocker,
				cruiseHandoffBlocker,
				hoverBudgetBlocker,
				cruiseBudgetBlocker,
				readiness.acceptanceHandoffBlockerMessageCount(),
				readiness.dominantAcceptanceHandoffMessage(),
				readiness.validationBudgetBlockerMessageCount(),
				readiness.dominantValidationBudgetMessage(),
				!readiness.runtimeCouplingAllowed(),
				!readiness.gameplayAutoApplyAllowed(),
				readiness.hoverAcceptanceHandoffReady(),
				readiness.cruiseAcceptanceHandoffReady(),
				readiness.readyHandoffCount(),
				readiness.expectedHandoffCount(),
				readiness.hoverValidationBudgetCandidate(),
				readiness.cruiseValidationBudgetCandidate(),
				readiness.validationBudgetCandidateCount(),
				readiness.expectedValidationBudgetGroupCount(),
				readiness.maxForceErrorRatio(),
				readiness.maxForceErrorNewtons(),
				readiness.maxMomentErrorNewtonMeters(),
				readiness.maxCenterOfForceErrorMeters(),
				readiness.sourceRuntimeInfo(),
				nextRequiredAction(handoffBlocker, budgetBlocker),
				ready ? "READY" : "BLOCKED",
				ready ? "powered-source-acceptance-budget-clear" : "powered-source-acceptance-budget-blocked"
		);
	}

	private static String nextRequiredAction(boolean handoffBlocker, boolean budgetBlocker) {
		if (handoffBlocker) {
			return "complete-hover-and-cruise-powered-source-acceptance-handoffs";
		}
		if (budgetBlocker) {
			return "produce-hover-and-cruise-validation-error-budget-candidates";
		}
		return "powered-source-acceptance-evidence-ready-for-final-coupling-review";
	}

	private static int countTrue(boolean... values) {
		int count = 0;
		for (boolean value : values) {
			if (value) {
				count++;
			}
		}
		return count;
	}

	private static PoweredSourceAcceptanceBudgetBlockerExtrema extrema(
			List<PoweredSourceAcceptanceBudgetBlockerScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int handoff = 0;
		int budget = 0;
		int hoverHandoff = 0;
		int cruiseHandoff = 0;
		int hoverBudget = 0;
		int cruiseBudget = 0;
		int maxHandoffMessages = 0;
		int maxBudgetMessages = 0;
		for (PoweredSourceAcceptanceBudgetBlockerScenario scenario : scenarios) {
			PoweredSourceAcceptanceBudgetBlockerSummary summary = scenario.summary();
			if (summary.acceptanceBudgetGateReady()) {
				ready++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.acceptanceHandoffBlocker()) {
				handoff++;
			}
			if (summary.validationBudgetBlocker()) {
				budget++;
			}
			if (summary.hoverAcceptanceHandoffBlocker()) {
				hoverHandoff++;
			}
			if (summary.cruiseAcceptanceHandoffBlocker()) {
				cruiseHandoff++;
			}
			if (summary.hoverValidationBudgetBlocker()) {
				hoverBudget++;
			}
			if (summary.cruiseValidationBudgetBlocker()) {
				cruiseBudget++;
			}
			maxHandoffMessages = Math.max(
					maxHandoffMessages,
					summary.acceptanceHandoffBlockerMessageCount());
			maxBudgetMessages = Math.max(maxBudgetMessages, summary.validationBudgetBlockerMessageCount());
		}
		return new PoweredSourceAcceptanceBudgetBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				handoff,
				budget,
				hoverHandoff,
				cruiseHandoff,
				hoverBudget,
				cruiseBudget,
				maxHandoffMessages,
				maxBudgetMessages
		);
	}
}
