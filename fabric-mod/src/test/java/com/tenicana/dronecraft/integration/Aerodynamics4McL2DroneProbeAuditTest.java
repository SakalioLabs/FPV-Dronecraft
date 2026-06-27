package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2DroneProbeAuditTest {
	@Test
	void auditBuildsStableGeometryPacketForRepresentativeDronePresets() {
		Aerodynamics4McL2DroneProbeAudit.DroneProbeGeometryAudit audit =
				Aerodynamics4McL2DroneProbeAudit.audit();

		assertEquals("A4MC-L2-Drone-Wind-Tunnel-Geometry-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Geometry-only packet"));
		assertEquals(148, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(34, audit.probeMetricCount());
		assertEquals(6, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertIterableEquals(
				List.of("racingQuad", "apDrone", "cinewhoop", "heavyLift"),
				audit.presets().stream().map(Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary::presetName).toList()
		);

		for (Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary summary : audit.presets()) {
			assertTrue(summary.rotorCount() >= 4);
			assertTrue(summary.massKg() > 0.0);
			assertEquals(summary.nx() * summary.ny() * summary.nz(), summary.gridCellCount());
			assertEquals(summary.gridCellCount(), summary.maskByteLength());
			assertTrue(summary.cellSizeMeters() >= 0.02 && summary.cellSizeMeters() <= 0.08);
			assertTrue(summary.solidCellCount() > 0);
			assertTrue(summary.solidFraction() > 0.0 && summary.solidFraction() < 0.20);
			assertTrue(summary.maxRotorRadiusMeters() > 0.0);
			assertTrue(summary.rotorSpanMeters() > 0.0);
			assertTrue(summary.rotorDiskRadiusCells() > 1.5);
			assertFalse(summary.outputFlowAtlas());
			assertTrue(summary.computeForceMoment());
			assertTrue(summary.solidMaskPresent());
			assertTrue(summary.bodyCenterSolid());
			assertEquals(summary.rotorCount(), summary.rotorHubSolidCount());
			assertTrue(summary.allRotorHubsSolid());
			assertEquals(summary.rotorCount(), summary.openRotorDiskSampleCount());
			assertTrue(summary.allRotorDiskSamplesOpen());
			assertEquals(0.5 * summary.nx() * summary.cellSizeMeters(), summary.referenceX(), 1.0e-12);
			assertEquals(0.5 * summary.ny() * summary.cellSizeMeters(), summary.referenceY(), 1.0e-12);
			assertEquals(0.5 * summary.nz() * summary.cellSizeMeters(), summary.referenceZ(), 1.0e-12);
		}
	}

	@Test
	void extremaSummarizePresetProbeEnvelope() {
		Aerodynamics4McL2DroneProbeAudit.DroneProbeGeometryAudit audit =
				Aerodynamics4McL2DroneProbeAudit.audit();
		Aerodynamics4McL2DroneProbeAudit.DroneProbeGeometryExtrema extrema = audit.extrema();

		int maxGridCells = audit.presets().stream()
				.mapToInt(Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary::gridCellCount)
				.max()
				.orElseThrow();
		double minSolidFraction = audit.presets().stream()
				.mapToDouble(Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary::solidFraction)
				.min()
				.orElseThrow();
		double maxSolidFraction = audit.presets().stream()
				.mapToDouble(Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary::solidFraction)
				.max()
				.orElseThrow();

		assertEquals(maxGridCells, extrema.maxGridCellCount());
		assertEquals(minSolidFraction, extrema.minSolidFraction(), 1.0e-15);
		assertEquals(maxSolidFraction, extrema.maxSolidFraction(), 1.0e-15);
		assertEquals(audit.heavyLift().rotorSpanMeters(), extrema.maxRotorSpanMeters(), 1.0e-12);
		assertTrue(extrema.maxRotorDiskRadiusCells() >= audit.apDrone().rotorDiskRadiusCells());
		assertEquals(audit.racingQuad().inletSpeedMetersPerSecond(), extrema.maxInletSpeedMetersPerSecond(), 1.0e-12);
		assertTrue(audit.heavyLift().gridCellCount() > audit.apDrone().gridCellCount());
		assertTrue(audit.cinewhoop().rotorSpanMeters() < audit.racingQuad().rotorSpanMeters());
	}

	@Test
	void customSummaryRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2DroneProbeAudit.summary("missing", null, null, 24));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2DroneProbeAudit.DroneProbeGeometryAudit audit =
				Aerodynamics4McL2DroneProbeAudit.audit();
		List<String> lines = Files.readAllLines(findRepoRoot()
				.resolve("docs/data/a4mc_l2_drone_probe_geometry_packet.csv"));

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_drone_probe_geometry_summary,all_presets,max_grid_cell_count,14560,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_drone_probe_geometry_preset,heavyLift,rotor_span_m,0.763116882454,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_drone_probe_geometry_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
