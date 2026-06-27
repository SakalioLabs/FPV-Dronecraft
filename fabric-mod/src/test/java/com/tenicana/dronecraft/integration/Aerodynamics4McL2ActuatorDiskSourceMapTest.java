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

class Aerodynamics4McL2ActuatorDiskSourceMapTest {
	@Test
	void auditBuildsStableHoverSourceMaps() {
		Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMapAudit audit =
				Aerodynamics4McL2ActuatorDiskSourceMap.audit();

		assertEquals("A4MC-L2-Actuator-Disk-Source-Map-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("force and moment targets"));
		assertEquals(94, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(20, audit.sourceMapMetricCount());
		assertEquals(8, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.sourceMaps().size());

		for (Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMap map : audit.sourceMaps()) {
			assertEquals("hover", map.spinState());
			assertEquals(4, map.rotorCount());
			assertEquals(4, map.sourceTerms().size());
			assertEquals(1.0, map.thrustToWeight(), 1.0e-12);
			assertEquals(map.totalThrustNewtons(), map.netForceBodyNewtons().y(), 1.0e-12);
			assertEquals(0.0, map.netForceBodyNewtons().x(), 1.0e-12);
			assertEquals(0.0, map.netForceBodyNewtons().z(), 1.0e-12);
			assertEquals(0.0, map.netMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, map.centerOfThrustBodyMeters().length(), 1.0e-12);
			assertTrue(map.meanPressureJumpPascals() > 0.0);
			assertTrue(map.meanOpenFraction() > 0.70);
			for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm term : map.sourceTerms()) {
				assertEquals(term.thrustNewtons() / term.openAreaSquareMeters(), term.pressureJumpPascals(), 1.0e-12);
				assertEquals(term.thrustNewtons(), term.forceBodyNewtons().length(), 1.0e-12);
				assertTrue(term.openFraction() > 0.70);
			}
		}
		assertEquals(4, audit.extrema().sourceMapCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(audit.sourceMaps().stream()
						.mapToDouble(Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMap::meanPressureJumpPascals)
						.max().orElseThrow(),
				audit.extrema().maxMeanPressureJumpPascals(), 1.0e-12);
	}

	@Test
	void cantedSourceMapKeepsForceTargetInRotorAxisFrame() {
		DroneConfig canted = DroneConfig.racingQuad().withRotorOutwardCantDegrees(12.0);
		Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMap map =
				Aerodynamics4McL2ActuatorDiskSourceMap.sourceMap(
						"canted",
						canted,
						new Vec3(0.0, 0.0, -18.0),
						72,
						"hover"
				);

		assertEquals("canted", map.presetName());
		assertEquals(1.0, map.thrustToWeight(), 1.0e-12);
		assertTrue(map.netForceBodyNewtons().y() < map.totalThrustNewtons());
		assertTrue(map.netForceMagnitudeNewtons() < map.totalThrustNewtons());
		assertEquals(0.0, map.netMomentMagnitudeNewtonMeters(), 1.0e-12);
		assertTrue(map.sourceTerms().stream().anyMatch(term -> Math.abs(term.thrustAxisBody().z()) > 0.01));
	}

	@Test
	void sourceMapRequiresConfigAndMatchingApertures() {
		DroneConfig config = DroneConfig.apDrone();
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, new Vec3(0.0, 0.0, -14.0), 64);
		List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures =
				Aerodynamics4McL2RotorDiskAperture.apertures(config, probe);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2ActuatorDiskSourceMap.sourceMap("missing", null, Vec3.ZERO, 24, "hover"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2ActuatorDiskSourceMap.sourceMap("missing", config, List.of(), "hover"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2ActuatorDiskSourceMap.sourceMap("missing", config,
						apertures.subList(0, apertures.size() - 1), "hover"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMapAudit audit =
				Aerodynamics4McL2ActuatorDiskSourceMap.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_actuator_disk_source_map_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_actuator_disk_source_map_summary,all_presets,source_term_count,16,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
