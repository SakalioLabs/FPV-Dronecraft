package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FlightModeTest {
	@Test
	void cyclesFromDefaultHorizonTowardSaferModesBeforeAcro() {
		assertEquals(FlightMode.ANGLE, FlightMode.HORIZON.next());
		assertEquals(FlightMode.ACRO, FlightMode.ANGLE.next());
		assertEquals(FlightMode.HORIZON, FlightMode.ACRO.next());
	}
}
