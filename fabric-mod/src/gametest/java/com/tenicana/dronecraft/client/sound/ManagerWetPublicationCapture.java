package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticPropagation;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

/** Off-callback provenance capture for manager-published wet source controls. */
public final class ManagerWetPublicationCapture {
    private static final int MAXIMUM_PLANS = 4_096;
    private static final IdentityHashMap<PreparedListenerReverbMixer.PreparedSources, JsonObject> PUBLICATIONS =
            new IdentityHashMap<>();
    private static long nextPublicationId;
    private static FdnEnvironmentMapper.Controls generatedEnvironment;

    private ManagerWetPublicationCapture() { }

    public static synchronized void record(
            Object controller,
            AcousticListenerFrame listener,
            Map<Integer, ?> activeSounds
    ) {
        if (!ActualManagerCallbackClientGameTest.requested()) return;
        if (controller == null || listener == null || activeSounds == null) {
            throw new NullPointerException("controller, listener and activeSounds are required");
        }
        Object rawState = field(controller, "state");
        if (!(rawState instanceof ListenerReverbState state)) {
            throw new IllegalStateException("controller state is not ListenerReverbState");
        }
        ListenerReverbState.Snapshot snapshot = state.snapshot();
        PreparedListenerReverbMixer.PreparedSources key = snapshot.preparedSources();
        if (PUBLICATIONS.containsKey(key)) {
            throw new IllegalStateException("prepared wet source plan was published more than once");
        }
        if (PUBLICATIONS.size() >= MAXIMUM_PLANS) {
            throw new IllegalStateException("wet publication capture exceeded " + MAXIMUM_PLANS + " plans");
        }

        JsonObject publication = new JsonObject();
        publication.addProperty("publication_id", nextPublicationId++);
        publication.add("listener_position", vector(listener.positionMeters()));
        publication.add("listener_velocity", vector(listener.velocityMetersPerSecond()));
        JsonArray sources = new JsonArray();
        for (ListenerReverbState.Source source : snapshot.sources()) {
            Object sounds = activeSounds.get(source.entityId());
            if (sounds == null) {
                throw new IllegalStateException("no selected DroneSoundSet for wet entity " + source.entityId());
            }
            Object drone = field(sounds, "drone");
            DroneAcousticRenderState acousticState = (DroneAcousticRenderState) field(sounds, "acousticState");
            DroneLoopSoundInstance motor = (DroneLoopSoundInstance) field(sounds, "motor");
            DroneLoopSoundInstance propeller = (DroneLoopSoundInstance) field(sounds, "propeller");
            if (motor == null || propeller == null) {
                throw new IllegalStateException("wet source has no active motor/propeller layer: " + source.entityId());
            }

            double motorEnvelope = motor.getVolume();
            double propellerEnvelope = propeller.getVolume();
            double motorCalibration = acousticState.playbackCalibration().motorPlaybackAmplitude();
            double propellerCalibration = acousticState.playbackCalibration().propellerPlaybackAmplitude();
            AcousticVector sourcePosition = new AcousticVector(
                    ((net.minecraft.world.entity.Entity) drone).getX(),
                    ((net.minecraft.world.entity.Entity) drone).getY()
                            + ((net.minecraft.world.entity.Entity) drone).getBbHeight() * 0.5,
                    ((net.minecraft.world.entity.Entity) drone).getZ()
            );
            double distance = sourcePosition.subtract(listener.positionMeters()).length();
            double pressureDistanceGain = AcousticPropagation.pressureDistanceGain(distance);
            double expectedMotorAmplitude = pressureDistanceGain * motorEnvelope * motorCalibration;
            double expectedPropellerAmplitude = pressureDistanceGain * propellerEnvelope * propellerCalibration;
            requireFormula("motor", source.entityId(), source.motorAmplitude(), expectedMotorAmplitude);
            requireFormula("propeller", source.entityId(), source.propellerAmplitude(), expectedPropellerAmplitude);

            JsonObject item = new JsonObject();
            item.addProperty("entity_id", source.entityId());
            item.addProperty("E_motor", motorEnvelope);
            item.addProperty("E_prop", propellerEnvelope);
            item.addProperty("C_motor", motorCalibration);
            item.addProperty("C_prop", propellerCalibration);
            item.addProperty("distance_m", distance);
            item.addProperty("pressure_distance_gain", pressureDistanceGain);
            item.addProperty("actual_motor_amplitude", source.motorAmplitude());
            item.addProperty("actual_propeller_amplitude", source.propellerAmplitude());
            item.add("source_position", vector(sourcePosition));
            item.add("listener_position", vector(listener.positionMeters()));
            item.addProperty("emission_identity_match",
                    source.emission() == acousticState.unoccludedEmissionFrame());
            sources.add(item);
        }
        publication.add("sources", sources);
        PUBLICATIONS.put(key, publication);
    }

