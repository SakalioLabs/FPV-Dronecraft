package com.tenicana.dronecraft.acoustics.diffraction;

/**
 * Radial geometry for a three-sided barrier with two parallel infinite edges.
 *
 * <p>Edge 1 is assigned to the source and edge 2 to the receiver, following
 * Figure 7 of Kirsch & Ewert (Acta Acustica, 2024). Distances are metres,
 * angles are radians, and the source/receiver angles at the opposite edge may
 * lie beyond that edge's exterior wedge angle in the double shadow zone.</p>
 */
public record DoubleEdgeGeometry(
		double sourceDistanceToFirstEdgeMeters,
		double sourceDistanceToSecondEdgeMeters,
		double receiverDistanceToFirstEdgeMeters,
		double receiverDistanceToSecondEdgeMeters,
		double sourceAzimuthAtFirstEdgeRadians,
		double sourceAzimuthAtSecondEdgeRadians,
		double receiverAzimuthAtFirstEdgeRadians,
		double receiverAzimuthAtSecondEdgeRadians,
		double firstExteriorWedgeAngleRadians,
		double secondExteriorWedgeAngleRadians,
		double edgeSeparationMeters,
		double incidenceAngleRadians,
		double speedOfSoundMetersPerSecond,
		double exteriorAngleBlend
) {
	public DoubleEdgeGeometry {
		requirePositiveFinite(
				sourceDistanceToFirstEdgeMeters,
				"sourceDistanceToFirstEdgeMeters"
		);
		requirePositiveFinite(
				sourceDistanceToSecondEdgeMeters,
				"sourceDistanceToSecondEdgeMeters"
		);
		requirePositiveFinite(
				receiverDistanceToFirstEdgeMeters,
				"receiverDistanceToFirstEdgeMeters"
		);
		requirePositiveFinite(
				receiverDistanceToSecondEdgeMeters,
				"receiverDistanceToSecondEdgeMeters"
		);
		requireFinite(sourceAzimuthAtFirstEdgeRadians,
				"sourceAzimuthAtFirstEdgeRadians");
		requireFinite(sourceAzimuthAtSecondEdgeRadians,
				"sourceAzimuthAtSecondEdgeRadians");
		requireFinite(receiverAzimuthAtFirstEdgeRadians,
				"receiverAzimuthAtFirstEdgeRadians");
		requireFinite(receiverAzimuthAtSecondEdgeRadians,
				"receiverAzimuthAtSecondEdgeRadians");
		requireExteriorWedge(firstExteriorWedgeAngleRadians,
				"firstExteriorWedgeAngleRadians");
		requireExteriorWedge(secondExteriorWedgeAngleRadians,
				"secondExteriorWedgeAngleRadians");
		if (firstExteriorWedgeAngleRadians + secondExteriorWedgeAngleRadians
				- Math.PI > 2.0 * Math.PI + 1.0e-12) {
			throw new IllegalArgumentException(
					"combined zero-width exterior angle must not exceed 2pi"
			);
		}
		if (!(edgeSeparationMeters >= 0.0) || !Double.isFinite(edgeSeparationMeters)) {
			throw new IllegalArgumentException(
					"edgeSeparationMeters must be finite and non-negative"
			);
		}
		if (!(incidenceAngleRadians > 0.0 && incidenceAngleRadians <= Math.PI / 2.0)) {
			throw new IllegalArgumentException(
					"incidenceAngleRadians must be in (0, pi/2]"
			);
		}
		requirePositiveFinite(
				speedOfSoundMetersPerSecond,
				"speedOfSoundMetersPerSecond"
		);
		if (!(exteriorAngleBlend >= 0.0 && exteriorAngleBlend <= 1.0)
				|| !Double.isFinite(exteriorAngleBlend)) {
			throw new IllegalArgumentException("exteriorAngleBlend must be in [0, 1]");
		}
	}

	public double effectiveEdgeSeparationMeters() {
		return edgeSeparationMeters / Math.sin(incidenceAngleRadians);
	}

	public double shortestDiffractedPathMeters() {
		return sourceDistanceToFirstEdgeMeters
				+ effectiveEdgeSeparationMeters()
				+ receiverDistanceToSecondEdgeMeters;
	}

	/**
	 * Constructs a square-barrier cross section. Horizontal coordinates must
	 * increase from source through both top edges to receiver; both edges must
	 * have the same height.
	 */
	public static DoubleEdgeGeometry squareBarrierInPlane(
			PlanarPoint source,
			PlanarPoint receiver,
			PlanarPoint firstEdge,
			PlanarPoint secondEdge,
			double incidenceAngleRadians,
			double speedOfSoundMetersPerSecond
	) {
		if (!(source.horizontalMeters() < firstEdge.horizontalMeters()
				&& firstEdge.horizontalMeters() < secondEdge.horizontalMeters()
				&& secondEdge.horizontalMeters() < receiver.horizontalMeters())) {
			throw new IllegalArgumentException(
					"points must be ordered source < edge1 < edge2 < receiver"
			);
		}
		if (Math.abs(firstEdge.verticalMeters() - secondEdge.verticalMeters())
				> 1.0e-12) {
			throw new IllegalArgumentException(
					"parallel square-barrier edges must have equal height"
			);
		}
		double sourceFirstHorizontal = firstEdge.horizontalMeters()
				- source.horizontalMeters();
		double sourceSecondHorizontal = secondEdge.horizontalMeters()
				- source.horizontalMeters();
		double receiverFirstHorizontal = receiver.horizontalMeters()
				- firstEdge.horizontalMeters();
		double receiverSecondHorizontal = receiver.horizontalMeters()
				- secondEdge.horizontalMeters();
		double sourceVertical = firstEdge.verticalMeters()
				- source.verticalMeters();
		double receiverVertical = secondEdge.verticalMeters()
				- receiver.verticalMeters();
		return squareBarrier(
				Math.hypot(sourceFirstHorizontal, sourceVertical),
				Math.hypot(sourceSecondHorizontal, sourceVertical),
				Math.hypot(receiverFirstHorizontal, receiverVertical),
				Math.hypot(receiverSecondHorizontal, receiverVertical),
				Math.atan2(sourceFirstHorizontal, sourceVertical),
				2.0 * Math.PI - Math.atan2(
						sourceSecondHorizontal,
						sourceVertical
				),
				2.0 * Math.PI - Math.atan2(
						receiverFirstHorizontal,
						receiverVertical
				),
				Math.atan2(receiverSecondHorizontal, receiverVertical),
				secondEdge.horizontalMeters() - firstEdge.horizontalMeters(),
				incidenceAngleRadians,
				speedOfSoundMetersPerSecond
		);
	}

	public static DoubleEdgeGeometry squareBarrier(
			double sourceDistanceToFirstEdgeMeters,
			double sourceDistanceToSecondEdgeMeters,
			double receiverDistanceToFirstEdgeMeters,
			double receiverDistanceToSecondEdgeMeters,
			double sourceAzimuthAtFirstEdgeRadians,
			double sourceAzimuthAtSecondEdgeRadians,
			double receiverAzimuthAtFirstEdgeRadians,
			double receiverAzimuthAtSecondEdgeRadians,
			double edgeSeparationMeters,
			double incidenceAngleRadians,
			double speedOfSoundMetersPerSecond
	) {
		return new DoubleEdgeGeometry(
				sourceDistanceToFirstEdgeMeters,
				sourceDistanceToSecondEdgeMeters,
				receiverDistanceToFirstEdgeMeters,
				receiverDistanceToSecondEdgeMeters,
				sourceAzimuthAtFirstEdgeRadians,
				sourceAzimuthAtSecondEdgeRadians,
				receiverAzimuthAtFirstEdgeRadians,
				receiverAzimuthAtSecondEdgeRadians,
				3.0 * Math.PI / 2.0,
				3.0 * Math.PI / 2.0,
				edgeSeparationMeters,
				incidenceAngleRadians,
				speedOfSoundMetersPerSecond,
				1.0
		);
	}

	private static void requirePositiveFinite(double value, String name) {
		if (!(value > 0.0) || !Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be positive and finite");
		}
	}

	private static void requireFinite(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}

	private static void requireExteriorWedge(double value, String name) {
		if (!(value > Math.PI && value < 2.0 * Math.PI)
				|| !Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be in (pi, 2pi)");
		}
	}

	public record PlanarPoint(double horizontalMeters, double verticalMeters) {
		public PlanarPoint {
			requireFinite(horizontalMeters, "horizontalMeters");
			requireFinite(verticalMeters, "verticalMeters");
		}
	}
}
