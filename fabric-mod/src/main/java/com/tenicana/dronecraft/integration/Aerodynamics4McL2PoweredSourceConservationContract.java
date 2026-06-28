package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceConservationContract {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Conservation-Contract-Packet";
	public static final String CAVEAT =
			"Powered-source conservation contract records actuator-disk force, momentum, and kinetic-power closure targets for future live A4MC source-term executors; it is audit-only and never enables runtime coupling or gameplay auto-apply.";
	public static final double MAX_MOMENTUM_CLOSURE_ERROR_RATIO = 0.015;
	public static final double MAX_KINETIC_POWER_CLOSURE_ERROR_RATIO = 0.02;
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SPIN_STATE_SAMPLE_COUNT = 2;
	public static final int CONTRACT_METRIC_COUNT = 26;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SPIN_STATE_SAMPLE_COUNT * CONTRACT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceConservationContract() {
	}

	public record PoweredSourceConservationContractRow(
			String spinState,
			int footprintCount,
			int sourceTermCount,
			double maxTotalThrustNewtons,
			double maxMassFlowKilogramsPerSecond,
			double maxMomentumPowerWatts,
			double maxMomentumClosureErrorNewtons,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorWatts,
			double maxKineticPowerClosureErrorRatio,
			double maxWakeSpeedMetersPerSecond,
			double maxWakeTransitTimeSeconds,
			int recommendedWakeSampleCount,
			boolean targetModelSelfConsistent,
			boolean sourceForceDeltaRequired,
			boolean sourceMomentDeltaRequired,
			boolean wakeMomentumResidualRequired,
			boolean wakeKineticPowerResidualRequired,
			boolean livePoweredSourceEvidencePresent,
			boolean liveWakeProbeEvidencePresent,
			boolean liveConservationAccepted,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceConservationContractExtrema(
			int rowCount,
			int targetModelSelfConsistentCount,
			int liveConservationAcceptedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int sourceForceDeltaRequiredCount,
			int sourceMomentDeltaRequiredCount,
			int wakeResidualRequiredCount,
			int totalFootprintCount,
			int totalSourceTermCount,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio
	) {
	}

	public record PoweredSourceConservationContractAudit(
			String sourceId,
			String caveat,
			double maxMomentumClosureErrorRatioAllowed,
			double maxKineticPowerClosureErrorRatioAllowed,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int spinStateSampleCount,
			int contractMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceConservationContractRow> rows,
			PoweredSourceConservationContractExtrema extrema
	) {
		public PoweredSourceConservationContractAudit {
			rows = List.copyOf(rows);
		}
	}

	public static PoweredSourceConservationContractAudit audit() {
		List<PoweredSourceConservationContractRow> rows = List.of(
				hoverRow(Aerodynamics4McL2PoweredHoverWakeFootprint.audit()),
				cruiseRow(Aerodynamics4McL2PoweredCruiseWakeFootprint.audit())
		);
		return new PoweredSourceConservationContractAudit(
				SOURCE_ID,
				CAVEAT,
				MAX_MOMENTUM_CLOSURE_ERROR_RATIO,
				MAX_KINETIC_POWER_CLOSURE_ERROR_RATIO,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SPIN_STATE_SAMPLE_COUNT,
				CONTRACT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceConservationContractRow hoverRow(
			Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprintAudit audit
	) {
		if (audit == null) {
			throw new IllegalArgumentException("hover wake footprint audit is required.");
		}
		List<Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint> footprints =
				audit.footprints();
		int sourceTerms = 0;
		double maxThrust = 0.0;
		double maxMassFlow = 0.0;
		double maxPower = 0.0;
		double maxMomentumError = 0.0;
		double maxMomentumRatio = 0.0;
		double maxPowerError = 0.0;
		double maxPowerRatio = 0.0;
		double maxWakeSpeed = 0.0;
		int maxWakeSamples = 0;
		for (Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint footprint : footprints) {
			sourceTerms += footprint.sourceTermCount();
			maxThrust = Math.max(maxThrust, footprint.totalThrustNewtons());
			maxMassFlow = Math.max(maxMassFlow, footprint.massFlowKilogramsPerSecond());
			maxPower = Math.max(maxPower, footprint.totalMomentumPowerWatts());
			maxMomentumError = Math.max(maxMomentumError, footprint.momentumFluxClosureErrorNewtons());
			maxMomentumRatio = Math.max(maxMomentumRatio,
					ratio(footprint.momentumFluxClosureErrorNewtons(), footprint.totalThrustNewtons()));
			maxPowerError = Math.max(maxPowerError, footprint.kineticPowerClosureErrorWatts());
			maxPowerRatio = Math.max(maxPowerRatio,
					ratio(footprint.kineticPowerClosureErrorWatts(), footprint.totalMomentumPowerWatts()));
			maxWakeSpeed = Math.max(maxWakeSpeed, footprint.farWakeVelocityMetersPerSecond());
			maxWakeSamples = Math.max(maxWakeSamples, footprint.recommendedRotorWakeSampleCount());
		}
		return row(
				"hover",
				footprints.size(),
				sourceTerms,
				maxThrust,
				maxMassFlow,
				maxPower,
				maxMomentumError,
				maxMomentumRatio,
				maxPowerError,
				maxPowerRatio,
				maxWakeSpeed,
				0.0,
				maxWakeSamples
		);
	}

	public static PoweredSourceConservationContractRow cruiseRow(
			Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprintAudit audit
	) {
		if (audit == null) {
			throw new IllegalArgumentException("cruise wake footprint audit is required.");
		}
		List<Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint> footprints =
				audit.footprints();
		int sourceTerms = 0;
		double maxThrust = 0.0;
		double maxMassFlow = 0.0;
		double maxPower = 0.0;
		double maxMomentumError = 0.0;
		double maxMomentumRatio = 0.0;
		double maxPowerError = 0.0;
		double maxPowerRatio = 0.0;
		double maxWakeSpeed = 0.0;
		double maxTransitTime = 0.0;
		int maxWakeSamples = 0;
		for (Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint : footprints) {
			sourceTerms += footprint.sourceTermCount();
			maxThrust = Math.max(maxThrust, footprint.totalThrustNewtons());
			maxMassFlow = Math.max(maxMassFlow, footprint.massFlowKilogramsPerSecond());
			maxPower = Math.max(maxPower, footprint.totalMomentumPowerWatts());
			maxMomentumError = Math.max(maxMomentumError, footprint.momentumFluxClosureErrorNewtons());
			maxMomentumRatio = Math.max(maxMomentumRatio,
					ratio(footprint.momentumFluxClosureErrorNewtons(), footprint.totalThrustNewtons()));
			maxPowerError = Math.max(maxPowerError, footprint.kineticPowerClosureErrorWatts());
			maxPowerRatio = Math.max(maxPowerRatio,
					ratio(footprint.kineticPowerClosureErrorWatts(), footprint.totalMomentumPowerWatts()));
			maxWakeSpeed = Math.max(maxWakeSpeed, footprint.resultantWakeVelocityMetersPerSecond());
			maxTransitTime = Math.max(maxTransitTime, footprint.axialTransitTimeSeconds());
			maxWakeSamples = Math.max(maxWakeSamples, footprint.recommendedSkewWakeSampleCount());
		}
		return row(
				"cruise",
				footprints.size(),
				sourceTerms,
				maxThrust,
				maxMassFlow,
				maxPower,
				maxMomentumError,
				maxMomentumRatio,
				maxPowerError,
				maxPowerRatio,
				maxWakeSpeed,
				maxTransitTime,
				maxWakeSamples
		);
	}

	private static PoweredSourceConservationContractRow row(
			String spinState,
			int footprintCount,
			int sourceTermCount,
			double maxThrust,
			double maxMassFlow,
			double maxPower,
			double maxMomentumError,
			double maxMomentumRatio,
			double maxPowerError,
			double maxPowerRatio,
			double maxWakeSpeed,
			double maxTransitTime,
			int maxWakeSamples
	) {
		boolean targetSelfConsistent = footprintCount > 0
				&& sourceTermCount > 0
				&& maxMomentumRatio <= MAX_MOMENTUM_CLOSURE_ERROR_RATIO
				&& maxPowerRatio <= MAX_KINETIC_POWER_CLOSURE_ERROR_RATIO;
		boolean liveSourceEvidence = false;
		boolean liveWakeEvidence = false;
		boolean liveAccepted = targetSelfConsistent && liveSourceEvidence && liveWakeEvidence;
		return new PoweredSourceConservationContractRow(
				spinState,
				footprintCount,
				sourceTermCount,
				maxThrust,
				maxMassFlow,
				maxPower,
				maxMomentumError,
				maxMomentumRatio,
				maxPowerError,
				maxPowerRatio,
				maxWakeSpeed,
				maxTransitTime,
				maxWakeSamples,
				targetSelfConsistent,
				true,
				true,
				true,
				true,
				liveSourceEvidence,
				liveWakeEvidence,
				liveAccepted,
				false,
				false,
				"capture-live-a4mc-powered-source-force-moment-and-wake-residuals",
				liveAccepted ? "ACCEPTED" : "TARGET_ONLY",
				liveAccepted
						? "powered-source-conservation-contract-accepted"
						: "powered-source-conservation-target-self-consistent-live-evidence-missing"
		);
	}

	private static PoweredSourceConservationContractExtrema extrema(
			List<PoweredSourceConservationContractRow> rows
	) {
		int targetSelfConsistent = 0;
		int liveAccepted = 0;
		int runtime = 0;
		int autoApply = 0;
		int forceDelta = 0;
		int momentDelta = 0;
		int wakeResidual = 0;
		int footprints = 0;
		int sourceTerms = 0;
		double maxMomentumRatio = 0.0;
		double maxPowerRatio = 0.0;
		for (PoweredSourceConservationContractRow row : rows) {
			if (row.targetModelSelfConsistent()) {
				targetSelfConsistent++;
			}
			if (row.liveConservationAccepted()) {
				liveAccepted++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				autoApply++;
			}
			if (row.sourceForceDeltaRequired()) {
				forceDelta++;
			}
			if (row.sourceMomentDeltaRequired()) {
				momentDelta++;
			}
			if (row.wakeMomentumResidualRequired() && row.wakeKineticPowerResidualRequired()) {
				wakeResidual++;
			}
			footprints += row.footprintCount();
			sourceTerms += row.sourceTermCount();
			maxMomentumRatio = Math.max(maxMomentumRatio, row.maxMomentumClosureErrorRatio());
			maxPowerRatio = Math.max(maxPowerRatio, row.maxKineticPowerClosureErrorRatio());
		}
		return new PoweredSourceConservationContractExtrema(
				rows.size(),
				targetSelfConsistent,
				liveAccepted,
				runtime,
				autoApply,
				forceDelta,
				momentDelta,
				wakeResidual,
				footprints,
				sourceTerms,
				maxMomentumRatio,
				maxPowerRatio
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return Math.abs(numerator) / Math.abs(denominator);
	}
}
