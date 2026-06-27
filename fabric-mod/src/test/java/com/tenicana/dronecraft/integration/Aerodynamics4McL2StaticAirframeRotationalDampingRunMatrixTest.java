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

class Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrixTest {
	@Test
	void auditBuildsSkippedRotationalDampingRunMatrixUntilApiExists() {
		Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.audit();

		assertEquals("A4MC-L2-Static-Airframe-Rotational-Damping-Run-Matrix-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("rotational runs are skipped"));
		assertEquals(978, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(24, audit.sweepCaseSampleCount());
		assertEquals(40, audit.runMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(24, audit.runs().size());

		for (Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunSummary run : audit.runs()) {
			assertTrue(run.baselineStaticRequestReady());
			assertTrue(run.rotationalFlowApiRequired());
			assertFalse(run.rotationalFlowApiAvailable());
			assertTrue(run.movingBoundaryOrVelocityFieldRequired());
			assertTrue(run.expectedOpposingMomentSign());
			assertTrue(run.requestForceMoment());
			assertFalse(run.requestFlowAtlas());
			assertFalse(run.rotationalRequestBuildable());
			assertFalse(run.invoked());
			assertFalse(run.succeeded());
			assertFalse(run.available());
			assertFalse(run.hasForceMoment());
			assertEquals(0.0, run.momentMagnitudeNewtonMeters(), 1.0e-15);
			assertFalse(run.angularDampingMomentSignValid());
			assertFalse(run.angularDragFitReady());
			assertEquals("SKIPPED", run.status());
			assertEquals("rotational-flow-api-unavailable", run.message());
			assertEquals("plan-only-rotational-flow-api-unavailable", run.runtimeInfo());
		}
		assertEquals(24, audit.extrema().runSummaryCount());
		assertEquals(24, audit.extrema().baselineStaticRequestReadyCount());
		assertEquals(0, audit.extrema().rotationalRequestBuildableCount());
		assertEquals(0, audit.extrema().invokedCount());
		assertEquals(0, audit.extrema().availableCount());
		assertEquals(0, audit.extrema().forceMomentCount());
		assertEquals(0, audit.extrema().angularDragFitReadyCount());
		assertEquals(0, audit.extrema().rotationalFlowApiAvailableCount());
		assertEquals(24, audit.extrema().rawFlowAtlasDisabledCount());
		assertEquals(24, audit.extrema().skippedForMissingRotationalApiCount());
	}

	@Test
	void summarySeparatesFutureBuildableRotationalRequestsFromCurrentUnavailableApi() {
		Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase sweepCase =
				withRotationalApiAvailable(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit()
						.sweepCases()
						.get(0));
		Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunSummary run =
				Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.summary(sweepCase);

		assertTrue(run.baselineStaticRequestReady());
		assertTrue(run.rotationalFlowApiAvailable());
		assertTrue(run.movingBoundaryOrVelocityFieldRequired());
		assertTrue(run.rotationalRequestBuildable());
		assertFalse(run.invoked());
		assertFalse(run.available());
		assertFalse(run.angularDragFitReady());
		assertEquals("PENDING", run.status());
		assertEquals("rotational-run-not-invoked", run.message());
	}

	@Test
	void runMatrixPreservesRotationalSweepPlanBodyRatesAndGeometry() {
		Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.audit();
		Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunSummary positivePitch =
				find(audit.runs(), "racingQuad", "positive_pitch_rate");
		Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunSummary negativeYaw =
				find(audit.runs(), "racingQuad", "negative_yaw_rate");
		Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunSummary positiveRoll =
				find(audit.runs(), "racingQuad", "positive_roll_rate");

		assertEquals("pitch_angular_drag_positive", positivePitch.fitRole());
		assertEquals("pitch_x", positivePitch.bodyAxisName());
		assertEquals(4.8, positivePitch.bodyRateXRadPerSecond(), 1.0e-12);
		assertEquals(1.0, positivePitch.rateSign(), 1.0e-12);
		assertTrue(positivePitch.maxTangentialSpeedMetersPerSecond() > 0.0);
		assertEquals(positivePitch.gridCellCount(), negativeYaw.gridCellCount());
		assertEquals(positivePitch.referenceLengthMeters(), positiveRoll.referenceLengthMeters(), 1.0e-12);

		assertEquals("yaw_y", negativeYaw.bodyAxisName());
		assertEquals(-4.8, negativeYaw.bodyRateYRadPerSecond(), 1.0e-12);
		assertEquals(-1.0, negativeYaw.rateSign(), 1.0e-12);
		assertEquals(0.68, negativeYaw.axisTangentialSpeedScale(), 1.0e-12);
		assertEquals(4.8 * negativeYaw.referenceRadiusMeters() * 0.68,
				negativeYaw.maxTangentialSpeedMetersPerSecond(), 1.0e-12);

		assertEquals("roll_z", positiveRoll.bodyAxisName());
		assertEquals(4.8, positiveRoll.bodyRateZRadPerSecond(), 1.0e-12);
		assertEquals(positiveRoll.bodyRateMagnitudeRadPerSecond() * positiveRoll.currentAngularDragCoefficient(),
				positiveRoll.currentDampingTorqueMagnitudeNewtonMeters(), 1.0e-15);
	}

	@Test
	void auditRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.summary(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunMatrixAudit audit =
				Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_static_airframe_rotational_damping_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_rotational_damping_run_matrix_summary,all_sweeps,skipped_for_missing_rotational_api_count,24,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_rotational_damping_run_matrix_case,racingQuad:positive_pitch_rate,status,SKIPPED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_rotational_damping_run_matrix_case,racingQuad:negative_yaw_rate,body_axis_name,yaw_y,")));
	}

	private static Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunSummary find(
			List<Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix.RotationalDampingRunSummary> runs,
			String presetName,
			String sweepName
	) {
		return runs.stream()
				.filter(run -> presetName.equals(run.presetName()) && sweepName.equals(run.sweepName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase withRotationalApiAvailable(
			Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase sweepCase
	) {
		return new Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase(
				sweepCase.presetName(),
				sweepCase.sweepName(),
				sweepCase.fitRole(),
				sweepCase.bodyAxisName(),
				sweepCase.bodyRateXRadPerSecond(),
				sweepCase.bodyRateYRadPerSecond(),
				sweepCase.bodyRateZRadPerSecond(),
				sweepCase.bodyRateMagnitudeRadPerSecond(),
				sweepCase.rateSign(),
				sweepCase.axisTangentialSpeedScale(),
				sweepCase.referenceRadiusMeters(),
				sweepCase.maxTangentialSpeedMetersPerSecond(),
				sweepCase.currentAngularDragCoefficient(),
				sweepCase.currentDampingTorqueMagnitudeNewtonMeters(),
				sweepCase.steps(),
				sweepCase.gridCellCount(),
				sweepCase.cellSizeMeters(),
				sweepCase.solidFraction(),
				sweepCase.referenceLengthMeters(),
				sweepCase.outputFlowAtlas(),
				sweepCase.computeForceMoment(),
				sweepCase.baselineStaticRequestReady(),
				sweepCase.rotationalFlowApiRequired(),
				true,
				sweepCase.movingBoundaryOrVelocityFieldRequired(),
				sweepCase.expectedOpposingMomentSign(),
				sweepCase.angularDragCalibrationAllowed(),
				"live-rotational-flow-api-available"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_static_airframe_rotational_damping_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
