package com.tenicana.dronecraft.sim;

public final class AirframeAngularDampingModel {
	private AirframeAngularDampingModel() {
	}

	public static AirframeAngularDampingSample samplePassive(
			DroneConfig config,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double effectiveSeparatedFlowIntensity
	) {
		return sampleSteady(
				config,
				angularVelocityBodyRadiansPerSecond,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				effectiveSeparatedFlowIntensity,
				Vec3.ZERO
		);
	}

	public static AirframeAngularDampingSample sampleSteady(
			DroneConfig config,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double effectiveSeparatedFlowIntensity,
			Vec3 rotorWashDampingCoefficients
	) {
		validateInputs(
				config,
				angularVelocityBodyRadiansPerSecond,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				effectiveSeparatedFlowIntensity,
				rotorWashDampingCoefficients
		);
		double separation = MathUtil.clamp(effectiveSeparatedFlowIntensity, 0.0, 1.0);
		double airspeed = relativeAirVelocityBodyMetersPerSecond.length();
		double dynamicScale = Math.max(0.0, airDensityRatio) * airspeed * airspeed;
		Vec3 drag = config.bodyDragCoefficients();
		Vec3 freestreamDynamicDamping = new Vec3(
				MathUtil.clamp(dynamicScale * (0.00022 * drag.z() + 0.00006 * drag.y()), 0.0, 0.36),
				MathUtil.clamp(dynamicScale * (0.00018 * drag.x() + 0.00008 * drag.z()), 0.0, 0.36),
				MathUtil.clamp(dynamicScale * (0.00020 * drag.x() + 0.00006 * drag.y()), 0.0, 0.36)
		);
		double frameRadius = equivalentFrameRadiusMeters(config);
		Vec3 rotationalDamping = rotationalDampingCoefficients(
				config,
				angularVelocityBodyRadiansPerSecond,
				airDensityRatio,
				frameRadius
		);
		Vec3 separatedFlowDamping = separatedFlowDampingCoefficients(
				config,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				separation
		);
		double weathercockYawDamping = sideslipWeathercockYawDampingCoefficient(
				config,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio
		);
		Vec3 totalDampingCoefficients = new Vec3(
				config.angularDragCoefficient()
						+ freestreamDynamicDamping.x()
						+ rotationalDamping.x()
						+ separatedFlowDamping.x()
						+ rotorWashDampingCoefficients.x(),
				config.angularDragCoefficient()
						+ freestreamDynamicDamping.y()
						+ weathercockYawDamping
						+ rotationalDamping.y()
						+ separatedFlowDamping.y()
						+ rotorWashDampingCoefficients.y(),
				config.angularDragCoefficient()
						+ freestreamDynamicDamping.z()
						+ rotationalDamping.z()
						+ separatedFlowDamping.z()
						+ rotorWashDampingCoefficients.z()
		);
		Vec3 rawDampingTorque = new Vec3(
				-angularVelocityBodyRadiansPerSecond.x() * totalDampingCoefficients.x(),
				-angularVelocityBodyRadiansPerSecond.y() * totalDampingCoefficients.y(),
				-angularVelocityBodyRadiansPerSecond.z() * totalDampingCoefficients.z()
		);
		GuardedTorque guardedTorque = applyNeuroBemGuard(
				config,
				rawDampingTorque,
				angularVelocityBodyRadiansPerSecond,
				airspeed,
				airDensityRatio
		);
		double powerDissipation = Math.max(
				0.0,
				-guardedTorque.torqueBodyNewtonMeters().dot(angularVelocityBodyRadiansPerSecond)
		);
		return new AirframeAngularDampingSample(
				angularVelocityBodyRadiansPerSecond,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				separation,
				airspeed,
				frameRadius,
				freestreamDynamicDamping,
				weathercockYawDamping,
				rotationalDamping,
				separatedFlowDamping,
				rotorWashDampingCoefficients,
				totalDampingCoefficients,
				rawDampingTorque,
				guardedTorque.coverage(),
				guardedTorque.axisLimitNewtonMeters(),
				guardedTorque.torqueBodyNewtonMeters(),
				powerDissipation
		);
	}

