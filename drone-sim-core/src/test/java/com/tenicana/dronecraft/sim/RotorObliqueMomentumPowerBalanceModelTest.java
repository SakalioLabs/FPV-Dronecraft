package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RotorObliqueMomentumPowerBalanceModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void staticAnchorClosesHoverFigureOfMeritPowerAndTorqueInSiUnits() {
		ReferenceSample standard = referenceSample("static_anchor_low_rpm", RHO);
		ReferenceSample dense = referenceSample("static_anchor_low_rpm", RHO * 2.0);
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample sample =
				standard.powerBalance();

		assertEquals(RotorObliqueMomentumPowerBalanceModel.Status.SOLVED_HOVER, sample.status());
		assertTrue(sample.solved());
		assertFalse(sample.blocked());
		assertTrue(sample.partitioned());
		assertTrue(sample.hoverFigureOfMeritApplicable());
		assertEquals(standard.dimensional().shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-15);
		assertEquals(standard.dimensional().shaftTorqueNewtonMeters(),
				sample.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(sample.shaftPowerWatts(),
				sample.shaftTorqueNewtonMeters() * sample.angularVelocityRadiansPerSecond(), 1.0e-15);
		assertEquals(standard.dimensional().idealMomentumPowerWatts(),
				sample.idealMomentumPowerWatts(), 1.0e-15);
		assertEquals(standard.dimensional().shaftPowerResidualWatts(),
				sample.signedShaftPowerMarginWatts(), 1.0e-15);
		assertEquals(sample.signedShaftPowerMarginWatts(),
				sample.unresolvedNonidealPowerWatts(), 1.0e-15);
		assertEquals(0.0, sample.shaftPowerDeficitWatts(), 0.0);
		assertEquals(standard.dimensional().idealMomentumPowerOverShaftPower(),
				sample.hoverFigureOfMerit(), 1.0e-15);
		assertEquals(1.0 / sample.hoverFigureOfMerit(),
				sample.inducedPowerFactorUpperBound(), 1.0e-15);
		assertEquals(0.0, sample.powerClosureResidualWatts(), 1.0e-15);
		assertEquals(0.0, sample.torqueClosureResidualNewtonMeters(), 1.0e-18);

		assertEquals(sample.inflow().thrustNewtons() * 2.0,
				dense.powerBalance().inflow().thrustNewtons(), 1.0e-15);
		assertEquals(sample.shaftPowerWatts() * 2.0,
				dense.powerBalance().shaftPowerWatts(), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters() * 2.0,
				dense.powerBalance().shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(sample.inflow().inducedVelocityMetersPerSecond(),
				dense.powerBalance().inflow().inducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(sample.hoverFigureOfMerit(),
				dense.powerBalance().hoverFigureOfMerit(), 1.0e-15);
	}

	@Test
	void midAdvanceRatioPreservesCtCpPowerAndExposesOnlyLumpedLossBound() {
		ReferenceSample reference = referenceSample("mid_domain_mid_rpm", RHO);
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample sample =
				reference.powerBalance();

		assertEquals(RotorObliqueMomentumPowerBalanceModel.Status.SOLVED_NORMAL_WORKING,
				sample.status());
		assertTrue(sample.solved());
		assertFalse(sample.hoverFigureOfMeritApplicable());
		assertEquals(0.0, sample.hoverFigureOfMerit(), 0.0);
		assertEquals(reference.dimensional().usefulAxialThrustPowerWatts(),
				sample.usefulAxialPowerWatts(), 1.0e-12);
		assertEquals(reference.dimensional().idealInducedPowerWatts(),
				sample.idealInducedPowerWatts(), 1.0e-12);
		assertEquals(reference.dimensional().idealMomentumPowerWatts(),
				sample.idealMomentumPowerWatts(), 1.0e-12);
		assertEquals(reference.dimensional().axialPropulsiveEfficiency(),
				sample.axialPropulsiveEfficiency(), 1.0e-15);
		assertEquals(reference.dimensional().idealMomentumPowerOverShaftPower(),
				sample.momentumPowerFraction(), 1.0e-12);
		assertTrue(sample.unresolvedNonidealPowerWatts() > 0.0);
		assertTrue(sample.nonUsefulPowerOverIdealInducedPower() > 1.0);
		assertEquals(sample.nonUsefulPowerOverIdealInducedPower(),
				sample.inducedPowerFactorUpperBound(), 0.0);
		assertEquals(sample.unresolvedNonidealPowerWatts(),
				sample.unresolvedNonidealTorqueNewtonMeters()
						* sample.angularVelocityRadiansPerSecond(), 1.0e-15);
		assertEquals(0.0, sample.powerClosureResidualWatts(), 1.0e-15);
	}

	@Test
	void highAdvanceReferenceBlocksInsteadOfInventingNegativeLossPower() {
		ReferenceSample high = referenceSample("high_domain_max_rpm", RHO);
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample sample =
				high.powerBalance();
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample momentumBlocked =
				RotorObliqueMomentumPowerBalanceModel.solve(
						DroneConfig.apDrone().rotors().get(0),
						new Vec3(0.0, -8.0, 0.0),
						2.4,
						50.0,
						1_000.0,
						RHO
				);

		assertEquals(RotorObliqueMomentumPowerBalanceModel.Status.BLOCKED_SHAFT_POWER_DEFICIT,
				sample.status());
		assertTrue(sample.blocked());
		assertTrue(sample.partitioned());
		assertTrue(sample.momentumPowerFraction() > 1.0);
		assertTrue(sample.signedShaftPowerMarginWatts() < 0.0);
		assertEquals(0.0, sample.unresolvedNonidealPowerWatts(), 0.0);
		assertEquals(sample.idealMomentumPowerWatts() - sample.shaftPowerWatts(),
				sample.shaftPowerDeficitWatts(), 1.0e-15);
		assertEquals(0.0, sample.inducedPowerFactorUpperBound(), 0.0);
		assertEquals(0.0, sample.powerClosureResidualWatts(), 1.0e-15);

		assertEquals(RotorObliqueMomentumPowerBalanceModel.Status.BLOCKED_MOMENTUM_INFLOW,
				momentumBlocked.status());
		assertTrue(momentumBlocked.blocked());
		assertFalse(momentumBlocked.partitioned());
		assertEquals(50.0, momentumBlocked.shaftPowerWatts(), 0.0);
		assertEquals(0.0, momentumBlocked.idealMomentumPowerWatts(), 0.0);
		assertEquals(0.0, momentumBlocked.unresolvedNonidealPowerWatts(), 0.0);
		assertThrows(IllegalArgumentException.class, () ->
				RotorObliqueMomentumPowerBalanceModel.solve(
						DroneConfig.apDrone().rotors().get(0),
						Vec3.ZERO,
						1.0,
						1.0,
						0.0,
						RHO
				));
	}

	@Test
	void aggregateKeepsSolvedAndDeficitPowerSeparate() {
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample hover =
				referenceSample("static_anchor_low_rpm", RHO).powerBalance();
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample mid =
				referenceSample("mid_domain_mid_rpm", RHO).powerBalance();
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample high =
				referenceSample("high_domain_max_rpm", RHO).powerBalance();
		RotorObliqueMomentumPowerBalanceModel.ConfigurationRotorObliqueMomentumPowerBalanceSample aggregate =
				RotorObliqueMomentumPowerBalanceModel.aggregate(List.of(hover, mid, high));

		assertEquals(3, aggregate.rotorCount());
		assertEquals(2, aggregate.solvedRotorCount());
		assertEquals(1, aggregate.blockedRotorCount());
		assertEquals(1, aggregate.shaftPowerDeficitRotorCount());
		assertEquals(0, aggregate.momentumBlockedRotorCount());
		assertEquals(hover.shaftPowerWatts() + mid.shaftPowerWatts() + high.shaftPowerWatts(),
				aggregate.partitionedShaftPowerWatts(), 1.0e-15);
		assertEquals(high.shaftPowerDeficitWatts(), aggregate.shaftPowerDeficitWatts(), 1.0e-15);
		assertEquals(hover.unresolvedNonidealPowerWatts() + mid.unresolvedNonidealPowerWatts(),
				aggregate.unresolvedNonidealPowerWatts(), 1.0e-15);
		assertTrue(aggregate.momentumPowerFraction() > 1.0);
		assertTrue(aggregate.maximumPowerClosureResidualWatts() < 1.0e-14);
		assertTrue(aggregate.maximumTorqueClosureResidualNewtonMeters() < 1.0e-16);
	}

	@Test
	void worldProviderExposesObliquePowerBalanceWithoutExpandingRuntimeEnvelope() {
		DroneConfig config = DroneConfig.apDrone();
		double omega = 7_946.7 * 2.0 * Math.PI / 60.0;
		double[] omegas = uniformOmegas(config, omega);
		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample accepted =
				worldSample(config, new Vec3(3.5, 13.5, 0.0), omegas);
		RotorObliqueMomentumPowerBalanceModel.ConfigurationRotorObliqueMomentumPowerBalanceSample raw =
				accepted.baselineRotorObliqueMomentumPowerBalanceSample();
		RotorObliqueMomentumPowerBalanceModel.ConfigurationRotorObliqueMomentumPowerBalanceSample runtime =
				accepted.runtimeReplacementBaselineRotorObliqueMomentumPowerBalanceSample();

		assertEquals(config.rotors().size(), raw.rotorCount());
		assertEquals(config.rotors().size(), raw.solvedRotorCount());
		assertEquals(config.rotors().size(), runtime.rotorCount());
		assertEquals(config.rotors().size(), runtime.solvedRotorCount());
		assertEquals(0, runtime.blockedRotorCount());
		assertTrue(runtime.requestedShaftPowerWatts() > runtime.idealMomentumPowerWatts());
		assertTrue(runtime.unresolvedNonidealPowerWatts() > 0.0);
		assertTrue(runtime.nonUsefulPowerOverIdealInducedPower() > 1.0);
		assertTrue(runtime.maximumPowerClosureResidualWatts() < 1.0e-12);
		assertEquals(runtime.requestedShaftPowerWatts(),
				runtime.shaftTorqueNewtonMeters() * omega, 1.0e-12);

		PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample outsideRuntime =
				worldSample(config, new Vec3(8.0, 1.0, 0.0), omegas);
		assertEquals(config.rotors().size(),
				outsideRuntime.baselineRotorObliqueMomentumPowerBalanceSample().rotorCount());
		assertEquals(0, outsideRuntime.runtimeReplacementBaselineRotorObliqueMomentumPowerBalanceSample()
				.rotorCount());
	}

	private static ReferenceSample referenceSample(String caseName, double airDensityKgPerCubicMeter) {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double diameter = rotor.radiusMeters() * 2.0;
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery query =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						caseName,
						diameter,
						airDensityKgPerCubicMeter
				);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);
		Vec3 relativeAirVelocity = rotor.thrustAxisBody().multiply(
				dimensional.axialAdvanceSpeedMetersPerSecond()
		);
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample powerBalance =
				RotorObliqueMomentumPowerBalanceModel.solve(
						rotor,
						relativeAirVelocity,
						dimensional.thrustNewtons(),
						dimensional.shaftPowerWatts(),
						dimensional.angularVelocityRadiansPerSecond(),
						airDensityKgPerCubicMeter
				);
		return new ReferenceSample(dimensional, powerBalance);
	}

	private static PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample(
			DroneConfig config,
			Vec3 relativeAirVelocityBody,
			double[] omegas
	) {
		return PropellerArchiveCtCpJWorldForceApplicationProvider
				.sampleStaticAnchoredConfigurationFromWorldKinematics(
						"apDrone",
						"rotor_oblique_momentum_power_balance",
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

	private record ReferenceSample(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional,
			RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample powerBalance
	) {
	}
}
