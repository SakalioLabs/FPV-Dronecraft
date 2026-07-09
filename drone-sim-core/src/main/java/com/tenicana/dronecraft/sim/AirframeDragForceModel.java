package com.tenicana.dronecraft.sim;

public final class AirframeDragForceModel {
	private static final double IMMEDIATE_SEPARATION_FRACTION = 0.32;
	private static final double SEPARATED_FLOW_FORCE_LIMIT_NEWTONS = 38.0;

	private AirframeDragForceModel() {
	}

	public static AirframeDragForceSample sampleSteady(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double separatedFlowStateIntensity
	) {
		validateInputs(config, relativeAirVelocityBodyMetersPerSecond, airDensityRatio,
				separatedFlowStateIntensity);
		Vec3 dragCoefficients = config.bodyDragCoefficients();
		double targetSeparation = targetSeparationIntensity(
				relativeAirVelocityBodyMetersPerSecond,
				dragCoefficients
		);
		double effectiveSeparation = effectiveSeparationIntensity(
				targetSeparation,
				separatedFlowStateIntensity
		);
		Vec3 unscaledBaseQuadraticDrag = new Vec3(
				-dragCoefficients.x() * MathUtil.squareSigned(relativeAirVelocityBodyMetersPerSecond.x()),
				-dragCoefficients.y() * MathUtil.squareSigned(relativeAirVelocityBodyMetersPerSecond.y()),
				-dragCoefficients.z() * MathUtil.squareSigned(relativeAirVelocityBodyMetersPerSecond.z())
		);
		Vec3 unscaledSeparatedFlowDrag = separatedFlowDragForceBodyNewtons(
				relativeAirVelocityBodyMetersPerSecond,
				dragCoefficients,
				effectiveSeparation
		);
		Vec3 baseQuadraticDrag = unscaledBaseQuadraticDrag.multiply(airDensityRatio);
		Vec3 separatedFlowDrag = unscaledSeparatedFlowDrag.multiply(airDensityRatio);
		Vec3 bodyDrag = unscaledBaseQuadraticDrag.add(unscaledSeparatedFlowDrag).multiply(airDensityRatio);
		Vec3 linearDampingDrag = linearDampingDragForce(
				config,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio
		);
		Vec3 totalDrag = bodyDrag.add(linearDampingDrag);
		double speed = relativeAirVelocityBodyMetersPerSecond.length();
		double alongFlowDrag = speed <= 1.0e-6
				? 0.0
				: Math.max(0.0, totalDrag.dot(
						relativeAirVelocityBodyMetersPerSecond.multiply(-1.0 / speed)
				));
		double equivalentLinearCoefficient = AirframeDragCalibration.equivalentLinearCoefficient(
				alongFlowDrag,
				speed
		);
		double equivalentCdA = AirframeDragCalibration.equivalentCdAMetersSquared(
				alongFlowDrag,
				speed,
				airDensityRatio
		);
		double imavReferenceForce = AirframeDragCalibration.imav2022ReferenceDragForceNewtons(
				config,
				speed,
				airDensityRatio
		);
		double imavReferenceRatio = imavReferenceForce > 1.0e-9
				? alongFlowDrag / imavReferenceForce
				: 0.0;
		return new AirframeDragForceSample(
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				separatedFlowStateIntensity,
				targetSeparation,
				effectiveSeparation,
				baseQuadraticDrag,
				separatedFlowDrag,
				bodyDrag,
				linearDampingDrag,
				totalDrag,
				alongFlowDrag,
				equivalentLinearCoefficient,
				equivalentCdA,
				imavReferenceRatio
		);
	}

	public static AirframeDragForceSample sampleSettled(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio
	) {
		double targetSeparation = targetSeparationIntensity(config, relativeAirVelocityBodyMetersPerSecond);
		return sampleSteady(
				config,
				relativeAirVelocityBodyMetersPerSecond,
				airDensityRatio,
				targetSeparation
		);
	}

	public static double targetSeparationIntensity(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		return targetSeparationIntensity(
				relativeAirVelocityBodyMetersPerSecond,
				config.bodyDragCoefficients()
		);
	}

	public static double effectiveSeparationIntensity(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double separatedFlowStateIntensity
	) {
		if (!Double.isFinite(separatedFlowStateIntensity)) {
			throw new IllegalArgumentException("separatedFlowStateIntensity must be finite.");
		}
		return effectiveSeparationIntensity(
				targetSeparationIntensity(config, relativeAirVelocityBodyMetersPerSecond),
				separatedFlowStateIntensity
		);
	}

	public static Vec3 linearDampingDragForce(
			DroneConfig config,
			Vec3 relativeAirVelocityMetersPerSecond,
			double airDensityRatio
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (relativeAirVelocityMetersPerSecond == null || !relativeAirVelocityMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityMetersPerSecond must be finite.");
		}
		if (!Double.isFinite(airDensityRatio) || airDensityRatio < 0.0) {
			throw new IllegalArgumentException("airDensityRatio must be finite and nonnegative.");
		}
		return relativeAirVelocityMetersPerSecond.multiply(
				-config.linearDragCoefficient() * airDensityRatio
		);
	}

