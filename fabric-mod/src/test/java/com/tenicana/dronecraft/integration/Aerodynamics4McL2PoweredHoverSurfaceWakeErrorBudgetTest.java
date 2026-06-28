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

class Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudgetTest {
	@Test
	void auditBuildsCurrentBlockedSurfaceWakeErrorBudget() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Error-Budget-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not provide gameplay"));
		assertEquals(920, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(32, audit.groupSampleCount());
		assertEquals(4, audit.expectedRotorSeedCount());
		assertEquals(28, audit.groupMetricCount());
		assertEquals(16, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(32, audit.groups().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup group
				: audit.groups()) {
			assertEquals("hover", group.spinState());
			assertTrue("ground".equals(group.surfaceType()) || "ceiling".equals(group.surfaceType()));
			assertTrue(group.clearanceOverRadius() == 0.5
					|| group.clearanceOverRadius() == 1.0
					|| group.clearanceOverRadius() == 2.0
					|| group.clearanceOverRadius() == 4.0);
			assertTrue(group.clearanceMeters() > 0.0);
			assertEquals(4, group.expectedRotorSeedCount());
			assertEquals(4, group.observedRotorSeedCount());
			assertEquals(0, group.missingRotorSeedCount());
			assertEquals(0, group.unexpectedRotorSeedCount());
			assertEquals(0, group.readyRotorSeedCount());
			assertEquals(0, group.passedRotorSeedCount());
			assertEquals(0, group.failedRotorSeedCount());
			assertEquals(4, group.unavailableRotorSeedCount());
			assertTrue(group.allRotorSeedsPresent());
			assertFalse(group.allRotorSeedsReady());
			assertFalse(group.allRotorSeedsPassed());
			assertTrue(group.meanTargetPressurePascals() > 0.0);
			assertEquals(0.0, group.maxPressureErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.meanPressureErrorRatio(), 1.0e-12);
			assertTrue(group.meanTargetWakeVelocityMetersPerSecond() > 0.0);
			assertEquals(0.0, group.maxWakeVelocityErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.meanWakeVelocityErrorRatio(), 1.0e-12);
			assertTrue(group.meanArrivalBandSeconds() > 0.0);
			assertEquals(0.0, group.maxArrivalBandErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.meanArrivalBandErrorRatio(), 1.0e-12);
			assertFalse(group.validationCandidate());
			assertEquals("BLOCKED", group.status());
			assertEquals("rotor-seeds-not-ready", group.message());
		}

		assertEquals(32, audit.extrema().groupCount());
		assertEquals(16, audit.extrema().groundGroupCount());
		assertEquals(16, audit.extrema().ceilingGroupCount());
		assertEquals(32, audit.extrema().completeGroupCount());
		assertEquals(0, audit.extrema().readyGroupCount());
		assertEquals(0, audit.extrema().passedGroupCount());
		assertEquals(0, audit.extrema().validationCandidateCount());
		assertEquals(32, audit.extrema().blockedGroupCount());
		assertEquals(0, audit.extrema().maxMissingRotorSeedCount());
		assertEquals(0, audit.extrema().maxUnexpectedRotorSeedCount());
		assertEquals(4, audit.extrema().maxUnavailableRotorSeedCount());
		assertEquals(0.0, audit.extrema().maxPressureErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxWakeVelocityErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxArrivalBandErrorRatio(), 1.0e-12);
		assertTrue(audit.extrema().maxMeanTargetPressurePascals() > 0.0);
		assertTrue(audit.extrema().maxMeanTargetWakeVelocityMetersPerSecond() > 0.0);
	}

	@Test
	void groupAcceptsOnlyCompleteReadyPassingRotorSeeds() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> current =
				currentGroup("racingQuad", "ground", 1.0);
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> passing =
				current.stream()
						.map(Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudgetTest::passingSeed)
						.toList();

		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup ready =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"racingQuad", "hover", "ground", 1.0, passing);

