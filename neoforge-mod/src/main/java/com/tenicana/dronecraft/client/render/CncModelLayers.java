package com.tenicana.dronecraft.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class CncModelLayers {
    public static final ModelLayerLocation CNC_WORKSTATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "cnc_workstation"), "main");

    private CncModelLayers() {}

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CNC_WORKSTATION, CncMachineGeometry::createLayer);
    }
}
