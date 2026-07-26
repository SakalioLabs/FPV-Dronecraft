package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.Objects;

/**
 * First-order shoebox paths whose two open-air legs are traversed by the
 * production CPU {@link VoxelDda}. The image-source construction determines
 * the continuous reflection point; DDA remains the topology/visibility
 * reference.
 */
public final class DdaFirstOrderPathSolver {
	private static final double BOUNDARY_EPSILON_METERS = 1.0e-9;

	private DdaFirstOrderPathSolver() {
	}

	public static Path direct(
			AcousticVector source,
			AcousticVector listener,
			RoomBounds bounds,
			int maximumCellsPerLeg
	) {
		validateEndpoint(source, bounds, "source");
		validateEndpoint(listener, bounds, "listener");
		Leg leg = traceOpenLeg(source, listener, bounds, maximumCellsPerLeg);
		return new Path(
				null,
				null,
				listener.subtract(source).length(),
				leg.visitedCells(),
				leg.reachedEnd()
		);
	}

	public static Path reflected(
			AcousticVector source,
			AcousticVector listener,
			RoomBounds bounds,
			Facet facet,
			int maximumCellsPerLeg
	) {
		Objects.requireNonNull(facet, "facet");
		validateEndpoint(source, bounds, "source");
		validateEndpoint(listener, bounds, "listener");

		double plane = facet.plane(bounds);
		AcousticVector image = facet.mirror(source, plane);
		double imageAxis = facet.axis(image);
		double listenerAxis = facet.axis(listener);
		double denominator = listenerAxis - imageAxis;
		if (Math.abs(denominator) <= 1.0e-12) {
			throw new IllegalArgumentException(
					"image-to-listener line is parallel to facet"
			);
		}
		double interpolation = (plane - imageAxis) / denominator;
		if (!(interpolation > 0.0 && interpolation < 1.0)) {
			throw new IllegalArgumentException(
					"reflection point is outside image-to-listener segment"
			);
		}
		AcousticVector reflectionPoint = image.add(
				listener.subtract(image).multiply(interpolation)
		);
		if (!facet.containsOnPlane(reflectionPoint, bounds)) {
			throw new IllegalArgumentException(
					"reflection point lies outside finite facet"
			);
		}
		AcousticVector interiorPoint = facet.moveInside(
				reflectionPoint,
				BOUNDARY_EPSILON_METERS
		);
		Leg outgoing = traceOpenLeg(
				source,
				interiorPoint,
				bounds,
				maximumCellsPerLeg
		);
		Leg incoming = traceOpenLeg(
				interiorPoint,
				listener,
				bounds,
				maximumCellsPerLeg
		);
		double length = reflectionPoint.subtract(source).length()
				+ listener.subtract(reflectionPoint).length();
		return new Path(
				facet,
				reflectionPoint,
				length,
				outgoing.visitedCells() + incoming.visitedCells(),
				outgoing.reachedEnd() && incoming.reachedEnd()
		);
	}

	private static Leg traceOpenLeg(
			AcousticVector start,
			AcousticVector end,
			RoomBounds bounds,
			int maximumCells
	) {
		boolean[] open = {true};
		VoxelDda.WalkResult walk = VoxelDda.walk(
				start,
				end,
				(x, y, z, segmentLengthMeters) -> {
					open[0] &= bounds.containsCell(x, y, z);
					return open[0];
				},
				maximumCells
		);
		return new Leg(
				walk.visitedCellCount(),
				open[0] && walk.reachedEnd()
		);
	}

	private static void validateEndpoint(
			AcousticVector position,
			RoomBounds bounds,
			String label
	) {
		Objects.requireNonNull(position, label);
		Objects.requireNonNull(bounds, "bounds");
		if (!bounds.containsPosition(position)) {
			throw new IllegalArgumentException(label + " must be inside room");
		}
	}

	public enum Facet {
		FLOOR(Axis.Z, false),
		CEILING(Axis.Z, true),
		WEST(Axis.X, false),
		SOUTH(Axis.Y, false),
		EAST(Axis.X, true),
		NORTH(Axis.Y, true);

