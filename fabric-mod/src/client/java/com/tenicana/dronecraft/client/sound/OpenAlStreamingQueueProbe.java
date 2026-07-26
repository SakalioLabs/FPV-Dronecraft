package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.SOFTSourceLatency;
import org.lwjgl.openal.SOFTEvents;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Diagnostic-only binding between bytes returned by a procedural stream and
 * the OpenAL buffer id Minecraft subsequently queues for those bytes.
 */
public final class OpenAlStreamingQueueProbe {
	private static final Object LOCK = new Object();
	private static final String SOURCE_LATENCY_EXTENSION =
			"AL_SOFT_source_latency";
	private static final String EVENTS_EXTENSION = "AL_SOFT_events";
	private static final char[] HEX =
			"0123456789abcdef".toCharArray();
	private static final Map<AudioStream, ArrayDeque<PendingBuffer>> PENDING =
			new IdentityHashMap<>();
	private static final Map<PhaseContinuousSynthesizer.Layer, Integer>
			LAYER_COUNTS = new EnumMap<>(
					PhaseContinuousSynthesizer.Layer.class
			);
	private static final List<QueueEvent> EVENTS = new ArrayList<>();
	private static volatile boolean enabled;
	private static int targetEventsPerLayer;
	private static long sequence;

	private OpenAlStreamingQueueProbe() {
	}

	static void begin(int eventsPerLayer) {
		if (eventsPerLayer < 4 || eventsPerLayer > 32) {
			throw new IllegalArgumentException(
					"eventsPerLayer must be in [4, 32]"
			);
		}
		synchronized (LOCK) {
			PENDING.clear();
			LAYER_COUNTS.clear();
			EVENTS.clear();
			targetEventsPerLayer = eventsPerLayer;
			sequence = 0L;
			enabled = true;
		}
	}

	static boolean active() {
		return enabled;
	}

	static boolean ready() {
		synchronized (LOCK) {
			return layerReady(PhaseContinuousSynthesizer.Layer.MOTOR)
					&& layerReady(
							PhaseContinuousSynthesizer.Layer.PROPELLER
					);
		}
	}

	static Snapshot finish() {
		synchronized (LOCK) {
			enabled = false;
			if (!ready()) {
				throw new IllegalStateException(
						"OpenAL streaming queue probe is incomplete"
				);
			}
			PENDING.clear();
			return new Snapshot(
					targetEventsPerLayer,
					List.copyOf(EVENTS)
			);
		}
	}

	static void abort() {
		synchronized (LOCK) {
			enabled = false;
			PENDING.clear();
			LAYER_COUNTS.clear();
			EVENTS.clear();
		}
	}

	static void onPcmRead(
			AudioStream stream,
			PhaseContinuousSynthesizer.Layer layer,
			DroneAcousticRenderState.AudioSnapshot snapshot,
			ByteBuffer pcm
	) {
		if (!enabled) {
			return;
		}
		Objects.requireNonNull(stream, "stream");
		Objects.requireNonNull(layer, "layer");
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(pcm, "pcm");
		synchronized (LOCK) {
			if (!enabled || !isProductLayer(layer)) {
				return;
			}
			int layerCount = LAYER_COUNTS.getOrDefault(layer, 0);
			if (layerCount >= targetEventsPerLayer + 8) {
				return;
			}
			ByteBuffer copy = pcm.asReadOnlyBuffer();
			byte[] bytes = new byte[copy.remaining()];
			copy.get(bytes);
			PENDING.computeIfAbsent(
					stream,
					ignored -> new ArrayDeque<>()
			).addLast(new PendingBuffer(
					layer,
					snapshot.source().entityId(),
					snapshot.source().simulationTimeNanos(),
					snapshot.dopplerFrequencyRatio(),
					bytes.length,
					sha256(bytes)
			));
		}
	}

	/**
	 * Called by the Channel mixin immediately after Minecraft queues a buffer.
	 */
	public static void onBufferQueued(
			AudioStream stream,
			int source,
			int buffer
	) {
		if (!enabled) {
			return;
		}
		PendingBuffer pending;
		synchronized (LOCK) {
			if (!enabled || stream == null) {
				return;
			}
			ArrayDeque<PendingBuffer> buffers = PENDING.get(stream);
			pending = buffers == null ? null : buffers.pollFirst();
			if (buffers != null && buffers.isEmpty()) {
				PENDING.remove(stream);
			}
			if (pending == null) {
				return;
			}
		}

		QueueState state = captureQueueState(source, buffer);
		synchronized (LOCK) {
			if (!enabled) {
				return;
			}
			int layerSequence = LAYER_COUNTS.getOrDefault(
					pending.layer(),
					0
			);
			if (layerSequence >= targetEventsPerLayer + 8) {
				return;
			}
			EVENTS.add(new QueueEvent(
					sequence++,
					layerSequence,
					pending.layer(),
					pending.entityId(),
					pending.simulationTimeNanos(),
					pending.dopplerFrequencyRatio(),
					pending.pcmBytes(),
					pending.pcmSha256(),
					source,
					buffer,
					Thread.currentThread().getName(),
					state
			));
			LAYER_COUNTS.put(pending.layer(), layerSequence + 1);
		}
	}

