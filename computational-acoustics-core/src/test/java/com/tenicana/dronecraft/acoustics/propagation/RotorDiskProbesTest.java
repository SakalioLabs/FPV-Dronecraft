package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotorDiskProbesTest {
	@Test
	void generatesCenterAndFourCoplanarRimPoints() {
		AcousticVector center = new AcousticVector(3.0, 4.0, 5.0);
		AcousticVector normal = new AcousticVector(0.2, 0.95, -0.1).normalized();
		AcousticSourceFrame source = new AcousticSourceFrame(
				1L,
				0L,
				center,
				AcousticVector.ZERO,
				normal,
				0.1,
				List.of(new RotorAcousticState(10_000, 0.5, 1.0, 0.1, 3, 7, 1, 0.0))
		);

		List<MultiPathSolver.Probe> probes = RotorDiskProbes.generate(source);

		assertEquals(5, probes.size());
		assertEquals(center, probes.getFirst().position());
		assertEquals(1.0, probes.stream().mapToDouble(MultiPathSolver.Probe::weight).sum(), 1.0e-12);
		for (MultiPathSolver.Probe probe : probes) {
			AcousticVector offset = probe.position().subtract(center);
			assertEquals(0.0, offset.dot(normal), 1.0e-12);
		}
		assertEquals(0.085, probes.get(1).position().subtract(center).length(), 1.0e-12);
	}
}
