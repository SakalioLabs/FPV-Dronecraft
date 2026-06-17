package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightModeInputRampTest {
	@Test
	void idleRampPassesFullInput() {
		FlightModeInputRamp ramp = new FlightModeInputRamp(8);

		assertEquals(1.0f, ramp.sampleAndAdvance(), 1.0e-6f);
		assertEquals(1.0f, ramp.sampleAndAdvance(), 1.0e-6f);
	}

	@Test
	void triggeredRampFadesAxesBackIn() {
		FlightModeInputRamp ramp = new FlightModeInputRamp(4);

		ramp.trigger();
		float first = ramp.sampleAndAdvance();
		float second = ramp.sampleAndAdvance();
		float third = ramp.sampleAndAdvance();
		float fourth = ramp.sampleAndAdvance();
		float done = ramp.sampleAndAdvance();

		assertTrue(first > 0.0f && first < 0.25f);
		assertTrue(second > first);
		assertTrue(third > second);
		assertEquals(1.0f, fourth, 1.0e-6f);
		assertEquals(1.0f, done, 1.0e-6f);
	}

	@Test
	void retriggerRestartsRamp() {
		FlightModeInputRamp ramp = new FlightModeInputRamp(4);

		ramp.trigger();
		float first = ramp.sampleAndAdvance();
		ramp.sampleAndAdvance();
		ramp.trigger();

		assertEquals(first, ramp.sampleAndAdvance(), 1.0e-6f);
	}

	@Test
	void resetReturnsToFullInputImmediately() {
		FlightModeInputRamp ramp = new FlightModeInputRamp(4);

		ramp.trigger();
		ramp.sampleAndAdvance();
		ramp.reset();

		assertEquals(1.0f, ramp.sampleAndAdvance(), 1.0e-6f);
	}
}
