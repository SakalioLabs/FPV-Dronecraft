package com.tenicana.dronecraft.sim;

import java.util.List;
import java.util.function.ToDoubleFunction;

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

	public static StateRotorTelemetryComparisonSample compareStateRotorTelemetryToReference(
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
		return compareStateRotorTelemetryToReference(
				presetName,
				caseName,
				config,
				state,
				environment,
				state.motorOmegaRadiansPerSecond(),
				envelopePolicy
		);
	}

	public static StateRotorTelemetryComparisonSample compareStateRotorTelemetryToReference(
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
		WorldForceApplicationSample reference = sampleStaticAnchoredConfigurationFromState(
				presetName,
				caseName,
				config,
				state,
				environment,
				omegaRadiansPerSecond,
				envelopePolicy
		);
		return new StateRotorTelemetryComparisonSample(
				reference,
				state.motorCount(),
				sum(state.rotorForceBodyNewtons()),
				sum(state.rotorTorqueBodyNewtonMeters()),
				sum(state.rotorThrustNewtons()),
				sum(state.motorShaftPowerWatts()),
				sum(state.motorAerodynamicTorqueNewtonMeters())
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
				aggregate.runtimeForceReplacementRotorWorldForceApplications(momentReference, orientation),
				aggregate.rotorActuatorDiskSourceTerms(momentReference, orientation),
				aggregate.runtimeForceReplacementRotorActuatorDiskSourceTerms(momentReference, orientation)
		);
	}

	public record WorldForceApplicationSample(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate,
			Vec3 momentReferenceWorldMeters,
			Quaternion bodyToWorldOrientation,
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> rotorApplications,
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> runtimeReplacementRotorApplications,
			List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> rotorActuatorDiskSourceTerms,
			List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample>
					runtimeReplacementRotorActuatorDiskSourceTerms
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
			rotorActuatorDiskSourceTerms = List.copyOf(rotorActuatorDiskSourceTerms == null
					? List.of()
					: rotorActuatorDiskSourceTerms);
			runtimeReplacementRotorActuatorDiskSourceTerms =
					List.copyOf(runtimeReplacementRotorActuatorDiskSourceTerms == null
							? List.of()
							: runtimeReplacementRotorActuatorDiskSourceTerms);
		}

		public int rotorCount() {
			return rotorApplications.size();
		}

		public int sourceTermCount() {
			return rotorActuatorDiskSourceTerms.size();
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

		public int appliedSourceTermCount() {
			int count = 0;
			for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm
					: rotorActuatorDiskSourceTerms) {
				if (sourceTerm.applied()) {
					count++;
				}
			}
			return count;
		}

		public int runtimeReplacementAppliedSourceTermCount() {
			int count = 0;
			for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm
					: runtimeReplacementRotorActuatorDiskSourceTerms) {
				if (sourceTerm.applied()) {
					count++;
				}
			}
			return count;
		}

		public boolean runtimeReplacementAccepted() {
			return rotorCount() > 0 && runtimeReplacementAppliedRotorCount() == rotorCount();
		}

		public PropellerArchiveCtCpJActuatorDiskSourceField actuatorDiskSourceField(double sourceThicknessMeters) {
			return new PropellerArchiveCtCpJActuatorDiskSourceField(
					rotorActuatorDiskSourceTerms,
					sourceThicknessMeters
			);
		}

		public PropellerArchiveCtCpJActuatorDiskSourceField runtimeReplacementActuatorDiskSourceField(
				double sourceThicknessMeters
		) {
			return new PropellerArchiveCtCpJActuatorDiskSourceField(
					runtimeReplacementRotorActuatorDiskSourceTerms,
					sourceThicknessMeters
			);
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

		public Vec3 totalActuatorDiskSurfaceForceWorldNewtons() {
			return sumActuatorDiskSurfaceForce(rotorActuatorDiskSourceTerms);
		}

		public Vec3 runtimeReplacementTotalActuatorDiskSurfaceForceWorldNewtons() {
			return sumActuatorDiskSurfaceForce(runtimeReplacementRotorActuatorDiskSourceTerms);
		}

		public Vec3 totalActuatorDiskWakeAngularMomentumTorqueWorldNewtonMeters() {
			return sumActuatorDiskWakeAngularMomentumTorque(rotorActuatorDiskSourceTerms);
		}

		public Vec3 runtimeReplacementTotalActuatorDiskWakeAngularMomentumTorqueWorldNewtonMeters() {
			return sumActuatorDiskWakeAngularMomentumTorque(runtimeReplacementRotorActuatorDiskSourceTerms);
		}

		public Vec3 totalActuatorDiskWakeAngularMomentumTorqueResidualWorldNewtonMeters() {
			return sumActuatorDiskWakeAngularMomentumTorqueResidual(rotorActuatorDiskSourceTerms);
		}

		public Vec3 runtimeReplacementTotalActuatorDiskWakeAngularMomentumTorqueResidualWorldNewtonMeters() {
			return sumActuatorDiskWakeAngularMomentumTorqueResidual(runtimeReplacementRotorActuatorDiskSourceTerms);
		}

		public double totalActuatorDiskMassFlowKilogramsPerSecond() {
			return sumActuatorDiskScalar(
					rotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::diskMassFlowKilogramsPerSecond
			);
		}

		public double runtimeReplacementTotalActuatorDiskMassFlowKilogramsPerSecond() {
			return sumActuatorDiskScalar(
					runtimeReplacementRotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::diskMassFlowKilogramsPerSecond
			);
		}

		public double totalActuatorDiskIdealMomentumPowerWatts() {
			return sumActuatorDiskScalar(
					rotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::idealMomentumPowerWatts
			);
		}

		public double runtimeReplacementTotalActuatorDiskIdealMomentumPowerWatts() {
			return sumActuatorDiskScalar(
					runtimeReplacementRotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::idealMomentumPowerWatts
			);
		}

		public double totalActuatorDiskWakeSwirlKineticPowerWatts() {
			return sumActuatorDiskScalar(
					rotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::wakeSwirlKineticPowerWatts
			);
		}

		public double runtimeReplacementTotalActuatorDiskWakeSwirlKineticPowerWatts() {
			return sumActuatorDiskScalar(
					runtimeReplacementRotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::wakeSwirlKineticPowerWatts
			);
		}

		public double totalActuatorDiskWakeKineticPowerWatts() {
			return sumActuatorDiskScalar(
					rotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::totalWakeKineticPowerWatts
			);
		}

		public double runtimeReplacementTotalActuatorDiskWakeKineticPowerWatts() {
			return sumActuatorDiskScalar(
					runtimeReplacementRotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::totalWakeKineticPowerWatts
			);
		}

		public double totalActuatorDiskWakeKineticPowerResidualWatts() {
			return sumActuatorDiskScalar(
					rotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::totalWakeKineticPowerResidualWatts
			);
		}

		public double runtimeReplacementTotalActuatorDiskWakeKineticPowerResidualWatts() {
			return sumActuatorDiskScalar(
					runtimeReplacementRotorActuatorDiskSourceTerms,
					PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample
							::totalWakeKineticPowerResidualWatts
			);
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

	public record StateRotorTelemetryComparisonSample(
			WorldForceApplicationSample referenceSample,
			int actualRotorCount,
			Vec3 actualTotalForceBodyNewtons,
			Vec3 actualTotalTorqueBodyNewtonMeters,
			double actualTotalThrustNewtons,
			double actualTotalShaftPowerWatts,
			double actualTotalShaftTorqueNewtonMeters
	) {
		public StateRotorTelemetryComparisonSample {
			if (referenceSample == null) {
				throw new IllegalArgumentException("referenceSample must not be null.");
			}
			actualRotorCount = Math.max(0, actualRotorCount);
			actualTotalForceBodyNewtons = finiteVecOrZero(actualTotalForceBodyNewtons);
			actualTotalTorqueBodyNewtonMeters = finiteVecOrZero(actualTotalTorqueBodyNewtonMeters);
			actualTotalThrustNewtons = finiteNonnegative(actualTotalThrustNewtons);
			actualTotalShaftPowerWatts = finiteNonnegative(actualTotalShaftPowerWatts);
			actualTotalShaftTorqueNewtonMeters = finiteNonnegative(actualTotalShaftTorqueNewtonMeters);
		}

		public PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample referenceAggregate() {
			return referenceSample.aggregate();
		}

		public Vec3 referenceTotalForceBodyNewtons() {
			return referenceAggregate().totalThrustForceBodyNewtons();
		}

		public Vec3 referenceTotalTorqueBodyNewtonMeters() {
			return referenceAggregate().totalBodyTorqueNewtonMeters();
		}

		public double referenceTotalThrustNewtons() {
			return referenceAggregate().totalThrustNewtons();
		}

		public double referenceTotalShaftPowerWatts() {
			return referenceAggregate().totalShaftPowerWatts();
		}

		public double referenceTotalShaftTorqueNewtonMeters() {
			return referenceAggregate().totalShaftTorqueNewtonMeters();
		}

		public Vec3 runtimeReplacementReferenceTotalForceBodyNewtons() {
			return referenceAggregate().runtimeForceReplacementThrustForceBodyNewtons();
		}

		public Vec3 runtimeReplacementReferenceTotalTorqueBodyNewtonMeters() {
			return referenceAggregate().runtimeForceReplacementTotalBodyTorqueNewtonMeters();
		}

		public double runtimeReplacementReferenceTotalThrustNewtons() {
			return referenceAggregate().runtimeForceReplacementThrustNewtons();
		}

		public double runtimeReplacementReferenceTotalShaftPowerWatts() {
			return referenceAggregate().runtimeForceReplacementShaftPowerWatts();
		}

		public double runtimeReplacementReferenceTotalShaftTorqueNewtonMeters() {
			return referenceAggregate().runtimeForceReplacementShaftTorqueNewtonMeters();
		}

		public Vec3 forceBodyResidualNewtons() {
			return actualTotalForceBodyNewtons.subtract(referenceTotalForceBodyNewtons());
		}

		public double forceBodyResidualFraction() {
			return ratio(forceBodyResidualNewtons().length(), referenceTotalForceBodyNewtons().length());
		}

		public Vec3 torqueBodyResidualNewtonMeters() {
			return actualTotalTorqueBodyNewtonMeters.subtract(referenceTotalTorqueBodyNewtonMeters());
		}

		public double torqueBodyResidualFraction() {
			return ratio(
					torqueBodyResidualNewtonMeters().length(),
					referenceTotalTorqueBodyNewtonMeters().length()
			);
		}

		public double thrustResidualNewtons() {
			return actualTotalThrustNewtons - referenceTotalThrustNewtons();
		}

		public double thrustResidualFraction() {
			return ratio(thrustResidualNewtons(), referenceTotalThrustNewtons());
		}

		public double shaftPowerResidualWatts() {
			return actualTotalShaftPowerWatts - referenceTotalShaftPowerWatts();
		}

		public double shaftPowerResidualFraction() {
			return ratio(shaftPowerResidualWatts(), referenceTotalShaftPowerWatts());
		}

		public double shaftTorqueResidualNewtonMeters() {
			return actualTotalShaftTorqueNewtonMeters - referenceTotalShaftTorqueNewtonMeters();
		}

		public double shaftTorqueResidualFraction() {
			return ratio(shaftTorqueResidualNewtonMeters(), referenceTotalShaftTorqueNewtonMeters());
		}

		public double maxAbsoluteResidualFraction() {
			double max = Math.max(forceBodyResidualFraction(), torqueBodyResidualFraction());
			max = Math.max(max, Math.abs(thrustResidualFraction()));
			max = Math.max(max, Math.abs(shaftPowerResidualFraction()));
			return Math.max(max, Math.abs(shaftTorqueResidualFraction()));
		}

		public Vec3 runtimeReplacementForceBodyResidualNewtons() {
			return actualTotalForceBodyNewtons.subtract(runtimeReplacementReferenceTotalForceBodyNewtons());
		}

		public double runtimeReplacementForceBodyResidualFraction() {
			return ratio(
					runtimeReplacementForceBodyResidualNewtons().length(),
					runtimeReplacementReferenceTotalForceBodyNewtons().length()
			);
		}

		public Vec3 runtimeReplacementTorqueBodyResidualNewtonMeters() {
			return actualTotalTorqueBodyNewtonMeters.subtract(
					runtimeReplacementReferenceTotalTorqueBodyNewtonMeters()
			);
		}

		public double runtimeReplacementTorqueBodyResidualFraction() {
			return ratio(
					runtimeReplacementTorqueBodyResidualNewtonMeters().length(),
					runtimeReplacementReferenceTotalTorqueBodyNewtonMeters().length()
			);
		}

		public double runtimeReplacementThrustResidualNewtons() {
			return actualTotalThrustNewtons - runtimeReplacementReferenceTotalThrustNewtons();
		}

		public double runtimeReplacementThrustResidualFraction() {
			return ratio(runtimeReplacementThrustResidualNewtons(),
					runtimeReplacementReferenceTotalThrustNewtons());
		}

		public double runtimeReplacementShaftPowerResidualWatts() {
			return actualTotalShaftPowerWatts - runtimeReplacementReferenceTotalShaftPowerWatts();
		}

		public double runtimeReplacementShaftPowerResidualFraction() {
			return ratio(runtimeReplacementShaftPowerResidualWatts(),
					runtimeReplacementReferenceTotalShaftPowerWatts());
		}

		public double runtimeReplacementShaftTorqueResidualNewtonMeters() {
			return actualTotalShaftTorqueNewtonMeters
					- runtimeReplacementReferenceTotalShaftTorqueNewtonMeters();
		}

		public double runtimeReplacementShaftTorqueResidualFraction() {
			return ratio(runtimeReplacementShaftTorqueResidualNewtonMeters(),
					runtimeReplacementReferenceTotalShaftTorqueNewtonMeters());
		}

		public double runtimeReplacementMaxAbsoluteResidualFraction() {
			double max = Math.max(
					runtimeReplacementForceBodyResidualFraction(),
					runtimeReplacementTorqueBodyResidualFraction()
			);
			max = Math.max(max, Math.abs(runtimeReplacementThrustResidualFraction()));
			max = Math.max(max, Math.abs(runtimeReplacementShaftPowerResidualFraction()));
			return Math.max(max, Math.abs(runtimeReplacementShaftTorqueResidualFraction()));
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

	private static Vec3 sumActuatorDiskSurfaceForce(
			List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			sum = sum.add(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter()
					.multiply(sourceTerm.diskAreaSquareMeters()));
		}
		return sum;
	}

	private static Vec3 sumActuatorDiskWakeAngularMomentumTorque(
			List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			sum = sum.add(sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters());
		}
		return sum;
	}

	private static Vec3 sumActuatorDiskWakeAngularMomentumTorqueResidual(
			List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			sum = sum.add(sourceTerm.wakeAngularMomentumTorqueResidualWorldNewtonMeters());
		}
		return sum;
	}

	private static double sumActuatorDiskScalar(
			List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms,
			ToDoubleFunction<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> scalar
	) {
		double sum = 0.0;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			double value = scalar.applyAsDouble(sourceTerm);
			if (Double.isFinite(value)) {
				sum += value;
			}
		}
		return finiteOrZero(sum);
	}

	private static Vec3 sum(Vec3[] values) {
		Vec3 sum = Vec3.ZERO;
		if (values == null) {
			return sum;
		}
		for (Vec3 value : values) {
			sum = sum.add(finiteVecOrZero(value));
		}
		return sum;
	}

	private static double sum(double[] values) {
		double sum = 0.0;
		if (values == null) {
			return sum;
		}
		for (double value : values) {
			if (Double.isFinite(value)) {
				sum += Math.max(0.0, value);
			}
		}
		return sum;
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
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
