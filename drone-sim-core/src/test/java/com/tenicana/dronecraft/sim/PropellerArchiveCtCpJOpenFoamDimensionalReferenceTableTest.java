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

class PropellerArchiveCtCpJOpenFoamDimensionalReferenceTableTest {
	private static final List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> TARGETS =
			List.of(
					target("racingQuad", "static_anchor_low_rpm", 0.0, 1_477.8, true),
					target("racingQuad", "mid_domain_mid_rpm", 0.4064, 4_712.25, false),
					target("racingQuad", "high_domain_max_rpm", 0.73152, 7_946.7, false),
					target("apDrone", "static_anchor_low_rpm", 0.0, 1_477.8, true),
					target("apDrone", "mid_domain_mid_rpm", 0.4064, 4_712.25, false),
					target("apDrone", "high_domain_max_rpm", 0.73152, 7_946.7, false)
			);

	@Test
	void auditBuildsBlockedCurrentDimensionalReferenceRows() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				tableAudit("current_dimensional_support_blocked");

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Reference-Table-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("six geometry-backed SI CFD reference rows"));
		assertTrue(audit.caveat().contains("CT/CP/J lookup reference review readiness"));
		assertTrue(audit.caveat().contains("solver-quality QA state"));
		assertTrue(audit.caveat().contains("coefficient lookup shape-guard diagnostics"));
		assertTrue(audit.caveat().contains("inherited archive curve-shape diagnostics"));
		assertTrue(audit.caveat().contains("zero weights"));
		assertEquals(47, audit.packetRowCount());
		assertEquals(11, audit.sourceReferenceRowCount());
		assertEquals(6, audit.referenceRowCount());
		assertEquals(29, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(6, audit.rows().size());

		for (PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow row
				: audit.rows()) {
			assertFalse(row.lookupExecutionContractReady());
			assertFalse(row.dimensionalSupportReady());
			assertFalse(row.openFoamSolverQualityContractReady());
			assertEquals(4, row.openFoamSolverQualityBlockerCount());
			assertEquals("review-openfoam-mesh-yplus-and-time-step-against-run-setup",
					row.openFoamSolverQualityNextRequiredAction());
			assertFalse(row.openFoamCoefficientLookupShapeGuardReady());
			assertEquals(5, row.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
			assertEquals(1, row.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
			assertEquals(9, row.maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
			assertTrue(row.maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual()
					<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
			assertTrue(row.maxOpenFoamCoefficientArchiveCurveCtIncrease()
					<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
			assertEquals(2, row.archiveCurveShapeGuardInheritedReferenceCount());
			assertEquals(9, row.negativeThrustTailReferenceCount());
			assertTrue(row.maxArchiveCurveEtaFormulaResidual()
					<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
			assertTrue(row.maxArchiveCurveCtIncrease()
					<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
			assertFalse(row.archiveCurveShapeGuardComplete());
			assertFalse(row.dimensionalReferenceReviewed());
			assertFalse(row.referenceMaterialExportAllowed());
			assertFalse(row.openFoamDimensionalReferenceRowAvailable());
			assertEquals(0.0, row.thrustReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.shaftPowerReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.shaftTorqueReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.inducedVelocityReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.momentumPowerReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.residualReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("openfoam-dimensional-lookup-support-not-ready", row.message());
			assertEquals("compact-openfoam-dimensional-rotor-response-reference",
					row.referencePayloadKind());
		}

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow apMid =
				audit.rows()
						.stream()
						.filter(row -> row.presetName().equals("apDrone")
								&& row.caseName().equals("mid_domain_mid_rpm"))
						.findFirst()
						.orElseThrow();
		assertEquals(PropellerArchiveSourceFingerprint.ARCHIVE_SHA256, apMid.sourceArchiveSha256());
		assertEquals(PropellerArchiveCtCpJOpenFoamValidationPlan.SOLVER_FAMILY, apMid.solverFamily());
		assertEquals("da4052 5.0x3.75", apMid.meshGeometryId());
		assertEquals(0.4064, apMid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_712.25, apMid.queryRpm(), 1.0e-9);
		assertEquals(0.4064 / Math.PI, apMid.equivalentProjectMu(), 1.0e-12);
		assertFalse(apMid.staticAnchorReferenceRow());

		assertEquals(6, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().referenceRowAvailableCount());
		assertEquals(6, audit.extrema().blockedRowCount());
		assertEquals(2, audit.extrema().staticAnchorReferenceRowCount());
		assertEquals(0, audit.extrema().staticAnchorReferenceAvailableCount());
		assertEquals(0.73152, audit.extrema().maxQueryAdvanceRatioJ(), 1.0e-12);
		assertEquals(7_946.7, audit.extrema().maxQueryRpm(), 1.0e-9);
		assertEquals(0.0, audit.extrema().maxThrustReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxResidualReferenceWeight(), 1.0e-12);
		assertEquals(0, audit.extrema().lookupExecutionContractReadyCount());
		assertEquals(0, audit.extrema().dimensionalSupportReadyCount());
		assertEquals(0, audit.extrema().openFoamSolverQualityContractReadyCount());
		assertEquals(4, audit.extrema().maxOpenFoamSolverQualityBlockerCount());
		assertEquals(6, audit.extrema().openFoamSolverQualityBlockerRowCount());
		assertEquals(0, audit.extrema().openFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(5, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, audit.extrema().maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertTrue(audit.extrema().maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(audit.extrema().maxOpenFoamCoefficientArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(2, audit.extrema().maxArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(9, audit.extrema().maxNegativeThrustTailReferenceCount());
		assertTrue(audit.extrema().maxArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(audit.extrema().maxArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(0, audit.extrema().archiveCurveShapeGuardCompleteRowCount());
		assertEquals(0, audit.extrema().dimensionalReferenceReviewedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void reviewedDimensionalHandoffEnablesSixReferenceRows() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("dimensional_support_ready_reference_reviewed");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(handoff, TARGETS);

		assertEquals(6, audit.extrema().referenceRowAvailableCount());
		assertEquals(0, audit.extrema().blockedRowCount());
		assertEquals(2, audit.extrema().staticAnchorReferenceAvailableCount());
		assertEquals(1.0, audit.extrema().maxThrustReferenceWeight(), 1.0e-12);
		assertEquals(1.0, audit.extrema().maxResidualReferenceWeight(), 1.0e-12);
		assertEquals(6, audit.extrema().lookupExecutionContractReadyCount());
		assertEquals(6, audit.extrema().dimensionalSupportReadyCount());
		assertEquals(6, audit.extrema().openFoamSolverQualityContractReadyCount());
		assertEquals(0, audit.extrema().maxOpenFoamSolverQualityBlockerCount());
		assertEquals(0, audit.extrema().openFoamSolverQualityBlockerRowCount());
		assertEquals(6, audit.extrema().openFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(5, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount());
		assertEquals(1, audit.extrema().maxOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount());
		assertEquals(9, audit.extrema().maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
		assertTrue(audit.extrema().maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(audit.extrema().maxOpenFoamCoefficientArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(6, audit.extrema().maxArchiveCurveShapeGuardInheritedReferenceCount());
		assertEquals(0, audit.extrema().maxNegativeThrustTailReferenceCount());
		assertEquals(0.0, audit.extrema().maxArchiveCurveEtaFormulaResidual());
		assertEquals(0.0, audit.extrema().maxArchiveCurveCtIncrease());
		assertEquals(6, audit.extrema().archiveCurveShapeGuardCompleteRowCount());
		assertEquals(6, audit.extrema().dimensionalReferenceReviewedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());

		for (PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow row
				: audit.rows()) {
			assertTrue(row.lookupExecutionContractReady());
			assertTrue(row.dimensionalSupportReady());
			assertTrue(row.openFoamSolverQualityContractReady());
			assertEquals(0, row.openFoamSolverQualityBlockerCount());
			assertEquals("openfoam-solver-quality-blockers-clear",
					row.openFoamSolverQualityNextRequiredAction());
			assertTrue(row.openFoamCoefficientLookupShapeGuardReady());
			assertEquals(5, row.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
			assertEquals(1, row.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
			assertEquals(9, row.maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
			assertTrue(row.maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual()
					<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
			assertTrue(row.maxOpenFoamCoefficientArchiveCurveCtIncrease()
					<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
			assertEquals(6, row.archiveCurveShapeGuardInheritedReferenceCount());
			assertEquals(0, row.negativeThrustTailReferenceCount());
			assertEquals(0.0, row.maxArchiveCurveEtaFormulaResidual());
			assertEquals(0.0, row.maxArchiveCurveCtIncrease());
			assertTrue(row.archiveCurveShapeGuardComplete());
			assertTrue(row.dimensionalReferenceReviewed());
			assertTrue(row.referenceMaterialExportAllowed());
			assertTrue(row.openFoamDimensionalReferenceRowAvailable());
			assertEquals(1.0, row.thrustReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.shaftPowerReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.shaftTorqueReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.inducedVelocityReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.momentumPowerReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.residualReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("AVAILABLE", row.status());
			assertEquals("openfoam-dimensional-reference-row-available", row.message());
		}
	}

	@Test
	void materializationGateBlocksReadyOpenFoamRowsUntilLookupReferenceReviewIsReady() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario materialization =
						materialization("openfoam_ready_lookup_reference_review_missing");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("dimensional_support_ready_reference_reviewed");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(materialization, handoff, TARGETS);

		assertEquals(0, audit.extrema().referenceRowAvailableCount());
		assertEquals(6, audit.extrema().blockedRowCount());
		assertEquals(6, audit.extrema().dimensionalSupportReadyCount());
		assertEquals(6, audit.extrema().openFoamSolverQualityContractReadyCount());
		assertEquals(6, audit.extrema().openFoamCoefficientLookupShapeGuardReadyRowCount());
		assertEquals(6, audit.extrema().archiveCurveShapeGuardCompleteRowCount());
		assertEquals(6, audit.extrema().dimensionalReferenceReviewedCount());
		assertEquals(0.0, audit.extrema().maxThrustReferenceWeight(), 1.0e-12);

		for (PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow row
				: audit.rows()) {
			assertTrue(row.dimensionalSupportReady());
			assertTrue(row.openFoamSolverQualityContractReady());
			assertTrue(row.openFoamCoefficientLookupShapeGuardReady());
			assertTrue(row.archiveCurveShapeGuardComplete());
			assertTrue(row.dimensionalReferenceReviewed());
			assertFalse(row.referenceMaterialExportAllowed());
			assertFalse(row.openFoamDimensionalReferenceRowAvailable());
			assertEquals("ct-cp-j-lookup-reference-review-not-ready", row.message());
			assertEquals("openfoam_ready_lookup_reference_review_missing", row.sourceRuntimeInfo());
		}
	}

	@Test
	void materializationGateOpensRowsOnlyWhenLookupAndOpenFoamAreReady() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario materialization =
						materialization("lookup_reference_and_openfoam_ready");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("dimensional_support_ready_reference_reviewed");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(materialization, handoff, TARGETS);

		assertEquals(6, audit.extrema().referenceRowAvailableCount());
		assertEquals(0, audit.extrema().blockedRowCount());
		assertEquals(2, audit.extrema().staticAnchorReferenceAvailableCount());
		assertEquals(1.0, audit.extrema().maxThrustReferenceWeight(), 1.0e-12);
		assertEquals(1.0, audit.extrema().maxResidualReferenceWeight(), 1.0e-12);

		for (PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow row
				: audit.rows()) {
			assertTrue(row.referenceMaterialExportAllowed());
			assertTrue(row.openFoamDimensionalReferenceRowAvailable());
			assertEquals("openfoam-dimensional-reference-row-available", row.message());
			assertEquals("lookup_reference_and_openfoam_ready", row.sourceRuntimeInfo());
		}
	}

	@Test
	void supportReadyButUnreviewedReferenceKeepsRowsBlocked() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("dimensional_support_ready_reference_review_missing");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(handoff, TARGETS);

		assertEquals(0, audit.extrema().referenceRowAvailableCount());
		assertEquals(6, audit.extrema().blockedRowCount());
		for (PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow row
				: audit.rows()) {
			assertTrue(row.lookupExecutionContractReady());
			assertTrue(row.dimensionalSupportReady());
			assertTrue(row.openFoamSolverQualityContractReady());
			assertEquals(0, row.openFoamSolverQualityBlockerCount());
			assertFalse(row.dimensionalReferenceReviewed());
			assertFalse(row.referenceMaterialExportAllowed());
			assertEquals("openfoam-dimensional-reference-review-missing", row.message());
		}
	}

	@Test
	void executionBlockedHandoffKeepsRowsBlockedWithSpecificReason() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("lookup_execution_blocked_reference_reviewed");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(handoff, TARGETS);

		assertEquals(6, audit.extrema().blockedRowCount());
		for (PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow row
				: audit.rows()) {
			assertFalse(row.lookupExecutionContractReady());
			assertFalse(row.dimensionalSupportReady());
			assertTrue(row.openFoamSolverQualityContractReady());
			assertEquals(0, row.openFoamSolverQualityBlockerCount());
			assertTrue(row.dimensionalReferenceReviewed());
			assertFalse(row.referenceMaterialExportAllowed());
			assertEquals("lookup-execution-contract-not-ready", row.message());
		}
	}

	@Test
	void tableRejectsInvalidInputs() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("dimensional_support_ready_reference_reviewed");
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target =
				target("apDrone", "mid_domain_mid_rpm", 0.4064, 4_712.25, false);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(handoff, null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.row(null, target));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.row(handoff, null));

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario materialization =
						materialization("lookup_reference_and_openfoam_ready");
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(null, handoff, TARGETS));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(materialization, null, TARGETS));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(materialization, handoff, null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.row(null, handoff, target));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.row(materialization, null, target));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.row(materialization, handoff, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				tableAudit("current_dimensional_support_blocked");
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_reference_table_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference,racingQuad,static_anchor_low_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference,apDrone,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.contains("solver_quality_blockers=4;solver_quality_next_action=review-openfoam-mesh-yplus-and-time-step-against-run-setup")));
		assertTrue(lines.stream().anyMatch(line ->
				line.contains("coefficient_shape_guard_ready=false;coefficient_shape_guard_scenarios=5;coefficient_shape_guard_blockers=1")));
		assertTrue(lines.stream().anyMatch(line ->
				line.contains("shape_guard_refs=2;negative_tail_refs=9;eta_residual=0.00027500814692071884;ct_increase=0.000071")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,reference_row_available_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,static_anchor_reference_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,openfoam_solver_quality_contract_ready_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,max_openfoam_solver_quality_blocker_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,max_openfoam_coefficient_lookup_shape_guard_inherited_scenario_count,5,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,max_openfoam_coefficient_negative_thrust_tail_execution_input_row_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,max_archive_curve_shape_guard_inherited_reference_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,max_negative_thrust_tail_reference_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,archive_curve_shape_guard_complete_row_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,runtime_coupling_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,reference_payload_kind,compact-openfoam-dimensional-rotor-response-reference,text,")));
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary
			handoff(String name) {
		return switch (name) {
			case "current_dimensional_support_blocked" -> handoffSummary(
					false, false, false, false, false, 4, 0, 6, false, 2, 9,
					0.00027500814692071884, 0.000071, false,
					"BLOCKED", "openfoam-dimensional-lookup-support-not-ready",
					"audit-only-openfoam-dimensional-reference-handoff");
			case "lookup_execution_blocked_reference_reviewed" -> handoffSummary(
					false, false, true, true, true, 0, 0, 6, true, 6, 0,
					0.0, 0.0, false,
					"BLOCKED", "lookup-execution-contract-not-ready",
					"synthetic-openfoam-dimensional-reference-execution-blocked");
			case "dimensional_support_ready_reference_review_missing" -> handoffSummary(
					true, true, false, true, true, 0, 6, 0, true, 6, 0,
					0.0, 0.0, false,
					"BLOCKED", "openfoam-dimensional-reference-review-missing",
					"synthetic-openfoam-dimensional-support-ready-review-missing");
			case "dimensional_support_ready_reference_reviewed" -> handoffSummary(
					true, true, true, true, true, 0, 6, 0, true, 6, 0,
					0.0, 0.0, true,
					"READY", "openfoam-dimensional-reference-material-ready",
					"synthetic-openfoam-dimensional-reference-handoff-ready");
			default -> throw new IllegalArgumentException("unknown OpenFOAM dimensional handoff fixture: " + name);
		};
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary
			handoffSummary(
					boolean lookupExecutionContractReady,
					boolean dimensionalSupportReady,
					boolean dimensionalReferenceReviewed,
					boolean lookupSupportReady,
					boolean dimensionalResidualReady,
					int openFoamSolverQualityBlockerCount,
					int supportedRows,
					int blockedRows,
					boolean coefficientShapeGuardReady,
					int archiveCurveShapeGuardInheritedReferenceCount,
					int negativeThrustTailReferenceCount,
					double maxArchiveCurveEtaFormulaResidual,
					double maxArchiveCurveCtIncrease,
					boolean referenceMaterialExportAllowed,
					String status,
					String message,
					String sourceRuntimeInfo
			) {
		return new PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary(
				lookupExecutionContractReady,
				dimensionalSupportReady,
				dimensionalReferenceReviewed,
				lookupSupportReady,
				dimensionalResidualReady,
				openFoamSolverQualityBlockerCount == 0,
				openFoamSolverQualityBlockerCount,
				openFoamSolverQualityBlockerCount == 0
						? "openfoam-solver-quality-blockers-clear"
						: "review-openfoam-mesh-yplus-and-time-step-against-run-setup",
				6,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				supportedRows,
				blockedRows,
				coefficientShapeGuardReady,
				5,
				1,
				9,
				0.00027500814692071884,
				0.000071,
				archiveCurveShapeGuardInheritedReferenceCount,
				negativeThrustTailReferenceCount,
				maxArchiveCurveEtaFormulaResidual,
				maxArchiveCurveCtIncrease,
				true,
				referenceMaterialExportAllowed,
				referenceMaterialExportAllowed ? 6 : 0,
				false,
				false,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_PAYLOAD_KIND,
				status,
				message,
				dimensionalSupportReady ? "READY" : "BLOCKED",
				sourceRuntimeInfo
		);
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
			.OpenFoamDimensionalReferenceMaterializationScenario materialization(String name) {
		return PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate.audit()
				.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit
			tableAudit(String handoffScenarioName) {
		return PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(
				handoff(handoffScenarioName),
				TARGETS);
	}

	private static PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target(
			String presetName,
			String caseName,
			double queryAdvanceRatioJ,
			double queryRpm,
			boolean staticAnchorCase
	) {
		return new PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase(
				presetName,
				caseName,
				"da4052_5x3.75",
				"da4052 5.0x3.75",
				queryAdvanceRatioJ,
				queryRpm,
				queryAdvanceRatioJ / Math.PI,
				2,
				staticAnchorCase,
				true,
				true,
				true,
				false,
				true,
				false,
				3,
				PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CT_RESIDUAL,
				PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CP_RESIDUAL,
				PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_ETA_RESIDUAL,
				false,
				false,
				PropellerArchiveCtCpJOpenFoamValidationPlan.SOLVER_FAMILY,
				"BLOCKED",
				"openfoam-case-template-not-run",
				PropellerArchiveCtCpJOpenFoamValidationPlan.NEXT_REQUIRED_ACTION
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_reference_table_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
