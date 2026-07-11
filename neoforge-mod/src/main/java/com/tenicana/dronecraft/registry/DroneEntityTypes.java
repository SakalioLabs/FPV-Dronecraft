package com.tenicana.dronecraft.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.entity.DroneEntity;

public final class DroneEntityTypes {
	private static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(FpvDronecraftMod.MOD_ID);
	private static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE = ENTITY_TYPES.registerEntityType(
			"drone",
			DroneEntity::new,
			MobCategory.MISC,
			builder -> builder
					.sized(0.85f, 0.35f)
					.eyeHeight(0.25f)
					.clientTrackingRange(96)
					.updateInterval(1)
	);

	private DroneEntityTypes() {
	}

	public static void register(IEventBus modEventBus) {
		ENTITY_TYPES.register(modEventBus);
	}

	public static EntityType<DroneEntity> drone() {
		return DRONE.get();
	}
}
