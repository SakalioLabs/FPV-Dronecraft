package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2PoweredSourceRequestPlanTest {
	@Test
	void auditBuildsStablePoweredSourceRequestPlan() {
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequestAudit audit =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit();

		assertEquals("A4MC-L2-Powered-Source-Request-Plan-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("body-force or porous-source"));
		assertEquals(305, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(8, audit.requestSampleCount());
		assertEquals(36, audit.requestMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(8, audit.requests().size());

		for (Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request : audit.requests()) {
			assertTrue("hover".equals(request.spinState()) || "cruise".equals(request.spinState()));
			assertEquals(4, request.rotorCount());
			assertEquals(4, request.sourceTermCount());
			assertEquals(request.nx() * request.ny() * request.nz(), request.gridCellCount());
			assertTrue(request.gridCellCount() > 0);
			assertTrue(request.cellSizeMeters() > 0.0);
			assertTrue(request.steps() > 0);
			assertEquals(0.0, request.inletVxMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, request.inletVyMetersPerSecond(), 1.0e-12);
			assertTrue(request.inletVzMetersPerSecond() < 0.0);
			assertEquals(Math.abs(request.inletVzMetersPerSecond()), request.inletSpeedMetersPerSecond(), 1.0e-12);
			assertTrue(request.spinRatio() > 0.0);
			assertTrue(request.totalThrustNewtons() > 0.0);
			assertTrue(request.thrustToWeight() > 0.0);
			assertTrue(request.totalOpenAreaSquareMeters() > 0.0);
			assertTrue(request.meanPressureJumpPascals() > 0.0);
			assertTrue(request.maxPressureJumpPascals() >= request.meanPressureJumpPascals());
			assertEquals(request.netForceYNewtons(), request.netForceMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, request.netForceXNewtons(), 1.0e-12);
			assertEquals(0.0, request.netForceZNewtons(), 1.0e-12);
			assertEquals(0.0, request.netMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, request.centerOfThrustOffsetMeters(), 1.0e-12);
			assertTrue(request.baselineForceMomentRequest());
			assertTrue(request.poweredSourceApiRequired());
			assertEquals(false, request.poweredSourceApiAvailable());
			assertEquals(false, request.requestBuildAllowed());
			assertEquals("plan-only-powered-source-api-unavailable", request.runtimeInfo());
			if ("hover".equals(request.spinState())) {
				assertEquals(Aerodynamics4McL2PoweredHoverSourceMap.SOURCE_ID, request.sourceMapId());
				assertEquals(Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID, request.validationPacketId());
				assertEquals(Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID, request.acceptanceGatePacketId());
				assertEquals(1.0, request.thrustToWeight(), 1.0e-12);
			} else {
				assertEquals(Aerodynamics4McL2PoweredCruiseSourceMap.SOURCE_ID, request.sourceMapId());
				assertEquals(Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID, request.validationPacketId());
				assertEquals(Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID, request.acceptanceGatePacketId());
			}
		}
		assertEquals(8, audit.extrema().requestCount());
		assertEquals(4, audit.extrema().hoverRequestCount());
		assertEquals(4, audit.extrema().cruiseRequestCount());
		assertEquals(32, audit.extrema().sourceTermCount());
		assertEquals(0, audit.extrema().poweredSourceApiAvailableCount());
		assertEquals(0, audit.extrema().requestBuildAllowedCount());
	}

	@Test
	void requestPlanMatchesHoverAndCruiseSourceMaps() {
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest hover =
				find(Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests(), "racingQuad", "hover");
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest cruise =
				find(Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests(), "racingQuad", "cruise");
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap hoverMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap cruiseMap =
				Aerodynamics4McL2PoweredCruiseSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);

		assertEquals(hoverMap.netForceMagnitudeNewtons(), hover.netForceMagnitudeNewtons(), 1.0e-12);
		assertEquals(hoverMap.meanPressureJumpPascals(), hover.meanPressureJumpPascals(), 1.0e-12);
		assertEquals(hoverMap.centerOfThrustOffsetMeters(), hover.centerOfThrustOffsetMeters(), 1.0e-12);
		assertEquals(cruiseMap.netForceMagnitudeNewtons(), cruise.netForceMagnitudeNewtons(), 1.0e-12);
		assertEquals(cruiseMap.meanPressureJumpPascals(), cruise.meanPressureJumpPascals(), 1.0e-12);
		assertEquals(cruiseMap.centerOfThrustOffsetMeters(), cruise.centerOfThrustOffsetMeters(), 1.0e-12);
		assertTrue(cruise.netForceMagnitudeNewtons() > hover.netForceMagnitudeNewtons());
		assertTrue(cruise.meanPressureJumpPascals() > hover.meanPressureJumpPascals());
	}

	@Test
	void requestRequiresSupportedSpinStateAndConfig() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRequestPlan.request("missing", null, Vec3.ZERO, 24, "hover"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRequestPlan.request(
						"missing",
						DroneConfig.racingQuad(),
						Vec3.ZERO,
						24,
						"max"
				));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequestAudit audit =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_request_plan_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request_summary,all_requests,source_term_count,32,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request,racingQuad:hover,source_map_id,A4MC-L2-Powered-Hover-Source-Map-Packet,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request,racingQuad:cruise,source_map_id,A4MC-L2-Powered-Cruise-Source-Map-Packet,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_request,racingQuad:hover,request_build_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest find(
			List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests,
			String presetName,
			String spinState
	) {
		return requests.stream()
				.filter(request -> presetName.equals(request.presetName()) && spinState.equals(request.spinState()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_source_request_plan_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
