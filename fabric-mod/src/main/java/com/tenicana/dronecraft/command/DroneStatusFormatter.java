package com.tenicana.dronecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneStatusFormatter {
	private static final double BATTERY_BUS_RIPPLE_WARNING_MIN_VOLTS = 0.18;
	private static final double BATTERY_BUS_RIPPLE_WARNING_RATIO = 0.0125;

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
				"Drone status | mode %s armed %s link raw %s fc %s failsafe %s %.2fs rc %.3f/%.3fs err %.4f | input T %.2f P %.2f R %.2f Y %.2f | speed %.2fm/s contact %.2f/%.2f/%.2fm/s %.0fd/s air %.2fm/s AoA %.1f slip %.1f | forces lift %.1fN sep %.2f flap %.1fdeg cushion %.1fN wash %.1fN wall %.1fN | baro %.1fm %.1fm/s %.1fhPa err %.2fm | battery %.2fV %.0f%% sag %.2fV ir %.0fmOhm sag20 %.0fA margin %.2f spike %.2fV ripple %.3fV %.1fA regen %.1fA limit %.2f current-limit %.2f | imu clip G %.2f A %.2f pwr %.2f dterm %.0fHz | health frame %.0f%% rotor %.0f%% motor %.1fC %.2f head %.2f mR %.2f esc %.1fC %.2f cool %.2f sig %.3f/%.3fs err %.4f desync %.2f load %.2f hforce %.2fN scrape %.2f prop strikes %d last %s | aero propwash %.2f VRS %.2f vrsbuf %.0f%% vrsF %.2fN ETL %.2f ind %.2fm/s iloss %.0f%% adv %.2f J %.2f pthr %.2f ppwr %.2f rev %.2f tipmach %.2f machloss %.0f%% lowre %.2f blade %.0fdeg bstall %.2f bpass %.3f bdiss %.3fNm skew %.2f rwake %.2f wloss %.0f%% coax %.3f wetloss %.0f%% swirl %.2fm/s wmill %.2f swirlT %.3fNm brakeT %.3fNm accelT %.3fNm gyroT %.3fNm flapT %.3fNm wake %.2f ceil %.2f asym %.2f blk %.2f water %.2f rain %.2f temp %.1fC stall %.2f vib %.2f coning %.2f mixer %.2f wind %.1fm/s airmass %.1fm/s gust %.2fm/s shear %.2fm/s2 turb %.2f obs %.2f ground %.2f | blackbox %d/%d | %s | warnings %s",
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
				telemetry.airframeSeparatedFlowIntensity(),
				telemetry.rotorFlappingTiltDegrees(),
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
				telemetry.batteryEffectiveResistanceOhms() * 1000.0,
				telemetry.batteryTwentyPercentSagCurrentAmps(),
				telemetry.batteryTwentyPercentSagCurrentMargin(),
				telemetry.batteryVoltageSpike(),
				telemetry.batteryBusRippleVoltage(),
				telemetry.batteryCurrentAmps(),
				telemetry.batteryRegenerativeCurrentAmps(),
				telemetry.batteryPowerLimit(),
				telemetry.batteryCurrentLimit(),
				telemetry.gyroClipIntensity(),
				telemetry.accelerometerClipIntensity(),
				telemetry.imuSupplyNoiseIntensity(),
				telemetry.pidDTermLowPassCutoffHertz(),
				telemetry.frameHealth() * 100.0,
				telemetry.rotorHealth() * 100.0,
				telemetry.motorTemperatureCelsius(),
				telemetry.motorThermalLimit(),
				telemetry.motorVoltageHeadroom(),
				telemetry.motorWindingResistanceScale(),
				telemetry.escTemperatureCelsius(),
				telemetry.escThermalLimit(),
				telemetry.escCoolingFactor(),
				telemetry.escCommandFrameAgeSeconds(),
				telemetry.escCommandFrameIntervalSeconds(),
				telemetry.escCommandError(),
				telemetry.escDesyncIntensity(),
				telemetry.rotorAerodynamicLoadFactor(),
				telemetry.rotorInPlaneDragForceNewtons(),
				telemetry.rotorSurfaceScrapeIntensity(),
				telemetry.propStrikeCount(),
				formatLastPropStrike(telemetry.lastPropStrikeRotorIndex(), telemetry.lastPropStrikeSeverity()),
				telemetry.propwashIntensity(),
				telemetry.vortexRingStateIntensity(),
				telemetry.vortexRingThrustBuffetAmplitude() * 100.0,
				telemetry.vortexRingBuffetForceNewtons(),
				telemetry.rotorTranslationalLiftIntensity(),
				telemetry.rotorInducedVelocityMetersPerSecond(),
				(1.0 - telemetry.rotorInducedLagThrustScale()) * 100.0,
				telemetry.rotorAdvanceRatio(),
				telemetry.rotorPropellerAdvanceRatioJ(),
				telemetry.rotorPropellerThrustScale(),
				telemetry.rotorPropellerPowerScale(),
				telemetry.rotorReverseFlowInboardFraction(),
				telemetry.rotorTipMach(),
				(1.0 - telemetry.rotorCompressibilityThrustScale()) * 100.0,
				telemetry.rotorLowReynoldsLoss(),
				telemetry.rotorBladeAngleOfAttackDegrees(),
				telemetry.rotorBladeElementStallIntensity(),
				telemetry.rotorBladePassRippleIntensity(),
				telemetry.rotorBladeDissymmetryTorqueNewtonMeters(),
				telemetry.rotorInflowSkewIntensity(),
				telemetry.rotorWakeInterferenceIntensity(),
				(1.0 - telemetry.rotorWakeThrustScale()) * 100.0,
				telemetry.rotorCoaxialLoadBias(),
				(1.0 - telemetry.rotorWetThrustScale()) * 100.0,
				telemetry.rotorWakeSwirlVelocityMetersPerSecond(),
				telemetry.rotorWindmillingIntensity(),
				telemetry.rotorWakeSwirlTorqueNewtonMeters(),
				telemetry.rotorActiveBrakingTorqueNewtonMeters(),
				telemetry.rotorAccelerationReactionTorqueNewtonMeters(),
				telemetry.rotorGyroscopicTorqueNewtonMeters(),
				telemetry.rotorFlappingTorqueNewtonMeters(),
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
		if (telemetry.batteryTwentyPercentSagCurrentMargin() < 1.0) {
			warnings.add("sag-headroom");
		}
		if (telemetry.batteryVoltageSpike() > 0.35) {
			warnings.add("bus-spike");
		}
		if (batteryBusRippleWarning(telemetry)) {
			warnings.add("bus-ripple");
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
		if (telemetry.motorVoltageHeadroom() < 0.18) {
			warnings.add("voltage-headroom");
		}
		if (telemetry.motorWindingResistanceScale() > 1.25) {
			warnings.add("hot-winding");
		}
		if (telemetry.escThermalLimit() < 0.98) {
			warnings.add("esc-thermal-limit");
		}
		if (telemetry.gyroClipIntensity() > 0.05 || telemetry.accelerometerClipIntensity() > 0.05) {
			warnings.add("imu-clip");
		}
		if (telemetry.imuSupplyNoiseIntensity() > 0.35) {
			warnings.add("imu-power-noise");
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
		if (telemetry.airframeSeparatedFlowIntensity() > 0.55) {
			warnings.add("airframe-separation");
		}
		if (telemetry.rotorFlappingTiltDegrees() > 8.0) {
			warnings.add("rotor-flapping");
		}
		if (telemetry.rotorAdvanceRatio() > 0.55) {
			warnings.add("high-advance");
		}
		if (telemetry.rotorPropellerAdvanceRatioJ() > 0.45) {
			warnings.add("prop-advance");
		}
		if (telemetry.rotorPropellerThrustScale() < 0.80) {
			warnings.add("prop-thrust-loss");
		}
		if (telemetry.rotorPropellerPowerScale() < 0.80) {
			warnings.add("prop-power-loss");
		}
		if (telemetry.rotorReverseFlowInboardFraction() > 0.25) {
			warnings.add("reverse-flow");
		}
		if (telemetry.rotorTipMach() > 0.70) {
			warnings.add("tip-mach");
		}
		if (telemetry.rotorCompressibilityThrustScale() < 0.94) {
			warnings.add("compressibility-loss");
		}
		if (telemetry.rotorLowReynoldsLoss() > 0.25) {
			warnings.add("low-re");
		}
		if (telemetry.rotorBladeAngleOfAttackDegrees() > 28.0) {
			warnings.add("blade-aoa");
		}
		if (telemetry.rotorBladeElementStallIntensity() > 0.35) {
			warnings.add("blade-stall");
		}
		if (telemetry.rotorBladePassRippleIntensity() > 0.025) {
			warnings.add("blade-pass-ripple");
		}
		if (telemetry.rotorBladeDissymmetryTorqueNewtonMeters() > 0.015) {
			warnings.add("blade-dissymmetry");
		}
		if (telemetry.vortexRingStateIntensity() > 0.35) {
			warnings.add("vrs");
		}
		if (telemetry.vortexRingThrustBuffetAmplitude() > 0.08 || telemetry.vortexRingBuffetForceNewtons() > 0.20) {
			warnings.add("vrs-buffet");
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
		if (telemetry.rotorWakeThrustScale() < 0.94) {
			warnings.add("wake-thrust-loss");
		}
		if (telemetry.rotorInPlaneDragForceNewtons() > 2.0) {
			warnings.add("rotor-hforce");
		}
		if (telemetry.rotorCoaxialLoadBias() > 0.070) {
			warnings.add("coax-load-bias");
		}
		if (telemetry.rotorInducedLagThrustScale() < 0.93) {
			warnings.add("inflow-lag");
		}
		if (telemetry.rotorWetThrustScale() < 0.96) {
			warnings.add("wet-thrust-loss");
		}
		if (telemetry.rotorWakeSwirlVelocityMetersPerSecond() > 0.75) {
			warnings.add("wake-swirl");
		}
		if (telemetry.rotorWindmillingIntensity() > 0.45) {
			warnings.add("rotor-windmilling");
		}
		if (telemetry.rotorWakeSwirlTorqueNewtonMeters() > 0.010) {
			warnings.add("wake-swirl-torque");
		}
		if (telemetry.rotorActiveBrakingTorqueNewtonMeters() > 0.015) {
			warnings.add("active-brake-torque");
		}
		if (telemetry.rotorAccelerationReactionTorqueNewtonMeters() > 0.015) {
			warnings.add("rotor-accel-torque");
		}
		if (telemetry.rotorGyroscopicTorqueNewtonMeters() > 0.012) {
			warnings.add("rotor-gyro-torque");
		}
		if (telemetry.rotorFlappingTorqueNewtonMeters() > 0.012) {
			warnings.add("rotor-flapping-torque");
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

	private static boolean batteryBusRippleWarning(Telemetry telemetry) {
		double threshold = Math.max(
				BATTERY_BUS_RIPPLE_WARNING_MIN_VOLTS,
				Math.max(1.0, telemetry.batteryVoltage()) * BATTERY_BUS_RIPPLE_WARNING_RATIO
		);
		return telemetry.batteryBusRippleVoltage() > threshold;
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
			double airframeSeparatedFlowIntensity,
			double rotorFlappingTiltDegrees,
			double groundEffectDragForceNewtons,
			double rotorWashDragForceNewtons,
			double rotorWallEffectForceNewtons,
			double barometerAltitudeMeters,
			double barometerVerticalSpeedMetersPerSecond,
			double barometerPressureHectopascals,
			double barometerErrorMeters,
			double batteryVoltage,
			double batterySagVoltage,
			double batteryEffectiveResistanceOhms,
			double batteryRegenerativeCurrentAmps,
			double batteryVoltageSpike,
			double batteryBusRippleVoltage,
			double batteryStateOfCharge,
			double batteryCurrentAmps,
			double batteryTwentyPercentSagCurrentAmps,
			double batteryTwentyPercentSagCurrentMargin,
			double batteryPowerLimit,
			double batteryCurrentLimit,
			double gyroClipIntensity,
			double accelerometerClipIntensity,
			double imuSupplyNoiseIntensity,
			double pidDTermLowPassCutoffHertz,
			double frameHealth,
			double rotorHealth,
			double motorTemperatureCelsius,
			double motorThermalLimit,
			double motorVoltageHeadroom,
			double motorWindingResistanceScale,
			double escTemperatureCelsius,
			double escThermalLimit,
			double escCoolingFactor,
			double escDesyncIntensity,
			double escCommandFrameAgeSeconds,
			double escCommandFrameIntervalSeconds,
			double escCommandError,
			double rotorAerodynamicLoadFactor,
			double rotorInPlaneDragForceNewtons,
			double rotorSurfaceScrapeIntensity,
			double propwashIntensity,
			double vortexRingStateIntensity,
			double vortexRingThrustBuffetAmplitude,
			double vortexRingBuffetForceNewtons,
			double rotorTranslationalLiftIntensity,
			double rotorInducedVelocityMetersPerSecond,
			double rotorInducedLagThrustScale,
			double rotorAdvanceRatio,
			double rotorPropellerAdvanceRatioJ,
			double rotorPropellerThrustScale,
			double rotorPropellerPowerScale,
			double rotorReverseFlowInboardFraction,
			double rotorTipMach,
			double rotorCompressibilityThrustScale,
			double rotorLowReynoldsLoss,
			double rotorBladeAngleOfAttackDegrees,
			double rotorBladeElementStallIntensity,
			double rotorBladePassRippleIntensity,
			double rotorBladeDissymmetryTorqueNewtonMeters,
			double rotorInflowSkewIntensity,
			double rotorWakeInterferenceIntensity,
			double rotorWakeThrustScale,
			double rotorCoaxialLoadBias,
			double rotorWetThrustScale,
			double rotorWakeSwirlVelocityMetersPerSecond,
			double rotorWindmillingIntensity,
			double rotorWakeSwirlTorqueNewtonMeters,
			double rotorActiveBrakingTorqueNewtonMeters,
			double rotorAccelerationReactionTorqueNewtonMeters,
			double rotorGyroscopicTorqueNewtonMeters,
			double rotorFlappingTorqueNewtonMeters,
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
