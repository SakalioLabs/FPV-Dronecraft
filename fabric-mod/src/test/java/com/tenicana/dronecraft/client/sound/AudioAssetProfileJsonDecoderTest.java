package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

class AudioAssetProfileJsonDecoderTest {
	private static final String VALID_UNQUALIFIED = """
			{
			  "schema_version": 1,
			  "id": "fixture:synthetic_asset",
			  "key": {"airframe_preset":"fixture_quad","rotor_count":4,"blade_count":3,"rotor_radius_mm":63,"motor_pole_pairs":7},
			  "layer": "MOTOR",
			  "resource": "fixture:sounds/synthetic.ogg",
			  "calibration": {
			    "encoded_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			    "decoded_sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
			    "sample_rate":48000,"frames":2,"reference_start_frame":0,"reference_frame_count":2,
			    "reference_digital_rms":0.030517578125,"reference_pressure_rms_pa":0.0079345703125,
			    "full_scale_pressure_pa":1.0,"reference_distance_m":1.0,"reference_rpm":10000,"reference_axis_cosine":0,
			    "rpm_anchors":[5000,10000,20000],"axis_cosine_anchors":[-1,0,1],
			    "response_gain_db":[[-12,-6,0],[-12,0,12],[12,18,24]]
			  },
			  "validation":{"evaluated":false,"unseen_rpm_samples":0,"unseen_angle_samples":0,"order_spectrum_samples":0,
			    "maximum_unseen_rpm_error_db":0,"maximum_unseen_angle_error_db":0,"maximum_order_spectrum_error_db":0},
			  "evidence":{"source":"https://example.invalid/synthetic","license":"","motor":"","propeller":"","geometry":"",
			    "measurement_chain":"","background_conditions":"","raw_pressure_sha256":"","synchronized_rpm_sha256":"",
			    "training_sha256":"","holdout_sha256":"","validation_report_sha256":""}
			}
			""";

	@Test
	void acceptsSyntheticNumericSchemaButCannotAdmitItForRelease() {
		AudioAssetProfile profile = decode(VALID_UNQUALIFIED);
		assertFalse(profile.validation().passesReleaseGate());
		assertThrows(IllegalArgumentException.class, profile::requireReleaseDeclarations);
	}

	@Test
	void rejectsDuplicateRootAndNestedFields() {
		String duplicateRoot = VALID_UNQUALIFIED.replace(
				"\"schema_version\": 1,", "\"schema_version\": 1, \"schema_version\": 1,");
		String duplicateNested = VALID_UNQUALIFIED.replace(
				"\"rotor_count\":4,", "\"rotor_count\":4,\"rotor_count\":4,");
		assertThrows(IllegalArgumentException.class, () -> decode(duplicateRoot));
		assertThrows(IllegalArgumentException.class, () -> decode(duplicateNested));
	}

	@Test
	void rejectsUnknownMissingAndUnsupportedSchemaFields() {
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"schema_version\": 1,", "\"schema_version\": 2,")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"schema_version\": 1,", "\"schema_version\": 1,\"extra\":true,")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"layer\": \"MOTOR\",", "")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"sample_rate\":48000,", "\"sample_rate\":48000,\"source_model_calibration\":{},")));
	}

	@Test
	void rejectsCoercedTypesFractionsAndNulls() {
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"sample_rate\":48000", "\"sample_rate\":\"48000\"")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"evaluated\":false", "\"evaluated\":\"false\"")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"rotor_count\":4", "\"rotor_count\":4.5")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"key\": {", "\"key\": null,")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"rpm_anchors\":[5000,10000,20000]", "\"rpm_anchors\":null")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("\"reference_distance_m\":1.0", "\"reference_distance_m\":2.0")));
	}

	@Test
	void rejectsTrailingDocumentAndRelativeOrMalformedSourceUri() {
		assertThrows(IllegalArgumentException.class, () -> decode(VALID_UNQUALIFIED + " {}"));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("https://example.invalid/synthetic", "relative/source")));
		assertThrows(IllegalArgumentException.class,
				() -> decode(VALID_UNQUALIFIED.replace("https://example.invalid/synthetic", "http://[bad")));
	}

	@Test
	void rejectsSourceModelCalibrationShapeInAssetCalibrationSlot() {
		String wrongCalibration = "\"calibration\": {\"rpm_to_playback_gain_db\":0,\"propeller_playback_gain_db\":0}";
		String replaced = VALID_UNQUALIFIED.replaceFirst("(?s)\"calibration\": \\{.*?\\n\\s*\\},\\n\\s*\"validation\"", wrongCalibration + ",\n  \"validation\"");
		assertTrue(!replaced.equals(VALID_UNQUALIFIED));
		assertThrows(IllegalArgumentException.class, () -> decode(replaced));
	}

	private static AudioAssetProfile decode(String json) {
		return AudioAssetProfileJsonDecoder.decode(new StringReader(json));
	}
}
