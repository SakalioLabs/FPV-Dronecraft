package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.tenicana.dronecraft.sim.ContactDynamics;

class DroneEntityContactSurfaceTest {
	private static final double EPSILON = 1.0e-9;

	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void hardMasonryBlocksUseReferenceInspiredContactSurface() throws ReflectiveOperationException {
		ContactDynamics.ContactSurface concrete = surfaceFor(Blocks.WHITE_CONCRETE);
		ContactDynamics.ContactSurface stone = surfaceFor(Blocks.STONE_BRICKS);

		assertEquals(concrete, stone);
		assertTrue(concrete.frictionMultiplier() > ContactDynamics.DEFAULT_SURFACE.frictionMultiplier());
		assertTrue(concrete.restitutionMultiplier() < ContactDynamics.DEFAULT_SURFACE.restitutionMultiplier());
		assertTrue(concrete.scrapeMultiplier() > ContactDynamics.DEFAULT_SURFACE.scrapeMultiplier());
	}

	@Test
	void woodMetalAndSoftBlocksNoLongerFallBackToDefaultSurface() throws ReflectiveOperationException {
		ContactDynamics.ContactSurface wood = surfaceFor(Blocks.OAK_PLANKS);
		ContactDynamics.ContactSurface metal = surfaceFor(Blocks.IRON_BLOCK);
		ContactDynamics.ContactSurface wool = surfaceFor(Blocks.WHITE_WOOL);

		assertTrue(wood.scrapeMultiplier() < ContactDynamics.DEFAULT_SURFACE.scrapeMultiplier());
		assertTrue(metal.frictionMultiplier() < ContactDynamics.DEFAULT_SURFACE.frictionMultiplier());
		assertTrue(metal.scrapeMultiplier() > wood.scrapeMultiplier());
		assertTrue(wool.restitutionMultiplier() < wood.restitutionMultiplier());
		assertTrue(wool.scrapeMultiplier() < wood.scrapeMultiplier());
	}

	@Test
	void existingSpecialBlocksKeepTheirExplicitContactTuning() throws ReflectiveOperationException {
		assertEquals(0.18, surfaceFor(Blocks.ICE).frictionMultiplier(), EPSILON);
		assertEquals(2.25, surfaceFor(Blocks.SLIME_BLOCK).restitutionMultiplier(), EPSILON);
		assertEquals(1.95, surfaceFor(Blocks.HONEY_BLOCK).frictionMultiplier(), EPSILON);
		assertEquals(1.35, surfaceFor(Blocks.SAND).frictionMultiplier(), EPSILON);
		assertEquals(1.65, surfaceFor(Blocks.SOUL_SAND).frictionMultiplier(), EPSILON);
		assertEquals(2.20, surfaceFor(Blocks.CACTUS).scrapeMultiplier(), EPSILON);
	}

	private static ContactDynamics.ContactSurface surfaceFor(Block block) throws ReflectiveOperationException {
		Method method = DroneEntity.class.getDeclaredMethod("surfaceForBlock", BlockState.class);
		method.setAccessible(true);
		return (ContactDynamics.ContactSurface) method.invoke(null, block.defaultBlockState());
	}
}
