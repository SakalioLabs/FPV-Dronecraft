package com.tenicana.dronecraft.sim;

public final class A4mcDiskGradientCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double MAX_DISK_GRADIENT_METERS_PER_SECOND = 12.0;
	private static final double MAX_DISK_GRADIENT_THRUST_LOSS = 0.045;
	private static final double WALL_SKIM_REFERENCE_RAW_PRESSURE_GRADIENT_METERS_PER_SECOND = 0.33;
	private static final double WALL_SKIM_REFERENCE_SOURCE_QUALITY = 0.86;
	private static final double[] RAW_GRADIENT_SAMPLES_METERS_PER_SECOND = { 0.0, 0.33, 1.0, 2.4, 6.0, 12.0 };
	private static final double[] SOURCE_QUALITY_SAMPLES = { 0.0, 0.50, 0.86, 1.0 };
	private static final String[] SPIN_STATE_SAMPLES = { "idle", "hover", "cruise", "max" };

	public static final String SOURCE_ID = "A4MC-Disk-Gradient-Response-Packet";
	public static final String CAVEAT =
			"Synthetic packet mirrors current Java response formulas with source quality as an availability gate; use as a coefficient audit before live A4MC blackbox fitting.";
	public static final int SOURCE_REFERENCE_COUNT = 4;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int SPIN_STATE_SAMPLE_COUNT = 4;
	public static final int SOURCE_QUALITY_SAMPLE_COUNT = 4;
	public static final int RAW_GRADIENT_SAMPLE_COUNT = 6;
	public static final int RESPONSE_METRIC_COUNT = 16;
	public static final int RESPONSE_MATRIX_SCENARIO_COUNT =
			PRESET_SAMPLE_COUNT * SPIN_STATE_SAMPLE_COUNT * SOURCE_QUALITY_SAMPLE_COUNT * RAW_GRADIENT_SAMPLE_COUNT;
	public static final int RESPONSE_MATRIX_METRIC_ROW_COUNT = RESPONSE_MATRIX_SCENARIO_COUNT * RESPONSE_METRIC_COUNT;
	public static final int WALL_SKIM_REFERENCE_METRIC_ROW_COUNT = PRESET_SAMPLE_COUNT * RESPONSE_METRIC_COUNT;
	public static final int SUMMARY_METRIC_ROW_COUNT = 14;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ RESPONSE_MATRIX_METRIC_ROW_COUNT
			+ WALL_SKIM_REFERENCE_METRIC_ROW_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private A4mcDiskGradientCalibration() {
	}

	public record DiskGradientResponse(
			String presetName,
			String spinState,
			double rawGradientMetersPerSecond,
			double sourceQualityFactor,
			double spinRatio,
			double adoptedGradientMetersPerSecond,
			double tipSpeedMetersPerSecond,
			double gradientOverTipSpeed,
			double steadyThrustFractionProxy,
			double steadyThrustNewtonsProxy,
			double thrustScale,
			double thrustLossFraction,
			double loadFactor,
			double vibration,
			double stallIntensity,
			double flappingTiltDegrees,
			double lateralFlappingForceNewtonsProxy,
			double verticalForceScaleProxy
	) {
	}

	public record ResponseMatrixExtrema(
			double maxFlappingTiltDegrees,
			double maxThrustLossFraction,
			double maxLoadFactor,
			double maxVibration,
			double maxStallIntensity
	) {
	}

	public record A4mcDiskGradientAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int responseMatrixScenarioCount,
			int responseMatrixMetricRowCount,
			int wallSkimReferenceMetricRowCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			double wallSkimReferenceRawPressureGradientMetersPerSecond,
			double wallSkimReferenceSourceQuality,
			double hoverTiltStartRawGradientAtWallSkimQualityMetersPerSecond,
			double hoverThrustLossStartRawGradientAtWallSkimQualityMetersPerSecond,
			DiskGradientResponse wallSkimHover,
			DiskGradientResponse maxGradient,
			DiskGradientResponse qualityZero,
			ResponseMatrixExtrema matrixExtrema
	) {
	}

	public static A4mcDiskGradientAudit racingQuadAudit() {
		return audit(DroneConfig.racingQuad(), "racingQuad");
	}

	public static A4mcDiskGradientAudit audit(DroneConfig config, String presetName) {
		RotorSpec rotor = representativeRotor(config);
		String name = presetName == null || presetName.isBlank() ? "custom" : presetName;
		double hoverSpin = hoverSpinRatio(config, rotor);
		return new A4mcDiskGradientAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				RESPONSE_MATRIX_SCENARIO_COUNT,
				RESPONSE_MATRIX_METRIC_ROW_COUNT,
				WALL_SKIM_REFERENCE_METRIC_ROW_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				WALL_SKIM_REFERENCE_RAW_PRESSURE_GRADIENT_METERS_PER_SECOND,
				WALL_SKIM_REFERENCE_SOURCE_QUALITY,
				rawGradientForTiltStart(rotor, hoverSpin, WALL_SKIM_REFERENCE_SOURCE_QUALITY),
				rawGradientForThrustLossStart(rotor, hoverSpin, WALL_SKIM_REFERENCE_SOURCE_QUALITY),
				response(
						config,
						name,
						"hover",
						WALL_SKIM_REFERENCE_RAW_PRESSURE_GRADIENT_METERS_PER_SECOND,
						WALL_SKIM_REFERENCE_SOURCE_QUALITY,
						hoverSpin
				),
				response(config, name, "max", MAX_DISK_GRADIENT_METERS_PER_SECOND, 1.0, 1.0),
				response(config, name, "hover", MAX_DISK_GRADIENT_METERS_PER_SECOND, 0.0, hoverSpin),
				scanExtrema()
		);
	}

	public static DiskGradientResponse response(
			DroneConfig config,
			String presetName,
			String spinState,
			double rawGradientMetersPerSecond,
			double sourceQualityFactor,
			double spinRatio
	) {
		RotorSpec rotor = representativeRotor(config);
		double sourceQuality = MathUtil.clamp(sourceQualityFactor, 0.0, 1.0);
		double rawGradient = MathUtil.clamp(rawGradientMetersPerSecond, 0.0, MAX_DISK_GRADIENT_METERS_PER_SECOND);
		double adoptedGradient = sourceQuality <= EPSILON ? 0.0 : rawGradient;
		double spin = MathUtil.clamp(spinRatio, 0.0, 1.0);
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		double omega = maxOmega * spin;
		double tipSpeed = tipSpeedMetersPerSecond(rotor, omega);
		double thrustFraction = MathUtil.clamp(spin * spin, 0.0, 1.0);
		double thrustNewtons = rotor.maxThrustNewtons() * thrustFraction;
		double thrustScale = diskGradientThrustScale(rotor, adoptedGradient, omega);
		double loadFactor = diskGradientLoadFactor(rotor, adoptedGradient, omega);
		double vibration = diskGradientVibration(rotor, adoptedGradient, omega);
		double stallIntensity = diskGradientStallIntensity(rotor, adoptedGradient, omega);
		RotorFlappingForceModel.RotorFlappingForceSample flappingSample =
				RotorFlappingForceModel.sampleSteady(
						rotor,
						Vec3.ZERO,
						Vec3.ZERO,
						new Vec3(adoptedGradient, 0.0, 0.0),
						omega,
						thrustNewtons
				);
		double tiltRadians = flappingSample.flappingTiltMagnitudeRadians();
		return new DiskGradientResponse(
				presetName == null || presetName.isBlank() ? "custom" : presetName,
				spinState == null || spinState.isBlank() ? "custom" : spinState,
				rawGradient,
				sourceQuality,
				spin,
				adoptedGradient,
				tipSpeed,
				ratio(adoptedGradient, tipSpeed),
				thrustFraction,
				thrustNewtons,
				thrustScale,
				1.0 - thrustScale,
				loadFactor,
				vibration,
				stallIntensity,
				Math.toDegrees(tiltRadians),
				thrustNewtons * tiltRadians,
				thrustScale * Math.sqrt(Math.max(0.0, 1.0 - tiltRadians * tiltRadians))
		);
	}

	static double hoverSpinRatio(DroneConfig config, RotorSpec rotor) {
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / Math.max(1, config.rotors().size());
		return Math.sqrt(MathUtil.clamp(hoverThrust / rotor.maxThrustNewtons(), 0.0, 1.0));
	}

	static double spinRatioForState(DroneConfig config, RotorSpec rotor, String spinState) {
		if ("hover".equals(spinState)) {
			return hoverSpinRatio(config, rotor);
		}
		if ("idle".equals(spinState)) {
			return 0.12;
		}
		if ("cruise".equals(spinState)) {
			return 0.65;
		}
		if ("max".equals(spinState)) {
			return 1.0;
		}
		return 0.0;
	}

	private static ResponseMatrixExtrema scanExtrema() {
		double maxTilt = 0.0;
		double maxThrustLoss = 0.0;
		double maxLoad = 0.0;
		double maxVibration = 0.0;
		double maxStall = 0.0;
		for (PresetConfig preset : presetConfigs()) {
			RotorSpec rotor = representativeRotor(preset.config());
			for (String spinState : SPIN_STATE_SAMPLES) {
				double spinRatio = spinRatioForState(preset.config(), rotor, spinState);
				for (double sourceQuality : SOURCE_QUALITY_SAMPLES) {
					for (double rawGradient : RAW_GRADIENT_SAMPLES_METERS_PER_SECOND) {
						DiskGradientResponse response = response(
								preset.config(),
								preset.name(),
								spinState,
								rawGradient,
								sourceQuality,
								spinRatio
						);
						maxTilt = Math.max(maxTilt, response.flappingTiltDegrees());
						maxThrustLoss = Math.max(maxThrustLoss, response.thrustLossFraction());
						maxLoad = Math.max(maxLoad, response.loadFactor());
						maxVibration = Math.max(maxVibration, response.vibration());
						maxStall = Math.max(maxStall, response.stallIntensity());
					}
				}
			}
		}
		return new ResponseMatrixExtrema(maxTilt, maxThrustLoss, maxLoad, maxVibration, maxStall);
	}

	private static PresetConfig[] presetConfigs() {
		return new PresetConfig[] {
				new PresetConfig("racingQuad", DroneConfig.racingQuad()),
				new PresetConfig("apDrone", DroneConfig.apDrone()),
				new PresetConfig("cinewhoop", DroneConfig.cinewhoop()),
				new PresetConfig("heavyLift", DroneConfig.heavyLift())
		};
	}

	private static double rawGradientForTiltStart(RotorSpec rotor, double spinRatio, double sourceQuality) {
		double tipSpeed = tipSpeedMetersPerSecond(rotor, rotor.maxOmegaRadiansPerSecond() * spinRatio);
		return rawGradientForAdoptedThreshold(0.03 * Math.max(1.0, tipSpeed * 0.12), sourceQuality);
	}

	private static double rawGradientForThrustLossStart(RotorSpec rotor, double spinRatio, double sourceQuality) {
		double tipSpeed = tipSpeedMetersPerSecond(rotor, rotor.maxOmegaRadiansPerSecond() * spinRatio);
		return rawGradientForAdoptedThreshold(0.04 * Math.max(1.0, tipSpeed * 0.14), sourceQuality);
	}

	private static double rawGradientForAdoptedThreshold(double adoptedThreshold, double sourceQuality) {
		if (!Double.isFinite(adoptedThreshold) || !Double.isFinite(sourceQuality) || sourceQuality <= EPSILON) {
			return 0.0;
		}
		return MathUtil.clamp(adoptedThreshold, 0.0, MAX_DISK_GRADIENT_METERS_PER_SECOND);
	}

	private static double diskGradientThrustScale(RotorSpec rotor, double adoptedGradient, double omegaRadiansPerSecond) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.06) {
			return 1.0;
		}
		double tipSpeed = tipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(adoptedGradient / Math.max(1.0, tipSpeed * 0.14), 0.0, 1.0);
		double loss = MAX_DISK_GRADIENT_THRUST_LOSS
				* smoothStep(0.04, 0.58, gradientRatio)
				* smoothStep(0.10, 0.55, spinRatio);
		return MathUtil.clamp(1.0 - loss, 1.0 - MAX_DISK_GRADIENT_THRUST_LOSS, 1.0);
	}

	private static double diskGradientLoadFactor(RotorSpec rotor, double adoptedGradient, double omegaRadiansPerSecond) {
		if (adoptedGradient <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double tipSpeed = tipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(adoptedGradient / Math.max(1.0, tipSpeed * 0.16), 0.0, 1.0);
		return MathUtil.clamp(0.18 * Math.pow(gradientRatio, 0.85) * smoothStep(0.10, 0.55, spinRatio), 0.0, 0.18);
	}

	private static double diskGradientVibration(RotorSpec rotor, double adoptedGradient, double omegaRadiansPerSecond) {
		if (adoptedGradient <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double tipSpeed = tipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(adoptedGradient / Math.max(1.0, tipSpeed * 0.12), 0.0, 1.0);
		return MathUtil.clamp(0.18 * Math.pow(gradientRatio, 0.80) * smoothStep(0.08, 0.50, spinRatio), 0.0, 0.18);
	}

	private static double diskGradientStallIntensity(RotorSpec rotor, double adoptedGradient, double omegaRadiansPerSecond) {
		if (adoptedGradient <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		if (spinRatio <= 0.08) {
			return 0.0;
		}
		double tipSpeed = tipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double gradientRatio = MathUtil.clamp(adoptedGradient / Math.max(1.0, tipSpeed * 0.10), 0.0, 1.0);
		return MathUtil.clamp(0.14 * smoothStep(0.10, 0.48, gradientRatio) * smoothStep(0.12, 0.50, spinRatio), 0.0, 0.14);
	}

	private static double tipSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static RotorSpec representativeRotor(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		return config.rotors().get(0);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private record PresetConfig(String name, DroneConfig config) {
	}
}
