package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.resources.model.geometry.ItemQuads;

/** Read-only startup evidence from the native baked item model, not an ItemStack. */
@Mixin(CuboidItemModelWrapper.class)
public interface CuboidItemModelAccessor {
    @Accessor("itemQuads")
    ItemQuads fpvdrone$bakedQuads();
}
