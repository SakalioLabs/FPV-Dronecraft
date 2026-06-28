package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceValidationErrorBudgetTest {
	@Test
	void auditBuildsCurrentBlockedValidationErrorBudgets() {
		Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Validation-Error-Budget-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not provide gameplay"));
		assertEquals(76, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(2, audit.groupSampleCount());
		assertEquals(4, audit.expectedPresetCount());
		assertEquals(28, audit.groupMetricCount());
		assertEquals(13, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(2, audit.groups().size());

		for (Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup group
				: audit.groups()) {
			assertTrue("hover".equals(group.spinState()) || "cruise".equals(group.spinState()));
			assertEquals(4, group.expectedPresetCount());
			assertEquals(4, group.observedValidationRunCount());
			assertEquals(0, group.validationSeedReadyCount());
			assertEquals(0, group.validationInvokedCount());
			assertEquals(0, group.validationPassedCount());
			assertEquals(4, group.skippedValidationRunCount());
			assertEquals(4, group.skippedValidationBlockerCount());
			assertEquals("powered-source-api-surface-missing", group.dominantSkippedValidationMessage());
			assertEquals(0, group.failedValidationRunCount());
			assertEquals(0, group.acceptanceCandidateCount());
			assertEquals(0, group.missingPresetCount());
			assertEquals(0, group.unexpectedPresetCount());
			assertTrue(group.allExpectedRunsPresent());
			assertFalse(group.allValidationSeedsReady());
			assertFalse(group.allValidationPassed());
			assertFalse(group.allAcceptanceCandidates());
			assertEquals(0.0, group.maxForceErrorRatio(), 1.0e-12);
			assertEquals(0.0, group.maxForceErrorNewtons(), 1.0e-12);
			assertEquals(0.0, group.maxMomentErrorNewtonMeters(), 1.0e-12);
			assertEquals(0.0, group.maxCenterOfForceErrorMeters(), 1.0e-12);
			assertTrue(group.meanTargetForceMagnitudeNewtons() > 0.0);
			assertTrue(group.meanTargetMomentMagnitudeNewtonMeters() >= 0.0);
			assertFalse(group.validationBudgetCandidate());
			assertEquals("BLOCKED", group.status());
			assertEquals("powered-source-api-surface-missing", group.message());
		}

		assertEquals(2, audit.extrema().groupCount());
		assertEquals(0, audit.extrema().validationBudgetCandidateCount());
		assertEquals(2, audit.extrema().blockedGroupCount());
		assertEquals(0, audit.extrema().maxMissingPresetCount());
		assertEquals(0, audit.extrema().maxUnexpectedPresetCount());
		assertEquals(4, audit.extrema().maxSkippedValidationRunCount());
		assertEquals(4, audit.extrema().maxSkippedValidationBlockerCount());
		assertEquals(0, audit.extrema().maxFailedValidationRunCount());
		assertEquals(0.0, audit.extrema().maxForceErrorRatio(), 1.0e-12);
		assertTrue(audit.extrema().maxMeanTargetForceMagnitudeNewtons() > 0.0);
	}

	@Test
	void groupAcceptsOnlyCompletePassingAcceptanceCandidates() {
		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> hoverRuns =
				currentRuns("hover");
		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> passing =
				hoverRuns.stream()
						.map(Aerodynamics4McL2PoweredSourceValidationErrorBudgetTest::passingRun)
						.toList();

		Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup ready =
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.group("hover", passing);

		assertEquals(4, ready.validationSeedReadyCount());
		assertEquals(4, ready.validationInvokedCount());
		assertEquals(4, ready.validationPassedCount());
		assertEquals(4, ready.acceptanceCandidateCount());
		assertEquals(0, ready.skippedValidationRunCount());
		assertEquals(0, ready.skippedValidationBlockerCount());
		assertEquals("none", ready.dominantSkippedValidationMessage());
		assertTrue(ready.allValidationSeedsReady());
		assertTrue(ready.allValidationPassed());
		assertTrue(ready.allAcceptanceCandidates());
		assertTrue(ready.validationBudgetCandidate());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-validation-error-budget-ready", ready.message());
	}

	@Test
	void groupRejectsMissingAndFailedValidationRuns() {
		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> passing =
				currentRuns("cruise").stream()
						.map(Aerodynamics4McL2PoweredSourceValidationErrorBudgetTest::passingRun)
						.toList();

		Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup missing =
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.group("cruise", passing.subList(0, 3));
		assertEquals(1, missing.missingPresetCount());
		assertFalse(missing.validationBudgetCandidate());
		assertEquals("validation-run-set-incomplete", missing.message());

		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> failed =
				new ArrayList<>(passing);
		failed.set(0, failedRun(failed.get(0)));
		Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetGroup failedGroup =
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.group("cruise", failed);
		assertEquals(1, failedGroup.failedValidationRunCount());
		assertEquals(3, failedGroup.validationPassedCount());
		assertTrue(failedGroup.allValidationSeedsReady());
		assertFalse(failedGroup.allValidationPassed());
		assertFalse(failedGroup.validationBudgetCandidate());
		assertTrue(failedGroup.maxForceErrorRatio() > 0.0);
		assertEquals("validation-runs-not-passing", failedGroup.message());
	}

	@Test
	void rejectsInvalidInputsAndDuplicatePresets() {
		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> passing =
				currentRuns("hover").stream()
						.map(Aerodynamics4McL2PoweredSourceValidationErrorBudgetTest::passingRun)
						.toList();
		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> duplicate =
				new ArrayList<>(passing);
		duplicate.set(1, passing.get(0));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit(
						(Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunMatrixAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceValidationErrorBudget.group("idle", passing));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceValidationErrorBudget.group("hover", null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceValidationErrorBudget.group("hover", duplicate));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceValidationErrorBudget.PoweredSourceValidationErrorBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve(
				"docs/data/a4mc_l2_powered_source_validation_error_budget_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_error_budget_summary,all_groups,validation_budget_candidate_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_error_budget,hover,skipped_validation_run_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_error_budget,hover,dominant_skipped_validation_message,powered-source-api-surface-missing,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_validation_error_budget,cruise,message,powered-source-api-surface-missing,")));
	}

	private static List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> currentRuns(
			String spinState
	) {
		return Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit().runs().stream()
				.filter(run -> spinState.equals(run.spinState()))
				.toList();
	}

	private static Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary passingRun(
			Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary run
	) {
		return copy(run, true, true, true, true, 0.0, 0.0, 0.0, 0.0,
				true, "PASSED", "validation-result-ready");
	}

	private static Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary failedRun(
			Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary run
	) {
		return copy(run, true, true, true, false,
				run.targetForceMagnitudeNewtons() * 0.5,
				0.5,
				0.0,
				0.0,
				false,
				"FAILED",
				"validation-result-failed");
	}

	private static Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary copy(
			Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary run,
			boolean seedReady,
			boolean deltaReady,
			boolean invoked,
			boolean passed,
			double forceError,
			double forceErrorRatio,
			double momentError,
			double centerError,
			boolean candidate,
			String status,
			String message
	) {
		return new Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary(
				run.presetName(),
				run.spinState(),
				run.validationPacketId(),
				run.acceptanceGatePacketId(),
				seedReady,
				deltaReady,
				invoked,
				passed,
				run.hoverValidationTarget(),
				run.cruiseValidationTarget(),
				run.forceDeltaMagnitudeNewtons(),
				run.targetForceMagnitudeNewtons(),
				forceError,
				forceErrorRatio,
				run.momentDeltaMagnitudeNewtonMeters(),
				run.targetMomentMagnitudeNewtonMeters(),
				momentError,
				run.centerOfForceOffsetMeters(),
				run.targetCenterOfForceOffsetMeters(),
				centerError,
				candidate,
				status,
				message,
				run.staticRuntimeInfo(),
				"synthetic-live-powered-source-validation"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_validation_error_budget_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
