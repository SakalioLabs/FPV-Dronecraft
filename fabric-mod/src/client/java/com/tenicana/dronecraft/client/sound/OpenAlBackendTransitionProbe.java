package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;
import com.tenicana.dronecraft.client.mixin.SoundEngineExecutorAccessor;
import com.tenicana.dronecraft.client.mixin.SoundManagerEngineAccessor;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
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

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Applies four production-mapped environments at exact one-second render
 * boundaries while one continuous four-chunk production input is active.
 */
final class OpenAlBackendTransitionProbe {
	static final int SAMPLE_RATE_HZ = 48_000;
	static final int SEGMENT_FRAMES = 48_000;
	static final int SEGMENT_COUNT = 4;
	static final int RENDER_FRAMES = SEGMENT_FRAMES * SEGMENT_COUNT;
	private static final String EFX_EXTENSION = "ALC_EXT_EFX";

	private OpenAlBackendTransitionProbe() {
	}

	static CompletableFuture<Result> capture(
			SoundManager soundManager,
			DopplerAudioChunkTrace.Snapshot trace,
			List<FdnEnvironmentMapper.Controls> environments
	) {
		Objects.requireNonNull(soundManager, "soundManager");
		Objects.requireNonNull(trace, "trace");
		Objects.requireNonNull(environments, "environments");
		if (environments.size() != SEGMENT_COUNT) {
			throw new IllegalArgumentException(
					"transition probe requires four environments"
			);
		}
		List<DopplerAudioChunkTrace.Chunk> motor = chunks(
				trace,
				PhaseContinuousSynthesizer.Layer.MOTOR
		);
		List<DopplerAudioChunkTrace.Chunk> propeller = chunks(
				trace,
				PhaseContinuousSynthesizer.Layer.PROPELLER
		);
		byte[] motorPcm = concatenate(motor);
		byte[] propellerPcm = concatenate(propeller);
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		SoundEngineExecutor soundExecutor =
				((SoundEngineExecutorAccessor) soundEngine)
						.fpvdrone$getExecutor();
		ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(
					task,
					"Dronecraft backend transition probe"
			);
			thread.setDaemon(true);
			return thread;
		});
		CompletableFuture<Result> future = captureContext(soundExecutor)
				.thenCompose(before -> CompletableFuture.supplyAsync(
						() -> render(
								before,
								motor,
								propeller,
								motorPcm,
								propellerPcm,
								List.copyOf(environments)
						),
						worker
				))
				.thenCompose(partial -> captureContext(soundExecutor)
						.thenApply(partial::finish));
		return future.whenComplete((ignored, error) -> worker.shutdown());
	}

	private static CompletableFuture<ContextIdentity> captureContext(
			SoundEngineExecutor executor
	) {
		CompletableFuture<ContextIdentity> result = new CompletableFuture<>();
		executor.schedule(() -> {
			long context = ALC10.alcGetCurrentContext();
			long device = context == 0L
					? 0L
					: ALC10.alcGetContextsDevice(context);
			result.complete(new ContextIdentity(
					context,
					device,
					Thread.currentThread().getName()
			));
		});
		return result;
	}

	private static PartialResult render(
			ContextIdentity before,
			List<DopplerAudioChunkTrace.Chunk> motor,
			List<DopplerAudioChunkTrace.Chunk> propeller,
			byte[] motorPcm,
			byte[] propellerPcm,
			List<FdnEnvironmentMapper.Controls> environments
	) {
		byte[] wetPcm = renderJavaWet(
				motorPcm,
				propellerPcm,
				environments
		);
		Render dry = renderLoopback(
				motorPcm,
				propellerPcm,
				null,
				environments,
				false
		);
		Render efx = renderLoopback(
				motorPcm,
				propellerPcm,
				null,
				environments,
				true
		);
		Render javaFdn = renderLoopback(
				motorPcm,
				propellerPcm,
				wetPcm,
				environments,
				false
		);
		return new PartialResult(
				before,
				Thread.currentThread().getName(),
				inputs(motor, propeller),
				sha256(motorPcm),
				sha256(propellerPcm),
				environments,
				dry,
				efx,
				javaFdn
		);
	}

	private static Render renderLoopback(
			byte[] motorPcm,
			byte[] propellerPcm,
			byte[] wetPcm,
			List<FdnEnvironmentMapper.Controls> environments,
			boolean efxEnabled
	) {
		long device = 0L;
		long context = 0L;
		int motorBuffer = 0;
		int propellerBuffer = 0;
		int wetBuffer = 0;
		int motorSource = 0;
		int propellerSource = 0;
		int wetSource = 0;
		int effect = 0;
		int slot = 0;
		int motorFilter = 0;
		int propellerFilter = 0;
		boolean threadContextSet = false;
		try {
			device = SOFTLoopback.alcLoopbackOpenDeviceSOFT(
					(CharSequence) null
			);
			if (device == 0L) {
				throw new IllegalStateException(
						"could not open transition loopback device"
				);
			}
			ALCCapabilities capabilities = ALC.createCapabilities(device);
			if (!capabilities.ALC_SOFT_loopback
					|| !capabilities.ALC_EXT_thread_local_context
					|| (efxEnabled && (!capabilities.ALC_EXT_EFX
					|| !ALC10.alcIsExtensionPresent(
							device,
							EFX_EXTENSION
					)))) {
				throw new IllegalStateException(
						"required transition extensions unavailable"
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
						"could not create transition thread context"
				);
			}
			threadContextSet = true;
			AL.createCapabilities(capabilities);
			clearAlErrors();
			motorBuffer = createBuffer(motorPcm);
			propellerBuffer = createBuffer(propellerPcm);
			motorSource = createSource(motorBuffer);
			propellerSource = createSource(propellerBuffer);
			if (wetPcm != null) {
				wetBuffer = createBuffer(wetPcm);
				wetSource = createSource(wetBuffer);
			}
			if (efxEnabled) {
				effect = EXTEfx.alGenEffects();
				EXTEfx.alEffecti(
						effect,
						EXTEfx.AL_EFFECT_TYPE,
						EXTEfx.AL_EFFECT_REVERB
				);
				slot = EXTEfx.alGenAuxiliaryEffectSlots();
				motorFilter = createSendFilter();
				propellerFilter = createSendFilter();
				attachSend(motorSource, slot, motorFilter);
				attachSend(propellerSource, slot, propellerFilter);
			}
			AL10.alSourcePlay(motorSource);
			AL10.alSourcePlay(propellerSource);
			if (wetSource != 0) {
				AL10.alSourcePlay(wetSource);
			}
			short[] output = new short[RENDER_FRAMES];
			for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
				if (efxEnabled) {
					FdnEnvironmentMapper.Controls environment =
							environments.get(segment);
					OpenAlEfxController.configureReverbEffect(
							effect,
							environment
					);
					EXTEfx.alAuxiliaryEffectSloti(
							slot,
							EXTEfx.AL_EFFECTSLOT_EFFECT,
							effect
					);
					OpenAlEfxController.configureSendFilter(
							motorFilter,
							environment.rt60Seconds()
					);
					OpenAlEfxController.configureSendFilter(
							propellerFilter,
							environment.rt60Seconds()
					);
				}
				ShortBuffer rendered =
						BufferUtils.createShortBuffer(SEGMENT_FRAMES);
				SOFTLoopback.alcRenderSamplesSOFT(
						device,
						rendered,
						SEGMENT_FRAMES
				);
				for (int frame = 0;
						frame < SEGMENT_FRAMES;
						frame++) {
					output[segment * SEGMENT_FRAMES + frame] =
							rendered.get(frame);
				}
			}
			int alError = AL10.alGetError();
			int alcError = ALC10.alcGetError(device);
			if (alError != AL10.AL_NO_ERROR
					|| alcError != ALC10.ALC_NO_ERROR) {
				throw new IllegalStateException(
						"transition loopback error: al="
								+ alError + " alc=" + alcError
				);
			}
			return new Render(pcm16Le(output), alError, alcError);
		} finally {
			if (threadContextSet) {
				deleteSource(wetSource);
				deleteSource(propellerSource);
				deleteSource(motorSource);
				if (propellerFilter != 0) {
					EXTEfx.alDeleteFilters(propellerFilter);
				}
				if (motorFilter != 0) {
					EXTEfx.alDeleteFilters(motorFilter);
				}
				if (slot != 0) {
					EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
				}
				if (effect != 0) {
					EXTEfx.alDeleteEffects(effect);
				}
				deleteBuffer(wetBuffer);
				deleteBuffer(propellerBuffer);
				deleteBuffer(motorBuffer);
				AL.setCurrentThread(null);
				EXTThreadLocalContext.alcSetThreadContext(0L);
			}
			if (context != 0L) {
				ALC10.alcDestroyContext(context);
			}
			boolean closed = device == 0L || ALC10.alcCloseDevice(device);
			ALC.setCapabilities(null);
			if (!closed) {
				throw new IllegalStateException(
						"could not close transition loopback device"
				);
			}
		}
	}

	private static byte[] renderJavaWet(
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
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE_HZ);
		for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
			FdnEnvironmentMapper.Controls environment =
					environments.get(segment);
			fdn.configure(
					environment.rt60Seconds(),
					environment.wetGain(),
					segment == 0
							? 0.0
							: environment.transitionSeconds()
			);
			fdn.process(
					input,
					wet,
					segment * SEGMENT_FRAMES,
					SEGMENT_FRAMES
			);
		}
		byte[] pcm = new byte[wet.length * 2];
		for (int frame = 0; frame < wet.length; frame++) {
			int value = Math.round(
					Math.max(-1.0F, Math.min(1.0F, wet[frame]))
							* 32_767.0F
			);
			pcm[frame * 2] = (byte) value;
			pcm[frame * 2 + 1] = (byte) (value >>> 8);
		}
		return pcm;
	}

	private static List<DopplerAudioChunkTrace.Chunk> chunks(
			DopplerAudioChunkTrace.Snapshot trace,
			PhaseContinuousSynthesizer.Layer layer
	) {
		List<DopplerAudioChunkTrace.Chunk> chunks = trace.chunks().stream()
				.filter(chunk -> chunk.layer() == layer)
				.sorted(Comparator.comparingLong(
						DopplerAudioChunkTrace.Chunk::sequence
				))
				.limit(SEGMENT_COUNT)
				.toList();
		if (chunks.size() != SEGMENT_COUNT) {
			throw new IllegalArgumentException(
					"transition trace requires four " + layer + " chunks"
			);
		}
		for (DopplerAudioChunkTrace.Chunk chunk : chunks) {
			if (chunk.pcmBytes().length != SEGMENT_FRAMES * 2) {
				throw new IllegalArgumentException(
						"transition chunks must be one-second PCM16"
				);
			}
		}
		return chunks;
	}

	private static byte[] concatenate(
			List<DopplerAudioChunkTrace.Chunk> chunks
	) {
		byte[] result = new byte[RENDER_FRAMES * 2];
		int offset = 0;
		for (DopplerAudioChunkTrace.Chunk chunk : chunks) {
			byte[] pcm = chunk.pcmBytes();
			System.arraycopy(pcm, 0, result, offset, pcm.length);
			offset += pcm.length;
		}
		return result;
	}

	private static List<InputChunk> inputs(
			List<DopplerAudioChunkTrace.Chunk> motor,
			List<DopplerAudioChunkTrace.Chunk> propeller
	) {
		List<InputChunk> result = new ArrayList<>(SEGMENT_COUNT * 2);
		for (DopplerAudioChunkTrace.Chunk chunk : motor) {
			result.add(new InputChunk(
					"motor",
					chunk.sequence(),
					chunk.pcmSha256()
			));
		}
		for (DopplerAudioChunkTrace.Chunk chunk : propeller) {
			result.add(new InputChunk(
					"propeller",
					chunk.sequence(),
					chunk.pcmSha256()
			));
		}
		return List.copyOf(result);
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
		if (source != 0) {
			AL10.alSourceStop(source);
			AL10.alDeleteSources(source);
		}
	}

	private static void deleteBuffer(int buffer) {
		if (buffer != 0) {
			AL10.alDeleteBuffers(buffer);
		}
	}

	private static int pcm16At(byte[] pcm, int frame) {
		int low = pcm[frame * 2] & 0xff;
		int high = pcm[frame * 2 + 1];
		return (short) (low | (high << 8));
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

	private static void clearAlErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(bytes);
			StringBuilder result = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				result.append(String.format(
						Locale.ROOT,
						"%02x",
						value & 0xff
				));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	record InputChunk(String layer, long sequence, String pcmSha256) {
	}

	private record ContextIdentity(
			long context,
			long device,
			String threadName
	) {
	}

	private record Render(byte[] pcm, int alError, int alcError) {
		private Render {
			pcm = pcm.clone();
		}
	}

	private record PartialResult(
			ContextIdentity before,
			String workerThread,
			List<InputChunk> inputs,
			String motorPcmSha256,
			String propellerPcmSha256,
			List<FdnEnvironmentMapper.Controls> environments,
			Render dry,
			Render efx,
			Render javaFdn
	) {
		private Result finish(ContextIdentity after) {
			return new Result(
					before.context() != 0L
							&& before.context() == after.context(),
					before.device() != 0L
							&& before.device() == after.device(),
					before.threadName().equals(after.threadName()),
					before.threadName(),
					workerThread,
					inputs,
					motorPcmSha256,
					propellerPcmSha256,
					environments,
					dry.pcm(),
					efx.pcm(),
					javaFdn.pcm(),
					dry.alError(),
					dry.alcError(),
					efx.alError(),
					efx.alcError(),
					javaFdn.alError(),
					javaFdn.alcError()
			);
		}
	}

	record Result(
			boolean minecraftContextUnchanged,
			boolean minecraftDeviceUnchanged,
			boolean minecraftSoundThreadUnchanged,
			String minecraftSoundThread,
			String workerThread,
			List<InputChunk> inputs,
			String motorPcmSha256,
			String propellerPcmSha256,
			List<FdnEnvironmentMapper.Controls> environments,
			byte[] dryPcm,
			byte[] efxPcm,
			byte[] javaFdnPcm,
			int dryAlError,
			int dryAlcError,
			int efxAlError,
			int efxAlcError,
			int javaFdnAlError,
			int javaFdnAlcError
	) {
		Result {
			inputs = List.copyOf(inputs);
			environments = List.copyOf(environments);
			dryPcm = dryPcm.clone();
			efxPcm = efxPcm.clone();
			javaFdnPcm = javaFdnPcm.clone();
		}

		@Override
		public byte[] dryPcm() {
			return dryPcm.clone();
		}

		@Override
		public byte[] efxPcm() {
			return efxPcm.clone();
		}

		@Override
		public byte[] javaFdnPcm() {
			return javaFdnPcm.clone();
		}

		String dryPcmSha256() {
			return sha256(dryPcm);
		}

		String efxPcmSha256() {
			return sha256(efxPcm);
		}

		String javaFdnPcmSha256() {
			return sha256(javaFdnPcm);
		}
	}
}
