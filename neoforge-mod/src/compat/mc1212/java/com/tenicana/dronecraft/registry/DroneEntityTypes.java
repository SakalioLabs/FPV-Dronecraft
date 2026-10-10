package com.tenicana.dronecraft.registry;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.entity.DroneEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DroneEntityTypes {
	private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
			Registries.ENTITY_TYPE, FpvDronecraftMod.MOD_ID);
	private static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE = ENTITY_TYPES.register(
			"drone", () -> EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
					.sized(0.85f, 0.35f)
					.eyeHeight(0.25f)
					.clientTrackingRange(96)
					.updateInterval(1)
					.build(ResourceKey.create(Registries.ENTITY_TYPE,
							ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone"))));

	private DroneEntityTypes() {
	}

	public static void register(IEventBus bus) {
		ENTITY_TYPES.register(bus);
	}

	public static EntityType<DroneEntity> drone() {
		return DRONE.get();
	}
}
