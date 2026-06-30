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

class PropellerArchiveCtCpJLookupReferenceTableTest {
	@Test
	void auditBuildsBlockedCurrentLookupReferenceRows() {
		PropellerArchiveCtCpJLookupReferenceTable.CtCpJLookupReferenceTableAudit audit =
				PropellerArchiveCtCpJLookupReferenceTable.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Reference-Table-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("zero weights"));
		assertEquals(32, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(9, audit.referenceRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(9, audit.rows().size());

		for (PropellerArchiveCtCpJLookupReferenceTable.LookupReferenceRow row : audit.rows()) {
			assertFalse(row.lookupAcceptanceReady());
			assertFalse(row.compactReferenceReviewed());
			assertFalse(row.referenceMaterialExportAllowed());
			assertFalse(row.performanceReferenceRowAvailable());
			assertFalse(row.fullSimulationReferenceRowAvailable());
			assertEquals(0.0, row.ctReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.cpReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.etaReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.staticAnchorReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.fullSimulationReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("lookup-acceptance-not-ready", row.message());
			assertEquals("compact-propeller-ct-cp-j-lookup-reference", row.referencePayloadKind());
		}

		PropellerArchiveCtCpJLookupReferenceTable.LookupReferenceRow apMid =
				PropellerArchiveCtCpJLookupReferenceTable.row("apDrone", "mid_domain_mid_rpm");
		assertEquals("da4052 5.0x3.75 - 3", apMid.performanceMatchId());
		assertEquals("da4052 5.0x3.75", apMid.geometryMatchId());
		assertEquals(0.4064, apMid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_712.25, apMid.queryRpm(), 1.0e-9);
		assertEquals(0.4064 / Math.PI, apMid.equivalentProjectMu(), 1.0e-12);
		assertEquals(4, apMid.minimumNeighborRows());
		assertFalse(apMid.staticAnchorReferenceRow());

		assertEquals(9, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().performanceReferenceRowAvailableCount());
		assertEquals(0, audit.extrema().fullSimulationReferenceRowAvailableCount());
		assertEquals(0, audit.extrema().performanceOnlyReferenceRowAvailableCount());
		assertEquals(9, audit.extrema().blockedRowCount());
		assertEquals(3, audit.extrema().staticAnchorReferenceRowCount());
		assertEquals(0, audit.extrema().staticAnchorReferenceAvailableCount());
		assertEquals(4, audit.extrema().maxMinimumNeighborRows());
		assertEquals(21, audit.extrema().totalMinimumNeighborRows());
		assertEquals(0.0, audit.extrema().maxCtReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxFullSimulationReferenceWeight(), 1.0e-12);
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceTable.row("apDrone", "missing"));
	}

	@Test
	void acceptedReviewedLookupEnablesPerformanceRowsAndFullSimulationSubset() {
		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary accepted =
				handoff("acceptance_ready_reference_reviewed");
		PropellerArchiveCtCpJLookupReferenceTable.CtCpJLookupReferenceTableAudit audit =
				PropellerArchiveCtCpJLookupReferenceTable.audit(accepted);

		assertEquals(9, audit.extrema().performanceReferenceRowAvailableCount());
		assertEquals(6, audit.extrema().fullSimulationReferenceRowAvailableCount());
		assertEquals(3, audit.extrema().performanceOnlyReferenceRowAvailableCount());
		assertEquals(0, audit.extrema().blockedRowCount());
		assertEquals(3, audit.extrema().staticAnchorReferenceAvailableCount());
		assertEquals(1.0, audit.extrema().maxCtReferenceWeight(), 1.0e-12);
		assertEquals(1.0, audit.extrema().maxFullSimulationReferenceWeight(), 1.0e-12);
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());

		for (PropellerArchiveCtCpJLookupReferenceTable.LookupReferenceRow row : audit.rows()) {
			assertTrue(row.lookupAcceptanceReady());
			assertTrue(row.compactReferenceReviewed());
			assertTrue(row.referenceMaterialExportAllowed());
			assertTrue(row.performanceReferenceRowAvailable());
			assertEquals(1.0, row.ctReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.cpReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.etaReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("AVAILABLE", row.status());
			if ("heavyLift".equals(row.presetName())) {
				assertFalse(row.fullSimulationReferenceRowAvailable());
				assertEquals(0.0, row.fullSimulationReferenceWeight(), 1.0e-12);
				assertEquals("performance-reference-only-full-simulation-blocked", row.message());
			} else {
				assertTrue(row.fullSimulationReferenceRowAvailable());
				assertEquals(1.0, row.fullSimulationReferenceWeight(), 1.0e-12);
				assertEquals("ct-cp-j-lookup-reference-row-available", row.message());
			}
		}
	}

	@Test
	void acceptedButUnreviewedReferenceKeepsRowsBlocked() {
		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary acceptedUnreviewed =
				handoff("acceptance_ready_reference_review_missing");
		PropellerArchiveCtCpJLookupReferenceTable.CtCpJLookupReferenceTableAudit audit =
				PropellerArchiveCtCpJLookupReferenceTable.audit(acceptedUnreviewed);

		assertEquals(0, audit.extrema().performanceReferenceRowAvailableCount());
		assertEquals(9, audit.extrema().blockedRowCount());
		for (PropellerArchiveCtCpJLookupReferenceTable.LookupReferenceRow row : audit.rows()) {
			assertTrue(row.lookupAcceptanceReady());
			assertFalse(row.compactReferenceReviewed());
			assertFalse(row.referenceMaterialExportAllowed());
			assertEquals("lookup-reference-review-missing", row.message());
		}
	}

	@Test
	void tableRejectsInvalidInputs() {
		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary accepted =
				handoff("acceptance_ready_reference_reviewed");
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target =
				PropellerArchiveCtCpJLookupAcceptanceGate.target("apDrone", "mid_domain_mid_rpm");

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceTable.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceTable.row(null, target));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReferenceTable.row(accepted, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupReferenceTable.CtCpJLookupReferenceTableAudit audit =
				PropellerArchiveCtCpJLookupReferenceTable.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_reference_table_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference,apDrone,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference,heavyLift,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_summary,all_rows,performance_reference_row_available_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_summary,all_rows,total_minimum_neighbor_rows,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reference_summary,all_rows,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary handoff(String name) {
		return PropellerArchiveCtCpJLookupReferenceHandoff.audit().scenarios().stream()
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
							"docs/data/propeller_archive_ct_cp_j_lookup_reference_table_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
