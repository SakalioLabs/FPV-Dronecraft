package com.tenicana.dronecraft.client.hud;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.DroneClientState.HudMode;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneHud {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "flight_hud");
	private static final int TEXT = 0xFFF1F7F7;
	private static final int MUTED = 0xFF9FB3B8;
	private static final int PRIMARY = 0xFF00E6A8;
	private static final int WARNING = 0xFFFFC14D;
	private static final int DANGER = 0xFFFF5E5E;
	private static final int PANEL = 0xA0101518;
	private static final int LINE = 0xAA7BE7D6;
	private static final int SHADOW = 0x70000000;
	private static final int MARGIN = 8;
	private static final float RPM_ACTIVE_THROTTLE_THRESHOLD = 0.06f;
	private static final float RPM_SPINNING_THRESHOLD = 1000.0f;

	private DroneHud() {
	}

	public static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HUD_ID, DroneHud::render);
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		HudMode hudMode = DroneClientState.hudMode();
		if (client.player == null || client.options.hideGui || hudMode == HudMode.OFF) {
			return;
		}
		DroneEntity drone = DroneClientState.controlledDrone();
		if (!DroneClientState.hasController() && !DroneClientState.isFpvActive() && drone == null) {
			return;
		}

		Font font = client.font;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();

		Telemetry telemetry = Telemetry.from(client, drone);
		if (hudMode == HudMode.MINIMAL) {
			drawMinimalOsd(graphics, font, screenWidth, screenHeight, telemetry);
			return;
		}

		drawCompactStatus(graphics, font, screenWidth, telemetry);
		if (DroneClientState.isFpvActive()) {
			drawAttitude(graphics, screenWidth / 2, screenHeight / 2, telemetry);
			drawSideScales(graphics, font, screenWidth, screenHeight, telemetry);
		}
		drawCompactTelemetry(graphics, font, screenWidth, screenHeight, telemetry);
	}

	private static void drawMinimalOsd(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, Telemetry telemetry) {
		Component mode = Component.translatable("hud.fpvdrone.mode_value", telemetry.mode().name());
		Component view = Component.translatable(telemetry.fpvView() ? "hud.fpvdrone.view_fpv" : "hud.fpvdrone.view_los");
		Component armed = Component.translatable(telemetry.armed() ? "hud.fpvdrone.armed" : "hud.fpvdrone.disarmed");
		Component link = Component.translatable(telemetry.linkOk() ? "hud.fpvdrone.link_ok" : "hud.fpvdrone.link_lost");
		Component throttle = Component.translatable("hud.fpvdrone.thr_short", percent(telemetry.throttle()));
		Component altitude = Component.translatable("hud.fpvdrone.alt_short", oneDecimal(telemetry.altitude()));
		Component speed = Component.translatable("hud.fpvdrone.spd_short", oneDecimal(telemetry.speed()));

		int y = MARGIN;
		int leftX = MARGIN;
		leftX = drawInline(graphics, font, mode, leftX, y, TEXT);
		leftX = drawInline(graphics, font, view, leftX, y, telemetry.fpvView() ? PRIMARY : MUTED);
		drawString(graphics, font, armed, leftX, y, telemetry.armed() ? PRIMARY : WARNING);
		if (!telemetry.linkOk() || telemetry.failsafe()) {
			drawString(graphics, font, link, MARGIN, y + 11, DANGER);
		}

		int rightX = screenWidth - MARGIN;
		rightX = drawRightInline(graphics, font, speed, rightX, y, TEXT);
		rightX = drawRightInline(graphics, font, altitude, rightX, y, TEXT);
		drawRightInline(graphics, font, throttle, rightX, y, TEXT);
		if (telemetry.fpvView()) {
			drawMinimalReticle(graphics, screenWidth / 2, screenHeight / 2, telemetry);
		}
	}

	private static void drawCompactStatus(GuiGraphics graphics, Font font, int screenWidth, Telemetry telemetry) {
		int y = MARGIN;
		Component mode = Component.translatable("hud.fpvdrone.mode_value", telemetry.mode().name());
		Component view = Component.translatable(telemetry.fpvView() ? "hud.fpvdrone.view_fpv" : "hud.fpvdrone.view_los");
		Component armed = Component.translatable(telemetry.armed() ? "hud.fpvdrone.armed" : "hud.fpvdrone.disarmed");
		Component link = Component.translatable(telemetry.linkOk() ? "hud.fpvdrone.link_ok" : "hud.fpvdrone.link_lost");
		Component battery = Component.translatable("hud.fpvdrone.battery_value", percent(telemetry.battery()));
		Component throttle = Component.translatable("hud.fpvdrone.throttle_value", percent(telemetry.throttle()));

		int leftWidth = font.width(mode) + font.width(view) + font.width(armed) + font.width(link) + 30;
		int rightWidth = font.width(battery) + font.width(throttle) + 18;
		graphics.fill(MARGIN - 3, y - 3, Math.min(screenWidth - MARGIN, MARGIN + leftWidth), y + 12, PANEL);
		graphics.fill(Math.max(MARGIN, screenWidth - MARGIN - rightWidth), y - 3, screenWidth - MARGIN + 3, y + 12, PANEL);
		int x = MARGIN;
		drawString(graphics, font, mode, x, y, TEXT);
		x += font.width(mode) + 8;
		drawString(graphics, font, view, x, y, telemetry.fpvView() ? PRIMARY : MUTED);
		x += font.width(view) + 8;
		drawString(graphics, font, armed, x, y, telemetry.armed() ? PRIMARY : WARNING);
		x += font.width(armed) + 8;
		drawString(graphics, font, link, x, y, telemetry.linkOk() ? PRIMARY : DANGER);

		int rightX = screenWidth - MARGIN;
		drawRight(graphics, font, throttle, rightX, y, TEXT);
		rightX -= font.width(throttle) + 10;
		drawRight(graphics, font, battery, rightX, y, batteryColor(telemetry));
	}

	private static void drawCompactTelemetry(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, Telemetry telemetry) {
		int y = screenHeight - MARGIN - 9;
		Component altitude = Component.translatable("hud.fpvdrone.alt_short", oneDecimal(telemetry.altitude()));
		Component speed = Component.translatable("hud.fpvdrone.spd_short", oneDecimal(telemetry.speed()));
		Component verticalSpeed = Component.translatable("hud.fpvdrone.vs_short", signedOneDecimal(telemetry.verticalSpeed()));
		Component rpm = Component.translatable("hud.fpvdrone.metric.rpm", compactRpm(telemetry));
		Component health = Component.translatable("hud.fpvdrone.metric.health", percent(Math.min(telemetry.frameHealth(), telemetry.rotorHealth())));
		int width = font.width(altitude) + font.width(speed) + font.width(verticalSpeed) + font.width(rpm) + font.width(health) + 42;
		int x = Math.max(MARGIN, (screenWidth - width) / 2);
		graphics.fill(x - 4, y - 3, Math.min(screenWidth - MARGIN, x + width + 4), y + 12, PANEL);
		drawString(graphics, font, altitude, x, y, TEXT);
		x += font.width(altitude) + 10;
		drawString(graphics, font, speed, x, y, TEXT);
		x += font.width(speed) + 10;
		drawString(graphics, font, verticalSpeed, x, y, verticalSpeedColor(telemetry));
		x += font.width(verticalSpeed) + 10;
		drawString(graphics, font, rpm, x, y, rpmColor(telemetry));
		x += font.width(rpm) + 10;
		drawString(graphics, font, health, x, y, healthColor(telemetry));
	}

	private static void drawAttitude(GuiGraphics graphics, int centerX, int centerY, Telemetry telemetry) {
		int radius = 42;
		int rollOffset = Math.round(Mth.clamp((float) Math.toDegrees(telemetry.roll()), -45.0f, 45.0f) * 0.46f);
		int pitchOffset = Math.round(Mth.clamp((float) Math.toDegrees(telemetry.pitch()), -35.0f, 35.0f) * 0.78f);
		int horizonY = centerY + pitchOffset;

		graphics.fill(centerX - radius, horizonY, centerX + radius, horizonY + 1, LINE);
		graphics.fill(centerX - 1, centerY - 26, centerX + 1, centerY + 27, SHADOW);
		graphics.fill(centerX - 14, centerY - 1, centerX - 5, centerY + 1, PRIMARY);
		graphics.fill(centerX + 5, centerY - 1, centerX + 14, centerY + 1, PRIMARY);
		graphics.fill(centerX - 1, centerY - 5, centerX + 1, centerY + 6, PRIMARY);

		for (int step = -3; step <= 3; step++) {
			if (step == 0) {
				continue;
			}
			int y = horizonY + step * 13;
			if (Math.abs(y - centerY) > 42) {
				continue;
			}
			int half = step % 2 == 0 ? 18 : 12;
			graphics.fill(centerX - rollOffset - half, y, centerX - rollOffset - 5, y + 1, MUTED);
			graphics.fill(centerX - rollOffset + 5, y, centerX - rollOffset + half, y + 1, MUTED);
		}

		graphics.fill(centerX - 2, centerY - radius - 4, centerX + 3, centerY - radius, telemetry.armed() ? PRIMARY : WARNING);
	}

	private static void drawMinimalReticle(GuiGraphics graphics, int centerX, int centerY, Telemetry telemetry) {
		int color = telemetry.armed() ? PRIMARY : WARNING;
		graphics.fill(centerX - 9, centerY, centerX - 3, centerY + 1, color);
		graphics.fill(centerX + 3, centerY, centerX + 10, centerY + 1, color);
		graphics.fill(centerX, centerY - 9, centerX + 1, centerY - 3, color);
		graphics.fill(centerX, centerY + 3, centerX + 1, centerY + 10, color);
	}

	private static void drawSideScales(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, Telemetry telemetry) {
		int centerY = screenHeight / 2;
		int scaleHeight = 70;
		int leftX = MARGIN + 4;
		int rightX = screenWidth - MARGIN - 42;
		int top = centerY - scaleHeight / 2;
		drawVerticalScale(graphics, leftX, top, 12, scaleHeight, telemetry.throttle(), PRIMARY);
		drawString(graphics, font, Component.translatable("hud.fpvdrone.thr_short", percent(telemetry.throttle())), leftX + 18, top + scaleHeight - 9, TEXT);

		float battery = Mth.clamp(telemetry.battery(), 0.0f, 1.0f);
		drawVerticalScale(graphics, rightX + 34, top, 12, scaleHeight, battery, batteryColor(telemetry));
		drawRight(graphics, font, Component.translatable("hud.fpvdrone.alt_short", oneDecimal(telemetry.altitude())), rightX + 28, top + 2, TEXT);
		drawRight(graphics, font, Component.translatable("hud.fpvdrone.spd_short", oneDecimal(telemetry.speed())), rightX + 28, top + 14, TEXT);
		drawRight(graphics, font, Component.translatable("hud.fpvdrone.vs_short", signedOneDecimal(telemetry.verticalSpeed())), rightX + 28, top + 26, verticalSpeedColor(telemetry));
	}

	private static void drawVerticalScale(GuiGraphics graphics, int x, int y, int width, int height, float value, int color) {
		float clamped = Mth.clamp(value, 0.0f, 1.0f);
		graphics.fill(x, y, x + width, y + height, PANEL);
		graphics.fill(x, y, x + 1, y + height, LINE);
		graphics.fill(x + width - 1, y, x + width, y + height, LINE);
		int filled = Math.round(height * clamped);
		graphics.fill(x + 2, y + height - filled, x + width - 2, y + height - 2, color);
	}

	private static int batteryColor(Telemetry telemetry) {
		if (telemetry.battery() < 0.18f) {
			return DANGER;
		}
		if (telemetry.battery() < 0.32f || telemetry.batteryVoltage() < 13.8f) {
			return WARNING;
		}
		return PRIMARY;
	}

	private static int verticalSpeedColor(Telemetry telemetry) {
		return telemetry.verticalSpeed() < -1.5f ? WARNING : TEXT;
	}

	private static int healthColor(Telemetry telemetry) {
		float health = Math.min(telemetry.frameHealth(), telemetry.rotorHealth());
		return health < 0.35f ? DANGER : (health < 0.70f ? WARNING : PRIMARY);
	}

	private static int rpmColor(Telemetry telemetry) {
		if (!telemetry.armed() || isIdleRpm(telemetry.armed(), telemetry.throttle(), telemetry.rpm())) {
			return MUTED;
		}
		return telemetry.rpm() > RPM_SPINNING_THRESHOLD ? PRIMARY : MUTED;
	}

	private static void drawRight(GuiGraphics graphics, Font font, Component component, int rightX, int y, int color) {
		graphics.drawString(font, component, rightX - font.width(component), y, color, false);
	}

	private static void drawString(GuiGraphics graphics, Font font, Component component, int x, int y, int color) {
		graphics.drawString(font, component, x, y, color, false);
	}

	private static int drawInline(GuiGraphics graphics, Font font, Component component, int x, int y, int color) {
		drawString(graphics, font, component, x, y, color);
		return x + font.width(component) + 8;
	}

	private static int drawRightInline(GuiGraphics graphics, Font font, Component component, int rightX, int y, int color) {
		drawRight(graphics, font, component, rightX, y, color);
		return rightX - font.width(component) - 10;
	}

	private static String percent(float value) {
		return String.format(Locale.ROOT, "%3.0f%%", Mth.clamp(value, 0.0f, 1.0f) * 100.0f);
	}

	private static String oneDecimal(float value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static String signedOneDecimal(float value) {
		return String.format(Locale.ROOT, "%+.1f", value);
	}

	private static String integer(float value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}

	private static Component compactRpm(Telemetry telemetry) {
		if (isIdleRpm(telemetry.armed(), telemetry.throttle(), telemetry.rpm())) {
			return Component.translatable("hud.fpvdrone.rpm_idle");
		}
		return Component.literal(compactRpmText(telemetry.armed(), telemetry.rpm()));
	}

	static boolean isIdleRpm(boolean armed, float throttle, float rpm) {
		float safeThrottle = Float.isFinite(throttle) ? Math.max(0.0f, throttle) : 0.0f;
		float safeRpm = sanitizedRpm(rpm);
		return armed
				&& safeThrottle <= RPM_ACTIVE_THROTTLE_THRESHOLD
				&& safeRpm > RPM_SPINNING_THRESHOLD;
	}

	static String compactRpmText(boolean armed, float rpm) {
		if (!armed) {
			return "0";
		}
		float safeRpm = sanitizedRpm(rpm);
		if (safeRpm >= RPM_SPINNING_THRESHOLD) {
			return String.format(Locale.ROOT, "%.1fk", safeRpm / 1000.0f);
		}
		return integer(safeRpm);
	}

	private static float sanitizedRpm(float rpm) {
		return Float.isFinite(rpm) ? Math.max(0.0f, rpm) : 0.0f;
	}

	private record Telemetry(
			boolean armed,
			boolean linkOk,
			boolean failsafe,
			boolean fpvView,
			FlightMode mode,
			float throttle,
			float pitch,
			float roll,
			float altitude,
			float speed,
			float verticalSpeed,
			float battery,
			float batteryVoltage,
			float rpm,
			float motorTemp,
			float escTemp,
			float frameHealth,
			float rotorHealth,
			float propwash,
			float vrs
	) {
		private static Telemetry from(Minecraft client, DroneEntity drone) {
			if (drone == null) {
				return new Telemetry(
						DroneClientState.armed(),
						false,
						false,
						DroneClientState.isFpvViewEnabled(),
						DroneClientState.flightMode(),
						DroneClientState.throttle(),
						0.0f,
						0.0f,
						(float) client.player.getY(),
						(float) client.player.getDeltaMovement().length(),
						0.0f,
						1.0f,
						16.8f,
						0.0f,
						25.0f,
						25.0f,
						1.0f,
						1.0f,
						0.0f,
						0.0f
				);
			}
			return new Telemetry(
					drone.isArmed(),
					drone.isRawControlLinkActive(),
					drone.isControlFailsafeActive(),
					DroneClientState.isFpvViewEnabled(),
					drone.getFlightMode(),
					drone.getControlThrottle(),
					drone.getRenderPitchRadians(),
					drone.getRenderRollRadians(),
					(float) drone.getY(),
					Math.max(drone.getSpeedMetersPerSecond(), drone.getAirspeedMetersPerSecond()),
					drone.getBarometerVerticalSpeedMetersPerSecond(),
					drone.getBatteryStateOfCharge(),
					drone.getBatteryVoltage(),
					drone.getAverageMotorRpm(),
					drone.getMotorTemperatureCelsius(),
					drone.getEscTemperatureCelsius(),
					drone.getFrameHealth(),
					drone.getRotorHealth(),
					drone.getPropwashIntensity(),
					drone.getVortexRingStateIntensity()
			);
		}
	}
}
