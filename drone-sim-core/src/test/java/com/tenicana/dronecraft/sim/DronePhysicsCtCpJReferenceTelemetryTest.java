package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DronePhysicsCtCpJReferenceTelemetryTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;

	@Test
	void apDroneMidDomainReferenceSampleCanBeStoredAsRuntimeTelemetry() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone", "static_anchored", rotor, reference.rpm(), RHO);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult archiveShape =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(reference);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult archiveStaticAnchor =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(
						PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
								"apDrone",
								"static_anchor_low_rpm",
								rotor.radiusMeters() * 2.0,
								RHO
						));
		double expectedCt = staticSample.thrustCoefficientCt()
				* archiveShape.thrustCoefficientCt()
				/ archiveStaticAnchor.thrustCoefficientCt();
		double expectedCp = staticSample.powerCoefficientCp()
				* archiveShape.powerCoefficientCp()
				/ archiveStaticAnchor.powerCoefficientCp();

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(reference.advanceRatioJ(), sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(reference.rpm(), sample.lookup().effectiveRpm(), 1.0e-9);
		assertEquals(expectedCt, sample.lookup().thrustCoefficientCt(), 1.0e-15);
		assertEquals(expectedCp, sample.lookup().powerCoefficientCp(), 1.0e-15);
		assertEquals(expectedThrust(sample.lookup(), rotor.radiusMeters() * 2.0, RHO),
				sample.thrustNewtons(), 1.0e-15);
		assertEquals(expectedShaftPower(sample.lookup(), rotor.radiusMeters() * 2.0, RHO),
				sample.shaftPowerWatts(), 1.0e-15);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);

		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceClamped(0));
		assertTrue(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				state.rotorCtCpJReferenceInterpolationStatus(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.INTERPOLATED,
				state.rotorCtCpJReferenceLookupStatusCode(0));
		assertEquals(sample.lookup().effectiveAdvanceRatioJ(), state.rotorCtCpJReferenceAdvanceRatioJ(0), 1.0e-12);
		assertEquals(sample.lookup().effectiveRpm(), state.rotorCtCpJReferenceRpm(0), 1.0e-9);
		assertEquals(sample.lookup().thrustCoefficientCt(), state.rotorCtCpJReferenceThrustCoefficientCt(0), 1.0e-12);
		assertEquals(sample.lookup().powerCoefficientCp(), state.rotorCtCpJReferencePowerCoefficientCp(0), 1.0e-12);
		assertEquals(sample.lookup().propulsiveEfficiencyEta(), state.rotorCtCpJReferenceEfficiencyEta(0), 1.0e-12);
		assertVectorEquals(sample.relativeAirVelocityBodyMetersPerSecond(),
				state.rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond(0), 1.0e-15);
		assertVectorEquals(sample.transverseAirVelocityBodyMetersPerSecond(),
				state.rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond(0), 1.0e-15);
		assertEquals(sample.transverseAirSpeedMetersPerSecond(),
				state.rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond(0), 1.0e-15);
		assertEquals(sample.inflowAngleRadians(), state.rotorCtCpJReferenceInflowAngleRadians(0), 1.0e-15);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint operatingPoint =
				sample.standardOperatingPoint();
		assertEquals(operatingPoint.tipMach(), state.rotorCtCpJReferenceTipMach(0), 1.0e-15);
		assertEquals(operatingPoint.reynoldsNumber(), state.rotorCtCpJReferenceReynoldsNumber(0), 1.0e-9);
		assertEquals(operatingPoint.reynoldsIndex(), state.rotorCtCpJReferenceReynoldsIndex(0), 1.0e-15);
		assertEquals(sample.thrustNewtons(), state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);
		assertEquals(sample.shaftPowerWatts(), state.rotorCtCpJReferenceShaftPowerWatts(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().diskLoadingNewtonsPerSquareMeter(),
				state.rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().idealInducedVelocityMetersPerSecond(),
				state.rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().idealMomentumPowerWatts(),
				state.rotorCtCpJReferenceIdealMomentumPowerWatts(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().idealMomentumPowerOverShaftPower(),
				state.rotorCtCpJReferenceIdealMomentumPowerOverShaftPower(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().shaftPowerResidualWatts(),
				state.rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().shaftPowerResidualFraction(),
				state.rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().wakeSwirlKineticPowerWatts(),
				state.rotorCtCpJReferenceWakeSwirlKineticPowerWatts(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerWatts(),
				state.rotorCtCpJReferenceTotalWakeKineticPowerWatts(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerOverShaftPower(),
				state.rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().wakeSwirlKineticPowerOverShaftPower(),
				state.rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerResidualWatts(),
				state.rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts(0), 1.0e-15);
		assertEquals(sample.dimensionalSample().totalWakeKineticPowerResidualFraction(),
				state.rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction(0), 1.0e-15);
		assertEquals(sample.shaftTorqueNewtonMeters(), state.rotorCtCpJReferenceShaftTorqueNewtonMeters(0), 1.0e-18);
		assertVectorEquals(sample.thrustForceBodyNewtons(),
				state.rotorCtCpJReferenceThrustForceBodyNewtons(0), 1.0e-15);
		assertVectorEquals(sample.reactionTorqueBodyNewtonMeters(),
				state.rotorCtCpJReferenceReactionTorqueBodyNewtonMeters(0), 1.0e-18);
		assertVectorEquals(sample.thrustMomentBodyNewtonMeters(),
				state.rotorCtCpJReferenceThrustMomentBodyNewtonMeters(0), 1.0e-18);
		assertVectorEquals(sample.totalTorqueBodyNewtonMeters(),
				state.rotorCtCpJReferenceTotalTorqueBodyNewtonMeters(0), 1.0e-18);
		assertVectorEquals(sample.totalTorqueBodyNewtonMeters(),
				state.rotorCtCpJReferenceTotalTorqueBodyNewtonMeters()[0], 1.0e-18);

		double torqueDelta = 0.0002;
		state.setRotorThrustNewtons(0, sample.thrustNewtons() + 0.0125);
		state.setMotorOmegaRadiansPerSecond(0, sample.dimensionalSample().angularVelocityRadiansPerSecond());
		state.setMotorShaftPowerWatts(0, sample.shaftPowerWatts() + 12.0);
		state.setMotorAerodynamicTorqueNewtonMeters(0, sample.shaftTorqueNewtonMeters() + torqueDelta);
		state.updateRotorCtCpJReferenceResidual(0);
		assertEquals(0.0125, state.rotorCtCpJReferenceThrustResidualNewtons(0), 1.0e-15);
		assertEquals(
				torqueDelta * sample.dimensionalSample().angularVelocityRadiansPerSecond(),
				state.rotorCtCpJReferenceShaftPowerResidualWatts(0),
				1.0e-12
		);
		assertEquals(torqueDelta, state.rotorCtCpJReferenceShaftTorqueResidualNewtonMeters(0), 1.0e-15);
		assertEquals((sample.thrustNewtons() + 0.0125) / sample.thrustNewtons(),
				state.rotorCtCpJReferenceThrustRatio(0), 1.0e-15);
		assertEquals((sample.shaftTorqueNewtonMeters() + torqueDelta) / sample.shaftTorqueNewtonMeters(),
				state.rotorCtCpJReferenceShaftTorqueRatio(0), 1.0e-15);

		state.resetMotors();
		assertFalse(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertEquals(0.0, state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceRpm(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceIdealMomentumPowerWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceIdealMomentumPowerOverShaftPower(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceWakeSwirlKineticPowerWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceTotalWakeKineticPowerWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction(0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, state.rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond(0), 1.0e-18);
		assertVectorEquals(Vec3.ZERO, state.rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond(0), 1.0e-18);
		assertEquals(0.0, state.rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceInflowAngleRadians(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceTipMach(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceReynoldsNumber(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceReynoldsIndex(0), 1.0e-15);
		assertVectorEquals(Vec3.ZERO, state.rotorCtCpJReferenceThrustForceBodyNewtons(0), 1.0e-18);
		assertVectorEquals(Vec3.ZERO, state.rotorCtCpJReferenceReactionTorqueBodyNewtonMeters(0), 1.0e-18);
		assertVectorEquals(Vec3.ZERO, state.rotorCtCpJReferenceThrustMomentBodyNewtonMeters(0), 1.0e-18);
		assertVectorEquals(Vec3.ZERO, state.rotorCtCpJReferenceTotalTorqueBodyNewtonMeters(0), 1.0e-18);
		assertEquals(0.0, state.rotorCtCpJReferenceThrustResidualNewtons(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftPowerResidualWatts(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftTorqueResidualNewtonMeters(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceThrustRatio(0), 1.0e-15);
		assertEquals(0.0, state.rotorCtCpJReferenceShaftTorqueRatio(0), 1.0e-15);
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED,
				state.rotorCtCpJReferenceInterpolationStatus(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.UNKNOWN,
				state.rotorCtCpJReferenceLookupStatusCode(0));
	}

	@Test
	void referenceOperatingPointTelemetryUsesAmbientTemperature() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		DroneState standardState = new DroneState(1);
		DroneState hotState = new DroneState(1);
		standardState.setRotorCtCpJReferenceSample(0, sample);
		hotState.setRotorCtCpJReferenceSample(0, sample, 55.0);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint standard =
				sample.standardOperatingPoint();
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint hot =
				sample.operatingPoint(55.0);

		assertEquals(standard.tipMach(), standardState.rotorCtCpJReferenceTipMach(0), 1.0e-15);
		assertEquals(standard.reynoldsNumber(), standardState.rotorCtCpJReferenceReynoldsNumber(0), 1.0e-9);
		assertEquals(standard.reynoldsIndex(), standardState.rotorCtCpJReferenceReynoldsIndex(0), 1.0e-15);
		assertEquals(hot.tipMach(), hotState.rotorCtCpJReferenceTipMach(0), 1.0e-15);
		assertEquals(hot.reynoldsNumber(), hotState.rotorCtCpJReferenceReynoldsNumber(0), 1.0e-9);
		assertEquals(hot.reynoldsIndex(), hotState.rotorCtCpJReferenceReynoldsIndex(0), 1.0e-15);
		assertTrue(hotState.rotorCtCpJReferenceTipMach(0) < standardState.rotorCtCpJReferenceTipMach(0));
		assertTrue(hotState.rotorCtCpJReferenceReynoldsNumber(0)
				< standardState.rotorCtCpJReferenceReynoldsNumber(0));
		assertTrue(hotState.rotorCtCpJReferenceReynoldsIndex(0)
				< standardState.rotorCtCpJReferenceReynoldsIndex(0));
	}

	@Test
	void referenceOperatingPointTelemetryUsesAmbientHumidity() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		DroneState dryState = new DroneState(1);
		DroneState humidState = new DroneState(1);
		dryState.setRotorCtCpJReferenceSample(0, sample, 55.0, 0.0);
		humidState.setRotorCtCpJReferenceSample(0, sample, 55.0, 1.0);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint dry =
				sample.operatingPoint(55.0, 0.0);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint humid =
				sample.operatingPoint(55.0, 1.0);

		assertEquals(0.0, dry.ambientHumidity(), 1.0e-15);
		assertEquals(1.0, humid.ambientHumidity(), 1.0e-15);
		assertTrue(humid.speedOfSoundMetersPerSecond() > dry.speedOfSoundMetersPerSecond());
		assertTrue(humid.tipMach() < dry.tipMach());
		assertTrue(humid.dynamicViscosityPascalSeconds() < dry.dynamicViscosityPascalSeconds());
		assertTrue(humid.reynoldsNumber() > dry.reynoldsNumber());
		assertTrue(humid.reynoldsIndex() > dry.reynoldsIndex());
		assertEquals(humid.speedOfSoundMetersPerSecond(),
				humidState.rotorCtCpJReferenceSpeedOfSoundMetersPerSecond(0), 1.0e-12);
		assertEquals(humid.dynamicViscosityPascalSeconds(),
				humidState.rotorCtCpJReferenceDynamicViscosityPascalSeconds(0), 1.0e-15);
		assertEquals(humid.tipMach(), humidState.rotorCtCpJReferenceTipMach(0), 1.0e-15);
		assertEquals(humid.reynoldsNumber(), humidState.rotorCtCpJReferenceReynoldsNumber(0), 1.0e-9);
		assertEquals(humid.reynoldsIndex(), humidState.rotorCtCpJReferenceReynoldsIndex(0), 1.0e-15);
		assertEquals(humid.runtimeTipMachMargin(),
				humidState.rotorCtCpJReferenceTipMachRuntimeMargin(0), 1.0e-15);
		assertEquals(humid.runtimeReynoldsIndexMargin(),
				humidState.rotorCtCpJReferenceReynoldsIndexRuntimeMargin(0), 1.0e-15);
		assertEquals(humid.runtimeOperatingEnvelopeMarginFraction(),
				humidState.rotorCtCpJReferenceOperatingEnvelopeMarginFraction(0), 1.0e-15);
		assertTrue(humidState.rotorCtCpJReferenceSpeedOfSoundMetersPerSecond(0)
				> dryState.rotorCtCpJReferenceSpeedOfSoundMetersPerSecond(0));
		assertTrue(humidState.rotorCtCpJReferenceDynamicViscosityPascalSeconds(0)
				< dryState.rotorCtCpJReferenceDynamicViscosityPascalSeconds(0));
		assertTrue(humidState.rotorCtCpJReferenceReynoldsNumber(0)
				> dryState.rotorCtCpJReferenceReynoldsNumber(0));
		assertTrue(humidState.rotorCtCpJReferenceReynoldsIndex(0)
				> dryState.rotorCtCpJReferenceReynoldsIndex(0));
	}

	@Test
	void runtimeReferenceOperatingPointTelemetryFollowsEnvironmentTemperature() {
		DronePhysics cold = new DronePhysics(DroneConfig.apDrone());
		DronePhysics hot = new DronePhysics(DroneConfig.apDrone());
		DroneInput input = new DroneInput(0.62, 0.0, 0.0, 0.0, true, FlightMode.ACRO);

		for (int i = 0; i < 16; i++) {
			cold.step(input, 0.010, stillAirAtTemperature(-20.0));
			hot.step(input, 0.010, stillAirAtTemperature(55.0));
		}

		assertTrue(cold.state().rotorCtCpJReferenceAvailable(0)
				|| cold.state().rotorCtCpJReferenceBlocked(0));
		assertTrue(hot.state().rotorCtCpJReferenceAvailable(0)
				|| hot.state().rotorCtCpJReferenceBlocked(0));
		assertTrue(cold.state().rotorCtCpJReferenceTipMach(0) > 0.0);
		assertTrue(hot.state().rotorCtCpJReferenceTipMach(0) > 0.0);
		assertTrue(hot.state().rotorCtCpJReferenceTipMach(0)
				< cold.state().rotorCtCpJReferenceTipMach(0));
		assertTrue(hot.state().rotorCtCpJReferenceReynoldsNumber(0)
				< cold.state().rotorCtCpJReferenceReynoldsNumber(0));
		assertTrue(hot.state().rotorCtCpJReferenceReynoldsIndex(0)
				< cold.state().rotorCtCpJReferenceReynoldsIndex(0));
	}

	@Test
	void runtimeReferenceOperatingPointTelemetryFollowsEnvironmentHumidity() {
		DronePhysics dry = new DronePhysics(DroneConfig.apDrone());
		DronePhysics humid = new DronePhysics(DroneConfig.apDrone());
		DroneInput input = new DroneInput(0.62, 0.0, 0.0, 0.0, true, FlightMode.ACRO);

		for (int i = 0; i < 16; i++) {
			dry.step(input, 0.010, stillAirAtTemperatureAndHumidity(55.0, 0.0));
			humid.step(input, 0.010, stillAirAtTemperatureAndHumidity(55.0, 1.0));
		}

		assertTrue(dry.state().rotorCtCpJReferenceAvailable(0)
				|| dry.state().rotorCtCpJReferenceBlocked(0));
		assertTrue(humid.state().rotorCtCpJReferenceAvailable(0)
				|| humid.state().rotorCtCpJReferenceBlocked(0));
		assertTrue(dry.state().rotorCtCpJReferenceReynoldsNumber(0) > 0.0);
		assertTrue(humid.state().rotorCtCpJReferenceReynoldsNumber(0) > 0.0);
		assertTrue(Math.abs(humid.state().rotorCtCpJReferenceReynoldsNumber(0)
				- dry.state().rotorCtCpJReferenceReynoldsNumber(0)) > 1.0);
		assertTrue(Math.abs(humid.state().rotorCtCpJReferenceReynoldsIndex(0)
				- dry.state().rotorCtCpJReferenceReynoldsIndex(0)) > 1.0e-4);
	}

	@Test
	void runtimeReferenceOperatingEnvelopeRejectsLowReynoldsReplacement() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(
						rotor,
						Vec3.ZERO,
						hoverOmega,
						0.20,
						config.centerOfMassOffsetBodyMeters(),
						65.0,
						0.0
				);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.momentumPowerClosureSatisfied());
		assertTrue(sample.runtimeInflowEnvelopeSatisfied());
		assertFalse(sample.runtimeOperatingPointEnvelopeSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertTrue(sample.operatingPoint(65.0, 0.0).reynoldsIndex() < 0.52);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample, 65.0, 0.0);
		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertEquals(sample.operatingPoint(65.0, 0.0).reynoldsIndex(),
				state.rotorCtCpJReferenceReynoldsIndex(0), 1.0e-15);
		assertTrue(state.rotorCtCpJReferenceTipMachRuntimeMargin(0) > 0.0);
		assertTrue(state.rotorCtCpJReferenceReynoldsIndexRuntimeMargin(0) < 0.0);
		assertTrue(state.rotorCtCpJReferenceOperatingEnvelopeMarginFraction(0) < 0.0);

		double fallbackThrust = 0.42;
		double fallbackTorque = 0.031;
		assertEquals(fallbackThrust, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 1.0, 0.20), 1.0e-15);
		assertEquals(fallbackTorque, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, fallbackTorque, 1.0, 1.0, 0.0002, 1.0, 0.20), 1.0e-15);
	}

	@Test
	void unsupportedRotorGeometryDoesNotUseApDroneReferencePayload() {
		RotorSpec racingRotor = DroneConfig.racingQuad().rotors().get(0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(
						racingRotor,
						racingRotor.thrustAxisBody().multiply(5.0),
						4_500.0,
						1.0
				);

		assertNull(sample);
	}

	@Test
	void referenceSamplerPreservesTransverseRelativeAirVelocityDiagnostics() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double rpm = 6_000.0;
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double axialSpeed = 2.0;
		Vec3 transverseVelocity = new Vec3(1.5, 0.0, -2.0);
		Vec3 relativeAirVelocity = rotor.thrustAxisBody().multiply(axialSpeed).add(transverseVelocity);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, relativeAirVelocity, omega, 1.0);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertEquals(axialSpeed / (rpm / 60.0 * rotor.radiusMeters() * 2.0),
				sample.query().advanceRatioJ(), 1.0e-12);
		assertVectorEquals(relativeAirVelocity, sample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertVectorEquals(transverseVelocity, sample.transverseAirVelocityBodyMetersPerSecond(), 1.0e-15);
		assertEquals(2.5, sample.transverseAirSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(Math.atan2(2.5, axialSpeed), sample.inflowAngleRadians(), 1.0e-15);
		assertFalse(sample.runtimeInflowEnvelopeSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertVectorEquals(rotor.thrustAxisBody().multiply(sample.thrustNewtons()),
				sample.thrustForceBodyNewtons(), 1.0e-15);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);
		assertFalse(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertVectorEquals(relativeAirVelocity,
				state.rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond(0), 1.0e-15);
		assertVectorEquals(transverseVelocity,
				state.rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond(0), 1.0e-15);
		assertEquals(2.5, state.rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond(0), 1.0e-15);
		assertEquals(Math.atan2(2.5, axialSpeed), state.rotorCtCpJReferenceInflowAngleRadians(0), 1.0e-15);
	}

	@Test
	void referenceSamplerReportsBodyTorqueAboutRequestedMomentReference() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		Vec3 momentReference = new Vec3(0.015, -0.003, -0.020);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, Vec3.ZERO, hoverOmega, 1.0, momentReference);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		Vec3 expectedArm = rotor.positionBodyMeters().subtract(momentReference);
		Vec3 expectedThrustMoment = expectedArm.cross(sample.thrustForceBodyNewtons());
		Vec3 expectedTotalTorque = expectedThrustMoment.add(sample.reactionTorqueBodyNewtonMeters());
		assertVectorEquals(expectedArm, sample.momentArmBodyMeters(), 1.0e-15);
		assertVectorEquals(expectedThrustMoment, sample.thrustMomentBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(expectedTotalTorque, sample.totalTorqueBodyNewtonMeters(), 1.0e-15);
	}

	@Test
	void acceptedReferenceSampleAnchorsRuntimeBaseThrustAndTorque() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		double fallbackThrust = rotor.thrustCoefficient() * omega * omega;
		assertEquals(sample.thrustNewtons(), DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 1.0, 1.0), 1.0e-15);
		assertEquals(sample.thrustNewtons() * 0.72, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 0.72, 1.0), 1.0e-15);

		double fallbackTorque = rotor.yawTorquePerThrustMeter() * fallbackThrust;
		assertEquals(sample.shaftTorqueNewtonMeters() * 0.99 + 0.0003,
				DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
						sample,
						fallbackTorque,
						1.1,
						0.9,
						0.0003,
						1.0,
						1.0
				),
				1.0e-18);
		assertEquals(sample.shaftTorqueNewtonMeters() * 0.99 * 0.72 + 0.0003,
				DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
						sample,
						fallbackTorque,
						1.1,
						0.9,
						0.0003,
						0.72,
						1.0
				),
				1.0e-18);
	}

	@Test
	void acceptedReferenceSampleAnchorsRuntimePropellerScales() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone",
				"static_anchored_runtime_power_scale",
				rotor,
				reference.rpm(),
				RHO
		);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals(
				sample.lookup().effectiveAdvanceRatioJ(),
				DronePhysics.rotorCtCpJRuntimePropellerAdvanceRatioJ(sample, 0.42),
				1.0e-15
		);
		assertEquals(
				sample.lookup().thrustCoefficientCt() / staticSample.thrustCoefficientCt(),
				DronePhysics.rotorCtCpJRuntimePropellerThrustScale(sample, 0.42),
				1.0e-15
		);
		assertEquals(
				sample.lookup().powerCoefficientCp() / staticSample.powerCoefficientCp(),
				DronePhysics.rotorCtCpJRuntimePropellerPowerScale(sample, 0.42),
				1.0e-15
		);
		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimePropellerAdvanceRatioJ(null, 0.42), 1.0e-15);
		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimePropellerThrustScale(null, 0.42), 1.0e-15);
		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimePropellerPowerScale(null, 0.42), 1.0e-15);
	}

	@Test
	void acceptedRuntimeReferenceAirflowScaleDoesNotDuplicateAxialAdvanceLoss() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);
		double fallbackScale = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				null,
				rotor,
				axialFlow,
				omega,
				0.0
		);
		double referenceScale = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				sample,
				rotor,
				axialFlow,
				omega,
				0.0
		);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertTrue(fallbackScale < 1.0);
		assertEquals(1.0, referenceScale, 1.0e-15);
	}

	@Test
	void obliqueRuntimeReferenceAirflowScaleKeepsLegacyTransverseDiskFlowFallback() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double rpm = 6_000.0;
		double omega = rpm * 2.0 * Math.PI / 60.0;
		double advanceRatio = 0.018;
		double transverseSpeed = rotor.radiusMeters() * omega * advanceRatio;
		Vec3 transverseFlow = new Vec3(transverseSpeed, 0.0, 0.0);
		double translationalLiftIntensity = 0.4;

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, transverseFlow, omega, 1.0);
		double scale = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				sample,
				rotor,
				transverseFlow,
				omega,
				translationalLiftIntensity
		);
		double fallbackScale = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				null,
				rotor,
				transverseFlow,
				omega,
				translationalLiftIntensity
		);
		double expectedTransverseLift = 1.0 + rotor.transverseFlowLiftCoefficient() * MathUtil.clamp(
				0.35 * (advanceRatio / 0.18) + 0.65 * translationalLiftIntensity,
				0.0,
				1.0
		);

		assertNotNull(sample);
		assertFalse(sample.runtimeInflowEnvelopeSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals(expectedTransverseLift, scale, 1.0e-15);
		assertEquals(fallbackScale, scale, 1.0e-15);
		assertTrue(scale > 1.0);
	}

	@Test
	void clampedRuntimeReferenceAirflowScaleKeepsLegacyFallback() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery highReference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"high_domain_max_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double queryJ = highReference.advanceRatioJ() + 0.20;
		double omega = highReference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(queryJ
				* highReference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);
		double fallbackScale = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				null,
				rotor,
				axialFlow,
				omega,
				0.0
		);
		double clampedScale = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				sample,
				rotor,
				axialFlow,
				omega,
				0.0
		);

		assertNotNull(sample);
		assertTrue(sample.clamped());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals(fallbackScale, clampedScale, 1.0e-15);
	}

	@Test
	void acceptedReferenceSampleAnchorsRuntimeInducedInflowTarget() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery reference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"mid_domain_mid_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double omega = reference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(reference.advanceRatioJ()
				* reference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		double fallbackTarget = DronePhysics.targetRotorInducedVelocityMetersPerSecond(
				rotor, sample.thrustNewtons(), 1.0);
		assertEquals(
				sample.dimensionalSample().idealInducedVelocityMetersPerSecond(),
				DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
						sample, fallbackTarget, sample.thrustNewtons()),
				1.0e-15
		);
		assertTrue(sample.dimensionalSample().idealInducedVelocityMetersPerSecond() < fallbackTarget);

		double scaledThrust = sample.thrustNewtons() * 0.72;
		assertEquals(
				DronePhysics.axialMomentumInducedVelocityMetersPerSecond(
						scaledThrust,
						sample.dimensionalSample().airDensityKgPerCubicMeter(),
						sample.dimensionalSample().diskAreaSquareMeters(),
						sample.dimensionalSample().axialAdvanceSpeedMetersPerSecond()
				),
				DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
						sample, fallbackTarget, scaledThrust),
				1.0e-15
		);
	}

	@Test
	void acceptedRuntimeReferenceThrustForceUsesCtCpJBodyVector() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withThrustAxisBody(new Vec3(0.18, 0.96, -0.08));
		double hoverOmega = Math.sqrt(1.15 / rotor.thrustCoefficient());

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, Vec3.ZERO, hoverOmega, 1.0);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		double finalThrust = sample.thrustNewtons() * 0.63;
		Vec3 force = DronePhysics.rotorCtCpJRuntimeThrustAxisForceBody(sample, rotor, finalThrust);
		assertVectorEquals(sample.thrustForceBodyNewtons().multiply(0.63), force, 1.0e-15);
		assertEquals(finalThrust, force.length(), 1.0e-15);
	}

	@Test
	void acceptedRuntimeReferenceThrustForceCanPreserveNonCollinearCtCpJBodyVector() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double hoverOmega = Math.sqrt(1.15 / rotor.thrustCoefficient());
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, Vec3.ZERO, hoverOmega, 1.0);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		Vec3 nonCollinearForce = sample.thrustForceBodyNewtons().add(new Vec3(0.018, 0.0, -0.011));
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample nonCollinearSample =
				new PropellerArchiveCtCpJRotorForceModel.RotorForceSample(
						sample.query(),
						sample.lookup(),
						sample.dimensionalSample(),
						sample.axialAdvanceSpeedMetersPerSecond(),
						sample.relativeAirVelocityBodyMetersPerSecond(),
						sample.transverseAirVelocityBodyMetersPerSecond(),
						sample.transverseAirSpeedMetersPerSecond(),
						sample.inflowAngleRadians(),
						nonCollinearForce,
						sample.reactionTorqueBodyNewtonMeters(),
						sample.momentArmBodyMeters(),
						sample.thrustMomentBodyNewtonMeters(),
						sample.totalTorqueBodyNewtonMeters(),
						sample.yawTorquePerThrustMeterEquivalent()
				);

		double finalThrust = sample.thrustNewtons() * 0.41;
		Vec3 force = DronePhysics.rotorCtCpJRuntimeThrustAxisForceBody(nonCollinearSample, rotor, finalThrust);

		assertVectorEquals(nonCollinearForce.multiply(finalThrust / sample.thrustNewtons()), force, 1.0e-15);
	}

	@Test
	void runtimeReferenceThrustForceFallsBackWithoutAcceptedCtCpJVector() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withThrustAxisBody(new Vec3(-0.14, 0.97, 0.10));

		Vec3 force = DronePhysics.rotorCtCpJRuntimeThrustAxisForceBody(null, rotor, 1.75);

		assertVectorEquals(rotor.thrustAxisBody().multiply(1.75), force, 1.0e-15);
		assertEquals(1.75, force.length(), 1.0e-15);
	}

	@Test
	void acceptedRuntimeReferenceReactionTorqueKeepsCurrentRuntimeDiskAxisPathForCollinearModel() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withThrustAxisBody(new Vec3(0.11, 0.97, -0.06));
		double hoverOmega = Math.sqrt(1.15 / rotor.thrustCoefficient());
		Vec3 runtimeDiskAxis = new Vec3(0.0, 1.0, 0.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, Vec3.ZERO, hoverOmega, 1.0);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		double aerodynamicTorque = sample.shaftTorqueNewtonMeters() * 1.17;
		double rippleTorque = -0.00004;
		Vec3 reactionTorque = DronePhysics.rotorCtCpJRuntimeReactionTorqueBody(
				sample,
				rotor,
				runtimeDiskAxis,
				aerodynamicTorque,
				rippleTorque
		);

		assertVectorEquals(
				runtimeDiskAxis.multiply(rotor.spinDirection() * (aerodynamicTorque + rippleTorque)),
				reactionTorque,
				1.0e-18
		);
	}

	@Test
	void acceptedRuntimeReferenceReactionTorqueCanPreserveNonCollinearCtCpJVector() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		double hoverOmega = Math.sqrt(1.15 / rotor.thrustCoefficient());
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, Vec3.ZERO, hoverOmega, 1.0);

		assertNotNull(sample);
		assertTrue(sample.runtimeForceReplacementAccepted());
		Vec3 nonCollinearTorque = sample.reactionTorqueBodyNewtonMeters().add(new Vec3(0.000012, 0.0, -0.000007));
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample nonCollinearSample =
				new PropellerArchiveCtCpJRotorForceModel.RotorForceSample(
						sample.query(),
						sample.lookup(),
						sample.dimensionalSample(),
						sample.axialAdvanceSpeedMetersPerSecond(),
						sample.relativeAirVelocityBodyMetersPerSecond(),
						sample.transverseAirVelocityBodyMetersPerSecond(),
						sample.transverseAirSpeedMetersPerSecond(),
						sample.inflowAngleRadians(),
						sample.thrustForceBodyNewtons(),
						nonCollinearTorque,
						sample.momentArmBodyMeters(),
						sample.thrustMomentBodyNewtonMeters(),
						sample.totalTorqueBodyNewtonMeters(),
						sample.yawTorquePerThrustMeterEquivalent()
				);

		double aerodynamicTorque = sample.shaftTorqueNewtonMeters() * 0.52;
		double rippleTorque = 0.00003;
		Vec3 reactionTorque = DronePhysics.rotorCtCpJRuntimeReactionTorqueBody(
				nonCollinearSample,
				rotor,
				rotor.thrustAxisBody(),
				aerodynamicTorque,
				rippleTorque
		);

		Vec3 expectedAerodynamic = nonCollinearTorque.multiply(aerodynamicTorque / sample.shaftTorqueNewtonMeters());
		Vec3 expectedRipple = rotor.thrustAxisBody().multiply(rotor.spinDirection() * rippleTorque);
		assertVectorEquals(expectedAerodynamic.add(expectedRipple), reactionTorque, 1.0e-18);
	}

	@Test
	void runtimeReferenceReactionTorqueFallsBackWithoutAcceptedCtCpJVector() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0)
				.withThrustAxisBody(new Vec3(-0.06, 0.98, 0.12));

		Vec3 reactionTorque = DronePhysics.rotorCtCpJRuntimeReactionTorqueBody(
				null,
				rotor,
				rotor.thrustAxisBody(),
				0.0031,
				-0.0002
		);

		assertVectorEquals(
				rotor.thrustAxisBody().multiply(rotor.spinDirection() * (0.0031 - 0.0002)),
				reactionTorque,
				1.0e-18
		);
	}

	@Test
	void hoverRuntimeReferenceUsesStaticAnchorAndCanReplaceRuntimeForce() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		double hoverRpm = hoverOmega * 60.0 / (2.0 * Math.PI);
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				"apDrone", "static_anchored_hover", rotor, hoverRpm, RHO);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, Vec3.ZERO, hoverOmega, 1.0);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertFalse(sample.clamped());
		assertTrue(sample.runtimeForceReplacementAccepted());
		assertEquals("static_anchored_forward_shape", sample.lookup().caseName());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.INTERPOLATED,
				sample.lookup().lookupStatusCode());
		assertEquals(staticSample.thrustCoefficientCt(), sample.lookup().thrustCoefficientCt(), 1.0e-15);
		assertEquals(staticSample.powerCoefficientCp(), sample.lookup().powerCoefficientCp(), 1.0e-15);
		assertEquals(staticSample.thrustNewtons(), sample.thrustNewtons(), 1.0e-12);
		assertEquals(staticSample.shaftPowerWatts(), sample.shaftPowerWatts(), 1.0e-12);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);
		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertFalse(state.rotorCtCpJReferenceClamped(0));
		assertTrue(state.rotorCtCpJReferenceRuntimeApplied(0));

		double fallbackThrust = rotor.thrustCoefficient() * hoverOmega * hoverOmega;
		double fallbackTorque = rotor.yawTorquePerThrustMeter() * fallbackThrust;
		assertEquals(fallbackThrust, sample.thrustNewtons(), 1.0e-12);
		assertEquals(fallbackTorque, sample.shaftTorqueNewtonMeters(), 1.0e-15);
		assertEquals(fallbackThrust, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 1.0, 1.0), 1.0e-15);
		assertEquals(fallbackTorque, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, fallbackTorque, 1.0, 1.0, 0.0, 1.0, 1.0), 1.0e-15);
	}

	@Test
	void reverseAxialRuntimeReferenceClampsForTelemetryWithoutReplacingRuntimeForce() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double hoverOmega = Math.sqrt(
				(config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size())
						/ rotor.thrustCoefficient());
		Vec3 reverseAxialFlow = rotor.thrustAxisBody().multiply(-4.5);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, reverseAxialFlow, hoverOmega, 1.0);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertTrue(sample.clamped());
		assertTrue(sample.momentumPowerClosureSatisfied());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals("reverse_axial_static_anchor", sample.lookup().caseName());
		assertEquals("CLAMPED", sample.lookup().status());
		assertEquals("reverse-axial-flow-clamped-to-static-anchor", sample.lookup().message());
		assertEquals(0.0, sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.0, sample.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);

		double fallbackThrust = rotor.thrustCoefficient() * hoverOmega * hoverOmega;
		double fallbackTorque = rotor.yawTorquePerThrustMeter() * fallbackThrust;
		assertEquals(fallbackThrust, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, fallbackThrust, 1.0, 1.0), 1.0e-15);
		assertEquals(fallbackTorque, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, fallbackTorque, 1.0, 1.0, 0.0, 1.0, 1.0), 1.0e-15);
	}

	@Test
	void outOfEnvelopeRuntimeReferenceSampleClampsForTelemetryWithoutReplacingRuntimeForce() {
		RotorSpec rotor = DroneConfig.apDrone().rotors().get(0);
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery highReference =
				PropellerArchiveCtCpJLookupEvaluator.queryForReferenceCase(
						"apDrone",
						"high_domain_max_rpm",
						rotor.radiusMeters() * 2.0,
						RHO
				);
		double queryJ = highReference.advanceRatioJ() + 0.20;
		double omega = highReference.rpm() * 2.0 * Math.PI / 60.0;
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(queryJ
				* highReference.rpm()
				/ 60.0
				* rotor.radiusMeters()
				* 2.0);

		PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
				DronePhysics.sampleRotorCtCpJReference(rotor, axialFlow, omega, 1.0);

		assertNotNull(sample);
		assertFalse(sample.blocked());
		assertTrue(sample.clamped());
		assertFalse(sample.runtimeForceReplacementAccepted());
		assertEquals("CLAMPED", sample.lookup().status());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.CLAMPED_EXACT,
				sample.lookup().interpolationStatus());
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.CLAMPED,
				sample.lookup().lookupStatusCode());
		assertEquals(sample.lookup().upperAdvanceRatioJ(), sample.lookup().effectiveAdvanceRatioJ(), 1.0e-12);
		assertTrue(sample.thrustNewtons() > 0.0);
		assertTrue(sample.shaftPowerWatts() > 0.0);

		DroneState state = new DroneState(1);
		state.setRotorCtCpJReferenceSample(0, sample);
		assertTrue(state.rotorCtCpJReferenceAvailable(0));
		assertFalse(state.rotorCtCpJReferenceBlocked(0));
		assertTrue(state.rotorCtCpJReferenceClamped(0));
		assertFalse(state.rotorCtCpJReferenceRuntimeApplied(0));
		assertEquals(PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.CLAMPED,
				state.rotorCtCpJReferenceLookupStatusCode(0));
		assertEquals(sample.lookup().effectiveAdvanceRatioJ(), state.rotorCtCpJReferenceAdvanceRatioJ(0), 1.0e-12);
		assertEquals(highReference.rpm(), state.rotorCtCpJReferenceRpm(0), 1.0e-9);
		assertTrue(state.rotorCtCpJReferenceTipMach(0) > 0.0);
		assertTrue(state.rotorCtCpJReferenceReynoldsNumber(0) > 0.0);
		assertTrue(state.rotorCtCpJReferenceReynoldsIndex(0) > 0.0);
		assertEquals(sample.thrustNewtons(), state.rotorCtCpJReferenceThrustNewtons(0), 1.0e-15);

		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample, 0.42, 1.0, 1.0), 1.0e-15);
		assertEquals(0.031, DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
				sample, 0.031, 1.0, 1.0, 0.0002, 1.0, 1.0), 1.0e-15);
		assertEquals(2.5, DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
				sample, 2.5, sample.thrustNewtons()), 1.0e-15);
		assertEquals(2.5, DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
				null, 2.5, sample.thrustNewtons()), 1.0e-15);
		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimePropellerAdvanceRatioJ(sample, 0.42), 1.0e-15);
		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimePropellerThrustScale(sample, 0.42), 1.0e-15);
		assertEquals(0.42, DronePhysics.rotorCtCpJRuntimePropellerPowerScale(sample, 0.42), 1.0e-15);
	}

	private static double expectedThrust(
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			double diameter,
			double density
	) {
		double n = lookup.queryRpm() / 60.0;
		return lookup.thrustCoefficientCt() * density * n * n * Math.pow(diameter, 4.0);
	}

	private static double expectedShaftPower(
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			double diameter,
			double density
	) {
		double n = lookup.queryRpm() / 60.0;
		return lookup.powerCoefficientCp() * density * n * n * n * Math.pow(diameter, 5.0);
	}

	private static DroneEnvironment stillAirAtTemperature(double ambientTemperatureCelsius) {
		return stillAirAtTemperatureAndHumidity(ambientTemperatureCelsius, 0.0);
	}

	private static DroneEnvironment stillAirAtTemperatureAndHumidity(
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				ambientTemperatureCelsius,
				null,
				null,
				DroneEnvironment.WIND_SOURCE_ENVIRONMENT_OVERRIDE,
				true,
				1.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				DroneEnvironment.WIND_SOURCE_LEVEL_NONE,
				DroneEnvironment.WIND_SOURCE_AUTHORITY_NONE,
				-1L,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				true,
				ambientHumidity,
				0.0,
				0.0
		);
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}
}
