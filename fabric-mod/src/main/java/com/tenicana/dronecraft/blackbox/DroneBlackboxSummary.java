package com.tenicana.dronecraft.blackbox;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record DroneBlackboxSummary(
		int sampleCount,
		double durationSeconds,
		int maxPhysicsSubsteps,
		double maxPhysicsRateHertz,
		double maxSpeedMetersPerSecond,
		double maxAirspeedMetersPerSecond,
		double maxBatteryCurrentAmps,
		double maxBatteryRegenerativeCurrentAmps,
		double maxBatterySagVoltage,
		double maxBatteryVoltageSpike,
		double maxBatteryBusRippleVoltage,
		double minBatteryVoltage,
		double minBatteryStateOfCharge,
		double minBatteryCurrentLimit,
		double maxBatteryTemperatureCelsius,
		double minBatteryThermalLimit,
		double maxPropwashIntensity,
		double maxVortexRingState,
		double maxRotorTranslationalLiftIntensity,
		double maxRotorAdvanceRatio,
		double maxRotorTipMach,
		double maxRotorLowReynoldsLoss,
		double maxRotorAerodynamicLoadFactor,
		double maxMotorMechanicalLossTorqueNewtonMeters,
		double maxMotorTrackingError,
		double minMotorActuatorAuthority,
		double maxRotorInflowSkewIntensity,
		double maxRotorBladeDissymmetryTorqueNewtonMeters,
		double maxRotorWakeInterferenceIntensity,
		double maxRotorWakeSwirlVelocityMetersPerSecond,
		double maxRotorWakeSwirlTorqueNewtonMeters,
		double maxRotorAngularDragTorqueNewtonMeters,
		double maxAirframeAngularDragTorqueNewtonMeters,
		double maxAirframeSeparatedFlowIntensity,
		double maxAirframeLiftForceNewtons,
		double maxGroundEffectDragForceNewtons,
		double maxRotorWashDragForceNewtons,
		double maxRotorWallEffectForceNewtons,
		double maxContactImpactSpeedMetersPerSecond,
		double maxContactSlipSpeedMetersPerSecond,
		double maxContactBounceSpeedMetersPerSecond,
		double maxContactAngularImpulseDegreesPerSecond,
		double maxBarometerErrorMeters,
		double maxBarometerPropwashErrorMeters,
		double minBarometerPressureHectopascals,
		double maxRotorStallIntensity,
		double maxRotorVibration,
		double maxRotorConingIntensity,
		double maxRotorFlappingTiltDegrees,
		double maxRotorArmFlexIntensity,
		double maxRotorSurfaceScrapeIntensity,
		double maxMixerSaturation,
		double maxMixerLowSaturation,
		double maxMixerHighSaturation,
		double minMixerLowHeadroom,
		double minMixerHighHeadroom,
		double minMixerAxisAuthority,
		double maxMotorTemperatureCelsius,
		double minMotorElectricalEfficiency,
		double minMotorVoltageHeadroom,
		double maxEscTemperatureCelsius,
		double minEscThermalLimit,
		double maxEscDesyncIntensity,
		double maxDroneWakeIntensity,
		double maxWaterImmersionIntensity,
		double maxPrecipitationWetnessIntensity,
		double minAmbientTemperatureCelsius,
		double maxAmbientTemperatureCelsius,
		double maxWindGustSpeedMetersPerSecond,
		double maxWindShearAccelerationMetersPerSecondSquared,
		double maxCeilingEffectMultiplier,
		double maxEnvironmentThrustAsymmetry,
		double maxRotorFlowObstruction,
		double minCeilingClearanceMeters,
		double maxAltitudeMeters,
		double maxControlLinkLossSeconds,
		double maxControlFrameAgeSeconds,
		double maxControlFrameError,
		double minRotorHealth,
		double maxPropStrikeSeverity,
		int propStrikeSamples,
		int propStrikeCount,
		int failsafeSamples,
		int collisionSamples
) {
	private static final double TICKS_PER_SECOND = 20.0;
	private static final Map<String, Integer> COLUMNS = columnIndex();

	public static DroneBlackboxSummary from(DroneBlackboxRecorder recorder) {
		List<DroneBlackboxSample> samples = recorder.snapshot();
		if (samples.isEmpty()) {
			return empty();
		}

		double maxSpeed = 0.0;
		double maxAirspeed = 0.0;
		double maxCurrent = 0.0;
		double maxRegenCurrent = 0.0;
		double maxSag = 0.0;
		double maxVoltageSpike = 0.0;
		double maxBusRipple = 0.0;
		double minVoltage = Double.POSITIVE_INFINITY;
		double minSoc = Double.POSITIVE_INFINITY;
		double minCurrentLimit = Double.POSITIVE_INFINITY;
		double maxBatteryTemperature = 25.0;
		double minBatteryThermalLimit = 1.0;
		double maxPropwash = 0.0;
		double maxVrs = 0.0;
		double maxRotorTranslationalLift = 0.0;
		double maxRotorAdvanceRatio = 0.0;
		double maxRotorTipMach = 0.0;
		double maxRotorLowReynoldsLoss = 0.0;
		double maxRotorAerodynamicLoad = 0.0;
		double maxMotorMechanicalLoss = 0.0;
		double maxMotorTrackingError = 0.0;
		double minMotorActuatorAuthority = 1.0;
		double maxRotorInflowSkew = 0.0;
		double maxRotorBladeDissymmetryTorque = 0.0;
		double maxRotorWakeInterference = 0.0;
		double maxRotorWakeSwirlVelocity = 0.0;
		double maxRotorWakeSwirlTorque = 0.0;
		double maxRotorAngularDrag = 0.0;
		double maxAirframeAngularDrag = 0.0;
		double maxAirframeSeparation = 0.0;
		double maxAirframeLift = 0.0;
		double maxGroundEffectDrag = 0.0;
		double maxRotorWashDrag = 0.0;
		double maxRotorWallEffect = 0.0;
		double maxContactImpact = 0.0;
		double maxContactSlip = 0.0;
		double maxContactBounce = 0.0;
		double maxContactAngularImpulse = 0.0;
		double maxBarometerError = 0.0;
		double maxBarometerPropwashError = 0.0;
		double minBarometerPressure = Double.POSITIVE_INFINITY;
		double maxRotorStall = 0.0;
		double maxRotorVibration = 0.0;
		double maxRotorConing = 0.0;
		double maxRotorFlappingTilt = 0.0;
		double maxRotorArmFlex = 0.0;
		double maxRotorSurfaceScrape = 0.0;
		double maxMixer = 0.0;
		double maxMixerLowSaturation = 0.0;
		double maxMixerHighSaturation = 0.0;
		double minMixerLowHeadroom = 1.0;
		double minMixerHighHeadroom = 1.0;
		double minMixerAxisAuthority = 1.0;
		double maxMotorTemp = 0.0;
		double minMotorElectricalEfficiency = Double.POSITIVE_INFINITY;
		double minMotorVoltageHeadroom = Double.POSITIVE_INFINITY;
		double maxEscTemp = 0.0;
		double minEscThermalLimit = 1.0;
		double maxEscDesync = 0.0;
		double maxWake = 0.0;
		double maxWaterImmersion = 0.0;
		double maxPrecipitationWetness = 0.0;
		double minAmbientTemperature = Double.POSITIVE_INFINITY;
		double maxAmbientTemperature = Double.NEGATIVE_INFINITY;
		double maxWindGust = 0.0;
		double maxWindShear = 0.0;
		double maxCeilingEffect = 1.0;
		double maxEnvironmentAsymmetry = 0.0;
		double maxRotorFlowObstruction = 0.0;
		double minCeilingClearance = Double.POSITIVE_INFINITY;
		double maxAltitude = Double.NEGATIVE_INFINITY;
		double maxLinkLoss = 0.0;
		double maxControlFrameAge = 0.0;
		double maxControlFrameError = 0.0;
		double minRotorHealth = Double.POSITIVE_INFINITY;
		double maxPropStrikeSeverity = 0.0;
		int maxPhysicsSubsteps = 0;
		double maxPhysicsRateHertz = 0.0;
		int propStrikeSamples = 0;
		int propStrikeCount = 0;
		int failsafeSamples = 0;
		int collisionSamples = 0;

		for (DroneBlackboxSample sample : samples) {
			String[] row = sample.toCsvLine().split(",", -1);
			maxPhysicsSubsteps = Math.max(maxPhysicsSubsteps, intValue(row, "physics_substeps"));
			maxPhysicsRateHertz = Math.max(maxPhysicsRateHertz, value(row, "physics_rate_hz"));
			maxSpeed = Math.max(maxSpeed, value(row, "speed_mps"));
			maxAirspeed = Math.max(maxAirspeed, value(row, "airspeed_mps"));
			maxCurrent = Math.max(maxCurrent, value(row, "battery_current_a"));
			maxRegenCurrent = Math.max(maxRegenCurrent, value(row, "battery_regen_current_a"));
			maxSag = Math.max(maxSag, value(row, "battery_ohmic_sag_v") + value(row, "battery_transient_sag_v"));
			maxVoltageSpike = Math.max(maxVoltageSpike, value(row, "battery_voltage_spike_v"));
			maxBusRipple = Math.max(maxBusRipple, value(row, "battery_bus_ripple_v"));
			minVoltage = Math.min(minVoltage, value(row, "battery_voltage"));
			minSoc = Math.min(minSoc, value(row, "battery_soc"));
			minCurrentLimit = Math.min(minCurrentLimit, value(row, "battery_current_limit"));
			maxBatteryTemperature = Math.max(maxBatteryTemperature, valueOrDefault(row, "battery_temp_c", 25.0));
			minBatteryThermalLimit = Math.min(minBatteryThermalLimit, valueOrDefault(row, "battery_thermal_limit", 1.0));
			maxPropwash = Math.max(maxPropwash, value(row, "propwash_intensity"));
			maxVrs = Math.max(maxVrs, value(row, "vortex_ring_state"));
			maxRotorTranslationalLift = Math.max(maxRotorTranslationalLift, value(row, "rotor_translational_lift"));
			maxRotorAdvanceRatio = Math.max(maxRotorAdvanceRatio, value(row, "rotor_advance_ratio"));
			maxRotorTipMach = Math.max(maxRotorTipMach, value(row, "rotor_tip_mach"));
			maxRotorLowReynoldsLoss = Math.max(
					maxRotorLowReynoldsLoss,
					Math.max(value(row, "rotor_low_reynolds_loss"), maxIndexedValue(row, "rotor_", "_low_reynolds_loss"))
			);
			maxRotorAerodynamicLoad = Math.max(maxRotorAerodynamicLoad, value(row, "rotor_aerodynamic_load"));
			maxMotorMechanicalLoss = Math.max(
					maxMotorMechanicalLoss,
					Math.max(value(row, "avg_motor_mechanical_loss_torque_nm"), maxIndexedValue(row, "motor_", "_mechanical_loss_torque_nm"))
			);
			maxMotorTrackingError = Math.max(
					maxMotorTrackingError,
					Math.max(value(row, "avg_motor_tracking_error"), maxIndexedValue(row, "motor_", "_tracking_error"))
			);
			minMotorActuatorAuthority = Math.min(
					minMotorActuatorAuthority,
					Math.min(valueOrDefault(row, "avg_motor_actuator_authority", 1.0), minIndexedValue(row, "motor_", "_actuator_authority", 1.0))
			);
			maxRotorInflowSkew = Math.max(maxRotorInflowSkew, value(row, "rotor_inflow_skew"));
			double bladeDissymmetryPitch = value(row, "rotor_blade_dissymmetry_pitch_torque_nm");
			double bladeDissymmetryYaw = value(row, "rotor_blade_dissymmetry_yaw_torque_nm");
			double bladeDissymmetryRoll = value(row, "rotor_blade_dissymmetry_roll_torque_nm");
			maxRotorBladeDissymmetryTorque = Math.max(
					maxRotorBladeDissymmetryTorque,
					Math.sqrt(bladeDissymmetryPitch * bladeDissymmetryPitch
							+ bladeDissymmetryYaw * bladeDissymmetryYaw
							+ bladeDissymmetryRoll * bladeDissymmetryRoll)
			);
			maxRotorWakeInterference = Math.max(maxRotorWakeInterference, value(row, "rotor_wake_interference"));
			maxRotorWakeSwirlVelocity = Math.max(
					maxRotorWakeSwirlVelocity,
					Math.max(value(row, "rotor_wake_swirl_mps"), maxIndexedValue(row, "rotor_", "_wake_swirl_mps"))
			);
			double wakeSwirlTorquePitch = value(row, "rotor_wake_swirl_pitch_torque_nm");
			double wakeSwirlTorqueYaw = value(row, "rotor_wake_swirl_yaw_torque_nm");
			double wakeSwirlTorqueRoll = value(row, "rotor_wake_swirl_roll_torque_nm");
			maxRotorWakeSwirlTorque = Math.max(
					maxRotorWakeSwirlTorque,
					Math.sqrt(wakeSwirlTorquePitch * wakeSwirlTorquePitch
							+ wakeSwirlTorqueYaw * wakeSwirlTorqueYaw
							+ wakeSwirlTorqueRoll * wakeSwirlTorqueRoll)
			);
			double rotorAngularDragPitch = value(row, "rotor_angular_drag_pitch_torque_nm");
			double rotorAngularDragYaw = value(row, "rotor_angular_drag_yaw_torque_nm");
			double rotorAngularDragRoll = value(row, "rotor_angular_drag_roll_torque_nm");
			maxRotorAngularDrag = Math.max(
					maxRotorAngularDrag,
					Math.sqrt(rotorAngularDragPitch * rotorAngularDragPitch + rotorAngularDragYaw * rotorAngularDragYaw + rotorAngularDragRoll * rotorAngularDragRoll)
			);
			double angularDragPitch = value(row, "airframe_angular_drag_pitch_torque_nm");
			double angularDragYaw = value(row, "airframe_angular_drag_yaw_torque_nm");
			double angularDragRoll = value(row, "airframe_angular_drag_roll_torque_nm");
			maxAirframeAngularDrag = Math.max(
					maxAirframeAngularDrag,
					Math.sqrt(angularDragPitch * angularDragPitch + angularDragYaw * angularDragYaw + angularDragRoll * angularDragRoll)
			);
			maxAirframeSeparation = Math.max(maxAirframeSeparation, value(row, "airframe_separation"));
			maxAirframeLift = Math.max(maxAirframeLift, value(row, "airframe_lift_n"));
			maxGroundEffectDrag = Math.max(maxGroundEffectDrag, value(row, "ground_effect_drag_n"));
			maxRotorWashDrag = Math.max(maxRotorWashDrag, value(row, "rotor_wash_drag_n"));
			maxRotorWallEffect = Math.max(maxRotorWallEffect, value(row, "rotor_wall_effect_n"));
			maxContactImpact = Math.max(maxContactImpact, value(row, "contact_impact_mps"));
			maxContactSlip = Math.max(maxContactSlip, value(row, "contact_slip_mps"));
			maxContactBounce = Math.max(maxContactBounce, value(row, "contact_bounce_mps"));
			maxContactAngularImpulse = Math.max(maxContactAngularImpulse, value(row, "contact_angular_impulse_dps"));
			maxBarometerError = Math.max(maxBarometerError, Math.abs(value(row, "barometer_error_m")));
			maxBarometerPropwashError = Math.max(maxBarometerPropwashError, Math.abs(value(row, "barometer_propwash_error_m")));
			minBarometerPressure = Math.min(minBarometerPressure, value(row, "barometer_pressure_hpa"));
			maxRotorStall = Math.max(maxRotorStall, value(row, "rotor_stall_intensity"));
			maxRotorVibration = Math.max(maxRotorVibration, value(row, "rotor_vibration"));
			maxRotorConing = Math.max(maxRotorConing, value(row, "rotor_coning"));
			maxRotorFlappingTilt = Math.max(
					maxRotorFlappingTilt,
					Math.max(value(row, "rotor_flapping_tilt_deg"), maxIndexedValue(row, "rotor_", "_flapping_tilt_deg"))
			);
			maxRotorArmFlex = Math.max(maxRotorArmFlex, value(row, "rotor_arm_flex"));
			maxRotorSurfaceScrape = Math.max(maxRotorSurfaceScrape, value(row, "rotor_surface_scrape"));
			maxMixer = Math.max(maxMixer, value(row, "mixer_saturation"));
			maxMixerLowSaturation = Math.max(maxMixerLowSaturation, valueOrDefault(row, "mixer_low_saturation", 0.0));
			maxMixerHighSaturation = Math.max(maxMixerHighSaturation, valueOrDefault(row, "mixer_high_saturation", 0.0));
			minMixerLowHeadroom = Math.min(minMixerLowHeadroom, valueOrDefault(row, "mixer_low_headroom", 1.0));
			minMixerHighHeadroom = Math.min(minMixerHighHeadroom, valueOrDefault(row, "mixer_high_headroom", 1.0));
			minMixerAxisAuthority = Math.min(
					minMixerAxisAuthority,
					valueOrDefault(row, "mixer_min_axis_authority", 1.0)
			);
			maxMotorTemp = Math.max(maxMotorTemp, value(row, "motor_temp_c"));
			minMotorElectricalEfficiency = Math.min(
					minMotorElectricalEfficiency,
					minPositiveIndexedValue(row, "motor_", "_electrical_efficiency", Double.POSITIVE_INFINITY)
			);
			minMotorVoltageHeadroom = Math.min(
					minMotorVoltageHeadroom,
					minIndexedValue(row, "motor_", "_voltage_headroom", Double.POSITIVE_INFINITY)
			);
			maxEscTemp = Math.max(maxEscTemp, value(row, "max_esc_temp_c"));
			minEscThermalLimit = Math.min(minEscThermalLimit, value(row, "esc_thermal_limit"));
			maxEscDesync = Math.max(maxEscDesync, value(row, "esc_desync"));
			maxWake = Math.max(maxWake, value(row, "drone_wake_intensity"));
			maxWaterImmersion = Math.max(
					maxWaterImmersion,
					Math.max(value(row, "water_immersion"), maxIndexedValue(row, "rotor_", "_water_immersion"))
			);
			maxPrecipitationWetness = Math.max(maxPrecipitationWetness, value(row, "precipitation_wetness"));
			double ambientTemperature = value(row, "ambient_temperature_c");
			minAmbientTemperature = Math.min(minAmbientTemperature, ambientTemperature);
			maxAmbientTemperature = Math.max(maxAmbientTemperature, ambientTemperature);
			maxWindGust = Math.max(maxWindGust, value(row, "wind_gust_speed_mps"));
			maxWindShear = Math.max(maxWindShear, value(row, "wind_shear_accel_mps2"));
			maxCeilingEffect = Math.max(maxCeilingEffect, value(row, "ceiling_effect_multiplier"));
			maxEnvironmentAsymmetry = Math.max(maxEnvironmentAsymmetry, value(row, "env_thrust_asymmetry"));
			maxRotorFlowObstruction = Math.max(maxRotorFlowObstruction, value(row, "rotor_flow_obstruction"));
			double ceilingClearance = value(row, "ceiling_clearance_m");
			if (ceilingClearance >= 0.0) {
				minCeilingClearance = Math.min(minCeilingClearance, ceilingClearance);
			}
			maxAltitude = Math.max(maxAltitude, value(row, "y"));
			maxLinkLoss = Math.max(maxLinkLoss, value(row, "control_link_loss_s"));
			maxControlFrameAge = Math.max(maxControlFrameAge, value(row, "control_frame_age_s"));
			maxControlFrameError = Math.max(maxControlFrameError, value(row, "control_frame_error"));
			minRotorHealth = Math.min(minRotorHealth, minIndexedValue(row, "rotor_", "_health", 1.0));
			maxPropStrikeSeverity = Math.max(maxPropStrikeSeverity, value(row, "prop_strike_severity"));
			maxPropStrikeSeverity = Math.max(maxPropStrikeSeverity, maxIndexedValue(row, "prop_strike_", "_severity"));
			propStrikeCount = Math.max(propStrikeCount, intValue(row, "prop_strike_count"));
			if (boolValue(row, "prop_strike")) {
				propStrikeSamples++;
			}
			if (boolValue(row, "control_failsafe")) {
				failsafeSamples++;
			}
			if (value(row, "collision_severity") > 0.001 || value(row, "contact_impact_mps") > 0.001) {
				collisionSamples++;
			}
		}

		return new DroneBlackboxSummary(
				samples.size(),
				samples.size() / TICKS_PER_SECOND,
				maxPhysicsSubsteps,
				maxPhysicsRateHertz,
				maxSpeed,
				maxAirspeed,
				maxCurrent,
				maxRegenCurrent,
				maxSag,
				maxVoltageSpike,
				maxBusRipple,
				finiteOrZero(minVoltage),
				finiteOrZero(minSoc),
				finiteOrZero(minCurrentLimit),
				maxBatteryTemperature,
				finiteOrZero(minBatteryThermalLimit),
				maxPropwash,
				maxVrs,
				maxRotorTranslationalLift,
				maxRotorAdvanceRatio,
				maxRotorTipMach,
				maxRotorLowReynoldsLoss,
				maxRotorAerodynamicLoad,
				maxMotorMechanicalLoss,
				maxMotorTrackingError,
				finiteOrOne(minMotorActuatorAuthority),
				maxRotorInflowSkew,
				maxRotorBladeDissymmetryTorque,
				maxRotorWakeInterference,
				maxRotorWakeSwirlVelocity,
				maxRotorWakeSwirlTorque,
				maxRotorAngularDrag,
				maxAirframeAngularDrag,
				maxAirframeSeparation,
				maxAirframeLift,
				maxGroundEffectDrag,
				maxRotorWashDrag,
				maxRotorWallEffect,
				maxContactImpact,
				maxContactSlip,
				maxContactBounce,
				maxContactAngularImpulse,
				maxBarometerError,
				maxBarometerPropwashError,
				finiteOrZero(minBarometerPressure),
				maxRotorStall,
				maxRotorVibration,
				maxRotorConing,
				maxRotorFlappingTilt,
				maxRotorArmFlex,
				maxRotorSurfaceScrape,
				maxMixer,
				maxMixerLowSaturation,
				maxMixerHighSaturation,
				finiteOrOne(minMixerLowHeadroom),
				finiteOrOne(minMixerHighHeadroom),
				finiteOrOne(minMixerAxisAuthority),
				maxMotorTemp,
				finiteOrZero(minMotorElectricalEfficiency),
				finiteOrZero(minMotorVoltageHeadroom),
				maxEscTemp,
				finiteOrZero(minEscThermalLimit),
				maxEscDesync,
				maxWake,
				maxWaterImmersion,
				maxPrecipitationWetness,
				finiteOrZero(minAmbientTemperature),
				finiteOrZero(maxAmbientTemperature),
				maxWindGust,
				maxWindShear,
				maxCeilingEffect,
				maxEnvironmentAsymmetry,
				maxRotorFlowObstruction,
				finiteOrZero(minCeilingClearance),
				finiteOrZero(maxAltitude),
				maxLinkLoss,
				maxControlFrameAge,
				maxControlFrameError,
				finiteOrZero(minRotorHealth),
				maxPropStrikeSeverity,
				propStrikeSamples,
				propStrikeCount,
				failsafeSamples,
				collisionSamples
		);
	}

	public boolean hasSamples() {
		return sampleCount > 0;
	}

	public String formatForChat() {
		if (!hasSamples()) {
			return "Blackbox summary: no samples.";
		}
		return String.format(
				Locale.ROOT,
				"Blackbox %.1fs/%d samples | loop %d@%.0fHz | max speed %.2fm/s air %.2fm/s contact %.2f/%.2f/%.2fm/s %.0fd/s | battery min %.2fV sag %.2fV spike %.2fV ripple %.3fV current %.1fA regen %.1fA soc %.1f%% current-limit %.2f temp %.1fC batt-limit %.2f | propwash %.2f VRS %.2f ETL %.2f adv %.2f tipmach %.2f lowre %.2f load %.2f mech-loss %.4fNm track %.3f auth %.2f skew %.2f bdiss %.3fNm rwake %.2f swirl %.2fm/s swirlT %.3fNm rdamp %.3f ang-drag %.3f sep %.2f lift %.2fN cushion %.2fN wash %.2fN wall %.2fN baro err %.2fm wash %.2fm min %.1fhPa wake %.2f water %.2f rain %.2f temp %.1f..%.1fC gust %.2fm/s shear %.2fm/s2 ceil %.2f/%s asym %.2f block %.2f stall %.2f vib %.2f coning %.2f flap %.1fdeg flex %.2f scrape %.2f mixer %.2f mix-auth %.2f mix-edge %.2f/%.2f mix-head %.2f/%.2f desync %.2f | motor %.1fC eff %.2f headroom %.2f esc %.1fC limit %.2f rotor min %.1f%% prop-strike %d samples max %.2f count %d | alt %.1fm link-loss %.2fs rc-frame %.3fs err %.4f failsafe %d collision %d",
				durationSeconds,
				sampleCount,
				maxPhysicsSubsteps,
				maxPhysicsRateHertz,
				maxSpeedMetersPerSecond,
				maxAirspeedMetersPerSecond,
				maxContactImpactSpeedMetersPerSecond,
				maxContactSlipSpeedMetersPerSecond,
				maxContactBounceSpeedMetersPerSecond,
				maxContactAngularImpulseDegreesPerSecond,
				minBatteryVoltage,
				maxBatterySagVoltage,
				maxBatteryVoltageSpike,
				maxBatteryBusRippleVoltage,
				maxBatteryCurrentAmps,
				maxBatteryRegenerativeCurrentAmps,
				minBatteryStateOfCharge * 100.0,
				minBatteryCurrentLimit,
				maxBatteryTemperatureCelsius,
				minBatteryThermalLimit,
				maxPropwashIntensity,
				maxVortexRingState,
				maxRotorTranslationalLiftIntensity,
				maxRotorAdvanceRatio,
				maxRotorTipMach,
				maxRotorLowReynoldsLoss,
				maxRotorAerodynamicLoadFactor,
				maxMotorMechanicalLossTorqueNewtonMeters,
				maxMotorTrackingError,
				minMotorActuatorAuthority,
				maxRotorInflowSkewIntensity,
				maxRotorBladeDissymmetryTorqueNewtonMeters,
				maxRotorWakeInterferenceIntensity,
				maxRotorWakeSwirlVelocityMetersPerSecond,
				maxRotorWakeSwirlTorqueNewtonMeters,
				maxRotorAngularDragTorqueNewtonMeters,
				maxAirframeAngularDragTorqueNewtonMeters,
				maxAirframeSeparatedFlowIntensity,
				maxAirframeLiftForceNewtons,
				maxGroundEffectDragForceNewtons,
				maxRotorWashDragForceNewtons,
				maxRotorWallEffectForceNewtons,
				maxBarometerErrorMeters,
				maxBarometerPropwashErrorMeters,
				minBarometerPressureHectopascals,
				maxDroneWakeIntensity,
				maxWaterImmersionIntensity,
				maxPrecipitationWetnessIntensity,
				minAmbientTemperatureCelsius,
				maxAmbientTemperatureCelsius,
				maxWindGustSpeedMetersPerSecond,
				maxWindShearAccelerationMetersPerSecondSquared,
				maxCeilingEffectMultiplier,
				formatCeilingClearance(minCeilingClearanceMeters),
				maxEnvironmentThrustAsymmetry,
				maxRotorFlowObstruction,
				maxRotorStallIntensity,
				maxRotorVibration,
				maxRotorConingIntensity,
				maxRotorFlappingTiltDegrees,
				maxRotorArmFlexIntensity,
				maxRotorSurfaceScrapeIntensity,
				maxMixerSaturation,
				minMixerAxisAuthority,
				maxMixerLowSaturation,
				maxMixerHighSaturation,
				minMixerLowHeadroom,
				minMixerHighHeadroom,
				maxEscDesyncIntensity,
				maxMotorTemperatureCelsius,
				minMotorElectricalEfficiency,
				minMotorVoltageHeadroom,
				maxEscTemperatureCelsius,
				minEscThermalLimit,
				minRotorHealth * 100.0,
				propStrikeSamples,
				maxPropStrikeSeverity,
				propStrikeCount,
				maxAltitudeMeters,
				maxControlLinkLossSeconds,
				maxControlFrameAgeSeconds,
				maxControlFrameError,
				failsafeSamples,
				collisionSamples
		);
	}

	private static DroneBlackboxSummary empty() {
		return new DroneBlackboxSummary(
				0, // sampleCount
				0.0, // durationSeconds
				0, // maxPhysicsSubsteps
				0.0, // maxPhysicsRateHertz
				0.0, // maxSpeedMetersPerSecond
				0.0, // maxAirspeedMetersPerSecond
				0.0, // maxBatteryCurrentAmps
				0.0, // maxBatteryRegenerativeCurrentAmps
				0.0, // maxBatterySagVoltage
				0.0, // maxBatteryVoltageSpike
				0.0, // maxBatteryBusRippleVoltage
				0.0, // minBatteryVoltage
				0.0, // minBatteryStateOfCharge
				1.0, // minBatteryCurrentLimit
				25.0, // maxBatteryTemperatureCelsius
				1.0, // minBatteryThermalLimit
				0.0, // maxPropwashIntensity
				0.0, // maxVortexRingState
				0.0, // maxRotorTranslationalLiftIntensity
				0.0, // maxRotorAdvanceRatio
				0.0, // maxRotorTipMach
				0.0, // maxRotorLowReynoldsLoss
				0.0, // maxRotorAerodynamicLoadFactor
				0.0, // maxMotorMechanicalLossTorqueNewtonMeters
				0.0, // maxMotorTrackingError
				1.0, // minMotorActuatorAuthority
				0.0, // maxRotorInflowSkewIntensity
				0.0, // maxRotorBladeDissymmetryTorqueNewtonMeters
				0.0, // maxRotorWakeInterferenceIntensity
				0.0, // maxRotorWakeSwirlVelocityMetersPerSecond
				0.0, // maxRotorWakeSwirlTorqueNewtonMeters
				0.0, // maxRotorAngularDragTorqueNewtonMeters
				0.0, // maxAirframeAngularDragTorqueNewtonMeters
				0.0, // maxAirframeSeparatedFlowIntensity
				0.0, // maxAirframeLiftForceNewtons
				0.0, // maxGroundEffectDragForceNewtons
				0.0, // maxRotorWashDragForceNewtons
				0.0, // maxRotorWallEffectForceNewtons
				0.0, // maxContactImpactSpeedMetersPerSecond
				0.0, // maxContactSlipSpeedMetersPerSecond
				0.0, // maxContactBounceSpeedMetersPerSecond
				0.0, // maxContactAngularImpulseDegreesPerSecond
				0.0, // maxBarometerErrorMeters
				0.0, // maxBarometerPropwashErrorMeters
				1013.25, // minBarometerPressureHectopascals
				0.0, // maxRotorStallIntensity
				0.0, // maxRotorVibration
				0.0, // maxRotorConingIntensity
				0.0, // maxRotorFlappingTiltDegrees
				0.0, // maxRotorArmFlexIntensity
				0.0, // maxRotorSurfaceScrapeIntensity
				0.0, // maxMixerSaturation
				0.0, // maxMixerLowSaturation
				0.0, // maxMixerHighSaturation
				1.0, // minMixerLowHeadroom
				1.0, // minMixerHighHeadroom
				1.0, // minMixerAxisAuthority
				0.0, // maxMotorTemperatureCelsius
				0.0, // minMotorElectricalEfficiency
				1.0, // minMotorVoltageHeadroom
				0.0, // maxEscTemperatureCelsius
				1.0, // minEscThermalLimit
				0.0, // maxEscDesyncIntensity
				0.0, // maxDroneWakeIntensity
				0.0, // maxWaterImmersionIntensity
				0.0, // maxPrecipitationWetnessIntensity
				0.0, // minAmbientTemperatureCelsius
				0.0, // maxAmbientTemperatureCelsius
				0.0, // maxWindGustSpeedMetersPerSecond
				0.0, // maxWindShearAccelerationMetersPerSecondSquared
				1.0, // maxCeilingEffectMultiplier
				0.0, // maxEnvironmentThrustAsymmetry
				0.0, // maxRotorFlowObstruction
				0.0, // minCeilingClearanceMeters
				0.0, // maxAltitudeMeters
				0.0, // maxControlLinkLossSeconds
				0.0, // maxControlFrameAgeSeconds
				0.0, // maxControlFrameError
				0.0, // minRotorHealth
				0.0, // maxPropStrikeSeverity
				0, // propStrikeSamples
				0, // propStrikeCount
				0, // failsafeSamples
				0 // collisionSamples
		);
	}

	private static double value(String[] row, String column) {
		return valueOrDefault(row, column, 0.0);
	}

	private static double valueOrDefault(String[] row, String column, double fallback) {
		int index = index(column);
		if (index >= row.length) {
			return fallback;
		}
		try {
			return Double.parseDouble(row[index]);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static boolean boolValue(String[] row, String column) {
		int index = index(column);
		return index < row.length && Boolean.parseBoolean(row[index]);
	}

	private static int intValue(String[] row, String column) {
		int index = index(column);
		if (index >= row.length) {
			return 0;
		}
		try {
			return Integer.parseInt(row[index]);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static double minIndexedValue(String[] row, String prefix, String suffix, double fallback) {
		double min = Double.POSITIVE_INFINITY;
		for (String column : COLUMNS.keySet()) {
			if (isIndexedColumn(column, prefix, suffix)) {
				min = Math.min(min, value(row, column));
			}
		}
		return Double.isFinite(min) ? min : fallback;
	}

	private static double minPositiveIndexedValue(String[] row, String prefix, String suffix, double fallback) {
		double min = Double.POSITIVE_INFINITY;
		for (String column : COLUMNS.keySet()) {
			if (isIndexedColumn(column, prefix, suffix)) {
				double value = value(row, column);
				if (value > 1.0e-9) {
					min = Math.min(min, value);
				}
			}
		}
		return Double.isFinite(min) ? min : fallback;
	}

	private static double maxIndexedValue(String[] row, String prefix, String suffix) {
		double max = 0.0;
		for (String column : COLUMNS.keySet()) {
			if (isIndexedColumn(column, prefix, suffix)) {
				max = Math.max(max, value(row, column));
			}
		}
		return max;
	}

	private static boolean isIndexedColumn(String column, String prefix, String suffix) {
		if (!column.startsWith(prefix)
				|| !column.endsWith(suffix)
				|| column.length() <= prefix.length() + suffix.length()) {
			return false;
		}
		String index = column.substring(prefix.length(), column.length() - suffix.length());
		if (index.isEmpty()) {
			return false;
		}
		for (int i = 0; i < index.length(); i++) {
			if (!Character.isDigit(index.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static int index(String column) {
		Integer index = COLUMNS.get(column);
		if (index == null) {
			throw new IllegalArgumentException("Missing blackbox column: " + column);
		}
		return index;
	}

	private static Map<String, Integer> columnIndex() {
		String[] columns = DroneBlackboxSample.CSV_HEADER.split(",", -1);
		Map<String, Integer> indices = new HashMap<>();
		for (int i = 0; i < columns.length; i++) {
			indices.put(columns[i], i);
		}
		return Map.copyOf(indices);
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double finiteOrOne(double value) {
		return Double.isFinite(value) ? value : 1.0;
	}

	private static String formatCeilingClearance(double value) {
		return value > 0.0 ? String.format(Locale.ROOT, "%.2fm", value) : "open";
	}
}
