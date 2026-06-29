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

class PropellerArchiveCurveFitPlanTest {
	@Test
	void auditDefinesOfflineCurveFamiliesWithoutRuntimeCoupling() {
		PropellerArchiveCurveFitPlan.CurveFitPlanAudit audit =
				PropellerArchiveCurveFitPlan.audit();

		assertEquals("User-Propeller-Archive-Curve-Fit-Plan-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("offline CT/CP/eta"));
		assertEquals(33, audit.packetRowCount());
		assertEquals(5, audit.sourceReferenceRowCount());
		assertEquals(6, audit.curveContractRowCount());
		assertEquals(4, audit.presetPlanRowCount());
		assertEquals(7, audit.fitStageRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());

		PropellerArchiveCurveFitPlan.CurveFitPlanSummary summary = audit.summary();
		assertEquals(6, summary.curveContractCount());
		assertEquals(6, summary.requiredCurveContractCount());
		assertEquals(4, summary.presetPlanCount());
		assertEquals(3, summary.postReviewCtCpFitAllowedCount());
		assertEquals(3, summary.postReviewGeometryFitAllowedCount());
		assertEquals(2, summary.postReviewFullSimulationFitAllowedCount());
		assertEquals(4, summary.syntheticFullSimulationFitAllowedCount());
		assertEquals(0, summary.compactReferenceExportAllowedAfterReviewCount());
		assertEquals(0, summary.runtimeCouplingAllowedPresetCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedPresetCount());
		assertEquals("resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage",
				summary.nextRequiredAction());
	}

	@Test
	void curveContractsCaptureCoefficientAndGeometryFitConstraints() {
		PropellerArchiveCurveFitPlan.CurveFitContract ct =
				PropellerArchiveCurveFitPlan.curve("ct_vs_advance");
		assertEquals("performance", ct.sourceTable());
		assertEquals("thrust_coefficient", ct.dependentVariable());
		assertEquals("shape-preserving-regularized-spline", ct.fitModel());
		assertTrue(ct.requiredForSimulationFit());
		assertTrue(ct.physicalConstraint().contains("static-anchor"));

		PropellerArchiveCurveFitPlan.CurveFitContract beta =
				PropellerArchiveCurveFitPlan.curve("beta_distribution");
		assertEquals("geometry", beta.sourceTable());
		assertEquals("beta_degrees", beta.dependentVariable());
		assertEquals("bounded-pchip-station-fit", beta.fitModel());
		assertTrue(beta.validationMetric().contains("70r-beta"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitPlan.curve("missing"));
	}

	@Test
	void presetPlansCarryPostReviewCoverageStateAndSyntheticTarget() {
		PropellerArchiveCurveFitPlan.PresetCurveFitPlan racing =
				PropellerArchiveCurveFitPlan.preset("racingQuad");
		assertTrue(racing.performanceInputReadyAfterReview());
		assertTrue(racing.bladeCoverageReadyAfterReview());
		assertTrue(racing.geometryInputReadyAfterReview());
		assertTrue(racing.ctCpCurveFitAllowedAfterReview());
		assertTrue(racing.geometryCurveFitAllowedAfterReview());
		assertTrue(racing.fullSimulationCurveFitAllowedAfterReview());
		assertFalse(racing.compactReferenceExportAllowedAfterReview());
		assertEquals("none", racing.postReviewBlocker());

		PropellerArchiveCurveFitPlan.PresetCurveFitPlan cine =
				PropellerArchiveCurveFitPlan.preset("cinewhoop");
		assertTrue(cine.performanceInputReadyAfterReview());
		assertFalse(cine.bladeCoverageReadyAfterReview());
		assertTrue(cine.geometryInputReadyAfterReview());
		assertFalse(cine.ctCpCurveFitAllowedAfterReview());
		assertTrue(cine.geometryCurveFitAllowedAfterReview());
		assertFalse(cine.fullSimulationCurveFitAllowedAfterReview());
		assertTrue(cine.fullSimulationCurveFitAllowedInSyntheticTarget());
		assertEquals("blade-count-coverage-missing", cine.postReviewBlocker());
		assertEquals("resolve-cinewhoop-three-blade-coverage-or-correction",
				cine.nextRequiredAction());

		PropellerArchiveCurveFitPlan.PresetCurveFitPlan heavy =
				PropellerArchiveCurveFitPlan.preset("heavyLift");
		assertTrue(heavy.ctCpCurveFitAllowedAfterReview());
		assertFalse(heavy.geometryCurveFitAllowedAfterReview());
		assertFalse(heavy.fullSimulationCurveFitAllowedAfterReview());
		assertEquals("geometry-fit-input-missing", heavy.postReviewBlocker());
		assertEquals("add-heavy-lift-geometry-match-or-reviewed-surrogate",
				heavy.nextRequiredAction());
		assertFalse(heavy.runtimeCouplingAllowed());
		assertFalse(heavy.gameplayAutoApplyAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitPlan.preset("unknown"));
	}

	@Test
	void stagesKeepCurrentFitClosedUntilReviewedCoverageAndQualityAcceptance() {
		PropellerArchiveCurveFitPlan.CurveFitStage source =
				PropellerArchiveCurveFitPlan.stage("source_review");
		assertFalse(source.currentSatisfied());
		assertTrue(source.reviewedImportSatisfied());
		assertTrue(source.syntheticTargetSatisfied());

		PropellerArchiveCurveFitPlan.CurveFitStage coverage =
				PropellerArchiveCurveFitPlan.stage("fit_input_coverage");
		assertFalse(coverage.currentSatisfied());
		assertFalse(coverage.reviewedImportSatisfied());
		assertTrue(coverage.syntheticTargetSatisfied());

		PropellerArchiveCurveFitPlan.CurveFitStage guard =
				PropellerArchiveCurveFitPlan.stage("runtime_leak_guard");
		assertTrue(guard.currentSatisfied());
		assertTrue(guard.reviewedImportSatisfied());
		assertTrue(guard.syntheticTargetSatisfied());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed", guard.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCurveFitPlan.stage("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCurveFitPlan.CurveFitPlanAudit audit =
				PropellerArchiveCurveFitPlan.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_curve_fit_plan_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_contract,ct_vs_advance,performance,advance_ratio;rpm;diameter_inches;pitch_inches;blade_count,thrust_coefficient,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_preset,cinewhoop,gwsdd 3.0x3.0 - 2,gwsdd 3.0x3.0,true,false,true,false,true,false,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_preset,heavyLift,ancf 10.0x5.0 - 2,missing-reviewed-geometry-match,true,true,false,true,false,false,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_curve_fit_summary,all,post_review_full_simulation_fit_allowed_count,2,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_curve_fit_plan_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
