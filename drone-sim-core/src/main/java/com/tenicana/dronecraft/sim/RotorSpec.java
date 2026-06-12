package com.tenicana.dronecraft.sim;

public record RotorSpec(
		Vec3 positionBodyMeters,
		Vec3 thrustAxisBody,
		int spinDirection,
		double maxThrustNewtons,
		double thrustCoefficient,
		double yawTorquePerThrustMeter,
		double radiusMeters,
		double bladePitchMeters,
		double transverseFlowLiftCoefficient,
		double axialFlowThrustLossCoefficient,
		double diskDragCoefficient,
		double rotorInertiaKgMetersSquared,
		double inducedInflowTimeConstantSeconds,
		double inducedInflowLagCoefficient,
		double flappingCoefficient,
		double stallThrustLossCoefficient,
		double imbalanceIntensity,
		double motorWindingResistanceOhms,
		double motorPolePairs,
		int bladeCount
) {
	private static final Vec3 DEFAULT_THRUST_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);
	private static final double DEFAULT_IMBALANCE_INTENSITY = 0.012;
	private static final int DEFAULT_BLADE_COUNT = 2;
	public static final double DEFAULT_MOTOR_POLE_PAIRS = 7.0;
	public static final double DEFAULT_BLADE_PITCH_TO_DIAMETER_RATIO = 0.85;
	public static final double BLADE_GEOMETRY_REFERENCE_STATION_FRACTION = 0.70;
	public static final double DEFAULT_REPRESENTATIVE_CHORD_TO_RADIUS_RATIO = 0.12;
	public static final double HUB_BIASED_PROP_INERTIA_COEFFICIENT = 0.25;
	public static final double UNIFORM_BLADE_PROP_INERTIA_COEFFICIENT = 1.0 / 3.0;
	public static final double TIP_BIASED_PROP_INERTIA_COEFFICIENT = 0.50;

	public RotorSpec(
			Vec3 positionBodyMeters,
			int spinDirection,
			double maxThrustNewtons,
			double thrustCoefficient,
			double yawTorquePerThrustMeter,
			double radiusMeters,
			double transverseFlowLiftCoefficient,
			double axialFlowThrustLossCoefficient,
			double diskDragCoefficient,
			double rotorInertiaKgMetersSquared,
			double inducedInflowTimeConstantSeconds,
			double inducedInflowLagCoefficient,
			double flappingCoefficient
	) {
		this(
				positionBodyMeters,
				DEFAULT_THRUST_AXIS_BODY,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				defaultBladePitchMeters(radiusMeters),
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				0.0,
				DEFAULT_IMBALANCE_INTENSITY,
				0.0,
				DEFAULT_MOTOR_POLE_PAIRS,
				DEFAULT_BLADE_COUNT
		);
	}

	public RotorSpec(
			Vec3 positionBodyMeters,
			int spinDirection,
			double maxThrustNewtons,
			double thrustCoefficient,
			double yawTorquePerThrustMeter,
			double radiusMeters,
			double transverseFlowLiftCoefficient,
			double axialFlowThrustLossCoefficient,
			double diskDragCoefficient,
			double rotorInertiaKgMetersSquared,
			double inducedInflowTimeConstantSeconds,
			double inducedInflowLagCoefficient,
			double flappingCoefficient,
			double stallThrustLossCoefficient
	) {
		this(
				positionBodyMeters,
				DEFAULT_THRUST_AXIS_BODY,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				defaultBladePitchMeters(radiusMeters),
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				stallThrustLossCoefficient,
				DEFAULT_IMBALANCE_INTENSITY,
				0.0,
				DEFAULT_MOTOR_POLE_PAIRS,
				DEFAULT_BLADE_COUNT
		);
	}

	public RotorSpec(
			Vec3 positionBodyMeters,
			int spinDirection,
			double maxThrustNewtons,
			double thrustCoefficient,
			double yawTorquePerThrustMeter,
			double radiusMeters,
			double transverseFlowLiftCoefficient,
			double axialFlowThrustLossCoefficient,
			double diskDragCoefficient,
			double rotorInertiaKgMetersSquared,
			double inducedInflowTimeConstantSeconds,
			double inducedInflowLagCoefficient
	) {
		this(
				positionBodyMeters,
				DEFAULT_THRUST_AXIS_BODY,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				defaultBladePitchMeters(radiusMeters),
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				0.0,
				0.0,
				DEFAULT_IMBALANCE_INTENSITY,
				0.0,
				DEFAULT_MOTOR_POLE_PAIRS,
				DEFAULT_BLADE_COUNT
		);
	}

	public RotorSpec(
			Vec3 positionBodyMeters,
			int spinDirection,
			double maxThrustNewtons,
			double thrustCoefficient,
			double yawTorquePerThrustMeter,
			double radiusMeters,
			double transverseFlowLiftCoefficient,
			double axialFlowThrustLossCoefficient,
			double diskDragCoefficient,
			double rotorInertiaKgMetersSquared
	) {
		this(
				positionBodyMeters,
				DEFAULT_THRUST_AXIS_BODY,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				defaultBladePitchMeters(radiusMeters),
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				0.0,
				0.0,
				0.0,
				0.0,
				DEFAULT_IMBALANCE_INTENSITY,
				0.0,
				DEFAULT_MOTOR_POLE_PAIRS,
				DEFAULT_BLADE_COUNT
		);
	}

	public RotorSpec(
			Vec3 positionBodyMeters,
			Vec3 thrustAxisBody,
			int spinDirection,
			double maxThrustNewtons,
			double thrustCoefficient,
			double yawTorquePerThrustMeter,
			double radiusMeters,
			double bladePitchMeters,
			double transverseFlowLiftCoefficient,
			double axialFlowThrustLossCoefficient,
			double diskDragCoefficient,
			double rotorInertiaKgMetersSquared,
			double inducedInflowTimeConstantSeconds,
			double inducedInflowLagCoefficient,
			double flappingCoefficient,
			double stallThrustLossCoefficient,
			double imbalanceIntensity,
			int bladeCount
	) {
		this(
				positionBodyMeters,
				thrustAxisBody,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				bladePitchMeters,
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				stallThrustLossCoefficient,
				imbalanceIntensity,
				0.0,
				DEFAULT_MOTOR_POLE_PAIRS,
				bladeCount
		);
	}

	public RotorSpec {
		if (positionBodyMeters == null || !positionBodyMeters.isFinite()) {
			positionBodyMeters = Vec3.ZERO;
		}
		if (thrustAxisBody == null || !thrustAxisBody.isFinite() || thrustAxisBody.lengthSquared() <= 1.0e-9) {
			thrustAxisBody = DEFAULT_THRUST_AXIS_BODY;
		} else {
			thrustAxisBody = thrustAxisBody.normalized();
		}
		if (spinDirection != -1 && spinDirection != 1) {
			throw new IllegalArgumentException("spinDirection must be -1 or 1");
		}
		if (maxThrustNewtons <= 0.0) {
			throw new IllegalArgumentException("maxThrustNewtons must be positive");
		}
		if (thrustCoefficient <= 0.0) {
			throw new IllegalArgumentException("thrustCoefficient must be positive");
		}
		if (radiusMeters <= 0.0) {
			throw new IllegalArgumentException("radiusMeters must be positive");
		}
		if (!Double.isFinite(bladePitchMeters) || bladePitchMeters <= 0.0) {
			bladePitchMeters = defaultBladePitchMeters(radiusMeters);
		}
		bladePitchMeters = MathUtil.clamp(bladePitchMeters, radiusMeters * 0.35, radiusMeters * 3.50);
		yawTorquePerThrustMeter = MathUtil.clamp(yawTorquePerThrustMeter, 0.0, 0.08);
		transverseFlowLiftCoefficient = MathUtil.clamp(transverseFlowLiftCoefficient, 0.0, 0.25);
		axialFlowThrustLossCoefficient = MathUtil.clamp(axialFlowThrustLossCoefficient, 0.0, 0.45);
		diskDragCoefficient = MathUtil.clamp(diskDragCoefficient, 0.0, 0.03);
		rotorInertiaKgMetersSquared = MathUtil.clamp(rotorInertiaKgMetersSquared, 0.0, 0.0005);
		inducedInflowTimeConstantSeconds = MathUtil.clamp(inducedInflowTimeConstantSeconds, 0.0, 0.4);
		inducedInflowLagCoefficient = MathUtil.clamp(inducedInflowLagCoefficient, 0.0, 0.6);
		flappingCoefficient = MathUtil.clamp(flappingCoefficient, 0.0, 0.2);
		stallThrustLossCoefficient = MathUtil.clamp(stallThrustLossCoefficient, 0.0, 0.65);
		imbalanceIntensity = MathUtil.clamp(imbalanceIntensity, 0.0, 0.35);
		if (!Double.isFinite(motorWindingResistanceOhms) || motorWindingResistanceOhms <= 0.0) {
			motorWindingResistanceOhms = 0.0;
		} else {
			motorWindingResistanceOhms = MathUtil.clamp(motorWindingResistanceOhms, 0.01, 2.5);
		}
		if (!Double.isFinite(motorPolePairs) || motorPolePairs <= 0.0) {
			motorPolePairs = DEFAULT_MOTOR_POLE_PAIRS;
		} else {
			motorPolePairs = MathUtil.clamp(motorPolePairs, 1.0, 28.0);
		}
		bladeCount = Math.max(1, Math.min(8, bladeCount));
	}

	public static double defaultBladePitchMeters(double radiusMeters) {
		return Math.max(0.01, radiusMeters * 2.0 * DEFAULT_BLADE_PITCH_TO_DIAMETER_RATIO);
	}

	public static double estimatedPropInertiaKgMetersSquared(
			double radiusMeters,
			double propMassGrams,
			double massDistributionCoefficient
	) {
		if (!Double.isFinite(radiusMeters)
				|| !Double.isFinite(propMassGrams)
				|| !Double.isFinite(massDistributionCoefficient)) {
			return 0.0;
		}
		double safeRadiusMeters = Math.max(0.0, radiusMeters);
		double safeMassKilograms = Math.max(0.0, propMassGrams) * 0.001;
		double safeCoefficient = MathUtil.clamp(massDistributionCoefficient, 0.0, 1.0);
		return safeCoefficient * safeMassKilograms * safeRadiusMeters * safeRadiusMeters;
	}

	public static double estimatedUniformBladePropInertiaKgMetersSquared(double radiusMeters, double propMassGrams) {
		return estimatedPropInertiaKgMetersSquared(
				radiusMeters,
				propMassGrams,
				UNIFORM_BLADE_PROP_INERTIA_COEFFICIENT
		);
	}

	public double bladePitchToDiameterRatio() {
		return bladePitchMeters / Math.max(1.0e-6, 2.0 * radiusMeters);
	}

	public double geometricBladePitchAngleRadians() {
		return geometricBladePitchAngleRadians(BLADE_GEOMETRY_REFERENCE_STATION_FRACTION);
	}

	public double geometricBladePitchAngleRadians(double radialFraction) {
		double stationFraction = MathUtil.clamp(radialFraction, 0.20, 1.0);
		double stationRadius = radiusMeters * stationFraction;
		return Math.atan(bladePitchMeters / Math.max(1.0e-6, 2.0 * Math.PI * stationRadius));
	}

	public double representativeBladeChordToRadiusRatio() {
		double pitchRatio = bladePitchToDiameterRatio() / DEFAULT_BLADE_PITCH_TO_DIAMETER_RATIO;
		double pitchScale = MathUtil.clamp(0.88 + 0.12 * Math.sqrt(Math.max(0.05, pitchRatio)), 0.82, 1.14);
		double bladeCountScale = MathUtil.clamp(1.0 + 0.28 * (bladeCount - DEFAULT_BLADE_COUNT), 0.72, 1.56);
		double utilityLiftScale = 1.0 + 0.38
				* smoothStep(0.08, 0.115, radiusMeters)
				* (1.0 - smoothStep(0.55, 0.85, bladePitchToDiameterRatio()));
		return MathUtil.clamp(
				DEFAULT_REPRESENTATIVE_CHORD_TO_RADIUS_RATIO * pitchScale * bladeCountScale * utilityLiftScale,
				0.075,
				0.220
		);
	}

	public double representativeBladeChordMeters() {
		return radiusMeters * representativeBladeChordToRadiusRatio();
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	public double maxOmegaRadiansPerSecond() {
		return Math.sqrt(maxThrustNewtons / thrustCoefficient);
	}

	public RotorSpec withMaxThrustNewtons(double maxThrustNewtons) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withThrustCoefficient(double thrustCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withRadiusMeters(double radiusMeters) {
		double pitchRatio = bladePitchMeters / Math.max(1.0e-6, this.radiusMeters);
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, radiusMeters * pitchRatio, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withBladePitchMeters(double bladePitchMeters) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withBladePitchToDiameterRatio(double pitchToDiameterRatio) {
		double safeRatio = Double.isFinite(pitchToDiameterRatio)
				? pitchToDiameterRatio
				: DEFAULT_BLADE_PITCH_TO_DIAMETER_RATIO;
		return withBladePitchMeters(2.0 * radiusMeters * safeRatio);
	}

	public RotorSpec withTransverseFlowLiftCoefficient(double transverseFlowLiftCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withAxialFlowThrustLossCoefficient(double axialFlowThrustLossCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withDiskDragCoefficient(double diskDragCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withRotorInertiaKgMetersSquared(double rotorInertiaKgMetersSquared) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withYawTorquePerThrustMeter(double yawTorquePerThrustMeter) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withInducedInflow(double inducedInflowTimeConstantSeconds, double inducedInflowLagCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withFlappingCoefficient(double flappingCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withStallThrustLossCoefficient(double stallThrustLossCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withImbalanceIntensity(double imbalanceIntensity) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient, imbalanceIntensity);
	}

	public RotorSpec withMotorWindingResistanceOhms(double motorWindingResistanceOhms) {
		return new RotorSpec(
				positionBodyMeters,
				thrustAxisBody,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				bladePitchMeters,
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				stallThrustLossCoefficient,
				imbalanceIntensity,
				motorWindingResistanceOhms,
				motorPolePairs,
				bladeCount
		);
	}

	public RotorSpec withMotorPolePairs(double motorPolePairs) {
		return new RotorSpec(
				positionBodyMeters,
				thrustAxisBody,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				bladePitchMeters,
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				stallThrustLossCoefficient,
				imbalanceIntensity,
				motorWindingResistanceOhms,
				motorPolePairs,
				bladeCount
		);
	}

	public RotorSpec withBladeCount(int bladeCount) {
		return new RotorSpec(
				positionBodyMeters,
				thrustAxisBody,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				bladePitchMeters,
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				stallThrustLossCoefficient,
				imbalanceIntensity,
				motorWindingResistanceOhms,
				motorPolePairs,
				bladeCount
		);
	}

	public RotorSpec withThrustAxisBody(Vec3 thrustAxisBody) {
		return new RotorSpec(
				positionBodyMeters,
				thrustAxisBody,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				bladePitchMeters,
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				stallThrustLossCoefficient,
				imbalanceIntensity,
				motorWindingResistanceOhms,
				motorPolePairs,
				bladeCount
		);
	}

	private RotorSpec copy(
			double maxThrustNewtons,
			double thrustCoefficient,
			double yawTorquePerThrustMeter,
			double radiusMeters,
			double bladePitchMeters,
			double transverseFlowLiftCoefficient,
			double axialFlowThrustLossCoefficient,
			double diskDragCoefficient,
			double rotorInertiaKgMetersSquared,
			double inducedInflowTimeConstantSeconds,
			double inducedInflowLagCoefficient,
			double flappingCoefficient,
			double stallThrustLossCoefficient,
			double imbalanceIntensity
	) {
		return new RotorSpec(
				positionBodyMeters,
				thrustAxisBody,
				spinDirection,
				maxThrustNewtons,
				thrustCoefficient,
				yawTorquePerThrustMeter,
				radiusMeters,
				bladePitchMeters,
				transverseFlowLiftCoefficient,
				axialFlowThrustLossCoefficient,
				diskDragCoefficient,
				rotorInertiaKgMetersSquared,
				inducedInflowTimeConstantSeconds,
				inducedInflowLagCoefficient,
				flappingCoefficient,
				stallThrustLossCoefficient,
				imbalanceIntensity,
				motorWindingResistanceOhms,
				motorPolePairs,
				bladeCount
		);
	}
}
