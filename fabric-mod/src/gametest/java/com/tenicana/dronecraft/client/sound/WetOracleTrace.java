package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.List;

/**
 * Bounded test-only observation of the real listener mixer from an empty epoch
 * through shadow rendering, its normal wet handoff and every wet render.
 * Installation, lifecycle observation and all copies returned to the fixture
 * occur off the audio callback. The wet tap only stores primitive values,
 * immutable control references and copies into storage allocated by beginEpoch.
 */
public final class WetOracleTrace {
    public static final int FAULT_NONE = 0;
    public static final int FAULT_SHADOW_CAPACITY = 1;
    public static final int FAULT_WET_CAPACITY = 2;
    public static final int FAULT_RESET_AFTER_RENDER = 3;
    public static final int FAULT_MULTIPLE_ACQUISITIONS = 4;
    public static final int FAULT_SESSION_IDENTITY = 5;
    public static final int FAULT_WET_BEFORE_ATTACH = 6;
    public static final int FAULT_CONTROL_OR_RANGE = 7;
    public static final int FAULT_SHADOW_AFTER_HANDOFF = 8;

    private static final VarHandle STATE_PRODUCER = field(ListenerReverbState.class,
            "historyProducer", ListenerReverbHistoryProducer.class);
    private static final VarHandle STREAM_SESSION = field(ListenerReverbAudioStream.class,
            "session", ListenerReverbHistoryProducer.ActiveSession.class);
    private static volatile Epoch current;

    private WetOracleTrace() { }

    /** Arm before any source is spawned or shadow tick is advanced. */
    public static Epoch beginEpoch(ListenerReverbState state, int epoch,
            int maximumShadowCalls, int maximumWetCalls, int maximumWetFrames) {
        if (current != null) throw new IllegalStateException("wet oracle epoch already armed");
        if (state == null || epoch < 0 || maximumShadowCalls < 1
                || maximumWetCalls < 1 || maximumWetFrames < 1) {
            throw new IllegalArgumentException("invalid wet oracle epoch capacities");
        }
        var diagnostics = state.historyDiagnostics();
        if (!diagnostics.shadowActive() || diagnostics.wetActive()
                || diagnostics.historyFrames() != 0 || diagnostics.synthesizerCount() != 0
                || state.callbackStream() != null || !state.snapshot().sources().isEmpty()) {
            throw new IllegalStateException("wet oracle must begin at an empty real producer epoch");
        }
        Epoch trace = new Epoch(state, (ListenerReverbHistoryProducer) STATE_PRODUCER.get(state),
                epoch, maximumShadowCalls, maximumWetCalls, maximumWetFrames);
        current = trace;
        return trace;
    }

    /** Call only after the sole native render worker has joined. */
    public static void finishEpoch(Epoch trace) {
        if (current != trace) throw new IllegalStateException("wet oracle epoch identity differs");
        current = null;
        trace.finished = true;
    }

    /** Lifecycle hook. clearSignalState runs on a control owner, never a callback. */
    public static void signalReset(Object producer) {
        Epoch trace = current;
        if (trace == null || trace.producer != producer) return;
        if (trace.shadowFrames != 0 || trace.session != null || trace.wetCalls != 0) {
            trace.fail(FAULT_RESET_AFTER_RENDER);
        }
        trace.resetCount++;
    }

    /** Shadow hook. The producer monitor serializes this off-callback method. */
    public static void shadowRendered(Object producer, Object preparedSources,
            float[] mixed, boolean accepted) {
        Epoch trace = current;
        if (trace == null || trace.producer != producer) return;
        if (!(preparedSources instanceof PreparedListenerReverbMixer.PreparedSources controls)
                || mixed == null || mixed.length < ListenerReverbHistoryProducer.TICK_FRAMES) {
            trace.fail(FAULT_CONTROL_OR_RANGE); return;
        }
        if (accepted && trace.session != null) {
            trace.fail(FAULT_SHADOW_AFTER_HANDOFF); return;
        }
        int call = trace.shadowCalls;
        int frames = accepted ? ListenerReverbHistoryProducer.TICK_FRAMES : 0;
        if (call >= trace.shadowControls.length || frames > trace.shadowMixed.length - trace.shadowFrames) {
            trace.fail(FAULT_SHADOW_CAPACITY); return;
        }
        trace.shadowControls[call] = controls;
        trace.shadowAccepted[call] = accepted;
        trace.shadowCursors[call] = trace.shadowFrames;
        if (accepted) {
            System.arraycopy(mixed, 0, trace.shadowMixed, trace.shadowFrames, frames);
            trace.shadowFrames += frames;
        }
        trace.shadowCalls = call + 1;
    }

