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

class PropellerArchiveCompactReferenceTableTest {
	@Test
	void auditBuildsBlockedCurrentCompactReferenceRows() {
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit audit =
				PropellerArchiveCompactReferenceTable.audit();

		assertEquals("User-Propeller-Archive-Compact-Reference-Table-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("export-disabled"));
		assertEquals(139, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.referenceSampleCount());
		assertEquals(30, audit.referenceMetricRowCount());
		assertEquals(12, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.rows().size());

		for (PropellerArchiveCompactReferenceTable.CompactReferenceRow row : audit.rows()) {
			assertFalse(row.curveFitAcceptanceReady());
			assertFalse(row.referenceMaterialExportAllowed());
			assertFalse(row.referenceRowAvailable());
			assertEquals(0.0, row.ctCpEtaReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.staticAnchorReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.geometryReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("reference-handoff-blocked", row.message());
			assertEquals("compact-propeller-ct-cp-eta-geometry-reference", row.referencePayloadKind());
		}

		PropellerArchiveCompactReferenceTable.CompactReferenceRow racing =
				PropellerArchiveCompactReferenceTable.row("racingQuad");
		assertEquals("da4052 5.0x3.75 - 3", racing.performanceMatchId());
		assertEquals("da4052 5.0x3.75", racing.geometryMatchId());
		assertEquals(0.8128, racing.advanceRatioMax(), 1.0e-12);
		assertEquals(7_946.7, racing.rpmMax(), 1.0e-12);
		assertEquals(0.148290142857143, racing.staticCtAnchorCandidate(), 1.0e-15);
		assertEquals(0.103175285714286, racing.staticCpAnchorCandidate(), 1.0e-15);
		assertTrue(racing.geometryStationAvailable());
		assertEquals(0.2068, racing.chordToRadius(), 1.0e-12);
		assertEquals(20.912, racing.betaDegrees(), 1.0e-12);

		PropellerArchiveCompactReferenceTable.CompactReferenceRow heavy =
				PropellerArchiveCompactReferenceTable.row("heavyLift");
		assertEquals("ancf 10.0x5.0 - 2", heavy.performanceMatchId());
		assertEquals("missing-reviewed-geometry-match", heavy.geometryMatchId());
		assertFalse(heavy.geometryStationAvailable());
		assertEquals(0.0, heavy.chordToRadius(), 1.0e-12);
		assertEquals(0.0, heavy.betaDegrees(), 1.0e-12);

		assertEquals(4, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().referenceRowAvailableCount());
		assertEquals(4, audit.extrema().blockedRowCount());
		assertEquals(3, audit.extrema().geometryStationAvailableCount());
		assertEquals(0, audit.extrema().referenceMaterialExportAllowedRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(1.0674, audit.extrema().maxAdvanceRatio(), 1.0e-12);
		assertEquals(15_063.0, audit.extrema().maxRpm(), 1.0e-12);
		assertEquals(0.192041923076923, audit.extrema().maxStaticCtAnchorCandidate(), 1.0e-15);
		assertEquals(0.0, audit.extrema().maxCtCpEtaReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxGeometryReferenceWeight(), 1.0e-12);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceTable.row("missing"));
	}

	@Test
	void readyHandoffEnablesOnlyRowsWithCurrentGeometryEvidence() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				findHandoff("acceptance_ready_reference_reviewed");
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit audit =
				PropellerArchiveCompactReferenceTable.audit(handoff);

		assertEquals(4, audit.extrema().referenceMaterialExportAllowedRowCount());
		assertEquals(3, audit.extrema().referenceRowAvailableCount());
		assertEquals(1, audit.extrema().blockedRowCount());
		assertEquals(1.0, audit.extrema().maxCtCpEtaReferenceWeight(), 1.0e-12);
		assertEquals(1.0, audit.extrema().maxGeometryReferenceWeight(), 1.0e-12);
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());

		for (PropellerArchiveCompactReferenceTable.CompactReferenceRow row : audit.rows()) {
			assertTrue(row.referenceMaterialExportAllowed());
			assertTrue(row.curveFitAcceptanceReady());
			assertEquals(1.0, row.ctCpEtaReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.staticAnchorReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			if ("heavyLift".equals(row.presetName())) {
				assertFalse(row.referenceRowAvailable());
				assertEquals(0.0, row.geometryReferenceWeight(), 1.0e-12);
				assertEquals("reference-row-geometry-source-missing", row.message());
			} else {
				assertTrue(row.referenceRowAvailable());
				assertEquals(1.0, row.geometryReferenceWeight(), 1.0e-12);
				assertEquals("AVAILABLE", row.status());
				assertEquals("compact-propeller-reference-row-available", row.message());
			}
		}
	}

	@Test
	void tableRejectsInvalidInputs() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				findHandoff("acceptance_ready_reference_reviewed");

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceTable.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceTable.row(
						null, PropellerArchiveDatasetTriage.presetMatch("racingQuad")));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceTable.row(handoff, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit audit =
				PropellerArchiveCompactReferenceTable.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_compact_reference_table_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_table_summary,all_rows,reference_row_available_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_table_summary,all_rows,runtime_coupling_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_table,racingQuad,performance_match_id,da4052 5.0x3.75 - 3,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_table,heavyLift,geometry_match_id,missing-reviewed-geometry-match,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_table,heavyLift,geometry_reference_weight,0,weight,")));
	}

	private static PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary findHandoff(String name) {
		return PropellerArchiveCompactReferenceHandoff.audit().scenarios().stream()
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
							"docs/data/propeller_archive_compact_reference_table_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
