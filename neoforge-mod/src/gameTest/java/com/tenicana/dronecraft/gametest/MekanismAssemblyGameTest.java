package com.tenicana.dronecraft.gametest;

import java.util.List;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.mekanism.DroneLoadout;
import com.tenicana.dronecraft.mekanism.DronePowerTier;
import com.tenicana.dronecraft.mekanism.DroneRadioTier;
import com.tenicana.dronecraft.recipe.DroneAssemblyRecipe;
import com.tenicana.dronecraft.registry.DroneDataComponents;
import com.tenicana.dronecraft.registry.DroneItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FpvDronecraftMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MekanismAssemblyGameTest {
	private MekanismAssemblyGameTest() {
	}

	@GameTest(template = "empty", templateNamespace = FpvDronecraftMod.MOD_ID, timeoutTicks = 20)
	public static void assembly_retains_selected_modules_and_stack_persistence(GameTestHelper helper) {
		DroneAssemblyRecipe recipe = new DroneAssemblyRecipe(CraftingBookCategory.EQUIPMENT);
		CraftingInput input = CraftingInput.of(2, 2, List.of(
				new ItemStack(DroneItems.radioModule(DroneRadioTier.ELITE)),
				new ItemStack(DroneItems.droneAirframe()),
				new ItemStack(DroneItems.quantumLinkModule()),
				new ItemStack(DroneItems.powerModule(DronePowerTier.ADVANCED))));
		helper.assertTrue(recipe.matches(input, helper.getLevel()), "valid modules did not assemble");
		ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
		DroneLoadout expected = new DroneLoadout(DronePowerTier.ADVANCED, DroneRadioTier.ELITE, true);
		helper.assertTrue(result.is(DroneItems.drone()), "assembly did not return the placement item");
		helper.assertTrue(result.getCount() == 1, "assembly returned the wrong stack count");
		helper.assertTrue(expected.equals(result.get(DroneDataComponents.DRONE_LOADOUT.get())), "assembly lost selected modules");
		var ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
		var encoded = ItemStack.CODEC.encodeStart(ops, result).getOrThrow();
		ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
		helper.assertTrue(expected.equals(restored.get(DroneDataComponents.DRONE_LOADOUT.get())), "saved item lost loadout component");
		helper.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "consumed module had a crafting remainder");
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = FpvDronecraftMod.MOD_ID, timeoutTicks = 20)
	public static void assembly_rejects_duplicate_missing_and_unrelated_modules(GameTestHelper helper) {
		DroneAssemblyRecipe recipe = new DroneAssemblyRecipe(CraftingBookCategory.EQUIPMENT);
		ItemStack airframe = new ItemStack(DroneItems.droneAirframe());
		ItemStack power = new ItemStack(DroneItems.powerModule(DronePowerTier.BASIC));
		ItemStack radio = new ItemStack(DroneItems.radioModule(DroneRadioTier.BASIC));
		ItemStack quantum = new ItemStack(DroneItems.quantumLinkModule());
		for (List<ItemStack> invalid : List.of(
				List.of(airframe, power),
				List.of(airframe, radio),
				List.of(power, radio),
				List.of(airframe, power, power, radio),
				List.of(airframe, power, radio, radio),
				List.of(airframe, airframe, power, radio),
				List.of(airframe, power, radio, quantum, quantum),
				List.of(airframe, power, radio, new ItemStack(Items.DIRT)))) {
			CraftingInput input = CraftingInput.of(invalid.size(), 1, invalid);
			helper.assertTrue(!recipe.matches(input, helper.getLevel()), "invalid module combination matched");
			helper.assertTrue(recipe.assemble(input, helper.getLevel().registryAccess()).isEmpty(), "invalid combination produced a drone");
		}
		CraftingInput basic = CraftingInput.of(3, 1, List.of(airframe, power, radio));
		helper.assertTrue(recipe.matches(basic, helper.getLevel()), "basic assembly required a quantum module");
		helper.assertTrue(DroneLoadout.BASIC.equals(recipe.assemble(basic, helper.getLevel().registryAccess())
				.get(DroneDataComponents.DRONE_LOADOUT.get())), "basic assembly did not retain basic tiers");
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = FpvDronecraftMod.MOD_ID, timeoutTicks = 20)
	public static void vanilla_recipe_keeps_standalone_drone_unconfigured(GameTestHelper helper) {
		CraftingInput input = CraftingInput.of(3, 3, List.of(
				new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.IRON_INGOT),
				new ItemStack(Items.REDSTONE), new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(Items.REDSTONE),
				new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.IRON_INGOT)));
		var manager = helper.getLevel().getRecipeManager();
		var recipe = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(recipe.isPresent(), "standalone drone recipe was missing");
		ItemStack result = recipe.orElseThrow().value().assemble(input, helper.getLevel().registryAccess());
		helper.assertTrue(result.is(DroneItems.drone()), "vanilla recipe did not create the placement item");
		helper.assertTrue(!result.has(DroneDataComponents.DRONE_LOADOUT.get()), "vanilla recipe added a restricted loadout");
		if (!ModList.get().isLoaded("mekanism")) {
			helper.assertTrue(manager.byKey(ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "basic_power_module")).isEmpty(),
					"absent Mekanism still loaded its power recipe");
			helper.assertTrue(manager.byKey(ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone_assembly")).isEmpty(),
					"absent Mekanism still loaded its assembly recipe");
		}
		helper.succeed();
	}
}
