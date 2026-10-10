package com.tenicana.dronecraft.acoustics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Allocation-free-per-sample mono renderer for an order-domain emission frame.
 * Oscillator state survives telemetry frame changes, preventing phase resets at
 * the 20 Hz game tick boundary.
 */
public final class PhaseContinuousSynthesizer {
	private final PhaseContinuousAudioKernel kernel;
	private final Map<ToneKey, PhaseContinuousAudioKernel.Oscillator> oscillators = new HashMap<>();
	private AcousticEmissionFrame lastFrame;
	private Layer lastLayer;

	public PhaseContinuousSynthesizer(int sampleRate, int noiseSeed) {
		kernel = new PhaseContinuousAudioKernel(sampleRate, noiseSeed);
	}

	public int sampleRate() {
		return kernel.sampleRate;
	}

	public int frequencySmoothingSamples() {
		return kernel.frequencySmoothingSamples;
	}

	/**
	 * Diagnostic snapshot of oscillator state at a render-buffer boundary.
	 */
	public List<OscillatorDiagnostic> oscillatorDiagnostics() {
		return oscillators.entrySet().stream()
				.map(entry -> new OscillatorDiagnostic(
						entry.getKey().kind(),
						entry.getKey().rotorIndex(),
						entry.getKey().order(),
						entry.getValue().frequencyHz,
						entry.getValue().targetFrequencyHz,
						entry.getValue().frequencyRampSamplesRemaining,
						entry.getValue().amplitude,
						entry.getValue().targetAmplitude
				))
				.sorted(java.util.Comparator
						.comparing(
								OscillatorDiagnostic::kind
						)
						.thenComparingInt(
								OscillatorDiagnostic::rotorIndex
						)
						.thenComparingInt(
								OscillatorDiagnostic::order
						)
				)
				.toList();
	}

	public void render(AcousticEmissionFrame frame, Layer layer, float[] output) {
		render(frame, layer, output, 0, output.length);
	}

	public void render(AcousticEmissionFrame frame, Layer layer, float[] output, int offset, int length) {
		Objects.requireNonNull(frame, "frame");
		Objects.requireNonNull(layer, "layer");
		Objects.requireNonNull(output, "output");
		Objects.checkFromIndexSize(offset, length, output.length);
		if (length == 0) {
			return;
		}

		if (frame != lastFrame || layer != lastLayer) {
			updateTargets(frame, layer);
			lastFrame = frame;
			lastLayer = layer;
		}

		for (int sample = offset; sample < offset + length; sample++) {
			output[sample] = 0.0f;
		}
		for (PhaseContinuousAudioKernel.Oscillator oscillator : oscillators.values()) {
			kernel.renderOscillator(oscillator, output, offset, length);
		}
		removeSilentOscillators();
		kernel.renderBroadband(frame.broadbandEnergy(), layer, output, offset, length);

		kernel.finishOutput(output, offset, length);
	}

	private void updateTargets(AcousticEmissionFrame frame, Layer layer) {
		for (PhaseContinuousAudioKernel.Oscillator oscillator : oscillators.values()) {
			oscillator.targetAmplitude = 0.0;
		}
		for (TonalComponent tone : frame.tones()) {
			if (!layer.accepts(tone.kind()) || tone.frequencyHz() >= kernel.sampleRate * 0.495) {
				continue;
			}
			ToneKey key = new ToneKey(tone.kind(), tone.rotorIndex(), tone.order());
			PhaseContinuousAudioKernel.Oscillator oscillator = oscillators.computeIfAbsent(
					key,
					ignored -> new PhaseContinuousAudioKernel.Oscillator(tone.phaseRadians(), tone.frequencyHz())
			);
			oscillator.targetAmplitude = tone.linearAmplitude();
			oscillator.setTargetFrequency(tone.frequencyHz(), kernel.frequencySmoothingSamples);
		}
	}

	private void removeSilentOscillators() {
		Iterator<PhaseContinuousAudioKernel.Oscillator> iterator = oscillators.values().iterator();
		while (iterator.hasNext()) {
			PhaseContinuousAudioKernel.Oscillator oscillator = iterator.next();
			if (oscillator.targetAmplitude == 0.0 && Math.abs(oscillator.amplitude) < PhaseContinuousAudioKernel.SILENCE_THRESHOLD) {
				iterator.remove();
			}
		}
	}

	public enum Layer {
		MOTOR(0.12),
		PROPELLER(0.88),
		FULL(1.0);

		private final double broadbandGain;

		Layer(double broadbandGain) {
			this.broadbandGain = broadbandGain;
		}

		boolean accepts(TonalComponent.Kind kind) {
			return this == FULL || (this == PROPELLER) == (kind == TonalComponent.Kind.BLADE_PASS);
		}

		double broadbandGain() {
			return broadbandGain;
		}
	}

	public record OscillatorDiagnostic(
			TonalComponent.Kind kind,
			int rotorIndex,
			int order,
			double currentFrequencyHz,
			double targetFrequencyHz,
			int frequencyRampSamplesRemaining,
			double currentAmplitude,
			double targetAmplitude
	) {
	}

	private record ToneKey(TonalComponent.Kind kind, int rotorIndex, int order) {
	}

}
