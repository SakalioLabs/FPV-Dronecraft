package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;

import java.util.List;
import java.util.Objects;

/**
 * Snapshot-time box list with allocation-free exact segment occlusion.
 * Interior epsilon keeps a segment ending on the air side of its reflecting
 * patch from self-intersecting the owning solid.
 */
public final class MaterialBoxSegmentBlockQuery
		implements LocalPlaneReflectionSolver.SegmentBlockQuery {
	public static final double INTERIOR_EPSILON_METERS = 1.0e-9;
	private final List<MaterialBox> boxes;

	public MaterialBoxSegmentBlockQuery(List<MaterialBox> boxes) {
		this.boxes = List.copyOf(Objects.requireNonNull(boxes, "boxes"));
	}

	@Override
	public boolean isBlocked(
			double startX,
			double startY,
			double startZ,
			double endX,
			double endY,
			double endZ
	) {
		for (MaterialBox box : boxes) {
			if (intersects(
					startX, startY, startZ,
					endX, endY, endZ,
					box
			)) {
				return true;
			}
		}
		return false;
	}

	private static boolean intersects(
			double startX,
			double startY,
			double startZ,
			double endX,
			double endY,
			double endZ,
			MaterialBox box
	) {
		double minimumX = box.minimumX() + INTERIOR_EPSILON_METERS;
		double maximumX = box.maximumX() - INTERIOR_EPSILON_METERS;
		double minimumY = box.minimumY() + INTERIOR_EPSILON_METERS;
		double maximumY = box.maximumY() - INTERIOR_EPSILON_METERS;
		double minimumZ = box.minimumZ() + INTERIOR_EPSILON_METERS;
		double maximumZ = box.maximumZ() - INTERIOR_EPSILON_METERS;
		if (minimumX >= maximumX
				|| minimumY >= maximumY
				|| minimumZ >= maximumZ) {
			return false;
		}
		double tMinimum = 0.0;
		double tMaximum = 1.0;
		double deltaX = endX - startX;
		if (Math.abs(deltaX) <= 1.0e-15) {
			if (startX < minimumX || startX > maximumX) {
				return false;
			}
		} else {
			double first = (minimumX - startX) / deltaX;
			double second = (maximumX - startX) / deltaX;
			tMinimum = Math.max(tMinimum, Math.min(first, second));
			tMaximum = Math.min(tMaximum, Math.max(first, second));
			if (tMinimum > tMaximum) {
				return false;
			}
		}
		double deltaY = endY - startY;
		if (Math.abs(deltaY) <= 1.0e-15) {
			if (startY < minimumY || startY > maximumY) {
				return false;
			}
		} else {
			double first = (minimumY - startY) / deltaY;
			double second = (maximumY - startY) / deltaY;
			tMinimum = Math.max(tMinimum, Math.min(first, second));
			tMaximum = Math.min(tMaximum, Math.max(first, second));
			if (tMinimum > tMaximum) {
				return false;
			}
		}
		double deltaZ = endZ - startZ;
		if (Math.abs(deltaZ) <= 1.0e-15) {
			return startZ >= minimumZ && startZ <= maximumZ;
		}
		double first = (minimumZ - startZ) / deltaZ;
		double second = (maximumZ - startZ) / deltaZ;
		tMinimum = Math.max(tMinimum, Math.min(first, second));
		tMaximum = Math.min(tMaximum, Math.max(first, second));
		return tMinimum <= tMaximum;
	}
}
