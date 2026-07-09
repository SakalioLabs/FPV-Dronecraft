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
		assertVectorEquals(sample.aggregate().totalWakeAngularMomentumTorqueWorldNewtonMeters(bodyToWorld),
				sample.totalActuatorDiskWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(sample.aggregate().totalWakeAngularMomentumTorqueResidualWorldNewtonMeters(bodyToWorld),
				sample.totalActuatorDiskWakeAngularMomentumTorqueResidualWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(
				sample.aggregate().runtimeForceReplacementWakeAngularMomentumTorqueWorldNewtonMeters(bodyToWorld),
				sample.runtimeReplacementTotalActuatorDiskWakeAngularMomentumTorqueWorldNewtonMeters(),
				1.0e-12
		);
		assertVectorEquals(
				sample.aggregate()
						.runtimeForceReplacementWakeAngularMomentumTorqueResidualWorldNewtonMeters(bodyToWorld),
				sample.runtimeReplacementTotalActuatorDiskWakeAngularMomentumTorqueResidualWorldNewtonMeters(),
				1.0e-12
		);
		assertEquals(sample.aggregate().totalDiskMassFlowKilogramsPerSecond(),
				sample.totalActuatorDiskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(sample.aggregate().totalIdealMomentumPowerWatts(),
				sample.totalActuatorDiskIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().totalWakeSwirlKineticPowerWatts(),
				sample.totalActuatorDiskWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().totalWakeKineticPowerWatts(),
				sample.totalActuatorDiskWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().totalWakeKineticPowerResidualWatts(),
				sample.totalActuatorDiskWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(sample.aggregate().runtimeForceReplacementDiskMassFlowKilogramsPerSecond(),
				sample.runtimeReplacementTotalActuatorDiskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(sample.aggregate().runtimeForceReplacementIdealMomentumPowerWatts(),
				sample.runtimeReplacementTotalActuatorDiskIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().runtimeForceReplacementWakeSwirlKineticPowerWatts(),
				sample.runtimeReplacementTotalActuatorDiskWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().runtimeForceReplacementWakeKineticPowerWatts(),
				sample.runtimeReplacementTotalActuatorDiskWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().runtimeForceReplacementWakeKineticPowerResidualWatts(),
				sample.runtimeReplacementTotalActuatorDiskWakeKineticPowerResidualWatts(), 1.0e-12);
		assertTrue(sample.totalActuatorDiskMassFlowKilogramsPerSecond() > 0.0);
		assertTrue(sample.totalActuatorDiskWakeKineticPowerWatts()
				>= sample.totalActuatorDiskIdealMomentumPowerWatts());
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample wrench =
				sample.rotorRigidBodyWrench(config, angularVelocityBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeWrench =
				sample.runtimeReplacementRigidBodyWrench(config, angularVelocityBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample gravityWrench =
				sample.rotorGravityRigidBodyWrench(config, angularVelocityBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeGravityWrench =
				sample.runtimeReplacementGravityRigidBodyWrench(config, angularVelocityBody);
		double[] previousOmegas = omegas.clone();
		previousOmegas[0] = Math.max(0.0, previousOmegas[0] - 120.0);
		RotorInertiaTorqueModel.ConfigurationRotorInertiaTorqueSample inertiaSample =
				sample.rotorInertiaTorqueSample(
						config,
						previousOmegas,
						omegas,
						angularVelocityBody,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample transientWrench =
				sample.rotorGravityTransientRigidBodyWrench(
						config,
						previousOmegas,
						omegas,
						angularVelocityBody,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeTransientWrench =
				sample.runtimeReplacementRotorGravityTransientRigidBodyWrench(
						config,
						previousOmegas,
						omegas,
						angularVelocityBody,
						dt
				);
		double separatedFlowStateIntensity = 0.55;
		AirframeDragForceModel.AirframeDragForceSample airframeDragSample =
				sample.steadyAirframeDragSample(config, separatedFlowStateIntensity);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample transientDragWrench =
				sample.rotorGravityTransientTranslationalDragRigidBodyWrench(
						config,
						previousOmegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeTransientDragWrench =
				sample.runtimeReplacementRotorGravityTransientTranslationalDragRigidBodyWrench(
						config,
						previousOmegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		Vec3 expectedTorqueBody = bodyToWorld.conjugate().rotate(sample.totalTorqueWorldNewtonMeters());
		Vec3 expectedGyroscopicTorque =
				angularVelocityBody.cross(config.inertiaKgMetersSquared().multiply(angularVelocityBody));
		Vec3 expectedAngularAcceleration =
				expectedTorqueBody.subtract(expectedGyroscopicTorque).divide(config.inertiaKgMetersSquared());
		Vec3 expectedTransientTorqueBody = gravityWrench.totalTorqueBodyNewtonMeters()
				.add(inertiaSample.totalReactionTorqueBodyNewtonMeters());
		Vec3 expectedTransientAngularAcceleration = expectedTransientTorqueBody
				.subtract(expectedGyroscopicTorque)
				.divide(config.inertiaKgMetersSquared());
		Vec3 gravityForceWorld = new Vec3(
				0.0,
				-config.massKg() * config.gravityMetersPerSecondSquared(),
				0.0
		);
		Vec3 gravityAccelerationWorld = new Vec3(0.0, -config.gravityMetersPerSecondSquared(), 0.0);
		Vec3 airframeDragForceWorld = bodyToWorld.rotate(airframeDragSample.totalDragForceBodyNewtons());
		assertVectorEquals(bodyRelativeAirVelocity,
				sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-12);
		assertEquals(RHO, sample.airDensityKgPerCubicMeter(), 1.0e-15);
		assertEquals(1.0, sample.airDensityRatio(), 1.0e-15);
		assertTrue(airframeDragSample.totalDragForceBodyNewtons().dot(bodyRelativeAirVelocity) < 0.0);
		assertVectorEquals(airframeDragSample.totalDragForceWorldNewtons(bodyToWorld),
				airframeDragForceWorld, 1.0e-12);
		assertFalse(wrench.runtimeReplacement());
		assertTrue(runtimeWrench.runtimeReplacement());
		assertFalse(gravityWrench.runtimeReplacement());
		assertTrue(runtimeGravityWrench.runtimeReplacement());
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
		assertVectorEquals(wrench.totalForceWorldNewtons().add(gravityForceWorld),
				gravityWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(wrench.totalTorqueWorldNewtonMeters(),
				gravityWrench.totalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(wrench.totalTorqueBodyNewtonMeters(),
				gravityWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(wrench.linearAccelerationWorldMetersPerSecondSquared().add(gravityAccelerationWorld),
				gravityWrench.linearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.angularAccelerationBodyRadiansPerSecondSquared(),
				gravityWrench.angularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(gravityWrench.totalForceWorldNewtons(),
				runtimeGravityWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(gravityWrench.totalTorqueBodyNewtonMeters(),
				runtimeGravityWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(gravityWrench.linearAccelerationWorldMetersPerSecondSquared(),
				runtimeGravityWrench.linearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(gravityWrench.angularAccelerationBodyRadiansPerSecondSquared(),
				runtimeGravityWrench.angularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertTrue(inertiaSample.totalReactionTorqueBodyNewtonMeters().length() > 0.0);
		assertVectorEquals(Vec3.ZERO,
				inertiaSample.totalGyroscopicReactionTorqueBodyNewtonMeters(), 1.0e-12);
		assertFalse(transientWrench.runtimeReplacement());
		assertTrue(runtimeTransientWrench.runtimeReplacement());
		assertVectorEquals(gravityWrench.totalForceWorldNewtons(),
				transientWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(expectedTransientTorqueBody,
				transientWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(expectedTransientAngularAcceleration,
				transientWrench.angularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(transientWrench.totalForceWorldNewtons(),
				runtimeTransientWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(transientWrench.totalTorqueBodyNewtonMeters(),
				runtimeTransientWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(transientWrench.angularAccelerationBodyRadiansPerSecondSquared(),
				runtimeTransientWrench.angularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertFalse(transientDragWrench.runtimeReplacement());
		assertTrue(runtimeTransientDragWrench.runtimeReplacement());
		assertVectorEquals(transientWrench.totalForceWorldNewtons().add(airframeDragForceWorld),
				transientDragWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(transientWrench.totalTorqueWorldNewtonMeters(),
				transientDragWrench.totalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(
				transientWrench.linearAccelerationWorldMetersPerSecondSquared().add(
						airframeDragForceWorld.multiply(1.0 / config.massKg())
				),
				transientDragWrench.linearAccelerationWorldMetersPerSecondSquared(),
				1.0e-12
		);
		assertVectorEquals(transientDragWrench.totalForceWorldNewtons(),
				runtimeTransientDragWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(transientDragWrench.totalTorqueBodyNewtonMeters(),
				runtimeTransientDragWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
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
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview gravityPreview =
				sample.rotorGravityStepPreview(config, positionWorld, velocityWorld, angularVelocityBody, dt);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview runtimeGravityPreview =
				sample.runtimeReplacementRotorGravityStepPreview(
						config,
						positionWorld,
						velocityWorld,
						angularVelocityBody,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview transientPreview =
				sample.rotorGravityTransientStepPreview(
						config,
						positionWorld,
						velocityWorld,
						angularVelocityBody,
						previousOmegas,
						omegas,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview runtimeTransientPreview =
				sample.runtimeReplacementRotorGravityTransientStepPreview(
						config,
						positionWorld,
						velocityWorld,
						angularVelocityBody,
						previousOmegas,
						omegas,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview transientDragPreview =
				sample.rotorGravityTransientTranslationalDragStepPreview(
						config,
						positionWorld,
						velocityWorld,
						angularVelocityBody,
						previousOmegas,
						omegas,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview runtimeTransientDragPreview =
				sample.runtimeReplacementRotorGravityTransientTranslationalDragStepPreview(
						config,
						positionWorld,
						velocityWorld,
						angularVelocityBody,
						previousOmegas,
						omegas,
						separatedFlowStateIntensity,
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
		Vec3 expectedGravityNextVelocity = velocityWorld.add(
				gravityWrench.linearAccelerationWorldMetersPerSecondSquared().multiply(dt)
		);
		Vec3 expectedGravityNextPosition = positionWorld.add(expectedGravityNextVelocity.multiply(dt));
		assertFalse(preview.runtimeReplacement());
		assertTrue(runtimePreview.runtimeReplacement());
		assertFalse(gravityPreview.runtimeReplacement());
		assertTrue(runtimeGravityPreview.runtimeReplacement());
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
		assertVectorEquals(expectedGravityNextVelocity,
				gravityPreview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(expectedGravityNextPosition,
				gravityPreview.nextPositionWorldMeters(), 1.0e-12);
		assertQuaternionEquals(expectedNextOrientation,
				gravityPreview.nextBodyToWorldOrientation(), 1.0e-12);
		assertVectorEquals(expectedNextAngularVelocity,
				gravityPreview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		assertVectorEquals(gravityPreview.nextPositionWorldMeters(),
				runtimeGravityPreview.nextPositionWorldMeters(), 1.0e-12);
		assertVectorEquals(gravityPreview.nextVelocityWorldMetersPerSecond(),
				runtimeGravityPreview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertQuaternionEquals(gravityPreview.nextBodyToWorldOrientation(),
				runtimeGravityPreview.nextBodyToWorldOrientation(), 1.0e-12);
		assertVectorEquals(gravityPreview.nextAngularVelocityBodyRadiansPerSecond(),
				runtimeGravityPreview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		Vec3 expectedTransientNextAngularVelocity = angularVelocityBody.add(
				expectedTransientAngularAcceleration.multiply(dt)
		);
		assertFalse(transientPreview.runtimeReplacement());
		assertTrue(runtimeTransientPreview.runtimeReplacement());
		assertVectorEquals(gravityPreview.nextVelocityWorldMetersPerSecond(),
				transientPreview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(expectedTransientNextAngularVelocity,
				transientPreview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		assertQuaternionEquals(
				bodyToWorld.integrateBodyAngularVelocity(expectedTransientNextAngularVelocity, dt),
				transientPreview.nextBodyToWorldOrientation(),
				1.0e-12
		);
		assertVectorEquals(transientPreview.nextVelocityWorldMetersPerSecond(),
				runtimeTransientPreview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(transientPreview.nextAngularVelocityBodyRadiansPerSecond(),
				runtimeTransientPreview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		assertQuaternionEquals(transientPreview.nextBodyToWorldOrientation(),
				runtimeTransientPreview.nextBodyToWorldOrientation(), 1.0e-12);
		Vec3 expectedTransientDragNextVelocity = velocityWorld.add(
				transientDragWrench.linearAccelerationWorldMetersPerSecondSquared().multiply(dt)
		);
		assertFalse(transientDragPreview.runtimeReplacement());
		assertTrue(runtimeTransientDragPreview.runtimeReplacement());
		assertVectorEquals(expectedTransientDragNextVelocity,
				transientDragPreview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(transientPreview.nextAngularVelocityBodyRadiansPerSecond(),
				transientDragPreview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		assertVectorEquals(transientDragPreview.nextVelocityWorldMetersPerSecond(),
				runtimeTransientDragPreview.nextVelocityWorldMetersPerSecond(), 1.0e-12);
		assertQuaternionEquals(transientDragPreview.nextBodyToWorldOrientation(),
				runtimeTransientDragPreview.nextBodyToWorldOrientation(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepEnergySample energy =
				sample.rotorOnlyStepEnergySample(config, positionWorld, velocityWorld, angularVelocityBody, dt);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepEnergySample runtimeEnergy =
				sample.runtimeReplacementRotorOnlyStepEnergySample(
						config,
						positionWorld,
						velocityWorld,
						angularVelocityBody,
						dt
				);
		assertFalse(energy.runtimeReplacement());
		assertTrue(runtimeEnergy.runtimeReplacement());
		assertEquals(config.massKg(), energy.massKg(), 1.0e-15);
		assertVectorEquals(config.inertiaKgMetersSquared(), energy.inertiaKgMetersSquared(), 1.0e-15);
		assertEquals(dt, energy.dtSeconds(), 1.0e-15);
		assertEquals(sample.aggregate().totalShaftPowerWatts(), energy.totalShaftPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().totalIdealMomentumPowerWatts(),
				energy.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().totalWakeKineticPowerWatts(),
				energy.totalWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(sample.aggregate().totalWakeKineticPowerResidualWatts(),
				energy.totalWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(energy.totalShaftPowerWatts() * dt, energy.shaftEnergyJoules(), 1.0e-12);
		assertEquals(energy.totalIdealMomentumPowerWatts() * dt,
				energy.idealMomentumEnergyJoules(), 1.0e-12);
		assertEquals(energy.totalWakeKineticPowerWatts() * dt,
				energy.wakeKineticEnergyJoules(), 1.0e-12);
		assertEquals(energy.totalWakeKineticPowerResidualWatts() * dt,
				energy.wakeKineticEnergyResidualJoules(), 1.0e-12);
		assertEquals(sample.aggregate().totalIdealMomentumPowerWatts()
						/ sample.aggregate().totalShaftPowerWatts(),
				energy.idealMomentumEnergyOverShaftEnergy(), 1.0e-12);
		assertEquals(sample.aggregate().totalWakeKineticPowerOverShaftPower(),
				energy.wakeKineticEnergyOverShaftEnergy(), 1.0e-12);
		assertEquals(translationalEnergy(config.massKg(), velocityWorld),
				energy.initialTranslationalKineticEnergyJoules(), 1.0e-12);
		assertEquals(translationalEnergy(config.massKg(), expectedNextVelocity),
				energy.nextTranslationalKineticEnergyJoules(), 1.0e-12);
		assertEquals(energy.nextTranslationalKineticEnergyJoules()
						- energy.initialTranslationalKineticEnergyJoules(),
				energy.translationalKineticEnergyDeltaJoules(), 1.0e-12);
		assertEquals(rotationalEnergy(config.inertiaKgMetersSquared(), angularVelocityBody),
				energy.initialRotationalKineticEnergyJoules(), 1.0e-12);
		assertEquals(rotationalEnergy(config.inertiaKgMetersSquared(), expectedNextAngularVelocity),
				energy.nextRotationalKineticEnergyJoules(), 1.0e-12);
		assertEquals(energy.nextRotationalKineticEnergyJoules()
						- energy.initialRotationalKineticEnergyJoules(),
				energy.rotationalKineticEnergyDeltaJoules(), 1.0e-12);
		assertEquals(energy.translationalKineticEnergyDeltaJoules()
						+ energy.rotationalKineticEnergyDeltaJoules(),
				energy.rigidBodyKineticEnergyDeltaJoules(), 1.0e-12);
		Vec3 expectedAverageVelocity = velocityWorld.add(expectedNextVelocity).multiply(0.5);
		Vec3 expectedForceWorkDisplacement = expectedAverageVelocity.multiply(dt);
		Vec3 expectedAverageAngularVelocity =
				angularVelocityBody.add(expectedNextAngularVelocity).multiply(0.5);
		Vec3 expectedAngularWorkDisplacement = expectedAverageAngularVelocity.multiply(dt);
		assertVectorEquals(expectedAverageVelocity, energy.averageVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(expectedForceWorkDisplacement, energy.forceWorkDisplacementWorldMeters(), 1.0e-12);
		assertVectorEquals(expectedAverageAngularVelocity,
				energy.averageAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
		assertVectorEquals(expectedAngularWorkDisplacement,
				energy.angularWorkDisplacementBodyRadians(), 1.0e-12);
		assertEquals(wrench.totalForceWorldNewtons().dot(expectedForceWorkDisplacement),
				energy.forceWorkJoules(), 1.0e-12);
		assertEquals(wrench.totalTorqueBodyNewtonMeters().dot(expectedAngularWorkDisplacement),
				energy.bodyTorqueWorkJoules(), 1.0e-12);
		assertEquals(wrench.gyroscopicTorqueBodyNewtonMeters().dot(expectedAngularWorkDisplacement),
				energy.gyroscopicTorqueWorkJoules(), 1.0e-12);
		assertEquals(energy.forceWorkJoules() + energy.bodyTorqueWorkJoules(),
				energy.rigidBodyWorkJoules(), 1.0e-12);
		assertEquals(energy.rigidBodyWorkJoules() - energy.rigidBodyKineticEnergyDeltaJoules(),
				energy.rigidBodyWorkResidualJoules(), 1.0e-12);
		assertEquals(transitionFraction(
						Math.abs(energy.rigidBodyWorkResidualJoules()),
						Math.abs(energy.rigidBodyWorkJoules()),
						Math.abs(energy.rigidBodyKineticEnergyDeltaJoules())),
				energy.rigidBodyWorkResidualFraction(), 1.0e-12);
		assertEquals(energy.rigidBodyKineticEnergyDeltaJoules() / energy.shaftEnergyJoules(),
				energy.rigidBodyKineticEnergyDeltaOverShaftEnergy(), 1.0e-12);
		assertEquals(energy.shaftEnergyJoules(), runtimeEnergy.shaftEnergyJoules(), 1.0e-12);
		assertEquals(energy.wakeKineticEnergyJoules(), runtimeEnergy.wakeKineticEnergyJoules(), 1.0e-12);
		assertEquals(energy.rigidBodyKineticEnergyDeltaJoules(),
				runtimeEnergy.rigidBodyKineticEnergyDeltaJoules(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample alignedResidual =
				preview.residualTo(
						expectedNextPosition,
						expectedNextVelocity,
						expectedNextOrientation,
						expectedNextAngularVelocity
				);
		assertFalse(alignedResidual.runtimeReplacement());
		assertVectorEquals(Vec3.ZERO, alignedResidual.positionResidualWorldMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, alignedResidual.velocityResidualWorldMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, alignedResidual.orientationAngleResidualRadians(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				alignedResidual.angularVelocityResidualBodyRadiansPerSecond(), 1.0e-12);
		assertEquals(0.0, alignedResidual.positionResidualFraction(), 1.0e-12);
		assertEquals(0.0, alignedResidual.velocityResidualFraction(), 1.0e-12);
		assertEquals(0.0, alignedResidual.orientationResidualFraction(), 1.0e-12);
		assertEquals(0.0, alignedResidual.angularVelocityResidualFraction(), 1.0e-12);
		assertEquals(0.0, alignedResidual.maxResidualFraction(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepWrenchResidualSample
				alignedWrenchResidual = alignedResidual.equivalentExternalWrench(config);
		assertFalse(alignedWrenchResidual.runtimeReplacement());
		assertEquals(dt, alignedWrenchResidual.dtSeconds(), 1.0e-15);
		assertQuaternionEquals(bodyToWorld, alignedWrenchResidual.bodyToWorldOrientation(), 1.0e-15);
		assertVectorEquals(wrench.linearAccelerationWorldMetersPerSecondSquared(),
				alignedWrenchResidual.referenceLinearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.linearAccelerationWorldMetersPerSecondSquared(),
				alignedWrenchResidual.actualLinearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				alignedWrenchResidual.residualLinearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.totalForceWorldNewtons(),
				alignedWrenchResidual.referenceForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(wrench.totalForceWorldNewtons(),
				alignedWrenchResidual.actualForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				alignedWrenchResidual.equivalentExternalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(bodyToWorld.conjugate().rotate(wrench.totalForceWorldNewtons()),
				alignedWrenchResidual.referenceForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(bodyToWorld.conjugate().rotate(wrench.totalForceWorldNewtons()),
				alignedWrenchResidual.actualForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				alignedWrenchResidual.equivalentExternalForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(gravityForceWorld,
				alignedWrenchResidual.gravityForceWorldNewtons(config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertVectorEquals(bodyToWorld.conjugate().rotate(gravityForceWorld),
				alignedWrenchResidual.gravityForceBodyNewtons(config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertVectorEquals(gravityForceWorld.multiply(-1.0),
				alignedWrenchResidual.nonGravityExternalForceWorldNewtons(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertVectorEquals(bodyToWorld.conjugate().rotate(gravityForceWorld.multiply(-1.0)),
				alignedWrenchResidual.nonGravityExternalForceBodyNewtons(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertEquals(0.0,
				alignedWrenchResidual.equivalentExternalForceOverWeight(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertEquals(1.0,
				alignedWrenchResidual.nonGravityExternalForceOverWeight(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertVectorEquals(wrench.angularAccelerationBodyRadiansPerSecondSquared(),
				alignedWrenchResidual.referenceAngularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.angularAccelerationBodyRadiansPerSecondSquared(),
				alignedWrenchResidual.actualAngularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				alignedWrenchResidual.residualAngularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.gyroscopicTorqueBodyNewtonMeters(),
				alignedWrenchResidual.gyroscopicTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(wrench.totalTorqueBodyNewtonMeters(),
				alignedWrenchResidual.referenceTotalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(wrench.totalTorqueBodyNewtonMeters(),
				alignedWrenchResidual.actualTotalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				alignedWrenchResidual.equivalentExternalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(bodyToWorld.rotate(wrench.totalTorqueBodyNewtonMeters()),
				alignedWrenchResidual.referenceTotalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(bodyToWorld.rotate(wrench.totalTorqueBodyNewtonMeters()),
				alignedWrenchResidual.actualTotalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				alignedWrenchResidual.equivalentExternalTorqueWorldNewtonMeters(), 1.0e-12);
		assertEquals(0.0, alignedWrenchResidual.forceResidualFraction(), 1.0e-12);
		assertEquals(0.0, alignedWrenchResidual.torqueResidualFraction(), 1.0e-12);
		assertEquals(0.0, alignedWrenchResidual.maxWrenchResidualFraction(), 1.0e-12);

		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepWrenchResidualSample
				gravityWrenchResidual = preview.residualTo(
						gravityPreview.nextPositionWorldMeters(),
						gravityPreview.nextVelocityWorldMetersPerSecond(),
						gravityPreview.nextBodyToWorldOrientation(),
						gravityPreview.nextAngularVelocityBodyRadiansPerSecond()
				).equivalentExternalWrench(config);
		assertVectorEquals(gravityForceWorld,
				gravityWrenchResidual.equivalentExternalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(bodyToWorld.conjugate().rotate(gravityForceWorld),
				gravityWrenchResidual.equivalentExternalForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				gravityWrenchResidual.nonGravityExternalForceWorldNewtons(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				gravityWrenchResidual.nonGravityExternalForceBodyNewtons(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertEquals(1.0,
				gravityWrenchResidual.equivalentExternalForceOverWeight(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		assertEquals(0.0,
				gravityWrenchResidual.nonGravityExternalForceOverWeight(
						config.gravityMetersPerSecondSquared()),
				1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample
				gravityAlignedResidual = gravityPreview.residualTo(
						gravityPreview.nextPositionWorldMeters(),
						gravityPreview.nextVelocityWorldMetersPerSecond(),
						gravityPreview.nextBodyToWorldOrientation(),
						gravityPreview.nextAngularVelocityBodyRadiansPerSecond()
				);
		assertVectorEquals(Vec3.ZERO, gravityAlignedResidual.positionResidualWorldMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, gravityAlignedResidual.velocityResidualWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				gravityAlignedResidual.angularVelocityResidualBodyRadiansPerSecond(), 1.0e-12);
		assertEquals(0.0, gravityAlignedResidual.maxResidualFraction(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				gravityAlignedResidual.equivalentExternalWrench(config)
						.equivalentExternalForceWorldNewtons(), 1.0e-12);

		Vec3 positionOffset = new Vec3(0.0010, -0.0020, 0.0005);
		Vec3 velocityOffset = new Vec3(0.020, -0.010, 0.005);
		Vec3 angularVelocityOffset = new Vec3(0.003, -0.006, 0.002);
		Quaternion actualNextOrientation =
				expectedNextOrientation.integrateBodyAngularVelocity(new Vec3(0.0, 0.20, 0.0), 0.01);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample offsetResidual =
				preview.residualTo(
						expectedNextPosition.add(positionOffset),
						expectedNextVelocity.add(velocityOffset),
						actualNextOrientation,
						expectedNextAngularVelocity.add(angularVelocityOffset)
				);
		assertVectorEquals(positionOffset, offsetResidual.positionResidualWorldMeters(), 1.0e-12);
		assertVectorEquals(velocityOffset, offsetResidual.velocityResidualWorldMetersPerSecond(), 1.0e-12);
		assertVectorEquals(angularVelocityOffset,
				offsetResidual.angularVelocityResidualBodyRadiansPerSecond(), 1.0e-12);
		assertEquals(quaternionAngleBetween(expectedNextOrientation, actualNextOrientation),
				offsetResidual.orientationAngleResidualRadians(), 1.0e-12);
		assertEquals(transitionFraction(
						positionOffset.length(),
						offsetResidual.referencePositionDeltaWorldMeters().length(),
						offsetResidual.actualPositionDeltaWorldMeters().length()),
				offsetResidual.positionResidualFraction(), 1.0e-12);
		assertEquals(transitionFraction(
						velocityOffset.length(),
						offsetResidual.referenceVelocityDeltaWorldMetersPerSecond().length(),
						offsetResidual.actualVelocityDeltaWorldMetersPerSecond().length()),
				offsetResidual.velocityResidualFraction(), 1.0e-12);
		assertEquals(transitionFraction(
						offsetResidual.orientationAngleResidualRadians(),
						offsetResidual.referenceOrientationStepAngleRadians(),
						offsetResidual.actualOrientationStepAngleRadians()),
				offsetResidual.orientationResidualFraction(), 1.0e-12);
		assertEquals(transitionFraction(
						angularVelocityOffset.length(),
						offsetResidual.referenceAngularVelocityDeltaBodyRadiansPerSecond().length(),
						offsetResidual.actualAngularVelocityDeltaBodyRadiansPerSecond().length()),
				offsetResidual.angularVelocityResidualFraction(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepWrenchResidualSample
				offsetWrenchResidual = offsetResidual.equivalentExternalWrench(config);
		Vec3 expectedResidualLinearAcceleration = velocityOffset.multiply(1.0 / dt);
		Vec3 expectedExternalForce = expectedResidualLinearAcceleration.multiply(config.massKg());
		Vec3 expectedResidualAngularAcceleration = angularVelocityOffset.multiply(1.0 / dt);
		Vec3 expectedExternalTorque = config.inertiaKgMetersSquared()
				.multiply(expectedResidualAngularAcceleration);
		assertVectorEquals(expectedResidualLinearAcceleration,
				offsetWrenchResidual.residualLinearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.linearAccelerationWorldMetersPerSecondSquared()
						.add(expectedResidualLinearAcceleration),
				offsetWrenchResidual.actualLinearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(expectedExternalForce,
				offsetWrenchResidual.equivalentExternalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(wrench.totalForceWorldNewtons().add(expectedExternalForce),
				offsetWrenchResidual.actualForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(bodyToWorld.conjugate().rotate(expectedExternalForce),
				offsetWrenchResidual.equivalentExternalForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(bodyToWorld.conjugate().rotate(
						wrench.totalForceWorldNewtons().add(expectedExternalForce)),
				offsetWrenchResidual.actualForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(expectedResidualAngularAcceleration,
				offsetWrenchResidual.residualAngularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(wrench.angularAccelerationBodyRadiansPerSecondSquared()
						.add(expectedResidualAngularAcceleration),
				offsetWrenchResidual.actualAngularAccelerationBodyRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(expectedExternalTorque,
				offsetWrenchResidual.equivalentExternalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(wrench.totalTorqueBodyNewtonMeters().add(expectedExternalTorque),
				offsetWrenchResidual.actualTotalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(bodyToWorld.rotate(expectedExternalTorque),
				offsetWrenchResidual.equivalentExternalTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(bodyToWorld.rotate(wrench.totalTorqueBodyNewtonMeters()
						.add(expectedExternalTorque)),
				offsetWrenchResidual.actualTotalTorqueWorldNewtonMeters(), 1.0e-12);
		assertEquals(transitionFraction(
						expectedExternalForce.length(),
						wrench.totalForceWorldNewtons().length(),
						offsetWrenchResidual.actualForceWorldNewtons().length()),
				offsetWrenchResidual.forceResidualFraction(), 1.0e-12);
		assertEquals(transitionFraction(
						expectedExternalTorque.length(),
						wrench.totalTorqueBodyNewtonMeters().length(),
						offsetWrenchResidual.actualTotalTorqueBodyNewtonMeters().length()),
				offsetWrenchResidual.torqueResidualFraction(), 1.0e-12);
		assertEquals(Math.max(offsetWrenchResidual.forceResidualFraction(),
						offsetWrenchResidual.torqueResidualFraction()),
				offsetWrenchResidual.maxWrenchResidualFraction(), 1.0e-12);
		double expectedMaxResidualFraction = Math.max(
				Math.max(offsetResidual.positionResidualFraction(), offsetResidual.velocityResidualFraction()),
				Math.max(offsetResidual.orientationResidualFraction(),
						offsetResidual.angularVelocityResidualFraction())
		);
		assertEquals(expectedMaxResidualFraction, offsetResidual.maxResidualFraction(), 1.0e-12);
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
	void providerComparesRotorOnlyStepTransitionFromStates() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double[] omegas = fill(config.rotors().size(), hoverOmega);
		DroneState previous = new DroneState(config.rotors().size());
		DroneState next = new DroneState(config.rotors().size());
		Vec3 positionWorld = new Vec3(0.75, 68.0, -1.25);
		Vec3 angularVelocityBody = new Vec3(0.25, -0.10, 0.04);
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.10),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.10)
		);
		Vec3 windWorld = new Vec3(0.20, 0.0, -0.15);
		Vec3 relativeAirBody = new Vec3(0.0, 1.25, 0.15);
		Vec3 velocityWorld = windWorld.add(bodyToWorld.rotate(relativeAirBody));
		DroneEnvironment environment = new DroneEnvironment(windWorld, 1.0, Double.POSITIVE_INFINITY);
		double dt = 0.01;
		for (int i = 0; i < omegas.length; i++) {
			previous.setMotorOmegaRadiansPerSecond(i, omegas[i]);
		}
		previous.setPositionMeters(positionWorld);
		previous.setVelocityMetersPerSecond(velocityWorld);
		previous.setOrientation(bodyToWorld);
		previous.setAngularVelocityBodyRadiansPerSecond(angularVelocityBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample reference =
				PropellerArchiveCtCpJWorldForceApplicationProvider.sampleStaticAnchoredConfigurationFromState(
						"apDrone",
						"provider_state_transition",
						config,
						previous,
						environment,
						omegas,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview preview =
				reference.rotorOnlyStepPreview(config, positionWorld, velocityWorld, angularVelocityBody, dt);
		next.setPositionMeters(preview.nextPositionWorldMeters());
		next.setVelocityMetersPerSecond(preview.nextVelocityWorldMetersPerSecond());
		next.setOrientation(preview.nextBodyToWorldOrientation());
		next.setAngularVelocityBodyRadiansPerSecond(preview.nextAngularVelocityBodyRadiansPerSecond());

		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample aligned =
				PropellerArchiveCtCpJWorldForceApplicationProvider.compareRotorOnlyStepToStateTransition(
						"apDrone",
						"provider_state_transition",
						config,
						previous,
						next,
						environment,
						omegas,
						dt,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample runtimeAligned =
				PropellerArchiveCtCpJWorldForceApplicationProvider
						.compareRuntimeReplacementRotorOnlyStepToStateTransition(
								"apDrone",
								"provider_state_transition",
								config,
								previous,
								next,
								environment,
								omegas,
								dt,
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
						);

		assertFalse(aligned.runtimeReplacement());
		assertTrue(runtimeAligned.runtimeReplacement());
		assertVectorEquals(Vec3.ZERO, aligned.positionResidualWorldMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aligned.velocityResidualWorldMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, aligned.orientationAngleResidualRadians(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aligned.angularVelocityResidualBodyRadiansPerSecond(), 1.0e-12);
		assertEquals(0.0, aligned.maxResidualFraction(), 1.0e-12);
		assertEquals(0.0, runtimeAligned.maxResidualFraction(), 1.0e-12);

		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview gravityPreview =
				reference.rotorGravityStepPreview(config, positionWorld, velocityWorld, angularVelocityBody, dt);
		next.setPositionMeters(gravityPreview.nextPositionWorldMeters());
		next.setVelocityMetersPerSecond(gravityPreview.nextVelocityWorldMetersPerSecond());
		next.setOrientation(gravityPreview.nextBodyToWorldOrientation());
		next.setAngularVelocityBodyRadiansPerSecond(gravityPreview.nextAngularVelocityBodyRadiansPerSecond());
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample gravityAligned =
				PropellerArchiveCtCpJWorldForceApplicationProvider.compareRotorGravityStepToStateTransition(
						"apDrone",
						"provider_state_transition",
						config,
						previous,
						next,
						environment,
						omegas,
						dt,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample runtimeGravityAligned =
				PropellerArchiveCtCpJWorldForceApplicationProvider
						.compareRuntimeReplacementRotorGravityStepToStateTransition(
								"apDrone",
								"provider_state_transition",
								config,
								previous,
								next,
								environment,
								omegas,
								dt,
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
						);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample rotorOnlyAgainstGravity =
				PropellerArchiveCtCpJWorldForceApplicationProvider.compareRotorOnlyStepToStateTransition(
						"apDrone",
						"provider_state_transition",
						config,
						previous,
						next,
						environment,
						omegas,
						dt,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		assertFalse(gravityAligned.runtimeReplacement());
		assertTrue(runtimeGravityAligned.runtimeReplacement());
		assertEquals(0.0, gravityAligned.maxResidualFraction(), 1.0e-12);
		assertEquals(0.0, runtimeGravityAligned.maxResidualFraction(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				gravityAligned.equivalentExternalWrench(config).equivalentExternalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(new Vec3(0.0, -config.massKg() * config.gravityMetersPerSecondSquared(), 0.0),
				rotorOnlyAgainstGravity.equivalentExternalWrench(config)
						.equivalentExternalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				rotorOnlyAgainstGravity.equivalentExternalWrench(config)
						.nonGravityExternalForceWorldNewtons(config.gravityMetersPerSecondSquared()),
				1.0e-12);

		Vec3 velocityOffset = new Vec3(0.015, 0.0, -0.005);
		next.setPositionMeters(preview.nextPositionWorldMeters());
		next.setVelocityMetersPerSecond(preview.nextVelocityWorldMetersPerSecond().add(velocityOffset));
		next.setOrientation(preview.nextBodyToWorldOrientation());
		next.setAngularVelocityBodyRadiansPerSecond(preview.nextAngularVelocityBodyRadiansPerSecond());
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample offset =
				PropellerArchiveCtCpJWorldForceApplicationProvider.compareRotorOnlyStepToStateTransition(
						"apDrone",
						"provider_state_transition",
						config,
						previous,
						next,
						environment,
						omegas,
						dt,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		assertVectorEquals(velocityOffset, offset.velocityResidualWorldMetersPerSecond(), 1.0e-12);
		assertTrue(offset.velocityResidualFraction() > 0.0);
		assertEquals(offset.velocityResidualFraction(), offset.maxResidualFraction(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepWrenchResidualSample
				offsetWrenchResidual = offset.equivalentExternalWrench(config);
		assertVectorEquals(velocityOffset.multiply(1.0 / dt),
				offsetWrenchResidual.residualLinearAccelerationWorldMetersPerSecondSquared(), 1.0e-12);
		assertVectorEquals(velocityOffset.multiply(config.massKg() / dt),
				offsetWrenchResidual.equivalentExternalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				offsetWrenchResidual.equivalentExternalTorqueBodyNewtonMeters(), 1.0e-12);
		assertTrue(offsetWrenchResidual.forceResidualFraction() > 0.0);
		assertEquals(offsetWrenchResidual.forceResidualFraction(),
				offsetWrenchResidual.maxWrenchResidualFraction(), 1.0e-12);
		assertVectorEquals(positionWorld, previous.positionMeters(), 1.0e-15);
		assertVectorEquals(velocityWorld, previous.velocityMetersPerSecond(), 1.0e-15);
		assertQuaternionEquals(bodyToWorld, previous.orientation(), 1.0e-15);
		assertVectorEquals(angularVelocityBody, previous.angularVelocityBodyRadiansPerSecond(), 1.0e-15);
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
		assertVectorEquals(direct.totalActuatorDiskWakeAngularMomentumTorqueWorldNewtonMeters(),
				fromState.totalActuatorDiskWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-12);
		assertVectorEquals(direct.totalActuatorDiskWakeAngularMomentumTorqueResidualWorldNewtonMeters(),
				fromState.totalActuatorDiskWakeAngularMomentumTorqueResidualWorldNewtonMeters(), 1.0e-12);
		assertEquals(direct.totalActuatorDiskMassFlowKilogramsPerSecond(),
				fromState.totalActuatorDiskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(direct.totalActuatorDiskIdealMomentumPowerWatts(),
				fromState.totalActuatorDiskIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(direct.totalActuatorDiskWakeKineticPowerWatts(),
				fromState.totalActuatorDiskWakeKineticPowerWatts(), 1.0e-12);
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
		assertEquals(0.0, aligned.forceBodyResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.torqueBodyResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.thrustResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.shaftPowerResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.shaftTorqueResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.maxAbsoluteResidualFraction(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aligned.runtimeReplacementForceBodyResidualNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aligned.runtimeReplacementTorqueBodyResidualNewtonMeters(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementThrustResidualNewtons(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementShaftPowerResidualWatts(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementShaftTorqueResidualNewtonMeters(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementForceBodyResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementTorqueBodyResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementThrustResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementShaftPowerResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementShaftTorqueResidualFraction(), 1.0e-12);
		assertEquals(0.0, aligned.runtimeReplacementMaxAbsoluteResidualFraction(), 1.0e-12);

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
		assertEquals(0.25 / offset.referenceTotalForceBodyNewtons().length(),
				offset.forceBodyResidualFraction(), 1.0e-12);
		assertEquals(0.010 / offset.referenceTotalTorqueBodyNewtonMeters().length(),
				offset.torqueBodyResidualFraction(), 1.0e-12);
		assertEquals(0.25 / offset.referenceTotalThrustNewtons(),
				offset.thrustResidualFraction(), 1.0e-12);
		assertEquals(2.0 / offset.referenceTotalShaftPowerWatts(),
				offset.shaftPowerResidualFraction(), 1.0e-12);
		assertEquals(0.003 / offset.referenceTotalShaftTorqueNewtonMeters(),
				offset.shaftTorqueResidualFraction(), 1.0e-12);
		double maxResidualFraction = Math.max(
				Math.max(offset.forceBodyResidualFraction(), offset.torqueBodyResidualFraction()),
				Math.max(Math.abs(offset.thrustResidualFraction()),
						Math.abs(offset.shaftPowerResidualFraction()))
		);
		maxResidualFraction = Math.max(maxResidualFraction,
				Math.abs(offset.shaftTorqueResidualFraction()));
		assertEquals(maxResidualFraction, offset.maxAbsoluteResidualFraction(), 1.0e-12);
		assertEquals(offset.forceBodyResidualFraction(),
				offset.runtimeReplacementForceBodyResidualFraction(), 1.0e-12);
		assertEquals(offset.torqueBodyResidualFraction(),
				offset.runtimeReplacementTorqueBodyResidualFraction(), 1.0e-12);
		assertEquals(offset.thrustResidualFraction(),
				offset.runtimeReplacementThrustResidualFraction(), 1.0e-12);
		assertEquals(offset.shaftPowerResidualFraction(),
				offset.runtimeReplacementShaftPowerResidualFraction(), 1.0e-12);
		assertEquals(offset.shaftTorqueResidualFraction(),
				offset.runtimeReplacementShaftTorqueResidualFraction(), 1.0e-12);
		assertEquals(offset.maxAbsoluteResidualFraction(),
				offset.runtimeReplacementMaxAbsoluteResidualFraction(), 1.0e-12);
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
		assertVectorEquals(Vec3.ZERO,
				sample.runtimeReplacementTotalActuatorDiskWakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				sample.runtimeReplacementTotalActuatorDiskWakeAngularMomentumTorqueResidualWorldNewtonMeters(),
				1.0e-15);
		assertEquals(0.0, sample.runtimeReplacementTotalActuatorDiskMassFlowKilogramsPerSecond(), 1.0e-15);
		assertEquals(0.0, sample.runtimeReplacementTotalActuatorDiskIdealMomentumPowerWatts(), 1.0e-15);
		assertEquals(0.0, sample.runtimeReplacementTotalActuatorDiskWakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, sample.runtimeReplacementTotalActuatorDiskWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, sample.runtimeReplacementTotalActuatorDiskWakeKineticPowerResidualWatts(), 1.0e-15);
		assertTrue(sample.totalActuatorDiskMassFlowKilogramsPerSecond() > 0.0);
		assertTrue(sample.totalActuatorDiskWakeKineticPowerWatts() > 0.0);
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
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepEnergySample runtimeEnergy =
				sample.runtimeReplacementRotorOnlyStepEnergySample(
						config,
						positionWorld,
						velocityWorld,
						Vec3.ZERO,
						dt
				);
		assertTrue(runtimeEnergy.runtimeReplacement());
		assertEquals(0.0, runtimeEnergy.totalShaftPowerWatts(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.totalIdealMomentumPowerWatts(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.totalWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.shaftEnergyJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.idealMomentumEnergyJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.wakeKineticEnergyJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.translationalKineticEnergyDeltaJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.rotationalKineticEnergyDeltaJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.rigidBodyKineticEnergyDeltaJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.forceWorkJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.bodyTorqueWorkJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.rigidBodyWorkResidualJoules(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.rigidBodyWorkResidualFraction(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.wakeKineticEnergyOverShaftEnergy(), 1.0e-15);
		assertEquals(0.0, runtimeEnergy.rigidBodyKineticEnergyDeltaOverShaftEnergy(), 1.0e-15);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample runtimeAlignedResidual =
				runtimePreview.residualTo(
						runtimePreview.nextPositionWorldMeters(),
						runtimePreview.nextVelocityWorldMetersPerSecond(),
						runtimePreview.nextBodyToWorldOrientation(),
						runtimePreview.nextAngularVelocityBodyRadiansPerSecond()
				);
		assertTrue(runtimeAlignedResidual.runtimeReplacement());
		assertEquals(0.0, runtimeAlignedResidual.maxResidualFraction(), 1.0e-12);
		Vec3 zeroForceVelocityDrift = new Vec3(0.01, 0.0, 0.0);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepResidualSample runtimeDriftResidual =
				runtimePreview.residualTo(
						runtimePreview.nextPositionWorldMeters(),
						runtimePreview.nextVelocityWorldMetersPerSecond().add(zeroForceVelocityDrift),
						runtimePreview.nextBodyToWorldOrientation(),
						runtimePreview.nextAngularVelocityBodyRadiansPerSecond()
				);
		assertVectorEquals(zeroForceVelocityDrift,
				runtimeDriftResidual.velocityResidualWorldMetersPerSecond(), 1.0e-12);
		assertEquals(1.0, runtimeDriftResidual.velocityResidualFraction(), 1.0e-12);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepWrenchResidualSample
				runtimeDriftWrenchResidual = runtimeDriftResidual.equivalentExternalWrench(config);
		assertTrue(runtimeDriftWrenchResidual.runtimeReplacement());
		assertVectorEquals(Vec3.ZERO, runtimeDriftWrenchResidual.referenceForceWorldNewtons(), 1.0e-15);
		assertVectorEquals(zeroForceVelocityDrift.multiply(config.massKg() / dt),
				runtimeDriftWrenchResidual.actualForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(zeroForceVelocityDrift.multiply(config.massKg() / dt),
				runtimeDriftWrenchResidual.equivalentExternalForceWorldNewtons(), 1.0e-12);
		assertEquals(1.0, runtimeDriftWrenchResidual.forceResidualFraction(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				runtimeDriftWrenchResidual.equivalentExternalTorqueBodyNewtonMeters(), 1.0e-15);
		assertEquals(0.0, runtimeDriftWrenchResidual.torqueResidualFraction(), 1.0e-15);
		assertEquals(1.0, runtimeDriftWrenchResidual.maxWrenchResidualFraction(), 1.0e-12);
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

	private static double transitionFraction(double residualMagnitude, double referenceMagnitude, double actualMagnitude) {
		double scale = Math.max(Math.abs(referenceMagnitude), Math.abs(actualMagnitude));
		return scale <= 1.0e-12 ? 0.0 : residualMagnitude / scale;
	}

	private static double quaternionAngleBetween(Quaternion expected, Quaternion actual) {
		double dot = Math.abs(expected.w() * actual.w()
				+ expected.x() * actual.x()
				+ expected.y() * actual.y()
				+ expected.z() * actual.z());
		return 2.0 * Math.acos(MathUtil.clamp(dot, 0.0, 1.0));
	}

	private static double translationalEnergy(double massKg, Vec3 velocityMetersPerSecond) {
		return 0.5 * massKg * velocityMetersPerSecond.lengthSquared();
	}

	private static double rotationalEnergy(Vec3 inertiaKgMetersSquared, Vec3 angularVelocityRadiansPerSecond) {
		return 0.5 * inertiaKgMetersSquared.multiply(angularVelocityRadiansPerSecond)
				.dot(angularVelocityRadiansPerSecond);
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
