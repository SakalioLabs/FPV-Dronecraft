package com.tenicana.dronecraft.entity;

import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Retains the exact contact-material groups across native block registry API changes. */
final class DroneSurfaceMaterialAdapter {
	private static final Set<Block> CONCRETE = Set.of(
			Blocks.WHITE_CONCRETE,
			Blocks.LIGHT_GRAY_CONCRETE,
			Blocks.GRAY_CONCRETE,
			Blocks.BLACK_CONCRETE,
			Blocks.BROWN_CONCRETE,
			Blocks.RED_CONCRETE,
			Blocks.ORANGE_CONCRETE,
			Blocks.YELLOW_CONCRETE,
			Blocks.LIME_CONCRETE,
			Blocks.GREEN_CONCRETE,
			Blocks.CYAN_CONCRETE,
			Blocks.LIGHT_BLUE_CONCRETE,
			Blocks.BLUE_CONCRETE,
			Blocks.PURPLE_CONCRETE,
			Blocks.MAGENTA_CONCRETE,
			Blocks.PINK_CONCRETE);
	private static final Set<Block> COPPER = Set.of(
			Blocks.COPPER_BLOCK,
			Blocks.EXPOSED_COPPER,
			Blocks.WEATHERED_COPPER,
			Blocks.OXIDIZED_COPPER,
			Blocks.CUT_COPPER,
			Blocks.EXPOSED_CUT_COPPER,
			Blocks.WEATHERED_CUT_COPPER,
			Blocks.OXIDIZED_CUT_COPPER,
			Blocks.WAXED_COPPER_BLOCK,
			Blocks.WAXED_EXPOSED_COPPER,
			Blocks.WAXED_WEATHERED_COPPER,
			Blocks.WAXED_OXIDIZED_COPPER,
			Blocks.WAXED_CUT_COPPER,
			Blocks.WAXED_EXPOSED_CUT_COPPER,
			Blocks.WAXED_WEATHERED_CUT_COPPER,
			Blocks.WAXED_OXIDIZED_CUT_COPPER);
	private static final Set<Block> WOOL = Set.of(
			Blocks.WHITE_WOOL,
			Blocks.LIGHT_GRAY_WOOL,
			Blocks.GRAY_WOOL,
			Blocks.BLACK_WOOL,
			Blocks.BROWN_WOOL,
			Blocks.RED_WOOL,
			Blocks.ORANGE_WOOL,
			Blocks.YELLOW_WOOL,
			Blocks.LIME_WOOL,
			Blocks.GREEN_WOOL,
			Blocks.CYAN_WOOL,
			Blocks.LIGHT_BLUE_WOOL,
			Blocks.BLUE_WOOL,
			Blocks.PURPLE_WOOL,
			Blocks.MAGENTA_WOOL,
			Blocks.PINK_WOOL);

	private DroneSurfaceMaterialAdapter() {
	}

	static boolean isConcrete(BlockState state) {
		return CONCRETE.contains(state.getBlock());
	}

	static boolean isCopperMetal(BlockState state) {
		return COPPER.contains(state.getBlock());
	}

	static boolean isWool(BlockState state) {
		return WOOL.contains(state.getBlock());
	}
}
