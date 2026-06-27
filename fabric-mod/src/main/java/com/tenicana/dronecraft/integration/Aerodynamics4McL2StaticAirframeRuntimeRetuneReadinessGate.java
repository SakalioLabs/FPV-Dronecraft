package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Runtime-Retune-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"Runtime retune readiness remains closed until multi-axis config blend suggestions are reviewed, runtime preset mutation is explicitly enabled, and rotational damping evidence is available for angular drag.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int ROTATIONAL_SWEEP_CASE_COUNT = 24;
	public static final int SCENARIO_METRIC_COUNT = 24;
	public static final int SUMMARY_METRIC_ROW_COUNT = 9;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate() {
	}

	public record RuntimeRetuneReadinessSummary(
			int presetCount,
			int configBlendCandidateCount,
			int suggestedBodyDragCandidateCount,
			int suggestedPressureCenterCandidateCount,
			int configBlendPolicyReviewedCount,
			int runtimeConfigAutoApplyAllowedCount,
			int angularDragReviewRequiredCount,
			int rotationalSweepCaseCount,
			int rotationalBaselineStaticRequestReadyCount,
			int rotationalFlowApiRequiredCount,
			int rotationalFlowApiAvailableCount,
			int angularDragCalibrationAllowedCount,
			int expectedOpposingMomentSignCount,
			boolean allExpectedPresetsPresent,
			boolean bodyDragRetuneReady,
			boolean pressureCenterRetuneReady,
			boolean angularDragRetuneReady,
			boolean runtimePresetMutationExplicitlyEnabled,
			boolean staticAirframeRuntimeRetuneAllowed,
			double maxBodyDragRelativeDelta,
			double maxPressureCenterDeltaMeters,
			double maxCurrentDampingTorqueMagnitudeNewtonMeters,
			String reviewMode,
			String sourceRuntimeInfo
	) {
	}

	public record RuntimeRetuneReadinessScenario(
			String scenarioName,
			RuntimeRetuneReadinessSummary summary
	) {
	}

	public record RuntimeRetuneReadinessExtrema(
			int scenarioCount,
			int allowedScenarioCount,
			int blockedScenarioCount,
			int syntheticTargetAllowedCount,
			int maxConfigBlendCandidateCount,
			int maxRotationalSweepCaseCount,
			double maxBodyDragRelativeDelta,
			double maxPressureCenterDeltaMeters,
			double maxCurrentDampingTorqueMagnitudeNewtonMeters
	) {
	}

	public record RuntimeRetuneReadinessAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int presetSampleCount,
			int rotationalSweepCaseCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<RuntimeRetuneReadinessScenario> scenarios,
			RuntimeRetuneReadinessExtrema extrema
	) {
		public RuntimeRetuneReadinessAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RuntimeRetuneReadinessAudit audit() {
		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendAudit blendAudit =
				Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.audit();
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepAudit rotationalAudit =
				Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit();
		List<Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase> rotationalCases =
				rotationalAudit.sweepCases();
		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario current =
				findBlendScenario(blendAudit.scenarios(), "current_runtime_unavailable");
		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario testRuntime =
				findBlendScenario(blendAudit.scenarios(), "test_runtime_fit_ready_blocked");
		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario live =
				findBlendScenario(blendAudit.scenarios(), "live_runtime_full_multi_axis_candidate");
		List<RuntimeRetuneReadinessScenario> scenarios = List.of(
				scenario("current_runtime_unavailable", current.candidates(), rotationalCases,
						false, false, false, false, "current_audit"),
				scenario("test_runtime_fit_ready_blocked", testRuntime.candidates(), rotationalCases,
						false, false, false, false, "test_runtime_blocked"),
				scenario("live_runtime_full_multi_axis_candidate_unreviewed", live.candidates(), rotationalCases,
						false, false, false, false, "live_unreviewed"),
				scenario("reviewed_live_multi_axis_with_rotational_evidence", live.candidates(), rotationalCases,
						true, true, true, true, "synthetic_reviewed_all_evidence_available")
		);
		return new RuntimeRetuneReadinessAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				PRESET_SAMPLE_COUNT,
				ROTATIONAL_SWEEP_CASE_COUNT,
				SCENARIO_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static RuntimeRetuneReadinessScenario scenario(
			String scenarioName,
			List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate> blendCandidates,
			List<Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase> rotationalCases,
			boolean blendPolicyReviewed,
			boolean runtimePresetMutationEnabled,
			boolean rotationalFlowApiAvailable,
			boolean angularDragCalibrationAllowed,
			String reviewMode
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (blendCandidates == null || rotationalCases == null) {
			throw new IllegalArgumentException("blendCandidates and rotationalCases are required.");
		}
		if (reviewMode == null || reviewMode.isBlank()) {
			throw new IllegalArgumentException("reviewMode must not be blank.");
		}
		return new RuntimeRetuneReadinessScenario(
				scenarioName,
				gate(blendCandidates, rotationalCases, blendPolicyReviewed, runtimePresetMutationEnabled,
						rotationalFlowApiAvailable, angularDragCalibrationAllowed, reviewMode)
		);
	}

	private static RuntimeRetuneReadinessSummary gate(
			List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate> blendCandidates,
			List<Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase> rotationalCases,
			boolean blendPolicyReviewed,
			boolean runtimePresetMutationEnabled,
			boolean rotationalFlowApiAvailable,
			boolean angularDragCalibrationAllowed,
			String reviewMode
	) {
		Set<String> presets = new HashSet<>();
		int suggestedBodyDrag = 0;
		int suggestedPressureCenter = 0;
		int reviewed = 0;
		int autoApply = 0;
		int angularReviewRequired = 0;
		double maxBodyDragDelta = 0.0;
		double maxPressureCenterDelta = 0.0;
		Set<String> runtimes = new HashSet<>();
		for (Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate candidate : blendCandidates) {
			if (candidate == null || candidate.presetName() == null || candidate.presetName().isBlank()) {
				throw new IllegalArgumentException("blend candidates must include stable preset names.");
			}
			presets.add(candidate.presetName());
			if (candidate.bodyDragBlendFraction() > 0.0) {
				suggestedBodyDrag++;
			}
			if (candidate.pressureCenterBlendFraction() > 0.0) {
				suggestedPressureCenter++;
			}
			if (blendPolicyReviewed || candidate.blendPolicyReviewed()) {
				reviewed++;
			}
			if (runtimePresetMutationEnabled || candidate.runtimeConfigAutoApplyAllowed()) {
				autoApply++;
			}
			if (candidate.angularDragReviewRequired()) {
				angularReviewRequired++;
			}
			maxBodyDragDelta = Math.max(maxBodyDragDelta, candidate.maxBodyDragRelativeDelta());
			maxPressureCenterDelta = Math.max(maxPressureCenterDelta, candidate.maxPressureCenterDeltaMeters());
			runtimes.add(runtimeOrNone(candidate.sourceRuntimeInfo()));
		}

		int rotationalBaselineReady = 0;
		int rotationalApiRequired = 0;
		int rotationalApiAvailable = 0;
		int angularCalibrationAllowed = 0;
		int opposingMomentSign = 0;
		double maxDampingTorque = 0.0;
		for (Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase sweepCase : rotationalCases) {
			if (sweepCase == null || sweepCase.presetName() == null || sweepCase.presetName().isBlank()) {
				throw new IllegalArgumentException("rotational sweep cases must include stable preset names.");
			}
			presets.add(sweepCase.presetName());
			if (sweepCase.baselineStaticRequestReady()) {
				rotationalBaselineReady++;
			}
			if (sweepCase.rotationalFlowApiRequired()) {
				rotationalApiRequired++;
			}
			if (rotationalFlowApiAvailable || sweepCase.rotationalFlowApiAvailable()) {
				rotationalApiAvailable++;
			}
			if (angularDragCalibrationAllowed || sweepCase.angularDragCalibrationAllowed()) {
				angularCalibrationAllowed++;
			}
			if (sweepCase.expectedOpposingMomentSign()) {
				opposingMomentSign++;
			}
			maxDampingTorque = Math.max(maxDampingTorque, sweepCase.currentDampingTorqueMagnitudeNewtonMeters());
			runtimes.add(runtimeOrNone(sweepCase.sourceRuntimeInfo()));
		}

		boolean allPresets = presets.size() == PRESET_SAMPLE_COUNT
				&& blendCandidates.size() == PRESET_SAMPLE_COUNT
				&& rotationalCases.size() == ROTATIONAL_SWEEP_CASE_COUNT;
		boolean runtimeEnabled = runtimePresetMutationEnabled || autoApply == blendCandidates.size();
		boolean bodyReady = allPresets
				&& suggestedBodyDrag == PRESET_SAMPLE_COUNT
				&& reviewed == PRESET_SAMPLE_COUNT
				&& autoApply == PRESET_SAMPLE_COUNT
				&& runtimeEnabled;
		boolean pressureReady = allPresets
				&& suggestedPressureCenter == PRESET_SAMPLE_COUNT
				&& reviewed == PRESET_SAMPLE_COUNT
				&& autoApply == PRESET_SAMPLE_COUNT
				&& runtimeEnabled;
		boolean angularReady = allPresets
				&& angularReviewRequired == PRESET_SAMPLE_COUNT
				&& rotationalBaselineReady == ROTATIONAL_SWEEP_CASE_COUNT
				&& rotationalApiRequired == ROTATIONAL_SWEEP_CASE_COUNT
				&& rotationalApiAvailable == ROTATIONAL_SWEEP_CASE_COUNT
				&& angularCalibrationAllowed == ROTATIONAL_SWEEP_CASE_COUNT
				&& opposingMomentSign == ROTATIONAL_SWEEP_CASE_COUNT;
		boolean allowed = bodyReady && pressureReady && angularReady && runtimeEnabled;
		return new RuntimeRetuneReadinessSummary(
				presets.size(),
				blendCandidates.size(),
				suggestedBodyDrag,
				suggestedPressureCenter,
				reviewed,
				autoApply,
				angularReviewRequired,
				rotationalCases.size(),
				rotationalBaselineReady,
				rotationalApiRequired,
				rotationalApiAvailable,
				angularCalibrationAllowed,
				opposingMomentSign,
				allPresets,
				bodyReady,
				pressureReady,
				angularReady,
				runtimeEnabled,
				allowed,
				maxBodyDragDelta,
				maxPressureCenterDelta,
				maxDampingTorque,
				reviewMode,
				runtimes.size() == 1 ? runtimes.iterator().next() : "mixed"
		);
	}

	private static RuntimeRetuneReadinessExtrema extrema(List<RuntimeRetuneReadinessScenario> scenarios) {
		int allowed = 0;
		int syntheticAllowed = 0;
		int maxBlendCandidates = 0;
		int maxRotationalCases = 0;
		double maxBodyDragDelta = 0.0;
		double maxPressureCenterDelta = 0.0;
		double maxDampingTorque = 0.0;
		for (RuntimeRetuneReadinessScenario scenario : scenarios) {
			RuntimeRetuneReadinessSummary summary = scenario.summary();
			if (summary.staticAirframeRuntimeRetuneAllowed()) {
				allowed++;
				if (summary.reviewMode().startsWith("synthetic_")) {
					syntheticAllowed++;
				}
			}
			maxBlendCandidates = Math.max(maxBlendCandidates, summary.configBlendCandidateCount());
			maxRotationalCases = Math.max(maxRotationalCases, summary.rotationalSweepCaseCount());
			maxBodyDragDelta = Math.max(maxBodyDragDelta, summary.maxBodyDragRelativeDelta());
			maxPressureCenterDelta = Math.max(maxPressureCenterDelta, summary.maxPressureCenterDeltaMeters());
			maxDampingTorque = Math.max(maxDampingTorque, summary.maxCurrentDampingTorqueMagnitudeNewtonMeters());
		}
		return new RuntimeRetuneReadinessExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				syntheticAllowed,
				maxBlendCandidates,
				maxRotationalCases,
				maxBodyDragDelta,
				maxPressureCenterDelta,
				maxDampingTorque
		);
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario findBlendScenario(
			List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static String runtimeOrNone(String runtime) {
		return runtime == null || runtime.isBlank() ? "none" : runtime;
	}
}
