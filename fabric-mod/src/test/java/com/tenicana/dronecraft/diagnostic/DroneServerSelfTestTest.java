package com.tenicana.dronecraft.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tenicana.dronecraft.debug.DroneDebugSettings.FlightModelMode;
import com.tenicana.dronecraft.sim.FlightMode;

class DroneServerSelfTestTest {
	@Test
	void reportJsonIncludesBatteryResistanceScaleAndAerodynamicTelemetry(@TempDir Path tempDir) throws ReflectiveOperationException {
		DroneServerSelfTest selfTest = newSelfTest();
		setDouble(selfTest, "initialX", 1.0);
		setDouble(selfTest, "initialY", 80.0);
		setDouble(selfTest, "initialZ", -2.0);
		setDouble(selfTest, "finalX", 1.30);
		setDouble(selfTest, "finalY", 80.12);
		setDouble(selfTest, "finalZ", -1.60);
		setDouble(selfTest, "minPlayableLowAltitudeAuthority", 0.62);
		setDouble(selfTest, "maxPlayableVisualPitchDegrees", 3.4);
		setDouble(selfTest, "maxPlayableVisualRollDegrees", 4.2);
		setDouble(selfTest, "maxPlayableVisualYawRateDegreesPerSecond", 72.0);
		setDouble(selfTest, "finalPlayableVisualYawDriftDegrees", 12.0);
		setInt(selfTest, "playableNeutralSampleCount", 87);
		setDouble(selfTest, "maxPlayableNeutralVisualPitchDegrees", 0.34);
		setDouble(selfTest, "maxPlayableNeutralVisualRollDegrees", 0.18);
		setDouble(selfTest, "maxPlayableNeutralVisualYawRateDegreesPerSecond", 0.05);
		setDouble(selfTest, "finalSpeed", 0.08);
		setDouble(selfTest, "finalAltitudeGain", 0.12);
		setDouble(selfTest, "finalHorizontalDistance", 0.50);
		setDouble(selfTest, "maxBatteryEffectiveResistance", 0.024);
		setDouble(selfTest, "maxBatteryStateOfChargeResistanceScale", 1.23);
		setDouble(selfTest, "maxBatteryTemperatureResistanceScale", 1.34);
		setDouble(selfTest, "maxBatteryPolarizationResistanceScale", 1.45);
		setDouble(selfTest, "maxAverageMotorTelemetryRpm", 18750.0);
		setDouble(selfTest, "maxMotor5TelemetryRpm", 18125.0);
		setDouble(selfTest, "maxAverageMotorTelemetryErpm100", 1312.5);
		setDouble(selfTest, "maxMotor5TelemetryErpm100", 1268.75);
		setDouble(selfTest, "minAverageMotorTelemetryEIntervalMicros", 457.14);
		setDouble(selfTest, "minMotor5TelemetryEIntervalMicros", 472.39);
		setDouble(selfTest, "maxAverageMotorRpmTelemetryValidity", 0.97);
		setDouble(selfTest, "maxMotor5RpmTelemetryValidity", 0.93);
		setDouble(selfTest, "maxGyroNotchFrequency", 312.5);
		setDouble(selfTest, "maxGyroNotchAttenuation", 0.38);
		setDouble(selfTest, "maxGyroNotchSpread", 24.25);
		setDouble(selfTest, "maxGyroRpmHarmonicNotchAttenuation", 0.19);
		setDouble(selfTest, "maxGyroBladePassNotchFrequency", 625.0);
		setDouble(selfTest, "maxGyroBladePassNotchAttenuation", 0.44);
		setDouble(selfTest, "maxGyroBladePassNotchSpread", 48.5);
		setDouble(selfTest, "maxVortexRingThrustBuffet", 0.56);
		setDouble(selfTest, "maxVortexRingBuffetForce", 0.78);
		setDouble(selfTest, "maxRotorInducedVelocity", 4.25);
		setDouble(selfTest, "minRotorInducedLagThrustScale", 0.91);
		setDouble(selfTest, "maxRotorDynamicInflowTimeConstant", 0.037);
		setDouble(selfTest, "maxRotorTranslationalLift", 0.44);
		setDouble(selfTest, "maxRotorPropellerAdvanceRatioJ", 0.62);
		setDouble(selfTest, "minRotorPropellerThrustScale", 0.88);
		setDouble(selfTest, "minRotorPropellerPowerScale", 0.83);
		setDouble(selfTest, "maxRotorReverseFlow", 0.17);
		setDouble(selfTest, "maxRotorLowReynoldsLoss", 0.13);
		setDouble(selfTest, "maxRotorBladePassRipple", 0.021);
		setDouble(selfTest, "maxRotorDamageVibration", 0.073);
		setDouble(selfTest, "minRotorWetThrustScale", 0.94);
		setDouble(selfTest, "maxRotorIcingSeverity", 0.27);
		setDouble(selfTest, "minRotorIcingThrustScale", 0.947);
		setDouble(selfTest, "maxRotorIcingPowerScale", 1.16);
		setDouble(selfTest, "maxRotorCoaxialLoadBias", 0.083);
		setDouble(selfTest, "maxRotorCoaxialLoadBiasTarget", 0.115);
		setDouble(selfTest, "maxRotorCoaxialLoadBiasClipping", 0.012);
		setDouble(selfTest, "maxRotorCoaxialAllocationLoadFraction", 0.60);
		setDouble(selfTest, "maxRotorCoaxialAllocationCommandRatio", 1.33);
		setDouble(selfTest, "maxRotorCoaxialAllocationMechanicalGainPercent", 4.41);
		setDouble(selfTest, "maxRotorCoaxialAllocationElectricalGainPercent", 2.84);
		setDouble(selfTest, "maxRotorCoaxialAllocationUncertaintyPercent", 8.61);
		setDouble(selfTest, "maxGroundEffectLevelingTorque", 0.03125);
		setDouble(selfTest, "maxAirframeBodyDragForce", 0.65);
		setDouble(selfTest, "maxLinearDampingDragForce", 1.85);
		setDouble(selfTest, "maxAirframeDragAlongFlow", 2.42);
		setDouble(selfTest, "maxAirframeDragEquivalentLinearCoefficient", 0.242);
		setDouble(selfTest, "maxAirframeDragEquivalentCdA", 0.0395);
		setDouble(selfTest, "maxAirframeDragImavReferenceRatio", 1.21);

		String json = reportJson(selfTest, tempDir.resolve("server-selftest.csv"));

		assertTrue(json.contains("\"flight_model\": \"simulation\""));
		assertTrue(json.contains("\"flight_mode\": \"unknown\""));
		assertTrue(json.contains("\"self_test_control_mode\": \"angle\""));
		assertTrue(json.contains("\"min_playable_low_altitude_authority\": 0.62000"));
		assertTrue(json.contains("\"max_playable_low_altitude_suppression_percent\": 38.000"));
		assertTrue(json.contains("\"max_playable_visual_pitch_deg\": 3.4000"));
		assertTrue(json.contains("\"max_playable_visual_roll_deg\": 4.2000"));
		assertTrue(json.contains("\"max_playable_visual_yaw_rate_dps\": 72.0000"));
		assertTrue(json.contains("\"final_playable_visual_yaw_drift_deg\": 12.0000"));
		assertTrue(json.contains("\"playable_neutral_sample_count\": 87"));
		assertTrue(json.contains("\"max_playable_neutral_visual_pitch_deg\": 0.3400"));
		assertTrue(json.contains("\"max_playable_neutral_visual_roll_deg\": 0.1800"));
		assertTrue(json.contains("\"max_playable_neutral_visual_yaw_rate_dps\": 0.0500"));
		assertTrue(json.contains("\"final_speed_mps\": 0.08000"));
		assertTrue(json.contains("\"final_altitude_gain_m\": 0.12000"));
		assertTrue(json.contains("\"final_horizontal_distance_m\": 0.50000"));
		assertTrue(json.contains("\"max_battery_effective_resistance_ohm\": 0.024000"));
		assertTrue(json.contains("\"max_battery_soc_resistance_scale\": 1.23000"));
		assertTrue(json.contains("\"max_battery_temp_resistance_scale\": 1.34000"));
		assertTrue(json.contains("\"max_battery_polarization_resistance_scale\": 1.45000"));
		assertTrue(json.contains("\"max_avg_motor_rpm_telemetry_rpm\": 18750.00"));
		assertTrue(json.contains("\"max_motor_5_rpm_telemetry_rpm\": 18125.00"));
		assertTrue(json.contains("\"max_avg_motor_erpm100\": 1312.50"));
		assertTrue(json.contains("\"max_motor_5_erpm100\": 1268.75"));
		assertTrue(json.contains("\"min_avg_motor_einterval_us\": 457.14"));
		assertTrue(json.contains("\"min_motor_5_einterval_us\": 472.39"));
		assertTrue(json.contains("\"max_avg_motor_rpm_telemetry_valid\": 0.97000"));
		assertTrue(json.contains("\"max_motor_5_rpm_telemetry_valid\": 0.93000"));
		assertTrue(json.contains("\"max_gyro_notch_hz\": 312.50000"));
		assertTrue(json.contains("\"max_gyro_notch_attenuation\": 0.38000"));
		assertTrue(json.contains("\"max_gyro_notch_spread_hz\": 24.25000"));
		assertTrue(json.contains("\"max_gyro_rpm_harmonic_notch_attenuation\": 0.19000"));
		assertTrue(json.contains("\"max_gyro_blade_pass_notch_hz\": 625.00000"));
		assertTrue(json.contains("\"max_gyro_blade_pass_notch_attenuation\": 0.44000"));
		assertTrue(json.contains("\"max_gyro_blade_pass_notch_spread_hz\": 48.50000"));
		assertTrue(json.contains("\"max_vortex_ring_thrust_buffet\": 0.56000"));
		assertTrue(json.contains("\"max_vortex_ring_buffet_force_n\": 0.78000"));
		assertTrue(json.contains("\"max_rotor_induced_velocity_mps\": 4.25000"));
		assertTrue(json.contains("\"max_rotor_inflow_lag_loss_percent\": 9.000"));
		assertTrue(json.contains("\"max_rotor_dynamic_inflow_tau_s\": 0.03700"));
		assertTrue(json.contains("\"max_rotor_translational_lift\": 0.44000"));
		assertTrue(json.contains("\"max_rotor_propeller_advance_ratio_j\": 0.62000"));
		assertTrue(json.contains("\"max_rotor_propeller_thrust_loss_percent\": 12.000"));
		assertTrue(json.contains("\"max_rotor_propeller_power_loss_percent\": 17.000"));
		assertTrue(json.contains("\"max_rotor_reverse_flow\": 0.17000"));
		assertTrue(json.contains("\"max_rotor_low_reynolds_loss\": 0.13000"));
		assertTrue(json.contains("\"max_rotor_blade_pass_ripple\": 0.02100"));
		assertTrue(json.contains("\"max_rotor_damage_vibration\": 0.07300"));
		assertTrue(json.contains("\"max_rotor_wet_thrust_loss_percent\": 6.000"));
		assertTrue(json.contains("\"max_rotor_icing_severity\": 0.27000"));
		assertTrue(json.contains("\"max_rotor_icing_thrust_loss_percent\": 5.300"));
		assertTrue(json.contains("\"max_rotor_icing_power_scale\": 1.16000"));
		assertTrue(json.contains("\"max_rotor_coaxial_load_bias\": 0.08300"));
		assertTrue(json.contains("\"max_rotor_coaxial_load_bias_target\": 0.11500"));
		assertTrue(json.contains("\"max_rotor_coaxial_load_bias_clipping\": 0.01200"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_load\": 0.60000"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_ratio\": 1.33000"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_mech_gain_pct\": 4.41000"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_elec_gain_pct\": 2.84000"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_uncertainty_pct\": 8.61000"));
		assertTrue(json.contains("\"max_ground_effect_leveling_torque_nm\": 0.031250"));
		assertTrue(json.contains("\"max_airframe_body_drag_n\": 0.65000"));
		assertTrue(json.contains("\"max_linear_damping_drag_n\": 1.85000"));
		assertTrue(json.contains("\"max_airframe_drag_along_flow_n\": 2.42000"));
		assertTrue(json.contains("\"max_airframe_drag_equivalent_linear_k\": 0.24200"));
		assertTrue(json.contains("\"max_airframe_drag_equivalent_cda_m2\": 0.03950"));
		assertTrue(json.contains("\"max_airframe_drag_imav_ratio\": 1.21000"));
	}

