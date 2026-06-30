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

class PropellerArchiveCtCpJOpenFoamSolverQualityContractTest {
	private static final String REVIEWED_CASE_SHA =
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	@Test
	void auditBuildsSolverQualityContractScenarios() {
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.CtCpJOpenFoamSolverQualityContractAudit audit =
				PropellerArchiveCtCpJOpenFoamSolverQualityContract.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Solver-Quality-Contract-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("mesh"));
		assertTrue(audit.caveat().contains("grid-independence"));
		assertEquals(43, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(16, audit.qualityFieldRowCount());
		assertEquals(5, audit.scenarioRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(16, audit.fields().size());
		assertEquals(5, audit.scenarios().size());

		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary current =
				scenario(audit, "current_no_case_hash_no_solver_quality").summary();
		assertFalse(current.numericalBudgetReady());
		assertEquals(6, current.expectedQualityCaseCount());
		assertEquals(6, current.missingQualityCaseCount());
		assertEquals("openfoam-numerical-budget-not-ready", current.message());

		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary missing =
				scenario(audit, "numerical_budget_ready_quality_missing").summary();
		assertTrue(missing.numericalBudgetReady());
		assertTrue(missing.externalCaseHashReady());
		assertEquals(6, missing.missingQualityCaseCount());
		assertEquals("openfoam-solver-quality-results-missing", missing.message());

		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary ready =
				scenario(audit, "synthetic_solver_quality_all_pass").summary();
		assertEquals(6, ready.observedQualityCaseCount());
		assertEquals(6, ready.passedQualityCaseCount());
		assertEquals(0, ready.failedQualityCaseCount());
		assertEquals(16, ready.minObservedQualityChannelCount());
		assertEquals(0.5, ready.maxBladeCellCourant(), 1.0e-12);
		assertEquals(0.04252185031386221, ready.maxFreestreamCourant(), 1.0e-15);
		assertEquals(0.44762327744595565, ready.maxAzimuthDegreesPerStep(), 1.0e-15);
		assertEquals(0.16263075459130397, ready.maxHelicalTipMach(), 1.0e-15);
		assertEquals(0.0, ready.maxReynoldsReferenceResidualRatio(), 1.0e-12);
		assertEquals(0.015, ready.maxGridIndependenceResidualRatio(), 1.0e-12);
		assertEquals(48.0, ready.maxMeshNonOrthogonalityDegrees(), 1.0e-12);
		assertEquals(2, ready.lowReynoldsReviewRequiredCount());
		assertEquals(0, ready.lowReynoldsReviewMissingCount());
		assertTrue(ready.openFoamSolverQualityContractReady());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary lowRe =
				scenario(audit, "synthetic_low_reynolds_review_missing").summary();
		assertEquals(1, lowRe.lowReynoldsReviewMissingCount());
		assertEquals("openfoam-low-reynolds-review-missing", lowRe.message());
		assertFalse(lowRe.openFoamSolverQualityContractReady());

		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary cfl =
				scenario(audit, "synthetic_blade_courant_failed").summary();
		assertEquals(0.6, cfl.maxBladeCellCourant(), 1.0e-12);
		assertEquals(1, cfl.failedQualityCaseCount());
		assertEquals("openfoam-solver-quality-gate-failed", cfl.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(6, audit.extrema().maxExpectedQualityCaseCount());
		assertEquals(6, audit.extrema().maxMissingQualityCaseCount());
		assertEquals(1, audit.extrema().maxFailedQualityCaseCount());
		assertEquals(0.6, audit.extrema().maxBladeCellCourant(), 1.0e-12);
		assertEquals(0.015, audit.extrema().maxGridIndependenceResidualRatio(), 1.0e-12);
		assertEquals(1, audit.extrema().maxLowReynoldsReviewMissingCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void fieldsDefineCompactSolverQualityPayload() {
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualityField cfl =
				PropellerArchiveCtCpJOpenFoamSolverQualityContract.field("max_blade_cell_courant");
		assertEquals("ratio", cfl.unit());
		assertTrue(cfl.required());
		assertTrue(cfl.downstreamUse().contains("timestep"));
		assertFalse(cfl.runtimeCouplingAllowed());
		assertFalse(cfl.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualityField grid =
				PropellerArchiveCtCpJOpenFoamSolverQualityContract.field(
						"grid_independence_residual_ratio");
		assertTrue(grid.downstreamUse().contains("stability"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityContract.field("missing"));
	}

	@Test
	void qualitySampleValidatesCourantMeshDomainAndLowReynoldsReview() {
		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow high =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.row("apDrone", "high_domain_max_rpm");
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySample pass =
				passingSample(high);

		assertTrue(pass.passed());
		assertEquals("PASS", pass.status());
		assertEquals("apDrone/high_domain_max_rpm", pass.caseKey());
		assertEquals(REVIEWED_CASE_SHA, pass.sourceCaseSha256());
		assertEquals(16, pass.qualityChannelCount());
		assertEquals(0.5, pass.maxBladeCellCourant(), 1.0e-12);
		assertEquals(0.04252185031386221, pass.maxFreestreamCourant(), 1.0e-15);
		assertEquals(0.4359605716234232, pass.maxAzimuthDegreesPerStep(), 1.0e-15);
		assertEquals(0.16263075459130397, pass.maxHelicalTipMach(), 1.0e-15);
		assertEquals(28_145.342361325085, pass.stationReynoldsChordNumber(), 1.0e-12);
		assertEquals(0.0, pass.reynoldsReferenceResidualRatio(), 1.0e-12);
		assertEquals(0.00101203125, pass.nearBladeCellSizeMeters(), 1.0e-15);
		assertEquals(0.00269875, pass.wakeCoreCellSizeMeters(), 1.0e-15);
		assertEquals(8.0, pass.radialDomainDiameters(), 1.0e-12);
		assertEquals(4.0, pass.upstreamDomainDiameters(), 1.0e-12);
		assertEquals(12.0, pass.downstreamDomainDiameters(), 1.0e-12);
		assertEquals(0.015, pass.gridIndependenceResidualRatio(), 1.0e-12);
		assertEquals(48.0, pass.meshNonOrthogonalityMaxDegrees(), 1.0e-12);

		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySample highCfl =
				PropellerArchiveCtCpJOpenFoamSolverQualityContract.sample(
						high, REVIEWED_CASE_SHA, 16, 0.51,
						high.freestreamCourantAtSuggestedTimeStep(),
						high.azimuthDegreesPerStep(), high.helicalTipMach(),
						high.reynoldsStationChordNumber(), true,
						high.nearBladeCellSizeMeters(), high.wakeCoreCellSizeMeters(),
						8.0, 4.0, 12.0, 0.015, 48.0);
		assertFalse(highCfl.passed());
		assertEquals("FAIL", highCfl.status());

		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow staticAnchor =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.row("racingQuad", "static_anchor_low_rpm");
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySample lowReMissing =
				PropellerArchiveCtCpJOpenFoamSolverQualityContract.sample(
						staticAnchor, REVIEWED_CASE_SHA, 16,
						staticAnchor.bladeCellCourantAtSuggestedTimeStep(),
						staticAnchor.freestreamCourantAtSuggestedTimeStep(),
						staticAnchor.azimuthDegreesPerStep(), staticAnchor.helicalTipMach(),
						staticAnchor.reynoldsStationChordNumber(), false,
						staticAnchor.nearBladeCellSizeMeters(), staticAnchor.wakeCoreCellSizeMeters(),
						8.0, 4.0, 12.0, 0.015, 48.0);
		assertFalse(lowReMissing.passed());
	}

	@Test
	void reviewRejectsInvalidInputsAndDuplicateRows() {
		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow high =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.row("apDrone", "high_domain_max_rpm");
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySample sample =
				passingSample(high);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityContract.review(
						true, true, true, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityContract.review(
						true, true, true, List.of(sample), ""));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityContract.review(
						true, true, true, List.of(sample, sample), "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityContract.sample(
						null, REVIEWED_CASE_SHA, 16, 0.5, 0.0, 0.5, 0.1,
						1.0, true, 0.001, 0.002, 8.0, 4.0, 12.0, 0.01, 40.0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityContract.sample(
						high, "not-a-sha", 16, 0.5, 0.0, 0.5, 0.1,
						1.0, true, 0.001, 0.002, 8.0, 4.0, 12.0, 0.01, 40.0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamSolverQualityContract.sample(
						high, REVIEWED_CASE_SHA, 16, Double.NaN, 0.0, 0.5, 0.1,
						1.0, true, 0.001, 0.002, 8.0, 4.0, 12.0, 0.01, 40.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.CtCpJOpenFoamSolverQualityContractAudit audit =
				PropellerArchiveCtCpJOpenFoamSolverQualityContract.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_solver_quality_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_field,max_blade_cell_courant,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_scenario,current_no_case_hash_no_solver_quality,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_scenario,synthetic_solver_quality_all_pass,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_summary,all_scenarios,max_blade_cell_courant,0.6,ratio,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_solver_quality_method,compact_solver_qa_rule,method,")));
	}

	private static PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySample
			passingSample(
					PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow target
			) {
		return PropellerArchiveCtCpJOpenFoamSolverQualityContract.sample(
				target,
				REVIEWED_CASE_SHA,
				16,
				target.bladeCellCourantAtSuggestedTimeStep(),
				target.freestreamCourantAtSuggestedTimeStep(),
				target.azimuthDegreesPerStep(),
				target.helicalTipMach(),
				target.reynoldsStationChordNumber(),
				true,
				target.nearBladeCellSizeMeters(),
				target.wakeCoreCellSizeMeters(),
				8.0,
				4.0,
				12.0,
				0.015,
				48.0);
	}

	private static PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualityScenario
			scenario(
					PropellerArchiveCtCpJOpenFoamSolverQualityContract.CtCpJOpenFoamSolverQualityContractAudit audit,
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
							"docs/data/propeller_archive_ct_cp_j_openfoam_solver_quality_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
