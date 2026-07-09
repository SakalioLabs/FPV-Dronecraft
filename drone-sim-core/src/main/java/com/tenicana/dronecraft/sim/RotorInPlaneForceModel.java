package com.tenicana.dronecraft.sim;

import java.util.List;

public final class RotorInPlaneForceModel {
	private static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	private static final double REFERENCE_DISK_DRAG_COEFFICIENT = 0.0028;
	private static final Vec3 DEFAULT_ROTOR_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);

	private RotorInPlaneForceModel() {
	}

	public static RotorInPlaneForceSample sample(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double translationalLiftIntensity,
			double bladeDissymmetryIntensity,
			double rotorStallIntensity
	) {
		validateInputs(
				rotor,
				momentArmBodyMeters,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				thrustNewtons,
				airDensityRatio,
				translationalLiftIntensity,
				bladeDissymmetryIntensity,
				rotorStallIntensity
		);
		Vec3 rotorAxisBody = rotorAxisBody(rotor);
		Vec3 transverseVelocityBody = transverseVelocityBody(
				relativeAirVelocityBodyMetersPerSecond,
				rotorAxisBody
		);
		double transverseSpeed = transverseVelocityBody.length();
		double advanceRatio = transverseSpeed / rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		Vec3 diskDragForceBody = diskDragForceBodyNewtons(
				rotor,
				transverseVelocityBody,
				omegaRadiansPerSecond,
				airDensityRatio
		);
		Vec3 loadedInPlaneDragForceBody = loadedInPlaneDragForceBodyNewtons(
				rotor,
				transverseVelocityBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				airDensityRatio,
				translationalLiftIntensity,
				bladeDissymmetryIntensity,
				rotorStallIntensity
		);
		Vec3 totalInPlaneForceBody = diskDragForceBody.add(loadedInPlaneDragForceBody);
		double additionalShaftTorque = additionalShaftTorqueNewtonMeters(
				rotor,
				transverseVelocityBody,
				loadedInPlaneDragForceBody,
				omegaRadiansPerSecond
		);
		double additionalShaftPower = additionalShaftTorque * Math.abs(omegaRadiansPerSecond);
		double loadFactor = loadFactor(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				translationalLiftIntensity,
				bladeDissymmetryIntensity,
				rotorStallIntensity
		);
		Vec3 forceMomentBody = momentArmBodyMeters.cross(totalInPlaneForceBody);
		Vec3 additionalReactionTorqueBody = rotorAxisBody.multiply(
				rotor.spinDirection() * additionalShaftTorque
		);
		Vec3 totalTorqueBody = forceMomentBody.add(additionalReactionTorqueBody);
		double translationalPowerDissipation = Math.max(
				0.0,
				-totalInPlaneForceBody.dot(relativeAirVelocityBodyMetersPerSecond)
		);
		return new RotorInPlaneForceSample(
				rotor,
				momentArmBodyMeters,
				rotorAxisBody,
				relativeAirVelocityBodyMetersPerSecond,
				transverseVelocityBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				airDensityRatio,
				advanceRatio,
				MathUtil.clamp(translationalLiftIntensity, 0.0, 1.0),
				MathUtil.clamp(bladeDissymmetryIntensity, 0.0, 1.0),
				MathUtil.clamp(rotorStallIntensity, 0.0, 1.0),
				diskDragForceBody,
				loadedInPlaneDragForceBody,
				totalInPlaneForceBody,
				loadFactor,
				additionalShaftTorque,
				additionalShaftPower,
				forceMomentBody,
				additionalReactionTorqueBody,
				totalTorqueBody,
				translationalPowerDissipation
		);
	}

	public static ConfigurationRotorInPlaneForceSample aggregate(
			List<RotorInPlaneForceSample> rotorSamples
	) {
		List<RotorInPlaneForceSample> requestedSamples = rotorSamples == null ? List.of() : rotorSamples;
		for (RotorInPlaneForceSample sample : requestedSamples) {
			if (sample == null) {
				throw new IllegalArgumentException("rotorSamples must not contain null entries.");
			}
		}
		List<RotorInPlaneForceSample> samples = List.copyOf(requestedSamples);
		Vec3 totalDiskDragForceBody = Vec3.ZERO;
		Vec3 totalLoadedInPlaneDragForceBody = Vec3.ZERO;
		Vec3 totalInPlaneForceBody = Vec3.ZERO;
		Vec3 totalForceMomentBody = Vec3.ZERO;
		Vec3 totalAdditionalReactionTorqueBody = Vec3.ZERO;
		Vec3 totalTorqueBody = Vec3.ZERO;
		double totalAdditionalShaftTorque = 0.0;
		double totalAdditionalShaftPower = 0.0;
		double totalTranslationalPowerDissipation = 0.0;
		for (RotorInPlaneForceSample sample : samples) {
			totalDiskDragForceBody = totalDiskDragForceBody.add(sample.diskDragForceBodyNewtons());
			totalLoadedInPlaneDragForceBody = totalLoadedInPlaneDragForceBody
					.add(sample.loadedInPlaneDragForceBodyNewtons());
			totalInPlaneForceBody = totalInPlaneForceBody.add(sample.totalInPlaneForceBodyNewtons());
			totalForceMomentBody = totalForceMomentBody.add(sample.forceMomentBodyNewtonMeters());
			totalAdditionalReactionTorqueBody = totalAdditionalReactionTorqueBody
					.add(sample.additionalReactionTorqueBodyNewtonMeters());
			totalTorqueBody = totalTorqueBody.add(sample.totalTorqueBodyNewtonMeters());
			totalAdditionalShaftTorque += sample.additionalShaftTorqueNewtonMeters();
			totalAdditionalShaftPower += sample.additionalShaftPowerWatts();
			totalTranslationalPowerDissipation += sample.translationalPowerDissipationWatts();
		}
		return new ConfigurationRotorInPlaneForceSample(
				samples,
				totalDiskDragForceBody,
				totalLoadedInPlaneDragForceBody,
				totalInPlaneForceBody,
				totalForceMomentBody,
				totalAdditionalReactionTorqueBody,
				totalTorqueBody,
				totalAdditionalShaftTorque,
				totalAdditionalShaftPower,
				totalTranslationalPowerDissipation
		);
	}

	public static double loadFactor(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double translationalLiftIntensity,
			double bladeDissymmetryIntensity,
			double rotorStallIntensity
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		if (!Double.isFinite(omegaRadiansPerSecond) || omegaRadiansPerSecond < 0.0) {
			throw new IllegalArgumentException("omegaRadiansPerSecond must be finite and nonnegative.");
		}
		validateIntensity(translationalLiftIntensity, "translationalLiftIntensity");
		validateIntensity(bladeDissymmetryIntensity, "bladeDissymmetryIntensity");
		validateIntensity(rotorStallIntensity, "rotorStallIntensity");

		double diskDragScale = MathUtil.clamp(
				rotor.diskDragCoefficient() / REFERENCE_DISK_DRAG_COEFFICIENT,
				0.0,
				3.5
		);
		if (diskDragScale <= 1.0e-6) {
			return 0.0;
		}
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.10
		);
		if (spinRatio <= 0.08) {
			return 0.0;
		}
		double advanceRatio = clampedAdvanceRatio(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond
		);
		double crossflow = smoothStep(0.04, 0.46, advanceRatio);
		double separatedLoading = 0.35 * MathUtil.clamp(translationalLiftIntensity, 0.0, 1.0)
				+ 0.45 * MathUtil.clamp(bladeDissymmetryIntensity, 0.0, 1.0)
				+ 0.55 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0);
		return MathUtil.clamp(
				0.10 * diskDragScale * crossflow * (0.35 + 0.65 * spinRatio) * (1.0 + separatedLoading),
				0.0,
				0.42
		);
	}

	public record RotorInPlaneForceSample(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 rotorAxisBody,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double advanceRatio,
			double translationalLiftIntensity,
			double bladeDissymmetryIntensity,
			double rotorStallIntensity,
			Vec3 diskDragForceBodyNewtons,
			Vec3 loadedInPlaneDragForceBodyNewtons,
			Vec3 totalInPlaneForceBodyNewtons,
			double inPlaneLoadFactor,
			double additionalShaftTorqueNewtonMeters,
			double additionalShaftPowerWatts,
			Vec3 forceMomentBodyNewtonMeters,
			Vec3 additionalReactionTorqueBodyNewtonMeters,
			Vec3 totalTorqueBodyNewtonMeters,
			double translationalPowerDissipationWatts
	) {
		public Vec3 totalInPlaneForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalInPlaneForceBodyNewtons);
		}

		public Vec3 totalTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalTorqueBodyNewtonMeters);
		}
	}

	public record ConfigurationRotorInPlaneForceSample(
			List<RotorInPlaneForceSample> rotorSamples,
			Vec3 totalDiskDragForceBodyNewtons,
			Vec3 totalLoadedInPlaneDragForceBodyNewtons,
			Vec3 totalInPlaneForceBodyNewtons,
			Vec3 totalForceMomentBodyNewtonMeters,
			Vec3 totalAdditionalReactionTorqueBodyNewtonMeters,
			Vec3 totalTorqueBodyNewtonMeters,
			double totalAdditionalShaftTorqueNewtonMeters,
			double totalAdditionalShaftPowerWatts,
			double totalTranslationalPowerDissipationWatts
	) {
		public ConfigurationRotorInPlaneForceSample {
			rotorSamples = List.copyOf(rotorSamples == null ? List.of() : rotorSamples);
		}

		public int rotorCount() {
			return rotorSamples.size();
		}

		public Vec3 totalInPlaneForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalInPlaneForceBodyNewtons);
		}

		public Vec3 totalTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalTorqueBodyNewtonMeters);
		}
	}

	private static Vec3 diskDragForceBodyNewtons(
			RotorSpec rotor,
			Vec3 transverseVelocityBody,
			double omegaRadiansPerSecond,
			double airDensityRatio
	) {
		double transverseSpeed = transverseVelocityBody.length();
		if (transverseSpeed <= 1.0e-6 || rotor.diskDragCoefficient() <= 0.0) {
			return Vec3.ZERO;
		}
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		double spinFactor = 0.15 + 0.85 * spinRatio;
		double dragScale = rotor.diskDragCoefficient() * airDensityRatio * spinFactor * transverseSpeed;
		return transverseVelocityBody.multiply(-dragScale);
	}

	private static Vec3 loadedInPlaneDragForceBodyNewtons(
			RotorSpec rotor,
			Vec3 transverseVelocityBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double translationalLiftIntensity,
			double bladeDissymmetryIntensity,
			double rotorStallIntensity
	) {
		double transverseSpeed = transverseVelocityBody.length();
		double diskDragScale = MathUtil.clamp(
				rotor.diskDragCoefficient() / REFERENCE_DISK_DRAG_COEFFICIENT,
				0.0,
				3.5
		);
		if (transverseSpeed <= 1.0e-6 || thrustNewtons <= 1.0e-6 || diskDragScale <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.10
		);
		if (spinRatio <= 0.08) {
			return Vec3.ZERO;
		}

		double advanceRatio = transverseSpeed / rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond);
		double activeDisk = smoothStep(0.10, 0.32, spinRatio);
		double crossflow = smoothStep(0.025, 0.35, advanceRatio);
		double loadedCrossflow = smoothStep(0.08, 0.55, advanceRatio);
		double hCoefficient = diskDragScale
				* activeDisk
				* crossflow
				* (0.030
						+ 0.105 * loadedCrossflow
						+ 0.035 * MathUtil.clamp(translationalLiftIntensity, 0.0, 1.0)
						+ 0.045 * MathUtil.clamp(bladeDissymmetryIntensity, 0.0, 1.0)
						+ 0.055 * MathUtil.clamp(rotorStallIntensity, 0.0, 1.0));
		double thrustCoupledForce = Math.max(0.0, thrustNewtons) * hCoefficient;

		double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.max(0.20, airDensityRatio);
		double dynamicPressure = 0.5 * density * transverseSpeed * transverseSpeed;
		double profileCoefficient = diskDragScale
				* activeDisk
				* (0.020 + 0.045 * loadedCrossflow)
				* smoothStep(0.04, 0.32, advanceRatio);
		double profileForce = dynamicPressure * diskArea * profileCoefficient;
		double forceMagnitude = MathUtil.clamp(
				thrustCoupledForce + profileForce,
				0.0,
				rotor.maxThrustNewtons() * 0.42
		);
		return transverseVelocityBody.multiply(-forceMagnitude / transverseSpeed);
	}

	private static double additionalShaftTorqueNewtonMeters(
			RotorSpec rotor,
			Vec3 transverseVelocityBody,
			Vec3 loadedInPlaneDragForceBody,
			double omegaRadiansPerSecond
	) {
		double forceMagnitude = loadedInPlaneDragForceBody.length();
		double shaftSpeed = Math.abs(omegaRadiansPerSecond);
		if (forceMagnitude <= 1.0e-9 || shaftSpeed <= 1.0e-6) {
			return 0.0;
		}
		double transverseSpeed = transverseVelocityBody.length();
		if (transverseSpeed <= 1.0e-6) {
			return 0.0;
		}

		double advanceRatio = MathUtil.clamp(
				transverseSpeed / rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond),
				0.0,
				2.0
		);
		double profilePowerWatts = forceMagnitude
				* transverseSpeed
				* MathUtil.clamp(
						0.48 + 0.34 * smoothStep(0.05, 0.55, advanceRatio),
						0.48,
						0.82
				);
		double torqueLimit = Math.max(
				rotor.maxThrustNewtons() * Math.abs(rotor.yawTorquePerThrustMeter()) * 0.90,
				rotor.maxThrustNewtons() * rotor.radiusMeters() * 0.075
		);
		return MathUtil.clamp(profilePowerWatts / shaftSpeed, 0.0, torqueLimit);
	}

	private static double clampedAdvanceRatio(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond
	) {
		double transverseSpeed = transverseVelocityBody(
				relativeAirVelocityBodyMetersPerSecond,
				rotorAxisBody(rotor)
		).length();
		return MathUtil.clamp(
				transverseSpeed / rotorTipSpeedMetersPerSecond(rotor, omegaRadiansPerSecond),
				0.0,
				2.0
		);
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

	private static void validateInputs(
			RotorSpec rotor,
			Vec3 momentArmBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double translationalLiftIntensity,
			double bladeDissymmetryIntensity,
			double rotorStallIntensity
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (momentArmBodyMeters == null || !momentArmBodyMeters.isFinite()) {
			throw new IllegalArgumentException("momentArmBodyMeters must be finite.");
		}
		if (relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		if (!Double.isFinite(omegaRadiansPerSecond) || omegaRadiansPerSecond < 0.0) {
			throw new IllegalArgumentException("omegaRadiansPerSecond must be finite and nonnegative.");
		}
		if (!Double.isFinite(thrustNewtons) || thrustNewtons < 0.0) {
			throw new IllegalArgumentException("thrustNewtons must be finite and nonnegative.");
		}
		if (!Double.isFinite(airDensityRatio) || airDensityRatio < 0.0) {
			throw new IllegalArgumentException("airDensityRatio must be finite and nonnegative.");
		}
		validateIntensity(translationalLiftIntensity, "translationalLiftIntensity");
		validateIntensity(bladeDissymmetryIntensity, "bladeDissymmetryIntensity");
		validateIntensity(rotorStallIntensity, "rotorStallIntensity");
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
