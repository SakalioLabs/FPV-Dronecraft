package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.sounds.SoundSource;

@Mixin(SoundEngine.class)
public interface SoundEngineMainOutputAccessor {
    @Accessor("library") Library mainOutput$getLibrary();
    @Accessor("gainBySource") Object2FloatMap<SoundSource> mainOutput$getGains();
}
