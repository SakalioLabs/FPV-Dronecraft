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

class PropellerArchiveCtCpJLookupExecutionInputReadinessGateTest {
	private static final PropellerArchiveCtCpJLookupExecutionInputReadinessGate
			.CtCpJLookupExecutionInputReadinessGateAudit AUDIT =
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.audit();

	@Test
	void auditCombinesPayloadAndSurfaceFitExecutionHandoffs() {
		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Execution-Input-Readiness-Gate-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("requires both reviewed coefficient payload handoff"));
		assertEquals(29, AUDIT.packetRowCount());
		assertEquals(7, AUDIT.sourceReferenceRowCount());
		assertEquals(4, AUDIT.readinessScenarioRowCount());
		assertEquals(17, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(4, AUDIT.scenarios().size());
		assertEquals("current_payload_and_surface_fit_blocked", AUDIT.scenarios().get(0).scenarioName());
	}

	@Test
	void currentScenarioKeepsBothInputChainsClosed() {
		PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario current =
				scenario("current_payload_and_surface_fit_blocked");

		assertFalse(current.coefficientPayloadHandoffReady());
		assertFalse(current.scatteredSurfaceFitHandoffReady());
		assertFalse(current.sourceRowsReviewed());
		assertFalse(current.scatteredSurfaceFitContractReady());
		assertEquals(21, current.expectedCoefficientPayloadSlotCount());
		assertEquals(0, current.payloadLookupInputSlotCount());
		assertEquals(9, current.expectedSurfaceFitTargetCount());
		assertEquals(0, current.exportedSurfaceFitInputRowCount());
		assertEquals(0, current.lookupExecutionTargetReadyCount());
		assertFalse(current.lookupExecutionInputReady());
		assertEquals("BLOCKED", current.status());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				current.nextRequiredAction());
	}

	@Test
	void payloadReadyCannotRunWithoutSurfaceFitHandoff() {
		PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario =
				scenario("payload_ready_surface_fit_blocked");

		assertTrue(scenario.coefficientPayloadHandoffReady());
		assertFalse(scenario.scatteredSurfaceFitHandoffReady());
		assertEquals(21, scenario.payloadLookupInputSlotCount());
		assertEquals(0, scenario.exportedSurfaceFitInputRowCount());
		assertEquals(0, scenario.lookupExecutionTargetReadyCount());
		assertFalse(scenario.lookupExecutionInputReady());
		assertEquals("complete-scattered-surface-fit-execution-handoff-before-lookup-run",
				scenario.nextRequiredAction());
		assertTrue(scenario.message().contains("scattered surface fit execution handoff blocked"));
	}

	@Test
	void surfaceFitReadyCannotRunWithoutPayloadHandoff() {
		PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario =
				scenario("surface_fit_ready_payload_blocked");

		assertFalse(scenario.coefficientPayloadHandoffReady());
		assertTrue(scenario.scatteredSurfaceFitHandoffReady());
		assertEquals(0, scenario.payloadLookupInputSlotCount());
		assertEquals(9, scenario.exportedSurfaceFitInputRowCount());
		assertEquals(9, scenario.archiveCurveShapeGuardReadyTargetCount());
		assertEquals(9, scenario.negativeThrustTailExecutionInputRowCount());
		assertEquals(6, scenario.fullSimulationExecutionInputRowCount());
		assertEquals(3, scenario.performanceOnlyExecutionInputRowCount());
		assertEquals(0, scenario.lookupExecutionTargetReadyCount());
		assertFalse(scenario.lookupExecutionInputReady());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				scenario.nextRequiredAction());
	}

	@Test
	void bothHandoffsReadyExposeLookupExecutionTargetsWithoutRuntimeAuthority() {
		PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario =
				scenario("payload_and_surface_fit_ready");

		assertTrue(scenario.coefficientPayloadHandoffReady());
		assertTrue(scenario.scatteredSurfaceFitHandoffReady());
		assertEquals(21, scenario.payloadLookupInputSlotCount());
		assertEquals(9, scenario.exportedSurfaceFitInputRowCount());
		assertEquals(9, scenario.lookupExecutionTargetReadyCount());
		assertTrue(scenario.lookupExecutionInputReady());
		assertFalse(scenario.runtimeCouplingAllowed());
		assertFalse(scenario.gameplayAutoApplyAllowed());
		assertEquals("READY", scenario.status());
		assertEquals("run-lookup-execution-contract-with-reviewed-payload-and-surface-fit-inputs",
				scenario.nextRequiredAction());
	}

	@Test
	void summaryKeepsCurrentStateBlockedAndReportsOnlySyntheticReady() {
		PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessSummary summary =
				AUDIT.summary();

		assertEquals(4, summary.scenarioCount());
		assertEquals(1, summary.readyScenarioCount());
		assertEquals(3, summary.blockedScenarioCount());
		assertEquals(21, summary.maxPayloadLookupInputSlotCount());
		assertEquals(9, summary.maxSurfaceFitExportedInputRowCount());
		assertEquals(9, summary.maxLookupExecutionTargetReadyCount());
		assertFalse(summary.currentCoefficientPayloadHandoffReady());
		assertFalse(summary.currentScatteredSurfaceFitHandoffReady());
		assertEquals(0, summary.currentPayloadLookupInputSlotCount());
		assertEquals(0, summary.currentSurfaceFitExportedInputRowCount());
		assertEquals(0, summary.currentLookupExecutionTargetReadyCount());
		assertEquals(9, summary.currentNegativeThrustTailExecutionInputRowCount());
		assertEquals("BLOCKED", summary.currentStatus());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("payload_and_surface_fit_ready", summary.firstReadyScenario());
	}

	@Test
	void rejectsUnknownReadinessScenario() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionInputReadinessGate.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_execution_input_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_input_readiness_gate,current_payload_and_surface_fit_blocked,BLOCKED,lookup_execution_input_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_input_readiness_gate,payload_and_surface_fit_ready,READY,lookup_execution_target_ready_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_input_readiness_gate_summary,all,BLOCKED,current_lookup_execution_target_ready_count,0,count,")));
	}

	private static PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario
			scenario(String scenarioName) {
		return AUDIT.scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_execution_input_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
