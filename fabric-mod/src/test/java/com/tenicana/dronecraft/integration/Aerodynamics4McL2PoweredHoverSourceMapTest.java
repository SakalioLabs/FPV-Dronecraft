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

class Aerodynamics4McL2PoweredHoverSourceMapTest {
	@Test
	void auditBuildsStablePoweredHoverSourceMaps() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMapAudit audit =
				Aerodynamics4McL2PoweredHoverSourceMap.audit();

		assertEquals("A4MC-L2-Powered-Hover-Source-Map-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Hover source-map"));
		assertEquals(428, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(16, audit.sourceTermSampleCount());
		assertEquals(31, audit.sourceMapMetricCount());
		assertEquals(18, audit.sourceTermMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.sourceMaps().size());

		for (Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap : audit.sourceMaps()) {
			assertEquals("hover", sourceMap.spinState());
			assertEquals(4, sourceMap.rotorCount());
			assertEquals(4, sourceMap.sourceTermCount());
			assertEquals(4, sourceMap.sourceTerms().size());
			assertTrue(sourceMap.spinRatio() > 0.0);
			assertTrue(sourceMap.spinRatio() < 1.0);
			assertTrue(sourceMap.inletSpeedMetersPerSecond() > 0.0);
			assertTrue(sourceMap.totalThrustNewtons() > 0.0);
			assertEquals(1.0, sourceMap.thrustToWeight(), 1.0e-12);
			assertTrue(sourceMap.meanPressureJumpPascals() > 0.0);
			assertTrue(sourceMap.maxPressureJumpPascals() >= sourceMap.meanPressureJumpPascals());
			assertEquals(sourceMap.totalThrustNewtons(), sourceMap.netForceYNewtons(), 1.0e-12);
			assertEquals(0.0, sourceMap.netForceXNewtons(), 1.0e-12);
			assertEquals(0.0, sourceMap.netForceZNewtons(), 1.0e-12);
			assertEquals(sourceMap.totalThrustNewtons(), sourceMap.netForceMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, sourceMap.netMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, sourceMap.centerOfThrustOffsetMeters(), 1.0e-12);
			assertTrue(sourceMap.totalMomentumPowerWatts() > 0.0);
			assertEquals(2.0 * sourceMap.idealInducedVelocityMetersPerSecond(),
					sourceMap.farWakeVelocityMetersPerSecond(), 1.0e-12);
			assertTrue(sourceMap.tipSpeedMetersPerSecond() > 0.0);
			assertTrue(sourceMap.edgewiseAdvanceRatio() > 0.0);
			assertTrue(sourceMap.representativeBladeReynoldsNumber() > 0.0);
			assertTrue(sourceMap.poweredSourceApiRequired());
			assertEquals(false, sourceMap.poweredSourceApiAvailable());
			for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm term : sourceMap.sourceTerms()) {
				assertEquals(term.thrustNewtons() / term.openAreaSquareMeters(), term.pressureJumpPascals(), 1.0e-12);
				assertEquals(term.thrustNewtons(), term.forceBodyNewtons().length(), 1.0e-12);
				assertTrue(term.openFraction() > 0.70);
			}
		}
		assertEquals(4, audit.extrema().sourceMapCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(0, audit.extrema().poweredSourceApiAvailableCount());
		assertEquals(audit.sourceMaps().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap::meanPressureJumpPascals)
						.max().orElseThrow(),
				audit.extrema().maxMeanPressureJumpPascals(), 1.0e-12);
	}

	@Test
	void hoverSourceMapMatchesPoweredHoverExperimentTarget() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				find(Aerodynamics4McL2PoweredHoverSourceMap.audit().sourceMaps(), "racingQuad");
		Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment experiment =
				Aerodynamics4McL2PoweredHoverExperimentPlan.experiment(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);

		assertEquals(experiment.targetForceMagnitudeNewtons(), sourceMap.netForceMagnitudeNewtons(), 1.0e-12);
		assertEquals(experiment.targetForceYNewtons(), sourceMap.netForceYNewtons(), 1.0e-12);
		assertEquals(experiment.meanPressureJumpPascals(), sourceMap.meanPressureJumpPascals(), 1.0e-12);
		assertEquals(experiment.centerOfThrustOffsetMeters(), sourceMap.centerOfThrustOffsetMeters(), 1.0e-12);
	}

	@Test
	void cantedHoverSourceMapKeepsPerRotorForcesInRotorAxisFrame() {
		DroneConfig canted = DroneConfig.racingQuad().withRotorOutwardCantDegrees(12.0);
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"canted",
						canted,
						new Vec3(0.0, 0.0, -18.0),
						72
				);

		assertEquals("hover", sourceMap.spinState());
		assertTrue(sourceMap.netForceYNewtons() < sourceMap.totalThrustNewtons());
		assertTrue(sourceMap.netForceMagnitudeNewtons() < sourceMap.totalThrustNewtons());
		assertTrue(sourceMap.sourceTerms().stream().anyMatch(term -> Math.abs(term.thrustAxisBody().z()) > 0.01));
		assertTrue(sourceMap.edgewiseAdvanceRatio() > 0.0);
	}

	@Test
	void sourceMapRequiresConfigWithRotors() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSourceMap.sourceMap("missing", null, Vec3.ZERO, 24));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMapAudit audit =
				Aerodynamics4McL2PoweredHoverSourceMap.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_source_map_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_source_map_summary,all_presets,source_term_count,16,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_source_term,racingQuad:rotor0,pressure_jump_pa,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_source_map_preset,racingQuad,spin_state,hover,")));
	}

	private static Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap find(
			List<Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap> sourceMaps,
			String presetName
	) {
		return sourceMaps.stream()
				.filter(sourceMap -> presetName.equals(sourceMap.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_source_map_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
