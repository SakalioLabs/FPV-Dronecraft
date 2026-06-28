package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Reference-Table-Packet";
	public static final String CAVEAT =
			"Reference table rows stay export-disabled until the surface-wake reference handoff opens; rows are compact lab evidence for downstream review, not gameplay tuning parameters.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int REFERENCE_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.GROUP_SAMPLE_COUNT;
	public static final int REFERENCE_METRIC_COUNT = 24;
	public static final int SUMMARY_METRIC_ROW_COUNT = 13;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ REFERENCE_SAMPLE_COUNT * REFERENCE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable() {
	}

	public record PoweredHoverSurfaceWakeReferenceRow(
			String presetName,
			String spinState,
			String surfaceType,
			double clearanceOverRadius,
			double clearanceMeters,
			boolean labValidationAccepted,
			boolean errorBudgetGroupReady,
			boolean referenceMaterialExportAllowed,
			boolean referenceRowAvailable,
			double meanTargetPressurePascals,
			double maxPressureErrorRatio,
			double meanTargetWakeVelocityMetersPerSecond,
			double maxWakeVelocityErrorRatio,
			double meanArrivalBandSeconds,
			double maxArrivalBandErrorRatio,
			double pressureReferenceWeight,
			double wakeVelocityReferenceWeight,
			double arrivalReferenceWeight,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String referencePayloadKind,
			String sourceRuntimeInfo
	) {
	}

	public record PoweredHoverSurfaceWakeReferenceTableExtrema(
			int rowCount,
			int referenceRowAvailableCount,
			int blockedRowCount,
			int groundRowCount,
			int ceilingRowCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double maxPressureReferenceWeight,
			double maxWakeVelocityReferenceWeight,
			double maxArrivalReferenceWeight,
			double maxMeanTargetPressurePascals,
			double maxMeanTargetWakeVelocityMetersPerSecond,
			double maxMeanArrivalBandSeconds
	) {
	}

	public record PoweredHoverSurfaceWakeReferenceTableAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int referenceSampleCount,
			int referenceMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeReferenceRow> rows,
			PoweredHoverSurfaceWakeReferenceTableExtrema extrema
	) {
		public PoweredHoverSurfaceWakeReferenceTableAudit {
			rows = List.copyOf(rows);
		}
	}

	public static PoweredHoverSurfaceWakeReferenceTableAudit audit() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary current =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.audit().scenarios().stream()
						.filter(scenario -> "current_lab_validation_blocked".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		return audit(current, Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit());
	}

	public static PoweredHoverSurfaceWakeReferenceTableAudit audit(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary handoff,
			Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetAudit errorBudget
	) {
		if (handoff == null || errorBudget == null) {
			throw new IllegalArgumentException("handoff and errorBudget are required.");
		}
		List<PoweredHoverSurfaceWakeReferenceRow> rows = errorBudget.groups().stream()
				.map(group -> row(handoff, group))
				.toList();
		return new PoweredHoverSurfaceWakeReferenceTableAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				REFERENCE_SAMPLE_COUNT,
				REFERENCE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredHoverSurfaceWakeReferenceRow row(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary handoff,
			Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup group
	) {
		if (handoff == null || group == null) {
			throw new IllegalArgumentException("handoff and group are required.");
		}
		boolean groupReady = group.validationCandidate()
				&& group.allRotorSeedsReady()
				&& group.allRotorSeedsPassed();
		boolean available = handoff.referenceMaterialExportAllowed()
				&& handoff.allErrorBudgetGroupsReady()
				&& groupReady;
		double weight = available ? 1.0 : 0.0;
		return new PoweredHoverSurfaceWakeReferenceRow(
				group.presetName(),
				group.spinState(),
				group.surfaceType(),
				group.clearanceOverRadius(),
				group.clearanceMeters(),
				handoff.labValidationAccepted(),
				groupReady,
				handoff.referenceMaterialExportAllowed(),
				available,
				group.meanTargetPressurePascals(),
				group.maxPressureErrorRatio(),
				group.meanTargetWakeVelocityMetersPerSecond(),
				group.maxWakeVelocityErrorRatio(),
				group.meanArrivalBandSeconds(),
				group.maxArrivalBandErrorRatio(),
				weight,
				weight,
				weight,
				false,
				false,
				available ? "AVAILABLE" : "BLOCKED",
				messageFor(handoff, groupReady),
				handoff.referencePayloadKind(),
				handoff.sourceRuntimeInfo()
		);
	}

	private static String messageFor(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary handoff,
			boolean groupReady
	) {
		if (!handoff.referenceMaterialExportAllowed()) {
			return "reference-handoff-blocked";
		}
		if (!groupReady) {
			return "reference-row-error-budget-blocked";
		}
		return "surface-wake-reference-row-available";
	}

	private static PoweredHoverSurfaceWakeReferenceTableExtrema extrema(
			List<PoweredHoverSurfaceWakeReferenceRow> rows
	) {
		int available = 0;
		int ground = 0;
		int ceiling = 0;
		int runtime = 0;
		int autoApply = 0;
		double maxPressureWeight = 0.0;
		double maxVelocityWeight = 0.0;
		double maxArrivalWeight = 0.0;
		double maxPressure = 0.0;
		double maxVelocity = 0.0;
		double maxArrival = 0.0;
		for (PoweredHoverSurfaceWakeReferenceRow row : rows) {
			if (row.referenceRowAvailable()) {
				available++;
			}
			if ("ground".equals(row.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(row.surfaceType())) {
				ceiling++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				autoApply++;
			}
			maxPressureWeight = Math.max(maxPressureWeight, row.pressureReferenceWeight());
			maxVelocityWeight = Math.max(maxVelocityWeight, row.wakeVelocityReferenceWeight());
			maxArrivalWeight = Math.max(maxArrivalWeight, row.arrivalReferenceWeight());
			maxPressure = Math.max(maxPressure, row.meanTargetPressurePascals());
			maxVelocity = Math.max(maxVelocity, row.meanTargetWakeVelocityMetersPerSecond());
			maxArrival = Math.max(maxArrival, row.meanArrivalBandSeconds());
		}
		return new PoweredHoverSurfaceWakeReferenceTableExtrema(
				rows.size(),
				available,
				rows.size() - available,
				ground,
				ceiling,
				runtime,
				autoApply,
				maxPressureWeight,
				maxVelocityWeight,
				maxArrivalWeight,
				maxPressure,
				maxVelocity,
				maxArrival
		);
	}
}
