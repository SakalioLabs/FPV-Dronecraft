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

class PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReportTest {
	private static final PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
			.CtCpJOpenFoamSolverQualityBlockerReportAudit AUDIT =
					PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport.audit();
	private static final PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary
			READY_SUMMARY = PropellerArchiveCtCpJOpenFoamSolverQualityContract.audit()
					.scenarios()
					.stream()
					.filter(scenario -> "synthetic_solver_quality_all_pass"
							.equals(scenario.scenarioName()))
					.findFirst()
					.orElseThrow()
					.summary();

	@Test
	void auditDecomposesOpenFoamSolverQualityBlockers() {
		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Solver-Quality-Blocker-Report-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("external case provenance"));
		assertTrue(AUDIT.caveat().contains("mesh/timestep"));
		assertEquals(26, AUDIT.packetRowCount());
		assertEquals(5, AUDIT.sourceReferenceRowCount());
		assertEquals(5, AUDIT.scenarioRowCount());
		assertEquals(15, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(5, AUDIT.scenarios().size());

		PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
				.OpenFoamSolverQualityBlockerScenario current =
						scenario(AUDIT, "current_no_case_hash_no_solver_quality");
		assertEquals(4, current.blockerCount());
		assertTrue(current.numericalBudgetBlocker());
		assertTrue(current.externalCaseHashBlocker());
		assertTrue(current.solverQualityExtractionBlocker());
		assertTrue(current.missingQualityCaseBlocker());
		assertFalse(current.failedQualityCaseBlocker());
		assertEquals(6, current.expectedQualityCaseCount());
		assertEquals(0, current.observedQualityCaseCount());
		assertEquals(6, current.missingQualityCaseCount());
		assertEquals("review-openfoam-mesh-yplus-and-time-step-against-run-setup",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());

		PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
				.OpenFoamSolverQualityBlockerScenario missing =
						scenario(AUDIT, "numerical_budget_ready_quality_missing");
		assertEquals(1, missing.blockerCount());
		assertFalse(missing.numericalBudgetBlocker());
		assertFalse(missing.externalCaseHashBlocker());
		assertFalse(missing.solverQualityExtractionBlocker());
		assertTrue(missing.missingQualityCaseBlocker());
		assertEquals("extract-compact-openfoam-solver-quality-summary",
				missing.nextRequiredAction());

		PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
				.OpenFoamSolverQualityBlockerScenario ready =
						scenario(AUDIT, "synthetic_solver_quality_all_pass");
		assertEquals(0, ready.blockerCount());
		assertEquals(6, ready.observedQualityCaseCount());
		assertEquals(0, ready.missingQualityCaseCount());
		assertEquals("READY", ready.status());
		assertEquals("openfoam-solver-quality-blockers-clear", ready.message());
		assertEquals("openfoam-solver-quality-blockers-clear", ready.nextRequiredAction());
		assertFalse(ready.runtimeCouplingLeakBlocker());
		assertFalse(ready.gameplayAutoApplyLeakBlocker());
	}

	@Test
	void reportSeparatesLowReynoldsReviewAndCourantFailures() {
		PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
				.OpenFoamSolverQualityBlockerScenario lowRe =
						scenario(AUDIT, "synthetic_low_reynolds_review_missing");
		assertEquals(2, lowRe.blockerCount());
		assertTrue(lowRe.failedQualityCaseBlocker());
		assertTrue(lowRe.lowReynoldsReviewBlocker());
		assertEquals(1, lowRe.lowReynoldsReviewMissingCount());
		assertFalse(lowRe.bladeCourantBlocker());
		assertEquals("review-low-reynolds-static-anchor-openfoam-model",
				lowRe.nextRequiredAction());

		PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
				.OpenFoamSolverQualityBlockerScenario cfl =
						scenario(AUDIT, "synthetic_blade_courant_failed");
		assertEquals(2, cfl.blockerCount());
		assertTrue(cfl.failedQualityCaseBlocker());
		assertTrue(cfl.bladeCourantBlocker());
		assertFalse(cfl.lowReynoldsReviewBlocker());
		assertEquals("bind-openfoam-deltaT-to-run-setup-budget",
				cfl.nextRequiredAction());
	}

	@Test
	void extremaPreserveBlockerCountsAndLeakGuards() {
		assertEquals(5, AUDIT.extrema().scenarioCount());
		assertEquals(1, AUDIT.extrema().readyScenarioCount());
		assertEquals(4, AUDIT.extrema().blockedScenarioCount());
		assertEquals(4, AUDIT.extrema().maxBlockerCount());
		assertEquals(1, AUDIT.extrema().numericalBudgetBlockerScenarioCount());
		assertEquals(1, AUDIT.extrema().externalCaseHashBlockerScenarioCount());
		assertEquals(1, AUDIT.extrema().solverQualityExtractionBlockerScenarioCount());
		assertEquals(2, AUDIT.extrema().missingQualityCaseBlockerScenarioCount());
		assertEquals(2, AUDIT.extrema().failedQualityCaseBlockerScenarioCount());
		assertEquals(1, AUDIT.extrema().lowReynoldsReviewBlockerScenarioCount());
		assertEquals(1, AUDIT.extrema().bladeCourantBlockerScenarioCount());
		assertEquals(0, AUDIT.extrema().gridIndependenceBlockerScenarioCount());
		assertEquals(0, AUDIT.extrema().meshNonOrthogonalityBlockerScenarioCount());
		assertEquals(0, AUDIT.extrema().runtimeCouplingLeakScenarioCount());
		assertEquals(0, AUDIT.extrema().gameplayAutoApplyLeakScenarioCount());
	}

	@Test
	void reportRejectsMalformedInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport.scenario(
						(PropellerArchiveCtCpJOpenFoamSolverQualityContract
								.OpenFoamSolverQualityScenario) null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport.scenario("", READY_SUMMARY));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport.scenario("ready", null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_solver_quality_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_blocker_scenario,current_no_case_hash_no_solver_quality,4,true,true,true,true,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_blocker_scenario,numerical_budget_ready_quality_missing,1,false,false,false,true,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_blocker_scenario,synthetic_low_reynolds_review_missing,2,false,false,false,false,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_blocker_scenario,synthetic_blade_courant_failed,2,false,false,false,false,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_blocker_summary,max_blocker_count,4,")));
	}

	private static PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
			.OpenFoamSolverQualityBlockerScenario scenario(
					PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport
							.CtCpJOpenFoamSolverQualityBlockerReportAudit audit,
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
							"docs/data/propeller_archive_ct_cp_j_openfoam_solver_quality_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
