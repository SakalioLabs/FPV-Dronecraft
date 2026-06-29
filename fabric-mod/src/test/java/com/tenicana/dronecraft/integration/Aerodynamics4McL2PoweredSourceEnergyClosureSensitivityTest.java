package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceEnergyClosureSensitivityTest {
	@Test
	void auditClassifiesLocalClosureResidualSensitivity() {
		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityAudit audit =
				Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.audit();

		assertEquals("A4MC-L2-Powered-Source-Energy-Closure-Sensitivity-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("local torque, axial-wake, and swirl-wake residual directions"));
		assertEquals(36, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(16, audit.manifoldSampleRowCount());
		assertEquals(12, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(16, audit.rows().size());

		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow
				currentRacing = find(audit.rows(), "hover", "racingQuad", "current_torque");
		assertTrue(currentRacing.energyMarginWithCurrentAxialWatts() > 70.0);
		assertTrue(currentRacing.torqueGradientWattsPerTorqueScale() > 150.0);
		assertPositiveResidualAndBlocked(currentRacing);

		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow
				currentCine = find(audit.rows(), "hover", "cinewhoop", "current_torque");
		assertTrue(currentCine.energyMarginWithCurrentAxialWatts() < -150.0);
		assertTrue(currentCine.torqueGradientWattsPerTorqueScale() < -80.0);
		assertEquals(1.0, currentCine.requiredAxialScaleReductionForLinearClosure(), 1.0e-12);
		assertEquals(1.0, currentCine.requiredSwirlScaleReductionForLinearClosure(), 1.0e-12);
		assertTorqueWorsensAndBlocked(currentCine);

		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow
				peakCine = find(audit.rows(), "cruise", "cinewhoop", "peak_torque");
		assertEquals(0.0, peakCine.torqueGradientWattsPerTorqueScale(), 1.0e-12);
		assertEquals(0.9248721173779811,
				peakCine.requiredAxialScaleReductionForLinearClosure(), 1.0e-12);
		assertEquals(1.0, peakCine.requiredSwirlScaleReductionForLinearClosure(), 1.0e-12);
		assertStationaryDeficitAndBlocked(peakCine);

		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow
				currentHeavy = find(audit.rows(), "cruise", "heavyLift", "current_torque");
		assertTrue(currentHeavy.torqueGradientWattsPerTorqueScale() > 780.0);
		assertPositiveResidualAndBlocked(currentHeavy);

		assertEquals(16, audit.extrema().rowCount());
		assertEquals(12, audit.extrema().positiveMarginCount());
		assertEquals(4, audit.extrema().negativeMarginCount());
		assertEquals(6, audit.extrema().torqueIncreaseImprovesCount());
		assertEquals(2, audit.extrema().torqueIncreaseWorsensCount());
		assertEquals(8, audit.extrema().torqueStationaryCount());
		assertEquals(0, audit.extrema().torqueOnlyLinearClosureAvailableCount());
		assertEquals(2, audit.extrema().axialOnlyLinearClosureAvailableCount());
		assertEquals(0, audit.extrema().swirlOnlyLinearClosureAvailableCount());
		assertEquals(4, audit.extrema().jointWakeOrSourceCalibrationRequiredCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertTrue(audit.extrema().minTorqueGradientWattsPerTorqueScale() < -150.0);
		assertTrue(audit.extrema().maxTorqueGradientWattsPerTorqueScale() > 780.0);
		assertEquals(1.0, audit.extrema().maxRequiredAxialScaleReductionForLinearClosure(), 1.0e-12);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityAudit audit =
				Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_energy_closure_sensitivity_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_sensitivity,hover:cinewhoop,current_torque,1.0,-155.75495423689642,-87.44110015938952,-139.2865598928443,-70.9727058153374,false,true,false,1.0,false,false,false,TORQUE_INCREASE_WORSENS_DEFICIT,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_sensitivity,cruise:cinewhoop,peak_torque,0.38398079124317924,-225.2109565184897,0.0,-243.50496926749648,-18.29401274900679,false,false,true,0.9248721173779811,false,true,false,TORQUE_GRADIENT_STATIONARY_DEFICIT,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_sensitivity_summary,all_samples,torque_increase_worsens_count,,,,,,,,,,,,2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_energy_closure_sensitivity_method,local_derivatives,dE_dq_equals_S_minus_2qW,,,,,,,,,,,,method,")));
	}

	private static void assertPositiveResidualAndBlocked(
			Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow row
	) {
		assertTrue(row.energyMarginWithCurrentAxialWatts() >= 0.0);
		assertFalse(row.torqueOnlyLinearClosureAvailable());
		assertFalse(row.axialOnlyLinearClosureAvailable());
		assertFalse(row.swirlOnlyLinearClosureAvailable());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-live-powered-source-residual-sensitivity-telemetry",
				row.nextRequiredAction());
		assertEquals("POSITIVE_RESIDUAL_MARGIN", row.status());
	}

	private static void assertTorqueWorsensAndBlocked(
			Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow row
	) {
		assertTrue(row.torqueIncreaseWorsensMargin());
		assertFalse(row.torqueIncreaseImprovesMargin());
		assertFalse(row.torqueStationaryAtSample());
		assertFalse(row.torqueOnlyLinearClosureAvailable());
		assertFalse(row.axialOnlyLinearClosureAvailable());
		assertFalse(row.swirlOnlyLinearClosureAvailable());
		assertTrue(row.jointWakeOrSourceCalibrationRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("reduce-torque-scale-toward-peak-before-joint-wake-calibration",
				row.nextRequiredAction());
		assertEquals("TORQUE_INCREASE_WORSENS_DEFICIT", row.status());
	}

	private static void assertStationaryDeficitAndBlocked(
			Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow row
	) {
		assertFalse(row.torqueIncreaseWorsensMargin());
		assertFalse(row.torqueIncreaseImprovesMargin());
		assertTrue(row.torqueStationaryAtSample());
		assertFalse(row.torqueOnlyLinearClosureAvailable());
		assertTrue(row.axialOnlyLinearClosureAvailable());
		assertFalse(row.swirlOnlyLinearClosureAvailable());
		assertTrue(row.jointWakeOrSourceCalibrationRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("calibrate-axial-wake-or-swirl-wake-because-torque-gradient-is-zero",
				row.nextRequiredAction());
		assertEquals("TORQUE_GRADIENT_STATIONARY_DEFICIT", row.status());
	}

	private static Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow
	find(
			List<Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow>
					rows,
			String spinState,
			String presetName,
			String sampleName
	) {
		return rows.stream()
				.filter(row -> spinState.equals(row.spinState())
						&& presetName.equals(row.presetName())
						&& sampleName.equals(row.sampleName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_energy_closure_sensitivity_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
