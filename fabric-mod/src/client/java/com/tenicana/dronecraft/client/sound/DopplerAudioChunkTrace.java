package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.TonalComponent;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded diagnostic tap for the exact PCM buffers returned by
 * {@link ProceduralDroneAudioStream#read(int)}.
 */
final class DopplerAudioChunkTrace {
	private static final Object LOCK = new Object();
	private static final char[] HEX =
			"0123456789abcdef".toCharArray();
	private static final Map<PhaseContinuousSynthesizer.Layer, Integer>
			LAYER_COUNTS = new EnumMap<>(
					PhaseContinuousSynthesizer.Layer.class
			);
	private static final List<Chunk> CHUNKS = new ArrayList<>();
	private static boolean enabled;
	private static int targetChunksPerLayer;
	private static long sequence;

	private DopplerAudioChunkTrace() {
	}

	static void begin(int chunksPerLayer) {
		if (chunksPerLayer < 4 || chunksPerLayer > 32) {
			throw new IllegalArgumentException(
					"chunksPerLayer must be in [4, 32]"
			);
		}
		synchronized (LOCK) {
			CHUNKS.clear();
			LAYER_COUNTS.clear();
			targetChunksPerLayer = chunksPerLayer;
			sequence = 0L;
			enabled = true;
		}
	}

	static boolean active() {
		synchronized (LOCK) {
			return enabled;
		}
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
			if (!layerReady(PhaseContinuousSynthesizer.Layer.MOTOR)
					|| !layerReady(
							PhaseContinuousSynthesizer.Layer.PROPELLER
					)) {
				throw new IllegalStateException(
						"Doppler audio chunk trace is incomplete"
				);
			}
			return new Snapshot(
					targetChunksPerLayer,
					List.copyOf(CHUNKS)
			);
		}
	}

	static void abort() {
		synchronized (LOCK) {
			enabled = false;
			CHUNKS.clear();
			LAYER_COUNTS.clear();
		}
	}

	static void record(
			PhaseContinuousSynthesizer.Layer layer,
			DroneAcousticRenderState.AudioSnapshot snapshot,
			int frequencySmoothingSamples,
			List<PhaseContinuousSynthesizer.OscillatorDiagnostic> before,
			int smoothingCheckpointSamples,
			List<PhaseContinuousSynthesizer.OscillatorDiagnostic>
					smoothingCheckpoint,
			List<PhaseContinuousSynthesizer.OscillatorDiagnostic> after,
			ByteBuffer pcm
	) {
		Objects.requireNonNull(layer, "layer");
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(before, "before");
		Objects.requireNonNull(
				smoothingCheckpoint,
				"smoothingCheckpoint"
		);
		Objects.requireNonNull(after, "after");
		Objects.requireNonNull(pcm, "pcm");
		synchronized (LOCK) {
			if (!enabled || !isProductLayer(layer)) {
				return;
			}
			int layerSequence = LAYER_COUNTS.getOrDefault(layer, 0);
			if (layerSequence >= targetChunksPerLayer + 8) {
				return;
			}
			TonalComponent selected = selectTone(
					snapshot.emission(),
					layer
			);
			PhaseContinuousSynthesizer.OscillatorDiagnostic beforeTone =
					find(before, selected);
			PhaseContinuousSynthesizer.OscillatorDiagnostic checkpointTone =
					find(smoothingCheckpoint, selected);
			PhaseContinuousSynthesizer.OscillatorDiagnostic afterTone =
					find(after, selected);
			if (checkpointTone == null || afterTone == null) {
				return;
			}
			ByteBuffer copy = pcm.asReadOnlyBuffer();
			byte[] bytes = new byte[copy.remaining()];
			copy.get(bytes);
			boolean targetChanged = beforeTone != null
					&& Math.abs(
							beforeTone.targetFrequencyHz()
									- selected.frequencyHz()
					) > 1.0e-9;
			CHUNKS.add(new Chunk(
					sequence++,
					layerSequence,
					layer,
					snapshot.source().entityId(),
					snapshot.source().simulationTimeNanos(),
					snapshot.dopplerFrequencyRatio(),
					emissionSha256(snapshot.emission()),
					selected.kind(),
					selected.rotorIndex(),
					selected.order(),
					snapshot.source().rotors()
							.get(selected.rotorIndex())
							.rpm(),
					snapshot.source().rotors()
							.get(selected.rotorIndex())
							.bladeCount(),
					selected.frequencyHz(),
					frequencySmoothingSamples,
					beforeTone == null
							? Double.NaN
							: beforeTone.currentFrequencyHz(),
					beforeTone == null
							? Double.NaN
							: beforeTone.targetFrequencyHz(),
					beforeTone == null
							? -1
							: beforeTone
									.frequencyRampSamplesRemaining(),
					smoothingCheckpointSamples,
					checkpointTone.currentFrequencyHz(),
					checkpointTone.targetFrequencyHz(),
					checkpointTone.frequencyRampSamplesRemaining(),
					afterTone.currentFrequencyHz(),
					afterTone.targetFrequencyHz(),
					afterTone.frequencyRampSamplesRemaining(),
					targetChanged,
					bytes,
					sha256(bytes)
			));
			LAYER_COUNTS.put(layer, layerSequence + 1);
		}
	}

	private static boolean layerReady(
			PhaseContinuousSynthesizer.Layer layer
	) {
		return LAYER_COUNTS.getOrDefault(layer, 0)
						>= targetChunksPerLayer
				&& CHUNKS.stream().anyMatch(chunk ->
						chunk.layer() == layer && chunk.targetChanged()
				);
	}

	private static boolean isProductLayer(
			PhaseContinuousSynthesizer.Layer layer
	) {
		return layer == PhaseContinuousSynthesizer.Layer.MOTOR
				|| layer == PhaseContinuousSynthesizer.Layer.PROPELLER;
	}

	private static TonalComponent selectTone(
			AcousticEmissionFrame frame,
			PhaseContinuousSynthesizer.Layer layer
	) {
		return frame.tones().stream()
				.filter(tone ->
						(layer == PhaseContinuousSynthesizer.Layer.PROPELLER)
								== (tone.kind()
										== TonalComponent.Kind.BLADE_PASS)
				)
				.filter(tone -> tone.linearAmplitude() > 0.0)
				.max(Comparator
						.comparingDouble(TonalComponent::linearAmplitude)
						.thenComparingDouble(tone -> -tone.frequencyHz())
				)
				.orElseThrow(() -> new IllegalArgumentException(
						"trace frame has no measurable " + layer + " tone"
				));
	}

	private static PhaseContinuousSynthesizer.OscillatorDiagnostic find(
			List<PhaseContinuousSynthesizer.OscillatorDiagnostic> values,
			TonalComponent tone
	) {
		for (PhaseContinuousSynthesizer.OscillatorDiagnostic value : values) {
			if (value.kind() == tone.kind()
					&& value.rotorIndex() == tone.rotorIndex()
					&& value.order() == tone.order()) {
				return value;
			}
		}
		return null;
	}

	private static String emissionSha256(AcousticEmissionFrame frame) {
		MessageDigest digest = sha256Digest();
		for (TonalComponent tone : frame.tones()) {
			updateLong(digest, tone.kind().ordinal());
			updateLong(digest, tone.rotorIndex());
			updateLong(digest, tone.order());
			updateLong(digest, Double.doubleToLongBits(tone.frequencyHz()));
			updateLong(
					digest,
					Double.doubleToLongBits(tone.linearAmplitude())
			);
			updateLong(
					digest,
					Double.doubleToLongBits(tone.phaseRadians())
			);
		}
		updateLong(
				digest,
				Double.doubleToLongBits(frame.broadbandEnergy().low())
		);
		updateLong(
				digest,
				Double.doubleToLongBits(frame.broadbandEnergy().mid())
		);
		updateLong(
				digest,
				Double.doubleToLongBits(frame.broadbandEnergy().high())
		);
		return hex(digest.digest());
	}

	private static String sha256(byte[] bytes) {
		return hex(sha256Digest().digest(bytes));
	}

	private static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static void updateLong(MessageDigest digest, long value) {
		digest.update((byte) (value >>> 56));
		digest.update((byte) (value >>> 48));
		digest.update((byte) (value >>> 40));
		digest.update((byte) (value >>> 32));
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
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

	record Snapshot(int targetChunksPerLayer, List<Chunk> chunks) {
		Snapshot {
			chunks = List.copyOf(chunks);
		}
	}

	record Chunk(
			long sequence,
			int layerSequence,
			PhaseContinuousSynthesizer.Layer layer,
			long entityId,
			long simulationTimeNanos,
			double dopplerFrequencyRatio,
			String emissionSha256,
			TonalComponent.Kind trackedToneKind,
			int trackedRotorIndex,
			int trackedOrder,
			double sourceRotorRpm,
			int sourceRotorBladeCount,
			double frameTargetFrequencyHz,
			int frequencySmoothingSamples,
			double beforeCurrentFrequencyHz,
			double beforeTargetFrequencyHz,
			int beforeRampSamplesRemaining,
			int smoothingCheckpointSamples,
			double checkpointCurrentFrequencyHz,
			double checkpointTargetFrequencyHz,
			int checkpointRampSamplesRemaining,
			double afterCurrentFrequencyHz,
			double afterTargetFrequencyHz,
			int afterRampSamplesRemaining,
			boolean targetChanged,
			byte[] pcmBytes,
			String pcmSha256
	) {
		Chunk {
			pcmBytes = pcmBytes.clone();
		}

		@Override
		public byte[] pcmBytes() {
			return pcmBytes.clone();
		}
	}
}
