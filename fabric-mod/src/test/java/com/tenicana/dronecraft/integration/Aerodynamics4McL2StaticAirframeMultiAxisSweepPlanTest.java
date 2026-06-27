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

class Aerodynamics4McL2StaticAirframeMultiAxisSweepPlanTest {
	@Test
	void auditBuildsStableMultiAxisSweepPlan() {
		Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.MultiAxisSweepAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.audit();

		assertEquals("A4MC-L2-Static-Airframe-Multi-Axis-Sweep-Plan-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Multi-axis sweep plan"));
		assertEquals(545, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(6, audit.sweepCasesPerPreset());
		assertEquals(22, audit.sweepCaseMetricCount());
		assertEquals(11, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(24, audit.sweepCases().size());

		assertTrue(audit.sweepCases().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase::coefficientFitReady));
		assertTrue(audit.sweepCases().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase::outputFlowAtlas));
		assertTrue(audit.sweepCases().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase::computeForceMoment));
		assertEquals(8, audit.sweepCases().stream()
				.filter(Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase::forwardDragFitSample)
				.count());
		assertEquals(8, audit.extrema().sideforceFitSampleCount());
		assertEquals(8, audit.extrema().liftFitSampleCount());
		assertEquals(24, audit.extrema().momentPressureCenterFitSampleCount());
		assertEquals(18.0, audit.extrema().maxInletSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(12.0, audit.extrema().maxSweepAngleDegrees(), 1.0e-12);
	}

	@Test
	void sideslipAndAngleOfAttackVectorsKeepReferenceSpeed() {
		Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.MultiAxisSweepAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.audit();
		Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase rightSideslip =
				find(audit.sweepCases(), "racingQuad", "right_sideslip_12deg");
		Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase positiveAoa =
				find(audit.sweepCases(), "racingQuad", "positive_aoa_12deg");
		Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase forward =
				find(audit.sweepCases(), "racingQuad", "forward_drag");

		double angle = Math.toRadians(12.0);
		assertEquals(18.0 * Math.sin(angle), rightSideslip.inletVxMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, rightSideslip.inletVyMetersPerSecond(), 1.0e-12);
		assertEquals(-18.0 * Math.cos(angle), rightSideslip.inletVzMetersPerSecond(), 1.0e-12);
		assertEquals(18.0, rightSideslip.inletSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(Math.sin(angle), rightSideslip.normalizedInletX(), 1.0e-12);
		assertEquals(-Math.cos(angle), rightSideslip.normalizedInletZ(), 1.0e-12);

		assertEquals(18.0 * Math.sin(angle), positiveAoa.inletVyMetersPerSecond(), 1.0e-12);
		assertEquals(-18.0 * Math.cos(angle), positiveAoa.inletVzMetersPerSecond(), 1.0e-12);
		assertEquals(18.0, positiveAoa.inletSpeedMetersPerSecond(), 1.0e-12);
		assertTrue(rightSideslip.projectedReferenceAreaSquareMeters() > forward.projectedReferenceAreaSquareMeters());
		assertTrue(positiveAoa.projectedReferenceAreaSquareMeters() > forward.projectedReferenceAreaSquareMeters());
	}

	@Test
	void casesForPresetRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.casesForPreset("", DroneConfig.racingQuad(), 18.0, 72));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.casesForPreset("racingQuad", null, 18.0, 72));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.casesForPreset("racingQuad", DroneConfig.racingQuad(), 0.0, 72));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.casesForPreset("racingQuad", DroneConfig.racingQuad(), 18.0, 0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.MultiAxisSweepAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_static_airframe_multi_axis_sweep_plan_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_sweep_plan_summary,all_sweeps,sweep_case_count,24,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_sweep_plan_case,racingQuad:right_sideslip_12deg,fit_role,sideforce_yaw_positive,")));
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase find(
			List<Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase> cases,
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
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_static_airframe_multi_axis_sweep_plan_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
