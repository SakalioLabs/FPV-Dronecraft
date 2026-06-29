package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Sensitivity-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility sensitivity replays accepted physical-budget rows over cold, reference, and hot-air speed-of-sound cases; it is an audit layer only and never enables playable reference export, DroneConfig patching, runtime coupling, or gameplay auto-apply.";
	public static final double REFERENCE_AMBIENT_TEMPERATURE_CELSIUS =
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.AMBIENT_TEMPERATURE_CELSIUS;
	public static final double COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	public static final double HOT_AIR_TEMPERATURE_CELSIUS = 30.0;
	public static final double MAX_TIP_MACH_LIMIT =
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.MAX_TIP_MACH_LIMIT;
	public static final double COMPRESSIBILITY_ONSET_MACH =
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.COMPRESSIBILITY_ONSET_MACH;
	public static final double SERIOUS_LOSS_MACH =
			PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.NACA_SERIOUS_LOSS_MACH;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.SCENARIO_SAMPLE_COUNT;
	public static final int AMBIENT_CASE_COUNT = 3;
	public static final int SENSITIVITY_ROW_COUNT = 6;
	public static final int SCENARIO_METRIC_ROW_COUNT = 18;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SENSITIVITY_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<AmbientCaseDefinition> CASES = List.of(
			new AmbientCaseDefinition(
					"cold_sea_level_minus10c",
					COLD_AIR_TEMPERATURE_CELSIUS,
					"cold air lowers sound speed and raises the same tip speed Mach"
			),
			new AmbientCaseDefinition(
					"reference_lab_15c",
					REFERENCE_AMBIENT_TEMPERATURE_CELSIUS,
					"matches the existing RotorSpec tip-Mach and compressibility impact gates"
			),
			new AmbientCaseDefinition(
					"hot_air_30c",
					HOT_AIR_TEMPERATURE_CELSIUS,
					"hot air raises sound speed and reduces the same tip speed Mach"
			)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity() {
	}

	public record AmbientCaseDefinition(
			String ambientCaseName,
			double ambientTemperatureCelsius,
			String note
	) {
	}

	public record RetuneAmbientCompressibilitySensitivityRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double ambientTemperatureCelsius,
			double speedOfSoundMetersPerSecond,
			double speedOfSoundScaleVsReference,
			double candidateHoverTipMach,
			double candidateMaxTipMach,
			double candidateMaxMachMargin,
			double seriousLossMachMargin,
			double candidateMaxCompressibilityIntensity,
			double candidateMaxThrustLossPercent,
			double candidateMaxLoadFactor,
			double candidateMaxReactionTorqueScale,
			double candidateMaxVibrationProxy,
			double maxTipMachLimit,
			double compressibilityOnsetMach,
			double seriousLossMach,
			boolean physicalBudgetAccepted,
			boolean maxTipMachWithinBudget,
			boolean belowSeriousLossMach,
			boolean candidateImpactWithinBudget,
			boolean compressibilityReviewRequired,
			boolean ambientSensitivityAccepted,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetuneAmbientCompressibilitySensitivityScenarioSummary(
			boolean validationAcceptanceReady,
			boolean physicalBudgetAvailable,
			boolean ambientSensitivityAvailable,
			int sensitivityRowCount,
			int ambientCaseCount,
			int ambientSensitivityAcceptedCount,
			int impactWithinBudgetCount,
			int compressibilityReviewRequiredCount,
			double maxCandidateMaxTipMach,
			double minCandidateMaxMachMargin,
			double maxCandidateMaxThrustLossPercent,
			double maxCandidateMaxVibrationProxy,
			int playableReferenceAllowedCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneAmbientCompressibilitySensitivityScenario(
			String scenarioName,
			RetuneAmbientCompressibilitySensitivityScenarioSummary summary
	) {
	}

	public record RetuneAmbientCompressibilitySensitivityExtrema(
			int scenarioCount,
			int ambientSensitivityAvailableScenarioCount,
			int totalSensitivityRowCount,
			int maxSensitivityRowCount,
			int maxAmbientCaseCount,
			int maxAmbientSensitivityAcceptedCount,
			int maxImpactWithinBudgetCount,
			int maxCompressibilityReviewRequiredCount,
			double maxCandidateMaxTipMach,
			double minCandidateMaxMachMargin,
			double maxCandidateMaxThrustLossPercent,
			double maxCandidateMaxVibrationProxy,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneAmbientCompressibilitySensitivityAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int ambientCaseCount,
			int sensitivityRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			double referenceAmbientTemperatureCelsius,
			double referenceSpeedOfSoundMetersPerSecond,
			double maxTipMachLimit,
			double compressibilityOnsetMach,
			double seriousLossMach,
			List<AmbientCaseDefinition> cases,
			List<RetuneAmbientCompressibilitySensitivityRow> rows,
			List<RetuneAmbientCompressibilitySensitivityScenario> scenarios,
			RetuneAmbientCompressibilitySensitivityExtrema extrema
	) {
		public RetuneAmbientCompressibilitySensitivityAudit {
			cases = List.copyOf(cases);
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneAmbientCompressibilitySensitivityAudit audit() {
		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit physical =
				PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.audit();
		List<RetuneAmbientCompressibilitySensitivityScenario> scenarios = physical.scenarios().stream()
				.map(scenario -> scenario(physical, scenario))
				.toList();
		List<RetuneAmbientCompressibilitySensitivityRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(physical, scenario.scenarioName()).stream())
				.toList();
		return new RetuneAmbientCompressibilitySensitivityAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				AMBIENT_CASE_COUNT,
				SENSITIVITY_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				REFERENCE_AMBIENT_TEMPERATURE_CELSIUS,
				DroneEnvironment.speedOfSoundMetersPerSecond(REFERENCE_AMBIENT_TEMPERATURE_CELSIUS),
				MAX_TIP_MACH_LIMIT,
				COMPRESSIBILITY_ONSET_MACH,
				SERIOUS_LOSS_MACH,
				CASES,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static AmbientCaseDefinition caseDefinition(String ambientCaseName) {
		return CASES.stream()
				.filter(caseDefinition -> caseDefinition.ambientCaseName().equals(ambientCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient compressibility case: " + ambientCaseName));
	}

	public static RetuneAmbientCompressibilitySensitivityScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient compressibility scenario: " + scenarioName));
	}

	public static RetuneAmbientCompressibilitySensitivityRow row(
			String scenarioName,
			String presetName,
			String ambientCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.ambientCaseName().equals(ambientCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient compressibility row: "
								+ scenarioName + "/" + presetName + "/" + ambientCaseName));
	}

	private static RetuneAmbientCompressibilitySensitivityScenario scenario(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit physical,
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenario scenario
	) {
		List<RetuneAmbientCompressibilitySensitivityRow> rows = rowsForScenario(physical, scenario.scenarioName());
		return new RetuneAmbientCompressibilitySensitivityScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetuneAmbientCompressibilitySensitivityScenarioSummary summary(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary physicalSummary,
			List<RetuneAmbientCompressibilitySensitivityRow> rows
	) {
		int accepted = 0;
		int impact = 0;
		int review = 0;
		double maxMach = 0.0;
		double minMachMargin = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneAmbientCompressibilitySensitivityRow row : rows) {
			if (row.ambientSensitivityAccepted()) {
				accepted++;
			}
			if (row.candidateImpactWithinBudget()) {
				impact++;
			}
			if (row.compressibilityReviewRequired()) {
				review++;
			}
			maxMach = Math.max(maxMach, row.candidateMaxTipMach());
			minMachMargin = Math.min(minMachMargin, row.candidateMaxMachMargin());
			maxLoss = Math.max(maxLoss, row.candidateMaxThrustLossPercent());
			maxVibration = Math.max(maxVibration, row.candidateMaxVibrationProxy());
			if (row.playableReferenceAllowed()) {
				playable++;
			}
			if (row.configPatchAllowed()) {
				config++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		boolean available = physicalSummary.physicalBudgetAvailable() && !rows.isEmpty();
		return new RetuneAmbientCompressibilitySensitivityScenarioSummary(
				physicalSummary.validationAcceptanceReady(),
				physicalSummary.physicalBudgetAvailable(),
				available,
				rows.size(),
				available ? CASES.size() : 0,
				accepted,
				impact,
				review,
				maxMach,
				minMachMargin,
				maxLoss,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay,
				status(physicalSummary, rows),
				message(physicalSummary, rows),
				physicalSummary.sourceRuntimeInfo()
		);
	}

	private static List<RetuneAmbientCompressibilitySensitivityRow> rowsForScenario(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit physical,
			String scenarioName
	) {
		return physical.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceRow::physicalBudgetAccepted)
				.flatMap(row -> CASES.stream().map(ambientCase -> row(row, ambientCase)))
				.toList();
	}

	private static RetuneAmbientCompressibilitySensitivityRow row(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceRow physical,
			AmbientCaseDefinition ambientCase
	) {
		double referenceSpeed = DroneEnvironment.speedOfSoundMetersPerSecond(REFERENCE_AMBIENT_TEMPERATURE_CELSIUS);
		double speedOfSound = DroneEnvironment.speedOfSoundMetersPerSecond(ambientCase.ambientTemperatureCelsius());
		double speedScale = referenceSpeed / speedOfSound;
		double hoverMach = physical.candidateHoverTipMach() * speedScale;
		double maxMach = physical.candidateMaxTipMach() * speedScale;
		double intensity = rotorCompressibilityIntensity(maxMach);
		double thrustLoss = (1.0 - rotorCompressibilityThrustScale(maxMach)) * 100.0;
		double load = rotorCompressibilityLoadFactor(maxMach);
		double torque = rotorCompressibilityReactionTorqueScale(maxMach);
		double vibration = rotorCompressibilityVibrationProxy(
				physical.candidateMaxTipMach(),
				physical.candidateMaxVibrationProxy(),
				maxMach
		);
		boolean machWithin = maxMach <= MAX_TIP_MACH_LIMIT;
		boolean belowSerious = maxMach < SERIOUS_LOSS_MACH;
		boolean impactWithin = thrustLoss <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_THRUST_LOSS_PERCENT_LIMIT
				&& load <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_LOAD_FACTOR_LIMIT
				&& torque <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_REACTION_TORQUE_SCALE_LIMIT
				&& vibration <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_VIBRATION_PROXY_LIMIT;
		boolean reviewRequired = maxMach >= COMPRESSIBILITY_ONSET_MACH;
		boolean accepted = physical.physicalBudgetAccepted() && machWithin && belowSerious && impactWithin;
		return new RetuneAmbientCompressibilitySensitivityRow(
				physical.scenarioName(),
				physical.presetName(),
				ambientCase.ambientCaseName(),
				ambientCase.ambientTemperatureCelsius(),
				speedOfSound,
				speedScale,
				hoverMach,
				maxMach,
				MAX_TIP_MACH_LIMIT - maxMach,
				SERIOUS_LOSS_MACH - maxMach,
				intensity,
				thrustLoss,
				load,
				torque,
				vibration,
				MAX_TIP_MACH_LIMIT,
				COMPRESSIBILITY_ONSET_MACH,
				SERIOUS_LOSS_MACH,
				physical.physicalBudgetAccepted(),
				machWithin,
				belowSerious,
				impactWithin,
				reviewRequired,
				accepted,
				false,
				false,
				false,
				false,
				accepted ? "READY" : "PENDING_REVIEW",
				accepted
						? "ambient-compressibility-sensitivity-within-budget"
						: "cold-air-compressibility-impact-exceeds-budget"
		);
	}

	private static RetuneAmbientCompressibilitySensitivityExtrema extrema(
			List<RetuneAmbientCompressibilitySensitivityRow> rows,
			List<RetuneAmbientCompressibilitySensitivityScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxCases = 0;
		int maxAccepted = 0;
		int maxImpact = 0;
		int maxReview = 0;
		double maxMach = 0.0;
		double minMachMargin = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneAmbientCompressibilitySensitivityScenario scenario : scenarios) {
			RetuneAmbientCompressibilitySensitivityScenarioSummary summary = scenario.summary();
			if (summary.ambientSensitivityAvailable()) {
				available++;
			}
			if (summary.playableReferenceAllowedCount() > 0) {
				playable++;
			}
			if (summary.configPatchAllowedCount() > 0) {
				config++;
			}
			if (summary.runtimeCouplingAllowedCount() > 0) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowedCount() > 0) {
				gameplay++;
			}
			maxRows = Math.max(maxRows, summary.sensitivityRowCount());
			maxCases = Math.max(maxCases, summary.ambientCaseCount());
			maxAccepted = Math.max(maxAccepted, summary.ambientSensitivityAcceptedCount());
			maxImpact = Math.max(maxImpact, summary.impactWithinBudgetCount());
			maxReview = Math.max(maxReview, summary.compressibilityReviewRequiredCount());
			if (summary.ambientSensitivityAvailable()) {
				maxMach = Math.max(maxMach, summary.maxCandidateMaxTipMach());
				minMachMargin = Math.min(minMachMargin, summary.minCandidateMaxMachMargin());
				maxLoss = Math.max(maxLoss, summary.maxCandidateMaxThrustLossPercent());
				maxVibration = Math.max(maxVibration, summary.maxCandidateMaxVibrationProxy());
			}
		}
		return new RetuneAmbientCompressibilitySensitivityExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxCases,
				maxAccepted,
				maxImpact,
				maxReview,
				maxMach,
				minMachMargin,
				maxLoss,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay
		);
	}

	private static String status(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary physicalSummary,
			List<RetuneAmbientCompressibilitySensitivityRow> rows
	) {
		if (!physicalSummary.physicalBudgetAvailable()) {
			return "BLOCKED";
		}
		return rows.stream()
				.allMatch(RetuneAmbientCompressibilitySensitivityRow::ambientSensitivityAccepted)
						? "READY"
						: "PENDING_REVIEW";
	}

	private static String message(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary physicalSummary,
			List<RetuneAmbientCompressibilitySensitivityRow> rows
	) {
		if (!physicalSummary.physicalBudgetAvailable()) {
			return physicalSummary.message();
		}
		return rows.stream()
				.allMatch(RetuneAmbientCompressibilitySensitivityRow::ambientSensitivityAccepted)
						? "ambient-compressibility-sensitivity-within-budget"
						: "cold-air-compressibility-impact-exceeds-budget";
	}

	private static double rotorCompressibilityIntensity(double rotorTipMach) {
		return smoothStep(0.46, 0.82, rotorTipMach);
	}

	private static double rotorCompressibilityThrustScale(double rotorTipMach) {
		return MathUtil.clamp(1.0 - 0.20 * rotorCompressibilityIntensity(rotorTipMach), 0.74, 1.0);
	}

	private static double rotorCompressibilityLoadFactor(double rotorTipMach) {
		return 0.42 * rotorCompressibilityIntensity(rotorTipMach);
	}

	private static double rotorCompressibilityReactionTorqueScale(double rotorTipMach) {
		double intensity = rotorCompressibilityIntensity(rotorTipMach);
		return MathUtil.clamp(1.0 + 0.32 * intensity + 0.10 * intensity * intensity, 1.0, 1.42);
	}

	private static double rotorCompressibilityVibrationProxy(
			double referenceTipMach,
			double referenceVibrationProxy,
			double rotorTipMach
	) {
		double referenceIntensity = rotorCompressibilityIntensity(referenceTipMach);
		if (referenceIntensity <= 1.0e-9) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(referenceVibrationProxy / (0.22 * referenceIntensity), 0.0, 1.0);
		return MathUtil.clamp(0.22 * rotorCompressibilityIntensity(rotorTipMach) * spinRatio, 0.0, 0.34);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
