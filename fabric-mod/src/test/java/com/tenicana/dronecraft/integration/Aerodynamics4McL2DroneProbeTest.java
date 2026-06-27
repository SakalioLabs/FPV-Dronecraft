package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2DroneProbeTest {
	@Test
	void droneProbeBuildsApDroneWindTunnelMaskFromConfig() {
		DroneConfig config = DroneConfig.apDrone();
		Vec3 inlet = new Vec3(0.0, 0.0, -14.0);

		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inlet, 48);

		assertTrue(probe.nx() >= 16 && probe.nx() <= 96);
		assertTrue(probe.ny() >= 12 && probe.ny() <= 96);
		assertTrue(probe.nz() >= 16 && probe.nz() <= 96);
		assertTrue(probe.cellSizeMeters() >= 0.02 && probe.cellSizeMeters() <= 0.08);
		assertTrue(probe.solidCellCount() > 0);
		assertTrue(probe.solidCellCount() < probe.nx() * probe.ny() * probe.nz() / 5);
		assertEquals(probe.nx() * probe.ny() * probe.nz(), probe.requestSpec().solidMask().length);
		assertFalse(probe.requestSpec().outputFlowAtlas());
		assertTrue(probe.requestSpec().computeForceMoment());
		assertEquals(48, probe.requestSpec().steps());
		assertEquals(inlet.x(), probe.requestSpec().inletVx(), 1.0e-9);
		assertEquals(inlet.y(), probe.requestSpec().inletVy(), 1.0e-9);
		assertEquals(inlet.z(), probe.requestSpec().inletVz(), 1.0e-9);
		assertEquals(0.5 * probe.nx() * probe.cellSizeMeters(), probe.requestSpec().referenceX(), 1.0e-9);
		assertEquals(0.5 * probe.ny() * probe.cellSizeMeters(), probe.requestSpec().referenceY(), 1.0e-9);
		assertEquals(0.5 * probe.nz() * probe.cellSizeMeters(), probe.requestSpec().referenceZ(), 1.0e-9);
		assertTrue(probe.solidAtBodyPosition(Vec3.ZERO), "body center should be solid");
		for (RotorSpec rotor : config.rotors()) {
			assertTrue(probe.solidAtBodyPosition(rotor.positionBodyMeters()),
					"rotor hub should be included in the airframe mask");
		}
		assertFalse(probe.solidAtBodyPosition(new Vec3(0.0, probe.cellSizeMeters() * probe.ny(), 0.0)),
				"far space above the tunnel should stay open");
	}

	@Test
	void droneProbeRunsThroughBoundedL2BridgeSummary() {
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(DroneConfig.apDrone(), new Vec3(0.0, 0.0, -12.0), 24);

		Aerodynamics4McL2Bridge.L2RunResult result =
				Aerodynamics4McL2Bridge.run(getClass().getClassLoader(), probe.requestSpec());

		assertTrue(result.invoked(), result.message());
		assertTrue(result.succeeded(), result.status());
		assertTrue(result.hasForceMoment());
		assertTrue(result.forceMoment().forceMagnitudeN() > 0.0);
		assertTrue(result.forceMoment().centerOfPressureOffsetMeters() > 0.0);
		assertFalse(result.hasFlowAtlas(), "drone force/moment probes should keep raw CFD atlas out of the summary path");
	}

	@Test
	void heavyLiftProbeStaysInsideBoundedL2Envelope() {
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -18.0), 80);

		assertTrue(probe.nx() <= 96);
		assertTrue(probe.ny() <= 96);
		assertTrue(probe.nz() <= 96);
		assertTrue(probe.rotorSpanMeters() > 0.7);
		assertTrue(probe.solidCellCount() > 0);
		assertTrue(Aerodynamics4McL2Bridge.buildRequest(getClass().getClassLoader(), probe.requestSpec()).built());
	}
}
