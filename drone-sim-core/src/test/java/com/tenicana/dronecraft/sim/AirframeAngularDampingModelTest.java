package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirframeAngularDampingModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void zeroAngularVelocityProducesNoTorqueOrPower() {
		AirframeAngularDampingModel.AirframeAngularDampingSample sample =
				AirframeAngularDampingModel.sampleSteady(
						dampingConfig(),
						Vec3.ZERO,
						new Vec3(8.0, 5.0, 18.0),
						1.0,
						0.8,
						new Vec3(0.02, 0.01, 0.03)
				);

		assertTrue(sample.totalDampingCoefficients().x() > 0.0);
		assertTrue(sample.totalDampingCoefficients().y() > 0.0);
		assertTrue(sample.totalDampingCoefficients().z() > 0.0);
		assertVectorEquals(Vec3.ZERO, sample.rawDampingTorqueBodyNewtonMeters(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.dampingTorqueBodyNewtonMeters(), 0.0);
		assertEquals(0.0, sample.powerDissipationWatts(), 0.0);
	}

	@Test
	void passiveTorqueOpposesEveryBodyRateAndClosesDissipatedPower() {
		Vec3 angularVelocity = new Vec3(0.30, 0.18, -0.24);
		AirframeAngularDampingModel.AirframeAngularDampingSample passive =
				AirframeAngularDampingModel.samplePassive(
						dampingConfig(),
						angularVelocity,
						new Vec3(5.0, 2.0, 8.0),
						1.0,
						0.25
				);
		Vec3 washCoefficients = new Vec3(0.02, 0.01, 0.03);
		AirframeAngularDampingModel.AirframeAngularDampingSample withWash =
				AirframeAngularDampingModel.sampleSteady(
						dampingConfig(),
						angularVelocity,
						new Vec3(5.0, 2.0, 8.0),
						1.0,
						0.25,
						washCoefficients
				);

		assertTrue(passive.dampingTorqueBodyNewtonMeters().x() * angularVelocity.x() < 0.0);
		assertTrue(passive.dampingTorqueBodyNewtonMeters().y() * angularVelocity.y() < 0.0);
		assertTrue(passive.dampingTorqueBodyNewtonMeters().z() * angularVelocity.z() < 0.0);
		assertEquals(-passive.dampingTorqueBodyNewtonMeters().dot(angularVelocity),
				passive.powerDissipationWatts(), 1.0e-15);
		assertEquals(0.0, passive.neuroBemGuardCoverage(), 0.0);
		assertVectorEquals(passive.totalDampingCoefficients().add(washCoefficients),
				withWash.totalDampingCoefficients(), 1.0e-15);
		assertVectorEquals(passive.rawDampingTorqueBodyNewtonMeters().subtract(
					angularVelocity.multiply(washCoefficients)),
				withWash.rawDampingTorqueBodyNewtonMeters(), 1.0e-15);
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				0.0,
				Math.sin(Math.PI * 0.25)
		);
		assertVectorEquals(bodyToWorld.rotate(passive.dampingTorqueBodyNewtonMeters()),
				passive.dampingTorqueWorldNewtonMeters(bodyToWorld), 1.0e-15);
	}

	@Test
	void airspeedSeparationAndNeuroBemGuardShapeDampingMagnitude() {
		DroneConfig config = dampingConfig().withAngularDragCoefficient(0.0);
		Vec3 lowRate = new Vec3(0.30, 0.18, -0.24);
		AirframeAngularDampingModel.AirframeAngularDampingSample still =
				AirframeAngularDampingModel.samplePassive(
						config,
						lowRate,
						Vec3.ZERO,
						1.0,
						0.0
				);
		AirframeAngularDampingModel.AirframeAngularDampingSample fast =
				AirframeAngularDampingModel.samplePassive(
						config,
						lowRate,
						new Vec3(0.0, 0.0, 20.0),
						1.0,
						0.0
				);
		Vec3 highSideslip = new Vec3(17.0, 0.0, 4.0);
		AirframeAngularDampingModel.AirframeAngularDampingSample attached =
				AirframeAngularDampingModel.samplePassive(
						config,
						lowRate,
						highSideslip,
						1.0,
						0.0
				);
		AirframeAngularDampingModel.AirframeAngularDampingSample separated =
				AirframeAngularDampingModel.samplePassive(
						config,
						lowRate,
						highSideslip,
						1.0,
						1.0
				);
		Vec3 highRate = new Vec3(8.0, 3.5, -6.0);
		AirframeAngularDampingModel.AirframeAngularDampingSample guarded =
				AirframeAngularDampingModel.samplePassive(
						config,
						highRate,
						new Vec3(0.0, 0.0, 28.0),
						1.0,
						0.0
				);

		assertTrue(fast.powerDissipationWatts() > still.powerDissipationWatts());
		assertTrue(separated.powerDissipationWatts() > attached.powerDissipationWatts());
		assertTrue(separated.separatedFlowDampingCoefficients().length()
				> attached.separatedFlowDampingCoefficients().length());
		assertEquals(1.0, guarded.neuroBemGuardCoverage(), 1.0e-12);
		Vec3 limit = guarded.neuroBemGuardAxisLimitNewtonMeters();
		assertTrue(Math.abs(guarded.dampingTorqueBodyNewtonMeters().x()) <= limit.x() + 1.0e-12);
		assertTrue(Math.abs(guarded.dampingTorqueBodyNewtonMeters().y()) <= limit.y() + 1.0e-12);
		assertTrue(Math.abs(guarded.dampingTorqueBodyNewtonMeters().z()) <= limit.z() + 1.0e-12);
		assertTrue(guarded.powerDissipationWatts() > 0.0);
	}

	@Test
	void ctCpJPassiveAerodynamicWrenchAddsRotorFlappingRateDampingAndObliqueInflowMoment() {
		DroneConfig config = DroneConfig.apDrone()
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
		Vec3 relativeAirVelocityBody = new Vec3(5.0, 4.0, 20.0);
		Vec3 angularVelocityBody = new Vec3(0.30, -0.18, 0.24);
		double[] omegas = hoverOmegas(config);
		double separatedFlowStateIntensity = 0.40;
		double dt = 0.01;
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample =
				PropellerArchiveCtCpJWorldForceApplicationProvider
						.sampleStaticAnchoredConfigurationFromWorldKinematics(
								"apDrone",
								"passive_airframe_angular_damping",
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
		AirframeAngularDampingModel.AirframeAngularDampingSample damping =
				worldSample.passiveAirframeAngularDampingSample(
						config,
						angularVelocityBody,
						separatedFlowStateIntensity
				);
		RotorFlappingForceModel.ConfigurationRotorFlappingForceSample flapping =
				worldSample.baselineRotorFlappingForceSample();
		RotorFlappingForceModel.ConfigurationRotorFlappingForceSample runtimeFlapping =
				worldSample.runtimeReplacementBaselineRotorFlappingForceSample();
		RotorObliqueInflowMomentModel.ConfigurationRotorObliqueInflowSample obliqueInflow =
				worldSample.baselineRotorObliqueInflowSample();
		RotorObliqueInflowMomentModel.ConfigurationRotorObliqueInflowSample runtimeObliqueInflow =
				worldSample.runtimeReplacementBaselineRotorObliqueInflowSample();
		RotorAngularRateDampingModel.ConfigurationRotorAngularRateDampingSample rotorDamping =
				worldSample.baselineRotorAngularRateDampingSample(angularVelocityBody);
		RotorAngularRateDampingModel.ConfigurationRotorAngularRateDampingSample runtimeRotorDamping =
				worldSample.runtimeReplacementBaselineRotorAngularRateDampingSample(angularVelocityBody);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample base =
				worldSample.rotorGravityTransientCrossflowLiftDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample passive =
				worldSample.rotorGravityTransientPassiveAerodynamicRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeBase =
				worldSample.runtimeReplacementRotorGravityTransientCrossflowLiftDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimePassive =
				worldSample.runtimeReplacementRotorGravityTransientPassiveAerodynamicRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						separatedFlowStateIntensity,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview preview =
				worldSample.rotorGravityTransientPassiveAerodynamicStepPreview(
						config,
						Vec3.ZERO,
						relativeAirVelocityBody,
						angularVelocityBody,
						omegas,
						omegas,
						separatedFlowStateIntensity,
						dt
				);

		assertTrue(damping.powerDissipationWatts() > 0.0);
		assertVectorEquals(base.totalForceWorldNewtons().add(flapping.totalFlappingForceBodyNewtons()),
				passive.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(base.totalTorqueBodyNewtonMeters()
						.add(damping.dampingTorqueBodyNewtonMeters())
						.add(flapping.totalTorqueCorrectionBodyNewtonMeters())
						.add(rotorDamping.totalDampingTorqueBodyNewtonMeters())
						.add(obliqueInflow.totalHubMomentBodyNewtonMeters()),
				passive.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(runtimeBase.totalForceWorldNewtons().add(
					runtimeFlapping.totalFlappingForceBodyNewtons()),
				runtimePassive.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(runtimeBase.totalTorqueBodyNewtonMeters()
						.add(damping.dampingTorqueBodyNewtonMeters())
						.add(runtimeFlapping.totalTorqueCorrectionBodyNewtonMeters())
						.add(runtimeRotorDamping.totalDampingTorqueBodyNewtonMeters())
						.add(runtimeObliqueInflow.totalHubMomentBodyNewtonMeters()),
				runtimePassive.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(angularVelocityBody.add(
					passive.angularAccelerationBodyRadiansPerSecondSquared().multiply(dt)),
				preview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
	}

	private static DroneConfig dampingConfig() {
		return DroneConfig.racingQuad().withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
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
