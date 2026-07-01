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

class PropellerArchiveCtCpJScatteredSurfaceFitContractTest {
	@Test
	void auditBuildsScatteredSurfaceFitContractScenarios() {
		PropellerArchiveCtCpJScatteredSurfaceFitContract.CtCpJScatteredSurfaceFitContractAudit audit =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Scattered-Surface-Fit-Contract-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("sparse RPM-track topology"));
		assertEquals(58, audit.packetRowCount());
		assertEquals(10, audit.sourceReferenceRowCount());
		assertEquals(15, audit.fitFieldRowCount());
		assertEquals(9, audit.targetRowCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(15, audit.fields().size());
		assertEquals(9, audit.targets().size());
		assertEquals(5, audit.scenarios().size());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary current =
				scenario(audit, "current_source_review_blocked_no_surface_fit").summary();
		assertFalse(current.sourceRowsReviewed());
		assertTrue(current.archiveGridCoverageReady());
		assertFalse(current.scatteredSurfaceFitRun());
		assertEquals(9, current.expectedTargetCount());
		assertEquals(3, current.directNeighborTargetCount());
		assertEquals(6, current.scatteredFitRequiredTargetCount());
		assertEquals(9, current.archiveCurveShapeGuardReadyTargetCount());
		assertEquals(9, current.negativeThrustTailTargetCount());
		assertEquals(9, current.missingResultCount());
		assertEquals("source-license-review-required", current.message());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary staticOnly =
				scenario(audit, "reviewed_static_neighbors_only_nonstatic_missing").summary();
		assertTrue(staticOnly.sourceRowsReviewed());
		assertFalse(staticOnly.scatteredSurfaceFitRun());
		assertEquals(3, staticOnly.observedResultCount());
		assertEquals(6, staticOnly.missingResultCount());
		assertEquals(3, staticOnly.passedResultCount());
		assertEquals("scattered-surface-fit-not-run", staticOnly.message());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary missing =
				scenario(audit, "reviewed_surface_fit_results_missing").summary();
		assertTrue(missing.scatteredSurfaceFitRun());
		assertEquals(9, missing.missingResultCount());
		assertEquals("scattered-surface-fit-results-missing", missing.message());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary failed =
				scenario(audit, "reviewed_surface_fit_one_failed").summary();
		assertEquals(9, failed.observedResultCount());
		assertEquals(8, failed.passedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertEquals(0.075, failed.maxCtHoldoutResidual(), 1.0e-12);
		assertFalse(failed.scatteredSurfaceFitContractReady());
		assertEquals("scattered-surface-fit-residual-gate-failed", failed.message());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary ready =
				scenario(audit, "reviewed_surface_fit_all_pass").summary();
		assertEquals(9, ready.observedResultCount());
		assertEquals(9, ready.passedResultCount());
		assertEquals(0, ready.failedResultCount());
		assertEquals(6, ready.readyFullSimulationTargetCount());
		assertEquals(3, ready.readyPerformanceOnlyTargetCount());
		assertEquals(9, ready.archiveCurveShapeGuardReadyTargetCount());
		assertEquals(0.002, ready.maxStaticAnchorResidual(), 1.0e-12);
		assertEquals(0.03, ready.maxCtHoldoutResidual(), 1.0e-12);
		assertEquals(0.04, ready.maxCpHoldoutResidual(), 1.0e-12);
		assertEquals(0.02, ready.maxEtaConsistencyResidual(), 1.0e-12);
		assertEquals(0.00027500814692071884, ready.maxArchiveCurveEtaFormulaResidual(), 1.0e-18);
		assertEquals(0.000071, ready.maxArchiveCurveCtIncrease(), 1.0e-12);
		assertTrue(ready.scatteredSurfaceFitContractReady());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("scattered-surface-fit-contract-ready", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(9, audit.extrema().maxObservedResultCount());
		assertEquals(9, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(6, audit.extrema().maxReadyFullSimulationTargetCount());
		assertEquals(3, audit.extrema().maxReadyPerformanceOnlyTargetCount());
		assertEquals(0.075, audit.extrema().maxCtHoldoutResidual(), 1.0e-12);
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void targetsSeparateStaticDirectBindingFromScatteredFitRows() {
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget staticTarget =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.target("racingQuad", "static_anchor_low_rpm");
		assertTrue(staticTarget.directNeighborBindingReady());
		assertFalse(staticTarget.scatteredFitRequired());
		assertTrue(staticTarget.archiveCurveShapeGuardPassed());
		assertEquals(5, staticTarget.negativeThrustTailRowCount());
		assertEquals(0.00027500814692071884, staticTarget.archiveMaxEtaFormulaResidual(), 1.0e-18);
		assertEquals(1, staticTarget.minimumPerformanceNeighborRows());
		assertEquals(2, staticTarget.availableRectangularNeighborRows());
		assertEquals("ready-for-reviewed-neighbor-binding", staticTarget.nextRequiredAction());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget midTarget =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.target("racingQuad", "mid_domain_mid_rpm");
		assertFalse(midTarget.directNeighborBindingReady());
		assertTrue(midTarget.scatteredFitRequired());
		assertEquals(4, midTarget.minimumPerformanceNeighborRows());
		assertEquals(2, midTarget.availableRectangularNeighborRows());
		assertEquals(0, midTarget.availableNonstaticNeighborRows());
		assertTrue(midTarget.archiveCurveShapeGuardPassed());
		assertEquals("fit-scattered-ct-cp-j-surface-before-direct-lookup-binding",
				midTarget.nextRequiredAction());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget heavyStatic =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.target("heavyLift", "static_anchor_low_rpm");
		assertTrue(heavyStatic.directNeighborBindingReady());
		assertTrue(heavyStatic.archiveCurveShapeGuardPassed());
		assertEquals(44, heavyStatic.negativeThrustTailRowCount());
		assertEquals(0.000071, heavyStatic.archiveMaxCtIncrease(), 1.0e-12);
		assertFalse(heavyStatic.postReviewFullSimulationLookupAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.target(
						"cinewhoop", "static_anchor_low_rpm"));
	}

	@Test
	void fieldsAndResultsCarryValidationGuards() {
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitField ct =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.field("ct_holdout_residual");
		assertEquals("ratio", ct.unit());
		assertTrue(ct.required());
		assertTrue(ct.downstreamUse().contains("thrust-coefficient"));
		assertFalse(ct.runtimeCouplingAllowed());
		assertFalse(ct.gameplayAutoApplyAllowed());
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.field("missing"));

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget staticTarget =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.target("racingQuad", "static_anchor_low_rpm");
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitResult staticPass =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.result(staticTarget, true, false,
						0.001, 0.0, 0.0, 0.0, true, true);
		assertTrue(staticPass.surfaceEvidenceReady());
		assertTrue(staticPass.passed());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget midTarget =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.target("racingQuad", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitResult noFit =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.result(midTarget, true, false,
						0.002, 0.03, 0.04, 0.02, true, true);
		assertFalse(noFit.surfaceEvidenceReady());
		assertFalse(noFit.passed());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitResult pass =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.result(midTarget, true, true,
						0.002, 0.03, 0.04, 0.02, true, true);
		assertTrue(pass.surfaceEvidenceReady());
		assertTrue(pass.passed());

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitResult fail =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.result(midTarget, true, true,
						0.002, 0.061, 0.04, 0.02, true, true);
		assertFalse(fail.passed());
		assertEquals("FAIL", fail.status());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.result(null, true, true,
						0.002, 0.03, 0.04, 0.02, true, true));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.result(midTarget, true, true,
						Double.NaN, 0.03, 0.04, 0.02, true, true));
	}

	@Test
	void reviewRejectsInvalidInputs() {
		List<PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget> targets =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.targets();
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget first = targets.get(0);
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitResult result =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.result(first, true, false,
						0.001, 0.0, 0.0, 0.0, true, true);
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitResult unexpected =
				new PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitResult(
						"unexpected", "case", true, true, true,
						0.0, 0.0, 0.0, 0.0, true, true, true, "PASS");

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.review(
						true, true, true, null, List.of(result), "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.review(
						true, true, true, targets, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.review(
						true, true, true, targets, List.of(result), ""));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.review(
						true, true, true, targets, List.of(result, result), "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitContract.review(
						true, true, true, targets, List.of(unexpected), "source"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJScatteredSurfaceFitContract.CtCpJScatteredSurfaceFitContractAudit audit =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_scattered_surface_fit_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_surface_fit_source,archive_curve_shape_review,source,READY,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_surface_fit_target,racingQuad,mid_domain_mid_rpm,SCATTERED_FIT_REQUIRED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_surface_fit_scenario,reviewed_surface_fit_all_pass,READY,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_surface_fit_summary,all,scattered_fit_required_target_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_surface_fit_summary,all,archive_curve_shape_guard_ready_target_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_surface_fit_summary,all,max_archive_curve_eta_formula_residual,0.00027500814692071884,ratio,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_surface_fit_summary,all,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitScenario scenario(
			PropellerArchiveCtCpJScatteredSurfaceFitContract.CtCpJScatteredSurfaceFitContractAudit audit,
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
							"docs/data/propeller_archive_ct_cp_j_scattered_surface_fit_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
