package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticPropagation;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.TonalComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DopplerShift {
	private static final double MIN_RATIO = 0.5;
	private static final double MAX_RATIO = 2.0;

	private DopplerShift() {
	}

	public static Result apply(
			AcousticEmissionFrame emission,
			AcousticSourceFrame source,
			AcousticListenerFrame listener,
			double soundSpeedMetersPerSecond
	) {
		Objects.requireNonNull(emission, "emission");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(listener, "listener");
		AcousticVector lineOfSight = source.positionMeters().subtract(listener.positionMeters());
		double listenerRadial = 0.0;
		double sourceRadial = 0.0;
		double ratio = AcousticPropagation.dopplerRatio(
				soundSpeedMetersPerSecond,
				listenerRadial,
				sourceRadial
		);
		if (lineOfSight.length() > 1.0e-6) {
			AcousticVector direction = lineOfSight.normalized();
			double radialLimit = soundSpeedMetersPerSecond * 0.9;
			listenerRadial = clamp(
					listener.velocityMetersPerSecond().dot(direction),
					-radialLimit,
					radialLimit
			);
			sourceRadial = clamp(
					source.velocityMetersPerSecond().dot(direction),
					-radialLimit,
					radialLimit
			);
			ratio = AcousticPropagation.dopplerRatio(
					soundSpeedMetersPerSecond,
					listenerRadial,
					sourceRadial
			);
			ratio = Math.max(MIN_RATIO, Math.min(MAX_RATIO, ratio));
		}

		List<TonalComponent> shifted = new ArrayList<>(emission.tones().size());
		for (TonalComponent tone : emission.tones()) {
			shifted.add(new TonalComponent(
					tone.kind(),
					tone.rotorIndex(),
					tone.order(),
					tone.frequencyHz() * ratio,
					tone.linearAmplitude(),
					tone.phaseRadians()
			));
		}
		return new Result(
				new AcousticEmissionFrame(shifted, emission.broadbandEnergy()),
				ratio,
				new Kinematics(
						soundSpeedMetersPerSecond,
						listenerRadial,
						sourceRadial
				)
		);
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	public record Result(
			AcousticEmissionFrame emission,
			double frequencyRatio,
			Kinematics kinematics
	) {
		public Result {
			Objects.requireNonNull(emission, "emission");
			Objects.requireNonNull(kinematics, "kinematics");
			if (!Double.isFinite(frequencyRatio)
					|| frequencyRatio <= 0.0) {
				throw new IllegalArgumentException(
						"frequencyRatio must be finite and positive"
				);
			}
		}
	}

	public record Kinematics(
			double soundSpeedMetersPerSecond,
			double listenerRadialVelocityMetersPerSecond,
			double sourceRadialVelocityMetersPerSecond
	) {
		public Kinematics {
			AcousticPropagation.dopplerRatio(
					soundSpeedMetersPerSecond,
					listenerRadialVelocityMetersPerSecond,
					sourceRadialVelocityMetersPerSecond
			);
		}
	}
}
