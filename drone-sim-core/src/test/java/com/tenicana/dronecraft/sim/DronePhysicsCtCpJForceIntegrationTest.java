package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DronePhysicsCtCpJForceIntegrationTest {
	@Test
	void acceptedSampleAnchorsBaseThrustShaftTorqueAndMomentumInflow() {
		Fixture fixture = acceptedFixture();
		ApDroneCtCpJRuntimeWakeReference.Sample sample = fixture.sample();

		assertRawEquals(sample.thrustNewtons(), DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample,
				0.42,
				1.0,
				1.0
		));
		assertRawEquals(sample.thrustNewtons() * 0.72, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				sample,
				0.42,
				0.72,
				1.0
		));
		assertRawBits(0x3F8361124616C602L,
				DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
						sample,
						0.031,
						1.0,
						1.0,
						0.0,
						1.0,
						1.0
				));

		double density = ApDroneCtCpJRuntimeWakeReference.runtimeAirDensityKgPerCubicMeter(1.0);
		double diskArea = fixture.geometry().diskAreaSquareMeters();
		assertRawBits(0x3FFDB617E8723164L,
				DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
						sample,
						2.5,
						sample.thrustNewtons(),
						density,
						diskArea
				));
		assertRawBits(0x3FF6C7338B347F4CL,
				DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
						sample,
						2.5,
						sample.thrustNewtons() * 0.72,
						density,
						diskArea
				));
		double tinyPositiveThrust = 5.0e-10;
		double advanceSpeed = sample.axialAdvanceSpeedMetersPerSecond();
		double diskTerm = 2.0 * tinyPositiveThrust / (density * diskArea);
		double expectedTinyThrustInflow = 0.5 * (
				Math.sqrt(advanceSpeed * advanceSpeed + diskTerm) - advanceSpeed
		);
		assertTrue(expectedTinyThrustInflow > 0.0);
		assertRawEquals(expectedTinyThrustInflow,
				DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
						sample,
						2.5,
						tinyPositiveThrust,
						density,
						diskArea
				));
	}

	@Test
	void acceptedSampleUsesTransverseOnlyAirflowAndShapesItsOwnWake() {
		Fixture fixture = acceptedFixture();
		ApDroneCtCpJRuntimeWakeReference.Sample sample = fixture.sample();
		double fallbackAirflow = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				null,
				fixture.rotor(),
				fixture.axialFlow(),
				fixture.omegaRadiansPerSecond(),
				0.0
		);
		double referenceAirflow = DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
				sample,
				fixture.rotor(),
				fixture.axialFlow(),
				fixture.omegaRadiansPerSecond(),
				0.0
		);

		assertTrue(fallbackAirflow < 1.0);
		assertRawEquals(1.0, referenceAirflow);
		double fallbackWake = sample.idealInducedVelocityMetersPerSecond() * 0.85;
		assertRawBits(0x3FFEEFD84113103AL,
				DronePhysics.rotorCtCpJRuntimeTargetWakeVelocityMetersPerSecond(
						sample,
						fallbackWake,
						0.85
				));
		assertRawBits(0x3F907A6EDC6B1523L,
				DronePhysics.rotorCtCpJRuntimeWakeResidenceTimeSeconds(
						sample,
						fixture.rotor(),
						0.180
				));
	}

	@Test
	void acceptedSampleRemovesDensityBeforeApplyingEnvironmentalLoadScales() {
		double densityRatio = 0.85;
		double environmentalLoadScale = 0.72;
		double thrustScale = densityRatio * environmentalLoadScale;
		double nonDensityScale = thrustScale / densityRatio;
		Fixture fixture = acceptedFixture(densityRatio);
		ApDroneCtCpJRuntimeWakeReference.Sample sample = fixture.sample();

		assertRawEquals(
				sample.thrustNewtons() * nonDensityScale,
				DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
						sample,
						0.42,
						thrustScale,
						densityRatio
				)
		);
		double reactionScale = 1.1;
		double compressibilityScale = 0.9;
		double inPlaneTorque = 0.0003;
		double loadScale = reactionScale * compressibilityScale * nonDensityScale;
		assertRawEquals(
				sample.shaftTorqueNewtonMeters() * loadScale + inPlaneTorque,
				DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
						sample,
						0.031,
						reactionScale,
						compressibilityScale,
						inPlaneTorque,
						thrustScale,
						densityRatio
				)
		);
	}

	@Test
	void absentOrRejectedSamplePreservesEveryFallbackExactly() {
		Fixture fixture = acceptedFixture();
		ApDroneCtCpJRuntimeWakeReference.Sample rejected = new ApDroneCtCpJRuntimeWakeReference.Sample();
		double fallbackThrust = 0.42;
		double fallbackTorque = 0.031;
		double fallbackTarget = 2.5;
		double fallbackWake = 0.85;
		double fallbackResidence = 0.180;

		assertRawEquals(fallbackThrust, DronePhysics.rotorCtCpJRuntimeBaseThrustNewtons(
				rejected, fallbackThrust, 0.72, 0.85));
		assertRawEquals(fallbackTorque,
				DronePhysics.rotorCtCpJRuntimeRawAerodynamicTorqueNewtonMeters(
						rejected, fallbackTorque, 1.1, 0.9, 0.0003, 0.72, 0.85));
		assertRawEquals(fallbackTarget,
				DronePhysics.rotorCtCpJRuntimeTargetInducedVelocityMetersPerSecond(
						rejected,
						fallbackTarget,
						fixture.sample().thrustNewtons(),
						ApDroneCtCpJRuntimeWakeReference.runtimeAirDensityKgPerCubicMeter(1.0),
						fixture.geometry().diskAreaSquareMeters()
				));
		assertRawEquals(fallbackWake,
				DronePhysics.rotorCtCpJRuntimeTargetWakeVelocityMetersPerSecond(
						rejected, fallbackWake, 0.85));
		assertRawEquals(fallbackResidence,
				DronePhysics.rotorCtCpJRuntimeWakeResidenceTimeSeconds(
						rejected, fixture.rotor(), fallbackResidence));
		assertRawEquals(
				DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
						null,
						fixture.rotor(),
						fixture.axialFlow(),
						fixture.omegaRadiansPerSecond(),
						0.37
				),
				DronePhysics.rotorCtCpJRuntimeAirflowThrustMultiplier(
						rejected,
						fixture.rotor(),
						fixture.axialFlow(),
						fixture.omegaRadiansPerSecond(),
						0.37
				)
		);
	}

	private static Fixture acceptedFixture() {
		return acceptedFixture(1.0);
	}

	private static Fixture acceptedFixture(double densityRatio) {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		ApDroneCtCpJRuntimeWakeReference.RotorGeometry geometry =
				ApDroneCtCpJRuntimeWakeReference.geometry(rotor);
		ApDroneCtCpJRuntimeWakeReference.Sample sample =
				new ApDroneCtCpJRuntimeWakeReference.Sample();
		DronePhysics.AtmosphereCache atmosphere = new DronePhysics.AtmosphereCache();
		atmosphere.resolve(DroneEnvironment.calm());
		double rpm = 6_000.0;
		double omega = rpm * (2.0 * Math.PI) / 60.0;
		double axialSpeed = 0.4064 * (rpm / 60.0) * geometry.diameterMeters();
		Vec3 axialFlow = rotor.thrustAxisBody().multiply(axialSpeed);
		double density = ApDroneCtCpJRuntimeWakeReference.runtimeAirDensityKgPerCubicMeter(densityRatio);
		ApDroneCtCpJRuntimeWakeReference.sampleInto(
				geometry,
				rotor.thrustAxisBody(),
				axialFlow,
				omega,
				density,
				atmosphere.speedOfSoundMetersPerSecond(),
				ApDroneCtCpJRuntimeWakeReference.runtimeDensityViscosityRatio(
						density,
						atmosphere.dynamicViscosityRatio()
				),
				sample
		);
		assertTrue(sample.applied());
		return new Fixture(rotor, geometry, sample, omega, axialFlow);
	}

	private static void assertRawBits(long expectedBits, double actual) {
		assertEquals(expectedBits, Double.doubleToRawLongBits(actual));
	}

	private static void assertRawEquals(double expected, double actual) {
		assertRawBits(Double.doubleToRawLongBits(expected), actual);
	}

	private record Fixture(
			RotorSpec rotor,
			ApDroneCtCpJRuntimeWakeReference.RotorGeometry geometry,
			ApDroneCtCpJRuntimeWakeReference.Sample sample,
			double omegaRadiansPerSecond,
			Vec3 axialFlow
	) {
	}
}
