package com.tenicana.dronecraft.registry;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.mekanism.DroneLoadout;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DroneDataComponents {
	private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(
			Registries.DATA_COMPONENT_TYPE,
			FpvDronecraftMod.MOD_ID
	);
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<DroneLoadout>> DRONE_LOADOUT = COMPONENTS.register(
			"drone_loadout",
			() -> DataComponentType.<DroneLoadout>builder()
					.persistent(DroneLoadout.CODEC)
					.networkSynchronized(DroneLoadout.STREAM_CODEC)
					.cacheEncoding()
					.build()
	);

	private DroneDataComponents() {
	}

	public static void register(IEventBus modEventBus) {
		COMPONENTS.register(modEventBus);
	}
}
