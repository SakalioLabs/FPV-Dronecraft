package com.tenicana.dronecraft.client.mixin;
import com.tenicana.dronecraft.client.DroneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private GameRenderState gameRenderState;

    // Native 26.1/26.2 render consumers read this snapshot, not a CameraRenderState flag.
    @Inject(method="extractOptions",at=@At("RETURN"))
    private void fpvdrone$extractDroneViewMode(CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (DroneClientState.isDroneCameraActive(level)) {
            gameRenderState.optionsRenderState.cameraType = DroneClientState.isFpvActive(level)
                    ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
        }
    }

    // Preserve native world feature submission and buffer flush before suppressing hands.
    @Inject(method="renderItemInHand",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
            ordinal=0,shift=At.Shift.AFTER),cancellable=true)
    private void fpvdrone$hideDroneCameraHands(CameraRenderState camera,float partialTick,Matrix4fc view,CallbackInfo callback) {
        if (DroneClientState.isDroneCameraActive(Minecraft.getInstance().level)) callback.cancel();
    }
}
