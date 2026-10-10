package com.tenicana.dronecraft.clientvalidation;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Separate runtime mod and package, so the production jar has no validation entry point. */
@Mod(ClientValidationMod.MOD_ID)
public final class ClientValidationMod {
    public static final String MOD_ID = "fpvdrone_client_validation";

    public ClientValidationMod() {
        if (!Boolean.getBoolean("fpvdrone.client_validation")) return;
        NativeClientWorldValidation validation = new NativeClientWorldValidation();
        NeoForge.EVENT_BUS.addListener(validation::clientTick);
        NeoForge.EVENT_BUS.addListener(validation::serverTick);
        NeoForge.EVENT_BUS.addListener(validation::renderLevel);
        NeoForge.EVENT_BUS.addListener(validation::renderPlayer);
        NeoForge.EVENT_BUS.addListener(validation::damage);
        NeoForge.EVENT_BUS.addListener(validation::entityLeave);
        NeoForge.EVENT_BUS.addListener(validation::serverStopping);
        NeoForge.EVENT_BUS.addListener(validation::serverStopped);
    }
}
