package com.tenicana.dronecraft.client.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientCameraSafetyTest {
	@Test
	void invalidEntityTargetsAreRejectedBeforeAttackOrUsePackets() {
		assertFalse(
				ClientCameraSafety.isInvalidEntityTarget(false, false, false, true, false),
				"missing targets should not cancel ordinary input"
		);
		assertFalse(
				ClientCameraSafety.isInvalidEntityTarget(true, true, true, false, true),
				"current-world live targets must remain interactable"
		);

		assertTrue(
				ClientCameraSafety.isInvalidEntityTarget(true, false, false, false, true),
				"entity targets must be cleared when the client level has already gone away"
		);
		assertTrue(
				ClientCameraSafety.isInvalidEntityTarget(true, true, false, false, true),
				"cross-world entity targets must be cleared"
		);
		assertTrue(
				ClientCameraSafety.isInvalidEntityTarget(true, true, true, true, true),
				"removed entity targets must be cleared"
		);
		assertTrue(
				ClientCameraSafety.isInvalidEntityTarget(true, true, true, false, false),
				"dead entity targets must be cleared"
		);
	}

	@Test
	void fpvCameraRequiresAliveCurrentWorldDrone() {
		assertTrue(
				ClientCameraSafety.isUsableFpvDroneReference(true, true, true, false, true),
				"active FPV with a live current-world drone should keep rendering the drone camera"
		);

		assertFalse(
				ClientCameraSafety.isUsableFpvDroneReference(false, true, true, false, true),
				"inactive FPV must not use the drone camera"
		);
		assertFalse(
				ClientCameraSafety.isUsableFpvDroneReference(true, false, false, false, false),
				"missing drones must not keep FPV active"
		);
		assertFalse(
				ClientCameraSafety.isUsableFpvDroneReference(true, true, false, false, true),
				"cross-world drones must not keep FPV active"
		);
		assertFalse(
				ClientCameraSafety.isUsableFpvDroneReference(true, true, true, true, true),
				"removed drones must not keep FPV active"
		);
		assertFalse(
				ClientCameraSafety.isUsableFpvDroneReference(true, true, true, false, false),
				"dead drones must not keep FPV active"
		);
	}

	@Test
	void fpvPoseDelayResetsWhenEntityIdOrWorldChanges() {
		Object oldLevel = new Object();
		Object newLevel = new Object();

		assertFalse(
				ClientCameraSafety.shouldResetFpvPoseDelay(42, 42, oldLevel, oldLevel),
				"same entity id in the same world can reuse pose delay history"
		);
		assertTrue(
				ClientCameraSafety.shouldResetFpvPoseDelay(43, 42, oldLevel, oldLevel),
				"new entity ids must reset pose delay history"
		);
		assertTrue(
				ClientCameraSafety.shouldResetFpvPoseDelay(42, 42, newLevel, oldLevel),
				"the same entity id in a new world must not inherit old-world camera pose history"
		);
		assertTrue(
				ClientCameraSafety.shouldResetFpvPoseDelay(42, 42, oldLevel, null),
				"entering FPV from a clean state must initialize pose delay history"
		);
	}
}
