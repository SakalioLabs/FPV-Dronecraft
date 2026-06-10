package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;

class RotorLayoutCodecTest {
	@Test
	void racingQuadLayoutPreservesLegacyRotorScale() {
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode(RotorLayoutCodec.encode(DroneConfig.racingQuad()));

		assertEquals(4, layout.rotorCount());
		assertEquals(1, layout.spinDirection(0));
		assertEquals(-1, layout.spinDirection(1));
		assertEquals(14.0f, layout.xModelUnits(0), 0.2f);
		assertEquals(14.0f, layout.zModelUnits(0), 0.2f);
		assertEquals(-14.0f, layout.xModelUnits(2), 0.2f);
		assertEquals(-14.0f, layout.zModelUnits(2), 0.2f);
	}

	@Test
	void hexLiftLayoutSyncsSixRotorGeometryAndSpinDirections() {
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode(RotorLayoutCodec.encode(DroneConfig.hexLift()));

		assertEquals(6, layout.rotorCount());
		assertEquals(1, layout.spinDirection(0));
		assertEquals(-1, layout.spinDirection(1));
		assertTrue(layout.xModelUnits(0) > 10.0f);
		assertTrue(layout.zModelUnits(0) > 18.0f);
		assertTrue(layout.xModelUnits(1) > 25.0f);
		assertTrue(Math.abs(layout.zModelUnits(1)) < 0.2f);
		assertTrue(layout.xModelUnits(3) < -10.0f);
		assertTrue(layout.zModelUnits(3) < -18.0f);
	}

	@Test
	void octoLiftLayoutSyncsEightRotorGeometryAndSpinDirections() {
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode(RotorLayoutCodec.encode(DroneConfig.octoLift()));

		assertEquals(8, layout.rotorCount());
		assertEquals(1, layout.spinDirection(0));
		assertEquals(-1, layout.spinDirection(1));
		assertTrue(layout.xModelUnits(0) > 8.0f);
		assertTrue(layout.zModelUnits(0) > 20.0f);
		assertTrue(layout.xModelUnits(7) < -8.0f);
		assertTrue(layout.zModelUnits(7) > 20.0f);
	}

	@Test
	void coaxialX8LayoutSyncsStackedRotorHeight() {
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode(RotorLayoutCodec.encode(DroneConfig.coaxialX8()));

		assertEquals(8, layout.rotorCount());
		assertEquals(1, layout.spinDirection(0));
		assertEquals(-1, layout.spinDirection(1));
		assertEquals(layout.xModelUnits(0), layout.xModelUnits(1), 0.01f);
		assertEquals(layout.zModelUnits(0), layout.zModelUnits(1), 0.01f);
		assertTrue(layout.yModelUnits(0) > 3.0f);
		assertTrue(layout.yModelUnits(1) < -3.0f);
	}

	@Test
	void legacyThreeFieldLayoutDecodesWithZeroRotorHeight() {
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode("2;0.2000,0.3000,1;0.2000,0.3000,-1");

		assertEquals(2, layout.rotorCount());
		assertEquals(0.0f, layout.yModelUnits(0), 1.0e-6f);
		assertEquals(0.0f, layout.yModelUnits(1), 1.0e-6f);
		assertEquals(1, layout.spinDirection(0));
		assertEquals(-1, layout.spinDirection(1));
	}

	@Test
	void malformedLayoutFallsBackToQuad() {
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode("6;bad");

		assertEquals(4, layout.rotorCount());
		assertEquals(14.0f, layout.xModelUnits(0), 0.2f);
	}
}