    public static synchronized JsonObject sourcePublication(ListenerReverbState.Snapshot snapshot) {
        if (snapshot == null) throw new NullPointerException("snapshot");
        JsonObject publication = PUBLICATIONS.get(snapshot.preparedSources());
        if (publication == null) {
            throw new IllegalStateException("no wet publication metadata for prepared source plan");
        }
        return publication.deepCopy();
    }

    public static synchronized JsonArray snapshot() {
        JsonArray result = new JsonArray();
        var ordered = new ArrayList<>(PUBLICATIONS.values());
        ordered.sort(Comparator.comparingLong(publication -> publication.get("publication_id").getAsLong()));
        for (JsonObject publication : ordered) result.add(publication.deepCopy());
        return result;
    }

    /** Publisher-only stimulus; never called by a native PCM callback. */
    public static synchronized FdnEnvironmentMapper.Controls environmentControl(
            FdnEnvironmentMapper.Controls actual
    ) {
        if (!ActualManagerCallbackClientGameTest.requested()) return actual;
        var protocol = MainContextOutputCapture.protocol();
        if (!protocol.has("test_only_generated_wet_environment")
                || !protocol.get("test_only_generated_wet_environment").getAsBoolean()) return actual;
        if (generatedEnvironment == null) {
            var declared = protocol.getAsJsonObject("generated_wet_environment");
            var rt60 = declared.getAsJsonObject("rt60_seconds");
            generatedEnvironment = new FdnEnvironmentMapper.Controls(
                    declared.get("snapshot_generation").getAsLong(),
                    new AcousticBands(rt60.get("low").getAsDouble(), rt60.get("mid").getAsDouble(),
                            rt60.get("high").getAsDouble()),
                    declared.get("wet_gain").getAsDouble(),
                    declared.get("transition_seconds").getAsDouble());
        }
        return generatedEnvironment;
    }

    /** Install the declared environment before the first managed wet render. */
    public static synchronized void applyEnvironmentStimulus(Object controller) {
        if (!ActualManagerCallbackClientGameTest.requested()) return;
        var state = (ListenerReverbState) field(controller, "state");
        var actual = state.snapshot().environment();
        var generated = environmentControl(actual);
        if (generated != actual) state.publishEnvironment(generated);
    }

    public static synchronized void reset() {
        PUBLICATIONS.clear();
        nextPublicationId = 0L;
        generatedEnvironment = null;
    }

    private static void requireFormula(String layer, int entityId, double actual, double expected) {
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) {
            throw new IllegalStateException(layer + " source amplitude is non-finite for entity " + entityId);
        }
        double difference = Math.abs(actual - expected);
        double scale = Math.max(Math.abs(actual), Math.abs(expected));
        if (difference > 1.0e-15 && difference / scale > 1.0e-12) {
            throw new IllegalStateException(layer + " source amplitude formula mismatch for entity "
                    + entityId + ": actual=" + actual + ", expected=" + expected);
        }
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("could not read " + name + " from " + target.getClass().getName(), error);
        }
    }

    private static JsonArray vector(AcousticVector vector) {
        JsonArray result = new JsonArray(3);
        result.add(vector.x());
        result.add(vector.y());
        result.add(vector.z());
        return result;
    }
}
