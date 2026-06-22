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
		String syncedFlightStateMethod = source.substring(
				source.indexOf("private void updateSyncedFlightState"),
				source.indexOf("private FlightMode syncedFlightMode")
		);
		String saveMethod = source.substring(
				source.indexOf("protected void addAdditionalSaveData"),
				source.indexOf("protected void readAdditionalSaveData")
		);
		String loadBatteryMethod = source.substring(
				source.indexOf("private void loadBatteryTransientState"),
				source.indexOf("private void saveRotorDynamicState")
		);
		String dimensionsMethod = source.substring(
				source.indexOf("public EntityDimensions getDimensions"),
				source.indexOf("public void setOwner")
		);
		String layoutMethod = source.substring(
				source.indexOf("private int syncedRotorCount"),
				source.indexOf("private static double valueOrZero")
		);
		String applyConfigMethod = source.substring(
				source.indexOf("public void applyConfig(DroneConfig config, String presetName)"),
				source.indexOf("private void replaceSimulationRuntime")
		);
		String tickMethod = source.substring(
				source.indexOf("public void tick()"),
				source.indexOf("private void applyDebugFlight")
		);
		String playableStepMethods = source.substring(
				source.indexOf("private void applyDebugFlight"),
				source.indexOf("private void applyDebugMovement")
		);
		String directControlMethods = source.substring(
				source.indexOf("private DroneInput directFailsafeInput"),
				source.indexOf("private static final float DEBUG_ARM_THRUST_THRESHOLD")
		);
		String damageSyncMethods = source.substring(
				source.indexOf("private boolean isAirworthy"),
				source.indexOf("private void recordBlackbox")
		);

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
		assertTrue(syncedFlightStateMethod.contains("simulationRuntime.syncedTelemetry(lastEnvironment)"), "synced entity telemetry should be projected by SimulationFlightRuntime");
		assertFalse(syncedFlightStateMethod.contains("simulationRuntime.state()"), "synced entity telemetry should not read DroneState directly");
		assertFalse(syncedFlightStateMethod.contains("simulationRuntime.config()"), "synced entity telemetry should not read DroneConfig directly");
		assertTrue(saveMethod.contains("simulationRuntime.persistenceStateSnapshot()"), "simulation persistence should be projected by SimulationFlightRuntime");
		assertFalse(saveMethod.contains("simulationRuntime.state()"), "simulation persistence should not read DroneState directly");
		assertTrue(loadBatteryMethod.contains("simulationRuntime.batteryTransientStateSnapshot()"), "battery transient defaults should be projected by SimulationFlightRuntime");
		assertFalse(loadBatteryMethod.contains("simulationRuntime.state()"), "battery transient defaults should not read DroneState directly");
		assertFalse(dimensionsMethod.contains("simulationRuntime.config()"), "airframe dimensions should be projected by SimulationFlightRuntime");
		assertFalse(layoutMethod.contains("simulationRuntime.config()"), "airframe layout should be projected by SimulationFlightRuntime");
		assertFalse(applyConfigMethod.contains("simulationRuntime.config()"), "config comparison should be projected by SimulationFlightRuntime");
		assertTrue(tickMethod.contains("simulationRuntime.controlInput(activeOwner, tickCount)"), "owner control input should be projected by SimulationFlightRuntime");
		assertFalse(tickMethod.contains("DroneControlManager.get(activeOwner"), "DroneEntity should not pass DroneState/DroneConfig into control input directly");
		assertTrue(playableStepMethods.contains("simulationRuntime.flightModelConfig()"), "flight model context config should cross the runtime boundary");
		assertTrue(playableStepMethods.contains("simulationRuntime.flightStateSnapshot("), "playable snapshots should be projected by SimulationFlightRuntime");
		assertTrue(playableStepMethods.contains("simulationRuntime.simulationStateSnapshot()"), "simulation snapshots should be projected by SimulationFlightRuntime");
		assertFalse(playableStepMethods.contains("simulationRuntime.state()"), "playable/simulation snapshots should not read DroneState directly");
		assertFalse(playableStepMethods.contains("simulationRuntime.config()"), "playable/simulation model stepping should not read DroneConfig directly");
		assertTrue(directControlMethods.contains("simulationRuntime.restoreDirectPerRotorTelemetry("), "direct per-rotor telemetry should be projected by SimulationFlightRuntime");
		assertTrue(directControlMethods.contains("simulationRuntime.clampedHoverThrottle("), "direct failsafe throttle should be projected by SimulationFlightRuntime");
		assertFalse(directControlMethods.contains("simulationRuntime.state()"), "direct control telemetry should not read DroneState directly");
		assertFalse(directControlMethods.contains("simulationRuntime.config()"), "direct control telemetry should not read DroneConfig directly");
		assertTrue(damageSyncMethods.contains("simulationRuntime.rotorHealthState()"), "rotor health telemetry should be projected by SimulationFlightRuntime");
		assertFalse(damageSyncMethods.contains("simulationRuntime.state()"), "damage sync should not read DroneState directly");
		assertFalse(damageSyncMethods.contains("simulationRuntime.config()"), "damage sync should not read DroneConfig directly");
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
