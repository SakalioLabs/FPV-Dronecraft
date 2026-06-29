package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Plan-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate plan converts failed cold-air sensitivity rows into offline max-tip-Mach and max-RPM scale targets only; it never applies DroneConfig changes, runtime coupling, playable reference export, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.SCENARIO_SAMPLE_COUNT;
	public static final int DERATE_ROW_COUNT = 2;
	public static final int SCENARIO_METRIC_ROW_COUNT = 18;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ DERATE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan() {
	}

	public record RetuneAmbientCompressibilityDerateRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double ambientTemperatureCelsius,
			double candidateMaxTipMach,
			double candidateMaxThrustLossPercent,
			double candidateMaxLoadFactor,
			double candidateMaxReactionTorqueScale,
			double candidateMaxVibrationProxy,
			double targetMaxTipMach,
			double targetMaxTipMachMargin,
			double targetMaxRpmScale,
			double requiredMaxRpmDeratePercent,
			String limitingBudget,
			double postDerateThrustLossPercent,
			double postDerateLoadFactor,
			double postDerateReactionTorqueScale,
			double postDerateVibrationProxy,
			double maxTipMachLimit,
			double maxThrustLossPercentLimit,
			double maxLoadFactorLimit,
			double maxReactionTorqueScaleLimit,
			double maxVibrationProxyLimit,
			boolean ambientSensitivityAccepted,
			boolean derateTargetWithinBudget,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetuneAmbientCompressibilityDerateScenarioSummary(
			boolean validationAcceptanceReady,
			boolean ambientSensitivityAvailable,
			boolean deratePlanAvailable,
			int derateRowCount,
			int coldAirFailedRowCount,
			int derateTargetWithinBudgetCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double minTargetMaxTipMach,
			double maxPostDerateThrustLossPercent,
			double maxPostDerateReactionTorqueScale,
			double maxPostDerateVibrationProxy,
			int playableReferenceAllowedCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneAmbientCompressibilityDerateScenario(
			String scenarioName,
			RetuneAmbientCompressibilityDerateScenarioSummary summary
	) {
	}

	public record RetuneAmbientCompressibilityDerateExtrema(
			int scenarioCount,
			int deratePlanAvailableScenarioCount,
			int totalDerateRowCount,
			int maxDerateRowCount,
			int maxColdAirFailedRowCount,
			int maxDerateTargetWithinBudgetCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double minTargetMaxTipMach,
			double maxPostDerateThrustLossPercent,
			double maxPostDerateReactionTorqueScale,
			double maxPostDerateVibrationProxy,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneAmbientCompressibilityDerateAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int derateRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<RetuneAmbientCompressibilityDerateRow> rows,
			List<RetuneAmbientCompressibilityDerateScenario> scenarios,
			RetuneAmbientCompressibilityDerateExtrema extrema
	) {
		public RetuneAmbientCompressibilityDerateAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneAmbientCompressibilityDerateAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityAudit sensitivity =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.audit();
		List<RetuneAmbientCompressibilityDerateScenario> scenarios = sensitivity.scenarios().stream()
				.map(scenario -> scenario(sensitivity, scenario))
				.toList();
		List<RetuneAmbientCompressibilityDerateRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(sensitivity, scenario.scenarioName()).stream())
				.toList();
		return new RetuneAmbientCompressibilityDerateAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				DERATE_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static RetuneAmbientCompressibilityDerateScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient compressibility derate scenario: " + scenarioName));
	}

	public static RetuneAmbientCompressibilityDerateRow row(
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
						"unknown RotorSpec retune ambient compressibility derate row: "
								+ scenarioName + "/" + presetName + "/" + ambientCaseName));
	}

	private static RetuneAmbientCompressibilityDerateScenario scenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityAudit sensitivity,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenario scenario
	) {
		List<RetuneAmbientCompressibilityDerateRow> rows = rowsForScenario(sensitivity, scenario.scenarioName());
		return new RetuneAmbientCompressibilityDerateScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetuneAmbientCompressibilityDerateScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenarioSummary sensitivitySummary,
			List<RetuneAmbientCompressibilityDerateRow> rows
	) {
		int targetWithin = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double minTargetMach = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneAmbientCompressibilityDerateRow row : rows) {
			if (row.derateTargetWithinBudget()) {
				targetWithin++;
			}
			maxDerate = Math.max(maxDerate, row.requiredMaxRpmDeratePercent());
			minScale = Math.min(minScale, row.targetMaxRpmScale());
			minTargetMach = Math.min(minTargetMach, row.targetMaxTipMach());
			maxLoss = Math.max(maxLoss, row.postDerateThrustLossPercent());
			maxTorque = Math.max(maxTorque, row.postDerateReactionTorqueScale());
			maxVibration = Math.max(maxVibration, row.postDerateVibrationProxy());
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
		boolean available = sensitivitySummary.ambientSensitivityAvailable() && !rows.isEmpty();
		return new RetuneAmbientCompressibilityDerateScenarioSummary(
				sensitivitySummary.validationAcceptanceReady(),
				sensitivitySummary.ambientSensitivityAvailable(),
				available,
				rows.size(),
				rows.size(),
				targetWithin,
				maxDerate,
				minScale,
				minTargetMach,
				maxLoss,
				maxTorque,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay,
				status(sensitivitySummary, rows),
				message(sensitivitySummary, rows),
				sensitivitySummary.sourceRuntimeInfo()
		);
	}

	private static List<RetuneAmbientCompressibilityDerateRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityAudit sensitivity,
			String scenarioName
	) {
		return sensitivity.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(row -> !row.ambientSensitivityAccepted())
				.map(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan::row)
				.toList();
	}

	private static RetuneAmbientCompressibilityDerateRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityRow sensitivity
	) {
		double allowedIntensity = allowedCompressibilityIntensity(sensitivity);
		double targetMach = Math.min(
				Math.min(sensitivity.maxTipMachLimit(), sensitivity.seriousLossMach()),
				machFromCompressibilityIntensity(allowedIntensity)
		);
		double scale = MathUtil.clamp(targetMach / sensitivity.candidateMaxTipMach(), 0.0, 1.0);
		double deratePercent = (1.0 - scale) * 100.0;
		double postLoss = (1.0 - rotorCompressibilityThrustScale(targetMach)) * 100.0;
		double postLoad = rotorCompressibilityLoadFactor(targetMach);
		double postTorque = rotorCompressibilityReactionTorqueScale(targetMach);
		double postVibration = rotorCompressibilityVibrationProxy(
				sensitivity.candidateMaxCompressibilityIntensity(),
				sensitivity.candidateMaxVibrationProxy(),
				targetMach
		);
		boolean targetWithin = targetMach <= sensitivity.maxTipMachLimit()
				&& targetMach < sensitivity.seriousLossMach()
				&& postLoss <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_THRUST_LOSS_PERCENT_LIMIT
				&& postLoad <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_LOAD_FACTOR_LIMIT
				&& postTorque <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_REACTION_TORQUE_SCALE_LIMIT
				&& postVibration <= PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_VIBRATION_PROXY_LIMIT;
		return new RetuneAmbientCompressibilityDerateRow(
				sensitivity.scenarioName(),
				sensitivity.presetName(),
				sensitivity.ambientCaseName(),
				sensitivity.ambientTemperatureCelsius(),
				sensitivity.candidateMaxTipMach(),
				sensitivity.candidateMaxThrustLossPercent(),
				sensitivity.candidateMaxLoadFactor(),
				sensitivity.candidateMaxReactionTorqueScale(),
				sensitivity.candidateMaxVibrationProxy(),
				targetMach,
				sensitivity.maxTipMachLimit() - targetMach,
				scale,
				deratePercent,
				limitingBudget(sensitivity),
				postLoss,
				postLoad,
				postTorque,
				postVibration,
				sensitivity.maxTipMachLimit(),
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_THRUST_LOSS_PERCENT_LIMIT,
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_LOAD_FACTOR_LIMIT,
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_REACTION_TORQUE_SCALE_LIMIT,
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_VIBRATION_PROXY_LIMIT,
				sensitivity.ambientSensitivityAccepted(),
				targetWithin,
				false,
				false,
				false,
				false,
				"PENDING_REVIEW",
				targetWithin
						? "cold-air-derate-target-planned"
						: "cold-air-derate-target-incomplete"
		);
	}

	private static RetuneAmbientCompressibilityDerateExtrema extrema(
			List<RetuneAmbientCompressibilityDerateRow> rows,
			List<RetuneAmbientCompressibilityDerateScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxFailed = 0;
		int maxWithin = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double minTargetMach = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneAmbientCompressibilityDerateScenario scenario : scenarios) {
			RetuneAmbientCompressibilityDerateScenarioSummary summary = scenario.summary();
			if (summary.deratePlanAvailable()) {
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
			maxRows = Math.max(maxRows, summary.derateRowCount());
			maxFailed = Math.max(maxFailed, summary.coldAirFailedRowCount());
			maxWithin = Math.max(maxWithin, summary.derateTargetWithinBudgetCount());
			if (summary.deratePlanAvailable()) {
				maxDerate = Math.max(maxDerate, summary.maxRequiredMaxRpmDeratePercent());
				minScale = Math.min(minScale, summary.minTargetMaxRpmScale());
				minTargetMach = Math.min(minTargetMach, summary.minTargetMaxTipMach());
				maxLoss = Math.max(maxLoss, summary.maxPostDerateThrustLossPercent());
				maxTorque = Math.max(maxTorque, summary.maxPostDerateReactionTorqueScale());
				maxVibration = Math.max(maxVibration, summary.maxPostDerateVibrationProxy());
			}
		}
		return new RetuneAmbientCompressibilityDerateExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxFailed,
				maxWithin,
				maxDerate,
				minScale,
				minTargetMach,
				maxLoss,
				maxTorque,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay
		);
	}

	private static String status(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenarioSummary sensitivitySummary,
			List<RetuneAmbientCompressibilityDerateRow> rows
	) {
		if (!sensitivitySummary.ambientSensitivityAvailable()) {
			return "BLOCKED";
		}
		return rows.isEmpty() ? "READY" : "PENDING_REVIEW";
	}

	private static String message(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenarioSummary sensitivitySummary,
			List<RetuneAmbientCompressibilityDerateRow> rows
	) {
		if (!sensitivitySummary.ambientSensitivityAvailable()) {
			return sensitivitySummary.message();
		}
		return rows.isEmpty()
				? "ambient-compressibility-derate-not-required"
				: "cold-air-derate-target-planned";
	}

	private static double allowedCompressibilityIntensity(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityRow sensitivity
	) {
		double thrustIntensity = PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_THRUST_LOSS_PERCENT_LIMIT
				/ 20.0;
		double loadIntensity = PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_LOAD_FACTOR_LIMIT / 0.42;
		double torqueIntensity = torqueIntensityLimit(
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_REACTION_TORQUE_SCALE_LIMIT
		);
		double vibrationIntensity = vibrationIntensityLimit(
				sensitivity.candidateMaxCompressibilityIntensity(),
				sensitivity.candidateMaxVibrationProxy()
		);
		return MathUtil.clamp(
				Math.min(
						Math.min(thrustIntensity, loadIntensity),
						Math.min(torqueIntensity, vibrationIntensity)
				),
				0.0,
				1.0
		);
	}

	private static String limitingBudget(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityRow sensitivity
	) {
		double allowed = allowedCompressibilityIntensity(sensitivity);
		double torque = torqueIntensityLimit(
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_REACTION_TORQUE_SCALE_LIMIT
		);
		if (Math.abs(allowed - torque) <= 1.0e-12) {
			return "reaction_torque_scale_limit";
		}
		double thrust = PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_THRUST_LOSS_PERCENT_LIMIT / 20.0;
		if (Math.abs(allowed - thrust) <= 1.0e-12) {
			return "thrust_loss_percent_limit";
		}
		double load = PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_LOAD_FACTOR_LIMIT / 0.42;
		if (Math.abs(allowed - load) <= 1.0e-12) {
			return "load_factor_limit";
		}
		return "vibration_proxy_limit";
	}

	private static double torqueIntensityLimit(double torqueScaleLimit) {
		double a = 0.10;
		double b = 0.32;
		double c = 1.0 - torqueScaleLimit;
		double discriminant = b * b - 4.0 * a * c;
		if (discriminant < 0.0) {
			return 0.0;
		}
		return MathUtil.clamp((-b + Math.sqrt(discriminant)) / (2.0 * a), 0.0, 1.0);
	}

	private static double vibrationIntensityLimit(double referenceIntensity, double referenceVibrationProxy) {
		if (referenceIntensity <= 1.0e-9 || referenceVibrationProxy <= 1.0e-9) {
			return 1.0;
		}
		double spinRatio = MathUtil.clamp(referenceVibrationProxy / (0.22 * referenceIntensity), 0.0, 1.0);
		if (spinRatio <= 1.0e-9) {
			return 1.0;
		}
		return MathUtil.clamp(
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.MAX_VIBRATION_PROXY_LIMIT
						/ (0.22 * spinRatio),
				0.0,
				1.0
		);
	}

	private static double rotorCompressibilityIntensity(double rotorTipMach) {
		return smoothStep(0.46, 0.82, rotorTipMach);
	}

	private static double machFromCompressibilityIntensity(double intensity) {
		double low = 0.46;
		double high = 0.82;
		for (int i = 0; i < 80; i++) {
			double mid = (low + high) * 0.5;
			if (rotorCompressibilityIntensity(mid) < intensity) {
				low = mid;
			} else {
				high = mid;
			}
		}
		return (low + high) * 0.5;
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
			double referenceIntensity,
			double referenceVibrationProxy,
			double rotorTipMach
	) {
		if (referenceIntensity <= 1.0e-9 || referenceVibrationProxy <= 1.0e-9) {
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
