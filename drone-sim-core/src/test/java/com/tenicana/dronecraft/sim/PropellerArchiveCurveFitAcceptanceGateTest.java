package com.tenicana.dronecraft.sim;

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

class PropellerArchiveCurveFitAcceptanceGateTest {
	@Test
	void auditBuildsStableCurveFitAcceptanceScenarios() {
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceAudit audit =
				PropellerArchiveCurveFitAcceptanceGate.audit();

		assertEquals("User-Propeller-Archive-Curve-Fit-Acceptance-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Curve-fit acceptance remains closed"));
		assertEquals(65, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(12, audit.scenarioMetricRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(24, audit.targets().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary current =
				find(audit.scenarios(), "current_no_reviewed_import_no_results").summary();
		assertFalse(current.reviewedImportReady());
		assertEquals(24, current.expectedTargetCount());
		assertEquals(0, current.observedResultCount());
		assertEquals(24, current.missingResultCount());
		assertFalse(current.curveFitAcceptanceReady());
		assertFalse(current.compactReferenceExportAllowed());
		assertEquals("reviewed-import-missing", current.message());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary blocked =
				find(audit.scenarios(), "reviewed_import_coverage_blocked_no_results").summary();
		assertTrue(blocked.reviewedImportReady());
		assertFalse(blocked.fitInputCoverageReady());
		assertEquals("fit-input-coverage-blocked", blocked.message());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary allPass =
				find(audit.scenarios(), "synthetic_all_curve_targets_pass").summary();
		assertTrue(allPass.reviewedImportReady());
		assertTrue(allPass.fitInputCoverageReady());
		assertTrue(allPass.curveFitExecuted());
		assertEquals(24, allPass.observedResultCount());
		assertEquals(24, allPass.passedResultCount());
		assertEquals(0, allPass.failedResultCount());
		assertTrue(allPass.allTargetsPresent());
		assertTrue(allPass.allExpectedResultsPassed());
		assertTrue(allPass.curveFitAcceptanceReady());
		assertTrue(allPass.compactReferenceExportAllowed());
		assertFalse(allPass.runtimeCouplingAllowed());
		assertFalse(allPass.gameplayAutoApplyAllowed());
		assertEquals("curve-fit-acceptance-ready", allPass.message());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary failed =
				find(audit.scenarios(), "synthetic_one_curve_result_failed").summary();
		assertEquals(23, failed.passedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertFalse(failed.curveFitAcceptanceReady());
		assertFalse(failed.compactReferenceExportAllowed());
		assertEquals("curve-fit-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().acceptedScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(24, audit.extrema().maxExpectedTargetCount());
		assertEquals(24, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(1, audit.extrema().compactReferenceExportAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void targetsCarryCurveSpecificThresholdsAndDownstreamUse() {
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget ct =
				PropellerArchiveCurveFitAcceptanceGate.target("racingQuad", "ct_vs_advance");
		assertEquals("racingQuad", ct.presetName());
		assertEquals("ct_vs_advance", ct.curveName());
		assertEquals("performance", ct.sourceTable());
		assertEquals(0.015, ct.maxWeightedRmse(), 1.0e-12);
		assertEquals(0.010, ct.maxAnchorError(), 1.0e-12);
		assertEquals(24, ct.minValidationRowCount());
		assertEquals(0, ct.maxPhysicalConstraintViolationCount());
		assertTrue(ct.downstreamUse().contains("thrust coefficient"));

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget beta =
				PropellerArchiveCurveFitAcceptanceGate.target("heavyLift", "beta_distribution");
		assertEquals("geometry", beta.sourceTable());
		assertEquals(1.5, beta.maxWeightedRmse(), 1.0e-12);
		assertEquals(1.0, beta.maxAnchorError(), 1.0e-12);
		assertEquals(8, beta.minValidationRowCount());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.target("missing", "ct_vs_advance"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.target("racingQuad", "missing"));
	}

	@Test
	void resultsApplyThresholdsRowsAndPhysicalViolations() {
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult passing =
				PropellerArchiveCurveFitAcceptanceGate.result(
						"racingQuad", "ct_vs_advance", 0.010, 0.006, 28, 0);
		assertTrue(passing.passed());
		assertEquals("PASS", passing.status());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult highError =
				PropellerArchiveCurveFitAcceptanceGate.result(
						"racingQuad", "ct_vs_advance", 0.020, 0.006, 28, 0);
		assertFalse(highError.passed());
		assertEquals("FAIL", highError.status());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult tooFewRows =
				PropellerArchiveCurveFitAcceptanceGate.result(
						"racingQuad", "ct_vs_advance", 0.010, 0.006, 23, 0);
		assertFalse(tooFewRows.passed());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult violation =
				PropellerArchiveCurveFitAcceptanceGate.result(
						"racingQuad", "ct_vs_advance", 0.010, 0.006, 28, 1);
		assertFalse(violation.passed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.result(
						"racingQuad", "ct_vs_advance", -1.0, 0.0, 28, 0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.result(
						"racingQuad", "ct_vs_advance", 0.0, 0.0, -1, 0));
	}

	@Test
	void gateRejectsIncompleteFailedUnexpectedAndDuplicateResults() {
		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget> targets =
				PropellerArchiveCurveFitAcceptanceGate.targets();
		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult> passing =
				passingResults(targets);

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary open =
				PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, false, targets, passing, "accepted-reference-review-pending");
		assertTrue(open.curveFitAcceptanceReady());
		assertFalse(open.compactReferenceExportAllowed());
		assertEquals("curve-fit-accepted-reference-review-blocked", open.message());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary missing =
				PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, true, targets, passing.subList(0, 23), "missing-one-result");
		assertFalse(missing.curveFitAcceptanceReady());
		assertEquals(1, missing.missingResultCount());
		assertEquals("curve-fit-result-set-incomplete", missing.message());

		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult> failed = new ArrayList<>(passing);
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget first = targets.get(0);
		failed.set(0, PropellerArchiveCurveFitAcceptanceGate.result(
				first.presetName(), first.curveName(), first.maxWeightedRmse() * 2.0,
				first.maxAnchorError(), first.minValidationRowCount() + 1, 0));
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary failedSummary =
				PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, true, targets, failed, "one-result-failed");
		assertFalse(failedSummary.curveFitAcceptanceReady());
		assertEquals(1, failedSummary.failedResultCount());

		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult> unexpected = new ArrayList<>(passing);
		unexpected.remove(0);
		unexpected.add(new PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult(
				"unexpectedPreset", "ct_vs_advance", 0.0, 0.0, 99, 0, true, "PASS"));
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary unexpectedSummary =
				PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, true, targets, unexpected, "unexpected-result");
		assertFalse(unexpectedSummary.curveFitAcceptanceReady());
		assertEquals(1, unexpectedSummary.missingResultCount());
		assertEquals(1, unexpectedSummary.unexpectedResultCount());

		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult> duplicateResult = new ArrayList<>(passing);
		duplicateResult.set(1, duplicateResult.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, true, targets, duplicateResult, "duplicate-result"));

		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget> duplicateTarget =
				new ArrayList<>(targets);
		duplicateTarget.set(1, duplicateTarget.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, true, duplicateTarget, passing, "duplicate-target"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, true, null, passing, "missing-targets"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitAcceptanceGate.gate(
						true, true, true, true, targets, null, "missing-results"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceAudit audit =
				PropellerArchiveCurveFitAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_curve_fit_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_acceptance_gate_summary,all_scenarios,accepted_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_acceptance_gate_scenario,current_no_reviewed_import_no_results,missing_result_count,24,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_acceptance_gate_scenario,synthetic_all_curve_targets_pass,curve_fit_acceptance_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_acceptance_gate_scenario,synthetic_one_curve_result_failed,failed_result_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_acceptance_gate_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceScenario find(
			List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult> passingResults(
			List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget> targets
	) {
		return targets.stream()
				.map(target -> PropellerArchiveCurveFitAcceptanceGate.result(
						target.presetName(),
						target.curveName(),
						target.maxWeightedRmse() * 0.5,
						target.maxAnchorError() * 0.5,
						target.minValidationRowCount() + 2,
						0
				))
				.toList();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_curve_fit_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
