package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Blackbox-Result-Review-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook blackbox result review runs explicit DronePhysics target-omega derate regressions for the accepted racingQuad and APDrone contract rows; passing results remain lab evidence only and do not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int CONTRACT_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT;
	public static final int REGRESSION_CASE_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.REGRESSION_CASE_COUNT;
	public static final int RESULT_ROW_COUNT = CONTRACT_ROW_COUNT * REGRESSION_CASE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ RESULT_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double REVIEW_COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	private static final double TIP_MACH_LIMIT = 0.70;
	private static final double NO_LOAD_OVERSPEED_MAX_OMEGA_SCALE = 1.08;
	private static final double FAILSAFE_TIMEOUT_SECONDS = 0.020;
	private static final int FAILSAFE_SPOOL_SAMPLE_COUNT = 120;
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);
	private static final List<String> CASE_NAMES = List.of(
			"hover_hold_target_omega_scale",
			"full_throttle_ramp_derate_clamp",
			"cold_air_forward_punchout_margin",
			"failsafe_disarm_no_overspeed"
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview() {
	}

	public record DerateControlHookBlackboxResultReviewRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String regressionCaseName,
			String flightPhase,
			String targetMetric,
			int sampleCount,
			int minSampleCount,
			double dtSeconds,
			double targetMaxRpmScale,
			double contractedMaxRpm,
			double primaryErrorRatio,
			double secondaryErrorRatio,
			double maxAllowedPrimaryErrorRatio,
			double maxAllowedSecondaryErrorRatio,
			int physicalConstraintViolationCount,
			double maxTargetScaleErrorRatio,
			double maxTargetOmegaOvershootRatio,
			double maxEscElectricalOutputDelta,
			double maxMotorOmegaAboveNeutralRatio,
			double maxTipMachMarginViolationRatio,
			double maxThrustLossOverContractRatio,
			double maxTargetOmegaAfterReleaseRatio,
			double maxMotorOmegaOverspeedRatio,
			boolean failsafeActivated,
			boolean processedDisarmed,
			boolean blackboxRegressionPassed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DerateControlHookBlackboxResultReviewSummary(
			int rowCount,
			int passedRowCount,
			int presetCount,
			int regressionCaseCount,
			int failsafeActivatedRowCount,
			int processedDisarmedRowCount,
			double maxPrimaryErrorRatio,
			double maxSecondaryErrorRatio,
			double maxTargetScaleErrorRatio,
			double maxTargetOmegaOvershootRatio,
			double maxEscElectricalOutputDelta,
			double maxMotorOmegaAboveNeutralRatio,
			double maxTipMachMarginViolationRatio,
			double maxThrustLossOverContractRatio,
			double maxTargetOmegaAfterReleaseRatio,
			double maxMotorOmegaOverspeedRatio,
			int physicalConstraintViolationCount,
			boolean blackboxRegressionPassed
	) {
	}

	public record DerateControlHookBlackboxResultReviewAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int contractRowCount,
			int regressionCaseCount,
			int resultRowCount,
			int summaryRowCount,
			int methodRowCount,
			String controlBoundary,
			List<DerateControlHookBlackboxResultReviewRow> rows,
			DerateControlHookBlackboxResultReviewSummary summary
	) {
		public DerateControlHookBlackboxResultReviewAudit {
			rows = List.copyOf(rows);
		}
	}

	public static DerateControlHookBlackboxResultReviewAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow> contractRows = contract.rows().stream()
				.filter(row -> "synthetic_derate_validation_all_pass".equals(row.scenarioName()))
				.toList();
		List<DerateControlHookBlackboxResultReviewRow> rows = new ArrayList<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contractRow : contractRows) {
			for (String caseName : CASE_NAMES) {
				rows.add(row(contractRow,
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.caseDefinition(caseName)));
			}
		}
		return new DerateControlHookBlackboxResultReviewAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				CONTRACT_ROW_COUNT,
				REGRESSION_CASE_COUNT,
				RESULT_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CONTROL_BOUNDARY,
				rows,
				summary(rows)
		);
	}

	public static DerateControlHookBlackboxResultReviewRow row(
			String presetName,
			String regressionCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.regressionCaseName().equals(regressionCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate blackbox result row: "
								+ presetName + "/" + regressionCaseName));
	}

	private static DerateControlHookBlackboxResultReviewRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contractRow,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.BlackboxRegressionCaseDefinition caseDefinition
	) {
		DroneConfig baseConfig = presetConfig(contractRow.presetName());
		BlackboxMetrics metrics = simulate(baseConfig, contractRow.targetMaxRpmScale(),
				contractRow.equivalentMaxThrustLossPercent(), caseDefinition);
		double primary = primaryError(caseDefinition.targetMetric(), metrics);
		double secondary = secondaryError(caseDefinition.targetMetric(), metrics);
		int violations = physicalConstraintViolations(caseDefinition, metrics, primary, secondary);
		boolean passed = metrics.sampleCount() >= caseDefinition.minSampleCount()
				&& primary <= caseDefinition.maxPrimaryErrorRatio()
				&& secondary <= caseDefinition.maxSecondaryErrorRatio()
				&& violations == 0;
		return new DerateControlHookBlackboxResultReviewRow(
				contractRow.scenarioName(),
				contractRow.presetName(),
				contractRow.ambientCaseName(),
				caseDefinition.regressionCaseName(),
				caseDefinition.flightPhase(),
				caseDefinition.targetMetric(),
				metrics.sampleCount(),
				caseDefinition.minSampleCount(),
				dtSeconds(caseDefinition.regressionCaseName()),
				contractRow.targetMaxRpmScale(),
				contractRow.contractedMaxRpm(),
				primary,
				secondary,
				caseDefinition.maxPrimaryErrorRatio(),
				caseDefinition.maxSecondaryErrorRatio(),
				violations,
				metrics.maxTargetScaleErrorRatio(),
				metrics.maxTargetOmegaOvershootRatio(),
				metrics.maxEscElectricalOutputDelta(),
				metrics.maxMotorOmegaAboveNeutralRatio(),
				metrics.maxTipMachMarginViolationRatio(),
				metrics.maxThrustLossOverContractRatio(),
				metrics.maxTargetOmegaAfterReleaseRatio(),
				metrics.maxMotorOmegaOverspeedRatio(),
				metrics.failsafeActivated(),
				metrics.processedDisarmed(),
				passed,
				false,
				false,
				false,
				passed ? "PASS" : "FAIL",
				passed
						? "target-omega-blackbox-regression-result-passed"
						: "target-omega-blackbox-regression-result-failed"
		);
	}

	private static DerateControlHookBlackboxResultReviewSummary summary(
			List<DerateControlHookBlackboxResultReviewRow> rows
	) {
		int passed = 0;
		int failsafe = 0;
		int disarmed = 0;
		int violations = 0;
		double primary = 0.0;
		double secondary = 0.0;
		double targetScale = 0.0;
		double targetOvershoot = 0.0;
		double escDelta = 0.0;
		double motorAboveNeutral = 0.0;
		double tipMach = 0.0;
		double thrustLoss = 0.0;
		double releaseTarget = 0.0;
		double releaseOverspeed = 0.0;
		for (DerateControlHookBlackboxResultReviewRow row : rows) {
			if (row.blackboxRegressionPassed()) {
				passed++;
			}
			if (row.failsafeActivated()) {
				failsafe++;
			}
			if (row.processedDisarmed()) {
				disarmed++;
			}
			violations += row.physicalConstraintViolationCount();
			primary = Math.max(primary, row.primaryErrorRatio());
			secondary = Math.max(secondary, row.secondaryErrorRatio());
			targetScale = Math.max(targetScale, row.maxTargetScaleErrorRatio());
			targetOvershoot = Math.max(targetOvershoot, row.maxTargetOmegaOvershootRatio());
			escDelta = Math.max(escDelta, row.maxEscElectricalOutputDelta());
			motorAboveNeutral = Math.max(motorAboveNeutral, row.maxMotorOmegaAboveNeutralRatio());
			tipMach = Math.max(tipMach, row.maxTipMachMarginViolationRatio());
			thrustLoss = Math.max(thrustLoss, row.maxThrustLossOverContractRatio());
			releaseTarget = Math.max(releaseTarget, row.maxTargetOmegaAfterReleaseRatio());
			releaseOverspeed = Math.max(releaseOverspeed, row.maxMotorOmegaOverspeedRatio());
		}
		return new DerateControlHookBlackboxResultReviewSummary(
				rows.size(),
				passed,
				(int) rows.stream().map(DerateControlHookBlackboxResultReviewRow::presetName).distinct().count(),
				REGRESSION_CASE_COUNT,
				failsafe,
				disarmed,
				primary,
				secondary,
				targetScale,
				targetOvershoot,
				escDelta,
				motorAboveNeutral,
				tipMach,
				thrustLoss,
				releaseTarget,
				releaseOverspeed,
				violations,
				!rows.isEmpty() && passed == rows.size()
		);
	}

	private static BlackboxMetrics simulate(
			DroneConfig baseConfig,
			double targetMaxRpmScale,
			double equivalentMaxThrustLossPercent,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.BlackboxRegressionCaseDefinition caseDefinition
	) {
		return switch (caseDefinition.regressionCaseName()) {
			case "hover_hold_target_omega_scale", "full_throttle_ramp_derate_clamp",
					"cold_air_forward_punchout_margin" ->
					simulatePoweredCase(baseConfig, targetMaxRpmScale, equivalentMaxThrustLossPercent, caseDefinition);
			case "failsafe_disarm_no_overspeed" ->
					simulateFailsafeCase(baseConfig, targetMaxRpmScale, caseDefinition);
			default -> throw new IllegalArgumentException(
					"unknown RotorSpec retune ambient derate blackbox result case: "
							+ caseDefinition.regressionCaseName());
		};
	}

	private static BlackboxMetrics simulatePoweredCase(
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
		if ("cold_air_forward_punchout_margin".equals(caseDefinition.regressionCaseName())) {
			Vec3 forwardVelocity = new Vec3(0.0, 0.0, 22.0);
			neutral.state().setVelocityMetersPerSecond(forwardVelocity);
			derated.state().setVelocityMetersPerSecond(forwardVelocity);
		}
		MetricAccumulator metrics = new MetricAccumulator();
		for (int sample = 0; sample < caseDefinition.minSampleCount(); sample++) {
			DroneInput input = poweredInput(neutralConfig, caseDefinition.regressionCaseName(), sample,
					caseDefinition.minSampleCount());
			DroneEnvironment environment = environmentFor(caseDefinition.regressionCaseName());
			neutral.step(input, dtSeconds(caseDefinition.regressionCaseName()), environment);
			derated.step(input, dtSeconds(caseDefinition.regressionCaseName()), environment);
			metrics.recordPoweredSample(neutralConfig, neutral, derated, targetMaxRpmScale,
					equivalentMaxThrustLossPercent);
		}
		return metrics.toMetrics(caseDefinition.minSampleCount());
	}

	private static BlackboxMetrics simulateFailsafeCase(
			DroneConfig baseConfig,
			double targetMaxRpmScale,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.BlackboxRegressionCaseDefinition caseDefinition
	) {
		DroneConfig neutralConfig = reviewConfig(baseConfig, 1.0);
		DroneConfig deratedConfig = reviewConfig(baseConfig, targetMaxRpmScale);
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		DroneInput spoolInput = new DroneInput(1.0, 0.0, 0.0, 0.0, true, true);
		for (int sample = 0; sample < FAILSAFE_SPOOL_SAMPLE_COUNT; sample++) {
			neutral.step(spoolInput, dtSeconds(caseDefinition.regressionCaseName()), environmentFor(
					caseDefinition.regressionCaseName()));
			derated.step(spoolInput, dtSeconds(caseDefinition.regressionCaseName()), environmentFor(
					caseDefinition.regressionCaseName()));
		}
		MetricAccumulator metrics = new MetricAccumulator();
		double[] releaseStartOmega = derated.state().motorOmegaRadiansPerSecond();
		DroneInput releaseInput = new DroneInput(1.0, 0.0, 0.0, 0.0, true, false);
		for (int sample = 0; sample < caseDefinition.minSampleCount(); sample++) {
			neutral.step(releaseInput, dtSeconds(caseDefinition.regressionCaseName()), environmentFor(
					caseDefinition.regressionCaseName()));
			derated.step(releaseInput, dtSeconds(caseDefinition.regressionCaseName()), environmentFor(
					caseDefinition.regressionCaseName()));
			if (!derated.state().controlFailsafeActive()) {
				continue;
			}
			metrics.recordFailsafeSample(neutralConfig, neutral, derated, releaseStartOmega);
		}
		return metrics.toMetrics(FAILSAFE_SPOOL_SAMPLE_COUNT + caseDefinition.minSampleCount());
	}

	private static DroneInput poweredInput(
			DroneConfig config,
			String regressionCaseName,
			int sample,
			int sampleCount
	) {
		double throttle = switch (regressionCaseName) {
			case "hover_hold_target_omega_scale" -> config.hoverThrottle();
			case "full_throttle_ramp_derate_clamp" ->
					0.20 + 0.80 * sample / Math.max(1.0, sampleCount - 1.0);
			case "cold_air_forward_punchout_margin" -> 1.0;
			default -> throw new IllegalArgumentException(
					"unknown RotorSpec retune ambient derate powered blackbox case: " + regressionCaseName);
		};
		return new DroneInput(throttle, 0.0, 0.0, 0.0, true, true);
	}

	private static double primaryError(String targetMetric, BlackboxMetrics metrics) {
		return switch (targetMetric) {
			case "target_omega_scale_closure" -> metrics.maxTargetScaleErrorRatio();
			case "target_omega_clamp_and_slew" -> metrics.maxTargetOmegaOvershootRatio();
			case "tip_mach_and_thrust_loss_margin" -> metrics.maxTipMachMarginViolationRatio();
			case "no_load_overspeed_and_clamp_release" -> metrics.maxTargetOmegaAfterReleaseRatio();
			default -> throw new IllegalArgumentException(
					"unknown RotorSpec retune ambient derate blackbox target metric: " + targetMetric);
		};
	}

	private static double secondaryError(String targetMetric, BlackboxMetrics metrics) {
		return switch (targetMetric) {
			case "target_omega_scale_closure" -> metrics.maxMotorOmegaAboveNeutralRatio();
			case "target_omega_clamp_and_slew" -> metrics.maxEscElectricalOutputDelta();
			case "tip_mach_and_thrust_loss_margin" -> metrics.maxThrustLossOverContractRatio();
			case "no_load_overspeed_and_clamp_release" -> metrics.maxMotorOmegaOverspeedRatio();
			default -> throw new IllegalArgumentException(
					"unknown RotorSpec retune ambient derate blackbox target metric: " + targetMetric);
		};
	}

	private static int physicalConstraintViolations(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.BlackboxRegressionCaseDefinition caseDefinition,
			BlackboxMetrics metrics,
			double primaryErrorRatio,
			double secondaryErrorRatio
	) {
		int violations = 0;
		if (primaryErrorRatio > caseDefinition.maxPrimaryErrorRatio()) {
			violations++;
		}
		if (secondaryErrorRatio > caseDefinition.maxSecondaryErrorRatio()) {
			violations++;
		}
		if ("no_load_overspeed_and_clamp_release".equals(caseDefinition.targetMetric())
				&& (!metrics.failsafeActivated() || !metrics.processedDisarmed())) {
			violations++;
		}
		return violations;
	}

	private static DroneConfig reviewConfig(DroneConfig config, double targetMaxRpmScale) {
		return config
				.withRotorTargetMaxRpmScale(targetMaxRpmScale)
				.withControlLink(0.0, 0.0, FAILSAFE_TIMEOUT_SECONDS)
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

	private static DroneEnvironment environmentFor(String regressionCaseName) {
		if ("cold_air_forward_punchout_margin".equals(regressionCaseName)) {
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
		return DroneEnvironment.calm();
	}

	private static double dtSeconds(String regressionCaseName) {
		return switch (regressionCaseName) {
			case "hover_hold_target_omega_scale", "cold_air_forward_punchout_margin" -> 0.005;
			case "full_throttle_ramp_derate_clamp" -> 0.006;
			case "failsafe_disarm_no_overspeed" -> 0.010;
			default -> throw new IllegalArgumentException(
					"unknown RotorSpec retune ambient derate blackbox dt case: " + regressionCaseName);
		};
	}

	private static DroneConfig presetConfig(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			default -> throw new IllegalArgumentException(
					"unsupported RotorSpec retune ambient derate blackbox result preset: " + presetName);
		};
	}

	private static final class MetricAccumulator {
		private double maxTargetScaleErrorRatio;
		private double maxTargetOmegaOvershootRatio;
		private double maxEscElectricalOutputDelta;
		private double maxMotorOmegaAboveNeutralRatio;
		private double maxTipMachMarginViolationRatio;
		private double maxThrustLossOverContractRatio;
		private double maxTargetOmegaAfterReleaseRatio;
		private double maxMotorOmegaOverspeedRatio;
		private boolean failsafeActivated;
		private boolean processedDisarmed;

		void recordPoweredSample(
				DroneConfig neutralConfig,
				DronePhysics neutral,
				DronePhysics derated,
				double targetMaxRpmScale,
				double equivalentMaxThrustLossPercent
		) {
			double neutralThrust = 0.0;
			double deratedThrust = 0.0;
			for (int rotorIndex = 0; rotorIndex < neutralConfig.rotors().size(); rotorIndex++) {
				RotorSpec rotor = neutralConfig.rotors().get(rotorIndex);
				double maxOmega = Math.max(EPSILON, rotor.maxOmegaRadiansPerSecond());
				double neutralTarget = neutral.state().motorTargetOmegaRadiansPerSecond(rotorIndex);
				double deratedTarget = derated.state().motorTargetOmegaRadiansPerSecond(rotorIndex);
				double neutralOmega = neutral.state().motorOmegaRadiansPerSecond(rotorIndex);
				double deratedOmega = derated.state().motorOmegaRadiansPerSecond(rotorIndex);
				if (neutralTarget > EPSILON) {
					maxTargetScaleErrorRatio = Math.max(
							maxTargetScaleErrorRatio,
							Math.abs(deratedTarget / neutralTarget - targetMaxRpmScale));
				}
				maxTargetOmegaOvershootRatio = Math.max(
						maxTargetOmegaOvershootRatio,
						Math.max(0.0, deratedTarget - maxOmega * targetMaxRpmScale)
								/ Math.max(EPSILON, maxOmega * targetMaxRpmScale));
				maxEscElectricalOutputDelta = Math.max(
						maxEscElectricalOutputDelta,
						Math.abs(neutral.state().escElectricalOutputCommand(rotorIndex)
								- derated.state().escElectricalOutputCommand(rotorIndex)));
				maxMotorOmegaAboveNeutralRatio = Math.max(
						maxMotorOmegaAboveNeutralRatio,
						Math.max(0.0, deratedOmega - neutralOmega)
								/ Math.max(1.0, neutralOmega));
				maxTipMachMarginViolationRatio = Math.max(
						maxTipMachMarginViolationRatio,
						Math.max(0.0, derated.state().rotorTipMach(rotorIndex) - TIP_MACH_LIMIT)
								/ TIP_MACH_LIMIT);
				neutralThrust += Math.max(0.0, neutral.state().rotorThrustNewtons(rotorIndex));
				deratedThrust += Math.max(0.0, derated.state().rotorThrustNewtons(rotorIndex));
			}
			if (neutralThrust > EPSILON) {
				double lossPercent = Math.max(0.0, (neutralThrust - deratedThrust) / neutralThrust) * 100.0;
				maxThrustLossOverContractRatio = Math.max(
						maxThrustLossOverContractRatio,
						Math.max(0.0, lossPercent - equivalentMaxThrustLossPercent) / 100.0);
			}
		}

		void recordFailsafeSample(
				DroneConfig neutralConfig,
				DronePhysics neutral,
				DronePhysics derated,
				double[] releaseStartOmega
		) {
			failsafeActivated |= derated.state().controlFailsafeActive();
			processedDisarmed |= !derated.state().processedControlInput().armed();
			for (int rotorIndex = 0; rotorIndex < neutralConfig.rotors().size(); rotorIndex++) {
				RotorSpec rotor = neutralConfig.rotors().get(rotorIndex);
				double maxOmega = Math.max(EPSILON, rotor.maxOmegaRadiansPerSecond());
				double deratedOmega = derated.state().motorOmegaRadiansPerSecond(rotorIndex);
				maxTargetOmegaAfterReleaseRatio = Math.max(
						maxTargetOmegaAfterReleaseRatio,
						derated.state().motorTargetOmegaRadiansPerSecond(rotorIndex) / maxOmega);
				maxMotorOmegaOverspeedRatio = Math.max(
						maxMotorOmegaOverspeedRatio,
						Math.max(0.0, deratedOmega - maxOmega * NO_LOAD_OVERSPEED_MAX_OMEGA_SCALE)
								/ (maxOmega * NO_LOAD_OVERSPEED_MAX_OMEGA_SCALE));
				maxMotorOmegaOverspeedRatio = Math.max(
						maxMotorOmegaOverspeedRatio,
						Math.max(0.0, deratedOmega - releaseStartOmega[rotorIndex]) / maxOmega);
				maxMotorOmegaAboveNeutralRatio = Math.max(
						maxMotorOmegaAboveNeutralRatio,
						Math.max(0.0, deratedOmega
								- neutral.state().motorOmegaRadiansPerSecond(rotorIndex)) / maxOmega);
			}
		}

		BlackboxMetrics toMetrics(int sampleCount) {
			return new BlackboxMetrics(
					sampleCount,
					maxTargetScaleErrorRatio,
					maxTargetOmegaOvershootRatio,
					maxEscElectricalOutputDelta,
					maxMotorOmegaAboveNeutralRatio,
					maxTipMachMarginViolationRatio,
					maxThrustLossOverContractRatio,
					maxTargetOmegaAfterReleaseRatio,
					maxMotorOmegaOverspeedRatio,
					failsafeActivated,
					processedDisarmed
			);
		}
	}

	private record BlackboxMetrics(
			int sampleCount,
			double maxTargetScaleErrorRatio,
			double maxTargetOmegaOvershootRatio,
			double maxEscElectricalOutputDelta,
			double maxMotorOmegaAboveNeutralRatio,
			double maxTipMachMarginViolationRatio,
			double maxThrustLossOverContractRatio,
			double maxTargetOmegaAfterReleaseRatio,
			double maxMotorOmegaOverspeedRatio,
			boolean failsafeActivated,
			boolean processedDisarmed
	) {
	}
}
