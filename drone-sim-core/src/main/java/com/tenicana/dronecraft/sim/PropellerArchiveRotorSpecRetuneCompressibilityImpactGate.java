package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneCompressibilityImpactGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Compressibility-Impact-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune compressibility impact gate quantifies runtime thrust loss, load, reaction-torque, and vibration impact only after Reynolds-budget acceptance; it produces audit material and never emits a DroneConfig patch, runtime coupling, or gameplay auto-apply.";
	public static final double AMBIENT_TEMPERATURE_CELSIUS =
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.AMBIENT_TEMPERATURE_CELSIUS;
	public static final double COMPRESSIBILITY_ONSET_MACH =
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.COMPRESSIBILITY_ONSET_MACH;
	public static final double MAX_THRUST_LOSS_PERCENT_LIMIT = 10.0;
	public static final double MAX_LOAD_FACTOR_LIMIT = 0.22;
	public static final double MAX_REACTION_TORQUE_SCALE_LIMIT = 1.18;
	public static final double MAX_VIBRATION_PROXY_LIMIT = 0.12;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int IMPACT_ROW_COUNT = 2;
	public static final int SCENARIO_METRIC_ROW_COUNT = 16;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ IMPACT_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double RPM_TO_RADIANS_PER_SECOND = 2.0 * Math.PI / 60.0;

	private PropellerArchiveRotorSpecRetuneCompressibilityImpactGate() {
	}

	public record RetuneCompressibilityImpactRow(
			String scenarioName,
			String presetName,
			double ambientTemperatureCelsius,
			double speedOfSoundMetersPerSecond,
			double currentHoverTipMach,
			double candidateHoverTipMach,
			double currentMaxTipMach,
			double candidateMaxTipMach,
			double currentHoverCompressibilityIntensity,
			double candidateHoverCompressibilityIntensity,
			double currentMaxCompressibilityIntensity,
			double candidateMaxCompressibilityIntensity,
			double candidateHoverThrustScale,
			double candidateMaxThrustScale,
			double candidateMaxThrustLossPercent,
			double candidateMaxLoadFactor,
			double candidateMaxReactionTorqueScale,
			double candidateMaxVibrationProxy,
			double maxThrustLossPercentLimit,
			double maxLoadFactorLimit,
			double maxReactionTorqueScaleLimit,
			double maxVibrationProxyLimit,
			boolean candidateHoverCompressibilityFree,
			boolean candidateMaxCompressibilityReviewRequired,
			boolean candidateImpactWithinBudget,
			boolean compressibilityImpactAccepted,
			boolean reynoldsBudgetAccepted,
			boolean manualPatchReviewAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetuneCompressibilityImpactScenarioSummary(
			boolean validationAcceptanceReady,
			boolean reynoldsBudgetAvailable,
			boolean compressibilityImpactAvailable,
			int impactRowCount,
			int compressibilityImpactAcceptedCount,
			int compressibilityReviewRequiredCount,
			double maxCandidateMaxCompressibilityIntensity,
			double maxCandidateMaxThrustLossPercent,
			double maxCandidateMaxLoadFactor,
			double maxCandidateMaxReactionTorqueScale,
			double maxCandidateMaxVibrationProxy,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneCompressibilityImpactScenario(
			String scenarioName,
			RetuneCompressibilityImpactScenarioSummary summary
	) {
	}

	public record RetuneCompressibilityImpactExtrema(
			int scenarioCount,
			int compressibilityImpactAvailableScenarioCount,
			int totalImpactRowCount,
			int maxImpactRowCount,
			int maxCompressibilityImpactAcceptedCount,
			int maxCompressibilityReviewRequiredCount,
			double maxCandidateMaxCompressibilityIntensity,
			double maxCandidateMaxThrustLossPercent,
			double maxCandidateMaxLoadFactor,
			double maxCandidateMaxReactionTorqueScale,
			double maxCandidateMaxVibrationProxy,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneCompressibilityImpactAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int impactRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			double ambientTemperatureCelsius,
			double speedOfSoundMetersPerSecond,
			double compressibilityOnsetMach,
			double maxThrustLossPercentLimit,
			double maxLoadFactorLimit,
			double maxReactionTorqueScaleLimit,
			double maxVibrationProxyLimit,
			List<RetuneCompressibilityImpactRow> rows,
			List<RetuneCompressibilityImpactScenario> scenarios,
			RetuneCompressibilityImpactExtrema extrema
	) {
		public RetuneCompressibilityImpactAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneCompressibilityImpactAudit audit() {
		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetAudit reynolds =
				PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.audit();
		List<RetuneCompressibilityImpactScenario> scenarios = reynolds.scenarios().stream()
				.map(scenario -> scenario(reynolds, scenario))
				.toList();
		List<RetuneCompressibilityImpactRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(reynolds, scenario.scenarioName()).stream())
				.toList();
		return new RetuneCompressibilityImpactAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				IMPACT_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				AMBIENT_TEMPERATURE_CELSIUS,
				DroneEnvironment.speedOfSoundMetersPerSecond(AMBIENT_TEMPERATURE_CELSIUS),
				COMPRESSIBILITY_ONSET_MACH,
				MAX_THRUST_LOSS_PERCENT_LIMIT,
				MAX_LOAD_FACTOR_LIMIT,
				MAX_REACTION_TORQUE_SCALE_LIMIT,
				MAX_VIBRATION_PROXY_LIMIT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static RetuneCompressibilityImpactScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune compressibility impact scenario: " + scenarioName));
	}

	public static RetuneCompressibilityImpactRow row(String scenarioName, String presetName) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune compressibility impact row: " + scenarioName + "/" + presetName));
	}

	private static RetuneCompressibilityImpactScenario scenario(
			PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetAudit reynolds,
			PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenario scenario
	) {
		List<RetuneCompressibilityImpactRow> rows = rowsForScenario(reynolds, scenario.scenarioName());
		return new RetuneCompressibilityImpactScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetuneCompressibilityImpactScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenarioSummary reynoldsSummary,
			List<RetuneCompressibilityImpactRow> rows
	) {
		int accepted = 0;
		int reviewRequired = 0;
		double maxIntensity = 0.0;
		double maxLoss = 0.0;
		double maxLoad = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneCompressibilityImpactRow row : rows) {
			if (row.compressibilityImpactAccepted()) {
				accepted++;
			}
			if (row.candidateMaxCompressibilityReviewRequired()) {
				reviewRequired++;
			}
			maxIntensity = Math.max(maxIntensity, row.candidateMaxCompressibilityIntensity());
			maxLoss = Math.max(maxLoss, row.candidateMaxThrustLossPercent());
			maxLoad = Math.max(maxLoad, row.candidateMaxLoadFactor());
			maxTorque = Math.max(maxTorque, row.candidateMaxReactionTorqueScale());
			maxVibration = Math.max(maxVibration, row.candidateMaxVibrationProxy());
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
		boolean available = reynoldsSummary.reynoldsBudgetAvailable() && !rows.isEmpty();
		boolean allAccepted = available && accepted == rows.size();
		return new RetuneCompressibilityImpactScenarioSummary(
				reynoldsSummary.validationAcceptanceReady(),
				reynoldsSummary.reynoldsBudgetAvailable(),
				available,
				rows.size(),
				accepted,
				reviewRequired,
				maxIntensity,
				maxLoss,
				maxLoad,
				maxTorque,
				maxVibration,
				config,
				runtime,
				gameplay,
				available ? "READY" : "BLOCKED",
				message(reynoldsSummary, available, allAccepted, reviewRequired),
				reynoldsSummary.sourceRuntimeInfo()
		);
	}

	private static List<RetuneCompressibilityImpactRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetAudit reynolds,
			String scenarioName
	) {
		return reynolds.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetRow::reynoldsBudgetAccepted)
				.map(PropellerArchiveRotorSpecRetuneCompressibilityImpactGate::row)
				.toList();
	}

	private static RetuneCompressibilityImpactRow row(
			PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetRow reynolds
	) {
		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetRow tipMach =
				PropellerArchiveRotorSpecRetuneTipMachBudgetGate.row(
						reynolds.scenarioName(),
						reynolds.presetName()
				);
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow impact =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						reynolds.scenarioName(),
						reynolds.presetName()
				);
		RotorSpec candidate = candidateRotor(reynolds.presetName());
		double candidateMaxOmega = omegaFromRpm(impact.candidateMaxRpm());
		double currentHoverIntensity = rotorCompressibilityIntensity(tipMach.currentHoverTipMach());
		double candidateHoverIntensity = rotorCompressibilityIntensity(tipMach.candidateHoverTipMach());
		double currentMaxIntensity = rotorCompressibilityIntensity(tipMach.currentMaxTipMach());
		double candidateMaxIntensity = rotorCompressibilityIntensity(tipMach.candidateMaxTipMach());
		double candidateHoverThrustScale = rotorCompressibilityThrustScale(tipMach.candidateHoverTipMach());
		double candidateMaxThrustScale = rotorCompressibilityThrustScale(tipMach.candidateMaxTipMach());
		double candidateMaxLossPercent = (1.0 - candidateMaxThrustScale) * 100.0;
		double candidateMaxLoad = rotorCompressibilityLoadFactor(tipMach.candidateMaxTipMach());
		double candidateMaxTorque = rotorCompressibilityReactionTorqueScale(tipMach.candidateMaxTipMach());
		double candidateMaxVibration = rotorCompressibilityVibration(
				candidate,
				candidateMaxOmega,
				tipMach.candidateMaxTipMach()
		);
		boolean impactWithinBudget = candidateMaxLossPercent <= MAX_THRUST_LOSS_PERCENT_LIMIT
				&& candidateMaxLoad <= MAX_LOAD_FACTOR_LIMIT
				&& candidateMaxTorque <= MAX_REACTION_TORQUE_SCALE_LIMIT
				&& candidateMaxVibration <= MAX_VIBRATION_PROXY_LIMIT;
		boolean accepted = reynolds.reynoldsBudgetAccepted() && impactWithinBudget;
		return new RetuneCompressibilityImpactRow(
				reynolds.scenarioName(),
				reynolds.presetName(),
				AMBIENT_TEMPERATURE_CELSIUS,
				tipMach.speedOfSoundMetersPerSecond(),
				tipMach.currentHoverTipMach(),
				tipMach.candidateHoverTipMach(),
				tipMach.currentMaxTipMach(),
				tipMach.candidateMaxTipMach(),
				currentHoverIntensity,
				candidateHoverIntensity,
				currentMaxIntensity,
				candidateMaxIntensity,
				candidateHoverThrustScale,
				candidateMaxThrustScale,
				candidateMaxLossPercent,
				candidateMaxLoad,
				candidateMaxTorque,
				candidateMaxVibration,
				MAX_THRUST_LOSS_PERCENT_LIMIT,
				MAX_LOAD_FACTOR_LIMIT,
				MAX_REACTION_TORQUE_SCALE_LIMIT,
				MAX_VIBRATION_PROXY_LIMIT,
				candidateHoverIntensity <= 1.0e-9,
				tipMach.candidateMaxCompressibilityReviewRequired(),
				impactWithinBudget,
				accepted,
				reynolds.reynoldsBudgetAccepted(),
				reynolds.manualPatchReviewAllowed(),
				false,
				false,
				false,
				accepted ? "READY" : "BLOCKED",
				accepted
						? (tipMach.candidateMaxCompressibilityReviewRequired()
								? "compressibility-impact-bounded-review-required"
								: "compressibility-impact-free")
						: "compressibility-impact-exceeds-budget"
		);
	}

	private static RetuneCompressibilityImpactExtrema extrema(
			List<RetuneCompressibilityImpactRow> rows,
			List<RetuneCompressibilityImpactScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxAccepted = 0;
		int maxReview = 0;
		double maxIntensity = 0.0;
		double maxLoss = 0.0;
		double maxLoad = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneCompressibilityImpactScenario scenario : scenarios) {
			RetuneCompressibilityImpactScenarioSummary summary = scenario.summary();
			if (summary.compressibilityImpactAvailable()) {
				available++;
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
			maxRows = Math.max(maxRows, summary.impactRowCount());
			maxAccepted = Math.max(maxAccepted, summary.compressibilityImpactAcceptedCount());
			maxReview = Math.max(maxReview, summary.compressibilityReviewRequiredCount());
			maxIntensity = Math.max(maxIntensity, summary.maxCandidateMaxCompressibilityIntensity());
			maxLoss = Math.max(maxLoss, summary.maxCandidateMaxThrustLossPercent());
			maxLoad = Math.max(maxLoad, summary.maxCandidateMaxLoadFactor());
			maxTorque = Math.max(maxTorque, summary.maxCandidateMaxReactionTorqueScale());
			maxVibration = Math.max(maxVibration, summary.maxCandidateMaxVibrationProxy());
		}
		return new RetuneCompressibilityImpactExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxAccepted,
				maxReview,
				maxIntensity,
				maxLoss,
				maxLoad,
				maxTorque,
				maxVibration,
				config,
				runtime,
				gameplay
		);
	}

	private static RotorSpec candidateRotor(String presetName) {
		DroneConfig config = PropellerArchiveRotorSpecConfigDeltaReport.configFor(presetName);
		RotorSpec current = config.rotors().get(0);
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta =
				PropellerArchiveRotorSpecConfigDeltaReport.row(presetName);
		return current
				.withRadiusMeters(delta.candidateRadiusMeters())
				.withBladePitchMeters(delta.candidateBladePitchMeters())
				.withBladeCount(delta.candidateBladeCount())
				.withThrustCoefficient(delta.candidateThrustCoefficient())
				.withYawTorquePerThrustMeter(delta.candidateYawTorquePerThrustMeters());
	}

	private static String message(
			PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenarioSummary reynoldsSummary,
			boolean available,
			boolean allAccepted,
			int reviewRequiredCount
	) {
		if (!available) {
			return reynoldsSummary.message();
		}
		if (!allAccepted) {
			return "compressibility-impact-exceeds-budget";
		}
		if (reviewRequiredCount > 0) {
			return "compressibility-impact-bounded-review-required";
		}
		return "compressibility-impact-free";
	}

	private static double omegaFromRpm(double rpm) {
		return rpm * RPM_TO_RADIANS_PER_SECOND;
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

	private static double rotorCompressibilityVibration(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double rotorTipMach
	) {
		double intensity = rotorCompressibilityIntensity(rotorTipMach);
		if (intensity <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		return MathUtil.clamp(0.22 * intensity * spinRatio, 0.0, 0.34);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
