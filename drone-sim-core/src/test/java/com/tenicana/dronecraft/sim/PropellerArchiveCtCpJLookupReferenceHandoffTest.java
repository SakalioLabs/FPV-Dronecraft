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

class PropellerArchiveCtCpJLookupReferenceHandoffTest {
	@Test
	void auditBuildsLookupReferenceHandoffScenarios() {
		PropellerArchiveCtCpJLookupReferenceHandoff.CtCpJLookupReferenceHandoffAudit audit =
				PropellerArchiveCtCpJLookupReferenceHandoff.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Reference-Handoff-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("reviewed payload shape"));
		assertTrue(audit.caveat().contains("handoff-aware lookup execution"));
		assertEquals(111, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(12, audit.referenceFieldRowCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(16, audit.scenarioMetricRowCount());
		assertEquals(11, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(12, audit.fields().size());
		assertEquals(5, audit.scenarios().size());

		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary current =
				find(audit.scenarios(), "current_acceptance_blocked").summary();
		assertFalse(current.lookupAcceptanceReady());
		assertFalse(current.lookupExecutionContractReady());
		assertFalse(current.referenceMaterialExportAllowed());
		assertEquals(9, current.expectedReferenceRowCount());
		assertEquals(12, current.observedReferenceFieldCount());
		assertEquals(0, current.acceptedLookupTargetCount());
		assertEquals(9, current.blockedLookupTargetCount());
		assertEquals("lookup-acceptance-not-ready", current.message());

		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary executionBlocked =
				find(audit.scenarios(), "acceptance_execution_blocked").summary();
		assertFalse(executionBlocked.lookupAcceptanceReady());
		assertFalse(executionBlocked.lookupExecutionContractReady());
		assertEquals(9, executionBlocked.blockedLookupTargetCount());
		assertEquals("lookup-execution-contract-not-ready", executionBlocked.message());

		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary reviewMissing =
				find(audit.scenarios(), "acceptance_ready_reference_review_missing").summary();
		assertTrue(reviewMissing.lookupAcceptanceReady());
		assertTrue(reviewMissing.lookupExecutionContractReady());
		assertFalse(reviewMissing.compactReferenceReviewed());
		assertFalse(reviewMissing.referenceMaterialExportAllowed());
		assertEquals("lookup-reference-review-missing", reviewMissing.message());

		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary ready =
				find(audit.scenarios(), "acceptance_ready_reference_reviewed").summary();
		assertTrue(ready.lookupAcceptanceReady());
		assertTrue(ready.lookupExecutionContractReady());
		assertTrue(ready.compactReferenceReviewed());
		assertTrue(ready.referenceMaterialExportAllowed());
		assertEquals(9, ready.performanceReferenceRowAvailableCount());
		assertEquals(6, ready.fullSimulationReferenceRowAvailableCount());
		assertEquals(3, ready.performanceOnlyReferenceRowAvailableCount());
		assertEquals("compact-propeller-ct-cp-j-lookup-reference", ready.referencePayloadKind());
		assertEquals("lookup-reference-material-ready", ready.message());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary failed =
				find(audit.scenarios(), "reference_reviewed_acceptance_failed").summary();
		assertFalse(failed.lookupAcceptanceReady());
		assertTrue(failed.lookupExecutionContractReady());
		assertEquals(8, failed.acceptedLookupTargetCount());
		assertEquals(1, failed.blockedLookupTargetCount());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(1, audit.extrema().lookupExecutionBlockedScenarioCount());
		assertEquals(1, audit.extrema().referenceMaterialExportAllowedCount());
		assertEquals(9, audit.extrema().maxAcceptedLookupTargetCount());
		assertEquals(9, audit.extrema().maxBlockedLookupTargetCount());
		assertEquals(9, audit.extrema().maxPerformanceReferenceRowAvailableCount());
		assertEquals(6, audit.extrema().maxFullSimulationReferenceRowAvailableCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void fieldsDefineStableLookupReferencePayload() {
		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceField ct =
				PropellerArchiveCtCpJLookupReferenceHandoff.field("ct_cp_eta_reference");
		assertEquals("coefficient", ct.unit());
		assertTrue(ct.required());
		assertTrue(ct.source().contains("accepted lookup"));
		assertFalse(ct.runtimeCouplingAllowed());
		assertFalse(ct.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceField mu =
				PropellerArchiveCtCpJLookupReferenceHandoff.field("equivalent_project_mu");
		assertEquals("ratio", mu.unit());
		assertTrue(mu.downstreamUse().contains("advance-scale"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceHandoff.field("missing"));
	}

	@Test
	void handoffRejectsInvalidInputs() {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary accepted =
				findAcceptanceScenario(PropellerArchiveCtCpJLookupAcceptanceGate.audit().scenarios(),
						"synthetic_all_lookup_targets_pass").summary();
		List<PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceField> fields =
				PropellerArchiveCtCpJLookupReferenceHandoff.audit().fields();

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceHandoff.handoff(null, fields, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceHandoff.handoff(accepted, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceHandoff.handoff(accepted, fields, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupReferenceHandoff.CtCpJLookupReferenceHandoffAudit audit =
				PropellerArchiveCtCpJLookupReferenceHandoff.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_reference_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_handoff_field,ct_cp_eta_reference,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_handoff_scenario,current_acceptance_blocked,blocked_lookup_target_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_handoff_scenario,acceptance_execution_blocked,lookup_execution_contract_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_handoff_scenario,acceptance_ready_reference_reviewed,reference_material_export_allowed,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_handoff_summary,all_scenarios,lookup_execution_blocked_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_handoff_summary,all_scenarios,max_full_simulation_reference_row_available_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_handoff_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffScenario find(
			List<PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceScenario findAcceptanceScenario(
			List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_reference_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
