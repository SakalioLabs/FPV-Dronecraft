package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirframePressureCenterModelTest {
	@Test
	void pressureCenterTorqueUsesCenterOfMassReferencedCrossProductAndRotatesToWorld() {
		DroneConfig config = DroneConfig.apDrone()
				.withCenterOfMassOffsetBodyMeters(new Vec3(0.010, -0.020, 0.030))
				.withCenterOfPressureOffsetBodyMeters(new Vec3(-0.040, 0.050, -0.010));
		Vec3 forceBody = new Vec3(2.0, -3.0, -5.0);
		Vec3 dynamicOffset = new Vec3(0.005, -0.010, 0.020);

		AirframePressureCenterModel.AirframePressureCenterSample sample =
				AirframePressureCenterModel.sampleAtDynamicPressureCenter(
						config,
						forceBody,
						dynamicOffset
				);

		Vec3 expectedApplicationPoint = new Vec3(-0.035, 0.040, 0.010);
		Vec3 expectedMomentArm = new Vec3(-0.045, 0.060, -0.020);
		Vec3 expectedTorque = new Vec3(-0.360, -0.265, 0.015);
		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				Math.sin(Math.PI * 0.25),
				0.0
		);
		Vec3 angularVelocityBody = new Vec3(0.4, -0.7, 1.1);

		assertVectorEquals(expectedApplicationPoint, sample.forceApplicationPointBodyMeters(), 1.0e-15);
		assertVectorEquals(expectedMomentArm, sample.momentArmBodyMeters(), 1.0e-15);
		assertVectorEquals(expectedTorque, sample.pressureCenterTorqueBodyNewtonMeters(), 1.0e-15);
		assertVectorEquals(bodyToWorld.rotate(forceBody),
				sample.aerodynamicForceWorldNewtons(bodyToWorld), 1.0e-15);
		assertVectorEquals(bodyToWorld.rotate(expectedTorque),
				sample.pressureCenterTorqueWorldNewtonMeters(bodyToWorld), 1.0e-15);
		assertEquals(expectedTorque.dot(angularVelocityBody),
				sample.rotationalPowerWatts(angularVelocityBody), 1.0e-15);
		AirframePressureCenterModel.AirframePressureCenterSample deadbandSample =
				AirframePressureCenterModel.sampleAtDynamicPressureCenter(
						DroneConfig.apDrone()
								.withCenterOfPressureOffsetBodyMeters(new Vec3(5.0e-7, 0.0, 0.0)),
						new Vec3(0.0, 1.0, 0.0),
						Vec3.ZERO
				);
		assertVectorEquals(Vec3.ZERO, deadbandSample.pressureCenterTorqueBodyNewtonMeters(), 0.0);
	}

	@Test
	void steadyFlowMigrationHasExpectedAngleSymmetryAndNoStraightFlowBias() {
		Vec3 flow = new Vec3(8.0, 6.0, 12.0);
		Vec3 sideMirroredFlow = new Vec3(-8.0, 6.0, 12.0);
		Vec3 angleMirroredFlow = new Vec3(8.0, -6.0, 12.0);

		Vec3 offset = AirframePressureCenterModel.steadyDynamicPressureCenterOffsetBodyMeters(
				flow,
				0.75
		);
		Vec3 sideMirroredOffset = AirframePressureCenterModel.steadyDynamicPressureCenterOffsetBodyMeters(
				sideMirroredFlow,
				0.75
		);
		Vec3 angleMirroredOffset = AirframePressureCenterModel.steadyDynamicPressureCenterOffsetBodyMeters(
				angleMirroredFlow,
				0.75
		);

		assertTrue(offset.x() < 0.0);
		assertTrue(offset.y() > 0.0);
		assertTrue(offset.z() < 0.0);
		assertEquals(-offset.x(), sideMirroredOffset.x(), 1.0e-15);
		assertEquals(offset.y(), sideMirroredOffset.y(), 1.0e-15);
		assertEquals(offset.z(), sideMirroredOffset.z(), 1.0e-15);
		assertEquals(offset.x(), angleMirroredOffset.x(), 1.0e-15);
		assertEquals(-offset.y(), angleMirroredOffset.y(), 1.0e-15);
		assertEquals(offset.z(), angleMirroredOffset.z(), 1.0e-15);
		assertVectorEquals(Vec3.ZERO,
				AirframePressureCenterModel.steadyDynamicPressureCenterOffsetBodyMeters(
						new Vec3(0.0, 0.0, 18.0),
						1.0
				),
				1.0e-15
		);
		assertVectorEquals(Vec3.ZERO,
				AirframePressureCenterModel.steadyDynamicPressureCenterOffsetBodyMeters(
						new Vec3(1.0, 1.0, 0.0),
						1.0
				),
				1.0e-15
		);
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, "x");
		assertEquals(expected.y(), actual.y(), tolerance, "y");
		assertEquals(expected.z(), actual.z(), tolerance, "z");
	}
}
