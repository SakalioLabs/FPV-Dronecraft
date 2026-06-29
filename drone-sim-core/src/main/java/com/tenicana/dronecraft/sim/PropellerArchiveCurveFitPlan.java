package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCurveFitPlan {
	public static final String SOURCE_ID = "User-Propeller-Archive-Curve-Fit-Plan-Packet";
	public static final String CAVEAT =
			"Curve-fit plan defines the offline CT/CP/eta and blade-geometry fit surface after reviewed import; it produces no coefficients, imports no raw rows, and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 5;
	public static final int CURVE_CONTRACT_ROW_COUNT = 6;
	public static final int PRESET_PLAN_ROW_COUNT = 4;
	public static final int FIT_STAGE_ROW_COUNT = 7;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ CURVE_CONTRACT_ROW_COUNT
			+ PRESET_PLAN_ROW_COUNT
			+ FIT_STAGE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<CurveFitContract> CURVE_CONTRACTS = List.of(
			new CurveFitContract(
					"ct_vs_advance",
					"performance",
					"advance_ratio;rpm;diameter_inches;pitch_inches;blade_count",
					"thrust_coefficient",
					"shape-preserving-regularized-spline",
					true,
					"finite-CT-static-anchor-and-zero-crossing-preserved",
					"weighted-rmse-plus-static-anchor-error",
					"RotorSpec thrust coefficient and high-J thrust loss"
			),
			new CurveFitContract(
					"cp_vs_advance",
					"performance",
					"advance_ratio;rpm;diameter_inches;pitch_inches;blade_count",
					"power_coefficient",
					"positive-shape-preserving-regularized-spline",
					true,
					"finite-positive-CP-and-smooth-low-J-power",
					"weighted-rmse-plus-power-closure-error",
					"shaft-power and induced-power consistency"
			),
			new CurveFitContract(
					"eta_consistency",
					"performance",
					"advance_ratio;thrust_coefficient;power_coefficient",
					"efficiency",
					"derived-consistency-check",
					true,
					"eta-consistent-with-J-CT-over-CP-with-low-J-guard",
					"max-absolute-efficiency-residual",
					"reject inconsistent power/thrust rows before fit"
			),
			new CurveFitContract(
					"static_ct_cp_vs_rpm",
					"performance",
					"rpm;diameter_inches;pitch_inches;blade_count",
					"static_thrust_and_power_coefficients",
					"rpm-binned-static-anchor-fit",
					true,
					"J-zero-static-rows-retain-mean-and-spread",
					"static-mean-error-and-static-spread-coverage",
					"hover/static thrust and power calibration"
			),
			new CurveFitContract(
					"chord_distribution",
					"geometry",
					"station_radius_ratio;diameter_inches;pitch_inches",
					"chord_to_radius",
					"bounded-pchip-station-fit",
					true,
					"positive-chord-and-integrated-solidity-finite",
					"station-rmse-and-solidity-error",
					"blade chord and solidity proxy"
			),
			new CurveFitContract(
					"beta_distribution",
					"geometry",
					"station_radius_ratio;diameter_inches;pitch_inches",
					"beta_degrees",
					"bounded-pchip-station-fit",
					true,
					"finite-beta-and-smooth-root-to-tip-twist",
					"station-rmse-and-70r-beta-error",
					"blade pitch/twist geometry"
			)
	);

	private static final List<CurveFitStage> FIT_STAGES = List.of(
			new CurveFitStage("source_review", false, true, true,
					"review-source-license-before-importing-raw-rows"),
			new CurveFitStage("import_contract", false, true, true,
					"import-reviewed-raw-rows-into-offline-fitting-pipeline"),
			new CurveFitStage("fit_input_coverage", false, false, true,
					"resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage"),
			new CurveFitStage("curve_fit_execution", false, false, true,
					"fit-ct-cp-j-re-and-blade-geometry-curves"),
			new CurveFitStage("fit_quality_acceptance", false, false, true,
					"accept-fit-quality-before-reference-export"),
			new CurveFitStage("compact_reference_handoff", false, false, true,
					"review-compact-reference-before-playable-handoff"),
			new CurveFitStage("runtime_leak_guard", true, true, true,
					"keep-runtime-coupling-and-gameplay-auto-apply-closed")
	);

	private PropellerArchiveCurveFitPlan() {
	}

	public record CurveFitContract(
			String curveName,
			String sourceTable,
			String independentVariables,
			String dependentVariable,
			String fitModel,
			boolean requiredForSimulationFit,
			String physicalConstraint,
			String validationMetric,
			String downstreamUse
	) {
	}

	public record PresetCurveFitPlan(
			String presetName,
			String performanceMatchId,
			String geometryMatchId,
			boolean performanceInputReadyAfterReview,
			boolean bladeCoverageReadyAfterReview,
			boolean geometryInputReadyAfterReview,
			boolean ctCpCurveFitAllowedAfterReview,
			boolean geometryCurveFitAllowedAfterReview,
			boolean fullSimulationCurveFitAllowedAfterReview,
			boolean fullSimulationCurveFitAllowedInSyntheticTarget,
			boolean compactReferenceExportAllowedAfterReview,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String postReviewBlocker,
			String nextRequiredAction
	) {
	}

	public record CurveFitStage(
			String stageName,
			boolean currentSatisfied,
			boolean reviewedImportSatisfied,
			boolean syntheticTargetSatisfied,
			String nextRequiredAction
	) {
	}

	public record CurveFitPlanSummary(
			int curveContractCount,
			int requiredCurveContractCount,
			int presetPlanCount,
			int postReviewCtCpFitAllowedCount,
			int postReviewGeometryFitAllowedCount,
			int postReviewFullSimulationFitAllowedCount,
			int syntheticFullSimulationFitAllowedCount,
			int compactReferenceExportAllowedAfterReviewCount,
			int runtimeCouplingAllowedPresetCount,
			int gameplayAutoApplyAllowedPresetCount,
			String nextRequiredAction
	) {
	}

	public record CurveFitPlanAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int curveContractRowCount,
			int presetPlanRowCount,
			int fitStageRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CurveFitContract> curves,
			List<PresetCurveFitPlan> presets,
			List<CurveFitStage> stages,
			CurveFitPlanSummary summary
	) {
		public CurveFitPlanAudit {
			curves = List.copyOf(curves);
			presets = List.copyOf(presets);
			stages = List.copyOf(stages);
		}
	}

	public static CurveFitPlanAudit audit() {
		List<PresetCurveFitPlan> presets = PropellerArchiveFitImportContract.audit()
				.presets()
				.stream()
				.map(PropellerArchiveCurveFitPlan::preset)
				.toList();
		return new CurveFitPlanAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				CURVE_CONTRACT_ROW_COUNT,
				PRESET_PLAN_ROW_COUNT,
				FIT_STAGE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CURVE_CONTRACTS,
				presets,
				FIT_STAGES,
				summary(presets)
		);
	}

	public static CurveFitContract curve(String curveName) {
		return CURVE_CONTRACTS.stream()
				.filter(curve -> curve.curveName().equals(curveName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown curve fit contract: " + curveName));
	}

	public static PresetCurveFitPlan preset(String presetName) {
		return PropellerArchiveFitImportContract.audit()
				.presets()
				.stream()
				.filter(preset -> preset.presetName().equals(presetName))
				.findFirst()
				.map(PropellerArchiveCurveFitPlan::preset)
				.orElseThrow(() -> new IllegalArgumentException("unknown curve fit preset: " + presetName));
	}

	public static CurveFitStage stage(String stageName) {
		return FIT_STAGES.stream()
				.filter(stage -> stage.stageName().equals(stageName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown curve fit stage: " + stageName));
	}

	private static PresetCurveFitPlan preset(
			PropellerArchiveFitImportContract.PresetFitInputContract input
	) {
		boolean performanceReady = input.performanceSchemaReadyAfterReview();
		boolean bladeReady = input.bladeCountCoverageReadyAfterReview();
		boolean geometryReady = input.geometryCoverageReadyAfterReview();
		boolean ctCpAllowed = performanceReady && bladeReady;
		boolean geometryAllowed = geometryReady;
		boolean fullAllowed = input.fitInputReadyAfterReview();
		String blocker = postReviewBlocker(performanceReady, bladeReady, geometryReady);
		return new PresetCurveFitPlan(
				input.presetName(),
				input.performanceMatchId(),
				input.geometryMatchId(),
				performanceReady,
				bladeReady,
				geometryReady,
				ctCpAllowed,
				geometryAllowed,
				fullAllowed,
				input.fitInputReadyInSyntheticTarget(),
				false,
				false,
				false,
				blocker,
				nextRequiredAction(blocker)
		);
	}

	private static CurveFitPlanSummary summary(List<PresetCurveFitPlan> presets) {
		int requiredCurves = 0;
		for (CurveFitContract curve : CURVE_CONTRACTS) {
			if (curve.requiredForSimulationFit()) {
				requiredCurves++;
			}
		}
		int ctCp = 0;
		int geometry = 0;
		int full = 0;
		int synthetic = 0;
		int reference = 0;
		int runtime = 0;
		int gameplay = 0;
		for (PresetCurveFitPlan preset : presets) {
			if (preset.ctCpCurveFitAllowedAfterReview()) {
				ctCp++;
			}
			if (preset.geometryCurveFitAllowedAfterReview()) {
				geometry++;
			}
			if (preset.fullSimulationCurveFitAllowedAfterReview()) {
				full++;
			}
			if (preset.fullSimulationCurveFitAllowedInSyntheticTarget()) {
				synthetic++;
			}
			if (preset.compactReferenceExportAllowedAfterReview()) {
				reference++;
			}
			if (preset.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (preset.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		String nextAction = full == presets.size()
				? "fit-ct-cp-j-re-and-blade-geometry-curves"
				: "resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage";
		return new CurveFitPlanSummary(
				CURVE_CONTRACTS.size(),
				requiredCurves,
				presets.size(),
				ctCp,
				geometry,
				full,
				synthetic,
				reference,
				runtime,
				gameplay,
				nextAction
		);
	}

	private static String postReviewBlocker(
			boolean performanceReady,
			boolean bladeReady,
			boolean geometryReady
	) {
		if (!performanceReady) {
			return "performance-fit-input-missing";
		}
		if (!bladeReady) {
			return "blade-count-coverage-missing";
		}
		if (!geometryReady) {
			return "geometry-fit-input-missing";
		}
		return "none";
	}

	private static String nextRequiredAction(String blocker) {
		return switch (blocker) {
			case "performance-fit-input-missing" -> "add-reviewed-performance-match-for-preset";
			case "blade-count-coverage-missing" -> "resolve-cinewhoop-three-blade-coverage-or-correction";
			case "geometry-fit-input-missing" -> "add-heavy-lift-geometry-match-or-reviewed-surrogate";
			case "none" -> "fit-ct-cp-j-re-and-blade-geometry-curves";
			default -> "inspect-curve-fit-plan-blocker";
		};
	}
}
