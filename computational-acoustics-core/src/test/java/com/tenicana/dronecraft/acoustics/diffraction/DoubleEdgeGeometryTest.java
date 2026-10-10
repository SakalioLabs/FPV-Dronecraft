package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DoubleEdgeGeometryTest {
	private static final DoubleEdgeGeometry.PlanarPoint SOURCE =
			new DoubleEdgeGeometry.PlanarPoint(2.487, 1.235);
	private static final DoubleEdgeGeometry.PlanarPoint RECEIVER =
			new DoubleEdgeGeometry.PlanarPoint(8.512, 1.235);
	private static final DoubleEdgeGeometry.PlanarPoint FIRST_EDGE =
			new DoubleEdgeGeometry.PlanarPoint(5.487, 2.066);
	private static final DoubleEdgeGeometry.PlanarPoint SECOND_EDGE =
			new DoubleEdgeGeometry.PlanarPoint(5.512, 2.066);

	@Test
	void constructsPublishedBrasCrossSection() {
		DoubleEdgeGeometry geometry = geometry(SOURCE, RECEIVER);

		assertEquals(
				Math.hypot(3.0, 0.831),
				geometry.sourceDistanceToFirstEdgeMeters(),
				1.0e-12
		);
		assertEquals(
				Math.hypot(3.0, 0.831),
				geometry.receiverDistanceToSecondEdgeMeters(),
				1.0e-12
		);
		assertEquals(
				Math.atan2(3.0, 0.831),
				geometry.sourceAzimuthAtFirstEdgeRadians(),
				1.0e-12
		);
		assertEquals(
				Math.atan2(3.0, 0.831),
				geometry.receiverAzimuthAtSecondEdgeRadians(),
				1.0e-12
		);
		assertEquals(
				Math.hypot(3.0, 0.831) * 2.0 + 0.025,
				geometry.shortestDiffractedPathMeters(),
				1.0e-12
		);
	}

	@Test
	void symmetricFloorImagePathsHaveReciprocalMagnitude() {
		DoubleEdgeGeometry.PlanarPoint sourceImage =
				new DoubleEdgeGeometry.PlanarPoint(2.487, -1.235);
		DoubleEdgeGeometry.PlanarPoint receiverImage =
				new DoubleEdgeGeometry.PlanarPoint(8.512, -1.235);
		DoubleEdgeGeometry before = geometry(sourceImage, RECEIVER);
		DoubleEdgeGeometry after = geometry(SOURCE, receiverImage);
		UdfaDoubleEdgeFilter filter = new UdfaDoubleEdgeFilter(
				UdfaInfiniteWedgeFilter.Parameters.published2023()
		);

		assertEquals(
				before.shortestDiffractedPathMeters(),
				after.shortestDiffractedPathMeters(),
				1.0e-12
		);
		assertEquals(
				filter.pressureMagnitude(4_000.0, before),
				filter.pressureMagnitude(4_000.0, after),
				1.0e-12
		);
		assertEquals(
				3.928846,
				(
						before.shortestDiffractedPathMeters()
								- geometry(SOURCE, RECEIVER)
								.shortestDiffractedPathMeters()
				) / 343.0 * 1_000.0,
				0.000001
		);
	}

	@Test
	void rejectsMisorderedOrUnequalHeightEdges() {
		assertThrows(
				IllegalArgumentException.class,
				() -> DoubleEdgeGeometry.squareBarrierInPlane(
						RECEIVER,
						SOURCE,
						FIRST_EDGE,
						SECOND_EDGE,
						Math.PI / 2.0,
						343.0
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> DoubleEdgeGeometry.squareBarrierInPlane(
						SOURCE,
						RECEIVER,
						FIRST_EDGE,
						new DoubleEdgeGeometry.PlanarPoint(5.512, 2.1),
						Math.PI / 2.0,
						343.0
				)
		);
	}

	private static DoubleEdgeGeometry geometry(
			DoubleEdgeGeometry.PlanarPoint source,
			DoubleEdgeGeometry.PlanarPoint receiver
	) {
		return DoubleEdgeGeometry.squareBarrierInPlane(
				source,
				receiver,
				FIRST_EDGE,
				SECOND_EDGE,
				Math.PI / 2.0,
				343.0
		);
	}
}
