package com.tenicana.dronecraft.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.item.DroneControllerItem;

public final class DroneItems {
	public static final Item DRONE_CONTROLLER = register(
			"drone_controller",
			DroneControllerItem::new,
			new Item.Properties().stacksTo(1)
	);

	public static final ResourceKey<CreativeModeTab> DRONE_TAB_KEY = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(),
			Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "dronecraft")
	);

	public static final CreativeModeTab DRONE_TAB = FabricItemGroup.builder()
			.icon(() -> new ItemStack(DRONE_CONTROLLER))
			.title(Component.translatable("creativeTab.fpvdrone"))
			.displayItems((parameters, output) -> {
				output.accept(DRONE_CONTROLLER);
			})
			.build();

	private DroneItems() {
	}

	public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, name));
		T item = itemFactory.apply(settings.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DRONE_TAB_KEY, DRONE_TAB);
	}
}
