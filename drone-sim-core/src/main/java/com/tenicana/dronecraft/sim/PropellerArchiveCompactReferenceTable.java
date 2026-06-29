package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCompactReferenceTable {
	public static final String SOURCE_ID = "User-Propeller-Archive-Compact-Reference-Table-Packet";
	public static final String CAVEAT =
			"Compact propeller reference rows remain export-disabled in the current state; rows expose reviewed table shape and candidate domains only, keep weights at zero until handoff opens, and never enable runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int REFERENCE_SAMPLE_COUNT = PropellerArchiveCurveFitPlan.PRESET_PLAN_ROW_COUNT;
	public static final int REFERENCE_METRIC_ROW_COUNT = 30;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REFERENCE_SAMPLE_COUNT * REFERENCE_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveCompactReferenceTable() {
	}

	public record CompactReferenceRow(
			String presetName,
			int targetBladeCount,
			double targetDiameterInches,
			double targetPitchInches,
			String performanceMatchId,
			String geometryMatchId,
			int matchedBladeCount,
			double advanceRatioMin,
			double advanceRatioMax,
			double rpmMin,
			double rpmMax,
			double staticCtAnchorCandidate,
			double staticCpAnchorCandidate,
			double maxEfficiencyCandidate,
			boolean geometryStationAvailable,
			double stationRadiusRatio,
			double chordToRadius,
			double betaDegrees,
			boolean curveFitAcceptanceReady,
			boolean referenceMaterialExportAllowed,
			boolean referenceRowAvailable,
			double ctCpEtaReferenceWeight,
			double staticAnchorReferenceWeight,
			double geometryReferenceWeight,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String referencePayloadKind,
			String sourceRuntimeInfo
	) {
	}

	public record CompactReferenceTableExtrema(
			int rowCount,
			int referenceRowAvailableCount,
			int blockedRowCount,
			int geometryStationAvailableCount,
			int referenceMaterialExportAllowedRowCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double maxAdvanceRatio,
			double maxRpm,
			double maxStaticCtAnchorCandidate,
			double maxCtCpEtaReferenceWeight,
			double maxGeometryReferenceWeight
	) {
	}

	public record CompactReferenceTableAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int referenceSampleCount,
			int referenceMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CompactReferenceRow> rows,
			CompactReferenceTableExtrema extrema
	) {
		public CompactReferenceTableAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CompactReferenceTableAudit audit() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary current =
				PropellerArchiveCompactReferenceHandoff.audit().scenarios().stream()
						.filter(scenario -> "current_acceptance_blocked".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		return audit(current);
	}

	public static CompactReferenceTableAudit audit(
			PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff
	) {
		if (handoff == null) {
			throw new IllegalArgumentException("handoff summary must not be null.");
		}
		List<CompactReferenceRow> rows = PropellerArchiveDatasetTriage.audit()
				.presetMatches()
				.stream()
				.map(match -> row(handoff, match))
				.toList();
		return new CompactReferenceTableAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				REFERENCE_SAMPLE_COUNT,
				REFERENCE_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static CompactReferenceRow row(String presetName) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown compact reference row: " + presetName));
	}

	public static CompactReferenceRow row(
			PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff,
			PropellerArchiveDatasetTriage.PresetPerformanceMatch match
	) {
		if (handoff == null || match == null) {
			throw new IllegalArgumentException("handoff and match are required.");
		}
		PropellerArchiveDatasetTriage.GeometryStationMatch geometry = geometryOrNull(match.presetName());
		boolean geometryAvailable = match.geometryAvailable() && geometry != null;
		boolean exportAllowed = handoff.referenceMaterialExportAllowed();
		boolean rowAvailable = exportAllowed && geometryAvailable;
		double performanceWeight = exportAllowed ? 1.0 : 0.0;
		double geometryWeight = rowAvailable ? 1.0 : 0.0;
		return new CompactReferenceRow(
				match.presetName(),
				match.targetBladeCount(),
				match.targetDiameterInches(),
				match.targetPitchInches(),
				match.matchedPropName(),
				geometryAvailable ? geometry.matchedBladeName() : "missing-reviewed-geometry-match",
				match.matchedBladeCount(),
				match.advanceRatioMin(),
				match.advanceRatioMax(),
				match.rpmMin(),
				match.rpmMax(),
				match.staticCtMean(),
				match.staticCpMean(),
				match.maxEfficiency(),
				geometryAvailable,
				geometryAvailable ? geometry.stationRadiusRatio() : 0.0,
				geometryAvailable ? geometry.chordToRadius() : 0.0,
				geometryAvailable ? geometry.betaDegrees() : 0.0,
				handoff.curveFitAcceptanceReady(),
				exportAllowed,
				rowAvailable,
				performanceWeight,
				performanceWeight,
				geometryWeight,
				false,
				false,
				rowAvailable ? "AVAILABLE" : "BLOCKED",
				messageFor(handoff, geometryAvailable),
				handoff.referencePayloadKind(),
				handoff.sourceRuntimeInfo()
		);
	}

	private static PropellerArchiveDatasetTriage.GeometryStationMatch geometryOrNull(String presetName) {
		try {
			return PropellerArchiveDatasetTriage.geometryMatch(presetName);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static CompactReferenceTableExtrema extrema(List<CompactReferenceRow> rows) {
		int available = 0;
		int geometryAvailable = 0;
		int exportAllowed = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxAdvance = 0.0;
		double maxRpm = 0.0;
		double maxStaticCt = 0.0;
		double maxPerformanceWeight = 0.0;
		double maxGeometryWeight = 0.0;
		for (CompactReferenceRow row : rows) {
			if (row.referenceRowAvailable()) {
				available++;
			}
			if (row.geometryStationAvailable()) {
				geometryAvailable++;
			}
			if (row.referenceMaterialExportAllowed()) {
				exportAllowed++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxAdvance = Math.max(maxAdvance, row.advanceRatioMax());
			maxRpm = Math.max(maxRpm, row.rpmMax());
			maxStaticCt = Math.max(maxStaticCt, row.staticCtAnchorCandidate());
			maxPerformanceWeight = Math.max(maxPerformanceWeight, row.ctCpEtaReferenceWeight());
			maxGeometryWeight = Math.max(maxGeometryWeight, row.geometryReferenceWeight());
		}
		return new CompactReferenceTableExtrema(
				rows.size(),
				available,
				rows.size() - available,
				geometryAvailable,
				exportAllowed,
				runtime,
				gameplay,
				maxAdvance,
				maxRpm,
				maxStaticCt,
				maxPerformanceWeight,
				maxGeometryWeight
		);
	}

	private static String messageFor(
			PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff,
			boolean geometryAvailable
	) {
		if (!handoff.referenceMaterialExportAllowed()) {
			return "reference-handoff-blocked";
		}
		if (!geometryAvailable) {
			return "reference-row-geometry-source-missing";
		}
		return "compact-propeller-reference-row-available";
	}
}
