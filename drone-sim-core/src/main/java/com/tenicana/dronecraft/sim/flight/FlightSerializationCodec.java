package com.tenicana.dronecraft.sim.flight;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

public final class FlightSerializationCodec {
	private static final String MODEL_ID = "model_id";

	private FlightSerializationCodec() {
	}

	public static Map<String, String> encodeFrame(String modelId, DroneInput input, FlightStateSnapshot state) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put(MODEL_ID, requireModelId(modelId));
		values.putAll(encodeInput(input));
		values.putAll(encodeState(state));
		return Map.copyOf(values);
	}

	public static String decodeModelId(Map<String, String> values) {
		return requireModelId(required(values, MODEL_ID));
	}

	public static Map<String, String> encodeInput(DroneInput input) {
		DroneInput safeInput = (input == null ? DroneInput.idle() : input).normalized();
		Map<String, String> values = new LinkedHashMap<>();
		values.put("input_throttle", Double.toString(safeInput.throttle()));
		values.put("input_pitch", Double.toString(safeInput.pitch()));
		values.put("input_roll", Double.toString(safeInput.roll()));
		values.put("input_yaw", Double.toString(safeInput.yaw()));
		values.put("input_armed", Boolean.toString(safeInput.armed()));
		values.put("input_link_active", Boolean.toString(safeInput.linkActive()));
		values.put("input_mode", safeInput.flightMode().name());
		return Map.copyOf(values);
	}

	public static DroneInput decodeInput(Map<String, String> values) {
		return new DroneInput(
				finiteDouble(values, "input_throttle"),
				finiteDouble(values, "input_pitch"),
				finiteDouble(values, "input_roll"),
				finiteDouble(values, "input_yaw"),
				booleanValue(values, "input_armed"),
				booleanValue(values, "input_link_active"),
				flightMode(values, "input_mode")
		).normalized();
	}

	public static Map<String, String> encodeState(FlightStateSnapshot state) {
		FlightStateSnapshot safeState = state == null ? FlightStateSnapshot.zero() : state;
		Map<String, String> values = new LinkedHashMap<>();
		putVec(values, "state_position_world", safeState.positionWorldMeters());
		putVec(values, "state_velocity_world", safeState.velocityWorldMetersPerSecond());
		putQuaternion(values, "state_attitude", safeState.attitude());
		putVec(values, "state_angular_velocity_body", safeState.angularVelocityBodyRadiansPerSecond());
		values.put("state_flight_mode", safeState.flightMode().name());
		values.put("state_armed", Boolean.toString(safeState.armed()));
		return Map.copyOf(values);
	}

	public static FlightStateSnapshot decodeState(Map<String, String> values) {
		return new FlightStateSnapshot(
				vec(values, "state_position_world"),
				vec(values, "state_velocity_world"),
				quaternion(values, "state_attitude"),
				vec(values, "state_angular_velocity_body"),
				flightMode(values, "state_flight_mode"),
				booleanValue(values, "state_armed")
		);
	}

	private static void putVec(Map<String, String> values, String prefix, Vec3 value) {
		Vec3 safeValue = value == null ? Vec3.ZERO : value;
		values.put(prefix + "_x", Double.toString(safeValue.x()));
		values.put(prefix + "_y", Double.toString(safeValue.y()));
		values.put(prefix + "_z", Double.toString(safeValue.z()));
	}

	private static Vec3 vec(Map<String, String> values, String prefix) {
		return new Vec3(
				finiteDouble(values, prefix + "_x"),
				finiteDouble(values, prefix + "_y"),
				finiteDouble(values, prefix + "_z")
		);
	}

	private static void putQuaternion(Map<String, String> values, String prefix, Quaternion value) {
		Quaternion safeValue = (value == null ? Quaternion.IDENTITY : value).normalized();
		values.put(prefix + "_w", Double.toString(safeValue.w()));
		values.put(prefix + "_x", Double.toString(safeValue.x()));
		values.put(prefix + "_y", Double.toString(safeValue.y()));
		values.put(prefix + "_z", Double.toString(safeValue.z()));
	}

	private static Quaternion quaternion(Map<String, String> values, String prefix) {
		return new Quaternion(
				finiteDouble(values, prefix + "_w"),
				finiteDouble(values, prefix + "_x"),
				finiteDouble(values, prefix + "_y"),
				finiteDouble(values, prefix + "_z")
		).normalized();
	}

	private static FlightMode flightMode(Map<String, String> values, String key) {
		String value = required(values, key);
		try {
			return FlightMode.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Unknown flight mode for " + key + ": " + value, exception);
		}
	}

	private static boolean booleanValue(Map<String, String> values, String key) {
		String value = required(values, key);
		if ("true".equals(value)) {
			return true;
		}
		if ("false".equals(value)) {
			return false;
		}
		throw new IllegalArgumentException("Expected boolean for " + key + ": " + value);
	}

	private static double finiteDouble(Map<String, String> values, String key) {
		String value = required(values, key);
		try {
			double parsed = Double.parseDouble(value);
			if (!Double.isFinite(parsed)) {
				throw new IllegalArgumentException("Expected finite number for " + key + ": " + value);
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Expected finite number for " + key + ": " + value, exception);
		}
	}

	private static String required(Map<String, String> values, String key) {
		String value = Objects.requireNonNull(values, "values").get(key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing serialized flight field: " + key);
		}
		return value;
	}

	private static String requireModelId(String modelId) {
		if (modelId == null || modelId.isBlank()) {
			throw new IllegalArgumentException("modelId is required");
		}
		return modelId;
	}
}
