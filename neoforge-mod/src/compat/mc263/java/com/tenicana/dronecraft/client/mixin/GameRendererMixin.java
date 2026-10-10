package com.tenicana.dronecraft.client.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.tenicana.dronecraft.client.DroneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$hideDroneCameraHands(CameraRenderState camera, PlayerRenderState player,
            GpuTextureView depthTexture, CallbackInfo callback) {
        if (DroneClientState.isDroneCameraActive(Minecraft.getInstance().level)) callback.cancel();
    }
}
