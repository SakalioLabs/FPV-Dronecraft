package com.tenicana.dronecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneStatusFormatter {
	private DroneStatusFormatter() {
	}

	public static String format(Telemetry telemetry) {
		String diagnostic = telemetry.diagnosticActive()
				? String.format(
						Locale.ROOT,
						"diagnostic %s %.1f/%.1fs",
						telemetry.diagnosticPhase(),
						telemetry.diagnosticElapsedSeconds(),
						telemetry.diagnosticDurationSeconds()
				)
				: "diagnostic idle";
		String warnings = warnings(telemetry);
		return String.format(
				Locale.ROOT,
				"Drone status | mode %s armed %s link raw %s fc %s failsafe %s %.2fs rc %.3f/%.3fs err %.4f | input T %.2f P %.2f R %.2f Y %.2f | speed %.2fm/s contact %.2f/%.2f/%.2fm/s %.0fd/s air %.2fm/s AoA %.1f slip %.1f | forces lift %.1fN cushion %.1fN wash %.1fN wall %.1fN | baro %.1fm %.1fm/s %.1fhPa err %.2fm | battery %.2fV %.0f%% sag %.2fV spike %.2fV %.1fA regen %.1fA limit %.2f current-limit %.2f | imu clip G %.2f A %.2f dterm %.0fHz | health frame %.0f%% rotor %.0f%% motor %.1fC %.2f esc %.1fC %.2f cool %.2f sig %.3f/%.3fs err %.4f desync %.2f load %.2f scrape %.2f prop strikes %d last %s | aero propwash %.2f VRS %.2f ETL %.2f adv %.2f skew %.2f rwake %.2f wake %.2f ceil %.2f asym %.2f blk %.2f water %.2f rain %.2f temp %.1fC stall %.2f vib %.2f coning %.2f mixer %.2f wind %.1fm/s airmass %.1fm/s gust %.2fm/s shear %.2fm/s2 turb %.2f obs %.2f ground %.2f | blackbox %d/%d | %s | warnings %s",
				telemetry.flightMode(),
				yesNo(telemetry.armed()),
				yesNo(telemetry.rawControlLinkActive()),
				yesNo(telemetry.processedControlLinkActive()),
				yesNo(telemetry.controlFailsafeActive()),
				telemetry.controlLinkLossSeconds(),
				telemetry.controlFrameAgeSeconds(),
				telemetry.controlFrameIntervalSeconds(),
				telemetry.controlFrameError(),
				telemetry.controlThrottle(),
				telemetry.controlPitch(),
				telemetry.controlRoll(),
				telemetry.controlYaw(),
				telemetry.speedMetersPerSecond(),
				telemetry.contactImpactSpeedMetersPerSecond(),
				telemetry.contactSlipSpeedMetersPerSecond(),
				telemetry.contactBounceSpeedMetersPerSecond(),
				telemetry.contactAngularImpulseDegreesPerSecond(),
				telemetry.airspeedMetersPerSecond(),
				telemetry.angleOfAttackDegrees(),
				telemetry.sideslipDegrees(),
				telemetry.airframeLiftForceNewtons(),
				telemetry.groundEffectDragForceNewtons(),
				telemetry.rotorWashDragForceNewtons(),
				telemetry.rotorWallEffectForceNewtons(),
				telemetry.barometerAltitudeMeters(),
				telemetry.barometerVerticalSpeedMetersPerSecond(),
				telemetry.barometerPressureHectopascals(),
				telemetry.barometerErrorMeters(),
				telemetry.batteryVoltage(),
				telemetry.batteryStateOfCharge() * 100.0,
				telemetry.batterySagVoltage(),
				telemetry.batteryVoltageSpike(),
				telemetry.batteryCurrentAmps(),
				telemetry.batteryRegenerativeCurrentAmps(),
				telemetry.batteryPowerLimit(),
				telemetry.batteryCurrentLimit(),
				telemetry.gyroClipIntensity(),
				telemetry.accelerometerClipIntensity(),
				telemetry.pidDTermLowPassCutoffHertz(),
				telemetry.frameHealth() * 100.0,
				telemetry.rotorHealth() * 100.0,
				telemetry.motorTemperatureCelsius(),
				telemetry.motorThermalLimit(),
				telemetry.escTemperatureCelsius(),
				telemetry.escThermalLimit(),
				telemetry.escCoolingFactor(),
				telemetry.escCommandFrameAgeSeconds(),
				telemetry.escCommandFrameIntervalSeconds(),
				telemetry.escCommandError(),
				telemetry.escDesyncIntensity(),
				telemetry.rotorAerodynamicLoadFactor(),
				telemetry.rotorSurfaceScrapeIntensity(),
				telemetry.propStrikeCount(),
				formatLastPropStrike(telemetry.lastPropStrikeRotorIndex(), telemetry.lastPropStrikeSeverity()),
				telemetry.propwashIntensity(),
				telemetry.vortexRingStateIntensity(),
				telemetry.rotorTranslationalLiftIntensity(),
				telemetry.rotorAdvanceRatio(),
				telemetry.rotorInflowSkewIntensity(),
				telemetry.rotorWakeInterferenceIntensity(),
				telemetry.droneWakeIntensity(),
				telemetry.ceilingEffectMultiplier(),
				telemetry.environmentThrustAsymmetry(),
				telemetry.rotorFlowObstruction(),
				telemetry.waterImmersionIntensity(),
				telemetry.precipitationWetnessIntensity(),
				telemetry.ambientTemperatureCelsius(),
				telemetry.rotorStallIntensity(),
				telemetry.rotorVibration(),
				telemetry.rotorConingIntensity(),
				telemetry.mixerSaturation(),
				telemetry.windSpeedMetersPerSecond(),
				telemetry.effectiveWindSpeedMetersPerSecond(),
				telemetry.windGustSpeedMetersPerSecond(),
				telemetry.windShearAccelerationMetersPerSecondSquared(),
				telemetry.turbulenceIntensity(),
				telemetry.obstacleProximity(),
				telemetry.groundEffectMultiplier(),
				telemetry.blackboxSamples(),
				telemetry.blackboxCapacity(),
				diagnostic,
				warnings
		);
	}

	static String warnings(Telemetry telemetry) {
		List<String> warnings = new ArrayList<>();
		if (telemetry.controlFailsafeActive()) {
			warnings.add("failsafe");
		}
		if (telemetry.armed() && !telemetry.rawControlLinkActive()) {
			warnings.add("raw-link-lost");
		}
		if (telemetry.armed() && !telemetry.processedControlLinkActive()) {
			warnings.add("fc-link-lost");
		}
		if (telemetry.batteryStateOfCharge() < 0.20 || telemetry.batteryPowerLimit() < 0.98) {
			warnings.add("battery-limit");
		}
		if (telemetry.batteryCurrentLimit() < 0.98) {
			warnings.add("current-limit");
		}
		if (telemetry.batteryVoltageSpike() > 0.35) {
			warnings.add("bus-spike");
		}
		if (telemetry.windGustSpeedMetersPerSecond() > 1.0 || telemetry.windShearAccelerationMetersPerSecondSquared() > 4.0) {
			warnings.add("gusty-air");
		}
		if (telemetry.contactImpactSpeedMetersPerSecond() > 3.2) {
			warnings.add("contact-impact");
		}
		if (telemetry.contactSlipSpeedMetersPerSecond() > 4.0 && telemetry.contactImpactSpeedMetersPerSecond() > 0.2) {
			warnings.add("ground-slide");
		}
		if (telemetry.contactAngularImpulseDegreesPerSecond() > 520.0) {
			warnings.add("contact-tumble");
		}
		if (telemetry.frameHealth() < 0.75) {
			warnings.add("frame-damage");
		}
		if (telemetry.rotorHealth() < 0.80) {
			warnings.add("rotor-damage");
		}
		if (telemetry.motorThermalLimit() < 0.98) {
			warnings.add("thermal-limit");
		}
		if (telemetry.escThermalLimit() < 0.98) {
			warnings.add("esc-thermal-limit");
		}
		if (telemetry.gyroClipIntensity() > 0.05 || telemetry.accelerometerClipIntensity() > 0.05) {
			warnings.add("imu-clip");
		}
		if (telemetry.escDesyncIntensity() > 0.20) {
			warnings.add("esc-desync");
		}
		if (telemetry.mixerSaturation() > 0.85) {
			warnings.add("mixer-saturation");
		}
		if (telemetry.rotorStallIntensity() > 0.35) {
			warnings.add("rotor-stall");
		}
		if (telemetry.rotorConingIntensity() > 0.35) {
			warnings.add("rotor-coning");
		}
		if (telemetry.rotorAdvanceRatio() > 0.55) {
			warnings.add("high-advance");
		}
		if (telemetry.vortexRingStateIntensity() > 0.35) {
			warnings.add("vrs");
		}
		if (telemetry.propwashIntensity() > 0.55) {
			warnings.add("propwash");
		}
		if (Math.abs(telemetry.barometerErrorMeters()) > 1.5) {
			warnings.add("baro-disturbed");
		}
		if (telemetry.droneWakeIntensity() > 0.45) {
			warnings.add("drone-wake");
		}
		if (telemetry.rotorWakeInterferenceIntensity() > 0.35) {
			warnings.add("rotor-wake");
		}
		if (telemetry.ceilingEffectMultiplier() > 1.08) {
			warnings.add("ceiling-effect");
		}
		if (telemetry.environmentThrustAsymmetry() > 0.08) {
			warnings.add("env-asymmetry");
		}
		if (telemetry.rotorFlowObstruction() > 0.45) {
			warnings.add("rotor-flow-blocked");
		}
		if (telemetry.waterImmersionIntensity() > 0.05) {
			warnings.add("water-ingress");
		}
		if (telemetry.precipitationWetnessIntensity() > 0.45) {
			warnings.add("rain-wet");
		}
		if (telemetry.ambientTemperatureCelsius() < -5.0) {
			warnings.add("cold-air");
		}
		if (telemetry.ambientTemperatureCelsius() > 42.0) {
			warnings.add("hot-air");
		}
		if (telemetry.rotorSurfaceScrapeIntensity() > 0.28) {
			warnings.add("prop-scrape");
		}
		if (telemetry.rotorWallEffectForceNewtons() > 2.5) {
			warnings.add("wall-effect");
		}
		if (telemetry.rotorVibration() > 0.45) {
			warnings.add("vibration");
		}
		if (telemetry.obstacleProximity() > 0.65) {
			warnings.add("dirty-air");
		}
		return warnings.isEmpty() ? "none" : String.join(", ", warnings);
	}

	private static String yesNo(boolean value) {
		return value ? "yes" : "no";
	}

	private static String formatLastPropStrike(int rotorIndex, double severity) {
		if (rotorIndex < 0 || severity <= 0.0) {
			return "none";
		}
		return String.format(Locale.ROOT, "r%d/%.2f", rotorIndex, severity);
	}

	public record Telemetry(
			boolean armed,
			FlightMode flightMode,
			boolean rawControlLinkActive,
			boolean processedControlLinkActive,
			boolean controlFailsafeActive,
			double controlLinkLossSeconds,
			double controlFrameAgeSeconds,
			double controlFrameIntervalSeconds,
			double controlFrameError,
			double controlThrottle,
			double controlPitch,
			double controlRoll,
			double controlYaw,
			double speedMetersPerSecond,
			double contactImpactSpeedMetersPerSecond,
			double contactSlipSpeedMetersPerSecond,
			double contactBounceSpeedMetersPerSecond,
			double contactAngularImpulseDegreesPerSecond,
			double airspeedMetersPerSecond,
			double angleOfAttackDegrees,
			double sideslipDegrees,
			double airframeLiftForceNewtons,
			double groundEffectDragForceNewtons,
			double rotorWashDragForceNewtons,
			double rotorWallEffectForceNewtons,
			double barometerAltitudeMeters,
			double barometerVerticalSpeedMetersPerSecond,
			double barometerPressureHectopascals,
			double barometerErrorMeters,
			double batteryVoltage,
			double batterySagVoltage,
			double batteryRegenerativeCurrentAmps,
			double batteryVoltageSpike,
			double batteryStateOfCharge,
			double batteryCurrentAmps,
			double batteryPowerLimit,
			double batteryCurrentLimit,
			double gyroClipIntensity,
			double accelerometerClipIntensity,
			double pidDTermLowPassCutoffHertz,
			double frameHealth,
			double rotorHealth,
			double motorTemperatureCelsius,
			double motorThermalLimit,
			double escTemperatureCelsius,
			double escThermalLimit,
			double escCoolingFactor,
			double escDesyncIntensity,
			double escCommandFrameAgeSeconds,
			double escCommandFrameIntervalSeconds,
			double escCommandError,
			double rotorAerodynamicLoadFactor,
			double rotorSurfaceScrapeIntensity,
			double propwashIntensity,
			double vortexRingStateIntensity,
			double rotorTranslationalLiftIntensity,
			double rotorAdvanceRatio,
			double rotorInflowSkewIntensity,
			double rotorWakeInterferenceIntensity,
			double droneWakeIntensity,
			double ceilingEffectMultiplier,
			double environmentThrustAsymmetry,
			double rotorFlowObstruction,
			double waterImmersionIntensity,
			double precipitationWetnessIntensity,
			double ambientTemperatureCelsius,
			double rotorStallIntensity,
			double rotorVibration,
			double rotorConingIntensity,
			double mixerSaturation,
			double windSpeedMetersPerSecond,
			double effectiveWindSpeedMetersPerSecond,
			double windGustSpeedMetersPerSecond,
			double windShearAccelerationMetersPerSecondSquared,
			double turbulenceIntensity,
			double obstacleProximity,
			double groundEffectMultiplier,
			int blackboxSamples,
			int blackboxCapacity,
			int propStrikeCount,
			int lastPropStrikeRotorIndex,
			double lastPropStrikeSeverity,
			boolean diagnosticActive,
			String diagnosticPhase,
			double diagnosticElapsedSeconds,
			double diagnosticDurationSeconds
	) {
	}
}
