package com.tenicana.dronecraft.diagnostic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DroneServerSelfTestTest {
	@Test
	void reportJsonIncludesBatteryResistanceScaleAndAerodynamicTelemetry(@TempDir Path tempDir) throws ReflectiveOperationException {
		DroneServerSelfTest selfTest = newSelfTest();
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
		setDouble(selfTest, "minRotorWetThrustScale", 0.94);
		setDouble(selfTest, "maxRotorCoaxialLoadBias", 0.083);
		setDouble(selfTest, "maxRotorCoaxialLoadBiasTarget", 0.115);
		setDouble(selfTest, "maxRotorCoaxialLoadBiasClipping", 0.012);
		setDouble(selfTest, "maxRotorCoaxialAllocationLoadFraction", 0.60);
		setDouble(selfTest, "maxRotorCoaxialAllocationCommandRatio", 1.33);
		setDouble(selfTest, "maxRotorCoaxialAllocationMechanicalGainPercent", 4.41);
		setDouble(selfTest, "maxRotorCoaxialAllocationElectricalGainPercent", 2.84);
		setDouble(selfTest, "maxAirframeBodyDragForce", 0.65);
		setDouble(selfTest, "maxLinearDampingDragForce", 1.85);
		setDouble(selfTest, "maxAirframeDragAlongFlow", 2.42);
		setDouble(selfTest, "maxAirframeDragEquivalentLinearCoefficient", 0.242);
		setDouble(selfTest, "maxAirframeDragEquivalentCdA", 0.0395);
		setDouble(selfTest, "maxAirframeDragImavReferenceRatio", 1.21);

		String json = reportJson(selfTest, tempDir.resolve("server-selftest.csv"));

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
		assertTrue(json.contains("\"max_rotor_wet_thrust_loss_percent\": 6.000"));
		assertTrue(json.contains("\"max_rotor_coaxial_load_bias\": 0.08300"));
		assertTrue(json.contains("\"max_rotor_coaxial_load_bias_target\": 0.11500"));
		assertTrue(json.contains("\"max_rotor_coaxial_load_bias_clipping\": 0.01200"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_load\": 0.60000"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_ratio\": 1.33000"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_mech_gain_pct\": 4.41000"));
		assertTrue(json.contains("\"max_rotor_coaxial_allocation_elec_gain_pct\": 2.84000"));
		assertTrue(json.contains("\"max_airframe_body_drag_n\": 0.65000"));
		assertTrue(json.contains("\"max_linear_damping_drag_n\": 1.85000"));
		assertTrue(json.contains("\"max_airframe_drag_along_flow_n\": 2.42000"));
		assertTrue(json.contains("\"max_airframe_drag_equivalent_linear_k\": 0.24200"));
		assertTrue(json.contains("\"max_airframe_drag_equivalent_cda_m2\": 0.03950"));
		assertTrue(json.contains("\"max_airframe_drag_imav_ratio\": 1.21000"));
	}

	private static DroneServerSelfTest newSelfTest() throws ReflectiveOperationException {
		Constructor<DroneServerSelfTest> constructor = DroneServerSelfTest.class.getDeclaredConstructor(int.class);
		constructor.setAccessible(true);
		return constructor.newInstance(12);
	}

	private static String reportJson(DroneServerSelfTest selfTest, Path csvPath) throws ReflectiveOperationException {
		Method method = DroneServerSelfTest.class.getDeclaredMethod("reportJson", boolean.class, String.class, Path.class);
		method.setAccessible(true);
		return (String) method.invoke(selfTest, true, "passed", csvPath);
	}

	private static void setDouble(DroneServerSelfTest selfTest, String fieldName, double value) throws ReflectiveOperationException {
		Field field = DroneServerSelfTest.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setDouble(selfTest, value);
	}
}
