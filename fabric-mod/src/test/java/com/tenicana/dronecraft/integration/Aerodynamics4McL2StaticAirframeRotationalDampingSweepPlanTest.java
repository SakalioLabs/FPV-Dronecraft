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

import com.tenicana.dronecraft.sim.DroneConfig;

class Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlanTest {
	@Test
	void auditBuildsStableRotationalDampingSweepPlan() {
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepAudit audit =
				Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit();

		assertEquals("A4MC-L2-Static-Airframe-Rotational-Damping-Sweep-Plan-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("rotational velocity-field"));
		assertEquals(690, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(6, audit.sweepCasesPerPreset());
		assertEquals(28, audit.sweepCaseMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(24, audit.sweepCases().size());

		assertTrue(audit.sweepCases().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::baselineStaticRequestReady));
		assertTrue(audit.sweepCases().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::outputFlowAtlas));
		assertTrue(audit.sweepCases().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::computeForceMoment));
		assertTrue(audit.sweepCases().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::rotationalFlowApiRequired));
		assertTrue(audit.sweepCases().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::rotationalFlowApiAvailable));
		assertTrue(audit.sweepCases().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::movingBoundaryOrVelocityFieldRequired));
		assertTrue(audit.sweepCases().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::expectedOpposingMomentSign));
		assertTrue(audit.sweepCases().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase::angularDragCalibrationAllowed));
		assertEquals(4, audit.extrema().presetCount());
		assertEquals(24, audit.extrema().baselineStaticRequestReadyCount());
		assertEquals(0, audit.extrema().rotationalFlowApiAvailableCount());
		assertEquals(0, audit.extrema().angularDragCalibrationAllowedCount());
		assertEquals(24, audit.extrema().expectedOpposingMomentSignCount());
		assertEquals(24, audit.extrema().movingBoundaryOrVelocityFieldRequiredCount());
	}

	@Test
	void pitchYawAndRollBodyRateCasesUseExpectedAxesAndScales() {
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepAudit audit =
				Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit();
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase positivePitch =
				find(audit.sweepCases(), "racingQuad", "positive_pitch_rate");
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase negativeYaw =
				find(audit.sweepCases(), "racingQuad", "negative_yaw_rate");
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase positiveRoll =
				find(audit.sweepCases(), "racingQuad", "positive_roll_rate");
		DroneConfig config = DroneConfig.racingQuad();

		assertEquals(4.8, positivePitch.bodyRateXRadPerSecond(), 1.0e-12);
		assertEquals(0.0, positivePitch.bodyRateYRadPerSecond(), 1.0e-12);
		assertEquals(0.0, positivePitch.bodyRateZRadPerSecond(), 1.0e-12);
		assertEquals(1.0, positivePitch.rateSign(), 1.0e-12);
		assertEquals("pitch_x", positivePitch.bodyAxisName());
		assertEquals("pitch_angular_drag_positive", positivePitch.fitRole());

		assertEquals(0.0, negativeYaw.bodyRateXRadPerSecond(), 1.0e-12);
		assertEquals(-4.8, negativeYaw.bodyRateYRadPerSecond(), 1.0e-12);
		assertEquals(0.0, negativeYaw.bodyRateZRadPerSecond(), 1.0e-12);
		assertEquals(-1.0, negativeYaw.rateSign(), 1.0e-12);
		assertEquals("yaw_y", negativeYaw.bodyAxisName());
		assertEquals(0.68, negativeYaw.axisTangentialSpeedScale(), 1.0e-12);
		assertEquals(4.8 * negativeYaw.referenceRadiusMeters() * 0.68,
				negativeYaw.maxTangentialSpeedMetersPerSecond(), 1.0e-12);

		assertEquals(0.0, positiveRoll.bodyRateXRadPerSecond(), 1.0e-12);
		assertEquals(0.0, positiveRoll.bodyRateYRadPerSecond(), 1.0e-12);
		assertEquals(4.8, positiveRoll.bodyRateZRadPerSecond(), 1.0e-12);
		assertEquals("roll_z", positiveRoll.bodyAxisName());
		assertEquals(config.angularDragCoefficient() * positiveRoll.bodyRateMagnitudeRadPerSecond(),
				positiveRoll.currentDampingTorqueMagnitudeNewtonMeters(), 1.0e-15);
		assertFalse(positiveRoll.angularDragCalibrationAllowed());
	}

	@Test
	void casesForPresetRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.casesForPreset("", DroneConfig.racingQuad(), 4.8, 72));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.casesForPreset("racingQuad", null, 4.8, 72));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.casesForPreset("racingQuad", DroneConfig.racingQuad(), 0.0, 72));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.casesForPreset("racingQuad", DroneConfig.racingQuad(), 4.8, 0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepAudit audit =
				Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_static_airframe_rotational_damping_sweep_plan_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_rotational_damping_sweep_plan_summary,all_sweeps,sweep_case_count,24,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_rotational_damping_sweep_plan_summary,all_sweeps,angular_drag_calibration_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_rotational_damping_sweep_plan_case,racingQuad:positive_pitch_rate,fit_role,pitch_angular_drag_positive,")));
	}

	private static Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase find(
			List<Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase> cases,
			String presetName,
			String sweepName
	) {
		return cases.stream()
				.filter(sweepCase -> presetName.equals(sweepCase.presetName())
						&& sweepName.equals(sweepCase.sweepName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_static_airframe_rotational_damping_sweep_plan_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
