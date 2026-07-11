package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.Quaternion;

class FlightAttitudeProjectionTest {
	@Test
	void headingYawDoesNotFoldPastNinetyDegrees() {
		assertHeading(120.0, attitudeQuaternion(120.0, 0.0, 0.0));
		assertHeading(-120.0, attitudeQuaternion(-120.0, 0.0, 0.0));
		assertHeading(179.0, attitudeQuaternion(179.0, 0.0, 0.0));
		assertHeading(-179.0, attitudeQuaternion(-179.0, 0.0, 0.0));
	}

	@Test
	void headingYawSurvivesPitchAndRoll() {
		assertHeading(135.0, attitudeQuaternion(135.0, Math.toRadians(15.0), Math.toRadians(-12.0)));
		assertHeading(-135.0, attitudeQuaternion(-135.0, Math.toRadians(-15.0), Math.toRadians(12.0)));
	}

	@Test
	void verticalForwardUsesFiniteFallback() {
		double heading = FlightAttitudeProjection.headingYawDegrees(
				attitudeQuaternion(50.0, Math.toRadians(90.0), 0.0),
				42.0
		);

		assertEquals(42.0, heading, 1.0e-9);
	}

	private static void assertHeading(double expectedDegrees, Quaternion attitude) {
		double actualDegrees = FlightAttitudeProjection.headingYawDegrees(attitude, 0.0);
		assertEquals(0.0, angularDifferenceDegrees(expectedDegrees, actualDegrees), 1.0e-9,
				() -> "expected=" + expectedDegrees + " actual=" + actualDegrees);
	}

	private static double angularDifferenceDegrees(double expected, double actual) {
		double difference = actual - expected;
		while (difference > 180.0) {
			difference -= 360.0;
		}
		while (difference < -180.0) {
			difference += 360.0;
		}
		return difference;
	}

	private static Quaternion attitudeQuaternion(double yawDegrees, double pitchRadians, double rollRadians) {
		Quaternion yaw = axisAngle(0.0, 1.0, 0.0, Math.toRadians(yawDegrees));
		Quaternion pitch = axisAngle(1.0, 0.0, 0.0, pitchRadians);
		Quaternion roll = axisAngle(0.0, 0.0, 1.0, rollRadians);
		return yaw.multiply(pitch).multiply(roll).normalized();
	}

	private static Quaternion axisAngle(double x, double y, double z, double radians) {
		double half = radians * 0.5;
		double sin = Math.sin(half);
		return new Quaternion(Math.cos(half), x * sin, y * sin, z * sin).normalized();
	}
}
