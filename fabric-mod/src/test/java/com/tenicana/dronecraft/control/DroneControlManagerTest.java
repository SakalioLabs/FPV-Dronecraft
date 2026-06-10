package com.tenicana.dronecraft.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Vec3;

class DroneControlManagerTest {
	@Test
	void diagnosticScriptOverridesManualInputAndExpires() {
		UUID playerId = UUID.randomUUID();
		DroneControlManager.update(playerId, new DroneInput(0.2, 0.1, -0.1, 0.2, true, true, FlightMode.ACRO), 95);

		DroneConfig config = DroneConfig.racingQuad();
		DroneState state = new DroneState(config.rotors().size());
		state.setPositionMeters(new Vec3(0.0, 80.0, 0.0));
		state.setVelocityMetersPerSecond(Vec3.ZERO);

		int durationTicks = DroneControlManager.startDiagnostic(playerId, 100, 320);
		assertEquals(320, durationTicks);
		assertEquals("spool", DroneControlManager.diagnosticStatus(playerId, 100).phase());

		DroneInput spool = DroneControlManager.get(playerId, 100, state, config);
		assertEquals(FlightMode.HORIZON, spool.flightMode());
		assertTrue(spool.armed());
		assertTrue(spool.throttle() > 0.05 && spool.throttle() < config.hoverThrottle());

		DroneInput rollStep = DroneControlManager.get(playerId, 195, state, config);
		assertEquals(FlightMode.HORIZON, rollStep.flightMode());
		assertTrue(Math.abs(rollStep.roll()) > 0.20);
		assertEquals("roll_step", DroneControlManager.diagnosticStatus(playerId, 195).phase());

		DroneInput punch = DroneControlManager.get(playerId, 330, state, config);
		assertEquals("throttle_punch", DroneControlManager.diagnosticStatus(playerId, 330).phase());
		assertTrue(punch.throttle() > config.hoverThrottle() + 0.10);

		DroneInput afterExpiry = DroneControlManager.get(playerId, 421, state, config);
		assertFalse(DroneControlManager.diagnosticStatus(playerId, 421).active());
		assertFalse(afterExpiry.armed());
		assertFalse(afterExpiry.linkActive());
	}

	@Test
	void diagnosticDurationIsClampedToSupportedRange() {
		UUID shortRun = UUID.randomUUID();
		UUID longRun = UUID.randomUUID();

		assertEquals(120, DroneControlManager.startDiagnostic(shortRun, 0, 20));
		assertEquals(1200, DroneControlManager.startDiagnostic(longRun, 0, 5000));

		DroneControlManager.stopDiagnostic(shortRun);
		DroneControlManager.stopDiagnostic(longRun);
	}

	@Test
	void completedDiagnosticCarriesAutoSaveFlag() {
		UUID playerId = UUID.randomUUID();
		DroneConfig config = DroneConfig.racingQuad();
		DroneState state = new DroneState(config.rotors().size());

		DroneControlManager.startDiagnostic(playerId, 10, 120, true);
		assertTrue(DroneControlManager.get(playerId, 129, state, config).linkActive());
		DroneControlManager.get(playerId, 130, state, config);

		DroneControlManager.CompletedDiagnostic completed = DroneControlManager.consumeCompletedDiagnostic(playerId);
		assertEquals(120, completed.elapsedTicks());
		assertEquals(120, completed.durationTicks());
		assertTrue(completed.autoSaveBlackbox());
		assertEquals(null, DroneControlManager.consumeCompletedDiagnostic(playerId));
	}
}
