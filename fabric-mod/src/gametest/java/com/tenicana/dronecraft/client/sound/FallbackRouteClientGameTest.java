package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.tenicana.dronecraft.acoustics.*;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.sound.DroneSoundPhysics;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Software-only backend/calibration boundary; no native playback claim. */
final class FallbackRouteClientGameTest {
    static final String PROPERTY = "fpvdrone.acoustics.fallbackRouteProtocol";
    private static final String BACKEND = "fpvdrone.proceduralAudio";
    private static final int RELEASE_TICKS = 128;
    private static final class ProbeDrone extends DroneEntity {
        float rpm = 12000;
        ProbeDrone(ClientLevel level) { super(DroneEntityTypes.DRONE, level); setId(159001); }
        @Override public float getAverageMotorRpm() { return rpm; }
        @Override public float getMotorPower() { return .5f; }
        @Override public int getRotorCount() { return 4; }
        @Override public float getRotorAerodynamicLoadFactor() { return .5f; }
        @Override public float getAirspeedMetersPerSecond() { return 8; }
        @Override public float getTurbulenceIntensity() { return .2f; }
    }
    private FallbackRouteClientGameTest() { }
    static boolean requested() { return System.getProperty(PROPERTY) != null; }
    static void run(ClientGameTestContext context) {
        Path protocolPath = Path.of(System.getProperty(PROPERTY));
        Path output = Path.of(System.getProperty("fpvdrone.acoustics.fallbackRouteOutput"));
        Path pcmPath = Path.of(output.toString().replace(".json", ".pcm"));
        if (Files.exists(output) || Files.exists(pcmPath)) throw new IllegalStateException("write-once output exists");
        JsonObject protocol = JsonParser.parseString(read(protocolPath)).getAsJsonObject();
        JsonObject report = new JsonObject(); JsonArray rows = new JsonArray(), errors = new JsonArray(), wetRows = new JsonArray();
        Path wetPath = protocolPath.resolveSibling("fallback-wet-boundary-contract-d159-v1.json");
        JsonObject wetContract = JsonParser.parseString(read(wetPath)).getAsJsonObject();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        String previous = System.getProperty(BACKEND);
        Throwable failure = null;
        report.addProperty("started_utc", Instant.now().toString());
        report.addProperty("protocol_sha256", sha(readBytes(protocolPath)));
        report.addProperty("wet_contract_sha256", sha(readBytes(wetPath)));
        report.add("wet_scope", wetContract.get("scope").deepCopy());
        report.add("scope", protocol.get("scope").deepCopy());
        try {
            JsonObject loaded = new JsonObject();
            for (Class<?> type : List.of(DroneLoopSoundInstance.class, DroneAcousticRenderState.class,
                    ProceduralDroneAudioStream.class, DroneSoundPhysics.class, PhaseContinuousSynthesizer.class,
                    SoundBufferLibrary.class, LoopingAudioStream.class, JOrbisAudioStream.class,
                    FallbackRouteClientGameTest.class, ProbeDrone.class, DroneSoundManager.class,
                    Class.forName(DroneSoundManager.class.getName()+"$DroneSoundSet"),
                    Class.forName(DroneSoundManager.class.getName()+"$ListenerReverbController"), ListenerReverbState.class)) {
                String resource = "/" + type.getName().replace('.', '/') + ".class";
                try (var stream = type.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("missing loaded class " + resource);
                    loaded.addProperty(type.getName(), sha(stream.readAllBytes()));
                }
            }
            report.add("loaded_class_sha256", loaded);
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksDownload();
                world.getServer().runCommand("gamerule spawn_mobs false");
                context.runOnClient(client -> {
                    SoundBufferLibrary loader = new SoundBufferLibrary(client.getResourceManager());
                    Identifier resource = Identifier.parse(protocol.getAsJsonObject("stimulus").get("resource").getAsString());
                    try (var asset = client.getResourceManager().open(resource)) {
                        byte[] bytes = asset.readAllBytes();
                        report.addProperty("loaded_fixture_sha256", sha(bytes));
                        report.addProperty("loaded_fixture_bytes", bytes.length);
                    } catch (Exception e) { throw new IllegalStateException(e); }
                    for (JsonElement item : protocol.getAsJsonArray("cases")) {
                        JsonObject declared = item.getAsJsonObject();
                        rows.add(capture(client, loader, resource, protocol, declared, pcm, errors));
                    }
                    for (JsonElement item : wetContract.getAsJsonArray("cases")) wetRows.add(staleOwnerPublication(client, item.getAsJsonObject(), errors));
                });
            }
        } catch (Throwable e) { failure = e; report.addProperty("failure", e.toString()); }
        finally {
            if (previous == null) System.clearProperty(BACKEND); else System.setProperty(BACKEND, previous);
        }
        report.add("rows", rows); report.add("wet_rows", wetRows); report.add("gate_errors", errors);
        report.addProperty("gate_passed", failure == null && errors.isEmpty() && rows.size() == 32 && wetRows.size() == 4);
        report.addProperty("finished_utc", Instant.now().toString());
        report.addProperty("pcm_file", pcmPath.toAbsolutePath().toString());
        report.addProperty("pcm_bytes", pcm.size()); report.addProperty("pcm_sha256", sha(pcm.toByteArray()));
        try {
            Files.write(pcmPath, pcm.toByteArray(), StandardOpenOption.CREATE_NEW);
            Files.writeString(output, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report)+"\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception e) { throw new IllegalStateException(e); }
        if (failure != null || !errors.isEmpty()) throw new IllegalStateException("fallback route gate failed: " + errors.size(), failure);
    }
    private static JsonObject capture(Minecraft client, SoundBufferLibrary loader, Identifier resource,
                                      JsonObject protocol, JsonObject declared, ByteArrayOutputStream pcm, JsonArray errors) {
        boolean procedural = declared.get("constructor_procedural").getAsBoolean();
        var layer = DroneLoopSoundInstance.Layer.valueOf(declared.get("layer").getAsString());
        var drone = new ProbeDrone(client.level); var state = state(declared.get("calibration_db").getAsDouble());
        System.setProperty(BACKEND, Boolean.toString(procedural));
        var sound = new DroneLoopSoundInstance(drone, layer, state);
        JsonObject row = declared.deepCopy(); row.addProperty("initial_volume", scalar(sound, "volume")); row.addProperty("initial_pitch", scalar(sound, "pitch"));
        if (declared.get("toggle").getAsString().equals("before_open")) System.setProperty(BACKEND, Boolean.toString(!procedural));
        AudioStream first = null, second = null;
        try {
            first = sound.getAudioStream(loader, resource, true).join();
            row.add("first_stream", describe(first));
            byte[] initial = bytes(first.read(protocol.get("requested_pcm_bytes").getAsInt()));
            store(row, "first", initial, pcm);
            System.setProperty(BACKEND, Boolean.toString(!procedural));
            JsonArray ticks = new JsonArray();
            float targetVolume = procedural ? 1 : legacyVolume(drone, layer);
            float targetPitch = procedural ? 1 : legacyPitch(drone, layer);
            for (int n = 0; n < protocol.get("tick_count").getAsInt(); n++) ticks.add(tick(sound, targetVolume, targetPitch, errors, row.get("name").getAsString()));
            row.add("attack_ticks", ticks);
            second = sound.getAudioStream(loader, resource, true).join();
            row.add("second_stream", describe(second));
            byte[] fresh = bytes(second.read(protocol.get("requested_pcm_bytes").getAsInt())); store(row, "fresh", fresh, pcm);
            ByteArrayOutputStream loop = new ByteArrayOutputStream(); JsonArray batches = new JsonArray();
            for (int n = 0; n < protocol.get("loop_read_count").getAsInt(); n++) {
                byte[] batch = bytes(first.read(protocol.get("requested_pcm_bytes").getAsInt()));
                batches.add(batch.length); loop.writeBytes(batch);
                if (batch.length == 0) errors.add(row.get("name").getAsString()+":empty_loop_read");
            }
            row.add("loop_batch_bytes", batches); store(row, "loop", loop.toByteArray(), pcm);
            String expected = procedural ? ProceduralDroneAudioStream.class.getName() : LoopingAudioStream.class.getName();
            if (!first.getClass().getName().equals(expected) || !second.getClass().getName().equals(expected)) errors.add(row.get("name").getAsString()+":route");
            if (!Arrays.equals(initial, fresh)) errors.add(row.get("name").getAsString()+":fresh_pcm");
            drone.rpm = 0; JsonArray release = new JsonArray();
            float releasePitch = procedural ? 1 : legacyPitch(drone, layer);
            for (int n = 0; n < RELEASE_TICKS; n++) release.add(tick(sound, 0, releasePitch, errors, row.get("name").getAsString()+":release"));
            row.add("release_ticks", release);
            if (scalar(sound, "volume") != 0) errors.add(row.get("name").getAsString()+":release_zero");
        } catch (Exception e) { throw new IllegalStateException("case " + row.get("name"), e); }
        finally {
            try { if (first != null) first.close(); if (second != null) second.close(); }
            catch (Exception e) { throw new IllegalStateException(e); }
            sound.end();
        }
        return row;
    }
    private static JsonObject tick(DroneLoopSoundInstance sound, float target, float pitchTarget, JsonArray errors, String name) {
        float before = scalar(sound, "volume"), beforePitch = scalar(sound, "pitch");
        float expected = before + (target - before) * (target > before ? .30f : .16f);
        float expectedPitch = beforePitch + (pitchTarget - beforePitch) * .24f;
        if (target == 0 && expected < .0005f) expected = 0;
        sound.tick(); float after = scalar(sound, "volume"), afterPitch = scalar(sound, "pitch");
        JsonObject r = new JsonObject(); r.addProperty("before", before); r.addProperty("after", after);
        r.addProperty("pitch_before", beforePitch); r.addProperty("pitch_after", afterPitch);
        if (Float.floatToIntBits(after) != Float.floatToIntBits(expected)) errors.add(name+":volume");
        if (Float.floatToIntBits(afterPitch) != Float.floatToIntBits(expectedPitch)) errors.add(name+":pitch");
        return r;
    }
    private static JsonObject staleOwnerPublication(Minecraft client, JsonObject declared, JsonArray errors) {
        Object controller = null;
        try {
            var drone = new ProbeDrone(client.level); drone.setPos(0, -drone.getBbHeight()*.5, 0);
            Class<?> setType = Class.forName(DroneSoundManager.class.getName()+"$DroneSoundSet");
            var setConstructor = setType.getDeclaredConstructor(DroneEntity.class); setConstructor.setAccessible(true);
            Object set = setConstructor.newInstance(drone);
            var state = (DroneAcousticRenderState) field(set, "acousticState");
            put(state, "audioSnapshot", field(state(0), "audioSnapshot"));
            put(state, "rawEmissionFrame", state.audioSnapshot().preDopplerEmission());
            System.setProperty(BACKEND, declared.get("motor_procedural").getAsString());
            var motor = new DroneLoopSoundInstance(drone, DroneLoopSoundInstance.Layer.MOTOR, state);
            System.setProperty(BACKEND, declared.get("propeller_procedural").getAsString());
            var prop = new DroneLoopSoundInstance(drone, DroneLoopSoundInstance.Layer.PROPELLER, state);
            // Metadata resolution only; neither resource-backed drone Ogg is opened.
            motor.resolve(client.getSoundManager()); prop.resolve(client.getSoundManager());
            for (int n = 0; n < 32; n++) { motor.tick(); prop.tick(); }
            put(set, "motor", motor); put(set, "propeller", prop);
            Object oldMotor = new Object(), oldProp = new Object();
            state.registerDistanceStream(PhaseContinuousSynthesizer.Layer.MOTOR, oldMotor);
            state.registerDistanceStream(PhaseContinuousSynthesizer.Layer.PROPELLER, oldProp);
            state.configureDistanceStream(PhaseContinuousSynthesizer.Layer.MOTOR, oldMotor, true);
            state.configureDistanceStream(PhaseContinuousSynthesizer.Layer.PROPELLER, oldProp, true);
            // These token flags deliberately simulate stale owners; they are not native evidence.
            Class<?> type = Class.forName(DroneSoundManager.class.getName()+"$ListenerReverbController");
            var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); controller = ctor.newInstance();
            Method publish = type.getDeclaredMethod("publishSources", AcousticListenerFrame.class, Map.class); publish.setAccessible(true);
            JsonObject r = declared.deepCopy(); r.addProperty("synthetic_owner_state_before", state.distanceStreamsVerified());
            publish.invoke(controller, new AcousticListenerFrame(new AcousticVector(0, 0, 1), new AcousticVector(0, 0, 0)), Map.of(drone.getId(), set));
            var snapshot = ((ListenerReverbState) field(controller, "state")).snapshot();
            r.addProperty("actual_published_sources", snapshot.sources().size());
            JsonArray coefficients = new JsonArray();
            for (var source : snapshot.sources()) {
                JsonObject coefficient = new JsonObject(); coefficient.addProperty("motor", source.motorAmplitude());
                coefficient.addProperty("propeller", source.propellerAmplitude()); coefficients.add(coefficient);
            }
            r.add("published_coefficients", coefficients);
            r.addProperty("actual_private_publisher_invoked", true); r.addProperty("native_verification_performed", false);
            if (snapshot.sources().size() != declared.get("expected_published_sources").getAsInt()) errors.add("stale_owner_publication:"+declared);
            motor.end(); prop.end(); state.resetDistanceOwnership();
            return r;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        finally {
            try { if (controller != null) ((AutoCloseable) controller).close(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
    }
    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void put(Object target, String name, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static JsonObject describe(AudioStream stream) {
        var f = stream.getFormat(); JsonObject r = new JsonObject(); r.addProperty("class", stream.getClass().getName());
        r.addProperty("sample_rate", f.getSampleRate()); r.addProperty("channels", f.getChannels()); r.addProperty("sample_bits", f.getSampleSizeInBits());
        r.addProperty("big_endian", f.isBigEndian()); return r;
    }
    private static void store(JsonObject r, String prefix, byte[] data, ByteArrayOutputStream pcm) {
        r.addProperty(prefix+"_offset", pcm.size()); r.addProperty(prefix+"_bytes", data.length); r.addProperty(prefix+"_sha256", sha(data)); pcm.writeBytes(data);
    }
    private static float legacyVolume(ProbeDrone d, DroneLoopSoundInstance.Layer l) {
        return l == DroneLoopSoundInstance.Layer.MOTOR ? DroneSoundPhysics.motorVolume(d.rpm, d.getMotorPower(), d.getRotorCount())
                : DroneSoundPhysics.propellerVolume(d.rpm, d.getMotorPower(), d.getRotorAerodynamicLoadFactor(), d.getAirspeedMetersPerSecond(), d.getTurbulenceIntensity(), d.getRotorCount());
    }
    private static float legacyPitch(ProbeDrone d, DroneLoopSoundInstance.Layer l) {
        return l == DroneLoopSoundInstance.Layer.MOTOR ? DroneSoundPhysics.motorPitch(d.rpm, d.getMotorPower())
                : DroneSoundPhysics.propellerPitch(d.rpm, d.getAirspeedMetersPerSecond(), d.getTurbulenceIntensity());
    }
    private static float scalar(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.getFloat(o); }
            catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        throw new IllegalStateException("missing " + name);
    }
    private static DroneAcousticRenderState state(double db) {
        var state = new DroneAcousticRenderState(); var zero = new AcousticVector(0, 0, 0);
        var source = new AcousticSourceFrame(159001, 0, zero, zero, new AcousticVector(0, 1, 0), .127,
                List.of(new RotorAcousticState(12000, .5, .5, .0635, 3, 7, 1, 0)));
        var emission = new AcousticEmissionFrame(List.of(new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 700, .08, 0),
                new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 1000, .08, 0)), AcousticBands.SILENT);
        var snapshot = new DroneAcousticRenderState.AudioSnapshot(source, new AcousticListenerFrame(new AcousticVector(0, 0, 1), zero),
                emission, emission, 1, 343, new AcousticSourceProfile.Calibration(true, true, db, db));
        try { Field f = DroneAcousticRenderState.class.getDeclaredField("audioSnapshot"); f.setAccessible(true); f.set(state, snapshot); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return state;
    }
    private static byte[] bytes(ByteBuffer buffer) { byte[] b = new byte[buffer.remaining()]; buffer.get(b); return b; }
    private static byte[] readBytes(Path p) { try { return Files.readAllBytes(p); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String read(Path p) { try { return Files.readString(p); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
