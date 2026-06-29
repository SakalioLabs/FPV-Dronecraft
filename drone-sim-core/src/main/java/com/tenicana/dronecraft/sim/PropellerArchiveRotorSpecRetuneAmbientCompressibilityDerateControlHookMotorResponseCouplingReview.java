package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Motor-Response-Coupling-Review-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook motor-response coupling review compares neutral and target-max-RPM-derated DronePhysics runs at the ESC-output to motor-response boundary; it does not enable candidate derates, runtime coupling, playable export, or gameplay auto-apply.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int CONTRACT_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT;
	public static final int REVIEW_CASE_COUNT = 3;
	public static final int REVIEW_ROW_COUNT = CONTRACT_ROW_COUNT * REVIEW_CASE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double MAX_ESC_ELECTRICAL_OUTPUT_DELTA = 2.0e-6;
	private static final double MAX_TARGET_SCALE_ERROR_RATIO = 0.012;
	private static final double MAX_TARGET_OVERSHOOT_RATIO = 0.006;
	private static final double MAX_MOTOR_OMEGA_ABOVE_NEUTRAL_RATIO = 1.0e-6;
	private static final DroneEnvironment REVIEW_ENVIRONMENT = DroneEnvironment.calm();
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);

	private static final List<ReviewCaseDefinition> REVIEW_CASES = List.of(
			new ReviewCaseDefinition(
					"first_frame_target_scale_closure",
					"single_frame_full_throttle",
					1,
					0.005,
					MAX_ESC_ELECTRICAL_OUTPUT_DELTA,
					MAX_TARGET_SCALE_ERROR_RATIO,
					MAX_TARGET_OVERSHOOT_RATIO,
					MAX_MOTOR_OMEGA_ABOVE_NEUTRAL_RATIO
			),
			new ReviewCaseDefinition(
					"full_throttle_ramp_motor_response",
					"throttle_ramp",
					180,
					0.006,
					MAX_ESC_ELECTRICAL_OUTPUT_DELTA,
					MAX_TARGET_SCALE_ERROR_RATIO,
					MAX_TARGET_OVERSHOOT_RATIO,
					MAX_MOTOR_OMEGA_ABOVE_NEUTRAL_RATIO
			),
			new ReviewCaseDefinition(
					"hover_hold_tracking_response",
					"hover_hold",
					240,
					0.005,
					MAX_ESC_ELECTRICAL_OUTPUT_DELTA,
					MAX_TARGET_SCALE_ERROR_RATIO,
					MAX_TARGET_OVERSHOOT_RATIO,
					MAX_MOTOR_OMEGA_ABOVE_NEUTRAL_RATIO
			)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview() {
	}

	public record ReviewCaseDefinition(
			String reviewCaseName,
			String flightPhase,
			int sampleCount,
			double dtSeconds,
			double maxAllowedEscElectricalOutputDelta,
			double maxAllowedTargetScaleErrorRatio,
			double maxAllowedTargetOvershootRatio,
			double maxAllowedMotorOmegaAboveNeutralRatio
	) {
	}

	public record DerateControlHookMotorResponseCouplingReviewRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String reviewCaseName,
			String flightPhase,
			int sampleCount,
			double targetMaxRpmScale,
			double contractedMaxRpm,
			double maxEscElectricalOutputDelta,
			double maxTargetScaleErrorRatio,
			double maxTargetOvershootRatio,
			double maxMotorOmegaAboveNeutralRatio,
			boolean defaultPresetTargetScaleNeutral,
			boolean motorResponseCouplingReviewed,
			boolean configMutationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DerateControlHookMotorResponseCouplingSummary(
			int rowCount,
			int reviewedRowCount,
			int presetCount,
			int reviewCaseCount,
			int neutralDefaultPresetCount,
			double maxEscElectricalOutputDelta,
			double maxTargetScaleErrorRatio,
			double maxTargetOvershootRatio,
			double maxMotorOmegaAboveNeutralRatio,
			double minTargetMaxRpmScale,
			double maxEquivalentMaxThrustLossPercent,
			int configMutationAllowedCount,
			int runtimeCouplingAllowedCount,
			int playableReferenceAllowedCount,
			int gameplayAutoApplyAllowedCount,
			boolean motorResponseCouplingReviewed
	) {
	}

	public record DerateControlHookMotorResponseCouplingAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int contractRowCount,
			int reviewCaseCount,
			int reviewRowCount,
			int summaryRowCount,
			int methodRowCount,
			String controlBoundary,
			List<ReviewCaseDefinition> reviewCases,
			List<DerateControlHookMotorResponseCouplingReviewRow> rows,
			DerateControlHookMotorResponseCouplingSummary summary
	) {
		public DerateControlHookMotorResponseCouplingAudit {
			reviewCases = List.copyOf(reviewCases);
			rows = List.copyOf(rows);
		}
	}

	public static DerateControlHookMotorResponseCouplingAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow> contractRows = contract.rows().stream()
				.filter(row -> "synthetic_derate_validation_all_pass".equals(row.scenarioName()))
				.toList();
		List<DerateControlHookMotorResponseCouplingReviewRow> rows = new ArrayList<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contractRow : contractRows) {
			for (ReviewCaseDefinition reviewCase : REVIEW_CASES) {
				rows.add(row(contractRow, reviewCase));
			}
		}
		return new DerateControlHookMotorResponseCouplingAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				CONTRACT_ROW_COUNT,
				REVIEW_CASE_COUNT,
				REVIEW_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CONTROL_BOUNDARY,
				REVIEW_CASES,
				rows,
				summary(rows, contractRows)
		);
	}

	public static DerateControlHookMotorResponseCouplingReviewRow row(
			String presetName,
			String reviewCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.reviewCaseName().equals(reviewCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate motor-response coupling row: "
								+ presetName + "/" + reviewCaseName));
	}

	public static ReviewCaseDefinition reviewCase(String reviewCaseName) {
		return REVIEW_CASES.stream()
				.filter(reviewCase -> reviewCase.reviewCaseName().equals(reviewCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate motor-response coupling case: "
								+ reviewCaseName));
	}

	private static DerateControlHookMotorResponseCouplingReviewRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contractRow,
			ReviewCaseDefinition reviewCase
	) {
		DroneConfig baseConfig = presetConfig(contractRow.presetName());
		ReviewMetrics metrics = simulate(baseConfig, contractRow.targetMaxRpmScale(), reviewCase);
		boolean neutralDefault = defaultPresetTargetScaleNeutral(baseConfig);
		boolean reviewed = neutralDefault
				&& metrics.maxEscElectricalOutputDelta() <= reviewCase.maxAllowedEscElectricalOutputDelta()
				&& metrics.maxTargetScaleErrorRatio() <= reviewCase.maxAllowedTargetScaleErrorRatio()
				&& metrics.maxTargetOvershootRatio() <= reviewCase.maxAllowedTargetOvershootRatio()
				&& metrics.maxMotorOmegaAboveNeutralRatio() <= reviewCase.maxAllowedMotorOmegaAboveNeutralRatio();
		return new DerateControlHookMotorResponseCouplingReviewRow(
				contractRow.scenarioName(),
				contractRow.presetName(),
				contractRow.ambientCaseName(),
				reviewCase.reviewCaseName(),
				reviewCase.flightPhase(),
				reviewCase.sampleCount(),
				contractRow.targetMaxRpmScale(),
				contractRow.contractedMaxRpm(),
				metrics.maxEscElectricalOutputDelta(),
				metrics.maxTargetScaleErrorRatio(),
				metrics.maxTargetOvershootRatio(),
				metrics.maxMotorOmegaAboveNeutralRatio(),
				neutralDefault,
				reviewed,
				false,
				false,
				false,
				false,
				reviewed ? "REVIEWED" : "BLOCKED",
				reviewed
						? "target-omega-derate-couples-before-motor-response-with-esc-output-invariant"
						: "target-omega-derate-motor-response-coupling-review-failed"
		);
	}

	private static DerateControlHookMotorResponseCouplingSummary summary(
			List<DerateControlHookMotorResponseCouplingReviewRow> rows,
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow> contractRows
	) {
		int reviewed = 0;
		int neutralDefaults = 0;
		double escDelta = 0.0;
		double targetScaleError = 0.0;
		double targetOvershoot = 0.0;
		double motorAboveNeutral = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		for (DerateControlHookMotorResponseCouplingReviewRow row : rows) {
			if (row.motorResponseCouplingReviewed()) {
				reviewed++;
			}
			if (row.defaultPresetTargetScaleNeutral()) {
				neutralDefaults++;
			}
			escDelta = Math.max(escDelta, row.maxEscElectricalOutputDelta());
			targetScaleError = Math.max(targetScaleError, row.maxTargetScaleErrorRatio());
			targetOvershoot = Math.max(targetOvershoot, row.maxTargetOvershootRatio());
			motorAboveNeutral = Math.max(motorAboveNeutral, row.maxMotorOmegaAboveNeutralRatio());
			minScale = Math.min(minScale, row.targetMaxRpmScale());
		}
		double maxEquivalentLoss = contractRows.stream()
				.mapToDouble(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
						.DerateControlContractRow::equivalentMaxThrustLossPercent)
				.max()
				.orElse(0.0);
		return new DerateControlHookMotorResponseCouplingSummary(
				rows.size(),
				reviewed,
				(int) contractRows.stream().map(
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
								.DerateControlContractRow::presetName).distinct().count(),
				REVIEW_CASE_COUNT,
				(int) rows.stream()
						.map(DerateControlHookMotorResponseCouplingReviewRow::presetName)
						.distinct()
						.filter(presetName -> defaultPresetTargetScaleNeutral(presetConfig(presetName)))
						.count(),
				escDelta,
				targetScaleError,
				targetOvershoot,
				motorAboveNeutral,
				rows.isEmpty() ? 0.0 : minScale,
				maxEquivalentLoss,
				count(rows, DerateControlHookMotorResponseCouplingReviewRow::configMutationAllowed),
				count(rows, DerateControlHookMotorResponseCouplingReviewRow::runtimeCouplingAllowed),
				count(rows, DerateControlHookMotorResponseCouplingReviewRow::playableReferenceAllowed),
				count(rows, DerateControlHookMotorResponseCouplingReviewRow::gameplayAutoApplyAllowed),
				!rows.isEmpty() && reviewed == rows.size()
		);
	}

	private static int count(
			List<DerateControlHookMotorResponseCouplingReviewRow> rows,
			RowPredicate predicate
	) {
		int count = 0;
		for (DerateControlHookMotorResponseCouplingReviewRow row : rows) {
			if (predicate.test(row)) {
				count++;
			}
		}
		return count;
	}

	private static ReviewMetrics simulate(
			DroneConfig baseConfig,
			double targetMaxRpmScale,
			ReviewCaseDefinition reviewCase
	) {
		DroneConfig neutralConfig = reviewConfig(baseConfig);
		DroneConfig deratedConfig = reviewConfig(baseConfig.withRotorTargetMaxRpmScale(targetMaxRpmScale));
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		double maxEscDelta = 0.0;
		double maxTargetScaleError = 0.0;
		double maxTargetOvershoot = 0.0;
		double maxMotorAboveNeutral = 0.0;
		for (int sample = 0; sample < reviewCase.sampleCount(); sample++) {
			DroneInput input = inputFor(neutralConfig, reviewCase, sample);
			neutral.step(input, reviewCase.dtSeconds(), REVIEW_ENVIRONMENT);
			derated.step(input, reviewCase.dtSeconds(), REVIEW_ENVIRONMENT);
			for (int rotorIndex = 0; rotorIndex < neutralConfig.rotors().size(); rotorIndex++) {
				double neutralTarget = neutral.state().motorTargetOmegaRadiansPerSecond(rotorIndex);
				double deratedTarget = derated.state().motorTargetOmegaRadiansPerSecond(rotorIndex);
				double neutralEsc = neutral.state().escElectricalOutputCommand(rotorIndex);
				double deratedEsc = derated.state().escElectricalOutputCommand(rotorIndex);
				double neutralOmega = neutral.state().motorOmegaRadiansPerSecond(rotorIndex);
				double deratedOmega = derated.state().motorOmegaRadiansPerSecond(rotorIndex);
				double contractedMaxOmega = neutralConfig.rotors().get(rotorIndex).maxOmegaRadiansPerSecond()
						* targetMaxRpmScale;
				maxEscDelta = Math.max(maxEscDelta, Math.abs(neutralEsc - deratedEsc));
				if (neutralTarget > EPSILON) {
					maxTargetScaleError = Math.max(
							maxTargetScaleError,
							Math.abs(deratedTarget / neutralTarget - targetMaxRpmScale));
				}
				maxTargetOvershoot = Math.max(
						maxTargetOvershoot,
						Math.max(0.0, deratedTarget - contractedMaxOmega)
								/ Math.max(EPSILON, contractedMaxOmega));
				maxMotorAboveNeutral = Math.max(
						maxMotorAboveNeutral,
						Math.max(0.0, deratedOmega - neutralOmega)
								/ Math.max(1.0, neutralOmega));
			}
		}
		return new ReviewMetrics(
				maxEscDelta,
				maxTargetScaleError,
				maxTargetOvershoot,
				maxMotorAboveNeutral
		);
	}

	private static DroneInput inputFor(DroneConfig config, ReviewCaseDefinition reviewCase, int sample) {
		double throttle = switch (reviewCase.reviewCaseName()) {
			case "first_frame_target_scale_closure" -> 1.0;
			case "full_throttle_ramp_motor_response" -> 0.20
					+ 0.80 * sample / Math.max(1.0, reviewCase.sampleCount() - 1.0);
			case "hover_hold_tracking_response" -> config.hoverThrottle();
			default -> throw new IllegalArgumentException(
					"unknown RotorSpec retune ambient derate motor-response coupling case: "
							+ reviewCase.reviewCaseName());
		};
		return new DroneInput(throttle, 0.0, 0.0, 0.0, true);
	}

	private static DroneConfig reviewConfig(DroneConfig config) {
		return config
				.withControlLink(0.0, 0.0, config.rcFailsafeTimeoutSeconds())
				.withControlReceiver(0.0, 0.0)
				.withEscCommandSignal(0.0, 0.0)
				.withRateSuper(Vec3.ZERO)
				.withPitchGains(ZERO_GAINS)
				.withYawGains(ZERO_GAINS)
				.withRollGains(ZERO_GAINS)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.006)
				.withBattery(
						config.nominalBatteryVoltage(),
						config.nominalBatteryVoltage() - 0.1,
						0.0,
						Math.max(20.0, config.batteryCapacityAmpHours()),
						Math.max(240.0, config.maxBatteryCurrentAmps()));
	}

	private static DroneConfig presetConfig(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			default -> throw new IllegalArgumentException(
					"unsupported RotorSpec retune ambient derate motor-response preset: " + presetName);
		};
	}

	private static boolean defaultPresetTargetScaleNeutral(DroneConfig config) {
		return config.rotors().stream()
				.allMatch(rotor -> Math.abs(rotor.targetMaxOmegaScale() - 1.0) <= EPSILON);
	}

	private record ReviewMetrics(
			double maxEscElectricalOutputDelta,
			double maxTargetScaleErrorRatio,
			double maxTargetOvershootRatio,
			double maxMotorOmegaAboveNeutralRatio
	) {
	}

	private interface RowPredicate {
		boolean test(DerateControlHookMotorResponseCouplingReviewRow row);
	}
}
