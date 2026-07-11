package com.tenicana.dronecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.tenicana.dronecraft.command.DroneCommands;
import com.tenicana.dronecraft.diagnostic.DroneServerSelfTest;
import com.tenicana.dronecraft.network.DroneNetworking;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.sound.DroneSoundEvents;

@Mod(FpvDronecraftMod.MOD_ID)
public final class FpvDronecraftMod {
	public static final String MOD_ID = "fpvdrone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public FpvDronecraftMod(IEventBus modEventBus) {
		DroneEntityTypes.register(modEventBus);
		DroneItems.register(modEventBus);
		DroneSoundEvents.register(modEventBus);
		modEventBus.addListener(DroneNetworking::registerPayloadHandlers);
		DroneNetworking.initialize();
		DroneCommands.initialize();
		DroneServerSelfTest.initialize();
		LOGGER.info("FPV Dronecraft NeoForge adapter initialized");
	}
}
