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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controlled transfer comparison for the two production reverb backends.
 *
 * <p>The exact first motor and propeller chunks from the production trace are
 * rendered as dry, OpenAL EFX, and dry plus the actual Java shared FDN. Every
 * render owns an isolated loopback device and thread-local context; no capture
 * endpoint or physical playback device is opened.</p>
 */
final class OpenAlBackendTransferProbe {
	static final int SAMPLE_RATE_HZ = 48_000;
	static final int INPUT_FRAMES = 48_000;
	static final int TAIL_FRAMES = 144_000;
	static final int RENDER_FRAMES = INPUT_FRAMES + TAIL_FRAMES;
	private static final String EFX_EXTENSION = "ALC_EXT_EFX";

	private OpenAlBackendTransferProbe() {
	}

	static CompletableFuture<Result> capture(
			SoundManager soundManager,
			DopplerAudioChunkTrace.Snapshot trace,
			FdnEnvironmentMapper.Controls environment
	) {
		Objects.requireNonNull(soundManager, "soundManager");
		Objects.requireNonNull(trace, "trace");
		Objects.requireNonNull(environment, "environment");
		DopplerAudioChunkTrace.Chunk motor = firstChunk(
				trace.chunks(),
				PhaseContinuousSynthesizer.Layer.MOTOR
		);
		DopplerAudioChunkTrace.Chunk propeller = firstChunk(
				trace.chunks(),
				PhaseContinuousSynthesizer.Layer.PROPELLER
		);
		requireInput(motor.pcmBytes(), propeller.pcmBytes());
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		SoundEngineExecutor soundExecutor =
				((SoundEngineExecutorAccessor) soundEngine)
						.fpvdrone$getExecutor();
		ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(
					task,
					"Dronecraft backend transfer probe"
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
								environment
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
			DopplerAudioChunkTrace.Chunk motor,
			DopplerAudioChunkTrace.Chunk propeller,
			FdnEnvironmentMapper.Controls environment
	) {
		byte[] motorPcm = motor.pcmBytes();
		byte[] propellerPcm = propeller.pcmBytes();
		byte[] wetPcm = renderJavaFdnWet(
				motorPcm,
				propellerPcm,
				environment
		);
		Render dry = renderLoopback(
				motorPcm,
				propellerPcm,
				null,
				environment,
				false
		);
		Render efx = renderLoopback(
				motorPcm,
				propellerPcm,
				null,
				environment,
				true
		);
		Render javaFdn = renderLoopback(
				motorPcm,
				propellerPcm,
				wetPcm,
				environment,
				false
		);
		return new PartialResult(
				before,
				Thread.currentThread().getName(),
				motor.sequence(),
				motor.pcmSha256(),
				propeller.sequence(),
				propeller.pcmSha256(),
				environment,
				dry,
				efx,
				javaFdn
		);
	}

	private static Render renderLoopback(
			byte[] motorPcm,
			byte[] propellerPcm,
			byte[] wetPcm,
			FdnEnvironmentMapper.Controls environment,
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
						"could not open isolated loopback device"
				);
			}
			ALCCapabilities capabilities = ALC.createCapabilities(device);
			if (!capabilities.ALC_SOFT_loopback
					|| !capabilities.ALC_EXT_thread_local_context) {
				throw new IllegalStateException(
						"required isolated loopback extensions unavailable"
				);
			}
			if (efxEnabled && (!capabilities.ALC_EXT_EFX
					|| !ALC10.alcIsExtensionPresent(
							device,
							EFX_EXTENSION
					))) {
				throw new IllegalStateException(
						"ALC_EXT_EFX unavailable on loopback device"
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
						"could not make loopback context thread-local"
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
				OpenAlEfxController.configureReverbEffect(
						effect,
						environment
				);
				slot = EXTEfx.alGenAuxiliaryEffectSlots();
				EXTEfx.alAuxiliaryEffectSloti(
						slot,
						EXTEfx.AL_EFFECTSLOT_EFFECT,
						effect
				);
				motorFilter = createSendFilter(
						environment.rt60Seconds()
				);
				propellerFilter = createSendFilter(
						environment.rt60Seconds()
				);
				attachSend(motorSource, slot, motorFilter);
				attachSend(propellerSource, slot, propellerFilter);
			}
			AL10.alSourcePlay(motorSource);
			AL10.alSourcePlay(propellerSource);
			if (wetSource != 0) {
				AL10.alSourcePlay(wetSource);
			}
			ShortBuffer rendered =
					BufferUtils.createShortBuffer(RENDER_FRAMES);
			SOFTLoopback.alcRenderSamplesSOFT(
					device,
					rendered,
					RENDER_FRAMES
			);
			byte[] pcm = pcm16Le(rendered, RENDER_FRAMES);
			int alError = AL10.alGetError();
			int alcError = ALC10.alcGetError(device);
			if (alError != AL10.AL_NO_ERROR
					|| alcError != ALC10.ALC_NO_ERROR) {
				throw new IllegalStateException(
						"loopback backend render error: al="
								+ alError + " alc=" + alcError
				);
			}
			return new Render(pcm, alError, alcError);
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
						"could not close loopback device"
				);
			}
		}
	}

	private static byte[] renderJavaFdnWet(
			byte[] motorPcm,
			byte[] propellerPcm,
			FdnEnvironmentMapper.Controls environment
	) {
		float[] input = new float[RENDER_FRAMES];
		for (int frame = 0; frame < INPUT_FRAMES; frame++) {
			double mixed = pcm16At(motorPcm, frame) / 32_768.0
					+ pcm16At(propellerPcm, frame) / 32_768.0;
			input[frame] = (float) (mixed / (1.0 + Math.abs(mixed)));
		}
		float[] wet = new float[RENDER_FRAMES];
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE_HZ);
		fdn.configure(
				environment.rt60Seconds(),
				environment.wetGain(),
				0.0
		);
		fdn.process(input, wet, 0, wet.length);
		byte[] pcm = new byte[wet.length * 2];
		for (int frame = 0; frame < wet.length; frame++) {
			int value = (int) Math.round(
					Math.max(-1.0, Math.min(1.0, wet[frame]))
							* 32_767.0
			);
			pcm[frame * 2] = (byte) value;
			pcm[frame * 2 + 1] = (byte) (value >>> 8);
		}
		return pcm;
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

	private static int createSendFilter(
			com.tenicana.dronecraft.acoustics.AcousticBands rt60
	) {
		int filter = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(
				filter,
				EXTEfx.AL_FILTER_TYPE,
				EXTEfx.AL_FILTER_LOWPASS
		);
		OpenAlEfxController.configureSendFilter(filter, rt60);
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

	private static byte[] pcm16Le(ShortBuffer samples, int frames) {
		byte[] pcm = new byte[frames * 2];
		for (int frame = 0; frame < frames; frame++) {
			int value = samples.get(frame) & 0xffff;
			pcm[frame * 2] = (byte) value;
			pcm[frame * 2 + 1] = (byte) (value >>> 8);
		}
		return pcm;
	}

	private static int pcm16At(byte[] pcm, int frame) {
		int low = pcm[frame * 2] & 0xff;
		int high = pcm[frame * 2 + 1];
		return (short) (low | (high << 8));
	}

	private static void requireInput(byte[] motor, byte[] propeller) {
		int requiredBytes = INPUT_FRAMES * 2;
		if (motor.length != requiredBytes
				|| propeller.length != requiredBytes) {
			throw new IllegalArgumentException(
					"backend transfer requires two exact one-second chunks"
			);
		}
	}

	private static DopplerAudioChunkTrace.Chunk firstChunk(
			List<DopplerAudioChunkTrace.Chunk> chunks,
			PhaseContinuousSynthesizer.Layer layer
	) {
		return chunks.stream()
				.filter(chunk -> chunk.layer() == layer)
				.min(Comparator.comparingLong(
						DopplerAudioChunkTrace.Chunk::sequence
				))
				.orElseThrow(() -> new IllegalArgumentException(
						"trace has no " + layer + " chunk"
				));
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
			long motorSequence,
			String motorPcmSha256,
			long propellerSequence,
			String propellerPcmSha256,
			FdnEnvironmentMapper.Controls environment,
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
					motorSequence,
					motorPcmSha256,
					propellerSequence,
					propellerPcmSha256,
					environment,
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
			long motorSequence,
			String motorPcmSha256,
			long propellerSequence,
			String propellerPcmSha256,
			FdnEnvironmentMapper.Controls environment,
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
