package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Facet;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Path;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdaFirstOrderPathSolverTest {
	private static final AcousticVector SOURCE =
			new AcousticVector(1.0, 1.0, 1.0);
	private static final AcousticVector LISTENER =
			new AcousticVector(3.0, 2.0, 1.5);

	@Test
	void directPathUsesProductionDdaAndPreservesMetricLength() {
		Path path = DdaFirstOrderPathSolver.direct(
				SOURCE,
				LISTENER,
				new RoomBounds(6.0, 6.0, 2.0),
				32
		);

		assertEquals(
				LISTENER.subtract(SOURCE).length(),
				path.lengthMeters(),
				1.0e-12
		);
		assertTrue(path.visitedCells() >= 3);
		assertTrue(path.topologyVisible());
	}

	@Test
	void reflectedLengthEqualsMirroredImageDistance() {
		RoomBounds bounds = new RoomBounds(6.0, 6.0, 2.0);
		Path path = DdaFirstOrderPathSolver.reflected(
				SOURCE,
				LISTENER,
				bounds,
				Facet.CEILING,
				32
		);
		AcousticVector mirrored =
				new AcousticVector(SOURCE.x(), SOURCE.y(), 3.0);

		assertEquals(
				LISTENER.subtract(mirrored).length(),
				path.lengthMeters(),
				1.0e-12
		);
		assertEquals(2.0, path.reflectionPoint().z(), 1.0e-12);
		assertTrue(path.topologyVisible());
	}

	@Test
	void continuousCeilingCorrectionChangesTimingWithoutChangingFacet() {
		Path voxel = DdaFirstOrderPathSolver.reflected(
				SOURCE,
				LISTENER,
				new RoomBounds(6.0, 6.0, 2.0),
				Facet.CEILING,
				32
		);
		Path continuous = DdaFirstOrderPathSolver.reflected(
				SOURCE,
				LISTENER,
				new RoomBounds(5.705, 5.965, 2.355),
				Facet.CEILING,
				32
		);

		assertTrue(continuous.lengthMeters() > voxel.lengthMeters());
		assertEquals(Facet.CEILING, continuous.facet());
		assertTrue(continuous.topologyVisible());
	}
}
