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

class Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrixTest {
	@Test
	void auditBuildsCurrentSkippedCruiseSkewWakeValidationRunMatrix() {
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Validation-Run-Matrix-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("current rows stay skipped"));
		assertEquals(10010, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(192, audit.runSampleCount());
		assertEquals(52, audit.runMetricCount());
		assertEquals(18, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(192, audit.runs().size());

		for (Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run
				: audit.runs()) {
			assertEquals("cruise", run.spinState());
			assertTrue(run.rotorIndex() >= 0);
			assertTrue(run.rotorIndex() < 4);
			assertTrue(run.axialPlaneIndex() >= 1);
			assertTrue(run.axialPlaneIndex() <= 4);
			assertTrue(run.sweepColumnIndex() == -1 || run.sweepColumnIndex() == 0 || run.sweepColumnIndex() == 1);
			assertTrue(run.axialPlaneFraction() > 0.0);
			assertTrue(run.axialDistanceMeters() > 0.0);
			assertTrue(run.centerlineDistanceMeters() > 0.0);
			assertTrue(run.distanceFromRotorMeters() >= run.centerlineDistanceMeters());
			assertTrue(run.expectedAxialWakeVelocityMetersPerSecond() > 0.0);
			assertTrue(run.expectedFreestreamVelocityMetersPerSecond() > 0.0);
			assertTrue(run.expectedResultantWakeVelocityMetersPerSecond() > 0.0);
			assertTrue(run.centerlineResultantTransitSeconds() > 0.0);
			assertTrue(run.lateralAdjustedTransitSeconds() >= run.centerlineResultantTransitSeconds());
			assertTrue(run.requiredMaxSamplePeriodSeconds() > 0.0);
			assertEquals(0.05, run.appliedMaxSamplePeriodSeconds(), 1.0e-12);
			assertTrue(run.requiredSubstepsPerMinecraftTick() > 0);
			assertTrue(run.requiredSubstepsPerOneKilohertzFrame() > 0);
			assertTrue(run.expectedAxialWakePressurePascals() > 0.0);
			assertTrue(run.expectedResultantDynamicPressurePascals() > 0.0);
			assertTrue(run.perRotorAxialMomentumFluxNewtons() > 0.0);
			assertFalse(run.poweredSourceApiAvailable());
			assertFalse(run.skewWakeProbeApiAvailable());
			assertFalse(run.transientProbeApiAvailable());
			assertFalse(run.targetSkewWakeProbeApiAvailable());
			assertFalse(run.targetTransientProbeApiAvailable());
			assertFalse(run.readinessGateOpen());
			assertFalse(run.validationRunAllowed());
			assertTrue(run.requestPressureProbe());
			assertTrue(run.requestVelocityProbe());
			assertTrue(run.requestTransientSeries());
			assertTrue(run.requestMomentumClosure());
			assertFalse(run.invoked());
			assertFalse(run.succeeded());
			assertFalse(run.available());
			assertFalse(run.hasPressureEvidence());
			assertFalse(run.hasVelocityEvidence());
			assertFalse(run.hasTransitEvidence());
			assertFalse(run.hasMomentumEvidence());
			assertEquals(0.0, run.observedPressurePascals(), 1.0e-12);
			assertEquals(0.0, run.observedWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, run.observedArrivalTimeSeconds(), 1.0e-12);
			assertEquals(0.0, run.observedMomentumFluxNewtons(), 1.0e-12);
			assertEquals(0.0, run.pressureErrorRatio(), 1.0e-12);
			assertEquals(0.0, run.velocityErrorRatio(), 1.0e-12);
			assertEquals(0.0, run.arrivalTimeErrorRatio(), 1.0e-12);
			assertEquals(0.0, run.momentumErrorRatio(), 1.0e-12);
			assertEquals("SKIPPED", run.status());
			assertEquals("cruise-skew-wake-readiness-gate-blocked", run.message());
			assertEquals("audit-only-unvalidated-cruise-skew-wake-validation-run", run.runtimeInfo());
		}

		assertEquals(192, audit.extrema().runCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(64, audit.extrema().centerlineSweepRunCount());
		assertEquals(128, audit.extrema().lateralSweepRunCount());
		assertEquals(0, audit.extrema().readinessGateOpenCount());
		assertEquals(0, audit.extrema().validationRunAllowedCount());
		assertEquals(0, audit.extrema().targetSkewWakeProbeApiAvailableCount());
		assertEquals(0, audit.extrema().targetTransientProbeApiAvailableCount());
		assertEquals(0, audit.extrema().invokedCount());
		assertEquals(0, audit.extrema().availableCount());
		assertEquals(0, audit.extrema().pressureEvidenceCount());
		assertEquals(0, audit.extrema().velocityEvidenceCount());
		assertEquals(0, audit.extrema().transitEvidenceCount());
		assertEquals(0, audit.extrema().momentumEvidenceCount());
		assertEquals(192, audit.extrema().skippedForReadinessCount());
		assertEquals(0, audit.extrema().pendingExecutorCount());
		assertTrue(audit.extrema().maxRequiredSubstepsPerMinecraftTick() > 0);
		assertTrue(audit.extrema().maxRequiredSampleRateHertz() > 0.0);
	}

	@Test
	void readyTargetsBecomePendingWithoutFakeProbeEvidence() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTemporal =
				readyTemporalTargets();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> readyTransit =
				readyTransitTargets();
		double requiredMaxSamplePeriod = requiredMaxSamplePeriod(readyTemporal);

		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit(
						true,
						true,
						true,
						requiredMaxSamplePeriod,
						readyTemporal,
						readyTransit
				);

		assertEquals(192, audit.runs().size());
		for (Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run
				: audit.runs()) {
			assertTrue(run.poweredSourceApiAvailable());
			assertTrue(run.skewWakeProbeApiAvailable());
			assertTrue(run.transientProbeApiAvailable());
			assertTrue(run.targetSkewWakeProbeApiAvailable());
			assertTrue(run.targetTransientProbeApiAvailable());
			assertTrue(run.readinessGateOpen());
			assertTrue(run.validationRunAllowed());
			assertEquals(requiredMaxSamplePeriod, run.appliedMaxSamplePeriodSeconds(), 1.0e-16);
			assertEquals("PENDING", run.status());
			assertEquals("cruise-skew-wake-validator-not-invoked", run.message());
			assertFalse(run.invoked());
			assertFalse(run.succeeded());
			assertFalse(run.available());
			assertFalse(run.hasPressureEvidence());
			assertFalse(run.hasVelocityEvidence());
			assertFalse(run.hasTransitEvidence());
			assertFalse(run.hasMomentumEvidence());
			assertEquals(0.0, run.observedPressurePascals(), 1.0e-12);
			assertEquals(0.0, run.observedWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, run.observedArrivalTimeSeconds(), 1.0e-12);
			assertEquals(0.0, run.observedMomentumFluxNewtons(), 1.0e-12);
		}
		assertEquals(192, audit.extrema().readinessGateOpenCount());
		assertEquals(192, audit.extrema().validationRunAllowedCount());
		assertEquals(192, audit.extrema().targetSkewWakeProbeApiAvailableCount());
		assertEquals(192, audit.extrema().targetTransientProbeApiAvailableCount());
		assertEquals(0, audit.extrema().skippedForReadinessCount());
		assertEquals(192, audit.extrema().pendingExecutorCount());
	}

	@Test
	void openReadinessStillRequiresTargetSkewWakeProbeAvailability() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTemporal =
				readyTemporalTargets();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> currentTransit =
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.audit().targets();
		double requiredMaxSamplePeriod = requiredMaxSamplePeriod(readyTemporal);

		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit(
						true,
						true,
						true,
						requiredMaxSamplePeriod,
						readyTemporal,
						currentTransit
				);

		assertEquals(192, audit.extrema().readinessGateOpenCount());
		assertEquals(0, audit.extrema().validationRunAllowedCount());
		assertEquals(0, audit.extrema().targetSkewWakeProbeApiAvailableCount());
		assertEquals(0, audit.extrema().pendingExecutorCount());
		assertTrue(audit.runs().stream()
				.allMatch(run -> "skew-wake-probe-target-unavailable".equals(run.message())));
	}

