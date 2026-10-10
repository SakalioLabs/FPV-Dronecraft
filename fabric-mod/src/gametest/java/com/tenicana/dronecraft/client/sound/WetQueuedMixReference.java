package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonObject;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Off-callback differential reference for source phase/history and mixing.
 * This retains the old queued map facade and independently writes the source
 * loop, layer order, float accumulation, membership retirement and limiter.
 * The queued facade shares the tested production phase kernel; this is not an
 * independent oscillator or FDN implementation. No production FDN is called.
 */
public final class WetQueuedMixReference {
    public static final double MAXIMUM_FLOAT_DIFFERENCE = 2e-6;
    public static final int MAXIMUM_S16_DIFFERENCE = 1;
    public static final double MAXIMUM_GAIN_DIFFERENCE_DB = .005;

    private WetQueuedMixReference() { }

    public static Result replay(WetOracleTrace.Epoch trace) {
        if (trace.fault() != WetOracleTrace.FAULT_NONE || trace.acquisitionSnapshot() == null
                || trace.shadowFramesAtAcquisition() != trace.shadowFrames()
                || trace.shadowCallsAtAcquisition() != trace.shadowCalls()) {
            throw new IllegalArgumentException("wet reference needs one complete captured shadow prefix");
        }
        float[] actualShadow = trace.shadowMixedCopy(), actualWet = trace.wetMixedCopy();
        float[] expectedShadow = new float[actualShadow.length], expectedWet = new float[actualWet.length];
        float[] scratchOutput = new float[CallbackPcmStream.MAXIMUM_CALLBACK_FRAMES];
        var reference = new QueuedMixer(CallbackPcmStream.MAXIMUM_CALLBACK_FRAMES);
        var shadowDifference = new Difference();
        var wetDifference = new Difference();
        for (int call = 0; call < trace.shadowCalls(); call++) {
            if (!trace.shadowAccepted(call)) continue;
            int frames = trace.shadowFrames(call), cursor = trace.shadowCursor(call);
            reference.render(trace.shadowSources(call), scratchOutput, frames);
            System.arraycopy(scratchOutput, 0, expectedShadow, cursor, frames);
            shadowDifference.compare(actualShadow, expectedShadow, cursor, frames);
        }
        float[] actualPreroll = trace.prerollCopy();
        int historyFrames = Math.min(ListenerReverbHistoryProducer.HISTORY_FRAMES, actualShadow.length);
        if (actualPreroll.length != historyFrames) {
            throw new IllegalStateException("normal handoff history length differs from accepted shadow prefix");
        }
        int historyStart = actualShadow.length - historyFrames;
        float[] expectedPreroll = Arrays.copyOfRange(expectedShadow, historyStart, expectedShadow.length);
        for (int sample = 0; sample < historyFrames; sample++) {
            // The production history ring must be the exact chronological tail
            // of the separately captured actual accepted shadow mix inputs.
            if (Float.floatToRawIntBits(actualPreroll[sample])
                    != Float.floatToRawIntBits(actualShadow[historyStart + sample])) {
                throw new IllegalStateException("history ring chronology differs at frame " + sample);
            }
        }
        var historyDifference = new Difference();
        historyDifference.compare(actualPreroll, expectedPreroll, 0, historyFrames);
        for (int call = 0; call < trace.wetCalls(); call++) {
            int frames = trace.wetFrames(call), cursor = trace.wetCursor(call);
            if (trace.wetAccepted(call)) {
                reference.render(trace.wetSnapshot(call).sources(), scratchOutput, frames);
                System.arraycopy(scratchOutput, 0, expectedWet, cursor, frames);
            }
            // A rejected producer render writes zeros without advancing either
            // source phases or the FDN. Qualified native runs reject such calls.
            wetDifference.compare(actualWet, expectedWet, cursor, frames);
        }
        shadowDifference.requireWithinGate("shadow");
        historyDifference.requireWithinGate("preroll");
        wetDifference.requireWithinGate("wet mixed input");
        return new Result(expectedShadow, expectedWet, expectedPreroll,
                shadowDifference.result(), historyDifference.result(), wetDifference.result());
    }

    public record Result(float[] expectedShadowMixed, float[] expectedWetMixed,
            float[] expectedPreroll, Comparison shadow, Comparison preroll, Comparison wet) {
        public boolean sharesProductionPhaseKernel() { return true; }
        public boolean callsProductionFdn() { return false; }

        public JsonObject serialize() {
            JsonObject value = new JsonObject();
            value.addProperty("shares_production_phase_kernel", true);
            value.addProperty("calls_production_fdn", false);
            value.addProperty("maximum_float_difference_gate", MAXIMUM_FLOAT_DIFFERENCE);
            value.addProperty("maximum_s16_difference_gate", MAXIMUM_S16_DIFFERENCE);
            value.addProperty("maximum_gain_difference_db_gate", MAXIMUM_GAIN_DIFFERENCE_DB);
            value.add("shadow", serializeComparison(shadow));
            value.add("preroll", serializeComparison(preroll));
            value.add("wet", serializeComparison(wet));
            return value;
        }
    }

