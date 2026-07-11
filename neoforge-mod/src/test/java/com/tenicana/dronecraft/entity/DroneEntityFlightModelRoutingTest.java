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
	void neoforgeDeferredRegistriesAreConsumedThroughValueAccessors() throws IOException {
		String source = Files.readString(droneEntitySource(), StandardCharsets.UTF_8);

		assertTrue(source.contains("DroneItems.droneController()"), "NeoForge deferred items must be resolved through their accessor");
		assertTrue(source.contains("DroneSoundEvents.impact()"), "NeoForge deferred sounds must be resolved through their accessor");
		assertFalse(source.contains("DroneItems.DRONE_CONTROLLER"), "Fabric's eager item field must not leak into NeoForge code");
		assertFalse(source.contains("DroneSoundEvents.IMPACT"), "Fabric's eager sound field must not leak into NeoForge code");
	}

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
		String simulationStepMethod = source.substring(
				source.indexOf("private void stepSimulationFlightModel"),
				source.indexOf("private void applyDebugMovement")
		);
		String directControlMethods = source.substring(
				source.indexOf("private DroneInput directFailsafeInput"),
				source.indexOf("private static final float DEBUG_ARM_THRUST_THRESHOLD")
		);
		String groundTakeoffMethods = source.substring(
				source.indexOf("private double groundClearanceMetersAt"),
				source.indexOf("private boolean advancedContactEffectsActive")
		);
		String droneWakeMethods = source.substring(
				source.indexOf("private DroneWakeAirflow sampleDroneWakeAirflow"),
				source.indexOf("private ObstacleAirflow sampleObstacleAirflow")
		);
		String rotorEnvironmentMethods = source.substring(
				source.indexOf("private PrecipitationWetness samplePrecipitationWetness"),
				source.indexOf("private DroneWakeAirflow sampleDroneWakeAirflow")
		);
		String movementMethods = source.substring(
				source.indexOf("private void applyPhysicsMovement"),
				source.indexOf("private void updateSyncedFlightState")
		);
		String propStrikeMethods = source.substring(
				source.indexOf("private void applyCollisionDamage"),
				source.indexOf("private boolean isAirworthy")
		);
		String damageSyncMethods = source.substring(
				source.indexOf("private boolean isAirworthy"),
				source.indexOf("private void recordBlackbox")
		);
		String blackboxMethod = source.substring(
				source.indexOf("private void recordBlackbox"),
				source.indexOf("private double maxPropStrikeSeverityThisTick")
		);
		String configAccessorMethod = source.substring(
				source.indexOf("public DroneConfig config()"),
				source.indexOf("public DroneEnvironmentOverride environmentOverride()")
		);
		String saveConfigMethod = source.substring(
				source.indexOf("private void saveConfig"),
				source.indexOf("private void loadConfig")
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
		assertTrue(groundTakeoffMethods.contains("simulationRuntime.groundEffectRayLength("), "ground clearance ray lengths should be projected by SimulationFlightRuntime");
		assertTrue(groundTakeoffMethods.contains("simulationRuntime.ceilingEffectHeightMeters()"), "ceiling effect height should be projected by SimulationFlightRuntime");
		assertTrue(groundTakeoffMethods.contains("simulationRuntime.verticalVelocityAtOrBelow("), "ground sleep velocity checks should be projected by SimulationFlightRuntime");
		assertTrue(groundTakeoffMethods.contains("simulationRuntime.takeoffThrottleRelease("), "takeoff throttle threshold should be projected by SimulationFlightRuntime");
		assertTrue(groundTakeoffMethods.contains("simulationRuntime.verticalRotorThrustNewtons()"), "vertical rotor thrust should be projected by SimulationFlightRuntime");
		assertTrue(groundTakeoffMethods.contains("simulationRuntime.releaseGroundTakeoff("), "takeoff release kinematics should be projected by SimulationFlightRuntime");
		assertFalse(groundTakeoffMethods.contains("simulationRuntime.state()"), "ground/takeoff helpers should not read DroneState directly");
		assertFalse(groundTakeoffMethods.contains("simulationRuntime.config()"), "ground/takeoff helpers should not read DroneConfig directly");
		assertTrue(droneWakeMethods.contains("source.simulationRuntime.droneWakeSource()"), "drone wake source telemetry should be projected by SimulationFlightRuntime");
		assertFalse(droneWakeMethods.contains("simulationRuntime.state()"), "drone wake sampling should not read DroneState directly");
		assertFalse(droneWakeMethods.contains("simulationRuntime.config()"), "drone wake sampling should not read DroneConfig directly");
		assertTrue(rotorEnvironmentMethods.contains("simulationRuntime.rotorGeometry()"), "rotor geometry should be projected by SimulationFlightRuntime");
		assertTrue(rotorEnvironmentMethods.contains("simulationRuntime.rotorPlaneWorldDirection("), "rotor sample directions should be projected by SimulationFlightRuntime");
		assertTrue(rotorEnvironmentMethods.contains("simulationRuntime.weightedGroundEffectThrustMultiplier("), "ground effect weighting should be projected by SimulationFlightRuntime");
		assertTrue(rotorEnvironmentMethods.contains("simulationRuntime.weightedCeilingEffectThrustMultiplier("), "ceiling effect weighting should be projected by SimulationFlightRuntime");
		assertTrue(rotorEnvironmentMethods.contains("obstruction.wallForceGeometryFactor()"), "wall force should reuse the sampled rotor obstruction geometry");
		assertTrue(rotorEnvironmentMethods.contains("flowObstructionWallForceFactors[i] = flowObstruction.wallForceFactor()"), "wall force geometry should be carried per rotor");
		assertFalse(rotorEnvironmentMethods.contains("simulationRuntime.state()"), "rotor environment sampling should not read DroneState directly");
		assertFalse(rotorEnvironmentMethods.contains("simulationRuntime.config()"), "rotor environment sampling should not read DroneConfig directly");
		assertTrue(movementMethods.contains("simulationRuntime.movementState()"), "movement state should be projected by SimulationFlightRuntime");
		assertTrue(movementMethods.contains("simulationRuntime.contactAngularVelocityImpulseBody("), "contact angular impulse should be projected by SimulationFlightRuntime");
		assertFalse(movementMethods.contains("simulationRuntime.state()"), "physics movement should not read DroneState directly");
		assertFalse(movementMethods.contains("simulationRuntime.config()"), "physics movement should not read DroneConfig directly");
		assertTrue(propStrikeMethods.contains("simulationRuntime.exposedRotorIndex("), "collision damage rotor selection should be projected by SimulationFlightRuntime");
		assertTrue(propStrikeMethods.contains("simulationRuntime.propStrikeState()"), "prop strike state should be projected by SimulationFlightRuntime");
		assertTrue(propStrikeMethods.contains("simulationRuntime.rotorCount()"), "prop strike array sizing should use the runtime boundary");
		assertFalse(propStrikeMethods.contains("simulationRuntime.state()"), "prop strike helpers should not read DroneState directly");
		assertFalse(propStrikeMethods.contains("simulationRuntime.config()"), "prop strike helpers should not read DroneConfig directly");
		assertTrue(damageSyncMethods.contains("simulationRuntime.rotorHealthState()"), "rotor health telemetry should be projected by SimulationFlightRuntime");
		assertFalse(damageSyncMethods.contains("simulationRuntime.state()"), "damage sync should not read DroneState directly");
		assertFalse(damageSyncMethods.contains("simulationRuntime.config()"), "damage sync should not read DroneConfig directly");
		assertTrue(blackboxMethod.contains("simulationRuntime.blackboxSample("), "blackbox sample construction should be projected by SimulationFlightRuntime");
		assertTrue(blackboxMethod.contains("if (blackbox.recordingEnabled())"), "expensive blackbox samples should only be built while capture is explicitly enabled");
		assertTrue(
				blackboxMethod.indexOf("if (blackbox.recordingEnabled())") < blackboxMethod.indexOf("simulationRuntime.blackboxSample("),
				"the blackbox recording guard must run before the sample is constructed"
		);
		assertFalse(blackboxMethod.contains("simulationRuntime.state()"), "blackbox recording should not read DroneState directly");
		assertFalse(blackboxMethod.contains("simulationRuntime.config()"), "blackbox recording should not read DroneConfig directly");
		assertTrue(configAccessorMethod.contains("simulationRuntime.currentConfig()"), "public config access should cross the runtime boundary explicitly");
		assertFalse(configAccessorMethod.contains("simulationRuntime.config()"), "public config access should not use the raw runtime config method");
		assertTrue(saveConfigMethod.contains("simulationRuntime.currentConfig()"), "config saving should cross the runtime boundary explicitly");
		assertFalse(saveConfigMethod.contains("simulationRuntime.config()"), "config saving should not use the raw runtime config method");
		assertTrue(source.contains("SimulationFlightRuntime simulationRuntime"), "DroneEntity should hold simulation internals behind a runtime facade");
		assertTrue(source.contains("FlightModel simulationFlightModel"), "DroneEntity should own simulation through the common FlightModel contract");
		assertTrue(source.contains("FlightModel playableFlightModel"), "DroneEntity should own playable through the common FlightModel contract");
		assertTrue(source.contains("FlightModelRouter flightModels"), "DroneEntity should route active models through the common facade");
		assertTrue(playableStepMethods.contains("flightModels.step(new FlightStepContext("), "rich playable steps should cross the common FlightStepContext boundary");
		assertTrue(simulationStepMethod.contains("flightModels.stepStateOnly("), "high-rate simulation substeps should use the common state-only model boundary");
		assertFalse(simulationStepMethod.contains("new FlightStepContext("), "state-only simulation substeps should not allocate a rich step context");
		assertFalse(simulationStepMethod.contains("flightModels.snapshot()"), "state-only simulation substeps should not materialize a previous snapshot");
		assertTrue(
				tickMethod.indexOf("DroneDebugSettings.controlLoggingEnabled()") < tickMethod.indexOf("level().getEntitiesOfClass"),
				"disabled ownerless logging must not perform a spatial entity query"
		);
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
			Path child = current.resolve("neoforge-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate DroneEntity.java");
		return Path.of(".");
	}
}