	@Test
	void reportJsonRecordsPlayableFlightModel(@TempDir Path tempDir) throws ReflectiveOperationException {
		DroneServerSelfTest selfTest = newSelfTest(FlightModelMode.PLAYABLE);

		String json = reportJson(selfTest, tempDir.resolve("server-selftest-playable.csv"));

		assertTrue(json.contains("\"flight_model\": \"playable\""));
		assertTrue(json.contains("\"flight_mode\": \"unknown\""));
		assertTrue(json.contains("\"self_test_control_mode\": \"angle\""));
		assertTrue(json.contains("\"min_playable_low_altitude_authority\": 1.00000"));
		assertTrue(json.contains("\"max_playable_low_altitude_suppression_percent\": 0.000"));
		assertTrue(json.contains("\"max_playable_visual_pitch_deg\": 0.0000"));
		assertTrue(json.contains("\"max_playable_visual_roll_deg\": 0.0000"));
		assertTrue(json.contains("\"max_playable_visual_yaw_rate_dps\": 0.0000"));
		assertTrue(json.contains("\"final_playable_visual_yaw_drift_deg\": 0.0000"));
		assertTrue(json.contains("\"playable_neutral_sample_count\": 0"));
		assertTrue(json.contains("\"max_playable_neutral_visual_yaw_rate_dps\": 0.0000"));
	}

