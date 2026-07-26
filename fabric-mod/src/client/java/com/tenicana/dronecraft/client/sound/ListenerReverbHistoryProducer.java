package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listener-owned input history and synthesis phase.
 *
 * <p>The producer is advanced once per Minecraft tick while OpenAL EFX owns
 * the wet path. It is deliberately not attached to an OpenAL source: a silent
 * streaming source would have several seconds of already queued silence when
 * fallback occurs. Ownership transfers to the active Java stream without
 * recreating the synthesizers, then a new shadow epoch is started when EFX
 * takes ownership again.
 */
final class ListenerReverbHistoryProducer {
	static final int HISTORY_FRAMES =
			DroneAcousticRenderState.SAMPLE_RATE / 2;
	static final int TICK_FRAMES =
			DroneAcousticRenderState.SAMPLE_RATE / 20;

	private final float[] history = new float[HISTORY_FRAMES];
	private final float[] shadowBuffer = new float[TICK_FRAMES];
	private final Map<Integer, SynthesizerPair> synthesizers = new HashMap<>();
	private final Set<Integer> activeIds = new HashSet<>();
	private float[] scratch = new float[0];
	private int historyWriteIndex;
	private int historyFrameCount;
	private boolean shadowActive = true;
	private long ownershipGeneration;
	private long activeOwner;
	private long shadowRenderCount;
	private long wetRenderCount;

	synchronized void enterShadow() {
		if (shadowActive) {
			return;
		}
		ownershipGeneration++;
		activeOwner = 0L;
		shadowActive = true;
		clearSignalState();
	}

	synchronized void requestWet() {
		shadowActive = false;
	}

	synchronized void advanceShadow(List<ListenerReverbState.Source> sources) {
		if (!shadowActive) {
			return;
		}
		renderMixed(sources, shadowBuffer, TICK_FRAMES);
		appendHistory(shadowBuffer, TICK_FRAMES);
		shadowRenderCount++;
	}

	synchronized ActiveSession acquireWet(
			List<ListenerReverbState.Source> currentSources
	) {
		shadowActive = false;
		long owner = ++ownershipGeneration;
		activeOwner = owner;
		return new ActiveSession(
				this,
				owner,
				historySnapshot(),
				List.copyOf(currentSources)
		);
	}

	synchronized void reset() {
		ownershipGeneration++;
		activeOwner = 0L;
		shadowActive = true;
		shadowRenderCount = 0L;
		wetRenderCount = 0L;
		clearSignalState();
	}

	synchronized Diagnostics diagnostics() {
		return new Diagnostics(
				shadowActive,
				activeOwner != 0L,
				historyFrameCount,
				ownershipGeneration,
				shadowRenderCount,
				wetRenderCount,
				synthesizers.size()
		);
	}

	private synchronized boolean renderWet(
			long owner,
			List<ListenerReverbState.Source> sources,
			float[] output,
			int frameCount
	) {
		if (owner != activeOwner || shadowActive) {
			Arrays.fill(output, 0, frameCount, 0.0F);
			return false;
		}
		renderMixed(sources, output, frameCount);
		wetRenderCount++;
		return true;
	}

	private synchronized void release(long owner) {
		if (owner == activeOwner) {
			activeOwner = 0L;
		}
	}

	private void renderMixed(
			List<ListenerReverbState.Source> sources,
			float[] output,
			int frameCount
	) {
		ensureScratch(frameCount);
		Arrays.fill(output, 0, frameCount, 0.0F);
		activeIds.clear();
		for (ListenerReverbState.Source source : sources) {
			activeIds.add(source.entityId());
			SynthesizerPair pair = synthesizers.computeIfAbsent(
					source.entityId(),
					SynthesizerPair::new
			);
			pair.motor.render(
					source.emission(),
					PhaseContinuousSynthesizer.Layer.MOTOR,
					scratch,
					0,
					frameCount
			);
			addToMix(
					output,
					scratch,
					source.motorAmplitude(),
					frameCount
			);
			pair.propeller.render(
					source.emission(),
					PhaseContinuousSynthesizer.Layer.PROPELLER,
					scratch,
					0,
					frameCount
			);
			addToMix(
					output,
					scratch,
					source.propellerAmplitude(),
					frameCount
			);
		}
		synthesizers.keySet().removeIf(id -> !activeIds.contains(id));
		for (int index = 0; index < frameCount; index++) {
			float value = output[index];
			output[index] = value / (1.0F + Math.abs(value));
		}
	}

	private void appendHistory(float[] input, int frameCount) {
		for (int index = 0; index < frameCount; index++) {
			history[historyWriteIndex] = input[index];
			historyWriteIndex = (historyWriteIndex + 1) % history.length;
			if (historyFrameCount < history.length) {
				historyFrameCount++;
			}
		}
	}

	private float[] historySnapshot() {
		float[] copy = new float[historyFrameCount];
		int first = Math.floorMod(
				historyWriteIndex - historyFrameCount,
				history.length
		);
		int firstLength = Math.min(historyFrameCount, history.length - first);
		System.arraycopy(history, first, copy, 0, firstLength);
		if (firstLength < historyFrameCount) {
			System.arraycopy(
					history,
					0,
					copy,
					firstLength,
					historyFrameCount - firstLength
			);
		}
		return copy;
	}

	private void clearSignalState() {
		Arrays.fill(history, 0.0F);
		historyWriteIndex = 0;
		historyFrameCount = 0;
		synthesizers.clear();
		activeIds.clear();
		scratch = new float[0];
	}

	private void ensureScratch(int frameCount) {
		if (scratch.length < frameCount) {
			scratch = new float[frameCount];
		}
	}

	private static void addToMix(
			float[] output,
			float[] source,
			double amplitude,
			int frameCount
	) {
		for (int index = 0; index < frameCount; index++) {
			output[index] += (float) (source[index] * amplitude);
		}
	}

	static final class ActiveSession implements AutoCloseable {
		private final ListenerReverbHistoryProducer producer;
		private final long owner;
		private final float[] history;
		private final List<ListenerReverbState.Source> sourcesAtHandoff;
		private boolean closed;

		private ActiveSession(
				ListenerReverbHistoryProducer producer,
				long owner,
				float[] history,
				List<ListenerReverbState.Source> sourcesAtHandoff
		) {
			this.producer = producer;
			this.owner = owner;
			this.history = history;
			this.sourcesAtHandoff = sourcesAtHandoff;
		}

		float[] history() {
			return history.clone();
		}

		List<ListenerReverbState.Source> sourcesAtHandoff() {
			return sourcesAtHandoff;
		}

		boolean render(
				List<ListenerReverbState.Source> sources,
				float[] output,
				int frameCount
		) {
			if (closed) {
				Arrays.fill(output, 0, frameCount, 0.0F);
				return false;
			}
			return producer.renderWet(owner, sources, output, frameCount);
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			producer.release(owner);
		}
	}

	record Diagnostics(
			boolean shadowActive,
			boolean wetActive,
			int historyFrames,
			long ownershipGeneration,
			long shadowRenderCount,
			long wetRenderCount,
			int synthesizerCount
	) {
	}

	private static final class SynthesizerPair {
		private final PhaseContinuousSynthesizer motor;
		private final PhaseContinuousSynthesizer propeller;

		private SynthesizerPair(int entityId) {
			motor = new PhaseContinuousSynthesizer(
					DroneAcousticRenderState.SAMPLE_RATE,
					31 * entityId + 17
			);
			propeller = new PhaseContinuousSynthesizer(
					DroneAcousticRenderState.SAMPLE_RATE,
					31 * entityId + 23
			);
		}
	}
}
