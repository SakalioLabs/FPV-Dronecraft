package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.PreparedPhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.TonalComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Single-owner listener source mixer. Publication compiles immutable controls;
 * rendering reuses fixed storage and takes no monitors. The caller must provide
 * exclusive ownership over the entire mix and subsequent FDN processing.
 */
final class PreparedListenerReverbMixer {
    private static final int SAMPLE_RATE = DroneAcousticRenderState.SAMPLE_RATE;
    private final SynthesizerPair[] pairs;
    private final int[] entityIds, selectedSlots;
    private final boolean[] active, reserved;
    private final float[] scratch;
    private final int toneCapacity;
    private int synthesizerCount;

    PreparedListenerReverbMixer(int maximumSources, int maximumFrames, int toneCapacity) {
        if (maximumSources < 1 || maximumFrames < 1) {
            throw new IllegalArgumentException("source and frame capacities must be positive");
        }
        this.toneCapacity = toneCapacity;
        pairs = new SynthesizerPair[maximumSources];
        entityIds = new int[maximumSources];
        selectedSlots = new int[maximumSources];
        active = new boolean[maximumSources];
        reserved = new boolean[maximumSources];
        scratch = new float[maximumFrames];
        for (int slot = 0; slot < maximumSources; slot++) pairs[slot] = new SynthesizerPair(toneCapacity);
    }

    /** Allocating publisher operation; preserves source entries and their order. */
    static PreparedSources prepare(List<ListenerReverbState.Source> sources) {
        List<ListenerReverbState.Source> original = List.copyOf(sources);
        var groups = new LinkedHashMap<Integer, GroupBuilder>();
        Entry[] entries = new Entry[original.size()];
        for (int index = 0; index < original.size(); index++) {
            ListenerReverbState.Source source = original.get(index);
            GroupBuilder group = groups.get(source.entityId());
            if (group == null) {
                group = new GroupBuilder(groups.size(), source.entityId());
                groups.put(source.entityId(), group);
            }
            var motor = PreparedPhaseContinuousSynthesizer.prepare(source.emission(), PhaseContinuousSynthesizer.Layer.MOTOR, SAMPLE_RATE);
            var propeller = PreparedPhaseContinuousSynthesizer.prepare(source.emission(), PhaseContinuousSynthesizer.Layer.PROPELLER, SAMPLE_RATE);
            entries[index] = new Entry(group.index, motor, propeller, source.motorAmplitude(), source.propellerAmplitude());
            group.entries.add(entries[index]);
            group.tones.addAll(source.emission().tones());
        }
        Group[] preparedGroups = new Group[groups.size()];
        for (GroupBuilder group : groups.values()) {
            // A duplicate entity is rendered once per original entry. Reserve
            // all of its keys first, including keys appearing only later.
            Entry first = group.entries.get(0);
            var union = new AcousticEmissionFrame(group.tones, AcousticBands.SILENT);
            preparedGroups[group.index] = new Group(group.entityId,
                    group.entries.size() == 1 ? first.motor : PreparedPhaseContinuousSynthesizer.prepare(union, PhaseContinuousSynthesizer.Layer.MOTOR, SAMPLE_RATE),
                    group.entries.size() == 1 ? first.propeller : PreparedPhaseContinuousSynthesizer.prepare(union, PhaseContinuousSynthesizer.Layer.PROPELLER, SAMPLE_RATE));
        }
        return new PreparedSources(original, entries, preparedGroups);
    }

    /**
     * Rejects a capacity failure before changing output, membership, or DSP.
     * Range failures also leave the destination unchanged. Valid controls and
     * buffers must be published/allocated before entering an audio callback.
     */
    boolean render(PreparedSources sources, float[] output, int frames) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(output, "output");
        if (frames < 0 || frames > scratch.length || frames > output.length || sources.groups.length > pairs.length) return false;
        if (!preflight(sources)) return false;
        if (frames == 0) return true;

