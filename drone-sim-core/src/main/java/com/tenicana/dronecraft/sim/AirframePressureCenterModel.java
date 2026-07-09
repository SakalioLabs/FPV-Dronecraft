package com.tenicana.dronecraft.sim;

public final class AirframePressureCenterModel {
	private static final double MAX_DYNAMIC_OFFSET_METERS = 0.040;
	private static final double MAX_PRESSURE_CENTER_TORQUE_NEWTON_METERS = 0.45;

	private AirframePressureCenterModel() {
	}

	public static AirframePressureCenterSample sampleSteady(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 aerodynamicForceBodyNewtons,
			double effectiveSeparatedFlowIntensity
	) {
		validateConfigAndForce(config, aerodynamicForceBodyNewtons);
		if (relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		if (!Double.isFinite(effectiveSeparatedFlowIntensity)) {
			throw new IllegalArgumentException("effectiveSeparatedFlowIntensity must be finite.");
		}
		return sampleAtDynamicPressureCenter(
				config,
				aerodynamicForceBodyNewtons,
				steadyDynamicPressureCenterOffsetBodyMeters(
						relativeAirVelocityBodyMetersPerSecond,
						effectiveSeparatedFlowIntensity
				)
		);
	}

	public static AirframePressureCenterSample sampleAtDynamicPressureCenter(
			DroneConfig config,
			Vec3 aerodynamicForceBodyNewtons,
			Vec3 dynamicPressureCenterOffsetBodyMeters
	) {
		validateConfigAndForce(config, aerodynamicForceBodyNewtons);
		if (dynamicPressureCenterOffsetBodyMeters == null
				|| !dynamicPressureCenterOffsetBodyMeters.isFinite()) {
			throw new IllegalArgumentException("dynamicPressureCenterOffsetBodyMeters must be finite.");
		}

		Vec3 dynamicOffset = dynamicPressureCenterOffsetBodyMeters.clamp(
				-MAX_DYNAMIC_OFFSET_METERS,
				MAX_DYNAMIC_OFFSET_METERS
		);
		Vec3 applicationPoint = config.centerOfPressureOffsetBodyMeters().add(dynamicOffset);
		Vec3 momentArm = applicationPoint.subtract(config.centerOfMassOffsetBodyMeters());
		Vec3 torque = momentArm.lengthSquared() <= 1.0e-12
				|| aerodynamicForceBodyNewtons.lengthSquared() <= 1.0e-12
				? Vec3.ZERO
				: momentArm.cross(aerodynamicForceBodyNewtons).clamp(
						-MAX_PRESSURE_CENTER_TORQUE_NEWTON_METERS,
						MAX_PRESSURE_CENTER_TORQUE_NEWTON_METERS
				);
		return new AirframePressureCenterSample(
				aerodynamicForceBodyNewtons,
				config.centerOfMassOffsetBodyMeters(),
				config.centerOfPressureOffsetBodyMeters(),
				dynamicOffset,
				applicationPoint,
				momentArm,
				torque
		);
	}

	public static Vec3 steadyDynamicPressureCenterOffsetBodyMeters(
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double effectiveSeparatedFlowIntensity
	) {
		if (relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		if (!Double.isFinite(effectiveSeparatedFlowIntensity)) {
			throw new IllegalArgumentException("effectiveSeparatedFlowIntensity must be finite.");
		}

		double speed = relativeAirVelocityBodyMetersPerSecond.length();
		if (speed < 2.0) {
			return Vec3.ZERO;
		}
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBodyMetersPerSecond.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBodyMetersPerSecond.y(), forwardReference);
		double sideslip = Math.atan2(relativeAirVelocityBodyMetersPerSecond.x(), forwardReference);
		double angleIntensity = MathUtil.clamp(
				Math.abs(angleOfAttack) / Math.toRadians(55.0)
						+ 0.80 * Math.abs(sideslip) / Math.toRadians(60.0),
				0.0,
				1.0
		);
		if (angleIntensity <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double speedScale = smoothStep(3.0, 18.0, speed);
		double separationBias = 0.35
				+ 0.65 * MathUtil.clamp(effectiveSeparatedFlowIntensity, 0.0, 1.0);
		double migrationScale = speedScale * separationBias * angleIntensity;
		if (migrationScale <= 1.0e-6) {
			return Vec3.ZERO;
		}

		double lateralShift = -0.018 * Math.sin(sideslip) * migrationScale;
		double verticalShift = 0.016 * Math.sin(angleOfAttack) * migrationScale;
		double forwardFlowFraction = MathUtil.clamp(
				Math.abs(relativeAirVelocityBodyMetersPerSecond.z()) / speed,
				0.0,
				1.0
		);
		double aftShift = -0.026
				* (Math.abs(Math.sin(angleOfAttack)) + 0.70 * Math.abs(Math.sin(sideslip)))
				* migrationScale
				* forwardFlowFraction;
		return new Vec3(lateralShift, verticalShift, aftShift).clamp(
				-MAX_DYNAMIC_OFFSET_METERS,
				MAX_DYNAMIC_OFFSET_METERS
		);
	}

	public record AirframePressureCenterSample(
			Vec3 aerodynamicForceBodyNewtons,
			Vec3 centerOfMassOffsetBodyMeters,
			Vec3 staticCenterOfPressureOffsetBodyMeters,
			Vec3 dynamicPressureCenterOffsetBodyMeters,
			Vec3 forceApplicationPointBodyMeters,
			Vec3 momentArmBodyMeters,
			Vec3 pressureCenterTorqueBodyNewtonMeters
	) {
		public Vec3 aerodynamicForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(aerodynamicForceBodyNewtons);
		}

		public Vec3 pressureCenterTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(pressureCenterTorqueBodyNewtonMeters);
		}

		public double rotationalPowerWatts(Vec3 angularVelocityBodyRadiansPerSecond) {
			if (angularVelocityBodyRadiansPerSecond == null
					|| !angularVelocityBodyRadiansPerSecond.isFinite()) {
				throw new IllegalArgumentException("angularVelocityBodyRadiansPerSecond must be finite.");
			}
			return pressureCenterTorqueBodyNewtonMeters.dot(angularVelocityBodyRadiansPerSecond);
		}
	}

	private static void validateConfigAndForce(DroneConfig config, Vec3 aerodynamicForceBodyNewtons) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (aerodynamicForceBodyNewtons == null || !aerodynamicForceBodyNewtons.isFinite()) {
			throw new IllegalArgumentException("aerodynamicForceBodyNewtons must be finite.");
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
