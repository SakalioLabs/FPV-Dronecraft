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
	private static final int PANEL_DARK = 0xC00A0E11;
	private static final int LINE = 0xAA7BE7D6;
	private static final int SHADOW = 0x70000000;
	private static final int MARGIN = 8;

	private DroneHud() {
	}

	public static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HUD_ID, DroneHud::render);
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || !DroneClientState.isHudEnabled()) {
			return;
		}
		DroneEntity drone = DroneClientState.controlledDrone();
		if (!DroneClientState.hasController() && !DroneClientState.isFpvActive() && drone == null) {
			return;
		}

		Font font = client.font;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int hudWidth = Math.max(280, Math.min(560, screenWidth - MARGIN * 2));
		int hudX = (screenWidth - hudWidth) / 2;

		Telemetry telemetry = Telemetry.from(client, drone);
		drawTopStatus(graphics, font, hudX, MARGIN, hudWidth, telemetry);
		drawAttitude(graphics, screenWidth / 2, screenHeight / 2, telemetry);
		drawSideScales(graphics, font, screenWidth, screenHeight, telemetry);
		drawBottomTelemetry(graphics, font, hudX, screenHeight - MARGIN - 42, hudWidth, telemetry);
	}

	private static void drawTopStatus(GuiGraphics graphics, Font font, int x, int y, int width, Telemetry telemetry) {
		graphics.fill(x, y, x + width, y + 26, PANEL_DARK);
		graphics.fill(x, y + 25, x + width, y + 26, LINE);

		int col = Math.max(64, width / 6);
		drawCentered(graphics, font, Component.translatable("hud.fpvdrone.mode_value", telemetry.mode().name()), x + col / 2, y + 5, TEXT);
		drawCentered(graphics, font, Component.translatable(telemetry.fpvView() ? "hud.fpvdrone.view_fpv" : "hud.fpvdrone.view_los"), x + col + col / 2, y + 5, telemetry.fpvView() ? PRIMARY : TEXT);
		drawCentered(graphics, font, Component.translatable(telemetry.armed() ? "hud.fpvdrone.armed" : "hud.fpvdrone.disarmed"), x + col * 2 + col / 2, y + 5, telemetry.armed() ? PRIMARY : WARNING);
		drawCentered(graphics, font, Component.translatable(telemetry.linkOk() ? "hud.fpvdrone.link_ok" : "hud.fpvdrone.link_lost"), x + col * 3 + col / 2, y + 5, telemetry.linkOk() ? PRIMARY : DANGER);
		drawCentered(graphics, font, Component.translatable("hud.fpvdrone.battery_value", percent(telemetry.battery())), x + col * 4 + col / 2, y + 5, batteryColor(telemetry));
		drawCentered(graphics, font, Component.translatable("hud.fpvdrone.throttle_value", percent(telemetry.throttle())), x + width - col / 2, y + 5, TEXT);
	}

	private static void drawAttitude(GuiGraphics graphics, int centerX, int centerY, Telemetry telemetry) {
		int radius = 66;
		int rollOffset = Math.round(Mth.clamp((float) Math.toDegrees(telemetry.roll()), -45.0f, 45.0f) * 0.75f);
		int pitchOffset = Math.round(Mth.clamp((float) Math.toDegrees(telemetry.pitch()), -35.0f, 35.0f) * 1.15f);
		int horizonY = centerY + pitchOffset;

		graphics.fill(centerX - radius, horizonY - 1, centerX + radius, horizonY + 1, LINE);
		graphics.fill(centerX - 1, centerY - 42, centerX + 1, centerY + 42, SHADOW);
		graphics.fill(centerX - 18, centerY - 1, centerX - 5, centerY + 1, PRIMARY);
		graphics.fill(centerX + 5, centerY - 1, centerX + 18, centerY + 1, PRIMARY);
		graphics.fill(centerX - 1, centerY - 5, centerX + 1, centerY + 6, PRIMARY);

		for (int step = -3; step <= 3; step++) {
			if (step == 0) {
				continue;
			}
			int y = horizonY + step * 18;
			if (Math.abs(y - centerY) > 62) {
				continue;
			}
			int half = step % 2 == 0 ? 26 : 17;
			graphics.fill(centerX - rollOffset - half, y, centerX - rollOffset - 5, y + 1, MUTED);
			graphics.fill(centerX - rollOffset + 5, y, centerX - rollOffset + half, y + 1, MUTED);
		}

		graphics.fill(centerX - 2, centerY - radius - 6, centerX + 3, centerY - radius + 1, telemetry.armed() ? PRIMARY : WARNING);
	}

	private static void drawSideScales(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, Telemetry telemetry) {
		int centerY = screenHeight / 2;
		int scaleHeight = 96;
		int leftX = MARGIN + 4;
		int rightX = screenWidth - MARGIN - 46;
		int top = centerY - scaleHeight / 2;
		drawVerticalScale(graphics, leftX, top, 12, scaleHeight, telemetry.throttle(), PRIMARY);
		drawString(graphics, font, Component.translatable("hud.fpvdrone.thr_short", percent(telemetry.throttle())), leftX + 18, top + scaleHeight - 9, TEXT);

		float battery = Mth.clamp(telemetry.battery(), 0.0f, 1.0f);
		drawVerticalScale(graphics, rightX + 34, top, 12, scaleHeight, battery, batteryColor(telemetry));
		drawRight(graphics, font, Component.translatable("hud.fpvdrone.alt_short", oneDecimal(telemetry.altitude())), rightX + 28, top + 2, TEXT);
		drawRight(graphics, font, Component.translatable("hud.fpvdrone.spd_short", oneDecimal(telemetry.speed())), rightX + 28, top + 14, TEXT);
		drawRight(graphics, font, Component.translatable("hud.fpvdrone.vs_short", signedOneDecimal(telemetry.verticalSpeed())), rightX + 28, top + 26, verticalSpeedColor(telemetry));
	}

	private static void drawBottomTelemetry(GuiGraphics graphics, Font font, int x, int y, int width, Telemetry telemetry) {
		graphics.fill(x, y, x + width, y + 42, PANEL_DARK);
		graphics.fill(x, y, x + width, y + 1, LINE);
		int columnWidth = Math.max(68, width / 5);
		drawMetric(graphics, font, x + 8, y + 7, "hud.fpvdrone.metric.altitude", oneDecimal(telemetry.altitude()), TEXT);
		drawMetric(graphics, font, x + columnWidth + 8, y + 7, "hud.fpvdrone.metric.speed", oneDecimal(telemetry.speed()), TEXT);
		drawMetric(graphics, font, x + columnWidth * 2 + 8, y + 7, "hud.fpvdrone.metric.rpm", compactRpm(telemetry), telemetry.armed() && telemetry.rpm() > 1000.0f ? PRIMARY : MUTED);
		drawMetric(graphics, font, x + columnWidth * 3 + 8, y + 7, "hud.fpvdrone.metric.temp", integer(Math.max(telemetry.motorTemp(), telemetry.escTemp())), tempColor(telemetry));
		drawMetric(graphics, font, x + columnWidth * 4 + 8, y + 7, "hud.fpvdrone.metric.health", percent(Math.min(telemetry.frameHealth(), telemetry.rotorHealth())), healthColor(telemetry));

		int warnColor = warningColor(telemetry);
		Component status = Component.translatable(warningKey(telemetry));
		drawCentered(graphics, font, status, x + width / 2, y + 27, warnColor);
	}

	private static void drawMetric(GuiGraphics graphics, Font font, int x, int y, String key, String value, int color) {
		drawString(graphics, font, Component.translatable(key, value), x, y, color);
	}

	private static void drawVerticalScale(GuiGraphics graphics, int x, int y, int width, int height, float value, int color) {
		float clamped = Mth.clamp(value, 0.0f, 1.0f);
		graphics.fill(x, y, x + width, y + height, PANEL);
		graphics.fill(x, y, x + 1, y + height, LINE);
		graphics.fill(x + width - 1, y, x + width, y + height, LINE);
		int filled = Math.round(height * clamped);
		graphics.fill(x + 2, y + height - filled, x + width - 2, y + height - 2, color);
	}

	private static int warningColor(Telemetry telemetry) {
		if (!telemetry.linkOk() || telemetry.failsafe()) {
			return DANGER;
		}
		if (telemetry.vrs() > 0.25f || telemetry.propwash() > 0.35f || telemetry.battery() < 0.22f) {
			return WARNING;
		}
		return PRIMARY;
	}

	private static String warningKey(Telemetry telemetry) {
		if (!telemetry.linkOk() || telemetry.failsafe()) {
			return "hud.fpvdrone.warn_link";
		}
		if (telemetry.vrs() > 0.25f) {
			return "hud.fpvdrone.warn_vrs";
		}
		if (telemetry.propwash() > 0.35f) {
			return "hud.fpvdrone.warn_propwash";
		}
		if (telemetry.battery() < 0.22f) {
			return "hud.fpvdrone.warn_battery";
		}
		return telemetry.armed() ? "hud.fpvdrone.warn_ready" : "hud.fpvdrone.warn_locked";
	}

	private static int textColor(boolean ok) {
		return ok ? PRIMARY : WARNING;
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

	private static int tempColor(Telemetry telemetry) {
		float temp = Math.max(telemetry.motorTemp(), telemetry.escTemp());
		return temp > 95.0f ? DANGER : (temp > 75.0f ? WARNING : TEXT);
	}

	private static int healthColor(Telemetry telemetry) {
		float health = Math.min(telemetry.frameHealth(), telemetry.rotorHealth());
		return health < 0.35f ? DANGER : (health < 0.70f ? WARNING : PRIMARY);
	}

	private static void drawCentered(GuiGraphics graphics, Font font, Component component, int centerX, int y, int color) {
		graphics.drawString(font, component, centerX - font.width(component) / 2, y, color, false);
	}

	private static void drawRight(GuiGraphics graphics, Font font, Component component, int rightX, int y, int color) {
		graphics.drawString(font, component, rightX - font.width(component), y, color, false);
	}

	private static void drawString(GuiGraphics graphics, Font font, Component component, int x, int y, int color) {
		graphics.drawString(font, component, x, y, color, false);
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

	private static String compactRpm(Telemetry telemetry) {
		if (!telemetry.armed()) {
			return "0";
		}
		float rpm = Math.max(0.0f, telemetry.rpm());
		if (rpm >= 1000.0f) {
			return String.format(Locale.ROOT, "%.1fk", rpm / 1000.0f);
		}
		return integer(rpm);
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
