package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoeboxRoomDecayTest {
	private static final double SOUND_SPEED = 343.0;

	@Test
	void computesVolumeSurfaceAndCauchyMeanFreePath() {
		ShoeboxRoomDecay room = new ShoeboxRoomDecay(3.0, 1.8, 2.2);
		assertEquals(11.88, room.volumeCubicMeters(), 1.0e-12);
		assertEquals(31.92, room.surfaceAreaSquareMeters(), 1.0e-12);
		assertEquals(
				1.4887218045112784,
				room.diffuseMeanFreePathMeters(),
				1.0e-12
		);
	}

	@Test
	void eyringAbsorptionAndDecayAreExactInverses() {
		ShoeboxRoomDecay room = new ShoeboxRoomDecay(10.8, 10.9, 3.15);
		AcousticBands measured = new AcousticBands(
				0.857164910011111,
				0.9030624220156351,
				0.6915336132873643
		);
		AcousticBands absorption =
				room.effectiveEyringAbsorption(measured, SOUND_SPEED);
		AcousticBands reconstructed =
				room.eyringRt60Seconds(absorption, SOUND_SPEED);
		assertEquals(measured.low(), reconstructed.low(), 1.0e-12);
		assertEquals(measured.mid(), reconstructed.mid(), 1.0e-12);
		assertEquals(measured.high(), reconstructed.high(), 1.0e-12);
	}

	@Test
	void sabineAndEyringConvergeForWeakAbsorption() {
		ShoeboxRoomDecay room = new ShoeboxRoomDecay(11.0, 11.0, 3.0);
		double rt60 = room.eyringRt60Seconds(0.01, SOUND_SPEED);
		double sabine = room.effectiveSabineAbsorption(rt60, SOUND_SPEED);
		assertEquals(0.01, sabine, 6.0e-5);
		assertTrue(sabine > 0.01);
	}

	@Test
	void rejectsNonPhysicalInputs() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new ShoeboxRoomDecay(0.0, 1.0, 1.0)
		);
		ShoeboxRoomDecay room = new ShoeboxRoomDecay(1.0, 1.0, 1.0);
		assertThrows(
				IllegalArgumentException.class,
				() -> room.eyringRt60Seconds(-0.1, SOUND_SPEED)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> room.effectiveEyringAbsorption(0.0, SOUND_SPEED)
		);
	}
}