	public record AirframeAngularDampingSample(
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double effectiveSeparatedFlowIntensity,
			double airspeedMetersPerSecond,
			double equivalentFrameRadiusMeters,
			Vec3 freestreamDynamicDampingCoefficients,
			double weathercockYawDampingCoefficient,
			Vec3 rotationalDampingCoefficients,
			Vec3 separatedFlowDampingCoefficients,
			Vec3 rotorWashDampingCoefficients,
			Vec3 totalDampingCoefficients,
			Vec3 rawDampingTorqueBodyNewtonMeters,
			double neuroBemGuardCoverage,
			Vec3 neuroBemGuardAxisLimitNewtonMeters,
			Vec3 dampingTorqueBodyNewtonMeters,
			double powerDissipationWatts
	) {
		public Vec3 dampingTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(dampingTorqueBodyNewtonMeters);
		}
	}

	private static Vec3 rotationalDampingCoefficients(
			DroneConfig config,
			Vec3 angularVelocityBody,
			double airDensityRatio,
			double frameRadiusMeters
	) {
		if (airDensityRatio <= 0.0 || angularVelocityBody.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}
		Vec3 drag = config.bodyDragCoefficients();
		if (Math.max(drag.x(), Math.max(drag.y(), drag.z())) <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double pitchLocalSpeed = Math.abs(angularVelocityBody.x()) * frameRadiusMeters;
		double yawLocalSpeed = Math.abs(angularVelocityBody.y()) * frameRadiusMeters * 0.68;
		double rollLocalSpeed = Math.abs(angularVelocityBody.z()) * frameRadiusMeters;
		return new Vec3(
				MathUtil.clamp(airDensityRatio * pitchLocalSpeed * (0.020 * drag.z() + 0.006 * drag.y()), 0.0, 0.08),
				MathUtil.clamp(airDensityRatio * yawLocalSpeed * (0.014 * Math.sqrt(Math.max(0.0, drag.x() * drag.z())) + 0.004 * drag.y()), 0.0, 0.06),
				MathUtil.clamp(airDensityRatio * rollLocalSpeed * (0.020 * drag.x() + 0.006 * drag.y()), 0.0, 0.08)
		);
	}

	private static Vec3 separatedFlowDampingCoefficients(
			DroneConfig config,
			Vec3 relativeAirVelocityBody,
			double airDensityRatio,
			double separation
	) {
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared <= 1.0e-6 || airDensityRatio <= 0.0 || separation <= 1.0e-6) {
			return Vec3.ZERO;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBody.y(), forwardReference);
		double sideslip = Math.atan2(relativeAirVelocityBody.x(), forwardReference);
		double pitchExposure = 0.35 + 0.65 * smoothStep(
				Math.toRadians(24.0),
				Math.toRadians(72.0),
				Math.abs(angleOfAttack)
		);
		double yawExposure = 0.35 + 0.65 * smoothStep(
				Math.toRadians(26.0),
				Math.toRadians(74.0),
				Math.abs(sideslip)
		);
		double rollExposure = 0.45 + 0.55 * smoothStep(
				Math.toRadians(32.0),
				Math.toRadians(82.0),
				Math.abs(angleOfAttack) + 0.80 * Math.abs(sideslip)
		);
		double separatedDynamicScale = Math.max(0.0, airDensityRatio) * speedSquared * separation;
		return new Vec3(
				MathUtil.clamp(separatedDynamicScale * pitchExposure * (0.00012 * drag.z() + 0.00005 * drag.y()), 0.0, 0.18),
				MathUtil.clamp(separatedDynamicScale * yawExposure * (0.00012 * drag.x() + 0.00005 * drag.z()), 0.0, 0.18),
				MathUtil.clamp(separatedDynamicScale * rollExposure * (0.00010 * drag.x() + 0.00005 * drag.y()), 0.0, 0.16)
		);
	}

	private static double sideslipWeathercockYawDampingCoefficient(
			DroneConfig config,
			Vec3 relativeAirVelocityBody,
			double airDensityRatio
	) {
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared <= 1.0e-6 || airDensityRatio <= 0.0) {
			return 0.0;
		}

		double lateralSpeed = Math.abs(relativeAirVelocityBody.x());
		double forwardSpeed = Math.abs(relativeAirVelocityBody.z());
		if (lateralSpeed <= 1.0e-6 || forwardSpeed <= 1.0e-6) {
			return 0.0;
		}

		Vec3 drag = config.bodyDragCoefficients();
		double lateralArea = Math.max(0.0, drag.x());
		double frontalArea = Math.max(0.0, drag.z());
		if (lateralArea <= 1.0e-9 || frontalArea <= 1.0e-9) {
			return 0.0;
		}

		double sideslip = Math.atan2(relativeAirVelocityBody.x(), Math.max(2.0, forwardSpeed));
		double sideslipExposure = smoothStep(
				Math.toRadians(7.0),
				Math.toRadians(48.0),
				Math.abs(sideslip)
		);
		double forwardExposure = smoothStep(2.5, 16.0, forwardSpeed);
		double lateralExposure = smoothStep(1.5, 12.0, lateralSpeed);
		double dynamicScale = Math.max(0.0, airDensityRatio) * speedSquared;
		double weathercockArea = Math.sqrt(lateralArea * frontalArea);
		return MathUtil.clamp(
				dynamicScale
						* weathercockArea
						* sideslipExposure
						* (0.45 + 0.35 * forwardExposure + 0.20 * lateralExposure)
						* 0.00016,
				0.0,
				0.22
		);
	}

	private static GuardedTorque applyNeuroBemGuard(
			DroneConfig config,
			Vec3 rawDampingTorque,
			Vec3 angularVelocityBody,
			double airspeedMetersPerSecond,
			double airDensityRatio
	) {
		if (airDensityRatio <= 0.0
				|| rawDampingTorque.lengthSquared() <= 1.0e-12
				|| angularVelocityBody.lengthSquared() <= 1.0e-12) {
			return new GuardedTorque(rawDampingTorque, 0.0, Vec3.ZERO);
		}

		double speedCoverage = smoothStep(
				NeuroBemAirframeResidualCalibration.BODY_SPEED_SAMPLE_P50_METERS_PER_SECOND,
				NeuroBemAirframeResidualCalibration.BODY_SPEED_SAMPLE_P95_METERS_PER_SECOND,
				airspeedMetersPerSecond
		);
		double rateCoverage = smoothStep(
				NeuroBemAirframeResidualCalibration.ANGULAR_SPEED_SAMPLE_P50_RADIANS_PER_SECOND,
				NeuroBemAirframeResidualCalibration.ANGULAR_SPEED_SAMPLE_P95_RADIANS_PER_SECOND,
				angularVelocityBody.length()
		);
		double guardCoverage = speedCoverage * rateCoverage;
		if (guardCoverage <= 1.0e-6) {
			return new GuardedTorque(rawDampingTorque, guardCoverage, Vec3.ZERO);
		}

		Vec3 torqueLimit = NeuroBemAirframeResidualCalibration
				.runtimeResidualTorqueP95AxisLimitNewtonMeters(config);
		Vec3 guardedTorque = new Vec3(
				MathUtil.clamp(rawDampingTorque.x(), -torqueLimit.x(), torqueLimit.x()),
				MathUtil.clamp(rawDampingTorque.y(), -torqueLimit.y(), torqueLimit.y()),
				MathUtil.clamp(rawDampingTorque.z(), -torqueLimit.z(), torqueLimit.z())
		);
		Vec3 torque = rawDampingTorque.add(
				guardedTorque.subtract(rawDampingTorque).multiply(guardCoverage)
		);
		return new GuardedTorque(torque, guardCoverage, torqueLimit);
	}

	private static double equivalentFrameRadiusMeters(DroneConfig config) {
		double rotorArmSum = 0.0;
		int rotorCount = 0;
		Vec3 centerOfMass = config.centerOfMassOffsetBodyMeters();
		for (RotorSpec rotor : config.rotors()) {
			rotorArmSum += rotor.positionBodyMeters().subtract(centerOfMass).length();
			rotorCount++;
		}
		double averageRotorArm = rotorCount == 0 ? 0.12 : rotorArmSum / rotorCount;
		double boardOffset = config.imuOffsetBodyMeters().length();
		double frameExposureRadius = averageRotorArm * 0.75;
		return MathUtil.clamp(Math.max(boardOffset, frameExposureRadius), 0.06, 0.28);
	}

	private static void validateInputs(
			DroneConfig config,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double effectiveSeparatedFlowIntensity,
			Vec3 rotorWashDampingCoefficients
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (angularVelocityBodyRadiansPerSecond == null
				|| !angularVelocityBodyRadiansPerSecond.isFinite()) {
			throw new IllegalArgumentException("angularVelocityBodyRadiansPerSecond must be finite.");
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
		if (rotorWashDampingCoefficients == null || !rotorWashDampingCoefficients.isFinite()
				|| rotorWashDampingCoefficients.x() < 0.0
				|| rotorWashDampingCoefficients.y() < 0.0
				|| rotorWashDampingCoefficients.z() < 0.0) {
			throw new IllegalArgumentException("rotorWashDampingCoefficients must be finite and nonnegative.");
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

	private record GuardedTorque(
			Vec3 torqueBodyNewtonMeters,
			double coverage,
			Vec3 axisLimitNewtonMeters
	) {
	}
}
