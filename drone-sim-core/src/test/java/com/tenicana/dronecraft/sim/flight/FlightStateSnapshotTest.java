package com.tenicana.dronecraft.sim.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

class FlightStateSnapshotTest {
	@Test
	void normalizesQuaternionAndRoundTripsVectors() {
		Quaternion yawQuarterTurn = axisAngle(0.0, 1.0, 0.0, Math.PI * 0.5).multiply(3.0);
		FlightStateSnapshot snapshot = new FlightStateSnapshot(
				new Vec3(1.0, 2.0, 3.0),
				new Vec3(4.0, -2.0, 1.0),
				yawQuarterTurn,
				new Vec3(0.1, 0.2, 0.3),
				FlightMode.ACRO,
				true
		);

		assertEquals(1.0, snapshot.attitude().length(), 1.0e-12);
		Vec3 body = snapshot.velocityBodyMetersPerSecond();
		Vec3 world = snapshot.bodyVectorToWorld(body);
		assertVecClose(snapshot.velocityWorldMetersPerSecond(), world, 1.0e-12);
		assertTrue(snapshot.isFinite());
	}

	@Test
	void stepContextRequiresExplicitPositiveDt() {
		DroneConfig config = DroneConfig.racingQuad();
		DroneInput input = new DroneInput(0.2, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);

		FlightStepContext context = new FlightStepContext(input, null, DroneEnvironment.calm(), 0.005, 12L, config);

		assertEquals(0.005, context.dtSeconds(), 1.0e-12);
		assertEquals(FlightMode.HORIZON, context.previousState().flightMode());
		assertThrows(IllegalArgumentException.class, () -> new FlightStepContext(input, null, DroneEnvironment.calm(), 0.0, 12L, config));
	}

	@Test
	void actuatorOutputDefensivelyCopiesArrays() {
		double[] motorPower = {0.1, 0.2, 0.3, 0.4};
		ActuatorOutput output = new ActuatorOutput(motorPower, null, new double[] {1.0, 2.0});
		motorPower[0] = 0.9;
		double[] copy = output.motorPower();
		copy[1] = 0.9;

		assertEquals(0.1, output.motorPower()[0], 1.0e-12);
		assertEquals(0.2, output.motorPower()[1], 1.0e-12);
		assertEquals(0.25, output.averageMotorPower(), 1.0e-12);
		assertEquals(1.5, output.averageRotorThrustNewtons(), 1.0e-12);
		assertEquals(0.0, output.averageMotorRpm(), 1.0e-12);
	}

	private static Quaternion axisAngle(double x, double y, double z, double radians) {
		double half = radians * 0.5;
		double sin = Math.sin(half);
		return new Quaternion(Math.cos(half), x * sin, y * sin, z * sin);
	}

	private static void assertVecClose(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.y(), actual.y(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.z(), actual.z(), tolerance, () -> "expected=" + expected + " actual=" + actual);
	}
}
