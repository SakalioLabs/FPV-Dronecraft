package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FlightModeTest {
	@Test
	void cyclesFromFirstFlightAngleTowardAdvancedModes() {
		assertEquals(FlightMode.HORIZON, FlightMode.ANGLE.next());
		assertEquals(FlightMode.ACRO, FlightMode.HORIZON.next());
		assertEquals(FlightMode.ANGLE, FlightMode.ACRO.next());
	}

	@Test
	void invalidNetworkIdsFallBackToFirstFlightAngle() {
		assertEquals(FlightMode.ANGLE, FlightMode.byId(-1));
		assertEquals(FlightMode.ANGLE, FlightMode.byId(FlightMode.values().length));
		assertEquals(FlightMode.ACRO, FlightMode.byId(FlightMode.ACRO.id()));
		assertEquals(FlightMode.HORIZON, FlightMode.byId(FlightMode.HORIZON.id()));
	}
}
