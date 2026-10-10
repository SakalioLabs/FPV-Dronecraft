package com.tenicana.dronecraft.client.sound;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Selects whether Dronecraft or an installed sound-physics mod owns
 * environment propagation. Source synthesis, directivity and Doppler remain
 * active in both modes.
 */
public final class PropagationCompatibilityPolicy {
	public static final String MODE_PROPERTY =
			"fpvdrone.acoustics.propagationMode";
	public static final List<String> KNOWN_EXTERNAL_PROPAGATION_MODS = List.of(
			"sound_physics_remastered"
	);

	private PropagationCompatibilityPolicy() {
	}

	public static Decision resolve(
			String configuredMode,
			Predicate<String> isModLoaded
	) {
		Objects.requireNonNull(isModLoaded, "isModLoaded");
		String mode = configuredMode == null
				? "auto"
				: configuredMode.strip().toLowerCase(Locale.ROOT);
		if (mode.isEmpty()) {
			mode = "auto";
		}
		return switch (mode) {
			case "internal" -> new Decision(
					true,
					"forced internal propagation"
			);
			case "clean" -> new Decision(
					false,
					"forced clean source"
			);
			case "auto" -> autoDecision(isModLoaded);
			default -> throw new IllegalArgumentException(
					MODE_PROPERTY + " must be auto, internal, or clean"
			);
		};
	}

	private static Decision autoDecision(Predicate<String> isModLoaded) {
		for (String modId : KNOWN_EXTERNAL_PROPAGATION_MODS) {
			if (isModLoaded.test(modId)) {
				return new Decision(
						false,
						"external propagation mod detected: " + modId
				);
			}
		}
		return new Decision(true, "no external propagation mod detected");
	}

	public record Decision(
			boolean internalPropagationEnabled,
			String reason
	) {
		public Decision {
			Objects.requireNonNull(reason, "reason");
			if (reason.isBlank()) {
				throw new IllegalArgumentException("reason must not be blank");
			}
		}
	}
}
