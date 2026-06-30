package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJArchiveLookupGridCoverage {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Archive-Lookup-Grid-Coverage-Packet";
	public static final String CAVEAT =
			"Archive lookup grid coverage records non-vendored aggregate topology from the user-supplied CSV archive; it proves direct rectangular CT/CP/J lookup binding is not enough for nonstatic rows and never enables runtime physics or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int PRESET_TOPOLOGY_ROW_COUNT = PropellerArchiveCtCpJLookupSeed.PRESET_SEED_ROW_COUNT;
	public static final int QUERY_COVERAGE_ROW_COUNT =
			PropellerArchiveCtCpJLookupQueryEnvelope.PRESET_QUERY_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ PRESET_TOPOLOGY_ROW_COUNT
			+ QUERY_COVERAGE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"fit-scattered-ct-cp-j-surface-before-direct-lookup-binding";

	private static final List<ArchiveLookupGridTopology> TOPOLOGIES = List.of(
			new ArchiveLookupGridTopology("racingQuad", "da4052 5.0x3.75 - 3",
					63, 14, 49, 18, 4, 14,
					false, true, true, false, false,
					"SCATTERED_FIT_REQUIRED", "nonstatic samples are sparse rpm tracks not a rectangular lookup grid"),
			new ArchiveLookupGridTopology("apDrone", "da4052 5.0x3.75 - 3",
					63, 14, 49, 18, 4, 14,
					false, true, true, false, false,
					"SCATTERED_FIT_REQUIRED", "shares the da4052 sparse rpm-track topology"),
			new ArchiveLookupGridTopology("cinewhoop", "gwsdd 3.0x3.0 - 2",
					103, 13, 90, 18, 5, 13,
					false, false, false, false, false,
					"COVERAGE_BLOCKED", "blade-count mismatch still blocks reviewed CT/CP/J lookup binding"),
			new ArchiveLookupGridTopology("heavyLift", "ancf 10.0x5.0 - 2",
					128, 14, 114, 20, 6, 14,
					false, true, false, false, false,
					"PERFORMANCE_ONLY_SCATTERED_FIT_REQUIRED",
					"performance rows exist but geometry coverage remains blocked")
	);

	private static final List<ArchiveLookupGridCoverageRow> COVERAGE_ROWS = List.of(
			row("racingQuad", "static_anchor_low_rpm", 1, 2, 0, true,
					"ready-for-reviewed-neighbor-binding"),
			row("racingQuad", "mid_domain_mid_rpm", 4, 2, 0, false,
					"rectangular-neighbor-count-missing"),
			row("racingQuad", "high_domain_max_rpm", 2, 1, 0, false,
					"rectangular-neighbor-count-missing"),
			row("racingQuad", "high_j_extrapolation_probe", 0, 0, 0, false,
					"query-outside-j-rpm-domain"),
			row("apDrone", "static_anchor_low_rpm", 1, 2, 0, true,
					"ready-for-reviewed-neighbor-binding"),
			row("apDrone", "mid_domain_mid_rpm", 4, 2, 0, false,
					"rectangular-neighbor-count-missing"),
			row("apDrone", "high_domain_max_rpm", 2, 1, 0, false,
					"rectangular-neighbor-count-missing"),
			row("apDrone", "high_j_extrapolation_probe", 0, 0, 0, false,
					"query-outside-j-rpm-domain"),
			row("cinewhoop", "static_anchor_low_rpm", 1, 1, 0, false,
					"post-review-ct-cp-j-coverage-blocked"),
			row("cinewhoop", "mid_domain_mid_rpm", 4, 3, 2, false,
					"post-review-ct-cp-j-coverage-blocked"),
			row("cinewhoop", "high_domain_max_rpm", 2, 1, 1, false,
					"post-review-ct-cp-j-coverage-blocked"),
			row("cinewhoop", "high_j_extrapolation_probe", 0, 0, 0, false,
					"query-outside-j-rpm-domain"),
			row("heavyLift", "static_anchor_low_rpm", 1, 1, 0, true,
					"ready-for-reviewed-neighbor-binding"),
			row("heavyLift", "mid_domain_mid_rpm", 4, 2, 0, false,
					"rectangular-neighbor-count-missing"),
			row("heavyLift", "high_domain_max_rpm", 2, 1, 0, false,
					"rectangular-neighbor-count-missing"),
			row("heavyLift", "high_j_extrapolation_probe", 0, 0, 0, false,
					"query-outside-j-rpm-domain")
	);

	private PropellerArchiveCtCpJArchiveLookupGridCoverage() {
	}

	public record ArchiveLookupGridTopology(
			String presetName,
			String performanceMatchId,
			int performanceSampleRowCount,
			int staticSampleRowCount,
			int nonstaticSampleRowCount,
			int distinctRpmBinCount,
			int denseNonstaticRpmBinCount,
			int staticOnlyRpmBinCount,
			boolean directRectangularGridReadyAfterReview,
			boolean postReviewCtCpJLookupSeedAllowed,
			boolean postReviewFullSimulationLookupSeedAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record ArchiveLookupGridCoverageRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			double queryAdvanceRatioJ,
			double queryRpm,
			boolean insideLookupDomain,
			int minimumPerformanceNeighborRows,
			int availableRectangularNeighborRows,
			int availableNonstaticNeighborRows,
			boolean reviewedNeighborBindingReady,
			boolean scatteredFitRequired,
			boolean postReviewCtCpJLookupAllowed,
			boolean postReviewFullSimulationLookupAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record ArchiveLookupGridCoverageSummary(
			int topologyPresetCount,
			int queryCoverageRowCount,
			int insideLookupDomainQueryCount,
			int postReviewCtCpJAllowedQueryCount,
			int reviewedNeighborBindingReadyQueryCount,
			int postReviewFullSimulationNeighborReadyQueryCount,
			int scatteredFitRequiredQueryCount,
			int rectangularNeighborMissingQueryCount,
			int nonstaticNeighborMissingQueryCount,
			int extrapolationRejectedQueryCount,
			int coverageBlockedQueryCount,
			int maxAvailableRectangularNeighborRows,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJArchiveLookupGridCoverageAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int presetTopologyRowCount,
			int queryCoverageRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ArchiveLookupGridTopology> topologies,
			List<ArchiveLookupGridCoverageRow> rows,
			ArchiveLookupGridCoverageSummary summary
	) {
		public CtCpJArchiveLookupGridCoverageAudit {
			topologies = List.copyOf(topologies);
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJArchiveLookupGridCoverageAudit audit() {
		return new CtCpJArchiveLookupGridCoverageAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PRESET_TOPOLOGY_ROW_COUNT,
				QUERY_COVERAGE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				TOPOLOGIES,
				COVERAGE_ROWS,
				summary(COVERAGE_ROWS)
		);
	}

	public static ArchiveLookupGridTopology topology(String presetName) {
		return TOPOLOGIES.stream()
				.filter(topology -> topology.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown archive lookup grid topology preset: " + presetName));
	}

	public static ArchiveLookupGridCoverageRow coverage(String presetName, String caseName) {
		return COVERAGE_ROWS.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown archive lookup grid coverage row: " + presetName + " / " + caseName));
	}

	private static ArchiveLookupGridCoverageRow row(
			String presetName,
			String caseName,
			int minimumPerformanceNeighborRows,
			int availableRectangularNeighborRows,
			int availableNonstaticNeighborRows,
			boolean reviewedNeighborBindingReady,
			String blocker
	) {
		PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query =
				PropellerArchiveCtCpJLookupQueryEnvelope.query(presetName, caseName);
		boolean scatteredFitRequired = query.insideLookupDomain()
				&& query.postReviewCtCpJQueryAllowed()
				&& !reviewedNeighborBindingReady;
		String status = statusFor(query, reviewedNeighborBindingReady, scatteredFitRequired, blocker);
		return new ArchiveLookupGridCoverageRow(
				query.presetName(),
				query.caseName(),
				query.performanceMatchId(),
				query.queryAdvanceRatioJ(),
				query.queryRpm(),
				query.insideLookupDomain(),
				minimumPerformanceNeighborRows,
				availableRectangularNeighborRows,
				availableNonstaticNeighborRows,
				reviewedNeighborBindingReady,
				scatteredFitRequired,
				query.postReviewCtCpJQueryAllowed(),
				query.postReviewFullSimulationQueryAllowed(),
				false,
				false,
				status,
				messageFor(query, reviewedNeighborBindingReady, scatteredFitRequired, blocker)
		);
	}

	private static ArchiveLookupGridCoverageSummary summary(List<ArchiveLookupGridCoverageRow> rows) {
		int inside = 0;
		int postReview = 0;
		int ready = 0;
		int fullReady = 0;
		int scattered = 0;
		int rectangularMissing = 0;
		int nonstaticMissing = 0;
		int extrapolation = 0;
		int coverageBlocked = 0;
		int maxNeighbors = 0;
		int runtime = 0;
		int gameplay = 0;
		for (ArchiveLookupGridCoverageRow row : rows) {
			if (row.insideLookupDomain()) {
				inside++;
			}
			if (row.postReviewCtCpJLookupAllowed()) {
				postReview++;
			}
			if (row.reviewedNeighborBindingReady()) {
				ready++;
			}
			if (row.reviewedNeighborBindingReady() && row.postReviewFullSimulationLookupAllowed()) {
				fullReady++;
			}
			if (row.scatteredFitRequired()) {
				scattered++;
			}
			if ("RECTANGULAR_GRID_MISSING".equals(row.status())) {
				rectangularMissing++;
			}
			if (row.insideLookupDomain()
					&& row.postReviewCtCpJLookupAllowed()
					&& row.queryAdvanceRatioJ() > 1.0e-12
					&& row.availableNonstaticNeighborRows() < row.minimumPerformanceNeighborRows()) {
				nonstaticMissing++;
			}
			if ("EXTRAPOLATION_REJECTED".equals(row.status())) {
				extrapolation++;
			}
			if ("COVERAGE_BLOCKED".equals(row.status())) {
				coverageBlocked++;
			}
			maxNeighbors = Math.max(maxNeighbors, row.availableRectangularNeighborRows());
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new ArchiveLookupGridCoverageSummary(
				TOPOLOGIES.size(),
				rows.size(),
				inside,
				postReview,
				ready,
				fullReady,
				scattered,
				rectangularMissing,
				nonstaticMissing,
				extrapolation,
				coverageBlocked,
				maxNeighbors,
				runtime,
				gameplay
		);
	}

	private static String statusFor(
			PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query,
			boolean reviewedNeighborBindingReady,
			boolean scatteredFitRequired,
			String blocker
	) {
		if (!query.insideLookupDomain()) {
			return "EXTRAPOLATION_REJECTED";
		}
		if (!query.postReviewCtCpJQueryAllowed()) {
			return "COVERAGE_BLOCKED";
		}
		if (reviewedNeighborBindingReady) {
			return "NEIGHBOR_BINDING_READY_AFTER_REVIEW";
		}
		if ("rectangular-neighbor-count-missing".equals(blocker)) {
			return "RECTANGULAR_GRID_MISSING";
		}
		if (scatteredFitRequired) {
			return "SCATTERED_FIT_REQUIRED";
		}
		return "BLOCKED";
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query,
			boolean reviewedNeighborBindingReady,
			boolean scatteredFitRequired,
			String blocker
	) {
		if (!query.insideLookupDomain()) {
			return "query-requires-extrapolation-and-is-rejected";
		}
		if (!query.postReviewCtCpJQueryAllowed()) {
			return query.postReviewBlocker();
		}
		if (reviewedNeighborBindingReady) {
			return "reviewed-archive-neighbor-binding-ready";
		}
		if (scatteredFitRequired || "rectangular-neighbor-count-missing".equals(blocker)) {
			return "scattered-ct-cp-j-fit-required-before-lookup-binding";
		}
		return blocker;
	}
}
