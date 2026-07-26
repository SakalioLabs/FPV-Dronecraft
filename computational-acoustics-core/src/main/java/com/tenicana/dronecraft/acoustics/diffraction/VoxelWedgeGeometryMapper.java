package com.tenicana.dronecraft.acoustics.diffraction;

import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.util.Objects;
import java.util.Optional;

/**
 * Maps a known convex Minecraft block edge to the cylindrical coordinates
 * required by the infinite-wedge UDFA reference.
 */
public final class VoxelWedgeGeometryMapper {
	private static final double EXTERIOR_BLOCK_WEDGE_ANGLE = 3.0 * Math.PI / 2.0;
	private static final double HALF_BLOCK_EDGE_LENGTH = 0.5;
	private static final double MIN_RADIAL_DISTANCE = 1.0e-9;
	private static final double ANGLE_TOLERANCE = 1.0e-9;

	private VoxelWedgeGeometryMapper() {
	}

	public static Optional<Result> map(
			VoxelDiffractionEdge edge,
			AcousticVector source,
			AcousticVector receiver,
			double speedOfSoundMetersPerSecond
	) {
		Objects.requireNonNull(edge, "edge");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(receiver, "receiver");
		if (!(speedOfSoundMetersPerSecond > 0.0)
				|| !Double.isFinite(speedOfSoundMetersPerSecond)) {
			throw new IllegalArgumentException(
					"speedOfSoundMetersPerSecond must be positive and finite"
			);
		}

		AcousticVector axis = vector(edge.edgeAxis());
		AcousticVector sourceFromAnchor = source.subtract(edge.apexPoint());
		AcousticVector receiverFromAnchor = receiver.subtract(edge.apexPoint());
		double sourceAxial = sourceFromAnchor.dot(axis);
		double receiverAxial = receiverFromAnchor.dot(axis);
		double sourceRadial = radialLength(sourceFromAnchor, axis);
		double receiverRadial = radialLength(receiverFromAnchor, axis);
		if (sourceRadial < MIN_RADIAL_DISTANCE || receiverRadial < MIN_RADIAL_DISTANCE) {
			return Optional.empty();
		}

		double axialOffset = (
				sourceAxial * receiverRadial + receiverAxial * sourceRadial
		) / (sourceRadial + receiverRadial);
		AcousticVector optimizedApex = edge.apexPoint().add(axis.multiply(axialOffset));
		AcousticVector sourceVector = source.subtract(optimizedApex);
		AcousticVector receiverVector = receiver.subtract(optimizedApex);
		double sourceDistance = sourceVector.length();
		double receiverDistance = receiverVector.length();

		AcousticVector reference = vector(edge.incomingDirection()).multiply(-1.0);
		AcousticVector positiveAzimuth = axis.cross(reference);
		double sourceAzimuth = azimuth(sourceVector, reference, positiveAzimuth);
		double receiverAzimuth = azimuth(receiverVector, reference, positiveAzimuth);
		if (sourceAzimuth > EXTERIOR_BLOCK_WEDGE_ANGLE + ANGLE_TOLERANCE
				|| receiverAzimuth > EXTERIOR_BLOCK_WEDGE_ANGLE + ANGLE_TOLERANCE) {
			return Optional.empty();
		}
		sourceAzimuth = clampExteriorBoundary(sourceAzimuth);
		receiverAzimuth = clampExteriorBoundary(receiverAzimuth);

		double sourceIncidence = Math.atan2(
				sourceRadial,
				Math.abs(sourceVector.dot(axis))
		);
		double receiverIncidence = Math.atan2(
				receiverRadial,
				Math.abs(receiverVector.dot(axis))
		);
		double incidence = 0.5 * (sourceIncidence + receiverIncidence);
		AcousticVector firstEndpoint = edge.apexPoint().add(
				axis.multiply(-HALF_BLOCK_EDGE_LENGTH)
		);
		AcousticVector secondEndpoint = edge.apexPoint().add(
				axis.multiply(HALF_BLOCK_EDGE_LENGTH)
		);
		double apexPathLength = sourceDistance + receiverDistance;
		double firstEndpointExcessTime = excessTravelTime(
				source,
				receiver,
				firstEndpoint,
				apexPathLength,
				speedOfSoundMetersPerSecond
		);
		double secondEndpointExcessTime = excessTravelTime(
				source,
				receiver,
				secondEndpoint,
				apexPathLength,
				speedOfSoundMetersPerSecond
		);
		InfiniteWedgeGeometry geometry = new InfiniteWedgeGeometry(
				sourceDistance,
				receiverDistance,
				sourceAzimuth,
				receiverAzimuth,
				EXTERIOR_BLOCK_WEDGE_ANGLE,
				incidence,
				speedOfSoundMetersPerSecond
		);
		return Optional.of(new Result(
				geometry,
				UdfaZoneClassifier.classify(geometry),
				optimizedApex,
				axialOffset,
				Math.abs(axialOffset) <= HALF_BLOCK_EDGE_LENGTH + ANGLE_TOLERANCE,
				Math.abs(sourceIncidence - receiverIncidence),
				firstEndpointExcessTime,
				secondEndpointExcessTime
		));
	}

	private static double excessTravelTime(
			AcousticVector source,
			AcousticVector receiver,
			AcousticVector endpoint,
			double apexPathLength,
			double speedOfSound
	) {
		double endpointPathLength = source.subtract(endpoint).length()
				+ receiver.subtract(endpoint).length();
		return Math.max(0.0, endpointPathLength - apexPathLength) / speedOfSound;
	}

	private static double radialLength(AcousticVector vector, AcousticVector axis) {
		return vector.subtract(axis.multiply(vector.dot(axis))).length();
	}

	private static double azimuth(
			AcousticVector vector,
			AcousticVector reference,
			AcousticVector positiveAzimuth
	) {
		double angle = Math.atan2(
				vector.dot(positiveAzimuth),
				vector.dot(reference)
		);
		return angle < 0.0 ? angle + 2.0 * Math.PI : angle;
	}

	private static double clampExteriorBoundary(double angle) {
		if (angle > EXTERIOR_BLOCK_WEDGE_ANGLE) {
			return EXTERIOR_BLOCK_WEDGE_ANGLE;
		}
		return angle;
	}

	private static AcousticVector vector(VoxelDiffractionEdge.AxisDirection direction) {
		return new AcousticVector(direction.x(), direction.y(), direction.z());
	}

	public record Result(
			InfiniteWedgeGeometry geometry,
			UdfaZoneClassifier.Classification zone,
			AcousticVector optimizedApex,
			double axialOffsetFromBlockEdgeCenter,
			boolean apexWithinPhysicalBlockEdge,
			double incidenceAngleMismatchRadians,
			double firstEndpointExcessTimeSeconds,
			double secondEndpointExcessTimeSeconds
	) {
		public Result {
			Objects.requireNonNull(geometry, "geometry");
			Objects.requireNonNull(zone, "zone");
			Objects.requireNonNull(optimizedApex, "optimizedApex");
			if (firstEndpointExcessTimeSeconds < 0.0
					|| secondEndpointExcessTimeSeconds < 0.0
					|| !Double.isFinite(firstEndpointExcessTimeSeconds
					+ secondEndpointExcessTimeSeconds)) {
				throw new IllegalArgumentException(
						"endpoint excess times must be finite and non-negative"
				);
			}
		}
	}
}
