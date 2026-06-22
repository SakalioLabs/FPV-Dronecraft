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
		assertFalse(source.contains("simulationRuntime.state().setPositionMeters"), "canonical position writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().setVelocityMetersPerSecond"), "canonical velocity writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().setAngularVelocityBodyRadiansPerSecond"), "canonical angular-rate writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().setContactTelemetry"), "contact state writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().damageRotor"), "rotor damage writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().damageAllRotors"), "rotor damage writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().repairAllRotors"), "rotor repair writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().setBattery"), "battery persistence writes should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().motorRpmTelemetry"), "motor RPM telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().averageMotorRpmTelemetry"), "average motor RPM telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("SimulationFlightRuntime.betaflight"), "Betaflight telemetry projection should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("private double averageMotorPolePairs"), "motor pole-pair telemetry projection should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("private double motorPolePairs"), "per-motor pole-pair telemetry projection should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().escCommandFrame"), "ESC frame telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().escCommandError"), "ESC error telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().maxRotorDamageVibration"), "rotor damage telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().maxRotorDynamicInflowTimeConstantSeconds"), "rotor inflow telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().maxAbsRotorCoaxialLoadBiasTarget"), "coaxial bias telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().maxRotorCoaxialLoadBiasClipping"), "coaxial clipping telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().maxRotorCoaxialAllocation"), "coaxial allocation telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().maxRotorIcing"), "rotor icing telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().minRotorIcing"), "rotor icing telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().airframeBodyDragForceBodyNewtons"), "airframe drag telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().linearDampingDragForceWorldNewtons"), "linear drag telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().airframeDrag"), "airframe drag telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().batteryStateOfChargeResistanceScale"), "battery resistance telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().batteryTemperatureResistanceScale"), "battery resistance telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().batteryPolarizationResistanceScale"), "battery resistance telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("valueOrOne(simulationRuntime.state().rotorHealth()"), "rotor health fallback reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().controlFrame"), "control frame telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().gyroDynamicNotchSpread"), "gyro notch telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().gyroRpmHarmonicNotchAttenuation"), "gyro RPM notch telemetry reads should stay behind SimulationFlightRuntime");
		assertFalse(source.contains("simulationRuntime.state().gyroBladePass"), "gyro blade-pass telemetry reads should stay behind SimulationFlightRuntime");
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