		private final Axis axis;
		private final boolean maximum;

		Facet(Axis axis, boolean maximum) {
			this.axis = axis;
			this.maximum = maximum;
		}

		private double plane(RoomBounds bounds) {
			return maximum ? axis.extent(bounds) : 0.0;
		}

		private double axis(AcousticVector vector) {
			return axis.component(vector);
		}

		private AcousticVector mirror(AcousticVector point, double plane) {
			return axis.withComponent(point, 2.0 * plane - axis(point));
		}

		private AcousticVector moveInside(
				AcousticVector point,
				double epsilon
		) {
			double coordinate = axis(point) + (maximum ? -epsilon : epsilon);
			return axis.withComponent(point, coordinate);
		}

		private boolean containsOnPlane(
				AcousticVector point,
				RoomBounds bounds
		) {
			double epsilon = 1.0e-9;
			return point.x() >= -epsilon
					&& point.x() <= bounds.xMeters() + epsilon
					&& point.y() >= -epsilon
					&& point.y() <= bounds.yMeters() + epsilon
					&& point.z() >= -epsilon
					&& point.z() <= bounds.zMeters() + epsilon;
		}
	}

	private enum Axis {
		X {
			@Override
			double component(AcousticVector vector) {
				return vector.x();
			}

			@Override
			double extent(RoomBounds bounds) {
				return bounds.xMeters();
			}

			@Override
			AcousticVector withComponent(AcousticVector vector, double value) {
				return new AcousticVector(value, vector.y(), vector.z());
			}
		},
		Y {
			@Override
			double component(AcousticVector vector) {
				return vector.y();
			}

			@Override
			double extent(RoomBounds bounds) {
				return bounds.yMeters();
			}

			@Override
			AcousticVector withComponent(AcousticVector vector, double value) {
				return new AcousticVector(vector.x(), value, vector.z());
			}
		},
		Z {
			@Override
			double component(AcousticVector vector) {
				return vector.z();
			}

			@Override
			double extent(RoomBounds bounds) {
				return bounds.zMeters();
			}

			@Override
			AcousticVector withComponent(AcousticVector vector, double value) {
				return new AcousticVector(vector.x(), vector.y(), value);
			}
		};

		abstract double component(AcousticVector vector);

		abstract double extent(RoomBounds bounds);

		abstract AcousticVector withComponent(
				AcousticVector vector,
				double value
		);
	}

	public record RoomBounds(double xMeters, double yMeters, double zMeters) {
		public RoomBounds {
			if (!Double.isFinite(xMeters) || xMeters <= 0.0
					|| !Double.isFinite(yMeters) || yMeters <= 0.0
					|| !Double.isFinite(zMeters) || zMeters <= 0.0) {
				throw new IllegalArgumentException(
						"room extents must be finite and positive"
				);
			}
		}

		public boolean containsPosition(AcousticVector position) {
			return position.x() >= 0.0 && position.x() < xMeters
					&& position.y() >= 0.0 && position.y() < yMeters
					&& position.z() >= 0.0 && position.z() < zMeters;
		}

		public boolean containsCell(int x, int y, int z) {
			return x >= 0 && x < (int) Math.ceil(xMeters)
					&& y >= 0 && y < (int) Math.ceil(yMeters)
					&& z >= 0 && z < (int) Math.ceil(zMeters);
		}
	}

	public record Path(
			Facet facet,
			AcousticVector reflectionPoint,
			double lengthMeters,
			int visitedCells,
			boolean topologyVisible
	) {
		public Path {
			if (!Double.isFinite(lengthMeters) || lengthMeters < 0.0) {
				throw new IllegalArgumentException(
						"path length must be finite and non-negative"
				);
			}
			if (visitedCells < 1) {
				throw new IllegalArgumentException(
						"visitedCells must be positive"
				);
			}
		}
	}

	private record Leg(int visitedCells, boolean reachedEnd) {
	}
}
