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

class Aerodynamics4McL2PoweredSourceResultSeedTest {
	@Test
	void auditBuildsUnavailableValidationSeedsWithStaticTestBaseline() {
		Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceResultSeedAudit audit =
				Aerodynamics4McL2PoweredSourceResultSeed.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Result-Seed-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("validation remains unavailable"));
		assertEquals(332, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(8, audit.seedSampleCount());
		assertEquals(39, audit.seedMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(8, audit.seeds().size());

		for (Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed seed : audit.seeds()) {
			assertTrue("hover".equals(seed.spinState()) || "cruise".equals(seed.spinState()));
			assertTrue(seed.validationPacketId().contains("Validation"));
			assertTrue(seed.acceptanceGatePacketId().contains("Acceptance-Gate"));
			assertTrue(seed.staticBaselineAvailable());
			assertTrue(seed.staticBaselineHasForceMoment());
			assertFalse(seed.poweredRunAvailable());
			assertFalse(seed.poweredRunHasForceMoment());
			assertFalse(seed.baselineSubtractedDeltaReady());
			assertFalse(seed.validationSeedReady());
			assertEquals(1.25, seed.staticForceXNewtons(), 1.0e-6);
			assertEquals(-0.50, seed.staticForceYNewtons(), 1.0e-6);
			assertEquals(3.00, seed.staticForceZNewtons(), 1.0e-6);
			assertTrue(seed.staticForceMagnitudeNewtons() > 0.0);
			assertEquals(0.20, seed.staticMomentXNewtonMeters(), 1.0e-6);
			assertEquals(-0.10, seed.staticMomentYNewtonMeters(), 1.0e-6);
			assertEquals(0.40, seed.staticMomentZNewtonMeters(), 1.0e-6);
			assertEquals(0.0, seed.poweredForceDeltaMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.0, seed.poweredMomentDeltaMagnitudeNewtonMeters(), 1.0e-12);
			assertTrue(seed.targetForceMagnitudeNewtons() > 0.0);
			assertEquals(0.0, seed.targetMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, seed.targetCenterOfForceOffsetMeters(), 1.0e-12);
			assertEquals(0.0, seed.forceErrorNewtons(), 1.0e-12);
			assertEquals(0.0, seed.forceErrorRatio(), 1.0e-12);
			assertEquals(0.0, seed.momentErrorNewtonMeters(), 1.0e-12);
			assertEquals(0.0, seed.centerOfForceErrorMeters(), 1.0e-12);
			assertFalse(seed.validationPassed());
			assertEquals("UNAVAILABLE", seed.status());
			assertEquals("powered-source-run-unavailable", seed.message());
			assertEquals("test-runtime", seed.staticRuntimeInfo());
			assertEquals("plan-only-powered-source-api-unavailable", seed.poweredRuntimeInfo());
		}

		assertEquals(8, audit.extrema().validationSeedCount());
		assertEquals(4, audit.extrema().hoverSeedCount());
		assertEquals(4, audit.extrema().cruiseSeedCount());
		assertEquals(8, audit.extrema().staticBaselineAvailableCount());
		assertEquals(8, audit.extrema().staticBaselineForceMomentCount());
		assertEquals(0, audit.extrema().poweredRunAvailableCount());
		assertEquals(0, audit.extrema().poweredRunForceMomentCount());
		assertEquals(0, audit.extrema().baselineSubtractedDeltaReadyCount());
		assertEquals(0, audit.extrema().validationSeedReadyCount());
		assertEquals(0, audit.extrema().validationPassedCount());
		assertTrue(audit.extrema().maxTargetForceMagnitudeNewtons() > 0.0);
		assertEquals(0.0, audit.extrema().maxForceErrorRatio(), 1.0e-12);
	}

	@Test
	void matchingPoweredDeltaCreatesPassingValidationSeed() {
		Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary currentRun =
				Aerodynamics4McL2PoweredSourceRunMatrix.audit().runs().get(0);
		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets().get(0);
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary baseline =
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader()).runs().get(0);

		Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed seed =
				Aerodynamics4McL2PoweredSourceResultSeed.seed(
						liveRun(currentRun,
								target.targetForceXNewtons(),
								target.targetForceYNewtons(),
								target.targetForceZNewtons(),
								target.targetMomentXNewtonMeters(),
								target.targetMomentYNewtonMeters(),
								target.targetMomentZNewtonMeters(),
								target.targetCenterOfThrustOffsetMeters()),
						baseline
				);

		assertTrue(seed.baselineSubtractedDeltaReady());
		assertTrue(seed.validationSeedReady());
		assertTrue(seed.validationPassed());
		assertEquals("READY_PASS", seed.status());
		assertEquals("validation-seed-passed", seed.message());
		assertEquals(0.0, seed.forceErrorNewtons(), 1.0e-12);
		assertEquals(0.0, seed.momentErrorNewtonMeters(), 1.0e-12);
		assertEquals(0.0, seed.centerOfForceErrorMeters(), 1.0e-12);
	}

