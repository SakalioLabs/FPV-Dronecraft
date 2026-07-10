package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorObliqueInflowMomentModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void hoverAxialFlowAndUnloadedRotorHaveNoSkewOrHubMoment() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = rotor.maxOmegaRadiansPerSecond() * 0.70;
		RotorObliqueInflowMomentModel.RotorObliqueInflowSample axial =
				RotorObliqueInflowMomentModel.sampleSteady(
						rotor,
						new Vec3(0.0, 4.0, 0.0),
						omega,
						2.0,
						7.5,
						0.0
				);
		RotorObliqueInflowMomentModel.RotorObliqueInflowMomentSample unloaded =
				RotorObliqueInflowMomentModel.sampleMoment(
						rotor,
						new Vec3(8.0, 0.0, 0.0),
						omega,
						0.0,
						0.0,
						0.0,
						0.0
				);

		assertTrue(axial.moment().wakeModelActive());
		assertEquals(0.0, axial.translationalLift().targetIntensity(), 0.0);
		assertEquals(0.0, axial.moment().wakeSkewAngleRadians(), 0.0);
		assertEquals(0.0, axial.moment().pittPetersFirstHarmonicGain(), 0.0);
		assertVectorEquals(Vec3.ZERO, axial.moment().totalHubMomentBodyNewtonMeters(), 0.0);
		assertFalse(unloaded.wakeModelActive());
		assertEquals(0.0, unloaded.wakeAxialConvectionVelocityMetersPerSecond(), 0.0);
		assertEquals(0.0, unloaded.wakeSkewAngleRadians(), 0.0);
		assertVectorEquals(Vec3.ZERO,
				unloaded.firstHarmonicTipAmplitudeGradientBodyMetersPerSecond(), 0.0);
		assertVectorEquals(Vec3.ZERO, unloaded.totalHubMomentBodyNewtonMeters(), 0.0);
	}

	@Test
	void crossflowReversesFirstHarmonicGradientAndHubMoment() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = rotor.maxOmegaRadiansPerSecond() * 0.70;
		double inducedVelocity = 7.0;
		RotorObliqueInflowMomentModel.RotorObliqueInflowSample positive =
				RotorObliqueInflowMomentModel.sampleSteady(
						rotor,
						new Vec3(8.0, 2.0, 0.0),
						omega,
						3.0,
						inducedVelocity,
						0.0
				);
		RotorObliqueInflowMomentModel.RotorObliqueInflowSample negative =
				RotorObliqueInflowMomentModel.sampleSteady(
						rotor,
						new Vec3(-8.0, 2.0, 0.0),
						omega,
						3.0,
						inducedVelocity,
						0.0
				);
		RotorObliqueInflowMomentModel.RotorObliqueInflowMomentSample positiveMoment = positive.moment();
		RotorObliqueInflowMomentModel.RotorObliqueInflowMomentSample negativeMoment = negative.moment();
		double expectedGain = 15.0 * Math.PI / 64.0
				* Math.tan(0.5 * positiveMoment.wakeSkewAngleRadians());
		Vec3 positiveTip = positiveMoment.firstHarmonicGradientDirectionBody()
				.multiply(rotor.radiusMeters());

		assertTrue(positive.translationalLift().intensity() > 0.0);
		assertTrue(positiveMoment.wakeSkewAngleRadians() > 0.0);
		assertEquals(expectedGain, positiveMoment.pittPetersFirstHarmonicGain(), 1.0e-15);
		assertEquals(inducedVelocity * expectedGain,
				positiveMoment.firstHarmonicTipInducedVelocityAmplitudeMetersPerSecond(), 1.0e-15);
		assertTrue(positiveMoment.firstHarmonicGradientDirectionBody().dot(
				positiveMoment.transverseAirVelocityBodyMetersPerSecond()) < 0.0);
		assertEquals(positiveMoment.firstHarmonicTipInducedVelocityAmplitudeMetersPerSecond(),
				positiveMoment.firstHarmonicInducedVelocityPerturbationMetersPerSecond(positiveTip),
				1.0e-15);
		assertEquals(-positiveMoment.firstHarmonicTipInducedVelocityAmplitudeMetersPerSecond(),
				positiveMoment.firstHarmonicInducedVelocityPerturbationMetersPerSecond(
						positiveTip.multiply(-1.0)),
				1.0e-15);
		assertTrue(positiveMoment.totalHubMomentBodyNewtonMeters().isFinite());
		assertTrue(positiveMoment.totalHubMomentBodyNewtonMeters().length() > 0.0);
		assertEquals(positiveMoment.hubMomentMagnitudeNewtonMeters(),
				negativeMoment.hubMomentMagnitudeNewtonMeters(), 1.0e-15);
		assertVectorEquals(positiveMoment.firstHarmonicTipAmplitudeGradientBodyMetersPerSecond()
					.multiply(-1.0),
				negativeMoment.firstHarmonicTipAmplitudeGradientBodyMetersPerSecond(), 1.0e-15);
		assertVectorEquals(positiveMoment.totalHubMomentBodyNewtonMeters().multiply(-1.0),
				negativeMoment.totalHubMomentBodyNewtonMeters(), 1.0e-15);
	}

	@Test
	void translationalLiftBuildsReleasesAndIsPenalizedInDescent() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double omega = rotor.maxOmegaRadiansPerSecond() * 0.70;
		double inducedVelocity = 8.0;
		Vec3 cleanCrossflow = new Vec3(8.0, 0.0, 0.0);
		RotorObliqueInflowMomentModel.TranslationalLiftSample steady =
				RotorObliqueInflowMomentModel.sampleSteadyTranslationalLift(
						rotor, cleanCrossflow, omega, inducedVelocity);
		RotorObliqueInflowMomentModel.RotorObliqueInflowSample descending =
				RotorObliqueInflowMomentModel.sampleSteady(
						rotor, new Vec3(8.0, -12.0, 0.0), omega, 3.0, inducedVelocity, 0.0);
		RotorObliqueInflowMomentModel.TranslationalLiftSample firstBuild =
				RotorObliqueInflowMomentModel.stepTranslationalLift(
						rotor, cleanCrossflow, omega, inducedVelocity, 0.0, 0.01);
		RotorObliqueInflowMomentModel.TranslationalLiftSample secondBuild =
				RotorObliqueInflowMomentModel.stepTranslationalLift(
						rotor, cleanCrossflow, omega, inducedVelocity, firstBuild.intensity(), 0.01);
		RotorObliqueInflowMomentModel.TranslationalLiftSample release =
				RotorObliqueInflowMomentModel.stepTranslationalLift(
						rotor, Vec3.ZERO, omega, inducedVelocity, secondBuild.intensity(), 0.01);

		assertTrue(steady.targetIntensity() > 0.0);
		assertEquals(steady.targetIntensity(), steady.intensity(), 0.0);
		assertTrue(descending.translationalLift().descentWashPenalty() < 1.0);
		assertTrue(descending.translationalLift().targetIntensity() < steady.targetIntensity());
		assertFalse(descending.moment().pittPetersFirstHarmonicApplicable());
		assertTrue(descending.moment().wakeAxialConvectionVelocityMetersPerSecond() < 0.0);
		assertEquals(0.0, descending.moment().pittPetersFirstHarmonicGain(), 0.0);
		assertTrue(firstBuild.intensity() > 0.0);
		assertTrue(firstBuild.intensity() < steady.targetIntensity());
		assertTrue(secondBuild.intensity() > firstBuild.intensity());
		assertEquals(0.0, release.targetIntensity(), 0.0);
		assertTrue(release.intensity() > 0.0);
		assertTrue(release.intensity() < secondBuild.intensity());
		assertTrue(firstBuild.buildTimeConstantSeconds() < firstBuild.releaseTimeConstantSeconds());
	}

	@Test
	void ctCpJProviderAddsAcceptedMomentAndBlocksUnsupportedQueries() {
		DroneConfig config = DroneConfig.apDrone();
		double omega = 7_946.7 * 2.0 * Math.PI / 60.0;
		double[] omegas = uniformOmegas(config, omega);
		Vec3 acceptedFlow = new Vec3(3.5, 13.5, 0.0);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample accepted =
				worldSample(config, acceptedFlow, omegas);
		RotorObliqueInflowMomentModel.ConfigurationRotorObliqueInflowSample raw =
				accepted.baselineRotorObliqueInflowSample();
		RotorObliqueInflowMomentModel.ConfigurationRotorObliqueInflowSample runtime =
				accepted.runtimeReplacementBaselineRotorObliqueInflowSample();
		RotorFlappingForceModel.ConfigurationRotorFlappingForceSample flapping =
				accepted.baselineRotorFlappingForceSample();
		RotorFlappingForceModel.ConfigurationRotorFlappingForceSample runtimeFlapping =
				accepted.runtimeReplacementBaselineRotorFlappingForceSample();
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample base =
				accepted.rotorGravityTransientCrossflowLiftDragRigidBodyWrench(
						config, omegas, omegas, Vec3.ZERO, 0.0, 0.01);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample passive =
				accepted.rotorGravityTransientPassiveAerodynamicRigidBodyWrench(
						config, omegas, omegas, Vec3.ZERO, 0.0, 0.01);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeBase =
				accepted.runtimeReplacementRotorGravityTransientCrossflowLiftDragRigidBodyWrench(
						config, omegas, omegas, Vec3.ZERO, 0.0, 0.01);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimePassive =
				accepted.runtimeReplacementRotorGravityTransientPassiveAerodynamicRigidBodyWrench(
						config, omegas, omegas, Vec3.ZERO, 0.0, 0.01);

		assertEquals(config.rotors().size(), raw.rotorCount());
		assertEquals(config.rotors().size(), runtime.rotorCount());
		assertEquals(config.rotors().size(), runtime.pittPetersFirstHarmonicApplicableRotorCount());
		assertTrue(raw.averageTranslationalLiftIntensity() > 0.0);
		assertTrue(raw.totalHubMomentBodyNewtonMeters().length() > 0.0);
		assertVectorEquals(base.totalForceWorldNewtons().add(
				flapping.totalFlappingForceBodyNewtons()), passive.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(base.totalTorqueBodyNewtonMeters()
					.add(flapping.totalTorqueCorrectionBodyNewtonMeters())
					.add(raw.totalHubMomentBodyNewtonMeters()),
				passive.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(runtimeBase.totalForceWorldNewtons().add(
				runtimeFlapping.totalFlappingForceBodyNewtons()),
				runtimePassive.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(runtimeBase.totalTorqueBodyNewtonMeters()
					.add(runtimeFlapping.totalTorqueCorrectionBodyNewtonMeters())
					.add(runtime.totalHubMomentBodyNewtonMeters()),
				runtimePassive.totalTorqueBodyNewtonMeters(), 1.0e-12);

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample obliqueOutside =
				worldSample(config, new Vec3(8.0, 1.0, 0.0), omegas);
		assertEquals(config.rotors().size(), obliqueOutside.baselineRotorObliqueInflowSample().rotorCount());
		assertEquals(0, obliqueOutside.runtimeReplacementBaselineRotorObliqueInflowSample().rotorCount());
		assertTrue(obliqueOutside.baselineRotorObliqueInflowSample()
				.totalHubMomentBodyNewtonMeters().length() > 0.0);

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample lookupBlocked =
				worldSample(config, new Vec3(0.0, 30.0, 0.0), omegas);
		assertEquals(0, lookupBlocked.baselineRotorObliqueInflowSample().rotorCount());
		assertEquals(0, lookupBlocked.runtimeReplacementBaselineRotorObliqueInflowSample().rotorCount());
		assertVectorEquals(Vec3.ZERO,
				lookupBlocked.baselineRotorObliqueInflowSample().totalHubMomentBodyNewtonMeters(), 0.0);
	}

	private static PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample(
			DroneConfig config,
			Vec3 relativeAirVelocityBody,
			double[] omegas
	) {
		return PropellerArchiveCtCpJWorldForceApplicationProvider
				.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"rotor_oblique_inflow_moment_model",
						config,
						Vec3.ZERO,
						Quaternion.IDENTITY,
						relativeAirVelocityBody,
						Vec3.ZERO,
						Vec3.ZERO,
						null,
						omegas,
						RHO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
	}

	private static double[] uniformOmegas(DroneConfig config, double omegaRadiansPerSecond) {
		double[] omegas = new double[config.rotors().size()];
		for (int i = 0; i < omegas.length; i++) {
			omegas[i] = omegaRadiansPerSecond;
		}
		return omegas;
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, "x");
		assertEquals(expected.y(), actual.y(), tolerance, "y");
		assertEquals(expected.z(), actual.z(), tolerance, "z");
	}
}
