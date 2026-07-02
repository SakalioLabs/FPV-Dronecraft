package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJLookupExecutionAcceptanceHandoffTest {
	private static final PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff
			.CtCpJLookupExecutionAcceptanceHandoffAudit AUDIT =
			PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.audit();
	private static final PropellerArchiveCtCpJLookupExecutionTargetRunQueue.CtCpJLookupExecutionTargetRunQueueAudit
			RUN_QUEUE_AUDIT = PropellerArchiveCtCpJLookupExecutionTargetRunQueue.audit();
	private static final PropellerArchiveCtCpJLookupExecutionContract.CtCpJLookupExecutionContractAudit
			EXECUTION_AUDIT = PropellerArchiveCtCpJLookupExecutionContract.audit();

	@Test
	void auditBuildsAcceptanceHandoffScenarios() {
		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Execution-Acceptance-Handoff-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("aggregates result seeds"));
		assertEquals(32, AUDIT.packetRowCount());
		assertEquals(8, AUDIT.sourceReferenceRowCount());
		assertEquals(3, AUDIT.handoffScenarioRowCount());
		assertEquals(20, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(3, AUDIT.scenarios().size());
	}

	@Test
	void currentScenarioBlocksBeforeResultSeedAvailability() {
		PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario current =
				scenario("current_result_seeds_unavailable");

		assertEquals("current_payload_and_surface_fit_blocked", current.resultSeedScenarioName());
		assertFalse(current.reviewedImportReady());
		assertFalse(current.interpolationPolicyReady());
		assertFalse(current.lookupExecutionContractReady());
		assertFalse(current.lookupInterpolationExecuted());
		assertEquals(9, current.expectedTargetCount());
		assertEquals(9, current.seedRowCount());
		assertEquals(0, current.readySeedCount());
		assertEquals(0, current.pendingSeedCount());
		assertEquals(9, current.unavailableSeedCount());
		assertEquals(0, current.acceptanceResultCount());
		assertEquals(9, current.missingAcceptanceResultCount());
		assertFalse(current.lookupAcceptanceReady());
		assertFalse(current.acceptanceHandoffReady());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", current.status());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				current.nextRequiredAction());
	}

	@Test
	void authorizedRunsRemainPendingUntilResultEvidenceExists() {
		PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario pending =
				scenario("authorized_runs_pending_results");

		assertTrue(pending.reviewedImportReady());
		assertTrue(pending.interpolationPolicyReady());
		assertTrue(pending.lookupExecutionContractReady());
		assertFalse(pending.lookupInterpolationExecuted());
		assertEquals(9, pending.seedRowCount());
		assertEquals(0, pending.readySeedCount());
		assertEquals(9, pending.pendingSeedCount());
		assertEquals(0, pending.unavailableSeedCount());
		assertEquals(0, pending.acceptanceResultCount());
		assertEquals(9, pending.missingAcceptanceResultCount());
		assertFalse(pending.lookupAcceptanceReady());
		assertEquals("PENDING_RESULTS", pending.status());
		assertEquals("record-lookup-execution-result-evidence-before-acceptance",
				pending.nextRequiredAction());
		assertTrue(pending.message().contains("waiting for CT/CP/J result evidence"));
	}

	@Test
	void syntheticAllReadyScenarioFeedsAcceptanceGateWithoutReferenceOrRuntimeAuthority() {
		PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario ready =
				scenario("synthetic_all_result_seeds_ready");

		assertTrue(ready.reviewedImportReady());
		assertTrue(ready.interpolationPolicyReady());
		assertTrue(ready.lookupExecutionContractReady());
		assertTrue(ready.lookupInterpolationExecuted());
		assertFalse(ready.compactReferenceReviewed());
		assertEquals(9, ready.readySeedCount());
		assertEquals(0, ready.pendingSeedCount());
		assertEquals(9, ready.acceptanceResultCount());
		assertEquals(9, ready.passedAcceptanceResultCount());
		assertEquals(0, ready.failedAcceptanceResultCount());
		assertEquals(0, ready.missingAcceptanceResultCount());
		assertEquals(0, ready.unexpectedAcceptanceResultCount());
		assertEquals(3, ready.minObservedNeighborRows());
		assertEquals(0.0, ready.maxCtShapeOvershoot(), 1.0e-12);
		assertEquals(0.010, ready.minCpCoefficient(), 1.0e-12);
		assertEquals(0.010, ready.maxEtaResidual(), 1.0e-12);
		assertEquals(0.002, ready.maxStaticAnchorError(), 1.0e-12);
		assertTrue(ready.allTargetsPresent());
		assertTrue(ready.allExpectedResultsPassed());
		assertTrue(ready.lookupAcceptanceReady());
		assertTrue(ready.acceptanceHandoffReady());
		assertFalse(ready.compactReferenceExportAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("HANDOFF_READY", ready.status());
		assertEquals("review-accepted-ct-cp-j-results-before-reference-export",
				ready.nextRequiredAction());
	}

	@Test
	void failedResultSeedBlocksHandoffEvenWhenUpstreamFlagsAreReady() {
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow =
				runRow("payload_and_surface_fit_ready", "apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow failedSeed =
				PropellerArchiveCtCpJLookupExecutionResultSeed.seed(
						runRow, executionScenario("synthetic_missing_neighbor_blocked"));

		PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario failed =
				PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.scenario(
						"one_failed_seed",
						"one_failed_seed",
						List.of(failedSeed),
						true,
						true,
						true,
						false,
						"one-failed-result-seed");

		assertEquals(1, failed.seedRowCount());
		assertEquals(0, failed.readySeedCount());
		assertEquals(1, failed.failedSeedCount());
		assertEquals(0, failed.acceptanceResultCount());
		assertEquals(9, failed.missingAcceptanceResultCount());
		assertFalse(failed.lookupAcceptanceReady());
		assertEquals("RESULT_BLOCKED", failed.status());
		assertEquals("fix-lookup-result-seeds-before-acceptance-handoff", failed.nextRequiredAction());
	}

	@Test
	void summaryTracksCurrentBlockedStateAndSyntheticReadyCeiling() {
		PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffSummary summary =
				AUDIT.summary();

		assertEquals(3, summary.scenarioCount());
		assertEquals(1, summary.handoffReadyScenarioCount());
		assertEquals(2, summary.blockedScenarioCount());
		assertEquals(9, summary.maxExpectedTargetCount());
		assertEquals(9, summary.maxReadySeedCount());
		assertEquals(9, summary.maxPendingSeedCount());
		assertEquals(9, summary.maxUnavailableSeedCount());
		assertEquals(9, summary.maxAcceptanceResultCount());
		assertEquals(9, summary.maxPassedAcceptanceResultCount());
		assertEquals(9, summary.maxMissingAcceptanceResultCount());
		assertEquals(0, summary.currentReadySeedCount());
		assertEquals(0, summary.currentAcceptanceResultCount());
		assertEquals(9, summary.currentMissingAcceptanceResultCount());
		assertEquals(0, summary.compactReferenceExportAllowedCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("synthetic_all_result_seeds_ready", summary.firstHandoffReadyScenario());
		assertEquals("authorized_runs_pending_results", summary.firstPendingScenario());
		assertEquals("BLOCKED", summary.currentStatus());
	}

	@Test
	void rejectsUnknownScenariosAndInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.scenario(
						"", "seed", List.of(), true, true, true, false, "bad"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.scenario(
						"bad", "seed", null, true, true, true, false, "bad"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.scenario(
						"bad", "seed", List.of(), true, true, true, false, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_execution_acceptance_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_acceptance_handoff,current_result_seeds_unavailable,BLOCKED,acceptance_handoff_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_acceptance_handoff,authorized_runs_pending_results,PENDING_RESULTS,pending_seed_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_acceptance_handoff,synthetic_all_result_seeds_ready,HANDOFF_READY,acceptance_result_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_acceptance_handoff_summary,all,BLOCKED,max_ready_seed_count,9,count,")));
	}

	private static PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario
			scenario(String scenarioName) {
		return AUDIT.scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow(
			String scenarioName,
			String presetName,
			String caseName
	) {
		return RUN_QUEUE_AUDIT.rows()
				.stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult executionScenario(
			String scenarioName
	) {
		return EXECUTION_AUDIT.scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow()
				.result();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_execution_acceptance_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
