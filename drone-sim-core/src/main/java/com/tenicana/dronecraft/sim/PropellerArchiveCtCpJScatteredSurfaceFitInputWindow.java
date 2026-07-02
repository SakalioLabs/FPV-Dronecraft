package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJScatteredSurfaceFitInputWindow {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Scattered-Surface-Fit-Input-Window-Packet";
	public static final String CAVEAT =
			"Scattered CT/CP/J fit input windows bind each lookup target to compact archive topology and curve-shape evidence before any reviewed fit rows can reach lookup execution; raw archive rows stay unvendored and runtime/gameplay coupling remain closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int WINDOW_ROW_COUNT = 9;
	public static final int SUMMARY_ROW_COUNT = 15;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ WINDOW_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveCtCpJScatteredSurfaceFitInputWindow() {
	}

	public record ScatteredSurfaceFitInputWindowRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String fitInputWindowKind,
			double queryAdvanceRatioJ,
			double queryRpm,
			int archiveSampleRowCount,
			int archiveStaticSampleRowCount,
			int archiveNonstaticSampleRowCount,
			int archiveDistinctRpmBinCount,
			int minimumPerformanceNeighborRows,
			int availableRectangularNeighborRows,
			int availableNonstaticNeighborRows,
			boolean directStaticAnchorBinding,
			boolean scatteredSurfaceFitRequired,
			boolean archiveCurveShapeGuardPassed,
			int negativeThrustTailRowCount,
			double archiveMaxEtaFormulaResidual,
			double archiveMaxCtIncrease,
			boolean postReviewFullSimulationLookupAllowed,
			boolean sourceRowsReviewed,
			boolean fitInputWindowReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record ScatteredSurfaceFitInputWindowSummary(
			int windowRowCount,
			int directStaticAnchorWindowCount,
			int scatteredSurfaceFitRequiredWindowCount,
			int postReviewFullSimulationWindowCount,
			int postReviewPerformanceOnlyWindowCount,
			int archiveCurveShapeGuardReadyWindowCount,
			int negativeThrustTailWindowCount,
			int sourceRowsReviewedWindowCount,
			int fitInputWindowReadyCount,
			int maxArchiveSampleRowCount,
			int maxArchiveStaticSampleRowCount,
			int maxArchiveNonstaticSampleRowCount,
			int maxArchiveDistinctRpmBinCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJScatteredSurfaceFitInputWindowAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int windowRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ScatteredSurfaceFitInputWindowRow> rows,
			ScatteredSurfaceFitInputWindowSummary summary
	) {
		public CtCpJScatteredSurfaceFitInputWindowAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJScatteredSurfaceFitInputWindowAudit audit() {
		List<ScatteredSurfaceFitInputWindowRow> rows =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.audit()
						.rows()
						.stream()
						.filter(row -> row.insideLookupDomain() && row.postReviewCtCpJLookupAllowed())
						.map(PropellerArchiveCtCpJScatteredSurfaceFitInputWindow::window)
						.toList();
		return new CtCpJScatteredSurfaceFitInputWindowAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				WINDOW_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(rows)
		);
	}

	public static ScatteredSurfaceFitInputWindowRow window(String presetName, String caseName) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown scattered CT/CP/J fit input window: " + presetName + " / " + caseName));
	}

	private static ScatteredSurfaceFitInputWindowRow window(
			PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow coverage
	) {
		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology topology =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.topology(coverage.presetName());
		PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow shape =
				PropellerArchiveCtCpJArchiveCurveShapeReview.row(coverage.presetName());
		boolean direct = coverage.reviewedNeighborBindingReady();
		boolean sourceRowsReviewed = false;
		boolean ready = sourceRowsReviewed
				&& coverage.postReviewCtCpJLookupAllowed()
				&& shape.shapeGuardPassed();
		return new ScatteredSurfaceFitInputWindowRow(
				coverage.presetName(),
				coverage.caseName(),
				coverage.performanceMatchId(),
				direct ? "DIRECT_STATIC_ANCHOR" : "SCATTERED_SURFACE_FIT",
				coverage.queryAdvanceRatioJ(),
				coverage.queryRpm(),
				topology.performanceSampleRowCount(),
				topology.staticSampleRowCount(),
				topology.nonstaticSampleRowCount(),
				topology.distinctRpmBinCount(),
				coverage.minimumPerformanceNeighborRows(),
				coverage.availableRectangularNeighborRows(),
				coverage.availableNonstaticNeighborRows(),
				direct,
				coverage.scatteredFitRequired(),
				shape.shapeGuardPassed(),
				shape.negativeThrustTailRowCount(),
				shape.maxEtaFormulaResidual(),
				shape.maxCtIncrease(),
				coverage.postReviewFullSimulationLookupAllowed(),
				sourceRowsReviewed,
				ready,
				false,
				false,
				nextRequiredActionFor(coverage, shape, sourceRowsReviewed),
				ready ? "READY" : "BLOCKED",
				messageFor(coverage, shape, sourceRowsReviewed)
		);
	}

	private static ScatteredSurfaceFitInputWindowSummary summary(
			List<ScatteredSurfaceFitInputWindowRow> rows
	) {
		int direct = 0;
		int scattered = 0;
		int full = 0;
		int performanceOnly = 0;
		int shapeReady = 0;
		int negativeTail = 0;
		int reviewed = 0;
		int ready = 0;
		int maxSamples = 0;
		int maxStatic = 0;
		int maxNonstatic = 0;
		int maxRpm = 0;
		int runtime = 0;
		int gameplay = 0;
		for (ScatteredSurfaceFitInputWindowRow row : rows) {
			if (row.directStaticAnchorBinding()) {
				direct++;
			}
			if (row.scatteredSurfaceFitRequired()) {
				scattered++;
			}
			if (row.postReviewFullSimulationLookupAllowed()) {
				full++;
			} else {
				performanceOnly++;
			}
			if (row.archiveCurveShapeGuardPassed()) {
				shapeReady++;
			}
			if (row.negativeThrustTailRowCount() > 0) {
				negativeTail++;
			}
			if (row.sourceRowsReviewed()) {
				reviewed++;
			}
			if (row.fitInputWindowReady()) {
				ready++;
			}
			maxSamples = Math.max(maxSamples, row.archiveSampleRowCount());
			maxStatic = Math.max(maxStatic, row.archiveStaticSampleRowCount());
			maxNonstatic = Math.max(maxNonstatic, row.archiveNonstaticSampleRowCount());
			maxRpm = Math.max(maxRpm, row.archiveDistinctRpmBinCount());
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new ScatteredSurfaceFitInputWindowSummary(
				rows.size(),
				direct,
				scattered,
				full,
				performanceOnly,
				shapeReady,
				negativeTail,
				reviewed,
				ready,
				maxSamples,
				maxStatic,
				maxNonstatic,
				maxRpm,
				runtime,
				gameplay
		);
	}

	private static String nextRequiredActionFor(
			PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow coverage,
			PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow shape,
			boolean sourceRowsReviewed
	) {
		if (!sourceRowsReviewed) {
			return "review-source-license-before-fit-input-window";
		}
		if (!shape.shapeGuardPassed()) {
			return shape.nextRequiredAction();
		}
		return coverage.reviewedNeighborBindingReady()
				? "bind-reviewed-static-ct-cp-j-anchor"
				: "fit-reviewed-scattered-ct-cp-j-surface";
	}

	private static String messageFor(
			PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow coverage,
			PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow shape,
			boolean sourceRowsReviewed
	) {
		if (!sourceRowsReviewed) {
			return coverage.reviewedNeighborBindingReady()
					? "source-review-blocks-static-anchor-input-window"
					: "source-review-blocks-scattered-fit-input-window";
		}
		if (!shape.shapeGuardPassed()) {
			return "archive-curve-shape-guard-blocks-fit-input-window";
		}
		return coverage.reviewedNeighborBindingReady()
				? "static-anchor-input-window-ready"
				: "scattered-surface-fit-input-window-ready";
	}
}
