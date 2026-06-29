package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceEnergyClosureManifoldTest {
	@Test
	void auditSamplesCurrentAndPeakTorqueEnergyClosureManifold() {
		Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureAudit audit =
				Aerodynamics4McL2PoweredSourceEnergyClosureManifold.audit();

		assertEquals("A4MC-L2-Powered-Source-Energy-Closure-Manifold-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("joint motor torque, axial wake, and swirl wake closure bounds"));
		assertEquals(34, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(8, audit.presetStateSampleCount());
		assertEquals(2, audit.torqueSampleCountPerPreset());
		assertEquals(16, audit.manifoldSampleRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(16, audit.samples().size());

		Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample currentRacing =
				find(audit.samples(), "hover", "racingQuad", "current_torque");
		assertEquals(1.0, currentRacing.torqueScale(), 1.0e-12);
		assertTrue(currentRacing.maxAxialPowerScale() > 1.7);
		assertTrue(currentRacing.energyMarginWithCurrentAxialWatts() > 70.0);
		assertInsideAndBlocked(currentRacing);

		Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample currentCine =
				find(audit.samples(), "hover", "cinewhoop", "current_torque");
		assertEquals(0.0, currentCine.maxAxialPowerWatts(), 1.0e-12);
		assertEquals(1.0, currentCine.requiredAxialReductionFraction(), 1.0e-12);
		assertTrue(currentCine.energyMarginWithCurrentAxialWatts() < -150.0);
		assertZeroAxialBudgetAndBlocked(currentCine);

		Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample peakCine =
				find(audit.samples(), "cruise", "cinewhoop", "peak_torque");
		assertTrue(peakCine.torqueScale() < 0.4);
		assertEquals(0.07512788262201885, peakCine.maxAxialPowerScale(), 1.0e-12);
		assertTrue(peakCine.requiredAxialReductionFraction() > 0.92);
		assertTrue(peakCine.energyMarginWithCurrentAxialWatts() < -220.0);
		assertAxialReductionAndBlocked(peakCine);

		Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample peakRacing =
				find(audit.samples(), "cruise", "racingQuad", "peak_torque");
		assertTrue(peakRacing.maxAxialPowerScale() > 4.3);
		assertTrue(peakRacing.energyMarginWithCurrentAxialWatts() > 1000.0);
		assertInsideAndBlocked(peakRacing);

		assertEquals(16, audit.extrema().sampleRowCount());
		assertEquals(12, audit.extrema().currentWakeInsideManifoldCount());
		assertEquals(2, audit.extrema().axialReductionRequiredCount());
		assertEquals(2, audit.extrema().zeroAxialBudgetCount());
		assertEquals(4, audit.extrema().jointWakeOrSourceCalibrationRequiredCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(0.0, audit.extrema().minMaxAxialPowerScale(), 1.0e-12);
		assertEquals(1.0, audit.extrema().maxRequiredAxialReductionFraction(), 1.0e-12);
		assertTrue(audit.extrema().minEnergyMarginWithCurrentAxialWatts() < -270.0);
		assertTrue(audit.extrema().maxEnergyMarginWithCurrentAxialWatts() > 1000.0);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureAudit audit =
				Aerodynamics4McL2PoweredSourceEnergyClosureManifold.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_energy_closure_manifold_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_manifold,hover:cinewhoop,current_torque,1.0,1.0,54.5043114712853,70.9727058153374,0.0,0.0,1.0,-155.75495423689642,false,ZERO_AXIAL_BUDGET_AT_SAMPLED_TORQUE,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_manifold,cruise:cinewhoop,peak_torque,0.38398079124317924,1.0,36.58802549801357,18.29401274900679,18.294012749006782,0.07512788262201885,0.9248721173779811,-225.2109565184897,false,AXIAL_SOURCE_REDUCTION_REQUIRED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_manifold_summary,all_samples,current_wake_inside_manifold_count,,,,,,,,,,12,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_manifold_method,energy_closure_equation,qS_minus_A_M_minus_B_q2_W,,,,,,,,,,method,")));
	}

	private static void assertInsideAndBlocked(
			Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample sample
	) {
		assertTrue(sample.currentAxialRetained());
		assertFalse(sample.zeroAxialBudgetAtThisTorque());
		assertTrue(sample.liveMotorTorqueCurveRequired());
		assertTrue(sample.liveWakePowerCalibrationRequired());
		assertTrue(sample.liveSwirlProbeRequired());
		assertFalse(sample.runtimeCouplingAllowed());
		assertFalse(sample.gameplayAutoApplyAllowed());
		assertEquals("capture-live-motor-torque-curve-and-powered-wake-power-telemetry",
				sample.nextRequiredAction());
		assertEquals("CURRENT_WAKE_INSIDE_MANIFOLD", sample.status());
	}

	private static void assertZeroAxialBudgetAndBlocked(
			Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample sample
	) {
		assertFalse(sample.currentAxialRetained());
		assertTrue(sample.zeroAxialBudgetAtThisTorque());
		assertTrue(sample.jointWakeOrSourceCalibrationRequired());
		assertFalse(sample.runtimeCouplingAllowed());
		assertFalse(sample.gameplayAutoApplyAllowed());
		assertEquals("reduce-swirl-power-or-increase-motor-torque-before-axial-source-fit",
				sample.nextRequiredAction());
		assertEquals("ZERO_AXIAL_BUDGET_AT_SAMPLED_TORQUE", sample.status());
	}

	private static void assertAxialReductionAndBlocked(
			Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample sample
	) {
		assertFalse(sample.currentAxialRetained());
		assertFalse(sample.zeroAxialBudgetAtThisTorque());
		assertTrue(sample.jointWakeOrSourceCalibrationRequired());
		assertFalse(sample.runtimeCouplingAllowed());
		assertFalse(sample.gameplayAutoApplyAllowed());
		assertEquals("calibrate-axial-source-map-wake-footprint-and-motor-torque-together",
				sample.nextRequiredAction());
		assertEquals("AXIAL_SOURCE_REDUCTION_REQUIRED", sample.status());
	}

	private static Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample find(
			List<Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample> samples,
			String spinState,
			String presetName,
			String sampleName
	) {
		return samples.stream()
				.filter(sample -> spinState.equals(sample.spinState())
						&& presetName.equals(sample.presetName())
						&& sampleName.equals(sample.sampleName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_energy_closure_manifold_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
