package com.tenicana.dronecraft.acoustics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable exact-match catalog. A missing measured configuration always
 * resolves to the explicit research fallback.
 */
public final class AcousticProfileCatalog {
	private final Map<AcousticProfileKey, AcousticSourceProfile> profilesByKey;
	private final List<AcousticSourceProfile> profiles;
	private final AcousticSourceProfile fallback;

	public AcousticProfileCatalog(
			Collection<AcousticSourceProfile> profiles,
			AcousticSourceProfile fallback
	) {
		Objects.requireNonNull(profiles, "profiles");
		this.fallback = Objects.requireNonNull(fallback, "fallback");
		Map<AcousticProfileKey, AcousticSourceProfile> byKey =
				new LinkedHashMap<>();
		Map<String, AcousticSourceProfile> byId = new LinkedHashMap<>();
		byId.put(fallback.id(), fallback);
		for (AcousticSourceProfile profile : profiles) {
			Objects.requireNonNull(profile, "profile");
			if (!profile.calibration().measured()) {
				throw new IllegalArgumentException(
						"catalog entries must be measured; use the explicit "
								+ "fallback for unmeasured research parameters"
				);
			}
			AcousticSourceProfile duplicateKey =
					byKey.putIfAbsent(profile.key(), profile);
			if (duplicateKey != null) {
				throw new IllegalArgumentException(
						"duplicate acoustic profile key for "
								+ duplicateKey.id() + " and " + profile.id()
				);
			}
			AcousticSourceProfile duplicateId =
					byId.putIfAbsent(profile.id(), profile);
			if (duplicateId != null) {
				throw new IllegalArgumentException(
						"duplicate acoustic profile id " + profile.id()
				);
			}
		}
		profilesByKey = Map.copyOf(byKey);
		this.profiles = List.copyOf(byKey.values());
	}

	public static AcousticProfileCatalog researchFallbackOnly() {
		return new AcousticProfileCatalog(
				List.of(),
				AcousticSourceProfile.researchFallback()
		);
	}

	public AcousticSourceProfile select(AcousticProfileKey key) {
		return profilesByKey.getOrDefault(
				Objects.requireNonNull(key, "key"),
				fallback
		);
	}

	public List<AcousticSourceProfile> profiles() {
		return profiles;
	}

	public AcousticSourceProfile fallback() {
		return fallback;
	}
}