	@Test
	void offTargetPoweredDeltaCreatesFailingValidationSeed() {
		Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary currentRun =
				Aerodynamics4McL2PoweredSourceRunMatrix.audit().runs().get(3);
		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets().get(3);
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary baseline =
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader()).runs().get(3);

		Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed seed =
				Aerodynamics4McL2PoweredSourceResultSeed.seed(
						liveRun(currentRun,
								target.targetForceXNewtons(),
								target.targetForceYNewtons() * 0.50,
								target.targetForceZNewtons(),
								target.targetMomentXNewtonMeters(),
								target.targetMomentYNewtonMeters(),
								target.targetMomentZNewtonMeters(),
								target.targetCenterOfThrustOffsetMeters()),
						baseline
				);

		assertTrue(seed.baselineSubtractedDeltaReady());
		assertTrue(seed.validationSeedReady());
		assertFalse(seed.validationPassed());
		assertEquals("READY_FAIL", seed.status());
		assertEquals("validation-seed-failed", seed.message());
		assertTrue(seed.forceErrorRatio() > Aerodynamics4McL2PoweredHoverValidation.FORCE_MATCH_RELATIVE_TOLERANCE);
	}

	@Test
	void seedRejectsInvalidInputs() {
		Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary run =
				Aerodynamics4McL2PoweredSourceRunMatrix.audit().runs().get(0);
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary baseline =
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader()).runs().get(0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceResultSeed.seed(null, baseline));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceResultSeed.seed(run, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceResultSeed.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceResultSeed.audit(
						null,
						Aerodynamics4McL2PoweredSourceRunMatrix.audit()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceResultSeed.audit(
						Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader()),
						null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceResultSeedAudit audit =
				Aerodynamics4McL2PoweredSourceResultSeed.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_result_seed_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_result_seed_summary,all_seeds,validation_seed_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_result_seed_summary,all_seeds,powered_run_available_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_result_seed,racingQuad:hover,status,UNAVAILABLE,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_result_seed,racingQuad:hover,message,powered-source-run-unavailable,")));
	}

	private static Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary liveRun(
			Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary run,
			double forceX,
			double forceY,
			double forceZ,
			double momentX,
			double momentY,
			double momentZ,
			double centerOfForceOffsetMeters
	) {
		return new Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary(
				run.presetName(),
				run.spinState(),
				run.sourceMapId(),
				run.validationPacketId(),
				run.acceptanceGatePacketId(),
				run.rotorCount(),
				run.sourceTermCount(),
				run.gridCellCount(),
				run.cellSizeMeters(),
				run.steps(),
				run.inletSpeedMetersPerSecond(),
				run.totalThrustNewtons(),
				run.meanPressureJumpPascals(),
				run.maxPressureJumpPascals(),
				run.targetForceMagnitudeNewtons(),
				run.targetMomentMagnitudeNewtonMeters(),
				run.targetCenterOfForceOffsetMeters(),
				run.baselineForceMomentRequest(),
				run.poweredSourceApiRequired(),
				true,
				true,
				true,
				true,
				true,
				5,
				5,
				"none",
				true,
				5,
				5,
				"none",
				run.requestForceMoment(),
				run.requestFlowAtlas(),
				true,
				true,
				true,
				true,
				forceX,
				forceY,
				forceZ,
				magnitude(forceX, forceY, forceZ),
				momentX,
				momentY,
				momentZ,
				magnitude(momentX, momentY, momentZ),
				centerOfForceOffsetMeters,
				"OK",
				"none",
				"live-runtime"
		);
	}

	private static double magnitude(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_source_result_seed_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
