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
	private static final int PANEL_COLOR = 0x8A05070A;
	private static final int BORDER_COLOR = 0xFF22E6C7;
	private static final int TEXT_COLOR = 0xFFEAF7F3;
	private static final int WARN_COLOR = 0xFFFFA94D;
	private static final float BATTERY_BUS_RIPPLE_WARNING_MIN_VOLTS = 0.18f;
	private static final float BATTERY_BUS_RIPPLE_WARNING_RATIO = 0.0125f;

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

		DroneEntity drone = DroneClientState.controlledDrone();
		if (!DroneClientState.hasController() && !DroneClientState.isFpvActive()) {
			return;
		}

		Font font = client.font;
		int x = 8;
		int y = 8;
		int width = 288;
		int height = drone == null ? 38 : 274;
		graphics.fill(x, y, x + width, y + height, PANEL_COLOR);
		graphics.renderOutline(x, y, width, height, BORDER_COLOR);

		boolean armed = drone == null ? DroneClientState.armed() : drone.isArmed();
		String mode = armed ? Component.translatable("hud.fpvdrone.armed").getString() : Component.translatable("hud.fpvdrone.disarmed").getString();
		String source = Component.translatable(DroneClientState.inputSource().translationKey()).getString();
		FlightMode flightMode = drone == null ? DroneClientState.flightMode() : drone.getFlightMode();
		graphics.drawString(font, Component.translatable("hud.fpvdrone.fpv").getString() + "  " + mode + "  " + flightMode.name() + "  " + source, x + 8, y + 7, armed ? BORDER_COLOR : WARN_COLOR, false);

		drawBar(graphics, x + 8, y + 22, 102, 6, DroneClientState.throttle(), 0xFF2BE870);
		graphics.drawString(font, percent("THR", DroneClientState.throttle()), x + 116, y + 20, TEXT_COLOR, false);

		if (drone == null) {
			graphics.drawString(font, "NO LINKED DRONE", x + 8, y + 31, WARN_COLOR, false);
			return;
		}

		float motorPower = drone.getMotorPower();
		graphics.drawString(font, linkStatusLine(drone), x + 8, y + 35, linkStatusColor(drone), false);
		graphics.drawString(font, commandLine(drone), x + 8, y + 46, drone.isControlFailsafeActive() ? WARN_COLOR : TEXT_COLOR, false);
		graphics.drawString(font, targetRateLine(drone), x + 8, y + 57, TEXT_COLOR, false);
		graphics.drawString(font, gyroRateLine(drone), x + 8, y + 68, rateTrackingColor(drone), false);
		graphics.drawString(font, pidOutputLine(drone), x + 8, y + 79, pidStatusColor(drone), false);
		graphics.drawString(font, estimatorLine(drone), x + 8, y + 90, estimatorStatusColor(drone), false);

		drawBar(graphics, x + 8, y + 103, 116, 6, motorPower, 0xFF48A8FF);
		graphics.drawString(font, motorLine(drone, motorPower), x + 132, y + 101, TEXT_COLOR, false);

		int rotorCount = visibleRotorCount(drone);
		int motorBarGap = 4;
		int motorBarWidth = Math.max(14, Math.min(34, (205 - motorBarGap * (rotorCount - 1)) / rotorCount));
		graphics.drawString(font, "MOT" + rotorCount, x + 8, y + 115, TEXT_COLOR, false);
		for (int i = 0; i < rotorCount; i++) {
			float value = drone.getMotorPower(i);
			int color = value > 0.92f ? WARN_COLOR : 0xFF48A8FF;
			drawBar(graphics, x + 36 + i * (motorBarWidth + motorBarGap), y + 117, motorBarWidth, 5, value, color);
		}

		float batteryPercent = drone.getBatteryStateOfCharge();
		graphics.drawString(font, rotorThrustLine(drone), x + 8, y + 128, TEXT_COLOR, false);

		int batteryColor = batteryPercent < 0.25f
				|| drone.getBatteryPowerLimit() < 0.9f
				|| drone.getBatteryCurrentLimit() < 0.9f
				|| drone.getBatteryVoltageSpike() > 0.35f
				|| batteryBusRippleWarning(drone)
				? WARN_COLOR
				: 0xFF2BE870;
		drawBar(graphics, x + 8, y + 143, 116, 6, batteryPercent, batteryColor);
		graphics.drawString(font, batteryLine(drone), x + 126, y + 141, batteryColor == WARN_COLOR ? WARN_COLOR : TEXT_COLOR, false);

		float health = Math.min(drone.getFrameHealth(), minRotorHealth(drone));
		drawBar(graphics, x + 8, y + 158, 116, 6, health, health < 0.35f ? WARN_COLOR : 0xFFC8E83C);
		graphics.drawString(font, damageLine(drone, health), x + 132, y + 156, damageStatusColor(drone, health), false);
		graphics.drawString(font, rotorHealthLine(drone), x + 8, y + 169, damageStatusColor(drone, health), false);

		String speed = String.format(
				Locale.ROOT,
				"SPD %4.1f C%3.1f/%3.1f/%3.1f A%3.0f",
				drone.getSpeedMetersPerSecond(),
				drone.getContactImpactSpeedMetersPerSecond(),
				drone.getContactSlipSpeedMetersPerSecond(),
				drone.getContactBounceSpeedMetersPerSecond(),
				drone.getContactAngularImpulseDegreesPerSecond()
		);
		String altitude = altitudeLine(drone);
		String attitude = String.format(
				Locale.ROOT,
				"P%4.0f R%4.0f Y%4.0f",
				Math.toDegrees(drone.getRenderPitchRadians()),
				Math.toDegrees(drone.getRenderRollRadians()),
				Math.toDegrees(drone.getRenderYawRadians())
		);
		graphics.drawString(font, speed, x + 8, y + 183, speedStatusColor(drone), false);
		graphics.drawString(font, altitude, x + 8, y + 194, barometerStatusColor(drone), false);
		graphics.drawString(font, attitude, x + 8, y + 205, TEXT_COLOR, false);
		graphics.drawString(font, aerodynamicStatusLine(drone), x + 8, y + 216, aerodynamicStatusColor(drone), false);
		graphics.drawString(font, aeroTorqueLine(drone), x + 8, y + 227, aeroTorqueStatusColor(drone), false);
		graphics.drawString(font, aeroForceLine(drone), x + 8, y + 238, aeroForceStatusColor(drone), false);
		graphics.drawString(font, environmentLine(drone), x + 8, y + 249, environmentStatusColor(drone), false);
		graphics.drawString(font, thermalLine(drone), x + 8, y + 260, thermalStatusColor(drone), false);
	}

	private static void drawBar(GuiGraphics graphics, int x, int y, int width, int height, float value, int color) {
		graphics.fill(x, y, x + width, y + height, 0xFF1B2426);
		int filled = Math.round(width * Math.max(0.0f, Math.min(1.0f, value)));
		graphics.fill(x, y, x + filled, y + height, color);
	}

	private static String percent(String label, float value) {
		return String.format(Locale.ROOT, "%s %3.0f%%", label, value * 100.0f);
	}

	private static String linkStatusLine(DroneEntity drone) {
		String status;
		if (drone.isControlFailsafeActive()) {
			status = "FS";
		} else if (drone.isRawControlLinkActive()) {
			status = "OK";
		} else {
			status = "HOLD";
		}
		return String.format(Locale.ROOT, "RC %-4s LOSS %.2fs", status, drone.getControlLinkLossSeconds());
	}

	private static String commandLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"CMD T%3.0f P%+4.0f R%+4.0f Y%+4.0f",
				drone.getControlThrottle() * 100.0f,
				drone.getControlPitch() * 100.0f,
				drone.getControlRoll() * 100.0f,
				drone.getControlYaw() * 100.0f
		);
	}

	private static String targetRateLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"TRG P%+4.0f Y%+4.0f R%+4.0f d/s",
				drone.getTargetPitchRateDegreesPerSecond(),
				drone.getTargetYawRateDegreesPerSecond(),
				drone.getTargetRollRateDegreesPerSecond()
		);
	}

	private static String motorLine(DroneEntity drone, float motorPower) {
		return String.format(Locale.ROOT, "MTR%3.0f%% %4.1fk", motorPower * 100.0f, drone.getAverageMotorRpm() / 1000.0f);
	}

	private static String gyroRateLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"GYR P%+4.0f Y%+4.0f R%+4.0f N%3.0f/%2.0f C%2.0f P%2.0f",
				drone.getGyroPitchRateDegreesPerSecond(),
				drone.getGyroYawRateDegreesPerSecond(),
				drone.getGyroRollRateDegreesPerSecond(),
				drone.getGyroNotchFrequencyHertz(),
				drone.getGyroNotchAttenuation() * 100.0f,
				Math.max(drone.getGyroClipIntensity(), drone.getAccelerometerClipIntensity()) * 100.0f,
				drone.getImuSupplyNoiseIntensity() * 100.0f
		);
	}

	private static String pidOutputLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"PID %+.2f/%+.2f/%+.2f D%3.0f PA%2.0f IR%2.0f AG%.1f",
				drone.getPidPitchOutputNewtonMeters(),
				drone.getPidYawOutputNewtonMeters(),
				drone.getPidRollOutputNewtonMeters(),
				drone.getPidDTermLowPassCutoffHertz(),
				drone.getPidAttenuation() * 100.0f,
				drone.getPidIntegralRelax() * 100.0f,
				drone.getAntiGravityBoost()
		);
	}

	private static String estimatorLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"EST P%+4.0f Y%+4.0f R%+4.0f E%.1f T%2.0f",
				drone.getEstimatedPitchDegrees(),
				drone.getEstimatedYawDegrees(),
				drone.getEstimatedRollDegrees(),
				drone.getAttitudeEstimateErrorDegrees(),
				drone.getAttitudeAccelerometerTrust() * 100.0f
		);
	}

	private static String rotorThrustLine(DroneEntity drone) {
		int rotorCount = visibleRotorCount(drone);
		if (rotorCount <= 6) {
			StringBuilder builder = new StringBuilder("THR N");
			for (int i = 0; i < rotorCount; i++) {
				builder.append(String.format(Locale.ROOT, " %4.1f", drone.getRotorThrustNewtons(i)));
			}
			return builder.toString();
		}
		return String.format(
				Locale.ROOT,
				"THR N n%d avg%4.1f min%4.1f max%4.1f",
				rotorCount,
				averageRotorThrust(drone, rotorCount),
				minRotorThrust(drone, rotorCount),
				maxRotorThrust(drone, rotorCount)
		);
	}

	private static String batteryLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"%.1fV %.0fA R%.0f +%.1f Rp%.2f L%.0f",
				drone.getBatteryVoltage(),
				drone.getBatteryCurrentAmps(),
				drone.getBatteryRegenerativeCurrentAmps(),
				drone.getBatteryVoltageSpike(),
				drone.getBatteryBusRippleVoltage(),
				Math.min(drone.getBatteryPowerLimit(), drone.getBatteryCurrentLimit()) * 100.0f
		);
	}

	private static boolean batteryBusRippleWarning(DroneEntity drone) {
		float threshold = Math.max(
				BATTERY_BUS_RIPPLE_WARNING_MIN_VOLTS,
				Math.max(1.0f, drone.getBatteryVoltage()) * BATTERY_BUS_RIPPLE_WARNING_RATIO
		);
		return drone.getBatteryBusRippleVoltage() > threshold;
	}

	private static String damageLine(DroneEntity drone, float health) {
		return String.format(
				Locale.ROOT,
				"DMG F%2.0f R%2.0f V%2.0f C%2.0f",
				(1.0f - drone.getFrameHealth()) * 100.0f,
				(1.0f - minRotorHealth(drone)) * 100.0f,
				drone.getRotorVibration() * 100.0f,
				drone.getRotorConingIntensity() * 100.0f
		);
	}

	private static String rotorHealthLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"RHL n%d min%3.0f r%d avg%3.0f PS%d SC%2.0f %s",
				visibleRotorCount(drone),
				minRotorHealth(drone) * 100.0f,
				weakestRotorIndex(drone),
				averageRotorHealth(drone) * 100.0f,
				drone.getPropStrikeCount(),
				drone.getRotorSurfaceScrapeIntensity() * 100.0f,
				lastPropStrikeLabel(drone)
		);
	}

	private static String lastPropStrikeLabel(DroneEntity drone) {
		int rotorIndex = drone.getLastPropStrikeRotorIndex();
		double severity = drone.getLastPropStrikeSeverity();
		if (rotorIndex < 0 || severity <= 0.0) {
			return "--";
		}
		return String.format(Locale.ROOT, "r%d/%.2f", rotorIndex, severity);
	}

	private static float minRotorHealth(DroneEntity drone) {
		float health = 1.0f;
		int rotorCount = visibleRotorCount(drone);
		for (int i = 0; i < rotorCount; i++) {
			health = Math.min(health, drone.getRotorHealth(i));
		}
		return health;
	}

	private static float averageRotorHealth(DroneEntity drone) {
		float sum = 0.0f;
		int rotorCount = visibleRotorCount(drone);
		for (int i = 0; i < rotorCount; i++) {
			sum += drone.getRotorHealth(i);
		}
		return sum / rotorCount;
	}

	private static int weakestRotorIndex(DroneEntity drone) {
		int weakest = 0;
		float min = Float.POSITIVE_INFINITY;
		int rotorCount = visibleRotorCount(drone);
		for (int i = 0; i < rotorCount; i++) {
			float health = drone.getRotorHealth(i);
			if (health < min) {
				min = health;
				weakest = i;
			}
		}
		return weakest;
	}

	private static float averageRotorThrust(DroneEntity drone, int rotorCount) {
		float sum = 0.0f;
		for (int i = 0; i < rotorCount; i++) {
			sum += drone.getRotorThrustNewtons(i);
		}
		return sum / rotorCount;
	}

	private static float minRotorThrust(DroneEntity drone, int rotorCount) {
		float min = Float.POSITIVE_INFINITY;
		for (int i = 0; i < rotorCount; i++) {
			min = Math.min(min, drone.getRotorThrustNewtons(i));
		}
		return Float.isFinite(min) ? min : 0.0f;
	}

	private static float maxRotorThrust(DroneEntity drone, int rotorCount) {
		float max = 0.0f;
		for (int i = 0; i < rotorCount; i++) {
			max = Math.max(max, drone.getRotorThrustNewtons(i));
		}
		return max;
	}

	private static int visibleRotorCount(DroneEntity drone) {
		return Math.max(1, Math.min(8, drone.getRotorCount()));
	}

	private static String altitudeLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"ALT %5.1f BAR %5.1f E%+4.1f",
				drone.getY(),
				drone.getBarometerAltitudeMeters(),
				drone.getBarometerErrorMeters()
		);
	}

	private static String aerodynamicStatusLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"AS%4.1f A%+3.0f S%+3.0f E%2.0f Iv%.1f IL%2.0f Mu%2.0f TM%2.0f Re%2.0f BA%2.0f BS%2.0f BP%2.0f K%2.0f W%2.0f WL%2.0f CX%2.0f WW%2.0f SW%.1f WM%2.0f P%2.0f V%2.0f R%2.0f",
				drone.getAirspeedMetersPerSecond(),
				drone.getAngleOfAttackDegrees(),
				drone.getSideslipDegrees(),
				drone.getRotorTranslationalLiftIntensity() * 100.0f,
				drone.getRotorInducedVelocityMetersPerSecond(),
				(1.0f - drone.getRotorInducedLagThrustScale()) * 100.0f,
				drone.getRotorAdvanceRatio() * 100.0f,
				drone.getRotorTipMach() * 100.0f,
				drone.getRotorLowReynoldsLoss() * 100.0f,
				drone.getRotorBladeAngleOfAttackDegrees(),
				drone.getRotorBladeElementStallIntensity() * 100.0f,
				drone.getRotorBladePassRippleIntensity() * 100.0f,
				drone.getRotorInflowSkewIntensity() * 100.0f,
				drone.getRotorWakeInterferenceIntensity() * 100.0f,
				(1.0f - drone.getRotorWakeThrustScale()) * 100.0f,
				drone.getRotorCoaxialLoadBias() * 100.0f,
				(1.0f - drone.getRotorWetThrustScale()) * 100.0f,
				drone.getRotorWakeSwirlVelocityMetersPerSecond(),
				drone.getRotorWindmillingIntensity() * 100.0f,
				drone.getPropwashIntensity() * 100.0f,
				drone.getVortexRingStateIntensity() * 100.0f,
				drone.getRotorStallIntensity() * 100.0f
		);
	}

	private static String aeroTorqueLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"TRQ SP%2.0f FL%2.0f BD%.3f WT%.3f BT%.3f AT%.3f GT%.3f FT%.3f",
				drone.getAirframeSeparatedFlowIntensity() * 100.0f,
				drone.getRotorFlappingTiltDegrees(),
				drone.getRotorBladeDissymmetryTorqueNewtonMeters(),
				drone.getRotorWakeSwirlTorqueNewtonMeters(),
				drone.getRotorActiveBrakingTorqueNewtonMeters(),
				drone.getRotorAccelerationReactionTorqueNewtonMeters(),
				drone.getRotorGyroscopicTorqueNewtonMeters(),
				drone.getRotorFlappingTorqueNewtonMeters()
		);
	}

	private static String aeroForceLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"FOR L%4.1f G%4.1f W%4.1f H%4.1f WL%4.1f",
				drone.getAirframeLiftForceNewtons(),
				drone.getGroundEffectDragForceNewtons(),
				drone.getRotorWashDragForceNewtons(),
				drone.getRotorInPlaneDragForceNewtons(),
				drone.getRotorWallEffectForceNewtons()
		);
	}

	private static String environmentLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"ENV W%2.0f A%2.0f G%2.0f Sh%2.0f O%2.0f B%2.0f Wt%2.0f Rn%2.0f",
				drone.getWindSpeedMetersPerSecond(),
				drone.getEffectiveWindSpeedMetersPerSecond(),
				drone.getWindGustSpeedMetersPerSecond(),
				drone.getWindShearAccelerationMetersPerSecondSquared(),
				drone.getObstacleProximity() * 100.0f,
				drone.getRotorFlowObstruction() * 100.0f,
				drone.getWaterImmersionIntensity() * 100.0f,
				drone.getPrecipitationWetnessIntensity() * 100.0f
		);
	}

	private static String thermalLine(DroneEntity drone) {
		return String.format(
				Locale.ROOT,
				"TMP A%+3.0f M%3.0f E%3.0f TL%3.0f/%3.0f H%2.0f D%2.0f L%.1f",
				drone.getAmbientTemperatureCelsius(),
				drone.getMotorTemperatureCelsius(),
				drone.getEscTemperatureCelsius(),
				drone.getMotorThermalLimit() * 100.0f,
				drone.getEscThermalLimit() * 100.0f,
				drone.getMotorVoltageHeadroom() * 100.0f,
				drone.getEscDesyncIntensity() * 100.0f,
				drone.getRotorAerodynamicLoadFactor()
		);
	}

	private static int aerodynamicStatusColor(DroneEntity drone) {
		if (Math.abs(drone.getAngleOfAttackDegrees()) > 35.0f
				|| Math.abs(drone.getSideslipDegrees()) > 35.0f
				|| drone.getMixerSaturation() > 0.18f
				|| drone.getPropwashIntensity() > 0.25f
				|| drone.getVortexRingStateIntensity() > 0.25f
				|| drone.getRotorAdvanceRatio() > 0.55f
				|| drone.getRotorTipMach() > 0.70f
				|| drone.getRotorLowReynoldsLoss() > 0.25f
				|| drone.getRotorBladeAngleOfAttackDegrees() > 28.0f
				|| drone.getRotorBladeElementStallIntensity() > 0.35f
				|| drone.getRotorBladePassRippleIntensity() > 0.025f
				|| drone.getRotorInPlaneDragForceNewtons() > 2.0f
				|| drone.getRotorInducedLagThrustScale() < 0.93f
				|| drone.getRotorWakeInterferenceIntensity() > 0.35f
				|| drone.getRotorWakeThrustScale() < 0.94f
				|| drone.getRotorCoaxialLoadBias() > 0.070f
				|| drone.getRotorWetThrustScale() < 0.96f
				|| drone.getRotorWakeSwirlVelocityMetersPerSecond() > 0.75f
				|| drone.getRotorWindmillingIntensity() > 0.45f
				|| drone.getRotorStallIntensity() > 0.35f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int environmentStatusColor(DroneEntity drone) {
		if (drone.getDroneWakeIntensity() > 0.35f
				|| drone.getCeilingEffectIntensity() > 0.35f
				|| drone.getEnvironmentThrustAsymmetry() > 0.08f
				|| drone.getRotorFlowObstruction() > 0.45f
				|| drone.getWindGustSpeedMetersPerSecond() > 1.0f
				|| drone.getWindShearAccelerationMetersPerSecondSquared() > 4.0f
				|| drone.getTurbulenceIntensity() > 0.55f
				|| drone.getObstacleProximity() > 0.55f
				|| drone.getWaterImmersionIntensity() > 0.05f
				|| drone.getPrecipitationWetnessIntensity() > 0.45f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int aeroTorqueStatusColor(DroneEntity drone) {
		if (drone.getAirframeSeparatedFlowIntensity() > 0.55f
				|| drone.getRotorFlappingTiltDegrees() > 8.0f
				|| drone.getRotorBladeDissymmetryTorqueNewtonMeters() > 0.015f
				|| drone.getRotorWakeSwirlTorqueNewtonMeters() > 0.010f
				|| drone.getRotorActiveBrakingTorqueNewtonMeters() > 0.015f
				|| drone.getRotorAccelerationReactionTorqueNewtonMeters() > 0.015f
				|| drone.getRotorGyroscopicTorqueNewtonMeters() > 0.012f
				|| drone.getRotorFlappingTorqueNewtonMeters() > 0.012f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int aeroForceStatusColor(DroneEntity drone) {
		if (drone.getAirframeLiftForceNewtons() > 6.0f
				|| drone.getGroundEffectDragForceNewtons() > 6.0f
				|| drone.getRotorWashDragForceNewtons() > 6.0f
				|| drone.getRotorWallEffectForceNewtons() > 3.0f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int barometerStatusColor(DroneEntity drone) {
		return Math.abs(drone.getBarometerErrorMeters()) > 1.5f ? WARN_COLOR : TEXT_COLOR;
	}

	private static int speedStatusColor(DroneEntity drone) {
		if (drone.getContactImpactSpeedMetersPerSecond() > 3.2f
				|| (drone.getContactSlipSpeedMetersPerSecond() > 4.0f && drone.getContactImpactSpeedMetersPerSecond() > 0.2f)
				|| drone.getContactAngularImpulseDegreesPerSecond() > 520.0f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int linkStatusColor(DroneEntity drone) {
		if (drone.isControlFailsafeActive() || !drone.isRawControlLinkActive()) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int rateTrackingColor(DroneEntity drone) {
		float targetMagnitude = Math.abs(drone.getTargetPitchRateDegreesPerSecond())
				+ Math.abs(drone.getTargetYawRateDegreesPerSecond())
				+ Math.abs(drone.getTargetRollRateDegreesPerSecond());
		float errorMagnitude = Math.abs(drone.getTargetPitchRateDegreesPerSecond() - drone.getGyroPitchRateDegreesPerSecond())
				+ Math.abs(drone.getTargetYawRateDegreesPerSecond() - drone.getGyroYawRateDegreesPerSecond())
				+ Math.abs(drone.getTargetRollRateDegreesPerSecond() - drone.getGyroRollRateDegreesPerSecond());
		if ((targetMagnitude > 120.0f && errorMagnitude > targetMagnitude * 0.85f)
				|| drone.getGyroClipIntensity() > 0.05f
				|| drone.getAccelerometerClipIntensity() > 0.05f
				|| drone.getImuSupplyNoiseIntensity() > 0.35f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int pidStatusColor(DroneEntity drone) {
		if (drone.getPidAttenuation() < 0.70f || drone.getPidIntegralRelax() > 0.60f || drone.getAntiGravityBoost() > 1.5f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int damageStatusColor(DroneEntity drone, float health) {
		if (health < 0.35f
				|| drone.getRotorVibration() > 0.12f
				|| drone.getRotorConingIntensity() > 0.35f
				|| drone.getRotorSurfaceScrapeIntensity() > 0.28f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int estimatorStatusColor(DroneEntity drone) {
		if (drone.getAttitudeEstimateErrorDegrees() > 12.0f || drone.getAttitudeAccelerometerTrust() < 0.20f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}

	private static int thermalStatusColor(DroneEntity drone) {
		if (drone.getAmbientTemperatureCelsius() < -5.0f
				|| drone.getAmbientTemperatureCelsius() > 42.0f
				|| drone.getMotorTemperatureCelsius() > 95.0f
				|| drone.getEscTemperatureCelsius() > 95.0f
				|| drone.getMotorThermalLimit() < 0.95f
				|| drone.getEscThermalLimit() < 0.95f
				|| drone.getMotorVoltageHeadroom() < 0.18f
				|| drone.getEscDesyncIntensity() > 0.20f
				|| drone.getRotorAerodynamicLoadFactor() > 1.35f) {
			return WARN_COLOR;
		}
		return TEXT_COLOR;
	}
}
