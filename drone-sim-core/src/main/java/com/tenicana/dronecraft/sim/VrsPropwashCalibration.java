package com.tenicana.dronecraft.sim;

public final class VrsPropwashCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double CAMBRIDGE_PEAK_BAND_LOW_VI = 1.20;
	private static final double CAMBRIDGE_PEAK_BAND_HIGH_VI = 1.30;
	private static final double CAMBRIDGE_PEAK_LOSS_FRACTION = 0.33;
	private static final double BROAD_REGIME_LOW_VI = 0.50;
	private static final double BROAD_REGIME_HIGH_VI = 2.00;
	private static final double SHETTY_MAX_DIGITIZED_HALF_AMPLITUDE_FRACTION = 0.6625;
	private static final double SHETTY_MAX_DIGITIZED_DESCENT_RATIO_VI = 1.2396163037650905;
	private static final double[] PACKET_SCAN_DESCENT_RATIOS_VI = {
			0.0,
			0.25,
			0.45,
			0.75,
			0.95,
			1.20,
			1.30,
			1.55,
			1.90,
			2.25,
			2.50
	};

	public static final String SOURCE_ID = "VRS-Propwash-Calibration-Packet";
	public static final String CAVEAT = "Keep VRS mean thrust loss, buffet half-amplitude, lateral disturbance, and propwash torque as separate calibration surfaces.";
	public static final int PACKET_METRIC_ROW_COUNT = 1094;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int CURRENT_SCAN_METRIC_ROW_COUNT = 726;
	public static final int CURRENT_SCAN_SCENARIO_COUNT = 66;
	public static final int CURRENT_VS_SHETTY_METRIC_ROW_COUNT = 234;
	public static final int SHETTY_DIGITIZED_METRIC_ROW_COUNT = 91;
	public static final int SHETTY_DIGITIZED_POINT_COUNT = 13;
	public static final int REFERENCE_ANCHOR_METRIC_ROW_COUNT = 28;
	public static final String JOHNSON_REGIME_SOURCE_ID = "VRS-Johnson-Regime-Packet";
	public static final String JOHNSON_REGIME_REPORT_ID = "NASA/TP-2005-213477";
	public static final String JOHNSON_REGIME_CAVEAT =
			"Johnson TP-2005-213477 is used as normalized VRS regime and mean-inflow boundary evidence only.";
	public static final int JOHNSON_REGIME_PACKET_METRIC_ROW_COUNT = 272;
	public static final int JOHNSON_TABLE4_PARAMETER_COUNT = 10;
	public static final int JOHNSON_PRESET_COUNT = 6;
	public static final int JOHNSON_REGIME_BOUNDARY_ROW_COUNT = 6;
	public static final int JOHNSON_PRESET_BOUNDARY_SPEED_ROW_COUNT = 36;
	public static final int JOHNSON_CURRENT_AT_BOUNDARY_ROW_COUNT = 144;
	public static final int JOHNSON_PRESET_SUMMARY_ROW_COUNT = 60;

	private VrsPropwashCalibration() {
	}

	public record ReferenceRegimeAudit(
			double cambridgePeakBandLowVi,
			double cambridgePeakBandHighVi,
			double cambridgePeakLossFraction,
			double broadRegimeLowVi,
			double broadRegimeHighVi,
			double shettyMaxDigitizedHalfAmplitudeFraction,
			double shettyMaxDigitizedDescentRatioVi
	) {
	}

	public record VrsScanSample(
			double descentRatioVi,
			double hoverInducedVelocityMetersPerSecond,
			double descentSpeedMetersPerSecond,
			double currentVrsIntensityHoverSpinNoCrossflow,
			double currentVrsEntryComponent,
			double currentVrsExitComponent,
			double currentVrsBaseThrustLossPercentHoverSpin,
			double currentVrsBuffetHalfAmplitudePercentMaxSpin,
			double currentVrsLateralForceBoundPercentMaxThrust,
			double currentPropwashDescentFactor,
			double propwashMaxTorqueNewtonMeters,
			double buffetFrequencyHertzHoverSpin
	) {
	}

	public record ShettyComparison(
			String referencePropeller,
			double referenceAdvanceRatioJ,
			double descentRatioViProxy,
			double referenceMeasuredHalfAmplitudeFraction,
			double currentVrsBuffetHalfAmplitudeFractionMaxSpin,
			double currentBuffetOverReferenceMeasuredHalfAmplitude,
			double currentBaseLossOverCambridgePeakLoss
	) {
	}

	public record VrsActiveEnvelope(
			double firstActiveDescentRatioVi,
			double lastActiveDescentRatioVi,
			double propwashFullyActiveFromDescentRatioVi,
			double peakLossDescentRatioVi,
			double peakHoverSpinLossPercent
	) {
	}

	public record JohnsonRegimeAudit(
			String sourceId,
			String reportId,
			String caveat,
			int packetMetricRowCount,
			int table4ParameterCount,
			int presetCount,
			int regimeBoundaryRowCount,
			int presetBoundarySpeedRowCount,
			int currentAtBoundaryRowCount,
			int presetSummaryRowCount,
			double modelJoinLowDescentRatioVi,
			double zeroDampingLowDescentRatioVi,
			double zeroDampingHighDescentRatioVi,
			double modelJoinHighDescentRatioVi,
			double baselineForwardCutoffVxOverVh,
			double vrsForwardCutoffVxOverVh,
			double hoverInducedVelocityMetersPerSecond,
			double johnsonNDescentSpeedMetersPerSecond,
			double johnsonXDescentSpeedMetersPerSecond,
			double baselineForwardCutoffMetersPerSecond,
			double vrsForwardCutoffMetersPerSecond,
			double currentIntensityAtJohnsonN,
			double currentIntensityAtJohnsonX,
			double currentPeakDescentIntensityAtBaselineForwardCutoff,
			double currentPeakDescentIntensityAtVrsForwardCutoff,
			double currentForwardEscapeAtBaselineForwardCutoff,
			double currentForwardEscapeAtVrsForwardCutoff
	) {
	}

	public record VrsPropwashAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int currentScanMetricRowCount,
			int currentScanScenarioCount,
			int currentVsShettyMetricRowCount,
			int shettyDigitizedMetricRowCount,
			int shettyDigitizedPointCount,
			int referenceAnchorMetricRowCount,
			ReferenceRegimeAudit referenceRegime,
			JohnsonRegimeAudit johnsonRegime,
			VrsScanSample earlyEntry,
			VrsScanSample peakBandLow,
			VrsScanSample peakBandCenter,
			VrsScanSample highDescentExit,
			ShettyComparison largestShettyDigitized,
			ShettyComparison bestCurrentShettyMatch,
			VrsActiveEnvelope activeEnvelope
	) {
	}

	public static VrsPropwashAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		VrsScanSample early = scanSample(config, 0.75);
		VrsScanSample peakLow = scanSample(config, CAMBRIDGE_PEAK_BAND_LOW_VI);
		VrsScanSample peakCenter = scanSample(config, 1.25);
		VrsScanSample highExit = scanSample(config, 1.90);
		ShettyComparison largestShetty = shettyComparison(
				config,
				"APC Thin Electric 10x5",
				-0.30,
				SHETTY_MAX_DIGITIZED_DESCENT_RATIO_VI,
				SHETTY_MAX_DIGITIZED_HALF_AMPLITUDE_FRACTION
		);
		ShettyComparison bestCurrentMatch = shettyComparison(
				config,
				"APC Thin Electric 10x10",
				-0.35,
				1.3107472992273979,
				0.36725663716814155
		);
		return new VrsPropwashAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				CURRENT_SCAN_METRIC_ROW_COUNT,
				CURRENT_SCAN_SCENARIO_COUNT,
				CURRENT_VS_SHETTY_METRIC_ROW_COUNT,
				SHETTY_DIGITIZED_METRIC_ROW_COUNT,
				SHETTY_DIGITIZED_POINT_COUNT,
				REFERENCE_ANCHOR_METRIC_ROW_COUNT,
				new ReferenceRegimeAudit(
						CAMBRIDGE_PEAK_BAND_LOW_VI,
						CAMBRIDGE_PEAK_BAND_HIGH_VI,
						CAMBRIDGE_PEAK_LOSS_FRACTION,
						BROAD_REGIME_LOW_VI,
						BROAD_REGIME_HIGH_VI,
						SHETTY_MAX_DIGITIZED_HALF_AMPLITUDE_FRACTION,
						SHETTY_MAX_DIGITIZED_DESCENT_RATIO_VI
				),
				johnsonRegimeAudit(config),
				early,
				peakLow,
				peakCenter,
				highExit,
				largestShetty,
				bestCurrentMatch,
				activeEnvelope(config)
		);
	}

	private static JohnsonRegimeAudit johnsonRegimeAudit(DroneConfig config) {
		RotorSpec rotor = representativeRotor(config);
		double hoverThrust = hoverThrustPerRotor(config);
		double hoverOmega = Math.sqrt(hoverThrust / rotor.thrustCoefficient());
		double hoverInducedVelocity = DronePhysics.targetRotorInducedVelocityMetersPerSecond(rotor, hoverThrust, 1.0);
		double baselineForwardCutoff = DronePhysics.JOHNSON_BASELINE_FORWARD_CUTOFF_VX_OVER_VH * hoverInducedVelocity;
		double vrsForwardCutoff = DronePhysics.JOHNSON_VRS_FORWARD_CUTOFF_VX_OVER_VH * hoverInducedVelocity;
		double peakDescentSpeed = CAMBRIDGE_PEAK_BAND_LOW_VI * hoverInducedVelocity;
		return new JohnsonRegimeAudit(
				JOHNSON_REGIME_SOURCE_ID,
				JOHNSON_REGIME_REPORT_ID,
				JOHNSON_REGIME_CAVEAT,
				JOHNSON_REGIME_PACKET_METRIC_ROW_COUNT,
				JOHNSON_TABLE4_PARAMETER_COUNT,
				JOHNSON_PRESET_COUNT,
				JOHNSON_REGIME_BOUNDARY_ROW_COUNT,
				JOHNSON_PRESET_BOUNDARY_SPEED_ROW_COUNT,
				JOHNSON_CURRENT_AT_BOUNDARY_ROW_COUNT,
				JOHNSON_PRESET_SUMMARY_ROW_COUNT,
				DronePhysics.JOHNSON_VRS_MODEL_JOIN_LOW_VI,
				DronePhysics.JOHNSON_VRS_ZERO_DAMPING_LOW_VI,
				DronePhysics.JOHNSON_VRS_ZERO_DAMPING_HIGH_VI,
				DronePhysics.JOHNSON_VRS_MODEL_JOIN_HIGH_VI,
				DronePhysics.JOHNSON_BASELINE_FORWARD_CUTOFF_VX_OVER_VH,
				DronePhysics.JOHNSON_VRS_FORWARD_CUTOFF_VX_OVER_VH,
				hoverInducedVelocity,
				DronePhysics.JOHNSON_VRS_ZERO_DAMPING_LOW_VI * hoverInducedVelocity,
				DronePhysics.JOHNSON_VRS_ZERO_DAMPING_HIGH_VI * hoverInducedVelocity,
				baselineForwardCutoff,
				vrsForwardCutoff,
				steadyVrsAtDescentAndForwardSpeed(rotor, hoverOmega, hoverInducedVelocity,
						DronePhysics.JOHNSON_VRS_ZERO_DAMPING_LOW_VI * hoverInducedVelocity, 0.0),
				steadyVrsAtDescentAndForwardSpeed(rotor, hoverOmega, hoverInducedVelocity,
						DronePhysics.JOHNSON_VRS_ZERO_DAMPING_HIGH_VI * hoverInducedVelocity, 0.0),
				steadyVrsAtDescentAndForwardSpeed(rotor, hoverOmega, hoverInducedVelocity,
						peakDescentSpeed, baselineForwardCutoff),
				steadyVrsAtDescentAndForwardSpeed(rotor, hoverOmega, hoverInducedVelocity,
						peakDescentSpeed, vrsForwardCutoff),
				DronePhysics.rotorVortexRingForwardEscape(baselineForwardCutoff, hoverInducedVelocity),
				DronePhysics.rotorVortexRingForwardEscape(vrsForwardCutoff, hoverInducedVelocity)
		);
	}

	private static double steadyVrsAtDescentAndForwardSpeed(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double hoverInducedVelocityMetersPerSecond,
			double descentSpeedMetersPerSecond,
			double forwardSpeedMetersPerSecond
	) {
		return DronePhysics.calculateSteadyRotorVortexRingStateIntensity(
				rotor,
				new Vec3(forwardSpeedMetersPerSecond, -descentSpeedMetersPerSecond, 0.0),
				omegaRadiansPerSecond,
				hoverInducedVelocityMetersPerSecond
		);
	}

	private static VrsScanSample scanSample(DroneConfig config, double descentRatioVi) {
		RotorSpec rotor = representativeRotor(config);
		double hoverThrust = hoverThrustPerRotor(config);
		double hoverOmega = Math.sqrt(hoverThrust / rotor.thrustCoefficient());
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		double hoverInducedVelocity = DronePhysics.targetRotorInducedVelocityMetersPerSecond(rotor, hoverThrust, 1.0);
		double descentSpeed = descentRatioVi * hoverInducedVelocity;
		Vec3 relativeAirVelocityBody = new Vec3(0.0, -descentSpeed, 0.0);
		double hoverSpinVrs = DronePhysics.calculateSteadyRotorVortexRingStateIntensity(
				rotor,
				relativeAirVelocityBody,
				hoverOmega,
				hoverInducedVelocity
		);
		double maxSpinVrs = DronePhysics.calculateSteadyRotorVortexRingStateIntensity(
				rotor,
				relativeAirVelocityBody,
				maxOmega,
				hoverInducedVelocity
		);
		double maxSpinBuffetFraction = maxSpinBuffetHalfAmplitudeFraction(rotor, maxOmega, maxSpinVrs, descentRatioVi);
		return new VrsScanSample(
				descentRatioVi,
				hoverInducedVelocity,
				descentSpeed,
				hoverSpinVrs,
				vrsEntryComponent(descentRatioVi),
				vrsExitComponent(descentRatioVi),
				DronePhysics.rotorVortexRingMeanThrustLoss(rotor, hoverSpinVrs) * 100.0,
				maxSpinBuffetFraction * 100.0,
				14.0,
				propwashDescentFactor(config, descentSpeed),
				config.propwashMaxTorqueNewtonMeters(),
				DronePhysics.rotorVortexRingBuffetFrequencyHertz(rotor, hoverOmega, hoverSpinVrs, descentRatioVi)
		);
	}

	private static ShettyComparison shettyComparison(
			DroneConfig config,
			String referencePropeller,
			double referenceAdvanceRatioJ,
			double descentRatioViProxy,
			double referenceMeasuredHalfAmplitudeFraction
	) {
		VrsScanSample sample = scanSample(config, descentRatioViProxy);
		double currentBuffet = sample.currentVrsBuffetHalfAmplitudePercentMaxSpin() / 100.0;
		double currentMaxSpinBaseLoss = maxSpinBaseLossFraction(config, descentRatioViProxy);
		return new ShettyComparison(
				referencePropeller,
				referenceAdvanceRatioJ,
				descentRatioViProxy,
				referenceMeasuredHalfAmplitudeFraction,
				currentBuffet,
				ratio(currentBuffet, referenceMeasuredHalfAmplitudeFraction),
				ratio(currentMaxSpinBaseLoss, CAMBRIDGE_PEAK_LOSS_FRACTION)
		);
	}

	private static VrsActiveEnvelope activeEnvelope(DroneConfig config) {
		double firstActive = Double.POSITIVE_INFINITY;
		double lastActive = Double.NEGATIVE_INFINITY;
		double propwashFull = Double.NaN;
		double peakLossRatio = 0.0;
		double peakLossPercent = Double.NEGATIVE_INFINITY;
		for (double descentRatio : PACKET_SCAN_DESCENT_RATIOS_VI) {
			VrsScanSample sample = scanSample(config, descentRatio);
			if (sample.currentVrsIntensityHoverSpinNoCrossflow() > 0.0) {
				firstActive = Math.min(firstActive, descentRatio);
				lastActive = Math.max(lastActive, descentRatio);
			}
			if (!Double.isFinite(propwashFull) && sample.currentPropwashDescentFactor() >= 1.0) {
				propwashFull = descentRatio;
			}
			if (sample.currentVrsBaseThrustLossPercentHoverSpin() > peakLossPercent) {
				peakLossPercent = sample.currentVrsBaseThrustLossPercentHoverSpin();
				peakLossRatio = descentRatio;
			}
		}
		return new VrsActiveEnvelope(
				Double.isFinite(firstActive) ? firstActive : 0.0,
				Double.isFinite(lastActive) ? lastActive : 0.0,
				Double.isFinite(propwashFull) ? propwashFull : 0.0,
				peakLossRatio,
				peakLossPercent
		);
	}

	private static double maxSpinBaseLossFraction(DroneConfig config, double descentRatioVi) {
		RotorSpec rotor = representativeRotor(config);
		double hoverThrust = hoverThrustPerRotor(config);
		double hoverInducedVelocity = DronePhysics.targetRotorInducedVelocityMetersPerSecond(rotor, hoverThrust, 1.0);
		double descentSpeed = descentRatioVi * hoverInducedVelocity;
		double maxSpinVrs = DronePhysics.calculateSteadyRotorVortexRingStateIntensity(
				rotor,
				new Vec3(0.0, -descentSpeed, 0.0),
				rotor.maxOmegaRadiansPerSecond(),
				hoverInducedVelocity
		);
		return DronePhysics.rotorVortexRingMeanThrustLoss(rotor, maxSpinVrs);
	}

	private static double maxSpinBuffetHalfAmplitudeFraction(
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double vortexRingStateIntensity,
			double descentRatioVi
	) {
		double spinRatio = MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double activeRotor = smoothStep(0.16, 0.52, spinRatio);
		double buffetEnvelope = DronePhysics.rotorVortexRingBuffetEnvelope(descentRatioVi);
		double intensity = MathUtil.clamp(
				MathUtil.clamp(vortexRingStateIntensity, 0.0, 1.0) * activeRotor * (0.28 + 0.72 * buffetEnvelope),
				0.0,
				1.0
		);
		double thrustAmplitude = MathUtil.clamp(0.042 + 0.138 * intensity, 0.0, 0.20) * intensity;
		return MathUtil.clamp(thrustAmplitude * 1.55, 0.0, 0.28);
	}

	private static double propwashDescentFactor(DroneConfig config, double descentSpeedMetersPerSecond) {
		return MathUtil.clamp(
				(descentSpeedMetersPerSecond - config.propwashStartDescentMetersPerSecond())
						/ (config.propwashFullDescentMetersPerSecond() - config.propwashStartDescentMetersPerSecond()),
				0.0,
				1.0
		);
	}

	private static double vrsEntryComponent(double descentRatioVi) {
		return smoothStep(0.45, 1.20, descentRatioVi);
	}

	private static double vrsExitComponent(double descentRatioVi) {
		return 1.0 - smoothStep(1.35, 2.25, descentRatioVi);
	}

	private static RotorSpec representativeRotor(DroneConfig config) {
		return config.rotors().get(0);
	}

	private static double hoverThrustPerRotor(DroneConfig config) {
		return config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double ratio(double numerator, double denominator) {
		return Math.abs(denominator) <= EPSILON ? 0.0 : numerator / denominator;
	}
}
