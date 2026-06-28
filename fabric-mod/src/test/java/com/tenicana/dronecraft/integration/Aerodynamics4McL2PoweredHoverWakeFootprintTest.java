package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2PoweredHoverWakeFootprintTest {
	@Test
	void auditBuildsStablePoweredHoverWakeFootprints() {
		Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprintAudit audit =
				Aerodynamics4McL2PoweredHoverWakeFootprint.audit();

		assertEquals("A4MC-L2-Powered-Hover-Wake-Footprint-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("near-field downwash"));
		assertEquals(153, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(34, audit.footprintMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(1.225, audit.airDensityKilogramsPerCubicMeter(), 1.0e-12);
		assertEquals(4, audit.footprints().size());

		for (Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint footprint : audit.footprints()) {
			assertEquals("hover", footprint.spinState());
			assertEquals(4, footprint.rotorCount());
			assertEquals(4, footprint.sourceTermCount());
			assertTrue(footprint.totalThrustNewtons() > 0.0);
			assertEquals(1.0, footprint.thrustToWeight(), 1.0e-12);
			assertEquals(footprint.totalOpenAreaSquareMeters(), footprint.diskPlaneWakeAreaSquareMeters(), 1.0e-12);
			assertEquals(0.5 * footprint.diskPlaneWakeAreaSquareMeters(),
					footprint.farWakeContractedAreaSquareMeters(), 1.0e-12);
			assertEquals(0.5, footprint.contractionAreaRatio(), 1.0e-12);
			assertEquals(Math.sqrt(0.5), footprint.contractionRadiusRatio(), 1.0e-12);
			assertTrue(footprint.meanPressureJumpPascals() > 0.0);
			assertTrue(footprint.maxPressureJumpPascals() >= footprint.meanPressureJumpPascals());
			assertEquals(2.0 * footprint.idealInducedVelocityMetersPerSecond(),
					footprint.farWakeVelocityMetersPerSecond(), 1.0e-12);
			assertTrue(footprint.massFlowKilogramsPerSecond() > 0.0);
			assertEquals(footprint.totalThrustNewtons(), footprint.farWakeMomentumFluxNewtons(), 1.0e-12);
			assertEquals(0.0, footprint.momentumFluxClosureErrorNewtons(), 1.0e-12);
			assertEquals(footprint.totalMomentumPowerWatts(), footprint.farWakeKineticPowerWatts(), 1.0e-12);
			assertEquals(0.0, footprint.kineticPowerClosureErrorWatts(), 1.0e-12);
			assertTrue(footprint.requestGridCellCount() > 0);
			assertTrue(footprint.requestCellSizeMeters() > 0.0);
			assertTrue(footprint.diskEquivalentRadiusCells() > footprint.farWakeEquivalentRadiusCells());
			assertTrue(footprint.farWakeEquivalentRadiusCells() > 0.0);
			assertEquals(3, footprint.recommendedAxialSamplePlaneCount());
			assertEquals(12, footprint.recommendedRotorWakeSampleCount());
			assertFalse(footprint.poweredSourceApiAvailable());
			assertFalse(footprint.localWakeProbeApiAvailable());
			assertFalse(footprint.runtimeCouplingAllowed());
			assertTrue(footprint.validationBeforeRuntimeRequired());
			assertEquals("target-only-local-wake-probe-unavailable", footprint.status());
			assertEquals("audit-only-unvalidated-hover-downwash", footprint.runtimeInfo());
		}
		assertEquals(4, audit.extrema().footprintCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(12, audit.extrema().maxRecommendedRotorWakeSampleCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(audit.footprints().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint::totalThrustNewtons)
						.max().orElseThrow(),
				audit.extrema().maxTotalThrustNewtons(), 1.0e-12);
	}

	@Test
	void wakeFootprintMatchesHoverSourceMapAndRequestPlan() {
		Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint footprint =
				find(Aerodynamics4McL2PoweredHoverWakeFootprint.audit().footprints(), "racingQuad");
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72,
						"hover"
				);

		assertEquals(sourceMap.totalThrustNewtons(), footprint.totalThrustNewtons(), 1.0e-12);
		assertEquals(sourceMap.totalOpenAreaSquareMeters(), footprint.totalOpenAreaSquareMeters(), 1.0e-12);
		assertEquals(sourceMap.idealInducedVelocityMetersPerSecond(),
				footprint.idealInducedVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(sourceMap.farWakeVelocityMetersPerSecond(), footprint.farWakeVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(sourceMap.totalMomentumPowerWatts(), footprint.totalMomentumPowerWatts(), 1.0e-12);
		assertEquals(request.gridCellCount(), footprint.requestGridCellCount());
		assertEquals(request.cellSizeMeters(), footprint.requestCellSizeMeters(), 1.0e-12);
	}

	@Test
	void footprintRequiresHoverSourceAndRequestRows() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest cruiseRequest =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72,
						"cruise"
				);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverWakeFootprint.footprint("missing", null, Vec3.ZERO, 24));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverWakeFootprint.footprint(sourceMap, cruiseRequest));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprintAudit audit =
				Aerodynamics4McL2PoweredHoverWakeFootprint.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_wake_footprint_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_wake_footprint_summary,all_presets,source_term_count,16,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_wake_footprint,racingQuad,contraction_area_ratio,0.5,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_wake_footprint,racingQuad,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint find(
			List<Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint> footprints,
			String presetName
	) {
		return footprints.stream()
				.filter(footprint -> presetName.equals(footprint.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_wake_footprint_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
