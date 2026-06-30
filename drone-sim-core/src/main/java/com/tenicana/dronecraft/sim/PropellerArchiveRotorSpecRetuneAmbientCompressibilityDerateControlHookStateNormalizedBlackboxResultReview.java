package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Blackbox-Result-Review-Packet";
	public static final String CAVEAT =
			"State-normalized blackbox result review directly runs held-kinematics cold-air forward-punchout regressions for racingQuad and APDrone; rows are lab evidence only and do not enable free-flight acceptance, runtime coupling, playable export, or gameplay auto-apply.";
	public static final String REGRESSION_CASE_NAME = "cold_air_forward_punchout_margin";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int PRESET_SAMPLE_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT;
	public static final int RESULT_ROW_COUNT = PRESET_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ RESULT_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double REVIEW_COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	private static final double REVIEW_FORWARD_SPEED_METERS_PER_SECOND = 22.0;
	private static final double REVIEW_THROTTLE_COMMAND = 1.0;
	private static final double RESIDUAL_DEFICIT_THRESHOLD =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
					.RESIDUAL_DEFICIT_THRESHOLD;
	private static final double STATE_DELTA_EPSILON = 1.0e-12;
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);
	private static final List<String> PRESET_NAMES = List.of("racingQuad", "apDrone");

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview() {
	}

	public record StateNormalizedBlackboxResultReviewRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String regressionCaseName,
			String flightPhase,
			String targetMetric,
			int sampleCount,
			int minSampleCount,
			double dtSeconds,
			double forwardSpeedMetersPerSecond,
			double targetMaxRpmScale,
			double contractThrustRatio,
			double freeFlightSecondaryErrorRatio,
			double stateNormalizedPrimaryErrorRatio,
			double stateNormalizedSecondaryErrorRatio,
			double maxAllowedPrimaryErrorRatio,
			double maxAllowedSecondaryErrorRatio,
			int stateNormalizedPhysicalConstraintViolationCount,
			int maxMarginSampleIndex,
			double maxMarginTimeSeconds,
			double heldObservedThrustRatio,
			double heldCompressibilityProxyRatio,
			double heldResidualThrustDeficitRatio,
			double heldNeutralThrustNewtons,
			double heldDeratedThrustNewtons,
			double heldNeutralAveragePropellerThrustScale,
			double heldDeratedAveragePropellerThrustScale,
			double heldDeratedPropellerThrustScaleRange,
			double heldDeratedAdvanceRatioRange,
			double heldStateVelocityDeltaMetersPerSecond,
			double heldAttitudeEulerDeltaRadians,
			double heldAngularVelocityDeltaRadiansPerSecond,
			boolean stateNormalizedRegressionPassed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record StateNormalizedBlackboxResultReviewSummary(
			int rowCount,
			int passedRowCount,
			int failedRowCount,
			int freeFlightFailedRowCount,
			int stateNormalizedPhysicalConstraintViolationCount,
			double maxFreeFlightSecondaryErrorRatio,
			double maxStateNormalizedSecondaryErrorRatio,
			double maxHeldResidualThrustDeficitRatio,
			double maxHeldStateVelocityDeltaMetersPerSecond,
			double racingQuadHeldSecondaryErrorRatio,
			double racingQuadHeldResidualThrustDeficitRatio,
			double apDroneHeldSecondaryErrorRatio,
			double apDroneHeldObservedThrustRatio,
			double apDroneHeldResidualThrustDeficitRatio,
			int apDroneMaxMarginSampleIndex,
			double apDroneMaxMarginTimeSeconds,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			boolean stateNormalizedBlackboxResultReviewPassed
	) {
	}

	public record StateNormalizedBlackboxResultReviewAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int presetSampleCount,
			int resultRowCount,
			int summaryRowCount,
			int methodRowCount,
			double residualDeficitThreshold,
			double stateDeltaEpsilon,
			List<StateNormalizedBlackboxResultReviewRow> rows,
			StateNormalizedBlackboxResultReviewSummary summary
	) {
		public StateNormalizedBlackboxResultReviewAudit {
			rows = List.copyOf(rows);
		}
	}

	public static StateNormalizedBlackboxResultReviewAudit audit() {
		List<StateNormalizedBlackboxResultReviewRow> rows = PRESET_NAMES.stream()
				.map(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
						::row)
				.toList();
		return new StateNormalizedBlackboxResultReviewAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PRESET_SAMPLE_COUNT,
				RESULT_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RESIDUAL_DEFICIT_THRESHOLD,
				STATE_DELTA_EPSILON,
				rows,
				summary(rows)
		);
	}

	public static StateNormalizedBlackboxResultReviewRow row(String presetName) {
		return row(presetName, REGRESSION_CASE_NAME);
	}

	public static StateNormalizedBlackboxResultReviewRow row(
			String presetName,
			String regressionCaseName
	) {
		if (!REGRESSION_CASE_NAME.equals(regressionCaseName)) {
			throw new IllegalArgumentException(
					"unsupported state-normalized blackbox result case: " + regressionCaseName);
		}
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								presetName,
								"cold_sea_level_minus10c");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.BlackboxRegressionCaseDefinition caseDefinition =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.caseDefinition(REGRESSION_CASE_NAME);
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow freeFlight =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.row(presetName, REGRESSION_CASE_NAME);
		NormalizedBlackboxMetrics metrics = simulate(
				presetConfig(presetName),
				contract.targetMaxRpmScale(),
				contract.equivalentMaxThrustLossPercent(),
				caseDefinition);
		int violations = physicalConstraintViolations(caseDefinition, metrics);
		boolean passed = metrics.sampleCount() >= caseDefinition.minSampleCount()
				&& metrics.maxTipMachMarginViolationRatio() <= caseDefinition.maxPrimaryErrorRatio()
				&& metrics.maxThrustLossOverContractRatio() <= caseDefinition.maxSecondaryErrorRatio()
				&& violations == 0;
		return new StateNormalizedBlackboxResultReviewRow(
				contract.scenarioName(),
				contract.presetName(),
				contract.ambientCaseName(),
				caseDefinition.regressionCaseName(),
				caseDefinition.flightPhase(),
				caseDefinition.targetMetric(),
				metrics.sampleCount(),
				caseDefinition.minSampleCount(),
				caseDefinitionDtSeconds(),
				REVIEW_FORWARD_SPEED_METERS_PER_SECOND,
				contract.targetMaxRpmScale(),
				1.0 - contract.equivalentMaxThrustLossPercent() / 100.0,
				freeFlight.secondaryErrorRatio(),
				metrics.maxTipMachMarginViolationRatio(),
				metrics.maxThrustLossOverContractRatio(),
				caseDefinition.maxPrimaryErrorRatio(),
				caseDefinition.maxSecondaryErrorRatio(),
				violations,
				metrics.maxMarginSampleIndex(),
				(metrics.maxMarginSampleIndex() + 1) * caseDefinitionDtSeconds(),
				metrics.heldObservedThrustRatio(),
				metrics.heldCompressibilityProxyRatio(),
				metrics.heldResidualThrustDeficitRatio(),
				metrics.heldNeutralThrustNewtons(),
				metrics.heldDeratedThrustNewtons(),
				metrics.heldNeutralAveragePropellerThrustScale(),
				metrics.heldDeratedAveragePropellerThrustScale(),
				metrics.heldDeratedPropellerThrustScaleRange(),
				metrics.heldDeratedAdvanceRatioRange(),
				metrics.heldStateVelocityDeltaMetersPerSecond(),
				metrics.heldAttitudeEulerDeltaRadians(),
				metrics.heldAngularVelocityDeltaRadiansPerSecond(),
				passed,
				false,
				false,
				false,
				passed ? "PASS" : "FAIL",
				passed
						? "state-normalized-forward-punchout-regression-passed"
						: "state-normalized-forward-punchout-regression-failed"
		);
	}

	private static StateNormalizedBlackboxResultReviewSummary summary(
			List<StateNormalizedBlackboxResultReviewRow> rows
	) {
		int passed = 0;
		int freeFailed = 0;
		int violations = 0;
		double maxFreeSecondary = 0.0;
		double maxNormalizedSecondary = 0.0;
		double maxResidual = 0.0;
		double maxStateDelta = 0.0;
		double racingSecondary = 0.0;
		double racingResidual = 0.0;
		double apDroneSecondary = 0.0;
		double apDroneObserved = 0.0;
		double apDroneResidual = 0.0;
		int apDronePeakIndex = 0;
		double apDronePeakTime = 0.0;
		for (StateNormalizedBlackboxResultReviewRow row : rows) {
			if (row.stateNormalizedRegressionPassed()) {
				passed++;
			}
			if (row.freeFlightSecondaryErrorRatio() > row.maxAllowedSecondaryErrorRatio()) {
				freeFailed++;
			}
			violations += row.stateNormalizedPhysicalConstraintViolationCount();
			maxFreeSecondary = Math.max(maxFreeSecondary, row.freeFlightSecondaryErrorRatio());
			maxNormalizedSecondary = Math.max(maxNormalizedSecondary, row.stateNormalizedSecondaryErrorRatio());
			maxResidual = Math.max(maxResidual, row.heldResidualThrustDeficitRatio());
			maxStateDelta = Math.max(maxStateDelta, row.heldStateVelocityDeltaMetersPerSecond());
			if ("racingQuad".equals(row.presetName())) {
				racingSecondary = row.stateNormalizedSecondaryErrorRatio();
				racingResidual = row.heldResidualThrustDeficitRatio();
			}
			if ("apDrone".equals(row.presetName())) {
				apDroneSecondary = row.stateNormalizedSecondaryErrorRatio();
				apDroneObserved = row.heldObservedThrustRatio();
				apDroneResidual = row.heldResidualThrustDeficitRatio();
				apDronePeakIndex = row.maxMarginSampleIndex();
				apDronePeakTime = row.maxMarginTimeSeconds();
			}
		}
		return new StateNormalizedBlackboxResultReviewSummary(
				rows.size(),
				passed,
				rows.size() - passed,
				freeFailed,
				violations,
				maxFreeSecondary,
				maxNormalizedSecondary,
				maxResidual,
				maxStateDelta,
				racingSecondary,
				racingResidual,
				apDroneSecondary,
				apDroneObserved,
				apDroneResidual,
				apDronePeakIndex,
				apDronePeakTime,
				false,
				false,
				false,
				!rows.isEmpty() && passed == rows.size() && violations == 0
		);
	}

	private static NormalizedBlackboxMetrics simulate(
			DroneConfig baseConfig,
			double targetMaxRpmScale,
			double equivalentMaxThrustLossPercent,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.BlackboxRegressionCaseDefinition caseDefinition
	) {
		DroneConfig neutralConfig = reviewConfig(baseConfig, 1.0);
		DroneConfig deratedConfig = reviewConfig(baseConfig, targetMaxRpmScale);
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, REVIEW_FORWARD_SPEED_METERS_PER_SECOND);
		DroneEnvironment environment = coldAirEnvironment();
		DroneInput input = new DroneInput(REVIEW_THROTTLE_COMMAND, 0.0, 0.0, 0.0, true, true);
		NormalizedBlackboxAccumulator metrics = new NormalizedBlackboxAccumulator();
		for (int sample = 0; sample < caseDefinition.minSampleCount(); sample++) {
			holdKinematics(neutral, forwardVelocity);
			holdKinematics(derated, forwardVelocity);
			neutral.step(input, caseDefinitionDtSeconds(), environment);
			derated.step(input, caseDefinitionDtSeconds(), environment);
			metrics.record(sample, neutralConfig, neutral, derated, equivalentMaxThrustLossPercent);
			holdKinematics(neutral, forwardVelocity);
			holdKinematics(derated, forwardVelocity);
			metrics.recordPostClampState(neutral, derated);
		}
		return metrics.toMetrics(caseDefinition.minSampleCount());
	}

	private static int physicalConstraintViolations(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.BlackboxRegressionCaseDefinition caseDefinition,
			NormalizedBlackboxMetrics metrics
	) {
		int violations = 0;
		if (metrics.maxTipMachMarginViolationRatio() > caseDefinition.maxPrimaryErrorRatio()) {
			violations++;
		}
		if (metrics.maxThrustLossOverContractRatio() > caseDefinition.maxSecondaryErrorRatio()) {
			violations++;
		}
		if (metrics.heldResidualThrustDeficitRatio() > RESIDUAL_DEFICIT_THRESHOLD) {
			violations++;
		}
		if (metrics.heldStateVelocityDeltaMetersPerSecond() > STATE_DELTA_EPSILON
				|| metrics.heldAttitudeEulerDeltaRadians() > STATE_DELTA_EPSILON
				|| metrics.heldAngularVelocityDeltaRadiansPerSecond() > STATE_DELTA_EPSILON) {
			violations++;
		}
		return violations;
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

	private static DroneConfig presetConfig(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			default -> throw new IllegalArgumentException(
					"unsupported state-normalized blackbox result preset: " + presetName);
		};
	}

	private static double caseDefinitionDtSeconds() {
		return 0.005;
	}

	private static void holdKinematics(DronePhysics physics, Vec3 forwardVelocity) {
		physics.state().setPositionMeters(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(forwardVelocity);
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
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

	private static final class NormalizedBlackboxAccumulator {
		private int maxMarginSampleIndex;
		private double maxThrustLossOverContractRatio = -1.0;
		private double maxTipMachMarginViolationRatio;
		private double heldObservedThrustRatio;
		private double heldCompressibilityProxyRatio;
		private double heldResidualThrustDeficitRatio;
		private double heldNeutralThrustNewtons;
		private double heldDeratedThrustNewtons;
		private double heldNeutralAveragePropellerThrustScale;
		private double heldDeratedAveragePropellerThrustScale;
		private double heldDeratedPropellerThrustScaleRange;
		private double heldDeratedAdvanceRatioRange;
		private double heldStateVelocityDeltaMetersPerSecond;
		private double heldAttitudeEulerDeltaRadians;
		private double heldAngularVelocityDeltaRadiansPerSecond;

		void record(
				int sampleIndex,
				DroneConfig neutralConfig,
				DronePhysics neutral,
				DronePhysics derated,
				double equivalentMaxThrustLossPercent
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
				double neutralOmega = neutral.state().motorOmegaRadiansPerSecond(rotorIndex);
				double deratedOmega = derated.state().motorOmegaRadiansPerSecond(rotorIndex);
				double neutralOmegaSquared = rotor.thrustCoefficient() * neutralOmega * neutralOmega;
				double deratedOmegaSquared = rotor.thrustCoefficient() * deratedOmega * deratedOmega;
				double neutralPropeller = neutral.state().rotorPropellerThrustScale(rotorIndex);
				double deratedRotorPropeller = derated.state().rotorPropellerThrustScale(rotorIndex);
				double neutralInflow = neutral.state().rotorInducedLagThrustScale(rotorIndex);
				double deratedInflow = derated.state().rotorInducedLagThrustScale(rotorIndex);
				double neutralCompressibility = neutral.state().rotorCompressibilityThrustScale(rotorIndex);
				double deratedCompressibility = derated.state().rotorCompressibilityThrustScale(rotorIndex);
				neutralCompressibilityProxy += neutralOmegaSquared * neutralPropeller * neutralInflow
						* neutralCompressibility;
				deratedCompressibilityProxy += deratedOmegaSquared * deratedRotorPropeller * deratedInflow
						* deratedCompressibility;
				neutralThrust += Math.max(0.0, neutral.state().rotorThrustNewtons(rotorIndex));
				deratedThrust += Math.max(0.0, derated.state().rotorThrustNewtons(rotorIndex));
				neutralPropellerScale += neutralPropeller;
				deratedPropellerScale += deratedRotorPropeller;
				maxTipMachMarginViolationRatio = Math.max(
						maxTipMachMarginViolationRatio,
						Math.max(0.0, derated.state().rotorTipMach(rotorIndex) - 0.70) / 0.70);
			}
			double lossPercent = neutralThrust > EPSILON
					? Math.max(0.0, (neutralThrust - deratedThrust) / neutralThrust) * 100.0
					: 0.0;
			double overContract = Math.max(0.0, lossPercent - equivalentMaxThrustLossPercent) / 100.0;
			if (overContract >= maxThrustLossOverContractRatio) {
				int rotorCount = Math.max(1, neutralConfig.rotors().size());
				maxMarginSampleIndex = sampleIndex;
				maxThrustLossOverContractRatio = overContract;
				heldObservedThrustRatio = deratedThrust / Math.max(EPSILON, neutralThrust);
				heldCompressibilityProxyRatio = deratedCompressibilityProxy / Math.max(
						EPSILON,
						neutralCompressibilityProxy);
				heldResidualThrustDeficitRatio = Math.max(0.0,
						heldCompressibilityProxyRatio - heldObservedThrustRatio);
				heldNeutralThrustNewtons = neutralThrust;
				heldDeratedThrustNewtons = deratedThrust;
				heldNeutralAveragePropellerThrustScale = neutralPropellerScale / rotorCount;
				heldDeratedAveragePropellerThrustScale = deratedPropellerScale / rotorCount;
				heldDeratedPropellerThrustScaleRange = range(deratedPropeller);
				heldDeratedAdvanceRatioRange = range(deratedAdvance);
			}
		}

		void recordPostClampState(DronePhysics neutral, DronePhysics derated) {
			Vec3 neutralEuler = neutral.state().orientation().toEulerXYZRadians();
			Vec3 deratedEuler = derated.state().orientation().toEulerXYZRadians();
			heldStateVelocityDeltaMetersPerSecond = Math.max(
					heldStateVelocityDeltaMetersPerSecond,
					neutral.state().velocityMetersPerSecond()
							.subtract(derated.state().velocityMetersPerSecond())
							.length());
			heldAttitudeEulerDeltaRadians = Math.max(
					heldAttitudeEulerDeltaRadians,
					neutralEuler.subtract(deratedEuler).length());
			heldAngularVelocityDeltaRadiansPerSecond = Math.max(
					heldAngularVelocityDeltaRadiansPerSecond,
					neutral.state().angularVelocityBodyRadiansPerSecond()
							.subtract(derated.state().angularVelocityBodyRadiansPerSecond())
							.length());
		}

		NormalizedBlackboxMetrics toMetrics(int sampleCount) {
			return new NormalizedBlackboxMetrics(
					sampleCount,
					maxMarginSampleIndex,
					maxTipMachMarginViolationRatio,
					maxThrustLossOverContractRatio,
					heldObservedThrustRatio,
					heldCompressibilityProxyRatio,
					heldResidualThrustDeficitRatio,
					heldNeutralThrustNewtons,
					heldDeratedThrustNewtons,
					heldNeutralAveragePropellerThrustScale,
					heldDeratedAveragePropellerThrustScale,
					heldDeratedPropellerThrustScaleRange,
					heldDeratedAdvanceRatioRange,
					heldStateVelocityDeltaMetersPerSecond,
					heldAttitudeEulerDeltaRadians,
					heldAngularVelocityDeltaRadiansPerSecond
			);
		}
	}

	private record NormalizedBlackboxMetrics(
			int sampleCount,
			int maxMarginSampleIndex,
			double maxTipMachMarginViolationRatio,
			double maxThrustLossOverContractRatio,
			double heldObservedThrustRatio,
			double heldCompressibilityProxyRatio,
			double heldResidualThrustDeficitRatio,
			double heldNeutralThrustNewtons,
			double heldDeratedThrustNewtons,
			double heldNeutralAveragePropellerThrustScale,
			double heldDeratedAveragePropellerThrustScale,
			double heldDeratedPropellerThrustScaleRange,
			double heldDeratedAdvanceRatioRange,
			double heldStateVelocityDeltaMetersPerSecond,
			double heldAttitudeEulerDeltaRadians,
			double heldAngularVelocityDeltaRadiansPerSecond
	) {
	}
}
