package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Rotor-State-Dispersion-Diagnostic-Packet";
	public static final String CAVEAT =
			"Rotor-state dispersion diagnostic compares APDrone neutral and derated free-flight settled punchout states at the existing peak samples; rows are lab evidence only and do not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final double STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND = 0.75;
	public static final double PROPELLER_SCALE_RANGE_THRESHOLD = 0.12;
	public static final double DROP_OVER_ARCHIVE_GAP_THRESHOLD = 1.50;
	public static final double RESIDUAL_DEFICIT_THRESHOLD = 0.04;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SPEED_SAMPLE_COUNT = 5;
	public static final int DISPERSION_ROW_COUNT = SPEED_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 27;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ DISPERSION_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double REVIEW_COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	private static final double REVIEW_DT_SECONDS = 0.005;
	private static final double REVIEW_THROTTLE_COMMAND = 1.0;
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic() {
	}

	public record RotorStateDispersionRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double forwardSpeedMetersPerSecond,
			int peakSampleIndex,
			double peakTimeSeconds,
			double thrustLossOverContractRatio,
			double stateVelocityDeltaMetersPerSecond,
			double attitudeEulerDeltaRadians,
			double angularVelocityDeltaRadiansPerSecond,
			double propellerScaleDropAverage,
			double propellerScaleDropMax,
			double propellerScaleDropRange,
			double deratedAveragePropellerThrustScale,
			double deratedMinPropellerThrustScale,
			double deratedMaxPropellerThrustScale,
			double deratedPropellerThrustScaleRange,
			double deratedAverageAdvanceRatio,
			double deratedAdvanceRatioRange,
			double deratedRotorThrustRangeNewtons,
			double deratedAverageRotorStallIntensity,
			double deratedAverageBladeDissymmetryIntensity,
			double deratedAverageBladeElementStallIntensity,
			double deratedAverageConingIntensity,
			double residualAfterCompressibilityRatio,
			double residualThrustDeficitRatio,
			double propellerScaleDropOverFormulaArchiveGap,
			boolean freeFlightStateDivergence,
			boolean propellerScaleSpreadLarge,
			boolean propellerScaleDropExceedsArchiveGap,
			boolean residualDeficitAboveThreshold,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String dominantDispersionBucket,
			String status,
			String message
	) {
	}

	public record RotorStateDispersionSummary(
			int rowCount,
			int failedRowCount,
			int freeFlightStateDivergenceRowCount,
			int propellerScaleSpreadLargeRowCount,
			int propellerScaleDropExceedsArchiveGapRowCount,
			int residualDeficitAboveThresholdRowCount,
			double maxStateVelocityDeltaMetersPerSecond,
			double maxAttitudeEulerDeltaRadians,
			double maxAngularVelocityDeltaRadiansPerSecond,
			double maxPropellerScaleDropAverage,
			double maxPropellerScaleDropMax,
			double maxPropellerScaleDropRange,
			double maxDeratedPropellerThrustScaleRange,
			double maxDeratedAdvanceRatioRange,
			double maxDeratedRotorThrustRangeNewtons,
			double maxDeratedAverageBladeElementStallIntensity,
			double maxDeratedAverageRotorStallIntensity,
			double maxResidualThrustDeficitRatio,
			double blackboxSpeedStateVelocityDeltaMetersPerSecond,
			double blackboxSpeedPropellerScaleDropAverage,
			double blackboxSpeedResidualThrustDeficitRatio,
			double fastSpeedStateVelocityDeltaMetersPerSecond,
			double fastSpeedPropellerScaleDropAverage,
			double fastSpeedDeratedPropellerThrustScaleRange,
			String dominantDispersionBucket,
			String nextRequiredAction,
			boolean rotorStateDispersionDiagnosticPassed
	) {
	}

	public record RotorStateDispersionAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int speedSampleCount,
			int dispersionRowCount,
			int summaryRowCount,
			int methodRowCount,
			double stateDivergenceVelocityDeltaThresholdMetersPerSecond,
			double propellerScaleRangeThreshold,
			double dropOverArchiveGapThreshold,
			double residualDeficitThreshold,
			List<RotorStateDispersionRow> rows,
			RotorStateDispersionSummary summary
	) {
		public RotorStateDispersionAudit {
			rows = List.copyOf(rows);
		}
	}

	public static RotorStateDispersionAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c");
		List<RotorStateDispersionRow> rows = new ArrayList<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow settledRow
				: PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
						.audit()
						.rows()) {
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
					.ForwardAdvanceScaleGapRow gapRow =
							PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
									.row(settledRow.forwardSpeedMetersPerSecond());
			rows.add(row(contract, settledRow, gapRow));
		}
		return new RotorStateDispersionAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SPEED_SAMPLE_COUNT,
				DISPERSION_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND,
				PROPELLER_SCALE_RANGE_THRESHOLD,
				DROP_OVER_ARCHIVE_GAP_THRESHOLD,
				RESIDUAL_DEFICIT_THRESHOLD,
				rows,
				summary(rows)
		);
	}

	public static RotorStateDispersionRow row(double forwardSpeedMetersPerSecond) {
		return audit().rows().stream()
				.filter(row -> Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown APDrone rotor-state dispersion row: " + forwardSpeedMetersPerSecond));
	}

	private static RotorStateDispersionRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
					.SettledThrustModelBreakdownRow settledRow,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
					.ForwardAdvanceScaleGapRow gapRow
	) {
		DispersionMetrics metrics = simulate(
				contract.targetMaxRpmScale(),
				settledRow.forwardSpeedMetersPerSecond(),
				settledRow.peakSampleIndex());
		boolean freeFlightStateDivergence = metrics.stateVelocityDeltaMetersPerSecond()
				> STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND;
		boolean propellerSpread = metrics.deratedPropellerThrustScaleRange() > PROPELLER_SCALE_RANGE_THRESHOLD;
		boolean dropExceedsGap = gapRow.propellerScaleDropOverFormulaArchiveGap() > DROP_OVER_ARCHIVE_GAP_THRESHOLD;
		boolean residualDeficit = settledRow.residualThrustDeficitRatio() > RESIDUAL_DEFICIT_THRESHOLD;
		String bucket = bucketFor(freeFlightStateDivergence, propellerSpread, dropExceedsGap, residualDeficit);
		return new RotorStateDispersionRow(
				settledRow.scenarioName(),
				settledRow.presetName(),
				settledRow.ambientCaseName(),
				settledRow.forwardSpeedMetersPerSecond(),
				settledRow.peakSampleIndex(),
				settledRow.peakTimeSeconds(),
				settledRow.thrustLossOverContractRatio(),
				metrics.stateVelocityDeltaMetersPerSecond(),
				metrics.attitudeEulerDeltaRadians(),
				metrics.angularVelocityDeltaRadiansPerSecond(),
				metrics.propellerScaleDropAverage(),
				metrics.propellerScaleDropMax(),
				metrics.propellerScaleDropRange(),
				metrics.deratedAveragePropellerThrustScale(),
				metrics.deratedMinPropellerThrustScale(),
				metrics.deratedMaxPropellerThrustScale(),
				metrics.deratedPropellerThrustScaleRange(),
				metrics.deratedAverageAdvanceRatio(),
				metrics.deratedAdvanceRatioRange(),
				metrics.deratedRotorThrustRangeNewtons(),
				metrics.deratedAverageRotorStallIntensity(),
				metrics.deratedAverageBladeDissymmetryIntensity(),
				metrics.deratedAverageBladeElementStallIntensity(),
				metrics.deratedAverageConingIntensity(),
				settledRow.residualAfterCompressibilityRatio(),
				settledRow.residualThrustDeficitRatio(),
				gapRow.propellerScaleDropOverFormulaArchiveGap(),
				freeFlightStateDivergence,
				propellerSpread,
				dropExceedsGap,
				residualDeficit,
				false,
				false,
				false,
				bucket,
				"BLOCKED",
				messageFor(bucket)
		);
	}

	private static RotorStateDispersionSummary summary(List<RotorStateDispersionRow> rows) {
		int failed = 0;
		int stateDiverged = 0;
		int propellerSpread = 0;
		int dropExceedsGap = 0;
		int residualDeficit = 0;
		double maxVelocityDelta = 0.0;
		double maxEulerDelta = 0.0;
		double maxAngularDelta = 0.0;
		double maxDropAverage = 0.0;
		double maxDropMax = 0.0;
		double maxDropRange = 0.0;
		double maxDeratedPropRange = 0.0;
		double maxDeratedAdvanceRange = 0.0;
		double maxDeratedThrustRange = 0.0;
		double maxBladeElement = 0.0;
		double maxRotorStall = 0.0;
		double maxResidualDeficit = 0.0;
		double blackboxVelocityDelta = 0.0;
		double blackboxDropAverage = 0.0;
		double blackboxResidualDeficit = 0.0;
		double fastVelocityDelta = 0.0;
		double fastDropAverage = 0.0;
		double fastDeratedPropRange = 0.0;
		for (RotorStateDispersionRow row : rows) {
			if (row.thrustLossOverContractRatio() > 0.0) {
				failed++;
			}
			if (row.freeFlightStateDivergence()) {
				stateDiverged++;
			}
			if (row.propellerScaleSpreadLarge()) {
				propellerSpread++;
			}
			if (row.propellerScaleDropExceedsArchiveGap()) {
				dropExceedsGap++;
			}
			if (row.residualDeficitAboveThreshold()) {
				residualDeficit++;
			}
			maxVelocityDelta = Math.max(maxVelocityDelta, row.stateVelocityDeltaMetersPerSecond());
			maxEulerDelta = Math.max(maxEulerDelta, row.attitudeEulerDeltaRadians());
			maxAngularDelta = Math.max(maxAngularDelta, row.angularVelocityDeltaRadiansPerSecond());
			maxDropAverage = Math.max(maxDropAverage, row.propellerScaleDropAverage());
			maxDropMax = Math.max(maxDropMax, row.propellerScaleDropMax());
			maxDropRange = Math.max(maxDropRange, row.propellerScaleDropRange());
			maxDeratedPropRange = Math.max(maxDeratedPropRange, row.deratedPropellerThrustScaleRange());
			maxDeratedAdvanceRange = Math.max(maxDeratedAdvanceRange, row.deratedAdvanceRatioRange());
			maxDeratedThrustRange = Math.max(maxDeratedThrustRange, row.deratedRotorThrustRangeNewtons());
			maxBladeElement = Math.max(maxBladeElement, row.deratedAverageBladeElementStallIntensity());
			maxRotorStall = Math.max(maxRotorStall, row.deratedAverageRotorStallIntensity());
			maxResidualDeficit = Math.max(maxResidualDeficit, row.residualThrustDeficitRatio());
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 22.0) == 0) {
				blackboxVelocityDelta = row.stateVelocityDeltaMetersPerSecond();
				blackboxDropAverage = row.propellerScaleDropAverage();
				blackboxResidualDeficit = row.residualThrustDeficitRatio();
			}
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 28.0) == 0) {
				fastVelocityDelta = row.stateVelocityDeltaMetersPerSecond();
				fastDropAverage = row.propellerScaleDropAverage();
				fastDeratedPropRange = row.deratedPropellerThrustScaleRange();
			}
		}
		boolean passed = failed == 0;
		return new RotorStateDispersionSummary(
				rows.size(),
				failed,
				stateDiverged,
				propellerSpread,
				dropExceedsGap,
				residualDeficit,
				maxVelocityDelta,
				maxEulerDelta,
				maxAngularDelta,
				maxDropAverage,
				maxDropMax,
				maxDropRange,
				maxDeratedPropRange,
				maxDeratedAdvanceRange,
				maxDeratedThrustRange,
				maxBladeElement,
				maxRotorStall,
				maxResidualDeficit,
				blackboxVelocityDelta,
				blackboxDropAverage,
				blackboxResidualDeficit,
				fastVelocityDelta,
				fastDropAverage,
				fastDeratedPropRange,
				stateDiverged >= 3 && propellerSpread >= 4
						? "free-flight-state-divergence-with-per-rotor-propeller-scale-spread"
						: "residual-thrust-scale-before-state-dispersion",
				passed
						? "feed-rotor-state-dispersion-to-blackbox-acceptance"
						: "inspect-free-flight-state-hold-or-normalize-blackbox-punchout-before-residual-thrust-tuning",
				passed
		);
	}

	private static DispersionMetrics simulate(
			double targetMaxRpmScale,
			double forwardSpeedMetersPerSecond,
			int peakSampleIndex
	) {
		DroneConfig neutralConfig = reviewConfig(DroneConfig.apDrone(), 1.0);
		DroneConfig deratedConfig = reviewConfig(DroneConfig.apDrone(), targetMaxRpmScale);
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, forwardSpeedMetersPerSecond);
		neutral.state().setVelocityMetersPerSecond(forwardVelocity);
		derated.state().setVelocityMetersPerSecond(forwardVelocity);
		DroneEnvironment environment = coldAirEnvironment();
		DroneInput input = new DroneInput(REVIEW_THROTTLE_COMMAND, 0.0, 0.0, 0.0, true, true);
		for (int sample = 0; sample <= peakSampleIndex; sample++) {
			neutral.step(input, REVIEW_DT_SECONDS, environment);
			derated.step(input, REVIEW_DT_SECONDS, environment);
		}
		double[] neutralPropeller = neutral.state().rotorPropellerThrustScale();
		double[] deratedPropeller = derated.state().rotorPropellerThrustScale();
		double[] deratedAdvance = derated.state().rotorAdvanceRatio();
		double[] deratedThrust = derated.state().rotorThrustNewtons();
		double[] propellerDrop = new double[neutralPropeller.length];
		for (int i = 0; i < propellerDrop.length; i++) {
			propellerDrop[i] = neutralPropeller[i] - deratedPropeller[i];
		}
		Vec3 neutralEuler = neutral.state().orientation().toEulerXYZRadians();
		Vec3 deratedEuler = derated.state().orientation().toEulerXYZRadians();
		return new DispersionMetrics(
				neutral.state().velocityMetersPerSecond().subtract(derated.state().velocityMetersPerSecond()).length(),
				neutralEuler.subtract(deratedEuler).length(),
				neutral.state().angularVelocityBodyRadiansPerSecond()
						.subtract(derated.state().angularVelocityBodyRadiansPerSecond())
						.length(),
				average(propellerDrop),
				max(propellerDrop),
				range(propellerDrop),
				average(deratedPropeller),
				min(deratedPropeller),
				max(deratedPropeller),
				range(deratedPropeller),
				average(deratedAdvance),
				range(deratedAdvance),
				range(deratedThrust),
				derated.state().averageRotorStallIntensity(),
				derated.state().averageRotorBladeDissymmetryIntensity(),
				derated.state().averageRotorBladeElementStallIntensity(),
				derated.state().averageRotorConingIntensity()
		);
	}

	private static DroneConfig reviewConfig(DroneConfig config, double targetMaxRpmScale) {
		return config
				.withRotorTargetMaxRpmScale(targetMaxRpmScale)
				.withControlLink(0.0, 0.0, 0.020)
				.withControlReceiver(0.0, 0.0)
				.withEscCommandSignal(0.0, 0.0)
				.withRateSuper(Vec3.ZERO)
				.withPitchGains(ZERO_GAINS)
				.withYawGains(ZERO_GAINS)
				.withRollGains(ZERO_GAINS)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.65)
				.withMotorTimeConstantSeconds(0.006)
				.withBattery(
						config.nominalBatteryVoltage(),
						config.nominalBatteryVoltage() - 0.1,
						0.0,
						Math.max(20.0, config.batteryCapacityAmpHours()),
						Math.max(240.0, config.maxBatteryCurrentAmps()));
	}

	private static DroneEnvironment coldAirEnvironment() {
		return new DroneEnvironment(
				Vec3.ZERO,
				DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, REVIEW_COLD_AIR_TEMPERATURE_CELSIUS),
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				REVIEW_COLD_AIR_TEMPERATURE_CELSIUS);
	}

	private static String bucketFor(
			boolean freeFlightStateDivergence,
			boolean propellerSpread,
			boolean dropExceedsGap,
			boolean residualDeficit
	) {
		if (freeFlightStateDivergence && propellerSpread && dropExceedsGap) {
			return "free-flight-state-divergence-with-per-rotor-propeller-scale-spread";
		}
		if (residualDeficit) {
			return "residual-thrust-scale-before-state-dispersion";
		}
		return "local-propeller-scale-spread-without-large-state-divergence";
	}

	private static String messageFor(String bucket) {
		return switch (bucket) {
			case "free-flight-state-divergence-with-per-rotor-propeller-scale-spread" ->
					"free-flight-state-divergence-amplifies-per-rotor-propeller-scale-spread";
			case "residual-thrust-scale-before-state-dispersion" ->
					"residual-thrust-scale-remains-above-threshold";
			default -> "local-propeller-scale-spread-does-not-explain-global-failure";
		};
	}

	private static double average(double[] values) {
		double sum = 0.0;
		for (double value : values) {
			sum += value;
		}
		return values.length == 0 ? 0.0 : sum / values.length;
	}

	private static double min(double[] values) {
		double min = Double.POSITIVE_INFINITY;
		for (double value : values) {
			min = Math.min(min, value);
		}
		return Double.isInfinite(min) ? 0.0 : min;
	}

	private static double max(double[] values) {
		double max = Double.NEGATIVE_INFINITY;
		for (double value : values) {
			max = Math.max(max, value);
		}
		return Double.isInfinite(max) ? 0.0 : max;
	}

	private static double range(double[] values) {
		return max(values) - min(values);
	}

	private record DispersionMetrics(
			double stateVelocityDeltaMetersPerSecond,
			double attitudeEulerDeltaRadians,
			double angularVelocityDeltaRadiansPerSecond,
			double propellerScaleDropAverage,
			double propellerScaleDropMax,
			double propellerScaleDropRange,
			double deratedAveragePropellerThrustScale,
			double deratedMinPropellerThrustScale,
			double deratedMaxPropellerThrustScale,
			double deratedPropellerThrustScaleRange,
			double deratedAverageAdvanceRatio,
			double deratedAdvanceRatioRange,
			double deratedRotorThrustRangeNewtons,
			double deratedAverageRotorStallIntensity,
			double deratedAverageBladeDissymmetryIntensity,
			double deratedAverageBladeElementStallIntensity,
			double deratedAverageConingIntensity
	) {
	}
}
