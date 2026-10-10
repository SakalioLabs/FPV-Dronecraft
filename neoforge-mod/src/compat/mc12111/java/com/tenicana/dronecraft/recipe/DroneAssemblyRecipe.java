package com.tenicana.dronecraft.recipe;

import com.tenicana.dronecraft.mekanism.DroneLoadout;
import com.tenicana.dronecraft.mekanism.DronePowerTier;
import com.tenicana.dronecraft.mekanism.DroneRadioTier;
import com.tenicana.dronecraft.registry.DroneDataComponents;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.registry.DroneRecipeSerializers;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class DroneAssemblyRecipe extends CustomRecipe {
	public DroneAssemblyRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return findLoadout(input) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
		DroneLoadout loadout = findLoadout(input);
		if (loadout == null) {
			return ItemStack.EMPTY;
		}
		ItemStack drone = new ItemStack(DroneItems.drone());
		drone.set(DroneDataComponents.DRONE_LOADOUT.get(), loadout);
		return drone;
	}

	@Override
	public RecipeSerializer<DroneAssemblyRecipe> getSerializer() {
		return DroneRecipeSerializers.droneAssembly();
	}

	private static DroneLoadout findLoadout(CraftingInput input) {
		boolean airframe = false;
		boolean quantum = false;
		DronePowerTier power = null;
		DroneRadioTier radio = null;
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(DroneItems.droneAirframe()) && !airframe) {
				airframe = true;
			} else if (stack.is(DroneItems.quantumLinkModule()) && !quantum) {
				quantum = true;
			} else {
				DronePowerTier candidatePower = DroneItems.powerTier(stack);
				if (candidatePower != null && power == null) {
					power = candidatePower;
					continue;
				}
				DroneRadioTier candidateRadio = DroneItems.radioTier(stack);
				if (candidateRadio != null && radio == null) {
					radio = candidateRadio;
					continue;
				}
				return null;
			}
		}
		if (!airframe || power == null || radio == null) {
			return null;
		}
		return new DroneLoadout(power, radio, quantum);
	}
}
