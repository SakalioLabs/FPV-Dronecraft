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

class PropellerArchiveCtCpJOpenFoamDimensionalResidualContractTest {
	private static final String REVIEWED_CASE_SHA =
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
	private static final double REFERENCE_THRUST_NEWTONS = 10.0;
	private static final double REFERENCE_SHAFT_POWER_WATTS = 20.0;
	private static final double REFERENCE_SHAFT_TORQUE_NEWTON_METERS = 1.0;
	private static final double REFERENCE_INDUCED_VELOCITY_METERS_PER_SECOND = 5.0;
	private static final double REFERENCE_MOMENTUM_POWER_WATTS = 30.0;
	private static final double COEFFICIENT_REFERENCE_CT = 0.100;
	private static final double COEFFICIENT_REFERENCE_CP = 0.050;
	private static final double COEFFICIENT_REFERENCE_ETA = 0.400;

	@Test
	void auditBuildsDimensionalResidualContractScenarios() {
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.CtCpJOpenFoamDimensionalResidualContractAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Residual-Contract-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("SI thrust"));
		assertEquals(52, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(23, audit.resultFieldRowCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(23, audit.fields().size());
		assertEquals(5, audit.scenarios().size());

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary current =
				scenario(audit, "current_dimensional_and_openfoam_blocked").summary();
		assertFalse(current.dimensionalResponseReferenceReady());
		assertFalse(current.openFoamCoefficientResultReady());
		assertFalse(current.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(5, current.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, current.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, current.maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertFalse(current.externalDimensionalExtractionReady());
		assertEquals(6, current.expectedDimensionalReferenceCount());
		assertEquals(2, current.readyDimensionalReferenceCount());
		assertEquals(2, current.archiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(9, current.negativeThrustTailReferenceCount());
		assertTrue(current.maxArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(current.maxArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(6, current.expectedOpenFoamDimensionalResultCaseCount());
		assertEquals(6, current.missingResultCount());
		assertEquals("dimensional-response-reference-not-ready", current.message());

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary missing =
				scenario(audit, "dimensional_reference_ready_openfoam_si_missing").summary();
		assertTrue(missing.dimensionalResponseReferenceReady());
		assertEquals(6, missing.archiveCurveShapeGuardInheritedReferenceCount());
		assertTrue(missing.openFoamCoefficientResultReady());
		assertTrue(missing.openFoamCoefficientLookupShapeGuardReady());
		assertTrue(missing.externalDimensionalExtractionReady());
		assertEquals(6, missing.readyDimensionalReferenceCount());
		assertEquals(6, missing.missingResultCount());
		assertEquals("openfoam-dimensional-results-missing", missing.message());

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary dimMissing =
				scenario(audit, "openfoam_si_ready_dimensional_reference_missing").summary();
		assertFalse(dimMissing.dimensionalResponseReferenceReady());
		assertEquals(2, dimMissing.archiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(6, dimMissing.observedResultCount());
		assertEquals(6, dimMissing.passedResultCount());
		assertFalse(dimMissing.openFoamDimensionalResidualReady());
		assertEquals("dimensional-response-reference-not-ready", dimMissing.message());

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary failed =
				scenario(audit, "dimensional_and_openfoam_si_residual_failed").summary();
		assertEquals(5, failed.passedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertEquals(0.075, failed.maxInducedVelocityResidualToReference(), 1.0e-12);
		assertFalse(failed.openFoamDimensionalResidualReady());
		assertEquals("openfoam-dimensional-residual-gate-failed", failed.message());

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary ready =
				scenario(audit, "dimensional_and_openfoam_si_ready").summary();
		assertEquals(6, ready.observedResultCount());
		assertEquals(6, ready.passedResultCount());
		assertEquals(0, ready.failedResultCount());
		assertTrue(ready.openFoamCoefficientLookupShapeGuardReady());
		assertEquals(5, ready.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertTrue(ready.maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(ready.maxOpenFoamCoefficientArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(5, ready.minObservedResultChannelCount());
		assertEquals(0.04, ready.maxThrustResidualToReference(), 1.0e-12);
		assertEquals(0.05, ready.maxShaftPowerResidualToReference(), 1.0e-12);
		assertEquals(0.05, ready.maxShaftTorqueResidualToReference(), 1.0e-12);
		assertEquals(0.03, ready.maxInducedVelocityResidualToReference(), 1.0e-12);
		assertEquals(0.06, ready.maxMomentumPowerResidualToReference(), 1.0e-12);
		assertEquals(5.0e-5, ready.maxSolverConvergenceResidual(), 1.0e-12);
		assertTrue(ready.openFoamDimensionalResidualReady());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("openfoam-dimensional-residual-contract-ready", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(6, audit.extrema().maxExpectedOpenFoamDimensionalResultCaseCount());
		assertEquals(6, audit.extrema().maxReadyDimensionalReferenceCount());
		assertEquals(6, audit.extrema().maxArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(9, audit.extrema().maxNegativeThrustTailReferenceCount());
		assertTrue(audit.extrema().maxArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(audit.extrema().maxArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(4, audit.extrema().openFoamCoefficientLookupShapeGuardReadyScenarioCount());
		assertEquals(5, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, audit.extrema().maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertTrue(audit.extrema().maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(audit.extrema().maxOpenFoamCoefficientArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(6, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(0.075, audit.extrema().maxInducedVelocityResidualToReference(), 1.0e-12);
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void reviewRequiresShapeGuardedCoefficientResultBeforeSiResiduals() {
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary blocked =
				PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, 6, blockedCoefficientResultSummary(), true,
						passingDimensionalResults(), "synthetic-si-ready-coefficient-shape-blocked");

		assertTrue(blocked.dimensionalResponseReferenceReady());
		assertFalse(blocked.openFoamCoefficientLookupShapeGuardReady());
		assertFalse(blocked.openFoamCoefficientResultReady());
		assertEquals(0, blocked.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertFalse(blocked.openFoamDimensionalResidualReady());
		assertEquals("openfoam-coefficient-lookup-shape-guard-not-ready", blocked.message());
	}

	@Test
	void fieldsDefineExternalDimensionalResultPayload() {
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResultField thrust =
				PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.field("thrust_residual_to_reference");
		assertEquals("ratio", thrust.unit());
		assertTrue(thrust.required());
		assertTrue(thrust.downstreamUse().contains("SI thrust"));
		assertFalse(thrust.runtimeCouplingAllowed());
		assertFalse(thrust.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResultField induced =
				PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.field(
						"induced_velocity_residual_to_reference");
		assertTrue(induced.downstreamUse().contains("wake"));

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResultField cfdTorque =
				PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.field(
						"cfd_shaft_torque_newton_meters");
		assertEquals("N*m", cfdTorque.unit());
		assertTrue(cfdTorque.downstreamUse().contains("reaction-torque"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.field("missing"));
	}

	@Test
	void resultRowsValidateDimensionalResidualThresholdsAndRunnableTargets() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalCompactResult pass =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5);
		assertTrue(pass.passed());
		assertEquals("PASS", pass.status());
		assertEquals("external-openfoam-steady-rotor-cfd", pass.solverFamily());
		assertEquals(REVIEWED_CASE_SHA, pass.sourceCaseSha256());
		assertEquals("da4052 5.0x3.75", pass.meshGeometryId());
		assertEquals(apMid.queryAdvanceRatioJ(), pass.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(apMid.queryRpm(), pass.queryRpm(), 1.0e-9);
		assertEquals(REFERENCE_THRUST_NEWTONS, pass.referenceThrustNewtons(), 1.0e-12);
		assertEquals(10.4, pass.cfdThrustNewtons(), 1.0e-12);
		assertEquals(REFERENCE_SHAFT_POWER_WATTS, pass.referenceShaftPowerWatts(), 1.0e-12);
		assertEquals(21.0, pass.cfdShaftPowerWatts(), 1.0e-12);
		assertEquals(REFERENCE_INDUCED_VELOCITY_METERS_PER_SECOND,
				pass.referenceInducedVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(5.15, pass.cfdInducedVelocityMetersPerSecond(), 1.0e-12);

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalCompactResult fail =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.061, 0.06, 5.0e-5);
		assertFalse(fail.passed());
		assertEquals("FAIL", fail.status());

		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalCompactResult queryMismatch =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ() + 0.01, apMid.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5);
		assertFalse(queryMismatch.passed());
		assertEquals("FAIL", queryMismatch.status());

		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase heavy =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("heavyLift", "mid_domain_mid_rpm");
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(
						heavy, REVIEWED_CASE_SHA, heavy.queryAdvanceRatioJ(), heavy.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(
						apMid, "not-a-sha", apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(
						apMid, REVIEWED_CASE_SHA, Double.NaN, apMid.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(
						apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), -1,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> reviewedResult(
						apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 5,
						Double.NaN, 0.05, 0.05, 0.03, 0.06, 5.0e-5));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.result(
						apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(),
						REFERENCE_THRUST_NEWTONS, 10.4,
						REFERENCE_SHAFT_POWER_WATTS, 21.0,
						REFERENCE_SHAFT_TORQUE_NEWTON_METERS, 1.05,
						REFERENCE_INDUCED_VELOCITY_METERS_PER_SECOND, 5.15,
						REFERENCE_MOMENTUM_POWER_WATTS, 31.8,
						5, 0.02, 0.05, 0.05, 0.03, 0.06, 5.0e-5));
	}

	@Test
	void reviewRejectsInvalidInputs() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalCompactResult result =
				reviewedResult(apMid, REVIEWED_CASE_SHA, apMid.queryAdvanceRatioJ(), apMid.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, -1, true, true, List.of(result), "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, 6, true, true, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, 6, true, true, List.of(result), ""));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, 6, true, true, List.of(result, result), "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, 6, -1, 0, 0.0, 0.0, true, true, List.of(result), "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, 6, 6, -1, 0.0, 0.0, true, true, List.of(result), "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.review(
						true, 6, 6, 0, Double.NaN, 0.0, true, true, List.of(result), "source"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.CtCpJOpenFoamDimensionalResidualContractAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_residual_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_result_field,thrust_residual_to_reference,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_residual_scenario,current_dimensional_and_openfoam_blocked,BLOCKED,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_residual_scenario,dimensional_and_openfoam_si_ready,READY,true,")));
		assertTrue(lines.get(0).contains("openfoam_coefficient_lookup_shape_guard_ready"));
		assertTrue(lines.stream().anyMatch(line -> line.contains(
				"dimensional_and_openfoam_si_ready,READY,true,,boolean,6,6,true,5,1,9,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_residual_summary,all_scenarios,ready_scenario_count,,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_residual_summary,all_scenarios,max_archive_curve_shape_guard_inherited_reference_count,,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_residual_summary,all_scenarios,runtime_coupling_allowed_count,,0,count,")));
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualScenario scenario(
			PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.CtCpJOpenFoamDimensionalResidualContractAudit audit,
			String name
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalCompactResult
			reviewedResult(
					PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
					String sourceCaseSha256,
					double queryAdvanceRatioJ,
					double queryRpm,
					int resultChannelCount,
					double thrustResidualToReference,
					double shaftPowerResidualToReference,
					double shaftTorqueResidualToReference,
					double inducedVelocityResidualToReference,
					double momentumPowerResidualToReference,
					double solverConvergenceResidual
			) {
		return PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.result(
				target,
				sourceCaseSha256,
				queryAdvanceRatioJ,
				queryRpm,
				REFERENCE_THRUST_NEWTONS,
				cfdValue(REFERENCE_THRUST_NEWTONS, thrustResidualToReference),
				REFERENCE_SHAFT_POWER_WATTS,
				cfdValue(REFERENCE_SHAFT_POWER_WATTS, shaftPowerResidualToReference),
				REFERENCE_SHAFT_TORQUE_NEWTON_METERS,
				cfdValue(REFERENCE_SHAFT_TORQUE_NEWTON_METERS, shaftTorqueResidualToReference),
				REFERENCE_INDUCED_VELOCITY_METERS_PER_SECOND,
				cfdValue(REFERENCE_INDUCED_VELOCITY_METERS_PER_SECOND, inducedVelocityResidualToReference),
				REFERENCE_MOMENTUM_POWER_WATTS,
				cfdValue(REFERENCE_MOMENTUM_POWER_WATTS, momentumPowerResidualToReference),
				resultChannelCount,
				thrustResidualToReference,
				shaftPowerResidualToReference,
				shaftTorqueResidualToReference,
				inducedVelocityResidualToReference,
				momentumPowerResidualToReference,
				solverConvergenceResidual);
	}

	private static List<PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalCompactResult>
			passingDimensionalResults() {
		return PropellerArchiveCtCpJOpenFoamValidationPlan.audit()
				.cases()
				.stream()
				.filter(PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase
						::postReviewOpenFoamCaseRunnable)
				.map(target -> reviewedResult(target, REVIEWED_CASE_SHA,
						target.queryAdvanceRatioJ(), target.queryRpm(), 5,
						0.04, 0.05, 0.05, 0.03, 0.06, 5.0e-5))
				.toList();
	}

	private static PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary
			blockedCoefficientResultSummary() {
		return PropellerArchiveCtCpJOpenFoamResultContract.review(
				true, true, true, passingCoefficientResults(), blockedLookupExecutionSummary(),
				"synthetic-coefficient-shape-guard-blocked");
	}

	private static List<PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult>
			passingCoefficientResults() {
		return PropellerArchiveCtCpJOpenFoamValidationPlan.audit()
				.cases()
				.stream()
				.filter(PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase
						::postReviewOpenFoamCaseRunnable)
				.map(target -> reviewedCoefficientResult(target,
						target.maxCtResidual() * 0.50,
						target.maxCpResidual() * 0.50,
						target.staticAnchorCase() ? 0.0 : target.maxEtaResidual() * 0.50))
				.toList();
	}

	private static PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamCompactResult reviewedCoefficientResult(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			double ctResidualToWindTunnel,
			double cpResidualToWindTunnel,
			double etaResidualToWindTunnel
	) {
		double etaReference = target.staticAnchorCase() ? 0.0 : COEFFICIENT_REFERENCE_ETA;
		return PropellerArchiveCtCpJOpenFoamResultContract.result(
				target,
				REVIEWED_CASE_SHA,
				target.queryAdvanceRatioJ(),
				target.queryRpm(),
				COEFFICIENT_REFERENCE_CT,
				cfdValue(COEFFICIENT_REFERENCE_CT, ctResidualToWindTunnel),
				COEFFICIENT_REFERENCE_CP,
				cfdValue(COEFFICIENT_REFERENCE_CP, cpResidualToWindTunnel),
				etaReference,
				cfdValue(etaReference, etaResidualToWindTunnel),
				3,
				ctResidualToWindTunnel,
				cpResidualToWindTunnel,
				etaResidualToWindTunnel,
				5.0e-5);
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
							"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_residual_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
