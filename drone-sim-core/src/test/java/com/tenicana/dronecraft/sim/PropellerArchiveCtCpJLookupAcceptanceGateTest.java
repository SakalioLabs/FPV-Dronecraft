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

class PropellerArchiveCtCpJLookupAcceptanceGateTest {
	@Test
	void auditBuildsStableLookupAcceptanceScenarios() {
		PropellerArchiveCtCpJLookupAcceptanceGate.CtCpJLookupAcceptanceAudit audit =
				PropellerArchiveCtCpJLookupAcceptanceGate.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Acceptance-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("lookup acceptance remains closed"));
		assertTrue(audit.caveat().contains("handoff-aware lookup execution"));
		assertEquals(105, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(9, audit.targetRowCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(15, audit.scenarioMetricRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(9, audit.targets().size());
		assertEquals(5, audit.scenarios().size());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary current =
				find(audit.scenarios(), "current_no_reviewed_import_no_results").summary();
		assertFalse(current.reviewedImportReady());
		assertEquals(9, current.expectedTargetCount());
		assertEquals(0, current.observedResultCount());
		assertEquals(9, current.missingResultCount());
		assertFalse(current.lookupExecutionContractReady());
		assertFalse(current.lookupAcceptanceReady());
		assertEquals("reviewed-import-missing", current.message());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary executionBlocked =
				find(audit.scenarios(), "reviewed_import_policy_ready_execution_blocked").summary();
		assertTrue(executionBlocked.reviewedImportReady());
		assertTrue(executionBlocked.interpolationPolicyReady());
		assertFalse(executionBlocked.lookupExecutionContractReady());
		assertFalse(executionBlocked.lookupInterpolationExecuted());
		assertEquals("lookup-execution-contract-blocked", executionBlocked.message());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary noResults =
				find(audit.scenarios(), "lookup_execution_ready_no_results").summary();
		assertTrue(noResults.reviewedImportReady());
		assertTrue(noResults.interpolationPolicyReady());
		assertTrue(noResults.lookupExecutionContractReady());
		assertFalse(noResults.lookupInterpolationExecuted());
		assertEquals("lookup-interpolation-results-missing", noResults.message());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary allPass =
				find(audit.scenarios(), "synthetic_all_lookup_targets_pass").summary();
		assertTrue(allPass.reviewedImportReady());
		assertTrue(allPass.interpolationPolicyReady());
		assertTrue(allPass.lookupExecutionContractReady());
		assertTrue(allPass.lookupInterpolationExecuted());
		assertEquals(9, allPass.observedResultCount());
		assertEquals(9, allPass.passedResultCount());
		assertEquals(0, allPass.failedResultCount());
		assertEquals(3, allPass.minObservedNeighborRows());
		assertEquals(0.0, allPass.maxCtShapeOvershoot(), 1.0e-12);
		assertEquals(0.010, allPass.minCpCoefficient(), 1.0e-12);
		assertEquals(0.010, allPass.maxEtaResidual(), 1.0e-12);
		assertTrue(allPass.allTargetsPresent());
		assertTrue(allPass.allExpectedResultsPassed());
		assertTrue(allPass.lookupAcceptanceReady());
		assertTrue(allPass.compactReferenceExportAllowed());
		assertFalse(allPass.runtimeCouplingAllowed());
		assertFalse(allPass.gameplayAutoApplyAllowed());
		assertEquals("lookup-acceptance-ready", allPass.message());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary failed =
				find(audit.scenarios(), "synthetic_one_lookup_result_failed").summary();
		assertEquals(8, failed.passedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertFalse(failed.lookupAcceptanceReady());
		assertFalse(failed.compactReferenceExportAllowed());
		assertEquals("lookup-result-failed", failed.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().acceptedScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(1, audit.extrema().lookupExecutionBlockedScenarioCount());
		assertEquals(9, audit.extrema().maxExpectedTargetCount());
		assertEquals(9, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(1, audit.extrema().compactReferenceExportAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void targetsCarryNeighborAndPhysicalGuardThresholds() {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget staticTarget =
				PropellerArchiveCtCpJLookupAcceptanceGate.target("apDrone", "static_anchor_low_rpm");
		assertEquals("apDrone", staticTarget.presetName());
		assertEquals("static_anchor_low_rpm", staticTarget.caseName());
		assertEquals(1, staticTarget.minNeighborRows());
		assertEquals(0.0, staticTarget.maxCtShapeOvershoot(), 1.0e-12);
		assertEquals(1.0e-6, staticTarget.minCpCoefficient(), 1.0e-18);
		assertEquals(0.020, staticTarget.maxEtaResidual(), 1.0e-12);
		assertEquals(0.004, staticTarget.maxStaticAnchorError(), 1.0e-12);
		assertTrue(staticTarget.requiresStaticAnchorPreservation());
		assertEquals("full-simulation-ct-cp-j-lookup-reference", staticTarget.downstreamUse());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget mid =
				PropellerArchiveCtCpJLookupAcceptanceGate.target("apDrone", "mid_domain_mid_rpm");
		assertEquals(4, mid.minNeighborRows());
		assertEquals(0.0, mid.maxStaticAnchorError(), 1.0e-12);
		assertFalse(mid.requiresStaticAnchorPreservation());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget heavy =
				PropellerArchiveCtCpJLookupAcceptanceGate.target("heavyLift", "mid_domain_mid_rpm");
		assertEquals("performance-only-ct-cp-j-lookup-reference", heavy.downstreamUse());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.target("cinewhoop", "mid_domain_mid_rpm"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.target("apDrone", "high_j_extrapolation_probe"));
	}

	@Test
	void resultsApplyNeighborShapePowerEtaAndAnchorGuards() {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult passing =
				PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "mid_domain_mid_rpm", 4, 0.0, 0.010, 0.010, 0.0);
		assertTrue(passing.passed());
		assertEquals("PASS", passing.status());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult tooFewRows =
				PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "mid_domain_mid_rpm", 3, 0.0, 0.010, 0.010, 0.0);
		assertFalse(tooFewRows.passed());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult overshoot =
				PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "mid_domain_mid_rpm", 4, 0.001, 0.010, 0.010, 0.0);
		assertFalse(overshoot.passed());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult lowCp =
				PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "mid_domain_mid_rpm", 4, 0.0, 0.0, 0.010, 0.0);
		assertFalse(lowCp.passed());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult highEta =
				PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "mid_domain_mid_rpm", 4, 0.0, 0.010, 0.030, 0.0);
		assertFalse(highEta.passed());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult anchor =
				PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "static_anchor_low_rpm", 1, 0.0, 0.010, 0.010, 0.005);
		assertFalse(anchor.passed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "mid_domain_mid_rpm", -1, 0.0, 0.010, 0.010, 0.0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.result(
						"apDrone", "mid_domain_mid_rpm", 4, -1.0, 0.010, 0.010, 0.0));
	}

	@Test
	void gateRejectsIncompleteFailedUnexpectedAndDuplicateResults() {
		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets =
				PropellerArchiveCtCpJLookupAcceptanceGate.targets();
		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> passing =
				passingResults(targets);

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary referenceBlocked =
				PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, false, targets, passing, "reference-review-pending");
		assertTrue(referenceBlocked.lookupAcceptanceReady());
		assertFalse(referenceBlocked.compactReferenceExportAllowed());
		assertEquals("lookup-accepted-reference-review-blocked", referenceBlocked.message());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary executionBlocked =
				PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, false, true, true, targets, passing, "execution-blocked");
		assertFalse(executionBlocked.lookupExecutionContractReady());
		assertTrue(executionBlocked.lookupInterpolationExecuted());
		assertFalse(executionBlocked.lookupAcceptanceReady());
		assertEquals("lookup-execution-contract-blocked", executionBlocked.message());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary missing =
				PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, true, targets, passing.subList(0, 8), "missing-one-result");
		assertFalse(missing.lookupAcceptanceReady());
		assertEquals(1, missing.missingResultCount());
		assertEquals("lookup-result-set-incomplete", missing.message());

		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> failed = new ArrayList<>(passing);
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget first = targets.get(0);
		failed.set(0, PropellerArchiveCtCpJLookupAcceptanceGate.result(
				first.presetName(), first.caseName(), 0, 0.010, 0.0, 0.030, 0.020));
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary failedSummary =
				PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, true, targets, failed, "one-result-failed");
		assertFalse(failedSummary.lookupAcceptanceReady());
		assertEquals(1, failedSummary.failedResultCount());

		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> unexpected =
				new ArrayList<>(passing);
		unexpected.remove(0);
		unexpected.add(new PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult(
				"unexpectedPreset", "mid_domain_mid_rpm", 99, 0.0, 0.010, 0.0, 0.0, true, "PASS"));
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary unexpectedSummary =
				PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, true, targets, unexpected, "unexpected-result");
		assertFalse(unexpectedSummary.lookupAcceptanceReady());
		assertEquals(1, unexpectedSummary.missingResultCount());
		assertEquals(1, unexpectedSummary.unexpectedResultCount());

		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> duplicateResult =
				new ArrayList<>(passing);
		duplicateResult.set(1, duplicateResult.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, true, targets, duplicateResult, "duplicate-result"));

		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> duplicateTarget =
				new ArrayList<>(targets);
		duplicateTarget.set(1, duplicateTarget.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, true, duplicateTarget, passing, "duplicate-target"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, true, null, passing, "missing-targets"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						true, true, true, true, targets, null, "missing-results"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupAcceptanceGate.CtCpJLookupAcceptanceAudit audit =
				PropellerArchiveCtCpJLookupAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_acceptance_target,apDrone,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_acceptance_scenario,current_no_reviewed_import_no_results,missing_result_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_acceptance_scenario,reviewed_import_policy_ready_execution_blocked,lookup_execution_contract_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_acceptance_scenario,synthetic_all_lookup_targets_pass,lookup_acceptance_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_acceptance_scenario,synthetic_one_lookup_result_failed,failed_result_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_acceptance_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceScenario find(
			List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> passingResults(
			List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets
	) {
		return targets.stream()
				.map(target -> PropellerArchiveCtCpJLookupAcceptanceGate.result(
						target.presetName(),
						target.caseName(),
						target.minNeighborRows() + 1,
						0.0,
						0.010,
						target.maxEtaResidual() * 0.5,
						target.requiresStaticAnchorPreservation()
								? target.maxStaticAnchorError() * 0.5
								: 0.0
				))
				.toList();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
