package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiPathSolverTest {
	@Test
	void averagesPathTransmissionInEnergyDomain() {
		AcousticMaterial nearOpaque = new AcousticMaterial(
				"near-opaque",
				new com.tenicana.dronecraft.acoustics.AcousticBands(120.0, 120.0, 120.0),
				com.tenicana.dronecraft.acoustics.AcousticBands.SILENT,
				0.0
		);
		List<MultiPathSolver.Probe> probes = List.of(
				new MultiPathSolver.Probe(new AcousticVector(0.5, 0.25, 0.5), 0.5),
				new MultiPathSolver.Probe(new AcousticVector(0.5, 1.25, 0.5), 0.5)
		);

		MultiPathSolver.Result result = MultiPathSolver.solve(
				probes,
				new AcousticVector(2.5, 1.25, 0.5),
				(x, y, z) -> x == 1 && y == 0
						? DirectPathSolver.MaterialSample.full(nearOpaque)
						: DirectPathSolver.MaterialSample.AIR,
				16
		);

		assertEquals(0.5, result.transmissionEnergyGain().low(), 1.0e-4);
		assertEquals(2, result.pathCount());
		assertTrue(result.complete());
	}

	@Test
	void normalizesArbitraryPositiveProbeWeights() {
		MultiPathSolver.Result result = MultiPathSolver.solve(
				List.of(
						new MultiPathSolver.Probe(new AcousticVector(0.5, 0.5, 0.5), 2.0),
						new MultiPathSolver.Probe(new AcousticVector(0.5, 1.5, 0.5), 3.0)
				),
				new AcousticVector(1.5, 1.0, 0.5),
				(x, y, z) -> DirectPathSolver.MaterialSample.AIR,
				8
		);

		assertEquals(1.0, result.transmissionEnergyGain().mid(), 1.0e-12);
	}
}
