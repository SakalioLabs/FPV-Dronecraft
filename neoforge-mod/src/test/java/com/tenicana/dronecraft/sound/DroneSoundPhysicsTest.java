package com.tenicana.dronecraft.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneSoundPhysicsTest {
	@Test
	void stoppedAndInvalidMotorsAreSilent() {
		assertEquals(0.0f, DroneSoundPhysics.motorVolume(0.0, 0.0, 4));
		assertEquals(0.0f, DroneSoundPhysics.propellerVolume(-1.0, 1.0, 1.0, 20.0, 1.0, 4));
		assertEquals(0.0f, DroneSoundPhysics.motorVolume(Double.NaN, Double.POSITIVE_INFINITY, 4));
		assertEquals(0.0f, DroneSoundPhysics.propellerVolume(
				Double.NaN,
				Double.POSITIVE_INFINITY,
				Double.NaN,
				Double.NEGATIVE_INFINITY,
				Double.NaN,
				4
		));
		assertFalse(DroneSoundPhysics.isAudible(Double.NaN));
	}

	@Test
	void rpmRaisesBothLayersWithoutExceedingSafeLimits() {
		float lowMotorVolume = DroneSoundPhysics.motorVolume(2_500.0, 0.25, 4);
		float highMotorVolume = DroneSoundPhysics.motorVolume(18_000.0, 0.90, 4);
		float lowMotorPitch = DroneSoundPhysics.motorPitch(2_500.0, 0.25);
		float highMotorPitch = DroneSoundPhysics.motorPitch(18_000.0, 0.90);
		float lowPropellerVolume = DroneSoundPhysics.propellerVolume(2_500.0, 0.25, 0.4, 2.0, 0.0, 4);
		float highPropellerVolume = DroneSoundPhysics.propellerVolume(18_000.0, 0.90, 1.2, 25.0, 0.7, 4);

		assertTrue(highMotorVolume > lowMotorVolume);
		assertTrue(highMotorPitch > lowMotorPitch);
		assertTrue(highPropellerVolume > lowPropellerVolume);
		assertTrue(highMotorVolume <= 0.45f);
		assertTrue(highPropellerVolume <= 0.62f);
		assertTrue(highMotorPitch <= 1.85f);
	}

	@Test
	void additionalRotorsIncreaseButClampTheCombinedSound() {
		float quad = DroneSoundPhysics.propellerVolume(12_000.0, 0.7, 1.0, 12.0, 0.2, 4);
		float octo = DroneSoundPhysics.propellerVolume(12_000.0, 0.7, 1.0, 12.0, 0.2, 8);
		float excessive = DroneSoundPhysics.propellerVolume(100_000.0, 4.0, 4.0, 400.0, 4.0, 100);

		assertTrue(octo > quad);
		assertEquals(0.62f, excessive);
	}

	@Test
	void impactThresholdAndMixStaySubtle() {
		assertFalse(DroneSoundPhysics.shouldPlayImpact(0.59));
		assertTrue(DroneSoundPhysics.shouldPlayImpact(0.60));
		assertFalse(DroneSoundPhysics.shouldPlayImpact(Double.NaN));
		assertTrue(DroneSoundPhysics.impactVolume(0.60) >= 0.10f);
		assertTrue(DroneSoundPhysics.impactVolume(20.0) <= 0.38f);
		assertTrue(DroneSoundPhysics.impactPitch(20.0, -4.0) >= 0.80f);
		assertTrue(DroneSoundPhysics.impactPitch(20.0, 4.0) <= 1.15f);
	}
}
