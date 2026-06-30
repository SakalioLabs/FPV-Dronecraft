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

class PropellerArchiveCtCpJOpenFoamResultContractTest {
	@Test
	void auditBuildsOpenFoamResultContractScenarios() {
		PropellerArchiveCtCpJOpenFoamResultContract.CtCpJOpenFoamResultContractAudit audit =
				PropellerArchiveCtCpJOpenFoamResultContract.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Result-Contract-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("compact external CT/CP/eta residual summaries"));
		assertEquals(109, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(14, audit.resultFieldRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(19, audit.scenarioMetricRowCount());
		assertEquals(11, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(14, audit.fields().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary current =
				scenario(audit, "current_no_reviewed_import_no_openfoam_results").summary();
		assertFalse(current.reviewedArchiveImportReady());
		assertEquals(9, current.expectedValidationCaseCount());
		assertEquals(6, current.expectedOpenFoamResultCaseCount());
		assertEquals(6, current.missingResultCount());
		assertEquals("openfoam-prerequisites-not-ready", current.message());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary missing =
				scenario(audit, "reviewed_import_openfoam_ready_results_missing").summary();
		assertTrue(missing.reviewedArchiveImportReady());
		assertTrue(missing.externalCaseTemplateReady());
		assertTrue(missing.resultExtractionReady());
		assertEquals(6, missing.missingResultCount());
		assertEquals("openfoam-results-missing", missing.message());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary ready =
				scenario(audit, "synthetic_openfoam_results_all_pass").summary();
		assertEquals(6, ready.observedResultCount());
		assertEquals(6, ready.passedResultCount());
		assertEquals(0, ready.failedResultCount());
		assertEquals(3, ready.minObservedResultChannelCount());
		assertEquals(0.04, ready.maxCtResidualToWindTunnel(), 1.0e-12);
		assertEquals(0.05, ready.maxCpResidualToWindTunnel(), 1.0e-12);
		assertEquals(0.04, ready.maxEtaResidualToWindTunnel(), 1.0e-12);
		assertEquals(5.0e-5, ready.maxSolverConvergenceResidual(), 1.0e-12);
		assertTrue(ready.allExpectedResultsPresent());
		assertTrue(ready.allObservedResultsPassed());
		assertTrue(ready.openFoamResultContractReady());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("openfoam-result-contract-ready", ready.message());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary failed =
				scenario(audit, "synthetic_openfoam_result_failed").summary();
		assertEquals(5, failed.passedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertFalse(failed.openFoamResultContractReady());
		assertEquals("openfoam-residual-gate-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(6, audit.extrema().maxExpectedOpenFoamResultCaseCount());
		assertEquals(6, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(0.10, audit.extrema().maxCtResidualToWindTunnel(), 1.0e-12);
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void fieldsDefineCompactExternalResultPayload() {
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultField ct =
				PropellerArchiveCtCpJOpenFoamResultContract.field("ct_residual_to_wind_tunnel");
		assertEquals("ratio", ct.unit());
		assertTrue(ct.required());
		assertTrue(ct.downstreamUse().contains("thrust coefficient"));
		assertFalse(ct.runtimeCouplingAllowed());
		assertFalse(ct.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultField convergence =
				PropellerArchiveCtCpJOpenFoamResultContract.field("solver_convergence_residual");
		assertEquals("external solver convergence summary", convergence.source());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.field("missing"));
	}

	@Test
	void resultRowsValidateResidualThresholdsAndRunnableTargets() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult pass =
				PropellerArchiveCtCpJOpenFoamResultContract.result(apMid, 3,
						0.04, 0.05, 0.04, 5.0e-5);
		assertTrue(pass.passed());
		assertEquals("PASS", pass.status());
		assertEquals("external-openfoam-steady-rotor-cfd", pass.solverFamily());
		assertEquals("da4052 5.0x3.75", pass.meshGeometryId());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult fail =
				PropellerArchiveCtCpJOpenFoamResultContract.result(apMid, 3,
						0.081, 0.05, 0.04, 5.0e-5);
		assertFalse(fail.passed());
		assertEquals("FAIL", fail.status());

		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase heavy =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("heavyLift", "mid_domain_mid_rpm");
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.result(heavy, 3,
						0.04, 0.05, 0.04, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.result(apMid, -1,
						0.04, 0.05, 0.04, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.result(apMid, 3,
						Double.NaN, 0.05, 0.04, 5.0e-5));
	}

	@Test
	void reviewRejectsInvalidInputs() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult result =
				PropellerArchiveCtCpJOpenFoamResultContract.result(apMid, 3,
						0.04, 0.05, 0.04, 5.0e-5);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.review(
						true, true, true, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.review(
						true, true, true, List.of(result), ""));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.review(
						true, true, true, List.of(result, result), "source"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamResultContract.CtCpJOpenFoamResultContractAudit audit =
				PropellerArchiveCtCpJOpenFoamResultContract.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_result_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_field,ct_residual_to_wind_tunnel,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_scenario,current_no_reviewed_import_no_openfoam_results,missing_result_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_scenario,synthetic_openfoam_results_all_pass,openfoam_result_contract_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_summary,all_scenarios,max_expected_openfoam_result_case_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractScenario scenario(
			PropellerArchiveCtCpJOpenFoamResultContract.CtCpJOpenFoamResultContractAudit audit,
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
							"docs/data/propeller_archive_ct_cp_j_openfoam_result_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
