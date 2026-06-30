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
	private static final PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
			.CtCpJOpenFoamDimensionalReferenceHandoffAudit HANDOFF_AUDIT =
					PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.audit();

	@Test
	void auditBuildsBlockedCurrentDimensionalReferenceRows() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Reference-Table-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("six geometry-backed SI CFD reference rows"));
		assertTrue(audit.caveat().contains("solver-quality QA state"));
		assertTrue(audit.caveat().contains("zero weights"));
		assertEquals(35, audit.packetRowCount());
		assertEquals(10, audit.sourceReferenceRowCount());
		assertEquals(6, audit.referenceRowCount());
		assertEquals(18, audit.summaryRowCount());
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
		assertEquals(0, audit.extrema().dimensionalReferenceReviewedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void reviewedDimensionalHandoffEnablesSixReferenceRows() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("dimensional_support_ready_reference_reviewed");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(handoff);

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
	void supportReadyButUnreviewedReferenceKeepsRowsBlocked() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff =
				handoff("dimensional_support_ready_reference_review_missing");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(handoff);

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
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(handoff);

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
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.row(null, target));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.row(handoff, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit();
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
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,reference_row_available_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,static_anchor_reference_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,openfoam_solver_quality_contract_ready_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,max_openfoam_solver_quality_blocker_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,runtime_coupling_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_summary,all_rows,reference_payload_kind,compact-openfoam-dimensional-rotor-response-reference,text,")));
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary
			handoff(String name) {
		return HANDOFF_AUDIT.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
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
