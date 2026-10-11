package com.tenicana.dronecraft.client.diagnostic;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.registry.DroneItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Checks the native baked item model and its actual GUI render layers after reload. */
public final class ClientModelReadiness {
    private static final ResourceLocation CONTROLLER = ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone_controller");
    private ClientModelReadiness() { }

    public static boolean controllerModelReady(Minecraft client) {
        var model = client.getModelManager().getItemModel(CONTROLLER);
        if (model == null || model instanceof MissingItemModel) return false;
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(state, new ItemStack(DroneItems.droneController()),
                ItemDisplayContext.GUI, false, null, null, 0);
        return !state.isEmpty();
    }

    public static boolean cncModelsReady(Minecraft client) {
        return itemReady(client, "cnc_workstation", DroneItems.cncWorkstation())
                && itemReady(client, "carbon_plate", DroneItems.carbonPlate());
    }

    private static boolean itemReady(Minecraft client, String path, net.minecraft.world.item.Item item) {
        var model = client.getModelManager().getItemModel(ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, path));
        if (model == null || model instanceof MissingItemModel) return false;
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(state, new ItemStack(item),
                ItemDisplayContext.GUI, false, null, null, 0);
        return !state.isEmpty();
    }
}
