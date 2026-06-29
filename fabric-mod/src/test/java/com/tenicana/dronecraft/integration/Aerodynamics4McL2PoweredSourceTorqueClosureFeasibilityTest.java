package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceTorqueClosureFeasibilityTest {
	@Test
	void auditSeparatesTorqueBandTargetsFromTorqueOnlyImpossibleRows() {
		Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureAudit audit =
				Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.audit();

		assertEquals("A4MC-L2-Powered-Source-Torque-Closure-Feasibility-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("rotor torque-scale energy band"));
		assertEquals(148, audit.packetMetricRowCount());
		assertEquals(9, audit.sourceReferenceCount());
		assertEquals(2, audit.spinStateSampleCount());
		assertEquals(8, audit.presetSampleCount());
		assertEquals(16, audit.feasibilityMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(8, audit.rows().size());

		Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow hoverRacing =
				find(audit.rows(), "hover", "racingQuad");
		assertEquals(4, hoverRacing.rotorBudgetCount());
		assertEquals(0.014, hoverRacing.thrustWeightedYawTorquePerThrustMeter(), 1.0e-12);
		assertTrue(hoverRacing.currentShaftPowerWatts() > 200.0);
		assertTrue(hoverRacing.currentShaftPowerMarginWatts() > 70.0);
		assertTrue(hoverRacing.torqueScaleClosurePossible());
		assertTrue(hoverRacing.lowerTorqueScaleForPowerClosure() < 1.0);
		assertTrue(hoverRacing.upperTorqueScaleForPowerClosure() > 8.0);
		assertTrue(hoverRacing.torqueCoefficientOnlySufficient());
		assertTargetOnlyAndBlocked(hoverRacing);

		Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow hoverCine =
				find(audit.rows(), "hover", "cinewhoop");
		assertEquals(4, hoverCine.rotorBudgetCount());
		assertEquals(0.013, hoverCine.thrustWeightedYawTorquePerThrustMeter(), 1.0e-12);
		assertTrue(hoverCine.currentShaftPowerMarginWatts() < -150.0);
		assertTrue(hoverCine.currentShaftPowerMarginRatio() < -0.7);
		assertTrue(hoverCine.torqueScaleDiscriminant() < 0.0);
		assertFalse(hoverCine.torqueScaleClosurePossible());
		assertEquals(0.0, hoverCine.lowerTorqueScaleForPowerClosure(), 1.0e-12);
		assertEquals(0.0, hoverCine.upperTorqueScaleForPowerClosure(), 1.0e-12);
		assertTrue(hoverCine.minimumMomentumPowerReductionForTorqueOnlyClosureWatts() > 120.0);
		assertTorqueOnlyImpossibleAndBlocked(hoverCine);

		Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow cruiseCine =
				find(audit.rows(), "cruise", "cinewhoop");
		assertTrue(cruiseCine.currentShaftPowerMarginWatts() < hoverCine.currentShaftPowerMarginWatts());
		assertTrue(cruiseCine.torqueScaleDiscriminant() < hoverCine.torqueScaleDiscriminant());
		assertTrue(cruiseCine.minimumMomentumPowerReductionForTorqueOnlyClosureWatts()
				> hoverCine.minimumMomentumPowerReductionForTorqueOnlyClosureWatts());
		assertTorqueOnlyImpossibleAndBlocked(cruiseCine);

		assertEquals(8, audit.extrema().rowCount());
		assertEquals(6, audit.extrema().torqueScaleClosurePossibleCount());
		assertEquals(2, audit.extrema().torqueScaleClosureImpossibleCount());
		assertEquals(6, audit.extrema().torqueCoefficientOnlySufficientCount());
		assertEquals(2, audit.extrema().jointWakeOrSourceCalibrationRequiredCount());
		assertEquals(8, audit.extrema().liveMotorTorqueCurveRequiredCount());
		assertEquals(8, audit.extrema().liveWakePowerCalibrationRequiredCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertTrue(audit.extrema().minTorqueScaleDiscriminant() < -100000.0);
		assertTrue(audit.extrema().minCurrentShaftPowerMarginRatio() < -0.7);
		assertTrue(audit.extrema().maxMomentumPowerReductionForTorqueOnlyClosureWatts() > 220.0);
		assertTrue(audit.extrema().maxFeasibleMarginWatts() > 1000.0);
		assertTrue(audit.extrema().maxCurrentAeroPowerDemandWatts() > 900.0);
		assertTrue(audit.extrema().maxCurrentShaftPowerWatts() > 1100.0);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureAudit audit =
				Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_torque_closure_feasibility_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_torque_closure_feasibility_summary,all_rows,torque_scale_closure_impossible_count,2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_torque_closure_feasibility,hover:cinewhoop,torque_scale_discriminant,-36571.456188261975,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_torque_closure_feasibility,cruise:cinewhoop,minimum_momentum_power_reduction_for_torque_only_closure_w,225.2109565184897,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_torque_closure_feasibility,cruise:heavyLift,torque_coefficient_only_sufficient,true,")));
	}

	private static void assertTargetOnlyAndBlocked(
			Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow row
	) {
		assertFalse(row.jointWakeOrSourceCalibrationRequired());
		assertTrue(row.liveMotorTorqueCurveRequired());
		assertTrue(row.liveWakePowerCalibrationRequired());
		assertTrue(row.liveSwirlProbeRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-live-motor-torque-curve-and-powered-wake-power-telemetry",
				row.nextRequiredAction());
		assertEquals("TORQUE_BAND_TARGET_ONLY", row.status());
		assertEquals("current-torque-coefficient-inside-power-closure-band-live-evidence-missing",
				row.message());
	}

	private static void assertTorqueOnlyImpossibleAndBlocked(
			Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow row
	) {
		assertFalse(row.torqueCoefficientOnlySufficient());
		assertTrue(row.jointWakeOrSourceCalibrationRequired());
		assertTrue(row.liveMotorTorqueCurveRequired());
		assertTrue(row.liveWakePowerCalibrationRequired());
		assertTrue(row.liveSwirlProbeRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("calibrate-source-map-wake-footprint-and-motor-torque-together",
				row.nextRequiredAction());
		assertEquals("TORQUE_ONLY_IMPOSSIBLE", row.status());
		assertEquals("powered-source-wake-demand-exceeds-torque-only-closure-envelope", row.message());
	}

	private static Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow find(
			List<Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow> rows,
			String spinState,
			String presetName
	) {
		return rows.stream()
				.filter(row -> spinState.equals(row.spinState()) && presetName.equals(row.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_torque_closure_feasibility_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
