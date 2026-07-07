package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJWorldForceApplicationProviderTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void providerBuildsWorldForceApplicationsFromWorldKinematics() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double[] omegas = fill(config.rotors().size(), hoverOmega);
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, 3.0, 0.0);
		Vec3 angularVelocityBody = new Vec3(4.0, 0.0, 0.0);
		Vec3 positionWorld = new Vec3(2.0, 65.0, -1.5);
		Vec3 velocityWorld = new Vec3(1.25, -0.20, 0.35);
		double dt = 0.0125;
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.25)
		);
		Vec3 momentReferenceWorld = new Vec3(20.0, 80.0, -7.5);
		Vec3 vehicleVelocityWorld = bodyToWorld.rotate(bodyRelativeAirVelocity);

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample sample =
				PropellerArchiveCtCpJWorldForceApplicationProvider
						.sampleStaticAnchoredConfigurationFromWorldKinematics(
								"apDrone",
								"provider_world_kinematics",
								config,
								momentReferenceWorld,
								bodyToWorld,
								vehicleVelocityWorld,
								angularVelocityBody,
								Vec3.ZERO,
								null,
								omegas,
								RHO,
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
						);

		assertEquals(config.rotors().size(), sample.rotorCount());
		assertEquals(config.rotors().size(), sample.sourceTermCount());
		assertEquals(config.rotors().size(), sample.appliedRotorCount());
		assertEquals(config.rotors().size(), sample.appliedSourceTermCount());
		assertEquals(config.rotors().size(), sample.runtimeReplacementAppliedRotorCount());
		assertEquals(config.rotors().size(), sample.runtimeReplacementAppliedSourceTermCount());
		assertTrue(sample.runtimeReplacementAccepted());
		assertEquals(config.rotors().size(), sample.aggregate().acceptedRotorCount());
		assertEquals(config.rotors().size(), sample.aggregate().runtimeForceReplacementAcceptedRotorCount());
		assertVectorEquals(sample.aggregate().totalThrustForceWorldNewtons(bodyToWorld),
				sample.totalThrustForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(sample.aggregate().totalReactionTorqueWorldNewtonMeters(bodyToWorld),
				sample.totalReactionTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.aggregate().totalThrustMomentWorldNewtonMeters(bodyToWorld),
				sample.totalThrustMomentWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.aggregate().totalBodyTorqueWorldNewtonMeters(bodyToWorld),
				sample.totalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.aggregate().runtimeForceReplacementThrustForceWorldNewtons(bodyToWorld),
				sample.runtimeReplacementTotalThrustForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(sample.aggregate().runtimeForceReplacementReactionTorqueWorldNewtonMeters(bodyToWorld),
				sample.runtimeReplacementTotalReactionTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.aggregate().runtimeForceReplacementThrustMomentWorldNewtonMeters(bodyToWorld),
				sample.runtimeReplacementTotalThrustMomentWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.aggregate().runtimeForceReplacementTotalBodyTorqueWorldNewtonMeters(bodyToWorld),
				sample.runtimeReplacementTotalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.totalThrustForceWorldNewtons(),
				sample.totalActuatorDiskSurfaceForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(sample.runtimeReplacementTotalThrustForceWorldNewtons(),
				sample.runtimeReplacementTotalActuatorDiskSurfaceForceWorldNewtons(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample wrench =
				sample.rotorRigidBodyWrench(config, angularVelocityBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeWrench =
				sample.runtimeReplacementRigidBodyWrench(config, angularVelocityBody);
		Vec3 expectedTorqueBody = bodyToWorld.conjugate().rotate(sample.totalTorqueWorldNewtonMeters());
		Vec3 expectedGyroscopicTorque =
				angularVelocityBody.cross(config.inertiaKgMetersSquared().multiply(angularVelocityBody));
		Vec3 expectedAngularAcceleration =
				expectedTorqueBody.subtract(expectedGyroscopicTorque).divide(config.inertiaKgMetersSquared());
		assertFalse(wrench.runtimeReplacement());
		assertTrue(runtimeWrench.runtimeReplacement());
		assertVectorEquals(sample.totalThrustForceWorldNewtons(), wrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(sample.totalTorqueWorldNewtonMeters(), wrench.totalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(expectedTorqueBody, wrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.totalThrustForceWorldNewtons().multiply(1.0 / config.massKg()),
				wrench.linearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(expectedGyroscopicTorque, wrench.gyroscopicTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(expectedAngularAcceleration,
				wrench.angularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.totalForceWorldNewtons(), runtimeWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(wrench.totalTorqueBodyNewtonMeters(), runtimeWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(wrench.linearAccelerationWorldMetersPerSecondSquared(),
				runtimeWrench.linearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.angularAccelerationBodyRadiansPerSecondSquared(),
				runtimeWrench.angularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview preview =
				sample.rotorOnlyStepPreview(config, positionWorld, velocityWorld, angularVelocityBody, dt);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview runtimePreview =
				sample.runtimeReplacementRotorOnlyStepPreview(
						config,
						positionWorld,
						velocityWorld,
						angularVelocityBody,
						dt
				);
		Vec3 expectedNextVelocity = velocityWorld.add(
				wrench.linearAccelerationWorldMetersPerSecondSquared().multiply(dt)
		);
		Vec3 expectedNextPosition = positionWorld.add(expectedNextVelocity.multiply(dt));
		Vec3 expectedNextAngularVelocity = angularVelocityBody.add(
				wrench.angularAccelerationBodyRadiansPerSecondSquared().multiply(dt)
		);
		Quaternion expectedNextOrientation =
				bodyToWorld.integrateBodyAngularVelocity(expectedNextAngularVelocity, dt);
		assertFalse(preview.runtimeReplacement());
		assertTrue(runtimePreview.runtimeReplacement());
		assertEquals(dt, preview.dtSeconds(), 1.0e-15);
		assertVectorEquals(positionWorld, preview.initialPositionWorldMeters(), 1.0e-15);
		assertVectorEquals(velocityWorld, preview.initialVelocityWorldMetersPerSecond(), 1.0e-15);
		assertQuaternionEquals(bodyToWorld, preview.initialBodyToWorldOrientation(), 1.0e-15);
		assertVectorEquals(angularVelocityBody, preview.initialAngularVelocityBodyRadiansPerSecond(), 1.0e-15);
		assertVectorEquals(expectedNextVelocity, preview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(expectedNextPosition, preview.nextPositionWorldMeters(), 1.0e-12);
		assertVectorEquals(expectedNextAngularVelocity,
				preview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		assertQuaternionEquals(expectedNextOrientation, preview.nextBodyToWorldOrientation(), 1.0e-12);
		assertVectorEquals(preview.nextPositionWorldMeters(), runtimePreview.nextPositionWorldMeters(), 1.0e-12);
		assertVectorEquals(preview.nextVelocityWorldMetersPerSecond(),
				runtimePreview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertQuaternionEquals(preview.nextBodyToWorldOrientation(),
				runtimePreview.nextBodyToWorldOrientation(), 1.0e-12);
		assertVectorEquals(preview.nextAngularVelocityBodyRadiansPerSecond(),
				runtimePreview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		for (int i = 0; i < sample.rotorApplications().size(); i++) {
			PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application =
					sample.rotorApplications().get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
					sample.rotorActuatorDiskSourceTerms().get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample runtimeSourceTerm =
					sample.runtimeReplacementRotorActuatorDiskSourceTerms().get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample rotorSample =
					sample.aggregate().rotorSamples().get(i);
			assertEquals(i, application.rotorIndex());
			assertEquals(i, sourceTerm.rotorIndex());
			assertTrue(application.applied());
			assertTrue(sourceTerm.applied());
			assertVectorEquals(
					momentReferenceWorld.add(rotorSample.momentArmWorldMeters(bodyToWorld)),
					application.forceApplicationPointWorldMeters(),
					1.0e-15
			);
			assertVectorEquals(application.forceApplicationPointWorldMeters(),
					sourceTerm.diskCenterWorldMeters(), 1.0e-15);
			assertVectorEquals(application.thrustForceWorldNewtons(),
					sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter()
							.multiply(sourceTerm.diskAreaSquareMeters()),
					1.0e-12);
			assertVectorEquals(rotorSample.farWakeAxialVelocityWorldMetersPerSecond(bodyToWorld),
					sourceTerm.farWakeAxialVelocityWorldMetersPerSecond(), 1.0e-15);
			assertTrue(runtimeSourceTerm.applied());
			assertVectorEquals(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(),
					runtimeSourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(), 1.0e-12);
			assertVectorEquals(
					application.forceApplicationPointWorldMeters()
							.subtract(momentReferenceWorld)
							.cross(application.thrustForceWorldNewtons()),
					application.thrustMomentWorldNewtonMeters(),
					1.0e-12
			);
		}
	}

	@Test
	void providerSamplesStateAndEnvironmentWithoutMutatingRuntimeState() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double[] omegas = fill(config.rotors().size(), hoverOmega);
		DroneState state = new DroneState(config.rotors().size());
		Vec3 positionWorld = new Vec3(-3.5, 71.25, 8.0);
		Vec3 angularVelocityBody = new Vec3(0.4, -0.2, 0.15);
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.125),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.125)
		);
		Vec3 baselineWindWorld = new Vec3(0.3, -0.1, 0.2);
		Vec3 targetRelativeAirBody = new Vec3(0.0, 2.0, 0.5);
		Vec3 velocityWorld = baselineWindWorld.add(bodyToWorld.rotate(targetRelativeAirBody));
		DroneEnvironment environment =
				new DroneEnvironment(baselineWindWorld, 0.92, Double.POSITIVE_INFINITY);
		for (int i = 0; i < config.rotors().size(); i++) {
			state.setMotorOmegaRadiansPerSecond(i, omegas[i]);
		}
		state.setPositionMeters(positionWorld);
		state.setVelocityMetersPerSecond(velocityWorld);
		state.setOrientation(bodyToWorld);
		state.setAngularVelocityBodyRadiansPerSecond(angularVelocityBody);

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample fromState =
				PropellerArchiveCtCpJWorldForceApplicationProvider.sampleStaticAnchoredConfigurationFromState(
						"apDrone",
						"provider_state_environment",
						config,
						state,
						environment,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample direct =
				PropellerArchiveCtCpJWorldForceApplicationProvider
						.sampleStaticAnchoredConfigurationFromWorldKinematics(
								"apDrone",
								"provider_state_environment",
								config,
								positionWorld,
								bodyToWorld,
								velocityWorld,
								angularVelocityBody,
								environment.windVelocityWorldMetersPerSecond(),
								environment.rotorWindVelocityWorldMetersPerSecond(),
								omegas,
								RHO * environment.effectiveAirDensityRatio(),
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
								environment.effectiveAmbientTemperatureCelsius(),
								environment.ambientHumidity()
						);

		assertEquals(direct.rotorCount(), fromState.rotorCount());
		assertEquals(direct.sourceTermCount(), fromState.sourceTermCount());
		assertEquals(direct.appliedRotorCount(), fromState.appliedRotorCount());
		assertEquals(direct.appliedSourceTermCount(), fromState.appliedSourceTermCount());
		assertEquals(direct.runtimeReplacementAppliedRotorCount(),
				fromState.runtimeReplacementAppliedRotorCount());
		assertEquals(direct.runtimeReplacementAppliedSourceTermCount(),
				fromState.runtimeReplacementAppliedSourceTermCount());
		assertEquals(direct.runtimeReplacementAccepted(), fromState.runtimeReplacementAccepted());
		assertVectorEquals(direct.momentReferenceWorldMeters(), fromState.momentReferenceWorldMeters(), 1.0e-15);
		assertQuaternionEquals(direct.bodyToWorldOrientation(), fromState.bodyToWorldOrientation(), 1.0e-15);
		assertVectorEquals(direct.totalThrustForceWorldNewtons(),
				fromState.totalThrustForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(direct.totalReactionTorqueWorldNewtonMeters(),
				fromState.totalReactionTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(direct.totalThrustMomentWorldNewtonMeters(),
				fromState.totalThrustMomentWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(direct.totalTorqueWorldNewtonMeters(),
				fromState.totalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(direct.totalActuatorDiskSurfaceForceWorldNewtons(),
				fromState.totalActuatorDiskSurfaceForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(fromState.totalThrustForceWorldNewtons(),
				fromState.totalActuatorDiskSurfaceForceWorldNewtons(), 1.0e-12);
		assertTrue(fromState.totalThrustForceWorldNewtons().length() > 0.0);
		assertVectorEquals(
				direct.rotorRigidBodyWrench(config, angularVelocityBody)
						.angularAccelerationBodyRadiansPerSecondSquared(),
				fromState.rotorRigidBodyWrench(config, angularVelocityBody)
						.angularAccelerationBodyRadiansPerSecondSquared(),
				1.0e-12
		);
		assertVectorEquals(positionWorld, state.positionMeters(), 1.0e-15);
		assertVectorEquals(velocityWorld, state.velocityMetersPerSecond(), 1.0e-15);
		assertQuaternionEquals(bodyToWorld, state.orientation(), 1.0e-15);
		assertVectorEquals(angularVelocityBody, state.angularVelocityBodyRadiansPerSecond(), 1.0e-15);
		for (int i = 0; i < omegas.length; i++) {
			assertEquals(omegas[i], state.motorOmegaRadiansPerSecond(i), 1.0e-15);
		}
	}

	@Test
	void providerComparesStateRotorTelemetryToCtCpJReferenceWithoutMutatingRuntimeState() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double[] omegas = fill(config.rotors().size(), hoverOmega);
		DroneState state = new DroneState(config.rotors().size());
		Vec3 positionWorld = new Vec3(1.0, 70.0, -2.0);
		Vec3 angularVelocityBody = new Vec3(1.2, 0.0, 0.0);
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI / 6.0),
				0.0,
				0.0,
				Math.sin(Math.PI / 6.0)
		);
		Vec3 baselineWindWorld = new Vec3(0.35, -0.10, 0.25);
		Vec3 targetRelativeAirBody = rotor.thrustAxisBody().multiply(3.0);
		Vec3 velocityWorld = baselineWindWorld.add(bodyToWorld.rotate(targetRelativeAirBody));
		DroneEnvironment environment =
				new DroneEnvironment(baselineWindWorld, 1.0, Double.POSITIVE_INFINITY);
		for (int i = 0; i < config.rotors().size(); i++) {
			state.setMotorOmegaRadiansPerSecond(i, omegas[i]);
		}
		state.setPositionMeters(positionWorld);
		state.setVelocityMetersPerSecond(velocityWorld);
		state.setOrientation(bodyToWorld);
		state.setAngularVelocityBodyRadiansPerSecond(angularVelocityBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample reference =
				PropellerArchiveCtCpJWorldForceApplicationProvider.sampleStaticAnchoredConfigurationFromState(
						"apDrone",
						"provider_state_residual_reference",
						config,
						state,
						environment,
						omegas,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		copyReferenceRotorTelemetryIntoState(state, reference.aggregate());

		PropellerArchiveCtCpJWorldForceApplicationProvider.StateRotorTelemetryComparisonSample aligned =
				PropellerArchiveCtCpJWorldForceApplicationProvider.compareStateRotorTelemetryToReference(
						"apDrone",
						"provider_state_residual_reference",
						config,
						state,
						environment,
						omegas,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(config.rotors().size(), aligned.actualRotorCount());
		assertEquals(config.rotors().size(), aligned.referenceAggregate().acceptedRotorCount());
		assertEquals(config.rotors().size(),
				aligned.referenceAggregate().runtimeForceReplacementAcceptedRotorCount());
		assertVectorEquals(Vec3.ZERO, aligned.forceBodyResidualNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aligned.torqueBodyResidualNewtonMeters(), 1.0e-12);
		assertEquals(0.0, aligned.thrustResidualNewtons(), 1.0e-12);
		assertEquals(0.0, aligned.shaftPowerResidualWatts(), 1.0e-12);
		assertEquals(0.0, aligned.shaftTorqueResidualNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aligned.runtimeReplacementForceBodyResidualNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aligned.runtimeReplacementTorqueBodyResidualNewtonMeters(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementThrustResidualNewtons(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementShaftPowerResidualWatts(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementShaftTorqueResidualNewtonMeters(), 1.0e-12);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample first =
				reference.aggregate().rotorSamples().get(0);
		state.setRotorThrustNewtons(0, first.thrustNewtons() + 0.25);
		state.setRotorForceBodyNewtons(0, first.thrustForceBodyNewtons().add(new Vec3(0.0, 0.25, 0.0)));
		state.setRotorTorqueBodyNewtonMeters(0,
				first.totalTorqueBodyNewtonMeters().add(new Vec3(0.010, 0.0, 0.0)));
		state.setMotorShaftPowerWatts(0, first.shaftPowerWatts() + 2.0);
		state.setMotorAerodynamicTorqueNewtonMeters(0, first.shaftTorqueNewtonMeters() + 0.003);

		PropellerArchiveCtCpJWorldForceApplicationProvider.StateRotorTelemetryComparisonSample offset =
				PropellerArchiveCtCpJWorldForceApplicationProvider.compareStateRotorTelemetryToReference(
						"apDrone",
						"provider_state_residual_reference",
						config,
						state,
						environment,
						omegas,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertVectorEquals(new Vec3(0.0, 0.25, 0.0), offset.forceBodyResidualNewtons(), 1.0e-12);
		assertVectorEquals(new Vec3(0.010, 0.0, 0.0), offset.torqueBodyResidualNewtonMeters(), 1.0e-12);
		assertEquals(0.25, offset.thrustResidualNewtons(), 1.0e-12);
		assertEquals(2.0, offset.shaftPowerResidualWatts(), 1.0e-12);
		assertEquals(0.003, offset.shaftTorqueResidualNewtonMeters(), 1.0e-12);
		assertVectorEquals(positionWorld, state.positionMeters(), 1.0e-15);
		assertVectorEquals(velocityWorld, state.velocityMetersPerSecond(), 1.0e-15);
		assertQuaternionEquals(bodyToWorld, state.orientation(), 1.0e-15);
		assertVectorEquals(angularVelocityBody, state.angularVelocityBodyRadiansPerSecond(), 1.0e-15);
	}

	@Test
	void runtimeReplacementApplicationsZeroClampRejectedRotorForces() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;
		double[] omegas = fill(config.rotors().size(), omega);
		Vec3 momentReferenceWorld = new Vec3(-4.0, 72.0, 11.0);
		Vec3 reverseVehicleVelocityWorld = rotor.thrustAxisBody().multiply(-4.5);
		Vec3 positionWorld = new Vec3(3.0, 66.0, 1.0);
		Vec3 velocityWorld = new Vec3(0.20, 0.10, -0.30);
		double dt = 0.02;

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample sample =
				PropellerArchiveCtCpJWorldForceApplicationProvider
						.sampleStaticAnchoredConfigurationFromWorldKinematics(
								"apDrone",
								"provider_reverse_clamp",
								config,
								momentReferenceWorld,
								Quaternion.IDENTITY,
								reverseVehicleVelocityWorld,
								Vec3.ZERO,
								Vec3.ZERO,
								null,
								omegas,
								RHO,
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE
						);

		assertEquals(config.rotors().size(), sample.rotorCount());
		assertEquals(config.rotors().size(), sample.sourceTermCount());
		assertEquals(config.rotors().size(), sample.appliedRotorCount());
		assertEquals(config.rotors().size(), sample.appliedSourceTermCount());
		assertEquals(0, sample.runtimeReplacementAppliedRotorCount());
		assertEquals(0, sample.runtimeReplacementAppliedSourceTermCount());
		assertFalse(sample.runtimeReplacementAccepted());
		assertEquals(config.rotors().size(), sample.aggregate().clampedRotorCount());
		assertTrue(sample.aggregate().totalThrustForceBodyNewtons().length() > 0.0);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalThrustForceWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalReactionTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalThrustMomentWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(sample.totalThrustForceWorldNewtons(),
				sample.totalActuatorDiskSurfaceForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalActuatorDiskSurfaceForceWorldNewtons(), 1.0e-15);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample rawWrench =
				sample.rotorRigidBodyWrench(config, Vec3.ZERO);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeWrench =
				sample.runtimeReplacementRigidBodyWrench(config, Vec3.ZERO);
		assertTrue(rawWrench.totalForceWorldNewtons().length() > 0.0);
		assertTrue(rawWrench.linearAccelerationWorldMetersPerSecondSquared().length() > 0.0);
		assertVectorEquals(Vec3.ZERO, runtimeWrench.totalForceWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeWrench.totalTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeWrench.totalTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeWrench.linearAccelerationWorldMetersPerSecondSquared(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeWrench.angularAccelerationBodyRadiansPerSecondSquared(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeWrench.gyroscopicTorqueBodyNewtonMeters(), 1.0e-15);
		assertTrue(runtimeWrench.runtimeReplacement());
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview rawPreview =
				sample.rotorOnlyStepPreview(config, positionWorld, velocityWorld, Vec3.ZERO, dt);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview runtimePreview =
				sample.runtimeReplacementRotorOnlyStepPreview(config, positionWorld, velocityWorld, Vec3.ZERO, dt);
		assertTrue(rawPreview.nextVelocityWorldMetersPerSecond().subtract(velocityWorld).length() > 0.0);
		assertVectorEquals(velocityWorld, runtimePreview.nextVelocityWorldMetersPerSecond(), 1.0e-15);
		assertVectorEquals(positionWorld.add(velocityWorld.multiply(dt)),
				runtimePreview.nextPositionWorldMeters(), 1.0e-15);
		assertQuaternionEquals(Quaternion.IDENTITY, runtimePreview.nextBodyToWorldOrientation(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimePreview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-15);
		for (int i = 0; i < sample.runtimeReplacementRotorApplications().size(); i++) {
			PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application =
					sample.runtimeReplacementRotorApplications().get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
					sample.rotorActuatorDiskSourceTerms().get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample runtimeSourceTerm =
					sample.runtimeReplacementRotorActuatorDiskSourceTerms().get(i);
			assertEquals(i, application.rotorIndex());
			assertFalse(application.applied());
			assertFalse(application.runtimeForceReplacementAccepted());
			assertEquals("CLAMPED", application.lookupStatus());
			assertTrue(sourceTerm.applied());
			assertFalse(runtimeSourceTerm.applied());
			assertEquals("CLAMPED", runtimeSourceTerm.lookupStatus());
			assertVectorEquals(
					sample.rotorApplications().get(i).forceApplicationPointWorldMeters(),
					application.forceApplicationPointWorldMeters(),
					1.0e-15
			);
			assertVectorEquals(sourceTerm.diskCenterWorldMeters(), runtimeSourceTerm.diskCenterWorldMeters(), 1.0e-15);
			assertTrue(sourceTerm.pressureJumpPascals() > 0.0);
			assertEquals(0.0, runtimeSourceTerm.pressureJumpPascals(), 1.0e-15);
			assertVectorEquals(Vec3.ZERO, application.thrustForceWorldNewtons(), 1.0e-15);
			assertVectorEquals(Vec3.ZERO, application.totalTorqueWorldNewtonMeters(), 1.0e-15);
			assertVectorEquals(Vec3.ZERO,
					runtimeSourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(), 1.0e-15);
		}
	}

	private static double[] fill(int count, double value) {
		double[] values = new double[count];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
	}

	private static void copyReferenceRotorTelemetryIntoState(
			DroneState state,
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		for (int i = 0; i < aggregate.rotorSamples().size(); i++) {
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
					aggregate.rotorSamples().get(i);
			state.setRotorThrustNewtons(i, sample.thrustNewtons());
			state.setRotorForceBodyNewtons(i, sample.thrustForceBodyNewtons());
			state.setRotorTorqueBodyNewtonMeters(i, sample.totalTorqueBodyNewtonMeters());
			state.setMotorShaftPowerWatts(i, sample.shaftPowerWatts());
			state.setMotorAerodynamicTorqueNewtonMeters(i, sample.shaftTorqueNewtonMeters());
		}
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}

	private static void assertQuaternionEquals(Quaternion expected, Quaternion actual, double tolerance) {
		assertEquals(expected.w(), actual.w(), tolerance);
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}
}
