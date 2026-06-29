package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveDatasetTriage {
	public static final String SOURCE_ID = "User-Propeller-Archive-Dataset-Triage-Packet";
	public static final String CAVEAT =
			"User-supplied archive contains propeller experimental CT/CP/eta curves and blade geometry rows; raw rows are not vendored and require source/license review before fitting or runtime use.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int AGGREGATE_ROW_COUNT = 22;
	public static final int VOLUME_SUMMARY_ROW_COUNT = 6;
	public static final int BLADE_COUNT_DISTRIBUTION_ROW_COUNT = 3;
	public static final int PRESET_MATCH_ROW_COUNT = 4;
	public static final int GEOMETRY_MATCH_ROW_COUNT = 3;
	public static final int BOUNDARY_ROW_COUNT = 7;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ AGGREGATE_ROW_COUNT
			+ VOLUME_SUMMARY_ROW_COUNT
			+ BLADE_COUNT_DISTRIBUTION_ROW_COUNT
			+ PRESET_MATCH_ROW_COUNT
			+ GEOMETRY_MATCH_ROW_COUNT
			+ BOUNDARY_ROW_COUNT;
	public static final int ARCHIVE_ENTRY_COUNT = 6;
	public static final int EXPERIMENT_VOLUME_COUNT = 3;
	public static final int GEOMETRY_VOLUME_COUNT = 3;
	public static final int EXPERIMENT_ROW_COUNT = 27_495;
	public static final int GEOMETRY_ROW_COUNT = 2_316;
	public static final int UNIQUE_PROPELLER_COUNT = 240;
	public static final int EXPERIMENT_BLADE_COUNT = 226;
	public static final int GEOMETRY_BLADE_COUNT = 120;
	public static final int EXPERIMENT_FAMILY_COUNT = 32;
	public static final int GEOMETRY_FAMILY_COUNT = 26;
	public static final boolean RAW_ROWS_VENDORED = false;
	public static final boolean SOURCE_LICENSE_REVIEW_REQUIRED = true;
	public static final boolean DIRECT_A4MC_L2_EVIDENCE = false;
	public static final boolean ROTOR_CT_CP_CURVE_FIT_CANDIDATE = true;
	public static final boolean PROP_GEOMETRY_FIT_CANDIDATE = true;
	public static final boolean GAMEPLAY_AUTO_APPLY_ALLOWED = false;
	public static final String NEXT_REQUIRED_ACTION =
			"review-source-license-then-fit-ct-cp-j-re-and-geometry-curves";

	private static final List<ArchiveEntrySummary> ARCHIVE_ENTRIES = List.of(
			new ArchiveEntrySummary("volume1_exp.csv", "experiment", 1_335_172, 189_118),
			new ArchiveEntrySummary("volume1_geom.csv", "geometry", 64_513, 10_468),
			new ArchiveEntrySummary("volume2_exp.csv", "experiment", 483_556, 89_060),
			new ArchiveEntrySummary("volume2_geom.csv", "geometry", 42_103, 7_296),
			new ArchiveEntrySummary("volume3_exp.csv", "experiment", 550_305, 87_372),
			new ArchiveEntrySummary("volume3_geom.csv", "geometry", 4_572, 616)
	);

	private static final DatasetSummary DATASET_SUMMARY = new DatasetSummary(
			ARCHIVE_ENTRY_COUNT,
			EXPERIMENT_VOLUME_COUNT,
			GEOMETRY_VOLUME_COUNT,
			EXPERIMENT_ROW_COUNT,
			GEOMETRY_ROW_COUNT,
			UNIQUE_PROPELLER_COUNT,
			EXPERIMENT_BLADE_COUNT,
			GEOMETRY_BLADE_COUNT,
			EXPERIMENT_FAMILY_COUNT,
			GEOMETRY_FAMILY_COUNT,
			1.2,
			19.0,
			0.6,
			13.0,
			0.0,
			1.552,
			940.0,
			27_050.0,
			-0.12614,
			0.253789,
			0.0025,
			0.192791,
			-22.698791,
			0.840262,
			0.0021,
			0.3783,
			0.15,
			1.0,
			0.0,
			55.845
	);

	private static final List<ExperimentVolumeSummary> EXPERIMENT_VOLUMES = List.of(
			new ExperimentVolumeSummary(
					"volume1_exp.csv", 16_455, 139, 139, 19, 7.0, 19.0, 3.0, 13.0,
					0.0, 1.552, 1_261.0, 7_203.0, -0.0638, 0.1797, 0.0025, 0.1482,
					-15.915, 0.762
			),
			new ExperimentVolumeSummary(
					"volume2_exp.csv", 5_050, 63, 49, 14, 2.244, 9.0, 0.787, 8.95,
					0.0, 1.199, 1_317.8, 27_050.0, -0.12614, 0.25379, 0.00612, 0.19279,
					-22.69879, 0.73772
			),
			new ExperimentVolumeSummary(
					"volume3_exp.csv", 5_990, 40, 40, 1, 1.2, 16.0, 0.6, 13.0,
					0.0, 1.468, 940.0, 7_960.0, -0.02683, 0.13021, 0.00479, 0.13442,
					-4.16602, 0.84026
			)
	);

	private static final List<GeometryVolumeSummary> GEOMETRY_VOLUMES = List.of(
			new GeometryVolumeSummary(
					"volume1_geom.csv", 1_422, 79, 14, 9.0, 19.0, 3.8, 13.0,
					0.015, 0.247, 0.15, 1.0, 3.93, 55.01
			),
			new GeometryVolumeSummary(
					"volume2_geom.csv", 816, 42, 14, 2.244, 9.0, 0.787, 8.95,
					0.0021, 0.3783, 0.15, 1.0, 0.0, 55.845
			),
			new GeometryVolumeSummary(
					"volume3_geom.csv", 78, 5, 2, 5.0, 9.0, 3.157, 7.718,
					0.0605, 0.2926, 0.15, 1.0, 2.932, 40.279
			)
	);

	private static final List<BladeCountDistribution> BLADE_COUNT_DISTRIBUTION = List.of(
			new BladeCountDistribution(2, 26_615),
			new BladeCountDistribution(3, 501),
			new BladeCountDistribution(4, 379)
	);

	private static final List<PresetPerformanceMatch> PRESET_MATCHES = List.of(
			new PresetPerformanceMatch(
					"racingQuad",
					3,
					5.0,
					4.25,
					"da4052 5.0x3.75 - 3",
					"da4052",
					3,
					5.0,
					3.75,
					63,
					0.0,
					0.8128,
					1_477.8,
					7_946.7,
					14,
					0.148290142857143,
					0.139069,
					0.151686,
					0.103175285714286,
					0.094482,
					0.116804,
					0.576493,
					true,
					true,
					"closest-five-inch-three-blade-low-J-ct-cp-anchor"
			),
			new PresetPerformanceMatch(
					"apDrone",
					3,
					5.1,
					4.5,
					"da4052 5.0x3.75 - 3",
					"da4052",
					3,
					5.0,
					3.75,
					63,
					0.0,
					0.8128,
					1_477.8,
					7_946.7,
					14,
					0.148290142857143,
					0.139069,
					0.151686,
					0.103175285714286,
					0.094482,
					0.116804,
					0.576493,
					true,
					true,
					"same-five-inch-three-blade-anchor-but-under-pitched-for-current-target"
			),
			new PresetPerformanceMatch(
					"cinewhoop",
					3,
					2.9921,
					2.5433,
					"gwsdd 3.0x3.0 - 2",
					"gwsdd",
					2,
					3.0,
					3.0,
					103,
					0.0,
					1.0674,
					2_963.3,
					15_063.0,
					13,
					0.192041923076923,
					0.186341,
					0.195937,
					0.143023846153846,
					0.139471,
					0.158068,
					0.680984,
					false,
					true,
					"useful-small-prop-anchor-but-blade-count-mismatch"
			),
			new PresetPerformanceMatch(
					"heavyLift",
					2,
					10.0,
					5.0,
					"ancf 10.0x5.0 - 2",
					"ancf",
					2,
					10.0,
					5.0,
					128,
					0.0,
					0.667,
					1_060.0,
					7_440.0,
					14,
					0.0900960714285714,
					0.071796,
					0.095842,
					0.0308062142857143,
					0.018547,
					0.032542,
					0.679833,
					true,
					false,
					"direct-ten-inch-two-blade-static-performance-anchor-but-no-matching-geometry-row"
			)
	);

	private static final List<GeometryStationMatch> GEOMETRY_MATCHES = List.of(
			new GeometryStationMatch(
					"racingQuad",
					"da4052 5.0x3.75",
					"da4052",
					5.0,
					3.75,
					18,
					0.7,
					0.2068,
					20.912
			),
			new GeometryStationMatch(
					"apDrone",
					"da4052 5.0x3.75",
					"da4052",
					5.0,
					3.75,
					18,
					0.7,
					0.2068,
					20.912
			),
			new GeometryStationMatch(
					"cinewhoop",
					"gwsdd 3.0x3.0",
					"gwsdd",
					3.0,
					3.0,
					18,
					0.7,
					0.2599,
					25.05
			)
	);

	private PropellerArchiveDatasetTriage() {
	}

	public record ArchiveEntrySummary(
			String fileName,
			String dataKind,
			int uncompressedBytes,
			int compressedBytes
	) {
	}

	public record DatasetSummary(
			int archiveEntryCount,
			int experimentVolumeCount,
			int geometryVolumeCount,
			int experimentRowCount,
			int geometryRowCount,
			int uniquePropellerCount,
			int experimentBladeCount,
			int geometryBladeCount,
			int experimentFamilyCount,
			int geometryFamilyCount,
			double experimentDiameterMinInches,
			double experimentDiameterMaxInches,
			double experimentPitchMinInches,
			double experimentPitchMaxInches,
			double experimentAdvanceRatioMin,
			double experimentAdvanceRatioMax,
			double experimentRpmMin,
			double experimentRpmMax,
			double experimentCtMin,
			double experimentCtMax,
			double experimentCpMin,
			double experimentCpMax,
			double experimentEfficiencyMin,
			double experimentEfficiencyMax,
			double geometryChordToRadiusMin,
			double geometryChordToRadiusMax,
			double geometryStationMin,
			double geometryStationMax,
			double geometryBetaMinDegrees,
			double geometryBetaMaxDegrees
	) {
	}

	public record ExperimentVolumeSummary(
			String fileName,
			int rowCount,
			int propellerCount,
			int bladeNameCount,
			int familyCount,
			double diameterMinInches,
			double diameterMaxInches,
			double pitchMinInches,
			double pitchMaxInches,
			double advanceRatioMin,
			double advanceRatioMax,
			double rpmMin,
			double rpmMax,
			double ctMin,
			double ctMax,
			double cpMin,
			double cpMax,
			double efficiencyMin,
			double efficiencyMax
	) {
	}

	public record GeometryVolumeSummary(
			String fileName,
			int rowCount,
			int bladeNameCount,
			int familyCount,
			double diameterMinInches,
			double diameterMaxInches,
			double pitchMinInches,
			double pitchMaxInches,
			double chordToRadiusMin,
			double chordToRadiusMax,
			double stationMin,
			double stationMax,
			double betaMinDegrees,
			double betaMaxDegrees
	) {
	}

	public record BladeCountDistribution(
			int bladeCount,
			int experimentRowCount
	) {
	}

	public record PresetPerformanceMatch(
			String presetName,
			int targetBladeCount,
			double targetDiameterInches,
			double targetPitchInches,
			String matchedPropName,
			String matchedFamily,
			int matchedBladeCount,
			double matchedDiameterInches,
			double matchedPitchInches,
			int sampleRowCount,
			double advanceRatioMin,
			double advanceRatioMax,
			double rpmMin,
			double rpmMax,
			int staticRowCount,
			double staticCtMean,
			double staticCtMin,
			double staticCtMax,
			double staticCpMean,
			double staticCpMin,
			double staticCpMax,
			double maxEfficiency,
			boolean bladeCountMatches,
			boolean geometryAvailable,
			String evidenceRole
	) {
	}

	public record GeometryStationMatch(
			String presetName,
			String matchedBladeName,
			String matchedFamily,
			double matchedDiameterInches,
			double matchedPitchInches,
			int stationCount,
			double stationRadiusRatio,
			double chordToRadius,
			double betaDegrees
	) {
	}

	public record TriageBoundary(
			boolean rawRowsVendored,
			boolean sourceLicenseReviewRequired,
			boolean directA4mcL2Evidence,
			boolean rotorCtCpCurveFitCandidate,
			boolean propGeometryFitCandidate,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction
	) {
	}

	public record PropellerArchiveDatasetTriageAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int aggregateRowCount,
			int volumeSummaryRowCount,
			int bladeCountDistributionRowCount,
			int presetMatchRowCount,
			int geometryMatchRowCount,
			int boundaryRowCount,
			List<ArchiveEntrySummary> archiveEntries,
			DatasetSummary datasetSummary,
			List<ExperimentVolumeSummary> experimentVolumes,
			List<GeometryVolumeSummary> geometryVolumes,
			List<BladeCountDistribution> bladeCountDistribution,
			List<PresetPerformanceMatch> presetMatches,
			List<GeometryStationMatch> geometryMatches,
			TriageBoundary boundary
	) {
		public PropellerArchiveDatasetTriageAudit {
			archiveEntries = List.copyOf(archiveEntries);
			experimentVolumes = List.copyOf(experimentVolumes);
			geometryVolumes = List.copyOf(geometryVolumes);
			bladeCountDistribution = List.copyOf(bladeCountDistribution);
			presetMatches = List.copyOf(presetMatches);
			geometryMatches = List.copyOf(geometryMatches);
		}
	}

	public static PropellerArchiveDatasetTriageAudit audit() {
		return new PropellerArchiveDatasetTriageAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				AGGREGATE_ROW_COUNT,
				VOLUME_SUMMARY_ROW_COUNT,
				BLADE_COUNT_DISTRIBUTION_ROW_COUNT,
				PRESET_MATCH_ROW_COUNT,
				GEOMETRY_MATCH_ROW_COUNT,
				BOUNDARY_ROW_COUNT,
				ARCHIVE_ENTRIES,
				DATASET_SUMMARY,
				EXPERIMENT_VOLUMES,
				GEOMETRY_VOLUMES,
				BLADE_COUNT_DISTRIBUTION,
				PRESET_MATCHES,
				GEOMETRY_MATCHES,
				new TriageBoundary(
						RAW_ROWS_VENDORED,
						SOURCE_LICENSE_REVIEW_REQUIRED,
						DIRECT_A4MC_L2_EVIDENCE,
						ROTOR_CT_CP_CURVE_FIT_CANDIDATE,
						PROP_GEOMETRY_FIT_CANDIDATE,
						GAMEPLAY_AUTO_APPLY_ALLOWED,
						NEXT_REQUIRED_ACTION
				)
		);
	}

	public static PresetPerformanceMatch presetMatch(String presetName) {
		return PRESET_MATCHES.stream()
				.filter(match -> match.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown preset: " + presetName));
	}

	public static GeometryStationMatch geometryMatch(String presetName) {
		return GEOMETRY_MATCHES.stream()
				.filter(match -> match.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("no geometry match for preset: " + presetName));
	}
}
