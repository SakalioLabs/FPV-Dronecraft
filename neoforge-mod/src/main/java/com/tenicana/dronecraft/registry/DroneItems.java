package com.tenicana.dronecraft.registry;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.item.DroneControllerItem;
import com.tenicana.dronecraft.item.DronePlacementItem;
import com.tenicana.dronecraft.mekanism.DronePowerTier;
import com.tenicana.dronecraft.mekanism.DroneRadioTier;

public final class DroneItems {
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FpvDronecraftMod.MOD_ID);
	private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
			Registries.CREATIVE_MODE_TAB,
			FpvDronecraftMod.MOD_ID
	);
	private static final DeferredItem<Item> PROPELLER = component("propeller");
	private static final DeferredItem<Item> BRUSHLESS_MOTOR = component("brushless_motor");
	private static final DeferredItem<Item> ESC = component("esc");
	private static final DeferredItem<Item> FLIGHT_CONTROLLER = component("flight_controller");
	private static final DeferredItem<Item> LIPO_BATTERY = component("lipo_battery");
	private static final DeferredItem<Item> CARBON_PLATE = component("carbon_plate");
	private static final DeferredItem<BlockItem> CNC_WORKSTATION = ITEMS.registerSimpleBlockItem("cnc_workstation", DroneBlocks::cncWorkstation);
	private static final DeferredItem<DronePlacementItem> CARBON_FRAME = ITEMS.registerItem(
			"carbon_frame", properties -> new DronePlacementItem(properties, true),
			new Item.Properties().stacksTo(1)
	);
	private static final DeferredItem<Item> RECEIVER = component("receiver");
	private static final DeferredItem<Item> FPV_CAMERA = component("fpv_camera");
	private static final DeferredItem<DronePlacementItem> DRONE_AIRFRAME = ITEMS.registerItem(
			"drone_airframe", properties -> new DronePlacementItem(properties, true),
			new Item.Properties().stacksTo(1)
	);
	private static final Map<DronePowerTier, DeferredItem<Item>> POWER_MODULES = new EnumMap<>(DronePowerTier.class);
	private static final Map<DroneRadioTier, DeferredItem<Item>> RADIO_MODULES = new EnumMap<>(DroneRadioTier.class);
	private static final DeferredItem<Item> QUANTUM_LINK_MODULE = component("quantum_link_module");
	private static final DeferredItem<DronePlacementItem> FPV_DRONE = ITEMS.registerItem(
			"drone",
			DronePlacementItem::new,
			new Item.Properties().stacksTo(1)
	);
	private static final DeferredItem<DroneControllerItem> DRONE_CONTROLLER = ITEMS.registerItem(
			"drone_controller",
			DroneControllerItem::new,
			new Item.Properties().stacksTo(1)
	);
	private static final DeferredHolder<CreativeModeTab, CreativeModeTab> DRONE_TAB = CREATIVE_TABS.register(
			"dronecraft",
			() -> CreativeModeTab.builder()
					.icon(() -> new ItemStack(droneController()))
					.title(Component.translatable("creativeTab.fpvdrone"))
					.displayItems((parameters, output) -> {
						output.accept(propeller());
						output.accept(brushlessMotor());
						output.accept(esc());
						output.accept(flightController());
						output.accept(lipoBattery());
						output.accept(carbonPlate());
						output.accept(cncWorkstation());
						output.accept(receiver());
						output.accept(fpvCamera());
						output.accept(carbonFrame());
						output.accept(droneAirframe());
						POWER_MODULES.values().forEach(item -> output.accept(item.get()));
						RADIO_MODULES.values().forEach(item -> output.accept(item.get()));
						output.accept(quantumLinkModule());
						output.accept(drone());
						output.accept(droneController());
					})
					.build()
	);

	static {
		for (DronePowerTier tier : DronePowerTier.values()) {
			POWER_MODULES.put(tier, component(tier.getSerializedName() + "_power_module"));
		}
		for (DroneRadioTier tier : DroneRadioTier.values()) {
			RADIO_MODULES.put(tier, component(tier.getSerializedName() + "_radio_module"));
		}
	}

	private DroneItems() {
	}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
		CREATIVE_TABS.register(modEventBus);
	}

	public static Item droneController() {
		return DRONE_CONTROLLER.get();
	}

	public static Item propeller() {
		return PROPELLER.get();
	}

	public static Item brushlessMotor() {
		return BRUSHLESS_MOTOR.get();
	}

	public static Item esc() {
		return ESC.get();
	}

	public static Item flightController() {
		return FLIGHT_CONTROLLER.get();
	}

	public static Item lipoBattery() {
		return LIPO_BATTERY.get();
	}

	public static Item receiver() { return RECEIVER.get(); }

	public static Item carbonPlate() { return CARBON_PLATE.get(); }

	public static Item cncWorkstation() { return CNC_WORKSTATION.get(); }

	public static Item fpvCamera() { return FPV_CAMERA.get(); }

	public static Item carbonFrame() {
		return CARBON_FRAME.get();
	}

	public static Item droneAirframe() {
		return DRONE_AIRFRAME.get();
	}

	public static Item drone() {
		return FPV_DRONE.get();
	}

	public static Item quantumLinkModule() {
		return QUANTUM_LINK_MODULE.get();
	}

	public static Item powerModule(DronePowerTier tier) {
		return POWER_MODULES.get(tier).get();
	}

	public static Item radioModule(DroneRadioTier tier) {
		return RADIO_MODULES.get(tier).get();
	}

	public static DronePowerTier powerTier(ItemStack stack) {
		for (Map.Entry<DronePowerTier, DeferredItem<Item>> entry : POWER_MODULES.entrySet()) {
			if (stack.is(entry.getValue().get())) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static DroneRadioTier radioTier(ItemStack stack) {
		for (Map.Entry<DroneRadioTier, DeferredItem<Item>> entry : RADIO_MODULES.entrySet()) {
			if (stack.is(entry.getValue().get())) {
				return entry.getKey();
			}
		}
		return null;
	}

	private static DeferredItem<Item> component(String name) {
		return ITEMS.registerSimpleItem(name);
	}
}
