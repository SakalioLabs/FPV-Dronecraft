package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerSharedFdnTest {
	private static final int SAMPLE_RATE = 48_000;

	@Test
	void impulseStartsAfterPhysicalDelayAndProducesStableTail() {
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE);
		fdn.configure(new AcousticBands(1.2, 0.9, 0.6), 0.5, 0.0);
		float[] input = new float[SAMPLE_RATE];
		float[] output = new float[SAMPLE_RATE];
		input[0] = 1.0F;

		fdn.process(input, output, 0, output.length);

		int minimumDelay = Arrays.stream(
				fdn.diagnosticDelaySamples()
		).min().orElseThrow();
		assertEquals(0.0, energy(output, 0, minimumDelay), 0.0);
		assertTrue(energy(output, minimumDelay, SAMPLE_RATE / 2) > 0.0);
		assertTrue(energy(output, SAMPLE_RATE / 2, SAMPLE_RATE) > 0.0);
		assertTrue(maximumAbsolute(output) < 1.0);
	}

	@Test
	void longerRt60RetainsMoreLateEnergy() {
		float[] shortTail = impulseResponse(
				new AcousticBands(0.25, 0.25, 0.25),
				2 * SAMPLE_RATE
		);
		float[] longTail = impulseResponse(
				new AcousticBands(1.5, 1.5, 1.5),
				2 * SAMPLE_RATE
		);

		double shortLate = energy(
				shortTail,
				SAMPLE_RATE,
				2 * SAMPLE_RATE
		);
		double longLate = energy(
				longTail,
				SAMPLE_RATE,
				2 * SAMPLE_RATE
		);
		assertTrue(longLate > shortLate * 100.0);
	}

	@Test
	void parameterTransitionDoesNotClearExistingTail() {
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE);
		fdn.configure(new AcousticBands(1.5, 1.2, 0.9), 0.5, 0.0);
		float[] firstInput = new float[SAMPLE_RATE / 4];
		float[] firstOutput = new float[firstInput.length];
		firstInput[0] = 1.0F;
		fdn.process(firstInput, firstOutput, 0, firstInput.length);

		fdn.configure(new AcousticBands(0.2, 0.15, 0.1), 0.2, 0.2);
		float[] silence = new float[SAMPLE_RATE / 2];
		float[] transitioned = new float[silence.length];
		fdn.process(silence, transitioned, 0, silence.length);

		assertTrue(energy(transitioned, 0, transitioned.length) > 0.0);
		assertTrue(energy(transitioned, 0, 512) > 0.0);
	}

	@Test
	void outputIsIndependentOfCallerBufferChunking() {
		AcousticBands rt60 = new AcousticBands(1.0, 0.7, 0.4);
		float[] input = new float[20_000];
		input[0] = 1.0F;
		input[3_333] = -0.25F;

		ListenerSharedFdn oneBlock = new ListenerSharedFdn(SAMPLE_RATE);
		oneBlock.configure(rt60, 0.4, 0.0);
		float[] expected = new float[input.length];
		oneBlock.process(input, expected, 0, input.length);

		ListenerSharedFdn chunks = new ListenerSharedFdn(SAMPLE_RATE);
		chunks.configure(rt60, 0.4, 0.0);
		float[] actual = new float[input.length];
		int offset = 0;
		while (offset < input.length) {
			int length = Math.min(137, input.length - offset);
			chunks.process(input, actual, offset, length);
			offset += length;
		}

		assertArrayEquals(expected, actual);
	}

	@Test
	void resetClearsTailButKeepsConfiguration() {
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE);
		fdn.configure(new AcousticBands(1.0, 1.0, 1.0), 0.5, 0.0);
		float[] impulse = new float[4_000];
		float[] output = new float[4_000];
		impulse[0] = 1.0F;
		fdn.process(impulse, output, 0, output.length);
		assertTrue(energy(output, 0, output.length) > 0.0);

		fdn.reset();
		float[] silence = new float[4_000];
		float[] cleared = new float[4_000];
		fdn.process(silence, cleared, 0, cleared.length);
		assertEquals(0.0, energy(cleared, 0, cleared.length), 0.0);
	}

	private static float[] impulseResponse(
			AcousticBands rt60,
			int samples
	) {
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE);
		fdn.configure(rt60, 0.5, 0.0);
		float[] input = new float[samples];
		float[] output = new float[samples];
		input[0] = 1.0F;
		fdn.process(input, output, 0, samples);
		return output;
	}

	private static double energy(float[] values, int start, int end) {
		double result = 0.0;
		for (int index = start; index < end; index++) {
			result += values[index] * values[index];
		}
		return result;
	}

	private static double maximumAbsolute(float[] values) {
		double result = 0.0;
		for (float value : values) {
			result = Math.max(result, Math.abs(value));
		}
		return result;
	}
}
