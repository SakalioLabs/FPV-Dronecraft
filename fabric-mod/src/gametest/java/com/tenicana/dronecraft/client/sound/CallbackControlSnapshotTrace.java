package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.TonalComponent;

/** Off-callback JSON serialization for immutable dry and wet control snapshots. */
final class CallbackControlSnapshotTrace {
    private CallbackControlSnapshotTrace() { }

    static JsonObject serialize(Object snapshot) {
        if (snapshot instanceof DroneAcousticRenderState.AudioSnapshot dry) {
            return serializeDry(dry);
        }
        if (snapshot instanceof ListenerReverbState.Snapshot wet) {
            return serializeWet(wet);
        }
        throw new IllegalArgumentException("unsupported callback control snapshot: "
                + (snapshot == null ? "null" : snapshot.getClass().getName()));
    }

    private static JsonObject serializeDry(DroneAcousticRenderState.AudioSnapshot snapshot) {
        var source = snapshot.source();
        var listener = snapshot.listener();
        var calibration = snapshot.calibration();
        JsonObject result = new JsonObject();
        result.addProperty("kind", "dry");
        result.addProperty("source_entity_id", source.entityId());
        result.add("source_position", vector(source.positionMeters()));
        result.add("source_velocity", vector(source.velocityMetersPerSecond()));
        result.add("listener_position", vector(listener.positionMeters()));
        result.add("listener_velocity", vector(listener.velocityMetersPerSecond()));
        result.addProperty("doppler_ratio", snapshot.dopplerFrequencyRatio());
        result.addProperty("sound_speed", snapshot.soundSpeedMetersPerSecond());
        result.addProperty("calibration_motor_amplitude", calibration.motorPlaybackAmplitude());
        result.addProperty("calibration_propeller_amplitude", calibration.propellerPlaybackAmplitude());
        result.addProperty("calibration_motor_gain_db", calibration.motorPlaybackGainDb());
        result.addProperty("calibration_propeller_gain_db", calibration.propellerPlaybackGainDb());
        result.add("emission", emission(snapshot.emission()));
        result.add("pre_doppler_emission", emission(snapshot.preDopplerEmission()));
        return result;
    }

    private static JsonObject serializeWet(ListenerReverbState.Snapshot snapshot) {
        var controls = snapshot.environment();
        JsonObject result = new JsonObject();
        result.addProperty("kind", "wet");

        JsonObject environment = new JsonObject();
        environment.addProperty("generation", controls.snapshotGeneration());
        environment.add("rt60", bands(controls.rt60Seconds()));
        environment.addProperty("wet_gain", controls.wetGain());
        environment.addProperty("transition_seconds", controls.transitionSeconds());
        result.add("environment", environment);

        JsonArray sources = new JsonArray();
        for (ListenerReverbState.Source source : snapshot.sources()) {
            JsonObject item = new JsonObject();
            item.addProperty("entity_id", source.entityId());
            item.addProperty("motor_amplitude", source.motorAmplitude());
            item.addProperty("propeller_amplitude", source.propellerAmplitude());
            item.add("emission", emission(source.emission()));
            sources.add(item);
        }
        result.add("sources", sources);
        return result;
    }

    private static JsonObject emission(AcousticEmissionFrame frame) {
        JsonObject result = new JsonObject();
        JsonArray tones = new JsonArray();
        for (TonalComponent tone : frame.tones()) {
            JsonObject item = new JsonObject();
            item.addProperty("kind", tone.kind().name());
            item.addProperty("rotor_index", tone.rotorIndex());
            item.addProperty("order", tone.order());
            item.addProperty("frequency_hz", tone.frequencyHz());
            item.addProperty("amplitude", tone.linearAmplitude());
            item.addProperty("phase_rad", tone.phaseRadians());
            tones.add(item);
        }
        result.add("tones", tones);
        result.add("broadband3bands", bands(frame.broadbandEnergy()));
        return result;
    }

    private static JsonObject bands(com.tenicana.dronecraft.acoustics.AcousticBands bands) {
        JsonObject result = new JsonObject();
        result.addProperty("low", bands.low());
        result.addProperty("mid", bands.mid());
        result.addProperty("high", bands.high());
        return result;
    }

    private static JsonArray vector(AcousticVector vector) {
        JsonArray result = new JsonArray(3);
        result.add(vector.x());
        result.add(vector.y());
        result.add(vector.z());
        return result;
    }
}
