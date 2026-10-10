package com.tenicana.dronecraft.client.diagnostic;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.mixin.CuboidItemModelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.resources.Identifier;

/** Checks the actual native item-model cache without requiring bound item components. */
public final class ClientModelReadiness {
    private static final Identifier CONTROLLER = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone_controller");
    private static final Identifier CONTROLLER_TEXTURE = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "item/fpv_controller_v1");
    private ClientModelReadiness() { }

    public static boolean controllerModelReady(Minecraft client) {
        var model = client.getModelManager().getItemModel(CONTROLLER);
        if (!(model instanceof CuboidItemModelWrapper) || !(model instanceof CuboidItemModelAccessor baked)) return false;
        var quads = baked.fpvdrone$bakedQuads().getAll();
        // A missing parent can still produce a cuboid wrapper around fallback geometry.
        // Require nonempty baked controller geometry with its resolved native sprite.
        return !quads.isEmpty() && quads.stream().allMatch(quad ->
                CONTROLLER_TEXTURE.equals(quad.materialInfo().sprite().contents().name()));
    }
}
