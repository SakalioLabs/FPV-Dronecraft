package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseContinuousSynthesizerTest {
	@Test
	void routesBladePassAndMotorOrdersToDifferentLayers() {
		AcousticEmissionFrame frame = new AcousticEmissionFrame(List.of(
				new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 1_000.0, 0.2, 0.0),
				new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 700.0, 0.2, 0.0)
		), AcousticBands.SILENT);
		float[] motor = new float[4_800];
		float[] propeller = new float[4_800];

		new PhaseContinuousSynthesizer(48_000, 1).render(
				frame, PhaseContinuousSynthesizer.Layer.MOTOR, motor
		);
		new PhaseContinuousSynthesizer(48_000, 1).render(
				frame, PhaseContinuousSynthesizer.Layer.PROPELLER, propeller
		);

		assertTrue(rms(motor) > 0.01);
		assertTrue(rms(propeller) > 0.01);
	}

	@Test
	void outputIsDeterministicAndBounded() {
		AcousticEmissionFrame frame = new AcousticEmissionFrame(List.of(
				new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 500.0, 4.0, 0.2)
		), new AcousticBands(2.0, 2.0, 2.0));
		float[] first = new float[1_024];
		float[] second = new float[1_024];

		new PhaseContinuousSynthesizer(48_000, 1234).render(
				frame, PhaseContinuousSynthesizer.Layer.FULL, first
		);
		new PhaseContinuousSynthesizer(48_000, 1234).render(
				frame, PhaseContinuousSynthesizer.Layer.FULL, second
		);

		assertArrayEquals(first, second);
		for (float sample : first) {
			assertTrue(Float.isFinite(sample));
			assertTrue(Math.abs(sample) < 1.0f);
		}
	}

	@Test
	void oscillatorReleasesWithoutDiscontinuity() {
		AcousticEmissionFrame active = new AcousticEmissionFrame(List.of(
				new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 750.0, 0.3, 0.0)
		), AcousticBands.SILENT);
		AcousticEmissionFrame silent = new AcousticEmissionFrame(List.of(), AcousticBands.SILENT);
		PhaseContinuousSynthesizer synthesizer = new PhaseContinuousSynthesizer(48_000, 99);
		float[] first = new float[9_600];
		float[] release = new float[9_600];

		synthesizer.render(active, PhaseContinuousSynthesizer.Layer.MOTOR, first);
		synthesizer.render(silent, PhaseContinuousSynthesizer.Layer.MOTOR, release);

		assertTrue(Math.abs(release[0] - first[first.length - 1]) < 0.15f);
		assertTrue(rms(release, release.length - 480, 480) < rms(release, 0, 480) * 0.30);
	}

	@Test
	void tonalAndBroadbandGainsAreIndependentOfBufferChunking() {
		AcousticEmissionFrame frame = new AcousticEmissionFrame(List.of(
				new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 750.0, 0.3, 0.0)
		), new AcousticBands(0.2, 0.5, 0.3));
		PhaseContinuousSynthesizer wholeBuffer = new PhaseContinuousSynthesizer(48_000, 123);
		PhaseContinuousSynthesizer splitBuffers = new PhaseContinuousSynthesizer(48_000, 123);
		float[] whole = new float[4_800];
		float[] split = new float[4_800];

		wholeBuffer.render(frame, PhaseContinuousSynthesizer.Layer.FULL, whole);
		for (int offset = 0; offset < split.length; offset += 480) {
			splitBuffers.render(
					frame,
					PhaseContinuousSynthesizer.Layer.FULL,
					split,
					offset,
					480
			);
		}

		for (int sample = 0; sample < whole.length; sample++) {
			assertEquals(whole[sample], split[sample], 2.0e-5f);
		}
	}

	@Test
	void equalBroadbandBandEnergyProducesComparableSteadyStateRms() {
		double[] bandRms = {
				broadbandRms(new AcousticBands(0.001, 0.0, 0.0), 201),
				broadbandRms(new AcousticBands(0.0, 0.001, 0.0), 202),
				broadbandRms(new AcousticBands(0.0, 0.0, 0.001), 203)
		};
		double minimum = Math.min(bandRms[0], Math.min(bandRms[1], bandRms[2]));
		double maximum = Math.max(bandRms[0], Math.max(bandRms[1], bandRms[2]));

		assertTrue(minimum > 0.011);
		assertTrue(maximum < 0.014);
		assertTrue(maximum / minimum < 1.08);
	}

	@Test
	void frequencyChangesUseAChunkIndependentFortyMillisecondChirp() {
		AcousticEmissionFrame initial = tonalFrame(1_000.0);
		AcousticEmissionFrame changed = tonalFrame(2_000.0);
		PhaseContinuousSynthesizer wholeBuffer = new PhaseContinuousSynthesizer(48_000, 7);
		PhaseContinuousSynthesizer splitBuffers = new PhaseContinuousSynthesizer(48_000, 7);
		float[] warmupA = new float[480];
		float[] warmupB = new float[480];
		wholeBuffer.render(initial, PhaseContinuousSynthesizer.Layer.MOTOR, warmupA);
		splitBuffers.render(initial, PhaseContinuousSynthesizer.Layer.MOTOR, warmupB);

		float[] whole = new float[1_920];
		float[] split = new float[1_920];
		wholeBuffer.render(changed, PhaseContinuousSynthesizer.Layer.MOTOR, whole);
		for (int offset = 0; offset < split.length; offset += 480) {
			splitBuffers.render(
					changed,
					PhaseContinuousSynthesizer.Layer.MOTOR,
					split,
					offset,
					480
			);
		}

		for (int sample = 0; sample < whole.length; sample++) {
			assertEquals(whole[sample], split[sample], 2.0e-5f);
		}
	}

	@Test
	void frequencyChangeDoesNotJumpToTheTargetOnTheFirstWindow() {
		PhaseContinuousSynthesizer synthesizer = new PhaseContinuousSynthesizer(48_000, 11);
		float[] warmup = new float[480];
		synthesizer.render(
				tonalFrame(1_000.0),
				PhaseContinuousSynthesizer.Layer.MOTOR,
				warmup
		);
		float[] transition = new float[1_920];
		synthesizer.render(
				tonalFrame(2_000.0),
				PhaseContinuousSynthesizer.Layer.MOTOR,
				transition
		);

		int firstFiveMilliseconds = zeroCrossings(transition, 0, 240);
		int lastFiveMilliseconds = zeroCrossings(transition, transition.length - 240, 240);
		assertTrue(firstFiveMilliseconds >= 8 && firstFiveMilliseconds <= 13);
		assertTrue(lastFiveMilliseconds >= 18 && lastFiveMilliseconds <= 21);
	}

	@Test
	void oscillatorDiagnosticsExposeTheExactFortyMillisecondRampState() {
		PhaseContinuousSynthesizer synthesizer =
				new PhaseContinuousSynthesizer(48_000, 12);
		assertEquals(1_920, synthesizer.frequencySmoothingSamples());
		assertTrue(synthesizer.oscillatorDiagnostics().isEmpty());

		float[] warmup = new float[480];
		synthesizer.render(
				tonalFrame(1_000.0),
				PhaseContinuousSynthesizer.Layer.MOTOR,
				warmup
		);
		float[] firstQuarter = new float[480];
		synthesizer.render(
				tonalFrame(2_000.0),
				PhaseContinuousSynthesizer.Layer.MOTOR,
				firstQuarter
		);

		PhaseContinuousSynthesizer.OscillatorDiagnostic ramping =
				synthesizer.oscillatorDiagnostics().getFirst();
		assertEquals(TonalComponent.Kind.ELECTRICAL, ramping.kind());
		assertEquals(0, ramping.rotorIndex());
		assertEquals(1, ramping.order());
		assertEquals(1_250.0, ramping.currentFrequencyHz(), 1.0e-9);
		assertEquals(2_000.0, ramping.targetFrequencyHz(), 1.0e-9);
		assertEquals(1_440, ramping.frequencyRampSamplesRemaining());
		assertTrue(ramping.currentAmplitude() > 0.0);
		assertEquals(0.3, ramping.targetAmplitude(), 1.0e-12);

		float[] finalThreeQuarters = new float[1_440];
		synthesizer.render(
				tonalFrame(2_000.0),
				PhaseContinuousSynthesizer.Layer.MOTOR,
				finalThreeQuarters
		);
		PhaseContinuousSynthesizer.OscillatorDiagnostic completed =
				synthesizer.oscillatorDiagnostics().getFirst();
		assertEquals(2_000.0, completed.currentFrequencyHz(), 1.0e-9);
		assertEquals(2_000.0, completed.targetFrequencyHz(), 1.0e-9);
		assertEquals(0, completed.frequencyRampSamplesRemaining());
	}

	private static AcousticEmissionFrame tonalFrame(double frequencyHz) {
		return new AcousticEmissionFrame(List.of(
				new TonalComponent(
						TonalComponent.Kind.ELECTRICAL,
						0,
						1,
						frequencyHz,
						0.3,
						0.0
				)
		), AcousticBands.SILENT);
	}

	private static int zeroCrossings(float[] samples, int offset, int length) {
		int crossings = 0;
		for (int sample = offset + 1; sample < offset + length; sample++) {
			if ((samples[sample - 1] < 0.0f && samples[sample] >= 0.0f)
					|| (samples[sample - 1] >= 0.0f && samples[sample] < 0.0f)) {
				crossings++;
			}
		}
		return crossings;
	}

	private static double broadbandRms(AcousticBands energy, int noiseSeed) {
		PhaseContinuousSynthesizer synthesizer = new PhaseContinuousSynthesizer(
				48_000,
				noiseSeed
		);
		AcousticEmissionFrame frame = new AcousticEmissionFrame(List.of(), energy);
		float[] samples = new float[96_000];
		synthesizer.render(frame, PhaseContinuousSynthesizer.Layer.FULL, samples);
		return rms(samples, 48_000, 48_000);
	}

	private static double rms(float[] samples) {
		return rms(samples, 0, samples.length);
	}

	private static double rms(float[] samples, int offset, int length) {
		double energy = 0.0;
		for (int sample = offset; sample < offset + length; sample++) {
			energy += samples[sample] * samples[sample];
		}
		return Math.sqrt(energy / length);
	}
}
