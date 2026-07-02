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

class PropellerArchiveCtCpJLookupExecutionTargetRunQueueTest {
	private static final PropellerArchiveCtCpJLookupExecutionTargetRunQueue
			.CtCpJLookupExecutionTargetRunQueueAudit AUDIT =
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.audit();

	@Test
	void auditBuildsPerTargetRunQueueFromReadinessAndAcceptanceTargets() {
		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Execution-Target-Run-Queue-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("per-target lab authorization rows"));
		assertEquals(62, AUDIT.packetRowCount());
		assertEquals(7, AUDIT.sourceReferenceRowCount());
		assertEquals(4, AUDIT.readinessScenarioCount());
		assertEquals(9, AUDIT.targetRowCount());
		assertEquals(36, AUDIT.targetRunRowCount());
		assertEquals(18, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(9, AUDIT.targets().size());
		assertEquals(36, AUDIT.rows().size());
		assertEquals(4, AUDIT.scenarioSummaries().size());
	}

	@Test
	void currentScenarioBlocksEveryTargetBeforeLookupExecution() {
		List<PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow> rows =
				PropellerArchiveCtCpJLookupExecutionTargetRunQueue.rowsForScenario(
						"current_payload_and_surface_fit_blocked");

		assertEquals(9, rows.size());
		for (PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow row : rows) {
			assertFalse(row.coefficientPayloadHandoffReady());
			assertFalse(row.scatteredSurfaceFitHandoffReady());
			assertFalse(row.lookupExecutionInputReady());
			assertEquals(0, row.payloadLookupInputSlotCount());
			assertEquals(0, row.exportedSurfaceFitInputRowCount());
			assertFalse(row.lookupRunAuthorized());
			assertFalse(row.lookupResultEvidencePresent());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
					row.nextRequiredAction());
			assertTrue(row.message().contains("coefficient payload review handoff blocked"));
		}

		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow first = rows.get(0);
		assertEquals(1, first.queueOrdinal());
		assertEquals("racingQuad", first.presetName());
		assertEquals("static_anchor_low_rpm", first.caseName());
		assertEquals(0.0, first.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(1, first.minimumNeighborRowsRequired());
		assertTrue(first.requiresStaticAnchorPreservation());
		assertTrue(first.fullSimulationCandidate());
		assertFalse(first.performanceOnlyCandidate());
	}

