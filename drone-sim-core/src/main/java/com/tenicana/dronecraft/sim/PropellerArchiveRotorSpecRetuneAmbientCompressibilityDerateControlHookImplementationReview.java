package com.tenicana.dronecraft.sim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Implementation-Review-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook implementation review material is generated only after blackbox acceptance; it records target-omega insertion and telemetry review items without enabling preset candidate derates, runtime coupling, playable export, or gameplay auto-apply.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int REVIEW_ITEM_COUNT_PER_PRESET = 5;
	public static final int REVIEW_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT
					* REVIEW_ITEM_COUNT_PER_PRESET;
	public static final int SCENARIO_METRIC_ROW_COUNT = 12;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<ReviewItemDefinition> REVIEW_ITEMS = List.of(
			new ReviewItemDefinition(
					"target_max_rpm_scale",
					"ratio",
					true,
					true,
					false,
					"target_omega_scale_closure"
			),
			new ReviewItemDefinition(
					"contracted_max_rpm",
					"rpm",
					true,
					true,
					false,
					"target_omega_clamp_and_slew"
			),
			new ReviewItemDefinition(
					"equivalent_max_thrust_loss_percent",
					"percent",
					true,
					true,
					false,
					"tip_mach_and_thrust_loss_margin"
			),
			new ReviewItemDefinition(
					"hook_insertion_boundary",
					"text",
					false,
					true,
					false,
					"target_omega_clamp_and_slew"
			),
			new ReviewItemDefinition(
					"runtime_leak_guard",
					"text",
					false,
					false,
					true,
					"leak_guard"
			)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview() {
	}

	public record ReviewItemDefinition(
			String itemName,
			String unit,
			boolean numericItem,
			boolean telemetryRequired,
			boolean leakGuardItem,
			String blackboxMetric
	) {
	}

	public record DerateControlHookImplementationReviewRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String itemName,
			String reviewValue,
			double numericValue,
			boolean numericItem,
			String unit,
			String controlBoundary,
			String blackboxMetric,
			boolean telemetryRequired,
			boolean leakGuardItem,
			boolean blackboxRegressionAcceptanceReady,
			boolean manualControlHookReviewAllowed,
			boolean runtimeImplementationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DerateControlHookImplementationReviewScenarioSummary(
			boolean blackboxRegressionAcceptanceReady,
			boolean manualControlHookReviewAllowed,
			int manualControlHookCandidatePresetCount,
			int reviewRowCount,
			int numericReviewRowCount,
			int telemetryRequiredRowCount,
			int leakGuardRowCount,
			int runtimeImplementationAllowedCount,
			int runtimeCouplingAllowedCount,
			int playableReferenceAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record DerateControlHookImplementationReviewScenario(
			String scenarioName,
			DerateControlHookImplementationReviewScenarioSummary summary
	) {
	}

	public record DerateControlHookImplementationReviewExtrema(
			int scenarioCount,
			int manualControlHookReviewAllowedScenarioCount,
			int totalReviewRowCount,
			int maxReviewRowCount,
			int maxManualControlHookCandidatePresetCount,
			int maxNumericReviewRowCount,
			int maxTelemetryRequiredRowCount,
			int maxLeakGuardRowCount,
			int runtimeImplementationAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int playableReferenceAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record DerateControlHookImplementationReviewAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int reviewRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ReviewItemDefinition> reviewItems,
			List<DerateControlHookImplementationReviewRow> rows,
			List<DerateControlHookImplementationReviewScenario> scenarios,
			DerateControlHookImplementationReviewExtrema extrema
	) {
		public DerateControlHookImplementationReviewAudit {
			reviewItems = List.copyOf(reviewItems);
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static DerateControlHookImplementationReviewAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceAudit acceptance =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
								.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary current =
						acceptance(acceptance, "current_control_hook_blackbox_blocked");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary missing =
						acceptance(acceptance, "synthetic_control_hook_blackbox_results_missing");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary accepted =
						acceptance(acceptance, "synthetic_control_hook_blackbox_all_pass");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary failed =
						acceptance(acceptance, "synthetic_control_hook_blackbox_one_failed");

		List<DerateControlHookImplementationReviewRow> currentRows =
				reviewRows("current_control_hook_blackbox_blocked", current, contract);
		List<DerateControlHookImplementationReviewRow> missingRows =
				reviewRows("synthetic_control_hook_blackbox_results_missing", missing, contract);
		List<DerateControlHookImplementationReviewRow> acceptedRows =
				reviewRows("synthetic_control_hook_blackbox_all_pass", accepted, contract);
		List<DerateControlHookImplementationReviewRow> failedRows =
				reviewRows("synthetic_control_hook_blackbox_one_failed", failed, contract);

		List<DerateControlHookImplementationReviewScenario> scenarios = List.of(
				scenario("current_control_hook_blackbox_blocked",
						current,
						currentRows,
						"current-control-hook-implementation-review-blocked"),
				scenario("synthetic_control_hook_blackbox_results_missing",
						missing,
						missingRows,
						"synthetic-control-hook-blackbox-results-missing"),
				scenario("synthetic_control_hook_blackbox_all_pass",
						accepted,
						acceptedRows,
						"synthetic-control-hook-implementation-review-material"),
				scenario("synthetic_control_hook_blackbox_one_failed",
						failed,
						failedRows,
						"synthetic-control-hook-blackbox-one-failed")
		);
		List<DerateControlHookImplementationReviewRow> rows =
				Stream.of(currentRows, missingRows, acceptedRows, failedRows)
						.flatMap(List::stream)
						.toList();
		return new DerateControlHookImplementationReviewAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				REVIEW_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				REVIEW_ITEMS,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static DerateControlHookImplementationReviewScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate control-hook implementation review scenario: "
								+ scenarioName));
	}

	public static DerateControlHookImplementationReviewRow row(
			String scenarioName,
			String presetName,
			String itemName
	) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.itemName().equals(itemName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate control-hook implementation review row: "
								+ scenarioName + "/" + presetName + "/" + itemName));
	}

	public static List<ReviewItemDefinition> reviewItems() {
		return REVIEW_ITEMS;
	}

	private static DerateControlHookImplementationReviewScenario scenario(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
					.DerateControlHookBlackboxAcceptanceSummary acceptance,
			List<DerateControlHookImplementationReviewRow> rows,
			String sourceRuntimeInfo
	) {
		return new DerateControlHookImplementationReviewScenario(
				scenarioName,
				summary(acceptance, rows, sourceRuntimeInfo)
		);
	}

	private static DerateControlHookImplementationReviewScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
					.DerateControlHookBlackboxAcceptanceSummary acceptance,
			List<DerateControlHookImplementationReviewRow> rows,
			String sourceRuntimeInfo
	) {
		int numeric = 0;
		int telemetry = 0;
		int leak = 0;
		int implementation = 0;
		int runtime = 0;
		int playable = 0;
		int gameplay = 0;
		Set<String> presets = new HashSet<>();
		for (DerateControlHookImplementationReviewRow row : rows) {
			presets.add(row.presetName());
			if (row.numericItem()) {
				numeric++;
			}
			if (row.telemetryRequired()) {
				telemetry++;
			}
			if (row.leakGuardItem()) {
				leak++;
			}
			if (row.runtimeImplementationAllowed()) {
				implementation++;
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
		boolean reviewAllowed = acceptance.manualControlHookReviewAllowed() && !rows.isEmpty();
		return new DerateControlHookImplementationReviewScenarioSummary(
				acceptance.blackboxRegressionAcceptanceReady(),
				reviewAllowed,
				reviewAllowed ? presets.size() : 0,
				rows.size(),
				numeric,
				telemetry,
				leak,
				implementation,
				runtime,
				playable,
				gameplay,
				reviewAllowed ? "REVIEW_READY" : "BLOCKED",
				reviewAllowed ? "manual-control-hook-implementation-review-material-ready" : acceptance.message(),
				sourceRuntimeInfo
		);
	}

	private static List<DerateControlHookImplementationReviewRow> reviewRows(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
					.DerateControlHookBlackboxAcceptanceSummary acceptance,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractAudit contract
	) {
		if (!acceptance.manualControlHookReviewAllowed()) {
			return List.of();
		}
		return contract.rows().stream()
				.flatMap(contractRow -> REVIEW_ITEMS.stream()
						.map(item -> reviewRow(scenarioName, acceptance, contractRow, item)))
				.toList();
	}

	private static DerateControlHookImplementationReviewRow reviewRow(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
					.DerateControlHookBlackboxAcceptanceSummary acceptance,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			ReviewItemDefinition item
	) {
		double numericValue = numericValue(contract, item.itemName());
		return new DerateControlHookImplementationReviewRow(
				scenarioName,
				contract.presetName(),
				contract.ambientCaseName(),
				item.itemName(),
				reviewValue(contract, item.itemName()),
				numericValue,
				item.numericItem(),
				item.unit(),
				CONTROL_BOUNDARY,
				item.blackboxMetric(),
				item.telemetryRequired(),
				item.leakGuardItem(),
				acceptance.blackboxRegressionAcceptanceReady(),
				acceptance.manualControlHookReviewAllowed(),
				false,
				false,
				false,
				false,
				"REVIEW_READY",
				item.leakGuardItem()
						? "runtime-leak-guard-review-required"
						: "manual-control-hook-review-candidate"
		);
	}

	private static String reviewValue(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			String itemName
	) {
		return switch (itemName) {
			case "target_max_rpm_scale" -> Double.toString(contract.targetMaxRpmScale());
			case "contracted_max_rpm" -> Double.toString(contract.contractedMaxRpm());
			case "equivalent_max_thrust_loss_percent" -> Double.toString(
					contract.equivalentMaxThrustLossPercent());
			case "hook_insertion_boundary" ->
					"apply-targetMaxRpmScale-between-maxOmega-and-state.setMotorTargetOmega-before-motorResponse";
			case "runtime_leak_guard" ->
					"candidate-derate-runtime-coupling-playable-export-gameplay-auto-apply-remain-closed";
			default -> throw new IllegalArgumentException("unknown control-hook implementation review item: "
					+ itemName);
		};
	}

	private static double numericValue(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			String itemName
	) {
		return switch (itemName) {
			case "target_max_rpm_scale" -> contract.targetMaxRpmScale();
			case "contracted_max_rpm" -> contract.contractedMaxRpm();
			case "equivalent_max_thrust_loss_percent" -> contract.equivalentMaxThrustLossPercent();
			case "hook_insertion_boundary", "runtime_leak_guard" -> Double.NaN;
			default -> throw new IllegalArgumentException("unknown control-hook implementation review item: "
					+ itemName);
		};
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
			.DerateControlHookBlackboxAcceptanceSummary acceptance(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
					.DerateControlHookBlackboxAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static DerateControlHookImplementationReviewExtrema extrema(
			List<DerateControlHookImplementationReviewRow> rows,
			List<DerateControlHookImplementationReviewScenario> scenarios
	) {
		int reviewAllowed = 0;
		int maxRows = 0;
		int maxPresets = 0;
		int maxNumeric = 0;
		int maxTelemetry = 0;
		int maxLeak = 0;
		int implementation = 0;
		int runtime = 0;
		int playable = 0;
		int gameplay = 0;
		for (DerateControlHookImplementationReviewScenario scenario : scenarios) {
			DerateControlHookImplementationReviewScenarioSummary summary = scenario.summary();
			if (summary.manualControlHookReviewAllowed()) {
				reviewAllowed++;
			}
			if (summary.runtimeImplementationAllowedCount() > 0) {
				implementation++;
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
			maxRows = Math.max(maxRows, summary.reviewRowCount());
			maxPresets = Math.max(maxPresets, summary.manualControlHookCandidatePresetCount());
			maxNumeric = Math.max(maxNumeric, summary.numericReviewRowCount());
			maxTelemetry = Math.max(maxTelemetry, summary.telemetryRequiredRowCount());
			maxLeak = Math.max(maxLeak, summary.leakGuardRowCount());
		}
		return new DerateControlHookImplementationReviewExtrema(
				scenarios.size(),
				reviewAllowed,
				rows.size(),
				maxRows,
				maxPresets,
				maxNumeric,
				maxTelemetry,
				maxLeak,
				implementation,
				runtime,
				playable,
				gameplay
		);
	}
}
