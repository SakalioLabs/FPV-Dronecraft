package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook readiness requires the accepted control contract plus a reviewed target-omega hook, motor-response coupling, failsafe clamp, and blackbox regression before any candidate derate can be treated as validation-ready; runtime coupling, playable export, and gameplay auto-apply remain closed.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int REQUIRED_CONTRACT_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_ROW_COUNT = 10;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate() {
	}

	private record HookImplementationEvidence(
			boolean targetOmegaHookImplemented,
			boolean motorResponseCouplingReviewed,
			boolean failsafeClampReviewed,
			boolean blackboxRegressionAvailable
	) {
	}

	public record DerateControlHookReadinessRow(
			String scenarioName,
			boolean controlContractAvailable,
			boolean contractCoverageComplete,
			boolean targetOmegaBoundaryMatched,
			boolean controlLayerClampRequired,
			boolean runtimeHookImplemented,
			boolean motorResponseCouplingReviewed,
			boolean failsafeClampReviewed,
			boolean blackboxRegressionAvailable,
			boolean implementationReady,
			int contractRowCount,
			int requiredContractRowCount,
			double minTargetMaxRpmScale,
			double maxEquivalentMaxThrustLossPercent,
			double minContractedMaxRpm,
			double maxContractedMaxRpm,
			String controlBoundary,
			boolean configMutationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String dominantBlocker,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record DerateControlHookReadinessExtrema(
			int scenarioCount,
			int implementationReadyScenarioCount,
			int controlContractAvailableScenarioCount,
			int contractCoverageCompleteScenarioCount,
			int targetOmegaBoundaryMatchedScenarioCount,
			int controlLayerClampRequiredScenarioCount,
			int runtimeHookImplementedScenarioCount,
			int motorResponseCouplingReviewedScenarioCount,
			int failsafeClampReviewedScenarioCount,
			int blackboxRegressionAvailableScenarioCount,
			int maxContractRowCount,
			double minTargetMaxRpmScale,
			double maxEquivalentMaxThrustLossPercent,
			double minContractedMaxRpm,
			double maxContractedMaxRpm,
			int configMutationAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int playableReferenceAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record DerateControlHookReadinessAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			int requiredContractRowCount,
			String controlBoundary,
			List<DerateControlHookReadinessRow> rows,
			DerateControlHookReadinessExtrema extrema
	) {
		public DerateControlHookReadinessAudit {
			rows = List.copyOf(rows);
		}
	}

	public static DerateControlHookReadinessAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();
		List<DerateControlHookReadinessRow> rows = new ArrayList<>();
		boolean motorResponseCouplingReviewed =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
						.audit()
						.summary()
						.motorResponseCouplingReviewed();
		boolean failsafeClampReviewed =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
						.audit()
						.summary()
						.failsafeClampReviewed();
		HookImplementationEvidence currentEvidence =
				new HookImplementationEvidence(true, motorResponseCouplingReviewed, failsafeClampReviewed, false);
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractScenario scenario : contract.scenarios()) {
			rows.add(row(scenario.scenarioName(), scenario.summary(), currentEvidence));
		}
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractScenarioSummary readySummary =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.scenario(
								"synthetic_derate_validation_all_pass").summary();
		rows.add(row(
				"synthetic_control_hook_ready_reviewed",
				readySummary,
				new HookImplementationEvidence(true, true, true, true)
		));
		return new DerateControlHookReadinessAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				REQUIRED_CONTRACT_ROW_COUNT,
				CONTROL_BOUNDARY,
				rows,
				extrema(rows)
		);
	}

	public static DerateControlHookReadinessRow row(String scenarioName) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate control-hook readiness scenario: " + scenarioName));
	}

	private static DerateControlHookReadinessRow row(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractScenarioSummary summary,
			HookImplementationEvidence evidence
	) {
		boolean available = summary.derateControlContractAvailable();
		boolean coverage = available && summary.contractRowCount() == REQUIRED_CONTRACT_ROW_COUNT;
		boolean boundary = available && CONTROL_BOUNDARY.equals(summary.controlBoundary());
		boolean clamp = available && summary.controlLayerClampRequiredCount() == summary.contractRowCount();
		boolean hook = coverage && boundary && evidence.targetOmegaHookImplemented();
		boolean motorResponseCouplingReviewed = hook && evidence.motorResponseCouplingReviewed();
		boolean failsafeClampReviewed = motorResponseCouplingReviewed && evidence.failsafeClampReviewed();
		boolean leakBlocked = !summary.postDeratePhysicalBudgetAccepted() || noMutationLeak(summary);
		boolean implementationReady = coverage
				&& boundary
				&& clamp
				&& hook
				&& motorResponseCouplingReviewed
				&& failsafeClampReviewed
				&& evidence.blackboxRegressionAvailable()
				&& leakBlocked;
		String blocker = dominantBlocker(summary, coverage, boundary, clamp, evidence, leakBlocked);
		return new DerateControlHookReadinessRow(
				scenarioName,
				available,
				coverage,
				boundary,
				clamp,
				hook,
				motorResponseCouplingReviewed,
				failsafeClampReviewed,
				evidence.blackboxRegressionAvailable(),
				implementationReady,
				summary.contractRowCount(),
				REQUIRED_CONTRACT_ROW_COUNT,
				available ? summary.minTargetMaxRpmScale() : 0.0,
				available ? summary.maxEquivalentMaxThrustLossPercent() : 0.0,
				available ? summary.minContractedMaxRpm() : 0.0,
				available ? summary.maxContractedMaxRpm() : 0.0,
				CONTROL_BOUNDARY,
				false,
				false,
				false,
				false,
				blocker,
				nextRequiredAction(blocker),
				implementationReady ? "VALIDATION_READY" : "BLOCKED",
				implementationReady ? "target-omega-control-hook-ready-for-offline-validation" : blocker
		);
	}

	private static boolean noMutationLeak(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractScenarioSummary summary
	) {
		return summary.rotorSpecPatchAllowedCount() == 0
				&& summary.droneConfigPatchAllowedCount() == 0
				&& summary.runtimeCouplingAllowedCount() == 0
				&& summary.playableReferenceAllowedCount() == 0
				&& summary.gameplayAutoApplyAllowedCount() == 0;
	}

	private static String dominantBlocker(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractScenarioSummary summary,
			boolean coverage,
			boolean boundary,
			boolean clamp,
			HookImplementationEvidence evidence,
			boolean leakBlocked
	) {
		if (!summary.derateControlContractAvailable()) {
			return summary.message();
		}
		if (!coverage) {
			return "control-contract-coverage-incomplete";
		}
		if (!boundary) {
			return "target-omega-boundary-mismatch";
		}
		if (!clamp) {
			return "control-layer-clamp-contract-missing";
		}
		if (!evidence.targetOmegaHookImplemented()) {
			return "target-omega-derate-hook-not-implemented";
		}
		if (!evidence.motorResponseCouplingReviewed()) {
			return "motor-response-coupling-review-missing";
		}
		if (!evidence.failsafeClampReviewed()) {
			return "failsafe-clamp-review-missing";
		}
		if (!evidence.blackboxRegressionAvailable()) {
			return "derate-hook-blackbox-regression-missing";
		}
		if (!leakBlocked) {
			return "control-hook-runtime-leak-guard-failed";
		}
		return "target-omega-control-hook-ready-for-offline-validation";
	}

	private static String nextRequiredAction(String blocker) {
		return switch (blocker) {
			case "cold-air-derate-validation-not-planned" ->
					"complete-cold-air-derate-validation-before-control-hook";
			case "cold-air-derate-validation-result-set-incomplete" ->
					"provide-complete-derate-validation-result-set";
			case "cold-air-derate-validation-result-failed" ->
					"repair-failed-cold-air-derate-validation-result";
			case "control-contract-coverage-incomplete" ->
					"complete-racingQuad-and-apDrone-control-contract-rows";
			case "target-omega-boundary-mismatch" ->
					"review-target-omega-boundary-before-hook";
			case "control-layer-clamp-contract-missing" ->
					"require-control-layer-clamp-for-each-contract-row";
			case "target-omega-derate-hook-not-implemented" ->
					"implement-target-omega-scale-hook-before-motor-response";
			case "motor-response-coupling-review-missing" ->
					"review-motor-response-and-slew-effects-after-target-omega-derate";
			case "failsafe-clamp-review-missing" ->
					"review-failsafe-clamp-and-no-load-overspeed-interaction";
			case "derate-hook-blackbox-regression-missing" ->
					"add-blackbox-regression-for-cold-air-derate-hook";
			case "control-hook-runtime-leak-guard-failed" ->
					"close-runtime-playable-and-gameplay-leak-paths-before-validation";
			default ->
					"run-offline-cold-air-derate-flight-validation-before-runtime-coupling";
		};
	}

	private static DerateControlHookReadinessExtrema extrema(List<DerateControlHookReadinessRow> rows) {
		int ready = 0;
		int available = 0;
		int coverage = 0;
		int boundary = 0;
		int clamp = 0;
		int hook = 0;
		int motor = 0;
		int failsafe = 0;
		int blackbox = 0;
		int maxRows = 0;
		double minScale = Double.POSITIVE_INFINITY;
		double maxThrustLoss = 0.0;
		double minRpm = Double.POSITIVE_INFINITY;
		double maxRpm = 0.0;
		int config = 0;
		int runtime = 0;
		int playable = 0;
		int gameplay = 0;
		for (DerateControlHookReadinessRow row : rows) {
			if (row.implementationReady()) {
				ready++;
			}
			if (row.controlContractAvailable()) {
				available++;
				minScale = Math.min(minScale, row.minTargetMaxRpmScale());
				maxThrustLoss = Math.max(maxThrustLoss, row.maxEquivalentMaxThrustLossPercent());
				minRpm = Math.min(minRpm, row.minContractedMaxRpm());
				maxRpm = Math.max(maxRpm, row.maxContractedMaxRpm());
			}
			if (row.contractCoverageComplete()) {
				coverage++;
			}
			if (row.targetOmegaBoundaryMatched()) {
				boundary++;
			}
			if (row.controlLayerClampRequired()) {
				clamp++;
			}
			if (row.runtimeHookImplemented()) {
				hook++;
			}
			if (row.motorResponseCouplingReviewed()) {
				motor++;
			}
			if (row.failsafeClampReviewed()) {
				failsafe++;
			}
			if (row.blackboxRegressionAvailable()) {
				blackbox++;
			}
			if (row.configMutationAllowed()) {
				config++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.playableReferenceAllowed()) {
				playable++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxRows = Math.max(maxRows, row.contractRowCount());
		}
		return new DerateControlHookReadinessExtrema(
				rows.size(),
				ready,
				available,
				coverage,
				boundary,
				clamp,
				hook,
				motor,
				failsafe,
				blackbox,
				maxRows,
				available == 0 ? 0.0 : minScale,
				maxThrustLoss,
				available == 0 ? 0.0 : minRpm,
				maxRpm,
				config,
				runtime,
				playable,
				gameplay
		);
	}
}
