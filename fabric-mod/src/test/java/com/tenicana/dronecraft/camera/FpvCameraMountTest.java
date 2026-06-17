package com.tenicana.dronecraft.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class FpvCameraMountTest {
	@Test
	void retreatFromHitStopsBeforeObstacle() {
		Vec3 origin = new Vec3(0.0, 1.0, 0.0);
		Vec3 desired = new Vec3(0.0, 1.0, -1.0);
		Vec3 hit = new Vec3(0.0, 1.0, -0.40);

		Vec3 adjusted = FpvCameraMount.retreatFromHit(origin, desired, hit, 0.08);

		assertEquals(0.0, adjusted.x(), 1.0e-9);
		assertEquals(1.0, adjusted.y(), 1.0e-9);
		assertEquals(-0.32, adjusted.z(), 1.0e-9);
	}

	@Test
	void retreatFromHitKeepsDesiredWhenHitIsPastCameraMount() {
		Vec3 origin = new Vec3(0.0, 1.0, 0.0);
		Vec3 desired = new Vec3(0.0, 1.0, -1.0);
		Vec3 hit = new Vec3(0.0, 1.0, -2.0);

		Vec3 adjusted = FpvCameraMount.retreatFromHit(origin, desired, hit, 0.08);

		assertEquals(desired.x(), adjusted.x(), 1.0e-9);
		assertEquals(desired.y(), adjusted.y(), 1.0e-9);
		assertEquals(desired.z(), adjusted.z(), 1.0e-9);
	}

	@Test
	void retreatFromHitFallsBackToOriginWhenObstacleIsTooClose() {
		Vec3 origin = new Vec3(0.0, 1.0, 0.0);
		Vec3 desired = new Vec3(0.0, 1.0, -1.0);
		Vec3 hit = new Vec3(0.0, 1.0, -0.03);

		Vec3 adjusted = FpvCameraMount.retreatFromHit(origin, desired, hit, 0.08);

		assertEquals(origin.x(), adjusted.x(), 1.0e-9);
		assertEquals(origin.y(), adjusted.y(), 1.0e-9);
		assertEquals(origin.z(), adjusted.z(), 1.0e-9);
	}

	@Test
	void clearOffsetsKeepCameraOutOfAirframeEvenWithOldConfig() {
		assertEquals(0.95, FpvCameraMount.clearForwardOffset(0.16), 1.0e-9);
		assertEquals(0.58, FpvCameraMount.clearUpOffset(0.16), 1.0e-9);
		assertEquals(1.05, FpvCameraMount.clearForwardOffset(1.05), 1.0e-9);
		assertEquals(0.62, FpvCameraMount.clearUpOffset(0.62), 1.0e-9);
	}
}
