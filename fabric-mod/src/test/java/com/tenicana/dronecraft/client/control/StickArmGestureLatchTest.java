package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StickArmGestureLatchTest {
	@Test
	void firesOnlyAfterConfiguredHold() {
		StickArmGestureLatch latch = new StickArmGestureLatch(3);

		assertFalse(latch.update(true));
		assertFalse(latch.update(true));
		assertTrue(latch.update(true));
	}

	@Test
	void doesNotRepeatUntilGestureReleases() {
		StickArmGestureLatch latch = new StickArmGestureLatch(2);

		assertFalse(latch.update(true));
		assertTrue(latch.update(true));
		assertFalse(latch.update(true));
		assertFalse(latch.update(true));

		assertFalse(latch.update(false));
		assertFalse(latch.update(true));
		assertTrue(latch.update(true));
	}

	@Test
	void resetClearsHoldProgressAndLatch() {
		StickArmGestureLatch latch = new StickArmGestureLatch(2);

		assertFalse(latch.update(true));
		latch.reset();
		assertFalse(latch.update(true));
		assertTrue(latch.update(true));
	}

	@Test
	void invalidHoldCountFallsBackToOneTick() {
		StickArmGestureLatch latch = new StickArmGestureLatch(0);

		assertTrue(latch.update(true));
		assertFalse(latch.update(true));
	}
}
