package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdfaZoneClassifierTest {
	@Test
	void separatesReflectionViewAndShadowRegionsForSquareWedge() {
		UdfaZoneClassifier.Classification firstReflection =
				UdfaZoneClassifier.classify(geometry(1.0));
		UdfaZoneClassifier.Classification view =
				UdfaZoneClassifier.classify(geometry(3.0));
		UdfaZoneClassifier.Classification shadow =
				UdfaZoneClassifier.classify(geometry(4.2));

		assertTrue(firstReflection.directVisible());
		assertTrue(firstReflection.firstPlaneReflectionVisible());
		assertEquals(1, firstReflection.visibleReflectionCount());
		assertTrue(view.directVisible());
		assertEquals(0, view.visibleReflectionCount());
		assertTrue(shadow.shadowZone());
		assertFalse(shadow.firstPlaneReflectionVisible());
	}

	@Test
	void shadowBoundaryItselfRemainsDirectVisibleForContinuousBlend() {
		double source = Math.PI / 4.0;
		UdfaZoneClassifier.Classification boundary =
				UdfaZoneClassifier.classify(geometry(source + Math.PI));

		assertTrue(boundary.directVisible());
		assertEquals(0.0, boundary.signedShadowBoundaryOffsetRadians(), 1.0e-12);
	}

	@Test
	void detectsSecondPlaneReflectionWhenBoundaryLiesInsideWedge() {
		double wedge = 3.0 * Math.PI / 2.0;
		double source = 1.4 * Math.PI;
		double secondBoundary = 2.0 * wedge - Math.PI - source;
		InfiniteWedgeGeometry geometry = new InfiniteWedgeGeometry(
				2.0,
				2.0,
				source,
				secondBoundary + 0.1,
				wedge,
				Math.PI / 2.0,
				343.0
		);

		assertTrue(UdfaZoneClassifier.classify(geometry)
				.secondPlaneReflectionVisible());
	}

	private static InfiniteWedgeGeometry geometry(double receiverAzimuth) {
		return new InfiniteWedgeGeometry(
				2.0,
				2.0,
				Math.PI / 4.0,
				receiverAzimuth,
				3.0 * Math.PI / 2.0,
				Math.PI / 2.0,
				343.0
		);
	}
}
