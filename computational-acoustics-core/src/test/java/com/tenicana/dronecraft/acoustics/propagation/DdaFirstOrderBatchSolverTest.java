package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderBatchSolver.Workspace;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Facet;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Path;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DdaFirstOrderBatchSolverTest {
	@Test
	void primitiveBatchMatchesObjectReferenceForAllSevenPaths() {
		AcousticVector source =
				new AcousticVector(1.6330509, 0.6820041, 1.16493109);
		AcousticVector listener =
				new AcousticVector(2.21190695, 1.71556362, 1.30742631);
		RoomBounds bounds = new RoomBounds(5.705, 5.965, 2.355);
		Workspace workspace = new Workspace();

		DdaFirstOrderBatchSolver.solve(
				source.x(),
				source.y(),
				source.z(),
				listener.x(),
				listener.y(),
				listener.z(),
				bounds,
				32,
				workspace
		);

		Path direct = DdaFirstOrderPathSolver.direct(
				source,
				listener,
				bounds,
				32
		);
		assertPathEquals(direct, 0, workspace);
		for (Facet facet : Facet.values()) {
			Path reflected = DdaFirstOrderPathSolver.reflected(
					source,
					listener,
					bounds,
					facet,
					32
			);
			int index = facet.ordinal() + 1;
			assertPathEquals(reflected, index, workspace);
			assertEquals(
					reflected.reflectionPoint().x(),
					workspace.reflectionX(index),
					1.0e-12
			);
			assertEquals(
					reflected.reflectionPoint().y(),
					workspace.reflectionY(index),
					1.0e-12
			);
			assertEquals(
					reflected.reflectionPoint().z(),
					workspace.reflectionZ(index),
					1.0e-12
			);
		}
	}

	private static void assertPathEquals(
			Path expected,
			int index,
			Workspace actual
	) {
		assertEquals(
				expected.lengthMeters(),
				actual.lengthMeters(index),
				1.0e-12
		);
		assertEquals(expected.visitedCells(), actual.visitedCells(index));
		assertEquals(
				expected.topologyVisible(),
				actual.topologyVisible(index)
		);
	}
}
