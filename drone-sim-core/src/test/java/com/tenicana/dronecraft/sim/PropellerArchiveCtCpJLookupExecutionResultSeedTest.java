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

class PropellerArchiveCtCpJLookupExecutionResultSeedTest {
	private static final PropellerArchiveCtCpJLookupExecutionResultSeed.CtCpJLookupExecutionResultSeedAudit AUDIT =
			PropellerArchiveCtCpJLookupExecutionResultSeed.audit();
	private static final PropellerArchiveCtCpJLookupExecutionTargetRunQueue.CtCpJLookupExecutionTargetRunQueueAudit
			RUN_QUEUE_AUDIT = PropellerArchiveCtCpJLookupExecutionTargetRunQueue.audit();
	private static final PropellerArchiveCtCpJLookupExecutionContract.CtCpJLookupExecutionContractAudit
			EXECUTION_AUDIT = PropellerArchiveCtCpJLookupExecutionContract.audit();

	@Test
	void auditBuildsResultEvidenceSlotsWithoutInventingLookupResults() {
		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Execution-Result-Seed-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("per-target evidence slots"));
		assertEquals(65, AUDIT.packetRowCount());
		assertEquals(8, AUDIT.sourceReferenceRowCount());
		assertEquals(36, AUDIT.seedRowCount());
		assertEquals(20, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(36, AUDIT.rows().size());
		assertEquals(4, AUDIT.scenarioSummaries().size());
	}

