package com.tenicana.dronecraft.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FpvCameraVibrationTest {
	@Test
	void bladePassPhaseUsesConfiguredBladeCount() {
		float motorPhase = 1.25f;
		float washPhase = 0.70f;
		float twoBlade = FpvCameraVibration.bladePassPhaseRadians(motorPhase, 2, washPhase);
		float threeBlade = FpvCameraVibration.bladePassPhaseRadians(motorPhase, 3, washPhase);

		assertEquals(motorPhase, threeBlade - twoBlade, 1.0e-6f);
	}

	@Test
	void bladePassPhaseSanitizesBladeCount() {
		float motorPhase = 0.80f;
		float washPhase = 0.25f;

		assertEquals(
				FpvCameraVibration.bladePassPhaseRadians(motorPhase, 2, washPhase),
				FpvCameraVibration.bladePassPhaseRadians(motorPhase, 0, washPhase),
				1.0e-6f
		);
		assertEquals(
				FpvCameraVibration.bladePassPhaseRadians(motorPhase, 8, washPhase),
				FpvCameraVibration.bladePassPhaseRadians(motorPhase, 99, washPhase),
				1.0e-6f
		);
	}
}
