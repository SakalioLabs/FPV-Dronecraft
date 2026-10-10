package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.FpvDronecraftMod;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTSourceDistanceModel;

/** Assigns native distance to an opted-in internal source before its first play. */
public final class ProceduralDistanceOwner {
    private ProceduralDistanceOwner() { }

    public static boolean mayPlayAttachedStream(AudioStream stream) {
        return !(stream instanceof InternalDistanceAudioStream drone) || !drone.internalDistanceOwnership()
                || drone.distanceOwnerVerified();
    }

    public static boolean playAttachedSource(AudioStream stream, int source) {
        return runAttachedPlayback(stream, () -> AL10.alSourcePlay(source));
    }

    public static boolean runAttachedPlayback(AudioStream stream, Runnable originalPlay) {
        if (stream instanceof InternalDistanceAudioStream drone && drone.internalDistanceOwnership()) {
            synchronized (drone.distanceOwnershipMonitor()) {
                if (!drone.distanceOwnerVerified()) return false;
                originalPlay.run();
                return true;
            }
        }
        originalPlay.run();
        return true;
    }

    public static Result onStreamAttached(AudioStream stream, int source) {
        if (!(stream instanceof InternalDistanceAudioStream drone)
                || !drone.internalDistanceOwnership()) {
            return new Result(Status.BYPASS, 0, 0);
        }
        Result result;
        synchronized (drone.distanceOwnershipMonitor()) {
            if (!drone.distanceOwnerMayConfigure()) {
                return new Result(Status.FAILED, 0, 0);
            }
            result = configure(source);
            boolean accepted = drone.nativeDistanceOwnerConfigured(result.status() == Status.VERIFIED);
            if (result.status() == Status.VERIFIED && !accepted) {
                throw new IllegalStateException("distance ownership token changed inside its monitor");
            }
        }
        if (!result.mayPlay()) {
            FpvDronecraftMod.LOGGER.warn(
                    "Procedural distance ownership rejected source {}: {} (AL {}, rollback {})",
                    source, result.status(), result.alError(), result.rollbackError());
        }
        return result;
    }

    private static Result configure(int source) {
        int previousError = AL10.alGetError();
        if (previousError != AL10.AL_NO_ERROR || !AL.getCapabilities().AL_EXT_source_distance_model
                || !AL10.alIsEnabled(EXTSourceDistanceModel.AL_SOURCE_DISTANCE_MODEL)
                || !AL10.alIsSource(source)) {
            return new Result(Status.FAILED, previousError, 0);
        }
        int model = AL10.alGetSourcei(source, AL10.AL_DISTANCE_MODEL);
        float reference = AL10.alGetSourcef(source, AL10.AL_REFERENCE_DISTANCE);
        float maximum = AL10.alGetSourcef(source, AL10.AL_MAX_DISTANCE);
        float rolloff = AL10.alGetSourcef(source, AL10.AL_ROLLOFF_FACTOR);
        float gain = AL10.alGetSourcef(source, AL10.AL_GAIN);
        int global = AL10.alGetInteger(AL10.AL_DISTANCE_MODEL);
        int readError = AL10.alGetError();
        if (readError != AL10.AL_NO_ERROR) return new Result(Status.FAILED, readError, 0);
        AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL10.AL_INVERSE_DISTANCE_CLAMPED);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1.0f);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 1_000_000.0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0f);
        int error = AL10.alGetError();
        boolean matches = AL10.alGetSourcei(source, AL10.AL_DISTANCE_MODEL) == AL10.AL_INVERSE_DISTANCE_CLAMPED
                && AL10.alGetSourcef(source, AL10.AL_REFERENCE_DISTANCE) == 1.0f
                && AL10.alGetSourcef(source, AL10.AL_MAX_DISTANCE) == 1_000_000.0f
                && AL10.alGetSourcef(source, AL10.AL_ROLLOFF_FACTOR) == 1.0f
                && AL10.alGetSourcef(source, AL10.AL_GAIN) == gain
                && AL10.alGetInteger(AL10.AL_DISTANCE_MODEL) == global;
        int verificationError = AL10.alGetError();
        if (error == AL10.AL_NO_ERROR && verificationError == AL10.AL_NO_ERROR && matches) {
            return new Result(Status.VERIFIED, 0, 0);
        }
        AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, model);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, reference);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, maximum);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, rolloff);
        boolean restored = AL10.alGetSourcei(source, AL10.AL_DISTANCE_MODEL) == model
                && AL10.alGetSourcef(source, AL10.AL_REFERENCE_DISTANCE) == reference
                && AL10.alGetSourcef(source, AL10.AL_MAX_DISTANCE) == maximum
                && AL10.alGetSourcef(source, AL10.AL_ROLLOFF_FACTOR) == rolloff
                && AL10.alGetSourcef(source, AL10.AL_GAIN) == gain
                && AL10.alGetInteger(AL10.AL_DISTANCE_MODEL) == global;
        int rollbackError = AL10.alGetError();
        return new Result(restored && rollbackError == AL10.AL_NO_ERROR ? Status.FAILED : Status.ROLLBACK_FAILED,
                error != 0 ? error : verificationError, rollbackError);
    }

    public enum Status { BYPASS, VERIFIED, FAILED, ROLLBACK_FAILED }

    public record Result(Status status, int alError, int rollbackError) {
        public boolean mayPlay() { return status == Status.BYPASS || status == Status.VERIFIED; }
    }
}
