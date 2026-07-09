package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class RotorInertiaTorqueModel {
	private static final Vec3 DEFAULT_ROTOR_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);

	private RotorInertiaTorqueModel() {
	}

	public static RotorInertiaTorqueSample sample(
			RotorSpec rotor,
			double previousOmegaRadiansPerSecond,
			double omegaRadiansPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 rotorAxisBody,
			double dtSeconds
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		validateOmega(previousOmegaRadiansPerSecond, "previousOmegaRadiansPerSecond");
		validateOmega(omegaRadiansPerSecond, "omegaRadiansPerSecond");
		if (angularVelocityBodyRadiansPerSecond == null || !angularVelocityBodyRadiansPerSecond.isFinite()) {
			throw new IllegalArgumentException("angularVelocityBodyRadiansPerSecond must be finite.");
		}
		if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
			throw new IllegalArgumentException("dtSeconds must be finite and positive.");
		}

		Vec3 axisBody = resolvedRotorAxisBody(rotor, rotorAxisBody);
		double inertia = rotor.rotorInertiaKgMetersSquared();
		double angularAcceleration = (omegaRadiansPerSecond - previousOmegaRadiansPerSecond) / dtSeconds;
		Vec3 angularMomentumBody = axisBody.multiply(
				rotor.spinDirection() * inertia * omegaRadiansPerSecond
		);
		Vec3 accelerationReactionTorqueBody = axisBody.multiply(
				-rotor.spinDirection() * inertia * angularAcceleration
		);
		Vec3 gyroscopicReactionTorqueBody = angularVelocityBodyRadiansPerSecond
				.cross(angularMomentumBody)
				.multiply(-1.0);
		double initialSpinKineticEnergy = 0.5 * inertia
				* previousOmegaRadiansPerSecond * previousOmegaRadiansPerSecond;
		double spinKineticEnergy = 0.5 * inertia * omegaRadiansPerSecond * omegaRadiansPerSecond;
		return new RotorInertiaTorqueSample(
				rotor,
				axisBody,
				previousOmegaRadiansPerSecond,
				omegaRadiansPerSecond,
				angularAcceleration,
				angularMomentumBody,
				accelerationReactionTorqueBody,
				gyroscopicReactionTorqueBody,
				initialSpinKineticEnergy,
				spinKineticEnergy
		);
	}

	public static ConfigurationRotorInertiaTorqueSample sampleConfiguration(
			DroneConfig config,
			double[] previousOmegaRadiansPerSecond,
			double[] omegaRadiansPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double dtSeconds
	) {
		return sampleConfiguration(
				config,
				previousOmegaRadiansPerSecond,
				omegaRadiansPerSecond,
				angularVelocityBodyRadiansPerSecond,
				null,
				dtSeconds
		);
	}

	public static ConfigurationRotorInertiaTorqueSample sampleConfiguration(
			DroneConfig config,
			double[] previousOmegaRadiansPerSecond,
			double[] omegaRadiansPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3[] rotorAxesBody,
			double dtSeconds
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		int rotorCount = config.rotors().size();
		validateRotorArray(previousOmegaRadiansPerSecond, rotorCount, "previousOmegaRadiansPerSecond");
		validateRotorArray(omegaRadiansPerSecond, rotorCount, "omegaRadiansPerSecond");
		if (rotorAxesBody != null && rotorAxesBody.length != rotorCount) {
			throw new IllegalArgumentException("rotorAxesBody length must match config rotor count.");
		}

		List<RotorInertiaTorqueSample> rotorSamples = new ArrayList<>(rotorCount);
		Vec3 totalAngularMomentumBody = Vec3.ZERO;
		Vec3 totalAccelerationReactionTorqueBody = Vec3.ZERO;
		Vec3 totalGyroscopicReactionTorqueBody = Vec3.ZERO;
		double initialSpinKineticEnergy = 0.0;
		double spinKineticEnergy = 0.0;
		for (int i = 0; i < rotorCount; i++) {
			RotorInertiaTorqueSample rotorSample = sample(
					config.rotors().get(i),
					previousOmegaRadiansPerSecond[i],
					omegaRadiansPerSecond[i],
					angularVelocityBodyRadiansPerSecond,
					rotorAxesBody == null ? null : rotorAxesBody[i],
					dtSeconds
			);
			rotorSamples.add(rotorSample);
			totalAngularMomentumBody = totalAngularMomentumBody.add(rotorSample.angularMomentumBodyKgMetersSquaredPerSecond());
			totalAccelerationReactionTorqueBody = totalAccelerationReactionTorqueBody
					.add(rotorSample.accelerationReactionTorqueBodyNewtonMeters());
			totalGyroscopicReactionTorqueBody = totalGyroscopicReactionTorqueBody
					.add(rotorSample.gyroscopicReactionTorqueBodyNewtonMeters());
			initialSpinKineticEnergy += rotorSample.initialSpinKineticEnergyJoules();
			spinKineticEnergy += rotorSample.spinKineticEnergyJoules();
		}
		return new ConfigurationRotorInertiaTorqueSample(
				rotorSamples,
				totalAngularMomentumBody,
				totalAccelerationReactionTorqueBody,
				totalGyroscopicReactionTorqueBody,
				initialSpinKineticEnergy,
				spinKineticEnergy
		);
	}

	public record RotorInertiaTorqueSample(
			RotorSpec rotor,
			Vec3 rotorAxisBody,
			double previousOmegaRadiansPerSecond,
			double omegaRadiansPerSecond,
			double angularAccelerationRadiansPerSecondSquared,
			Vec3 angularMomentumBodyKgMetersSquaredPerSecond,
			Vec3 accelerationReactionTorqueBodyNewtonMeters,
			Vec3 gyroscopicReactionTorqueBodyNewtonMeters,
			double initialSpinKineticEnergyJoules,
			double spinKineticEnergyJoules
	) {
		public Vec3 totalReactionTorqueBodyNewtonMeters() {
			return accelerationReactionTorqueBodyNewtonMeters.add(gyroscopicReactionTorqueBodyNewtonMeters);
		}

		public double spinKineticEnergyDeltaJoules() {
			return spinKineticEnergyJoules - initialSpinKineticEnergyJoules;
		}
	}

	public record ConfigurationRotorInertiaTorqueSample(
			List<RotorInertiaTorqueSample> rotorSamples,
			Vec3 totalAngularMomentumBodyKgMetersSquaredPerSecond,
			Vec3 totalAccelerationReactionTorqueBodyNewtonMeters,
			Vec3 totalGyroscopicReactionTorqueBodyNewtonMeters,
			double initialSpinKineticEnergyJoules,
			double spinKineticEnergyJoules
	) {
		public ConfigurationRotorInertiaTorqueSample {
			rotorSamples = List.copyOf(rotorSamples == null ? List.of() : rotorSamples);
		}

		public Vec3 totalReactionTorqueBodyNewtonMeters() {
			return totalAccelerationReactionTorqueBodyNewtonMeters
					.add(totalGyroscopicReactionTorqueBodyNewtonMeters);
		}

		public double spinKineticEnergyDeltaJoules() {
			return spinKineticEnergyJoules - initialSpinKineticEnergyJoules;
		}
	}

	private static Vec3 resolvedRotorAxisBody(RotorSpec rotor, Vec3 requestedAxisBody) {
		Vec3 axis = requestedAxisBody;
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9) {
			axis = rotor.thrustAxisBody();
		}
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9) {
			return DEFAULT_ROTOR_AXIS_BODY;
		}
		return axis.normalized();
	}

	private static void validateOmega(double omegaRadiansPerSecond, String name) {
		if (!Double.isFinite(omegaRadiansPerSecond) || omegaRadiansPerSecond < 0.0) {
			throw new IllegalArgumentException(name + " must be finite and nonnegative.");
		}
	}

	private static void validateRotorArray(double[] values, int rotorCount, String name) {
		if (values == null || values.length != rotorCount) {
			throw new IllegalArgumentException(name + " length must match config rotor count.");
		}
	}
}
