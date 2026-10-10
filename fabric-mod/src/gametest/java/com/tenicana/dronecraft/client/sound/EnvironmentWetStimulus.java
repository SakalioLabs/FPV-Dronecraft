package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonObject;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

/** Immutable, declared environment changes installed only by the client-side test publisher. */
public final class EnvironmentWetStimulus {
    public enum Phase { A, B, C }

    private static final FdnEnvironmentMapper.Controls A = new FdnEnvironmentMapper.Controls(
            172004, new AcousticBands(.8, .6, .4), .35, .15);
    private static final FdnEnvironmentMapper.Controls B = new FdnEnvironmentMapper.Controls(
            172004, new AcousticBands(.25, .2, .15), .1, .05);
    private static final FdnEnvironmentMapper.Controls C = new FdnEnvironmentMapper.Controls(
            172005, new AcousticBands(1.2, 1.0, .8), .4, .25);
    private static volatile FdnEnvironmentMapper.Controls selected = A;

    private EnvironmentWetStimulus() { }

    public static boolean requested() {
        if (!MainContextOutputCapture.requested()) return false;
        var protocol = MainContextOutputCapture.protocol();
        return protocol.has("dynamic_environment_experiment")
                && protocol.get("dynamic_environment_experiment").getAsBoolean();
    }

    /** Invoked by the fixture on the client before the next real manager publication. */
    public static void setPhase(Phase phase) {
        if (phase == null) throw new NullPointerException("phase");
        selected = switch (phase) { case A -> A; case B -> B; case C -> C; };
    }

    public static FdnEnvironmentMapper.Controls selected() { return selected; }

    /** This is an off-callback publisher hook; it never changes the source plan. */
    public static FdnEnvironmentMapper.Controls replace(FdnEnvironmentMapper.Controls actual) {
        return requested() ? selected : actual;
    }

    public static void validateDeclaration(JsonObject protocol) {
        if (!requested()) throw new IllegalStateException("D175 dynamic environment declaration missing");
        var schedule = protocol.getAsJsonObject("dynamic_wet_environments");
        if (schedule == null || schedule.size() != 3) {
            throw new IllegalStateException("exact A/B/C environment schedule required");
        }
        validate(schedule.getAsJsonObject("A"), A, "A");
        validate(schedule.getAsJsonObject("B"), B, "B");
        validate(schedule.getAsJsonObject("C"), C, "C");
        validate(protocol.getAsJsonObject("generated_wet_environment"), A, "initial A");
    }

    private static void validate(JsonObject declared, FdnEnvironmentMapper.Controls expected, String phase) {
        if (declared == null) throw new IllegalStateException("environment declaration absent: " + phase);
        var bands = declared.getAsJsonObject("rt60_seconds");
        if (bands == null || declared.get("snapshot_generation").getAsLong() != expected.snapshotGeneration()
                || bands.get("low").getAsDouble() != expected.rt60Seconds().low()
                || bands.get("mid").getAsDouble() != expected.rt60Seconds().mid()
                || bands.get("high").getAsDouble() != expected.rt60Seconds().high()
                || declared.get("wet_gain").getAsDouble() != expected.wetGain()
                || declared.get("transition_seconds").getAsDouble() != expected.transitionSeconds()) {
            throw new IllegalStateException("environment differs from fixed fixture contract: " + phase);
        }
    }
}
