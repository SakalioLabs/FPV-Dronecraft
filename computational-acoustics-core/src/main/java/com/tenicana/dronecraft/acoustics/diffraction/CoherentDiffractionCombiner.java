package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.List;
import java.util.Objects;

/**
 * Coherent tonal combination of alternative diffraction paths with explicit
 * relative propagation phase. Broadband energy remains a separate operation.
 */
public final class CoherentDiffractionCombiner {
	private CoherentDiffractionCombiner() {
	}

	public static Result combine(
			double frequencyHz,
			double speedOfSoundMetersPerSecond,
			List<PathContribution> paths
	) {
		if (!(frequencyHz >= 0.0) || !Double.isFinite(frequencyHz)) {
			throw new IllegalArgumentException("frequencyHz must be finite and non-negative");
		}
		if (!(speedOfSoundMetersPerSecond > 0.0)
				|| !Double.isFinite(speedOfSoundMetersPerSecond)) {
			throw new IllegalArgumentException(
					"speedOfSoundMetersPerSecond must be positive and finite"
			);
		}
		paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
		if (paths.isEmpty()) {
			return new Result(ComplexPressure.ZERO, 0.0, 0.0);
		}
		double referenceLength = paths.stream()
				.mapToDouble(PathContribution::pathLengthMeters)
				.min()
				.orElseThrow();
		ComplexPressure sum = ComplexPressure.ZERO;
		double incoherentEnergy = 0.0;
		for (PathContribution path : paths) {
			double relativeDelay = (
					path.pathLengthMeters() - referenceLength
			) / speedOfSoundMetersPerSecond;
			ComplexPressure propagationPhase = ComplexPressure.fromPolar(
					1.0,
					-2.0 * Math.PI * frequencyHz * relativeDelay
			);
			ComplexPressure scaled = path.transfer()
					.scale(path.pressureScale());
			sum = sum.add(scaled.multiply(propagationPhase));
			incoherentEnergy += scaled.energy();
		}
		return new Result(
				sum,
				referenceLength / speedOfSoundMetersPerSecond,
				incoherentEnergy
		);
	}

	public static ComplexPressure cascade(List<ComplexPressure> edgeTransfers) {
		ComplexPressure result = ComplexPressure.ONE;
		for (ComplexPressure transfer : List.copyOf(edgeTransfers)) {
			result = result.multiply(transfer);
		}
		return result;
	}

	public record PathContribution(
			double pathLengthMeters,
			double pressureScale,
			ComplexPressure transfer
	) {
		public PathContribution {
			if (!(pathLengthMeters > 0.0)
					|| !(pressureScale >= 0.0)
					|| !Double.isFinite(pathLengthMeters + pressureScale)) {
				throw new IllegalArgumentException(
						"path length and pressure scale must be finite"
				);
			}
			Objects.requireNonNull(transfer, "transfer");
		}
	}

	public record Result(
			ComplexPressure coherentPressure,
			double referenceDelaySeconds,
			double incoherentEnergy
	) {
		public Result {
			Objects.requireNonNull(coherentPressure, "coherentPressure");
		}

		public double coherentEnergy() {
			return coherentPressure.energy();
		}
	}
}
