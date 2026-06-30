package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJArchiveCurveShapeReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Archive-Curve-Shape-Review-Packet";
	public static final String CAVEAT =
			"Archive curve-shape review records non-vendored CT/CP/J shape diagnostics from the user-supplied propeller archive; it proves which sparse curves are fit candidates after source review and never enables runtime physics or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int CURVE_SHAPE_ROW_COUNT = PropellerArchiveCtCpJLookupSeed.PRESET_SEED_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ CURVE_SHAPE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double MAX_ETA_FORMULA_RESIDUAL = 0.001;
	public static final double MAX_CT_INCREASE_TOLERANCE = 0.0001;
	public static final String NEXT_REQUIRED_ACTION =
			"review-source-license-then-fit-positive-thrust-ct-cp-j-surfaces";

	private static final List<ArchiveCurveShapeMetric> CURVE_METRICS = List.of(
			new ArchiveCurveShapeMetric(
					"da4052 5.0x3.75 - 3",
					63,
					14,
					49,
					18,
					0.0,
					0.812815,
					58,
					5,
					0.758639,
					0,
					0.00027500814692071884,
					0,
					0,
					0.0
			),
			new ArchiveCurveShapeMetric(
					"gwsdd 3.0x3.0 - 2",
					103,
					13,
					90,
					18,
					0.0,
					1.067438,
					94,
					9,
					0.956164,
					0,
					0.0004755449264837175,
					0,
					0,
					0.0
			),
			new ArchiveCurveShapeMetric(
					"ancf 10.0x5.0 - 2",
					128,
					14,
					114,
					20,
					0.0,
					0.667043,
					84,
					44,
					0.614319,
					0,
					0.00007780270707657966,
					1,
					1,
					0.000071
			)
	);

	private PropellerArchiveCtCpJArchiveCurveShapeReview() {
	}

	public record ArchiveCurveShapeMetric(
			String performanceMatchId,
			int sampleRowCount,
			int staticSampleRowCount,
			int nonstaticSampleRowCount,
			int rpmTrackCount,
			double minAdvanceRatioJ,
			double maxAdvanceRatioJ,
			int positiveThrustRowCount,
			int negativeThrustTailRowCount,
			double minNegativeThrustAdvanceRatioJ,
			int nonpositivePowerCoefficientRowCount,
			double maxEtaFormulaResidual,
			int ctIncreaseStepCount,
			int ctIncreasingTrackCount,
			double maxCtIncrease
	) {
		public ArchiveCurveShapeMetric {
			if (performanceMatchId == null || performanceMatchId.isBlank()) {
				throw new IllegalArgumentException("performanceMatchId must not be blank.");
			}
			if (sampleRowCount < 0 || staticSampleRowCount < 0 || nonstaticSampleRowCount < 0
					|| rpmTrackCount < 0 || positiveThrustRowCount < 0 || negativeThrustTailRowCount < 0
					|| nonpositivePowerCoefficientRowCount < 0 || ctIncreaseStepCount < 0
					|| ctIncreasingTrackCount < 0) {
				throw new IllegalArgumentException("curve-shape counts must be non-negative.");
			}
			validateFiniteNonnegative("minAdvanceRatioJ", minAdvanceRatioJ);
			validateFiniteNonnegative("maxAdvanceRatioJ", maxAdvanceRatioJ);
			validateFiniteNonnegative("minNegativeThrustAdvanceRatioJ", minNegativeThrustAdvanceRatioJ);
			validateFiniteNonnegative("maxEtaFormulaResidual", maxEtaFormulaResidual);
			validateFiniteNonnegative("maxCtIncrease", maxCtIncrease);
		}
	}

	public record ArchiveCurveShapeRow(
			String presetName,
			String performanceMatchId,
			int sampleRowCount,
			int staticSampleRowCount,
			int nonstaticSampleRowCount,
			int rpmTrackCount,
			double minAdvanceRatioJ,
			double maxAdvanceRatioJ,
			int positiveThrustRowCount,
			int negativeThrustTailRowCount,
			double minNegativeThrustAdvanceRatioJ,
			int nonpositivePowerCoefficientRowCount,
			double maxEtaFormulaResidual,
			int ctIncreaseStepCount,
			int ctIncreasingTrackCount,
			double maxCtIncrease,
			boolean positivePowerPreserved,
			boolean etaFormulaConsistent,
			boolean ctMonotonicWithTolerance,
			boolean positiveThrustEnvelopeCoversAllowedQueries,
			boolean shapeGuardPassed,
			boolean postReviewCtCpJLookupAllowed,
			boolean postReviewFullSimulationLookupAllowed,
			boolean currentCurveFitAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record ArchiveCurveShapeSummary(
			int presetRowCount,
			int uniquePerformanceCurveCount,
			int postReviewCtCpJCandidateCount,
			int postReviewFullSimulationCandidateCount,
			int performanceOnlyCandidateCount,
			int shapeGuardPassedCount,
			int coverageBlockedCount,
			int positivePowerPreservedCount,
			int etaFormulaConsistentCount,
			int ctMonotonicWithToleranceCount,
			int negativeThrustTailPresetCount,
			int uniqueNegativeThrustTailRowCount,
			double maxEtaFormulaResidual,
			double maxCtIncrease,
			int currentCurveFitAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJArchiveCurveShapeReviewAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int curveShapeRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ArchiveCurveShapeRow> rows,
			ArchiveCurveShapeSummary summary
	) {
		public CtCpJArchiveCurveShapeReviewAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJArchiveCurveShapeReviewAudit audit() {
		List<ArchiveCurveShapeRow> rows = PropellerArchiveCtCpJLookupSeed.audit()
				.presets()
				.stream()
				.map(PropellerArchiveCtCpJArchiveCurveShapeReview::row)
				.toList();
		return new CtCpJArchiveCurveShapeReviewAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				CURVE_SHAPE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(rows)
		);
	}

	public static ArchiveCurveShapeRow row(String presetName) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown archive CT/CP/J curve-shape preset: " + presetName));
	}

	private static ArchiveCurveShapeRow row(PropellerArchiveCtCpJLookupSeed.PresetLookupSeed seed) {
		ArchiveCurveShapeMetric metric = metric(seed.performanceMatchId());
		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology topology =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.topology(seed.presetName());
		boolean positivePowerPreserved = metric.nonpositivePowerCoefficientRowCount() == 0;
		boolean etaFormulaConsistent = metric.maxEtaFormulaResidual() <= MAX_ETA_FORMULA_RESIDUAL;
		boolean ctMonotonicWithTolerance = metric.maxCtIncrease() <= MAX_CT_INCREASE_TOLERANCE;
		boolean positiveEnvelope = topology.postReviewCtCpJLookupSeedAllowed()
				&& positiveThrustEnvelopeCoversAllowedQueries(seed.presetName(), metric);
		boolean shapeGuardPassed = positivePowerPreserved
				&& etaFormulaConsistent
				&& ctMonotonicWithTolerance
				&& positiveEnvelope;
		boolean currentCurveFitAllowed = false;
		return new ArchiveCurveShapeRow(
				seed.presetName(),
				metric.performanceMatchId(),
				metric.sampleRowCount(),
				metric.staticSampleRowCount(),
				metric.nonstaticSampleRowCount(),
				metric.rpmTrackCount(),
				metric.minAdvanceRatioJ(),
				metric.maxAdvanceRatioJ(),
				metric.positiveThrustRowCount(),
				metric.negativeThrustTailRowCount(),
				metric.minNegativeThrustAdvanceRatioJ(),
				metric.nonpositivePowerCoefficientRowCount(),
				metric.maxEtaFormulaResidual(),
				metric.ctIncreaseStepCount(),
				metric.ctIncreasingTrackCount(),
				metric.maxCtIncrease(),
				positivePowerPreserved,
				etaFormulaConsistent,
				ctMonotonicWithTolerance,
				positiveEnvelope,
				shapeGuardPassed,
				topology.postReviewCtCpJLookupSeedAllowed(),
				topology.postReviewFullSimulationLookupSeedAllowed(),
				currentCurveFitAllowed,
				false,
				false,
				statusFor(topology, shapeGuardPassed),
				nextRequiredActionFor(topology, shapeGuardPassed),
				messageFor(topology, shapeGuardPassed)
		);
	}

	private static ArchiveCurveShapeSummary summary(List<ArchiveCurveShapeRow> rows) {
		int postReview = 0;
		int fullSimulation = 0;
		int performanceOnly = 0;
		int passed = 0;
		int coverageBlocked = 0;
		int positivePower = 0;
		int eta = 0;
		int ct = 0;
		int negativeTailPreset = 0;
		int currentAllowed = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxEta = 0.0;
		double maxCtIncrease = 0.0;
		for (ArchiveCurveShapeRow row : rows) {
			if (row.postReviewCtCpJLookupAllowed()) {
				postReview++;
			}
			if (row.postReviewFullSimulationLookupAllowed()) {
				fullSimulation++;
			}
			if (row.postReviewCtCpJLookupAllowed() && !row.postReviewFullSimulationLookupAllowed()) {
				performanceOnly++;
			}
			if (row.shapeGuardPassed()) {
				passed++;
			}
			if ("COVERAGE_BLOCKED".equals(row.status())) {
				coverageBlocked++;
			}
			if (row.positivePowerPreserved()) {
				positivePower++;
			}
			if (row.etaFormulaConsistent()) {
				eta++;
			}
			if (row.ctMonotonicWithTolerance()) {
				ct++;
			}
			if (row.negativeThrustTailRowCount() > 0) {
				negativeTailPreset++;
			}
			if (row.currentCurveFitAllowed()) {
				currentAllowed++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxEta = Math.max(maxEta, row.maxEtaFormulaResidual());
			maxCtIncrease = Math.max(maxCtIncrease, row.maxCtIncrease());
		}
		return new ArchiveCurveShapeSummary(
				rows.size(),
				CURVE_METRICS.size(),
				postReview,
				fullSimulation,
				performanceOnly,
				passed,
				coverageBlocked,
				positivePower,
				eta,
				ct,
				negativeTailPreset,
				uniqueNegativeThrustTailRowCount(),
				maxEta,
				maxCtIncrease,
				currentAllowed,
				runtime,
				gameplay
		);
	}

	private static ArchiveCurveShapeMetric metric(String performanceMatchId) {
		return CURVE_METRICS.stream()
				.filter(metric -> metric.performanceMatchId().equals(performanceMatchId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown archive CT/CP/J curve metric: " + performanceMatchId));
	}

	private static boolean positiveThrustEnvelopeCoversAllowedQueries(
			String presetName,
			ArchiveCurveShapeMetric metric
	) {
		double maxAllowedQueryJ = PropellerArchiveCtCpJArchiveLookupGridCoverage.audit()
				.rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName))
				.filter(row -> row.insideLookupDomain() && row.postReviewCtCpJLookupAllowed())
				.mapToDouble(PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow
						::queryAdvanceRatioJ)
				.max()
				.orElse(Double.NaN);
		if (!Double.isFinite(maxAllowedQueryJ)) {
			return false;
		}
		return metric.negativeThrustTailRowCount() == 0
				|| metric.minNegativeThrustAdvanceRatioJ() > maxAllowedQueryJ;
	}

	private static int uniqueNegativeThrustTailRowCount() {
		int count = 0;
		for (ArchiveCurveShapeMetric metric : CURVE_METRICS) {
			count += metric.negativeThrustTailRowCount();
		}
		return count;
	}

	private static String statusFor(
			PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology topology,
			boolean shapeGuardPassed
	) {
		if (!topology.postReviewCtCpJLookupSeedAllowed()) {
			return "COVERAGE_BLOCKED";
		}
		if (!shapeGuardPassed) {
			return "SHAPE_GUARD_BLOCKED";
		}
		if (!topology.postReviewFullSimulationLookupSeedAllowed()) {
			return "PERFORMANCE_ONLY_SOURCE_REVIEW_REQUIRED";
		}
		return "SOURCE_REVIEW_REQUIRED";
	}

	private static String nextRequiredActionFor(
			PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology topology,
			boolean shapeGuardPassed
	) {
		if (!topology.postReviewCtCpJLookupSeedAllowed()) {
			return "resolve-cinewhoop-three-blade-coverage-or-correction";
		}
		if (!shapeGuardPassed) {
			return "repair-ct-cp-j-shape-before-scattered-surface-fit";
		}
		if (!topology.postReviewFullSimulationLookupSeedAllowed()) {
			return "review-source-license-fit-performance-surface-and-resolve-geometry";
		}
		return NEXT_REQUIRED_ACTION;
	}

	private static String messageFor(
			PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology topology,
			boolean shapeGuardPassed
	) {
		if (!topology.postReviewCtCpJLookupSeedAllowed()) {
			return "preset-coverage-blocks-curve-shape-use";
		}
		if (!shapeGuardPassed) {
			return "ct-cp-j-shape-guard-blocks-scattered-fit";
		}
		if (!topology.postReviewFullSimulationLookupSeedAllowed()) {
			return "curve-shape-passes-for-performance-fit-but-full-simulation-geometry-is-blocked";
		}
		return "curve-shape-passes-after-source-review-before-scattered-fit";
	}

	private static void validateFiniteNonnegative(String fieldName, double value) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(fieldName + " must be finite and nonnegative.");
		}
	}
}
