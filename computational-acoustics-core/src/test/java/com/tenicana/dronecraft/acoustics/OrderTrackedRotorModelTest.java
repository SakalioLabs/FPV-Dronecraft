package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import java.util.List;

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

	private static AcousticSourceFrame frame(List<RotorAcousticState> rotors) {
		return new AcousticSourceFrame(
				1L, 0L, AcousticVector.ZERO, AcousticVector.ZERO,
				new AcousticVector(0.0, 1.0, 0.0), 0.1, rotors
		);
	}
}
