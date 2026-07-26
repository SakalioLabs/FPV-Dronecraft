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
import java.util.Comparator;
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
import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;

/**
 * Isolated OpenAL Soft render that creates each fallback backend only at its
 * ownership boundary. No physical playback or capture device is opened.
 */
public final class BackendColdStartContinuityProbe {
	private static final int SAMPLE_RATE_HZ = 48_000;
	private static final int SEGMENT_FRAMES = 48_000;
	private static final int SEGMENT_COUNT = 4;
	private static final int RENDER_FRAMES =
			SEGMENT_FRAMES * SEGMENT_COUNT;
	private static final int OUTPUT_BYTES = RENDER_FRAMES * 2;
	private static final int[][] FALLBACK_WINDOWS = {
		{12_000, 24_000},
		{60_000, 72_000},
		{108_000, 120_000}
	};
	private static final String[] FAULT_STAGES = {
		"resource-create",
		"parameter-write",
		"source-route"
	};
	private static final String EFX_EXTENSION = "ALC_EXT_EFX";

	private BackendColdStartContinuityProbe() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 8) {
			throw new IllegalArgumentException(
					"usage: BackendColdStartContinuityProbe "
							+ "<D100-json> <D094-json> <D094-pcm> "
							+ "<D102-json> <output-json> <cold-pcm> "
							+ "<dry-pcm> <java-wet-pcm>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		Path transitionPath = Path.of(arguments[0]);
		Path tracePath = Path.of(arguments[1]);
		Path tracePcmPath = Path.of(arguments[2]);
		Path failoverPath = Path.of(arguments[3]);
		byte[] transitionBytes = Files.readAllBytes(transitionPath);
		byte[] traceBytes = Files.readAllBytes(tracePath);
		byte[] tracePcm = Files.readAllBytes(tracePcmPath);
		byte[] failoverBytes = Files.readAllBytes(failoverPath);
		JsonObject transition = JsonParser.parseString(
				new String(transitionBytes, StandardCharsets.UTF_8)
		).getAsJsonObject();
		JsonObject trace = JsonParser.parseString(
				new String(traceBytes, StandardCharsets.UTF_8)
		).getAsJsonObject();
		JsonObject failover = JsonParser.parseString(
				new String(failoverBytes, StandardCharsets.UTF_8)
		).getAsJsonObject();
		validateReports(transition, trace, tracePcm, failover);
		byte[] motor = concatenateLayer(
				"motor",
				transition,
				trace,
				tracePcm
		);
		byte[] propeller = concatenateLayer(
				"propeller",
				transition,
				trace,
				tracePcm
		);
		List<FdnEnvironmentMapper.Controls> environments =
				readEnvironments(transition);
		JavaWet javaWet = renderColdJavaWet(
				motor,
				propeller,
				environments
		);
		Render dry = renderLoopback(
				motor,
				propeller,
				new byte[OUTPUT_BYTES],
				environments,
				false
		);
		Render cold = renderLoopback(
				motor,
				propeller,
				javaWet.pcm(),
				environments,
				true
		);
		Path reportOutput = Path.of(arguments[4])
				.toAbsolutePath().normalize();
		Path coldOutput = Path.of(arguments[5])
				.toAbsolutePath().normalize();
		Path dryOutput = Path.of(arguments[6])
				.toAbsolutePath().normalize();
		Path wetOutput = Path.of(arguments[7])
				.toAbsolutePath().normalize();
		for (Path output : List.of(
				reportOutput,
				coldOutput,
				dryOutput,
				wetOutput
		)) {
			Files.createDirectories(output.getParent());
		}
		Files.write(coldOutput, cold.pcm());
		Files.write(dryOutput, dry.pcm());
		Files.write(wetOutput, javaWet.pcm());
		Files.writeString(
				reportOutput,
				reportJson(
						sha256(transitionBytes),
						sha256(traceBytes),
						sha256(tracePcm),
						sha256(failoverBytes),
						motor,
						propeller,
						environments,
						javaWet,
						dry,
						cold
				),
				StandardCharsets.UTF_8
		);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-backend-cold-start-render\","
						+ "\"output\":\"%s\"}%n",
				reportOutput.toString().replace("\\", "\\\\")
		);
	}

	private static void validateReports(
			JsonObject transition,
			JsonObject trace,
			byte[] tracePcm,
			JsonObject failover
	) throws Exception {
		if (transition.get("schema_version").getAsInt() != 1
				|| !"valid-backend-dynamic-transition".equals(
						transition.get("status").getAsString()
				)
				|| transition.get("render_frames").getAsInt()
						!= RENDER_FRAMES
				|| !"s16le-mono-48000".equals(
						transition.get("render_format").getAsString()
				)) {
			throw new IllegalArgumentException(
					"D100 transition report is incompatible"
			);
		}
		if (!"valid-doppler-production-chunk-trace".equals(
				trace.get("status").getAsString()
		) || !sha256(tracePcm).equals(
				trace.get("pcm_sha256").getAsString()
		)) {
			throw new IllegalArgumentException(
					"D094 trace report or sidecar is incompatible"
			);
		}
		if (!"valid-openal-efx-fault-failover".equals(
				failover.get("status").getAsString()
		)) {
			throw new IllegalArgumentException(
					"D102 failover report is incompatible"
			);
		}
		JsonArray cycles = failover.getAsJsonArray("fault_cycles");
		if (cycles.size() != FAULT_STAGES.length) {
			throw new IllegalArgumentException(
					"D102 fault cycle count changed"
			);
		}
		for (int index = 0; index < cycles.size(); index++) {
			JsonObject cycle = cycles.get(index).getAsJsonObject();
			if (!FAULT_STAGES[index].equals(
					cycle.get("stage").getAsString()
			) || !"JAVA_FDN".equals(
					cycle.getAsJsonObject("fallback")
							.get("backend").getAsString()
			) || cycle.getAsJsonObject("fallback")
					.get("double_wet_path").getAsBoolean()
					|| !"OPENAL_EFX".equals(
							cycle.getAsJsonObject("recovery_runtime")
									.get("backend").getAsString()
					)
					|| cycle.getAsJsonObject("recovery_runtime")
							.get("double_wet_path").getAsBoolean()) {
				throw new IllegalArgumentException(
						"D102 ownership sequence changed"
				);
			}
		}
	}

	private static byte[] concatenateLayer(
			String layer,
			JsonObject transition,
			JsonObject trace,
			byte[] sidecar
	) throws Exception {
		List<JsonObject> chunks = new ArrayList<>();
		for (var element : trace.getAsJsonArray("chunks")) {
			JsonObject chunk = element.getAsJsonObject();
			if (layer.equals(chunk.get("layer").getAsString())) {
				chunks.add(chunk);
			}
		}
		chunks.sort(Comparator.comparingLong(
				value -> value.get("sequence").getAsLong()
		));
		if (chunks.size() < SEGMENT_COUNT) {
			throw new IllegalArgumentException(
					"D094 " + layer + " chunks are incomplete"
			);
		}
		JsonArray manifest = transition.getAsJsonArray("inputs");
		byte[] result = new byte[OUTPUT_BYTES];
		int outputOffset = 0;
		for (int index = 0; index < SEGMENT_COUNT; index++) {
			JsonObject chunk = chunks.get(index);
			long sequence = chunk.get("sequence").getAsLong();
			int offset = chunk.get("pcm_offset").getAsInt();
			int bytes = chunk.get("pcm_bytes").getAsInt();
			String hash = chunk.get("pcm_sha256").getAsString();
			if (bytes != SEGMENT_FRAMES * 2
					|| offset < 0
					|| offset + bytes > sidecar.length
					|| !manifestContains(
							manifest,
							layer,
							sequence,
							hash
					)) {
				throw new IllegalArgumentException(
						"D100/D094 " + layer + " input detached"
				);
			}
			byte[] payload = Arrays.copyOfRange(
					sidecar,
					offset,
					offset + bytes
			);
			if (!hash.equals(sha256(payload))) {
				throw new IllegalArgumentException(
						"D094 " + layer + " PCM hash changed"
				);
			}
			System.arraycopy(
					payload,
					0,
					result,
					outputOffset,
					payload.length
			);
			outputOffset += payload.length;
		}
		if (!sha256(result).equals(
				transition.get(layer + "_pcm_sha256").getAsString()
		)) {
			throw new IllegalArgumentException(
					"D100 combined " + layer + " hash changed"
			);
		}
		return result;
	}

	private static boolean manifestContains(
			JsonArray manifest,
			String layer,
			long sequence,
			String hash
	) {
		for (var element : manifest) {
			JsonObject item = element.getAsJsonObject();
			if (layer.equals(item.get("layer").getAsString())
					&& sequence == item.get("sequence").getAsLong()
					&& hash.equals(
							item.get("pcm_sha256").getAsString()
					)) {
				return true;
			}
		}
		return false;
	}

	private static List<FdnEnvironmentMapper.Controls> readEnvironments(
			JsonObject transition
	) {
		JsonArray values = transition.getAsJsonArray("environments");
		if (values.size() != SEGMENT_COUNT) {
			throw new IllegalArgumentException(
					"D100 environment count changed"
			);
		}
		List<FdnEnvironmentMapper.Controls> result = new ArrayList<>();
		for (int index = 0; index < values.size(); index++) {
			JsonObject value = values.get(index).getAsJsonObject();
			JsonObject rt60 = value.getAsJsonObject("rt60_seconds");
			result.add(new FdnEnvironmentMapper.Controls(
					value.get("snapshot_generation").getAsLong(),
					new AcousticBands(
							rt60.get("low").getAsDouble(),
							rt60.get("mid").getAsDouble(),
							rt60.get("high").getAsDouble()
					),
					value.get("wet_gain").getAsDouble(),
					value.get("transition_seconds").getAsDouble()
			));
		}
		return List.copyOf(result);
	}

	private static JavaWet renderColdJavaWet(
			byte[] motorPcm,
			byte[] propellerPcm,
			List<FdnEnvironmentMapper.Controls> environments
	) {
		float[] input = new float[RENDER_FRAMES];
		for (int frame = 0; frame < RENDER_FRAMES; frame++) {
			float mixed = pcm16At(motorPcm, frame) / 32_768.0F
					+ pcm16At(propellerPcm, frame) / 32_768.0F;
			input[frame] = mixed / (1.0F + Math.abs(mixed));
		}
		float[] wet = new float[RENDER_FRAMES];
		List<JavaWindow> windows = new ArrayList<>();
		for (int index = 0; index < FALLBACK_WINDOWS.length; index++) {
			int start = FALLBACK_WINDOWS[index][0];
			int end = FALLBACK_WINDOWS[index][1];
			ListenerSharedFdn fdn =
					new ListenerSharedFdn(SAMPLE_RATE_HZ);
			FdnEnvironmentMapper.Controls environment =
					environments.get(index);
			fdn.configure(
					environment.rt60Seconds(),
					environment.wetGain(),
					0.0
			);
			fdn.process(input, wet, start, end - start);
			int firstNonzero = -1;
			for (int frame = start; frame < end; frame++) {
				if (wet[frame] != 0.0F) {
					firstNonzero = frame;
					break;
				}
			}
			windows.add(new JavaWindow(
					start,
					end,
					firstNonzero,
					fdn.diagnosticDelaySamples()
			));
		}
		return new JavaWet(pcm16Le(wet), List.copyOf(windows));
	}

	private static Render renderLoopback(
			byte[] motorPcm,
			byte[] propellerPcm,
			byte[] javaWetPcm,
			List<FdnEnvironmentMapper.Controls> environments,
			boolean coldStartSwitching
	) {
		try (LoopbackRenderer renderer = new LoopbackRenderer(
				motorPcm,
				propellerPcm,
				javaWetPcm
		)) {
			if (!coldStartSwitching) {
				return renderer.renderDry();
			}
			return renderer.renderCold(environments);
		}
	}

	private static String reportJson(
			String transitionSha,
			String traceSha,
			String tracePcmSha,
			String failoverSha,
			byte[] motor,
			byte[] propeller,
			List<FdnEnvironmentMapper.Controls> environments,
			JavaWet javaWet,
			Render dry,
			Render cold
	) {
		JsonObject report = new JsonObject();
		report.addProperty("schema_version", 1);
		report.addProperty("status", "valid-backend-cold-start-render");
		report.addProperty("render_format", "s16le-mono-48000");
		report.addProperty("sample_rate_hz", SAMPLE_RATE_HZ);
		report.addProperty("render_frames", RENDER_FRAMES);
		report.addProperty(
				"source_transition_report_sha256",
				transitionSha
		);
		report.addProperty("source_trace_report_sha256", traceSha);
		report.addProperty("source_trace_pcm_sha256", tracePcmSha);
		report.addProperty(
				"source_failover_report_sha256",
				failoverSha
		);
		report.addProperty("motor_pcm_sha256", sha256(motor));
		report.addProperty("propeller_pcm_sha256", sha256(propeller));
		report.addProperty("dry_pcm_bytes", dry.pcm().length);
		report.addProperty("dry_pcm_sha256", sha256(dry.pcm()));
		report.addProperty("cold_pcm_bytes", cold.pcm().length);
		report.addProperty("cold_pcm_sha256", sha256(cold.pcm()));
		report.addProperty(
				"java_wet_pcm_bytes",
				javaWet.pcm().length
		);
		report.addProperty(
				"java_wet_pcm_sha256",
				sha256(javaWet.pcm())
		);
		JsonArray environmentJson = new JsonArray();
		for (int index = 0; index < environments.size(); index++) {
			FdnEnvironmentMapper.Controls value =
					environments.get(index);
			JsonObject item = new JsonObject();
			item.addProperty("segment", index);
			item.addProperty(
					"snapshot_generation",
					value.snapshotGeneration()
			);
			JsonObject rt60 = new JsonObject();
			rt60.addProperty("low", value.rt60Seconds().low());
			rt60.addProperty("mid", value.rt60Seconds().mid());
			rt60.addProperty("high", value.rt60Seconds().high());
			item.add("rt60_seconds", rt60);
			item.addProperty("wet_gain", value.wetGain());
			environmentJson.add(item);
		}
		report.add("environments", environmentJson);
		JsonArray windows = new JsonArray();
		for (int index = 0; index < javaWet.windows().size(); index++) {
			JavaWindow value = javaWet.windows().get(index);
			JsonObject item = new JsonObject();
			item.addProperty("fault_stage", FAULT_STAGES[index]);
			item.addProperty("start_frame", value.startFrame());
			item.addProperty("end_frame", value.endFrame());
			item.addProperty(
					"first_nonzero_wet_frame",
					value.firstNonzeroWetFrame()
			);
			item.addProperty(
					"onset_delay_samples",
					value.firstNonzeroWetFrame() - value.startFrame()
			);
			JsonArray delays = new JsonArray();
			for (int delay : value.delaySamples()) {
				delays.add(delay);
			}
			item.add("fdn_delay_samples", delays);
			windows.add(item);
		}
		report.add("java_fallback_windows", windows);
		report.addProperty(
				"efx_resources_created_at_recovery",
				true
		);
		report.addProperty(
				"java_fdn_created_at_fallback",
				true
		);
		report.addProperty("backend_state_preheated", false);
		report.addProperty(
				"splice_method",
				"cold-start-exclusive-owner-no-crossfade"
		);
		report.addProperty("al_error", cold.alError());
		report.addProperty("alc_error", cold.alcError());
		report.addProperty("dry_al_error", dry.alError());
		report.addProperty("dry_alc_error", dry.alcError());
		report.addProperty("exclusive_wet_owner", true);
		report.addProperty("physical_endpoint_opened", false);
		report.addProperty("captures_audio", false);
		report.addProperty("release_calibrated", false);
		report.addProperty(
				"claim_boundary",
				"Isolated OpenAL Soft cold-start lifecycle render using "
						+ "production PCM, EFX parameter mapping, and "
						+ "ListenerSharedFdn. It is not Minecraft "
						+ "main-context output, a physical endpoint "
						+ "capture, an audible threshold, or release "
						+ "calibration."
		);
		return report.toString() + System.lineSeparator();
	}

	private static int pcm16At(byte[] pcm, int frame) {
		int low = pcm[frame * 2] & 0xff;
		int high = pcm[frame * 2 + 1];
		return (short) (low | (high << 8));
	}

	private static byte[] pcm16Le(float[] samples) {
		byte[] pcm = new byte[samples.length * 2];
		for (int frame = 0; frame < samples.length; frame++) {
			int value = Math.round(
					Math.max(-1.0F, Math.min(1.0F, samples[frame]))
							* 32_767.0F
			);
			pcm[frame * 2] = (byte) value;
			pcm[frame * 2 + 1] = (byte) (value >>> 8);
		}
		return pcm;
	}

	private static byte[] pcm16Le(short[] samples) {
		byte[] pcm = new byte[samples.length * 2];
		for (int frame = 0; frame < samples.length; frame++) {
			int value = samples[frame] & 0xffff;
			pcm[frame * 2] = (byte) value;
			pcm[frame * 2 + 1] = (byte) (value >>> 8);
		}
		return pcm;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (Exception error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	private static final class LoopbackRenderer implements AutoCloseable {
		private final long device;
		private final long context;
		private final int motorBuffer;
		private final int propellerBuffer;
		private final int javaWetBuffer;
		private final int motorSource;
		private final int propellerSource;
		private final int javaWetSource;
		private EfxResources efx;

		private LoopbackRenderer(
				byte[] motor,
				byte[] propeller,
				byte[] javaWet
		) {
			device = SOFTLoopback.alcLoopbackOpenDeviceSOFT(
					(CharSequence) null
			);
			if (device == 0L) {
				throw new IllegalStateException(
						"could not open cold-start loopback device"
				);
			}
			ALCCapabilities capabilities = ALC.createCapabilities(device);
			if (!capabilities.ALC_SOFT_loopback
					|| !capabilities.ALC_EXT_thread_local_context
					|| !capabilities.ALC_EXT_EFX
					|| !ALC10.alcIsExtensionPresent(
							device,
							EFX_EXTENSION
					)) {
				throw new IllegalStateException(
						"cold-start loopback extensions unavailable"
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
					|| !EXTThreadLocalContext.alcSetThreadContext(
							context
					)) {
				throw new IllegalStateException(
						"could not create cold-start loopback context"
				);
			}
			AL.createCapabilities(capabilities);
			clearAlErrors();
			motorBuffer = createBuffer(motor);
			propellerBuffer = createBuffer(propeller);
			javaWetBuffer = createBuffer(javaWet);
			motorSource = createSource(motorBuffer);
			propellerSource = createSource(propellerBuffer);
			javaWetSource = createSource(javaWetBuffer);
		}

		private Render renderDry() {
			startSources();
			short[] output = renderFrames(RENDER_FRAMES);
			return finish(output);
		}

		private Render renderCold(
				List<FdnEnvironmentMapper.Controls> environments
		) {
			efx = createEfx(environments.get(0));
			startSources();
			short[] output = new short[RENDER_FRAMES];
			int cursor = 0;
			cursor = renderUntil(output, cursor, 12_000);
			deleteEfx();
			cursor = renderUntil(output, cursor, 24_000);
			efx = createEfx(environments.get(0));
			cursor = renderUntil(output, cursor, 48_000);
			configureEfx(environments.get(1));
			cursor = renderUntil(output, cursor, 60_000);
			deleteEfx();
			cursor = renderUntil(output, cursor, 72_000);
			efx = createEfx(environments.get(1));
			cursor = renderUntil(output, cursor, 96_000);
			configureEfx(environments.get(2));
			cursor = renderUntil(output, cursor, 108_000);
			deleteEfx();
			cursor = renderUntil(output, cursor, 120_000);
			efx = createEfx(environments.get(2));
			cursor = renderUntil(output, cursor, 144_000);
			configureEfx(environments.get(3));
			renderUntil(output, cursor, RENDER_FRAMES);
			return finish(output);
		}

		private void startSources() {
			AL10.alSourcePlay(motorSource);
			AL10.alSourcePlay(propellerSource);
			AL10.alSourcePlay(javaWetSource);
		}

		private int renderUntil(
				short[] output,
				int cursor,
				int end
		) {
			short[] rendered = renderFrames(end - cursor);
			System.arraycopy(
					rendered,
					0,
					output,
					cursor,
					rendered.length
			);
			return end;
		}

		private short[] renderFrames(int frames) {
			ShortBuffer rendered = BufferUtils.createShortBuffer(frames);
			SOFTLoopback.alcRenderSamplesSOFT(
					device,
					rendered,
					frames
			);
			short[] result = new short[frames];
			for (int frame = 0; frame < frames; frame++) {
				result[frame] = rendered.get(frame);
			}
			return result;
		}

		private Render finish(short[] output) {
			int alError = AL10.alGetError();
			int alcError = ALC10.alcGetError(device);
			if (alError != AL10.AL_NO_ERROR
					|| alcError != ALC10.ALC_NO_ERROR) {
				throw new IllegalStateException(
						"cold-start loopback error: al="
								+ alError + " alc=" + alcError
				);
			}
			return new Render(pcm16Le(output), alError, alcError);
		}

		private EfxResources createEfx(
				FdnEnvironmentMapper.Controls environment
		) {
			int effect = EXTEfx.alGenEffects();
			EXTEfx.alEffecti(
					effect,
					EXTEfx.AL_EFFECT_TYPE,
					EXTEfx.AL_EFFECT_REVERB
			);
			int slot = EXTEfx.alGenAuxiliaryEffectSlots();
			int motorFilter = createSendFilter();
			int propellerFilter = createSendFilter();
			EfxResources result = new EfxResources(
					effect,
					slot,
					motorFilter,
					propellerFilter
			);
			efx = result;
			configureEfx(environment);
			attachSend(motorSource, slot, motorFilter);
			attachSend(propellerSource, slot, propellerFilter);
			return result;
		}

		private void configureEfx(
				FdnEnvironmentMapper.Controls environment
		) {
			OpenAlEfxController.configureReverbEffect(
					efx.effect(),
					environment
			);
			EXTEfx.alAuxiliaryEffectSloti(
					efx.slot(),
					EXTEfx.AL_EFFECTSLOT_EFFECT,
					efx.effect()
			);
			OpenAlEfxController.configureSendFilter(
					efx.motorFilter(),
					environment.rt60Seconds()
			);
			OpenAlEfxController.configureSendFilter(
					efx.propellerFilter(),
					environment.rt60Seconds()
			);
		}

		private void deleteEfx() {
			if (efx == null) {
				return;
			}
			attachSend(motorSource, 0, 0);
			attachSend(propellerSource, 0, 0);
			EXTEfx.alDeleteFilters(efx.propellerFilter());
			EXTEfx.alDeleteFilters(efx.motorFilter());
			EXTEfx.alDeleteAuxiliaryEffectSlots(efx.slot());
			EXTEfx.alDeleteEffects(efx.effect());
			efx = null;
		}

		@Override
		public void close() {
			deleteEfx();
			deleteSource(javaWetSource);
			deleteSource(propellerSource);
			deleteSource(motorSource);
			deleteBuffer(javaWetBuffer);
			deleteBuffer(propellerBuffer);
			deleteBuffer(motorBuffer);
			AL.setCurrentThread(null);
			EXTThreadLocalContext.alcSetThreadContext(0L);
			ALC10.alcDestroyContext(context);
			boolean closed = ALC10.alcCloseDevice(device);
			ALC.setCapabilities(null);
			if (!closed) {
				throw new IllegalStateException(
						"could not close cold-start loopback device"
				);
			}
		}
	}

	private static int createBuffer(byte[] pcm) {
		int buffer = AL10.alGenBuffers();
		ByteBuffer direct = BufferUtils.createByteBuffer(pcm.length);
		direct.put(pcm).flip();
		AL10.alBufferData(
				buffer,
				AL10.AL_FORMAT_MONO16,
				direct,
				SAMPLE_RATE_HZ
		);
		return buffer;
	}

	private static int createSource(int buffer) {
		int source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
		return source;
	}

	private static int createSendFilter() {
		int filter = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(
				filter,
				EXTEfx.AL_FILTER_TYPE,
				EXTEfx.AL_FILTER_LOWPASS
		);
		return filter;
	}

	private static void attachSend(int source, int slot, int filter) {
		AL11.alSource3i(
				source,
				EXTEfx.AL_AUXILIARY_SEND_FILTER,
				slot,
				0,
				filter
		);
	}

	private static void deleteSource(int source) {
		AL10.alSourceStop(source);
		AL10.alDeleteSources(source);
	}

	private static void deleteBuffer(int buffer) {
		AL10.alDeleteBuffers(buffer);
	}

	private static void clearAlErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	private record EfxResources(
			int effect,
			int slot,
			int motorFilter,
			int propellerFilter
	) {
	}

	private record JavaWindow(
			int startFrame,
			int endFrame,
			int firstNonzeroWetFrame,
			int[] delaySamples
	) {
		private JavaWindow {
			delaySamples = delaySamples.clone();
		}

		@Override
		public int[] delaySamples() {
			return delaySamples.clone();
		}
	}

	private record JavaWet(byte[] pcm, List<JavaWindow> windows) {
		private JavaWet {
			pcm = pcm.clone();
			windows = List.copyOf(windows);
		}

		@Override
		public byte[] pcm() {
			return pcm.clone();
		}
	}

	private record Render(byte[] pcm, int alError, int alcError) {
		private Render {
			pcm = pcm.clone();
		}

		@Override
		public byte[] pcm() {
			return pcm.clone();
		}
	}
}
