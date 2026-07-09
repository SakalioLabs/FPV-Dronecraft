package com.tenicana.dronecraft.sim;

public final class AirframeLiftForceModel {
	private static final double FORCE_COMPONENT_LIMIT_NEWTONS = 18.0;

	private AirframeLiftForceModel() {
	}

	public static AirframeLiftForceSample sampleSteady(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double effectiveSeparatedFlowIntensity
	) {
		validateInputs(
				config,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				effectiveSeparatedFlowIntensity
		);
		Vec3 dragCoefficients = config.bodyDragCoefficients();
		double separatedFlow = MathUtil.clamp(effectiveSeparatedFlowIntensity, 0.0, 1.0);
		double speedSquared = relativeAirVelocityBodyMetersPerSecond.lengthSquared();
		double pitchLiftCoefficient = 0.085 * Math.sqrt(
				Math.max(0.0, dragCoefficients.y() * dragCoefficients.z())
		);
		double sideForceCoefficient = 0.065 * Math.sqrt(
				Math.max(0.0, dragCoefficients.x() * dragCoefficients.z())
		);
		if (speedSquared < 1.0e-6 || airDensityRatio <= 0.0) {
			return new AirframeLiftForceSample(
					relativeAirVelocityBodyMetersPerSecond,
					airDensityRatio,
					separatedFlow,
					speedSquared,
					0.0,
					0.0,
					0.0,
					0.0,
					pitchLiftCoefficient,
					sideForceCoefficient,
					0.0,
					0.0,
					1.0,
					1.0,
					0.0,
					0.0,
					Vec3.ZERO,
					Vec3.ZERO,
					Vec3.ZERO,
					Vec3.ZERO
			);
		}

		double pitchPlaneSpeed = Math.hypot(
				relativeAirVelocityBodyMetersPerSecond.y(),
				relativeAirVelocityBodyMetersPerSecond.z()
		);
		double angleOfAttack = 0.0;
		double pitchStallIntensity = 0.0;
		double pitchStallScale = 1.0;
		double pitchLiftMagnitude = 0.0;
		Vec3 pitchLiftForceBody = Vec3.ZERO;
		if (pitchPlaneSpeed > 1.0e-6) {
			angleOfAttack = Math.atan2(
					relativeAirVelocityBodyMetersPerSecond.y(),
					relativeAirVelocityBodyMetersPerSecond.z()
			);
			pitchStallIntensity = smoothStep(
					Math.toRadians(34.0),
					Math.toRadians(72.0),
					Math.abs(angleOfAttack)
			);
			double dynamicPitchStall = Math.max(
					0.32 * pitchStallIntensity,
					separatedFlow * pitchStallIntensity
			);
			pitchStallScale = 1.0 - 0.55 * dynamicPitchStall;
			pitchLiftMagnitude = pitchLiftCoefficient
					* speedSquared
					* Math.sin(2.0 * angleOfAttack)
					* pitchStallScale
					* airDensityRatio;
			Vec3 liftDirection = new Vec3(
					0.0,
					relativeAirVelocityBodyMetersPerSecond.z(),
					-relativeAirVelocityBodyMetersPerSecond.y()
			).normalized();
			pitchLiftForceBody = liftDirection.multiply(pitchLiftMagnitude);
		}

		double yawPlaneSpeed = Math.hypot(
				relativeAirVelocityBodyMetersPerSecond.x(),
				relativeAirVelocityBodyMetersPerSecond.z()
		);
		double sideslip = 0.0;
		double yawStallIntensity = 0.0;
		double yawStallScale = 1.0;
		double sideForceMagnitude = 0.0;
		Vec3 sideLiftForceBody = Vec3.ZERO;
		if (yawPlaneSpeed > 1.0e-6) {
			sideslip = Math.atan2(
					relativeAirVelocityBodyMetersPerSecond.x(),
					relativeAirVelocityBodyMetersPerSecond.z()
			);
			yawStallIntensity = smoothStep(
					Math.toRadians(35.0),
					Math.toRadians(75.0),
					Math.abs(sideslip)
			);
			double dynamicYawStall = Math.max(
					0.32 * yawStallIntensity,
					separatedFlow * yawStallIntensity
			);
			yawStallScale = 1.0 - 0.50 * dynamicYawStall;
			sideForceMagnitude = sideForceCoefficient
					* speedSquared
					* Math.sin(2.0 * sideslip)
					* yawStallScale
					* airDensityRatio;
			Vec3 sideForceDirection = new Vec3(
					-relativeAirVelocityBodyMetersPerSecond.z(),
					0.0,
					relativeAirVelocityBodyMetersPerSecond.x()
			).normalized();
			sideLiftForceBody = sideForceDirection.multiply(sideForceMagnitude);
		}

		Vec3 unclampedTotalLiftForceBody = pitchLiftForceBody.add(sideLiftForceBody);
		Vec3 totalLiftForceBody = unclampedTotalLiftForceBody.clamp(
				-FORCE_COMPONENT_LIMIT_NEWTONS,
				FORCE_COMPONENT_LIMIT_NEWTONS
		);
		return new AirframeLiftForceSample(
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				separatedFlow,
				speedSquared,
				pitchPlaneSpeed,
				yawPlaneSpeed,
				angleOfAttack,
				sideslip,
				pitchLiftCoefficient,
				sideForceCoefficient,
				pitchStallIntensity,
				yawStallIntensity,
				pitchStallScale,
				yawStallScale,
				pitchLiftMagnitude,
				sideForceMagnitude,
				pitchLiftForceBody,
				sideLiftForceBody,
				unclampedTotalLiftForceBody,
				totalLiftForceBody
		);
	}

