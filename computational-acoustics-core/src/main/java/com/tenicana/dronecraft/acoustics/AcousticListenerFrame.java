package com.tenicana.dronecraft.acoustics;

import java.util.Objects;

public record AcousticListenerFrame(
		AcousticVector positionMeters,
		AcousticVector velocityMetersPerSecond
) {
	public AcousticListenerFrame {
		Objects.requireNonNull(positionMeters, "positionMeters");
		Objects.requireNonNull(velocityMetersPerSecond, "velocityMetersPerSecond");
	}
}
