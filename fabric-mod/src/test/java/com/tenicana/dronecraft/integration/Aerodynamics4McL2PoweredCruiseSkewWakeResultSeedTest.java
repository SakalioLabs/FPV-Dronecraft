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

class Aerodynamics4McL2PoweredCruiseSkewWakeResultSeedTest {
	@Test
	void auditBuildsUnavailableCruiseSkewWakeValidationSeeds() {
		Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeResultSeedAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Result-Seed-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("validation remains unavailable"));
		assertEquals(9050, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(192, audit.seedSampleCount());
		assertEquals(47, audit.seedMetricCount());
		assertEquals(18, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(192, audit.seeds().size());

		for (Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
				: audit.seeds()) {
			assertEquals("cruise", seed.spinState());
			assertTrue(seed.rotorIndex() >= 0);
			assertTrue(seed.rotorIndex() < 4);
			assertTrue(seed.axialPlaneIndex() >= 1);
			assertTrue(seed.axialPlaneIndex() <= 4);
			assertTrue(seed.sweepColumnIndex() == -1 || seed.sweepColumnIndex() == 0 || seed.sweepColumnIndex() == 1);
			assertTrue(seed.axialPlaneFraction() > 0.0);
			assertFalse(seed.validationRunAvailable());
			assertFalse(seed.pressureEvidenceAvailable());
			assertFalse(seed.velocityEvidenceAvailable());
			assertFalse(seed.transitEvidenceAvailable());
			assertFalse(seed.momentumEvidenceAvailable());
			assertFalse(seed.validationSeedReady());
			assertTrue(seed.targetAxialWakePressurePascals() > 0.0);
			assertTrue(seed.targetResultantDynamicPressurePascals() > 0.0);
			assertEquals(0.0, seed.observedPressurePascals(), 1.0e-12);
			assertEquals(0.0, seed.pressureErrorPascals(), 1.0e-12);
			assertEquals(0.0, seed.pressureErrorRatio(), 1.0e-12);
			assertTrue(seed.targetResultantWakeVelocityMetersPerSecond() > 0.0);
			assertEquals(0.0, seed.observedWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, seed.wakeVelocityErrorMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, seed.wakeVelocityErrorRatio(), 1.0e-12);
			assertTrue(seed.targetCenterlineArrivalSeconds() > 0.0);
			assertTrue(seed.targetLateralArrivalSeconds() >= seed.targetCenterlineArrivalSeconds());
			assertEquals(seed.targetLateralArrivalSeconds() - seed.targetCenterlineArrivalSeconds(),
					seed.targetArrivalBandSeconds(), 1.0e-12);
			assertTrue(seed.targetArrivalToleranceSeconds() > 0.0);
			assertEquals(0.0, seed.observedArrivalTimeSeconds(), 1.0e-12);
			assertEquals(0.0, seed.arrivalWindowErrorSeconds(), 1.0e-12);
			assertEquals(0.0, seed.arrivalWindowErrorRatio(), 1.0e-12);
			assertTrue(seed.targetAxialMomentumFluxNewtons() > 0.0);
			assertEquals(0.0, seed.observedMomentumFluxNewtons(), 1.0e-12);
			assertEquals(0.0, seed.momentumErrorNewtons(), 1.0e-12);
			assertEquals(0.0, seed.momentumErrorRatio(), 1.0e-12);
			assertEquals(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PRESSURE_RELATIVE_TOLERANCE,
					seed.pressureToleranceRatio(), 1.0e-12);
			assertEquals(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.WAKE_VELOCITY_RELATIVE_TOLERANCE,
					seed.velocityToleranceRatio(), 1.0e-12);
			assertEquals(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.ARRIVAL_TIME_TOLERANCE_FRACTION,
					seed.arrivalTimeToleranceFraction(), 1.0e-12);
			assertEquals(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.MOMENTUM_RELATIVE_TOLERANCE,
					seed.momentumToleranceRatio(), 1.0e-12);
			assertFalse(seed.pressureValidationPassed());
			assertFalse(seed.velocityValidationPassed());
			assertFalse(seed.arrivalTimeValidationPassed());
			assertFalse(seed.momentumValidationPassed());
			assertFalse(seed.validationPassed());
			assertEquals("UNAVAILABLE", seed.status());
			assertEquals("cruise-skew-wake-run-unavailable", seed.message());
			assertEquals("SKIPPED", seed.runStatus());
			assertEquals("cruise-skew-wake-readiness-gate-blocked", seed.runMessage());
			assertEquals("audit-only-unvalidated-cruise-skew-wake-validation-run", seed.runRuntimeInfo());
			assertEquals("audit-only-unvalidated-cruise-skew-wake-result-seed", seed.resultRuntimeInfo());
		}

		assertEquals(192, audit.extrema().validationSeedCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(64, audit.extrema().centerlineSweepSeedCount());
		assertEquals(128, audit.extrema().lateralSweepSeedCount());
		assertEquals(0, audit.extrema().validationRunAvailableCount());
		assertEquals(0, audit.extrema().pressureEvidenceCount());
		assertEquals(0, audit.extrema().velocityEvidenceCount());
		assertEquals(0, audit.extrema().transitEvidenceCount());
		assertEquals(0, audit.extrema().momentumEvidenceCount());
		assertEquals(0, audit.extrema().validationSeedReadyCount());
		assertEquals(0, audit.extrema().validationPassedCount());
		assertEquals(0.0, audit.extrema().maxPressureErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxWakeVelocityErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxArrivalWindowErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxMomentumErrorRatio(), 1.0e-12);
		assertTrue(audit.extrema().maxTargetResultantDynamicPressurePascals() > 0.0);
		assertTrue(audit.extrema().maxTargetResultantWakeVelocityMetersPerSecond() > 0.0);
		assertTrue(audit.extrema().maxTargetAxialMomentumFluxNewtons() > 0.0);
	}

	@Test
	void matchingLiveProbeEvidenceCreatesPassingSeed() {
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run =
				liveRun(currentRun("racingQuad", 1, 0, 0), 1.0, 1.0, 0.0, 1.0);

		Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.seed(run);

		assertTrue(seed.validationRunAvailable());
		assertTrue(seed.pressureEvidenceAvailable());
		assertTrue(seed.velocityEvidenceAvailable());
		assertTrue(seed.transitEvidenceAvailable());
		assertTrue(seed.momentumEvidenceAvailable());
		assertTrue(seed.validationSeedReady());
		assertTrue(seed.pressureValidationPassed());
		assertTrue(seed.velocityValidationPassed());
		assertTrue(seed.arrivalTimeValidationPassed());
		assertTrue(seed.momentumValidationPassed());
		assertTrue(seed.validationPassed());
		assertEquals("READY_PASS", seed.status());
		assertEquals("cruise-skew-wake-validation-seed-passed", seed.message());
		assertEquals(0.0, seed.pressureErrorRatio(), 1.0e-12);
		assertEquals(0.0, seed.wakeVelocityErrorRatio(), 1.0e-12);
		assertEquals(0.0, seed.arrivalWindowErrorRatio(), 1.0e-12);
		assertEquals(0.0, seed.momentumErrorRatio(), 1.0e-12);
	}

	@Test
	void offTargetMomentumCreatesFailingSeed() {
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run =
				liveRun(currentRun("racingQuad", 1, 0, 0), 1.0, 1.0, 0.0, 0.50);

		Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.seed(run);

		assertTrue(seed.validationSeedReady());
		assertTrue(seed.pressureValidationPassed());
		assertTrue(seed.velocityValidationPassed());
		assertTrue(seed.arrivalTimeValidationPassed());
		assertFalse(seed.momentumValidationPassed());
		assertFalse(seed.validationPassed());
		assertEquals("READY_FAIL", seed.status());
		assertEquals("cruise-skew-wake-validation-seed-failed", seed.message());
		assertTrue(seed.momentumErrorRatio()
				> Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.MOMENTUM_RELATIVE_TOLERANCE);
	}

	@Test
	void centerlineArrivalUsesFiniteTolerance() {
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun passingRun =
				liveRun(currentRun("racingQuad", 1, 0, 0), 1.0, 1.0, 0.05, 1.0);
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun failingRun =
				liveRun(currentRun("racingQuad", 1, 0, 0), 1.0, 1.0, 0.25, 1.0);

		Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed passing =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.seed(passingRun);
		Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed failing =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.seed(failingRun);

		assertEquals(0.0, passing.targetArrivalBandSeconds(), 1.0e-12);
		assertTrue(passing.arrivalTimeValidationPassed());
		assertFalse(failing.arrivalTimeValidationPassed());
		assertFalse(failing.validationPassed());
	}

	@Test
	void missingMomentumEvidenceKeepsSeedUnavailable() {
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run =
				liveRunWithoutMomentum(currentRun("racingQuad", 1, 0, 0));

		Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.seed(run);

		assertTrue(seed.validationRunAvailable());
		assertTrue(seed.pressureEvidenceAvailable());
		assertTrue(seed.velocityEvidenceAvailable());
		assertTrue(seed.transitEvidenceAvailable());
		assertFalse(seed.momentumEvidenceAvailable());
		assertFalse(seed.validationSeedReady());
		assertEquals("UNAVAILABLE", seed.status());
		assertEquals("momentum-evidence-missing", seed.message());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.seed(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeResultSeedAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_result_seed_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_result_seed_summary,all_seeds,validation_seed_count,192,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_result_seed_summary,all_seeds,validation_seed_ready_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_result_seed,racingQuad:plane1:sweep0:rotor0,status,UNAVAILABLE,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_result_seed,racingQuad:plane1:sweep0:rotor0,message,cruise-skew-wake-run-unavailable,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun currentRun(
			String presetName,
			int axialPlaneIndex,
			int sweepColumnIndex,
			int rotorIndex
	) {
		return Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit().runs().stream()
				.filter(run -> presetName.equals(run.presetName())
						&& axialPlaneIndex == run.axialPlaneIndex()
						&& sweepColumnIndex == run.sweepColumnIndex()
						&& rotorIndex == run.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun liveRun(
			Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run,
			double pressureScale,
			double velocityScale,
			double arrivalCenterlineOffsetFraction,
			double momentumScale
	) {
		double observedArrival = run.centerlineResultantTransitSeconds()
				+ run.centerlineResultantTransitSeconds() * arrivalCenterlineOffsetFraction;
		if (run.lateralAdjustedTransitSeconds() > run.centerlineResultantTransitSeconds()
				&& arrivalCenterlineOffsetFraction == 0.0) {
			observedArrival = 0.5
					* (run.centerlineResultantTransitSeconds() + run.lateralAdjustedTransitSeconds());
		}
		return copyLive(run,
				run.expectedResultantDynamicPressurePascals() * pressureScale,
				run.expectedResultantWakeVelocityMetersPerSecond() * velocityScale,
				observedArrival,
				run.perRotorAxialMomentumFluxNewtons() * momentumScale,
				true,
				true,
				true,
				true);
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun liveRunWithoutMomentum(
			Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run
	) {
		return copyLive(run,
				run.expectedResultantDynamicPressurePascals(),
				run.expectedResultantWakeVelocityMetersPerSecond(),
				run.centerlineResultantTransitSeconds(),
				0.0,
				true,
				true,
				true,
				false);
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun copyLive(
			Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run,
			double observedPressurePascals,
			double observedWakeVelocityMetersPerSecond,
			double observedArrivalTimeSeconds,
			double observedMomentumFluxNewtons,
			boolean hasPressureEvidence,
			boolean hasVelocityEvidence,
			boolean hasTransitEvidence,
			boolean hasMomentumEvidence
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun(
				run.presetName(),
				run.spinState(),
				run.rotorIndex(),
				run.axialPlaneIndex(),
				run.sweepColumnIndex(),
				run.axialPlaneFraction(),
				run.axialDistanceMeters(),
				run.freestreamSweepDistanceMeters(),
				run.lateralOffsetMeters(),
				run.centerlineDistanceMeters(),
				run.distanceFromRotorMeters(),
				run.expectedAxialWakeVelocityMetersPerSecond(),
				run.expectedFreestreamVelocityMetersPerSecond(),
				run.expectedResultantWakeVelocityMetersPerSecond(),
				run.centerlineResultantTransitSeconds(),
				run.lateralAdjustedTransitSeconds(),
				run.requiredMaxSamplePeriodSeconds(),
				run.requiredMaxSamplePeriodSeconds(),
				run.requiredSubstepsPerMinecraftTick(),
				run.requiredSubstepsPerOneKilohertzFrame(),
				run.expectedAxialWakePressurePascals(),
				run.expectedResultantDynamicPressurePascals(),
				run.perRotorAxialMomentumFluxNewtons(),
				true,
				true,
				true,
				true,
				true,
				true,
				true,
				run.requestPressureProbe(),
				run.requestVelocityProbe(),
				run.requestTransientSeries(),
				run.requestMomentumClosure(),
				true,
				true,
				true,
				hasPressureEvidence,
				hasVelocityEvidence,
				hasTransitEvidence,
				hasMomentumEvidence,
				observedPressurePascals,
				observedWakeVelocityMetersPerSecond,
				observedArrivalTimeSeconds,
				observedMomentumFluxNewtons,
				0.0,
				0.0,
				0.0,
				0.0,
				"OK",
				"live-cruise-skew-wake-sample",
				"live-runtime"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_result_seed_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
