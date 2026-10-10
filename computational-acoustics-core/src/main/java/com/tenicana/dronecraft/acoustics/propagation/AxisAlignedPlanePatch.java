package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;

import java.util.Objects;

/**
 * Finite continuous reflection face. The normal sign points toward the air
 * half-space from which both source and listener must see the face.
 */
public record AxisAlignedPlanePatch(
		int axis,
		int normalSign,
		double coordinateMeters,
		double minimumFirstMeters,
		double maximumFirstMeters,
		double minimumSecondMeters,
		double maximumSecondMeters,
		AcousticMaterial material
) {
	public AxisAlignedPlanePatch {
		if (axis < 0 || axis > 2) {
			throw new IllegalArgumentException("axis must be 0, 1 or 2");
		}
		if (normalSign != -1 && normalSign != 1) {
			throw new IllegalArgumentException("normalSign must be -1 or 1");
		}
		requireFinite(coordinateMeters, "coordinateMeters");
		requireFinite(minimumFirstMeters, "minimumFirstMeters");
		requireFinite(maximumFirstMeters, "maximumFirstMeters");
		requireFinite(minimumSecondMeters, "minimumSecondMeters");
		requireFinite(maximumSecondMeters, "maximumSecondMeters");
		if (minimumFirstMeters >= maximumFirstMeters
				|| minimumSecondMeters >= maximumSecondMeters) {
			throw new IllegalArgumentException(
					"plane patch must have positive area"
			);
		}
		Objects.requireNonNull(material, "material");
	}

	private static void requireFinite(double value, String label) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(label + " must be finite");
		}
	}
}
