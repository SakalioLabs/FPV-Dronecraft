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

class Aerodynamics4McL2PoweredCruiseWakeFootprintTest {
	@Test
	void auditBuildsStablePoweredCruiseWakeFootprints() {
		Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprintAudit audit =
				Aerodynamics4McL2PoweredCruiseWakeFootprint.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Wake-Footprint-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("skew-wake"));
		assertEquals(199, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(45, audit.footprintMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(1.225, audit.airDensityKilogramsPerCubicMeter(), 1.0e-12);
		assertEquals(3.0, audit.recommendedDownstreamSampleDistanceRotors(), 1.0e-12);
		assertEquals(4, audit.footprints().size());

		for (Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint : audit.footprints()) {
			assertEquals("cruise", footprint.spinState());
			assertEquals(4, footprint.rotorCount());
			assertEquals(4, footprint.sourceTermCount());
			assertTrue(footprint.inletSpeedMetersPerSecond() > 0.0);
			assertTrue(footprint.edgewiseAdvanceRatio() > 0.0);
			assertTrue(footprint.totalThrustNewtons() > 0.0);
			assertTrue(footprint.thrustToWeight() > 1.0);
			assertEquals(footprint.totalOpenAreaSquareMeters(), footprint.diskPlaneWakeAreaSquareMeters(), 1.0e-12);
			assertEquals(0.5 * footprint.diskPlaneWakeAreaSquareMeters(),
					footprint.farWakeContractedAreaSquareMeters(), 1.0e-12);
			assertEquals(0.5, footprint.contractionAreaRatio(), 1.0e-12);
			assertEquals(Math.sqrt(0.5), footprint.contractionRadiusRatio(), 1.0e-12);
			assertTrue(footprint.meanPressureJumpPascals() > 0.0);
			assertTrue(footprint.maxPressureJumpPascals() >= footprint.meanPressureJumpPascals());
			assertEquals(2.0 * footprint.idealInducedVelocityMetersPerSecond(),
					footprint.axialWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(footprint.inletSpeedMetersPerSecond(), footprint.freestreamVelocityMetersPerSecond(),
					1.0e-12);
			assertEquals(Math.hypot(footprint.axialWakeVelocityMetersPerSecond(),
							footprint.freestreamVelocityMetersPerSecond()),
					footprint.resultantWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(Math.toDegrees(Math.atan2(
							footprint.freestreamVelocityMetersPerSecond(),
							footprint.axialWakeVelocityMetersPerSecond())),
					footprint.wakeSkewAngleDegrees(), 1.0e-12);
			assertTrue(footprint.wakeSkewAngleDegrees() > 0.0);
			assertTrue(footprint.wakeSkewAngleDegrees() < 90.0);
			assertEquals(3.0, footprint.recommendedDownstreamSampleDistanceRotors(), 1.0e-12);
			assertEquals(3.0 * footprint.diskEquivalentRadiusMeters(),
					footprint.downstreamSampleDistanceMeters(), 1.0e-12);
			assertEquals(footprint.downstreamSampleDistanceMeters() / footprint.axialWakeVelocityMetersPerSecond(),
					footprint.axialTransitTimeSeconds(), 1.0e-12);
			assertEquals(footprint.freestreamVelocityMetersPerSecond() * footprint.axialTransitTimeSeconds(),
					footprint.freestreamSweepDistanceMeters(), 1.0e-12);
			assertEquals(Math.hypot(footprint.downstreamSampleDistanceMeters(),
							footprint.freestreamSweepDistanceMeters()),
					footprint.skewedWakeCenterlineDistanceMeters(), 1.0e-12);
			assertTrue(footprint.massFlowKilogramsPerSecond() > 0.0);
			assertEquals(footprint.totalThrustNewtons(), footprint.axialMomentumFluxNewtons(), 1.0e-12);
			assertEquals(0.0, footprint.momentumFluxClosureErrorNewtons(), 1.0e-12);
			assertEquals(footprint.totalMomentumPowerWatts(), footprint.axialWakeKineticPowerWatts(), 1.0e-12);
			assertEquals(0.0, footprint.kineticPowerClosureErrorWatts(), 1.0e-12);
			assertTrue(footprint.requestGridCellCount() > 0);
			assertTrue(footprint.requestCellSizeMeters() > 0.0);
			assertEquals(footprint.downstreamSampleDistanceMeters() / footprint.requestCellSizeMeters(),
					footprint.downstreamSampleDistanceCells(), 1.0e-12);
			assertEquals(footprint.freestreamSweepDistanceMeters() / footprint.requestCellSizeMeters(),
					footprint.freestreamSweepDistanceCells(), 1.0e-12);
			assertEquals(4, footprint.recommendedAxialSamplePlaneCount());
			assertEquals(3, footprint.recommendedSweepSampleColumnCount());
			assertEquals(48, footprint.recommendedSkewWakeSampleCount());
			assertFalse(footprint.poweredSourceApiAvailable());
			assertFalse(footprint.skewWakeProbeApiAvailable());
			assertFalse(footprint.runtimeCouplingAllowed());
			assertTrue(footprint.validationBeforeRuntimeRequired());
			assertEquals("target-only-skew-wake-probe-unavailable", footprint.status());
			assertEquals("audit-only-unvalidated-cruise-skew-wake", footprint.runtimeInfo());
		}
		assertEquals(4, audit.extrema().footprintCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(48, audit.extrema().maxRecommendedSkewWakeSampleCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(4, audit.extrema().validationBeforeRuntimeRequiredCount());
		assertEquals(audit.footprints().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint::wakeSkewAngleDegrees)
						.max().orElseThrow(),
				audit.extrema().maxWakeSkewAngleDegrees(), 1.0e-12);
	}

	@Test
	void wakeFootprintMatchesCruiseSourceMapAndRequestPlan() {
		Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint =
				find(Aerodynamics4McL2PoweredCruiseWakeFootprint.audit().footprints(), "racingQuad");
		Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap =
				Aerodynamics4McL2PoweredCruiseSourceMap.sourceMap(
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
						"cruise"
				);

		assertEquals(sourceMap.totalThrustNewtons(), footprint.totalThrustNewtons(), 1.0e-12);
		assertEquals(sourceMap.totalOpenAreaSquareMeters(), footprint.totalOpenAreaSquareMeters(), 1.0e-12);
		assertEquals(sourceMap.edgewiseAdvanceRatio(), footprint.edgewiseAdvanceRatio(), 1.0e-12);
		assertEquals(sourceMap.idealInducedVelocityMetersPerSecond(),
				footprint.idealInducedVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(sourceMap.farWakeVelocityMetersPerSecond(), footprint.axialWakeVelocityMetersPerSecond(),
				1.0e-12);
		assertEquals(sourceMap.totalMomentumPowerWatts(), footprint.totalMomentumPowerWatts(), 1.0e-12);
		assertEquals(request.gridCellCount(), footprint.requestGridCellCount());
		assertEquals(request.cellSizeMeters(), footprint.requestCellSizeMeters(), 1.0e-12);
	}

	@Test
	void footprintRequiresCruiseSourceAndRequestRows() {
		Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap =
				Aerodynamics4McL2PoweredCruiseSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest hoverRequest =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72,
						"hover"
				);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseWakeFootprint.footprint("missing", null, Vec3.ZERO, 24));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseWakeFootprint.footprint(sourceMap, hoverRequest));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprintAudit audit =
				Aerodynamics4McL2PoweredCruiseWakeFootprint.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_cruise_wake_footprint_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_wake_footprint_summary,all_presets,footprint_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_wake_footprint,racingQuad,spin_state,cruise,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_wake_footprint,racingQuad,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint find(
			List<Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint> footprints,
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
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_cruise_wake_footprint_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
