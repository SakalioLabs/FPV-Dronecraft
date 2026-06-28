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

class Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeedTest {
	@Test
	void auditBuildsUnavailableSurfaceWakeValidationSeeds() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeResultSeedAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Result-Seed-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("validation remains unavailable"));
		assertEquals(4886, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(128, audit.seedSampleCount());
		assertEquals(38, audit.seedMetricCount());
		assertEquals(14, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(128, audit.seeds().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
				: audit.seeds()) {
			assertEquals("hover", seed.spinState());
			assertTrue("ground".equals(seed.surfaceType()) || "ceiling".equals(seed.surfaceType()));
			assertTrue(seed.rotorIndex() >= 0);
			assertTrue(seed.rotorIndex() < 4);
			assertTrue(seed.clearanceOverRadius() == 0.5
					|| seed.clearanceOverRadius() == 1.0
					|| seed.clearanceOverRadius() == 2.0
					|| seed.clearanceOverRadius() == 4.0);
			assertTrue(seed.clearanceMeters() > 0.0);
			assertFalse(seed.validationRunAvailable());
			assertFalse(seed.pressureEvidenceAvailable());
			assertFalse(seed.velocityEvidenceAvailable());
			assertFalse(seed.transitEvidenceAvailable());
			assertFalse(seed.validationSeedReady());
			assertTrue(seed.targetPressurePascals() > 0.0);
			assertEquals(0.0, seed.observedPressurePascals(), 1.0e-12);
			assertEquals(0.0, seed.pressureErrorPascals(), 1.0e-12);
			assertEquals(0.0, seed.pressureErrorRatio(), 1.0e-12);
			assertTrue(seed.targetWakeVelocityMetersPerSecond() > 0.0);
			assertEquals(0.0, seed.observedWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, seed.wakeVelocityErrorMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, seed.wakeVelocityErrorRatio(), 1.0e-12);
			assertTrue(seed.targetFastArrivalSeconds() > 0.0);
			assertTrue(seed.targetSlowArrivalSeconds() > seed.targetFastArrivalSeconds());
			assertEquals(seed.targetSlowArrivalSeconds() - seed.targetFastArrivalSeconds(),
					seed.targetArrivalBandSeconds(), 1.0e-12);
			assertEquals(0.0, seed.observedArrivalTimeSeconds(), 1.0e-12);
			assertEquals(0.0, seed.arrivalBandErrorSeconds(), 1.0e-12);
			assertEquals(0.0, seed.arrivalBandErrorRatio(), 1.0e-12);
			assertEquals(Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PRESSURE_RELATIVE_TOLERANCE,
					seed.pressureToleranceRatio(), 1.0e-12);
			assertEquals(Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.WAKE_VELOCITY_RELATIVE_TOLERANCE,
					seed.velocityToleranceRatio(), 1.0e-12);
			assertEquals(Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.ARRIVAL_BAND_TOLERANCE_FRACTION,
					seed.arrivalBandToleranceFraction(), 1.0e-12);
			assertFalse(seed.pressureValidationPassed());
			assertFalse(seed.velocityValidationPassed());
			assertFalse(seed.arrivalTimeValidationPassed());
			assertFalse(seed.validationPassed());
			assertEquals("UNAVAILABLE", seed.status());
			assertEquals("surface-wake-run-unavailable", seed.message());
			assertEquals("SKIPPED", seed.runStatus());
			assertEquals("surface-wake-readiness-gate-blocked", seed.runMessage());
			assertEquals("audit-only-unvalidated-surface-wake-validation-run", seed.runRuntimeInfo());
			assertEquals("audit-only-unvalidated-surface-wake-result-seed", seed.resultRuntimeInfo());
		}

		assertEquals(128, audit.extrema().validationSeedCount());
		assertEquals(64, audit.extrema().groundSeedCount());
		assertEquals(64, audit.extrema().ceilingSeedCount());
		assertEquals(0, audit.extrema().validationRunAvailableCount());
		assertEquals(0, audit.extrema().pressureEvidenceCount());
		assertEquals(0, audit.extrema().velocityEvidenceCount());
		assertEquals(0, audit.extrema().transitEvidenceCount());
		assertEquals(0, audit.extrema().validationSeedReadyCount());
		assertEquals(0, audit.extrema().validationPassedCount());
		assertEquals(0.0, audit.extrema().maxPressureErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxWakeVelocityErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxArrivalBandErrorRatio(), 1.0e-12);
		assertTrue(audit.extrema().maxTargetPressurePascals() > 0.0);
		assertTrue(audit.extrema().maxTargetWakeVelocityMetersPerSecond() > 0.0);
	}

	@Test
	void matchingLiveProbeEvidenceCreatesPassingSeed() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run =
				liveRun(currentRun("racingQuad", "ground", 1.0, 0), 1.0, 1.0, 0.5);

		Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed =
				Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.seed(run);

		assertTrue(seed.validationRunAvailable());
		assertTrue(seed.pressureEvidenceAvailable());
		assertTrue(seed.velocityEvidenceAvailable());
		assertTrue(seed.transitEvidenceAvailable());
		assertTrue(seed.validationSeedReady());
		assertTrue(seed.pressureValidationPassed());
		assertTrue(seed.velocityValidationPassed());
		assertTrue(seed.arrivalTimeValidationPassed());
		assertTrue(seed.validationPassed());
		assertEquals("READY_PASS", seed.status());
		assertEquals("surface-wake-validation-seed-passed", seed.message());
		assertEquals(0.0, seed.pressureErrorRatio(), 1.0e-12);
		assertEquals(0.0, seed.wakeVelocityErrorRatio(), 1.0e-12);
		assertEquals(0.0, seed.arrivalBandErrorRatio(), 1.0e-12);
	}

	@Test
	void offTargetPressureCreatesFailingSeed() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run =
				liveRun(currentRun("racingQuad", "ground", 1.0, 0), 0.50, 1.0, 0.5);

		Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed =
				Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.seed(run);

		assertTrue(seed.validationSeedReady());
		assertFalse(seed.pressureValidationPassed());
		assertTrue(seed.velocityValidationPassed());
		assertTrue(seed.arrivalTimeValidationPassed());
		assertFalse(seed.validationPassed());
		assertEquals("READY_FAIL", seed.status());
		assertEquals("surface-wake-validation-seed-failed", seed.message());
		assertTrue(seed.pressureErrorRatio()
				> Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PRESSURE_RELATIVE_TOLERANCE);
	}

	@Test
	void missingEvidenceKeepsSeedUnavailable() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run =
				liveRunWithoutTransit(currentRun("racingQuad", "ground", 1.0, 0));

		Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed =
				Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.seed(run);

		assertTrue(seed.validationRunAvailable());
		assertTrue(seed.pressureEvidenceAvailable());
		assertTrue(seed.velocityEvidenceAvailable());
		assertFalse(seed.transitEvidenceAvailable());
		assertFalse(seed.validationSeedReady());
		assertEquals("UNAVAILABLE", seed.status());
		assertEquals("arrival-time-evidence-missing", seed.message());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.seed(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeResultSeedAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_result_seed_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_result_seed_summary,all_seeds,validation_seed_count,128,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_result_seed_summary,all_seeds,validation_seed_ready_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_result_seed,racingQuad:ground:hR1.0:rotor0,status,UNAVAILABLE,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_result_seed,racingQuad:ground:hR1.0:rotor0,message,surface-wake-run-unavailable,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun currentRun(
			String presetName,
			String surfaceType,
			double clearanceOverRadius,
			int rotorIndex
	) {
		return Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.audit().runs().stream()
				.filter(run -> presetName.equals(run.presetName())
						&& surfaceType.equals(run.surfaceType())
						&& Math.abs(clearanceOverRadius - run.clearanceOverRadius()) <= 1.0e-12
						&& rotorIndex == run.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun liveRun(
			Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run,
			double pressureScale,
			double velocityScale,
			double arrivalBandFraction
	) {
		double observedArrival = run.fastFarWakeTransitSeconds()
				+ (run.slowDiskPlaneTransitSeconds() - run.fastFarWakeTransitSeconds()) * arrivalBandFraction;
		return copyLive(run,
				run.perRotorImpingementPressurePascals() * pressureScale,
				run.farWakeVelocityMetersPerSecond() * velocityScale,
				observedArrival,
				true,
				true,
				true);
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun liveRunWithoutTransit(
			Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run
	) {
		return copyLive(run,
				run.perRotorImpingementPressurePascals(),
				run.farWakeVelocityMetersPerSecond(),
				0.0,
				true,
				true,
				false);
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun copyLive(
			Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run,
			double observedPressurePascals,
			double observedWakeVelocityMetersPerSecond,
			double observedArrivalTimeSeconds,
			boolean hasPressureEvidence,
			boolean hasVelocityEvidence,
			boolean hasTransitEvidence
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun(
				run.presetName(),
				run.spinState(),
				run.surfaceType(),
				run.rotorIndex(),
				run.clearanceOverRadius(),
				run.clearanceMeters(),
				run.rotorDiskRadiusMeters(),
				run.idealInducedVelocityMetersPerSecond(),
				run.farWakeVelocityMetersPerSecond(),
				run.fastFarWakeTransitSeconds(),
				run.meanWakeTransitSeconds(),
				run.slowDiskPlaneTransitSeconds(),
				run.requiredMaxSamplePeriodSeconds(),
				run.requiredMaxSamplePeriodSeconds(),
				run.requiredSubstepsPerMinecraftTick(),
				run.perRotorImpingementPressurePascals(),
				run.surfaceCurveMultiplier(),
				run.perRotorSurfaceCushionForceNewtons(),
				true,
				true,
				true,
				true,
				true,
				run.requestPressureProbe(),
				run.requestVelocityProbe(),
				run.requestTransientSeries(),
				true,
				true,
				true,
				hasPressureEvidence,
				hasVelocityEvidence,
				hasTransitEvidence,
				observedPressurePascals,
				observedWakeVelocityMetersPerSecond,
				observedArrivalTimeSeconds,
				0.0,
				0.0,
				0.0,
				"OK",
				"live-surface-wake-sample",
				"live-runtime"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_hover_surface_wake_result_seed_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
