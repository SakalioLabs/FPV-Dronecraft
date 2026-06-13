package com.tenicana.dronecraft.client.hud;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneHud {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "flight_hud");
	private static final int TEXT = 0xFFF0F8FF;
	private static final int PRIMARY = 0xFF00FFB2;
	private static final int WARNING = 0xFFFFC266;
	private static final int BACKDROP = 0xA80E1318;
	private static final int BORDER = 0xCC1E2931;
	private static final int HUD_MARGIN = 8;
	private static final int HUD_WIDTH = 150;
	private static final int HUD_HEIGHT = 44;

	private DroneHud() {
	}

	public static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HUD_ID, DroneHud::render);
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (!DroneClientState.hasController() && !DroneClientState.isFpvActive()) {
			return;
		}

		DroneEntity drone = DroneClientState.controlledDrone();
		Font font = client.font;
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		boolean armed = drone == null ? DroneClientState.armed() : drone.isArmed();
		FlightMode mode = drone == null ? DroneClientState.flightMode() : drone.getFlightMode();
		float throttle = drone == null ? DroneClientState.throttle() : drone.getControlThrottle();
		float pitch = drone == null ? 0.0f : drone.getRenderPitchRadians();
		float roll = drone == null ? 0.0f : drone.getRenderRollRadians();
		float yaw = drone == null ? 0.0f : drone.getRenderYawRadians();
		float altitude = drone == null ? (float) client.player.getY() : (float) drone.getY();
		float speed = drone == null ? (float) client.player.getDeltaMovement().length() : drone.getSpeedMetersPerSecond();
		float battery = drone == null ? 1.0f : drone.getBatteryStateOfCharge();
		float frameHealth = drone == null ? 1.0f : (float) drone.getFrameHealth();
		float rotorHealth = drone == null ? 1.0f : (float) drone.getRotorHealth();
		boolean link = drone != null && drone.isRawControlLinkActive();

		int tlX = HUD_MARGIN;
		int trX = width - HUD_MARGIN - HUD_WIDTH;
		int blX = HUD_MARGIN;
		int brX = width - HUD_MARGIN - HUD_WIDTH;
		int topY = HUD_MARGIN;
		int bottomY = height - HUD_MARGIN - HUD_HEIGHT;

		drawCornerPanel(graphics, tlX, topY, textColor(armed, drone), String.format(Locale.ROOT, "FPV %s", armed ? "ARM" : "DIS"),
				String.format(Locale.ROOT, "MODE %s", mode.name()),
				String.format(Locale.ROOT, "THR %3.0f%%", throttle * 100.0f),
				String.format(Locale.ROOT, "LINK %s", link ? "OK" : "OFF"),
				font);

		drawCornerPanel(graphics, trX, topY, textColor(armed, drone), String.format(Locale.ROOT, "BAT %3.0f%%", battery * 100.0f),
				String.format(Locale.ROOT, "ALT %5.1fm", altitude),
				String.format(Locale.ROOT, "SPD %4.1fm/s", speed),
				String.format(Locale.ROOT, "F/H %3.0f/%3.0f", frameHealth * 100.0f, rotorHealth * 100.0f),
				font);

		drawCornerPanel(graphics, blX, bottomY, textColor(armed, drone), String.format(Locale.ROOT, "ATT %+.0f/%+.0f", Math.toDegrees(pitch), Math.toDegrees(roll)),
				String.format(Locale.ROOT, "YAW %+.0f", Math.toDegrees(yaw)),
				String.format(Locale.ROOT, "PWR %d", armed ? 1 : 0),
				String.format(Locale.ROOT, "HOLD"), font);

		drawCornerPanel(graphics, brX, bottomY, textColor(armed, drone), "CTRL",
				String.format(Locale.ROOT, "H %d", DroneClientState.hasController() ? 1 : 0),
				String.format(Locale.ROOT, "F %s", armed ? "ARM" : "DIS"),
				String.format(Locale.ROOT, "T %d", DroneClientState.hasController() ? 1 : 0),
				font);
	}

	private static int textColor(boolean armed, DroneEntity drone) {
		return armed && (drone == null || drone.isRawControlLinkActive()) ? PRIMARY : WARNING;
	}

	private static void drawCornerPanel(
			GuiGraphics graphics,
			int x,
			int y,
			int color,
			String title,
			String line1,
			String line2,
			String line3,
			Font font
	) {
		int right = x + HUD_WIDTH;
		int bottom = y + HUD_HEIGHT;

		graphics.fill(x, y, right, bottom, BACKDROP);
		graphics.fill(x, y, right, y + 2, BORDER);
		graphics.drawString(font, Component.literal(title), x + 4, y + 3, color, false);
		graphics.drawString(font, Component.literal(line1), x + 4, y + 13, color, false);
		graphics.drawString(font, Component.literal(line2), x + 4, y + 22, TEXT, false);
		graphics.drawString(font, Component.literal(line3), x + 4, y + 30, TEXT, false);

		graphics.fill(x - 1, y, x, y + HUD_HEIGHT, BORDER);
		graphics.fill(right, y, right + 1, y + HUD_HEIGHT, BORDER);
	}
}