	@Test
	void currentScenarioKeepsAllResultSeedsUnavailable() {
		List<PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow> rows =
				PropellerArchiveCtCpJLookupExecutionResultSeed.rowsForScenario(
						"current_payload_and_surface_fit_blocked");

		assertEquals(9, rows.size());
		for (PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow row : rows) {
			assertFalse(row.lookupRunAuthorized());
			assertFalse(row.lookupResultEvidencePresent());
			assertEquals(0, row.observedNeighborRows());
			assertEquals(0.0, row.ctCoefficient(), 1.0e-12);
			assertEquals(0.0, row.cpCoefficient(), 1.0e-12);
			assertFalse(row.archiveCurveShapeGuardInherited());
			assertFalse(row.lookupExecutionAccepted());
			assertFalse(row.acceptanceResultReady());
			assertFalse(row.acceptanceResultPassed());
			assertFalse(row.compactReferenceExportAllowed());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("UNAVAILABLE", row.status());
			assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
					row.nextRequiredAction());
		}
	}

	@Test
	void authorizedScenarioCreatesPendingResultSeedsOnly() {
		PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedScenarioSummary summary =
				PropellerArchiveCtCpJLookupExecutionResultSeed.scenarioSummary("payload_and_surface_fit_ready");

		assertEquals(9, summary.seedRowCount());
		assertEquals(9, summary.authorizedSeedCount());
		assertEquals(0, summary.unavailableSeedCount());
		assertEquals(9, summary.pendingResultSeedCount());
		assertEquals(0, summary.evidencePresentSeedCount());
		assertEquals(0, summary.acceptanceResultReadyCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("PENDING_RESULT", summary.status());
		assertEquals("record-lookup-execution-result-evidence-before-acceptance", summary.nextRequiredAction());

		PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow heavy =
				seedFromAudit("payload_and_surface_fit_ready", "heavyLift", "high_domain_max_rpm");
		assertTrue(heavy.lookupRunAuthorized());
		assertFalse(heavy.lookupResultEvidencePresent());
		assertFalse(heavy.acceptanceResultReady());
		assertEquals(2, heavy.minimumNeighborRowsRequired());
		assertEquals(9, heavy.negativeThrustTailExecutionInputRowCount());
		assertTrue(heavy.performanceOnlyCandidate());
		assertEquals("PENDING_RESULT", heavy.status());
	}

	@Test
	void reviewedExecutionResultCanSeedAcceptanceWhenTargetAndGuardsMatch() {
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow =
				runRow("payload_and_surface_fit_ready", "apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult result =
				executionScenario("synthetic_mid_bilinear_pass");

		PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow seed =
				PropellerArchiveCtCpJLookupExecutionResultSeed.seed(runRow, result);

		assertTrue(seed.lookupRunAuthorized());
		assertTrue(seed.lookupResultEvidencePresent());
		assertEquals(4, seed.observedNeighborRows());
		assertEquals(0.046, seed.minCpCoefficient(), 1.0e-12);
		assertTrue(seed.archiveCurveShapeGuardInherited());
		assertTrue(seed.lookupExecutionAccepted());
		assertEquals("ACCEPTED", seed.lookupExecutionStatus());
		assertTrue(seed.acceptanceResultReady());
		assertTrue(seed.acceptanceResultPassed());
		assertFalse(seed.compactReferenceExportAllowed());
		assertFalse(seed.runtimeCouplingAllowed());
		assertFalse(seed.gameplayAutoApplyAllowed());
		assertEquals("READY", seed.status());
		assertEquals("feed-lookup-execution-results-into-acceptance-gate", seed.nextRequiredAction());

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult acceptance =
				PropellerArchiveCtCpJLookupExecutionResultSeed.acceptanceResult(seed);
		assertTrue(acceptance.passed());
		assertEquals("apDrone", acceptance.presetName());
		assertEquals("mid_domain_mid_rpm", acceptance.caseName());
		assertEquals(4, acceptance.observedNeighborRows());
	}

	@Test
	void failedExecutionResultStaysBlockedBeforeAcceptance() {
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow =
				runRow("payload_and_surface_fit_ready", "apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult result =
				executionScenario("synthetic_missing_neighbor_blocked");

		PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow seed =
				PropellerArchiveCtCpJLookupExecutionResultSeed.seed(runRow, result);

		assertTrue(seed.lookupResultEvidencePresent());
		assertFalse(seed.lookupExecutionAccepted());
		assertFalse(seed.acceptanceResultReady());
		assertFalse(seed.acceptanceResultPassed());
		assertEquals("RESULT_BLOCKED", seed.status());
		assertEquals("fix-lookup-execution-result-before-acceptance", seed.nextRequiredAction());
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionResultSeed.acceptanceResult(seed));
	}

	@Test
	void summaryKeepsAuditAsEvidenceSlotsNotAcceptedResults() {
		PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedSummary summary = AUDIT.summary();

		assertEquals(4, summary.scenarioCount());
		assertEquals(36, summary.seedRowCount());
		assertEquals(9, summary.currentUnavailableSeedCount());
		assertEquals(0, summary.currentPendingResultSeedCount());
		assertEquals(0, summary.currentAcceptanceResultReadyCount());
		assertEquals(9, summary.maxPendingResultSeedCount());
		assertEquals(0, summary.maxAcceptanceResultReadyCount());
		assertEquals(0, summary.evidencePresentSeedCount());
		assertEquals(0, summary.acceptanceResultReadyCount());
		assertEquals(0, summary.failedResultSeedCount());
		assertEquals(0, summary.compactReferenceExportAllowedCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("payload_and_surface_fit_ready", summary.firstPendingScenario());
		assertEquals("", summary.firstReadyScenario());
		assertEquals("UNAVAILABLE", summary.currentStatus());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				summary.nextRequiredAction());
	}

	@Test
	void rejectsUnknownTargetsAndMismatchedResultEvidence() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionResultSeed.rowsForScenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionResultSeed.scenarioSummary("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionResultSeed.row(
						"payload_and_surface_fit_ready", "apDrone", "missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionResultSeed.seed(null));
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow =
				runRow("payload_and_surface_fit_ready", "heavyLift", "mid_domain_mid_rpm");
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionResultSeed.seed(runRow,
						executionScenario("synthetic_mid_bilinear_pass")));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionResultSeed.acceptanceResult(
						seedFromAudit("payload_and_surface_fit_ready", "apDrone", "mid_domain_mid_rpm")));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_execution_result_seed_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_result_seed,current_payload_and_surface_fit_blocked,UNAVAILABLE,1,racingQuad,static_anchor_low_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_result_seed,payload_and_surface_fit_ready,PENDING_RESULT,9,heavyLift,high_domain_max_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_result_seed_summary,all,UNAVAILABLE,max_pending_result_seed_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_result_seed_method,result_seed_rule,READY,method,")));
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

	private static PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow seedFromAudit(
			String scenarioName,
			String presetName,
			String caseName
	) {
		return AUDIT.rows()
				.stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_execution_result_seed_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
