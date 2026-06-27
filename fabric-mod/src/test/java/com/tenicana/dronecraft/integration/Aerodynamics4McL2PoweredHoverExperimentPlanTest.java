package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2PoweredHoverExperimentPlanTest {
	@Test
	void auditBuildsStablePoweredHoverExperimentPlan() {
		Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperimentAudit audit =
				Aerodynamics4McL2PoweredHoverExperimentPlan.audit();

		assertEquals("A4MC-L2-Powered-Hover-Experiment-Plan", audit.sourceId());
		assertTrue(audit.caveat().contains("static-airframe L2"));
		assertEquals(102, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(22, audit.experimentMetricCount());
		assertEquals(8, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.experiments().size());

		for (Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment experiment : audit.experiments()) {
			assertEquals(4, experiment.rotorCount());
			assertEquals(4, experiment.sourceTermCount());
			assertEquals(experiment.nx() * experiment.ny() * experiment.nz(), experiment.gridCellCount());
			assertTrue(experiment.cellSizeMeters() > 0.0);
			assertTrue(experiment.steps() > 0);
			assertTrue(experiment.inletSpeedMetersPerSecond() > 0.0);
			assertTrue(experiment.baselineForceMomentRequest());
			assertTrue(experiment.poweredSourceApiRequired());
			assertTrue(experiment.meanPressureJumpPascals() > 0.0);
			assertTrue(experiment.maxPressureJumpPascals() >= experiment.meanPressureJumpPascals());
			assertEquals(false, experiment.baselineFlowAtlasOutput());
			assertEquals(false, experiment.poweredSourceApiAvailable());
			assertEquals(0.0, experiment.targetForceXNewtons(), 1.0e-12);
			assertEquals(0.0, experiment.targetForceZNewtons(), 1.0e-12);
			assertEquals(experiment.targetForceYNewtons(), experiment.targetForceMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, experiment.targetMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, experiment.centerOfThrustOffsetMeters(), 1.0e-12);
		}
		assertEquals(audit.experiments().stream()
						.mapToInt(Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment::gridCellCount)
						.max().orElseThrow(),
				audit.extrema().maxGridCellCount());
		assertEquals(audit.experiments().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment::meanPressureJumpPascals)
						.max().orElseThrow(),
				audit.extrema().maxMeanPressureJumpPascals(), 1.0e-12);
		assertEquals(audit.experiments().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment::targetForceMagnitudeNewtons)
						.max().orElseThrow(),
				audit.extrema().maxTargetForceMagnitudeNewtons(), 1.0e-12);
		assertEquals(4, audit.extrema().experimentCount());
		assertEquals(16, audit.extrema().sourceTermCount());
	}

	@Test
	void experimentRequiresConfigWithRotors() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverExperimentPlan.experiment("missing", null, Vec3.ZERO, 24));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperimentAudit audit =
				Aerodynamics4McL2PoweredHoverExperimentPlan.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_experiment_plan.csv");

		assertEquals(audit.packetMetricRowCount() + 1, Files.readAllLines(packet).size());
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_experiment_summary,all_presets,source_term_count,16,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_experiment_plan.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
