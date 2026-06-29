package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Forward-Advance-Scale-Gap-Diagnostic-Packet";
	public static final String CAVEAT =
			"Forward-advance scale gap diagnostic compares APDrone settled peak equivalent J values against compact DA4052 archive CT/static interpolation; rows are audit-only and do not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final double FORMULA_ARCHIVE_GAP_TOLERANCE = 0.06;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SPEED_SAMPLE_COUNT = 5;
	public static final int GAP_ROW_COUNT = SPEED_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 21;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ GAP_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<ArchiveCtRatioAtSpeed> ARCHIVE_CT_RATIOS = List.of(
			new ArchiveCtRatioAtSpeed(0.0, 0.244610545941252),
			new ArchiveCtRatioAtSpeed(8.0, 0.453938599390903),
			new ArchiveCtRatioAtSpeed(16.0, 0.646048169543159),
			new ArchiveCtRatioAtSpeed(22.0, 0.612195779760975),
			new ArchiveCtRatioAtSpeed(28.0, 0.484872044400078)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic() {
	}

	public record ForwardAdvanceScaleGapRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double forwardSpeedMetersPerSecond,
			int peakSampleIndex,
			double peakTimeSeconds,
			double thrustLossOverContractRatio,
			double equivalentAdvanceRatioJ,
			double archiveAdvanceRatioMax,
			double archiveCoverageRatio,
			double currentFormulaThrustScale,
			double archiveInterpolatedCtRatio,
			double formulaMinusArchiveCtRatio,
			double absoluteFormulaArchiveScaleGap,
			boolean formulaArchiveGapWithinTolerance,
			double neutralAveragePropellerThrustScale,
			double deratedAveragePropellerThrustScale,
			double neutralToDeratedPropellerScaleDrop,
			double propellerScaleDropOverFormulaArchiveGap,
			double targetPitchInches,
			double matchedPitchInches,
			double matchedOverTargetPitchRatio,
			int targetBladeCount,
			int matchedBladeCount,
			boolean bladeCountMatches,
			boolean sourceLicenseReviewRequired,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String dominantDeficitBucket,
			String status,
			String message
	) {
	}

	public record ForwardAdvanceScaleGapSummary(
			int rowCount,
			int failedRowCount,
			int archiveCoveredRowCount,
			int formulaWithinArchiveToleranceRowCount,
			double matchedOverTargetPitchRatio,
			double maxEquivalentAdvanceRatioJ,
			double maxArchiveCoverageRatio,
			double minArchiveInterpolatedCtRatio,
			double minCurrentFormulaThrustScale,
			double maxFormulaMinusArchiveCtRatio,
			double maxAbsoluteFormulaArchiveScaleGap,
			double maxNeutralToDeratedPropellerScaleDrop,
			double maxPropellerScaleDropOverFormulaArchiveGap,
			double blackboxSpeedEquivalentAdvanceRatioJ,
			double blackboxSpeedFormulaMinusArchiveCtRatio,
			double blackboxSpeedAbsoluteFormulaArchiveScaleGap,
			double blackboxSpeedNeutralToDeratedPropellerScaleDrop,
			double fastSpeedNeutralToDeratedPropellerScaleDrop,
			String dominantInterpretation,
			String nextRequiredAction,
			boolean forwardAdvanceScaleGapDiagnosticPassed
	) {
	}

	public record ForwardAdvanceScaleGapAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int speedSampleCount,
			int gapRowCount,
			int summaryRowCount,
			int methodRowCount,
			double formulaArchiveGapTolerance,
			List<ForwardAdvanceScaleGapRow> rows,
			ForwardAdvanceScaleGapSummary summary
	) {
		public ForwardAdvanceScaleGapAudit {
			rows = List.copyOf(rows);
		}
	}

	public static ForwardAdvanceScaleGapAudit audit() {
		PropellerArchiveDatasetTriage.PresetPerformanceMatch match =
				PropellerArchiveDatasetTriage.presetMatch("apDrone");
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		List<ForwardAdvanceScaleGapRow> rows = new ArrayList<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow settledRow
				: PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
						.audit()
						.rows()) {
			rows.add(row(rotor, match, settledRow));
		}
		return new ForwardAdvanceScaleGapAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SPEED_SAMPLE_COUNT,
				GAP_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				FORMULA_ARCHIVE_GAP_TOLERANCE,
				rows,
				summary(rows)
		);
	}

	public static ForwardAdvanceScaleGapRow row(double forwardSpeedMetersPerSecond) {
		return audit().rows().stream()
				.filter(row -> Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown APDrone forward-advance scale gap row: " + forwardSpeedMetersPerSecond));
	}

	private static ForwardAdvanceScaleGapRow row(
			RotorSpec rotor,
			PropellerArchiveDatasetTriage.PresetPerformanceMatch match,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
					.SettledThrustModelBreakdownRow settledRow
	) {
		double equivalentJ = DronePhysics.rotorUiucEquivalentPropellerAdvanceRatio(
				rotor,
				settledRow.peakRotorAdvanceRatio());
		double currentScale = DronePhysics.rotorForwardAdvanceThrustScale(
				rotor,
				settledRow.peakRotorAdvanceRatio());
		double archiveCtRatio = archiveInterpolatedCtRatio(settledRow.forwardSpeedMetersPerSecond());
		double formulaMinusArchive = currentScale - archiveCtRatio;
		double absoluteGap = Math.abs(formulaMinusArchive);
		double propellerDrop = settledRow.neutralAveragePropellerThrustScale()
				- settledRow.deratedAveragePropellerThrustScale();
		boolean gapWithinTolerance = absoluteGap <= FORMULA_ARCHIVE_GAP_TOLERANCE;
		return new ForwardAdvanceScaleGapRow(
				settledRow.scenarioName(),
				settledRow.presetName(),
				settledRow.ambientCaseName(),
				settledRow.forwardSpeedMetersPerSecond(),
				settledRow.peakSampleIndex(),
				settledRow.peakTimeSeconds(),
				settledRow.thrustLossOverContractRatio(),
				equivalentJ,
				match.advanceRatioMax(),
				safeRatio(equivalentJ, match.advanceRatioMax()),
				currentScale,
				archiveCtRatio,
				formulaMinusArchive,
				absoluteGap,
				gapWithinTolerance,
				settledRow.neutralAveragePropellerThrustScale(),
				settledRow.deratedAveragePropellerThrustScale(),
				propellerDrop,
				safeRatio(propellerDrop, absoluteGap),
				match.targetPitchInches(),
				match.matchedPitchInches(),
				safeRatio(match.matchedPitchInches(), match.targetPitchInches()),
				match.targetBladeCount(),
				match.matchedBladeCount(),
				match.bladeCountMatches(),
				PropellerArchiveDatasetTriage.SOURCE_LICENSE_REVIEW_REQUIRED,
				false,
				false,
				false,
				settledRow.dominantDeficitBucket(),
				"BLOCKED",
				messageFor(gapWithinTolerance, propellerDrop, absoluteGap)
		);
	}

	private static ForwardAdvanceScaleGapSummary summary(List<ForwardAdvanceScaleGapRow> rows) {
		int failed = 0;
		int covered = 0;
		int withinTolerance = 0;
		double pitchRatio = 0.0;
		double maxJ = 0.0;
		double maxCoverage = 0.0;
		double minArchive = Double.POSITIVE_INFINITY;
		double minScale = Double.POSITIVE_INFINITY;
		double maxSignedGap = 0.0;
		double maxAbsoluteGap = 0.0;
		double maxPropellerDrop = 0.0;
		double maxDropOverGap = 0.0;
		double blackboxJ = 0.0;
		double blackboxSignedGap = 0.0;
		double blackboxAbsoluteGap = 0.0;
		double blackboxDrop = 0.0;
		double fastDrop = 0.0;
		for (ForwardAdvanceScaleGapRow row : rows) {
			if (row.thrustLossOverContractRatio() > 0.0) {
				failed++;
			}
			if (row.equivalentAdvanceRatioJ() <= row.archiveAdvanceRatioMax()) {
				covered++;
			}
			if (row.formulaArchiveGapWithinTolerance()) {
				withinTolerance++;
			}
			pitchRatio = row.matchedOverTargetPitchRatio();
			maxJ = Math.max(maxJ, row.equivalentAdvanceRatioJ());
			maxCoverage = Math.max(maxCoverage, row.archiveCoverageRatio());
			minArchive = Math.min(minArchive, row.archiveInterpolatedCtRatio());
			minScale = Math.min(minScale, row.currentFormulaThrustScale());
			if (Math.abs(row.formulaMinusArchiveCtRatio()) > Math.abs(maxSignedGap)) {
				maxSignedGap = row.formulaMinusArchiveCtRatio();
			}
			maxAbsoluteGap = Math.max(maxAbsoluteGap, row.absoluteFormulaArchiveScaleGap());
			maxPropellerDrop = Math.max(maxPropellerDrop, row.neutralToDeratedPropellerScaleDrop());
			maxDropOverGap = Math.max(maxDropOverGap, row.propellerScaleDropOverFormulaArchiveGap());
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 22.0) == 0) {
				blackboxJ = row.equivalentAdvanceRatioJ();
				blackboxSignedGap = row.formulaMinusArchiveCtRatio();
				blackboxAbsoluteGap = row.absoluteFormulaArchiveScaleGap();
				blackboxDrop = row.neutralToDeratedPropellerScaleDrop();
			}
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 28.0) == 0) {
				fastDrop = row.neutralToDeratedPropellerScaleDrop();
			}
		}
		boolean passed = failed == 0;
		return new ForwardAdvanceScaleGapSummary(
				rows.size(),
				failed,
				covered,
				withinTolerance,
				pitchRatio,
				maxJ,
				maxCoverage,
				Double.isInfinite(minArchive) ? 0.0 : minArchive,
				Double.isInfinite(minScale) ? 0.0 : minScale,
				maxSignedGap,
				maxAbsoluteGap,
				maxPropellerDrop,
				maxDropOverGap,
				blackboxJ,
				blackboxSignedGap,
				blackboxAbsoluteGap,
				blackboxDrop,
				fastDrop,
				withinTolerance == rows.size()
						? "advance-scale-formula-is-inside-compact-archive-ct-ratio-tolerance"
						: "advance-scale-formula-needs-archive-curve-refit",
				passed
						? "feed-forward-advance-scale-gap-to-blackbox-acceptance"
						: "investigate-rotor-state-dispersion-and-residual-thrust-terms-before-changing-advance-scale",
				passed
		);
	}

	private static String messageFor(
			boolean gapWithinTolerance,
			double propellerDrop,
			double absoluteFormulaArchiveGap
	) {
		if (!gapWithinTolerance) {
			return "advance-scale-formula-outside-compact-archive-ct-ratio-tolerance";
		}
		if (propellerDrop > absoluteFormulaArchiveGap * 1.50) {
			return "propeller-scale-drop-exceeds-formula-archive-gap";
		}
		return "advance-scale-formula-gap-is-not-dominant-blocker";
	}

	private static double archiveInterpolatedCtRatio(double forwardSpeedMetersPerSecond) {
		return ARCHIVE_CT_RATIOS.stream()
				.filter(row -> Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"no DA4052 archive CT/static interpolation for speed: " + forwardSpeedMetersPerSecond))
				.archiveInterpolatedCtRatio();
	}

	private static double safeRatio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private record ArchiveCtRatioAtSpeed(
			double forwardSpeedMetersPerSecond,
			double archiveInterpolatedCtRatio
	) {
	}
}