		assertEquals(4, ready.observedRotorSeedCount());
		assertEquals(4, ready.readyRotorSeedCount());
		assertEquals(4, ready.passedRotorSeedCount());
		assertTrue(ready.allRotorSeedsPresent());
		assertTrue(ready.allRotorSeedsReady());
		assertTrue(ready.allRotorSeedsPassed());
		assertTrue(ready.validationCandidate());
		assertEquals("READY", ready.status());
		assertEquals("surface-wake-error-budget-ready", ready.message());
	}

	@Test
	void groupRejectsMissingAndFailedRotorEvidence() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> passing =
				currentGroup("racingQuad", "ground", 1.0).stream()
						.map(Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudgetTest::passingSeed)
						.toList();

		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup missing =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"racingQuad", "hover", "ground", 1.0, passing.subList(0, 3));
		assertEquals(1, missing.missingRotorSeedCount());
		assertFalse(missing.allRotorSeedsPresent());
		assertFalse(missing.validationCandidate());
		assertEquals("rotor-seed-set-incomplete", missing.message());

		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> failed =
				new java.util.ArrayList<>(passing);
		failed.set(0, failingSeed(passing.get(0)));
		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup failedGroup =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"racingQuad", "hover", "ground", 1.0, failed);
		assertEquals(1, failedGroup.failedRotorSeedCount());
		assertTrue(failedGroup.allRotorSeedsReady());
		assertFalse(failedGroup.allRotorSeedsPassed());
		assertFalse(failedGroup.validationCandidate());
		assertEquals("rotor-seeds-failed", failedGroup.message());
		assertTrue(failedGroup.maxPressureErrorRatio()
				> Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PRESSURE_RELATIVE_TOLERANCE);
	}

	@Test
	void rejectsInvalidInputsAndDuplicateRotorSeeds() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> current =
				currentGroup("racingQuad", "ground", 1.0);
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> duplicate =
				new java.util.ArrayList<>(current);
		duplicate.set(1, current.get(0));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"", "hover", "ground", 1.0, current));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"racingQuad", "cruise", "ground", 1.0, current));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"racingQuad", "hover", "wall", 1.0, current));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"racingQuad", "hover", "ground", 1.0, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.group(
						"racingQuad", "hover", "ground", 1.0, duplicate));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_error_budget_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_error_budget_summary,all_groups,group_count,32,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_error_budget_summary,all_groups,validation_candidate_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_error_budget,racingQuad:ground:hR1.0,unavailable_rotor_seed_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_error_budget,racingQuad:ground:hR1.0,message,rotor-seeds-not-ready,")));
	}

	private static List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> currentGroup(
			String presetName,
			String surfaceType,
			double clearanceOverRadius
	) {
		return Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit().seeds().stream()
				.filter(seed -> presetName.equals(seed.presetName())
						&& surfaceType.equals(seed.surfaceType())
						&& Math.abs(clearanceOverRadius - seed.clearanceOverRadius()) <= 1.0e-12)
				.toList();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed passingSeed(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		return copy(seed,
				true,
				seed.targetPressurePascals(),
				seed.targetWakeVelocityMetersPerSecond(),
				seed.targetFastArrivalSeconds() + seed.targetArrivalBandSeconds() * 0.5,
				0.0,
				0.0,
				0.0,
				true,
				true,
				true,
				true,
				"READY_PASS",
				"surface-wake-validation-seed-passed");
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed failingSeed(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		return copy(seed,
				true,
				seed.targetPressurePascals() * 0.5,
				seed.targetWakeVelocityMetersPerSecond(),
				seed.targetFastArrivalSeconds() + seed.targetArrivalBandSeconds() * 0.5,
				seed.targetPressurePascals() * 0.5,
				0.5,
				0.0,
				false,
				true,
				true,
				false,
				"READY_FAIL",
				"surface-wake-validation-seed-failed");
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed copy(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed,
			boolean ready,
			double observedPressure,
			double observedVelocity,
			double observedArrival,
			double pressureError,
			double pressureErrorRatio,
			double wakeVelocityErrorRatio,
			boolean pressurePassed,
			boolean velocityPassed,
			boolean arrivalPassed,
			boolean validationPassed,
			String status,
			String message
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed(
				seed.presetName(),
				seed.spinState(),
				seed.surfaceType(),
				seed.rotorIndex(),
				seed.clearanceOverRadius(),
				seed.clearanceMeters(),
				ready,
				ready,
				ready,
				ready,
				ready,
				seed.targetPressurePascals(),
				observedPressure,
				pressureError,
				pressureErrorRatio,
				seed.targetWakeVelocityMetersPerSecond(),
				observedVelocity,
				Math.abs(observedVelocity - seed.targetWakeVelocityMetersPerSecond()),
				wakeVelocityErrorRatio,
				seed.targetFastArrivalSeconds(),
				seed.targetSlowArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				observedArrival,
				0.0,
				0.0,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalBandToleranceFraction(),
				pressurePassed,
				velocityPassed,
				arrivalPassed,
				validationPassed,
				status,
				message,
				ready ? "OK" : seed.runStatus(),
				ready ? "live-surface-wake-sample" : seed.runMessage(),
				ready ? "live-runtime" : seed.runRuntimeInfo(),
				ready ? "synthetic-live-surface-wake-error-budget" : seed.resultRuntimeInfo()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_hover_surface_wake_error_budget_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
