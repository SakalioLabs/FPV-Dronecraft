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

class Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudgetTest {
	@Test
	void auditBuildsCurrentBlockedCruiseSkewWakeErrorBudget() {
		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Error-Budget-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not provide gameplay"));
		assertEquals(1514, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(48, audit.groupSampleCount());
		assertEquals(4, audit.expectedRotorSeedCount());
		assertEquals(31, audit.groupMetricCount());
		assertEquals(18, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(48, audit.groups().size());

		for (Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group
				: audit.groups()) {
			assertEquals("cruise", group.spinState());
			assertTrue(group.axialPlaneIndex() >= 1);
			assertTrue(group.axialPlaneIndex() <= 4);
			assertTrue(group.sweepColumnIndex() == -1 || group.sweepColumnIndex() == 0 || group.sweepColumnIndex() == 1);
			assertTrue(group.axialPlaneFraction() > 0.0);
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
			assertTrue(group.meanTargetResultantDynamicPressurePascals() > 0.0);
			assertEquals(0.0, group.maxPressureErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.meanPressureErrorRatio(), 1.0e-12);
			assertTrue(group.meanTargetResultantWakeVelocityMetersPerSecond() > 0.0);
			assertEquals(0.0, group.maxWakeVelocityErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.meanWakeVelocityErrorRatio(), 1.0e-12);
			assertTrue(group.meanArrivalBandSeconds() >= 0.0);
			assertEquals(0.0, group.maxArrivalWindowErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.meanArrivalWindowErrorRatio(), 1.0e-12);
			assertTrue(group.meanTargetAxialMomentumFluxNewtons() > 0.0);
			assertEquals(0.0, group.maxMomentumErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.meanMomentumErrorRatio(), 1.0e-12);
			assertFalse(group.validationCandidate());
			assertEquals("BLOCKED", group.status());
			assertEquals("rotor-seeds-not-ready", group.message());
		}

		assertEquals(48, audit.extrema().groupCount());
		assertEquals(16, audit.extrema().centerlineSweepGroupCount());
		assertEquals(32, audit.extrema().lateralSweepGroupCount());
		assertEquals(48, audit.extrema().completeGroupCount());
		assertEquals(0, audit.extrema().readyGroupCount());
		assertEquals(0, audit.extrema().passedGroupCount());
		assertEquals(0, audit.extrema().validationCandidateCount());
		assertEquals(48, audit.extrema().blockedGroupCount());
		assertEquals(0, audit.extrema().maxMissingRotorSeedCount());
		assertEquals(0, audit.extrema().maxUnexpectedRotorSeedCount());
		assertEquals(4, audit.extrema().maxUnavailableRotorSeedCount());
		assertEquals(0.0, audit.extrema().maxPressureErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxWakeVelocityErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxArrivalWindowErrorRatio(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxMomentumErrorRatio(), 1.0e-12);
		assertTrue(audit.extrema().maxMeanTargetResultantDynamicPressurePascals() > 0.0);
		assertTrue(audit.extrema().maxMeanTargetResultantWakeVelocityMetersPerSecond() > 0.0);
		assertTrue(audit.extrema().maxMeanTargetAxialMomentumFluxNewtons() > 0.0);
	}

	@Test
	void groupAcceptsOnlyCompleteReadyPassingRotorSeeds() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> current =
				currentGroup("racingQuad", 1, 0);
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> passing =
				current.stream()
						.map(Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudgetTest::passingSeed)
						.toList();

		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup ready =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "cruise", 1, 0, passing);

		assertEquals(4, ready.observedRotorSeedCount());
		assertEquals(4, ready.readyRotorSeedCount());
		assertEquals(4, ready.passedRotorSeedCount());
		assertTrue(ready.allRotorSeedsPresent());
		assertTrue(ready.allRotorSeedsReady());
		assertTrue(ready.allRotorSeedsPassed());
		assertTrue(ready.validationCandidate());
		assertEquals("READY", ready.status());
		assertEquals("cruise-skew-wake-error-budget-ready", ready.message());
	}

	@Test
	void groupRejectsMissingAndFailedRotorEvidence() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> passing =
				currentGroup("racingQuad", 1, 0).stream()
						.map(Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudgetTest::passingSeed)
						.toList();

		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup missing =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "cruise", 1, 0, passing.subList(0, 3));
		assertEquals(1, missing.missingRotorSeedCount());
		assertFalse(missing.allRotorSeedsPresent());
		assertFalse(missing.validationCandidate());
		assertEquals("rotor-seed-set-incomplete", missing.message());

		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> failed =
				new java.util.ArrayList<>(passing);
		failed.set(0, failingSeed(passing.get(0)));
		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup failedGroup =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "cruise", 1, 0, failed);
		assertEquals(1, failedGroup.failedRotorSeedCount());
		assertTrue(failedGroup.allRotorSeedsReady());
		assertFalse(failedGroup.allRotorSeedsPassed());
		assertFalse(failedGroup.validationCandidate());
		assertEquals("rotor-seeds-failed", failedGroup.message());
		assertTrue(failedGroup.maxMomentumErrorRatio()
				> Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.MOMENTUM_RELATIVE_TOLERANCE);
	}

	@Test
	void rejectsInvalidInputsAndDuplicateRotorSeeds() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> current =
				currentGroup("racingQuad", 1, 0);
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> duplicate =
				new java.util.ArrayList<>(current);
		duplicate.set(1, current.get(0));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"", "cruise", 1, 0, current));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "hover", 1, 0, current));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "cruise", 0, 0, current));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "cruise", 1, 2, current));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "cruise", 1, 0, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
						"racingQuad", "cruise", 1, 0, duplicate));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_error_budget_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_error_budget_summary,all_groups,group_count,48,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_error_budget_summary,all_groups,validation_candidate_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_error_budget,racingQuad:plane1:sweep0,unavailable_rotor_seed_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_error_budget,racingQuad:plane1:sweep0,message,rotor-seeds-not-ready,")));
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> currentGroup(
			String presetName,
			int axialPlaneIndex,
			int sweepColumnIndex
	) {
		return Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit().seeds().stream()
				.filter(seed -> presetName.equals(seed.presetName())
						&& axialPlaneIndex == seed.axialPlaneIndex()
						&& sweepColumnIndex == seed.sweepColumnIndex())
				.toList();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed passingSeed(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		return copy(seed,
				true,
				seed.targetResultantDynamicPressurePascals(),
				seed.targetResultantWakeVelocityMetersPerSecond(),
				seed.targetCenterlineArrivalSeconds(),
				seed.targetAxialMomentumFluxNewtons(),
				0.0,
				0.0,
				0.0,
				0.0,
				true,
				true,
				true,
				true,
				true,
				"READY_PASS",
				"cruise-skew-wake-validation-seed-passed");
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed failingSeed(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		return copy(seed,
				true,
				seed.targetResultantDynamicPressurePascals(),
				seed.targetResultantWakeVelocityMetersPerSecond(),
				seed.targetCenterlineArrivalSeconds(),
				seed.targetAxialMomentumFluxNewtons() * 0.5,
				0.0,
				0.0,
				0.0,
				0.5,
				true,
				true,
				true,
				false,
				false,
				"READY_FAIL",
				"cruise-skew-wake-validation-seed-failed");
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed copy(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed,
			boolean ready,
			double observedPressure,
			double observedVelocity,
			double observedArrival,
			double observedMomentum,
			double pressureErrorRatio,
			double wakeVelocityErrorRatio,
			double arrivalErrorRatio,
			double momentumErrorRatio,
			boolean pressurePassed,
			boolean velocityPassed,
			boolean arrivalPassed,
			boolean momentumPassed,
			boolean validationPassed,
			String status,
			String message
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed(
				seed.presetName(),
				seed.spinState(),
				seed.rotorIndex(),
				seed.axialPlaneIndex(),
				seed.sweepColumnIndex(),
				seed.axialPlaneFraction(),
				ready,
				ready,
				ready,
				ready,
				ready,
				ready,
				seed.targetAxialWakePressurePascals(),
				seed.targetResultantDynamicPressurePascals(),
				observedPressure,
				Math.abs(observedPressure - seed.targetResultantDynamicPressurePascals()),
				pressureErrorRatio,
				seed.targetResultantWakeVelocityMetersPerSecond(),
				observedVelocity,
				Math.abs(observedVelocity - seed.targetResultantWakeVelocityMetersPerSecond()),
				wakeVelocityErrorRatio,
				seed.targetCenterlineArrivalSeconds(),
				seed.targetLateralArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				seed.targetArrivalToleranceSeconds(),
				observedArrival,
				0.0,
				arrivalErrorRatio,
				seed.targetAxialMomentumFluxNewtons(),
				observedMomentum,
				Math.abs(observedMomentum - seed.targetAxialMomentumFluxNewtons()),
				momentumErrorRatio,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalTimeToleranceFraction(),
				seed.momentumToleranceRatio(),
				pressurePassed,
				velocityPassed,
				arrivalPassed,
				momentumPassed,
				validationPassed,
				status,
				message,
				ready ? "OK" : seed.runStatus(),
				ready ? "live-cruise-skew-wake-sample" : seed.runMessage(),
				ready ? "live-runtime" : seed.runRuntimeInfo(),
				ready ? "synthetic-live-cruise-skew-wake-error-budget" : seed.resultRuntimeInfo()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_error_budget_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
