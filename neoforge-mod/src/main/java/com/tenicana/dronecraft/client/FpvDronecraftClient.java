package com.tenicana.dronecraft.client;

import static com.tenicana.dronecraft.FpvDronecraftMod.MOD_ID;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import com.tenicana.dronecraft.client.control.DroneClientControls;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public final class FpvDronecraftClient {
	private FpvDronecraftClient() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(DroneClientControls::initialize);
	}

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		DroneClientControls.registerKeyMappings(event);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		DroneClientControls.onClientTick(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		DroneClientControls.onLoggingIn(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		DroneClientControls.onLoggingOut(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		Minecraft client = Minecraft.getInstance();
		if (event.getLevel().isClientSide() && event.getLevel() == client.level) {
			DroneClientControls.onLevelUnload(client);
		}
	}

	@SubscribeEvent
	public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (event.getLevel().isClientSide() && event.getLevel() == client.level) {
			DroneClientControls.onEntityLeaveLevel(client, event.getEntity());
		}
	}

	@SubscribeEvent
	public static void onClientStopping(ClientStoppingEvent event) {
		DroneClientControls.onClientStopping(event.getClient());
	}
}
