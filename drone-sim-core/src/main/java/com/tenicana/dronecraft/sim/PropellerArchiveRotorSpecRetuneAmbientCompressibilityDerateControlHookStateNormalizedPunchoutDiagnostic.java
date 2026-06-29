package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Punchout-Diagnostic-Packet";
	public static final String CAVEAT =
			"State-normalized punchout diagnostic replays APDrone settled peak samples with kinematics held to the same forward speed and attitude; rows are lab evidence only and do not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final double RESIDUAL_DEFICIT_THRESHOLD =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
					.RESIDUAL_DEFICIT_THRESHOLD;
	public static final double STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
					.STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND;
	public static final double STATE_NORMALIZED_RESIDUAL_REDUCTION_THRESHOLD = 0.50;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SPEED_SAMPLE_COUNT = 5;
	public static final int DIAGNOSTIC_ROW_COUNT = SPEED_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 25;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ DIAGNOSTIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double REVIEW_COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	private static final double REVIEW_DT_SECONDS = 0.005;
	private static final double REVIEW_THROTTLE_COMMAND = 1.0;
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic() {
	}

	public record StateNormalizedPunchoutRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double forwardSpeedMetersPerSecond,
			int peakSampleIndex,
			double peakTimeSeconds,
			double targetMaxRpmScale,
			double contractThrustRatio,
			double freeFlightObservedThrustRatio,
			double freeFlightCompressibilityProxyRatio,
			double freeFlightResidualThrustDeficitRatio,
			double freeFlightStateVelocityDeltaMetersPerSecond,
			double heldObservedThrustRatio,
			double heldCompressibilityProxyRatio,
			double heldResidualThrustDeficitRatio,
			double heldResidualReductionRatio,
			double heldObservedMinusFreeFlightRatio,
			double heldNeutralThrustNewtons,
			double heldDeratedThrustNewtons,
			double heldNeutralAveragePropellerThrustScale,
			double heldDeratedAveragePropellerThrustScale,
			double heldDeratedPropellerThrustScaleRange,
			double heldDeratedAdvanceRatioRange,
			double heldStateVelocityDeltaMetersPerSecond,
			double heldAttitudeEulerDeltaRadians,
			double heldAngularVelocityDeltaRadiansPerSecond,
			boolean freeFlightStateDivergence,
			boolean stateNormalizationReducedResidual,
			boolean heldResidualAboveThreshold,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String dominantBucket,
			String status,
			String message
	) {
	}

	public record StateNormalizedPunchoutSummary(
			int rowCount,
			int blockedRowCount,
			int freeFlightStateDivergenceRowCount,
			int stateNormalizationReducedResidualRowCount,
			int heldResidualAboveThresholdRowCount,
			double minFreeFlightObservedThrustRatio,
			double minHeldObservedThrustRatio,
			double maxFreeFlightResidualThrustDeficitRatio,
			double maxHeldResidualThrustDeficitRatio,
			double maxResidualReductionRatio,
			double minResidualReductionRatio,
			double maxHeldObservedMinusFreeFlightRatio,
			double maxHeldDeratedPropellerThrustScaleRange,
			double maxHeldDeratedAdvanceRatioRange,
			double maxHeldStateVelocityDeltaMetersPerSecond,
			double blackboxSpeedHeldObservedThrustRatio,
			double blackboxSpeedHeldResidualThrustDeficitRatio,
			double blackboxSpeedResidualReductionRatio,
			double fastSpeedHeldObservedThrustRatio,
			double fastSpeedHeldResidualThrustDeficitRatio,
			double fastSpeedResidualReductionRatio,
			String dominantBucket,
			String nextRequiredAction,
			boolean stateNormalizedPunchoutDiagnosticPassed
	) {
	}

	public record StateNormalizedPunchoutAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int speedSampleCount,
			int diagnosticRowCount,
			int summaryRowCount,
			int methodRowCount,
			double residualDeficitThreshold,
			double stateDivergenceVelocityDeltaThresholdMetersPerSecond,
			double stateNormalizedResidualReductionThreshold,
			List<StateNormalizedPunchoutRow> rows,
			StateNormalizedPunchoutSummary summary
	) {
		public StateNormalizedPunchoutAudit {
			rows = List.copyOf(rows);
		}
	}

	public static StateNormalizedPunchoutAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c");
		List<StateNormalizedPunchoutRow> rows = new ArrayList<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow settledRow
				: PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
						.audit()
						.rows()) {
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
					.RotorStateDispersionRow dispersionRow =
							PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
									.row(settledRow.forwardSpeedMetersPerSecond());
			rows.add(row(contract, settledRow, dispersionRow));
		}
		return new StateNormalizedPunchoutAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SPEED_SAMPLE_COUNT,
				DIAGNOSTIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RESIDUAL_DEFICIT_THRESHOLD,
				STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND,
				STATE_NORMALIZED_RESIDUAL_REDUCTION_THRESHOLD,
				rows,
				summary(rows)
		);
	}

	public static StateNormalizedPunchoutRow row(double forwardSpeedMetersPerSecond) {
		return audit().rows().stream()
				.filter(row -> Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown APDrone state-normalized punchout row: " + forwardSpeedMetersPerSecond));
	}

	private static StateNormalizedPunchoutRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
					.SettledThrustModelBreakdownRow settledRow,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
					.RotorStateDispersionRow dispersionRow
	) {
		NormalizedMetrics metrics = simulate(
				contract.targetMaxRpmScale(),
				settledRow.forwardSpeedMetersPerSecond(),
				settledRow.peakSampleIndex());
		double contractThrustRatio = 1.0 - contract.equivalentMaxThrustLossPercent() / 100.0;
		double residualReduction = (settledRow.residualThrustDeficitRatio()
				- metrics.residualThrustDeficitRatio())
				/ Math.max(EPSILON, settledRow.residualThrustDeficitRatio());
		boolean stateReducedResidual = residualReduction >= STATE_NORMALIZED_RESIDUAL_REDUCTION_THRESHOLD;
		boolean heldResidualAboveThreshold = metrics.residualThrustDeficitRatio() > RESIDUAL_DEFICIT_THRESHOLD;
		boolean freeFlightStateDivergence = dispersionRow.stateVelocityDeltaMetersPerSecond()
				> STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND;
		String bucket = bucketFor(freeFlightStateDivergence, stateReducedResidual, heldResidualAboveThreshold);
		return new StateNormalizedPunchoutRow(
				settledRow.scenarioName(),
				settledRow.presetName(),
				settledRow.ambientCaseName(),
				settledRow.forwardSpeedMetersPerSecond(),
				settledRow.peakSampleIndex(),
				settledRow.peakTimeSeconds(),
				contract.targetMaxRpmScale(),
				contractThrustRatio,
				settledRow.observedThrustRatio(),
				settledRow.compressibilityProxyRatio(),
				settledRow.residualThrustDeficitRatio(),
				dispersionRow.stateVelocityDeltaMetersPerSecond(),
				metrics.observedThrustRatio(),
				metrics.compressibilityProxyRatio(),
				metrics.residualThrustDeficitRatio(),
				residualReduction,
				metrics.observedThrustRatio() - settledRow.observedThrustRatio(),
				metrics.neutralThrustNewtons(),
				metrics.deratedThrustNewtons(),
				metrics.neutralAveragePropellerThrustScale(),
				metrics.deratedAveragePropellerThrustScale(),
				metrics.deratedPropellerThrustScaleRange(),
				metrics.deratedAdvanceRatioRange(),
				metrics.stateVelocityDeltaMetersPerSecond(),
				metrics.attitudeEulerDeltaRadians(),
				metrics.angularVelocityDeltaRadiansPerSecond(),
				freeFlightStateDivergence,
				stateReducedResidual,
				heldResidualAboveThreshold,
				false,
				false,
				false,
				bucket,
				heldResidualAboveThreshold ? "BLOCKED" : "STATE_NORMALIZED",
				messageFor(bucket)
		);
	}

	private static StateNormalizedPunchoutSummary summary(List<StateNormalizedPunchoutRow> rows) {
		int blocked = 0;
		int freeFlightDiverged = 0;
		int stateReduced = 0;
		int heldResidualAbove = 0;
		double minFreeObserved = Double.POSITIVE_INFINITY;
		double minHeldObserved = Double.POSITIVE_INFINITY;
		double maxFreeResidual = 0.0;
		double maxHeldResidual = 0.0;
		double maxReduction = Double.NEGATIVE_INFINITY;
		double minReduction = Double.POSITIVE_INFINITY;
		double maxObservedGain = Double.NEGATIVE_INFINITY;
		double maxHeldPropRange = 0.0;
		double maxHeldAdvanceRange = 0.0;
		double maxHeldStateDelta = 0.0;
		double blackboxHeldObserved = 0.0;
		double blackboxHeldResidual = 0.0;
		double blackboxReduction = 0.0;
		double fastHeldObserved = 0.0;
		double fastHeldResidual = 0.0;
		double fastReduction = 0.0;
		for (StateNormalizedPunchoutRow row : rows) {
			if (row.heldResidualAboveThreshold()) {
				blocked++;
			}
			if (row.freeFlightStateDivergence()) {
				freeFlightDiverged++;
			}
			if (row.stateNormalizationReducedResidual()) {
				stateReduced++;
			}
			if (row.heldResidualAboveThreshold()) {
				heldResidualAbove++;
			}
			minFreeObserved = Math.min(minFreeObserved, row.freeFlightObservedThrustRatio());
			minHeldObserved = Math.min(minHeldObserved, row.heldObservedThrustRatio());
			maxFreeResidual = Math.max(maxFreeResidual, row.freeFlightResidualThrustDeficitRatio());
			maxHeldResidual = Math.max(maxHeldResidual, row.heldResidualThrustDeficitRatio());
			maxReduction = Math.max(maxReduction, row.heldResidualReductionRatio());
			minReduction = Math.min(minReduction, row.heldResidualReductionRatio());
			maxObservedGain = Math.max(maxObservedGain, row.heldObservedMinusFreeFlightRatio());
			maxHeldPropRange = Math.max(maxHeldPropRange, row.heldDeratedPropellerThrustScaleRange());
			maxHeldAdvanceRange = Math.max(maxHeldAdvanceRange, row.heldDeratedAdvanceRatioRange());
			maxHeldStateDelta = Math.max(maxHeldStateDelta, row.heldStateVelocityDeltaMetersPerSecond());
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 22.0) == 0) {
				blackboxHeldObserved = row.heldObservedThrustRatio();
				blackboxHeldResidual = row.heldResidualThrustDeficitRatio();
				blackboxReduction = row.heldResidualReductionRatio();
			}
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 28.0) == 0) {
				fastHeldObserved = row.heldObservedThrustRatio();
				fastHeldResidual = row.heldResidualThrustDeficitRatio();
				fastReduction = row.heldResidualReductionRatio();
			}
		}
		boolean passed = heldResidualAbove == 0 && stateReduced >= freeFlightDiverged;
		String dominantBucket = stateReduced >= 3 && heldResidualAbove == 0
				? "free-flight-state-drift-dominates-residual"
				: heldResidualAbove > 0
						? "held-state-residual-still-above-threshold"
						: "state-normalized-harness-required-before-acceptance";
		return new StateNormalizedPunchoutSummary(
				rows.size(),
				blocked,
				freeFlightDiverged,
				stateReduced,
				heldResidualAbove,
				Double.isInfinite(minFreeObserved) ? 0.0 : minFreeObserved,
				Double.isInfinite(minHeldObserved) ? 0.0 : minHeldObserved,
				maxFreeResidual,
				maxHeldResidual,
				Double.isInfinite(maxReduction) ? 0.0 : maxReduction,
				Double.isInfinite(minReduction) ? 0.0 : minReduction,
				Double.isInfinite(maxObservedGain) ? 0.0 : maxObservedGain,
				maxHeldPropRange,
				maxHeldAdvanceRange,
				maxHeldStateDelta,
				blackboxHeldObserved,
				blackboxHeldResidual,
				blackboxReduction,
				fastHeldObserved,
				fastHeldResidual,
				fastReduction,
				dominantBucket,
				passed
						? "use-state-normalized-punchout-harness-before-residual-thrust-tuning"
						: "inspect-held-state-residual-thrust-deficit-before-acceptance",
				passed
		);
	}

	private static NormalizedMetrics simulate(
			double targetMaxRpmScale,
			double forwardSpeedMetersPerSecond,
			int peakSampleIndex
	) {
		DroneConfig neutralConfig = reviewConfig(DroneConfig.apDrone(), 1.0);
		DroneConfig deratedConfig = reviewConfig(DroneConfig.apDrone(), targetMaxRpmScale);
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, forwardSpeedMetersPerSecond);
		DroneEnvironment environment = coldAirEnvironment();
		DroneInput input = new DroneInput(REVIEW_THROTTLE_COMMAND, 0.0, 0.0, 0.0, true, true);
		for (int sample = 0; sample <= peakSampleIndex; sample++) {
			holdKinematics(neutral, forwardVelocity);
			holdKinematics(derated, forwardVelocity);
			neutral.step(input, REVIEW_DT_SECONDS, environment);
			derated.step(input, REVIEW_DT_SECONDS, environment);
			if (sample == peakSampleIndex) {
				NormalizedMetrics metrics = metricsAt(neutralConfig, neutral, derated);
				holdKinematics(neutral, forwardVelocity);
				holdKinematics(derated, forwardVelocity);
				return metrics.withHeldStateDelta(neutral, derated);
			}
			holdKinematics(neutral, forwardVelocity);
			holdKinematics(derated, forwardVelocity);
		}
		throw new IllegalStateException("peak sample was not simulated: " + peakSampleIndex);
	}

	private static NormalizedMetrics metricsAt(
			DroneConfig neutralConfig,
			DronePhysics neutral,
			DronePhysics derated
	) {
		double neutralThrust = 0.0;
		double deratedThrust = 0.0;
		double neutralCompressibilityProxy = 0.0;
		double deratedCompressibilityProxy = 0.0;
		double neutralPropellerScale = 0.0;
		double deratedPropellerScale = 0.0;
		double[] deratedPropeller = derated.state().rotorPropellerThrustScale();
		double[] deratedAdvance = derated.state().rotorAdvanceRatio();
		for (int rotorIndex = 0; rotorIndex < neutralConfig.rotors().size(); rotorIndex++) {
			RotorSpec rotor = neutralConfig.rotors().get(rotorIndex);
			double neutralOmegaSquared = rotor.thrustCoefficient()
					* neutral.state().motorOmegaRadiansPerSecond(rotorIndex)
					* neutral.state().motorOmegaRadiansPerSecond(rotorIndex);
			double deratedOmegaSquared = rotor.thrustCoefficient()
					* derated.state().motorOmegaRadiansPerSecond(rotorIndex)
					* derated.state().motorOmegaRadiansPerSecond(rotorIndex);
			double neutralPropeller = neutral.state().rotorPropellerThrustScale(rotorIndex);
			double deratedRotorPropellerScale = derated.state().rotorPropellerThrustScale(rotorIndex);
			double neutralInflow = neutral.state().rotorInducedLagThrustScale(rotorIndex);
			double deratedInflow = derated.state().rotorInducedLagThrustScale(rotorIndex);
			double neutralCompressibility = neutral.state().rotorCompressibilityThrustScale(rotorIndex);
			double deratedCompressibility = derated.state().rotorCompressibilityThrustScale(rotorIndex);
			neutralCompressibilityProxy += neutralOmegaSquared * neutralPropeller * neutralInflow
					* neutralCompressibility;
			deratedCompressibilityProxy += deratedOmegaSquared * deratedRotorPropellerScale * deratedInflow
					* deratedCompressibility;
			neutralThrust += Math.max(0.0, neutral.state().rotorThrustNewtons(rotorIndex));
			deratedThrust += Math.max(0.0, derated.state().rotorThrustNewtons(rotorIndex));
			neutralPropellerScale += neutralPropeller;
			deratedPropellerScale += deratedRotorPropellerScale;
		}
		int rotorCount = Math.max(1, neutralConfig.rotors().size());
		double observedRatio = deratedThrust / Math.max(EPSILON, neutralThrust);
		double compressibilityRatio = deratedCompressibilityProxy / Math.max(EPSILON, neutralCompressibilityProxy);
		return new NormalizedMetrics(
				observedRatio,
				compressibilityRatio,
				Math.max(0.0, compressibilityRatio - observedRatio),
				neutralThrust,
				deratedThrust,
				neutralPropellerScale / rotorCount,
				deratedPropellerScale / rotorCount,
				range(deratedPropeller),
				range(deratedAdvance),
				0.0,
				0.0,
				0.0
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

	private static void holdKinematics(DronePhysics physics, Vec3 forwardVelocity) {
		physics.state().setPositionMeters(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(forwardVelocity);
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
	}

	private static String bucketFor(
			boolean freeFlightStateDivergence,
			boolean stateReducedResidual,
			boolean heldResidualAboveThreshold
	) {
		if (heldResidualAboveThreshold) {
			return "held-state-residual-still-above-threshold";
		}
		if (freeFlightStateDivergence && stateReducedResidual) {
			return "free-flight-state-drift-dominates-residual";
		}
		if (stateReducedResidual) {
			return "state-normalization-removes-local-residual";
		}
		return "state-normalized-harness-required-before-acceptance";
	}

	private static String messageFor(String bucket) {
		return switch (bucket) {
			case "held-state-residual-still-above-threshold" ->
					"held-state-punchout-keeps-residual-deficit-above-threshold";
			case "free-flight-state-drift-dominates-residual" ->
					"held-state-punchout-removes-free-flight-residual-deficit";
			case "state-normalization-removes-local-residual" ->
					"state-normalization-removes-residual-without-large-free-flight-velocity-delta";
			default -> "state-normalized-harness-must-feed-acceptance-before-runtime-coupling";
		};
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

	private record NormalizedMetrics(
			double observedThrustRatio,
			double compressibilityProxyRatio,
			double residualThrustDeficitRatio,
			double neutralThrustNewtons,
			double deratedThrustNewtons,
			double neutralAveragePropellerThrustScale,
			double deratedAveragePropellerThrustScale,
			double deratedPropellerThrustScaleRange,
			double deratedAdvanceRatioRange,
			double stateVelocityDeltaMetersPerSecond,
			double attitudeEulerDeltaRadians,
			double angularVelocityDeltaRadiansPerSecond
	) {
		NormalizedMetrics withHeldStateDelta(DronePhysics neutral, DronePhysics derated) {
			Vec3 neutralEuler = neutral.state().orientation().toEulerXYZRadians();
			Vec3 deratedEuler = derated.state().orientation().toEulerXYZRadians();
			return new NormalizedMetrics(
					observedThrustRatio,
					compressibilityProxyRatio,
					residualThrustDeficitRatio,
					neutralThrustNewtons,
					deratedThrustNewtons,
					neutralAveragePropellerThrustScale,
					deratedAveragePropellerThrustScale,
					deratedPropellerThrustScaleRange,
					deratedAdvanceRatioRange,
					neutral.state().velocityMetersPerSecond()
							.subtract(derated.state().velocityMetersPerSecond())
							.length(),
					neutralEuler.subtract(deratedEuler).length(),
					neutral.state().angularVelocityBodyRadiansPerSecond()
							.subtract(derated.state().angularVelocityBodyRadiansPerSecond())
							.length()
			);
		}
	}
}