    /** Exact arguments and session returned by the normal production acquisition. */
    public static void acquired(Object producer, Object session, Object snapshot) {
        Epoch trace = current;
        if (trace == null || trace.producer != producer) return;
        if (trace.session != null) { trace.fail(FAULT_MULTIPLE_ACQUISITIONS); return; }
        if (!(session instanceof ListenerReverbHistoryProducer.ActiveSession active)
                || !(snapshot instanceof ListenerReverbState.Snapshot controls)) {
            trace.fail(FAULT_CONTROL_OR_RANGE); return;
        }
        trace.acquisitionSnapshot = controls;
        trace.preroll = active.history();
        trace.shadowCallsAtAcquisition = trace.shadowCalls;
        trace.shadowFramesAtAcquisition = trace.shadowFrames;
        trace.prerollNanos = active.prerollNanos();
        trace.session = active;
    }

    /** Owner-side attach hook, before the native buffer can invoke readCallback. */
    public static void bindManagerStream(Object stream) {
        Epoch trace = current;
        if (trace == null || !(stream instanceof ListenerReverbAudioStream wet)
                || wet.callbackControlOwner() != trace.state) return;
        Object session = STREAM_SESSION.get(wet);
        if (trace.session == null || trace.session != session
                || trace.stream != null && trace.stream != stream) {
            trace.fail(FAULT_SESSION_IDENTITY); return;
        }
        trace.stream = wet;
        trace.wetCallsAtAttach = trace.wetCalls;
    }

    /**
     * Primitive return-value hook on Producer.renderWet. It observes the exact
     * mix input actually passed to its FDN, after that render has completed.
     * No reflection, AL calls, locking, allocation or source-list traversal.
     */
    public static void wetRendered(Object producer, Object session, Object snapshot,
            float[] mixed, int frames, boolean accepted) {
        Epoch trace = current;
        if (trace == null || trace.producer != producer) return;
        if (trace.session != session) { trace.fail(FAULT_SESSION_IDENTITY); return; }
        if (trace.stream == null) { trace.fail(FAULT_WET_BEFORE_ATTACH); return; }
        if (!(snapshot instanceof ListenerReverbState.Snapshot controls) || mixed == null
                || frames < 0 || frames > mixed.length || frames > CallbackPcmStream.MAXIMUM_CALLBACK_FRAMES) {
            trace.fail(FAULT_CONTROL_OR_RANGE); return;
        }
        int call = trace.wetCalls, cursor = trace.wetFrames;
        if (call >= trace.wetControls.length || frames > trace.wetMixed.length - cursor) {
            trace.fail(FAULT_WET_CAPACITY); return;
        }
        trace.wetControls[call] = controls;
        trace.wetSizes[call] = frames;
        trace.wetAccepted[call] = accepted;
        trace.wetCursors[call] = cursor;
        System.arraycopy(mixed, 0, trace.wetMixed, cursor, frames);
        trace.wetFrames = cursor + frames;
        trace.wetCalls = call + 1;
    }

    private static VarHandle field(Class<?> owner, String name, Class<?> type) {
        try { return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findVarHandle(owner, name, type); }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }

    public static final class Epoch {
        private final ListenerReverbState state;
        private final ListenerReverbHistoryProducer producer;
        private final int epoch;
        private final PreparedListenerReverbMixer.PreparedSources[] shadowControls;
        private final boolean[] shadowAccepted;
        private final int[] shadowCursors;
        private final float[] shadowMixed;
        private final ListenerReverbState.Snapshot[] wetControls;
        private final boolean[] wetAccepted;
        private final int[] wetSizes, wetCursors;
        private final float[] wetMixed;
        private volatile ListenerReverbHistoryProducer.ActiveSession session;
        private volatile ListenerReverbAudioStream stream;
        private volatile ListenerReverbState.Snapshot acquisitionSnapshot;
        private float[] preroll;
        private long prerollNanos;
        private volatile int shadowCalls, shadowFrames, wetCalls, wetFrames, fault;
        private int resetCount, shadowCallsAtAcquisition, shadowFramesAtAcquisition, wetCallsAtAttach;
        private volatile boolean finished;

