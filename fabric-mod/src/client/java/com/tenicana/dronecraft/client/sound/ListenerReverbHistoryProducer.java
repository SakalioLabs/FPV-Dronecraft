package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import java.util.Arrays;
import java.util.List;

/** Listener phase/history with lifecycle quiescence outside the render path. */
final class ListenerReverbHistoryProducer {
    static final int HISTORY_FRAMES = DroneAcousticRenderState.SAMPLE_RATE / 2;
    static final int TICK_FRAMES = DroneAcousticRenderState.SAMPLE_RATE / 20;
    static final int MAX_RENDER_FRAMES = 65_536;
    private final float[] history = new float[HISTORY_FRAMES];
    private final float[] shadowBuffer = new float[TICK_FRAMES];
    private final SingleOwnerRenderGate gate = new SingleOwnerRenderGate();
    private final PreparedListenerReverbMixer mixer = new PreparedListenerReverbMixer(
            StreamingVoiceBudget.MAX_DRONES, MAX_RENDER_FRAMES, 128);
    private PreparedListenerReverbMixer.PreparedSources renderingPlan;
    private boolean lastMixAccepted;
    private int historyWriteIndex;
    private volatile int historyFrameCount, synthesizerCount;
    private volatile boolean shadowActive = true;
    private volatile long ownershipGeneration, activeOwner, shadowRenderCount, wetRenderCount;

    synchronized void enterShadow() {
        if (shadowActive) return;
        gate.closeForControl();
        try { ownershipGeneration++; activeOwner = 0; shadowActive = true; clearSignalState(); }
        finally { gate.openForControl(); }
    }

    synchronized void requestWet() {
        if (!shadowActive) return;
        gate.closeForControl();
        try { shadowActive = false; }
        finally { gate.openForControl(); }
    }

    synchronized void advanceShadow(PreparedListenerReverbMixer.PreparedSources sources) {
        if (!shadowActive) return;
        gate.closeForControl();
        try {
            renderingPlan = sources;
            renderMixed(sources.originalSources(), shadowBuffer, TICK_FRAMES);
            if (lastMixAccepted) { appendHistory(shadowBuffer, TICK_FRAMES); shadowRenderCount++; }
        } finally { renderingPlan = null; gate.openForControl(); }
    }

    synchronized ActiveSession acquireWet(ListenerReverbState.Snapshot snapshot) {
        gate.closeForControl();
        try {
            shadowActive = false;
            long owner = ++ownershipGeneration;
            activeOwner = owner;
            return new ActiveSession(this, owner, historySnapshot(), snapshot);
        } finally { gate.openForControl(); }
    }

    synchronized void reset() {
        gate.closeForControl();
        try {
            ownershipGeneration++; activeOwner = 0; shadowActive = true;
            shadowRenderCount = wetRenderCount = 0; clearSignalState();
        } finally { gate.openForControl(); }
    }

    Diagnostics diagnostics() {
        return new Diagnostics(shadowActive, activeOwner != 0, historyFrameCount,
                ownershipGeneration, shadowRenderCount, wetRenderCount, synthesizerCount);
    }

    private boolean renderWet(ActiveSession session, ListenerReverbState.Snapshot snapshot,
            float[] mixed, float[] wet, int frames) {
        if (!gate.tryEnter()) { Arrays.fill(mixed, 0, frames, 0); Arrays.fill(wet, 0, frames, 0); return false; }
        try {
            if (session.closed || session.owner != activeOwner || shadowActive) {
                Arrays.fill(mixed, 0, frames, 0); Arrays.fill(wet, 0, frames, 0); return false;
            }
            renderingPlan = snapshot.preparedSources();
            renderMixed(snapshot.sources(), mixed, frames);
            if (!lastMixAccepted) { Arrays.fill(mixed, 0, frames, 0); Arrays.fill(wet, 0, frames, 0); return false; }
            var environment = snapshot.environment();
            if (environment != session.environment) {
                // Game time can stay fixed while a new probe changes the environment.
                if (!environment.equals(session.environment)) {
                    session.fdn.configure(environment.rt60Seconds(), environment.wetGain(), environment.transitionSeconds());
                }
                session.environment = environment;
            }
            session.fdn.process(mixed, wet, 0, frames);
            wetRenderCount++;
            return true;
        } finally { renderingPlan = null; gate.leave(); }
    }

