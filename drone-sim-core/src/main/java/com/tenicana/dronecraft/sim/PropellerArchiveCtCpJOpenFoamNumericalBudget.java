package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamNumericalBudget {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Numerical-Budget-Packet";
	public static final String CAVEAT =
			"OpenFOAM numerical budget derives pre-run mesh, timestep, Mach, Reynolds, domain constraints, and upstream reference materialization readiness from run setup rows; it is external case-authoring evidence only and cannot run OpenFOAM, vendor solver output, or tune gameplay.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 9;
	public static final int NUMERICAL_RULE_ROW_COUNT = 9;
	public static final int NUMERICAL_BUDGET_ROW_COUNT =
			PropellerArchiveCtCpJOpenFoamRunSetup.RUN_SETUP_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 21;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ NUMERICAL_RULE_ROW_COUNT
			+ NUMERICAL_BUDGET_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double INCOMPRESSIBLE_TIP_MACH_LIMIT = 0.30;
	public static final double NEAR_BLADE_CELL_DIVISIONS_PER_DIAMETER = 128.0;
	public static final double WAKE_CORE_CELL_DIVISIONS_PER_DIAMETER = 48.0;
	public static final double FAR_FIELD_RADIUS_DIAMETERS = 8.0;
	public static final double UPSTREAM_LENGTH_DIAMETERS = 4.0;
	public static final double DOWNSTREAM_LENGTH_DIAMETERS = 12.0;
	public static final double MAX_BLADE_COURANT = 0.50;
	public static final double MAX_FREESTREAM_COURANT = 1.00;
	public static final double MAX_AZIMUTH_DEGREES_PER_STEP = 2.0;
	public static final double TRANSITIONAL_REYNOLDS_REVIEW_FLOOR = 10_000.0;
	public static final String NEXT_REQUIRED_ACTION =
			"review-openfoam-mesh-yplus-and-time-step-against-run-setup";

	private static final double EPSILON = 1.0e-12;

	private static final List<OpenFoamNumericalBudgetRule> RULES = List.of(
			new OpenFoamNumericalBudgetRule("run_setup_required", true, true, true,
					"consume only manifest-backed OpenFOAM run setup rows",
					"keep-openfoam-run-setup-current"),
			new OpenFoamNumericalBudgetRule("reference_materialization_required", true, false, true,
					"keep numerical budgets pre-authoring until reviewed CT/CP/J lookup and OpenFOAM dimensional reference materialization opens",
					"execute-clearance-evidence-ledger-before-reviewed-payload-output"),
			new OpenFoamNumericalBudgetRule("incompressible_tip_mach_budget", true, true, true,
					"require helical tip Mach at or below 0.30 before using incompressible steady rotor cases",
					"review-openfoam-compressibility-assumption"),
			new OpenFoamNumericalBudgetRule("near_blade_cell_budget", true, true, true,
					"target near-blade cell size no coarser than D/128 before force or torque extraction",
					"author-openfoam-near-blade-mesh-budget"),
			new OpenFoamNumericalBudgetRule("wake_core_cell_budget", true, true, true,
					"target wake-core cell size no coarser than D/48 for induced-velocity and momentum-power checks",
					"author-openfoam-wake-core-mesh-budget"),
			new OpenFoamNumericalBudgetRule("courant_timestep_budget", true, true, true,
					"limit timestep by blade-cell Courant, freestream Courant, and two-degree azimuth advance",
					"bind-openfoam-deltaT-to-run-setup-budget"),
			new OpenFoamNumericalBudgetRule("domain_padding_budget", true, true, true,
					"use at least 8D radial, 4D upstream, and 12D downstream padding around the rotor",
					"author-openfoam-domain-padding-budget"),
			new OpenFoamNumericalBudgetRule("low_reynolds_model_review", true, false, true,
					"flag 0.75R chord Reynolds below 10000 for laminar or transition-model review",
					"review-low-reynolds-static-anchor-openfoam-model"),
			new OpenFoamNumericalBudgetRule("no_runtime_or_gameplay_auto_apply", true, true, true,
					"numerical budgets are offline CFD authoring evidence only",
					"keep-runtime-coupling-and-gameplay-auto-apply-closed")
	);

	private PropellerArchiveCtCpJOpenFoamNumericalBudget() {
	}

	public record OpenFoamNumericalBudgetRule(
			String ruleName,
			boolean required,
			boolean currentSatisfied,
			boolean postReviewSatisfied,
			String requirement,
			String nextRequiredAction
	) {
	}

	public record OpenFoamNumericalBudgetRow(
			String presetName,
			String caseName,
			String caseKey,
			double queryAdvanceRatioJ,
			double queryRpm,
			double propellerDiameterMeters,
			double helicalTipMach,
			boolean incompressibleAssumptionReady,
			double reynoldsStationChordNumber,
			boolean lowReynoldsTransitionReviewRequired,
			double nearBladeCellSizeMeters,
			double wakeCoreCellSizeMeters,
			double farFieldRadiusMeters,
			double upstreamLengthMeters,
			double downstreamLengthMeters,
			double azimuthLimitedTimeStepSeconds,
			double bladeCourantLimitedTimeStepSeconds,
			double freestreamCourantLimitedTimeStepSeconds,
			double suggestedTimeStepSeconds,
			double azimuthDegreesPerStep,
			double bladeCellCourantAtSuggestedTimeStep,
			double freestreamCourantAtSuggestedTimeStep,
			double stepsPerRevolutionAtSuggestedTimeStep,
			boolean timeStepBudgetReady,
			String referenceMaterializationScenarioName,
			boolean referenceMaterializationReady,
			int blockedOpenFoamReferenceRowCount,
			boolean runSetupReadyForExternalAuthoring,
			String referenceMaterializationNextRequiredAction,
			boolean numericalBudgetReadyForExternalAuthoring,
			boolean currentCaseRunnable,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String note
	) {
	}

	public record OpenFoamNumericalBudgetSummary(
			int numericalBudgetRowCount,
			int incompressibleAssumptionReadyCount,
			int timeStepBudgetReadyCount,
			int referenceMaterializationReadyBudgetCount,
			int runSetupReadyForExternalAuthoringCount,
			int numericalBudgetReadyForExternalAuthoringCount,
			int blockedOpenFoamReferenceRowTotal,
			int lowReynoldsTransitionReviewRequiredCount,
			int currentCaseRunnableCount,
			double maxHelicalTipMach,
			double minReynoldsStationChordNumber,
			double maxReynoldsStationChordNumber,
			double minNearBladeCellSizeMeters,
			double minSuggestedTimeStepSeconds,
			double maxSuggestedTimeStepSeconds,
			double maxStepsPerRevolutionAtSuggestedTimeStep,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String currentReferenceMaterializationScenarioName,
			String currentReferenceMaterializationNextRequiredAction,
			String nextRequiredAction
	) {
	}

	public record CtCpJOpenFoamNumericalBudgetAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int numericalRuleRowCount,
			int numericalBudgetRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamNumericalBudgetRule> rules,
			List<OpenFoamNumericalBudgetRow> rows,
			OpenFoamNumericalBudgetSummary summary
	) {
		public CtCpJOpenFoamNumericalBudgetAudit {
			rules = List.copyOf(rules);
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJOpenFoamNumericalBudgetAudit audit() {
		List<OpenFoamNumericalBudgetRow> rows = PropellerArchiveCtCpJOpenFoamRunSetup.audit()
				.rows()
				.stream()
				.map(PropellerArchiveCtCpJOpenFoamNumericalBudget::row)
				.toList();
		return new CtCpJOpenFoamNumericalBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				NUMERICAL_RULE_ROW_COUNT,
				NUMERICAL_BUDGET_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RULES,
				rows,
				summary(rows)
		);
	}

	public static OpenFoamNumericalBudgetRule rule(String ruleName) {
		return RULES.stream()
				.filter(rule -> rule.ruleName().equals(ruleName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM numerical budget rule: " + ruleName));
	}

	public static OpenFoamNumericalBudgetRow row(String presetName, String caseName) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM numerical budget row: " + presetName + "/" + caseName));
	}

	public static OpenFoamNumericalBudgetRow row(
			PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRow setup
	) {
		if (setup == null) {
			throw new IllegalArgumentException("OpenFOAM run setup row must not be null.");
		}
		double diameter = setup.propellerDiameterMeters();
		double nearBladeCell = diameter / NEAR_BLADE_CELL_DIVISIONS_PER_DIAMETER;
		double wakeCoreCell = diameter / WAKE_CORE_CELL_DIVISIONS_PER_DIAMETER;
		double farFieldRadius = diameter * FAR_FIELD_RADIUS_DIAMETERS;
		double upstreamLength = diameter * UPSTREAM_LENGTH_DIAMETERS;
		double downstreamLength = diameter * DOWNSTREAM_LENGTH_DIAMETERS;
		double azimuthLimitedDt = Math.toRadians(MAX_AZIMUTH_DEGREES_PER_STEP)
				/ Math.max(EPSILON, Math.abs(setup.angularVelocityRadiansPerSecond()));
		double bladeCflLimitedDt = MAX_BLADE_COURANT * nearBladeCell
				/ Math.max(1.0, setup.helicalTipSpeedMetersPerSecond());
		double freestreamCflLimitedDt = setup.axialFreestreamSpeedMetersPerSecond() > EPSILON
				? MAX_FREESTREAM_COURANT * wakeCoreCell
						/ setup.axialFreestreamSpeedMetersPerSecond()
				: Double.POSITIVE_INFINITY;
		double suggestedDt = Math.min(azimuthLimitedDt,
				Math.min(bladeCflLimitedDt, freestreamCflLimitedDt));
		double azimuthDegrees = Math.toDegrees(
				Math.abs(setup.angularVelocityRadiansPerSecond()) * suggestedDt);
		double bladeCourant = setup.helicalTipSpeedMetersPerSecond() * suggestedDt
				/ Math.max(EPSILON, nearBladeCell);
		double freestreamCourant = setup.axialFreestreamSpeedMetersPerSecond() > EPSILON
				? setup.axialFreestreamSpeedMetersPerSecond() * suggestedDt
						/ Math.max(EPSILON, wakeCoreCell)
				: 0.0;
		double stepsPerRevolution = setup.revolutionsPerSecond() > EPSILON
				? 1.0 / (setup.revolutionsPerSecond() * suggestedDt)
				: 0.0;
		boolean incompressibleReady = setup.helicalTipMach() <= INCOMPRESSIBLE_TIP_MACH_LIMIT;
		boolean lowReynoldsReview =
				setup.reynoldsStationChordNumber() < TRANSITIONAL_REYNOLDS_REVIEW_FLOOR;
		boolean timeStepReady = bladeCourant <= MAX_BLADE_COURANT + 1.0e-9
				&& freestreamCourant <= MAX_FREESTREAM_COURANT + 1.0e-9
				&& azimuthDegrees <= MAX_AZIMUTH_DEGREES_PER_STEP + 1.0e-9;
		boolean authoringReady = setup.runSetupReadyForExternalAuthoring()
				&& incompressibleReady
				&& timeStepReady;
		String nextAction = nextRequiredAction(setup, lowReynoldsReview);
		return new OpenFoamNumericalBudgetRow(
				setup.presetName(),
				setup.caseName(),
				setup.caseKey(),
				setup.queryAdvanceRatioJ(),
				setup.queryRpm(),
				diameter,
				setup.helicalTipMach(),
				incompressibleReady,
				setup.reynoldsStationChordNumber(),
				lowReynoldsReview,
				nearBladeCell,
				wakeCoreCell,
				farFieldRadius,
				upstreamLength,
				downstreamLength,
				azimuthLimitedDt,
				bladeCflLimitedDt,
				freestreamCflLimitedDt,
				suggestedDt,
				azimuthDegrees,
				bladeCourant,
				freestreamCourant,
				stepsPerRevolution,
				timeStepReady,
				setup.referenceMaterializationScenarioName(),
				setup.referenceMaterializationReady(),
				setup.blockedOpenFoamReferenceRowCount(),
				setup.runSetupReadyForExternalAuthoring(),
				setup.referenceMaterializationNextRequiredAction(),
				authoringReady,
				setup.currentCaseRunnable(),
				false,
				false,
				"BLOCKED",
				nextAction,
				note(setup, lowReynoldsReview)
		);
	}

	private static OpenFoamNumericalBudgetSummary summary(List<OpenFoamNumericalBudgetRow> rows) {
		int machReady = 0;
		int timeStepReady = 0;
		int materializationReady = 0;
		int setupAuthoringReady = 0;
		int authoringReady = 0;
		int blockedReferenceRows = 0;
		int lowReynolds = 0;
		int runnable = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxMach = 0.0;
		double minReynolds = Double.POSITIVE_INFINITY;
		double maxReynolds = 0.0;
		double minNearCell = Double.POSITIVE_INFINITY;
		double minDt = Double.POSITIVE_INFINITY;
		double maxDt = 0.0;
		double maxSteps = 0.0;
		String currentScenario = "";
		String currentMaterializationAction = "";
		String nextAction = NEXT_REQUIRED_ACTION;
		for (OpenFoamNumericalBudgetRow row : rows) {
			if (row.incompressibleAssumptionReady()) {
				machReady++;
			}
			if (row.timeStepBudgetReady()) {
				timeStepReady++;
			}
			if (row.referenceMaterializationReady()) {
				materializationReady++;
			}
			if (row.runSetupReadyForExternalAuthoring()) {
				setupAuthoringReady++;
			}
			if (row.numericalBudgetReadyForExternalAuthoring()) {
				authoringReady++;
			}
			blockedReferenceRows += row.blockedOpenFoamReferenceRowCount();
			if (row.lowReynoldsTransitionReviewRequired()) {
				lowReynolds++;
			}
			if (row.currentCaseRunnable()) {
				runnable++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxMach = Math.max(maxMach, row.helicalTipMach());
			minReynolds = Math.min(minReynolds, row.reynoldsStationChordNumber());
			maxReynolds = Math.max(maxReynolds, row.reynoldsStationChordNumber());
			minNearCell = Math.min(minNearCell, row.nearBladeCellSizeMeters());
			minDt = Math.min(minDt, row.suggestedTimeStepSeconds());
			maxDt = Math.max(maxDt, row.suggestedTimeStepSeconds());
			maxSteps = Math.max(maxSteps, row.stepsPerRevolutionAtSuggestedTimeStep());
			if (currentScenario.isBlank()) {
				currentScenario = row.referenceMaterializationScenarioName();
				currentMaterializationAction = row.referenceMaterializationNextRequiredAction();
				nextAction = row.nextRequiredAction();
			}
		}
		if (!Double.isFinite(minReynolds)) {
			minReynolds = 0.0;
		}
		if (!Double.isFinite(minNearCell)) {
			minNearCell = 0.0;
		}
		if (!Double.isFinite(minDt)) {
			minDt = 0.0;
		}
		return new OpenFoamNumericalBudgetSummary(
				rows.size(),
				machReady,
				timeStepReady,
				materializationReady,
				setupAuthoringReady,
				authoringReady,
				blockedReferenceRows,
				lowReynolds,
				runnable,
				maxMach,
				minReynolds,
				maxReynolds,
				minNearCell,
				minDt,
				maxDt,
				maxSteps,
				runtime,
				gameplay,
				currentScenario,
				currentMaterializationAction,
				nextAction
		);
	}

	private static String nextRequiredAction(
			PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRow setup,
			boolean lowReynoldsReview
	) {
		if (!setup.runSetupReadyForExternalAuthoring()) {
			return setup.nextRequiredAction();
		}
		return lowReynoldsReview
				? "review-low-reynolds-static-anchor-openfoam-model"
				: NEXT_REQUIRED_ACTION;
	}

	private static String note(
			PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRow setup,
			boolean lowReynoldsReview
	) {
		if (!setup.runSetupReadyForExternalAuthoring()) {
			return "numerical budget is computable, but reference materialization blocks external case authoring";
		}
		return lowReynoldsReview
				? "numerical budget is computable but static-anchor Reynolds requires transition or laminar-model review"
				: "numerical budget is computable but external case hash and solver output remain absent";
	}
}
