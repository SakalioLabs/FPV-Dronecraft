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
	private static final String REVIEWED_CASE_SHA =
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
	private static final double REFERENCE_CT = 0.100;
	private static final double REFERENCE_CP = 0.050;
	private static final double REFERENCE_ETA = 0.400;

	@Test
	void auditBuildsOpenFoamResultContractScenarios() {
		PropellerArchiveCtCpJOpenFoamResultContract.CtCpJOpenFoamResultContractAudit audit =
				PropellerArchiveCtCpJOpenFoamResultContract.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Result-Contract-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("compact external CT/CP/eta coefficient values"));
		assertEquals(143, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(17, audit.resultFieldRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(25, audit.scenarioMetricRowCount());
		assertEquals(17, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(17, audit.fields().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary current =
				scenario(audit, "current_no_reviewed_import_no_openfoam_results").summary();
		assertFalse(current.reviewedArchiveImportReady());
		assertFalse(current.lookupExecutionArchiveCurveShapeGuardReady());
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
		assertTrue(ready.lookupExecutionArchiveCurveShapeGuardReady());
		assertEquals(5, ready.lookupExecutionArchiveCurveShapeGuardInheritedScenarioCount());
		assertEquals(1, ready.lookupExecutionArchiveCurveShapeGuardBlockedScenarioCount());
		assertEquals(9, ready.maxNegativeThrustTailExecutionInputRowCount());
		assertTrue(ready.maxArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(ready.maxArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
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
		assertEquals(3, audit.extrema().lookupExecutionArchiveCurveShapeGuardReadyScenarioCount());
		assertEquals(5, audit.extrema().maxLookupExecutionArchiveCurveShapeGuardInheritedScenarioCount());
		assertEquals(1, audit.extrema().maxLookupExecutionArchiveCurveShapeGuardBlockedScenarioCount());
		assertEquals(9, audit.extrema().maxNegativeThrustTailExecutionInputRowCount());
		assertTrue(audit.extrema().maxArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(audit.extrema().maxArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(0.10, audit.extrema().maxCtResidualToWindTunnel(), 1.0e-12);
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void reviewRequiresLookupExecutionShapeGuardBeforeCfdReady() {
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary blocked =
				PropellerArchiveCtCpJOpenFoamResultContract.review(
						true, true, true, passingResults(), blockedLookupExecutionSummary(),
						"synthetic-cfd-ready-shape-guard-blocked");

		assertFalse(blocked.lookupExecutionArchiveCurveShapeGuardReady());
		assertEquals(0, blocked.lookupExecutionArchiveCurveShapeGuardInheritedScenarioCount());
		assertEquals(1, blocked.lookupExecutionArchiveCurveShapeGuardBlockedScenarioCount());
		assertFalse(blocked.openFoamResultContractReady());
		assertEquals("lookup-execution-archive-curve-shape-guard-not-ready", blocked.message());
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

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultField cfdEta =
				PropellerArchiveCtCpJOpenFoamResultContract.field("cfd_efficiency_eta");
		assertEquals("ratio", cfdEta.unit());
		assertTrue(cfdEta.downstreamUse().contains("eta"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.field("missing"));
	}

	@Test
	void resultRowsValidateResidualThresholdsAndRunnableTargets() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult pass =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 3,
						0.04, 0.05, 0.04, 5.0e-5);
		assertTrue(pass.passed());
		assertEquals("PASS", pass.status());
		assertEquals("external-openfoam-steady-rotor-cfd", pass.solverFamily());
		assertEquals(REVIEWED_CASE_SHA, pass.sourceCaseSha256());
		assertEquals("da4052 5.0x3.75", pass.meshGeometryId());
		assertEquals(apMid.queryAdvanceRatioJ(), pass.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(apMid.queryRpm(), pass.queryRpm(), 1.0e-9);
		assertEquals(REFERENCE_CT, pass.referenceThrustCoefficientCt(), 1.0e-12);
		assertEquals(0.104, pass.cfdThrustCoefficientCt(), 1.0e-12);
		assertEquals(REFERENCE_CP, pass.referencePowerCoefficientCp(), 1.0e-12);
		assertEquals(0.0525, pass.cfdPowerCoefficientCp(), 1.0e-12);
		assertEquals(REFERENCE_ETA, pass.referenceEfficiencyEta(), 1.0e-12);
		assertEquals(0.416, pass.cfdEfficiencyEta(), 1.0e-12);

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult fail =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 3,
						0.081, 0.05, 0.04, 5.0e-5);
		assertFalse(fail.passed());
		assertEquals("FAIL", fail.status());

		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult queryMismatch =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ() + 0.01, apMid.queryRpm(), 3,
						0.04, 0.05, 0.04, 5.0e-5);
		assertFalse(queryMismatch.passed());
		assertEquals("FAIL", queryMismatch.status());

		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase heavy =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("heavyLift", "mid_domain_mid_rpm");
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(heavy, REVIEWED_CASE_SHA, heavy.queryAdvanceRatioJ(), heavy.queryRpm(), 3,
						0.04, 0.05, 0.04, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(apMid, "not-a-sha", apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 3,
						0.04, 0.05, 0.04, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(apMid, REVIEWED_CASE_SHA, Double.NaN, apMid.queryRpm(), 3,
						0.04, 0.05, 0.04, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), -1,
						0.04, 0.05, 0.04, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 3,
						Double.NaN, 0.05, 0.04, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamResultContract.result(apMid, REVIEWED_CASE_SHA,
						apMid.queryAdvanceRatioJ(), apMid.queryRpm(),
						REFERENCE_CT, 0.104,
						REFERENCE_CP, 0.0525,
						REFERENCE_ETA, 0.416,
						3, 0.02, 0.05, 0.04, 5.0e-5));
	}

	@Test
	void reviewRejectsInvalidInputs() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult result =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 3,
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
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_source,lookup_execution_contract,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_scenario,synthetic_openfoam_results_all_pass,lookup_execution_archive_curve_shape_guard_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_scenario,current_no_reviewed_import_no_openfoam_results,missing_result_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_scenario,synthetic_openfoam_results_all_pass,openfoam_result_contract_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_summary,all_scenarios,max_expected_openfoam_result_case_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_result_summary,all_scenarios,max_lookup_execution_archive_curve_shape_guard_inherited_scenario_count,5,count,")));
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

	private static PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult reviewedResult(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			String sourceCaseSha256,
			double queryAdvanceRatioJ,
			double queryRpm,
			int resultChannelCount,
			double ctResidualToWindTunnel,
			double cpResidualToWindTunnel,
			double etaResidualToWindTunnel,
			double solverConvergenceResidual
	) {
		return PropellerArchiveCtCpJOpenFoamResultContract.result(
				target,
				sourceCaseSha256,
				queryAdvanceRatioJ,
				queryRpm,
				REFERENCE_CT,
				cfdValue(REFERENCE_CT, ctResidualToWindTunnel),
				REFERENCE_CP,
				cfdValue(REFERENCE_CP, cpResidualToWindTunnel),
				REFERENCE_ETA,
				cfdValue(REFERENCE_ETA, etaResidualToWindTunnel),
				resultChannelCount,
				ctResidualToWindTunnel,
				cpResidualToWindTunnel,
				etaResidualToWindTunnel,
				solverConvergenceResidual);
	}

	private static List<PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult> passingResults() {
		return PropellerArchiveCtCpJOpenFoamValidationPlan.audit()
				.cases()
				.stream()
				.filter(PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase
						::postReviewOpenFoamCaseRunnable)
				.map(target -> reviewedResult(target, REVIEWED_CASE_SHA,
						target.queryAdvanceRatioJ(), target.queryRpm(), 3,
						target.maxCtResidual() * 0.50,
						target.maxCpResidual() * 0.50,
						target.staticAnchorCase() ? 0.0 : target.maxEtaResidual() * 0.50,
						5.0e-5))
				.toList();
	}

	private static PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionSummary
			blockedLookupExecutionSummary() {
		return new PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionSummary(
				1,
				0,
				1,
				1,
				0,
				0,
				0,
				0,
				0,
				1,
				0,
				0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0,
				0,
				"carry-archive-curve-shape-guard-into-lookup-execution");
	}

	private static double cfdValue(double referenceValue, double residual) {
		return referenceValue * (1.0 + residual);
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
