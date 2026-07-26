package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.TonalComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies energy-domain transmission to tonal pressure amplitudes and
 * broadband energies.
 */
public final class EmissionTransmission {
	public static final double LOW_MID_CROSSOVER_HZ = 500.0;
	public static final double MID_HIGH_CROSSOVER_HZ = 2_000.0;

	private EmissionTransmission() {
	}

	public static AcousticEmissionFrame apply(
			AcousticEmissionFrame emission,
			AcousticBands transmissionEnergyGain
	) {
		Objects.requireNonNull(emission, "emission");
		Objects.requireNonNull(transmissionEnergyGain, "transmissionEnergyGain");
		List<TonalComponent> propagatedTones = new ArrayList<>(emission.tones().size());
		for (TonalComponent tone : emission.tones()) {
			double energyGain = bandGain(tone.frequencyHz(), transmissionEnergyGain);
			propagatedTones.add(new TonalComponent(
					tone.kind(),
					tone.rotorIndex(),
					tone.order(),
					tone.frequencyHz(),
					tone.linearAmplitude() * Math.sqrt(energyGain),
					tone.phaseRadians()
			));
		}
		return new AcousticEmissionFrame(
				propagatedTones,
				emission.broadbandEnergy().multiply(transmissionEnergyGain)
		);
	}

	public static double bandGain(double frequencyHz, AcousticBands gain) {
		if (!Double.isFinite(frequencyHz) || frequencyHz < 0.0) {
			throw new IllegalArgumentException("frequencyHz must be finite and non-negative");
		}
		if (frequencyHz < LOW_MID_CROSSOVER_HZ) {
			return gain.low();
		}
		if (frequencyHz < MID_HIGH_CROSSOVER_HZ) {
			return gain.mid();
		}
		return gain.high();
	}
}
