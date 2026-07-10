package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RotorObliqueMomentumInflowModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void hoverMatchesIdealMomentumSolutionAndClosesSiOutputs() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double thrust = 2.4;
		double area = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double expectedInducedVelocity = Math.sqrt(thrust / (2.0 * RHO * area));
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample sample =
				RotorObliqueMomentumInflowModel.solve(rotor, Vec3.ZERO, thrust, RHO);

		assertEquals(RotorObliqueMomentumInflowModel.Status.SOLVED_HOVER, sample.status());
		assertTrue(sample.solved());
		assertFalse(sample.blocked());
		assertEquals(area, sample.diskAreaSquareMeters(), 1.0e-15);
		assertEquals(thrust / area, sample.diskLoadingNewtonsPerSquareMeter(), 1.0e-12);
		assertEquals(expectedInducedVelocity, sample.idealHoverInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(expectedInducedVelocity, sample.inducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(expectedInducedVelocity, sample.resultantDiskVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.normalDiskMassFlowKilogramsPerSecond(),
				sample.effectiveMomentumMassFlowKilogramsPerSecond(), 1.0e-15);
		assertEquals(thrust, sample.momentumTheoryThrustNewtons(), 1.0e-12);
		assertEquals(0.0, sample.thrustClosureResidualFraction(), 1.0e-12);
		assertEquals(thrust * expectedInducedVelocity, sample.idealInducedPowerWatts(), 1.0e-12);
		assertEquals(sample.idealInducedPowerWatts(), sample.idealMomentumPowerWatts(), 1.0e-12);
		assertEquals(0, sample.solverIterations());
	}

	@Test
	void axialClimbMatchesClosedFormMomentumSolution() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double thrust = 2.4;
		double axialVelocity = 6.0;
		double area = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double expectedInducedVelocity = 0.5 * (
				Math.sqrt(axialVelocity * axialVelocity + 2.0 * thrust / (RHO * area))
						- axialVelocity
		);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample sample =
				RotorObliqueMomentumInflowModel.solve(
						rotor, new Vec3(0.0, axialVelocity, 0.0), thrust, RHO);

		assertEquals(RotorObliqueMomentumInflowModel.Status.SOLVED_NORMAL_WORKING, sample.status());
		assertEquals(expectedInducedVelocity, sample.inducedVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, sample.transverseSpeedMetersPerSecond(), 0.0);
		assertEquals(sample.normalDiskMassFlowKilogramsPerSecond(),
				sample.effectiveMomentumMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(thrust, sample.momentumTheoryThrustNewtons(), 1.0e-12);
		assertEquals(thrust * axialVelocity, sample.usefulAxialPowerWatts(), 1.0e-12);
		assertEquals(sample.usefulAxialPowerWatts() + sample.idealInducedPowerWatts(),
				sample.idealMomentumPowerWatts(), 1.0e-12);
	}

	@Test
	void transverseFlowMonotonicallyReducesInducedVelocityAndPreservesClosure() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double thrust = 2.4;
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample hover =
				RotorObliqueMomentumInflowModel.solve(rotor, Vec3.ZERO, thrust, RHO);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample moderate =
				RotorObliqueMomentumInflowModel.solve(rotor, new Vec3(5.0, 0.0, 0.0), thrust, RHO);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample fast =
				RotorObliqueMomentumInflowModel.solve(rotor, new Vec3(10.0, 0.0, 0.0), thrust, RHO);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample opposite =
				RotorObliqueMomentumInflowModel.solve(rotor, new Vec3(-5.0, 0.0, 0.0), thrust, RHO);

		assertTrue(moderate.solved());
		assertTrue(fast.solved());
		assertTrue(moderate.inducedVelocityMetersPerSecond() < hover.inducedVelocityMetersPerSecond());
		assertTrue(fast.inducedVelocityMetersPerSecond() < moderate.inducedVelocityMetersPerSecond());
		assertTrue(moderate.effectiveMomentumMassFlowKilogramsPerSecond()
				> moderate.normalDiskMassFlowKilogramsPerSecond());
		assertTrue(moderate.idealInducedPowerWatts() < hover.idealInducedPowerWatts());
		assertEquals(moderate.inducedVelocityMetersPerSecond(),
				opposite.inducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(thrust, moderate.momentumTheoryThrustNewtons(), 1.0e-12);
		assertEquals(thrust, fast.momentumTheoryThrustNewtons(), 1.0e-12);
		assertTrue(Math.abs(moderate.thrustClosureResidualFraction()) < 1.0e-12);
		assertTrue(Math.abs(fast.thrustClosureResidualFraction()) < 1.0e-12);
		assertTrue(moderate.solverIterations() > 0);
		assertTrue(moderate.solverIterations() <= 80);
	}

	@Test
	void descentIsBlockedForVrsModelWhileZeroThrustRemainsFinite() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample descent =
				RotorObliqueMomentumInflowModel.solve(
						rotor, new Vec3(0.0, -8.0, 0.0), 2.4, RHO);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample lowDescent =
				RotorObliqueMomentumInflowModel.solve(
						rotor, new Vec3(3.0, -0.1, 0.0), 2.4, RHO);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample zero =
				RotorObliqueMomentumInflowModel.solve(
						rotor, new Vec3(3.0, -2.0, 0.0), 0.0, RHO);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample hover =
				RotorObliqueMomentumInflowModel.solve(rotor, Vec3.ZERO, 2.4, RHO);
		RotorObliqueMomentumInflowModel.ConfigurationRotorObliqueMomentumInflowSample aggregate =
				RotorObliqueMomentumInflowModel.aggregate(List.of(hover, descent, zero));

		assertEquals(RotorObliqueMomentumInflowModel.Status.BLOCKED_DESCENT_REQUIRES_VRS_MODEL,
				descent.status());
		assertTrue(descent.blocked());
		assertEquals(0.0, descent.inducedVelocityMetersPerSecond(), 0.0);
		assertEquals(0.0, descent.idealMomentumPowerWatts(), 0.0);
		assertEquals(RotorObliqueMomentumInflowModel.Status.SOLVED_WAKE_CONVECTED_DESCENT,
				lowDescent.status());
		assertTrue(lowDescent.solved());
		assertEquals(RotorObliqueMomentumInflowModel.Status.ZERO_THRUST, zero.status());
		assertFalse(zero.blocked());
		assertTrue(zero.relativeAirVelocityBodyMetersPerSecond().isFinite());
		assertEquals(3, aggregate.rotorCount());
		assertEquals(1, aggregate.solvedRotorCount());
		assertEquals(1, aggregate.blockedRotorCount());
		assertEquals(1, aggregate.zeroThrustRotorCount());
		assertEquals(4.8, aggregate.requestedThrustNewtons(), 1.0e-15);
		assertEquals(2.4, aggregate.solvedThrustNewtons(), 1.0e-15);
	}

	@Test
	void ctCpJProviderExposesAcceptedSolutionsAndKeepsRuntimeEnvelope() {
		DroneConfig config = DroneConfig.apDrone();
		double omega = 7_946.7 * 2.0 * Math.PI / 60.0;
		double[] omegas = uniformOmegas(config, omega);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample accepted =
				worldSample(config, new Vec3(3.5, 13.5, 0.0), omegas);
		RotorObliqueMomentumInflowModel.ConfigurationRotorObliqueMomentumInflowSample raw =
				accepted.baselineRotorObliqueMomentumInflowSample();
		RotorObliqueMomentumInflowModel.ConfigurationRotorObliqueMomentumInflowSample runtime =
				accepted.runtimeReplacementBaselineRotorObliqueMomentumInflowSample();

		assertEquals(config.rotors().size(), raw.rotorCount());
		assertEquals(config.rotors().size(), raw.solvedRotorCount());
		assertEquals(config.rotors().size(), runtime.rotorCount());
		assertEquals(config.rotors().size(), runtime.solvedRotorCount());
		assertEquals(0, runtime.blockedRotorCount());
		assertTrue(runtime.idealInducedPowerWatts() > 0.0);
		assertTrue(runtime.idealMomentumPowerWatts() > runtime.idealInducedPowerWatts());
		assertTrue(runtime.maximumThrustClosureResidualNewtons() < 1.0e-10);

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample obliqueOutside =
				worldSample(config, new Vec3(8.0, 1.0, 0.0), omegas);
		assertEquals(config.rotors().size(),
				obliqueOutside.baselineRotorObliqueMomentumInflowSample().solvedRotorCount());
		assertEquals(0, obliqueOutside.runtimeReplacementBaselineRotorObliqueMomentumInflowSample()
				.rotorCount());

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample lookupBlocked =
				worldSample(config, new Vec3(0.0, 30.0, 0.0), omegas);
		assertEquals(0, lookupBlocked.baselineRotorObliqueMomentumInflowSample().rotorCount());
		assertEquals(0, lookupBlocked.runtimeReplacementBaselineRotorObliqueMomentumInflowSample()
				.rotorCount());
	}

	private static PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample(
			DroneConfig config,
			Vec3 relativeAirVelocityBody,
			double[] omegas
	) {
		return PropellerArchiveCtCpJWorldForceApplicationProvider
				.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"rotor_oblique_momentum_inflow_model",
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
}
