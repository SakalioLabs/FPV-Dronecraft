package com.tenicana.dronecraft.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Physical-Budget-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate physical budget acceptance verifies accepted cold-air derate review rows against post-derate thrust, load, reaction-torque, vibration, and max-tip-Mach limits only; it never exports playable references, patches DroneConfig, enables runtime coupling, or applies gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int BUDGET_ROW_COUNT = 2;
	public static final int SCENARIO_METRIC_ROW_COUNT = 20;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ BUDGET_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate() {
	}

	public record DeratePhysicalBudgetAcceptanceRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			boolean derateValidationAcceptanceReady,
			boolean manualDerateReviewAllowed,
			boolean derateManualReviewMaterialAvailable,
			boolean rpmControlReviewComplete,
			boolean physicsBudgetReviewComplete,
			double targetMaxTipMach,
			double targetMaxRpmScale,
			double requiredMaxRpmDeratePercent,
			String limitingBudget,
			double maxTipMachLimit,
			double postDerateThrustLossPercent,
			double maxThrustLossPercentLimit,
			double postDerateLoadFactor,
			double maxLoadFactorLimit,
			double postDerateReactionTorqueScale,
			double maxReactionTorqueScaleLimit,
			double postDerateVibrationProxy,
			double maxVibrationProxyLimit,
			boolean derateTargetWithinBudget,
			boolean postDeratePhysicalBudgetAccepted,
			boolean manualDerateReviewReady,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DeratePhysicalBudgetAcceptanceScenarioSummary(
			boolean derateValidationAcceptanceReady,
			boolean manualDerateReviewAllowed,
			boolean deratePhysicalBudgetAvailable,
			int budgetRowCount,
			int postDeratePhysicalBudgetAcceptedCount,
			int manualDerateReviewReadyCount,
			int rpmControlReviewCompleteCount,
			int physicsBudgetReviewCompleteCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double maxPostDerateThrustLossPercent,
			double maxPostDerateLoadFactor,
			double maxPostDerateReactionTorqueScale,
			double maxPostDerateVibrationProxy,
			int playableReferenceAllowedCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message
	) {
	}

	public record DeratePhysicalBudgetAcceptanceScenario(
			String scenarioName,
			DeratePhysicalBudgetAcceptanceScenarioSummary summary
	) {
	}

	public record DeratePhysicalBudgetAcceptanceExtrema(
			int scenarioCount,
			int deratePhysicalBudgetAvailableScenarioCount,
			int totalBudgetRowCount,
			int maxBudgetRowCount,
			int maxPostDeratePhysicalBudgetAcceptedCount,
			int maxManualDerateReviewReadyCount,
			int maxRpmControlReviewCompleteCount,
			int maxPhysicsBudgetReviewCompleteCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double maxPostDerateThrustLossPercent,
			double maxPostDerateLoadFactor,
			double maxPostDerateReactionTorqueScale,
			double maxPostDerateVibrationProxy,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record DeratePhysicalBudgetAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int budgetRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<DeratePhysicalBudgetAcceptanceRow> rows,
			List<DeratePhysicalBudgetAcceptanceScenario> scenarios,
			DeratePhysicalBudgetAcceptanceExtrema extrema
	) {
		public DeratePhysicalBudgetAcceptanceAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static DeratePhysicalBudgetAcceptanceAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewAudit review =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit derate =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.audit();
		List<DeratePhysicalBudgetAcceptanceScenario> scenarios = review.scenarios().stream()
				.map(scenario -> scenario(review, derate, scenario))
				.toList();
		List<DeratePhysicalBudgetAcceptanceRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(review, derate, scenario.scenarioName()).stream())
				.toList();
		return new DeratePhysicalBudgetAcceptanceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				BUDGET_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static DeratePhysicalBudgetAcceptanceScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate physical budget acceptance scenario: "
								+ scenarioName));
	}

	public static DeratePhysicalBudgetAcceptanceRow row(
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
						"unknown RotorSpec retune ambient derate physical budget acceptance row: "
								+ scenarioName + "/" + presetName + "/" + ambientCaseName));
	}

	private static DeratePhysicalBudgetAcceptanceScenario scenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewAudit review,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit derate,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewScenario scenario
	) {
		List<DeratePhysicalBudgetAcceptanceRow> rows = rowsForScenario(review, derate, scenario.scenarioName());
		return new DeratePhysicalBudgetAcceptanceScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static DeratePhysicalBudgetAcceptanceScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewScenarioSummary reviewSummary,
			List<DeratePhysicalBudgetAcceptanceRow> rows
	) {
		int accepted = 0;
		int manualReady = 0;
		int rpmComplete = 0;
		int budgetComplete = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxLoad = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (DeratePhysicalBudgetAcceptanceRow row : rows) {
			if (row.postDeratePhysicalBudgetAccepted()) {
				accepted++;
			}
			if (row.manualDerateReviewReady()) {
				manualReady++;
			}
			if (row.rpmControlReviewComplete()) {
				rpmComplete++;
			}
			if (row.physicsBudgetReviewComplete()) {
				budgetComplete++;
			}
			maxDerate = Math.max(maxDerate, row.requiredMaxRpmDeratePercent());
			minScale = Math.min(minScale, row.targetMaxRpmScale());
			maxLoss = Math.max(maxLoss, row.postDerateThrustLossPercent());
			maxLoad = Math.max(maxLoad, row.postDerateLoadFactor());
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
		boolean available = reviewSummary.manualDerateReviewAllowed() && !rows.isEmpty();
		boolean allAccepted = available && accepted == rows.size();
		return new DeratePhysicalBudgetAcceptanceScenarioSummary(
				reviewSummary.derateValidationAcceptanceReady(),
				reviewSummary.manualDerateReviewAllowed(),
				available,
				rows.size(),
				accepted,
				manualReady,
				rpmComplete,
				budgetComplete,
				maxDerate,
				rows.isEmpty() ? 0.0 : minScale,
				maxLoss,
				maxLoad,
				maxTorque,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay,
				available ? "READY" : "BLOCKED",
				message(reviewSummary, available, allAccepted)
		);
	}

	private static List<DeratePhysicalBudgetAcceptanceRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewAudit review,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit derate,
			String scenarioName
	) {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewScenarioSummary summary =
				review.scenarios().stream()
						.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		if (!summary.manualDerateReviewAllowed()) {
			return List.of();
		}
		return reviewGroups(review.rows(), scenarioName).values().stream()
				.map(group -> row(scenarioName, summary, group, derate))
				.toList();
	}

	private static Map<String, ReviewGroup> reviewGroups(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewRow> rows,
			String scenarioName
	) {
		Map<String, ReviewGroup> groups = new HashMap<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewRow row : rows) {
			if (!scenarioName.equals(row.scenarioName())) {
				continue;
			}
			String key = row.presetName() + "/" + row.ambientCaseName();
			ReviewGroup group = groups.computeIfAbsent(
					key,
					ignored -> new ReviewGroup(row.presetName(), row.ambientCaseName())
			);
			group.values.put(row.fieldName(), row.reviewValue());
		}
		return groups;
	}

	private static DeratePhysicalBudgetAcceptanceRow row(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewScenarioSummary review,
			ReviewGroup group,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit derate
	) {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateRow target =
				derate.rows().stream()
						.filter(row -> "synthetic_ready_validation_all_pass".equals(row.scenarioName()))
						.filter(row -> group.presetName.equals(row.presetName()))
						.filter(row -> group.ambientCaseName.equals(row.ambientCaseName()))
						.findFirst()
						.orElseThrow();
		boolean rpmComplete = group.values.containsKey("target_max_tip_mach")
				&& group.values.containsKey("target_max_rpm_scale")
				&& group.values.containsKey("required_max_rpm_derate_percent");
		boolean budgetComplete = group.values.containsKey("post_derate_thrust_loss_percent")
				&& group.values.containsKey("post_derate_load_factor")
				&& group.values.containsKey("post_derate_reaction_torque_scale")
				&& group.values.containsKey("post_derate_vibration_proxy");
		double targetMach = value(group, "target_max_tip_mach");
		double targetScale = value(group, "target_max_rpm_scale");
		double deratePercent = value(group, "required_max_rpm_derate_percent");
		double loss = value(group, "post_derate_thrust_loss_percent");
		double load = value(group, "post_derate_load_factor");
		double torque = value(group, "post_derate_reaction_torque_scale");
		double vibration = value(group, "post_derate_vibration_proxy");
		boolean budgetAccepted = review.manualDerateReviewAllowed()
				&& rpmComplete
				&& budgetComplete
				&& target.derateTargetWithinBudget()
				&& targetMach <= target.maxTipMachLimit()
				&& targetScale > 0.0
				&& targetScale <= 1.0
				&& deratePercent >= 0.0
				&& loss <= target.maxThrustLossPercentLimit()
				&& load <= target.maxLoadFactorLimit()
				&& torque <= target.maxReactionTorqueScaleLimit()
				&& vibration <= target.maxVibrationProxyLimit();
		return new DeratePhysicalBudgetAcceptanceRow(
				scenarioName,
				group.presetName,
				group.ambientCaseName,
				review.derateValidationAcceptanceReady(),
				review.manualDerateReviewAllowed(),
				true,
				rpmComplete,
				budgetComplete,
				targetMach,
				targetScale,
				deratePercent,
				target.limitingBudget(),
				target.maxTipMachLimit(),
				loss,
				target.maxThrustLossPercentLimit(),
				load,
				target.maxLoadFactorLimit(),
				torque,
				target.maxReactionTorqueScaleLimit(),
				vibration,
				target.maxVibrationProxyLimit(),
				target.derateTargetWithinBudget(),
				budgetAccepted,
				budgetAccepted,
				false,
				false,
				false,
				false,
				budgetAccepted ? "READY" : "BLOCKED",
				budgetAccepted
						? "post-derate-physical-budget-accepted-for-manual-review"
						: "post-derate-physical-budget-incomplete"
		);
	}

	private static double value(ReviewGroup group, String fieldName) {
		Double value = group.values.get(fieldName);
		if (value == null) {
			return 0.0;
		}
		return value;
	}

	private static DeratePhysicalBudgetAcceptanceExtrema extrema(
			List<DeratePhysicalBudgetAcceptanceRow> rows,
			List<DeratePhysicalBudgetAcceptanceScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxAccepted = 0;
		int maxManual = 0;
		int maxRpm = 0;
		int maxBudget = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxLoad = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (DeratePhysicalBudgetAcceptanceScenario scenario : scenarios) {
			DeratePhysicalBudgetAcceptanceScenarioSummary summary = scenario.summary();
			if (summary.deratePhysicalBudgetAvailable()) {
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
			maxRows = Math.max(maxRows, summary.budgetRowCount());
			maxAccepted = Math.max(maxAccepted, summary.postDeratePhysicalBudgetAcceptedCount());
			maxManual = Math.max(maxManual, summary.manualDerateReviewReadyCount());
			maxRpm = Math.max(maxRpm, summary.rpmControlReviewCompleteCount());
			maxBudget = Math.max(maxBudget, summary.physicsBudgetReviewCompleteCount());
			if (summary.deratePhysicalBudgetAvailable()) {
				maxDerate = Math.max(maxDerate, summary.maxRequiredMaxRpmDeratePercent());
				minScale = Math.min(minScale, summary.minTargetMaxRpmScale());
				maxLoss = Math.max(maxLoss, summary.maxPostDerateThrustLossPercent());
				maxLoad = Math.max(maxLoad, summary.maxPostDerateLoadFactor());
				maxTorque = Math.max(maxTorque, summary.maxPostDerateReactionTorqueScale());
				maxVibration = Math.max(maxVibration, summary.maxPostDerateVibrationProxy());
			}
		}
		return new DeratePhysicalBudgetAcceptanceExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxAccepted,
				maxManual,
				maxRpm,
				maxBudget,
				maxDerate,
				rows.isEmpty() ? 0.0 : minScale,
				maxLoss,
				maxLoad,
				maxTorque,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay
		);
	}

	private static String message(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.DerateManualReviewScenarioSummary review,
			boolean available,
			boolean allAccepted
	) {
		if (!available) {
			return review.message();
		}
		if (allAccepted) {
			return "post-derate-physical-budget-accepted-for-manual-review";
		}
		return "post-derate-physical-budget-incomplete";
	}

	private static final class ReviewGroup {
		private final String presetName;
		private final String ambientCaseName;
		private final Map<String, Double> values = new HashMap<>();

		private ReviewGroup(String presetName, String ambientCaseName) {
			this.presetName = presetName;
			this.ambientCaseName = ambientCaseName;
		}
	}
}
