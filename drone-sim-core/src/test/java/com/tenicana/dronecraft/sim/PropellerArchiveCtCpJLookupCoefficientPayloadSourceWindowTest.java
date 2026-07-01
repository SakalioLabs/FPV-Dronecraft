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

class PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindowTest {
	@Test
	void auditBindsReviewedGridSlotsToArchiveSourceWindows() {
		PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CtCpJLookupCoefficientPayloadSourceWindowAudit
				audit = PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Source-Window-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("compact archive J/RPM support"));
		assertEquals(46, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(21, audit.sourceWindowRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(21, audit.rows().size());
	}

	@Test
	void staticAnchorsKeepDirectArchiveSampleCandidatesWhileNonstaticSlotsRequireFits() {
		PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow racingStatic =
				PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.window(
						"racingQuad", "static_anchor_low_rpm", "static-anchor");
		assertEquals("volume2_exp.csv", racingStatic.sourceArchiveFileName());
		assertEquals(63, racingStatic.sourcePerformanceSampleRowCount());
		assertEquals(14, racingStatic.sourceStaticSampleRowCount());
		assertEquals(49, racingStatic.sourceNonstaticSampleRowCount());
		assertEquals(18, racingStatic.sourceDistinctRpmBinCount());
		assertEquals(0.0, racingStatic.lowerSourceAdvanceRatioJ(), 1.0e-12);
		assertEquals(1477.778, racingStatic.lowerSourceRpm(), 1.0e-12);
		assertEquals(1, racingStatic.directArchiveSampleCount());
		assertTrue(racingStatic.directArchiveSampleAvailable());
		assertTrue(racingStatic.directStaticAnchorBinding());
		assertFalse(racingStatic.scatteredFitRequired());
		assertTrue(racingStatic.archiveCurveShapeGuardPassed());
		assertFalse(racingStatic.sourceRowsReviewed());
		assertFalse(racingStatic.payloadSourceWindowReady());
		assertEquals("source-review-blocks-direct-static-payload-binding", racingStatic.message());

		PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow racingMid =
				PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.window(
						"racingQuad", "mid_domain_mid_rpm", "j0-r0");
		assertEquals(0.324629, racingMid.lowerSourceAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.329413, racingMid.upperSourceAdvanceRatioJ(), 1.0e-12);
		assertEquals(4025.0, racingMid.lowerSourceRpm(), 1.0e-12);
		assertEquals(4500.0, racingMid.upperSourceRpm(), 1.0e-12);
		assertEquals(0, racingMid.directArchiveSampleCount());
		assertFalse(racingMid.directArchiveSampleAvailable());
		assertFalse(racingMid.directStaticAnchorBinding());
		assertTrue(racingMid.scatteredFitRequired());
		assertEquals("source-review-blocks-scattered-fit-payload-source-window", racingMid.message());

		PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow heavyHigh =
				PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.window(
						"heavyLift", "high_domain_max_rpm", "j1-rmax");
		assertEquals("volume3_exp.csv", heavyHigh.sourceArchiveFileName());
		assertEquals(128, heavyHigh.sourcePerformanceSampleRowCount());
		assertEquals(0.667043, heavyHigh.lowerSourceAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.667043, heavyHigh.upperSourceAdvanceRatioJ(), 1.0e-12);
		assertEquals(7440.0, heavyHigh.lowerSourceRpm(), 1.0e-12);
		assertFalse(heavyHigh.postReviewFullSimulationLookupAllowed());
	}

	@Test
	void summaryKeepsPayloadSourceWindowsReviewBlockedAndRuntimeClosed() {
		PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowSummary summary =
				PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.audit().summary();

		assertEquals(21, summary.sourceWindowRowCount());
		assertEquals(3, summary.directStaticAnchorSourceWindowCount());
		assertEquals(18, summary.scatteredFitSourceWindowCount());
		assertEquals(3, summary.directArchiveSampleAvailableSlotCount());
		assertEquals(0, summary.directNonstaticArchiveSampleSlotCount());
		assertEquals(14, summary.postReviewFullSimulationSourceWindowCount());
		assertEquals(7, summary.postReviewPerformanceOnlySourceWindowCount());
		assertEquals(21, summary.archiveCurveShapeGuardReadySlotCount());
		assertEquals(0, summary.sourceRowsReviewedSlotCount());
		assertEquals(0, summary.coefficientPayloadReviewedSlotCount());
		assertEquals(0, summary.payloadSourceWindowReadySlotCount());
		assertEquals(0.022381, summary.maxSourceJBracketWidth(), 1.0e-12);
		assertEquals(500.0, summary.maxSourceRpmBracketWidth(), 1.0e-12);
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("review-source-license-then-fill-coefficient-payload-source-windows",
				summary.nextRequiredAction());
	}

	@Test
	void rejectsUnknownPayloadSourceWindowTargets() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.window(
						"cinewhoop", "static_anchor_low_rpm", "static-anchor"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.window(
						"racingQuad", "mid_domain_mid_rpm", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CtCpJLookupCoefficientPayloadSourceWindowAudit
				audit = PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_source_window_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_source_window,racingQuad,static_anchor_low_rpm,static-anchor,BLOCKED,direct_archive_sample_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_source_window,racingQuad,mid_domain_mid_rpm,j0-r0,BLOCKED,direct_archive_sample_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_source_window,heavyLift,high_domain_max_rpm,j1-rmax,BLOCKED,direct_archive_sample_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_source_window_summary,all,source_window_row_count,,BLOCKED,source_window_row_count,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_coefficient_payload_source_window_summary,all,payload_source_window_ready_slot_count,,BLOCKED,payload_source_window_ready_slot_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_coefficient_payload_source_window_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
