package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.OrderTrackedRotorModel;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;

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
		assertEquals(1, profile.schemaVersion());
		assertTrue(profile.sourceModel().broadbandOperatingPointGainCurve().isUnity());
		assertTrue(profile.calibration().replacesLegacyRpmVolume());
	}

	@Test
	void decodesV2AndPreservesBothGainCurvesAcrossJsonRoundtrip() {
		String json = validV2Json();
		AcousticSourceProfile profile = decode(json);
		AcousticSourceProfile reparsed = decode(JsonParser.parseString(json).toString());
		assertEquals(2, profile.schemaVersion());
		assertEquals(2, profile.sourceModel().broadbandOperatingPointGainCurve().anchors().size());
		assertEquals(profile.sourceModel().broadbandOperatingPointGainCurve().anchors(),
				reparsed.sourceModel().broadbandOperatingPointGainCurve().anchors());
		assertEquals(profile.sourceModel().operatingPointGainCurve().anchors(),
				reparsed.sourceModel().operatingPointGainCurve().anchors());

		AcousticEmissionFrame legacy = emission(decode(validJson()), 6_000.0);
		AcousticEmissionFrame calibrated = emission(profile, 6_000.0);
		assertEquals(legacy.tones(), calibrated.tones());
		assertEquals(legacy.broadbandEnergy().low() * Math.pow(10.0, -6.0 / 10.0),
				calibrated.broadbandEnergy().low(), 1.0e-12);
		assertEquals(legacy.broadbandEnergy().mid(), calibrated.broadbandEnergy().mid(), 1.0e-12);
		assertEquals(legacy.broadbandEnergy().high() * Math.pow(10.0, 6.0 / 10.0),
				calibrated.broadbandEnergy().high(), 1.0e-12);
		for (double rpm : new double[]{6_000.0, Math.sqrt(6_000.0 * 12_000.0), 12_000.0}) {
			assertEquals(emission(profile, rpm), emission(reparsed, rpm));
		}
	}

	@Test
	void v2UnityIsOutputCompatibleWithV1() {
		JsonObject json = JsonParser.parseString(validV2Json()).getAsJsonObject();
		for (var anchor : json.getAsJsonObject("source_model").getAsJsonArray("broadband_operating_points")) {
			for (String field : List.of("low_gain_db", "mid_gain_db", "high_gain_db")) {
				anchor.getAsJsonObject().addProperty(field, 0.0);
			}
		}
		AcousticSourceProfile v1 = decode(validJson());
		AcousticSourceProfile v2 = decode(json.toString());
		for (double rpm : new double[]{4_000.0, 6_000.0, 8_000.0, 12_000.0, 16_000.0}) {
			assertEquals(emission(v1, rpm), emission(v2, rpm));
		}
	}

	@Test
	void rejectsUnknownVersionAndVersionSpecificFieldMismatch() {
		String unknownVersion = validJson().replace("\"schema_version\": 1", "\"schema_version\": 3");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> decode(unknownVersion))
				.getMessage().contains("$.schema_version"));
		String v2WithoutCurve = validJson().replace("\"schema_version\": 1", "\"schema_version\": 2");
		assertEquals("$.source_model: missing fields [broadband_operating_points]",
				assertThrows(IllegalArgumentException.class, () -> decode(v2WithoutCurve)).getMessage());
		JsonObject v1WithCurve = JsonParser.parseString(validV2Json()).getAsJsonObject();
		v1WithCurve.addProperty("schema_version", 1);
		for (var anchor : v1WithCurve.getAsJsonObject("source_model").getAsJsonArray("broadband_operating_points")) {
			for (String field : List.of("low_gain_db", "mid_gain_db", "high_gain_db")) {
				anchor.getAsJsonObject().addProperty(field, 0.0);
			}
		}
		assertTrue(assertThrows(IllegalArgumentException.class, () -> decode(v1WithCurve.toString()))
				.getMessage().contains("broadband_operating_points: unknown field"));
	}

	@Test
	void rejectsInvalidIndependentBroadbandAnchors() {
		for (String anchors : List.of(
				"[]",
				"[{\"rpm\":0,\"low_gain_db\":0,\"mid_gain_db\":0,\"high_gain_db\":0}]",
				"[{\"rpm\":6000,\"low_gain_db\":0,\"mid_gain_db\":0,\"high_gain_db\":0},"
						+ "{\"rpm\":6000,\"low_gain_db\":0,\"mid_gain_db\":0,\"high_gain_db\":0}]",
				"[{\"rpm\":6000,\"low_gain_db\":60.01,\"mid_gain_db\":0,\"high_gain_db\":0}]",
				"[{\"rpm\":6000,\"low_gain_db\":0,\"mid_gain_db\":-120.01,\"high_gain_db\":0}]",
				"[{\"rpm\":6000,\"low_gain_db\":0,\"mid_gain_db\":0,\"high_gain_db\":1e999}]",
				"[{\"rpm\":6000,\"low_gain_db\":0,\"mid_gain_db\":0}]",
				"[{\"rpm\":6000,\"low_gain_db\":0,\"mid_gain_db\":0,\"high_gain_db\":0,\"typo\":0}]"
		)) {
			JsonObject json = JsonParser.parseString(validV2Json()).getAsJsonObject();
			json.getAsJsonObject("source_model").add("broadband_operating_points", JsonParser.parseString(anchors));
			assertThrows(IllegalArgumentException.class, () -> decode(json.toString()), anchors);
		}
	}

	@Test
	void rejectsDuplicateIndependentBandFieldsAndV2FailedReleaseGate() {
		String duplicate = validV2Json().replace("\"low_gain_db\":-6", "\"low_gain_db\":-6,\"low_gain_db\":0");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> decode(duplicate))
				.getMessage().contains("low_gain_db: duplicate field"));
		JsonObject failedGate = JsonParser.parseString(validV2Json()).getAsJsonObject();
		failedGate.getAsJsonObject("validation").addProperty("maximum_unseen_rpm_error_db", 3.01);
		assertTrue(assertThrows(IllegalArgumentException.class, () -> decode(failedGate.toString()))
				.getMessage().contains("holdouts with maximum error <= 3 dB"));
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

	private static AcousticSourceProfile decode(String json) {
		return AcousticProfileJsonDecoder.decode(new StringReader(json));
	}

	private static AcousticEmissionFrame emission(AcousticSourceProfile profile, double rpm) {
		return new OrderTrackedRotorModel(profile.sourceModel()).evaluate(new AcousticSourceFrame(
				1L, 0L, AcousticVector.ZERO, AcousticVector.ZERO,
				new AcousticVector(0.0, 1.0, 0.0), 0.1,
				List.of(new RotorAcousticState(rpm, 0.8, 1.0, 0.0635, 3, 7, 1, 0.0))
		), 48_000);
	}

	private static String validV2Json() {
		JsonObject json = JsonParser.parseString(validJson()).getAsJsonObject();
		json.addProperty("schema_version", 2);
		JsonArray anchors = JsonParser.parseString("""
				[
				  {"rpm":6000,"low_gain_db":-6,"mid_gain_db":0,"high_gain_db":6},
				  {"rpm":12000,"low_gain_db":6,"mid_gain_db":-2,"high_gain_db":-6}
				]
				""").getAsJsonArray();
		json.getAsJsonObject("source_model").add("broadband_operating_points", anchors);
		return json.toString();
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
