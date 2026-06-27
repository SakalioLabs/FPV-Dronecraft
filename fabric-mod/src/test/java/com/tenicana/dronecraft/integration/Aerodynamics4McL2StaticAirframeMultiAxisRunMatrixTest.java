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

class Aerodynamics4McL2StaticAirframeMultiAxisRunMatrixTest {
	@Test
	void auditRunsMultiAxisMatrixThroughReflectedTestApi() {
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Static-Airframe-Multi-Axis-Run-Matrix-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Multi-axis run matrix"));
		assertEquals(905, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(24, audit.sweepCaseSampleCount());
		assertEquals(37, audit.runMetricCount());
		assertEquals(11, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(24, audit.runs().size());

		for (Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary run : audit.runs()) {
			assertTrue(run.requestForceMoment());
			assertFalse(run.requestFlowAtlas());
			assertTrue(run.invoked());
			assertTrue(run.succeeded());
			assertTrue(run.available());
			assertTrue(run.hasForceMoment());
			assertFalse(run.hasFlowAtlas());
			assertEquals(0, run.atlasValueCount());
			assertTrue(run.coefficientFitReady());
			assertEquals("OK", run.status());
			assertEquals("none", run.message());
			assertEquals("test-runtime", run.runtimeInfo());
			assertEquals(1.25, run.forceXNewtons(), 1.0e-6);
			assertEquals(-0.50, run.forceYNewtons(), 1.0e-6);
			assertEquals(3.00, run.forceZNewtons(), 1.0e-6);
			assertEquals(0.20, run.momentXNewtonMeters(), 1.0e-6);
			assertEquals(-0.10, run.momentYNewtonMeters(), 1.0e-6);
			assertEquals(0.40, run.momentZNewtonMeters(), 1.0e-6);
			assertEquals(0.10, run.pressureCenterOffsetXBodyMeters(), 1.0e-6);
			assertEquals(-0.20, run.pressureCenterOffsetYBodyMeters(), 1.0e-6);
			assertEquals(0.30, run.pressureCenterOffsetZBodyMeters(), 1.0e-6);
		}
		assertEquals(24, audit.extrema().runSummaryCount());
		assertEquals(24, audit.extrema().invokedCount());
		assertEquals(24, audit.extrema().succeededCount());
		assertEquals(24, audit.extrema().availableCount());
		assertEquals(24, audit.extrema().forceMomentCount());
		assertEquals(24, audit.extrema().rawFlowAtlasDisabledCount());
		assertEquals(24, audit.extrema().testRuntimeCount());
	}

	@Test
	void runMatrixPreservesSweepPlanGeometryAndFitRoles() {
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(getClass().getClassLoader());
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary rightSideslip =
				find(audit.runs(), "racingQuad", "right_sideslip_12deg");
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary forward =
				find(audit.runs(), "racingQuad", "forward_drag");

		assertEquals("sideforce_yaw_positive", rightSideslip.fitRole());
		assertEquals("drag_z", forward.fitRole());
		assertEquals(18.0, rightSideslip.inletSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(12.0, rightSideslip.sweepAngleDegrees(), 1.0e-12);
		assertTrue(rightSideslip.projectedReferenceAreaSquareMeters() > forward.projectedReferenceAreaSquareMeters());
		assertEquals(forward.gridCellCount(), rightSideslip.gridCellCount());
		assertEquals(forward.referenceLengthMeters(), rightSideslip.referenceLengthMeters(), 1.0e-12);
	}

	@Test
	void unavailableProductionRunMatrixDoesNotBecomeFitReady() {
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit();

		assertEquals(24, audit.runs().size());
		for (Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary run : audit.runs()) {
			assertFalse(run.available());
			assertFalse(run.hasForceMoment());
			assertFalse(run.coefficientFitReady());
		}
		assertEquals(0, audit.extrema().availableCount());
		assertEquals(0, audit.extrema().forceMomentCount());
	}

	@Test
	void auditRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(null, spec ->
						Aerodynamics4McL2Bridge.run(getClass().getClassLoader(), spec)));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(
						Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.audit(),
						null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.summary(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_static_airframe_multi_axis_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_run_matrix_summary,all_sweeps,force_moment_count,24,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_run_matrix_case,racingQuad:right_sideslip_12deg,fit_role,sideforce_yaw_positive,")));
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary find(
			List<Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary> runs,
			String presetName,
			String sweepName
	) {
		return runs.stream()
				.filter(run -> presetName.equals(run.presetName()) && sweepName.equals(run.sweepName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_static_airframe_multi_axis_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
