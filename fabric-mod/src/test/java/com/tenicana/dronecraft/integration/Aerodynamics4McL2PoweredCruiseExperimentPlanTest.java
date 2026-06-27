package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2PoweredCruiseExperimentPlanTest {
	@Test
	void auditBuildsStablePoweredCruiseExperimentPlan() {
		Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperimentAudit audit =
				Aerodynamics4McL2PoweredCruiseExperimentPlan.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Experiment-Plan", audit.sourceId());
		assertTrue(audit.caveat().contains("forward-flight"));
		assertEquals(173, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(39, audit.experimentMetricCount());
		assertEquals(11, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.experiments().size());

		for (Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperiment experiment : audit.experiments()) {
			assertEquals("cruise", experiment.spinState());
			assertEquals(4, experiment.rotorCount());
			assertEquals(4, experiment.sourceTermCount());
			assertEquals(experiment.nx() * experiment.ny() * experiment.nz(), experiment.gridCellCount());
			assertTrue(experiment.cellSizeMeters() > 0.0);
			assertTrue(experiment.steps() > 0);
			assertEquals(0.0, experiment.inletVxMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, experiment.inletVyMetersPerSecond(), 1.0e-12);
			assertTrue(experiment.inletVzMetersPerSecond() < 0.0);
			assertEquals(Math.abs(experiment.inletVzMetersPerSecond()),
					experiment.inletSpeedMetersPerSecond(), 1.0e-12);
			assertEquals(0.65, experiment.spinRatio(), 1.0e-12);
			assertTrue(experiment.baselineForceMomentRequest());
			assertEquals(false, experiment.baselineFlowAtlasOutput());
			assertTrue(experiment.poweredSourceApiRequired());
			assertEquals(false, experiment.poweredSourceApiAvailable());
			assertTrue(experiment.actuatorDiskRepresentationRequired());
			assertEquals(false, experiment.porousOrBodyForceSourceApiAvailable());
			assertTrue(experiment.meanPressureJumpPascals() > 0.0);
			assertTrue(experiment.maxPressureJumpPascals() >= experiment.meanPressureJumpPascals());
			assertTrue(experiment.thrustToWeight() > 0.0);
			assertEquals(0.0, experiment.targetForceXNewtons(), 1.0e-12);
			assertEquals(0.0, experiment.targetForceZNewtons(), 1.0e-12);
			assertEquals(experiment.targetForceYNewtons(), experiment.targetForceMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, experiment.targetMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertTrue(experiment.totalMomentumPowerWatts() > 0.0);
			assertTrue(experiment.idealInducedVelocityMetersPerSecond() > 0.0);
			assertEquals(2.0 * experiment.idealInducedVelocityMetersPerSecond(),
					experiment.farWakeVelocityMetersPerSecond(), 1.0e-12);
			assertTrue(experiment.tipSpeedMetersPerSecond() > 0.0);
			assertTrue(experiment.edgewiseAdvanceRatio() > 0.0);
			assertEquals(0.0, experiment.axialInletOverInducedVelocity(), 1.0e-12);
			assertTrue(experiment.representativeBladeReynoldsNumber() > 0.0);
		}
		assertEquals(audit.experiments().stream()
						.mapToInt(Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperiment::gridCellCount)
						.max().orElseThrow(),
				audit.extrema().maxGridCellCount());
		assertEquals(audit.experiments().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperiment::edgewiseAdvanceRatio)
						.max().orElseThrow(),
				audit.extrema().maxEdgewiseAdvanceRatio(), 1.0e-12);
		assertEquals(audit.experiments().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperiment::totalMomentumPowerWatts)
						.max().orElseThrow(),
				audit.extrema().maxTotalMomentumPowerWatts(), 1.0e-12);
		assertEquals(4, audit.extrema().experimentCount());
		assertEquals(16, audit.extrema().sourceTermCount());
	}

	@Test
	void cruisePlanKeepsHigherThanHoverThrustTargetForRacingQuad() {
		Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperiment cruise =
				Aerodynamics4McL2PoweredCruiseExperimentPlan.experiment(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment hover =
				Aerodynamics4McL2PoweredHoverExperimentPlan.experiment(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);

		assertTrue(cruise.targetForceMagnitudeNewtons() > hover.targetForceMagnitudeNewtons());
		assertTrue(cruise.meanPressureJumpPascals() > hover.meanPressureJumpPascals());
		assertTrue(cruise.edgewiseAdvanceRatio() > 0.0);
	}

	@Test
	void experimentRequiresConfigWithRotors() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseExperimentPlan.experiment("missing", null, Vec3.ZERO, 24));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperimentAudit audit =
				Aerodynamics4McL2PoweredCruiseExperimentPlan.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_cruise_experiment_plan.csv");

		assertEquals(audit.packetMetricRowCount() + 1, Files.readAllLines(packet).size());
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_experiment_summary,all_presets,source_term_count,16,")));
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_experiment_preset,racingQuad,spin_state,cruise,")));
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_experiment_preset,racingQuad,edgewise_advance_ratio,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_cruise_experiment_plan.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
