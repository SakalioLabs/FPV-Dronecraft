package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DronePhysicsCtCpJReferenceTelemetryTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void apDroneMidDomainReferenceSampleCanBeStoredAsRuntimeTelemetry() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BILINEAR,
				sample.lookup().interpolationStatus());
		assertEquals(reference.advanceRatioJ(), sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(reference.rpm(), sample.lookup().effectiveRpm(), 1.0e-9);
		assertEquals(0.09325, sample.lookup().thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.05075, sample.lookup().powerCoefficientCp(), 1.0e-12);
		assertEquals(0.19840592872073343, sample.thrustNewtons(), 1.0e-15);
		assertEquals(1.0985575597709705, sample.shaftPowerWatts(), 1.0e-15);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);

		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceClamped(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BILINEAR,
				state.rotorCtCpJReferenceInterpolationStatus(0));
		assertEquals(sample.lookup().effectiveAdvanceRatioJ(), state.rotorCtCpJReferenceAdvanceRatioJ(0), 1.0e-12);
		assertEquals(sample.lookup().effectiveRpm(), state.rotorCtCpJReferenceRpm(0), 1.0e-9);
		assertEquals(sample.lookup().thrustCoefficientCt(), state.rotorCtCpJReferenceThrustCoefficientCt(0), 1.0e-12);
		assertEquals(sample.lookup().powerCoefficientCp(), state.rotorCtCpJReferencePowerCoefficientCp(0), 1.0e-12);
		assertEquals(sample.lookup().propulsiveEfficiencyEta(), state.rotorCtCpJReferenceEfficiencyEta(0), 1.0e-12);
		assertEquals(sample.thrustNewtons(), state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);
		assertEquals(sample.shaftPowerWatts(), state.rotorCtCpJReferenceShaftPowerWatts(0), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters(), state.rotorCtCpJReferenceShaftTorqueNewtonMeters(0), 1.0e-18);

		state.setRotorThrustNewtons(0, sample.thrustNewtons() + 0.0125);
		state.setMotorShaftPowerWatts(0, sample.shaftPowerWatts() + 0.25);
		state.setMotorAerodynamicTorqueNewtonMeters(0, sample.shaftTorqueNewtonMeters() + 0.0002);
		state.updateRotorCtCpJReferenceResidual(0);
		assertEquals(0.0125, state.rotorCtCpJReferenceThrustResidualNewtons(0), 1.0e-15);
		assertEquals(0.25, state.rotorCtCpJReferenceShaftPowerResidualWatts(0), 1.0e-15);
		assertEquals(0.0002, state.rotorCtCpJReferenceShaftTorqueResidualNewtonMeters(0), 1.0e-15);
		assertEquals((sample.thrustNewtons() + 0.0125) / sample.thrustNewtons(),
				state.rotorCtCpJReferenceThrustRatio(0), 1.0e-15);
		assertEquals((sample.shaftTorqueNewtonMeters() + 0.0002) / sample.shaftTorqueNewtonMeters(),
				state.rotorCtCpJReferenceShaftTorqueRatio(0), 1.0e-15);

		state.resetMotors();
		assertFalse(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertEquals(0.0, state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceRpm(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceThrustResidualNewtons(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftPowerResidualWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftTorqueResidualNewtonMeters(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceThrustRatio(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftTorqueRatio(0), 1.0e-15);
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED,
				state.rotorCtCpJReferenceInterpolationStatus(0));
	}

	@Test
	void unsupportedRotorGeometryDoesNotUseApDroneReferencePayload() {
		RotorSpec racingRotor = DroneConfig.racingQuad().rotors().get(0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(
						racingRotor,
						racingRotor.thrustAxisBody().multiply(5.0),
						4_500.0,
						1.0
				);

		assertNull(sample);
	}

	@Test
	void acceptedReferenceSampleAnchorsRuntimeBaseThrustAndTorque() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		double fallbackThrust = rotor.thrustCoefficient() * omega * omega;
		assertEquals(sample.thrustNewtons(), DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 1.0, 1.0), 1.0e-15);
		assertEquals(sample.thrustNewtons() * 0.72, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 0.72, 1.0), 1.0e-15);

		double fallbackTorque = rotor.yawTorquePerThrustMeter() * fallbackThrust;
		assertEquals(sample.shaftTorqueNewtonMeters() * 0.99 + 0.0003,
				DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
						sample,
						fallbackTorque,
						1.1,
						0.9,
						0.0003,
						1.0,
						1.0
				),
				1.0e-18);
		assertEquals(sample.shaftTorqueNewtonMeters() * 0.99 * 0.72 + 0.0003,
				DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
						sample,
						fallbackTorque,
						1.1,
						0.9,
						0.0003,
						0.72,
						1.0
				),
				1.0e-18);
	}

	@Test
	void outOfEnvelopeRuntimeReferenceSampleIsExplicitlyBlocked() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery highReference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"high_domain_max_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double queryJ = highReference.advanceRatioJ() + 0.20;
		double omega = highReference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(queryJ
				* highReference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		assertTrue(sample.blocked());
		assertFalse(sample.clamped());
		assertEquals("REFERENCE_WINDOW_UNAVAILABLE", sample.lookup().status());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED,
				sample.lookup().interpolationStatus());
		assertEquals(0.0, sample.thrustNewtons(), 1.0e-15);
		assertEquals(0.0, sample.shaftPowerWatts(), 1.0e-15);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);
		assertFalse(state.rotorCtCpJReferenceAvailable(0));
		assertTrue(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceClamped(0));
		assertEquals(queryJ, state.rotorCtCpJReferenceAdvanceRatioJ(0), 1.0e-12);
		assertEquals(highReference.rpm(), state.rotorCtCpJReferenceRpm(0), 1.0e-9);
		assertEquals(0.0, state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);

		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, 0.42, 1.0, 1.0), 1.0e-15);
		assertEquals(0.031, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, 0.031, 1.0, 1.0, 0.0002, 1.0, 1.0), 1.0e-15);
	}
}
