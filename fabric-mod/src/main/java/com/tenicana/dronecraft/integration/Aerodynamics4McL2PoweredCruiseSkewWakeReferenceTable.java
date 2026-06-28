package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Reference-Table-Packet";
	public static final String CAVEAT =
			"Reference table rows stay export-disabled until the cruise skew-wake reference handoff opens; rows are compact lab evidence for downstream review, not gameplay tuning parameters.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int REFERENCE_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.GROUP_SAMPLE_COUNT;
	public static final int REFERENCE_METRIC_COUNT = 27;
	public static final int SUMMARY_METRIC_ROW_COUNT = 14;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ REFERENCE_SAMPLE_COUNT * REFERENCE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable() {
	}

	public record PoweredCruiseSkewWakeReferenceRow(
			String presetName,
			String spinState,
			int axialPlaneIndex,
			int sweepColumnIndex,
			double axialPlaneFraction,
			boolean labValidationAccepted,
			boolean errorBudgetGroupReady,
			boolean referenceMaterialExportAllowed,
			boolean referenceRowAvailable,
			double meanTargetResultantDynamicPressurePascals,
			double maxPressureErrorRatio,
			double meanTargetResultantWakeVelocityMetersPerSecond,
			double maxWakeVelocityErrorRatio,
			double meanArrivalBandSeconds,
			double maxArrivalWindowErrorRatio,
			double meanTargetAxialMomentumFluxNewtons,
			double maxMomentumErrorRatio,
			double pressureReferenceWeight,
			double wakeVelocityReferenceWeight,
			double arrivalReferenceWeight,
			double momentumReferenceWeight,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String referencePayloadKind,
			String sourceRuntimeInfo
	) {
	}

	public record PoweredCruiseSkewWakeReferenceTableExtrema(
			int rowCount,
			int referenceRowAvailableCount,
			int blockedRowCount,
			int centerlineSweepRowCount,
			int lateralSweepRowCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double maxPressureReferenceWeight,
			double maxWakeVelocityReferenceWeight,
			double maxArrivalReferenceWeight,
			double maxMomentumReferenceWeight,
			double maxMeanTargetResultantDynamicPressurePascals,
			double maxMeanTargetResultantWakeVelocityMetersPerSecond,
			double maxMeanTargetAxialMomentumFluxNewtons
	) {
	}

	public record PoweredCruiseSkewWakeReferenceTableAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int referenceSampleCount,
			int referenceMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeReferenceRow> rows,
			PoweredCruiseSkewWakeReferenceTableExtrema extrema
	) {
		public PoweredCruiseSkewWakeReferenceTableAudit {
			rows = List.copyOf(rows);
		}
	}

	public static PoweredCruiseSkewWakeReferenceTableAudit audit() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary current =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.audit().scenarios().stream()
						.filter(scenario -> "current_lab_validation_blocked".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		return audit(current, Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit());
	}

	public static PoweredCruiseSkewWakeReferenceTableAudit audit(
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary handoff,
			Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetAudit errorBudget
	) {
		if (handoff == null || errorBudget == null) {
			throw new IllegalArgumentException("handoff and errorBudget are required.");
		}
		List<PoweredCruiseSkewWakeReferenceRow> rows = errorBudget.groups().stream()
				.map(group -> row(handoff, group))
				.toList();
		return new PoweredCruiseSkewWakeReferenceTableAudit(
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

	public static PoweredCruiseSkewWakeReferenceRow row(
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary handoff,
			Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group
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
		return new PoweredCruiseSkewWakeReferenceRow(
				group.presetName(),
				group.spinState(),
				group.axialPlaneIndex(),
				group.sweepColumnIndex(),
				group.axialPlaneFraction(),
				handoff.labValidationAccepted(),
				groupReady,
				handoff.referenceMaterialExportAllowed(),
				available,
				group.meanTargetResultantDynamicPressurePascals(),
				group.maxPressureErrorRatio(),
				group.meanTargetResultantWakeVelocityMetersPerSecond(),
				group.maxWakeVelocityErrorRatio(),
				group.meanArrivalBandSeconds(),
				group.maxArrivalWindowErrorRatio(),
				group.meanTargetAxialMomentumFluxNewtons(),
				group.maxMomentumErrorRatio(),
				weight,
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
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary handoff,
			boolean groupReady
	) {
		if (!handoff.referenceMaterialExportAllowed()) {
			return "reference-handoff-blocked";
		}
		if (!groupReady) {
			return "reference-row-error-budget-blocked";
		}
		return "cruise-skew-wake-reference-row-available";
	}

	private static PoweredCruiseSkewWakeReferenceTableExtrema extrema(
			List<PoweredCruiseSkewWakeReferenceRow> rows
	) {
		int available = 0;
		int centerline = 0;
		int lateral = 0;
		int runtime = 0;
		int autoApply = 0;
		double maxPressureWeight = 0.0;
		double maxVelocityWeight = 0.0;
		double maxArrivalWeight = 0.0;
		double maxMomentumWeight = 0.0;
		double maxPressure = 0.0;
		double maxVelocity = 0.0;
		double maxMomentum = 0.0;
		for (PoweredCruiseSkewWakeReferenceRow row : rows) {
			if (row.referenceRowAvailable()) {
				available++;
			}
			if (row.sweepColumnIndex() == 0) {
				centerline++;
			} else {
				lateral++;
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
			maxMomentumWeight = Math.max(maxMomentumWeight, row.momentumReferenceWeight());
			maxPressure = Math.max(maxPressure, row.meanTargetResultantDynamicPressurePascals());
			maxVelocity = Math.max(maxVelocity, row.meanTargetResultantWakeVelocityMetersPerSecond());
			maxMomentum = Math.max(maxMomentum, row.meanTargetAxialMomentumFluxNewtons());
		}
		return new PoweredCruiseSkewWakeReferenceTableExtrema(
				rows.size(),
				available,
				rows.size() - available,
				centerline,
				lateral,
				runtime,
				autoApply,
				maxPressureWeight,
				maxVelocityWeight,
				maxArrivalWeight,
				maxMomentumWeight,
				maxPressure,
				maxVelocity,
				maxMomentum
		);
	}
}
