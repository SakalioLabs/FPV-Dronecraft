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
	void defaultsUseClearFpvCameraMount() {
		DroneClientConfig config = DroneClientConfig.defaults();

		assertEquals(14.0f, config.cameraTiltDegrees(), 1.0e-4f);
		assertEquals(1.05f, config.cameraForwardOffsetMeters(), 1.0e-4f);
		assertEquals(0.62f, config.cameraUpOffsetMeters(), 1.0e-4f);
		assertEquals(0.12f, config.cameraVibrationScale(), 1.0e-4f);
		assertEquals(0.06f, config.cameraRollingShutterScale(), 1.0e-4f);
		assertEquals(0.018f, config.cameraLatencySeconds(), 1.0e-4f);
		assertEquals(112.0f, config.cameraFovDegrees(), 1.0e-4f);
		assertEquals(1.0f, config.cameraDynamicFovDegrees(), 1.0e-4f);
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

	@Test
	void legacyBlockedFpvCameraDefaultsMigrate() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "cameraTiltDegrees", 25.0f);
		setFloat(config, "cameraForwardOffsetMeters", 0.16f);
		setFloat(config, "cameraUpOffsetMeters", 0.16f);
		setFloat(config, "cameraVibrationScale", 1.0f);
		setFloat(config, "cameraRollingShutterScale", 0.55f);
		setFloat(config, "cameraLatencySeconds", 0.035f);
		setFloat(config, "cameraFovDegrees", 105.0f);
		setFloat(config, "cameraDynamicFovDegrees", 6.0f);

		config = normalize(config);

		assertEquals(14.0f, config.cameraTiltDegrees(), 1.0e-4f);
		assertEquals(1.05f, config.cameraForwardOffsetMeters(), 1.0e-4f);
		assertEquals(0.62f, config.cameraUpOffsetMeters(), 1.0e-4f);
		assertEquals(0.12f, config.cameraVibrationScale(), 1.0e-4f);
		assertEquals(0.06f, config.cameraRollingShutterScale(), 1.0e-4f);
		assertEquals(0.018f, config.cameraLatencySeconds(), 1.0e-4f);
		assertEquals(112.0f, config.cameraFovDegrees(), 1.0e-4f);
		assertEquals(1.0f, config.cameraDynamicFovDegrees(), 1.0e-4f);
	}

	@Test
	void previousClearCameraDefaultsMigrateToNoseMountedView() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "cameraTiltDegrees", 18.0f);
		setFloat(config, "cameraForwardOffsetMeters", 0.42f);
		setFloat(config, "cameraUpOffsetMeters", 0.24f);
		setFloat(config, "cameraVibrationScale", 0.45f);
		setFloat(config, "cameraRollingShutterScale", 0.25f);
		setFloat(config, "cameraLatencySeconds", 0.025f);
		setFloat(config, "cameraFovDegrees", 115.0f);
		setFloat(config, "cameraDynamicFovDegrees", 3.0f);

		config = normalize(config);

		assertEquals(1.05f, config.cameraForwardOffsetMeters(), 1.0e-4f);
		assertEquals(0.62f, config.cameraUpOffsetMeters(), 1.0e-4f);
		assertEquals(0.12f, config.cameraVibrationScale(), 1.0e-4f);
		assertEquals(112.0f, config.cameraFovDegrees(), 1.0e-4f);
	}

	@Test
	void recentNoseCameraDefaultsMigrateAboveAirframe() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "cameraTiltDegrees", 18.0f);
		setFloat(config, "cameraForwardOffsetMeters", 0.68f);
		setFloat(config, "cameraUpOffsetMeters", 0.34f);
		setFloat(config, "cameraVibrationScale", 0.22f);
		setFloat(config, "cameraRollingShutterScale", 0.12f);
		setFloat(config, "cameraLatencySeconds", 0.018f);
		setFloat(config, "cameraFovDegrees", 118.0f);
		setFloat(config, "cameraDynamicFovDegrees", 2.0f);

		config = normalize(config);

		assertEquals(14.0f, config.cameraTiltDegrees(), 1.0e-4f);
		assertEquals(1.05f, config.cameraForwardOffsetMeters(), 1.0e-4f);
		assertEquals(0.62f, config.cameraUpOffsetMeters(), 1.0e-4f);
		assertEquals(0.12f, config.cameraVibrationScale(), 1.0e-4f);
		assertEquals(0.06f, config.cameraRollingShutterScale(), 1.0e-4f);
		assertEquals(1.0f, config.cameraDynamicFovDegrees(), 1.0e-4f);
	}

	private static DroneClientConfig normalize(DroneClientConfig config) throws ReflectiveOperationException {
		Method normalized = DroneClientConfig.class.getDeclaredMethod("normalized");
		normalized.setAccessible(true);
		return (DroneClientConfig) normalized.invoke(config);
	}

	private static void setGamepadDeadband(DroneClientConfig config, float value) throws ReflectiveOperationException {
		setFloat(config, "gamepadDeadband", value);
	}

	private static void setFloat(DroneClientConfig config, String fieldName, float value) throws ReflectiveOperationException {
		Field field = DroneClientConfig.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setFloat(config, value);
	}
}
