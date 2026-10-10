package com.tenicana.dronecraft.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

final class AcousticSourceProfileTest {
	@Test
	void measuredProfileRejectsFailedRpmHoldout() {
		assertThrows(
				IllegalArgumentException.class,
				() -> measured(
						new AcousticSourceProfile.Validation(
								true, 4, 4, 4, 3.01, 2.0, 2.0
						),
						hash()
				)
		);
	}

	@Test
	void measuredProfileRejectsFailedAngleHoldout() {
		assertThrows(
				IllegalArgumentException.class,
				() -> measured(
						new AcousticSourceProfile.Validation(
								true, 4, 4, 4, 2.0, 3.01, 2.0
						),
						hash()
				)
		);
	}

	@Test
	void measuredProfileRejectsFailedOrderSpectrumHoldout() {
		assertThrows(
				IllegalArgumentException.class,
				() -> measured(
						new AcousticSourceProfile.Validation(
								true, 4, 4, 4, 2.0, 2.0, 3.01
						),
						hash()
				)
		);
	}

	@Test
	void measuredProfileRequiresSourceHash() {
		assertThrows(
				IllegalArgumentException.class,
				() -> measured(
						new AcousticSourceProfile.Validation(
								true, 4, 4, 4, 2.0, 2.0, 2.0
						),
						""
				)
		);
	}

	@Test
	void measuredProfileCannotLeaveLegacyRpmVolumeEnabled() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new AcousticSourceProfile.Calibration(
						true, false, 0.0, 0.0
				)
		);
	}

	@Test
	void v1CannotCarryNonUnityIndependentBroadbandGains() {
		assertThrows(IllegalArgumentException.class, () -> measured(
				1, passingValidation(), bandCalibratedModel(), hash()
		));
		assertEquals(2, measured(2, passingValidation(), bandCalibratedModel(), hash()).schemaVersion());
	}

	@Test
	void v2RetainsEveryReleaseGateAndEvidenceRequirement() {
		for (AcousticSourceProfile.Validation validation : List.of(
				new AcousticSourceProfile.Validation(true, 4, 4, 4, 3.01, 2.0, 2.0),
				new AcousticSourceProfile.Validation(true, 4, 4, 4, 2.0, 3.01, 2.0),
				new AcousticSourceProfile.Validation(true, 4, 4, 4, 2.0, 2.0, 3.01),
				AcousticSourceProfile.Validation.notEvaluated()
		)) {
			assertThrows(IllegalArgumentException.class, () -> measured(2, validation, bandCalibratedModel(), hash()));
		}
		assertThrows(IllegalArgumentException.class, () -> measured(2, passingValidation(), bandCalibratedModel(), ""));
	}

	@Test
	void currentSchemasRejectNonUnityHarmonicGainsWithoutChangingTheVersion() {
		assertEquals(2, AcousticSourceProfile.CURRENT_SCHEMA_VERSION);
		RotorHarmonicGainCurve harmonicCurve = new RotorHarmonicGainCurve(Map.of(
				2, List.of(new RotorHarmonicGainCurve.Anchor(8_000.0, 1.0))
		));
		for (int schemaVersion : new int[]{1, 2}) {
			assertThrows(IllegalArgumentException.class, () -> measured(
					schemaVersion, passingValidation(), harmonicModel(harmonicCurve), hash()
			));
			AcousticSourceProfile fallback = AcousticSourceProfile.researchFallback();
			assertThrows(IllegalArgumentException.class, () -> new AcousticSourceProfile(
					schemaVersion, fallback.id(), fallback.key(), fallback.calibration(), fallback.validation(),
					fallback.evidence(), harmonicModel(harmonicCurve), fallback.directivity()
			));
		}
	}

	@Test
	void currentSchemasAcceptHarmonicCurvesWithOnlyZeroDbAnchors() {
		RotorHarmonicGainCurve harmonicCurve = new RotorHarmonicGainCurve(Map.of(
				2, List.of(new RotorHarmonicGainCurve.Anchor(4_000.0, 0.0),
						new RotorHarmonicGainCurve.Anchor(16_000.0, 0.0))
		));
		for (int schemaVersion : new int[]{1, 2}) {
			assertEquals(schemaVersion, measured(
					schemaVersion, passingValidation(), harmonicModel(harmonicCurve), hash()
			).schemaVersion());
		}
	}

	@Test
	void rejectsUnknownSchemaVersions() {
		for (int schemaVersion : new int[]{0, 3}) {
			assertThrows(IllegalArgumentException.class, () -> measured(
					schemaVersion, passingValidation(), OrderTrackedRotorModel.Parameters.researchDefaults(), hash()
			));
		}
	}

	private static AcousticSourceProfile.Validation passingValidation() {
		return new AcousticSourceProfile.Validation(true, 4, 4, 4, 2.0, 2.0, 2.0);
	}

	private static OrderTrackedRotorModel.Parameters bandCalibratedModel() {
		return new OrderTrackedRotorModel.Parameters(
				12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08,
				RotorOperatingPointGainCurve.unity(), new AcousticBands(0.2, 0.5, 0.3),
				new RotorBroadbandGainCurve(List.of(new RotorBroadbandGainCurve.Anchor(8_000.0, 1.0, -2.0, 3.0)))
		);
	}

	private static OrderTrackedRotorModel.Parameters harmonicModel(RotorHarmonicGainCurve harmonicCurve) {
		return new OrderTrackedRotorModel.Parameters(
				12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08,
				RotorOperatingPointGainCurve.unity(), new AcousticBands(0.2, 0.5, 0.3),
				RotorBroadbandGainCurve.unity(), harmonicCurve
		);
	}

	private static AcousticSourceProfile measured(
			AcousticSourceProfile.Validation validation,
			String sha256
	) {
		return measured(1, validation, OrderTrackedRotorModel.Parameters.researchDefaults(), sha256);
	}

	private static AcousticSourceProfile measured(
			int schemaVersion,
			AcousticSourceProfile.Validation validation,
			OrderTrackedRotorModel.Parameters sourceModel,
			String sha256
	) {
		return new AcousticSourceProfile(
				schemaVersion,
				"test:measured",
				new AcousticProfileKey("racing_quad", 4, 3, 64, 7),
				new AcousticSourceProfile.Calibration(true, true, 0.0, 0.0),
				validation,
				List.of(new AcousticSourceProfile.Evidence(
						"Test measurement",
						URI.create("https://example.invalid/dataset"),
						"Test-only",
						"One metre free field",
						sha256
				)),
				sourceModel,
				AxisymmetricSourceDirectivity.Parameters.omnidirectional()
		);
	}

	private static String hash() {
		return "0123456789abcdef0123456789abcdef"
				+ "0123456789abcdef0123456789abcdef";
	}
}
