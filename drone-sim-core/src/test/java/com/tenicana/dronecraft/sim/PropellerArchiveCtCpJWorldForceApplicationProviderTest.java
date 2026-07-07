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
		assertEquals(config.rotors().size(), sample.appliedRotorCount());
		assertEquals(config.rotors().size(), sample.runtimeReplacementAppliedRotorCount());
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
		for (int i = 0; i < sample.rotorApplications().size(); i++) {
			PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application =
					sample.rotorApplications().get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample rotorSample =
					sample.aggregate().rotorSamples().get(i);
			assertEquals(i, application.rotorIndex());
			assertTrue(application.applied());
			assertVectorEquals(
					momentReferenceWorld.add(rotorSample.momentArmWorldMeters(bodyToWorld)),
					application.forceApplicationPointWorldMeters(),
					1.0e-15
			);
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
	void runtimeReplacementApplicationsZeroClampRejectedRotorForces() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;
		double[] omegas = fill(config.rotors().size(), omega);
		Vec3 momentReferenceWorld = new Vec3(-4.0, 72.0, 11.0);
		Vec3 reverseVehicleVelocityWorld = rotor.thrustAxisBody().multiply(-4.5);

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
		assertEquals(config.rotors().size(), sample.appliedRotorCount());
		assertEquals(0, sample.runtimeReplacementAppliedRotorCount());
		assertFalse(sample.runtimeReplacementAccepted());
		assertEquals(config.rotors().size(), sample.aggregate().clampedRotorCount());
		assertTrue(sample.aggregate().totalThrustForceBodyNewtons().length() > 0.0);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalThrustForceWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalReactionTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalThrustMomentWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sample.runtimeReplacementTotalTorqueWorldNewtonMeters(), 1.0e-15);
		for (int i = 0; i < sample.runtimeReplacementRotorApplications().size(); i++) {
			PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application =
					sample.runtimeReplacementRotorApplications().get(i);
			assertEquals(i, application.rotorIndex());
			assertFalse(application.applied());
			assertFalse(application.runtimeForceReplacementAccepted());
			assertEquals("CLAMPED", application.lookupStatus());
			assertVectorEquals(
					sample.rotorApplications().get(i).forceApplicationPointWorldMeters(),
					application.forceApplicationPointWorldMeters(),
					1.0e-15
			);
			assertVectorEquals(Vec3.ZERO, application.thrustForceWorldNewtons(), 1.0e-15);
			assertVectorEquals(Vec3.ZERO, application.totalTorqueWorldNewtonMeters(), 1.0e-15);
		}
	}

	private static double[] fill(int count, double value) {
		double[] values = new double[count];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}
}
