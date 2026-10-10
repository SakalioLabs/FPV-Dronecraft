package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.util.List;
import java.util.Objects;

/**
 * Energy-weighted aggregation of several direct paths.
 */
public final class MultiPathSolver {
	private MultiPathSolver() {
	}

	public static Result solve(
			List<Probe> probes,
			AcousticVector listener,
			DirectPathSolver.MaterialQuery materials,
			int maxCellsPerPath
	) {
		probes = List.copyOf(Objects.requireNonNull(probes, "probes"));
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(materials, "materials");
		if (probes.isEmpty()) {
			throw new IllegalArgumentException("at least one probe is required");
		}

		double totalWeight = probes.stream().mapToDouble(Probe::weight).sum();
		AcousticBands weightedGain = AcousticBands.SILENT;
		int visitedCells = 0;
		int materialCells = 0;
		boolean complete = true;
		for (Probe probe : probes) {
			DirectPathSolver.Result path = DirectPathSolver.solve(
					probe.position(),
					listener,
					materials,
					maxCellsPerPath
			);
			double normalizedWeight = probe.weight() / totalWeight;
			weightedGain = weightedGain.add(
					path.transmissionEnergyGain().multiply(normalizedWeight)
			);
			visitedCells += path.visitedCellCount();
			materialCells += path.materialCellCount();
			complete &= path.complete();
		}

		AcousticBands effectiveLoss = weightedGain.map(
				gain -> -10.0 * Math.log10(Math.max(gain, 1.0e-12))
		);
		return new Result(
				weightedGain,
				effectiveLoss,
				probes.size(),
				visitedCells,
				materialCells,
				complete
		);
	}

	public record Probe(AcousticVector position, double weight) {
		public Probe {
			Objects.requireNonNull(position, "position");
			if (!Double.isFinite(weight) || weight <= 0.0) {
				throw new IllegalArgumentException("weight must be finite and positive");
			}
		}
	}

	public record Result(
			AcousticBands transmissionEnergyGain,
			AcousticBands effectiveTransmissionLossDb,
			int pathCount,
			int visitedCellCount,
			int materialCellCount,
			boolean complete
	) {
	}
}
