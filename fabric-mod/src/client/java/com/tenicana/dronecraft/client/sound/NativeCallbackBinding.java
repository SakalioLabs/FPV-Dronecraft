package com.tenicana.dronecraft.client.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTCallbackBuffer;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Sound-executor ownership of one callback buffer on an existing Channel source.
 * Native lifetime relies on the guarded OpenAL Soft 1.23.1 stop/mix barrier, not
 * merely on the Java callback gate. No OpenAL call is made by the callback.
 */
public final class NativeCallbackBinding {
    public static final String ENABLE_PROPERTY = "fpvdrone.acoustics.callbackBuffer";
    private static final int SAMPLE_RATE = 48_000;
    // An unverified native cleanup must retain its closure and stream. Leaking
    // on this exceptional path is safer than freeing executable memory in use.
    private static final List<NativeCallbackBinding> QUARANTINED = new ArrayList<>();
    private final int source;
    private final Signed32PcmCallback callback;
    private final CallbackPcmStream stream;
    private final ByteBuffer ordinarySample = BufferUtils.createByteBuffer(Short.BYTES);
    private int buffer;
    private boolean attached, closed, quarantined, callbackCleared, bufferDeleted, closureFreed;
    private String failure;

    private NativeCallbackBinding(int source, CallbackPcmStream stream) {
        this.source = source;
        this.stream = stream;
        callback = new Signed32PcmCallback(stream);
    }

    /** Null means eligible. Every check runs off callback on the AL owner. */
    public static String disabledReason(AudioFormat format) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return "callback buffer opt-in disabled";
        var capabilities = AL.getCapabilities();
        if (!capabilities.AL_SOFT_callback_buffer || capabilities.alBufferCallbackSOFT == 0
                || !AL10.alIsExtensionPresent("AL_SOFT_callback_buffer")) return "callback buffer extension unavailable";
        if (!"1.1 ALSOFT 1.23.1".equals(AL10.alGetString(AL10.AL_VERSION))) return "native callback lifetime unqualified for this OpenAL version";
        if (format.getSampleRate() != SAMPLE_RATE || format.getChannels() != 1 || format.getSampleSizeInBits() != 16
                || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()) || format.isBigEndian()) return "callback requires mono little-endian S16 at 48000 Hz";
        return null;
    }

    /** A failed binding remains owned until vanilla destroys this Channel. */
    public static NativeCallbackBinding attach(int source, CallbackPcmStream stream) {
        NativeCallbackBinding binding = new NativeCallbackBinding(source, stream);
        try {
            check("before callback attachment");
            if (!AL10.alIsSource(source)) throw new IllegalStateException("callback Channel source does not exist");
            if (AL10.alGetSourcei(source, AL10.AL_BUFFER) != 0) throw new IllegalStateException("callback Channel already has a buffer");
            check("verify empty Channel");
            binding.buffer = AL10.alGenBuffers();
            check("allocate callback buffer");
            SOFTCallbackBuffer.nalBufferCallbackSOFT(binding.buffer, AL10.AL_FORMAT_MONO16, SAMPLE_RATE,
                    binding.callback.address(), binding.callback.token());
            check("register callback buffer");
            AL10.alSourcei(source, AL10.AL_BUFFER, binding.buffer);
            check("attach callback buffer");
            binding.attached = true;
        } catch (Throwable error) {
            binding.failure = error.toString();
            binding.callback.closeAdmission();
        }
        return binding;
    }

    /**
     * Called immediately after vanilla alSourceStop inside initialized destroy.
     * Never free a closure if any native lifetime step fails. The pinned native
     * stop publishes its voice change before waiting for the current mix; the
     * next mix processes that change before accessing source buffers.
     */
    public boolean closeAfterSourceStop() {
        if (closed) return true;
        if (quarantined) return false;
        callback.closeAdmission();
        try {
            check("stop callback source");
            if (buffer != 0) {
                AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                check("detach callback buffer");
                AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, ordinarySample, SAMPLE_RATE);
                check("clear callback buffer");
                if (SOFTCallbackBuffer.alGetBufferPtrSOFT(buffer, SOFTCallbackBuffer.AL_BUFFER_CALLBACK_FUNCTION_SOFT) != 0
                        || SOFTCallbackBuffer.alGetBufferPtrSOFT(buffer, SOFTCallbackBuffer.AL_BUFFER_CALLBACK_USER_PARAM_SOFT) != 0)
                    throw new IllegalStateException("native callback pointers were not cleared");
                check("verify callback cleared");
                callbackCleared = true;
                AL10.alDeleteBuffers(buffer);
                check("delete callback buffer");
                if (AL10.alIsBuffer(buffer)) throw new IllegalStateException("callback buffer was not deleted");
                check("verify callback buffer deleted");
                bufferDeleted = true;
            }
            callback.awaitJavaQuiescence();
            callback.free();
            closureFreed = true;
            closed = true;
            return true;
        } catch (Throwable error) {
            failure = error.toString();
            // No future attempt assumes a native barrier after an AL failure.
            // This exceptional binding remains pinned even after Channel.delete.
            if (!quarantined) {
                synchronized (QUARANTINED) { QUARANTINED.add(this); }
                quarantined = true;
            }
            return false;
        }
    }

    private static void check(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) throw new IllegalStateException(operation + " OpenAL error " + error);
    }

    public int source() { return source; }
    public CallbackPcmStream stream() { return stream; }
    public int buffer() { return buffer; }
    public boolean attached() { return attached; }
    public boolean closed() { return closed; }
    public boolean quarantined() { return quarantined; }
    public boolean callbackCleared() { return callbackCleared; }
    public boolean bufferDeleted() { return bufferDeleted; }
    public boolean closureFreed() { return closureFreed; }
    public String failure() { return failure; }
    public long calls() { return callback.calls(); }
    public long requestedBytes() { return callback.requestedBytes(); }
    public long returnedBytes() { return callback.returnedBytes(); }
    public int fault() { return callback.fault(); }
    public long callbackAddress() { return callback.token(); }
    public long userPointer() { return callback.token(); }
    public int cifReturnType() { return callback.returnType(); }
    public long cifReturnSize() { return callback.returnSize(); }
}
