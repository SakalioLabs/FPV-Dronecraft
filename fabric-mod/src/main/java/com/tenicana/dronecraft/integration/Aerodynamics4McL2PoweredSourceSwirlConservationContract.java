package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;

public final class Aerodynamics4McL2PoweredSourceSwirlConservationContract {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Swirl-Conservation-Contract-Packet";
	public static final String CAVEAT =
			"Powered-source swirl conservation contract records rotor reaction-torque and wake angular-momentum targets for future live A4MC source-term executors; it is audit-only and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SPIN_STATE_SAMPLE_COUNT = 2;
	public static final int SPIN_STATE_METRIC_COUNT = 25;
	public static final int SUMMARY_METRIC_ROW_COUNT = 13;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SPIN_STATE_SAMPLE_COUNT * SPIN_STATE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final int SWIRL_PROBE_AZIMUTH_SAMPLE_COUNT = 3;
	private static final List<PresetConfig> PRESETS = List.of(
			new PresetConfig("racingQuad", DroneConfig.racingQuad()),
			new PresetConfig("apDrone", DroneConfig.apDrone()),
			new PresetConfig("cinewhoop", DroneConfig.cinewhoop()),
			new PresetConfig("heavyLift", DroneConfig.heavyLift())
	);

	private Aerodynamics4McL2PoweredSourceSwirlConservationContract() {
	}

	public record PoweredSourceSwirlConservationContractRow(
			String spinState,
			int sourceMapCount,
			int rotorSwirlTargetCount,
			int recommendedSwirlProbeCount,
			double maxPerRotorReactionTorqueNewtonMeters,
			double maxSignedWakeAngularMomentumFluxNewtonMeters,
			double maxSwirlRadiusMeters,
			double maxTargetTangentialWakeVelocityMetersPerSecond,
			double maxSwirlKineticPowerWatts,
			double maxSwirlPowerFractionOfMomentumPower,
			double maxNetSignedReactionTorqueNewtonMeters,
			double maxNetTorqueCancellationErrorRatio,
			boolean angularMomentumTargetSelfConsistent,
			boolean sourceMomentDeltaRequired,
			boolean wakeTangentialVelocityRequired,
			boolean wakeAngularMomentumResidualRequired,
			boolean livePoweredSourceEvidencePresent,
			boolean liveSwirlProbeEvidencePresent,
			boolean liveSwirlConservationAccepted,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String targetPayloadKind,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceSwirlConservationContractExtrema(
			int rowCount,
			int targetSelfConsistentCount,
			int liveSwirlConservationAcceptedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int sourceMomentDeltaRequiredCount,
			int wakeTangentialVelocityRequiredCount,
			int wakeAngularMomentumResidualRequiredCount,
			int totalRotorSwirlTargetCount,
			int maxRecommendedSwirlProbeCount,
			double maxTargetTangentialWakeVelocityMetersPerSecond,
			double maxSwirlPowerFractionOfMomentumPower,
			double maxNetTorqueCancellationErrorRatio
	) {
	}

	public record PoweredSourceSwirlConservationContractAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int spinStateSampleCount,
			int spinStateMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			int swirlProbeAzimuthSampleCount,
			List<PoweredSourceSwirlConservationContractRow> rows,
			PoweredSourceSwirlConservationContractExtrema extrema
	) {
		public PoweredSourceSwirlConservationContractAudit {
			rows = List.copyOf(rows);
		}
	}

	private record PresetConfig(String presetName, DroneConfig config) {
	}

