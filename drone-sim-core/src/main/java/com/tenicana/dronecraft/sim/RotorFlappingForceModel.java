package com.tenicana.dronecraft.sim;

import java.util.List;

public final class RotorFlappingForceModel {
	private static final Vec3 DEFAULT_ROTOR_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);
	private static final double MAX_FLAPPING_TILT_RADIANS = Math.toRadians(18.0);
	private static final double MAX_DISK_WIND_GRADIENT_TILT_RADIANS = Math.toRadians(4.5);
	private static final double FLAPPING_ADVANCE_RATIO_FULL_RESPONSE = 0.095;
	private static final double BUILDUP_TIME_CONSTANT_SECONDS = 0.026;
	private static final double RECOVERY_TIME_CONSTANT_SECONDS = 0.050;

	private RotorFlappingForceModel() {
	}

	public static RotorFlappingForceSample sampleSteady(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 diskWindGradientBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons
	) {
		return sampleSteady(
				rotor,
				momentArmBodyMeters,
				relativeAirVelocityBodyMetersPerSecond,
				diskWindGradientBodyMetersPerSecond,
				omegaRadiansPerSecond,
				thrustNewtons,
				Vec3.ZERO
		);
	}

	public static RotorFlappingForceSample sampleSteady(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 diskWindGradientBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			Vec3 nominalReactionTorqueBodyNewtonMeters
	) {
		return sample(
				rotor,
				momentArmBodyMeters,
				relativeAirVelocityBodyMetersPerSecond,
				diskWindGradientBodyMetersPerSecond,
				omegaRadiansPerSecond,
				thrustNewtons,
				Vec3.ZERO,
				0.0,
				nominalReactionTorqueBodyNewtonMeters,
				true
		);
	}

	public static RotorFlappingForceSample sampleTransient(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 diskWindGradientBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			Vec3 previousFlappingTiltBodyRadians,
			double dtSeconds
	) {
		if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
			throw new IllegalArgumentException("dtSeconds must be finite and positive.");
		}
		return sample(
				rotor,
				momentArmBodyMeters,
				relativeAirVelocityBodyMetersPerSecond,
				diskWindGradientBodyMetersPerSecond,
				omegaRadiansPerSecond,
				thrustNewtons,
				previousFlappingTiltBodyRadians,
				dtSeconds,
				Vec3.ZERO,
				false
		);
	}

	public static ConfigurationRotorFlappingForceSample aggregate(
			List<RotorFlappingForceSample> rotorSamples
	) {
		List<RotorFlappingForceSample> requestedSamples = rotorSamples == null ? List.of() : rotorSamples;
		for (RotorFlappingForceSample sample : requestedSamples) {
			if (sample == null) {
				throw new IllegalArgumentException("rotorSamples must not contain null entries.");
			}
		}
		List<RotorFlappingForceSample> samples = List.copyOf(requestedSamples);
		Vec3 totalFlappingForceBody = Vec3.ZERO;
		Vec3 totalForceMomentBody = Vec3.ZERO;
		Vec3 totalReactionTorqueAxisCorrectionBody = Vec3.ZERO;
		Vec3 totalTorqueCorrectionBody = Vec3.ZERO;
		double totalTranslationalPower = 0.0;
		double maximumTilt = 0.0;
		double maximumThrustMagnitudeResidual = 0.0;
		for (RotorFlappingForceSample sample : samples) {
			totalFlappingForceBody = totalFlappingForceBody.add(sample.flappingForceBodyNewtons());
			totalForceMomentBody = totalForceMomentBody.add(sample.forceMomentBodyNewtonMeters());
			totalReactionTorqueAxisCorrectionBody = totalReactionTorqueAxisCorrectionBody.add(
					sample.reactionTorqueAxisCorrectionBodyNewtonMeters()
			);
			totalTorqueCorrectionBody = totalTorqueCorrectionBody.add(sample.totalTorqueCorrectionBodyNewtonMeters());
			totalTranslationalPower += sample.translationalPowerWatts();
			maximumTilt = Math.max(maximumTilt, sample.effectiveDiskTiltAngleRadians());
			maximumThrustMagnitudeResidual = Math.max(
					maximumThrustMagnitudeResidual,
					Math.abs(sample.thrustMagnitudeResidualNewtons())
			);
		}
		return new ConfigurationRotorFlappingForceSample(
				samples,
				totalFlappingForceBody,
				totalForceMomentBody,
				totalReactionTorqueAxisCorrectionBody,
				totalTorqueCorrectionBody,
				totalTranslationalPower,
				maximumTilt,
				maximumThrustMagnitudeResidual
		);
	}

	private static RotorFlappingForceSample sample(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 diskWindGradientBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			Vec3 previousFlappingTiltBodyRadians,
			double dtSeconds,
			Vec3 nominalReactionTorqueBodyNewtonMeters,
			boolean steady
	) {
		validateInputs(
				rotor,
				momentArmBodyMeters,
				relativeAirVelocityBodyMetersPerSecond,
				diskWindGradientBodyMetersPerSecond,
				omegaRadiansPerSecond,
				thrustNewtons,
				previousFlappingTiltBodyRadians,
				nominalReactionTorqueBodyNewtonMeters
		);
		Vec3 rotorAxisBody = rotorAxisBody(rotor);
		Vec3 transverseVelocityBody = projectOntoRotorDisk(
				relativeAirVelocityBodyMetersPerSecond,
				rotorAxisBody
		);
		double transverseSpeed = transverseVelocityBody.length();
		double tipSpeed = rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double advanceRatio = MathUtil.clamp(transverseSpeed / tipSpeed, 0.0, 2.0);
		double thrustFraction = MathUtil.clamp(thrustNewtons / rotor.maxThrustNewtons(), 0.0, 1.0);
		double advanceResponse = MathUtil.clamp(
				advanceRatio / FLAPPING_ADVANCE_RATIO_FULL_RESPONSE,
				0.0,
				1.0
		);
		double diskLoadingResponse = MathUtil.clamp(
				0.72 + 0.28 * Math.sqrt(thrustFraction),
				0.0,
				1.0
		);
		Vec3 crossflowTargetTiltBody = crossflowTargetTiltBody(
				rotor,
				transverseVelocityBody,
				thrustNewtons,
				advanceResponse,
				diskLoadingResponse
		);

		Vec3 diskWindGradientInPlaneBody = projectOntoRotorDisk(
				diskWindGradientBodyMetersPerSecond,
				rotorAxisBody
		).clamp(-12.0, 12.0);
		double diskWindGradientSpeed = diskWindGradientInPlaneBody.length();
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		double diskWindGradientRatio = MathUtil.clamp(
				diskWindGradientSpeed / Math.max(1.0, tipSpeed * 0.12),
				0.0,
				1.0
		);
		Vec3 diskWindGradientTargetTiltBody = diskWindGradientTargetTiltBody(
				diskWindGradientInPlaneBody,
				diskWindGradientSpeed,
				diskWindGradientRatio,
				spinRatio,
				thrustFraction,
				thrustNewtons
		);
		Vec3 targetTiltBody = crossflowTargetTiltBody.add(diskWindGradientTargetTiltBody);
		Vec3 previousTiltBody = previousFlappingTiltBodyRadians;
		double responseTimeConstant = responseTimeConstantSeconds(
				targetTiltBody.length(),
				previousTiltBody.length(),
				spinRatio
		);
		double responseAlpha = steady ? 1.0 : MathUtil.expSmoothing(dtSeconds, responseTimeConstant);
		Vec3 unclampedTiltBody = previousTiltBody.add(
				targetTiltBody.subtract(previousTiltBody).multiply(responseAlpha)
		);
		Vec3 tiltBody = unclampedTiltBody;
		double tiltMagnitude = tiltBody.length();
		boolean tiltClamped = tiltMagnitude > MAX_FLAPPING_TILT_RADIANS;
		if (tiltClamped) {
			tiltBody = tiltBody.multiply(MAX_FLAPPING_TILT_RADIANS / tiltMagnitude);
			tiltMagnitude = MAX_FLAPPING_TILT_RADIANS;
		}

		Vec3 transverseFlappingForceBody = Vec3.ZERO;
		Vec3 axialForceCorrectionBody = Vec3.ZERO;
		Vec3 flappingForceBody = Vec3.ZERO;
		if (thrustNewtons > 1.0e-6 && tiltMagnitude > 1.0e-6) {
			transverseFlappingForceBody = tiltBody.multiply(thrustNewtons);
			double axialForceLoss = thrustNewtons
					* (1.0 - Math.sqrt(Math.max(0.0, 1.0 - tiltMagnitude * tiltMagnitude)));
			axialForceCorrectionBody = rotorAxisBody.multiply(-axialForceLoss);
			flappingForceBody = transverseFlappingForceBody.add(axialForceCorrectionBody);
		}
		Vec3 thrustAxisForceBody = rotorAxisBody.multiply(thrustNewtons).add(flappingForceBody);
		Vec3 effectiveDiskAxisBody = thrustAxisForceBody.lengthSquared() <= 1.0e-18
				? rotorAxisBody
				: thrustAxisForceBody.normalized();
		double effectiveDiskTiltAngle = Math.acos(MathUtil.clamp(
				rotorAxisBody.dot(effectiveDiskAxisBody),
				-1.0,
				1.0
		));
		Vec3 forceMomentBody = momentArmBodyMeters.cross(flappingForceBody);
		Vec3 tiltedReactionTorqueBody = tiltedReactionTorqueBody(
				nominalReactionTorqueBodyNewtonMeters,
				rotorAxisBody,
				effectiveDiskAxisBody
		);
		Vec3 reactionTorqueAxisCorrectionBody = tiltedReactionTorqueBody.subtract(
				nominalReactionTorqueBodyNewtonMeters
		);
		Vec3 totalTorqueCorrectionBody = forceMomentBody.add(reactionTorqueAxisCorrectionBody);
		double translationalPower = flappingForceBody.dot(relativeAirVelocityBodyMetersPerSecond);
		double thrustMagnitudeResidual = thrustAxisForceBody.length() - thrustNewtons;
		return new RotorFlappingForceSample(
				rotor,
				momentArmBodyMeters,
				rotorAxisBody,
				relativeAirVelocityBodyMetersPerSecond,
				transverseVelocityBody,
				diskWindGradientBodyMetersPerSecond,
				diskWindGradientInPlaneBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				spinRatio,
				tipSpeed,
				advanceRatio,
				thrustFraction,
				advanceResponse,
				diskLoadingResponse,
				diskWindGradientRatio,
				crossflowTargetTiltBody,
				diskWindGradientTargetTiltBody,
				targetTiltBody,
				previousTiltBody,
				responseTimeConstant,
				responseAlpha,
				unclampedTiltBody,
				tiltBody,
				tiltClamped,
				effectiveDiskTiltAngle,
				transverseFlappingForceBody,
				axialForceCorrectionBody,
				flappingForceBody,
				thrustAxisForceBody,
				effectiveDiskAxisBody,
				forceMomentBody,
				nominalReactionTorqueBodyNewtonMeters,
				tiltedReactionTorqueBody,
				reactionTorqueAxisCorrectionBody,
				totalTorqueCorrectionBody,
				translationalPower,
				thrustMagnitudeResidual
		);
	}

	public record RotorFlappingForceSample(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 rotorAxisBody,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			Vec3 diskWindGradientBodyMetersPerSecond,
			Vec3 diskWindGradientInPlaneBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double spinRatio,
			double rotorTipSpeedMetersPerSecond,
			double advanceRatio,
			double thrustFraction,
			double advanceRatioResponse,
			double diskLoadingResponse,
			double diskWindGradientRatio,
			Vec3 crossflowTargetTiltBodyRadians,
			Vec3 diskWindGradientTargetTiltBodyRadians,
			Vec3 targetTiltBodyRadians,
			Vec3 previousFlappingTiltBodyRadians,
			double responseTimeConstantSeconds,
			double responseAlpha,
			Vec3 unclampedFlappingTiltBodyRadians,
			Vec3 flappingTiltBodyRadians,
			boolean tiltClamped,
			double effectiveDiskTiltAngleRadians,
			Vec3 transverseFlappingForceBodyNewtons,
			Vec3 axialForceCorrectionBodyNewtons,
			Vec3 flappingForceBodyNewtons,
			Vec3 thrustAxisForceBodyNewtons,
			Vec3 effectiveDiskAxisBody,
			Vec3 forceMomentBodyNewtonMeters,
			Vec3 nominalReactionTorqueBodyNewtonMeters,
			Vec3 tiltedReactionTorqueBodyNewtonMeters,
			Vec3 reactionTorqueAxisCorrectionBodyNewtonMeters,
			Vec3 totalTorqueCorrectionBodyNewtonMeters,
			double translationalPowerWatts,
			double thrustMagnitudeResidualNewtons
	) {
		public double flappingTiltMagnitudeRadians() {
			return flappingTiltBodyRadians.length();
		}

		public Vec3 flappingForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(flappingForceBodyNewtons);
		}

		public Vec3 totalTorqueCorrectionWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalTorqueCorrectionBodyNewtonMeters);
		}
	}

	public record ConfigurationRotorFlappingForceSample(
			List<RotorFlappingForceSample> rotorSamples,
			Vec3 totalFlappingForceBodyNewtons,
			Vec3 totalForceMomentBodyNewtonMeters,
			Vec3 totalReactionTorqueAxisCorrectionBodyNewtonMeters,
			Vec3 totalTorqueCorrectionBodyNewtonMeters,
			double totalTranslationalPowerWatts,
			double maximumEffectiveDiskTiltAngleRadians,
			double maximumThrustMagnitudeResidualNewtons
	) {
		public ConfigurationRotorFlappingForceSample {
			rotorSamples = List.copyOf(rotorSamples == null ? List.of() : rotorSamples);
		}

		public int rotorCount() {
			return rotorSamples.size();
		}

		public Vec3 totalFlappingForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalFlappingForceBodyNewtons);
		}

		public Vec3 totalTorqueCorrectionWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalTorqueCorrectionBodyNewtonMeters);
		}
	}

	private static Vec3 crossflowTargetTiltBody(
			RotorSpec rotor,
			Vec3 transverseVelocityBody,
			double thrustNewtons,
			double advanceResponse,
			double diskLoadingResponse
	) {
		double transverseSpeed = transverseVelocityBody.length();
		if (transverseSpeed <= 1.0e-6
				|| thrustNewtons <= 1.0e-6
				|| rotor.flappingCoefficient() <= 0.0) {
			return Vec3.ZERO;
		}
		double tilt = rotor.flappingCoefficient() * advanceResponse * diskLoadingResponse;
		Vec3 transverseUnit = transverseVelocityBody.multiply(1.0 / transverseSpeed);
		return transverseUnit.multiply(-tilt);
	}

	private static Vec3 diskWindGradientTargetTiltBody(
			Vec3 diskWindGradientInPlaneBody,
			double diskWindGradientSpeed,
			double diskWindGradientRatio,
			double spinRatio,
			double thrustFraction,
			double thrustNewtons
	) {
		if (diskWindGradientSpeed <= 1.0e-6 || thrustNewtons <= 1.0e-6 || spinRatio <= 0.06) {
			return Vec3.ZERO;
		}
		double tilt = MAX_DISK_WIND_GRADIENT_TILT_RADIANS
				* smoothStep(0.03, 0.42, diskWindGradientRatio)
				* smoothStep(0.10, 0.55, spinRatio)
				* MathUtil.clamp(0.55 + 0.45 * Math.sqrt(thrustFraction), 0.0, 1.0);
		return diskWindGradientInPlaneBody.multiply(1.0 / diskWindGradientSpeed).multiply(tilt);
	}

	private static double responseTimeConstantSeconds(
			double targetTiltMagnitude,
			double previousTiltMagnitude,
			double spinRatio
	) {
		double timeConstant = targetTiltMagnitude > previousTiltMagnitude
				? BUILDUP_TIME_CONSTANT_SECONDS
				: RECOVERY_TIME_CONSTANT_SECONDS;
		return timeConstant * MathUtil.clamp(1.20 - 0.35 * spinRatio, 0.78, 1.20);
	}

	private static Vec3 tiltedReactionTorqueBody(
			Vec3 nominalReactionTorqueBody,
			Vec3 rotorAxisBody,
			Vec3 effectiveDiskAxisBody
	) {
		double signedAxialTorque = nominalReactionTorqueBody.dot(rotorAxisBody);
		Vec3 nonAxialTorque = nominalReactionTorqueBody.subtract(
				rotorAxisBody.multiply(signedAxialTorque)
		);
		return nonAxialTorque.add(effectiveDiskAxisBody.multiply(signedAxialTorque));
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9 || axis.y() <= 0.0) {
			return DEFAULT_ROTOR_AXIS_BODY;
		}
		return axis.normalized();
	}

	private static Vec3 projectOntoRotorDisk(Vec3 vector, Vec3 rotorAxisBody) {
		return vector.subtract(rotorAxisBody.multiply(vector.dot(rotorAxisBody)));
	}

	private static double rotorTipSpeedMetersPerSecond(RotorSpec rotor, double omegaRadiansPerSecond) {
		return Math.max(1.0, Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters());
	}

	private static void validateInputs(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 diskWindGradientBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			Vec3 previousFlappingTiltBodyRadians,
			Vec3 nominalReactionTorqueBodyNewtonMeters
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		validateFiniteVector(momentArmBodyMeters, "momentArmBodyMeters");
		validateFiniteVector(relativeAirVelocityBodyMetersPerSecond,
				"relativeAirVelocityBodyMetersPerSecond");
		validateFiniteVector(diskWindGradientBodyMetersPerSecond,
				"diskWindGradientBodyMetersPerSecond");
		validateFiniteVector(previousFlappingTiltBodyRadians, "previousFlappingTiltBodyRadians");
		validateFiniteVector(nominalReactionTorqueBodyNewtonMeters,
				"nominalReactionTorqueBodyNewtonMeters");
		if (!Double.isFinite(omegaRadiansPerSecond) || omegaRadiansPerSecond < 0.0) {
			throw new IllegalArgumentException("omegaRadiansPerSecond must be finite and nonnegative.");
		}
		if (!Double.isFinite(thrustNewtons) || thrustNewtons < 0.0) {
			throw new IllegalArgumentException("thrustNewtons must be finite and nonnegative.");
		}
	}

	private static void validateFiniteVector(Vec3 value, String name) {
		if (value == null || !value.isFinite()) {
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
