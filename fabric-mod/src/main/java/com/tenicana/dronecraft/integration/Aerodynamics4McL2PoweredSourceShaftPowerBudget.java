package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;

public final class Aerodynamics4McL2PoweredSourceShaftPowerBudget {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Shaft-Power-Budget-Packet";
	public static final String CAVEAT =
			"Powered-source shaft-power budget compares rotor reaction-torque shaft power against axial and swirl wake-power demand; it is audit-only and never changes runtime config or gameplay feel.";
	public static final int SOURCE_REFERENCE_COUNT = 8;
	public static final int SPIN_STATE_SAMPLE_COUNT = 2;
	public static final int SPIN_STATE_METRIC_COUNT = 26;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SPIN_STATE_SAMPLE_COUNT * SPIN_STATE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final List<PresetConfig> PRESETS = List.of(
			new PresetConfig("racingQuad", DroneConfig.racingQuad()),
			new PresetConfig("apDrone", DroneConfig.apDrone()),
			new PresetConfig("cinewhoop", DroneConfig.cinewhoop()),
			new PresetConfig("heavyLift", DroneConfig.heavyLift())
	);

	private Aerodynamics4McL2PoweredSourceShaftPowerBudget() {
	}

	public record PoweredSourceShaftPowerBudgetRow(
			String spinState,
			int presetBudgetCount,
			int rotorBudgetCount,
			double totalShaftPowerWatts,
			double totalMomentumPowerWatts,
			double totalSwirlPowerWatts,
			double totalAeroPowerDemandWatts,
			double totalShaftPowerMarginWatts,
			double minPresetShaftPowerMarginRatio,
			double maxPresetShaftPowerMarginRatio,
			double maxPresetShaftPowerDeficitWatts,
			double maxPerRotorReactionTorqueNewtonMeters,
			double maxPerRotorAngularVelocityRadiansPerSecond,
			double maxPerRotorReactionPowerWatts,
			double maxSwirlPowerFractionOfAeroDemand,
			double maxShaftPowerFractionOfBatteryLimit,
			boolean shaftTelemetryRequired,
			boolean motorTorqueCalibrationRequired,
			boolean wakePowerClosureRequired,
			boolean swirlPowerClosureRequired,
			boolean budgetSelfConsistent,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceShaftPowerBudgetExtrema(
			int rowCount,
			int budgetSelfConsistentCount,
			int budgetInconsistentCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int totalRotorBudgetCount,
			double maxTotalShaftPowerWatts,
			double maxTotalAeroPowerDemandWatts,
			double maxPresetShaftPowerDeficitWatts,
			double minPresetShaftPowerMarginRatio,
			double maxShaftPowerFractionOfBatteryLimit,
			double maxPerRotorReactionPowerWatts
	) {
	}

	public record PoweredSourceShaftPowerBudgetAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int spinStateSampleCount,
			int spinStateMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceShaftPowerBudgetRow> rows,
			PoweredSourceShaftPowerBudgetExtrema extrema
	) {
		public PoweredSourceShaftPowerBudgetAudit {
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

	public static PoweredSourceShaftPowerBudgetAudit audit() {
		List<PoweredSourceShaftPowerBudgetRow> rows = List.of(
				hoverRow(),
				cruiseRow()
		);
		return new PoweredSourceShaftPowerBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SPIN_STATE_SAMPLE_COUNT,
				SPIN_STATE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceShaftPowerBudgetRow hoverRow() {
		return row(
				"hover",
				Aerodynamics4McL2PoweredHoverSourceMap.audit().sourceMaps(),
				Aerodynamics4McL2PoweredHoverWakeFootprint.audit().footprints()
		);
	}

	public static PoweredSourceShaftPowerBudgetRow cruiseRow() {
		return row(
				"cruise",
				Aerodynamics4McL2PoweredCruiseSourceMap.audit().sourceMaps(),
				Aerodynamics4McL2PoweredCruiseWakeFootprint.audit().footprints()
		);
	}

	private static PoweredSourceShaftPowerBudgetRow row(
			String spinState,
			List<?> sourceMaps,
			List<?> footprints
	) {
		if (spinState == null || spinState.isBlank()) {
			throw new IllegalArgumentException("spinState must not be blank.");
		}
		if (sourceMaps == null || footprints == null
				|| sourceMaps.size() != PRESETS.size()
				|| footprints.size() != PRESETS.size()) {
			throw new IllegalArgumentException("source maps and footprints must cover all presets.");
		}
		int rotorBudgetCount = 0;
		double totalShaftPower = 0.0;
		double totalMomentumPower = 0.0;
		double totalSwirlPower = 0.0;
		double minMarginRatio = Double.POSITIVE_INFINITY;
		double maxMarginRatio = Double.NEGATIVE_INFINITY;
		double maxDeficit = 0.0;
		double maxReactionTorque = 0.0;
		double maxAngularVelocity = 0.0;
		double maxReactionPower = 0.0;
		double maxSwirlPowerFraction = 0.0;
		double maxBatteryFraction = 0.0;
		for (int i = 0; i < PRESETS.size(); i++) {
			PresetConfig preset = PRESETS.get(i);
			SourceMapView sourceMap = sourceMapView(sourceMaps.get(i));
			FootprintView footprint = footprintView(footprints.get(i));
			if (!spinState.equals(sourceMap.spinState()) || !spinState.equals(footprint.spinState())) {
				throw new IllegalArgumentException("source maps and footprints must match spinState.");
			}
			if (!preset.presetName().equals(sourceMap.presetName())
					|| !preset.presetName().equals(footprint.presetName())) {
				throw new IllegalArgumentException("source maps and footprints must use the preset order.");
			}
			if (sourceMap.rotorCount() != preset.config().rotors().size()
					|| sourceMap.sourceTerms().size() != preset.config().rotors().size()
					|| footprint.rotorCount() != preset.config().rotors().size()) {
				throw new IllegalArgumentException("source terms and footprints must match rotor count.");
			}
			double presetShaftPower = 0.0;
			double presetSwirlPower = 0.0;
			double massFlowPerRotor = ratio(footprint.massFlowKilogramsPerSecond(), footprint.rotorCount());
			double swirlRadius = footprint.farWakeEquivalentRadiusMeters()
					* RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION;
			for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm term : sourceMap.sourceTerms()) {
				RotorSpec rotor = preset.config().rotors().get(term.rotorIndex());
				double angularVelocity = sourceMap.spinRatio() * rotor.maxOmegaRadiansPerSecond();
				double reactionTorque = term.thrustNewtons() * rotor.yawTorquePerThrustMeter();
				double reactionPower = reactionTorque * angularVelocity;
				double signedAngularMomentumFlux = reactionTorque * rotor.spinDirection();
				double targetTangentialVelocity = ratio(Math.abs(signedAngularMomentumFlux),
						massFlowPerRotor * swirlRadius);
				double swirlPower = 0.5 * massFlowPerRotor * targetTangentialVelocity * targetTangentialVelocity;
				rotorBudgetCount++;
				presetShaftPower += reactionPower;
				presetSwirlPower += swirlPower;
				maxReactionTorque = Math.max(maxReactionTorque, reactionTorque);
				maxAngularVelocity = Math.max(maxAngularVelocity, angularVelocity);
				maxReactionPower = Math.max(maxReactionPower, reactionPower);
			}
			double presetAeroDemand = sourceMap.totalMomentumPowerWatts() + presetSwirlPower;
			double margin = presetShaftPower - presetAeroDemand;
			double marginRatio = ratio(margin, presetAeroDemand);
			double batteryFraction = ratio(presetShaftPower,
					preset.config().nominalBatteryVoltage() * preset.config().maxBatteryCurrentAmps());
			totalShaftPower += presetShaftPower;
			totalMomentumPower += sourceMap.totalMomentumPowerWatts();
			totalSwirlPower += presetSwirlPower;
			minMarginRatio = Math.min(minMarginRatio, marginRatio);
			maxMarginRatio = Math.max(maxMarginRatio, marginRatio);
			maxDeficit = Math.max(maxDeficit, Math.max(0.0, -margin));
			maxSwirlPowerFraction = Math.max(maxSwirlPowerFraction, ratio(presetSwirlPower, presetAeroDemand));
			maxBatteryFraction = Math.max(maxBatteryFraction, batteryFraction);
		}
		double totalAeroDemand = totalMomentumPower + totalSwirlPower;
		double totalMargin = totalShaftPower - totalAeroDemand;
		boolean selfConsistent = maxDeficit <= 1.0e-9 && minMarginRatio >= 0.0;
		return new PoweredSourceShaftPowerBudgetRow(
				spinState,
				sourceMaps.size(),
				rotorBudgetCount,
				totalShaftPower,
				totalMomentumPower,
				totalSwirlPower,
				totalAeroDemand,
				totalMargin,
				Double.isInfinite(minMarginRatio) ? 0.0 : minMarginRatio,
				Double.isInfinite(maxMarginRatio) ? 0.0 : maxMarginRatio,
				maxDeficit,
				maxReactionTorque,
				maxAngularVelocity,
				maxReactionPower,
				maxSwirlPowerFraction,
				maxBatteryFraction,
				true,
				true,
				true,
				true,
				selfConsistent,
				false,
				false,
				selfConsistent
						? "capture-live-a4mc-powered-source-shaft-power-telemetry"
						: "calibrate-powered-source-shaft-power-and-rotor-torque-coefficients",
				selfConsistent ? "TARGET_ONLY" : "TARGET_INCONSISTENT",
				selfConsistent
						? "powered-source-shaft-power-budget-target-live-evidence-missing"
						: "powered-source-shaft-power-budget-deficit-detected"
		);
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

	private static PoweredSourceShaftPowerBudgetExtrema extrema(
			List<PoweredSourceShaftPowerBudgetRow> rows
	) {
		int selfConsistent = 0;
		int runtime = 0;
		int gameplay = 0;
		int rotorBudgets = 0;
		double maxShaftPower = 0.0;
		double maxAeroDemand = 0.0;
		double maxDeficit = 0.0;
		double minMarginRatio = Double.POSITIVE_INFINITY;
		double maxBatteryFraction = 0.0;
		double maxReactionPower = 0.0;
		for (PoweredSourceShaftPowerBudgetRow row : rows) {
			if (row.budgetSelfConsistent()) {
				selfConsistent++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			rotorBudgets += row.rotorBudgetCount();
			maxShaftPower = Math.max(maxShaftPower, row.totalShaftPowerWatts());
			maxAeroDemand = Math.max(maxAeroDemand, row.totalAeroPowerDemandWatts());
			maxDeficit = Math.max(maxDeficit, row.maxPresetShaftPowerDeficitWatts());
			minMarginRatio = Math.min(minMarginRatio, row.minPresetShaftPowerMarginRatio());
			maxBatteryFraction = Math.max(maxBatteryFraction, row.maxShaftPowerFractionOfBatteryLimit());
			maxReactionPower = Math.max(maxReactionPower, row.maxPerRotorReactionPowerWatts());
		}
		return new PoweredSourceShaftPowerBudgetExtrema(
				rows.size(),
				selfConsistent,
				rows.size() - selfConsistent,
				runtime,
				gameplay,
				rotorBudgets,
				maxShaftPower,
				maxAeroDemand,
				maxDeficit,
				Double.isInfinite(minMarginRatio) ? 0.0 : minMarginRatio,
				maxBatteryFraction,
				maxReactionPower
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
