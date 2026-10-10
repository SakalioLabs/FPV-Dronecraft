package com.tenicana.dronecraft.acoustics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Single-owner, fixed-capacity oscillator storage sharing the queued DSP kernel.
 * Prepare controls outside rendering. Successful valid renders, including target
 * changes, do not allocate or acquire monitors. This does not guarantee JVM or
 * native mixer deadlines, and is not attached to a Minecraft Channel yet.
 */
public final class PreparedPhaseContinuousSynthesizer {
    private final PhaseContinuousAudioKernel kernel;
    private final PhaseContinuousAudioKernel.Oscillator[] oscillators;
    private final TonalComponent.Kind[] kinds;
    private final int[] rotors, orders, selectedSlots, reservations;
    private final boolean[] active;
    private int reservationGeneration;
    private PreparedEmission lastApplied;

    public PreparedPhaseContinuousSynthesizer(int sampleRate, int noiseSeed, int capacity) {
        if (capacity < 1 || capacity > 16_384) throw new IllegalArgumentException("capacity must be in [1, 16384]");
        kernel = new PhaseContinuousAudioKernel(sampleRate, noiseSeed);
        oscillators = new PhaseContinuousAudioKernel.Oscillator[capacity];
        kinds = new TonalComponent.Kind[capacity]; rotors = new int[capacity]; orders = new int[capacity];
        selectedSlots = new int[capacity]; reservations = new int[capacity]; active = new boolean[capacity];
        for (int slot = 0; slot < capacity; slot++) oscillators[slot] = new PhaseContinuousAudioKernel.Oscillator(0, 0);
    }

    /** Immutable compilation owned by the publisher, never by a render callback. */
    public static PreparedEmission prepare(AcousticEmissionFrame frame, PhaseContinuousSynthesizer.Layer layer, int sampleRate) {
        Objects.requireNonNull(frame, "frame"); Objects.requireNonNull(layer, "layer");
        if (sampleRate < 8_000 || sampleRate > 192_000) throw new IllegalArgumentException("sampleRate must be in [8000, 192000]");
        List<TonalComponent> accepted = new ArrayList<>();
        for (TonalComponent tone : frame.tones()) {
            if (layer.accepts(tone.kind()) && tone.frequencyHz() < sampleRate * .495) accepted.add(tone);
        }
        TonalComponent[] tones = accepted.toArray(TonalComponent[]::new);
        int[] firstIndexes = new int[tones.length], uniquePerEntry = new int[tones.length];
        int uniqueCount = 0;
        for (int entry = 0; entry < tones.length; entry++) {
            TonalComponent tone = tones[entry]; int unique = -1;
            for (int index = 0; index < uniqueCount; index++) {
                TonalComponent first = tones[firstIndexes[index]];
                if (sameKey(tone, first.kind(), first.rotorIndex(), first.order())) { unique = index; break; }
            }
            if (unique < 0) { unique = uniqueCount++; firstIndexes[unique] = entry; }
            uniquePerEntry[entry] = unique;
        }
        return new PreparedEmission(tones, java.util.Arrays.copyOf(firstIndexes, uniqueCount), uniquePerEntry,
                frame.broadbandEnergy(), layer, sampleRate);
    }

    /**
     * Returns false if all targets cannot fit alongside releasing oscillators.
     * A false result leaves audio state and the destination unchanged. Controls
     * and storage/range arguments must already be valid before callback entry.
     */
    public boolean render(PreparedEmission emission, float[] output, int offset, int length) {
        Objects.requireNonNull(emission, "emission"); Objects.requireNonNull(output, "output");
        Objects.checkFromIndexSize(offset, length, output.length);
        if (emission.sampleRate != kernel.sampleRate) throw new IllegalArgumentException("prepared sample rate differs from renderer");
        if (length == 0) return true;
        if (emission != lastApplied) {
            if (!preflight(emission)) return false;
            applyTargets(emission);
            lastApplied = emission;
        }
        for (int sample = offset; sample < offset + length; sample++) output[sample] = 0;
        for (int slot = 0; slot < active.length; slot++) {
            if (active[slot]) kernel.renderOscillator(oscillators[slot], output, offset, length);
        }
        // Keep the old whole-render removal boundary, including release tails.
        for (int slot = 0; slot < active.length; slot++) {
            var oscillator = oscillators[slot];
            if (active[slot] && oscillator.targetAmplitude == 0 && Math.abs(oscillator.amplitude) < PhaseContinuousAudioKernel.SILENCE_THRESHOLD)
                active[slot] = false;
        }
        kernel.renderBroadband(emission.broadband, emission.layer, output, offset, length);
        kernel.finishOutput(output, offset, length);
        return true;
    }

