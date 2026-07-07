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
		assertVectorEquals(Vec3.ZERO, sample.transverseAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, sample.transverseAirSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, sample.inflowAngleRadians(), 1.0e-15);
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
		double expectedIdealMomentumPower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().idealMomentumPowerWatts())
				.sum();
		double expectedWakeSwirlPower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().wakeSwirlKineticPowerWatts())
				.sum();
		double expectedWakePower = aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.dimensionalSample().totalWakeKineticPowerWatts())
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
		assertVectorEquals(Vec3.ZERO, aggregate.totalThrustMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalBodyTorqueNewtonMeters(), 1.0e-15);
		assertVectorEquals(aggregate.totalThrustForceBodyNewtons(),
				aggregate.runtimeForceReplacementThrustForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(aggregate.totalReactionTorqueBodyNewtonMeters(),
				aggregate.runtimeForceReplacementReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(aggregate.totalThrustMomentBodyNewtonMeters(),
				aggregate.runtimeForceReplacementThrustMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(aggregate.totalBodyTorqueNewtonMeters(),
				aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters(), 1.0e-15);
		assertEquals(aggregate.totalThrustNewtons(), aggregate.runtimeForceReplacementThrustNewtons(), 1.0e-12);
		assertEquals(aggregate.totalShaftPowerWatts(), aggregate.runtimeForceReplacementShaftPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalShaftTorqueNewtonMeters(),
				aggregate.runtimeForceReplacementShaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(expectedIdealMomentumPower, aggregate.totalIdealMomentumPowerWatts(), 1.0e-12);
		assertEquals(expectedWakeSwirlPower, aggregate.totalWakeSwirlKineticPowerWatts(), 1.0e-12);
		assertEquals(expectedWakePower, aggregate.totalWakeKineticPowerWatts(), 1.0e-12);
		assertEquals(aggregate.totalShaftPowerWatts() - aggregate.totalWakeKineticPowerWatts(),
				aggregate.totalWakeKineticPowerResidualWatts(), 1.0e-12);
		assertEquals(aggregate.totalWakeKineticPowerWatts() / aggregate.totalShaftPowerWatts(),
				aggregate.totalWakeKineticPowerOverShaftPower(), 1.0e-12);
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
		assertTrue(aggregate.totalShaftPowerWatts() > 0.0);
		assertTrue(aggregate.totalShaftTorqueNewtonMeters() > 0.0);
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
		assertEquals(accepted.dimensionalSample().idealMomentumPowerWatts()
						+ referenceOnly.dimensionalSample().idealMomentumPowerWatts(),
				aggregate.totalIdealMomentumPowerWatts(), 1.0e-12);
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
