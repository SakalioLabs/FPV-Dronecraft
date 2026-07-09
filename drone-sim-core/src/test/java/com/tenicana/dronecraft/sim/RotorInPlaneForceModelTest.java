package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RotorInPlaneForceModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void pureAxialFlowProducesNoInPlaneForceMomentOrPower() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		RotorInPlaneForceModel.RotorInPlaneForceSample sample = RotorInPlaneForceModel.sample(
				rotor,
				rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters()),
				new Vec3(0.0, 8.0, 0.0),
				900.0,
				2.5,
				1.0,
				0.0,
				0.0,
				0.0
		);

		assertVectorEquals(Vec3.ZERO, sample.transverseAirVelocityBodyMetersPerSecond(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.diskDragForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.loadedInPlaneDragForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.totalInPlaneForceBodyNewtons(), 0.0);
		assertVectorEquals(Vec3.ZERO, sample.totalTorqueBodyNewtonMeters(), 0.0);
		assertEquals(0.0, sample.additionalShaftTorqueNewtonMeters(), 0.0);
		assertEquals(0.0, sample.additionalShaftPowerWatts(), 0.0);
		assertEquals(0.0, sample.translationalPowerDissipationWatts(), 0.0);
	}

	@Test
	void crossflowForceOpposesMotionAndClosesMomentTorqueAndPower() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		Vec3 momentArm = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
		Vec3 relativeAirVelocity = new Vec3(6.0, 1.5, 4.0);
		double omega = 900.0;
		RotorInPlaneForceModel.RotorInPlaneForceSample sample = RotorInPlaneForceModel.sample(
				rotor,
				momentArm,
				relativeAirVelocity,
				omega,
				2.5,
				1.0,
				0.35,
				0.20,
				0.10
		);
		RotorInPlaneForceModel.RotorInPlaneForceSample reversed = RotorInPlaneForceModel.sample(
				rotor,
				momentArm,
				new Vec3(-6.0, 1.5, -4.0),
				omega,
				2.5,
				1.0,
				0.35,
				0.20,
				0.10
		);

		assertTrue(sample.totalInPlaneForceBodyNewtons().dot(sample.transverseAirVelocityBodyMetersPerSecond()) < 0.0);
		assertEquals(0.0, sample.totalInPlaneForceBodyNewtons().dot(sample.rotorAxisBody()), 1.0e-15);
		assertVectorEquals(sample.diskDragForceBodyNewtons().add(sample.loadedInPlaneDragForceBodyNewtons()),
				sample.totalInPlaneForceBodyNewtons(), 1.0e-15);
		assertVectorEquals(momentArm.cross(sample.totalInPlaneForceBodyNewtons()),
				sample.forceMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(sample.rotorAxisBody().multiply(
					rotor.spinDirection() * sample.additionalShaftTorqueNewtonMeters()),
				sample.additionalReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(sample.forceMomentBodyNewtonMeters().add(
					sample.additionalReactionTorqueBodyNewtonMeters()),
				sample.totalTorqueBodyNewtonMeters(), 1.0e-15);
		assertEquals(sample.additionalShaftTorqueNewtonMeters() * omega,
				sample.additionalShaftPowerWatts(), 1.0e-15);
		assertEquals(-sample.totalInPlaneForceBodyNewtons().dot(relativeAirVelocity),
				sample.translationalPowerDissipationWatts(), 1.0e-15);
		assertVectorEquals(sample.totalInPlaneForceBodyNewtons().multiply(-1.0),
				reversed.totalInPlaneForceBodyNewtons(), 1.0e-15);
		assertEquals(sample.additionalShaftTorqueNewtonMeters(),
				reversed.additionalShaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(sample.inPlaneLoadFactor(), reversed.inPlaneLoadFactor(), 1.0e-15);
	}

	@Test
	void higherCrossflowRaisesForceLoadAndPowerWhileSymmetricQuadTorquesCancel() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec firstRotor = config.rotors().get(0);
		double omega = 900.0;
		RotorInPlaneForceModel.RotorInPlaneForceSample moderate = RotorInPlaneForceModel.sample(
				firstRotor,
				firstRotor.positionBodyMeters(),
				new Vec3(3.0, 0.0, 0.0),
				omega,
				2.5,
				1.0,
				0.0,
				0.0,
				0.0
		);
		RotorInPlaneForceModel.RotorInPlaneForceSample high = RotorInPlaneForceModel.sample(
				firstRotor,
				firstRotor.positionBodyMeters(),
				new Vec3(12.0, 0.0, 0.0),
				omega,
				2.5,
				1.0,
				0.0,
				0.0,
				0.0
		);
		assertTrue(high.totalInPlaneForceBodyNewtons().length()
				> moderate.totalInPlaneForceBodyNewtons().length());
		assertTrue(high.inPlaneLoadFactor() > moderate.inPlaneLoadFactor());
		assertTrue(high.additionalShaftTorqueNewtonMeters() > moderate.additionalShaftTorqueNewtonMeters());
		assertTrue(high.additionalShaftPowerWatts() > moderate.additionalShaftPowerWatts());

		List<RotorInPlaneForceModel.RotorInPlaneForceSample> rotorSamples = new ArrayList<>();
		for (RotorSpec rotor : config.rotors()) {
			rotorSamples.add(RotorInPlaneForceModel.sample(
					rotor,
					rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters()),
					new Vec3(6.0, 0.0, 0.0),
					omega,
					2.5,
					1.0,
					0.0,
					0.0,
					0.0
			));
		}
		RotorInPlaneForceModel.ConfigurationRotorInPlaneForceSample aggregate =
				RotorInPlaneForceModel.aggregate(rotorSamples);
		RotorInPlaneForceModel.RotorInPlaneForceSample first = rotorSamples.get(0);

		assertEquals(config.rotors().size(), aggregate.rotorCount());
		assertVectorEquals(first.totalInPlaneForceBodyNewtons().multiply(config.rotors().size()),
				aggregate.totalInPlaneForceBodyNewtons(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalForceMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalAdditionalReactionTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, aggregate.totalTorqueBodyNewtonMeters(), 1.0e-15);
		assertEquals(first.additionalShaftPowerWatts() * config.rotors().size(),
				aggregate.totalAdditionalShaftPowerWatts(), 1.0e-15);
		assertEquals(first.translationalPowerDissipationWatts() * config.rotors().size(),
				aggregate.totalTranslationalPowerDissipationWatts(), 1.0e-15);
	}

	@Test
	void ctCpJCrossflowWrenchAddsBaselineRotorForceMomentAndAngularResponse() {
		DroneConfig config = DroneConfig.apDrone();
		double[] omegas = hoverOmegas(config);
		Vec3 relativeAirVelocityBody = new Vec3(1.8, 8.0, 0.8);
		Vec3 angularVelocityBody = new Vec3(0.15, -0.10, 0.05);
		double dt = 0.01;
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample =
				worldSample(config, relativeAirVelocityBody, angularVelocityBody, omegas);

		RotorInPlaneForceModel.ConfigurationRotorInPlaneForceSample inPlane =
				worldSample.baselineRotorInPlaneForceSample();
		RotorInPlaneForceModel.ConfigurationRotorInPlaneForceSample runtimeInPlane =
				worldSample.runtimeReplacementBaselineRotorInPlaneForceSample();
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample airframe =
				worldSample.rotorGravityTransientAirframeDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						0.0,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample crossflow =
				worldSample.rotorGravityTransientCrossflowDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						angularVelocityBody,
						0.0,
						dt
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RotorOnlyStepPreview preview =
				worldSample.rotorGravityTransientCrossflowDragStepPreview(
						config,
						Vec3.ZERO,
						relativeAirVelocityBody,
						angularVelocityBody,
						omegas,
						omegas,
						0.0,
						dt
				);

		assertEquals(config.rotors().size(), inPlane.rotorCount());
		assertEquals(config.rotors().size(), runtimeInPlane.rotorCount());
		assertTrue(inPlane.totalInPlaneForceBodyNewtons().dot(
				new Vec3(relativeAirVelocityBody.x(), 0.0, relativeAirVelocityBody.z())) < 0.0);
		assertTrue(inPlane.totalAdditionalShaftPowerWatts() > 0.0,
				() -> "shaftPower=" + inPlane.totalAdditionalShaftPowerWatts());
		assertVectorEquals(airframe.totalForceWorldNewtons().add(inPlane.totalInPlaneForceBodyNewtons()),
				crossflow.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(airframe.totalTorqueBodyNewtonMeters().add(inPlane.totalTorqueBodyNewtonMeters()),
				crossflow.totalTorqueBodyNewtonMeters(), 1.0e-12);
		assertVectorEquals(angularVelocityBody.add(
					crossflow.angularAccelerationBodyRadiansPerSecondSquared().multiply(dt)),
				preview.nextAngularVelocityBodyRadiansPerSecond(), 1.0e-12);
	}

	@Test
	void ctCpJRuntimeCrossflowForceIsBlockedOutsideObliqueInflowEnvelope() {
		DroneConfig config = DroneConfig.apDrone();
		double[] omegas = hoverOmegas(config);
		Vec3 relativeAirVelocityBody = new Vec3(8.0, 1.0, 0.0);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample =
				worldSample(config, relativeAirVelocityBody, Vec3.ZERO, omegas);

		RotorInPlaneForceModel.ConfigurationRotorInPlaneForceSample raw =
				worldSample.baselineRotorInPlaneForceSample();
		RotorInPlaneForceModel.ConfigurationRotorInPlaneForceSample runtime =
				worldSample.runtimeReplacementBaselineRotorInPlaneForceSample();
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeAirframe =
				worldSample.runtimeReplacementRotorGravityTransientAirframeDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						Vec3.ZERO,
						0.0,
						0.01
				);
		PropellerArchiveCtCpJWorldForceApplicationProvider.RigidBodyWrenchSample runtimeCrossflow =
				worldSample.runtimeReplacementRotorGravityTransientCrossflowDragRigidBodyWrench(
						config,
						omegas,
						omegas,
						Vec3.ZERO,
						0.0,
						0.01
				);

		assertEquals(config.rotors().size(), raw.rotorCount());
		assertEquals(0, runtime.rotorCount());
		assertTrue(raw.totalInPlaneForceBodyNewtons().length() > 0.0);
		assertVectorEquals(runtimeAirframe.totalForceWorldNewtons(),
				runtimeCrossflow.totalForceWorldNewtons(), 1.0e-12);
		assertVectorEquals(runtimeAirframe.totalTorqueBodyNewtonMeters(),
				runtimeCrossflow.totalTorqueBodyNewtonMeters(), 1.0e-12);
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
						"rotor_in_plane_force_model",
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
