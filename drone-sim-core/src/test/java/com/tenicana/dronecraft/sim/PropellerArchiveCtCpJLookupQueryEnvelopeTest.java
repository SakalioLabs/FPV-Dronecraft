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

class PropellerArchiveCtCpJLookupQueryEnvelopeTest {
	@Test
	void auditDefinesOfflineQueryEnvelopeWithoutInterpolationOrRuntimeCoupling() {
		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryEnvelopeAudit audit =
				PropellerArchiveCtCpJLookupQueryEnvelope.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Query-Envelope-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("deterministic J/RPM domain checks"));
		assertEquals(43, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.queryCaseRowCount());
		assertEquals(16, audit.presetQueryRowCount());
		assertEquals(20, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.cases().size());
		assertEquals(16, audit.rows().size());

		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryEnvelopeSummary summary =
				audit.summary();
		assertEquals(16, summary.queryRowCount());
		assertEquals(4, summary.queryCaseCount());
		assertEquals(12, summary.insideAdvanceRatioDomainCount());
		assertEquals(16, summary.insideRpmDomainCount());
		assertEquals(12, summary.insideLookupDomainCount());
		assertEquals(4, summary.extrapolationRequiredCount());
		assertEquals(0, summary.currentQueryAllowedCount());
		assertEquals(9, summary.postReviewCtCpJQueryAllowedCount());
		assertEquals(6, summary.postReviewFullSimulationQueryAllowedCount());
		assertEquals(3, summary.postReviewPerformanceOnlyQueryCount());
		assertEquals(3, summary.bladeCoverageBlockedQueryCount());
		assertEquals(3, summary.geometryCoverageBlockedQueryCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals(1.0674 * 1.15 / Math.PI, summary.maxEquivalentProjectMu(), 1.0e-12);
		assertEquals(1.0674 * 0.90 / Math.PI,
				summary.maxInsideDomainEquivalentProjectMu(), 1.0e-12);
		assertTrue(summary.apDroneForwardDiagnosticMaxEquivalentJ() > 0.60);
		assertTrue(summary.apDroneForwardDiagnosticArchiveCoverageRatio() < 1.0);
		assertTrue(summary.apDroneForwardDiagnosticInsideSeedDomain());
		assertEquals("review-source-license-then-run-reviewed-lookup-query-envelope-with-real-ct-cp-j-rows",
				summary.nextRequiredAction());
	}

	@Test
	void queryCasesDefineInsideAndRejectedProbePoints() {
		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryCase staticCase =
				PropellerArchiveCtCpJLookupQueryEnvelope.queryCase("static_anchor_low_rpm");
		assertEquals(0.0, staticCase.advanceRatioFraction());
		assertEquals(0.0, staticCase.rpmFraction());
		assertTrue(staticCase.expectedInsideLookupDomain());
		assertTrue(staticCase.purpose().contains("static"));

		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryCase highProbe =
				PropellerArchiveCtCpJLookupQueryEnvelope.queryCase("high_j_extrapolation_probe");
		assertEquals(1.15, highProbe.advanceRatioFraction());
		assertEquals(1.0, highProbe.rpmFraction());
		assertFalse(highProbe.expectedInsideLookupDomain());
		assertTrue(highProbe.purpose().contains("rejected"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupQueryEnvelope.queryCase("missing"));
	}

	@Test
	void apDroneRowsExposeDomainGuardAndSourceBlocker() {
		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow mid =
				PropellerArchiveCtCpJLookupQueryEnvelope.query("apDrone", "mid_domain_mid_rpm");
		assertEquals("da4052 5.0x3.75 - 3", mid.performanceMatchId());
		assertEquals("da4052 5.0x3.75", mid.geometryMatchId());
		assertEquals(0.8128 * 0.5, mid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals((1_477.8 + 7_946.7) * 0.5, mid.queryRpm(), 1.0e-9);
		assertEquals(mid.queryAdvanceRatioJ() / Math.PI, mid.equivalentProjectMu(), 1.0e-12);
		assertEquals(0.5, mid.jCoverageRatio(), 1.0e-12);
		assertEquals(0.5, mid.rpmCoverageFraction(), 1.0e-12);
		assertTrue(mid.insideAdvanceRatioDomain());
		assertTrue(mid.insideRpmDomain());
		assertTrue(mid.insideLookupDomain());
		assertFalse(mid.extrapolationRequired());
		assertFalse(mid.currentQueryAllowed());
		assertTrue(mid.postReviewCtCpJQueryAllowed());
		assertTrue(mid.postReviewFullSimulationQueryAllowed());
		assertFalse(mid.runtimeCouplingAllowed());
		assertFalse(mid.gameplayAutoApplyAllowed());
		assertEquals("source-license-review-required", mid.currentBlocker());
		assertEquals("none", mid.postReviewBlocker());
		assertEquals("LOOKUP_READY_AFTER_REVIEW", mid.status());

		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow probe =
				PropellerArchiveCtCpJLookupQueryEnvelope.query("apDrone", "high_j_extrapolation_probe");
		assertEquals(0.8128 * 1.15, probe.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(1.15, probe.jCoverageRatio(), 1.0e-12);
		assertFalse(probe.insideAdvanceRatioDomain());
		assertTrue(probe.insideRpmDomain());
		assertFalse(probe.insideLookupDomain());
		assertTrue(probe.extrapolationRequired());
		assertFalse(probe.postReviewCtCpJQueryAllowed());
		assertFalse(probe.postReviewFullSimulationQueryAllowed());
		assertEquals("query-outside-j-rpm-domain", probe.postReviewBlocker());
		assertEquals("OUT_OF_DOMAIN", probe.status());
		assertEquals("query-requires-extrapolation-and-is-rejected", probe.message());
	}

	@Test
	void coverageBlockedRowsStayClosedAfterReview() {
		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow cine =
				PropellerArchiveCtCpJLookupQueryEnvelope.query("cinewhoop", "mid_domain_mid_rpm");
		assertTrue(cine.insideLookupDomain());
		assertFalse(cine.postReviewCtCpJQueryAllowed());
		assertFalse(cine.postReviewFullSimulationQueryAllowed());
		assertEquals("blade-count-coverage-missing", cine.postReviewBlocker());
		assertEquals("COVERAGE_BLOCKED", cine.status());
		assertEquals("resolve-cinewhoop-three-blade-coverage-or-correction", cine.message());

		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow heavy =
				PropellerArchiveCtCpJLookupQueryEnvelope.query("heavyLift", "high_domain_max_rpm");
		assertTrue(heavy.insideLookupDomain());
		assertTrue(heavy.postReviewCtCpJQueryAllowed());
		assertFalse(heavy.postReviewFullSimulationQueryAllowed());
		assertEquals("geometry-fit-input-missing", heavy.postReviewBlocker());
		assertEquals("PERFORMANCE_LOOKUP_READY_AFTER_REVIEW_FULL_SIM_BLOCKED", heavy.status());
		assertEquals("add-heavy-lift-geometry-match-or-reviewed-surrogate", heavy.message());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupQueryEnvelope.query("unknown", "mid_domain_mid_rpm"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupQueryEnvelope.query("apDrone", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryEnvelopeAudit audit =
				PropellerArchiveCtCpJLookupQueryEnvelope.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_query_envelope_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_query,apDrone,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_query,cinewhoop,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_query,heavyLift,high_domain_max_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_query_summary,all,post_review_ct_cp_j_query_allowed_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_query_summary,all,extrapolation_required_count,4,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_query_envelope_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