        private Epoch(ListenerReverbState state, ListenerReverbHistoryProducer producer, int epoch,
                int maximumShadowCalls, int maximumWetCalls, int maximumWetFrames) {
            this.state = state; this.producer = producer; this.epoch = epoch;
            shadowControls = new PreparedListenerReverbMixer.PreparedSources[maximumShadowCalls];
            shadowAccepted = new boolean[maximumShadowCalls]; shadowCursors = new int[maximumShadowCalls];
            shadowMixed = new float[Math.multiplyExact(maximumShadowCalls, ListenerReverbHistoryProducer.TICK_FRAMES)];
            wetControls = new ListenerReverbState.Snapshot[maximumWetCalls];
            wetAccepted = new boolean[maximumWetCalls]; wetSizes = new int[maximumWetCalls];
            wetCursors = new int[maximumWetCalls]; wetMixed = new float[maximumWetFrames];
        }

        private void fail(int value) { if (fault == FAULT_NONE) fault = value; }

        public int epoch() { return epoch; }
        public Object producerIdentity() { return producer; }
        public Object sessionIdentity() { return session; }
        public Object streamIdentity() { return stream; }
        public int fault() { return fault; }
        public int resetCount() { return resetCount; }
        public boolean finished() { return finished; }
        public int shadowCalls() { return shadowCalls; }
        public int shadowFrames() { return shadowFrames; }
        public boolean shadowAccepted(int call) { return shadowAccepted[call]; }
        public int shadowFrames(int call) { return ListenerReverbHistoryProducer.TICK_FRAMES; }
        public int shadowCursor(int call) { return shadowCursors[call]; }
        public Object shadowPlan(int call) { return shadowControls[call]; }
        public List<ListenerReverbState.Source> shadowSources(int call) { return shadowControls[call].originalSources(); }
        public float[] shadowMixedCopy() { return Arrays.copyOf(shadowMixed, shadowFrames); }
        public ListenerReverbState.Snapshot acquisitionSnapshot() { return acquisitionSnapshot; }
        public float[] prerollCopy() { return preroll == null ? new float[0] : preroll.clone(); }
        public int prerollFrames() { return preroll == null ? 0 : preroll.length; }
        public long prerollNanos() { return prerollNanos; }
        public int shadowCallsAtAcquisition() { return shadowCallsAtAcquisition; }
        public int shadowFramesAtAcquisition() { return shadowFramesAtAcquisition; }
        public int wetCallsAtAttach() { return wetCallsAtAttach; }
        public int wetCalls() { return wetCalls; }
        public int wetFrames() { return wetFrames; }
        public ListenerReverbState.Snapshot wetSnapshot(int call) { return wetControls[call]; }
        public boolean wetAccepted(int call) { return wetAccepted[call]; }
        public int wetFrames(int call) { return wetSizes[call]; }
        public int wetCursor(int call) { return wetCursors[call]; }
        public float[] wetMixedCopy() { return Arrays.copyOf(wetMixed, wetFrames); }

