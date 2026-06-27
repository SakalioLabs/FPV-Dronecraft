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

import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2StaticAirframeRunMatrixTest {
	@Test
	void auditRunsStaticAirframeMatrixThroughReflectedTestApi() {
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Static-Airframe-Run-Matrix", audit.sourceId());
		assertTrue(audit.caveat().contains("force/moment summaries"));
		assertEquals(127, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(28, audit.runMetricCount());
		assertEquals(9, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.runs().size());

		for (Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary run : audit.runs()) {
			assertTrue(run.nx() >= 16);
			assertTrue(run.ny() >= 12);
			assertTrue(run.nz() >= 16);
			assertEquals(run.nx() * run.ny() * run.nz(), run.gridCellCount());
			assertTrue(run.solidFraction() > 0.0);
			assertTrue(run.requestForceMoment());
			assertFalse(run.requestFlowAtlas());
			assertTrue(run.invoked(), run.message());
			assertTrue(run.succeeded(), run.status());
			assertTrue(run.available(), run.status());
			assertTrue(run.hasForceMoment());
			assertFalse(run.hasFlowAtlas());
			assertEquals(0, run.atlasValueCount());
			assertEquals(1.25, run.forceXNewtons(), 1.0e-6);
			assertEquals(-0.50, run.forceYNewtons(), 1.0e-6);
			assertEquals(3.00, run.forceZNewtons(), 1.0e-6);
			assertEquals(0.20, run.momentXNewtonMeters(), 1.0e-6);
			assertEquals(-0.10, run.momentYNewtonMeters(), 1.0e-6);
			assertEquals(0.40, run.momentZNewtonMeters(), 1.0e-6);
			assertEquals("OK", run.status());
			assertEquals("none", run.message());
			assertEquals("test-runtime", run.runtimeInfo());
			assertTrue(run.pressureCenterOffsetMeters() > 0.0);
		}
		assertEquals(4, audit.extrema().runSummaryCount());
		assertEquals(4, audit.extrema().invokedCount());
		assertEquals(4, audit.extrema().succeededCount());
		assertEquals(4, audit.extrema().availableCount());
		assertEquals(4, audit.extrema().forceMomentCount());
		assertEquals(audit.runs().stream()
						.mapToInt(Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary::gridCellCount)
						.max().orElseThrow(),
				audit.extrema().maxGridCellCount());
	}

	@Test
	void publicSummaryRespectsMissingA4mcRuntime() {
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary run =
				Aerodynamics4McL2StaticAirframeRunMatrix.summary(
						"custom",
						com.tenicana.dronecraft.sim.DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -12.0),
						24
				);

		assertFalse(run.invoked());
		assertFalse(run.available());
		assertFalse(run.hasForceMoment());
		assertTrue(run.message().contains("aerodynamics4mc mod is not loaded"));
	}

	@Test
	void runMatrixRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRunMatrix.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRunMatrix.summary("missing", null, Vec3.ZERO, 24));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_static_airframe_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_run_matrix_summary,all_presets,force_moment_count,4,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_static_airframe_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
