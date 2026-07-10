package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorHoverBladeProfilePowerModelTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double UIUC_MICRO_INVENT_DIAMETER_METERS = 0.127;
	private static final double UIUC_MICRO_INVENT_RPM = 3_988.889;
	private static final double UIUC_MICRO_INVENT_CT = 0.176045;
	private static final double UIUC_MICRO_INVENT_CP = 0.112252;

	@Test
	void uiucStationGeometryProducesDeterministicPlanformAndDragIntegrals() {
		RotorHoverBladeProfilePowerModel.BladeGeometry da4052 =
				UiucFiveInchPropellerGeometry.da4052FiveByThreePointSevenFiveThreeBlade();
		RotorHoverBladeProfilePowerModel.BladeGeometry nr640 =
				UiucFiveInchPropellerGeometry.nr640FiveInchFifteenDegreeThreeBlade();
		RotorHoverBladeProfilePowerModel.BladeGeometry microInvent =
				UiucFiveInchPropellerGeometry.microInventFiveByFourThreeBlade();

		assertEquals(3, UiucFiveInchPropellerGeometry.threeBladeProfiles().size());
		assertEquals(18, da4052.stationCount());
		assertEquals(18, nr640.stationCount());
		assertEquals(18, microInvent.stationCount());
		assertEquals(3, da4052.bladeCount());
		assertEquals(0.15, da4052.rootCutoutFraction(), 0.0);
		assertEquals(0.0375169375, da4052.profileDragMomentIntegralDimensionless(), 1.0e-15);
		assertEquals(0.0255538675, nr640.profileDragMomentIntegralDimensionless(), 1.0e-15);
		assertEquals(0.0326608146875,
				microInvent.profileDragMomentIntegralDimensionless(), 1.0e-15);
		assertEquals(0.183819184622846, da4052.planformSolidity(), 1.0e-15);
		assertEquals(0.0981405083334709, nr640.planformSolidity(), 1.0e-15);
		assertEquals(0.126893440352453, microInvent.planformSolidity(), 1.0e-15);
		assertEquals(0.2068, da4052.chordToRadiusAt(0.70), 1.0e-15);
		assertEquals(20.912, Math.toDegrees(da4052.pitchAngleRadiansAt(0.70)), 1.0e-12);
		assertEquals(0.17165, microInvent.chordToRadiusAt(0.725), 1.0e-15);
		assertEquals(18.088, Math.toDegrees(microInvent.pitchAngleRadiansAt(0.725)), 1.0e-12);
		assertEquals(0.0, microInvent.chordToRadiusAt(0.10), 0.0);
		assertTrue(microInvent.sourceUrl().startsWith("https://m-selig.ae.illinois.edu/props/"));
	}

	@Test
	void profilePowerFollowsDensityOmegaCubedRadiusFifthAndTorqueUnits() {
		RotorHoverBladeProfilePowerModel.BladeGeometry geometry =
				UiucFiveInchPropellerGeometry.microInventFiveByFourThreeBlade();
		double radius = UIUC_MICRO_INVENT_DIAMETER_METERS * 0.5;
		double omega = UIUC_MICRO_INVENT_RPM * 2.0 * Math.PI / 60.0;
		double meanDragCoefficient = 0.08;
		RotorHoverBladeProfilePowerModel.ProfilePowerSample sample =
				RotorHoverBladeProfilePowerModel.sample(
						geometry, radius, RHO, omega, meanDragCoefficient);
		RotorHoverBladeProfilePowerModel.ProfilePowerSample dense =
				RotorHoverBladeProfilePowerModel.sample(
						geometry, radius, RHO * 2.0, omega, meanDragCoefficient);
		RotorHoverBladeProfilePowerModel.ProfilePowerSample fast =
				RotorHoverBladeProfilePowerModel.sample(
						geometry, radius, RHO, omega * 2.0, meanDragCoefficient);
		RotorHoverBladeProfilePowerModel.ProfilePowerSample large =
				RotorHoverBladeProfilePowerModel.sample(
						geometry, radius * 2.0, RHO, omega, meanDragCoefficient);

		double expectedPower = 0.5
				* RHO
				* geometry.bladeCount()
				* Math.pow(omega, 3.0)
				* Math.pow(radius, 5.0)
				* geometry.profileDragMomentIntegralDimensionless()
				* meanDragCoefficient;
		assertEquals(expectedPower, sample.profilePowerWatts(), 1.0e-15);
		assertEquals(sample.profilePowerWatts(),
				sample.profileTorqueNewtonMeters() * omega, 1.0e-15);
		assertEquals(geometry.rotorProfilePowerCoefficientPerMeanDragCoefficient()
					* meanDragCoefficient, sample.rotorProfilePowerCoefficient(), 1.0e-15);
		assertEquals(geometry.propellerProfilePowerCoefficientPerMeanDragCoefficient()
					* meanDragCoefficient, sample.propellerProfilePowerCoefficient(), 1.0e-15);
		assertEquals(sample.profilePowerWatts() * 2.0, dense.profilePowerWatts(), 1.0e-15);
		assertEquals(sample.profilePowerWatts() * 8.0, fast.profilePowerWatts(), 1.0e-14);
		assertEquals(sample.profileTorqueNewtonMeters() * 4.0,
				fast.profileTorqueNewtonMeters(), 1.0e-15);
		assertEquals(sample.profilePowerWatts() * 32.0, large.profilePowerWatts(), 1.0e-13);
		assertEquals(sample.totalBladePlanformAreaSquareMeters() * 4.0,
				large.totalBladePlanformAreaSquareMeters(), 1.0e-15);
		assertEquals(sample.propellerProfilePowerCoefficient(),
				dense.propellerProfilePowerCoefficient(), 1.0e-15);
		assertEquals(sample.propellerProfilePowerCoefficient(),
				fast.propellerProfilePowerCoefficient(), 1.0e-15);
		assertEquals(sample.propellerProfilePowerCoefficient(),
				large.propellerProfilePowerCoefficient(), 1.0e-15);
	}

	@Test
	void uiucMeasuredStaticCtCpSupportsExplicitKappaAndMeanCdSeparation() {
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample powerBalance =
				uiucMicroInventPowerBalance(Vec3.ZERO);
		RotorHoverBladeProfilePowerModel.BladeGeometry geometry =
				UiucFiveInchPropellerGeometry.microInventFiveByFourThreeBlade();
		double inducedPowerFactor = 1.10;
		RotorHoverBladeProfilePowerModel.HoverPowerSeparationSample separated =
				RotorHoverBladeProfilePowerModel.separate(
						powerBalance, geometry, inducedPowerFactor);
		RotorHoverBladeProfilePowerModel.HoverPowerSeparationSample minimumInduced =
				RotorHoverBladeProfilePowerModel.separate(powerBalance, geometry, 1.0);
		RotorHoverBladeProfilePowerModel.HoverPowerSeparationSample zeroProfile =
				RotorHoverBladeProfilePowerModel.separate(
						powerBalance,
						geometry,
						powerBalance.inducedPowerFactorUpperBound()
				);

		double expectedFigureOfMerit = Math.pow(UIUC_MICRO_INVENT_CT, 1.5)
				* Math.sqrt(2.0 / Math.PI)
				/ UIUC_MICRO_INVENT_CP;
		assertEquals(RotorObliqueMomentumPowerBalanceModel.Status.SOLVED_HOVER,
				powerBalance.status());
		assertEquals(expectedFigureOfMerit, powerBalance.hoverFigureOfMerit(), 1.0e-15);
		assertEquals(1.0 / expectedFigureOfMerit,
				powerBalance.inducedPowerFactorUpperBound(), 1.0e-15);
		assertEquals(RotorHoverBladeProfilePowerModel.SeparationStatus.SOLVED, separated.status());
		assertTrue(separated.separated());
		assertEquals(inducedPowerFactor * powerBalance.idealInducedPowerWatts(),
				separated.separatedInducedPowerWatts(), 1.0e-15);
		assertEquals(powerBalance.shaftPowerWatts() - separated.separatedInducedPowerWatts(),
				separated.profilePower().profilePowerWatts(), 1.0e-15);
		assertTrue(separated.profilePower().powerWeightedMeanSectionDragCoefficient() > 0.0);
		assertTrue(separated.profilePower().powerWeightedMeanSectionDragCoefficient()
				< minimumInduced.profilePower().powerWeightedMeanSectionDragCoefficient());
		assertEquals(0.0, zeroProfile.profilePower().profilePowerWatts(), 1.0e-9);
		assertEquals(0.0, separated.powerClosureResidualWatts(), 1.0e-15);
		assertEquals(0.0, separated.torqueClosureResidualNewtonMeters(), 1.0e-18);

		double revolutionsPerSecond = UIUC_MICRO_INVENT_RPM / 60.0;
		double propellerPowerDenominator = RHO
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(UIUC_MICRO_INVENT_DIAMETER_METERS, 5.0);
		double expectedProfileCp = separated.profilePower().profilePowerWatts()
				/ propellerPowerDenominator;
		assertEquals(expectedProfileCp,
				separated.profilePower().propellerProfilePowerCoefficient(), 1.0e-15);
	}

	@Test
	void separationBlocksImpossibleFactorNonHoverAndBladeCountMismatch() {
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample hover =
				uiucMicroInventPowerBalance(Vec3.ZERO);
		RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample crossflow =
				uiucMicroInventPowerBalance(new Vec3(2.0, 0.0, 0.0));
		RotorHoverBladeProfilePowerModel.BladeGeometry geometry =
				UiucFiveInchPropellerGeometry.microInventFiveByFourThreeBlade();
		RotorHoverBladeProfilePowerModel.BladeGeometry twoBlade =
				new RotorHoverBladeProfilePowerModel.BladeGeometry(
						"uiuc-microinvent-5x4-two-blade-mismatch",
						"UIUC MicroInvent 5x4 two-blade mismatch",
						geometry.sourceUrl(),
						2,
						geometry.stations()
				);

		RotorHoverBladeProfilePowerModel.HoverPowerSeparationSample impossible =
				RotorHoverBladeProfilePowerModel.separate(
						hover,
						geometry,
						hover.inducedPowerFactorUpperBound() + 0.01
				);
		RotorHoverBladeProfilePowerModel.HoverPowerSeparationSample notHover =
				RotorHoverBladeProfilePowerModel.separate(crossflow, geometry, 1.0);
		RotorHoverBladeProfilePowerModel.HoverPowerSeparationSample mismatch =
				RotorHoverBladeProfilePowerModel.separate(hover, twoBlade, 1.0);

		assertEquals(RotorHoverBladeProfilePowerModel.SeparationStatus
				.BLOCKED_INDUCED_POWER_EXCEEDS_SHAFT, impossible.status());
		assertTrue(impossible.blocked());
		assertTrue(impossible.signedAvailableProfilePowerWatts() < 0.0);
		assertEquals(RotorHoverBladeProfilePowerModel.SeparationStatus.BLOCKED_NOT_HOVER,
				notHover.status());
		assertEquals(RotorHoverBladeProfilePowerModel.SeparationStatus.BLOCKED_BLADE_COUNT_MISMATCH,
				mismatch.status());
		assertThrows(IllegalArgumentException.class, () ->
				RotorHoverBladeProfilePowerModel.separate(hover, geometry, 0.99));
	}

	private static RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample
			uiucMicroInventPowerBalance(Vec3 relativeAirVelocityBodyMetersPerSecond) {
		double radius = UIUC_MICRO_INVENT_DIAMETER_METERS * 0.5;
		double revolutionsPerSecond = UIUC_MICRO_INVENT_RPM / 60.0;
		double omega = revolutionsPerSecond * 2.0 * Math.PI;
		double thrust = UIUC_MICRO_INVENT_CT
				* RHO
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(UIUC_MICRO_INVENT_DIAMETER_METERS, 4.0);
		double shaftPower = UIUC_MICRO_INVENT_CP
				* RHO
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(UIUC_MICRO_INVENT_DIAMETER_METERS, 5.0);
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withRadiusMeters(radius)
				.withBladeCount(3);
		return RotorObliqueMomentumPowerBalanceModel.solve(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				thrust,
				shaftPower,
				omega,
				RHO
		);
	}
}
