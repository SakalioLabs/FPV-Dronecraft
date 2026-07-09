package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirframeLiftForceModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void straightBodyAxisFlowProducesNoLiftOrPower() {
		AirframeLiftForceModel.AirframeLiftForceSample sample = AirframeLiftForceModel.sampleSteady(
				liftConfig(),
				new Vec3(0.0, 0.0, 20.0),
				1.0,
				0.0
		);

		assertEquals(0.0, sample.angleOfAttackRadians(), 0.0);
		assertEquals(0.0, sample.sideslipRadians(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.pitchLiftForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.sideLiftForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.totalLiftForceBodyNewtons(), 0.0);
		assertEquals(0.0, sample.orthogonalityPowerResidualWatts(), 0.0);
	}

	@Test
	void angleAndSideslipLiftAreOrthogonalSymmetricAndLinearWithDensity() {
		DroneConfig config = liftConfig();
		Vec3 relativeAirVelocity = new Vec3(5.0, 4.0, 20.0);
		AirframeLiftForceModel.AirframeLiftForceSample sample = AirframeLiftForceModel.sampleSteady(
				config,
				relativeAirVelocity,
				1.0,
				0.20
		);
		AirframeLiftForceModel.AirframeLiftForceSample halfDensity = AirframeLiftForceModel.sampleSteady(
				config,
				relativeAirVelocity,
				0.5,
				0.20
		);
		AirframeLiftForceModel.AirframeLiftForceSample mirroredSideslip =
				AirframeLiftForceModel.sampleSteady(
						config,
						new Vec3(-5.0, 4.0, 20.0),
						1.0,
						0.20
				);
		AirframeLiftForceModel.AirframeLiftForceSample mirroredAngle =
				AirframeLiftForceModel.sampleSteady(
						config,
						new Vec3(5.0, -4.0, 20.0),
						1.0,
						0.20
				);

		assertTrue(sample.pitchLiftForceBodyNewtons().y() > 0.0);
		assertTrue(sample.pitchLiftForceBodyNewtons().z() < 0.0);
		assertTrue(sample.sideLiftForceBodyNewtons().x() < 0.0);
		assertTrue(sample.sideLiftForceBodyNewtons().z() > 0.0);
		assertEquals(0.0, sample.pitchLiftPowerWatts(), 1.0e-12);
		assertEquals(0.0, sample.sideForcePowerWatts(), 1.0e-12);
		assertTrue(sample.orthogonalityPowerResidualWatts() < 1.0e-12);
		assertVectorEquals(sample.totalLiftForceBodyNewtons().multiply(0.5),
				halfDensity.totalLiftForceBodyNewtons(), 1.0e-15);
		assertEquals(-sample.sideLiftForceBodyNewtons().x(),
				mirroredSideslip.sideLiftForceBodyNewtons().x(), 1.0e-15);
		assertEquals(sample.sideLiftForceBodyNewtons().z(),
				mirroredSideslip.sideLiftForceBodyNewtons().z(), 1.0e-15);
		assertEquals(-sample.pitchLiftForceBodyNewtons().y(),
				mirroredAngle.pitchLiftForceBodyNewtons().y(), 1.0e-15);
		assertEquals(sample.pitchLiftForceBodyNewtons().z(),
				mirroredAngle.pitchLiftForceBodyNewtons().z(), 1.0e-15);
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.25)
		);
		assertVectorEquals(bodyToWorld.rotate(sample.totalLiftForceBodyNewtons()),
				sample.totalLiftForceWorldNewtons(bodyToWorld), 1.0e-15);
	}

	@Test
	void separatedFlowReducesHighAngleLiftWithoutAddingPower() {
		DroneConfig config = liftConfig();
		Vec3 highAngleFlow = new Vec3(0.0, 20.0, 5.0);
		AirframeLiftForceModel.AirframeLiftForceSample attached = AirframeLiftForceModel.sampleSteady(
				config,
				highAngleFlow,
				1.0,
				0.0
		);
		AirframeLiftForceModel.AirframeLiftForceSample separated = AirframeLiftForceModel.sampleSteady(
				config,
				highAngleFlow,
				1.0,
				1.0
		);

		assertTrue(attached.pitchStallIntensity() > 0.95);
		assertTrue(separated.pitchStallScale() < attached.pitchStallScale());
		assertTrue(separated.pitchLiftForceBodyNewtons().length()
				< attached.pitchLiftForceBodyNewtons().length());
		assertTrue(attached.orthogonalityPowerResidualWatts() < 1.0e-12);
		assertTrue(separated.orthogonalityPowerResidualWatts() < 1.0e-12);
	}

	@Test
	void ctCpJWrenchAddsLiftAndCombinedPressureCenterMoment() {
		DroneConfig config = DroneConfig.apDrone()
				.withCenterOfPressureOffsetBodyMeters(new Vec3(0.015, 0.020, -0.010));
		Vec3 relativeAirVelocityBody = new Vec3(5.0, 4.0, 20.0);
		Vec3 angularVelocityBody = new Vec3(0.20, -0.12, 0.08);
		double[] omegas = hoverOmegas(config);
		double separatedFlowStateIntensity = 0.35;
		double dt = 0.01;
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample =
				PropellerArchiveCtCpJWorldForceApplicationProvider
						.sampleStaticAnchoredConfigurationFromWorldKinematics(
								"apDrone",
								"airframe_lift_wrench",
								config,
								Vec3.ZERO,
								Quaternion.IDENTITY,
								relativeAirVelocityBody,
								angularVelocityBody,
								Vec3.ZERO,
								null,
								omegas,
								RHO,
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
						);
		AirframeDragForceModel.AirframeDragForceSample drag = worldSample.steadyAirframeDragSample(
				config,
				separatedFlowStateIntensity
		);
		AirframeLiftForceModel.AirframeLiftForceSample lift = worldSample.steadyAirframeLiftSample(
				config,
				separatedFlowStateIntensity
		);
		AirframePressureCenterModel.AirframePressureCenterSample dragPressure =
				worldSample.steadyAirframePressureCenterSample(config, separatedFlowStateIntensity);
		AirframePressureCenterModel.AirframePressureCenterSample combinedPressure =
				AirframePressureCenterModel.sampleSteady(
						config,
						relativeAirVelocityBody,
						drag.bodyDragForceBodyNewtons().add(lift.totalLiftForceBodyNewtons()),
						drag.effectiveSeparationIntensity()
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample dragWrench =
				worldSample.rotorGravityTransientCrossflowDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample liftWrench =
				worldSample.rotorGravityTransientCrossflowLiftDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeDragWrench =
				worldSample.runtimeReplacementRotorGravityTransientCrossflowDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeLiftWrench =
				worldSample.runtimeReplacementRotorGravityTransientCrossflowLiftDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview preview =
				worldSample.rotorGravityTransientCrossflowLiftDragStepPreview(
						config,
						Vec3.ZERO,
						relativeAirVelocityBody,
						angularVelocityBody,
						omegas,
						omegas,
						separatedFlowStateIntensity,
						dt
				);

		Vec3 expectedPressureTorqueDelta = combinedPressure.pressureCenterTorqueBodyNewtonMeters()
				.subtract(dragPressure.pressureCenterTorqueBodyNewtonMeters());
		assertTrue(lift.totalLiftForceBodyNewtons().length() > 0.0);
		assertTrue(lift.orthogonalityPowerResidualWatts() < 1.0e-12);
		assertVectorEquals(dragWrench.totalForceWorldNewtons().add(lift.totalLiftForceBodyNewtons()),
				liftWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(dragWrench.totalTorqueBodyNewtonMeters().add(expectedPressureTorqueDelta),
				liftWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(runtimeDragWrench.totalForceWorldNewtons().add(lift.totalLiftForceBodyNewtons()),
				runtimeLiftWrench.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(runtimeDragWrench.totalTorqueBodyNewtonMeters().add(expectedPressureTorqueDelta),
				runtimeLiftWrench.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertTrue(runtimeLiftWrench.runtimeReplacement());
		assertVectorEquals(angularVelocityBody.add(
					liftWrench.angularAccelerationBodyRadiansPerSecondSquared().multiply(dt)),
				preview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
	}

	private static DroneConfig liftConfig() {
		return DroneConfig.racingQuad().withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
	}

	private static double[] hoverOmegas(DroneConfig config) {
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient()
		);
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < omegas.length; i++) {
			omegas[i] = hoverOmega;
		}
		return omegas;
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, "x");
		assertEquals(expected.y(), actual.y(), tolerance, "y");
		assertEquals(expected.z(), actual.z(), tolerance, "z");
	}
}
