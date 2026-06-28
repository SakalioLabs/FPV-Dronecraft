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

class Aerodynamics4McL2PoweredSourceRunMatrixTest {
	@Test
	void auditBuildsCurrentSkippedPoweredSourceRunMatrix() {
		Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunMatrixAudit audit =
				Aerodynamics4McL2PoweredSourceRunMatrix.audit();

		assertEquals("A4MC-L2-Powered-Source-Run-Matrix-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("current rows stay skipped"));
		assertEquals(351, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(8, audit.runSampleCount());
		assertEquals(41, audit.runMetricCount());
		assertEquals(15, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(8, audit.runs().size());

		for (Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary run : audit.runs()) {
			assertTrue("hover".equals(run.spinState()) || "cruise".equals(run.spinState()));
			assertEquals(4, run.rotorCount());
			assertEquals(4, run.sourceTermCount());
			assertTrue(run.gridCellCount() > 0);
			assertTrue(run.cellSizeMeters() > 0.0);
			assertTrue(run.steps() > 0);
			assertTrue(run.inletSpeedMetersPerSecond() > 0.0);
			assertTrue(run.totalThrustNewtons() > 0.0);
			assertTrue(run.meanPressureJumpPascals() > 0.0);
			assertTrue(run.maxPressureJumpPascals() >= run.meanPressureJumpPascals());
			assertEquals(run.totalThrustNewtons(), run.targetForceMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, run.targetMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.targetCenterOfForceOffsetMeters(), 1.0e-12);
			assertTrue(run.baselineForceMomentRequest());
			assertTrue(run.poweredSourceApiRequired());
			assertFalse(run.poweredSourceApiAvailable());
			assertFalse(run.requestBuildAllowed());
			assertFalse(run.readinessGateOpen());
			assertFalse(run.requestExecutionAllowed());
			assertTrue(run.requestForceMoment());
			assertFalse(run.requestFlowAtlas());
			assertFalse(run.invoked());
			assertFalse(run.succeeded());
			assertFalse(run.available());
			assertFalse(run.hasForceMoment());
			assertEquals(0.0, run.forceDeltaXNewtons(), 1.0e-12);
			assertEquals(0.0, run.forceDeltaYNewtons(), 1.0e-12);
			assertEquals(0.0, run.forceDeltaZNewtons(), 1.0e-12);
			assertEquals(0.0, run.forceDeltaMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaXNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaYNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaZNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.centerOfForceOffsetMeters(), 1.0e-12);
			assertEquals("SKIPPED", run.status());
			assertEquals("powered-source-readiness-gate-blocked", run.message());
			assertEquals("plan-only-powered-source-api-unavailable", run.runtimeInfo());
		}

		assertEquals(8, audit.extrema().runSummaryCount());
		assertEquals(4, audit.extrema().hoverRunCount());
		assertEquals(4, audit.extrema().cruiseRunCount());
		assertEquals(0, audit.extrema().readinessGateOpenCount());
		assertEquals(0, audit.extrema().requestExecutionAllowedCount());
		assertEquals(0, audit.extrema().requestBuildAllowedCount());
		assertEquals(0, audit.extrema().poweredSourceApiAvailableCount());
		assertEquals(0, audit.extrema().invokedCount());
		assertEquals(0, audit.extrema().availableCount());
		assertEquals(0, audit.extrema().forceMomentCount());
		assertEquals(8, audit.extrema().skippedForReadinessCount());
		assertEquals(0, audit.extrema().pendingExecutorCount());
		assertTrue(audit.extrema().maxGridCellCount() > 0);
		assertTrue(audit.extrema().maxTargetForceMagnitudeNewtons() > 0.0);
		assertTrue(audit.extrema().maxMeanPressureJumpPascals() > 0.0);
	}

	@Test
	void readyRequestsBecomePendingWithoutFakeForceMomentEvidence() {
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> buildable =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests().stream()
						.map(request -> copy(request, true, true))
						.toList();

		Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunMatrixAudit legacyProbe =
				Aerodynamics4McL2PoweredSourceRunMatrix.audit(true, true, true, buildable);
		assertEquals(0, legacyProbe.extrema().requestExecutionAllowedCount());
		assertEquals(8, legacyProbe.extrema().skippedForReadinessCount());

		Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunMatrixAudit audit =
				Aerodynamics4McL2PoweredSourceRunMatrix.audit(
						Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary(),
						true,
						true,
						buildable);

		assertEquals(8, audit.runs().size());
		for (Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary run : audit.runs()) {
			assertTrue(run.poweredSourceApiAvailable());
			assertTrue(run.requestBuildAllowed());
			assertTrue(run.readinessGateOpen());
			assertTrue(run.requestExecutionAllowed());
			assertEquals("PENDING", run.status());
			assertEquals("powered-source-executor-not-invoked", run.message());
			assertFalse(run.invoked());
			assertFalse(run.succeeded());
			assertFalse(run.available());
			assertFalse(run.hasForceMoment());
			assertEquals(0.0, run.forceDeltaXNewtons(), 1.0e-12);
			assertEquals(0.0, run.forceDeltaYNewtons(), 1.0e-12);
			assertEquals(0.0, run.forceDeltaZNewtons(), 1.0e-12);
			assertEquals(0.0, run.forceDeltaMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaXNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaYNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaZNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaMagnitudeNewtonMeters(), 1.0e-12);
		}
		assertEquals(8, audit.extrema().readinessGateOpenCount());
		assertEquals(8, audit.extrema().requestExecutionAllowedCount());
		assertEquals(8, audit.extrema().requestBuildAllowedCount());
		assertEquals(8, audit.extrema().poweredSourceApiAvailableCount());
		assertEquals(0, audit.extrema().invokedCount());
		assertEquals(0, audit.extrema().forceMomentCount());
		assertEquals(0, audit.extrema().skippedForReadinessCount());
		assertEquals(8, audit.extrema().pendingExecutorCount());
	}

	@Test
	void summaryAndAuditRejectInvalidInputs() {
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests();
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary readiness =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(false, false, false, requests);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRunMatrix.summary(null, readiness));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRunMatrix.summary(requests.get(0), null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRunMatrix.audit(false, false, false, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceRunMatrix.audit(null, false, false, requests));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunMatrixAudit audit =
				Aerodynamics4McL2PoweredSourceRunMatrix.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_run_summary,all_runs,run_summary_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_run_summary,all_runs,skipped_for_readiness_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_run,racingQuad:hover,status,SKIPPED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_run,racingQuad:hover,message,powered-source-readiness-gate-blocked,")));
	}

	private static Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest copy(
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request,
			boolean poweredSourceApiAvailable,
			boolean requestBuildAllowed
	) {
		return new Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest(
				request.presetName(),
				request.spinState(),
				request.sourceMapId(),
				request.validationPacketId(),
				request.acceptanceGatePacketId(),
				request.rotorCount(),
				request.sourceTermCount(),
				request.nx(),
				request.ny(),
				request.nz(),
				request.gridCellCount(),
				request.cellSizeMeters(),
				request.steps(),
				request.inletVxMetersPerSecond(),
				request.inletVyMetersPerSecond(),
				request.inletVzMetersPerSecond(),
				request.inletSpeedMetersPerSecond(),
				request.spinRatio(),
				request.totalThrustNewtons(),
				request.thrustToWeight(),
				request.totalOpenAreaSquareMeters(),
				request.meanPressureJumpPascals(),
				request.maxPressureJumpPascals(),
				request.netForceXNewtons(),
				request.netForceYNewtons(),
				request.netForceZNewtons(),
				request.netForceMagnitudeNewtons(),
				request.netMomentXNewtonMeters(),
				request.netMomentYNewtonMeters(),
				request.netMomentZNewtonMeters(),
				request.netMomentMagnitudeNewtonMeters(),
				request.centerOfThrustOffsetMeters(),
				request.baselineForceMomentRequest(),
				request.poweredSourceApiRequired(),
				poweredSourceApiAvailable,
				requestBuildAllowed,
				requestBuildAllowed ? "test-buildable" : request.runtimeInfo()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_source_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
