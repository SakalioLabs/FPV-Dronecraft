package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceClosureCalibrationTargetTest {
	@Test
	void auditConvertsClosureResidualsIntoFiniteCalibrationTargets() {
		Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetAudit audit =
				Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.audit();

		assertEquals("A4MC-L2-Powered-Source-Closure-Calibration-Target-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("finite torque, axial-wake, and swirl-wake targets"));
		assertEquals(29, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(8, audit.presetStateRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(8, audit.rows().size());

		Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow
				currentRacing = find(audit.rows(), "hover", "racingQuad");
		assertEquals(1.0, currentRacing.targetTorqueScale(), 1.0e-12);
		assertEquals(1.0, currentRacing.targetAxialWakePowerScale(), 1.0e-12);
		assertTrue(currentRacing.currentEnergyMarginWatts() > 70.0);
		assertCurrentTargetAndBlocked(currentRacing);

		Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow
				hoverCine = find(audit.rows(), "hover", "cinewhoop");
		assertEquals(0.3839807912431793, hoverCine.targetTorqueScale(), 1.0e-12);
		assertTrue(hoverCine.torqueScaleShiftFromCurrent() < -0.61);
		assertEquals(0.07512788262201889, hoverCine.targetAxialWakePowerScale(), 1.0e-12);
		assertEquals(0.9248721173779811,
				hoverCine.requiredAxialWakePowerReductionFraction(), 1.0e-12);
		assertEquals(1.0, hoverCine.targetSwirlWakePowerScale(), 1.0e-12);
		assertTrue(Math.abs(hoverCine.targetResidualMarginWatts()) < 1.0e-9);
		assertFiniteCalibrationTargetAndBlocked(hoverCine);

		Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow
				cruiseCine = find(audit.rows(), "cruise", "cinewhoop");
		assertEquals(hoverCine.targetAxialWakePowerScale(),
				cruiseCine.targetAxialWakePowerScale(), 1.0e-12);
		assertEquals(0.0, cruiseCine.targetResidualMarginWatts(), 1.0e-12);
		assertTrue(cruiseCine.currentEnergyMarginWatts() < -270.0);
		assertFiniteCalibrationTargetAndBlocked(cruiseCine);

		assertEquals(8, audit.extrema().rowCount());
		assertEquals(6, audit.extrema().currentWakeTargetInsideClosureCount());
		assertEquals(2, audit.extrema().finiteCalibrationTargetRequiredCount());
		assertEquals(6, audit.extrema().torqueOnlyClosurePossibleAtCurrentWakeCount());
		assertEquals(2, audit.extrema().torqueScaleDecreaseRequiredCount());
		assertEquals(2, audit.extrema().axialWakePowerReductionRequiredCount());
		assertEquals(0, audit.extrema().swirlWakePowerReductionRequiredCount());
		assertEquals(2, audit.extrema().currentTorqueIncreaseWorsensDeficitCount());
		assertEquals(2, audit.extrema().peakTorqueStationaryDeficitCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertTrue(audit.extrema().minTargetTorqueScale() < 0.39);
		assertEquals(0.9248721173779811,
				audit.extrema().maxRequiredAxialWakePowerReductionFraction(), 1.0e-12);
		assertTrue(audit.extrema().minCurrentEnergyMarginWatts() < -270.0);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetAudit audit =
				Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_closure_calibration_target_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_calibration_target,hover:cinewhoop,0.3839807912431793,-0.6160192087568207,-155.75495423689642,-128.8222555703899,1.7763568394002505E-15,0.07512788262201889,0.9248721173779811,1.0,false,false,true,true,true,false,FINITE_WAKE_AND_TORQUE_CALIBRATION_TARGET_REQUIRED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_calibration_target,cruise:cinewhoop,0.38398079124317924,-0.6160192087568208,-272.2955134644276,-225.2109565184897,0.0,0.07512788262201885,0.9248721173779811,1.0,false,false,true,true,true,false,FINITE_WAKE_AND_TORQUE_CALIBRATION_TARGET_REQUIRED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_calibration_target_summary,all_rows,finite_calibration_target_required_count,,,,,,,,,,,,2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_calibration_target_method,finite_target_rule,target_q_and_axial_scale,,,,,,,,,,,,method,")));
	}

	private static void assertCurrentTargetAndBlocked(
			Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow row
	) {
		assertTrue(row.torqueOnlyClosurePossibleAtCurrentWake());
		assertTrue(row.currentWakeTargetInsideClosure());
		assertFalse(row.finiteCalibrationTargetRequired());
		assertFalse(row.torqueScaleDecreaseRequired());
		assertFalse(row.axialWakePowerReductionRequired());
		assertFalse(row.swirlWakePowerReductionRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-live-motor-torque-curve-and-powered-wake-power-telemetry",
				row.nextRequiredAction());
		assertEquals("CURRENT_WAKE_TARGET_INSIDE_CLOSURE", row.status());
	}

	private static void assertFiniteCalibrationTargetAndBlocked(
			Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow row
	) {
		assertFalse(row.torqueOnlyClosurePossibleAtCurrentWake());
		assertFalse(row.currentWakeTargetInsideClosure());
		assertTrue(row.finiteCalibrationTargetRequired());
		assertTrue(row.torqueScaleDecreaseRequired());
		assertTrue(row.axialWakePowerReductionRequired());
		assertFalse(row.swirlWakePowerReductionRequired());
		assertTrue(row.currentTorqueIncreaseWorsensDeficit());
		assertTrue(row.peakTorqueStationaryDeficit());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-live-source-map-at-peak-torque-and-reduced-axial-wake",
				row.nextRequiredAction());
		assertEquals("FINITE_WAKE_AND_TORQUE_CALIBRATION_TARGET_REQUIRED", row.status());
	}

	private static Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow
	find(
			List<Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow>
					rows,
			String spinState,
			String presetName
	) {
		return rows.stream()
				.filter(row -> spinState.equals(row.spinState())
						&& presetName.equals(row.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_closure_calibration_target_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
