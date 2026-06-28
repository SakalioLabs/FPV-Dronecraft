package com.tenicana.dronecraft.integration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Validation-Run-Matrix-Packet";
	public static final String CAVEAT =
			"Surface-wake validation run matrix records compact execution status only; current rows stay skipped until the surface wake readiness gate opens and a live A4MC transient probe executor exists.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int RUN_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.TEMPORAL_TARGET_COUNT;
	public static final int RUN_METRIC_COUNT = 41;
	public static final int SUMMARY_METRIC_ROW_COUNT = 15;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ RUN_SAMPLE_COUNT * RUN_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix() {
	}

	public record PoweredHoverSurfaceWakeValidationRun(
			String presetName,
			String spinState,
			String surfaceType,
			int rotorIndex,
			double clearanceOverRadius,
			double clearanceMeters,
			double rotorDiskRadiusMeters,
			double idealInducedVelocityMetersPerSecond,
			double farWakeVelocityMetersPerSecond,
			double fastFarWakeTransitSeconds,
			double meanWakeTransitSeconds,
			double slowDiskPlaneTransitSeconds,
			double requiredMaxSamplePeriodSeconds,
			double appliedMaxSamplePeriodSeconds,
			int requiredSubstepsPerMinecraftTick,
			double perRotorImpingementPressurePascals,
			double surfaceCurveMultiplier,
			double perRotorSurfaceCushionForceNewtons,
			boolean poweredSourceApiAvailable,
			boolean transientProbeApiAvailable,
			boolean targetTransientProbeApiAvailable,
			boolean readinessGateOpen,
			boolean validationRunAllowed,
			boolean requestPressureProbe,
			boolean requestVelocityProbe,
			boolean requestTransientSeries,
			boolean invoked,
			boolean succeeded,
			boolean available,
			boolean hasPressureEvidence,
			boolean hasVelocityEvidence,
			boolean hasTransitEvidence,
			double observedPressurePascals,
			double observedWakeVelocityMetersPerSecond,
			double observedArrivalTimeSeconds,
			double pressureErrorRatio,
			double velocityErrorRatio,
			double arrivalTimeErrorRatio,
			String status,
			String message,
			String runtimeInfo
	) {
	}

	public record PoweredHoverSurfaceWakeValidationRunExtrema(
			int runCount,
			int groundRunCount,
			int ceilingRunCount,
			int readinessGateOpenCount,
			int validationRunAllowedCount,
			int targetTransientProbeApiAvailableCount,
			int invokedCount,
			int availableCount,
			int pressureEvidenceCount,
			int velocityEvidenceCount,
			int transitEvidenceCount,
			int skippedForReadinessCount,
			int pendingExecutorCount,
			int maxRequiredSubstepsPerMinecraftTick,
			double maxRequiredSampleRateHertz
	) {
	}

	public record PoweredHoverSurfaceWakeValidationRunMatrixAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int runSampleCount,
			int runMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeValidationRun> runs,
			PoweredHoverSurfaceWakeValidationRunExtrema extrema
	) {
		public PoweredHoverSurfaceWakeValidationRunMatrixAudit {
			runs = List.copyOf(runs);
		}
	}

	public static PoweredHoverSurfaceWakeValidationRunMatrixAudit audit() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> temporalTargets =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.audit().targets();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> transitTargets =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit().targets();
		return audit(false, false, 0.05, temporalTargets, transitTargets);
	}

	public static PoweredHoverSurfaceWakeValidationRunMatrixAudit audit(
			boolean poweredSourceApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> temporalTargets,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> transitTargets
	) {
		if (temporalTargets == null || transitTargets == null) {
			throw new IllegalArgumentException("temporal and transit targets are required.");
		}
		Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessSummary readiness =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.gate(
						poweredSourceApiAvailable,
						transientProbeApiAvailable,
						appliedMaxSamplePeriodSeconds,
						temporalTargets
				);
		Map<String, Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> transitByName =
				transitTargets.stream()
						.collect(Collectors.toMap(
								Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix::targetName,
								Function.identity()
						));
		List<PoweredHoverSurfaceWakeValidationRun> runs = temporalTargets.stream()
				.map(temporal -> {
					Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget transit =
							transitByName.get(targetName(temporal));
					if (transit == null) {
						throw new IllegalArgumentException("missing transit target for " + targetName(temporal));
					}
					return run(transit, temporal, readiness);
				})
				.toList();
		return new PoweredHoverSurfaceWakeValidationRunMatrixAudit(
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

	public static PoweredHoverSurfaceWakeValidationRun run(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget transit,
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget temporal,
			Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessSummary readiness
	) {
		if (transit == null || temporal == null || readiness == null) {
			throw new IllegalArgumentException("transit target, temporal target, and readiness are required.");
		}
		if (!targetName(transit).equals(targetName(temporal))) {
			throw new IllegalArgumentException("transit and temporal target names must match.");
		}
		boolean readinessOpen = readiness.liveValidationRunAllowed();
		boolean validationRunAllowed = readinessOpen
				&& temporal.transientProbeApiAvailable()
				&& !temporal.runtimeCouplingAllowed();
		String status = validationRunAllowed ? "PENDING" : "SKIPPED";
		String message = messageFor(temporal, readiness, validationRunAllowed);
		return new PoweredHoverSurfaceWakeValidationRun(
				transit.presetName(),
				transit.spinState(),
				transit.surfaceType(),
				transit.rotorIndex(),
				transit.clearanceOverRadius(),
				transit.clearanceMeters(),
				transit.rotorDiskRadiusMeters(),
				transit.idealInducedVelocityMetersPerSecond(),
				transit.farWakeVelocityMetersPerSecond(),
				transit.fastFarWakeTransitSeconds(),
				transit.meanWakeTransitSeconds(),
				transit.slowDiskPlaneTransitSeconds(),
				temporal.recommendedMaxSamplePeriodSeconds(),
				readiness.appliedMaxSamplePeriodSeconds(),
				temporal.requiredSubstepsPerMinecraftTick(),
				transit.perRotorImpingementPressurePascals(),
				transit.surfaceCurveMultiplier(),
				transit.perRotorSurfaceCushionForceNewtons(),
				readiness.poweredSourceApiAvailable(),
				readiness.transientProbeApiAvailable(),
				temporal.transientProbeApiAvailable(),
				readinessOpen,
				validationRunAllowed,
				true,
				true,
				true,
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
				status,
				message,
				"audit-only-unvalidated-surface-wake-validation-run"
		);
	}

	private static String messageFor(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget temporal,
			Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate.PoweredHoverSurfaceWakeReadinessSummary readiness,
			boolean validationRunAllowed
	) {
		if (validationRunAllowed) {
			return "surface-wake-validator-not-invoked";
		}
		if (!readiness.liveValidationRunAllowed()) {
			return "surface-wake-readiness-gate-blocked";
		}
		if (!temporal.transientProbeApiAvailable()) {
			return "transient-probe-target-unavailable";
		}
		if (temporal.runtimeCouplingAllowed()) {
			return "runtime-coupling-not-allowed-for-validation";
		}
		return "surface-wake-validation-unavailable";
	}

	private static String targetName(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget target
	) {
		return target.presetName() + ":" + target.surfaceType() + ":hR"
				+ Double.toString(target.clearanceOverRadius()) + ":rotor" + target.rotorIndex();
	}

	private static String targetName(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target
	) {
		return target.presetName() + ":" + target.surfaceType() + ":hR"
				+ Double.toString(target.clearanceOverRadius()) + ":rotor" + target.rotorIndex();
	}

	private static PoweredHoverSurfaceWakeValidationRunExtrema extrema(
			List<PoweredHoverSurfaceWakeValidationRun> runs
	) {
		int ground = 0;
		int ceiling = 0;
		int readinessOpen = 0;
		int validationAllowed = 0;
		int targetProbeAvailable = 0;
		int invoked = 0;
		int available = 0;
		int pressureEvidence = 0;
		int velocityEvidence = 0;
		int transitEvidence = 0;
		int skippedForReadiness = 0;
		int pendingExecutor = 0;
		int maxMinecraftSubsteps = 0;
		double maxRequiredRate = 0.0;
		for (PoweredHoverSurfaceWakeValidationRun run : runs) {
			if ("ground".equals(run.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(run.surfaceType())) {
				ceiling++;
			}
			if (run.readinessGateOpen()) {
				readinessOpen++;
			}
			if (run.validationRunAllowed()) {
				validationAllowed++;
			}
			if (run.targetTransientProbeApiAvailable()) {
				targetProbeAvailable++;
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
			if ("surface-wake-readiness-gate-blocked".equals(run.message())) {
				skippedForReadiness++;
			}
			if ("surface-wake-validator-not-invoked".equals(run.message())) {
				pendingExecutor++;
			}
			maxMinecraftSubsteps = Math.max(maxMinecraftSubsteps, run.requiredSubstepsPerMinecraftTick());
			maxRequiredRate = Math.max(maxRequiredRate, ratio(1.0, run.requiredMaxSamplePeriodSeconds()));
		}
		return new PoweredHoverSurfaceWakeValidationRunExtrema(
				runs.size(),
				ground,
				ceiling,
				readinessOpen,
				validationAllowed,
				targetProbeAvailable,
				invoked,
				available,
				pressureEvidence,
				velocityEvidence,
				transitEvidence,
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
