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

class PropellerArchiveSourceFingerprintTest {
	@Test
	void auditIdentifiesExternalArchiveWithoutVendoringRawRows() {
		PropellerArchiveSourceFingerprint.SourceFingerprintAudit audit =
				PropellerArchiveSourceFingerprint.audit();

		assertEquals("User-Propeller-Archive-Source-Fingerprint-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("raw rows stay outside"));
		assertEquals(26, audit.packetRowCount());
		assertEquals(1, audit.archiveRowCount());
		assertEquals(6, audit.entryRowCount());
		assertEquals(2, audit.schemaRowCount());
		assertEquals(6, audit.boundaryRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());

		PropellerArchiveSourceFingerprint.ArchiveFingerprint archive = audit.archive();
		assertEquals("archive.zip", archive.archiveName());
		assertEquals("f234863d7a0c29f44733bc818790c75616a61a2980fc30577a43122cc2cd1b2b",
				archive.archiveSha256());
		assertEquals(384_834, archive.archiveBytes());
		assertEquals(6, archive.expectedEntryCount());
		assertFalse(archive.rawRowsVendored());
		assertFalse(archive.currentImportAllowed());
		assertFalse(archive.runtimeCouplingAllowed());
		assertFalse(archive.gameplayAutoApplyAllowed());
		assertEquals("review-source-license-before-importing-raw-rows",
				archive.nextRequiredAction());
	}

	@Test
	void entryFingerprintsCarryStableHashesAndRowCounts() {
		PropellerArchiveSourceFingerprint.ArchiveEntryFingerprint volume1 =
				PropellerArchiveSourceFingerprint.entry("volume1_exp.csv");
		assertEquals("performance", volume1.tableKind());
		assertEquals("4af1490043202ebbebe446898eb5d75af0c8218cf2cc6ed928d628053f097cfb",
				volume1.sha256());
		assertEquals(1_335_172, volume1.uncompressedBytes());
		assertEquals(189_118, volume1.compressedBytes());
		assertEquals(16_455, volume1.dataRowCount());
		assertEquals("PropName;BladeName;Family;B;D;P;J;N;CT;CP;eta",
				volume1.headerColumns());
		assertTrue(volume1.matchesImportContractHeader());
		assertFalse(volume1.rawRowsVendored());

		PropellerArchiveSourceFingerprint.ArchiveEntryFingerprint geometry =
				PropellerArchiveSourceFingerprint.entry("volume3_geom.csv");
		assertEquals("geometry", geometry.tableKind());
		assertEquals("4c917b2673ec38df65b83926f8e35185a2cfe1598b86b3a4de2a069a121bfd09",
				geometry.sha256());
		assertEquals(78, geometry.dataRowCount());
		assertEquals("BladeName;Family;D;P;c/R;r/R;beta", geometry.headerColumns());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveSourceFingerprint.entry("missing.csv"));
	}

	@Test
	void schemasMatchImportContractAndDatasetTriageCounts() {
		PropellerArchiveSourceFingerprint.SchemaFingerprint performance =
				PropellerArchiveSourceFingerprint.schema("performance");
		assertEquals(11, performance.columnCount());
		assertEquals(3, performance.entryCount());
		assertEquals(27_495, performance.dataRowCount());
		assertTrue(performance.matchesImportContract());
		assertTrue(performance.rowCountMatchesDatasetTriage());
		assertEquals("PropellerArchiveFitImportContract.performance",
				performance.importContractScope());

		PropellerArchiveSourceFingerprint.SchemaFingerprint geometry =
				PropellerArchiveSourceFingerprint.schema("geometry");
		assertEquals(7, geometry.columnCount());
		assertEquals(2_316, geometry.dataRowCount());
		assertEquals("PropellerArchiveFitImportContract.geometry",
				geometry.importContractScope());

		PropellerArchiveSourceFingerprint.SourceFingerprintSummary summary =
				PropellerArchiveSourceFingerprint.audit().summary();
		assertEquals(3, summary.performanceEntryCount());
		assertEquals(3, summary.geometryEntryCount());
		assertEquals(27_495, summary.performanceDataRowCount());
		assertEquals(2_316, summary.geometryDataRowCount());
		assertEquals(6, summary.entryHashCount());
		assertEquals(2, summary.schemaMatchCount());
		assertEquals(0, summary.rawRowsVendoredCount());
		assertFalse(summary.currentImportAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveSourceFingerprint.schema("missing"));
	}

	@Test
	void boundariesKeepImportBlockedOnLicenseReviewAndRuntimeClosed() {
		PropellerArchiveSourceFingerprint.FingerprintBoundary hash =
				PropellerArchiveSourceFingerprint.boundary("archive_sha256_known");
		assertTrue(hash.currentSatisfied());
		assertTrue(hash.requiredForReviewedImport());
		assertFalse(hash.blocksCurrentImport());
		assertEquals("READY", hash.status());

		PropellerArchiveSourceFingerprint.FingerprintBoundary license =
				PropellerArchiveSourceFingerprint.boundary("source_license_review");
		assertFalse(license.currentSatisfied());
		assertTrue(license.requiredForReviewedImport());
		assertTrue(license.blocksCurrentImport());
		assertEquals("BLOCKED", license.status());
		assertEquals("review-source-license-before-importing-raw-rows",
				license.nextRequiredAction());

		PropellerArchiveSourceFingerprint.FingerprintBoundary guard =
				PropellerArchiveSourceFingerprint.boundary("runtime_leak_guard");
		assertTrue(guard.currentSatisfied());
		assertFalse(guard.blocksCurrentImport());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed",
				guard.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveSourceFingerprint.boundary("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveSourceFingerprint.SourceFingerprintAudit audit =
				PropellerArchiveSourceFingerprint.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_source_fingerprint_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_source_fingerprint_archive,archive.zip,archive_sha256,f234863d7a0c29f44733bc818790c75616a61a2980fc30577a43122cc2cd1b2b,sha256,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_source_fingerprint_entry,volume1_exp.csv,entry_sha256,4af1490043202ebbebe446898eb5d75af0c8218cf2cc6ed928d628053f097cfb,sha256,user_supplied_archive_zip,PropName;BladeName;Family;B;D;P;J;N;CT;CP;eta,16455,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_source_fingerprint_schema,performance,data_rows,27495,count,user_supplied_archive_zip,PropName;BladeName;Family;B;D;P;J;N;CT;CP;eta,27495,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_source_fingerprint_boundary,source_license_review,current_satisfied,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_source_fingerprint_summary,all,raw_rows_vendored_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_source_fingerprint_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
