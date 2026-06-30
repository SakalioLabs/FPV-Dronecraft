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

class PropellerArchiveCtCpJOpenFoamLookupSupportGateTest {
	private static final PropellerArchiveCtCpJOpenFoamLookupSupportGate.CtCpJOpenFoamLookupSupportGateAudit
			AUDIT = PropellerArchiveCtCpJOpenFoamLookupSupportGate.audit();
	private static final PropellerArchiveCtCpJLookupAcceptanceGate.CtCpJLookupAcceptanceAudit
			LOOKUP_AUDIT = PropellerArchiveCtCpJLookupAcceptanceGate.audit();
	private static final PropellerArchiveCtCpJOpenFoamResultContract.CtCpJOpenFoamResultContractAudit
			CFD_AUDIT = PropellerArchiveCtCpJOpenFoamResultContract.audit();
	private static final PropellerArchiveCtCpJOpenFoamSolverQualityContract
			.CtCpJOpenFoamSolverQualityContractAudit QUALITY_AUDIT =
					PropellerArchiveCtCpJOpenFoamSolverQualityContract.audit();

	@Test
	void auditCombinesLookupAcceptanceAndOpenFoamResults() {
		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Lookup-Support-Gate-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("cannot replace wind-tunnel acceptance"));
		assertTrue(AUDIT.caveat().contains("handoff-aware CT/CP/J lookup execution"));
		assertTrue(AUDIT.caveat().contains("compact solver-quality QA"));
		assertEquals(163, AUDIT.packetRowCount());
		assertEquals(10, AUDIT.sourceReferenceRowCount());
		assertEquals(6, AUDIT.scenarioSampleCount());
		assertEquals(23, AUDIT.scenarioMetricRowCount());
		assertEquals(14, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(6, AUDIT.scenarios().size());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary current =
				scenario(AUDIT, "current_lookup_and_cfd_blocked").summary();
		assertFalse(current.lookupAcceptanceReady());
		assertFalse(current.lookupExecutionContractReady());
		assertFalse(current.openFoamResultContractReady());
		assertFalse(current.openFoamSolverQualityContractReady());
		assertEquals(4, current.openFoamSolverQualityBlockerCount());
		assertEquals("review-openfoam-mesh-yplus-and-time-step-against-run-setup",
				current.openFoamSolverQualityNextRequiredAction());
		assertEquals(9, current.expectedLookupTargetCount());
		assertEquals(6, current.expectedOpenFoamResultCaseCount());
		assertEquals(0, current.acceptedLookupTargetCount());
		assertEquals(6, current.cfdMissingResultCount());
		assertEquals(0, current.cfdSupportedLookupTargetCount());
		assertEquals(3, current.cfdGeometryUnsupportedLookupTargetCount());
		assertEquals("lookup-acceptance-not-ready", current.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary executionBlocked =
				scenario(AUDIT, "lookup_execution_blocked_cfd_ready").summary();
		assertFalse(executionBlocked.lookupAcceptanceReady());
		assertFalse(executionBlocked.lookupExecutionContractReady());
		assertTrue(executionBlocked.openFoamResultContractReady());
		assertTrue(executionBlocked.openFoamSolverQualityContractReady());
		assertEquals(0, executionBlocked.openFoamSolverQualityBlockerCount());
		assertEquals(0, executionBlocked.cfdSupportedLookupTargetCount());
		assertEquals("lookup-execution-contract-not-ready", executionBlocked.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary missing =
				scenario(AUDIT, "lookup_ready_cfd_results_missing").summary();
		assertTrue(missing.lookupAcceptanceReady());
		assertTrue(missing.lookupExecutionContractReady());
		assertFalse(missing.openFoamResultContractReady());
		assertTrue(missing.openFoamSolverQualityContractReady());
		assertEquals(0, missing.openFoamSolverQualityBlockerCount());
		assertEquals(9, missing.acceptedLookupTargetCount());
		assertEquals(6, missing.cfdMissingResultCount());
		assertEquals("openfoam-results-missing", missing.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookupFailed =
				scenario(AUDIT, "cfd_ready_lookup_acceptance_failed").summary();
		assertFalse(lookupFailed.lookupAcceptanceReady());
		assertTrue(lookupFailed.lookupExecutionContractReady());
		assertTrue(lookupFailed.openFoamResultContractReady());
		assertTrue(lookupFailed.openFoamSolverQualityContractReady());
		assertEquals(8, lookupFailed.acceptedLookupTargetCount());
		assertEquals(1, lookupFailed.failedLookupTargetCount());
		assertEquals("lookup-acceptance-not-ready", lookupFailed.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary cfdFailed =
				scenario(AUDIT, "lookup_ready_cfd_residual_failed").summary();
		assertTrue(cfdFailed.lookupAcceptanceReady());
		assertTrue(cfdFailed.lookupExecutionContractReady());
		assertFalse(cfdFailed.openFoamResultContractReady());
		assertTrue(cfdFailed.openFoamSolverQualityContractReady());
		assertEquals(1, cfdFailed.cfdFailedResultCount());
		assertEquals("openfoam-residual-gate-failed", cfdFailed.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary ready =
				scenario(AUDIT, "lookup_and_cfd_ready").summary();
		assertTrue(ready.lookupAcceptanceReady());
		assertTrue(ready.lookupExecutionContractReady());
		assertTrue(ready.openFoamResultContractReady());
		assertTrue(ready.openFoamSolverQualityContractReady());
		assertEquals(0, ready.openFoamSolverQualityBlockerCount());
		assertEquals("openfoam-solver-quality-blockers-clear",
				ready.openFoamSolverQualityNextRequiredAction());
		assertTrue(ready.cfdLookupSupportReady());
		assertEquals(6, ready.cfdSupportedLookupTargetCount());
		assertEquals(3, ready.cfdGeometryUnsupportedLookupTargetCount());
		assertFalse(ready.referenceExportAuthorityAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("openfoam-lookup-support-ready", ready.message());

		assertEquals(6, AUDIT.extrema().scenarioCount());
		assertEquals(1, AUDIT.extrema().readyScenarioCount());
		assertEquals(5, AUDIT.extrema().blockedScenarioCount());
		assertEquals(1, AUDIT.extrema().lookupExecutionBlockedScenarioCount());
		assertEquals(9, AUDIT.extrema().maxAcceptedLookupTargetCount());
		assertEquals(6, AUDIT.extrema().maxCfdSupportedLookupTargetCount());
		assertEquals(3, AUDIT.extrema().maxCfdGeometryUnsupportedLookupTargetCount());
		assertEquals(6, AUDIT.extrema().maxCfdMissingResultCount());
		assertEquals(1, AUDIT.extrema().maxCfdFailedResultCount());
		assertEquals(4, AUDIT.extrema().maxOpenFoamSolverQualityBlockerCount());
		assertEquals(1, AUDIT.extrema().openFoamSolverQualityBlockerScenarioCount());
		assertEquals(0, AUDIT.extrema().referenceExportAuthorityAllowedCount());
		assertEquals(0, AUDIT.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, AUDIT.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void supportRejectsInvalidInputs() {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary lookup =
				LOOKUP_AUDIT.scenarios().stream()
						.filter(scenario -> "synthetic_all_lookup_targets_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary cfd =
				CFD_AUDIT.scenarios().stream()
						.filter(scenario -> "synthetic_openfoam_results_all_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary quality =
				QUALITY_AUDIT.scenarios().stream()
						.filter(scenario -> "synthetic_solver_quality_all_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary missingQuality =
				QUALITY_AUDIT.scenarios().stream()
						.filter(scenario -> "numerical_budget_ready_quality_missing".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(null, cfd, quality, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(lookup, null, quality, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(lookup, cfd, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(lookup, cfd, quality, ""));

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary qualityBlocked =
				PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(
						lookup, cfd, missingQuality, "solver-quality-missing");
		assertFalse(qualityBlocked.openFoamSolverQualityContractReady());
		assertEquals(1, qualityBlocked.openFoamSolverQualityBlockerCount());
		assertEquals("extract-compact-openfoam-solver-quality-summary",
				qualityBlocked.openFoamSolverQualityNextRequiredAction());
		assertFalse(qualityBlocked.cfdLookupSupportReady());
		assertEquals("openfoam-solver-quality-not-ready", qualityBlocked.message());
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_lookup_support_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,current_lookup_and_cfd_blocked,cfd_missing_result_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,current_lookup_and_cfd_blocked,openfoam_solver_quality_contract_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,current_lookup_and_cfd_blocked,openfoam_solver_quality_blocker_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,lookup_execution_blocked_cfd_ready,lookup_execution_contract_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,lookup_and_cfd_ready,openfoam_solver_quality_contract_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,lookup_and_cfd_ready,cfd_lookup_support_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,lookup_and_cfd_ready,reference_export_authority_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_summary,all_scenarios,lookup_execution_blocked_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_summary,all_scenarios,max_cfd_supported_lookup_target_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_summary,all_scenarios,max_openfoam_solver_quality_blocker_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportScenario scenario(
			PropellerArchiveCtCpJOpenFoamLookupSupportGate.CtCpJOpenFoamLookupSupportGateAudit audit,
			String name
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_lookup_support_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
