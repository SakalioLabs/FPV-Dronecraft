package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Readiness-Blocker-Report-Packet";
	public static final String CAVEAT =
			"Readiness blocker report decomposes live hover surface-wake validation blockers into audit reasons only; it does not invoke A4MC, export gameplay tuning data, or enable runtime coupling.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.SCENARIO_SAMPLE_COUNT;
	public static final int SCENARIO_METRIC_COUNT = 25;
	public static final int SUMMARY_METRIC_ROW_COUNT = 17;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessBlockerReport() {
	}

	public record PoweredHoverSurfaceWakeReadinessBlockerSummary(
			boolean liveValidationRunAllowed,
			int blockerCount,
			boolean poweredSourceApiBlocker,
			boolean transientProbeApiBlocker,
			boolean targetCoverageBlocker,
			boolean targetProbeApiBlocker,
			boolean temporalResolutionBlocker,
			boolean invalidTargetBlocker,
			boolean unexpectedTargetBlocker,
			boolean runtimeCouplingLeakBlocker,
			int expectedTargetCount,
			int observedTargetCount,
			int groundTargetCount,
			int ceilingTargetCount,
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

	public record PoweredHoverSurfaceWakeReadinessBlockerScenario(
			String scenarioName,
			PoweredHoverSurfaceWakeReadinessBlockerSummary summary
	) {
	}

	public record PoweredHoverSurfaceWakeReadinessBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int poweredSourceApiBlockerScenarioCount,
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

	public record PoweredHoverSurfaceWakeReadinessBlockerAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeReadinessBlockerScenario> scenarios,
			PoweredHoverSurfaceWakeReadinessBlockerExtrema extrema
	) {
		public PoweredHoverSurfaceWakeReadinessBlockerAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredHoverSurfaceWakeReadinessBlockerAudit audit() {
		return audit(Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.audit());
	}

	public static PoweredHoverSurfaceWakeReadinessBlockerAudit audit(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessAudit readinessAudit
	) {
		if (readinessAudit == null) {
			throw new IllegalArgumentException("readinessAudit must not be null.");
		}
		List<PoweredHoverSurfaceWakeReadinessBlockerScenario> scenarios = readinessAudit.scenarios().stream()
				.map(scenario -> new PoweredHoverSurfaceWakeReadinessBlockerScenario(
						scenario.scenarioName(),
						report(scenario.summary())))
				.toList();
		return new PoweredHoverSurfaceWakeReadinessBlockerAudit(
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

	public static PoweredHoverSurfaceWakeReadinessBlockerSummary report(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessSummary readiness
	) {
		if (readiness == null) {
			throw new IllegalArgumentException("readiness summary must not be null.");
		}
		boolean poweredSourceApiBlocker = !readiness.poweredSourceApiAvailable();
		boolean transientProbeApiBlocker = !readiness.transientProbeApiAvailable();
		boolean coverageBlocker = !readiness.allExpectedTargetsPresent();
		boolean targetProbeApiBlocker = !coverageBlocker && !readiness.allTargetProbeApisAvailable();
		boolean temporalBlocker = !readiness.temporalResolutionSufficient();
		boolean invalidBlocker = readiness.invalidTargetCount() > 0;
		boolean unexpectedBlocker = readiness.unexpectedTargetCount() > 0;
		boolean runtimeLeakBlocker = !readiness.runtimeCouplingBlockedForAllTargets();
		int blockerCount = countTrue(
				poweredSourceApiBlocker,
				transientProbeApiBlocker,
				coverageBlocker,
				targetProbeApiBlocker,
				temporalBlocker,
				invalidBlocker,
				unexpectedBlocker,
				runtimeLeakBlocker);
		boolean allowed = readiness.liveValidationRunAllowed() && blockerCount == 0;
		return new PoweredHoverSurfaceWakeReadinessBlockerSummary(
				allowed,
				blockerCount,
				poweredSourceApiBlocker,
				transientProbeApiBlocker,
				coverageBlocker,
				targetProbeApiBlocker,
				temporalBlocker,
				invalidBlocker,
				unexpectedBlocker,
				runtimeLeakBlocker,
				readiness.expectedTargetCount(),
				readiness.observedTargetCount(),
				readiness.groundTargetCount(),
				readiness.ceilingTargetCount(),
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
						transientProbeApiBlocker,
						coverageBlocker,
						invalidBlocker,
						unexpectedBlocker,
						targetProbeApiBlocker,
						temporalBlocker,
						runtimeLeakBlocker),
				allowed ? "READY" : "BLOCKED",
				allowed ? "hover-surface-wake-readiness-clear" : "hover-surface-wake-readiness-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean poweredSourceApiBlocker,
			boolean transientProbeApiBlocker,
			boolean targetCoverageBlocker,
			boolean invalidTargetBlocker,
			boolean unexpectedTargetBlocker,
			boolean targetProbeApiBlocker,
			boolean temporalResolutionBlocker,
			boolean runtimeCouplingLeakBlocker
	) {
		if (poweredSourceApiBlocker) {
			return "wait-for-powered-source-api-before-surface-wake-validation";
		}
		if (transientProbeApiBlocker) {
			return "wait-for-transient-surface-wake-probe-api";
		}
		if (targetCoverageBlocker || invalidTargetBlocker || unexpectedTargetBlocker) {
			return "restore-complete-hover-surface-wake-target-set";
		}
		if (targetProbeApiBlocker) {
			return "make-every-surface-wake-target-probe-api-backed";
		}
		if (temporalResolutionBlocker) {
			return "substep-hover-surface-wake-validation-to-fast-arrival-window";
		}
		if (runtimeCouplingLeakBlocker) {
			return "close-runtime-coupling-before-surface-wake-validation";
		}
		return "hover-surface-wake-readiness-ready-for-live-validation";
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

	private static PoweredHoverSurfaceWakeReadinessBlockerExtrema extrema(
			List<PoweredHoverSurfaceWakeReadinessBlockerScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int poweredSource = 0;
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
		for (PoweredHoverSurfaceWakeReadinessBlockerScenario scenario : scenarios) {
			PoweredHoverSurfaceWakeReadinessBlockerSummary summary = scenario.summary();
			if (summary.liveValidationRunAllowed()) {
				ready++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.poweredSourceApiBlocker()) {
				poweredSource++;
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
		return new PoweredHoverSurfaceWakeReadinessBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				poweredSource,
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
