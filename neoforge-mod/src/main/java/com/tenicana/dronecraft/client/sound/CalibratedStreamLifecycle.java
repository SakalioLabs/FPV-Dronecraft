package com.tenicana.dronecraft.client.sound;

import java.util.function.BooleanSupplier;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;

/** Calibrated stream gates apply to both internal and ordinary native attenuation. */
public final class CalibratedStreamLifecycle {
    private CalibratedStreamLifecycle() { }

    public static void stop(SoundInstance sound) {
        if (sound instanceof DroneLoopSoundInstance drone) drone.retireCalibratedStreams();
    }

    public static void stopAll() { CalibratedStreamLifetime.retireAll(); }

    public static boolean attach(AudioStream stream, Runnable original) {
        if (stream instanceof CalibratedAssetAudioStream calibrated) return calibrated.attachWhileLive(original);
        original.run();
        return true;
    }

    public static boolean mayPlay(AudioStream stream) {
        return !(stream instanceof CalibratedAssetAudioStream calibrated) || calibrated.playbackLive();
    }

    public static boolean play(AudioStream stream, BooleanSupplier original) {
        return stream instanceof CalibratedAssetAudioStream calibrated
                ? calibrated.playWhileLive(original) : original.getAsBoolean();
    }
}
