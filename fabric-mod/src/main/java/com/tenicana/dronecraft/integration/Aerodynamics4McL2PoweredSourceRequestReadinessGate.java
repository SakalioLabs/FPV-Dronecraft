package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredSourceRequestReadinessGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Request-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"Gate remains closed until the A4MC powered-source API surface audit includes physical source-term contract readiness, hover and cruise acceptance gates are open, and every powered-source request envelope is present, valid, API-backed, and buildable.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_COUNT = 27;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceRequestReadinessGate() {
	}

	public record PoweredSourceRequestReadinessSummary(
			boolean poweredSourceApiAvailable,
			boolean poweredSourceApiSurfaceReady,
			boolean poweredSourceExecutorWiringAllowed,
			int poweredSourceApiSurfaceCount,
			int requiredPoweredSourceApiSurfaceCount,
			String missingPoweredSourceApiList,
			boolean poweredSourcePhysicalContractReady,
			int poweredSourcePhysicalContractCount,
			int requiredPoweredSourcePhysicalContractCount,
			String missingPoweredSourcePhysicalContractList,
			boolean poweredHoverAcceptanceGateOpen,
			boolean poweredCruiseAcceptanceGateOpen,
			int expectedRequestCount,
			int observedRequestCount,
			int hoverRequestCount,
			int cruiseRequestCount,
			int buildAllowedRequestCount,
			int apiAvailableRequestCount,
			int invalidRequestCount,
			int missingRequestCount,
			int unexpectedRequestCount,
			boolean allExpectedRequestsPresent,
			boolean allRequestsBuildAllowed,
			boolean allRequestApisAvailable,
			boolean bothAcceptanceGatesOpen,
			boolean requestExecutionAllowed,
			String status
	) {
	}

	public record PoweredSourceRequestReadinessScenario(
			String scenarioName,
			PoweredSourceRequestReadinessSummary summary
	) {
	}

	public record PoweredSourceRequestReadinessExtrema(
			int scenarioCount,
			int allowedScenarioCount,
			int blockedScenarioCount,
			int poweredSourceApiSurfaceReadyScenarioCount,
			int maxExpectedRequestCount,
			int maxPoweredSourceApiSurfaceCount,
			int poweredSourcePhysicalContractReadyScenarioCount,
			int maxPoweredSourcePhysicalContractCount,
			int maxMissingRequestCount,
			int maxInvalidRequestCount,
			int maxUnexpectedRequestCount,
			int maxBuildAllowedRequestCount
	) {
	}

	public record PoweredSourceRequestReadinessAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceRequestReadinessScenario> scenarios,
			PoweredSourceRequestReadinessExtrema extrema
	) {
		public PoweredSourceRequestReadinessAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceRequestReadinessAudit audit() {
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> current =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests();
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> buildable =
				current.stream()
						.map(request -> copy(request, true, true))
						.toList();
		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary currentApiSurface =
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.currentSummary();
		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary readyApiSurface =
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary();
		List<PoweredSourceRequestReadinessScenario> scenarios = List.of(
				new PoweredSourceRequestReadinessScenario(
						"current_api_unavailable_requests_blocked",
						gate(currentApiSurface, false, false, current)
				),
				new PoweredSourceRequestReadinessScenario(
						"api_available_acceptance_open_requests_buildable",
						gate(readyApiSurface, true, true, buildable)
				),
				new PoweredSourceRequestReadinessScenario(
						"api_available_acceptance_open_one_request_missing",
						gate(readyApiSurface, true, true, buildable.subList(0, buildable.size() - 1))
				),
				new PoweredSourceRequestReadinessScenario(
						"api_available_acceptance_open_build_blocked",
						gate(readyApiSurface, true, true, current)
				),
				new PoweredSourceRequestReadinessScenario(
						"api_available_requests_buildable_acceptance_closed",
						gate(readyApiSurface, false, true, buildable)
				)
		);
		return new PoweredSourceRequestReadinessAudit(
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

	public static PoweredSourceRequestReadinessSummary gate(
			boolean poweredSourceApiAvailable,
			boolean poweredHoverAcceptanceGateOpen,
			boolean poweredCruiseAcceptanceGateOpen,
			List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests
	) {
		return gate(
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.currentSummary(),
				poweredSourceApiAvailable,
				poweredHoverAcceptanceGateOpen,
				poweredCruiseAcceptanceGateOpen,
				requests
		);
	}

	public static PoweredSourceRequestReadinessSummary gate(
			Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary apiSurface,
			boolean poweredHoverAcceptanceGateOpen,
			boolean poweredCruiseAcceptanceGateOpen,
			List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests
	) {
		return gate(apiSurface, true, poweredHoverAcceptanceGateOpen, poweredCruiseAcceptanceGateOpen, requests);
	}

	private static PoweredSourceRequestReadinessSummary gate(
			Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary apiSurface,
			boolean poweredSourceApiProbeAllowed,
			boolean poweredHoverAcceptanceGateOpen,
			boolean poweredCruiseAcceptanceGateOpen,
			List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests
	) {
		if (apiSurface == null) {
			throw new IllegalArgumentException("apiSurface must not be null.");
		}
		if (requests == null) {
			throw new IllegalArgumentException("requests must not be null.");
		}
		Set<String> expected = expectedRequestNames();
		Set<String> observed = new HashSet<>();
		int hover = 0;
		int cruise = 0;
		int buildAllowed = 0;
		int apiAvailable = 0;
		int invalid = 0;
		int unexpected = 0;
		for (Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request : requests) {
			if (request == null || request.presetName() == null || request.presetName().isBlank()
					|| request.spinState() == null || request.spinState().isBlank()) {
				throw new IllegalArgumentException("requests must include stable preset and spin-state names.");
			}
			String name = request.presetName() + ":" + request.spinState();
			if (!observed.add(name)) {
				throw new IllegalArgumentException("duplicate powered-source request: " + name);
			}
			if (!expected.contains(name)) {
				unexpected++;
			}
			if ("hover".equals(request.spinState())) {
				hover++;
			} else if ("cruise".equals(request.spinState())) {
				cruise++;
			} else {
				invalid++;
			}
			if (request.requestBuildAllowed()) {
				buildAllowed++;
			}
			if (request.poweredSourceApiAvailable()) {
				apiAvailable++;
			}
			if (!validRequestEnvelope(request)) {
				invalid++;
			}
		}
		int missing = 0;
		for (String name : expected) {
			if (!observed.contains(name)) {
				missing++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && observed.size() == expected.size();
		boolean allBuildAllowed = buildAllowed == expected.size();
		boolean allApisAvailable = apiAvailable == expected.size();
		boolean poweredSourceApiAvailable = poweredSourceApiProbeAllowed && apiSurface.poweredSourceApiReady();
		boolean poweredSourceExecutorWiringAllowed =
				poweredSourceApiProbeAllowed && apiSurface.poweredSourceExecutorWiringAllowed();
		boolean poweredSourceApiSurfaceReady = poweredSourceApiAvailable && poweredSourceExecutorWiringAllowed;
		boolean bothAcceptanceOpen = poweredHoverAcceptanceGateOpen && poweredCruiseAcceptanceGateOpen;
		boolean allowed = poweredSourceApiSurfaceReady
				&& bothAcceptanceOpen
				&& allPresent
				&& invalid == 0
				&& allBuildAllowed
				&& allApisAvailable;
		return new PoweredSourceRequestReadinessSummary(
				poweredSourceApiAvailable,
				poweredSourceApiSurfaceReady,
				poweredSourceExecutorWiringAllowed,
				apiSurface.poweredSourceApiSurfaceCount(),
				apiSurface.requiredPoweredSourceApiSurfaceCount(),
				apiSurface.missingPoweredSourceApiList(),
				apiSurface.poweredSourcePhysicalContractReady(),
				apiSurface.poweredSourcePhysicalContractCount(),
				apiSurface.requiredPoweredSourcePhysicalContractCount(),
				apiSurface.missingPoweredSourcePhysicalContractList(),
				poweredHoverAcceptanceGateOpen,
				poweredCruiseAcceptanceGateOpen,
				expected.size(),
				requests.size(),
				hover,
				cruise,
				buildAllowed,
				apiAvailable,
				invalid,
				missing,
				unexpected,
				allPresent,
				allBuildAllowed,
				allApisAvailable,
				bothAcceptanceOpen,
				allowed,
				allowed ? "READY_FOR_POWERED_SOURCE_REQUEST_EXECUTION" : "BLOCKED"
		);
	}

	private static boolean validRequestEnvelope(
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request
	) {
		if (request.rotorCount() <= 0 || request.sourceTermCount() != request.rotorCount()) {
			return false;
		}
		if (request.gridCellCount() <= 0 || request.steps() <= 0 || request.cellSizeMeters() <= 0.0) {
			return false;
		}
		if (!request.baselineForceMomentRequest() || !request.poweredSourceApiRequired()) {
			return false;
		}
		if (request.totalThrustNewtons() <= 0.0 || request.meanPressureJumpPascals() <= 0.0) {
			return false;
		}
		if ("hover".equals(request.spinState())) {
			return Aerodynamics4McL2PoweredHoverSourceMap.SOURCE_ID.equals(request.sourceMapId())
					&& Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID.equals(request.validationPacketId())
					&& Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID.equals(request.acceptanceGatePacketId());
		}
		if ("cruise".equals(request.spinState())) {
			return Aerodynamics4McL2PoweredCruiseSourceMap.SOURCE_ID.equals(request.sourceMapId())
					&& Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID.equals(request.validationPacketId())
					&& Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID.equals(request.acceptanceGatePacketId());
		}
		return false;
	}

	private static Set<String> expectedRequestNames() {
		return Set.of(
				"racingQuad:hover",
				"apDrone:hover",
				"cinewhoop:hover",
				"heavyLift:hover",
				"racingQuad:cruise",
				"apDrone:cruise",
				"cinewhoop:cruise",
				"heavyLift:cruise"
		);
	}

	private static Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest copy(
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request,
			boolean poweredSourceApiAvailable,
			boolean requestBuildAllowed
	) {
		return new Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest(
				request.presetName(),
				request.spinState(),
				request.sourceMapId(),
				request.validationPacketId(),
				request.acceptanceGatePacketId(),
				request.rotorCount(),
				request.sourceTermCount(),
				request.nx(),
				request.ny(),
				request.nz(),
				request.gridCellCount(),
				request.cellSizeMeters(),
				request.steps(),
				request.inletVxMetersPerSecond(),
				request.inletVyMetersPerSecond(),
				request.inletVzMetersPerSecond(),
				request.inletSpeedMetersPerSecond(),
				request.spinRatio(),
				request.totalThrustNewtons(),
				request.thrustToWeight(),
				request.totalOpenAreaSquareMeters(),
				request.meanPressureJumpPascals(),
				request.maxPressureJumpPascals(),
				request.netForceXNewtons(),
				request.netForceYNewtons(),
				request.netForceZNewtons(),
				request.netForceMagnitudeNewtons(),
				request.netMomentXNewtonMeters(),
				request.netMomentYNewtonMeters(),
				request.netMomentZNewtonMeters(),
				request.netMomentMagnitudeNewtonMeters(),
				request.centerOfThrustOffsetMeters(),
				request.baselineForceMomentRequest(),
				request.poweredSourceApiRequired(),
				poweredSourceApiAvailable,
				requestBuildAllowed,
				requestBuildAllowed
						? "synthetic-powered-source-api-buildable"
						: request.runtimeInfo()
		);
	}

	private static PoweredSourceRequestReadinessExtrema extrema(List<PoweredSourceRequestReadinessScenario> scenarios) {
		int allowed = 0;
		int apiSurfaceReady = 0;
		int maxExpected = 0;
		int maxPoweredSourceApiSurface = 0;
		int physicalContractReady = 0;
		int maxPhysicalContract = 0;
		int maxMissing = 0;
		int maxInvalid = 0;
		int maxUnexpected = 0;
		int maxBuildAllowed = 0;
		for (PoweredSourceRequestReadinessScenario scenario : scenarios) {
			PoweredSourceRequestReadinessSummary summary = scenario.summary();
			if (summary.requestExecutionAllowed()) {
				allowed++;
			}
			if (summary.poweredSourceApiSurfaceReady()) {
				apiSurfaceReady++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedRequestCount());
			maxPoweredSourceApiSurface = Math.max(
					maxPoweredSourceApiSurface,
					summary.poweredSourceApiSurfaceCount());
			if (summary.poweredSourcePhysicalContractReady()) {
				physicalContractReady++;
			}
			maxPhysicalContract = Math.max(maxPhysicalContract, summary.poweredSourcePhysicalContractCount());
			maxMissing = Math.max(maxMissing, summary.missingRequestCount());
			maxInvalid = Math.max(maxInvalid, summary.invalidRequestCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedRequestCount());
			maxBuildAllowed = Math.max(maxBuildAllowed, summary.buildAllowedRequestCount());
		}
		return new PoweredSourceRequestReadinessExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				apiSurfaceReady,
				maxExpected,
				maxPoweredSourceApiSurface,
				physicalContractReady,
				maxPhysicalContract,
				maxMissing,
				maxInvalid,
				maxUnexpected,
				maxBuildAllowed
		);
	}
}