        // No source state changes until every source and both layers can fit.
        for (int slot = 0; slot < pairs.length; slot++) {
            if (active[slot] && !sources.containsEntityId(entityIds[slot])) active[slot] = false;
        }
        for (int group = 0; group < sources.groups.length; group++) {
            int slot = selectedSlots[group];
            int entityId = sources.groups[group].entityId;
            if (!active[slot]) {
                pairs[slot].motor.reset(31 * entityId + 17);
                pairs[slot].propeller.reset(31 * entityId + 23);
                entityIds[slot] = entityId;
                active[slot] = true;
            }
        }
        synthesizerCount = sources.groups.length;
        Arrays.fill(output, 0, frames, 0);
        for (int index = 0; index < sources.entries.length; index++) {
            Entry entry = sources.entries[index];
            SynthesizerPair pair = pairs[selectedSlots[entry.group]];
            // The union preflight covers every entry plus all releasing keys.
            // These subset renders therefore cannot fail for capacity.
            pair.motor.render(entry.motor, scratch, 0, frames);
            ListenerReverbHistoryProducer.addToMix(output, scratch, entry.motorAmplitude, frames);
            pair.propeller.render(entry.propeller, scratch, 0, frames);
            ListenerReverbHistoryProducer.addToMix(output, scratch, entry.propellerAmplitude, frames);
        }
        for (int index = 0; index < frames; index++) {
            float value = output[index];
            output[index] = value / (1.0F + Math.abs(value));
        }
        return true;
    }

    private boolean preflight(PreparedSources sources) {
        Arrays.fill(reserved, false);
        for (int group = 0; group < sources.groups.length; group++) {
            Group control = sources.groups[group];
            int selected = -1;
            for (int slot = 0; slot < pairs.length; slot++) {
                if (active[slot] && entityIds[slot] == control.entityId) { selected = slot; break; }
            }
            if (selected >= 0) {
                if (!pairs[selected].motor.canAccept(control.motorUnion) || !pairs[selected].propeller.canAccept(control.propellerUnion)) return false;
            } else {
                // Absent entities retire at this block boundary, so replacing
                // all six IDs does not require twelve source slots.
                if (control.motorUnion.uniqueToneCount() > toneCapacity || control.propellerUnion.uniqueToneCount() > toneCapacity) return false;
                for (int slot = 0; slot < pairs.length; slot++) {
                    if (!reserved[slot] && (!active[slot] || !sources.containsEntityId(entityIds[slot]))) { selected = slot; break; }
                }
                if (selected < 0) return false;
            }
            selectedSlots[group] = selected;
            reserved[selected] = true;
        }
        return true;
    }

    void reset() {
        for (int slot = 0; slot < pairs.length; slot++) {
            pairs[slot].motor.reset(0);
            pairs[slot].propeller.reset(0);
        }
        Arrays.fill(active, false);
        Arrays.fill(reserved, false);
        synthesizerCount = 0;
    }

    int synthesizerCount() { return synthesizerCount; }

    static final class PreparedSources {
        private final List<ListenerReverbState.Source> original;
        private final Entry[] entries;
        private final Group[] groups;

        private PreparedSources(List<ListenerReverbState.Source> original, Entry[] entries, Group[] groups) {
            this.original = original;
            this.entries = entries;
            this.groups = groups;
        }

        List<ListenerReverbState.Source> originalSources() { return original; }

        private boolean containsEntityId(int entityId) {
            for (int group = 0; group < groups.length; group++) if (groups[group].entityId == entityId) return true;
            return false;
        }
    }

    private record Entry(int group, PreparedPhaseContinuousSynthesizer.PreparedEmission motor,
            PreparedPhaseContinuousSynthesizer.PreparedEmission propeller, double motorAmplitude, double propellerAmplitude) { }

    private record Group(int entityId, PreparedPhaseContinuousSynthesizer.PreparedEmission motorUnion,
            PreparedPhaseContinuousSynthesizer.PreparedEmission propellerUnion) { }

    private static final class GroupBuilder {
        private final int index, entityId;
        private final List<Entry> entries = new ArrayList<>();
        private final List<TonalComponent> tones = new ArrayList<>();

        private GroupBuilder(int index, int entityId) { this.index = index; this.entityId = entityId; }
    }

    private static final class SynthesizerPair {
        private final PreparedPhaseContinuousSynthesizer motor, propeller;

        private SynthesizerPair(int capacity) {
            motor = new PreparedPhaseContinuousSynthesizer(SAMPLE_RATE, 0, capacity);
            propeller = new PreparedPhaseContinuousSynthesizer(SAMPLE_RATE, 0, capacity);
        }
    }
}
