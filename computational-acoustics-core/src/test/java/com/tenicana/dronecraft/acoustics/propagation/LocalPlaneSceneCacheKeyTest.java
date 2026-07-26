package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlaneSceneCacheKeyTest {
	@Test
	void exactIdentityInvalidatesEveryMovementAndGenerationChange() {
		LocalPlaneSceneCacheKey cache = new LocalPlaneSceneCacheKey();
		assertFalse(cache.matches(1, 1, 2, 3, 4, 5, 6));

		cache.update(1, 1, 2, 3, 4, 5, 6);

		assertTrue(cache.matches(1, 1, 2, 3, 4, 5, 6));
		assertFalse(cache.matches(2, 1, 2, 3, 4, 5, 6));
		assertFalse(cache.matches(1, 1.000001, 2, 3, 4, 5, 6));
		assertFalse(cache.matches(1, 1, 2, 3, 4, 5.000001, 6));

		cache.invalidate();
		assertFalse(cache.matches(1, 1, 2, 3, 4, 5, 6));
	}

	@Test
	void positiveAndNegativeZeroShareCanonicalIdentity() {
		LocalPlaneSceneCacheKey cache = new LocalPlaneSceneCacheKey();
		cache.update(0, -0.0, 0.0, -0.0, 0.0, -0.0, 0.0);

		assertTrue(cache.matches(0, 0.0, -0.0, 0.0, -0.0, 0.0, -0.0));
	}
}
