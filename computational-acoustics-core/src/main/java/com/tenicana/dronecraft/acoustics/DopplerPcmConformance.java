package com.tenicana.dronecraft.acoustics;

import com.tenicana.dronecraft.acoustics.propagation.DopplerShift;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Measures a Doppler-shifted tone after the production synthesizer, soft clip,
 * and PCM16 quantization path.
 */
public final class DopplerPcmConformance {
	public static final int SAMPLE_RATE = 48_000;
	private static final int SAMPLE_COUNT = SAMPLE_RATE;
	private static final int ANALYSIS_START = SAMPLE_RATE / 5;

	private DopplerPcmConformance() {
	}

	public static Result measure(
			AcousticEmissionFrame unshiftedEmission,
			AcousticSourceFrame source,
			AcousticListenerFrame listener,
			double soundSpeedMetersPerSecond,
			PhaseContinuousSynthesizer.Layer layer
	) {
		Objects.requireNonNull(unshiftedEmission, "unshiftedEmission");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(layer, "layer");
		DopplerShift.Result shifted = DopplerShift.apply(
				unshiftedEmission,
				source,
				listener,
				soundSpeedMetersPerSecond
		);
		TonalComponent selected = shifted.emission().tones().stream()
				.filter(tone -> accepts(layer, tone.kind()))
				.filter(tone -> tone.linearAmplitude() > 0.0)
				.filter(tone -> tone.frequencyHz() < SAMPLE_RATE * 0.45)
				.max(Comparator
						.comparingDouble(TonalComponent::linearAmplitude)
						.thenComparingDouble(tone -> -tone.frequencyHz())
				)
				.orElseThrow(() -> new IllegalArgumentException(
						"no measurable tone exists for " + layer
				));
		AcousticEmissionFrame isolated = new AcousticEmissionFrame(
				List.of(selected),
				AcousticBands.SILENT
		);
		float[] rendered = new float[SAMPLE_COUNT];
		new PhaseContinuousSynthesizer(SAMPLE_RATE, 0x5eed093)
				.render(isolated, layer, rendered);
		short[] pcm16 = new short[rendered.length];
		int clippedSamples = 0;
		for (int index = 0; index < rendered.length; index++) {
			double scaled = Math.max(
					-1.0,
					Math.min(1.0, rendered[index])
			) * 32767.0;
			pcm16[index] = (short) Math.round(scaled);
			if (Math.abs(pcm16[index]) == 32767) {
				clippedSamples++;
			}
		}
		FrequencyEstimate estimate = estimateFrequency(
				pcm16,
				ANALYSIS_START,
				SAMPLE_RATE
		);
		double baseFrequency = selected.frequencyHz()
				/ shifted.frequencyRatio();
		double errorHz = estimate.frequencyHz() - selected.frequencyHz();
		return new Result(
				layer,
				selected.kind(),
				selected.rotorIndex(),
				selected.order(),
				baseFrequency,
				shifted.frequencyRatio(),
				selected.frequencyHz(),
				estimate.frequencyHz(),
				errorHz,
				errorHz / selected.frequencyHz() * 1_000_000.0,
				shifted.kinematics(),
				SAMPLE_RATE,
				pcm16.length - ANALYSIS_START,
				estimate.positiveCrossings(),
				clippedSamples,
				true,
				true
		);
	}

	static FrequencyEstimate estimateFrequency(
			short[] pcm16,
			int start,
			int sampleRate
	) {
		Objects.requireNonNull(pcm16, "pcm16");
		if (sampleRate <= 0 || start < 1 || start >= pcm16.length - 1) {
			throw new IllegalArgumentException(
					"frequency estimator bounds are invalid"
			);
		}
		double firstCrossing = Double.NaN;
		double lastCrossing = Double.NaN;
		int crossings = 0;
		for (int index = start; index < pcm16.length; index++) {
			double previous = pcm16[index - 1];
			double current = pcm16[index];
			if (previous <= 0.0 && current > 0.0) {
				double denominator = current - previous;
				double crossing = index - 1
						+ (denominator == 0.0
								? 0.0
								: -previous / denominator);
				if (crossings == 0) {
					firstCrossing = crossing;
				}
				lastCrossing = crossing;
				crossings++;
			}
		}
		if (crossings < 3 || !(lastCrossing > firstCrossing)) {
			throw new IllegalArgumentException(
					"PCM window has too few positive crossings"
			);
		}
		double frequency = (crossings - 1.0) * sampleRate
				/ (lastCrossing - firstCrossing);
		return new FrequencyEstimate(frequency, crossings);
	}

	private static boolean accepts(
			PhaseContinuousSynthesizer.Layer layer,
			TonalComponent.Kind kind
	) {
		return layer == PhaseContinuousSynthesizer.Layer.FULL
				|| (layer == PhaseContinuousSynthesizer.Layer.PROPELLER)
						== (kind == TonalComponent.Kind.BLADE_PASS);
	}

	public record Result(
			PhaseContinuousSynthesizer.Layer layer,
			TonalComponent.Kind toneKind,
			int rotorIndex,
			int order,
			double baseFrequencyHz,
			double dopplerFrequencyRatio,
			double expectedFrequencyHz,
			double measuredFrequencyHz,
			double frequencyErrorHz,
			double frequencyErrorPpm,
			DopplerShift.Kinematics kinematics,
			int sampleRateHz,
			int analyzedSamples,
			int positiveCrossings,
			int clippedSamples,
			boolean broadbandExcluded,
			boolean pcm16Quantized
	) {
		public Result {
			Objects.requireNonNull(layer, "layer");
			Objects.requireNonNull(toneKind, "toneKind");
			Objects.requireNonNull(kinematics, "kinematics");
		}
	}

	record FrequencyEstimate(double frequencyHz, int positiveCrossings) {
	}
}
