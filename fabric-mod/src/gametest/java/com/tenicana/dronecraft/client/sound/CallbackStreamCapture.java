package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.system.MemoryUtil.memGetShort;

/** Gametest-only observation of production PCM stream callback activity. */
public final class CallbackStreamCapture {
    private static volatile Capture[] registrations = new Capture[0];

    private CallbackStreamCapture() { }

    public static Capture register(Object stream, int maxFrames, int maxCalls) {
        if (stream == null) throw new NullPointerException("stream");
        if (maxFrames < 0 || maxCalls < 0) throw new IllegalArgumentException("capture limits must be nonnegative");
        Capture capture = new Capture(stream, maxFrames, maxCalls);
        Capture[] current = registrations;
        Capture[] next = new Capture[current.length + 1];
        System.arraycopy(current, 0, next, 0, current.length);
        next[current.length] = capture;
        registrations = next;
        return capture;
    }

    public static void clearRegistrations() {
        registrations = new Capture[0];
    }

    /** Attach admission runs on the sound executor, before any native callback. */
    public static void registerManagerStream(Object stream) {
        if (!ActualManagerCallbackClientGameTest.requested() || !(stream instanceof CallbackPcmStream)) return;
        if (find(stream) != null) throw new IllegalStateException("manager callback stream attached twice");
        MainContextOutputCapture.registerCallbackStream(stream);
        register(stream, ActualManagerCallbackClientGameTest.MAX_CAPTURE_FRAMES,
                ActualManagerCallbackClientGameTest.MAX_CAPTURE_CALLS);
    }

    public static Capture find(Object instance) {
        Capture[] current = registrations;
        for (Capture capture : current) if (capture.stream == instance) return capture;
        return null;
    }

    /** The getter's actual immutable return value; no copying or serialization. */
    public static void control(Object instance, Object snapshot) {
        Capture capture = find(instance);
        if (capture == null) return;
        int call = capture.calls;
        if (call >= capture.maxCalls) { capture.fault = 3; return; }
        capture.controlSnapshots[call] = snapshot;
    }

    /** Actual shared holder return value, retained in preallocated storage. */
    public static void controlFrame(Object instance, Object frame) {
        Capture capture = find(instance);
        if (capture == null) return;
        int call = capture.calls;
        if (call >= capture.maxCalls) { capture.fault = 4; return; }
        capture.controlFrames[call] = frame;
    }

    /** Called at the RETURN of production {@code readCallback}. */
    public static void record(Object instance, long samples, int requested, int returned) {
        Capture[] current = registrations;
        for (Capture capture : current) {
            if (capture.stream == instance) {
                capture.record(samples, requested, returned);
                capture.active = false;
                return;
            }
        }
    }

    /** Observation only: never pause or coordinate work inside the callback. */
    public static void begin(Object instance) {
        Capture[] current=registrations;
        for(Capture capture:current)if(capture.stream==instance){capture.active=true;return;}
    }

    /** Called at HEAD of queued {@code read}; no-op for unregistered streams. */
    public static void countReads(Object instance) {
        Capture[] current = registrations;
        for (Capture capture : current) {
            if (capture.stream == instance) {
                capture.queuedReads++;
                return;
            }
        }
    }

    public static final class Capture {
        private final Object stream;
        public final int maxFrames;
        public final int maxCalls;
        public final short[] pcm;
        public final int[] requestedBytes;
        public final int[] returnedBytes;
        public final int[] controlVersions;
        public final long[] cursors;
        public final long[] threadIds;
        public final Object[] controlSnapshots;
        public final Object[] controlFrames;
        public volatile int controlVersion;
        public volatile boolean active;
        public volatile int fault;
        public volatile int calls;
        public volatile int queuedReads;
        public volatile long cursor;

        private Capture(Object stream, int maxFrames, int maxCalls) {
            this.stream = stream;
            this.maxFrames = maxFrames;
            this.maxCalls = maxCalls;
            pcm = new short[maxFrames];
            requestedBytes = new int[maxCalls];
            returnedBytes = new int[maxCalls];
            controlVersions = new int[maxCalls];
            cursors = new long[maxCalls];
            threadIds = new long[maxCalls];
            controlSnapshots = new Object[maxCalls];
            controlFrames = new Object[maxCalls];
        }

        private void record(long samples, int requested, int returned) {
            int call = calls;
            if (call >= maxCalls || cursor > maxFrames || returned < 0
                    || returned > requested || (returned & 1) != 0
                    || requested < 0 || (requested & 1) != 0
                    || (returned > 0 && samples == 0)) {
                fault = 1;
                return;
            }
            int frames = returned / Short.BYTES;
            long start = cursor;
            if (start + frames > maxFrames) {
                fault = 2;
                return;
            }
            requestedBytes[call] = requested;
            returnedBytes[call] = returned;
            controlVersions[call] = controlVersion;
            cursors[call] = start;
            threadIds[call] = Thread.currentThread().threadId();
            for (int i = 0; i < frames; i++) pcm[(int) start + i] = memGetShort(samples + (long) i * Short.BYTES);
            cursor = start + frames;
            calls = call + 1;
        }

        /** Returns a detached little-endian copy, intended for off-callback use. */
        public byte[] pcmBytes() {
            int frames = (int) Math.min(cursor, maxFrames);
            ByteBuffer bytes = ByteBuffer.allocate(frames * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frames; i++) bytes.putShort(pcm[i]);
            return bytes.array();
        }

        /** Builds a detached trace object; call outside the native callback. */
        public JsonObject snapshot() {
            JsonObject result = new JsonObject();
            JsonArray trace = new JsonArray();
            int count = Math.min(calls, maxCalls);
            result.addProperty("fault", fault);
            result.addProperty("calls", count);
            result.addProperty("queued_reads", queuedReads);
            result.addProperty("rendered_frames", cursor);
            result.addProperty("max_frames", maxFrames);
            result.addProperty("max_calls", maxCalls);
            for (int i = 0; i < count; i++) {
                JsonObject row = new JsonObject();
                row.addProperty("requested_bytes", requestedBytes[i]);
                row.addProperty("returned_bytes", returnedBytes[i]);
                row.addProperty("control_version", controlVersions[i]);
                row.addProperty("input_cursor", cursors[i]);
                row.addProperty("thread_id", threadIds[i]);
                trace.add(row);
            }
            result.add("trace", trace);
            return result;
        }
    }
}
