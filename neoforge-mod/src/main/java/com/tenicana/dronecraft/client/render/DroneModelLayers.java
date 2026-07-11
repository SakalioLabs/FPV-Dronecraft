package com.tenicana.dronecraft.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class DroneModelLayers {
	public static final ModelLayerLocation DRONE = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone"),
			"main"
	);

	private DroneModelLayers() {
	}

	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(DRONE, DroneEntityModel::createBodyLayer);
	}
}
