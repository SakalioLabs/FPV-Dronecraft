package com.tenicana.dronecraft.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

final class AcousticProfileCatalogTest {
	@Test
	void exactPhysicalKeySelectsMeasuredProfile() {
		AcousticProfileKey key = new AcousticProfileKey(
				"racing_quad", 4, 3, 127, 7
		);
		AcousticSourceProfile measured = measured("test:five_inch", key);
		AcousticProfileCatalog catalog = new AcousticProfileCatalog(
				List.of(measured),
				AcousticSourceProfile.researchFallback()
		);

		assertEquals(measured, catalog.select(key));
		assertTrue(catalog.select(key).calibration().replacesLegacyRpmVolume());
	}

	@Test
	void unknownConfigurationUsesExplicitUnmeasuredFallback() {
		AcousticProfileCatalog catalog =
				AcousticProfileCatalog.researchFallbackOnly();

		AcousticSourceProfile selected = catalog.select(
				new AcousticProfileKey("cinewhoop", 4, 3, 76, 7)
		);

		assertEquals("fpvdrone:research_unity_fallback", selected.id());
		assertFalse(selected.calibration().measured());
		assertFalse(selected.calibration().replacesLegacyRpmVolume());
	}

	@Test
	void duplicatePhysicalConfigurationIsRejected() {
		AcousticProfileKey key = new AcousticProfileKey(
				"racing_quad", 4, 3, 127, 7
		);

		assertThrows(
				IllegalArgumentException.class,
				() -> new AcousticProfileCatalog(
						List.of(
								measured("test:first", key),
								measured("test:second", key)
						),
						AcousticSourceProfile.researchFallback()
				)
		);
	}

	@Test
	void loadedProfileCannotShadowFallbackIdentity() {
		AcousticSourceProfile fallback =
				AcousticSourceProfile.researchFallback();
		AcousticSourceProfile collision = measured(
				fallback.id(),
				new AcousticProfileKey("cinewhoop", 4, 3, 64, 7)
		);

		assertThrows(
				IllegalArgumentException.class,
				() -> new AcousticProfileCatalog(
						List.of(collision),
						fallback
				)
		);
	}

	@Test
	void unmeasuredResourceCannotOverrideExplicitFallback() {
		AcousticSourceProfile fallback =
				AcousticSourceProfile.researchFallback();

		assertThrows(
				IllegalArgumentException.class,
				() -> new AcousticProfileCatalog(
						List.of(fallback),
						fallback
				)
		);
	}

	@Test
	void radiusConversionIsStableAtMillimetreResolution() {
		assertEquals(
				127,
				AcousticProfileKey.fromMetres(
						"racing_quad", 4, 3, 0.127000004, 7
				).rotorRadiusMillimetres()
		);
	}

	private static AcousticSourceProfile measured(
			String id,
			AcousticProfileKey key
	) {
		return new AcousticSourceProfile(
				1,
				id,
				key,
				new AcousticSourceProfile.Calibration(true, true, -3.0, -1.0),
				new AcousticSourceProfile.Validation(
						true, 3, 4, 5, 1.0, 2.0, 2.5
				),
				List.of(new AcousticSourceProfile.Evidence(
						"Test measurement",
						URI.create("https://example.invalid/dataset"),
						"Test-only",
						"1 m free field",
						"0123456789abcdef0123456789abcdef"
								+ "0123456789abcdef0123456789abcdef"
				)),
				OrderTrackedRotorModel.Parameters.researchDefaults(),
				AxisymmetricSourceDirectivity.Parameters.omnidirectional()
		);
	}
}