	@Test
	void partialReadinessScenariosDoNotAuthorizeTargets() {
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow payloadOnly =
				PropellerArchiveCtCpJLookupExecutionTargetRunQueue.row(
						"payload_ready_surface_fit_blocked", "apDrone", "mid_domain_mid_rpm");
		assertTrue(payloadOnly.coefficientPayloadHandoffReady());
		assertFalse(payloadOnly.scatteredSurfaceFitHandoffReady());
		assertEquals(21, payloadOnly.payloadLookupInputSlotCount());
		assertEquals(0, payloadOnly.exportedSurfaceFitInputRowCount());
		assertFalse(payloadOnly.lookupRunAuthorized());
		assertEquals("complete-scattered-surface-fit-execution-handoff-before-lookup-run",
				payloadOnly.nextRequiredAction());

		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow surfaceOnly =
				PropellerArchiveCtCpJLookupExecutionTargetRunQueue.row(
						"surface_fit_ready_payload_blocked", "heavyLift", "high_domain_max_rpm");
		assertFalse(surfaceOnly.coefficientPayloadHandoffReady());
		assertTrue(surfaceOnly.scatteredSurfaceFitHandoffReady());
		assertEquals(0, surfaceOnly.payloadLookupInputSlotCount());
		assertEquals(9, surfaceOnly.exportedSurfaceFitInputRowCount());
		assertEquals(9, surfaceOnly.archiveCurveShapeGuardReadyTargetCount());
		assertEquals(9, surfaceOnly.negativeThrustTailExecutionInputRowCount());
		assertFalse(surfaceOnly.lookupRunAuthorized());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				surfaceOnly.nextRequiredAction());
	}

	@Test
	void allReadyScenarioAuthorizesAllLabTargetsWithoutResultOrRuntimeAuthority() {
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunScenarioSummary summary =
				PropellerArchiveCtCpJLookupExecutionTargetRunQueue.scenarioSummary("payload_and_surface_fit_ready");
		assertEquals(9, summary.targetRunRowCount());
		assertEquals(9, summary.authorizedTargetCount());
		assertEquals(0, summary.blockedTargetCount());
		assertEquals(6, summary.fullSimulationAuthorizedTargetCount());
		assertEquals(3, summary.performanceOnlyAuthorizedTargetCount());
		assertEquals(3, summary.staticAnchorAuthorizedTargetCount());
		assertTrue(summary.coefficientPayloadHandoffReady());
		assertTrue(summary.scatteredSurfaceFitHandoffReady());
		assertTrue(summary.lookupExecutionInputReady());
		assertEquals(21, summary.payloadLookupInputSlotCount());
		assertEquals(9, summary.exportedSurfaceFitInputRowCount());
		assertEquals(9, summary.archiveCurveShapeGuardReadyTargetCount());
		assertEquals(9, summary.negativeThrustTailExecutionInputRowCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("AUTHORIZED", summary.status());
		assertEquals("execute-authorized-lab-lookup-targets-with-reviewed-ct-cp-j-payloads",
				summary.nextRequiredAction());

		for (PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow row
				: PropellerArchiveCtCpJLookupExecutionTargetRunQueue.rowsForScenario(
						"payload_and_surface_fit_ready")) {
			assertTrue(row.lookupRunAuthorized());
			assertFalse(row.lookupResultEvidencePresent());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("AUTHORIZED", row.status());
			assertEquals("execute-authorized-lab-lookup-targets-with-reviewed-ct-cp-j-payloads",
					row.nextRequiredAction());
		}

		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow heavy =
				PropellerArchiveCtCpJLookupExecutionTargetRunQueue.row(
						"payload_and_surface_fit_ready", "heavyLift", "mid_domain_mid_rpm");
		assertFalse(heavy.fullSimulationCandidate());
		assertTrue(heavy.performanceOnlyCandidate());
		assertEquals("performance-only-ct-cp-j-lookup-reference", heavy.downstreamUse());
	}

	@Test
	void summaryKeepsCurrentStateBlockedAndOnlySyntheticAllReadyAuthorized() {
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunQueueSummary summary =
				AUDIT.summary();

		assertEquals(4, summary.scenarioCount());
		assertEquals(36, summary.targetRunRowCount());
		assertEquals(0, summary.currentAuthorizedTargetCount());
		assertEquals(9, summary.currentBlockedTargetCount());
		assertEquals(0, summary.currentPayloadLookupInputSlotCount());
		assertEquals(0, summary.currentSurfaceFitExportedInputRowCount());
		assertEquals(9, summary.maxAuthorizedTargetCount());
		assertEquals(6, summary.maxFullSimulationAuthorizedTargetCount());
		assertEquals(3, summary.maxPerformanceOnlyAuthorizedTargetCount());
		assertEquals(3, summary.maxStaticAnchorAuthorizedTargetCount());
		assertEquals(9, summary.maxNegativeThrustTailExecutionInputRowCount());
		assertEquals(1, summary.authorizedScenarioCount());
		assertEquals(3, summary.blockedScenarioCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("payload_and_surface_fit_ready", summary.firstAuthorizedScenario());
		assertEquals("BLOCKED", summary.currentStatus());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				summary.nextRequiredAction());
	}

	@Test
	void rejectsUnknownScenariosAndNullAuditInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionTargetRunQueue.rowsForScenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionTargetRunQueue.scenarioSummary("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionTargetRunQueue.row(
						"payload_and_surface_fit_ready", "apDrone", "missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionTargetRunQueue.audit(null,
						PropellerArchiveCtCpJLookupAcceptanceGate.targets()));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionTargetRunQueue.audit(
						PropellerArchiveCtCpJLookupExecutionInputReadinessGate.audit(), null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_execution_target_run_queue_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_target_run,current_payload_and_surface_fit_blocked,BLOCKED,1,racingQuad,static_anchor_low_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_target_run,payload_and_surface_fit_ready,AUTHORIZED,9,heavyLift,high_domain_max_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_target_run_summary,all,BLOCKED,max_authorized_target_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_target_run_method,target_run_queue_rule,READY,method,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_execution_target_run_queue_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
