package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Forward-Punchout-Margin-Diagnostic-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook forward-punchout margin diagnostic scans full-throttle cold-air speed sensitivity after the APDrone blackbox result failure; rows remain lab evidence only and do not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int PRESET_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT;
	public static final int SPEED_SAMPLE_COUNT = 5;
	public static final int DIAGNOSTIC_ROW_COUNT = PRESET_COUNT * SPEED_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ DIAGNOSTIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double REVIEW_COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	private static final double REVIEW_DT_SECONDS = 0.005;
	private static final int REVIEW_SAMPLE_COUNT = 360;
	private static final double REVIEW_THROTTLE_COMMAND = 1.0;
	private static final double TIP_MACH_LIMIT = 0.70;
	private static final double MAX_THRUST_LOSS_OVER_CONTRACT_RATIO =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.caseDefinition("cold_air_forward_punchout_margin")
					.maxSecondaryErrorRatio();
	private static final List<Double> FORWARD_SPEED_SAMPLES_METERS_PER_SECOND =
			List.of(0.0, 8.0, 16.0, 22.0, 28.0);
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic() {
	}

	public record ForwardPunchoutMarginDiagnosticRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double forwardSpeedMetersPerSecond,
			double throttleCommand,
			int sampleCount,
			double dtSeconds,
			double targetMaxRpmScale,
			double contractedMaxRpm,
			double equivalentMaxThrustLossPercent,
			double observedThrustLossPercent,
			double thrustLossOverContractRatio,
			double maxAllowedThrustLossOverContractRatio,
			double maxTargetScaleErrorRatio,
			double maxTargetOmegaOvershootRatio,
			double maxMotorOmegaAboveNeutralRatio,
			double maxRotorAdvanceRatio,
			double maxRotorTipMach,
			double neutralThrustNewtonsAtMaxMargin,
			double deratedThrustNewtonsAtMaxMargin,
			boolean thrustMarginPassed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record ForwardPunchoutMarginDiagnosticSummary(
			int rowCount,
			int passedRowCount,
			int failedRowCount,
			int presetCount,
			int speedSampleCount,
			int apDroneFailedRowCount,
			int racingQuadFailedRowCount,
			double maxObservedThrustLossPercent,
			double maxThrustLossOverContractRatio,
			double minFailingThrustLossOverContractRatio,
			double blackboxSpeedApDroneThrustLossOverContractRatio,
			double blackboxSpeedRacingQuadThrustLossOverContractRatio,
			double maxTargetScaleErrorRatio,
			double maxTargetOmegaOvershootRatio,
			double maxMotorOmegaAboveNeutralRatio,
			double maxRotorAdvanceRatio,
			double maxRotorTipMach,
			String dominantFailurePreset,
			String nextRequiredAction,
			boolean marginDiagnosticPassed
	) {
	}

	public record ForwardPunchoutMarginDiagnosticAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int presetCount,
			int speedSampleCount,
			int diagnosticRowCount,
			int summaryRowCount,
			int methodRowCount,
			String controlBoundary,
			List<ForwardPunchoutMarginDiagnosticRow> rows,
			ForwardPunchoutMarginDiagnosticSummary summary
	) {
		public ForwardPunchoutMarginDiagnosticAudit {
			rows = List.copyOf(rows);
		}
	}

	public static ForwardPunchoutMarginDiagnosticAudit audit() {
		List<ForwardPunchoutMarginDiagnosticRow> rows = new ArrayList<>();
		for (String presetName : List.of("racingQuad", "apDrone")) {
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract =
							PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
									"synthetic_derate_validation_all_pass",
									presetName,
									"cold_sea_level_minus10c");
			DroneConfig baseConfig = presetConfig(presetName);
			for (double forwardSpeedMetersPerSecond : FORWARD_SPEED_SAMPLES_METERS_PER_SECOND) {
				rows.add(row(contract, baseConfig, forwardSpeedMetersPerSecond));
			}
		}
		return new ForwardPunchoutMarginDiagnosticAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PRESET_COUNT,
				SPEED_SAMPLE_COUNT,
				DIAGNOSTIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CONTROL_BOUNDARY,
				rows,
				summary(rows)
		);
	}

	public static ForwardPunchoutMarginDiagnosticRow row(
			String presetName,
			double forwardSpeedMetersPerSecond
	) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName)
						&& Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate forward punchout diagnostic row: "
								+ presetName + "/" + forwardSpeedMetersPerSecond));
	}

	private static ForwardPunchoutMarginDiagnosticRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			DroneConfig baseConfig,
			double forwardSpeedMetersPerSecond
	) {
		DiagnosticMetrics metrics = simulate(baseConfig, contract.targetMaxRpmScale(),
				contract.equivalentMaxThrustLossPercent(), forwardSpeedMetersPerSecond);
		boolean passed = metrics.thrustLossOverContractRatio() <= MAX_THRUST_LOSS_OVER_CONTRACT_RATIO
				&& metrics.maxRotorTipMach() <= TIP_MACH_LIMIT;
		return new ForwardPunchoutMarginDiagnosticRow(
				contract.scenarioName(),
				contract.presetName(),
				contract.ambientCaseName(),
				forwardSpeedMetersPerSecond,
				REVIEW_THROTTLE_COMMAND,
				REVIEW_SAMPLE_COUNT,
				REVIEW_DT_SECONDS,
				contract.targetMaxRpmScale(),
				contract.contractedMaxRpm(),
				contract.equivalentMaxThrustLossPercent(),
				metrics.observedThrustLossPercent(),
				metrics.thrustLossOverContractRatio(),
				MAX_THRUST_LOSS_OVER_CONTRACT_RATIO,
				metrics.maxTargetScaleErrorRatio(),
				metrics.maxTargetOmegaOvershootRatio(),
				metrics.maxMotorOmegaAboveNeutralRatio(),
				metrics.maxRotorAdvanceRatio(),
				metrics.maxRotorTipMach(),
				metrics.neutralThrustNewtonsAtMaxMargin(),
				metrics.deratedThrustNewtonsAtMaxMargin(),
				passed,
				false,
				false,
				false,
				passed ? "PASS" : "FAIL",
				passed
						? "forward-punchout-derate-margin-within-contract"
						: "forward-punchout-derate-margin-exceeds-contract"
		);
	}

	private static ForwardPunchoutMarginDiagnosticSummary summary(
			List<ForwardPunchoutMarginDiagnosticRow> rows
	) {
		int passed = 0;
		int apDroneFailed = 0;
		int racingFailed = 0;
		double maxObservedLoss = 0.0;
		double maxOver = 0.0;
		double minFailingOver = Double.POSITIVE_INFINITY;
		double apDroneBlackboxOver = 0.0;
		double racingBlackboxOver = 0.0;
		double maxScaleError = 0.0;
		double maxOvershoot = 0.0;
		double maxMotorAbove = 0.0;
		double maxAdvance = 0.0;
		double maxTipMach = 0.0;
		for (ForwardPunchoutMarginDiagnosticRow row : rows) {
			if (row.thrustMarginPassed()) {
				passed++;
			} else {
				if ("apDrone".equals(row.presetName())) {
					apDroneFailed++;
				}
				if ("racingQuad".equals(row.presetName())) {
					racingFailed++;
				}
				minFailingOver = Math.min(minFailingOver, row.thrustLossOverContractRatio());
			}
			if ("apDrone".equals(row.presetName())
					&& Double.compare(row.forwardSpeedMetersPerSecond(), 22.0) == 0) {
				apDroneBlackboxOver = row.thrustLossOverContractRatio();
			}
			if ("racingQuad".equals(row.presetName())
					&& Double.compare(row.forwardSpeedMetersPerSecond(), 22.0) == 0) {
				racingBlackboxOver = row.thrustLossOverContractRatio();
			}
			maxObservedLoss = Math.max(maxObservedLoss, row.observedThrustLossPercent());
			maxOver = Math.max(maxOver, row.thrustLossOverContractRatio());
			maxScaleError = Math.max(maxScaleError, row.maxTargetScaleErrorRatio());
			maxOvershoot = Math.max(maxOvershoot, row.maxTargetOmegaOvershootRatio());
			maxMotorAbove = Math.max(maxMotorAbove, row.maxMotorOmegaAboveNeutralRatio());
			maxAdvance = Math.max(maxAdvance, row.maxRotorAdvanceRatio());
			maxTipMach = Math.max(maxTipMach, row.maxRotorTipMach());
		}
		int failed = rows.size() - passed;
		boolean allPassed = failed == 0;
		return new ForwardPunchoutMarginDiagnosticSummary(
				rows.size(),
				passed,
				failed,
				(int) rows.stream().map(ForwardPunchoutMarginDiagnosticRow::presetName).distinct().count(),
				FORWARD_SPEED_SAMPLES_METERS_PER_SECOND.size(),
				apDroneFailed,
				racingFailed,
				maxObservedLoss,
				maxOver,
				Double.isInfinite(minFailingOver) ? 0.0 : minFailingOver,
				apDroneBlackboxOver,
				racingBlackboxOver,
				maxScaleError,
				maxOvershoot,
				maxMotorAbove,
				maxAdvance,
				maxTipMach,
				apDroneFailed >= racingFailed ? "apDrone" : "racingQuad",
				allPassed
						? "feed-forward-punchout-margin-diagnostic-to-blackbox-acceptance"
						: "investigate-apDrone-full-throttle-derate-transient-thrust-loss-before-runtime-coupling",
				allPassed
		);
	}

	private static DiagnosticMetrics simulate(
			DroneConfig baseConfig,
			double targetMaxRpmScale,
			double equivalentMaxThrustLossPercent,
			double forwardSpeedMetersPerSecond
	) {
		DroneConfig neutralConfig = reviewConfig(baseConfig, 1.0);
		DroneConfig deratedConfig = reviewConfig(baseConfig, targetMaxRpmScale);
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, forwardSpeedMetersPerSecond);
		neutral.state().setVelocityMetersPerSecond(forwardVelocity);
		derated.state().setVelocityMetersPerSecond(forwardVelocity);
		DiagnosticAccumulator metrics = new DiagnosticAccumulator();
		DroneEnvironment environment = coldAirEnvironment();
		DroneInput input = new DroneInput(REVIEW_THROTTLE_COMMAND, 0.0, 0.0, 0.0, true, true);
		for (int sample = 0; sample < REVIEW_SAMPLE_COUNT; sample++) {
			neutral.step(input, REVIEW_DT_SECONDS, environment);
			derated.step(input, REVIEW_DT_SECONDS, environment);
			metrics.record(neutralConfig, neutral, derated, targetMaxRpmScale, equivalentMaxThrustLossPercent);
		}
		return metrics.toMetrics();
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
					"unsupported RotorSpec retune ambient derate forward punchout diagnostic preset: " + presetName);
		};
	}

	private static final class DiagnosticAccumulator {
		private double maxTargetScaleErrorRatio;
		private double maxTargetOmegaOvershootRatio;
		private double maxMotorOmegaAboveNeutralRatio;
		private double maxRotorAdvanceRatio;
		private double maxRotorTipMach;
		private double observedThrustLossPercent;
		private double thrustLossOverContractRatio;
		private double neutralThrustNewtonsAtMaxMargin;
		private double deratedThrustNewtonsAtMaxMargin;

		void record(
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
				maxMotorOmegaAboveNeutralRatio = Math.max(
						maxMotorOmegaAboveNeutralRatio,
						Math.max(0.0, deratedOmega - neutralOmega)
								/ Math.max(1.0, neutralOmega));
				maxRotorAdvanceRatio = Math.max(maxRotorAdvanceRatio, derated.state().rotorAdvanceRatio(rotorIndex));
				maxRotorTipMach = Math.max(maxRotorTipMach, derated.state().rotorTipMach(rotorIndex));
				neutralThrust += Math.max(0.0, neutral.state().rotorThrustNewtons(rotorIndex));
				deratedThrust += Math.max(0.0, derated.state().rotorThrustNewtons(rotorIndex));
			}
			if (neutralThrust > EPSILON) {
				double lossPercent = Math.max(0.0, (neutralThrust - deratedThrust) / neutralThrust) * 100.0;
				double overContractRatio = Math.max(
						0.0,
						lossPercent - equivalentMaxThrustLossPercent
				) / 100.0;
				if (overContractRatio >= thrustLossOverContractRatio) {
					observedThrustLossPercent = lossPercent;
					thrustLossOverContractRatio = overContractRatio;
					neutralThrustNewtonsAtMaxMargin = neutralThrust;
					deratedThrustNewtonsAtMaxMargin = deratedThrust;
				}
			}
		}

		DiagnosticMetrics toMetrics() {
			return new DiagnosticMetrics(
					observedThrustLossPercent,
					thrustLossOverContractRatio,
					maxTargetScaleErrorRatio,
					maxTargetOmegaOvershootRatio,
					maxMotorOmegaAboveNeutralRatio,
					maxRotorAdvanceRatio,
					maxRotorTipMach,
					neutralThrustNewtonsAtMaxMargin,
					deratedThrustNewtonsAtMaxMargin
			);
		}
	}

	private record DiagnosticMetrics(
			double observedThrustLossPercent,
			double thrustLossOverContractRatio,
			double maxTargetScaleErrorRatio,
			double maxTargetOmegaOvershootRatio,
			double maxMotorOmegaAboveNeutralRatio,
			double maxRotorAdvanceRatio,
			double maxRotorTipMach,
			double neutralThrustNewtonsAtMaxMargin,
			double deratedThrustNewtonsAtMaxMargin
	) {
	}
}
