package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredHoverAcceptanceGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"Gate remains closed until powered source-term API support is available and every preset passes baseline-subtracted hover force/moment validation.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 14;
	public static final int SUMMARY_METRIC_ROW_COUNT = 8;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverAcceptanceGate() {
	}

	public record PoweredHoverAcceptanceSummary(
			boolean poweredSourceApiAvailable,
			int expectedTargetCount,
			int observedResultCount,
			int passedResultCount,
			int failedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			double maxForceErrorRatio,
			double maxForceErrorNewtons,
			double maxMomentErrorNewtonMeters,
			double maxCenterOfForceErrorMeters,
			boolean allTargetsPresent,
			boolean allExpectedResultsPassed,
			boolean gameplayCouplingAllowed
	) {
	}

	public record PoweredHoverAcceptanceScenario(
			String scenarioName,
			PoweredHoverAcceptanceSummary summary
	) {
	}

	public record PoweredHoverAcceptanceExtrema(
			int scenarioCount,
			int allowedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedTargetCount,
			int maxMissingResultCount,
			int maxUnexpectedResultCount,
			double maxForceErrorRatio,
			double maxForceErrorNewtons
	) {
	}

	public record PoweredHoverAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverAcceptanceScenario> scenarios,
			PoweredHoverAcceptanceExtrema extrema
	) {
		public PoweredHoverAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredHoverAcceptanceAudit audit() {
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget> targets =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets();
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> passingResults =
				passingResults(targets);
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> missingOne =
				passingResults.subList(0, passingResults.size() - 1);
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> failedOne =
				new ArrayList<>(passingResults);
		failedOne.set(failedOne.size() - 1, failingResult(targets.get(targets.size() - 1)));

		List<PoweredHoverAcceptanceScenario> scenarios = List.of(
				new PoweredHoverAcceptanceScenario("current_api_unavailable_no_results", gate(false, targets, List.of())),
				new PoweredHoverAcceptanceScenario("api_available_all_presets_pass", gate(true, targets, passingResults)),
				new PoweredHoverAcceptanceScenario("api_available_one_preset_missing", gate(true, targets, missingOne)),
				new PoweredHoverAcceptanceScenario("api_available_one_preset_failed", gate(true, targets, failedOne))
		);
		return new PoweredHoverAcceptanceAudit(
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

	public static PoweredHoverAcceptanceSummary gate(
			boolean poweredSourceApiAvailable,
			List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget> targets,
			List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> results
	) {
		if (targets == null || targets.isEmpty()) {
			throw new IllegalArgumentException("targets must not be empty.");
		}
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		Set<String> targetNames = new HashSet<>();
		for (Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target : targets) {
			if (target == null || target.presetName() == null || target.presetName().isBlank()) {
				throw new IllegalArgumentException("targets must include stable preset names.");
			}
			if (!targetNames.add(target.presetName())) {
				throw new IllegalArgumentException("duplicate target preset: " + target.presetName());
			}
		}

		Set<String> resultNames = new HashSet<>();
		for (Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult result : results) {
			if (result == null || result.presetName() == null || result.presetName().isBlank()) {
				throw new IllegalArgumentException("results must include stable preset names.");
			}
			if (!resultNames.add(result.presetName())) {
				throw new IllegalArgumentException("duplicate validation result preset: " + result.presetName());
			}
		}

		int passed = 0;
		int failed = 0;
		int missing = 0;
		double maxForceRatio = 0.0;
		double maxForceError = 0.0;
		double maxMomentError = 0.0;
		double maxCenterError = 0.0;
		for (Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target : targets) {
			Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult result =
					findResult(results, target.presetName());
			if (result == null) {
				missing++;
				continue;
			}
			maxForceRatio = Math.max(maxForceRatio, result.forceErrorRatio());
			maxForceError = Math.max(maxForceError, result.forceErrorNewtons());
			maxMomentError = Math.max(maxMomentError, result.momentErrorNewtonMeters());
			maxCenterError = Math.max(maxCenterError, result.centerOfForceErrorMeters());
			if (result.passed()) {
				passed++;
			} else {
				failed++;
			}
		}
		int unexpected = 0;
		for (Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult result : results) {
			if (!targetNames.contains(result.presetName())) {
				unexpected++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && results.size() == targets.size();
		boolean allPassed = failed == 0 && passed == targets.size();
		boolean allowed = poweredSourceApiAvailable && allPresent && allPassed;
		return new PoweredHoverAcceptanceSummary(
				poweredSourceApiAvailable,
				targets.size(),
				results.size(),
				passed,
				failed,
				missing,
				unexpected,
				maxForceRatio,
				maxForceError,
				maxMomentError,
				maxCenterError,
				allPresent,
				allPassed,
				allowed
		);
	}

	private static Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult findResult(
			List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> results,
			String presetName
	) {
		for (Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult result : results) {
			if (presetName.equals(result.presetName())) {
				return result;
			}
		}
		return null;
	}

	private static List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> passingResults(
			List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget> targets
	) {
		return targets.stream()
				.map(Aerodynamics4McL2PoweredHoverAcceptanceGate::passingResult)
				.toList();
	}

	private static Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult passingResult(
			Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target
	) {
		Aerodynamics4McL2Bridge.L2ForceMomentSample baseline = sample(1.25, -0.50, 3.0, 0.20, -0.10, 0.40);
		Aerodynamics4McL2Bridge.L2ForceMomentSample powered = sample(
				1.25 + target.targetForceXNewtons(),
				-0.50 + target.targetForceYNewtons(),
				3.0 + target.targetForceZNewtons(),
				0.20 + target.targetMomentXNewtonMeters(),
				-0.10 + target.targetMomentYNewtonMeters(),
				0.40 + target.targetMomentZNewtonMeters()
		);
		return Aerodynamics4McL2PoweredHoverValidation.evaluate(target, baseline, powered);
	}

	private static Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult failingResult(
			Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target
	) {
		return Aerodynamics4McL2PoweredHoverValidation.evaluate(
				target,
				sample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
				sample(0.0, target.targetForceYNewtons() * 0.50, 0.0, 0.0, 0.0, 0.0)
		);
	}

	private static Aerodynamics4McL2Bridge.L2ForceMomentSample sample(
			double forceX,
			double forceY,
			double forceZ,
			double momentX,
			double momentY,
			double momentZ
	) {
		return new Aerodynamics4McL2Bridge.L2ForceMomentSample(
				forceX,
				forceY,
				forceZ,
				momentX,
				momentY,
				momentZ,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0
		);
	}

	private static PoweredHoverAcceptanceExtrema extrema(List<PoweredHoverAcceptanceScenario> scenarios) {
		int allowed = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxUnexpected = 0;
		double maxForceRatio = 0.0;
		double maxForceError = 0.0;
		for (PoweredHoverAcceptanceScenario scenario : scenarios) {
			PoweredHoverAcceptanceSummary summary = scenario.summary();
			if (summary.gameplayCouplingAllowed()) {
				allowed++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedTargetCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedResultCount());
			maxForceRatio = Math.max(maxForceRatio, summary.maxForceErrorRatio());
			maxForceError = Math.max(maxForceError, summary.maxForceErrorNewtons());
		}
		return new PoweredHoverAcceptanceExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				maxExpected,
				maxMissing,
				maxUnexpected,
				maxForceRatio,
				maxForceError
		);
	}
}
