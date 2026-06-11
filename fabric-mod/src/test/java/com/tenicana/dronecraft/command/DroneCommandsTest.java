package com.tenicana.dronecraft.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;

class DroneCommandsTest {
	@Test
	void tuneStatusShowsRotorBladeCount() throws Exception {
		Method method = DroneCommands.class.getDeclaredMethod("formatTuneStatus", DroneConfig.class);
		method.setAccessible(true);

		String status = (String) method.invoke(null, DroneConfig.racingQuad().withRotorBladeCount(4));

		assertTrue(status.contains("blades 4"));
	}
}
