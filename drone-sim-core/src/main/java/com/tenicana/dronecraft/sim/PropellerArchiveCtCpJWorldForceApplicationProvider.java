package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJWorldForceApplicationProvider {
	private static final double DEFAULT_AMBIENT_TEMPERATURE_CELSIUS = 25.0;

	private PropellerArchiveCtCpJWorldForceApplicationProvider() {
	}

	public static WorldForceApplicationSample sampleStaticAnchoredConfigurationFromState(
			String presetName,
			String caseName,
			DroneConfig config,
			DroneState state,
			DroneEnvironment environment,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		if (state == null) {
			throw new IllegalArgumentException("state must not be null.");
		}
		return sampleStaticAnchoredConfigurationFromState(
				presetName,
				caseName,
				config,
				state,
				environment,
				state.motorOmegaRadiansPerSecond(),
				envelopePolicy
		);
	}

	public static WorldForceApplicationSample sampleStaticAnchoredConfigurationFromState(
			String presetName,
			String caseName,
			DroneConfig config,
			DroneState state,
			DroneEnvironment environment,
			double[] omegaRadiansPerSecond,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		if (state == null) {
			throw new IllegalArgumentException("state must not be null.");
		}
		DroneEnvironment resolvedEnvironment = environment == null ? DroneEnvironment.calm() : environment;
		return sampleStaticAnchoredConfigurationFromWorldKinematics(
				presetName,
				caseName,
				config,
				state.positionMeters(),
				state.orientation(),
				state.velocityMetersPerSecond(),
				state.angularVelocityBodyRadiansPerSecond(),
				resolvedEnvironment.windVelocityWorldMetersPerSecond(),
				resolvedEnvironment.rotorWindVelocityWorldMetersPerSecond(),
				omegaRadiansPerSecond,
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
						* resolvedEnvironment.effectiveAirDensityRatio(),
				envelopePolicy,
				resolvedEnvironment.effectiveAmbientTemperatureCelsius(),
				resolvedEnvironment.ambientHumidity()
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
				momentReference,
				orientation,
				aggregate.rotorWorldForceApplications(momentReference, orientation),
				aggregate.runtimeForceReplacementRotorWorldForceApplications(momentReference, orientation)
		);
	}

	public record WorldForceApplicationSample(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate,
			Vec3 momentReferenceWorldMeters,
			Quaternion bodyToWorldOrientation,
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> rotorApplications,
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> runtimeReplacementRotorApplications
	) {
		public WorldForceApplicationSample {
			if (aggregate == null) {
				throw new IllegalArgumentException("aggregate must not be null.");
			}
			momentReferenceWorldMeters = finiteVecOrZero(momentReferenceWorldMeters);
			bodyToWorldOrientation = finiteQuaternionOrIdentity(bodyToWorldOrientation).normalized();
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

		public RigidBodyWrenchSample rotorRigidBodyWrench(
				DroneConfig config,
				Vec3 angularVelocityBodyRadiansPerSecond
		) {
			return rigidBodyWrench(
					config,
					totalThrustForceWorldNewtons(),
					totalTorqueWorldNewtonMeters(),
					angularVelocityBodyRadiansPerSecond,
					false
			);
		}

		public RigidBodyWrenchSample runtimeReplacementRigidBodyWrench(
				DroneConfig config,
				Vec3 angularVelocityBodyRadiansPerSecond
		) {
			return rigidBodyWrench(
					config,
					runtimeReplacementTotalThrustForceWorldNewtons(),
					runtimeReplacementTotalTorqueWorldNewtonMeters(),
					angularVelocityBodyRadiansPerSecond,
					true
			);
		}

		public RotorOnlyStepPreview rotorOnlyStepPreview(
				DroneConfig config,
				Vec3 positionWorldMeters,
				Vec3 velocityWorldMetersPerSecond,
				Vec3 angularVelocityBodyRadiansPerSecond,
				double dtSeconds
		) {
			return stepPreview(
					rotorRigidBodyWrench(config, angularVelocityBodyRadiansPerSecond),
					positionWorldMeters,
					velocityWorldMetersPerSecond,
					angularVelocityBodyRadiansPerSecond,
					dtSeconds
			);
		}

		public RotorOnlyStepPreview runtimeReplacementRotorOnlyStepPreview(
				DroneConfig config,
				Vec3 positionWorldMeters,
				Vec3 velocityWorldMetersPerSecond,
				Vec3 angularVelocityBodyRadiansPerSecond,
				double dtSeconds
		) {
			return stepPreview(
					runtimeReplacementRigidBodyWrench(config, angularVelocityBodyRadiansPerSecond),
					positionWorldMeters,
					velocityWorldMetersPerSecond,
					angularVelocityBodyRadiansPerSecond,
					dtSeconds
			);
		}

		private RotorOnlyStepPreview stepPreview(
				RigidBodyWrenchSample wrench,
				Vec3 positionWorldMeters,
				Vec3 velocityWorldMetersPerSecond,
				Vec3 angularVelocityBodyRadiansPerSecond,
				double dtSeconds
		) {
			double dt = finiteNonnegative(dtSeconds);
			Vec3 initialPosition = finiteVecOrZero(positionWorldMeters);
			Vec3 initialVelocity = finiteVecOrZero(velocityWorldMetersPerSecond);
			Vec3 initialAngularVelocity = finiteVecOrZero(angularVelocityBodyRadiansPerSecond);
			Vec3 nextVelocity = initialVelocity.add(
					wrench.linearAccelerationWorldMetersPerSecondSquared().multiply(dt)
			);
			Vec3 nextPosition = initialPosition.add(nextVelocity.multiply(dt));
			Vec3 nextAngularVelocity = initialAngularVelocity.add(
					wrench.angularAccelerationBodyRadiansPerSecondSquared().multiply(dt)
			);
			Quaternion nextOrientation = bodyToWorldOrientation.integrateBodyAngularVelocity(
					nextAngularVelocity,
					dt
			);
			return new RotorOnlyStepPreview(
					wrench,
					dt,
					initialPosition,
					initialVelocity,
					bodyToWorldOrientation,
					initialAngularVelocity,
					nextPosition,
					nextVelocity,
					nextOrientation,
					nextAngularVelocity
			);
		}

		private RigidBodyWrenchSample rigidBodyWrench(
				DroneConfig config,
				Vec3 totalForceWorldNewtons,
				Vec3 totalTorqueWorldNewtonMeters,
				Vec3 angularVelocityBodyRadiansPerSecond,
				boolean runtimeReplacement
		) {
			if (config == null) {
				throw new IllegalArgumentException("config must not be null.");
			}
			Vec3 forceWorld = finiteVecOrZero(totalForceWorldNewtons);
			Vec3 torqueWorld = finiteVecOrZero(totalTorqueWorldNewtonMeters);
			Vec3 torqueBody = rotateWorldVectorToBody(torqueWorld, bodyToWorldOrientation);
			Vec3 angularVelocityBody = finiteVecOrZero(angularVelocityBodyRadiansPerSecond);
			Vec3 inertia = config.inertiaKgMetersSquared();
			Vec3 gyroscopicTorqueBody = angularVelocityBody.cross(inertia.multiply(angularVelocityBody));
			Vec3 angularAccelerationBody = torqueBody.subtract(gyroscopicTorqueBody).divide(inertia);
			Vec3 linearAccelerationWorld = forceWorld.multiply(1.0 / config.massKg());
			return new RigidBodyWrenchSample(
					forceWorld,
					torqueWorld,
					torqueBody,
					linearAccelerationWorld,
					angularAccelerationBody,
					gyroscopicTorqueBody,
					runtimeReplacement
			);
		}
	}

	public record RigidBodyWrenchSample(
			Vec3 totalForceWorldNewtons,
			Vec3 totalTorqueWorldNewtonMeters,
			Vec3 totalTorqueBodyNewtonMeters,
			Vec3 linearAccelerationWorldMetersPerSecondSquared,
			Vec3 angularAccelerationBodyRadiansPerSecondSquared,
			Vec3 gyroscopicTorqueBodyNewtonMeters,
			boolean runtimeReplacement
	) {
		public RigidBodyWrenchSample {
			totalForceWorldNewtons = finiteVecOrZero(totalForceWorldNewtons);
			totalTorqueWorldNewtonMeters = finiteVecOrZero(totalTorqueWorldNewtonMeters);
			totalTorqueBodyNewtonMeters = finiteVecOrZero(totalTorqueBodyNewtonMeters);
			linearAccelerationWorldMetersPerSecondSquared =
					finiteVecOrZero(linearAccelerationWorldMetersPerSecondSquared);
			angularAccelerationBodyRadiansPerSecondSquared =
					finiteVecOrZero(angularAccelerationBodyRadiansPerSecondSquared);
			gyroscopicTorqueBodyNewtonMeters = finiteVecOrZero(gyroscopicTorqueBodyNewtonMeters);
		}
	}

	public record RotorOnlyStepPreview(
			RigidBodyWrenchSample wrench,
			double dtSeconds,
			Vec3 initialPositionWorldMeters,
			Vec3 initialVelocityWorldMetersPerSecond,
			Quaternion initialBodyToWorldOrientation,
			Vec3 initialAngularVelocityBodyRadiansPerSecond,
			Vec3 nextPositionWorldMeters,
			Vec3 nextVelocityWorldMetersPerSecond,
			Quaternion nextBodyToWorldOrientation,
			Vec3 nextAngularVelocityBodyRadiansPerSecond
	) {
		public RotorOnlyStepPreview {
			if (wrench == null) {
				throw new IllegalArgumentException("wrench must not be null.");
			}
			dtSeconds = finiteNonnegative(dtSeconds);
			initialPositionWorldMeters = finiteVecOrZero(initialPositionWorldMeters);
			initialVelocityWorldMetersPerSecond = finiteVecOrZero(initialVelocityWorldMetersPerSecond);
			initialBodyToWorldOrientation = finiteQuaternionOrIdentity(initialBodyToWorldOrientation).normalized();
			initialAngularVelocityBodyRadiansPerSecond =
					finiteVecOrZero(initialAngularVelocityBodyRadiansPerSecond);
			nextPositionWorldMeters = finiteVecOrZero(nextPositionWorldMeters);
			nextVelocityWorldMetersPerSecond = finiteVecOrZero(nextVelocityWorldMetersPerSecond);
			nextBodyToWorldOrientation = finiteQuaternionOrIdentity(nextBodyToWorldOrientation).normalized();
			nextAngularVelocityBodyRadiansPerSecond =
					finiteVecOrZero(nextAngularVelocityBodyRadiansPerSecond);
		}

		public boolean runtimeReplacement() {
			return wrench.runtimeReplacement();
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

	private static double finiteNonnegative(double value) {
		if (!Double.isFinite(value) || value < 0.0) {
			return 0.0;
		}
		return value;
	}

	private static Vec3 rotateWorldVectorToBody(Vec3 worldVector, Quaternion bodyToWorldOrientation) {
		return finiteQuaternionOrIdentity(bodyToWorldOrientation)
				.normalized()
				.conjugate()
				.rotate(finiteVecOrZero(worldVector));
	}
}
