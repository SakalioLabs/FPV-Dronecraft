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

class PropellerArchiveCtCpJArchiveCurveShapeReviewTest {
	@Test
	void auditRecordsNonVendoredArchiveCurveShapeGuards() {
		PropellerArchiveCtCpJArchiveCurveShapeReview.CtCpJArchiveCurveShapeReviewAudit audit =
				PropellerArchiveCtCpJArchiveCurveShapeReview.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Archive-Curve-Shape-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("non-vendored CT/CP/J shape diagnostics"));
		assertEquals(27, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(4, audit.curveShapeRowCount());
		assertEquals(14, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.rows().size());
	}

	@Test
	void selectedCurveRowsExposePositivePowerEtaAndCtShapeEvidence() {
		PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow racing =
				PropellerArchiveCtCpJArchiveCurveShapeReview.row("racingQuad");
		assertEquals("da4052 5.0x3.75 - 3", racing.performanceMatchId());
		assertEquals(63, racing.sampleRowCount());
		assertEquals(14, racing.staticSampleRowCount());
		assertEquals(49, racing.nonstaticSampleRowCount());
		assertEquals(18, racing.rpmTrackCount());
		assertEquals(0.812815, racing.maxAdvanceRatioJ(), 1.0e-12);
		assertEquals(58, racing.positiveThrustRowCount());
		assertEquals(5, racing.negativeThrustTailRowCount());
		assertEquals(0.758639, racing.minNegativeThrustAdvanceRatioJ(), 1.0e-12);
		assertEquals(0, racing.nonpositivePowerCoefficientRowCount());
		assertEquals(0.00027500814692071884, racing.maxEtaFormulaResidual(), 1.0e-18);
		assertEquals(0, racing.ctIncreaseStepCount());
		assertTrue(racing.positivePowerPreserved());
		assertTrue(racing.etaFormulaConsistent());
		assertTrue(racing.ctMonotonicWithTolerance());
		assertTrue(racing.positiveThrustEnvelopeCoversAllowedQueries());
		assertTrue(racing.shapeGuardPassed());
		assertTrue(racing.postReviewCtCpJLookupAllowed());
		assertTrue(racing.postReviewFullSimulationLookupAllowed());
		assertFalse(racing.currentCurveFitAllowed());
		assertEquals("SOURCE_REVIEW_REQUIRED", racing.status());

		PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow apDrone =
				PropellerArchiveCtCpJArchiveCurveShapeReview.row("apDrone");
		assertEquals(racing.performanceMatchId(), apDrone.performanceMatchId());
		assertTrue(apDrone.shapeGuardPassed());
		assertEquals("SOURCE_REVIEW_REQUIRED", apDrone.status());
	}

	@Test
	void coverageAndGeometryBlockersStayVisibleAfterShapeReview() {
		PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow cine =
				PropellerArchiveCtCpJArchiveCurveShapeReview.row("cinewhoop");
		assertEquals("gwsdd 3.0x3.0 - 2", cine.performanceMatchId());
		assertEquals(103, cine.sampleRowCount());
		assertEquals(9, cine.negativeThrustTailRowCount());
		assertEquals(0.0004755449264837175, cine.maxEtaFormulaResidual(), 1.0e-18);
		assertTrue(cine.positivePowerPreserved());
		assertTrue(cine.etaFormulaConsistent());
		assertTrue(cine.ctMonotonicWithTolerance());
		assertFalse(cine.positiveThrustEnvelopeCoversAllowedQueries());
		assertFalse(cine.shapeGuardPassed());
		assertFalse(cine.postReviewCtCpJLookupAllowed());
		assertEquals("COVERAGE_BLOCKED", cine.status());

		PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow heavy =
				PropellerArchiveCtCpJArchiveCurveShapeReview.row("heavyLift");
		assertEquals("ancf 10.0x5.0 - 2", heavy.performanceMatchId());
		assertEquals(128, heavy.sampleRowCount());
		assertEquals(44, heavy.negativeThrustTailRowCount());
		assertEquals(1, heavy.ctIncreaseStepCount());
		assertEquals(1, heavy.ctIncreasingTrackCount());
		assertEquals(0.000071, heavy.maxCtIncrease(), 1.0e-12);
		assertTrue(heavy.shapeGuardPassed());
		assertTrue(heavy.postReviewCtCpJLookupAllowed());
		assertFalse(heavy.postReviewFullSimulationLookupAllowed());
		assertEquals("PERFORMANCE_ONLY_SOURCE_REVIEW_REQUIRED", heavy.status());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJArchiveCurveShapeReview.row("missing"));
	}

	@Test
	void summaryKeepsRuntimeClosedAndCountsFitCandidates() {
		PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeSummary summary =
				PropellerArchiveCtCpJArchiveCurveShapeReview.audit().summary();

		assertEquals(4, summary.presetRowCount());
		assertEquals(3, summary.uniquePerformanceCurveCount());
		assertEquals(3, summary.postReviewCtCpJCandidateCount());
		assertEquals(2, summary.postReviewFullSimulationCandidateCount());
		assertEquals(1, summary.performanceOnlyCandidateCount());
		assertEquals(3, summary.shapeGuardPassedCount());
		assertEquals(1, summary.coverageBlockedCount());
		assertEquals(4, summary.positivePowerPreservedCount());
		assertEquals(4, summary.etaFormulaConsistentCount());
		assertEquals(4, summary.ctMonotonicWithToleranceCount());
		assertEquals(4, summary.negativeThrustTailPresetCount());
		assertEquals(58, summary.uniqueNegativeThrustTailRowCount());
		assertEquals(0.0004755449264837175, summary.maxEtaFormulaResidual(), 1.0e-18);
		assertEquals(0.000071, summary.maxCtIncrease(), 1.0e-12);
		assertEquals(0, summary.currentCurveFitAllowedCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJArchiveCurveShapeReview.CtCpJArchiveCurveShapeReviewAudit audit =
				PropellerArchiveCtCpJArchiveCurveShapeReview.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_archive_curve_shape_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_curve_shape_review_curve,racingQuad,da4052 5.0x3.75 - 3,SOURCE_REVIEW_REQUIRED,63,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_curve_shape_review_curve,cinewhoop,gwsdd 3.0x3.0 - 2,COVERAGE_BLOCKED,103,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_curve_shape_review_curve,heavyLift,ancf 10.0x5.0 - 2,PERFORMANCE_ONLY_SOURCE_REVIEW_REQUIRED,128,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_curve_shape_review_summary,all,shape_guard_passed_count,3,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_curve_shape_review_summary,all,current_curve_fit_allowed_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_archive_curve_shape_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
