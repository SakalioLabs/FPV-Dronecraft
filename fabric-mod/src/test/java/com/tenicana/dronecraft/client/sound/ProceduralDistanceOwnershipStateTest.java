package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralDistanceOwnershipStateTest {
    @Test void wetWaitsForBothActualCurrentStreams() {
        var state = new DroneAcousticRenderState();
        Object motor = new Object(), propeller = new Object();
        state.registerDistanceStream(Layer.MOTOR, motor);
        state.registerDistanceStream(Layer.PROPELLER, propeller);
        assertFalse(state.distanceStreamsVerified());
        state.configureDistanceStream(Layer.MOTOR, motor, true);
        assertFalse(state.distanceStreamsVerified());
        state.configureDistanceStream(Layer.PROPELLER, propeller, true);
        assertTrue(state.distanceStreamsVerified());
        state.closeDistanceStream(Layer.MOTOR, motor);
        assertFalse(state.distanceStreamsVerified());
    }

    @Test void staleCloseOrVerificationCannotCertifyReplacementStream() {
        var state = new DroneAcousticRenderState();
        Object old = new Object(), next = new Object(), propeller = new Object();
        state.registerDistanceStream(Layer.MOTOR, old);
        state.registerDistanceStream(Layer.MOTOR, next);
        state.registerDistanceStream(Layer.PROPELLER, propeller);
        state.configureDistanceStream(Layer.PROPELLER, propeller, true);
        state.configureDistanceStream(Layer.MOTOR, old, true);
        assertFalse(state.distanceStreamMayConfigure(Layer.MOTOR, old));
        assertFalse(state.configureDistanceStream(Layer.MOTOR, old, true));
        state.closeDistanceStream(Layer.MOTOR, old);
        assertFalse(state.distanceStreamsVerified());
        state.configureDistanceStream(Layer.MOTOR, next, true);
        assertTrue(state.distanceStreamsVerified());
    }

    @Test void failureSurvivesCloseAndReplacementUntilExplicitLifecycleReset() {
        var state = new DroneAcousticRenderState();
        Object failed = new Object(), next = new Object(), propeller = new Object();
        state.registerDistanceStream(Layer.MOTOR, failed);
        state.configureDistanceStream(Layer.MOTOR, failed, false);
        state.closeDistanceStream(Layer.MOTOR, failed);
        state.registerDistanceStream(Layer.MOTOR, next);
        state.registerDistanceStream(Layer.PROPELLER, propeller);
        state.configureDistanceStream(Layer.MOTOR, next, true);
        state.configureDistanceStream(Layer.PROPELLER, propeller, true);
        assertTrue(state.distanceOwnershipFailed());
        assertFalse(state.distanceStreamMayConfigure(Layer.PROPELLER, propeller));
        assertFalse(state.configureDistanceStream(Layer.PROPELLER, propeller, true));
        assertFalse(state.distanceStreamsVerified());
        state.resetDistanceOwnership();
        assertFalse(state.distanceOwnershipFailed());
        assertFalse(state.distanceStreamsVerified());
        state.configureDistanceStream(Layer.MOTOR, next, true);
        assertFalse(state.distanceStreamMayConfigure(Layer.MOTOR, next));
        assertFalse(state.distanceStreamsVerified());
    }

    @Test void ordinaryResearchStreamsNeverRegisterRequestedNativeOwnership() {
        var state = new DroneAcousticRenderState();
        var stream = new ProceduralDroneAudioStream(state, Layer.MOTOR, 4);
        assertFalse(stream.internalDistanceOwnership());
        assertFalse(state.distanceStreamsVerified());
        stream.close();
        assertFalse(state.distanceOwnershipFailed());
    }

    @Test void actualHelperRejectsStaleClosedAndResetStreamsBeforeAnyNativeCall() {
        var state = new DroneAcousticRenderState();
        var stale = new ProceduralDroneAudioStream(state, Layer.MOTOR, 1, true);
        var current = new ProceduralDroneAudioStream(state, Layer.MOTOR, 2, true);
        assertFalse(ProceduralDistanceOwner.onStreamAttached(stale, 0).mayPlay());
        assertFalse(state.distanceOwnershipFailed());
        current.close();
        assertFalse(ProceduralDistanceOwner.onStreamAttached(current, 0).mayPlay());
        var reset = new ProceduralDroneAudioStream(state, Layer.PROPELLER, 3, true);
        state.resetDistanceOwnership();
        assertFalse(ProceduralDistanceOwner.onStreamAttached(reset, 0).mayPlay());
        assertFalse(state.distanceOwnershipFailed());
    }

    @Test void resetOrPeerFailureBetweenAttachAndPlayRejectsActualPlayHelper() {
        var state = new DroneAcousticRenderState();
        var motor = new ProceduralDroneAudioStream(state, Layer.MOTOR, 1, true);
        assertTrue(motor.nativeDistanceOwnerConfigured(true));
        assertTrue(ProceduralDistanceOwner.mayPlayAttachedStream(motor));
        state.resetDistanceOwnership();
        assertFalse(ProceduralDistanceOwner.playAttachedSource(motor, 0));
        var next = new ProceduralDroneAudioStream(state, Layer.MOTOR, 2, true);
        var peer = new ProceduralDroneAudioStream(state, Layer.PROPELLER, 3, true);
        assertTrue(next.nativeDistanceOwnerConfigured(true));
        assertFalse(peer.nativeDistanceOwnerConfigured(false));
        assertFalse(ProceduralDistanceOwner.mayPlayAttachedStream(next));
        assertFalse(ProceduralDistanceOwner.playAttachedSource(next, 0));
        assertFalse(ProceduralDistanceOwner.onStreamAttached(next, 0).mayPlay());
    }
}
