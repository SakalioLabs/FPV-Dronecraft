package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DroneEntityFlightModelRoutingTest {
	@Test
	void entityRoutesPlayableAndSimulationStepsThroughFlightModelFacade() throws IOException {
		String source = Files.readString(droneEntitySource(), StandardCharsets.UTF_8);

		assertFalse(source.contains("physics.step("), "DroneEntity should call FlightModel.step instead of DronePhysics.step directly");
		assertFalse(source.contains("PlayableFlightModel."), "DroneEntity should route playable math through LegacyPlayableFlightModelAdapter");
		assertTrue(source.contains("FlightModel simulationFlightModel"), "DroneEntity should own simulation through the common FlightModel contract");
		assertTrue(source.contains("FlightModel playableFlightModel"), "DroneEntity should own playable through the common FlightModel contract");
		assertTrue(source.contains("FlightModelRouter flightModels"), "DroneEntity should route active models through the common facade");
		assertTrue(source.contains("flightModels.step(new FlightStepContext("), "model steps should cross the common FlightStepContext boundary");
	}

	private static Path droneEntitySource() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java");
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate DroneEntity.java");
		return Path.of(".");
	}
}
