package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLookupQueryEnvelope {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-Lookup-Query-Envelope-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup query envelope turns the offline lookup seed into deterministic J/RPM domain checks; it performs no coefficient interpolation, imports no raw rows, and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int QUERY_CASE_ROW_COUNT = 4;
	public static final int PRESET_QUERY_ROW_COUNT =
			PropellerArchiveCtCpJLookupSeed.PRESET_SEED_ROW_COUNT * QUERY_CASE_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ PRESET_QUERY_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"review-source-license-then-run-reviewed-lookup-query-envelope-with-real-ct-cp-j-rows";
	private static final double APDRONE_FORWARD_DIAGNOSTIC_MAX_EQUIVALENT_J = 0.619644960857424;
	private static final double APDRONE_FORWARD_DIAGNOSTIC_ARCHIVE_COVERAGE_RATIO = 0.762358465621831;
	private static final boolean APDRONE_FORWARD_DIAGNOSTIC_INSIDE_SEED_DOMAIN = true;

	private static final List<LookupQueryCase> QUERY_CASES = List.of(
			new LookupQueryCase("static_anchor_low_rpm", 0.0, 0.0, true,
					"preserve-j-zero-static-ct-cp-anchor"),
			new LookupQueryCase("mid_domain_mid_rpm", 0.5, 0.5, true,
					"exercise-mid-domain-interpolation-without-extrapolation"),
			new LookupQueryCase("high_domain_max_rpm", 0.90, 1.0, true,
					"exercise-upper-domain-interpolation-without-extrapolation"),
			new LookupQueryCase("high_j_extrapolation_probe", 1.15, 1.0, false,
					"prove-high-j-queries-stay-rejected-until-reviewed-extrapolation-policy")
	);

	private PropellerArchiveCtCpJLookupQueryEnvelope() {
	}

	public record LookupQueryCase(
			String caseName,
			double advanceRatioFraction,
			double rpmFraction,
			boolean expectedInsideLookupDomain,
			String purpose
	) {
	}

	public record LookupQueryRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String geometryMatchId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double equivalentProjectMu,
			double jCoverageRatio,
			double rpmCoverageFraction,
			boolean insideAdvanceRatioDomain,
			boolean insideRpmDomain,
			boolean insideLookupDomain,
			boolean extrapolationRequired,
			boolean currentQueryAllowed,
			boolean postReviewCtCpJQueryAllowed,
			boolean postReviewFullSimulationQueryAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String currentBlocker,
			String postReviewBlocker,
			String status,
			String message
	) {
	}

	public record LookupQueryEnvelopeSummary(
			int queryRowCount,
			int queryCaseCount,
			int insideAdvanceRatioDomainCount,
			int insideRpmDomainCount,
			int insideLookupDomainCount,
			int extrapolationRequiredCount,
			int currentQueryAllowedCount,
			int postReviewCtCpJQueryAllowedCount,
			int postReviewFullSimulationQueryAllowedCount,
			int postReviewPerformanceOnlyQueryCount,
			int bladeCoverageBlockedQueryCount,
			int geometryCoverageBlockedQueryCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double maxEquivalentProjectMu,
			double maxInsideDomainEquivalentProjectMu,
			double apDroneForwardDiagnosticMaxEquivalentJ,
			double apDroneForwardDiagnosticArchiveCoverageRatio,
			boolean apDroneForwardDiagnosticInsideSeedDomain,
			String nextRequiredAction
	) {
	}

	public record LookupQueryEnvelopeAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int queryCaseRowCount,
			int presetQueryRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupQueryCase> cases,
			List<LookupQueryRow> rows,
			LookupQueryEnvelopeSummary summary
	) {
		public LookupQueryEnvelopeAudit {
			cases = List.copyOf(cases);
			rows = List.copyOf(rows);
		}
	}

	public static LookupQueryEnvelopeAudit audit() {
		List<LookupQueryRow> rows = new ArrayList<>();
		for (PropellerArchiveCtCpJLookupSeed.PresetLookupSeed seed
				: PropellerArchiveCtCpJLookupSeed.audit().presets()) {
			for (LookupQueryCase queryCase : QUERY_CASES) {
				rows.add(row(seed, queryCase));
			}
		}
		return new LookupQueryEnvelopeAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				QUERY_CASE_ROW_COUNT,
				PRESET_QUERY_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				QUERY_CASES,
				rows,
				summary(rows)
		);
	}

	public static LookupQueryCase queryCase(String caseName) {
		return QUERY_CASES.stream()
				.filter(queryCase -> queryCase.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown CT/CP/J lookup query case: " + caseName));
	}

	public static LookupQueryRow query(String presetName, String caseName) {
		return row(PropellerArchiveCtCpJLookupSeed.preset(presetName), queryCase(caseName));
	}

	private static LookupQueryRow row(
			PropellerArchiveCtCpJLookupSeed.PresetLookupSeed seed,
			LookupQueryCase queryCase
	) {
		double queryJ = seed.advanceRatioMin()
				+ (seed.advanceRatioMax() - seed.advanceRatioMin()) * queryCase.advanceRatioFraction();
		double queryRpm = seed.rpmMin() + (seed.rpmMax() - seed.rpmMin()) * queryCase.rpmFraction();
		double equivalentMu = queryJ / Math.PI;
		boolean insideJ = queryJ >= seed.advanceRatioMin() - 1.0e-12
				&& queryJ <= seed.advanceRatioMax() + 1.0e-12;
		boolean insideRpm = queryRpm >= seed.rpmMin() - 1.0e-9
				&& queryRpm <= seed.rpmMax() + 1.0e-9;
		boolean insideDomain = insideJ && insideRpm;
		boolean extrapolation = !insideDomain;
		boolean currentAllowed = seed.currentLookupSeedAllowed() && insideDomain;
		boolean postReviewCtCpJ = seed.postReviewCtCpJLookupSeedAllowed() && insideDomain;
		boolean postReviewFull = seed.postReviewFullSimulationLookupSeedAllowed() && insideDomain;
		return new LookupQueryRow(
				seed.presetName(),
				queryCase.caseName(),
				seed.performanceMatchId(),
				seed.geometryMatchId(),
				queryJ,
				queryRpm,
				equivalentMu,
				safeRatio(queryJ - seed.advanceRatioMin(), seed.advanceRatioMax() - seed.advanceRatioMin()),
				safeRatio(queryRpm - seed.rpmMin(), seed.rpmMax() - seed.rpmMin()),
				insideJ,
				insideRpm,
				insideDomain,
				extrapolation,
				currentAllowed,
				postReviewCtCpJ,
				postReviewFull,
				false,
				false,
				seed.currentBlocker(),
				insideDomain ? seed.postReviewBlocker() : "query-outside-j-rpm-domain",
				statusFor(seed, insideDomain, postReviewCtCpJ, postReviewFull),
				messageFor(seed, insideDomain, postReviewCtCpJ, postReviewFull)
		);
	}

	private static LookupQueryEnvelopeSummary summary(List<LookupQueryRow> rows) {
		int insideJ = 0;
		int insideRpm = 0;
		int insideDomain = 0;
		int extrapolation = 0;
		int current = 0;
		int ctCpJ = 0;
		int full = 0;
		int performanceOnly = 0;
		int bladeBlocked = 0;
		int geometryBlocked = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxMu = 0.0;
		double maxInsideMu = 0.0;
		for (LookupQueryRow row : rows) {
			if (row.insideAdvanceRatioDomain()) {
				insideJ++;
			}
			if (row.insideRpmDomain()) {
				insideRpm++;
			}
			if (row.insideLookupDomain()) {
				insideDomain++;
				maxInsideMu = Math.max(maxInsideMu, row.equivalentProjectMu());
			}
			if (row.extrapolationRequired()) {
				extrapolation++;
			}
			if (row.currentQueryAllowed()) {
				current++;
			}
			if (row.postReviewCtCpJQueryAllowed()) {
				ctCpJ++;
			}
			if (row.postReviewFullSimulationQueryAllowed()) {
				full++;
			}
			if (row.postReviewCtCpJQueryAllowed() && !row.postReviewFullSimulationQueryAllowed()) {
				performanceOnly++;
			}
			if ("blade-count-coverage-missing".equals(row.postReviewBlocker())) {
				bladeBlocked++;
			}
			if ("geometry-fit-input-missing".equals(row.postReviewBlocker())) {
				geometryBlocked++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxMu = Math.max(maxMu, row.equivalentProjectMu());
		}
		return new LookupQueryEnvelopeSummary(
				rows.size(),
				QUERY_CASES.size(),
				insideJ,
				insideRpm,
				insideDomain,
				extrapolation,
				current,
				ctCpJ,
				full,
				performanceOnly,
				bladeBlocked,
				geometryBlocked,
				runtime,
				gameplay,
				maxMu,
				maxInsideMu,
				APDRONE_FORWARD_DIAGNOSTIC_MAX_EQUIVALENT_J,
				APDRONE_FORWARD_DIAGNOSTIC_ARCHIVE_COVERAGE_RATIO,
				APDRONE_FORWARD_DIAGNOSTIC_INSIDE_SEED_DOMAIN,
				NEXT_REQUIRED_ACTION
		);
	}

	private static String statusFor(
			PropellerArchiveCtCpJLookupSeed.PresetLookupSeed seed,
			boolean insideDomain,
			boolean postReviewCtCpJ,
			boolean postReviewFull
	) {
		if (!insideDomain) {
			return "OUT_OF_DOMAIN";
		}
		if (!postReviewCtCpJ) {
			return "COVERAGE_BLOCKED";
		}
		if (!postReviewFull) {
			return "PERFORMANCE_LOOKUP_READY_AFTER_REVIEW_FULL_SIM_BLOCKED";
		}
		return seed.currentLookupSeedAllowed() ? "CURRENT_READY" : "LOOKUP_READY_AFTER_REVIEW";
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupSeed.PresetLookupSeed seed,
			boolean insideDomain,
			boolean postReviewCtCpJ,
			boolean postReviewFull
	) {
		if (!insideDomain) {
			return "query-requires-extrapolation-and-is-rejected";
		}
		if (!postReviewCtCpJ) {
			return seed.postReviewNextRequiredAction();
		}
		if (!postReviewFull) {
			return seed.postReviewNextRequiredAction();
		}
		return NEXT_REQUIRED_ACTION;
	}

	private static double safeRatio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
