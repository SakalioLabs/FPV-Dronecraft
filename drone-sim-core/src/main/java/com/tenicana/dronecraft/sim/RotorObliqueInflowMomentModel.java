package com.tenicana.dronecraft.sim;

import java.util.List;

public final class RotorObliqueInflowMomentModel {
	private static final Vec3 DEFAULT_ROTOR_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);
	// NREL/CP-5000-63217 Eq. 30, static Pitt-Peters thrust-only first-harmonic coefficient.
	private static final double PITT_PETERS_FIRST_HARMONIC_COEFFICIENT = 15.0 * Math.PI / 64.0;

	private RotorObliqueInflowMomentModel() {
	}

	public static TranslationalLiftSample sampleSteadyTranslationalLift(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond
	) {
		return sampleTranslationalLift(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond,
				0.0,
				0.0,
				true
		);
	}

	public static TranslationalLiftSample stepTranslationalLift(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond,
			double previousIntensity,
			double dtSeconds
	) {
		if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
			throw new IllegalArgumentException("dtSeconds must be finite and positive.");
		}
		validateIntensity(previousIntensity, "previousIntensity");
		return sampleTranslationalLift(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond,
				previousIntensity,
				dtSeconds,
				false
		);
	}

	public static RotorObliqueInflowMomentSample sampleMoment(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double inducedVelocityMetersPerSecond,
			double translationalLiftIntensity,
			double rotorStallIntensity
	) {
		validateMomentInputs(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				thrustNewtons,
				inducedVelocityMetersPerSecond,
				translationalLiftIntensity,
				rotorStallIntensity
		);
		Vec3 rotorAxisBody = rotorAxisBody(rotor);
		Vec3 transverseVelocityBody = transverseVelocityBody(
				relativeAirVelocityBodyMetersPerSecond,
				rotorAxisBody
		);
		double transverseSpeed = transverseVelocityBody.length();
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double advanceRatio = MathUtil.clamp(transverseSpeed / tipSpeed, 0.0, 2.0);
		double advanceSkew = 0.0;
		double loadedRotor = 0.0;
		double stallSoftening = 1.0;
		double inflowSkewIntensity = 0.0;
		if (spinRatio > 0.12 && transverseSpeed > 0.25 && translationalLiftIntensity > 1.0e-6) {
			advanceSkew = smoothStep(0.035, 0.24, advanceRatio);
			loadedRotor = smoothStep(0.18, 0.60, spinRatio);
			stallSoftening = 1.0 - 0.35 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0);
			inflowSkewIntensity = MathUtil.clamp(
					translationalLiftIntensity * advanceSkew * loadedRotor * stallSoftening,
					0.0,
					1.0
			);
		}

		Vec3 transverseUnitBody = Vec3.ZERO;
		// Keep the runtime T*R closure; a 1P inflow distribution is not a blade-root moment law.
		double hubMomentCoefficient = 0.85 * rotor.flappingCoefficient()
				+ 0.35 * rotor.transverseFlowLiftCoefficient();
		double hubMomentMagnitude = 0.0;
		Vec3 skewMomentBody = Vec3.ZERO;
		Vec3 spinCoupledMomentBody = Vec3.ZERO;
		Vec3 totalHubMomentBody = Vec3.ZERO;
		if (inflowSkewIntensity > 1.0e-6
				&& transverseSpeed > 1.0e-6
				&& thrustNewtons > 1.0e-6
				&& hubMomentCoefficient > 1.0e-6) {
			transverseUnitBody = transverseVelocityBody.multiply(1.0 / transverseSpeed);
			hubMomentMagnitude = thrustNewtons
					* rotor.radiusMeters()
					* hubMomentCoefficient
					* inflowSkewIntensity;
			skewMomentBody = transverseUnitBody.cross(rotorAxisBody).multiply(hubMomentMagnitude);
			double advancingBladeMoment = hubMomentMagnitude * 0.28 * rotor.spinDirection();
			spinCoupledMomentBody = transverseUnitBody.multiply(advancingBladeMoment);
			totalHubMomentBody = skewMomentBody.add(spinCoupledMomentBody);
		}

		double axialVelocity = relativeAirVelocityBodyMetersPerSecond.dot(rotorAxisBody);
		boolean wakeModelActive = spinRatio > 0.12
				&& thrustNewtons > 1.0e-6
				&& inducedVelocityMetersPerSecond > 1.0e-6;
		double wakeAxialConvectionVelocity = wakeModelActive
				? axialVelocity + inducedVelocityMetersPerSecond
				: 0.0;
		boolean pittPetersFirstHarmonicApplicable = wakeModelActive
				&& wakeAxialConvectionVelocity > 1.0e-6;
		double wakeSkewAngle = !pittPetersFirstHarmonicApplicable || transverseSpeed <= 1.0e-9
				? 0.0
				: Math.atan2(transverseSpeed, wakeAxialConvectionVelocity);
		double firstHarmonicGain = PITT_PETERS_FIRST_HARMONIC_COEFFICIENT
				* Math.tan(0.5 * wakeSkewAngle);
		double firstHarmonicTipAmplitude = inducedVelocityMetersPerSecond * firstHarmonicGain;
		Vec3 firstHarmonicGradientDirectionBody = !pittPetersFirstHarmonicApplicable
				|| transverseSpeed <= 1.0e-9
				? Vec3.ZERO
				: transverseVelocityBody.multiply(-1.0 / transverseSpeed);
		Vec3 firstHarmonicTipAmplitudeGradientBody = firstHarmonicGradientDirectionBody.multiply(
				firstHarmonicTipAmplitude
		);
		return new RotorObliqueInflowMomentSample(
				rotor,
				rotorAxisBody,
				relativeAirVelocityBodyMetersPerSecond,
				transverseVelocityBody,
				transverseUnitBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				inducedVelocityMetersPerSecond,
				translationalLiftIntensity,
				rotorStallIntensity,
				spinRatio,
				tipSpeed,
				advanceRatio,
				axialVelocity,
				wakeModelActive,
				wakeAxialConvectionVelocity,
				pittPetersFirstHarmonicApplicable,
				wakeSkewAngle,
				firstHarmonicGain,
				firstHarmonicTipAmplitude,
				firstHarmonicGradientDirectionBody,
				firstHarmonicTipAmplitudeGradientBody,
				advanceSkew,
				loadedRotor,
				stallSoftening,
				inflowSkewIntensity,
				hubMomentCoefficient,
				hubMomentMagnitude,
				skewMomentBody,
				spinCoupledMomentBody,
				totalHubMomentBody
		);
	}

	public static RotorObliqueInflowSample sampleSteady(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double inducedVelocityMetersPerSecond,
			double rotorStallIntensity
	) {
		TranslationalLiftSample translationalLift = sampleSteadyTranslationalLift(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond
		);
		RotorObliqueInflowMomentSample moment = sampleMoment(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				thrustNewtons,
				inducedVelocityMetersPerSecond,
				translationalLift.intensity(),
				rotorStallIntensity
		);
		return new RotorObliqueInflowSample(translationalLift, moment);
	}

	public static ConfigurationRotorObliqueInflowSample aggregate(
			List<RotorObliqueInflowSample> rotorSamples
	) {
		List<RotorObliqueInflowSample> requestedSamples = rotorSamples == null ? List.of() : rotorSamples;
		for (RotorObliqueInflowSample sample : requestedSamples) {
			if (sample == null) {
				throw new IllegalArgumentException("rotorSamples must not contain null entries.");
			}
		}
		List<RotorObliqueInflowSample> samples = List.copyOf(requestedSamples);
		Vec3 totalHubMomentBody = Vec3.ZERO;
		double translationalLiftSum = 0.0;
		double inflowSkewSum = 0.0;
		int pittPetersApplicableRotorCount = 0;
		double maximumWakeSkewAngle = 0.0;
		double maximumFirstHarmonicGain = 0.0;
		double maximumFirstHarmonicTipAmplitude = 0.0;
		for (RotorObliqueInflowSample sample : samples) {
			RotorObliqueInflowMomentSample moment = sample.moment();
			totalHubMomentBody = totalHubMomentBody.add(moment.totalHubMomentBodyNewtonMeters());
			translationalLiftSum += sample.translationalLift().intensity();
			inflowSkewSum += moment.inflowSkewIntensity();
			if (moment.pittPetersFirstHarmonicApplicable()) {
				pittPetersApplicableRotorCount++;
			}
			maximumWakeSkewAngle = Math.max(maximumWakeSkewAngle, moment.wakeSkewAngleRadians());
			maximumFirstHarmonicGain = Math.max(
					maximumFirstHarmonicGain,
					moment.pittPetersFirstHarmonicGain()
			);
			maximumFirstHarmonicTipAmplitude = Math.max(
					maximumFirstHarmonicTipAmplitude,
					moment.firstHarmonicTipInducedVelocityAmplitudeMetersPerSecond()
			);
		}
		double divisor = Math.max(1, samples.size());
		return new ConfigurationRotorObliqueInflowSample(
				samples,
				totalHubMomentBody,
				translationalLiftSum / divisor,
				inflowSkewSum / divisor,
				pittPetersApplicableRotorCount,
				maximumWakeSkewAngle,
				maximumFirstHarmonicGain,
				maximumFirstHarmonicTipAmplitude
		);
	}

	private static TranslationalLiftSample sampleTranslationalLift(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond,
			double previousIntensity,
			double dtSeconds,
			boolean steady
	) {
		validateTranslationalLiftInputs(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond
		);
		Vec3 rotorAxisBody = rotorAxisBody(rotor);
		Vec3 transverseVelocityBody = transverseVelocityBody(
				relativeAirVelocityBodyMetersPerSecond,
				rotorAxisBody
		);
		double transverseSpeed = transverseVelocityBody.length();
		double steadySpinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double advanceRatio = transverseSpeed / tipSpeed;
		double inducedRatio = transverseSpeed / Math.max(1.0, inducedVelocityMetersPerSecond);
		double axialVelocity = relativeAirVelocityBodyMetersPerSecond.dot(rotorAxisBody);
		double descentSpeed = Math.max(0.0, -axialVelocity);
		double descentRatio = descentSpeed / Math.max(1.0, inducedVelocityMetersPerSecond);
		double cleanDiskFlow = 0.0;
		double highAdvanceFlow = 0.0;
		double loadedRotor = 0.0;
		double descentWashPenalty = 1.0;
		double targetIntensity = 0.0;
		if (steadySpinRatio > 0.12 && transverseSpeed > 0.25 && inducedVelocityMetersPerSecond > 1.0e-6) {
			cleanDiskFlow = smoothStep(0.45, 1.45, inducedRatio);
			highAdvanceFlow = smoothStep(0.025, 0.16, advanceRatio);
			loadedRotor = smoothStep(0.16, 0.55, steadySpinRatio);
			descentWashPenalty = 1.0 - 0.55 * smoothStep(0.65, 1.50, descentRatio);
			targetIntensity = MathUtil.clamp(
					cleanDiskFlow * highAdvanceFlow * loadedRotor * descentWashPenalty,
					0.0,
					1.0
			);
		}

		double transientSpinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.10
		);
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.50, 2.60);
		double transverseFlush = smoothStep(1.0, 8.0, transverseSpeed);
		double buildTimeConstant = MathUtil.clamp(
				0.075 * Math.sqrt(radiusScale)
						/ (0.78 + 0.36 * transientSpinRatio + 0.24 * transverseFlush),
				0.024,
				0.155
		);
		double releaseTimeConstant = MathUtil.clamp(
				(0.180 - 0.080 * transverseFlush) * Math.sqrt(radiusScale)
						/ (0.72 + 0.28 * transientSpinRatio),
				0.055,
				0.280
		);
		double responseTimeConstant = targetIntensity > previousIntensity
				? buildTimeConstant
				: releaseTimeConstant;
		double responseAlpha = steady ? 1.0 : MathUtil.expSmoothing(dtSeconds, responseTimeConstant);
		double intensity = steady
				? targetIntensity
				: previousIntensity + (targetIntensity - previousIntensity) * responseAlpha;
		if (targetIntensity <= 1.0e-6 && intensity < 1.0e-5) {
			intensity = 0.0;
		}
		return new TranslationalLiftSample(
				rotor,
				rotorAxisBody,
				relativeAirVelocityBodyMetersPerSecond,
				transverseVelocityBody,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond,
				previousIntensity,
				dtSeconds,
				steadySpinRatio,
				transientSpinRatio,
				tipSpeed,
				advanceRatio,
				inducedRatio,
				axialVelocity,
				descentRatio,
				cleanDiskFlow,
				highAdvanceFlow,
				loadedRotor,
				descentWashPenalty,
				targetIntensity,
				radiusScale,
				transverseFlush,
				buildTimeConstant,
				releaseTimeConstant,
				responseTimeConstant,
				responseAlpha,
				intensity,
				steady
		);
	}

	public record TranslationalLiftSample(
			RotorSpec rotor,
			Vec3 rotorAxisBody,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond,
			double previousIntensity,
			double dtSeconds,
			double steadySpinRatio,
			double transientSpinRatio,
			double rotorTipSpeedMetersPerSecond,
			double advanceRatio,
			double transverseOverInducedVelocityRatio,
			double axialVelocityMetersPerSecond,
			double descentOverInducedVelocityRatio,
			double cleanDiskFlowResponse,
			double highAdvanceFlowResponse,
			double loadedRotorResponse,
			double descentWashPenalty,
			double targetIntensity,
			double radiusScale,
			double transverseFlushResponse,
			double buildTimeConstantSeconds,
			double releaseTimeConstantSeconds,
			double responseTimeConstantSeconds,
			double responseAlpha,
			double intensity,
			boolean steady
	) {
	}

	public record RotorObliqueInflowMomentSample(
			RotorSpec rotor,
			Vec3 rotorAxisBody,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityUnitBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double inducedVelocityMetersPerSecond,
			double translationalLiftIntensity,
			double rotorStallIntensity,
			double spinRatio,
			double rotorTipSpeedMetersPerSecond,
			double advanceRatio,
			double axialVelocityMetersPerSecond,
			boolean wakeModelActive,
			double wakeAxialConvectionVelocityMetersPerSecond,
			boolean pittPetersFirstHarmonicApplicable,
			double wakeSkewAngleRadians,
			double pittPetersFirstHarmonicGain,
			double firstHarmonicTipInducedVelocityAmplitudeMetersPerSecond,
			Vec3 firstHarmonicGradientDirectionBody,
			Vec3 firstHarmonicTipAmplitudeGradientBodyMetersPerSecond,
			double advanceSkewResponse,
			double loadedRotorResponse,
			double stallSoftening,
			double inflowSkewIntensity,
			double hubMomentCoefficient,
			double hubMomentMagnitudeNewtonMeters,
			Vec3 skewMomentBodyNewtonMeters,
			Vec3 spinCoupledMomentBodyNewtonMeters,
			Vec3 totalHubMomentBodyNewtonMeters
	) {
		public Vec3 totalHubMomentWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalHubMomentBodyNewtonMeters);
		}

		public double firstHarmonicInducedVelocityPerturbationMetersPerSecond(
				Vec3 diskOffsetBodyMeters
		) {
			validateFiniteVector(diskOffsetBodyMeters, "diskOffsetBodyMeters");
			Vec3 diskOffsetInPlaneBody = diskOffsetBodyMeters.subtract(
					rotorAxisBody.multiply(diskOffsetBodyMeters.dot(rotorAxisBody))
			);
			return firstHarmonicTipAmplitudeGradientBodyMetersPerSecond.dot(diskOffsetInPlaneBody)
					/ rotor.radiusMeters();
		}
	}

	public record RotorObliqueInflowSample(
			TranslationalLiftSample translationalLift,
			RotorObliqueInflowMomentSample moment
	) {
		public RotorObliqueInflowSample {
			if (translationalLift == null || moment == null) {
				throw new IllegalArgumentException("translationalLift and moment must not be null.");
			}
		}
	}

	public record ConfigurationRotorObliqueInflowSample(
			List<RotorObliqueInflowSample> rotorSamples,
			Vec3 totalHubMomentBodyNewtonMeters,
			double averageTranslationalLiftIntensity,
			double averageInflowSkewIntensity,
			int pittPetersFirstHarmonicApplicableRotorCount,
			double maximumWakeSkewAngleRadians,
			double maximumPittPetersFirstHarmonicGain,
			double maximumFirstHarmonicTipInducedVelocityAmplitudeMetersPerSecond
	) {
		public ConfigurationRotorObliqueInflowSample {
			rotorSamples = List.copyOf(rotorSamples == null ? List.of() : rotorSamples);
		}

		public int rotorCount() {
			return rotorSamples.size();
		}

		public Vec3 totalHubMomentWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalHubMomentBodyNewtonMeters);
		}
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9 || axis.y() <= 0.0) {
			return DEFAULT_ROTOR_AXIS_BODY;
		}
		return axis.normalized();
	}

	private static Vec3 transverseVelocityBody(Vec3 relativeAirVelocityBody, Vec3 rotorAxisBody) {
		return relativeAirVelocityBody.subtract(
				rotorAxisBody.multiply(relativeAirVelocityBody.dot(rotorAxisBody))
		);
	}

	private static double rotorTipSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
	}

	private static void validateTranslationalLiftInputs(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double inducedVelocityMetersPerSecond
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		validateFiniteVector(relativeAirVelocityBodyMetersPerSecond,
				"relativeAirVelocityBodyMetersPerSecond");
		validateFiniteNonnegative(omegaRadiansPerSecond, "omegaRadiansPerSecond");
		validateFiniteNonnegative(inducedVelocityMetersPerSecond, "inducedVelocityMetersPerSecond");
	}

	private static void validateMomentInputs(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double inducedVelocityMetersPerSecond,
			double translationalLiftIntensity,
			double rotorStallIntensity
	) {
		validateTranslationalLiftInputs(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				inducedVelocityMetersPerSecond
		);
		validateFiniteNonnegative(thrustNewtons, "thrustNewtons");
		validateIntensity(translationalLiftIntensity, "translationalLiftIntensity");
		validateIntensity(rotorStallIntensity, "rotorStallIntensity");
	}

	private static void validateFiniteVector(Vec3 value, String name) {
		if (value == null || !value.isFinite()) {
			throw new IllegalArgumentException(name + " must be finite.");
		}
	}

	private static void validateFiniteNonnegative(double value, String name) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(name + " must be finite and nonnegative.");
		}
	}

	private static void validateIntensity(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite.");
		}
	}

	private static Quaternion resolvedOrientation(Quaternion bodyToWorldOrientation) {
		if (bodyToWorldOrientation == null
				|| !Double.isFinite(bodyToWorldOrientation.w())
				|| !Double.isFinite(bodyToWorldOrientation.x())
				|| !Double.isFinite(bodyToWorldOrientation.y())
				|| !Double.isFinite(bodyToWorldOrientation.z())) {
			return Quaternion.IDENTITY;
		}
		return bodyToWorldOrientation.normalized();
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
