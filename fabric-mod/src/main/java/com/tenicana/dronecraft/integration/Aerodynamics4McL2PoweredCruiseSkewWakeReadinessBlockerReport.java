package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Readiness-Blocker-Report-Packet";
	public static final String CAVEAT =
			"Readiness blocker report decomposes live cruise skew-wake validation blockers into audit reasons only; it does not invoke A4MC, export gameplay tuning data, or enable runtime coupling.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.SCENARIO_SAMPLE_COUNT;
	public static final int SCENARIO_METRIC_COUNT = 27;
	public static final int SUMMARY_METRIC_ROW_COUNT = 18;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeReadinessBlockerReport() {
	}

	public record PoweredCruiseSkewWakeReadinessBlockerSummary(
			boolean liveValidationRunAllowed,
			int blockerCount,
			boolean poweredSourceApiBlocker,
			boolean skewWakeProbeApiBlocker,
			boolean transientProbeApiBlocker,
			boolean targetCoverageBlocker,
			boolean targetProbeApiBlocker,
			boolean temporalResolutionBlocker,
			boolean invalidTargetBlocker,
			boolean unexpectedTargetBlocker,
			boolean runtimeCouplingLeakBlocker,
			int expectedTargetCount,
			int observedTargetCount,
			int sourceTermTargetCount,
			int centerlineSweepTargetCount,
			int lateralSweepTargetCount,
			int transientProbeApiAvailableTargetCount,
			int runtimeCouplingAllowedTargetCount,
			int invalidTargetCount,
			int missingTargetCount,
			int unexpectedTargetCount,
			double appliedMaxSamplePeriodSeconds,
			double requiredMaxSamplePeriodSeconds,
			double maxRequiredSampleRateHertz,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredCruiseSkewWakeReadinessBlockerScenario(
			String scenarioName,
			PoweredCruiseSkewWakeReadinessBlockerSummary summary
	) {
	}

	public record PoweredCruiseSkewWakeReadinessBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int poweredSourceApiBlockerScenarioCount,
			int skewWakeProbeApiBlockerScenarioCount,
			int transientProbeApiBlockerScenarioCount,
			int targetCoverageBlockerScenarioCount,
			int targetProbeApiBlockerScenarioCount,
			int temporalResolutionBlockerScenarioCount,
			int invalidTargetBlockerScenarioCount,
			int unexpectedTargetBlockerScenarioCount,
			int runtimeCouplingLeakBlockerScenarioCount,
			int maxMissingTargetCount,
			int maxInvalidTargetCount,
			int maxUnexpectedTargetCount,
			double minRequiredMaxSamplePeriodSeconds,
			double maxRequiredSampleRateHertz
	) {
	}

	public record PoweredCruiseSkewWakeReadinessBlockerAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeReadinessBlockerScenario> scenarios,
			PoweredCruiseSkewWakeReadinessBlockerExtrema extrema
	) {
		public PoweredCruiseSkewWakeReadinessBlockerAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredCruiseSkewWakeReadinessBlockerAudit audit() {
		return audit(Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.audit());
	}

	public static PoweredCruiseSkewWakeReadinessBlockerAudit audit(
			Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessAudit readinessAudit
	) {
		if (readinessAudit == null) {
			throw new IllegalArgumentException("readinessAudit must not be null.");
		}
		List<PoweredCruiseSkewWakeReadinessBlockerScenario> scenarios = readinessAudit.scenarios().stream()
				.map(scenario -> new PoweredCruiseSkewWakeReadinessBlockerScenario(
						scenario.scenarioName(),
						report(scenario.summary())))
				.toList();
		return new PoweredCruiseSkewWakeReadinessBlockerAudit(
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

	public static PoweredCruiseSkewWakeReadinessBlockerSummary report(
			Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary readiness
	) {
		if (readiness == null) {
			throw new IllegalArgumentException("readiness summary must not be null.");
		}
		boolean poweredSourceApiBlocker = !readiness.poweredSourceApiAvailable();
		boolean skewWakeProbeApiBlocker = !readiness.skewWakeProbeApiAvailable();
		boolean transientProbeApiBlocker = !readiness.transientProbeApiAvailable();
		boolean coverageBlocker = !readiness.allExpectedTargetsPresent();
		boolean targetProbeApiBlocker = !coverageBlocker && !readiness.allTargetProbeApisAvailable();
		boolean temporalBlocker = !readiness.temporalResolutionSufficient();
		boolean invalidBlocker = readiness.invalidTargetCount() > 0;
		boolean unexpectedBlocker = readiness.unexpectedTargetCount() > 0;
		boolean runtimeLeakBlocker = !readiness.runtimeCouplingBlockedForAllTargets();
		int blockerCount = countTrue(
				poweredSourceApiBlocker,
				skewWakeProbeApiBlocker,
				transientProbeApiBlocker,
				coverageBlocker,
				targetProbeApiBlocker,
				temporalBlocker,
				invalidBlocker,
				unexpectedBlocker,
				runtimeLeakBlocker);
		boolean allowed = readiness.liveValidationRunAllowed() && blockerCount == 0;
		return new PoweredCruiseSkewWakeReadinessBlockerSummary(
				allowed,
				blockerCount,
				poweredSourceApiBlocker,
				skewWakeProbeApiBlocker,
				transientProbeApiBlocker,
				coverageBlocker,
				targetProbeApiBlocker,
				temporalBlocker,
				invalidBlocker,
				unexpectedBlocker,
				runtimeLeakBlocker,
				readiness.expectedTargetCount(),
				readiness.observedTargetCount(),
				readiness.sourceTermTargetCount(),
				readiness.centerlineSweepTargetCount(),
				readiness.lateralSweepTargetCount(),
				readiness.transientProbeApiAvailableTargetCount(),
				readiness.runtimeCouplingAllowedTargetCount(),
				readiness.invalidTargetCount(),
				readiness.missingTargetCount(),
				readiness.unexpectedTargetCount(),
				readiness.appliedMaxSamplePeriodSeconds(),
				readiness.requiredMaxSamplePeriodSeconds(),
				readiness.maxRequiredSampleRateHertz(),
				nextRequiredAction(
						poweredSourceApiBlocker,
						skewWakeProbeApiBlocker,
						transientProbeApiBlocker,
						coverageBlocker,
						invalidBlocker,
						unexpectedBlocker,
						targetProbeApiBlocker,
						temporalBlocker,
						runtimeLeakBlocker),
				allowed ? "READY" : "BLOCKED",
				allowed ? "cruise-skew-wake-readiness-clear" : "cruise-skew-wake-readiness-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean poweredSourceApiBlocker,
			boolean skewWakeProbeApiBlocker,
			boolean transientProbeApiBlocker,
			boolean targetCoverageBlocker,
			boolean invalidTargetBlocker,
			boolean unexpectedTargetBlocker,
			boolean targetProbeApiBlocker,
			boolean temporalResolutionBlocker,
			boolean runtimeCouplingLeakBlocker
	) {
		if (poweredSourceApiBlocker) {
			return "wait-for-powered-source-api-before-cruise-skew-wake-validation";
		}
		if (skewWakeProbeApiBlocker) {
			return "wait-for-local-cruise-skew-wake-probe-api";
		}
		if (transientProbeApiBlocker) {
			return "wait-for-transient-cruise-skew-wake-probe-api";
		}
		if (targetCoverageBlocker || invalidTargetBlocker || unexpectedTargetBlocker) {
			return "restore-complete-cruise-skew-wake-target-set";
		}
		if (targetProbeApiBlocker) {
			return "make-every-cruise-skew-wake-target-probe-api-backed";
		}
		if (temporalResolutionBlocker) {
			return "substep-cruise-skew-wake-validation-to-fast-arrival-window";
		}
		if (runtimeCouplingLeakBlocker) {
			return "close-runtime-coupling-before-cruise-skew-wake-validation";
		}
		return "cruise-skew-wake-readiness-ready-for-live-validation";
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

	private static PoweredCruiseSkewWakeReadinessBlockerExtrema extrema(
			List<PoweredCruiseSkewWakeReadinessBlockerScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int poweredSource = 0;
		int skewWakeProbe = 0;
		int transientProbe = 0;
		int coverage = 0;
		int targetProbe = 0;
		int temporal = 0;
		int invalid = 0;
		int unexpected = 0;
		int runtimeLeak = 0;
		int maxMissing = 0;
		int maxInvalid = 0;
		int maxUnexpected = 0;
		double minRequiredPeriod = Double.POSITIVE_INFINITY;
		double maxRequiredRate = 0.0;
		for (PoweredCruiseSkewWakeReadinessBlockerScenario scenario : scenarios) {
			PoweredCruiseSkewWakeReadinessBlockerSummary summary = scenario.summary();
			if (summary.liveValidationRunAllowed()) {
				ready++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.poweredSourceApiBlocker()) {
				poweredSource++;
			}
			if (summary.skewWakeProbeApiBlocker()) {
				skewWakeProbe++;
			}
			if (summary.transientProbeApiBlocker()) {
				transientProbe++;
			}
			if (summary.targetCoverageBlocker()) {
				coverage++;
			}
			if (summary.targetProbeApiBlocker()) {
				targetProbe++;
			}
			if (summary.temporalResolutionBlocker()) {
				temporal++;
			}
			if (summary.invalidTargetBlocker()) {
				invalid++;
			}
			if (summary.unexpectedTargetBlocker()) {
				unexpected++;
			}
			if (summary.runtimeCouplingLeakBlocker()) {
				runtimeLeak++;
			}
			maxMissing = Math.max(maxMissing, summary.missingTargetCount());
			maxInvalid = Math.max(maxInvalid, summary.invalidTargetCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedTargetCount());
			if (summary.requiredMaxSamplePeriodSeconds() > 0.0) {
				minRequiredPeriod = Math.min(minRequiredPeriod, summary.requiredMaxSamplePeriodSeconds());
			}
			maxRequiredRate = Math.max(maxRequiredRate, summary.maxRequiredSampleRateHertz());
		}
		return new PoweredCruiseSkewWakeReadinessBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				poweredSource,
				skewWakeProbe,
				transientProbe,
				coverage,
				targetProbe,
				temporal,
				invalid,
				unexpected,
				runtimeLeak,
				maxMissing,
				maxInvalid,
				maxUnexpected,
				scenarios.isEmpty() ? 0.0 : minRequiredPeriod,
				maxRequiredRate
		);
	}
}
