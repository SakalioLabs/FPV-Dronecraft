package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorStaticCtCpModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void apDroneStaticCoefficientsMatchFoxeerBenchThrustAndTorque() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);

		RotorStaticCtCpModel.StaticRotorSample sample = RotorStaticCtCpModel.sample(
				"apDrone",
				"foxeer_donut_5145_static",
				rotor,
				MotorBenchCurrentModel.FOXEER_DONUT_5145_PUBLIC_TEST_RPM,
				RHO
		);

		assertEquals(RotorStaticCtCpModel.SOURCE_ID, sample.sourceId());
		assertEquals(0.159299848814191, sample.thrustCoefficientCt(), 1.0e-15);
		assertEquals(0.104870616017161, sample.powerCoefficientCp(), 1.0e-15);
		assertEquals(MotorBenchCurrentModel.FOXEER_DONUT_5145_PUBLIC_TEST_THRUST_NEWTONS,
				sample.thrustNewtons(), 1.0e-12);
		assertEquals(MotorBenchCurrentModel.FOXEER_DONUT_5145_PUBLIC_TEST_TORQUE_NEWTON_METERS,
				sample.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters() * sample.angularVelocityRadiansPerSecond(),
				sample.shaftPowerWatts(), 1.0e-12);
		assertTrue(sample.shaftPowerWatts()
				< MotorBenchCurrentModel.FOXEER_DONUT_5145_PUBLIC_TEST_POWER_WATTS);
		assertEquals(0.0, sample.propulsiveEfficiencyEta(), 1.0e-12);
	}

	@Test
	void staticModelScalesWithRpmAndDensity() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		RotorStaticCtCpModel.StaticRotorSample low = RotorStaticCtCpModel.sample(
				"apDrone", "static", rotor, 10_000.0, RHO);
		RotorStaticCtCpModel.StaticRotorSample high = RotorStaticCtCpModel.sample(
				"apDrone", "static", rotor, 20_000.0, RHO);
		RotorStaticCtCpModel.StaticRotorSample dense = RotorStaticCtCpModel.sample(
				"apDrone", "static", rotor, 10_000.0, RHO * 2.0);

		assertEquals(low.thrustNewtons() * 4.0, high.thrustNewtons(), 1.0e-12);
		assertEquals(low.shaftPowerWatts() * 8.0, high.shaftPowerWatts(), 1.0e-12);
		assertEquals(low.shaftTorqueNewtonMeters() * 4.0, high.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(low.thrustNewtons() * 2.0, dense.thrustNewtons(), 1.0e-12);
		assertEquals(low.shaftPowerWatts() * 2.0, dense.shaftPowerWatts(), 1.0e-12);
		assertEquals(low.shaftTorqueNewtonMeters() * 2.0, dense.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(low.idealInducedVelocityMetersPerSecond(),
				dense.idealInducedVelocityMetersPerSecond(), 1.0e-15);
	}

	@Test
	void rejectsInvalidStaticInputs() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		assertThrows(IllegalArgumentException.class,
				() -> RotorStaticCtCpModel.sample("apDrone", "static", null, 10_000.0, RHO));
		assertThrows(IllegalArgumentException.class,
				() -> RotorStaticCtCpModel.sample("apDrone", "static", rotor, 0.0, RHO));
		assertThrows(IllegalArgumentException.class,
				() -> RotorStaticCtCpModel.sample("apDrone", "static", rotor, 10_000.0, 0.0));
		assertThrows(IllegalArgumentException.class,
				() -> RotorStaticCtCpModel.sample("apDrone", "static", rotor, 10_000.0, RHO, 0.0));
	}
}
