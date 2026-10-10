package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.lwjgl.system.Callback;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.Pointer;
import org.lwjgl.system.libffi.FFICIF;
import static org.lwjgl.system.APIUtil.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.libffi.LibFFI.*;

/** Test-only ALsizei ABI adapter. No production AudioStream or decoder runs here. */
final class IntegerPcmCallback extends Callback implements CallbackI {
    private static final FFICIF CIF = apiCreateCIF(FFI_DEFAULT_ABI, ffi_type_sint32,
            ffi_type_pointer, ffi_type_pointer, ffi_type_sint32);
    record Control(int version, double amplitude) { }
    final long token;
    final long frameLimit;
    final float[] wave = new float[48];
    final short[] pcm = new short[400_000];
    final int[] requested = new int[2048], returned = new int[2048], versions = new int[2048];
    final long[] cursors = new long[2048], threads = new long[2048];
    final double[] amplitudes = new double[2048];
    volatile Control control = new Control(0, 1);
    int calls, fault;
    long cursor;

    IntegerPcmCallback(long token, long frameLimit) {
        super(CIF); this.token = token; this.frameLimit = frameLimit;
        for (int i = 0; i < wave.length; i++) wave[i] = (float) (.08 * Math.sin(2 * Math.PI * i / wave.length));
    }
    @Override public FFICIF getCallInterface() { return CIF; }
    @Override public void callback(long ret, long args) {
        long user = memGetAddress(memGetAddress(args));
        long samples = memGetAddress(memGetAddress(args + Pointer.POINTER_SIZE));
        int bytes = memGetInt(memGetAddress(args + 2L * Pointer.POINTER_SIZE));
        apiClosureRet(ret, fill(user, samples, bytes));
    }
    private int fill(long user, long samples, int bytes) {
        // All storage and the immutable control object were prepared off callback.
        // JVM/GC real-time guarantees are deliberately outside this experiment.
        if (user != token || samples == 0 || bytes <= 0 || (bytes & 1) != 0) { fault = 1; return 0; }
        if (calls == requested.length || cursor + bytes / 2 > pcm.length) { fault = 2; return 0; }
        Control current = control;
        int frames = (int) Math.min(bytes / 2, Math.max(0, frameLimit - cursor));
        requested[calls] = bytes; returned[calls] = frames * 2; versions[calls] = current.version();
        amplitudes[calls] = current.amplitude(); cursors[calls] = cursor;
        threads[calls] = Thread.currentThread().threadId(); calls++;
        for (int i = 0; i < frames; i++) {
            float value = (float) (wave[(int) ((cursor + i) % wave.length)] * current.amplitude());
            short sample = (short) Math.round(Math.max(-1f, Math.min(1f, value)) * 32767f);
            memPutShort(samples + 2L * i, sample); pcm[(int) cursor + i] = sample;
        }
        cursor += frames;
        return frames * 2;
    }
    JsonObject snapshot() {
        JsonObject result = new JsonObject(); JsonArray trace = new JsonArray();
        result.addProperty("calls", calls); result.addProperty("fault", fault);
        result.addProperty("rendered_input_frames", cursor); result.addProperty("frame_limit", frameLimit);
        result.addProperty("cif_return_type", CIF.rtype().type());
        result.addProperty("cif_return_size", CIF.rtype().size());
        result.addProperty("callback_address", address()); result.addProperty("token", token);
        for (int i = 0; i < calls; i++) {
            JsonObject row = new JsonObject(); row.addProperty("requested_bytes", requested[i]);
            row.addProperty("returned_bytes", returned[i]); row.addProperty("input_cursor", cursors[i]);
            row.addProperty("control_version", versions[i]); row.addProperty("amplitude", amplitudes[i]);
            row.addProperty("thread_id", threads[i]); trace.add(row);
        }
        result.add("trace", trace); return result;
    }
}
