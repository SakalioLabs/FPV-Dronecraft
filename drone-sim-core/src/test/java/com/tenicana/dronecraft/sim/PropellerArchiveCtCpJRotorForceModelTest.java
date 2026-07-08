package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
		assertTrue(sample.wakePowerClosureSatisfied());
		assertTrue(sample.runtimeInflowEnvelopeSatisfied());
		assertFalse(sample.runtimeOperatingPointEnvelopeSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(dimensionalReference.thrustNewtons(), sample.thrustNewtons(), 1.0e-15);
		assertEquals(dimensionalReference.shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(dimensionalReference.shaftTorqueNewtonMeters(), sample.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(dimensionalReference.thrustNewtons() / dimensionalReference.diskAreaSquareMeters(),
				sample.actuatorDiskPressureJumpPascals(), 1.0e-12);
		assertEquals(dimensionalReference.diskMassFlowKilogramsPerSecond()
						/ dimensionalReference.diskAreaSquareMeters(),
				sample.actuatorDiskMassFluxKilogramsPerSecondSquareMeter(), 1.0e-12);
		assertEquals(dimensionalReference.idealMomentumPowerWatts() / dimensionalReference.diskAreaSquareMeters(),
				sample.actuatorDiskIdealMomentumPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, sample.transverseAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, sample.transverseAirSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, sample.inflowAngleRadians(), 1.0e-15);
		assertEquals(0.0, sample.thrustForceBodyNewtons().x(), 1.0e-15);
		assertEquals(sample.thrustNewtons(), sample.thrustForceBodyNewtons().y(), 1.0e-15);
		assertEquals(0.0, sample.thrustForceBodyNewtons().z(), 1.0e-15);
		assertEquals(rotor.spinDirection() * sample.shaftTorqueNewtonMeters(),
				sample.reactionTorqueBodyNewtonMeters().y(), 1.0e-18);
		assertVectorEquals(sample.reactionTorqueBodyNewtonMeters(),
				sample.wakeAngularMomentumTorqueBodyNewtonMeters(), 1.0e-18);
		assertVectorEquals(Vec3.ZERO, sample.wakeAngularMomentumTorqueResidualBodyNewtonMeters(), 1.0e-18);
		assertVectorEquals(rotor.thrustAxisBody().multiply(dimensionalReference.farWakeAxialVelocityMetersPerSecond()),
				sample.farWakeAxialVelocityBodyMetersPerSecond(), 1.0e-15);
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
		assertTrue(sample.wakePowerClosureSatisfied());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BILINEAR,
				sample.lookup().interpolationStatus());
		assertEquals(reference.advanceRatioJ(), sample.query().advanceRatioJ(), 1.0e-12);
		assertEquals(reference.rpm(), sample.query().rpm(), 1.0e-9);
		assertEquals(axialAdvanceSpeed, sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertVectorEquals(rotor.thrustAxisBody().multiply(axialAdvanceSpeed),
				sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, sample.transverseAirSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(sample.thrustNewtons(), sample.thrustForceBodyNewtons().length(), 1.0e-15);
		assertTrue(sample.shaftPowerWatts() > 0.0);
		assertTrue(sample.shaftTorqueNewtonMeters() > 0.0);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint operatingPoint =
				sample.standardOperatingPoint();
		assertEquals(0.0, operatingPoint.ambientHumidity(), 1.0e-15);
		assertEquals(omega * rotor.radiusMeters(),
				operatingPoint.rotationalTipSpeedMetersPerSecond(), 1.0e-12);
		assertTrue(operatingPoint.helicalTipSpeedMetersPerSecond()
				> operatingPoint.rotationalTipSpeedMetersPerSecond());
		assertEquals(operatingPoint.helicalTipSpeedMetersPerSecond()
						/ operatingPoint.speedOfSoundMetersPerSecond(),
				operatingPoint.tipMach(), 1.0e-15);
		assertEquals(rotor.representativeBladeChordMeters(),
				operatingPoint.representativeBladeChordMeters(), 1.0e-15);
		assertTrue(operatingPoint.reynoldsNumber() > 10_000.0);
		assertTrue(operatingPoint.reynoldsIndex() > 0.0);
		assertTrue(operatingPoint.runtimeTipMachMargin() > 0.0);
		assertTrue(operatingPoint.runtimeReynoldsIndexMargin() > 0.0);
		assertTrue(operatingPoint.runtimeOperatingEnvelopeMarginFraction() > 0.0);
	}

	@Test
	void runtimeReplacementRejectsSyntheticWakePowerClosureFailure() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				new PropellerArchiveCtCpJLookupEvaluator.LookupResult(
						"apDrone",
						"synthetic_wake_power_reject",
						"synthetic-test",
						reference.advanceRatioJ(),
						reference.rpm(),
						reference.advanceRatioJ(),
						reference.rpm(),
						reference.advanceRatioJ(),
						reference.advanceRatioJ(),
						reference.rpm(),
						reference.rpm(),
						0.0,
						0.0,
						1,
						1,
						0.09325,
						4.0,
						reference.advanceRatioJ() * 0.09325 / 4.0,
						PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
						false,
						false,
						"INTERPOLATED",
						"synthetic-swirl-power-heavy-point"
				);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						lookup,
						reference.propellerDiameterMeters(),
						reference.airDensityKgPerCubicMeter()
				);
		Vec3 relativeAir = rotor.thrustAxisBody().multiply(dimensional.axialAdvanceSpeedMetersPerSecond());
		Vec3 thrustForce = rotor.thrustAxisBody().multiply(dimensional.thrustNewtons());
		Vec3 reactionTorque = rotor.thrustAxisBody()
				.multiply(rotor.spinDirection() * dimensional.shaftTorqueNewtonMeters());
		Vec3 momentArm = rotor.positionBodyMeters();
		Vec3 thrustMoment = momentArm.cross(thrustForce);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				new PropellerArchiveCtCpJRotorForceModel.RotorForceSample(
						new PropellerArchiveCtCpJRotorForceModel.RotorForceQuery(
								"apDrone",
								"synthetic_wake_power_reject",
								rotor,
								reference.advanceRatioJ(),
								reference.rpm(),
								RHO,
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
						),
						lookup,
						dimensional,
						dimensional.axialAdvanceSpeedMetersPerSecond(),
						relativeAir,
						Vec3.ZERO,
						0.0,
						0.0,
						thrustForce,
						reactionTorque,
						momentArm,
						thrustMoment,
						thrustMoment.add(reactionTorque),
						dimensional.shaftTorqueNewtonMeters() / dimensional.thrustNewtons()
				);

		assertTrue(sample.momentumPowerClosureSatisfied());
		assertFalse(sample.wakePowerClosureSatisfied());
		assertTrue(sample.dimensionalSample().idealMomentumPowerOverShaftPower() < 1.0);
		assertTrue(sample.dimensionalSample().totalWakeKineticPowerOverShaftPower() > 1.0);
		assertTrue(sample.runtimeInflowEnvelopeSatisfied());
		assertTrue(sample.runtimeOperatingPointEnvelopeSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
	}

	@Test
	void positiveAxialAdvanceSamplerUsesAmbientOperatingEnvelope() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		double axialAdvanceSpeed = reference.advanceRatioJ() * reference.rpm() / 60.0
				* rotor.radiusMeters() * 2.0;
		double thinAirDensity = RHO * 0.60;

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample standard =
				PropellerArchiveCtCpJRotorForceModel.sampleFromAxialAdvanceSpeed(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor,
						axialAdvanceSpeed,
						omega,
						thinAirDensity,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						25.0,
						0.0
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample hot =
				PropellerArchiveCtCpJRotorForceModel.sampleFromAxialAdvanceSpeed(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor,
						axialAdvanceSpeed,
						omega,
						thinAirDensity,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						65.0,
						0.0
				);

		assertFalse(standard.blocked());
		assertFalse(hot.blocked());
		assertTrue(standard.momentumPowerClosureSatisfied());
		assertTrue(hot.momentumPowerClosureSatisfied());
		assertTrue(standard.runtimeInflowEnvelopeSatisfied());
		assertTrue(hot.runtimeInflowEnvelopeSatisfied());
		assertEquals(25.0, standard.query().ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(65.0, hot.query().ambientTemperatureCelsius(), 1.0e-12);
		assertTrue(standard.operatingPoint(25.0, 0.0).reynoldsIndex() > 0.52);
		assertTrue(hot.operatingPoint(65.0, 0.0).reynoldsIndex() < 0.52);
		assertTrue(standard.runtimeForceReplacementAccepted());
		assertFalse(hot.runtimeForceReplacementAccepted());
		assertEquals(standard.thrustNewtons(), hot.thrustNewtons(), 1.0e-12);
		assertEquals(standard.shaftPowerWatts(), hot.shaftPowerWatts(), 1.0e-12);
		assertEquals(standard.shaftTorqueNewtonMeters(), hot.shaftTorqueNewtonMeters(), 1.0e-15);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample staticAnchoredHot =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromAxialAdvanceSpeed(
						"apDrone",
						"positive_axial_static_ambient",
						rotor,
						axialAdvanceSpeed,
						omega,
						thinAirDensity,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						65.0,
						0.0
				);
		assertFalse(staticAnchoredHot.blocked());
		assertEquals(65.0, staticAnchoredHot.query().ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(reference.advanceRatioJ(), staticAnchoredHot.query().advanceRatioJ(), 1.0e-12);
		assertFalse(staticAnchoredHot.runtimeOperatingPointEnvelopeSatisfied());
	}

	@Test
	void runtimeReplacementRejectsOperatingPointsOutsideMachOrReynoldsEnvelope() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample lowReynolds =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
						"apDrone",
						"low_reynolds_static_anchor",
						rotor,
						0.0,
						hoverOmega,
						RHO * 0.20,
						config.centerOfMassOffsetBodyMeters(),
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						65.0,
						0.0
				);
		double highMachOmega = 0.50 * DroneEnvironment.speedOfSoundMetersPerSecond(25.0)
				/ rotor.radiusMeters();
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample highMach =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
						"apDrone",
						"high_mach_static_anchor",
						rotor,
						0.0,
						highMachOmega,
						RHO,
						config.centerOfMassOffsetBodyMeters(),
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						25.0,
						0.0
				);

		assertFalse(lowReynolds.blocked());
		assertFalse(lowReynolds.clamped());
		assertTrue(lowReynolds.momentumPowerClosureSatisfied());
		assertTrue(lowReynolds.runtimeInflowEnvelopeSatisfied());
		assertTrue(lowReynolds.operatingPoint(65.0, 0.0).reynoldsIndex() < 0.52);
		assertTrue(lowReynolds.operatingPoint(65.0, 0.0).runtimeTipMachMargin() > 0.0);
		assertTrue(lowReynolds.operatingPoint(65.0, 0.0).runtimeReynoldsIndexMargin() < 0.0);
		assertTrue(lowReynolds.operatingPoint(65.0, 0.0).runtimeOperatingEnvelopeMarginFraction() < 0.0);
		assertFalse(lowReynolds.runtimeOperatingPointEnvelopeSatisfied());
		assertFalse(lowReynolds.runtimeForceReplacementAccepted());

		assertFalse(highMach.blocked());
		assertFalse(highMach.clamped());
		assertTrue(highMach.momentumPowerClosureSatisfied());
		assertTrue(highMach.runtimeInflowEnvelopeSatisfied());
		assertTrue(highMach.operatingPoint(25.0, 0.0).tipMach() > 0.46);
		assertTrue(highMach.operatingPoint(25.0, 0.0).runtimeTipMachMargin() < 0.0);
		assertTrue(highMach.operatingPoint(25.0, 0.0).runtimeReynoldsIndexMargin() > 0.0);
		assertTrue(highMach.operatingPoint(25.0, 0.0).runtimeOperatingEnvelopeMarginFraction() < 0.0);
		assertFalse(highMach.runtimeOperatingPointEnvelopeSatisfied());
		assertFalse(highMach.runtimeForceReplacementAccepted());
	}

	@Test
	void relativeAirVelocitySamplePreservesTransverseFlowWithoutInventingSideForce() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double rpm = 6_000.0;
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 2.25;
		Vec3 transverseVelocity = new Vec3(3.0, 0.0, 4.0);
		Vec3 relativeAirVelocity = rotor.thrustAxisBody().multiply(axialSpeed).add(transverseVelocity);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromRelativeAirVelocity(
						"apDrone",
						"relative_velocity_single_rotor",
						rotor,
						relativeAirVelocity,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		double expectedJ = axialSpeed / (rpm / 60.0 * rotor.radiusMeters() * 2.0);
		assertFalse(sample.blocked());
		assertEquals(expectedJ, sample.query().advanceRatioJ(), 1.0e-12);
		assertVectorEquals(relativeAirVelocity, sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertVectorEquals(transverseVelocity, sample.transverseAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertEquals(5.0, sample.transverseAirSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(Math.atan2(5.0, axialSpeed), sample.inflowAngleRadians(), 1.0e-15);
		assertFalse(sample.runtimeInflowEnvelopeSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals(0.0, sample.thrustForceBodyNewtons().x(), 1.0e-15);
		assertEquals(sample.thrustNewtons(), sample.thrustForceBodyNewtons().y(), 1.0e-15);
		assertEquals(0.0, sample.thrustForceBodyNewtons().z(), 1.0e-15);
	}

	@Test
	void nearStaticTransverseFlowRemainsInsideRuntimeReplacementEnvelope() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double rpm = 6_000.0;
		double omega = rpm * 2.0 * Math.PI / 60.0;
		Vec3 relativeAirVelocity = new Vec3(0.20, 0.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromRelativeAirVelocity(
						"apDrone",
						"near_static_oblique_single_rotor",
						rotor,
						relativeAirVelocity,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.momentumPowerClosureSatisfied());
		assertEquals(Math.PI * 0.5, sample.inflowAngleRadians(), 1.0e-8);
		assertTrue(sample.runtimeInflowEnvelopeSatisfied());
		assertTrue(sample.runtimeForceReplacementAccepted());
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
	void rotorForceSampleReportsBodyMomentAndTotalTorqueAboutReferencePoint() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withThrustAxisBody(new Vec3(0.12, 0.98, -0.15));
		Vec3 momentReference = new Vec3(0.010, -0.002, -0.018);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromAxialAdvanceSpeed(
						"apDrone",
						"body_torque_static_anchor",
						rotor,
						0.0,
						omega,
						RHO,
						momentReference,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		Vec3 expectedArm = rotor.positionBodyMeters().subtract(momentReference);
		Vec3 expectedThrustMoment = expectedArm.cross(sample.thrustForceBodyNewtons());
		Vec3 expectedTotalTorque = expectedThrustMoment.add(sample.reactionTorqueBodyNewtonMeters());
		assertFalse(sample.blocked());
		assertVectorEquals(expectedArm, sample.momentArmBodyMeters(), 1.0e-15);
		assertVectorEquals(expectedThrustMoment, sample.thrustMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(expectedTotalTorque, sample.totalTorqueBodyNewtonMeters(), 1.0e-15);
		assertTrue(sample.totalTorqueBodyNewtonMeters().length() > sample.reactionTorqueBodyNewtonMeters().length());
	}

	@Test
	void rotorForceSampleProjectsLocalForceAndMomentIntoWorldFrame() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withThrustAxisBody(new Vec3(0.12, 0.98, -0.15));
		Vec3 momentReference = new Vec3(0.010, -0.002, -0.018);
		Vec3 relativeAirVelocity = rotor.thrustAxisBody().multiply(1.8)
				.add(perpendicularUnit(rotor.thrustAxisBody()).multiply(0.5));
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.25)
		);
		Vec3 momentReferenceWorld = new Vec3(12.0, 64.0, -3.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromRelativeAirVelocity(
						"apDrone",
						"single_rotor_world_projection",
						rotor,
						relativeAirVelocity,
						omega,
						RHO,
						momentReference,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		Vec3 expectedWorldThrustMoment =
				sample.momentArmWorldMeters(bodyToWorld).cross(sample.thrustForceWorldNewtons(bodyToWorld));
		Vec3 expectedWorldTotalTorque =
				expectedWorldThrustMoment.add(sample.reactionTorqueWorldNewtonMeters(bodyToWorld));
		PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application =
				sample.worldForceApplication(7, momentReferenceWorld, bodyToWorld);
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				sample.actuatorDiskSourceTerm(7, momentReferenceWorld, bodyToWorld);
		Vec3 expectedApplicationPoint = momentReferenceWorld.add(sample.momentArmWorldMeters(bodyToWorld));
		Vec3 expectedDiskNormalWorld = bodyToWorld.rotate(rotor.thrustAxisBody()).normalized();
		assertFalse(sample.blocked());
		assertVectorEquals(bodyToWorld.rotate(sample.relativeAirVelocityBodyMetersPerSecond()),
				sample.relativeAirVelocityWorldMetersPerSecond(bodyToWorld), 1.0e-15);
		assertVectorEquals(bodyToWorld.rotate(sample.transverseAirVelocityBodyMetersPerSecond()),
				sample.transverseAirVelocityWorldMetersPerSecond(bodyToWorld), 1.0e-15);
		assertVectorEquals(bodyToWorld.rotate(sample.thrustForceBodyNewtons()),
				sample.thrustForceWorldNewtons(bodyToWorld), 1.0e-15);
		assertVectorEquals(bodyToWorld.rotate(sample.reactionTorqueBodyNewtonMeters()),
				sample.reactionTorqueWorldNewtonMeters(bodyToWorld), 1.0e-15);
		assertVectorEquals(bodyToWorld.rotate(sample.momentArmBodyMeters()),
				sample.momentArmWorldMeters(bodyToWorld), 1.0e-15);
		assertVectorEquals(expectedWorldThrustMoment,
				sample.thrustMomentWorldNewtonMeters(bodyToWorld), 1.0e-15);
		assertVectorEquals(expectedWorldTotalTorque,
				sample.totalTorqueWorldNewtonMeters(bodyToWorld), 1.0e-15);
		assertEquals(sample.thrustForceBodyNewtons().length(),
				sample.thrustForceWorldNewtons(bodyToWorld).length(), 1.0e-15);
		assertVectorEquals(sample.totalTorqueBodyNewtonMeters(),
				sample.totalTorqueWorldNewtonMeters(Quaternion.IDENTITY), 1.0e-15);
		assertEquals(7, application.rotorIndex());
		assertTrue(application.applied());
		assertEquals(sample.runtimeForceReplacementAccepted(), application.runtimeForceReplacementAccepted());
		assertEquals(sample.lookup().status(), application.lookupStatus());
		assertVectorEquals(expectedApplicationPoint, application.forceApplicationPointWorldMeters(), 1.0e-15);
		assertVectorEquals(sample.thrustForceWorldNewtons(bodyToWorld),
				application.thrustForceWorldNewtons(), 1.0e-15);
		assertVectorEquals(sample.reactionTorqueWorldNewtonMeters(bodyToWorld),
				application.reactionTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(expectedWorldThrustMoment, application.thrustMomentWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(expectedWorldTotalTorque, application.totalTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(
				application.forceApplicationPointWorldMeters()
						.subtract(momentReferenceWorld)
						.cross(application.thrustForceWorldNewtons()),
				application.thrustMomentWorldNewtonMeters(),
				1.0e-15
		);
		assertEquals(7, sourceTerm.rotorIndex());
		assertTrue(sourceTerm.applied());
		assertEquals(sample.runtimeForceReplacementAccepted(), sourceTerm.runtimeForceReplacementAccepted());
		assertEquals(sample.lookup().status(), sourceTerm.lookupStatus());
		assertVectorEquals(expectedApplicationPoint, sourceTerm.diskCenterWorldMeters(), 1.0e-15);
		assertVectorEquals(expectedDiskNormalWorld, sourceTerm.diskNormalWorld(), 1.0e-15);
		assertEquals(sample.dimensionalSample().diskAreaSquareMeters(),
				sourceTerm.diskAreaSquareMeters(), 1.0e-15);
		assertEquals(sample.actuatorDiskPressureJumpPascals(),
				sourceTerm.pressureJumpPascals(), 1.0e-12);
		assertEquals(sample.actuatorDiskMassFluxKilogramsPerSecondSquareMeter(),
				sourceTerm.massFluxKilogramsPerSecondSquareMeter(), 1.0e-12);
		assertEquals(sample.actuatorDiskIdealMomentumPowerLoadingWattsPerSquareMeter(),
				sourceTerm.idealMomentumPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertVectorEquals(expectedDiskNormalWorld.multiply(sourceTerm.pressureJumpPascals()),
				sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(), 1.0e-12);
		assertVectorEquals(sample.thrustForceWorldNewtons(bodyToWorld),
				sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter()
						.multiply(sourceTerm.diskAreaSquareMeters()),
				1.0e-12);
		assertVectorEquals(sample.farWakeAxialVelocityWorldMetersPerSecond(bodyToWorld),
				sourceTerm.farWakeAxialVelocityWorldMetersPerSecond(), 1.0e-15);
		assertVectorEquals(sample.reactionTorqueWorldNewtonMeters(bodyToWorld),
				sourceTerm.reactionTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(sample.wakeAngularMomentumTorqueWorldNewtonMeters(bodyToWorld),
				sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(sample.wakeAngularMomentumTorqueResidualWorldNewtonMeters(bodyToWorld),
				sourceTerm.wakeAngularMomentumTorqueResidualWorldNewtonMeters(), 1.0e-15);
		assertEquals(sourceTerm.diskAreaSquareMeters() * 0.1,
				sourceTerm.sourceVolumeCubicMeters(0.1), 1.0e-15);
		assertVectorEquals(sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters()
						.multiply(1.0 / sourceTerm.sourceVolumeCubicMeters(0.1)),
				sourceTerm.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(0.1),
				1.0e-15);
		assertEquals(sample.dimensionalSample().angularMomentumSwirlRadiusMeters(),
				sourceTerm.angularMomentumSwirlRadiusMeters(), 1.0e-15);
		assertEquals(sample.dimensionalSample().wakeTangentialVelocityMetersPerSecond(),
				sourceTerm.wakeTangentialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.dimensionalSample().wakeSwirlKineticPowerWatts(),
				sourceTerm.wakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerWatts(),
				sourceTerm.totalWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(sample.dimensionalSample().wakeSwirlKineticPowerOverShaftPower(),
				sourceTerm.wakeSwirlKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerOverShaftPower(),
				sourceTerm.totalWakeKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerResidualWatts(),
				sourceTerm.totalWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerResidualFraction(),
				sourceTerm.totalWakeKineticPowerResidualFraction(), 1.0e-15);
		Vec3 swirlRadialDirection = perpendicularUnit(sourceTerm.diskNormalWorld());
		Vec3 swirlReferencePoint = sourceTerm.diskCenterWorldMeters()
				.add(swirlRadialDirection.multiply(sourceTerm.angularMomentumSwirlRadiusMeters()));
		Vec3 swirlVelocity = sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(swirlReferencePoint);
		assertEquals(sourceTerm.wakeSwirlAngularVelocityRadiansPerSecond()
						* Math.min(sourceTerm.angularMomentumSwirlRadiusMeters(), sourceTerm.diskRadiusMeters()),
				swirlVelocity.length(), 1.0e-12);
		assertEquals(0.0, swirlVelocity.dot(swirlRadialDirection), 1.0e-12);
		assertEquals(0.0, swirlVelocity.dot(sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters()), 1.0e-12);
		assertTrue(swirlRadialDirection.cross(swirlVelocity.normalized())
				.dot(sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters()) > 0.0);
		Vec3 rimSwirlVelocity = sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(
				sourceTerm.diskCenterWorldMeters().add(swirlRadialDirection.multiply(sourceTerm.diskRadiusMeters())));
		assertEquals(sourceTerm.wakeSwirlAngularVelocityRadiansPerSecond() * sourceTerm.diskRadiusMeters(),
				rimSwirlVelocity.length(), 1.0e-12);
		assertTrue(rimSwirlVelocity.length() > swirlVelocity.length());
		assertVectorEquals(swirlVelocity.multiply(-1.0),
				sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(sourceTerm.diskCenterWorldMeters()
						.subtract(swirlRadialDirection.multiply(sourceTerm.angularMomentumSwirlRadiusMeters()))),
				1.0e-12);
		assertVectorEquals(swirlVelocity,
				sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(
						swirlReferencePoint.add(sourceTerm.diskNormalWorld().multiply(2.0))),
				1.0e-12);
		assertVectorEquals(Vec3.ZERO,
				sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(sourceTerm.diskCenterWorldMeters()),
				1.0e-15);
		assertVectorEquals(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter().multiply(10.0),
				sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(0.1), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(0.0), 1.0e-15);
		assertEquals(0.0, sourceTerm.sourceVolumeCubicMeters(0.0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				sourceTerm.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(0.0),
				1.0e-15);
	}

	@Test
	void staticAnchoredConfigurationHoverAggregatesSymmetricBodyForceAndTorque() {
		DroneConfig config = DroneConfig.apDrone();
		double hoverRpm = 6_000.0;
		double hoverOmega = hoverRpm * 2.0 * Math.PI / 60.0;
		double[] axialSpeeds = new double[config.rotors().size()];
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			omegas[i] = hoverOmega;
		}

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						"apDrone",
						"aggregate_hover_static_anchor",
						config,
						axialSpeeds,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		double expectedThrust = aggregate.rotorSamples().stream()
				.mapToDouble(PropellerArchiveCtCpJRotorForceModel.RotorForceSample::thrustNewtons)
				.sum();
		double expectedDiskMassFlow = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().diskMassFlowKilogramsPerSecond())
				.sum();
		double expectedUsefulAxialPower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().usefulAxialThrustPowerWatts())
				.sum();
		double expectedIdealInducedPower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().idealInducedPowerWatts())
				.sum();
		double expectedIdealMomentumPower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().idealMomentumPowerWatts())
				.sum();
		double expectedWakeSwirlPower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().wakeSwirlKineticPowerWatts())
				.sum();
		double expectedWakePower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().totalWakeKineticPowerWatts())
				.sum();
		double expectedDiskArea = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().diskAreaSquareMeters())
				.sum();
		assertEquals(config.rotors().size(), aggregate.rotorSamples().size());
		assertEquals(config.rotors().size(), aggregate.acceptedRotorCount());
		assertEquals(config.rotors().size(), aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(0, aggregate.blockedRotorCount());
		assertEquals(0, aggregate.clampedRotorCount());
		assertEquals(expectedThrust, aggregate.totalThrustNewtons(), 1.0e-12);
		assertEquals(expectedThrust, aggregate.totalThrustForceBodyNewtons().y(), 1.0e-12);
		assertEquals(0.0, aggregate.totalThrustForceBodyNewtons().x(), 1.0e-15);
		assertEquals(0.0, aggregate.totalThrustForceBodyNewtons().z(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalWakeAngularMomentumTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalWakeAngularMomentumTorqueResidualBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalThrustMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalBodyTorqueNewtonMeters(), 1.0e-15);
		assertVectorEquals(aggregate.totalThrustForceBodyNewtons(),
				aggregate.runtimeForceReplacementThrustForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(aggregate.totalReactionTorqueBodyNewtonMeters(),
				aggregate.runtimeForceReplacementReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(aggregate.totalWakeAngularMomentumTorqueBodyNewtonMeters(),
				aggregate.runtimeForceReplacementWakeAngularMomentumTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				aggregate.runtimeForceReplacementWakeAngularMomentumTorqueResidualBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(aggregate.totalThrustMomentBodyNewtonMeters(),
				aggregate.runtimeForceReplacementThrustMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(aggregate.totalBodyTorqueNewtonMeters(),
				aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters(), 1.0e-15);
		assertEquals(aggregate.totalThrustNewtons(), aggregate.runtimeForceReplacementThrustNewtons(), 1.0e-12);
		assertEquals(aggregate.totalShaftPowerWatts(), aggregate.runtimeForceReplacementShaftPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalShaftTorqueNewtonMeters(),
				aggregate.runtimeForceReplacementShaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(expectedDiskMassFlow, aggregate.totalDiskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(expectedUsefulAxialPower, aggregate.totalUsefulAxialThrustPowerWatts(), 1.0e-12);
		assertEquals(expectedIdealInducedPower, aggregate.totalIdealInducedPowerWatts(), 1.0e-12);
		assertEquals(expectedIdealMomentumPower, aggregate.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(
				aggregate.totalUsefulAxialThrustPowerWatts() + aggregate.totalIdealInducedPowerWatts(),
				aggregate.totalIdealMomentumPowerWatts(),
				1.0e-12
		);
		assertEquals(expectedWakeSwirlPower, aggregate.totalWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(expectedWakePower, aggregate.totalWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalShaftPowerWatts() - aggregate.totalWakeKineticPowerWatts(),
				aggregate.totalWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(aggregate.totalWakeKineticPowerWatts() / aggregate.totalShaftPowerWatts(),
				aggregate.totalWakeKineticPowerOverShaftPower(), 1.0e-12);
		assertEquals(expectedDiskArea, aggregate.totalActuatorDiskAreaSquareMeters(), 1.0e-15);
		assertEquals(aggregate.totalThrustNewtons() / aggregate.totalActuatorDiskAreaSquareMeters(),
				aggregate.meanActuatorDiskPressureJumpPascals(), 1.0e-12);
		assertEquals(aggregate.totalDiskMassFlowKilogramsPerSecond()
						/ aggregate.totalActuatorDiskAreaSquareMeters(),
				aggregate.meanActuatorDiskMassFluxKilogramsPerSecondSquareMeter(), 1.0e-12);
		assertEquals(aggregate.totalIdealMomentumPowerWatts() / aggregate.totalActuatorDiskAreaSquareMeters(),
				aggregate.meanActuatorDiskIdealMomentumPowerLoadingWattsPerSquareMeter(), 1.0e-12);
		assertEquals(aggregate.totalDiskMassFlowKilogramsPerSecond(),
				aggregate.runtimeForceReplacementDiskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(aggregate.totalUsefulAxialThrustPowerWatts(),
				aggregate.runtimeForceReplacementUsefulAxialThrustPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalIdealInducedPowerWatts(),
				aggregate.runtimeForceReplacementIdealInducedPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalIdealMomentumPowerWatts(),
				aggregate.runtimeForceReplacementIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalWakeSwirlKineticPowerWatts(),
				aggregate.runtimeForceReplacementWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalWakeKineticPowerWatts(),
				aggregate.runtimeForceReplacementWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalWakeKineticPowerResidualWatts(),
				aggregate.runtimeForceReplacementWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(aggregate.totalWakeKineticPowerOverShaftPower(),
				aggregate.runtimeForceReplacementWakeKineticPowerOverShaftPower(), 1.0e-12);
		assertEquals(aggregate.totalActuatorDiskAreaSquareMeters(),
				aggregate.runtimeForceReplacementActuatorDiskAreaSquareMeters(), 1.0e-15);
		assertEquals(aggregate.meanActuatorDiskPressureJumpPascals(),
				aggregate.runtimeForceReplacementMeanActuatorDiskPressureJumpPascals(), 1.0e-12);
		assertEquals(aggregate.meanActuatorDiskMassFluxKilogramsPerSecondSquareMeter(),
				aggregate.runtimeForceReplacementMeanActuatorDiskMassFluxKilogramsPerSecondSquareMeter(), 1.0e-12);
		assertEquals(aggregate.meanActuatorDiskIdealMomentumPowerLoadingWattsPerSquareMeter(),
				aggregate.runtimeForceReplacementMeanActuatorDiskIdealMomentumPowerLoadingWattsPerSquareMeter(),
				1.0e-12);
		assertTrue(aggregate.totalShaftPowerWatts() > 0.0);
		assertTrue(aggregate.totalShaftTorqueNewtonMeters() > 0.0);
	}

	@Test
	void aggregateProjectsBodyForcesAndTorquesIntoWorldFrame() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double rpm = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient()) * 60.0 / (2.0 * Math.PI);
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < omegas.length; i++) {
			omegas[i] = omega;
		}
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, 3.0, 0.0);
		Vec3 angularVelocityBody = new Vec3(4.0, 0.0, 0.0);
		Quaternion quarterTurnAroundZ = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.25)
		);
		Vec3 momentReferenceWorld = new Vec3(-8.0, 70.0, 4.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromBodyKinematics(
						"apDrone",
						"aggregate_world_projection",
						config,
						bodyRelativeAirVelocity,
						angularVelocityBody,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications =
				aggregate.rotorWorldForceApplications(momentReferenceWorld, quarterTurnAroundZ);
		List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> runtimeApplications =
				aggregate.runtimeForceReplacementRotorWorldForceApplications(momentReferenceWorld, quarterTurnAroundZ);
		List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms =
				aggregate.rotorActuatorDiskSourceTerms(momentReferenceWorld, quarterTurnAroundZ);
		List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> runtimeSourceTerms =
				aggregate.runtimeForceReplacementRotorActuatorDiskSourceTerms(momentReferenceWorld, quarterTurnAroundZ);

		assertEquals(config.rotors().size(), aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(config.rotors().size(), applications.size());
		assertEquals(config.rotors().size(), runtimeApplications.size());
		assertEquals(config.rotors().size(), sourceTerms.size());
		assertEquals(config.rotors().size(), runtimeSourceTerms.size());
		assertTrue(aggregate.totalThrustForceBodyNewtons().length() > 0.0);
		assertTrue(aggregate.totalBodyTorqueNewtonMeters().length() > 0.0);
		assertVectorEquals(quarterTurnAroundZ.rotate(aggregate.totalThrustForceBodyNewtons()),
				aggregate.totalThrustForceWorldNewtons(quarterTurnAroundZ), 1.0e-12);
		assertVectorEquals(quarterTurnAroundZ.rotate(aggregate.totalReactionTorqueBodyNewtonMeters()),
				aggregate.totalReactionTorqueWorldNewtonMeters(quarterTurnAroundZ), 1.0e-15);
		assertVectorEquals(quarterTurnAroundZ.rotate(aggregate.totalThrustMomentBodyNewtonMeters()),
				aggregate.totalThrustMomentWorldNewtonMeters(quarterTurnAroundZ), 1.0e-12);
		assertVectorEquals(quarterTurnAroundZ.rotate(aggregate.totalBodyTorqueNewtonMeters()),
				aggregate.totalBodyTorqueWorldNewtonMeters(quarterTurnAroundZ), 1.0e-12);
		assertVectorEquals(aggregate.totalThrustForceBodyNewtons(),
				aggregate.totalThrustForceWorldNewtons(Quaternion.IDENTITY), 1.0e-15);
		assertVectorEquals(aggregate.runtimeForceReplacementThrustForceWorldNewtons(quarterTurnAroundZ),
				aggregate.totalThrustForceWorldNewtons(quarterTurnAroundZ), 1.0e-12);
		assertVectorEquals(aggregate.runtimeForceReplacementTotalBodyTorqueWorldNewtonMeters(quarterTurnAroundZ),
				aggregate.totalBodyTorqueWorldNewtonMeters(quarterTurnAroundZ), 1.0e-12);
		assertEquals(aggregate.totalThrustForceBodyNewtons().length(),
				aggregate.totalThrustForceWorldNewtons(quarterTurnAroundZ).length(), 1.0e-12);
		assertEquals(aggregate.totalBodyTorqueNewtonMeters().length(),
				aggregate.totalBodyTorqueWorldNewtonMeters(quarterTurnAroundZ).length(), 1.0e-12);
		assertVectorEquals(aggregate.totalThrustForceWorldNewtons(quarterTurnAroundZ),
				sumWorldThrustForce(applications), 1.0e-12);
		assertVectorEquals(aggregate.totalReactionTorqueWorldNewtonMeters(quarterTurnAroundZ),
				sumWorldReactionTorque(applications), 1.0e-12);
		assertVectorEquals(aggregate.totalThrustMomentWorldNewtonMeters(quarterTurnAroundZ),
				sumWorldThrustMoment(applications), 1.0e-12);
		assertVectorEquals(aggregate.totalBodyTorqueWorldNewtonMeters(quarterTurnAroundZ),
				sumWorldTotalTorque(applications), 1.0e-12);
		assertVectorEquals(aggregate.runtimeForceReplacementThrustForceWorldNewtons(quarterTurnAroundZ),
				sumWorldThrustForce(runtimeApplications), 1.0e-12);
		assertVectorEquals(aggregate.runtimeForceReplacementTotalBodyTorqueWorldNewtonMeters(quarterTurnAroundZ),
				sumWorldTotalTorque(runtimeApplications), 1.0e-12);
		assertVectorEquals(aggregate.totalThrustForceWorldNewtons(quarterTurnAroundZ),
				sumWorldActuatorDiskSurfaceForce(sourceTerms), 1.0e-12);
		assertVectorEquals(aggregate.runtimeForceReplacementThrustForceWorldNewtons(quarterTurnAroundZ),
				sumWorldActuatorDiskSurfaceForce(runtimeSourceTerms), 1.0e-12);
		for (int i = 0; i < applications.size(); i++) {
			PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application =
					applications.get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
					sourceTerms.get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample runtimeSourceTerm =
					runtimeSourceTerms.get(i);
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample = aggregate.rotorSamples().get(i);
			assertEquals(i, application.rotorIndex());
			assertEquals(i, sourceTerm.rotorIndex());
			assertTrue(application.applied());
			assertTrue(sourceTerm.applied());
			assertVectorEquals(sample.forceApplicationPointWorldMeters(momentReferenceWorld, quarterTurnAroundZ),
					application.forceApplicationPointWorldMeters(), 1.0e-15);
			assertVectorEquals(application.forceApplicationPointWorldMeters(),
					sourceTerm.diskCenterWorldMeters(), 1.0e-15);
			assertVectorEquals(quarterTurnAroundZ.rotate(sample.query().rotor().thrustAxisBody()).normalized(),
					sourceTerm.diskNormalWorld(), 1.0e-15);
			assertVectorEquals(application.thrustForceWorldNewtons(),
					sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter()
							.multiply(sourceTerm.diskAreaSquareMeters()),
					1.0e-12);
			assertVectorEquals(sample.farWakeAxialVelocityWorldMetersPerSecond(quarterTurnAroundZ),
					sourceTerm.farWakeAxialVelocityWorldMetersPerSecond(), 1.0e-15);
			assertVectorEquals(application.reactionTorqueWorldNewtonMeters(),
					sourceTerm.reactionTorqueWorldNewtonMeters(), 1.0e-15);
			assertVectorEquals(sample.wakeAngularMomentumTorqueWorldNewtonMeters(quarterTurnAroundZ),
					sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
			assertVectorEquals(sample.wakeAngularMomentumTorqueResidualWorldNewtonMeters(quarterTurnAroundZ),
					sourceTerm.wakeAngularMomentumTorqueResidualWorldNewtonMeters(), 1.0e-15);
			assertVectorEquals(
					application.forceApplicationPointWorldMeters()
							.subtract(momentReferenceWorld)
							.cross(application.thrustForceWorldNewtons()),
					application.thrustMomentWorldNewtonMeters(),
					1.0e-12
			);
			assertTrue(runtimeApplications.get(i).applied());
			assertTrue(runtimeSourceTerm.applied());
			assertVectorEquals(application.forceApplicationPointWorldMeters(),
					runtimeApplications.get(i).forceApplicationPointWorldMeters(), 1.0e-15);
			assertVectorEquals(sourceTerm.diskCenterWorldMeters(), runtimeSourceTerm.diskCenterWorldMeters(), 1.0e-15);
			assertVectorEquals(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(),
					runtimeSourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(), 1.0e-12);
			assertVectorEquals(sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters(),
					runtimeSourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
			assertEquals(sourceTerm.wakeTangentialVelocityMetersPerSecond(),
					runtimeSourceTerm.wakeTangentialVelocityMetersPerSecond(), 1.0e-15);
		}
	}

	@Test
	void staticAnchoredConfigurationAggregateUsesAmbientOperatingEnvelope() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double[] axialSpeeds = new double[config.rotors().size()];
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			omegas[i] = hoverOmega;
		}
		double thinAirDensity = RHO * 0.28;

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample standard =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						"apDrone",
						"aggregate_standard_low_density",
						config,
						axialSpeeds,
						omegas,
						thinAirDensity,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample hot =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						"apDrone",
						"aggregate_hot_low_density",
						config,
						axialSpeeds,
						omegas,
						thinAirDensity,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						65.0,
						0.0
				);

		assertEquals(config.rotors().size(), standard.acceptedRotorCount());
		assertEquals(config.rotors().size(), hot.acceptedRotorCount());
		assertEquals(config.rotors().size(), standard.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(0, hot.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(0, hot.blockedRotorCount());
		assertEquals(0, hot.clampedRotorCount());
		assertTrue(standard.rotorSamples().get(0).runtimeOperatingPointEnvelopeSatisfied());
		assertFalse(hot.rotorSamples().get(0).runtimeOperatingPointEnvelopeSatisfied());
		assertTrue(hot.rotorSamples().get(0).operatingPoint(65.0, 0.0).reynoldsIndex() < 0.52);
		assertEquals(standard.totalThrustNewtons(), hot.totalThrustNewtons(), 1.0e-12);
		assertEquals(standard.totalThrustNewtons(), standard.runtimeForceReplacementThrustNewtons(), 1.0e-12);
		assertEquals(standard.totalShaftPowerWatts(), standard.runtimeForceReplacementShaftPowerWatts(), 1.0e-12);
		assertEquals(0.0, hot.runtimeForceReplacementThrustNewtons(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementShaftPowerWatts(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementShaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementDiskMassFlowKilogramsPerSecond(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementUsefulAxialThrustPowerWatts(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementIdealInducedPowerWatts(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(0.0, hot.runtimeForceReplacementWakeKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(standard.totalWakeKineticPowerWatts(),
				standard.runtimeForceReplacementWakeKineticPowerWatts(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, hot.runtimeForceReplacementThrustForceBodyNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, hot.runtimeForceReplacementTotalBodyTorqueNewtonMeters(), 1.0e-15);
		assertTrue(hot.totalShaftPowerWatts() > 0.0);
		assertTrue(hot.totalWakeKineticPowerWatts() > 0.0);
		assertTrue(hot.totalBodyTorqueNewtonMeters().isFinite());
	}

	@Test
	void relativeAirVelocityConfigurationProjectsEachRotorAxisBeforeLookup() {
		DroneConfig config = DroneConfig.apDrone().withRotorOutwardCantDegrees(8.0);
		double rpm = 6_000.0;
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 2.75;
		Vec3[] relativeAirVelocities = new Vec3[config.rotors().size()];
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			Vec3 axis = rotor.thrustAxisBody();
			Vec3 transverseVelocity = perpendicularUnit(axis).multiply(5.0);
			relativeAirVelocities[i] = axis.multiply(axialSpeed).add(transverseVelocity);
			omegas[i] = omega;
			assertEquals(axialSpeed, relativeAirVelocities[i].dot(axis), 1.0e-12);
		}

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
						"apDrone",
						"relative_velocity_aggregate",
						config,
						relativeAirVelocities,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		double expectedJ = axialSpeed / (rpm / 60.0 * config.rotors().get(0).radiusMeters() * 2.0);
		double expectedForceY = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.thrustForceBodyNewtons().y())
				.sum();
		assertEquals(config.rotors().size(), aggregate.acceptedRotorCount());
		assertEquals(0, aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(0, aggregate.blockedRotorCount());
		assertEquals(expectedJ, aggregate.rotorSamples().get(0).query().advanceRatioJ(), 1.0e-12);
		assertEquals(expectedJ, aggregate.rotorSamples().get(2).query().advanceRatioJ(), 1.0e-12);
		assertVectorEquals(relativeAirVelocities[0],
				aggregate.rotorSamples().get(0).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertEquals(5.0, aggregate.rotorSamples().get(0).transverseAirSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(Math.atan2(5.0, axialSpeed),
				aggregate.rotorSamples().get(0).inflowAngleRadians(), 1.0e-12);
		assertFalse(aggregate.rotorSamples().get(0).runtimeInflowEnvelopeSatisfied());
		assertFalse(aggregate.rotorSamples().get(0).runtimeForceReplacementAccepted());
		assertEquals(expectedForceY, aggregate.totalThrustForceBodyNewtons().y(), 1.0e-12);
		assertEquals(0.0, aggregate.totalThrustForceBodyNewtons().x(), 1.0e-12);
		assertEquals(0.0, aggregate.totalThrustForceBodyNewtons().z(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.totalReactionTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.totalThrustMomentBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.totalBodyTorqueNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.runtimeForceReplacementThrustForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.runtimeForceReplacementReactionTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.runtimeForceReplacementThrustMomentBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters(), 1.0e-12);
		assertEquals(0.0, aggregate.runtimeForceReplacementThrustNewtons(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementShaftPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementShaftTorqueNewtonMeters(), 1.0e-15);
		assertTrue(aggregate.totalShaftPowerWatts() > 0.0);
	}

	@Test
	void bodyKinematicsConfigurationAddsLocalRotorAirVelocityFromBodyRate() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double rpm = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient()) * 60.0 / (2.0 * Math.PI);
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 0.4064 * rpm / 60.0 * rotor.radiusMeters() * 2.0;
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, axialSpeed, 0.0);
		Vec3 angularVelocityBody = new Vec3(5.0, 0.0, 0.0);
		Vec3[] expectedRelativeAirVelocities = new Vec3[config.rotors().size()];
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			Vec3 rotorArm = config.rotors().get(i).positionBodyMeters()
					.subtract(config.centerOfMassOffsetBodyMeters());
			expectedRelativeAirVelocities[i] = bodyRelativeAirVelocity.add(angularVelocityBody.cross(rotorArm));
			omegas[i] = omega;
		}

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample kinematic =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromBodyKinematics(
						"apDrone",
						"body_rate_roll_kinematics",
						config,
						bodyRelativeAirVelocity,
						angularVelocityBody,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample direct =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
						"apDrone",
						"body_rate_roll_kinematics",
						config,
						expectedRelativeAirVelocities,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(config.rotors().size(), kinematic.acceptedRotorCount());
		assertEquals(config.rotors().size(), kinematic.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(0, kinematic.blockedRotorCount());
		assertEquals(0, kinematic.clampedRotorCount());
		assertTrue(kinematic.rotorSamples().get(0).query().advanceRatioJ()
				< kinematic.rotorSamples().get(2).query().advanceRatioJ());
		assertTrue(kinematic.rotorSamples().get(0).thrustNewtons()
				> kinematic.rotorSamples().get(2).thrustNewtons());
		for (int i = 0; i < config.rotors().size(); i++) {
			assertVectorEquals(expectedRelativeAirVelocities[i],
					kinematic.rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
			assertEquals(direct.rotorSamples().get(i).query().advanceRatioJ(),
					kinematic.rotorSamples().get(i).query().advanceRatioJ(), 1.0e-15);
			assertEquals(direct.rotorSamples().get(i).thrustNewtons(),
					kinematic.rotorSamples().get(i).thrustNewtons(), 1.0e-12);
		}
		assertVectorEquals(direct.totalThrustForceBodyNewtons(),
				kinematic.totalThrustForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(direct.totalBodyTorqueNewtonMeters(),
				kinematic.totalBodyTorqueNewtonMeters(), 1.0e-12);
		assertEquals(direct.totalShaftPowerWatts(), kinematic.totalShaftPowerWatts(), 1.0e-12);
		assertTrue(Math.abs(kinematic.totalBodyTorqueNewtonMeters().x()) > 1.0e-3);
		assertEquals(0.0, kinematic.totalReactionTorqueBodyNewtonMeters().y(), 1.0e-12);
	}

	@Test
	void worldKinematicsConfigurationMatchesBodyKinematicsForIdentityOrientation() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double rpm = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient()) * 60.0 / (2.0 * Math.PI);
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 0.4064 * rpm / 60.0 * rotor.radiusMeters() * 2.0;
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, axialSpeed, 0.0);
		Vec3 angularVelocityBody = new Vec3(4.0, 0.0, 0.0);
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < omegas.length; i++) {
			omegas[i] = omega;
		}

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample body =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromBodyKinematics(
						"apDrone",
						"world_identity_body_reference",
						config,
						bodyRelativeAirVelocity,
						angularVelocityBody,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample world =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"world_identity_kinematics",
						config,
						Quaternion.IDENTITY,
						bodyRelativeAirVelocity,
						angularVelocityBody,
						Vec3.ZERO,
						null,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(config.rotors().size(), world.acceptedRotorCount());
		assertEquals(0, world.blockedRotorCount());
		assertEquals(body.totalThrustNewtons(), world.totalThrustNewtons(), 1.0e-12);
		assertEquals(body.totalShaftPowerWatts(), world.totalShaftPowerWatts(), 1.0e-12);
		assertVectorEquals(body.totalThrustForceBodyNewtons(), world.totalThrustForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(body.totalBodyTorqueNewtonMeters(), world.totalBodyTorqueNewtonMeters(), 1.0e-12);
		for (int i = 0; i < config.rotors().size(); i++) {
			assertVectorEquals(body.rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(),
					world.rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
			assertEquals(body.rotorSamples().get(i).query().advanceRatioJ(),
					world.rotorSamples().get(i).query().advanceRatioJ(), 1.0e-15);
			assertEquals(body.rotorSamples().get(i).shaftTorqueNewtonMeters(),
					world.rotorSamples().get(i).shaftTorqueNewtonMeters(), 1.0e-15);
		}
	}

	@Test
	void worldKinematicsConfigurationUsesPerRotorWindWithoutTouchingOtherRotors() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double rpm = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient()) * 60.0 / (2.0 * Math.PI);
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 0.4064 * rpm / 60.0 * rotor.radiusMeters() * 2.0;
		Vec3 vehicleVelocityWorld = new Vec3(0.0, axialSpeed, 0.0);
		Vec3[] rotorWinds = new Vec3[config.rotors().size()];
		rotorWinds[0] = new Vec3(0.0, 1.0, 0.0);
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < omegas.length; i++) {
			omegas[i] = omega;
		}

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample baseline =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"world_wind_baseline",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						Vec3.ZERO,
						null,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample localWind =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"world_per_rotor_wind",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						Vec3.ZERO,
						rotorWinds,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(config.rotors().size(), localWind.acceptedRotorCount());
		assertEquals(0, localWind.blockedRotorCount());
		assertVectorEquals(new Vec3(0.0, axialSpeed - 1.0, 0.0),
				localWind.rotorSamples().get(0).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertTrue(localWind.rotorSamples().get(0).query().advanceRatioJ()
				< baseline.rotorSamples().get(0).query().advanceRatioJ());
		assertTrue(localWind.rotorSamples().get(0).thrustNewtons()
				> baseline.rotorSamples().get(0).thrustNewtons());
		for (int i = 1; i < config.rotors().size(); i++) {
			assertVectorEquals(baseline.rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(),
					localWind.rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
			assertEquals(baseline.rotorSamples().get(i).query().advanceRatioJ(),
					localWind.rotorSamples().get(i).query().advanceRatioJ(), 1.0e-15);
			assertEquals(baseline.rotorSamples().get(i).thrustNewtons(),
					localWind.rotorSamples().get(i).thrustNewtons(), 1.0e-12);
		}
		assertTrue(Math.abs(localWind.totalBodyTorqueNewtonMeters().x()
				- baseline.totalBodyTorqueNewtonMeters().x()) > 1.0e-4);
	}

	@Test
	void environmentKinematicsConfigurationMatchesCalmWorldKinematics() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double rpm = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient()) * 60.0 / (2.0 * Math.PI);
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 0.4064 * rpm / 60.0 * rotor.radiusMeters() * 2.0;
		Vec3 vehicleVelocityWorld = new Vec3(0.0, axialSpeed, 0.0);
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < omegas.length; i++) {
			omegas[i] = omega;
		}

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample world =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"environment_calm_world_reference",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						Vec3.ZERO,
						null,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						25.0,
						0.0
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample environment =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromEnvironmentKinematics(
						"apDrone",
						"environment_calm_kinematics",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						omegas,
						DroneEnvironment.calm(),
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		assertEquals(config.rotors().size(), environment.acceptedRotorCount());
		assertEquals(0, environment.blockedRotorCount());
		assertEquals(world.totalThrustNewtons(), environment.totalThrustNewtons(), 1.0e-12);
		assertEquals(world.totalShaftPowerWatts(), environment.totalShaftPowerWatts(), 1.0e-12);
		assertVectorEquals(world.totalThrustForceBodyNewtons(),
				environment.totalThrustForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(world.totalBodyTorqueNewtonMeters(),
				environment.totalBodyTorqueNewtonMeters(), 1.0e-12);
		assertEquals(RHO, environment.rotorSamples().get(0).query().airDensityKgPerCubicMeter(), 1.0e-15);
		assertEquals(25.0, environment.rotorSamples().get(0).query().ambientTemperatureCelsius(), 1.0e-15);
	}

	@Test
	void aggregatePreservesBlockedCounts() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = 6_000.0 * 2.0 * Math.PI / 60.0;
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample blocked =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromAxialAdvanceSpeed(
						"apDrone",
						"aggregate_high_j_block",
						rotor,
						1.20 * omega / (2.0 * Math.PI) * rotor.radiusMeters() * 2.0,
						omega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.aggregate(List.of(blocked));

		assertTrue(blocked.blocked());
		assertEquals(0, aggregate.acceptedRotorCount());
		assertEquals(0, aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(1, aggregate.blockedRotorCount());
		assertEquals(0.0, aggregate.totalThrustNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalBodyTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementThrustNewtons(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementShaftPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementShaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.0, aggregate.totalDiskMassFlowKilogramsPerSecond(), 1.0e-15);
		assertEquals(0.0, aggregate.totalUsefulAxialThrustPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.totalIdealInducedPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementDiskMassFlowKilogramsPerSecond(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementUsefulAxialThrustPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementIdealInducedPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.totalWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.totalWakeKineticPowerResidualWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.totalWakeKineticPowerOverShaftPower(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementWakeKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, aggregate.runtimeForceReplacementWakeKineticPowerResidualWatts(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters(), 1.0e-15);
	}

	@Test
	void aggregateSeparatesRuntimeAcceptedSubsetTotals() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample accepted =
				sampleReferenceCase(rotor, "mid_domain_mid_rpm");
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample referenceOnly =
				sampleReferenceCase(rotor, "high_domain_max_rpm");

		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.aggregate(List.of(accepted, referenceOnly));

		assertTrue(accepted.runtimeForceReplacementAccepted());
		assertFalse(referenceOnly.blocked());
		assertFalse(referenceOnly.runtimeForceReplacementAccepted());
		assertEquals(2, aggregate.acceptedRotorCount());
		assertEquals(1, aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(0, aggregate.blockedRotorCount());
		assertEquals(accepted.thrustNewtons() + referenceOnly.thrustNewtons(),
				aggregate.totalThrustNewtons(), 1.0e-12);
		assertEquals(accepted.shaftPowerWatts() + referenceOnly.shaftPowerWatts(),
				aggregate.totalShaftPowerWatts(), 1.0e-12);
		assertEquals(accepted.shaftTorqueNewtonMeters() + referenceOnly.shaftTorqueNewtonMeters(),
				aggregate.totalShaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(accepted.dimensionalSample().diskMassFlowKilogramsPerSecond()
						+ referenceOnly.dimensionalSample().diskMassFlowKilogramsPerSecond(),
				aggregate.totalDiskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().usefulAxialThrustPowerWatts()
						+ referenceOnly.dimensionalSample().usefulAxialThrustPowerWatts(),
				aggregate.totalUsefulAxialThrustPowerWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().idealInducedPowerWatts()
						+ referenceOnly.dimensionalSample().idealInducedPowerWatts(),
				aggregate.totalIdealInducedPowerWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().idealMomentumPowerWatts()
						+ referenceOnly.dimensionalSample().idealMomentumPowerWatts(),
				aggregate.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(
				aggregate.totalUsefulAxialThrustPowerWatts() + aggregate.totalIdealInducedPowerWatts(),
				aggregate.totalIdealMomentumPowerWatts(),
				1.0e-12
		);
		assertEquals(accepted.dimensionalSample().wakeSwirlKineticPowerWatts()
						+ referenceOnly.dimensionalSample().wakeSwirlKineticPowerWatts(),
				aggregate.totalWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().totalWakeKineticPowerWatts()
						+ referenceOnly.dimensionalSample().totalWakeKineticPowerWatts(),
				aggregate.totalWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalShaftPowerWatts() - aggregate.totalWakeKineticPowerWatts(),
				aggregate.totalWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(aggregate.totalWakeKineticPowerWatts() / aggregate.totalShaftPowerWatts(),
				aggregate.totalWakeKineticPowerOverShaftPower(), 1.0e-12);
		assertEquals(accepted.thrustNewtons(), aggregate.runtimeForceReplacementThrustNewtons(), 1.0e-12);
		assertEquals(accepted.shaftPowerWatts(), aggregate.runtimeForceReplacementShaftPowerWatts(), 1.0e-12);
		assertEquals(accepted.shaftTorqueNewtonMeters(),
				aggregate.runtimeForceReplacementShaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(accepted.dimensionalSample().diskMassFlowKilogramsPerSecond(),
				aggregate.runtimeForceReplacementDiskMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().usefulAxialThrustPowerWatts(),
				aggregate.runtimeForceReplacementUsefulAxialThrustPowerWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().idealInducedPowerWatts(),
				aggregate.runtimeForceReplacementIdealInducedPowerWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().idealMomentumPowerWatts(),
				aggregate.runtimeForceReplacementIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().wakeSwirlKineticPowerWatts(),
				aggregate.runtimeForceReplacementWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().totalWakeKineticPowerWatts(),
				aggregate.runtimeForceReplacementWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(accepted.shaftPowerWatts() - accepted.dimensionalSample().totalWakeKineticPowerWatts(),
				aggregate.runtimeForceReplacementWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(accepted.dimensionalSample().totalWakeKineticPowerOverShaftPower(),
				aggregate.runtimeForceReplacementWakeKineticPowerOverShaftPower(), 1.0e-12);
		assertVectorEquals(accepted.thrustForceBodyNewtons(),
				aggregate.runtimeForceReplacementThrustForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(accepted.reactionTorqueBodyNewtonMeters(),
				aggregate.runtimeForceReplacementReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(accepted.thrustMomentBodyNewtonMeters(),
				aggregate.runtimeForceReplacementThrustMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(accepted.totalTorqueBodyNewtonMeters(),
				aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters(), 1.0e-15);
		assertTrue(aggregate.totalThrustNewtons() > aggregate.runtimeForceReplacementThrustNewtons());
		assertTrue(aggregate.totalShaftPowerWatts() > aggregate.runtimeForceReplacementShaftPowerWatts());
	}

	@Test
	void highAdvanceSampleKeepsCtCpTrendAndFiniteTorque() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample mid = sampleReferenceCase(rotor, "mid_domain_mid_rpm");
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample high = sampleReferenceCase(rotor, "high_domain_max_rpm");

		assertFalse(high.blocked());
		assertFalse(high.momentumPowerClosureSatisfied());
		assertFalse(high.wakePowerClosureSatisfied());
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
		assertTrue(high.dimensionalSample().shaftPowerResidualWatts() < 0.0);
		assertTrue(mid.dimensionalSample().shaftPowerResidualWatts() > 0.0);
		assertEquals(high.shaftPowerWatts() / high.dimensionalSample().angularVelocityRadiansPerSecond(),
				high.shaftTorqueNewtonMeters(), 1.0e-17);
		assertEquals(high.lookup().powerCoefficientCp() / (2.0 * Math.PI),
				high.dimensionalSample().torqueCoefficientCq(), 1.0e-18);
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
		assertFalse(blocked.wakePowerClosureSatisfied());
		assertFalse(blocked.runtimeForceReplacementAccepted());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", blocked.lookup().status());
		assertEquals(0.0, blocked.thrustNewtons(), 1.0e-15);
		assertEquals(0.0, blocked.shaftPowerWatts(), 1.0e-15);
		assertEquals(0.0, blocked.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.0, blocked.thrustForceBodyNewtons().length(), 1.0e-15);
		assertEquals(0.0, blocked.reactionTorqueBodyNewtonMeters().length(), 1.0e-15);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint operatingPoint =
				blocked.standardOperatingPoint();
		assertTrue(operatingPoint.rotationalTipSpeedMetersPerSecond() > 0.0);
		assertTrue(operatingPoint.helicalTipSpeedMetersPerSecond()
				> operatingPoint.rotationalTipSpeedMetersPerSecond());
		assertTrue(operatingPoint.tipMach() > 0.0);
		assertTrue(operatingPoint.reynoldsNumber() > 0.0);
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
		assertFalse(sample.wakePowerClosureSatisfied());
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
	void staticAnchoredTargetThrustSolverRecoversHoverRpmAndMomentumTerms() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		double expectedOmega = Math.sqrt(targetThrust / rotor.thrustCoefficient());

		PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredRpmForTargetThrust(
						"apDrone",
						"target_hover_solve",
						rotor,
						0.0,
						targetThrust,
						expectedOmega * 0.55,
						expectedOmega * 1.45,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample = solution.solutionSample();

		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.SOLVED,
				solution.status());
		assertTrue(solution.solved());
		assertFalse(solution.blocked());
		assertFalse(solution.clamped());
		assertTrue(solution.iterations() > 0);
		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals(targetThrust, sample.thrustNewtons(), targetThrust * 1.0e-6);
		assertEquals(expectedOmega, solution.solutionOmegaRadiansPerSecond(), expectedOmega * 1.0e-5);
		assertEquals(sample.query().rpm(), solution.solutionRpm(), 1.0e-9);
		assertEquals(0.0, sample.query().advanceRatioJ(), 1.0e-12);
		assertEquals(sample.dimensionalSample().usefulAxialThrustPowerWatts()
						+ sample.dimensionalSample().idealInducedPowerWatts(),
				sample.dimensionalSample().idealMomentumPowerWatts(),
				1.0e-12);
		assertEquals(sample.thrustNewtons() - targetThrust, solution.thrustResidualNewtons(), 1.0e-12);
		assertTrue(solution.absoluteThrustResidualNewtons() <= targetThrust * 1.0e-6);
	}

	@Test
	void staticAnchoredTargetThrustSolverRecoversForwardFlowRpm() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double referenceRpm = 6_000.0;
		double referenceOmega = referenceRpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 2.25;
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample reference =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromAxialAdvanceSpeed(
						"apDrone",
						"target_forward_reference",
						rotor,
						axialSpeed,
						referenceOmega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredRpmForTargetThrust(
						"apDrone",
						"target_forward_solve",
						rotor,
						axialSpeed,
						reference.thrustNewtons(),
						4_000.0 * 2.0 * Math.PI / 60.0,
						9_000.0 * 2.0 * Math.PI / 60.0,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample = solution.solutionSample();

		assertFalse(reference.blocked());
		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.SOLVED,
				solution.status());
		assertTrue(solution.solved());
		assertFalse(solution.clamped());
		assertFalse(solution.lowerBoundSample().blocked());
		assertFalse(solution.upperBoundSample().blocked());
		assertEquals(reference.thrustNewtons(), sample.thrustNewtons(), reference.thrustNewtons() * 1.0e-6);
		assertEquals(referenceOmega, solution.solutionOmegaRadiansPerSecond(), referenceOmega * 1.0e-5);
		assertEquals(reference.query().advanceRatioJ(), sample.query().advanceRatioJ(), 1.0e-5);
		assertEquals(axialSpeed, sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-9);
		assertTrue(sample.dimensionalSample().usefulAxialThrustPowerWatts() > 0.0);
		assertTrue(sample.dimensionalSample().idealInducedPowerWatts() > 0.0);
		assertTrue(sample.shaftPowerWatts() > sample.dimensionalSample().idealMomentumPowerWatts());
	}

	@Test
	void staticAnchoredTargetThrustSolverBlocksWhenUpperRpmCannotReachTarget() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double upperOmega = 5_000.0 * 2.0 * Math.PI / 60.0;
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample upper =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromAxialAdvanceSpeed(
						"apDrone",
						"target_upper_reference",
						rotor,
						0.0,
						upperOmega,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredRpmForTargetThrust(
						"apDrone",
						"target_above_upper",
						rotor,
						0.0,
						upper.thrustNewtons() * 1.20,
						2_500.0 * 2.0 * Math.PI / 60.0,
						upperOmega,
						RHO
				);

		assertFalse(upper.blocked());
		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.UPPER_BOUND_BELOW_TARGET,
				solution.status());
		assertFalse(solution.solved());
		assertTrue(solution.blocked());
		assertFalse(solution.clamped());
		assertEquals(upper.thrustNewtons(), solution.upperBoundSample().thrustNewtons(), 1.0e-12);
		assertTrue(solution.thrustResidualNewtons() < 0.0);
		assertEquals(solution.upperBoundSample(), solution.solutionSample());
	}

	@Test
	void staticAnchoredTargetThrustSolverBlocksWhenUpperRpmIsOutsideAdvanceEnvelope() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double axialSpeed = 22.0;

		PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredRpmForTargetThrust(
						"apDrone",
						"target_upper_high_j_block",
						rotor,
						axialSpeed,
						1.0,
						3_000.0 * 2.0 * Math.PI / 60.0,
						6_000.0 * 2.0 * Math.PI / 60.0,
						RHO
				);

		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.UPPER_BOUND_BLOCKED,
				solution.status());
		assertFalse(solution.solved());
		assertTrue(solution.blocked());
		assertFalse(solution.clamped());
		assertTrue(solution.upperBoundSample().blocked());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", solution.upperBoundSample().lookup().status());
		assertEquals(0.0, solution.solutionSample().thrustNewtons(), 1.0e-15);
		assertEquals(axialSpeed, solution.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
	}

	@Test
	void staticAnchoredConfigurationTargetThrustSolverRecoversHoverAggregateRpm() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double expectedOmega = Math.sqrt(
				targetThrust / config.rotors().size() / rotor.thrustCoefficient());

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						"apDrone",
						"configuration_target_hover",
						config,
						0.0,
						targetThrust,
						expectedOmega * 0.55,
						expectedOmega * 1.45,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				solution.solutionSample();

		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.SOLVED,
				solution.status());
		assertTrue(solution.solved());
		assertFalse(solution.blocked());
		assertFalse(solution.clamped());
		assertEquals(config.rotors().size(), aggregate.rotorSamples().size());
		assertEquals(config.rotors().size(), aggregate.acceptedRotorCount());
		assertEquals(config.rotors().size(), aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(0, aggregate.blockedRotorCount());
		assertEquals(0, aggregate.clampedRotorCount());
		assertEquals(targetThrust, aggregate.totalThrustNewtons(), targetThrust * 1.0e-6);
		assertEquals(targetThrust, aggregate.totalThrustForceBodyNewtons().y(), targetThrust * 1.0e-6);
		assertVectorEquals(Vec3.ZERO, aggregate.totalReactionTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.totalThrustMomentBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, aggregate.totalBodyTorqueNewtonMeters(), 1.0e-12);
		assertEquals(expectedOmega, solution.solutionOmegaRadiansPerSecond(), expectedOmega * 1.0e-5);
		assertEquals(aggregate.rotorSamples().get(0).query().rpm(), solution.solutionRpm(), 1.0e-9);
		assertEquals(0.0, aggregate.rotorSamples().get(0).query().advanceRatioJ(), 1.0e-12);
		assertEquals(aggregate.totalUsefulAxialThrustPowerWatts() + aggregate.totalIdealInducedPowerWatts(),
				aggregate.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalThrustNewtons() - targetThrust, solution.thrustResidualNewtons(), 1.0e-12);
		assertTrue(solution.absoluteThrustResidualNewtons() <= targetThrust * 1.0e-6);
	}

	@Test
	void staticAnchoredConfigurationTargetThrustSolverRecoversForwardFlowAggregateRpm() {
		DroneConfig config = DroneConfig.apDrone();
		double referenceRpm = 6_000.0;
		double referenceOmega = referenceRpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 2.25;
		double[] axialSpeeds = new double[config.rotors().size()];
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			axialSpeeds[i] = axialSpeed;
			omegas[i] = referenceOmega;
		}
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample reference =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						"apDrone",
						"configuration_target_forward_reference",
						config,
						axialSpeeds,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						"apDrone",
						"configuration_target_forward",
						config,
						axialSpeed,
						reference.totalThrustNewtons(),
						4_000.0 * 2.0 * Math.PI / 60.0,
						9_000.0 * 2.0 * Math.PI / 60.0,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				solution.solutionSample();

		assertEquals(config.rotors().size(), reference.acceptedRotorCount());
		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.SOLVED,
				solution.status());
		assertTrue(solution.solved());
		assertFalse(solution.clamped());
		assertEquals(0, solution.lowerBoundSample().blockedRotorCount());
		assertEquals(0, solution.upperBoundSample().blockedRotorCount());
		assertEquals(reference.totalThrustNewtons(), aggregate.totalThrustNewtons(),
				reference.totalThrustNewtons() * 1.0e-6);
		assertEquals(referenceOmega, solution.solutionOmegaRadiansPerSecond(), referenceOmega * 1.0e-5);
		assertEquals(reference.rotorSamples().get(0).query().advanceRatioJ(),
				aggregate.rotorSamples().get(0).query().advanceRatioJ(), 1.0e-5);
		assertEquals(axialSpeed, aggregate.rotorSamples().get(0).axialAdvanceSpeedMetersPerSecond(), 1.0e-9);
		assertTrue(aggregate.totalUsefulAxialThrustPowerWatts() > 0.0);
		assertTrue(aggregate.totalIdealInducedPowerWatts() > 0.0);
		assertTrue(aggregate.totalShaftPowerWatts() > aggregate.totalIdealMomentumPowerWatts());
		assertVectorEquals(reference.totalThrustForceBodyNewtons(),
				aggregate.totalThrustForceBodyNewtons(), reference.totalThrustNewtons() * 1.0e-6);
	}

	@Test
	void staticAnchoredConfigurationTargetThrustFromBodyKinematicsSolvesCommonRpmWithLocalInflow() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, 3.0, 0.0);
		Vec3 angularVelocityBody = new Vec3(4.0, 0.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel
						.solveStaticAnchoredConfigurationRpmForTargetThrustFromBodyKinematics(
								"apDrone",
								"configuration_target_body_kinematics",
								config,
								bodyRelativeAirVelocity,
								angularVelocityBody,
								targetThrust,
								hoverOmega * 0.55,
								hoverOmega * 1.80,
								RHO
						);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				solution.solutionSample();

		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.SOLVED,
				solution.status());
		assertTrue(solution.solved());
		assertFalse(solution.clamped());
		assertEquals(config.rotors().size(), aggregate.acceptedRotorCount());
		assertEquals(config.rotors().size(), aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertEquals(targetThrust, aggregate.totalThrustNewtons(), targetThrust * 1.0e-6);
		assertEquals(bodyRelativeAirVelocity.y(), solution.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		double commonRpm = aggregate.rotorSamples().get(0).query().rpm();
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec localRotor = config.rotors().get(i);
			Vec3 arm = localRotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
			Vec3 expectedRelativeAirVelocity = bodyRelativeAirVelocity.add(angularVelocityBody.cross(arm));
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample = aggregate.rotorSamples().get(i);
			assertEquals(commonRpm, sample.query().rpm(), 1.0e-9);
			assertVectorEquals(expectedRelativeAirVelocity,
					sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-12);
			assertEquals(expectedRelativeAirVelocity.dot(localRotor.thrustAxisBody()),
					sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		}
		assertTrue(aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveAdvanceRatioJ())
				.max()
				.orElse(0.0)
				> aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveAdvanceRatioJ())
				.min()
				.orElse(0.0));
		assertTrue(aggregate.totalUsefulAxialThrustPowerWatts() > 0.0);
		assertTrue(aggregate.totalShaftPowerWatts() > aggregate.totalIdealMomentumPowerWatts());
		assertTrue(Math.abs(aggregate.totalBodyTorqueNewtonMeters().x()) > 1.0e-4);
	}

	@Test
	void staticAnchoredConfigurationTargetThrustFromWorldKinematicsMatchesBodyKinematics() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, 3.0, 0.0);
		Vec3 angularVelocityBody = new Vec3(4.0, 0.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution body =
				PropellerArchiveCtCpJRotorForceModel
						.solveStaticAnchoredConfigurationRpmForTargetThrustFromBodyKinematics(
								"apDrone",
								"configuration_target_world_body_reference",
								config,
								bodyRelativeAirVelocity,
								angularVelocityBody,
								targetThrust,
								hoverOmega * 0.55,
								hoverOmega * 1.80,
								RHO
						);
		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution world =
				PropellerArchiveCtCpJRotorForceModel
						.solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
								"apDrone",
								"configuration_target_world_identity",
								config,
								Quaternion.IDENTITY,
								bodyRelativeAirVelocity,
								angularVelocityBody,
								Vec3.ZERO,
								null,
								targetThrust,
								hoverOmega * 0.55,
								hoverOmega * 1.80,
								RHO
						);

		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.SOLVED,
				world.status());
		assertTrue(world.solved());
		assertEquals(body.solutionOmegaRadiansPerSecond(), world.solutionOmegaRadiansPerSecond(), 1.0e-12);
		assertEquals(body.solutionSample().totalThrustNewtons(),
				world.solutionSample().totalThrustNewtons(), 1.0e-12);
		assertVectorEquals(body.solutionSample().totalBodyTorqueNewtonMeters(),
				world.solutionSample().totalBodyTorqueNewtonMeters(), 1.0e-12);
		for (int i = 0; i < config.rotors().size(); i++) {
			assertVectorEquals(body.solutionSample().rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(),
					world.solutionSample().rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
			assertEquals(body.solutionSample().rotorSamples().get(i).query().advanceRatioJ(),
					world.solutionSample().rotorSamples().get(i).query().advanceRatioJ(), 1.0e-12);
		}
	}

	@Test
	void staticAnchoredConfigurationTargetThrustFromWorldKinematicsUsesPerRotorWind() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 vehicleVelocityWorld = new Vec3(0.0, 3.0, 0.0);
		Vec3[] rotorWinds = new Vec3[config.rotors().size()];
		rotorWinds[0] = new Vec3(0.0, 1.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution baseline =
				PropellerArchiveCtCpJRotorForceModel
						.solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
								"apDrone",
								"configuration_target_world_wind_baseline",
								config,
								Quaternion.IDENTITY,
								vehicleVelocityWorld,
								Vec3.ZERO,
								Vec3.ZERO,
								null,
								targetThrust,
								hoverOmega * 0.55,
								hoverOmega * 1.80,
								RHO
						);
		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution localWind =
				PropellerArchiveCtCpJRotorForceModel
						.solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
								"apDrone",
								"configuration_target_world_per_rotor_wind",
								config,
								Quaternion.IDENTITY,
								vehicleVelocityWorld,
								Vec3.ZERO,
								Vec3.ZERO,
								rotorWinds,
								targetThrust,
								hoverOmega * 0.55,
								hoverOmega * 1.80,
								RHO
						);

		assertTrue(baseline.solved());
		assertTrue(localWind.solved());
		assertEquals(targetThrust, localWind.solutionSample().totalThrustNewtons(), targetThrust * 1.0e-6);
		assertTrue(localWind.solutionOmegaRadiansPerSecond() < baseline.solutionOmegaRadiansPerSecond());
		assertEquals(vehicleVelocityWorld.y(), baseline.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(vehicleVelocityWorld.y() - 0.25,
				localWind.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertVectorEquals(new Vec3(0.0, 2.0, 0.0),
				localWind.solutionSample().rotorSamples().get(0).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
		for (int i = 1; i < config.rotors().size(); i++) {
			assertVectorEquals(vehicleVelocityWorld,
					localWind.solutionSample().rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(),
					1.0e-15);
		}
	}

	@Test
	void staticAnchoredConfigurationTargetThrustFromEnvironmentKinematicsMatchesCalmWorldKinematics() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 vehicleVelocityWorld = new Vec3(0.0, 3.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution world =
				PropellerArchiveCtCpJRotorForceModel
						.solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
								"apDrone",
								"configuration_target_environment_world_reference",
								config,
								Quaternion.IDENTITY,
								vehicleVelocityWorld,
								Vec3.ZERO,
								Vec3.ZERO,
								null,
								targetThrust,
								hoverOmega * 0.55,
								hoverOmega * 1.80,
								RHO,
								25.0,
								0.0
						);
		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution environment =
				PropellerArchiveCtCpJRotorForceModel
						.solveStaticAnchoredConfigurationRpmForTargetThrustFromEnvironmentKinematics(
								"apDrone",
								"configuration_target_environment_calm",
								config,
								Quaternion.IDENTITY,
								vehicleVelocityWorld,
								Vec3.ZERO,
								targetThrust,
								hoverOmega * 0.55,
								hoverOmega * 1.80,
								DroneEnvironment.calm()
						);

		assertTrue(environment.solved());
		assertEquals(world.solutionOmegaRadiansPerSecond(),
				environment.solutionOmegaRadiansPerSecond(), 1.0e-12);
		assertEquals(world.solutionSample().totalShaftPowerWatts(),
				environment.solutionSample().totalShaftPowerWatts(), 1.0e-12);
		assertEquals(RHO,
				environment.solutionSample().rotorSamples().get(0).query().airDensityKgPerCubicMeter(), 1.0e-15);
		assertEquals(25.0,
				environment.solutionSample().rotorSamples().get(0).query().ambientTemperatureCelsius(), 1.0e-15);
	}

	@Test
	void staticAnchoredConfigurationTargetThrustSolverBlocksWhenUpperRpmCannotReachTarget() {
		DroneConfig config = DroneConfig.apDrone();
		double upperOmega = 5_000.0 * 2.0 * Math.PI / 60.0;
		double[] axialSpeeds = new double[config.rotors().size()];
		double[] upperOmegas = new double[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			upperOmegas[i] = upperOmega;
		}
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample upper =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						"apDrone",
						"configuration_target_upper_reference",
						config,
						axialSpeeds,
						upperOmegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						"apDrone",
						"configuration_target_above_upper",
						config,
						0.0,
						upper.totalThrustNewtons() * 1.20,
						2_500.0 * 2.0 * Math.PI / 60.0,
						upperOmega,
						RHO
				);

		assertEquals(config.rotors().size(), upper.acceptedRotorCount());
		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.UPPER_BOUND_BELOW_TARGET,
				solution.status());
		assertFalse(solution.solved());
		assertTrue(solution.blocked());
		assertFalse(solution.clamped());
		assertEquals(upper.totalThrustNewtons(), solution.upperBoundSample().totalThrustNewtons(), 1.0e-12);
		assertTrue(solution.thrustResidualNewtons() < 0.0);
		assertEquals(solution.upperBoundSample(), solution.solutionSample());
	}

	@Test
	void staticAnchoredConfigurationTargetThrustSolverBlocksWhenUpperRpmIsOutsideAdvanceEnvelope() {
		DroneConfig config = DroneConfig.apDrone();
		double axialSpeed = 22.0;

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution solution =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						"apDrone",
						"configuration_target_upper_high_j_block",
						config,
						axialSpeed,
						config.massKg() * config.gravityMetersPerSecondSquared(),
						3_000.0 * 2.0 * Math.PI / 60.0,
						6_000.0 * 2.0 * Math.PI / 60.0,
						RHO
				);

		assertEquals(PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.UPPER_BOUND_BLOCKED,
				solution.status());
		assertFalse(solution.solved());
		assertTrue(solution.blocked());
		assertFalse(solution.clamped());
		assertEquals(config.rotors().size(), solution.upperBoundSample().blockedRotorCount());
		assertEquals(0, solution.upperBoundSample().acceptedRotorCount());
		assertEquals(0.0, solution.solutionSample().totalThrustNewtons(), 1.0e-15);
		assertEquals(axialSpeed, solution.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
	}

	@Test
	void staticAnchoredConfigurationTrimSolvesHoverAndZeroBodyTorque() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double expectedOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution trim =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrim(
						"apDrone",
						"configuration_trim_hover",
						config,
						0.0,
						targetThrust,
						Vec3.ZERO,
						expectedOmega * 0.55,
						expectedOmega * 1.45,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate = trim.solutionSample();
		double[] allocated = trim.allocatedRotorThrustsNewtons();

		assertEquals(PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolveStatus.SOLVED,
				trim.status());
		assertTrue(trim.solved());
		assertFalse(trim.blocked());
		assertEquals(config.rotors().size(), trim.rotorSolutions().size());
		assertEquals(config.rotors().size(), allocated.length);
		for (double thrust : allocated) {
			assertEquals(targetThrust / config.rotors().size(), thrust, 1.0e-8);
		}
		assertEquals(targetThrust, aggregate.totalThrustNewtons(), targetThrust * 1.0e-6);
		assertVectorEquals(Vec3.ZERO, aggregate.totalBodyTorqueNewtonMeters(), 1.0e-8);
		assertEquals(0.0, trim.thrustResidualNewtons(), targetThrust * 1.0e-6);
		assertVectorEquals(Vec3.ZERO, trim.bodyTorqueResidualNewtonMeters(), 1.0e-8);
		assertEquals(config.rotors().size(), aggregate.runtimeForceReplacementAcceptedRotorCount());
		assertTrue(aggregate.totalShaftPowerWatts() > 0.0);
		assertTrue(aggregate.totalIdealMomentumPowerWatts() > 0.0);
	}

	@Test
	void staticAnchoredConfigurationTrimSolvesBodyTorqueTargetsWithPerRotorRpmSpread() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 targetTorque = new Vec3(0.045, 0.012, -0.035);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution trim =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrim(
						"apDrone",
						"configuration_trim_body_torque",
						config,
						0.0,
						targetThrust,
						targetTorque,
						hoverOmega * 0.50,
						hoverOmega * 1.60,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate = trim.solutionSample();
		double[] allocated = trim.allocatedRotorThrustsNewtons();

		assertEquals(PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolveStatus.SOLVED,
				trim.status());
		assertTrue(trim.solved());
		assertEquals(config.rotors().size(), aggregate.acceptedRotorCount());
		assertTrue(trim.maxAllocatedRotorThrustNewtons() > trim.minAllocatedRotorThrustNewtons());
		assertTrue(allocated[0] != allocated[1] || allocated[1] != allocated[2]);
		assertEquals(targetThrust, aggregate.totalThrustNewtons(), targetThrust * 1.0e-6);
		assertEquals(targetTorque.x(), aggregate.totalBodyTorqueNewtonMeters().x(), 3.0e-5);
		assertEquals(targetTorque.y(), aggregate.totalBodyTorqueNewtonMeters().y(), 3.0e-5);
		assertEquals(targetTorque.z(), aggregate.totalBodyTorqueNewtonMeters().z(), 3.0e-5);
		assertEquals(0.0, trim.thrustResidualNewtons(), targetThrust * 1.0e-6);
		assertVectorEquals(Vec3.ZERO, trim.bodyTorqueResidualNewtonMeters(), 3.0e-5);
		assertTrue(aggregate.totalShaftPowerWatts() > 0.0);
		assertTrue(aggregate.totalShaftTorqueNewtonMeters() > 0.0);
	}

	@Test
	void staticAnchoredConfigurationTrimFromBodyKinematicsUsesRotorLocalInflow() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, 3.0, 0.0);
		Vec3 angularVelocityBody = new Vec3(4.0, 0.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution trim =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrimFromBodyKinematics(
						"apDrone",
						"configuration_trim_body_kinematics",
						config,
						bodyRelativeAirVelocity,
						angularVelocityBody,
						targetThrust,
						Vec3.ZERO,
						hoverOmega * 0.55,
						hoverOmega * 1.80,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate = trim.solutionSample();

		assertEquals(PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolveStatus.SOLVED,
				trim.status());
		assertTrue(trim.solved());
		assertEquals(config.rotors().size(), trim.rotorSolutions().size());
		assertEquals(config.rotors().size(), aggregate.acceptedRotorCount());
		assertEquals(targetThrust, aggregate.totalThrustNewtons(), targetThrust * 1.0e-6);
		assertEquals(0.0, trim.thrustResidualNewtons(), targetThrust * 1.0e-6);
		assertVectorEquals(Vec3.ZERO, aggregate.totalThrustMomentBodyNewtonMeters(), 1.0e-6);
		assertVectorEquals(Vec3.ZERO, trim.bodyTorqueResidualNewtonMeters(), 2.0e-5);
		assertEquals(bodyRelativeAirVelocity.y(), trim.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec localRotor = config.rotors().get(i);
			Vec3 arm = localRotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
			Vec3 expectedRelativeAirVelocity = bodyRelativeAirVelocity.add(angularVelocityBody.cross(arm));
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample = aggregate.rotorSamples().get(i);
			assertVectorEquals(expectedRelativeAirVelocity,
					sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-12);
			assertEquals(expectedRelativeAirVelocity.dot(localRotor.thrustAxisBody()),
					sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
			assertEquals(targetThrust / config.rotors().size(), sample.thrustNewtons(),
					targetThrust * 1.0e-6);
		}
		assertTrue(aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.query().rpm())
				.max()
				.orElse(0.0)
				> aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.query().rpm())
				.min()
				.orElse(0.0));
		assertTrue(aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveAdvanceRatioJ())
				.max()
				.orElse(0.0)
				> aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveAdvanceRatioJ())
				.min()
				.orElse(0.0));
		assertTrue(aggregate.totalShaftPowerWatts() > aggregate.totalIdealMomentumPowerWatts());
	}

	@Test
	void staticAnchoredConfigurationTrimFromWorldKinematicsMatchesBodyKinematics() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 bodyRelativeAirVelocity = new Vec3(0.0, 3.0, 0.0);
		Vec3 angularVelocityBody = new Vec3(4.0, 0.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution body =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrimFromBodyKinematics(
						"apDrone",
						"configuration_trim_world_body_reference",
						config,
						bodyRelativeAirVelocity,
						angularVelocityBody,
						targetThrust,
						Vec3.ZERO,
						hoverOmega * 0.55,
						hoverOmega * 1.80,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution world =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrimFromWorldKinematics(
						"apDrone",
						"configuration_trim_world_identity",
						config,
						Quaternion.IDENTITY,
						bodyRelativeAirVelocity,
						angularVelocityBody,
						Vec3.ZERO,
						null,
						targetThrust,
						Vec3.ZERO,
						hoverOmega * 0.55,
						hoverOmega * 1.80,
						RHO
				);

		assertEquals(PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolveStatus.SOLVED,
				world.status());
		assertTrue(world.solved());
		assertEquals(body.signedAxialAdvanceSpeedMetersPerSecond(),
				world.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(body.solutionSample().totalThrustNewtons(),
				world.solutionSample().totalThrustNewtons(), 1.0e-12);
		assertVectorEquals(body.solutionSample().totalBodyTorqueNewtonMeters(),
				world.solutionSample().totalBodyTorqueNewtonMeters(), 1.0e-12);
		for (int i = 0; i < config.rotors().size(); i++) {
			assertEquals(body.rotorSolutions().get(i).solutionOmegaRadiansPerSecond(),
					world.rotorSolutions().get(i).solutionOmegaRadiansPerSecond(), 1.0e-12);
			assertVectorEquals(body.solutionSample().rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(),
					world.solutionSample().rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
		}
	}

	@Test
	void staticAnchoredConfigurationTrimFromWorldKinematicsUsesPerRotorWind() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 vehicleVelocityWorld = new Vec3(0.0, 3.0, 0.0);
		Vec3[] rotorWinds = new Vec3[config.rotors().size()];
		rotorWinds[0] = new Vec3(0.0, 1.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution baseline =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrimFromWorldKinematics(
						"apDrone",
						"configuration_trim_world_wind_baseline",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						Vec3.ZERO,
						null,
						targetThrust,
						Vec3.ZERO,
						hoverOmega * 0.55,
						hoverOmega * 1.80,
						RHO
				);
		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution localWind =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrimFromWorldKinematics(
						"apDrone",
						"configuration_trim_world_per_rotor_wind",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						Vec3.ZERO,
						rotorWinds,
						targetThrust,
						Vec3.ZERO,
						hoverOmega * 0.55,
						hoverOmega * 1.80,
						RHO
				);

		assertTrue(baseline.solved());
		assertTrue(localWind.solved());
		assertEquals(targetThrust, localWind.solutionSample().totalThrustNewtons(), targetThrust * 1.0e-6);
		assertVectorEquals(Vec3.ZERO,
				localWind.solutionSample().totalThrustMomentBodyNewtonMeters(), 2.0e-5);
		assertEquals(localWind.solutionSample().totalReactionTorqueBodyNewtonMeters().y(),
				localWind.bodyTorqueResidualNewtonMeters().y(), 1.0e-12);
		assertTrue(Math.abs(localWind.bodyTorqueResidualNewtonMeters().y()) > 1.0e-4);
		assertEquals(vehicleVelocityWorld.y(), baseline.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(vehicleVelocityWorld.y() - 0.25,
				localWind.signedAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertVectorEquals(new Vec3(0.0, 2.0, 0.0),
				localWind.solutionSample().rotorSamples().get(0).relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertTrue(localWind.rotorSolutions().get(0).solutionOmegaRadiansPerSecond()
				< baseline.rotorSolutions().get(0).solutionOmegaRadiansPerSecond());
		for (int i = 1; i < config.rotors().size(); i++) {
			assertVectorEquals(vehicleVelocityWorld,
					localWind.solutionSample().rotorSamples().get(i).relativeAirVelocityBodyMetersPerSecond(),
					1.0e-15);
		}
	}

	@Test
	void staticAnchoredConfigurationTrimFromEnvironmentKinematicsMatchesCalmWorldKinematics() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());
		Vec3 vehicleVelocityWorld = new Vec3(0.0, 3.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution world =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrimFromWorldKinematics(
						"apDrone",
						"configuration_trim_environment_world_reference",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						Vec3.ZERO,
						null,
						targetThrust,
						Vec3.ZERO,
						hoverOmega * 0.55,
						hoverOmega * 1.80,
						RHO,
						25.0,
						0.0
				);
		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution environment =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrimFromEnvironmentKinematics(
						"apDrone",
						"configuration_trim_environment_calm",
						config,
						Quaternion.IDENTITY,
						vehicleVelocityWorld,
						Vec3.ZERO,
						targetThrust,
						Vec3.ZERO,
						hoverOmega * 0.55,
						hoverOmega * 1.80,
						DroneEnvironment.calm()
				);

		assertTrue(environment.solved());
		assertEquals(world.solutionSample().totalThrustNewtons(),
				environment.solutionSample().totalThrustNewtons(), 1.0e-12);
		assertVectorEquals(world.solutionSample().totalBodyTorqueNewtonMeters(),
				environment.solutionSample().totalBodyTorqueNewtonMeters(), 1.0e-12);
		assertEquals(RHO,
				environment.solutionSample().rotorSamples().get(0).query().airDensityKgPerCubicMeter(), 1.0e-15);
		assertEquals(25.0,
				environment.solutionSample().rotorSamples().get(0).query().ambientTemperatureCelsius(), 1.0e-15);
	}

	@Test
	void staticAnchoredConfigurationTrimBlocksWhenTorqueAllocationRequiresNegativeThrust() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double hoverOmega = Math.sqrt(targetThrust / config.rotors().size() / rotor.thrustCoefficient());

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution trim =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrim(
						"apDrone",
						"configuration_trim_negative_allocation",
						config,
						0.0,
						targetThrust,
						new Vec3(3.0, 0.0, 0.0),
						hoverOmega * 0.50,
						hoverOmega * 1.60,
						RHO
				);

		assertEquals(PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolveStatus.NEGATIVE_ALLOCATED_THRUST,
				trim.status());
		assertFalse(trim.solved());
		assertTrue(trim.blocked());
		assertTrue(trim.minAllocatedRotorThrustNewtons() < 0.0);
		assertTrue(trim.solutionSample() == null);
		assertTrue(trim.rotorSolutions().isEmpty());
	}

	@Test
	void staticAnchoredConfigurationTrimBlocksWhenAllocatedRotorThrustExceedsRpmBracket() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double targetThrust = config.totalMaxThrustNewtons() * 1.20;
		double lowOmega = 2_500.0 * 2.0 * Math.PI / 60.0;
		double highOmega = rotor.maxOmegaRadiansPerSecond() * 0.55;

		PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution trim =
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrim(
						"apDrone",
						"configuration_trim_rotor_solve_blocked",
						config,
						0.0,
						targetThrust,
						Vec3.ZERO,
						lowOmega,
						highOmega,
						RHO
				);

		assertEquals(PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolveStatus.ROTOR_SOLVE_BLOCKED,
				trim.status());
		assertFalse(trim.solved());
		assertTrue(trim.blocked());
		assertEquals(config.rotors().size(), trim.rotorSolutions().size());
		assertTrue(trim.rotorSolutions().stream()
				.allMatch(solution -> solution.status()
						== PropellerArchiveCtCpJRotorForceModel.RotorTargetThrustSolveStatus.UPPER_BOUND_BELOW_TARGET));
		assertTrue(trim.solutionSample() != null);
		assertTrue(trim.solutionSample().totalThrustNewtons() < targetThrust);
		assertTrue(trim.thrustResidualNewtons() < 0.0);
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
		assertVectorEquals(rotor.thrustAxisBody().multiply(-4.5),
				sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-12);
		assertEquals(Math.PI, sample.inflowAngleRadians(), 1.0e-12);
		assertFalse(sample.runtimeInflowEnvelopeSatisfied());
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
		Vec3 momentReferenceWorld = new Vec3(1.0, 2.0, 3.0);
		PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample runtimeApplication =
				sample.runtimeForceReplacementWorldForceApplication(
						0,
						momentReferenceWorld,
						Quaternion.IDENTITY
				);
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm =
				sample.actuatorDiskSourceTerm(0, momentReferenceWorld, Quaternion.IDENTITY);
		PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample runtimeSourceTerm =
				sample.runtimeForceReplacementActuatorDiskSourceTerm(0, momentReferenceWorld, Quaternion.IDENTITY);

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
		assertVectorEquals(rotor.thrustAxisBody().multiply(-4.5),
				sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-12);
		assertEquals(Math.PI, sample.inflowAngleRadians(), 1.0e-12);
		assertFalse(sample.runtimeInflowEnvelopeSatisfied());
		assertFalse(runtimeApplication.applied());
		assertFalse(runtimeApplication.runtimeForceReplacementAccepted());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", runtimeApplication.lookupStatus());
		assertVectorEquals(momentReferenceWorld.add(sample.momentArmWorldMeters(Quaternion.IDENTITY)),
				runtimeApplication.forceApplicationPointWorldMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeApplication.thrustForceWorldNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeApplication.reactionTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeApplication.thrustMomentWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeApplication.totalTorqueWorldNewtonMeters(), 1.0e-15);
		assertFalse(sourceTerm.applied());
		assertFalse(runtimeSourceTerm.applied());
		assertFalse(sourceTerm.runtimeForceReplacementAccepted());
		assertFalse(runtimeSourceTerm.runtimeForceReplacementAccepted());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", sourceTerm.lookupStatus());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", runtimeSourceTerm.lookupStatus());
		assertVectorEquals(runtimeApplication.forceApplicationPointWorldMeters(),
				sourceTerm.diskCenterWorldMeters(), 1.0e-15);
		assertVectorEquals(runtimeApplication.forceApplicationPointWorldMeters(),
				runtimeSourceTerm.diskCenterWorldMeters(), 1.0e-15);
		assertVectorEquals(rotor.thrustAxisBody().normalized(), sourceTerm.diskNormalWorld(), 1.0e-15);
		assertEquals(sample.dimensionalSample().diskAreaSquareMeters(), sourceTerm.diskAreaSquareMeters(), 1.0e-15);
		assertEquals(0.0, sourceTerm.pressureJumpPascals(), 1.0e-15);
		assertEquals(0.0, sourceTerm.massFluxKilogramsPerSecondSquareMeter(), 1.0e-15);
		assertEquals(0.0, sourceTerm.idealMomentumPowerLoadingWattsPerSquareMeter(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sourceTerm.farWakeAxialVelocityWorldMetersPerSecond(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sourceTerm.reactionTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sourceTerm.wakeAngularMomentumTorqueWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, sourceTerm.wakeAngularMomentumTorqueResidualWorldNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				sourceTerm.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(0.05),
				1.0e-15);
		assertEquals(0.0, sourceTerm.angularMomentumSwirlRadiusMeters(), 1.0e-15);
		assertEquals(0.0, sourceTerm.wakeTangentialVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, sourceTerm.wakeSwirlKineticPowerWatts(), 1.0e-15);
		assertEquals(0.0, sourceTerm.totalWakeKineticPowerWatts(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(momentReferenceWorld.add(new Vec3(1.0, 0.0, 0.0))),
				1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeSourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, runtimeSourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(0.05), 1.0e-15);
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

	private static Vec3 sumWorldThrustForce(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.thrustForceWorldNewtons());
		}
		return sum;
	}

	private static Vec3 sumWorldReactionTorque(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.reactionTorqueWorldNewtonMeters());
		}
		return sum;
	}

	private static Vec3 sumWorldThrustMoment(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.thrustMomentWorldNewtonMeters());
		}
		return sum;
	}

	private static Vec3 sumWorldTotalTorque(
			List<PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample> applications
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorWorldForceApplicationSample application : applications) {
			sum = sum.add(application.totalTorqueWorldNewtonMeters());
		}
		return sum;
	}

	private static Vec3 sumWorldActuatorDiskSurfaceForce(
			List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms
	) {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			sum = sum.add(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter()
					.multiply(sourceTerm.diskAreaSquareMeters()));
		}
		return sum;
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}

	private static Vec3 perpendicularUnit(Vec3 axis) {
		Vec3 candidate = new Vec3(axis.y(), -axis.x(), 0.0);
		if (candidate.lengthSquared() <= 1.0e-12) {
			candidate = new Vec3(0.0, axis.z(), -axis.y());
		}
		return candidate.normalized();
	}
}
