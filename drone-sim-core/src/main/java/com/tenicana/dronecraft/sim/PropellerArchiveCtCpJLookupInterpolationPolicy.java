package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupInterpolationPolicy {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-Lookup-Interpolation-Policy-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup interpolation policy specifies reviewed-neighbor requirements and physical guards for future lookup rows; it interpolates no coefficients, imports no raw rows, and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int POLICY_RULE_ROW_COUNT = 9;
	public static final int QUERY_CONTRACT_ROW_COUNT =
			PropellerArchiveCtCpJLookupQueryEnvelope.PRESET_QUERY_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ POLICY_RULE_ROW_COUNT
			+ QUERY_CONTRACT_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"review-source-license-then-bind-reviewed-ct-cp-j-rows-to-interpolation-policy";

	private static final List<InterpolationPolicyRule> POLICY_RULES = List.of(
			new InterpolationPolicyRule("source_license_review", true, false, true, true,
					"raw rows must be reviewed before policy can bind to data",
					"review-source-license-before-importing-raw-rows"),
			new InterpolationPolicyRule("finite_sorted_j_rows", true, false, true, true,
					"J rows must be finite nonnegative and sorted inside each RPM bin",
					"validate-reviewed-j-grid-before-interpolation"),
			new InterpolationPolicyRule("finite_positive_rpm_bins", true, false, true, true,
					"RPM bins must be finite positive and bracket query RPM",
					"validate-reviewed-rpm-bins-before-interpolation"),
			new InterpolationPolicyRule("static_anchor_preservation", true, false, true, true,
					"J-zero CT/CP anchors must remain exactly recoverable",
					"preserve-static-ct-cp-anchor"),
			new InterpolationPolicyRule("reject_extrapolation", true, true, true, true,
					"queries outside reviewed J/RPM bounds are rejected instead of extrapolated",
					"reject-query-until-reviewed-extrapolation-policy"),
			new InterpolationPolicyRule("shape_preserving_ct", true, false, true, true,
					"CT interpolation must not overshoot neighbor extrema",
					"accept-ct-shape-preservation-before-reference-export"),
			new InterpolationPolicyRule("positive_cp", true, false, true, true,
					"CP interpolation must remain positive and finite",
					"accept-cp-positivity-before-reference-export"),
			new InterpolationPolicyRule("eta_consistency", true, false, true, true,
					"eta must agree with J times CT over CP after low-J guard",
					"accept-eta-consistency-before-reference-export"),
			new InterpolationPolicyRule("runtime_leak_guard", true, true, true, true,
					"policy output cannot drive runtime physics or gameplay auto-apply",
					"keep-runtime-coupling-and-gameplay-auto-apply-closed")
	);

	private PropellerArchiveCtCpJLookupInterpolationPolicy() {
	}

	public record InterpolationPolicyRule(
			String ruleName,
			boolean required,
			boolean currentSatisfied,
			boolean reviewedImportSatisfied,
			boolean syntheticTargetSatisfied,
			String requirement,
			String nextRequiredAction
	) {
	}

	public record QueryInterpolationContract(
			String presetName,
			String caseName,
			String performanceMatchId,
			String geometryMatchId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double jCoverageRatio,
			double rpmCoverageFraction,
			boolean insideLookupDomain,
			boolean extrapolationRequired,
			int minimumAdvanceNeighbors,
			int minimumRpmNeighbors,
			int minimumPerformanceNeighborRows,
			boolean staticAnchorPreserved,
			boolean etaConsistencyRequired,
			boolean cpPositiveRequired,
			boolean currentInterpolationAllowed,
			boolean postReviewCtCpJInterpolationAllowed,
			boolean postReviewFullSimulationInterpolationAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String postReviewBlocker,
			String status,
			String message
	) {
	}

	public record InterpolationPolicySummary(
			int policyRuleCount,
			int queryContractCount,
			int insideLookupDomainContractCount,
			int extrapolationRejectedContractCount,
			int currentInterpolationAllowedCount,
			int postReviewCtCpJInterpolationAllowedCount,
			int postReviewFullSimulationInterpolationAllowedCount,
			int postReviewPerformanceOnlyInterpolationCount,
			int bladeCoverageBlockedContractCount,
			int geometryCoverageBlockedContractCount,
			int staticAnchorContractCount,
			int etaConsistencyRequiredContractCount,
			int cpPositiveRequiredContractCount,
			int minimumPostReviewPerformanceNeighborRows,
			int minimumPostReviewFullSimulationNeighborRows,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupInterpolationPolicyAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int policyRuleRowCount,
			int queryContractRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<InterpolationPolicyRule> rules,
			List<QueryInterpolationContract> contracts,
			InterpolationPolicySummary summary
	) {
		public CtCpJLookupInterpolationPolicyAudit {
			rules = List.copyOf(rules);
			contracts = List.copyOf(contracts);
		}
	}

	public static CtCpJLookupInterpolationPolicyAudit audit() {
		List<QueryInterpolationContract> contracts =
				PropellerArchiveCtCpJLookupQueryEnvelope.audit()
						.rows()
						.stream()
						.map(PropellerArchiveCtCpJLookupInterpolationPolicy::contract)
						.toList();
		return new CtCpJLookupInterpolationPolicyAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				POLICY_RULE_ROW_COUNT,
				QUERY_CONTRACT_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				POLICY_RULES,
				contracts,
				summary(contracts)
		);
	}

	public static InterpolationPolicyRule rule(String ruleName) {
		return POLICY_RULES.stream()
				.filter(rule -> rule.ruleName().equals(ruleName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown CT/CP/J interpolation rule: " + ruleName));
	}

	public static QueryInterpolationContract contract(String presetName, String caseName) {
		return audit().contracts().stream()
				.filter(contract -> contract.presetName().equals(presetName)
						&& contract.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J interpolation contract: " + presetName + " / " + caseName));
	}

	private static QueryInterpolationContract contract(
			PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query
	) {
		boolean acceptedPerformance = query.postReviewCtCpJQueryAllowed();
		boolean acceptedFull = query.postReviewFullSimulationQueryAllowed();
		int advanceNeighbors = minimumAdvanceNeighbors(query);
		int rpmNeighbors = minimumRpmNeighbors(query);
		int neighborRows = query.insideLookupDomain() ? advanceNeighbors * rpmNeighbors : 0;
		boolean staticAnchor = query.insideLookupDomain() && query.queryAdvanceRatioJ() <= 1.0e-12;
		return new QueryInterpolationContract(
				query.presetName(),
				query.caseName(),
				query.performanceMatchId(),
				query.geometryMatchId(),
				query.queryAdvanceRatioJ(),
				query.queryRpm(),
				query.jCoverageRatio(),
				query.rpmCoverageFraction(),
				query.insideLookupDomain(),
				query.extrapolationRequired(),
				advanceNeighbors,
				rpmNeighbors,
				neighborRows,
				staticAnchor,
				acceptedPerformance,
				acceptedPerformance,
				false,
				acceptedPerformance,
				acceptedFull,
				false,
				false,
				query.postReviewBlocker(),
				statusFor(query, acceptedPerformance, acceptedFull),
				messageFor(query, acceptedPerformance, acceptedFull)
		);
	}

	private static InterpolationPolicySummary summary(List<QueryInterpolationContract> contracts) {
		int inside = 0;
		int extrapolated = 0;
		int current = 0;
		int performance = 0;
		int full = 0;
		int performanceOnly = 0;
		int bladeBlocked = 0;
		int geometryBlocked = 0;
		int staticAnchors = 0;
		int eta = 0;
		int cp = 0;
		int performanceNeighbors = 0;
		int fullNeighbors = 0;
		int runtime = 0;
		int gameplay = 0;
		for (QueryInterpolationContract contract : contracts) {
			if (contract.insideLookupDomain()) {
				inside++;
			}
			if (contract.extrapolationRequired()) {
				extrapolated++;
			}
			if (contract.currentInterpolationAllowed()) {
				current++;
			}
			if (contract.postReviewCtCpJInterpolationAllowed()) {
				performance++;
				performanceNeighbors += contract.minimumPerformanceNeighborRows();
			}
			if (contract.postReviewFullSimulationInterpolationAllowed()) {
				full++;
				fullNeighbors += contract.minimumPerformanceNeighborRows();
			}
			if (contract.postReviewCtCpJInterpolationAllowed()
					&& !contract.postReviewFullSimulationInterpolationAllowed()) {
				performanceOnly++;
			}
			if ("blade-count-coverage-missing".equals(contract.postReviewBlocker())) {
				bladeBlocked++;
			}
			if ("geometry-fit-input-missing".equals(contract.postReviewBlocker())) {
				geometryBlocked++;
			}
			if (contract.staticAnchorPreserved() && contract.postReviewCtCpJInterpolationAllowed()) {
				staticAnchors++;
			}
			if (contract.etaConsistencyRequired()) {
				eta++;
			}
			if (contract.cpPositiveRequired()) {
				cp++;
			}
			if (contract.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (contract.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new InterpolationPolicySummary(
				POLICY_RULES.size(),
				contracts.size(),
				inside,
				extrapolated,
				current,
				performance,
				full,
				performanceOnly,
				bladeBlocked,
				geometryBlocked,
				staticAnchors,
				eta,
				cp,
				performanceNeighbors,
				fullNeighbors,
				runtime,
				gameplay,
				NEXT_REQUIRED_ACTION
		);
	}

	private static int minimumAdvanceNeighbors(
			PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query
	) {
		if (!query.insideLookupDomain()) {
			return 0;
		}
		if (query.queryAdvanceRatioJ() <= 1.0e-12) {
			return 1;
		}
		return 2;
	}

	private static int minimumRpmNeighbors(
			PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query
	) {
		if (!query.insideLookupDomain()) {
			return 0;
		}
		if (query.rpmCoverageFraction() <= 1.0e-12 || query.rpmCoverageFraction() >= 1.0 - 1.0e-12) {
			return 1;
		}
		return 2;
	}

	private static String statusFor(
			PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query,
			boolean acceptedPerformance,
			boolean acceptedFull
	) {
		if (query.extrapolationRequired()) {
			return "EXTRAPOLATION_REJECTED";
		}
		if (!acceptedPerformance) {
			return "COVERAGE_BLOCKED";
		}
		if (!acceptedFull) {
			return "PERFORMANCE_INTERPOLATION_READY_AFTER_REVIEW_FULL_SIM_BLOCKED";
		}
		return "INTERPOLATION_READY_AFTER_REVIEW";
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupQueryEnvelope.LookupQueryRow query,
			boolean acceptedPerformance,
			boolean acceptedFull
	) {
		if (query.extrapolationRequired()) {
			return "query-requires-extrapolation-and-is-rejected";
		}
		if (!acceptedPerformance || !acceptedFull) {
			return query.message();
		}
		return NEXT_REQUIRED_ACTION;
	}
}
