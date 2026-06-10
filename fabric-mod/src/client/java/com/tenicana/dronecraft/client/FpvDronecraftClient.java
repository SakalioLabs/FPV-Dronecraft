package com.tenicana.dronecraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.client.hud.DroneHud;
import com.tenicana.dronecraft.client.render.DroneEntityRenderer;
import com.tenicana.dronecraft.client.render.DroneModelLayers;
import com.tenicana.dronecraft.registry.DroneEntityTypes;

public final class FpvDronecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DroneModelLayers.initialize();
		EntityRendererRegistry.register(DroneEntityTypes.DRONE, DroneEntityRenderer::new);
		DroneClientControls.initialize();
		DroneHud.initialize();
	}
}
