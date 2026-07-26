package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdfaSerialShelvingIirDesignerTest {
	private static final double SAMPLE_RATE = 48_000.0;
	private static final UdfaInfiniteWedgeFilter TARGET =
			new UdfaInfiniteWedgeFilter(
					UdfaInfiniteWedgeFilter.Parameters.published2023()
			);
	private static final InfiniteWedgeGeometry GEOMETRY =
			new InfiniteWedgeGeometry(
					2.0,
					3.0,
					0.0,
					Math.PI + 0.6,
					2.0 * Math.PI,
					Math.PI / 2.0,
					343.0
			);

	@Test
	void fourShelvesRemainStableAndTrackReferenceAcrossAudioBand() {
		UdfaSerialShelvingIirDesigner.Design design =
				UdfaSerialShelvingIirDesigner.design(
						TARGET, GEOMETRY, SAMPLE_RATE, 4
				);

		assertEquals(4, design.sections().size());
		double rmsErrorDb = rmsError(design, GEOMETRY);
		assertTrue(rmsErrorDb < 0.5, "RMS error was " + rmsErrorDb + " dB");
	}

	@Test
	void impulseProcessorIsFiniteAndDecays() {
		UdfaSerialShelvingIirDesigner.Design design =
				UdfaSerialShelvingIirDesigner.design(
						TARGET, GEOMETRY, SAMPLE_RATE, 4
				);
		UdfaSerialShelvingIirDesigner.Processor processor = design.newProcessor();
		double tailEnergy = 0.0;
		for (int sample = 0; sample < 16_384; sample++) {
			double output = processor.process(sample == 0 ? 1.0 : 0.0);
			assertTrue(Double.isFinite(output));
			if (sample >= 8_192) {
				tailEnergy += output * output;
			}
		}
		assertTrue(tailEnergy < 1.0e-12);
	}

	@Test
	void distantDeepShadowStillMeetsHalfDbRmsGate() {
		InfiniteWedgeGeometry distant = new InfiniteWedgeGeometry(
				24.0,
				12.0,
				0.0,
				Math.PI + 1.5,
				2.0 * Math.PI,
				Math.PI / 2.0,
				343.0
		);
		UdfaSerialShelvingIirDesigner.Design design =
				UdfaSerialShelvingIirDesigner.design(
						TARGET, distant, SAMPLE_RATE, 4
				);

		assertTrue(rmsError(design, distant) < 0.5);
	}

	@Test
	void dualFilterTransitionStartsContinuousAndFinishesOnNewState() {
		UdfaSerialShelvingIirDesigner.Design first =
				UdfaSerialShelvingIirDesigner.design(
						TARGET, GEOMETRY, SAMPLE_RATE, 4
				);
		InfiniteWedgeGeometry changedGeometry = new InfiniteWedgeGeometry(
				8.0,
				2.0,
				0.0,
				Math.PI + 1.2,
				2.0 * Math.PI,
				Math.PI / 2.0,
				343.0
		);
		UdfaSerialShelvingIirDesigner.Design second =
				UdfaSerialShelvingIirDesigner.design(
						TARGET, changedGeometry, SAMPLE_RATE, 4
				);
		UdfaSerialShelvingIirDesigner.TimeVaryingProcessor timeVarying =
				first.newTimeVaryingProcessor();
		UdfaSerialShelvingIirDesigner.Processor oldReference = first.newProcessor();
		for (int sample = 0; sample < 1_000; sample++) {
			double input = Math.sin(2.0 * Math.PI * 440.0 * sample / SAMPLE_RATE);
			assertEquals(oldReference.process(input), timeVarying.process(input), 1.0e-12);
		}

		int transitionSamples = 2_400;
		timeVarying.update(second, transitionSamples);
		UdfaSerialShelvingIirDesigner.Processor newReference = second.newProcessor();
		for (int sample = 0; sample < transitionSamples + 100; sample++) {
			double input = Math.sin(2.0 * Math.PI * 880.0 * sample / SAMPLE_RATE);
			double oldOutput = oldReference.process(input);
			double newOutput = newReference.process(input);
			double actual = timeVarying.process(input);
			if (sample == 0) {
				assertEquals(oldOutput, actual, 1.0e-12);
			}
			if (sample >= transitionSamples) {
				assertEquals(newOutput, actual, 1.0e-12);
			}
		}
		assertTrue(!timeVarying.transitioning());
	}

	private static double rmsError(
			UdfaSerialShelvingIirDesigner.Design design,
			InfiniteWedgeGeometry geometry
	) {
		double squaredError = 0.0;
		int count = 96;
		for (int index = 0; index < count; index++) {
			double frequency = 20.0 * Math.pow(
					16_000.0 / 20.0,
					(double) index / (count - 1)
			);
			double target = TARGET.pressureMagnitude(frequency, geometry);
			double actual = design.pressureMagnitude(frequency);
			double errorDb = 20.0 * Math.log10(actual / target);
			squaredError += errorDb * errorDb;
		}
		return Math.sqrt(squaredError / count);
	}
}