    private boolean preflight(PreparedEmission emission) {
        if (emission.uniqueFirstIndexes.length > selectedSlots.length) return false;
        if (++reservationGeneration == 0) { java.util.Arrays.fill(reservations, 0); reservationGeneration = 1; }
        for (int index = 0; index < emission.uniqueFirstIndexes.length; index++) {
            TonalComponent tone = emission.tones[emission.uniqueFirstIndexes[index]]; int selected = -1;
            for (int slot = 0; slot < active.length; slot++) {
                if (active[slot] && sameKey(tone, kinds[slot], rotors[slot], orders[slot])) { selected = slot; break; }
            }
            if (selected < 0) {
                for (int slot = 0; slot < active.length; slot++) {
                    if (!active[slot] && reservations[slot] != reservationGeneration) { selected = slot; break; }
                }
                if (selected < 0) return false;
            }
            selectedSlots[index] = selected; reservations[selected] = reservationGeneration;
        }
        return true;
    }

    /** Single-owner capacity probe for a whole mix, before any DSP is advanced. */
    public boolean canAccept(PreparedEmission emission) {
        Objects.requireNonNull(emission, "emission");
        if (emission.sampleRate != kernel.sampleRate) throw new IllegalArgumentException("prepared sample rate differs from renderer");
        return emission == lastApplied || preflight(emission);
    }

    /** Reuses all storage for a new source, restoring fresh phase and noise. */
    public void reset(int noiseSeed) {
        kernel.reset(noiseSeed);
        java.util.Arrays.fill(active, false);
        java.util.Arrays.fill(kinds, null);
        java.util.Arrays.fill(reservations, 0);
        reservationGeneration = 0;
        lastApplied = null;
    }

    private void applyTargets(PreparedEmission emission) {
        for (int slot = 0; slot < active.length; slot++) if (active[slot]) oscillators[slot].targetAmplitude = 0;
        // Apply every entry in order: duplicates share storage, while first phase
        // initialization and later frequency-ramp/target changes retain old semantics.
        for (int index = 0; index < emission.tones.length; index++) {
            TonalComponent tone = emission.tones[index]; int slot = selectedSlots[emission.uniqueIndexPerEntry[index]]; var oscillator = oscillators[slot];
            if (!active[slot]) {
                kinds[slot] = tone.kind(); rotors[slot] = tone.rotorIndex(); orders[slot] = tone.order();
                oscillator.phaseSin = Math.sin(tone.phaseRadians()); oscillator.phaseCos = Math.cos(tone.phaseRadians());
                oscillator.frequencyHz = oscillator.targetFrequencyHz = tone.frequencyHz();
                oscillator.frequencyStepHz = 0; oscillator.frequencyRampSamplesRemaining = 0;
                oscillator.amplitude = oscillator.targetAmplitude = 0; active[slot] = true;
            }
            oscillator.targetAmplitude = tone.linearAmplitude();
            oscillator.setTargetFrequency(tone.frequencyHz(), kernel.frequencySmoothingSamples);
        }
    }

    private static boolean sameKey(TonalComponent tone, TonalComponent.Kind kind, int rotor, int order) {
        return tone.kind() == kind && tone.rotorIndex() == rotor && tone.order() == order;
    }

    /** Allocating diagnostic for callers outside rendering. */
    public List<PhaseContinuousSynthesizer.OscillatorDiagnostic> oscillatorDiagnostics() {
        var result = new ArrayList<PhaseContinuousSynthesizer.OscillatorDiagnostic>();
        for (int slot = 0; slot < active.length; slot++) if (active[slot]) {
            var oscillator = oscillators[slot];
            result.add(new PhaseContinuousSynthesizer.OscillatorDiagnostic(kinds[slot], rotors[slot], orders[slot], oscillator.frequencyHz,
                    oscillator.targetFrequencyHz, oscillator.frequencyRampSamplesRemaining, oscillator.amplitude, oscillator.targetAmplitude));
        }
        result.sort(Comparator.comparing(PhaseContinuousSynthesizer.OscillatorDiagnostic::kind)
                .thenComparingInt(PhaseContinuousSynthesizer.OscillatorDiagnostic::rotorIndex)
                .thenComparingInt(PhaseContinuousSynthesizer.OscillatorDiagnostic::order));
        return List.copyOf(result);
    }

    public int capacity() { return active.length; }
    public int activeOscillatorCount() { int count = 0; for (boolean value : active) if (value) count++; return count; }

    public static final class PreparedEmission {
        private final TonalComponent[] tones;
        private final int[] uniqueFirstIndexes, uniqueIndexPerEntry;
        private final AcousticBands broadband;
        private final PhaseContinuousSynthesizer.Layer layer;
        private final int sampleRate;
        private PreparedEmission(TonalComponent[] tones, int[] uniqueFirstIndexes, int[] uniqueIndexPerEntry,
                AcousticBands broadband, PhaseContinuousSynthesizer.Layer layer, int sampleRate) {
            this.tones = tones; this.uniqueFirstIndexes = uniqueFirstIndexes; this.uniqueIndexPerEntry = uniqueIndexPerEntry;
            this.broadband = broadband; this.layer = layer; this.sampleRate = sampleRate;
        }
        public int acceptedToneCount() { return tones.length; }
        public int uniqueToneCount() { return uniqueFirstIndexes.length; }
    }
}
