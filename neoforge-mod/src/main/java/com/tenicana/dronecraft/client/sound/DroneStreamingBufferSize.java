package com.tenicana.dronecraft.client.sound;

import net.minecraft.client.sounds.AudioStream;

/** Equal dry/wet queue blocks keep dynamic drone synthesis close to playback. */
public final class DroneStreamingBufferSize {
    // Four 62.5 ms blocks retain 50 ms beyond the tested 200 ms refill gap.
    public static final int BLOCKS_PER_SECOND = 16;
    private DroneStreamingBufferSize() { }

    public static int bytesPerBuffer(AudioStream stream, int vanillaBytes) {
        if (!(stream instanceof ProceduralDroneAudioStream
                || stream instanceof CalibratedAssetAudioStream
                || stream instanceof ListenerReverbAudioStream)) return vanillaBytes;
        var format = stream.getFormat();
        int frameBytes = format.getFrameSize();
        if (frameBytes <= 0 || vanillaBytes < frameBytes || vanillaBytes % frameBytes != 0)
            throw new IllegalArgumentException("drone streaming size must contain complete PCM frames");
        if (!Float.isFinite(format.getFrameRate()) || format.getFrameRate() <= 0)
            throw new IllegalArgumentException("drone streaming format needs a positive frame rate");
        int targetBytes = Math.multiplyExact(frameBytes,
                Math.max(1, (int) (format.getFrameRate() / BLOCKS_PER_SECOND)));
        return Math.min(vanillaBytes, targetBytes);
    }
}
