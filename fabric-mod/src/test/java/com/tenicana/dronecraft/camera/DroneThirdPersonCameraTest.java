package com.tenicana.dronecraft.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class DroneThirdPersonCameraTest {
	@Test
	void defaultBoomFramesDroneCenterFromBehindAndAbove() {
		DroneThirdPersonCamera.Pose pose = pose(0.0f, DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);

		assertEquals(12.0, pose.xMeters(), 1.0e-8);
		assertEquals(66.0, pose.yMeters(), 1.0e-8);
		assertEquals(-9.0, pose.zMeters(), 1.0e-8);
		assertLooksAtCenter(pose);
	}

	@Test
	void yawFollowsMinecraftHeadingWithoutRotatingTheHorizon() {
		for (float yawDegrees : new float[] {-179.0f, -90.0f, 0.0f, 45.0f, 90.0f, 179.0f}) {
			float yawRadians = (float) Math.toRadians(yawDegrees);
			DroneThirdPersonCamera.Pose pose = pose(yawRadians, DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);
			FpvCameraOrientation.Orientation orientation = orientation(pose);

			assertEquals(2.0, pose.yMeters() - 64.0, 1.0e-8);
			assertEquals(5.0, Math.hypot(pose.xMeters() - 12.0, pose.zMeters() + 4.0), 1.0e-8);
			assertEquals(0.0f, orientation.left().y(), 1.0e-6f);
			assertTrue(orientation.up().y() > 0.92f, "camera remains upright at yaw " + yawDegrees);
			assertLooksAtCenter(pose);
		}
	}

	@Test
	void fullYawTurnsAndWrapBoundaryKeepTheSameFollowFrame() {
		DroneThirdPersonCamera.Pose reference = pose((float) Math.toRadians(35.0), DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);
		for (int turns : new int[] {-2, -1, 1, 2}) {
			DroneThirdPersonCamera.Pose turned = pose((float) Math.toRadians(35.0 + 360.0 * turns), DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);
			assertEquals(reference.xMeters(), turned.xMeters(), 5.0e-6);
			assertEquals(reference.zMeters(), turned.zMeters(), 5.0e-6);
			assertTrue(orientation(reference).forwards().dot(orientation(turned).forwards()) > 0.999999f);
		}
		DroneThirdPersonCamera.Pose before = pose((float) Math.toRadians(179.9), DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);
		DroneThirdPersonCamera.Pose after = pose((float) Math.toRadians(-179.9), DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);
		assertTrue(orientation(before).forwards().dot(orientation(after).forwards()) > 0.99999f);
		assertTrue(orientation(before).up().dot(orientation(after).up()) > 0.99999f);
	}

	@Test
	void obstructionRetractsAlongTheBoomAndKeepsDroneCentered() {
		double tracedDistance = 2.7;
		double clearDistance = DroneThirdPersonCamera.clearBoomLength(tracedDistance);
		DroneThirdPersonCamera.Pose retracted = pose((float) Math.toRadians(73.0), clearDistance);

		assertTrue(clearDistance < tracedDistance, "camera stays before the hit surface");
		assertEquals(clearDistance, distanceFromCenter(retracted), 1.0e-8);
		assertLooksAtCenter(retracted);
		assertEquals(0.0f, orientation(retracted).left().y(), 1.0e-6f);
	}

	@Test
	void unobstructedNativeFloatDistancePreservesFullBoom() {
		double allowed = DroneThirdPersonCamera.clearBoomLength((float) DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS);

		assertEquals(DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS, allowed, 0.0);
		assertEquals(DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS,
				DroneThirdPersonCamera.clearBoomLength(100.0), 0.0);
	}

	@Test
	void invalidOrTinyCollisionDistancesCannotPushCameraThroughCenter() {
		for (double distance : new double[] {-5.0, 0.0, 0.1, Double.NaN, Double.POSITIVE_INFINITY}) {
			double clearDistance = DroneThirdPersonCamera.clearBoomLength(distance);
			DroneThirdPersonCamera.Pose atCenter = pose(Float.NaN, clearDistance);
			assertEquals(0.0, clearDistance, 0.0);
			assertEquals(12.0, atCenter.xMeters(), 0.0);
			assertEquals(64.0, atCenter.yMeters(), 0.0);
			assertEquals(-4.0, atCenter.zMeters(), 0.0);
			assertEquals(0.0f, atCenter.yawDegrees(), 0.0f);
			assertTrue(Float.isFinite(atCenter.pitchDegrees()));
		}
		assertEquals(DroneThirdPersonCamera.MAX_BOOM_LENGTH_METERS,
				distanceFromCenter(pose(0.0f, 100.0)), 1.0e-8);
	}

	private static DroneThirdPersonCamera.Pose pose(float yawRadians, double distanceMeters) {
		return DroneThirdPersonCamera.fromDroneCenter(12.0, 64.0, -4.0, yawRadians, distanceMeters);
	}

	private static FpvCameraOrientation.Orientation orientation(DroneThirdPersonCamera.Pose pose) {
		return FpvCameraOrientation.fromCameraAngles(pose.yawDegrees(), pose.pitchDegrees(), 0.0f);
	}

	private static double distanceFromCenter(DroneThirdPersonCamera.Pose pose) {
		return Math.sqrt(Math.pow(pose.xMeters() - 12.0, 2.0)
				+ Math.pow(pose.yMeters() - 64.0, 2.0) + Math.pow(pose.zMeters() + 4.0, 2.0));
	}

	private static void assertLooksAtCenter(DroneThirdPersonCamera.Pose pose) {
		Vector3f towardCenter = new Vector3f((float) (12.0 - pose.xMeters()),
				(float) (64.0 - pose.yMeters()), (float) (-4.0 - pose.zMeters())).normalize();
		assertTrue(orientation(pose).forwards().dot(towardCenter) > 0.999999f,
				"camera must point at the drone's interpolated center");
	}
}
