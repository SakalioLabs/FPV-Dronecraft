package com.tenicana.dronecraft.client.sound;

/** Off-callback diagnostics for the managed, transformed Minecraft Channel. */
public interface CallbackChannelAccess {
    NativeCallbackBinding callbackBinding();
    String callbackDisabledReason();
    void enrollCallbackControls(DroneCallbackControlCoordinator owner);
    void rejectCallbackControls(String reason);
}
