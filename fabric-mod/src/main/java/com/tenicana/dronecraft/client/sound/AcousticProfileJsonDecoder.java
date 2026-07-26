package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import com.tenicana.dronecraft.acoustics.AcousticProfileKey;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import com.tenicana.dronecraft.acoustics.AxisymmetricSourceDirectivity;
import com.tenicana.dronecraft.acoustics.OrderTrackedRotorModel;
import com.tenicana.dronecraft.acoustics.RotorOperatingPointGainCurve;

/**
 * Strict v1 decoder. Unknown or missing fields fail the whole resource reload
 * instead of silently producing a partially calibrated model.
 */
final class AcousticProfileJsonDecoder {
	private AcousticProfileJsonDecoder() {
	}

	static AcousticSourceProfile decode(Reader reader) {
		JsonElement rootElement = parseStrict(reader);
		JsonObject root = object(rootElement, "$");
		exactFields(
				root,
				"$",
				"schema_version",
				"id",
				"key",
				"calibration",
				"validation",
				"evidence",
				"source_model",
				"directivity"
		);
		int schemaVersion = integer(root, "schema_version", "$");
		if (schemaVersion != AcousticSourceProfile.CURRENT_SCHEMA_VERSION) {
			throw error(
					"$.schema_version",
					"expected " + AcousticSourceProfile.CURRENT_SCHEMA_VERSION
							+ " but got " + schemaVersion
			);
		}
		return new AcousticSourceProfile(
				schemaVersion,
				text(root, "id", "$"),
				decodeKey(requiredObject(root, "key", "$")),
				decodeCalibration(requiredObject(root, "calibration", "$")),
				decodeValidation(requiredObject(root, "validation", "$")),
				decodeEvidence(requiredArray(root, "evidence", "$")),
				decodeSourceModel(requiredObject(root, "source_model", "$")),
				decodeDirectivity(requiredObject(root, "directivity", "$"))
		);
	}

	private static AcousticSourceProfile.Validation decodeValidation(
			JsonObject object
	) {
		String path = "$.validation";
		exactFields(
				object,
				path,
				"evaluated",
				"unseen_rpm_samples",
				"unseen_angle_samples",
				"order_spectrum_samples",
				"maximum_unseen_rpm_error_db",
				"maximum_unseen_angle_error_db",
				"maximum_order_spectrum_error_db"
		);
		return new AcousticSourceProfile.Validation(
				bool(object, "evaluated", path),
				integer(object, "unseen_rpm_samples", path),
				integer(object, "unseen_angle_samples", path),
				integer(object, "order_spectrum_samples", path),
				number(object, "maximum_unseen_rpm_error_db", path),
				number(object, "maximum_unseen_angle_error_db", path),
				number(object, "maximum_order_spectrum_error_db", path)
		);
	}