	public static AirframeLiftForceSample sampleSettled(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio
	) {
		AirframeDragForceModel.AirframeDragForceSample dragSample = AirframeDragForceModel.sampleSettled(
				config,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio
		);
		return sampleSteady(
				config,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				dragSample.effectiveSeparationIntensity()
		);
	}

	public record AirframeLiftForceSample(
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double effectiveSeparatedFlowIntensity,
			double referenceSpeedSquaredMetersSquaredPerSecondSquared,
			double pitchPlaneSpeedMetersPerSecond,
			double yawPlaneSpeedMetersPerSecond,
			double angleOfAttackRadians,
			double sideslipRadians,
			double pitchLiftCoefficient,
			double sideForceCoefficient,
			double pitchStallIntensity,
			double yawStallIntensity,
			double pitchStallScale,
			double yawStallScale,
			double pitchLiftSignedMagnitudeNewtons,
			double sideForceSignedMagnitudeNewtons,
			Vec3 pitchLiftForceBodyNewtons,
			Vec3 sideLiftForceBodyNewtons,
			Vec3 unclampedTotalLiftForceBodyNewtons,
			Vec3 totalLiftForceBodyNewtons
	) {
		public Vec3 totalLiftForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalLiftForceBodyNewtons);
		}

		public double pitchLiftPowerWatts() {
			return pitchLiftForceBodyNewtons.dot(relativeAirVelocityBodyMetersPerSecond);
		}

		public double sideForcePowerWatts() {
			return sideLiftForceBodyNewtons.dot(relativeAirVelocityBodyMetersPerSecond);
		}

		public double orthogonalityPowerResidualWatts() {
			return Math.abs(unclampedTotalLiftForceBodyNewtons.dot(
					relativeAirVelocityBodyMetersPerSecond
			));
		}
	}

	private static void validateInputs(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double effectiveSeparatedFlowIntensity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		if (!Double.isFinite(airDensityRatio) || airDensityRatio < 0.0) {
			throw new IllegalArgumentException("airDensityRatio must be finite and nonnegative.");
		}
		if (!Double.isFinite(effectiveSeparatedFlowIntensity)) {
			throw new IllegalArgumentException("effectiveSeparatedFlowIntensity must be finite.");
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
