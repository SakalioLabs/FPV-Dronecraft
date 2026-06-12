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
	void reportJsonIncludesBatteryResistanceScaleSplit(@TempDir Path tempDir) throws ReflectiveOperationException {
		DroneServerSelfTest selfTest = newSelfTest();
		setDouble(selfTest, "maxBatteryEffectiveResistance", 0.024);
		setDouble(selfTest, "maxBatteryStateOfChargeResistanceScale", 1.23);
		setDouble(selfTest, "maxBatteryTemperatureResistanceScale", 1.34);
		setDouble(selfTest, "maxBatteryPolarizationResistanceScale", 1.45);

		String json = reportJson(selfTest, tempDir.resolve("server-selftest.csv"));

		assertTrue(json.contains("\"max_battery_effective_resistance_ohm\": 0.024000"));
		assertTrue(json.contains("\"max_battery_soc_resistance_scale\": 1.23000"));
		assertTrue(json.contains("\"max_battery_temp_resistance_scale\": 1.34000"));
		assertTrue(json.contains("\"max_battery_polarization_resistance_scale\": 1.45000"));
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