	@Test
	void runMatchesTransitAndTemporalTargets() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTemporal =
				readyTemporalTargets();
		Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit =
				findTransit(readyTransitTargets(), "racingQuad", 1, 0, 0);
		Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget temporal =
				findTemporal(readyTemporal, "racingQuad", 1, 0, 0);
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary readiness =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true,
						true,
						true,
						requiredMaxSamplePeriod(readyTemporal),
						readyTemporal
				);
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run =
				Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.run(transit, temporal, readiness);

		assertEquals(transit.expectedResultantWakeVelocityMetersPerSecond(),
				run.expectedResultantWakeVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(transit.expectedResultantDynamicPressurePascals(),
				run.expectedResultantDynamicPressurePascals(), 1.0e-12);
		assertEquals(transit.perRotorAxialMomentumFluxNewtons(),
				run.perRotorAxialMomentumFluxNewtons(), 1.0e-12);
		assertEquals(transit.centerlineResultantTransitSeconds(),
				run.centerlineResultantTransitSeconds(), 1.0e-12);
		assertEquals(temporal.recommendedMaxSamplePeriodSeconds(),
				run.requiredMaxSamplePeriodSeconds(), 1.0e-12);
		assertEquals(temporal.requiredSubstepsPerMinecraftTick(), run.requiredSubstepsPerMinecraftTick());
		assertEquals(temporal.requiredSubstepsPerOneKilohertzFrame(), run.requiredSubstepsPerOneKilohertzFrame());
		assertEquals("PENDING", run.status());
	}

	@Test
	void rejectsInvalidInputsAndMismatchedTargets() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTemporal =
				readyTemporalTargets();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> readyTransit =
				readyTransitTargets();
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary readiness =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true,
						true,
						true,
						requiredMaxSamplePeriod(readyTemporal),
						readyTemporal
				);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit(
						true, true, true, 0.001, null, readyTransit));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit(
						true, true, true, 0.001, readyTemporal, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit(
						true, true, true, 0.001, readyTemporal,
						readyTransit.subList(0, readyTransit.size() - 1)));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.run(
						null, readyTemporal.get(0), readiness));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.run(
						readyTransit.get(0), null, readiness));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.run(
						readyTransit.get(0), readyTemporal.get(1), readiness));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_validation_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_validation_run_summary,all_runs,run_count,192,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_validation_run_summary,all_runs,skipped_for_readiness_count,192,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_validation_run,racingQuad:plane1:sweep0:rotor0,status,SKIPPED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_validation_run,racingQuad:plane1:sweep0:rotor0,message,cruise-skew-wake-readiness-gate-blocked,")));
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTemporalTargets() {
		return Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.audit().targets().stream()
				.map(target -> copy(target, true, false))
				.toList();
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> readyTransitTargets() {
		return Aerodynamics4McL2PoweredCruiseSkewWakeTransit.audit().targets().stream()
				.map(target -> copy(target, true, true, false))
				.toList();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget copy(
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget(
				target.presetName(),
				target.spinState(),
				target.rotorIndex(),
				target.axialPlaneIndex(),
				target.sweepColumnIndex(),
				target.axialPlaneFraction(),
				target.centerlineResultantTransitSeconds(),
				target.lateralAdjustedTransitSeconds(),
				target.transitBandSeconds(),
				target.configuredDynamicInflowTauSeconds(),
				target.tauOverCenterlineTransit(),
				target.tauOverLateralTransit(),
				target.minimumSamplesPerFastTransit(),
				target.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMinSampleRateHertz(),
				target.minecraftTickRateHertz(),
				target.minecraftTickSamplesPerCenterlineTransit(),
				target.minecraftTickResolvesCenterlineTransit(),
				target.requiredSubstepsPerMinecraftTick(),
				target.sixtyHertzSamplesPerCenterlineTransit(),
				target.sixtyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerSixtyHertzFrame(),
				target.oneTwentyHertzSamplesPerCenterlineTransit(),
				target.oneTwentyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerOneTwentyHertzFrame(),
				target.twoFortyHertzSamplesPerCenterlineTransit(),
				target.twoFortyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerTwoFortyHertzFrame(),
				target.oneKilohertzSamplesPerCenterlineTransit(),
				target.oneKilohertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerOneKilohertzFrame(),
				target.oneKilohertzSamplesPerLateralAdjustedTransit(),
				target.oneKilohertzResolvesLateralAdjustedTransit(),
				transientProbeApiAvailable,
				runtimeCouplingAllowed,
				target.validationBeforeRuntimeRequired(),
				target.status(),
				target.runtimeInfo()
		);
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget copy(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget target,
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget(
				target.presetName(),
				target.spinState(),
				target.rotorIndex(),
				target.axialPlaneIndex(),
				target.sweepColumnIndex(),
				target.axialPlaneFraction(),
				target.axialDistanceMeters(),
				target.freestreamSweepDistanceMeters(),
				target.lateralOffsetMeters(),
				target.centerlineDistanceMeters(),
				target.distanceFromRotorMeters(),
				target.expectedAxialWakeVelocityMetersPerSecond(),
				target.expectedFreestreamVelocityMetersPerSecond(),
				target.expectedResultantWakeVelocityMetersPerSecond(),
				target.axialOnlyTransitSeconds(),
				target.freestreamSweepTransitSeconds(),
				target.centerlineResultantTransitSeconds(),
				target.lateralAdjustedTransitSeconds(),
				target.transitBandSeconds(),
				target.axialTransitMilliseconds(),
				target.centerlineTransitMilliseconds(),
				target.lateralTransitMilliseconds(),
				target.configuredDynamicInflowTauSeconds(),
				target.tauOverCenterlineTransit(),
				target.tauOverLateralTransit(),
				target.centerlineTransitOverConfiguredTau(),
				target.expectedAxialWakePressurePascals(),
				target.expectedResultantDynamicPressurePascals(),
				target.perRotorAxialMomentumFluxNewtons(),
				target.minimumSamplesPerFastTransit(),
				target.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMinSampleRateHertz(),
				skewWakeProbeApiAvailable,
				transientProbeApiAvailable,
				runtimeCouplingAllowed,
				target.validationBeforeRuntimeRequired(),
				target.status(),
				target.runtimeInfo()
		);
	}

	private static double requiredMaxSamplePeriod(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> targets
	) {
		return targets.stream()
				.mapToDouble(Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget::recommendedMaxSamplePeriodSeconds)
				.min()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget findTransit(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> targets,
			String presetName,
			int axialPlaneIndex,
			int sweepColumnIndex,
			int rotorIndex
	) {
		return targets.stream()
				.filter(target -> presetName.equals(target.presetName())
						&& axialPlaneIndex == target.axialPlaneIndex()
						&& sweepColumnIndex == target.sweepColumnIndex()
						&& rotorIndex == target.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget findTemporal(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> targets,
			String presetName,
			int axialPlaneIndex,
			int sweepColumnIndex,
			int rotorIndex
	) {
		return targets.stream()
				.filter(target -> presetName.equals(target.presetName())
						&& axialPlaneIndex == target.axialPlaneIndex()
						&& sweepColumnIndex == target.sweepColumnIndex()
						&& rotorIndex == target.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_validation_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
