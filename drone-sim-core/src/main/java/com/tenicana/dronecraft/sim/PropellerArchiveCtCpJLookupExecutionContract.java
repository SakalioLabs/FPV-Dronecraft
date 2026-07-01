package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PropellerArchiveCtCpJLookupExecutionContract {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Execution-Contract-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup execution accepts only caller-supplied reviewed rows after the reviewed grid input, reviewed coefficient payload, and scattered-fit execution handoff are ready, rejects extrapolation, preserves J-zero anchors, and never imports raw archive rows or enables runtime coupling/gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 10;
	public static final int EXECUTION_RULE_ROW_COUNT = 11;
	public static final int SCENARIO_ROW_COUNT = 8;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ EXECUTION_RULE_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"complete-scattered-surface-fit-execution-handoff-before-lookup-run";

	private static final double EPSILON = 1.0e-9;

	private static final List<LookupExecutionRule> EXECUTION_RULES = List.of(
			new LookupExecutionRule("source_license_review", true, false, false, true,
					"raw propeller rows must be reviewed before execution input can be trusted",
					"review-source-license-before-binding-rows"),
			new LookupExecutionRule("caller_supplied_reviewed_rows", true, false, true, true,
					"lookup runner receives rows from the reviewed offline import path only",
					"feed-reviewed-rows-from-offline-import"),
			new LookupExecutionRule("scattered_fit_execution_handoff_ready", true, false, false, true,
					"scattered surface fit handoff must be ready before execution consumes reviewed rows",
					"complete-scattered-surface-fit-execution-handoff-before-lookup-run"),
			new LookupExecutionRule("archive_curve_shape_guard_inherited", true, false, false, true,
					"lookup execution must inherit the archive curve-shape guard from the fit handoff",
					"carry-archive-curve-shape-guard-into-lookup-execution"),
			new LookupExecutionRule("finite_sorted_grid", true, false, true, true,
					"J/RPM grid rows must be finite, nonnegative in J, positive in RPM/CP, and duplicate-free",
					"validate-reviewed-row-grid-before-execution"),
			new LookupExecutionRule("reject_extrapolation", true, true, true, true,
					"queries outside the reviewed J/RPM bracket are rejected",
					"keep-extrapolation-disabled-until-reviewed"),
			new LookupExecutionRule("minimum_neighbor_rows", true, false, true, true,
					"observed neighbor rows must meet the interpolation policy contract",
					"feed-all-required-bracketing-neighbors"),
			new LookupExecutionRule("reviewed_coefficient_payload_ready", true, false, true, true,
					"each reviewed grid input slot must carry reviewed finite CT and positive CP before execution",
					"review-and-bind-ct-cp-coefficient-payloads-to-grid-slots-before-lookup-execution"),
			new LookupExecutionRule("shape_preserving_ct", true, false, true, true,
					"interpolated CT cannot exceed the neighbor CT extrema",
					"verify-ct-shape-guard-before-acceptance"),
			new LookupExecutionRule("cp_eta_static_guards", true, false, true, true,
					"CP stays positive, eta equals J*CT/CP, and static anchors remain exact",
					"verify-cp-eta-static-guards-before-acceptance"),
			new LookupExecutionRule("runtime_leak_guard", true, true, true, true,
					"execution results cannot mutate runtime physics or gameplay tuning",
					"keep-runtime-coupling-and-gameplay-auto-apply-closed")
	);

	private PropellerArchiveCtCpJLookupExecutionContract() {
	}

	public record LookupExecutionRule(
			String ruleName,
			boolean required,
			boolean currentSatisfied,
			boolean callerSuppliedReviewedRowsSatisfied,
			boolean syntheticTargetSatisfied,
			String requirement,
			String nextRequiredAction
	) {
	}

	public record LookupGridRow(
			String rowId,
			double advanceRatioJ,
			double rpm,
			double ctCoefficient,
			double cpCoefficient
	) {
	}

	public record LookupExecutionResult(
			String presetName,
			String caseName,
			double queryAdvanceRatioJ,
			double queryRpm,
			double lowerAdvanceRatioJ,
			double upperAdvanceRatioJ,
			double lowerRpm,
			double upperRpm,
			double advanceInterpolationFraction,
			double rpmInterpolationFraction,
			int observedNeighborRows,
			int minimumNeighborRowsRequired,
			double ctCoefficient,
			double cpCoefficient,
			double eta,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double etaResidual,
			double staticAnchorError,
			boolean archiveCurveShapeGuardInherited,
			int negativeThrustTailExecutionInputRowCount,
			double archiveCurveEtaFormulaResidual,
			double archiveCurveCtIncrease,
			boolean insideLookupDomain,
			boolean acceptedByLookupGate,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record LookupExecutionScenario(
			String scenarioName,
			LookupExecutionResult result
	) {
	}

	public record LookupExecutionSummary(
			int scenarioCount,
			int acceptedScenarioCount,
			int blockedScenarioCount,
			int handoffBlockedScenarioCount,
			int noReviewedRowsScenarioCount,
			int outOfDomainScenarioCount,
			int missingNeighborScenarioCount,
			int acceptanceGuardFailedScenarioCount,
			int archiveCurveShapeGuardInheritedScenarioCount,
			int archiveCurveShapeGuardBlockedScenarioCount,
			int maxObservedNeighborRows,
			int maxNegativeThrustTailExecutionInputRowCount,
			double maxCtShapeOvershoot,
			double minAcceptedCpCoefficient,
			double maxEtaResidual,
			double maxStaticAnchorError,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupExecutionContractAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int executionRuleRowCount,
			int scenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupExecutionRule> rules,
			List<LookupExecutionScenario> scenarios,
			LookupExecutionSummary summary
	) {
		public CtCpJLookupExecutionContractAudit {
			rules = List.copyOf(rules);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupExecutionContractAudit audit() {
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract staticContract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "static_anchor_low_rpm");
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract midContract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract extrapolationContract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "high_j_extrapolation_probe");
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit
				handoffAudit = PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.audit();
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary currentHandoff =
				handoffScenario(handoffAudit, "current_source_review_blocked_no_execution_input");
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary readyHandoff =
				handoffScenario(handoffAudit, "surface_fit_ready_execution_input_handoff");
		List<LookupExecutionScenario> scenarios = List.of(
				new LookupExecutionScenario("current_handoff_blocked_no_execution",
						executeFromHandoff(currentHandoff, List.of(), midContract)),
				new LookupExecutionScenario("current_no_reviewed_rows", execute(List.of(), midContract)),
				new LookupExecutionScenario("synthetic_handoff_curve_shape_guard_blocked",
						executeFromHandoff(shapeGuardBlockedHandoff(readyHandoff),
								midRows(midContract, 1.0), midContract)),
				new LookupExecutionScenario("synthetic_static_anchor_exact",
						executeFromHandoff(readyHandoff, staticAnchorRows(staticContract), staticContract)),
				new LookupExecutionScenario("synthetic_mid_bilinear_pass",
						executeFromHandoff(readyHandoff, midRows(midContract, 1.0), midContract)),
				new LookupExecutionScenario("synthetic_missing_neighbor_blocked",
						executeFromHandoff(readyHandoff, missingNeighborRows(midContract), midContract)),
				new LookupExecutionScenario("synthetic_high_j_extrapolation_rejected",
						executeFromHandoff(readyHandoff, midRows(midContract, 1.0), extrapolationContract)),
				new LookupExecutionScenario("synthetic_cp_guard_failed",
						executeFromHandoff(readyHandoff, midRows(midContract, 1.0e-6), midContract))
		);
		return new CtCpJLookupExecutionContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				EXECUTION_RULE_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				EXECUTION_RULES,
				scenarios,
				summary(scenarios)
		);
	}

	public static LookupExecutionRule rule(String ruleName) {
		return EXECUTION_RULES.stream()
				.filter(rule -> rule.ruleName().equals(ruleName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown CT/CP/J lookup execution rule: " + ruleName));
	}

	public static LookupExecutionResult execute(
			List<LookupGridRow> rows,
			String presetName,
			String caseName
	) {
		return execute(rows, PropellerArchiveCtCpJLookupInterpolationPolicy.contract(presetName, caseName));
	}

	public static LookupExecutionResult execute(
			List<LookupGridRow> rows,
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract
	) {
		if (contract == null) {
			throw new IllegalArgumentException("query interpolation contract must not be null.");
		}
		validateRows(rows);
		if (!contract.insideLookupDomain() || contract.extrapolationRequired()) {
			return blocked(contract, 0, 0, 0.0, 0.0, "OUT_OF_DOMAIN",
					"query-requires-extrapolation-and-is-rejected");
		}
		if (!contract.postReviewCtCpJInterpolationAllowed()) {
			return blocked(contract, 0, 0, 0.0, 0.0, "LOOKUP_CONTRACT_BLOCKED",
					"lookup-contract-coverage-blocked");
		}
		if (rows.isEmpty()) {
			return blocked(contract, 0, contract.minimumPerformanceNeighborRows(), 0.0, 0.0,
					"NO_REVIEWED_ROWS", "reviewed-ct-cp-j-rows-missing");
		}
		Bracket bracket = bracket(rows, contract.queryAdvanceRatioJ(), contract.queryRpm());
		if (!bracket.inside()) {
			return blocked(contract, 0, contract.minimumPerformanceNeighborRows(), 0.0, 0.0,
					"OUT_OF_DOMAIN", "reviewed-grid-does-not-bracket-query");
		}
		List<LookupGridRow> neighbors = neighborRows(rows, bracket);
		if (neighbors.size() < expectedNeighborRows(bracket)) {
			return blocked(contract, neighbors.size(), contract.minimumPerformanceNeighborRows(),
					bracket.lowerAdvanceRatioJ(), bracket.lowerRpm(),
					"NEIGHBOR_ROWS_MISSING", "reviewed-neighbor-row-missing");
		}

		LookupGridRow j0r0 = findRow(rows, bracket.lowerAdvanceRatioJ(), bracket.lowerRpm());
		LookupGridRow j1r0 = findRow(rows, bracket.upperAdvanceRatioJ(), bracket.lowerRpm());
		LookupGridRow j0r1 = findRow(rows, bracket.lowerAdvanceRatioJ(), bracket.upperRpm());
		LookupGridRow j1r1 = findRow(rows, bracket.upperAdvanceRatioJ(), bracket.upperRpm());
		double ct = interpolate(bracket, j0r0.ctCoefficient(), j1r0.ctCoefficient(),
				j0r1.ctCoefficient(), j1r1.ctCoefficient());
		double cp = interpolate(bracket, j0r0.cpCoefficient(), j1r0.cpCoefficient(),
				j0r1.cpCoefficient(), j1r1.cpCoefficient());
		double eta = eta(contract.queryAdvanceRatioJ(), ct, cp);
		double expectedEta = eta(contract.queryAdvanceRatioJ(), ct, cp);
		double etaResidual = Math.abs(eta - expectedEta);
		double minCt = neighbors.stream()
				.mapToDouble(LookupGridRow::ctCoefficient)
				.min()
				.orElse(ct);
		double maxCt = neighbors.stream()
				.mapToDouble(LookupGridRow::ctCoefficient)
				.max()
				.orElse(ct);
		double overshoot = Math.max(Math.max(0.0, ct - maxCt), Math.max(0.0, minCt - ct));
		double minCp = Math.min(cp, neighbors.stream()
				.mapToDouble(LookupGridRow::cpCoefficient)
				.min()
				.orElse(cp));
		double staticAnchorError = staticAnchorError(rows, contract, ct);
		boolean accepted = neighbors.size() >= contract.minimumPerformanceNeighborRows()
				&& overshoot <= PropellerArchiveCtCpJLookupAcceptanceGate.MAX_CT_SHAPE_OVERSHOOT
				&& minCp >= PropellerArchiveCtCpJLookupAcceptanceGate.MIN_CP_COEFFICIENT
				&& etaResidual <= PropellerArchiveCtCpJLookupAcceptanceGate.MAX_ETA_RESIDUAL
				&& staticAnchorError <= (contract.staticAnchorPreserved()
						? PropellerArchiveCtCpJLookupAcceptanceGate.MAX_STATIC_ANCHOR_ERROR
						: 0.0);
		return result(contract, bracket, neighbors.size(), contract.minimumPerformanceNeighborRows(),
				ct, cp, eta, overshoot, minCp, etaResidual, staticAnchorError, true, accepted,
				accepted ? "ACCEPTED" : "ACCEPTANCE_GUARD_FAILED",
				accepted ? "lookup-execution-accepted" : guardFailureMessage(overshoot, minCp,
						etaResidual, staticAnchorError, contract.staticAnchorPreserved()));
	}

	public static LookupExecutionResult executeFromHandoff(
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary handoff,
			List<LookupGridRow> rows,
			String presetName,
			String caseName
	) {
		return executeFromHandoff(handoff, rows,
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract(presetName, caseName));
	}

	public static LookupExecutionResult executeFromHandoff(
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary handoff,
			List<LookupGridRow> rows,
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract
	) {
		if (contract == null) {
			throw new IllegalArgumentException("query interpolation contract must not be null.");
		}
		validateRows(rows);
		if (handoff == null) {
			throw new IllegalArgumentException("execution handoff summary must not be null.");
		}
		if (handoff.sourceRuntimeInfo() == null || handoff.sourceRuntimeInfo().isBlank()) {
			throw new IllegalArgumentException("handoff sourceRuntimeInfo must not be blank.");
		}
		if (handoff.runtimeCouplingAllowed() || handoff.gameplayAutoApplyAllowed()) {
			return blocked(contract, 0, contract.minimumPerformanceNeighborRows(), 0.0, 0.0,
					"HANDOFF_LEAK_GUARD_FAILED", "handoff-runtime-leak-guard-failed");
		}
		if (!handoff.executionInputHandoffReady()
				|| !handoff.sourceRowsReviewed()
				|| !handoff.scatteredSurfaceFitContractReady()) {
			return blocked(contract, 0, contract.minimumPerformanceNeighborRows(), 0.0, 0.0,
					"HANDOFF_BLOCKED", handoff.message());
		}
		if (handoff.exportedExecutionInputRowCount() < handoff.expectedExecutionInputRowCount()
				|| handoff.candidateExecutionInputRowCount() < handoff.expectedExecutionInputRowCount()) {
			return blocked(contract, 0, contract.minimumPerformanceNeighborRows(), 0.0, 0.0,
					"HANDOFF_EXPORT_INCOMPLETE", "execution-input-handoff-export-incomplete");
		}
		if (handoff.archiveCurveShapeGuardReadyTargetCount() < handoff.expectedExecutionInputRowCount()
				|| !Double.isFinite(handoff.maxArchiveCurveEtaFormulaResidual())
				|| handoff.maxArchiveCurveEtaFormulaResidual()
						> PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL
				|| !Double.isFinite(handoff.maxArchiveCurveCtIncrease())
				|| handoff.maxArchiveCurveCtIncrease()
						> PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE) {
			return withArchiveCurveShapeDiagnostics(
					blocked(contract, 0, contract.minimumPerformanceNeighborRows(), 0.0, 0.0,
							"HANDOFF_CURVE_SHAPE_GUARD_FAILED",
							"archive-curve-shape-guard-not-ready"),
					handoff,
					false);
		}
		return withArchiveCurveShapeDiagnostics(execute(rows, contract), handoff, true);
	}

	public static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult acceptanceResult(
			List<LookupGridRow> rows,
			String presetName,
			String caseName
	) {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target =
				PropellerArchiveCtCpJLookupAcceptanceGate.target(presetName, caseName);
		LookupExecutionResult result = execute(rows, presetName, caseName);
		return PropellerArchiveCtCpJLookupAcceptanceGate.result(
				target.presetName(),
				target.caseName(),
				result.observedNeighborRows(),
				result.maxCtShapeOvershoot(),
				result.minCpCoefficient(),
				result.etaResidual(),
				result.staticAnchorError()
		);
	}

	public static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult acceptanceResultFromHandoff(
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary handoff,
			List<LookupGridRow> rows,
			String presetName,
			String caseName
	) {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target =
				PropellerArchiveCtCpJLookupAcceptanceGate.target(presetName, caseName);
		LookupExecutionResult result = executeFromHandoff(handoff, rows, presetName, caseName);
		return PropellerArchiveCtCpJLookupAcceptanceGate.result(
				target.presetName(),
				target.caseName(),
				result.observedNeighborRows(),
				result.maxCtShapeOvershoot(),
				result.minCpCoefficient(),
				result.etaResidual(),
				result.staticAnchorError()
		);
	}

	private static LookupExecutionSummary summary(List<LookupExecutionScenario> scenarios) {
		int accepted = 0;
		int handoffBlocked = 0;
		int noRows = 0;
		int outOfDomain = 0;
		int missing = 0;
		int guardFailed = 0;
		int inheritedShape = 0;
		int shapeBlocked = 0;
		int maxNeighbors = 0;
		int maxNegativeThrustTail = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxOvershoot = 0.0;
		double minAcceptedCp = Double.POSITIVE_INFINITY;
		double maxEtaResidual = 0.0;
		double maxStaticAnchorError = 0.0;
		double maxArchiveEtaResidual = 0.0;
		double maxArchiveCtIncrease = 0.0;
		for (LookupExecutionScenario scenario : scenarios) {
			LookupExecutionResult result = scenario.result();
			if (result.acceptedByLookupGate()) {
				accepted++;
				minAcceptedCp = Math.min(minAcceptedCp, result.minCpCoefficient());
			}
			if (result.status().startsWith("HANDOFF_")) {
				handoffBlocked++;
			}
			if ("NO_REVIEWED_ROWS".equals(result.status())) {
				noRows++;
			}
			if ("OUT_OF_DOMAIN".equals(result.status())) {
				outOfDomain++;
			}
			if ("NEIGHBOR_ROWS_MISSING".equals(result.status())) {
				missing++;
			}
			if ("ACCEPTANCE_GUARD_FAILED".equals(result.status())) {
				guardFailed++;
			}
			if (result.archiveCurveShapeGuardInherited()) {
				inheritedShape++;
			}
			if ("HANDOFF_CURVE_SHAPE_GUARD_FAILED".equals(result.status())) {
				shapeBlocked++;
			}
			maxNeighbors = Math.max(maxNeighbors, result.observedNeighborRows());
			maxNegativeThrustTail = Math.max(maxNegativeThrustTail,
					result.negativeThrustTailExecutionInputRowCount());
			maxOvershoot = Math.max(maxOvershoot, result.maxCtShapeOvershoot());
			maxEtaResidual = Math.max(maxEtaResidual, result.etaResidual());
			maxStaticAnchorError = Math.max(maxStaticAnchorError, result.staticAnchorError());
			maxArchiveEtaResidual = Math.max(maxArchiveEtaResidual,
					result.archiveCurveEtaFormulaResidual());
			maxArchiveCtIncrease = Math.max(maxArchiveCtIncrease, result.archiveCurveCtIncrease());
			if (result.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (result.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new LookupExecutionSummary(
				scenarios.size(),
				accepted,
				scenarios.size() - accepted,
				handoffBlocked,
				noRows,
				outOfDomain,
				missing,
				guardFailed,
				inheritedShape,
				shapeBlocked,
				maxNeighbors,
				maxNegativeThrustTail,
				maxOvershoot,
				Double.isInfinite(minAcceptedCp) ? 0.0 : minAcceptedCp,
				maxEtaResidual,
				maxStaticAnchorError,
				maxArchiveEtaResidual,
				maxArchiveCtIncrease,
				runtime,
				gameplay,
				NEXT_REQUIRED_ACTION
		);
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary
			handoffScenario(
					PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff
							.CtCpJScatteredSurfaceFitExecutionHandoffAudit audit,
					String scenarioName
			) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary
			shapeGuardBlockedHandoff(
					PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary handoff
			) {
		return new PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary(
				handoff.sourceRowsReviewed(),
				handoff.scatteredSurfaceFitContractReady(),
				handoff.scatteredSurfaceFitRun(),
				handoff.expectedExecutionInputRowCount(),
				handoff.candidateExecutionInputRowCount(),
				handoff.exportedExecutionInputRowCount(),
				handoff.directNeighborCandidateRowCount(),
				handoff.scatteredSurfaceCandidateRowCount(),
				handoff.fullSimulationExecutionInputRowCount(),
				handoff.performanceOnlyExecutionInputRowCount(),
				Math.max(0, handoff.expectedExecutionInputRowCount() - 1),
				handoff.negativeThrustTailExecutionInputRowCount(),
				handoff.maxArchiveCurveEtaFormulaResidual(),
				handoff.maxArchiveCurveCtIncrease(),
				handoff.executionInputHandoffReady(),
				handoff.runtimeCouplingAllowed(),
				handoff.gameplayAutoApplyAllowed(),
				"BLOCKED",
				"archive-curve-shape-guard-not-ready",
				handoff.sourceRuntimeInfo());
	}

	private static List<LookupGridRow> staticAnchorRows(
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract
	) {
		return List.of(new LookupGridRow("static-anchor",
				contract.queryAdvanceRatioJ(), contract.queryRpm(), 0.120, 0.040));
	}

	private static List<LookupGridRow> midRows(
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract,
			double cpScale
	) {
		double jStep = 0.100;
		double rpmStep = 750.0;
		double j0 = contract.queryAdvanceRatioJ() - jStep;
		double j1 = contract.queryAdvanceRatioJ() + jStep;
		double rpm0 = contract.queryRpm() - rpmStep;
		double rpm1 = contract.queryRpm() + rpmStep;
		return List.of(
				new LookupGridRow("j0-r0", j0, rpm0, 0.100, 0.046 * cpScale),
				new LookupGridRow("j1-r0", j1, rpm0, 0.092, 0.051 * cpScale),
				new LookupGridRow("j0-r1", j0, rpm1, 0.095, 0.049 * cpScale),
				new LookupGridRow("j1-r1", j1, rpm1, 0.086, 0.057 * cpScale)
		);
	}

	private static List<LookupGridRow> missingNeighborRows(
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract
	) {
		return midRows(contract, 1.0).subList(0, 3);
	}

	private static LookupExecutionResult blocked(
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract,
			int observedNeighborRows,
			int minimumNeighborRowsRequired,
			double lowerAdvanceRatioJ,
			double lowerRpm,
			String status,
			String message
	) {
		Bracket bracket = new Bracket(false, lowerAdvanceRatioJ, lowerAdvanceRatioJ, lowerRpm, lowerRpm, 0.0, 0.0);
		return result(contract, bracket, observedNeighborRows, minimumNeighborRowsRequired,
				0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				contract.staticAnchorPreserved()
						? PropellerArchiveCtCpJLookupAcceptanceGate.MAX_STATIC_ANCHOR_ERROR + 1.0
						: 0.0,
				false, false, status, message);
	}

	private static LookupExecutionResult result(
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract,
			Bracket bracket,
			int observedNeighborRows,
			int minimumNeighborRowsRequired,
			double ct,
			double cp,
			double eta,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double etaResidual,
			double staticAnchorError,
			boolean insideLookupDomain,
			boolean accepted,
			String status,
			String message
	) {
		return new LookupExecutionResult(
				contract.presetName(),
				contract.caseName(),
				contract.queryAdvanceRatioJ(),
				contract.queryRpm(),
				bracket.lowerAdvanceRatioJ(),
				bracket.upperAdvanceRatioJ(),
				bracket.lowerRpm(),
				bracket.upperRpm(),
				bracket.advanceFraction(),
				bracket.rpmFraction(),
				observedNeighborRows,
				minimumNeighborRowsRequired,
				ct,
				cp,
				eta,
				maxCtShapeOvershoot,
				minCpCoefficient,
				etaResidual,
				staticAnchorError,
				false,
				0,
				0.0,
				0.0,
				insideLookupDomain,
				accepted,
				false,
				false,
				status,
				message
		);
	}

	private static LookupExecutionResult withArchiveCurveShapeDiagnostics(
			LookupExecutionResult result,
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary handoff,
			boolean inherited
	) {
		return new LookupExecutionResult(
				result.presetName(),
				result.caseName(),
				result.queryAdvanceRatioJ(),
				result.queryRpm(),
				result.lowerAdvanceRatioJ(),
				result.upperAdvanceRatioJ(),
				result.lowerRpm(),
				result.upperRpm(),
				result.advanceInterpolationFraction(),
				result.rpmInterpolationFraction(),
				result.observedNeighborRows(),
				result.minimumNeighborRowsRequired(),
				result.ctCoefficient(),
				result.cpCoefficient(),
				result.eta(),
				result.maxCtShapeOvershoot(),
				result.minCpCoefficient(),
				result.etaResidual(),
				result.staticAnchorError(),
				inherited,
				handoff.negativeThrustTailExecutionInputRowCount(),
				handoff.maxArchiveCurveEtaFormulaResidual(),
				handoff.maxArchiveCurveCtIncrease(),
				result.insideLookupDomain(),
				result.acceptedByLookupGate(),
				result.runtimeCouplingAllowed(),
				result.gameplayAutoApplyAllowed(),
				result.status(),
				result.message());
	}

	private static String guardFailureMessage(
			double overshoot,
			double minCp,
			double etaResidual,
			double staticAnchorError,
			boolean staticAnchorRequired
	) {
		if (overshoot > PropellerArchiveCtCpJLookupAcceptanceGate.MAX_CT_SHAPE_OVERSHOOT) {
			return "ct-shape-overshoot-guard-failed";
		}
		if (minCp < PropellerArchiveCtCpJLookupAcceptanceGate.MIN_CP_COEFFICIENT) {
			return "cp-positive-guard-failed";
		}
		if (etaResidual > PropellerArchiveCtCpJLookupAcceptanceGate.MAX_ETA_RESIDUAL) {
			return "eta-consistency-guard-failed";
		}
		if (staticAnchorRequired
				&& staticAnchorError > PropellerArchiveCtCpJLookupAcceptanceGate.MAX_STATIC_ANCHOR_ERROR) {
			return "static-anchor-guard-failed";
		}
		return "lookup-execution-acceptance-guard-failed";
	}

	private static Bracket bracket(List<LookupGridRow> rows, double queryJ, double queryRpm) {
		List<Double> jValues = distinctSortedAdvanceRatios(rows);
		List<Double> rpmValues = distinctSortedRpms(rows);
		Double lowerJ = lowerOrEqual(jValues, queryJ);
		Double upperJ = upperOrEqual(jValues, queryJ);
		Double lowerRpm = lowerOrEqual(rpmValues, queryRpm);
		Double upperRpm = upperOrEqual(rpmValues, queryRpm);
		if (lowerJ == null || upperJ == null || lowerRpm == null || upperRpm == null) {
			return new Bracket(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		}
		return new Bracket(true, lowerJ, upperJ, lowerRpm, upperRpm,
				fraction(queryJ, lowerJ, upperJ), fraction(queryRpm, lowerRpm, upperRpm));
	}

	private static List<Double> distinctSortedAdvanceRatios(List<LookupGridRow> rows) {
		List<Double> values = new ArrayList<>();
		for (LookupGridRow row : rows) {
			addDistinct(values, row.advanceRatioJ());
		}
		values.sort(Comparator.naturalOrder());
		return values;
	}

	private static List<Double> distinctSortedRpms(List<LookupGridRow> rows) {
		List<Double> values = new ArrayList<>();
		for (LookupGridRow row : rows) {
			addDistinct(values, row.rpm());
		}
		values.sort(Comparator.naturalOrder());
		return values;
	}

	private static void addDistinct(List<Double> values, double candidate) {
		for (double value : values) {
			if (same(value, candidate)) {
				return;
			}
		}
		values.add(candidate);
	}

	private static Double lowerOrEqual(List<Double> values, double query) {
		Double selected = null;
		for (double value : values) {
			if (value <= query + EPSILON) {
				selected = value;
			}
		}
		return selected;
	}

	private static Double upperOrEqual(List<Double> values, double query) {
		for (double value : values) {
			if (value >= query - EPSILON) {
				return value;
			}
		}
		return null;
	}

	private static List<LookupGridRow> neighborRows(List<LookupGridRow> rows, Bracket bracket) {
		List<LookupGridRow> neighbors = new ArrayList<>();
		addIfPresent(neighbors, findRow(rows, bracket.lowerAdvanceRatioJ(), bracket.lowerRpm()));
		addIfPresent(neighbors, findRow(rows, bracket.upperAdvanceRatioJ(), bracket.lowerRpm()));
		addIfPresent(neighbors, findRow(rows, bracket.lowerAdvanceRatioJ(), bracket.upperRpm()));
		addIfPresent(neighbors, findRow(rows, bracket.upperAdvanceRatioJ(), bracket.upperRpm()));
		return neighbors;
	}

	private static void addIfPresent(List<LookupGridRow> rows, LookupGridRow row) {
		if (row != null && rows.stream().noneMatch(existing -> existing == row)) {
			rows.add(row);
		}
	}

	private static int expectedNeighborRows(Bracket bracket) {
		int jCount = same(bracket.lowerAdvanceRatioJ(), bracket.upperAdvanceRatioJ()) ? 1 : 2;
		int rpmCount = same(bracket.lowerRpm(), bracket.upperRpm()) ? 1 : 2;
		return jCount * rpmCount;
	}

	private static LookupGridRow findRow(List<LookupGridRow> rows, double j, double rpm) {
		for (LookupGridRow row : rows) {
			if (same(row.advanceRatioJ(), j) && same(row.rpm(), rpm)) {
				return row;
			}
		}
		return null;
	}

	private static double interpolate(
			Bracket bracket,
			double j0r0,
			double j1r0,
			double j0r1,
			double j1r1
	) {
		double lowRpm = lerp(j0r0, j1r0, bracket.advanceFraction());
		double highRpm = lerp(j0r1, j1r1, bracket.advanceFraction());
		return lerp(lowRpm, highRpm, bracket.rpmFraction());
	}

	private static double staticAnchorError(
			List<LookupGridRow> rows,
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract,
			double ct
	) {
		if (!contract.staticAnchorPreserved()) {
			return 0.0;
		}
		LookupGridRow exact = findRow(rows, contract.queryAdvanceRatioJ(), contract.queryRpm());
		if (exact == null) {
			return PropellerArchiveCtCpJLookupAcceptanceGate.MAX_STATIC_ANCHOR_ERROR + 1.0;
		}
		return Math.abs(ct - exact.ctCoefficient());
	}

	private static double eta(double j, double ct, double cp) {
		if (j <= EPSILON || cp <= EPSILON) {
			return 0.0;
		}
		return j * ct / cp;
	}

	private static double lerp(double lower, double upper, double fraction) {
		return lower + (upper - lower) * fraction;
	}

	private static double fraction(double query, double lower, double upper) {
		if (same(lower, upper)) {
			return 0.0;
		}
		return (query - lower) / (upper - lower);
	}

	private static boolean same(double left, double right) {
		return Math.abs(left - right) <= EPSILON;
	}

	private static void validateRows(List<LookupGridRow> rows) {
		if (rows == null) {
			throw new IllegalArgumentException("lookup rows must not be null.");
		}
		for (int i = 0; i < rows.size(); i++) {
			LookupGridRow row = rows.get(i);
			if (row == null || row.rowId() == null || row.rowId().isBlank()) {
				throw new IllegalArgumentException("lookup rows must include stable row identifiers.");
			}
			if (!Double.isFinite(row.advanceRatioJ()) || row.advanceRatioJ() < 0.0) {
				throw new IllegalArgumentException("advanceRatioJ must be finite and nonnegative.");
			}
			if (!Double.isFinite(row.rpm()) || row.rpm() <= 0.0) {
				throw new IllegalArgumentException("rpm must be finite and positive.");
			}
			if (!Double.isFinite(row.ctCoefficient()) || row.ctCoefficient() < 0.0) {
				throw new IllegalArgumentException("ctCoefficient must be finite and nonnegative.");
			}
			if (!Double.isFinite(row.cpCoefficient()) || row.cpCoefficient() <= 0.0) {
				throw new IllegalArgumentException("cpCoefficient must be finite and positive.");
			}
			for (int j = i + 1; j < rows.size(); j++) {
				LookupGridRow other = rows.get(j);
				if (other != null
						&& same(row.advanceRatioJ(), other.advanceRatioJ())
						&& same(row.rpm(), other.rpm())) {
					throw new IllegalArgumentException("duplicate CT/CP/J grid coordinate: "
							+ row.advanceRatioJ() + " / " + row.rpm());
				}
			}
		}
	}

	private record Bracket(
			boolean inside,
			double lowerAdvanceRatioJ,
			double upperAdvanceRatioJ,
			double lowerRpm,
			double upperRpm,
			double advanceFraction,
			double rpmFraction
	) {
	}
}
