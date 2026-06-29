package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Contract-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control contract maps accepted cold-air max-RPM scale targets onto the control-layer target-omega boundary only; it does not mutate RotorSpec, patch DroneConfig, enable runtime coupling, export playable references, or apply gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int CONTRACT_ROW_COUNT = 2;
	public static final int SCENARIO_METRIC_ROW_COUNT = 18;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ CONTRACT_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double RADIANS_PER_SECOND_TO_RPM = 60.0 / (Math.PI * 2.0);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract() {
	}

	public record DerateControlContractRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			boolean postDeratePhysicalBudgetAccepted,
			boolean manualDerateReviewReady,
			double rotorSpecMaxOmegaRadiansPerSecond,
			double rotorSpecMaxRpm,
			double targetMaxRpmScale,
			double contractedMaxOmegaRadiansPerSecond,
			double contractedMaxRpm,
			double requiredMaxRpmDeratePercent,
			double equivalentMaxThrustScale,
			double equivalentMaxThrustLossPercent,
			double targetMaxTipMach,
			String controlBoundary,
			boolean controlLayerClampRequired,
			boolean rotorSpecPatchAllowed,
			boolean droneConfigPatchAllowed,
			boolean runtimeImplementationReady,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DerateControlContractScenarioSummary(
			boolean postDeratePhysicalBudgetAccepted,
			boolean derateControlContractAvailable,
			int contractRowCount,
			int controlLayerClampRequiredCount,
			int runtimeImplementationReadyCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double maxEquivalentMaxThrustLossPercent,
			double minContractedMaxRpm,
			double maxContractedMaxRpm,
			int rotorSpecPatchAllowedCount,
			int droneConfigPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int playableReferenceAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String controlBoundary,
			String status,
			String message
	) {
	}

	public record DerateControlContractScenario(
			String scenarioName,
			DerateControlContractScenarioSummary summary
	) {
	}

	public record DerateControlContractExtrema(
			int scenarioCount,
			int derateControlContractAvailableScenarioCount,
			int totalContractRowCount,
			int maxContractRowCount,
			int maxControlLayerClampRequiredCount,
			int maxRuntimeImplementationReadyCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double maxEquivalentMaxThrustLossPercent,
			double minContractedMaxRpm,
			double maxContractedMaxRpm,
			int rotorSpecPatchAllowedScenarioCount,
			int droneConfigPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int playableReferenceAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record DerateControlContractAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int contractRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<DerateControlContractRow> rows,
			List<DerateControlContractScenario> scenarios,
			DerateControlContractExtrema extrema
	) {
		public DerateControlContractAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static DerateControlContractAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceAudit budget =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate.audit();
		List<DerateControlContractScenario> scenarios = budget.scenarios().stream()
				.map(scenario -> scenario(budget, scenario))
				.toList();
		List<DerateControlContractRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(budget, scenario.scenarioName()).stream())
				.toList();
		return new DerateControlContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				CONTRACT_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static DerateControlContractScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate control contract scenario: " + scenarioName));
	}

	public static DerateControlContractRow row(
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
						"unknown RotorSpec retune ambient derate control contract row: "
								+ scenarioName + "/" + presetName + "/" + ambientCaseName));
	}

	private static DerateControlContractScenario scenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
					.DeratePhysicalBudgetAcceptanceAudit budget,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
					.DeratePhysicalBudgetAcceptanceScenario scenario
	) {
		List<DerateControlContractRow> rows = rowsForScenario(budget, scenario.scenarioName());
		return new DerateControlContractScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static DerateControlContractScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
					.DeratePhysicalBudgetAcceptanceScenarioSummary budgetSummary,
			List<DerateControlContractRow> rows
	) {
		int clamps = 0;
		int runtimeReady = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxThrustLoss = 0.0;
		double minRpm = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxRpm = 0.0;
		int rotorPatch = 0;
		int configPatch = 0;
		int runtime = 0;
		int playable = 0;
		int gameplay = 0;
		for (DerateControlContractRow row : rows) {
			if (row.controlLayerClampRequired()) {
				clamps++;
			}
			if (row.runtimeImplementationReady()) {
				runtimeReady++;
			}
			maxDerate = Math.max(maxDerate, row.requiredMaxRpmDeratePercent());
			minScale = Math.min(minScale, row.targetMaxRpmScale());
			maxThrustLoss = Math.max(maxThrustLoss, row.equivalentMaxThrustLossPercent());
			minRpm = Math.min(minRpm, row.contractedMaxRpm());
			maxRpm = Math.max(maxRpm, row.contractedMaxRpm());
			if (row.rotorSpecPatchAllowed()) {
				rotorPatch++;
			}
			if (row.droneConfigPatchAllowed()) {
				configPatch++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.playableReferenceAllowed()) {
				playable++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		boolean available = budgetSummary.deratePhysicalBudgetAvailable() && !rows.isEmpty();
		return new DerateControlContractScenarioSummary(
				budgetSummary.postDeratePhysicalBudgetAcceptedCount() > 0,
				available,
				rows.size(),
				clamps,
				runtimeReady,
				maxDerate,
				rows.isEmpty() ? 0.0 : minScale,
				maxThrustLoss,
				rows.isEmpty() ? 0.0 : minRpm,
				maxRpm,
				rotorPatch,
				configPatch,
				runtime,
				playable,
				gameplay,
				"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				available ? "REVIEW_READY" : "BLOCKED",
				available
						? "control-contract-ready-runtime-hook-not-enabled"
						: budgetSummary.message()
		);
	}

	private static List<DerateControlContractRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
					.DeratePhysicalBudgetAcceptanceAudit budget,
			String scenarioName
	) {
		return budget.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
						.DeratePhysicalBudgetAcceptanceRow::postDeratePhysicalBudgetAccepted)
				.map(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract::row)
				.toList();
	}

	private static DerateControlContractRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
					.DeratePhysicalBudgetAcceptanceRow budget
	) {
		RotorSpec rotor = configForPreset(budget.presetName()).rotors().get(0);
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		double maxRpm = maxOmega * RADIANS_PER_SECOND_TO_RPM;
		double scale = budget.targetMaxRpmScale();
		double contractedOmega = maxOmega * scale;
		double contractedRpm = maxRpm * scale;
		double equivalentThrustScale = scale * scale;
		double equivalentThrustLossPercent = (1.0 - equivalentThrustScale) * 100.0;
		return new DerateControlContractRow(
				budget.scenarioName(),
				budget.presetName(),
				budget.ambientCaseName(),
				budget.postDeratePhysicalBudgetAccepted(),
				budget.manualDerateReviewReady(),
				maxOmega,
				maxRpm,
				scale,
				contractedOmega,
				contractedRpm,
				budget.requiredMaxRpmDeratePercent(),
				equivalentThrustScale,
				equivalentThrustLossPercent,
				budget.targetMaxTipMach(),
				"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				true,
				false,
				false,
				false,
				false,
				false,
				false,
				"REVIEW_READY",
				"control-contract-ready-runtime-hook-not-enabled"
		);
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			default -> throw new IllegalArgumentException("unsupported derate control contract preset: " + presetName);
		};
	}

	private static DerateControlContractExtrema extrema(
			List<DerateControlContractRow> rows,
			List<DerateControlContractScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxClamps = 0;
		int maxRuntimeReady = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxThrustLoss = 0.0;
		double minRpm = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxRpm = 0.0;
		int rotorPatch = 0;
		int configPatch = 0;
		int runtime = 0;
		int playable = 0;
		int gameplay = 0;
		for (DerateControlContractScenario scenario : scenarios) {
			DerateControlContractScenarioSummary summary = scenario.summary();
			if (summary.derateControlContractAvailable()) {
				available++;
			}
			if (summary.rotorSpecPatchAllowedCount() > 0) {
				rotorPatch++;
			}
			if (summary.droneConfigPatchAllowedCount() > 0) {
				configPatch++;
			}
			if (summary.runtimeCouplingAllowedCount() > 0) {
				runtime++;
			}
			if (summary.playableReferenceAllowedCount() > 0) {
				playable++;
			}
			if (summary.gameplayAutoApplyAllowedCount() > 0) {
				gameplay++;
			}
			maxRows = Math.max(maxRows, summary.contractRowCount());
			maxClamps = Math.max(maxClamps, summary.controlLayerClampRequiredCount());
			maxRuntimeReady = Math.max(maxRuntimeReady, summary.runtimeImplementationReadyCount());
			if (summary.derateControlContractAvailable()) {
				maxDerate = Math.max(maxDerate, summary.maxRequiredMaxRpmDeratePercent());
				minScale = Math.min(minScale, summary.minTargetMaxRpmScale());
				maxThrustLoss = Math.max(maxThrustLoss, summary.maxEquivalentMaxThrustLossPercent());
				minRpm = Math.min(minRpm, summary.minContractedMaxRpm());
				maxRpm = Math.max(maxRpm, summary.maxContractedMaxRpm());
			}
		}
		return new DerateControlContractExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxClamps,
				maxRuntimeReady,
				maxDerate,
				rows.isEmpty() ? 0.0 : minScale,
				maxThrustLoss,
				rows.isEmpty() ? 0.0 : minRpm,
				maxRpm,
				rotorPatch,
				configPatch,
				runtime,
				playable,
				gameplay
		);
	}
}
