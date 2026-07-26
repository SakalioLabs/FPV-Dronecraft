package com.tenicana.dronecraft.client.sound;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTLoopback;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

/**
 * In-process OpenAL Soft loopback cost for the maximum production source set.
 *
 * <p>The benchmark uses six drones x two layers, one shared production EFX
 * effect/slot, and per-source production send filters. It opens no physical
 * playback or capture endpoint.
 */
public final class OpenAlEfxRuntimeBenchmark {
	private static final int SAMPLE_RATE_HZ = 48_000;
	private static final int SOURCE_COUNT = 12;
	private static final int BLOCK_FRAMES = 4_096;
	private static final int WARMUP_ITERATIONS = 60;
	private static final int MEASURED_ITERATIONS = 240;
	private static final int CHUNK_BYTES = 96_000;
	private static final FdnEnvironmentMapper.Controls CLOSED_ROOM =
			new FdnEnvironmentMapper.Controls(
					75L,
					new AcousticBands(
							6.547525842,
							3.919804879,
							2.442873467
					),
					0.388323554,
					0.2
			);

	private OpenAlEfxRuntimeBenchmark() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 3) {
			throw new IllegalArgumentException(
					"usage: OpenAlEfxRuntimeBenchmark "
							+ "<D094-json> <D094-pcm> <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		JsonObject trace = JsonParser.parseString(
				Files.readString(Path.of(arguments[0]))
		).getAsJsonObject();
		byte[] sidecar = Files.readAllBytes(Path.of(arguments[1]));
		String sidecarSha256 = sha256(sidecar);
		if (!sidecarSha256.equals(
				trace.get("pcm_sha256").getAsString()
		)) {
			throw new IllegalArgumentException(
					"D094 sidecar SHA-256 mismatch"
			);
		}
		Chunk propellerChunk = firstChunk(trace, "propeller", sidecar);
		Chunk motorChunk = firstChunk(trace, "motor", sidecar);
		byte[] propeller = Arrays.copyOfRange(
				sidecar,
				propellerChunk.offset(),
				propellerChunk.offset() + propellerChunk.bytes()
		);
		byte[] motor = Arrays.copyOfRange(
				sidecar,
				motorChunk.offset(),
				motorChunk.offset() + motorChunk.bytes()
		);
		CaseResult dry = runCase(motor, propeller, false);
		CaseResult efx = runCase(motor, propeller, true);
		Path output = Path.of(arguments[2]).toAbsolutePath().normalize();
		Files.createDirectories(output.getParent());
		Files.writeString(
				output,
				toJson(dry, efx, sidecarSha256),
				StandardCharsets.UTF_8
		);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-openal-efx-runtime-benchmark\","
						+ "\"output\":\"%s\"}%n",
				output.toString().replace("\\", "\\\\")
		);
	}

	private static Chunk firstChunk(
			JsonObject trace,
			String layer,
			byte[] sidecar
	) throws Exception {
		JsonArray chunks = trace.getAsJsonArray("chunks");
		for (int index = 0; index < chunks.size(); index++) {
			JsonObject chunk = chunks.get(index).getAsJsonObject();
			if (!layer.equals(chunk.get("layer").getAsString())) {
				continue;
			}
			int offset = chunk.get("pcm_offset").getAsInt();
			int bytes = chunk.get("pcm_bytes").getAsInt();
			if (bytes != CHUNK_BYTES || offset < 0
					|| offset + bytes > sidecar.length) {
				throw new IllegalArgumentException(
						"invalid D094 " + layer + " chunk range"
				);
			}
			byte[] payload = Arrays.copyOfRange(
					sidecar,
					offset,
					offset + bytes
			);
			if (!sha256(payload).equals(
					chunk.get("pcm_sha256").getAsString()
			)) {
				throw new IllegalArgumentException(
						"D094 " + layer + " chunk SHA-256 mismatch"
				);
			}
			return new Chunk(offset, bytes);
		}
		throw new IllegalArgumentException(
				"D094 " + layer + " chunk is missing"
		);
	}

	private static CaseResult runCase(
			byte[] motor,
			byte[] propeller,
			boolean efxEnabled
	) {
		long device = 0L;
		long context = 0L;
		boolean threadContextSet = false;
		int motorBuffer = 0;
		int propellerBuffer = 0;
		int effect = 0;
		int slot = 0;
		List<Integer> sources = new ArrayList<>();
		List<Integer> filters = new ArrayList<>();
		try {
			device = SOFTLoopback.alcLoopbackOpenDeviceSOFT(
					(CharSequence) null
			);
			if (device == 0L) {
				throw new IllegalStateException("loopback device unavailable");
			}
			ALCCapabilities capabilities = ALC.createCapabilities(device);
			if (!capabilities.ALC_SOFT_loopback
					|| !capabilities.ALC_EXT_thread_local_context
					|| (efxEnabled && !capabilities.ALC_EXT_EFX)) {
				throw new IllegalStateException(
						"required loopback/EFX capability unavailable"
				);
			}
			IntBuffer attributes = BufferUtils.createIntBuffer(7);
			attributes.put(ALC10.ALC_FREQUENCY).put(SAMPLE_RATE_HZ);
			attributes.put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT)
					.put(SOFTLoopback.ALC_MONO_SOFT);
			attributes.put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT)
					.put(SOFTLoopback.ALC_SHORT_SOFT);
			attributes.put(0).flip();
			context = ALC10.alcCreateContext(device, attributes);
			if (context == 0L
					|| !EXTThreadLocalContext.alcSetThreadContext(context)) {
				throw new IllegalStateException(
						"could not activate loopback context"
				);
			}
			threadContextSet = true;
			AL.createCapabilities(capabilities);
			clearAlErrors();
			motorBuffer = createBuffer(motor);
			propellerBuffer = createBuffer(propeller);
			if (efxEnabled) {
				effect = EXTEfx.alGenEffects();
				EXTEfx.alEffecti(
						effect,
						EXTEfx.AL_EFFECT_TYPE,
						EXTEfx.AL_EFFECT_REVERB
				);
				OpenAlEfxController.configureReverbEffect(
						effect,
						CLOSED_ROOM
				);
				slot = EXTEfx.alGenAuxiliaryEffectSlots();
				EXTEfx.alAuxiliaryEffectSloti(
						slot,
						EXTEfx.AL_EFFECTSLOT_EFFECT,
						effect
				);
			}
			for (int index = 0; index < SOURCE_COUNT; index++) {
				int source = AL10.alGenSources();
				AL10.alSourcei(
						source,
						AL10.AL_BUFFER,
						index % 2 == 0 ? motorBuffer : propellerBuffer
				);
				AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
				sources.add(source);
				if (efxEnabled) {
					int filter = EXTEfx.alGenFilters();
					EXTEfx.alFilteri(
							filter,
							EXTEfx.AL_FILTER_TYPE,
							EXTEfx.AL_FILTER_LOWPASS
					);
					OpenAlEfxController.configureSendFilter(
							filter,
							CLOSED_ROOM.rt60Seconds()
					);
					AL11.alSource3i(
							source,
							EXTEfx.AL_AUXILIARY_SEND_FILTER,
							slot,
							0,
							filter
					);
					filters.add(filter);
				}
				AL10.alSourcePlay(source);
			}
			if (AL10.alGetError() != AL10.AL_NO_ERROR) {
				throw new IllegalStateException("OpenAL setup failed");
			}
			ShortBuffer rendered =
					BufferUtils.createShortBuffer(BLOCK_FRAMES);
			for (int index = 0; index < WARMUP_ITERATIONS; index++) {
				rendered.clear();
				SOFTLoopback.alcRenderSamplesSOFT(
						device,
						rendered,
						BLOCK_FRAMES
				);
			}
			long[] elapsed = new long[MEASURED_ITERATIONS];
			long checksum = 0L;
			for (int index = 0; index < MEASURED_ITERATIONS; index++) {
				rendered.clear();
				long start = System.nanoTime();
				SOFTLoopback.alcRenderSamplesSOFT(
						device,
						rendered,
						BLOCK_FRAMES
				);
				elapsed[index] = System.nanoTime() - start;
				checksum += rendered.get(index % BLOCK_FRAMES);
			}
			int alError = AL10.alGetError();
			int alcError = ALC10.alcGetError(device);
			if (alError != AL10.AL_NO_ERROR
					|| alcError != ALC10.ALC_NO_ERROR) {
				throw new IllegalStateException(
						"OpenAL render failed: al=" + alError
								+ " alc=" + alcError
				);
			}
			Arrays.sort(elapsed);
			double bufferMilliseconds =
					BLOCK_FRAMES * 1_000.0 / SAMPLE_RATE_HZ;
			double p99Milliseconds =
					percentile(elapsed, 0.99) / 1_000_000.0;
			return new CaseResult(
					efxEnabled ? "openal-efx" : "dry",
					elapsed[MEASURED_ITERATIONS / 2] / 1_000_000.0,
					percentile(elapsed, 0.95) / 1_000_000.0,
					p99Milliseconds,
					p99Milliseconds / bufferMilliseconds,
					checksum,
					alError,
					alcError
			);
		} finally {
			if (threadContextSet) {
				for (int source : sources) {
					AL10.alDeleteSources(source);
				}
				for (int filter : filters) {
					EXTEfx.alDeleteFilters(filter);
				}
				if (slot != 0) {
					EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
				}
				if (effect != 0) {
					EXTEfx.alDeleteEffects(effect);
				}
				if (propellerBuffer != 0) {
					AL10.alDeleteBuffers(propellerBuffer);
				}
				if (motorBuffer != 0) {
					AL10.alDeleteBuffers(motorBuffer);
				}
				AL.setCurrentThread(null);
				EXTThreadLocalContext.alcSetThreadContext(0L);
			}
			if (context != 0L) {
				ALC10.alcDestroyContext(context);
			}
			if (device != 0L) {
				ALC10.alcCloseDevice(device);
			}
			ALC.setCapabilities(null);
		}
	}

	private static int createBuffer(byte[] pcm) {
		ByteBuffer input = BufferUtils.createByteBuffer(pcm.length);
		input.put(pcm).flip();
		int buffer = AL10.alGenBuffers();
		AL10.alBufferData(
				buffer,
				AL10.AL_FORMAT_MONO16,
				input,
				SAMPLE_RATE_HZ
		);
		return buffer;
	}

	private static void clearAlErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	private static double percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static String sha256(byte[] input) throws Exception {
		return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(input)
		);
	}

	private static String toJson(
			CaseResult dry,
			CaseResult efx,
			String sidecarSha256
	) {
		double incrementalP99 =
				Math.max(0.0, efx.p99Milliseconds()
						- dry.p99Milliseconds());
		double bufferMilliseconds =
				BLOCK_FRAMES * 1_000.0 / SAMPLE_RATE_HZ;
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": "
						+ "\"valid-openal-efx-runtime-benchmark\",%n"
						+ "  \"source_count\": %d,%n"
						+ "  \"drone_count\": 6,%n"
						+ "  \"layers_per_drone\": 2,%n"
						+ "  \"block_frames\": %d,%n"
						+ "  \"buffer_duration_ms\": %.17g,%n"
						+ "  \"warmup_iterations\": %d,%n"
						+ "  \"measured_iterations\": %d,%n"
						+ "  \"d094_sidecar_sha256\": \"%s\",%n"
						+ "  \"shared_effects\": 1,%n"
						+ "  \"shared_auxiliary_slots\": 1,%n"
						+ "  \"per_source_send_filters\": %d,%n"
						+ "  \"incremental_efx_p99_ms\": %.17g,%n"
						+ "  \"incremental_efx_p99_buffer_fraction\": "
						+ "%.17g,%n"
						+ "  \"gates\": {"
						+ "\"dry_p99_below_25_percent\":%s,"
						+ "\"efx_p99_below_25_percent\":%s},%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"physical_endpoint_opened\": false,%n"
						+ "  \"captures_audio\": false,%n"
						+ "  \"claim_boundary\": \"In-process OpenAL Soft "
						+ "loopback renderer wall time on this host; includes "
						+ "12 looping production PCM sources and native mixing, "
						+ "but excludes Minecraft frame time, physical endpoint "
						+ "callbacks, device drivers, and underruns.\",%n"
						+ "  \"cases\": [%s,%s]%n"
						+ "}%n",
				SOURCE_COUNT,
				BLOCK_FRAMES,
				bufferMilliseconds,
				WARMUP_ITERATIONS,
				MEASURED_ITERATIONS,
				sidecarSha256,
				SOURCE_COUNT,
				incrementalP99,
				incrementalP99 / bufferMilliseconds,
				dry.p99BufferFraction() <= 0.25,
				efx.p99BufferFraction() <= 0.25,
				dry.toJson(),
				efx.toJson()
		);
	}

	private record Chunk(int offset, int bytes) {
	}

	private record CaseResult(
			String backend,
			double p50Milliseconds,
			double p95Milliseconds,
			double p99Milliseconds,
			double p99BufferFraction,
			long checksum,
			int alErrorCode,
			int alcErrorCode
	) {
		private String toJson() {
			return String.format(
					Locale.ROOT,
					"{\"backend\":\"%s\",\"p50_ms\":%.17g,"
							+ "\"p95_ms\":%.17g,\"p99_ms\":%.17g,"
							+ "\"p99_buffer_fraction\":%.17g,"
							+ "\"checksum\":%d,\"al_error\":%d,"
							+ "\"alc_error\":%d}",
					backend,
					p50Milliseconds,
					p95Milliseconds,
					p99Milliseconds,
					p99BufferFraction,
					checksum,
					alErrorCode,
					alcErrorCode
			);
		}
	}
}
