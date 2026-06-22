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
		assertFalse(source.contains("DronePhysics"), "DroneEntity should not construct or type the simulation internals directly");
		assertFalse(source.contains("new DronePhysics"), "simulation runtime construction should stay behind SimulationFlightRuntime");
		assertTrue(source.contains("SimulationFlightRuntime simulationRuntime"), "DroneEntity should hold simulation internals behind a runtime facade");
		assertTrue(source.contains("FlightModel simulationFlightModel"), "DroneEntity should own simulation through the common FlightModel contract");
		assertTrue(source.contains("FlightModel playableFlightModel"), "DroneEntity should own playable through the common FlightModel contract");
		assertTrue(source.contains("FlightModelRouter flightModels"), "DroneEntity should route active models through the common facade");
		assertTrue(source.contains("flightModels.step(new FlightStepContext("), "model steps should cross the common FlightStepContext boundary");
		assertTrue(source.contains("applySimulationResolvedState"), "simulation state corrections should be routed back through the facade");
		assertTrue(source.contains("StateCorrectionReason.COLLISION_CONTACT_SOLVE"), "collision movement should report an explicit state correction");
		assertTrue(source.contains("\"TAKEOFF_RELEASE\""), "takeoff assist should report an explicit state correction");
		assertTrue(source.contains("\"DIRECT_CLEAR\""), "direct playable reset should report an explicit state correction");
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