    public record Comparison(long frames, double maximumFloatDifference,
            int maximumS16Difference, double maximumGainDifferenceDb) { }

    private static JsonObject serializeComparison(Comparison comparison) {
        JsonObject value = new JsonObject();
        value.addProperty("frames", comparison.frames());
        value.addProperty("maximum_float_difference", comparison.maximumFloatDifference());
        value.addProperty("maximum_s16_difference", comparison.maximumS16Difference());
        value.addProperty("maximum_gain_difference_db", comparison.maximumGainDifferenceDb());
        return value;
    }

    private static final class Difference {
        private long frames;
        private double maximumFloatDifference, maximumGainDifferenceDb;
        private int maximumS16Difference;

        private void compare(float[] actual, float[] expected, int start, int count) {
            double actualEnergy = 0, expectedEnergy = 0;
            for (int frame = start; frame < start + count; frame++) {
                float a = actual[frame], e = expected[frame];
                if (!Float.isFinite(a) || !Float.isFinite(e)) {
                    throw new IllegalStateException("nonfinite source mixer sample at frame " + frame);
                }
                maximumFloatDifference = Math.max(maximumFloatDifference, Math.abs((double) a - e));
                maximumS16Difference = Math.max(maximumS16Difference, Math.abs(s16(a) - s16(e)));
                actualEnergy += (double) a * a;
                expectedEnergy += (double) e * e;
            }
            if (actualEnergy > 0 && expectedEnergy > 0) {
                maximumGainDifferenceDb = Math.max(maximumGainDifferenceDb,
                        Math.abs(10 * Math.log10(actualEnergy / expectedEnergy)));
            } else if (actualEnergy != expectedEnergy) {
                throw new IllegalStateException("source mixer silence differs from queued reference");
            }
            frames += count;
        }

        private void requireWithinGate(String label) {
            if (maximumFloatDifference > MAXIMUM_FLOAT_DIFFERENCE
                    || maximumS16Difference > MAXIMUM_S16_DIFFERENCE
                    || maximumGainDifferenceDb > MAXIMUM_GAIN_DIFFERENCE_DB) {
                throw new IllegalStateException(label + " differs from queued source reference: float="
                        + maximumFloatDifference + ", S16=" + maximumS16Difference
                        + ", gainDb=" + maximumGainDifferenceDb);
            }
        }

        private Comparison result() {
            return new Comparison(frames, maximumFloatDifference, maximumS16Difference, maximumGainDifferenceDb);
        }
    }

    private static int s16(float sample) {
        return Math.round(Math.max(-1.0F, Math.min(1.0F, sample)) * 32767.0F);
    }

    private static final class QueuedMixer {
        private final Map<Integer, Pair> pairs = new HashMap<>();
        private final HashSet<Integer> activeIds = new HashSet<>();
        private final float[] scratch;

        private QueuedMixer(int maximumFrames) { scratch = new float[maximumFrames]; }

        private void render(List<ListenerReverbState.Source> sources, float[] output, int frames) {
            if (frames == 0) return;
            Arrays.fill(output, 0, frames, 0);
            activeIds.clear();
            for (var source : sources) {
                activeIds.add(source.entityId());
                Pair pair = pairs.computeIfAbsent(source.entityId(), Pair::new);
                pair.motor.render(source.emission(), PhaseContinuousSynthesizer.Layer.MOTOR, scratch, 0, frames);
                for (int frame = 0; frame < frames; frame++) {
                    output[frame] += (float) (scratch[frame] * source.motorAmplitude());
                }
                pair.propeller.render(source.emission(), PhaseContinuousSynthesizer.Layer.PROPELLER, scratch, 0, frames);
                for (int frame = 0; frame < frames; frame++) {
                    output[frame] += (float) (scratch[frame] * source.propellerAmplitude());
                }
            }
            pairs.keySet().removeIf(id -> !activeIds.contains(id));
            for (int frame = 0; frame < frames; frame++) {
                float value = output[frame];
                output[frame] = value / (1.0F + Math.abs(value));
            }
        }
    }

    private static final class Pair {
        private final PhaseContinuousSynthesizer motor, propeller;
        private Pair(int id) {
            motor = new PhaseContinuousSynthesizer(DroneAcousticRenderState.SAMPLE_RATE, 31 * id + 17);
            propeller = new PhaseContinuousSynthesizer(DroneAcousticRenderState.SAMPLE_RATE, 31 * id + 23);
        }
    }
}
