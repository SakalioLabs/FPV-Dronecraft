package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamValidationPlan {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-OpenFOAM-Validation-Plan-Packet";
	public static final String CAVEAT =
			"OpenFOAM is treated as an external offline CFD validator for reviewed CT/CP/J lookup rows; no OpenFOAM code or raw solver output is vendored, and runtime/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int SOLVER_REQUIREMENT_ROW_COUNT = 8;
	public static final int VALIDATION_CASE_ROW_COUNT = PropellerArchiveCtCpJLookupAcceptanceGate.TARGET_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SOLVER_REQUIREMENT_ROW_COUNT
			+ VALIDATION_CASE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String OPENFOAM_REPOSITORY = "https://github.com/OpenFOAM/OpenFOAM-dev";
	public static final String SOLVER_FAMILY = "external-openfoam-steady-rotor-cfd";
	public static final String NEXT_REQUIRED_ACTION =
			"run-external-openfoam-validation-after-reviewed-ct-cp-j-import";
	public static final double MAX_CT_RESIDUAL = 0.08;
	public static final double MAX_CP_RESIDUAL = 0.10;
	public static final double MAX_ETA_RESIDUAL = 0.08;

	private static final List<OpenFoamValidationRequirement> REQUIREMENTS = List.of(
			new OpenFoamValidationRequirement("openfoam_source_external", "solver_source", true,
					true, true, "source_boundary", "keep-openfoam-code-out-of-repository",
					"READY", "use OpenFOAM as an external offline executable only"),
			new OpenFoamValidationRequirement("gpl_code_boundary", "license_boundary", true,
					true, true, "source_boundary", "keep-gpl-implementation-out-of-mod-code",
					"READY", "store only derived reviewed coefficients or residual summaries"),
			new OpenFoamValidationRequirement("reviewed_archive_import", "wind_tunnel_reference", true,
					false, true, "data_source", "review-source-license-before-importing-raw-rows",
					"BLOCKED", "wind-tunnel CT CP eta rows still need source review"),
			new OpenFoamValidationRequirement("blade_geometry_mesh_input", "geometry", true,
					false, false, "mesh_input", "resolve-heavy-lift-reviewed-geometry-before-openfoam-mesh",
					"BLOCKED", "heavyLift has no reviewed blade geometry row yet"),
			new OpenFoamValidationRequirement("rotating_domain_case_template", "solver_setup", true,
					false, false, "solver_setup", "author-external-openfoam-case-template-outside-mod-code",
					"BLOCKED", "steady rotor or actuator-disk CFD template is not executed"),
			new OpenFoamValidationRequirement("ct_cp_eta_extraction", "result_schema", true,
					false, false, "coefficient_extraction", "extract-ct-cp-eta-from-openfoam-force-power-results",
					"BLOCKED", "no solver result channels exist in the repository"),
			new OpenFoamValidationRequirement("wind_tunnel_cfd_residual_gate", "acceptance_gate", true,
					false, false, "quality_gate", "compare-openfoam-and-wind-tunnel-ct-cp-eta-residuals",
					"BLOCKED", "CFD residuals must be reviewed before lookup acceptance can trust them"),
			new OpenFoamValidationRequirement("no_runtime_or_playable_auto_apply", "leak_guard", true,
					true, true, "leak_guard", "keep-runtime-coupling-and-gameplay-auto-apply-closed",
					"READY", "this plan cannot mutate DronePhysics or playable tuning")
	);

	private PropellerArchiveCtCpJOpenFoamValidationPlan() {
	}

	public record OpenFoamValidationRequirement(
			String requirementName,
			String category,
			boolean required,
			boolean currentSatisfied,
			boolean postReviewSatisfied,
			String evidenceRole,
			String nextRequiredAction,
			String status,
			String note
	) {
	}

	public record OpenFoamValidationCase(
			String presetName,
			String caseName,
			String performanceMatchId,
			String geometryMatchId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double equivalentProjectMu,
			int minimumNeighborRows,
			boolean staticAnchorCase,
			boolean performanceValidationTarget,
			boolean fullSimulationCandidate,
			boolean meshGeometryReadyAfterReview,
			boolean currentOpenFoamCaseRunnable,
			boolean postReviewOpenFoamCaseRunnable,
			boolean openFoamResultAvailable,
			int requiredResultChannels,
			double maxCtResidual,
			double maxCpResidual,
			double maxEtaResidual,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String solverFamily,
			String status,
			String message,
			String nextRequiredAction
	) {
	}

	public record OpenFoamValidationSummary(
			int validationCaseCount,
			int performanceValidationCaseCount,
			int fullSimulationCandidateCount,
			int performanceOnlyCaseCount,
			int meshGeometryReadyAfterReviewCaseCount,
			int missingGeometryCaseCount,
			int currentRunnableCaseCount,
			int postReviewRunnableCaseCount,
			int openFoamResultAvailableCount,
			int requiredCtCpEtaChannelCount,
			int staticAnchorCaseCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String nextRequiredAction
	) {
	}

	public record CtCpJOpenFoamValidationPlanAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int solverRequirementRowCount,
			int validationCaseRowCount,
			int summaryRowCount,
			int methodRowCount,
			String openFoamRepository,
			List<OpenFoamValidationRequirement> requirements,
			List<OpenFoamValidationCase> cases,
			OpenFoamValidationSummary summary
	) {
		public CtCpJOpenFoamValidationPlanAudit {
			requirements = List.copyOf(requirements);
			cases = List.copyOf(cases);
		}
	}

	public static CtCpJOpenFoamValidationPlanAudit audit() {
		List<OpenFoamValidationCase> cases = PropellerArchiveCtCpJLookupAcceptanceGate.targets()
				.stream()
				.map(PropellerArchiveCtCpJOpenFoamValidationPlan::validationCase)
				.toList();
		return new CtCpJOpenFoamValidationPlanAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SOLVER_REQUIREMENT_ROW_COUNT,
				VALIDATION_CASE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				OPENFOAM_REPOSITORY,
				REQUIREMENTS,
				cases,
				summary(cases)
		);
	}

	public static OpenFoamValidationRequirement requirement(String requirementName) {
		return REQUIREMENTS.stream()
				.filter(requirement -> requirement.requirementName().equals(requirementName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM validation requirement: " + requirementName));
	}

	public static OpenFoamValidationCase caseRow(String presetName, String caseName) {
		return audit().cases()
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM CT/CP/J validation case: " + presetName + " / " + caseName));
	}

	private static OpenFoamValidationCase validationCase(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target
	) {
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract(target.presetName(), target.caseName());
		boolean geometryReady = !"missing-reviewed-geometry-match".equals(contract.geometryMatchId());
		boolean fullSimulation = target.downstreamUse().startsWith("full-simulation");
		boolean postReviewRunnable = geometryReady && fullSimulation;
		return new OpenFoamValidationCase(
				target.presetName(),
				target.caseName(),
				contract.performanceMatchId(),
				contract.geometryMatchId(),
				contract.queryAdvanceRatioJ(),
				contract.queryRpm(),
				contract.queryAdvanceRatioJ() / Math.PI,
				target.minNeighborRows(),
				target.requiresStaticAnchorPreservation(),
				true,
				fullSimulation,
				geometryReady,
				false,
				postReviewRunnable,
				false,
				3,
				MAX_CT_RESIDUAL,
				MAX_CP_RESIDUAL,
				MAX_ETA_RESIDUAL,
				false,
				false,
				SOLVER_FAMILY,
				"BLOCKED",
				messageFor(geometryReady, postReviewRunnable),
				nextActionFor(geometryReady, postReviewRunnable)
		);
	}

	private static OpenFoamValidationSummary summary(List<OpenFoamValidationCase> cases) {
		int performance = 0;
		int fullSimulation = 0;
		int performanceOnly = 0;
		int meshReady = 0;
		int missingGeometry = 0;
		int currentRunnable = 0;
		int postReviewRunnable = 0;
		int results = 0;
		int channels = 0;
		int staticAnchors = 0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamValidationCase validationCase : cases) {
			if (validationCase.performanceValidationTarget()) {
				performance++;
			}
			if (validationCase.fullSimulationCandidate()) {
				fullSimulation++;
			} else {
				performanceOnly++;
			}
			if (validationCase.meshGeometryReadyAfterReview()) {
				meshReady++;
			} else {
				missingGeometry++;
			}
			if (validationCase.currentOpenFoamCaseRunnable()) {
				currentRunnable++;
			}
			if (validationCase.postReviewOpenFoamCaseRunnable()) {
				postReviewRunnable++;
			}
			if (validationCase.openFoamResultAvailable()) {
				results++;
			}
			channels += validationCase.requiredResultChannels();
			if (validationCase.staticAnchorCase()) {
				staticAnchors++;
			}
			if (validationCase.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (validationCase.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new OpenFoamValidationSummary(
				cases.size(),
				performance,
				fullSimulation,
				performanceOnly,
				meshReady,
				missingGeometry,
				currentRunnable,
				postReviewRunnable,
				results,
				channels,
				staticAnchors,
				runtime,
				gameplay,
				NEXT_REQUIRED_ACTION
		);
	}

	private static String messageFor(boolean geometryReady, boolean postReviewRunnable) {
		if (!geometryReady) {
			return "openfoam-blade-geometry-missing";
		}
		if (!postReviewRunnable) {
			return "openfoam-case-not-buildable";
		}
		return "openfoam-run-not-executed";
	}

	private static String nextActionFor(boolean geometryReady, boolean postReviewRunnable) {
		if (!geometryReady) {
			return "resolve-heavy-lift-reviewed-geometry-before-openfoam-mesh";
		}
		if (!postReviewRunnable) {
			return "author-external-openfoam-case-template-outside-mod-code";
		}
		return NEXT_REQUIRED_ACTION;
	}
}