	public static PoweredSourceSwirlConservationContractAudit audit() {
		List<PoweredSourceSwirlConservationContractRow> rows = List.of(
				hoverRow(),
				cruiseRow()
		);
		return new PoweredSourceSwirlConservationContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SPIN_STATE_SAMPLE_COUNT,
				SPIN_STATE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				SWIRL_PROBE_AZIMUTH_SAMPLE_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceSwirlConservationContractRow hoverRow() {
		List<Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap> sourceMaps =
				Aerodynamics4McL2PoweredHoverSourceMap.audit().sourceMaps();
		List<Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint> footprints =
				Aerodynamics4McL2PoweredHoverWakeFootprint.audit().footprints();
		return row("hover", sourceMaps, footprints);
	}

	public static PoweredSourceSwirlConservationContractRow cruiseRow() {
		List<Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap> sourceMaps =
				Aerodynamics4McL2PoweredCruiseSourceMap.audit().sourceMaps();
		List<Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint> footprints =
				Aerodynamics4McL2PoweredCruiseWakeFootprint.audit().footprints();
		return row("cruise", sourceMaps, footprints);
	}

	private static PoweredSourceSwirlConservationContractRow row(
			String spinState,
			List<?> sourceMaps,
			List<?> footprints
	) {
		if (spinState == null || spinState.isBlank()) {
			throw new IllegalArgumentException("spinState must not be blank.");
		}
		if (sourceMaps == null || footprints == null || sourceMaps.size() != PRESETS.size()
				|| footprints.size() != PRESETS.size()) {
			throw new IllegalArgumentException("source maps and footprints must cover all presets.");
		}
		int targetCount = 0;
		double maxReactionTorque = 0.0;
		double maxSignedFlux = 0.0;
		double maxSwirlRadius = 0.0;
		double maxTangentialVelocity = 0.0;
		double maxSwirlPower = 0.0;
		double maxSwirlPowerFraction = 0.0;
		double maxNetSignedTorque = 0.0;
		double maxCancellationRatio = 0.0;
		for (int i = 0; i < PRESETS.size(); i++) {
			PresetConfig preset = PRESETS.get(i);
			SourceMapView sourceMap = sourceMapView(sourceMaps.get(i));
			FootprintView footprint = footprintView(footprints.get(i));
			if (!spinState.equals(sourceMap.spinState()) || !spinState.equals(footprint.spinState())) {
				throw new IllegalArgumentException("source maps and footprints must match spinState.");
			}
			if (!preset.presetName().equals(sourceMap.presetName()) || !preset.presetName().equals(footprint.presetName())) {
				throw new IllegalArgumentException("source maps and footprints must use the preset order.");
			}
			if (sourceMap.sourceTerms().size() != preset.config().rotors().size()) {
				throw new IllegalArgumentException("source terms must match rotor count.");
			}
			double massFlowPerRotor = ratio(footprint.massFlowKilogramsPerSecond(), footprint.rotorCount());
			double swirlRadius = footprint.farWakeEquivalentRadiusMeters()
					* RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION;
			double netSignedTorque = 0.0;
			double sumAbsSignedTorque = 0.0;
			double swirlPowerSum = 0.0;
			for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm term : sourceMap.sourceTerms()) {
				RotorSpec rotor = preset.config().rotors().get(term.rotorIndex());
				double reactionTorque = term.thrustNewtons() * rotor.yawTorquePerThrustMeter();
				double signedAngularMomentumFlux = reactionTorque * rotor.spinDirection();
				double targetTangentialVelocity = ratio(Math.abs(signedAngularMomentumFlux),
						massFlowPerRotor * swirlRadius);
				double swirlPower = 0.5 * massFlowPerRotor * targetTangentialVelocity * targetTangentialVelocity;
				targetCount++;
				maxReactionTorque = Math.max(maxReactionTorque, reactionTorque);
				maxSignedFlux = Math.max(maxSignedFlux, Math.abs(signedAngularMomentumFlux));
				maxSwirlRadius = Math.max(maxSwirlRadius, swirlRadius);
				maxTangentialVelocity = Math.max(maxTangentialVelocity, targetTangentialVelocity);
				maxSwirlPower = Math.max(maxSwirlPower, swirlPower);
				netSignedTorque += signedAngularMomentumFlux;
				sumAbsSignedTorque += Math.abs(signedAngularMomentumFlux);
				swirlPowerSum += swirlPower;
			}
			maxNetSignedTorque = Math.max(maxNetSignedTorque, Math.abs(netSignedTorque));
			maxCancellationRatio = Math.max(maxCancellationRatio, ratio(Math.abs(netSignedTorque), sumAbsSignedTorque));
			maxSwirlPowerFraction = Math.max(maxSwirlPowerFraction,
					ratio(swirlPowerSum, footprint.totalMomentumPowerWatts()));
		}
		boolean targetSelfConsistent = targetCount == PRESETS.stream()
				.mapToInt(preset -> preset.config().rotors().size())
				.sum()
				&& maxCancellationRatio < 1.0e-12
				&& maxTangentialVelocity > 0.0;
		return new PoweredSourceSwirlConservationContractRow(
				spinState,
				sourceMaps.size(),
				targetCount,
				targetCount * SWIRL_PROBE_AZIMUTH_SAMPLE_COUNT,
				maxReactionTorque,
				maxSignedFlux,
				maxSwirlRadius,
				maxTangentialVelocity,
				maxSwirlPower,
				maxSwirlPowerFraction,
				maxNetSignedTorque,
				maxCancellationRatio,
				targetSelfConsistent,
				true,
				true,
				true,
				false,
				false,
				false,
				false,
				false,
				"per-rotor-powered-source-wake-swirl-angular-momentum-evidence",
				"capture-live-a4mc-powered-source-swirl-angular-momentum-residuals",
				"TARGET_ONLY",
				targetSelfConsistent
						? "powered-source-swirl-target-self-consistent-live-evidence-missing"
						: "powered-source-swirl-target-inconsistent"
		);
	}