	private static JsonElement parseStrict(Reader source) {
		try {
			JsonReader reader = new JsonReader(source);
			JsonElement value = readElement(reader, "$");
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw error("$", "unexpected trailing JSON value");
			}
			return value;
		} catch (IOException error) {
			throw new IllegalArgumentException("$: invalid JSON", error);
		}
	}

	private static JsonElement readElement(JsonReader reader, String path)
			throws IOException {
		return switch (reader.peek()) {
			case BEGIN_OBJECT -> readObject(reader, path);
			case BEGIN_ARRAY -> readArray(reader, path);
			case STRING -> new JsonPrimitive(reader.nextString());
			case NUMBER -> readNumber(reader, path);
			case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
			case NULL -> {
				reader.nextNull();
				yield JsonNull.INSTANCE;
			}
			default -> throw error(path, "unexpected JSON token " + reader.peek());
		};
	}

	private static JsonObject readObject(JsonReader reader, String path)
			throws IOException {
		JsonObject object = new JsonObject();
		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			String childPath = path + "." + name;
			if (object.has(name)) {
				throw error(childPath, "duplicate field");
			}
			object.add(name, readElement(reader, childPath));
		}
		reader.endObject();
		return object;
	}

	private static JsonArray readArray(JsonReader reader, String path)
			throws IOException {
		JsonArray array = new JsonArray();
		reader.beginArray();
		int index = 0;
		while (reader.hasNext()) {
			array.add(readElement(reader, path + "[" + index + "]"));
			index++;
		}
		reader.endArray();
		return array;
	}

	private static JsonPrimitive readNumber(JsonReader reader, String path)
			throws IOException {
		String encoded = reader.nextString();
		try {
			return new JsonPrimitive(new BigDecimal(encoded));
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(
					path + ": invalid JSON number " + encoded,
					error
			);
		}
	}

	private static AcousticProfileKey decodeKey(JsonObject object) {
		String path = "$.key";
		exactFields(
				object,
				path,
				"airframe_preset",
				"rotor_count",
				"blade_count",
				"rotor_radius_mm",
				"motor_pole_pairs"
		);
		return new AcousticProfileKey(
				text(object, "airframe_preset", path),
				integer(object, "rotor_count", path),
				integer(object, "blade_count", path),
				integer(object, "rotor_radius_mm", path),
				integer(object, "motor_pole_pairs", path)
		);
	}

	private static AcousticSourceProfile.Calibration decodeCalibration(
			JsonObject object
	) {
		String path = "$.calibration";
		exactFields(
				object,
				path,
				"measured",
				"replaces_legacy_rpm_volume",
				"motor_playback_gain_db",
				"propeller_playback_gain_db"
		);
		return new AcousticSourceProfile.Calibration(
				bool(object, "measured", path),
				bool(object, "replaces_legacy_rpm_volume", path),
				number(object, "motor_playback_gain_db", path),
				number(object, "propeller_playback_gain_db", path)
		);
	}

	private static List<AcousticSourceProfile.Evidence> decodeEvidence(
			JsonArray array
	) {
		List<AcousticSourceProfile.Evidence> evidence =
				new ArrayList<>(array.size());
		for (int index = 0; index < array.size(); index++) {
			String path = "$.evidence[" + index + "]";
			JsonObject object = object(array.get(index), path);
			exactFields(
					object,
					path,
					"citation",
					"source",
					"license",
					"measurement_conditions",
					"sha256"
			);
			evidence.add(new AcousticSourceProfile.Evidence(
					text(object, "citation", path),
					URI.create(text(object, "source", path)),
					text(object, "license", path),
					text(object, "measurement_conditions", path),
					text(object, "sha256", path)
			));
		}
		return evidence;
	}

	private static OrderTrackedRotorModel.Parameters decodeSourceModel(
			JsonObject object
	) {
		String path = "$.source_model";
		exactFields(
				object,
				path,
				"blade_pass_harmonics",
				"harmonic_rolloff",
				"shaft_amplitude",
				"blade_pass_amplitude",
				"electrical_amplitude",
				"cogging_candidate_amplitude",
				"broadband_energy",
				"operating_points",
				"broadband_distribution"
		);
		return new OrderTrackedRotorModel.Parameters(
				integer(object, "blade_pass_harmonics", path),
				number(object, "harmonic_rolloff", path),
				number(object, "shaft_amplitude", path),
				number(object, "blade_pass_amplitude", path),
				number(object, "electrical_amplitude", path),
				number(object, "cogging_candidate_amplitude", path),
				number(object, "broadband_energy", path),
				decodeOperatingPoints(
						requiredArray(object, "operating_points", path)
				),
				decodeBands(
						requiredObject(object, "broadband_distribution", path),
						path + ".broadband_distribution"
				)
		);
	}

	private static AcousticBands decodeBands(
			JsonObject object,
			String path
	) {
		exactFields(object, path, "low", "mid", "high");
		return new AcousticBands(
				number(object, "low", path),
				number(object, "mid", path),
				number(object, "high", path)
		);
	}

	private static RotorOperatingPointGainCurve decodeOperatingPoints(
			JsonArray array
	) {
		List<RotorOperatingPointGainCurve.Anchor> anchors =
				new ArrayList<>(array.size());
		for (int index = 0; index < array.size(); index++) {
			String path = "$.source_model.operating_points[" + index + "]";
			JsonObject object = object(array.get(index), path);
			exactFields(
					object,
					path,
					"rpm",
					"rotor_tonal_gain_db",
					"motor_tonal_gain_db",
					"broadband_gain_db"
			);
			anchors.add(new RotorOperatingPointGainCurve.Anchor(
					number(object, "rpm", path),
					number(object, "rotor_tonal_gain_db", path),
					number(object, "motor_tonal_gain_db", path),
					number(object, "broadband_gain_db", path)
			));
		}
		return new RotorOperatingPointGainCurve(anchors);
	}

	private static AxisymmetricSourceDirectivity.Parameters decodeDirectivity(
			JsonObject object
	) {
		String path = "$.directivity";
		exactFields(
				object,
				path,
				"low_anchor_hz",
				"mid_anchor_hz",
				"high_anchor_hz",
				"low",
				"mid",
				"high"
		);
		return new AxisymmetricSourceDirectivity.Parameters(
				number(object, "low_anchor_hz", path),
				number(object, "mid_anchor_hz", path),
				number(object, "high_anchor_hz", path),
				decodePolynomial(requiredObject(object, "low", path), path + ".low"),
				decodePolynomial(requiredObject(object, "mid", path), path + ".mid"),
				decodePolynomial(requiredObject(object, "high", path), path + ".high")
		);
	}

	private static AxisymmetricSourceDirectivity.EvenPolynomial decodePolynomial(
			JsonObject object,
			String path
	) {
		exactFields(object, path, "c2_db", "c4_db", "minimum_db", "maximum_db");
		return new AxisymmetricSourceDirectivity.EvenPolynomial(
				number(object, "c2_db", path),
				number(object, "c4_db", path),
				number(object, "minimum_db", path),
				number(object, "maximum_db", path)
		);
	}

	private static JsonObject requiredObject(
			JsonObject parent,
			String name,
			String path
	) {
		return object(required(parent, name, path), path + "." + name);
	}

	private static JsonArray requiredArray(
			JsonObject parent,
			String name,
			String path
	) {
		JsonElement value = required(parent, name, path);
		if (!value.isJsonArray()) {
			throw error(path + "." + name, "expected an array");
		}
		return value.getAsJsonArray();
	}

	private static JsonObject object(JsonElement value, String path) {
		if (value == null || !value.isJsonObject()) {
			throw error(path, "expected an object");
		}
		return value.getAsJsonObject();
	}

	private static String text(JsonObject object, String name, String path) {
		JsonElement value = required(object, name, path);
		if (!value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isString()) {
			throw error(path + "." + name, "expected a string");
		}
		return value.getAsString();
	}

	private static boolean bool(JsonObject object, String name, String path) {
		JsonElement value = required(object, name, path);
		if (!value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isBoolean()) {
			throw error(path + "." + name, "expected a boolean");
		}
		return value.getAsBoolean();
	}

	private static int integer(JsonObject object, String name, String path) {
		double value = number(object, name, path);
		if (value != Math.rint(value)
				|| value < Integer.MIN_VALUE
				|| value > Integer.MAX_VALUE) {
			throw error(path + "." + name, "expected a 32-bit integer");
		}
		return (int) value;
	}

	private static double number(JsonObject object, String name, String path) {
		JsonElement value = required(object, name, path);
		if (!value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isNumber()) {
			throw error(path + "." + name, "expected a number");
		}
		double number = value.getAsDouble();
		if (!Double.isFinite(number)) {
			throw error(path + "." + name, "expected a finite number");
		}
		return number;
	}

	private static JsonElement required(
			JsonObject object,
			String name,
			String path
	) {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull()) {
			throw error(path + "." + name, "field is required");
		}
		return value;
	}

	private static void exactFields(
			JsonObject object,
			String path,
			String... expectedNames
	) {
		Set<String> expected = new HashSet<>(Arrays.asList(expectedNames));
		for (String actual : object.keySet()) {
			if (!expected.remove(actual)) {
				throw error(path + "." + actual, "unknown field");
			}
		}
		if (!expected.isEmpty()) {
			throw error(path, "missing fields " + expected);
		}
	}

	private static IllegalArgumentException error(String path, String message) {
		return new IllegalArgumentException(path + ": " + message);
	}
}