	private static QueueState captureQueueState(int source, int buffer) {
		long hostMonotonicNs = System.nanoTime();
		clearAlErrors();
		boolean bufferValid = AL10.alIsBuffer(buffer);
		int bufferFrequency = bufferValid
				? AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY)
				: 0;
		int bufferBits = bufferValid
				? AL10.alGetBufferi(buffer, AL10.AL_BITS)
				: 0;
		int bufferChannels = bufferValid
				? AL10.alGetBufferi(buffer, AL10.AL_CHANNELS)
				: 0;
		int bufferBytes = bufferValid
				? AL10.alGetBufferi(buffer, AL10.AL_SIZE)
				: 0;
		int sourceState = AL10.alGetSourcei(
				source,
				AL10.AL_SOURCE_STATE
		);
		int sourceType = AL10.alGetSourcei(
				source,
				AL10.AL_SOURCE_TYPE
		);
		int queued = AL10.alGetSourcei(
				source,
				AL10.AL_BUFFERS_QUEUED
		);
		int processed = AL10.alGetSourcei(
				source,
				AL10.AL_BUFFERS_PROCESSED
		);
		int sampleOffset = AL10.alGetSourcei(
				source,
				AL11.AL_SAMPLE_OFFSET
		);
		boolean sourceLatencySupported =
				AL10.alIsExtensionPresent(SOURCE_LATENCY_EXTENSION)
						&& AL.getCapabilities().AL_SOFT_source_latency;
		boolean eventsSupported =
				AL10.alIsExtensionPresent(EVENTS_EXTENSION)
						&& AL.getCapabilities().AL_SOFT_events;
		boolean eventCallbackRegistered = eventsSupported
				&& SOFTEvents.alGetPointerSOFT(
						SOFTEvents.AL_EVENT_CALLBACK_FUNCTION_SOFT
				) != 0L;
		double sourceOffsetSeconds = 0.0;
		double sourceLatencySeconds = 0.0;
		if (sourceLatencySupported) {
			double[] offsetAndLatency = new double[2];
			SOFTSourceLatency.alGetSourcedvSOFT(
					source,
					SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT,
					offsetAndLatency
			);
			sourceOffsetSeconds = offsetAndLatency[0];
			sourceLatencySeconds = offsetAndLatency[1];
		}
		int alError = AL10.alGetError();
		return new QueueState(
				hostMonotonicNs,
				bufferValid,
				bufferFrequency,
				bufferBits,
				bufferChannels,
				bufferBytes,
				sourceState,
				sourceType,
				queued,
				processed,
				sampleOffset,
				sourceLatencySupported,
				sourceOffsetSeconds,
				sourceLatencySeconds,
				eventsSupported,
				eventCallbackRegistered,
				alError
		);
	}

	private static void clearAlErrors() {
		for (int index = 0; index < 8; index++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	private static boolean layerReady(
			PhaseContinuousSynthesizer.Layer layer
	) {
		return LAYER_COUNTS.getOrDefault(layer, 0)
				>= targetEventsPerLayer;
	}

	private static boolean isProductLayer(
			PhaseContinuousSynthesizer.Layer layer
	) {
		return layer == PhaseContinuousSynthesizer.Layer.MOTOR
				|| layer == PhaseContinuousSynthesizer.Layer.PROPELLER;
	}

	private static String sha256(byte[] bytes) {
		try {
			return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static String hex(byte[] bytes) {
		char[] output = new char[bytes.length * 2];
		for (int index = 0; index < bytes.length; index++) {
			int value = bytes[index] & 0xff;
			output[index * 2] = HEX[value >>> 4];
			output[index * 2 + 1] = HEX[value & 0x0f];
		}
		return new String(output);
	}

	record Snapshot(int targetEventsPerLayer, List<QueueEvent> events) {
		Snapshot {
			events = List.copyOf(events);
		}
	}

	record QueueEvent(
			long sequence,
			int layerSequence,
			PhaseContinuousSynthesizer.Layer layer,
			long entityId,
			long simulationTimeNanos,
			double dopplerFrequencyRatio,
			int pcmBytes,
			String pcmSha256,
			int source,
			int buffer,
			String threadName,
			QueueState state
	) {
	}

	record QueueState(
			long hostMonotonicNs,
			boolean bufferValid,
			int bufferFrequency,
			int bufferBits,
			int bufferChannels,
			int bufferBytes,
			int sourceState,
			int sourceType,
			int buffersQueued,
			int buffersProcessed,
			int sampleOffset,
			boolean sourceLatencySupported,
			double sourceOffsetSeconds,
			double sourceLatencySeconds,
			boolean eventsSupported,
			boolean eventCallbackRegistered,
			int alError
	) {
	}

	private record PendingBuffer(
			PhaseContinuousSynthesizer.Layer layer,
			long entityId,
			long simulationTimeNanos,
			double dopplerFrequencyRatio,
			int pcmBytes,
			String pcmSha256
	) {
	}
}
