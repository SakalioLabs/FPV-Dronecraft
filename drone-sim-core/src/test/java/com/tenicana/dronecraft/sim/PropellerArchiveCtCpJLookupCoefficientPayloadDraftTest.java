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

class PropellerArchiveCtCpJLookupCoefficientPayloadDraftTest {
	@Test
	void auditDefinesCompactCoefficientDraftsWithoutOpeningLookupExecution() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraft.CtCpJLookupCoefficientPayloadDraftAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraft.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Draft-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("compact archive-derived coefficient candidates"));
		assertEquals(48, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(21, audit.draftRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(21, audit.rows().size());
	}

	@Test
	void directStaticDraftsCarryArchiveSamplesAndLocalDraftsStayReviewBlocked() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraft.CoefficientPayloadDraftRow racingStatic =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraft.draft(
						"racingQuad", "static_anchor_low_rpm", "static-anchor");

		assertEquals("volume2_exp.csv", racingStatic.sourceArchiveFileName());
		assertEquals("DIRECT_STATIC_SAMPLE_DRAFT", racingStatic.draftCoefficientSourceKind());
		assertEquals(0.139069, racingStatic.draftCtCoefficient(), 1.0e-12);
		assertEquals(0.116804, racingStatic.draftCpCoefficient(), 1.0e-12);
		assertEquals(1, racingStatic.fitNeighborRowCount());
		assertTrue(racingStatic.directStaticSampleUsed());
		assertTrue(racingStatic.nonnegativeDraftCt());
		assertTrue(racingStatic.positiveDraftCp());
		assertFalse(racingStatic.sourceRowsReviewed());
		assertFalse(racingStatic.coefficientPayloadReviewed());
		assertFalse(racingStatic.lookupExecutionInputReady());
		assertEquals("source-review-blocks-draft-coefficient-payload", racingStatic.message());

		PropellerArchiveCtCpJLookupCoefficientPayloadDraft.CoefficientPayloadDraftRow apMid =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraft.draft(
						"apDrone", "mid_domain_mid_rpm", "j0-r0");
		assertEquals("LOCAL_IDW_8_NEIGHBOR_DRAFT", apMid.draftCoefficientSourceKind());
		assertEquals(0.106433175, apMid.draftCtCoefficient(), 1.0e-12);
		assertEquals(0.082779485, apMid.draftCpCoefficient(), 1.0e-12);
		assertEquals(0.418020889, apMid.draftEtaAtSlot(), 1.0e-12);
		assertEquals(8, apMid.fitNeighborRowCount());
		assertFalse(apMid.directStaticSampleUsed());
		assertEquals(0.042344994, apMid.nearestSourceNormalizedDistance(), 1.0e-12);
		assertTrue(apMid.etaFormulaConsistent());
	}

	@Test
	void negativeHighDomainDraftRemainsDiagnosticOnly() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraft.CoefficientPayloadDraftRow heavyHigh =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraft.draft(
						"heavyLift", "high_domain_max_rpm", "j1-rmax");

		assertEquals("volume3_exp.csv", heavyHigh.sourceArchiveFileName());
		assertEquals(-0.008978626, heavyHigh.draftCtCoefficient(), 1.0e-12);
		assertEquals(0.006045, heavyHigh.draftCpCoefficient(), 1.0e-12);
		assertEquals(-0.990693680, heavyHigh.draftEtaAtSlot(), 1.0e-12);
		assertFalse(heavyHigh.nonnegativeDraftCt());
		assertTrue(heavyHigh.positiveDraftCp());
		assertTrue(heavyHigh.etaFormulaConsistent());
		assertFalse(heavyHigh.coefficientPayloadDraftReady());
		assertFalse(heavyHigh.lookupExecutionInputReady());
		assertFalse(heavyHigh.runtimeCouplingAllowed());
		assertFalse(heavyHigh.gameplayAutoApplyAllowed());
	}

	@Test
	void summarySeparatesDraftCoverageFromReviewedPayloadReadiness() {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraft.CoefficientPayloadDraftSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraft.audit().summary();

		assertEquals(21, summary.draftRowCount());
		assertEquals(3, summary.directStaticDraftCount());
		assertEquals(18, summary.localIdwDraftCount());
		assertEquals(21, summary.finiteDraftCoefficientCount());
		assertEquals(20, summary.nonnegativeDraftCtCount());
		assertEquals(1, summary.negativeDraftCtCount());
		assertEquals(21, summary.positiveDraftCpCount());
		assertEquals(21, summary.etaFormulaConsistentCount());
		assertEquals(0, summary.sourceRowsReviewedCount());
		assertEquals(0, summary.coefficientPayloadReviewedCount());
		assertEquals(0, summary.coefficientPayloadDraftReadyCount());
		assertEquals(0, summary.lookupExecutionInputReadyCount());
		assertEquals(14, summary.postReviewFullSimulationDraftCount());
		assertEquals(7, summary.postReviewPerformanceOnlyDraftCount());
		assertEquals(0.218215047, summary.maxNearestSourceNormalizedDistance(), 1.0e-12);
		assertEquals(0.316538365, summary.maxFitSourceNormalizedDistance(), 1.0e-12);
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
	}

	@Test
	void rejectsUnknownDraftTargets() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadDraft.draft(
						"cinewhoop", "static_anchor_low_rpm", "static-anchor"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadDraft.draft(
						"racingQuad", "mid_domain_mid_rpm", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadDraft.CtCpJLookupCoefficientPayloadDraftAudit audit =
				PropellerArchiveCtCpJLookupCoefficientPayloadDraft.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft,racingQuad,static_anchor_low_rpm,static-anchor,BLOCKED,draft_ct_coefficient,0.139069,coefficient,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft,apDrone,mid_domain_mid_rpm,j0-r0,BLOCKED,draft_ct_coefficient,0.106433175,coefficient,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft,heavyLift,high_domain_max_rpm,j1-rmax,BLOCKED,draft_ct_coefficient,-0.008978626,coefficient,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_summary,all,negative_draft_ct_count,,BLOCKED,negative_draft_ct_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_summary,all,lookup_execution_input_ready_count,,BLOCKED,lookup_execution_input_ready_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_draft_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