        /** Complete controls/cursors only; the fixture exports the F32 arrays. */
        public JsonObject serialize() {
            JsonObject value = new JsonObject();
            value.addProperty("epoch", epoch);
            value.addProperty("fault", fault);
            value.addProperty("reset_count", resetCount);
            value.addProperty("finished", finished);
            value.addProperty("producer_identity", System.identityHashCode(producer));
            value.addProperty("session_identity", System.identityHashCode(session));
            value.addProperty("stream_identity", System.identityHashCode(stream));
            value.addProperty("maximum_shadow_calls", shadowControls.length);
            value.addProperty("maximum_wet_calls", wetControls.length);
            value.addProperty("maximum_wet_frames", wetMixed.length);
            value.addProperty("shadow_call_count", shadowCalls);
            value.addProperty("shadow_frame_count", shadowFrames);
            value.addProperty("shadow_calls_at_acquisition", shadowCallsAtAcquisition);
            value.addProperty("shadow_frames_at_acquisition", shadowFramesAtAcquisition);
            value.addProperty("preroll_frames", prerollFrames());
            value.addProperty("preroll_nanos", prerollNanos);
            value.addProperty("wet_calls_at_attach", wetCallsAtAttach);
            value.addProperty("wet_call_count", wetCalls);
            value.addProperty("wet_frame_count", wetFrames);
            value.addProperty("initial_fdn_configure_transition_seconds", 0.0);
            if (acquisitionSnapshot != null) {
                value.add("acquisition_control", CallbackControlSnapshotTrace.serialize(acquisitionSnapshot));
            }
            JsonArray shadow = new JsonArray(shadowCalls);
            for (int call = 0; call < shadowCalls; call++) {
                JsonObject item = new JsonObject();
                item.addProperty("call", call);
                item.addProperty("frames", shadowFrames(call));
                item.addProperty("accepted", shadowAccepted[call]);
                item.addProperty("mixed_cursor", shadowCursors[call]);
                item.addProperty("plan_identity", System.identityHashCode(shadowControls[call]));
                item.add("sources", serializeSources(shadowSources(call)));
                shadow.add(item);
            }
            value.add("shadow_calls", shadow);
            JsonArray wet = new JsonArray(wetCalls);
            for (int call = 0; call < wetCalls; call++) {
                JsonObject item = new JsonObject();
                item.addProperty("call", call);
                item.addProperty("frames", wetSizes[call]);
                item.addProperty("accepted", wetAccepted[call]);
                item.addProperty("mixed_cursor", wetCursors[call]);
                item.add("control", CallbackControlSnapshotTrace.serialize(wetControls[call]));
                wet.add(item);
            }
            value.add("wet_calls", wet);
            return value;
        }

        /** Off-callback qualification, after all native rendering has joined. */
        public void requireComplete(Object actualWetStream, int actualWetCalls, int actualWetFrames) {
            if (fault != FAULT_NONE || session == null || stream != actualWetStream
                    || acquisitionSnapshot == null || wetCallsAtAttach != 0
                    || wetCalls != actualWetCalls || wetFrames != actualWetFrames
                    || shadowFramesAtAcquisition <= 0 || prerollFrames() <= 0
                    || shadowCallsAtAcquisition != shadowCalls || shadowFramesAtAcquisition != shadowFrames) {
                throw new IllegalStateException("incomplete wet oracle trace: fault=" + fault
                        + ", shadowCalls=" + shadowCalls + ", shadowFrames=" + shadowFrames
                        + ", wetCalls=" + wetCalls + ", wetFrames=" + wetFrames);
            }
            for (int call = 0; call < wetCalls; call++) {
                if (!wetAccepted[call]) throw new IllegalStateException("real wet render rejected at call " + call);
            }
        }
    }

    private static JsonArray serializeSources(List<ListenerReverbState.Source> sources) {
        JsonArray values = new JsonArray(sources.size());
        for (var source : sources) {
            JsonObject value = new JsonObject();
            value.addProperty("entity_id", source.entityId());
            value.addProperty("motor_amplitude", source.motorAmplitude());
            value.addProperty("propeller_amplitude", source.propellerAmplitude());
            value.add("emission", serializeEmission(source.emission()));
            values.add(value);
        }
        return values;
    }

    private static JsonObject serializeEmission(AcousticEmissionFrame frame) {
        JsonObject value = new JsonObject();
        JsonArray tones = new JsonArray(frame.tones().size());
        for (var tone : frame.tones()) {
            JsonObject item = new JsonObject();
            item.addProperty("kind", tone.kind().name());
            item.addProperty("rotor_index", tone.rotorIndex());
            item.addProperty("order", tone.order());
            item.addProperty("frequency_hz", tone.frequencyHz());
            item.addProperty("amplitude", tone.linearAmplitude());
            item.addProperty("phase_rad", tone.phaseRadians());
            tones.add(item);
        }
        value.add("tones", tones);
        value.add("broadband3bands", serializeBands(frame.broadbandEnergy()));
        return value;
    }

    private static JsonObject serializeBands(AcousticBands bands) {
        JsonObject value = new JsonObject();
        value.addProperty("low", bands.low());
        value.addProperty("mid", bands.mid());
        value.addProperty("high", bands.high());
        return value;
    }
}
