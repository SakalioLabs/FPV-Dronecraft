package com.tenicana.dronecraft.client.sound;

import net.minecraft.client.sounds.AudioStream;

/** Optional single-owner PCM path prepared on the sound executor before playback. */
public interface CallbackPcmStream extends AudioStream {
    int MAXIMUM_CALLBACK_FRAMES = 65_536;

    /** Claims this stream before its first queued read, allocating all render storage. */
    boolean prepareCallback();

    /** Writes native mono S16 PCM. No allocation, blocking, probes, or OpenAL calls. */
    int readCallback(long samples, int requestedBytes);

    /** Stable admission identity; inspected only by the sound executor. */
    default Object callbackControlOwner() { return null; }
    default int callbackControlLayer() { return -1; }
    default void enrollCallbackControls(Object snapshot) { }
    default boolean callbackControlsEnrolled() { return false; }
    default Object previousCallbackControl() { return null; }
}
