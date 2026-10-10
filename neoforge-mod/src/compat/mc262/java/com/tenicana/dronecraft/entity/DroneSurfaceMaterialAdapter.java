package com.tenicana.dronecraft.entity;

import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Retains the exact contact-material groups across native block registry API changes. */
final class DroneSurfaceMaterialAdapter {
	private static final Set<Block> CONCRETE = Set.copyOf(Blocks.CONCRETE.asList());
	private static final Set<Block> WOOL = Set.copyOf(Blocks.WOOL.asList());
	private static final Set<Block> COPPER = copperBlocks();

	private static Set<Block> copperBlocks() {
		var blocks = new java.util.HashSet<Block>(Blocks.COPPER_BLOCK.asList());
		blocks.addAll(Blocks.CUT_COPPER.asList());
		return Set.copyOf(blocks);
	}

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