	@Test
	void playableTelemetryRequiresStableNeutralWindow() throws ReflectiveOperationException {
		DroneServerSelfTest selfTest = playableTelemetrySelfTest();

		assertTrue(playableTelemetryExercised(selfTest));

		setInt(selfTest, "playableNeutralSampleCount", 19);
		assertFalse(playableTelemetryExercised(selfTest));

		selfTest = playableTelemetrySelfTest();
		setDouble(selfTest, "maxPlayableNeutralVisualPitchDegrees", 1.51);
		assertFalse(playableTelemetryExercised(selfTest));

		selfTest = playableTelemetrySelfTest();
		setDouble(selfTest, "maxPlayableNeutralVisualRollDegrees", 1.51);
		assertFalse(playableTelemetryExercised(selfTest));

		selfTest = playableTelemetrySelfTest();
		setDouble(selfTest, "maxPlayableNeutralVisualYawRateDegreesPerSecond", 0.36);
		assertFalse(playableTelemetryExercised(selfTest));
	}

	@Test
	void playableAcroTelemetryAllowsHeldAttitudeButRequiresStableYaw() throws ReflectiveOperationException {
		DroneServerSelfTest selfTest = playableTelemetrySelfTest(FlightMode.ACRO);
		setDouble(selfTest, "maxPlayableVisualPitchDegrees", 11.0);
		setDouble(selfTest, "maxPlayableVisualRollDegrees", 14.0);
		setDouble(selfTest, "maxPlayableVisualYawRateDegreesPerSecond", 24.0);
		setDouble(selfTest, "maxPlayableNeutralVisualPitchDegrees", 17.5);
		setDouble(selfTest, "maxPlayableNeutralVisualRollDegrees", 3.0);
		setDouble(selfTest, "maxPlayableNeutralVisualYawRateDegreesPerSecond", 0.35);

		assertTrue(playableTelemetryExercised(selfTest));

		selfTest = playableTelemetrySelfTest(FlightMode.ACRO);
		setDouble(selfTest, "maxPlayableVisualPitchDegrees", 4.0);
		setDouble(selfTest, "maxPlayableVisualRollDegrees", 4.0);
		setDouble(selfTest, "maxPlayableVisualYawRateDegreesPerSecond", 24.0);
		assertFalse(playableTelemetryExercised(selfTest));

		selfTest = playableTelemetrySelfTest(FlightMode.ACRO);
		setDouble(selfTest, "maxPlayableVisualPitchDegrees", 11.0);
		setDouble(selfTest, "maxPlayableVisualRollDegrees", 14.0);
		setDouble(selfTest, "maxPlayableVisualYawRateDegreesPerSecond", 24.0);
		setDouble(selfTest, "maxPlayableNeutralVisualPitchDegrees", 18.1);
		assertFalse(playableTelemetryExercised(selfTest));

		selfTest = playableTelemetrySelfTest(FlightMode.ACRO);
		setDouble(selfTest, "maxPlayableVisualPitchDegrees", 11.0);
		setDouble(selfTest, "maxPlayableVisualRollDegrees", 14.0);
		setDouble(selfTest, "maxPlayableVisualYawRateDegreesPerSecond", 24.0);
		setDouble(selfTest, "maxPlayableNeutralVisualYawRateDegreesPerSecond", 0.36);
		assertFalse(playableTelemetryExercised(selfTest));
	}

