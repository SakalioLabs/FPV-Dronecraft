package com.tenicana.dronecraft.client.render;

import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;

/** Immutable values also remain safe when a deferred renderer submits several machines. */
public record CncVisualState(float progress, boolean processing, int stage, boolean recipeWorkpiece,
                             float doorDegrees, float drawerOffset, Layer layer) {
    public enum Layer { OPAQUE, GLASS, LAMP }

    public CncVisualState {
        progress = Float.isFinite(progress) ? Math.max(0.0f, Math.min(1.0f, progress)) : 0.0f;
        stage = Math.max(-1, Math.min(4, stage));
    }

    public static CncVisualState capture(CncWorkstationBlockEntity machine, float partialTick) {
        float progress = machine.getProgress();
        if (machine.isProcessing()) {
            // Native update packets arrive every ten ticks; never extrapolate beyond that lease.
            progress += Math.min(10.0f, machine.getProgressAgeTicks() + Math.max(0.0f, partialTick));
        } else if (!machine.getItem(1).isEmpty()) {
            progress = machine.getProcessingTicks();
        }
        return new CncVisualState(progress / machine.getProcessingTicks(), machine.isProcessing(),
                machine.getWorkpieceStage(), true, 0.0f, 0.0f, Layer.OPAQUE);
    }

    public CncVisualState withLayer(Layer selected) {
        return new CncVisualState(progress, processing, stage, recipeWorkpiece, doorDegrees, drawerOffset, selected);
    }

    int lampIndex() {
        return processing ? 1 : stage == 4 ? 3 : progress > 0.0f ? 2 : 0;
    }
}
