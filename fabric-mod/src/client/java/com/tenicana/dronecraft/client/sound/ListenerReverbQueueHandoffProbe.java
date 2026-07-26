package com.tenicana.dronecraft.client.sound;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Development-only D108 probe binding listener wet PCM to the native OpenAL
 * source and buffer Minecraft queues after an EFX fault.
 */
public final class ListenerReverbQueueHandoffProbe {
	private static final Object LOCK = new Object();
	private static final int INITIAL_QUEUE_BUFFERS = 4;
	private static final List<MutableCycle> CYCLES = new ArrayList<>();
	private static final Map<AudioStream, ArrayDeque<PendingPcm>> PENDING =
			new IdentityHashMap<>();
	private static final Map<AudioStream, Long> STREAM_SEQUENCES =
			new IdentityHashMap<>();
	private static boolean enabled;
	private static long nextStreamSequence;
	private static MutableCycle current;

	private ListenerReverbQueueHandoffProbe() {
	}

	static void begin() {
		synchronized (LOCK) {
			enabled = true;
			CYCLES.clear();
			PENDING.clear();
			STREAM_SEQUENCES.clear();
			nextStreamSequence = 1L;
			current = null;
		}
	}

	static void arm(String stage, int historyFrames) {
		synchronized (LOCK) {
			if (!enabled) {
				throw new IllegalStateException("D108 probe is not active");
			}
			if (current != null && !current.complete()) {
				throw new IllegalStateException(
						"preceding D108 cycle is incomplete"
				);
			}
			current = new MutableCycle(
					stage,
					System.nanoTime(),
					historyFrames
			);
			CYCLES.add(current);
		}
	}

	static void onFallbackRequested(
			ListenerReverbHistoryProducer.Diagnostics diagnostics
	) {
		synchronized (LOCK) {
			if (!enabled || current == null
					|| current.fallbackRequestedNanos != 0L) {
				return;
			}
			current.fallbackRequestedNanos = System.nanoTime();
			current.historyFramesAtFallback = diagnostics.historyFrames();
			current.synthesizersAtFallback =
					diagnostics.synthesizerCount();
			current.shadowActiveAtFallback = diagnostics.shadowActive();
			current.wetActiveAtFallback = diagnostics.wetActive();
		}
	}

	static void onPcmRead(
			ListenerReverbAudioStream stream,
			ByteBuffer pcm,
			int prerollFrames,
			long prerollNanos
	) {
		synchronized (LOCK) {
			if (!enabled || current == null
					|| current.fallbackRequestedNanos == 0L
					|| current.generatedPcmCount
					>= INITIAL_QUEUE_BUFFERS) {
				return;
			}
			ByteBuffer copy = pcm.asReadOnlyBuffer();
			byte[] bytes = new byte[copy.remaining()];
			copy.get(bytes);
			long streamSequence = STREAM_SEQUENCES.computeIfAbsent(
					stream,
					ignored -> nextStreamSequence++
			);
			PENDING.computeIfAbsent(
					stream,
					ignored -> new ArrayDeque<>()
			).addLast(new PendingPcm(
					current,
					current.generatedPcmCount++,
					streamSequence,
					bytes.length,
					sha256(bytes),
					firstNonZeroFrame(bytes),
					prerollFrames,
					prerollNanos
			));
		}
	}

