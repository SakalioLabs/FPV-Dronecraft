package com.tenicana.dronecraft.sim.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

class FlightSerializationCodecTest {
	@Test
	void frameRoundTripPreservesModelInputAndStateFields() {
		DroneInput input = new DroneInput(0.57, -0.20, 0.35, -0.45, true, false, FlightMode.ACRO);
		FlightStateSnapshot state = new FlightStateSnapshot(
				new Vec3(1.25, 2.5, -3.75),
				new Vec3(-4.0, 5.5, 6.25),
				new Quaternion(0.94, 0.05, -0.11, 0.31).normalized(),
				new Vec3(0.7, -0.8, 0.9),
				FlightMode.HORIZON,
				true
		);

		Map<String, String> frame = FlightSerializationCodec.encodeFrame("legacy_playable_direct", input, state);

		assertEquals("legacy_playable_direct", FlightSerializationCodec.decodeModelId(frame));
		assertEquals(input, FlightSerializationCodec.decodeInput(frame));
		assertStateEquals(state, FlightSerializationCodec.decodeState(frame));
	}

	@Test
	void decoderRejectsMissingOrNonFiniteFields() {
		Map<String, String> missingStateField = new HashMap<>(FlightSerializationCodec.encodeFrame(
				"simulation_drone_physics",
				new DroneInput(0.4, 0.1, -0.1, 0.2, true, true, FlightMode.ANGLE),
				FlightStateSnapshot.zero(FlightMode.ANGLE)
		));

		missingStateField.remove("state_position_world_x");
		assertThrows(IllegalArgumentException.class, () -> FlightSerializationCodec.decodeState(missingStateField));

		Map<String, String> nonFiniteInput = new HashMap<>(FlightSerializationCodec.encodeFrame(
				"simulation_drone_physics",
				DroneInput.idle(),
				FlightStateSnapshot.zero(FlightMode.ANGLE)
		));
		nonFiniteInput.put("input_throttle", "NaN");
		assertThrows(IllegalArgumentException.class, () -> FlightSerializationCodec.decodeInput(nonFiniteInput));
	}

	private static void assertStateEquals(FlightStateSnapshot expected, FlightStateSnapshot actual) {
		assertVecEquals(expected.positionWorldMeters(), actual.positionWorldMeters());
		assertVecEquals(expected.velocityWorldMetersPerSecond(), actual.velocityWorldMetersPerSecond());
		assertQuaternionEquals(expected.attitude(), actual.attitude());
		assertVecEquals(expected.angularVelocityBodyRadiansPerSecond(), actual.angularVelocityBodyRadiansPerSecond());
		assertEquals(expected.flightMode(), actual.flightMode());
		assertEquals(expected.armed(), actual.armed());
	}

	private static void assertVecEquals(Vec3 expected, Vec3 actual) {
		assertEquals(expected.x(), actual.x(), 1.0e-12);
		assertEquals(expected.y(), actual.y(), 1.0e-12);
		assertEquals(expected.z(), actual.z(), 1.0e-12);
	}

	private static void assertQuaternionEquals(Quaternion expected, Quaternion actual) {
		assertEquals(expected.w(), actual.w(), 1.0e-12);
		assertEquals(expected.x(), actual.x(), 1.0e-12);
		assertEquals(expected.y(), actual.y(), 1.0e-12);
		assertEquals(expected.z(), actual.z(), 1.0e-12);
	}
}
