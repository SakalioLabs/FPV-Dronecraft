package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;

public final class Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Torque-Closure-Feasibility-Packet";
	public static final String CAVEAT =
			"Powered-source torque-closure feasibility solves the rotor torque-scale energy band for axial plus swirl wake power; it is audit-only and never changes runtime config or gameplay feel.";
	public static final int SOURCE_REFERENCE_COUNT = 9;
	public static final int SPIN_STATE_SAMPLE_COUNT = 2;
	public static final int PRESET_SAMPLE_COUNT = 8;
	public static final int FEASIBILITY_METRIC_COUNT = 16;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * FEASIBILITY_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final List<PresetConfig> PRESETS = List.of(
			new PresetConfig("racingQuad", DroneConfig.racingQuad()),
			new PresetConfig("apDrone", DroneConfig.apDrone()),
			new PresetConfig("cinewhoop", DroneConfig.cinewhoop()),
			new PresetConfig("heavyLift", DroneConfig.heavyLift())
	);

	private Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility() {
	}

	public record PoweredSourceTorqueClosureRow(
			String spinState,
			String presetName,
			int rotorBudgetCount,
			double thrustWeightedYawTorquePerThrustMeter,
			double currentShaftPowerWatts,
			double axialMomentumPowerWatts,
			double currentSwirlPowerWatts,
			double currentAeroPowerDemandWatts,
			double currentShaftPowerMarginWatts,
			double currentShaftPowerMarginRatio,
			double torqueScaleDiscriminant,
			boolean torqueScaleClosurePossible,
			double lowerTorqueScaleForPowerClosure,
			double upperTorqueScaleForPowerClosure,
			double peakFeasibleTorqueScale,
			double maxFeasibleMarginWatts,
			double minimumMomentumPowerReductionForTorqueOnlyClosureWatts,
			boolean torqueCoefficientOnlySufficient,
			boolean jointWakeOrSourceCalibrationRequired,
			boolean liveMotorTorqueCurveRequired,
			boolean liveWakePowerCalibrationRequired,
			boolean liveSwirlProbeRequired,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceTorqueClosureExtrema(
			int rowCount,
			int torqueScaleClosurePossibleCount,
			int torqueScaleClosureImpossibleCount,
			int torqueCoefficientOnlySufficientCount,
			int jointWakeOrSourceCalibrationRequiredCount,
			int liveMotorTorqueCurveRequiredCount,
			int liveWakePowerCalibrationRequiredCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double minTorqueScaleDiscriminant,
			double minCurrentShaftPowerMarginRatio,
			double maxMomentumPowerReductionForTorqueOnlyClosureWatts,
			double maxFeasibleMarginWatts,
			double maxCurrentAeroPowerDemandWatts,
			double maxCurrentShaftPowerWatts
	) {
	}

	public record PoweredSourceTorqueClosureAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int spinStateSampleCount,
			int presetSampleCount,
			int feasibilityMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceTorqueClosureRow> rows,
			PoweredSourceTorqueClosureExtrema extrema
	) {
		public PoweredSourceTorqueClosureAudit {
			rows = List.copyOf(rows);
		}
	}

	private record PresetConfig(String presetName, DroneConfig config) {
	}

	private record SourceMapView(
			String presetName,
			String spinState,
			int rotorCount,
			double spinRatio,
			double totalMomentumPowerWatts,
			List<Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm> sourceTerms
	) {
	}

	private record FootprintView(
			String presetName,
			String spinState,
			int rotorCount,
			double massFlowKilogramsPerSecond,
			double farWakeEquivalentRadiusMeters
	) {
	}

	public static PoweredSourceTorqueClosureAudit audit() {
		List<PoweredSourceTorqueClosureRow> rows = List.of(
				row("hover", "racingQuad"),
				row("hover", "apDrone"),
				row("hover", "cinewhoop"),
				row("hover", "heavyLift"),
				row("cruise", "racingQuad"),
				row("cruise", "apDrone"),
				row("cruise", "cinewhoop"),
				row("cruise", "heavyLift")
		);
		return new PoweredSourceTorqueClosureAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SPIN_STATE_SAMPLE_COUNT,
				PRESET_SAMPLE_COUNT,
				FEASIBILITY_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceTorqueClosureRow row(String spinState, String presetName) {
		if (spinState == null || spinState.isBlank()) {
			throw new IllegalArgumentException("spinState must not be blank.");
		}
		if (presetName == null || presetName.isBlank()) {
			throw new IllegalArgumentException("presetName must not be blank.");
		}
		List<?> sourceMaps = sourceMaps(spinState);
		List<?> footprints = footprints(spinState);
		if (sourceMaps.size() != PRESETS.size() || footprints.size() != PRESETS.size()) {
			throw new IllegalStateException("source maps and footprints must cover all presets.");
		}
		for (int i = 0; i < PRESETS.size(); i++) {
			PresetConfig preset = PRESETS.get(i);
			if (presetName.equals(preset.presetName())) {
				return row(preset, sourceMapView(sourceMaps.get(i)), footprintView(footprints.get(i)));
			}
		}
		throw new IllegalArgumentException("unsupported presetName: " + presetName);
	}

	private static PoweredSourceTorqueClosureRow row(
			PresetConfig preset,
			SourceMapView sourceMap,
			FootprintView footprint
	) {
		if (!preset.presetName().equals(sourceMap.presetName())
				|| !preset.presetName().equals(footprint.presetName())) {
			throw new IllegalArgumentException("source map and footprint preset order must match.");
		}
		if (!sourceMap.spinState().equals(footprint.spinState())) {
			throw new IllegalArgumentException("source map and footprint spin state must match.");
		}
		if (sourceMap.rotorCount() != preset.config().rotors().size()
				|| sourceMap.sourceTerms().size() != preset.config().rotors().size()
				|| footprint.rotorCount() != preset.config().rotors().size()) {
			throw new IllegalArgumentException("source terms and footprints must match rotor count.");
		}

		double shaftPower = 0.0;
		double swirlPower = 0.0;
		double reactionTorqueSum = 0.0;
		double thrustSum = 0.0;
		double massFlowPerRotor = ratio(footprint.massFlowKilogramsPerSecond(), footprint.rotorCount());
		double swirlRadius = footprint.farWakeEquivalentRadiusMeters()
				* RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION;
		for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm term : sourceMap.sourceTerms()) {
			RotorSpec rotor = preset.config().rotors().get(term.rotorIndex());
			double angularVelocity = sourceMap.spinRatio() * rotor.maxOmegaRadiansPerSecond();
			double reactionTorque = term.thrustNewtons() * rotor.yawTorquePerThrustMeter();
			double targetTangentialVelocity = ratio(Math.abs(reactionTorque * rotor.spinDirection()),
					massFlowPerRotor * swirlRadius);
			shaftPower += reactionTorque * angularVelocity;
			swirlPower += 0.5 * massFlowPerRotor * targetTangentialVelocity * targetTangentialVelocity;
			reactionTorqueSum += reactionTorque;
			thrustSum += term.thrustNewtons();
		}

		double momentumPower = sourceMap.totalMomentumPowerWatts();
		double currentDemand = momentumPower + swirlPower;
		double currentMargin = shaftPower - currentDemand;
		double discriminant = shaftPower * shaftPower - 4.0 * swirlPower * momentumPower;
		boolean closurePossible = discriminant >= 0.0 && swirlPower > 0.0;
		double lowerScale = 0.0;
		double upperScale = 0.0;
		if (closurePossible) {
			double root = Math.sqrt(discriminant);
			lowerScale = (shaftPower - root) / (2.0 * swirlPower);
			upperScale = (shaftPower + root) / (2.0 * swirlPower);
		}
		double peakScale = swirlPower > 0.0 ? shaftPower / (2.0 * swirlPower) : 0.0;
		double maxFeasibleMargin = swirlPower > 0.0
				? shaftPower * shaftPower / (4.0 * swirlPower) - momentumPower
				: shaftPower - momentumPower;
		double requiredMomentumReduction = Math.max(0.0, -maxFeasibleMargin);
		boolean currentCoefficientSufficient = closurePossible && lowerScale <= 1.0 && upperScale >= 1.0;
		boolean jointCalibrationRequired = !closurePossible;
		return new PoweredSourceTorqueClosureRow(
				sourceMap.spinState(),
				preset.presetName(),
				sourceMap.sourceTerms().size(),
				ratio(reactionTorqueSum, thrustSum),
				shaftPower,
				momentumPower,
				swirlPower,
				currentDemand,
				currentMargin,
				ratio(currentMargin, currentDemand),
				discriminant,
				closurePossible,
				lowerScale,
				upperScale,
				peakScale,
				maxFeasibleMargin,
				requiredMomentumReduction,
				currentCoefficientSufficient,
				jointCalibrationRequired,
				true,
				true,
				true,
				false,
				false,
				jointCalibrationRequired
						? "calibrate-source-map-wake-footprint-and-motor-torque-together"
						: "capture-live-motor-torque-curve-and-powered-wake-power-telemetry",
				jointCalibrationRequired ? "TORQUE_ONLY_IMPOSSIBLE" : "TORQUE_BAND_TARGET_ONLY",
				jointCalibrationRequired
						? "powered-source-wake-demand-exceeds-torque-only-closure-envelope"
						: "current-torque-coefficient-inside-power-closure-band-live-evidence-missing"
		);
	}

	private static List<?> sourceMaps(String spinState) {
		return switch (spinState) {
			case "hover" -> Aerodynamics4McL2PoweredHoverSourceMap.audit().sourceMaps();
			case "cruise" -> Aerodynamics4McL2PoweredCruiseSourceMap.audit().sourceMaps();
			default -> throw new IllegalArgumentException("unsupported spinState: " + spinState);
		};
	}

	private static List<?> footprints(String spinState) {
		return switch (spinState) {
			case "hover" -> Aerodynamics4McL2PoweredHoverWakeFootprint.audit().footprints();
			case "cruise" -> Aerodynamics4McL2PoweredCruiseWakeFootprint.audit().footprints();
			default -> throw new IllegalArgumentException("unsupported spinState: " + spinState);
		};
	}

	private static SourceMapView sourceMapView(Object row) {
		if (row instanceof Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap hover) {
			return new SourceMapView(hover.presetName(), hover.spinState(), hover.rotorCount(),
					hover.spinRatio(), hover.totalMomentumPowerWatts(), hover.sourceTerms());
		}
		if (row instanceof Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap cruise) {
			return new SourceMapView(cruise.presetName(), cruise.spinState(), cruise.rotorCount(),
					cruise.spinRatio(), cruise.totalMomentumPowerWatts(), cruise.sourceTerms());
		}
		throw new IllegalArgumentException("unsupported source map row.");
	}

	private static FootprintView footprintView(Object row) {
		if (row instanceof Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint hover) {
			return new FootprintView(hover.presetName(), hover.spinState(), hover.rotorCount(),
					hover.massFlowKilogramsPerSecond(), hover.farWakeEquivalentRadiusMeters());
		}
		if (row instanceof Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint cruise) {
			return new FootprintView(cruise.presetName(), cruise.spinState(), cruise.rotorCount(),
					cruise.massFlowKilogramsPerSecond(), cruise.farWakeEquivalentRadiusMeters());
		}
		throw new IllegalArgumentException("unsupported wake footprint row.");
	}

	private static PoweredSourceTorqueClosureExtrema extrema(List<PoweredSourceTorqueClosureRow> rows) {
		int possible = 0;
		int sufficient = 0;
		int joint = 0;
		int motor = 0;
		int wake = 0;
		int runtime = 0;
		int gameplay = 0;
		double minDiscriminant = Double.POSITIVE_INFINITY;
		double minMarginRatio = Double.POSITIVE_INFINITY;
		double maxRequiredReduction = 0.0;
		double maxFeasibleMargin = Double.NEGATIVE_INFINITY;
		double maxDemand = 0.0;
		double maxShaft = 0.0;
		for (PoweredSourceTorqueClosureRow row : rows) {
			if (row.torqueScaleClosurePossible()) {
				possible++;
			}
			if (row.torqueCoefficientOnlySufficient()) {
				sufficient++;
			}
			if (row.jointWakeOrSourceCalibrationRequired()) {
				joint++;
			}
			if (row.liveMotorTorqueCurveRequired()) {
				motor++;
			}
			if (row.liveWakePowerCalibrationRequired()) {
				wake++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			minDiscriminant = Math.min(minDiscriminant, row.torqueScaleDiscriminant());
			minMarginRatio = Math.min(minMarginRatio, row.currentShaftPowerMarginRatio());
			maxRequiredReduction = Math.max(maxRequiredReduction,
					row.minimumMomentumPowerReductionForTorqueOnlyClosureWatts());
			maxFeasibleMargin = Math.max(maxFeasibleMargin, row.maxFeasibleMarginWatts());
			maxDemand = Math.max(maxDemand, row.currentAeroPowerDemandWatts());
			maxShaft = Math.max(maxShaft, row.currentShaftPowerWatts());
		}
		return new PoweredSourceTorqueClosureExtrema(
				rows.size(),
				possible,
				rows.size() - possible,
				sufficient,
				joint,
				motor,
				wake,
				runtime,
				gameplay,
				Double.isInfinite(minDiscriminant) ? 0.0 : minDiscriminant,
				Double.isInfinite(minMarginRatio) ? 0.0 : minMarginRatio,
				maxRequiredReduction,
				Double.isInfinite(maxFeasibleMargin) ? 0.0 : maxFeasibleMargin,
				maxDemand,
				maxShaft
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
