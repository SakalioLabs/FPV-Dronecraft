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

class PropellerArchiveCtCpJOpenFoamValidationPlanTest {
	@Test
	void auditBuildsExternalOpenFoamValidationPlan() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.CtCpJOpenFoamValidationPlanAudit audit =
				PropellerArchiveCtCpJOpenFoamValidationPlan.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Validation-Plan-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("external offline CFD"));
		assertEquals(39, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(8, audit.solverRequirementRowCount());
		assertEquals(9, audit.validationCaseRowCount());
		assertEquals(14, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals("https://github.com/OpenFOAM/OpenFOAM-dev", audit.openFoamRepository());
		assertEquals(8, audit.requirements().size());
		assertEquals(9, audit.cases().size());

		assertEquals(9, audit.summary().performanceValidationCaseCount());
		assertEquals(6, audit.summary().fullSimulationCandidateCount());
		assertEquals(3, audit.summary().performanceOnlyCaseCount());
		assertEquals(6, audit.summary().meshGeometryReadyAfterReviewCaseCount());
		assertEquals(3, audit.summary().missingGeometryCaseCount());
		assertEquals(0, audit.summary().currentRunnableCaseCount());
		assertEquals(6, audit.summary().postReviewRunnableCaseCount());
		assertEquals(0, audit.summary().openFoamResultAvailableCount());
		assertEquals(27, audit.summary().requiredCtCpEtaChannelCount());
		assertEquals(3, audit.summary().staticAnchorCaseCount());
		assertEquals(0, audit.summary().runtimeCouplingAllowedCount());
		assertEquals(0, audit.summary().gameplayAutoApplyAllowedCount());
	}

	@Test
	void requirementsKeepSolverAndRuntimeBoundariesExplicit() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationRequirement source =
				PropellerArchiveCtCpJOpenFoamValidationPlan.requirement("openfoam_source_external");
		assertTrue(source.currentSatisfied());
		assertTrue(source.postReviewSatisfied());
		assertEquals("source_boundary", source.evidenceRole());
		assertEquals("READY", source.status());

		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationRequirement archive =
				PropellerArchiveCtCpJOpenFoamValidationPlan.requirement("reviewed_archive_import");
		assertFalse(archive.currentSatisfied());
		assertTrue(archive.postReviewSatisfied());
		assertEquals("BLOCKED", archive.status());

		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationRequirement leak =
				PropellerArchiveCtCpJOpenFoamValidationPlan.requirement("no_runtime_or_playable_auto_apply");
		assertTrue(leak.currentSatisfied());
		assertTrue(leak.note().contains("DronePhysics"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamValidationPlan.requirement("missing"));
	}

	@Test
	void caseRowsMapLookupTargetsIntoOpenFoamWorkItems() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		assertEquals("da4052 5.0x3.75 - 3", apMid.performanceMatchId());
		assertEquals("da4052 5.0x3.75", apMid.geometryMatchId());
		assertEquals(0.4064, apMid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_712.25, apMid.queryRpm(), 1.0e-9);
		assertEquals(0.4064 / Math.PI, apMid.equivalentProjectMu(), 1.0e-12);
		assertEquals(4, apMid.minimumNeighborRows());
		assertTrue(apMid.performanceValidationTarget());
		assertTrue(apMid.fullSimulationCandidate());
		assertTrue(apMid.meshGeometryReadyAfterReview());
		assertFalse(apMid.currentOpenFoamCaseRunnable());
		assertTrue(apMid.postReviewOpenFoamCaseRunnable());
		assertFalse(apMid.openFoamResultAvailable());
		assertEquals(3, apMid.requiredResultChannels());
		assertEquals(0.08, apMid.maxCtResidual(), 1.0e-12);
		assertEquals(0.10, apMid.maxCpResidual(), 1.0e-12);
		assertEquals(0.08, apMid.maxEtaResidual(), 1.0e-12);
		assertEquals("external-openfoam-steady-rotor-cfd", apMid.solverFamily());
		assertEquals("openfoam-run-not-executed", apMid.message());

		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase heavy =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("heavyLift", "mid_domain_mid_rpm");
		assertEquals("missing-reviewed-geometry-match", heavy.geometryMatchId());
		assertFalse(heavy.fullSimulationCandidate());
		assertFalse(heavy.meshGeometryReadyAfterReview());
		assertFalse(heavy.postReviewOpenFoamCaseRunnable());
		assertEquals("openfoam-blade-geometry-missing", heavy.message());
		assertEquals("resolve-heavy-lift-reviewed-geometry-before-openfoam-mesh",
				heavy.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamValidationPlan.CtCpJOpenFoamValidationPlanAudit audit =
				PropellerArchiveCtCpJOpenFoamValidationPlan.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_validation_plan_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_validation_requirement,openfoam_source_external,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_validation_case,apDrone,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_validation_case,heavyLift,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_validation_summary,all_cases,post_review_runnable_case_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_validation_summary,all_cases,runtime_coupling_allowed_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_validation_plan_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
