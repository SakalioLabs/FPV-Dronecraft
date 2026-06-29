package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Failsafe-Clamp-Review-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook failsafe clamp review compares neutral and target-max-RPM-derated DronePhysics release runs after direct disarm, link-loss failsafe, and high-RPM no-load release; it does not enable candidate derates, runtime coupling, playable export, or gameplay auto-apply.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int CONTRACT_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT;
	public static final int REVIEW_CASE_COUNT = 3;
	public static final int REVIEW_ROW_COUNT = CONTRACT_ROW_COUNT * REVIEW_CASE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double MAX_TARGET_OMEGA_AFTER_RELEASE_RATIO = 1.0e-12;
	private static final double MAX_ESC_ELECTRICAL_OUTPUT_AFTER_RELEASE = 1.0e-8;
	private static final double MAX_MOTOR_OMEGA_OVERSPEED_RATIO = 1.0e-12;
	private static final double MAX_MOTOR_OMEGA_RISE_AFTER_RELEASE_RATIO = 1.0e-12;
	private static final double MAX_DERATED_OMEGA_ABOVE_NEUTRAL_MAX_OMEGA_RATIO = 0.020;
	private static final double FAILSAFE_TIMEOUT_SECONDS = 0.020;
	private static final DroneEnvironment REVIEW_ENVIRONMENT = DroneEnvironment.calm();
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);

	private static final List<ReviewCaseDefinition> REVIEW_CASES = List.of(
			new ReviewCaseDefinition(
					"direct_disarm_target_zero",
					"direct_disarm",
					120,
					80,
					0.005,
					false,
					MAX_TARGET_OMEGA_AFTER_RELEASE_RATIO,
					MAX_ESC_ELECTRICAL_OUTPUT_AFTER_RELEASE,
					MAX_MOTOR_OMEGA_OVERSPEED_RATIO,
					MAX_MOTOR_OMEGA_RISE_AFTER_RELEASE_RATIO,
					MAX_DERATED_OMEGA_ABOVE_NEUTRAL_MAX_OMEGA_RATIO
			),
			new ReviewCaseDefinition(
					"link_loss_failsafe_target_zero",
					"failsafe_disarm",
					120,
					120,
					0.005,
					true,
					MAX_TARGET_OMEGA_AFTER_RELEASE_RATIO,
					MAX_ESC_ELECTRICAL_OUTPUT_AFTER_RELEASE,
					MAX_MOTOR_OMEGA_OVERSPEED_RATIO,
					MAX_MOTOR_OMEGA_RISE_AFTER_RELEASE_RATIO,
					MAX_DERATED_OMEGA_ABOVE_NEUTRAL_MAX_OMEGA_RATIO
			),
			new ReviewCaseDefinition(
					"high_rpm_no_load_disarm_release",
					"no_load_overspeed_release",
					220,
					120,
					0.005,
					false,
					MAX_TARGET_OMEGA_AFTER_RELEASE_RATIO,
					MAX_ESC_ELECTRICAL_OUTPUT_AFTER_RELEASE,
					MAX_MOTOR_OMEGA_OVERSPEED_RATIO,
					MAX_MOTOR_OMEGA_RISE_AFTER_RELEASE_RATIO,
					MAX_DERATED_OMEGA_ABOVE_NEUTRAL_MAX_OMEGA_RATIO
			)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview() {
	}

	public record ReviewCaseDefinition(
			String reviewCaseName,
			String flightPhase,
			int spoolSampleCount,
			int releaseSampleCount,
			double dtSeconds,
			boolean requiresFailsafeActivation,
			double maxAllowedTargetOmegaAfterReleaseRatio,
			double maxAllowedEscElectricalOutputAfterRelease,
			double maxAllowedMotorOmegaOverspeedRatio,
			double maxAllowedMotorOmegaRiseAfterReleaseRatio,
			double maxAllowedDeratedOmegaAboveNeutralMaxOmegaRatio
	) {
	}

	public record DerateControlHookFailsafeClampReviewRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String reviewCaseName,
			String flightPhase,
			int spoolSampleCount,
			int releaseSampleCount,
			double dtSeconds,
			double targetMaxRpmScale,
			double contractedMaxRpm,
			double maxTargetOmegaAfterReleaseRatio,
			double maxEscElectricalOutputAfterRelease,
			double maxMotorOmegaOverspeedRatio,
			double maxMotorOmegaRiseAfterReleaseRatio,
			double maxDeratedOmegaAboveNeutralMaxOmegaRatio,
			boolean failsafeActivated,
			boolean processedDisarmed,
			boolean failsafeClampReviewed,
			boolean configMutationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DerateControlHookFailsafeClampSummary(
			int rowCount,
			int reviewedRowCount,
			int presetCount,
			int reviewCaseCount,
			int failsafeActivationRowCount,
			int disarmedReleaseRowCount,
			double maxTargetOmegaAfterReleaseRatio,
			double maxEscElectricalOutputAfterRelease,
			double maxMotorOmegaOverspeedRatio,
			double maxMotorOmegaRiseAfterReleaseRatio,
			double maxDeratedOmegaAboveNeutralMaxOmegaRatio,
			double minTargetMaxRpmScale,
			double maxEquivalentMaxThrustLossPercent,
			int configMutationAllowedCount,
			int runtimeCouplingAllowedCount,
			int playableReferenceAllowedCount,
			int gameplayAutoApplyAllowedCount,
			boolean failsafeClampReviewed
	) {
	}

	public record DerateControlHookFailsafeClampAudit(
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
			List<DerateControlHookFailsafeClampReviewRow> rows,
			DerateControlHookFailsafeClampSummary summary
	) {
		public DerateControlHookFailsafeClampAudit {
			reviewCases = List.copyOf(reviewCases);
			rows = List.copyOf(rows);
		}
	}

	public static DerateControlHookFailsafeClampAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow> contractRows = contract.rows().stream()
				.filter(row -> "synthetic_derate_validation_all_pass".equals(row.scenarioName()))
				.toList();
		List<DerateControlHookFailsafeClampReviewRow> rows = new ArrayList<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contractRow : contractRows) {
			for (ReviewCaseDefinition reviewCase : REVIEW_CASES) {
				rows.add(row(contractRow, reviewCase));
			}
		}
		return new DerateControlHookFailsafeClampAudit(
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

	public static DerateControlHookFailsafeClampReviewRow row(
			String presetName,
			String reviewCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.reviewCaseName().equals(reviewCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate failsafe clamp row: "
								+ presetName + "/" + reviewCaseName));
	}

	public static ReviewCaseDefinition reviewCase(String reviewCaseName) {
		return REVIEW_CASES.stream()
				.filter(reviewCase -> reviewCase.reviewCaseName().equals(reviewCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate failsafe clamp case: "
								+ reviewCaseName));
	}

	private static DerateControlHookFailsafeClampReviewRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contractRow,
			ReviewCaseDefinition reviewCase
	) {
		DroneConfig baseConfig = presetConfig(contractRow.presetName());
		ReleaseMetrics metrics = simulate(baseConfig, contractRow.targetMaxRpmScale(), reviewCase);
		boolean reviewed = metrics.processedDisarmed()
				&& (!reviewCase.requiresFailsafeActivation() || metrics.failsafeActivated())
				&& metrics.maxTargetOmegaAfterReleaseRatio() <= reviewCase.maxAllowedTargetOmegaAfterReleaseRatio()
				&& metrics.maxEscElectricalOutputAfterRelease() <= reviewCase.maxAllowedEscElectricalOutputAfterRelease()
				&& metrics.maxMotorOmegaOverspeedRatio() <= reviewCase.maxAllowedMotorOmegaOverspeedRatio()
				&& metrics.maxMotorOmegaRiseAfterReleaseRatio() <= reviewCase.maxAllowedMotorOmegaRiseAfterReleaseRatio()
				&& metrics.maxDeratedOmegaAboveNeutralMaxOmegaRatio()
						<= reviewCase.maxAllowedDeratedOmegaAboveNeutralMaxOmegaRatio();
		return new DerateControlHookFailsafeClampReviewRow(
				contractRow.scenarioName(),
				contractRow.presetName(),
				contractRow.ambientCaseName(),
				reviewCase.reviewCaseName(),
				reviewCase.flightPhase(),
				reviewCase.spoolSampleCount(),
				reviewCase.releaseSampleCount(),
				reviewCase.dtSeconds(),
				contractRow.targetMaxRpmScale(),
				contractRow.contractedMaxRpm(),
				metrics.maxTargetOmegaAfterReleaseRatio(),
				metrics.maxEscElectricalOutputAfterRelease(),
				metrics.maxMotorOmegaOverspeedRatio(),
				metrics.maxMotorOmegaRiseAfterReleaseRatio(),
				metrics.maxDeratedOmegaAboveNeutralMaxOmegaRatio(),
				metrics.failsafeActivated(),
				metrics.processedDisarmed(),
				reviewed,
				false,
				false,
				false,
				false,
				reviewed ? "REVIEWED" : "BLOCKED",
				reviewed
						? "target-omega-derate-failsafe-clamp-and-no-load-release-reviewed"
						: "target-omega-derate-failsafe-clamp-review-failed"
		);
	}

	private static DerateControlHookFailsafeClampSummary summary(
			List<DerateControlHookFailsafeClampReviewRow> rows,
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow> contractRows
	) {
		int reviewed = 0;
		int failsafe = 0;
		int disarmed = 0;
		double target = 0.0;
		double esc = 0.0;
		double overspeed = 0.0;
		double rise = 0.0;
		double deratedAboveNeutral = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		for (DerateControlHookFailsafeClampReviewRow row : rows) {
			if (row.failsafeClampReviewed()) {
				reviewed++;
			}
			if (row.failsafeActivated()) {
				failsafe++;
			}
			if (row.processedDisarmed()) {
				disarmed++;
			}
			target = Math.max(target, row.maxTargetOmegaAfterReleaseRatio());
			esc = Math.max(esc, row.maxEscElectricalOutputAfterRelease());
			overspeed = Math.max(overspeed, row.maxMotorOmegaOverspeedRatio());
			rise = Math.max(rise, row.maxMotorOmegaRiseAfterReleaseRatio());
			deratedAboveNeutral = Math.max(deratedAboveNeutral, row.maxDeratedOmegaAboveNeutralMaxOmegaRatio());
			minScale = Math.min(minScale, row.targetMaxRpmScale());
		}
		double maxEquivalentLoss = contractRows.stream()
				.mapToDouble(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
						.DerateControlContractRow::equivalentMaxThrustLossPercent)
				.max()
				.orElse(0.0);
		return new DerateControlHookFailsafeClampSummary(
				rows.size(),
				reviewed,
				(int) contractRows.stream().map(
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
								.DerateControlContractRow::presetName).distinct().count(),
				REVIEW_CASE_COUNT,
				failsafe,
				disarmed,
				target,
				esc,
				overspeed,
				rise,
				deratedAboveNeutral,
				rows.isEmpty() ? 0.0 : minScale,
				maxEquivalentLoss,
				count(rows, DerateControlHookFailsafeClampReviewRow::configMutationAllowed),
				count(rows, DerateControlHookFailsafeClampReviewRow::runtimeCouplingAllowed),
				count(rows, DerateControlHookFailsafeClampReviewRow::playableReferenceAllowed),
				count(rows, DerateControlHookFailsafeClampReviewRow::gameplayAutoApplyAllowed),
				!rows.isEmpty() && reviewed == rows.size()
		);
	}

	private static int count(
			List<DerateControlHookFailsafeClampReviewRow> rows,
			RowPredicate predicate
	) {
		int count = 0;
		for (DerateControlHookFailsafeClampReviewRow row : rows) {
			if (predicate.test(row)) {
				count++;
			}
		}
		return count;
	}

	private static ReleaseMetrics simulate(
			DroneConfig baseConfig,
			double targetMaxRpmScale,
			ReviewCaseDefinition reviewCase
	) {
		DroneConfig neutralConfig = reviewConfig(baseConfig, 1.0);
		DroneConfig deratedConfig = reviewConfig(baseConfig, targetMaxRpmScale);
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		DroneInput spoolInput = new DroneInput(1.0, 0.0, 0.0, 0.0, true, true);
		for (int sample = 0; sample < reviewCase.spoolSampleCount(); sample++) {
			neutral.step(spoolInput, reviewCase.dtSeconds(), REVIEW_ENVIRONMENT);
			derated.step(spoolInput, reviewCase.dtSeconds(), REVIEW_ENVIRONMENT);
		}

		double[] releaseStartOmega = derated.state().motorOmegaRadiansPerSecond();
		boolean failsafeActivated = false;
		boolean processedDisarmed = false;
		double maxTarget = 0.0;
		double maxEsc = 0.0;
		double maxOverspeed = 0.0;
		double maxRise = 0.0;
		double maxDeratedAboveNeutral = 0.0;
		for (int sample = 0; sample < reviewCase.releaseSampleCount(); sample++) {
			DroneInput input = releaseInput(reviewCase);
			neutral.step(input, reviewCase.dtSeconds(), REVIEW_ENVIRONMENT);
			derated.step(input, reviewCase.dtSeconds(), REVIEW_ENVIRONMENT);
			boolean reviewWindowOpen = !reviewCase.requiresFailsafeActivation()
					|| derated.state().controlFailsafeActive();
			if (!reviewWindowOpen) {
				continue;
			}
			failsafeActivated |= derated.state().controlFailsafeActive();
			processedDisarmed |= !derated.state().processedControlInput().armed();
			for (int rotorIndex = 0; rotorIndex < deratedConfig.rotors().size(); rotorIndex++) {
				RotorSpec rotor = deratedConfig.rotors().get(rotorIndex);
				double maxOmega = Math.max(EPSILON, rotor.maxOmegaRadiansPerSecond());
				double deratedOmega = derated.state().motorOmegaRadiansPerSecond(rotorIndex);
				double neutralOmega = neutral.state().motorOmegaRadiansPerSecond(rotorIndex);
				maxTarget = Math.max(
						maxTarget,
						derated.state().motorTargetOmegaRadiansPerSecond(rotorIndex) / maxOmega);
				maxEsc = Math.max(maxEsc, derated.state().escElectricalOutputCommand(rotorIndex));
				maxOverspeed = Math.max(
						maxOverspeed,
						Math.max(0.0, deratedOmega - maxOmega * 1.08) / (maxOmega * 1.08));
				maxRise = Math.max(
						maxRise,
						Math.max(0.0, deratedOmega - releaseStartOmega[rotorIndex]) / maxOmega);
				maxDeratedAboveNeutral = Math.max(
						maxDeratedAboveNeutral,
						Math.max(0.0, deratedOmega - neutralOmega) / maxOmega);
			}
		}
		return new ReleaseMetrics(
				failsafeActivated,
				processedDisarmed,
				maxTarget,
				maxEsc,
				maxOverspeed,
				maxRise,
				maxDeratedAboveNeutral
		);
	}

	private static DroneInput releaseInput(ReviewCaseDefinition reviewCase) {
		return switch (reviewCase.reviewCaseName()) {
			case "direct_disarm_target_zero", "high_rpm_no_load_disarm_release" ->
					new DroneInput(0.0, 0.0, 0.0, 0.0, false, true);
			case "link_loss_failsafe_target_zero" ->
					new DroneInput(1.0, 0.0, 0.0, 0.0, true, false);
			default -> throw new IllegalArgumentException(
					"unknown RotorSpec retune ambient derate failsafe clamp case: "
							+ reviewCase.reviewCaseName());
		};
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

	private static DroneConfig presetConfig(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			default -> throw new IllegalArgumentException(
					"unsupported RotorSpec retune ambient derate failsafe clamp preset: " + presetName);
		};
	}

	private record ReleaseMetrics(
			boolean failsafeActivated,
			boolean processedDisarmed,
			double maxTargetOmegaAfterReleaseRatio,
			double maxEscElectricalOutputAfterRelease,
			double maxMotorOmegaOverspeedRatio,
			double maxMotorOmegaRiseAfterReleaseRatio,
			double maxDeratedOmegaAboveNeutralMaxOmegaRatio
	) {
	}

	private interface RowPredicate {
		boolean test(DerateControlHookFailsafeClampReviewRow row);
	}
}
