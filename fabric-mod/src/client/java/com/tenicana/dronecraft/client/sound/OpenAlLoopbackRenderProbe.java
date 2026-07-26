package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
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
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTLoopback;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders two exact production drone PCM chunks through an isolated
 * {@code ALC_SOFT_loopback} device.
 *
 * <p>The worker uses {@code ALC_EXT_thread_local_context}; it never makes the
 * loopback context current on Minecraft's sound thread and never opens a
 * capture or physical playback endpoint.</p>
 */
final class OpenAlLoopbackRenderProbe {
	static final int SAMPLE_RATE_HZ = 48_000;
	static final int SILENCE_CONTROL_FRAMES = 256;
	static final int WALL_CLOCK_HOLD_MILLIS = 50;
	private static final String LOOPBACK_EXTENSION = "ALC_SOFT_loopback";
	private static final String THREAD_CONTEXT_EXTENSION =
			"ALC_EXT_thread_local_context";

	private OpenAlLoopbackRenderProbe() {
	}

	static CompletableFuture<Result> capture(
			SoundManager soundManager,
			DopplerAudioChunkTrace.Snapshot trace
	) {
		Objects.requireNonNull(soundManager, "soundManager");
		Objects.requireNonNull(trace, "trace");
		DopplerAudioChunkTrace.Chunk motor = firstChunk(
				trace.chunks(),
				PhaseContinuousSynthesizer.Layer.MOTOR
		);
		DopplerAudioChunkTrace.Chunk propeller = firstChunk(
				trace.chunks(),
				PhaseContinuousSynthesizer.Layer.PROPELLER
		);
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		SoundEngineExecutor soundExecutor =
				((SoundEngineExecutorAccessor) soundEngine)
						.fpvdrone$getExecutor();
		ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(
					task,
					"Dronecraft OpenAL loopback probe"
			);
			thread.setDaemon(true);
			return thread;
		});
		CompletableFuture<Result> result = captureMinecraftContext(soundExecutor)
				.thenCompose(before -> CompletableFuture.supplyAsync(
						() -> render(before, motor, propeller),
						worker
				))
				.thenCompose(partial -> captureMinecraftContext(soundExecutor)
						.thenApply(after -> partial.finish(after)));
		return result.whenComplete((ignored, error) -> worker.shutdown());
	}

	private static CompletableFuture<ContextIdentity> captureMinecraftContext(
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
			DopplerAudioChunkTrace.Chunk propeller
	) {
		long device = 0L;
		long context = 0L;
		int motorBuffer = 0;
		int propellerBuffer = 0;
		int motorSource = 0;
		int propellerSource = 0;
		boolean threadContextSet = false;
		boolean contextDestroyed = false;
		boolean deviceClosed = false;
		int alError = AL10.AL_NO_ERROR;
		int alcError = ALC10.ALC_NO_ERROR;
		try {
			device = SOFTLoopback.alcLoopbackOpenDeviceSOFT(
					(CharSequence) null
			);
			if (device == 0L) {
				throw new IllegalStateException(
						"ALC_SOFT_loopback could not open a device"
				);
			}
			ALCCapabilities alcCapabilities =
					ALC.createCapabilities(device);
			boolean loopbackSupported =
					alcCapabilities.ALC_SOFT_loopback
							&& ALC10.alcIsExtensionPresent(
									device,
									LOOPBACK_EXTENSION
							);
			boolean threadContextSupported =
					alcCapabilities.ALC_EXT_thread_local_context
							&& ALC10.alcIsExtensionPresent(
									device,
									THREAD_CONTEXT_EXTENSION
							);
			if (!loopbackSupported || !threadContextSupported) {
				throw new IllegalStateException(
						"loopback or thread-local context extension unavailable"
				);
			}
			boolean formatSupported =
					SOFTLoopback.alcIsRenderFormatSupportedSOFT(
							device,
							SAMPLE_RATE_HZ,
							SOFTLoopback.ALC_MONO_SOFT,
							SOFTLoopback.ALC_SHORT_SOFT
					);
			if (!formatSupported) {
				throw new IllegalStateException(
						"48 kHz mono signed-short loopback is unsupported"
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
						"could not create a thread-local loopback context"
				);
			}
			threadContextSet = true;
			AL.createCapabilities(alcCapabilities);
			clearAlErrors();

			ShortBuffer silence = BufferUtils.createShortBuffer(
					SILENCE_CONTROL_FRAMES
			);
			for (int frame = 0;
					frame < SILENCE_CONTROL_FRAMES;
					frame++) {
				silence.put((short) 0x5a5a);
			}
			silence.flip();
			SOFTLoopback.alcRenderSamplesSOFT(
					device,
					silence,
					SILENCE_CONTROL_FRAMES
			);
			int silenceNonzero = countNonzero(
					silence,
					SILENCE_CONTROL_FRAMES
			);
			int silencePeak = peakAbsolute(
					silence,
					SILENCE_CONTROL_FRAMES
			);

			byte[] motorPcm = motor.pcmBytes();
			byte[] propellerPcm = propeller.pcmBytes();
			int renderFrames = requireMatchingFrames(
					motorPcm,
					propellerPcm
			);
			int midpointFrames = renderFrames / 2;
			motorBuffer = AL10.alGenBuffers();
			propellerBuffer = AL10.alGenBuffers();
			motorSource = AL10.alGenSources();
			propellerSource = AL10.alGenSources();
			upload(motorBuffer, motorPcm);
			upload(propellerBuffer, propellerPcm);
			AL10.alSourcei(motorSource, AL10.AL_BUFFER, motorBuffer);
			AL10.alSourcei(
					propellerSource,
					AL10.AL_BUFFER,
					propellerBuffer
			);
			AL10.alSourcePlay(motorSource);
			AL10.alSourcePlay(propellerSource);
			int motorOffsetBeforeHold =
					AL10.alGetSourcei(motorSource, AL11.AL_SAMPLE_OFFSET);
			int propellerOffsetBeforeHold =
					AL10.alGetSourcei(
							propellerSource,
							AL11.AL_SAMPLE_OFFSET
					);
			try {
				Thread.sleep(WALL_CLOCK_HOLD_MILLIS);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"loopback wall-clock hold interrupted",
						error
				);
			}
			int motorOffsetAfterHold =
					AL10.alGetSourcei(motorSource, AL11.AL_SAMPLE_OFFSET);
			int propellerOffsetAfterHold =
					AL10.alGetSourcei(
							propellerSource,
							AL11.AL_SAMPLE_OFFSET
					);

			ShortBuffer firstHalf =
					BufferUtils.createShortBuffer(midpointFrames);
			SOFTLoopback.alcRenderSamplesSOFT(
					device,
					firstHalf,
					midpointFrames
			);
			int motorMidpointOffset =
					AL10.alGetSourcei(motorSource, AL11.AL_SAMPLE_OFFSET);
			int propellerMidpointOffset =
					AL10.alGetSourcei(
							propellerSource,
							AL11.AL_SAMPLE_OFFSET
					);
			int motorMidpointState =
					AL10.alGetSourcei(motorSource, AL10.AL_SOURCE_STATE);
			int propellerMidpointState =
					AL10.alGetSourcei(
							propellerSource,
							AL10.AL_SOURCE_STATE
					);
			ShortBuffer secondHalf = BufferUtils.createShortBuffer(
					renderFrames - midpointFrames
			);
			SOFTLoopback.alcRenderSamplesSOFT(
					device,
					secondHalf,
					renderFrames - midpointFrames
			);
			int motorFinalState =
					AL10.alGetSourcei(motorSource, AL10.AL_SOURCE_STATE);
			int propellerFinalState =
					AL10.alGetSourcei(
							propellerSource,
							AL10.AL_SOURCE_STATE
					);
			short[] output = concatenate(
					firstHalf,
					midpointFrames,
					secondHalf,
					renderFrames - midpointFrames
			);
			byte[] outputPcm = pcm16Le(output);
			SignalMetrics metrics = measure(
					output,
					motorPcm,
					propellerPcm
			);
			alError = AL10.alGetError();
			alcError = ALC10.alcGetError(device);
			if (silencePeak > 1
					|| motorOffsetBeforeHold != 0
					|| propellerOffsetBeforeHold != 0
					|| motorOffsetAfterHold != 0
					|| propellerOffsetAfterHold != 0
					|| alError != AL10.AL_NO_ERROR
					|| alcError != ALC10.ALC_NO_ERROR) {
				throw new IllegalStateException(
						"loopback control or error gate failed: silence="
								+ silenceNonzero
								+ " peak=" + silencePeak
								+ " offsets="
								+ motorOffsetBeforeHold + "/"
								+ propellerOffsetBeforeHold + " -> "
								+ motorOffsetAfterHold + "/"
								+ propellerOffsetAfterHold
								+ " al=" + alError
								+ " alc=" + alcError
				);
			}
			return new PartialResult(
					before,
					Thread.currentThread().getName(),
					loopbackSupported,
					threadContextSupported,
					formatSupported,
					motor.sequence(),
					motor.pcmSha256(),
					motorPcm.length,
					propeller.sequence(),
					propeller.pcmSha256(),
					propellerPcm.length,
					renderFrames,
					silenceNonzero,
					silencePeak,
					motorOffsetBeforeHold,
					propellerOffsetBeforeHold,
					motorOffsetAfterHold,
					propellerOffsetAfterHold,
					motorMidpointOffset,
					propellerMidpointOffset,
					motorMidpointState,
					propellerMidpointState,
					motorFinalState,
					propellerFinalState,
					metrics,
					outputPcm,
					alError,
					alcError
			);
		} finally {
			if (threadContextSet) {
				if (motorSource != 0) {
					AL10.alSourceStop(motorSource);
					AL10.alDeleteSources(motorSource);
				}
				if (propellerSource != 0) {
					AL10.alSourceStop(propellerSource);
					AL10.alDeleteSources(propellerSource);
				}
				if (motorBuffer != 0) {
					AL10.alDeleteBuffers(motorBuffer);
				}
				if (propellerBuffer != 0) {
					AL10.alDeleteBuffers(propellerBuffer);
				}
				AL.setCurrentThread(null);
				EXTThreadLocalContext.alcSetThreadContext(0L);
			}
			if (context != 0L) {
				ALC10.alcDestroyContext(context);
				contextDestroyed = true;
			}
			if (device != 0L) {
				deviceClosed = ALC10.alcCloseDevice(device);
			}
			ALC.setCapabilities(null);
			if (context != 0L && (!contextDestroyed || !deviceClosed)) {
				throw new IllegalStateException(
						"loopback context or device cleanup failed"
				);
			}
		}
	}

	private static DopplerAudioChunkTrace.Chunk firstChunk(
			List<DopplerAudioChunkTrace.Chunk> chunks,
			PhaseContinuousSynthesizer.Layer layer
	) {
		return chunks.stream()
				.filter(chunk -> chunk.layer() == layer)
				.min(java.util.Comparator.comparingLong(
						DopplerAudioChunkTrace.Chunk::sequence
				))
				.orElseThrow(() -> new IllegalArgumentException(
						"trace has no " + layer + " chunk"
				));
	}

	private static int requireMatchingFrames(
			byte[] motor,
			byte[] propeller
	) {
		if (motor.length == 0
				|| motor.length != propeller.length
				|| (motor.length & 1) != 0) {
			throw new IllegalArgumentException(
					"production chunks must be equal non-empty PCM16"
			);
		}
		return motor.length / 2;
	}

	private static void upload(int buffer, byte[] pcm) {
		ByteBuffer direct = BufferUtils.createByteBuffer(pcm.length);
		direct.put(pcm).flip();
		AL10.alBufferData(
				buffer,
				AL10.AL_FORMAT_MONO16,
				direct,
				SAMPLE_RATE_HZ
		);
	}

	private static short[] concatenate(
			ShortBuffer first,
			int firstFrames,
			ShortBuffer second,
			int secondFrames
	) {
		short[] output = new short[firstFrames + secondFrames];
		for (int index = 0; index < firstFrames; index++) {
			output[index] = first.get(index);
		}
		for (int index = 0; index < secondFrames; index++) {
			output[firstFrames + index] = second.get(index);
		}
		return output;
	}

	private static int countNonzero(ShortBuffer samples, int frames) {
		int count = 0;
		for (int index = 0; index < frames; index++) {
			if (samples.get(index) != 0) {
				count++;
			}
		}
		return count;
	}

	private static int peakAbsolute(ShortBuffer samples, int frames) {
		int peak = 0;
		for (int index = 0; index < frames; index++) {
			peak = Math.max(peak, Math.abs(samples.get(index)));
		}
		return peak;
	}

	private static byte[] pcm16Le(short[] samples) {
		byte[] bytes = new byte[samples.length * 2];
		for (int index = 0; index < samples.length; index++) {
			int value = samples[index] & 0xffff;
			bytes[index * 2] = (byte) value;
			bytes[index * 2 + 1] = (byte) (value >>> 8);
		}
		return bytes;
	}

	private static SignalMetrics measure(
			short[] output,
			byte[] motor,
			byte[] propeller
	) {
		long nonzero = 0L;
		int peak = 0;
		double energy = 0.0;
		int[] expected = new int[output.length];
		for (int index = 0; index < output.length; index++) {
			int rendered = output[index];
			expected[index] = pcm16At(motor, index)
					+ pcm16At(propeller, index);
			if (rendered != 0) {
				nonzero++;
			}
			peak = Math.max(peak, Math.abs(rendered));
			energy += (double) rendered * rendered;
		}
		LagCorrelation aligned = bestLagCorrelation(
				output,
				expected,
				1_024
		);
		return new SignalMetrics(
				nonzero,
				peak,
				Math.sqrt(energy / output.length),
				aligned.correlation(),
				aligned.lagSamples()
		);
	}

	private static LagCorrelation bestLagCorrelation(
			short[] output,
			int[] expected,
			int maximumAbsoluteLag
	) {
		double best = -1.0;
		int bestLag = 0;
		for (int lag = -maximumAbsoluteLag;
				lag <= maximumAbsoluteLag;
				lag++) {
			int outputStart = Math.max(0, lag);
			int expectedStart = Math.max(0, -lag);
			int length = output.length - Math.abs(lag);
			double outputEnergy = 0.0;
			double expectedEnergy = 0.0;
			double dot = 0.0;
			for (int index = 0; index < length; index++) {
				int rendered = output[outputStart + index];
				int source = expected[expectedStart + index];
				outputEnergy += (double) rendered * rendered;
				expectedEnergy += (double) source * source;
				dot += (double) rendered * source;
			}
			double correlation =
					outputEnergy == 0.0 || expectedEnergy == 0.0
							? 0.0
							: dot / Math.sqrt(
									outputEnergy * expectedEnergy
							);
			if (correlation > best) {
				best = correlation;
				bestLag = lag;
			}
		}
		return new LagCorrelation(best, bestLag);
	}

	private static int pcm16At(byte[] pcm, int index) {
		int low = pcm[index * 2] & 0xff;
		int high = pcm[index * 2 + 1];
		return (short) (low | (high << 8));
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

	private record SignalMetrics(
			long nonzeroSamples,
			int peakAbsoluteSample,
			double rms,
			double summedInputCorrelation,
			int summedInputLagSamples
	) {
	}

	private record LagCorrelation(
			double correlation,
			int lagSamples
	) {
	}

	private record PartialResult(
			ContextIdentity before,
			String workerThread,
			boolean loopbackSupported,
			boolean threadContextSupported,
			boolean formatSupported,
			long motorSequence,
			String motorPcmSha256,
			int motorPcmBytes,
			long propellerSequence,
			String propellerPcmSha256,
			int propellerPcmBytes,
			int renderFrames,
			int silenceNonzeroSamples,
			int silencePeakAbsoluteSample,
			int motorOffsetBeforeHold,
			int propellerOffsetBeforeHold,
			int motorOffsetAfterHold,
			int propellerOffsetAfterHold,
			int motorMidpointOffset,
			int propellerMidpointOffset,
			int motorMidpointState,
			int propellerMidpointState,
			int motorFinalState,
			int propellerFinalState,
			SignalMetrics metrics,
			byte[] renderedPcm,
			int alError,
			int alcError
	) {
		Result finish(ContextIdentity after) {
			return new Result(
					before.context() != 0L
							&& before.context() == after.context(),
					before.device() != 0L
							&& before.device() == after.device(),
					before.threadName().equals(after.threadName()),
					before.threadName(),
					workerThread,
					loopbackSupported,
					threadContextSupported,
					formatSupported,
					motorSequence,
					motorPcmSha256,
					motorPcmBytes,
					propellerSequence,
					propellerPcmSha256,
					propellerPcmBytes,
					renderFrames,
					silenceNonzeroSamples,
					silencePeakAbsoluteSample,
					motorOffsetBeforeHold,
					propellerOffsetBeforeHold,
					motorOffsetAfterHold,
					propellerOffsetAfterHold,
					motorMidpointOffset,
					propellerMidpointOffset,
					motorMidpointState,
					propellerMidpointState,
					motorFinalState,
					propellerFinalState,
					metrics.nonzeroSamples(),
					metrics.peakAbsoluteSample(),
					metrics.rms(),
					metrics.summedInputCorrelation(),
					metrics.summedInputLagSamples(),
					renderedPcm,
					alError,
					alcError
			);
		}
	}

	record Result(
			boolean minecraftContextUnchanged,
			boolean minecraftDeviceUnchanged,
			boolean minecraftSoundThreadUnchanged,
			String minecraftSoundThread,
			String workerThread,
			boolean loopbackSupported,
			boolean threadContextSupported,
			boolean formatSupported,
			long motorSequence,
			String motorPcmSha256,
			int motorPcmBytes,
			long propellerSequence,
			String propellerPcmSha256,
			int propellerPcmBytes,
			int renderFrames,
			int silenceNonzeroSamples,
			int silencePeakAbsoluteSample,
			int motorOffsetBeforeHold,
			int propellerOffsetBeforeHold,
			int motorOffsetAfterHold,
			int propellerOffsetAfterHold,
			int motorMidpointOffset,
			int propellerMidpointOffset,
			int motorMidpointState,
			int propellerMidpointState,
			int motorFinalState,
			int propellerFinalState,
			long renderedNonzeroSamples,
			int renderedPeakAbsoluteSample,
			double renderedRms,
			double summedInputCorrelation,
			int summedInputLagSamples,
			byte[] renderedPcm,
			int alError,
			int alcError
	) {
		Result {
			renderedPcm = renderedPcm.clone();
		}

		@Override
		public byte[] renderedPcm() {
			return renderedPcm.clone();
		}

		String renderedPcmSha256() {
			return sha256(renderedPcm);
		}
	}
}
