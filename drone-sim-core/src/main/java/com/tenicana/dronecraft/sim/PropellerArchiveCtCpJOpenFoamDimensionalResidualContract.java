package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveCtCpJOpenFoamDimensionalResidualContract {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Residual-Contract-Packet";
	public static final String CAVEAT =
			"OpenFOAM dimensional residual contract accepts only compact external SI thrust, shaft-power, torque, induced-velocity, and momentum-power residual summaries for geometry-backed CT/CP/J targets; it vendors no solver output and cannot mutate runtime physics or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int RESULT_FIELD_ROW_COUNT = 15;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ RESULT_FIELD_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final int REQUIRED_DIMENSIONAL_RESULT_CHANNEL_COUNT = 5;
	public static final double MAX_THRUST_RESIDUAL_TO_REFERENCE = 0.08;
	public static final double MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE = 0.10;
	public static final double MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE = 0.10;
	public static final double MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE = 0.06;
	public static final double MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE = 0.12;
	public static final double MAX_SOLVER_CONVERGENCE_RESIDUAL =
			PropellerArchiveCtCpJOpenFoamResultContract.MAX_SOLVER_CONVERGENCE_RESIDUAL;

	private static final List<OpenFoamDimensionalResultField> RESULT_FIELDS = List.of(
			new OpenFoamDimensionalResultField("preset_name", "text", true,
					"lookup target join key", "join dimensional result to DroneConfig preset", false, false),
			new OpenFoamDimensionalResultField("case_name", "text", true,
					"lookup target join key", "join dimensional result to static mid or high-domain case", false, false),
			new OpenFoamDimensionalResultField("solver_family", "text", true,
					"external solver identity", "prove result came from the expected OpenFOAM setup", false, false),
			new OpenFoamDimensionalResultField("source_case_sha256", "sha256", true,
					"external case archive hash", "prove exact offline case provenance", false, false),
			new OpenFoamDimensionalResultField("mesh_geometry_id", "text", true,
					"reviewed blade geometry", "prove mesh input is geometry-backed", false, false),
			new OpenFoamDimensionalResultField("query_advance_ratio_j", "J", true,
					"lookup query coordinate", "verify CFD run point", false, false),
			new OpenFoamDimensionalResultField("query_rpm", "rpm", true,
					"lookup query coordinate", "verify CFD RPM bin", false, false),
			new OpenFoamDimensionalResultField("reference_thrust_newtons", "N", true,
					"dimensional rotor response", "derive thrust residual", false, false),
			new OpenFoamDimensionalResultField("cfd_thrust_newtons", "N", true,
					"external OpenFOAM force extraction", "derive thrust residual", false, false),
			new OpenFoamDimensionalResultField("thrust_residual_to_reference", "ratio", true,
					"OpenFOAM versus dimensional CT/CP/J reference", "gate SI thrust agreement", false, false),
			new OpenFoamDimensionalResultField("shaft_power_residual_to_reference", "ratio", true,
					"OpenFOAM versus dimensional CT/CP/J reference", "gate shaft-power agreement", false, false),
			new OpenFoamDimensionalResultField("shaft_torque_residual_to_reference", "ratio", true,
					"OpenFOAM versus dimensional CT/CP/J reference", "gate reaction-torque agreement", false, false),
			new OpenFoamDimensionalResultField("induced_velocity_residual_to_reference", "ratio", true,
					"OpenFOAM wake extraction versus dimensional reference", "gate induced wake agreement", false, false),
			new OpenFoamDimensionalResultField("momentum_power_residual_to_reference", "ratio", true,
					"OpenFOAM wake power versus dimensional reference", "gate momentum-energy closure", false, false),
			new OpenFoamDimensionalResultField("solver_convergence_residual", "ratio", true,
					"external solver convergence summary", "reject non-converged dimensional CFD rows", false, false)
	);

	private PropellerArchiveCtCpJOpenFoamDimensionalResidualContract() {
	}

	public record OpenFoamDimensionalResultField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record OpenFoamDimensionalCompactResult(
			String presetName,
			String caseName,
			String solverFamily,
			String meshGeometryId,
			int resultChannelCount,
			double thrustResidualToReference,
			double shaftPowerResidualToReference,
			double shaftTorqueResidualToReference,
			double inducedVelocityResidualToReference,
			double momentumPowerResidualToReference,
			double solverConvergenceResidual,
			boolean passed,
			String status
	) {
	}

	public record OpenFoamDimensionalResidualSummary(
			boolean dimensionalResponseReferenceReady,
			boolean openFoamCoefficientResultReady,
			boolean externalDimensionalExtractionReady,
			int expectedDimensionalReferenceCount,
			int readyDimensionalReferenceCount,
			int expectedOpenFoamDimensionalResultCaseCount,
			int observedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			int passedResultCount,
			int failedResultCount,
			int minObservedResultChannelCount,
			double maxThrustResidualToReference,
			double maxShaftPowerResidualToReference,
			double maxShaftTorqueResidualToReference,
			double maxInducedVelocityResidualToReference,
			double maxMomentumPowerResidualToReference,
			double maxSolverConvergenceResidual,
			boolean allExpectedResultsPresent,
			boolean allObservedResultsPassed,
			boolean openFoamDimensionalResidualReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record OpenFoamDimensionalResidualScenario(
			String scenarioName,
			OpenFoamDimensionalResidualSummary summary
	) {
	}

	public record OpenFoamDimensionalResidualExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxExpectedOpenFoamDimensionalResultCaseCount,
			int maxReadyDimensionalReferenceCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			double maxThrustResidualToReference,
			double maxShaftPowerResidualToReference,
			double maxShaftTorqueResidualToReference,
			double maxInducedVelocityResidualToReference,
			double maxMomentumPowerResidualToReference,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJOpenFoamDimensionalResidualContractAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int resultFieldRowCount,
			int scenarioSampleCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamDimensionalResultField> fields,
			List<OpenFoamDimensionalResidualScenario> scenarios,
			OpenFoamDimensionalResidualExtrema extrema
	) {
		public CtCpJOpenFoamDimensionalResidualContractAudit {
			fields = List.copyOf(fields);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamDimensionalResidualContractAudit audit() {
		List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> targets =
				openFoamDimensionalTargets();
		List<OpenFoamDimensionalCompactResult> passingResults = passingResults(targets);
		List<OpenFoamDimensionalCompactResult> failedResults = new ArrayList<>(passingResults);
		failedResults.set(1, failingResult(targets.get(1)));
		int currentDimensionalReadyCount =
				PropellerArchiveCtCpJDimensionalRotorResponse.audit().summary().readyScenarioCount();
		int expectedCount = targets.size();
		List<OpenFoamDimensionalResidualScenario> scenarios = List.of(
				new OpenFoamDimensionalResidualScenario(
						"current_dimensional_and_openfoam_blocked",
						review(false, currentDimensionalReadyCount, false, false, List.of(),
								"current-openfoam-dimensional-residual-blocked")),
				new OpenFoamDimensionalResidualScenario(
						"dimensional_reference_ready_openfoam_si_missing",
						review(true, expectedCount, true, true, List.of(),
								"synthetic-dimensional-reference-ready-openfoam-si-missing")),
				new OpenFoamDimensionalResidualScenario(
						"openfoam_si_ready_dimensional_reference_missing",
						review(false, currentDimensionalReadyCount, true, true, passingResults,
								"synthetic-openfoam-si-ready-dimensional-reference-missing")),
				new OpenFoamDimensionalResidualScenario(
						"dimensional_and_openfoam_si_residual_failed",
						review(true, expectedCount, true, true, failedResults,
								"synthetic-openfoam-dimensional-residual-failed")),
				new OpenFoamDimensionalResidualScenario(
						"dimensional_and_openfoam_si_ready",
						review(true, expectedCount, true, true, passingResults,
								"synthetic-openfoam-dimensional-residual-all-pass"))
		);
		return new CtCpJOpenFoamDimensionalResidualContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				RESULT_FIELD_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RESULT_FIELDS,
				scenarios,
				extrema(scenarios)
		);
	}

	public static OpenFoamDimensionalResultField field(String fieldName) {
		return RESULT_FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM dimensional result field: " + fieldName));
	}

	public static OpenFoamDimensionalCompactResult result(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			int resultChannelCount,
			double thrustResidualToReference,
			double shaftPowerResidualToReference,
			double shaftTorqueResidualToReference,
			double inducedVelocityResidualToReference,
			double momentumPowerResidualToReference,
			double solverConvergenceResidual
	) {
		if (target == null) {
			throw new IllegalArgumentException("OpenFOAM validation target must not be null.");
		}
		if (!target.postReviewOpenFoamCaseRunnable()) {
			throw new IllegalArgumentException("OpenFOAM dimensional result target must be geometry-backed and runnable.");
		}
		if (resultChannelCount < 0) {
			throw new IllegalArgumentException("resultChannelCount must be nonnegative.");
		}
		validateResidual("thrustResidualToReference", thrustResidualToReference);
		validateResidual("shaftPowerResidualToReference", shaftPowerResidualToReference);
		validateResidual("shaftTorqueResidualToReference", shaftTorqueResidualToReference);
		validateResidual("inducedVelocityResidualToReference", inducedVelocityResidualToReference);
		validateResidual("momentumPowerResidualToReference", momentumPowerResidualToReference);
		validateResidual("solverConvergenceResidual", solverConvergenceResidual);
		boolean passed = passes(resultChannelCount, thrustResidualToReference,
				shaftPowerResidualToReference, shaftTorqueResidualToReference,
				inducedVelocityResidualToReference, momentumPowerResidualToReference,
				solverConvergenceResidual);
		return new OpenFoamDimensionalCompactResult(
				target.presetName(),
				target.caseName(),
				target.solverFamily(),
				target.geometryMatchId(),
				resultChannelCount,
				thrustResidualToReference,
				shaftPowerResidualToReference,
				shaftTorqueResidualToReference,
				inducedVelocityResidualToReference,
				momentumPowerResidualToReference,
				solverConvergenceResidual,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static OpenFoamDimensionalResidualSummary review(
			boolean dimensionalResponseReferenceReady,
			int readyDimensionalReferenceCount,
			boolean openFoamCoefficientResultReady,
			boolean externalDimensionalExtractionReady,
			List<OpenFoamDimensionalCompactResult> results,
			String sourceRuntimeInfo
	) {
		if (readyDimensionalReferenceCount < 0) {
			throw new IllegalArgumentException("readyDimensionalReferenceCount must be nonnegative.");
		}
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> targets =
				openFoamDimensionalTargets();
		Set<String> targetKeys = new HashSet<>();
		for (PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target : targets) {
			targetKeys.add(key(target.presetName(), target.caseName()));
		}
		Set<String> resultKeys = new HashSet<>();
		for (OpenFoamDimensionalCompactResult result : results) {
			if (result == null || result.presetName() == null || result.caseName() == null
					|| result.presetName().isBlank() || result.caseName().isBlank()) {
				throw new IllegalArgumentException("results must include stable preset and case names.");
			}
			String key = key(result.presetName(), result.caseName());
			if (!resultKeys.add(key)) {
				throw new IllegalArgumentException("duplicate OpenFOAM dimensional compact result: " + key);
			}
		}

		int missing = 0;
		int passed = 0;
		int failed = 0;
		for (PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target : targets) {
			OpenFoamDimensionalCompactResult result = findResult(results, target.presetName(), target.caseName());
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
		double maxThrust = 0.0;
		double maxPower = 0.0;
		double maxTorque = 0.0;
		double maxInduced = 0.0;
		double maxMomentum = 0.0;
		double maxConvergence = 0.0;
		for (OpenFoamDimensionalCompactResult result : results) {
			if (!targetKeys.contains(key(result.presetName(), result.caseName()))) {
				unexpected++;
			}
			minChannels = Math.min(minChannels, result.resultChannelCount());
			maxThrust = Math.max(maxThrust, result.thrustResidualToReference());
			maxPower = Math.max(maxPower, result.shaftPowerResidualToReference());
			maxTorque = Math.max(maxTorque, result.shaftTorqueResidualToReference());
			maxInduced = Math.max(maxInduced, result.inducedVelocityResidualToReference());
			maxMomentum = Math.max(maxMomentum, result.momentumPowerResidualToReference());
			maxConvergence = Math.max(maxConvergence, result.solverConvergenceResidual());
		}
		int expectedCount = targets.size();
		boolean dimensionalReady = dimensionalResponseReferenceReady
				&& readyDimensionalReferenceCount >= expectedCount;
		boolean allPresent = missing == 0 && unexpected == 0 && results.size() == expectedCount;
		boolean allPassed = failed == 0 && passed == expectedCount;
		boolean ready = dimensionalReady
				&& openFoamCoefficientResultReady
				&& externalDimensionalExtractionReady
				&& allPresent
				&& allPassed;
		return new OpenFoamDimensionalResidualSummary(
				dimensionalReady,
				openFoamCoefficientResultReady,
				externalDimensionalExtractionReady,
				expectedCount,
				readyDimensionalReferenceCount,
				expectedCount,
				results.size(),
				missing,
				unexpected,
				passed,
				failed,
				minChannels == Integer.MAX_VALUE ? 0 : minChannels,
				maxThrust,
				maxPower,
				maxTorque,
				maxInduced,
				maxMomentum,
				maxConvergence,
				allPresent,
				allPassed,
				ready,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(dimensionalReady, openFoamCoefficientResultReady,
						externalDimensionalExtractionReady, allPresent, allPassed),
				sourceRuntimeInfo
		);
	}

	private static List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase>
			openFoamDimensionalTargets() {
		return PropellerArchiveCtCpJOpenFoamValidationPlan.audit()
				.cases()
				.stream()
				.filter(PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase
						::postReviewOpenFoamCaseRunnable)
				.toList();
	}

	private static List<OpenFoamDimensionalCompactResult> passingResults(
			List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> targets
	) {
		return targets.stream()
				.map(target -> result(target,
						REQUIRED_DIMENSIONAL_RESULT_CHANNEL_COUNT,
						MAX_THRUST_RESIDUAL_TO_REFERENCE * 0.50,
						MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE * 0.50,
						MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE * 0.50,
						MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE * 0.50,
						MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE * 0.50,
						MAX_SOLVER_CONVERGENCE_RESIDUAL * 0.50))
				.toList();
	}

	private static OpenFoamDimensionalCompactResult failingResult(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target
	) {
		return result(target,
				REQUIRED_DIMENSIONAL_RESULT_CHANNEL_COUNT,
				MAX_THRUST_RESIDUAL_TO_REFERENCE * 0.50,
				MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE * 0.50,
				MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE * 0.50,
				MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE * 1.25,
				MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE * 0.50,
				MAX_SOLVER_CONVERGENCE_RESIDUAL * 0.50);
	}

	private static boolean passes(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target,
			OpenFoamDimensionalCompactResult result
	) {
		return result.solverFamily().equals(target.solverFamily())
				&& result.meshGeometryId().equals(target.geometryMatchId())
				&& passes(result.resultChannelCount(), result.thrustResidualToReference(),
						result.shaftPowerResidualToReference(), result.shaftTorqueResidualToReference(),
						result.inducedVelocityResidualToReference(), result.momentumPowerResidualToReference(),
						result.solverConvergenceResidual());
	}

	private static boolean passes(
			int resultChannelCount,
			double thrustResidualToReference,
			double shaftPowerResidualToReference,
			double shaftTorqueResidualToReference,
			double inducedVelocityResidualToReference,
			double momentumPowerResidualToReference,
			double solverConvergenceResidual
	) {
		return resultChannelCount >= REQUIRED_DIMENSIONAL_RESULT_CHANNEL_COUNT
				&& thrustResidualToReference <= MAX_THRUST_RESIDUAL_TO_REFERENCE
				&& shaftPowerResidualToReference <= MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE
				&& shaftTorqueResidualToReference <= MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE
				&& inducedVelocityResidualToReference <= MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE
				&& momentumPowerResidualToReference <= MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE
				&& solverConvergenceResidual <= MAX_SOLVER_CONVERGENCE_RESIDUAL;
	}

	private static OpenFoamDimensionalCompactResult findResult(
			List<OpenFoamDimensionalCompactResult> results,
			String presetName,
			String caseName
	) {
		return results.stream()
				.filter(result -> result.presetName().equals(presetName) && result.caseName().equals(caseName))
				.findFirst()
				.orElse(null);
	}

	private static OpenFoamDimensionalResidualExtrema extrema(
			List<OpenFoamDimensionalResidualScenario> scenarios
	) {
		int ready = 0;
		int maxExpected = 0;
		int maxReferences = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		double maxThrust = 0.0;
		double maxPower = 0.0;
		double maxTorque = 0.0;
		double maxInduced = 0.0;
		double maxMomentum = 0.0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamDimensionalResidualScenario scenario : scenarios) {
			OpenFoamDimensionalResidualSummary summary = scenario.summary();
			if (summary.openFoamDimensionalResidualReady()) {
				ready++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedOpenFoamDimensionalResultCaseCount());
			maxReferences = Math.max(maxReferences, summary.readyDimensionalReferenceCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxFailed = Math.max(maxFailed, summary.failedResultCount());
			maxThrust = Math.max(maxThrust, summary.maxThrustResidualToReference());
			maxPower = Math.max(maxPower, summary.maxShaftPowerResidualToReference());
			maxTorque = Math.max(maxTorque, summary.maxShaftTorqueResidualToReference());
			maxInduced = Math.max(maxInduced, summary.maxInducedVelocityResidualToReference());
			maxMomentum = Math.max(maxMomentum, summary.maxMomentumPowerResidualToReference());
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new OpenFoamDimensionalResidualExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxExpected,
				maxReferences,
				maxMissing,
				maxFailed,
				maxThrust,
				maxPower,
				maxTorque,
				maxInduced,
				maxMomentum,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			boolean dimensionalReady,
			boolean openFoamCoefficientResultReady,
			boolean externalDimensionalExtractionReady,
			boolean allPresent,
			boolean allPassed
	) {
		if (!dimensionalReady) {
			return "dimensional-response-reference-not-ready";
		}
		if (!openFoamCoefficientResultReady) {
			return "openfoam-coefficient-result-contract-not-ready";
		}
		if (!externalDimensionalExtractionReady) {
			return "openfoam-dimensional-extraction-not-ready";
		}
		if (!allPresent) {
			return "openfoam-dimensional-results-missing";
		}
		if (!allPassed) {
			return "openfoam-dimensional-residual-gate-failed";
		}
		return "openfoam-dimensional-residual-contract-ready";
	}

	private static void validateResidual(String fieldName, double value) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(fieldName + " must be finite and nonnegative.");
		}
	}

	private static String key(String presetName, String caseName) {
		return presetName + "/" + caseName;
	}
}
