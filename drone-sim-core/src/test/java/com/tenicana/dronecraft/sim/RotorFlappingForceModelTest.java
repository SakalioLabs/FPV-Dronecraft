package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorFlappingForceModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void axialFlowProducesNoTiltForceMomentOrReactionAxisCorrection() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		Vec3 arm = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
		Vec3 reactionTorque = rotor.thrustAxisBody().normalized().multiply(0.03 * rotor.spinDirection());
		RotorFlappingForceModel.RotorFlappingForceSample sample =
				RotorFlappingForceModel.sampleSteady(
						rotor,
						arm,
						new Vec3(0.0, 8.0, 0.0),
						Vec3.ZERO,
						900.0,
						2.5,
						reactionTorque
				);

		assertEquals(0.0, sample.advanceRatio(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.flappingTiltBodyRadians(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.flappingForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.forceMomentBodyNewtonMeters(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.reactionTorqueAxisCorrectionBodyNewtonMeters(), 0.0);
		assertVectorEquals(reactionTorque, sample.tiltedReactionTorqueBodyNewtonMeters(), 0.0);
		assertVectorEquals(sample.rotorAxisBody(), sample.effectiveDiskAxisBody(), 0.0);
		assertEquals(0.0, sample.translationalPowerWatts(), 0.0);
		assertEquals(0.0, sample.thrustMagnitudeResidualNewtons(), 0.0);
	}

	@Test
	void racingQuadLowSpeedTiltMatchesStarmacAnchorAndPreservesThrustMagnitude() {
		DroneConfig config = DroneConfig.racingQuad();
		RotorSpec rotor = config.rotors().get(0);
		double measuredWindSpeed = 3.4;
		double measuredAdvanceRatio = 0.03926110405546548;
		double measuredFlappingDegrees = 1.12;
		double omega = measuredWindSpeed / (measuredAdvanceRatio * rotor.radiusMeters());
		double thrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		Vec3 nominalReactionTorque = rotor.thrustAxisBody().normalized().multiply(
				0.02 * rotor.spinDirection()
		);
		RotorFlappingForceModel.RotorFlappingForceSample lowSpeed =
				RotorFlappingForceModel.sampleSteady(
						rotor,
						rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters()),
						new Vec3(measuredWindSpeed, 0.0, 0.0),
						Vec3.ZERO,
						omega,
						thrust,
						nominalReactionTorque
				);
		RotorFlappingForceModel.RotorFlappingForceSample highSpeed =
				RotorFlappingForceModel.sampleSteady(
						rotor,
						rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters()),
						new Vec3(16.0, 0.0, 0.0),
						Vec3.ZERO,
						omega,
						thrust,
						nominalReactionTorque
				);

		double lowSpeedTiltDegrees = Math.toDegrees(lowSpeed.effectiveDiskTiltAngleRadians());
		assertEquals(measuredAdvanceRatio, lowSpeed.advanceRatio(), 1.0e-15);
		assertEquals(measuredFlappingDegrees, lowSpeedTiltDegrees, 0.20);
		assertTrue(lowSpeed.flappingForceBodyNewtons().x() < 0.0);
		assertTrue(lowSpeed.axialForceCorrectionBodyNewtons().y() < 0.0);
		assertEquals(thrust, lowSpeed.thrustAxisForceBodyNewtons().length(), 1.0e-12);
		assertEquals(nominalReactionTorque.length(),
				lowSpeed.tiltedReactionTorqueBodyNewtonMeters().length(), 1.0e-15);
		assertTrue(lowSpeed.reactionTorqueAxisCorrectionBodyNewtonMeters().length() > 0.0);
		assertTrue(highSpeed.effectiveDiskTiltAngleRadians() > lowSpeed.effectiveDiskTiltAngleRadians());
		assertTrue(highSpeed.flappingForceBodyNewtons().x() < lowSpeed.flappingForceBodyNewtons().x());
		assertEquals(thrust, highSpeed.thrustAxisForceBodyNewtons().length(), 1.0e-12);
	}

	@Test
	void transientTiltLagsCrossflowReversalThenConvergesToOppositeDirection() {
		DroneConfig config = DroneConfig.racingQuad().withRotorFlappingCoefficient(0.16);
		RotorSpec rotor = config.rotors().get(0);
		Vec3 arm = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
		double thrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double omega = Math.sqrt(thrust / rotor.thrustCoefficient());
		RotorFlappingForceModel.RotorFlappingForceSample settledForward =
				RotorFlappingForceModel.sampleSteady(
						rotor,
						arm,
						new Vec3(16.0, 0.0, 0.0),
						Vec3.ZERO,
						omega,
						thrust
				);
		RotorFlappingForceModel.RotorFlappingForceSample transientSample =
				RotorFlappingForceModel.sampleTransient(
						rotor,
						arm,
						new Vec3(-16.0, 0.0, 0.0),
						Vec3.ZERO,
						omega,
						thrust,
						settledForward.flappingTiltBodyRadians(),
						0.005
				);

		assertTrue(settledForward.flappingForceBodyNewtons().x() < 0.0);
		assertTrue(transientSample.flappingForceBodyNewtons().x() < 0.0);
		assertTrue(transientSample.responseAlpha() > 0.0 && transientSample.responseAlpha() < 1.0);
		for (int i = 0; i < 80; i++) {
			transientSample = RotorFlappingForceModel.sampleTransient(
					rotor,
					arm,
					new Vec3(-16.0, 0.0, 0.0),
					Vec3.ZERO,
					omega,
					thrust,
					transientSample.flappingTiltBodyRadians(),
					0.005
			);
		}

		assertTrue(transientSample.flappingForceBodyNewtons().x() > 0.0);
		assertEquals(
				-settledForward.flappingTiltBodyRadians().x(),
				transientSample.flappingTiltBodyRadians().x(),
				2.0e-5
		);
	}

	@Test
	void ctCpJFlappingSamplesRespectRuntimeEnvelopeAndAggregateCorrections() {
		DroneConfig config = DroneConfig.apDrone();
		double[] omegas = hoverOmegas(config);
		Vec3 relativeAirVelocityBody = new Vec3(1.8, 8.0, 0.8);
		Vec3 angularVelocityBody = new Vec3(0.15, -0.10, 0.05);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample =
				worldSample(config, relativeAirVelocityBody, angularVelocityBody, omegas);
		RotorFlappingForceModel.ConfigurationRotorFlappingForceSample raw =
				worldSample.baselineRotorFlappingForceSample();
		RotorFlappingForceModel.ConfigurationRotorFlappingForceSample runtime =
				worldSample.runtimeReplacementBaselineRotorFlappingForceSample();

		assertEquals(config.rotors().size(), raw.rotorCount());
		assertEquals(config.rotors().size(), runtime.rotorCount());
		assertTrue(raw.totalFlappingForceBodyNewtons().dot(
				new Vec3(relativeAirVelocityBody.x(), 0.0, relativeAirVelocityBody.z())) < 0.0);
		assertTrue(raw.maximumEffectiveDiskTiltAngleRadians() > 0.0);
		assertTrue(raw.maximumThrustMagnitudeResidualNewtons() < 1.0e-12);

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample blocked =
				worldSample(config, new Vec3(8.0, 1.0, 0.0), Vec3.ZERO, omegas);
		assertEquals(config.rotors().size(), blocked.baselineRotorFlappingForceSample().rotorCount());
		assertEquals(0, blocked.runtimeReplacementBaselineRotorFlappingForceSample().rotorCount());
	}

	private static PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample(
			DroneConfig config,
			Vec3 relativeAirVelocityBody,
			Vec3 angularVelocityBody,
			double[] omegas
	) {
		return PropellerArchiveCtCpJWorldForceApplicationProvider
				.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"rotor_flapping_force_model",
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