	public static void onBufferQueued(
			AudioStream stream,
			int source,
			int buffer
	) {
		PendingPcm pending;
		synchronized (LOCK) {
			if (!enabled || stream == null) {
				return;
			}
			ArrayDeque<PendingPcm> queue = PENDING.get(stream);
			pending = queue == null ? null : queue.pollFirst();
			if (queue != null && queue.isEmpty()) {
				PENDING.remove(stream);
			}
			if (pending == null) {
				return;
			}
		}
		long queuedNanos = System.nanoTime();
		boolean bufferValid = AL10.alIsBuffer(buffer);
		QueueEvent event = new QueueEvent(
				pending.queueIndex,
				pending.streamSequence,
				source,
				buffer,
				queuedNanos,
				queuedNanos - pending.cycle.fallbackRequestedNanos,
				pending.pcmBytes,
				pending.pcmSha256,
				pending.firstNonZeroFrame,
				pending.prerollFrames,
				pending.prerollNanos,
				bufferValid
						? AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY)
						: 0,
				bufferValid
						? AL10.alGetBufferi(buffer, AL10.AL_BITS)
						: 0,
				bufferValid
						? AL10.alGetBufferi(buffer, AL10.AL_CHANNELS)
						: 0,
				bufferValid
						? AL10.alGetBufferi(buffer, AL10.AL_SIZE)
						: 0,
				AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE),
				AL10.alGetSourcei(source, AL10.AL_SOURCE_TYPE),
				AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED),
				AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED),
				AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET)
		);
		synchronized (LOCK) {
			if (enabled && pending.cycle == current
					&& pending.cycle.queued.size()
					< INITIAL_QUEUE_BUFFERS) {
				pending.cycle.queued.add(event);
			}
		}
	}

	static void onShadowAdvanced(
			ListenerReverbHistoryProducer.Diagnostics diagnostics
	) {
		synchronized (LOCK) {
			if (!enabled || current == null
					|| current.fallbackRequestedNanos == 0L
					|| !diagnostics.shadowActive()
					|| diagnostics.wetActive()) {
				return;
			}
			if (current.shadowRestartNanos == 0L) {
				current.shadowRestartNanos = System.nanoTime();
				current.historyFramesAtShadowRestart =
						diagnostics.historyFrames();
				current.synthesizersAtShadowRestart =
						diagnostics.synthesizerCount();
			}
			if (diagnostics.historyFrames()
					== ListenerReverbHistoryProducer.HISTORY_FRAMES) {
				current.historyReadyNanos = System.nanoTime();
			}
		}
	}

	static Snapshot finish() {
		synchronized (LOCK) {
			enabled = false;
			PENDING.clear();
			STREAM_SEQUENCES.clear();
			if (CYCLES.size() != 3
					|| CYCLES.stream().anyMatch(cycle -> !cycle.complete())) {
				throw new IllegalStateException(
						"D108 queue handoff probe is incomplete"
				);
			}
			List<Cycle> cycles = CYCLES.stream()
					.map(MutableCycle::snapshot)
					.toList();
			current = null;
			return new Snapshot(cycles);
		}
	}

	static void abort() {
		synchronized (LOCK) {
			enabled = false;
			CYCLES.clear();
			PENDING.clear();
			STREAM_SEQUENCES.clear();
			current = null;
		}
	}

	private static int firstNonZeroFrame(byte[] pcm) {
		for (int frame = 0; frame < pcm.length / 2; frame++) {
			int low = pcm[frame * 2] & 0xff;
			int high = pcm[frame * 2 + 1];
			if ((short) (low | high << 8) != 0) {
				return frame;
			}
		}
		return -1;
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

	record Snapshot(List<Cycle> cycles) {
		Snapshot {
			cycles = List.copyOf(cycles);
		}
	}

	record Cycle(
			String stage,
			long armedNanos,
			int historyFramesAtArm,
			long fallbackRequestedNanos,
			int historyFramesAtFallback,
			int synthesizersAtFallback,
			boolean shadowActiveAtFallback,
			boolean wetActiveAtFallback,
			List<QueueEvent> queued,
			long shadowRestartNanos,
			int historyFramesAtShadowRestart,
			int synthesizersAtShadowRestart,
			long historyReadyNanos
	) {
		Cycle {
			queued = List.copyOf(queued);
		}
	}

	record QueueEvent(
			int queueIndex,
			long streamSequence,
			int source,
			int buffer,
			long queuedNanos,
			long fallbackToQueueNanos,
			int pcmBytes,
			String pcmSha256,
			int firstNonZeroFrame,
			int prerollFrames,
			long prerollNanos,
			int bufferFrequency,
			int bufferBits,
			int bufferChannels,
			int bufferBytes,
			int sourceState,
			int sourceType,
			int buffersQueued,
			int buffersProcessed,
			int sampleOffset
	) {
	}

	private record PendingPcm(
			MutableCycle cycle,
			int queueIndex,
			long streamSequence,
			int pcmBytes,
			String pcmSha256,
			int firstNonZeroFrame,
			int prerollFrames,
			long prerollNanos
	) {
	}

	private static final class MutableCycle {
		private final String stage;
		private final long armedNanos;
		private final int historyFramesAtArm;
		private long fallbackRequestedNanos;
		private int historyFramesAtFallback;
		private int synthesizersAtFallback;
		private boolean shadowActiveAtFallback;
		private boolean wetActiveAtFallback;
		private int generatedPcmCount;
		private final List<QueueEvent> queued = new ArrayList<>();
		private long shadowRestartNanos;
		private int historyFramesAtShadowRestart;
		private int synthesizersAtShadowRestart;
		private long historyReadyNanos;

		private MutableCycle(
				String stage,
				long armedNanos,
				int historyFramesAtArm
		) {
			this.stage = stage;
			this.armedNanos = armedNanos;
			this.historyFramesAtArm = historyFramesAtArm;
		}

		private boolean complete() {
			return historyFramesAtArm
							== ListenerReverbHistoryProducer.HISTORY_FRAMES
					&& fallbackRequestedNanos > armedNanos
					&& queued.size() == INITIAL_QUEUE_BUFFERS
					&& shadowRestartNanos
							> queued.getFirst().queuedNanos()
					&& historyFramesAtShadowRestart
							== ListenerReverbHistoryProducer.TICK_FRAMES
					&& synthesizersAtShadowRestart > 0
					&& historyReadyNanos > shadowRestartNanos;
		}

		private Cycle snapshot() {
			return new Cycle(
					stage,
					armedNanos,
					historyFramesAtArm,
					fallbackRequestedNanos,
					historyFramesAtFallback,
					synthesizersAtFallback,
					shadowActiveAtFallback,
					wetActiveAtFallback,
					queued,
					shadowRestartNanos,
					historyFramesAtShadowRestart,
					synthesizersAtShadowRestart,
					historyReadyNanos
			);
		}
	}
}
