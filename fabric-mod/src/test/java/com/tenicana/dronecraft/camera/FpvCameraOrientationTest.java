package com.tenicana.dronecraft.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class FpvCameraOrientationTest {
	@Test
	void levelDroneLooksAlongMinecraftForwardZ() {
		FpvCameraOrientation.Orientation orientation = FpvCameraOrientation.fromCameraAngles(0.0f, 0.0f, 0.0f);

		assertEquals(0.0f, orientation.forwards().x(), 1.0e-6f);
		assertEquals(0.0f, orientation.forwards().y(), 1.0e-6f);
		assertEquals(1.0f, orientation.forwards().z(), 1.0e-6f);
		assertOrthonormal(orientation);
	}

	@Test
	void steepPitchAndRollStayOrthonormal() {
		FpvCameraOrientation.Orientation orientation = FpvCameraOrientation.fromFpvMount(
				42.0f,
				91.0f,
				(float) Math.toRadians(137.0),
				16.0f,
				0.4f,
				-0.6f,
				(float) Math.toRadians(0.8)
		);

		assertOrthonormal(orientation);
		assertFinite(orientation.forwards());
		assertFinite(orientation.up());
		assertFinite(orientation.left());
	}

	@Test
	void rollCanPassAFullTurnWithoutFlippingForwardAxis() {
		FpvCameraOrientation.Orientation start = FpvCameraOrientation.fromCameraAngles(12.0f, -8.0f, 0.0f);
		FpvCameraOrientation.Orientation rolled = FpvCameraOrientation.fromCameraAngles(12.0f, -8.0f, (float) Math.toRadians(375.0));

		assertTrue(start.forwards().dot(rolled.forwards()) > 0.9999f);
		assertTrue(Math.abs(start.rotation().dot(rolled.rotation())) > 0.991f);
		assertOrthonormal(rolled);
	}

	@Test
	void neighboringInvertedRollSamplesUseSameQuaternionHemisphere() {
		FpvCameraOrientation.Orientation before = FpvCameraOrientation.fromCameraAngles(0.0f, 179.0f, (float) Math.toRadians(179.0));
		FpvCameraOrientation.Orientation after = FpvCameraOrientation.fromCameraAngles(0.0f, 181.0f, (float) Math.toRadians(181.0));

		assertTrue(Math.abs(before.rotation().dot(after.rotation())) > 0.999f);
		assertOrthonormal(before);
		assertOrthonormal(after);
	}

	@Test
	void completedPositiveAndNegativeLoopsPreserveTheCameraControlFrame() {
		FpvCameraOrientation.Orientation reference = FpvCameraOrientation.fromFpvMount(
				37.0f, 8.0f, (float) Math.toRadians(-12.0), 16.0f, 0.0f, 0.0f, 0.0f);
		for (int turns : new int[] {-2, -1, 1, 2}) {
			FpvCameraOrientation.Orientation pitched = FpvCameraOrientation.fromFpvMount(
					37.0f, 8.0f + turns * 360.0f, (float) Math.toRadians(-12.0), 16.0f, 0.0f, 0.0f, 0.0f);
			FpvCameraOrientation.Orientation rolled = FpvCameraOrientation.fromFpvMount(
					37.0f, 8.0f, (float) Math.toRadians(-12.0 + turns * 360.0), 16.0f, 0.0f, 0.0f, 0.0f);
			assertSameBasis(reference, pitched);
			assertSameBasis(reference, rolled);
		}
	}

	@Test
	void neighboringSamplesAcrossVerticalAndFullTurnsDoNotSnapTheCamera() {
		for (float boundary : new float[] {-720.0f, -360.0f, -180.0f, -90.0f, 90.0f, 180.0f, 360.0f, 720.0f}) {
			FpvCameraOrientation.Orientation beforePitch = FpvCameraOrientation.fromCameraAngles(
					37.0f, boundary - 1.0f, (float) Math.toRadians(12.0));
			FpvCameraOrientation.Orientation afterPitch = FpvCameraOrientation.fromCameraAngles(
					37.0f, boundary + 1.0f, (float) Math.toRadians(12.0));
			FpvCameraOrientation.Orientation beforeRoll = FpvCameraOrientation.fromCameraAngles(
					37.0f, 8.0f, (float) Math.toRadians(boundary - 1.0f));
			FpvCameraOrientation.Orientation afterRoll = FpvCameraOrientation.fromCameraAngles(
					37.0f, 8.0f, (float) Math.toRadians(boundary + 1.0f));
			assertTrue(Math.abs(beforePitch.rotation().dot(afterPitch.rotation())) > 0.9998f,
					"pitch camera snapped at " + boundary);
			assertTrue(Math.abs(beforeRoll.rotation().dot(afterRoll.rotation())) > 0.9998f,
					"roll camera snapped at " + boundary);
		}
	}

	private static void assertSameBasis(FpvCameraOrientation.Orientation expected, FpvCameraOrientation.Orientation actual) {
		assertTrue(expected.forwards().dot(actual.forwards()) > 0.99999f);
		assertTrue(expected.up().dot(actual.up()) > 0.99999f);
		assertTrue(expected.left().dot(actual.left()) > 0.99999f);
		assertOrthonormal(actual);
	}

	private static void assertOrthonormal(FpvCameraOrientation.Orientation orientation) {
		assertEquals(1.0f, orientation.forwards().length(), 1.0e-5f);
		assertEquals(1.0f, orientation.up().length(), 1.0e-5f);
		assertEquals(1.0f, orientation.left().length(), 1.0e-5f);
		assertEquals(0.0f, orientation.forwards().dot(orientation.up()), 1.0e-5f);
		assertEquals(0.0f, orientation.forwards().dot(orientation.left()), 1.0e-5f);
		assertEquals(0.0f, orientation.up().dot(orientation.left()), 1.0e-5f);
	}

	private static void assertFinite(Vector3f vector) {
		assertTrue(Float.isFinite(vector.x()));
		assertTrue(Float.isFinite(vector.y()));
		assertTrue(Float.isFinite(vector.z()));
	}
}