	private record SourceMapView(
			String presetName,
			String spinState,
			List<Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm> sourceTerms
	) {
	}

	private record FootprintView(
			String presetName,
			String spinState,
			int rotorCount,
			double massFlowKilogramsPerSecond,
			double farWakeEquivalentRadiusMeters,
			double totalMomentumPowerWatts
	) {
	}

	private static SourceMapView sourceMapView(Object row) {
		if (row instanceof Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap hover) {
			return new SourceMapView(hover.presetName(), hover.spinState(), hover.sourceTerms());
		}
		if (row instanceof Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap cruise) {
			return new SourceMapView(cruise.presetName(), cruise.spinState(), cruise.sourceTerms());
		}
		throw new IllegalArgumentException("unsupported source map row.");
	}

	private static FootprintView footprintView(Object row) {
		if (row instanceof Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint hover) {
			return new FootprintView(hover.presetName(), hover.spinState(), hover.rotorCount(),
					hover.massFlowKilogramsPerSecond(), hover.farWakeEquivalentRadiusMeters(),
					hover.totalMomentumPowerWatts());
		}
		if (row instanceof Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint cruise) {
			return new FootprintView(cruise.presetName(), cruise.spinState(), cruise.rotorCount(),
					cruise.massFlowKilogramsPerSecond(), cruise.farWakeEquivalentRadiusMeters(),
					cruise.totalMomentumPowerWatts());
		}
		throw new IllegalArgumentException("unsupported wake footprint row.");
	}

	private static PoweredSourceSwirlConservationContractExtrema extrema(
			List<PoweredSourceSwirlConservationContractRow> rows
	) {
		int target = 0;
		int accepted = 0;
		int runtime = 0;
		int gameplay = 0;
		int moment = 0;
		int velocity = 0;
		int angular = 0;
		int targets = 0;
		int probes = 0;
		double maxVelocity = 0.0;
		double maxPowerFraction = 0.0;
		double maxCancellation = 0.0;
		for (PoweredSourceSwirlConservationContractRow row : rows) {
			if (row.angularMomentumTargetSelfConsistent()) {
				target++;
			}
			if (row.liveSwirlConservationAccepted()) {
				accepted++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			if (row.sourceMomentDeltaRequired()) {
				moment++;
			}
			if (row.wakeTangentialVelocityRequired()) {
				velocity++;
			}
			if (row.wakeAngularMomentumResidualRequired()) {
				angular++;
			}
			targets += row.rotorSwirlTargetCount();
			probes = Math.max(probes, row.recommendedSwirlProbeCount());
			maxVelocity = Math.max(maxVelocity, row.maxTargetTangentialWakeVelocityMetersPerSecond());
			maxPowerFraction = Math.max(maxPowerFraction, row.maxSwirlPowerFractionOfMomentumPower());
			maxCancellation = Math.max(maxCancellation, row.maxNetTorqueCancellationErrorRatio());
		}
		return new PoweredSourceSwirlConservationContractExtrema(
				rows.size(),
				target,
				accepted,
				runtime,
				gameplay,
				moment,
				velocity,
				angular,
				targets,
				probes,
				maxVelocity,
				maxPowerFraction,
				maxCancellation
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
