package com.tenicana.dronecraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.item.DroneControllerItem;

public final class DroneItems {
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FpvDronecraftMod.MOD_ID);
	private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
			Registries.CREATIVE_MODE_TAB,
			FpvDronecraftMod.MOD_ID
	);
	private static final DeferredItem<DroneControllerItem> DRONE_CONTROLLER = ITEMS.registerItem(
			"drone_controller",
			DroneControllerItem::new,
			properties -> properties.stacksTo(1)
	);
	private static final DeferredHolder<CreativeModeTab, CreativeModeTab> DRONE_TAB = CREATIVE_TABS.register(
			"dronecraft",
			() -> CreativeModeTab.builder()
					.icon(() -> new ItemStack(droneController()))
					.title(Component.translatable("creativeTab.fpvdrone"))
					.displayItems((parameters, output) -> output.accept(droneController()))
					.build()
	);

	private DroneItems() {
	}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
		CREATIVE_TABS.register(modEventBus);
	}

	public static Item droneController() {
		return DRONE_CONTROLLER.get();
	}
}
