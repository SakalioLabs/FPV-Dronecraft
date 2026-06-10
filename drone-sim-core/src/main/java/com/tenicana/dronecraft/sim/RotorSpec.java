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
		double stallThrustLossCoefficient
) {
	private static final Vec3 DEFAULT_THRUST_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);

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
				0.0
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
				stallThrustLossCoefficient
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
				0.0
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
				0.0
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
	}

	public static double defaultBladePitchMeters(double radiusMeters) {
		return Math.max(0.01, radiusMeters * 1.70);
	}

	public double maxOmegaRadiansPerSecond() {
		return Math.sqrt(maxThrustNewtons / thrustCoefficient);
	}

	public RotorSpec withMaxThrustNewtons(double maxThrustNewtons) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withThrustCoefficient(double thrustCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withRadiusMeters(double radiusMeters) {
		double pitchRatio = bladePitchMeters / Math.max(1.0e-6, this.radiusMeters);
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, radiusMeters * pitchRatio, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withBladePitchMeters(double bladePitchMeters) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withTransverseFlowLiftCoefficient(double transverseFlowLiftCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withAxialFlowThrustLossCoefficient(double axialFlowThrustLossCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withDiskDragCoefficient(double diskDragCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withRotorInertiaKgMetersSquared(double rotorInertiaKgMetersSquared) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withYawTorquePerThrustMeter(double yawTorquePerThrustMeter) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withInducedInflow(double inducedInflowTimeConstantSeconds, double inducedInflowLagCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withFlappingCoefficient(double flappingCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
	}

	public RotorSpec withStallThrustLossCoefficient(double stallThrustLossCoefficient) {
		return copy(maxThrustNewtons, thrustCoefficient, yawTorquePerThrustMeter, radiusMeters, bladePitchMeters, transverseFlowLiftCoefficient, axialFlowThrustLossCoefficient, diskDragCoefficient, rotorInertiaKgMetersSquared, inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient, flappingCoefficient, stallThrustLossCoefficient);
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
				stallThrustLossCoefficient
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
			double stallThrustLossCoefficient
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
				stallThrustLossCoefficient
		);
	}
}
