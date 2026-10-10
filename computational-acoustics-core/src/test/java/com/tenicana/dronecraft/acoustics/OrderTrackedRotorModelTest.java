package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTrackedRotorModelTest {
	@Test
	void derivesPhysicalOrdersAndClampsAtNyquistGuard() {
		RotorAcousticState rotor = new RotorAcousticState(
				30_000.0, 1.0, 1.0, 0.0635, 3, 7, 1, 0.25
		);
		AcousticSourceFrame source = new AcousticSourceFrame(
				42L, 1L, AcousticVector.ZERO, AcousticVector.ZERO,
				new AcousticVector(0.0, 1.0, 0.0), 0.1, List.of(rotor)
		);

		AcousticEmissionFrame output = new OrderTrackedRotorModel(
				OrderTrackedRotorModel.Parameters.researchDefaults()
		).evaluate(source, 8_000);

		assertTrue(output.tones().stream().anyMatch(tone ->
				tone.kind() == TonalComponent.Kind.BLADE_PASS
						&& tone.order() == 1
						&& tone.frequencyHz() == 1_500.0
		));
		assertTrue(output.tones().stream().allMatch(tone -> tone.frequencyHz() < 3_600.0));
		assertTrue(output.tones().stream().anyMatch(tone ->
				tone.kind() == TonalComponent.Kind.ELECTRICAL
		));
		assertFalse(output.tones().stream().anyMatch(tone ->
				tone.kind() == TonalComponent.Kind.COGGING_CANDIDATE
		));
		assertTrue(output.broadbandEnergy().totalEnergy() > 0.0);
	}

	@Test
	void excludesTonesAtTheFortyFivePercentSampleRateGuard() {
		RotorAcousticState rotor = new RotorAcousticState(
				9_000.0, 1.0, 1.0, 0.0635, 3, 7, 1, 0.0
		);
		OrderTrackedRotorModel model = new OrderTrackedRotorModel(
				new OrderTrackedRotorModel.Parameters(
						8,
						1.0,
						0.0,
						1.0,
						0.0,
						0.0,
						0.0
				)
		);

		AcousticEmissionFrame output = model.evaluate(frame(List.of(rotor)), 8_000);

		assertTrue(output.tones().stream().anyMatch(tone -> tone.frequencyHz() == 3_150.0));
		assertFalse(output.tones().stream().anyMatch(tone -> tone.frequencyHz() == 3_600.0));
	}

	@Test
	void sumsBroadbandAsPowerAcrossRotors() {
		RotorAcousticState rotor = new RotorAcousticState(
				12_000.0, 0.5, 1.0, 0.0635, 3, 7, 1, 0.0
		);
		OrderTrackedRotorModel model = new OrderTrackedRotorModel(
				OrderTrackedRotorModel.Parameters.researchDefaults()
		);
		AcousticSourceFrame one = frame(List.of(rotor));
		AcousticSourceFrame two = frame(List.of(rotor, rotor));

		assertEquals(
				2.0 * model.evaluate(one, 48_000).broadbandEnergy().totalEnergy(),
				model.evaluate(two, 48_000).broadbandEnergy().totalEnergy(),
				1.0e-12
		);
	}

	@Test
	void appliesProfileOwnedBroadbandEnergyDistribution() {
		OrderTrackedRotorModel.Parameters defaults =
				OrderTrackedRotorModel.Parameters.researchDefaults();
		OrderTrackedRotorModel model = new OrderTrackedRotorModel(
				new OrderTrackedRotorModel.Parameters(
						defaults.bladePassHarmonics(),
						defaults.harmonicRolloff(),
						defaults.shaftAmplitude(),
						defaults.bladePassAmplitude(),
						defaults.electricalAmplitude(),
						defaults.coggingCandidateAmplitude(),
						defaults.broadbandEnergy(),
						defaults.operatingPointGainCurve(),
						new AcousticBands(1.0, 0.0, 0.0)
				)
		);
		RotorAcousticState rotor = new RotorAcousticState(
				12_000.0, 0.5, 1.0, 0.0635, 3, 7, 1, 0.0
		);

		AcousticBands broadband = model.evaluate(
				frame(List.of(rotor)),
				48_000
		).broadbandEnergy();

		assertTrue(broadband.low() > 0.0);
		assertEquals(0.0, broadband.mid());
		assertEquals(0.0, broadband.high());
	}

	@Test
	void rejectsBroadbandDistributionThatDoesNotSumToUnity() {
		OrderTrackedRotorModel.Parameters defaults =
				OrderTrackedRotorModel.Parameters.researchDefaults();

		assertThrows(
				IllegalArgumentException.class,
				() -> new OrderTrackedRotorModel.Parameters(
						defaults.bladePassHarmonics(),
						defaults.harmonicRolloff(),
						defaults.shaftAmplitude(),
						defaults.bladePassAmplitude(),
						defaults.electricalAmplitude(),
						defaults.coggingCandidateAmplitude(),
						defaults.broadbandEnergy(),
						defaults.operatingPointGainCurve(),
						new AcousticBands(0.2, 0.2, 0.2)
				)
		);
	}

	@Test
	void appliesSeparateRotorMotorAndBroadbandOperatingPointGains() {
		RotorAcousticState rotor = new RotorAcousticState(
				20_000.0, 0.8, 1.0, 0.0635, 3, 7, 1, 0.0
		);
		OrderTrackedRotorModel.Parameters defaults =
				OrderTrackedRotorModel.Parameters.researchDefaults();
		OrderTrackedRotorModel baseline = new OrderTrackedRotorModel(defaults);
		OrderTrackedRotorModel calibrated = new OrderTrackedRotorModel(
				new OrderTrackedRotorModel.Parameters(
						defaults.bladePassHarmonics(),
						defaults.harmonicRolloff(),
						defaults.shaftAmplitude(),
						defaults.bladePassAmplitude(),
						defaults.electricalAmplitude(),
						defaults.coggingCandidateAmplitude(),
						defaults.broadbandEnergy(),
						new RotorOperatingPointGainCurve(List.of(
								new RotorOperatingPointGainCurve.Anchor(
										20_000.0,
										6.0,
										-6.0,
										3.0
								)
						))
				)
		);

		AcousticEmissionFrame baselineFrame = baseline.evaluate(frame(List.of(rotor)), 48_000);
		AcousticEmissionFrame calibratedFrame = calibrated.evaluate(frame(List.of(rotor)), 48_000);

		assertEquals(
				dbToAmplitude(6.0),
				tone(calibratedFrame, TonalComponent.Kind.BLADE_PASS).linearAmplitude()
						/ tone(baselineFrame, TonalComponent.Kind.BLADE_PASS).linearAmplitude(),
				1.0e-12
		);
		assertEquals(
				dbToAmplitude(-6.0),
				tone(calibratedFrame, TonalComponent.Kind.ELECTRICAL).linearAmplitude()
						/ tone(baselineFrame, TonalComponent.Kind.ELECTRICAL).linearAmplitude(),
				1.0e-12
		);
		assertEquals(
				Math.pow(10.0, 3.0 / 10.0),
				calibratedFrame.broadbandEnergy().totalEnergy()
						/ baselineFrame.broadbandEnergy().totalEnergy(),
				1.0e-12
		);
	}

	@Test
	void changesBroadbandSpectralShapeWithRpmAfterTheCommonGain() {
		OrderTrackedRotorModel.Parameters defaults = OrderTrackedRotorModel.Parameters.researchDefaults();
		RotorOperatingPointGainCurve common = new RotorOperatingPointGainCurve(List.of(
				new RotorOperatingPointGainCurve.Anchor(4_000.0, 2.0, -2.0, 3.0),
				new RotorOperatingPointGainCurve.Anchor(16_000.0, 6.0, 0.0, 9.0)
		));
		OrderTrackedRotorModel baseline = new OrderTrackedRotorModel(new OrderTrackedRotorModel.Parameters(
				12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08, common, defaults.broadbandDistribution()
		));
		OrderTrackedRotorModel calibrated = new OrderTrackedRotorModel(new OrderTrackedRotorModel.Parameters(
				12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08, common, defaults.broadbandDistribution(),
				new RotorBroadbandGainCurve(List.of(
						new RotorBroadbandGainCurve.Anchor(4_000.0, -6.0, 0.0, 6.0),
						new RotorBroadbandGainCurve.Anchor(16_000.0, 6.0, 0.0, -6.0)
				))
		));
		AcousticBands[] calibratedBands = new AcousticBands[3];
		double[] rpms = {4_000.0, 8_000.0, 16_000.0};
		for (int index = 0; index < rpms.length; index++) {
			RotorAcousticState rotor = new RotorAcousticState(rpms[index], 0.8, 1.2, 0.0635, 3, 7, 1, 0.0);
			AcousticEmissionFrame legacyOutput = baseline.evaluate(frame(List.of(rotor)), 48_000);
			AcousticEmissionFrame bandOutput = calibrated.evaluate(frame(List.of(rotor)), 48_000);
			calibratedBands[index] = bandOutput.broadbandEnergy();
			double lowDb = -6.0 + index * 6.0;
			assertEquals(Math.pow(10.0, lowDb / 10.0),
					bandOutput.broadbandEnergy().low() / legacyOutput.broadbandEnergy().low(), 1.0e-12);
			assertEquals(1.0, bandOutput.broadbandEnergy().mid() / legacyOutput.broadbandEnergy().mid(), 1.0e-12);
			assertEquals(Math.pow(10.0, -lowDb / 10.0),
					bandOutput.broadbandEnergy().high() / legacyOutput.broadbandEnergy().high(), 1.0e-12);
			assertEquals(legacyOutput.tones(), bandOutput.tones());
		}
		assertTrue(calibratedBands[0].high() > calibratedBands[0].low());
		assertTrue(calibratedBands[2].low() > calibratedBands[2].high());
		AcousticBands mixed = calibrated.evaluate(frame(List.of(
				new RotorAcousticState(4_000.0, 0.8, 1.2, 0.0635, 3, 7, 1, 0.0),
				new RotorAcousticState(16_000.0, 0.8, 1.2, 0.0635, 3, 7, 1, 0.0)
		)), 48_000).broadbandEnergy();
		assertEquals(calibratedBands[0].add(calibratedBands[2]), mixed);
	}

	@Test
	void interpolatesIndependentHarmonicAmplitudeGainsAndClampsEndpoints() {
		RotorHarmonicGainCurve curve = new RotorHarmonicGainCurve(Map.of(
				1, List.of(new RotorHarmonicGainCurve.Anchor(1_000.0, -6.0),
						new RotorHarmonicGainCurve.Anchor(4_000.0, 6.0)),
				2, List.of(new RotorHarmonicGainCurve.Anchor(1_000.0, 0.0),
						new RotorHarmonicGainCurve.Anchor(2_000.0, 6.0),
						new RotorHarmonicGainCurve.Anchor(8_000.0, -6.0)),
				64, List.of(new RotorHarmonicGainCurve.Anchor(3_000.0, 60.0))
		));
		assertFalse(curve.isUnity());
		assertEquals(dbToAmplitude(-6.0), curve.evaluate(1, 0.0));
		assertEquals(dbToAmplitude(-6.0), curve.evaluate(1, 500.0));
		assertEquals(1.0, curve.evaluate(1, 2_000.0), 1.0e-12);
		assertEquals(dbToAmplitude(6.0), curve.evaluate(1, 10_000.0));
		assertEquals(dbToAmplitude(3.0), curve.evaluate(2, Math.sqrt(2_000_000.0)), 1.0e-12);
		assertEquals(dbToAmplitude(6.0), curve.evaluate(2, 2_000.0));
		assertEquals(1.0, curve.evaluate(2, 4_000.0), 1.0e-12);
		assertEquals(dbToAmplitude(-6.0), curve.evaluate(2, 8_000.0));
		assertEquals(1.0, curve.evaluate(3, 2_000.0));
		assertEquals(1_000.0, curve.evaluate(64, 0.0));
		assertEquals(1_000.0, curve.evaluate(64, Double.MAX_VALUE));
		RotorHarmonicGainCurve attenuation = new RotorHarmonicGainCurve(Map.of(
				1, List.of(new RotorHarmonicGainCurve.Anchor(1.0, -120.0))
		));
		assertEquals(1.0e-6, attenuation.evaluate(1, 1.0));
	}

	@Test
	void copiesHarmonicAnchorsDeeplyAndRecognizesSemanticUnity() {
		List<RotorHarmonicGainCurve.Anchor> mutableAnchors = new ArrayList<>(List.of(
				new RotorHarmonicGainCurve.Anchor(4_000.0, 6.0)
		));
		Map<Integer, List<RotorHarmonicGainCurve.Anchor>> mutableMap = new HashMap<>();
		mutableMap.put(2, mutableAnchors);
		RotorHarmonicGainCurve curve = new RotorHarmonicGainCurve(mutableMap);
		mutableAnchors.clear();
		mutableMap.clear();
		assertEquals(dbToAmplitude(6.0), curve.evaluate(2, 4_000.0));
		assertThrows(UnsupportedOperationException.class, () -> curve.anchors().clear());
		assertThrows(UnsupportedOperationException.class, () -> curve.anchors().get(2).clear());
		assertTrue(RotorHarmonicGainCurve.unity().anchors().isEmpty());
		assertTrue(RotorHarmonicGainCurve.unity().isUnity());
		RotorHarmonicGainCurve zeroAnchors = new RotorHarmonicGainCurve(Map.of(
				1, List.of(new RotorHarmonicGainCurve.Anchor(4_000.0, 0.0),
						new RotorHarmonicGainCurve.Anchor(16_000.0, -0.0)),
				9, List.of(new RotorHarmonicGainCurve.Anchor(8_000.0, 0.0))
		));
		assertTrue(zeroAnchors.isUnity());
		assertEquals(1.0, zeroAnchors.evaluate(1, 8_000.0));
		assertEquals(1.0, zeroAnchors.evaluate(64, 0.0));
	}

	@Test
	void rejectsInvalidHarmonicCurveInputsEvenForUnityAndUnspecifiedOrders() {
		List<RotorHarmonicGainCurve.Anchor> anchors = List.of(new RotorHarmonicGainCurve.Anchor(4_000.0, 6.0));
		RotorHarmonicGainCurve curve = new RotorHarmonicGainCurve(Map.of(1, anchors));
		for (int harmonic : new int[]{0, 65, -1}) {
			assertThrows(IllegalArgumentException.class, () -> new RotorHarmonicGainCurve(Map.of(harmonic, anchors)));
			assertThrows(IllegalArgumentException.class, () -> RotorHarmonicGainCurve.unity().evaluate(harmonic, 4_000.0));
			assertThrows(IllegalArgumentException.class, () -> curve.evaluate(harmonic, 4_000.0));
		}
		for (double rpm : new double[]{-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class, () -> RotorHarmonicGainCurve.unity().evaluate(1, rpm));
			assertThrows(IllegalArgumentException.class, () -> curve.evaluate(2, rpm));
		}
		for (double rpm : new double[]{0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class, () -> new RotorHarmonicGainCurve.Anchor(rpm, 0.0));
		}
		for (double gainDb : new double[]{-120.01, 60.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class, () -> new RotorHarmonicGainCurve.Anchor(4_000.0, gainDb));
		}
		assertThrows(IllegalArgumentException.class, () -> new RotorHarmonicGainCurve(Map.of(1, List.of())));
		assertThrows(IllegalArgumentException.class, () -> new RotorHarmonicGainCurve(Map.of(1, List.of(
				new RotorHarmonicGainCurve.Anchor(4_000.0, 0.0), new RotorHarmonicGainCurve.Anchor(4_000.0, 6.0)
		))));
		assertThrows(IllegalArgumentException.class, () -> new RotorHarmonicGainCurve(Map.of(1, List.of(
				new RotorHarmonicGainCurve.Anchor(8_000.0, 0.0), new RotorHarmonicGainCurve.Anchor(4_000.0, 6.0)
		))));
		assertThrows(NullPointerException.class, () -> new OrderTrackedRotorModel.Parameters(
				12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08, RotorOperatingPointGainCurve.unity(),
				new AcousticBands(0.2, 0.5, 0.3), RotorBroadbandGainCurve.unity(), null
		));
	}

	@Test
	void changesIndividualBpfOrdersWithRpmAndMultipliesTheCommonRotorGainOnly() {
		OrderTrackedRotorModel.Parameters defaults = OrderTrackedRotorModel.Parameters.researchDefaults();
		RotorOperatingPointGainCurve common = new RotorOperatingPointGainCurve(List.of(
				new RotorOperatingPointGainCurve.Anchor(4_000.0, 2.0, -2.0, 3.0),
				new RotorOperatingPointGainCurve.Anchor(16_000.0, 6.0, 0.0, 9.0)
		));
		RotorBroadbandGainCurve bands = new RotorBroadbandGainCurve(List.of(
				new RotorBroadbandGainCurve.Anchor(4_000.0, 1.0, -2.0, 3.0),
				new RotorBroadbandGainCurve.Anchor(16_000.0, -3.0, 2.0, 1.0)
		));
		RotorHarmonicGainCurve harmonics = new RotorHarmonicGainCurve(Map.of(
				1, List.of(new RotorHarmonicGainCurve.Anchor(4_000.0, -6.0),
						new RotorHarmonicGainCurve.Anchor(16_000.0, 6.0)),
				2, List.of(new RotorHarmonicGainCurve.Anchor(4_000.0, 6.0),
						new RotorHarmonicGainCurve.Anchor(16_000.0, -6.0)),
				4, List.of(new RotorHarmonicGainCurve.Anchor(4_000.0, 3.0))
		));
		OrderTrackedRotorModel baseline = new OrderTrackedRotorModel(new OrderTrackedRotorModel.Parameters(
				12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08, common, defaults.broadbandDistribution(), bands
		));
		OrderTrackedRotorModel calibrated = new OrderTrackedRotorModel(new OrderTrackedRotorModel.Parameters(
				12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08, common, defaults.broadbandDistribution(), bands, harmonics
		));
		double[] rpms = {4_000.0, 8_000.0, 16_000.0};
		for (int index = 0; index < rpms.length; index++) {
			RotorAcousticState rotor = new RotorAcousticState(rpms[index], 0.8, 1.2, 0.0635, 3, 7, 1, 0.25);
			AcousticSourceFrame source = frame(List.of(rotor));
			AcousticEmissionFrame legacyOutput = baseline.evaluate(source, 48_000);
			AcousticEmissionFrame orderOutput = calibrated.evaluate(source, 48_000);
			AcousticEmissionFrame unityOutput = new OrderTrackedRotorModel(defaults).evaluate(source, 48_000);
			assertEquals(legacyOutput.broadbandEnergy(), orderOutput.broadbandEnergy());
			assertEquals(legacyOutput.tones().size(), orderOutput.tones().size());
			for (int toneIndex = 0; toneIndex < legacyOutput.tones().size(); toneIndex++) {
				TonalComponent before = legacyOutput.tones().get(toneIndex);
				TonalComponent after = orderOutput.tones().get(toneIndex);
				assertEquals(before.frequencyHz(), after.frequencyHz());
				assertEquals(before.phaseRadians(), after.phaseRadians());
				assertEquals(before.rotorIndex(), after.rotorIndex());
				assertEquals(before.order(), after.order());
				assertEquals(before.kind(), after.kind());
				if (before.kind() != TonalComponent.Kind.BLADE_PASS) {
					assertEquals(before, after);
					continue;
				}
				double harmonicDb = switch (before.order()) {
					case 1 -> -6.0 + index * 6.0;
					case 2 -> 6.0 - index * 6.0;
					case 4 -> 3.0;
					default -> 0.0;
				};
				assertEquals(dbToAmplitude(harmonicDb), after.linearAmplitude() / before.linearAmplitude(), 1.0e-12);
				assertEquals(dbToAmplitude(2.0 + index * 2.0 + harmonicDb),
						after.linearAmplitude() / harmonic(unityOutput, 0, before.order()).linearAmplitude(), 1.0e-12);
			}
			assertEquals(Math.pow(2.0, -1.15) * dbToAmplitude(12.0 - index * 12.0),
					harmonic(orderOutput, 0, 2).linearAmplitude() / harmonic(orderOutput, 0, 1).linearAmplitude(), 1.0e-12);
		}
		AcousticSourceFrame mixedSource = frame(List.of(
				new RotorAcousticState(4_000.0, 0.8, 1.2, 0.0635, 3, 7, 1, 0.25),
				new RotorAcousticState(16_000.0, 0.8, 1.2, 0.0635, 3, 7, 1, 0.25)
		));
		AcousticEmissionFrame legacyMixed = baseline.evaluate(mixedSource, 48_000);
		AcousticEmissionFrame orderMixed = calibrated.evaluate(mixedSource, 48_000);
		assertEquals(dbToAmplitude(-6.0), harmonic(orderMixed, 0, 1).linearAmplitude()
				/ harmonic(legacyMixed, 0, 1).linearAmplitude(), 1.0e-12);
		assertEquals(dbToAmplitude(6.0), harmonic(orderMixed, 1, 1).linearAmplitude()
				/ harmonic(legacyMixed, 1, 1).linearAmplitude(), 1.0e-12);
	}

	@Test
	void legacyConstructorsAndResearchDefaultsKeepTheOriginalOutput() {
		OrderTrackedRotorModel.Parameters defaults = OrderTrackedRotorModel.Parameters.researchDefaults();
		List<OrderTrackedRotorModel.Parameters> constructors = List.of(
				new OrderTrackedRotorModel.Parameters(12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08),
				new OrderTrackedRotorModel.Parameters(12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08,
						RotorOperatingPointGainCurve.unity()),
				new OrderTrackedRotorModel.Parameters(12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08,
						RotorOperatingPointGainCurve.unity(), new AcousticBands(0.2, 0.5, 0.3)),
				new OrderTrackedRotorModel.Parameters(12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08,
						RotorOperatingPointGainCurve.unity(), new AcousticBands(0.2, 0.5, 0.3),
						RotorBroadbandGainCurve.unity()),
				new OrderTrackedRotorModel.Parameters(12, 1.15, 0.05, 0.18, 0.035, 0.012, 0.08,
						RotorOperatingPointGainCurve.unity(), new AcousticBands(0.2, 0.5, 0.3),
						RotorBroadbandGainCurve.unity(), RotorHarmonicGainCurve.unity())
		);
		for (double rpm : new double[]{0.0, 4_000.0, 8_000.0, 30_000.0}) {
			RotorAcousticState rotor = new RotorAcousticState(rpm, 0.7, 1.3, 0.0635, 3, 7, 1, 0.25);
			AcousticEmissionFrame expected = new OrderTrackedRotorModel(defaults).evaluate(frame(List.of(rotor)), 48_000);
			for (OrderTrackedRotorModel.Parameters legacy : constructors) {
				assertTrue(legacy.broadbandOperatingPointGainCurve().isUnity());
				assertTrue(legacy.rotorHarmonicGainCurve().isUnity());
				assertEquals(expected, new OrderTrackedRotorModel(legacy).evaluate(frame(List.of(rotor)), 48_000));
			}
			double originalScale = rpm <= 1.0 ? 0.0 : 0.08 * 0.7 * (0.25 + 0.75 * 1.3);
			assertEquals(new AcousticBands(0.2 * originalScale, 0.5 * originalScale, 0.3 * originalScale),
					expected.broadbandEnergy());
			if (rpm > 1.0) {
				double originalAmplitudeScale = Math.sqrt(0.7) * (0.35 + 0.65 * 1.3);
				for (TonalComponent tone : expected.tones()) {
					double originalAmplitude = switch (tone.kind()) {
						case SHAFT -> 0.05 * originalAmplitudeScale;
						case ELECTRICAL -> 0.035 * originalAmplitudeScale;
						case COGGING_CANDIDATE -> 0.012 * originalAmplitudeScale;
						case BLADE_PASS -> 0.18 * originalAmplitudeScale / Math.pow(tone.order(), 1.15);
					};
					assertEquals(originalAmplitude, tone.linearAmplitude());
				}
			}
		}
	}

	private static TonalComponent tone(
			AcousticEmissionFrame frame,
			TonalComponent.Kind kind
	) {
		return frame.tones().stream()
				.filter(tone -> tone.kind() == kind)
				.findFirst()
				.orElseThrow();
	}

	private static double dbToAmplitude(double gainDb) {
		return Math.pow(10.0, gainDb / 20.0);
	}

	private static TonalComponent harmonic(AcousticEmissionFrame frame, int rotorIndex, int order) {
		return frame.tones().stream()
				.filter(tone -> tone.kind() == TonalComponent.Kind.BLADE_PASS
						&& tone.rotorIndex() == rotorIndex && tone.order() == order)
				.findFirst().orElseThrow();
	}

	private static AcousticSourceFrame frame(List<RotorAcousticState> rotors) {
		return new AcousticSourceFrame(
				1L, 0L, AcousticVector.ZERO, AcousticVector.ZERO,
				new AcousticVector(0.0, 1.0, 0.0), 0.1, rotors
		);
	}
}
