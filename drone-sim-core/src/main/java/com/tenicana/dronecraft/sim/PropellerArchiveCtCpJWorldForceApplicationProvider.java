package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJWorldForceApplicationProvider {
	private static final double DEFAULT_AMBIENT_TEMPERATURE_CELSIUS = 25.0;

	private PropellerArchiveCtCpJWorldForceApplicationProvider() {
	}

	public static WorldForceApplicationSample sampleStaticAnchoredConfigurationFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 momentReferenceWorldMeters,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleStaticAnchoredConfigurationFromWorldKinematics(
				presetName,
				caseName,
				config,
				momentReferenceWorldMeters,
				bodyToWorldOrientation,
				vehicleVelocityWorldMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				windVelocityWorldMetersPerSecond,
				rotorWindVelocityWorldMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				DEFAULT_AMBIENT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static WorldForceApplicationSample sampleStaticAnchoredConfigurationFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 momentReferenceWorldMeters,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		Quaternion orientation = finiteQuaternionOrIdentity(bodyToWorldOrientation).normalized();
		Vec3 momentReference = finiteVecOrZero(momentReferenceWorldMeters);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromWorldKinematics(
						presetName,
						caseName,
						config,
						orientation,
						vehicleVelocityWorldMetersPerSecond,
						angularVelocityBodyRadiansPerSecond,
						windVelocityWorldMetersPerSecond,
						rotorWindVelocityWorldMetersPerSecond,
						omegaRadiansPerSecond,
						airDensityKgPerCubicMeter,
						envelopePolicy,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		return new WorldForceApplicationSample(
				aggregate,
				aggregate.rotorWorldForceApplications(momentReference, orientation),
				aggregate.runtimeForceReplacementRotorWorldForceApplications(momentReference, orientation)
		);
	}

	public record WorldForceApplicationSample(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate,
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> rotorApplications,
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> runtimeReplacementRotorApplications
	) {
		public WorldForceApplicationSample {
			if (aggregate == null) {
				throw new IllegalArgumentException("aggregate must not be null.");
			}
			rotorApplications = List.copyOf(rotorApplications == null ? List.of() : rotorApplications);
			runtimeReplacementRotorApplications = List.copyOf(runtimeReplacementRotorApplications == null
					? List.of()
					: runtimeReplacementRotorApplications);
		}

		public int rotorCount() {
			return rotorApplications.size();
		}

		public int appliedRotorCount() {
			int count = 0;
			for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application
					: rotorApplications) {
				if (application.applied()) {
					count++;
				}
			}
			return count;
		}

		public int runtimeReplacementAppliedRotorCount() {
			int count = 0;
			for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application
					: runtimeReplacementRotorApplications) {
				if (application.applied()) {
					count++;
				}
			}
			return count;
		}

		public boolean runtimeReplacementAccepted() {
			return rotorCount() > 0 && runtimeReplacementAppliedRotorCount() == rotorCount();
		}

		public Vec3 totalThrustForceWorldNewtons() {
			return sumThrustForce(rotorApplications);
		}

		public Vec3 totalReactionTorqueWorldNewtonMeters() {
			return sumReactionTorque(rotorApplications);
		}

		public Vec3 totalThrustMomentWorldNewtonMeters() {
			return sumThrustMoment(rotorApplications);
		}

		public Vec3 totalTorqueWorldNewtonMeters() {
			return sumTotalTorque(rotorApplications);
		}

		public Vec3 runtimeReplacementTotalThrustForceWorldNewtons() {
			return sumThrustForce(runtimeReplacementRotorApplications);
		}

		public Vec3 runtimeReplacementTotalReactionTorqueWorldNewtonMeters() {
			return sumReactionTorque(runtimeReplacementRotorApplications);
		}

		public Vec3 runtimeReplacementTotalThrustMomentWorldNewtonMeters() {
			return sumThrustMoment(runtimeReplacementRotorApplications);
		}

		public Vec3 runtimeReplacementTotalTorqueWorldNewtonMeters() {
			return sumTotalTorque(runtimeReplacementRotorApplications);
		}
	}

	private static Vec3 sumThrustForce(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.thrustForceWorldNewtons());
		}
		return sum;
	}

	private static Vec3 sumReactionTorque(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.reactionTorqueWorldNewtonMeters());
		}
		return sum;
	}

	private static Vec3 sumThrustMoment(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.thrustMomentWorldNewtonMeters());
		}
		return sum;
	}

	private static Vec3 sumTotalTorque(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.totalTorqueWorldNewtonMeters());
		}
		return sum;
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		if (value == null || !value.isFinite()) {
			return Vec3.ZERO;
		}
		return value;
	}

	private static Quaternion finiteQuaternionOrIdentity(Quaternion value) {
		if (value == null
				|| !Double.isFinite(value.w())
				|| !Double.isFinite(value.x())
				|| !Double.isFinite(value.y())
				|| !Double.isFinite(value.z())) {
			return Quaternion.IDENTITY;
		}
		return value;
	}
}
