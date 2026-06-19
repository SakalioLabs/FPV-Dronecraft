package com.tenicana.dronecraft.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.client.DroneClientState.HudMode;

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
		assertEquals(0.08f, config.gamepadDeadband(), 1.0e-4f);
		assertTrainingFeel(config);
		assertEquals(0.0f, config.rollCenter(), 1.0e-4f);
		assertEquals(0.0f, config.pitchCenter(), 1.0e-4f);
		assertEquals(0.0f, config.yawCenter(), 1.0e-4f);
		assertEquals(HudMode.MINIMAL, config.hudMode());
	}

	@Test
	void invalidGamepadFeelSettingsNormalizeToPlayableDefaults() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "gamepadExpo", Float.NaN);
		setFloat(config, "gamepadRollPitchRateScale", 0.0f);
		setFloat(config, "gamepadYawRateScale", -1.0f);
		setFloat(config, "gamepadAxisRisePerTick", Float.NEGATIVE_INFINITY);
		setFloat(config, "gamepadAxisFallPerTick", 0.0f);

		config = normalize(config);

		assertTrainingFeel(config);
	}

	@Test
	void hudModePersistsAndNormalizes() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();

		config.setHudMode(HudMode.OFF);
		assertEquals(HudMode.OFF, normalize(config).hudMode());

		setObject(config, "hudMode", null);
		assertEquals(HudMode.MINIMAL, normalize(config).hudMode());
	}

	@Test
	void gamepadFeelPresetsExposePlayableInGameProfiles() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();

		assertEquals(DroneClientConfig.ControlFeelPreset.TRAINING, config.gamepadFeelPreset());

		DroneClientConfig.ControlFeelPreset next = config.nextGamepadFeelPreset();
		assertEquals(DroneClientConfig.ControlFeelPreset.SPORT, next);
		assertEquals(0.48f, config.gamepadExpo(), 1.0e-4f);
		assertEquals(1.00f, config.gamepadRollPitchRateScale(), 1.0e-4f);
		assertEquals(1.00f, config.gamepadYawRateScale(), 1.0e-4f);
		assertEquals(0.22f, config.gamepadAxisRisePerTick(), 1.0e-4f);
		assertEquals(0.50f, config.gamepadAxisFallPerTick(), 1.0e-4f);

		next = config.nextGamepadFeelPreset();
		assertEquals(DroneClientConfig.ControlFeelPreset.ACRO, next);
		assertAcroFeel(config);

		setFloat(config, "gamepadRollPitchRateScale", 0.83f);
		config = normalize(config);

		assertEquals(DroneClientConfig.ControlFeelPreset.CUSTOM, config.gamepadFeelPreset());
		assertEquals(DroneClientConfig.ControlFeelPreset.TRAINING, config.nextGamepadFeelPreset());
		assertTrainingFeel(config);
	}

	@Test
	void legacyHotTrainingDefaultsMigrateToGentlerPreset() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "gamepadExpo", 0.97f);
		setFloat(config, "gamepadRollPitchRateScale", 0.72f);
		setFloat(config, "gamepadYawRateScale", 0.70f);
		setFloat(config, "gamepadAxisRisePerTick", 0.075f);
		setFloat(config, "gamepadAxisFallPerTick", 0.18f);

		config = normalize(config);

		assertEquals(DroneClientConfig.ControlFeelPreset.TRAINING, config.gamepadFeelPreset());
		assertTrainingFeel(config);
	}

	@Test
	void recentTrainingDefaultsMigrateToSofterPreset() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "gamepadExpo", 0.98f);
		setFloat(config, "gamepadRollPitchRateScale", 0.55f);
		setFloat(config, "gamepadYawRateScale", 0.52f);
		setFloat(config, "gamepadAxisRisePerTick", 0.055f);
		setFloat(config, "gamepadAxisFallPerTick", 0.24f);

		config = normalize(config);

		assertEquals(DroneClientConfig.ControlFeelPreset.TRAINING, config.gamepadFeelPreset());
		assertTrainingFeel(config);
	}

	@Test
	void previousSoftTrainingDefaultsMigrateToPrecisionPreset() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "gamepadExpo", 0.98f);
		setFloat(config, "gamepadRollPitchRateScale", 0.48f);
		setFloat(config, "gamepadYawRateScale", 0.44f);
		setFloat(config, "gamepadAxisRisePerTick", 0.040f);
		setFloat(config, "gamepadAxisFallPerTick", 0.28f);

		config = normalize(config);

		assertEquals(DroneClientConfig.ControlFeelPreset.TRAINING, config.gamepadFeelPreset());
		assertTrainingFeel(config);
	}

	@Test
	void slowTrainingDefaultsMigrateToDirectPreset() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "gamepadExpo", 1.00f);
		setFloat(config, "gamepadRollPitchRateScale", 0.42f);
		setFloat(config, "gamepadYawRateScale", 0.38f);
		setFloat(config, "gamepadAxisRisePerTick", 0.032f);
		setFloat(config, "gamepadAxisFallPerTick", 0.32f);
		setGamepadDeadband(config, 0.10f);

		config = normalize(config);

		assertEquals(DroneClientConfig.ControlFeelPreset.TRAINING, config.gamepadFeelPreset());
		assertEquals(0.08f, config.gamepadDeadband(), 1.0e-4f);
		assertTrainingFeel(config);
	}

	@Test
	void previousAcroDefaultsMigrateToDirectAcroPreset() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "gamepadExpo", 0.75f);
		setFloat(config, "gamepadRollPitchRateScale", 1.00f);
		setFloat(config, "gamepadYawRateScale", 1.00f);
		setFloat(config, "gamepadAxisRisePerTick", 0.20f);
		setFloat(config, "gamepadAxisFallPerTick", 0.35f);

		config = normalize(config);

		assertEquals(DroneClientConfig.ControlFeelPreset.ACRO, config.gamepadFeelPreset());
		assertAcroFeel(config);
	}

	@Test
	void recentAcroDefaultsMigrateToDirectAcroPreset() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "gamepadExpo", 0.70f);
		setFloat(config, "gamepadRollPitchRateScale", 1.00f);
		setFloat(config, "gamepadYawRateScale", 1.00f);
		setFloat(config, "gamepadAxisRisePerTick", 0.25f);
		setFloat(config, "gamepadAxisFallPerTick", 0.55f);

		config = normalize(config);

		assertEquals(DroneClientConfig.ControlFeelPreset.ACRO, config.gamepadFeelPreset());
		assertAcroFeel(config);
	}

	@Test
	void stickCenterCalibrationRemovesGamepadDriftAndPreservesEndpoints() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setStickCenters(0.18f, -0.12f, 0.09f);

		assertEquals(0.0f, config.calibrateRollAxis(0.18f), 1.0e-5f);
		assertEquals(0.0f, config.calibratePitchAxis(0.12f), 1.0e-5f);
		assertEquals(0.0f, config.calibrateYawAxis(0.09f), 1.0e-5f);
		assertEquals(1.0f, config.calibrateRollAxis(1.0f), 1.0e-5f);
		assertEquals(-1.0f, config.calibrateRollAxis(-1.0f), 1.0e-5f);
	}

	@Test
	void invalidStickCentersNormalizeToSafeOffsets() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "rollCenter", Float.NaN);
		setFloat(config, "pitchCenter", 2.0f);
		setFloat(config, "yawCenter", -2.0f);

		config = normalize(config);

		assertEquals(0.0f, config.rollCenter(), 1.0e-5f);
		assertEquals(0.45f, config.pitchCenter(), 1.0e-5f);
		assertEquals(-0.45f, config.yawCenter(), 1.0e-5f);
	}

	@Test
	void defaultsUseClearFpvCameraMount() {
		DroneClientConfig config = DroneClientConfig.defaults();

		assertSightlineCameraDefaults(config);
		assertEquals(0.0f, config.cameraDynamicFovDegrees(), 1.0e-4f);
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
		assertTrue(config.gamepadDeadband() >= 0.08f);
	}

	@Test
	void leftStickAxisSwapCorrectsYawThrottleAndClearsAxisSpecificCalibration() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setYawAxis(1);
		config.setThrottleAxis(0);
		config.setStickCenters(0.0f, 0.0f, 0.11f);
		config.setThrottleCalibration(0.18f, 0.82f);

		config.swapYawThrottleAxes();

		assertEquals(0, config.yawAxis());
		assertEquals(1, config.throttleAxis());
		assertEquals(0.0f, config.yawCenter(), 1.0e-5f);
		assertFalse(config.throttleCalibrated());
		assertEquals(0.0f, config.throttleCalibrationMin(), 1.0e-5f);
		assertEquals(1.0f, config.throttleCalibrationMax(), 1.0e-5f);
	}

	@Test
	void rightStickAxisSwapKeepsCentersAttachedToPhysicalAxes() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setStickCenters(0.18f, -0.12f, 0.0f);

		config.swapRollPitchAxes();

		assertEquals(3, config.rollAxis());
		assertEquals(2, config.pitchAxis());
		assertEquals(-0.12f, config.rollCenter(), 1.0e-5f);
		assertEquals(0.18f, config.pitchCenter(), 1.0e-5f);
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

		assertSightlineCameraDefaults(config);
		assertEquals(0.0f, config.cameraDynamicFovDegrees(), 1.0e-4f);
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

		assertSightlineCameraDefaults(config);
		assertEquals(0.0f, config.cameraDynamicFovDegrees(), 1.0e-4f);
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

		assertSightlineCameraDefaults(config);
		assertEquals(0.0f, config.cameraDynamicFovDegrees(), 1.0e-4f);
	}

	@Test
	void previousUnblockedCameraDefaultsMigrateFartherForward() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "cameraTiltDegrees", 14.0f);
		setFloat(config, "cameraForwardOffsetMeters", 1.05f);
		setFloat(config, "cameraUpOffsetMeters", 0.62f);
		setFloat(config, "cameraVibrationScale", 0.12f);
		setFloat(config, "cameraRollingShutterScale", 0.06f);
		setFloat(config, "cameraLatencySeconds", 0.018f);
		setFloat(config, "cameraFovDegrees", 112.0f);
		setFloat(config, "cameraDynamicFovDegrees", 1.0f);

		config = normalize(config);

		assertSightlineCameraDefaults(config);
	}

	@Test
	void previousSightlineCameraDefaultsMigrateFartherForwardAndCalmer() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "cameraTiltDegrees", 14.0f);
		setFloat(config, "cameraForwardOffsetMeters", 1.12f);
		setFloat(config, "cameraUpOffsetMeters", 0.68f);
		setFloat(config, "cameraVibrationScale", 0.12f);
		setFloat(config, "cameraRollingShutterScale", 0.06f);
		setFloat(config, "cameraLatencySeconds", 0.018f);
		setFloat(config, "cameraFovDegrees", 112.0f);
		setFloat(config, "cameraDynamicFovDegrees", 1.0f);

		config = normalize(config);

		assertSightlineCameraDefaults(config);
	}

	@Test
	void previousSightlineCameraLatencyMigratesToZeroDelay() throws ReflectiveOperationException {
		DroneClientConfig config = DroneClientConfig.defaults();
		setFloat(config, "cameraLatencySeconds", 0.006f);

		config = normalize(config);

		assertSightlineCameraDefaults(config);
	}

	private static DroneClientConfig normalize(DroneClientConfig config) throws ReflectiveOperationException {
		Method normalized = DroneClientConfig.class.getDeclaredMethod("normalized");
		normalized.setAccessible(true);
		return (DroneClientConfig) normalized.invoke(config);
	}

	private static void assertTrainingFeel(DroneClientConfig config) {
		assertEquals(0.60f, config.gamepadExpo(), 1.0e-4f);
		assertEquals(0.86f, config.gamepadRollPitchRateScale(), 1.0e-4f);
		assertEquals(0.95f, config.gamepadYawRateScale(), 1.0e-4f);
		assertEquals(0.16f, config.gamepadAxisRisePerTick(), 1.0e-4f);
		assertEquals(0.45f, config.gamepadAxisFallPerTick(), 1.0e-4f);
	}

	private static void assertAcroFeel(DroneClientConfig config) {
		assertEquals(0.52f, config.gamepadExpo(), 1.0e-4f);
		assertEquals(1.00f, config.gamepadRollPitchRateScale(), 1.0e-4f);
		assertEquals(1.00f, config.gamepadYawRateScale(), 1.0e-4f);
		assertEquals(0.34f, config.gamepadAxisRisePerTick(), 1.0e-4f);
		assertEquals(0.70f, config.gamepadAxisFallPerTick(), 1.0e-4f);
	}

	private static void assertSightlineCameraDefaults(DroneClientConfig config) {
		assertEquals(16.0f, config.cameraTiltDegrees(), 1.0e-4f);
		assertEquals(1.20f, config.cameraForwardOffsetMeters(), 1.0e-4f);
		assertEquals(0.72f, config.cameraUpOffsetMeters(), 1.0e-4f);
		assertEquals(0.02f, config.cameraVibrationScale(), 1.0e-4f);
		assertEquals(0.0f, config.cameraRollingShutterScale(), 1.0e-4f);
		assertEquals(0.0f, config.cameraLatencySeconds(), 1.0e-4f);
		assertEquals(96.0f, config.cameraFovDegrees(), 1.0e-4f);
	}

	private static void setGamepadDeadband(DroneClientConfig config, float value) throws ReflectiveOperationException {
		setFloat(config, "gamepadDeadband", value);
	}

	private static void setFloat(DroneClientConfig config, String fieldName, float value) throws ReflectiveOperationException {
		Field field = DroneClientConfig.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setFloat(config, value);
	}

	private static void setObject(DroneClientConfig config, String fieldName, Object value) throws ReflectiveOperationException {
		Field field = DroneClientConfig.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(config, value);
	}
}
