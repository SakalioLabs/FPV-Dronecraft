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
	void unsafeServerSideArmTransitionIsRejectedUntilControlsAreSafe() {
		UUID playerId = UUID.randomUUID();

		DroneControlManager.update(playerId, new DroneInput(0.65, 0.30, 0.0, 0.0, true, true, FlightMode.ANGLE), 10);
		DroneInput blocked = DroneControlManager.get(playerId, 10);

		assertFalse(blocked.armed());
		assertTrue(blocked.linkActive());
		assertEquals(0.65, blocked.throttle(), 1.0e-12);

		DroneControlManager.update(playerId, new DroneInput(0.02, 0.0, 0.0, 0.0, true, true, FlightMode.ANGLE), 11);
		DroneInput armed = DroneControlManager.get(playerId, 11);

		assertTrue(armed.armed());
		assertEquals(0.02, armed.throttle(), 1.0e-12);
	}

	@Test
	void serverAcceptsModeTwoStickArmGesture() {
		UUID playerId = UUID.randomUUID();

		DroneControlManager.update(playerId, new DroneInput(0.03, -0.80, 0.80, -0.80, true, true, FlightMode.ANGLE), 20);
		DroneInput armed = DroneControlManager.get(playerId, 20);

		assertTrue(armed.armed());
		assertEquals(-0.80, armed.pitch(), 1.0e-12);
		assertEquals(0.80, armed.roll(), 1.0e-12);
		assertEquals(-0.80, armed.yaw(), 1.0e-12);
	}

	@Test
	void serverAllowsThrottleAfterSafeArmTransition() {
		UUID playerId = UUID.randomUUID();

		DroneControlManager.update(playerId, new DroneInput(0.02, 0.0, 0.0, 0.0, true, true, FlightMode.ANGLE), 30);
		DroneControlManager.update(playerId, new DroneInput(0.70, 0.12, -0.10, 0.08, true, true, FlightMode.ANGLE), 31);
		DroneInput flying = DroneControlManager.get(playerId, 31);

		assertTrue(flying.armed());
		assertEquals(0.70, flying.throttle(), 1.0e-12);
		assertEquals(0.12, flying.pitch(), 1.0e-12);
		assertEquals(-0.10, flying.roll(), 1.0e-12);
		assertEquals(0.08, flying.yaw(), 1.0e-12);
	}

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
		assertEquals(FlightMode.ANGLE, spool.flightMode());
		assertTrue(spool.armed());
		assertTrue(spool.throttle() > 0.05 && spool.throttle() < config.hoverThrottle());

		DroneInput rollStep = DroneControlManager.get(playerId, 195, state, config);
		assertEquals(FlightMode.ANGLE, rollStep.flightMode());
		assertTrue(Math.abs(rollStep.roll()) > 0.38);
		assertEquals("roll_step", DroneControlManager.diagnosticStatus(playerId, 195).phase());

		DroneInput yawStep = DroneControlManager.get(playerId, 292, state, config);
		assertEquals(FlightMode.ANGLE, yawStep.flightMode());
		assertTrue(Math.abs(yawStep.yaw()) > 0.50);
		assertEquals("yaw_step", DroneControlManager.diagnosticStatus(playerId, 292).phase());

		DroneInput punch = DroneControlManager.get(playerId, 330, state, config);
		assertEquals("throttle_punch", DroneControlManager.diagnosticStatus(playerId, 330).phase());
		assertTrue(punch.throttle() > config.hoverThrottle() + 0.10);

		state.setPositionMeters(new Vec3(0.0, 80.35, 0.0));
		state.setVelocityMetersPerSecond(Vec3.ZERO);
		DroneInput softLanding = DroneControlManager.get(playerId, 400, state, config);
		assertEquals("settle", DroneControlManager.diagnosticStatus(playerId, 400).phase());
		assertTrue(softLanding.armed());
		assertTrue(softLanding.throttle() > 0.05 && softLanding.throttle() < config.hoverThrottle());
		assertEquals(0.0, softLanding.pitch(), 1.0e-12);
		assertEquals(0.0, softLanding.roll(), 1.0e-12);
		assertEquals(0.0, softLanding.yaw(), 1.0e-12);

		DroneInput disarm = DroneControlManager.get(playerId, 416, state, config);
		assertEquals("disarm", DroneControlManager.diagnosticStatus(playerId, 416).phase());
		assertFalse(disarm.armed());
		assertTrue(disarm.linkActive());

		DroneInput afterExpiry = DroneControlManager.get(playerId, 421, state, config);
		assertFalse(DroneControlManager.diagnosticStatus(playerId, 421).active());
		assertFalse(afterExpiry.armed());
		assertFalse(afterExpiry.linkActive());
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, afterExpiry.flightMode());
	}

	@Test
	void expiredManualInputReturnsFirstFlightIdleMode() {
		UUID playerId = UUID.randomUUID();

		DroneControlManager.update(playerId, new DroneInput(0.20, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO), 10);
		DroneInput active = DroneControlManager.get(playerId, 10);
		DroneInput expired = DroneControlManager.get(playerId, 19);

		assertEquals(FlightMode.ACRO, active.flightMode());
		assertFalse(expired.armed());
		assertFalse(expired.linkActive());
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, expired.flightMode());
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
	void diagnosticScriptCanRunInRequestedFlightMode() {
		UUID playerId = UUID.randomUUID();
		DroneConfig config = DroneConfig.racingQuad();
		DroneState state = new DroneState(config.rotors().size());

		DroneControlManager.startDiagnostic(playerId, 10, 240, false, FlightMode.HORIZON);

		DroneInput spool = DroneControlManager.get(playerId, 10, state, config);
		DroneInput rollStep = DroneControlManager.get(playerId, 85, state, config);

		assertEquals(FlightMode.HORIZON, spool.flightMode());
		assertEquals(FlightMode.HORIZON, rollStep.flightMode());
		assertTrue(rollStep.armed());
		assertTrue(Math.abs(rollStep.roll()) > 0.24);
		DroneControlManager.stopDiagnostic(playerId);
	}

	@Test
	void diagnosticScriptKeepsAcroExerciseCommandsManual() {
		UUID playerId = UUID.randomUUID();
		DroneConfig config = DroneConfig.racingQuad();
		DroneState state = new DroneState(config.rotors().size());

		DroneControlManager.startDiagnostic(playerId, 10, 240, false, FlightMode.ACRO);

		DroneInput rollStep = DroneControlManager.get(playerId, 85, state, config);

		assertEquals(FlightMode.ACRO, rollStep.flightMode());
		assertEquals(0.24, Math.abs(rollStep.roll()), 1.0e-12);
		DroneControlManager.stopDiagnostic(playerId);
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
