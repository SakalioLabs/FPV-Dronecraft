package com.tenicana.dronecraft.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.util.Mth;

import net.fabricmc.loader.api.FabricLoader;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class DroneClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "fpvdrone-client.json";
	private static final float MIN_THROTTLE_CALIBRATION_SPAN = 0.05f;
	private static final float AXIS_DETECTION_THRESHOLD = 0.05f;
	private static final float DEFAULT_GAMEPAD_DEADBAND = 0.10f;
	private static final float DEFAULT_GAMEPAD_EXPO = 0.97f;
	private static final float DEFAULT_GAMEPAD_ROLL_PITCH_RATE_SCALE = 0.72f;
	private static final float DEFAULT_GAMEPAD_YAW_RATE_SCALE = 0.70f;
	private static final float DEFAULT_GAMEPAD_AXIS_RISE_PER_TICK = 0.075f;
	private static final float DEFAULT_GAMEPAD_AXIS_FALL_PER_TICK = 0.18f;
	private static final float DEFAULT_CAMERA_TILT_DEGREES = 14.0f;
	private static final float DEFAULT_CAMERA_FORWARD_OFFSET_METERS = 1.05f;
	private static final float DEFAULT_CAMERA_UP_OFFSET_METERS = 0.62f;
	private static final float DEFAULT_CAMERA_VIBRATION_SCALE = 0.12f;
	private static final float DEFAULT_CAMERA_ROLLING_SHUTTER_SCALE = 0.06f;
	private static final float DEFAULT_CAMERA_LATENCY_SECONDS = 0.018f;
	private static final float DEFAULT_CAMERA_FOV_DEGREES = 112.0f;
	private static final float DEFAULT_CAMERA_DYNAMIC_FOV_DEGREES = 1.0f;
	private static final float CLEAR_CAMERA_TILT_DEGREES = 16.0f;
	private static final float CLEAR_CAMERA_FORWARD_OFFSET_METERS = 0.82f;
	private static final float CLEAR_CAMERA_UP_OFFSET_METERS = 0.52f;
	private static final float CLEAR_CAMERA_VIBRATION_SCALE = 0.16f;
	private static final float CLEAR_CAMERA_ROLLING_SHUTTER_SCALE = 0.08f;
	private static final float CLEAR_CAMERA_LATENCY_SECONDS = 0.018f;
	private static final float CLEAR_CAMERA_FOV_DEGREES = 118.0f;
	private static final float CLEAR_CAMERA_DYNAMIC_FOV_DEGREES = 1.5f;
	private static final float RECENT_CAMERA_TILT_DEGREES = 18.0f;
	private static final float RECENT_CAMERA_FORWARD_OFFSET_METERS = 0.68f;
	private static final float RECENT_CAMERA_UP_OFFSET_METERS = 0.34f;
	private static final float RECENT_CAMERA_VIBRATION_SCALE = 0.22f;
	private static final float RECENT_CAMERA_ROLLING_SHUTTER_SCALE = 0.12f;
	private static final float RECENT_CAMERA_LATENCY_SECONDS = 0.018f;
	private static final float RECENT_CAMERA_FOV_DEGREES = 118.0f;
	private static final float RECENT_CAMERA_DYNAMIC_FOV_DEGREES = 2.0f;
	private static final float PREVIOUS_CAMERA_TILT_DEGREES = 18.0f;
	private static final float PREVIOUS_CAMERA_FORWARD_OFFSET_METERS = 0.42f;
	private static final float PREVIOUS_CAMERA_UP_OFFSET_METERS = 0.24f;
	private static final float PREVIOUS_CAMERA_VIBRATION_SCALE = 0.45f;
	private static final float PREVIOUS_CAMERA_ROLLING_SHUTTER_SCALE = 0.25f;
	private static final float PREVIOUS_CAMERA_LATENCY_SECONDS = 0.025f;
	private static final float PREVIOUS_CAMERA_FOV_DEGREES = 115.0f;
	private static final float PREVIOUS_CAMERA_DYNAMIC_FOV_DEGREES = 3.0f;
	private static final float LEGACY_CAMERA_TILT_DEGREES = 25.0f;
	private static final float LEGACY_CAMERA_FORWARD_OFFSET_METERS = 0.16f;
	private static final float LEGACY_CAMERA_UP_OFFSET_METERS = 0.16f;
	private static final float LEGACY_CAMERA_VIBRATION_SCALE = 1.0f;
	private static final float LEGACY_CAMERA_ROLLING_SHUTTER_SCALE = 0.55f;
	private static final float LEGACY_CAMERA_LATENCY_SECONDS = 0.035f;
	private static final float LEGACY_CAMERA_FOV_DEGREES = 105.0f;
	private static final float LEGACY_CAMERA_DYNAMIC_FOV_DEGREES = 6.0f;

	private boolean gamepadEnabled = true;
	private int rollAxis = 2;
	private int pitchAxis = 3;
	private int yawAxis = 0;
	private int throttleAxis = 1;
	private boolean rollInverted;
	private boolean pitchInverted = true;
	private boolean yawInverted;
	private boolean throttleInverted = true;
	private float gamepadDeadband = DEFAULT_GAMEPAD_DEADBAND;
	private float gamepadExpo = DEFAULT_GAMEPAD_EXPO;
	private float gamepadRollPitchRateScale = DEFAULT_GAMEPAD_ROLL_PITCH_RATE_SCALE;
	private float gamepadYawRateScale = DEFAULT_GAMEPAD_YAW_RATE_SCALE;
	private float gamepadAxisRisePerTick = DEFAULT_GAMEPAD_AXIS_RISE_PER_TICK;
	private float gamepadAxisFallPerTick = DEFAULT_GAMEPAD_AXIS_FALL_PER_TICK;
	private int armButton = -1;
	private int disarmButton = -1;
	private int throttleCalibrateButton = -1;
	private boolean throttleCalibrated = true;
	private float throttleCalibrationMin = 0.0f;
	private float throttleCalibrationMax = 1.0f;
	private float cameraTiltDegrees = DEFAULT_CAMERA_TILT_DEGREES;
	private float cameraForwardOffsetMeters = DEFAULT_CAMERA_FORWARD_OFFSET_METERS;
	private float cameraUpOffsetMeters = DEFAULT_CAMERA_UP_OFFSET_METERS;
	private float cameraVibrationScale = DEFAULT_CAMERA_VIBRATION_SCALE;
	private float cameraRollingShutterScale = DEFAULT_CAMERA_ROLLING_SHUTTER_SCALE;
	private float cameraLatencySeconds = DEFAULT_CAMERA_LATENCY_SECONDS;
	private float cameraFovDegrees = DEFAULT_CAMERA_FOV_DEGREES;
	private float cameraDynamicFovDegrees = DEFAULT_CAMERA_DYNAMIC_FOV_DEGREES;

	public static DroneClientConfig defaults() {
		return new DroneClientConfig().normalized();
	}

	public static DroneClientConfig load() {
		Path path = path();
		if (!Files.exists(path)) {
			DroneClientConfig config = defaults();
			config.save();
			return config;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			DroneClientConfig config = GSON.fromJson(reader, DroneClientConfig.class);
			if (config == null) {
				config = defaults();
			}
			config.normalized().save();
			return config;
		} catch (IOException | JsonSyntaxException exception) {
			FpvDronecraftMod.LOGGER.warn("Failed to load FPV Dronecraft client config from {}", path, exception);
			return defaults();
		}
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(normalized(), writer);
			}
		} catch (IOException exception) {
			FpvDronecraftMod.LOGGER.warn("Failed to save FPV Dronecraft client config to {}", path, exception);
		}
	}

	public boolean gamepadEnabled() {
		return gamepadEnabled;
	}

	public void setGamepadEnabled(boolean gamepadEnabled) {
		this.gamepadEnabled = gamepadEnabled;
	}

	public int rollAxis() {
		return rollAxis;
	}

	public int pitchAxis() {
		return pitchAxis;
	}

	public int yawAxis() {
		return yawAxis;
	}

	public int throttleAxis() {
		return throttleAxis;
	}

	public boolean rollInverted() {
		return rollInverted;
	}

	public boolean pitchInverted() {
		return pitchInverted;
	}

	public boolean yawInverted() {
		return yawInverted;
	}

	public boolean throttleInverted() {
		return throttleInverted;
	}

	public float gamepadDeadband() {
		return gamepadDeadband;
	}

	public float gamepadExpo() {
		return gamepadExpo;
	}

	public float gamepadRollPitchRateScale() {
		return gamepadRollPitchRateScale;
	}

	public float gamepadYawRateScale() {
		return gamepadYawRateScale;
	}

	public float gamepadAxisRisePerTick() {
		return gamepadAxisRisePerTick;
	}

	public float gamepadAxisFallPerTick() {
		return gamepadAxisFallPerTick;
	}

	public int armButton() {
		return armButton;
	}

	public void setArmButton(int armButton) {
		this.armButton = sanitizeButton(armButton);
	}

	public int disarmButton() {
		return disarmButton;
	}

	public void setDisarmButton(int disarmButton) {
		this.disarmButton = sanitizeButton(disarmButton);
	}

	public int throttleCalibrateButton() {
		return throttleCalibrateButton;
	}

	public boolean hasArmControlsConfigured() {
		return armButton >= 0 || disarmButton >= 0;
	}

	public void setThrottleCalibrateButton(int throttleCalibrateButton) {
		this.throttleCalibrateButton = sanitizeButton(throttleCalibrateButton);
	}

	public void setRollAxis(int rollAxis) {
		this.rollAxis = sanitizeAxis(rollAxis);
	}

	public void setPitchAxis(int pitchAxis) {
		this.pitchAxis = sanitizeAxis(pitchAxis);
	}

	public void setYawAxis(int yawAxis) {
		this.yawAxis = sanitizeAxis(yawAxis);
	}

	public void setThrottleAxis(int throttleAxis) {
		this.throttleAxis = sanitizeAxis(throttleAxis);
	}

	public void setRollInverted(boolean rollInverted) {
		this.rollInverted = rollInverted;
	}

	public void setPitchInverted(boolean pitchInverted) {
		this.pitchInverted = pitchInverted;
	}

	public void setYawInverted(boolean yawInverted) {
		this.yawInverted = yawInverted;
	}

	public void setThrottleInverted(boolean throttleInverted) {
		this.throttleInverted = throttleInverted;
	}

	public void setGamepadExpo(float gamepadExpo) {
		this.gamepadExpo = gamepadExpo;
	}

	public void setGamepadRollPitchRateScale(float gamepadRollPitchRateScale) {
		this.gamepadRollPitchRateScale = gamepadRollPitchRateScale;
	}

	public void setGamepadYawRateScale(float gamepadYawRateScale) {
		this.gamepadYawRateScale = gamepadYawRateScale;
	}

	public void setGamepadAxisRisePerTick(float gamepadAxisRisePerTick) {
		this.gamepadAxisRisePerTick = gamepadAxisRisePerTick;
	}

	public void setGamepadAxisFallPerTick(float gamepadAxisFallPerTick) {
		this.gamepadAxisFallPerTick = gamepadAxisFallPerTick;
	}

	public boolean throttleCalibrated() {
		return throttleCalibrated;
	}

	public float throttleCalibrationMin() {
		return throttleCalibrationMin;
	}

	public float throttleCalibrationMax() {
		return throttleCalibrationMax;
	}

	public float calibrateThrottle(float rawThrottle) {
		if (!throttleCalibrated || throttleCalibrationMax <= throttleCalibrationMin + MIN_THROTTLE_CALIBRATION_SPAN) {
			float endpointSnap = gamepadDeadband() * 0.5f;
			if (rawThrottle <= endpointSnap) {
				return 0.0f;
			}
			if (rawThrottle >= 1.0f - endpointSnap) {
				return 1.0f;
			}
			return rawThrottle;
		}
		float normalized = (float) ((rawThrottle - throttleCalibrationMin) / (throttleCalibrationMax - throttleCalibrationMin));
		return (float) Mth.clamp(normalized, 0.0, 1.0);
	}

	public void setThrottleCalibration(float min, float max) {
		if (!Float.isFinite(min) || !Float.isFinite(max)) {
			return;
		}
		float normalizedMin = Math.max(0.0f, Math.min(1.0f, min));
		float normalizedMax = Math.max(0.0f, Math.min(1.0f, max));
		if (normalizedMax < normalizedMin) {
			float temp = normalizedMin;
			normalizedMin = normalizedMax;
			normalizedMax = temp;
		}
		float span = normalizedMax - normalizedMin;
		if (span < MIN_THROTTLE_CALIBRATION_SPAN) {
			return;
		}
		throttleCalibrationMin = normalizedMin;
		throttleCalibrationMax = normalizedMax;
		throttleCalibrated = true;
	}

	public void clearThrottleCalibration() {
		throttleCalibrated = false;
		throttleCalibrationMin = 0.0f;
		throttleCalibrationMax = 1.0f;
	}

	public float cameraTiltDegrees() {
		return cameraTiltDegrees;
	}

	public float cameraForwardOffsetMeters() {
		return cameraForwardOffsetMeters;
	}

	public float cameraUpOffsetMeters() {
		return cameraUpOffsetMeters;
	}

	public float cameraVibrationScale() {
		return cameraVibrationScale;
	}

	public float cameraRollingShutterScale() {
		return cameraRollingShutterScale;
	}

	public float cameraLatencySeconds() {
		return cameraLatencySeconds;
	}

	public float cameraFovDegrees() {
		return cameraFovDegrees;
	}

	public float cameraDynamicFovDegrees() {
		return cameraDynamicFovDegrees;
	}

	public float axisDetectionThreshold() {
		return AXIS_DETECTION_THRESHOLD;
	}

	public boolean hasRequiredAxes(int axisCount) {
		return axisCount > rollAxis
				&& axisCount > pitchAxis
				&& axisCount > yawAxis
				&& axisCount > throttleAxis;
	}

	private DroneClientConfig normalized() {
		migrateLegacySwappedMode2Defaults();
		rollAxis = sanitizeAxis(rollAxis);
		pitchAxis = sanitizeAxis(pitchAxis);
		yawAxis = sanitizeAxis(yawAxis);
		throttleAxis = sanitizeAxis(throttleAxis);
		armButton = sanitizeButton(armButton);
		disarmButton = sanitizeButton(disarmButton);
		throttleCalibrateButton = sanitizeButton(throttleCalibrateButton);
		if (!Float.isFinite(throttleCalibrationMin)) {
			throttleCalibrationMin = 0.0f;
		}
		if (!Float.isFinite(throttleCalibrationMax)) {
			throttleCalibrationMax = 1.0f;
		}
		if (throttleCalibrationMax < throttleCalibrationMin) {
			float temp = throttleCalibrationMin;
			throttleCalibrationMin = throttleCalibrationMax;
			throttleCalibrationMax = temp;
		}
		if (throttleCalibrationMax - throttleCalibrationMin < MIN_THROTTLE_CALIBRATION_SPAN) {
			throttleCalibrationMin = 0.0f;
			throttleCalibrationMax = 1.0f;
			throttleCalibrated = false;
		}
		if (!Float.isFinite(gamepadDeadband)) {
			gamepadDeadband = DEFAULT_GAMEPAD_DEADBAND;
		}
		gamepadDeadband = Math.max(0.0f, Math.min(0.4f, gamepadDeadband));
		if (!Float.isFinite(gamepadExpo) || gamepadExpo <= 0.0f) {
			gamepadExpo = DEFAULT_GAMEPAD_EXPO;
		}
		if (!Float.isFinite(gamepadRollPitchRateScale) || gamepadRollPitchRateScale <= 0.0f) {
			gamepadRollPitchRateScale = DEFAULT_GAMEPAD_ROLL_PITCH_RATE_SCALE;
		}
		if (!Float.isFinite(gamepadYawRateScale) || gamepadYawRateScale <= 0.0f) {
			gamepadYawRateScale = DEFAULT_GAMEPAD_YAW_RATE_SCALE;
		}
		if (!Float.isFinite(gamepadAxisRisePerTick) || gamepadAxisRisePerTick <= 0.0f) {
			gamepadAxisRisePerTick = DEFAULT_GAMEPAD_AXIS_RISE_PER_TICK;
		}
		if (!Float.isFinite(gamepadAxisFallPerTick) || gamepadAxisFallPerTick <= 0.0f) {
			gamepadAxisFallPerTick = DEFAULT_GAMEPAD_AXIS_FALL_PER_TICK;
		}
		gamepadExpo = Math.max(0.0f, Math.min(1.0f, gamepadExpo));
		gamepadRollPitchRateScale = Math.max(0.20f, Math.min(1.0f, gamepadRollPitchRateScale));
		gamepadYawRateScale = Math.max(0.20f, Math.min(1.0f, gamepadYawRateScale));
		gamepadAxisRisePerTick = Math.max(0.01f, Math.min(1.0f, gamepadAxisRisePerTick));
		gamepadAxisFallPerTick = Math.max(0.01f, Math.min(1.0f, gamepadAxisFallPerTick));
		if (!Float.isFinite(cameraTiltDegrees)) {
			cameraTiltDegrees = DEFAULT_CAMERA_TILT_DEGREES;
		}
		if (!Float.isFinite(cameraForwardOffsetMeters)) {
			cameraForwardOffsetMeters = DEFAULT_CAMERA_FORWARD_OFFSET_METERS;
		}
		if (!Float.isFinite(cameraUpOffsetMeters)) {
			cameraUpOffsetMeters = DEFAULT_CAMERA_UP_OFFSET_METERS;
		}
		if (!Float.isFinite(cameraVibrationScale)) {
			cameraVibrationScale = DEFAULT_CAMERA_VIBRATION_SCALE;
		}
		if (!Float.isFinite(cameraRollingShutterScale)) {
			cameraRollingShutterScale = DEFAULT_CAMERA_ROLLING_SHUTTER_SCALE;
		}
		if (!Float.isFinite(cameraLatencySeconds)) {
			cameraLatencySeconds = DEFAULT_CAMERA_LATENCY_SECONDS;
		}
		if (!Float.isFinite(cameraFovDegrees)) {
			cameraFovDegrees = DEFAULT_CAMERA_FOV_DEGREES;
		}
		if (!Float.isFinite(cameraDynamicFovDegrees)) {
			cameraDynamicFovDegrees = DEFAULT_CAMERA_DYNAMIC_FOV_DEGREES;
		}
		migrateLegacyBlockedFpvCameraDefaults();
		cameraTiltDegrees = Math.max(-15.0f, Math.min(70.0f, cameraTiltDegrees));
		cameraForwardOffsetMeters = Math.max(-0.20f, Math.min(1.20f, cameraForwardOffsetMeters));
		cameraUpOffsetMeters = Math.max(-0.20f, Math.min(0.80f, cameraUpOffsetMeters));
		cameraVibrationScale = Math.max(0.0f, Math.min(2.0f, cameraVibrationScale));
		cameraRollingShutterScale = Math.max(0.0f, Math.min(2.0f, cameraRollingShutterScale));
		cameraLatencySeconds = Math.max(0.0f, Math.min(0.20f, cameraLatencySeconds));
		cameraFovDegrees = Math.max(70.0f, Math.min(130.0f, cameraFovDegrees));
		cameraDynamicFovDegrees = Math.max(0.0f, Math.min(25.0f, cameraDynamicFovDegrees));
		return this;
	}

	private void migrateLegacySwappedMode2Defaults() {
		if (rollAxis == 0
				&& pitchAxis == 1
				&& yawAxis == 3
				&& throttleAxis == 2
				&& !rollInverted
				&& pitchInverted
				&& !yawInverted
				&& throttleInverted) {
			rollAxis = 2;
			pitchAxis = 3;
			yawAxis = 0;
			throttleAxis = 1;
			if (gamepadDeadband < 0.10f) {
				gamepadDeadband = DEFAULT_GAMEPAD_DEADBAND;
			}
		}
	}

	private void migrateLegacyBlockedFpvCameraDefaults() {
		boolean legacyCamera = nearly(cameraTiltDegrees, LEGACY_CAMERA_TILT_DEGREES)
				&& nearly(cameraForwardOffsetMeters, LEGACY_CAMERA_FORWARD_OFFSET_METERS)
				&& nearly(cameraUpOffsetMeters, LEGACY_CAMERA_UP_OFFSET_METERS)
				&& nearly(cameraVibrationScale, LEGACY_CAMERA_VIBRATION_SCALE)
				&& nearly(cameraRollingShutterScale, LEGACY_CAMERA_ROLLING_SHUTTER_SCALE)
				&& nearly(cameraLatencySeconds, LEGACY_CAMERA_LATENCY_SECONDS)
				&& nearly(cameraFovDegrees, LEGACY_CAMERA_FOV_DEGREES)
				&& nearly(cameraDynamicFovDegrees, LEGACY_CAMERA_DYNAMIC_FOV_DEGREES);
		boolean previousDefaultCamera = nearly(cameraTiltDegrees, PREVIOUS_CAMERA_TILT_DEGREES)
				&& nearly(cameraForwardOffsetMeters, PREVIOUS_CAMERA_FORWARD_OFFSET_METERS)
				&& nearly(cameraUpOffsetMeters, PREVIOUS_CAMERA_UP_OFFSET_METERS)
				&& nearly(cameraVibrationScale, PREVIOUS_CAMERA_VIBRATION_SCALE)
				&& nearly(cameraRollingShutterScale, PREVIOUS_CAMERA_ROLLING_SHUTTER_SCALE)
				&& nearly(cameraLatencySeconds, PREVIOUS_CAMERA_LATENCY_SECONDS)
				&& nearly(cameraFovDegrees, PREVIOUS_CAMERA_FOV_DEGREES)
				&& nearly(cameraDynamicFovDegrees, PREVIOUS_CAMERA_DYNAMIC_FOV_DEGREES);
		boolean recentDefaultCamera = nearly(cameraTiltDegrees, RECENT_CAMERA_TILT_DEGREES)
				&& nearly(cameraForwardOffsetMeters, RECENT_CAMERA_FORWARD_OFFSET_METERS)
				&& nearly(cameraUpOffsetMeters, RECENT_CAMERA_UP_OFFSET_METERS)
				&& nearly(cameraVibrationScale, RECENT_CAMERA_VIBRATION_SCALE)
				&& nearly(cameraRollingShutterScale, RECENT_CAMERA_ROLLING_SHUTTER_SCALE)
				&& nearly(cameraLatencySeconds, RECENT_CAMERA_LATENCY_SECONDS)
				&& nearly(cameraFovDegrees, RECENT_CAMERA_FOV_DEGREES)
				&& nearly(cameraDynamicFovDegrees, RECENT_CAMERA_DYNAMIC_FOV_DEGREES);
		boolean clearDefaultCamera = nearly(cameraTiltDegrees, CLEAR_CAMERA_TILT_DEGREES)
				&& nearly(cameraForwardOffsetMeters, CLEAR_CAMERA_FORWARD_OFFSET_METERS)
				&& nearly(cameraUpOffsetMeters, CLEAR_CAMERA_UP_OFFSET_METERS)
				&& nearly(cameraVibrationScale, CLEAR_CAMERA_VIBRATION_SCALE)
				&& nearly(cameraRollingShutterScale, CLEAR_CAMERA_ROLLING_SHUTTER_SCALE)
				&& nearly(cameraLatencySeconds, CLEAR_CAMERA_LATENCY_SECONDS)
				&& nearly(cameraFovDegrees, CLEAR_CAMERA_FOV_DEGREES)
				&& nearly(cameraDynamicFovDegrees, CLEAR_CAMERA_DYNAMIC_FOV_DEGREES);
		if (!legacyCamera && !previousDefaultCamera && !recentDefaultCamera && !clearDefaultCamera) {
			return;
		}
		cameraTiltDegrees = DEFAULT_CAMERA_TILT_DEGREES;
		cameraForwardOffsetMeters = DEFAULT_CAMERA_FORWARD_OFFSET_METERS;
		cameraUpOffsetMeters = DEFAULT_CAMERA_UP_OFFSET_METERS;
		cameraVibrationScale = DEFAULT_CAMERA_VIBRATION_SCALE;
		cameraRollingShutterScale = DEFAULT_CAMERA_ROLLING_SHUTTER_SCALE;
		cameraLatencySeconds = DEFAULT_CAMERA_LATENCY_SECONDS;
		cameraFovDegrees = DEFAULT_CAMERA_FOV_DEGREES;
		cameraDynamicFovDegrees = DEFAULT_CAMERA_DYNAMIC_FOV_DEGREES;
	}

	private static boolean nearly(float value, float expected) {
		return Math.abs(value - expected) <= 1.0e-4f;
	}

	private static int sanitizeAxis(int axis) {
		return Math.max(0, Math.min(31, axis));
	}

	private static int sanitizeButton(int button) {
		return Math.max(-1, Math.min(31, button));
	}
}
