package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.client.DroneClientState.InputSource;
import com.tenicana.dronecraft.client.config.DroneClientConfig;

class ControllerActivityKeepaliveTest {
	private static final float HOVER_THROTTLE = 0.22f;

	@Test
	void controllerAxisChangeRefreshesUserActivity() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, 1.0f, 0.0f, 0.0f)));
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();

		GamepadInputPath.Decision neutral = decision(config, provider);
		keepalive.sample(0, neutral, neutral.frame().controlInput(), true, true, false, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);
		provider.set(remote(0, "Radio", "radio-guid", axes(0.40f, 1.0f, 0.0f, 0.0f)));
		GamepadInputPath.Decision moved = decision(config, provider);

		ControllerActivityKeepalive.Result result = keepalive.sample(ControllerActivityKeepalive.REPORT_INTERVAL_TICKS, moved, moved.frame().controlInput(), true, true, false, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);

		assertTrue(result.meaningfulControllerActivity());
		assertTrue(result.axisChange());
		assertTrue(result.userActivityReported());
		assertEquals(1, notifier.reportCount());
	}

	@Test
	void staticControllerDoesNotContinuouslyRefreshUserActivity() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, 1.0f, 0.0f, 0.0f)));
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();

		for (int tick = 0; tick < 120; tick++) {
			GamepadInputPath.Decision decision = decision(config, provider);
			keepalive.sample(tick, decision, decision.frame().controlInput(), true, true, false, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);
			notifier.advanceTick();
		}

		assertEquals(0, notifier.reportCount());
	}

	@Test
	void connectedDeviceWithoutOperationDoesNotBlockAfk() {
		DroneClientConfig config = DroneClientConfig.defaults();
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, 0.0f, 0.0f, 0.0f)));
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();

		for (int tick = 0; tick < 60; tick++) {
			GamepadInputPath.Decision decision = decision(config, provider);
			keepalive.sample(tick, decision, decision.frame().controlInput(), true, true, false, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);
			notifier.advanceTick();
		}

		assertEquals(0, notifier.reportCount());
	}

	@Test
	void armedSustainedGamepadControlPreventsSixtySecondAfkWindow() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, -0.70f, 0.20f, -0.20f)));
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();
		long maxElapsed = 0L;

		for (int tick = 0; tick < 20 * 75; tick++) {
			GamepadInputPath.Decision decision = decision(config, provider);
			keepalive.sample(tick, decision, decision.frame().controlInput(), true, true, true, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);
			maxElapsed = Math.max(maxElapsed, notifier.status().inactivityElapsedMillis());
			notifier.advanceTick();
		}

		assertTrue(notifier.reportCount() > 20);
		assertTrue(maxElapsed < 60_000L, "controller keepalive should refresh before vanilla AFK threshold");
	}

	@Test
	void keyboardPathDoesNotReportControllerActivity() {
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();
		GamepadInputPath.Decision keyboardDecision = new GamepadInputPath.Decision(
				true,
				false,
				false,
				GamepadInputFrame.unavailable(new GamepadDeviceResolver.Resolution(List.of(), null, GamepadDeviceResolver.Status.NO_DEVICES, false)),
				InputSource.KEYBOARD
		);

		keepalive.sample(20, keyboardDecision, new ClientControlInput(0.5f, 1.0f, 1.0f, 1.0f, InputSource.KEYBOARD), true, true, true, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);

		assertEquals(0, notifier.reportCount());
	}

	@Test
	void pausedOrMinimizedClientDoesNotBypassVanillaPowerSaving() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, -0.70f, 0.20f, -0.20f)));
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		notifier.setCanNotify(false);
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();
		GamepadInputPath.Decision decision = decision(config, provider);

		ControllerActivityKeepalive.Result result = keepalive.sample(30, decision, decision.frame().controlInput(), true, true, true, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);

		assertTrue(result.blockedByWindowState());
		assertFalse(result.userActivityReported());
		assertEquals(0, notifier.reportCount());
	}

	@Test
	void disconnectedControllerCanEnterAfkAgain() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, -0.70f, 0.20f, -0.20f)));
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();

		GamepadInputPath.Decision active = decision(config, provider);
		keepalive.sample(20, active, active.frame().controlInput(), true, true, true, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);
		assertEquals(1, notifier.reportCount());

		provider.set();
		for (int tick = 21; tick < 21 + 20 * 70; tick++) {
			GamepadInputPath.Decision disconnected = decision(config, provider);
			keepalive.sample(tick, disconnected, new ClientControlInput(0.0f, 0.0f, 0.0f, 0.0f, InputSource.KEYBOARD), true, true, true, ControllerButtonEdgeTracker.ButtonEdges.none(), notifier);
			notifier.advanceTick();
		}

		assertEquals(1, notifier.reportCount());
		assertTrue(notifier.status().inactivityElapsedMillis() >= 60_000L);
	}

	@Test
	void buttonEdgeRefreshesUserActivityWithoutAxisMotion() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setPreferredGamepadDevice("radio-guid", "Radio");
		FakeJoystickProvider provider = new FakeJoystickProvider(remote(0, "Radio", "radio-guid", axes(0.0f, 1.0f, 0.0f, 0.0f)));
		FakeUserActivityNotifier notifier = new FakeUserActivityNotifier();
		ControllerActivityKeepalive keepalive = new ControllerActivityKeepalive();
		GamepadInputPath.Decision decision = decision(config, provider);

		ControllerActivityKeepalive.Result result = keepalive.sample(20, decision, decision.frame().controlInput(), true, true, false, new ControllerButtonEdgeTracker.ButtonEdges(true, false, false), notifier);

		assertTrue(result.buttonEdge());
		assertTrue(result.userActivityReported());
		assertEquals(1, notifier.reportCount());
	}

	private static GamepadInputPath.Decision decision(DroneClientConfig config, JoystickProvider provider) {
		return GamepadInputPath.evaluate(config, provider, true, false, true, HOVER_THROTTLE, true);
	}

	private static JoystickSnapshot remote(int id, String name, String guid, float[] axes) {
		return new JoystickSnapshot(id, name, guid, axes, new byte[0]);
	}

	private static float[] axes(float yaw, float throttle, float roll, float pitch) {
		return new float[] { yaw, throttle, roll, pitch };
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

	private static final class FakeUserActivityNotifier implements UserActivityNotifier {
		private static final long TICK_MILLIS = 50L;

		private boolean canNotify = true;
		private long nowMillis;
		private long lastNativeActivityMillis;
		private int lastReportTick = Integer.MIN_VALUE;
		private int reportCount;

		@Override
		public void notifyUserActivity(int tick) {
			if (!canNotify) {
				return;
			}
			reportCount++;
			lastReportTick = tick;
			lastNativeActivityMillis = nowMillis;
		}

		@Override
		public boolean canNotifyUserActivity() {
			return canNotify;
		}

		@Override
		public Status status() {
			return new Status(nowMillis - lastNativeActivityMillis, "AFK", reportCount > 0 ? "NONE" : "SHORT_AFK", 120, lastReportTick);
		}

		private int reportCount() {
			return reportCount;
		}

		private void setCanNotify(boolean canNotify) {
			this.canNotify = canNotify;
		}

		private void advanceTick() {
			nowMillis += TICK_MILLIS;
		}
	}
}
