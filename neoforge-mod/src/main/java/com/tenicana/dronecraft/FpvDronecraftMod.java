package com.tenicana.dronecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(FpvDronecraftMod.MOD_ID)
public final class FpvDronecraftMod {
	public static final String MOD_ID = "fpvdrone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public FpvDronecraftMod(IEventBus modEventBus) {
		LOGGER.info("FPV Dronecraft NeoForge adapter initialized");
	}
}
