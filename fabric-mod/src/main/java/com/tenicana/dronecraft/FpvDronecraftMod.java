package com.tenicana.dronecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import com.tenicana.dronecraft.command.DroneCommands;
import com.tenicana.dronecraft.diagnostic.DroneServerSelfTest;
import com.tenicana.dronecraft.network.DroneNetworking;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.sound.DroneSoundEvents;

public final class FpvDronecraftMod implements ModInitializer {
	public static final String MOD_ID = "fpvdrone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DroneEntityTypes.initialize();
		DroneItems.initialize();
		DroneSoundEvents.initialize();
		DroneNetworking.initialize();
		DroneCommands.initialize();
		DroneServerSelfTest.initialize();
		LOGGER.info("FPV Dronecraft initialized");
	}
}
