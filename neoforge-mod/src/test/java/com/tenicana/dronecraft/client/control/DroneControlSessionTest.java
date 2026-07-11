package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DroneControlSessionTest {
	@Test
	void controlledDroneChangeTriggersControlReset() {
		DroneControlSession session = new DroneControlSession();
		UUID firstDrone = UUID.randomUUID();
		UUID secondDrone = UUID.randomUUID();

		assertFalse(session.updateControlledDrone(null));
		assertTrue(session.updateControlledDrone(firstDrone));
		assertFalse(session.updateControlledDrone(firstDrone));
		assertTrue(session.updateControlledDrone(secondDrone));
		assertFalse(session.updateControlledDrone(secondDrone));
		assertTrue(session.updateControlledDrone(null));
		assertFalse(session.updateControlledDrone(null));
	}

	@Test
	void clearForgetsLastControlledDrone() {
		DroneControlSession session = new DroneControlSession();
		UUID drone = UUID.randomUUID();

		assertTrue(session.updateControlledDrone(drone));
		session.clear();

		assertFalse(session.updateControlledDrone(null));
		assertTrue(session.updateControlledDrone(drone));
	}
}
