package com.tenicana.dronecraft.clientvalidation.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only native terrain observations; never moves, compiles or submits sections. */
@Mixin(LevelRenderer.class)
public interface TerrainRenderProbeAccess {
    @Accessor("viewArea")
    ViewArea fpvValidation$viewArea();

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> fpvValidation$visibleSections();
}
