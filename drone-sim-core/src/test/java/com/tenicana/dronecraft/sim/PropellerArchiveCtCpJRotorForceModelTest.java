package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropellerArchiveCtCpJRotorForceModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void staticAnchorProducesBodyForceAndReactionTorque() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"static_anchor_low_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensionalReference =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(reference);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sample(new PropellerArchiveCtCpJRotorForceModel.RotorForceQuery(
						"apDrone",
						"static_anchor_low_rpm",
						rotor,
						reference.advanceRatioJ(),
						reference.rpm(),
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				));

		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.momentumPowerClosureSatisfied());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(dimensionalReference.thrustNewtons(), sample.thrustNewtons(), 1.0e-15);
		assertEquals(dimensionalReference.shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(dimensionalReference.shaftTorqueNewtonMeters(), sample.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(0.0, sample.thrustForceBodyNewtons().x(), 1.0e-15);
		assertEquals(sample.thrustNewtons(), sample.thrustForceBodyNewtons().y(), 1.0e-15);
		assertEquals(0.0, sample.thrustForceBodyNewtons().z(), 1.0e-15);
		assertEquals(rotor.spinDirection() * sample.shaftTorqueNewtonMeters(),
				sample.reactionTorqueBodyNewtonMeters().y(), 1.0e-18);
		assertEquals(sample.shaftTorqueNewtonMeters() / sample.thrustNewtons(),
				sample.yawTorquePerThrustMeterEquivalent(), 1.0e-18);
	}

	@Test
	void axialAdvanceSpeedQueryReconstructsMidAdvanceRatio() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		double axialAdvanceSpeed = reference.advanceRatioJ() * reference.rpm() / 60.0 * rotor.radiusMeters() * 2.0;

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleFromAxialAdvanceSpeed(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor,
						axialAdvanceSpeed,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertFalse(sample.blocked());
		assertTrue(sample.momentumPowerClosureSatisfied());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BILINEAR,
				sample.lookup().interpolationStatus());
		assertEquals(reference.advanceRatioJ(), sample.query().advanceRatioJ(), 1.0e-12);
		assertEquals(reference.rpm(), sample.query().rpm(), 1.0e-9);
		assertEquals(axialAdvanceSpeed, sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(sample.thrustNewtons(), sample.thrustForceBodyNewtons().length(), 1.0e-15);
		assertTrue(sample.shaftPowerWatts() > 0.0);
		assertTrue(sample.shaftTorqueNewtonMeters() > 0.0);
	}

	@Test
	void cantedRotorForceAndReactionTorqueFollowThrustAxis() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withThrustAxisBody(new Vec3(0.0, Math.sqrt(3.0) * 0.5, 0.5));
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromAxialAdvanceSpeed(
						"apDrone",
						"canted_static_anchor",
						rotor,
						0.0,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.runtimeForceReplacementAccepted());
		Vec3 axis = rotor.thrustAxisBody();
		assertEquals(axis.x() * sample.thrustNewtons(), sample.thrustForceBodyNewtons().x(), 1.0e-15);
		assertEquals(axis.y() * sample.thrustNewtons(), sample.thrustForceBodyNewtons().y(), 1.0e-15);
		assertEquals(axis.z() * sample.thrustNewtons(), sample.thrustForceBodyNewtons().z(), 1.0e-15);
		assertEquals(axis.x() * rotor.spinDirection() * sample.shaftTorqueNewtonMeters(),
				sample.reactionTorqueBodyNewtonMeters().x(), 1.0e-15);
		assertEquals(axis.y() * rotor.spinDirection() * sample.shaftTorqueNewtonMeters(),
				sample.reactionTorqueBodyNewtonMeters().y(), 1.0e-15);
		assertEquals(axis.z() * rotor.spinDirection() * sample.shaftTorqueNewtonMeters(),
				sample.reactionTorqueBodyNewtonMeters().z(), 1.0e-15);
		assertEquals(sample.thrustNewtons(), sample.thrustForceBodyNewtons().length(), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters(), sample.reactionTorqueBodyNewtonMeters().length(), 1.0e-15);
	}

	@Test
	void highAdvanceSampleKeepsCtCpTrendAndFiniteTorque() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample mid = sampleReferenceCase(rotor, "mid_domain_mid_rpm");
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample high = sampleReferenceCase(rotor, "high_domain_max_rpm");

		assertFalse(high.blocked());
		assertFalse(high.momentumPowerClosureSatisfied());
		assertFalse(high.runtimeForceReplacementAccepted());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.LINEAR_ADVANCE,
				high.lookup().interpolationStatus());
		assertTrue(high.lookup().thrustCoefficientCt() < mid.lookup().thrustCoefficientCt());
		assertTrue(high.lookup().powerCoefficientCp() > mid.lookup().powerCoefficientCp());
		assertTrue(high.axialAdvanceSpeedMetersPerSecond() > mid.axialAdvanceSpeedMetersPerSecond());
		assertTrue(high.thrustNewtons() > mid.thrustNewtons());
		assertTrue(high.shaftPowerWatts() > mid.shaftPowerWatts());
		assertTrue(high.shaftTorqueNewtonMeters() > mid.shaftTorqueNewtonMeters());
		assertTrue(high.dimensionalSample().idealMomentumPowerOverShaftPower() > 1.0);
		assertEquals(high.shaftPowerWatts() / high.dimensionalSample().angularVelocityRadiansPerSecond(),
				high.shaftTorqueNewtonMeters(), 1.0e-18);
	}

	@Test
	void outOfEnvelopeRotorForceQueryBlocksWithoutForceOrTorque() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery highReference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"high_domain_max_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample blocked =
				PropellerArchiveCtCpJRotorForceModel.sample(new PropellerArchiveCtCpJRotorForceModel.RotorForceQuery(
						"apDrone",
						"high_domain_max_rpm",
						rotor,
						highReference.advanceRatioJ() + 0.20,
						highReference.rpm(),
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				));

		assertTrue(blocked.blocked());
		assertFalse(blocked.clamped());
		assertFalse(blocked.momentumPowerClosureSatisfied());
		assertFalse(blocked.runtimeForceReplacementAccepted());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", blocked.lookup().status());
		assertEquals(0.0, blocked.thrustNewtons(), 1.0e-15);
		assertEquals(0.0, blocked.shaftPowerWatts(), 1.0e-15);
		assertEquals(0.0, blocked.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.0, blocked.thrustForceBodyNewtons().length(), 1.0e-15);
		assertEquals(0.0, blocked.reactionTorqueBodyNewtonMeters().length(), 1.0e-15);
	}

	@Test
	void clampedRotorForceSampleUsesEffectiveEnvelopeForDimensionalForce() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery highReference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"high_domain_max_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sample(new PropellerArchiveCtCpJRotorForceModel.RotorForceQuery(
						"apDrone",
						"high_domain_max_rpm",
						rotor,
						highReference.advanceRatioJ() + 0.20,
						highReference.rpm() * 1.25,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE
				));

		assertFalse(sample.blocked());
		assertTrue(sample.clamped());
		assertFalse(sample.momentumPowerClosureSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals(highReference.rpm(), sample.lookup().effectiveRpm(), 1.0e-9);
		assertEquals(sample.lookup().effectiveRpm(), sample.dimensionalSample().revolutionsPerSecond() * 60.0, 1.0e-9);
		assertEquals(sample.dimensionalSample().axialAdvanceSpeedMetersPerSecond(),
				sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(sample.thrustNewtons(), sample.thrustForceBodyNewtons().length(), 1.0e-15);
		assertTrue(sample.thrustNewtons() > 0.0);
		assertTrue(sample.shaftPowerWatts() > 0.0);
		assertTrue(sample.shaftTorqueNewtonMeters() > 0.0);
	}

	@Test
	void signedAxialStaticAnchoredQueryClampsReverseFlowWithoutRuntimeReplacement() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
						"apDrone",
						"",
						rotor,
						-4.5,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE
				);

		assertFalse(sample.blocked());
		assertTrue(sample.clamped());
		assertTrue(sample.momentumPowerClosureSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals("reverse_axial_static_anchor", sample.lookup().caseName());
		assertEquals("CLAMPED", sample.lookup().status());
		assertEquals("reverse-axial-flow-clamped-to-static-anchor", sample.lookup().message());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.CLAMPED_EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.CLAMPED,
				sample.lookup().lookupStatusCode());
		assertEquals(0.0, sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.0, sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertTrue(sample.thrustNewtons() > 0.0);
		assertTrue(sample.shaftPowerWatts() > 0.0);
		assertTrue(sample.shaftTorqueNewtonMeters() > 0.0);
	}

	@Test
	void signedAxialStaticAnchoredQueryBlocksReverseFlowWhenClampIsNotRequested() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
						"apDrone",
						"",
						rotor,
						-4.5,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertTrue(sample.blocked());
		assertFalse(sample.clamped());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals("reverse_axial_static_anchor", sample.lookup().caseName());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", sample.lookup().status());
		assertEquals("reverse-axial-flow-outside-ct-cp-j-envelope", sample.lookup().message());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED,
				sample.lookup().interpolationStatus());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.OUT_OF_ENVELOPE_BLOCKED,
				sample.lookup().lookupStatusCode());
		assertEquals(0.0, sample.thrustNewtons(), 1.0e-15);
		assertEquals(0.0, sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(0.0, sample.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.0, sample.thrustForceBodyNewtons().length(), 1.0e-15);
		assertEquals(0.0, sample.reactionTorqueBodyNewtonMeters().length(), 1.0e-15);
	}

	@Test
	void rejectsUnsupportedNegativeAxialAdvanceSpeed() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJRotorForceModel.sampleFromAxialAdvanceSpeed(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor,
						-0.1,
						4_000.0,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				));
	}

	private static PropellerArchiveCtCpJRotorForceModel.RotorForceSample sampleReferenceCase(
			RotorSpec rotor,
			String caseName
	) {
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						caseName,
						rotor.radiusMeters() * 2.0,
						RHO
				);
		return PropellerArchiveCtCpJRotorForceModel.sample(new PropellerArchiveCtCpJRotorForceModel.RotorForceQuery(
				"apDrone",
				caseName,
				rotor,
				reference.advanceRatioJ(),
				reference.rpm(),
				RHO,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
		));
	}
}
