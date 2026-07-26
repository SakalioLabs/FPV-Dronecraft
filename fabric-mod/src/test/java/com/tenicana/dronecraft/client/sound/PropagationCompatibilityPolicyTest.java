package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PropagationCompatibilityPolicyTest {
	@Test
	void autoUsesInternalPropagationWithoutKnownExternalMod() {
		PropagationCompatibilityPolicy.Decision decision =
				PropagationCompatibilityPolicy.resolve(
						"auto",
						Set.of("unrelated_mod")::contains
				);

		assertTrue(decision.internalPropagationEnabled());
	}

	@Test
	void autoUsesCleanSourceForSoundPhysicsRemastered() {
		PropagationCompatibilityPolicy.Decision decision =
				PropagationCompatibilityPolicy.resolve(
						null,
						Set.of("sound_physics_remastered")::contains
				);

		assertFalse(decision.internalPropagationEnabled());
		assertTrue(decision.reason().contains("sound_physics_remastered"));
	}

	@Test
	void explicitModesOverrideDetection() {
		assertTrue(PropagationCompatibilityPolicy.resolve(
				"internal",
				modId -> true
		).internalPropagationEnabled());
		assertFalse(PropagationCompatibilityPolicy.resolve(
				"clean",
				modId -> false
		).internalPropagationEnabled());
	}

	@Test
	void invalidModeIsRejectedInsteadOfSilentlyChangingOwnership() {
		assertThrows(
				IllegalArgumentException.class,
				() -> PropagationCompatibilityPolicy.resolve(
						"both",
						modId -> false
				)
		);
	}
}
