package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Validation-Run-Matrix-Packet";
	public static final String CAVEAT =
			"Cruise skew-wake validation run matrix records compact execution status only; current rows stay skipped until the skew-wake readiness gate opens and a live A4MC transient skew-wake probe executor exists.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int RUN_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.TEMPORAL_TARGET_COUNT;
	public static final int RUN_METRIC_COUNT = 52;
	public static final int SUMMARY_METRIC_ROW_COUNT = 18;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ RUN_SAMPLE_COUNT * RUN_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix() {
	}

	public record PoweredCruiseSkewWakeValidationRun(
			String presetName,
			String spinState,
			int rotorIndex,
			int axialPlaneIndex,
			int sweepColumnIndex,
			double axialPlaneFraction,
			double axialDistanceMeters,
			double freestreamSweepDistanceMeters,
			double lateralOffsetMeters,
			double centerlineDistanceMeters,
			double distanceFromRotorMeters,
			double expectedAxialWakeVelocityMetersPerSecond,
			double expectedFreestreamVelocityMetersPerSecond,
			double expectedResultantWakeVelocityMetersPerSecond,
			double centerlineResultantTransitSeconds,
			double lateralAdjustedTransitSeconds,
			double requiredMaxSamplePeriodSeconds,
			double appliedMaxSamplePeriodSeconds,
			int requiredSubstepsPerMinecraftTick,
			int requiredSubstepsPerOneKilohertzFrame,
			double expectedAxialWakePressurePascals,
			double expectedResultantDynamicPressurePascals,
			double perRotorAxialMomentumFluxNewtons,
			boolean poweredSourceApiAvailable,
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			boolean targetSkewWakeProbeApiAvailable,
			boolean targetTransientProbeApiAvailable,
			boolean readinessGateOpen,
			boolean validationRunAllowed,
			boolean requestPressureProbe,
			boolean requestVelocityProbe,
			boolean requestTransientSeries,
			boolean requestMomentumClosure,
			boolean invoked,
			boolean succeeded,
			boolean available,
			boolean hasPressureEvidence,
			boolean hasVelocityEvidence,
			boolean hasTransitEvidence,
			boolean hasMomentumEvidence,
			double observedPressurePascals,
			double observedWakeVelocityMetersPerSecond,
			double observedArrivalTimeSeconds,
			double observedMomentumFluxNewtons,
			double pressureErrorRatio,
			double velocityErrorRatio,
			double arrivalTimeErrorRatio,
			double momentumErrorRatio,
			String status,
			String message,
			String runtimeInfo
	) {
	}

	public record PoweredCruiseSkewWakeValidationRunExtrema(
			int runCount,
			int sourceTermCount,
			int centerlineSweepRunCount,
			int lateralSweepRunCount,
			int readinessGateOpenCount,
			int validationRunAllowedCount,
			int targetSkewWakeProbeApiAvailableCount,
			int targetTransientProbeApiAvailableCount,
			int invokedCount,
			int availableCount,
			int pressureEvidenceCount,
			int velocityEvidenceCount,
			int transitEvidenceCount,
			int momentumEvidenceCount,
			int skippedForReadinessCount,
			int pendingExecutorCount,
			int maxRequiredSubstepsPerMinecraftTick,
			double maxRequiredSampleRateHertz
	) {
	}

	public record PoweredCruiseSkewWakeValidationRunMatrixAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int runSampleCount,
			int runMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeValidationRun> runs,
			PoweredCruiseSkewWakeValidationRunExtrema extrema
	) {
		public PoweredCruiseSkewWakeValidationRunMatrixAudit {
			runs = List.copyOf(runs);
		}
	}

	public static PoweredCruiseSkewWakeValidationRunMatrixAudit audit() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> temporalTargets =
				Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.audit().targets();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> transitTargets =
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.audit().targets();
		return audit(false, false, false, 0.05, temporalTargets, transitTargets);
	}

	public static PoweredCruiseSkewWakeValidationRunMatrixAudit audit(
			boolean poweredSourceApiAvailable,
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> temporalTargets,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> transitTargets
	) {
		if (temporalTargets == null || transitTargets == null) {
			throw new IllegalArgumentException("temporal and transit targets are required.");
		}
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary readiness =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						poweredSourceApiAvailable,
						skewWakeProbeApiAvailable,
						transientProbeApiAvailable,
						appliedMaxSamplePeriodSeconds,
						temporalTargets
				);
		Map<String, Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> transitByName =
				transitTargets.stream()
						.collect(Collectors.toMap(
								Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix::targetName,
								Function.identity()
						));
		List<PoweredCruiseSkewWakeValidationRun> runs = temporalTargets.stream()
				.map(temporal -> {
					Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit =
							transitByName.get(targetName(temporal));
					if (transit == null) {
						throw new IllegalArgumentException("missing transit target for " + targetName(temporal));
					}
					return run(transit, temporal, readiness);
				})
				.toList();
		return new PoweredCruiseSkewWakeValidationRunMatrixAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				RUN_SAMPLE_COUNT,
				RUN_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				runs,
				extrema(runs)
		);
	}

	public static PoweredCruiseSkewWakeValidationRun run(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit,
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget temporal,
			Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary readiness
	) {
		if (transit == null || temporal == null || readiness == null) {
			throw new IllegalArgumentException("transit target, temporal target, and readiness are required.");
		}
		if (!targetName(transit).equals(targetName(temporal))) {
			throw new IllegalArgumentException("transit and temporal target names must match.");
		}
		boolean readinessOpen = readiness.liveValidationRunAllowed();
		boolean runtimeCouplingBlocked = !transit.runtimeCouplingAllowed()
				&& !temporal.runtimeCouplingAllowed();
		boolean validationRunAllowed = readinessOpen
				&& transit.skewWakeProbeApiAvailable()
				&& temporal.transientProbeApiAvailable()
				&& runtimeCouplingBlocked;
		String status = validationRunAllowed ? "PENDING" : "SKIPPED";
		String message = messageFor(transit, temporal, readiness, validationRunAllowed, runtimeCouplingBlocked);
		return new PoweredCruiseSkewWakeValidationRun(
				transit.presetName(),
				transit.spinState(),
				transit.rotorIndex(),
				transit.axialPlaneIndex(),
				transit.sweepColumnIndex(),
				transit.axialPlaneFraction(),
				transit.axialDistanceMeters(),
				transit.freestreamSweepDistanceMeters(),
				transit.lateralOffsetMeters(),
				transit.centerlineDistanceMeters(),
				transit.distanceFromRotorMeters(),
				transit.expectedAxialWakeVelocityMetersPerSecond(),
				transit.expectedFreestreamVelocityMetersPerSecond(),
				transit.expectedResultantWakeVelocityMetersPerSecond(),
				transit.centerlineResultantTransitSeconds(),
				transit.lateralAdjustedTransitSeconds(),
				temporal.recommendedMaxSamplePeriodSeconds(),
				readiness.appliedMaxSamplePeriodSeconds(),
				temporal.requiredSubstepsPerMinecraftTick(),
				temporal.requiredSubstepsPerOneKilohertzFrame(),
				transit.expectedAxialWakePressurePascals(),
				transit.expectedResultantDynamicPressurePascals(),
				transit.perRotorAxialMomentumFluxNewtons(),
				readiness.poweredSourceApiAvailable(),
				readiness.skewWakeProbeApiAvailable(),
				readiness.transientProbeApiAvailable(),
				transit.skewWakeProbeApiAvailable(),
				temporal.transientProbeApiAvailable(),
				readinessOpen,
				validationRunAllowed,
				true,
				true,
				true,
				true,
				false,
				false,
				false,
				false,
				false,
				false,
				false,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				status,
				message,
				"audit-only-unvalidated-cruise-skew-wake-validation-run"
		);
	}

	private static String messageFor(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit,
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget temporal,
			Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary readiness,
			boolean validationRunAllowed,
			boolean runtimeCouplingBlocked
	) {
		if (validationRunAllowed) {
			return "cruise-skew-wake-validator-not-invoked";
		}
		if (!readiness.liveValidationRunAllowed()) {
			return "cruise-skew-wake-readiness-gate-blocked";
		}
		if (!transit.skewWakeProbeApiAvailable()) {
			return "skew-wake-probe-target-unavailable";
		}
		if (!temporal.transientProbeApiAvailable()) {
			return "transient-probe-target-unavailable";
		}
		if (!runtimeCouplingBlocked) {
			return "runtime-coupling-not-allowed-for-validation";
		}
		return "cruise-skew-wake-validation-unavailable";
	}

	private static String targetName(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget target
	) {
		return target.presetName()
				+ ":plane" + target.axialPlaneIndex()
				+ ":sweep" + target.sweepColumnIndex()
				+ ":rotor" + target.rotorIndex();
	}

	private static String targetName(
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target
	) {
		return target.presetName()
				+ ":plane" + target.axialPlaneIndex()
				+ ":sweep" + target.sweepColumnIndex()
				+ ":rotor" + target.rotorIndex();
	}

	private static PoweredCruiseSkewWakeValidationRunExtrema extrema(
			List<PoweredCruiseSkewWakeValidationRun> runs
	) {
		Set<String> sourceTerms = new HashSet<>();
		int centerlineSweep = 0;
		int lateralSweep = 0;
		int readinessOpen = 0;
		int validationAllowed = 0;
		int targetSkewProbeAvailable = 0;
		int targetTransientProbeAvailable = 0;
		int invoked = 0;
		int available = 0;
		int pressureEvidence = 0;
		int velocityEvidence = 0;
		int transitEvidence = 0;
		int momentumEvidence = 0;
		int skippedForReadiness = 0;
		int pendingExecutor = 0;
		int maxMinecraftSubsteps = 0;
		double maxRequiredRate = 0.0;
		for (PoweredCruiseSkewWakeValidationRun run : runs) {
			sourceTerms.add(run.presetName() + ":rotor" + run.rotorIndex());
			if (run.sweepColumnIndex() == 0) {
				centerlineSweep++;
			} else {
				lateralSweep++;
			}
			if (run.readinessGateOpen()) {
				readinessOpen++;
			}
			if (run.validationRunAllowed()) {
				validationAllowed++;
			}
			if (run.targetSkewWakeProbeApiAvailable()) {
				targetSkewProbeAvailable++;
			}
			if (run.targetTransientProbeApiAvailable()) {
				targetTransientProbeAvailable++;
			}
			if (run.invoked()) {
				invoked++;
			}
			if (run.available()) {
				available++;
			}
			if (run.hasPressureEvidence()) {
				pressureEvidence++;
			}
			if (run.hasVelocityEvidence()) {
				velocityEvidence++;
			}
			if (run.hasTransitEvidence()) {
				transitEvidence++;
			}
			if (run.hasMomentumEvidence()) {
				momentumEvidence++;
			}
			if ("cruise-skew-wake-readiness-gate-blocked".equals(run.message())) {
				skippedForReadiness++;
			}
			if ("cruise-skew-wake-validator-not-invoked".equals(run.message())) {
				pendingExecutor++;
			}
			maxMinecraftSubsteps = Math.max(maxMinecraftSubsteps, run.requiredSubstepsPerMinecraftTick());
			maxRequiredRate = Math.max(maxRequiredRate, ratio(1.0, run.requiredMaxSamplePeriodSeconds()));
		}
		return new PoweredCruiseSkewWakeValidationRunExtrema(
				runs.size(),
				sourceTerms.size(),
				centerlineSweep,
				lateralSweep,
				readinessOpen,
				validationAllowed,
				targetSkewProbeAvailable,
				targetTransientProbeAvailable,
				invoked,
				available,
				pressureEvidence,
				velocityEvidence,
				transitEvidence,
				momentumEvidence,
				skippedForReadiness,
				pendingExecutor,
				maxMinecraftSubsteps,
				maxRequiredRate
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
