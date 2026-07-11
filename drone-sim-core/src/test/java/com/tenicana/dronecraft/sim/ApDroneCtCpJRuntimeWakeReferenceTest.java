package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApDroneCtCpJRuntimeWakeReferenceTest {
	@Test
	void onlyTheCompleteApDroneRotorSetEnablesTheCompactReference() {
		assertTrue(ApDroneCtCpJRuntimeWakeReference.hasEligibleRotorSet(DroneConfig.apDrone()));
		assertFalse(ApDroneCtCpJRuntimeWakeReference.hasEligibleRotorSet(DroneConfig.racingQuad()));
		assertFalse(ApDroneCtCpJRuntimeWakeReference.hasEligibleRotorSet(DroneConfig.hexLift()));
	}

	@Test
	void acceptedMidEnvelopeSampleProducesFiniteWakePrimitivesWithoutAllocatingAResult() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		ApDroneCtCpJRuntimeWakeReference.RotorGeometry geometry =
				ApDroneCtCpJRuntimeWakeReference.geometry(rotor);
		ApDroneCtCpJRuntimeWakeReference.Sample sample =
				new ApDroneCtCpJRuntimeWakeReference.Sample();
		DronePhysics.AtmosphereCache atmosphere = calmAtmosphere();
		double rpm = 6_000.0;
		double revolutionsPerSecond = rpm / 60.0;
		double advanceRatioJ = 0.4064;
		Vec3 relativeAir = new Vec3(
				0.0,
				advanceRatioJ * revolutionsPerSecond * geometry.diameterMeters(),
				0.0
		);

		ApDroneCtCpJRuntimeWakeReference.sampleInto(
				geometry,
				rotor.thrustAxisBody(),
				relativeAir,
				rpm * (2.0 * Math.PI) / 60.0,
				standardAirDensity(),
				atmosphere.speedOfSoundMetersPerSecond(),
				standardDensityViscosityRatio(atmosphere),
				sample
		);

		assertTrue(sample.applied());
		assertTrue(Double.isFinite(sample.thrustNewtons()) && sample.thrustNewtons() > 0.0);
		assertTrue(Double.isFinite(sample.diskLoadingNewtonsPerSquareMeter())
				&& sample.diskLoadingNewtonsPerSquareMeter() > 0.0);
		assertTrue(sample.diskLoadingStrength() > 0.0 && sample.diskLoadingStrength() <= 1.0);
		assertTrue(Double.isFinite(sample.idealInducedVelocityMetersPerSecond())
				&& sample.idealInducedVelocityMetersPerSecond() > 0.0);
		assertTrue(sample.farWakeAxialVelocityMetersPerSecond()
				> sample.idealInducedVelocityMetersPerSecond());
		assertTrue(sample.farWakeEquivalentRadiusMeters() > 0.0);
		assertTrue(sample.wakeTangentialVelocityMetersPerSecond() > 0.0);
		assertRawBits(0x3FDA027525460AA6L, sample.advanceRatioJ());
		assertRawBits(0x40150EDA8E566F78L, sample.axialAdvanceSpeedMetersPerSecond());
		assertRawBits(0x3FDB54139D1F4E98L, sample.thrustNewtons());
		assertRawBits(0x3F8361124616C602L, sample.shaftTorqueNewtonMeters());
		assertRawBits(0x3FE8DDDDDDDDDDDEL, sample.propellerThrustScale());
		assertRawBits(0x3FF44CCCCCCCCCCCL, sample.propellerPowerScale());
		assertRawBits(0x4040331F7F83D466L, sample.diskLoadingNewtonsPerSquareMeter());
		assertRawBits(0x3FA031D2BBECA05BL, sample.diskLoadingStrength());
		assertRawBits(0x3FFDB617E8723164L, sample.idealInducedVelocityMetersPerSecond());
		assertRawBits(0x4021F4F34147C415L, sample.farWakeAxialVelocityMetersPerSecond());
		assertRawBits(0x3FAD88D0122F39EBL, sample.farWakeEquivalentRadiusMeters());
		assertRawBits(0x40004E39F2C5CBC2L, sample.wakeTangentialVelocityMetersPerSecond());
	}

	@Test
	void rejectedSamplesClearTheReusableScratch() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		ApDroneCtCpJRuntimeWakeReference.RotorGeometry geometry =
				ApDroneCtCpJRuntimeWakeReference.geometry(rotor);
		ApDroneCtCpJRuntimeWakeReference.Sample sample =
				new ApDroneCtCpJRuntimeWakeReference.Sample();
		DronePhysics.AtmosphereCache atmosphere = calmAtmosphere();
		double rpm = 6_000.0;
		double omega = rpm * (2.0 * Math.PI) / 60.0;
		double revolutionsPerSecond = rpm / 60.0;
		Vec3 acceptedAir = new Vec3(
				0.0,
				0.4064 * revolutionsPerSecond * geometry.diameterMeters(),
				0.0
		);
		ApDroneCtCpJRuntimeWakeReference.sampleInto(
				geometry,
				rotor.thrustAxisBody(),
				acceptedAir,
				omega,
				standardAirDensity(),
				atmosphere.speedOfSoundMetersPerSecond(),
				standardDensityViscosityRatio(atmosphere),
				sample
		);
		assertTrue(sample.applied());

		ApDroneCtCpJRuntimeWakeReference.sampleInto(
				geometry,
				rotor.thrustAxisBody(),
				new Vec3(0.0, -1.0, 0.0),
				omega,
				standardAirDensity(),
				atmosphere.speedOfSoundMetersPerSecond(),
				standardDensityViscosityRatio(atmosphere),
				sample
		);

		assertFalse(sample.applied());
		assertEquals(0.0, sample.advanceRatioJ(), 0.0);
		assertEquals(0.0, sample.axialAdvanceSpeedMetersPerSecond(), 0.0);
		assertEquals(0.0, sample.thrustNewtons(), 0.0);
		assertEquals(0.0, sample.shaftTorqueNewtonMeters(), 0.0);
		assertEquals(0.0, sample.propellerThrustScale(), 0.0);
		assertEquals(0.0, sample.propellerPowerScale(), 0.0);
		assertEquals(0.0, sample.diskLoadingNewtonsPerSquareMeter(), 0.0);
		assertEquals(0.0, sample.diskLoadingStrength(), 0.0);
		assertEquals(0.0, sample.idealInducedVelocityMetersPerSecond(), 0.0);
		assertEquals(0.0, sample.farWakeAxialVelocityMetersPerSecond(), 0.0);
		assertEquals(0.0, sample.farWakeEquivalentRadiusMeters(), 0.0);
		assertEquals(0.0, sample.wakeTangentialVelocityMetersPerSecond(), 0.0);
	}

	@Test
	void runtimeAcceptanceRejectsLookupAndOperatingEnvelopeViolations() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		ApDroneCtCpJRuntimeWakeReference.RotorGeometry geometry =
				ApDroneCtCpJRuntimeWakeReference.geometry(rotor);
		ApDroneCtCpJRuntimeWakeReference.Sample sample =
				new ApDroneCtCpJRuntimeWakeReference.Sample();
		DronePhysics.AtmosphereCache atmosphere = calmAtmosphere();

		assertRejected(
				geometry,
				rotor,
				new Vec3(0.0, 0.90 * 100.0 * geometry.diameterMeters(), 0.0),
				6_000.0,
				atmosphere,
				sample
		);
		assertRejected(
				geometry,
				rotor,
				new Vec3(5.0, 1.0, 0.0),
				6_000.0,
				atmosphere,
				sample
		);
		assertRejected(
				geometry,
				rotor,
				Vec3.ZERO,
				500.0,
				atmosphere,
				sample
		);
		assertRejected(
				geometry,
				rotor,
				Vec3.ZERO,
				30_000.0,
				atmosphere,
				sample
		);
	}

	@Test
	void lookupEnvelopeKeepsTheAcceptedPayloadEpsilonSemantics() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		ApDroneCtCpJRuntimeWakeReference.RotorGeometry geometry =
				ApDroneCtCpJRuntimeWakeReference.geometry(rotor);
		ApDroneCtCpJRuntimeWakeReference.Sample sample =
				new ApDroneCtCpJRuntimeWakeReference.Sample();
		DronePhysics.AtmosphereCache atmosphere = calmAtmosphere();
		double rpm = 6_000.0;
		double revolutionsPerSecond = rpm / 60.0;
		double speedPerAdvanceRatio = revolutionsPerSecond * geometry.diameterMeters();

		ApDroneCtCpJRuntimeWakeReference.sampleInto(
				geometry,
				rotor.thrustAxisBody(),
				new Vec3(0.0, (0.8128 + 0.5e-9) * speedPerAdvanceRatio, 0.0),
				rpm * (2.0 * Math.PI) / 60.0,
				standardAirDensity(),
				atmosphere.speedOfSoundMetersPerSecond(),
				standardDensityViscosityRatio(atmosphere),
				sample
		);
		assertTrue(sample.applied());

		ApDroneCtCpJRuntimeWakeReference.sampleInto(
				geometry,
				rotor.thrustAxisBody(),
				new Vec3(0.0, (0.8128 + 2.0e-9) * speedPerAdvanceRatio, 0.0),
				rpm * (2.0 * Math.PI) / 60.0,
				standardAirDensity(),
				atmosphere.speedOfSoundMetersPerSecond(),
				standardDensityViscosityRatio(atmosphere),
				sample
		);
		assertFalse(sample.applied());
	}

	private static void assertRejected(
			ApDroneCtCpJRuntimeWakeReference.RotorGeometry geometry,
			RotorSpec rotor,
			Vec3 relativeAir,
			double rpm,
			DronePhysics.AtmosphereCache atmosphere,
			ApDroneCtCpJRuntimeWakeReference.Sample sample
	) {
		ApDroneCtCpJRuntimeWakeReference.sampleInto(
				geometry,
				rotor.thrustAxisBody(),
				relativeAir,
				rpm * (2.0 * Math.PI) / 60.0,
				standardAirDensity(),
				atmosphere.speedOfSoundMetersPerSecond(),
				standardDensityViscosityRatio(atmosphere),
				sample
		);
		assertFalse(sample.applied());
	}

	private static DronePhysics.AtmosphereCache calmAtmosphere() {
		DronePhysics.AtmosphereCache atmosphere = new DronePhysics.AtmosphereCache();
		atmosphere.resolve(DroneEnvironment.calm());
		return atmosphere;
	}

	private static double standardAirDensity() {
		return ApDroneCtCpJRuntimeWakeReference.runtimeAirDensityKgPerCubicMeter(1.0);
	}

	private static double standardDensityViscosityRatio(DronePhysics.AtmosphereCache atmosphere) {
		return ApDroneCtCpJRuntimeWakeReference.runtimeDensityViscosityRatio(
				standardAirDensity(),
				atmosphere.dynamicViscosityRatio()
		);
	}

	private static void assertRawBits(long expectedBits, double actual) {
		assertEquals(expectedBits, Double.doubleToRawLongBits(actual));
	}
}
