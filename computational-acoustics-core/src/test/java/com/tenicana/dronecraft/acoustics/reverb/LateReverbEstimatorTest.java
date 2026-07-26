package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LateReverbEstimatorTest {
	private static final AcousticVector LISTENER =
			new AcousticVector(0.5, 0.5, 0.5);
	private static final VoxelReflectionProbe.Config CONFIG =
			new VoxelReflectionProbe.Config(256, 16, 64.0, 256, 1.0e-10);

	@Test
	void stoneRoomDecaysLongerThanSoftRoomInEveryBand() {
		LateReverbEstimator.Parameters stone = estimate(
				VoxelReflectionProbeTest.closedRoom(
						AcousticMaterials.STONE,
						21L
				)
		);
		LateReverbEstimator.Parameters soft = estimate(
				VoxelReflectionProbeTest.closedRoom(
						AcousticMaterials.SOFT,
						22L
				)
		);

		assertTrue(stone.rt60Seconds().low() > soft.rt60Seconds().low());
		assertTrue(stone.rt60Seconds().mid() > soft.rt60Seconds().mid());
		assertTrue(stone.rt60Seconds().high() > soft.rt60Seconds().high());
		assertTrue(stone.edtSeconds().low() > soft.edtSeconds().low());
		assertTrue(
				stone.firstReflectionEnergy().low()
						> soft.firstReflectionEnergy().low()
		);
		assertTrue(
				stone.directToReverberantDb().low()
						< soft.directToReverberantDb().low()
		);
		assertEquals(
				AcousticMaterials.STONE.scattering(),
				stone.diffusion(),
				1.0e-12
		);
		assertEquals(
				AcousticMaterials.SOFT.scattering(),
				soft.diffusion(),
				1.0e-12
		);
	}

	@Test
	void highFrequencyDecaysFasterForCanonicalStone() {
		LateReverbEstimator.Parameters stone = estimate(
				VoxelReflectionProbeTest.closedRoom(
						AcousticMaterials.STONE,
						23L
				)
		);

		assertTrue(stone.rt60Seconds().low() > stone.rt60Seconds().mid());
		assertTrue(stone.rt60Seconds().mid() > stone.rt60Seconds().high());
		assertTrue(stone.edtSeconds().low() > stone.edtSeconds().high());
		assertEquals(0.0, stone.openness());
		assertTrue(stone.meanFreePathMeters() > 0.0);
	}

	private static LateReverbEstimator.Parameters estimate(
			ReflectionVolume volume
	) {
		return LateReverbEstimator.estimate(
				VoxelReflectionProbe.analyze(LISTENER, volume, CONFIG),
				343.0
		);
	}
}
