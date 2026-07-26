package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;

final class AcousticProfileJsonDecoderTest {
	@Test
	void decodesCompleteMeasuredV1Profile() {
		AcousticSourceProfile profile = AcousticProfileJsonDecoder.decode(
				new StringReader(validJson())
		);

		assertEquals("test:five_inch", profile.id());
		assertEquals(127, profile.key().rotorRadiusMillimetres());
		assertEquals(2, profile.sourceModel()
				.operatingPointGainCurve().anchors().size());
		assertTrue(profile.calibration().replacesLegacyRpmVolume());
	}

	@Test
	void rejectsUnknownFields() {
		String json = validJson().replace(
				"\"schema_version\": 1,",
				"\"schema_version\": 1, \"typo\": 2,"
		);

		IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> AcousticProfileJsonDecoder.decode(new StringReader(json))
		);

		assertTrue(error.getMessage().contains("$.typo: unknown field"));
	}

	@Test
	void rejectsDuplicateFieldsBeforeBuildingJsonTree() {
		String json = validJson().replace(
				"\"schema_version\": 1,",
				"\"schema_version\": 1, \"schema_version\": 1,"
		);

		IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> AcousticProfileJsonDecoder.decode(new StringReader(json))
		);

		assertTrue(error.getMessage().contains(
				"$.schema_version: duplicate field"
		));
	}

	@Test
	void rejectsMissingProvenance() {
		String json = validJson().replace(
				"""
				  "evidence": [{
				    "citation": "Test stand measurement",
				    "source": "https://example.invalid/data",
				    "license": "Test-only",
				    "measurement_conditions": "One metre, free field",
				    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
				  }],
				""",
				"""
				  "evidence": [],
				"""
		);

		IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> AcousticProfileJsonDecoder.decode(new StringReader(json))
		);
		assertTrue(error.getMessage().contains("evidence record"));
	}

	@Test
	void rejectsNonIncreasingRpmAnchors() {
		String json = validJson().replace(
				"\"rpm\": 12000",
				"\"rpm\": 6000"
		);

		IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> AcousticProfileJsonDecoder.decode(new StringReader(json))
		);

		assertTrue(error.getMessage().contains("strictly increasing"));
	}

	private static String validJson() {
		return """
				{
				  "schema_version": 1,
				  "id": "test:five_inch",
				  "key": {
				    "airframe_preset": "racing_quad",
				    "rotor_count": 4,
				    "blade_count": 3,
				    "rotor_radius_mm": 127,
				    "motor_pole_pairs": 7
				  },
				  "calibration": {
				    "measured": true,
				    "replaces_legacy_rpm_volume": true,
				    "motor_playback_gain_db": -3.0,
				    "propeller_playback_gain_db": -1.0
				  },
				  "validation": {
				    "evaluated": true,
				    "unseen_rpm_samples": 6,
				    "unseen_angle_samples": 8,
				    "order_spectrum_samples": 12,
				    "maximum_unseen_rpm_error_db": 1.5,
				    "maximum_unseen_angle_error_db": 2.0,
				    "maximum_order_spectrum_error_db": 2.5
				  },
				  "evidence": [{
				    "citation": "Test stand measurement",
				    "source": "https://example.invalid/data",
				    "license": "Test-only",
				    "measurement_conditions": "One metre, free field",
				    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
				  }],
				  "source_model": {
				    "blade_pass_harmonics": 12,
				    "harmonic_rolloff": 1.15,
				    "shaft_amplitude": 0.05,
				    "blade_pass_amplitude": 0.18,
				    "electrical_amplitude": 0.035,
				    "cogging_candidate_amplitude": 0.012,
				    "broadband_energy": 0.08,
				    "broadband_distribution": {
				      "low": 0.2,
				      "mid": 0.5,
				      "high": 0.3
				    },
				    "operating_points": [
				      {
				        "rpm": 6000,
				        "rotor_tonal_gain_db": -6,
				        "motor_tonal_gain_db": -4,
				        "broadband_gain_db": -8
				      },
				      {
				        "rpm": 12000,
				        "rotor_tonal_gain_db": 0,
				        "motor_tonal_gain_db": 1,
				        "broadband_gain_db": 2
				      }
				    ]
				  },
				  "directivity": {
				    "low_anchor_hz": 150,
				    "mid_anchor_hz": 1000,
				    "high_anchor_hz": 8000,
				    "low": {
				      "c2_db": 0,
				      "c4_db": 0,
				      "minimum_db": 0,
				      "maximum_db": 0
				    },
				    "mid": {
				      "c2_db": -2,
				      "c4_db": 1,
				      "minimum_db": -12,
				      "maximum_db": 0
				    },
				    "high": {
				      "c2_db": -4,
				      "c4_db": 2,
				      "minimum_db": -18,
				      "maximum_db": 0
				    }
				  }
				}
				""";
	}
}
