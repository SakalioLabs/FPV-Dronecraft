package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceRequestReadinessGateTest {
	@Test
	void auditBuildsStableReadinessScenarios() {
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessAudit audit =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.audit();

		assertEquals("A4MC-L2-Powered-Source-Request-Readiness-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Gate remains closed"));
		assertEquals(155, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(27, audit.scenarioMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary current =
				findScenario(audit.scenarios(), "current_api_unavailable_requests_blocked").summary();
		assertFalse(current.poweredSourceApiAvailable());
		assertFalse(current.poweredSourceApiSurfaceReady());
		assertFalse(current.poweredSourceExecutorWiringAllowed());
		assertEquals(0, current.poweredSourceApiSurfaceCount());
		assertEquals(5, current.requiredPoweredSourceApiSurfaceCount());
		assertTrue(current.missingPoweredSourceApiList().contains("body_force_source_api"));
		assertFalse(current.poweredSourcePhysicalContractReady());
		assertEquals(0, current.poweredSourcePhysicalContractCount());
		assertEquals(5, current.requiredPoweredSourcePhysicalContractCount());
		assertTrue(current.missingPoweredSourcePhysicalContractList().contains("source_term_si_units"));
		assertFalse(current.poweredHoverAcceptanceGateOpen());
		assertFalse(current.poweredCruiseAcceptanceGateOpen());
		assertEquals(8, current.expectedRequestCount());
		assertEquals(8, current.observedRequestCount());
		assertEquals(4, current.hoverRequestCount());
		assertEquals(4, current.cruiseRequestCount());
		assertEquals(0, current.buildAllowedRequestCount());
		assertEquals(0, current.apiAvailableRequestCount());
		assertEquals(0, current.invalidRequestCount());
		assertEquals(0, current.missingRequestCount());
		assertEquals(0, current.unexpectedRequestCount());
		assertTrue(current.allExpectedRequestsPresent());
		assertFalse(current.allRequestsBuildAllowed());
		assertFalse(current.allRequestApisAvailable());
		assertFalse(current.bothAcceptanceGatesOpen());
		assertFalse(current.requestExecutionAllowed());
		assertEquals("BLOCKED", current.status());

		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary ready =
				findScenario(audit.scenarios(), "api_available_acceptance_open_requests_buildable").summary();
		assertTrue(ready.poweredSourceApiAvailable());
		assertTrue(ready.poweredSourceApiSurfaceReady());
		assertTrue(ready.poweredSourceExecutorWiringAllowed());
		assertEquals(5, ready.poweredSourceApiSurfaceCount());
		assertEquals(5, ready.requiredPoweredSourceApiSurfaceCount());
		assertEquals("none", ready.missingPoweredSourceApiList());
		assertTrue(ready.poweredSourcePhysicalContractReady());
		assertEquals(5, ready.poweredSourcePhysicalContractCount());
		assertEquals("none", ready.missingPoweredSourcePhysicalContractList());
		assertTrue(ready.poweredHoverAcceptanceGateOpen());
		assertTrue(ready.poweredCruiseAcceptanceGateOpen());
		assertEquals(8, ready.expectedRequestCount());
		assertEquals(8, ready.observedRequestCount());
		assertEquals(8, ready.buildAllowedRequestCount());
		assertEquals(8, ready.apiAvailableRequestCount());
		assertEquals(0, ready.invalidRequestCount());
		assertEquals(0, ready.missingRequestCount());
		assertEquals(0, ready.unexpectedRequestCount());
		assertTrue(ready.allExpectedRequestsPresent());
		assertTrue(ready.allRequestsBuildAllowed());
		assertTrue(ready.allRequestApisAvailable());
		assertTrue(ready.bothAcceptanceGatesOpen());
		assertTrue(ready.requestExecutionAllowed());
		assertEquals("READY_FOR_POWERED_SOURCE_REQUEST_EXECUTION", ready.status());

		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary missing =
				findScenario(audit.scenarios(), "api_available_acceptance_open_one_request_missing").summary();
		assertEquals(7, missing.observedRequestCount());
		assertEquals(1, missing.missingRequestCount());
		assertFalse(missing.allExpectedRequestsPresent());
		assertFalse(missing.requestExecutionAllowed());

		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary buildBlocked =
				findScenario(audit.scenarios(), "api_available_acceptance_open_build_blocked").summary();
		assertEquals(0, buildBlocked.buildAllowedRequestCount());
		assertEquals(0, buildBlocked.apiAvailableRequestCount());
		assertFalse(buildBlocked.allRequestsBuildAllowed());
		assertFalse(buildBlocked.requestExecutionAllowed());

		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary acceptanceClosed =
				findScenario(audit.scenarios(), "api_available_requests_buildable_acceptance_closed").summary();
		assertFalse(acceptanceClosed.poweredHoverAcceptanceGateOpen());
		assertTrue(acceptanceClosed.poweredCruiseAcceptanceGateOpen());
		assertFalse(acceptanceClosed.bothAcceptanceGatesOpen());
		assertFalse(acceptanceClosed.requestExecutionAllowed());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().allowedScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(4, audit.extrema().poweredSourceApiSurfaceReadyScenarioCount());
		assertEquals(8, audit.extrema().maxExpectedRequestCount());
		assertEquals(5, audit.extrema().maxPoweredSourceApiSurfaceCount());
		assertEquals(4, audit.extrema().poweredSourcePhysicalContractReadyScenarioCount());
		assertEquals(5, audit.extrema().maxPoweredSourcePhysicalContractCount());
		assertEquals(1, audit.extrema().maxMissingRequestCount());
		assertEquals(0, audit.extrema().maxInvalidRequestCount());
		assertEquals(0, audit.extrema().maxUnexpectedRequestCount());
		assertEquals(8, audit.extrema().maxBuildAllowedRequestCount());
	}

	@Test
	void gateAllowsOnlyWhenRequestsApiAcceptanceAndBuildAreAllReady() {
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> current =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests();
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> buildable =
				current.stream()
						.map(request -> copy(request, request.presetName(), request.spinState(), request.sourceMapId(),
								request.sourceTermCount(), true, true))
						.toList();
		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary readyApiSurface =
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary();

		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary ready =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(readyApiSurface, true, true, buildable);
		assertTrue(ready.requestExecutionAllowed());
		assertTrue(ready.poweredSourcePhysicalContractReady());

		assertFalse(Aerodynamics4McL2PoweredSourceRequestReadinessGate
				.gate(true, true, true, buildable).requestExecutionAllowed());
		assertFalse(Aerodynamics4McL2PoweredSourceRequestReadinessGate
				.gate(false, true, true, buildable).requestExecutionAllowed());
		assertFalse(Aerodynamics4McL2PoweredSourceRequestReadinessGate
				.gate(readyApiSurface, false, true, buildable).requestExecutionAllowed());
		assertFalse(Aerodynamics4McL2PoweredSourceRequestReadinessGate
				.gate(readyApiSurface, true, true, current).requestExecutionAllowed());
	}

	@Test
	void gateRejectsMissingUnexpectedInvalidAndDuplicateRequests() {
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> buildable =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests().stream()
						.map(request -> copy(request, request.presetName(), request.spinState(), request.sourceMapId(),
								request.sourceTermCount(), true, true))
						.toList();
		Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary readyApiSurface =
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary();

		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary missing =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(
						readyApiSurface, true, true, buildable.subList(0, buildable.size() - 1));
		assertEquals(1, missing.missingRequestCount());
		assertFalse(missing.requestExecutionAllowed());

		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> unexpected = new ArrayList<>(buildable);
		unexpected.remove(0);
		unexpected.add(copy(buildable.get(0), "experimentalQuad", "hover", buildable.get(0).sourceMapId(),
				buildable.get(0).sourceTermCount(), true, true));
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary unexpectedSummary =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(readyApiSurface, true, true, unexpected);
		assertEquals(1, unexpectedSummary.missingRequestCount());
		assertEquals(1, unexpectedSummary.unexpectedRequestCount());
		assertFalse(unexpectedSummary.requestExecutionAllowed());

		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> invalid = new ArrayList<>(buildable);
		invalid.set(0, copy(buildable.get(0), buildable.get(0).presetName(), buildable.get(0).spinState(),
				buildable.get(0).sourceMapId(), buildable.get(0).sourceTermCount() + 1, true, true));
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary invalidSummary =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(readyApiSurface, true, true, invalid);
		assertEquals(1, invalidSummary.invalidRequestCount());
		assertFalse(invalidSummary.requestExecutionAllowed());

		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> duplicate = new ArrayList<>(buildable);
		duplicate.set(1, buildable.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(readyApiSurface, true, true, duplicate));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(null, true, true, buildable));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(true, true, true, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessAudit audit =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_request_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_readiness_summary,all_scenarios,allowed_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_readiness_scenario,current_api_unavailable_requests_blocked,request_execution_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_readiness_scenario,current_api_unavailable_requests_blocked,powered_source_api_surface_ready,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_readiness_scenario,current_api_unavailable_requests_blocked,missing_powered_source_physical_contract_list,source_term_si_units;")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_readiness_scenario,api_available_acceptance_open_requests_buildable,request_execution_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessScenario findScenario(
			List<Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest copy(
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request,
			String presetName,
			String spinState,
			String sourceMapId,
			int sourceTermCount,
			boolean poweredSourceApiAvailable,
			boolean requestBuildAllowed
	) {
		return new Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest(
				presetName,
				spinState,
				sourceMapId,
				request.validationPacketId(),
				request.acceptanceGatePacketId(),
				request.rotorCount(),
				sourceTermCount,
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
				requestBuildAllowed ? "test-buildable" : request.runtimeInfo()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_request_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
