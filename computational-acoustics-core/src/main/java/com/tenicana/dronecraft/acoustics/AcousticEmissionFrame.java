package com.tenicana.dronecraft.acoustics;

import java.util.List;
import java.util.Objects;

public record AcousticEmissionFrame(List<TonalComponent> tones, AcousticBands broadbandEnergy) {
	public AcousticEmissionFrame {
		tones = List.copyOf(Objects.requireNonNull(tones, "tones"));
		Objects.requireNonNull(broadbandEnergy, "broadbandEnergy");
	}
}