    // Retain the original diagnostic hook descriptor and exact accumulation law.
    private void renderMixed(List<ListenerReverbState.Source> sources, float[] output, int frames) {
        lastMixAccepted = mixer.render(renderingPlan, output, frames);
        if (lastMixAccepted) synthesizerCount = mixer.synthesizerCount();
    }

    private synchronized void release(ActiveSession session) {
        if (session.closed) return;
        if (session.owner == activeOwner) {
            gate.closeForControl();
            try { session.closed = true; activeOwner = 0; session.fdn.reset(); }
            finally { gate.openForControl(); }
        } else {
            // Acquiring the replacement already acknowledged the old render's end.
            session.closed = true; session.fdn.reset();
        }
    }

    private void appendHistory(float[] input, int frames) {
        for (int index = 0; index < frames; index++) {
            history[historyWriteIndex] = input[index];
            historyWriteIndex = (historyWriteIndex + 1) % history.length;
            if (historyFrameCount < history.length) historyFrameCount++;
        }
    }

    private float[] historySnapshot() {
        float[] copy = new float[historyFrameCount];
        int first = Math.floorMod(historyWriteIndex - historyFrameCount, history.length);
        int firstLength = Math.min(historyFrameCount, history.length - first);
        System.arraycopy(history, first, copy, 0, firstLength);
        if (firstLength < historyFrameCount) System.arraycopy(history, 0, copy, firstLength, historyFrameCount - firstLength);
        return copy;
    }

    private void clearSignalState() {
        Arrays.fill(history, 0); historyWriteIndex = historyFrameCount = synthesizerCount = 0;
        mixer.reset();
    }

    static void addToMix(float[] output, float[] source, double amplitude, int frames) {
        for (int index = 0; index < frames; index++) output[index] += (float) (source[index] * amplitude);
    }

    static final class ActiveSession implements AutoCloseable {
        private final ListenerReverbHistoryProducer producer;
        private final long owner;
        private final float[] history;
        private final List<ListenerReverbState.Source> sourcesAtHandoff;
        private final ListenerSharedFdn fdn = new ListenerSharedFdn(DroneAcousticRenderState.SAMPLE_RATE);
        private FdnEnvironmentMapper.Controls environment;
        private final long prerollNanos;
        private volatile boolean closed;

        private ActiveSession(ListenerReverbHistoryProducer producer, long owner, float[] history,
                ListenerReverbState.Snapshot snapshot) {
            this.producer = producer; this.owner = owner; this.history = history;
            sourcesAtHandoff = snapshot.sources();
            var environment = snapshot.environment();
            fdn.configure(environment.rt60Seconds(), environment.wetGain(), 0);
            this.environment = environment;
            long started = System.nanoTime();
            if (history.length > 0) fdn.process(history, new float[history.length], 0, history.length);
            prerollNanos = System.nanoTime() - started;
        }

        float[] history() { return history.clone(); }
        List<ListenerReverbState.Source> sourcesAtHandoff() { return sourcesAtHandoff; }
        int prerollFrames() { return history.length; }
        long prerollNanos() { return prerollNanos; }

        /** Whole prepared mix/FDN path; buffers and controls must be prepared by caller. */
        boolean renderWet(ListenerReverbState.Snapshot snapshot, float[] mixed, float[] wet, int frames) {
            return producer.renderWet(this, snapshot, mixed, wet, frames);
        }

        // Compatibility diagnostic used outside audio rendering; compiles/allocates.
        boolean render(List<ListenerReverbState.Source> sources, float[] output, int frames) {
            if (!producer.gate.tryEnter()) { Arrays.fill(output, 0, frames, 0); return false; }
            try {
                if (closed || owner != producer.activeOwner || producer.shadowActive) { Arrays.fill(output, 0, frames, 0); return false; }
                producer.renderingPlan = PreparedListenerReverbMixer.prepare(sources);
                producer.renderMixed(sources, output, frames);
                if (producer.lastMixAccepted) producer.wetRenderCount++;
                return producer.lastMixAccepted;
            } finally { producer.renderingPlan = null; producer.gate.leave(); }
        }
        @Override public void close() { producer.release(this); }
    }

    record Diagnostics(boolean shadowActive, boolean wetActive, int historyFrames,
            long ownershipGeneration, long shadowRenderCount, long wetRenderCount, int synthesizerCount) { }
}
