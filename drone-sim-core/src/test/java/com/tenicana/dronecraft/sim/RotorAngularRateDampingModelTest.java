package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorAngularRateDampingModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void stoppedRotorOrZeroBodyRateProducesNoTorqueOrPower() {
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		RotorAngularRateDampingModel.RotorAngularRateDampingSample stopped =
				RotorAngularRateDampingModel.sample(
						rotor,
						new Vec3(5.0, 2.0, -4.0),
						rotor.thrustAxisBody(),
						0.0,
						2.0,
						1.0,
						1.0,
						0.0,
						0.0
				);
		RotorAngularRateDampingModel.RotorAngularRateDampingSample stationary =
				RotorAngularRateDampingModel.sample(
						rotor,
						Vec3.ZERO,
						rotor.thrustAxisBody(),
						rotor.maxOmegaRadiansPerSecond() * 0.7,
						2.0,
						1.0,
						1.0,
						0.0,
						0.0
				);

		assertTrue(!stopped.active());
		assertTrue(!stationary.active());
		assertVectorEquals(Vec3.ZERO, stopped.dampingTorqueBodyNewtonMeters(), 0.0);
		assertVectorEquals(Vec3.ZERO, stationary.dampingTorqueBodyNewtonMeters(), 0.0);
		assertEquals(0.0, stopped.powerDissipationWatts(), 0.0);
		assertEquals(0.0, stationary.powerDissipationWatts(), 0.0);
		assertEquals(0.0, stopped.rateLoadFactor(), 0.0);
	}

	@Test
	void torqueOpposesAxialAndTransverseRatesAndClosesDissipatedPower() {
		DroneConfig config = DroneConfig.racingQuad().withRotorDiskDragCoefficient(0.010);
		RotorSpec rotor = config.rotors().get(0);
		Vec3 bodyRates = new Vec3(2.4, 1.2, -3.1);
		Vec3 diskAxis = new Vec3(-0.04, 0.998, 0.05).normalized();
		RotorAngularRateDampingModel.RotorAngularRateDampingSample sample =
				RotorAngularRateDampingModel.sample(
						rotor,
						bodyRates,
						diskAxis,
						rotor.maxOmegaRadiansPerSecond() * 0.72,
						2.4,
						1.0,
						1.25,
						0.20,
						0.30
				);

		assertTrue(sample.active());
		assertVectorEquals(bodyRates,
				sample.axialRateBodyRadiansPerSecond().add(sample.transverseRateBodyRadiansPerSecond()),
				1.0e-15);
		assertEquals(0.0, sample.transverseRateBodyRadiansPerSecond().dot(sample.rotorDiskAxisBody()),
				1.0e-15);
		assertTrue(sample.rawTransverseTorqueBodyNewtonMeters().dot(
				sample.transverseRateBodyRadiansPerSecond()) < 0.0);
		assertTrue(sample.rawAxialTorqueBodyNewtonMeters().dot(
				sample.axialRateBodyRadiansPerSecond()) < 0.0);
		assertVectorEquals(sample.rawTransverseTorqueBodyNewtonMeters().add(
					sample.rawAxialTorqueBodyNewtonMeters()),
				sample.rawDampingTorqueBodyNewtonMeters(), 1.0e-15);
		assertEquals(sample.transverseMomentPerRadPerSecond() * 0.22,
				sample.axialMomentPerRadPerSecond(), 1.0e-15);
		assertTrue(sample.dampingTorqueBodyNewtonMeters().dot(bodyRates) < 0.0);
		assertEquals(-sample.dampingTorqueBodyNewtonMeters().dot(bodyRates),
				sample.powerDissipationWatts(), 1.0e-15);
	}

	@Test
	void rateLoadDensityDirtyAirAndTorqueLimitShapeResponse() {
		DroneConfig config = DroneConfig.racingQuad().withRotorDiskDragCoefficient(0.010);
		RotorSpec rotor = config.rotors().get(0);
		double omega = rotor.maxOmegaRadiansPerSecond() * 0.75;
		Vec3 moderateRates = new Vec3(1.8, 0.6, -2.2);
		RotorAngularRateDampingModel.RotorAngularRateDampingSample thinClean =
				RotorAngularRateDampingModel.sample(
						rotor,
						moderateRates,
						rotor.thrustAxisBody(),
						omega,
						2.0,
						0.55,
						1.0,
						0.0,
						0.0
				);
		RotorAngularRateDampingModel.RotorAngularRateDampingSample denseDirty =
				RotorAngularRateDampingModel.sample(
						rotor,
						moderateRates,
						rotor.thrustAxisBody(),
						omega,
						2.0,
						1.10,
						1.5,
						0.6,
						0.7
				);
		RotorAngularRateDampingModel.RotorAngularRateDampingSample limited =
				RotorAngularRateDampingModel.sample(
						rotor,
						new Vec3(180.0, 90.0, -160.0),
						rotor.thrustAxisBody(),
						omega,
						rotor.maxThrustNewtons(),
						1.0,
						2.0,
						1.0,
						1.0
				);

		assertTrue(denseDirty.transverseMomentPerRadPerSecond()
				> thinClean.transverseMomentPerRadPerSecond());
		assertTrue(denseDirty.powerDissipationWatts() > thinClean.powerDissipationWatts());
		assertEquals(0.0, RotorAngularRateDampingModel.loadFactor(
				rotor, new Vec3(0.5, 0.1, -0.4), omega), 0.0);
		assertTrue(RotorAngularRateDampingModel.loadFactor(
				rotor, new Vec3(12.0, 3.0, -10.0), omega) > 0.0);
		assertTrue(limited.torqueClamped());
		assertTrue(Math.abs(limited.dampingTorqueBodyNewtonMeters().x()) <= 0.18);
		assertTrue(Math.abs(limited.dampingTorqueBodyNewtonMeters().y()) <= 0.18);
		assertTrue(Math.abs(limited.dampingTorqueBodyNewtonMeters().z()) <= 0.18);
		assertTrue(limited.powerDissipationWatts() > 0.0);
	}

	@Test
	void ctCpJBaselineUsesFlappedAxesAndBlocksOutOfEnvelope() {
		DroneConfig config = DroneConfig.apDrone();
		double[] omegas = hoverOmegas(config);
		Vec3 bodyRates = new Vec3(0.30, -0.18, 0.24);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample =
				worldSample(config, new Vec3(1.8, 8.0, 0.8), bodyRates, omegas);
		RotorFlappingForceModel.ConfigurationRotorFlappingForceSample flapping =
				worldSample.baselineRotorFlappingForceSample();
		RotorAngularRateDampingModel.ConfigurationRotorAngularRateDampingSample damping =
				worldSample.baselineRotorAngularRateDampingSample(bodyRates);
		RotorAngularRateDampingModel.ConfigurationRotorAngularRateDampingSample runtimeDamping =
				worldSample.runtimeReplacementBaselineRotorAngularRateDampingSample(bodyRates);

		assertEquals(config.rotors().size(), damping.rotorCount());
		assertEquals(config.rotors().size(), damping.activeRotorCount());
		assertEquals(config.rotors().size(), runtimeDamping.rotorCount());
		assertTrue(damping.totalDampingTorqueBodyNewtonMeters().dot(bodyRates) < 0.0);
		assertEquals(-damping.totalDampingTorqueBodyNewtonMeters().dot(bodyRates),
				damping.totalPowerDissipationWatts(), 1.0e-15);
		for (int i = 0; i < damping.rotorCount(); i++) {
			assertVectorEquals(flapping.rotorSamples().get(i).effectiveDiskAxisBody(),
					damping.rotorSamples().get(i).rotorDiskAxisBody(), 1.0e-15);
		}

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample blocked =
				worldSample(config, new Vec3(8.0, 1.0, 0.0), bodyRates, omegas);
		assertEquals(config.rotors().size(),
				blocked.baselineRotorAngularRateDampingSample(bodyRates).rotorCount());
		assertEquals(0,
				blocked.runtimeReplacementBaselineRotorAngularRateDampingSample(bodyRates).rotorCount());
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
						"rotor_angular_rate_damping_model",
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
