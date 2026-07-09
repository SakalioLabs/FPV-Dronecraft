package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RotorInertiaTorqueModelTest {
	@Test
	void spinAccelerationReactionClosesAngularMomentumRateAndSpinEnergy() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double previousOmega = 1_000.0;
		double omega = 1_120.0;
		double dt = 0.020;
		double expectedAngularAcceleration = (omega - previousOmega) / dt;
		Vec3 expectedAngularMomentum = rotor.thrustAxisBody().multiply(
				rotor.spinDirection() * rotor.rotorInertiaKgMetersSquared() * omega
		);
		Vec3 expectedReactionTorque = rotor.thrustAxisBody().multiply(
				-rotor.spinDirection() * rotor.rotorInertiaKgMetersSquared() * expectedAngularAcceleration
		);

		RotorInertiaTorqueModel.RotorInertiaTorqueSample sample = RotorInertiaTorqueModel.sample(
				rotor,
				previousOmega,
				omega,
				Vec3.ZERO,
				rotor.thrustAxisBody(),
				dt
		);

		assertEquals(expectedAngularAcceleration, sample.angularAccelerationRadiansPerSecondSquared(), 1.0e-12);
		assertVectorEquals(expectedAngularMomentum,
				sample.angularMomentumBodyKgMetersSquaredPerSecond(), 1.0e-15);
		assertVectorEquals(expectedReactionTorque,
				sample.accelerationReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sample.gyroscopicReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(expectedReactionTorque, sample.totalReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertEquals(
				0.5 * rotor.rotorInertiaKgMetersSquared() * previousOmega * previousOmega,
				sample.initialSpinKineticEnergyJoules(),
				1.0e-12
		);
		assertEquals(
				0.5 * rotor.rotorInertiaKgMetersSquared() * omega * omega,
				sample.spinKineticEnergyJoules(),
				1.0e-12
		);
		assertEquals(sample.spinKineticEnergyJoules() - sample.initialSpinKineticEnergyJoules(),
				sample.spinKineticEnergyDeltaJoules(), 1.0e-12);
	}

	@Test
	void gyroscopicReactionOpposesBodyRateCrossRotorAngularMomentum() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = 1_800.0;
		Vec3 bodyRate = new Vec3(3.5, -0.4, 1.2);

		RotorInertiaTorqueModel.RotorInertiaTorqueSample sample = RotorInertiaTorqueModel.sample(
				rotor,
				omega,
				omega,
				bodyRate,
				rotor.thrustAxisBody(),
				0.010
		);
		Vec3 expectedGyroscopicReaction = bodyRate
				.cross(sample.angularMomentumBodyKgMetersSquaredPerSecond())
				.multiply(-1.0);

		assertVectorEquals(Vec3.ZERO, sample.accelerationReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(expectedGyroscopicReaction,
				sample.gyroscopicReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(expectedGyroscopicReaction, sample.totalReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertEquals(0.0,
				sample.gyroscopicReactionTorqueBodyNewtonMeters().dot(
						sample.angularMomentumBodyKgMetersSquaredPerSecond()),
				1.0e-15);
	}

	@Test
	void balancedCounterRotatingQuadCancelsRotorMomentumAndReactionTorque() {
		DroneConfig config = DroneConfig.apDrone();
		double[] omega = fill(config.rotors().size(), 1_650.0);
		double[] balancedPreviousOmega = fill(config.rotors().size(), 1_500.0);
		Vec3 bodyRate = new Vec3(4.0, -1.2, 2.5);

		RotorInertiaTorqueModel.ConfigurationRotorInertiaTorqueSample balanced =
				RotorInertiaTorqueModel.sampleConfiguration(
						config,
						balancedPreviousOmega,
						omega,
						bodyRate,
						0.010
				);

		assertEquals(config.rotors().size(), balanced.rotorSamples().size());
		assertVectorEquals(Vec3.ZERO,
				balanced.totalAngularMomentumBodyKgMetersSquaredPerSecond(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				balanced.totalAccelerationReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				balanced.totalGyroscopicReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, balanced.totalReactionTorqueBodyNewtonMeters(), 1.0e-15);

		double[] unbalancedPreviousOmega = omega.clone();
		unbalancedPreviousOmega[0] -= 180.0;
		RotorInertiaTorqueModel.ConfigurationRotorInertiaTorqueSample unbalanced =
				RotorInertiaTorqueModel.sampleConfiguration(
						config,
						unbalancedPreviousOmega,
						omega,
						bodyRate,
						0.010
				);
		RotorInertiaTorqueModel.RotorInertiaTorqueSample firstRotor = unbalanced.rotorSamples().get(0);

		assertVectorEquals(firstRotor.accelerationReactionTorqueBodyNewtonMeters(),
				unbalanced.totalAccelerationReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				unbalanced.totalGyroscopicReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(firstRotor.accelerationReactionTorqueBodyNewtonMeters(),
				unbalanced.totalReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertEquals(firstRotor.spinKineticEnergyDeltaJoules(),
				unbalanced.spinKineticEnergyDeltaJoules(), 1.0e-12);
	}

	private static double[] fill(int size, double value) {
		double[] values = new double[size];
		for (int i = 0; i < size; i++) {
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
