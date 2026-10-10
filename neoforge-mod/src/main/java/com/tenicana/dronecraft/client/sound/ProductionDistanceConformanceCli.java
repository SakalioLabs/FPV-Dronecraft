package com.tenicana.dronecraft.client.sound;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tenicana.dronecraft.acoustics.AcousticPropagation;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.openal.EXTSourceDistanceModel;
import org.lwjgl.openal.SOFTHRTF;
import org.lwjgl.openal.EXTEfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Standalone synthetic native-renderer experiment; never opens a playback endpoint. */
public final class ProductionDistanceConformanceCli {
    private ProductionDistanceConformanceCli() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("expected a frozen protocol JSON");
        Path protocolPath = Path.of(args[0]).toAbsolutePath().normalize();
        JsonObject protocol = JsonParser.parseString(Files.readString(protocolPath)).getAsJsonObject();
        Path root = Path.of(protocol.get("workspace_root").getAsString());
        Path reportPath = root.resolve(protocol.get("output_report").getAsString());
        Path pcmPath = root.resolve(protocol.get("output_pcm").getAsString());
        if (Files.exists(reportPath) || Files.exists(pcmPath) || reportPath.equals(pcmPath)) {
            throw new IllegalArgumentException("refusing existing or aliased experiment outputs");
        }
        JsonObject bindings = protocol.getAsJsonObject("evidence_sha256");
        for (var entry : bindings.entrySet()) {
            if (!sha(Files.readAllBytes(root.resolve(entry.getKey()))).equals(entry.getValue().getAsString())) {
                throw new IllegalArgumentException("evidence changed: " + entry.getKey());
            }
        }
        int sampleRate = protocol.get("sample_rate_hz").getAsInt();
        int frames = protocol.get("render_frames").getAsInt();
        int discard = protocol.get("discard_initial_frames").getAsInt();
        int analysisFrames = protocol.get("analysis_frames").getAsInt();
        if (sampleRate != 48000 || frames != 48000 || discard != 12000 || analysisFrames != 24000) {
            throw new IllegalArgumentException("unexpected frozen sampling contract");
        }
        String started = Instant.now().toString();
        JsonObject report = new JsonObject();
        report.addProperty("schema_version", 1);
        report.addProperty("type", "synthetic_production_helper_distance_reference");
        report.addProperty("started_utc", started);
        report.addProperty("protocol_sha256", sha(Files.readAllBytes(protocolPath)));
        report.add("evidence_sha256", bindings.deepCopy());
        try (var compiled = ProductionDistanceConformanceCli.class.getResourceAsStream("ProductionDistanceConformanceCli.class")) {
            if (compiled == null) throw new IllegalStateException("compiled class bytes missing");
            report.addProperty("compiled_producer_class_sha256", sha(compiled.readAllBytes()));
        }
        try (var loaded = ProceduralDistanceOwner.class.getResourceAsStream("ProceduralDistanceOwner.class")) {
            report.addProperty("loaded_helper_class_sha256", sha(loaded.readAllBytes()));
        }
        report.addProperty("sample_rate_hz", sampleRate);
        report.addProperty("render_frames_per_case", frames);
        report.addProperty("discard_initial_frames", discard);
        report.addProperty("analysis_frames", analysisFrames);
        report.addProperty("pcm_format", "float32_little_endian_mono");
        report.addProperty("pcm_file", protocol.get("output_pcm").getAsString());
        report.addProperty("synthetic_only", true);
        report.addProperty("physical_playback_or_capture_endpoint_opened", false);
        report.addProperty("virtual_loopback_device_opened", true);
        report.addProperty("minecraft_client_started", false);
        report.addProperty("production_distance_owner_changed", true);
        report.addProperty("real_pressure_or_rpm_samples_accessed", false);
        report.addProperty("release_profile_eligible", false);
        JsonArray rows = new JsonArray();
        long device = 0;
        long context = 0;
        int source = 0;
        int buffer = 0;
        boolean closed = false;
        try {
            var baseCapabilities = ALC.createCapabilities(0L);
            if (!baseCapabilities.ALC_SOFT_loopback) throw new IllegalStateException("loopback unavailable");
            device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((CharSequence) null);
            if (device == 0L) throw new IllegalStateException("loopback open failed");
            var capabilities = ALC.createCapabilities(device);
            if (!SOFTLoopback.alcIsRenderFormatSupportedSOFT(device, sampleRate,
                    SOFTLoopback.ALC_MONO_SOFT, SOFTLoopback.ALC_FLOAT_SOFT)) {
                throw new IllegalStateException("float mono loopback format unavailable");
            }
            IntBuffer attributes = BufferUtils.createIntBuffer(9);
            attributes.put(ALC10.ALC_FREQUENCY).put(sampleRate)
                    .put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT).put(SOFTLoopback.ALC_MONO_SOFT)
                    .put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT).put(SOFTLoopback.ALC_FLOAT_SOFT);
            if (capabilities.ALC_SOFT_HRTF) attributes.put(SOFTHRTF.ALC_HRTF_SOFT).put(ALC10.ALC_FALSE);
            attributes.put(0).flip();
            context = ALC10.alcCreateContext(device, attributes);
            if (context == 0L || !ALC10.alcMakeContextCurrent(context)) {
                throw new IllegalStateException("loopback context failed");
            }
            var alCapabilities = AL.createCapabilities(capabilities);
            if (!alCapabilities.AL_EXT_source_distance_model || !alCapabilities.AL_EXT_LINEAR_DISTANCE) {
                throw new IllegalStateException("Minecraft per-source distance model capabilities unavailable");
            }
            report.addProperty("al_vendor", AL10.alGetString(AL10.AL_VENDOR));
            report.addProperty("al_renderer", AL10.alGetString(AL10.AL_RENDERER));
            report.addProperty("al_version", AL10.alGetString(AL10.AL_VERSION));
            AL10.alEnable(EXTSourceDistanceModel.AL_SOURCE_DISTANCE_MODEL);
            AL10.alDopplerFactor(0.0f);
            AL10.alListener3f(AL10.AL_POSITION, 0, 0, 0);
            AL10.alListener3f(AL10.AL_VELOCITY, 0, 0, 0);
            AL10.alListenerf(AL10.AL_GAIN, 1.0f);
            report.addProperty("per_source_distance_models_enabled", AL10.alIsEnabled(EXTSourceDistanceModel.AL_SOURCE_DISTANCE_MODEL));
            report.addProperty("global_distance_model_unchanged", AL10.alGetInteger(AL10.AL_DISTANCE_MODEL));
            report.addProperty("hrtf_enabled", capabilities.ALC_SOFT_HRTF
                    && ALC10.alcGetInteger(device, SOFTHRTF.ALC_HRTF_SOFT) == ALC10.ALC_TRUE);
            report.addProperty("listener_gain_readback", AL10.alGetListenerf(AL10.AL_GAIN));
            report.add("listener_position_readback", listenerVector(AL10.AL_POSITION));
            report.add("listener_velocity_readback", listenerVector(AL10.AL_VELOCITY));
            report.addProperty("doppler_factor_readback", AL10.alGetFloat(AL10.AL_DOPPLER_FACTOR));
            report.addProperty("efx_capability", capabilities.ALC_EXT_EFX);
            report.addProperty("effects_attached", false);
            report.addProperty("direct_filter_binding_readback_supported", false);
            FloatBuffer silence = BufferUtils.createFloatBuffer(frames);
            for (int index = 0; index < frames; index++) silence.put(index, Float.NaN);
            SOFTLoopback.alcRenderSamplesSOFT(device, silence, frames);
            double silencePeak = 0.0;
            for (int index = 0; index < frames; index++) {
                float sample = silence.get(index);
                if (!Float.isFinite(sample)) throw new IllegalStateException("silence control not rendered");
                silencePeak = Math.max(silencePeak, Math.abs(sample));
            }
            report.addProperty("no_source_control_peak", silencePeak);
            if (silencePeak > 1.0e-9) throw new IllegalStateException("no-source control failed");
            buffer = AL10.alGenBuffers();
            Files.createDirectories(pcmPath.getParent());
            try (FileChannel output = FileChannel.open(pcmPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                for (var frequency : protocol.getAsJsonArray("frequencies_hz")) {
                    double hz = frequency.getAsDouble();
                    ShortBuffer input = BufferUtils.createShortBuffer(frames);
                    ByteBuffer inputBytes = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (int index = 0; index < frames; index++) {
                        short sample = (short) Math.round(0.4 * 32767 * Math.sin(2 * Math.PI * hz * index / sampleRate));
                        input.put(sample);
                        inputBytes.putShort(sample);
                    }
                    input.flip();
                    AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, input, sampleRate);
                    String inputSha = sha(inputBytes.array());
                    for (var gainValue : protocol.getAsJsonArray("base_gains")) {
                        double baseGain = gainValue.getAsDouble();
                        for (var modeValue : protocol.getAsJsonArray("modes")) {
                            String mode = modeValue.getAsString();
                            if (!mode.equals("native_linear") && !mode.equals("manual_inverse")
                                    && !mode.equals("native_inverse") && !mode.equals("double_distance")
                                    && !mode.equals("unity_control")) {
                                throw new IllegalArgumentException("unknown experiment mode");
                            }
                            for (var distanceValue : protocol.getAsJsonArray("distances_m")) {
                                double distance = distanceValue.getAsDouble();
                                boolean linear = mode.equals("native_linear") || mode.equals("double_distance");
                                boolean manual = mode.equals("manual_inverse") || mode.equals("double_distance");
                                boolean nativeInverse = mode.equals("native_inverse");
                                double manualGain = manual ? AcousticPropagation.pressureDistanceGain(distance) : 1.0;
                                source = AL10.alGenSources();
                                AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
                                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                                AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, (float) -distance);
                                AL10.alSource3f(source, AL10.AL_VELOCITY, 0, 0, 0);
                                AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, linear || nativeInverse ? AL11.AL_LINEAR_DISTANCE : AL10.AL_NONE);
                                AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0.0f);
                                AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 48.0f);
                                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, linear || nativeInverse ? 1.0f : 0.0f);
                                AL10.alSourcef(source, AL10.AL_GAIN, (float) (baseGain * manualGain));
                                AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
                                AL10.alSourcef(source, AL10.AL_MIN_GAIN, 0.0f);
                                AL10.alSourcef(source, AL10.AL_MAX_GAIN, 1.0f);
                                AL10.alSourcef(source, AL10.AL_CONE_INNER_ANGLE, 360.0f);
                                AL10.alSourcef(source, AL10.AL_CONE_OUTER_ANGLE, 360.0f);
                                AL10.alSourcef(source, AL10.AL_CONE_OUTER_GAIN, 1.0f);
                                if (capabilities.ALC_EXT_EFX) {
                                    AL10.alSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR, 0.0f);
                                    AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                                }
                                ProceduralDroneAudioStream ownedStream = null;
                                String ownerStatus = "NOT_REQUESTED";
                                if (nativeInverse) {
                                    ownedStream = new ProceduralDroneAudioStream(new DroneAcousticRenderState(),
                                            com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer.MOTOR, 1, true);
                                    var ownership = ProceduralDistanceOwner.onStreamAttached(ownedStream, source);
                                    ownerStatus = ownership.status().name();
                                    if (ownership.status() != ProceduralDistanceOwner.Status.VERIFIED)
                                        throw new IllegalStateException("actual production helper failed: " + ownership);
                                }
                                int setupError = AL10.alGetError();
                                if (setupError != AL10.AL_NO_ERROR) throw new IllegalStateException("source setup error: " + setupError);
                                JsonObject row = new JsonObject();
                                row.addProperty("source_setup_error", setupError);
                                row.addProperty("production_helper_status", ownerStatus);
                                row.addProperty("case_index", rows.size());
                                row.addProperty("mode", mode);
                                row.addProperty("frequency_hz", hz);
                                row.addProperty("base_gain", baseGain);
                                row.addProperty("distance_m", distance);
                                row.addProperty("input_pcm16_sha256", inputSha);
                                row.addProperty("global_distance_model_readback", AL10.alGetInteger(AL10.AL_DISTANCE_MODEL));
                                row.addProperty("source_distance_model_readback", AL10.alGetSourcei(source, AL10.AL_DISTANCE_MODEL));
                                row.addProperty("source_relative_readback", AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE));
                                row.addProperty("reference_distance_readback", AL10.alGetSourcef(source, AL10.AL_REFERENCE_DISTANCE));
                                row.addProperty("max_distance_readback", AL10.alGetSourcef(source, AL10.AL_MAX_DISTANCE));
                                row.addProperty("rolloff_readback", AL10.alGetSourcef(source, AL10.AL_ROLLOFF_FACTOR));
                                row.addProperty("source_gain_readback", AL10.alGetSourcef(source, AL10.AL_GAIN));
                                row.addProperty("pitch_readback", AL10.alGetSourcef(source, AL10.AL_PITCH));
                                row.addProperty("min_gain_readback", AL10.alGetSourcef(source, AL10.AL_MIN_GAIN));
                                row.addProperty("max_gain_readback", AL10.alGetSourcef(source, AL10.AL_MAX_GAIN));
                                row.addProperty("cone_inner_readback", AL10.alGetSourcef(source, AL10.AL_CONE_INNER_ANGLE));
                                row.addProperty("cone_outer_readback", AL10.alGetSourcef(source, AL10.AL_CONE_OUTER_ANGLE));
                                row.addProperty("cone_outer_gain_readback", AL10.alGetSourcef(source, AL10.AL_CONE_OUTER_GAIN));
                                row.add("source_position_readback", sourceVector(source, AL10.AL_POSITION));
                                row.add("source_velocity_readback", sourceVector(source, AL10.AL_VELOCITY));
                                if (capabilities.ALC_EXT_EFX) {
                                    row.addProperty("air_absorption_readback", AL10.alGetSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR));
                                    row.addProperty("direct_filter_null_set_without_native_error", true);
                                }
                                FloatBuffer samples = BufferUtils.createFloatBuffer(frames);
                                for (int index = 0; index < frames; index++) samples.put(index, Float.NaN);
                                if (nativeInverse) {
                                    if (!ProceduralDistanceOwner.playAttachedSource(ownedStream, source))
                                        throw new IllegalStateException("production play gate rejected verified stream");
                                } else AL10.alSourcePlay(source);
                                SOFTLoopback.alcRenderSamplesSOFT(device, samples, frames);
                                int alError = AL10.alGetError();
                                int alcError = ALC10.alcGetError(device);
                                if (alError != AL10.AL_NO_ERROR || alcError != ALC10.ALC_NO_ERROR) {
                                    throw new IllegalStateException("AL/ALC error: " + alError + "/" + alcError);
                                }
                                ByteBuffer encoded = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
                                double energy = 0;
                                double peak = 0;
                                for (int index = 0; index < frames; index++) {
                                    float sample = samples.get(index);
                                    if (!Float.isFinite(sample)) throw new IllegalStateException("nonfinite or unwritten PCM");
                                    encoded.putFloat(sample);
                                    if (index >= discard && index < discard + analysisFrames) {
                                        energy += (double) sample * sample;
                                        peak = Math.max(peak, Math.abs(sample));
                                    }
                                }
                                row.addProperty("pcm_offset_bytes", output.position());
                                row.addProperty("pcm_bytes", encoded.position());
                                row.addProperty("pcm_sha256", sha(encoded.array()));
                                row.addProperty("steady_rms", Math.sqrt(energy / analysisFrames));
                                row.addProperty("steady_peak", peak);
                                row.addProperty("al_error", alError);
                                row.addProperty("alc_error", alcError);
                                encoded.flip();
                                while (encoded.hasRemaining()) output.write(encoded);
                                rows.add(row);
                                AL10.alSourceStop(source);
                                if (ownedStream != null) ownedStream.close();
                                AL10.alDeleteSources(source);
                                source = 0;
                            }
                        }
                    }
                }
            }
            report.add("cases", rows);
            report.addProperty("case_count", rows.size());
            report.addProperty("pcm_sha256", sha(Files.readAllBytes(pcmPath)));
            report.addProperty("pcm_bytes", Files.size(pcmPath));
            report.addProperty("al_error_final", AL10.alGetError());
            report.addProperty("alc_error_final", ALC10.alcGetError(device));
        } finally {
            if (context != 0L) {
                if (source != 0) { AL10.alSourceStop(source); AL10.alDeleteSources(source); }
                if (buffer != 0) AL10.alDeleteBuffers(buffer);
                AL.setCurrentProcess(null);
                ALC10.alcMakeContextCurrent(0L);
                ALC10.alcDestroyContext(context);
            }
            if (device != 0L) closed = ALC10.alcCloseDevice(device);
            ALC.setCapabilities(null);
        }
        if (!closed) throw new IllegalStateException("loopback device cleanup failed");
        report.addProperty("loopback_device_closed", true);
        report.addProperty("finished_utc", Instant.now().toString());
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n";
        Files.writeString(reportPath, json, StandardOpenOption.CREATE_NEW);
        System.out.println("Native distance matrix captured: " + rows.size() + " cases; " + reportPath);
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static JsonArray listenerVector(int parameter) {
        float[] vector = new float[3];
        AL10.alGetListenerfv(parameter, vector);
        JsonArray result = new JsonArray();
        for (float value : vector) result.add(value);
        return result;
    }

    private static JsonArray sourceVector(int source, int parameter) {
        float[] vector = new float[3];
        AL10.alGetSourcefv(source, parameter, vector);
        JsonArray result = new JsonArray();
        for (float value : vector) result.add(value);
        return result;
    }
}
