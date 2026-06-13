package com.tenicana.dronecraft.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class DroneClientConfigTest {
	@Test
	void defaultsUseModeTwoStickLayout() {
		DroneClientConfig config = DroneClientConfig.defaults();

		assertEquals(0, config.yawAxis());
		assertEquals(1, config.throttleAxis());
		assertEquals(2, config.rollAxis());
		assertEquals(3, config.pitchAxis());
		assertTrue(config.pitchInverted());
		assertTrue(config.throttleInverted());
		assertTrue(config.gamepadDeadband() >= 0.10f);
	}

	@Test
	void legacySwappedModeTwoDefaultsMigrate() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setRollAxis(0);
		config.setPitchAxis(1);
		config.setYawAxis(3);
		config.setThrottleAxis(2);
		config.setRollInverted(false);
		config.setPitchInverted(true);
		config.setYawInverted(false);
		config.setThrottleInverted(true);
		setGamepadDeadband(config, 0.06f);

		config = normalize(config);

		assertEquals(0, config.yawAxis());
		assertEquals(1, config.throttleAxis());
		assertEquals(2, config.rollAxis());
		assertEquals(3, config.pitchAxis());
		assertTrue(config.gamepadDeadband() >= 0.10f);
	}

	private static DroneClientConfig normalize(DroneClientConfig config) throws ReflectiveOperationException {
		Method normalized = DroneClientConfig.class.getDeclaredMethod("normalized");
		normalized.setAccessible(true);
		return (DroneClientConfig) normalized.invoke(config);
	}

	private static void setGamepadDeadband(DroneClientConfig config, float value) throws ReflectiveOperationException {
		Field gamepadDeadband = DroneClientConfig.class.getDeclaredField("gamepadDeadband");
		gamepadDeadband.setAccessible(true);
		gamepadDeadband.setFloat(config, value);
	}
}
