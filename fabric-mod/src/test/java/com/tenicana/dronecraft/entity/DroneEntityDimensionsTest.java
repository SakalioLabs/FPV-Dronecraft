package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.PathfinderMob;

import com.tenicana.dronecraft.sim.DroneConfig;

class DroneEntityDimensionsTest {
	@Test
	void droneEntityUsesMechanicalEntityBaseInsteadOfMobAi() {
		assertTrue(Entity.class.isAssignableFrom(DroneEntity.class));
		assertFalse(PathfinderMob.class.isAssignableFrom(DroneEntity.class));
	}

	@Test
	void racingQuadKeepsCompactDefaultCollisionFootprint() {
		EntityDimensions dimensions = DroneAirframeDimensions.forConfig(DroneConfig.racingQuad());

		assertEquals(0.85f, dimensions.width(), 1.0e-6f);
		assertEquals(0.35f, dimensions.height(), 1.0e-6f);
		assertEquals(0.25f, dimensions.eyeHeight(), 1.0e-6f);
	}

	@Test
	void hexLiftExpandsCollisionFootprintToRotorGeometry() {
		EntityDimensions racing = DroneAirframeDimensions.forConfig(DroneConfig.racingQuad());
		EntityDimensions hex = DroneAirframeDimensions.forConfig(DroneConfig.hexLift());

		assertTrue(hex.width() > racing.width() + 0.30f);
		assertTrue(hex.width() > 1.15f);
		assertTrue(hex.width() < 1.30f);
		assertEquals(racing.height(), hex.height(), 1.0e-6f);
	}

	@Test
	void octoLiftCollisionFootprintIsWiderThanHexLift() {
		EntityDimensions hex = DroneAirframeDimensions.forConfig(DroneConfig.hexLift());
		EntityDimensions octo = DroneAirframeDimensions.forConfig(DroneConfig.octoLift());

		assertTrue(octo.width() > hex.width() + 0.15f);
		assertTrue(octo.width() > 1.40f);
		assertTrue(octo.width() < 1.55f);
		assertEquals(hex.height(), octo.height(), 1.0e-6f);
	}

	@Test
	void coaxialX8CollisionFootprintReflectsCompactStackedArms() {
		EntityDimensions racing = DroneAirframeDimensions.forConfig(DroneConfig.racingQuad());
		EntityDimensions octo = DroneAirframeDimensions.forConfig(DroneConfig.octoLift());
		EntityDimensions coaxial = DroneAirframeDimensions.forConfig(DroneConfig.coaxialX8());

		assertTrue(coaxial.width() > racing.width() + 0.25f);
		assertTrue(coaxial.width() < octo.width() - 0.10f);
		assertTrue(coaxial.width() > 1.15f);
		assertTrue(coaxial.width() < 1.35f);
		assertEquals(octo.height(), coaxial.height(), 1.0e-6f);
	}
}