	@Test
	void reportFlightModeUsesBlackboxInputAndControlMode() throws ReflectiveOperationException {
		String csv = String.join(
				"\n",
				"game_time,flight_model,flight_mode,control_flight_mode,armed,control_armed,motor_power",
				"1,playable,acro,acro,false,false,0.00000",
				"2,playable,angle,angle,true,true,0.16624",
				"3,playable,acro,acro,false,false,0.00000"
		);
		String controlOnlyCsv = String.join(
				"\n",
				"game_time,flight_model,flight_mode,control_flight_mode,armed,control_armed,motor_power",
				"1,playable,,horizon,true,true,0.12000"
		);

		assertEquals("angle", reportFlightModeFromCsv(csv));
		assertEquals("horizon", reportFlightModeFromCsv(controlOnlyCsv));
		assertEquals("unknown", reportFlightModeFromCsv("game_time,flight_model\n1,playable"));
	}

	@Test
	void parsesSelfTestFlightModelAliases() throws ReflectiveOperationException {
		assertEquals(FlightModelMode.PLAYABLE, parseFlightModelMode("playable"));
		assertEquals(FlightModelMode.PLAYABLE, parseFlightModelMode("bypass"));
		assertEquals(FlightModelMode.SIMULATION, parseFlightModelMode("sim"));
		assertEquals(FlightModelMode.SIMULATION, parseFlightModelMode("6dof"));
		assertEquals(FlightModelMode.SIMULATION, parseFlightModelMode("unknown"));
		assertEquals(FlightMode.HORIZON, parseControlFlightMode("horizon"));
		assertEquals(FlightMode.ACRO, parseControlFlightMode("acro"));
		assertEquals(FlightMode.ANGLE, parseControlFlightMode("stable"));
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, parseControlFlightMode("unknown"));
	}

	private static DroneServerSelfTest newSelfTest() throws ReflectiveOperationException {
		return newSelfTest(FlightModelMode.SIMULATION);
	}

	private static DroneServerSelfTest newSelfTest(FlightModelMode mode) throws ReflectiveOperationException {
		return newSelfTest(mode, FlightMode.DEFAULT_FIRST_FLIGHT);
	}

	private static DroneServerSelfTest newSelfTest(FlightModelMode mode, FlightMode controlMode) throws ReflectiveOperationException {
		Constructor<DroneServerSelfTest> constructor = DroneServerSelfTest.class.getDeclaredConstructor(int.class);
		constructor.setAccessible(true);
		if (mode == FlightModelMode.SIMULATION && controlMode == FlightMode.DEFAULT_FIRST_FLIGHT) {
			return constructor.newInstance(12);
		}
		if (controlMode == FlightMode.DEFAULT_FIRST_FLIGHT) {
			Constructor<DroneServerSelfTest> modeConstructor = DroneServerSelfTest.class.getDeclaredConstructor(int.class, FlightModelMode.class);
			modeConstructor.setAccessible(true);
			return modeConstructor.newInstance(12, mode);
		}
		Constructor<DroneServerSelfTest> modeConstructor = DroneServerSelfTest.class.getDeclaredConstructor(int.class, FlightModelMode.class, FlightMode.class);
		modeConstructor.setAccessible(true);
		return modeConstructor.newInstance(12, mode, controlMode);
	}

	private static DroneServerSelfTest playableTelemetrySelfTest() throws ReflectiveOperationException {
		return playableTelemetrySelfTest(FlightMode.DEFAULT_FIRST_FLIGHT);
	}

	private static DroneServerSelfTest playableTelemetrySelfTest(FlightMode controlMode) throws ReflectiveOperationException {
		DroneServerSelfTest selfTest = newSelfTest(FlightModelMode.PLAYABLE, controlMode);
		setDouble(selfTest, "maxHorizontalDistance", 0.06);
		setDouble(selfTest, "maxAverageMotorTelemetryRpm", 1200.0);
		setInt(selfTest, "playableNeutralSampleCount", 20);
		setDouble(selfTest, "maxPlayableNeutralVisualPitchDegrees", 1.5);
		setDouble(selfTest, "maxPlayableNeutralVisualRollDegrees", 1.5);
		setDouble(selfTest, "maxPlayableNeutralVisualYawRateDegreesPerSecond", 0.35);
		return selfTest;
	}

	private static String reportJson(DroneServerSelfTest selfTest, Path csvPath) throws ReflectiveOperationException {
		Method method = DroneServerSelfTest.class.getDeclaredMethod("reportJson", boolean.class, String.class, Path.class);
		method.setAccessible(true);
		return (String) method.invoke(selfTest, true, "passed", csvPath);
	}

	private static FlightModelMode parseFlightModelMode(String value) throws ReflectiveOperationException {
		Method method = DroneServerSelfTest.class.getDeclaredMethod("parseFlightModelMode", String.class);
		method.setAccessible(true);
		return (FlightModelMode) method.invoke(null, value);
	}

	private static FlightMode parseControlFlightMode(String value) throws ReflectiveOperationException {
		Method method = DroneServerSelfTest.class.getDeclaredMethod("parseControlFlightMode", String.class);
		method.setAccessible(true);
		return (FlightMode) method.invoke(null, value);
	}

	private static boolean playableTelemetryExercised(DroneServerSelfTest selfTest) throws ReflectiveOperationException {
		Method method = DroneServerSelfTest.class.getDeclaredMethod("playableTelemetryExercised");
		method.setAccessible(true);
		return (boolean) method.invoke(selfTest);
	}

	private static String reportFlightModeFromCsv(String csv) throws ReflectiveOperationException {
		Method method = DroneServerSelfTest.class.getDeclaredMethod("reportFlightModeFromCsv", String.class);
		method.setAccessible(true);
		return (String) method.invoke(null, csv);
	}

	private static void setDouble(DroneServerSelfTest selfTest, String fieldName, double value) throws ReflectiveOperationException {
		Field field = DroneServerSelfTest.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setDouble(selfTest, value);
	}

	private static void setInt(DroneServerSelfTest selfTest, String fieldName, int value) throws ReflectiveOperationException {
		Field field = DroneServerSelfTest.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setInt(selfTest, value);
	}
}
