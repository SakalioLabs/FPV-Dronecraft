package com.tenicana.dronecraft.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.entity.DroneAirframeDimensions;
import com.tenicana.dronecraft.sim.DroneConfig;

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
		assertEquals(1.10, FpvCameraMount.clearForwardOffset(0.16), 1.0e-9);
		assertEquals(0.66, FpvCameraMount.clearUpOffset(0.16), 1.0e-9);
		assertEquals(1.12, FpvCameraMount.clearForwardOffset(1.12), 1.0e-9);
		assertEquals(0.68, FpvCameraMount.clearUpOffset(0.68), 1.0e-9);
	}

	@Test
	void clearOffsetsRemainClearAfterCameraShake() {
		assertEquals(1.10, FpvCameraMount.clearForwardOffset(1.10, 0.20), 1.0e-9);
		assertEquals(1.22, FpvCameraMount.clearForwardOffset(1.05, -0.12), 1.0e-9);
		assertEquals(0.66, FpvCameraMount.clearUpOffset(0.66, -0.20), 1.0e-9);
		assertEquals(0.74, FpvCameraMount.clearUpOffset(0.68, 0.06), 1.0e-9);
	}

	@Test
	void defaultCameraMountClearsEveryBuiltInAirframe() {
		DroneClientConfig config = DroneClientConfig.defaults();

		assertCameraClearsAirframe(config, DroneConfig.racingQuad());
		assertCameraClearsAirframe(config, DroneConfig.apDrone());
		assertCameraClearsAirframe(config, DroneConfig.cinewhoop());
		assertCameraClearsAirframe(config, DroneConfig.heavyLift());
		assertCameraClearsAirframe(config, DroneConfig.hexLift());
		assertCameraClearsAirframe(config, DroneConfig.octoLift());
		assertCameraClearsAirframe(config, DroneConfig.coaxialX8());
	}

	private static void assertCameraClearsAirframe(DroneClientConfig cameraConfig, DroneConfig droneConfig) {
		EntityDimensions dimensions = DroneAirframeDimensions.forConfig(droneConfig);
		double forward = FpvCameraMount.clearForwardOffset(cameraConfig.cameraForwardOffsetMeters());
		double up = FpvCameraMount.clearUpOffset(cameraConfig.cameraUpOffsetMeters());

		assertEquals(1.12, forward, 1.0e-6);
		assertEquals(0.68, up, 1.0e-6);
		assertTrue(forward >= dimensions.width() * 0.5 + 0.28);
		assertTrue(up >= dimensions.height() * 0.5 + 0.40);
	}
}
