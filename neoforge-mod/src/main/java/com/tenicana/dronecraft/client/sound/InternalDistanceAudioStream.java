package com.tenicana.dronecraft.client.sound;

import net.minecraft.client.sounds.AudioStream;

/** Identity-gated native distance ownership, independent of the waveform backend. */
interface InternalDistanceAudioStream extends AudioStream {
    boolean internalDistanceOwnership();
    DroneAcousticRenderState distanceOwnershipMonitor();
    boolean distanceOwnerMayConfigure();
    boolean distanceOwnerVerified();
    boolean nativeDistanceOwnerConfigured(boolean verified);
}
