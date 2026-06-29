package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneReynoldsBudgetGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Reynolds-Budget-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune Reynolds budget gate checks tip-Mach-accepted candidates against the runtime low-Reynolds proxy at a dry 15C sea-level reference; it only produces audit material and never emits a DroneConfig patch, runtime coupling, or gameplay auto-apply.";
	public static final double AMBIENT_TEMPERATURE_CELSIUS =
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.AMBIENT_TEMPERATURE_CELSIUS;
	public static final double AMBIENT_HUMIDITY = 0.0;
	public static final double AIR_DENSITY_RATIO = 1.0;
	public static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	public static final double REFERENCE_AIR_TEMPERATURE_KELVIN = 298.15;
	public static final double REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.837e-5;
	public static final double AIR_SUTHERLAND_CONSTANT_KELVIN = 110.4;
	public static final double HOVER_REYNOLDS_NUMBER_FLOOR = 30_000.0;
	public static final double LOW_REYNOLDS_INDEX_FLOOR = 1.05;
	public static final double LOW_REYNOLDS_LOSS_LIMIT = 0.02;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int BUDGET_ROW_COUNT = 2;
	public static final int SCENARIO_METRIC_ROW_COUNT = 15;
	public static final int SUMMARY_ROW_COUNT = 13;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ BUDGET_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double RPM_TO_RADIANS_PER_SECOND = 2.0 * Math.PI / 60.0;

	private PropellerArchiveRotorSpecRetuneReynoldsBudgetGate() {
	}

	public record RetuneReynoldsBudgetRow(
			String scenarioName,
			String presetName,
			double ambientTemperatureCelsius,
			double ambientHumidity,
			double airDensityRatio,
			double dynamicViscosityRatio,
			double candidateRepresentativeBladeChordMeters,
			double currentHoverReynoldsNumber,
			double candidateHoverReynoldsNumber,
			double currentMaxReynoldsNumber,
			double candidateMaxReynoldsNumber,
			double currentHoverLowReynoldsIndex,
			double candidateHoverLowReynoldsIndex,
			double currentMaxLowReynoldsIndex,
			double candidateMaxLowReynoldsIndex,
			double candidateHoverLowReynoldsLoss,
			double candidateMaxLowReynoldsLoss,
			double hoverReynoldsNumberFloor,
			double lowReynoldsIndexFloor,
			double lowReynoldsLossLimit,
			boolean candidateHoverReynoldsWithinBudget,
			boolean candidateHoverLowReynoldsIndexWithinBudget,
			boolean candidateMaxLowReynoldsIndexWithinBudget,
			boolean candidateLowReynoldsLossWithinBudget,
			boolean reynoldsBudgetAccepted,
			boolean tipMachCompressibilityReviewRequired,
			boolean manualPatchReviewAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetuneReynoldsBudgetScenarioSummary(
			boolean validationAcceptanceReady,
			boolean tipMachBudgetAvailable,
			boolean reynoldsBudgetAvailable,
			int budgetRowCount,
			int reynoldsBudgetAcceptedCount,
			int tipMachCompressibilityReviewRequiredCount,
			double minCandidateHoverReynoldsNumber,
			double minCandidateHoverLowReynoldsIndex,
			double minCandidateMaxLowReynoldsIndex,
			double maxCandidateLowReynoldsLoss,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneReynoldsBudgetScenario(
			String scenarioName,
			RetuneReynoldsBudgetScenarioSummary summary
	) {
	}

	public record RetuneReynoldsBudgetExtrema(
			int scenarioCount,
			int reynoldsBudgetAvailableScenarioCount,
			int totalBudgetRowCount,
			int maxBudgetRowCount,
			int maxReynoldsBudgetAcceptedCount,
			int maxTipMachCompressibilityReviewRequiredCount,
			double minCandidateHoverReynoldsNumber,
			double minCandidateHoverLowReynoldsIndex,
			double minCandidateMaxLowReynoldsIndex,
			double maxCandidateLowReynoldsLoss,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneReynoldsBudgetAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int budgetRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			double ambientTemperatureCelsius,
			double ambientHumidity,
			double airDensityRatio,
			double dynamicViscosityRatio,
			double hoverReynoldsNumberFloor,
			double lowReynoldsIndexFloor,
			double lowReynoldsLossLimit,
			List<RetuneReynoldsBudgetRow> rows,
			List<RetuneReynoldsBudgetScenario> scenarios,
			RetuneReynoldsBudgetExtrema extrema
	) {
		public RetuneReynoldsBudgetAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneReynoldsBudgetAudit audit() {
		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetAudit tipMach =
				PropellerArchiveRotorSpecRetuneTipMachBudgetGate.audit();
		List<RetuneReynoldsBudgetScenario> scenarios = tipMach.scenarios().stream()
				.map(scenario -> scenario(tipMach, scenario))
				.toList();
		List<RetuneReynoldsBudgetRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(tipMach, scenario.scenarioName()).stream())
				.toList();
		return new RetuneReynoldsBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				BUDGET_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				AMBIENT_TEMPERATURE_CELSIUS,
				AMBIENT_HUMIDITY,
				AIR_DENSITY_RATIO,
				airDynamicViscosityRatio(AMBIENT_TEMPERATURE_CELSIUS, AMBIENT_HUMIDITY),
				HOVER_REYNOLDS_NUMBER_FLOOR,
				LOW_REYNOLDS_INDEX_FLOOR,
				LOW_REYNOLDS_LOSS_LIMIT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static RetuneReynoldsBudgetScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune Reynolds budget scenario: " + scenarioName));
	}

	public static RetuneReynoldsBudgetRow row(String scenarioName, String presetName) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune Reynolds budget row: " + scenarioName + "/" + presetName));
	}

	private static RetuneReynoldsBudgetScenario scenario(
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetAudit tipMach,
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenario scenario
	) {
		List<RetuneReynoldsBudgetRow> rows = rowsForScenario(tipMach, scenario.scenarioName());
		return new RetuneReynoldsBudgetScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetuneReynoldsBudgetScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenarioSummary tipSummary,
			List<RetuneReynoldsBudgetRow> rows
	) {
		int accepted = 0;
		int compressibility = 0;
		double minHoverRe = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double minHoverIndex = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double minMaxIndex = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneReynoldsBudgetRow row : rows) {
			if (row.reynoldsBudgetAccepted()) {
				accepted++;
			}
			if (row.tipMachCompressibilityReviewRequired()) {
				compressibility++;
			}
			minHoverRe = Math.min(minHoverRe, row.candidateHoverReynoldsNumber());
			minHoverIndex = Math.min(minHoverIndex, row.candidateHoverLowReynoldsIndex());
			minMaxIndex = Math.min(minMaxIndex, row.candidateMaxLowReynoldsIndex());
			maxLoss = Math.max(maxLoss, Math.max(row.candidateHoverLowReynoldsLoss(),
					row.candidateMaxLowReynoldsLoss()));
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
		boolean available = tipSummary.tipMachBudgetAvailable() && !rows.isEmpty();
		boolean allAccepted = available && accepted == rows.size();
		return new RetuneReynoldsBudgetScenarioSummary(
				tipSummary.validationAcceptanceReady(),
				tipSummary.tipMachBudgetAvailable(),
				available,
				rows.size(),
				accepted,
				compressibility,
				minHoverRe,
				minHoverIndex,
				minMaxIndex,
				maxLoss,
				config,
				runtime,
				gameplay,
				available ? "READY" : "BLOCKED",
				message(tipSummary, available, allAccepted, compressibility),
				tipSummary.sourceRuntimeInfo()
		);
	}

	private static List<RetuneReynoldsBudgetRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetAudit tipMach,
			String scenarioName
	) {
		return tipMach.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetRow::tipMachBudgetAccepted)
				.map(PropellerArchiveRotorSpecRetuneReynoldsBudgetGate::row)
				.toList();
	}

	private static RetuneReynoldsBudgetRow row(
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetRow tipMach
	) {
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow impact =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						tipMach.scenarioName(),
						tipMach.presetName()
				);
		DroneConfig config = PropellerArchiveRotorSpecConfigDeltaReport.configFor(tipMach.presetName());
		RotorSpec currentRotor = config.rotors().get(0);
		RotorSpec candidateRotor = candidateRotor(tipMach.presetName());
		double currentHoverOmega = omegaFromRpm(impact.currentHoverRpm());
		double candidateHoverOmega = omegaFromRpm(impact.candidateHoverRpm());
		double currentMaxOmega = omegaFromRpm(impact.currentMaxRpm());
		double candidateMaxOmega = omegaFromRpm(impact.candidateMaxRpm());
		double viscosityRatio = airDynamicViscosityRatio(AMBIENT_TEMPERATURE_CELSIUS, AMBIENT_HUMIDITY);
		double currentHoverReynolds = rotorReynoldsNumber(currentRotor, currentHoverOmega);
		double candidateHoverReynolds = rotorReynoldsNumber(candidateRotor, candidateHoverOmega);
		double currentMaxReynolds = rotorReynoldsNumber(currentRotor, currentMaxOmega);
		double candidateMaxReynolds = rotorReynoldsNumber(candidateRotor, candidateMaxOmega);
		double currentHoverIndex = rotorLowReynoldsIndex(currentRotor, currentHoverOmega);
		double candidateHoverIndex = rotorLowReynoldsIndex(candidateRotor, candidateHoverOmega);
		double currentMaxIndex = rotorLowReynoldsIndex(currentRotor, currentMaxOmega);
		double candidateMaxIndex = rotorLowReynoldsIndex(candidateRotor, candidateMaxOmega);
		double candidateHoverLoss = rotorLowReynoldsLoss(candidateRotor, candidateHoverOmega);
		double candidateMaxLoss = rotorLowReynoldsLoss(candidateRotor, candidateMaxOmega);
		boolean hoverReynoldsWithinBudget = candidateHoverReynolds >= HOVER_REYNOLDS_NUMBER_FLOOR;
		boolean hoverIndexWithinBudget = candidateHoverIndex >= LOW_REYNOLDS_INDEX_FLOOR;
		boolean maxIndexWithinBudget = candidateMaxIndex >= LOW_REYNOLDS_INDEX_FLOOR;
		boolean lossWithinBudget = Math.max(candidateHoverLoss, candidateMaxLoss) <= LOW_REYNOLDS_LOSS_LIMIT;
		boolean accepted = hoverReynoldsWithinBudget
				&& hoverIndexWithinBudget
				&& maxIndexWithinBudget
				&& lossWithinBudget;
		return new RetuneReynoldsBudgetRow(
				tipMach.scenarioName(),
				tipMach.presetName(),
				AMBIENT_TEMPERATURE_CELSIUS,
				AMBIENT_HUMIDITY,
				AIR_DENSITY_RATIO,
				viscosityRatio,
				candidateRotor.representativeBladeChordMeters(),
				currentHoverReynolds,
				candidateHoverReynolds,
				currentMaxReynolds,
				candidateMaxReynolds,
				currentHoverIndex,
				candidateHoverIndex,
				currentMaxIndex,
				candidateMaxIndex,
				candidateHoverLoss,
				candidateMaxLoss,
				HOVER_REYNOLDS_NUMBER_FLOOR,
				LOW_REYNOLDS_INDEX_FLOOR,
				LOW_REYNOLDS_LOSS_LIMIT,
				hoverReynoldsWithinBudget,
				hoverIndexWithinBudget,
				maxIndexWithinBudget,
				lossWithinBudget,
				accepted,
				tipMach.candidateMaxCompressibilityReviewRequired(),
				tipMach.manualPatchReviewAllowed(),
				false,
				false,
				false,
				accepted ? "READY" : "BLOCKED",
				accepted
						? (tipMach.candidateMaxCompressibilityReviewRequired()
								? "reynolds-budget-ready-tip-mach-compressibility-review-carried"
								: "reynolds-budget-ready")
						: "reynolds-budget-review-required"
		);
	}

	private static RetuneReynoldsBudgetExtrema extrema(
			List<RetuneReynoldsBudgetRow> rows,
			List<RetuneReynoldsBudgetScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxAccepted = 0;
		int maxCompressibility = 0;
		double minHoverRe = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double minHoverIndex = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double minMaxIndex = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneReynoldsBudgetScenario scenario : scenarios) {
			RetuneReynoldsBudgetScenarioSummary summary = scenario.summary();
			if (summary.reynoldsBudgetAvailable()) {
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
			maxRows = Math.max(maxRows, summary.budgetRowCount());
			maxAccepted = Math.max(maxAccepted, summary.reynoldsBudgetAcceptedCount());
			maxCompressibility = Math.max(maxCompressibility, summary.tipMachCompressibilityReviewRequiredCount());
			if (summary.reynoldsBudgetAvailable()) {
				minHoverRe = Math.min(minHoverRe, summary.minCandidateHoverReynoldsNumber());
				minHoverIndex = Math.min(minHoverIndex, summary.minCandidateHoverLowReynoldsIndex());
				minMaxIndex = Math.min(minMaxIndex, summary.minCandidateMaxLowReynoldsIndex());
				maxLoss = Math.max(maxLoss, summary.maxCandidateLowReynoldsLoss());
			}
		}
		return new RetuneReynoldsBudgetExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxAccepted,
				maxCompressibility,
				minHoverRe,
				minHoverIndex,
				minMaxIndex,
				maxLoss,
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
			PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenarioSummary tipSummary,
			boolean available,
			boolean allAccepted,
			int compressibilityReviewCount
	) {
		if (!available) {
			return tipSummary.message();
		}
		if (!allAccepted) {
			return "reynolds-budget-review-required";
		}
		if (compressibilityReviewCount > 0) {
			return "reynolds-budget-ready-tip-mach-compressibility-review-carried";
		}
		return "reynolds-budget-ready";
	}

	private static double omegaFromRpm(double rpm) {
		return rpm * RPM_TO_RADIANS_PER_SECOND;
	}

	private static double rotorReynoldsNumber(RotorSpec rotor, double omegaRadiansPerSecond) {
		double stationSpeed = 0.75 * rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		if (stationSpeed <= 1.0e-9) {
			return 0.0;
		}
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.max(0.0, AIR_DENSITY_RATIO);
		double dynamicViscosity = REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS
				* airDynamicViscosityRatio(AMBIENT_TEMPERATURE_CELSIUS, AMBIENT_HUMIDITY);
		return MathUtil.clamp(
				density * stationSpeed * rotor.representativeBladeChordMeters()
						/ Math.max(1.0e-9, dynamicViscosity),
				0.0,
				2.0e6
		);
	}

	private static double rotorLowReynoldsIndex(RotorSpec rotor, double omegaRadiansPerSecond) {
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double densityViscosityRatio = MathUtil.clamp(
				Math.max(0.0, AIR_DENSITY_RATIO)
						/ airDynamicViscosityRatio(AMBIENT_TEMPERATURE_CELSIUS, AMBIENT_HUMIDITY),
				0.20,
				1.90
		);
		double chordScale = MathUtil.clamp(
				rotor.representativeBladeChordMeters()
						/ (0.0635 * RotorSpec.DEFAULT_REPRESENTATIVE_CHORD_TO_RADIUS_RATIO),
				0.24,
				3.60
		);
		return densityViscosityRatio
				* chordScale
				* MathUtil.clamp(tipSpeed / 34.0, 0.0, 2.8);
	}

	private static double rotorLowReynoldsLoss(RotorSpec rotor, double omegaRadiansPerSecond) {
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.10
		);
		if (spinRatio <= 0.08) {
			return 0.0;
		}
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.32, 3.0);
		double smallPropFactor = 1.0 - smoothStep(0.62, 0.96, radiusScale);
		if (smallPropFactor <= 1.0e-6) {
			return 0.0;
		}
		double lowReynolds = 1.0 - smoothStep(0.52, 1.05,
				rotorLowReynoldsIndex(rotor, omegaRadiansPerSecond));
		return MathUtil.clamp(
				lowReynolds * smallPropFactor * smoothStep(0.10, 0.34, spinRatio),
				0.0,
				1.0
		);
	}

	private static double airDynamicViscosityRatio(double ambientTemperatureCelsius, double ambientHumidity) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = 25.0;
		}
		double temperatureKelvin = MathUtil.clamp(ambientTemperatureCelsius + 273.15, 233.15, 338.15);
		double ratio = Math.pow(temperatureKelvin / REFERENCE_AIR_TEMPERATURE_KELVIN, 1.5)
				* (REFERENCE_AIR_TEMPERATURE_KELVIN + AIR_SUTHERLAND_CONSTANT_KELVIN)
				/ (temperatureKelvin + AIR_SUTHERLAND_CONSTANT_KELVIN);
		return MathUtil.clamp(
				ratio * DroneEnvironment.moistAirDynamicViscosityMultiplier(
						ambientTemperatureCelsius,
						ambientHumidity
				),
				0.64,
				1.20
		);
	}

	private static double rotorTipSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters();
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
