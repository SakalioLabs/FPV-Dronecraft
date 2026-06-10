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

import net.fabricmc.loader.api.FabricLoader;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class DroneClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "fpvdrone-client.json";

	private boolean gamepadEnabled = true;
	private int rollAxis = 0;
	private int pitchAxis = 1;
	private int yawAxis = 2;
	private int throttleAxis = 3;
	private boolean rollInverted;
	private boolean pitchInverted = true;
	private boolean yawInverted;
	private boolean throttleInverted = true;
	private float gamepadDeadband = 0.06f;
	private float cameraTiltDegrees = 25.0f;
	private float cameraForwardOffsetMeters = 0.16f;
	private float cameraUpOffsetMeters = 0.16f;
	private float cameraVibrationScale = 1.0f;
	private float cameraFovDegrees = 105.0f;
	private float cameraDynamicFovDegrees = 6.0f;

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

	public float cameraFovDegrees() {
		return cameraFovDegrees;
	}

	public float cameraDynamicFovDegrees() {
		return cameraDynamicFovDegrees;
	}

	public boolean hasRequiredAxes(int axisCount) {
		return axisCount > rollAxis
				&& axisCount > pitchAxis
				&& axisCount > yawAxis
				&& axisCount > throttleAxis;
	}

	private DroneClientConfig normalized() {
		rollAxis = sanitizeAxis(rollAxis);
		pitchAxis = sanitizeAxis(pitchAxis);
		yawAxis = sanitizeAxis(yawAxis);
		throttleAxis = sanitizeAxis(throttleAxis);
		if (!Float.isFinite(gamepadDeadband)) {
			gamepadDeadband = 0.06f;
		}
		gamepadDeadband = Math.max(0.0f, Math.min(0.4f, gamepadDeadband));
		if (!Float.isFinite(cameraTiltDegrees)) {
			cameraTiltDegrees = 25.0f;
		}
		if (!Float.isFinite(cameraForwardOffsetMeters)) {
			cameraForwardOffsetMeters = 0.16f;
		}
		if (!Float.isFinite(cameraUpOffsetMeters)) {
			cameraUpOffsetMeters = 0.16f;
		}
		if (!Float.isFinite(cameraVibrationScale)) {
			cameraVibrationScale = 1.0f;
		}
		if (!Float.isFinite(cameraFovDegrees)) {
			cameraFovDegrees = 105.0f;
		}
		if (!Float.isFinite(cameraDynamicFovDegrees)) {
			cameraDynamicFovDegrees = 6.0f;
		}
		cameraTiltDegrees = Math.max(-15.0f, Math.min(70.0f, cameraTiltDegrees));
		cameraForwardOffsetMeters = Math.max(-0.20f, Math.min(0.80f, cameraForwardOffsetMeters));
		cameraUpOffsetMeters = Math.max(-0.20f, Math.min(0.60f, cameraUpOffsetMeters));
		cameraVibrationScale = Math.max(0.0f, Math.min(2.0f, cameraVibrationScale));
		cameraFovDegrees = Math.max(70.0f, Math.min(130.0f, cameraFovDegrees));
		cameraDynamicFovDegrees = Math.max(0.0f, Math.min(25.0f, cameraDynamicFovDegrees));
		return this;
	}

	private static int sanitizeAxis(int axis) {
		return Math.max(0, Math.min(31, axis));
	}
}
