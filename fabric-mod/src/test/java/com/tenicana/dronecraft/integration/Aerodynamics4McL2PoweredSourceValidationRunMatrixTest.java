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

class Aerodynamics4McL2PoweredSourceValidationRunMatrixTest {
	@Test
	void auditBuildsSkippedValidationRunsFromUnavailableSeeds() {
		Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Validation-Run-Matrix-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("powered-run blocker message"));
		assertEquals(220, audit.packetMetricRowCount());
		assertEquals(8, audit.sourceReferenceCount());
		assertEquals(8, audit.validationSampleCount());
		assertEquals(25, audit.validationMetricCount());
		assertEquals(11, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(8, audit.runs().size());

		for (Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary run : audit.runs()) {
			assertTrue("hover".equals(run.spinState()) || "cruise".equals(run.spinState()));
			assertTrue(run.validationPacketId().contains("Validation"));
			assertTrue(run.acceptanceGatePacketId().contains("Acceptance-Gate"));
			assertFalse(run.validationSeedReady());
			assertFalse(run.baselineSubtractedDeltaReady());
			assertFalse(run.validationInvoked());
			assertFalse(run.validationPassed());
			assertEquals("hover".equals(run.spinState()), run.hoverValidationTarget());
			assertEquals("cruise".equals(run.spinState()), run.cruiseValidationTarget());
			assertEquals(0.0, run.forceDeltaMagnitudeNewtons(), 1.0e-12);
			assertTrue(run.targetForceMagnitudeNewtons() > 0.0);
			assertEquals(0.0, run.forceErrorNewtons(), 1.0e-12);
			assertEquals(0.0, run.forceErrorRatio(), 1.0e-12);
			assertEquals(0.0, run.momentDeltaMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.targetMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.momentErrorNewtonMeters(), 1.0e-12);
			assertEquals(0.0, run.centerOfForceOffsetMeters(), 1.0e-12);
			assertEquals(0.0, run.targetCenterOfForceOffsetMeters(), 1.0e-12);
			assertEquals(0.0, run.centerOfForceErrorMeters(), 1.0e-12);
			assertFalse(run.acceptanceResultCandidate());
			assertEquals("SKIPPED", run.status());
			assertEquals("powered-source-api-surface-missing", run.message());
			assertEquals("test-runtime", run.staticRuntimeInfo());
			assertEquals("plan-only-powered-source-api-unavailable", run.poweredRuntimeInfo());
		}

		assertEquals(8, audit.extrema().validationRunCount());
		assertEquals(4, audit.extrema().hoverValidationRunCount());
		assertEquals(4, audit.extrema().cruiseValidationRunCount());
		assertEquals(0, audit.extrema().validationSeedReadyCount());
		assertEquals(0, audit.extrema().validationInvokedCount());
		assertEquals(0, audit.extrema().validationPassedCount());
		assertEquals(8, audit.extrema().validationSkippedCount());
		assertEquals(0, audit.extrema().validationFailedCount());
		assertEquals(0, audit.extrema().acceptanceResultCandidateCount());
		assertEquals(0.0, audit.extrema().maxForceErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxCenterOfForceErrorMeters(), 1.0e-12);
	}

	@Test
	void readySeedsBecomePassedOrFailedValidationRuns() {
		Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed base =
				Aerodynamics4McL2PoweredSourceResultSeed.audit(getClass().getClassLoader()).seeds().get(0);
		Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary passed =
				Aerodynamics4McL2PoweredSourceValidationRunMatrix.summary(readySeed(base, true, 0.0, 0.0));
		Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary failed =
				Aerodynamics4McL2PoweredSourceValidationRunMatrix.summary(readySeed(base, false, 0.50, 0.12));

		assertTrue(passed.validationSeedReady());
		assertTrue(passed.validationInvoked());
		assertTrue(passed.validationPassed());
		assertTrue(passed.acceptanceResultCandidate());
		assertEquals("PASSED", passed.status());
		assertEquals("validation-result-ready", passed.message());

		assertTrue(failed.validationSeedReady());
		assertTrue(failed.validationInvoked());
		assertFalse(failed.validationPassed());
		assertFalse(failed.acceptanceResultCandidate());
		assertEquals("FAILED", failed.status());
		assertEquals("validation-result-failed", failed.message());
		assertEquals(0.50, failed.forceErrorRatio(), 1.0e-12);
		assertEquals(0.12, failed.centerOfForceErrorMeters(), 1.0e-12);
	}

	@Test
	void auditAndSummaryRejectInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit(
						(Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceResultSeedAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceValidationRunMatrix.summary(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_validation_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_run_summary,all_runs,validation_run_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_run_summary,all_runs,validation_skipped_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_run,racingQuad:hover,status,SKIPPED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_run,racingQuad:hover,message,powered-source-api-surface-missing,")));
	}

	private static Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed readySeed(
			Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed seed,
			boolean passed,
			double forceErrorRatio,
			double centerOfForceErrorMeters
	) {
		return new Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed(
				seed.presetName(),
				seed.spinState(),
				seed.validationPacketId(),
				seed.acceptanceGatePacketId(),
				seed.staticBaselineAvailable(),
				seed.staticBaselineHasForceMoment(),
				true,
				true,
				"OK",
				"none",
				true,
				true,
				true,
				true,
				"none",
				"none",
				true,
				true,
				seed.staticForceXNewtons(),
				seed.staticForceYNewtons(),
				seed.staticForceZNewtons(),
				seed.staticForceMagnitudeNewtons(),
				seed.staticMomentXNewtonMeters(),
				seed.staticMomentYNewtonMeters(),
				seed.staticMomentZNewtonMeters(),
				seed.staticMomentMagnitudeNewtonMeters(),
				0.0,
				seed.targetForceMagnitudeNewtons(),
				0.0,
				seed.targetForceMagnitudeNewtons(),
				0.0,
				0.0,
				0.0,
				0.0,
				seed.targetCenterOfForceOffsetMeters(),
				seed.targetForceMagnitudeNewtons(),
				seed.targetMomentMagnitudeNewtonMeters(),
				seed.targetCenterOfForceOffsetMeters(),
				forceErrorRatio * seed.targetForceMagnitudeNewtons(),
				forceErrorRatio,
				0.0,
				centerOfForceErrorMeters,
				passed,
				passed ? "READY_PASS" : "READY_FAIL",
				passed ? "validation-seed-passed" : "validation-seed-failed",
				seed.staticRuntimeInfo(),
				"live-runtime"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_validation_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
