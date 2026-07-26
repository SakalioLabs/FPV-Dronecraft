package com.tenicana.dronecraft.acoustics;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;

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

	private static AcousticSourceProfile measured(
			AcousticSourceProfile.Validation validation,
			String sha256
	) {
		return new AcousticSourceProfile(
				1,
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
				OrderTrackedRotorModel.Parameters.researchDefaults(),
				AxisymmetricSourceDirectivity.Parameters.omnidirectional()
		);
	}

	private static String hash() {
		return "0123456789abcdef0123456789abcdef"
				+ "0123456789abcdef0123456789abcdef";
	}
}
