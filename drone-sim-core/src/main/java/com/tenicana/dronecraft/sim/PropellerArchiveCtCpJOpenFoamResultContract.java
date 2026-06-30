package com.tenicana.dronecraft.sim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveCtCpJOpenFoamResultContract {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-OpenFOAM-Result-Contract-Packet";
	public static final String CAVEAT =
			"OpenFOAM result contract accepts only compact external CT/CP/eta coefficient values and residual summaries for geometry-backed, shape-guarded lookup targets; it vendors no solver output and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int RESULT_FIELD_ROW_COUNT = 17;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 25;
	public static final int SUMMARY_ROW_COUNT = 17;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ RESULT_FIELD_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final int REQUIRED_RESULT_CHANNEL_COUNT = 3;
	public static final double MAX_QUERY_ADVANCE_RATIO_DELTA = 1.0e-9;
	public static final double MAX_QUERY_RPM_DELTA = 1.0e-6;
	public static final double MAX_SOLVER_CONVERGENCE_RESIDUAL = 1.0e-4;
	public static final double MAX_COEFFICIENT_RESIDUAL_DECLARATION_DELTA = 1.0e-9;

	private static final double EPSILON = 1.0e-12;

	private static final List<OpenFoamResultField> RESULT_FIELDS = List.of(
			new OpenFoamResultField("preset_name", "text", true,
					"lookup target join key", "join result to DroneConfig preset", false, false),
			new OpenFoamResultField("case_name", "text", true,
					"lookup target join key", "join result to static mid or high-domain case", false, false),
			new OpenFoamResultField("solver_family", "text", true,
					"external solver identity", "prove result came from the expected OpenFOAM setup", false, false),
			new OpenFoamResultField("source_case_sha256", "sha256", true,
					"external case archive hash", "prove exact offline case provenance", false, false),
			new OpenFoamResultField("mesh_geometry_id", "text", true,
					"reviewed blade geometry", "prove mesh input is geometry-backed", false, false),
			new OpenFoamResultField("query_advance_ratio_j", "J", true,
					"lookup query coordinate", "verify CFD run point", false, false),
			new OpenFoamResultField("query_rpm", "rpm", true,
					"lookup query coordinate", "verify CFD RPM bin", false, false),
			new OpenFoamResultField("reference_thrust_coefficient_ct", "coefficient", true,
					"reviewed wind-tunnel lookup", "derive CT residual", false, false),
			new OpenFoamResultField("cfd_thrust_coefficient_ct", "coefficient", true,
					"external OpenFOAM force extraction", "derive CT residual", false, false),
			new OpenFoamResultField("ct_residual_to_wind_tunnel", "ratio", true,
					"OpenFOAM versus reviewed wind-tunnel lookup", "gate thrust coefficient agreement", false, false),
			new OpenFoamResultField("reference_power_coefficient_cp", "coefficient", true,
					"reviewed wind-tunnel lookup", "derive CP residual", false, false),
			new OpenFoamResultField("cfd_power_coefficient_cp", "coefficient", true,
					"external OpenFOAM torque/power extraction", "derive CP residual", false, false),
			new OpenFoamResultField("cp_residual_to_wind_tunnel", "ratio", true,
					"OpenFOAM versus reviewed wind-tunnel lookup", "gate power coefficient agreement", false, false),
			new OpenFoamResultField("reference_efficiency_eta", "ratio", true,
					"reviewed wind-tunnel lookup", "derive eta residual", false, false),
			new OpenFoamResultField("cfd_efficiency_eta", "ratio", true,
					"external OpenFOAM CT/CP/J extraction", "derive eta residual", false, false),
			new OpenFoamResultField("eta_residual_to_wind_tunnel", "ratio", true,
					"OpenFOAM versus reviewed wind-tunnel lookup", "gate efficiency agreement", false, false),
			new OpenFoamResultField("solver_convergence_residual", "ratio", true,
					"external solver convergence summary", "reject non-converged CFD rows", false, false)
	);

	private PropellerArchiveCtCpJOpenFoamResultContract() {
	}

	public record OpenFoamResultField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record OpenFoamCompactResult(
			String presetName,
			String caseName,
			String solverFamily,
			String sourceCaseSha256,
			String meshGeometryId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double referenceThrustCoefficientCt,
			double cfdThrustCoefficientCt,
			double referencePowerCoefficientCp,
			double cfdPowerCoefficientCp,
			double referenceEfficiencyEta,
			double cfdEfficiencyEta,
			int resultChannelCount,
			double ctResidualToWindTunnel,
			double cpResidualToWindTunnel,
			double etaResidualToWindTunnel,
			double solverConvergenceResidual,
			boolean passed,
			String status
	) {
	}

	public record OpenFoamResultContractSummary(
			boolean reviewedArchiveImportReady,
			boolean externalCaseTemplateReady,
			boolean resultExtractionReady,
			boolean lookupExecutionArchiveCurveShapeGuardReady,
			int lookupExecutionArchiveCurveShapeGuardInheritedScenarioCount,
			int lookupExecutionArchiveCurveShapeGuardBlockedScenarioCount,
			int maxNegativeThrustTailExecutionInputRowCount,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
			int expectedValidationCaseCount,
			int expectedOpenFoamResultCaseCount,
			int observedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			int passedResultCount,
			int failedResultCount,
			int minObservedResultChannelCount,
			double maxCtResidualToWindTunnel,
			double maxCpResidualToWindTunnel,
			double maxEtaResidualToWindTunnel,
			double maxSolverConvergenceResidual,
			boolean allExpectedResultsPresent,
			boolean allObservedResultsPassed,
			boolean openFoamResultContractReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record OpenFoamResultContractScenario(
			String scenarioName,
			OpenFoamResultContractSummary summary
	) {
	}

	public record OpenFoamResultContractExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxExpectedOpenFoamResultCaseCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			int lookupExecutionArchiveCurveShapeGuardReadyScenarioCount,
			int maxLookupExecutionArchiveCurveShapeGuardInheritedScenarioCount,
			int maxLookupExecutionArchiveCurveShapeGuardBlockedScenarioCount,
			int maxNegativeThrustTailExecutionInputRowCount,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
			double maxCtResidualToWindTunnel,
			double maxCpResidualToWindTunnel,
			double maxEtaResidualToWindTunnel,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJOpenFoamResultContractAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int resultFieldRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamResultField> fields,
			List<OpenFoamResultContractScenario> scenarios,
			OpenFoamResultContractExtrema extrema
	) {
		public CtCpJOpenFoamResultContractAudit {
			fields = List.copyOf(fields);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamResultContractAudit audit() {
		List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> targets =
				openFoamResultTargets();
		List<OpenFoamCompactResult> passingResults = passingResults(targets);
		List<OpenFoamCompactResult> failedResults = new ArrayList<>(passingResults);
		failedResults.set(0, failingResult(targets.get(0)));
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionSummary lookupExecution =
				PropellerArchiveCtCpJLookupExecutionContract.audit().summary();
		List<OpenFoamResultContractScenario> scenarios = List.of(
				new OpenFoamResultContractScenario(
						"current_no_reviewed_import_no_openfoam_results",
						review(false, false, false, List.of(), lookupExecution,
								"current-openfoam-result-contract-blocked")),
				new OpenFoamResultContractScenario(
						"reviewed_import_openfoam_ready_results_missing",
						review(true, true, true, List.of(), lookupExecution,
								"synthetic-openfoam-results-missing")),
				new OpenFoamResultContractScenario(
						"synthetic_openfoam_results_all_pass",
						review(true, true, true, passingResults, lookupExecution,
								"synthetic-openfoam-results-all-pass")),
				new OpenFoamResultContractScenario(
						"synthetic_openfoam_result_failed",
						review(true, true, true, failedResults, lookupExecution,
								"synthetic-openfoam-result-failed"))
		);
		return new CtCpJOpenFoamResultContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				RESULT_FIELD_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RESULT_FIELDS,
				scenarios,
				extrema(scenarios)
		);
	}

	public static OpenFoamResultField field(String fieldName) {
		return RESULT_FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM result field: " + fieldName));
	}

	public static OpenFoamCompactResult result(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			String sourceCaseSha256,
			double queryAdvanceRatioJ,
			double queryRpm,
			double referenceThrustCoefficientCt,
			double cfdThrustCoefficientCt,
			double referencePowerCoefficientCp,
			double cfdPowerCoefficientCp,
			double referenceEfficiencyEta,
			double cfdEfficiencyEta,
			int resultChannelCount,
			double ctResidualToWindTunnel,
			double cpResidualToWindTunnel,
			double etaResidualToWindTunnel,
			double solverConvergenceResidual
	) {
		if (target == null) {
			throw new IllegalArgumentException("OpenFOAM validation target must not be null.");
		}
		if (!target.postReviewOpenFoamCaseRunnable()) {
			throw new IllegalArgumentException("OpenFOAM result target must be geometry-backed and runnable.");
		}
		validateSourceCaseSha256(sourceCaseSha256);
		validateQueryCoordinate("queryAdvanceRatioJ", queryAdvanceRatioJ);
		validateQueryCoordinate("queryRpm", queryRpm);
		validateCoefficientChannel("referenceThrustCoefficientCt", referenceThrustCoefficientCt);
		validateCoefficientChannel("cfdThrustCoefficientCt", cfdThrustCoefficientCt);
		validateCoefficientChannel("referencePowerCoefficientCp", referencePowerCoefficientCp);
		validateCoefficientChannel("cfdPowerCoefficientCp", cfdPowerCoefficientCp);
		validateCoefficientChannel("referenceEfficiencyEta", referenceEfficiencyEta);
		validateCoefficientChannel("cfdEfficiencyEta", cfdEfficiencyEta);
		if (resultChannelCount < 0) {
			throw new IllegalArgumentException("resultChannelCount must be nonnegative.");
		}
		if (!Double.isFinite(ctResidualToWindTunnel) || ctResidualToWindTunnel < 0.0) {
			throw new IllegalArgumentException("ctResidualToWindTunnel must be finite and nonnegative.");
		}
		if (!Double.isFinite(cpResidualToWindTunnel) || cpResidualToWindTunnel < 0.0) {
			throw new IllegalArgumentException("cpResidualToWindTunnel must be finite and nonnegative.");
		}
		if (!Double.isFinite(etaResidualToWindTunnel) || etaResidualToWindTunnel < 0.0) {
			throw new IllegalArgumentException("etaResidualToWindTunnel must be finite and nonnegative.");
		}
		if (!Double.isFinite(solverConvergenceResidual) || solverConvergenceResidual < 0.0) {
			throw new IllegalArgumentException("solverConvergenceResidual must be finite and nonnegative.");
		}
		validateResidualConsistency("ctResidualToWindTunnel", referenceThrustCoefficientCt,
				cfdThrustCoefficientCt, ctResidualToWindTunnel);
		validateResidualConsistency("cpResidualToWindTunnel", referencePowerCoefficientCp,
				cfdPowerCoefficientCp, cpResidualToWindTunnel);
		validateResidualConsistency("etaResidualToWindTunnel", referenceEfficiencyEta,
				cfdEfficiencyEta, etaResidualToWindTunnel);
		boolean passed = passes(target, queryAdvanceRatioJ, queryRpm, referenceThrustCoefficientCt,
				cfdThrustCoefficientCt, referencePowerCoefficientCp, cfdPowerCoefficientCp,
				referenceEfficiencyEta, cfdEfficiencyEta, resultChannelCount, ctResidualToWindTunnel,
				cpResidualToWindTunnel, etaResidualToWindTunnel, solverConvergenceResidual);
		return new OpenFoamCompactResult(
				target.presetName(),
				target.caseName(),
				target.solverFamily(),
				sourceCaseSha256,
				target.geometryMatchId(),
				queryAdvanceRatioJ,
				queryRpm,
				referenceThrustCoefficientCt,
				cfdThrustCoefficientCt,
				referencePowerCoefficientCp,
				cfdPowerCoefficientCp,
				referenceEfficiencyEta,
				cfdEfficiencyEta,
				resultChannelCount,
				ctResidualToWindTunnel,
				cpResidualToWindTunnel,
				etaResidualToWindTunnel,
				solverConvergenceResidual,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static OpenFoamResultContractSummary review(
			boolean reviewedArchiveImportReady,
			boolean externalCaseTemplateReady,
			boolean resultExtractionReady,
			List<OpenFoamCompactResult> results,
			String sourceRuntimeInfo
	) {
		return review(reviewedArchiveImportReady, externalCaseTemplateReady, resultExtractionReady,
				results, PropellerArchiveCtCpJLookupExecutionContract.audit().summary(), sourceRuntimeInfo);
	}

	public static OpenFoamResultContractSummary review(
			boolean reviewedArchiveImportReady,
			boolean externalCaseTemplateReady,
			boolean resultExtractionReady,
			List<OpenFoamCompactResult> results,
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionSummary lookupExecution,
			String sourceRuntimeInfo
	) {
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		if (lookupExecution == null) {
			throw new IllegalArgumentException("lookupExecution must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> targets =
				openFoamResultTargets();
		Set<String> targetKeys = new HashSet<>();
		for (PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target : targets) {
			targetKeys.add(key(target.presetName(), target.caseName()));
		}
		Set<String> resultKeys = new HashSet<>();
		for (OpenFoamCompactResult result : results) {
			if (result == null || result.presetName() == null || result.caseName() == null
					|| result.presetName().isBlank() || result.caseName().isBlank()) {
				throw new IllegalArgumentException("results must include stable preset and case names.");
			}
			String key = key(result.presetName(), result.caseName());
			if (!resultKeys.add(key)) {
				throw new IllegalArgumentException("duplicate OpenFOAM compact result: " + key);
			}
		}

		int missing = 0;
		int passed = 0;
		int failed = 0;
		for (PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target : targets) {
			OpenFoamCompactResult result = findResult(results, target.presetName(), target.caseName());
			if (result == null) {
				missing++;
				continue;
			}
			if (passes(target, result)) {
				passed++;
			} else {
				failed++;
			}
		}
		int unexpected = 0;
		int minChannels = results.isEmpty() ? 0 : Integer.MAX_VALUE;
		double maxCt = 0.0;
		double maxCp = 0.0;
		double maxEta = 0.0;
		double maxConvergence = 0.0;
		for (OpenFoamCompactResult result : results) {
			if (!targetKeys.contains(key(result.presetName(), result.caseName()))) {
				unexpected++;
			}
			minChannels = Math.min(minChannels, result.resultChannelCount());
			maxCt = Math.max(maxCt, result.ctResidualToWindTunnel());
			maxCp = Math.max(maxCp, result.cpResidualToWindTunnel());
			maxEta = Math.max(maxEta, result.etaResidualToWindTunnel());
			maxConvergence = Math.max(maxConvergence, result.solverConvergenceResidual());
		}
		boolean allPresent = missing == 0 && unexpected == 0 && results.size() == targets.size();
		boolean allPassed = failed == 0 && passed == targets.size();
		boolean shapeGuardReady = reviewedArchiveImportReady
				&& lookupExecutionArchiveCurveShapeGuardReady(lookupExecution);
		boolean ready = reviewedArchiveImportReady
				&& externalCaseTemplateReady
				&& resultExtractionReady
				&& shapeGuardReady
				&& allPresent
				&& allPassed;
		return new OpenFoamResultContractSummary(
				reviewedArchiveImportReady,
				externalCaseTemplateReady,
				resultExtractionReady,
				shapeGuardReady,
				lookupExecution.archiveCurveShapeGuardInheritedScenarioCount(),
				lookupExecution.archiveCurveShapeGuardBlockedScenarioCount(),
				lookupExecution.maxNegativeThrustTailExecutionInputRowCount(),
				lookupExecution.maxArchiveCurveEtaFormulaResidual(),
				lookupExecution.maxArchiveCurveCtIncrease(),
				PropellerArchiveCtCpJOpenFoamValidationPlan.audit().summary().validationCaseCount(),
				targets.size(),
				results.size(),
				missing,
				unexpected,
				passed,
				failed,
				minChannels == Integer.MAX_VALUE ? 0 : minChannels,
				maxCt,
				maxCp,
				maxEta,
				maxConvergence,
				allPresent,
				allPassed,
				ready,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(reviewedArchiveImportReady, externalCaseTemplateReady, resultExtractionReady,
						shapeGuardReady, allPresent, allPassed),
				sourceRuntimeInfo
		);
	}

	private static boolean lookupExecutionArchiveCurveShapeGuardReady(
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionSummary lookupExecution
	) {
		return lookupExecution.acceptedScenarioCount() > 0
				&& lookupExecution.archiveCurveShapeGuardInheritedScenarioCount() > 0
				&& lookupExecution.maxArchiveCurveEtaFormulaResidual()
						<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL
				&& lookupExecution.maxArchiveCurveCtIncrease()
						<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE;
	}

	private static List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase>
			openFoamResultTargets() {
		return PropellerArchiveCtCpJOpenFoamValidationPlan.audit()
				.cases()
				.stream()
				.filter(PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase
						::postReviewOpenFoamCaseRunnable)
				.toList();
	}

	private static List<OpenFoamCompactResult> passingResults(
			List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> targets
	) {
		return targets.stream()
				.map(PropellerArchiveCtCpJOpenFoamResultContract::passingResult)
				.toList();
	}

	private static OpenFoamCompactResult passingResult(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target
	) {
		return resultWithSyntheticValues(
				target,
				target.maxCtResidual() * 0.50,
				target.maxCpResidual() * 0.50,
				target.staticAnchorCase() ? 0.0 : target.maxEtaResidual() * 0.50,
				MAX_SOLVER_CONVERGENCE_RESIDUAL * 0.50);
	}

	private static OpenFoamCompactResult failingResult(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target
	) {
		return resultWithSyntheticValues(
				target,
				target.maxCtResidual() * 1.25,
				target.maxCpResidual() * 0.50,
				target.staticAnchorCase() ? 0.0 : target.maxEtaResidual() * 0.50,
				MAX_SOLVER_CONVERGENCE_RESIDUAL * 0.50);
	}

	private static OpenFoamCompactResult resultWithSyntheticValues(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			double ctResidualToWindTunnel,
			double cpResidualToWindTunnel,
			double etaResidualToWindTunnel,
			double solverConvergenceResidual
	) {
		CoefficientReferenceValues reference = syntheticReferenceValues(target);
		return result(target,
				syntheticCaseSha256(target),
				target.queryAdvanceRatioJ(),
				target.queryRpm(),
				reference.thrustCoefficientCt(),
				cfdValue(reference.thrustCoefficientCt(), ctResidualToWindTunnel),
				reference.powerCoefficientCp(),
				cfdValue(reference.powerCoefficientCp(), cpResidualToWindTunnel),
				reference.efficiencyEta(),
				cfdValue(reference.efficiencyEta(), etaResidualToWindTunnel),
				REQUIRED_RESULT_CHANNEL_COUNT,
				ctResidualToWindTunnel,
				cpResidualToWindTunnel,
				etaResidualToWindTunnel,
				solverConvergenceResidual);
	}

	private static boolean passes(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			OpenFoamCompactResult result
	) {
		return result.solverFamily().equals(target.solverFamily())
				&& isSha256Hex(result.sourceCaseSha256())
				&& result.meshGeometryId().equals(target.geometryMatchId())
				&& passes(target, result.queryAdvanceRatioJ(), result.queryRpm(),
						result.referenceThrustCoefficientCt(), result.cfdThrustCoefficientCt(),
						result.referencePowerCoefficientCp(), result.cfdPowerCoefficientCp(),
						result.referenceEfficiencyEta(), result.cfdEfficiencyEta(), result.resultChannelCount(),
						result.ctResidualToWindTunnel(), result.cpResidualToWindTunnel(),
						result.etaResidualToWindTunnel(), result.solverConvergenceResidual());
	}

	private static boolean passes(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			double queryAdvanceRatioJ,
			double queryRpm,
			double referenceThrustCoefficientCt,
			double cfdThrustCoefficientCt,
			double referencePowerCoefficientCp,
			double cfdPowerCoefficientCp,
			double referenceEfficiencyEta,
			double cfdEfficiencyEta,
			int resultChannelCount,
			double ctResidualToWindTunnel,
			double cpResidualToWindTunnel,
			double etaResidualToWindTunnel,
			double solverConvergenceResidual
	) {
		return matchesTargetQuery(target, queryAdvanceRatioJ, queryRpm)
				&& residualsMatch(referenceThrustCoefficientCt, cfdThrustCoefficientCt, ctResidualToWindTunnel,
						referencePowerCoefficientCp, cfdPowerCoefficientCp, cpResidualToWindTunnel,
						referenceEfficiencyEta, cfdEfficiencyEta, etaResidualToWindTunnel)
				&& resultChannelCount >= REQUIRED_RESULT_CHANNEL_COUNT
				&& ctResidualToWindTunnel <= target.maxCtResidual()
				&& cpResidualToWindTunnel <= target.maxCpResidual()
				&& etaResidualToWindTunnel <= target.maxEtaResidual()
				&& solverConvergenceResidual <= MAX_SOLVER_CONVERGENCE_RESIDUAL;
	}

	private static OpenFoamCompactResult findResult(
			List<OpenFoamCompactResult> results,
			String presetName,
			String caseName
	) {
		return results.stream()
				.filter(result -> result.presetName().equals(presetName) && result.caseName().equals(caseName))
				.findFirst()
				.orElse(null);
	}

	private record CoefficientReferenceValues(
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double efficiencyEta
	) {
	}

	private static OpenFoamResultContractExtrema extrema(List<OpenFoamResultContractScenario> scenarios) {
		int ready = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int shapeReady = 0;
		int maxShapeInherited = 0;
		int maxShapeBlocked = 0;
		int maxNegativeTail = 0;
		double maxArchiveEta = 0.0;
		double maxArchiveCt = 0.0;
		double maxCt = 0.0;
		double maxCp = 0.0;
		double maxEta = 0.0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamResultContractScenario scenario : scenarios) {
			OpenFoamResultContractSummary summary = scenario.summary();
			if (summary.openFoamResultContractReady()) {
				ready++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedOpenFoamResultCaseCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxFailed = Math.max(maxFailed, summary.failedResultCount());
			if (summary.lookupExecutionArchiveCurveShapeGuardReady()) {
				shapeReady++;
			}
			maxShapeInherited = Math.max(maxShapeInherited,
					summary.lookupExecutionArchiveCurveShapeGuardInheritedScenarioCount());
			maxShapeBlocked = Math.max(maxShapeBlocked,
					summary.lookupExecutionArchiveCurveShapeGuardBlockedScenarioCount());
			maxNegativeTail = Math.max(maxNegativeTail,
					summary.maxNegativeThrustTailExecutionInputRowCount());
			maxArchiveEta = Math.max(maxArchiveEta, summary.maxArchiveCurveEtaFormulaResidual());
			maxArchiveCt = Math.max(maxArchiveCt, summary.maxArchiveCurveCtIncrease());
			maxCt = Math.max(maxCt, summary.maxCtResidualToWindTunnel());
			maxCp = Math.max(maxCp, summary.maxCpResidualToWindTunnel());
			maxEta = Math.max(maxEta, summary.maxEtaResidualToWindTunnel());
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new OpenFoamResultContractExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxExpected,
				maxMissing,
				maxFailed,
				shapeReady,
				maxShapeInherited,
				maxShapeBlocked,
				maxNegativeTail,
				maxArchiveEta,
				maxArchiveCt,
				maxCt,
				maxCp,
				maxEta,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			boolean reviewedArchiveImportReady,
			boolean externalCaseTemplateReady,
			boolean resultExtractionReady,
			boolean lookupExecutionArchiveCurveShapeGuardReady,
			boolean allPresent,
			boolean allPassed
	) {
		if (!reviewedArchiveImportReady || !externalCaseTemplateReady || !resultExtractionReady) {
			return "openfoam-prerequisites-not-ready";
		}
		if (!lookupExecutionArchiveCurveShapeGuardReady) {
			return "lookup-execution-archive-curve-shape-guard-not-ready";
		}
		if (!allPresent) {
			return "openfoam-results-missing";
		}
		if (!allPassed) {
			return "openfoam-residual-gate-failed";
		}
		return "openfoam-result-contract-ready";
	}

	private static void validateSourceCaseSha256(String sourceCaseSha256) {
		if (!isSha256Hex(sourceCaseSha256)) {
			throw new IllegalArgumentException("sourceCaseSha256 must be a 64-character lowercase SHA-256 hex string.");
		}
	}

	private static void validateQueryCoordinate(String fieldName, double value) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(fieldName + " must be finite and nonnegative.");
		}
	}

	private static void validateCoefficientChannel(String fieldName, double value) {
		if (!coefficientChannel(value)) {
			throw new IllegalArgumentException(fieldName + " must be finite and nonnegative.");
		}
	}

	private static boolean coefficientChannel(double value) {
		return Double.isFinite(value) && value >= 0.0;
	}

	private static void validateResidualConsistency(String fieldName, double reference, double cfd, double residual) {
		if (!residualMatches(reference, cfd, residual)) {
			throw new IllegalArgumentException(fieldName + " must match the supplied reference and CFD coefficient values.");
		}
	}

	private static boolean isSha256Hex(String value) {
		if (value == null || value.length() != 64) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesTargetQuery(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			double queryAdvanceRatioJ,
			double queryRpm
	) {
		return Math.abs(queryAdvanceRatioJ - target.queryAdvanceRatioJ()) <= MAX_QUERY_ADVANCE_RATIO_DELTA
				&& Math.abs(queryRpm - target.queryRpm()) <= MAX_QUERY_RPM_DELTA;
	}

	private static CoefficientReferenceValues syntheticReferenceValues(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target
	) {
		double ct = target.staticAnchorCase()
				? 0.120
				: Math.max(0.050, 0.110 - target.queryAdvanceRatioJ() * 0.035);
		double cp = target.staticAnchorCase()
				? 0.040
				: Math.max(0.035, 0.045 + target.queryAdvanceRatioJ() * 0.010);
		double eta = target.queryAdvanceRatioJ() <= EPSILON || cp <= EPSILON
				? 0.0
				: target.queryAdvanceRatioJ() * ct / cp;
		return new CoefficientReferenceValues(ct, cp, eta);
	}

	private static double cfdValue(double reference, double residual) {
		return reference * (1.0 + residual);
	}

	private static boolean residualsMatch(
			double referenceCt,
			double cfdCt,
			double ctResidualToWindTunnel,
			double referenceCp,
			double cfdCp,
			double cpResidualToWindTunnel,
			double referenceEta,
			double cfdEta,
			double etaResidualToWindTunnel
	) {
		return residualMatches(referenceCt, cfdCt, ctResidualToWindTunnel)
				&& residualMatches(referenceCp, cfdCp, cpResidualToWindTunnel)
				&& residualMatches(referenceEta, cfdEta, etaResidualToWindTunnel);
	}

	private static boolean residualMatches(double reference, double cfd, double declaredResidual) {
		return coefficientChannel(reference)
				&& coefficientChannel(cfd)
				&& Double.isFinite(declaredResidual)
				&& declaredResidual >= 0.0
				&& Math.abs(actualResidual(reference, cfd) - declaredResidual)
						<= MAX_COEFFICIENT_RESIDUAL_DECLARATION_DELTA;
	}

	private static double actualResidual(double reference, double cfd) {
		return Math.abs(cfd - reference) / Math.max(Math.abs(reference), EPSILON);
	}

	private static String syntheticCaseSha256(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target
	) {
		return sha256("synthetic-openfoam-coefficient-case:" + key(target.presetName(), target.caseName()));
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(64);
			for (byte b : bytes) {
				String hex = Integer.toHexString(b & 0xff);
				if (hex.length() == 1) {
					builder.append('0');
				}
				builder.append(hex);
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 digest is not available.", e);
		}
	}

	private static String key(String presetName, String caseName) {
		return presetName + "/" + caseName;
	}
}
