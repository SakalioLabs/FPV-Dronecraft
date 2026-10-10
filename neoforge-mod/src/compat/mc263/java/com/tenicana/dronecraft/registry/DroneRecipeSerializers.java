package com.tenicana.dronecraft.registry;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.recipe.DroneAssemblyRecipe;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DroneRecipeSerializers {
	private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
			Registries.RECIPE_SERIALIZER,
			FpvDronecraftMod.MOD_ID
	);
	private static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DroneAssemblyRecipe>> DRONE_ASSEMBLY = SERIALIZERS.register(
			"drone_assembly",
			() -> new RecipeSerializer<>(MapCodec.unit(DroneAssemblyRecipe::new),
					StreamCodec.unit(new DroneAssemblyRecipe()))
	);

	private DroneRecipeSerializers() {
	}

	public static void register(IEventBus modEventBus) {
		SERIALIZERS.register(modEventBus);
	}

	public static RecipeSerializer<DroneAssemblyRecipe> droneAssembly() {
		return DRONE_ASSEMBLY.get();
	}
}
