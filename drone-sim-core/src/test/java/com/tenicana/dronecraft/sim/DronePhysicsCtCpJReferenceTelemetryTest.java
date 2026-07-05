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
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone", "static_anchored", rotor, reference.rpm(), RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult archiveShape =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(reference);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult archiveStaticAnchor =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(
						PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
								"apDrone",
								"static_anchor_low_rpm",
								rotor.radiusMeters() * 2.0,
								RHO
						));
		double expectedCt = staticSample.thrustCoefficientCt()
				* archiveShape.thrustCoefficientCt()
				/ archiveStaticAnchor.thrustCoefficientCt();
		double expectedCp = staticSample.powerCoefficientCp()
				* archiveShape.powerCoefficientCp()
				/ archiveStaticAnchor.powerCoefficientCp();

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(reference.advanceRatioJ(), sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(reference.rpm(), sample.lookup().effectiveRpm(), 1.0e-9);
		assertEquals(expectedCt, sample.lookup().thrustCoefficientCt(), 1.0e-15);
		assertEquals(expectedCp, sample.lookup().powerCoefficientCp(), 1.0e-15);
		assertEquals(expectedThrust(sample.lookup(), rotor.radiusMeters() * 2.0, RHO),
				sample.thrustNewtons(), 1.0e-15);
		assertEquals(expectedShaftPower(sample.lookup(), rotor.radiusMeters() * 2.0, RHO),
				sample.shaftPowerWatts(), 1.0e-15);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);

		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceClamped(0));
		assertTrue(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				state.rotorCtCpJReferenceInterpolationStatus(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.INTERPOLATED,
				state.rotorCtCpJReferenceLookupStatusCode(0));
		assertEquals(sample.lookup().effectiveAdvanceRatioJ(), state.rotorCtCpJReferenceAdvanceRatioJ(0), 1.0e-12);
		assertEquals(sample.lookup().effectiveRpm(), state.rotorCtCpJReferenceRpm(0), 1.0e-9);
		assertEquals(sample.lookup().thrustCoefficientCt(), state.rotorCtCpJReferenceThrustCoefficientCt(0), 1.0e-12);
		assertEquals(sample.lookup().powerCoefficientCp(), state.rotorCtCpJReferencePowerCoefficientCp(0), 1.0e-12);
		assertEquals(sample.lookup().propulsiveEfficiencyEta(), state.rotorCtCpJReferenceEfficiencyEta(0), 1.0e-12);
		assertEquals(sample.thrustNewtons(), state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);
		assertEquals(sample.shaftPowerWatts(), state.rotorCtCpJReferenceShaftPowerWatts(0), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters(), state.rotorCtCpJReferenceShaftTorqueNewtonMeters(0), 1.0e-18);

		double torqueDelta = 0.0002;
		state.setRotorThrustNewtons(0, sample.thrustNewtons() + 0.0125);
		state.setMotorOmegaRadiansPerSecond(0, sample.dimensionalSample().angularVelocityRadiansPerSecond());
		state.setMotorShaftPowerWatts(0, sample.shaftPowerWatts() + 12.0);
		state.setMotorAerodynamicTorqueNewtonMeters(0, sample.shaftTorqueNewtonMeters() + torqueDelta);
		state.updateRotorCtCpJReferenceResidual(0);
		assertEquals(0.0125, state.rotorCtCpJReferenceThrustResidualNewtons(0), 1.0e-15);
		assertEquals(
				torqueDelta * sample.dimensionalSample().angularVelocityRadiansPerSecond(),
				state.rotorCtCpJReferenceShaftPowerResidualWatts(0),
				1.0e-12
		);
		assertEquals(torqueDelta, state.rotorCtCpJReferenceShaftTorqueResidualNewtonMeters(0), 1.0e-15);
		assertEquals((sample.thrustNewtons() + 0.0125) / sample.thrustNewtons(),
				state.rotorCtCpJReferenceThrustRatio(0), 1.0e-15);
		assertEquals((sample.shaftTorqueNewtonMeters() + torqueDelta) / sample.shaftTorqueNewtonMeters(),
				state.rotorCtCpJReferenceShaftTorqueRatio(0), 1.0e-15);

		state.resetMotors();
		assertFalse(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertEquals(0.0, state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceRpm(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceThrustResidualNewtons(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftPowerResidualWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftTorqueResidualNewtonMeters(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceThrustRatio(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftTorqueRatio(0), 1.0e-15);
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED,
				state.rotorCtCpJReferenceInterpolationStatus(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.UNKNOWN,
				state.rotorCtCpJReferenceLookupStatusCode(0));
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
	void hoverRuntimeReferenceUsesStaticAnchorAndCanReplaceRuntimeForce() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double hoverRpm = hoverOmega * 60.0 / (2.0 * Math.PI);
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone", "static_anchored_hover", rotor, hoverRpm, RHO);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, Vec3.ZERO, hoverOmega, 1.0);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals("static_anchored_forward_shape", sample.lookup().caseName());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.INTERPOLATED,
				sample.lookup().lookupStatusCode());
		assertEquals(staticSample.thrustCoefficientCt(), sample.lookup().thrustCoefficientCt(), 1.0e-15);
		assertEquals(staticSample.powerCoefficientCp(), sample.lookup().powerCoefficientCp(), 1.0e-15);
		assertEquals(staticSample.thrustNewtons(), sample.thrustNewtons(), 1.0e-12);
		assertEquals(staticSample.shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-12);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);
		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceClamped(0));
		assertTrue(state.rotorCtCpJReferenceRuntimeApplied(0));

		double fallbackThrust = rotor.thrustCoefficient() * hoverOmega * hoverOmega;
		double fallbackTorque = rotor.yawTorquePerThrustMeter() * fallbackThrust;
		assertEquals(fallbackThrust, sample.thrustNewtons(), 1.0e-12);
		assertEquals(fallbackTorque, sample.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(fallbackThrust, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 1.0, 1.0), 1.0e-15);
		assertEquals(fallbackTorque, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, fallbackTorque, 1.0, 1.0, 0.0, 1.0, 1.0), 1.0e-15);
	}

	@Test
	void reverseAxialRuntimeReferenceClampsForTelemetryWithoutReplacingRuntimeForce() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		Vec3 reverseAxialFlow = rotor.thrustAxisBody().multiply(-4.5);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, reverseAxialFlow, hoverOmega, 1.0);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertTrue(sample.clamped());
		assertTrue(sample.momentumPowerClosureSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals("reverse_axial_static_anchor", sample.lookup().caseName());
		assertEquals("CLAMPED", sample.lookup().status());
		assertEquals("reverse-axial-flow-clamped-to-static-anchor", sample.lookup().message());
		assertEquals(0.0, sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.0, sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);

		double fallbackThrust = rotor.thrustCoefficient() * hoverOmega * hoverOmega;
		double fallbackTorque = rotor.yawTorquePerThrustMeter() * fallbackThrust;
		assertEquals(fallbackThrust, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 1.0, 1.0), 1.0e-15);
		assertEquals(fallbackTorque, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, fallbackTorque, 1.0, 1.0, 0.0, 1.0, 1.0), 1.0e-15);
	}

	@Test
	void outOfEnvelopeRuntimeReferenceSampleClampsForTelemetryWithoutReplacingRuntimeForce() {
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
		assertFalse(sample.blocked());
		assertTrue(sample.clamped());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals("CLAMPED", sample.lookup().status());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.CLAMPED_EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.CLAMPED,
				sample.lookup().lookupStatusCode());
		assertEquals(sample.lookup().upperAdvanceRatioJ(), sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertTrue(sample.thrustNewtons() > 0.0);
		assertTrue(sample.shaftPowerWatts() > 0.0);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);
		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertTrue(state.rotorCtCpJReferenceClamped(0));
		assertFalse(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.CLAMPED,
				state.rotorCtCpJReferenceLookupStatusCode(0));
		assertEquals(sample.lookup().effectiveAdvanceRatioJ(), state.rotorCtCpJReferenceAdvanceRatioJ(0), 1.0e-12);
		assertEquals(highReference.rpm(), state.rotorCtCpJReferenceRpm(0), 1.0e-9);
		assertEquals(sample.thrustNewtons(), state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);

		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, 0.42, 1.0, 1.0), 1.0e-15);
		assertEquals(0.031, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, 0.031, 1.0, 1.0, 0.0002, 1.0, 1.0), 1.0e-15);
	}

	private static double expectedThrust(
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			double diameter,
			double density
	) {
		double n = lookup.queryRpm() / 60.0;
		return lookup.thrustCoefficientCt() * density * n * n * Math.pow(diameter, 4.0);
	}

	private static double expectedShaftPower(
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			double diameter,
			double density
	) {
		double n = lookup.queryRpm() / 60.0;
		return lookup.powerCoefficientCp() * density * n * n * n * Math.pow(diameter, 5.0);
	}
}