	public record AirframeDragForceSample(
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double separatedFlowStateIntensity,
			double targetSeparationIntensity,
			double effectiveSeparationIntensity,
			Vec3 baseQuadraticDragForceBodyNewtons,
			Vec3 separatedFlowDragForceBodyNewtons,
			Vec3 bodyDragForceBodyNewtons,
			Vec3 linearDampingDragForceBodyNewtons,
			Vec3 totalDragForceBodyNewtons,
			double dragAlongFlowNewtons,
			double equivalentLinearCoefficient,
			double equivalentCdAMetersSquared,
			double imavReferenceDragRatio
	) {
		public Vec3 bodyDragForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(bodyDragForceBodyNewtons);
		}

		public Vec3 linearDampingDragForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(linearDampingDragForceBodyNewtons);
		}

		public Vec3 totalDragForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalDragForceBodyNewtons);
		}

		public double bodyDragPowerDissipationWatts() {
			return Math.max(0.0, -bodyDragForceBodyNewtons.dot(relativeAirVelocityBodyMetersPerSecond));
		}

		public double linearDampingPowerDissipationWatts() {
			return Math.max(0.0,
					-linearDampingDragForceBodyNewtons.dot(relativeAirVelocityBodyMetersPerSecond));
		}

		public double totalDragPowerDissipationWatts() {
			return Math.max(0.0, -totalDragForceBodyNewtons.dot(relativeAirVelocityBodyMetersPerSecond));
		}
	}

	private static double targetSeparationIntensity(Vec3 relativeAirVelocityBody, Vec3 dragCoefficients) {
		if (relativeAirVelocityBody.lengthSquared() <= 1.0e-6
				|| dragCoefficients.z() <= 1.0e-9
				|| Math.max(dragCoefficients.x(), dragCoefficients.y()) <= 1.0e-9) {
			return 0.0;
		}
		double forwardReference = Math.max(2.0, Math.abs(relativeAirVelocityBody.z()));
		double angleOfAttack = Math.atan2(relativeAirVelocityBody.y(), forwardReference);
		double sideSlip = Math.atan2(relativeAirVelocityBody.x(), forwardReference);
		double pitchSeparation = smoothStep(
				Math.toRadians(30.0),
				Math.toRadians(66.0),
				Math.abs(angleOfAttack)
		);
		double yawSeparation = smoothStep(
				Math.toRadians(32.0),
				Math.toRadians(68.0),
				Math.abs(sideSlip)
		);
		return MathUtil.clamp(
				1.0 - (1.0 - pitchSeparation) * (1.0 - yawSeparation),
				0.0,
				1.0
		);
	}

	private static double effectiveSeparationIntensity(
			double targetSeparationIntensity,
			double separatedFlowStateIntensity
	) {
		double immediateSeparation = IMMEDIATE_SEPARATION_FRACTION * targetSeparationIntensity;
		return MathUtil.clamp(
				Math.max(MathUtil.clamp(separatedFlowStateIntensity, 0.0, 1.0), immediateSeparation),
				0.0,
				1.0
		);
	}

	private static Vec3 separatedFlowDragForceBodyNewtons(
			Vec3 relativeAirVelocityBody,
			Vec3 dragCoefficients,
			double effectiveSeparationIntensity
	) {
		double speedSquared = relativeAirVelocityBody.lengthSquared();
		if (speedSquared <= 1.0e-6 || effectiveSeparationIntensity <= 1.0e-6) {
			return Vec3.ZERO;
		}
		double maxBroadsideDrag = Math.max(dragCoefficients.x(), dragCoefficients.y());
		if (maxBroadsideDrag <= 1.0e-9 || dragCoefficients.z() <= 1.0e-9) {
			return Vec3.ZERO;
		}
		double broadsideCoefficient = 0.20 * maxBroadsideDrag
				+ 0.14 * Math.sqrt(Math.max(
						0.0,
						(dragCoefficients.x() + dragCoefficients.y()) * dragCoefficients.z()
				));
		return relativeAirVelocityBody.normalized()
				.multiply(-speedSquared * broadsideCoefficient * effectiveSeparationIntensity)
				.clamp(-SEPARATED_FLOW_FORCE_LIMIT_NEWTONS, SEPARATED_FLOW_FORCE_LIMIT_NEWTONS);
	}

	private static void validateInputs(
			DroneConfig config,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double airDensityRatio,
			double separatedFlowStateIntensity
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
		if (!Double.isFinite(separatedFlowStateIntensity)) {
			throw new IllegalArgumentException("separatedFlowStateIntensity must be finite.");
		}
	}

	private static Quaternion resolvedOrientation(Quaternion bodyToWorldOrientation) {
		if (bodyToWorldOrientation == null || !Double.isFinite(bodyToWorldOrientation.w())
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
