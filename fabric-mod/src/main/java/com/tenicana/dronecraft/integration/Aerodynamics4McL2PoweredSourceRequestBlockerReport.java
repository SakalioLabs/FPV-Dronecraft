package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceRequestBlockerReport {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Request-Blocker-Report-Packet";
	public static final String CAVEAT =
			"Request blocker report decomposes powered-source request readiness, API-surface blockers, and physical source-term contract blockers into audit reasons only; it does not build A4MC requests or enable runtime coupling.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredSourceRequestReadinessGate.SCENARIO_SAMPLE_COUNT;
	public static final int SCENARIO_METRIC_COUNT = 30;
	public static final int SUMMARY_METRIC_ROW_COUNT = 16;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceRequestBlockerReport() {
	}

	public record PoweredSourceRequestBlockerSummary(
			boolean requestExecutionAllowed,
			int blockerCount,
			boolean poweredSourceApiBlocker,
			boolean poweredSourceApiSurfaceBlocker,
			boolean poweredSourcePhysicalContractBlocker,
			boolean hoverAcceptanceGateBlocker,
			boolean cruiseAcceptanceGateBlocker,
			boolean requestPresenceBlocker,
			boolean requestBuildBlocker,
			boolean requestApiBlocker,
			boolean invalidRequestBlocker,
			boolean unexpectedRequestBlocker,
			int expectedRequestCount,
			int observedRequestCount,
			int missingRequestCount,
			int unexpectedRequestCount,
			int invalidRequestCount,
			int buildAllowedRequestCount,
			int apiAvailableRequestCount,
			int poweredSourceApiSurfaceCount,
			int requiredPoweredSourceApiSurfaceCount,
			String missingPoweredSourceApiList,
			int poweredSourcePhysicalContractCount,
			int requiredPoweredSourcePhysicalContractCount,
			String missingPoweredSourcePhysicalContractList,
			int hoverRequestCount,
			int cruiseRequestCount,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceRequestBlockerScenario(
			String scenarioName,
			PoweredSourceRequestBlockerSummary summary
	) {
	}

	public record PoweredSourceRequestBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int poweredSourceApiBlockerScenarioCount,
			int poweredSourceApiSurfaceBlockerScenarioCount,
			int poweredSourcePhysicalContractBlockerScenarioCount,
			int hoverAcceptanceGateBlockerScenarioCount,
			int cruiseAcceptanceGateBlockerScenarioCount,
			int requestPresenceBlockerScenarioCount,
			int requestBuildBlockerScenarioCount,
			int requestApiBlockerScenarioCount,
			int invalidRequestBlockerScenarioCount,
			int unexpectedRequestBlockerScenarioCount,
			int maxPoweredSourceApiSurfaceCount,
			int maxPoweredSourcePhysicalContractCount
	) {
	}

	public record PoweredSourceRequestBlockerAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceRequestBlockerScenario> scenarios,
			PoweredSourceRequestBlockerExtrema extrema
	) {
		public PoweredSourceRequestBlockerAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceRequestBlockerAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceRequestReadinessGate.audit());
	}

	public static PoweredSourceRequestBlockerAudit audit(
			Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessAudit readinessAudit
	) {
		if (readinessAudit == null) {
			throw new IllegalArgumentException("readinessAudit must not be null.");
		}
		List<PoweredSourceRequestBlockerScenario> scenarios = readinessAudit.scenarios().stream()
				.map(scenario -> new PoweredSourceRequestBlockerScenario(
						scenario.scenarioName(),
						report(scenario.summary())))
				.toList();
		return new PoweredSourceRequestBlockerAudit(
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

	public static PoweredSourceRequestBlockerSummary report(
			Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary readiness
	) {
		if (readiness == null) {
			throw new IllegalArgumentException("readiness summary must not be null.");
		}
		boolean sourceApiBlocker = !readiness.poweredSourceApiAvailable();
		boolean sourceApiSurfaceBlocker =
				readiness.poweredSourceApiSurfaceCount() < readiness.requiredPoweredSourceApiSurfaceCount();
		boolean physicalContractBlocker = !readiness.poweredSourcePhysicalContractReady();
		boolean hoverAcceptanceBlocker = !readiness.poweredHoverAcceptanceGateOpen();
		boolean cruiseAcceptanceBlocker = !readiness.poweredCruiseAcceptanceGateOpen();
		boolean presenceBlocker = !readiness.allExpectedRequestsPresent();
		boolean buildBlocker = !presenceBlocker && !readiness.allRequestsBuildAllowed();
		boolean requestApiBlocker = !presenceBlocker && !readiness.allRequestApisAvailable();
		boolean invalidBlocker = readiness.invalidRequestCount() > 0;
		boolean unexpectedBlocker = readiness.unexpectedRequestCount() > 0;
		int blockerCount = countTrue(
				sourceApiSurfaceBlocker,
				physicalContractBlocker,
				hoverAcceptanceBlocker,
				cruiseAcceptanceBlocker,
				presenceBlocker,
				buildBlocker,
				requestApiBlocker,
				invalidBlocker,
				unexpectedBlocker);
		boolean allowed = readiness.requestExecutionAllowed() && blockerCount == 0;
		return new PoweredSourceRequestBlockerSummary(
				allowed,
				blockerCount,
				sourceApiBlocker,
				sourceApiSurfaceBlocker,
				physicalContractBlocker,
				hoverAcceptanceBlocker,
				cruiseAcceptanceBlocker,
				presenceBlocker,
				buildBlocker,
				requestApiBlocker,
				invalidBlocker,
				unexpectedBlocker,
				readiness.expectedRequestCount(),
				readiness.observedRequestCount(),
				readiness.missingRequestCount(),
				readiness.unexpectedRequestCount(),
				readiness.invalidRequestCount(),
				readiness.buildAllowedRequestCount(),
				readiness.apiAvailableRequestCount(),
				readiness.poweredSourceApiSurfaceCount(),
				readiness.requiredPoweredSourceApiSurfaceCount(),
				readiness.missingPoweredSourceApiList(),
				readiness.poweredSourcePhysicalContractCount(),
				readiness.requiredPoweredSourcePhysicalContractCount(),
				readiness.missingPoweredSourcePhysicalContractList(),
				readiness.hoverRequestCount(),
				readiness.cruiseRequestCount(),
				nextRequiredAction(
						sourceApiSurfaceBlocker,
						physicalContractBlocker,
						hoverAcceptanceBlocker,
						cruiseAcceptanceBlocker,
						presenceBlocker,
						invalidBlocker,
						unexpectedBlocker,
						requestApiBlocker,
						buildBlocker),
				allowed ? "READY" : "BLOCKED",
				allowed ? "powered-source-requests-clear" : "powered-source-requests-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean sourceApiSurfaceBlocker,
			boolean physicalContractBlocker,
			boolean hoverAcceptanceBlocker,
			boolean cruiseAcceptanceBlocker,
			boolean presenceBlocker,
			boolean invalidBlocker,
			boolean unexpectedBlocker,
			boolean requestApiBlocker,
			boolean buildBlocker
	) {
		if (sourceApiSurfaceBlocker) {
			return "wait-for-powered-source-api-surface";
		}
		if (physicalContractBlocker) {
			return "wait-for-powered-source-physical-contract";
		}
		if (hoverAcceptanceBlocker || cruiseAcceptanceBlocker) {
			return "open-hover-and-cruise-powered-acceptance-gates";
		}
		if (presenceBlocker) {
			return "restore-complete-hover-and-cruise-request-envelope-set";
		}
		if (invalidBlocker || unexpectedBlocker) {
			return "repair-request-envelope-identity-and-physical-shape";
		}
		if (requestApiBlocker) {
			return "mark-every-request-api-backed-after-source-api-probe";
		}
		if (buildBlocker) {
			return "allow-request-build-only-after-api-backed-validation";
		}
		return "powered-source-requests-ready-for-live-executor";
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

	private static PoweredSourceRequestBlockerExtrema extrema(
			List<PoweredSourceRequestBlockerScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int source = 0;
		int sourceSurface = 0;
		int physicalContract = 0;
		int hover = 0;
		int cruise = 0;
		int presence = 0;
		int build = 0;
		int requestApi = 0;
		int invalid = 0;
		int unexpected = 0;
		int maxPoweredSourceApiSurface = 0;
		int maxPhysicalContract = 0;
		for (PoweredSourceRequestBlockerScenario scenario : scenarios) {
			PoweredSourceRequestBlockerSummary summary = scenario.summary();
			if (summary.requestExecutionAllowed()) {
				ready++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.poweredSourceApiBlocker()) {
				source++;
			}
			if (summary.poweredSourceApiSurfaceBlocker()) {
				sourceSurface++;
			}
			if (summary.poweredSourcePhysicalContractBlocker()) {
				physicalContract++;
			}
			if (summary.hoverAcceptanceGateBlocker()) {
				hover++;
			}
			if (summary.cruiseAcceptanceGateBlocker()) {
				cruise++;
			}
			if (summary.requestPresenceBlocker()) {
				presence++;
			}
			if (summary.requestBuildBlocker()) {
				build++;
			}
			if (summary.requestApiBlocker()) {
				requestApi++;
			}
			if (summary.invalidRequestBlocker()) {
				invalid++;
			}
			if (summary.unexpectedRequestBlocker()) {
				unexpected++;
			}
			maxPoweredSourceApiSurface = Math.max(
					maxPoweredSourceApiSurface,
					summary.poweredSourceApiSurfaceCount());
			maxPhysicalContract = Math.max(maxPhysicalContract, summary.poweredSourcePhysicalContractCount());
		}
		return new PoweredSourceRequestBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				source,
				sourceSurface,
				physicalContract,
				hover,
				cruise,
				presence,
				build,
				requestApi,
				invalid,
				unexpected,
				maxPoweredSourceApiSurface,
				maxPhysicalContract
		);
	}
}
