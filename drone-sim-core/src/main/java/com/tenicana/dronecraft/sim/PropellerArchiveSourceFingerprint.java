package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveSourceFingerprint {
	public static final String SOURCE_ID = "User-Propeller-Archive-Source-Fingerprint-Packet";
	public static final String CAVEAT =
			"Source fingerprint identifies the external archive.zip and CSV entry schemas only; raw rows stay outside the repository until source/license review and no runtime or gameplay use is enabled.";
	public static final int ARCHIVE_ROW_COUNT = 1;
	public static final int ENTRY_ROW_COUNT = 6;
	public static final int SCHEMA_ROW_COUNT = 2;
	public static final int BOUNDARY_ROW_COUNT = 6;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = ARCHIVE_ROW_COUNT
			+ ENTRY_ROW_COUNT
			+ SCHEMA_ROW_COUNT
			+ BOUNDARY_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String ARCHIVE_NAME = "archive.zip";
	public static final String ARCHIVE_SHA256 =
			"f234863d7a0c29f44733bc818790c75616a61a2980fc30577a43122cc2cd1b2b";
	public static final int ARCHIVE_BYTES = 384_834;
	public static final String PERFORMANCE_HEADER =
			"PropName,BladeName,Family,B,D,P,J,N,CT,CP,eta";
	public static final String GEOMETRY_HEADER =
			"BladeName,Family,D,P,c/R,r/R,beta";
	public static final boolean RAW_ROWS_VENDORED = false;
	public static final boolean CURRENT_IMPORT_ALLOWED = false;
	public static final boolean RUNTIME_COUPLING_ALLOWED = false;
	public static final boolean GAMEPLAY_AUTO_APPLY_ALLOWED = false;
	public static final String NEXT_REQUIRED_ACTION =
			"review-source-license-before-importing-raw-rows";

	private static final List<ArchiveEntryFingerprint> ENTRIES = List.of(
			new ArchiveEntryFingerprint(
					"volume1_exp.csv",
					"performance",
					"4af1490043202ebbebe446898eb5d75af0c8218cf2cc6ed928d628053f097cfb",
					1_335_172,
					189_118,
					16_455,
					PERFORMANCE_HEADER,
					true,
					false
			),
			new ArchiveEntryFingerprint(
					"volume1_geom.csv",
					"geometry",
					"e7d0f9af7bc013e1a32d3a459fb8159f371a6969dd5c069aae1096d5f2e77172",
					64_513,
					10_468,
					1_422,
					GEOMETRY_HEADER,
					true,
					false
			),
			new ArchiveEntryFingerprint(
					"volume2_exp.csv",
					"performance",
					"a4b2d9c3f917c16d2bcee44b1239cacfa11ae9590978d476277d87422e6e56bd",
					483_556,
					89_060,
					5_050,
					PERFORMANCE_HEADER,
					true,
					false
			),
			new ArchiveEntryFingerprint(
					"volume2_geom.csv",
					"geometry",
					"4eed89c2742ec521afc556ea794e5c1db51420e95698a822c2e1d662b0208b48",
					42_103,
					7_296,
					816,
					GEOMETRY_HEADER,
					true,
					false
			),
			new ArchiveEntryFingerprint(
					"volume3_exp.csv",
					"performance",
					"6f66d3d0122a9441870904579f3301feff17c97ac3280be5d235cffbd29871ac",
					550_305,
					87_372,
					5_990,
					PERFORMANCE_HEADER,
					true,
					false
			),
			new ArchiveEntryFingerprint(
					"volume3_geom.csv",
					"geometry",
					"4c917b2673ec38df65b83926f8e35185a2cfe1598b86b3a4de2a069a121bfd09",
					4_572,
					616,
					78,
					GEOMETRY_HEADER,
					true,
					false
			)
	);

	private static final List<SchemaFingerprint> SCHEMAS = List.of(
			new SchemaFingerprint(
					"performance",
					PERFORMANCE_HEADER,
					11,
					3,
					PropellerArchiveDatasetTriage.EXPERIMENT_ROW_COUNT,
					true,
					true,
					"PropellerArchiveFitImportContract.performance"
			),
			new SchemaFingerprint(
					"geometry",
					GEOMETRY_HEADER,
					7,
					3,
					PropellerArchiveDatasetTriage.GEOMETRY_ROW_COUNT,
					true,
					true,
					"PropellerArchiveFitImportContract.geometry"
			)
	);

	private static final List<FingerprintBoundary> BOUNDARIES = List.of(
			new FingerprintBoundary("archive_sha256_known", true, true, false, "READY",
					"compare-local-archive-hash-before-reviewed-import"),
			new FingerprintBoundary("entry_sha256_manifest", true, true, false, "READY",
					"compare-entry-hashes-before-row-normalization"),
			new FingerprintBoundary("schema_headers_match_contract", true, true, false, "READY",
					"validate-performance-and-geometry-headers"),
			new FingerprintBoundary("dataset_triage_row_counts_match", true, true, false, "READY",
					"reuse-triage-row-counts-for-coverage-gates"),
			new FingerprintBoundary("source_license_review", false, true, true, "BLOCKED",
					NEXT_REQUIRED_ACTION),
			new FingerprintBoundary("runtime_leak_guard", true, true, false, "READY",
					"keep-runtime-coupling-and-gameplay-auto-apply-closed")
	);

	private PropellerArchiveSourceFingerprint() {
	}

	public record ArchiveFingerprint(
			String archiveName,
			String archiveSha256,
			int archiveBytes,
			int expectedEntryCount,
			boolean rawRowsVendored,
			boolean currentImportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction
	) {
	}

	public record ArchiveEntryFingerprint(
			String entryName,
			String tableKind,
			String sha256,
			int uncompressedBytes,
			int compressedBytes,
			int dataRowCount,
			String headerColumns,
			boolean matchesImportContractHeader,
			boolean rawRowsVendored
	) {
	}

	public record SchemaFingerprint(
			String tableKind,
			String headerColumns,
			int columnCount,
			int entryCount,
			int dataRowCount,
			boolean matchesImportContract,
			boolean rowCountMatchesDatasetTriage,
			String importContractScope
	) {
	}

	public record FingerprintBoundary(
			String boundaryName,
			boolean currentSatisfied,
			boolean requiredForReviewedImport,
			boolean blocksCurrentImport,
			String status,
			String nextRequiredAction
	) {
	}

	public record SourceFingerprintSummary(
			int archiveBytes,
			int archiveEntryCount,
			int performanceEntryCount,
			int geometryEntryCount,
			int performanceDataRowCount,
			int geometryDataRowCount,
			int entryHashCount,
			int schemaMatchCount,
			int rawRowsVendoredCount,
			boolean currentImportAllowed,
			String nextRequiredAction
	) {
	}

	public record SourceFingerprintAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int archiveRowCount,
			int entryRowCount,
			int schemaRowCount,
			int boundaryRowCount,
			int summaryRowCount,
			int methodRowCount,
			ArchiveFingerprint archive,
			List<ArchiveEntryFingerprint> entries,
			List<SchemaFingerprint> schemas,
			List<FingerprintBoundary> boundaries,
			SourceFingerprintSummary summary
	) {
		public SourceFingerprintAudit {
			entries = List.copyOf(entries);
			schemas = List.copyOf(schemas);
			boundaries = List.copyOf(boundaries);
		}
	}

	public static SourceFingerprintAudit audit() {
		return new SourceFingerprintAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				ARCHIVE_ROW_COUNT,
				ENTRY_ROW_COUNT,
				SCHEMA_ROW_COUNT,
				BOUNDARY_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				new ArchiveFingerprint(
						ARCHIVE_NAME,
						ARCHIVE_SHA256,
						ARCHIVE_BYTES,
						ENTRY_ROW_COUNT,
						RAW_ROWS_VENDORED,
						CURRENT_IMPORT_ALLOWED,
						RUNTIME_COUPLING_ALLOWED,
						GAMEPLAY_AUTO_APPLY_ALLOWED,
						NEXT_REQUIRED_ACTION
				),
				ENTRIES,
				SCHEMAS,
				BOUNDARIES,
				summary()
		);
	}

	public static ArchiveEntryFingerprint entry(String entryName) {
		return ENTRIES.stream()
				.filter(entry -> entry.entryName().equals(entryName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown archive entry: " + entryName));
	}

	public static SchemaFingerprint schema(String tableKind) {
		return SCHEMAS.stream()
				.filter(schema -> schema.tableKind().equals(tableKind))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown archive schema: " + tableKind));
	}

	public static FingerprintBoundary boundary(String boundaryName) {
		return BOUNDARIES.stream()
				.filter(boundary -> boundary.boundaryName().equals(boundaryName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown fingerprint boundary: " + boundaryName));
	}

	private static SourceFingerprintSummary summary() {
		int performanceEntryCount = 0;
		int geometryEntryCount = 0;
		int performanceRows = 0;
		int geometryRows = 0;
		int entryHashes = 0;
		int rawRowsVendored = 0;
		for (ArchiveEntryFingerprint entry : ENTRIES) {
			if ("performance".equals(entry.tableKind())) {
				performanceEntryCount++;
				performanceRows += entry.dataRowCount();
			}
			if ("geometry".equals(entry.tableKind())) {
				geometryEntryCount++;
				geometryRows += entry.dataRowCount();
			}
			if (!entry.sha256().isBlank()) {
				entryHashes++;
			}
			if (entry.rawRowsVendored()) {
				rawRowsVendored++;
			}
		}
		int schemaMatches = 0;
		for (SchemaFingerprint schema : SCHEMAS) {
			if (schema.matchesImportContract() && schema.rowCountMatchesDatasetTriage()) {
				schemaMatches++;
			}
		}
		return new SourceFingerprintSummary(
				ARCHIVE_BYTES,
				ENTRIES.size(),
				performanceEntryCount,
				geometryEntryCount,
				performanceRows,
				geometryRows,
				entryHashes,
				schemaMatches,
				rawRowsVendored,
				CURRENT_IMPORT_ALLOWED,
				NEXT_REQUIRED_ACTION
		);
	}
}
