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

		String json = reportJson(selfTest, tempDir.resolve("server-selftest.csv"));

		assertTrue(json.contains("\"max_battery_effective_resistance_ohm\": 0.024000"));
		assertTrue(json.contains("\"max_battery_soc_resistance_scale\": 1.23000"));
		assertTrue(json.contains("\"max_battery_temp_resistance_scale\": 1.34000"));
		assertTrue(json.contains("\"max_battery_polarization_resistance_scale\": 1.45000"));
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
