package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2ActuatorDiskCalibrationTest {
	@Test
	void auditBuildsStableActuatorDiskLoadMatrix() {
		Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskAudit audit =
				Aerodynamics4McL2ActuatorDiskCalibration.audit();

		assertEquals("A4MC-L2-Actuator-Disk-Load-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Momentum-theory actuator-disk"));
		assertEquals(206, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(4, audit.spinStateSampleCount());
		assertEquals(16, audit.loadScenarioCount());
		assertEquals(12, audit.loadMetricCount());
		assertEquals(8, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(1.225, audit.airDensityKgM3(), 1.0e-12);
		assertEquals(1.5e-5, audit.airKinematicViscosityM2S(), 1.0e-15);
		assertEquals(16, audit.loads().size());
		assertIterableEquals(
				List.of("idle", "hover", "cruise", "max"),
				audit.loads().stream().limit(4)
						.map(Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad::spinState)
						.toList()
		);

		for (Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad load : audit.loads()) {
			assertEquals(4, load.rotorCount());
			assertTrue(load.spinRatio() > 0.0 && load.spinRatio() <= 1.0);
			assertTrue(load.openFraction() > 0.70 && load.openFraction() <= 1.0);
			assertTrue(load.openAreaPerRotorSquareMeters() > 0.0);
			assertEquals(load.thrustNewtonsPerRotor() / load.openAreaPerRotorSquareMeters(),
					load.pressureJumpPascals(), 1.0e-12);
			assertEquals(2.0 * load.idealInducedVelocityMetersPerSecond(),
					load.farWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(load.thrustNewtonsPerRotor() * load.idealInducedVelocityMetersPerSecond(),
					load.momentumPowerWattsPerRotor(), 1.0e-12);
			assertEquals(load.totalThrustNewtons() * load.idealInducedVelocityMetersPerSecond(),
					load.totalMomentumPowerWatts(), 1.0e-12);
			assertEquals(0.0, load.axialInletOverInducedVelocity(), 1.0e-12);
			assertTrue(load.edgewiseAdvanceRatio() >= 0.0);
			assertTrue(load.representativeBladeReynoldsNumber() > 0.0);
		}
	}

	@Test
	void hoverLoadsMatchPresetWeightAndExtremaMirrorMatrix() {
		Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskAudit audit =
				Aerodynamics4McL2ActuatorDiskCalibration.audit();
		List<Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad> hoverLoads = audit.loads().stream()
				.filter(load -> "hover".equals(load.spinState()))
				.toList();

		assertEquals(4, hoverLoads.size());
		for (Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad hover : hoverLoads) {
			assertEquals(1.0, hover.thrustToWeight(), 1.0e-12);
		}
		assertEquals(max(audit.loads(), Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad::pressureJumpPascals),
				audit.extrema().maxPressureJumpPascals(), 1.0e-12);
		assertEquals(max(audit.loads(), Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad::idealInducedVelocityMetersPerSecond),
				audit.extrema().maxIdealInducedVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(max(audit.loads(), Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad::totalMomentumPowerWatts),
				audit.extrema().maxTotalMomentumPowerWatts(), 1.0e-12);
		assertEquals(max(audit.loads(), Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad::edgewiseAdvanceRatio),
				audit.extrema().maxEdgewiseAdvanceRatio(), 1.0e-12);
		assertEquals(max(audit.loads(), Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad::thrustToWeight),
				audit.extrema().maxThrustToWeight(), 1.0e-12);
		assertEquals(min(audit.loads(), Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad::openFraction),
				audit.extrema().minOpenFraction(), 1.0e-12);
		assertEquals(16, audit.extrema().scenarioCount());
	}

	@Test
	void cantedRotorLoadSplitsAxialAndEdgewiseInlet() {
		DroneConfig canted = DroneConfig.racingQuad().withRotorOutwardCantDegrees(12.0);
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(canted, new Vec3(0.0, 0.0, -18.0), 72);
		List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures =
				Aerodynamics4McL2RotorDiskAperture.apertures(canted, probe);

		Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad hover =
				Aerodynamics4McL2ActuatorDiskCalibration.load("canted", canted, apertures, "hover");

		assertEquals("canted", hover.presetName());
		assertTrue(hover.axialInletOverInducedVelocity() > 0.0);
		assertTrue(hover.edgewiseAdvanceRatio() > 0.0);
		assertEquals(1.0, hover.thrustToWeight(), 1.0e-12);
	}

	@Test
	void loadMatrixRequiresConfigAndApertures() {
		DroneConfig config = DroneConfig.apDrone();
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, new Vec3(0.0, 0.0, -14.0), 64);
		List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures =
				Aerodynamics4McL2RotorDiskAperture.apertures(config, probe);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2ActuatorDiskCalibration.loads("missing", null, Vec3.ZERO, 24));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2ActuatorDiskCalibration.load("missing", null, apertures, "hover"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2ActuatorDiskCalibration.load("missing", config, List.of(), "hover"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskAudit audit =
				Aerodynamics4McL2ActuatorDiskCalibration.audit();
		List<String> lines = Files.readAllLines(findRepoRoot()
				.resolve("docs/data/a4mc_l2_actuator_disk_load_packet.csv"));

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_actuator_disk_load_summary,all_scenarios,max_pressure_jump_pa,1881.055652424987,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_actuator_disk_load_matrix,apDrone_hover,thrust_to_weight,1,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_actuator_disk_load_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}

	private static double max(
			List<Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad> loads,
			LoadMetric metric
	) {
		return loads.stream().mapToDouble(metric::value).max().orElseThrow();
	}

	private static double min(
			List<Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad> loads,
			LoadMetric metric
	) {
		return loads.stream().mapToDouble(metric::value).min().orElseThrow();
	}

	@FunctionalInterface
	private interface LoadMetric {
		double value(Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad load);
	}
}
