package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Forward-Punchout-Peak-Timing-Diagnostic-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook forward-punchout peak timing diagnostic decomposes APDrone full-throttle cold-air thrust-loss failures by peak sample and time window; rows remain lab evidence only and do not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SPEED_SAMPLE_COUNT = 5;
	public static final int PEAK_ROW_COUNT = SPEED_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ PEAK_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double REVIEW_COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	private static final double REVIEW_DT_SECONDS = 0.005;
	private static final int REVIEW_SAMPLE_COUNT = 360;
	private static final double REVIEW_THROTTLE_COMMAND = 1.0;
	private static final double MAX_THRUST_LOSS_OVER_CONTRACT_RATIO =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.caseDefinition("cold_air_forward_punchout_margin")
					.maxSecondaryErrorRatio();
	private static final List<Double> FORWARD_SPEED_SAMPLES_METERS_PER_SECOND =
			List.of(0.0, 8.0, 16.0, 22.0, 28.0);
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic() {
	}

	public record ForwardPunchoutPeakTimingDiagnosticRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double forwardSpeedMetersPerSecond,
			double throttleCommand,
			int sampleCount,
			double dtSeconds,
			double targetMaxRpmScale,
			double equivalentMaxThrustLossPercent,
			int peakSampleIndex,
			double peakTimeSeconds,
			String peakWindow,
			double peakObservedThrustLossPercent,
			double peakThrustLossOverContractRatio,
			double maxAllowedThrustLossOverContractRatio,
			double peakNeutralThrustNewtons,
			double peakDeratedThrustNewtons,
			double peakAverageTargetOmegaRatio,
			double peakAverageMotorOmegaRatio,
			double peakTargetScaleErrorRatio,
			double peakTargetOmegaOvershootRatio,
			double peakMotorOmegaAboveNeutralRatio,
			double peakRotorAdvanceRatio,
			double peakRotorTipMach,
			double earlyWindowMaxThrustLossOverContractRatio,
			double midWindowMaxThrustLossOverContractRatio,
			double lateWindowMaxThrustLossOverContractRatio,
			double settledWindowMaxThrustLossOverContractRatio,
			boolean peakInSettledWindow,
			boolean thrustMarginPassed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record ForwardPunchoutPeakTimingDiagnosticSummary(
			int rowCount,
			int failedRowCount,
			int settledPeakRowCount,
			int earlyWindowFailureRowCount,
			int settledWindowFailureRowCount,
			int deratedTargetAboveNeutralPeakRowCount,
			int deratedMotorAboveNeutralPeakRowCount,
			double minPeakTimeSeconds,
			double maxPeakTimeSeconds,
			double maxPeakThrustLossOverContractRatio,
			double minPeakThrustLossOverContractRatio,
			double maxEarlyWindowThrustLossOverContractRatio,
			double maxMidWindowThrustLossOverContractRatio,
			double maxLateWindowThrustLossOverContractRatio,
			double maxSettledWindowThrustLossOverContractRatio,
			double maxPeakAverageTargetOmegaRatio,
			double maxPeakAverageMotorOmegaRatio,
			String dominantPeakWindow,
			String nextRequiredAction,
			boolean peakTimingDiagnosticPassed
	) {
	}

	public record ForwardPunchoutPeakTimingDiagnosticAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int speedSampleCount,
			int peakRowCount,
			int summaryRowCount,
			int methodRowCount,
			String controlBoundary,
			List<ForwardPunchoutPeakTimingDiagnosticRow> rows,
			ForwardPunchoutPeakTimingDiagnosticSummary summary
	) {
		public ForwardPunchoutPeakTimingDiagnosticAudit {
			rows = List.copyOf(rows);
		}
	}

	public static ForwardPunchoutPeakTimingDiagnosticAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c");
		DroneConfig baseConfig = DroneConfig.apDrone();
		List<ForwardPunchoutPeakTimingDiagnosticRow> rows = new ArrayList<>();
		for (double forwardSpeedMetersPerSecond : FORWARD_SPEED_SAMPLES_METERS_PER_SECOND) {
			rows.add(row(contract, baseConfig, forwardSpeedMetersPerSecond));
		}
		return new ForwardPunchoutPeakTimingDiagnosticAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SPEED_SAMPLE_COUNT,
				PEAK_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CONTROL_BOUNDARY,
				rows,
				summary(rows)
		);
	}

	public static ForwardPunchoutPeakTimingDiagnosticRow row(double forwardSpeedMetersPerSecond) {
		return audit().rows().stream()
				.filter(row -> Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate APDrone forward punchout peak row: "
								+ forwardSpeedMetersPerSecond));
	}

	private static ForwardPunchoutPeakTimingDiagnosticRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			DroneConfig baseConfig,
			double forwardSpeedMetersPerSecond
	) {
		PeakMetrics metrics = simulate(baseConfig, contract.targetMaxRpmScale(),
				contract.equivalentMaxThrustLossPercent(), forwardSpeedMetersPerSecond);
		boolean passed = metrics.peakThrustLossOverContractRatio() <= MAX_THRUST_LOSS_OVER_CONTRACT_RATIO;
		return new ForwardPunchoutPeakTimingDiagnosticRow(
				contract.scenarioName(),
				contract.presetName(),
				contract.ambientCaseName(),
				forwardSpeedMetersPerSecond,
				REVIEW_THROTTLE_COMMAND,
				REVIEW_SAMPLE_COUNT,
				REVIEW_DT_SECONDS,
				contract.targetMaxRpmScale(),
				contract.equivalentMaxThrustLossPercent(),
				metrics.peakSampleIndex(),
				(metrics.peakSampleIndex() + 1) * REVIEW_DT_SECONDS,
				phaseName(metrics.peakSampleIndex()),
				metrics.peakObservedThrustLossPercent(),
				metrics.peakThrustLossOverContractRatio(),
				MAX_THRUST_LOSS_OVER_CONTRACT_RATIO,
				metrics.peakNeutralThrustNewtons(),
				metrics.peakDeratedThrustNewtons(),
				metrics.peakAverageTargetOmegaRatio(),
				metrics.peakAverageMotorOmegaRatio(),
				metrics.peakTargetScaleErrorRatio(),
				metrics.peakTargetOmegaOvershootRatio(),
				metrics.peakMotorOmegaAboveNeutralRatio(),
				metrics.peakRotorAdvanceRatio(),
				metrics.peakRotorTipMach(),
				metrics.earlyWindowMaxThrustLossOverContractRatio(),
				metrics.midWindowMaxThrustLossOverContractRatio(),
				metrics.lateWindowMaxThrustLossOverContractRatio(),
				metrics.settledWindowMaxThrustLossOverContractRatio(),
				isSettledWindow(metrics.peakSampleIndex()),
				passed,
				false,
				false,
				false,
				passed ? "PASS" : "FAIL",
				passed
						? "forward-punchout-peak-margin-within-contract"
						: "forward-punchout-peak-margin-settled-window-failure"
		);
	}

	private static ForwardPunchoutPeakTimingDiagnosticSummary summary(
			List<ForwardPunchoutPeakTimingDiagnosticRow> rows
	) {
		int failed = 0;
		int settledPeak = 0;
		int earlyFailure = 0;
		int settledFailure = 0;
		int targetAbove = 0;
		int motorAbove = 0;
		double minPeakTime = Double.POSITIVE_INFINITY;
		double maxPeakTime = 0.0;
		double maxPeakOver = 0.0;
		double minPeakOver = Double.POSITIVE_INFINITY;
		double earlyMax = 0.0;
		double midMax = 0.0;
		double lateMax = 0.0;
		double settledMax = 0.0;
		double maxTargetRatio = 0.0;
		double maxMotorRatio = 0.0;
		for (ForwardPunchoutPeakTimingDiagnosticRow row : rows) {
			if (!row.thrustMarginPassed()) {
				failed++;
			}
			if (row.peakInSettledWindow()) {
				settledPeak++;
			}
			if (row.earlyWindowMaxThrustLossOverContractRatio() > MAX_THRUST_LOSS_OVER_CONTRACT_RATIO) {
				earlyFailure++;
			}
			if (row.settledWindowMaxThrustLossOverContractRatio() > MAX_THRUST_LOSS_OVER_CONTRACT_RATIO) {
				settledFailure++;
			}
			if (row.peakAverageTargetOmegaRatio() > 1.0) {
				targetAbove++;
			}
			if (row.peakAverageMotorOmegaRatio() > 1.0) {
				motorAbove++;
			}
			minPeakTime = Math.min(minPeakTime, row.peakTimeSeconds());
			maxPeakTime = Math.max(maxPeakTime, row.peakTimeSeconds());
			maxPeakOver = Math.max(maxPeakOver, row.peakThrustLossOverContractRatio());
			minPeakOver = Math.min(minPeakOver, row.peakThrustLossOverContractRatio());
			earlyMax = Math.max(earlyMax, row.earlyWindowMaxThrustLossOverContractRatio());
			midMax = Math.max(midMax, row.midWindowMaxThrustLossOverContractRatio());
			lateMax = Math.max(lateMax, row.lateWindowMaxThrustLossOverContractRatio());
			settledMax = Math.max(settledMax, row.settledWindowMaxThrustLossOverContractRatio());
			maxTargetRatio = Math.max(maxTargetRatio, row.peakAverageTargetOmegaRatio());
			maxMotorRatio = Math.max(maxMotorRatio, row.peakAverageMotorOmegaRatio());
		}
		boolean passed = failed == 0;
		return new ForwardPunchoutPeakTimingDiagnosticSummary(
				rows.size(),
				failed,
				settledPeak,
				earlyFailure,
				settledFailure,
				targetAbove,
				motorAbove,
				Double.isInfinite(minPeakTime) ? 0.0 : minPeakTime,
				maxPeakTime,
				maxPeakOver,
				Double.isInfinite(minPeakOver) ? 0.0 : minPeakOver,
				earlyMax,
				midMax,
				lateMax,
				settledMax,
				maxTargetRatio,
				maxMotorRatio,
				"settled_after_900ms",
				passed
						? "feed-peak-timing-diagnostic-to-blackbox-acceptance"
						: "investigate-apDrone-settled-full-throttle-thrust-model-before-runtime-coupling",
				passed
		);
	}

	private static PeakMetrics simulate(
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
		PeakAccumulator metrics = new PeakAccumulator();
		DroneEnvironment environment = coldAirEnvironment();
		DroneInput input = new DroneInput(REVIEW_THROTTLE_COMMAND, 0.0, 0.0, 0.0, true, true);
		for (int sample = 0; sample < REVIEW_SAMPLE_COUNT; sample++) {
			neutral.step(input, REVIEW_DT_SECONDS, environment);
			derated.step(input, REVIEW_DT_SECONDS, environment);
			metrics.record(sample, neutralConfig, neutral, derated, targetMaxRpmScale,
					equivalentMaxThrustLossPercent);
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

	private static String phaseName(int sampleIndex) {
		if (sampleIndex < 20) {
			return "early_0_to_100ms";
		}
		if (sampleIndex < 80) {
			return "mid_100_to_400ms";
		}
		if (sampleIndex < 180) {
			return "late_400_to_900ms";
		}
		return "settled_after_900ms";
	}

	private static boolean isSettledWindow(int sampleIndex) {
		return sampleIndex >= 180;
	}

	private static final class PeakAccumulator {
		private int peakSampleIndex;
		private double peakObservedThrustLossPercent;
		private double peakThrustLossOverContractRatio = -1.0;
		private double peakNeutralThrustNewtons;
		private double peakDeratedThrustNewtons;
		private double peakAverageTargetOmegaRatio;
		private double peakAverageMotorOmegaRatio;
		private double peakTargetScaleErrorRatio;
		private double peakTargetOmegaOvershootRatio;
		private double peakMotorOmegaAboveNeutralRatio;
		private double peakRotorAdvanceRatio;
		private double peakRotorTipMach;
		private double earlyWindowMaxThrustLossOverContractRatio;
		private double midWindowMaxThrustLossOverContractRatio;
		private double lateWindowMaxThrustLossOverContractRatio;
		private double settledWindowMaxThrustLossOverContractRatio;

		void record(
				int sampleIndex,
				DroneConfig neutralConfig,
				DronePhysics neutral,
				DronePhysics derated,
				double targetMaxRpmScale,
				double equivalentMaxThrustLossPercent
		) {
			double neutralThrust = 0.0;
			double deratedThrust = 0.0;
			double neutralOmega = 0.0;
			double deratedOmega = 0.0;
			double neutralTarget = 0.0;
			double deratedTarget = 0.0;
			double targetScaleError = 0.0;
			double targetOvershoot = 0.0;
			double motorAbove = 0.0;
			double advanceRatio = 0.0;
			double tipMach = 0.0;
			for (int rotorIndex = 0; rotorIndex < neutralConfig.rotors().size(); rotorIndex++) {
				RotorSpec rotor = neutralConfig.rotors().get(rotorIndex);
				double maxOmega = Math.max(EPSILON, rotor.maxOmegaRadiansPerSecond());
				double rotorNeutralTarget = neutral.state().motorTargetOmegaRadiansPerSecond(rotorIndex);
				double rotorDeratedTarget = derated.state().motorTargetOmegaRadiansPerSecond(rotorIndex);
				double rotorNeutralOmega = neutral.state().motorOmegaRadiansPerSecond(rotorIndex);
				double rotorDeratedOmega = derated.state().motorOmegaRadiansPerSecond(rotorIndex);
				neutralTarget += rotorNeutralTarget;
				deratedTarget += rotorDeratedTarget;
				neutralOmega += rotorNeutralOmega;
				deratedOmega += rotorDeratedOmega;
				if (rotorNeutralTarget > EPSILON) {
					targetScaleError = Math.max(
							targetScaleError,
							Math.abs(rotorDeratedTarget / rotorNeutralTarget - targetMaxRpmScale));
				}
				targetOvershoot = Math.max(
						targetOvershoot,
						Math.max(0.0, rotorDeratedTarget - maxOmega * targetMaxRpmScale)
								/ Math.max(EPSILON, maxOmega * targetMaxRpmScale));
				motorAbove = Math.max(
						motorAbove,
						Math.max(0.0, rotorDeratedOmega - rotorNeutralOmega)
								/ Math.max(1.0, rotorNeutralOmega));
				advanceRatio = Math.max(advanceRatio, derated.state().rotorAdvanceRatio(rotorIndex));
				tipMach = Math.max(tipMach, derated.state().rotorTipMach(rotorIndex));
				neutralThrust += Math.max(0.0, neutral.state().rotorThrustNewtons(rotorIndex));
				deratedThrust += Math.max(0.0, derated.state().rotorThrustNewtons(rotorIndex));
			}
			double lossPercent = neutralThrust > EPSILON
					? Math.max(0.0, (neutralThrust - deratedThrust) / neutralThrust) * 100.0
					: 0.0;
			double overContractRatio = Math.max(0.0, lossPercent - equivalentMaxThrustLossPercent) / 100.0;
			recordWindow(sampleIndex, overContractRatio);
			if (overContractRatio >= peakThrustLossOverContractRatio) {
				peakSampleIndex = sampleIndex;
				peakObservedThrustLossPercent = lossPercent;
				peakThrustLossOverContractRatio = overContractRatio;
				peakNeutralThrustNewtons = neutralThrust;
				peakDeratedThrustNewtons = deratedThrust;
				peakAverageTargetOmegaRatio = deratedTarget / Math.max(EPSILON, neutralTarget);
				peakAverageMotorOmegaRatio = deratedOmega / Math.max(EPSILON, neutralOmega);
				peakTargetScaleErrorRatio = targetScaleError;
				peakTargetOmegaOvershootRatio = targetOvershoot;
				peakMotorOmegaAboveNeutralRatio = motorAbove;
				peakRotorAdvanceRatio = advanceRatio;
				peakRotorTipMach = tipMach;
			}
		}

		private void recordWindow(int sampleIndex, double overContractRatio) {
			if (sampleIndex < 20) {
				earlyWindowMaxThrustLossOverContractRatio = Math.max(
						earlyWindowMaxThrustLossOverContractRatio,
						overContractRatio);
			} else if (sampleIndex < 80) {
				midWindowMaxThrustLossOverContractRatio = Math.max(
						midWindowMaxThrustLossOverContractRatio,
						overContractRatio);
			} else if (sampleIndex < 180) {
				lateWindowMaxThrustLossOverContractRatio = Math.max(
						lateWindowMaxThrustLossOverContractRatio,
						overContractRatio);
			} else {
				settledWindowMaxThrustLossOverContractRatio = Math.max(
						settledWindowMaxThrustLossOverContractRatio,
						overContractRatio);
			}
		}

		PeakMetrics toMetrics() {
			return new PeakMetrics(
					peakSampleIndex,
					peakObservedThrustLossPercent,
					peakThrustLossOverContractRatio,
					peakNeutralThrustNewtons,
					peakDeratedThrustNewtons,
					peakAverageTargetOmegaRatio,
					peakAverageMotorOmegaRatio,
					peakTargetScaleErrorRatio,
					peakTargetOmegaOvershootRatio,
					peakMotorOmegaAboveNeutralRatio,
					peakRotorAdvanceRatio,
					peakRotorTipMach,
					earlyWindowMaxThrustLossOverContractRatio,
					midWindowMaxThrustLossOverContractRatio,
					lateWindowMaxThrustLossOverContractRatio,
					settledWindowMaxThrustLossOverContractRatio
			);
		}
	}

	private record PeakMetrics(
			int peakSampleIndex,
			double peakObservedThrustLossPercent,
			double peakThrustLossOverContractRatio,
			double peakNeutralThrustNewtons,
			double peakDeratedThrustNewtons,
			double peakAverageTargetOmegaRatio,
			double peakAverageMotorOmegaRatio,
			double peakTargetScaleErrorRatio,
			double peakTargetOmegaOvershootRatio,
			double peakMotorOmegaAboveNeutralRatio,
			double peakRotorAdvanceRatio,
			double peakRotorTipMach,
			double earlyWindowMaxThrustLossOverContractRatio,
			double midWindowMaxThrustLossOverContractRatio,
			double lateWindowMaxThrustLossOverContractRatio,
			double settledWindowMaxThrustLossOverContractRatio
	) {
	}
}
