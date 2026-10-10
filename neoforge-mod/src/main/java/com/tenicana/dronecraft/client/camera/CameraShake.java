package com.tenicana.dronecraft.client.camera;

/** Immutable vibration pose shared by camera adapters outside the mixin package. */
public record CameraShake(
        float lateralMeters,
        float verticalMeters,
        float forwardMeters,
        float pitchDegrees,
        float yawDegrees,
        float rollRadians
) {
    public static final CameraShake NONE = new CameraShake(0, 0, 0, 0, 0, 0);
}
