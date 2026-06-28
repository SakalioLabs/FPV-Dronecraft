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

class Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrixTest {
	@Test
	void auditBuildsCurrentSkippedSurfaceWakeValidationRunMatrix() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Validation-Run-Matrix-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("current rows stay skipped"));
		assertEquals(5271, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(128, audit.runSampleCount());
		assertEquals(41, audit.runMetricCount());
		assertEquals(15, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(128, audit.runs().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run
				: audit.runs()) {
			assertEquals("hover", run.spinState());
			assertTrue("ground".equals(run.surfaceType()) || "ceiling".equals(run.surfaceType()));
			assertTrue(run.rotorIndex() >= 0);
			assertTrue(run.rotorIndex() < 4);
			assertTrue(run.clearanceOverRadius() == 0.5
					|| run.clearanceOverRadius() == 1.0
					|| run.clearanceOverRadius() == 2.0
					|| run.clearanceOverRadius() == 4.0);
			assertTrue(run.clearanceMeters() > 0.0);
			assertTrue(run.rotorDiskRadiusMeters() > 0.0);
			assertEquals(2.0 * run.idealInducedVelocityMetersPerSecond(),
					run.farWakeVelocityMetersPerSecond(), 1.0e-12);
			assertTrue(run.fastFarWakeTransitSeconds() > 0.0);
			assertTrue(run.meanWakeTransitSeconds() > run.fastFarWakeTransitSeconds());
			assertTrue(run.slowDiskPlaneTransitSeconds() > run.meanWakeTransitSeconds());
			assertTrue(run.requiredMaxSamplePeriodSeconds() > 0.0);
			assertEquals(0.05, run.appliedMaxSamplePeriodSeconds(), 1.0e-12);
			assertTrue(run.requiredSubstepsPerMinecraftTick() > 0);
			assertTrue(run.perRotorImpingementPressurePascals() > 0.0);
			assertTrue(run.surfaceCurveMultiplier() >= 1.0);
			assertTrue(run.perRotorSurfaceCushionForceNewtons() >= 0.0);
			assertFalse(run.poweredSourceApiAvailable());
			assertFalse(run.transientProbeApiAvailable());
			assertFalse(run.targetTransientProbeApiAvailable());
			assertFalse(run.readinessGateOpen());
			assertFalse(run.validationRunAllowed());
			assertTrue(run.requestPressureProbe());
			assertTrue(run.requestVelocityProbe());
			assertTrue(run.requestTransientSeries());
			assertFalse(run.invoked());
			assertFalse(run.succeeded());
			assertFalse(run.available());
			assertFalse(run.hasPressureEvidence());
			assertFalse(run.hasVelocityEvidence());
			assertFalse(run.hasTransitEvidence());
			assertEquals(0.0, run.observedPressurePascals(), 1.0e-12);
			assertEquals(0.0, run.observedWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, run.observedArrivalTimeSeconds(), 1.0e-12);
			assertEquals(0.0, run.pressureErrorRatio(), 1.0e-12);
			assertEquals(0.0, run.velocityErrorRatio(), 1.0e-12);
			assertEquals(0.0, run.arrivalTimeErrorRatio(), 1.0e-12);
			assertEquals("SKIPPED", run.status());
			assertEquals("surface-wake-readiness-gate-blocked", run.message());
			assertEquals("audit-only-unvalidated-surface-wake-validation-run", run.runtimeInfo());
		}

		assertEquals(128, audit.extrema().runCount());
		assertEquals(64, audit.extrema().groundRunCount());
		assertEquals(64, audit.extrema().ceilingRunCount());
		assertEquals(0, audit.extrema().readinessGateOpenCount());
		assertEquals(0, audit.extrema().validationRunAllowedCount());
		assertEquals(0, audit.extrema().targetTransientProbeApiAvailableCount());
		assertEquals(0, audit.extrema().invokedCount());
		assertEquals(0, audit.extrema().availableCount());
		assertEquals(0, audit.extrema().pressureEvidenceCount());
		assertEquals(0, audit.extrema().velocityEvidenceCount());
		assertEquals(0, audit.extrema().transitEvidenceCount());
		assertEquals(128, audit.extrema().skippedForReadinessCount());
		assertEquals(0, audit.extrema().pendingExecutorCount());
		assertTrue(audit.extrema().maxRequiredSubstepsPerMinecraftTick() > 0);
		assertTrue(audit.extrema().maxRequiredSampleRateHertz() > 0.0);
	}

	@Test
	void readyTargetsBecomePendingWithoutFakeProbeEvidence() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> readyTemporal =
				readyTemporalTargets();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> transit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit().targets();
		double requiredMaxSamplePeriod = requiredMaxSamplePeriod(readyTemporal);

		Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.audit(
						true,
						true,
						requiredMaxSamplePeriod,
						readyTemporal,
						transit
				);

		assertEquals(128, audit.runs().size());
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run
				: audit.runs()) {
			assertTrue(run.poweredSourceApiAvailable());
			assertTrue(run.transientProbeApiAvailable());
			assertTrue(run.targetTransientProbeApiAvailable());
			assertTrue(run.readinessGateOpen());
			assertTrue(run.validationRunAllowed());
			assertEquals(requiredMaxSamplePeriod, run.appliedMaxSamplePeriodSeconds(), 1.0e-16);
			assertEquals("PENDING", run.status());
			assertEquals("surface-wake-validator-not-invoked", run.message());
			assertFalse(run.invoked());
			assertFalse(run.succeeded());
			assertFalse(run.available());
			assertFalse(run.hasPressureEvidence());
			assertFalse(run.hasVelocityEvidence());
			assertFalse(run.hasTransitEvidence());
			assertEquals(0.0, run.observedPressurePascals(), 1.0e-12);
			assertEquals(0.0, run.observedWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(0.0, run.observedArrivalTimeSeconds(), 1.0e-12);
		}
		assertEquals(128, audit.extrema().readinessGateOpenCount());
		assertEquals(128, audit.extrema().validationRunAllowedCount());
		assertEquals(128, audit.extrema().targetTransientProbeApiAvailableCount());
		assertEquals(0, audit.extrema().skippedForReadinessCount());
		assertEquals(128, audit.extrema().pendingExecutorCount());
	}

	@Test
	void runMatchesTransitAndTemporalTargets() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget transit =
				findTransit(Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit().targets(),
						"racingQuad", "ground", 1.0, 0);
		Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget temporal =
				findTemporal(readyTemporalTargets(), "racingQuad", "ground", 1.0, 0);
		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessSummary readiness =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.gate(
						true,
						true,
						requiredMaxSamplePeriod(readyTemporalTargets()),
						readyTemporalTargets()
				);
		Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run =
				Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.run(transit, temporal, readiness);

		assertEquals(transit.farWakeVelocityMetersPerSecond(), run.farWakeVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(transit.perRotorImpingementPressurePascals(),
				run.perRotorImpingementPressurePascals(), 1.0e-12);
		assertEquals(transit.surfaceCurveMultiplier(), run.surfaceCurveMultiplier(), 1.0e-12);
		assertEquals(transit.perRotorSurfaceCushionForceNewtons(),
				run.perRotorSurfaceCushionForceNewtons(), 1.0e-12);
		assertEquals(temporal.recommendedMaxSamplePeriodSeconds(),
				run.requiredMaxSamplePeriodSeconds(), 1.0e-12);
		assertEquals(temporal.requiredSubstepsPerMinecraftTick(), run.requiredSubstepsPerMinecraftTick());
		assertEquals("PENDING", run.status());
	}

	@Test
	void rejectsInvalidInputsAndMismatchedTargets() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> readyTemporal =
				readyTemporalTargets();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> transit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit().targets();
		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessSummary readiness =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.gate(
						true,
						true,
						requiredMaxSamplePeriod(readyTemporal),
						readyTemporal
				);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.audit(
						true, true, 0.001, null, transit));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.audit(
						true, true, 0.001, readyTemporal, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.run(null, readyTemporal.get(0), readiness));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.run(transit.get(0), null, readiness));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.run(transit.get(0), readyTemporal.get(1), readiness));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRunMatrixAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_validation_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_validation_run_summary,all_runs,run_count,128,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_validation_run_summary,all_runs,skipped_for_readiness_count,128,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_validation_run,racingQuad:ground:hR1.0:rotor0,status,SKIPPED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_validation_run,racingQuad:ground:hR1.0:rotor0,message,surface-wake-readiness-gate-blocked,")));
	}

	private static List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> readyTemporalTargets() {
		return Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.audit().targets().stream()
				.map(target -> copy(target, true, false))
				.toList();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget copy(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget(
				target.presetName(),
				target.spinState(),
				target.surfaceType(),
				target.rotorIndex(),
				target.clearanceOverRadius(),
				target.clearanceMeters(),
				target.fastFarWakeTransitSeconds(),
				target.meanWakeTransitSeconds(),
				target.slowDiskPlaneTransitSeconds(),
				target.transitBandSeconds(),
				target.configuredDynamicInflowTauSeconds(),
				target.tauOverFastTransit(),
				target.minimumSamplesPerFastTransit(),
				target.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMinSampleRateHertz(),
				target.minecraftTickRateHertz(),
				target.minecraftTickSamplesPerFastTransit(),
				target.minecraftTickResolvesFastTransit(),
				target.requiredSubstepsPerMinecraftTick(),
				target.sixtyHertzSamplesPerFastTransit(),
				target.sixtyHertzResolvesFastTransit(),
				target.requiredSubstepsPerSixtyHertzFrame(),
				target.oneTwentyHertzSamplesPerFastTransit(),
				target.oneTwentyHertzResolvesFastTransit(),
				target.requiredSubstepsPerOneTwentyHertzFrame(),
				target.twoFortyHertzSamplesPerFastTransit(),
				target.twoFortyHertzResolvesFastTransit(),
				target.requiredSubstepsPerTwoFortyHertzFrame(),
				target.oneKilohertzSamplesPerFastTransit(),
				target.oneKilohertzResolvesFastTransit(),
				target.requiredSubstepsPerOneKilohertzFrame(),
				transientProbeApiAvailable,
				runtimeCouplingAllowed,
				target.validationBeforeRuntimeRequired(),
				target.status(),
				target.runtimeInfo()
		);
	}

	private static double requiredMaxSamplePeriod(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> targets
	) {
		return targets.stream()
				.mapToDouble(Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget::recommendedMaxSamplePeriodSeconds)
				.min()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget findTransit(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> targets,
			String presetName,
			String surfaceType,
			double clearanceOverRadius,
			int rotorIndex
	) {
		return targets.stream()
				.filter(target -> presetName.equals(target.presetName())
						&& surfaceType.equals(target.surfaceType())
						&& Math.abs(clearanceOverRadius - target.clearanceOverRadius()) <= 1.0e-12
						&& rotorIndex == target.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget findTemporal(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> targets,
			String presetName,
			String surfaceType,
			double clearanceOverRadius,
			int rotorIndex
	) {
		return targets.stream()
				.filter(target -> presetName.equals(target.presetName())
						&& surfaceType.equals(target.surfaceType())
						&& Math.abs(clearanceOverRadius - target.clearanceOverRadius()) <= 1.0e-12
						&& rotorIndex == target.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_hover_surface_wake_validation_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
