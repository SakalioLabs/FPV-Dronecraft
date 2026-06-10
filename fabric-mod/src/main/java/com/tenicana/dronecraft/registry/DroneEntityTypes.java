package com.tenicana.dronecraft.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.entity.DroneEntity;

public final class DroneEntityTypes {
	public static final EntityType<DroneEntity> DRONE = register(
			"drone",
			EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
					.sized(0.85f, 0.35f)
					.eyeHeight(0.25f)
					.clientTrackingRange(96)
					.updateInterval(1)
	);

	private DroneEntityTypes() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void initialize() {
	}

	public static void registerAttributes() {
		FabricDefaultAttributeRegistry.register(DRONE, DroneEntity.createAttributes());
	}
}
