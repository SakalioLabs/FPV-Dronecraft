package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.client.DroneClientState.InputSource;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.network.DroneControlPayload;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

class ControllerInputPathTest {
	@Test
	void singleControllerMapsThroughToGamepadPayload() {
		DroneClientConfig config = DroneClientConfig.defaults();
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "radio", "radio-guid", axes(0.35f, -0.60f, 0.50f, -0.40f)));

		GamepadInputPath.Decision decision = GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true);
		ClientControlInput input = decision.frame().controlInput();
		DroneControlPayload payload = new DroneControlPayload(input.throttle(), input.pitch(), input.roll(), input.yaw(), true, FlightMode.ACRO.id());
		UUID playerId = UUID.randomUUID();
		DroneControlManager.update(playerId, new DroneInput(
				payload.throttle(),
				payload.pitch(),
				payload.roll(),
				payload.yaw(),
				false,
				true,
				FlightMode.byId(payload.flightMode())
		), 10);
		DroneInput serverInput = DroneControlManager.get(playerId, 10);

		assertEquals(InputSource.GAMEPAD, decision.inputSource());
		assertEquals("radio-guid", config.preferredGamepadGuid());
		assertTrue(payload.throttle() > 0.0f);
		assertEquals(payload.throttle(), serverInput.throttle(), 1.0e-6f);
		assertNotEquals(0.0f, payload.pitch(), 1.0e-6f);
		assertNotEquals(0.0f, payload.roll(), 1.0e-6f);
		assertNotEquals(0.0f, payload.yaw(), 1.0e-6f);
	}

	@Test
	void disabledGamepadShowsAxisActivityButDoesNotClaimReady() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setGamepadEnabled(false);
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "radio", "radio-guid", axes(0.20f, -0.60f, 0.30f, -0.30f)));

		GamepadInputPath.Decision decision = GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true);
		GamepadControlPreview.Preview preview = GamepadControlPreview.fromAxes(config, provider.snapshots().get(0).axes(), 0.22f);

		assertTrue(preview.allAxesPresent());
		assertTrue(decision.disabledControllerActivity());
		assertFalse(decision.hasUsableGamepadInput());
		assertEquals(InputSource.KEYBOARD, decision.inputSource());
	}

	@Test
	void enablingGamepadTakesEffectOnNextDecisionWithoutReload() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setGamepadEnabled(false);
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "radio", "radio-guid", axes(0.0f, -0.60f, 0.30f, -0.30f)));

		assertEquals(InputSource.KEYBOARD, GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true).inputSource());

		config.setGamepadEnabled(true);
		GamepadInputPath.Decision enabled = GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true);

		assertEquals(InputSource.GAMEPAD, enabled.inputSource());
		assertTrue(enabled.hasUsableGamepadInput());
	}

	@Test
	void savedGuidKeepsCalibrationAndFlightOnSameController() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("real-guid", "Actual Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(
				remote(1, "Virtual HID", "virtual-guid", axes(0.0f, 0.0f, 0.0f, 0.0f)),
				remote(2, "Actual Radio", "real-guid", axes(0.40f, -0.70f, 0.50f, -0.50f))
		);

		GamepadInputPath.Decision decision = GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true);

		assertEquals(InputSource.GAMEPAD, decision.inputSource());
		assertEquals("real-guid", decision.frame().resolution().selected().guid());
		assertEquals(2, decision.frame().resolution().selected().glfwId());
	}

	@Test
	void preferredControllerDoesNotSilentlyFallbackAndReconnects() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("real-guid", "Actual Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(1, "Virtual HID", "virtual-guid", axes(0.0f, 0.0f, 0.0f, 0.0f)));

		GamepadInputPath.Decision disconnected = GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true);

		assertEquals(InputSource.KEYBOARD, disconnected.inputSource());
		assertEquals(GamepadDeviceResolver.Status.PREFERRED_DISCONNECTED, disconnected.frame().resolution().status());

		provider.set(remote(2, "Actual Radio", "real-guid", axes(0.0f, -0.70f, 0.50f, -0.50f)));
		GamepadInputPath.Decision reconnected = GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true);

		assertEquals(InputSource.GAMEPAD, reconnected.inputSource());
		assertEquals("real-guid", reconnected.frame().resolution().selected().guid());
	}

	@Test
	void throttleInversionAndCalibrationMapPhysicalEnds() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		config.setThrottleCalibration(0.20f, 0.80f);
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, 0.60f, 0.0f, 0.0f)));

		GamepadInputFrame low = GamepadInputFrame.sample(config, provider, 0.22f, true);
		provider.set(remote(0, "Radio", "radio-guid", axes(0.0f, -0.60f, 0.0f, 0.0f)));
		GamepadInputFrame high = GamepadInputFrame.sample(config, provider, 0.22f, true);

		assertEquals(0.0f, low.throttle(), 1.0e-6f);
		assertEquals(1.0f, high.throttle(), 1.0e-6f);
	}

	@Test
	void lowThrottleAllowsControllerArmAndHighThrottleReportsReason() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, 1.0f, 0.0f, 0.0f)));

		GamepadInputFrame low = GamepadInputFrame.sample(config, provider, 0.22f, true);
		ArmSafetyCheck.Result lowResult = ArmSafetyCheck.canArmFromMomentaryControl(low.throttle(), low.pitch(), low.roll(), low.yaw());

		provider.set(remote(0, "Radio", "radio-guid", axes(0.0f, -1.0f, 0.0f, 0.0f)));
		GamepadInputFrame high = GamepadInputFrame.sample(config, provider, 0.22f, true);
		ArmSafetyCheck.Result highResult = ArmSafetyCheck.canArmFromMomentaryControl(high.throttle(), high.pitch(), high.roll(), high.yaw());

		assertTrue(lowResult.allowed());
		assertFalse(highResult.allowed());
		assertEquals(ArmSafetyCheck.Reason.THROTTLE_NOT_LOW, highResult.reason());
	}

	@Test
	void armButtonEdgeDoesNotRepeatOnHeldButton() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		config.setArmButton(4);
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, 1.0f, 0.0f, 0.0f), buttons(4)));
		ControllerButtonEdgeTracker tracker = new ControllerButtonEdgeTracker();
		GamepadInputFrame frame = GamepadInputFrame.sample(config, provider, 0.22f, true);

		assertTrue(tracker.sample(frame).armPressed());
		assertFalse(tracker.sample(frame).armPressed());
	}

	@Test
	void validControllerInputDoesNotArbitrateBackToKeyboard() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, -0.70f, 0.30f, -0.30f)));

		GamepadInputPath.Decision decision = GamepadInputPath.evaluate(config, provider, true, false, true, 0.22f, true);

		assertTrue(decision.hasUsableGamepadInput());
		assertEquals(InputSource.GAMEPAD, decision.inputSource());
	}

	@Test
	void keyboardOnlyAuthorityRegressionRemainsKeyboard() {
		DroneClientConfig config = DroneClientConfig.defaults();
		FakeJoystickProvider provider = new FakeJoystickProvider();

		GamepadInputPath.Decision decision = GamepadInputPath.evaluate(config, provider, false, false, false, 0.22f, true);

		assertFalse(decision.controlAuthorized());
		assertEquals(InputSource.KEYBOARD, decision.inputSource());
	}

	private static JoystickSnapshot remote(int id, String name, String guid, float[] axes) {
		return remote(id, name, guid, axes, new byte[0]);
	}

	private static JoystickSnapshot remote(int id, String name, String guid, float[] axes, byte[] buttons) {
		return new JoystickSnapshot(id, name, guid, axes, buttons);
	}

	private static float[] axes(float yaw, float throttle, float roll, float pitch) {
		return new float[] { yaw, throttle, roll, pitch };
	}

	private static byte[] buttons(int pressed) {
		byte[] buttons = new byte[pressed + 1];
		buttons[pressed] = 1;
		return buttons;
	}

	private static final class FakeJoystickProvider implements JoystickProvider {
		private final List<JoystickSnapshot> snapshots = new ArrayList<>();

		private FakeJoystickProvider(JoystickSnapshot... snapshots) {
			set(snapshots);
		}

		private void set(JoystickSnapshot... nextSnapshots) {
			snapshots.clear();
			snapshots.addAll(List.of(nextSnapshots));
		}

		@Override
		public List<JoystickSnapshot> snapshots() {
			return List.copyOf(snapshots);
		}
	}
}
