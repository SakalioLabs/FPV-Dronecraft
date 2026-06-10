package com.tenicana.dronecraft.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class DroneModelLayers {
	public static final ModelLayerLocation DRONE = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone"),
			"main"
	);

	private DroneModelLayers() {
	}

	public static void initialize() {
		EntityModelLayerRegistry.registerModelLayer(DRONE, DroneEntityModel::createBodyLayer);
	}
}
